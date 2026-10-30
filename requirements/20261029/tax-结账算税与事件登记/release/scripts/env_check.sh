#!/usr/bin/env bash
# 渲染本目录候选 / 回滚覆盖（compose.sh config，只读），与 mor-test 运行容器 env 逐键逐值比较（只打印键名）。可重跑。
# 改写自 REQ-03 release/scripts/env_check.sh（envcmp.py 同源）。期望：回滚 6 行 missing=[] extra=[] valuediff=[]；
# 候选 missing=[] valuediff=[]，extra 只有本需求新增键（tax 17、acquiring 1、ops-bff 1，其余 0）。
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
cd /Users/kenny/Desktop/Workspace/Code-MoR
for f in candidate rollback; do
  for s in tax-service acquiring-service billing-service account-service operations-console-bff operations-console-web; do
    ./test-environment/compose.sh -f "$here/compose-$f.$s.yaml" config --format json "$s" > "$tmp"
    python3 -I "$here/harness/envcmp.py" "$tmp" "$s" "$f"
  done
done
