# MPGS（Network International）接入：技术方案

- 状态：第 1～6 节已确认并在 mor-test 实施（2026-10-21）；第 7 节为 2026-10-22 追加的预授权与请款，设计已确认并在 mor-test 实施
- 需求：[requirement.md](./requirement.md)

## 1. 现状差异与影响（阶段 2）

### 1.1 需求项差异

| 需求项 | 当前行为及证据 | 目标 | 缺口 | 处理建议 |
|---|---|---|---|---|
| PUT / GET、路径变量 | 原子 API 支持 GET/POST/PUT/PATCH/DELETE（`IntegrationApiDefinitionValidator` 第 68 行）；`pathTemplate` 的 `{var}` 由 PATH 字段填充并逐段编码（`ChannelRequestBuilder` 第 201–222 行） | MPGS 四个接口 | 无 | 配置 |
| Basic 认证 | `basicAuth(username, password)` 两个参数都必须是受保护句柄（`ScriptFunctions` 第 150–163 行），不能在脚本里拼 `merchant.` 前缀 | `merchant.<merchantId>:<apiPassword>` | 无（配置方式规避） | 验证账号 / 渠道账号凭据另存 `apiUser = "merchant.<merchantId>"`；`merchantId` 作为参数用于路径 |
| 金额 | 原子 API `decimalEncoding: NUMBER`；`MONEY_MAJOR_UNITS` 按币种精度补齐 | JSON 数字，小数位等于币种精度 | 无 | 配置（与 dLocal 相同的两步换算） |
| 卡字段 | `cardFields` + `PAYMENT_CARD` 初始化函数；沙箱验证改读验证账号 `testCard.*`（`VerifyPaymentCardFunction`），不调用 card | PAN、有效期（两位年）、CVV、持卡人 | 无 | 配置 |
| `PAY` 标准 API 的卡输入 | mor-test 现行 `PAY` 为托管收银台版本，没有 `card.token`、`payment.clientIp`、`payment.userAgent`（统一出口发布后快照）；卡直付版本只在本机做过（PayerMax 卡直付 T4，mor-test 未改）。`PAYMENT_CARD` 的 `paymentInstrumentToken` 参数必须绑定到输入路径 | MPGS `PAY` 流程从输入取卡句柄 | **标准 API 缺字段** | 见决定 D3：给 mor-test `PAY` 增加三个**可选**请求字段。修改时系统用新字段复核全部实现，会使任一实现失效即拒绝（`UpdateStandardApiProcessor.requireImplementationsIntact`）；只加可选字段不影响 PayerMax、PayBy、Alipay+、dLocal、TossPay 的流程。同时消除 Payment 已发送卡字段但 mor-test 标准 API 不认的不一致 |
| 状态判定 | 状态规则 + 返回出口配置 | `result=SUCCESS` + `gatewayCode=APPROVED` + `order.status=CAPTURED` 成功；退款 `REFUNDED/PARTIALLY_REFUNDED`；4xx `INVALID_REQUEST` 找不到订单为 `NOT_FOUND` | 无 | 配置（阶段 3 按沙箱实测细化） |
| 沙箱商户 | 对接包 USD 商户凭据失效（实测 401） | 平台起步币种 USD | **无可用 USD 商户** | 见决定 D1 |
| 3DS、Webhook、Payment 退款 / 查询 | 无入站入口、Payment 只调 PAY、认证动作 TODO | — | 平台级缺口 | 本期不做（需求第 4 节） |

### 1.2 系统影响

| 系统 | 影响点 | 改代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|
| channel-connector（mor-test 运行配置） | 新接入方 `I_MPGS_NI`、脚本、原子 API、4 个流程、验证账号与用例；`PAY` 标准 API 增加 3 个可选字段 | **只改运行配置**，经 Nginx → Gateway → ops BFF → `/external-api-admin/v1/**` 写入；不改代码、不改 SQL、不部署镜像 | 标准 API 修改持有全模块配置锁；其他渠道流程由服务端复核 |
| channel-connector 业务五表（渠道 / 账号 / MID / 能力） | 已有渠道只有 `DEMO_NI`（对账演示会话所有）与草稿 `AAAA`；PayerMax 等 5 家均未建渠道与 MID | 见决定 D2，建议不建 | 若建 MID，AE001 的卡候选查询会多出真实渠道，影响其他会话的演示 |
| Payment、card、Gateway、ops BFF / Web | 无 | 否 | — |
| 仓库 | 需求目录、配置脚本（harness，凭据从对接包读取）、回执、`CURRENT_IMPLEMENTATION.md` | 文档与脚本 | 凭据不入库 |

### 1.3 阶段调整建议

本需求只改运行配置，与 dLocal 接入相同。建议把阶段 4～7 合并为一个阶段，“在 mor-test 写配置并跑沙箱用例”。共 4 个阶段：

1. 需求评审（已确认）。
2. 现状差异分析（本节）。
3. 接入设计：先直连沙箱写探测，再出字段映射与用例清单。
4. 配置并验证：mor-test 写配置、跑沙箱用例、回归其他渠道。
5. 回写台账并提交（不推送）。

## 2. 沙箱写探测结果（阶段 3，2026-10-21）

本机 Python 直连 `https://test-gateway.mastercard.com`，API v100，商户 `JOLLYMAX_AED`（`MPGS_TEST200900000049`）。测试卡为 MPGS 公布的 Mastercard `5123450000000008`、CVV `100`。单号前缀 `PTTEST`，脚本放在会话 scratchpad，凭据只在进程内读取。

| 探测 | 请求 | 结果 |
|---|---|---|
| 支付批准 | `PUT order/A/transaction/A`，`PAY`，有效期 01/39，AED 10.00 | `201`，`result=SUCCESS`，`response.gatewayCode=APPROVED`，`order.status=CAPTURED`，`transaction.type=PAYMENT`，`transaction.receipt` 为 12 位 RRN；`order.authenticationStatus=AUTHENTICATION_NOT_IN_EFFECT` |
| 支付被拒 | 同上，有效期 05/39 | `201`，`result=FAILURE`，`gatewayCode=DECLINED`，`acquirerCode=05`（Do not honour），`order.status=FAILED`，交易已建、有 receipt |
| 查询 | `GET` 上面两笔 | `200`，结构与下单响应相同 |
| 部分退款 | `PUT order/A/transaction/AR1`，`REFUND`，AED 4.00 | `201`，`result=SUCCESS`，`gatewayCode=APPROVED`，`transaction.type=REFUND`，`order.status=PARTIALLY_REFUNDED`，`order.totalRefundedAmount=4.00` |
| 退款查询 | `GET order/A/transaction/AR1` | `200`，同上 |
| 原单不存在的退款 | `PUT`，order 不存在 | `400 {"error":{"cause":"INVALID_REQUEST","explanation":"… An order with this ID does not exist.","field":"orderid"},"result":"ERROR"}` |
| 交易不存在的查询 | `GET order/A/transaction/AR9` | `400 {"error":{"cause":"INVALID_REQUEST","explanation":"Unable to find order … and transaction …"},"result":"ERROR"}` |
| AED 商户收 USD | `PAY`，USD 1.00 | `201`，`SUCCESS`，`APPROVED`，`CAPTURED` |

**结论：**
- 测试商户不强制 3DS。
- 支付和退款都同步返回终态。
- AED 商户在沙箱里接受 USD，所以用例仍可用平台起步币种 USD。生产环境的商户币种以 NI 开通为准。
- 响应耗时 1.3～1.6 秒。

8 笔沙箱交易都已是终态，不需要清理：

| 单号 | 状态 |
|---|---|
| `PTTEST1021161128A` | 部分退款 |
| `PTTEST1021161128B` | 被拒 |
| `PTTEST1021161246U` | 已扣款 USD 1.00 |

## 3. 设计决定

| # | 决定 | 依据 |
|---|---|---|
| M1 | 接入方编码 `I_MPGS_NI`；出站端点 `MPGS_API` = `https://test-gateway.mastercard.com`，标记为沙箱；API 版本 `100` 写在路径模板中 | 对接包；第 2 节探测 |
| M2 | 订单号 = 交易号 = `payment.attemptId`；退款交易号 = `refund.refundId`，订单号 = `refund.paymentAttemptId`。不需要像旧系统那样借 addField1 传递 | 旧系统规则；标准 API 已带这两个标识 |
| M3 | 认证脚本 `MPGS_BASIC_AUTH` 对两个受保护凭据做 `basicAuth(params.apiUser, params.apiPassword)`；账号材料为参数 `merchantId`（填路径）、凭据 `apiUser`（`merchant.<merchantId>`）和 `apiPassword`。凭据不进日志（修正旧系统打印密码） | 第 1.1 节 |
| M4 | 金额以 `decimalEncoding: NUMBER` 原样发送：Payment 已按币种精度补齐（`PayActionActor.amountText`），验证用例也按精度写。响应金额用 `MONEY_MINOR_UNITS` → `MONEY_MAJOR_UNITS` 两步补齐后输出 | dLocal 做法 |
| M5 | 卡字段：`PAYMENT_CARD` 的 `pan`、`expiryMonth`、`expiryYearShort`、`cvv`、`cardholderName` 填入 `sourceOfFunds.provided.card.*`，年份统一为 2 位 | 修正旧系统年份格式不一 |
| M6 | 状态取交易层字段：`result` + `response.gatewayCode` + `transaction.type`，不再要求 `order.status=CAPTURED`。旧规则在原单部分退款后会把支付查询判成处理中 | 第 2 节：退款后 `order.status=PARTIALLY_REFUNDED` |
| M7 | `channelTransactionRef` 和 `channelRefundRef` 取 `transaction.receipt`（收单 RRN），缺失时取 `transaction.id`；`channelStatus` 取 `response.gatewayCode`，4xx 时取 `error.cause` | 对账可用 RRN 关联 |
| M8 | 失败码：已建交易被拒时，`failureCode` 取 `response.gatewayCode`（如 `DECLINED`、`EXPIRED_CARD`、`INSUFFICIENT_FUNDS`），`failureMessage` 为 `acquirerCode + ' ' + acquirerMessage`；请求被拒（4xx）时，`failureCode` 取 `error.cause`，`failureMessage` 取 `error.explanation` | 网关码稳定、与收单行无关；旧系统优先 acquirerCode |
| M9 | 不实现 `PAY_CANCEL`：卡支付同步扣款，没有待付款状态可撤销；冲正走退款 | 已有渠道也未全部实现 |
| M10 | mor-test 标准 API `PAY` 只新增可选请求字段：`card` 对象及 `card.token`、`payment.clientIp`、`payment.userAgent`，其余字段与现行定义逐项保持不变。保存前在 mor-test 上读取现行定义，脚本在其后追加；服务端复核其他接入方流程 | 阶段 2 决定 D3 |

## 4. 配置清单（mor-test，经 `/external-api-admin/v1/**`）

### 4.1 原子 API

所有原子 API 共用以下设置：

- 传输：`HTTP`，端点 `MPGS_API`，路径模板 `/api/rest/version/100/merchant/{merchantId}/order/{orderId}/transaction/{transactionId}`，三段都是 PATH 字段。
- 超时：连接 2 秒，响应 10 秒。
- 编码：请求和响应都是 JSON；请求脚本 `MPGS_BASIC_AUTH`。

| 编码 | 方法 | 请求体 | SUCCESS（节点） | FAIL（节点） | 其余 |
|---|---|---|---|---|---|
| `PAY` | PUT | `apiOperation=PAY`（固定值）、`sourceOfFunds.type=CARD`（固定值）、`sourceOfFunds.provided.card.{number,expiry.month,expiry.year,securityCode,nameOnCard}`（`cardFields`）、`order.amount`（DECIMAL）、`order.currency`、`order.reference`（可选）、`device.ipAddress`、`device.browser`（可选） | HTTP 200/201 且 `result` ∈ {`SUCCESS`, `FAILURE`, `PENDING`} 且 `transaction.type == 'PAYMENT'` | HTTP 400 且 `result=='ERROR'` 且 `error.cause` ∈ {`INVALID_REQUEST`, `REQUEST_REJECTED`}；HTTP 401 | ERROR：超时、5xx、`SERVER_BUSY`/`SERVER_FAILED`，以及无法识别的结构 |
| `RETRIEVE` | GET | 无 | HTTP 200 且 `transaction.type` 存在 | HTTP 400 且 `error.cause=='INVALID_REQUEST'`（找不到订单或交易） | ERROR |
| `REFUND` | PUT | `apiOperation=REFUND`、`transaction.amount`（DECIMAL）、`transaction.currency` | HTTP 200/201 且 `result` ∈ {`SUCCESS`, `FAILURE`, `PENDING`} 且 `transaction.type == 'REFUND'` | 同 `PAY` | ERROR |

节点输出包括：`result`、`gatewayCode`、`acquirerCode`、`acquirerMessage`、`transactionType`、`receipt`、`transactionId`，金额最小单位与币种（`RETRIEVE` 输出），以及 FAIL 时的 `errorCode`（`error.cause`）和 `errorMessage`（`error.explanation`）。

### 4.2 流程与返回

| 标准 API | 节点 | 返回 |
|---|---|---|
| `PAY` | 初始化：`CHANNEL_ACCOUNT_CONFIG` + `PAYMENT_CARD`（`input.card.token`、`input.payment.attemptId`）→ `PAY` | `SUCCESS`+`APPROVED` → SUCCESS/`SUCCEEDED`；`FAILURE` → SUCCESS/`FAILED`（带渠道引用与 M8 失败码）；`PENDING` → SUCCESS/`PENDING`；节点 FAIL → FAIL/`FAILED`；其余 → ERROR |
| `PAY_QUERY` | `RETRIEVE`（order = transaction = attemptId） | `transactionType=='PAYMENT'` 时：`SUCCESS`+`APPROVED` → `SUCCEEDED`，`FAILURE` → `FAILED`，`PENDING` → `PENDING`，均为 SUCCESS 并带 `amount`；节点 FAIL → FAIL/`NOT_FOUND` |
| `REFUND` | `REFUND`（order = paymentAttemptId，transaction = refundId） | `SUCCESS`+`APPROVED` → SUCCESS/`SUCCEEDED`；`PENDING` → SUCCESS/`PROCESSING`；`FAILURE` → FAIL/`FAILED`；节点 FAIL → FAIL/`FAILED` |
| `REFUND_QUERY` | `RETRIEVE`（order = paymentAttemptId，transaction = refundId） | `transactionType=='REFUND'` 时：`SUCCESS`+`APPROVED` → `SUCCEEDED`，`FAILURE` → `FAILED`，`PENDING` → `PROCESSING`；不是退款交易或节点 FAIL → FAIL/`NOT_FOUND` |

`REFUND_QUERY` 的 `paymentAttemptId` 在标准 API 中是可选字段，但 MPGS 必须有它；缺失时请求无法构建，按引擎规则返回 NOT_SENT。

## 5. 沙箱验证

**验证账号**（材料从仓库外对接包读取；测试卡为 MPGS 公布的测试卡，有效期决定沙箱结果）：

| 账号 | 商户 | 测试卡 |
|---|---|---|
| `SBX_APPROVED` | `JOLLYMAX_AED` | 5123450000000008，01/39，CVV 100 |
| `SBX_DECLINED` | `JOLLYMAX_AED` | 5123450000000008，05/39，CVV 100 |

**用例**（单号统一为 `PTTEST` + 随机数字，金额 USD，`card.token` 为占位值，沙箱运行不读 card）：

| 用例 | 账号 | 步骤与期望 |
|---|---|---|
| `SBX_PAY_REFUND_CHAIN` | APPROVED | `PAY` 10.00 → SUCCESS/`SUCCEEDED`、有渠道引用；`PAY_QUERY` → `SUCCEEDED`、`amount=10.00 USD`；`REFUND` 4.00 → `SUCCEEDED`；`REFUND_QUERY` → `SUCCEEDED`；再次 `PAY_QUERY` → 仍为 `SUCCEEDED`（M6） |
| `SBX_PAY_DECLINED` | DECLINED | `PAY` → SUCCESS/`FAILED`，`failureCode=DECLINED`；`PAY_QUERY` → `FAILED` |
| `SBX_PAY_QUERY_NOT_FOUND` | APPROVED | `PAY_QUERY` 查不存在的单 → FAIL/`NOT_FOUND`，`failureCode=INVALID_REQUEST` |
| `SBX_REFUND_NOT_EXIST` | APPROVED | 对不存在的原单退款 → FAIL/`FAILED`，`failureCode=INVALID_REQUEST` |
| `SBX_REFUND_QUERY_NOT_FOUND` | APPROVED | 查询不存在的退款 → FAIL/`NOT_FOUND` |

**报文核对（AC3）：** 用 `test-vectors.json` 的 `auth.basicHeader`、`paymentApply.v100.card.request`、`refund.v100.request` 核对 Authorization、URL 与请求体。设计差异：本方案年份为 2 位，并带 `order.reference` 与 `device`。核对方法是在验证运行轨迹中比对遮蔽后的请求体和 URL。

**回归（AC5）：** PayerMax、PayBy、dLocal、Alipay+、TossPay 的沙箱或 UAT 用例中，不需要付款的部分重跑一遍。

## 6. 实施步骤（阶段 4）

1. `configure.py`（本需求 `assets/harness/`）通过 Nginx → Gateway → ops BFF → channel-connector 管理接口写入：
   - 读取 mor-test 现行 `PAY` 标准 API，追加 M10 的可选字段，按版本修改，服务端会复核其他流程；
   - 接入方 `I_MPGS_NI`、脚本、3 个原子 API；
   - 4 条流程，每条先调校验接口再保存。
2. `sandbox_cases.py` 录入两个验证账号，运行第 5 节的用例，生成回执（不含凭据、卡号、RRN）。
3. 回归其他渠道；对日志和回执做凭据与卡号的值比对检索。
4. 不改代码、SQL、镜像与 Nacos；不建渠道、账号、MID（D2）。

**恢复：** 标准 API `PAY` 可按版本改回原字段，前提是 MPGS 的 `PAY` 流程先删除。接入方及其子对象可以逐个删除。不涉及数据库与镜像。

## 7. 追加：预授权与请款（2026-10-22）

### 7.1 沙箱探测

本机直连沙箱，条件与第 2 节相同：商户 `JOLLYMAX_AED`，测试卡 5123450000000008，币种 USD，单号前缀 `PTTEST`，凭据只在进程内读取。

| 探测 | 结果 |
|---|---|
| 预授权，有效期 01/39，10.00 | `201`，`SUCCESS`/`APPROVED`，`transaction.type=AUTHORIZATION`，`order.status=AUTHORIZED`，已授权 10.00、已请款 0，有 RRN |
| 预授权，有效期 05/39 | `201`，`FAILURE`/`DECLINED`，收单码 `05`，`order.status=FAILED` |
| 查询预授权 | `200`，结构同上 |
| 部分请款 6.00（交易号 C1） | `201`，`SUCCESS`/`APPROVED`，`transaction.type=CAPTURE`，`order.status=PARTIALLY_CAPTURED`，已请款 6.00 |
| 请款后查询预授权交易 | 交易仍是 `AUTHORIZATION`/`APPROVED`，`order.status=PARTIALLY_CAPTURED` |
| 超额请款 5.00 | `400 INVALID_REQUEST`，“Requested capture amount exceeds outstanding authorized amount” |
| 余额请款 4.00 | `201`，`SUCCESS`，`order.status=CAPTURED`，已请款 10.00 |
| 对被拒的预授权请款 | `400 INVALID_REQUEST`，“The order does not have a successful Authorization that can be captured.” |
| 对不存在的订单请款 | `400 INVALID_REQUEST`，“An order with this ID does not exist.” |
| **重发 C1**（订单上已有更晚的交易） | `400 INVALID_REQUEST`，“Transaction already exists. Retry not supported as it is not the last transaction on the order.” |

**结论：**
- 预授权与 PAY 用同一个请求体，只是 `apiOperation` 不同；请款与退款结构相同。都同步返回终态，约 1.5～2.3 秒。
- 支持部分请款和多次请款，超出未请款余额时由渠道拒绝。
- **重发风险：** 同一交易号只有在它仍是订单最后一笔交易时才能重发。否则渠道返回 400，按现有映射会把一笔已成功的请款报成失败。处理见 A4。

新增沙箱交易：`PTTEST1022151501H`（已全额请款 10.00）、`PTTEST1022151501D`（预授权被拒），都是终态。

### 7.2 设计决定

| # | 决定 | 依据 |
|---|---|---|
| A1 | 标准 API `AUTHORIZE` 的请求字段与 `PAY` v3 逐项相同（22 个），Payment 可用同一套请求组装；响应字段为 `authorizationStatus`（必填：`SUCCEEDED` / `FAILED` / `PENDING`）、`nextAction`（预留 3DS）、`channelTransactionRef`、`channelStatus`、`failureCode`、`failureMessage` | 与 PAY 对称；Payment `AUTHORIZE` 动作只记授权结果 |
| A2 | 标准 API `CAPTURE` 请求：`channelAccount.*`（同其他 API）、`capture.captureId`、`capture.paymentAttemptId`、`capture.amount.value`、`capture.amount.currency`（均必填）。响应：`captureStatus`（必填：`SUCCEEDED` / `FAILED` / `PROCESSING`）、`channelCaptureRef`、`channelStatus`、`failureCode`、`failureMessage` | 与 `REFUND` 对称；请款按原 Attempt 定位授权 |
| A3 | 本次不加预授权、请款的查询标准 API，也不加 `VOID` | 用户只要求这两个；Payment 尚未调用 |
| A4 | 两条流程在渠道请求被拒（FAIL）时，**再查一次本笔交易**：查到同类型交易（`AUTHORIZATION` / `CAPTURE`）就按查到的结果返回；查不到就返回原来的拒绝原因。这样重发不会把已成功的交易报成失败 | 7.1 重发探测；标准 API 决定允许“按渠道合同有条件地查询原操作”，不涉及冲正 |
| A5 | 状态判定沿用 M6～M8：取 `result` + `gatewayCode` + `transaction.type`，失败码与说明同 PAY / REFUND | 7.1 |

**发现但不在本次处理：** 已上线的 MPGS `REFUND` 流程也有同样的重发风险（同一订单上做过第二笔退款后重发第一笔）。目前 Payment 不调用 REFUND，建议以后接入 Payment 时一并按 A4 加上回查。

### 7.3 配置

- **新增原子 API：**
  - `AUTHORIZE`：与原子 API `PAY` 相同，固定值改为 `apiOperation=AUTHORIZE`；成功条件中的 `transaction.type` 改为 `AUTHORIZATION`。
  - `CAPTURE`：与原子 API `REFUND` 相同，固定值改为 `apiOperation=CAPTURE`；成功条件中的 `transaction.type` 改为 `CAPTURE`。
  - 查询复用原子 API `RETRIEVE`。

| 标准 API | 节点 | 返回 |
|---|---|---|
| `AUTHORIZE` | 初始化同 PAY（账号配置 + 卡）→ `authorize`（order = transaction = attemptId）；被拒时 → `retrieve`（同一单号） | `SUCCESS`+`APPROVED` → SUCCESS/`SUCCEEDED`；`FAILURE` → SUCCESS/`FAILED`（带渠道引用与失败码）；`PENDING` → SUCCESS/`PENDING`；被拒且回查不到预授权 → FAIL/`FAILED`（原拒绝原因）；其余 → ERROR |
| `CAPTURE` | `capture`（order = paymentAttemptId，transaction = captureId）；被拒时 → `retrieve`（同一交易号） | `SUCCESS`+`APPROVED` → SUCCESS/`SUCCEEDED`；`PENDING` → SUCCESS/`PROCESSING`；`FAILURE` → FAIL/`FAILED`；被拒且回查不到请款 → FAIL/`FAILED`（原拒绝原因，如超额、无可请款授权）；其余 → ERROR |

标准 API 只新增、不修改，已有 7 个标准 API 和其他接入方不受影响。

### 7.4 沙箱用例

验证账号沿用 `SBX_APPROVED` 和 `SBX_DECLINED`，币种 USD。

| 用例 | 账号 | 步骤与期望 |
|---|---|---|
| `SBX_AUTH_CAPTURE_CHAIN` | APPROVED | `AUTHORIZE` 10.00 → `SUCCEEDED`；`CAPTURE` 6.00 → `SUCCEEDED`；`CAPTURE` 5.00 → FAIL/`FAILED`、`INVALID_REQUEST`（超额）；`CAPTURE` 4.00 → `SUCCEEDED`；用第一笔请款号重发 6.00 → `SUCCEEDED`（A4 回查）；`REFUND` 2.00 → `SUCCEEDED` |
| `SBX_AUTH_DECLINED` | DECLINED | `AUTHORIZE` → SUCCESS/`FAILED`、`DECLINED`；`CAPTURE` → FAIL/`FAILED`、`INVALID_REQUEST` |
| `SBX_CAPTURE_NOT_EXIST` | APPROVED | 对不存在的订单请款 → FAIL/`FAILED`、`INVALID_REQUEST` |

完成后重跑第 5 节的 5 个 MPGS 用例和其他渠道的 8 个回归用例，并按 AC4 的方法检查凭据和卡号。

### 7.5 实施与恢复

- **实施：** 扩展 `configure.py` 和 `sandbox_cases.py`，经管理接口创建两个标准 API、两个原子 API 和两条流程，录入并运行 3 个用例。不改代码、SQL、镜像与 Nacos。
- **渠道回查的局限：** 回查本身出错（超时、5xx、401）时返回 ERROR，不判成失败。原因是被拒的可能是一笔已成功交易的重发。
- **恢复：** 先删两条流程，再删原子 API 和标准 API。不影响已有配置。
