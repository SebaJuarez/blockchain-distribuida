import { api, getApiBaseUrl } from '../services/api.js';
import { render, html } from '../lib/preact-standalone.js';
import { truncateHash, showToast } from '../utils/dom.js';
import { getWalletAddress } from '../utils/crypto.js';
import { spinner, errorBox } from '../utils/ui.js';

const DASHBOARD_POLL_MS = 5000;

export async function dashboard(root) {
    if (root._dashTimer) {
        clearInterval(root._dashTimer);
        root._dashTimer = null;
    }
    render(spinner(), root);

    try {
        let allBlocksData, latestBlock, pendingTxCountData, poolStatusData;
        try {
            [allBlocksData, latestBlock, pendingTxCountData, poolStatusData] = await Promise.all([
                api.allBlocks(0, 1),
                api.latest(),
                api.txCount().catch(() => ({ count: 0 })),
                api.poolStatus().catch(() => null)
            ]);
        } catch (e) {
            render(errorBox(`No se pudo conectar con el coordinador (${getApiBaseUrl()}). ¿Está corriendo?`), root);
            return;
        }

        let systemConfig = {};
        let myBalance = { balance: 0 };
        let currentDifficulty = 'N/A';
        let systemRemainingValue = null;
        try { systemConfig = await api.config(); } catch (e) { console.log('Config no disponible'); }
        try { myBalance = await api.getBalance(getWalletAddress()); } catch (e) { console.log('Balance no disponible'); }
        try { currentDifficulty = await api.getDifficulty(); } catch (e) { console.log('Dificultad no disponible'); }
        try {
            const fundsStatus = await api.fundsStatus();
            systemRemainingValue = fundsStatus.systemRemaining;
        } catch (e) { console.log('Estado de fondos no disponible'); }

        const totalBlocks = allBlocksData.page ? allBlocksData.page.totalElements : (allBlocksData._embedded?.blockList?.length || 0);
        const pendingTxCount = pendingTxCountData.count !== undefined ? pendingTxCountData.count.toLocaleString() : '0';
        const miningInCandidate = poolStatusData ? (poolStatusData.inCandidateBlock || 0) : null;
        const queuedInPool = poolStatusData ? (poolStatusData.queued || 0) : null;

        const isTesting = systemConfig.mode === 'testing';
        const isGenesis = systemConfig.rewardSource === 'genesis';
        const genesisSupply = Number(systemConfig.genesisSupply) || 0;
        const systemRemaining = isGenesis && systemRemainingValue !== null ? Number(systemRemainingValue).toLocaleString() : 'N/A';
        const baseReward = systemConfig.baseReward || 50;
        const halvingInterval = systemConfig.halvingInterval || 10;
        const currentHalving = Math.floor(totalBlocks / halvingInterval);
        const currentReward = isGenesis ? (baseReward / Math.pow(2, currentHalving)) : 20;
        const nextHalvingBlock = (currentHalving + 1) * halvingInterval;
        const blocksUntilHalving = Math.max(0, nextHalvingBlock - totalBlocks);
        const supplyMined = isGenesis && genesisSupply > 0 && systemRemainingValue !== null
            ? (genesisSupply - Number(systemRemainingValue)).toLocaleString()
            : (totalBlocks * currentReward).toLocaleString();

        const modeColor = isTesting ? 'bg-orange-100 text-orange-800 border-orange-300' : 'bg-green-100 text-green-800 border-green-300';

        // Recent blocks (newest first gracias a la paginación del backend)
        let recentBlocks = [];
        try {
            const recent = await api.allBlocks(0, 5);
            recentBlocks = recent._embedded?.blockList || [];
            recentBlocks.sort((a, b) => b.index - a.index);
        } catch (e) { console.log('No se pudieron cargar bloques recientes'); }

        const walletAddress = getWalletAddress();

        render(html`
            ${systemConfig.mode ? html`
                <div class="mb-6 p-4 rounded-lg border ${modeColor} flex items-center justify-between">
                    <div class="flex items-center space-x-3">
                        <i class="fas ${isTesting ? 'fa-flask' : 'fa-shield-alt'} text-xl"></i>
                        <div>
                            <span class="font-bold text-lg">Modo: ${systemConfig.mode.toUpperCase()}</span>
                            <p class="text-sm opacity-80">${isTesting ? 'Modo prueba: validación libre.' : 'Validación de saldo activa.'}</p>
                        </div>
                    </div>
                    ${isGenesis ? html`
                        <div class="text-right">
                            <p class="text-sm font-semibold">Fondo Restante</p>
                            <p class="text-2xl font-bold">${systemRemaining}</p>
                        </div>` : ''}
                </div>` : ''}

            ${isTesting ? html`
                <div class="bg-gradient-to-r from-orange-50 to-yellow-50 p-6 rounded-xl mb-6 border-2 border-orange-200 border-dashed">
                    <div class="flex items-center justify-between flex-wrap gap-2">
                        <div>
                            <h3 class="text-lg font-bold text-orange-800">🚰 Fondo de Pruebas</h3>
                            <p class="text-sm text-orange-700">Cargá saldo a tu wallet para el demo.</p>
                        </div>
                        <button class="px-6 py-3 bg-orange-500 text-white font-bold rounded-lg hover:bg-orange-600 transition-transform hover:scale-105"
                                onClick=${async (e) => {
                                    const btn = e.target;
                                    btn.disabled = true; btn.textContent = '...';
                                    try {
                                        const res = await api.loadFunds({ publicKey: walletAddress, amount: 10000 });
                                        btn.textContent = `+${Number(res.amount).toLocaleString()} ✓`;
                                        showToast(`Cargados ${Number(res.amount).toLocaleString()} a tu wallet`, 'success');
                                        setTimeout(() => { btn.disabled = false; btn.textContent = 'Cargar 10,000'; dashboard(root); }, 800);
                                    } catch (err) {
                                        btn.textContent = 'Error al cargar saldo';
                                        showToast('Error al cargar fondos', 'error');
                                        setTimeout(() => { btn.disabled = false; btn.textContent = 'Cargar 10,000'; }, 3000);
                                    }
                                }}>
                            Cargar 10,000
                        </button>
                    </div>
                </div>` : ''}

            <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                ${statCard('fas fa-cube', 'bg-blue-100 text-blue-600', 'Último Bloque', `#${latestBlock.index}`,
                    html`<span class="text-xs font-mono text-gray-500 break-all">${truncateHash(latestBlock.hash, 20)}</span>
                         <a href="#blocks/${latestBlock.hash}" class="text-blue-500 text-sm hover:underline">Ver detalles</a>`)}
                ${statCard('fas fa-layer-group', 'bg-green-100 text-green-600', 'Total Bloques', totalBlocks.toLocaleString())}
                ${poolStatusData
                    ? html`
                        ${statCard('fas fa-hourglass-half', 'bg-blue-100 text-blue-600', 'En Cola', queuedInPool.toLocaleString(),
                            html`<span class="text-xs text-gray-500">Esperando ser incluidas en un bloque</span>`)}
                        ${statCard('fas fa-hammer', 'bg-amber-100 text-amber-600', 'Minándose', miningInCandidate.toLocaleString(),
                            html`<span class="text-xs text-gray-500">En el bloque candidato actual</span>`)}`
                    : statCard('fas fa-hourglass-half', 'bg-yellow-100 text-yellow-600', 'Pendientes', pendingTxCount)}
                ${statCard('fas fa-wallet', 'bg-purple-100 text-purple-600', 'Tu Balance',
                    Number(myBalance.balance || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 }),
                    html`<span class="text-xs text-gray-400 font-mono" title="${walletAddress}">${truncateHash(walletAddress, 16)}</span>`)}
                ${statCard('fas fa-coins', 'bg-indigo-100 text-indigo-600', 'Recompensa', currentReward.toFixed(2),
                    isGenesis ? `Halving #${currentHalving}: próx en ${blocksUntilHalving} bloques` : '')}
                ${statCard('fas fa-list-alt', 'bg-pink-100 text-pink-600', 'TXs Último Bloque', (latestBlock.data || []).length.toLocaleString())}
                ${statCard('fas fa-fingerprint', 'bg-teal-100 text-teal-600', 'Dificultad', currentDifficulty || 'N/A')}
                ${statCard('fas fa-tachometer-alt', 'bg-cyan-100 text-cyan-600', 'Supply', supplyMined)}
            </div>

            <div class="bg-white p-6 rounded-xl shadow-lg mt-6">
                <h2 class="text-xl font-bold mb-4 border-b pb-2">Bloques Recientes</h2>
                <div class="space-y-3">
                    ${recentBlocks.length === 0
                        ? html`<p class="text-gray-500">No se pudieron cargar bloques recientes.</p>`
                        : recentBlocks.map(b => {
                            const isReward = b.data && b.data.length === 1 && (b.data[0].sender === 'system' || b.data[0].sender === systemConfig.systemAddress);
                            return html`
                                <a href="#blocks/${b.hash}" class="border rounded p-3 hover:bg-gray-50 flex justify-between items-center no-underline">
                                    <div>
                                        <span class="font-bold text-blue-600">#${b.index}</span>
                                        ${isReward ? html`<span class="ml-2 px-2 py-0.5 bg-yellow-100 text-yellow-700 text-xs rounded-full font-bold">RECOMPENSA</span>` : ''}
                                        <span class="text-xs text-gray-500 ml-2 font-mono" title="${b.hash}">${truncateHash(b.hash, 16)}</span>
                                    </div>
                                    <span class="text-sm text-gray-600">${(b.data || []).length} TXs</span>
                                </a>`;
                        })}
                </div>
            </div>
        `, root);

        // Polling en vivo (pausa en pestaña oculta / cuando se navega fuera)
        root._dashTimer = setInterval(async () => {
            if (document.hidden) return;
            if (location.hash.split('/')[0] !== 'dashboard') {
                clearInterval(root._dashTimer);
                root._dashTimer = null;
                return;
            }
            await dashboard(root);
        }, DASHBOARD_POLL_MS);

    } catch (error) {
        render(errorBox(error.message || 'No se pudo cargar el dashboard.'), root);
        console.error('Dashboard error:', error);
    }
}

function statCard(icon, iconBgClass, title, value, footer) {
    return html`
        <div class="bg-white p-6 rounded-lg shadow-md">
            <div class="flex items-center space-x-3 mb-2">
                <div class="${iconBgClass} rounded-full p-3"><i class="${icon}"></i></div>
                <h3 class="text-lg font-semibold text-gray-700">${title}</h3>
            </div>
            <p class="text-2xl font-bold">${value}</p>
            ${footer ? html`<div class="mt-1">${footer}</div>` : ''}
        </div>`;
}
