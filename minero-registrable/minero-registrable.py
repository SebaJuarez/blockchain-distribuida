import json
import os
import subprocess
import sys
import threading
import time
import uuid
import re

import requests
import pika
from pika import exceptions as rabbitmq_exceptions

from utils.check_gpu import check_for_nvidia_smi
from model.block import Block
from plugins.rabbitmq import rabbit_connect

# --- Configuración HTTP / Registro / Keep-alive ---
POOL_BASE_URL    = os.environ.get("POOL_BASE_URL",   "http://localhost:8081/api/pool")
REGISTER_URL     = f"{POOL_BASE_URL}/register"
KEEP_ALIVE_URL   = f"{POOL_BASE_URL}/keep-alive"
RESULTS_URL      = f"{POOL_BASE_URL}/results"

MINER_ID         = os.environ.get("MINER_ID") or str(uuid.uuid4())
MINER_PUBLIC_KEY = os.environ.get("PUBLIC_KEY")   or str(uuid.uuid4())

# --- Configuración RabbitMQ ---
RABBITMQ_HOST      = os.environ.get("RABBITMQ_HOST", "localhost")
POOL_TASKS_QUEUE   = "pool_tasks"
POOL_CONTROL_QUEUE = "pool_control"

# --- CUDA / GPU check ---
cuda_bin_dir    = os.path.join(os.getcwd(), "utils", "cuda")
cuda_exe_name   = "md5_cuda.exe" if sys.platform.startswith("win") else "md5_cuda"
CUDA_EXECUTABLE = os.path.join(cuda_bin_dir, cuda_exe_name)
gpu_available   = check_for_nvidia_smi()

# --- Estado global de cancelación ---
stop_current_task = threading.Event()

# --- Funciones HTTP ---

def register_miner():
    print(f"[{MINER_ID}] Registrando minero con GPU disponible: {gpu_available}")
    payload = {
        "publicKey": MINER_PUBLIC_KEY,
        "lastTimestamp": int(time.time()),
        "gpuMiner": gpu_available
    }
    print(f"[{MINER_ID}] Payload de registro: {payload}", flush=True)
    try:
        r = requests.post(REGISTER_URL, json=payload, timeout=5)
        r.raise_for_status()
        print(f"[{MINER_ID}] Registro exitoso: status={r.status_code}, body={r.text}")
    except Exception as e:
        print(f"[{MINER_ID}] Error en registro: {e}", file=sys.stderr)


def keep_alive_loop(interval=10):
    while True:
        try:
            r = requests.post(KEEP_ALIVE_URL, json={"minerPublicKey": MINER_PUBLIC_KEY}, timeout=5)
            r.raise_for_status()
            print(f"[{MINER_ID}] Keep-alive enviado: status={r.status_code}, body={r.text}")
        except Exception as e:
            print(f"[{MINER_ID}] Error en keep-alive: {e}", file=sys.stderr)
            print(f"[{MINER_ID}] Intentando registrar nuevamente...")
            register_miner()
        time.sleep(interval)


def send_mining_result(block, block_id):
    payload = block.to_dict()
    payload.update({"blockId": block_id, "minerId": MINER_PUBLIC_KEY})
    print(f"[{MINER_ID}] Enviando resultado de minería: nonce={block.nonce}, hash={block.hash}")
    try:
        r = requests.post(RESULTS_URL, json=payload, timeout=5)
        r.raise_for_status()
        print(f"[{MINER_ID}] Resultado enviado con éxito: status={r.status_code}, body={r.text}")
    except Exception as e:
        print(f"[{MINER_ID}] Error enviando resultado: {e}", file=sys.stderr)

# --- Lógica de PoW interrumpible ---

def mine_range(challenge, block, start, end):
    preliminary_hash = block.hash
    content_hash     = block.get_block_content_hash()

    if gpu_available:
        if not os.path.exists(CUDA_EXECUTABLE):
            print(f"[{MINER_ID}] Ejecutable CUDA no encontrado en {CUDA_EXECUTABLE}", file=sys.stderr)
            return None, None, preliminary_hash

        proc = subprocess.Popen(
            [CUDA_EXECUTABLE, challenge, content_hash, str(start), str(end)],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
        )
        print(f"[{MINER_ID}] Iniciando PoW GPU: rango {start}-{end}")
        while proc.poll() is None:
            if stop_current_task.is_set():
                proc.terminate()
                print(f"[{MINER_ID}] PoW GPU abortado en rango {start}-{end}")
                return None, None, preliminary_hash
            time.sleep(0.5)
        out, _ = proc.communicate()
    else:
        print(f"[{MINER_ID}] GPU no disponible, minando en CPU: rango {start}-{end}")
        out = ""
        from utils.find_nonce import find_nonce
        for n in range(start, end):
            if stop_current_task.is_set():
                print(f"[{MINER_ID}] PoW CPU abortado en rango {start}-{end}")
                return None, None, preliminary_hash
            result = find_nonce(challenge, content_hash, n)
            if result:
                nonce, digest = result
                out = f"Nonce encontrado: {nonce}\nHash resultante: {digest}"
                print(f"[{MINER_ID}] {out}")
                break
        if not out:
            print(f"[{MINER_ID}] No se encontró nonce en rango {start}-{end}")

    m1 = re.search(r"Nonce encontrado: (\d+)", out)
    m2 = re.search(r"Hash resultante: ([0-9a-fA-F]+)", out)
    if m1 and m2:
        return int(m1.group(1)), m2.group(1), preliminary_hash
    return None, None, preliminary_hash

# --- RabbitMQ Consumers ---

def consume_subtasks():
    conn = rabbit_connect(RABBITMQ_HOST)
    ch   = conn.channel()
    ch.queue_declare(queue=POOL_TASKS_QUEUE, durable=True)
    ch.basic_qos(prefetch_count=1)

    def on_subtask(ch, method, props, body):
        stop_current_task.clear()
        data = json.loads(body)
        blk  = Block.from_task_payload(data["block"])
        chal = data["challenge"]
        frm  = data.get("from", 0)
        to   = data.get("to", 100_000_000_000)

        print(f"[{MINER_ID}] Nueva subtarea: idx={blk.index}, rango={frm}-{to}")
        nonce, hsh, prelim = mine_range(chal, blk, frm, to)

        if nonce is not None:
            blk.nonce = nonce
            blk.hash  = hsh
            print(f"[{MINER_ID}] Subtarea resuelta: nonce={nonce}, hash={hsh}")
            send_mining_result(blk, prelim)
        else:
            print(f"[{MINER_ID}] Subtarea sin resultado o abortada.")

        try:
            ch.basic_ack(delivery_tag=method.delivery_tag)
            print(f"[{MINER_ID}] ACK enviado para subtarea idx={blk.index}")
        except Exception as e:
            print(f"[{MINER_ID}] Error enviando ACK: {e}", file=sys.stderr)

    print(f"[{MINER_ID}] Esperando subtareas en cola '{POOL_TASKS_QUEUE}'...")
    ch.basic_consume(queue=POOL_TASKS_QUEUE, on_message_callback=on_subtask)
    ch.start_consuming()


def consume_control():
    while True:
        try:
            conn = rabbit_connect(RABBITMQ_HOST)
            ch   = conn.channel()
            ch.queue_declare(queue=POOL_CONTROL_QUEUE, durable=True)
            ch.basic_qos(prefetch_count=1)
            def on_control(ch, method, props, body):
                try:
                    data = json.loads(body)
                    ph   = data.get("preliminaryHash")
                    print(f"[{MINER_ID}] Cancel received for hash={ph}")
                    stop_current_task.set()
                except Exception as e:
                    print(f"[{MINER_ID}] Error in control handler: {e}", file=sys.stderr)
                finally:
                    try:
                        ch.basic_ack(delivery_tag=method.delivery_tag)
                        print(f"[{MINER_ID}] ACK sent for cancel event hash={ph}")
                    except Exception:
                        pass

            print(f"[{MINER_ID}] Listening for cancel events on '{POOL_CONTROL_QUEUE}'…")
            ch.basic_consume(queue=POOL_CONTROL_QUEUE, on_message_callback=on_control)
            ch.start_consuming()

        except rabbitmq_exceptions.ChannelClosedByBroker as e:
            print(f"[{MINER_ID}] Channel closed by broker, reconnecting… {e}", file=sys.stderr)
        except pika.exceptions.AMQPConnectionError as e:
            print(f"[{MINER_ID}] Connection error to RabbitMQ, retrying… {e}", file=sys.stderr)
        except Exception as e:
            print(f"[{MINER_ID}] Unexpected error in consume_control: {e}", file=sys.stderr)
        time.sleep(2)

# --- Main ---

if __name__ == "__main__":
    print(f"[{MINER_ID}] Minero iniciando registro y keep-alive...")
    register_miner()
    threading.Thread(target=keep_alive_loop, daemon=True).start()

    print(f"[{MINER_ID}] Lanzando consumidores de RabbitMQ...")
    threading.Thread(target=consume_subtasks, daemon=True).start()
    threading.Thread(target=consume_control, daemon=True).start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print(f"[{MINER_ID}] Shutting down miner...", flush=True)
        sys.exit(0)