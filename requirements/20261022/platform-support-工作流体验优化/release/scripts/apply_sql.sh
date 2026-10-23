#!/usr/bin/env bash
# 工作流体验优化 · mor-test SQL 执行包装（阶段 7，主会话执行；root；客户端 utf8mb4；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   precheck                                 ← sql/00-precheck.sql（只读）
#   status-dry / status                      ← ../sql/10-action-run-status.sql（停机窗口内：旧 Platform 已停、新 Platform 未起）
#   reg-dry / reg                            ← ../sql/20-registration.sql（三个新镜像都起来之后）
#   postcheck                                ← sql/99-postcheck.sql（只读）
#   rollback-status-dry / rollback-status    ← sql/90-rollback-status.sql（停机窗口内：新 Platform 已停、旧 Platform 未起）
#   rollback-reg-dry / rollback-reg          ← sql/91-rollback-registration.sql
# *-dry 不提交：断开即回滚（10 / 90 的第 1 步注释为 DDL，dry 也会生效，可重复执行、不影响行为）；非 dry 追加 COMMIT。
# 所有改数文件都有 DATABASE() = 'mor_platform' 守卫与 guard 行：不符时 guard 语句报错，mysql 非零退出，未提交事务随断开回滚。
# 每步输出 <备份目录>/sql-<步骤>.out；口令只经 MYSQL_PWD 传入容器，不回显。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_platform-full.sql" ]] || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
commit=0
case "$step" in
  precheck) db=mysql; file="$here/sql/00-precheck.sql" ;;
  status-dry) db=mor_platform; file="$here/../sql/10-action-run-status.sql" ;;
  status) db=mor_platform; file="$here/../sql/10-action-run-status.sql"; commit=1 ;;
  reg-dry) db=mor_platform; file="$here/../sql/20-registration.sql" ;;
  reg) db=mor_platform; file="$here/../sql/20-registration.sql"; commit=1 ;;
  postcheck) db=mysql; file="$here/sql/99-postcheck.sql" ;;
  rollback-status-dry) db=mor_platform; file="$here/sql/90-rollback-status.sql" ;;
  rollback-status) db=mor_platform; file="$here/sql/90-rollback-status.sql"; commit=1 ;;
  rollback-reg-dry) db=mor_platform; file="$here/sql/91-rollback-registration.sql" ;;
  rollback-reg) db=mor_platform; file="$here/sql/91-rollback-registration.sql"; commit=1 ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
[[ -f "$file" ]] || { echo "missing $file" >&2; exit 1; }
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
umask 077
{ cat "$file"; [[ $commit == 1 ]] && printf '\nCOMMIT;\nSELECT "committed" AS result;\n' || true; } \
  | docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
cat "$dir/sql-$step.out"
