const WALLETS_KEY = 'blockchain_wallets';
const ACTIVE_WALLET_KEY = 'blockchain_active_wallet';
const LEGACY_KEY = 'blockchain_wallet_privkey';

function getEc() {
    if (typeof elliptic === 'undefined') {
        throw new Error(
            'La librería "elliptic.js" no se cargó. ' +
            'Verificá tu conexión o descargá el archivo en frontend/public/lib/elliptic.min.js'
        );
    }
    return new elliptic.ec('secp256k1');
}

function uuid() {
    if (crypto.randomUUID) return crypto.randomUUID();
    return 'w-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
}

function readWallets() {
    try {
        const raw = localStorage.getItem(WALLETS_KEY);
        const wallets = raw ? JSON.parse(raw) : [];
        return Array.isArray(wallets) ? wallets : [];
    } catch (e) {
        console.error('Error leyendo wallets:', e);
        return [];
    }
}

function writeWallets(wallets) {
    localStorage.setItem(WALLETS_KEY, JSON.stringify(wallets));
}

function migrateLegacyWallet() {
    const legacy = localStorage.getItem(LEGACY_KEY);
    if (!legacy) return;
    const wallets = readWallets();
    if (wallets.length === 0) {
        try {
            const ec = getEc();
            const keyPair = ec.keyFromPrivate(legacy, 'hex');
            const publicKeyHex = keyPair.getPublic(true, 'hex');
            const wallet = { id: uuid(), name: 'Wallet 1', privHex: legacy, publicKeyHex, createdAt: Date.now() };
            writeWallets([wallet]);
            localStorage.setItem(ACTIVE_WALLET_KEY, wallet.id);
        } catch (e) {
            console.error('Wallet legacy inválida, se ignora:', e);
        }
    }
    localStorage.removeItem(LEGACY_KEY);
}

function getActiveWallet() {
    migrateLegacyWallet();
    const wallets = readWallets();
    const activeId = localStorage.getItem(ACTIVE_WALLET_KEY);
    return wallets.find(w => w.id === activeId) || wallets[0] || null;
}

// Devuelve la wallet activa, creando una nueva si no hay ninguna.
export function getOrCreateWallet() {
    let wallet = getActiveWallet();
    if (!wallet) {
        wallet = generateNewWallet();
    }
    const ec = getEc();
    const keyPair = ec.keyFromPrivate(wallet.privHex, 'hex');
    return { keyPair, publicKeyHex: wallet.publicKeyHex, id: wallet.id };
}

// Genera una nueva wallet y la guarda como activa.
export function generateNewWallet() {
    const ec = getEc();
    const keyPair = ec.genKeyPair();
    const publicKeyHex = keyPair.getPublic(true, 'hex');
    const wallet = {
        id: uuid(),
        name: `Wallet ${readWallets().length + 1}`,
        privHex: keyPair.getPrivate('hex'),
        publicKeyHex,
        createdAt: Date.now()
    };
    const wallets = readWallets();
    wallets.push(wallet);
    writeWallets(wallets);
    localStorage.setItem(ACTIVE_WALLET_KEY, wallet.id);
    return wallet;
}

// Valida una clave privada y la agrega como wallet nueva. Lanza error si es inválida.
export function addWallet(privHex, name) {
    const ec = getEc();
    let keyPair;
    try {
        keyPair = ec.keyFromPrivate(privHex.trim(), 'hex');
        if (!keyPair.getPrivate('hex')) throw new Error('Clave inválida');
    } catch (e) {
        throw new Error('La clave privada no es válida. Verificá que sea un hex de secp256k1.');
    }
    const publicKeyHex = keyPair.getPublic(true, 'hex');
    const wallets = readWallets();
    const existing = wallets.find(w => w.publicKeyHex === publicKeyHex);
    if (existing) {
        localStorage.setItem(ACTIVE_WALLET_KEY, existing.id);
        return existing;
    }
    const wallet = {
        id: uuid(),
        name: name?.trim() || `Wallet ${wallets.length + 1}`,
        privHex: privHex.trim(),
        publicKeyHex,
        createdAt: Date.now()
    };
    wallets.push(wallet);
    writeWallets(wallets);
    localStorage.setItem(ACTIVE_WALLET_KEY, wallet.id);
    return wallet;
}

export function switchWallet(id) {
    const wallets = readWallets();
    if (!wallets.some(w => w.id === id)) return false;
    localStorage.setItem(ACTIVE_WALLET_KEY, id);
    return true;
}

export function removeWallet(id) {
    let wallets = readWallets();
    const removed = wallets.find(w => w.id === id);
    if (!removed) return false;
    wallets = wallets.filter(w => w.id !== id);
    writeWallets(wallets);
    if (localStorage.getItem(ACTIVE_WALLET_KEY) === id) {
        const next = wallets[0] || null;
        localStorage.setItem(ACTIVE_WALLET_KEY, next ? next.id : '');
    }
    return true;
}

export function listWallets() {
    migrateLegacyWallet();
    const wallets = readWallets();
    const activeId = localStorage.getItem(ACTIVE_WALLET_KEY);
    return wallets.map(w => ({ ...w, active: w.id === activeId }));
}

async function sha256(message) {
    const encoded = new TextEncoder().encode(message);
    const digest = await crypto.subtle.digest('SHA-256', encoded);
    return new Uint8Array(digest);
}

function buildMessage(receiver, amount, timestamp) {
    return `${receiver}|${Number(amount).toFixed(2)}|${timestamp}`;
}

export async function signTransaction(receiver, amount) {
    const { keyPair, publicKeyHex } = getOrCreateWallet();
    const timestamp = Math.floor(Date.now() / 1000);
    const message = buildMessage(receiver, amount, timestamp);
    const hash = await sha256(message);

    const signature = keyPair.sign(hash);
    const signatureHex = signature.toDER('hex');

    return {
        sender: publicKeyHex,
        receiver,
        amount: Number(amount),
        timestamp,
        signature: signatureHex,
    };
}

export function getWalletAddress() {
    const { publicKeyHex } = getOrCreateWallet();
    return publicKeyHex;
}
