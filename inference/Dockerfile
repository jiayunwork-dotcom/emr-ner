FROM python:3.11-slim

WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY protos/ ./protos/
COPY src/ ./src/

RUN python -m grpc_tools.protoc \
    -I./protos \
    --python_out=./src \
    --grpc_python_out=./src \
    ./protos/inference.proto

RUN mkdir -p /app/models

EXPOSE 50051

ENV PYTHONPATH=/app/src

CMD ["python", "src/server.py"]
