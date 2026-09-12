# Customer Checkout 服务端实现方案

> 2026-09-12 当前Card实施路由：[Payment接口字段稿](../payment-自建CardVault/api-contract.md)与[数据/事务稿](../payment-自建CardVault/data-design.md)。用户已确认 Payment 的同一个 payments 请求接收卡与协议确认，调用 Commerce 内部确认后继续执行；公开提交 Owner、Commerce SubmitPaymentProcessor/登记回调、NI setup/Hosted Session 的前序段落均不再是当前方案，其余 Consumer/准备分层证据保留。

> 受影响范围：用户已改为[自建 Card Vault](../payment-自建CardVault/requirement.md)。以下 Commerce 分层、事务与订单/法律职责保留；NI 托管初始化、setup 绑定载体、卡输入及支付执行部署切片需重新分析，不能继续按本文旧 NI 首片实施。Commerce 不持有 NI 卡规则，也不接收原始卡数据。

本方案直接规划 Commerce、Payment、Checkout Web 三个仓库的职责，新增四个 Customer 操作；保留十个 Client 操作，复用既有 17 张画板。Commerce 建议调整一张旧表的一个字段可空性，不新增接受表；Payment 采集绑定的物理结构尚待专项定稿，不能将总新增表数写为零。本轮不新增索引、生成/执行 DDL/DML、注册路由或改图。

用户已确认[公共字段](./customer-api-contract.md)，并选定 NI。真实渠道的参数与核验点见 [NI 补充](./ni-provider-contract.md)。本文件交付可审阅的职责、事务、数据对应和研发顺序；已确认公共字段不重新评审，新增 NI 绑定/Owner 数据方案也不冒充已获实施批准。

## 1. 当前代码基线

| 证据 | 实现含义 |
|---|---|
| [Commerce README](../../../commerce-service/README.md) | 三个 Maven 模块，十个 Client 操作；基础 Checkout 只接 CCT 预检，最终准备和 Customer HTTP 未接入 |
| [AbstractGatewayProcessor](../../../commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/AbstractGatewayProcessor.java) | 复用既有基础校验/处理模板，不再造 Customer 基类 |
| [CreateRefundProcessor](../../../commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/service/processor/refund/CreateRefundProcessor.java) | 已有分阶段编排、TransactionExecutor、Handler.buildEvent 与事务内登记模式 |
| [RefundSubmissionHandler](../../../commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/ext/job/RefundSubmissionHandler.java) | Handler 自身是入口，两个短事务之间调用外部端口；不另设 Handler 专属 Processor |
| [CctPrecheckConfiguration](../../../commerce-service/paytech-commerce-web/src/main/java/com/paytech/commerce/configuration/CctPrecheckConfiguration.java) | @ImportHttpServices、LoadBalancer、Foundation MDC/Problem 处理；最终 CCT 解析沿这一公共调用方式接入 |
| [完整目标 Schema](../../../commerce-service/docs/database/sql/commerce-schema.sql) | Consumer/Legal/Acceptance 已有目标表；不等于存量环境已完成准备迁移 |
| [Commerce pom.xml](../../../commerce-service/pom.xml) | 当前 Enforcer 禁止 Vault Client/Redis 等首片未使用依赖；新消费者按需评审解除对应限制，不能直接复制其他服务整包依赖 |

实际运行、数据库和部署状态始终以 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。本方案没有运行 Java 或数据库环境。

## 2. Commerce 代码落点

建议增加轻量 paytech-commerce-customer-api 模块作为真实 Customer HTTP 契约边界，与已有 Client 契约分开；它只含 @HttpExchange、Models 和 Code 定义。业务仍在唯一 paytech-commerce-web 内，测试仍在 paytech-commerce-test。新增的是契约 JAR，不是服务或 BFF；Foundation、SDK、数据库和 Vault 依赖不能被 API JAR 传递给消费者。

| 层 | 计划类型/位置 | 职责 |
|---|---|---|
| Customer API | com.paytech.commerce.customer.api.v1.checkout.CheckoutApi / CheckoutModels | 四个方法的唯一 Mapping 与 DTO；代码类型无 V1 后缀 |
| HTTP 入口 | ext.rest.external.customerapi.v1.checkout.CustomerCheckoutController | 绑定/协议转换/HTTP 状态与 Header，委托 Processor；不访问数据库或 NI SDK |
| 应用编排 | service.processor.customer.checkout.GetCheckoutProcessor | 授权、本地读取、事务外 Payment/Vault/文件查询、最终状态重验与安全视图 |
| 应用编排 | 同包 SaveConsumerProcessor | 字段/业务验证、Consumer 上下文、事务外保护、短事务保存与回执 |
| 应用编排 | 同包 PrepareCheckoutProcessor | 原准备身份与阶段推进，外部调用次序、短事务边界、Card 绑定能力读取 |
| 应用编排 | 同包 SubmitPaymentProcessor | 合同确认和提交互斥、三份接受与恢复身份、提交后 Payment 调用 |
| Domain Service | 按 Checkout Access、Consumer、Preparation、Acceptance 实际业务能力拆分，interface + Impl | 归属/状态与不变量、锁内复验、结果落库；不在一个 create 方法承接完整 HTTP 编排 |
| Repository / Mapper | 按上述实际持久化能力增加 | 只操作本系统数据库，不暴露 DataObject 到 API 或 NI Adapter |
| 出站集成 | integration 下的 CCT/Vault/Finance/Platform/Payment 适配器 | 使用 Owner API JAR 和公共 HTTP 能力，验证坐标/状态，转换明确的依赖错误 |
| 补偿入口 | ext.job 下按准备恢复、支付结果恢复的实际职责增加 Handler | 直接恢复业务阶段，使用纯 buildEvent 工厂；不增加 SubmissionProcessor 等 Handler 专属中转 |

四个 Processor 都继承 AbstractGatewayProcessor。每个主方法按基础字段验证 → 请求业务验证 → 上下文构建 → 本地事务 → 事务外下游操作 → 响应组织；纯查询没有写事务则省略相应步骤，不为六阶段强制增加六个类。所有 if 都保留花括号。

不得在 Customer Controller 信任浏览器传来的 Client/Order/OE/MID/网络代理头。通过有效 Token 解析同一 Order/Session；可信 IP 来自已批准入口链，普通业务 DTO 不带 IP 或 Request ID 控制字段。Gateway 配置不在这一代码切片里补写。

## 3. 事务与并发安排

### 查询

读取本地 Order/Session/Context/Contract/Acceptance 的一致快照 → 退出事务 → 按需要 Reveal 个人原值、查询 Payment 当前事实和冻结文件链接 → 重新核对 Order/Session 的最小状态与内部版本 → 构建响应。支付查询不能触发新的 NI 资金请求或重建 3DS；不把过期/UNKNOWN 视作可再次支付。

### 保存消费者

校验 Token 与字段、按原键识别重放 → 事务外 Vault Protect → 短事务锁定原 Order，检查 preparation 尚未占有、重验内部版本、写 Consumer Token/派生字段及幂等结果 → 返回无个人原值回执。多个标签页以最后一次成功事务为准；准备与保存使用同一实际 Order 资源串行化。

Consumer 幂等必须验证规范输入相等，但不能保存明文请求或可枚举 Email 的裸 Hash；Vault 引用稳定性和 Profile 规范化要与真实 V1 用途合同一致。若每次 Protect 生成新引用，不得直接比较随机引用判异参。明确此机制后才能验收该用例的跨进程重试，不以单进程缓存代替。

### 支付准备

| 顺序 | 本地事务中的事实 | 事务外操作 |
|---|---|---|
| 1 | 认领原准备、固定 Consumer 输入和商业时点 | CCT 最终候选解析 |
| 2 | 落 selection，冻结 entityNo 与选择证据 | 原订单级 Risk、Finance Tax |
| 3 | 核验并写 Context/Legal/最终金额及阶段结果 | 实际合同呈现/冻结文件能力与 Payment Intent 创建，按前置依赖逐段执行 |
| 4 | 写回文件/Intent 稳定引用，达成 READY 的全部条件 | Payment 提供 NI 采集绑定/公开配置 |

该表是编排视图，阶段的严格次序和成功条件继续由[既有准备合同](../../20260911/client-contract-OE与Client模型梳理/stage3-contracts.md)拥有：不得在实际文件未成立时标记法律就绪。每次外部副作用前都先有可恢复的本地原命令身份；外部调用返回后另开短事务落结果。NI 采集配置失效不回退已经冻结的 OE/合同/金额。

### 点击支付

事务外校验同一冻结合同/现有支付和 NI 采集绑定 → 短事务锁定 Order，原子记录三条接受、LEGAL_ACCEPTANCE 证据及提交身份 → 提交后调用 Payment → 独立短事务写入稳定受理/结果。

Payment 自己锁定同一 Intent，创建/复用唯一 Attempt，执行 COM-I10 及规定的 Risk/首次提交前校验；所有远程查询也在 Payment 数据库事务外。最后在实际资金调用之前复验 Action/Intent 和本地版本，避免预检与提交之间并发变化。不能在准备阶段提前建立缺三份接受的 Attempt。

同步首发与补偿恢复共用同一业务命令/执行互斥。任务仅包含稳定 Order/命令/Action 定位符，不携带 card.token。一次性输入在何时进入可可靠恢复状态，以 NI 补充中的证据门槛为准；补偿 Handler 可以查已知引用的状态，不能从空输入重新生成卡 Token。

### 锁与外部调用

同步协调按既有 Foundation @ResourceLock 能力评审接入，以解析后的 Order/Intent 为真实资源，配合必要数据库锁、唯一键和 CAS；不锁整个 Client/Tenant。Redis/Lease 不能替代数据库最终保护。跨网络期间不得持有数据库事务，即使持有协调锁也必须遵守这一公理。

## 4. 数据对应与本轮建议

| 业务事实 | 现有目标载体 | 本期处理 |
|---|---|---|
| Order 及唯一准备 | t_order、t_command_idempotency | 复用原 preparation_command_id 和已批准准备恢复阶段；无前端版本 |
| Consumer | t_consumer_context | 建议 consumer_reference 改为 NULL；当前无输入来源，不生成假值。其余字段沿批准用途 |
| 最终上下文/金额 | t_order_context、t_order_item、t_order_legal | 按固定 OE/原商业时点补齐，不复制 NI 支付配置到 Commerce Context |
| 实际消费者合同 | t_consumer_contract | 使用真实 rendered_file_id/hash，不能拿当前模板或空文件代替 |
| 三份确认 | t_consumer_acceptance、t_checkout_network_evidence | 同合同三类型唯一、同 Session 证据、首次真实确认时间；无需新表/汇总字段 |
| 可恢复工作 | 既有本地 compensation_event 机制 | Handler-owned event 工厂，事务内登记、提交后执行；不包含个人原值或 NI 秘密 |
| NI 采集绑定 | Payment 尚无批准的接受前载体 | 单独完成最小字段/索引和生命周期设计；setupId 不能借用未创建的 Attempt，不擅自在 Commerce 新建 Payment 事实表 |

不在本轮生成 ALTER TABLE：完整目标模型与存量库仍有前序准备迁移差异，必须在数据切片核对准确基线后生成审查文件。新增公开操作也不意味着本轮自动注册四行 DML；Gateway/Platform 实际注册行数由后续环境查询确定。

## 5. 研发顺序与每段交付

| 顺序 | 范围 | 可验收结果 / 前提 |
|---|---|---|
| A | Commerce Customer 契约、Token 入口、GET/Consumer | 真实 HTTP、字段/权限/隐私边界与本地保存回填；Consumer 数据变更及真实 Vault 幂等用途先明确。外部 Simulator 只在测试源码，不生成成功 Mock 业务实现 |
| B | Commerce 准备编排及 CCT/Tax/文档/Intent Owner 适配 | 固定 OE/合同/金额、幂等恢复和取消竞争；真实依赖缺失时明确不可用，不能声称支付 READY。Risk 本期不改 |
| C | Payment NI 配置/采集绑定/提交/查询及 Commerce payments | 首先解决 NI 补充中的绑定、首次响应丢失与 3DS 投影门槛，再接真实 Adapter；公共四操作不因内部拆分变多 |
| D | Checkout Web 绑定与真实入口联调 | 按已认可页面验证骨架、资料锁定、一次点击确认/Tokenize、3DS 和最终结果；Gateway/环境配置单独列明后实施 |

当前只完成方案，不将上表视为全部仓库、数据库或环境的执行授权。若首轮只实施 Commerce，应把外部不可用明确列为首片边界，而不是交付看似可付的假成功页面。API JAR 与业务测试均复用仓库 Maven/格式门禁，版本沿现行技术栈，不另选框架。

## 6. 验证计划

- AC-01/06：Customer HTTP 的 Token 格式、越权、期限、状态视图、合法短期链接、敏感字段白名单和 no-store。
- AC-02/08：保存/准备的同 Order 竞争、不同 Order 并行、Vault 成功本地失败、恢复原商业时点、Owner 响应坐标冲突、准备后修改被拒绝。
- AC-03/07：三个接受与网络证据全有或全无、旧记录复用、部分历史记录拒绝、事务提交后才外呼、回滚不触发补偿。
- AC-04/05：NI 同一次 session_id 和不同 Key、渠道绑定改变、首次响应丢失、已知引用查询、UNKNOWN 占用、授权不显示 PAID、迟到成功/乱序输入。
- 四个 Processor 的实际入口测试；每个补偿 Handler 单独测试，不能用 HTTP 成功间接替代 Handler 的 Claim/Lease、重复执行、失败/重启恢复证据。
- Java 自动测试使用仓库本地 MySQL/Redis，外部 HTTP Simulator 在测试目录、走真实 Owner Client；不用 Mockito 行为 Stub、H2、Testcontainers 或应用启动建表。真实 NI 沙箱证据单列，测试模拟不冒充真实付款。

交付当前方案后先确认 NI 补充和明确的首片研发范围，再进入代码；公开字段已经确认，不重复要求用户逐字段批准。
