# 任务卡公共约定（财务控制台三项：人工调整与计费事实 / 期间关账与试算 / 记账规则包，阶段 4）

## 工作位置与冲突控制（必须遵守）

- **只在 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-fin3` 修改代码与文档**（detached HEAD，基于当前 main）。不得修改主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR` 下任何文件（需求目录 `requirements/20261020/finance-人工调整与计费事实/` 只读，报告写到该目录 `tasks/<卡号>-report.md` 例外）。
- 不创建分支、不提交、不推送、不 `git stash`、不回滚任何文件。
- Maven：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，在 worktree 根执行 `./mvnw -o -pl <模块> -am verify`（或 `-DskipTests package`）。**禁止 `install`**，避免把本 worktree 的 SNAPSHOT 写进其他会话共用的本地仓库 `~/.m3/repository`。
- 本机测试只用本需求专用资源，不碰其他会话使用的库：
  - MySQL：`mor_ledger_fin3`、`mor_billing_fin3`（127.0.0.1:13306）；通过 `LEDGER_TEST_DB_URL` / billing 测试库变量 覆盖。root 与应用账号口令只从 `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local` 按键读入环境变量，不打印。库不存在时用 root 创建、按 worktree 测试 DDL（`paytech-*-test/src/test/resources/init/ddl.sql`）初始化，并给 `ledger_app` / `billing_app` 授予该库权限（参照它们对 `mor_ledger` / `mor_billing` 的现有授权）。可随时 DROP 重建这两个库。
  - RabbitMQ：vhost `/mor-fin3`（不存在时经管理 API 127.0.0.1:15672 创建并给 `MOR_RABBITMQ_USER` 全权限），通过 `MOR_RABBITMQ_VHOST=/mor-fin3` 覆盖。
  - Redis：沿用本机 Redis，测试前设 `REDIS_KEY_PREFIX=<服务>-fin3`（若配置项不能由环境变量覆盖，用 `-Dredis.key-prefix=...`）。
- 不启动、停止或重建 `local-environment` 容器；不连 mor-test。

## 必读

- 需求与设计：`requirements/20261020/finance-人工调整与计费事实/requirement.md`、`technique.md`（第 2 节是合同，以它为准）、`db/`、决定记录 `agent-docs/decisions/2026-10-19-ledger-chart-hierarchy-and-account-numbers.md`（主工作区路径）。
- 规范（worktree 内）：`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`。开工前按 WRITE_API_EXAMPLE 第 1 节写几句说明（放进报告）。
- 项目习惯：Processor 模板方法（`processRequest` / `processWithinLock`）覆盖时不写 JavaDoc，目的与约束写在类注释；测试引用已有枚举 / 常量，不写字面量（JSON 样例除外）；中文 JavaDoc；方法 ≤ 30 行、嵌套 ≤ 3 层、分支必须带 `{}`；`TransactionTemplate`，不用 `@Transactional`；DataObject / Context / RequestBody / ResponseBody 用普通 class。
- 测试不使用 Mockito 行为 Stub、`@MockBean`；从 HTTP / Handler 入口触发；按场景命名 `test_*`。

## 报告

完成后写 `requirements/20261020/finance-人工调整与计费事实/tasks/<卡号>-report.md`：改动文件清单、对应 AC、实际执行的命令与结果（测试数 / 失败数）、偏离设计之处、未完成项。没有运行的检查必须写明。

## 本批补充

- `mvnw` 与 reactor 在 worktree 的 `paytech/` 下运行；测试带 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`，并用 `-Dtest='com/paytech/<服务>/**/*Test'` 限定本服务，避免跑到 Foundation 测试占用共享资源。
- `last_value`、`status` 等列名注意 MySQL 保留字。
- 测试库从 worktree 测试 DDL 建立：`mor_ledger_fin3`（ledger_app 授权）、`mor_billing_fin3`（billing_app 授权，`BILLING_TEST_DB_URL` 或该服务实际变量名覆盖）。
- 合同以 `technique.md` 为准（同目录上一级）；三个需求的需求稿见 `requirements/20261020/finance-*/requirement.md`。
