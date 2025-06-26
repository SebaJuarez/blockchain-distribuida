import { api } from '../services/api.js';
import { createEl, createLoadingSpinner, truncateHash } from '../utils/dom.js';

// Renders the dashboard view with key blockchain statistics
export async function dashboard(root) {
    root.innerHTML = ''; // Clear previous content
    root.appendChild(createLoadingSpinner()); // Show loading spinner

    try {
        const [allBlocksData, latestBlock] = await Promise.all([
            api.allBlocks(0, 1), // Fetch just one block to get total elements
            api.latest()
        ]);

        const totalBlocks = allBlocksData.page ? allBlocksData.page.totalElements : (allBlocksData._embedded?.blockList?.length || 0);
        let pendingTxCount = 'N/A';
        try {
            const txCountData = await api.txCount();
            pendingTxCount = txCountData.count !== undefined ? txCountData.count.toString() : 'Error';
        } catch (txError) {
            console.error('Error fetching pending transaction count:', txError);
            pendingTxCount = 'Error';
        }

        root.innerHTML = ''; // Clear spinner once data is fetched

        const cards = createEl('div', { className: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6 mb-8' },
            // Latest Block Card
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3 transform transition duration-300 hover:scale-105' },
                createEl('div', { className: 'flex items-center space-x-3' },
                    createEl('div', { className: 'flex-shrink-0 bg-blue-100 text-blue-600 rounded-full p-3' },
                        createEl('i', { className: 'fas fa-cube text-xl' })
                    ),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Último Bloque')
                ),
                createEl('p', { className: 'text-2xl font-bold text-gray-900' }, `Index: ${latestBlock.index}`),
                createEl('p', { className: 'text-sm text-gray-600 font-mono break-all' }, truncateHash(latestBlock.hash, 24)),
                createEl('a', { href: '#blocks/' + latestBlock.hash, className: 'text-blue-500 hover:underline text-sm mt-2' }, 'Ver Detalles')
            ),
            // Total Blocks Card
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3 transform transition duration-300 hover:scale-105' },
                createEl('div', { className: 'flex items-center space-x-3' },
                    createEl('div', { className: 'flex-shrink-0 bg-green-100 text-green-600 rounded-full p-3' },
                        createEl('i', { className: 'fas fa-layer-group text-xl' })
                    ),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Total de Bloques')
                ),
                createEl('p', { className: 'text-4xl font-bold text-gray-900' }, totalBlocks.toLocaleString())
            ),
            // Pending Transactions Card
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3 transform transition duration-300 hover:scale-105' },
                createEl('div', { className: 'flex items-center space-x-3' },
                    createEl('div', { className: 'flex-shrink-0 bg-yellow-100 text-yellow-600 rounded-full p-3' },
                        createEl('i', { className: 'fas fa-hourglass-half text-xl' })
                    ),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Transacciones Pendientes')
                ),
                createEl('p', { className: 'text-4xl font-bold text-gray-900' }, pendingTxCount)
            ),
            // Latest Block Transactions Count
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3 transform transition duration-300 hover:scale-105' },
                createEl('div', { className: 'flex items-center space-x-3' },
                    createEl('div', { className: 'flex-shrink-0 bg-purple-100 text-purple-600 rounded-full p-3' },
                        createEl('i', { className: 'fas fa-list-alt text-xl' })
                    ),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'TXs en Último Bloque')
                ),
                createEl('p', { className: 'text-4xl font-bold text-gray-900' }, latestBlock.data.length.toLocaleString())
            )
        );

        // Recent Blocks Section
        const recentBlocksSection = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl' },
            createEl('h2', { className: 'text-2xl font-bold text-gray-900 mb-6 border-b pb-4' }, 'Bloques Recientes'),
            createEl('div', { id: 'recent-blocks-list', className: 'space-y-4' })
        );

        const recentBlocksContainer = recentBlocksSection.querySelector('#recent-blocks-list');
        const recentBlocksData = await api.allBlocks(0, 5); // Fetch 5 most recent blocks
        const blocks = recentBlocksData._embedded?.blockList || [];

        if (blocks.length > 0) {
            blocks.forEach(block => {
                const blockCard = createEl('div', { className: 'border border-gray-200 rounded-md p-4 bg-gray-50 hover:bg-gray-100 transition-colors' },
                    createEl('div', { className: 'flex justify-between items-center mb-2' },
                        createEl('span', { className: 'font-semibold text-blue-600 text-lg' }, `Bloque #${block.index.toLocaleString()}`),
                        createEl('span', { className: 'text-sm text-gray-500' }, new Date(block.timestamp * 1000).toLocaleString())
                    ),
                    createEl('div', { className: 'flex items-center mb-2' },
                        createEl('span', { className: 'text-gray-700 font-medium mr-2' }, 'Hash:'),
                        createEl('span', { className: 'font-mono text-sm break-all' }, truncateHash(block.hash, 30)),
                        createEl('button', {
                            className: 'ml-2 text-gray-400 hover:text-gray-600 focus:outline-none',
                            onClick: () => copyToClipboard(block.hash)
                        }, createEl('i', { className: 'fas fa-copy text-xs' }))
                    ),
                    createEl('div', { className: 'flex items-center' },
                        createEl('span', { className: 'text-gray-700 font-medium mr-2' }, 'Transacciones:'),
                        createEl('span', { className: 'text-sm text-gray-900' }, block.data.length.toLocaleString())
                    ),
                    createEl('a', { href: `#blocks/${block.hash}`, className: 'text-blue-500 hover:underline text-sm mt-3 inline-block' }, 'Ver Detalles del Bloque')
                );
                recentBlocksContainer.append(blockCard);
            });
        } else {
            recentBlocksContainer.append(createEl('p', { className: 'text-gray-600 italic' }, 'No hay bloques recientes para mostrar.'));
        }


        root.append(cards, recentBlocksSection);
    } catch (error) {
        root.innerHTML = ''; // Clear spinner
        root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' },
            createEl('strong', { className: 'font-bold' }, 'Error: '),
            createEl('span', { className: 'block sm:inline' }, 'No se pudo cargar el dashboard. Intenta de nuevo más tarde.')
        ));
        console.error('Error in dashboard component:', error);
    }
}
