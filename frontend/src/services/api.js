const DEFAULT_BASE_BLOCKCHAIN_API = 'http://34.60.171.40:8080/api';
const DEFAULT_BASE_POOL_API = 'http://35.193.77.25:8081/api';

// Config centralizada: se puede sobrescribir con window.BLOCKCHAIN_API_CONFIG antes de cargar main.js
const config = window.BLOCKCHAIN_API_CONFIG || {};
const BASE_BLOCKCHAIN_API = config.blockchainApi || DEFAULT_BASE_BLOCKCHAIN_API;
const BASE_POOL_API = config.poolApi || DEFAULT_BASE_POOL_API;
const REQUEST_TIMEOUT_MS = 10000;

export function getApiBaseUrl() {
    return BASE_BLOCKCHAIN_API;
}

async function fetchJSON(path, opts = {}) {
    let baseUrl = BASE_BLOCKCHAIN_API;
    if (path.startsWith('/pools')) baseUrl = BASE_POOL_API;

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);

    try {
        const res = await fetch(baseUrl + path, { ...opts, signal: controller.signal });
        if (!res.ok) {
            const errorBody = await res.json().catch(() => ({ message: res.statusText }));
            throw new Error(`HTTP error! Status: ${res.status}, Message: ${errorBody.message || 'Unknown error'}`);
        }
        if (path.startsWith('/difficulty') && res.headers.get('content-type')?.includes('text/plain')) {
            return res.text();
        }
        return res.json();
    } catch (error) {
        if (error.name === 'AbortError') {
            throw new Error(`La API no respondió en ${REQUEST_TIMEOUT_MS / 1000}s (${baseUrl + path}). Revisá tu conexión o el estado del servidor.`);
        }
        console.error(`API fetch error for ${baseUrl + path}:`, error);
        throw error;
    } finally {
        clearTimeout(timeout);
    }
}

export const api = {
    status: () => fetchJSON('/blocks/status'),
    allBlocks: (page = 0, size = 10) => fetchJSON(`/blocks?page=${page}&size=${size}`),
    block: (h) => fetchJSON(`/blocks/${h}`),
    blockByIndex: (index) => fetchJSON(`/blocks/by-index/${index}`),
    latest: () => fetchJSON('/blocks/latest'),
    submitResult: (b) => fetchJSON('/blocks/result', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(b) }),
    txs: () => fetchJSON('/transactions/pending'),
    txCount: () => fetchJSON('/transactions/pending/count'),
    transaction: (txId) => fetchJSON(`/transactions/${txId}`),
    createTx: (t) => fetchJSON('/transactions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(t) }),
    getMiners: () => fetchJSON('/pools/miners'),
    getDifficulty: () => fetchJSON('/difficulty'),
    setDifficulty: (d) => fetchJSON('/difficulty', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ difficulty: d }) }),
    config: () => fetchJSON('/config'),
    faucet: (body) => fetchJSON('/faucet', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    faucetStatus: () => fetchJSON('/faucet'),
    getBalance: (pk) => fetchJSON(`/balance/${pk}`),
    getMinerBalance: (pk) => fetchJSON(`/pools/miners/${pk}/balance`),
};
