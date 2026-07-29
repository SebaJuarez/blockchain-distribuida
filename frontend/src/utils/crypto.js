// Wallet EC (secp256k1) persistido en localStorage, y firma de transacciones
// compatible con TransactionController.isSignatureValid() del coordinador.
// Usa elliptic.js (CDN, cargado global en index.html) para la curva y
// Web Crypto API nativa del navegador para SHA-256.

const STORAGE_KEY = 'blockchain_wallet_privkey';

function getEc() {
    if (typeof elliptic === 'undefined') {
        throw new Error('elliptic.js no está cargado. Verificá el <script> en index.html.');
    }
    return new elliptic.ec('secp256k1');
}

/**
 * Carga la clave privada del wallet desde localStorage, o genera una nueva
 * y la persiste si no existe.
 */
export function getOrCreateWallet() {
    const ec = getEc();
    let privHex = localStorage.getItem(STORAGE_KEY);
    let keyPair;

    if (privHex) {
        keyPair = ec.keyFromPrivate(privHex, 'hex');
    } else {
        keyPair = ec.genKeyPair();
        privHex = keyPair.getPrivate('hex');
        localStorage.setItem(STORAGE_KEY, privHex);
    }

    const publicKeyHex = keyPair.getPublic(true, 'hex'); // comprimido, 33 bytes
    return { keyPair, publicKeyHex };
}

async function sha256(message) {
    const encoded = new TextEncoder().encode(message);
    const digest = await crypto.subtle.digest('SHA-256', encoded);
    return new Uint8Array(digest);
}

/**
 * Mensaje canónico a firmar. DEBE coincidir EXACTO con el que reconstruye
 * TransactionController en el coordinador.
 */
function buildMessage(receiver, amount, timestamp) {
    return `${receiver}|${Number(amount).toFixed(2)}|${timestamp}`;
}

/**
 * Firma una transacción {receiver, amount} con la clave del wallet local.
 * Genera su propio timestamp (epoch en segundos) porque el valor firmado y
 * el que ve el coordinador tienen que ser EXACTAMENTE el mismo.
 * Retorna el objeto Transaction listo para POST /api/transactions.
 */
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