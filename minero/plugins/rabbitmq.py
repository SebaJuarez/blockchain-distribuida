import pika
import os
import sys
import time
def rabbit_connect(host=os.environ.get("RABBITMQ_HOST", "localhost")):
    """
    Establece y retorna una conexión a RabbitMQ.
    """
    retries = 5
    while retries > 0:
        try:
            connection = pika.BlockingConnection(
                pika.ConnectionParameters(host=host)
            )
            print(f"Successfully connected to RabbitMQ at {host}", file=sys.stdout, flush=True)
            return connection
        except pika.exceptions.AMQPConnectionError as e:
            print(f"Failed to connect to RabbitMQ at {host}: {e}. Retrying in 5 seconds...", file=sys.stderr, flush=True)
            retries -= 1
            time.sleep(5)
    raise Exception(f"Could not connect to RabbitMQ at {host} after multiple retries.")