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

cuda_bin_dir = os.path.join(os.getcwd(), "utils", "cuda")
cuda_exe_name = "md5_cuda.exe" if sys.platform.startswith("win") else "md5_cuda"
CUDA_EXECUTABLE_PATH = os.path.join(cuda_bin_dir, cuda_exe_name)

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
        print(f"[{miner_id}] Coordinador respondió: {resp.status_code}")
    except Exception as e:
        print(f"[{miner_id}] Error enviando resultado: {e}", file=sys.stderr)


def mine_block(challenge, block, frm, to):
    """
    Minado interrumpible. Devuelve (nonce, hash, preliminary_hash).
    """
    preliminary = block.hash
    content_hash = block.get_block_content_hash()
    nonce = None
    solved_hash = None

    reason = None
    while True:
        if stop_current_task.is_set():
            reason = "CANDIDATE_BLOCK_DROPPED" if stop_current_task.reason == "dropped" else "RESOLVED_CANDIDATE_BLOCK"
            print(f"[{MINER_ID}] ABORTANDO por evento: {reason}.")
            return None, None, preliminary

        if gpu_available:
            if not os.path.exists(CUDA_EXECUTABLE_PATH):
                print(f"[{MINER_ID}] CUDA no encontrado.", file=sys.stderr)
                return None, None, preliminary

            proc = subprocess.Popen(
                [CUDA_EXECUTABLE_PATH, challenge, content_hash, str(frm), str(to)],
                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True
            )
            while proc.poll() is None:
                if stop_current_task.is_set():
                    proc.terminate()
                    print(f"[{MINER_ID}] ABORTANDO por evento: {stop_current_task.reason}.")
                    return None, None, preliminary
                time.sleep(0.5)
            out, _ = proc.communicate()
        else:
            from utils.find_nonce import find_nonce_with_prefix
            out = ""
            for n in range(frm, to):
                if stop_current_task.is_set():
                    print(f"[{MINER_ID}] ABORTANDO por evento: {stop_current_task.reason}.")
                    return None, None, preliminary
                h = find_nonce_with_prefix(challenge, content_hash, n, n + 1)
                if h:
                    out = f"Nonce encontrado: {n}\nHash resultante: {h}"
                    break

        m1 = re.search(r"Nonce encontrado: (\d+)", out)
        m2 = re.search(r"Hash resultante: ([0-9a-fA-F]+)", out)
        if m1 and m2:
            nonce = int(m1.group(1))
            solved_hash = m2.group(1)
        return nonce, solved_hash, preliminary


def fanout_listener():
    """
    Escucha el exchange 'blockchain' y marca stop_current_task con reason.
    """
    conn = pika.BlockingConnection(pika.ConnectionParameters(host=RABBITMQ_HOST))
    ch = conn.channel()
    ch.exchange_declare(exchange=EXCHANGE_NAME, exchange_type=EXCHANGE_TYPE, durable=True)
    q = ch.queue_declare(queue="", exclusive=True).method.queue
    ch.queue_bind(exchange=EXCHANGE_NAME, queue=q)

    def on_event(_, __, ___, body):
        try:
            msg = json.loads(body)
            event = msg.get("event")

            # Bloque resuelto por otro minero
            if event == "RESOLVED_CANDIDATE_BLOCK":
                by = msg.get("minerId")
                hb = msg.get("preliminaryHashBlockResolved")
                if by and by != MINER_ID:
                    print(f"[{MINER_ID}] RESOLVED por {by} (hash={hb}), abortando.")
                    stop_current_task.reason = "resolved"
                    stop_current_task.set()
                else:
                    print(f"[{MINER_ID}] Self-resolved, ignorando.")

            # Bloque descartado
            elif event == "CANDIDATE_BLOCK_DROPPED":
                hb = msg.get("preliminaryHashBlockDropped")
                print(f"[{MINER_ID}] DROPPED hash={hb}, abortando.")
                stop_current_task.reason = "dropped"
                stop_current_task.set()

        except Exception as e:
            print(f"[{MINER_ID}] Error en fanout: {e}", file=sys.stderr)

    print(f"[{MINER_ID}] Listening fanout '{EXCHANGE_NAME}'…")
    ch.basic_consume(queue=q, on_message_callback=on_event, auto_ack=True)
    ch.start_consuming()


def mining_task_consumer():
    """
    Se bindea al mismo FanoutExchange 'blockchain'
    """
    while True:
        try:
            conn = rabbit_connect(RABBITMQ_HOST)
            ch = conn.channel()

            # Declara una cola efímera exclusiva y bindea al fanout 'blockchain'
            result = ch.queue_declare(queue="", exclusive=True, durable=True)
            my_queue = result.method.queue
            ch.exchange_declare(exchange=EXCHANGE_NAME, exchange_type=EXCHANGE_TYPE, durable=True)
            ch.queue_bind(exchange=EXCHANGE_NAME, queue=my_queue)

            def on_task(inner_ch, method, props, body):
                stop_current_task.clear()
                stop_current_task.reason = None

                try:
                    task = json.loads(body)
                    if task.get("event") != "NEW_CANDIDATE_BLOCK":
                        return

                    challenge = task["challenge"]
                    blk = Block.from_task_payload(task["block"])
                    frm = task.get("from", 0)
                    to = task.get("to", 100_000_000_000)

                    print(f"[{MINER_ID}] NEW task idx={blk.index} range={frm}-{to}")
                    nonce, fh, prelim = mine_block(challenge, blk, frm, to)

                    if nonce is not None:
                        blk.nonce = nonce
                        blk.hash = fh
                        send_mining_result(blk, MINER_ID, prelim)
                    else:
                        print(f"[{MINER_ID}] Sin solución o abortado.")

                except Exception as e:
                    print(f"[{MINER_ID}] Error on_task: {e}", file=sys.stderr)

                finally:
                    try:
                        inner_ch.basic_ack(method.delivery_tag)
                    except Exception as ack_e:
                        print(f"[{MINER_ID}] Ack ignored: {ack_e}", file=sys.stderr)

            ch.basic_consume(queue=my_queue, on_message_callback=on_task)
            print(f"[{MINER_ID}] Esperando NEW_CANDIDATE_BLOCK en '{my_queue}'…")
            ch.start_consuming()

        except rabbitmq_exceptions.ChannelClosedByBroker as e:
            print(f"[{MINER_ID}] Canal cerrado, reconectando… {e}", file=sys.stderr)
            time.sleep(1)
        except Exception as e:
            print(f"[{MINER_ID}] Error consumidor: {e}", file=sys.stderr)
            time.sleep(2)


if __name__ == "__main__":
    print(f"[{MINER_ID}] Minero arrancando…")
    # Listener de eventos
    threading.Thread(target=fanout_listener, daemon=True).start()
    # Consumer de tareas
    threading.Thread(target=mining_task_consumer, daemon=True).start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print(f"[{MINER_ID}] Minero apagando…")
        sys.exit(0)