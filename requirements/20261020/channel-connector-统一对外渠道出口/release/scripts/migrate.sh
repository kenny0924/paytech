#!/usr/bin/env bash
# 统一对外渠道出口第一段 · mor-test 数据库步骤包装（阶段 7 由主会话按 release.md 第 6 节顺序执行；阶段 6 只写不执行）。
# 用法：migrate.sh <步骤> [备份目录]
#   backup             只读 mor-test，写本机：新建 ~/Code-MoR-backups/<UTC>-ccu-outbound-mor-test/（0700 / 0600）并打印路径；
#                      含 mor_platform 10 表（结构 + 数据）、mor_channel_connector 全库（18 表）、四个运行镜像 ID 与 JAR SHA-256、
#                      两个覆盖文件、00-precheck 输出、SHA256SUMS。
#   precheck   <dir>   只读：sql/00-precheck.sql
#   freeze     <dir>   只读：sql/20-freeze-check.sql（输出带序号，第 2 次起与第 1 次比较，不同即失败）
#   ddl        <dir>   写 mor_channel_connector：sql/10-channel-connector-ddl.sql（前置：10 表不存在且 change_detail 不存在）
#   copy       <dir>   写 mor_channel_connector：mysqldump 导出 mor_platform 10 表数据到 <dir>，再导入（前置：目标 10 表为空）
#   verify     <dir>   只读：sql/30-verify-copy.sql，并自动判定 V1 / V2 / V3 / V4
#   predrop    <dir>   只读 mor-test：删表前再备份 mor_platform 10 表与 mor_channel_connector 全库到 <dir>/predrop/
#   drop       <dir>   写 mor_platform：sql/40-platform-drop-integration.sql（前置：predrop 已完成，platform-support 已是候选镜像）
#   rollback-truncate <dir>  写 mor_channel_connector：sql/90-rollback-truncate-channel-connector.sql
#   restore-platform  <dir>  写 mor_platform：用 <dir>/mor_platform-10-tables.sql 恢复 10 表（删表后回退用；表已存在时拒绝）
#   postcheck  <dir>   只读：sql/99-postcheck.sql
# root 口令从 test-environment/.env.test 读入，只经 MYSQL_PWD 传入容器，不回显。每步输出写 <dir>/sql-<步骤>.out；遇错即停。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
repo="$(cd "$here/../../../.." && pwd)"
step="${1:?step}"
dir="${2:-}"
container=mor-test-mysql-1
tables=(t_standard_api t_api_field t_integration t_integration_api t_integration_script t_integration_standard_api
  t_integration_verify_account t_integration_verify_case t_integration_verify_run t_integration_verify_run_step)
dump_opts=(--single-transaction --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4)
root_pw="$(grep '^MOR_MYSQL_ROOT_PASSWORD=' "$repo/test-environment/.env.test" | cut -d= -f2-)"
[[ -n "$root_pw" ]] || { echo "缺少 MOR_MYSQL_ROOT_PASSWORD" >&2; exit 1; }
umask 077

mysql_root() { docker exec -i -e MYSQL_PWD="$root_pw" "$container" mysql -uroot --default-character-set=utf8mb4 "$@"; }
dump_root() { docker exec -i -e MYSQL_PWD="$root_pw" "$container" mysqldump -uroot "$@"; }
q() { echo "$2" | mysql_root -N -B "$1"; }
need_dir() { [[ -n "$dir" && -d "$dir" && -s "$dir/mor_platform-10-tables.sql" ]] || { echo "备份目录缺失或不完整：$dir" >&2; exit 1; }; }
run_file() { mysql_root -t -v "$1" < "$2" > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }; cat "$dir/sql-$step.out"; }
in_list() { printf "'%s'," "${tables[@]}" | sed 's/,$//'; }
count_existing() { q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='$1' AND table_name IN ($(in_list))"; }

case "$step" in
  backup)
    stamp="$(date -u +%Y%m%dT%H%M%SZ)"
    dir="$HOME/Code-MoR-backups/${stamp}-ccu-outbound-mor-test"
    mkdir -p "$dir"
    dump_root "${dump_opts[@]}" --skip-triggers mor_platform "${tables[@]}" > "$dir/mor_platform-10-tables.sql"
    dump_root "${dump_opts[@]}" --skip-triggers mor_channel_connector > "$dir/mor_channel_connector-full.sql"
    for s in card-service channel-connector-service platform-support-service operations-console-bff; do
      printf '%s %s\n' "$s" "$(docker inspect "mor-test-$s-1" --format '{{.Image}} {{.State.StartedAt}} restarts={{.RestartCount}}')"
    done > "$dir/images-before.txt"
    for s in card-service channel-connector-service platform-support-service operations-console-bff; do
      docker cp "mor-test-$s-1:/app/application.jar" "$dir/$s-before.jar" >/dev/null
      printf '%s %s\n' "$s" "$(shasum -a 256 "$dir/$s-before.jar" | cut -d' ' -f1)" >> "$dir/jars-before.txt"
      rm -f -- "${dir:?}/$s-before.jar"
    done
    cp "$here/compose-candidate.yaml" "$here/compose-rollback.yaml" "$dir/"
    mysql_root -t mysql < "$here/sql/00-precheck.sql" > "$dir/00-precheck.out"
    for f in mor_platform-10-tables.sql mor_channel_connector-full.sql; do
      [[ "$(grep -c '^CREATE TABLE' "$dir/$f")" -gt 0 ]] || { echo "备份无建表语句：$f" >&2; exit 1; }
    done
    chmod 600 "$dir"/*
    (cd "$dir" && shasum -a 256 * > SHA256SUMS && chmod 600 SHA256SUMS)
    echo "$dir"
    ;;
  precheck) need_dir; run_file mysql "$here/sql/00-precheck.sql" ;;
  freeze)
    need_dir
    n=$(find "$dir" -maxdepth 1 -name 'freeze-*.out' | wc -l | tr -d ' ')
    out="$dir/freeze-$((n + 1)).out"
    mysql_root -N -B mysql < "$here/sql/20-freeze-check.sql" > "$out"
    cat "$out"
    awk -F'\t' '$1 != 0 || $2 != 0 {exit 1}' "$out" || { echo "停止：仍有运行中的验证或未完成的补偿" >&2; exit 1; }
    if [[ "$n" -gt 0 ]]; then cmp -s "$dir/freeze-1.out" "$out" || { echo "停止：10 表在冻结后有新写入" >&2; exit 1; }; fi
    ;;
  ddl)
    need_dir
    [[ "$(count_existing mor_channel_connector)" == 0 ]] || { echo "停止：mor_channel_connector 已有目标表" >&2; exit 1; }
    [[ "$(q information_schema "SELECT COUNT(*) FROM columns WHERE table_schema='mor_channel_connector' AND table_name='t_channel_config_change' AND column_name='change_detail'")" == 0 ]] \
      || { echo "停止：change_detail 已存在" >&2; exit 1; }
    run_file mor_channel_connector "$here/sql/10-channel-connector-ddl.sql"
    echo "mor_channel_connector 表数：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='mor_channel_connector'")（期望 28）"
    ;;
  copy)
    need_dir
    [[ "$(count_existing mor_channel_connector)" == 10 ]] || { echo "停止：目标 10 表未建齐，先执行 ddl" >&2; exit 1; }
    rows=$(q mor_channel_connector "SELECT (SELECT COUNT(*) FROM t_standard_api)+(SELECT COUNT(*) FROM t_integration)+(SELECT COUNT(*) FROM t_integration_verify_run)+(SELECT COUNT(*) FROM t_api_field)")
    [[ "$rows" == 0 ]] || { echo "停止：目标表已有数据（$rows 行）" >&2; exit 1; }
    data="$dir/mor_platform-10-tables-data.sql"
    dump_root "${dump_opts[@]}" --no-create-info --skip-triggers --complete-insert mor_platform "${tables[@]}" > "$data"
    chmod 600 "$data"
    ! grep -q 'CREATE TABLE\|DROP TABLE' "$data" || { echo "停止：数据导出含建表或删表语句" >&2; exit 1; }
    echo "导出 INSERT 语句：$(grep -c '^INSERT INTO' "$data")"
    mysql_root mor_channel_connector < "$data" > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
    echo "导入完成"
    ;;
  verify)
    need_dir
    mysql_root -N -B mysql < "$here/sql/30-verify-copy.sql" > "$dir/sql-verify.tsv"
    python3 -I - "$dir/sql-verify.tsv" <<'PY'
import sys
rows = [l.rstrip('\n').split('\t') for l in open(sys.argv[1], encoding='utf-8') if l.strip()]
v1 = [r for r in rows if len(r) == 4 and r[0].startswith('t_')]
sums = {r[0]: r[1] for r in rows if len(r) == 2 and r[0].startswith('mor_')}
v3 = [r for r in rows if len(r) == 3 and '.' in r[0] and not r[0].startswith('mor_')]
v4 = [r for r in rows if len(r) == 2 and r[0].isdigit()]
bad = [r[0] for r in v1 if r[3] != '1']
pairs = {k.split('.', 1)[1] for k in sums}
bad += [t for t in pairs if sums.get('mor_platform.' + t) != sums.get('mor_channel_connector.' + t)]
bad += [r[0] for r in v3 if r[2] not in ('0', 'NULL')]
bad += ['V4'] if not v4 or v4[0] != ['28', '1'] else []
print(f"V1 {len(v1)} 表；V2 {len(pairs)} 对校验和；V3 {len(v3)} 列；V4 {v4}")
print('一致' if not bad and len(v1) == 10 and len(pairs) == 10 and len(v3) == 15 else f'不一致：{bad}')
sys.exit(0 if not bad and len(v1) == 10 and len(pairs) == 10 and len(v3) == 15 else 1)
PY
    ;;
  predrop)
    need_dir
    mkdir -p "$dir/predrop"
    dump_root "${dump_opts[@]}" --skip-triggers mor_platform "${tables[@]}" > "$dir/predrop/mor_platform-10-tables.sql"
    dump_root "${dump_opts[@]}" --skip-triggers mor_channel_connector > "$dir/predrop/mor_channel_connector-full.sql"
    chmod 600 "$dir/predrop"/*
    (cd "$dir/predrop" && shasum -a 256 *.sql > SHA256SUMS && chmod 600 SHA256SUMS)
    echo "INSERT 语句：$(grep -c '^INSERT INTO' "$dir/predrop/mor_platform-10-tables.sql")"
    ;;
  drop)
    need_dir
    [[ -s "$dir/predrop/mor_platform-10-tables.sql" ]] || { echo "停止：先执行 predrop" >&2; exit 1; }
    [[ "$(docker inspect mor-test-platform-support-service-1 --format '{{.Image}}')" == \
       "$(awk '/^  platform-support-service:/{f=1} f && /image:/{print $2; exit}' "$here/compose-candidate.yaml")" ]] \
      || { echo "停止：platform-support-service 还不是候选镜像" >&2; exit 1; }
    run_file mor_platform "$here/sql/40-platform-drop-integration.sql"
    echo "mor_platform 表数：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='mor_platform'")（期望 47）"
    ;;
  rollback-truncate) need_dir; run_file mor_channel_connector "$here/sql/90-rollback-truncate-channel-connector.sql" ;;
  restore-platform)
    need_dir
    src="$dir/predrop/mor_platform-10-tables.sql"; [[ -s "$src" ]] || src="$dir/mor_platform-10-tables.sql"
    [[ "$(count_existing mor_platform)" == 0 ]] || { echo "停止：mor_platform 仍有目标表，无需恢复" >&2; exit 1; }
    mysql_root mor_platform < "$src" > "$dir/sql-$step.out" 2>&1 || { cat "$dir/sql-$step.out" >&2; exit 1; }
    echo "已从 $(basename "$src") 恢复；mor_platform 表数：$(q information_schema "SELECT COUNT(*) FROM tables WHERE table_schema='mor_platform'")"
    ;;
  postcheck) need_dir; run_file mysql "$here/sql/99-postcheck.sql" ;;
  *) sed -n '2,19p' "$0"; exit 2 ;;
esac
