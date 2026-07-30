import { api } from '../services/api.js';
import { createEl, createLoadingSpinner, truncateHash, copyToClipboard } from '../utils/dom.js';
import { getWalletAddress } from '../utils/crypto.js';

export async function dashboard(root) {
    root.innerHTML = '';
    root.appendChild(createLoadingSpinner());

    try {
        let allBlocksData, latestBlock, pendingTxCountData;
        try {
            [allBlocksData, latestBlock, pendingTxCountData] = await Promise.all([
                api.allBlocks(0, 1),
                api.latest(),
                api.txCount().catch(() => ({ count: 0 }))
            ]);
        } catch (e) {
            root.innerHTML = '';
            root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded' },
                createEl('strong', { className: 'font-bold' }, 'Error: '),
                'No se pudo conectar con el coordinador en localhost:8080. ¿Está corriendo?'
            ));
            return;
        }

        let systemConfig = {};
        let myBalance = { balance: 0 };
        try { systemConfig = await api.config(); } catch (e) { console.log('Config no disponible'); }
        try { myBalance = await api.getBalance(getWalletAddress()); } catch (e) { console.log('Balance no disponible'); }

        const totalBlocks = allBlocksData.page ? allBlocksData.page.totalElements : (allBlocksData._embedded?.blockList?.length || 0);
        const pendingTxCount = pendingTxCountData.count !== undefined ? pendingTxCountData.count.toLocaleString() : '0';

        const isTesting = systemConfig.mode === 'testing';
        const isGenesis = systemConfig.rewardSource === 'genesis';
        const systemRemaining = systemConfig.genesisSupply !== undefined ? Number(systemConfig.genesisSupply).toLocaleString() : 'N/A';
        const baseReward = systemConfig.baseReward || 50;
        const halvingInterval = systemConfig.halvingInterval || 10;
        const currentHalving = Math.floor(totalBlocks / halvingInterval);
        const currentReward = isGenesis ? (baseReward / Math.pow(2, currentHalving)) : 20;
        const nextHalvingBlock = (currentHalving + 1) * halvingInterval;
        const blocksUntilHalving = Math.max(0, nextHalvingBlock - totalBlocks);

        root.innerHTML = '';

        if (systemConfig.mode) {
            const modeColor = isTesting ? 'bg-orange-100 text-orange-800 border-orange-300' : 'bg-green-100 text-green-800 border-green-300';
            root.append(createEl('div', { className: `mb-6 p-4 rounded-lg border ${modeColor} flex items-center justify-between` },
                createEl('div', { className: 'flex items-center space-x-3' },
                    createEl('i', { className: `fas ${isTesting ? 'fa-flask' : 'fa-shield-alt'} text-xl` }),
                    createEl('div', {},
                        createEl('span', { className: 'font-bold text-lg' }, `Modo: ${systemConfig.mode.toUpperCase()}`),
                        createEl('p', { className: 'text-sm opacity-80' }, isTesting ? 'Faucet habilitado. Validación libre.' : 'Validación de saldo activa.')
                    )
                ),
                isGenesis && createEl('div', { className: 'text-right' },
                    createEl('p', { className: 'text-sm font-semibold' }, 'Fondo Restante'),
                    createEl('p', { className: 'text-2xl font-bold' }, systemRemaining)
                )
            ));
        }

        if (isTesting) {
            root.append(createEl('div', { className: 'bg-gradient-to-r from-orange-50 to-yellow-50 p-6 rounded-xl mb-6 border-2 border-orange-200 border-dashed' },
                createEl('div', { className: 'flex items-center justify-between' },
                    createEl('div', {},
                        createEl('h3', { className: 'text-lg font-bold text-orange-800' }, '🚰 Fondo de Pruebas'),
                        createEl('p', { className: 'text-sm text-orange-700' }, 'Cargá saldo a tu wallet para el demo.')
                    ),
                    createEl('button', {
                        className: 'px-6 py-3 bg-orange-500 text-white font-bold rounded-lg hover:bg-orange-600 transition-transform hover:scale-105',
                        onClick: async (e) => {
                            const btn = e.target;
                            btn.disabled = true; btn.textContent = '...';
                            try {
                                const res = await api.faucet({ publicKey: getWalletAddress(), amount: 10000 });
                                btn.textContent = `+${Number(res.amount).toLocaleString()} ✓`;
                                setTimeout(() => location.reload(), 1000);
                            } catch (err) {
                                btn.textContent = 'Error (¿/api/faucet?)';
                                setTimeout(() => { btn.disabled = false; btn.textContent = 'Cargar 10,000'; }, 3000);
                            }
                        }
                    }, 'Cargar 10,000')
                )
            ));
        }

        const cards = createEl('div', { className: 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8' },
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' },
                createEl('div', { className: 'flex items-center space-x-3 mb-2' },
                    createEl('div', { className: 'bg-blue-100 text-blue-600 rounded-full p-3' }, createEl('i', { className: 'fas fa-cube' })),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Último Bloque')
                ),
                createEl('p', { className: 'text-2xl font-bold' }, `#${latestBlock.index}`),
                createEl('p', { className: 'text-xs font-mono text-gray-500 break-all' }, truncateHash(latestBlock.hash, 20)),
                createEl('a', { href: '#blocks/' + latestBlock.hash, className: 'text-blue-500 text-sm hover:underline' }, 'Ver detalles')
            ),
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' },
                createEl('div', { className: 'flex items-center space-x-3 mb-2' },
                    createEl('div', { className: 'bg-green-100 text-green-600 rounded-full p-3' }, createEl('i', { className: 'fas fa-layer-group' })),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Total Bloques')
                ),
                createEl('p', { className: 'text-4xl font-bold' }, totalBlocks.toLocaleString())
            ),
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' },
                createEl('div', { className: 'flex items-center space-x-3 mb-2' },
                    createEl('div', { className: 'bg-yellow-100 text-yellow-600 rounded-full p-3' }, createEl('i', { className: 'fas fa-hourglass-half' })),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Pendientes')
                ),
                createEl('p', { className: 'text-4xl font-bold' }, pendingTxCount)
            ),
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' },
                createEl('div', { className: 'flex items-center space-x-3 mb-2' },
                    createEl('div', { className: 'bg-purple-100 text-purple-600 rounded-full p-3' }, createEl('i', { className: 'fas fa-wallet' })),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Tu Balance')
                ),
                createEl('p', { className: 'text-4xl font-bold' }, Number(myBalance.balance || 0).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })),
                createEl('p', { className: 'text-xs text-gray-400 font-mono' }, truncateHash(getWalletAddress(), 16))
            ),
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' },
                createEl('div', { className: 'flex items-center space-x-3 mb-2' },
                    createEl('div', { className: 'bg-indigo-100 text-indigo-600 rounded-full p-3' }, createEl('i', { className: 'fas fa-coins' })),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Recompensa')
                ),
                createEl('p', { className: 'text-4xl font-bold' }, currentReward.toFixed(2)),
                isGenesis && createEl('p', { className: 'text-xs text-gray-500' }, `Halving #${currentHalving} — próx en ${blocksUntilHalving} bloques`)
            ),
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' },
                createEl('div', { className: 'flex items-center space-x-3 mb-2' },
                    createEl('div', { className: 'bg-pink-100 text-pink-600 rounded-full p-3' }, createEl('i', { className: 'fas fa-list-alt' })),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'TXs Último Bloque')
                ),
                createEl('p', { className: 'text-4xl font-bold' }, (latestBlock.data || []).length.toLocaleString())
            ),
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' },
                createEl('div', { className: 'flex items-center space-x-3 mb-2' },
                    createEl('div', { className: 'bg-teal-100 text-teal-600 rounded-full p-3' }, createEl('i', { className: 'fas fa-fingerprint' })),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Dificultad')
                ),
                createEl('p', { className: 'text-xl font-mono font-bold' }, systemConfig.difficulty || 'N/A')
            ),
            createEl('div', { className: 'bg-white p-6 rounded-lg shadow-md' },
                createEl('div', { className: 'flex items-center space-x-3 mb-2' },
                    createEl('div', { className: 'bg-cyan-100 text-cyan-600 rounded-full p-3' }, createEl('i', { className: 'fas fa-tachometer-alt' })),
                    createEl('h3', { className: 'text-lg font-semibold text-gray-700' }, 'Supply')
                ),
                createEl('p', { className: 'text-4xl font-bold' }, isGenesis ? (Number(systemConfig.genesisSupply || 0) - Number(systemRemaining.replace(/,/g, ''))).toLocaleString() : (totalBlocks * 20).toLocaleString())
            )
        );

        root.append(cards);

        const recentSection = createEl('div', { className: 'bg-white p-6 rounded-xl shadow-lg mt-6' },
            createEl('h2', { className: 'text-xl font-bold mb-4 border-b pb-2' }, 'Bloques Recientes')
        );
        const recentList = createEl('div', { className: 'space-y-3' });
        try {
            const recent = await api.allBlocks(0, 5);
            const blocks = recent._embedded?.blockList || [];
            blocks.forEach(b => {
                const isReward = b.data && b.data.length === 1 && (b.data[0].sender === 'system' || b.data[0].sender === systemConfig.systemAddress);
                recentList.append(createEl('div', { className: 'border rounded p-3 hover:bg-gray-50 flex justify-between items-center' },
                    createEl('div', {},
                        createEl('span', { className: 'font-bold text-blue-600' }, `#${b.index}`),
                        isReward && createEl('span', { className: 'ml-2 px-2 py-0.5 bg-yellow-100 text-yellow-700 text-xs rounded-full font-bold' }, 'RECOMPENSA'),
                        createEl('span', { className: 'text-xs text-gray-500 ml-2 font-mono' }, truncateHash(b.hash, 16))
                    ),
                    createEl('span', { className: 'text-sm text-gray-600' }, `${(b.data || []).length} TXs`)
                ));
            });
        } catch (e) { recentList.append(createEl('p', { className: 'text-gray-500' }, 'No se pudieron cargar bloques recientes.')); }
        recentSection.append(recentList);
        root.append(recentSection);

    } catch (error) {
        root.innerHTML = '';
        root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded' },
            createEl('strong', { className: 'font-bold' }, 'Error: '),
            error.message || 'No se pudo cargar el dashboard.'
        ));
        console.error('Dashboard error:', error);
    }
}