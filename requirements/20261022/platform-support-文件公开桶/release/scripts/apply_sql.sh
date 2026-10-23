#!/usr/bin/env bash
# 文件公开桶 · mor-test SQL 执行包装（阶段 7；root；客户端 utf8mb4；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   precheck          ← ../sql/00-precheck.sql（只读）
#   platform-ddl      ← ../sql/01-platform-ddl.sql（mor_platform；旧 Platform 运行时可执行）
#   platform-backfill ← ../sql/02-platform-backfill.sql（mor_platform；可重跑：DDL 后一次，新 Platform 起来后再一次）
#   block134-reg      ← ../sql/11-registration-block134.sql（mor_platform；157 行休眠注册，任何镜像组合下无影响）
#   reg-check         ← ../sql/98-registration-check.sql（只读；block134-reg 之后）
#   block130-path     ← ../sql/10-block130-route-path.sql（mor_platform；新 ops-bff 与新 Platform 都在线后执行）
#   activate          ← ../test-activate.sql（mor_platform；期望 12 / 2 / 2 / 1）
#   postcheck         ← ../sql/99-postcheck.sql（只读）
#   deactivate        ← ../test-deactivate.sql（回滚用；期望 12 / 2 / 2 / 1）
#   rollback-path     ← ../sql/90-rollback-block130-route-path.sql（回滚用；只在 ops-web 与 ops-bff 都回滚时执行）
# 每步输出 <备份目录>/sql-<步骤>.out；mysql 遇错即停（非零退出）。容器按完整名称 mor-test-mysql-1 指定。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_platform_files.sql" && -s "$dir/mor_platform_registration.sql" ]] \
  || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
umask 077
case "$step" in
  precheck) db=mysql; file="$here/sql/00-precheck.sql" ;;
  platform-ddl) db=mor_platform; file="$here/sql/01-platform-ddl.sql" ;;
  platform-backfill) db=mor_platform; file="$here/sql/02-platform-backfill.sql" ;;
  block134-reg) db=mor_platform; file="$here/sql/11-registration-block134.sql" ;;
  reg-check) db=mysql; file="$here/sql/98-registration-check.sql" ;;
  block130-path) db=mor_platform; file="$here/sql/10-block130-route-path.sql" ;;
  activate) db=mor_platform; file="$here/test-activate.sql" ;;
  postcheck) db=mysql; file="$here/sql/99-postcheck.sql" ;;
  deactivate) db=mor_platform; file="$here/test-deactivate.sql" ;;
  rollback-path) db=mor_platform; file="$here/sql/90-rollback-block130-route-path.sql" ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
out="$dir/sql-$step-$(date -u +%H%M%SZ).out"
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  < "$file" > "$out" 2>&1 || { cat "$out" >&2; exit 1; }
cat "$out"
