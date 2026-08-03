import { api } from '../services/api.js';
import { render, html } from '../lib/preact-standalone.js';
import { shortenId, copyToClipboard, showToast } from '../utils/dom.js';
import { spinner, emptyState, errorBox } from '../utils/ui.js';

export async function blockList(root) {
    render(spinner(), root);

    let currentPage = 0;
    const pageSize = 10;

    async function renderBlocks() {
        render(spinner(), root);

        try {
            const data = await api.allBlocks(currentPage, pageSize);
            const blocks = data._embedded ? data._embedded.blockList : [];
            const totalElements = data.page ? data.page.totalElements : blocks.length;
            const totalPages = Math.max(1, Math.ceil(totalElements / pageSize));

            // Sort by index descending (latest first) if not already sorted
            blocks.sort((a, b) => b.index - a.index);

            // Pagination range (show up to 5 page buttons around current)
            const maxPagesToShow = 5;
            let startPage = Math.max(0, currentPage - Math.floor(maxPagesToShow / 2));
            let endPage = Math.min(totalPages - 1, startPage + maxPagesToShow - 1);
            if (endPage - startPage + 1 < maxPagesToShow) {
                startPage = Math.max(0, endPage - maxPagesToShow + 1);
            }
            const pageButtons = [];
            for (let i = startPage; i <= endPage; i++) {
                pageButtons.push(html`
                    <button class="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium transition-colors ${i === currentPage ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 hover:bg-blue-50 hover:text-blue-700'}"
                            onClick=${() => { currentPage = i; renderBlocks(); }}>
                        ${(i + 1).toString()}
                    </button>
                `);
            }

            render(html`
                <div class="flex justify-between items-center mb-6 gap-2 flex-wrap">
                    <h2 class="text-2xl font-bold text-gray-900">Explorador de Bloques</h2>
                    <div class="space-x-2 flex">
                        <button class="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg shadow-md hover:bg-gray-300 transition-colors flex items-center space-x-2 text-sm"
                                onClick=${async () => {
                                    try {
                                        const genesisBlock = await api.blockByIndex(0);
                                        if (genesisBlock) {
                                            location.hash = `#blocks/${genesisBlock.hash}`;
                                        } else {
                                            showToast('No se encontró el bloque Génesis (Índice 0).', 'warning');
                                        }
                                    } catch (e) {
                                        showToast('Error al cargar el bloque Génesis.', 'error');
                                        console.error('Error fetching genesis block:', e);
                                    }
                                }}>
                            <i class="fas fa-arrow-alt-circle-up"></i><span>Bloque Génesis</span>
                        </button>
                        <button class="px-4 py-2 bg-blue-600 text-white rounded-lg shadow-md hover:bg-blue-700 transition-colors flex items-center space-x-2 text-sm"
                                onClick=${() => location.hash = '#blocks/latest'}>
                            <i class="fas fa-arrow-alt-circle-down"></i><span>Último Bloque</span>
                        </button>
                    </div>
                </div>

                ${blocks.length === 0
                    ? emptyState('No hay bloques para mostrar en este momento.', 'fas fa-cube')
                    : html`
                        <div class="bg-white shadow-md rounded-lg overflow-hidden mb-6 table-responsive">
                            <table class="min-w-full divide-y divide-gray-200">
                                <thead class="bg-gray-50">
                                    <tr>
                                        <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Índice</th>
                                        <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Hash</th>
                                        <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Transacciones</th>
                                        <th class="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Marca de Tiempo</th>
                                    </tr>
                                </thead>
                                <tbody class="bg-white divide-y divide-gray-200">
                                    ${blocks.map(b => html`
                                        <tr class="hover:bg-gray-50 transition-colors">
                                            <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900">${b.index.toLocaleString()}</td>
                                            <td class="px-6 py-4 whitespace-nowrap text-sm font-medium text-blue-600">
                                                <a href="#blocks/${b.hash}" class="hover:underline flex items-center" title="${b.hash}">
                                                    <span class="font-mono text-xs md:text-sm">${shortenId(b.hash, 10, 10)}</span>
                                                    <button class="ml-2 text-gray-400 hover:text-gray-600 focus:outline-none"
                                                            onClick=${(e) => { e.preventDefault(); copyToClipboard(b.hash); }}>
                                                        <i class="fas fa-copy text-xs"></i>
                                                    </button>
                                                </a>
                                            </td>
                                            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-900">${b.data.length.toLocaleString()}</td>
                                            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">${new Date(b.timestamp * 1000).toLocaleString()}</td>
                                        </tr>
                                    `)}
                                </tbody>
                            </table>
                        </div>

                        <div class="flex justify-center mt-8 space-x-2">
                            <button class="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 ${currentPage === 0 ? 'opacity-50 cursor-not-allowed' : ''}"
                                    disabled=${currentPage === 0}
                                    onClick=${() => { if (currentPage > 0) { currentPage--; renderBlocks(); } }}>
                                <i class="fas fa-chevron-left"></i>
                            </button>
                            ${pageButtons}
                            <button class="px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 ${currentPage >= totalPages - 1 ? 'opacity-50 cursor-not-allowed' : ''}"
                                    disabled=${currentPage >= totalPages - 1}
                                    onClick=${() => { if (currentPage < totalPages - 1) { currentPage++; renderBlocks(); } }}>
                                <i class="fas fa-chevron-right"></i>
                            </button>
                        </div>
                        <p class="text-center text-xs text-gray-400 mt-3">Página ${currentPage + 1} de ${totalPages} · ${totalElements.toLocaleString()} bloques en total</p>`
                }
            `, root);

        } catch (error) {
            render(errorBox('No se pudieron cargar los bloques. Intenta de nuevo más tarde.'), root);
            console.error('Error in blockList component:', error);
        }
    }

    renderBlocks(); // Initial render
}
