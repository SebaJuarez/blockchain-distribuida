import { api } from '../services/api.js';
import { createEl, truncateHash, copyToClipboard, createLoadingSpinner, generateRandomAddress, shortenId } from '../utils/dom.js';
import { signTransaction, getWalletAddress } from '../utils/crypto.js';

export async function transactions(root) {
    root.innerHTML = '';
    root.appendChild(createLoadingSpinner());

    let currentDifficulty = 'Cargando...';
    let myBalance = 0;
    let systemConfig = {};

    async function updateDifficultyDisplay() {
        const difficultyDisplayEl = document.getElementById('current-difficulty-display');
        if (difficultyDisplayEl) {
            difficultyDisplayEl.textContent = 'Cargando...';
        }
        try {
            currentDifficulty = await api.getDifficulty();
            if (difficultyDisplayEl) difficultyDisplayEl.textContent = currentDifficulty || 'N/A';
        } catch (error) {
            console.error('Error al obtener la dificultad:', error);
            currentDifficulty = 'Error';
            if (difficultyDisplayEl) difficultyDisplayEl.textContent = 'Error al cargar';
        }
    }

    async function refreshBalance() {
        try {
            const res = await api.getBalance(getWalletAddress());
            myBalance = res.balance || 0;
            const el = document.getElementById('my-balance-display');
            if (el) el.textContent = Number(myBalance).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 });
        } catch (e) {
            console.error('Error leyendo balance:', e);
        }
    }

    try {
        const [txData, configRes] = await Promise.all([
            api.txs(),
            api.config().catch(() => ({}))
        ]);
        systemConfig = configRes;
        const pendingTransactions = txData._embedded ? txData._embedded.transactionList : [];
        const isTesting = systemConfig.mode === 'testing';
        const isGenesis = systemConfig.rewardSource === 'genesis';

        root.innerHTML = '';

        await refreshBalance();

        // Wallet & Balance Header
        const walletAddress = getWalletAddress();
        const headerCard = createEl('div', { className: 'bg-gradient-to-r from-blue-600 to-indigo-700 text-white p-6 rounded-xl shadow-lg mb-6' },
            createEl('div', { className: 'flex flex-col md:flex-row md:items-center md:justify-between' },
                createEl('div', {},
                    createEl('h2', { className: 'text-2xl font-bold mb-1' }, 'Tu Wallet'),
                    createEl('div', { className: 'flex items-center space-x-2 text-blue-100' },
                        createEl('span', { className: 'font-mono text-sm' }, shortenId(walletAddress, 12, 8)),
                        createEl('button', {
                            className: 'text-blue-200 hover:text-white transition-colors',
                            onClick: () => copyToClipboard(walletAddress)
                        }, createEl('i', { className: 'fas fa-copy' }))
                    )
                ),
                createEl('div', { className: 'mt-4 md:mt-0 text-right' },
                    createEl('p', { className: 'text-blue-200 text-sm' }, 'Balance Disponible'),
                    createEl('p', { id: 'my-balance-display', className: 'text-4xl font-bold' },
                        Number(myBalance).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })
                    ),
                    createEl('button', {
                        className: 'mt-2 text-xs bg-white/20 hover:bg-white/30 px-3 py-1 rounded transition-colors',
                        onClick: refreshBalance
                    }, createEl('i', { className: 'fas fa-sync-alt mr-1' }), 'Actualizar')
                )
            )
        );

        // System Status Bar
        const statusBar = createEl('div', { className: 'grid grid-cols-1 md:grid-cols-3 gap-4 mb-6' },
            createEl('div', { className: 'bg-white p-4 rounded-lg shadow flex items-center space-x-3' },
                createEl('div', { className: `w-3 h-3 rounded-full ${isTesting ? 'bg-orange-400' : 'bg-green-500'}` }),
                createEl('div', {},
                    createEl('p', { className: 'text-xs text-gray-500 uppercase font-bold' }, 'Modo'),
                    createEl('p', { className: 'font-semibold' }, isTesting ? 'Testing' : 'Production')
                )
            ),
            createEl('div', { className: 'bg-white p-4 rounded-lg shadow flex items-center space-x-3' },
                createEl('i', { className: 'fas fa-coins text-yellow-500' }),
                createEl('div', {},
                    createEl('p', { className: 'text-xs text-gray-500 uppercase font-bold' }, 'Fondo Sistema'),
                    createEl('p', { className: 'font-semibold' }, isGenesis && systemConfig.genesisSupply
                        ? Number(systemConfig.genesisSupply).toLocaleString()
                        : '∞')
                )
            ),
            createEl('div', { className: 'bg-white p-4 rounded-lg shadow flex items-center space-x-3' },
                createEl('i', { className: 'fas fa-shield-alt text-blue-500' }),
                createEl('div', {},
                    createEl('p', { className: 'text-xs text-gray-500 uppercase font-bold' }, 'Validación'),
                    createEl('p', { className: 'font-semibold' }, isTesting ? 'Libre (sin saldo)' : 'Requiere fondos')
                )
            )
        );

        // Faucet Card (Testing only)
        let faucetCard = null;
        if (isTesting) {
            faucetCard = createEl('div', { className: 'bg-orange-50 border-2 border-orange-300 border-dashed p-6 rounded-xl mb-6' },
                createEl('div', { className: 'flex items-center justify-between' },
                    createEl('div', {},
                        createEl('h3', { className: 'text-lg font-bold text-orange-800' }, '🚰 Faucet de Pruebas'),
                        createEl('p', { className: 'text-sm text-orange-700' }, 'Recarga tu wallet con fondos del sistema para hacer transacciones.')
                    ),
                    createEl('div', { className: 'flex space-x-2' },
                        createEl('button', {
                            className: 'px-4 py-2 bg-orange-500 text-white rounded-lg hover:bg-orange-600 transition-colors font-semibold',
                            onClick: async (e) => {
                                const btn = e.target;
                                btn.disabled = true; btn.textContent = '...';
                                try {
                                    const res = await api.faucet({ publicKey: walletAddress, amount: 1000 });
                                    await refreshBalance();
                                    btn.textContent = '+1,000 ✓';
                                    setTimeout(() => { btn.disabled = false; btn.textContent = '+1,000'; }, 1500);
                                } catch (err) { btn.textContent = 'Error'; setTimeout(() => { btn.disabled = false; btn.textContent = '+1,000'; }, 1500); }
                            }
                        }, '+1,000'),
                        createEl('button', {
                            className: 'px-4 py-2 bg-orange-600 text-white rounded-lg hover:bg-orange-700 transition-colors font-semibold',
                            onClick: async (e) => {
                                const btn = e.target;
                                btn.disabled = true; btn.textContent = '...';
                                try {
                                    const res = await api.faucet({ publicKey: walletAddress, amount: 10000 });
                                    await refreshBalance();
                                    btn.textContent = '+10,000 ✓';
                                    setTimeout(() => { btn.disabled = false; btn.textContent = '+10,000'; }, 1500);
                                } catch (err) { btn.textContent = 'Error'; setTimeout(() => { btn.disabled = false; btn.textContent = '+10,000'; }, 1500); }
                            }
                        }, '+10,000')
                    )
                )
            );
        }

        // Single Transaction Form
        const singleTxCard = createEl('div', { className: 'bg-white p-6 rounded-xl shadow-md mb-6' },
            createEl('h3', { className: 'text-xl font-bold text-gray-900 mb-4 border-b pb-2' }, 'Enviar Transacción Individual')
        );

        const singleTxForm = createEl('form', { className: 'grid grid-cols-1 md:grid-cols-12 gap-4 items-end' },
            createEl('div', { className: 'md:col-span-5' },
                createEl('label', { className: 'block text-sm font-medium text-gray-700 mb-1' }, 'Destinatario (Public Key)'),
                createEl('input', {
                    id: 'single-tx-receiver',
                    type: 'text',
                    className: 'w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 font-mono text-sm',
                    placeholder: '04a1b2... o addr123'
                })
            ),
            createEl('div', { className: 'md:col-span-3' },
                createEl('label', { className: 'block text-sm font-medium text-gray-700 mb-1' }, 'Monto'),
                createEl('input', {
                    id: 'single-tx-amount',
                    type: 'number',
                    step: '0.01',
                    min: '0.01',
                    className: 'w-full p-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500',
                    placeholder: '100.00'
                })
            ),
            createEl('div', { className: 'md:col-span-4 flex space-x-2' },
                createEl('button', {
                    type: 'submit',
                    className: 'flex-1 px-4 py-2 bg-blue-600 text-white font-semibold rounded-lg hover:bg-blue-700 transition-colors flex items-center justify-center space-x-2'
                }, createEl('i', { className: 'fas fa-paper-plane' }), createEl('span', {}, 'Enviar')),
                createEl('button', {
                    type: 'button',
                    className: 'px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300 transition-colors',
                    onClick: () => {
                        document.getElementById('single-tx-receiver').value = generateRandomAddress();
                        document.getElementById('single-tx-amount').value = (Math.floor(Math.random() * 100) + 1).toString();
                    }
                }, createEl('i', { className: 'fas fa-dice' }))
            )
        );

        const singleTxMsg = createEl('div', { id: 'single-tx-msg', className: 'mt-3' });

        singleTxForm.addEventListener('submit', async (e) => {
            e.preventDefault();
            const receiver = document.getElementById('single-tx-receiver').value.trim();
            const amount = parseFloat(document.getElementById('single-tx-amount').value);
            const btn = singleTxForm.querySelector('button[type="submit"]');
            const originalHtml = btn.innerHTML;

            singleTxMsg.innerHTML = '';

            if (!receiver) {
                singleTxMsg.append(createEl('div', { className: 'bg-yellow-100 border border-yellow-400 text-yellow-700 px-3 py-2 rounded text-sm' }, 'Ingresá un destinatario.'));
                return;
            }
            if (!amount || amount <= 0) {
                singleTxMsg.append(createEl('div', { className: 'bg-yellow-100 border border-yellow-400 text-yellow-700 px-3 py-2 rounded text-sm' }, 'Ingresá un monto válido.'));
                return;
            }
            if (!isTesting && myBalance < amount) {
                singleTxMsg.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-3 py-2 rounded text-sm' },
                    `Fondos insuficientes. Tenés ${myBalance.toFixed(2)}, necesitás ${amount.toFixed(2)}.`
                ));
                return;
            }

            btn.disabled = true;
            btn.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i> Enviando...';

            try {
                const signed = await signTransaction(receiver, amount);
                await api.createTx(signed);
                await refreshBalance();
                singleTxMsg.append(createEl('div', { className: 'bg-green-100 border border-green-400 text-green-700 px-3 py-2 rounded text-sm' }, '¡Transacción enviada!'));
                document.getElementById('single-tx-receiver').value = '';
                document.getElementById('single-tx-amount').value = '';
                setTimeout(() => transactions(root), 2000);
            } catch (err) {
                singleTxMsg.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-3 py-2 rounded text-sm' }, `Error: ${err.message || 'Rechazada'}`));
            } finally {
                btn.disabled = false;
                btn.innerHTML = originalHtml;
            }
        });

        singleTxCard.append(singleTxForm, singleTxMsg);

        // Pending Transactions
        const listCard = createEl('div', { className: 'bg-white p-6 rounded-xl shadow-md mb-6' },
            createEl('h2', { className: 'text-xl font-bold text-gray-900 mb-4 border-b pb-2' }, `Transacciones Pendientes (${pendingTransactions.length})`)
        );

        if (pendingTransactions.length > 0) {
            const table = createEl('div', { className: 'overflow-x-auto' },
                createEl('table', { className: 'min-w-full divide-y divide-gray-200 text-sm' },
                    createEl('thead', { className: 'bg-gray-50' },
                        createEl('tr', {},
                            createEl('th', { className: 'px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase' }, 'ID'),
                            createEl('th', { className: 'px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase' }, 'Remitente'),
                            createEl('th', { className: 'px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase' }, 'Receptor'),
                            createEl('th', { className: 'px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase' }, 'Monto'),
                            createEl('th', { className: 'px-4 py-2 text-left text-xs font-medium text-gray-500 uppercase' }, 'Hora')
                        )
                    ),
                    createEl('tbody', { className: 'bg-white divide-y divide-gray-200' },
                        ...pendingTransactions.map(tx => createEl('tr', { className: 'hover:bg-gray-50' },
                            createEl('td', { className: 'px-4 py-2 font-mono text-xs text-blue-600' }, shortenId(tx.id, 6, 4)),
                            createEl('td', { className: 'px-4 py-2 font-mono text-xs' }, shortenId(tx.sender, 6, 4)),
                            createEl('td', { className: 'px-4 py-2 font-mono text-xs' }, shortenId(tx.receiver, 6, 4)),
                            createEl('td', { className: 'px-4 py-2 font-semibold' }, tx.amount.toLocaleString()),
                            createEl('td', { className: 'px-4 py-2 text-gray-500 text-xs' }, new Date(tx.timestamp * 1000).toLocaleTimeString())
                        ))
                    )
                )
            );
            listCard.append(table);
        } else {
            listCard.append(createEl('p', { className: 'text-gray-500 italic text-center py-4' }, 'No hay transacciones pendientes.'));
        }

        // Batch Form (preserved, simplified)
        const batchCard = createEl('div', { className: 'bg-white p-6 rounded-xl shadow-md mb-6' },
            createEl('h3', { className: 'text-lg font-bold text-gray-900 mb-3 border-b pb-2' }, 'Enviar Batch de Transacciones'),
            createEl('p', { className: 'text-sm text-gray-600 mb-3' }, 'Formato: [ {"receiver":"...","amount":10}, ... ]'),
            createEl('textarea', {
                id: 'batch-transactions-textarea',
                className: 'w-full p-3 border border-gray-300 rounded-lg font-mono text-sm mb-3',
                placeholder: '[ { "receiver": "addr2", "amount": 10 } ]',
                rows: 4
            }),
            createEl('button', {
                className: 'px-4 py-2 bg-gray-800 text-white rounded-lg hover:bg-gray-900 transition-colors',
                onClick: async () => {
                    const textarea = document.getElementById('batch-transactions-textarea');
                    try {
                        const arr = JSON.parse(textarea.value);
                        if (!Array.isArray(arr)) throw new Error('Debe ser un array');
                        const signed = await Promise.all(arr.map(tx => signTransaction(tx.receiver, tx.amount)));
                        await Promise.all(signed.map(tx => api.createTx(tx)));
                        textarea.value = '';
                        alert('Batch enviado correctamente');
                        transactions(root);
                    } catch (err) {
                        alert('Error: ' + err.message);
                    }
                }
            }, 'Enviar Batch')
        );

        // Random Generator
        const randomCard = createEl('div', { className: 'bg-white p-6 rounded-xl shadow-md mb-6' },
            createEl('h3', { className: 'text-lg font-bold text-gray-900 mb-3 border-b pb-2' }, 'Generar Transacciones Aleatorias'),
            createEl('div', { className: 'flex items-center space-x-4' },
                createEl('div', {},
                    createEl('label', { className: 'text-sm text-gray-600' }, 'Cantidad'),
                    createEl('input', { id: 'num-random-txs', type: 'number', value: '10', min: '1', max: '1000', className: 'w-24 p-2 border rounded' })
                ),
                createEl('button', {
                    className: 'px-4 py-2 bg-green-600 text-white rounded-lg hover:bg-green-700 font-semibold',
                    onClick: async () => {
                        const n = parseInt(document.getElementById('num-random-txs').value, 10);
                        for (let i = 0; i < n; i++) {
                            const signed = await signTransaction(generateRandomAddress(), Math.floor(Math.random() * 100) + 1);
                            await api.createTx(signed);
                        }
                        alert(`${n} transacciones enviadas`);
                        transactions(root);
                    }
                }, 'Generar y Enviar')
            )
        );

        // Difficulty
        const difficultyCard = createEl('div', { className: 'bg-white p-6 rounded-xl shadow-md' },
            createEl('h3', { className: 'text-lg font-bold text-gray-900 mb-3 border-b pb-2' }, 'Configuración de Dificultad'),
            createEl('div', { className: 'flex items-center space-x-2 mb-3' },
                createEl('span', { className: 'text-gray-600' }, 'Actual:'),
                createEl('span', { id: 'current-difficulty-display', className: 'font-mono text-xl text-blue-600 font-bold' }, currentDifficulty)
            ),
            createEl('div', { className: 'flex space-x-2' },
                createEl('input', {
                    id: 'new-difficulty-input',
                    type: 'text',
                    className: 'flex-1 p-2 border border-gray-300 rounded-lg font-mono',
                    placeholder: 'Ej: 0000'
                }),
                createEl('button', {
                    className: 'px-4 py-2 bg-purple-600 text-white rounded-lg hover:bg-purple-700 font-semibold',
                    onClick: async () => {
                        const val = document.getElementById('new-difficulty-input').value.trim();
                        if (!val) return;
                        try {
                            const res = await api.setDifficulty(val);
                            alert('Dificultad actualizada: ' + res);
                            updateDifficultyDisplay();
                        } catch (e) {
                            alert('Error: ' + e.message);
                        }
                    }
                }, 'Cambiar')
            )
        );

        root.append(headerCard, statusBar);
        if (faucetCard) root.append(faucetCard);
        root.append(singleTxCard, listCard, batchCard, randomCard, difficultyCard);

        await updateDifficultyDisplay();

    } catch (error) {
        root.innerHTML = '';
        root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' },
            createEl('strong', { className: 'font-bold' }, 'Error: '),
            createEl('span', { className: 'block sm:inline' }, 'No se pudieron cargar las transacciones. Intenta de nuevo más tarde.')
        ));
        console.error('Error in transactions component:', error);
    }
}