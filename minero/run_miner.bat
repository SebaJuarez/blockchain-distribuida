@echo off
REM 1) Ir al directorio del script (raíz del proyecto)
cd /d "%~dp0"

REM 2) Activar virtualenv si existe, si no, créalo
if exist "venv\Scripts\activate.bat" (
    call "venv\Scripts\activate.bat"
) else (
    echo No se encontró 'venv'. Creando virtualenv...
    python -m venv venv
    call "venv\Scripts\activate.bat"
)

REM 3) Instalar dependencias
if exist "requirements.txt" (
    python -m pip install --upgrade pip
    python -m pip install -r requirements.txt
) else (
    echo ⚠️  requirements.txt no encontrado, saltando pip install
)

REM 4) Definir variables de entorno (ajústalas)
set "BLOCKS_COORDINATOR_URL=http://localhost:8080/api/blocks/result"
set "RABBITMQ_HOST=localhost"
set "MINER_ID=miner-python-001"

REM 5) Ejecutar el miner
python -m minero

pause