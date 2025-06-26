#!/bin/bash
exec > /var/log/startup-script.log 2>&1
set -x

# === Startup Worker Sobel ===

# 1) Actualizo repositorios e instalo dependencias
sudo apt-get update -y
sudo apt-get install -y docker.io netcat-traditional

# 2) Espero a que RabbitMQ este disponible
until nc -z 35.239.206.202 5672; do
  sleep 5
done


# 3) Arranco el contenedor de worker
sudo docker run --rm \
  -e RABBITMQ_HOST=35.239.206.202 \
  -e RABBITMQ_USER=guest \
  -e RABBITMQ_PASS=guest \
  -e RABBITMQ_PORT=5672 \
  dagyss/minero-registrable-cpu:latest

# 4) Fin del script
echo "MineroCPU iniciado correctamente"
