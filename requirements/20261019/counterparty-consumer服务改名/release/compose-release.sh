#!/usr/bin/env bash
# 用分支 worktree 的 compose 定义 + 主工作树的 project-directory / .env.test 操作 mor-test（只用于本需求合入前的部署）。
set -euo pipefail
main_env=/Users/kenny/Desktop/Workspace/Code-MoR/test-environment
wt_env=/Users/kenny/Desktop/Workspace/Code-MoR-counterparty/test-environment
release_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
set -a; . "$main_env/.env.test"; set +a
exec docker compose --project-name mor-test --project-directory "$main_env" --env-file "$main_env/.env.test" \
  -f "$wt_env/compose.yaml" -f "$wt_env/compose.applications.yaml" -f "$wt_env/compose.scheduler.yaml" \
  -f "$release_dir/compose-candidate.yaml" --profile base --profile applications --profile scheduler "$@"
