# PayBy 接入技术方案

- 日期：2026-10-14
- 状态：**已确认**（2026-10-14）：T2～T7 按建议；`PAY` 改为 DIRECTPAY，所需 Platform 能力交卡直付会话（[补充要求](./card-capability-addendum.md)）
- 需求：[requirement.md](./requirement.md)
- 依据（已读）：对接包 `README.md`、`notes.md`、`channel.json`、`common/runtime.md`、`test-vectors.json`；PayBy 官网 [integration-guide](https://developers.payby.com/docs/integration-guide)、[createorder](https://developers.payby.com/docs/createorder)、[retrieveorderdetail](https://developers.payby.com/docs/retrieveorderdetail)、[cancel](https://developers.payby.com/docs/cancel)、[revoke](https://developers.payby.com/docs/revoke)、[refund](https://developers.payby.com/docs/refund)、[hosted-paypage](https://developers.payby.com/docs/Online%20Payment%20Integration/Checkout/hosted-paypage)；PayerMax 接入/退款 harness 与技术方案；引擎源码 `RegisteredFunctionCatalog`、`ExpressionProfile`、`ExpressionEngine`、`ChannelRequestBuilder`、`HttpWork`。

## 1. 协议要点

| 项 | 内容 |
|---|---|
| 地址 | UAT `https://uat.test2pay.com/sgs/api`；生产 `https://api.payby.com/sgs/api`（本期不用） |
| 请求头 | `Partner-Id`（账号参数）、`Content-Language: en`、`sign`、`Content-Type: application/json` |
| 外层 | `{"requestTime": <毫秒数字>, "bizContent": {...}}`；`requestTime` 与当前时间偏差须在 15 分钟内 |
| 请求签名 | `sign` = Base64(SHA256withRSA(商户私钥 PKCS8, Body 原文 UTF-8)) |
| 响应验签 | 响应头 `sign`，用 PayBy 平台公钥（对接包 `channelPublicKey_bak`，实测）验 Body 原文 |
| 卡字段加密 | DIRECTPAY 的 `cardNo`、`holderName`、`cvv`：RSA/ECB/PKCS1Padding，同一把 PayBy 平台公钥（实测） |
| 结果判定 | `head.applyStatus=SUCCESS` 且 `head.code="0"` 才有 `body`；业务错误码也以 `applyStatus=SUCCESS` 返回且带签名；`applyStatus=ERROR` 为协议级错误，部分带签名 |
| 金额 | `Money{amount, currency}`；请求与响应 `amount` 都按 JSON 数字处理 |

## 2. 引擎能力核对

| 需要 | 现状 | 本期做法 |
|---|---|---|
| 对发送字节签名、RSA 签名/验签 | 已有 `sealJsonBody`、`rsaSha256Sign`、`requireRsaSha256Signature` | 直接使用 |
| 毫秒时间戳 | 只有 `nowRfc3339()`；脚本剖面允许 `long`、`string.substring` 与整数运算 | **T1：** 签名脚本把 `nowRfc3339()` 换算为 Unix 毫秒（Hinnant days-from-civil 算法，整数除法；Python 对 2000～2100 年 20 万个随机时刻核对 0 差异）。`// TODO by CLAUDE` 记为过渡做法；用户 2026-10-14 确认改为注册函数 `nowEpochMillis()`（[补充要求 A4](./card-capability-addendum.md#a4-毫秒时间戳函数-nowepochmillis)），随卡能力发布后替换 |
| 数字金额 | `decimalEncoding=NUMBER` | 全部 6 个原子 API 设为 `NUMBER` |
| Header 映射 | HEADER 字段大小写不敏感 | `Partner-Id` 由节点从账号参数赋值，`Content-Language` 为固定值 |

不改代码、不发镜像。

## 3. 账号材料

| 位置 | 键 | 来源（对接包 `records[0]`，“入款通用”） |
|---|---|---|
| 身份 | `channelCode=PAYBY`、`channelAccountId=PAYBY_PMHK01_01`、`entityNo` | 验证账号身份 |
| `parameters` | `partnerId` | `account.partnerId` |
| `parameters` | `paybyPublicKey` | `account.channelPublicKey_bak`（响应验签，实测） |
| `credentials` | `merchantPrivateKey` | `account.privateKey.decrypted` |

`channelPublicKey`（卡字段加密）本期不用，不录入。验证账号经 mor-test 管理接口录入（沙箱验证已确认的明文存储方式，S2），材料由脚本从仓库外文件读取，不打印、不入库仓库。

## 4. 接入方、脚本与原子 API

### 4.1 接入方 `PAYBY`

出站地址 `PAYBY_SGS` = `https://uat.test2pay.com/sgs/api`，`sandbox=true`。

### 4.2 公共脚本

| 编码 | 阶段 | 参数 | 行为 |
|---|---|---|---|
| `PAYBY_SIGN` | REQUEST | `privateKey`：PROTECTED_HANDLE | 由 `nowRfc3339()` 算出毫秒数，`json.set(http.requestBody, 'requestTime', <long>)`；`http.requestHeaders.sign = rsaSha256Sign(params.privateKey, sealJsonBody(http))` |
| `PAYBY_VERIFY` | RESPONSE | `publicKey`：STRING | `http.responseStatus == 200 && http.responseBody.head.applyStatus == 'SUCCESS'` 时 `requireRsaSha256Signature(publicKey, responseRaw, responseHeaders.sign)` |

**T2：** 只对 `applyStatus=SUCCESS` 验签：实测部分协议级错误不带签名，若一律验签它们会因缺签名变 ERROR，结果相同；按 `applyStatus` 判断能让轨迹里的错误原因保持为渠道原文。所有 SUCCESS/FAIL 判定都要求 `applyStatus=SUCCESS`，因此未验签的响应只会落到 ERROR。

### 4.3 原子 API 共用

POST、`PAYBY_SGS`、连接 2000 ms / 响应 10000 ms、JSON 出入、`decimalEncoding=NUMBER`；`params`：`merchantPrivateKey`（PROTECTED_HANDLE）、`paybyPublicKey`（STRING）；脚本 `PAYBY_SIGN` → `PAYBY_VERIFY`；固定值 `HEADER:Content-Language='en'`。请求字段：`HEADER:Partner-Id`（必填，节点赋值）、`requestTime`（INTEGER，非必填，脚本写入）、`bizContent`（OBJECT，非必填，同 PayerMax 父对象规则）。响应声明 `head.applyStatus`、`head.code`、`head.msg` 与各接口 `body.*` 字段。

`OK` = `http.responseStatus == 200 && head.applyStatus == 'SUCCESS' && head.code == '0'`；`SIGNED(code…)` = `http.responseStatus == 200 && head.applyStatus == 'SUCCESS' && (head.code == …)`。

| 原子 API | 路径 | 请求 `bizContent` | SUCCESS | FAIL | 其他 |
|---|---|---|---|---|---|
| `PLACE_ORDER`（待卡能力） | `/acquire2/placeOrder` | `merchantOrderNo`、`subject`（≤12）、`totalAmount.amount`（DECIMAL）、`totalAmount.currency`、`paySceneCode='DIRECTPAY'`、`paySceneParams.{cardNo,holderName,cvv}`（CARD + 加密）、`expYear/expMonth`（CARD，2 位）、`threeDSecure`、`saveCard`、`platformType`、`realIP`、`redirectUrl`、`notifyUrl`（选） | `OK && status ∈ {CREATED+表单, PAID_SUCCESS, SETTLED}` | `OK && status=='FAILURE'`；`SIGNED(T3 拒绝码)` | ERROR |
| `GET_ORDER` | `/acquire2/getOrder` | `merchantOrderNo` | `OK && status ∈ {CREATED, PAID_SUCCESS, SETTLED, FAILURE}` | `SIGNED('62004')` | ERROR |
| `CANCEL_ORDER` | `/acquire2/cancelOrder` | `merchantOrderNo` | `OK && status=='FAILURE'`；`SIGNED('62002')`（已失败/已关闭） | `SIGNED('62001','62003','62004')` | ERROR |
| `REFUND` | `/acquire2/refund/placeOrder` | `refundMerchantOrderNo`、`originMerchantOrderNo`、`amount.amount`（DECIMAL）、`amount.currency`、`reason`（选，≤64）、`notifyUrl`（选） | `OK && status ∈ {CREATED, REFUNDED_SETTLED, SUCCESS, FAILURE} && orderNo != nil` | `SIGNED(T3 退款拒绝码)` | ERROR |
| `REFUND_QUERY` | `/acquire2/refund/getOrder` | `refundMerchantOrderNo` | 同上状态集合 | `SIGNED('62007')` | ERROR |
| `GET_BALANCE` | `/member/getBalance` | `currencyCode` | `OK && balanceList != nil` | — | ERROR |

3DS 表单取 `body.interActionParams.threeDSecureDom`，为空时再取 `body.interactionParams.threeDSecureDom`（实测大写 A，官网小写），经 `hostChannelForm` 换成我们的 URL。已配置的 5 个原子 API 见 [assets/harness/configure.py](./assets/harness/configure.py)。

**T3 拒绝码（FAIL，渠道明确未受理）：**

- 下单：`601`、`62008`、`62009`、`62012`、`62018`、`62019`、`62020`、`62026`、`62036`、`62037`、`62072`、`62073`、`62083`、`62086`。
- 退款：`601`、`62002`、`62004`、`62006`、`62015`、`62035`、`62038`、`62040`、`62045`、`62083`、`62084`。
- 归 ERROR：`62001`/`62002`/`62003`（下单时同号订单已存在且已付/已失败/已结算）、`62016`、`62017`（同号不同参数）、`400`、`402`、`403`、`404`、`500`、`504`、未知码；调用方用查询确认。

## 5. 标准 API 与流程

初始化统一为 `channelAccount = CHANNEL_ACCOUNT_CONFIG`（必需）。节点 `params`：`merchantPrivateKey` ← `resources.channelAccount.credentials.merchantPrivateKey`，`paybyPublicKey` ← `resources.channelAccount.parameters.paybyPublicKey`；`HEADER:Partner-Id` ← `resources.channelAccount.parameters.partnerId`。每条流程单节点，出口 SUCCESS / FAIL / 默认 ERROR，不接补查或冲正节点。

| 标准 API | 输入映射 | SUCCESS 输出 | FAIL 输出 |
|---|---|---|---|
| `PAY`（DIRECTPAY，待卡能力） | `merchantOrderNo` ← `payment.attemptId`；`subject`；金额/币种；卡字段经 `PAYMENT_CARD`（三项加密）；`redirectUrl` ← `payment.returnUrl`；`platformType=WEB`、`saveCard=false`、`threeDSecure=false`（PayBy 仍可按风控要求 3DS）。`payerReference` 不传（`customerId` 会触发存卡） | `CREATED`+表单 → `PENDING` + `nextAction={REDIRECT, 托管 URL}`；`PAID_SUCCESS/SETTLED` → `SUCCEEDED`；`channelTransactionRef=orderNo` | `paymentStatus=FAILED`、`failureCode`（`failCode`，状态 FAILURE 无码时为 `FAILURE`；拒绝码时为 `head.code`）、`failureMessage` |
| `PAY_QUERY` | `merchantOrderNo` ← `payment.attemptId` | `paymentStatus`：CREATED→`PENDING`，PAID_SUCCESS/SETTLED→`SUCCEEDED`，FAILURE→`FAILED`；`channelTransactionRef`、`channelStatus`、`amount`、`paymentMethodType`（`paymentInfo.payChannel`）、`failureCode/Message` | `paymentStatus=NOT_FOUND` |
| `PAY_CANCEL`（新） | 输入 `channelAccount.*`、`payment.attemptId` | `cancelStatus=CLOSED`、`channelStatus` | 62001/62003 → `cancelStatus=ALREADY_PAID`；62004 → `NOT_FOUND`；`failureCode/Message` |
| `REFUND` | `refundMerchantOrderNo` ← `refund.refundId`；`originMerchantOrderNo` ← `refund.paymentAttemptId`；金额/币种；`reason`、`notifyUrl` | `refundStatus`：CREATED→`PENDING`，REFUNDED_SETTLED/SUCCESS→`SUCCEEDED`，FAILURE→`FAILED`；`channelRefundRef=orderNo`、`channelStatus` | `refundStatus=FAILED` |
| `REFUND_QUERY` | `refundMerchantOrderNo` ← `refund.refundId` | 同上 + `amount` | `refundStatus=NOT_FOUND` |
| `BALANCE_QUERY`（新） | 输入 `channelAccount.*`、`currency` | `available.value`、`available.currency`（取 `balanceList` 中币种等于输入的第一项；没有时流程走 FAIL） | `failureCode=CURRENCY_NOT_FOUND` |

**T4：** `PAY_QUERY` 把 FAILURE 映射为 `FAILED`（PayBy 的 FAILURE 含失败、取消、过期，渠道不区分），`revoked=true` 不单独表达。
**T5：** `PAY_CANCEL` 对重复关单（62002）返回 `SUCCESS/CLOSED`，保证重试幂等；它只在订单尚未支付时生效，不是冲正。`revokeOrder` 不接入。
**T6：** `BALANCE_QUERY` 只作渠道余额查询的标准 API 草案，没有业务消费者；Finance 未来接入时可调整。

## 6. 验证用例（mor-test 渠道沙箱验证，接入方 `PAYBY`、账号 `UAT_MAIN`）

| 用例 | 步骤 | 期望 |
|---|---|---|
| `UAT_PAY_QUERY_NOT_FOUND` | `PAY_QUERY` 随机单号 | FAIL，`NOT_FOUND` |
| `UAT_UNPAID_QUERY_CANCEL` | 种子 CREATED 订单（直连 UAT 以 PAYPAGE 创建，前缀 `MORSEED`）：`PAY_QUERY` → `REFUND` → `PAY_CANCEL` ×2 → `PAY_QUERY` | PENDING → FAIL `62015` → CLOSED → CLOSED → FAILED |
| `UAT_CANCEL_NOT_FOUND` | `PAY_CANCEL` 随机单号 | FAIL，`NOT_FOUND` |
| `UAT_REFUND_NOT_EXIST` | `REFUND` 随机原单号 | FAIL，`refundStatus=FAILED`、`failureCode=62004` |
| `UAT_REFUND_QUERY_NOT_FOUND` | `REFUND_QUERY` 随机退款号 | FAIL，`NOT_FOUND` |
| `UAT_BALANCE_AED` | `BALANCE_QUERY` AED | SUCCESS，`available.value` 存在 |
| 卡能力发布后 | 见[补充要求第 3 节](./card-capability-addendum.md#3-payby-会话在能力可用后的工作)：DIRECTPAY 测试卡成功/失败/待定、托管 3DS 跳转、付款后部分退款与退款查询 | — |

## 7. 配置方式与证据

- harness 放在 [assets/harness](./assets/harness/)：`configure.py` 复用 PayerMax harness 的 `common.py`（登录、调用、运行目录），经 mor-test `http://localhost:28080` 的真实管理接口保存（存在则按版本修改）；`uat_cases.py` 录入验证账号、保存并运行用例。
- 管理员登录信息只从不提交的 `test-environment/private/access.md` 读入 scratchpad 0700 运行目录；UAT 材料从仓库外对接包读取，进程内使用，不打印。
- 回执 `assets/test-uat-receipt.json` 只含用例、`executeStatus`、归一化状态、错误码与验签结论，不含密钥与 `tokenUrl`。

## 8. 风险

- **时间换算脚本**：依赖 `nowRfc3339` 格式稳定；格式变化会导致 `REQUESTTIME_*` 错误并归 ERROR，可立即发现。
- **验签公钥**：以实测 `channelPublicKey_bak` 为准；若 PayBy 轮换公钥，只需更新验证账号/Vault 参数。
- **金额小数位**：PayBy 响应金额不补小数位（`1.00` 返回 `1.0`），标准 API 输出原样为 `"1.0"`；下游必须按数值比较。
- **subject ≤ 12 字符**：标准 API 不截断，超长由 PayBy 拒绝（`400`，归 ERROR）；Payment 接入时需约束。
- **日志**：内部执行接口沿用完整 Body 日志（W5 已知遗留），`tokenUrl` 会出现在 Platform 日志中，私钥不会。

## 9. 评审点

| 编号 | 问题 | 建议 |
|---|---|---|
| T1 | 毫秒时间戳 | 已由用户选择：脚本换算，标 TODO，后续改注册函数 |
| T2 | 验签范围 | 只对 `applyStatus=SUCCESS` 的 HTTP 200 响应验签；验签公钥用 `channelPublicKey_bak` |
| T3 | 拒绝码划分 | 按第 4.3 节；同号已存在类归 ERROR |
| T4 | 查询状态映射 | FAILURE → `FAILED` |
| T5 | 关单 | 新增 `PAY_CANCEL`，重复关单幂等为 CLOSED；不接 `revokeOrder` |
| T6 | 余额 | 新增 `BALANCE_QUERY` 草案 |
| T7 | 执行授权 | 授权经 mor-test 管理接口写入上述配置、录入 UAT 验证账号并运行 UAT 用例；不含代码、镜像、SQL、提交与推送 |
