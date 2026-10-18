# 支付渠道术语统一：跨系统技术方案

用户已确认逐项映射并要求本地实施。本轮核对六个核心系统、Gateway、Operations BFF/Web，共九个应用仓库。候选改名涉及现有 Schema 中 23 张表名、79 处列名、51 个索引名；新增/删除表、字段、索引及新增 HTTP 操作均为 0。三类 Gateway 枚举、支付事件名和现有目录值另列，DML 影响行数需按环境盘点；本轮 SQL 执行及页面新增为 0。

上述数字是本次批准的命名差异；下文旧名列与源摘要保留评审基线。实施与验证证据只在当前台账记录，不代表物理数据库已迁移。完整现状仍以[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)为准。

## 1. 术语与改名规则

采用 Payment Channel/支付渠道作为支付领域的统一称呼：

| 现有自有名称 | 目标名称 | 含义保持 |
|---|---|---|
| Payment Provider | Payment Channel | NI、Geidea 等支付提供方身份 |
| Provider Account（Payment） | Channel Account（Payment） | 调用渠道 API 的技术账户 |
| Provider Account（Finance） | Channel Account（Finance） | Finance 的本地渠道结算核算身份，仍与 Payment 技术账户不同 |
| Provider MID | Channel MID | 归属固定 OE 的渠道 MID |
| providerId / provider_id | channelId / channel_id | 原身份值保留，引用字段重命名 |
| Provider Callback | Channel Callback | 仍由 Payment 验签、归一化和处理 |
| Provider Transaction Ref | Channel Transaction Ref | 原支付/退款/争议的渠道交易引用 |
| Provider Cost / Settlement | Channel Cost / Settlement | Finance 渠道成本及清算事实 |

对确认属于支付渠道的自有标识使用相应大小写转换：provider → channel、Provider → Channel、PROVIDER → CHANNEL。转换以文件/实体/字段用途清单为边界，不是全仓库 replace 指令。

Payment Method 继续表示支付方式；Platform 的 OPERATIONS/CLIENT/PARTNER 访问 Channel 继续表示访问渠道。Payment Channel 不是新增主体类型，不将 Channel Account 与 MID 合并，也不以改名改变 ID、归属或历史引用。

## 2. 数据模型与 Schema 映射

[rename-map.json](./rename-map.json)逐项保存旧表/列/索引与目标名称、源 Schema/模型路径和内容摘要。它是评审输入，不是 SQL 执行脚本，不生成迁移指令。基线变化时先重新核对，不能覆盖并行任务的模型改动。

| 系统 | 改表名 | 改列名的位置 | 改索引名 | 说明 |
|---|---:|---:|---:|---|
| Payment | 11 | 52（分布于 18 表） | 19 | 主档、技术账户、MID、回调、争议、通信/交易引用及选择/执行中的渠道字段 |
| Finance | 12 | 19（分布于 13 表） | 31 | 渠道成本、结算账户、清算项、批次与对账；还包括其他表中对这些事实的引用 |
| Risk | 0 | 8（分布于 4 张争议表） | 1 | 渠道 Notice/Action、来源版本、期限、费用承担与追偿；尽调/筛查 Provider 排除 |

Payment 表名全部映射如下：

| 当前 | 目标 |
|---|---|
| t_payment_provider | t_payment_channel |
| t_provider_account | t_channel_account |
| t_provider_mid | t_channel_mid |
| t_provider_transaction_ref | t_channel_transaction_ref |
| t_provider_interaction | t_channel_interaction |
| t_provider_callback | t_channel_callback |
| t_provider_callback_item | t_channel_callback_item |
| t_provider_event_exception | t_channel_event_exception |
| t_provider_dispute_notice | t_channel_dispute_notice |
| t_provider_dispute_event | t_channel_dispute_event |
| t_provider_dispute_action | t_channel_dispute_action |

Finance 的 `t_provider_cost_plan / cost_plan_ver / cost_scope / cost_component / cost_confirm / cost_alloc` 六表，以及 `t_provider_account / open_item / debit_item / settle_batch / settle_item / transaction_match` 六表，分别按相同后缀改为 `t_channel_*`；准确全名见映射文件。

两种物理列名直接替换后仍超过项目默认 32 字符，因此明确使用 `original_provider_transaction_ref_id → original_channel_txn_ref_id`（三处）与 `target_provider_transaction_ref_id → target_channel_txn_ref_id`（一处）。`txn` 仅缩短物理列名，引用含义及 ID 不变；Java/JSON 名称可继续使用完整 Transaction，Mapper 按精确列名映射。

Risk 限定 `t_dispute_case`、`t_dispute_submission_request`、`t_dispute_outcome`、`t_dispute_liability_decision` 中的支付渠道列。`t_dd_check.provider_code` 和 `t_screening_run.provider_code` 是数据供应商，保持。

CCT、Commerce 的当前模型未发现含 provider 的表/列名，仍需修改支付渠道相关注释、文档和引用说明。Platform 的身份、通知、存储 Provider 表/列保持；Gateway 配置表中的枚举说明和相应当前值按下一节迁移。

Card 待评审增量中的 `provider_order_ref`、`provider_payment_ref` 与 `idx_pay_action_provider_order` 分别改为 `channel_order_ref`、`channel_payment_ref`、`idx_pay_action_channel_order`。它们不计入上面的现有 Schema 差异；原 Card 字段/恢复方案仍按[专项](../../20260912/payment-自建CardVault/data-design.md)评审。

## 3. 事件、枚举与目录值

| Owner/消费者 | 当前 | 目标及同步点 |
|---|---|---|
| Payment → Risk | ProviderDisputeOutcomeReceived | ChannelDisputeOutcomeReceived；发布/订阅目录、消息路由、处理入口、补偿类型及测试按实际使用点同步 |
| Payment → Finance/Risk | provider_id、provider_account_id、provider_transaction_ref_id、provider_dispute_notice_id、provider_dispute_action_id 等 | 对应 channel_*；每个接收方同步自己的契约、投影、模型与去重入口，不重生成值 |
| Payment 内部类型 | PROVIDER_DISPUTE_NOTICE、PROVIDER_DISPUTE_ACTION | CHANNEL_DISPUTE_NOTICE、CHANNEL_DISPUTE_ACTION |
| Risk 争议来源/触发 | PROVIDER_ACTION、PROVIDER_OUTCOME | CHANNEL_ACTION、CHANNEL_OUTCOME；来源与 trigger_id 的绑定规则不变 |
| Risk/Payment 的支付渠道责任方值 | PROVIDER | CHANNEL；只重命名该参与方标签，不改变责任决定 |
| Finance | PROVIDER_ACCOUNT、PROVIDER_COST、PROVIDER_COST_INCREASE/DECREASE、PROVIDER_SETTLEMENT、PROVIDER_CREDIT/DEBIT、PROVIDER_SETTLEMENT_MISMATCH 等 | 对应 CHANNEL_*，分别核对 Owner 类型、经济组件、资金用途、对账及关账目录 |
| Finance 科目 | PROVIDER_UNSETTLED_RECEIVABLE/PAYABLE、PROVIDER_SETTLEMENT_RECEIVABLE/PAYABLE、PROVIDER_FEE_ACCRUAL 及其他经核实的渠道科目代码 | 对应 CHANNEL_*；保持科目/账户/Journal ID、余额桶、借贷方向及历史金额，不重建余额或重做分录 |
| Platform → Gateway、Operations BFF/Web | PROVIDER_CALLBACK | CHANNEL_CALLBACK；同时覆盖 Route Channel、Security Profile、Authorization Mode 三个代码定义及全部消费者 |

Gateway 已有真实 Java 和前端使用点，必须整体检查：

- [Platform GatewayRouteChannel](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/enums/GatewayRouteChannel.java)、[GatewaySecurityProfileCode](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/enums/GatewaySecurityProfileCode.java)、[GatewayAuthorizationMode](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/enums/GatewayAuthorizationMode.java)。
- Platform GatewayConfigurationCompiler 的三元组校验、GatewayRoutePolicyValidator、Gateway Metrics 的固定值集合、Snapshot 编译与测试。
- Operations BFF GatewayConfigurationModels 的校验表达式、PlatformGatewayConfigurationClientImpl 的允许值，以及 [GatewayConfiguration 页面](../../../operations-console-web/src/pages/GatewayConfiguration.tsx)的选项。
- Platform Gateway 模型说明、当前初始化 DML 与数据库实际值。六类 Route Channel 的数量、认证与授权职责保持；不将改名变成第七种业务通道。

API Method/Path 没有因本提案新增或自动改路由。外部渠道已经配置的回调 URL 和 HTTP/SDK 固定字段需按外部合同保持；必要的本地展示名/target code 修改须以实际数据库记录确认，不能依据测试 fixture 猜测环境值。

## 4. 逐系统与工件影响

| 范围 | 需同步评审的材料 |
|---|---|
| Payment | 逻辑规范/现实场景、模块 Owner 清单、Card 草稿、生成器与字段元数据、PDManer、完整 Schema、模型/场景/跨系统测试；后续业务类与契约采用新命名 |
| Finance | 渠道成本/清算/账务字典与场景、模块映射、生成器/Oracle/模型/Schema及相应测试；技术账户与结算账户仍分属各自 Owner |
| Risk | 争议规范、来源/触发校验器、争议生成清单/模型/Schema/独立测试；其他任务正在修改的变量/决策流原样保留 |
| CCT / Commerce | 支付渠道术语、契约说明、成功事实引用和 Provider payload 禁止项的用词；CCT/Commerce 自身业务事实及 Payment Method 不变 |
| Platform / Gateway / Operations | 三类回调枚举、代码允许值、Snapshot、指标、BFF字段校验与页面选项、对应模型注释/初始化资料及测试 |
| 工作区公共文档 | 新命名决定与现行 Owner 薄投影，AGENTS/导航/PROJECT_CONTEXT/APPLICATION_ARCHITECTURE，以及需求目录和全局时序中的支付渠道术语；不另造一套业务规则 |
| 历史材料 | 已执行 SQL、旧设计/验证日志、旧消息/快照保留；通过取代说明或迁移入口解释旧名，不批量改写历史 |

扫描已确认的保留示例：`org.junit.jupiter.params.provider`、Spring ObjectProvider/AuthenticationProvider、MyBatis SQL Provider、`ReactiveJwksProvider`、Foundation `RetryAfterProvider`、ReactFlowProvider/ConfigProvider/RouterProvider。Platform OAuth、Notification、Storage Provider 与 Risk DD/Screening Provider 均在排除范围；外部原始协议由适配器转换，不全局改包名或第三方库。

## 5. 实施与兼容边界

建议把下一步实施范围限定为当前文档、代码、模型和可审阅 SQL 工件的一致更名；物理数据库和环境发布单独准备和确认。

1. 先冻结本映射和支付语义白名单，登记窄范围命名决定，同步主题 Owner。重新检查并行修改，按实体和字段补丁合并。
2. 按映射更改生成器/元数据/独立 Oracle、PDManer、完整 Schema及命名消费者。Payment 生成器以表名/列名派生 UUID，不能直接替换后导致模型内部 ID 全部重建；需用经审查的旧新身份映射保持实体、字段、索引及图节点/关系身份。
3. 同步代码、Gateway 三类枚举、BFF/Web 和测试。Payment/Finance/Risk 尚无实际业务消费者的契约不为改名预建空实现；已经存在的消费者必须同时适配。
4. 未发布的合同可在已批准范围内直接统一新名；已有消息、Snapshot、部署制品或外部消费者的合同先核实使用情况。确需过渡时在边界显式兼容旧/新名，检测双字段矛盾，保持同一事件/命令幂等身份；不能以双发旧新事件制造两次处理。没有实际兼容需求时不预建双版本框架。
5. 物理数据库迁移先盘点各环境的旧名、枚举/科目代码、消息积压和历史快照，生成独立人工增量 SQL/数据迁移计划并明确回退。完整新建库 Schema 不能直接覆盖现有库，不自动 UPDATE 不可变 Journal/历史快照或重算 Hash/签名。
6. Gateway/Platform 在已经部署的环境不能只换一端：如有旧回调枚举数据，先准备能读取过渡数据的消费者，再切换写入/当前配置并发布 Snapshot，最后按证据退出旧值。具体 DML、制品、顺序和是否需要兼容由实际环境清单确定，本评审稿不执行它们。

历史事件名、原始 JSON 属性和日志中保留 Provider 可以是有意的兼容证据；“统一完成”指现行自有支付模型与新写合同完成迁移，不以全仓库搜索零命中为验收。

## 6. 验证计划与当前结论

本轮只读核对映射源、三库字段成员及命名冲突，未修改应用/模型/SQL。后续实际更名至少执行：

- 逐项比对表/列/索引映射、模型内部 ID、关系、字段结构/注释与 Schema；非命名业务结构和资金规则不得意外变化。
- Payment、Finance、Risk、Platform 的受影响模型/Schema/争议合同测试，以及 OE/Client 跨系统模型测试；独立 Oracle 与生成器分别更新并审阅，不仅刷新 Hash 使测试变绿。
- Platform/Gateway/BFF 的回调三元组与未知值拒绝、Snapshot兼容、前端选项/提交、Finance/Risk事件的去重/回放验证。
- 支付词汇扫描按白名单验收，同时验证框架、OAuth/通知/筛查 Provider 保留。
- 按实际代码改动运行相应 Java/前端测试及需求站测试；不把仅改文档的检查当作运行迁移证据。

下一确认点：评审此全系统命名映射和上述实施范围后，进入文档/模型/代码更名；数据库执行、部署、提交和外部渠道配置不随该确认自动授权。
