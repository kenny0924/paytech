# 任务卡 09 · 数据服务骨架与 Doris / Flink CDC 容器

- 分支：`refactor/step-09-data-service-skeleton`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step09`；起点：`refactor-main@36c0506`（第 00 步已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 09 步；决定 D25、D35；需求 [requirement.md](../requirement.md) 2.2 第 9 步与验收第 5 条。

## 1. 输入文档（先完整读取）

- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md) 全文；[design/02-archunit-rules.md](../design/02-archunit-rules.md) 全文；[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1 节 data-service 行与端口汇总。
- `agent-docs/decisions/2026-10-17-target-architecture-v2.md` 中 D25、D35。
- `local-environment/compose.yaml`、`compose.scheduler.yaml`、`README.md`、`.env.example`、`mysql/init/01-create-databases-and-users.sh`、`scripts/verify-local-environment.mjs`、`agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md`。
- 第 00 步产物：`paytech/pom.xml`、`paytech/tools/arch-rules`、`paytech/tools/instance-manifest`、`paytech/data-service/README.md`。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/TECHNOLOGY_STACK.md`（Doris / Flink 为 D35 新增，本步在 `paytech/data-service/README.md` 记录版本，不改 TECHNOLOGY_STACK 正文）。

## 2. 新建清单

### 2.1 `local-environment` 新 Profile `analytics`

- 新文件 `local-environment/compose.analytics.yaml`（与 `compose.scheduler.yaml` 同风格，`name: mor-local`，网络 `mor`，`restart: "no"`，`mem_limit`，healthcheck，端口只绑定 `127.0.0.1`）：
  - `doris-fe`、`doris-be`：Apache Doris 3.0.x 官方镜像单节点（FE 查询端口映射 `127.0.0.1:19030:9030`，HTTP `127.0.0.1:18030:8030`）；root 密码经 `MOR_DORIS_ROOT_PASSWORD`；启动后由 `doris-init` 一次性容器创建库 `mor_data_service` 与只读账号 `data_service_app`（密码 `MOR_DATA_SERVICE_DORIS_PASSWORD`）。
  - `flink-jobmanager`、`flink-taskmanager`：Flink 1.20 官方镜像加 Flink CDC 3.x pipeline（`mysql` source、`doris` sink）连接器，连接器 JAR 用 `local-environment/flink/Dockerfile` 在构建期下载并校验 SHA-256（镜像、版本、校验值写在 Dockerfile 注释与 README）；Web UI `127.0.0.1:18081` **与本机 Nexus 冲突，改用 `127.0.0.1:18091`**。
  - `flink-cdc-submit`：一次性容器，用 `flink-cdc.sh` 提交 `local-environment/flink/pipelines/demo-currency-spec.yaml`。
- MySQL 侧：`compose.yaml` 的 mysql `command` 加 `--binlog-format=ROW`、`--binlog-row-image=FULL`、`--gtid-mode=ON`、`--enforce-gtid-consistency=ON`、`--server-id=1`（8.4 默认已开 binlog；显式写出）；`mysql/init/01-create-databases-and-users.sh` 新增 CDC 用户 `cdc_reader`（`MOR_CDC_DB_PASSWORD`，仅 `SELECT, REPLICATION SLAVE, REPLICATION CLIENT`，`SELECT` 只授予白名单库）。
- `.env.example` 新增 `MOR_DORIS_ROOT_PASSWORD`、`MOR_DATA_SERVICE_DORIS_PASSWORD`、`MOR_CDC_DB_PASSWORD`（示例值，与现有格式一致）。
- `scripts/verify-local-environment.mjs` 把新 Profile 的服务、镜像、端口、卷纳入校验；`local-environment/README.md` 增加 `analytics` Profile 的启动、资源需求（给出实测内存）与停止说明。
- `local-environment/compose.sh`（若存在）支持 `--profile analytics`。

### 2.2 白名单约定与演示管道

- `paytech/data-service/cdc/whitelist.yaml`：公开契约文件，结构 `services: [{service, database, tables: [{name, maskedColumns: [], excludedColumns: []}]}]`；本步只登记演示表一张：`mor_finance.t_currency_spec`（阶段 0 它仍在 Finance 库；第 07 步后随归属变为 `mor_platform`，白名单文件同步改）。文件头注释说明：进入白名单 = 允许同步到数仓；含 PII 的列必须在 `maskedColumns`；未列出的表不得同步（D25）。
- `local-environment/flink/pipelines/demo-currency-spec.yaml`：Flink CDC pipeline 定义，source `mysql`（`cdc_reader`，`tables: mor_finance.t_currency_spec`），sink `doris`（库 `mor_data_service`，自动建表），并行度 1。
- 演示步骤写入 `paytech/data-service/README.md`：先用 `finance-service/docs/database/sql/finance-schema.sql` 中 `t_currency_spec` 的 DDL 与 `finance-initial-data.sql` 的对应插入在本地 `mor_finance` 建表并初始化（人工执行，命令给出），启动 `analytics` Profile，提交管道，再在 MySQL 执行一条 `UPDATE`，在 Doris 中查询到变更。**不要把 Finance 的 Schema 应用写进任何自动化脚本。**

### 2.3 `paytech/data-service` 四模块

- 坐标：`paytech-data-service-api` / `-core` / `-app` / `-test`，父 POM `paytech-parent`（`relativePath` `../../build/paytech-parent/pom.xml`），服务聚合 POM `paytech/data-service/pom.xml`；在 `paytech/pom.xml` 取消 `data-service` 注释。
- `-api`：空壳但合法（一个 `package-info.java`，`com.paytech.data.api`），不放接口。
- `-core`：`com.paytech.data`；`WhitelistCatalog`（`interface + Impl`）读取并校验 `cdc/whitelist.yaml`（表名唯一、列名非空、库名符合 `mor_` 前缀）；无 Mapper、无业务。
- `-app`：Spring Boot 启动类 `DataServiceApplication`，`spring.application.name=data-service`，端口 18120 / 管理 19120，依赖 `paytech-mvc-web-starter`；`application.yml` 中 Doris JDBC（MySQL 协议）DataSource 以 `data-service.doris.enabled` 开关控制（默认 `true`，`mock` Profile 为 `false`，关闭时不创建 DataSource Bean）；健康检查暴露 Doris 连通性指示器（开关关闭时不注册）。
- `-test`：`auto/` 下 `DataServiceApplicationAutoTest`（`mock` Profile 启动，健康 UP，白名单目录可读且含演示表）、`WhitelistCatalogAutoTest`（非法白名单样例被拒）、`ArchitectureAutoTest`（引入 `paytech-arch-rules` 执行全部规则，这是第一个真实服务模块上的规则执行，必须通过）；`manual/` 下 `DorisConnectivityManualTest`（需 `analytics` Profile 在跑，查询演示表行数）。
- `paytech/data-service/README.md` 改写为：职责（D25）、模块、端口、库、白名单契约、本地演示步骤、版本（Doris、Flink、Flink CDC 镜像与校验值）。

## 3. 测试与验证要求

1. `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home && cd paytech && ./mvnw -B verify` 全绿（含 arch-rules 对 data-service 的规则执行）。
2. `node scripts/verify-local-environment.mjs` 通过。
3. `docker compose --env-file .env.local --profile analytics config` 成功；`--profile analytics up -d` 后 Doris FE / BE、Flink JobManager / TaskManager 健康；记录 `docker stats` 一次的内存占用。
4. 演示管道：按 README 步骤完成一次 `UPDATE` → Doris 可查到新值；把命令与输出写进报告。若本机资源不足以同时跑 base + analytics，如实记录并说明哪一步失败。
5. `docker compose --profile analytics down` 后 base Profile 不受影响（`ps` 列表）。
6. `git status` 干净。

## 4. 禁止事项

- 不动 `local-environment` 中 base Profile 的现有服务定义，除 2.1 列出的 mysql 参数与用户脚本；不改 `.env.local`（它不在仓库中）。
- 不写任何查询实现、不接 Client / Partner 查询模板、不做租户过滤（D25 后续切片）。
- 不把白名单之外的表写进任何管道文件。
- 不执行业务 Schema 的自动化应用；不改 `finance-service/`。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 5. 交付物

1. 短分支提交，前缀 `refactor(step-09): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/09-data-service-skeleton-report.md`（随分支提交）：改动摘要 ≤ 30 行、第 3 节每项命令与结果、镜像版本与校验值、实测资源、待确认项（例如 Doris 单节点参数、是否需要 `analytics` 进入 mor-test）。
3. 最终回复两行：报告路径 + 一句话结论。
