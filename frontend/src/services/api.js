const BASE_BLOCKCHAIN_API = 'http://localhost:8080/api';
const BASE_POOL_API = 'http://localhost:8081/api';

export async function fetchJSON(path, opts) {
    let baseUrl = BASE_BLOCKCHAIN_API;
    if (path.startsWith('/pools')) baseUrl = BASE_POOL_API;

    try {
        const res = await fetch(baseUrl + path, opts);
        if (!res.ok) {
            const errorBody = await res.json().catch(() => ({ message: res.statusText }));
            throw new Error(`HTTP error! Status: ${res.status}, Message: ${errorBody.message || 'Unknown error'}`);
        }
        if (path.startsWith('/difficulty') && res.headers.get('content-type')?.includes('text/plain')) {
            return res.text();
        }
        return res.json();
    } catch (error) {
        console.error(`API fetch error for ${baseUrl + path}:`, error);
        throw error;
    }
}

export const api = {
    status: () => fetchJSON('/blocks/status'),
    allBlocks: (page = 0, size = 10) => fetchJSON(`/blocks?page=${page}&size=${size}`),
    block: (h) => fetchJSON(`/blocks/${h}`),
    latest: () => fetchJSON('/blocks/latest'),
    submitResult: (b) => fetchJSON('/blocks/result', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(b) }),
    txs: () => fetchJSON('/transactions/pending'),
    txCount: () => fetchJSON('/transactions/pending/count'),
    createTx: (t) => fetchJSON('/transactions', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(t) }),
    fetchAllBlocks: async () => {
        let allBlocks = [];
        let page = 0;
        const size = 100;
        let hasMore = true;
        while (hasMore) {
            try {
                const data = await api.allBlocks(page, size);
                const currentBlocks = data._embedded ? data._embedded.blockList : [];
                allBlocks = allBlocks.concat(currentBlocks);
                if (data.page && allBlocks.length < data.page.totalElements) page++;
                else hasMore = false;
                if (hasMore) await new Promise(r => setTimeout(r, 50));
            } catch (error) { console.error('Error fetching all blocks:', error); hasMore = false; }
        }
        return allBlocks;
    },
    getMiners: () => fetchJSON('/pools/miners'),
    getDifficulty: () => fetchJSON('/difficulty'),
    setDifficulty: (d) => fetchJSON('/difficulty', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ difficulty: d }) }),
    config: () => fetchJSON('/config'),
    faucet: (body) => fetchJSON('/faucet', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }),
    getBalance: (pk) => fetchJSON(`/balance/${pk}`),
    getMinerBalance: (pk) => fetchJSON(`/pools/miners/${pk}/balance`),
};