#!/usr/bin/env bash
# mor-test → V2 数据迁移执行器（评审 SQL 的顺序执行与前置核对；不生成 SQL，不改 SQL）。
#
#   migrate.sh plan     只读：核对前置状态，打印每一步将做什么
#   migrate.sh apply    按顺序执行 10 → Schema → 20 → 21 → 30 → 31 → 32 → 33 →（xxl_job 存在时）50，然后 verify
#   migrate.sh verify   只读：40 行数 / 校验和核对 + V2 库表集合 + risk-decision 种子主键（启动服务前执行）
#   migrate.sh fingerprint  只读：打印五个旧库的结构指纹（列 + 索引的 SHA-256；演练时写入 sql/legacy-schema.sha256）
#
# 连接（root，经环境变量，不出现在命令行）：MT_HOST（默认 127.0.0.1）、MT_PORT（默认 23306 = mor-test）、MYSQL_PWD。
# V2 账号口令：MT_ENV_FILE（默认 test-environment/.env.test）中的 MOR_*_DB_PASSWORD，只替换进 10 的标准输入。
# 选项：--park-legacy  当某个 V2 库名已被旧表占用（旧 init 脚本建的 mor_payment / mor_channel_connector 等）时，
#                      把这些旧表 RENAME 到 mor_v2_migration_archive.<库简名>_legacy_<表>（数据随表保留），否则停止。
# 每一步执行前核对前置状态；状态不符时停止且不做任何写入，已完成的步骤再次执行时跳过。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REQ="$(dirname "$HERE")"
SQL="$REQ/sql"
REPO="$(cd "$REQ/../../.." && pwd)"
PAYTECH="$REPO/paytech"
SUPPORT=(python3 -I "$HERE/migrate_support.py")
MT_HOST="${MT_HOST:-127.0.0.1}"
MT_PORT="${MT_PORT:-23306}"
MT_ENV_FILE="${MT_ENV_FILE:-$REPO/test-environment/.env.test}"
ARCHIVE=mor_v2_migration_archive
PARK_LEGACY=0
: "${MYSQL_PWD:?set MYSQL_PWD to the MySQL root password (environment only)}"
export MYSQL_PWD

LEGACY_DBS=(mor_platform mor_vault mor_cct mor_commerce mor_risk)
# V2 库：服务目录 : 库名 : 角色（target = 迁移目标，不执行初始化 DML；new = 空库新建，执行初始化 DML）
V2_DBS=(
  client-master:mor_client_master:target commercial:mor_commercial:target acquiring:mor_acquiring:target
  consumer:mor_consumer:target risk-decision:mor_risk_decision:target compliance:mor_compliance:target
  payment:mor_payment:new channel-connector:mor_channel_connector:new card:mor_card:new ledger:mor_ledger:new
  billing:mor_billing:new account:mor_account:new reconciliation:mor_reconciliation:new tax:mor_tax:new
)
LEGACY_APP_USERS="'cct_app','commerce_app','risk_app','platform_app','vault_app','payment_app','finance_app'"

mysql_base() { mysql -h"$MT_HOST" -P"$MT_PORT" -uroot --protocol=tcp --default-character-set=utf8mb4 "$@"; }
q() { mysql_base -N -B -e "$1"; }
run_sql() { echo "  执行 $(basename "$1")${2:+（库 $2）}"; mysql_base ${2:+"$2"} < "$1"; }
die() { echo "停止：$*" >&2; exit 1; }
schema_file() { echo "$PAYTECH/$1/docs/database/sql/$1-schema.sql"; }
initial_file() { echo "$PAYTECH/$1/docs/database/sql/$1-initial-data.sql"; }
db_exists() { [[ "$(q "SELECT COUNT(*) FROM information_schema.schemata WHERE schema_name='$1'")" == 1 ]]; }
table_count() { q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$1'"; }
row_total() {  # 库内全部表的精确行数之和；查询失败直接停止（不得把失败当作“非空”或“为空”）
  local db="$1" tables table sql="" total
  tables=$(q "SELECT table_name FROM information_schema.tables WHERE table_schema='$db' ORDER BY table_name") || die "读取 $db 表清单失败"
  for table in $tables; do sql="${sql:+$sql UNION ALL }SELECT COUNT(*) c FROM \`$db\`.\`$table\`"; done
  [[ -z "$sql" ]] && { echo 0; return; }
  total=$(q "SELECT SUM(c) FROM ($sql) x") || die "统计 $db 行数失败"
  [[ "$total" =~ ^[0-9]+$ ]] || die "统计 $db 行数得到非数字结果"
  echo "$total"
}

legacy_fingerprint() {
  q "SELECT CONCAT_WS('\t', table_schema, table_name, column_name, ordinal_position, column_type, is_nullable,
              IFNULL(column_default, '<NULL>'), extra, IFNULL(collation_name, ''))
       FROM information_schema.columns
      WHERE table_schema IN ('mor_platform','mor_vault','mor_cct','mor_commerce','mor_risk')
        AND NOT (table_schema = 'mor_platform' AND table_name = 't_currency_spec')
      ORDER BY table_schema, table_name, ordinal_position;
     SELECT CONCAT_WS('\t', table_schema, table_name, index_name, non_unique, seq_in_index, IFNULL(column_name, expression))
       FROM information_schema.statistics
      WHERE table_schema IN ('mor_platform','mor_vault','mor_cct','mor_commerce','mor_risk')
        AND NOT (table_schema = 'mor_platform' AND table_name = 't_currency_spec')
      ORDER BY table_schema, table_name, index_name, seq_in_index" | "${SUPPORT[@]}" fingerprint
}

# V2 库状态：ABSENT / EMPTY / PREPARED（表集合与 Schema 相同）/ FOREIGN（有不属于 V2 Schema 的表）
v2_state() {
  local svc="$1" db="$2"
  db_exists "$db" || { echo ABSENT; return; }
  local actual expected
  actual=$(q "SELECT table_name FROM information_schema.tables WHERE table_schema='$db' ORDER BY table_name")
  [[ -z "$actual" ]] && { echo EMPTY; return; }
  expected=$("${SUPPORT[@]}" tables "$(schema_file "$svc")" | sort)
  [[ "$actual" == "$expected" ]] && { echo PREPARED; return; }
  echo FOREIGN
}

preflight() {
  echo "== 前置核对（$MT_HOST:${MT_PORT}）"
  local expected_tables=(mor_platform:56:57 mor_vault:6:6 mor_cct:38:38 mor_commerce:17:17 mor_risk:85:85)
  for spec in "${expected_tables[@]}"; do
    IFS=: read -r db low high <<< "$spec"
    db_exists "$db" || die "旧库 $db 不存在"
    local n; n=$(table_count "$db")
    [[ "$n" -ge "$low" && "$n" -le "$high" ]] || die "$db 有 $n 张表，预期 ${low}—$high"
    echo "  旧库 ${db}：$n 张表"
  done
  local fp expected_fp; fp=$(legacy_fingerprint); expected_fp=$(tr -d '[:space:]' < "$SQL/legacy-schema.sha256")
  [[ "$fp" == "$expected_fp" ]] || die "旧库结构指纹 $fp 与演练时 $expected_fp 不同：mor-test 结构在转储后变化，需重新演练"
  echo "  旧库结构指纹与演练一致：$fp"
  local sessions; sessions=$(q "SELECT IFNULL(GROUP_CONCAT(DISTINCT user), '') FROM information_schema.processlist WHERE user IN ($LEGACY_APP_USERS)")
  [[ -z "$sessions" ]] || die "旧服务账号仍有连接（${sessions}）：先停旧服务"
  echo "  旧服务账号无连接"
  [[ -f "$MT_ENV_FILE" ]] || die "缺少口令文件 $MT_ENV_FILE"
  "${SUPPORT[@]}" render "$SQL/10-create-v2-databases.sql" "$MT_ENV_FILE" > /dev/null
  echo "  10 所需的 14 个 V2 口令齐全且格式合法（未打印）"
  for spec in "${V2_DBS[@]}"; do
    IFS=: read -r svc db role <<< "$spec"
    local state; state=$(v2_state "$svc" "$db")
    case "$state" in
      ABSENT|EMPTY)
        if [[ "$role" == new ]]; then echo "  ${db}：$state → 建表 + 初始化 DML"; else echo "  ${db}：$state → 建表（迁移目标，不执行初始化 DML）"; fi ;;
      PREPARED)
        local rows; rows=$(row_total "$db")
        echo "  ${db}：PREPARED（$rows 行）→ 跳过建表" ;;
      FOREIGN)
        local legacy; legacy=$(q "SELECT GROUP_CONCAT(CONCAT(table_name, '=', IFNULL(table_rows, 0)) ORDER BY table_name) FROM information_schema.tables WHERE table_schema='$db'")
        if [[ "$PARK_LEGACY" == 1 ]]; then echo "  ${db}：FOREIGN → 旧表移入 ${ARCHIVE}（$(echo "$legacy" | tr ',' ' ')）"
        else die "$db 已有非 V2 表（估计行数：${legacy}）；确认后加 --park-legacy 移入归档库"; fi ;;
    esac
  done
}

park_legacy() {
  local svc="$1" db="$2" short="${2#mor_}"
  local renames; renames=$(q "SELECT GROUP_CONCAT(CONCAT('\`$db\`.\`', table_name, '\` TO \`$ARCHIVE\`.\`${short}_legacy_', table_name, '\`') ORDER BY table_name SEPARATOR ', ')
                              FROM information_schema.tables WHERE table_schema='$db'")
  echo "  RENAME TABLE $renames"
  q "RENAME TABLE $renames"
}

apply_schemas() {
  echo "== V2 Schema 与初始化 DML"
  for spec in "${V2_DBS[@]}"; do
    IFS=: read -r svc db role <<< "$spec"
    local state; state=$(v2_state "$svc" "$db")
    if [[ "$state" == FOREIGN ]]; then park_legacy "$svc" "$db"; state=$(v2_state "$svc" "$db"); fi
    case "$state" in
      EMPTY)
        run_sql "$(schema_file "$svc")" "$db"
        if [[ "$role" == new && -f "$(initial_file "$svc")" ]]; then run_sql "$(initial_file "$svc")" "$db"; fi
        if [[ "$role" == target && -f "$(initial_file "$svc")" && "$svc" != risk-decision ]]; then
          [[ "$("${SUPPORT[@]}" inserts "$(initial_file "$svc")")" == 0 ]] || die "$svc 初始化 DML 含 INSERT，迁移目标库需人工决定是否执行"
        fi
        [[ "$(v2_state "$svc" "$db")" == PREPARED ]] || die "$db 建表后表集合与 Schema 不一致" ;;
      PREPARED) echo "  ${db}：已建表，跳过" ;;
      *) die "$db 状态 ${state}，不能建表" ;;
    esac
  done
}

all_empty() {  # 参数 = 目标库列表；全部为空返回 0
  local db rows
  for db in "$@"; do rows=$(row_total "$db") || exit 1; [[ "$rows" == 0 ]] || return 1; done
}

apply_platform() {
  echo "== 20 Platform 升级"
  local has; has=$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_platform' AND table_name='t_currency_spec'")
  local target; target=$(q "SELECT IFNULL(GROUP_CONCAT(discovery_service_name), '') FROM mor_platform.t_gateway_target WHERE target_service_code='commerce-client-api'")
  if [[ "$has" == 1 && "$target" != commerce-service ]]; then echo "  已升级（t_currency_spec 存在，Target 发现名 ${target:-无}），跳过"; return; fi
  run_sql "$SQL/20-platform-upgrade.sql"
}

apply_vault() {
  echo "== 21 Vault 访问规则"
  local existing; existing=$(q "SELECT (SELECT COUNT(*) FROM mor_vault.t_vault_access_rule WHERE access_rule_id BETWEEN 9100000000000701 AND 9100000000000707)
                                    + (SELECT COUNT(*) FROM mor_vault.t_vault_data_type WHERE data_type_id = 9100000000000700 OR data_type_code = 'PAYMENT_CARD_DEK')
                                    + (SELECT COUNT(*) FROM mor_vault.t_vault_policy_change WHERE policy_change_id = 9100000000000700)")
  local callers; callers=$(q "SELECT COUNT(*) FROM mor_vault.t_vault_access_rule WHERE caller_service IN ('client-master-service','card-service','channel-connector-service')")
  if [[ "$existing" == 9 && "$callers" == 7 ]]; then echo "  已登记，跳过"; return; fi
  [[ "$existing" == 0 && "$callers" == 0 ]] || die "Vault 已有部分 V2 规则或 ID 被占用（ID 命中 ${existing}，调用方规则 ${callers}），需人工核对"
  run_sql "$SQL/21-vault-upgrade.sql"
}

apply_migration() {  # $1 = SQL 文件，其余 = 目标库
  local file="$1"; shift
  echo "== $(basename "$file")"
  if all_empty "$@"; then run_sql "$file"; return; fi
  echo "  目标库已有数据（$*），视为已执行，交给 verify 核对"
}

apply_archive() {
  echo "== 33 归档"
  if [[ "$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$ARCHIVE' AND table_name NOT LIKE '%\\_legacy\\_%'")" != 0 ]]; then
    echo "  归档表已存在，跳过"; return
  fi
  run_sql "$SQL/33-archive-retired.sql"
}

apply_xxl() {
  echo "== 50 XXL-Job"
  db_exists xxl_job || { echo "  无 xxl_job 库，跳过"; return; }
  local old new
  old=$(q "SELECT COUNT(*) FROM xxl_job.xxl_job_group WHERE app_name='commerce-service'")
  new=$(q "SELECT COUNT(*) FROM xxl_job.xxl_job_group WHERE app_name='acquiring-service'")
  [[ "$new" == 0 ]] || { echo "  acquiring-service 执行器已存在，跳过"; return; }
  [[ "$old" == 1 ]] || { echo "  没有 commerce-service 执行器，跳过（按 runbook 在 Admin 页面登记）"; return; }
  run_sql "$SQL/50-xxl-job-acquiring.sql"
}

verify() {
  echo "== 核对"
  local out mismatch total
  out=$(mysql_base -N -B < "$SQL/40-verify-counts.sql")
  total=$(echo "$out" | grep -c . || true)
  mismatch=$(echo "$out" | awk -F'\t' '$4 != "OK"' )
  echo "$out" | awk -F'\t' '{ printf "  %-80s %8s %8s  %s\n", $1, $2, $3, $4 }' > "${MT_VERIFY_OUT:-/dev/null}"
  echo "  40：$total 项，MISMATCH $(echo -n "$mismatch" | grep -c . || true) 项"
  [[ -z "$mismatch" ]] || { echo "$mismatch" | awk -F'\t' '{ print "  MISMATCH " $1 " old=" $2 " new=" $3 }'; return 1; }
  local old_rows new_rows
  old_rows=$(( $(row_total mor_cct) + $(row_total mor_commerce) + $(row_total mor_risk) ))
  new_rows=0
  for db in mor_client_master mor_commercial mor_acquiring mor_consumer mor_risk_decision mor_compliance; do new_rows=$(( new_rows + $(row_total "$db") )); done
  local archived; archived=$(q "SELECT (SELECT COUNT(*) FROM $ARCHIVE.cct_t_command_idempotency) + (SELECT COUNT(*) FROM $ARCHIVE.commerce_t_command_idempotency)
                                 + (SELECT COUNT(*) FROM $ARCHIVE.risk_t_command_idempotency)")
  local disputes; disputes=$(q "SELECT (SELECT COUNT(*) FROM $ARCHIVE.risk_t_dispute_case) + (SELECT COUNT(*) FROM $ARCHIVE.risk_t_dispute_state_change)
    + (SELECT COUNT(*) FROM $ARCHIVE.risk_t_dispute_evidence_package) + (SELECT COUNT(*) FROM $ARCHIVE.risk_t_dispute_evidence_ref)
    + (SELECT COUNT(*) FROM $ARCHIVE.risk_t_dispute_submission_request) + (SELECT COUNT(*) FROM $ARCHIVE.risk_t_dispute_outcome)
    + (SELECT COUNT(*) FROM $ARCHIVE.risk_t_dispute_liability_decision)")
  echo "  三个拆分旧库总行数 ${old_rows}；V2 六库 $new_rows + 归档幂等记录 $archived + 归档争议表 $disputes = $(( new_rows + archived + disputes ))"
  [[ "$old_rows" == $(( new_rows + archived + disputes )) ]] || { echo "  总行数不守恒（服务启动后再执行 verify 会因新写入而不等，verify 只在启动服务前作为迁移门禁）"; return 1; }
  for spec in "${V2_DBS[@]}"; do
    IFS=: read -r svc db role <<< "$spec"
    [[ "$(v2_state "$svc" "$db")" == PREPARED ]] || { echo "  $db 表集合与 Schema 不一致"; return 1; }
  done
  echo "  14 个 V2 库表集合与评审 Schema 一致"
  [[ "$(q "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_platform'")" == 57 ]] || { echo "  mor_platform 不是 57 表"; return 1; }
  [[ "$(q "SELECT COUNT(*) FROM mor_platform.t_currency_spec WHERE currency='USD'")" == 1 ]] || { echo "  缺 USD 币种"; return 1; }
  [[ "$(q "SELECT discovery_service_name FROM mor_platform.t_gateway_target WHERE target_service_code='commerce-client-api'")" == acquiring-service ]] \
    || { echo "  commerce-client-api Target 未改为 acquiring-service"; return 1; }
  [[ "$(q "SELECT COUNT(*) FROM mor_vault.t_vault_access_rule WHERE caller_service IN ('client-master-service','card-service','channel-connector-service') AND status='ACTIVE'")" == 7 ]] \
    || { echo "  Vault V2 调用方规则不是 7 条"; return 1; }
  echo "  mor_platform 57 表、USD、Target 发现名、Vault 7 条规则：OK"
  local missing=0 table key
  while read -r table key; do
    local pk; pk=$(q "SELECT column_name FROM information_schema.key_column_usage WHERE table_schema='mor_risk_decision' AND table_name='$table' AND constraint_name='PRIMARY'")
    [[ "$(q "SELECT COUNT(*) FROM mor_risk_decision.\`$table\` WHERE \`$pk\` = $key")" == 1 ]] || { echo "  缺 risk-decision 种子 $table $key"; missing=$(( missing + 1 )); }
  done < <("${SUPPORT[@]}" seed-keys "$(initial_file risk-decision)" | sort -u)
  [[ "$missing" == 0 ]] || return 1
  echo "  risk-decision 初始化目录种子主键齐全（$("${SUPPORT[@]}" seed-keys "$(initial_file risk-decision)" | sort -u | wc -l | tr -d ' ') 个）"
  echo "核对通过"
}

main() {
  local command="${1:-}"; shift || true
  for arg in "$@"; do
    case "$arg" in --park-legacy) PARK_LEGACY=1 ;; *) die "未知参数 $arg" ;; esac
  done
  case "$command" in
    plan) preflight; echo "plan 完成：未写入任何数据" ;;
    apply)
      preflight
      echo "== 10 建库与账号"
      "${SUPPORT[@]}" render "$SQL/10-create-v2-databases.sql" "$MT_ENV_FILE" | mysql_base
      echo "  完成"
      apply_schemas
      apply_platform
      apply_vault
      apply_migration "$SQL/30-migrate-cct.sql" mor_client_master mor_commercial
      apply_migration "$SQL/31-migrate-commerce.sql" mor_acquiring mor_consumer
      apply_migration "$SQL/32-migrate-risk.sql" mor_risk_decision mor_compliance
      apply_archive
      apply_xxl
      verify ;;
    verify) verify ;;
    fingerprint) legacy_fingerprint ;;
    *) sed -n '2,12p' "$0"; exit 2 ;;
  esac
}

main "$@"
