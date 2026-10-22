#!/usr/bin/env bash
# 3DS 全链路 + 卡 BIN 库合并发布 · mor-test SQL 执行包装（阶段 7；root；客户端 utf8mb4；输出写入备份目录）。阶段 6 编写，未执行。
# 用法：apply_sql.sh <备份目录> <步骤>；mysql 遇错即停（非零退出），每步输出 <备份目录>/sql-<步骤>.out。
#   precheck / postcheck                     ← sql/00-precheck.sql / sql/99-postcheck.sql（只读）
#   risk                                     ← sql/10（mor_risk_decision：3 列 + 220003 + 变量）
#   payment-token / payment-consumed         ← sql/11、sql/12（mor_payment）
#   cc-ddl / cc-callback-def                 ← sql/13、sql/21（mor_channel_connector；21 在新 channel-connector 之后）
#   platform-country-ddl / platform-country-seed ← sql/14、sql/15（mor_platform）
#   card-ddl / card-seed                     ← sql/16、sql/17（mor_card）
#   platform-scan-policy                     ← sql/20（mor_platform；新 Platform 之后）
#   register132 / register133                ← sql/22、sql/23（mor_platform；休眠注册；23 在新 Gateway 之后）
#   activate132[-dry] / activate133[-dry]    ← sql/30、sql/31（无 COMMIT；非 -dry 时追加 COMMIT）
#   deactivate[-dry]                         ← sql/90（回滚第一步）
#   rollback-platform-data[-dry]             ← sql/91（一般不执行）
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
for db in mor_card mor_payment mor_channel_connector mor_risk_decision mor_platform; do
  [[ -s "$dir/$db-full.sql" ]] || { echo "backup missing: $dir/$db-full.sql" >&2; exit 1; }
done
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
commit=""
case "$step" in
  precheck)               db=mysql;                 file=00-precheck.sql ;;
  postcheck)              db=mysql;                 file=99-postcheck.sql ;;
  risk)                   db=mor_risk_decision;     file=10-mor_risk_decision-card-attribute-inputs.sql ;;
  payment-token)          db=mor_payment;           file=11-mor_payment-instrument-token-card-attributes.sql ;;
  payment-consumed)       db=mor_payment;           file=12-mor_payment-consumed-event-tables.sql ;;
  cc-ddl)                 db=mor_channel_connector; file=13-mor_channel_connector-callback-definition-ddl.sql ;;
  platform-country-ddl)   db=mor_platform;          file=14-mor_platform-country-region-ddl.sql ;;
  platform-country-seed)  db=mor_platform;          file=15-mor_platform-country-region-seed.sql ;;
  card-ddl)               db=mor_card;              file=16-mor_card-bin-library-ddl.sql ;;
  card-seed)              db=mor_card;              file=17-mor_card-bin-library-seed.sql ;;
  platform-scan-policy)   db=mor_platform;          file=20-mor_platform-card-bin-csv-scan-policy.sql ;;
  cc-callback-def)        db=mor_channel_connector; file=21-mor_channel_connector-callback-definition-dml.sql ;;
  register132)            db=mor_platform;          file=22-mor_platform-registration-block132.sql ;;
  register133)            db=mor_platform;          file=23-mor_platform-registration-block133.sql ;;
  activate132|activate132-dry) db=mor_platform;     file=30-activate-block132.sql ;;
  activate133|activate133-dry) db=mor_platform;     file=31-activate-block133.sql ;;
  deactivate|deactivate-dry)   db=mor_platform;     file=90-rollback-deactivate.sql ;;
  rollback-platform-data|rollback-platform-data-dry) db=mor_platform; file=91-rollback-platform-data.sql ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
case "$step" in activate132|activate133|deactivate|rollback-platform-data) commit=$'\nCOMMIT;\nSELECT '"'"'committed'"'"' AS result;\n' ;; esac
umask 077
{ cat "$here/sql/$file"; printf '%s' "$commit"; } \
  | docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
cat "$dir/sql-$step.out"
