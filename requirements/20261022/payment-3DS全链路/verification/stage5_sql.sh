#!/usr/bin/env bash
# 3DS 全链路 + 卡 BIN 库 阶段 5（tasks/E-stage5.md）：本机 e2e 库（*_v2e2e）的备份、执行与核对。只作用于本机 13306。
#   stage5_sql.sh precheck                 只读：各目标库现状与前置条件
#   stage5_sql.sh backup                   整库 mysqldump 涉及的 *_v2e2e 库到 local-environment/e2e/backup/<UTC>-card3ds-stage5/
#   stage5_sql.sh schema-payment|schema-risk   空库建表（集成目录 canonical schema + initial-data；需单独授权，见报告第 2 节）
#   stage5_sql.sh card|channel|platform    执行两项需求的增量 DDL / DML
#   stage5_sql.sh activate-blocks          激活块 132（授权、菜单、9 条路由）与块 133（回调路由）——各块注释中的激活语句
#   stage5_sql.sh verify                   执行后核对（只读）
# root 口令只从 local-environment/.env.local 读入 MYSQL_PWD，不打印。日志追加到 verification/stage5-sql-log.txt。
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAIN=/Users/kenny/Desktop/Workspace/Code-MoR
INT=/Users/kenny/Desktop/Workspace/Code-MoR-card3ds-int
R3DS="$MAIN/requirements/20261022/payment-3DS全链路/db"
RBIN="$MAIN/requirements/20261022/card-BIN库与卡属性/db"
set -a; . "$MAIN/local-environment/.env.local"; set +a
export MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD"
MYSQL=(mysql -h127.0.0.1 -P13306 -uroot --default-character-set=utf8mb4)
LOG="$HERE/stage5-sql-log.txt"
DBS=(mor_card_v2e2e mor_payment_v2e2e mor_channel_connector_v2e2e mor_risk_decision_v2e2e mor_platform_v2e2e)

q() { "${MYSQL[@]}" -N -B "$1" -e "$2"; }
run() { # run <db> <file>
  echo "$(date -u +%FT%TZ) $1 <- ${2#$MAIN/}" | tee -a "$LOG"
  "${MYSQL[@]}" -v "$1" < "$2" 2>&1 | grep -v '^-\{5,\}' | tail -5 | sed 's/^/    /' | tee -a "$LOG"
  [[ ${PIPESTATUS[0]} == 0 ]]
}
tables() { q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$1'"; }
require_empty() { [[ "$(tables "$1")" == 0 ]] || { echo "停止：$1 非空" >&2; exit 1; }; }

precheck() {
  for db in "${DBS[@]}"; do echo "$db: $(tables "$db") 张表"; done
  echo "card BIN 表: $(q mor_card_v2e2e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_card_v2e2e' AND table_name LIKE 't_card_bin%'")（应为 0）"
  echo "channel callback_definition_json 列: $(q mor_channel_connector_v2e2e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mor_channel_connector_v2e2e' AND table_name='t_channel' AND column_name='callback_definition_json'")（应为 0）"
  echo "channel 回调表行数: $(q mor_channel_connector_v2e2e "SELECT (SELECT COUNT(*) FROM t_channel_callback)+(SELECT COUNT(*) FROM t_channel_callback_item)")（应为 0）"
  echo "channel PAYERMAX/PAYERMAX_MOCK: $(q mor_channel_connector_v2e2e "SELECT GROUP_CONCAT(channel_code) FROM t_channel WHERE channel_code IN ('PAYERMAX','PAYERMAX_MOCK') AND channel_type='PAYMENT'")"
  echo "platform 块 132/133 已占用行: $(q mor_platform_v2e2e "SELECT (SELECT COUNT(*) FROM t_permission WHERE permission_id BETWEEN 900000000000132001 AND 900000000000133999)+(SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000132001 AND 900000000000133999)+(SELECT COUNT(*) FROM t_gateway_target WHERE target_id BETWEEN 900000000000132001 AND 900000000000133999 OR target_service_code='channel-connector-service')+(SELECT COUNT(*) FROM t_file_scan_policy WHERE policy_code='CARD_BIN_CSV')")（应为 0）"
  echo "platform t_country_region: $(q mor_platform_v2e2e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_platform_v2e2e' AND table_name='t_country_region'")（应为 0）"
}

backup() {
  local dir="$MAIN/local-environment/e2e/backup/$(date -u +%Y%m%dT%H%M%SZ)-card3ds-stage5"
  mkdir -p "$dir"; chmod 700 "$dir"
  for db in "${DBS[@]}"; do
    mysqldump -h127.0.0.1 -P13306 -uroot --default-character-set=utf8mb4 --single-transaction --no-tablespaces \
      --skip-triggers --set-gtid-purged=OFF --routines "$db" > "$dir/$db.sql" 2> "$dir/$db.err"
    echo "$(date -u +%FT%TZ) backup $db -> ${dir#$MAIN/}/$db.sql $(wc -c < "$dir/$db.sql") bytes, sha256 $(shasum -a 256 "$dir/$db.sql" | cut -c1-16)" | tee -a "$LOG"
  done
}

case "${1:-}" in
  precheck) precheck ;;
  backup) backup ;;
  schema-payment)
    require_empty mor_payment_v2e2e
    run mor_payment_v2e2e "$INT/paytech/payment/docs/database/sql/payment-schema.sql" ;;
  schema-risk)
    require_empty mor_risk_decision_v2e2e
    run mor_risk_decision_v2e2e "$INT/paytech/risk-decision/docs/database/sql/risk-decision-schema.sql"
    run mor_risk_decision_v2e2e "$INT/paytech/risk-decision/docs/database/sql/risk-decision-initial-data.sql" ;;
  card)
    run mor_card_v2e2e "$RBIN/card/ddl.sql"
    run mor_card_v2e2e "$RBIN/card/dml.sql" ;;
  channel)
    run mor_channel_connector_v2e2e "$R3DS/channel-connector/ddl.sql"
    run mor_channel_connector_v2e2e "$R3DS/channel-connector/dml.sql" ;;
  platform)
    run mor_platform_v2e2e "$RBIN/platform-support/ddl.sql"
    run mor_platform_v2e2e "$RBIN/platform-support/dml.sql"
    run mor_platform_v2e2e "$R3DS/platform-support/dml.sql" ;;
  activate-blocks)
    tmp=$(mktemp); cat > "$tmp" <<'SQL'
START TRANSACTION;
UPDATE t_role_permission SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE role_permission_id IN (900000000000132201,900000000000132202) AND status='REVOKED';
UPDATE t_console_resource SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE resource_id IN (900000000000132301,900000000000132302) AND status='DISABLED';
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE route_id BETWEEN 900000000000132401 AND 900000000000132409 AND status='DISABLED';
UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE route_id = 900000000000133401 AND status='DISABLED';
SELECT 'active 132 grants', COUNT(*) FROM t_role_permission WHERE role_permission_id IN (900000000000132201,900000000000132202) AND status='ACTIVE';
SELECT 'active 132 menus', COUNT(*) FROM t_console_resource WHERE resource_id IN (900000000000132301,900000000000132302) AND status='ACTIVE';
SELECT 'active 132/133 routes', COUNT(*) FROM t_gateway_route WHERE (route_id BETWEEN 900000000000132401 AND 900000000000132409 OR route_id=900000000000133401) AND status='ACTIVE';
COMMIT;
SQL
    run mor_platform_v2e2e "$tmp"; rm -f "$tmp" ;;
  verify)
    echo "card: $(q mor_card_v2e2e "SELECT CONCAT((SELECT COUNT(*) FROM t_card_bin_library_version),' 版本 / ',(SELECT COUNT(*) FROM t_card_bin_range),' 号段 / ',(SELECT COUNT(*) FROM t_card_bin_library_activation),' 激活 / ACTIVE=',(SELECT GROUP_CONCAT(version_no) FROM t_card_bin_library_version WHERE active_slot=1))")"
    echo "channel: $(q mor_channel_connector_v2e2e "SELECT GROUP_CONCAT(CONCAT(channel_code,':',JSON_UNQUOTE(JSON_EXTRACT(callback_definition_json,'\$.signature.algorithm')))) FROM t_channel WHERE callback_definition_json IS NOT NULL")"
    echo "platform: $(q mor_platform_v2e2e "SELECT CONCAT((SELECT COUNT(*) FROM t_country_region),' 国家 / CSV 策略 ',(SELECT COUNT(*) FROM t_file_scan_policy WHERE policy_code='CARD_BIN_CSV'),' / 块132 路由 ',(SELECT COUNT(*) FROM t_gateway_route WHERE route_id BETWEEN 900000000000132401 AND 900000000000132409),' / 块132 头 ',(SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000132501 AND 900000000000132599),' / 块132 guard ',(SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000132701 AND 900000000000132899),' / 块133 目标 ',(SELECT COUNT(*) FROM t_gateway_target WHERE target_id BETWEEN 900000000000133801 AND 900000000000133899),' 路由 ',(SELECT COUNT(*) FROM t_gateway_route WHERE route_id=900000000000133401),' 头 ',(SELECT COUNT(*) FROM t_gateway_route_header WHERE route_header_id BETWEEN 900000000000133501 AND 900000000000133599),' guard ',(SELECT COUNT(*) FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN 900000000000133701 AND 900000000000133799))")"
    echo "payment: $(tables mor_payment_v2e2e) 张表；token 卡属性列 $(q mor_payment_v2e2e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mor_payment_v2e2e' AND table_name='t_payment_instrument_token' AND column_name IN ('card_brand','funding_type','card_level','card_product','issuer_country','issuer_region','bin_library_version_no','bin_match_length')")"
    echo "risk: $(tables mor_risk_decision_v2e2e) 张表；评估快照卡属性列 $(q mor_risk_decision_v2e2e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mor_risk_decision_v2e2e' AND table_name='t_transaction_risk_assessment' AND column_name IN ('issuer_country_code','card_brand','funding_type')")；点版本 220003 $(q mor_risk_decision_v2e2e "SELECT COUNT(*) FROM t_decision_point_version WHERE point_version_id=220003")；变量 93014-16 $(q mor_risk_decision_v2e2e "SELECT COUNT(*) FROM t_risk_variable WHERE variable_id BETWEEN 900000000000093014 AND 900000000000093016")" ;;
  *) sed -n '2,9p' "$0"; exit 2 ;;
esac
