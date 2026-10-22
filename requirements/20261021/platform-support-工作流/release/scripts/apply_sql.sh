#!/usr/bin/env bash
# 工作流 · mor-test SQL 执行包装（阶段 7，主会话执行；root；客户端 utf8mb4；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   precheck          ← ../sql/00-precheck.sql（只读）
#   ddl               ← ../sql/01-workflow-tables.sql（mor_platform：10 张空表；DDL 隐式提交）
#   ddl-comment       ← ../sql/02-workflow-comment.sql（两列注释）
#   register          ← ../sql/10-registration-block127.sql（块 127，416 行休眠，自带事务与 COMMIT）
#   activate-dry      ← ../test-activate.sql（不提交：断开即回滚，只看 32 / 10 / 7 / 1）
#   activate          ← ../test-activate.sql + COMMIT（guard 不符则报错停止，事务回滚）
#   postcheck         ← ../sql/99-postcheck.sql（只读）
#   deactivate-dry / deactivate  ← ../sql/90-rollback-deactivate.sql（回滚，同上）
#   remove-block127-dry / remove-block127 ← ../sql/91-rollback-remove-block127.sql（可选）
#   drop-tables       ← ../sql/92-rollback-drop-workflow-tables.sql（可选，不可回滚）
# 每步输出 <备份目录>/sql-<步骤>.out；mysql 遇错即停（非零退出）。口令只经 MYSQL_PWD 传入容器，不回显。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_platform-full.sql" ]] || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
commit=0
case "$step" in
  precheck) db=mysql; file="$here/sql/00-precheck.sql" ;;
  ddl) db=mor_platform; file="$here/sql/01-workflow-tables.sql" ;;
  ddl-comment) db=mor_platform; file="$here/sql/02-workflow-comment.sql" ;;
  register) db=mor_platform; file="$here/sql/10-registration-block127.sql" ;;
  activate-dry) db=mor_platform; file="$here/test-activate.sql" ;;
  activate) db=mor_platform; file="$here/test-activate.sql"; commit=1 ;;
  postcheck) db=mysql; file="$here/sql/99-postcheck.sql" ;;
  deactivate-dry) db=mor_platform; file="$here/sql/90-rollback-deactivate.sql" ;;
  deactivate) db=mor_platform; file="$here/sql/90-rollback-deactivate.sql"; commit=1 ;;
  remove-block127-dry) db=mor_platform; file="$here/sql/91-rollback-remove-block127.sql" ;;
  remove-block127) db=mor_platform; file="$here/sql/91-rollback-remove-block127.sql"; commit=1 ;;
  drop-tables) db=mor_platform; file="$here/sql/92-rollback-drop-workflow-tables.sql" ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
umask 077
{ cat "$file"; [[ $commit == 1 ]] && printf '\nCOMMIT;\nSELECT "committed" AS result;\n' || true; } \
  | docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
cat "$dir/sql-$step.out"
