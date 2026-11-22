#!/usr/bin/env bash
# Executa inclusive os testes de concorrência, snapshot e upgrade que dependem do PostgreSQL.
set -euo pipefail
cd -- "$(dirname -- "$0")/.."
password=$(openssl rand -hex 24)
container=$(docker run -d --rm --tmpfs /var/lib/postgresql/data:rw \
  -e POSTGRES_USER=resolveai_teste -e "POSTGRES_PASSWORD=$password" -p 127.0.0.1::5432 postgres:17-alpine)
trap 'docker rm -f "$container" >/dev/null' EXIT
port=$(docker port "$container" 5432/tcp | cut -d: -f2)
for tentativa in {1..60}; do
  if docker exec "$container" pg_isready -h 127.0.0.1 -U resolveai_teste >/dev/null; then break; fi
  sleep 1
done
for banco in autorizacao dashboard categorias historico; do
  docker exec "$container" createdb -U resolveai_teste "$banco"
done
base="jdbc:postgresql://127.0.0.1:$port"
export AUTORIZACAO_POSTGRES17_URL="$base/autorizacao" DASHBOARD_POSTGRES17_URL="$base/dashboard"
export P1_POSTGRES17_MIGRATION_URL="$base/categorias" HISTORY_MIGRATION_URL="$base/historico"
for prefixo in AUTORIZACAO_POSTGRES17 DASHBOARD_POSTGRES17 P1_POSTGRES17 HISTORY_MIGRATION; do
  export "${prefixo}_USER=resolveai_teste" "${prefixo}_PASSWORD=$password"
done
mvn -B -ntp -f backend/pom.xml clean verify
