#!/bin/bash
# 离线编译网关快照（阶段 6 只读演练用）：从断网的一次性 MySQL 容器导出 6 张网关表，用候选 Platform JAR 内的生产
# GatewayConfigurationCompiler 编译（按 GatewayConfigurationRepositoryImpl 的加载方式组装 LoadedConfiguration）。
# 用法：check.sh <标签> <容器名> <Platform JAR 解出目录（含 BOOT-INF/classes 与 BOOT-INF/lib）> <输出目录>（复制自文件上传入口发布包，只改统计的路由前缀为块 137）
set -euo pipefail
LABEL=$1; CONTAINER=$2; JAR_DIR=$3; OUT=$4/export-$LABEL; mkdir -p "$OUT"
q() { docker exec "$CONTAINER" sh -c "MYSQL_PWD=${REHEARSAL_PWD:-throwaway} mysql -uroot --default-character-set=utf8mb4 -N -B mor_platform -e \"$1\""; }
q "SELECT target_id,target_service_code,discovery_service_name,channel,status FROM t_gateway_target" > "$OUT/targets.tsv"
q "SELECT route_id,route_code,channel,http_method,external_path_template,downstream_path_template,target_id,security_profile_code,authorization_mode,permission_id,required_scope_code,request_cookie_mode,response_cookie_mode,idempotency_mode,body_limit_bytes,body_receive_timeout_millis,response_timeout_millis,status FROM t_gateway_route" > "$OUT/routes.tsv"
q "SELECT permission_id,permission_code FROM t_permission" > "$OUT/permissions.tsv"
q "SELECT route_id,header_name,header_use FROM t_gateway_route_header" > "$OUT/headers.tsv"
q "SELECT route_id,parameter_name FROM t_gateway_route_query_param" > "$OUT/queries.tsv"
q "SELECT route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit FROM t_gateway_guard_rule" > "$OUT/guards.tsv"
CP="$JAR_DIR/BOOT-INF/classes:$JAR_DIR/BOOT-INF/lib/*"
/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java -cp "$CP" "$(dirname "$0")/SnapshotCheck.java" "$OUT"
