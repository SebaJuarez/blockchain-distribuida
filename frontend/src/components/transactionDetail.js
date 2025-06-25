import { createEl, createLoadingSpinner } from '../utils/dom.js';

// Placeholder component for future transaction details
export async function transactionDetail(root, txId) {
    root.innerHTML = '';
    root.appendChild(createLoadingSpinner());

    // In a real application, you would fetch transaction details from an API here
    // e.g., const tx = await api.transaction(txId);

    // Simulate fetching delay
    await new Promise(resolve => setTimeout(resolve, 500));

    root.innerHTML = ''; // Clear spinner

    const card = createEl('div', { className: 'bg-white p-8 rounded-lg shadow-xl mb-6' },
        createEl('h2', { className: 'text-3xl font-extrabold text-gray-900 mb-6 border-b pb-4' }, `Detalles de la Transacción`),
        createEl('div', { className: 'grid grid-cols-1 md:grid-cols-2 gap-4 text-gray-700' },
            createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {}, 'ID de Transacción:'), createEl('span', { className: 'font-mono break-all text-sm' }, txId)),
            createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {}, 'Estado:'), createEl('span', { className: 'text-sm text-yellow-600 font-semibold' }, 'Cargando/Pendiente (Simulado)')),
            createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {}, 'Remitente:'), createEl('span', { className: 'font-mono break-all text-sm' }, 'Simulado_Remitente')),
            createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {}, 'Receptor:'), createEl('span', { className: 'font-mono break-all text-sm' }, 'Simulado_Receptor')),
            createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {}, 'Cantidad:'), createEl('span', { className: 'text-sm' }, 'Simulado_100')),
            createEl('p', { className: 'flex items-center space-x-2' }, createEl('strong', {}, 'Marca de Tiempo:'), createEl('span', { className: 'text-sm' }, new Date().toLocaleString()))
        ),
        createEl('p', {className: 'text-gray-600 italic mt-6'}, 'Esta es una página de detalles de transacción de marcador de posición. Los datos son simulados hasta que el endpoint `/api/transactions/{id}` esté disponible.')
    );

    root.append(card);
}
