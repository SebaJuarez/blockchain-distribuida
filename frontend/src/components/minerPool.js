import { api } from '../services/api.js';
import { createEl, createLoadingSpinner, shortenId, copyToClipboard } from '../utils/dom.js';

export async function minerPool(root) {
    root.innerHTML = '';
    root.appendChild(createLoadingSpinner());

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

        root.innerHTML = '';

        const poolContainer = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl' },
            createEl('h2', { className: 'text-2xl font-bold text-gray-900 mb-6 border-b pb-4' }, 'Pool de Nodos Mineros'),
            createEl('p', { className: 'text-gray-600 mb-4' }, `Mineros activos: ${minersWithBalance.length}`)
        );

        if (minersWithBalance.length === 0) {
            poolContainer.append(createEl('p', { className: 'text-gray-600 italic' }, 'No hay mineros registrados en el pool en este momento.'));
        } else {
            const tableWrapper = createEl('div', { className: 'overflow-x-auto' },
                createEl('table', { className: 'min-w-full divide-y divide-gray-200' },
                    createEl('thead', { className: 'bg-gray-50' },
                        createEl('tr', {},
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Public Key'),
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Balance'),
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Último Keep-Alive'),
                            createEl('th', { className: 'px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'GPU')
                        )
                    ),
                    createEl('tbody', { className: 'bg-white divide-y divide-gray-200' },
                        ...minersWithBalance.map(miner => createEl('tr', { className: 'hover:bg-gray-50 transition-colors' },
                            createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm font-medium text-blue-600' },
                                createEl('div', { className: 'flex items-center' },
                                    createEl('span', { className: 'font-mono text-xs md:text-sm' }, shortenId(miner.publicKey, 10, 10)),
                                    createEl('button', {
                                        className: 'ml-2 text-gray-400 hover:text-gray-600 focus:outline-none',
                                        onClick: (e) => { e.preventDefault(); copyToClipboard(miner.publicKey); }
                                    }, createEl('i', { className: 'fas fa-copy text-xs' }))
                                )
                            ),
                            createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm font-bold text-gray-900' },
                                Number(miner.balance).toFixed(2)
                            ),
                            createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm text-gray-900' },
                                new Date(miner.lastTimestamp).toLocaleString()
                            ),
                            createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm text-center' },
                                miner.gpuMiner
                                    ? createEl('span', { className: 'px-2 py-1 bg-green-100 text-green-700 rounded-full text-xs font-bold' }, 'GPU')
                                    : createEl('span', { className: 'px-2 py-1 bg-gray-100 text-gray-600 rounded-full text-xs font-bold' }, 'CPU')
                            )
                        ))
                    )
                )
            );
            poolContainer.append(tableWrapper);
        }
        root.append(poolContainer);
    } catch (error) {
        root.innerHTML = '';
        root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' },
            createEl('strong', { className: 'font-bold' }, 'Error: '),
            createEl('span', { className: 'block sm:inline' }, 'No se pudieron cargar los mineros del pool.')
        ));
        console.error('Error al cargar el pool de mineros:', error);
    }
}