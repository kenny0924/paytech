#!/usr/bin/env bash
# 部署前生成回滚覆盖：回滚目标 = 此刻 mor-test 实际运行的镜像（docker inspect，只读），env 与对应候选文件相同。
# 用法：gen_rollback.sh [服务…]（缺省 6 个）。写 compose-rollback.<服务>.yaml，并把 "服务 镜像 创建时间 覆盖来源" 追加到 rollback-targets.tsv。
# 之后运行 env_check.sh：回滚文件渲染结果必须与运行容器逐键逐值相同（证明回滚只换镜像、不丢 env）。
set -euo pipefail
here="$(cd "$(dirname "$0")/.." && pwd)"
services=("$@"); [[ ${#services[@]} -gt 0 ]] || services=(ledger-service billing-service card-service client-master-service operations-console-bff platform-support-service)
stamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
for s in "${services[@]}"; do
  c="mor-test-$s-1"
  img="$(docker inspect "$c" --format '{{.Image}}')"; created="$(docker inspect "$c" --format '{{.Created}}')"
  src="$(docker inspect "$c" --format '{{index .Config.Labels "com.docker.compose.project.config_files"}}' | tr ',' '\n' | tail -n +4 | tr '\n' ' ')"
  python3 -I - "$here/compose-candidate.$s.yaml" "$here/compose-rollback.$s.yaml" "$s" "$img" "$created" "$stamp" <<'PY'
import sys, re
cand, out, svc, img, created, stamp = sys.argv[1:]
text = open(cand, encoding="utf-8").read()
body = text[text.index("services:\n"):]
body = re.sub(r"^    # .*\n", "", body, flags=re.M)                      # 去掉候选说明
body = re.sub(r"^    image: .*$", f"    image: {img}  # 部署前现网（容器创建 {created}，{stamp} 生成）", body, count=1, flags=re.M)
head = ("# 参考数据客户端组件：mor-test 回滚覆盖（scripts/gen_rollback.sh 于部署前生成，勿手改）。回滚目标 = 生成时刻现网运行镜像，env 与候选相同。\n"
        "# Platform 发布后，消费方回滚前必须先回滚 Platform（runbook 第 5 节）。\n")
open(out, "w", encoding="utf-8").write(head + body)
PY
  printf '%s\t%s\t%s\t%s\t%s\n' "$stamp" "$s" "$img" "$created" "$src" >> "$here/rollback-targets.tsv"
  echo "${s} → ${img:0:19}（created ${created}）"
done
