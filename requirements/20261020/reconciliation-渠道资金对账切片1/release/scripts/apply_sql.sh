#!/usr/bin/env bash
# 对账切片 1 · mor-test SQL 执行包装（阶段 7；root；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   billing     ← ../../db/billing/ddl.sql（mor_billing）
#   recon-drop  ← ../sql/01-reconciliation-drop-old-tables.sql（mor_reconciliation；先断言 18 张旧表为空）
#   recon-ddl   ← ../../db/reconciliation/ddl.sql（mor_reconciliation，15 张表）
#   vault       ← ../../db/vault/dml.sql（mor_vault，3 行）
#   platform-reg← ../../db/platform-support/registration-dml.sql（mor_platform 块 123，512 行，自带事务）
#   platform-act← ../../db/platform-support/test-activate.sql（38 / 10 / 3 / 1）
#   platform-deact ← ../../db/platform-support/test-deactivate.sql（回滚）
#   postcheck   ← ../sql/99-postcheck.sql（只读）
# 每步输出 <备份目录>/sql-<步骤>.out；mysql 遇错即停（非零退出）。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
req="$(cd "$here/.." && pwd)"
repo="$(cd "$req/../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_reconciliation.sql" ]] || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
case "$step" in
  billing) db=mor_billing; file="$req/db/billing/ddl.sql" ;;
  recon-drop) db=mor_reconciliation; file="$here/sql/01-reconciliation-drop-old-tables.sql" ;;
  recon-ddl) db=mor_reconciliation; file="$req/db/reconciliation/ddl.sql" ;;
  vault) db=mor_vault; file="$req/db/vault/dml.sql" ;;
  platform-reg) db=mor_platform; file="$req/db/platform-support/registration-dml.sql" ;;
  platform-act) db=mor_platform; file="$req/db/platform-support/test-activate.sql" ;;
  platform-deact) db=mor_platform; file="$req/db/platform-support/test-deactivate.sql" ;;
  postcheck) db=mysql; file="$here/sql/99-postcheck.sql" ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
umask 077
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  < "$file" > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
cat "$dir/sql-$step.out"
