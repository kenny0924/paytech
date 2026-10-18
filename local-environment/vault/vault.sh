#!/usr/bin/env bash
# 本机 Docker 中的 Vault（compose profile: vault）。
#
#   vault.sh build   交叉编译 Linux arm64 的 vault-server/vault-probe 到 vault/bin/image/，
#                    从 e2e/secrets.env 只取 Vault 需要的 4 个值写入 vault/private/vault.env（0600，不打印），再构建镜像
#   vault.sh up      启动（或按新镜像重建）vault-service；依赖的 mysql/nacos 需已在运行
#   vault.sh stop    停止 vault-service（停机时从 Nacos 注销）
#   vault.sh status  查看容器状态与健康
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
env_dir="$(dirname "$here")"
workspace="$(dirname "$env_dir")"

# compose 会插值整个文件；.env.local 尚未改名的 Redis 密码以旧名 MOR_REDIS_PASSWORD 补位（与运行中的 redis 相同）。
compose() {
  (
    cd "$env_dir"
    set -a; . ./.env.local; set +a
    export PAYTECH_REDIS_PASSWORD="${PAYTECH_REDIS_PASSWORD:-${MOR_REDIS_PASSWORD:-}}"
    docker compose --env-file .env.local --profile base --profile vault "$@"
  )
}

case "${1:-}" in
  up) compose up -d --no-deps vault-service; exit ;;
  stop) compose stop vault-service; exit ;;
  status) compose ps vault-service; exit ;;
  build) ;;
  *) echo "用法：vault.sh build|up|stop|status" >&2; exit 2 ;;
esac

go_bin="${GO_BIN:-go}"
go_bin="$(command -v "$go_bin")" || { echo 'Go 1.27.1 is required; set GO_BIN.' >&2; exit 1; }
if [[ "$(GOTOOLCHAIN=local "$go_bin" env GOVERSION)" != go1.27.1 ]]; then
  echo 'Go 1.27.1 is required; this build never downloads another toolchain.' >&2
  exit 1
fi
secrets="$env_dir/e2e/secrets.env"
[[ -f "$secrets" ]] || { echo "缺少 $secrets：先按客户入驻本机全链路说明生成本机材料。" >&2; exit 1; }

image_dir="$here/bin/image"
mkdir -p "$image_dir"
export GOTOOLCHAIN=local CGO_ENABLED=0 GOOS=linux GOARCH=arm64 GOFLAGS=-mod=readonly
(cd "$workspace/paytech/vault" && "$go_bin" build -trimpath -buildvcs=false -o "$image_dir/vault-server" ./cmd/vault-server)
"$go_bin" build -trimpath -buildvcs=false -o "$image_dir/vault-probe" "$workspace/test-environment/vault/probe.go"

mkdir -p "$here/private" && chmod 700 "$here/private"
python3 -I - "$secrets" "$here/private/vault.env" <<'PY'
import os, sys
wanted = {'VAULT_LOCAL_AES_ROOT_KEY': 'VAULT_LOCAL_AES_ROOT_KEY', 'VAULT_LOCAL_AES_KEY_ID': 'VAULT_LOCAL_AES_KEY_ID',
          'E2E_NACOS_USERNAME': 'VAULT_NACOS_USERNAME', 'E2E_NACOS_PASSWORD': 'VAULT_NACOS_PASSWORD'}
values = {}
for line in open(sys.argv[1], encoding='utf-8'):
    line = line.strip()
    if line.startswith('#') or '=' not in line:
        continue
    key, value = line.split('=', 1)
    if key in wanted:
        values[wanted[key]] = value.strip().strip('"').strip("'")
missing = sorted(set(wanted.values()) - set(values))
if missing:
    sys.exit('secrets.env 缺少：' + ', '.join(missing))
fd = os.open(sys.argv[2], os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
with os.fdopen(fd, 'w', encoding='utf-8') as out:
    for key in sorted(values):
        out.write(f'{key}={values[key]}\n')
PY

compose build vault-service
echo 'Built mor-local/vault-service:development; no containers were started.'
