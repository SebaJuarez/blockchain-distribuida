#!/usr/bin/env python3
import os
import time
import redis
from google.cloud import monitoring_v3

# Configuraciones por env vars
PROJECT_ID      = os.environ.get("GCP_PROJECT_ID", "gentle-oxygen-457917-c0")
REDIS_HOST      = os.environ.get("REDIS_HOST", "redis")
REDIS_PORT      = int(os.environ.get("REDIS_PORT", 6379))
LIST_NAME       = os.environ.get("LIST_NAME", "gpu_alive_miners")
METRIC_TYPE     = "custom.googleapis.com/gpu_alive_miners_count"
POLL_INTERVAL   = int(os.environ.get("POLL_INTERVAL_SEC", 5))

def create_metric_descriptor(client):
    descriptor = monitoring_v3.types.MetricDescriptor()
    descriptor.type = METRIC_TYPE
    descriptor.metric_kind = monitoring_v3.enums.MetricDescriptor.MetricKind.GAUGE
    descriptor.value_type = monitoring_v3.enums.MetricDescriptor.ValueType.INT64
    descriptor.description = "Cantidad de mineros GPU vivos según keep‑alive"
    descriptor = client.create_metric_descriptor(
        name=f"projects/{PROJECT_ID}",
        metric_descriptor=descriptor
    )
    print(f"Descriptor creado: {descriptor.name}")

def push_metric(client, value):
    series = monitoring_v3.types.TimeSeries()
    series.metric.type = METRIC_TYPE
    series.resource.type = "global"
    # podés agregar etiquetas si querés, ej: ID del pool
    # series.metric.labels["pool"] = "main-pool"

    point = series.points.add()
    now = time.time()
    point.interval.end_time.seconds = int(now)
    point.interval.end_time.nanos   = int((now - int(now)) * 1e9)
    point.value.int64_value = value

    client.create_time_series(name=f"projects/{PROJECT_ID}", time_series=[series])
    print(f"[{time.strftime('%Y-%m-%d %H:%M:%S')}] Métrica enviada: {value}")

def main():
    # Cliente Redis
    r = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)

    # Cliente Monitoring
    mon_client = monitoring_v3.MetricServiceClient()
    project_path = f"projects/{PROJECT_ID}"

    # Crear descriptor si no existe
    descriptors = list(mon_client.list_metric_descriptors(name=project_path, filter_=f'metric.type = "{METRIC_TYPE}"'))
    if not descriptors:
        create_metric_descriptor(mon_client)

    # Loop de polling
    while True:
        try:
            count = r.llen(LIST_NAME)
        except Exception as e:
            print(f"Error conectando a Redis: {e}")
            count = 0

        try:
            push_metric(mon_client, count)
        except Exception as e:
            print(f"Error enviando métrica: {e}")

        time.sleep(POLL_INTERVAL)

if __name__ == "__main__":
    main()