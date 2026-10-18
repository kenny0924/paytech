#!/usr/bin/env bash
# 阶段 5 本机全链路的库与基础设施准备（任务卡 14）。由主会话评审后执行；本步 agent 不执行。
# 用法：apply-e2e.sh plan | databases | schemas | platform | vault | channel | routes | rabbitmq | all
#   plan       只列出将执行的动作（默认）
#   databases  root 执行 00-e2e-databases.sql：15 个 *_v2e2e 库与授权
#   schemas    14 个服务库：各 paytech/<svc>/docs/database/sql/<svc>-schema.sql（+ initial-data，compliance 另加通用材料程序）
#   platform   mysqldump 复制 mor_platform_e2e → mor_platform_v2e2e，再执行 20-platform-v2e2e-delta.sql
#   vault      执行 30-vault-v2-callers.sql（mor_vault，容器 Vault 的库）
#   channel    执行 50-channel-connector-payermax-mock.sql（mor_channel_connector_v2e2e：合成 PayerMax 渠道、账号、MID、能力）
#   routes     在仓库根执行 51-platform-external-api-routes.sql（mor_platform_v2e2e：外部 API 管理与渠道沙箱运营路由，含 SOURCE 相对路径）
#   rabbitmq   在容器 RabbitMQ 新建 vhost /mor-e2e 并授予 MOR_RABBITMQ_USER 全部权限（auto 测试会清空 /mor-local 的队列）
# 每个目标库执行前核对为空库（0 表），否则停止。root 口令只从 local-environment/.env.local 读入 MYSQL_PWD，不打印。
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO="$(cd "$HERE/../../../../../.." && pwd)"
LOCAL_ENV="${E2E_LOCAL_ENV:-/Users/kenny/Desktop/Workspace/Code-MoR/local-environment}"
set -a; . "$LOCAL_ENV/.env.local"; set +a
export MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD"
MYSQL=(mysql -h127.0.0.1 -P13306 -uroot --default-character-set=utf8mb4)
SQL="$REPO/paytech"
MATERIALS="$REPO/requirements/20260914/client-console-注册与客户入驻/db/implementation/risk-generic-materials-init.sql"

# 服务名 → 库名；Schema 与初始化数据文件按服务名定位
SERVICES=(client-master commercial compliance risk-decision acquiring consumer payment card channel-connector
  billing ledger account reconciliation tax)
db_of() { echo "mor_${1//-/_}_v2e2e"; }

tables_in() {
  "${MYSQL[@]}" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$1'"
}

require_empty() {
  local count; count=$(tables_in "$1")
  [[ "$count" == 0 ]] || { echo "停止：$1 已有 $count 张表" >&2; exit 1; }
}

files_of() {
  local svc="$1" dir="$SQL/$1/docs/database/sql"
  echo "$dir/$svc-schema.sql"
  [[ -f "$dir/$svc-initial-data.sql" ]] && echo "$dir/$svc-initial-data.sql"
  [[ "$svc" == compliance ]] && echo "$MATERIALS"
  return 0
}

plan() {
  echo "databases: ${MYSQL[*]} < $HERE/00-e2e-databases.sql"
  for svc in "${SERVICES[@]}"; do
    echo "schemas:   $(db_of "$svc") <- $(files_of "$svc" | sed "s#$REPO/##" | paste -sd' ' -)"
  done
  echo "platform:  mysqldump mor_platform_e2e | mysql mor_platform_v2e2e; then 20-platform-v2e2e-delta.sql"
  echo "vault:     mor_vault <- 30-vault-v2-callers.sql"
  echo "channel:   mor_channel_connector_v2e2e <- 50-channel-connector-payermax-mock.sql"
  echo "routes:    (cd $REPO) mor_platform_v2e2e <- 51-platform-external-api-routes.sql"
  echo "rabbitmq:  docker exec mor-local-rabbitmq-1 rabbitmqctl add_vhost /mor-e2e; set_permissions -p /mor-e2e <MOR_RABBITMQ_USER> .* .* .*"
}

databases() { "${MYSQL[@]}" < "$HERE/00-e2e-databases.sql"; echo "databases: done"; }

schemas() {
  for svc in "${SERVICES[@]}"; do
    local db; db=$(db_of "$svc"); require_empty "$db"
    while read -r file; do "${MYSQL[@]}" "$db" < "$file"; done < <(files_of "$svc")
    echo "schemas: $db $(tables_in "$db") 张表"
  done
}

platform() {
  require_empty mor_platform_v2e2e
  mysqldump -h127.0.0.1 -P13306 -uroot --default-character-set=utf8mb4 --single-transaction --no-tablespaces \
    --skip-triggers --set-gtid-purged=OFF mor_platform_e2e | "${MYSQL[@]}" mor_platform_v2e2e
  "${MYSQL[@]}" < "$HERE/20-platform-v2e2e-delta.sql"
  echo "platform: mor_platform_v2e2e $(tables_in mor_platform_v2e2e) 张表"
}

vault() {
  local existing
  existing=$("${MYSQL[@]}" -N -B -e "SELECT COUNT(*) FROM mor_vault.t_vault_access_rule WHERE caller_service IN
    ('client-master-service','card-service','channel-connector-service')")
  [[ "$existing" == 0 ]] || { echo "停止：mor_vault 已有 $existing 条 V2 调用方规则" >&2; exit 1; }
  "${MYSQL[@]}" < "$HERE/30-vault-v2-callers.sql"
  echo "vault: done"
}

channel() {
  local existing
  existing=$("${MYSQL[@]}" -N -B -e "SELECT COUNT(*) FROM mor_channel_connector_v2e2e.t_payment_channel WHERE channel_code='PAYERMAX_MOCK'")
  [[ "$existing" == 0 ]] || { echo "停止：PAYERMAX_MOCK 已登记" >&2; exit 1; }
  "${MYSQL[@]}" < "$HERE/50-channel-connector-payermax-mock.sql"
  echo "channel: done"
}

routes() {
  local existing
  existing=$("${MYSQL[@]}" -N -B -e "SELECT COUNT(*) FROM mor_platform_v2e2e.t_gateway_route WHERE route_id BETWEEN 900000000000071001 AND 900000000000071044")
  [[ "$existing" == 0 ]] || { echo "停止：外部 API 路由已登记 $existing 条" >&2; exit 1; }
  (cd "$REPO" && "${MYSQL[@]}" < "$HERE/51-platform-external-api-routes.sql")
  echo "routes: done"
}

rabbitmq() {
  docker exec mor-local-rabbitmq-1 rabbitmqctl add_vhost /mor-e2e
  docker exec mor-local-rabbitmq-1 rabbitmqctl set_permissions -p /mor-e2e "$MOR_RABBITMQ_USER" '.*' '.*' '.*'
}

case "${1:-plan}" in
  plan) plan ;;
  databases|schemas|platform|vault|channel|routes|rabbitmq) "$1" ;;
  all) databases; schemas; platform; vault; channel; routes; rabbitmq ;;
  *) echo "用法：$0 plan|databases|schemas|platform|vault|channel|routes|rabbitmq|all" >&2; exit 2 ;;
esac
