#!/usr/bin/env bash
# 参考数据目录发布 SQL 演练（rel-dev 2026-10-27）：断网一次性 MySQL（与 mor-test 同一镜像 ID）导入 mor-test 只读备份，
# 按 runbook 顺序执行 release/sql 的全部正向脚本，再逆序执行全部回滚脚本，比对结构与数据回到备份状态；
# Platform 每一步后用候选 Platform JAR 内的生产 GatewayConfigurationCompiler 编译 Gateway 快照（BlockSnapshotCheck.java）。
# 用法：rehearse.sh <mor_platform 备份 .sql.gz> <mor_client_master 备份 .sql.gz> <Platform exec JAR> <工作目录>
# 不连任何环境；容器结束即删除（KEEP_CONTAINER=1 保留）。退出码非 0 = 任一 SQL 或编译失败。
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"; sqld="$here/../sql"
pf_dump="${1:?mor_platform dump}"; cm_dump="${2:?mor_client_master dump}"; jar="${3:?platform jar}"; work="${4:?work dir}"
IMAGE="${MYSQL_IMAGE:-mysql:8.4.11}"; JAVA_BIN="${JAVA_BIN:-/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java}"
c="refdata-rehearse-$$"; pw=throwaway; mkdir -p "$work"; log="$work/rehearse.log"; : > "$log"
say() { echo "### $*" | tee -a "$log"; }
cleanup() { [[ "${KEEP_CONTAINER:-0}" == 1 ]] || docker rm -f "$c" >/dev/null 2>&1 || true; }
trap cleanup EXIT
jardir="$work/platform-jar"; rm -rf "$jardir"; mkdir -p "$jardir"; (cd "$jardir" && unzip -q "$jar")
say "image $(docker image inspect "$IMAGE" --format '{{.Id}}'); platform jar $(shasum -a 256 "$jar" | cut -c1-16)"
docker run -d --name "$c" --network none -e MYSQL_ROOT_PASSWORD=$pw "$IMAGE" --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
for _ in $(seq 1 120); do docker logs "$c" 2>&1 | grep -q "ready for connections.*port: 3306" && break; sleep 2; done
for _ in $(seq 1 30); do docker exec "$c" sh -c "MYSQL_PWD=$pw mysql -uroot -e 'select 1'" >/dev/null 2>&1 && break; sleep 1; done
mysqlx() { docker exec -i "$c" sh -c "MYSQL_PWD=$pw mysql -uroot --default-character-set=utf8mb4 $*"; }
q() { mysqlx "-N -B $1" <<<"$2"; }
gunzip -c "$pf_dump" | mysqlx ""; gunzip -c "$cm_dump" | mysqlx ""
say "imported $(basename "$pf_dump") $(basename "$cm_dump")"

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
  "$JAVA_BIN" -cp "$jardir/BOOT-INF/classes:$jardir/BOOT-INF/lib/*" "$here/BlockSnapshotCheck.java" "$out" 900000000000139001 900000000000139999 | tee -a "$log"
  return "${PIPESTATUS[0]}"
}
step() {  # step <库> <标签> <sql 文件>：执行并计时，输出保存到 <工作目录>/<标签>.out
  local db=$1 label=$2 file="$sqld/$3" t0 t1 rc=0
  t0=$(python3 -c 'import time;print(time.time())')
  mysqlx "-t $db" < "$file" > "$work/$label.out" 2>&1 || rc=$?
  t1=$(python3 -c 'import time;print(time.time())')
  say "$label ($3) exit=$rc $(python3 -c "print(f'{$t1-$t0:.2f}s')")"
  grep -v "Using a password" "$work/$label.out" >> "$log" || true
  [[ $rc == 0 ]] || { say "SQL FAILED: $label"; exit 1; }
}
canon mor_platform "$work/pf-before.canon"; canon mor_client_master "$work/cm-before.canon"

say "===== Platform forward"
compile baseline
step mor_platform pf-00-precheck 00-platform-release-precheck.sql
step mor_platform pf-01-b139-precheck 01-platform-block139-precheck.sql
step mor_platform pf-10-refdata 10-platform-reference-data.sql;            compile after-refdata
step mor_platform pf-11-verify 11-platform-reference-data-verify.sql
canon mor_platform "$work/pf-after10.canon"
step mor_platform pf-12-localname-optional 12-platform-subdivision-local-name.optional.sql
canon mor_platform "$work/pf-after12.canon"
say "local_name optional script changes vs after 10: $(diff "$work/pf-after10.canon" "$work/pf-after12.canon" | grep -c '^[<>]' || true) lines"
step mor_platform pf-60-b139-dormant 60-platform-block139-registration.sql;  compile b139-dormant
step mor_platform pf-61-b139-active 61-platform-block139-activate.sql;       compile b139-active
say "===== client-master forward"
step mor_client_master cm-02-precheck 02-client-master-release-precheck.sql
step mor_client_master cm-20-catalog 20-client-master-reference-data-catalog.sql
step mor_client_master cm-21-comment 21-client-master-reference-data-catalog-comment.sql
step mor_client_master cm-02-postcheck 02-client-master-release-precheck.sql
step mor_client_master cm-20-rerun 20-client-master-reference-data-catalog.sql
say "===== rollback (reverse)"
step mor_client_master cm-81-comment-rollback 81-client-master-comment-rollback.optional.sql
step mor_client_master cm-80-catalog-rollback 80-client-master-reference-data-catalog-rollback.sql
step mor_platform pf-62-b139-deactivate 62-platform-block139-deactivate.sql; compile b139-deactivated
step mor_platform pf-69-b139-rollback 69-platform-block139-rollback.sql;     compile b139-rolled-back
step mor_platform pf-89-refdata-rollback 89-platform-reference-data-rollback.sql; compile refdata-rolled-back
canon mor_platform "$work/pf-final.canon"; canon mor_client_master "$work/cm-final.canon"
diff "$work/pf-before.canon" "$work/pf-final.canon" > "$work/pf-before-vs-final.diff" || true
diff "$work/cm-before.canon" "$work/cm-final.canon" > "$work/cm-before-vs-final.diff" || true
say "platform before vs final: $(grep -c '^[<>]' "$work/pf-before-vs-final.diff" || true) differing lines (see pf-before-vs-final.diff)"
say "client-master before vs final: $(grep -c '^[<>]' "$work/cm-before-vs-final.diff" || true) differing lines (see cm-before-vs-final.diff)"
say "DONE"
