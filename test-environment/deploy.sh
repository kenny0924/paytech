#!/usr/bin/env bash
set -Eeuo pipefail

environment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
build_script="$environment_dir/scripts/build.sh"
compose_script="$environment_dir/compose.sh"
smoke_script="$environment_dir/scripts/smoke.py"
wait_timeout=240
skip_build=false
check_only=false
compose_ready=false
deployment_phase=""
current_stage="部署前检查"
deploy_lock="$environment_dir/private/deploy.lock"
lock_acquired=false
phase_checkpoint="$environment_dir/private/risk-bff-web-phase.ready"
checkpoint_temp=""
checkpoint_expected_temp=""
phase_one_checkpoint_managed=false
phase_one_checkpoint_complete=false

readonly -a protected_infrastructure_services=(
  mysql
  redis
  rabbitmq
)

readonly -a risk_bff_web_services=(
  risk-decision-service
  compliance-service
  operations-console-bff
  operations-console-web
)

readonly -a platform_gateway_nginx_services=(
  platform-support-service
  nginx
  api-gateway
)

readonly -a operations_images=(
  mor-test/nacos:3.1.2
  mor-test/risk-decision-service:development
  mor-test/compliance-service:development
  mor-test/platform-support-service:development
  mor-test/operations-console-bff:development
  mor-test/api-gateway:development
  mor-test/operations-console-web:development
)

usage() {
  cat <<'EOF'
用法：./test-environment/deploy.sh --phase risk-bff-web|platform-gateway-nginx [选项]

按一个显式阶段构建并部署 Operations test 环境。

选项：
  --phase PHASE      必填：risk-bff-web 或 platform-gateway-nginx
  --skip-build       复用当前本地镜像；仅第二阶段在健康检查后执行烟测
  --check-only       只检查部署前置条件，不构建或启动服务
  --timeout SECONDS  Compose 健康检查等待时间，默认 240 秒
  -h, --help         显示帮助

该脚本只更新已初始化的 mor-test，不执行 Schema、初始化 DML、增量 SQL 或 Bootstrap。
实际第一阶段在构建/启动前使 private/risk-bff-web-phase.ready 失效，全部健康后才原子发布；
实际第二阶段必须先验证该文件及七个本地镜像 ID。--check-only 不创建或删除该文件。
Vault 使用独立的 test-environment/scripts/build-vault.sh 流程，不在此脚本中更新。
EOF
}

fail() {
  printf '错误：%s\n' "$1" >&2
  exit 1
}

is_jdk_25() {
  local candidate="$1"
  local version
  [[ -x "$candidate/bin/java" ]] || return 1
  version="$("$candidate/bin/java" -version 2>&1)" || return 1
  case "$version" in
    *'version "25"'*|*'version "25.'*) return 0 ;;
    *) return 1 ;;
  esac
}

find_jdk_25() {
  local candidate=""
  local java_properties=""
  local line=""

  if [[ -n "${JAVA_HOME:-}" ]] && is_jdk_25 "$JAVA_HOME"; then
    printf '%s\n' "$JAVA_HOME"
    return 0
  fi

  if [[ -x /usr/libexec/java_home ]]; then
    candidate="$(/usr/libexec/java_home -v 25 2>/dev/null || true)"
    if [[ -n "$candidate" ]] && is_jdk_25 "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi

  candidate="/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home"
  if is_jdk_25 "$candidate"; then
    printf '%s\n' "$candidate"
    return 0
  fi

  if command -v java >/dev/null 2>&1; then
    java_properties="$(java -XshowSettings:properties -version 2>&1 || true)"
    while IFS= read -r line; do
      case "$line" in
        *'java.home = '*)
          candidate="${line#*java.home = }"
          break
          ;;
      esac
    done <<< "$java_properties"
    if [[ -n "$candidate" ]] && is_jdk_25 "$candidate"; then
      printf '%s\n' "$candidate"
      return 0
    fi
  fi

  return 1
}

file_mode() {
  local mode=""
  if mode="$(stat -f '%Lp' "$1" 2>/dev/null)"; then
    printf '%s\n' "$mode"
    return 0
  fi
  if mode="$(stat -c '%a' "$1" 2>/dev/null)"; then
    printf '%s\n' "$mode"
    return 0
  fi
  return 1
}

file_owner_uid() {
  local owner_uid=""
  if owner_uid="$(stat -f '%u' "$1" 2>/dev/null)"; then
    printf '%s\n' "$owner_uid"
    return 0
  fi
  if owner_uid="$(stat -c '%u' "$1" 2>/dev/null)"; then
    printf '%s\n' "$owner_uid"
    return 0
  fi
  return 1
}

write_current_image_checkpoint() {
  local image_name=""
  local image_id=""

  for image_name in "${operations_images[@]}"; do
    image_id="$(docker image inspect --format '{{.Id}}' "$image_name")" || return $?
    if [[ ! "$image_id" =~ ^sha256:[0-9a-f]{64}$ ]]; then
      printf '错误：本地镜像 ID 无法写入阶段 checkpoint：%s。\n' "$image_name" >&2
      return 1
    fi
    printf '%s %s\n' "$image_name" "$image_id"
  done | LC_ALL=C sort
}

prepare_expected_checkpoint() {
  checkpoint_expected_temp="$(mktemp \
    "$environment_dir/private/.risk-bff-web-phase.ready.expected.XXXXXX")"
  chmod 600 "$checkpoint_expected_temp"
  write_current_image_checkpoint > "$checkpoint_expected_temp"
}

clear_expected_checkpoint() {
  if [[ -n "$checkpoint_expected_temp" ]]; then
    rm -f "$checkpoint_expected_temp"
    checkpoint_expected_temp=""
  fi
}

verify_checkpoint_file_safety() {
  [[ -e "$phase_checkpoint" || -L "$phase_checkpoint" ]] || fail \
    '缺少 risk-bff-web 阶段 checkpoint；必须先成功完成第一阶段。'
  [[ ! -L "$phase_checkpoint" ]] || fail \
    'risk-bff-web 阶段 checkpoint 不能是符号链接。'
  [[ -f "$phase_checkpoint" ]] || fail \
    'risk-bff-web 阶段 checkpoint 必须是普通文件。'
  [[ "$(file_owner_uid "$phase_checkpoint")" == "$(id -u)" ]] || fail \
    'risk-bff-web 阶段 checkpoint 必须属于当前用户。'
  [[ "$(file_mode "$phase_checkpoint")" == 600 ]] || fail \
    'risk-bff-web 阶段 checkpoint 权限必须为 0600。'
}

verify_phase_checkpoint() {
  verify_checkpoint_file_safety
  prepare_expected_checkpoint
  cmp -s "$checkpoint_expected_temp" "$phase_checkpoint" || fail \
    'risk-bff-web 阶段 checkpoint 格式错误或本地镜像已变化；请重新成功执行第一阶段。'
  clear_expected_checkpoint
}

invalidate_phase_checkpoint() {
  current_stage="旧 risk-bff-web 阶段 checkpoint 失效"
  if [[ -e "$phase_checkpoint" || -L "$phase_checkpoint" ]]; then
    rm -f "$phase_checkpoint"
  fi
  [[ ! -e "$phase_checkpoint" && ! -L "$phase_checkpoint" ]] || fail \
    '无法安全移除旧 risk-bff-web 阶段 checkpoint。'
}

publish_phase_checkpoint() {
  current_stage="发布 risk-bff-web 阶段 checkpoint"
  prepare_expected_checkpoint
  checkpoint_temp="$(mktemp \
    "$environment_dir/private/.risk-bff-web-phase.ready.tmp.XXXXXX")"
  chmod 600 "$checkpoint_temp"
  cp "$checkpoint_expected_temp" "$checkpoint_temp"
  chmod 600 "$checkpoint_temp"
  mv "$checkpoint_temp" "$phase_checkpoint"
  checkpoint_temp=""
  verify_checkpoint_file_safety
  cmp -s "$checkpoint_expected_temp" "$phase_checkpoint" || fail \
    '发布后的 risk-bff-web 阶段 checkpoint 内容校验失败。'
  clear_expected_checkpoint
}

release_lock() {
  if [[ "$lock_acquired" == true ]]; then
    rmdir "$deploy_lock" 2>/dev/null || true
  fi
}

cleanup() {
  if [[ -n "$checkpoint_temp" ]]; then
    rm -f "$checkpoint_temp" 2>/dev/null || true
  fi
  if [[ -n "$checkpoint_expected_temp" ]]; then
    rm -f "$checkpoint_expected_temp" 2>/dev/null || true
  fi
  if [[ "$phase_one_checkpoint_managed" == true && \
        "$phase_one_checkpoint_complete" == false && \
        ( -e "$phase_checkpoint" || -L "$phase_checkpoint" ) ]]; then
    rm -f "$phase_checkpoint" 2>/dev/null || true
  fi
  release_lock
}

on_error() {
  local exit_code=$?
  trap - ERR
  printf '\n部署失败：%s（退出码 %s）。现有容器和数据卷已保留。\n' \
    "$current_stage" "$exit_code" >&2
  if [[ "$compose_ready" == true ]]; then
    "$compose_script" ps >&2 || true
  fi
  printf '诊断命令：./test-environment/compose.sh logs --tail 200 <service>\n' >&2
  exit "$exit_code"
}
trap on_error ERR
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

while (($# > 0)); do
  case "$1" in
    --skip-build)
      skip_build=true
      shift
      ;;
    --check-only)
      check_only=true
      shift
      ;;
    --phase)
      (($# >= 2)) || fail '--phase 缺少阶段；必须是 risk-bff-web|platform-gateway-nginx。'
      [[ -z "$deployment_phase" ]] || fail '--phase 只能指定一次。'
      deployment_phase="$2"
      shift 2
      ;;
    --timeout)
      (($# >= 2)) || fail '--timeout 缺少秒数。'
      wait_timeout="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "未知参数：$1。使用 --help 查看帮助。"
      ;;
  esac
done

case "$deployment_phase" in
  risk-bff-web|platform-gateway-nginx) ;;
  '') fail '--phase 为必填参数；必须是 risk-bff-web|platform-gateway-nginx。' ;;
  *) fail '--phase 必须是 risk-bff-web|platform-gateway-nginx。' ;;
esac

[[ "$wait_timeout" =~ ^[1-9][0-9]*$ ]] || fail '--timeout 必须是正整数秒数。'
((wait_timeout <= 3600)) || fail '--timeout 不能超过 3600 秒。'

for command_name in docker python3; do
  command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name。"
done

if [[ "$skip_build" == false ]]; then
  for command_name in node npm; do
    command -v "$command_name" >/dev/null 2>&1 || fail "缺少命令：$command_name。"
  done

  java_home="$(find_jdk_25)" || fail '找不到 JDK 25。请安装 JDK 25 或将 JAVA_HOME 指向 JDK 25。'
  export JAVA_HOME="$java_home"
  export PATH="$JAVA_HOME/bin:$PATH"

  node -e '
    const [major, minor] = process.versions.node.split(".").map(Number);
    if (major < 22 || (major === 22 && minor < 12)) process.exit(1);
  ' || fail 'Operations 前端构建要求 Node.js 22.12.0 或更高版本。'
fi

for required_file in \
  "$environment_dir/.env.test" \
  "$environment_dir/private/platform.env" \
  "$environment_dir/private/operations-material.json"; do
  [[ -f "$required_file" ]] || fail \
    'mor-test 尚未完成准备；请阅读 test-environment/README.md 并先运行 scripts/prepare.py。'
done
[[ -f "$environment_dir/private/administrator-password-changed" ]] || fail \
  'mor-test 尚未完成首次初始化与管理员改密；请按 test-environment/README.md 的“首次准备”显式完成。'

[[ "$(file_mode "$environment_dir/private")" == 700 ]] || fail \
  'test-environment/private 权限必须为 0700。'
for private_file in \
  "$environment_dir/.env.test" \
  "$environment_dir/private/platform.env" \
  "$environment_dir/private/operations-material.json" \
  "$environment_dir/private/administrator-password-changed"; do
  [[ "$(file_mode "$private_file")" == 600 ]] || fail \
    "私密文件权限必须为 0600：${private_file}。"
done

if [[ "$skip_build" == false ]]; then
  [[ -x "$build_script" ]] || fail "构建脚本不存在或不可执行：$build_script。"
fi
[[ -x "$compose_script" ]] || fail "Compose 脚本不存在或不可执行：$compose_script。"
[[ -r "$smoke_script" ]] || fail "烟测脚本不存在或不可读：$smoke_script。"

case "${PYTHONOPTIMIZE:-0}" in
  ''|0) ;;
  *) fail '请取消 PYTHONOPTIMIZE；部署烟测必须执行 Python assert。' ;;
esac

for reserved_variable in DOCKER_HOST DOCKER_CONTEXT DOCKER_CONFIG BUILDKIT_HOST; do
  [[ -z "${!reserved_variable:-}" ]] || fail \
    "请取消环境变量 $reserved_variable；mor-test 只允许使用当前本机 Docker context。"
done

docker compose version >/dev/null
docker_context="$(docker context show)"
[[ -n "$docker_context" ]] || fail '无法确定当前 Docker context。'
docker_endpoint="$(docker context inspect --format '{{.Endpoints.docker.Host}}' "$docker_context")"
case "$docker_endpoint" in
  unix://*) ;;
  *) fail "当前 Docker context 不是本机 Unix Socket：$docker_context。" ;;
esac
docker info >/dev/null
"$compose_script" config --quiet
compose_ready=true

if [[ "$check_only" == true && \
      "$deployment_phase" == platform-gateway-nginx ]]; then
  current_stage="risk-bff-web 阶段 checkpoint 只读校验"
  verify_phase_checkpoint
elif [[ "$deployment_phase" == risk-bff-web && "$skip_build" == true ]]; then
  for image_name in "${operations_images[@]}"; do
    docker image inspect "$image_name" >/dev/null 2>&1 || fail \
      "缺少本地镜像 $image_name；请移除 --skip-build 后重新运行。"
  done
fi

if [[ "$check_only" == true ]]; then
  printf 'PASS：mor-test 部署前检查通过。\n'
  exit 0
fi

if ! mkdir "$deploy_lock" 2>/dev/null; then
  fail '另一个 mor-test Operations 部署正在执行；若上次进程异常终止，请确认无部署进程后删除 private/deploy.lock。'
fi
lock_acquired=true
if [[ "$deployment_phase" == risk-bff-web ]]; then
  phase_one_checkpoint_managed=true
fi
chmod 700 "$deploy_lock"

if [[ "$deployment_phase" == risk-bff-web ]]; then
  invalidate_phase_checkpoint
else
  current_stage="持锁后的 risk-bff-web 阶段 checkpoint 校验"
  verify_phase_checkpoint
fi

if [[ "$skip_build" == false ]]; then
  current_stage="应用构建"
  printf '\n[1/4] 构建 Nacos、全部 Java 服务和运营前端镜像\n'
  "$build_script"
else
  printf '\n[1/4] 跳过构建，复用当前本地镜像\n'
fi

if [[ "$deployment_phase" == platform-gateway-nginx && \
      "$skip_build" == false ]]; then
  current_stage="构建后的 risk-bff-web 阶段 checkpoint 复核"
  verify_phase_checkpoint
fi

replace_service() {
  local service_name="$1"
  current_stage="容器更新与健康检查：$service_name"
  "$compose_script" up -d --no-deps --no-build --pull never --force-recreate --wait \
    --wait-timeout "$wait_timeout" "$service_name"
}

if [[ "$deployment_phase" == risk-bff-web ]]; then
  current_stage="既有 MySQL 容器身份校验"
  mysql_container_id_before="$(docker ps -aq --no-trunc \
    --filter label=com.docker.compose.project=mor-test \
    --filter label=com.docker.compose.service=mysql)"
  case "$mysql_container_id_before" in
    ''|*$'\n'*) fail '必须存在且只能存在一个既有 mor-test MySQL 容器；拒绝创建或替换。' ;;
  esac

  current_stage="基础设施启动与健康检查"
  printf '\n[2/4] 启动并等待 MySQL、Redis、RabbitMQ、Nacos 健康\n'
  "$compose_script" up -d --no-recreate --no-build --pull never --wait \
    --wait-timeout "$wait_timeout" "${protected_infrastructure_services[@]}"
  "$compose_script" up -d --no-deps --no-build --pull never --wait \
    --wait-timeout "$wait_timeout" nacos

  current_stage="MySQL 容器身份复核"
  mysql_container_id_after="$(docker ps -aq --no-trunc \
    --filter label=com.docker.compose.project=mor-test \
    --filter label=com.docker.compose.service=mysql)"
  case "$mysql_container_id_after" in
    ''|*$'\n'*) fail '基础设施启动后必须仍存在且只能存在一个 mor-test MySQL 容器。' ;;
  esac
  [[ "$mysql_container_id_after" == "$mysql_container_id_before" ]] || fail \
    '基础设施启动意外替换了既有 mor-test MySQL 容器；立即停止后续应用更新。'

  printf '\n[3/4] 依次更新 Risk（risk-decision、compliance）、Operations BFF 和前端并等待各自健康\n'
  for service_name in "${risk_bff_web_services[@]}"; do
    replace_service "$service_name"
  done
else
  printf '\n[2/4] 依次更新 Platform、Nginx 和 Gateway 并等待各自健康\n'
  for service_name in "${platform_gateway_nginx_services[@]}"; do
    replace_service "$service_name"
  done

  current_stage="公开 HTTP 烟测"
  printf '\n[3/4] 验证登录、导航、分页、Gateway 管理读取、Refresh 和退出链路\n'
  python3 "$smoke_script"
fi

current_stage="部署状态输出"
printf '\n[4/4] 当前 mor-test 状态\n'
"$compose_script" ps

if [[ "$deployment_phase" == risk-bff-web ]]; then
  publish_phase_checkpoint
  printf '\nPASS：mor-test Risk/BFF/Web 阶段成功；本阶段未执行公开 HTTP 烟测。\n'
else
  printf '\nPASS：mor-test Platform/Gateway/Nginx 阶段与公开烟测成功，入口：http://localhost:28080\n'
fi
printf 'Vault 未在本次更新或验收；需要时请使用独立 Vault 部署流程。\n'

if [[ "$deployment_phase" == risk-bff-web ]]; then
  phase_one_checkpoint_complete=true
fi
