#!/bin/bash
exec > /var/log/startup-script.log 2>&1
set -x

# === Startup Worker Sobel ===

RABBIT_HOST="rabbitmq.internal"
RABBIT_PORT="5672"


# 1) Actualizo repositorios e instalo dependencias
sudo apt-get update -y
sudo apt-get install -y docker.io netcat-traditional

# 2) Espero a que RabbitMQ esté disponible
until nc -z $RABBIT_HOST $RABBIT_PORT; do
  sleep 5
done

# 3) Arranco el contenedor de worker
sudo docker run --rm \
  -e RABBITMQ_HOST=$RABBIT_HOST \
  -e RABBITMQ_USER=guest \
  -e RABBITMQ_PASS=guest \
  -e RABBITMQ_PORT=$RABBIT_PORT \
  -e POOL_BASE_URL=http://nodepool.internal:8081/api/pools \
  dagyss/minero-registrable-cpu:latest

# 4) Fin del script
echo "MineroCPU iniciado correctamente"
