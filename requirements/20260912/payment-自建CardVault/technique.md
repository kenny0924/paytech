# Payment Card 模块技术入口

> **后继入口与工具绑定：** Customer 入口按[Commerce 后继](../../../paytech/acquiring/docs/requirements/20260912-customer-checkout/commerce-entry-amendment.md)归 Commerce；交易级工具 Token、统一 pay、无独立会话表及 Card 受控适配职责按[Execution 修订稿](../../20260913/payment-Execution设计/instrument-token-amendment.md)评审。本文的旧 Payment Submission、Card Token 和材料传递方案不能直接用于该新目标；未被取代的加密与审计约束保留。真实 Card 仍需独立接入验证。

状态：阶段 1 模块边界已确认；用户在阶段 3 将前端两次提交修订为 Payment 承接的一次支付请求，本文同步受影响调用链。本文保留现状与影响分析；本轮阶段 3 交付[接口字段稿](./api-contract.md)和[数据/事务/实施稿](./data-design.md)。具体字段只在这两份稿件维护，未实现或发布接口。

## 1. 已确认归属与模块职责

在 `payment-orchestration-service` 内增加一个业务能力 Maven JAR，建议命名 `paytech-payment-card`。Card Vault 是这个模块内部的存储能力，卡采集处理、Token 和受控渠道用卡也归同一模块；不再分别拆出卡库服务、Token 服务或执行服务。

| 部分 | 职责 |
|---|---|
| Checkout 卡采集组件 | 一次 Pay 请求同时将卡和合同确认发送至 Payment；原始输入不经过 Commerce |
| Payment `web` | HTTP 协议入口、组合根和支付用例 Processor；统一支付入口把卡输入交给 Card 模块，通过独立内部 DTO 向 Commerce 确认；普通支付业务不持有卡原值 |
| Payment Card 模块 | 卡采集业务校验、PAN 加密存储、Token 归属/用途/生命周期、受控用卡和访问审计；不保存 CVV，不持有订单/计价/资金状态 Owner 职责 |
| Commerce | 提供内部访问校验、协议确认与原确认查询；三份接受/网络证据/确认回执同一事务提交，不反向登记 Payment，不接卡或NI参数 |
| Payment 支付编排 | 支付业务资格、幂等、OE 内路由、Attempt/Action 与结果事实；向 Card 模块提供由服务端确定的最小授权执行命令 |
| Card 模块内的渠道用卡适配器 | 在获准执行范围内解密卡数据并提交 NI；返回受限业务结果，不将 PAN 返回给普通编排。支付路由、回调与 UNKNOWN 判定仍由 Payment 支付业务拥有 |
| 现有非卡 Vault | 继续处理既有 Email/Profile 等批准字段，保持当前卡数据排除规则 |

### 模块调用与持久化

- Payment `web` 依赖 Card 模块公开的业务 Service 接口及 Domain/Application 输入输出；Card 模块不依赖 `web`，也不通过 `*-api` 共享内部领域对象。Service 使用 `interface + Impl`。
- 模块公开能力围绕采集/生成引用、验证引用用途、受控支付执行和生命周期操作设计；不提供通用 Reveal/Detokenize，不返回完整卡号，也不为了列出方法提前创建空接口。
- 卡库表及其 DataObject、Mapper、Repository 只归 Card 模块。默认仍使用 Payment 所属数据库；其他 Payment 代码通过模块业务接口操作，不直接读表。现有 `t_payment_instrument` 的渠道工具引用/卡片描述职责与 PAN 卡库区分，后续数据设计再明确引用关系。
- 服务级幂等和 `compensation_event` 保持一套。事件、幂等快照和重试载荷不得保存 PAN/CVV；恢复依赖稳定执行身份、Token 和渠道查询，不能靠保存 CVV 重放。
- 下游查询和必要 KMS 操作在本地事务外完成；本地事务只操作数据库；实际渠道提交在本地提交后执行，结果另开短事务保存。Card 模块公开的外部执行能力不能在事务内调用。

以上沿用[业务能力模块规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md#34-复杂核心服务的业务能力模块)。Maven 模块不提供进程、内存或数据库账号的隔离：同进程部署时，Payment `web`、Card 模块及所在运行环境均需纳入卡数据安全评估。独立进程/数据库不是本次模块决定的默认结果。

Card Token、当前授权输入引用和长期“已保存卡”标识是否需要区分，要由真实用途决定。当前不预建通用 Token 层、公共 Detokenize API 或跨 Channel 万能 JSON。

## 2. 现状差异与能力缺口

以下为本次读取到的组件证据，实际实现、验证与环境状态仍由 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 维护，不能把数据库模型或前序设计当成可调用服务。

| 能力 | 当前证据 | 本需求需要补齐 |
|---|---|---|
| Payment 工程 | 仓库当前只有[数据库设计材料](../../../paytech/payment/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md)，未发现 Java/POM | Payment 应用基础、真实 Card 模块及消费者；模块边界测试随功能实现，不只增加空 JAR |
| 支付执行 | 目标模型有 Intent、Attempt、Action、Channel Account/MID 和 Customer Action | 对应业务接口、状态机、合法接受门禁、路由、NI Adapter、原执行恢复；新增 Card 模块本身不能代替完整支付执行 |
| 自建卡库 | [当前 Schema](../../../paytech/payment/docs/database/sql/payment-schema.sql) 的 `t_payment_instrument` 必须有 `channel_instrument_ref`，并禁止 PAN/有效期 | Card 模块专属加密存储与平台引用；不能将平台 Token 填进 Channel 引用冒充已有模型支持 |
| Commerce | [Commerce README](../../../paytech/acquiring/README.md) 表明已有 Client 创建/查询/取消/退款，最终准备与 Payment 真正接入仍是缺口 | Customer 查询/资料/准备三个公开操作、内部法律确认及 Payment 状态查询；不把卡库塞进 Commerce |
| Checkout Web | 仓库有已认可的页面设计，未发现前端运行工程 | 页面实现、卡输入组件、统一 Pay 请求、3DS 与结果刷新；页面设计通过不等于 SDK 已接通 |
| 通用中间件 | [Foundation](../../../paytech/foundation/README.md) 已有 Request ID/MDC、统一错误与按路径仅记录元数据的日志配置 | 复用 `web.logging.metadata-only-path-prefixes`，覆盖卡入口和临时材料响应；无需为 Card 复制 Filter 或 Advice。出站 NI 日志、解析失败及追踪还需单独核验 |
| 非卡个人资料 | 原 Consumer 方案依赖现有非卡 Vault 的批准用途 | 继续完成原 Email/Profile 用途接入；Card 模块不替代 Consumer Vault，也不扩大现有 Vault 数据类型 |
| NI | 官方有直接提交卡数据的单阶段、两阶段 API，以及 EMV 3DS 流程 | 确认实际账户适用能力、请求/结果白名单、原订单查询和超时恢复；平台 Card Token 由本地解引用，不能直接作为 NI Session |

## 3. 已确认的点击 Pay 调用链

### 3.1 一次公开请求

用户已确认将前轮两个浏览器请求合并。CVV 仅在当前请求内使用并不要求把前端拆成两步；由 Payment 承接统一入口即可保留卡数据边界和 Commerce 接受事实 Owner。

1. 页面已通过 Commerce preparation 取得最终金额、卖方、三份冻结文档及已准备 Intent。Card 组件本地检查后，一次 `POST B/payments` 提交 `legalConfirmation + card`。
2. Payment 通过 Commerce 只读校验取得可信 Checkout/Order/Intent 绑定，短事务认领原 Pay 命令及 Submission。卡与 CVV 仍在当前请求内存，不先返回待收卡引用。
3. Payment 提交后调用 Commerce 内部确认，只传当前合同确认、提交坐标、Token 与可信网络证据。Commerce 短事务保存或复用三份接受、网络证据及稳定回执，提交后直接返回。
4. Payment 重新检查 COM-I10/执行资格，在事务外准备卡加密材料，短事务绑定卡和唯一 Attempt；其后按依赖顺序在事务外路由、检查 Risk、提交 NI，结果另开短事务写回。
5. 同一次公开请求返回原支付结果或 3DS 动作。前端通过 Payment GET 读取原动作，通过 Commerce GET 刷新订单最终状态。

所有外部服务/KMS/NI 调用都在事务外。一次 HTTP 请求不构成跨库原子事务；协议已确认、支付失败时，接受事实保留，不能借回滚或新键再次扣款。

### 3.2 前序设计的准确取代范围

| 部分 | 当前方向 |
|---|---|
| 原四个基础路径 | GET、consumer、preparation 仍由 Commerce 提供；payments 路径不变，Owner 改 Payment |
| 独立 `/card` | 删除前稿公开操作；采集和执行共用一个 Pay 请求、一个幂等命令 |
| Payment registrations | 删除前稿内部登记接口；Submission 由 Payment 自己首次受理时创建 |
| Commerce 接受 | 内部确认命令原子保存三份接受/网络证据/回执；不调用 Payment 登记或执行 |
| 查询和 3DS | 保留 Payment 原提交查询及最小3DS指令；Commerce只返回支付概要 |
| Card 模块与 Token | 模块保留，Token 只在 Payment 内部；前端无需先注册卡或取得 Card Token |

公开提交中的协议部分只接收合同标识与本次 confirmed=true，与 card 对象一起传入；三份文档及接受权威由 Commerce 判断。Checkout Token 授权、固定 OE/金额、幂等与唯一活动执行都在服务端验证，不允许前端选择或声明支付成功。

## 4. 数据与恢复影响

详细字段、Hash、事务和并发仅由[数据稿](./data-design.md)与[接口稿](./api-contract.md)维护。本次受影响部分：

- Payment Submission 的来源改为本地 Pay 命令，保留确认前无 Attempt 的受理与恢复身份。前稿卡表独立 capture_command_id 及索引移除，不再有第二个卡命令。
- Commerce 以 Payment submissionId 为内部确认幂等键；确认回执丢失时提供只读查询。后台不保存 Token/网络原值去重演同意，也不反向调用 Payment。
- 相同 Pay 键绑定合同和规范化卡输入，CVV 从持久化与 Hash 中完全排除。新键不能绕过原活动提交，异卡/异合同不能覆盖原命令。
- 瞬时输入丢失且证明资金尚未发送时，保留原 Submission 并等待用户按原键重发同一个 payments 请求；正常支付没有先 AWAITING_CARD 后第二次提交的步骤。
- 刷新先查原提交；Payment 经当前 Checkout 授权后可提供原 retryIdempotencyKey，仅恢复原身份，不是新的 bearer 权限。
- 可能发送、UNKNOWN、3DS、授权或成功只沿原动作查询/恢复；输入到期不能掩盖在途资金或解除占用。

## 5. NI Direct 接口核对结果

2026-09-12 只读取官方文档，没有使用账户或调用交易 API：

- [Two stage payments](https://docs.ngenius-payments.com/reference/two-stage-payments-orders) 支持先创建 NI Order，再向其指定 Payment 提交卡数据。建议作为恢复设计的优先候选：先取得并落库稳定 Channel Order/Payment 引用，再发送资金请求。NI Order 创建响应丢失的恢复仍需核实，不能由两阶段流程推断已有幂等保证；文档后半部有旧 3DS 1 示例，不沿用。
- [One stage payments](https://docs.ngenius-payments.com/reference/one-stage-payments-1) 也支持创建订单与卡支付一次请求。它减少渠道调用，但首次响应丢失时稳定查询坐标更需要证据；目前未验证商户自定义引用的查询/去重保证，不先认定该方式能安全自动重试。
- [Alternative EMV 3DS Workflow](https://docs.ngenius-payments.com/reference/alternative-emv-3ds-workflow-websdk-2) 描述处理 `AWAIT_3DS` 的 SDK 能力。该能力与前序“SDK 托管卡采集”分开评估；文档示例传整份支付响应，项目需核实最小指令字段，不能直接向 Commerce 或浏览器暴露未经筛选的 Channel 响应。

以上是协议可行性证据。实际 NI 账户权限、动作类型、环境、幂等/恢复和 3DS 最小字段尚未通过沙箱验证；首个 Channel 已选 NI，无需再次选渠道。本次不在浏览器保存 NI 私有凭据，不把平台 Token 当成 NI `session_id`。

## 6. 逐系统改动范围与下一阶段成果

| 系统/工程 | 与本需求直接相关的改动 |
|---|---|
| Payment | 主要实现范围：应用基础、Card 模块、统一支付入口/受理身份、Commerce 确认调用、NI、3DS、结果恢复及模型/人工 SQL |
| Commerce | 保留 Customer 查询/资料/准备；将法律确认作为内部命令，提供访问/确认查询，读取 Payment 当前概要，不增加原始卡字段 |
| Checkout Web | 在既有页面上实现卡输入组件和一次支付提交；根据原提交查询继续3DS或恢复输入 |
| Foundation | 现有 HTTP/负载均衡/MDC/错误/日志/锁/幂等能力先复用；仅发现真实缺口才另列公共能力变更，不默认新增中间件 |
| Gateway / Platform 入口控制 | 将 Payment 统一 payments 入口及提交查询路径列入后续路由、Guard、限流与禁内容日志设计。卡 HTTP 入口实际经过的 Nginx/Gateway 都要纳入安全范围；本阶段不写注册 DML或修改配置 |
| CCT / Finance / Platform 文件能力 / 非卡 Vault | 继续解决原 Customer 准备链的 Owner 能力缺口；存卡不改变 OE Affiliation 优先级、最终订单金额或这些系统的数据职责 |
| Risk | 本期保持不改；真实检查缺失或不可用不能默认 ALLOW，也不能因此声称真实支付闭环已完成 |

阶段 3 应交付：

1. Payment 统一 payments/查询字段、Commerce 确认/访问/确认查询契约及必要 3DS 合同；明确身份、期限、状态与恢复，保留原订单/法律确认决定。
2. Card Token、执行引用、一次提交与换卡的生命周期及幂等矩阵；包含 CVV 输入丢失、未知支付和取消竞争。
3. Card 模块表归属与最小字段/索引、对现有 Payment 表的增量，以及人工 DDL/DML 清单；不把所有卡事实塞到 `t_payment_instrument`。
4. 密钥、入口/脚本、运行进程、日志/追踪、备份/销毁及受限审计设计；采用项目既定技术栈，按用途评估密钥能力。
5. 分片研发与验证计划：真实入口、Processor、独立 Handler、模块边界、本地基础设施与测试侧 NI Simulator；测试代码不进入业务模块，真实 NI 沙箱证据单列。

本轮已完成阶段3字段与数据设计稿，等待评审后按明确切片研发；未生成模块代码、PDManer或执行SQL，没有数据库应用、路由注册、部署、提交或推送。NI 3DS裁剪实测和真实卡保存政策仍为明确门槛，不能标为已完成。

## 7. 前序证据与保留约束

- [原 Customer 公共合同](../commerce-CustomerCheckout接口设计/customer-api-contract.md)：原四个基础路径保留，payments 由 Payment 接收；Commerce 继续拥有订单/Consumer/legal，当前支付字段以本需求为准。
- [原 NI Hosted Session 研究](../commerce-CustomerCheckout接口设计/ni-provider-contract.md)：保留官方资料取证，但 Hosted Session 不再是当前自建卡库的实施方案。
- [现有 Vault 架构](../../../agent-docs/VAULT_SERVICE_ARCHITECTURE.md)：当前非卡服务的排除规则保留，不能直接复用它存 PAN。
- [开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)：事务公理、Processor/Handler 边界及测试代码隔离保持。

Payment 内单独 Card 模块的归属已由用户确认；接口、数据、安全控制及部署细节仍需专项设计。正式上线前应与适用的合规接受方/评估方确定验证要求；不从模块拆分或未来计划推断当前 PCI 状态。
