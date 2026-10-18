#!/usr/bin/env bash
set -euo pipefail

environment_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workspace_dir="$(dirname "$environment_dir")"
go_bin="${GO_BIN:-go}"
if ! go_bin="$(command -v "$go_bin")"; then
  echo 'Go 1.27.1 is required; set GO_BIN to the prepared Go executable.' >&2
  exit 1
fi
go_bin="$(cd "$(dirname "$go_bin")" && pwd)/$(basename "$go_bin")"
build_dir="$(mktemp -d /private/tmp/vault-test-build.XXXXXX)"
cleanup() {
  # 只清理本次 mktemp 创建的精确目录，不触碰用户缓存或其他构建产物。
  case "$build_dir" in
    /private/tmp/vault-test-build.*) rm -rf -- "$build_dir" ;;
  esac
}
trap cleanup EXIT
export GOCACHE="$build_dir/cache" GOTMPDIR="$build_dir" TEST_TELEMETRY_DIR="$build_dir/telemetry"
export GOTOOLCHAIN=local GOENV=off GOPROXY=off GOSUMDB=off GOWORK=off
if [[ "$("$go_bin" env GOVERSION)" != go1.27.1 ]]; then
  echo 'Go 1.27.1 is required; this build never downloads another toolchain.' >&2
  exit 1
fi

target_arch="${GOARCH:-arm64}"
if [[ "$target_arch" != arm64 ]]; then
  echo 'This Mac test deployment builds Linux ARM64 only; do not mix binary and image architectures.' >&2
  exit 1
fi
export CGO_ENABLED=0 GOOS=linux GOARCH="$target_arch" GOFLAGS=-mod=readonly

(
  cd "$workspace_dir/paytech/vault"
  "$go_bin" build -trimpath -buildvcs=false -o "$build_dir/vault-server" ./cmd/vault-server
)
"$go_bin" build -trimpath -buildvcs=false -o "$build_dir/vault-probe" "$environment_dir/vault/probe.go"

artifact_dir="$environment_dir/artifacts/vault"
mkdir -p "$artifact_dir"
cp "$build_dir/vault-server" "$artifact_dir/vault-server"
cp "$build_dir/vault-probe" "$artifact_dir/vault-probe"
"$environment_dir/compose.sh" build vault-service
echo 'Built only the Vault test image; no containers were started.'
