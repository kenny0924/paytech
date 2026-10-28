#!/usr/bin/env bash
# 渲染本目录候选 / 回滚覆盖（compose.sh config，只读），与 mor-test 运行容器 env 逐键逐值比较（只打印键名）。可重跑。
# envcmp.py 抄自参考数据客户端组件需求 release/scripts。
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; tmp="$(mktemp)"; trap 'rm -f "$tmp"' EXIT
cd /Users/kenny/Desktop/Workspace/Code-MoR
for f in candidate rollback; do
  for s in client-master-service client-console-bff operations-console-bff client-console-web operations-console-web; do
    ./test-environment/compose.sh -f "$here/compose-$f.$s.yaml" config --format json "$s" > "$tmp"
    python3 -I "$here/scripts/envcmp.py" "$tmp" "$s" "$f"
  done
done
