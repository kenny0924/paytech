#!/usr/bin/env bash
# 演练冒烟：在宿主机以 test Profile 启动 V2 服务连演练库（rehearse.sh 起的 mor-mt1-mysql:23307），
# 环境变量与 test-environment/compose.applications.yaml 同名同义，只把地址换成本机演练资源。
#
#   rehearse-services.sh start [服务…]   默认顺序：vault platform compliance risk-decision client-master commercial consumer payment acquiring
#   rehearse-services.sh stop  [服务…]   只停本脚本记录 PID 的进程
#   rehearse-services.sh status
#
# 演练资源（全部一次性、与 mor-local / mor-test 隔离）：
#   MySQL 127.0.0.1:23307（mor-mt1-mysql）；Redis 127.0.0.1:26389（mor-mt1-redis，database 5）；
#   RabbitMQ 127.0.0.1:25682 vhost /mor-mt1（mor-mt1-rabbitmq，带一致性哈希插件）；不连 Nacos（发现名经 simple discovery 指向本机端口）。
# Vault 用 28197（mor-local 的 Vault 占用 18107）；其他服务用 V2 标准端口（业务 181xx、管理 191xx）。
# 口令只来自 MT_WORK_DIR 下的随机文件（rehearsal.env、v2-accounts.env、infra.env），经环境变量传入进程，不打印。
# 与 mor-test 的差异（演练限制，见报告）：Platform 开运营认证与租户投影，但用演练新生成的签名材料；不开 Client 认证 / 通知 / 文件 /
# Webhook / 补偿（需要 mor-test 私有材料或外部 SES / MinIO）；
# XXL 执行器关闭；internal-auth 全部 OFF（与 compose 相同）；Vault 用随机根密钥（迁移来的密文不可解，readiness 不依赖解密）。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
: "${MT_WORK_DIR:?set MT_WORK_DIR to the rehearsal directory used by rehearse.sh}"
: "${MT_VAULT_BIN:=$MT_WORK_DIR/../bin/vault-server}"
STATE="$MT_WORK_DIR/services"; LOGS="$MT_WORK_DIR/logs"
mkdir -p "$STATE" "$LOGS"
set -a; . "$MT_WORK_DIR/rehearsal.env"; . "$MT_WORK_DIR/v2-accounts.env"; . "$MT_WORK_DIR/infra.env"
# Platform 人工认证材料：演练用 test-environment/scripts/GenerateOperationsMaterial.java 新生成（与 mor-test 的不同，
# 故迁移来的 Operations 公钥不对应此私钥；只用于让 Platform IAM / ID 生成器装配，不做登录）。
. "$MT_WORK_DIR/platform-material.env"; set +a

JAVA="${MT_JAVA:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java}"  # JDK 25；不读 shell 的 JAVA_HOME
JVM=(-Xms64m -Xmx320m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=256m
  -Duser.timezone=UTC -Dfile.encoding=UTF-8)
JDBC="jdbc:mysql://127.0.0.1:23307"
JDBC_OPTS="useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&allowPublicKeyRetrieval=true&useSSL=false"
DEFAULT_ORDER=(vault platform compliance risk-decision client-master commercial consumer payment acquiring)

port_of() {
  case "$1" in
    client-master) echo 18101 ;; acquiring) echo 18102 ;; payment) echo 18103 ;; risk-decision) echo 18104 ;;
    platform) echo 18106 ;; vault) echo 28197 ;; commercial) echo 18108 ;; consumer) echo 18109 ;; compliance) echo 18112 ;;
  esac
}
health_of() {
  case "$1" in
    vault) echo "http://127.0.0.1:28197/health/readiness" ;;
    platform) echo "http://127.0.0.1:18106/platform-support/actuator/health" ;;
    *) echo "http://127.0.0.1:$(( $(port_of "$1") + 1000 ))/actuator/health" ;;
  esac
}
module_of() { case "$1" in platform) echo platform-support ;; *) echo "$1" ;; esac; }
jar_of() { ls "$REPO/paytech/$(module_of "$1")/paytech-$(module_of "$1")-app/target/"*-exec.jar | head -1; }

DISCOVERY=(--spring.cloud.nacos.discovery.enabled=false --spring.cloud.nacos.discovery.register-enabled=false)
for pair in vault-service:28197 platform-support-service:18106 client-master-service:18101 commercial-service:18108 \
            acquiring-service:18102 consumer-service:18109 risk-decision-service:18104 compliance-service:18112 payment-service:18103; do
  DISCOVERY+=("--spring.cloud.discovery.client.simple.instances.${pair%%:*}[0].uri=http://127.0.0.1:${pair#*:}")
done
COMMON=(--spring.profiles.active=test --server.address=127.0.0.1 --spring.data.redis.database=5 "${DISCOVERY[@]}")
export SPRING_RABBITMQ_HOST=127.0.0.1 SPRING_RABBITMQ_PORT=25682 SPRING_RABBITMQ_USERNAME=mt1 SPRING_RABBITMQ_PASSWORD="$MT1_RABBIT_PASSWORD" \
  SPRING_RABBITMQ_VIRTUAL_HOST=/mor-mt1 SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT=26389 SPRING_DATA_REDIS_PASSWORD="$MT1_REDIS_PASSWORD" TZ=UTC

running() { [[ -f "$STATE/$1.pid" ]] && kill -0 "$(cat "$STATE/$1.pid")" 2>/dev/null; }

launch() {
  local name="$1"; shift
  running "$name" && { echo "$name 已在运行"; return; }
  local port; port=$(port_of "$name")
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t > /dev/null 2>&1; then echo "停止：端口 $port 已被占用" >&2; return 1; fi
  date +%s > "$STATE/$name.started"
  nohup "$@" > "$LOGS/$name.log" 2>&1 &
  echo $! > "$STATE/$name.pid"
}

java_app() {
  local name="$1"; shift
  local management=(--management.server.address=127.0.0.1); [[ "$name" == platform ]] && management=()
  launch "$name" "$JAVA" "${JVM[@]}" -jar "$(jar_of "$name")" "${COMMON[@]}" ${management[@]+"${management[@]}"} "$@"
}

start_one() {
  case "$1" in
    vault)
      VAULT_RUNTIME_MODE=test VAULT_HTTP_ADDRESS=127.0.0.1:28197 VAULT_ADMIN_CALLERS=operations-console-bff \
      VAULT_DB_DSN="vault_app:${MOR_VAULT_DB_PASSWORD}@tcp(127.0.0.1:23307)/mor_vault?parseTime=true&loc=UTC" \
      VAULT_LOCAL_AES_ROOT_KEY="$MT1_VAULT_ROOT_KEY" VAULT_LOCAL_AES_KEY_ID=mt1-rehearsal-v1 \
      VAULT_SNOWFLAKE_DATACENTER_ID=0 VAULT_SNOWFLAKE_WORKER_ID=24 VAULT_NACOS_ENABLED=false \
        launch vault "$MT_VAULT_BIN" ;;
    platform)
      PLATFORM_SUPPORT_DB_URL="$JDBC/mor_platform?$JDBC_OPTS" PLATFORM_SUPPORT_DB_USERNAME=platform_app \
      PLATFORM_SUPPORT_DB_PASSWORD="$MOR_PLATFORM_DB_PASSWORD" PLATFORM_SUPPORT_REDIS_HOST=127.0.0.1 PLATFORM_SUPPORT_REDIS_PORT=26389 \
      PLATFORM_SUPPORT_REDIS_PASSWORD="$MT1_REDIS_PASSWORD" TENANT_PROJECTION_ENABLED=true \
      OPERATIONS_AUTHENTICATION_ENABLED=true OPERATIONS_AUTHENTICATION_WORKER_ID=20 \
        java_app platform ;;
    client-master)
      SPRING_DATASOURCE_URL="$JDBC/mor_client_master?$JDBC_OPTS" SPRING_DATASOURCE_USERNAME=client_master_app \
      SPRING_DATASOURCE_PASSWORD="$MOR_CLIENT_MASTER_DB_PASSWORD" CCT_RABBITMQ_HOST=127.0.0.1 CCT_RABBITMQ_PORT=25682 \
      CCT_RABBITMQ_USERNAME=mt1 CCT_RABBITMQ_PASSWORD="$MT1_RABBIT_PASSWORD" CCT_RABBITMQ_VHOST=/mor-mt1 \
      MESSAGING_CONSUMER_SERVICE=client-master MESSAGING_CONSUMERS_COMPLIANCE_PARTITIONS=4 \
      ONBOARDING_DRAFT_ENABLED=true ONBOARDING_RECOVERY_ENABLED=true ONBOARDING_ID_DATACENTER=0 ONBOARDING_ID_WORKER=26 \
      COMPENSATION_INSTANCE_ID=client-master-mt1 \
        java_app client-master ;;
    commercial)
      SPRING_DATASOURCE_URL="$JDBC/mor_commercial?$JDBC_OPTS" SPRING_DATASOURCE_USERNAME=commercial_app \
      SPRING_DATASOURCE_PASSWORD="$MOR_COMMERCIAL_DB_PASSWORD" \
        java_app commercial ;;
    consumer)
      MOR_CONSUMER_DB_URL="$JDBC/mor_consumer?$JDBC_OPTS" MOR_CONSUMER_DB_USERNAME=consumer_app \
      MOR_CONSUMER_DB_PASSWORD="$MOR_CONSUMER_DB_PASSWORD" CONSUMER_SNOWFLAKE_REGION_ID=0 CONSUMER_SNOWFLAKE_WORKER_ID=27 \
        java_app consumer ;;
    risk-decision)
      RISK_DB_URL="$JDBC/mor_risk_decision?$JDBC_OPTS" RISK_DB_USERNAME=risk_decision_app RISK_DB_PASSWORD="$MOR_RISK_DECISION_DB_PASSWORD" \
      RISK_REDIS_HOST=127.0.0.1 RISK_REDIS_PORT=26389 RISK_REDIS_PASSWORD="$MT1_REDIS_PASSWORD" \
      RISK_SNOWFLAKE_REGION_ID=0 RISK_SNOWFLAKE_WORKER_ID=25 RISK_DECISION_ENVIRONMENT=TEST \
        java_app risk-decision ;;
    compliance)
      COMPLIANCE_DB_URL="$JDBC/mor_compliance?$JDBC_OPTS" COMPLIANCE_DB_USERNAME=compliance_app \
      COMPLIANCE_DB_PASSWORD="$MOR_COMPLIANCE_DB_PASSWORD" COMPLIANCE_RABBITMQ_HOST=127.0.0.1 COMPLIANCE_RABBITMQ_PORT=25682 \
      COMPLIANCE_RABBITMQ_USERNAME=mt1 COMPLIANCE_RABBITMQ_PASSWORD="$MT1_RABBIT_PASSWORD" COMPLIANCE_RABBITMQ_VHOST=/mor-mt1 \
      COMPLIANCE_SNOWFLAKE_REGION_ID=0 COMPLIANCE_SNOWFLAKE_WORKER_ID=29 ONBOARDING_REVIEW_ENABLED=true ONBOARDING_MATERIALS_ENABLED=true \
      MESSAGING_PRODUCER_SERVICE=compliance MESSAGING_PRODUCER_SEQUENCER_ENABLED=true MESSAGING_PUBLISHER_UNROUTABLEPOLICY=FAIL \
        java_app compliance ;;
    payment)
      MOR_PAYMENT_DB_URL="$JDBC/mor_payment?$JDBC_OPTS" MOR_PAYMENT_DB_USERNAME=payment_app MOR_PAYMENT_DB_PASSWORD="$MOR_PAYMENT_DB_PASSWORD" \
      MOR_REDIS_HOST=127.0.0.1 MOR_REDIS_PORT=26389 MOR_REDIS_PASSWORD="$MT1_REDIS_PASSWORD" PAYMENT_RABBITMQ_HOST=127.0.0.1 \
      PAYMENT_RABBITMQ_PORT=25682 PAYMENT_RABBITMQ_USERNAME=mt1 PAYMENT_RABBITMQ_PASSWORD="$MT1_RABBIT_PASSWORD" PAYMENT_RABBITMQ_VHOST=/mor-mt1 \
      PAYMENT_SNOWFLAKE_REGION_ID=0 PAYMENT_SNOWFLAKE_WORKER_ID=21 PAYTECH_INSTANCE_ID=payment-mt1 \
        java_app payment ;;
    acquiring)
      MOR_COMMERCE_DB_URL="$JDBC/mor_acquiring?$JDBC_OPTS" MOR_COMMERCE_DB_USERNAME=acquiring_app \
      MOR_COMMERCE_DB_PASSWORD="$MOR_ACQUIRING_DB_PASSWORD" MOR_REDIS_HOST=127.0.0.1 MOR_REDIS_PORT=26389 MOR_REDIS_PASSWORD="$MT1_REDIS_PASSWORD" \
      COMMERCE_RABBITMQ_HOST=127.0.0.1 COMMERCE_RABBITMQ_PORT=25682 COMMERCE_RABBITMQ_USERNAME=mt1 \
      COMMERCE_RABBITMQ_PASSWORD="$MT1_RABBIT_PASSWORD" COMMERCE_RABBITMQ_VHOST=/mor-mt1 \
      MESSAGING_CONSUMER_SERVICE=acquiring MESSAGING_CONSUMERS_PAYMENT_PARTITIONS=4 COMMERCE_CARD_PAYMENT_METHOD_CODE=CARD \
      COMMERCE_CHECKOUT_PUBLIC_ORIGIN=https://localhost:28443 COMMERCE_SNOWFLAKE_REGION_ID=0 COMMERCE_SNOWFLAKE_WORKER_ID=28 \
      COMPENSATION_INSTANCEID=acquiring-mt1 XXL_JOB_EXECUTOR_ENABLED=false \
        java_app acquiring ;;
    *) echo "未知服务 $1" >&2; return 2 ;;
  esac
}

wait_ready() {
  local name="$1" url; url=$(health_of "$name")
  local deadline=$(( $(date +%s) + ${MT_START_TIMEOUT:-180} ))
  while (( $(date +%s) < deadline )); do
    running "$name" || { echo "失败：$name 已退出；日志 $LOGS/$name.log" >&2; tail -30 "$LOGS/$name.log" >&2; return 1; }
    [[ "$(curl -s -m 2 "$url" 2>/dev/null || true)" == *'"UP"'* ]] && { echo "$name UP（$(( $(date +%s) - $(cat "$STATE/$name.started") ))s）"; return 0; }
    sleep 2
  done
  echo "超时：$name 未 UP：$(curl -s -m 2 "$url" || true)" >&2; return 1
}

stop_one() {
  running "$1" || { rm -f "$STATE/$1.pid"; return 0; }
  local pid; pid=$(cat "$STATE/$1.pid"); kill "$pid"
  for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  kill -0 "$pid" 2>/dev/null && kill -9 "$pid"
  rm -f "$STATE/$1.pid"; echo "$1 已停止"
}

status_one() {
  local pid=- rss=- health
  running "$1" && { pid=$(cat "$STATE/$1.pid"); rss="$(( $(ps -o rss= -p "$pid" | tr -d ' ') / 1024 ))MB"; }
  health=$({ curl -s -m 2 "$(health_of "$1")" 2>/dev/null || true; } | python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("status","?"))
except Exception: print("DOWN")')
  printf '%-14s pid=%-7s port=%-6s health=%-5s rss=%s\n' "$1" "$pid" "$(port_of "$1")" "$health" "$rss"
}

command="${1:-}"; shift || true
targets=("$@"); [[ ${#targets[@]} -gt 0 ]] || targets=("${DEFAULT_ORDER[@]}")
case "$command" in
  start) for s in "${targets[@]}"; do start_one "$s"; wait_ready "$s"; done ;;
  stop) for (( i=${#targets[@]}-1; i>=0; i-- )); do stop_one "${targets[$i]}"; done ;;
  status) for s in "${targets[@]}"; do status_one "$s"; done ;;
  *) sed -n '2,16p' "$0"; exit 2 ;;
esac
