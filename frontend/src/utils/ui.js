import { html } from '../lib/preact-standalone.js';

// Componentes UI compartidos para consistencia visual y menos código repetido.

export function errorBox(message) {
    return html`
        <div class="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative">
            <strong class="font-bold">Error: </strong>
            <span class="block sm:inline">${message}</span>
        </div>`;
}

export function warningBox(title, message) {
    return html`
        <div class="bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded relative">
            <strong class="font-bold">${title}: </strong>
            <span class="block sm:inline">${message}</span>
        </div>`;
}

export function emptyState(message, icon = 'fas fa-inbox') {
    return html`
        <div class="bg-white p-6 rounded-lg shadow-md text-center text-gray-500">
            <i class="${icon} text-3xl mb-2 opacity-50"></i>
            <p class="italic">${message}</p>
        </div>`;
}

export function spinner() {
    return html`
        <div class="flex justify-center items-center py-8">
            <div class="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
        </div>`;
}

export function badge(text, colorClass) {
    return html`<span class="px-2 py-1 rounded-full text-xs font-bold ${colorClass}">${text}</span>`;
}

export function sectionTitle(title, count) {
    return html`
        <h3 class="text-lg font-bold text-gray-900 mb-3 border-b pb-2">
            ${title}${count !== undefined ? html` <span class="text-gray-400 font-normal text-sm">(${count})</span>` : ''}
        </h3>`;
}

export function statCard(icon, iconBgClass, title, value, footer) {
    return html`
        <div class="bg-white p-6 rounded-lg shadow-md flex flex-col space-y-3">
            <div class="flex items-center space-x-3">
                <div class="flex-shrink-0 ${iconBgClass} rounded-full p-3">
                    <i class="${icon} text-xl"></i>
                </div>
                <h3 class="text-lg font-semibold text-gray-700">${title}</h3>
            </div>
            <p class="text-4xl font-bold text-gray-900">${value}</p>
            ${footer ? html`<p class="text-sm text-gray-500">${footer}</p>` : ''}
        </div>`;
}

export function keyValueRow(label, value, extra) {
    return html`
        <p class="flex items-start space-x-2">
            <strong class="w-32 flex-shrink-0">${label}:</strong>
            <span class="font-mono break-all text-sm">${value}</span>
            ${extra}
        </p>`;
}
