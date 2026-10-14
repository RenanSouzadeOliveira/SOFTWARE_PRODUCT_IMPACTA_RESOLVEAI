#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

repo_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
browser=0
keep=0
skip_build=0
for argument in "$@"; do
  case "$argument" in
    --com-navegador) browser=1 ;;
    --manter) keep=1 ;;
    --sem-build) skip_build=1 ;;
    --help)
      echo 'Uso: bash scripts/validar-ac2.sh [--com-navegador] [--manter] [--sem-build]'
      echo 'Cria PostgreSQL vazio isolado, valida a API e prepara exemplos de todos os estados.'
      echo '--com-navegador: valida interface e grava vídeo técnico local (Playwright necessário).'
      echo '--manter: preserva ambiente e credenciais locais para demonstração após sucesso.'
      echo '--sem-build: usa artefatos já testados; não substitui a execução dos testes.'
      exit 0 ;;
    *) echo "Argumento desconhecido: $argument" >&2; exit 2 ;;
  esac
done
for program in docker java node curl openssl; do
  command -v "$program" >/dev/null || { echo "Pré-requisito ausente: $program" >&2; exit 1; }
done
cd -- "$repo_dir"
if [[ "$skip_build" == 0 ]]; then
  (cd backend && mvn clean verify)
  (cd frontend && npm ci && npm test -- --watch=false && npm run build)
fi
jar="$repo_dir/backend/target/resolveai-api-0.0.1-SNAPSHOT.jar"
[[ -f "$jar" ]] || { echo 'JAR ausente. Execute mvn clean verify no backend.' >&2; exit 1; }

artifacts=$(mktemp -d /tmp/resolveai-ac2-XXXXXX)
container="resolveai-ac2-$(openssl rand -hex 6)"
api_pid=''
web_pid=''
container_created=0
cleanup() {
  result=$?
  if [[ "$keep" == 1 && "$result" == 0 ]]; then
    echo "Ambiente de demonstração preservado. Instruções privadas: $artifacts/ambiente.txt"
    return
  fi
  for process in "$web_pid" "$api_pid"; do
    if [[ -n "$process" ]] && kill -0 "$process" 2>/dev/null; then kill -- "$process" 2>/dev/null || true; fi
  done
  if [[ "$container_created" == 1 ]]; then docker rm -f "$container" >/dev/null 2>&1 || true; fi
  echo "Ambiente descartável removido; relatórios locais preservados em $artifacts"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

db_port=${AC2_DB_PORT:-55439}
api_port=${AC2_API_PORT:-18082}
if [[ "$keep" == 1 ]]; then
  api_port=${AC2_API_PORT:-8080}
  [[ "$api_port" == 8080 ]] || { echo '--manter exige API na porta 8080, usada pelo Angular de desenvolvimento.' >&2; exit 2; }
fi
web_port=${AC2_WEB_PORT:-4200}
for port in "$db_port" "$api_port" "$web_port"; do
  [[ "$port" =~ ^[0-9]+$ ]] && (( port > 0 && port < 65536 )) || { echo 'Porta inválida.' >&2; exit 2; }
done
# Refuse occupied ports instead of probing an unrelated application already running.
node --input-type=module - "$db_port" "$api_port" "$web_port" <<'JS'
import net from 'node:net';
for (const port of process.argv.slice(2).map(Number)) {
  await new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once('error', () => reject(new Error(`Porta ${port} ocupada; escolha outra porta AC2.`)));
    server.listen(port, '127.0.0.1', () => server.close(resolve));
  });
}
JS
export AC2_CONTAINER="$container"
export AC2_API_URL="http://127.0.0.1:$api_port/api"
export AC2_BROWSER_URL="http://127.0.0.1:$web_port"
export DB_USERNAME=resolveai_test
export DB_PASSWORD
DB_PASSWORD=$(openssl rand -hex 24)
export DB_URL="jdbc:postgresql://127.0.0.1:$db_port/resolveai_ac2"
export JWT_SECRET
JWT_SECRET=$(openssl rand -hex 48)
export CORS_ALLOWED_ORIGINS="http://localhost:$web_port,http://127.0.0.1:$web_port"
export SPRING_PROFILES_ACTIVE=dev

docker run -d --name "$container" --label resolveai.validation=ac2 \
  -e POSTGRES_DB=resolveai_ac2 -e POSTGRES_USER=resolveai_test \
  -e "POSTGRES_PASSWORD=$DB_PASSWORD" -p "127.0.0.1:$db_port:5432" \
  postgres:17-alpine >/dev/null
container_created=1
ready=0
for attempt in {1..60}; do
  if docker exec "$container" pg_isready -U resolveai_test -d resolveai_ac2 >/dev/null 2>&1; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo 'PostgreSQL não iniciou.' >&2; exit 1; }
cp -- "$jar" "$artifacts/resolveai-api.jar"
java -jar "$artifacts/resolveai-api.jar" --server.address=127.0.0.1 --server.port="$api_port" >"$artifacts/api.log" 2>&1 &
api_pid=$!
ready=0
for attempt in {1..90}; do
  kill -0 "$api_pid" 2>/dev/null || { echo "API não iniciou; confira $artifacts/api.log" >&2; exit 1; }
  if [[ $(< "$artifacts/api.log") == *'Started ResolveAiApplication'* ]] && \
    curl --fail --silent "$AC2_API_URL/health" >/dev/null; then ready=1; break; fi
  sleep 1
done
[[ "$ready" == 1 ]] || { echo "API indisponível; confira $artifacts/api.log" >&2; exit 1; }
curl --fail --silent "http://127.0.0.1:$api_port/v3/api-docs" >"$artifacts/openapi.json"
node scripts/ac2-api.mjs --fixtures "$artifacts/fixtures.json" | tee "$artifacts/api-report.json"

if [[ "$browser" == 1 || "$keep" == 1 ]]; then
  [[ -d frontend/node_modules ]] || { echo 'Execute npm ci no frontend.' >&2; exit 1; }
  # Execute directly: the tracked PID owns the Angular server, rather than an npm wrapper.
  (cd frontend && exec node node_modules/@angular/cli/bin/ng.js serve --host 127.0.0.1 --port "$web_port") \
    >"$artifacts/frontend.log" 2>&1 &
  web_pid=$!
  if ! kill -0 "$web_pid" 2>/dev/null; then echo "Angular não iniciou; confira $artifacts/frontend.log" >&2; exit 1; fi
  ready=0
  for attempt in {1..90}; do
    kill -0 "$web_pid" 2>/dev/null || { echo "Angular não iniciou; confira $artifacts/frontend.log" >&2; exit 1; }
    if curl --fail --silent "$AC2_BROWSER_URL" >/dev/null; then ready=1; break; fi
    sleep 1
  done
  [[ "$ready" == 1 ]] || { echo 'Angular indisponível.' >&2; exit 1; }
fi
if [[ "$browser" == 1 ]]; then
  node scripts/ac2-browser.mjs --fixtures "$artifacts/fixtures.json" --output-dir "$artifacts/browser" \
    | tee "$artifacts/browser-report.json"
fi
if [[ "$keep" == 1 ]]; then
  {
    echo "Interface: $AC2_BROWSER_URL"
    echo "API: $AC2_API_URL"
    echo "Credenciais fictícias (arquivo privado): $artifacts/fixtures.json"
    echo 'Não publique fixtures.json: contém senha e tokens locais.'
    echo 'Para encerrar somente este ambiente:'
    if [[ -n "$web_pid" ]]; then echo "kill $web_pid"; fi
    echo "kill $api_pid"
    echo "docker rm -f $container"
  } >"$artifacts/ambiente.txt"
fi
echo "AC2 validada. Artefatos locais: $artifacts"
