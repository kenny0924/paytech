#!/usr/bin/env bash
# 阶段 5 本机联调的库与种子准备（只作用于 local-environment 的 *_v2e2e 全链路库，合成数据）。root 执行；可重复执行（每步自带跳过判断）。
#   setup.sh plan | databases | schemas | client-master | acquiring | risk | commercial | platform | all
# 库：本机 e2e 全链路（requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/run-service.sh）使用 mor_<svc>_v2e2e；
#   client-master / commercial / compliance 三个 v2e2e 库在本步之前已不存在，按该目录 sql/00-e2e-databases.sql 同样的字符集重建
#   （应用账号的库级授权仍在），表结构取本 worktree 的 <svc>-schema.sql。
# 顺序：client-master 种子（AE001 先为 PLATFORM 来源）→ client-master 正式数据文件（迁到“我们的 PayFac”Partner 900000000001370001）
#   → acquiring 增量 DDL + PLATFORM 订单改写 → risk-decision PLATFORM 改写 → commercial Client 合同种子 + PayFac Partner 合同正式草稿
#   → Platform 注册块 137 + test-activate。
# 执行前备份：scratchpad backup/relsnap-stage5（mor_acquiring_v2e2e、mor_risk_decision_v2e2e、mor_platform_v2e2e 受影响表）。
# root 口令只从主仓库 local-environment/.env.local 读入 MYSQL_PWD，不打印。
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WT="$(cd "$HERE/../../../../.." && pwd)"
REQ="$(cd "$HERE/../.." && pwd)"
LOCAL_ENV="${E2E_LOCAL_ENV:-/Users/kenny/Desktop/Workspace/Code-MoR/local-environment}"
set -a; . "$LOCAL_ENV/.env.local"; set +a
export MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD"
MYSQL=(mysql -h127.0.0.1 -P13306 -uroot --default-character-set=utf8mb4)
PAYFAC=900000000001370001

q() { "${MYSQL[@]}" -N -B -e "$2" "$1"; }
run() { echo "  apply $2 -> $1"; "${MYSQL[@]}" "$1" < "$2"; }
tables_in() { "${MYSQL[@]}" -N -B -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$1'"; }

databases() {
  for db in mor_client_master_v2e2e mor_commercial_v2e2e mor_compliance_v2e2e; do
    "${MYSQL[@]}" -e "CREATE DATABASE IF NOT EXISTS $db CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
    echo "  database $db: $(tables_in $db) tables"
  done
}

schemas() {
  for svc in client-master commercial compliance; do
    local db="mor_${svc//-/_}_v2e2e" dir="$WT/paytech/$svc/docs/database/sql"
    if [[ "$(tables_in $db)" != 0 ]]; then echo "  skip schema $db (not empty)"; continue; fi
    run "$db" "$dir/$svc-schema.sql"
    [[ "$svc" == commercial ]] && run "$db" "$dir/commercial-initial-data.sql"
    echo "  $db: $(tables_in $db) tables"
  done
}

client_master() {
  local db=mor_client_master_v2e2e
  run $db "$HERE/10-client-master-seed.sql"
  if [[ "$(q $db "SELECT COUNT(*) FROM t_local_partner WHERE local_partner_id=$PAYFAC")" == 1 ]]; then
    echo "  skip data migration (PayFac Partner exists)"
  else
    run $db "$WT/paytech/client-master/docs/database/sql/changes/2026-10-24-relationship-snapshot-data.sql"
  fi
  q $db "SELECT entity_no, source_type, source_partner_id FROM t_entity_source_relation"
  q $db "SELECT role_code, subject_id, party_id FROM t_party_role ORDER BY role_code, subject_id"
}

acquiring() {
  local db=mor_acquiring_v2e2e dir="$WT/paytech/acquiring/docs/database/sql/changes"
  if [[ "$(q $db "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$db' AND table_name='t_order_relationship_binding'")" == 1 ]]; then
    echo "  skip acquiring DDL (binding table exists)"
  else
    run $db "$dir/2026-10-24-order-relationship-snapshot.sql"
  fi
  echo "  apply PLATFORM rewrite with @payfac_local_partner_id=$PAYFAC"
  { echo "SET @payfac_local_partner_id = $PAYFAC;"; cat "$dir/2026-10-24-remove-platform-entity-source.sql"; } | "${MYSQL[@]}" $db
  q $db "SELECT entity_source_type, local_partner_id, COUNT(*) FROM t_commerce_order GROUP BY 1,2"
}

risk() {
  local db=mor_risk_decision_v2e2e
  echo "  apply risk PLATFORM removal with @payfac_local_partner_id=$PAYFAC"
  { echo "SET @payfac_local_partner_id = $PAYFAC;"; cat "$WT/paytech/risk-decision/docs/database/sql/changes/2026-10-24-remove-platform-entity-source.sql"; } \
    | "${MYSQL[@]}" $db
  q $db "SELECT value_key FROM t_risk_variable_member WHERE variable_code='ENTITY_SOURCE_VALUES'"
  q $db "SELECT entity_source_type, local_partner_id, COUNT(*) FROM t_checkout_input_snapshot GROUP BY 1,2"
}

commercial() {
  local db=mor_commercial_v2e2e
  run $db "$HERE/20-commercial-seed.sql"
  if [[ "$(q $db "SELECT COUNT(*) FROM t_agreement WHERE agreement_id=900000000001371001")" == 1 ]]; then
    echo "  skip PayFac Partner agreement (exists)"
  else
    echo "  apply PayFac Partner agreement draft with synthetic variables"
    { cat <<SQL
SET @payfac_local_partner_id = $PAYFAC;
SET @ae001_party_id = 900000000001380102;
SET @ae001_party_profile_id = 900000000001380111;
SET @platform_party_id = 900000000001380900;
SET @platform_party_profile_id = 900000000001380901;
SET @signing_file_id = 900000000001371099;
SET @signing_content_hash = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
SET @operator_account_id = 1;
SET @card_profile_code = 'RELSNAP_CARD';
SET @effective_from = '2026-10-01 00:00:00.000000';
SQL
      cat "$WT/paytech/commercial/docs/database/sql/changes/2026-10-24-payfac-partner-agreement.sql"; } | "${MYSQL[@]}" $db
  fi
  q $db "SELECT agreement_id, agreement_type, business_mode, client_id, local_partner_id, entity_no, status FROM t_agreement ORDER BY 1"
}

platform() {
  local db=mor_platform_v2e2e dir="$REQ/db/platform-support"
  if [[ "$(q $db "SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000137001 AND 900000000000137999")" != 0 ]]; then
    echo "  skip block 137 registration (rows exist)"
  else
    run $db "$dir/registration-block137.sql"
  fi
  if [[ "$(q $db "SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000137401 AND 900000000000137411 AND status='DISABLED'")" == 0 ]]; then
    echo "  skip activation (no DISABLED block 137 route)"
  else
    run $db "$dir/test-activate.sql"
  fi
  q $db "SELECT status, COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000137401 AND 900000000000137411 GROUP BY 1"
}

case "${1:-plan}" in
  plan) sed -n '2,10p' "$0" ;;
  databases) databases ;; schemas) schemas ;; client-master) client_master ;; acquiring) acquiring ;; risk) risk ;;
  commercial) commercial ;; platform) platform ;;
  all) databases; schemas; client_master; acquiring; risk; commercial; platform ;;
  *) echo "unknown step $1" >&2; exit 2 ;;
esac
