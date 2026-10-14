# 自建 Card Vault 与支付卡采集

> **后继入口与工具绑定：** Customer 入口按[Commerce 后继](../../../commerce-service/docs/requirements/20260912-customer-checkout/commerce-entry-amendment.md)归 Commerce；交易级工具 Token、统一 pay、无独立会话表及 Card 受控适配职责按[Execution 修订稿](../../20260913/payment-Execution设计/instrument-token-amendment.md)评审。本文的旧 Payment Submission、Card Token 和材料传递方案不能直接用于该新目标；未被取代的加密与审计约束保留。真实 Card 仍需独立接入验证。

2026-09-12 用户已确认自建存卡、Payment内独立Card模块及一次 Pay 请求同时提交卡信息与协议确认，由 Payment 调用 Commerce 保存确认后执行支付。本需求接续 [Customer Checkout](../commerce-CustomerCheckout接口设计/requirement.md)；现状分析见[技术入口](./technique.md)，阶段3字段与数据见[接口稿](./api-contract.md)和[数据稿](./data-design.md)。不把未来PCI计划记为当前合规/上线状态，确认与验证见 [delivery.md](./delivery.md)。

## 1. 目标与保留决定

由 Payment 的独立 Card 业务模块承担卡采集处理、PAN 加密存储、平台 Card Token 和受控用卡。Commerce 继续负责订单、OE/金额/合同与法律确认，Payment 负责支付资格、渠道选择和资金执行；原始卡数据不加入 Commerce 请求或数据库。

Card 模块属于 `payment-orchestration-service`，建议 Maven 名称为 `paytech-payment-card`。本次确认的是同一 Payment 服务内的业务能力模块，不增加核心系统；也不因模块拆分增加独立仓库、进程或数据库。运行时卡数据环境与访问权限需按实际部署评估，不能把 JAR 边界视为安全隔离证明。

首个收单 Channel 仍为 NI；本次改变卡数据托管方，不取消 NI 的收单职责。原四个 Customer 基础路径保持，其中 payments 的公开 Owner 改为 Payment，另有原提交查询；无前端 Order Version、文档链接、点击 Pay 合并确认、事务内只操作数据库等决定保持。

## 2. 本阶段边界提案

- 自建 Card Vault 是 Payment Card 模块内的卡库能力；卡采集业务、Token、卡库持久化和接触明文的渠道执行部分集中在这一个模块。现有只处理非卡敏感值的 Vault 保持职责，不把它的 Protect/Reveal 开放为任意卡字段。
- 支持必要 PAN 密文、卡有效期及最小描述信息。确切字段、令牌作用域、复用/撤销与保留期限在数据设计阶段固定，当前不生成建表 SQL。
- CVV 仅用于当前授权的瞬时处理；本项目拟全程禁止持久化 CVV，包括数据库、缓存、队列、日志、备份和恢复快照。授权后不得保存，即使加密也不例外。[PCI SSC](https://www.pcisecuritystandards.org/faqs/are-merchants-allowed-to-request-card-verification-codes-values-from-cardholders/)
- 平台 Token 必须校验所属 Client/消费者或订单、允许用途和当前状态，不能把“知道 Token”当作任意扣款/解密权限；不向 Commerce 或一般后台开放读取完整 PAN 的能力。
- 普通支付编排通过 Card 模块公开业务接口传递 Token 与获准的支付执行命令；PAN 解密及向 NI 传卡在模块内部完成，不返回完整 PAN。统一支付协议入口由 Payment `web` 装配，卡输入委托 Card 模块处理，协议确认使用独立白名单 DTO 调用 Commerce；若同进程运行，该入口和所在 Payment 进程也必须纳入卡数据环境设计。
- 不自动加入“记住卡”、跨订单免输卡、订阅/MIT 或扣款授权等产品能力；这些与技术存卡不同，需要独立的授权和用途规则。
- 本期设计/开发验证使用合成数据。真实卡采集上线前确认适用 PCI 范围、评估/验证方式、收单方接入要求及运营控制；当前不部署公开采集入口或写入真实卡数据。

存储 PAN 必须使其不可读，但加密本身不会自动让系统退出 PCI 范围。[PAN 存储要求](https://www.pcisecuritystandards.org/faqs/1222/)、[加密与 PCI 范围](https://www.pcisecuritystandards.org/faqs/1086/)

## 3. 验收标准

- CARD-01：PAN 仅随统一 Customer payments 请求交给 Payment Card 模块处理；Commerce、其他 Payment 通用命令/查询、现有非卡 Vault、日志/事件均无原始卡数据。模块表只由模块持久化接口访问，同进程部署不宣称隔离于 Payment 运行环境。
  - **2026-10-14 修订（[卡数据经 Platform 渠道执行决定](../../../agent-docs/decisions/2026-10-14-card-data-platform-execution.md)）：** 渠道卡直付由 Platform 执行时，Platform 凭支付工具 Token 经 Card 读卡接口读取卡原值，只在本次执行内存中使用、不记录日志；其余位置的约束不变。
- CARD-02：平台 Token 不编码 PAN，限定归属与用途；越权、失效、重复使用符合明确的令牌生命周期规则。
- CARD-03：PAN 的存储、传输、密钥权限/轮换/备份恢复均有可验证设计；日志、追踪和错误也不泄漏 PAN。
- CARD-04：CVV 不落任何持久介质；授权完成、超时或进程恢复不能通过重读已保存 CVV 自动重付。
- CARD-05：Payment 保持固定 OE、唯一活动支付和真实渠道状态；首次响应丢失保持原执行身份，不自动换卡 Token 或再扣款。
- CARD-06：所有直接和间接外部服务/KMS/渠道调用在数据库事务外；事务仅操作所属数据库，结果使用独立短事务写回。
- CARD-07：前端的卡采集组件和 API 授权/防篡改边界有独立设计；使用 iframe 或独立域名本身不作为自动缩小 PCI 范围的证明。
- CARD-08：开发/测试结果与真实卡上线合规证据分开记录；没有评估证据就不标记“已 PCI 合规”。
- CARD-09：正常 Pay 点击只有一次支付提交 HTTP 请求，Body 同时包含卡与当前合同确认。Payment 先取得 Commerce 已提交的三份接受事实，再执行支付；独立 /card 与公开 acceptances 不提供，模块拆分不暴露为前端两步编排。
- CARD-10：协议确认响应丢失、支付失败或请求中断均恢复原命令；接受可成功而支付失败，不回滚接受或重复扣款。确认命令不反向调用 Payment。

## 4. 对前序方案的取代范围

本次明确指令取代“只能由 Channel 托管卡数据”的产品方向，并将卡能力归到 Payment 内一个业务模块；前序“独立卡库”不再解释为默认新增独立服务。新的原始卡处理例外限定于本专项拟建设的 Payment 统一 Customer 支付入口与 Card 模块，不扩大 Commerce、其他通用支付接口或非卡 Vault 的权限。详见[技术入口](./technique.md)。

前序 NI Hosted Session 的 hostedSessionApiKey/outletRef、NI session_id 作为 Customer card.token，以及接受前 NI setup 绑定均不再是新方案的必经合同。NI 专有执行、查询和认证仍需 Payment Adapter 核对；不得把平台 Token 直接送入只接受 NI Session 的端点。
