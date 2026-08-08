#!/bin/sh
set -e

BLOCKCHAIN_API="${FRONTEND_BLOCKCHAIN_API:-http://localhost:8080/api}"
POOL_API="${FRONTEND_POOL_API:-http://localhost:8081/api}"

cat > /usr/share/nginx/html/public/config.js <<EOF
window.BLOCKCHAIN_API_CONFIG = window.BLOCKCHAIN_API_CONFIG || {
    blockchainApi: '${BLOCKCHAIN_API}',
    poolApi: '${POOL_API}'
};
EOF

exec "$@"
