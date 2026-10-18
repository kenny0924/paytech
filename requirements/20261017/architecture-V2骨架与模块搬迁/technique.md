# V2 骨架与模块搬迁 技术方案

- 状态：阶段 2「现状差异与影响分析」评审稿，待用户确认缺口判断、受影响范围、处理边界与主要风险后进入阶段 3。
- 依据：六份只读盘点报告（[analysis/](./analysis/)：finance、risk、payment、cct、commerce、retained-systems），2026-10-17 对 `refactor-main` HEAD `4aed55d` 完成，未运行构建或测试。
- 上游：[requirement.md](./requirement.md)、[决定草案 D01–D42](../../../agent-docs/decisions/2026-10-17-target-architecture-v2.md)。

## 1. 阶段 2 结论先行

1. **代码层的跨边界耦合比预想少，数据层的耦合比预想集中。** 五个业务系统里，Risk、CCT、Commerce 没有任何跨未来 core 的写事务；Payment 只有两处（都在支付执行与卡域之间）；Finance 的六处全部源于同一张表 `t_account` 身兼会计过账科目与余额投影两职。
2. **"阶段 0 不改业务逻辑"与"每个 core 独立库"在两处无法同时成立。** Finance 的会计与账户分库、Payment 的支付执行与卡域分库都要求把现有同事务改成最终一致，这是逻辑变更。**用户决定（2026-10-17）：阶段 0 就重做这两处事务逻辑、彻底拆开，且所有逻辑服务独立进程（D09 / D36 修订）。** 因此第 5 节的分级改为：两对耦合在阶段 3 出方案、阶段 4 实现；其余拆分按搬迁步骤完成。
3. **大量目标能力只有 Schema 没有代码。** Finance 84 表有代码 34 张，Risk 85 表有生产 Mapper 约 50 张，Payment 31 表有代码 15 张；争议、出款、对账、税务、限额、结算控制、路由决策、回调全部仅 Schema。搬迁阶段这些表随 Schema 归属到新 core，不涉及代码移动。
4. **保留系统搬迁有五类路径耦合会断**，均可在骨架阶段一次修复：Platform 覆盖率门禁的 `${maven.multiModuleProjectDirectory}`（阶段 3 核实为 12 处，仅在 Platform；Commerce / Finance / Risk 用 `${project.basedir}/..`，搬迁后仍成立）、所有根的空 `<relativePath/>`、按目录名定位仓库的测试、`../` 耦合的 test-environment 与 Vault 脚本、文档相对链接（阶段 3 核实：指向旧根 520 条 / 141 文件，旧根内部出向链接 351 条 / 32 文件；`requirements-site` 测试也读取 Payment 文档路径）。完整清单见 design/09。
5. **三条设计前提需要修正**，已同步回目标架构文档：Direct Pay 目前只是保留枚举值，没有入口（能力标签由"已运行"改为"已设计"）；消费者的 Email / Phone Vault Token 现按订单存在 `t_consumer_context`、Phone 从未写入、Protect 为 Mock，所以"消费者 core 拥有 Token"是新模型；Commerce 代码在 CreateCheckout 一次性冻结 OE / 价格 / 税 / 法律事实，Schema 注释与 SQL 守卫仍描述已删除的"支付准备"阶段。

## 2. 逐系统现状与差异

### 2.1 Finance → billing / account / ledger / reconciliation / tax（+ payout 模块）

| 项 | 现状 |
|---|---|
| 模块 | `api`（62 文件）、`accounting`（344 文件，全部业务代码，单一包 `com.paytech.finance.accounting`）、`treasury` 与 `tax` 为空壳、`web`（12 个 Controller）、`test`（66 文件） |
| 表 | 84 张；已实现 34 张全部在 accounting。归属：ledger 27（全部实现）、account 1（`t_account`，争议）、billing 7（经济事实只读、渠道成本配置）、reconciliation 15（仅 `t_channel_account` 有代码）、tax 23（无代码）、payout 7（无代码）、shared 4（`t_currency_spec`、`t_finance_entity`、`t_compensation_event`、`t_command_idempotency` 可删） |
| 跨 core SQL | 9 条，全部 ledger ↔ account（余额查询联科目、投影核对联分录、试算与损益结转联账户）；1 条关账统计按 owner_type 跨入 reconciliation；1 条关账统计全库补偿事件 |
| 跨 core 事务 | 6 处写 ledger + account：`JournalPostingServiceImpl#post`（过账内核）、`TransactionPostingServiceImpl#post`（自动记账，并在事务内读 billing 与 reconciliation）、人工调整审批、损益结转、重开冲销、投影重建 |
| 跨 core 调用 | 共用一包，边由注入的 Repository 推断：ledger → billing / reconciliation / account 十余处；account → ledger（余额查询读账簿与科目） |
| 共享类型 | `AmountSource`、`OwnerType`、`OwnerRole`、`BalanceBucket`、`AccountIdentityRule`、`EconomicsType`、`AutoInstructionType`、`StandardAccountCodes`、`CurrencySpec`，以及 `Account` / `LedgerBook` / `ChartAccount` DataObject |
| 外部契约 | 入站 13 组 HTTP（ledger 11、billing 1、account 2）；无出站 Client、无 MQ、无 XXL-Job、无补偿 Handler（`FinanceModuleBoundaryTest` 断言 RabbitTemplate 不在 classpath） |
| 测试 | 39 个 auto 测试类、80 例；一个 `FinanceDataCleaner` 清 28 张表跨全部 core；测试 DDL 只建 26 表，依赖完整 Schema |

**差异判断：** 会计与账户的分库在现有逻辑下不可行（热点 1、2、4、9），阶段 0 只拆模块与包；billing、reconciliation、tax、payout 可以在阶段 0 分出模块与 Schema 归属，因为几乎没有代码。`t_account` 的拆分方案（会计侧"科目账户"与账户侧"余额账户"）留给计费切片与 D16 落地。

### 2.2 Risk → risk-decision / compliance（+ dispute 模块）

| 项 | 现状 |
|---|---|
| 模块 | 已是 `risk-api`、`risk-decision`（约 430 文件）、`risk-control`（35）、`risk-compliance`（85）、`risk-web`（48）、`risk-test`；只有 decision → control 一条内部依赖；compliance 与 decision 零 import |
| 表 | 85 张；compliance 20 + 3 待定（结算控制）、risk-decision 53（含 control 的名单）、dispute 7（仅 Schema）、shared 2 |
| 跨 core SQL | 生产零；测试一处联 `t_compensation_event` 与案件表 |
| 跨 core 事务 | 零；唯一共写对象是 `t_compensation_event` |
| 真正的耦合 | 共享数据库与补偿表、`risk-web` 的单一 DataSource / `@MapperScan` / TypeHandler 注册 / Snowflake / 请求上下文解析器、单一 `risk-api` JAR 被 CCT、Commerce、Payment、两个 BFF 五方消费、四条从 compliance / dispute 指向 decision 表的逻辑引用 |
| 外部契约 | 入站 compliance 4 组、decision 9 组；出站零；MQ 唯一生产者 `ClientReviewDecided`（CCT 消费）；2 个补偿 Handler |
| 测试 | 160 文件、596 例；没有任何测试同时涉及 compliance 与 decision；`RiskModuleBoundaryAutoTest` 锁定模块集合与依赖，`RiskDatabasePrecondition` 固定 85 表 |

**差异判断：** 阶段 0 可以完成分库（双数据源、限定事务管理器、分 Mapper 路径、分前置检查），风险在装配而非逻辑。必须先决定 `t_settlement_control` 三表归属（热点 1）；`risk-control` 建议并入 decision；`risk-api` 拆分会改变五个消费方的坐标（阶段 3 C6 已定：一次性拆分，第 6 步同分支改五方）。

### 2.3 Payment → payment / channel-connector / card

| 项 | 现状 |
|---|---|
| 模块 | 已是 `api`、`execution`（92）、`routing`（5）、`channel`（45）、`card`（38）、`web`（21，组合根）、`test`（27）；execution 依赖 card 与 routing，不依赖 channel；execution → channel 经 `PaymentChannelClient` 端口由 web 桥接 |
| 表 | 31 张，有代码 15 张；payment 10、channel-connector 5 + 3 待定（路由策略）、card 4、待定 6（渠道交互 / 回调条目 / 争议通知等以 payment 记录为目标）、`t_command_idempotency` 可删 |
| 跨 core SQL | 零 |
| 跨 core 事务 | 2 处写 payment + card：申请卡工具（卡工具 + Token 同事务）、支付成功（Action / Attempt / Intent / 渠道引用 / 凭证验证与已存卡 / 补偿事件六处写入） |
| 跨 core 调用 | execution → card 进程内直接调用（`CardService` 等）；card → execution 反向（读卡接口的准入校验在 execution 的 Processor）；web → card、web → execution 静态方法 |
| 共享类型 | 无 DataObject 跨模块；以字符串代码传递：`ActionType` ↔ `ChannelOperation`、结果枚举、能力代码在两处解析、`CaptureMode` 以字面量比较 |
| 外部契约 | 入站 4 组（Intent、Instrument、StoredCard、ChannelCardMaterial 仅接受 Platform 调用）；出站 Platform 标准 API、Risk 评估、Vault 两处；MQ 唯一生产者 `PaymentSucceeded`（手工信封已含 schemaVersion、payloadHash）；1 个补偿 Handler；无回调入口 |
| 测试 | 12 个 auto 类、约 96 例；一个测试库、`ChannelTestMapper` 清全部 15 表；6 个跨 core 测试经真实 HTTP 走完整卡支付链 |

**差异判断：** 渠道连接可以在阶段 0 分出模块与库（渠道表只读、已有端口）。卡域与支付执行的分库要把"支付成功同事务写卡表"改为最终一致，并解决读卡回调重入（热点 1），属于逻辑变更，阶段 0 只拆模块与包、补 Card / Execution 表的 ArchUnit 门禁（现在只有 Channel 表有）。渠道结果映射（`PayActionActor` / `PayOutputs` 解释 Platform 输出键）按 D06 应迁入渠道连接，这是逻辑等价的代码移动，可在阶段 0 做并以现有 6 个链路测试回归。

### 2.4 CCT → client-master / commercial

| 项 | 现状 |
|---|---|
| 模块 | `api`、`web`（280 文件）、`test`；单 DataSource |
| 表 | 38 张；client-master 21、commercial 11、shared 6（`t_consumer_legal_document`、`t_seller_disclosure` 归属待定；BCR 两表两边各需一份；补偿表；`t_command_idempotency` 可删） |
| 跨 core SQL | 11 条商务侧 `findForCheckout` 加根版本查询，用子查询扫 `t_client_entity_affiliation` 与 `t_entity_source_relation`；一条反向：`ClientPeopleMapper` 读 `t_agreement_party` 判断人员是否共享 |
| 跨 core 事务 | 零写事务；唯一跨 core 运行路径是 Checkout 解析：一个 REPEATABLE READ 只读快照读 23 张表，调 Risk 后重读并比对四类根版本 |
| 关键事实 | **商务侧没有任何写入代码**，Affiliation、OE、Scope、Source 也没有写入方；入驻审批只激活 Client 并盖章；Risk 的 Checkout 资格端口恒返回 503，成功路径未被测试覆盖；无 MQ 生产者 |
| 外部契约 | 入站 Checkout 解析（仅 Commerce 调用）、入驻与工作区若干；出站 Platform 三处、Risk 两处、Vault 一处；MQ 消费 `ClientReviewDecided`；3 个补偿 Handler，全部 client-master |
| 测试 | 12 类 53 例；16 例跨 core（固件在两边种 23 张表），37 例 client-master |

**差异判断：** 分库可在阶段 0 完成，代价是把 11 条商务查询改成以 OE 编号与合作伙伴 ID 列表为参数、把 Checkout 解析改为"client-master 返回有序候选与根版本，commercial 逐候选解析并二次校验"。这是逻辑等价改写，但快照一致性从一次数据库快照变为两次调用加版本比对，需要在阶段 3 明确。

### 2.5 Commerce → acquiring（transaction / checkout / command / client-api 共库）+ consumer

| 项 | 现状 |
|---|---|
| 模块 | `client-api`（24）、`customer-api`（10）、`web`（231）、`test`（99）；`client-contract-api` 已声明但无 import |
| 表 | 17 张；transaction 7、checkout 5、command 1、consumer 2（`t_client_stored_card_setting` 归属待定）、`t_command_idempotency` 死表、补偿表共享 |
| 跨边界 SQL / 事务 | 消费者与收单之间零；CreateCheckout 先用独立短事务建或取 Customer，再写订单八张表；收单内部有 4 条跨三个模块表的订单 CAS 语句 |
| 真正的耦合 | Payment 的已存卡以 Commerce `customer_id` 为键、`customer_no` 作为 consumerReference 传给 Payment；每次 GET checkout 与 SubmitPayment 实时读 Customer 状态与存卡开关判断资格 |
| 外部契约 | 入站 Client API 13 路由、Customer API 4 路由（接受协议在 `POST /payments` 内完成）；出站 Payment 三组、Risk、Platform Webhook；CCT、Finance 税、Platform 文档、Vault、Payment 取消与退款为固定 Mock；MQ 消费 `PaymentSucceeded`；3 个补偿 Handler；无 XXL-Job 接线 |
| 测试 | 33 类 285 方法（含参数化约 764 例）；清理在一个事务内同时删订单与 Customer |

**差异判断：** 消费者分库可在阶段 0 完成，但要同时处理：Payment 侧 `customer_id` 全局稳定、资格判断改为同 app 内经 `*-api` 调用并定义降级、消费者独立补偿表、测试清理拆分。Direct Pay 没有实现，不构成搬迁工作。

### 2.6 保留系统（Foundation、Platform、Gateway、BFF、Vault）

- 模块级无依赖环，可以组成 `paytech/` 单一 Reactor；今天根级的 Platform ↔ Payment、Platform ↔ CCT 互依赖靠本地仓库已安装的 Snapshot 引导，单 Reactor 后自然消除。
- `paytech-compensation-event` 不含 RabbitMQ；MQ 只在 Payment、Risk 发布与 Commerce、CCT 消费；Platform 的 Enforcer 禁止 AMQP。`COMPENSATION_EVENT_GUIDE.md` 第 4 节明确 JAR 不内置 RabbitMQ 路由与信封，所以 D34 的事件信封与一致性哈希应落在**新的 Foundation 模块**（如 `paytech-messaging`），不塞进 compensation-event；Platform 若要发布事件需放开 AMQP 禁令。`payload_json VARCHAR(4096)` 可能限制信封体积。
- 内部调用身份只有明文 `X-Caller-Service` 加配置白名单（Platform Webhook / Notification 两处 Properties、Vault Go 服务端 `headers.go`、Risk 与 CCT 若干 API）；D26 的替换点是 `paytech-http-client-support` 出站拦截器与 `paytech-mvc-web-starter` 入站校验，Gateway 为 WebFlux 需单独实现。
- 路径耦合清单见 [retained-systems.md 第 5、6、8 节](./analysis/retained-systems.md)：`build.sh` 逐根 `cd ... ./mvnw -pl` 与固定 `target/*-exec.jar`、`initialize.py` / `vault.py` 的固定 SQL 路径、约 30 个 PDManer 生成器与测试、Gateway 边界与 JaCoCo 脚本、`verify-java-foundation.mjs`、本地 compose 的 `../../../../test-environment/vault/Dockerfile`。
- Platform 覆盖率门禁（指令与分支 ≥ 0.90）是已知既有失败（最近 0.54 / 0.37），单 Reactor 的 `mvn verify` 会在 Platform 处失败，需限定范围或 `-fae`。
- `platform-support-web` 与 `platform-support-integration` 存在同包 `com.paytech.platform.support.integration`，api / core / app 重切时会暴露。

## 3. 测试基线（搬迁验收的"不少于"基准）

| 系统 | 测试类 | 最近记录 | 基础设施 |
|---|---|---|---|
| Foundation | 68 | 607 | MySQL / Redis |
| Platform | 80 | 736 | MySQL / Redis / MinIO |
| Gateway | 74 | 1277 | Redis |
| Operations BFF | 27 | 1386 | 无 DB |
| Client BFF | 6 | 57 | 无 DB |
| Vault | 8 Java + 29 Go | 34 | Go + MySQL |
| CCT | 12 | 53 | MySQL |
| Commerce | 33 | 764（含参数化） | MySQL / Redis / RabbitMQ |
| Payment | 12 | 96（阶段 3 按源码 @Test + @ParameterizedTest 核实） | MySQL / Redis / RabbitMQ；测试库 `mor_payment_channel_test` 不在 local-environment 中 |
| Risk | 160 | 738（源码核实；台账 596 已过时） | MySQL / Redis / RabbitMQ |
| Finance | 39 | 236（源码核实；台账 80 为早期数字，最近记录 242） | MySQL / Redis |

三个不在 `auto/` 目录、今天从不执行的测试类属既有问题，搬迁时顺手归位。仓库目前**没有任何 ArchUnit 依赖**，三个“边界测试”是手写的 POM / 正则扫描；design/02 的规则集需要在 BOM 新增 `archunit-junit5` 坐标。CCT 与 Risk 没有测试 `init/ddl.sql`，Finance 26/84、Payment 15/31，各目标服务的测试 DDL 需按 design/10 补齐。

## 4. 阶段 3 之前必须定的归属与设计点

| 编号 | 问题 | 来源 | 建议 |
|---|---|---|---|
| S1 | `t_account` 如何拆为会计科目账户与账户余额账户；过账与余额更新的最终一致方案 | Finance 热点 1、2、4 | 阶段 3 设计：候选方案为账户以事件消费会计过账结果维护余额（计费上线后改为双方消费经济事实），差异核对保留；阶段 4 实现 |
| S2 | `t_settlement_control` 三表归 compliance 还是 decision | Risk 热点 1 | 代码未实现，倾向 decision 持有、compliance 经事件感知；影响 D27 事件由谁发 |
| S3 | 卡域读卡回调重入：Platform 在 Intent 锁内回调 Payment 读卡，准入校验在 execution、CVV 只能领一次 | Payment 热点 1 | 阶段 3 设计：payment 在发起渠道调用前向 card 申请一次性有范围授权票据并随请求交给 Platform，Platform 凭票据直接向 card 读卡；支付成功对卡表的写入改为事件驱动；阶段 4 实现 |
| S4 | Checkout 解析改为两次调用后的快照一致性 | CCT 热点 1、2 | client-master 返回候选 + 根版本，commercial 解析后回查版本 |
| S5 | `t_consumer_legal_document`、`t_seller_disclosure` 归属 | CCT | 倾向 commercial（只被解析消费） |
| S6 | 消费者分库后 Payment 已存卡的 `customer_id` 稳定性与资格判断降级策略 | Commerce 热点 1、2 | ID 不变；资格读失败降级为"不可存卡" |
| S7 | 路由策略三表、`t_channel_transaction_ref`、渠道交互 / 回调条目 / 争议通知表的归属 | Payment 表清单 | 阶段 3 逐表定 |
| S8 | `risk-api` 单 JAR 拆分与五个消费方的迁移顺序 | Risk 热点 2 | 阶段 3 C6 已定：一次性拆分，无兼容期 |
| S9 | 事件信封与一致性哈希放在新 Foundation 模块；`payload_json` 上限；发布侧按聚合键串行的方式 | 保留系统 3.4 | 新模块 `paytech-messaging`；表加列需改 PDManer |
| S10 | 死表与残留：`t_command_idempotency`（五个系统）、Commerce 的准备阶段列与 PREPARING 状态、`CheckoutRepository` 等死代码 | 多系统 | 搬迁时删除，需 Owner 确认 |

## 5. 拆库可行性分级

| 分级 | 对象 | 原因 |
|---|---|---|
| 搬迁步骤内分模块、分库、独立进程 | Risk（decision / compliance）、CCT（client-master / commercial）、Commerce 的 consumer、Payment 的 channel-connector、Finance 的 billing / reconciliation / tax / payout 空壳模块 | 无跨 core 写事务；跨 core 查询可逻辑等价改写并由现有测试回归 |
| 阶段 0 内逻辑重做后分库、独立进程（第 11、12 步） | Finance 的 account 与 ledger；Payment 的 card 与 payment | 用户决定在阶段 0 彻底拆开；方案见阶段 3 的 S1、S3，实现时以现有 80 例 / 6 条链路测试加新增一致性测试为门禁 |
| 不拆 | acquiring 四模块共库（D31 已定）；Platform、Gateway、BFF、Vault 内部 | — |

## 6. 主要风险（跨系统排序）

1. `t_account` 双重身份决定了 D16 的真实成本；阶段 0 若强行分库会改动已验证的过账内核。
2. 卡域读卡回调重入与一次性 CVV：任何让读卡跨进程的改动都可能让支付在锁内失败且不可重试。
3. 结算控制归属未定会同时影响 Risk 分库、D27 事件和出款检查点。
4. CCT Checkout 解析从单快照变双调用，正确性依赖根版本比对的完整性；现有成功路径无测试。
5. `risk-api` 拆分牵动五个消费方，是搬迁中跨系统面最大的一步。
6. 事件信封与一致性哈希与现行补偿事件治理冲突，需要新决定与新模块。
7. 保留系统的路径耦合数量多但单点简单，漏改即构建失败或脚本失效。
8. Platform 覆盖率门禁既有失败会阻断单 Reactor 的整体 verify。
9. 消费者分库后 Payment 成为第三方，`customer_id` 与 consumerReference 的稳定性成为跨三方契约。
10. 大量仅 Schema 的表随归属迁移时，PDManer 规范模型与生成器必须按 core 拆分并通过门禁。

## 6a. 部署粒度与资源（D09 修订）

每个逻辑服务独立进程与库：约 25 个 JVM 加 Vault。测试环境单机需 8 核 64 GB（25 × 512 MB JVM 约 12.5 GB，中间件 4.5 GB，Doris 与 Flink 5 GB，含余量）；MySQL `max_connections` 需从默认 151 调到 400 以上；全量冷启动约 5 到 8 分钟。生产单实例计算约比组合部署多四分之一。

## 7. 对阶段 3 的输入

阶段 3 需要产出：`paytech/` 父 POM 与 api / core / app 约定（每服务一个 app）；S1 账户与会计最终一致方案与 `t_account` 拆表 SQL 草案；S3 卡域一次性授权票据与卡表事件方案；ArchUnit 规则集；每个服务的模块映射表与表归属表（含 S1–S10 的结论）；CCT 查询参数化与解析两段式合同；Payment 渠道结果映射迁移清单；Risk 双数据源装配方案与 `risk-api` 拆分；`paytech-messaging` 模块设计（信封、一致性哈希、单活消费者、Platform AMQP 禁令调整）；路径耦合修复清单；每系统测试基线与清理拆分方案。
