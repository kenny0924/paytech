# Payment Card 与 Customer 支付接口设计

> **后继入口与工具绑定：** Customer 入口按[Commerce 后继](../../../paytech/acquiring/docs/requirements/20260912-customer-checkout/commerce-entry-amendment.md)归 Commerce；交易级工具 Token、统一 pay、无独立会话表及 Card 受控适配职责按[Execution 修订稿](../../20260913/payment-Execution设计/instrument-token-amendment.md)评审。本文的旧 Payment Submission、Card Token 和材料传递方案不能直接用于该新目标；未被取代的加密与审计约束保留。真实 Card 仍需独立接入验证。

2026-09-12 阶段 3 修订稿。用户已确认一次 Pay 请求同时提交卡信息与协议确认，由 Payment 接收；Payment 调用 Commerce 保存接受事实后继续支付。本文取代前轮“Commerce 登记 → 浏览器 POST /card”的拆分；独立 Card 模块保留。本轮只调整设计，没有实现、发布接口或修改数据库。数据与恢复见 [data-design.md](./data-design.md)，确认与验证见 [delivery.md](./delivery.md)。

方案涉及 Commerce、Payment 两个核心系统及 Checkout Web：公共操作共 5 个（Commerce 3 个、Payment 2 个）；保留原 4 个基础路径，payments 改由 Payment 承接，另有 Payment 提交查询。内部新增 4 个具体操作；取消前稿独立公开 /card 和内部 registrations 提案。表与技术增量见数据稿，不在此重复维护统计。

## 1. 接口与标识

沿用 `B=/customer-gateway-api/v1/checkouts/{checkoutToken}`；定义 `P=B/payment-submissions/{submissionId}`。下表是服务 Mapping，实际公共入口仍由 Platform/Gateway 精确路由到 Owner，本轮不配置网关。

| Owner | Method / Path | 用途 |
|---|---|---|
| Commerce | GET B | 原 Checkout 视图、订单和支付概要 |
| Commerce | PUT B/consumer | 原消费者保存 |
| Commerce | POST B/preparation | 原 OE/金额/合同准备及 Intent 创建 |
| Payment | POST B/payments | 同一个请求接收法律确认与卡输入，编排确认和原支付执行 |
| Payment | GET P | 查询原提交、当前支付及已有 3DS 指令 |

不提供独立公开 acceptances、/card、tokenize、初始化采集会话或 resume 操作。Pay 前的订单准备仍独立；一次支付提交不包括随后必要的状态查询和 3DS 交互。NI SDK 的渠道交互不算 Commerce API；若沙箱证明还需第一方辅助接口，按准确用途补充合同。

| 标识 | 规则 |
|---|---|
| checkoutToken | 原 Checkout Public Token，保持既有格式、权限与原到期时间；不视为 Account 登录态 |
| submissionId | Payment Snowflake ID，对外正十进制字符串；一次已受理 Pay 命令的定位符，不是 bearer Token，也不是默认幂等键 |
| Card Token | `card_` 加 32 个密码学随机字节的无填充 Base64URL，共 48 字符；只由 Payment Card 模块生成及使用，不返回浏览器或 Commerce；不能单独授权扣款 |

Payment 先通过 Commerce 的只读访问校验获取可信 Order/Session/Intent，再核对本地 Intent 或原 Submission 的绑定。不得根据浏览器自报 Client、OE、金额、MID 或成功状态授权。公开 payments 由 Payment web 装配；敏感输入交给 Card 模块，发往 Commerce 的请求使用独立白名单 DTO，绝不转发原 Body。

继承原公共合同的 Money/ID/Instant、RFC 9457、Request ID 与三写操作幂等规则。没有 expectedOrderVersion、orderVersion、schemaVersion 外部字段。所有响应 `Cache-Control: no-store`。含卡入口上限建议 8 KiB，拒绝未知字段、重复 JSON 成员、非 JSON、Content-Encoding 压缩及 URL 卡参数；它取代普通接口忽略未知属性的规则，仅作用于该入口。错误只返回安全 field/message；代理与应用限制大小且不能把 Body 缓冲到文件。

## 2. Commerce 视图与准备的字段修订

原[Customer 字段合同](../commerce-CustomerCheckout接口设计/customer-api-contract.md)继续拥有订单、Consumer、seller、legal、金额与到期字段。Commerce 保留三个公共操作，协议接受改为内部命令；本节统一维护支付概要。

- 删除 cardSetup、NI configuration、setupId、outletRef、Hosted Session Key。自建卡组件属于已发布前端，不通过 Commerce 下发任意 SDK URL。
- preparation 的 200 只表示订单、实际 legal 文件、最终金额和唯一 Intent 已准备完成；不代表已收卡、已接受协议或选定 MID。
- CheckoutView.payment 通过 Payment 内部只读查询获得；不从 Commerce 的确认回执推断扣款状态。
- AWAITING_CARD 仅用于已受理请求丢失瞬时输入、且证明资金尚未发送的恢复分支。正常点击不会先返回该状态让前端再调用第二个卡接口。
- 删除前稿 SUBMIT_CARD。PAY 在 NOT_STARTED/允许新尝试的确定失败状态下表示新提交；在 AWAITING_CARD 下只允许原键重发同一个 payments 请求。其他活动、3DS、UNKNOWN、AUTHORIZED 或成功状态不开放 PAY。
- Commerce 的 nextAction 仅含 actionId/type/expiresAt。原 3DS 指令由 Payment 的 POST 响应或 GET P 返回；当前事实不可用时关闭 PAY。

Commerce PaymentView 完整成员：

| 字段 | 类型 / 可空 | 规则 |
|---|---|---|
| availability | AVAILABLE / UNAVAILABLE | 当前 Payment 概要是否取得权威依据；UNAVAILABLE 不允许继续付款 |
| submissionId / submissionExpiresAt | Id / Instant，均可空 | 无受理时 null；原提交的稳定身份与原截止时间 |
| attemptId | Id / null | 协议未确认或尚未绑定卡并创建 Attempt 时 null |
| status | enum / null | NOT_STARTED / AWAITING_CARD / PROCESSING / REQUIRES_ACTION / AUTHORIZED / UNKNOWN / SUCCEEDED / FAILED / CANCELLED / VOIDED / EXPIRED；没有可信状态时 null |
| nextAction | object / null | actionId(Id)、type(THREE_DS_CHALLENGE)、expiresAt(Instant/null)；无动作则 null |
| failure | object / null | 受控 code(1–64)、message(1–255)；无失败则 null |
| observedAt | Instant / null | 权威事实观察时间；没有依据时 null |

NOT_STARTED 必须由 Payment 确认 Intent 尚无提交。确认协议期间的已受理命令为 PROCESSING，不再提供 Commerce 登记中的 SUBMITTING 状态。已有在途/未知资金事实始终优先；确认完成不等于 Attempt 已创建或支付成功。

## 3. Payment POST B/payments

前端在一次 Pay 点击中提交一个请求；`Idempotency-Key` 由前端为这次支付生成并在网络重试时复用，格式沿原 Customer 公共合同。没有单独的卡命令或卡幂等键。

```json
{
  "legalConfirmation": {
    "consumerContractId": "10482001",
    "confirmed": true
  },
  "card": {
    "pan": "<本次卡号>",
    "expiryMonth": "09",
    "expiryYear": "2028",
    "cardholderName": "<本次持卡姓名>",
    "cvv": "<本次CVV>"
  }
}
```

示例中的尖括号为占位文字，不是可提交的测试卡值；研发只用明确的合成测试输入。两个对象及下列全部成员必填，无默认值、不接受 null。

| 字段 | 类型 | 规则 |
|---|---|---|
| legalConfirmation.consumerContractId | Id | 同 Order/Session 实际展示的当前冻结合同；服务端读取其三份文档，不接收整份条款或文档 URL/Hash |
| legalConfirmation.confirmed | boolean | 必须 true，表达当前 Pay 点击的确认；不能由页面加载或后台补偿生成 |
| card.pan | string | ASCII 数字，12–19 位；服务端做长度/Luhn/已支持卡种校验；展示空格由组件提交前移除 |
| card.expiryMonth | string | 固定两位，01–12 |
| card.expiryYear | string | 固定四位；按 UTC 月判断未过期，不猜测 Channel 支持的未来上限 |
| card.cardholderName | string | 1–128 Unicode 字符，NFC、去首尾空白、拒绝控制字符；不强制覆盖为 Consumer fullName |
| card.cvv | string | ASCII 数字 3–4 位，受支持卡种约束；仅本次请求/发送链路使用，不持久化或生成摘要 |

没有前端 amount、currency、Client、Order、OE、Channel、MID、submissionId、Card Token、支付成功状态或卡等级输入。BIN 推断不能冒充 Channel 确认的定价事实。

### 3.1 执行顺序与回执

1. Payment 做字段验证，在事务外调用 Commerce 访问校验，读取原命令和本地 Intent；Card 模块在事务外计算受限 HMAC。
2. Payment 短事务认领原 Pay 命令并创建 Submission，冻结原合同/Intent/期限，写本地恢复任务；此时没有 Attempt、资金动作或外部调用。
3. 提交后调用 Commerce 内部协议确认。Commerce 重新验证当前访问与合同，短事务原子保存三份接受、LEGAL_ACCEPTANCE 网络证据和命令回执；返回后不再调用 Payment 登记或启动支付。
4. Payment 取得确认结果后，在事务外读取新鲜 COM-I10 及必要 Owner 资格，完成 Card 加密准备；短事务绑定 Card、唯一 Attempt 和接受事实。路由、Risk、KMS、NI 都在各自所需事实提交后、数据库事务外执行；结果另开短事务写回。
5. Payment 返回 SubmissionView：200 为当前可确定响应（包含需 3DS 或明确失败），202 为已可靠受理且仍在执行/查询的原提交，并返回 Retry-After。202 不承诺后台可在缺少 CVV 时完成首次资金请求。

POST 与 GET P 共用下节 SubmissionView，消费者没有“先获取提交 ID，再发卡”的正常路径。协议已接受而支付失败是合法结果：接受事实不回滚，也不因此创建第二次扣款。

示例为已受理但尚未获得渠道确定结果的 202；null 字段并非省略：

```json
{
  "submissionId": "10482501",
  "expiresAt": "2026-09-12T14:30:00.000000Z",
  "attemptId": "10482601",
  "status": "PROCESSING",
  "card": {"last4": "1234"},
  "nextAction": null,
  "failure": null,
  "observedAt": "2026-09-12T14:10:00.000000Z",
  "pollAfterMs": 2000,
  "retryIdempotencyKey": null
}
```

在本地受理前拒绝时返回相应 Problem；受理后确认响应丢失不得谎报“未付款”，按原 Submission 恢复。不能将 202、协议确认回执或 SDK 结束事件投影为订单 PAID。

### 3.2 幂等与输入丢失

1. 命令作用域为可信 Checkout Session + SUBMIT_CHECKOUT_PAYMENT + 原 Idempotency-Key。同键固定一个 Submission；同 Intent 的活动提交和活动/UNKNOWN/已授权/成功 Attempt 阻止新键。
2. HMAC 绑定可信 Client/Session/Intent、当前合同及 confirmed=true、规范化 PAN/有效期/姓名；**排除 CVV**。Body、PAN 裸 Hash、CVV Hash/密文均不保存。具体字节和轮换按数据稿。
3. 同键变更合同或卡字段返回409 IDEMPOTENCY_KEY_REUSED，不能覆盖原绑定。基础字段验证失败尚未受理时可修正输入。CVV 是瞬时输入，不是永久身份的一部分。
4. 同键同参先恢复原执行；已经在途/3DS/UNKNOWN/授权/成功时只返回当前状态，新的 CVV 不触发第二次资金提交。
5. 只有证明资金尚未发送、旧执行者已失去执行权且输入期限仍有效时，才投影 AWAITING_CARD/CARD_INPUT_REQUIRED；用户重新输入原卡并重发同一个 payments 请求。没有后台 CVV 恢复，也不新增 /card 或 resume 接口。
6. 前端只保存非卡原幂等键用于重试。刷新后先 GET B 找到原 submissionId，再 GET P；如允许恢复，retryIdempotencyKey 返回原传输键。该键不是授权凭据，仍要求有效 Checkout Token、相同合同/卡与本地并发门禁；不能按 submissionId 猜一个新键。
7. 已绑定卡只有原尝试确定失败/取消且允许新尝试后，才通过新的 Pay 命令换卡；原三份接受可复用，不新增接受行或覆盖首次时间/网络证据。原输入期限不因重试刷新。

## 4. Payment GET P 与 SubmissionView

无 Body、无 Query。通过 Commerce 校验当前 Checkout 访问权限，再比较原提交的 Order/Session/Intent 绑定，并读取原支付事实/已有指令。GET 不生成提交、接受、卡、Attempt、Channel Order、挑战或扣款。必要的渠道只读查询仍在事务外。

| 字段 | 类型 / 可空 | 含义 |
|---|---|---|
| submissionId | Id | 原 Payment 提交 |
| expiresAt | Instant | 首次受理时冻结的 Checkout/Intent 较早期限 |
| attemptId | Id / null | 原 Attempt，尚未创建时 null |
| status | enum | AWAITING_CARD / PROCESSING / REQUIRES_ACTION / AUTHORIZED / UNKNOWN / SUCCEEDED / FAILED / CANCELLED / VOIDED / EXPIRED；按第5节投影 |
| card | object / null | 仅 last4:string(4)；无卡记录时 null，不返回 Token/有效期/姓名/BIN |
| nextAction | NextAction / null | 同一次 Customer Action，见下表 |
| failure | object / null | 受控 code(1–64)、message(1–255)，不回显 Channel/Risk 原因 |
| observedAt | Instant | 当前事实的权威观察时间，不伪造渠道证据新鲜度 |
| pollAfterMs | integer / null | 2000–10000；等待输入/确定终态通常 null |
| retryIdempotencyKey | string / null | 仅 AWAITING_CARD 且当前允许恢复时返回原 Pay 键，格式沿公共合同；其余 null。仅经本次 Checkout 授权返回，不进入普通日志 |

| NextAction 字段 | 类型 | 规则 |
|---|---|---|
| actionId | Id | 原 Customer Action ID |
| type | THREE_DS_CHALLENGE | 本期 Card 的 3DS 分支 |
| expiresAt | Instant / null | Channel/既有业务有明确期限时返回；没有证据时不捏造渠道期限，输入截止也不能擅自终止在途资金动作 |
| instruction | NiThreeDsInstruction / null | 仅本 Payment 响应提供；当前可读取且可安全复用才返回，不永久冻结到回执 |

NiThreeDsInstruction 是受控类型候选：`type=NI_EMV_3DS`；UUID 字符串 `paymentReference/orderReference/outletRef/threeDSServerTransId`；`messageVersion` 最多16字符；`directoryServerId` 可空、最多64；`authenticationCode` 可空、最多1024；`authenticationUrl/challengeResponseUrl` 为最多4096字符的 HTTPS URL；`threeDSMethodUrl` 同限制且可空。NI 链接需验证环境和同 Order/Payment 坐标，ACS URL 按 Channel 来源与安全策略处理，不能当作后台任意抓取地址。

前端受控 Adapter 根据这个类型调用 NI EMV 3DS SDK；不传整份 NI JSON，不动态执行服务端任意脚本地址。这个裁剪类型须通过 NI sandbox 的 frictionless/challenge/刷新测试后才启用；不足时修改准确字段，不能加任意 JSON。SDK 结束只触发结果查询，不上报“支付成功”。官方流程与限制见 [NI EMV 3DS SDK 指南](https://docs.ngenius-payments.com/reference/alternative-emv-3ds-workflow-websdk-2)。

当前订单已过期时沿原 Checkout 访问策略返回410，不因已知 submissionId 延长消费者权限；后台仍继续查询在途/UNKNOWN 支付。需要延长结果页可访问期属于独立产品决定。

## 5. 状态投影

| 本地/渠道事实 | 浏览器状态与操作 |
|---|---|
| 已受理，协议确认或其他必要检查进行中 | PROCESSING，只查询；接受结果未知不等于渠道扣款 UNKNOWN |
| 原请求丢失输入，确定无可能已发送资金动作，且已失效旧执行权 | AWAITING_CARD，原键重新提交同一个 payments 请求；已有摘要/卡绑定均不可换卡 |
| 执行或查询中，尚无可返回动作 | PROCESSING，只查询 |
| Channel Order 创建响应未知或资金请求可能已发送但未确定 | UNKNOWN，只查询，不释放占用 |
| Attempt REQUIRES_ACTION | REQUIRES_ACTION；有可用 instruction 才继续原 3DS |
| Attempt AUTHORIZED | AUTHORIZED，不显示 PAID，不新建支付 |
| Attempt SUCCEEDED | SUCCEEDED；Commerce 等待自己的 PaymentSucceeded 消费，不由浏览器写 PAID |
| Attempt FAILED/CANCELLED/VOIDED | 对应确定结果；新的 PAY 仍由两方实时检查 |
| 输入到期且证明没有可能已发送资金动作 | EXPIRED，关闭原输入资格；不将未知资金当作失败 |

Submission 与 Attempt 不重复保存同一套资金状态；视图优先展示在途资金，不能用输入到期遮盖 UNKNOWN。

## 6. 内部 HTTP 合同

以下均为拟新增 Owner 契约，通过私网、轻量 `*-api` 的 `@HttpExchange` 和公共负载均衡调用，不经过 Gateway。无新 Feign、base-url 或服务专用 MDC；类型名不带 V1 后缀。

| Owner | Method / Path | 请求（除注明外必填） | 响应 |
|---|---|---|---|
| Commerce | POST `/checkout-payment-access/v1/validations` | checkoutToken；purpose=SUBMIT_PAYMENT / READ_PAYMENT | AccessContext：orderId、orderNo、checkoutSessionId、clientId、paymentIntentId、consumerContractId、expiresAt；ID/公开编号/时间格式沿公共类型 |
| Commerce | POST `/checkout-payment-confirmation/v1/confirmations` | checkoutToken、submissionId、paymentIntentId、legalConfirmation、networkEvidence；Idempotency-Key=submissionId 的规范十进制字符串 | ConfirmationReceipt，见下文 |
| Commerce | POST `/checkout-payment-confirmation/v1/queries` | submissionId、orderId、checkoutSessionId、paymentIntentId | confirmation: ConfirmationReceipt/null；仅可信 Payment 可查询这组原命令坐标，全部匹配才返回 |
| Payment | POST `/checkout-payment-submission/v1/queries` | paymentIntentId；可空 submissionId，null 取活动或最近提交 | 第2节安全 PaymentView；没有受理为 NOT_STARTED，不创建事实或返回卡/NI材料 |

AccessContext 要求订单已经准备完成且有唯一 Intent；SUBMIT_PAYMENT 允许读取原请求定位所需上下文，不等于允许新扣款，已支付/活动状态的重放仍能定位原结果。READ_PAYMENT 可查询授权期内的终态。无准备返回 CHECKOUT_NOT_READY；资源、期限或权限拒绝沿公共 Problem。Payment 将返回坐标与自己的冻结 Intent 比较，前端不提供这些业务事实。

ConfirmationReceipt 仅含 submissionId、orderId、checkoutSessionId、paymentIntentId、consumerContractId、consumerTermsAcceptanceId、refundPolicyAcceptanceId、privacyNoticeAcceptanceId、legalAcceptanceNetworkEvidenceId（均为 Id）及 acceptanceCompletedAt（Instant），均非空。它是原已提交接受事实的稳定回执，不是新的扣款许可；真正执行前仍读取 COM-I10 的新鲜 Order Version/期限及必要 Risk/执行门禁。

networkEvidence 使用原 Commerce LEGAL_ACCEPTANCE 输入契约：仅受信入口得到的 clientIpAddress、userAgent、trustedProxyChain 及已批准派生维度，限制沿 [Consumer/网络证据设计](../../../agent-docs/plans/2026-08-24-commerce-privacy-fulfillment-simplification-design.md)。浏览器 Body 不提供这些字段；Payment 只瞬时转交，Commerce 负责持久化，不能从不受信的 X-Forwarded-For 推断。此次变更要求确认 Gateway 私有链路实际保留的可信证据合同，不能把 Client API 特有 Header 规则直接套给 CHECKOUT。

Commerce 确认步骤：验证 Token 与冻结 Intent/合同 → 复用原命令或首次原子保存恰好三份接受、同 Session 网络证据及完成回执。确认命令作用域为可信 Payment 调用方/Checkout Session，键为 submissionId；Hash 只覆盖 submissionId、paymentIntentId、consumerContractId、confirmed=true 的规范身份。Token 和网络信息不进 Hash；原回执优先，重试产生的 Request ID/网络变化不改写首次成功保存的证据。不同新 Pay 对同一已接受合同复用原三行及原证据；不因换卡再造一组接受记录。确认事务及其提交后逻辑均不反向调用 Payment。

确认响应丢失时，原 Payment 命令用上述只读 queries 恢复已有回执；null 仅表示查询当时未见回执，不证明在途确认已取消。后台不能据此补造同意或网络证据，也不保存 Checkout Token 以便重放。仍无回执且需要输入时，下一次原 payments 请求带当前合法确认和可信请求证据，调用同一 Commerce 幂等命令；并发确认只会保存一个结果。当前请求结束后，补偿只能查原事实或标记需输入，不能自动发起确认/资金首发。

COM-I10、COM-I13 职责保留。三个新增 Commerce 操作都只访问 Commerce 数据/已冻结合同，不调用 Payment，因此 Payment → Commerce 不形成同步回环；Commerce GET B 调用 Payment 只读概要也不触发反向访问校验。checkoutToken 不写 Payment 表、命令快照、事件或日志。

## 7. 错误与研发门禁

沿用400/422字段错误、404资源不匹配、410到期、409幂等/支付占用、429限流和502/503依赖错误。安全业务码候选：PAYMENT_SUBMISSION_NOT_FOUND、PAYMENT_SUBMISSION_EXPIRED、CARD_INPUT_REQUIRED、CARD_INPUT_ALREADY_BOUND、CARD_DATA_UNAVAILABLE；统一由公共 Problem Mapper 输出，不是新增数据库枚举。

Payment/NI 不可达不能承诺“没有扣款”。输入丢失且证明资金未发送时以原 SubmissionView 的 AWAITING_CARD + failure.code=CARD_INPUT_REQUIRED 提示，不能用失败回执冻结并丢失原恢复身份。所有 Controller/Processor 及独立恢复 Handler 都有入口测试；完整事务、数据与实施清单见数据稿。
