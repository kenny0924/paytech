#!/usr/bin/env bash
# 文件多存储并存 · mor-test SQL 执行包装（阶段 7；root；客户端 utf8mb4；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   precheck        ← ../sql/00-precheck.sql（只读）
#   vault           ← ../sql/02-vault-file-storage-credential.sql（mor_vault，1 类型 + 2 规则 + 1 策略变更，自带 USE 与事务）
#   platform-reg    ← ../sql/10-registration-block130.sql（mor_platform 块 130，152 行休眠，自带事务）
#   platform-ddl    ← ../sql/01-platform-file-storage.sql（mor_platform：3 表、加列、回填、NOT NULL、放宽 5 列；紧接着换 Platform）
#   platform-fix    ← ../sql/11-check-route-content-type.sql（桶检查路由补 content-type，2 行）
#   platform-act    ← ../test-activate.sql（12 / 2 / 2 / 1）
#   platform-deact  ← ../test-deactivate.sql（回滚，12 / 2 / 2 / 1）
#   postcheck       ← ../sql/99-postcheck.sql（只读）
#   storage-check   ← ../sql/98-storage-registration-check.sql（只读，存储登记之后）
#   rollback-relax  ← ../sql/90-rollback-relax-new-columns.sql（只在镜像回滚到旧 Platform 时执行）
# 每步输出 <备份目录>/sql-<步骤>.out；mysql 遇错即停（非零退出）。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_platform_files.sql" && -s "$dir/mor_vault_policy.sql" ]] \
  || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
case "$step" in
  precheck) db=mysql; file="$here/sql/00-precheck.sql" ;;
  vault) db=mor_vault; file="$here/sql/02-vault-file-storage-credential.sql" ;;
  platform-reg) db=mor_platform; file="$here/sql/10-registration-block130.sql" ;;
  platform-ddl) db=mor_platform; file="$here/sql/01-platform-file-storage.sql" ;;
  platform-fix) db=mor_platform; file="$here/sql/11-check-route-content-type.sql" ;;
  platform-act) db=mor_platform; file="$here/test-activate.sql" ;;
  platform-deact) db=mor_platform; file="$here/test-deactivate.sql" ;;
  postcheck) db=mysql; file="$here/sql/99-postcheck.sql" ;;
  storage-check) db=mysql; file="$here/sql/98-storage-registration-check.sql" ;;
  rollback-relax) db=mor_platform; file="$here/sql/90-rollback-relax-new-columns.sql" ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
umask 077
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  < "$file" > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
cat "$dir/sql-$step.out"
