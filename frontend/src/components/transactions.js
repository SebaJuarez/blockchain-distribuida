import { api } from '../services/api.js';
import { render, html } from '../lib/preact-standalone.js';
import { shortenId, copyToClipboard, generateRandomAddress, showToast } from '../utils/dom.js';
import { signTransaction, getWalletAddress, listWallets, addWallet, switchWallet, removeWallet, generateNewWallet } from '../utils/crypto.js';
import { spinner, errorBox, sectionTitle } from '../utils/ui.js';

const POLL_INTERVAL_MS = 5000;
const MASS_DELAYS = [['0', 'Sin espera'], ['100', '100 ms'], ['250', '250 ms'], ['500', '500 ms'], ['1000', '1 s'], ['2000', '2 s'], ['5000', '5 s']];

export async function transactions(root) {
    if (root._txPollTimer) {
        clearInterval(root._txPollTimer);
        root._txPollTimer = null;
    }

    render(spinner(), root);

    let currentDifficulty = 'Cargando...';
    let myBalance = 0;
    let systemConfig = {};
    let pendingTransactions = [];
    let miningTransactions = [];
    let queuedTransactions = [];
    let poolStatus = { inCandidateBlock: 0, queued: 0, total: 0, candidateTransactionIds: [] };
    let isTesting = false;
    let isGenesis = false;
    let walletAddress = getWalletAddress();

    async function updateDifficultyDisplay() {
        try {
            currentDifficulty = await api.getDifficulty();
        } catch (error) {
            console.error('Error al obtener la dificultad:', error);
            currentDifficulty = 'Error';
        }
        const el = document.getElementById('current-difficulty-display');
        if (el) el.textContent = currentDifficulty || 'N/A';
    }

    async function refreshBalance() {
        try {
            const res = await api.getBalance(getWalletAddress());
            myBalance = res.balance || 0;
            const el = document.getElementById('my-balance-display');
            if (el) el.textContent = Number(myBalance).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        } catch (e) {
            console.error('Error leyendo balance:', e);
        }
    }

    function getActiveWalletName() {
        const wallets = listWallets();
        const active = wallets.find(w => w.active) || wallets[0];
        return (active && active.name) ? active.name : shortenId(walletAddress, 12, 8);
    }

    function updateWalletHeader() {
        walletAddress = getWalletAddress();
        const el = document.getElementById('wallet-address-display');
        if (el) {
            el.textContent = shortenId(walletAddress, 12, 8);
            el.title = walletAddress;
        }
        const nameEl = document.getElementById('wallet-name-display');
        if (nameEl) nameEl.textContent = getActiveWalletName();
        refreshBalance();
    }

    // ---- Wallet Modal ----
    function renderWalletModal(modalRoot) {
        const wallets = listWallets();

        render(html`
            <div class="space-y-2 max-h-64 overflow-y-auto pr-1 mb-4">
                ${wallets.length === 0
                    ? html`<p class="text-gray-500 italic text-sm">No hay wallets guardadas.</p>`
                    : wallets.map(w => html`
                        <div class="flex items-center justify-between p-3 rounded-lg border ${w.active ? 'border-blue-400 bg-blue-50' : 'border-gray-200 hover:bg-gray-50'}">
                            <div class="min-w-0">
                                <div class="flex items-center space-x-2">
                                    <span class="font-semibold text-sm">${w.name}</span>
                                    ${w.active ? html`<span class="px-2 py-0.5 bg-blue-600 text-white text-xs rounded-full font-bold">ACTIVA</span>` : ''}
                                </div>
                                <div class="flex items-center space-x-1 mt-1">
                                    <span class="font-mono text-xs text-gray-500" title="${w.publicKeyHex}">${shortenId(w.publicKeyHex, 10, 8)}</span>
                                    <button class="text-gray-400 hover:text-gray-600" title="Copiar dirección"
                                            onClick=${() => copyToClipboard(w.publicKeyHex)}><i class="fas fa-copy text-xs"></i></button>
                                    <button class="text-gray-400 hover:text-gray-600" title="Copiar clave privada"
                                            onClick=${() => copyToClipboard(w.privHex)}><i class="fas fa-key text-xs"></i></button>
                                </div>
                            </div>
                            <div class="flex items-center space-x-2 flex-shrink-0">
                                ${!w.active ? html`
                                    <button class="px-3 py-1 bg-blue-600 text-white text-xs rounded-lg hover:bg-blue-700"
                                            onClick=${() => {
                                                switchWallet(w.id);
                                                renderWalletModal(modalRoot);
                                                updateWalletHeader();
                                                showToast(`Wallet "${w.name}" activada`, 'success');
                                            }}>Usar</button>` : ''}
                                ${wallets.length > 1 ? html`
                                    <button class="px-3 py-1 bg-red-100 text-red-600 text-xs rounded-lg hover:bg-red-200"
                                            title="Eliminar wallet"
                                            onClick=${() => {
                                                if (w.active) {
                                                    showToast('No se puede borrar la wallet activa. Cambiá primero.', 'warning');
                                                    return;
                                                }
                                                removeWallet(w.id);
                                                renderWalletModal(modalRoot);
                                                showToast(`Wallet "${w.name}" eliminada`, 'info');
                                            }}>
                                        <i class="fas fa-trash"></i>
                                    </button>` : ''}
                            </div>
                        </div>
                    `)}
            </div>

            <div class="mb-3">
                <label class="block text-sm font-medium text-gray-700 mb-1">Agregar wallet con clave privada</label>
                <div class="flex space-x-2">
                    <input id="wallet-add-pk" type="text" placeholder="Clave privada (hex)"
                           class="flex-1 p-2 border border-gray-300 rounded-lg font-mono text-sm focus:ring-2 focus:ring-blue-500" />
                    <input id="wallet-add-name" type="text" placeholder="Nombre (opcional)" class="w-32 p-2 border border-gray-300 rounded-lg text-sm" />
                    <button class="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-semibold text-sm"
                            onClick=${() => {
                                const pkInput = document.getElementById('wallet-add-pk');
                                const nameInput = document.getElementById('wallet-add-name');
                                try {
                                    const wallet = addWallet(pkInput.value, nameInput.value);
                                    pkInput.value = '';
                                    nameInput.value = '';
                                    renderWalletModal(modalRoot);
                                    updateWalletHeader();
                                    showToast(`Wallet "${wallet.name}" activada`, 'success');
                                } catch (err) {
                                    showToast(err.message, 'error');
                                }
                            }}>Agregar y usar</button>
                </div>
            </div>

            <button class="w-full px-4 py-2 bg-gray-200 text-gray-800 rounded-lg hover:bg-gray-300 font-semibold text-sm mb-3"
                    onClick=${() => {
                        const wallet = generateNewWallet();
                        renderWalletModal(modalRoot);
                        updateWalletHeader();
                        showToast(`Nueva wallet "${wallet.name}" generada y activada`, 'success');
                    }}>
                <i class="fas fa-plus mr-1"></i>Generar nueva wallet
            </button>

            <p class="text-xs text-gray-400">Las claves se guardan solo en este navegador (localStorage). No compartas tu clave privada.</p>
        `, modalRoot);
    }

    function openWalletModal() {
        const overlay = createOverlay();
        const modalRoot = document.createElement('div');
        overlay.querySelector('.modal-body').append(modalRoot);
        renderWalletModal(modalRoot);
    }

    function createOverlay() {
        const overlay = document.createElement('div');
        overlay.className = 'fixed inset-0 z-40 flex items-center justify-center bg-black bg-opacity-50 p-4';
        const modal = document.createElement('div');
        modal.className = 'bg-white rounded-xl shadow-2xl max-w-lg w-full p-6 max-h-[85vh] overflow-y-auto';
        const header = document.createElement('div');
        header.className = 'flex items-center justify-between mb-4';
        header.innerHTML = '<h3 class="text-xl font-bold text-gray-900">Cambiar Wallet</h3>';
        const closeBtn = document.createElement('button');
        closeBtn.className = 'p-2 text-gray-500 hover:text-gray-700';
        closeBtn.innerHTML = '<i class="fas fa-times text-xl"></i>';
        closeBtn.addEventListener('click', () => overlay.remove());
        header.append(closeBtn);
        const body = document.createElement('div');
        body.className = 'modal-body';
        modal.append(header, body);
        overlay.append(modal);
        overlay.addEventListener('click', (e) => { if (e.target === overlay) overlay.remove(); });
        document.body.append(overlay);
        return overlay;
    }

    // ---- Pending list ----
    function applyPoolSplit() {
        const miningIds = new Set(poolStatus.candidateTransactionIds || []);
        miningTransactions = pendingTransactions.filter(tx => miningIds.has(tx.id));
        queuedTransactions = pendingTransactions.filter(tx => !miningIds.has(tx.id));
    }

    function renderTxTable(txs) {
        return html`
            <div class="table-responsive">
                <table class="min-w-full divide-y divide-gray-200 text-sm">
                    <thead class="bg-gray-50">
                        <tr>
                            <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">ID</th>
                            <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Remitente</th>
                            <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Receptor</th>
                            <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Monto</th>
                            <th class="px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase">Hora</th>
                        </tr>
                    </thead>
                    <tbody class="bg-white divide-y divide-gray-200">
                        ${txs.map(tx => html`
                            <tr class="hover:bg-gray-50">
                                <td class="px-4 py-2 font-mono text-xs text-blue-600">
                                    <a href="#transactions/${tx.id}" class="hover:underline" title="${tx.id}">${shortenId(tx.id, 6, 4)}</a>
                                </td>
                                <td class="px-4 py-2 font-mono text-xs" title="${tx.sender}">${shortenId(tx.sender, 6, 4)}</td>
                                <td class="px-4 py-2 font-mono text-xs" title="${tx.receiver}">${shortenId(tx.receiver, 6, 4)}</td>
                                <td class="px-4 py-2 font-semibold">${tx.amount.toLocaleString()}</td>
                                <td class="px-4 py-2 text-gray-500 text-xs">${new Date(tx.timestamp * 1000).toLocaleTimeString()}</td>
                            </tr>
                        `)}
                    </tbody>
                </table>
            </div>`;
    }

    function renderPendingList(container) {
        render(html`
            <div class="grid grid-cols-1 md:grid-cols-2 gap-4 mb-5">
                <div class="flex items-center space-x-3 bg-amber-50 border border-amber-200 rounded-lg p-4">
                    <div class="w-11 h-11 rounded-full bg-amber-100 text-amber-600 flex items-center justify-center flex-shrink-0">
                        <i class="fas fa-hammer"></i>
                    </div>
                    <div>
                        <p class="text-xs text-amber-700 uppercase font-bold">Minándose</p>
                        <p class="text-2xl font-bold text-amber-800">${miningTransactions.length}</p>
                        <p class="text-xs text-amber-600">Ya entraron al bloque candidato</p>
                    </div>
                </div>
                <div class="flex items-center space-x-3 bg-blue-50 border border-blue-200 rounded-lg p-4">
                    <div class="w-11 h-11 rounded-full bg-blue-100 text-blue-600 flex items-center justify-center flex-shrink-0">
                        <i class="fas fa-hourglass-half"></i>
                    </div>
                    <div>
                        <p class="text-xs text-blue-700 uppercase font-bold">En Cola</p>
                        <p class="text-2xl font-bold text-blue-800">${queuedTransactions.length}</p>
                        <p class="text-xs text-blue-600">Esperando ser incluidas</p>
                    </div>
                </div>
            </div>

            <h3 class="text-sm font-bold text-amber-700 uppercase tracking-wide mb-2 flex items-center space-x-2">
                <i class="fas fa-hammer"></i>
                <span>Minándose (${miningTransactions.length})</span>
            </h3>
            ${miningTransactions.length === 0
                ? html`<p class="text-gray-500 italic text-sm mb-4">No hay transacciones siendo minadas en este momento.</p>`
                : renderTxTable(miningTransactions)}

            <h3 class="text-sm font-bold text-blue-700 uppercase tracking-wide mt-6 mb-2 flex items-center space-x-2">
                <i class="fas fa-hourglass-half"></i>
                <span>En Cola (${queuedTransactions.length})</span>
            </h3>
            ${queuedTransactions.length === 0
                ? html`<p class="text-gray-500 italic text-sm">No hay transacciones en cola.</p>`
                : renderTxTable(queuedTransactions)}
        `, container);
    }

    async function refreshPendingList() {
        if (document.hidden) return;
        try {
            const [txData, status] = await Promise.all([
                api.txs(),
                api.poolStatus().catch(() => null)
            ]);
            pendingTransactions = txData._embedded ? txData._embedded.transactionList : [];
            if (status) poolStatus = status;
            applyPoolSplit();
            const container = document.getElementById('pending-tx-table-container');
            const countEl = document.getElementById('pending-tx-count');
            if (countEl) countEl.textContent = `Transacciones Pendientes (${pendingTransactions.length})`;
            if (container) renderPendingList(container);
        } catch (e) {
            console.error('Error refrescando transacciones pendientes:', e);
        }
    }

    // ---- Mass Generator ----
    function buildMassGenerator() {
        let cancelFlag = false;

        const stateEl = () => document.getElementById('mass-status');
        const fillEl = () => document.getElementById('mass-progress-fill');
        const progressWrapEl = () => document.getElementById('mass-progress-wrap');

        function setProgress(pct, text) {
            const fill = fillEl();
            if (fill) fill.style.width = pct + '%';
            const status = stateEl();
            if (status) status.textContent = text;
        }

        function setRunning(running) {
            const startBtn = document.getElementById('mass-start-btn');
            const cancelBtn = document.getElementById('mass-cancel-btn');
            if (startBtn) {
                startBtn.disabled = running;
                startBtn.innerHTML = running
                    ? '<i class="fas fa-spinner fa-spin mr-1"></i> Enviando...'
                    : '<i class="fas fa-play mr-1"></i> Generar y Enviar';
            }
            if (cancelBtn) cancelBtn.classList.toggle('hidden', !running);
        }

        async function runGenerator() {
            const n = Math.min(5000, Math.max(1, parseInt(document.getElementById('mass-count').value, 10) || 1));
            const delay = parseInt(document.getElementById('mass-delay').value, 10) || 0;
            const destMode = document.getElementById('mass-dest-mode').value;
            const fixedDest = document.getElementById('mass-dest-fixed').value.trim();
            const amountMode = document.getElementById('mass-amount-mode').value;
            const fixedAmount = parseFloat(document.getElementById('mass-amount-fixed').value);

            if (destMode === 'fixed' && !fixedDest) {
                showToast('Ingresá un destinatario fijo o usá "aleatorio".', 'warning');
                return;
            }
            if (amountMode === 'fixed' && (!fixedAmount || fixedAmount <= 0)) {
                showToast('Ingresá un monto fijo válido.', 'warning');
                return;
            }

            cancelFlag = false;
            setRunning(true);
            const wrap = progressWrapEl();
            if (wrap) wrap.classList.remove('hidden');
            setProgress(0, `Preparando 0/${n}...`);

            let sent = 0, failed = 0;
            for (let i = 1; i <= n; i++) {
                if (cancelFlag) break;
                const receiver = destMode === 'fixed' ? fixedDest : generateRandomAddress();
                const amount = amountMode === 'fixed' ? fixedAmount : Math.floor(Math.random() * 100) + 1;
                try {
                    const signed = await signTransaction(receiver, amount);
                    await api.createTx(signed);
                    sent++;
                } catch (err) {
                    failed++;
                    console.error('Error en TX masiva:', err);
                }
                setProgress(Math.round((i / n) * 100), `${i}/${n} procesadas · ${sent} enviadas · ${failed} errores${cancelFlag ? ' (cancelado)' : ''}`);
                if (i < n && delay > 0) await new Promise(r => setTimeout(r, delay));
            }

            setRunning(false);
            setProgress(100, `${sent} enviadas · ${failed} errores${cancelFlag ? ' (cancelado)' : ''}`);
            showToast(
                cancelFlag
                    ? `Cancelado: ${sent} enviadas, ${failed} errores`
                    : `Batch completado: ${sent} enviadas, ${failed} errores`,
                failed > 0 ? 'warning' : 'success'
            );
            setTimeout(() => { const wrap = progressWrapEl(); if (wrap) wrap.classList.add('hidden'); }, 3000);
            refreshPendingList();
        }

        return html`
            <div class="bg-white p-6 rounded-xl shadow-md mb-6">
                ${sectionTitle('Generador Masivo de Transacciones')}
                <p class="text-sm text-gray-600 mb-4">
                    Envía muchas transacciones de a una con una espera configurable entre cada una, ideal para pruebas de carga sin saturar la red.
                </p>
                <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-4">
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">Cantidad</label>
                        <input id="mass-count" type="number" value="10" min="1" max="5000" class="w-full p-2 border border-gray-300 rounded-lg" />
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">Espera entre TXs</label>
                        <select id="mass-delay" class="w-full p-2 border border-gray-300 rounded-lg bg-white">
                            ${MASS_DELAYS.map(([value, label]) => html`
                                <option value="${value}" ${value === '250' ? 'selected' : ''}>${label}</option>`)}
                        </select>
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">Destino</label>
                        <select id="mass-dest-mode" class="w-full p-2 border border-gray-300 rounded-lg bg-white mb-1">
                            <option value="random">Aleatorio</option>
                            <option value="fixed">Fijo</option>
                        </select>
                        <input id="mass-dest-fixed" type="text" placeholder="Public key fija"
                               class="w-full p-2 border border-gray-300 rounded-lg font-mono text-xs" style="display:none" />
                    </div>
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-1">Monto</label>
                        <select id="mass-amount-mode" class="w-full p-2 border border-gray-300 rounded-lg bg-white mb-1">
                            <option value="random">Aleatorio (1-100)</option>
                            <option value="fixed">Fijo</option>
                        </select>
                        <input id="mass-amount-fixed" type="number" min="0.01" step="0.01" placeholder="Monto fijo"
                               class="w-full p-2 border border-gray-300 rounded-lg" style="display:none" />
                    </div>
                </div>
                <div class="flex items-center space-x-2 mb-2">
                    <button id="mass-start-btn" class="px-6 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 font-semibold"
                            onClick=${runGenerator}><i class="fas fa-play mr-1"></i>Generar y Enviar</button>
                    <button id="mass-cancel-btn" class="hidden px-6 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 font-semibold"
                            onClick=${() => { cancelFlag = true; }}><i class="fas fa-stop mr-1"></i>Cancelar</button>
                </div>
                <div id="mass-progress-wrap" class="hidden mt-2">
                    <div class="w-full bg-gray-200 rounded-full h-3 overflow-hidden">
                        <div id="mass-progress-fill" class="bg-blue-600 h-3 rounded-full transition-all duration-200" style="width:0%"></div>
                    </div>
                    <div id="mass-status" class="text-sm text-gray-600 mt-1"></div>
                </div>
            </div>`;
    }

    // Toggle de campos fijos (event delegation sobre el documento es frágil, se conectan post-render)
    function wireFixedToggles() {
        const destMode = document.getElementById('mass-dest-mode');
        const destFixed = document.getElementById('mass-dest-fixed');
        const amountMode = document.getElementById('mass-amount-mode');
        const amountFixed = document.getElementById('mass-amount-fixed');
        if (destMode) destMode.addEventListener('change', () => { destFixed.style.display = destMode.value === 'fixed' ? '' : 'none'; });
        if (amountMode) amountMode.addEventListener('change', () => { amountFixed.style.display = amountMode.value === 'fixed' ? '' : 'none'; });
    }

    // ---- Balance by Public Key ----
    function buildBalanceCheck() {
        return html`
            <div class="bg-white p-6 rounded-xl shadow-md mb-6">
                ${sectionTitle('Consultar Balance de una Cuenta')}
                <p class="text-sm text-gray-600 mb-3">Ingresá una public key para ver su saldo en la red.</p>
                <div class="flex flex-col md:flex-row space-y-2 md:space-y-0 md:space-x-2">
                    <input id="balance-pk-input" type="text" placeholder="Public Key (ej: 04a1b2...) o SYSTEM_GENESIS"
                           class="flex-1 p-2 border border-gray-300 rounded-lg font-mono text-sm" />
                    <button class="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 font-semibold"
                            onClick=${async () => {
                                const pk = document.getElementById('balance-pk-input').value.trim();
                                if (!pk) { showToast('Ingresá una public key.', 'warning'); return; }
                                const resultBox = document.getElementById('balance-check-result');
                                render(spinner(), resultBox);
                                try {
                                    const res = await api.getBalance(pk);
                                    const balance = Number(res.balance || 0);
                                    render(html`
                                        <div class="bg-gray-50 border border-gray-200 rounded-lg p-4 flex items-center justify-between">
                                            <div>
                                                <p class="text-xs text-gray-500 uppercase font-bold">Balance</p>
                                                <p class="text-2xl font-bold text-gray-900">${balance.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</p>
                                                <p class="font-mono text-xs text-gray-500 mt-1" title="${pk}">${shortenId(pk, 10, 8)}</p>
                                            </div>
                                            <button class="px-3 py-1 text-xs bg-white border border-gray-300 rounded-lg hover:bg-gray-100"
                                                    onClick=${() => copyToClipboard(pk)}><i class="fas fa-copy mr-1"></i>Copiar PK</button>
                                        </div>`, resultBox);
                                } catch (err) {
                                    render(html`
                                        <div class="bg-yellow-100 border border-yellow-400 text-yellow-700 px-3 py-2 rounded text-sm">
                                            No se pudo obtener el balance de esa public key. Verificá que exista en la red.
                                        </div>`, resultBox);
                                }
                            }}>Consultar</button>
                </div>
                <div class="mt-2">
                    <button class="text-xs text-blue-600 hover:underline"
                            onClick=${() => { document.getElementById('balance-pk-input').value = getWalletAddress(); }}>Usar mi wallet activa</button>
                </div>
                <div id="balance-check-result" class="mt-3"></div>
            </div>`;
    }

    try {
        const [txData, configRes, statusRes] = await Promise.all([
            api.txs(),
            api.config().catch(() => ({})),
            api.poolStatus().catch(() => null)
        ]);
        systemConfig = configRes;
        pendingTransactions = txData._embedded ? txData._embedded.transactionList : [];
        if (statusRes) poolStatus = statusRes;
        applyPoolSplit();
        isTesting = systemConfig.mode === 'testing';
        isGenesis = systemConfig.rewardSource === 'genesis';

        await refreshBalance();

        render(html`
            <div class="bg-gradient-to-r from-blue-600 to-indigo-700 text-white p-6 rounded-xl shadow-lg mb-6">
                <div class="flex flex-col md:flex-row md:items-center md:justify-between">
                    <div>
                        <h2 id="wallet-name-display" class="text-2xl font-bold mb-1">${getActiveWalletName()}</h2>
                        <div class="flex items-center space-x-2 text-blue-100">
                            <span id="wallet-address-display" class="font-mono text-sm" title="${walletAddress}">${shortenId(walletAddress, 12, 8)}</span>
                            <button class="text-blue-200 hover:text-white transition-colors" title="Copiar dirección"
                                    onClick=${() => copyToClipboard(walletAddress)}><i class="fas fa-copy"></i></button>
                            <button class="text-blue-200 hover:text-white transition-colors flex items-center space-x-1 text-xs bg-white/20 hover:bg-white/30 px-2 py-1 rounded"
                                    onClick=${openWalletModal}><i class="fas fa-user-plus"></i><span>Cambiar wallet</span></button>
                        </div>
                    </div>
                    <div class="mt-4 md:mt-0 text-right">
                        <p class="text-blue-200 text-sm">Balance Disponible</p>
                        <p id="my-balance-display" class="text-4xl font-bold">${Number(myBalance).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</p>
                        <button class="mt-2 text-xs bg-white/20 hover:bg-white/30 px-3 py-1 rounded transition-colors"
                                onClick=${refreshBalance}><i class="fas fa-sync-alt mr-1"></i>Actualizar</button>
                    </div>
                </div>
            </div>

            <div class="grid grid-cols-1 md:grid-cols-3 gap-4 mb-6">
                <div class="bg-white p-4 rounded-lg shadow flex items-center space-x-3">
                    <div class="w-3 h-3 rounded-full ${isTesting ? 'bg-orange-400' : 'bg-green-500'}"></div>
                    <div>
                        <p class="text-xs text-gray-500 uppercase font-bold">Modo</p>
                        <p class="font-semibold">${isTesting ? 'Testing' : 'Production'}</p>
                    </div>
                </div>
                <div class="bg-white p-4 rounded-lg shadow flex items-center space-x-3">
                    <i class="fas fa-coins text-yellow-500"></i>
                    <div>
                        <p class="text-xs text-gray-500 uppercase font-bold">Fondo Sistema</p>
                        <p class="font-semibold">${isGenesis && systemConfig.genesisSupply ? Number(systemConfig.genesisSupply).toLocaleString() : '∞'}</p>
                    </div>
                </div>
                <div class="bg-white p-4 rounded-lg shadow flex items-center space-x-3">
                    <i class="fas fa-shield-alt text-blue-500"></i>
                    <div>
                        <p class="text-xs text-gray-500 uppercase font-bold">Validación</p>
                        <p class="font-semibold">${isTesting ? 'Libre (sin saldo)' : 'Requiere fondos'}</p>
                    </div>
                </div>
            </div>

            ${isTesting ? html`
                <div class="bg-orange-50 border-2 border-orange-300 border-dashed p-6 rounded-xl mb-6">
                    <div class="flex items-center justify-between flex-wrap gap-2">
                        <div>
                            <h3 class="text-lg font-bold text-orange-800">🚰 Fondo de Pruebas</h3>
                            <p class="text-sm text-orange-700">Recarga tu wallet con fondos del sistema para hacer transacciones.</p>
                        </div>
                        <div class="flex space-x-2">
                            <button class="px-4 py-2 bg-orange-500 text-white rounded-lg hover:bg-orange-600 transition-colors font-semibold"
                                    onClick=${async (e) => {
                                        const btn = e.target;
                                        btn.disabled = true; btn.textContent = '...';
                                        try {
                                            const res = await api.loadFunds({ publicKey: walletAddress, amount: 1000 });
                                            await refreshBalance();
                                            btn.textContent = '+1,000 ✓';
                                            showToast(`Cargados ${Number(res.amount).toLocaleString()} a tu wallet`, 'success');
                                            setTimeout(() => { btn.disabled = false; btn.textContent = '+1,000'; }, 1500);
                                        } catch (err) { btn.textContent = 'Error'; setTimeout(() => { btn.disabled = false; btn.textContent = '+1,000'; }, 1500); }
                                    }}>+1,000</button>
                            <button class="px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 transition-colors font-semibold"
                                    onClick=${async (e) => {
                                        const btn = e.target;
                                        btn.disabled = true; btn.textContent = '...';
                                        try {
                                            const res = await api.loadFunds({ publicKey: walletAddress, amount: 10000 });
                                            await refreshBalance();
                                            btn.textContent = '+10,000 ✓';
                                            showToast(`Cargados ${Number(res.amount).toLocaleString()} a tu wallet`, 'success');
                                            setTimeout(() => { btn.disabled = false; btn.textContent = '+10,000'; }, 1500);
                                        } catch (err) { btn.textContent = 'Error'; setTimeout(() => { btn.disabled = false; btn.textContent = '+10,000'; }, 1500); }
                                    }}>+10,000</button>
                        </div>
                    </div>
                </div>` : ''}

            <div class="bg-white p-6 rounded-xl shadow-md mb-6">
                ${sectionTitle('Enviar Transacción Individual')}
                <form class="grid grid-cols-1 md:grid-cols-12 gap-4 items-end" onSubmit=${async (e) => {
                    e.preventDefault();
                    const receiver = document.getElementById('single-tx-receiver').value.trim();
                    const amount = parseFloat(document.getElementById('single-tx-amount').value);
                    const btn = e.target.querySelector('button[type="submit"]');
                    const originalHtml = btn.innerHTML;
                    const msgBox = document.getElementById('single-tx-msg');
                    msgBox.innerHTML = '';

                    if (!receiver) {
                        msgBox.append(createNotice('Ingresá un destinatario.', 'bg-yellow-100 border-yellow-400 text-yellow-700'));
                        return;
                    }
                    if (!amount || amount <= 0) {
                        msgBox.append(createNotice('Ingresá un monto válido.', 'bg-yellow-100 border-yellow-400 text-yellow-700'));
                        return;
                    }
                    if (!isTesting && myBalance < amount) {
                        msgBox.append(createNotice(`Fondos insuficientes. Tenés ${myBalance.toFixed(2)}, necesitás ${amount.toFixed(2)}.`, 'bg-red-100 border-red-400 text-red-700'));
                        return;
                    }

                    btn.disabled = true;
                    btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i> Enviando...';
                    try {
                        const signed = await signTransaction(receiver, amount);
                        await api.createTx(signed);
                        await refreshBalance();
                        msgBox.append(createNotice('¡Transacción enviada!', 'bg-green-100 border-green-400 text-green-700'));
                        document.getElementById('single-tx-receiver').value = '';
                        document.getElementById('single-tx-amount').value = '';
                        refreshPendingList();
                    } catch (err) {
                        msgBox.append(createNotice(`Error: ${err.message || 'Rechazada'}`, 'bg-red-100 border-red-400 text-red-700'));
                    } finally {
                        btn.disabled = false;
                        btn.innerHTML = originalHtml;
                    }
                }}>
                    <div class="md:col-span-5">
                        <label class="block text-sm font-medium text-gray-700 mb-1">Destinatario (Public Key)</label>
                        <input id="single-tx-receiver" type="text" placeholder="04a1b2... o addr123"
                               class="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 font-mono text-sm" />
                    </div>
                    <div class="md:col-span-3">
                        <label class="block text-sm font-medium text-gray-700 mb-1">Monto</label>
                        <input id="single-tx-amount" type="number" step="0.01" min="0.01" placeholder="100.00"
                               class="w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500" />
                    </div>
                    <div class="md:col-span-4 flex space-x-2">
                        <button type="submit" class="flex-1 px-4 py-2 bg-blue-600 text-white font-semibold rounded-lg hover:bg-blue-700 transition-colors flex items-center justify-center space-x-2">
                            <i class="fas fa-paper-plane"></i><span>Enviar</span>
                        </button>
                        <button type="button" class="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors"
                                title="Generar destinatario y monto aleatorios"
                                onClick=${() => {
                                    document.getElementById('single-tx-receiver').value = generateRandomAddress();
                                    document.getElementById('single-tx-amount').value = (Math.floor(Math.random() * 100) + 1).toString();
                                }}>
                            <i class="fas fa-dice"></i>
                        </button>
                    </div>
                </form>
                <div id="single-tx-msg" class="mt-3"></div>
            </div>

            <div class="bg-white p-6 rounded-xl shadow-md mb-6">
                <h2 id="pending-tx-count" class="text-xl font-bold text-gray-900 mb-4 border-b pb-2">Transacciones Pendientes (${pendingTransactions.length})</h2>
                <p class="text-xs text-gray-400 mb-3">Se actualiza automáticamente cada 5 segundos. Se separan las que ya entraron al bloque candidato (minándose) de las que siguen esperando en cola.</p>
                <div id="pending-tx-table-container"></div>
            </div>

            ${buildMassGenerator()}
            ${buildBalanceCheck()}

            <div class="bg-white p-6 rounded-xl shadow-md">
                ${sectionTitle('Configuración de Dificultad')}
                <div class="flex items-center space-x-2 mb-3">
                    <span class="text-gray-600">Actual:</span>
                    <span id="current-difficulty-display" class="font-mono text-xl text-blue-600 font-bold">${currentDifficulty}</span>
                </div>
                <div class="flex space-x-2">
                    <input id="new-difficulty-input" type="text" placeholder="Ej: 0000" class="flex-1 p-2 border border-gray-300 rounded-lg font-mono" />
                    <button class="px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 font-semibold"
                            onClick=${async () => {
                                const val = document.getElementById('new-difficulty-input').value.trim();
                                if (!val) return;
                                try {
                                    const res = await api.setDifficulty(val);
                                    showToast(`Dificultad actualizada: ${res}`, 'success');
                                    updateDifficultyDisplay();
                                } catch (e) {
                                    showToast(`Error: ${e.message}`, 'error');
                                }
                            }}>Cambiar</button>
                </div>
            </div>
        `, root);

        // Conectar comportamientos que requieren DOM ya montado
        renderPendingList(document.getElementById('pending-tx-table-container'));
        wireFixedToggles();
        updateDifficultyDisplay();

        // Polling de transacciones pendientes (pausa en pestaña oculta)
        root._txPollTimer = setInterval(refreshPendingList, POLL_INTERVAL_MS);

    } catch (error) {
        render(errorBox('No se pudieron cargar las transacciones. Intenta de nuevo más tarde.'), root);
        console.error('Error in transactions component:', error);
    }
}

function createNotice(message, colorClasses) {
    const div = document.createElement('div');
    div.className = `${colorClasses} border px-3 py-2 rounded text-sm`;
    div.textContent = message;
    return div;
}
