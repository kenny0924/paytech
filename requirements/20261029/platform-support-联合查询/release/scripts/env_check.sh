#!/usr/bin/env bash
# 渲染本目录候选 / 回滚覆盖（compose.sh config，只读），与 mor-test 运行容器 env 逐键逐值比较（只打印键名）。可重跑。
# ops-web 覆盖由 jq-web 提供后加入 SERVICES。
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
SERVICES=${SERVICES:-"platform-support-service operations-console-bff"}
cd /Users/kenny/Desktop/Workspace/Code-MoR
for f in candidate rollback; do
  for s in $SERVICES; do
    [[ -f "$here/compose-$f.$s.yaml" ]] || { echo "$f $s: no override file"; continue; }
    ./test-environment/compose.sh -f "$here/compose-$f.$s.yaml" config --format json "$s" > "$tmp"
    python3 -I "$here/harness/envcmp.py" "$tmp" "$s" "$f"
  done
done
