import json
import os
import subprocess
import sys
import threading
import time
import re

import requests
import pika
from pika import exceptions as rabbitmq_exceptions

from plugins.rabbitmq import rabbit_connect
from model.block import Block
from utils.check_gpu import check_for_nvidia_smi

# --- Configuración ---
BLOCKS_COORDINATOR_URL = os.environ.get(
    "BLOCKS_COORDINATOR_URL", "http://localhost:8080/api/blocks/result"
)
RABBITMQ_HOST = os.environ.get("RABBITMQ_HOST", "localhost")
MINER_ID = os.environ.get("MINER_ID", "miner-python-001")

EXCHANGE_NAME = "blockchain"
EXCHANGE_TYPE = "fanout"

# CUDA
cuda_bin_dir = os.path.join(os.getcwd(), "utils", "cuda")
cuda_exe_name = "md5_cuda.exe" if sys.platform.startswith("win") else "md5_cuda"
CUDA_EXECUTABLE_PATH = os.path.join(cuda_bin_dir, cuda_exe_name)

# Estado global
gpu_available = check_for_nvidia_smi()
stop_current_task = threading.Event()


def send_mining_result(block_solved, miner_id, block_id):
    try:
        payload = block_solved.to_dict()
        payload["blockId"] = block_id
        payload["minerId"] = miner_id

        print(f"[{miner_id}] Enviando bloque candidato al coordinador : {payload['hash']}")
        resp = requests.post(BLOCKS_COORDINATOR_URL, json=payload)
        resp.raise_for_status()
        print(f"[{miner_id}] Coordinator response: {resp.status_code} - {resp.text}")
    except Exception as e:
        print(f"[{miner_id}] Error enviando el resultado: {e}", file=sys.stderr)


def mine_block(challenge, block, from_range, to_range):
    """
    Minado interrumpible. Devuelve (nonce, hash, preliminary_hash) o (None, None, preliminary_hash).
    """
    preliminary_hash = block.hash
    block_content_hash = block.get_block_content_hash()

    found_nonce = None
    solved_hash = None

    if gpu_available:
        if not os.path.exists(CUDA_EXECUTABLE_PATH):
            print(f"[{MINER_ID}] CUDA ejecutable no encontrado.", file=sys.stderr)
            return None, None, preliminary_hash

        proc = subprocess.Popen(
            [CUDA_EXECUTABLE_PATH, challenge, block_content_hash, str(from_range), str(to_range)],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        while proc.poll() is None:
            if stop_current_task.is_set():
                proc.terminate()
                print(f"[{MINER_ID}] ABORTANDO por evento: RESOLVED_CANDIDATE_BLOCK.")
                return None, None, preliminary_hash
            time.sleep(0.5)

        out, _ = proc.communicate()
        m1 = re.search(r"Nonce encontrado: (\d+)", out)
        m2 = re.search(r"Hash resultante: ([0-9a-fA-F]+)", out)
        if m1 and m2:
            found_nonce = int(m1.group(1))
            solved_hash = m2.group(1)

    else:
        from utils.find_nonce import find_nonce_with_prefix
        for n in range(from_range, to_range):
            if stop_current_task.is_set():
                print(f"[{MINER_ID}] ABORTANDO por evento: RESOLVED_CANDIDATE_BLOCK.")
                return None, None, preliminary_hash
            h = find_nonce_with_prefix(challenge, block_content_hash, n, n + 1)
            if h:
                found_nonce, solved_hash = n, h
                break

    return found_nonce, solved_hash, preliminary_hash


def fanout_listener():
    """
    Hilo que escucha el exchange fanout y marca stop_current_task
    sólo si otro minero resolvió el bloque.
    """
    conn = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST))
    ch = conn.channel()
    ch.exchange_declare(exchange=EXCHANGE_NAME, exchange_type=EXCHANGE_TYPE, durable=True)
    q = ch.queue_declare(queue="", exclusive=True).method.queue
    ch.queue_bind(exchange=EXCHANGE_NAME, queue=q)

    def on_event(_, __, ___, body):
        try:
            msg = json.loads(body)
            if msg.get("event") == "RESOLVED_CANDIDATE_BLOCK":
                by = msg.get("minerId")
                hb = msg.get("preliminaryHashBlockResolved")
                if by and by != MINER_ID:
                    print(f"[{MINER_ID}] Se detecto que fue resuelto por: {by} (hash={hb}), abortando tarea de mineria.")
                    stop_current_task.set()
                else:
                    print(f"[{MINER_ID}] Auto-resuelto ({by}), ignorando evento..")
        except Exception as e:
            print(f"[{MINER_ID}] Error fanout_listener: {e}", file=sys.stderr)

    print(f"[{MINER_ID}] Listening fanout '{EXCHANGE_NAME}'…")
    ch.basic_consume(queue=q, on_message_callback=on_event, auto_ack=True)
    ch.start_consuming()


def mining_task_consumer():
    """
    Hilo principal de consumo de la cola 'blocks'. Reintenta si el canal se cierra.
    """
    while True:
        try:
            conn = rabbit_connect(RABBITMQ_HOST)
            ch = conn.channel()
            ch.queue_declare(queue="blocks", durable=True)

            def on_task(inner_ch, method, props, body):
                stop_current_task.clear()
                preliminary = None
                try:
                    task = json.loads(body)
                    if task.get("event") != "NEW_CANDIDATE_BLOCK":
                        return  # lo clean-ackeamos abajo

                    challenge = task["challenge"]
                    blk = Block.from_task_payload(task["block"])
                    frm = task.get("from", 0)
                    to = task.get("to", 100_000_000_000)

                    print(f"[{MINER_ID}] NUEVA tarea idx={blk.index} range={frm}-{to}")
                    nonce, fh, preliminary = mine_block(challenge, blk, frm, to)

                    if nonce is not None:
                        blk.nonce = nonce
                        blk.hash = fh
                        send_mining_result(blk, MINER_ID, preliminary)
                    else:
                        print(f"[{MINER_ID}] No se encontro solución o fue abortada.")

                except Exception as e:
                    print(f"[{MINER_ID}] Error en on_task: {e}", file=sys.stderr)

                finally:
                    # siempre ack-eamos, para no reprocesar
                    try:
                        inner_ch.basic_ack(method.delivery_tag)
                    except Exception as ack_e:
                        print(f"[{MINER_ID}] Ack failed (ignored): {ack_e}", file=sys.stderr)

            ch.basic_consume(queue="blocks", on_message_callback=on_task)
            print(f"[{MINER_ID}] Esperando tareas en la cola 'Blocks'..")
            ch.start_consuming()

        except rabbitmq_exceptions.ChannelClosedByBroker as e:
            print(f"[{MINER_ID}] ChannelClosedByBroker, reconectando... {e}", file=sys.stderr)
            time.sleep(1)
        except Exception as e:
            print(f"[{MINER_ID}] Error inesperado en el consumidor: {e}", file=sys.stderr)
            time.sleep(2)


if __name__ == "__main__":
    print(f"[{MINER_ID}] Minero prendiendo..")

    threading.Thread(target=fanout_listener, daemon=True).start()
    threading.Thread(target=mining_task_consumer, daemon=True).start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print(f"[{MINER_ID}] Minero apagando..")
        sys.exit(0)