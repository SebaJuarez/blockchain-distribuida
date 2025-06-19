# miner.py
import ast
import json
import os
import subprocess
import sys  # <--- ¡IMPORTANTE: Añadido!
import threading
import time
import re  # Para parsear la salida del ejecutable CUDA

import requests
from pika import exceptions as rabbitmq_exceptions

# Asumiendo que estos están en las rutas relativas o en PYTHONPATH
from plugins.rabbitmq import rabbit_connect
from model.block import Block
from utils.check_gpu import check_for_nvidia_smi
# from utils.find_nonce import find_nonce_with_prefix  # Descomentar si usas CPU fallback

# --- Variables de Entorno y Configuración ---
# Puedes usar un .env o directamente en Docker Compose/línea de comandos
BLOCKS_COORDINATOR_URL = os.environ.get("BLOCKS_COORDINATOR_URL", "http://localhost:8080/api/blocks/result")
RABBITMQ_HOST = os.environ.get("RABBITMQ_HOST", "localhost")
MINER_ID = os.environ.get("MINER_ID", "miner-python-001")  # ID único para este minero

# --- Rutas y Nombres de Archivos para CUDA ---
cuda_bin_dir = os.path.join(os.getcwd(), "utils", "cuda")

# En Windows usamos .exe; en Linux/macOS no
if sys.platform.startswith("win"):
    cuda_exe_name = "md5_cuda.exe"
else:
    cuda_exe_name = "md5_cuda"
CUDA_EXECUTABLE_PATH = os.path.join(cuda_bin_dir, cuda_exe_name)

# --- Conexiones y Estado Global ---
rabbitmq_connection = None  # Inicializamos a None, se establecerá en main

# Verifica la disponibilidad de la GPU al inicio
gpu_available = check_for_nvidia_smi()

# --- Funciones de Utilidad ---

def send_mining_result(block_solved, miner_id, block_id):
    """
    Envía el bloque resuelto al Coordinador via HTTP POST,
    incluyendo el blockId (el hash de contenido original).
    """
    try:
        payload = block_solved.to_dict()
        payload["blockId"] = block_id
        payload["minerId"] = miner_id
        
        print(f"[{miner_id}] Sending solved block to Coordinator: {payload['hash']}", file=sys.stdout, flush=True)
        
        response = requests.post(
            BLOCKS_COORDINATOR_URL,
            json=payload,
            headers={"Content-Type": "application/json"}
        )
        response.raise_for_status()
        print(f"[{miner_id}] Coordinator response: {response.status_code} - {response.text}", file=sys.stdout, flush=True)

    except requests.exceptions.RequestException as e:
        print(f"[{miner_id}] HTTP request error when sending result: {e}", file=sys.stderr, flush=True)
    except Exception as e:
        print(f"[{miner_id}] Unexpected error when sending result: {e}", file=sys.stderr, flush=True)


def consume_tasks():
    global rabbitmq_connection
    print(" [*] Waiting for mining tasks. To exit press CTRL+C")

    while True:
        try:
            if rabbitmq_connection is None or rabbitmq_connection.is_closed:
                print("[*] Attempting to connect to RabbitMQ...", file=sys.stdout, flush=True)
                rabbitmq_connection = rabbit_connect(host=RABBITMQ_HOST)
                channel = rabbitmq_connection.channel()
                channel.queue_declare(queue="blocks", durable=True)
                channel.basic_consume(queue="blocks", on_message_callback=callback)
                print("[*] Connected to RabbitMQ. Starting consuming...", file=sys.stdout, flush=True)
                channel.start_consuming()
        except rabbitmq_exceptions.AMQPConnectionError as e:
            print(f"RabbitMQ connection error: {e}. Retrying in 5 seconds...", file=sys.stderr, flush=True)
            rabbitmq_connection = None
            time.sleep(5)
        except rabbitmq_exceptions.ChannelClosedByBroker as e:
            print(f"RabbitMQ channel closed by broker: {e}. Retrying in 5 seconds...", file=sys.stderr, flush=True)
            rabbitmq_connection = None
            time.sleep(5)
        except Exception as e:
            print(f"An unexpected error occurred in consume_tasks: {e}. Retrying...", file=sys.stderr, flush=True)
            rabbitmq_connection = None
            time.sleep(5)


def callback(ch, method, properties, body):
    try:
        task = json.loads(body)
        challenge = task["challenge"]
        block_data_from_task = task["block"]

        # agregar lógica de ajustar el rango de búsqueda según dificultad

        from_range = task.get("from", 0)
        to_range = task.get("to", 100000000000)

        print(f"[{MINER_ID}] Received mining task for block index: {block_data_from_task['index']}", file=sys.stdout, flush=True)
        print(f"[{MINER_ID}] Challenge: '{challenge}', Range: [{from_range}, {to_range}]", file=sys.stdout, flush=True)

        block = Block.from_task_payload(block_data_from_task)
        preliminary_hash = block.hash
        block_content_hash = block.get_block_content_hash()

        print(f"[{MINER_ID}] Task's preliminary hash (block_content_hash): {block_content_hash}", file=sys.stdout, flush=True)

        solved_block_hash = ""
        found_nonce = 0

        if gpu_available:
            if not os.path.exists(CUDA_EXECUTABLE_PATH):
                print(f"[{MINER_ID}] Error: CUDA executable not found at {CUDA_EXECUTABLE_PATH}.", file=sys.stderr, flush=True)
                ch.basic_ack(method.delivery_tag)
                return

            process = subprocess.run(
                [CUDA_EXECUTABLE_PATH, challenge, block_content_hash, str(from_range), str(to_range)],
                capture_output=True,
                text=True
            )

            nonce_match = re.search(r"Nonce encontrado: (\d+)", process.stdout)
            hash_match = re.search(r"Hash resultante: ([0-9a-fA-F]+)", process.stdout)

            if nonce_match and hash_match:
                found_nonce = int(nonce_match.group(1))
                solved_block_hash = hash_match.group(1)
                print(f"[{MINER_ID}] Nonce found: {found_nonce}, Hash: {solved_block_hash}", file=sys.stdout, flush=True)

        else:
            from utils.find_nonce import find_nonce_with_prefix
            found_nonce, solved_block_hash = find_nonce_with_prefix(challenge, block_content_hash, from_range, to_range)

        if found_nonce and solved_block_hash:
            block.hash = solved_block_hash
            block.nonce = found_nonce
            send_mining_result(block, MINER_ID, preliminary_hash)
        else:
            print(f"[{MINER_ID}] No nonce found for block {block.index}.", file=sys.stdout, flush=True)

        ch.basic_ack(method.delivery_tag)

    except Exception as e:
        print(f"[{MINER_ID}] Error in callback: {e}", file=sys.stderr, flush=True)
        ch.basic_ack(method.delivery_tag)


if __name__ == "__main__":
    print(f"[{MINER_ID}] Starting GPU/CPU PoW miner...", file=sys.stdout, flush=True)
    consumer_thread = threading.Thread(target=consume_tasks)
    consumer_thread.daemon = True
    consumer_thread.start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print(f"[{MINER_ID}] Miner stopped by user.", file=sys.stdout, flush=True)
        if rabbitmq_connection and not rabbitmq_connection.is_closed:
            rabbitmq_connection.close()
        sys.exit(0)