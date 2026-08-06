import { api } from '../services/api.js';

let statusTimer = null;
let statusVisible = true;

function setStatus(state, text) {
    const statusIndicator = document.getElementById('status-indicator');
    const statusIcon = statusIndicator.querySelector('i');
    const statusText = statusIndicator.querySelector('span');

    const colors = {
        online: 'text-green-500',
        offline: 'text-red-500',
        checking: 'text-yellow-500',
        error: 'text-red-500'
    };

    statusIcon.className = `fas fa-circle mr-2 ${colors[state] || 'text-yellow-500'} ${state === 'checking' ? 'animate-pulse' : ''}`;
    statusText.textContent = text;
}

export async function checkStatus() {
    try {
        const res = await api.status();
        const message = (res._embedded && res._embedded.message) || res.message || '';
        setStatus('online', 'Online');
        return message;
    } catch (error) {
        console.error('Error fetching coordinator status:', error);
        setStatus('offline', 'Offline');
        return null;
    }
}

// Renders the main navigation bar at the top
export async function navbar(root) {
    // Set initial title
    const headerTitleEl = document.getElementById('header-title');
    if (headerTitleEl) {
        headerTitleEl.textContent = 'Dashboard'; // Default title
    }

    // Update status indicator
    setStatus('checking', 'Verificando...');

    await checkStatus();

    // Poll status periodically, pausing when the tab is hidden
    const startPolling = () => {
        if (statusTimer) return;
        statusTimer = setInterval(async () => {
            if (!document.hidden) await checkStatus();
        }, 5000);
    };
    const stopPolling = () => {
        if (statusTimer) {
            clearInterval(statusTimer);
            statusTimer = null;
        }
    };

    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            statusVisible = false;
            stopPolling();
        } else {
            statusVisible = true;
            checkStatus();
            startPolling();
        }
    });

    startPolling();
}

// Function to update the header title based on the current route
export function updateHeaderTitle(title) {
    const headerTitleEl = document.getElementById('header-title');
    if (headerTitleEl) {
        headerTitleEl.textContent = title;
    }
}
