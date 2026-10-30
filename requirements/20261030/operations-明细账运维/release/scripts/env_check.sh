#!/usr/bin/env bash
# 渲染本目录候选 / 回滚覆盖（compose.sh config，只读），与 mor-test 运行容器 env 逐键逐值比较（只打印键名）。可重跑。
# 期望：10 行全部 missing=[] extra=[] valuediff=[]（本需求不改任何 env）。改写自 payout release/scripts/env_check.sh。
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
cd /Users/kenny/Desktop/Workspace/Code-MoR
for f in candidate rollback; do
  for s in ledger-service account-service platform-support-service operations-console-bff operations-console-web; do
    ./test-environment/compose.sh -f "$here/compose-$f.$s.yaml" config --format json "$s" > "$tmp"
    python3 -I "$here/harness/envcmp.py" "$tmp" "$s" "$f"
  done
done
