#!/usr/bin/env bash
# 渲染本目录候选 / 回滚覆盖（compose.sh config，只读），与 mor-test 运行容器 env 逐键逐值比较（只打印键名）。可重跑。
# 用法：env_check.sh（在任意目录执行）
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
cd /Users/kenny/Desktop/Workspace/Code-MoR
for f in candidate rollback; do
  for s in ledger-service billing-service card-service client-master-service operations-console-bff platform-support-service; do
    ./test-environment/compose.sh -f "$here/compose-$f.$s.yaml" config --format json > "$tmp"
    python3 -I "$here/scripts/envcmp.py" "$tmp" "$s" "$f"
  done
done
