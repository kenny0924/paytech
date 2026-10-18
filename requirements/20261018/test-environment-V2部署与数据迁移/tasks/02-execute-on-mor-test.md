# 任务卡 02 · 阶段 2：在 mor-test 执行迁移与 V2 部署

- 分支：`test-env/v2-migration-stage2`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-mt2`；起点 `main@f37bd4c3`（阶段 1 已合入）。**运行命令一律以主仓库 `/Users/kenny/Desktop/Workspace/Code-MoR` 为 `$REPO`**（`.env.test`、`private/`、`artifacts/` 都在那里）；worktree 只用于提交脚本改动与报告。
- 用户 2026-10-18 确认：进入阶段 2；部署期间 gitlab 已由主会话停掉（结束后由主会话恢复）；不停 nexus 与 mor-local；Docker 20.5 GiB。
- 阶段 1 待确认项的决定：1、2、3 按归档方案执行（归档库保留到阶段 3 之后）；4 旧调用方规则保留；5 用评审 SQL 修改、不要求审计痕迹；6 `plan` 若报 FOREIGN，先把 plan 输出发给主会话再决定是否 `--park-legacy`；7 只迁 acquiring 执行器；10 接受 JVM 取舍；11 旧队列先导出消息数再删除；12 指纹不符就停止并回报。

## 1. 执行依据

`requirements/20261018/test-environment-V2部署与数据迁移/runbook.md` 第 0–9 节，逐步执行、逐步记录（命令、关键输出、耗时）。备份产物放 `/Users/kenny/Desktop/Workspace/Code-MoR/test-environment/private/backup-20261018/`（已在 .gitignore 的 private 下；确认后再写）。

## 2. 额外要做的

1. **验收脚本 V2 化**（阶段 1 待确认 8）：把 `test-environment/scripts/` 与各需求 `verification/` 下 `E2E_TARGET=test` 的脚本（`acceptance_flow.py`、`scenario_flow.py`、`client_api_flow.py`、`register_client_api.py`、`risk_rule_configuration_smoke.py`、`smoke.py` 等）中的 V1 库名 / 账号 / 服务名（`mor_cct`→`mor_client_master`、`mor_commerce`→`mor_acquiring`、`cct_app`→`client_master_app`、`commerce-service`→`acquiring-service`、`risk-service`→`risk-decision-service`/`compliance-service`、旧 Topic→新信封）改为 V2；优先复用第 14 步 `verification/local-e2e/` 的 V2 版本加 `E2E_TARGET=test` 分支。改动在 worktree 提交，运行时从 worktree 路径调用。
2. 启动后按 runbook 第 7 节跑验收：Client 入驻（acceptance / scenario）、Client Console（HTTP 版）、Client API 下单（test 上无 Payment 真实渠道时按脚本原夹具方式）、运营链路 smoke。
3. 写 `delivery.md`（本需求目录）与 `tasks/02-report.md`：每步结果、迁移核对输出、服务 UP 清单与内存实测（`docker stats`）、验收计数、回滚点、遗留。

## 3. 规则

- 对 mor-test 的每个写操作前先执行 runbook 对应的只读核对；备份未完成不得停服务以外的任何写操作。
- 遇到权限分类器拦截的命令（例如 root 执行 SQL、删除卷），**不要换写法绕过**，把确切命令发给主会话由其执行，然后继续。
- 不碰 `mor-local-*`、`nexus`、`gitlab`；不推送；不提交到 main。提交前缀 `test-env(stage2): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- 最终回复：阶段 2 完成判定（runbook 第 9 节）逐项结果 + commit 哈希。
