# 任务卡 F 报告 · 阶段 6 mor-test 发布准备（2026-10-19）

结论：发布包已就绪，四个候选镜像构建完成。mor-test 现状满足执行前提：billing 旧 7 表 0 行，channel-connector 尚无渠道成本表，`account.billing.dlq` 0 条，channel-connector 尚无 XXL 执行器。发现 3 项需要阶段 7 知道的事实：channel-connector 回退镜像需要替身（已构建）、mor-test 没有渠道 / MID（已补验收种子）、billing 模块变更 SQL 与发布 SQL 的两张消费表定义不一致（发布用 REQ 文件，模块文件待 Owner 同步）。没有阻塞项。

## 1. 已读

`tasks/00-common.md`、`tasks/F-release-prep.md`、`delivery.md`、`verification/stage5-report.md`（含 E-1—E-3）、`tasks/B-report.md` 第 6 节、`tasks/C-report.md` 第 2 节（改动清单）、T0 / A / D 报告（改动归属比对）、前序 `requirements/20261019/finance-account明细账与ledger控制科目重构/release/{release.md,compose-candidate.yaml,source-overlay.txt}`、其 `tasks/F-release-prep.md` 与 `tasks/G-report.md`（XXL 登记方式）、`delivery.md` 阶段 6 / 7，`requirements/20261019/ledger-会计科目分级维护/delivery.md`（确认其在独立 worktree 实现），`test-environment/compose.sh`、`compose.applications.yaml`、`compose.scheduler.yaml`、`java/Dockerfile`，`paytech-channel-connector-app` 的 `application.yml` / `pom.xml` 与 `ChannelCostVersionActivationJobHandler`，`paytech-compensation-event/pom.xml`，`requirements/20261018/test-environment-V2部署与数据迁移/scripts/register_xxl_executors.py` 与 `sql/50-xxl-job-acquiring.sql`，`verification/local_acceptance.py`、`acceptance-cleanup-local.sql`、`e2e_http.py`（目标与探针部分）、`50-channel-connector-payermax-mock.sql`。`agent-docs/CURRENT_IMPLEMENTATION.md` 与 `test-environment/README.md` 未逐节精读：环境事实以本次只读核对为准。

未执行任何 git 写命令，也没有建 worktree，构建源码用 `git archive HEAD | tar` 解包到 scratchpad。没有对 mor-test 执行 UPDATE / DELETE / DDL，没有重启或替换容器，没有删队列，没有写 XXL。没有调用 Superpowers skill，没有读写其他会话的容器或文件。

## 2. 只读核对（2026-10-19 19:17—19:19Z）

| 命令 | 结果摘要 |
|---|---|
| `docker ps`、`docker inspect`（4 容器） | 30 个容器 healthy。channel-connector `07bd0636…`（2026-10-18 13:38:58Z），billing `403fc7de…`、ledger `fb7c661d…`、account `b356a4f3…`（均为 `subledger-20261019`，10:45Z）。`xxl-job-admin` 19:17:06Z 刚重启（非本线） |
| `docker image inspect` | `07bd0636…` 已不在镜像库（containerd 存储；`:development` 已重建为 `640d6ba4…`，其 JAR `c926322f…` ≠ 运行 JAR `4e1b6ef2…`）。另三个回退镜像存在 |
| 容器环境（口令类已遮蔽） | channel-connector 只有 DB、Nacos 发现、`PAYTECH_INSTANCE_ID`、`SPRING_PROFILES_ACTIVE=test`；无 Redis / Snowflake / XXL 变量 |
| `SELECT COUNT(*)`（应用账号，四库 62 表） | `mor_billing` 9 表：经济事实 3 表与渠道成本 4 表全 0，`t_currency_spec` 1；`mor_channel_connector` 13 表全 0（`t_payment_channel` 0、`t_channel_mid` 0）；`mor_account` 9 表全 0；`mor_ledger` 31 表：规则包 2 / 版本 2 / 规则行 36、科目表 1、科目 29、币种 1、关账科目规则 7，其余 0 |
| `SHOW GRANTS` | `billing_app` / `channel_connector_app` 只有本库 DML，DDL 需 root |
| `rabbitmqctl list_queues / list_bindings`（`/mor-test`） | `account.billing.p0`—`p3` 0 消息 / 各 1 消费者，参数 quorum + SAC + DLX + delivery-limit 5；`account.billing.dlq` 0；`ledger.account.*` 正常；绑定 `billing.events → account.billing.hash`；无 `ledger.billing.*` |
| `xxl_job_group / info / registry`（`xxl_job` 账号） | 分组 2—12，无 channel-connector；任务 2—14（14 为 `accountBalanceMaturity`）；心跳 10 个执行器，无 channel-connector |
| 事件目录 diff | 只改 `eventType`、`aggregateType`、`payloadSchemaRef` 与注释；producer / consumers 不变，队列与交换机名不变 → RabbitMQ 无需改 |
| 发布 SQL 与模块变更文件 | channel-connector 一致；billing 在两张消费表上不同（见第 6 节 1） |

## 3. 构建产物

源码：HEAD `08c8f794` + 460 个文件（A 201 / M 128 / D 131：billing 241、channel-connector 99、ledger 65、account 53、messaging 2）。归属核对：四服务与 messaging 的全部未提交改动都在本需求五线报告的改动范围内。ledger 科目分级会话在 `../Code-MoR-chart` 独立实现，主工作区没有它的改动；commercial 会话只改 `paytech/commercial` 与 arch-rules，不叠加。构建后复核主工作树 461 行（含 Compose）哈希与 `git status` 条目数，与清单一致。

| 动作 | 结果 |
|---|---|
| `./mvnw -B -o -DskipTests -pl <四个 app> -am clean package`（JDK 25） | BUILD SUCCESS，19 秒 |
| `./mvnw -B -o -pl <四个 *-test> -am test-compile` | BUILD SUCCESS（未运行测试） |
| JAR 内容核对 | 全部符合任务卡：billing 有 `BillingFactRecordedHandler`、无 `EconomicFact*` / `ChannelCostPlanController`；channel-connector 有 `ChannelCostPlanController`、`ChannelCostResolutionController`、`ChannelCostVersionActivationJobHandler`、xxl-job-core；account 有 `BillingFactRecordedBalanceHandler`；ledger 有 `BillingFactClientImpl`，ledger-api 有 `AutoInstructionType` |

| 镜像（`mor-test/<服务>:billing-fact-20261019`） | 镜像 ID | JAR SHA-256 |
|---|---|---|
| channel-connector | `sha256:194961f11db549ab57fa8ccb9f94ee51d737dee46cb588b86e2a40905539eff3` | `3092f217a1e9…` |
| billing | `sha256:eeec27f923b7f55f7f3f6b7a8b32528b063fe9a64197615b1e645d3f8cf6540e` | `92d34e4a9eb4…` |
| ledger | `sha256:0e9390666b839739d7b418783d70682040d761d0e3e5634be86f89076d4de020` | `e39ba806935a…` |
| account | `sha256:ad0621b12a80731ed550729ff7709383a7dee28017bc736c3ea9e2152a58ce00` | `709a3aeda936…` |
| channel-connector 回退替身 `:rollback-20261019` | `sha256:7eea94df1e63c58ec8e326abc7f28070f635560dafd9eb53cb2f09eb8e384d26` | `4e1b6ef2921d…`（= 运行中 JAR，`docker cp` 只读取出） |

每个镜像内 `/app/application.jar` 哈希已核对。构建上下文为 scratchpad 私有目录，没有写 `test-environment/artifacts/`。构建源码、上下文与解包 JAR 已删除，镜像保留。

## 4. 发布包文件

| 文件 | 内容 |
|---|---|
| [release/release.md](../release/release.md) | 范围、只读核对、已定事项 D1—D5、10 步执行顺序、恢复、执行记录空表 |
| [release/compose-candidate.yaml](../release/compose-candidate.yaml) | 4 个镜像 ID；`compose.sh -f … config --quiet` 通过 |
| [release/source-overlay.txt](../release/source-overlay.txt) | 461 行（460 + Compose），清单 SHA-256 `ba38482fc4f7a4e010935dd62a91c547451ee871b457093e0e2a1a063e105934` |
| [release/xxl-job-channel-cost-activation.md](../release/xxl-job-channel-cost-activation.md) | 分组与任务登记参数（Admin API）、验证、回退、不登记 `compensationEventScan` 的理由 |
| [release/test-channel-seed.sql](../release/test-channel-seed.sql) | 新增：mor-test 验收合成渠道 / 账号 / MID（无能力行） |
| [verification/local_acceptance.py](../verification/local_acceptance.py) | 修改：`--drop-seed` 允许 test 目标；`--cleanup` 只取同目标运行记录；文档串补 test 种子说明。`py_compile` 与 `E2E_TARGET=test` 导入干跑通过（未连接 mor-test 写入；干跑产生的 `__pycache__` 已删） |

## 5. Compose 改动（工作区，只改 channel-connector-service 段）

```diff
@@ -498,8 +498,25 @@ services:
       MOR_CHANNEL_CONNECTOR_DB_PASSWORD: ${MOR_CHANNEL_CONNECTOR_DB_PASSWORD:?prepare test secrets}
       PAYTECH_INSTANCE_ID: channel-connector-test
+      # 渠道成本方案写入口的资源锁（paytech-redis-starter）与主键（Snowflake，Worker 32 未被其他服务占用）。
+      CHANNEL_CONNECTOR_REDIS_HOST: redis
+      CHANNEL_CONNECTOR_REDIS_PORT: "6379"
+      CHANNEL_CONNECTOR_REDIS_PASSWORD: ${PAYTECH_REDIS_PASSWORD:?prepare test secrets}
+      CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID: "0"
+      CHANNEL_CONNECTOR_SNOWFLAKE_WORKER_ID: "32"
+      CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID: channel-connector-test
+      # XXL 执行器：任务 channelCostVersionActivation 在 xxl-job-admin 登记（20261019 billing 计费事实需求 release/xxl-job-channel-cost-activation.md）。
+      XXL_JOB_ADMIN_ADDRESSES: http://xxl-job-admin:8080
+      XXL_JOB_ADMIN_ACCESSTOKEN: ${MOR_XXL_JOB_ACCESS_TOKEN:?prepare test secrets}
+      XXL_JOB_EXECUTOR_ENABLED: "true"
+      XXL_JOB_EXECUTOR_APPNAME: channel-connector-service
+      XXL_JOB_EXECUTOR_IP: 172.30.73.36
+      XXL_JOB_EXECUTOR_PORT: "9999"
+      XXL_JOB_EXECUTOR_LOGPATH: /tmp/xxl-job
+      XXL_JOB_EXECUTOR_EXCLUDEDPACKAGES: org.springframework.,spring.
     depends_on:
       mysql: { condition: service_healthy }
+      redis: { condition: service_healthy }
       nacos: { condition: service_healthy }
```

与任务卡的差异：`depends_on` 没有加 `rabbitmq`，因为补偿组件不依赖 MQ，channel-connector 也不引入 messaging。也没有加 `xxl-job-admin`，因为卡片要求照 billing，而 billing 段没有这一项。Worker 选 32：mor-test 已占用 18—25、27—31，Onboarding 用了 26。Nacos 文件 `paytech/channel-connector/config/nacos/channel-connector-service.yaml` 没有改，mor-test 以 `test` Profile 启动，不加载它。

## 6. 待主会话确认 / 阶段 7 须知

1. **billing 模块变更 SQL 漂移**：`paytech/billing/docs/database/sql/changes/2026-10-19-billing-fact-model.sql` 的 `t_consumed_event`、`t_consumed_aggregate_version` 仍是旧写法：短注释、表排序规则 `utf8mb4_0900_bin`。REQ `db/billing/ddl.sql` 已与 Schema、测试 DDL 一致。发布执行 REQ 文件；模块文件需由 billing Owner（A 线 / 主会话）在阶段 8 提交前同步。本卡不在该路径授权内，未改。
2. **channel-connector 回退**：用替身 `7eea94df…`，不要用 `:development`，它的内容与运行中不同。
3. **验收种子**：mor-test 没有渠道 / MID，阶段 7 需在验收前执行 `test-channel-seed.sql`（第 4 节第 6 步），验收后 `--cleanup --drop-seed` 删除。种子没有能力行，Payment 路由不会选中它。
4. **并行会话**：`xxl-job-admin` 在 19:17Z 被其他会话重启过。阶段 7 第 1 步需与 commercial 会话（ops-bff / Web / Platform）确认此刻没有替换或验收在进行。本需求验收经 Gateway → ops-bff 访问 ledger。
5. 前序遗留（不阻塞，未做）：account 的 `ACCOUNT_BILLING_SERVICE_URL` 无用、未设 `PAYTECH_INSTANCE_ID`，与前序 F 报告相同。

## 7. 待执行项（阶段 7，任务卡 G）

按 [release.md](../release/release.md) 第 4 节执行：复核、备份、停 account、三份 SQL、四个替换、验收种子、XXL 登记、59/59 验收、清理，最后写第 6 节执行记录。
