# 任务卡 F · 阶段 6 mor-test 发布准备（只读核对 + 构建发布包；不改 mor-test 环境）

先读 `00-common.md`（禁令同样适用）、`delivery.md`（阶段 4 / 5 记录、遗留项中的 channel-connector 配置键）、`verification/stage5-report.md`（含部署注意 E-1—E-3）、`tasks/B-report.md` 第 6 节第 9 项（channel-connector 新配置键与 XXL）、前序先例 `requirements/20261019/finance-account明细账与ledger控制科目重构/release/{release.md,compose-candidate.yaml,source-overlay.txt}` 与其 `tasks/F-release-prep.md`、`delivery.md` 阶段 6 / 7，`agent-docs/CURRENT_IMPLEMENTATION.md` 相关节，`test-environment/README.md`、`test-environment/compose.sh`、`test-environment/compose.applications.yaml`。

## 并行会话协同（必须遵守）

- 另两个会话今晚也在发 mor-test：commercial 会话动 commercial / ops-bff / Web / Platform 与 `mor_platform` 块 95；ledger 科目分级会话等本需求提交后再动 ledger / account / ops-bff / Web 与块 91。本卡**只核对、只构建**本需求四个服务：billing、channel-connector、ledger、account；**不读写**其他容器。
- 构建基线：从 `HEAD` 建干净 worktree（scratchpad，用完删除），只叠加本需求的运行时源码：`paytech/billing/**`、`paytech/channel-connector/**`、`paytech/account/**`、`paytech/ledger/**`、`paytech/foundation/paytech-messaging/catalog/events.yaml`、`paytech/foundation/paytech-messaging/src/test/**`（若构建需要）、`test-environment/compose.applications.yaml` 中 channel-connector 一段（见下）。**不叠加** `scripts/**`、`paytech/tools/arch-rules/**`、`agent-docs/**`、`design-discuss/**`、`requirements/**`、`AGENTS.md`、`paytech/commercial/**`、`paytech/client-master/**`、`operations-console-*`、`paytech/platform-support/**`（后几类属于其他会话）。叠加前用 `git status` 与 `git diff --stat` 核对每个文件确属本需求（五线报告的改动清单），写 `release/source-overlay.txt`（文件清单 + SHA-256）。

## 允许的动作

- **只读核对 mor-test**：`docker ps` / `docker inspect`（四个容器的镜像 ID、启动时间、环境变量）、`docker exec mor-test-rabbitmq-1 rabbitmqctl list_queues / list_bindings`（只列）、经 `127.0.0.1:23306` 用 `test-environment/.env.test` 的账号 `SELECT`：`mor_billing` 9 表及事实三表 / 成本四表行数（期望 0：ledger 科目分级会话 18:56Z 已删其演示数据）、`mor_channel_connector` 13 表与 `t_payment_channel` / `t_channel_mid` 行数、`mor_account` / `mor_ledger` 行数快照、XXL `xxl_job_group` / `xxl_job_info` 中 billing / account / channel-connector 执行器与任务。**不执行任何 UPDATE / DELETE / DDL，不重启、不替换容器，不删队列。**
- **Compose（工作区，hunk 级）**：`test-environment/compose.applications.yaml` 的 `channel-connector-service` 段新增环境变量（照 billing 段）：`CHANNEL_CONNECTOR_REDIS_HOST: redis`、`_PORT: "6379"`、`_PASSWORD: ${PAYTECH_REDIS_PASSWORD:?...}`、`CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID: "0"`、`_WORKER_ID: "<未用的值，核对其他服务后选>"`、`CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID: channel-connector-test`、`XXL_JOB_*`（`APPNAME: channel-connector-service`、`IP: 172.30.73.36`、其余照 billing）、`depends_on` 加 redis / rabbitmq（若补偿组件需要 MQ 则加，否则只加 redis）与 xxl-job-admin（照 billing）。变量名以 `paytech-channel-connector-app/src/main/resources/application.yml` 为准。**只改这一段**，其他服务段一个字不动（其他会话可能同时改同一文件的别的段）。`config/nacos/channel-connector-service.yaml` 若存在且 mor-test 以 nacos profile 启动，同样补键（核对 Compose 的 `SPRING_PROFILES_ACTIVE`）。
- **构建**（安全，不部署）：worktree 叠加后 JDK 25 离线 `./mvnw -B -o -DskipTests package` 四个 app（billing、channel-connector、account、ledger）+ `-am`；按 `test-environment/java/Dockerfile` 构建 `mor-test/<服务>:billing-fact-20261019` 四个镜像，记录镜像 ID 与 JAR SHA-256；JAR 内容核对（billing 含 `BillingFactRecordedHandler`、无 `EconomicFact*` / `ChannelCostPlanController`；channel-connector 含 `ChannelCostPlanController`、`ChannelCostResolutionController`、`ChannelCostVersionActivationJobHandler`；account 含 `BillingFactRecordedBalanceHandler`；ledger 含 `BillingFactClientImpl` 且 ledger-api 含 `AutoInstructionType`）。写 `release/compose-candidate.yaml`（四个镜像固定 sha256 ID，`compose.sh -f … config --quiet` 通过）。
- **验收脚本 test 适配**：`verification/local_acceptance.py` / `acceptance-cleanup-local.sql` 已预留 `E2E_TARGET=test`，本机只做导入干跑（打印目标、库、地址，不连 mor-test 写入）；不通的地方补齐。
- 在仓库内写：`release/release.md`、`release/compose-candidate.yaml`、`release/source-overlay.txt`、`release/xxl-job-channel-cost-activation.md`（XXL 任务登记参数：执行器 `channel-connector-service`、Handler `channelCostVersionActivation`、Cron `0 */5 * * * ?`、路由策略、阻塞策略、超时、照 account 任务 14 的登记方式）。

## `release/release.md` 内容（照前序结构）

1. **范围**：镜像 4（billing、channel-connector、ledger、account；Gateway、Platform、BFF、Web 不动）；SQL 3 份（`mor_channel_connector` ← `db/channel-connector/ddl.sql`；`mor_billing` ← `db/billing/ddl.sql` + `dml.sql`；root 执行）；Compose channel-connector 段新增变量；RabbitMQ 无需改（`account.billing.*` 队列名不变，Fanout 绑定不变；核对后写明）；XXL：channel-connector 新执行器 + 任务 `channelCostVersionActivation` 登记。
2. **只读核对结果**：四容器镜像 ID / 启动时间；两库表数与行数（事实三表、成本四表 0 行是执行前提）；`account.billing.*` 队列参数与消费者数；XXL 执行器现状；Compose 环境变量现状。
3. **已定事项**（用户阶段 5–8 一并授权，无需再问）：D1 构建基线 HEAD + 本需求叠加；D2 验收后清理合成数据；D3 旧表 0 行直接 DROP；D4 XXL 登记任务；D5 若 `account.billing.dlq` 有历史消息，记录数量后不处理（旧事件名消息不会被新 Handler 消费，留待用户决定）。
4. **执行顺序**（任一步不符即停止）：核对实际环境与本清单一致 → 备份（`mor_billing`、`mor_channel_connector` 整库 dump + `mor_account` / `mor_ledger` 整库 dump 作回退点 + 四容器 `docker inspect` + RabbitMQ 定义导出 + `xxl_job_info` 相关行；`~/Code-MoR-backups/<ts>-billing-fact-test/`，`SHA256SUMS`）→ 停 account（避免旧消费者对新事件名进死信）→ SQL：channel-connector ddl → billing ddl → billing dml（每步核对表数 17 / 11、日历 1 行）→ 替换：channel-connector → billing → ledger → account（`compose.sh -f release/compose-candidate.yaml up -d --no-deps --no-build --pull never --wait`，各自 healthy、启动日志无 ERROR；channel-connector 启动后核对 XXL 执行器心跳与 Redis 连接；account 启动后核对 `account.billing.p0`—`p3` 各 1 消费者）→ XXL 登记任务并手动触发一次（期望 0 切换）→ 验收 `E2E_TARGET=test`（期望 59/59；S0 经 `http://localhost:28080` 运营登录）→ 清理合成数据并核对两库回到基线 → 更新台账与 `delivery.md`。
5. **恢复**：镜像回退到当前 ID（写明）；SQL 回滚：`mor_billing` 整库 dump 恢复（旧镜像需要旧表）、`mor_channel_connector` DROP 四表；XXL 任务停用；触发条件与顺序。
6. 执行记录（阶段 7 填写，留空表）。

## 报告

`REQ/tasks/F-report.md`：核对命令与结果摘要、构建产物与镜像 ID、发布包文件清单、Compose 改动 diff、待执行项。最终回复三行：报告路径、构建结果（四镜像 ID 前 12 位）、阻塞项（无则写无）。
