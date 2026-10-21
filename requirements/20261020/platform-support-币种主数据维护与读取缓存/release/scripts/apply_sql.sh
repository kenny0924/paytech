#!/usr/bin/env bash
# 币种主数据 · mor-test SQL 执行包装（阶段 7；root；客户端 utf8mb4；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   precheck        ← ../sql/00-precheck.sql（只读）
#   platform-alter  ← ../sql/01-platform-currency-spec-version.sql（mor_platform，加 version 列与表注释）
#   platform-reg    ← ../sql/10-registration-block125.sql（mor_platform 块 125，54 行休眠，自带事务）
#   platform-act    ← ../test-activate.sql（4 / 2 / 2 / 1）
#   platform-deact  ← ../test-deactivate.sql（回滚，4 / 2 / 2 / 1）
#   postcheck       ← ../sql/99-postcheck.sql（只读）
#   ledger-drop     ← ../sql/90-ledger-drop-currency-spec-copy.sql（mor_ledger；最后一步，另行授权）
#   billing-drop    ← ../sql/91-billing-drop-currency-spec-copy.sql（mor_billing；最后一步，另行授权）
# 每步输出 <备份目录>/sql-<步骤>.out；mysql 遇错即停（非零退出）。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_platform_currency_spec.sql" && -s "$dir/mor_ledger_currency_spec.sql" ]] \
  || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
case "$step" in
  precheck) db=mysql; file="$here/sql/00-precheck.sql" ;;
  platform-alter) db=mor_platform; file="$here/sql/01-platform-currency-spec-version.sql" ;;
  platform-reg) db=mor_platform; file="$here/sql/10-registration-block125.sql" ;;
  platform-act) db=mor_platform; file="$here/test-activate.sql" ;;
  platform-deact) db=mor_platform; file="$here/test-deactivate.sql" ;;
  postcheck) db=mysql; file="$here/sql/99-postcheck.sql" ;;
  ledger-drop) db=mor_ledger; file="$here/sql/90-ledger-drop-currency-spec-copy.sql" ;;
  billing-drop) db=mor_billing; file="$here/sql/91-billing-drop-currency-spec-copy.sql" ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
umask 077
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  < "$file" > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
cat "$dir/sql-$step.out"
