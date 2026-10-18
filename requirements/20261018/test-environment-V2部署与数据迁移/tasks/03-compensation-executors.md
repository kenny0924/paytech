# 任务卡 03 · mor-test 补齐补偿扫描执行器（XXL-Job）

- 分支：`test-env/compensation-executors`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-mt3`；起点 `main`。运行命令的 `$REPO` 是主仓库 `/Users/kenny/Desktop/Workspace/Code-MoR`（`.env.test` 在那里）；代码改动与报告提交到 worktree。
- 背景（2026-10-18 验证 messaging 连接恢复时暴露）：mor-test 只为 platform-support 与 acquiring 登记了 XXL 执行器与 `compensationEventScan` 任务。compliance 的 `CLIENT_REVIEW_RESULT` 补偿任务因交换机瞬时缺失失败后停在 ERROR（`mor_compliance.t_compensation_event`，next_execute_at 已过），没有执行器扫描就永不重试。有补偿事件目录的服务共 10 个：account、acquiring、billing、card、client-master、compliance、consumer、payment、platform-support、risk-decision（见各 `paytech/<svc>/compensation-event-catalog.yaml`）。

## 要求

1. **compose**：为缺失的 8 个服务在 `test-environment/compose.applications.yaml` 增加与 acquiring 同形的 `COMPENSATION_INSTANCEID`、`XXL_JOB_*` 环境变量（appname = 服务发现名，IP 用该服务的固定 IP，端口 9999，日志路径 /tmp/xxl-job），并核对各 app 的 `application.yml` 是否读取这些键（client-master、account 等若尚未接 XXL Starter，先按 `agent-docs/COMPENSATION_EVENT_GUIDE.md` 与现有 acquiring 的装配补齐，属最小接线，不改业务逻辑）。`compose config` 渲染通过，`test_deploy_script.py` 等 Python 测试通过（inventory 钉值按需重钉并说明）。
2. **XXL 登记**：参照 `requirements/20261018/test-environment-V2部署与数据迁移/sql/50-xxl-job-acquiring.sql` 与阶段 2 报告中经 Admin API 的做法，为 8 个服务创建执行器分组与 `compensationEventScan`（`*/30 * * * * ?`，启动）；card 另登记 `cardOrphanInstrumentPurge`（阶段 2 待确认第 7 项）。评审 SQL 或 Admin API 脚本放本需求 `sql/` 或 `scripts/`，由主会话执行需要 root 的部分（被拦截就把命令列出来）。
3. **部署与验证**（mor-test）：重建改了配置的服务（`up -d --no-deps --wait`），XXL Admin 中各执行器在线且任务运行；30 秒内 compliance 的 ERROR 任务被重试成功，`mor_client_master` 中对应申请状态变为 RETURNED（可用 `E2E_TARGET=test` 的 `acceptance_flow.py` 全跑一遍确认 28/28 与 41/41）；`docker stats` 记录新增内存。
4. 文档：阶段 2 报告待确认第 7 项与 `agent-docs/CURRENT_IMPLEMENTATION.md` 的 mor-test 条目同步；本需求 `delivery.md` 加一行。
5. 禁止：不碰 `mor-local-*`、`nexus`、`gitlab`；不操作 mor-test 的 MySQL 根账号（交主会话）；不调用 Superpowers skill；不提交到 main；不推送。提交前缀 `test-env(executors): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。报告 `tasks/03-report.md`。最终回复两行：commit 哈希 + 验证结果。
