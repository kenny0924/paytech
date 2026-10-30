#!/usr/bin/env bash
# tax 结账算税与事件登记 · 发布 SQL 演练：断网一次性 MySQL（与 mor-test 同一镜像 mysql:8.4.11）导入 mor-test 只读导出的
# mor_tax、mor_acquiring、mor_billing、mor_platform，按 runbook 顺序执行：注册块 146（休眠 → 编译 → 激活 → 编译）、mor_tax 重建 + 初始化
# （含重跑）、mor_billing 注释、mor_acquiring 迁移（含重跑），再逆序回退（停用 → 删除块、acquiring 回滚含"已有新单时拒绝"、billing 注释回退、
# mor_tax 回退），比对结构与数据回到导出状态；块 146 每一步后用候选 Platform JAR 内的生产 GatewayConfigurationCompiler 编译 Gateway 快照。
# 改写自 REQ-03 release/harness/rehearse.sh。
# 用法：rehearse.sh <导出目录（含 mor_tax / mor_acquiring / mor_billing / mor_platform .sql.gz）> <Platform exec JAR> <工作目录>
# 不连任何环境；容器结束即删除（KEEP_CONTAINER=1 保留）。退出码非 0 = 任一 SQL 或编译不符合预期。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; sqld="$here/../sql"
dumps="${1:?dump dir}"; jar="${2:?platform jar}"; work="${3:?work dir}"
IMAGE="${MYSQL_IMAGE:-mysql:8.4.11}"; JAVA_BIN="${JAVA_BIN:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java}"
c="tax-rehearse-$$"; pw=throwaway; mkdir -p "$work"; log="$work/rehearse.log"; : > "$log"
B=900000000000146
say() { echo "### $*" | tee -a "$log"; }
cleanup() { [[ "${KEEP_CONTAINER:-0}" == 1 ]] || docker rm -f "$c" >/dev/null 2>&1 || true; rm -rf "$work/platform-jar"; }
trap cleanup EXIT
jardir="$work/platform-jar"; rm -rf "$jardir"; mkdir -p "$jardir"; (cd "$jardir" && unzip -q "$jar")
say "image $(docker image inspect "$IMAGE" --format '{{.Id}}'); platform jar $(shasum -a 256 "$jar" | cut -c1-16)"
for db in mor_tax mor_acquiring mor_billing mor_platform; do say "dump $db $(shasum -a 256 "$dumps/$db.sql.gz" | cut -c1-16)"; done
docker run -d --name "$c" --network none -e MYSQL_ROOT_PASSWORD=$pw "$IMAGE" --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
for _ in $(seq 1 120); do docker logs "$c" 2>&1 | grep -q "ready for connections.*port: 3306" && break; sleep 2; done
for _ in $(seq 1 30); do docker exec "$c" sh -c "MYSQL_PWD=$pw mysql -uroot -e 'select 1'" >/dev/null 2>&1 && break; sleep 1; done
mysqlx() { docker exec -i "$c" sh -c "MYSQL_PWD=$pw mysql -uroot --default-character-set=utf8mb4 $*"; }
q() { mysqlx "-N -B $1" <<<"$2"; }
for db in mor_tax mor_acquiring mor_billing mor_platform; do gunzip -c "$dumps/$db.sql.gz" | mysqlx ""; done; say "imported 4 dumps"

canon() {  # 结构（SHOW CREATE，去掉 AUTO_INCREMENT 计数）+ 按主键排序的逐行数据
  local db=$1 out=$2; : > "$out"
  for t in $(q "$db" "SHOW TABLES"); do q "$db" "SHOW CREATE TABLE \`$t\`" | sed -E 's/ AUTO_INCREMENT=[0-9]+//' >> "$out"; done
  docker exec "$c" sh -c "MYSQL_PWD=$pw mysqldump -uroot --no-create-info --skip-extended-insert --skip-dump-date --order-by-primary --skip-comments --no-tablespaces --set-gtid-purged=OFF $db" >> "$out"
}
compile() {
  local label=$1 out="$work/export-$1"; mkdir -p "$out"
  q mor_platform "SELECT target_id,target_service_code,discovery_service_name,channel,status FROM t_gateway_target" > "$out/targets.tsv"
  q mor_platform "SELECT route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,idempotency_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status FROM t_gateway_route" > "$out/routes.tsv"
  q mor_platform "SELECT permission_id,permission_code FROM t_permission" > "$out/permissions.tsv"
  q mor_platform "SELECT route_id,header_name,header_use FROM t_gateway_route_header" > "$out/headers.tsv"
  q mor_platform "SELECT route_id,parameter_name FROM t_gateway_route_query_param" > "$out/queries.tsv"
  q mor_platform "SELECT route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit FROM t_gateway_guard_rule" > "$out/guards.tsv"
  printf '### compile %-22s ' "$label" | tee -a "$log"
  "$JAVA_BIN" -cp "$jardir/BOOT-INF/classes:$jardir/BOOT-INF/lib/*" "$here/BlockSnapshotCheck.java" "$out" ${B}001 ${B}999 | tee -a "$log"
  local rc="${PIPESTATUS[0]}"; rm -rf "$out"; return "$rc"
}
step() {  # step <库> <标签> <sql 文件（相对 sql/ 或绝对）> [expect-fail]
  local db=$1 label=$2 file=$3 expect=${4:-ok} t0 t1 rc=0
  [[ $file == /* ]] || file="$sqld/$file"
  t0=$(python3 -c 'import time;print(time.time())')
  mysqlx "-t $db" < "$file" > "$work/$label.out" 2>&1 || rc=$?
  t1=$(python3 -c 'import time;print(time.time())')
  say "$label ($(basename "$file") @ $db) exit=$rc expect=$expect $(python3 -c "print(f'{$t1-$t0:.2f}s')")"
  grep -v "Using a password" "$work/$label.out" >> "$log" || true
  if [[ $expect == ok && $rc != 0 ]]; then say "SQL FAILED: $label"; exit 1; fi
  if [[ $expect == fail && $rc == 0 ]]; then say "PROTECTION MISSING: $label succeeded"; exit 1; fi
}
for db in mor_tax mor_acquiring mor_billing mor_platform; do canon $db "$work/$db-before.canon"; done

say "===== forward (runbook order)"
compile baseline
step mor_platform p01-precheck mor_platform-block-146-precheck.sql
step mor_platform p10-register mor_platform-block-146.sql;                     compile dormant
step mor_platform p11-verify-dormant mor_platform-block-146-verify.sql
step mor_platform p10-register-again mor_platform-block-146.sql fail
step mor_tax t10-rebuild mor_tax-rebuild.sql
step mor_tax t11-initial-data mor_tax-initial-data.sql
step mor_tax t12-verify mor_tax-verify.sql
step mor_tax t11-initial-data-again mor_tax-initial-data.sql fail
canon mor_tax "$work/mor_tax-forwarded.canon"
step mor_billing b10-comment mor_billing-comment.sql
step mor_billing b11-comment-again mor_billing-comment.sql
step mor_acquiring a10-migrate mor_acquiring-migrate.sql
step mor_acquiring a11-verify-after mor_acquiring-verify-after.sql
step mor_acquiring a12-migrate-again mor_acquiring-migrate.sql fail
canon mor_acquiring "$work/mor_acquiring-migrated.canon"
step mor_platform p20-activate mor_platform-block-146-activate.sql;            compile active
step mor_platform p21-verify-active mor_platform-block-146-verify.sql

say "===== rollback (reverse order)"
step mor_platform p30-rollback-while-active mor_platform-block-146-rollback.sql fail
step mor_platform p31-deactivate mor_platform-block-146-deactivate.sql;        compile deactivated
step mor_platform p32-verify-deactivated mor_platform-block-146-verify.sql
fixture_upload=$(q mor_platform "SELECT upload_id FROM t_file_upload ORDER BY upload_id LIMIT 1")
if [[ -n "$fixture_upload" ]]; then
  orig_src=$(q mor_platform "SELECT source_system FROM t_file_upload WHERE upload_id = $fixture_upload")
  q mor_platform "UPDATE t_file_upload SET source_system = 'tax-service' WHERE upload_id = $fixture_upload"; say "fixture: upload $fixture_upload source_system -> tax-service"
  step mor_platform p33-rollback-refused-tax-files mor_platform-block-146-rollback.sql fail
  q mor_platform "UPDATE t_file_upload SET source_system = '$orig_src' WHERE upload_id = $fixture_upload"; say "fixture restored ($orig_src)"
fi
step mor_platform p34-block-rollback mor_platform-block-146-rollback.sql;       compile rolled-back
step mor_platform p35-block-rollback-again mor_platform-block-146-rollback.sql
canon mor_platform "$work/mor_platform-final.canon"

ctx=$(q mor_acquiring "SELECT order_context_id FROM t_order_context ORDER BY order_context_id LIMIT 1")
q mor_acquiring "UPDATE t_order_context SET finance_tax_snapshot_id = NULL WHERE order_context_id = $ctx"; say "fixture: context $ctx snapshot -> NULL (simulates a new order)"
step mor_acquiring a20-rollback-precheck mor_acquiring-rollback-precheck.sql
step mor_acquiring a21-rollback-refused mor_acquiring-rollback.sql fail
canon mor_acquiring "$work/mor_acquiring-after-refused.canon"
mysqlx "" <<<"DROP DATABASE mor_acquiring"; gunzip -c "$dumps/mor_acquiring.sql.gz" | mysqlx ""; step mor_acquiring a22-remigrate-clean mor_acquiring-migrate.sql
say "fixture discarded: mor_acquiring re-imported from dump and migrated again"
step mor_acquiring a23-rollback mor_acquiring-rollback.sql
step mor_acquiring a24-rollback-again mor_acquiring-rollback.sql fail
canon mor_acquiring "$work/mor_acquiring-final.canon"
step mor_billing b20-comment-rollback mor_billing-comment-rollback.sql
canon mor_billing "$work/mor_billing-final.canon"
step mor_tax t20-rollback mor_tax-rebuild-rollback.sql
canon mor_tax "$work/mor_tax-final.canon"
step mor_tax t21-rebuild-after-rollback mor_tax-rebuild.sql
step mor_tax t22-initial-after-rollback mor_tax-initial-data.sql
canon mor_tax "$work/mor_tax-reforwarded.canon"

d() { diff "$work/$1.canon" "$work/$2.canon" > "$work/$1-vs-$2.diff" || true; say "$1 vs $2: $(grep -c '^[<>]' "$work/$1-vs-$2.diff" || true) differing lines (expected $3)"; }
d mor_platform-before mor_platform-final "2 (PLATFORM_ADMIN version +2 by activate/deactivate)"
d mor_acquiring-before mor_acquiring-final 0
d mor_acquiring-migrated mor_acquiring-after-refused "2 (fixture row only)"
d mor_billing-before mor_billing-final 0
d mor_tax-before mor_tax-final "see diff (old 24 tables recreated by rollback)"
d mor_tax-forwarded mor_tax-reforwarded "seed timestamps only"
rm -f "$work"/*.canon
say "DONE"
