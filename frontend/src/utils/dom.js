export function createEl(tag, attrs = {}, ...children) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs)) {
        // Handle className for Tailwind CSS
        if (k === 'className') {
            el.setAttribute('class', v);
        }
        // Handle event listeners (e.g., onClick, onSubmit)
        else if (k.startsWith('on') && typeof v === 'function') {
            el.addEventListener(k.substring(2).toLowerCase(), v);
        }
        // Set other attributes
        else {
            el.setAttribute(k, v);
        }
    }
    // Append children (can be strings or other DOM elements)
    children.flat().forEach(c => el.append(typeof c === 'string' ? document.createTextNode(c) : c));
    return el;
}

// --- Toast singleton (reutilizable, sin acumular) ---
let toastEl = null;
let toastTimer = null;

export function showToast(message, type = 'success') {
    if (!toastEl) {
        toastEl = createEl('div', { className: 'fixed bottom-4 right-4 z-50 px-4 py-2 rounded-lg shadow-lg text-sm font-medium text-white transition-all duration-300 opacity-0 transform translate-y-4' });
        document.body.appendChild(toastEl);
    }

    const colors = { success: 'bg-green-600', error: 'bg-red-600', info: 'bg-blue-600', warning: 'bg-yellow-600' };
    toastEl.className = `fixed bottom-4 right-4 z-50 px-4 py-2 rounded-lg shadow-lg text-sm font-medium text-white transition-all duration-300 opacity-0 transform translate-y-4 ${colors[type] || colors.info}`;
    toastEl.textContent = message;

    requestAnimationFrame(() => {
        toastEl.classList.remove('opacity-0', 'translate-y-4');
        toastEl.classList.add('opacity-100', 'translate-y-0');
    });

    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => {
        toastEl.classList.remove('opacity-100', 'translate-y-0');
        toastEl.classList.add('opacity-0', 'translate-y-4');
    }, 2500);
}

// Function to copy text to clipboard (modern API with fallback)
export async function copyToClipboard(text) {
    try {
        if (navigator.clipboard && window.isSecureContext) {
            await navigator.clipboard.writeText(text);
        } else {
            const textarea = document.createElement('textarea');
            textarea.value = text;
            textarea.style.position = 'fixed';
            textarea.style.opacity = '0';
            document.body.appendChild(textarea);
            textarea.select();
            document.execCommand('copy');
            document.body.removeChild(textarea);
        }
        showToast('Copiado al portapapeles', 'success');
        return true;
    } catch (err) {
        console.error('Failed to copy text: ', err);
        showToast('No se pudo copiar', 'error');
        return false;
    }
}

// Function to truncate hash for display
export function truncateHash(hash, length = 12) {
    if (!hash || typeof hash !== 'string' || hash.length <= length) {
        return hash;
    }
    return `${hash.substring(0, length / 2)}...${hash.substring(hash.length - length / 2)}`;
}

// Function to truncate a longer ID/hash for display in tables
export function shortenId(id, startLength = 6, endLength = 6) {
    if (!id || typeof id !== 'string' || id.length <= startLength + endLength) {
        return id;
    }
    return `${id.substring(0, startLength)}...${id.substring(id.length - endLength)}`;
}

// Component to display a loading spinner
export function createLoadingSpinner() {
    return createEl('div', { className: 'flex justify-center items-center py-8' },
        createEl('div', { className: 'animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500' })
    );
}

// Function to generate a random alphanumeric string for addresses
export function generateRandomAddress(length = 40) {
    const chars = 'abcdef0123456789';
    let result = '04';
    for (let i = 0; i < length; i++) {
        result += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return result;
}

// Function to format duration in a human-readable way
export function formatDuration(seconds) {
    if (seconds < 60) {
        return `${Math.round(seconds)} segundos`;
    } else if (seconds < 3600) {
        const minutes = Math.floor(seconds / 60);
        const remainingSeconds = Math.round(seconds % 60);
        return `${minutes} min${minutes !== 1 ? 's' : ''}${remainingSeconds > 0 ? ` ${remainingSeconds} seg` : ''}`;
    } else if (seconds < 86400) {
        const hours = Math.floor(seconds / 3600);
        const remainingMinutes = Math.round((seconds % 3600) / 60);
        return `${hours} hr${hours !== 1 ? 's' : ''}${remainingMinutes > 0 ? ` ${remainingMinutes} min` : ''}`;
    } else {
        const days = Math.floor(seconds / 86400);
        const remainingHours = Math.round((seconds % 86400) / 3600);
        return `${days} día${days !== 1 ? 's' : ''}${remainingHours > 0 ? ` ${remainingHours} hr` : ''}`;
    }
}
