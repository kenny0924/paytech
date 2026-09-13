# Customer Checkout 接口字段

> 当前 Card 方向与提交顺序已确认：Payment 接收一次包含卡与协议确认的 payments 请求，调用 Commerce 保存法律确认后继续支付。订单/Consumer/legal 字段仍由本文拥有；卡提交、执行引用、支付视图及内部接口统一见 [Payment Card 字段稿](../payment-自建CardVault/api-contract.md)，该字段稿为阶段 3 评审稿。旧 NI Hosted Session 只保留在历史研究文档中，不作为当前实施合同。

## 1. 基础路径、Owner 与公共约定

`B=/customer-gateway-api/v1/checkouts/{checkoutToken}`，无尾随 `/`。前三个操作由 Commerce 提供，payments 由 Payment 提供，Checkout Web 消费；可信 Channel 为 CHECKOUT。浏览器页面仍是 `/c/{checkoutToken}`，公开 Gateway 映射与激活另行实施。

| Method / Path | 用途 | 请求 Body | 成功响应 |
|---|---|---|---|
| GET B | 加载、刷新和查询支付结果 | 无 | CheckoutView |
| PUT B/consumer | 保存消费者资料 | 姓名、邮箱、账单地址 | 保存结果 |
| POST B/preparation | 准备最终订单和 Intent | 无 | 准备结果、金额、卖方、legal |
| POST B/payments | Payment 承接一次支付提交 | 卡输入 + 合同确认 | 原 SubmissionView/3DS，见 Card 字段稿 |

另有 Payment 的 GET B/payment-submissions/{submissionId}，字段由 Card 稿拥有；整个本期目标共五个公开操作，不提供独立 /card。

- 所有接口校验当前 Token、同 Order/Session 归属、期限和操作权限。Token 不是 Account Session 或 Client Credential，不接受浏览器选择 OE/MID/Client、提交应付金额或宣告支付成功。
- Customer 请求和响应均无 `expectedOrderVersion`、`orderVersion`、`schemaVersion`。数据库版本、CAS、资源锁和内部 COM-I10 的 Order Version 保留；浏览器不负责并发版本管理。
- 三个写操作都要求 `Idempotency-Key`：1–255 字符，首字符字母或数字，其余只允许字母、数字、`.`、`_`、`:`、`-`。服务端作用域为同一 Checkout Session + 操作；同键异参 409，网络重试复用原键。不同键也不能绕过订单唯一准备、唯一在途支付。
- 相同命令先校验当前访问权限，再定位原受理结果；不能因原命令已使订单进入终态就执行第二次，或用新命令前置条件覆盖原回执。每个尚未执行的外部副作用仍重验资格。
- 成功使用 `application/json`，错误使用现行 `application/problem+json`。全部响应 `Cache-Control: no-store`。Consumer、Token 和 Channel 临时参数禁止 Body 日志，Checkout Token 在访问日志中脱敏。未知普通 JSON 属性沿现行 MVC 规则忽略；含卡 payments 按 Card 合同严格拒绝未知/重复字段并限制为8 KiB；不支持的 Query、重复单值 Query、GET/Preparation Body 返回 400。
- 字段类型/结构错误 400，字段约束错误 422。建议本期写请求上限 16 KiB；Channel 特殊限额须在具体分支评审，不能截断凭据。

| 公共类型 | 格式 |
|---|---|
| CheckoutToken | 复用既有大小写敏感 34 字符 `chk_` 格式；CRC 不替代授权 |
| Id | BIGINT 的正十进制字符串，最大 9223372036854775807；仅作定位，不替代归属校验 |
| OrderNo | 1–64 字符的既有公开编号，浏览器当作不透明字符串 |
| Money / Quantity | 十进制字符串，无指数；金额精度、范围和数量约束沿原 Client 创建合同，不使用 JSON 浮点 |
| Instant | UTC RFC 3339，六位微秒和 `Z`；时间由服务端产生 |
| Country / Currency | Owner 支持目录中的两位/三位大写代码 |
| 可空响应字段 | 除专有分支外，下表列出的成员均出现；尚无事实使用 null，不用空字符串或零伪造 |

各 Owner 独立拥有自己的 Mapping/DTO，SubmitPayment 类型归 Payment，不将原始卡 DTO 放进 Commerce API JAR。Java 入口 DTO 建议为 `GetCheckoutResponseBody`、`SaveConsumerRequestBody/ResponseBody`、`PrepareCheckoutResponseBody`、`SubmitPaymentRequestBody/ResponseBody`；无请求 Body 的操作不建空 RequestBody，类型名不带 V1。

## 2. GET B：查询 Checkout

唯一可选 Query：`includeConsumer`，Boolean，默认 false。首次需要回填资料时传 true；轮询不取个人原值。无 Body。

### 2.1 顶层字段

| 字段 | 类型 / 可空 | 含义 |
|---|---|---|
| orderNo | OrderNo | 当前订单编号 |
| orderStatus | enum | CREATED / OPEN / PAID / CANCELLED / EXPIRED，沿现行 Commerce 订单状态 |
| checkoutStatus | enum | PREPARING / OPEN / PAYMENT_PROCESSING / COMPLETED / CANCELLED / EXPIRED |
| preparationStatus | enum | PENDING / READY；PENDING 本身不代表准备命令已经启动 |
| amountStatus | enum | ESTIMATED / FINAL |
| currency | Currency | Client 创建时固定的币种 |
| consumerCountryCode | Country | Client 创建时固定的消费者计税国家；不是浏览器可修改字段 |
| locale | string，1–35 | 服务端确定的展示语言，沿 Owner Locale 目录 |
| expiresAt | Instant | 原 Checkout 期限，不因重试或刷新延长 |
| amounts | Amounts | 六项订单金额，见下表 |
| items | Item[] | 原订单行，非空；数量上限沿创建合同 |
| consumer | ConsumerView | 保存状态及按需回填资料 |
| seller | Seller 或 null | 已冻结法律卖方，准备前 null |
| legal | Legal 或 null | 同一冻结消费者合同及三份文档链接，准备前 null |
| payment | PaymentView 或 null | 无 Intent 且无支付受理事实时 null；字段见第 5 节 |
| allowedActions | enum[] | 当前可提示的操作，不替代命令端实时校验 |
| pollAfterMs | integer 或 null | 2000–10000 的建议轮询间隔；无需轮询为 null |

`allowedActions` 为 SAVE_CONSUMER / PREPARE / PAY / CONTINUE_AUTHENTICATION / CHECK_PAYMENT_STATUS。PAY 在等待输入恢复时只能用原键重发同一个 payments 请求；完整支付操作门禁见 Card 字段稿。CONTINUE_AUTHENTICATION 表示执行已有 SDK 指令，不是新的 HTTP 接口。文档是否可打开直接看 legal；本期只支持 Card，不提前返回 Wallet/Bank 支付方式目录。

### 2.2 订单、消费者与卖方

| 对象 | 字段与规则 |
|---|---|
| Amounts | merchandiseAmount、discountAmount、netAmount、taxAmount、roundingAmount、totalAmount 六项均出现，全部为 Money；ESTIMATED 时后三项 null，FINAL 时均为实际值。netAmount 为折扣后税前金额，totalAmount 为最终应付金额；零税明确返回零 |
| Item | name（1–255）、description（可空，最多 1024）、quantity（正十进制字符串）、unitAmount、discountAmount、netAmount；后三项为 Money，均来自原订单行，不接受 Customer 改写 |
| ConsumerView | status=MISSING/COMPLETE；details 为第 3 节资料对象或 null，仅 includeConsumer=true 且已有资料时返回。编辑权限由 SAVE_CONSUMER 表达 |
| Seller | legalName（1–255）、registrationNumber（可空，最多 128）、taxRegistrationNumber（可空，最多 64）、addressText（1–512）、supportEmail（可空，最多 254）、supportPhone（可空，最多 32）。从冻结卖方事实投影，不返回内部 entityNo |

示例：首次加载，所有值为合成设计数据；税额待确定。

```json
{
  "orderNo": "ord_example_10482",
  "orderStatus": "CREATED",
  "checkoutStatus": "PREPARING",
  "preparationStatus": "PENDING",
  "amountStatus": "ESTIMATED",
  "currency": "USD",
  "consumerCountryCode": "AE",
  "locale": "en",
  "expiresAt": "2026-09-12T14:30:00.000000Z",
  "amounts": {
    "merchandiseAmount": "120.00",
    "discountAmount": "0.00",
    "netAmount": "120.00",
    "taxAmount": null,
    "roundingAmount": null,
    "totalAmount": null
  },
  "items": [{"name":"Design Essentials","description":"Digital course","quantity":"1","unitAmount":"120.00","discountAmount":"0.00","netAmount":"120.00"}],
  "consumer": {"status":"MISSING","details":null},
  "seller": null,
  "legal": null,
  "payment": null,
  "allowedActions": ["SAVE_CONSUMER"],
  "pollAfterMs": null
}
```

### 2.3 Legal：直接给出文档链接

| 字段 | 类型 | 规则 |
|---|---|---|
| consumerContractId | Id | 当前 Order/Session 不可变合同根；同时绑定已展示卖方、最终金额及三份文件，支付原样回传进行核对，不是让用户选合同 |
| documents | Document[] | 必须恰好三项且类型唯一 |
| documents[].documentType | enum | CONSUMER_TERMS / REFUND_POLICY / PRIVACY_NOTICE |
| documents[].title | string，1–255 | 用于页面链接文字 |
| documents[].url | HTTPS URL，最多 4096 | 指向准确冻结文件；不能指向“最新模板”。只来自受控文件服务，禁止浏览器决定目标 |
| documents[].urlExpiresAt | Instant 或 null | 短期授权链接的期限；公开且永久不可变文件可为 null |

```json
{
  "consumerContractId": "10482001",
  "documents": [
    {"documentType":"CONSUMER_TERMS","title":"Terms of sale","url":"https://documents.example.com/immutable/example-terms.pdf","urlExpiresAt":null},
    {"documentType":"REFUND_POLICY","title":"Refund policy","url":"https://documents.example.com/immutable/example-refunds.pdf","urlExpiresAt":null},
    {"documentType":"PRIVACY_NOTICE","title":"Privacy notice","url":"https://documents.example.com/immutable/example-privacy.pdf","urlExpiresAt":null}
  ]
}
```

以上 URL 仅示意结构，不是已存在文件服务。实际文档的 documentId/fileId/contentHash 保存在服务端，前端无需回传。准备完成前必须有真实冻结文件；不得拿占位链接让用户确认。个性化文件使用受控短期链接，期限不超过访问权限；刷新链接仍指向原文件。GET 可取得同一文件的新访问链接，不创建或重渲染合同。打开文档不写接受记录。

### 2.4 查询一致性和状态映射

先读 Commerce 本地事实，再在事务外按需查询 Payment 当前视图和 Vault 原值，最后重验最小 Order/Session 状态与内部版本；变化时重新组装，避免跨时点拼接。AUTHORIZED 和当前 Customer Action 不能只靠旧 Commerce 投影，既有设计不新增 PaymentAuthorized MQ。

Commerce GET 只返回已有支付/动作概要，不承载 Card 配置或 NI 指令；Payment 查询只取已有可安全复用的3DS材料，不创建 Channel 会话、重建挑战或提交资金请求。无指令时关闭相应操作，不返回假参数。Payment 不可用时 payment.availability=UNAVAILABLE，关闭 PAY，可展示带 observedAt 的最近可信事实；不存在可信事实就 status=null，不能推断失败或未支付。

Payment SUCCEEDED 但 Commerce 尚未 PAID 时继续显示确认中并禁止再付，待 Commerce 消费权威成功事件后展示成功页。GET 不代替该事件写订单。无效 Token 统一 404；到期 410 返回通用提示，不返回个人化订单/支付结果，也不延长 Token。后台仍负责在途支付收敛。

## 3. PUT B/consumer：保存消费者资料

Header：`Idempotency-Key`。以下为完整替换；可选字段省略或 null 均清空该字段。

```json
{
  "fullName": "Alex Morgan",
  "email": "alex@example.com",
  "billingAddress": {
    "countryCode": "AE",
    "addressLine1": "Example building 10",
    "addressLine2": null,
    "region": null,
    "city": "Dubai",
    "postalCode": null
  }
}
```

| 字段 | 必填 | 类型与校验 |
|---|---|---|
| fullName | 是 | string，1–200 |
| email | 是 | 合法 Email，1–254；去首尾空白，域名小写 ASCII，保留 local-part 大小写，不删除 +tag |
| billingAddress | 是 | object |
| billingAddress.countryCode | 是 | Country；账单地址国家，不覆盖固定的 consumerCountryCode |
| billingAddress.addressLine1 | 是 | string，1–255 |
| billingAddress.addressLine2 | 否 | string，1–255 或 null |
| billingAddress.region | 否 | string，1–64 或 null |
| billingAddress.city | 是 | string，1–128 |
| billingAddress.postalCode | 条件 | string，1–32 或 null；依批准国家规则决定必填和格式，不要求所有国家都有邮编 |

姓名和地址去首尾空白、规范为 Unicode NFC、拒绝控制字符，保留内部空白。phone、consumerReference 和个人属性扩展不在本期输入内。账单国家与计税国家的差异不能静默重算国家或重选 OE；允许的跨国组合交由已批准 Risk/Tax 业务规则校验，本稿不额外设定“一律相等”字段约束，也不宣称任意组合都可支付。

200 保存成功，不回显个人原值：

```json
{"orderNo":"ord_example_10482","consumerStatus":"COMPLETE"}
```

准备启动前，后一次成功保存覆盖前一次；前端须串行提交保存和准备，不能把输入自动保存与 Continue 并发。准备占有订单后禁止修改，返回 409 CONSUMER_DETAILS_LOCKED。只有填完并保存成功才允许 PREPARE。

服务端在事务外执行 Vault Protect Email/Profile，再于短事务重验订单及准备状态、保存 Token 和本地命令结果。Profile 只含姓名与账单地址，不重复 Email。并发控制全在服务端；幂等和恢复不能将个人原文写入通用快照，也不能用可枚举 Email 裸 Hash 代替安全处理。

## 4. POST B/preparation：冻结上下文，准备 Intent

Header：`Idempotency-Key`。**无 Body**。服务端读取已保存消费者资料与原订单，不要求前端重复提交个人信息、方式、币种、金额或版本。

| 响应字段 | 类型 / 可空 | 含义 |
|---|---|---|
| orderNo | OrderNo | 原订单 |
| preparationStatus | PENDING / READY | 最终订单上下文和 Intent 是否准备完成 |
| amountStatus | ESTIMATED / FINAL | 金额确定程度 |
| currency / amounts | Currency / Amounts | 与 GET 相同 |
| seller / legal | Seller / Legal 或 null | 与 GET 相同，准备完成时非空 |
| pollAfterMs | integer 或 null | 等待时使用 2000；无需轮询为 null |

202 示例：已受理准备，服务端可靠任务继续执行；返回 `Retry-After: 2`。

```json
{
  "orderNo": "ord_example_10482",
  "preparationStatus": "PENDING",
  "amountStatus": "ESTIMATED",
  "currency": "USD",
  "amounts": {"merchandiseAmount":"120.00","discountAmount":"0.00","netAmount":"120.00","taxAmount":null,"roundingAmount":null,"totalAmount":null},
  "seller": null,
  "legal": null,
  "pollAfterMs": 2000
}
```

200 表示最终订单 READY、金额 FINAL、seller/legal 的真实冻结文件完整且唯一 Payment Intent 已创建。此时没有收卡，也不因 Card 采集锁定 NI Outlet/MID；技术依赖不可用仍返回503并保留已完成准备阶段，不能返回假READY。

新键不会重选已冻结OE/合同/金额；并发准备复用原准备身份，已经READY时读取原结果。采集与支付输入的时效不回退订单准备。

## 5. POST B/payments：Payment 统一支付入口

该路径由 Payment 承接，Body 同时包含 legalConfirmation 和 card。Payment 调用 Commerce 内部确认接口，仅转交合同确认与可信请求证据；Commerce 在本地事务保存三份接受、网络证据和回执，提交后直接返回。Card 原值不进入 Commerce，Commerce 不再登记或反向启动 Payment。

请求字段、SubmissionView、原键恢复和内部合同统一见 [Payment Card 接口合同](../payment-自建CardVault/api-contract.md)，不在两处维护。公开 acceptances 与独立 /card 均不提供；协议已确认而支付失败时保留原接受，重试不能重复扣款。

## 6. 错误、页面与实施边界

沿用全系统 RFC 9457 Profile；新增 Customer code 以下为字段评审提案，不代表已新增 Java 枚举。

| HTTP / code | 前端处理 |
|---|---|
| 400 INVALID_REQUEST；422 VALIDATION_ERROR | 结构/字段提示，不回显个人原值或令牌；errors 仅含 field/message |
| 400 IDEMPOTENCY_KEY_REQUIRED / INVALID_IDEMPOTENCY_KEY | 修正请求；不得把有受理可能的原支付当作失败重建 |
| 404 CHECKOUT_NOT_FOUND | 通用不可用链接页，不展示订单信息 |
| 410 CHECKOUT_EXPIRED | 到期通用页；如已提交支付，提示勿重复付款 |
| 409 IDEMPOTENCY_KEY_REUSED / IDEMPOTENCY_REQUEST_IN_PROGRESS | 异参冲突或原命令在途，按原命令查询/恢复 |
| 409 CONSUMER_DETAILS_REQUIRED / CONSUMER_DETAILS_LOCKED | 填写资料或展示已冻结资料 |
| 409 CHECKOUT_NOT_READY / CHECKOUT_CLOSED | GET 当前状态，停止无效操作 |
| 409 LEGAL_DOCUMENT_CHANGED | 重新取得同订单冻结 legal，要求实际重新确认；不自动确认另一份合同 |
| 409 LEGAL_ACCEPTANCE_REQUIRED | 明确提示本次支付需要条款确认；不得在服务端补造点击 |
| 409 PAYMENT_IN_PROGRESS | 只查询原支付，不换键重复付款 |
| 410 PAYMENT_SUBMISSION_EXPIRED | 原输入资格到期；先核对原支付，不能借新键绕过未知资金动作 |
| 422 CHECKOUT_NOT_ELIGIBLE / PAYMENT_METHOD_UNAVAILABLE | 展示受控不可用提示，不能换 OE 重路由 |
| 429 入口限流 code | 遵守 Retry-After |
| 502 UPSTREAM_PROTOCOL_ERROR；503 DEPENDENCY_UNAVAILABLE / DEPENDENCY_TIMEOUT | 技术提示；可能已受理时保留原命令，不映射为确定支付失败 |

```json
{"type":"about:blank","title":"Conflict","detail":"Consumer details cannot be changed after preparation has started.","code":"CONSUMER_DETAILS_LOCKED","requestId":"checkout-example-01","timestamp":"2026-09-12T14:07:00.000000Z"}
```

Body 无 status/instance，X-Request-Id Header 与 requestId 相同。业务错误用顶层 code/detail；errors 只用于字段约束。LOADING 和骨架属于前端请求状态，不新增数据库状态。

本稿保留订单、Consumer与legal字段；当前Card及payments字段修订见[Payment Card接口稿](../payment-自建CardVault/api-contract.md)，数据和研发门禁见[数据稿](../payment-自建CardVault/data-design.md)。方案未修改Java、PDManer或数据库，NI权限/恢复/3DS仍需沙箱核验。
