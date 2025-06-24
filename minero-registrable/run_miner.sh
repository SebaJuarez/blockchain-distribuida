#!/usr/bin/env bash
set -euo pipefail

# 1) Ir a la carpeta del script (raíz del proyecto)
cd "$(dirname "$0")"

# 2) Activar virtualenv si existe, si no, créalo
if [[ -f "venv/bin/activate" ]]; then
  source venv/bin/activate
else
  echo "No se encontró 'venv'. Creando virtualenv..."
  python3 -m venv venv
  source venv/bin/activate
fi

# 3) Instalar dependencias
if [[ -f "requirements.txt" ]]; then
  pip install --upgrade pip
  pip install -r requirements.txt
else
  echo "⚠️  requirements.txt no encontrado, saltando pip install"
fi

# 4) Exportar variables de entorno (ajústalas a tu configuración)
export POOL_BASE_URL ="${POOL_BASE_URL:-http://localhost:8080/api/pools}"
export RABBITMQ_HOST="${RABBITMQ_HOST:-localhost}"

# 5) Ejecutar el miner
python -m minero-registrable