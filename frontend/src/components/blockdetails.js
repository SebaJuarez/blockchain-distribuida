import { api } from '../services/api.js';
import { render, html } from '../lib/preact-standalone.js';
import { shortenId, copyToClipboard, showToast } from '../utils/dom.js';
import { spinner, warningBox, errorBox } from '../utils/ui.js';

function copyBtn(value) {
    return html`
        <button class="ml-2 text-gray-400 hover:text-gray-600 focus:outline-none" onClick=${() => copyToClipboard(value)}>
            <i class="fas fa-copy text-xs"></i>
        </button>`;
}

// Renders the detailed view for a single block
export async function blockDetail(root, hash) {
    render(spinner(), root);

    try {
        const block = await api.block(hash);

        if (!block) {
            render(warningBox('Advertencia', `No se encontró el bloque con hash: ${hash}.`), root);
            return;
        }

        // Navigation buttons (Prev/Next)
        const isGenesis = !block.previous_hash || block.previous_hash === '0';

        render(html`
            <div class="flex justify-between mb-6 gap-2 flex-wrap">
                <button class="px-6 py-2 bg-blue-600 text-white rounded-lg shadow-md hover:bg-blue-700 transition-colors flex items-center space-x-2 ${isGenesis ? 'opacity-50 cursor-not-allowed' : ''}"
                        disabled=${isGenesis}
                        onClick=${() => {
                            if (!isGenesis) location.hash = '#blocks/' + block.previous_hash;
                            else showToast('Este es el bloque Génesis, no hay un bloque anterior.', 'info');
                        }}>
                    <i class="fas fa-chevron-left"></i><span>Anterior</span>
                </button>
                <button id="next-block-btn" class="px-6 py-2 bg-blue-600 text-white rounded-lg shadow-md hover:bg-blue-700 transition-colors flex items-center space-x-2"
                        onClick=${async (e) => {
                            const btn = e.target.closest('#next-block-btn');
                            btn.disabled = true;
                            btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i> Buscando...';
                            try {
                                const next = await api.blockByIndex(block.index + 1);
                                location.hash = '#blocks/' + next.hash;
                            } catch (err) {
                                showToast('Este es el último bloque de la cadena.', 'info');
                                btn.disabled = false;
                                btn.innerHTML = '<span>Siguiente</span><i class="fas fa-chevron-right ml-2"></i>';
                            }
                        }}>
                    <span>Siguiente</span><i class="fas fa-chevron-right"></i>
                </button>
            </div>

            <div class="bg-white p-6 md:p-8 rounded-lg shadow-xl mb-6">
                <h2 class="text-3xl font-extrabold text-gray-900 mb-6 border-b pb-4">Bloque #${block.index.toLocaleString()}</h2>
                <div class="grid grid-cols-1 md:grid-cols-2 gap-y-4 gap-x-8 text-gray-700">
                    <p class="flex items-start space-x-2">
                        <strong class="w-32 flex-shrink-0">Hash:</strong>
                        <span class="font-mono break-all text-sm">${block.hash}</span>
                        ${copyBtn(block.hash)}
                    </p>
                    <p class="flex items-start space-x-2">
                        <strong class="w-32 flex-shrink-0">Hash Previo:</strong>
                        <span class="font-mono break-all text-sm">${block.previous_hash || 'N/A'}</span>
                        ${!isGenesis && copyBtn(block.previous_hash)}
                        ${!isGenesis && html`
                            <a href="#blocks/${block.previous_hash}" class="ml-2 text-blue-500 hover:underline text-xs">Ver</a>`}
                    </p>
                    <p class="flex items-center space-x-2">
                        <strong class="w-32 flex-shrink-0">Nonce:</strong>
                        <span class="font-mono text-sm">${block.nonce.toLocaleString()}</span>
                    </p>
                    <p class="flex items-center space-x-2">
                        <strong class="w-32 flex-shrink-0">Marca de Tiempo:</strong>
                        <span class="text-sm">${new Date(block.timestamp * 1000).toLocaleString()}</span>
                    </p>
                </div>
            </div>

            <div class="bg-white p-6 md:p-8 rounded-lg shadow-xl">
                <h3 class="text-2xl font-bold text-gray-900 mb-6 border-b pb-4">Transacciones (${block.data.length.toLocaleString()})</h3>
                ${block.data.length > 0 ? html`
                    <div class="table-responsive">
                        <table class="min-w-full divide-y divide-gray-200">
                            <thead class="bg-gray-50">
                                <tr>
                                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">ID TX</th>
                                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Remitente</th>
                                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Receptor</th>
                                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Cantidad</th>
                                    <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Marca de Tiempo</th>
                                </tr>
                            </thead>
                            <tbody class="bg-white divide-y divide-gray-200">
                                ${block.data.map(tx => html`
                                    <tr class="hover:bg-gray-50 transition-colors">
                                        <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-blue-600">
                                            <a href="#transactions/${tx.id}" class="hover:underline flex items-center" title="${tx.id}">
                                                <span class="font-mono text-xs md:text-sm">${shortenId(tx.id)}</span>
                                                <button class="ml-2 text-gray-400 hover:text-gray-600 focus:outline-none"
                                                        onClick=${(e) => { e.preventDefault(); copyToClipboard(tx.id); }}>
                                                    <i class="fas fa-copy text-xs"></i>
                                                </button>
                                            </a>
                                        </td>
                                        <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                            <span class="font-mono text-xs md:text-sm" title="${tx.sender}">${shortenId(tx.sender)}</span>
                                            ${copyBtn(tx.sender)}
                                        </td>
                                        <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                                            <span class="font-mono text-xs md:text-sm" title="${tx.receiver}">${shortenId(tx.receiver)}</span>
                                            ${copyBtn(tx.receiver)}
                                        </td>
                                        <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">${tx.amount.toLocaleString()}</td>
                                        <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">${new Date(tx.timestamp * 1000).toLocaleString()}</td>
                                    </tr>
                                `)}
                            </tbody>
                        </table>
                    </div>`
                : html`<p class="text-gray-600 italic">No hay transacciones en este bloque.</p>`}
            </div>
        `, root);
    } catch (error) {
        render(errorBox(`No se pudieron cargar los detalles del bloque con hash ${hash}. Intenta de nuevo más tarde.`), root);
        console.error('Error in blockDetail component:', error);
    }
}
