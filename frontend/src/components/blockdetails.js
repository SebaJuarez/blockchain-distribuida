import { api } from '../services/api.js';
import { createEl, truncateHash, copyToClipboard, createLoadingSpinner, shortenId } from '../utils/dom.js';

// Renders the detailed view for a single block
export async function blockDetail(root, hash) {
    root.innerHTML = ''; // Clear previous content
    root.appendChild(createLoadingSpinner()); // Show loading spinner

    try {
        const block = await api.block(hash);

        root.innerHTML = ''; // Clear spinner once data is fetched

        if (!block) {
            root.append(createEl('div', { className: 'bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative' },
                createEl('strong', { className: 'font-bold' }, 'Advertencia: '),
                createEl('span', { className: 'block sm:inline' }, `No se encontró el bloque con hash: ${hash}.`)
            ));
            return;
        }

        // Navigation buttons (Prev/Next)
        const nav = createEl('div', { className: 'flex justify-between mb-6' },
            createEl('button', {
                className: `px-6 py-2 bg-blue-600 text-white rounded-lg shadow-md hover:bg-blue-700 transition-colors flex items-center space-x-2 ${!block.previous_hash || block.previous_hash === '0' ? 'opacity-50 cursor-not-allowed' : ''}`,
                onClick: () => {
                    if (block.previous_hash && block.previous_hash !== '0') { // Assuming '0' or empty is genesis
                        location.hash = '#blocks/' + block.previous_hash;
                    } else {
                        const messageEl = createEl('div', { className: 'fixed bottom-4 right-4 bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative shadow-lg text-sm' },
                            createEl('strong', { className: 'font-bold' }, 'Información: '),
                            createEl('span', { className: 'block sm:inline' }, 'Este es el bloque Génesis o no hay un bloque anterior disponible.')
                        );
                        document.body.append(messageEl);
                        setTimeout(() => messageEl.remove(), 3000);
                    }
                },
                disabled: !block.previous_hash || block.previous_hash === '0'
            }, createEl('i', { className: 'fas fa-chevron-left' }), createEl('span', {}, 'Anterior')),
            createEl('button', {
                className: 'px-6 py-2 bg-blue-600 text-white rounded-lg shadow-md hover:bg-blue-700 transition-colors flex items-center space-x-2',
                onClick: () => {
                    // For "next", we assume sequential blocks or fetching next by index.
                    // For now, "latest" is a placeholder if no specific next hash is available.
                    const messageEl = createEl('div', { className: 'fixed bottom-4 right-4 bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative shadow-lg text-sm' },
                        createEl('strong', { className: 'font-bold' }, 'Información: '),
                        createEl('span', { className: 'block sm:inline' }, 'La navegación al siguiente bloque por índice aún no está implementada. Mostrando el último bloque.')
                    );
                    document.body.append(messageEl);
                    setTimeout(() => messageEl.remove(), 3000);
                    location.hash = '#blocks/latest'; // Fallback to latest
                }
            }, createEl('span', {}, 'Siguiente'), createEl('i', { className: 'fas fa-chevron-right' }))
        );

        // Block Information Card
        const info = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl mb-6' },
            createEl('h2', { className: 'text-3xl font-extrabold text-gray-900 mb-6 border-b pb-4' }, `Bloque #${block.index.toLocaleString()}`),
            createEl('div', { className: 'grid grid-cols-1 md:grid-cols-2 gap-y-4 gap-x-8 text-gray-700' },
                // Row 1
                createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {className: 'w-32 flex-shrink-0'}, 'Hash:'),
                    createEl('span', { className: 'font-mono break-all text-sm' }, block.hash),
                    createEl('button', {
                        className: 'ml-2 text-gray-400 hover:text-gray-600 focus:outline-none',
                        onClick: () => copyToClipboard(block.hash)
                    }, createEl('i', { className: 'fas fa-copy text-xs' }))
                ),
                createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {className: 'w-32 flex-shrink-0'}, 'Hash Previo:'),
                    createEl('span', { className: 'font-mono break-all text-sm' }, block.previous_hash || 'N/A'),
                    block.previous_hash && block.previous_hash !== '0' && createEl('button', { // Only show copy if exists and not genesis placeholder
                        className: 'ml-2 text-gray-400 hover:text-gray-600 focus:outline-none',
                        onClick: () => copyToClipboard(block.previous_hash)
                    }, createEl('i', { className: 'fas fa-copy text-xs' })),
                    block.previous_hash && block.previous_hash !== '0' && createEl('a', {
                        href: `#blocks/${block.previous_hash}`,
                        className: 'ml-2 text-blue-500 hover:underline text-xs'
                    }, 'Ver')
                ),
                // Row 2
                createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {className: 'w-32 flex-shrink-0'}, 'Nonce:'), createEl('span', { className: 'font-mono text-sm' }, block.nonce.toLocaleString())),
                createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {className: 'w-32 flex-shrink-0'}, 'Marca de Tiempo:'), createEl('span', { className: 'text-sm' }, new Date(block.timestamp * 1000).toLocaleString()))
            )
        );

        // Transactions in Block Card
        const txs = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl' },
            createEl('h3', { className: 'text-2xl font-bold text-gray-900 mb-6 border-b pb-4' }, `Transacciones (${block.data.length.toLocaleString()})`),
            block.data.length > 0
                ? createEl('div', { className: 'table-responsive' },
                    createEl('table', { className: 'min-w-full divide-y divide-gray-200' },
                        createEl('thead', { className: 'bg-gray-50' },
                            createEl('tr', {},
                                createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'ID TX'),
                                // FIX: Corrected createS to createEl here
                                createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Remitente'),
                                createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Receptor'),
                                createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Cantidad'),
                                createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Marca de Tiempo')
                            )
                        ),
                        createEl('tbody', { className: 'bg-white divide-y divide-gray-200' },
                            ...block.data.map(tx => createEl('tr', { className: 'hover:bg-gray-50 transition-colors' },
                                createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm font-medium text-blue-600' },
                                    createEl('a', { href: `#transactions/${tx.id}`, className: 'hover:underline flex items-center' },
                                        createEl('span', { className: 'font-mono text-xs md:text-sm' }, shortenId(tx.id)),
                                        createEl('button', {
                                            className: 'ml-2 text-gray-400 hover:text-gray-600 focus:outline-none',
                                            onClick: (e) => {
                                                e.preventDefault();
                                                copyToClipboard(tx.id);
                                            }
                                        }, createEl('i', { className: 'fas fa-copy text-xs' }))
                                    )
                                ),
                                createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm text-gray-900' },
                                    createEl('span', { className: 'font-mono text-xs md:text-sm' }, shortenId(tx.sender)),
                                    createEl('button', {
                                        className: 'ml-2 text-gray-400 hover:text-gray-600 focus:outline-none',
                                        onClick: (e) => { e.preventDefault(); copyToClipboard(tx.sender); }
                                    }, createEl('i', { className: 'fas fa-copy text-xs' }))
                                ),
                                createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm text-gray-900' },
                                    createEl('span', { className: 'font-mono text-xs md:text-sm' }, shortenId(tx.receiver)),
                                    createEl('button', {
                                        className: 'ml-2 text-gray-400 hover:text-gray-600 focus:outline-none',
                                        onClick: (e) => { e.preventDefault(); copyToClipboard(tx.receiver); }
                                    }, createEl('i', { className: 'fas fa-copy text-xs' }))
                                ),
                                createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900' }, tx.amount.toLocaleString()),
                                createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm text-gray-500' }, new Date(tx.timestamp * 1000).toLocaleString())
                            ))
                        )
                    )
                )
                : createEl('p', { className: 'text-gray-600 italic' }, 'No hay transacciones en este bloque.')
        );

        root.append(nav, info, txs);
    } catch (error) {
        root.innerHTML = ''; // Clear spinner
        root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' },
            createEl('strong', { className: 'font-bold' }, 'Error: '),
            createEl('span', { className: 'block sm:inline' }, `No se pudieron cargar los detalles del bloque con hash ${hash}. Intenta de nuevo más tarde.`)
        ));
        console.error('Error in blockDetail component:', error);
    }
}
