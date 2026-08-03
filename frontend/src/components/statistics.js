import { api } from '../services/api.js';
import { render, html } from '../lib/preact-standalone.js';
import { formatDuration } from '../utils/dom.js';
import { spinner, errorBox, statCard, warningBox } from '../utils/ui.js';

// --- Filtro de bloques de recompensa (y génesis) ---
function expectedReward(index, config) {
    if (config.rewardSource === 'unlimited') return 20;
    if (config.rewardSource === 'genesis') {
        return (config.baseReward || 50) / Math.pow(2, Math.floor(index / (config.halvingInterval || 10)));
    }
    return 20;
}

// Un bloque de recompensa contiene exactamente 1 TX del sistema con el monto esperado.
export function isRewardBlock(block, config) {
    if (!block.data || block.data.length !== 1 || block.index === 0) return false;
    const tx = block.data[0];
    const senderOk = tx.sender === (config.systemAddress || 'SYSTEM_GENESIS') || tx.sender === 'system';
    if (!senderOk) return false;
    return Math.abs(tx.amount - expectedReward(block.index, config)) < 0.01;
}

// Los bloques de recompensa y el génesis no son bloques de datos: se excluyen de las estadísticas.
function isDataBlock(block, config) {
    if (block.index === 0) return false;
    return !isRewardBlock(block, config);
}

// Descarga todas las páginas de bloques (paginación real del backend).
async function fetchAllBlocksPaged() {
    const allBlocks = [];
    const size = 500;
    let page = 0;
    let totalElements = null;
    while (true) {
        const data = await api.allBlocks(page, size);
        const blocks = data._embedded ? data._embedded.blockList : [];
        allBlocks.push(...blocks);
        if (data.page) totalElements = data.page.totalElements;
        const total = totalElements !== null ? totalElements : allBlocks.length;
        if (allBlocks.length >= total || blocks.length === 0) break;
        page++;
    }
    return allBlocks;
}

function createBarChart(containerId, data, title, xLabel, yLabel, formatX = d => d, formatY = d => d) {
    const container = document.getElementById(containerId);
    if (!container) return;

    const margin = { top: 30, right: 30, bottom: 60, left: 70 };
    const containerWidth = Math.max(320, container.clientWidth || 600);
    const width = containerWidth - margin.left - margin.right;
    const height = 300 - margin.top - margin.bottom;

    const svg = d3.select(`#${containerId}`)
        .append("svg")
        .attr("viewBox", `0 0 ${width + margin.left + margin.right} ${height + margin.top + margin.bottom}`)
        .attr("preserveAspectRatio", "xMidYMid meet")
        .attr("style", "width:100%;height:auto;max-width:700px")
        .append("g")
        .attr("transform", `translate(${margin.left},${margin.top})`);

    // Definir el gradiente
    const defs = svg.append("defs");
    const linearGradient = defs.append("linearGradient")
        .attr("id", `${containerId}-gradient`)
        .attr("x1", "0%")
        .attr("y1", "0%")
        .attr("x2", "0%")
        .attr("y2", "100%");

    linearGradient.append("stop")
        .attr("offset", "0%")
        .attr("stop-color", "#6366F1");

    linearGradient.append("stop")
        .attr("offset", "100%")
        .attr("stop-color", "#4338CA");

    const x = d3.scaleBand()
        .range([0, width])
        .padding(0.1);

    const y = d3.scaleLinear()
        .range([height, 0]);

    x.domain(data.map(d => d.label));
    y.domain([0, d3.max(data, d => d.value)]).nice();

    svg.append("g")
        .attr("transform", `translate(0,${height})`)
        .call(d3.axisBottom(x).tickFormat(formatX))
        .selectAll("text")
        .attr("transform", "rotate(-45)")
        .style("text-anchor", "end");

    svg.append("g")
        .call(d3.axisLeft(y).tickFormat(formatY));

    svg.selectAll(".bar")
        .data(data)
        .enter().append("rect")
        .attr("class", "bar")
        .attr("x", d => x(d.label))
        .attr("width", x.bandwidth())
        .attr("y", d => y(d.value))
        .attr("height", d => height - y(d.value))
        .attr("fill", `url(#${containerId}-gradient)`);

    // X-axis label
    svg.append("text")
        .attr("class", "x label")
        .attr("text-anchor", "middle")
        .attr("x", width / 2)
        .attr("y", height + margin.bottom - 5)
        .style("font-size", "14px")
        .text(xLabel);

    // Y-axis label
    svg.append("text")
        .attr("class", "y label")
        .attr("text-anchor", "middle")
        .attr("y", -margin.left + 20)
        .attr("x", -height / 2)
        .attr("dy", ".75em")
        .attr("transform", "rotate(-90)")
        .style("font-size", "14px")
        .text(yLabel);

    // Chart title
    svg.append("text")
        .attr("x", (width / 2))
        .attr("y", 0 - (margin.top / 2))
        .attr("text-anchor", "middle")
        .style("font-size", "18px")
        .style("font-weight", "bold")
        .text(title);
}


export async function statistics(root) {
    render(spinner(), root);

    try {
        const systemConfig = await api.config().catch(() => ({}));
        const [allBlocks, latestBlock, pendingTxCountData] = await Promise.all([
            fetchAllBlocksPaged(),
            api.latest(),
            api.txCount().catch(e => ({ count: 'Error' }))
        ]);

        // Excluir bloques de recompensa y génesis: no ensucian las métricas
        const dataBlocks = allBlocks.filter(b => isDataBlock(b, systemConfig));
        const excludedCount = allBlocks.length - dataBlocks.length;

        if (dataBlocks.length === 0) {
            render(warningBox('Información', 'No hay datos de bloques disponibles para generar estadísticas.'), root);
            return;
        }

        // Sort blocks by index for accurate time calculations
        dataBlocks.sort((a, b) => a.index - b.index);

        // --- Calculate Core Statistics ---
        const totalBlocks = dataBlocks.length;
        let totalTransactionsMined = 0;
        let totalNonce = 0;
        let totalTransactionAmount = 0;
        let totalBlockTime = 0;
        const blockIntervals = [];
        let minTxsInBlock = Infinity;
        let maxTxsInBlock = 0;

        let totalFirstTxDelay = 0;
        let minFirstTxDelay = Infinity;
        let maxFirstTxDelay = 0;
        let blocksWithCalculableDelay = 0;
        const firstTxDelayData = [];

        const txsPerBlockData = [];
        const blocksPerDay = {};
        const txsPerDay = {};

        for (let i = 0; i < totalBlocks; i++) {
            const block = dataBlocks[i];
            const blockDate = new Date(block.timestamp * 1000).toLocaleDateString('es-AR', { year: 'numeric', month: '2-digit', day: '2-digit' });

            totalTransactionsMined += block.data.length;
            totalNonce += block.nonce;

            if (block.data.length < minTxsInBlock) minTxsInBlock = block.data.length;
            if (block.data.length > maxTxsInBlock) maxTxsInBlock = block.data.length;

            if (i >= totalBlocks - 20) {
                txsPerBlockData.push({ label: `Bloque ${block.index}`, value: block.data.length });
            }

            if (block.data && block.data.length > 0) {
                const sortedTxs = [...block.data].sort((a, b) => a.timestamp - b.timestamp);
                const delay = block.timestamp - sortedTxs[0].timestamp;

                if (delay >= 0) {
                    totalFirstTxDelay += delay;
                    if (delay < minFirstTxDelay) minFirstTxDelay = delay;
                    if (delay > maxFirstTxDelay) maxFirstTxDelay = delay;
                    blocksWithCalculableDelay++;

                    if (i >= totalBlocks - 20) {
                        firstTxDelayData.push({ label: `Bloque ${block.index}`, value: delay });
                    }
                }
            }

            blocksPerDay[blockDate] = (blocksPerDay[blockDate] || 0) + 1;
            txsPerDay[blockDate] = (txsPerDay[blockDate] || 0) + block.data.length;

            block.data.forEach(tx => {
                totalTransactionAmount += tx.amount;
            });

            if (i > 0) {
                const interval = block.timestamp - dataBlocks[i - 1].timestamp;
                if (interval > 0) {
                    blockIntervals.push(interval);
                    totalBlockTime += interval;
                }
            }
        }

        const avgBlockTime = blockIntervals.length > 0 ? totalBlockTime / blockIntervals.length : 0;
        const avgTxsPerBlock = totalBlocks > 0 ? totalTransactionsMined / totalBlocks : 0;
        const avgNoncePerBlock = totalBlocks > 0 ? totalNonce / totalBlocks : 0;
        const timeSinceLastBlock = latestBlock ? (Date.now() / 1000) - latestBlock.timestamp : 0;
        const avgTransactionAmount = totalTransactionsMined > 0 ? totalTransactionAmount / totalTransactionsMined : 0;
        const avgFirstTxDelay = blocksWithCalculableDelay > 0 ? totalFirstTxDelay / blocksWithCalculableDelay : 0;

        const pendingTxCount = pendingTxCountData.count !== undefined ? pendingTxCountData.count.toLocaleString() : 'Error';

        // Prepare data for daily charts (last 7 days)
        const dailyBlocksChartData = [];
        const dailyTxsChartData = [];
        for (let i = 6; i >= 0; i--) {
            const d = new Date();
            d.setDate(d.getDate() - i);
            const dateLabel = d.toLocaleDateString('es-AR', { month: '2-digit', day: '2-digit' });
            const fullDateString = d.toLocaleDateString('es-AR', { year: 'numeric', month: '2-digit', day: '2-digit' });
            dailyBlocksChartData.push({ label: dateLabel, value: blocksPerDay[fullDateString] || 0 });
            dailyTxsChartData.push({ label: dateLabel, value: txsPerDay[fullDateString] || 0 });
        }

        const hasD3 = typeof d3 !== 'undefined';

        // --- Render ---
        render(html`
            <h1 class="text-3xl font-bold text-gray-900 mb-4">Estadísticas de la Red Blockchain</h1>
            <p class="text-sm text-gray-500 mb-8">
                Métricas sobre <strong>${totalBlocks.toLocaleString()} bloques de datos</strong>
                ${excludedCount > 0 ? html`(se excluyen ${excludedCount.toLocaleString()} bloques de recompensa/génesis)` : ''}.
            </p>
            ${!hasD3 ? html`
                <div class="bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative mb-6">
                    Advertencia: La librería de gráficos (D3.js) no se ha cargado. Asegúrate de incluirla en tu HTML.
                </div>` : ''}

            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3 gap-6 mb-8">
                ${statCard('fas fa-layer-group', 'bg-blue-100 text-blue-600', 'Total de Bloques de Datos', totalBlocks.toLocaleString())}
                ${statCard('fas fa-clock', 'bg-indigo-100 text-indigo-600', 'Tiempo Promedio por Bloque', formatDuration(avgBlockTime),
                    `Basado en ${blockIntervals.length.toLocaleString()} intervalos.`)}
                ${statCard('fas fa-history', 'bg-yellow-100 text-yellow-600', 'Tiempo Desde Último Bloque', formatDuration(timeSinceLastBlock))}
                ${statCard('fas fa-exchange-alt', 'bg-green-100 text-green-600', 'Transacciones Totales Minadas', totalTransactionsMined.toLocaleString())}
                ${statCard('fas fa-file-invoice-dollar', 'bg-teal-100 text-teal-600', 'TXs Promedio por Bloque', avgTxsPerBlock.toFixed(2))}
                ${statCard('fas fa-fingerprint', 'bg-pink-100 text-pink-600', 'Nonce Promedio por Bloque', avgNoncePerBlock.toFixed(0))}
                ${statCard('fas fa-tasks', 'bg-orange-100 text-orange-600', 'TXs Pendientes Actuales', pendingTxCount)}
                ${statCard('fas fa-money-bill-wave', 'bg-purple-100 text-purple-600', 'Valor Promedio de TX', `${avgTransactionAmount.toFixed(2)}`)}
                ${statCard('fas fa-minus-circle', 'bg-red-100 text-red-600', 'Bloque Más Pequeño (TXs)', minTxsInBlock.toLocaleString())}
                ${statCard('fas fa-plus-circle', 'bg-blue-100 text-blue-600', 'Bloque Más Grande (TXs)', maxTxsInBlock.toLocaleString())}
                ${statCard('fas fa-hourglass-start', 'bg-cyan-100 text-cyan-600', 'Retraso Promedio Primera TX', formatDuration(avgFirstTxDelay),
                    'Valores altos pueden indicar un desfase de reloj (cliente vs. servidor).')}
                ${statCard('fas fa-bolt', 'bg-lime-100 text-lime-600', 'Retraso Mínimo Primera TX', minFirstTxDelay === Infinity ? 'N/A' : formatDuration(minFirstTxDelay))}
                ${statCard('fas fa-exclamation-triangle', 'bg-rose-100 text-rose-600', 'Retraso Máximo Primera TX', maxFirstTxDelay === 0 ? 'N/A' : formatDuration(maxFirstTxDelay))}
            </div>

            <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-10">
                <div class="bg-white p-6 rounded-lg shadow-md overflow-x-auto">
                    <h3 class="text-xl font-bold text-gray-800 mb-4">Transacciones por Bloque (Últimos Bloques Minados)</h3>
                    <div id="txs-per-block-chart" class="flex justify-center items-center"></div>
                </div>
                <div class="bg-white p-6 rounded-lg shadow-md overflow-x-auto">
                    <h3 class="text-xl font-bold text-gray-800 mb-4">Bloques Minados por Día (Últimos 7 Días)</h3>
                    <div id="blocks-per-day-chart" class="flex justify-center items-center"></div>
                </div>
                <div class="bg-white p-6 rounded-lg shadow-md overflow-x-auto">
                    <h3 class="text-xl font-bold text-gray-800 mb-4">Retraso Primera TX (Últimos Bloques Minados)</h3>
                    <div id="first-tx-delay-chart" class="flex justify-center items-center"></div>
                </div>
            </div>
        `, root);

        if (hasD3) {
            createBarChart('txs-per-block-chart', txsPerBlockData, 'Transacciones por Bloque', 'Bloque', 'Número de Transacciones');
            createBarChart('blocks-per-day-chart', dailyBlocksChartData, 'Bloques por Día', 'Fecha', 'Número de Bloques');
            createBarChart('first-tx-delay-chart', firstTxDelayData, 'Retraso Primera TX', 'Bloque', 'Tiempo (segundos)');
        } else {
            console.warn("D3.js no está cargado. Los gráficos no se mostrarán.");
        }

    } catch (error) {
        render(errorBox('No se pudieron cargar las estadísticas. Asegúrate de que la API esté funcionando correctamente y que haya bloques disponibles.'), root);
        console.error('Error in statistics component:', error);
    }
}
