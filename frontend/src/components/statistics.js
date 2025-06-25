import { api } from '../services/api.js';
import { createEl, createLoadingSpinner, formatDuration } from '../utils/dom.js';

function createBarChart(containerId, data, title, xLabel, yLabel, formatX = d => d, formatY = d => d) {
    const margin = { top: 30, right: 30, bottom: 50, left: 70 }; // Aumento de margen para labels
    const width = 600 - margin.left - margin.right;
    const height = 300 - margin.top - margin.bottom;

    const svg = d3.select(`#${containerId}`)
        .append("svg")
        .attr("width", width + margin.left + margin.right)
        .attr("height", height + margin.top + margin.bottom)
        .append("g")
        .attr("transform", `translate(${margin.left},${margin.top})`);

    // Definir el gradiente
    const defs = svg.append("defs");
    const linearGradient = defs.append("linearGradient")
        .attr("id", `${containerId}-gradient`) // ID único para cada gradiente
        .attr("x1", "0%")
        .attr("y1", "0%")
        .attr("x2", "0%")
        .attr("y2", "100%"); // Gradiente vertical

    linearGradient.append("stop")
        .attr("offset", "0%")
        .attr("stop-color", "#6366F1"); // Tailwind indigo-500

    linearGradient.append("stop")
        .attr("offset", "100%")
        .attr("stop-color", "#4338CA"); // Tailwind indigo-700

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
        .attr("transform", "rotate(-45)") // Rotar labels para mejor visibilidad
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
        .attr("fill", `url(#${containerId}-gradient)`); // Usar el gradiente único

    // X-axis label
    svg.append("text")
        .attr("class", "x label")
        .attr("text-anchor", "middle")
        .attr("x", width / 2)
        .attr("y", height + margin.bottom - 5)
        .style("font-size", "14px") // Aumentar tamaño de fuente
        .text(xLabel);

    // Y-axis label
    svg.append("text")
        .attr("class", "y label")
        .attr("text-anchor", "middle")
        .attr("y", -margin.left + 20)
        .attr("x", -height / 2)
        .attr("dy", ".75em")
        .attr("transform", "rotate(-90)")
        .style("font-size", "14px") // Aumentar tamaño de fuente
        .text(yLabel);

    // Chart title
    svg.append("text")
        .attr("x", (width / 2))
        .attr("y", 0 - (margin.top / 2))
        .attr("text-anchor", "middle")
        .style("font-size", "18px") // Aumentar tamaño de fuente
        .style("font-weight", "bold")
        .text(title);
}


export async function statistics(root) {
    root.innerHTML = ''; // Clear previous content
    root.appendChild(createLoadingSpinner()); // Show loading spinner

    try {
        // Fetch all necessary data concurrently
        const [allBlocks, latestBlock, pendingTxCountData] = await Promise.all([
            api.fetchAllBlocks(), // Uses the new helper to get all blocks
            api.latest(),
            api.txCount().catch(e => ({ count: 'Error' })) // Handle error gracefully for txCount
        ]);

        root.innerHTML = ''; // Clear spinner once data is fetched

        if (allBlocks.length === 0) {
            root.append(createEl('div', { className: 'bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative' },
                createEl('strong', { className: 'font-bold' }, 'Información: '),
                createEl('span', { className: 'block sm:inline' }, 'No hay datos de bloques disponibles para generar estadísticas.')
            ));
            return;
        }

        // Sort blocks by index for accurate time calculations
        allBlocks.sort((a, b) => a.index - b.index);

        // --- Calculate Core Statistics ---
        const totalBlocks = allBlocks.length;
        let totalTransactionsMined = 0;
        let totalNonce = 0;
        let totalTransactionAmount = 0;
        let totalBlockTime = 0;
        let blockIntervals = [];
        let minTxsInBlock = Infinity;
        let maxTxsInBlock = 0;

        let totalFirstTxDelay = 0;
        let minFirstTxDelay = Infinity;
        let maxFirstTxDelay = 0;
        let blocksWithCalculableDelay = 0; // Para contar bloques con TXs y delay válido
        const firstTxDelayData = []; // Para el nuevo gráfico

        // Data for charts
        const txsPerBlockData = []; // [{label: blockIndex, value: txCount}] for last N blocks
        const blocksPerDay = {}; // {date: count}
        const txsPerDay = {}; // {date: count}

        for (let i = 0; i < totalBlocks; i++) {
            const block = allBlocks[i];
            const blockDate = new Date(block.timestamp * 1000).toLocaleDateString('es-AR', { year: 'numeric', month: '2-digit', day: '2-digit' });

            totalTransactionsMined += block.data.length;
            totalNonce += block.nonce;

            if (block.data.length < minTxsInBlock) minTxsInBlock = block.data.length;
            if (block.data.length > maxTxsInBlock) maxTxsInBlock = block.data.length;

            // Aggregate data for transactions per block chart (last 20 blocks)
            // Aseguramos que se tomen los últimos 20 bloques disponibles
            if (i >= totalBlocks - 20) {
                txsPerBlockData.push({ label: `Bloque ${block.index}`, value: block.data.length });
            }

            // Calculate First Transaction Delay
            if (block.data && block.data.length > 0) {
                // Se ordena las transacciones por su timestamp para encontrar la "primera"
                // Luego se calcula el retraso: tiempo del bloque - tiempo de la primera tx.
                const sortedTxs = [...block.data].sort((a, b) => a.timestamp - b.timestamp);
                const firstTxTimestamp = sortedTxs[0].timestamp;
                const delay = block.timestamp - firstTxTimestamp;
                
                if (delay >= 0) { // Considerar solo retrasos no negativos para un significado lógico de "retraso"
                    totalFirstTxDelay += delay;
                    if (delay < minFirstTxDelay) minFirstTxDelay = delay;
                    if (delay > maxFirstTxDelay) maxFirstTxDelay = delay;
                    blocksWithCalculableDelay++;

                    // Para el nuevo gráfico de Retraso de la Primer TX (últimos 20 bloques)
                    // Aseguramos que se tomen los últimos 20 bloques con TXs disponibles
                    if (i >= totalBlocks - 20) {
                        firstTxDelayData.push({ label: `Bloque ${block.index}`, value: delay });
                    }
                }
            }


            // Aggregate data for blocks per day chart
            blocksPerDay[blockDate] = (blocksPerDay[blockDate] || 0) + 1;

            // Aggregate data for transactions per day chart
            txsPerDay[blockDate] = (txsPerDay[blockDate] || 0) + block.data.length;

            block.data.forEach(tx => {
                totalTransactionAmount += tx.amount;
            });

            if (i > 0) {
                const prevBlock = allBlocks[i - 1];
                const interval = block.timestamp - prevBlock.timestamp;
                if (interval > 0) { // Only consider positive intervals
                    blockIntervals.push(interval);
                    totalBlockTime += interval;
                }
            }
        }

        const avgBlockTime = blockIntervals.length > 0 ? totalBlockTime / blockIntervals.length : 0;
        const avgTxsPerBlock = totalBlocks > 0 ? totalTransactionsMined / totalBlocks : 0;
        const avgNoncePerBlock = totalBlocks > 0 ? totalNonce / totalBlocks : 0;
        const timeSinceLastBlock = latestBlock ? (Date.now() / 1000) - latestBlock.timestamp : 0; // Current time in seconds
        const avgTransactionAmount = totalTransactionsMined > 0 ? totalTransactionAmount / totalTransactionsMined : 0;
        // El promedio del retraso de la primera TX solo debe considerar los bloques con transacciones
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


        // --- Render Statistics ---
        const statsGrid = createEl('div', { className: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3 gap-6 mb-8' });

        // Card: Total Blocks
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-blue-100 text-blue-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-layer-group text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Total de Bloques')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, totalBlocks.toLocaleString())
        ));

        // Card: Average Block Time
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-indigo-100 text-indigo-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-clock text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Tiempo Promedio por Bloque')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, formatDuration(avgBlockTime)),
            createEl('p', { className: 'text-sm text-gray-500' }, `Basado en ${blockIntervals.length.toLocaleString()} intervalos.`)
        ));

        // Card: Time Since Last Block
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-yellow-100 text-yellow-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-history text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Tiempo Desde Último Bloque')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, formatDuration(timeSinceLastBlock))
        ));

        // Card: Total Transactions Mined
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-green-100 text-green-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-exchange-alt text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Transacciones Totales Minadas')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, totalTransactionsMined.toLocaleString())
        ));

        // Card: Average Transactions per Block
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-teal-100 text-teal-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-file-invoice-dollar text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'TXs Promedio por Bloque')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, avgTxsPerBlock.toFixed(2))
        ));

        // Card: Average Nonce per Block
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-pink-100 text-pink-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-fingerprint text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Nonce Promedio por Bloque')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, avgNoncePerBlock.toFixed(0))
        ));

        // Card: Current Pending Transactions
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-orange-100 text-orange-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-tasks text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'TXs Pendientes Actuales')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, pendingTxCount)
        ));

        // New Card: Average Transaction Value
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-purple-100 text-purple-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-money-bill-wave text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Valor Promedio de TX')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, `${avgTransactionAmount.toFixed(2)}`)
        ));

        // New Card: Smallest Block (by TXs)
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-red-100 text-red-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-minus-circle text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Bloque Más Pequeño (TXs)')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, minTxsInBlock.toLocaleString()),
             minTxsInBlock === Infinity ? createEl('p', { className: 'text-sm text-gray-500' }, '(N/A si no hay TXs)') : null // Mostrar N/A si no hay transacciones para calcular
        ));

        // New Card: Largest Block (by TXs)
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-blue-100 text-blue-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-plus-circle text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Bloque Más Grande (TXs)')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, maxTxsInBlock.toLocaleString())
        ));

        // New Card: Average First TX Delay
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-cyan-100 text-cyan-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-hourglass-start text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Retraso Promedio Primera TX')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, formatDuration(avgFirstTxDelay)),
            createEl('p', { className: 'text-sm text-gray-500' }, 'Valores altos pueden indicar un desfase de reloj (cliente vs. servidor).') // Aclaración más explícita
        ));

        // New Card: Minimum First TX Delay
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-lime-100 text-lime-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-bolt text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Retraso Mínimo Primera TX')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, minFirstTxDelay === Infinity ? 'N/A' : formatDuration(minFirstTxDelay))
        ));

        // New Card: Maximum First TX Delay
        statsGrid.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3' },
            createEl('div', { className: 'flex items-center space-x-3' },
                createEl('div', { className: 'flex-shrink-0 bg-rose-100 text-rose-600 rounded-full p-3' },
                    createEl('i', { className: 'fas fa-exclamation-triangle text-xl' })
                ),
                createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Retraso Máximo Primera TX')
            ),
            createEl('p', { className: 'text-4xl font-bold text-gray-900' }, maxFirstTxDelay === 0 ? 'N/A' : formatDuration(maxFirstTxDelay))
        ));


        root.append(createEl('h1', { className: 'text-3xl font-bold text-gray-900 mb-8' }, 'Estadísticas de la Red Blockchain'), statsGrid);

        // --- Charts Section ---
        const chartsSection = createEl('div', { className: 'grid grid-cols-1 lg:grid-cols-2 gap-6 mt-10' });

        // Chart 1: Transactions per Block (Last 20 Blocks)
        const txsPerBlockChartContainer = createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' });
        txsPerBlockChartContainer.append(createEl('h3', { className: 'text-xl font-bold text-gray-800 mb-4' }, 'Transacciones por Bloque (Últimos Bloques Minados)')); // Título ajustado
        const txsChartDivId = 'txs-per-block-chart';
        txsPerBlockChartContainer.append(createEl('div', { id: txsChartDivId, className: 'flex justify-center items-center' }));
        chartsSection.append(txsPerBlockChartContainer);

        // Chart 2: Blocks Mined per Day (Last 7 Days)
        const blocksPerDayChartContainer = createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' });
        blocksPerDayChartContainer.append(createEl('h3', { className: 'text-xl font-bold text-gray-800 mb-4' }, 'Bloques Minados por Día (Últimos 7 Días)'));
        const blocksChartDivId = 'blocks-per-day-chart';
        blocksPerDayChartContainer.append(createEl('div', { id: blocksChartDivId, className: 'flex justify-center items-center' }));
        chartsSection.append(blocksPerDayChartContainer);

        // Chart 3: First Transaction Delay (Last 20 Blocks)
        const firstTxDelayChartContainer = createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' });
        firstTxDelayChartContainer.append(createEl('h3', { className: 'text-xl font-bold text-gray-800 mb-4' }, 'Retraso Primera TX (Últimos Bloques Minados)')); // Título ajustado
        const firstTxDelayChartDivId = 'first-tx-delay-chart';
        firstTxDelayChartContainer.append(createEl('div', { id: firstTxDelayChartDivId, className: 'flex justify-center items-center' }));
        chartsSection.append(firstTxDelayChartContainer);


        root.append(chartsSection);

        if (typeof d3 !== 'undefined') {
            createBarChart(txsChartDivId, txsPerBlockData, 'Transacciones por Bloque', 'Bloque', 'Número de Transacciones');
            createBarChart(blocksChartDivId, dailyBlocksChartData, 'Bloques por Día', 'Fecha', 'Número de Bloques');
            createBarChart(firstTxDelayChartDivId, firstTxDelayData, 'Retraso Primera TX', 'Bloque', 'Tiempo (segundos)');
        } else {
            console.warn("D3.js no está cargado. Los gráficos no se mostrarán.");
            root.append(createEl('div', { className: 'bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative mt-4' }, 'Advertencia: La librería de gráficos (D3.js) no se ha cargado. Asegúrate de incluirla en tu HTML.'));
        }

    } catch (error) {
        root.innerHTML = '';
        root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' },
            createEl('strong', { className: 'font-bold' }, 'Error: '),
            createEl('span', { className: 'block sm:inline' }, 'No se pudieron cargar las estadísticas. Asegúrate de que la API esté funcionando correctamente y que haya bloques disponibles.')
        ));
        console.error('Error in statistics component:', error);
    }
}
