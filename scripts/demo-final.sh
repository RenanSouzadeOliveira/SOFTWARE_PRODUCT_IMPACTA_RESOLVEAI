#!/usr/bin/env bash
# Laboratório descartável. Execute a partir de um checkout com os dois builds prontos.
set -euo pipefail
umask 077
cd -- "$(dirname -- "$0")/.."
root=$PWD
modo=${1:-}
[[ $# -le 1 && ( -z "$modo" || "$modo" == "--verificar" ) ]] || { echo 'Uso: bash scripts/demo-final.sh [--verificar]' >&2; exit 2; }
api_port=${DEMO_API_PORT:-18595}
web_port=${DEMO_WEB_PORT:-14595}
[[ "$api_port" =~ ^[0-9]{1,5}$ && "$web_port" =~ ^[0-9]{1,5}$ && "$api_port" != "$web_port" ]] || exit 2
jar="$root/backend/target/resolveai-api-0.0.1-SNAPSHOT.jar"
[[ -s "$jar" && -s frontend/dist/frontend/browser/index.html ]] || { echo 'Gere o JAR e o build Angular conforme o README.' >&2; exit 1; }
work=$(mktemp -d "${TMPDIR:-/tmp}/resolveai-final.XXXXXXXX")
container=''
api_pid=''
web_pid=''
cleanup() {
  if [[ -n "$web_pid" ]]; then kill "$web_pid" 2>/dev/null || true; wait "$web_pid" 2>/dev/null || true; fi
  if [[ -n "$api_pid" ]]; then kill "$api_pid" 2>/dev/null || true; wait "$api_pid" 2>/dev/null || true; fi
  if [[ -n "$container" ]]; then docker rm -f "$container" >/dev/null; fi
  rm -rf -- "$work"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
# O banco, a senha e a porta são exclusivos desta execução. O diretório de dados fica em memória.
db_password=$(openssl rand -hex 24)
container=$(docker run -d --rm --tmpfs /var/lib/postgresql/data:rw \
  -p 127.0.0.1::5432 -e POSTGRES_USER=resolveai -e POSTGRES_DB=resolveai \
  -e "POSTGRES_PASSWORD=$db_password" postgres:17-alpine)
db_port=$(docker port "$container" 5432/tcp | cut -d: -f2)
for tentativa in {1..60}; do
  if docker exec "$container" pg_isready -h 127.0.0.1 -U resolveai -d resolveai >/dev/null; then break; fi
  sleep 1
done
api="http://127.0.0.1:$api_port"
web="http://127.0.0.1:$web_port"
# A aplicação recebe somente a configuração deste laboratório, inclusive ao iniciar o seed.
app=(env -i "PATH=$PATH" "HOME=$work" SPRING_PROFILES_ACTIVE=dev
  "DB_URL=jdbc:postgresql://127.0.0.1:$db_port/resolveai" DB_USERNAME=resolveai
  "DB_PASSWORD=$db_password" "JWT_SECRET=$(openssl rand -hex 48)" "CORS_ALLOWED_ORIGINS=$web"
  "AC3_SEED_CREDENTIALS_FILE=$work/contas.json")
"${app[@]}" java -jar "$jar" --server.address=127.0.0.1 --server.port="$api_port" >"$work/api.log" 2>&1 &
api_pid=$!
aguardar() {
  local pid=$1 url=$2 log=$3 sinal=$4
  for tentativa in {1..90}; do
    kill -0 "$pid" 2>/dev/null || { echo "Falha ao iniciar $url. Verifique portas e builds." >&2; return 1; }
    if grep -qF "$sinal" "$log" && curl -qfsS "$url" >/dev/null 2>&1; then return; fi
    sleep 1
  done
  echo "Tempo excedido ao iniciar $url." >&2
  return 1
}
aguardar "$api_pid" "$api/api/health" "$work/api.log" 'Started ResolveAiApplication'
(cd "$work" && jar xf "$jar" BOOT-INF/classes BOOT-INF/lib)
seed() {
  (cd "$work" && "${app[@]}" java -cp 'BOOT-INF/classes:BOOT-INF/lib/*' "$root/scripts/Ac3DemoSeed.java") >"$work/seed.log" 2>&1 || {
    echo 'Falha na preparação dos dados acadêmicos.' >&2; return 1;
  }
  grep '^AC3_SEED_RESULT=' "$work/seed.log"
}
seed
if [[ "$modo" == "--verificar" ]]; then seed; fi
AC3_API_URL="$api/api" AC3_WEB_PORT="$web_port" node scripts/ac3-serve.mjs >"$work/web.log" 2>&1 &
web_pid=$!
aguardar "$web_pid" "$web/" "$work/web.log" 'Interface disponível'
node scripts/verificar-demo-final.mjs "$web" "$work/contas.json" "$modo"
if [[ "$modo" != "--verificar" ]]; then
  echo "Interface $web | Swagger $api/swagger-ui.html"
  echo "Contas locais por perfil em $work/contas.json (privado). Ctrl+C encerra e descarta este laboratório."
  wait -n "$api_pid" "$web_pid"
fi
