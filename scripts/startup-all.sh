#!/usr/bin/env bash
# One-command local backend stack: Kafka + auth-server + order-service +
# inventory-service, all containerized. Angular UIs are NOT started here —
# run them yourself with `npm start` in order-ui/ and inventory-ui/, they
# proxy to these containers' published ports same as they would to
# bare-metal processes.
#
# Tries docker compose / podman compose first (uses docker-compose.yml at
# repo root as the source of truth). Falls back to plain `podman run`/`build`
# commands, mirroring the same compose file, if no compose plugin is
# installed — this is what actually runs in a bare WSL+podman setup with no
# extra tooling installed.

set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

if docker compose version >/dev/null 2>&1; then
  echo "Using: docker compose"
  exec docker compose up -d --build
fi

if podman compose version >/dev/null 2>&1; then
  echo "Using: podman compose"
  exec podman compose up -d --build
fi

echo "No compose plugin found (docker compose / podman compose). Falling back to plain podman commands."

ENGINE=podman
command -v docker >/dev/null 2>&1 && ENGINE=docker

NETWORK=pub-rec-net

echo "Cleaning up any previous containers from this stack..."
for c in zookeeper kafka auth-server order-service inventory-service; do
  "$ENGINE" rm -f "$c" >/dev/null 2>&1 || true
done
"$ENGINE" network create "$NETWORK" >/dev/null 2>&1 || true

echo "Starting zookeeper..."
"$ENGINE" run -d --name zookeeper --network "$NETWORK" -p 2181:2181 \
  -e ZOOKEEPER_CLIENT_PORT=2181 -e ZOOKEEPER_TICK_TIME=2000 \
  docker.io/confluentinc/cp-zookeeper:7.8.0
sleep 8

echo "Starting kafka..."
"$ENGINE" run -d --name kafka --network "$NETWORK" -p 9092:9092 \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092 \
  -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=PLAINTEXT:PLAINTEXT \
  -e KAFKA_INTER_BROKER_LISTENER_NAME=PLAINTEXT \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 \
  docker.io/confluentinc/cp-kafka:7.8.0
sleep 10

echo "Building auth-server..."
"$ENGINE" build -t pub-rec/auth-server -f auth-server/Dockerfile .
echo "Starting auth-server..."
"$ENGINE" run -d --name auth-server --network "$NETWORK" -p 9000:9000 \
  -e GOOGLE_CLIENT_ID="${GOOGLE_CLIENT_ID:-placeholder}" \
  -e GOOGLE_CLIENT_SECRET="${GOOGLE_CLIENT_SECRET:-placeholder}" \
  pub-rec/auth-server

echo "Waiting for auth-server (http://localhost:9000/oauth2/jwks)..."
for _ in $(seq 1 30); do
  curl -sf http://localhost:9000/oauth2/jwks >/dev/null 2>&1 && break
  sleep 2
done

echo "Building order-service..."
"$ENGINE" build -t pub-rec/order-service -f order-service/Dockerfile .
echo "Starting order-service..."
"$ENGINE" run -d --name order-service --network "$NETWORK" -p 8080:8080 \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://auth-server:9000/oauth2/jwks \
  pub-rec/order-service

echo "Building inventory-service..."
"$ENGINE" build -t pub-rec/inventory-service -f inventory-service/Dockerfile .
echo "Starting inventory-service..."
"$ENGINE" run -d --name inventory-service --network "$NETWORK" -p 8081:8081 \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092 \
  -e SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI=http://auth-server:9000/oauth2/jwks \
  pub-rec/inventory-service

echo
echo "Backend stack starting. Check status with: $ENGINE ps"
echo "Once healthy, start the UIs on the host as usual:"
echo "  cd order-ui && npm start       # http://localhost:4200"
echo "  cd inventory-ui && npm start   # http://localhost:4201"
