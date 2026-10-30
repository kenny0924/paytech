#!/usr/bin/env bash
# operations 明细账运维 · 注册块 148 离线演练：断网一次性 MySQL（与 mor-test 同一镜像 mysql:8.4.11）导入 mor-test 只读导出的 mor_platform，
# 按 runbook 顺序执行 db/platform-support 六件套：预检 → 激活先于写入（空操作）→ 休眠写入 → 重复写入拒绝 → 激活 → 重复激活 → 激活中回滚拒绝
# → 停用 → 重复停用 → 回滚 → 重复回滚；每个状态用两个 Platform JAR（现网 0ea9cc2d 与候选 plops-20261030a）内的生产
# GatewayConfigurationCompiler 编译快照并序列化（BlockSnapshotCheck.java），响应体再用现网 Gateway JAR 的生产解析解码
# （GatewaySnapshotDecodeCheck.java）。最后比对 mor_platform 与导出逐行差异。改写自 payout release/harness/rehearse.sh（只保留块部分）。
# 用法：rehearse.sh <mor_platform 导出 .sql.gz> <现网 Platform JAR> <候选 Platform JAR> <Gateway JAR> <工作目录>
# 不连任何环境；容器结束即删除（KEEP_CONTAINER=1 保留）。退出码非 0 = 任一 SQL / 保护 / 编译 / 解码不符合预期，或响应体 ≥ SNAPSHOT_CAP（缺省 4194304）。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; sqld="$here/../../db/platform-support"
pf_dump="${1:?mor_platform dump}"; ljar="${2:?live platform jar}"; cjar="${3:?candidate platform jar}"; gjar="${4:?gateway jar}"; work="${5:?work dir}"
IMAGE="${MYSQL_IMAGE:-mysql:8.4.11}"; JAVA_BIN="${JAVA_BIN:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java}"
c="plops-rehearse-$$"; pw=throwaway
mkdir -p "$work"; chmod 700 "$work"; log="$work/rehearse.log"; : > "$log"
say() { echo "### $*" | tee -a "$log"; }
cleanup() { [[ "${KEEP_CONTAINER:-0}" == 1 ]] || docker rm -f "$c" >/dev/null 2>&1 || true; rm -rf "$work/live-jar" "$work/cand-jar" "$work/gateway-jar"; }
trap cleanup EXIT
for p in live:$ljar cand:$cjar gateway:$gjar; do d="$work/${p%%:*}-jar"; rm -rf "$d"; mkdir -p "$d"; (cd "$d" && unzip -q "${p#*:}"); done
say "image $(docker image inspect "$IMAGE" --format '{{.Id}}'); platform live $(shasum -a 256 "$ljar" | cut -c1-16) / candidate $(shasum -a 256 "$cjar" | cut -c1-16); gateway $(shasum -a 256 "$gjar" | cut -c1-16); dump $(shasum -a 256 "$pf_dump" | cut -c1-16)"
docker run -d --name "$c" --network none -e MYSQL_ROOT_PASSWORD=$pw "$IMAGE" --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
say "container network: $(docker inspect "$c" --format '{{.HostConfig.NetworkMode}}')"
for _ in $(seq 1 120); do docker logs "$c" 2>&1 | grep -q "ready for connections.*port: 3306" && break; sleep 2; done
for _ in $(seq 1 30); do docker exec "$c" sh -c "MYSQL_PWD=$pw mysql -uroot -e 'select 1'" >/dev/null 2>&1 && break; sleep 1; done
mysqlx() { docker exec -i "$c" sh -c "MYSQL_PWD=$pw mysql -uroot --default-character-set=utf8mb4 $*"; }
q() { mysqlx "-N -B $1" <<<"$2"; }
gunzip -c "$pf_dump" | mysqlx ""; say "imported $(basename "$pf_dump")"
canon() {
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
  for which in live cand; do
    local jardir="$work/$which-jar" res
    res=$("$JAVA_BIN" -cp "$jardir/BOOT-INF/classes:$jardir/BOOT-INF/lib/*" "$here/BlockSnapshotCheck.java" "$out" 900000000000148001 900000000000148999 "${SNAPSHOT_CAP:-4194304}" "$work/snapshot-$label-$which.json" 2>&1 | tail -2 || true)
    say "compile $label [$which platform]: $(tr '\n' ' ' <<<"$res")"
    [[ $res == *"COMPILE OK"* && $res != *"SIZE EXCEEDED"* ]] || { say "COMPILE NOT OK: $label $which"; exit 1; }
    res=$("$JAVA_BIN" -cp "$work/gateway-jar/BOOT-INF/classes:$work/gateway-jar/BOOT-INF/lib/*" "$here/GatewaySnapshotDecodeCheck.java" "$work/snapshot-$label-$which.json" 2>&1 | tail -1 || true)
    say "gateway-decode $label [$which platform]: $res"
    [[ $res == *"DECODE+COMPILE OK"* ]] || { say "GATEWAY DECODE NOT OK: $label $which"; exit 1; }
  done
  rm -rf "$out"
}
step() {  # step <标签> <sql 文件名> [ok|fail]
  local label=$1 file="$sqld/$2" expect=${3:-ok} rc=0
  mysqlx "-t mor_platform" < "$file" > "$work/$label.out" 2>&1 || rc=$?
  say "$label ($2) exit=$rc expect=$expect"; grep -v "Using a password" "$work/$label.out" >> "$log" || true
  if [[ $expect == ok && $rc != 0 ]]; then say "SQL FAILED: $label"; exit 1; fi
  if [[ $expect == fail && $rc == 0 ]]; then say "PROTECTION MISSING: $label succeeded"; exit 1; fi
}
cnt() { grep -c '^[<>]' "$1" || true; }
canon mor_platform "$work/pf-before.canon"
compile baseline
step c01-precheck mor_platform-block-148-precheck.sql
step c02-activate-before-register mor_platform-block-148-activate.sql
step c03-register mor_platform-block-148.sql;                   compile dormant
step c04-verify-dormant mor_platform-block-148-verify.sql
step c05-register-again mor_platform-block-148.sql fail
step c06-activate mor_platform-block-148-activate.sql;          compile active
step c07-verify-active mor_platform-block-148-verify.sql
step c08-activate-again mor_platform-block-148-activate.sql
step c09-rollback-while-active mor_platform-block-148-rollback.sql fail
step c10-deactivate mor_platform-block-148-deactivate.sql;      compile deactivated
step c11-verify-deactivated mor_platform-block-148-verify.sql
step c12-deactivate-again mor_platform-block-148-deactivate.sql
step c13-rollback mor_platform-block-148-rollback.sql;          compile rolled-back
step c14-rollback-again mor_platform-block-148-rollback.sql
step c15-precheck-after-rollback mor_platform-block-148-precheck.sql
canon mor_platform "$work/pf-final.canon"
diff "$work/pf-before.canon" "$work/pf-final.canon" > "$work/pf-before-vs-final.diff" || true
# 激活 / 停用各把平台管理员角色 900000000000000510 的 version + 1（会话权限缓存刷新），回滚不回退该计数——允许的差异只有 t_role 该角色一行。
other=$({ grep '^[<>]' "$work/pf-before-vs-final.diff" | grep -v "^[<>] INSERT INTO \`t_role\` VALUES (900000000000000510," || true; } | wc -l | tr -d ' ')
say "mor_platform before vs final (after block rollback): $(cnt "$work/pf-before-vs-final.diff") differing lines; outside PLATFORM_ADMIN role version row: $other (expected 0)"
[[ $other == 0 ]] || { say "CANON MISMATCH"; exit 1; }
rm -f "$work"/*.canon
mkdir -p "$work/evidence"; cp "$work"/*.out "$work"/*.diff "$work/rehearse.log" "$work/evidence/"
for f in "$work"/snapshot-*.json; do printf "%s %s %s\n" "$(basename "$f")" "$(wc -c < "$f" | tr -d " ")" "$(shasum -a 256 "$f" | cut -c1-16)"; done > "$work/evidence/snapshot-sizes.txt"
say "DONE"
