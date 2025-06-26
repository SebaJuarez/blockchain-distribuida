import { navbar, updateHeaderTitle } from './components/navbar.js';
import { dashboard } from './components/dashboard.js';
import { blockList } from './components/blockList.js';
import { blockDetail } from './components/blockDetails.js';
import { transactions } from './components/transactions.js';
import { transactionDetail } from './components/transactionDetail.js';
import { statistics } from './components/statistics.js';
import { minerPool } from './components/minerPool.js';
import { createEl } from './utils/dom.js';

// Get root elements for navigation and main content
const navRoot = document.getElementById('main-nav');
const content = document.getElementById('content');

// Initialize the navbar (which includes status indicator logic)
navbar(document.getElementById('navbar'));

// Sidebar navigation links
const navLinksData = [
    { name: 'Dashboard', hash: 'dashboard', icon: 'fas fa-chart-line' },
    { name: 'Blocks', hash: 'blocks', icon: 'fas fa-th-large' },
    { name: 'Transactions', hash: 'transactions', icon: 'fas fa-exchange-alt' },
    { name: 'Statistics', hash: 'statistics', icon: 'fas fa-chart-bar' },
    { name: 'Miner Pool', hash: 'miner-pool', icon: 'fas fa-network-wired' } // Nuevo enlace para Miner Pool
];

navLinksData.forEach(item => {
    const a = createEl('a', {
        className: 'nav-link flex items-center p-3 rounded-lg text-gray-300 hover:bg-gray-700 hover:text-white transition-colors space-x-3 text-lg',
        href: '#' + item.hash
    }, createEl('i', { className: item.icon }), createEl('span', {}, item.name));

    // Add click listener to manage active state
    a.addEventListener('click', () => {
        document.querySelectorAll('.nav-link').forEach(x => x.classList.remove('bg-blue-600', 'text-white'));
        a.classList.add('bg-blue-600', 'text-white');
        updateHeaderTitle(item.name); // Update header title on nav click
        // Close sidebar on mobile after click
        if (window.innerWidth <= 768) {
        }
    });

    // Set initial active state for Dashboard
    if (item.hash === 'dashboard') {
        a.classList.add('bg-blue-600', 'text-white');
    }
    navRoot.append(a);
});

// Event listener for hash changes in the URL
window.addEventListener('hashchange', route);

/**
 * Routes the application based on the URL hash.
 */
function route() {
    const hash = location.hash.slice(1) || 'dashboard'; // Default to 'dashboard'
    const currentRoute = hash.split('/')[0]; // Get the main part of the hash (e.g., 'blocks' from 'blocks/123')

    // Update active class for sidebar links
    document.querySelectorAll('.nav-link').forEach(link => {
        const linkHash = link.getAttribute('href').slice(1);
        link.classList.toggle('bg-blue-600', linkHash === currentRoute);
        link.classList.toggle('text-white', linkHash === currentRoute);
        link.classList.toggle('text-gray-300', linkHash !== currentRoute);
    });

    // Update the main header title
    const activeNavItem = navLinksData.find(item => item.hash === currentRoute);
    if (activeNavItem) {
        updateHeaderTitle(activeNavItem.name);
    } else if (currentRoute.startsWith('blocks/')) {
        updateHeaderTitle('Detalle del Bloque');
    } else if (currentRoute.startsWith('transactions/')) {
        updateHeaderTitle('Detalle de Transacción');
    } else {
        updateHeaderTitle('Página Desconocida'); // Default for unknown or dynamic routes
    }

    // Render component based on route
    if (hash === 'dashboard') {
        dashboard(content);
    } else if (hash === 'blocks') {
        blockList(content);
    } else if (hash.startsWith('blocks/')) {
        const blockHash = hash.split('/')[1];
        blockDetail(content, blockHash);
    } else if (hash === 'transactions') {
        transactions(content);
    } else if (hash.startsWith('transactions/')) {
        const txId = hash.split('/')[1];
        transactionDetail(content, txId);
    } else if (hash === 'statistics') {
        statistics(content);
    } else if (hash === 'miner-pool') { // Nueva ruta para Miner Pool
        minerPool(content);
    } else {
        // Handle unknown routes or 404
        content.innerHTML = '';
        content.append(createEl('div', { className: 'bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative' },
            createEl('strong', { className: 'font-bold' }, 'Página no encontrada: '),
            createEl('span', { className: 'block sm:inline' }, `La ruta '${hash}' no existe.`)
        ));
    }
}

// Initial route call when the page loads
route();