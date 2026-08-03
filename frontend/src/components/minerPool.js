import { api } from '../services/api.js';
import { render, html } from '../lib/preact-standalone.js';
import { shortenId, copyToClipboard } from '../utils/dom.js';
import { spinner, emptyState, errorBox } from '../utils/ui.js';

export async function minerPool(root) {
    render(spinner(), root);

    try {
        const data = await api.getMiners();
        const miners = data._embedded ? data._embedded.minerList : [];

        // Fetch balances for each miner
        const minersWithBalance = await Promise.all(miners.map(async (m) => {
            try {
                const bal = await api.getMinerBalance(m.publicKey);
                return { ...m, balance: bal.balance || 0 };
            } catch (e) {
                return { ...m, balance: 0 };
            }
        }));

        render(html`
            <div class="bg-white p-6 md:p-8 rounded-lg shadow-xl">
                <h2 class="text-2xl font-bold text-gray-900 mb-6 border-b pb-4">Pool de Nodos Mineros</h2>
                <p class="text-gray-600 mb-4">Mineros activos: <strong>${minersWithBalance.length}</strong></p>

                ${minersWithBalance.length === 0
                    ? emptyState('No hay mineros registrados en el pool en este momento.', 'fas fa-network-wired')
                    : html`
                        <div class="overflow-x-auto table-responsive">
                            <table class="min-w-full divide-y divide-gray-200">
                                <thead class="bg-gray-50">
                                    <tr>
                                        <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Public Key</th>
                                        <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Balance</th>
                                        <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Último Keep-Alive</th>
                                        <th class="px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider">GPU</th>
                                    </tr>
                                </thead>
                                <tbody class="bg-white divide-y divide-gray-200">
                                    ${minersWithBalance.map(miner => html`
                                        <tr class="hover:bg-gray-50 transition-colors">
                                            <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-blue-600">
                                                <div class="flex items-center">
                                                    <span class="font-mono text-xs md:text-sm" title="${miner.publicKey}">${shortenId(miner.publicKey, 10, 10)}</span>
                                                    <button class="ml-2 text-gray-400 hover:text-gray-600 focus:outline-none"
                                                            onClick=${() => copyToClipboard(miner.publicKey)} title="Copiar public key">
                                                        <i class="fas fa-copy text-xs"></i>
                                                    </button>
                                                </div>
                                            </td>
                                            <td class="px-6 py-4 whitespace-nowrap text-sm font-bold text-gray-900">${Number(miner.balance).toFixed(2)}</td>
                                            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                                ${miner.lastTimestamp
                                                    ? new Date(miner.lastTimestamp).toLocaleString()
                                                    : html`<span class="text-gray-400 italic">N/A</span>`}
                                            </td>
                                            <td class="px-6 py-4 whitespace-nowrap text-sm text-center">
                                                ${miner.gpuMiner
                                                    ? html`<span class="px-2 py-1 bg-green-100 text-green-700 rounded-full text-xs font-bold">GPU</span>`
                                                    : html`<span class="px-2 py-1 bg-gray-100 text-gray-600 rounded-full text-xs font-bold">CPU</span>`}
                                            </td>
                                        </tr>
                                    `)}
                                </tbody>
                            </table>
                        </div>`
                }
            </div>
        `, root);
    } catch (error) {
        render(errorBox('No se pudieron cargar los mineros del pool.'), root);
        console.error('Error al cargar el pool de mineros:', error);
    }
}
