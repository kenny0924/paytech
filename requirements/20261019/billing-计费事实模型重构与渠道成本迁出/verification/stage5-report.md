# 阶段 5 本机综合验证报告（任务卡 E，2026-10-19）

结论：本机 channel-connector 渠道成本 → billing 计费事实 → account → ledger 链路验收通过。接口验收 **59/59**，同一脚本连续两轮（第二轮在清理脚本修正后从重新插种子开始）都是 59/59。两库结构与 Schema、测试 DDL 逐列一致。两轮清理后四库共 68 张表行数都回到运行前基线，渠道种子删除后 channel-connector 17 张表全部 0 行。只读页面回归 **27/34**，失败的 7 页都是本卡不启动的 risk-decision / compliance 服务返回 503，与前序结果相同。没有发现本需求的业务缺陷；验收脚本自身的清理 SQL 有 1 处排序规则问题，已在本线修正；另记 3 项环境 / 部署注意事项。

已读：`tasks/00-common.md`、`tasks/E-local-verification.md`、`T0/A/B/C/D-report.md`、`requirement.md` 第 5 节（AC-01—07）、`technique.md` 2.1—2.6、`delivery.md`，以及前序先例 `requirements/20261019/finance-account明细账与ledger控制科目重构/verification/{local_acceptance.py,acceptance-cleanup-local.sql,stage5-report.md,test_regression.py}`、`requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/{run-service.sh,e2e_http.py,sql/apply-e2e.sh,sql/50-channel-connector-payermax-mock.sql}`。

未执行任何 git 写命令，未操作 mor-test、RabbitMQ 或 Docker 容器，未调用 Superpowers skill，未改业务代码。

## 1. 环境事实与已执行动作

### 1.1 执行前状态（22:55 +04）

| 项 | 状态 |
|---|---|
| `mor_billing_v2e2e` | 9 张旧表；`t_economic_fact` / `_line` / `_line_link` 与渠道成本四表精确计数均为 0；`t_currency_spec` 1 行（USD）、`t_compensation_event` 0 行 |
| `mor_channel_connector_v2e2e` | 0 张表 |
| `mor_account_v2e2e` / `mor_ledger_v2e2e` | 无本卡要动的结构；account 有 12 条历史 `ECONOMIC_FACT` 消费记录（基础设施表，保留） |
| 运行中进程 | platform、billing、ledger、account、ops-bff、gateway、ops-web、nginx UP；channel-connector、commercial、client-master 等 DOWN |

### 1.2 备份

目录 `local-environment/e2e/backup/20261019225607/`（已被 .gitignore 忽略）。root 口令只从 `.env.local` 读取，未打印。

| 库 | 结果 |
|---|---|
| `mor_billing_v2e2e` | rc 0，38 040 字节，9 张表 |
| `mor_channel_connector_v2e2e` | rc 0，1 545 字节，空库 |

### 1.3 SQL（root，按序）

| 步骤 | 输出与核对 |
|---|---|
| `paytech/channel-connector/docs/database/sql/channel-connector-schema.sql` → `mor_channel_connector_v2e2e` | 执行前 0 表；rc 0；执行后 **17** 张表 |
| `local-e2e/sql/50-channel-connector-payermax-mock.sql` | 执行前核对（Channel / MID 0 行）通过；rc 0；1 Channel（`9500000000000001`，ACTIVE）、1 技术账号（DRAFT）、1 MID（`9500000000000201`，OE `AE001`，ACTIVE）、1 能力组、5 能力明细。ID 均 ≥ 1 000 000，种子可直接使用，未另行插入 |
| `REQ/db/billing/ddl.sql` → `mor_billing_v2e2e` | rc 0，17 条语句全部 OK（DROP 7、CREATE 9、SET 1）；执行后 **11** 张表 |
| `REQ/db/billing/dml.sql` | rc 0，1 行；`t_business_calendar` = `DEFAULT / UTC / ACTIVE` |
| 结构核对（只读） | 用 `SHOW CREATE TABLE` 与文件中的 CREATE 块逐列、逐索引比较（含列注释与索引注释）。billing 11 张对 `billing-schema.sql` 与测试 `init/ddl.sql`、channel-connector 17 张对 `channel-connector-schema.sql` 与测试 `init/ddl.sql`：表集合一致，0 处结构差异。唯一的文本差异是 `t_compensation_event.execute_count` 的 `INTEGER` 被 MySQL 显示为同义的 `int` |
| 第二轮种子（清理脚本修正后复验） | 再次执行 `50-*.sql`，rc 0；该轮验收后由 `--drop-seed` 删除 |

执行输出原文保存在会话 scratchpad 的 `sqlout/01—06`（未入库）。

### 1.4 构建与重启

- `./mvnw -B -o package -DskipTests -pl billing/...-app,channel-connector/...-app,account/...-app,ledger/...-app -am` 结果为 BUILD SUCCESS，用时 4.7 秒，因为是增量构建。4 个 exec JAR 的时间戳晚于对应模块与 Foundation 的全部 `src/main` 文件。抽查 JAR 内容：billing 有 `BillingFactController`、`BillingFactPayload`，没有 `EconomicFact*` / `ChannelCostPlan*`，`application.yml` 为 `unroutable-policy: FAIL`；channel-connector 有 `ChannelCostPlanController`、`ChannelCostResolutionController`、`ChannelCostVersionActivationJobHandler`。ledger 工作区的未提交改动全部属于 C 线，没有其他会话的改动被打包进去。
- `run-service.sh jars` 复制了 18 个 JAR，最后在不存在的 `data-service` JAR 上以 `cp` 报错结束，与前序 E-2 相同。复制后 platform、ops-bff、gateway 仍为 UP，见第 4 节 E-2。
- `run-service.sh stop account ledger billing channel-connector`（逐个调用）：account 与 ledger 30 秒内未退出，启动器发送 KILL；billing 正常退出；channel-connector 原本未运行。停止后 8 个业务 / 管理端口都没有监听。
- `run-service.sh start` 按 channel-connector → billing → ledger → account 逐个启动，ledger 先于 account：

| 服务 | 启动（+04） | 就绪 | 状态 |
|---|---|---|---|
| channel-connector | 23:00:20 | 6s | UP |
| billing | 23:00:27 | 4s | UP |
| ledger | 23:00:31 | 4s | UP |
| account | 23:00:35 | 5s | UP |

其余进程状态与执行前相同：platform、ops-bff、gateway、ops-web、nginx、Vault 容器 UP；commercial、client-master 等 DOWN。本线没有停止或启动它们，验收期间也没有观察到它们的状态变化。四个服务日志 ERROR 计数为 0。RabbitMQ `/mor-e2e` 上 `account.billing.p0—p3` 与 `ledger.account.p0—p3` 各有 1 个消费者。

### 1.5 `run-service.sh` 改动（只改 `channel-connector)` 一段）

新增环境变量 `CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID=0`、`CHANNEL_CONNECTOR_SNOWFLAKE_WORKER_ID=16`（其他服务用 1—15，16 未占用）、`CHANNEL_CONNECTOR_REDIS_HOST=127.0.0.1`、`CHANNEL_CONNECTOR_REDIS_PORT=16379`、`CHANNEL_CONNECTOR_REDIS_PASSWORD=$MOR_REDIS_PASSWORD`、`CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID=channel-connector-local-e2e`，以及启动参数 `--xxl.job.executor.enabled=false`。XXL 执行器在 Foundation 中默认关闭（`@ConditionalOnProperty` 无 `matchIfMissing`）。billing 在启动器中也不设置它，这里只是显式写出。变量名以 B-report 第 6 节第 9 项与 `application.yml` 为准。注释写在独立的 `#` 行：放在环境变量赋值之间的反引号注释会让后面的赋值被当成命令名。

## 2. 逐项结果

验收脚本：[local_acceptance.py](./local_acceptance.py)（`python3 -I`；`E2E_TARGET=local` 默认，已预留 `E2E_TARGET=test`）。运行记录如下：

- 第一轮 `local-environment/e2e/runtime/billing-fact-acceptance-20261019230551.json`：OE `ZE143`，方案 `E2E_CC_57ABDF`，事实 `238014815015055360`（100.00）被 `238014828608794624`（80.00）替代。
- 第二轮 `billing-fact-acceptance-20261019231227.json`：OE `ZE260`，方案 `E2E_CC_13604F`。

| AC | 结果 | 证据（检查数，均为每轮） |
|---|---|---|
| AC-01 两库 11 / 17 张表且结构逐列一致 | 通过（本机） | 脚本 3：表数 11 / 17、DEFAULT 日历 1 行。结构比对见 1.3。PDManer、R4、`billing-model.test.mjs` 属阶段 4 门禁（`delivery.md`），本卡不重跑 |
| AC-02 人工登记 Maker-Checker、引用、行、幂等、替代 | 通过 | 脚本 14 + S4 1：登记 201，DRAFT、MANUAL、factVersion 0；同内容重放 200 返回同一事实；同来源改金额 400 `BUSINESS_CHECK_FAILED`；targetBucket 给非 Owner 收方 400；行缺收方 422；复核人等于提交人 400；批准 200，APPROVED、factVersion 1，businessTime = recordedTime = approvedTime；重复批准 200 返回原结果；GET 含 refs [TICKET]、行（含付方 / 收方与 targetBucket AVAILABLE），links 空、pricing 空；by-ref 命中 1 条，未知引用类型 400 `INVALID_REQUEST`，无匹配为空列表；按账簿流列表 1 条；替代事实 factVersion 2，原事实 SUPERSEDED |
| AC-03 事件载荷、account 消费、ledger 读取与关账检查 | 通过 | 脚本 18 + S5 1：发布任务成功后删除且 `published_at` 非空；进度 pendingCount 0、publishedFactCount 1 → 2；account 记 1 条 `BillingFactRecorded / BILLING_FACT` 消费幂等记录；`CLIENT_PAYABLE / CLIENT / PENDING` 贷 100.00；流水事实 v1；进度 appliedFactCount 1；替代后批次 2 有冲回行借 100.00 与新行贷 80.00（事实 v2），净 80.00。ledger 两条 `ACCOUNT_POSTING` 指令，指令的事实 ID 等于 billing 事实，经 `/billing/v1/facts/{id}` 读取。控制账户 `CLIENT_PAYABLE` 净 100.00 → 80.00，分录借贷平衡，控制科目行 `accountEntryId` 等于流水。catch-up 返回 caughtUp 且 lastSequence 2。关账 7 步全部 SUCCEEDED，其中 `CHECK_ACCOUNT_POSTING_PROGRESS` 调用 `/billing/v1/facts/progress`，billing 不可用时失败关闭；随后 Checker 驳回，期间回到 OPEN。account-test / ledger-test 全量属阶段 4（58/58、234/234） |
| AC-04 渠道成本 Maker-Checker、命中、Job | 通过（Job 登记 N/A） | 脚本 17：创建方案 201；同 Channel 第二个方案 400；版本 201，DRAFT、摘要 64 位；提交 → PENDING_APPROVAL；复核人等于编制人、摘要不一致均 400 `BUSINESS_CHECK_FAILED`；批准后因生效时间已过直接 ACTIVE；方案当前版本 1，库中指针指向 ACTIVE 版本；版本详情含 `componentId`；命中 200，返回方案、版本 1、内容摘要、范围 1、`channelAgreementRef` 与 1 个组件（PROCESSING_FEE、0.029、0.3、HALF_UP，componentId 与详情一致）。负例：不同国家、asOfTime 早于生效时间、命中时 MID 不存在，均 400 `CHANNEL_COST_NOT_RESOLVED`；新版本范围 MID 不存在 400 `BUSINESS_CHECK_FAILED`（消息含 MID，不落版本）；范围 OE 与 MID 主体不一致 400 `BUSINESS_CHECK_FAILED`。**到期切换 Job 登记为 N/A**：本机没有 XXL 调度中心，执行器已禁用。JAR 内 Handler `channelCostVersionActivation` 存在；行为由 B 线 `ChannelCostVersionActivationJobTest` 6/6 覆盖，调度中心登记在阶段 6。`CHANNEL_COST_AMBIGUOUS` 需要人为损坏版本区间才能触发，由 `ChannelCostResolutionTest` 覆盖 |
| AC-05 旧类 / 表 / 接口删除 | 通过（运行面） | 脚本 2：billing 库没有 `t_economic_fact*` / `t_channel_cost*`；billing 的 `/billing/v1/economic-facts/{id}` 与 `/channel-cost-plans/v1/{code}` 返回 404。源码 grep 属阶段 4 |
| AC-06 规则 / 模型 / 站点测试 | N/A | 阶段 4 门禁范围（`delivery.md`「测试 / 验收证据」），本卡不重跑 |
| AC-07 mor-test | N/A | 阶段 6—7；脚本已预留 `E2E_TARGET=test` |
| S0 基础资料 | 通过 | 脚本 3：新 OE 日历、当月 OPEN 期间、OE / USD 账簿均经 Gateway → ops-bff 新建。前序记录的 OE 与账簿已在前序清理中删除，不能复用。本链路不涉及合作方与平台服务费，因此没有新建财务主体 |

**合计 59/59，连续两轮。** 按运行记录中的标签计数为 AC-01 3、AC-02 14、AC-03 18、AC-04 17、AC-05 2、S0 3、S4 1、S5 1。

**只读回归 27/34（旁证）。** 用前序 `test_regression.py` 原样运行，截图在 scratchpad，未入库。页面总数断言 34/34，4 个财务页通过。失败的 7 页是 `/risk/*` 6 页与 `/client-onboarding/reviews`，原因都是 risk-decision 与 compliance 未启动导致 503，与前序报告一致，和本需求无关。

## 3. 清理与基线核对

`python3 -I local_acceptance.py --cleanup --drop-seed` 按最新运行记录执行 [acceptance-cleanup-local.sql](./acceptance-cleanup-local.sql)：ledger、account、billing、channel-connector 四段分别以各自的应用账号执行，`@channel_seed` 段删除任务卡第 3 步插入的渠道种子。

| 轮次 | 结果 |
|---|---|
| 第一轮 | 首次执行时 channel 段报 `ERROR 1267 Illegal mix of collations`，前三段已完成。修正后重跑，四库 68 张表回到基线，种子删除后 channel-connector 17 张表全部 0 行 |
| 第二轮（重新插种子 → 59/59 → 清理） | 一次通过：四库 68 张表回到基线，channel-connector 17 张表全部 0 行 |

验收没有向 `mor_platform_v2e2e` 写入数据。两库结构变更与 DEFAULT 日历属于授权 SQL，保留不回滚。

## 4. 发现的问题

**本需求缺陷**：无。

**验收脚本自身（已在本线修正）**

- **V-1。** `acceptance-cleanup-local.sql` 的 channel 段原来直接比较 `plan_code = @plan_code`。会话变量的排序规则是 `utf8mb4_general_ci`，列是 `utf8mb4_0900_ai_ci`，MySQL 报 1267。现改为先用 `BINARY` 比较取方案主键，再按主键删除。第二轮已一次通过。阶段 7 复用时，mor-test 也会走这条语句。

**环境 / 部署注意（既有或配套事项，未处理）**

- **E-1（既有，影响加重）。** `/mor-e2e` 上 11b 时期的 `ledger.billing.p0—p3` 队列仍绑定 `billing.events`，并且没有消费者。本卡两轮共 4 条 `BillingFactRecorded` 滞留在其中，`p1 / p2 / p3` 从 1 / 1 / 2 增至 2 / 2 / 4。`ledger.billing.dlq` 16 条、`account.billing.dlq` 2 条是历史残留。本卡未授权操作 RabbitMQ，所以没有删除。billing 现在是 `unroutable-policy: FAIL`。mor-test 删除旧队列后，必须确认 `account.billing.*` 绑定仍在，否则发布会因不可路由而失败重试。technique 2.5 的 AC-07 步骤已写了"删除旧绑定前确认 0 消息"，建议阶段 6 一并删除 ledger 对 `billing.events` 的旧队列。
- **E-2（既有，补充风险）。** `run-service.sh jars` 会把 18 个服务的 JAR 原地覆盖进运行目录，包括其他会话正在运行的 platform、ops-bff、gateway，最后在缺失的 `data-service` JAR 上报错。本次覆盖后三者仍为 UP。但正在运行的 JVM 若再懒加载类，读到的可能是新 JAR 的内容。建议后续让 `jars` 支持只复制指定服务。
- **E-3。** account 与 ledger 收到 SIGTERM 后 30 秒内没有退出，启动器发送了 KILL。重启后两者消费正常，验收通过。原因可能是 RabbitMQ 监听器或补偿执行器的关闭等待时间较长，本卡没有深入排查。部署到 mor-test 时，可以留意容器的停止宽限期。

## 5. 本机服务现状

channel-connector、billing、ledger、account 以新 JAR 运行（UP）。platform、ops-bff、gateway、ops-web、nginx 保持其他会话的原状。两库为新结构；四库验收数据与渠道种子均已清理。需要停止时，用 `run-service.sh stop <服务>` 逐个停止。
