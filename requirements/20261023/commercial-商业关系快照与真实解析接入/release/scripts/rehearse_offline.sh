#!/usr/bin/env bash
# 阶段 6 离线演练（不连 mor-test）：把 mor-test 只读备份导入断网的一次性 MySQL 容器，按发布顺序经 apply_sql.sh --apply 执行全部 SQL、
# 在 Platform 休眠 / 激活 / 停用 / 回滚四态编译 Gateway 快照、做结构漂移核对，再按逆序执行全部回滚并与备份逐表比对。
# 用法：rehearse_offline.sh <备份目录（含六个 <库>-full.sql）> <Platform JAR 解出目录> <工作目录>
# 前置：容器 relsnap-stage6-rehearsal-mysql 已用 mor-test 同一 MySQL 镜像 ID、同参数、--network none 启动，root 口令 throwaway，
#       六个备份已导入（导入命令见 evidence/rehearsal.md）。输出：<工作目录>/rehearsal.log 与各步 .out。
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backup="${1:?backup dir}"; jar="${2:?platform jar dir}"; work="${3:?work dir}"
export RELSNAP_MYSQL_CONTAINER=relsnap-stage6-rehearsal-mysql RELSNAP_MYSQL_PWD=throwaway
c=$RELSNAP_MYSQL_CONTAINER
mkdir -p "$work/run"; for f in "$backup"/*-full.sql; do ln -sf "$f" "$work/run/"; done
log="$work/rehearsal.log"; : > "$log"
say() { echo "### $*" | tee -a "$log"; }
step() { say "apply_sql.sh $1 ${2:-}"; "$here/scripts/apply_sql.sh" "$work/run" "$1" ${2:-} >> "$log" 2>&1 || { say "FAILED: $1"; tail -20 "$log"; exit 1; }; }
compile() { say "compile: $1"; "$here/evidence/snapshot/check.sh" "$1" "$c" "$jar" "$work/snapshot" | tee -a "$log"; }
sql() { docker exec "$c" sh -c "MYSQL_PWD=throwaway mysql -uroot --default-character-set=utf8mb4 -N -B -e \"$1\""; }

say "baseline"; step precheck; compile baseline
say "schema drift before migration: backup vs main 8a86a31c schema files (pre-existing drift)"; "$here/scripts/schema_drift.py" "$c" 8a86a31c | tee -a "$log"
# 1 compliance
step cmp-ddl --apply; step cmp-backfill; step cmp-backfill --apply
# 2 client-master（commercial 镜像先发布，与 SQL 无关）
step cm-ddl --apply; step cm-seed; step cm-seed --apply; step cm-data --apply; step cm-ac11; step cm-ac11 --apply
say "re-run guards (each must fail before writing)"
for s in cm-seed cm-ac11; do
  if "$here/scripts/apply_sql.sh" "$work/run" $s --apply >> "$log" 2>&1; then say "UNEXPECTED: re-run of $s succeeded"; exit 1; else say "re-run of $s aborted as expected"; fi
done
# 3 commercial 兜底（只在 D-2 选择 SQL 时；演练两条路径都覆盖）
step com-fallback; step com-fallback --apply
# 4 acquiring、5 risk-decision
step acq-ddl --apply; step acq-platform --apply; step risk --apply
# 6 Platform 块 137
step plt-reg; step plt-reg --apply; compile dormant
step plt-activate --apply; compile active
step postcheck
say "schema drift vs worktree schema files"; "$here/scripts/schema_drift.py" "$c" | tee -a "$log"
# 回滚（逆序）
step plt-deactivate --apply; compile deactivated
step plt-rollback --apply; compile rolled-back
rewrite_time="$(grep -A3 relsnap_rewrite_time "$(ls -t "$work"/run/sql-risk-*.out | head -1)" | grep -oE '[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9:.]+' | head -1)"
say "risk rewrite time from step 50 output: $rewrite_time"
sed "s/^SET @relsnap_rewrite_time = NULL;.*/SET @relsnap_rewrite_time = '$rewrite_time';/" "$here/sql/89-rollback-risk.sql" > "$work/89-rollback-risk.filled.sql"
say "rb-risk (89 with rewrite time filled)"
docker exec -i -e MYSQL_PWD=throwaway "$c" mysql -uroot --default-character-set=utf8mb4 -t mor_risk_decision < "$work/89-rollback-risk.filled.sql" >> "$log" 2>&1
step rb-acq-platform --apply; step rb-acq-ddl --apply
step rb-com-fallback --apply
step rb-cm-ac11 --apply; step rb-cm-data --apply; step rb-cm-seed --apply; step rb-cm-ddl --apply
step rb-cmp-backfill --apply; step rb-cmp-ddl --apply
say "after rollback"; step precheck
say "compare rolled-back databases with the backup"; "$here/scripts/compare_with_backup.py" "$c" "$backup" | tee -a "$log"
