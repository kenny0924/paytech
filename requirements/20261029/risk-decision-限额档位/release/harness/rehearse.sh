#!/usr/bin/env bash
# REQ-04 risk-decision 限额档位 · 发布 SQL 演练：断网一次性 MySQL（与 mor-test 同一镜像 mysql:8.4.11）导入 mor-test 只读导出的
# mor_risk_decision 与 mor_platform，按 runbook 顺序执行 risk 前滚 DDL + DML（含重跑）、注册块 145（休眠 → 激活），再逆序回退
# （停用 → 删除块 → risk 回滚，含"有档位数据时拒绝"），比对结构与数据回到导出状态；块 145 每一步后用现网 Platform JAR 内的生产
# GatewayConfigurationCompiler 编译 Gateway 快照（BlockSnapshotCheck.java）。
# 改写自 REQ-03 release/harness/rehearse.sh（块区间 145；mor_risk_decision 承保 DDL 换成限额档位 DDL）。
# 用法：rehearse.sh <mor_risk_decision 导出 .sql.gz> <mor_platform 导出 .sql.gz> <Platform exec JAR> <工作目录>
# 不连任何环境；容器结束即删除（KEEP_CONTAINER=1 保留）。退出码非 0 = 任一 SQL 或编译不符合预期，或 Snapshot 响应体 ≥ SNAPSHOT_CAP
# （缺省 4194304 = 新 Gateway 上限；D-9 之前的 Gateway 为 1048576）。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; sqld="$here/../sql"
rd_dump="${1:?mor_risk_decision dump}"; pf_dump="${2:?mor_platform dump}"; jar="${3:?platform jar}"; work="${4:?work dir}"
IMAGE="${MYSQL_IMAGE:-mysql:8.4.11}"; JAVA_BIN="${JAVA_BIN:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java}"
c="rq04-rehearse-$$"; pw=throwaway; mkdir -p "$work"; log="$work/rehearse.log"; : > "$log"
say() { echo "### $*" | tee -a "$log"; }
cleanup() { [[ "${KEEP_CONTAINER:-0}" == 1 ]] || docker rm -f "$c" >/dev/null 2>&1 || true; rm -rf "$work/platform-jar"; }
trap cleanup EXIT
jardir="$work/platform-jar"; rm -rf "$jardir"; mkdir -p "$jardir"; (cd "$jardir" && unzip -q "$jar")
say "image $(docker image inspect "$IMAGE" --format '{{.Id}}'); platform jar $(shasum -a 256 "$jar" | cut -c1-16); dumps $(shasum -a 256 "$rd_dump" | cut -c1-16) / $(shasum -a 256 "$pf_dump" | cut -c1-16)"
docker run -d --name "$c" --network none -e MYSQL_ROOT_PASSWORD=$pw "$IMAGE" --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
for _ in $(seq 1 120); do docker logs "$c" 2>&1 | grep -q "ready for connections.*port: 3306" && break; sleep 2; done
for _ in $(seq 1 30); do docker exec "$c" sh -c "MYSQL_PWD=$pw mysql -uroot -e 'select 1'" >/dev/null 2>&1 && break; sleep 1; done
mysqlx() { docker exec -i "$c" sh -c "MYSQL_PWD=$pw mysql -uroot --default-character-set=utf8mb4 $*"; }
q() { mysqlx "-N -B $1" <<<"$2"; }
gunzip -c "$rd_dump" | mysqlx ""; gunzip -c "$pf_dump" | mysqlx ""; say "imported $(basename "$rd_dump") $(basename "$pf_dump")"

canon() {  # 结构（SHOW CREATE，去掉 AUTO_INCREMENT 计数）+ 例程 + 按主键排序的逐行数据
  local db=$1 out=$2; : > "$out"
  for t in $(q "$db" "SHOW TABLES"); do q "$db" "SHOW CREATE TABLE \`$t\`" | sed -E 's/ AUTO_INCREMENT=[0-9]+//' >> "$out"; done
  q "$db" "SELECT routine_name FROM information_schema.routines WHERE routine_schema='$db' ORDER BY 1" | sed 's/^/ROUTINE /' >> "$out"
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
  "$JAVA_BIN" -cp "$jardir/BOOT-INF/classes:$jardir/BOOT-INF/lib/*" "$here/BlockSnapshotCheck.java" "$out" 900000000000145001 900000000000145999 "${SNAPSHOT_CAP:-4194304}" "$work/snapshot-$label.json" | tee -a "$log"
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
canon mor_risk_decision "$work/rd-before.canon"
canon mor_platform "$work/pf-before.canon"

say "===== forward"
step mor_risk_decision r00-precheck 00-mor_risk_decision-precheck.sql
step mor_risk_decision r10-forward 10-mor_risk_decision-forward.sql
step mor_risk_decision r11-verify 11-mor_risk_decision-verify.sql
step mor_risk_decision r12-forward-again 10-mor_risk_decision-forward.sql
canon mor_risk_decision "$work/rd-forwarded.canon"
compile baseline
step mor_platform p01-precheck 01-mor_platform-block-145-precheck.sql
step mor_platform p20-register 20-mor_platform-block-145.sql;                 compile dormant
step mor_platform p21-verify-dormant 21-mor_platform-block-145-verify.sql
step mor_platform p20-register-again 20-mor_platform-block-145.sql fail
step mor_platform p22-activate 22-mor_platform-block-145-activate.sql;        compile active
step mor_platform p23-verify-active 21-mor_platform-block-145-verify.sql
for gj in ${GATEWAY_JARS:-}; do  # D-9：用 Gateway 生产解析与编译检查激活后的响应体（候选应 OK，旧 1 MiB Gateway 应 FAILED）
  gdir="$work/gw-$(basename "$gj" .jar)"; rm -rf "$gdir"; mkdir -p "$gdir"; (cd "$gdir" && unzip -q "$gj")
  printf '### gateway %-30s ' "$(basename "$gj")" | tee -a "$log"
  "$JAVA_BIN" -cp "$gdir/BOOT-INF/classes:$gdir/BOOT-INF/lib/*" "$here/GatewaySnapshotDecodeCheck.java" "$work/snapshot-active.json" 2>&1 | tail -1 | tee -a "$log" || true
  rm -rf "$gdir"
done
step mor_platform p22-activate-again 22-mor_platform-block-145-activate.sql;  compile active-again

say "===== rollback (reverse order)"
step mor_platform p30-deactivate 23-mor_platform-block-145-deactivate.sql;    compile deactivated
step mor_platform p31-verify-deactivated 21-mor_platform-block-145-verify.sql
step mor_platform p32-block-rollback 92-mor_platform-block-145-rollback.sql;  compile rolled-back
step mor_platform p33-block-rollback-again 92-mor_platform-block-145-rollback.sql
canon mor_platform "$work/pf-final.canon"
step mor_risk_decision r20-fixture-row "$here/fixture-tier-row.sql"
step mor_risk_decision r21-rollback-refused 90-mor_risk_decision-rollback.sql fail
q mor_risk_decision "DELETE FROM t_client_limit_tier WHERE client_id = 880000000901"; say "fixture row deleted"
step mor_risk_decision r22-rollback 90-mor_risk_decision-rollback.sql
step mor_risk_decision r23-precheck-after-rollback 00-mor_risk_decision-precheck.sql
step mor_risk_decision r24-rollback-again 90-mor_risk_decision-rollback.sql
canon mor_risk_decision "$work/rd-final.canon"
step mor_risk_decision r30-forward-after-rollback 10-mor_risk_decision-forward.sql
canon mor_risk_decision "$work/rd-reforwarded.canon"

diff "$work/pf-before.canon" "$work/pf-final.canon" > "$work/pf-before-vs-final.diff" || true
diff "$work/rd-before.canon" "$work/rd-final.canon" > "$work/rd-before-vs-final.diff" || true
diff "$work/rd-forwarded.canon" "$work/rd-reforwarded.canon" > "$work/rd-forwarded-vs-reforwarded.diff" || true
say "mor_platform before vs final (after block rollback): $(grep -c '^[<>]' "$work/pf-before-vs-final.diff" || true) differing lines (expected 0)"
say "mor_risk_decision before vs final (after DDL rollback): $(grep -c '^[<>]' "$work/rd-before-vs-final.diff" || true) differing lines (expected 0)"
say "mor_risk_decision forwarded vs re-forwarded: $(grep -c '^[<>]' "$work/rd-forwarded-vs-reforwarded.diff" || true) differing lines (expected 0)"
rm -f "$work"/*.canon
say "DONE"
