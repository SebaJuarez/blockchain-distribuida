import { api } from '../services/api.js';
import { createEl } from '../utils/dom.js';

export async function renderStatus(root) {
    root.innerHTML = ''; // Clear existing content
    root.append(createEl('span', { className: 'text-gray-500' }, 'Cargando estado...')); // Loading state

    try {
        const data = await api.status();
        const message = data._embedded ? data._embedded.message : data.message;
        const statusColor = message && message.includes('healthy') ? 'text-green-500' : 'text-red-500';
        const statusText = message && message.includes('healthy') ? 'Online' : 'Offline';

        root.innerHTML = ''; // Clear loading state
        root.append(createEl('span', { className: `font-semibold ${statusColor}` }, statusText));
    } catch (error) {
        console.error('Error fetching coordinator status:', error);
        root.innerHTML = ''; // Clear loading state
        root.append(createEl('span', { className: 'text-red-500' }, 'Error al cargar el estado'));
    }
}