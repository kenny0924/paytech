#!/usr/bin/env bash
# 本机迁移演练：一次性 MySQL 8.4.11 容器（与 mor-test 同一镜像摘要）+ mor-test 转储 → migrate.sh plan / apply / verify。
#
#   rehearse.sh build     打包 8 个 Java app 的 exec JAR（paytech/ 单 Reactor）并本机编译 vault-server（Go 1.27.1）
#   rehearse.sh up        起 mor-mt1-mysql（127.0.0.1:23307）、mor-mt1-redis（26389）、mor-mt1-rabbitmq（25682），导入转储与模拟项
#   rehearse.sh migrate   plan → apply（含 --park-legacy）→ 再次 apply（证明可重复执行、全部跳过）→ verify
#   rehearse.sh smoke     rehearse-services.sh 起 9 个服务 → sample_reads.py 抽样核对 → 停全部进程
#   rehearse.sh down      删除三个容器（均无卷，数据随容器删除）
#   rehearse.sh all       build + up + migrate + smoke + down（任一步失败也执行 down，除非 MT_KEEP=1）
#
# 输入（环境变量）：MT_DUMP_DIR（五个 <库>.sql 转储，只读）、MT_WORK_DIR（演练私有目录，权限 700，存放随机 root /
# V2 账号 / Redis / RabbitMQ 口令、Vault 根密钥、Platform 运营签名材料与输出）。转储与口令都不进入仓库；输出只含计数与校验和。
# 模拟项（mor-test 实际状态在阶段 2 plan 时读取）：
#   - mor-test 已有的 platform_app、vault_app 账号（转储不含账号）；
#   - 旧 init 脚本建过的空库 mor_payment、mor_channel_connector；
#   - mor_payment 中一张空的旧表 t_legacy_probe，用于演练 --park-legacy 的 RENAME 路径；
#   - xxl_job 库（上游 3.4.2 表结构）中 commerce-service 执行器与一条 compensationEventScan 任务，用于演练 50。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
CONTAINER=mor-mt1-mysql
PORT=23307
IMAGE='mysql:8.4.11@sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb'
: "${MT_DUMP_DIR:?set MT_DUMP_DIR to the directory holding the mor-test dump}"
: "${MT_WORK_DIR:?set MT_WORK_DIR to a private rehearsal directory}"
mkdir -p "$MT_WORK_DIR"; chmod 700 "$MT_WORK_DIR"
SECRETS="$MT_WORK_DIR/rehearsal.env"
V2_ENV="$MT_WORK_DIR/v2-accounts.env"
INFRA_ENV="$MT_WORK_DIR/infra.env"
JDK=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
GO="${MT_GO:-$HOME/sdk/go1.27.1/bin/go}"
APPS=(platform-support client-master commercial acquiring consumer risk-decision compliance payment)

secrets() {
  (umask 077
   [[ -f "$SECRETS" ]] || printf 'MT1_ROOT_PASSWORD=%s\n' "$(openssl rand -hex 20)" > "$SECRETS"
   [[ -f "$INFRA_ENV" ]] || printf 'MT1_REDIS_PASSWORD=%s\nMT1_RABBIT_PASSWORD=%s\nMT1_VAULT_ROOT_KEY=%s\n' \
     "$(openssl rand -hex 20)" "$(openssl rand -hex 20)" "$(openssl rand -base64 32)" > "$INFRA_ENV"
   if [[ ! -f "$V2_ENV" ]]; then
     for key in CLIENT_MASTER COMMERCIAL ACQUIRING CONSUMER PAYMENT CHANNEL_CONNECTOR CARD RISK_DECISION COMPLIANCE \
                LEDGER BILLING ACCOUNT RECONCILIATION TAX PLATFORM VAULT; do
       printf 'MOR_%s_DB_PASSWORD=%s\n' "$key" "$(openssl rand -hex 16)"
     done > "$V2_ENV"
   fi)
  set -a; . "$SECRETS"; . "$INFRA_ENV"; set +a
  export MYSQL_PWD="$MT1_ROOT_PASSWORD"
}

build() {
  (cd "$REPO/paytech" && JAVA_HOME="$JDK" ./mvnw -B -q -DskipTests \
     -pl "$(printf '%s/paytech-%s-app,' $(for a in "${APPS[@]}"; do echo "$a $a"; done) | sed 's/,$//')" -am package)
  mkdir -p "$MT_WORK_DIR/../bin"
  (cd "$REPO/paytech/vault" && GOCACHE="$MT_WORK_DIR/gocache" GOTMPDIR="$MT_WORK_DIR" GOTOOLCHAIN=local GOENV=off GOPROXY=off \
     GOSUMDB=off GOWORK=off GOFLAGS=-mod=readonly CGO_ENABLED=0 "$GO" build -trimpath -buildvcs=false \
     -o "$MT_WORK_DIR/../bin/vault-server" ./cmd/vault-server)
  echo "构建完成：${#APPS[@]} 个 exec JAR + vault-server"
}

materials() {  # Platform 运营认证材料：演练新生成（与 mor-test 不同），只让 IAM 装配
  [[ -f "$MT_WORK_DIR/platform-material.env" ]] && return
  local repository="$HOME/.m3/repository" out="$MT_WORK_DIR/opsmat"
  mkdir -m 700 -p "$out"
  "$JDK/bin/java" --class-path "$repository/org/springframework/security/spring-security-crypto/7.0.7/spring-security-crypto-7.0.7.jar:$repository/org/bouncycastle/bcprov-jdk18on/1.85/bcprov-jdk18on-1.85.jar:$repository/commons-logging/commons-logging/1.3.6/commons-logging-1.3.6.jar" \
    "$REPO/test-environment/scripts/GenerateOperationsMaterial.java" "$out" > /dev/null
  (umask 077; python3 -I -c 'import json,sys
m=json.load(open(sys.argv[1]))
print("OPERATIONS_LOCAL_SIGNING_PRIVATE_KEY_BASE64=" + m["privateKeyBase64"])
print("OPERATIONS_LOCAL_HMAC_KEY_BASE64=" + m["hmacKeyBase64"])
print("OPERATIONS_LOCAL_HMAC_KEY_VERSION=" + str(m["hmacKeyVersion"]))' "$out/operations-material.json" > "$MT_WORK_DIR/platform-material.env")
}

m() { mysql -h127.0.0.1 -P"$PORT" -uroot --protocol=tcp --default-character-set=utf8mb4 "$@"; }

up() {
  secrets
  for c in "$CONTAINER" mor-mt1-redis mor-mt1-rabbitmq; do
    if docker ps -a --format '{{.Names}}' | grep -qx "$c"; then echo "容器 $c 已存在；先执行 down" >&2; exit 1; fi
  done
  docker run -d --name mor-mt1-redis -p 127.0.0.1:26389:6379 -e REDISPASS="$MT1_REDIS_PASSWORD" \
    'redis:8.2.1-alpine@sha256:987c376c727652f99625c7d205a1cba3cb2c53b92b0b62aade2bd48ee1593232' \
    sh -c 'exec redis-server --requirepass "$REDISPASS"' > /dev/null
  docker run -d --name mor-mt1-rabbitmq -p 127.0.0.1:25682:5672 -e RABBITMQ_DEFAULT_USER=mt1 -e RABBITMQ_DEFAULT_PASS="$MT1_RABBIT_PASSWORD" \
    -e RABBITMQ_DEFAULT_VHOST=/mor-mt1 -v "$REPO/test-environment/rabbitmq/enabled_plugins:/etc/rabbitmq/enabled_plugins:ro" \
    'rabbitmq:4.2.9-management@sha256:59935db6392a27b5192f1be080df9b4194bc22f104a7a1bf3b31479a8e0d1031' > /dev/null
  materials
  docker run -d --name "$CONTAINER" -p "127.0.0.1:$PORT:3306" -e MYSQL_ROOT_PASSWORD="$MT1_ROOT_PASSWORD" -e TZ=UTC "$IMAGE" \
    --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci --default-time-zone=+00:00 --max-connections=400 > /dev/null
  for _ in $(seq 1 90); do
    m -e "SELECT 1" > /dev/null 2>&1 && break  # 初始化期间的临时实例不监听 TCP
    sleep 2
  done
  m -N -e 'SELECT VERSION()'
  for db in mor_platform mor_vault mor_cct mor_commerce mor_risk; do
    m < "$MT_DUMP_DIR/$db.sql"
    echo "导入 ${db}：$(m -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db'") 张表"
  done
  m -e "CREATE DATABASE mor_payment CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
        CREATE DATABASE mor_channel_connector CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
        CREATE TABLE mor_payment.t_legacy_probe (id BIGINT NOT NULL PRIMARY KEY) COMMENT='演练：模拟旧 Payment 遗留表';"
  m -e "CREATE DATABASE xxl_job CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  m < "$REPO/local-environment/mysql/init/03-xxl-job-schema.sql"
  m xxl_job -e "INSERT INTO xxl_job_group (app_name, title, address_type, address_list, update_time) VALUES ('commerce-service', 'commerce-service', 0, NULL, NOW());
    INSERT INTO xxl_job_info (job_group, job_desc, add_time, update_time, author, alarm_email, schedule_type, schedule_conf, misfire_strategy,
      executor_route_strategy, executor_handler, executor_param, executor_block_strategy, executor_timeout, executor_fail_retry_count, glue_type,
      glue_source, glue_remark, glue_updatetime, child_jobid, trigger_status, trigger_last_time, trigger_next_time)
    SELECT id, 'commerce compensation scan', NOW(), NOW(), 'rehearsal', '', 'CRON', '0/30 * * * * ?', 'DO_NOTHING', 'SHARDING_BROADCAST',
      'compensationEventScan', '', 'SERIAL_EXECUTION', 0, 0, 'BEAN', '', '', NOW(), '', 1, 0, 0 FROM xxl_job_group WHERE app_name = 'commerce-service';"
  # mor-test 原有的 Platform / Vault 账号（旧 init 脚本建，GRANT ALL；转储不含账号），口令为本次随机值。
  set -a; . "$V2_ENV"; set +a
  m -e "CREATE USER 'platform_app'@'%' IDENTIFIED BY '$MOR_PLATFORM_DB_PASSWORD'; GRANT ALL PRIVILEGES ON mor_platform.* TO 'platform_app'@'%';
        CREATE USER 'vault_app'@'%' IDENTIFIED BY '$MOR_VAULT_DB_PASSWORD'; GRANT ALL PRIVILEGES ON mor_vault.* TO 'vault_app'@'%';"
  echo "模拟：platform_app / vault_app 账号；空库 mor_payment / mor_channel_connector、mor_payment.t_legacy_probe、xxl_job commerce-service 执行器与任务"
}

migrate() {
  secrets
  export MT_PORT="$PORT" MT_ENV_FILE="$V2_ENV" MT_VERIFY_OUT="$MT_WORK_DIR/verify-detail.txt"
  echo "######## plan（不带 --park-legacy，预期因 mor_payment 旧表停止）"
  if "$HERE/migrate.sh" plan; then echo "预期停止却通过" >&2; exit 1; fi
  echo "######## plan --park-legacy"
  "$HERE/migrate.sh" plan --park-legacy
  echo "######## apply --park-legacy"
  "$HERE/migrate.sh" apply --park-legacy
  echo "######## 再次 apply（全部应跳过）"
  "$HERE/migrate.sh" apply
  echo "######## xxl_job 结果"
  m -N -e "SELECT g.app_name, i.executor_handler, i.trigger_status FROM xxl_job.xxl_job_info i JOIN xxl_job.xxl_job_group g ON g.id = i.job_group ORDER BY g.app_name"
  echo "逐表核对明细：$MT_VERIFY_OUT"
}

smoke() {
  secrets
  export MT_WORK_DIR MT_PORT="$PORT"
  local rc=0
  "$HERE/rehearse-services.sh" start || rc=$?
  "$HERE/rehearse-services.sh" status
  if [[ "$rc" == 0 ]]; then python3 -I "$HERE/sample_reads.py" | tee "$MT_WORK_DIR/sample-reads.txt" || rc=$?; fi
  "$HERE/rehearse-services.sh" stop
  return "$rc"
}

down() {
  local c
  for c in "$CONTAINER" mor-mt1-redis mor-mt1-rabbitmq; do
    docker rm -f "$c" > /dev/null 2>&1 && echo "已删除 $c" || echo "$c 不存在"
  done
}

all() {  # set -e 下任一步失败即退出；EXIT trap 保证删除容器（MT_KEEP=1 时保留）
  trap '[[ "${MT_KEEP:-0}" == 1 ]] || down' EXIT
  build
  up
  migrate
  smoke
  echo "演练通过"
}

case "${1:-}" in
  build) build ;; up) up ;; migrate) migrate ;; smoke) smoke ;; down) down ;; all) all ;;
  *) sed -n '2,19p' "$0"; exit 2 ;;
esac
