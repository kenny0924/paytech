#!/usr/bin/env bash
# 本机全链路：按名称启动单个服务进程（后台），日志与 PID 写入 local-environment/e2e/。
# 用法：run-service.sh vault|platform|cct|risk|client-bff|ops-bff|gateway|client-web|ops-web|nginx
# 私密值只从 local-environment/.env.local 与 local-environment/e2e/secrets.env 读取，不打印。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../../.." && pwd)"
STATE="$ROOT/local-environment/e2e"
LOGS="$STATE/logs"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
mkdir -p "$LOGS"
set -a
. "$ROOT/local-environment/.env.local"
. "$STATE/secrets.env"
set +a
JAVA="/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java"
CLIENT_ORIGIN="https://127.0.0.1:18443"
OPERATIONS_ORIGIN="https://127.0.0.1:18444"
CLIENT_ISSUER="https://customer-auth.local.test"
OPERATIONS_ISSUER="https://platform-support.local.test"
NACOS=(--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 --spring.cloud.nacos.discovery.namespace=mor-local
  --spring.cloud.nacos.discovery.username="$E2E_NACOS_USERNAME" --spring.cloud.nacos.discovery.ip=127.0.0.1)
# 密码经环境变量传入 Spring，避免出现在进程参数中。
export SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD="$E2E_NACOS_PASSWORD"
# Platform 使用独立库 mor_platform_e2e：mor_platform 留给 auto 测试（测试要求其独占表为空）。
# 2026-10-15 起认证邮件经 Delivery 发送（SMTP 已移除），开启 Client 认证须同时开启 notification.enabled；本机未配置 SES，认证邮件发送失败只计指标。
MYSQL="jdbc:mysql://127.0.0.1:13306"

launch() {
  local name="$1"; shift
  if [[ -f "$STATE/$name.pid" ]] && kill -0 "$(cat "$STATE/$name.pid")" 2>/dev/null; then
    echo "$name 已在运行 (pid $(cat "$STATE/$name.pid"))"; return
  fi
  nohup "$@" > "$LOGS/$name.log" 2>&1 &
  echo $! > "$STATE/$name.pid"
  echo "$name 已启动 (pid $!)，日志 $LOGS/$name.log"
}

jar() { ls "$ROOT/$1"/target/*-exec.jar | head -1; }

case "${1:-}" in
  vault)
    VAULT_RUNTIME_MODE=local VAULT_HTTP_ADDRESS=127.0.0.1:18107 \
    VAULT_DB_DSN="vault_app:${MOR_VAULT_DB_PASSWORD}@tcp(127.0.0.1:13306)/mor_vault?parseTime=true&loc=UTC" \
    VAULT_POLICY_FILE="$ROOT/local-environment/vault/policy.local.json" \
    VAULT_SNOWFLAKE_DATACENTER_ID=0 VAULT_SNOWFLAKE_WORKER_ID=7 \
    VAULT_NACOS_ENABLED=true VAULT_NACOS_SERVER_ADDRESS=127.0.0.1:8848 VAULT_NACOS_NAMESPACE=mor-local \
    VAULT_NACOS_GROUP=DEFAULT_GROUP VAULT_NACOS_INSTANCE_IP=127.0.0.1 VAULT_NACOS_INSTANCE_PORT=18107 \
    VAULT_NACOS_USERNAME="$E2E_NACOS_USERNAME" VAULT_NACOS_PASSWORD="$E2E_NACOS_PASSWORD" \
      launch vault "$ROOT/local-environment/vault/bin/vault-server" ;;
  platform)
    PLATFORM_SUPPORT_DB_URL="$MYSQL/mor_platform_e2e" PLATFORM_SUPPORT_DB_USERNAME=platform_app \
    PLATFORM_SUPPORT_DB_PASSWORD="$MOR_PLATFORM_DB_PASSWORD" PLATFORM_SUPPORT_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
    MINIO_ACCESS="$MOR_MINIO_PLATFORM_ACCESS_KEY" MINIO_SECRET="$MOR_MINIO_PLATFORM_SECRET_KEY" \
      launch platform "$JAVA" -jar "$(jar platform-support-service/platform-support-web)" \
        --spring.profiles.active=local "${NACOS[@]}" \
        --operations.authentication.enabled=true --operations.authentication.worker-id=1 \
        --client.authentication.enabled=true --client.authentication.worker-id=2 \
        --client.authentication.issuer="$CLIENT_ISSUER" --client.authentication.public-origin="$CLIENT_ORIGIN" \
        --notification.enabled=true \
        --compensation.instance-id=platform-local-e2e --tenant.projection.enabled=true \
        --files.onboarding.enabled=true --files.onboarding.storage-provider=MINIO \
        --files.onboarding.storage-container-ref=mor-platform-files --files.onboarding.scan-policy-code=CLIENT_ONBOARDING_FORMAT \
        --files.onboarding.minio.endpoint=https://127.0.0.1:19400 --files.onboarding.minio.region=us-east-1 \
        '--files.onboarding.minio.access-key=${MINIO_ACCESS}' '--files.onboarding.minio.secret-key=${MINIO_SECRET}' \
        --files.onboarding.minio.trusted-certificate="$ROOT/local-environment/minio/certs/public.crt" ;;
  cct)
    SPRING_DATASOURCE_PASSWORD="$MOR_CCT_DB_PASSWORD" SPRING_DATA_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
    CCT_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" CCT_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" \
      launch cct "$JAVA" -jar "$(jar client-contract-service/client-contract-web)" "${NACOS[@]}" \
        --spring.datasource.url="$MYSQL/mor_cct" --spring.datasource.username=cct_app \
        --spring.data.redis.host=127.0.0.1 --spring.data.redis.port=16379 \
        --onboarding.draft-enabled=true --onboarding.recovery-enabled=true \
        --onboarding.id.datacenter=0 --onboarding.id.worker=3 \
        --compensation.instance-id=cct-local-e2e --vault.client.caller-service=client-contract-service ;;
  risk)
    RISK_DB_URL="$MYSQL/mor_risk" RISK_DB_USERNAME=risk_app RISK_DB_PASSWORD="$MOR_RISK_DB_PASSWORD" \
    RISK_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" RISK_SNOWFLAKE_REGION_ID=0 RISK_SNOWFLAKE_WORKER_ID=4 \
    RISK_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" RISK_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" \
      launch risk "$JAVA" -jar "$(jar risk-compliance-service/risk-web)" "${NACOS[@]}" \
        --onboarding.review.enabled=true --onboarding.materials.enabled=true \
        --compensation.instance-id=risk-local-e2e ;;
  client-bff)
    launch client-bff "$JAVA" -jar "$(jar client-console-bff/paytech-client-console-bff-web)" "${NACOS[@]}" \
      --client.onboarding-enabled=true --client.console-origin="$CLIENT_ORIGIN" ;;
  ops-bff)
    OPERATIONS_CONSOLE_ORIGIN="$OPERATIONS_ORIGIN" \
      launch ops-bff "$JAVA" -jar "$(jar operations-console-bff/paytech-operations-console-bff-web)" "${NACOS[@]}" ;;
  gateway)
    SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT=16379 SPRING_DATA_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
    GATEWAY_BIND_ADDRESS=127.0.0.1 NACOS_NAMESPACE=mor-local OPERATIONS_CONSOLE_ORIGIN="$OPERATIONS_ORIGIN" \
    OPERATIONS_JWT_ISSUER="$OPERATIONS_ISSUER" OPERATIONS_JWT_AUDIENCE=operations-console \
    OPERATIONS_GUARD_REDIS_NAMESPACE=local-e2e:api-gateway OPERATIONS_ACCESS_LOG_HMAC_VERSION=local-e2e-v1 \
    OPERATIONS_ACCESS_LOG_HMAC_MATERIAL="$E2E_GATEWAY_LOG_HMAC" \
    GATEWAY_CLIENT_ENABLED=true GATEWAY_CLIENT_CONSOLE_ORIGIN="$CLIENT_ORIGIN" \
    GATEWAY_CLIENT_SECURITY_ISSUER="$CLIENT_ISSUER" GATEWAY_CLIENT_SECURITY_AUDIENCE=client-console \
      launch gateway "$JAVA" -jar "$(jar api-gateway/api-gateway-web)" "${NACOS[@]}" ;;
  client-web)
    (cd "$ROOT/client-console-web" && launch client-web npx vite --host 127.0.0.1 --port 18302 --strictPort) ;;
  ops-web)
    (cd "$ROOT/operations-console-web" && launch ops-web npx vite --host 127.0.0.1 --port 18301 --strictPort) ;;
  nginx)
    mkdir -p "$STATE/nginx/tmp"
    if [[ ! -f "$STATE/nginx/local.crt" ]]; then
      /opt/homebrew/bin/openssl req -x509 -newkey rsa:2048 -nodes -days 365 -subj "/CN=127.0.0.1" \
        -addext "subjectAltName=IP:127.0.0.1" -keyout "$STATE/nginx/local.key" -out "$STATE/nginx/local.crt" 2>/dev/null
      chmod 600 "$STATE/nginx/local.key"
    fi
    sed "s#__STATE__#$STATE#g" "$HERE/nginx.conf.template" > "$STATE/nginx/nginx.conf"
    /opt/homebrew/bin/nginx -t -c "$STATE/nginx/nginx.conf" -p "$STATE/nginx" 2>&1 | tail -1
    /opt/homebrew/bin/nginx -c "$STATE/nginx/nginx.conf" -p "$STATE/nginx" && echo "nginx 已启动" ;;
  *)
    echo "未知服务: ${1:-}" >&2; exit 2 ;;
esac
