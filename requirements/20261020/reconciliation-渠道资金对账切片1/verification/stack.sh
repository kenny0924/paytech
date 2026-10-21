#!/usr/bin/env bash
# 对账切片 1 阶段 5 本机全链路的私有栈（不改动、不重启其他会话的进程）。
#
# 背景：本机 run-service.sh 栈（platform / channel-connector / ops-bff / gateway / nginx / ops-web）由其他会话启动且仍在运行，
# 其中 ops-bff（18201）是不含对账接口的旧 JAR。为不重启他人进程，本脚本另起一组与之并行的进程：
#   reconciliation   18115 / 19115   连 mor_reconciliation_v2e2e；RabbitMQ 独立临时 vhost /mor-recon5（避免运行中的 account
#                                     消费合成 BillingFactRecorded）；Nacos mor-local 注册（发现 channel-connector、vault-service）；XXL 关闭
#   ops-bff          18211 / 19211   当前源码构建；Nacos 只发现不注册（不进入共享 Gateway 的实例池）；对账基址 → 18115
#   gateway          18090 / 19090   关闭 Nacos，SimpleDiscovery 固定 platform-support-service → 18106、operations-console-bff → 18211
#   nginx            18454           OPERATIONS 站点：/api/ → 18090，/ → 共享 Vite（18301，读工作区源码）
# 共享（只读使用）：platform 18106、channel-connector 18110、Vault 容器、MySQL、Redis（database 3，Gateway 命名空间独立）、Vite 18301。
#
#   stack.sh start|start-recon|stop|status
#   start-recon：只启动 reconciliation（D-1 复测 perf_direct.py 直连 18115，不需要 Gateway / BFF / Nginx 与 IAM 合成人员）
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
LOCAL_ENV="$REPO/local-environment"
STATE="${RECON5_STATE:-/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/1a70f5c0-500a-4725-bdcb-fc5d7b9c803d/scratchpad/recon5}"
mkdir -p "$STATE/logs" "$STATE/jars" "$STATE/nginx/tmp"
set -a; . "$LOCAL_ENV/.env.local"; . "$LOCAL_ENV/e2e/secrets.env"; set +a

JAVA="/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java"
JVM=(-Xms128m -Xmx512m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=320m
  -Duser.timezone=UTC -Dfile.encoding=UTF-8)
VHOST=/mor-recon5
ORIGIN=https://127.0.0.1:18454
NACOS=(--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 --spring.cloud.nacos.discovery.namespace=mor-local
  "--spring.cloud.nacos.discovery.username=$E2E_NACOS_USERNAME" --spring.cloud.nacos.discovery.ip=127.0.0.1)
export SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD="$E2E_NACOS_PASSWORD" NACOS_NAMESPACE=mor-local NACOS_SERVER_ADDR=127.0.0.1:8848
export PAYTECH_INSTANCE_ID=recon5-local

recon_db_password() {
  if [[ -n "${MOR_RECONCILIATION_DB_PASSWORD:-}" ]]; then printf '%s' "$MOR_RECONCILIATION_DB_PASSWORD"; return; fi
  sed -nE "s/.*IDENTIFIED BY '([^']*)'.*/\1/p" "$REPO/paytech/reconciliation/paytech-reconciliation-test/src/test/resources/init/ddl.sql" | head -1
}

launch() {
  local name="$1"; shift
  if [[ -f "$STATE/$name.pid" ]] && kill -0 "$(cat "$STATE/$name.pid")" 2>/dev/null; then echo "$name 已在运行"; return; fi
  nohup "$@" > "$STATE/logs/$name.log" 2>&1 &
  echo $! > "$STATE/$name.pid"; echo "$name pid $!"
}

wait_up() {
  local name="$1" url="$2" deadline=$(( $(date +%s) + 180 ))
  while (( $(date +%s) < deadline )); do
    kill -0 "$(cat "$STATE/$name.pid")" 2>/dev/null || { echo "$name 已退出"; tail -40 "$STATE/logs/$name.log"; return 1; }
    [[ "$(curl -s -m 2 "$url" || true)" == *'"UP"'* ]] && { echo "$name UP"; return 0; }
    sleep 2
  done
  echo "$name 超时"; tail -40 "$STATE/logs/$name.log"; return 1
}

start_recon() {
  cp "$REPO/paytech/reconciliation/paytech-reconciliation-app/target/paytech-reconciliation-app-1.0.0-SNAPSHOT-exec.jar" "$STATE/jars/reconciliation.jar"

  RECONCILIATION_DB_URL="jdbc:mysql://127.0.0.1:13306/mor_reconciliation_v2e2e?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" \
  RECONCILIATION_DB_USERNAME=reconciliation_app RECONCILIATION_DB_PASSWORD="$(recon_db_password)" \
  RECONCILIATION_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" RECONCILIATION_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" \
  RECONCILIATION_RABBITMQ_VHOST="$VHOST" RECONCILIATION_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
  RECONCILIATION_SNOWFLAKE_REGION_ID=0 RECONCILIATION_SNOWFLAKE_WORKER_ID=14 \
  RECONCILIATION_COMPENSATION_INSTANCE_ID=reconciliation-recon5 \
    launch reconciliation "$JAVA" "${JVM[@]}" -jar "$STATE/jars/reconciliation.jar" "${NACOS[@]}" \
      --spring.data.redis.database=3 --xxl.job.executor.enabled=false
  wait_up reconciliation http://127.0.0.1:19115/actuator/health/readiness
}

start() {
  start_recon
  cp "$REPO/paytech/operations-console-bff/paytech-operations-console-bff-app/target/paytech-operations-console-bff-app-1.0.0-SNAPSHOT-exec.jar" "$STATE/jars/ops-bff.jar"
  cp "$LOCAL_ENV/e2e/jars/paytech-api-gateway-app-exec.jar" "$STATE/jars/gateway.jar"

  OPERATIONS_CONSOLE_ORIGIN="$ORIGIN" OPERATIONS_RECONCILIATION_BASE_URL=http://127.0.0.1:18115 \
    launch ops-bff "$JAVA" "${JVM[@]}" -jar "$STATE/jars/ops-bff.jar" "${NACOS[@]}" \
      --spring.cloud.nacos.discovery.register-enabled=false --server.port=18211 --management.server.port=19211
  wait_up ops-bff http://127.0.0.1:19211/actuator/health/readiness

  SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT=16379 SPRING_DATA_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
  GATEWAY_BIND_ADDRESS=127.0.0.1 OPERATIONS_CONSOLE_ORIGIN="$ORIGIN" \
  OPERATIONS_JWT_ISSUER=https://platform-support.local.test OPERATIONS_JWT_AUDIENCE=operations-console \
  OPERATIONS_GUARD_REDIS_NAMESPACE=local-recon5:api-gateway OPERATIONS_ACCESS_LOG_HMAC_VERSION=local-e2e-v1 \
  OPERATIONS_ACCESS_LOG_HMAC_MATERIAL="$E2E_GATEWAY_LOG_HMAC" GATEWAY_IDEMPOTENCY_REDIS_NAMESPACE=local-recon5:api-gateway-idem \
  GATEWAY_CLIENT_ENABLED=true GATEWAY_CLIENT_CONSOLE_ORIGIN=https://127.0.0.1:18443 \
  GATEWAY_CLIENT_SECURITY_ISSUER=https://customer-auth.local.test GATEWAY_CLIENT_SECURITY_AUDIENCE=client-console \
    launch gateway "$JAVA" "${JVM[@]}" -jar "$STATE/jars/gateway.jar" --spring.data.redis.database=3 \
      --server.port=18090 --management.server.port=19090 --management.server.address=127.0.0.1 \
      --spring.cloud.nacos.discovery.enabled=false \
      '--spring.cloud.discovery.client.simple.instances.platform-support-service[0].uri=http://127.0.0.1:18106' \
      '--spring.cloud.discovery.client.simple.instances.operations-console-bff[0].uri=http://127.0.0.1:18211'
  wait_up gateway http://127.0.0.1:19090/actuator/health/readiness

  cp "$LOCAL_ENV/e2e/nginx/local.crt" "$LOCAL_ENV/e2e/nginx/local.key" "$STATE/nginx/" 2>/dev/null || \
    /opt/homebrew/bin/openssl req -x509 -newkey rsa:2048 -nodes -days 30 -subj "/CN=127.0.0.1" -addext "subjectAltName=IP:127.0.0.1" \
      -keyout "$STATE/nginx/local.key" -out "$STATE/nginx/local.crt" 2>/dev/null
  cat > "$STATE/nginx/nginx.conf" <<EOF
worker_processes 1;
pid $STATE/nginx/nginx.pid;
error_log $STATE/logs/nginx-error.log warn;
events { worker_connections 256; }
http {
  access_log $STATE/logs/nginx-access.log;
  client_body_temp_path $STATE/nginx/tmp/body;
  proxy_temp_path $STATE/nginx/tmp/proxy;
  fastcgi_temp_path $STATE/nginx/tmp/fastcgi;
  uwsgi_temp_path $STATE/nginx/tmp/uwsgi;
  scgi_temp_path $STATE/nginx/tmp/scgi;
  client_max_body_size 1m;
  map \$http_upgrade \$connection_upgrade { default upgrade; '' close; }
  ssl_certificate $STATE/nginx/local.crt;
  ssl_certificate_key $STATE/nginx/local.key;
  server {
    listen 127.0.0.1:18454 ssl;
    location /api/ {
      proxy_pass http://127.0.0.1:18090;
      proxy_set_header X-Real-IP \$remote_addr;
      proxy_set_header X-Entry-Channel OPERATIONS;
      proxy_set_header Forwarded "";
      proxy_set_header X-Forwarded-For "";
      proxy_set_header X-Forwarded-Host "";
      proxy_set_header X-Forwarded-Proto "";
      proxy_set_header X-Forwarded-Port "";
    }
    location / {
      proxy_pass http://127.0.0.1:18301;
      proxy_http_version 1.1;
      proxy_set_header Upgrade \$http_upgrade;
      proxy_set_header Connection \$connection_upgrade;
    }
  }
}
EOF
  /opt/homebrew/bin/nginx -t -c "$STATE/nginx/nginx.conf" -p "$STATE/nginx" 2>&1 | tail -1
  /opt/homebrew/bin/nginx -c "$STATE/nginx/nginx.conf" -p "$STATE/nginx" && echo "nginx 18454 已启动"
}

stop() {
  [[ -f "$STATE/nginx/nginx.pid" ]] && kill -0 "$(cat "$STATE/nginx/nginx.pid")" 2>/dev/null && \
    /opt/homebrew/bin/nginx -c "$STATE/nginx/nginx.conf" -p "$STATE/nginx" -s quit && echo "nginx 已停止"
  for name in gateway ops-bff reconciliation; do
    [[ -f "$STATE/$name.pid" ]] || continue
    local pid; pid=$(cat "$STATE/$name.pid")
    if kill -0 "$pid" 2>/dev/null; then
      kill "$pid"; for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
      kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
      echo "$name 已停止"
    fi
    rm -f "$STATE/$name.pid"
  done
}

status() {
  for name in reconciliation ops-bff gateway; do
    local pid="-"; [[ -f "$STATE/$name.pid" ]] && pid=$(cat "$STATE/$name.pid")
    printf '%-15s pid=%-7s alive=%s\n' "$name" "$pid" "$(kill -0 "$pid" 2>/dev/null && echo yes || echo no)"
  done
}

case "${1:-}" in start) start ;; start-recon) start_recon ;; stop) stop ;; status) status ;; *) sed -n 2,14p "$0"; exit 2 ;; esac
