#!/usr/bin/env bash
# 把本需求在 worktree 中的全部改动（含未跟踪、删除）导出为一个二进制补丁，供在新的基线提交上重建（rebase）候选镜像。
# 用临时 GIT_INDEX_FILE，不碰共享 index。用法：export_patch.sh <worktree> <基线提交> <输出 .patch>
set -euo pipefail
wt="${1:?worktree}"; base="${2:?base rev}"; out="${3:?patch}"
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
cd "$wt"
GIT_INDEX_FILE="$tmp/index" git read-tree "$base"
GIT_INDEX_FILE="$tmp/index" git add -A .
GIT_INDEX_FILE="$tmp/index" git diff --cached --binary "$base" -- . ':(exclude)paytech/.mvn/maven.config' > "$out"
echo "patch $(shasum -a 256 "$out" | cut -c1-16) files $(grep -c '^diff --git' "$out")"
# 重建：git worktree add --detach <新目录> <新基线>；cd <新目录>；git apply --3way --index=false <patch>（冲突即停，交回研发）；
#      cp <旧 worktree>/paytech/.mvn/maven.config paytech/.mvn/；build_apps.sh <新目录>；镜像 tag 改为 refclient-20261028b。
