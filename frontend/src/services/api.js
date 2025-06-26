const BASE_BLOCKCHAIN_API = 'http://localhost:8080/api';
const BASE_POOL_API = 'http://localhost:8081/api';

/**
 * Fetches JSON data from the specified path, selecting the appropriate base URL.
 * @param {string} path - The API endpoint path.
 * @param {object} opts - Fetch options (method, headers, body, etc.).
 * @returns {Promise<object>} - A promise that resolves to the JSON response.
 */
export async function fetchJSON(path, opts) {
    let baseUrl = BASE_BLOCKCHAIN_API;
    // Determina qué base URL usar basado en el prefijo de la ruta
    if (path.startsWith('/pools')) {
        baseUrl = BASE_POOL_API;
    }

    try {
        const res = await fetch(baseUrl + path, opts);
        if (!res.ok) {
            // Handle HTTP errors
            const errorBody = await res.json().catch(() => ({ message: res.statusText }));
            throw new Error(`HTTP error! Status: ${res.status}, Message: ${errorBody.message || 'Unknown error'}`);
        }
        // Para los endpoints de dificultad que devuelven un string plano, no intentamos parsear JSON
        if (path.startsWith('/difficulty') && res.headers.get('content-type')?.includes('text/plain')) {
            return res.text();
        }
        return res.json();
    } catch (error) {
        console.error(`API fetch error for ${baseUrl + path}:`, error);
        // Re-throw to allow component-level error handling
        throw error;
    }
}

// API client object with methods for different endpoints
export const api = {
    /**
     * Fetches the coordinator status.
     * @returns {Promise<object>} - Status data.
     */
    status: () => fetchJSON('/blocks/status'),

    /**
     * Fetches a list of all blocks with pagination.
     * @param {number} page - The current page number (0-indexed).
     * @param {number} size - The number of items per page.
     * @returns {Promise<object>} - Paginated block list.
     */
    allBlocks: (page = 0, size = 10) => fetchJSON(`/blocks?page=${page}&size=${size}`),

    /**
     * Fetches details for a specific block by hash.
     * @param {string} hash - The hash of the block.
     * @returns {Promise<object>} - Block details.
     */
    block: (h) => fetchJSON(`/blocks/${h}`),

    /**
     * Fetches the latest block.
     * @returns {Promise<object>} - The latest block data.
     */
    latest: () => fetchJSON('/blocks/latest'),

    /**
     * Submits a mining result to the blockchain.
     * @param {object} b - The block result data.
     * @returns {Promise<object>} - The response from the submission.
     */
    submitResult: (b) => fetchJSON('/blocks/result', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(b)
    }),

    /**
     * Fetches a list of pending transactions.
     * @returns {Promise<object>} - Pending transactions list.
     */
    txs: () => fetchJSON('/transactions/pending'),

    /**
     * Fetches the count of pending transactions.
     * Expected to return an object like { count: 5 }
     * @returns {Promise<object>} - Object with transaction count.
     */
    txCount: () => fetchJSON('/transactions/pending/count'),

    /**
     * Creates a new transaction.
     * @param {object} t - The transaction data (sender, receiver, amount).
     * @returns {Promise<object>} - The response from transaction creation.
     */
    createTx: (t) => fetchJSON('/transactions', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(t)
    }),

    /**
     * Fetches all blocks by iterating through paginated results.
     * @returns {Promise<Array<object>>} - A promise that resolves to an array of all blocks.
     */
    fetchAllBlocks: async () => {
        let allBlocks = [];
        let page = 0;
        const size = 100; // Fetch larger pages for efficiency
        let hasMore = true;

        while (hasMore) {
            try {
                const data = await api.allBlocks(page, size);
                const currentBlocks = data._embedded ? data._embedded.blockList : [];
                allBlocks = allBlocks.concat(currentBlocks);
                
                // Check if there are more pages based on HATEOAS links or totalElements
                if (data.page && allBlocks.length < data.page.totalElements) {
                    page++;
                } else {
                    hasMore = false;
                }

                // Small delay to prevent overwhelming the backend in a rapid loop
                if (hasMore) {
                    await new Promise(resolve => setTimeout(resolve, 50)); // 50ms delay
                }

            } catch (error) {
                console.error('Error fetching all blocks:', error);
                hasMore = false; // Stop fetching on error
            }
        }
        return allBlocks;
    },

    /**
     * Fetches the list of registered miners in the pool.
     * @returns {Promise<object>} - List of miners.
     */
    getMiners: () => fetchJSON('/pools/miners'),

    /**
     * Fetches the current mining difficulty/challenge string.
     * @returns {Promise<string>} - The current challenge string (e.g., "0000").
     */
    getDifficulty: () => fetchJSON('/difficulty'),

    /**
     * Sets a new mining difficulty/challenge string.
     * @param {string} difficultyString - The new challenge string (e.g., "00000").
     * @returns {Promise<string>} - The updated challenge string.
     */
    setDifficulty: (difficultyString) => fetchJSON('/difficulty', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ difficulty: difficultyString })
    }),
};