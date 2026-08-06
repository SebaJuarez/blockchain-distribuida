import { api } from '../services/api.js';
import { render, html } from '../lib/preact-standalone.js';
import { shortenId, copyToClipboard } from '../utils/dom.js';
import { spinner, warningBox, keyValueRow, badge } from '../utils/ui.js';

export async function transactionDetail(root, txId) {
    render(spinner(), root);

    try {
        const detail = await api.transaction(txId);
        const tx = detail.transaction;
        const isPending = detail.status === 'PENDING';

        render(html`
            <div class="bg-white p-6 md:p-8 rounded-lg shadow-xl mb-6">
                <h2 class="text-3xl font-extrabold text-gray-900 mb-6 border-b pb-4 flex items-center space-x-3">
                    <span>Detalles de la Transacción</span>
                    ${isPending
                        ? badge('PENDIENTE', 'bg-yellow-100 text-yellow-700')
                        : badge('MINADA', 'bg-green-100 text-green-700')}
                </h2>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-y-4 gap-x-8 text-gray-700">
                    ${keyValueRow('ID', tx.id, copyBtn(tx.id))}
                    <p class="flex items-start space-x-2">
                        <strong class="w-32 flex-shrink-0">Estado:</strong>
                        <span class="text-sm font-semibold ${isPending ? 'text-yellow-600' : 'text-green-600'}">
                            ${isPending ? 'Pendiente (en pool de transacciones)' : 'Minada en bloque'}
                        </span>
                    </p>
                    ${keyValueRow('Remitente', tx.sender || 'N/A', tx.sender ? copyBtn(tx.sender) : '')}
                    ${keyValueRow('Receptor', tx.receiver || 'N/A', tx.receiver ? copyBtn(tx.receiver) : '')}
                    <p class="flex items-center space-x-2">
                        <strong class="w-32 flex-shrink-0">Cantidad:</strong>
                        <span class="text-sm font-bold text-gray-900">${Number(tx.amount || 0).toLocaleString()}</span>
                    </p>
                    <p class="flex items-center space-x-2">
                        <strong class="w-32 flex-shrink-0">Marca de Tiempo:</strong>
                        <span class="text-sm">${new Date(tx.timestamp * 1000).toLocaleString()}</span>
                    </p>
                    ${detail.blockIndex !== null && detail.blockIndex !== undefined && html`
                        <p class="flex items-center space-x-2">
                            <strong class="w-32 flex-shrink-0">Bloque:</strong>
                            <a href="#blocks/${detail.blockHash}" class="text-sm text-blue-600 hover:underline font-mono">
                                #${detail.blockIndex} (${shortenId(detail.blockHash, 8, 8)})
                            </a>
                        </p>`
                    }
                </div>
            </div>
        `, root);
    } catch (error) {
        render(warningBox('No encontrada',
            `No se encontró una transacción con ID ${shortenId(txId, 8, 8)} (pendiente o minada).`), root);
        console.error('Error in transactionDetail component:', error);
    }
}

function copyBtn(value) {
    return html`
        <button class="ml-2 text-gray-400 hover:text-gray-600 focus:outline-none" onClick=${() => copyToClipboard(value)}>
            <i class="fas fa-copy text-xs"></i>
        </button>`;
}
