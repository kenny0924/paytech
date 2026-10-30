#!/usr/bin/env bash
# AC-08a：删除 / 按备份恢复 ledger 规则行（mor_ledger_v2e2e）。
#   集合 cp（默认）= 5301、5302（CLIENT_PRINCIPAL × TRANSACTION_OE），备份 migration/rule-lines-5301-5302.backup.sql
#   集合 sc        = 5313、5314（PLATFORM_SERVICE_CHARGE × TRANSACTION_OE），备份 migration/rule-lines-5313-5314.backup.sql
# 用法：t6_rules.sh remove|restore|status [cp|sc]
set -euo pipefail
cd "$(dirname "$0")"
set -a; . /Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local; set +a
M() { MYSQL_PWD=$MOR_MYSQL_ROOT_PASSWORD docker exec -i -e MYSQL_PWD mor-local-mysql-1 mysql -uroot -N -B mor_ledger_v2e2e "$@"; }
case "${2:-cp}" in
  cp) IDS="5301,5302"; FILE=migration/rule-lines-5301-5302.backup.sql;;
  sc) IDS="5313,5314"; FILE=migration/rule-lines-5313-5314.backup.sql;;
esac
case "$1" in
  remove) M -e "DELETE FROM t_accounting_rule_line WHERE id IN ($IDS)";;
  restore) [ "$(M -e "SELECT COUNT(*) FROM t_accounting_rule_line WHERE id IN ($IDS)")" = 0 ] && M < "$FILE";;
esac
echo "$1 ${2:-cp}: rule lines $(M -e 'SELECT COUNT(*) FROM t_accounting_rule_line'), ($IDS) present $(M -e "SELECT COUNT(*) FROM t_accounting_rule_line WHERE id IN ($IDS)")"
