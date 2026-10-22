#!/usr/bin/env bash
# 文件多存储并存第二段 · mor-test SQL 执行包装（阶段 7；root；客户端 utf8mb4；输出写入备份目录）。
# 用法：apply_sql.sh <备份目录> <步骤>
#   precheck               ← ../sql/00-precheck.sql（只读）
#   cct-step1              ← ../sql/01-cct-file-address-step1.sql（mor_client_master；旧 CCT 运行时可执行）
#   postcheck              ← ../sql/99-postcheck.sql（只读；第 1 步与五个镜像替换之后）
#   cct-rollback           ← ../sql/90-rollback-cct-file-id.sql（只在把 CCT 换回旧镜像前执行）
#   cct-step2              ← ../sql/02-cct-file-address-step2.sql（mor_client_master；另行授权，最后执行）
#   platform-owner-backup  ← mysqldump mor_platform 的 t_file_upload、t_file_owner_ref 到 <备份目录>/before-owner-removal-*.sql
#   platform-owner-removal ← ../sql/03-platform-file-owner-removal.sql（mor_platform；另行授权，最后执行；要求先做上一步）
#   final-check            ← ../sql/98-final-check.sql（只读；两条删除 DDL 之后）
# 每步输出 <备份目录>/sql-<步骤>.out；mysql 遇错即停（非零退出）。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
dir="${1:?backup dir}"; step="${2:?step}"
[[ -d "$dir" && -s "$dir/mor_client_master_application_document.sql" && -s "$dir/mor_platform_files.sql" ]] \
  || { echo "backup dir missing or incomplete: $dir" >&2; exit 1; }
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
umask 077
if [[ "$step" == platform-owner-backup ]]; then
  out="$dir/before-owner-removal-$(date -u +%Y%m%dT%H%M%SZ).sql"
  docker exec -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysqldump -uroot --single-transaction --set-gtid-purged=OFF \
    --no-tablespaces --default-character-set=utf8mb4 mor_platform t_file_upload t_file_owner_ref > "$out"
  [[ -s "$out" ]] || { echo "empty dump: $out" >&2; exit 1; }
  shasum -a 256 "$out"; exit 0
fi
case "$step" in
  precheck) db=mysql; file="$here/sql/00-precheck.sql" ;;
  cct-step1) db=mor_client_master; file="$here/sql/01-cct-file-address-step1.sql" ;;
  postcheck) db=mysql; file="$here/sql/99-postcheck.sql" ;;
  cct-rollback) db=mor_client_master; file="$here/sql/90-rollback-cct-file-id.sql" ;;
  cct-step2) db=mor_client_master; file="$here/sql/02-cct-file-address-step2.sql" ;;
  platform-owner-removal)
    ls "$dir"/before-owner-removal-*.sql >/dev/null 2>&1 || { echo "run platform-owner-backup first" >&2; exit 1; }
    db=mor_platform; file="$here/sql/03-platform-file-owner-removal.sql" ;;
  final-check) db=mysql; file="$here/sql/98-final-check.sql" ;;
  *) echo "unknown step: $step" >&2; exit 2 ;;
esac
docker exec -i -e MYSQL_PWD="$root_pw" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 -t -v "$db" \
  < "$file" > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
cat "$dir/sql-$step.out"
