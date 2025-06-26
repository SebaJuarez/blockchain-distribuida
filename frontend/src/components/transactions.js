import { api } from '../services/api.js';
import { createEl, truncateHash, copyToClipboard, createLoadingSpinner, generateRandomAddress, shortenId } from '../utils/dom.js';

// Renders the pending transactions list, a batch submission form, and difficulty settings
export async function transactions(root) {
    root.innerHTML = ''; // Clear previous content
    root.appendChild(createLoadingSpinner()); // Show loading spinner

    let currentDifficulty = 'Cargando...'; // Estado para la dificultad

    // Función para actualizar y mostrar la dificultad
    async function updateDifficultyDisplay() {
        const difficultyDisplayEl = document.getElementById('current-difficulty-display');
        if (difficultyDisplayEl) {
            difficultyDisplayEl.textContent = 'Cargando...';
        }
        try {
            currentDifficulty = await api.getDifficulty();
            if (difficultyDisplayEl) {
                difficultyDisplayEl.textContent = currentDifficulty || 'N/A';
            }
        } catch (error) {
            console.error('Error al obtener la dificultad:', error);
            currentDifficulty = 'Error';
            if (difficultyDisplayEl) {
                difficultyDisplayEl.textContent = 'Error al cargar';
            }
        }
    }

    try {
        const txData = await api.txs();
        const pendingTransactions = txData._embedded ? txData._embedded.transactionList : [];

        root.innerHTML = ''; // Clear spinner once data is fetched

        // --- Sección de Transacciones Pendientes ---
        const listCard = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl mb-6' },
            createEl('h2', { className: 'text-2xl font-bold text-gray-900 mb-6 border-b pb-4' }, 'Transacciones Pendientes')
        );

        if (pendingTransactions.length > 0) {
            const table = createEl('div', { className: 'table-responsive' },
                createEl('table', { className: 'min-w-full divide-y divide-gray-200' },
                    createEl('thead', { className: 'bg-gray-50' },
                        createEl('tr', {},
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'ID TX'),
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Remitente'),
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Receptor'),
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Cantidad'),
                            createEl('th', { className: 'px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider' }, 'Marca de Tiempo')
                        )
                    ),
                    createEl('tbody', { className: 'bg-white divide-y divide-gray-200' },
                        ...pendingTransactions.map(tx => createEl('tr', { className: 'hover:bg-gray-50 transition-colors' },
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
            );
            listCard.append(table);
        } else {
            listCard.append(createEl('p', { className: 'text-gray-600 italic' }, 'No hay transacciones pendientes en este momento.'));
        }

        // --- Sección de Envío de Batch de Transacciones ---
        const formCard = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl' },
            createEl('h3', { className: 'text-2xl font-bold text-gray-900 mb-6 border-b pb-4' }, 'Enviar Batch de Transacciones')
        );

        const form = createEl('form', { className: 'space-y-4' },
            createEl('textarea', {
                id: 'batch-transactions-textarea',
                className: 'w-full p-3 border border-gray-300 rounded-md focus:ring-blue-500 focus:border-blue-500 resize-y font-mono text-sm',
                placeholder: '[ { "sender": "addr1", "receiver": "addr2", "amount": 10 }, { "sender": "addr3", "receiver": "addr4", "amount": 25 } ]',
                rows: 7
            }),
            createEl('button', {
                className: 'w-full px-6 py-3 bg-blue-600 text-white font-semibold rounded-lg shadow-md hover:bg-blue-700 transition-colors flex items-center justify-center space-x-2',
                type: 'submit'
            }, createEl('i', { className: 'fas fa-paper-plane' }), createEl('span', {}, 'Enviar Batch'))
        );

        form.addEventListener('submit', async e => {
            e.preventDefault();
            const textarea = form.querySelector('#batch-transactions-textarea');
            const button = form.querySelector('button[type="submit"]');
            const originalButtonText = button.innerHTML;

            button.disabled = true;
            button.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i> Enviando...';

            try {
                const arr = JSON.parse(textarea.value);
                if (!Array.isArray(arr)) {
                    throw new Error('El input debe ser un array JSON de transacciones.');
                }
                await Promise.all(arr.map(tx => api.createTx(tx)));
                textarea.value = '';
                const successMessage = createEl('div', { className: 'bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded relative mt-4' }, 'Transacciones enviadas con éxito!');
                formCard.append(successMessage);
                setTimeout(() => successMessage.remove(), 3000);
                transactions(root);
            } catch (error) {
                console.error('Error submitting batch transactions:', error);
                const errorMessage = createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative mt-4' }, `Error al enviar transacciones: ${error.message || 'Formato JSON inválido.'}`);
                formCard.append(errorMessage);
                setTimeout(() => errorMessage.remove(), 5000);
            } finally {
                button.disabled = false;
                button.innerHTML = originalButtonText;
            }
        });

        formCard.append(form);

        // --- Sección de Generación de Transacciones Aleatorias con Batching ---
        const randomGenerateCard = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl mt-6' },
            createEl('h3', { className: 'text-2xl font-bold text-gray-900 mb-6 border-b pb-4' }, 'Generar Transacciones Aleatorias por Batch'),
            createEl('div', { className: 'grid grid-cols-1 md:grid-cols-2 gap-4 items-center mb-4' },
                createEl('div', { className: 'flex items-center space-x-2' },
                    createEl('label', { htmlFor: 'num-random-txs', className: 'text-gray-700 text-lg' }, 'Total de TXs:'),
                    createEl('input', {
                        type: 'number',
                        id: 'num-random-txs',
                        className: 'w-24 p-2 border border-gray-300 rounded-md focus:ring-green-500 focus:border-green-500 text-sm',
                        value: '10',
                        min: '1',
                        max: '1000'
                    })
                ),
                createEl('div', { className: 'flex items-center space-x-2' },
                    createEl('label', { htmlFor: 'batch-size', className: 'text-gray-700 text-lg' }, 'TXs por Batch:'),
                    createEl('input', {
                        type: 'number',
                        id: 'batch-size',
                        className: 'w-24 p-2 border border-gray-300 rounded-md focus:ring-green-500 focus:border-green-500 text-sm',
                        value: '5',
                        min: '1',
                        max: '500'
                    })
                )
            ),
            createEl('button', {
                id: 'generate-random-batch-btn',
                className: 'w-full px-6 py-3 bg-green-600 text-white font-semibold rounded-lg shadow-md hover:bg-green-700 transition-colors flex items-center justify-center space-x-2',
                type: 'button'
            }, createEl('i', { className: 'fas fa-dice' }), createEl('span', {}, 'Generar y Enviar Batches Aleatorios'))
        );

        const randomTxMessageContainer = createEl('div', { id: 'random-tx-message-container', className: 'mt-4' });
        randomGenerateCard.append(randomTxMessageContainer);

        randomGenerateCard.querySelector('#generate-random-batch-btn').addEventListener('click', async () => {
            const numTxsInput = randomGenerateCard.querySelector('#num-random-txs');
            const batchSizeInput = randomGenerateCard.querySelector('#batch-size');
            const generateButton = randomGenerateCard.querySelector('#generate-random-batch-btn');

            const totalTxs = parseInt(numTxsInput.value, 10);
            const batchSize = parseInt(batchSizeInput.value, 10);

            randomTxMessageContainer.innerHTML = '';

            if (isNaN(totalTxs) || totalTxs < 1 || totalTxs > 1000) {
                const msg = createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' }, 'Por favor, introduce un número válido para "Total de TXs" (1-1000).');
                randomTxMessageContainer.append(msg);
                setTimeout(() => msg.remove(), 3000);
                return;
            }
            if (isNaN(batchSize) || batchSize < 1 || batchSize > 500) {
                const msg = createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' }, 'Por favor, introduce un número válido para "TXs por Batch" (1-500).');
                randomTxMessageContainer.append(msg);
                setTimeout(() => msg.remove(), 3000);
                return;
            }

            generateButton.disabled = true;
            const originalButtonContent = generateButton.innerHTML;
            generateButton.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i> Generando y Enviando...';

            let txSentCount = 0;
            let batchCount = 0;

            try {
                for (let i = 0; i < totalTxs; i += batchSize) {
                    batchCount++;
                    const currentBatch = [];
                    const limit = Math.min(i + batchSize, totalTxs);
                    for (let j = i; j < limit; j++) {
                        currentBatch.push({
                            sender: generateRandomAddress(),
                            receiver: generateRandomAddress(),
                            amount: Math.floor(Math.random() * 100) + 1
                        });
                    }

                    if (currentBatch.length > 0) {
                        const batchMessage = createEl('div', { className: 'bg-blue-100 border border-blue-400 text-blue-700 px-4 py-3 rounded relative mb-2' }, `Enviando Batch ${batchCount} (${currentBatch.length} TXs)...`);
                        randomTxMessageContainer.append(batchMessage);
                        await Promise.all(currentBatch.map(tx => api.createTx(tx)));
                        txSentCount += currentBatch.length;
                        batchMessage.className = 'bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded relative mb-2';
                        batchMessage.innerHTML = `<i class="fas fa-check-circle mr-2"></i> Batch ${batchCount} enviado con éxito (${currentBatch.length} TXs).`;
                        setTimeout(() => batchMessage.remove(), 3000);
                    }

                    if (i + batchSize < totalTxs) {
                        await new Promise(resolve => setTimeout(resolve, 500));
                    }
                }

                const finalMessage = createEl('div', { className: 'bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded relative' }, `Se generaron y enviaron ${txSentCount} transacciones en ${batchCount} batches.`);
                randomTxMessageContainer.append(finalMessage);
                setTimeout(() => finalMessage.remove(), 5000);
                transactions(root);
            } catch (error) {
                console.error('Error generating and sending random transactions:', error);
                const errorMessage = createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' }, `Error al enviar transacciones aleatorias: ${error.message || 'Error desconocido.'}`);
                randomTxMessageContainer.append(errorMessage);
                setTimeout(() => errorMessage.remove(), 5000);
            } finally {
                generateButton.disabled = false;
                generateButton.innerHTML = originalButtonContent;
            }
        });

        // --- Sección de Configuración de Dificultad ---
        const difficultyCard = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl mt-6' },
            createEl('h3', { className: 'text-2xl font-bold text-gray-900 mb-6 border-b pb-4' }, 'Configuración de Dificultad de Minería'),
            createEl('div', { className: 'mb-4 flex items-center space-x-2' },
                createEl('p', { className: 'text-gray-700 text-lg font-semibold' }, 'Dificultad Actual (Prefijo de Ceros):'),
                createEl('span', { id: 'current-difficulty-display', className: 'text-2xl font-mono text-blue-600' }, currentDifficulty)
            ),
            createEl('form', { id: 'set-difficulty-form', className: 'space-y-4' },
                createEl('label', { htmlFor: 'new-difficulty-input', className: 'block text-gray-700 text-lg mb-2' }, 'Establecer Nueva Dificultad:'),
                createEl('input', {
                    type: 'text',
                    id: 'new-difficulty-input',
                    className: 'w-full p-3 border border-gray-300 rounded-md focus:ring-purple-500 focus:border-purple-500 font-mono text-sm',
                    placeholder: 'Ej: 0000 (cuatro ceros)'
                }),
                createEl('button', {
                    className: 'w-full px-6 py-3 bg-purple-600 text-white font-semibold rounded-lg shadow-md hover:bg-purple-700 transition-colors flex items-center justify-center space-x-2',
                    type: 'submit'
                }, createEl('i', { className: 'fas fa-cogs' }), createEl('span', {}, 'Establecer Dificultad'))
            ),
            createEl('div', { id: 'difficulty-message-container', className: 'mt-4' })
        );

        difficultyCard.querySelector('#set-difficulty-form').addEventListener('submit', async e => {
            e.preventDefault();
            const input = difficultyCard.querySelector('#new-difficulty-input');
            const button = difficultyCard.querySelector('#set-difficulty-form button[type="submit"]');
            const messageContainer = difficultyCard.querySelector('#difficulty-message-container');
            const originalButtonText = button.innerHTML;

            const newDifficulty = input.value.trim();

            messageContainer.innerHTML = ''; // Clear previous messages

            if (!newDifficulty) {
                const msg = createEl('div', { className: 'bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative' }, 'Por favor, introduce la nueva cadena de dificultad.');
                messageContainer.append(msg);
                setTimeout(() => msg.remove(), 3000);
                return;
            }

            button.disabled = true;
            button.innerHTML = '<i class="fas fa-spinner fa-spin mr-2"></i> Actualizando...';

            try {
                const response = await api.setDifficulty(newDifficulty);
                input.value = ''; // Clear input
                const successMessage = createEl('div', { className: 'bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded relative' }, `Dificultad actualizada a: "${response}"`);
                messageContainer.append(successMessage);
                setTimeout(() => successMessage.remove(), 3000);
                await updateDifficultyDisplay(); // Actualizar la dificultad mostrada
            } catch (error) {
                console.error('Error al establecer la dificultad:', error);
                const errorMessage = createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' }, `Error al establecer dificultad: ${error.message || 'Error desconocido.'}`);
                messageContainer.append(errorMessage);
                setTimeout(() => errorMessage.remove(), 5000);
            } finally {
                button.disabled = false;
                button.innerHTML = originalButtonText;
            }
        });

        root.append(listCard, formCard, randomGenerateCard, difficultyCard);

        // Llama a la función para cargar la dificultad inicial
        await updateDifficultyDisplay();

    } catch (error) {
        root.innerHTML = '';
        root.append(createEl('div', { className: 'bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative' },
            createEl('strong', { className: 'font-bold' }, 'Error: '),
            createEl('span', { className: 'block sm:inline' }, 'No se pudieron cargar las transacciones ni la configuración de dificultad. Intenta de nuevo más tarde.')
        ));
        console.error('Error in transactions component:', error);
    }
}