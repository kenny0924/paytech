#!/usr/bin/env bash
# 阶段 7 第 ① 步：候选是否仍代表"现网 + 本需求"。只读，可重跑。任一 DRIFT 即停，回阶段 6 重建（runbook P-2）。
#  1) tax 研发 worktree Code-MoR-tax 的 378 个文件与阶段 6 迁入时逐字节相同（evidence/tax-worktree-files.sha256）；
#  2) main 自 30361be7 起没有触及 paytech/ 与 operations-console-web/（否则现网可能已含新代码，候选会回退）；
#  3) 6 个服务的现网镜像仍是阶段 6 基线（= compose-rollback 文件中的 sha256；tax 现网容器镜像记录已删除，按容器读到的 ID 比较）。
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
cd /Users/kenny/Desktop/Workspace/Code-MoR-tax
if shasum -a 256 -c --quiet "$here/evidence/tax-worktree-files.sha256"; then echo "tax worktree: same"; else echo "tax worktree: DRIFT"; fi
cd /Users/kenny/Desktop/Workspace/Code-MoR
changed=$(git diff --name-only 30361be7 HEAD -- paytech operations-console-web/src operations-console-web/i18n | wc -l | tr -d ' ')
echo "main HEAD $(git rev-parse --short HEAD); code paths changed since 30361be7: ${changed} $([ "$changed" = 0 ] && echo same || echo DRIFT)"
for pair in tax-service=sha256:aeebac7adf46021fe10258fc15d2612ea1cc0c1632e75790572504d3e463aa0a \
  acquiring-service=sha256:e0a1f618a050e30b85059aa4214fea43bedeb35cf48438374d444e735e107299 \
  billing-service=sha256:cf580cfbd05786e55c0e09b9749e1c1ba1a4fe54acef383e355bef7ac6c05650 \
  account-service=sha256:17831407811fefb3e747208d0bda1a4c5e0481e19ea4423ee98f28723cfa6284 \
  operations-console-bff=sha256:4f08f1e601beb791f8e61c088e471e8839fc65783acbf513e412e5fcaf200b6b \
  operations-console-web=sha256:a9d5000088e9bc239c85734c967a17f4d7f40f43efae94438f2241bba8078aa9 \
  platform-support-service=sha256:49f710118adb1d074de4bb4aa711e88cebec1544f3bfb00db7b4b12f213329d9; do
  s=${pair%%=*}; want=${pair#*=}; live=$(docker inspect "mor-test-$s-1" --format '{{.Image}}')
  echo "$s $([ "$live" = "$want" ] && echo same || echo "DRIFT live=$live")"
done
