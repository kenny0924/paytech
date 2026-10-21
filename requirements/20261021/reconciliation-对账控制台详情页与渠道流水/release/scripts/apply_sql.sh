#!/usr/bin/env bash
# 对账控制台详情页与渠道流水 · mor-test SQL 执行包装（阶段 7；root；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   precheck      ← ../sql/00-precheck.sql（只读）
#   platform-reg  ← ../../db/platform-support/registration-dml.sql（mor_platform 块 126，38 行休眠，自带事务；
#                   与 platform-support-initial-data.sql 末尾块 126 逐行一致，阶段 6 已 diff 核对）
#   platform-act  ← ../../db/platform-support/test-activate.sql（3 / 1 / 1）
#   platform-deact← ../../db/platform-support/test-deactivate.sql（回滚激活，3 / 1 / 1）
#   platform-unreg← ../sql/rollback-unregister.sql（回滚注册：删块 126 的 38 行；须先 deact）
#   postcheck     ← ../sql/99-postcheck.sql（只读）
# 每步输出 <备份目录>/sql-<步骤>.out；mysql 遇错即停（非零退出）。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
req="$(cd "$here/.." && pwd)"
repo="$(cd "$req/../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_platform_registration.sql" ]] || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
case "$step" in
  precheck) db=mysql; file="$here/sql/00-precheck.sql" ;;
  platform-reg) db=mor_platform; file="$req/db/platform-support/registration-dml.sql" ;;
  platform-act) db=mor_platform; file="$req/db/platform-support/test-activate.sql" ;;
  platform-deact) db=mor_platform; file="$req/db/platform-support/test-deactivate.sql" ;;
  platform-unreg) db=mor_platform; file="$here/sql/rollback-unregister.sql" ;;
  postcheck) db=mysql; file="$here/sql/99-postcheck.sql" ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
umask 077
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  < "$file" > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
cat "$dir/sql-$step.out"
