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
POOL_BASE_URL   = os.environ.get("POOL_BASE_URL",   "http://localhost:8081/api/pool")
REGISTER_URL    = f"{POOL_BASE_URL}/register"
KEEP_ALIVE_URL  = f"{POOL_BASE_URL}/keep-alive"
RESULTS_URL     = f"{POOL_BASE_URL}/result"

MINER_ID        = os.environ.get("MINER_ID") or str(uuid.uuid4())
MINER_PUBLIC_KEY= os.environ.get("PUBLIC_KEY")   or str(uuid.uuid4())

# --- Configuración RabbitMQ ---
RABBITMQ_HOST         = os.environ.get("RABBITMQ_HOST", "localhost")
POOL_TASKS_QUEUE      = "pool_tasks"
POOL_CONTROL_QUEUE    = "pool_control"

# --- CUDA / GPU check ---
cuda_bin_dir      = os.path.join(os.getcwd(), "utils", "cuda")
cuda_exe_name     = "md5_cuda.exe" if sys.platform.startswith("win") else "md5_cuda"
CUDA_EXECUTABLE   = os.path.join(cuda_bin_dir, cuda_exe_name)
gpu_available     = check_for_nvidia_smi()

# --- Estado global de cancelación ---
stop_current_task = threading.Event()

# --- Funciones HTTP ---

def register_miner():
    print(f"[{MINER_ID}] Registering miner... con gpu: {gpu_available}")
    payload = {
        "publicKey": MINER_ID,
        "lastTimestamp": int(time.time()),
        "gpuMiner": gpu_available
    }
    print(f"[{MINER_ID}] Registering with: {payload}", flush=True)
    try:
        r = requests.post(REGISTER_URL, json=payload)
        r.raise_for_status()
        print(f"[{MINER_ID}] Registered: {r.status_code}")
    except Exception as e:
        print(f"[{MINER_ID}] Register error: {e}", file=sys.stderr)


def keep_alive_loop(interval=10):
    while True:
        try:
            r = requests.post(KEEP_ALIVE_URL, json={"minerPublicKey": MINER_ID})
            r.raise_for_status()
            print(f"[{MINER_ID}] Keep-alive sent")
        except Exception as e:
            print(f"[{MINER_ID}] Keep-alive error: {e}", file=sys.stderr)
        time.sleep(interval)


def send_mining_result(block, block_id):
    payload = block.to_dict()
    payload["blockId"] = block_id
    payload["minerId"] = MINER_ID
    try:
        r = requests.post(RESULTS_URL, json=payload)
        r.raise_for_status()
        print(f"[{MINER_ID}] Result sent: {r.status_code}")
    except Exception as e:
        print(f"[{MINER_ID}] Send result error: {e}", file=sys.stderr)


# --- Lógica de PoW interrumpible ---

def mine_range(challenge, block, start, end):
    preliminary_hash = block.hash
    content_hash = block.get_block_content_hash()

    if gpu_available:
        if not os.path.exists(CUDA_EXECUTABLE):
            print(f"[{MINER_ID}] CUDA binary not found", file=sys.stderr)
            return None, None, preliminary_hash

        proc = subprocess.Popen(
            [CUDA_EXECUTABLE, challenge, content_hash, str(start), str(end)],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
        )
        while proc.poll() is None:
            if stop_current_task.is_set():
                proc.terminate()
                print(f"[{MINER_ID}] Aborting PoW")
                return None, None, preliminary_hash
            time.sleep(0.5)
        out, _ = proc.communicate()

    else:
        from utils.find_nonce import find_nonce_with_prefix
        out = ""
        for n in range(start, end):
            if stop_current_task.is_set():
                print(f"[{MINER_ID}] Aborting PoW")
                return None, None, preliminary_hash
            h = find_nonce_with_prefix(challenge, content_hash, n, n+1)
            if h:
                out = f"Nonce encontrado: {n}\nHash resultante: {h}"
                break

    m1 = re.search(r"Nonce encontrado: (\d+)", out)
    m2 = re.search(r"Hash resultante: ([0-9a-fA-F]+)", out)
    if m1 and m2:
        return int(m1.group(1)), m2.group(1), preliminary_hash
    return None, None, preliminary_hash


# --- RabbitMQ Consumers ---

def consume_subtasks():
    """
    Consume de pool_tasks, procesa cada SubTask(block, challenge, from, to)
    y reporta el resultado.
    """
    conn = rabbit_connect(RABBITMQ_HOST)
    ch   = conn.channel()
    ch.queue_declare(queue=POOL_TASKS_QUEUE, durable=True)
    ch.basic_qos(prefetch_count=1)

    def on_subtask(ch, method, props, body):
        stop_current_task.clear()
        data = json.loads(body)
        blk   = Block.from_task_payload(data["block"])
        chal  = data["challenge"]
        frm   = data["from"]
        to    = data["to"]

        print(f"[{MINER_ID}] SubTarea {blk.index} Rango {frm}-{to}")
        nonce, hsh, prelim = mine_range(chal, blk, frm, to)

        if nonce is not None:
            blk.nonce = nonce
            blk.hash  = hsh
            send_mining_result(blk, prelim)
        else:
            print(f"[{MINER_ID}] Sin solución o abortado.")

        try:
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except Exception as e:
            print(f"[{MINER_ID}] Ack error: {e}", file=sys.stderr)

    ch.basic_consume(queue=POOL_TASKS_QUEUE, on_message_callback=on_subtask)
    print(f"[{MINER_ID}] Esperando por subtareas...")
    ch.start_consuming()


def consume_control():
    """
    Consume de pool_control, recibe CancelTask(preliminaryHash)
    y marca stop_current_task para abortar PoW.
    """
    conn = rabbit_connect(RABBITMQ_HOST)
    ch   = conn.channel()
    ch.queue_declare(queue=POOL_CONTROL_QUEUE, durable=True)

    def on_control(ch, method, props, body):
        data = json.loads(body)
        ph  = data.get("preliminaryHash")
        print(f"[{MINER_ID}] Cancel received for {ph}")
        stop_current_task.set()
        try:
            ch.basic_ack(delivery_tag=method.delivery_tag)
        except:
            pass

    ch.basic_qos(prefetch_count=1)
    ch.basic_consume(queue=POOL_CONTROL_QUEUE, on_message_callback=on_control)
    print(f"[{MINER_ID}] Waiting for cancel events...")
    ch.start_consuming()


# --- Main ---

if __name__ == "__main__":
    # Registro + keep-alive
    register_miner()
    threading.Thread(target=keep_alive_loop, daemon=True).start()

    # RabbitMQ: subtasks + control
    threading.Thread(target=consume_subtasks, daemon=True).start()
    threading.Thread(target=consume_control, daemon=True).start()

    # Mantener vivo
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print(f"[{MINER_ID}] Shutting down")
        sys.exit(0)