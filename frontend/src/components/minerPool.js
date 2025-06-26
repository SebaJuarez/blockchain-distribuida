import { api } from '../services/api.js';
import { createEl, createLoadingSpinner, shortenId, copyToClipboard } from '../utils/dom.js';

let currentMinerPoolIntervalId = null;

export async function minerPool(root) {
    // Limpiar cualquier intervalo anterior si ya existía uno
    if (currentMinerPoolIntervalId) {
        clearInterval(currentMinerPoolIntervalId);
        currentMinerPoolIntervalId = null;
    }

    async function fetchAndRenderMiners() {
        root.innerHTML = ''; // Limpiar contenido anterior antes de renderizar
        root.appendChild(createLoadingSpinner()); // Mostrar spinner de carga

        try {
            const data = await api.getMiners();
            // Asumiendo que la estructura de la respuesta es { "_embedded": { "minerList": [...] } }
            const miners = data._embedded ? data._embedded.minerList : [];

            root.innerHTML = ''; // Limpiar spinner una vez que los datos son obtenidos

            const poolContainer = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl' },
                createEl('h2', { className: 'text-2xl font-bold text-gray-900 mb-6 border-b pb-4' }, 'Pool de Nodos Mineros')
            );

            if (miners.length === 0) {
                poolContainer.append(createEl('p', { className: 'text-gray-600 italic' }, 'No hay mineros registrados en el pool en este momento.'));
            } else {
                const tableWrapper = createEl('div', { className: 'table-responsive' },
                    createEl('table', { className: 'min-w-full divide-y divide-gray-200' },
                        createEl('thead', { className: 'bg-gray-50' },
                            createEl('tr', {},
                                createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Public Key'),
                                createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Último Keep-Alive'),
                                createEl('th', { className: 'px-6 py-3 text-center text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Minería GPU')
                            )
                        ),
                        createEl('tbody', { className: 'bg-white divide-y divide-gray-200' },
                            ...miners.map(miner => createEl('tr', { className: 'hover:bg-gray-50 transition-colors' },
                                createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm font-medium text-blue-600' },
                                    createEl('div', { className: 'flex items-center' },
                                        createEl('span', { className: 'font-mono text-xs md:text-sm' }, shortenId(miner.publicKey, 10, 10)),
                                        createEl('button', {
                                            className: 'ml-2 text-gray-400 hover:text-gray-600 focus:outline-none',
                                            onClick: (e) => {
                                                e.preventDefault(); // Prevenir navegación si el botón está dentro de un enlace
                                                copyToClipboard(miner.publicKey);
                                            }
                                        }, createEl('i', { className: 'fas fa-copy text-xs' }))
                                    )
                                ),
                                createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm text-gray-900' },
                                    // Convierte el timestamp ISO a un formato legible local
                                    new Date(miner.lastTimestamp).toLocaleString()
                                ),
                                createEl('td', { className: 'px-6 py-4 whitespace-nowrap text-sm text-center' },
                                    miner.gpuMiner
                                        ? createEl('i', { className: 'fas fa-check-circle text-green-500 text-lg', title: 'Sí, minero GPU' })
                                        : createEl('i', { className: 'fas fa-times-circle text-red-500 text-lg', title: 'No, minero CPU' })
                                )
                            ))
                        )
                    )
                );
                poolContainer.append(tableWrapper);
            }
            root.append(poolContainer);
        } catch (error) {
            root.innerHTML = ''; // Limpiar spinner
            root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' },
                createEl('strong', { className: 'font-bold' }, 'Error: '),
                createEl('span', { className: 'block sm:inline' }, 'No se pudieron cargar los mineros del pool. Asegúrate de que la API esté funcionando correctamente.')
            ));
            console.error('Error al cargar el pool de mineros:', error);
        }
    }

    // Realiza la primera carga de datos
    fetchAndRenderMiners();
}