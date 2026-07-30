const STORAGE_KEY = 'blockchain_wallet_privkey';

function getEc() {
    if (typeof elliptic === 'undefined') {
        throw new Error(
            'La librería "elliptic.js" no se cargó. ' +
            'Verificá tu conexión o descargá el archivo en frontend/public/lib/elliptic.min.js'
        );
    }
    return new elliptic.ec('secp256k1');
}

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

    const publicKeyHex = keyPair.getPublic(true, 'hex');
    return { keyPair, publicKeyHex };
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