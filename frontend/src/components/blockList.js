import { api } from '../services/api.js';
import { createEl, truncateHash, copyToClipboard, createLoadingSpinner, shortenId } from '../utils/dom.js';

export async function blockList(root) {
    root.innerHTML = ''; // Clear previous content
    root.appendChild(createLoadingSpinner()); // Show loading spinner

    let currentPage = 0;
    const pageSize = 10; // More items per page

    async function renderBlocks() {
        root.innerHTML = ''; // Clear previous content before re-rendering
        root.appendChild(createLoadingSpinner()); // Show loading spinner for each render

        try {
            const data = await api.allBlocks(currentPage, pageSize);
            let blocks = data._embedded ? data._embedded.blockList : [];
            const totalElements = data.page ? data.page.totalElements : blocks.length;
            const totalPages = Math.ceil(totalElements / pageSize);

            // Sort by index descending (latest first) if not already sorted
            blocks.sort((a, b) => b.index - a.index);

            root.innerHTML = ''; // Clear spinner once data is fetched

            // Top navigation and Genesis/Latest buttons
            const topNav = createEl('div', { className: 'flex justify-between items-center mb-6' },
                createEl('h2', { className: 'text-2xl font-bold text-gray-900' }, 'Explorador de Bloques'),
                createEl('div', { className: 'space-x-2' },
                    // Link to Genesis Block (assuming index 0)
                    createEl('button', {
                        className: 'px-4 py-2 bg-gray-200 text-gray-700 rounded-lg shadow-md hover:bg-gray-300 transition-colors flex items-center space-x-2 text-sm',
                        onClick: async () => {
                            try {
                                // Fetch the block with index 0 to get its hash
                                const genesisBlockData = await api.allBlocks(0, 1); // Fetch a small page
                                const genesisBlock = genesisBlockData._embedded?.blockList?.find(b => b.index === 0);
                                if (genesisBlock) {
                                    location.hash = `#blocks/${genesisBlock.hash}`;
                                } else {
                                    const messageEl = createEl('div', { className: 'fixed bottom-4 right-4 bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative shadow-lg text-sm' }, 'No se encontró el bloque Génesis (Índice 0).');
                                    document.body.append(messageEl);
                                    setTimeout(() => messageEl.remove(), 3000);
                                }
                            } catch (e) {
                                const messageEl = createEl('div', { className: 'fixed bottom-4 right-4 bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative shadow-lg text-sm' }, 'Error al cargar el bloque Génesis.');
                                document.body.append(messageEl);
                                setTimeout(() => messageEl.remove(), 3000);
                                console.error('Error fetching genesis block:', e);
                            }
                        }
                    }, createEl('i', { className: 'fas fa-arrow-alt-circle-up' }), createEl('span', {}, 'Bloque Génesis')),
                    // Link to Latest Block
                    createEl('button', {
                        className: 'px-4 py-2 bg-blue-600 text-white rounded-lg shadow-md hover:bg-blue-700 transition-colors flex items-center space-x-2 text-sm',
                        onClick: () => location.hash = '#blocks/latest'
                    }, createEl('i', { className: 'fas fa-arrow-alt-circle-down' }), createEl('span', {}, 'Último Bloque'))
                )
            );
            root.append(topNav);

            if (blocks.length === 0) {
                root.append(createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md text-center text-gray-600' },
                    createEl('p', {}, 'No hay bloques para mostrar en este momento.')
                ));
                return;
            }

            const tableWrapper = createEl('div', { className: 'bg-white shadow-md rounded-lg overflow-hidden mb-6 table-responsive' },
                createEl('table', { className: 'min-w-full divide-y divide-gray-200' },
                    createEl('thead', { className: 'bg-gray-50' },
                        createEl('tr', {},
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Índice'),
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Hash'),
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Transacciones'),
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Marca de Tiempo')
                        )
                    ),
                    createEl('tbody', { className: 'bg-white divide-y divide-gray-200' },
                        ...blocks.map(b => createEl('tr', { className: 'hover:bg-gray-50 transition-colors' },
                            createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm font-medium text-gray-900' }, b.index.toLocaleString()),
                            createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm font-medium text-blue-600' },
                                createEl('a', { href: '#blocks/' + b.hash, className: 'hover:underline flex items-center' },
                                    createEl('span', { className: 'font-mono text-xs md:text-sm' }, shortenId(b.hash, 10, 10)),
                                    createEl('button', {
                                        className: 'ml-2 text-gray-400 hover:text-gray-600 focus:outline-none',
                                        onClick: (e) => {
                                            e.preventDefault(); // Prevent navigation
                                            copyToClipboard(b.hash);
                                        }
                                    }, createEl('i', { className: 'fas fa-copy text-xs' }))
                                )
                            ),
                            createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm text-gray-900' }, b.data.length.toLocaleString()),
                            createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm text-gray-500' }, new Date(b.timestamp * 1000).toLocaleString())
                        ))
                    )
                )
            );
            root.append(tableWrapper);

            // Pagination controls
            const pagination = createEl('div', { className: 'flex justify-center mt-8 space-x-2' });

            // Previous button
            const prevBtn = createEl('button', {
                className: `px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 ${currentPage === 0 ? 'opacity-50 cursor-not-allowed' : ''}`,
                onClick: () => {
                    if (currentPage > 0) {
                        currentPage--;
                        renderBlocks();
                    }
                },
                disabled: currentPage === 0
            }, createEl('i', { className: 'fas fa-chevron-left' }));
            pagination.append(prevBtn);

            // Page buttons (show a limited range around current page)
            const maxPagesToShow = 5;
            let startPage = Math.max(0, currentPage - Math.floor(maxPagesToShow / 2));
            let endPage = Math.min(totalPages - 1, startPage + maxPagesToShow - 1);

            if (endPage - startPage + 1 < maxPagesToShow) {
                startPage = Math.max(0, endPage - maxPagesToShow + 1);
            }

            for (let i = startPage; i <= endPage; i++) {
                const btn = createEl('button', {
                    className: `px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium transition-colors ${i === currentPage ? 'bg-blue-600 text-white border-blue-600' : 'bg-white text-gray-700 hover:bg-blue-50 hover:text-blue-700'}`,
                    onClick: () => {
                        currentPage = i;
                        renderBlocks();
                    }
                }, (i + 1).toString());
                pagination.append(btn);
            }

            // Next button
            const nextBtn = createEl('button', {
                className: `px-4 py-2 border border-gray-300 rounded-md shadow-sm text-sm font-medium text-gray-700 bg-white hover:bg-gray-50 ${currentPage >= totalPages - 1 ? 'opacity-50 cursor-not-allowed' : ''}`,
                onClick: () => {
                    if (currentPage < totalPages - 1) {
                        currentPage++;
                        renderBlocks();
                    }
                },
                disabled: currentPage >= totalPages - 1
            }, createEl('i', { className: 'fas fa-chevron-right' }));
            pagination.append(nextBtn);

            root.append(pagination);

        } catch (error) {
            root.innerHTML = ''; // Clear spinner
            root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' },
                createEl('strong', { className: 'font-bold' }, 'Error: '),
                createEl('span', { className: 'block sm:inline' }, 'No se pudieron cargar los bloques. Intenta de nuevo más tarde.')
            ));
            console.error('Error in blockList component:', error);
        }
    }
    renderBlocks(); // Initial render
}
