#!/usr/bin/env bash
# 任务卡 P1-stage5 B 节：本机数据迁移演练（只在本机 MySQL 容器 mor-local-mysql-1 上执行，root 口令只经环境变量传入，不打印）。
#
#   stage5_migrate.sh precheck     只读：两库表数、10 表行数、channel_connector_app 库级授权、Platform 是否有 RUNNING 运行与待执行补偿
#   stage5_migrate.sh backup       B1：mysqldump 备份 mor_platform_v2e2e 的 10 张表与 mor_channel_connector_v2e2e.t_channel_config_change（结构 + 数据）
#   stage5_migrate.sh ddl          B2：在 mor_channel_connector_v2e2e 执行增量 DDL（10 张表 + change_detail）
#   stage5_migrate.sh import       B3：mysqldump --no-create-info --skip-triggers --complete-insert 导出 10 张表并导入 mor_channel_connector_v2e2e
#   stage5_migrate.sh verify       B4：逐表行数与 CHECKSUM TABLE；全部 JSON 列与脚本源码列逐行 SHA-256（两边按主键对齐比较）
#   stage5_migrate.sh droptrial    B5：临时库 mor_ccu_stage5_droptrial 建 10 张空表 → 执行库名替换后的 drop-integration.sql → 删除临时库
#   stage5_migrate.sh autodb-check B6 前置：mor_channel_connector 只缺 10 张表与 change_detail 列
#   stage5_migrate.sh autodb-ddl   B6：在 mor_channel_connector 执行同一增量 DDL（不导入数据）
# 每条变更语句的库名与 UTC 时间追加到 verification/stage5-sql-log.txt。
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MAIN="/Users/kenny/Desktop/Workspace/Code-MoR"
WORKTREE="/Users/kenny/Desktop/Workspace/Code-MoR-ccu"
DDL="$WORKTREE/paytech/channel-connector/docs/database/sql/changes/2026-10-20-protocol-tables.sql"
DROP_SQL="$HERE/../db/platform-support-service/drop-integration.sql"
SQL_LOG="$HERE/stage5-sql-log.txt"
SRC=mor_platform_v2e2e
DST=mor_channel_connector_v2e2e
TRIAL=mor_ccu_stage5_droptrial
TABLES=(t_standard_api t_api_field t_integration t_integration_api t_integration_script t_integration_standard_api
  t_integration_verify_account t_integration_verify_case t_integration_verify_run t_integration_verify_run_step)

set -a; . "$MAIN/local-environment/.env.local"; set +a
mysql_root() { docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysql -uroot --default-character-set=utf8mb4 "$@"; }
dump_root() { docker exec -i -e MYSQL_PWD="$MOR_MYSQL_ROOT_PASSWORD" mor-local-mysql-1 mysqldump -uroot --default-character-set=utf8mb4 "$@"; }
q() { echo "$2" | mysql_root -N -B "$1"; }
log_sql() { printf '%s\t%s\t%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$1" "$2" >> "$SQL_LOG"; }
in_list() { local IFS=,; echo "'${TABLES[*]//,/\',\'}'" | sed "s/,/','/g"; }
TABLE_IN="'$(IFS=,; echo "${TABLES[*]}" | sed "s/,/','/g")'"

precheck() {
  for db in $SRC $DST; do
    echo "$db 表数：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$db'")"
  done
  echo "$DST 已有 10 表：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$DST' AND table_name IN ($TABLE_IN)")"
  echo "$DST.t_channel_config_change.change_detail：$(q information_schema "SELECT COUNT(*) FROM columns WHERE table_schema='$DST' AND table_name='t_channel_config_change' AND column_name='change_detail'")"
  for t in "${TABLES[@]}"; do printf '%s %s\n' "$t" "$(q $SRC "SELECT COUNT(*) FROM $t")"; done
  echo "channel_connector_app 授权："; q mysql "SHOW GRANTS FOR 'channel_connector_app'@'%'" | grep -F "$DST" || echo "  缺少 $DST 库级授权"
  echo "Platform RUNNING 运行：$(q $SRC "SELECT COUNT(*) FROM t_integration_verify_run WHERE run_status='RUNNING'")"
  echo "Platform 待执行 INTEGRATION_VERIFY_RUN 补偿：$(q $SRC "SELECT COUNT(*) FROM t_compensation_event WHERE event_type='INTEGRATION_VERIFY_RUN' ")"; echo "  按状态：$(q $SRC "SELECT execute_status, COUNT(*) FROM t_compensation_event WHERE event_type='INTEGRATION_VERIFY_RUN' GROUP BY 1" | tr '\n' ' ')"
}

backup() {
  local dir; dir="$MAIN/local-environment/e2e/backup/$(date -u +%Y%m%dT%H%M%SZ)-ccu-stage5"
  mkdir -p "$dir"; chmod 700 "$dir"
  dump_root --single-transaction --set-gtid-purged=OFF --no-tablespaces --skip-triggers $SRC "${TABLES[@]}" > "$dir/$SRC-10-tables.sql"
  dump_root --single-transaction --set-gtid-purged=OFF --no-tablespaces --skip-triggers $DST t_channel_config_change > "$dir/$DST-t_channel_config_change.sql"
  chmod 600 "$dir"/*.sql
  for f in "$dir"/*.sql; do
    printf '%s  %s 字节  CREATE=%s  INSERT=%s  sha256=%s\n' "$(basename "$f")" "$(wc -c < "$f" | tr -d ' ')" \
      "$(grep -c '^CREATE TABLE' "$f")" "$(grep -c '^INSERT INTO' "$f")" "$(shasum -a 256 "$f" | cut -c1-16)"
  done
  echo "备份目录：$dir"
  echo "$dir" > "$HERE/.stage5-backup-dir"
}

ddl() {
  local present; present=$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$DST' AND table_name IN ($TABLE_IN)")
  [[ "$present" == 0 ]] || { echo "停止：$DST 已有 $present 张目标表"; exit 1; }
  { echo "USE $DST;"; cat "$DDL"; } | mysql_root
  log_sql "$DST" "执行 $(basename "$DDL")（ALTER t_channel_config_change + CREATE 10 表）"
  echo "$DST 表数：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$DST'")"
}

import_data() {
  local rows; rows=$(q $DST "SELECT (SELECT COUNT(*) FROM t_standard_api)+(SELECT COUNT(*) FROM t_integration)+(SELECT COUNT(*) FROM t_integration_verify_run)")
  [[ "$rows" == 0 ]] || { echo "停止：$DST 目标表已有数据"; exit 1; }
  local file; file="$(cat "$HERE/.stage5-backup-dir")/$SRC-10-tables-data.sql"
  dump_root --no-create-info --skip-triggers --complete-insert --single-transaction --set-gtid-purged=OFF --no-tablespaces \
    $SRC "${TABLES[@]}" > "$file"
  chmod 600 "$file"
  echo "导出：$(basename "$file")，INSERT 语句 $(grep -c '^INSERT INTO' "$file")，CREATE $(grep -c 'CREATE TABLE' "$file" || true)"
  mysql_root $DST < "$file"
  log_sql "$DST" "导入 $SRC 10 张表数据（mysqldump --no-create-info --skip-triggers --complete-insert）"
}

verify() {
  local out="$HERE/.stage5-verify.tsv" bad=0
  : > "$out"
  printf '%-32s %8s %8s %14s %14s\n' 表 源行数 目标行数 源CHECKSUM 目标CHECKSUM
  for t in "${TABLES[@]}"; do
    local a b ca cb
    a=$(q $SRC "SELECT COUNT(*) FROM $t"); b=$(q $DST "SELECT COUNT(*) FROM $t")
    ca=$(q $SRC "CHECKSUM TABLE $t" | cut -f2); cb=$(q $DST "CHECKSUM TABLE $t" | cut -f2)
    printf '%-32s %8s %8s %14s %14s %s\n' "$t" "$a" "$b" "$ca" "$cb" "$([[ "$a" == "$b" && "$ca" == "$cb" ]] && echo 一致 || echo 不一致)"
    [[ "$a" == "$b" && "$ca" == "$cb" ]] || bad=1
    printf 'TABLE\t%s\t%s\t%s\t%s\t%s\n' "$t" "$a" "$b" "$ca" "$cb" >> "$out"
  done
  # 逐行 SHA-256：全部 JSON 列与脚本源码列，按主键排序后整列比较
  local cols
  cols=$(q information_schema "SELECT table_name, column_name FROM columns WHERE table_schema='$SRC' AND table_name IN ($TABLE_IN) AND data_type IN ('json','text','mediumtext','longtext') ORDER BY table_name, ordinal_position")
  echo
  printf '%-32s %-22s %6s %6s %s\n' 表 列 行数 一致行 结论
  while IFS=$'\t' read -r t c; do
    local pk; pk=$(q information_schema "SELECT column_name FROM key_column_usage WHERE table_schema='$SRC' AND table_name='$t' AND constraint_name='PRIMARY'")
    local stmt="SELECT $pk, IFNULL(SHA2(CAST(\`$c\` AS CHAR), 256), 'NULL') FROM $t ORDER BY $pk"
    local sa sb
    sa=$(q $SRC "$stmt"); sb=$(q $DST "$stmt")
    local n match
    n=$(printf '%s\n' "$sa" | grep -c . || true)
    match=$(comm -12 <(printf '%s\n' "$sa" | sort) <(printf '%s\n' "$sb" | sort) | grep -c . || true)
    printf '%-32s %-22s %6s %6s %s\n' "$t" "$c" "$n" "$match" "$([[ "$sa" == "$sb" ]] && echo 一致 || echo 不一致)"
    [[ "$sa" == "$sb" ]] || bad=1
    printf 'SHA\t%s\t%s\t%s\t%s\t%s\n' "$t" "$c" "$n" "$match" "$([[ "$sa" == "$sb" ]] && echo OK || echo DIFF)" >> "$out"
  done <<< "$cols"
  return $bad
}

droptrial() {
  [[ -z "$(q information_schema "SELECT schema_name FROM schemata WHERE schema_name='$TRIAL'")" ]] || { echo "停止：临时库 $TRIAL 已存在"; exit 1; }
  q mysql "CREATE DATABASE $TRIAL CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci"
  log_sql "$TRIAL" "CREATE DATABASE"
  # 10 张空表：取增量 DDL 中的 CREATE TABLE 语句（不含 ALTER t_channel_config_change），结构与 Platform 原表一致
  { echo "USE $TRIAL; SET NAMES utf8mb4;"; awk '/^CREATE TABLE/{p=1} p{print} /;[[:space:]]*$/{p=0}' "$DDL"; } | mysql_root
  log_sql "$TRIAL" "CREATE 10 张空表（增量 DDL 的 CREATE TABLE 部分）"
  echo "试跑库表数（执行前）：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$TRIAL'")"
  # drop-integration.sql 不带库名（目标库在注释中为 mor_platform）：替换注释中的库名并以 USE 指定临时库
  { echo "USE $TRIAL;"; sed "s/mor_platform/$TRIAL/g" "$DROP_SQL"; } | mysql_root
  log_sql "$TRIAL" "执行 drop-integration.sql（库名替换为临时库）"
  echo "试跑库表数（执行后）：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$TRIAL'")"
  q mysql "DROP DATABASE $TRIAL"
  log_sql "$TRIAL" "DROP DATABASE"
  echo "临时库已删除：$(q information_schema "SELECT COUNT(*) FROM schemata WHERE schema_name='$TRIAL'") 个残留"
}

autodb_check() {
  local db=mor_channel_connector
  echo "$db 表数：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$db'")"
  echo "$db 已有 10 表：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$db' AND table_name IN ($TABLE_IN)")"
  echo "$db change_detail：$(q information_schema "SELECT COUNT(*) FROM columns WHERE table_schema='$db' AND table_name='t_channel_config_change' AND column_name='change_detail'")"
  # 与 v2e2e 迁移前的 18 张表逐列比较（表名、列名、类型、可空、默认值）；v2e2e 已执行 DDL 时排除 10 表与 change_detail
  local spec="SELECT table_name, column_name, column_type, is_nullable, IFNULL(column_default,'∅') FROM columns WHERE table_schema='%s' AND table_name NOT IN ($TABLE_IN) AND NOT (table_name='t_channel_config_change' AND column_name='change_detail') ORDER BY 1,2"
  # shellcheck disable=SC2059
  diff <(q information_schema "$(printf "$spec" "$db")") <(q information_schema "$(printf "$spec" "$DST")") \
    && echo "$db 18 张表结构与 $DST 一致（排除 10 表与 change_detail）"
}

autodb_ddl() {
  local db=mor_channel_connector
  local present; present=$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$db' AND table_name IN ($TABLE_IN)")
  [[ "$present" == 0 ]] || { echo "停止：$db 已有 $present 张目标表"; exit 1; }
  { echo "USE $db;"; cat "$DDL"; } | mysql_root
  log_sql "$db" "执行 $(basename "$DDL")（ALTER t_channel_config_change + CREATE 10 表，不导入数据）"
  echo "$db 表数：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$db'")"
}

case "${1:-}" in
  precheck) precheck ;; backup) backup ;; ddl) ddl ;; import) import_data ;; verify) verify ;;
  droptrial) droptrial ;; autodb-check) autodb_check ;; autodb-ddl) autodb_ddl ;;
  *) sed -n '2,13p' "$0"; exit 2 ;;
esac
