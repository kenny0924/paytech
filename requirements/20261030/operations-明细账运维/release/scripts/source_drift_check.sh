#!/usr/bin/env bash
# 发布前漂移检查（只读，可重跑）：① main 仍为 331d8e0e；② Code-MoR-plops 67 个文件与阶段 6 快照逐字节相同；③ 发布树（67 个文件 + ops-bff 路由清单测试修正 = 68）相对 331d8e0e 的
# 差异指纹不变（78ccfd1dc42e8c62）；④ mor-test 现网 6 个容器镜像 = baseline.md。任一不符输出 DRIFT 并退出 1。
set -uo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"; bad=0
head=$(git -C /Users/kenny/Desktop/Workspace/Code-MoR rev-parse main)
[[ $head == 331d8e0e* ]] && echo "main $head ok" || { echo "DRIFT main=$head"; bad=1; }
(cd /Users/kenny/Desktop/Workspace/Code-MoR-plops && shasum -a 256 -c "$here/evidence/source-plops-files.sha256" --quiet) && echo "plops 67 files ok" || { echo "DRIFT plops files"; bad=1; }
fp=$(cd /Users/kenny/Desktop/Workspace/Code-MoR-plops-rel && git add -N . && git diff --binary 331d8e0e | shasum -a 256 | cut -c1-16; git reset -q)
[[ $fp == 78ccfd1dc42e8c62 ]] && echo "release tree diff $fp ok" || { echo "DRIFT release tree diff $fp"; bad=1; }
while read -r svc id; do
  live=$(docker inspect "mor-test-$svc-1" --format '{{.Image}}' 2>/dev/null)
  [[ $live == "$id" ]] && echo "live $svc ok" || { echo "DRIFT live $svc=$live expected $id"; bad=1; }
done <<'IDS'
ledger-service sha256:51a57a3b721840b079798159f8d149a4362bbb76e763111a7183ab1bb08864e9
account-service sha256:8dfd1060d13607f68668b376541c9cf80fb6e9056f891a8b73cb74ac40db0fb5
platform-support-service sha256:0ea9cc2d6e897783080a009da6abaf5754a9599757e7f2cc90f0ae90e54514bb
operations-console-bff sha256:626a74a740cecb8ca643160b4f8423a1c071744b87f3cb1bf3a07c68ec779a3c
operations-console-web sha256:acb33e948c659b2d2acbdb0404914e88626b4c71802785de002dbd3b0c88d0d8
api-gateway sha256:d51daee3c4caecd720a8d512c6c4d7c43821cb2694cf1fa24d598449eac032cf
IDS
[[ $bad == 0 ]] && echo "NO DRIFT" || { echo "DRIFT"; exit 1; }
