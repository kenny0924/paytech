# reconciliation 渠道资金对账切片 1：技术方案

> 状态：第 1 节已确认（2026-10-20）；第 2 节已确认（2026-10-20）。需求见 [requirement.md](./requirement.md)。

## 1. 现状差异与影响（阶段 2）

依据：2026-10-20 只读调查（源码、Schema、Compose、注册 DML、事件目录），路径见各行。阶段 3 修订：上传方式改为分段导入，见 2.1 D-01。

### 1.1 表：18 → 13

| 目标表 | 来源 | 改动 |
|---|---|---|
| `t_clearing_account` | `t_channel_account` 改名 | 去掉 Journal 语义；加 `clearing_account_code`、`channel_code`、三项配置（`unmatched_wait_days`、`cost_tolerance_amount`、`cash_grace_days`）、`settlement_cash_account_id`；MID 移到子表 |
| `t_clearing_account_mid` | 新增 | `uk(mid_id, currency)` |
| `t_platform_txn` | `t_channel_open_item` + `t_channel_debit_item` 合并 | `direction` 区分应收应付；`billing_fact_id` 唯一；冻结渠道交易引用三项；进度金额 `matched / settled_amount`、`expected_fee / actual_fee` |
| `t_channel_statement` | `t_channel_settle_batch` 改名 | 加 `revision`（唯一键改三列）、`status` 含 `VOIDED`、`cash_confirmed_amount`、`reconciled_time` |
| `t_channel_statement_line` | `t_channel_settle_item` 改名 | 吸收匹配（`platform_txn_id`、`match_method`、`matched_time`）与成本（`actual_fee`），加 `raw_attributes`（JSON）、`resolution_id`、`match_status`、`version` |
| `t_cash_account` | 原表 | 加 `account_purpose`（吸收绑定槽） |
| `t_bank_statement` | 原表 | 加 `period_start / end`、`opening / closing_balance` |
| `t_bank_statement_line` | `t_bank_statement_item` 改名 | 加 `match_status`、`version` |
| `t_expected_bank_movement` | 新增 | `uk(movement_type, source_id)`；本期只有 `CHANNEL_SETTLEMENT` |
| `t_bank_match` | `t_bank_recon_match` 改名 | 指向预期银行流水而非批次 / 出款 / 税款三个可空列；删绑定版本列 |
| `t_recon_exception` | 原表 | 类型重分；加 `recon_layer`、`blocking`、带符号 `difference_amount`、`opened_time`；`source_type` 收敛为本库六类对象 |
| `t_recon_resolution` | 原表 | 类型收为三种；加 `classification_code`、带符号 `amount`；删 `instruction_id` |
| `t_compensation_event` | 原表 | 不变 |
| 删除 5 张 | `t_duplicate_capture_item`、`t_channel_transaction_match`、`t_channel_cost_confirm`、`t_channel_cost_alloc`、`t_cash_account_binding` + `_ver` | — |

库内全部表本机与 mor-test 均无业务代码写入；阶段 3 核对两环境行数，若为 0 行则按 billing 先例**整库重建**，不写逐表 ALTER。

### 1.2 能力缺口（逐系统）

**reconciliation（主体，几乎从零开始）**

| 项 | 现状 | 本期要补 |
|---|---|---|
| 代码 | 19 个 Java 文件，只有 `ChannelAccount` 只读 Repository 与三个配置类 | 全部领域代码；旧 `ChannelAccount` 删除 |
| api 模块 | 只有 lombok，没有合同 | 内部 HTTP 合同（解析、当前银行账户、运营命令与查询）、四类事件载荷 |
| 依赖 | 没有 compensation-event、messaging、billing-api、Redis 锁、XXL、vault-client、Nacos 发现 | 按 account（消费 billing 事件）与 channel-connector（Vault、锁、XXL）补齐 |
| 配置 | 只有 `application.yml`（端口 18115 / 19115，库与 Snowflake 变量） | RabbitMQ、Redis、Vault、XXL、channel-connector 地址；`config/nacos/reconciliation-service.yaml`；`compensation-event-catalog.yaml` |
| 测试 | `MockTestBase` 不启动外部 HTTP Mock，测试 DDL 18 张 | channel-connector MID 与 Vault 的 DTO Mock 端点、MQ 测试发布与消费、13 张测试 DDL |
| 模型生成 | 由 Finance 生成器 `05-channel-cash-recon.mjs` + 02 中两张成本表投影，期望 18 张；另被 `generate-oe-client-schemas.mjs:19` 与 `oe-client-model.test.mjs:55` 引用 | 脱离 Finance 生成器（`FINANCE_SERVICES` 去掉 reconciliation），新增 `reconciliation-model.test.mjs`（仿 `billing-model.test.mjs`）；OE 生成器引用按阶段 3 核查保留或去掉 |
| 表归属 | `table-ownership.yaml:293-312` 列 18 张 | 改 13 张，step-added / removed 计数同步 |

**billing（Q-02 合同小改）**

| 项 | 现状 | 改动 |
|---|---|---|
| `FactRefType` | 21 个值，无 `CHANNEL_TRANSACTION_REF`、`RECON_RESOLUTION` | 加两个值 |
| `BillingFactRefPayload` | 只有 `refType`、`refId` | 加可空 `externalReferenceType`、`externalTransactionId` |
| `t_billing_fact_ref` | `id, fact_id, ref_type, ref_id, create_time` | 加两列可空；变更 SQL 放 `docs/database/sql/changes/`，模型、Schema、测试 DDL 同步 |
| `billing-model.test.mjs` | 断言 `ref_type` 注释以 `TICKET、FX_DEAL、FX_QUOTE。` 结尾、事实引用字段数 | 按新注释与字段数更新 |
| 事件目录 | `BillingFactRecorded` 的 `consumers: [account]` | 加 `reconciliation`；`schemaVersion` 是否递增阶段 3 定（只加可空字段，account 不受影响） |

**事件与消息（Foundation 目录）**

- `events.yaml` 新增四个事件（生产方 reconciliation，消费方暂为空或 billing 待接，阶段 3 按目录校验规则定）；`payloadSchemaRef` 只是文本，真实结构是 reconciliation-api 的载荷类。
- 消费拓扑现成：`billing.events` → `reconciliation.billing.hash` → 分区队列；消费者照抄 account 的 `BillingFactRecordedBalanceHandler`（`@DomainEventHandler`、按 eventId 去重、拒绝进 DLQ）。

**channel-connector（只读调用，不改）**

- `GET /channel-connector/v1/mids` 可按 `entityNo`、`status`、`externalMidCode` 查，返回 `channelCode`、`entityNo`、`status`。**MID 上没有币种**，币种在详情的能力组 `capabilities[].currency`。挂 MID 时核对：MID 存在、状态有效、`channelCode` 与 `entityNo` 和清算账户一致、能力组里有该币种。

**Vault**

- 客户端与用法现成（`ConfigureChannelAccountProcessor.java:82` 的 `vaultClient.protect(...)`）。缺：新数据类型（如 `CASH_ACCOUNT_NUMBER_V1`）与 reconciliation 的 PROTECT 规则；本机 `local-environment/vault/policy.local.json`、mor-test `test-environment/vault/policy.json` 与 `mor_vault` DML 各补一处。

**Operations BFF**

- 现成模式：`FinanceClientConfiguration` 按服务建 `HttpServiceProxyFactory`，`integration/Finance*Client` + `impl`，`ext/rest/Finance*Controller`；加 reconciliation 一组即可。
- **文件上传是新模式：** 整个仓库没有 multipart。Platform 的文件服务只有入驻、审核两种专用用途（预签名上传），没有通用用途，也没有给内部服务读内容的接口。仍按 Q-03：BFF 收 CSV、传内容与 SHA-256 给 reconciliation；需在 BFF 与 Gateway 两处放开请求体大小限制（阶段 3 核对 Gateway 上限）。
- **权限校验位置与 AC-14 的措辞不同：** 现行做法是 Gateway 路由按 `HUMAN_PERMISSION` 校验权限，BFF 不逐接口校验，Maker ≠ Checker 在核心服务校验（人工调整、渠道成本都这样）。建议沿用：AC-14 改为"Gateway 路由按权限点拦截 + reconciliation 校验提交人 ≠ 复核人"，不在 BFF 另加一层。

**Platform 注册**

- 唯一执行来源 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`；ID 为 `900000000000BBBnxx`，已用到块 122，本需求用**块 123**（阶段 3 先查 mor-test 现网 `mor_platform` 确认未占用）。
- 照财务先例：先插休眠（授权 REVOKED、菜单与路由 DISABLED），另出激活脚本。新增权限点 `FINANCE_RECONCILIATION_OPERATE`、`FINANCE_RECONCILIATION_APPROVE`（名称阶段 3 定），读权限沿用还是另建也在阶段 3 定。

**operations-console-web**

- 菜单由 Platform 下发的 Console Resource 驱动，页面要在 `pageCapabilities.ts` 登记，且须与 `console-resource-capabilities.json` 一致（`financeWorkflow.test.ts:60` 校验），懒加载在 `ConsolePage.tsx:45`。照人工调整页的文件组织（页面、Tab、Drawer、workflow、hooks、测试）。本仓库前端没有 FormData 上传先例。

**部署环境**

| 环境 | 现状 | 要补 |
|---|---|---|
| 本机 | 库与账号已建（`01-create-databases-and-users.sh:107`），不在 local-environment Compose；e2e `run-service.sh` 只配了库与 worker 14 | run-service 加 MQ、Redis、Vault、XXL、channel-connector 变量 |
| mor-test | `compose.applications.yaml:732-760` 已有容器（IP .41，worker 19），只依赖 mysql、nacos | 加 rabbitmq、redis、vault 依赖与变量，XXL 执行器（照 account `:710-718`），登记 `compensationEventScan` 与等待期满任务；Operations BFF 加 reconciliation 地址 |

**文档**

- `FINANCE_DATABASE.md` §4.5（第 400 行）、§6.2（第 644 行）标注被取代；`paytech/reconciliation/README.md` 重写；台账。

### 1.3 风险与依赖

| 项 | 影响 | 处理 |
|---|---|---|
| 没有真实 PAYMENT 事实 | 端到端只能用合成事实 | 已按 Q-01；测试与 mor-test 由测试发布方发 `BillingFactRecorded` |
| 四类事件暂无消费方 | 发出的事件在 MQ 无人处理 | 事件目录允许空消费方则留空；否则阶段 3 定一个验收用的临时测试队列，不长期留 |
| 首个 multipart 上传 | Gateway / BFF 请求体限制、日志不应打印文件内容 | 阶段 3 定上限与日志脱敏 |
| 共享文件并行改动 | `events.yaml`、`table-ownership.yaml`、Platform 初始化 DML、`pageCapabilities.ts`、生成器脚本可能同时被其他会话修改 | 提交时按 hunk 拆分，用私有索引 |
| 工作量 | 13 张表 + 约 30 个接口 + 7 个页面 | 阶段 4 分线：T0 合同（api、事件、billing 小改）→ A 领域与持久化 → B 匹配与银行 → C 处置与事件 → D BFF / Web / 注册 → E 模型与文档 |

## 2. 方案、接口与数据（阶段 3）

### 2.1 本阶段新定的设计点

| 编号 | 问题 | 定法 |
|---|---|---|
| D-01 | **文件上传（修订 Q-03）。** Gateway 路由请求体有绝对上限 64 KiB（`GatewayRoutePolicyValidator.MAX_BODY_BYTES`），mor-test Nginx 也只放 128k，5 MB 的 CSV 进不来 | **改为分段导入**：浏览器本地解析 CSV、算整个文件的 SHA-256，按"开始 → 分段追加行（每段 ≤ 100 行）→ 完成"三步发 JSON。账单 / 对账单先建成 `RECEIVING`，完成时一次性校验，通过进入 `MATCHING` / `IMPORTED`，不通过整单 `REJECTED` 并返回逐行原因。不放宽 Gateway 上限，不接 Platform 文件服务，仓库仍没有 multipart。单张账单上限 20,000 行 |
| D-02 | 切片 1 没有超期任务，平台流水、预期银行流水、银行流水的异常从哪来？（AC-11 要求这三类能归类） | 加一个"人工开异常"命令：运营对这三类对象手工开不阻塞的异常，类型分别为 `PLATFORM_ITEM_UNSETTLED`、`BANK_AMOUNT_MISMATCH`、`BANK_LINE_UNMATCHED`，差异金额由系统按对象当前未完成金额算出，不让人填。切片 2 的超期任务以后自动开同样的异常 |
| D-03 | 银行账户要复核，但表已收到 13 张 | 复核直接放在 `t_cash_account` 上：新建 → `PENDING_VERIFICATION`，复核通过 → `ACTIVE`；申请关闭只记 `pending_action = CLOSE`，复核通过 → `CLOSED`，拒绝则清空申请。不改用途：换用途就是关旧户、开新户 |
| D-04 | 银行账户的 Vault 列 | Vault V1 不返回盲索引、记录版本、元数据 Hash，旧表这四列删掉；只存 `account_vault_token` 和导入前在本服务算好的 `account_last4`。数据类型 `CASH_ACCOUNT_NUMBER_V1`，Token 模式 `REFERENCE`，用途 `RECON_CASH_ACCOUNT`。银行账号重复不做库内唯一约束 |
| D-05 | 实际成本什么时候算、什么时候发 | 匹配时把账单行的费用（行上的 `fee_amount` 加同交易号 FEE 行）记到行的 `actual_fee`，累加到平台流水；平台流水完全匹配时比对预估：差额超过清算账户的 `cost_tolerance_amount` 开阻塞异常 `COST_DEVIATION`（处置用"接受"解除阻塞，不改金额）。账单对平时，本账单让其变成 `SETTLED` 的每笔平台流水，若 `actual_fee ≠ expected_fee`，发一条 `ActualChannelCostConfirmed`（聚合 `PLATFORM_TXN:{id}`） |
| D-06 | 平台流水的金额与预估费用 | `expected_amount` 取事实 `grossAmount`；`expected_fee` 取组件 `CHANNEL_COST` 各行 `postedAmount` 合计，没有就是 0（落实 Q-05）。清算账户取引用 `CLEARING_ACCOUNT`，渠道交易引用取 `CHANNEL_TRANSACTION_REF`（`refId` + 两列外部引用），来源对象取 `PAYMENT_ATTEMPT` 或 `CHANNEL_REFUND` |
| D-07 | 更正或作废的 billing 事实 | 切片 1 只接受没有 `supersedesFactId` 的事实；带更正关系的 PAYMENT / REFUND 事实拒收进 DLQ、记日志，人工处理（真实自动事实上线后随 billing 需求 B 再定） |
| D-08 | 四类事件没有消费方 | 事件目录允许 `consumers: []`（`YamlEventCatalog` 只校验是列表），四个事件都先登记为空列表，billing 需求 B 接入时再加。验收用测试消费者绑定临时队列观察，不长期留 |
| D-09 | `BillingFactRecorded` 加可空字段要不要升 `schemaVersion` | 不升，保持 1：只新增可空字段，account 不读，旧载荷仍然有效 |
| D-10 | 匹配在哪个事务里跑 | 账单完成导入的事务只做校验和落库，并登记本地补偿任务 `CHANNEL_STATEMENT_MATCH`；任务锁账单，逐行匹配、开异常、尝试对平。新平台流水到达时，对同清算账户里处于等待期的行登记同一任务。对账单导入后、预期银行流水生成后登记 `BANK_MATCH_SUGGEST` 任务 |
| D-11 | 锁 | 新写 Processor 用 Foundation `AbstractLockProcessor`：账单级（导入完成、匹配、作废、对平）、银行流水级（确认、手工匹配）、异常级（提交、复核）、清算账户级（挂 MID）。确认匹配要同时锁流水和预期银行流水时，先锁流水再锁预期，ID 升序 |
| D-12 | 本机与 mor-test 的库怎么改 | 两环境 `mor_reconciliation` 没有服务写入；阶段 6 先查行数，全部为 0 则 `DROP` 后按新 Schema 重建（照 billing 先例），否则停下汇报。本机重建在阶段 4 单独请你授权 |

### 2.2 数据库：13 张业务表 + 2 张消息去重表

字段级 DDL 见 [db/reconciliation/ddl.sql](./db/reconciliation/ddl.sql)，是本需求要执行的唯一来源（整库重建，执行前库须为空）；阶段 4 由 PDManer 模型生成同样内容的 `reconciliation-schema.sql` 和测试 DDL，三者逐字一致。要点：

| 表 | 唯一键 / 主要索引 | 说明 |
|---|---|---|
| `t_clearing_account` | `uk(clearing_account_code)`；`idx(entity_no, currency, status)` | 渠道、经营主体、币种、三项配置、`settlement_cash_account_id`、`version` |
| `t_clearing_account_mid` | `uk(mid_id, currency)`；`idx(clearing_account_id)` | 移除 MID 即删行（未来平台流水仍认原清算账户 ID） |
| `t_platform_txn` | `uk(billing_fact_id)`；`uk(clearing_account_id, reference_type, external_transaction_id, direction)`；`idx(clearing_account_id, status, create_time)` | 金额列 `expected / matched / settled_amount`、`expected / actual_fee` |
| `t_channel_statement` | `uk(clearing_account_id, channel_batch_ref, revision)`；`idx(clearing_account_id, status, settlement_date)` | 加 `imported_line_count`、`file_name`、`file_content_hash`、`maker_id`；状态多一个 `RECEIVING`（D-01） |
| `t_channel_statement_line` | `uk(statement_id, line_no)`；`idx(reference_type, external_transaction_id)`（跨账单查重与匹配）；`idx(platform_txn_id)` | `raw_attributes` JSON（≤ 4 KiB / 行） |
| `t_cash_account` | `idx(entity_type, entity_id, currency, account_purpose, status)` | D-03、D-04 |
| `t_bank_statement` | `uk(cash_account_id, statement_ref)`；`idx(cash_account_id, period_end)` | 期间、期初期末余额、`RECEIVING / IMPORTED / REJECTED` |
| `t_bank_statement_line` | `uk(cash_account_id, bank_transaction_ref)`；`idx(statement_id, line_no)`；`idx(cash_account_id, match_status, value_date)` | 冗余 `cash_account_id` 做去重键；`matched_amount` |
| `t_expected_bank_movement` | `uk(movement_type, source_id)`；`idx(cash_account_id, status, expected_date)` | `confirmed_amount`、`classified_amount` |
| `t_bank_match` | `idx(bank_statement_line_id, status)`；`idx(expected_movement_id, status)` | 带符号由方向推导，金额列存正数 |
| `t_recon_exception` | `uk(source_type, source_id, exception_type, active_slot)`；`idx(status, opened_time)`；`idx(clearing_account_id, status)`；`idx(cash_account_id, status)` | `active_slot`：开放中为 1，结案置 NULL，防止同一对象同类型重复开 |
| `t_recon_resolution` | `uk(exception_id, resolution_no)`；`idx(status, create_time)` | 三种类型、`classification_code`、带符号 `amount`、Maker / Checker / Hash |
| `t_compensation_event` | 照 billing 现行模型 | |
| `t_consumed_event`、`t_consumed_aggregate_version` | 与 billing 逐字相同 | 公共消息库消费去重的机制表（I-07），不是业务消费表 |

枚举按设计稿第 7 节，变化：`ChannelStatementStatus` 加 `RECEIVING`；新增 `BankStatementStatus`（`RECEIVING`、`IMPORTED`、`REJECTED`）、`CashAccountPendingAction`（`CLOSE`）；`ReconExceptionSourceType` 六类；切片 1 实际产生的异常类型为 `CHANNEL_LINE_UNMATCHED`、`AMBIGUOUS_REFERENCE`、`AMOUNT_MISMATCH`、`DUPLICATE_SETTLEMENT`、`UNEXPLAINED_ADJUSTMENT`、`COST_DEVIATION`、`BANK_STATEMENT_GAP`，以及 D-02 人工开的三类；其余值先登记、不产生。

**billing（Q-02）：** [db/billing/ddl.sql](./db/billing/ddl.sql) 给 `t_billing_fact_ref` 加两列并改 `ref_type` 注释；`billing.pdma.json`、Schema、测试 DDL、`billing-model.test.mjs` 同步；`FactRefType` 加 `CHANNEL_TRANSACTION_REF`、`RECON_RESOLUTION`，`BillingFactRefPayload` 加 `externalReferenceType`、`externalTransactionId`，人工登记接口不开放这两种引用。

### 2.3 内部 HTTP 接口（reconciliation，前缀 `/reconciliation/v1`）

写接口按[公共写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)：一个 Processor 一个命令，事务内只写本库，调 Vault、channel-connector 在事务前；操作人取 Gateway 传来的身份头（照人工调整）。

| # | 方法与路径 | 用例 | 权限点 | 说明 |
|---|---|---|---|---|
| 1 | `POST /clearing-accounts` | R01 | 操作 | 新建（编码、渠道、经营主体、币种、配置、到账银行账户） |
| 2 | `PUT /clearing-accounts/{id}/settings` | R01 | 操作 | 改三项配置与到账银行账户 |
| 3 | `POST /clearing-accounts/{id}/mids` · `DELETE /clearing-accounts/{id}/mids/{midId}` | R01 | 操作 | 挂 MID 前查 channel-connector：存在、`ACTIVE`、渠道与经营主体一致、能力组有该币种 |
| 4 | `POST /clearing-accounts/{id}/status` | R01 | 操作 | 启用 / 停用 |
| 5 | `GET /clearing-accounts` · `/{id}` · `GET /clearing-accounts/unassigned-mids` | R01、R17 | 读 | 未归属 MID = channel-connector `ACTIVE` MID × 能力组币种 − 已归属 |
| 6 | `POST /clearing-accounts/resolutions` | R03 | 内部（billing） | MID + 币种 → 清算账户 ID、编码、经营主体；不经 Gateway |
| 7 | `POST /cash-accounts` | R02 | 操作 | 提交新建（Vault Protect 在事务前） |
| 8 | `POST /cash-accounts/{id}/closure` | R02 | 操作 | 申请关闭 |
| 9 | `POST /cash-accounts/{id}/approval` | R02 | 审批 | 通过 / 拒绝；提交人 ≠ 复核人；通过新建时校验同主体 + 币种 + 用途只有一个 `ACTIVE` |
| 10 | `GET /cash-accounts` · `/{id}` | R02、R17 | 读 | 只返回末四位 |
| 11 | `GET /cash-accounts/current?entityType&entityId&currency&purpose` | R04 | 内部（payout、tax） | 只返回 ID 与末四位 |
| 12 | `POST /channel-statements` | R06 | 操作 | 开始导入：账单头 + 文件名 + 文件 SHA-256 + 声明行数 → `RECEIVING`；同账单号已有未作废的 `RECONCILED` 版本时拒绝，有 `MATCHING` 版本时要求先作废 |
| 13 | `POST /channel-statements/{id}/lines` | R06 | 操作 | 追加一段（≤ 100 行，按 `line_no` 幂等：同号同内容忽略，同号不同内容拒绝） |
| 14 | `POST /channel-statements/{id}/completion` | R06 | 操作 | 校验行数、行号连续、合计与账单头一致、币种一致 → `MATCHING` 并登记匹配任务；不通过 → `REJECTED` + 逐行原因 |
| 15 | `POST /channel-statements/{id}/void` | R08 | 操作 | 只限 `RECEIVING / MATCHING`；释放行匹配、回退平台流水金额、本账单开放异常 `AUTO_CLOSED` |
| 16 | `GET /channel-statements` · `/{id}` · `/{id}/lines` | R17 | 读 | 详情返回门禁四项的满足情况 |
| 17 | `GET /platform-txns` | R17 | 读 | 按清算账户、状态、外部交易号、日期 |
| 18 | `POST /bank-statements` · `/{id}/lines` · `/{id}/completion` | R11 | 操作 | 同 12–14；完成时校验期初 + Σ = 期末，接不上开 `BANK_STATEMENT_GAP`，按 `银行账户 + 银行交易号` 跳过重复流水并在结果里计数 |
| 19 | `GET /bank-statements` · `/{id}/lines` · `GET /bank-statement-lines` | R17 | 读 | 未匹配流水可按银行账户、方向筛选 |
| 20 | `GET /expected-bank-movements` | R17 | 读 | |
| 21 | `GET /bank-matches` | R13 | 读 | 按流水或预期流水 |
| 22 | `POST /bank-matches/{id}/confirmation` · `/{id}/rejection` | R13 | 操作 | 确认建议 / 拒绝建议 |
| 23 | `POST /bank-matches` | R13 | 操作 | 手工匹配：一条银行流水 + 多条 `{expectedMovementId, amount}`；校验同一银行账户、带符号合计 ≤ 流水金额、每条 ≤ 预期剩余；直接 `CONFIRMED` |
| 24 | `POST /recon-exceptions` | D-02 | 操作 | 人工开异常（三类对象） |
| 25 | `GET /recon-exceptions` · `/{id}` | R17 | 读 | 列表按账龄；详情含来源对象、可用处置与归类码、处置历史 |
| 26 | `POST /recon-exceptions/{id}/resolutions` | R15 | 操作 | 提交处置（类型、归类码、金额、重新匹配目标、说明）→ `PENDING_CHECK`，异常 `UNDER_REVIEW` |
| 27 | `POST /recon-resolutions/{id}/approval` | R16 | 审批 | 通过即应用；拒绝异常回 `OPEN` |
| 28 | `GET /summary?entityNo&currency` | F5 | 读 | 在途账单、开放异常（按账龄段）、未匹配银行流水金额 |

**处置应用规则：**
- `RE_MATCH`：交易层写账单行 `platform_txn_id`（目标须同清算账户、方向一致、金额不超剩余）；银行层追加一条 `CONFIRMED` 银行匹配，校验同 #23。
- `ACCEPT_TIMING_DIFFERENCE`：只关异常；交易层用于 `COST_DEVIATION`、`DUPLICATE_SETTLEMENT`（确认不是重复）、`PLATFORM_ITEM_UNSETTLED`。
- `CLASSIFY`：归类码限定见设计 F3 表；金额必须等于异常差异金额。账单行 → 行 `resolution_id` 赋值、行 `MATCHED`，随对平事件带出；平台流水 → `settled_amount` 补足、`SETTLED`；预期银行流水 → `classified_amount` 增加、满额 `CONFIRMED`；银行流水 → 行 `MATCHED`。后三者在同一事务登记 `ReconAdjustmentApproved`。
- 应用后对所属账单或预期流水重算状态（账单走对平检查）。

### 2.4 事件

全部由 reconciliation 发出（`reconciliation.events`），`strictOrder: false`，`consumers: []`（D-08），载荷类放 `paytech-reconciliation-api` 的 `event` 包；金额为十进制字符串，ID 为字符串。

| 事件 | 聚合 / 版本 | 何时 | 载荷要点 |
|---|---|---|---|
| `ActualChannelCostConfirmed` | `PLATFORM_TXN` / 1 | D-05 | 平台流水 ID、`billingFactId`、清算账户 ID 与编码、经营主体、币种、`txnSource`、`expectedFee`、`actualFee`、`difference`、渠道交易引用三项、账单 ID |
| `ChannelBatchReconciled` | `CHANNEL_STATEMENT` / 1 | 账单对平 | 账单 ID、清算账户 ID 与编码、经营主体、币种、渠道批次号与版本、结算日、`cashDirection`、`netAmount`、`receivableTotal`、`payableTotal`、`actualFeeTotal`、`classifiedLines[]`（`lineNo`、`classificationCode`、带符号 `amount`、`resolutionId`）、`platformTxnIds[]`、预期银行流水 ID |
| `ChannelSettlementConfirmed` | `BANK_MATCH` / 1 | 渠道预期流水的一条匹配确认 | 匹配 ID、账单 ID、清算账户、银行账户 ID、经营主体、币种、方向、`matchedAmount`、银行流水 ID、起息日 |
| `ReconAdjustmentApproved` | `RECON_RESOLUTION` / 1 | 平台流水 / 预期银行流水 / 银行流水的 `CLASSIFY` 批准 | 处置 ID、异常 ID、`sourceType`、`sourceId`、`classificationCode`、经营主体、币种、带符号 `amount`、清算账户或银行账户 ID、`makerId`、`checkerId`、`approvedContentHash`、`approvedTime` |

补偿事件目录 `paytech/reconciliation/compensation-event-catalog.yaml`：四个 `MQ_PUBLISH` 类发布任务，加本地任务 `CHANNEL_STATEMENT_MATCH`、`BANK_MATCH_SUGGEST`。

消费：`BillingFactRecorded` → `BillingFactRecordedPlatformTxnHandler`（照 account：`@DomainEventHandler(strictOrder=false)`，`messaging.consumer.service: reconciliation`，`messaging.consumers.billing.partitions: 4`）。只处理 `PAYMENT`、`REFUND` 且状态为已批准的事实，其他类型直接确认；缺必需引用或清算账户不存在 / 停用 → 抛可重试异常（消息重投，最终进 DLQ 告警），不丢弃（AC-04）。

**XXL 任务：** `compensationEventScan`（每 30 秒，分片广播）、`channelLineWaitExpiry`（每 10 分钟：`UNMATCHED` 且导入时间 + `unmatched_wait_days` 已过的账单行开 `CHANNEL_LINE_UNMATCHED`，同行已有开放异常则跳过）。

### 2.5 两种 CSV（浏览器解析，D-01）

UTF-8、逗号分隔、首行列名；金额为不带千分位的十进制，非负，方向单独一列。

**渠道账单**（第 1 行账单头，以 `#HEADER` 开头；其后明细）：

| 部分 | 必填列 | 可选 / 其他 |
|---|---|---|
| 账单头 | `clearing_account_code`、`channel_batch_ref`、`settlement_date`（YYYY-MM-DD）、`currency`、`gross_amount`、`fee_amount`、`net_amount`、`cash_direction`（CREDIT / DEBIT / NONE） | — |
| 明细 | `line_no`、`line_type`（PAYMENT / REFUND / CHARGEBACK / FEE / ADJUSTMENT）、`direction`（CREDIT / DEBIT）、`amount`、`fee_amount`、`reference_type`、`external_transaction_id`、`transaction_date` | 其他列原样进 `raw_attributes`；ADJUSTMENT 与无交易号的 FEE 行可空 `reference_type`、`external_transaction_id` |

恒等式：`gross_amount = Σ CREDIT 行 amount − Σ DEBIT 行 amount`（不含 FEE 行）；`fee_amount = Σ 明细 fee_amount + Σ FEE 行 amount`；`net_amount` 带 `cash_direction` 等于 `gross − fee`。

**银行对账单**：头 `bank_account_last4`、`statement_ref`、`period_start`、`period_end`、`currency`、`opening_balance`、`closing_balance`（期初期末可为负，带符号）；明细 `line_no`、`bank_transaction_ref`、`value_date`、`direction`、`amount`、`counterparty_name`、`reference_text`。导入时页面先选银行账户，末四位不一致则拒绝。

两份格式说明随研发写进 `paytech/reconciliation/docs/formats/`，并附示例文件（设计稿第 3 节 S1 的数据）。

### 2.6 Operations BFF 与页面

**BFF：** 新增 `ReconciliationClientConfiguration`（`operations.reconciliation-base-url`）、`integration/Reconciliation*Client` + `impl`、`ext/rest/Reconciliation*Controller`（前缀 `/finance/reconciliation/v1/...`），对 #1–5、7–10、12–28 一一转发并整形；页面聚合只在 BFF：清算账户详情补 MID 外部编码（读 channel-connector）、异常详情补来源对象摘要。BFF 无状态、不校验权限点（AC-14）。

**页面**（财务菜单「对账」分组，懒加载，照人工调整页的文件组织）：

| componentKey | 页面 | 主要操作 |
|---|---|---|
| `OPERATIONS_FINANCE_RECON_CLEARING` | 清算账户 | 列表、新建、配置、挂 / 摘 MID、启停、未归属 MID 提示 |
| `OPERATIONS_FINANCE_RECON_CASH` | 银行账户 | 列表、新建、申请关闭、复核 |
| `OPERATIONS_FINANCE_RECON_STATEMENTS` | 渠道账单 | 上传（分段进度条、被拒原因表）、列表、详情（行匹配、门禁四项）、作废 |
| `OPERATIONS_FINANCE_RECON_PLATFORM_TXNS` | 平台流水 | 查询、人工开异常 |
| `OPERATIONS_FINANCE_RECON_BANK` | 银行对账 | 上传对账单、未匹配流水、建议确认 / 拒绝、手工多选匹配（实时带符号合计）、人工开异常 |
| `OPERATIONS_FINANCE_RECON_EXCEPTIONS` | 对账异常 | 列表（账龄、筛选）、详情、提交处置（按来源对象过滤归类码）、复核 |
| `OPERATIONS_FINANCE_RECON_SUMMARY` | 在途汇总 | 三块汇总 |

按钮资源：`OPERATIONS_FINANCE_RECON_OPERATE`、`OPERATIONS_FINANCE_RECON_APPROVE`。`pageCapabilities.ts` 与 `console-resource-capabilities.json` 同步。

### 2.7 Platform 注册（块 123）

照财务先例写入 `platform-support-initial-data.sql`（唯一执行来源）并在本目录放阅读副本与激活脚本 `db/platform-support/`（阶段 4 产出）：

| 项 | 内容 |
|---|---|
| 权限 | `FINANCE_RECONCILIATION_READ`、`FINANCE_RECONCILIATION_OPERATE`、`FINANCE_RECONCILIATION_APPROVE`（另设读权限：对账与总账查看人群不同） |
| 角色授权 | 照财务先例授给财务运营 / 财务复核角色，初始 `REVOKED` |
| Console Resource | 分组菜单 1 + 页面 7 + 按钮 2，初始 `DISABLED` |
| 路由 | BFF 路由约 30 条，`HUMAN_PERMISSION`，分段导入三条路由 `body_limit_bytes = 65536`，其余按默认；初始 `DISABLED` |
| 前置 | 阶段 6 前查 mor-test `mor_platform` 确认块 123 未占用 |

### 2.8 其他改动清单

| 位置 | 改动 |
|---|---|
| `paytech/reconciliation` pom | 加 compensation-event、messaging / messaging-api、billing-api、channel-connector-api、vault-client、Redis 锁 starter、xxl-job、Nacos 发现（照 account / channel-connector） |
| 配置 | `application.yml` 与 `config/nacos/reconciliation-service.yaml`：RabbitMQ、Redis、Vault、XXL、channel-connector 地址 |
| Vault 策略 | 本机 `policy.local.json`、mor-test `policy.json` 与 `mor_vault` DML：数据类型 `CASH_ACCOUNT_NUMBER_V1`，`reconciliation-service` 的 PROTECT 规则 |
| 事件目录 | `events.yaml`：`BillingFactRecorded` 消费方加 reconciliation，新增四个事件 |
| 模型与规则 | `reconciliation.pdma.json` 脱离 Finance 生成器（`FINANCE_SERVICES` 与 `EXPECTED_SERVICE_TABLE_COUNTS` 去掉 reconciliation，02 / 05 清单不再投影到本库），新增 `reconciliation-model.test.mjs`；`generate-oe-client-schemas.mjs` 的 reconciliation 目标保留（只产出 OE 引用校验，阶段 4 核对后如无意义再去掉）；`table-ownership.yaml` 改 13 张 |
| 部署 | 本机 e2e `run-service.sh`、mor-test `compose.applications.yaml`：MQ、Redis、Vault、XXL 变量与依赖；Operations BFF 加 `RECONCILIATION_BASE_URL`；XXL 登记执行器与两个任务 |
| 文档 | `paytech/reconciliation/README.md` 重写；`FINANCE_DATABASE.md` §4.5、§6.2 加取代说明；requirements-site 对应条目 Owner 与说明（研发时核对 FIN 编号）；台账；design-discuss 状态 |

### 2.9 研发分线与验证计划

| 线 | 内容 | 依赖 |
|---|---|---|
| T0 合同 | reconciliation-api（请求 / 响应、枚举、四个事件载荷）、billing-api 两处改动、`events.yaml`、补偿事件目录 | — |
| A 底座 | PDManer 13 表 + Schema + 测试 DDL + 模型测试、生成器摘除、表归属；DataObject / Mapper / Repository；pom、配置、测试基座（channel-connector 与 Vault 的 DTO Mock 端点、MQ 测试发布 / 消费） | T0 |
| B 交易层 | 清算账户、解析、平台流水消费、账单分段导入、匹配任务、等待期满 Job、作废、对平、成本事件 | A |
| C 银行层与处置 | 银行账户与复核、当前账户查询、对账单分段导入、建议任务、确认 / 手工匹配、人工开异常、处置提交 / 复核 / 应用、三类事件、汇总查询 | A（处置的账单行分支依赖 B 的对平入口，B 先交合同） |
| D 控制台 | BFF、七个页面、Platform 块 123 注册与激活脚本、Vault 策略 | T0 |
| E 收尾 | billing 两列落库与模型测试、README / 格式说明 / FINANCE_DATABASE / 台账 / requirements-site | T0 |

**验证（阶段 5，本机 local-environment）：** reconciliation-test 以真实入口覆盖 AC-02–AC-14（HTTP、MQ 消费、补偿任务、Job Handler），场景矩阵按设计稿第 3 节 S1 例子（PAYMENT 100 费 0.45、REFUND 30、月费 2.00、银行到账 67.40 差 0.15 归 `BANK_FEE`）为主线，外加每条匹配规则与每类拒绝；billing-test、account-test 回归；operations-console-bff、web 测试；arch-rules、`scripts/pdmaner`、`npm --prefix requirements-site test`（既有失败单列）；浏览器走 AC-V1–V6。不跑全仓 reactor（共享本机库）。

**阶段 6–7（mor-test）：** 备份 `mor_reconciliation`、`mor_billing`、`mor_platform`、`mor_vault`；执行顺序：billing ALTER → reconciliation 重建 → Vault DML → Platform 注册 + 激活；替换 reconciliation、billing、operations-console-bff、operations-console-web 四个镜像；登记 XXL；用测试发布方发合成 `BillingFactRecorded` 跑 AC-16，验收后清理合成数据。

### 2.10 阶段 4 实施中的决定（2026-10-20，主会话按设计门禁定，均在已确认范围内）

| 编号 | 来源 | 决定 |
|---|---|---|
| I-01 | T0 报告 1 | 四类事件每个聚合只发一次，聚合版本固定为 1（载荷常量 `AGGREGATE_VERSION`），目录 `aggregateVersionField: none` |
| I-02 | T0 报告 2 | `ChannelBatchReconciled` 去掉 `platformTxnIds`，改为 `platformTxnCount`：2 万行账单的 ID 列表会超过 64 KiB 信封；billing 入账只用应收 / 应付 / 费用合计，需要明细时按账单 ID 查 `GET /platform-txns?statementId=` |
| I-03 | T0 报告 3 | `ReconAdjustmentApproved` 主体用 `entityType + entityId`（银行账户可能属财务主体）；`ResolutionStatus` 按 DDL 三值 |
| I-04 | T0 报告 4 | 接受：银行账户新建被拒 → `CLOSED`；净额为借方的渠道账单完成导入时整单拒绝（切片 2 再支持）；对平门禁加"到账银行账户已配置"；在途汇总账龄段 `D0_3 / D4_7 / D8_30 / D31_PLUS`；异常列表默认只查未结 |
| I-05 | T0 报告 5 | `POST /reconciliation/v1/cash-accounts` 带明文账号：reconciliation 与 BFF 对应路径的请求日志只记元数据，不记请求体 |
| I-06 | T0 报告 6、E1 报告 | billing 读取侧补齐两列（DataObject、Mapper、载荷映射、读接口响应、billing-app 转换、ledger 客户端 `toRef`）归 E2 线 |
| I-07 | A 报告 | 开启 MQ 消费时公共消息库（`IdempotentConsumeInterceptor`）要求宿主库有 `t_consumed_event`、`t_consumed_aggregate_version`，库内 15 张；用户 2026-10-20 确认，本机已补建 |
| I-08 | A 报告 | 四类事件无订阅方时未路由策略取 `FAIL`：补偿任务重试到上限留存 FAILED，billing 接入后人工补发，不丢事件；用户 2026-10-20 确认 |
| I-09 | D 报告 | CSV 头部写法定为两行 `#HEADER`（第一行列名、第二行取值），其后是明细列名行和明细；格式说明（E 线）按此写 |
| I-10 | D 报告 | 菜单可见性：「财务」父菜单要求 `FINANCE_ACCOUNTING_READ`，只有对账权限的角色还需该读权限才能看到「对账」分组；本期接受，阶段 5 浏览器验收按此配置角色 |
| I-11 | D 报告 | 渠道账单、银行对账单的两条分段追加路由限流取每人每分钟 300 次（2 万行 = 200 段），其余路由按默认 |
| I-12 | B 报告 1 | AC-04 措辞按 §2.4 修正：缺引用 / 清算账户缺失或停用 → 消息重投 5 次后进 `reconciliation.billing.dlq`，留存待补建后重放 |
| I-13 | B 报告 2 | `scheduleStatementEvaluation(statementId, resolutionId)`：匹配任务子类型带触发来源（`IMPORT`、`PLATFORM_TXN-<id>`、`RESOLUTION-<id>`），避免运行中再次唤醒被当成重复而丢失 |
| I-14 | B 报告 7 | 接受：AMBIGUOUS_REFERENCE 为同交易号不分方向命中多于 1 条；行级异常差异取该行带符号净额贡献；`platformTxnCount` 为本账单匹配到的去重笔数；新平台流水只唤醒同交易号仍有未匹配行的账单；匹配任务超时 100 秒（2 万行耗时阶段 5 实测，必要时批量查询或调大 Lease） |
| I-15 | C 报告 1 | 银行对账单含已导入（其他对账单）的银行交易号时**整单拒绝**并逐行给原因，不再"跳过并计数"（修订 §2.3 #18）：跳过会使期初 + Σ = 期末按部分行核对而误拒、重发分段会重复计数；`duplicate_line_count` 记录检出的重复行数。AC-09"重复流水不入库"仍成立 |
| I-16 | C 报告 2 | 被拒的银行对账单可用同一 `statement_ref` 重新导入：复用被拒的头记录重置为 `RECEIVING`；已 `IMPORTED` 的同号对账单仍拒绝 |
| I-17 | C 报告 3 | 接受 C 报告 §6.7 的处置类型补充：银行层异常均可"接受时间差"；`DUPLICATE_SETTLEMENT`、账单行未命中 / 金额不符可"归类" |
| I-18 | C 报告 4 | 银行流水金额须 > 0、起息日须在对账单期间内，完成导入时逐行拒绝；格式说明按 C 报告 §7 原文对齐 |
