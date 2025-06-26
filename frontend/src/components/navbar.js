import { createEl } from '../utils/dom.js';
import { api } from '../services/api.js';

// Renders the main navigation bar at the top
export async function navbar(root) {
    // Set initial title
    const headerTitleEl = document.getElementById('header-title');
    if (headerTitleEl) {
        headerTitleEl.textContent = 'Dashboard'; // Default title
    }

    // Update status indicator
    const statusIndicator = document.getElementById('status-indicator');
    const statusIcon = statusIndicator.querySelector('i');
    const statusText = statusIndicator.querySelector('span');

    try {
        const data = await api.status();
        const message = data._embedded ? data._embedded.message : data.message;

        if (message && message.includes('healthy')) { // Assuming "healthy" indicates a good status
            statusIcon.className = 'fas fa-circle mr-2 text-green-500';
            statusText.textContent = 'Online';
        } else {
            statusIcon.className = 'fas fa-circle mr-2 text-red-500';
            statusText.textContent = 'Offline';
        }
        statusIcon.classList.remove('animate-pulse');
    } catch (error) {
        console.error('Error fetching coordinator status:', error);
        statusIcon.className = 'fas fa-circle mr-2 text-red-500';
        statusText.textContent = 'Error';
        statusIcon.classList.remove('animate-pulse');
    }
}

// Function to update the header title based on the current route
export function updateHeaderTitle(title) {
    const headerTitleEl = document.getElementById('header-title');
    if (headerTitleEl) {
        headerTitleEl.textContent = title;
    }
}