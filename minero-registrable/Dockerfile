FROM python:3.11-slim

WORKDIR /app

COPY . .

RUN python -m pip install --upgrade pip && \
    if [ -f requirements.txt ]; then pip install -r requirements.txt; fi

CMD ["python", "-m", "minero-registrable"]