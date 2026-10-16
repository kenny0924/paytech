# dLocal 接入技术方案

- 日期：2026-10-17
- 状态：**已确认**（2026-10-17）：D1～D6 按建议
- 需求：[requirement.md](./requirement.md)
- 依据（已读）：对接包 `README.md`、`notes.md`、`channel.json`、`common/runtime.md`（凭据只按结构读取）；dLocal 官方 [make-a-test-payment](https://docs.dlocal.com/docs/make-a-test-payment)、[retrieve-a-refund-status](https://docs.dlocal.com/reference/retrieve-a-refund-status)；引擎源码 `ScriptFunctions`、`ChannelRequestBuilder`、`HttpWork`、`IntegrationApiDefinitionValidator`；mor-test 现行标准 API `PAY`（v2）、`PAY_QUERY`、`PAY_CANCEL`、`REFUND`（v1）、`REFUND_QUERY`（v2）；Alipay+、PayBy harness。

## 1. 协议要点

| 项 | 内容 |
|---|---|
| 地址 | 沙箱 `https://sandbox.dlocal.com`，生产 `https://api.dlocal.com` |
| 请求头 | `X-Login`、`X-Trans-Key`、`X-Version: 2.1`、`User-Agent`、`Content-Type: application/json`、`X-Date`、`Authorization`；POST 另带 `X-Idempotency-Key`（≤42 字符，7 天） |
| 签名 | `Authorization: V2-HMAC-SHA256, Signature: ` + 小写 hex(HMAC-SHA256(secretKey, xLogin + X-Date + body))；body 为实际发送字节，无 body 时为空串 |
| 响应 | 不签名；成功为业务对象（`status`），失败为 `{code: 数字, message}` + HTTP 4xx |
| 金额 | 主单位 JSON 数字 |

## 2. 引擎能力核对（不需要改代码）

| 需要 | 现状 |
|---|---|
| HMAC-SHA256 hex，密钥为受控句柄 | 已有 `hmacSha256Hex(handle, text)` |
| 对发送字节签名 | 已有 `sealJsonBody(http)`；GET 与无 body 的 POST 用 `requestEncoding=NONE`，签名原文中 body 为空串 |
| X-Date | 已有 `nowRfc3339()`（UTC 毫秒 `Z`），实测被接受 |
| `X-Trans-Key` 明文头 | 受控句柄赋给 Header，由传输层发送时展开（`ChannelRequestBuilder.headerText`） |
| GET、路径变量 | `transport.method` 支持 GET/POST，`pathTemplate` 变量按路径段编码 |
| 金额写成 JSON 数字 | 原子 API `decimalEncoding=NUMBER` |
| 多节点流程、数组按条件取值 | 流程节点与边；数组匹配沿用 Alipay+ 的 RESPONSE 脚本有界 `for` 写法 |

与 Alipay+ 不同，本次不需要发布 Platform 镜像。

## 3. 账号材料（D1）

| 位置 | 键 | 来源 |
|---|---|---|
| 身份 | `channelCode=DLOCAL`、`channelAccountId`、`entityNo=UAT01` | 验证账号身份 |
| `parameters` | `xLogin` | 记录 6 `xLogin`（参与签名原文，也是明文请求头，按参数存） |
| `credentials` | `xTransKey`、`secretKey` | 记录 6 |

**D1：** `xLogin` 作为参数、`xTransKey` 与 `secretKey` 作为凭据。`xLogin` 必须以明文进入签名原文，`hmacSha256Hex` 只对密钥接受句柄，所以不能作为凭据。

## 4. 脚本与原子 API

### 4.1 接入方 `DLOCAL`

出站地址 `DLOCAL_API` = `https://sandbox.dlocal.com`，`sandbox=true`。

### 4.2 公共脚本

| 编码 | 阶段 | 参数 | 行为 |
|---|---|---|---|
| `DLOCAL_SIGN` | REQUEST | `xLogin`、`xTransKey`（PROTECTED_HANDLE）、`secretKey`（PROTECTED_HANDLE）、`idempotencyKey`（可空）、`withBody`（布尔） | `d = nowRfc3339()`；`body = withBody ? sealJsonBody(http) : ''`；写 `X-Login`、`X-Trans-Key`、`X-Version=2.1`、`User-Agent=MoR-Platform`、`X-Date=d`、`Authorization='V2-HMAC-SHA256, Signature: ' + hmacSha256Hex(secretKey, xLogin + d + body)`；`idempotencyKey` 非空时写 `X-Idempotency-Key` |
| `DLOCAL_PICK_REFUND` | RESPONSE | `refundId` | 在 `refunds[]` 中找 `order_refund_id == refundId`，写入 `http.responseBody.refundRecord`（有界 `for`） |

**D2：** 响应不签名，没有 Alipay+ 的 `verified` 标记；状态规则直接按 HTTP 状态与 `status`/`code` 判定，任何未列出的组合落到 ERROR。`User-Agent` 用 `MoR-Platform`（官方要求标识商户应用，不照抄旧系统的 `WEB`）。

### 4.3 原子 API

连接 2000 ms / 响应 10000 ms；脚本 `DLOCAL_SIGN`（POST 带 body 的 `withBody=true`）。

| 原子 API | 方法与路径 | 请求要点 | 状态规则 |
|---|---|---|---|
| `CREATE_PAYMENT` | POST `/payments`，JSON，`decimalEncoding=NUMBER` | `order_id=attemptId`（也作幂等键）；`amount`、`currency`、`country`；`payment_method_flow=REDIRECT`；`payment_method_id=paymentMethodType`（可选）；`payer={}`；`description=subject`；`callback_url=returnUrl`；`notification_url=notifyUrl`（可选） | 200 且 `PENDING` 带 `redirect_url` / `PAID` → SUCCESS；200 且 `REJECTED`/`CANCELLED`/`EXPIRED`、或 4xx 带 `code` → FAIL；其他 → ERROR |
| `GET_ORDER` | GET `/orders/{orderId}`，NONE | 路径变量 | 200 且 `status` 非空 → SUCCESS；404 且 `code=4000` → FAIL；其他 → ERROR |
| `CANCEL_PAYMENT` | POST `/payments/{paymentId}/cancel`，NONE | 无 body，签名 body 为空串，无幂等键 | 200 且 `CANCELLED` → SUCCESS；400 且 `code=5013` → FAIL；其他 → ERROR |
| `CREATE_REFUND` | POST `/refunds`，JSON，NUMBER | `payment_id`（取自 `GET_ORDER`）、`order_refund_id=refundId`（也作幂等键）、`amount`、`currency`、`description=reason`（可选）、`notification_url=notifyUrl`（可选） | 200 且 `PENDING`/`SUCCESS` → SUCCESS；200 且 `REJECTED`/`CANCELLED`、或 4xx 带 `code` → FAIL；其他 → ERROR |
| `GET_PAYMENT_REFUNDS` | GET `/payments/{paymentId}`，NONE；RESPONSE 再跑 `DLOCAL_PICK_REFUND` | 路径变量；脚本参数 `refundId` | 200 且有 `refundRecord` → SUCCESS；200 无匹配 → FAIL（`REFUND_NOT_EXIST`）；其他 → ERROR |

`failureCode` 一律 `str(code)`（数字转字符串，修正旧系统 `ClassCastException`）。

### 4.4 流程

| 标准 API | 节点 | 映射 |
|---|---|---|
| `PAY` | `CREATE_PAYMENT` | `PENDING` → `PENDING` + `nextAction{REDIRECT, redirect_url}`；`PAID` → `SUCCEEDED`；FAIL → `FAILED` |
| `PAY_QUERY` | `GET_ORDER` | `PAID` → `SUCCEEDED`；`REJECTED`/`CANCELLED`/`EXPIRED` → `FAILED`；`PENDING`/`AUTHORIZED` → `PENDING`；FAIL → `NOT_FOUND`；`channelTransactionRef=payment_id`，`amount` 取订单金额 |
| `PAY_CANCEL` | `GET_ORDER` → `CANCEL_PAYMENT` | 撤销成功 → `CLOSED`；查单 `CANCELLED`/`REJECTED`/`EXPIRED` 时不再调用撤销，直接 `CLOSED`；查单 `PAID` 或撤销返回 `5013` → `ALREADY_PAID`；查单 FAIL → `NOT_FOUND` |
| `REFUND` | `GET_ORDER` → `CREATE_REFUND` | `SUCCESS` → `SUCCEEDED`；`PENDING` → `PROCESSING`；FAIL → `FAILED`（查单不存在时 `failureCode=4000`）；`channelRefundRef=id` |
| `REFUND_QUERY` | `GET_ORDER` → `GET_PAYMENT_REFUNDS` | `SUCCESS` → `SUCCEEDED`；`REJECTED`/`CANCELLED` → `FAILED`；其他 → `PROCESSING`；查单 FAIL 或无匹配 → `NOT_FOUND` |

**D3：退款查询沿用已有可选字段 `refund.paymentAttemptId`**（Alipay+ 时加入），在 dLocal 实现中必填，按原单取 `payment_id` 后在 `refunds[]` 中匹配，不改标准 API。备选：给 `REFUND_QUERY` 加可选 `refund.channelRefundRef`，直接 `GET /refunds/{id}`，少一次调用，但需要标准 API 升版且 Payment 必须保存渠道退款号。

**D4：退款与退款查询不输出 `amount`。** 实测 dLocal 退款记录的金额与币种按本地币种返回（USD 0.50 退款返回 BRL 2.53），与请求币种不同；`amount` 在标准 API 中是可选输出，按原币种输出会误导对账。

**D5：PAY_CANCEL 纳入本期。** 实测撤销只对待付款单生效，已付款单返回 `5013`，没有 Alipay+ `cancelPayment` 那样的冲正语义；与 PayBy `PAY_CANCEL` 的输出约定一致（`CLOSED` / `ALREADY_PAID` / `NOT_FOUND`）。

**D6：`payer` 固定发空对象。** 沙箱 BR 托管页可用；生产某些国家或支付方式是否要求预填付款人信息待 dLocal 确认，届时再按 Vault 与 Commerce 消费者数据边界另立需求。

## 5. 验证用例

| 用例 | 步骤 |
|---|---|
| `SBX_PAY_QUERY_NOT_FOUND` | 查询不存在的单 → `FAIL`、`NOT_FOUND`、`4000` |
| `SBX_REFUND_NOT_EXIST` | 对不存在的单退款 → `FAIL`、`FAILED`、`4000` |
| `SBX_REFUND_QUERY_NOT_FOUND` | 退款查询，原单不存在 → `FAIL`、`NOT_FOUND` |
| `SBX_PAY_PENDING_CANCEL` | 下单 USD 1.00 / BR → `PENDING` + REDIRECT；查询 `PENDING`；退款 → `FAIL`（`5002`）；退款查询 → `NOT_FOUND`（`REFUND_NOT_EXIST`）；撤销 → `CLOSED`；再撤销 → `CLOSED`；查询 `FAILED` |
| `SBX_PAID_REFUND` | 下单 USD 1.00 / BR / `PQ`；harness 直连沙箱工具强制 `PAID` 并等待生效；查询 `SUCCEEDED`；撤销 → `ALREADY_PAID`；部分退款 0.50 → `PROCESSING`；harness 强制退款 `SUCCESS`；退款查询 `SUCCEEDED` |

`SBX_PAID_REFUND` 分两段运行（强制状态不经 Platform），与 Alipay+ 的 `--paid-order`/`--paid-refund` 相同。测试单都以终态结束，无需另行清理。

## 6. 阶段建议

| 阶段 | 内容 |
|---|---|
| 1 | 需求、技术方案与沙箱直连探测（本稿） |
| 2 | mor-test 配置（接入方、脚本、原子 API、流程、验证账号）与第 5 节全部用例；PayBy、PayerMax、Alipay+ 回归 |
| 3 | 文档回写（`CURRENT_IMPLEMENTATION.md`）与提交（按授权） |
