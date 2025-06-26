FROM python:3.9-slim
WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY gpu_alive_miners.py .
CMD ["python3", "gpu_alive_miners.py"]