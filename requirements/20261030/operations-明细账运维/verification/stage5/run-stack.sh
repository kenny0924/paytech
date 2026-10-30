#!/usr/bin/env bash
# ======================================================================================================================
# 【恢复用副本 2026-10-30】明细账运维阶段 5：本机卡死重启后，各会话用 swap.py 捕获的 argv / env 已随 scratchpad 丢失，
# 以 requirements/20261021/platform-support-工作流/verification/local-e2e/run-service.sh 为底（下方原注释保留），按 10-17 至 10-30
# 各需求记录补齐此后各会话重启服务时加的参数，重建当前共享 e2e 栈的启动方式。原各 run-service.sh 副本不改。
# 运行 JAR 一律原样使用 local-environment/e2e/jars/*-exec.jar（组合构建，不重建、不替换）；PID / 日志 / 运行目录与原启动器共用。
#
# 相对底本的改动（参数 / 环境变量 → 来源记录）：
#   通用
#   - 进程以新会话脱离（perl POSIX::setsid + nohup），日志改为追加（>>），保留各会话的历史日志。
#   - nginx 模板改用 requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/nginx.conf.template（底本目录无模板；
#     生成结果与 e2e/nginx/nginx.conf 现有文件逐字相同）。
#   - all 不含 reconciliation（重启前未在栈中：无 pid / started / 日志），可单独 start reconciliation。
#   platform
#   - PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL + PLATFORM_SUPPORT_INTERNAL_AUTH_JWKS_URI（底本为 OFF）
#       ← 20261022/payment-3DS全链路/verification/stage5-report.md（SPRING_APPLICATION_JSON internal-auth DUAL + jwks-uri，等价改为环境变量）；
#         20261022/platform-support-文件多存储并存/reports/S2-A-report.md；20261017 run-service.sh 副本
#   - 去掉 MINIO_ACCESS / MINIO_SECRET（旧存储键已删除） ← 同上 S2-A-report.md
#   - --webhook.http.callers.acquiring-service 增加 order.expired ← 20261022/acquiring-订单过期收尾；20261017 run-service.sh 副本
#   - PLATFORM_SUPPORT_RABBITMQ_HOST/PORT/USERNAME/PASSWORD/VHOST（工作流事件） ← 20261028/platform-support-workflow按钮发起与数据验证/
#         verification/stage5-report.md、20261029/platform-support-联合查询/verification/stage5-backend-report.md（"含 SPRING_APPLICATION_JSON、RabbitMQ"）
#   - DATAQUERY_HTTP_CALLERS=operations-console-bff ← 20261029/platform-support-联合查询/verification/stage5-backend-report.md；
#         20261030/platform-support-多语言发布变更记录/delivery.md（"含 DATAQUERY_HTTP_CALLERS"）
#   - --notification.http.callers.payout-service=PAYOUT_BENEFICIARY_{VERIFIED,REJECTED,SUSPENDED,CLOSED}_EMAIL
#         ← 20261030/payout-收款账户与余额解释/delivery.md 第 18 行、release/compose-candidate.platform-support-service.yaml
#   commercial
#   - COMMERCIAL_SNOWFLAKE_REGION_ID=0、COMMERCIAL_SNOWFLAKE_WORKER_ID=7 ← 20261023/commercial-商业关系快照与真实解析接入/verification/local-e2e/stage5-report.md
#   card
#   - XXL 执行器（127.0.0.1:19991，调度中心为不可达占位，Token runtime/card3ds/xxl-token，日志 runtime/card3ds/xxl-card）、
#     JAVA_TOOL_OPTIONS 信任库 runtime/card3ds/card-truststore.p12 ← 20261022/payment-3DS全链路/verification/stage5-report.md、timeline.txt；
#         card.log 最后一次启动（10-29 07:41Z）执行器端口 19991
#   payment
#   - PAYMENT_CHANNEL_CALLBACK_BASE_URL=https://channel-callback.local.test ← 20261022/payment-3DS全链路/verification/stage5-report.md
#     （3DS 时的 XXL 19993 未保留：payment.log 自 10-22 起无执行器启动记录，19993 后来给了 tax）
#   acquiring
#   - XXL 执行器（127.0.0.1:19992，Token runtime/expiry/xxl-token，日志 runtime/expiry/xxl-acquiring）
#         ← 20261022/acquiring-订单过期收尾/verification/stage5-report.md；acquiring.log 最后一次启动端口 19992
#   - ACQUIRING_COMMERCIAL_RESOLUTION_MODE=REAL ← 20261029/tax-结账算税与事件登记/verification/stage5-report-backend.md（"保持 REAL"，之后无记录切回）
#   tax
#   - 全部参数 ← 20261029/tax-结账算税与事件登记/verification/stage5-report-backend.md 第 2 节：-Djavax.net.ssl.trustStore=runtime/tax/minio-truststore.p12、
#     TAX_PLATFORM_SERVICE_URL、TAX_RABBITMQ_*（/mor-e2e）、TAX_INTERNAL_AUTH_MODE=OFF、TAX_COMPENSATION_INSTANCE_ID=tax-local-e2e、
#     Snowflake Worker 14（底本为 15）、XXL 执行器 127.0.0.1:19993（appname tax-service，日志 runtime/tax/xxl-tax，占位调度中心）
#   payout（新服务，18117 / 19117）
#   - PAYOUT_DB_URL=mor_payout_v2e2e、PAYOUT_DB_USERNAME=payout_app、PAYOUT_DB_PASSWORD（.env.local MOR_PAYOUT_DB_PASSWORD）、
#     PAYOUT_SNOWFLAKE_REGION_ID=0 / WORKER_ID=17、PAYOUT_REDIS_*、PAYOUT_COMPENSATION_INSTANCE_ID=payout-local-e2e、Nacos 注册；
#     account 走 application.yml 默认 PAYOUT_ACCOUNT_SERVICE_URL=http://127.0.0.1:18114
#         ← 20261030/payout-收款账户与余额解释/delivery.md 第 18、21 行、verification/stage5-backend-report.md（Worker 号记录未写，取未占用的 17）
#   screening-mock（合成筛查，127.0.0.1:18191，channel-connector 渠道 S_SCREENING_MOCK 的出站地址）
#         ← 20261027/client-master-全球合规入驻与资格切片1/assets/harness/README.md、verification/infra-dev-report.md
#   未改：client-master / compliance / risk-decision / counterparty / channel-connector / billing / ledger / account / client-bff / ops-bff / gateway /
#     payermax-mock（3DS 扩展版已于验收后恢复原版）/ Web（主仓库 Vite 18301 / 18302）。其他会话的临时覆盖（健康明细、刷新周期、
#     RISK_LIMIT_TIER_MIGRATION_CALLERS 等）均已在原记录中撤回，不带。
# ======================================================================================================================
# 【副本】工作流阶段 5（requirements/20261021/platform-support-工作流）：自 channel-connector 统一对外渠道出口副本复制，只改 platform 的文件 / 补偿 / 数据查询参数。
# 原注释：【副本】统一对外渠道出口第一段阶段 5（requirements/20261020/channel-connector-统一对外渠道出口/tasks/P1-stage5.md C 节）：
# 自 requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/run-service.sh 复制，原脚本不改。目录深度相同，REPO 仍为主仓库，
# PID / 日志 / 运行 JAR 与原启动器共用（local-environment/e2e/）。只改两处：
#   1. platform：去掉 --ps.integration.execution.allowed-schemes（Platform 已删除外部 API 执行模块，该键不再存在）；
#   2. channel-connector：外部 API 执行迁入后以环境变量提供 channel-connector.protocol.execution.*（合成 PayerMax 为本机 http；
#      读卡 card-service 无 context path）。Vault 客户端沿用 application.yml 的 vault.client.*（caller-service=channel-connector-service），
#      card / vault 经同一 Nacos 命名空间发现。
# 阶段 5 本机全链路启动器（任务卡 14）：宿主机直接起 V2 全部服务，连 local-environment 的 MySQL / Redis / RabbitMQ / Nacos / MinIO / Vault 容器。
#
#   run-service.sh start  <服务|all|java|web>   后台启动；all = 18 个 Java 服务 + payermax-mock + nginx（逐个等待 Health UP）
#   run-service.sh stop   <服务|all>            只停止本启动器记录 PID 的进程（不会碰其他会话的进程）
#   run-service.sh status [服务|all]            PID、端口、Health、RSS
#   run-service.sh check                        启动前检查：端口空闲、库存在且账号可登录、vhost 存在、Vault 规则、Nacos 无同名实例
#   run-service.sh jars                         把 paytech/*/paytech-*-app/target/*-exec.jar 复制到运行目录（之后源码重建不影响运行中的进程）
#
# 服务名：platform client-master commercial compliance risk-decision counterparty card channel-connector payment acquiring
#         billing ledger account reconciliation tax client-bff ops-bff gateway data-service(不在 all 内，依赖 Doris)
#         payermax-mock nginx client-web ops-web vault(只查状态：沿用 local-environment 的 Vault 容器，不另起)
#
# 私密值只从主仓库 local-environment/.env.local 与 local-environment/e2e/secrets.env 读取（E2E_LOCAL_ENV 可改），经环境变量传给进程，不打印。
# 新库账号口令：.env.local 有 MOR_<X>_DB_PASSWORD 时用之，否则取该服务 *-test 的 init/ddl.sql 中仓库内隔离的本机默认值（与 auto 测试相同）。
# 运行状态：本 worktree 的 local-environment/e2e/（PID、nginx、运行文件、复制的 JAR）与 local-environment/logs/（日志），均被 .gitignore 忽略。
#
# 隔离（与 auto 测试和其他会话互不影响）：
#   - MySQL：*_v2e2e 专用库（sql/00-e2e-databases.sql）；auto 测试独占 mor_*
#   - RabbitMQ：vhost /mor-e2e（auto 测试会清空 /mor-local 的分区队列）；E2E_RABBITMQ_VHOST 可改
#   - Redis：database 3（auto 测试与旧全链路用 0）；E2E_REDIS_DB 可改
#   - Nacos：全部注册 mor-local（Vault 容器已注册在此）
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../../.." && pwd)"
LOCAL_ENV="${E2E_LOCAL_ENV:-/Users/kenny/Desktop/Workspace/Code-MoR/local-environment}"
WEB_ROOT="${E2E_WEB_ROOT:-$(dirname "$LOCAL_ENV")}"   # Web 依赖（node_modules）只装在主仓库
STATE="$REPO/local-environment/e2e"
LOGS="$REPO/local-environment/logs"
RUNTIME="$STATE/runtime"
JARS="$STATE/jars"
mkdir -p "$STATE" "$LOGS" "$RUNTIME" "$JARS"
chmod 700 "$STATE" "$RUNTIME"

set -a
. "$LOCAL_ENV/.env.local"
. "$LOCAL_ENV/e2e/secrets.env"
set +a

JAVA="/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java"
JVM=(-Xms128m -Xmx384m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:ReservedCodeCacheSize=64m -XX:MaxMetaspaceSize=320m
  -Duser.timezone=UTC -Dfile.encoding=UTF-8)
MYSQL_URL="jdbc:mysql://127.0.0.1:13306"
JDBC_OPTS="useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
VHOST="${E2E_RABBITMQ_VHOST:-/mor-e2e}"
REDIS_DB="${E2E_REDIS_DB:-3}"
CLIENT_ORIGIN="https://127.0.0.1:18443"
OPERATIONS_ORIGIN="https://127.0.0.1:18444"
CHECKOUT_ORIGIN="https://127.0.0.1:18446"
CLIENT_ISSUER="https://customer-auth.local.test"
OPERATIONS_ISSUER="https://platform-support.local.test"
PAYERMAX_MOCK_PORT="${E2E_PAYERMAX_MOCK_PORT:-18190}"

JAVA_SERVICES=(platform client-master commercial compliance risk-decision counterparty card channel-connector payment acquiring
  billing ledger account tax payout client-bff ops-bff gateway)
ALL=(screening-mock payermax-mock "${JAVA_SERVICES[@]}" nginx)
NGINX_TEMPLATE="$REPO/requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/nginx.conf.template"
XXL_PLACEHOLDER_ADMIN=http://127.0.0.1:1   # 本机无 XXL 调度中心：执行器只为手动 /run 触发补偿扫描

# 服务 → paytech 模块目录、业务端口、Health URL
module_of() {
  case "$1" in
    client-bff) echo client-console-bff ;; ops-bff) echo operations-console-bff ;; gateway) echo api-gateway ;;
    platform) echo platform-support ;; *) echo "$1" ;;
  esac
}
port_of() {
  case "$1" in
    client-master) echo 18101 ;; acquiring) echo 18102 ;; payment) echo 18103 ;; risk-decision) echo 18104 ;;
    ledger) echo 18105 ;; platform) echo 18106 ;; vault) echo 18107 ;; commercial) echo 18108 ;; counterparty) echo 18109 ;;
    channel-connector) echo 18110 ;; card) echo 18111 ;; compliance) echo 18112 ;; billing) echo 18113 ;;
    account) echo 18114 ;; reconciliation) echo 18115 ;; tax) echo 18116 ;; payout) echo 18117 ;; data-service) echo 18120 ;;
    gateway) echo 18080 ;; ops-bff) echo 18201 ;; client-bff) echo 18202 ;; payermax-mock) echo "$PAYERMAX_MOCK_PORT" ;; screening-mock) echo 18191 ;;
    client-web) echo 18302 ;; ops-web) echo 18301 ;; nginx) echo 18443 ;; *) return 1 ;;
  esac
}
health_of() {
  case "$1" in
    platform) echo "http://127.0.0.1:18106/platform-support/actuator/health/readiness" ;;
    vault) echo "http://127.0.0.1:18107/health/readiness" ;;
    payermax-mock|screening-mock|nginx|client-web|ops-web) echo "" ;;
    *) echo "http://127.0.0.1:$(( $(port_of "$1") + 1000 ))/actuator/health/readiness" ;;  # Gateway 的 /actuator/health 经身份验证过滤器返回 401
  esac
}

# 账号口令：.env.local 的键优先，否则取 *-test init/ddl.sql 的本机默认值。只经环境变量传递。
db_password() {
  local key="$1" module="$2"
  if [[ -n "${!key:-}" ]]; then printf '%s' "${!key}"; return; fi
  sed -nE "s/.*IDENTIFIED BY '([^']*)'.*/\1/p" "$REPO/paytech/$module/paytech-$module-test/src/test/resources/init/ddl.sql" | head -1
}
db_url() { echo "$MYSQL_URL/mor_${1//-/_}_v2e2e?$JDBC_OPTS"; }

# Nacos 注册（密码经环境变量 SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD）
NACOS=(--spring.cloud.nacos.discovery.server-addr=127.0.0.1:8848 --spring.cloud.nacos.discovery.namespace=mor-local
  "--spring.cloud.nacos.discovery.username=$E2E_NACOS_USERNAME" --spring.cloud.nacos.discovery.ip=127.0.0.1)
export SPRING_CLOUD_NACOS_DISCOVERY_PASSWORD="$E2E_NACOS_PASSWORD" NACOS_NAMESPACE=mor-local NACOS_SERVER_ADDR=127.0.0.1:8848
COMMON=(--server.address=127.0.0.1 "--spring.data.redis.database=$REDIS_DB"
  --spring.rabbitmq.host=127.0.0.1 --spring.rabbitmq.port=5672 "--spring.rabbitmq.virtual-host=$VHOST")
export PAYTECH_INSTANCE_ID=local-e2e
# 一致性修正：internal-auth 全部 OFF（本步不验证 D26 切换）；带 *_INTERNAL_AUTH_MODE 键的服务显式设置，其余在 application.yml 固定 "OFF"。
export ACQUIRING_INTERNAL_AUTH_MODE=OFF CARD_INTERNAL_AUTH_MODE=OFF CHANNEL_CONNECTOR_INTERNAL_AUTH_MODE=OFF \
  COMPLIANCE_INTERNAL_AUTH_MODE=OFF COUNTERPARTY_INTERNAL_AUTH_MODE=OFF PAYMENT_INTERNAL_AUTH_MODE=OFF \
  PLATFORM_SUPPORT_INTERNAL_AUTH_MODE=DUAL RISK_DECISION_INTERNAL_AUTH_MODE=OFF TAX_INTERNAL_AUTH_MODE=OFF
# Platform DUAL（10-22 文件多存储 / 3DS 阶段 5）：内部文件接口按 X-Caller-Service 取来源系统；JWKS 指向 Platform 自身
export PLATFORM_SUPPORT_INTERNAL_AUTH_JWKS_URI=http://127.0.0.1:18106/platform-support/platform/v1/internal-auth/jwks
# RabbitMQ 账号经 Spring 通用键注入（各服务自有前缀键默认值为空，二者都设）
export SPRING_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" SPRING_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD"
export SPRING_DATA_REDIS_HOST=127.0.0.1 SPRING_DATA_REDIS_PORT=16379 SPRING_DATA_REDIS_PASSWORD="$MOR_REDIS_PASSWORD"

pid_file() { echo "$STATE/$1.pid"; }
running() { local f; f=$(pid_file "$1"); [[ -f "$f" ]] && kill -0 "$(cat "$f")" 2>/dev/null; }
listening() { { lsof -nP -iTCP:"$1" -sTCP:LISTEN -t 2>/dev/null || true; } | head -1; }

jar_of() {
  local module; module=$(module_of "$1")
  local copied="$JARS/paytech-$module-app-exec.jar"
  if [[ -f "$copied" ]]; then echo "$copied"; return; fi
  ls "$REPO/paytech/$module/paytech-$module-app/target/"*-exec.jar | head -1
}

copy_jars() {
  for svc in "${JAVA_SERVICES[@]}" data-service; do
    local module; module=$(module_of "$svc")
    cp "$(ls "$REPO/paytech/$module/paytech-$module-app/target/"*-exec.jar | head -1)" "$JARS/paytech-$module-app-exec.jar"
  done
  ls -la "$JARS" | awk 'NR>1{print $5, $9}'
}

launch() {
  local name="$1"; shift
  if running "$name"; then echo "$name 已在运行 (pid $(cat "$(pid_file "$name")"))"; return 0; fi
  local port; port=$(port_of "$name" || true)
  if [[ -n "$port" && -n "$(listening "$port")" ]]; then
    echo "停止：$name 端口 $port 已被 pid $(listening "$port") 占用（不属于本启动器）" >&2; return 1
  fi
  date +%s > "$STATE/$name.started"
  # 新会话脱离（启动本脚本的终端 / 代理退出后进程继续运行）；日志追加
  nohup perl -MPOSIX -e 'POSIX::setsid(); exec @ARGV or die "exec: $!"' -- "$@" >> "$LOGS/$name.log" 2>&1 < /dev/null &
  echo $! > "$(pid_file "$name")"
  echo "$name 已启动 (pid $!)，日志 $LOGS/$name.log"
}

# Platform 的 Actuator 与业务共用 18106（无独立管理端口），不能设置 management.server.address。
java_app() {
  local name="$1"; shift
  local management=(--management.server.address=127.0.0.1); [[ "$name" == platform ]] && management=()
  local extra=(); [[ -n "${JVM_EXTRA:-}" ]] && extra=("$JVM_EXTRA")   # 只有 tax 用：服务端直传 MinIO 的信任库
  launch "$name" "$JAVA" "${JVM[@]}" ${extra[@]+"${extra[@]}"} -jar "$(jar_of "$name")" "${COMMON[@]}" ${management[@]+"${management[@]}"} "$@"
}

start_one() {
  local svc="$1"
  case "$svc" in
    platform)
      PLATFORM_SUPPORT_DB_URL="$(db_url platform)" PLATFORM_SUPPORT_DB_USERNAME=platform_app \
      PLATFORM_SUPPORT_DB_PASSWORD="$MOR_PLATFORM_DB_PASSWORD" PLATFORM_SUPPORT_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
      PLATFORM_SUPPORT_RABBITMQ_HOST=127.0.0.1 PLATFORM_SUPPORT_RABBITMQ_PORT=5672 PLATFORM_SUPPORT_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" \
      PLATFORM_SUPPORT_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" PLATFORM_SUPPORT_RABBITMQ_VHOST="$VHOST" \
      DATAQUERY_HTTP_CALLERS=operations-console-bff \
        java_app platform --spring.profiles.active=local "${NACOS[@]}" \
          --operations.authentication.enabled=true --operations.authentication.worker-id=1 \
          --client.authentication.enabled=true --client.authentication.worker-id=2 \
          "--client.authentication.issuer=$CLIENT_ISSUER" "--client.authentication.public-origin=$CLIENT_ORIGIN" \
          --notification.enabled=true --compensation.instance-id=platform-local-e2e --tenant.projection.enabled=true \
          --webhook.enabled=true --webhook.allowed-schemes=https,http --webhook.trusted-networks=127.0.0.1/32 \
          `# 第 03 步 Commerce 改名 acquiring-service；只放行已实现的 4 种业务事件（order.expired 见 20261022 订单过期收尾）` \
          --webhook.http.callers.acquiring-service=order.paid,order.cancelled,order.expired,refund.created \
          `# payout S1（20261030）：收款账户核验结果邮件四个模板` \
          --notification.http.callers.payout-service=PAYOUT_BENEFICIARY_VERIFIED_EMAIL,PAYOUT_BENEFICIARY_REJECTED_EMAIL,PAYOUT_BENEFICIARY_SUSPENDED_EMAIL,PAYOUT_BENEFICIARY_CLOSED_EMAIL \
          `# 工作流阶段 5：合并后的 Platform 已删除 files.onboarding 旧存储键（残留启动失败），e2e 库无文件存储与数据查询表，` \
          `# 入驻上传关闭、数据查询不定时刷新；补偿执行显式开启（工作流通知与回调、FILE_SCAN）；带文件验收开启 files.enabled（存储登记走运营存储管理）。` \
          --files.enabled=true --files.onboarding.enabled=false --compensation.enabled=true --data-query.datasource.refresh-enabled=false ;;
    client-master)
      SPRING_DATASOURCE_URL="$(db_url client-master)" SPRING_DATASOURCE_USERNAME=client_master_app \
      SPRING_DATASOURCE_PASSWORD="$(db_password MOR_CLIENT_MASTER_DB_PASSWORD client-master)" \
      CCT_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" CCT_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" CCT_RABBITMQ_VHOST="$VHOST" \
        java_app client-master "${NACOS[@]}" \
          --onboarding.draft-enabled=true --onboarding.recovery-enabled=true \
          --onboarding.id.datacenter=0 --onboarding.id.worker=3 --compensation.instance-id=client-master-local-e2e ;;
    commercial)
      SPRING_DATASOURCE_URL="$(db_url commercial)" SPRING_DATASOURCE_USERNAME=commercial_app \
      SPRING_DATASOURCE_PASSWORD="$(db_password MOR_COMMERCIAL_DB_PASSWORD commercial)" \
      COMMERCIAL_SNOWFLAKE_REGION_ID=0 COMMERCIAL_SNOWFLAKE_WORKER_ID=7 \
        java_app commercial "${NACOS[@]}" ;;
    compliance)
      COMPLIANCE_DB_URL="$(db_url compliance)" COMPLIANCE_DB_USERNAME=compliance_app \
      COMPLIANCE_DB_PASSWORD="$(db_password MOR_COMPLIANCE_DB_PASSWORD compliance)" \
      COMPLIANCE_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" COMPLIANCE_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" \
      COMPLIANCE_RABBITMQ_VHOST="$VHOST" COMPLIANCE_SNOWFLAKE_REGION_ID=0 COMPLIANCE_SNOWFLAKE_WORKER_ID=5 \
        java_app compliance "${NACOS[@]}" --onboarding.review.enabled=true --onboarding.materials.enabled=true \
          --compensation.instance-id=compliance-local-e2e ;;
    risk-decision)
      RISK_DB_URL="$(db_url risk-decision)" RISK_DB_USERNAME=risk_decision_app \
      RISK_DB_PASSWORD="$(db_password MOR_RISK_DECISION_DB_PASSWORD risk-decision)" RISK_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
      RISK_SNOWFLAKE_REGION_ID=0 RISK_SNOWFLAKE_WORKER_ID=4 RISK_DECISION_ENVIRONMENT=TEST \
        java_app risk-decision "${NACOS[@]}" --compensation.instance-id=risk-decision-local-e2e ;;
    counterparty)
      MOR_COUNTERPARTY_DB_URL="$(db_url counterparty)" MOR_COUNTERPARTY_DB_USERNAME=counterparty_app \
      MOR_COUNTERPARTY_DB_PASSWORD="$(db_password MOR_COUNTERPARTY_DB_PASSWORD counterparty)" \
      COUNTERPARTY_SNOWFLAKE_REGION_ID=0 COUNTERPARTY_SNOWFLAKE_WORKER_ID=10 \
        java_app counterparty "${NACOS[@]}" ;;
    card)
      [[ -f "$RUNTIME/card-fingerprint.key" ]] || (umask 077; /opt/homebrew/bin/openssl rand -base64 32 > "$RUNTIME/card-fingerprint.key")
      MOR_CARD_DB_URL="$(db_url card)" MOR_CARD_DB_USERNAME=card_app MOR_CARD_DB_PASSWORD="$(db_password MOR_CARD_DB_PASSWORD card)" \
      MOR_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" CARD_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" \
      CARD_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" CARD_RABBITMQ_VHOST="$VHOST" \
      CARD_FINGERPRINT_KEY="$(cat "$RUNTIME/card-fingerprint.key")" CARD_SNOWFLAKE_REGION_ID=0 CARD_SNOWFLAKE_WORKER_ID=9 \
      JAVA_TOOL_OPTIONS="-Djavax.net.ssl.trustStore=$RUNTIME/card3ds/card-truststore.p12" \
      XXL_JOB_EXECUTOR_ENABLED=true XXL_JOB_ADMIN_ADDRESSES="$XXL_PLACEHOLDER_ADMIN" \
      XXL_JOB_ADMIN_ACCESSTOKEN="$(tr -d '\n' < "$RUNTIME/card3ds/xxl-token")" XXL_JOB_EXECUTOR_APPNAME=card-service \
      XXL_JOB_EXECUTOR_IP=127.0.0.1 XXL_JOB_EXECUTOR_PORT=19991 XXL_JOB_EXECUTOR_LOGPATH="$RUNTIME/card3ds/xxl-card" \
      XXL_JOB_EXECUTOR_EXCLUDEDPACKAGES=org.springframework.,spring. \
        java_app card "${NACOS[@]}" --compensation.instance-id=card-local-e2e ;;
    channel-connector)
      # 2026-10-19 渠道成本迁入（requirements/20261019/billing-计费事实模型重构与渠道成本迁出 B-report 第 6 节第 9 项）：
      # Snowflake 必填（Worker 16 未被其他服务占用）、Redis 资源锁、补偿实例；XXL 执行器同 billing 本机不连调度中心，显式禁用。
      # 2026-10-20 统一对外出口第一段：外部 API 执行自 Platform 迁入；合成 PayerMax 在 http://127.0.0.1:18190（仅本机放开 http）
      # 2026-10-22 第二段：出站地址默认拦截回环与私网，本机放行 127.0.0.1/32（只放行合成 PayerMax 所在地址）
      MOR_CHANNEL_CONNECTOR_DB_URL="$(db_url channel-connector)" MOR_CHANNEL_CONNECTOR_DB_USERNAME=channel_connector_app \
      MOR_CHANNEL_CONNECTOR_DB_PASSWORD="$(db_password MOR_CHANNEL_CONNECTOR_DB_PASSWORD channel-connector)" \
      CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID=0 CHANNEL_CONNECTOR_SNOWFLAKE_WORKER_ID=16 \
      CHANNEL_CONNECTOR_REDIS_HOST=127.0.0.1 CHANNEL_CONNECTOR_REDIS_PORT=16379 CHANNEL_CONNECTOR_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
      CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID=channel-connector-local-e2e \
      CHANNELCONNECTOR_PROTOCOL_EXECUTION_ALLOWEDSCHEMES=https,http CHANNELCONNECTOR_PROTOCOL_EXECUTION_CARDCONTEXTPATH= \
      CHANNELCONNECTOR_PROTOCOL_EXECUTION_TRUSTEDNETWORKS=127.0.0.1/32 \
        java_app channel-connector "${NACOS[@]}" --xxl.job.executor.enabled=false ;;
    payment)
      MOR_PAYMENT_DB_URL="$(db_url payment)" MOR_PAYMENT_DB_USERNAME=payment_app MOR_PAYMENT_DB_PASSWORD="$MOR_PAYMENT_DB_PASSWORD" \
      MOR_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" PAYMENT_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" \
      PAYMENT_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" PAYMENT_RABBITMQ_VHOST="$VHOST" \
      PAYMENT_SNOWFLAKE_REGION_ID=0 PAYMENT_SNOWFLAKE_WORKER_ID=8 PAYMENT_INSTRUMENT_PREPARATION_WINDOW=PT30M \
      PAYMENT_CHANNEL_CALLBACK_BASE_URL=https://channel-callback.local.test \
        java_app payment "${NACOS[@]}" --compensation.instance-id=payment-local-e2e ;;
    acquiring)
      MOR_COMMERCE_DB_URL="$(db_url acquiring)" MOR_COMMERCE_DB_USERNAME=acquiring_app \
      MOR_COMMERCE_DB_PASSWORD="$(db_password MOR_ACQUIRING_DB_PASSWORD acquiring)" MOR_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
      COMMERCE_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" COMMERCE_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" \
      COMMERCE_RABBITMQ_VHOST="$VHOST" COMMERCE_CARD_PAYMENT_METHOD_CODE="${E2E_CARD_PAYMENT_METHOD_CODE:-CARD}" \
      COMMERCE_CHECKOUT_PUBLIC_ORIGIN="$CHECKOUT_ORIGIN" COMMERCE_SNOWFLAKE_REGION_ID=0 COMMERCE_SNOWFLAKE_WORKER_ID=6 \
      ACQUIRING_COMMERCIAL_RESOLUTION_MODE="${E2E_ACQUIRING_RESOLUTION_MODE:-REAL}" \
      XXL_JOB_EXECUTOR_ENABLED=true XXL_JOB_ADMIN_ADDRESSES="$XXL_PLACEHOLDER_ADMIN" \
      XXL_JOB_ADMIN_ACCESSTOKEN="$(tr -d '\n' < "$RUNTIME/expiry/xxl-token")" XXL_JOB_EXECUTOR_APPNAME=acquiring-service \
      XXL_JOB_EXECUTOR_IP=127.0.0.1 XXL_JOB_EXECUTOR_PORT=19992 XXL_JOB_EXECUTOR_LOGPATH="$RUNTIME/expiry/xxl-acquiring" \
      XXL_JOB_EXECUTOR_EXCLUDEDPACKAGES=org.springframework.,spring. \
        java_app acquiring "${NACOS[@]}" --compensation.instance-id=acquiring-local-e2e ;;
    billing)
      BILLING_DB_URL="$(db_url billing)" BILLING_DB_USERNAME=billing_app BILLING_DB_PASSWORD="$(db_password MOR_BILLING_DB_PASSWORD billing)" \
      BILLING_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" BILLING_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" \
      BILLING_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" BILLING_RABBITMQ_VHOST="$VHOST" \
      BILLING_SNOWFLAKE_REGION_ID=0 BILLING_SNOWFLAKE_WORKER_ID=11 BILLING_COMPENSATION_INSTANCE_ID=billing-local-e2e \
        java_app billing ;;
    ledger)
      LEDGER_DB_URL="$(db_url ledger)" LEDGER_DB_USERNAME=ledger_app LEDGER_DB_PASSWORD="$(db_password MOR_LEDGER_DB_PASSWORD ledger)" \
      LEDGER_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" LEDGER_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" \
      LEDGER_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" LEDGER_RABBITMQ_VHOST="$VHOST" \
      LEDGER_ACCOUNT_SERVICE_URL=http://127.0.0.1:18114 LEDGER_BILLING_SERVICE_URL=http://127.0.0.1:18113 \
      LEDGER_SNOWFLAKE_REGION_ID=0 LEDGER_SNOWFLAKE_WORKER_ID=12 \
        java_app ledger ;;
    account)
      ACCOUNT_DB_URL="$(db_url account)" ACCOUNT_DB_USERNAME=account_app ACCOUNT_DB_PASSWORD="$(db_password MOR_ACCOUNT_DB_PASSWORD account)" \
      ACCOUNT_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" ACCOUNT_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" \
      ACCOUNT_RABBITMQ_VHOST="$VHOST" ACCOUNT_LEDGER_SERVICE_URL=http://127.0.0.1:18105 \
      ACCOUNT_BILLING_SERVICE_URL=http://127.0.0.1:18113 ACCOUNT_SNOWFLAKE_REGION_ID=0 ACCOUNT_SNOWFLAKE_WORKER_ID=13 \
      ACCOUNT_COMPENSATION_INSTANCE_ID=account-local-e2e \
        java_app account ;;
    reconciliation)
      RECONCILIATION_DB_URL="$(db_url reconciliation)" RECONCILIATION_DB_USERNAME=reconciliation_app \
      RECONCILIATION_DB_PASSWORD="$(db_password MOR_RECONCILIATION_DB_PASSWORD reconciliation)" \
      RECONCILIATION_SNOWFLAKE_REGION_ID=0 RECONCILIATION_SNOWFLAKE_WORKER_ID=14 \
        java_app reconciliation ;;
    tax)
      TAX_DB_URL="$(db_url tax)" TAX_DB_USERNAME=tax_app TAX_DB_PASSWORD="$(db_password MOR_TAX_DB_PASSWORD tax)" \
      TAX_SNOWFLAKE_REGION_ID=0 TAX_SNOWFLAKE_WORKER_ID=14 \
      TAX_RABBITMQ_HOST=127.0.0.1 TAX_RABBITMQ_PORT=5672 TAX_RABBITMQ_USERNAME="$MOR_RABBITMQ_USER" \
      TAX_RABBITMQ_PASSWORD="$MOR_RABBITMQ_PASSWORD" TAX_RABBITMQ_VHOST="$VHOST" \
      TAX_PLATFORM_SERVICE_URL=http://127.0.0.1:18106/platform-support TAX_COMPENSATION_INSTANCE_ID=tax-local-e2e \
      XXL_JOB_EXECUTOR_ENABLED=true XXL_JOB_ADMIN_ADDRESSES="$XXL_PLACEHOLDER_ADMIN" \
      XXL_JOB_ADMIN_ACCESSTOKEN="$(tr -d '\n' < "$RUNTIME/expiry/xxl-token")" XXL_JOB_EXECUTOR_APPNAME=tax-service \
      XXL_JOB_EXECUTOR_IP=127.0.0.1 XXL_JOB_EXECUTOR_PORT=19993 XXL_JOB_EXECUTOR_LOGPATH="$RUNTIME/tax/xxl-tax" \
      XXL_JOB_EXECUTOR_EXCLUDEDPACKAGES=org.springframework.,spring. \
      JVM_EXTRA="-Djavax.net.ssl.trustStore=$RUNTIME/tax/minio-truststore.p12" \
        java_app tax ;;
    payout)
      PAYOUT_DB_URL="$(db_url payout)" PAYOUT_DB_USERNAME=payout_app PAYOUT_DB_PASSWORD="$MOR_PAYOUT_DB_PASSWORD" \
      PAYOUT_SNOWFLAKE_REGION_ID=0 PAYOUT_SNOWFLAKE_WORKER_ID=17 \
      PAYOUT_REDIS_HOST=127.0.0.1 PAYOUT_REDIS_PORT=16379 PAYOUT_REDIS_PASSWORD="$MOR_REDIS_PASSWORD" \
      PAYOUT_COMPENSATION_INSTANCE_ID=payout-local-e2e \
        java_app payout "${NACOS[@]}" ;;
    screening-mock)
      local sdir="$RUNTIME/screening-mock"; mkdir -p "$sdir"; echo 18191 > "$sdir/port"
      launch screening-mock python3 -I "$REPO/requirements/20261027/client-master-全球合规入驻与资格切片1/assets/harness/screening_mock_server.py" \
        --host 127.0.0.1 --port 18191 --journal "$sdir/journal.jsonl"
      cp "$(pid_file screening-mock)" "$sdir/mock.pid" ;;
    data-service)
      java_app data-service ;;
    client-bff)
      java_app client-bff "${NACOS[@]}" --client.onboarding-enabled=true "--client.console-origin=$CLIENT_ORIGIN" ;;
    ops-bff)
      OPERATIONS_CONSOLE_ORIGIN="$OPERATIONS_ORIGIN" java_app ops-bff "${NACOS[@]}" ;;
    gateway)
      GATEWAY_BIND_ADDRESS=127.0.0.1 OPERATIONS_CONSOLE_ORIGIN="$OPERATIONS_ORIGIN" \
      OPERATIONS_JWT_ISSUER="$OPERATIONS_ISSUER" OPERATIONS_JWT_AUDIENCE=operations-console \
      OPERATIONS_GUARD_REDIS_NAMESPACE=local-v2e2e:api-gateway OPERATIONS_ACCESS_LOG_HMAC_VERSION=local-e2e-v1 \
      OPERATIONS_ACCESS_LOG_HMAC_MATERIAL="$E2E_GATEWAY_LOG_HMAC" GATEWAY_IDEMPOTENCY_REDIS_NAMESPACE=local-v2e2e:api-gateway-idem \
      GATEWAY_CLIENT_ENABLED=true GATEWAY_CLIENT_CONSOLE_ORIGIN="$CLIENT_ORIGIN" \
      GATEWAY_CLIENT_SECURITY_ISSUER="$CLIENT_ISSUER" GATEWAY_CLIENT_SECURITY_AUDIENCE=client-console \
        java_app gateway "${NACOS[@]}" ;;
    payermax-mock)
      # 合成 PayerMax（requirements/20261014/platform-support-PayerMax接入/assets/harness/PayerMaxMock.java）：只依赖 Jackson 3，
      # 类路径取 platform exec JAR 内同版本的 Jackson 在本地仓库 ~/.m3 中的副本；端口固定写入运行文件，重启沿用合成密钥。
      local dir="$RUNTIME/payermax"; mkdir -p "$dir"; chmod 700 "$dir"
      [[ -f "$dir/payermax-mock.json" ]] || echo "{\"port\":$PAYERMAX_MOCK_PORT}" > "$dir/payermax-mock.json"
      local cp; cp=$(jackson_classpath)
      E2E_RUNTIME="$dir" launch payermax-mock "$JAVA" -Xmx128m --class-path "$cp" \
        "$REPO/requirements/20261014/platform-support-PayerMax接入/assets/harness/PayerMaxMock.java" ;;
    nginx)
      if [[ -f "$STATE/nginx/nginx.pid" ]] && kill -0 "$(cat "$STATE/nginx/nginx.pid")" 2>/dev/null; then echo "nginx 已在运行"; return 0; fi
      for p in 18443 18444 18445 18446; do
        [[ -z "$(listening $p)" ]] || { echo "停止：nginx 端口 $p 已被 pid $(listening $p) 占用" >&2; return 1; }
      done
      mkdir -p "$STATE/nginx/tmp"
      if [[ ! -f "$STATE/nginx/local.crt" ]]; then
        /opt/homebrew/bin/openssl req -x509 -newkey rsa:2048 -nodes -days 365 -subj "/CN=127.0.0.1" \
          -addext "subjectAltName=IP:127.0.0.1" -keyout "$STATE/nginx/local.key" -out "$STATE/nginx/local.crt" 2>/dev/null
        chmod 600 "$STATE/nginx/local.key"
      fi
      sed -e "s#__STATE__#$STATE#g" -e "s#__LOGS__#$LOGS#g" "$NGINX_TEMPLATE" > "$STATE/nginx/nginx.conf"
      /opt/homebrew/bin/nginx -t -c "$STATE/nginx/nginx.conf" -p "$STATE/nginx" 2>&1 | tail -1
      /opt/homebrew/bin/nginx -c "$STATE/nginx/nginx.conf" -p "$STATE/nginx" && echo "nginx 已启动（18443 CLIENT / 18444 OPERATIONS / 18445 CLIENT_API / 18446 CHECKOUT）" ;;
    client-web)
      (cd "$WEB_ROOT/client-console-web" && launch client-web npx vite --host 127.0.0.1 --port 18302 --strictPort) ;;
    ops-web)
      (cd "$WEB_ROOT/operations-console-web" && launch ops-web npx vite --host 127.0.0.1 --port 18301 --strictPort) ;;
    vault)
      echo "vault 沿用 local-environment 容器 mor-local-vault-service-1（不由本启动器启动）" ;;
    *) echo "未知服务: $svc" >&2; return 2 ;;
  esac
}

jackson_classpath() {
  local jar; jar=$(jar_of platform)
  local names; names=$(unzip -Z1 "$jar" | sed -nE 's#^BOOT-INF/lib/(jackson-(core|databind|annotations)-[0-9][^/]*\.jar)$#\1#p')
  local out=()
  for n in $names; do
    local found; found=$(find "$HOME/.m3/repository/tools/jackson" "$HOME/.m3/repository/com/fasterxml/jackson/core" -name "$n" 2>/dev/null | head -1)
    [[ -n "$found" ]] || { echo "缺少 ${n}（~/.m3）" >&2; return 1; }
    out+=("$found")
  done
  local IFS=:; echo "${out[*]}"
}

# 等待 Health UP（Java 服务）或端口监听（其他）；超时打印日志尾部
wait_ready() {
  local svc="$1" url; url=$(health_of "$svc")
  local deadline=$(( $(date +%s) + ${E2E_START_TIMEOUT:-180} ))
  while (( $(date +%s) < deadline )); do
    if ! running "$svc" && [[ "$svc" != nginx ]]; then echo "失败：$svc 进程已退出，日志尾部：" >&2; tail -40 "$LOGS/$svc.log" >&2; return 1; fi
    if [[ -n "$url" ]]; then
      [[ "$(curl -s -m 2 "$url" 2>/dev/null || true)" == *'"UP"'* ]] && break
    else
      [[ -n "$(listening "$(port_of "$svc")")" ]] && break
    fi
    sleep 2
  done
  if (( $(date +%s) >= deadline )); then echo "超时：$svc 未就绪" >&2; tail -40 "$LOGS/$svc.log" >&2; return 1; fi
  local started; started=$(cat "$STATE/$svc.started" 2>/dev/null || date +%s)
  echo "$svc 就绪（$(( $(date +%s) - started ))s）"
}

stop_one() {
  local svc="$1"
  if [[ "$svc" == nginx ]]; then
    if [[ -f "$STATE/nginx/nginx.pid" ]] && kill -0 "$(cat "$STATE/nginx/nginx.pid")" 2>/dev/null; then
      /opt/homebrew/bin/nginx -c "$STATE/nginx/nginx.conf" -p "$STATE/nginx" -s quit && echo "nginx 已停止"
    fi
    return 0
  fi
  running "$svc" || { rm -f "$(pid_file "$svc")"; return 0; }
  local pid; pid=$(cat "$(pid_file "$svc")")
  kill "$pid"
  for _ in $(seq 1 30); do kill -0 "$pid" 2>/dev/null || break; sleep 1; done
  kill -0 "$pid" 2>/dev/null && { echo "$svc 30s 未退出，发送 KILL"; kill -9 "$pid"; }
  rm -f "$(pid_file "$svc")"
  echo "$svc 已停止"
}

status_one() {
  local svc="$1" pid="-" rss="-" health="-" port; port=$(port_of "$svc" || echo "-")
  if [[ "$svc" == nginx ]]; then
    [[ -f "$STATE/nginx/nginx.pid" ]] && pid=$(cat "$STATE/nginx/nginx.pid")
  elif [[ "$svc" == vault ]]; then
    pid="container"
  elif running "$svc"; then
    pid=$(cat "$(pid_file "$svc")"); rss=$(( $(ps -o rss= -p "$pid" | tr -d ' ') / 1024 ))MB
  fi
  local url; url=$(health_of "$svc")
  if [[ -n "$url" ]]; then
    health=$({ curl -s -m 2 "$url" 2>/dev/null || true; } | python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("status","?"))
except Exception: print("DOWN")')
  elif [[ "$port" != "-" && -n "$(listening "$port")" ]]; then
    health="LISTEN"
  fi
  printf '%-18s pid=%-8s port=%-6s health=%-6s rss=%s\n' "$svc" "$pid" "$port" "$health" "$rss"
}

check() {
  local bad=0
  for svc in "${ALL[@]}"; do
    local p; p=$(port_of "$svc")
    local holder; holder=$(listening "$p")
    if [[ -n "$holder" ]] && ! running "$svc"; then echo "端口占用：$svc $p pid=$holder"; bad=1; fi
  done
  for p in 18444 18445 18446 19080 19101 19102 19103 19104 19105 19108 19109 19110 19111 19112 19113 19114 19115 19116 19201 19202; do
    local holder; holder=$(listening "$p")
    if [[ -n "$holder" ]] && ! grep -qx "$holder" "$STATE"/*.pid 2>/dev/null; then echo "端口占用：$p pid=$holder"; bad=1; fi
  done
  check_db platform_app "$MOR_PLATFORM_DB_PASSWORD" platform || bad=1
  check_db payment_app "$MOR_PAYMENT_DB_PASSWORD" payment || bad=1
  for pair in client-master:client_master_app:MOR_CLIENT_MASTER_DB_PASSWORD commercial:commercial_app:MOR_COMMERCIAL_DB_PASSWORD \
      compliance:compliance_app:MOR_COMPLIANCE_DB_PASSWORD risk-decision:risk_decision_app:MOR_RISK_DECISION_DB_PASSWORD \
      acquiring:acquiring_app:MOR_ACQUIRING_DB_PASSWORD counterparty:counterparty_app:MOR_COUNTERPARTY_DB_PASSWORD card:card_app:MOR_CARD_DB_PASSWORD \
      channel-connector:channel_connector_app:MOR_CHANNEL_CONNECTOR_DB_PASSWORD billing:billing_app:MOR_BILLING_DB_PASSWORD \
      ledger:ledger_app:MOR_LEDGER_DB_PASSWORD account:account_app:MOR_ACCOUNT_DB_PASSWORD \
      reconciliation:reconciliation_app:MOR_RECONCILIATION_DB_PASSWORD tax:tax_app:MOR_TAX_DB_PASSWORD; do
    IFS=: read -r svc user key <<< "$pair"
    check_db "$user" "$(db_password "$key" "$svc")" "$svc" || bad=1
  done
  local rules; rules=$(MYSQL_PWD="$MOR_VAULT_DB_PASSWORD" mysql -h127.0.0.1 -P13306 -uvault_app -N -B -e \
    "SELECT COUNT(*) FROM mor_vault.t_vault_access_rule WHERE status='ACTIVE' AND caller_service IN ('client-master-service','card-service','channel-connector-service')" 2>/dev/null || echo "?")
  echo "Vault V2 调用方规则：$rules / 7"; [[ "$rules" == 7 ]] || bad=1
  local vh; vh=$(curl -s -m 3 -u "$MOR_RABBITMQ_USER:$MOR_RABBITMQ_PASSWORD" "http://127.0.0.1:15672/api/vhosts/${VHOST//\//%2F}" -o /dev/null -w '%{http_code}')
  echo "RabbitMQ vhost ${VHOST}：HTTP $vh"; [[ "$vh" == 200 ]] || bad=1
  echo "Vault 容器 readiness：$(curl -s -m 3 http://127.0.0.1:18107/health/readiness)"
  echo "Nacos mor-local 现有服务：$(nacos_services)"
  return $bad
}

check_db() {
  local user="$1" pass="$2" svc="$3" db; db="mor_${3//-/_}_v2e2e"
  local n; n=$(MYSQL_PWD="$pass" mysql -h127.0.0.1 -P13306 -u"$user" -N -B -e \
    "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db'" 2>/dev/null) || { echo "库检查失败：${db}（$user 无法登录或无权限）"; return 1; }
  echo "库 ${db}：$n 张表"; [[ "$n" -gt 0 ]]
}

nacos_services() {
  local token; token=$(curl -s -m 5 -X POST http://127.0.0.1:8848/nacos/v1/auth/login \
    --data-urlencode "username=$E2E_NACOS_USERNAME" --data-urlencode "password=$E2E_NACOS_PASSWORD" |
    python3 -c 'import sys,json;print(json.load(sys.stdin).get("accessToken",""))')
  curl -s -m 5 "http://127.0.0.1:8848/nacos/v1/ns/service/list?pageNo=1&pageSize=200&namespaceId=mor-local&accessToken=$token" |
    python3 -c 'import sys,json;print(",".join(sorted(json.load(sys.stdin).get("doms",[]))))'
}

expand() {
  case "$1" in
    all) printf '%s\n' "${ALL[@]}" ;; java) printf '%s\n' "${JAVA_SERVICES[@]}" ;; web) printf '%s\n' client-web ops-web ;;
    *) echo "$1" ;;
  esac
}

cmd="${1:-}"; target="${2:-all}"
case "$cmd" in
  start)
    for svc in $(expand "$target"); do start_one "$svc"; wait_ready "$svc"; done ;;
  stop)
    targets=$(expand "$target")
    [[ "$target" == all ]] && targets="$(printf '%s\n' client-web ops-web data-service; expand all)"
    for svc in $(echo "$targets" | tail -r); do stop_one "$svc"; done ;;
  status)
    for svc in $( [[ "$target" == all ]] && { expand all; echo vault; } || expand "$target"); do status_one "$svc"; done ;;
  check) check ;;
  jars) copy_jars ;;
  *) sed -n '2,8p' "$0"; exit 2 ;;
esac
