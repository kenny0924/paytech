#!/usr/bin/env bash
# 工作流附件内嵌预览跟进 · mor-test SQL 执行包装（阶段 7，主会话执行；root；客户端 utf8mb4；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   precheck                       ← ../sql/00-precheck.sql（只读）
#   query-param-dry / query-param  ← ../sql/10-route-query-param.sql（dry 不提交：断开即回滚；非 dry 追加 COMMIT；guard 不符则报错停止）
#   postcheck                      ← ../sql/99-postcheck.sql（只读）
#   rollback-dry / rollback        ← ../sql/90-rollback.sql（同上）
# 每步输出 <备份目录>/sql-<步骤>.out；mysql 遇错即停（非零退出）。口令只经 MYSQL_PWD 传入容器，不回显。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_platform-full.sql" ]] || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
commit=0
case "$step" in
  precheck) db=mysql; file="$here/sql/00-precheck.sql" ;;
  query-param-dry) db=mor_platform; file="$here/sql/10-route-query-param.sql" ;;
  query-param) db=mor_platform; file="$here/sql/10-route-query-param.sql"; commit=1 ;;
  postcheck) db=mysql; file="$here/sql/99-postcheck.sql" ;;
  rollback-dry) db=mor_platform; file="$here/sql/90-rollback.sql" ;;
  rollback) db=mor_platform; file="$here/sql/90-rollback.sql"; commit=1 ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
umask 077
{ cat "$file"; [[ $commit == 1 ]] && printf '\nCOMMIT;\nSELECT "committed" AS result;\n' || true; } \
  | docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
cat "$dir/sql-$step.out"
