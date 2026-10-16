# Alipay+ 接入技术方案

- 日期：2026-10-16
- 状态：**已确认**（2026-10-16）：T1～T7 按建议，T1 改代码、由本会话实现
- 需求：[requirement.md](./requirement.md)
- 依据（已读）：对接包 `README.md`、`notes.md`、`channel.json`、`test-vectors.json`（凭据只按结构读取）；Alipay+ 官方 [cancelPayment](https://docs.alipayplus.com/alipayplus/alipayplus/api_acq_ns/cancel_payment)；引擎源码 `ScriptFunctions`、`RegisteredFunctionCatalog`、`ExpressionProfile`、`ScriptRuntime`、`HttpWork`；mor-test 现行标准 API `PAY`、`PAY_QUERY`、`REFUND`、`REFUND_QUERY`（均为版本 1）；PayBy 接入方案与 harness。

## 1. 协议要点

| 项 | 内容 |
|---|---|
| 地址 | `https://open-sea.alipayplus.com`，路径前缀 `/aps/api/v1`；沙箱用 `SANDBOX_` Client-Id |
| 请求头 | `Content-Type: application/json`、`Client-Id`、`Request-Time`、`Signature` |
| 请求签名 | `Signature: algorithm=RSA256, keyVersion=0, signature=` + URL 编码(Base64(SHA256withRSA(我方私钥, `POST {path}\n{clientId}.{Request-Time}.{body}`))) |
| 响应验签 | Alipay+ 公钥验 `POST {path}\n{响应头 client-id}.{响应头 response-time}.{响应原文}`；签名取 `signature` 头中 `signature=` 之后，URL 解码再 Base64 解码 |
| 结果 | `result.resultStatus`：S 成功 / F 失败 / U 未知；查询另有 `paymentResult`，退款查询看 `transactions[]` |
| 金额 | `{currency, value}`，`value` 为最小货币单位的字符串 |

## 2. 引擎能力核对

| 需要 | 现状 | 做法 |
|---|---|---|
| 对发送字节签名、RSA 签名/验签 | 已有 `sealJsonBody`、`rsaSha256Sign`、`requireRsaSha256Signature` | 直接使用；路径作为脚本参数按原子 API 绑定常量 |
| Request-Time | 已有 `nowRfc3339()`（UTC 毫秒 `Z`） | 实测被接受，直接使用 |
| 签名值 URL 编码 | **无** | **T1** |
| 响应签名 URL 解码 | **无** | **T1** |
| 请求金额转最小单位 | 已有 `MONEY_MINOR_UNITS`（返回整数） | `str(...)` 转字符串 |
| 响应金额最小单位转主单位 | **无** | **T1** |
| 读带连字符的响应头 | Header 映射大小写不敏感，`seq.get` 已开放 | 直接使用 |

### T1 引擎补 3 个函数（建议改代码）

| 函数 | 类别 | 行为 |
|---|---|---|
| `urlEncode(text)` | SCRIPT | RFC 3986：除 `A-Za-z0-9-._~` 外按 UTF-8 百分号编码 |
| `urlDecode(text)` | SCRIPT | 百分号解码；`+` 保持原样（不转空格，避开旧系统缺陷）；非法序列 → 节点 ERROR |
| `MONEY_MAJOR_UNITS(minor, currency)` | PURE | `MONEY_MINOR_UNITS` 的反向：按币种小数位把最小单位整数转为主单位十进制字符串；非整数或未知币种 → ERROR |

改动点：`ScriptFunctions`、`MoneyMinorUnitsFunction` 同目录新增函数、`RegisteredFunctionCatalog` 登记，`auto` 测试按入口覆盖。需要发布 Platform 镜像到 mor-test（另行授权）。

**备选（不改代码）：** Base64 只有 `+`、`/`、`=` 三个需编码的字符，可在脚本里用有界 `for` 和 `string.substring` 逐字符替换，解码同理；金额输出可以先不输出 `amount`（标准 API 中为可选）。缺点是两个脚本各多 10 行左右的字符处理，与 PayBy 时用户对“脚本里写换算”的意见相同，因此建议改代码。

**代码归属待用户定：** 卡直付会话已登记要改同一批文件（PayBy 补充要求 A1～A4，目前工作区里这些文件没有未提交改动）。可由本会话实现这 3 个函数，或并入卡直付会话的那一批。

## 3. 标准 API 调整

### T2 `PAY` 新增可选 `payment.paymentMethodType`

Alipay+ 收银台下单必须指定钱包类型。在 `PAY` 请求中新增可选 STRING 字段 `payment.paymentMethodType`（`PAY_QUERY` 响应已有同名字段）；Alipay+ 实现中为必填，值按 Alipay+ 钱包编码（`ALIPAY_CN`、`ALIPAY_HK`、`GCASH`、`TRUEMONEY` 等）透传。Payment 支付方式目录与钱包编码的对应关系等 Payment 接入时再定。PayerMax 实现不读这个字段，不受影响。

### T3 `REFUND_QUERY` 新增可选 `refund.paymentAttemptId`

Alipay+ 没有按退款单号查询的接口，只能按原支付单号查。在 `REFUND_QUERY` 请求中新增可选 STRING 字段 `refund.paymentAttemptId`；Alipay+ 实现中为必填，PayerMax 与 PayBy 实现不读。

两项都是新增可选字段，向后兼容；按标准 API 版本更新，已有实现无需改动。

## 4. 账号材料（T4）

| 位置 | 键 | 来源 |
|---|---|---|
| 身份 | `channelCode=ALIPAY_PLUS`、`channelAccountId`、`entityNo=UAT01` | 验证账号身份 |
| `parameters` | `clientId` | `materials["channelMerchant.clientId"]` |
| `parameters` | `alipayPublicKey` | `materials["channelMerchant.publicKey"]` |
| `parameters` | `referenceMerchantId`、`merchantName`、`merchantMcc`、`merchantRegion`、`merchantCity` | `subMerchants.items[0]`（二级商户，下单 `order.merchant`） |
| `parameters` | `settlementCurrency` | `USD` |
| `credentials` | `merchantPrivateKey` | `materials["channelMerchant.privateKey"]` |

**T4：** 二级商户信息与结算币种作为渠道账号参数，每个账号固定一个二级商户；本期只录一个验证账号。`env.terminalType` 固定 `WEB`（标准 API 没有终端类型，WEB 返回的 `normalUrl` 是网页收银台，手机浏览器同样可打开）。

## 5. 脚本与原子 API

### 5.1 接入方 `ALIPAY_PLUS`

出站地址 `ALIPAY_PLUS_OPEN` = `https://open-sea.alipayplus.com`，`sandbox=true`。

### 5.2 公共脚本

| 编码 | 阶段 | 参数 | 行为 |
|---|---|---|---|
| `ALIPAY_PLUS_SIGN` | REQUEST | `privateKey`（PROTECTED_HANDLE）、`clientId`、`path` | `rt = nowRfc3339()`；写 `Client-Id`、`Request-Time`；`Signature = 'algorithm=RSA256, keyVersion=0, signature=' + urlEncode(rsaSha256Sign(privateKey, 'POST ' + path + '\n' + clientId + '.' + rt + '.' + sealJsonBody(http)))` |
| `ALIPAY_PLUS_VERIFY` | RESPONSE | `publicKey`、`path` | HTTP 200 且有 `signature` 头时：取 `signature=` 之后，`urlDecode` 后 `requireRsaSha256Signature(publicKey, 'POST ' + path + '\n' + client-id + '.' + response-time + '.' + responseRaw, sig)` |

### T6 未签名响应一律 ERROR

实测签名错误、接口不存在的应答不带签名。验签脚本不对无签名应答报错，但所有 SUCCESS/FAIL 判定都要求响应已验签（脚本写入标记，状态规则检查该标记），否则落到 ERROR。这样轨迹里保留渠道原文，又不会把未签名的 `F` 当成确定失败。

### 5.3 原子 API

POST、连接 2000 ms / 响应 10000 ms、JSON；脚本 `ALIPAY_PLUS_SIGN` → `ALIPAY_PLUS_VERIFY`，`path` 绑定各自常量。

| 原子 API | 路径 | 请求要点 | 状态规则 |
|---|---|---|---|
| `PAY_CASHIER` | `/payments/pay` | `paymentRequestId=attemptId`；`paymentAmount`、`order.orderAmount` = `{currency, str(MONEY_MINOR_UNITS)}`；`order.referenceOrderId=attemptId`、`orderDescription=subject`；`order.merchant.*` 取账号参数；`order.env.terminalType=WEB`；`paymentMethod.paymentMethodType`；`paymentFactor.isCashierPayment='true'`；`settlementStrategy.settlementCurrency`；`paymentRedirectUrl=returnUrl`；`paymentNotifyUrl=notifyUrl`（缺省时用 returnUrl，本期不接通知）；`userRegion=country` | U 且 `paymentId`、`normalUrl` 都有 → SUCCESS；S → SUCCESS；F → FAIL；其他 → ERROR |
| `INQUIRY_PAYMENT` | `/payments/inquiryPayment` | `paymentRequestId` | `result` S → SUCCESS；`result` F 且 `ORDER_NOT_EXIST` → FAIL；其他 → ERROR |
| `REFUND` | `/payments/refund` | `paymentRequestId`、`refundRequestId=refundId`、`refundAmount`（最小单位）、`refundReason` | S/U → SUCCESS；F → FAIL；其他 → ERROR |

`REFUND_QUERY` 复用 `INQUIRY_PAYMENT`，输出声明 `transactions[]`。

> 阶段 3 实施调整：只读表达式不支持按条件取数组元素，退款查询改为单独的原子 API `INQUIRY_REFUND`（同一路径）加 RESPONSE 脚本 `ALIPAY_PLUS_PICK_REFUND` 匹配 `transactions[]`，见[交付记录](./delivery.md)第 6 节。

### 5.4 流程输出

| 标准 API | 映射 |
|---|---|
| `PAY` | U → `PENDING` + `nextAction{REDIRECT, normalUrl}`；S → `SUCCEEDED`；F → `FAILED`、`failureCode=resultCode` |
| `PAY_QUERY` | `paymentResult` S/F/U → `SUCCEEDED`/`FAILED`/`PENDING`；`ORDER_NOT_EXIST` → `NOT_FOUND`；`amount` 经 `MONEY_MAJOR_UNITS` 转换 |
| `REFUND` | S → `SUCCEEDED`；U → `PROCESSING`；F → `FAILED` |
| `REFUND_QUERY` | 在 `transactions[]` 中找 `transactionType=REFUND` 且 `transactionRequestId=refundId`：`transactionStatus` SUCCESS/FAIL/其他 → `SUCCEEDED`/`FAILED`/`PROCESSING`；找不到或原单不存在 → `NOT_FOUND` |

`transactions[]` 的匹配需要按条件取数组元素；若现有表达式做不到，在流程节点里用有界循环，具体写法在阶段 2 配置时按引擎实际能力确定。

## 6. T5 不接 `cancelPayment`

官方文档：可在 T+1 日 00:15（UTC+8）前撤销，超窗返回 `CANCEL_WINDOW_EXCEED` 并要求调退款，说明窗口内对已成功付款同样生效。这是冲正语义，按项目规定由 Commerce/Payment 编排，不作为 `PAY_CANCEL` 实现。未付款订单靠 Alipay+ 到期自动关闭，Payment 用 `PAY_QUERY` 确认。

## 7. T7 验证用例

| 用例 | 步骤 | 是否需要付款 |
|---|---|---|
| `SBX_PAY_QUERY_NOT_FOUND` | 查询不存在的单 → `FAIL`、`NOT_FOUND`、`ORDER_NOT_EXIST` | 否 |
| `SBX_REFUND_NOT_EXIST` | 对不存在的单退款 → `FAIL`、`FAILED` | 否 |
| `SBX_REFUND_QUERY_NOT_FOUND` | 退款查询，原单不存在 → `FAIL`、`NOT_FOUND` | 否 |
| `SBX_PAY_PENDING` | 下单 USD 1.00 / `ALIPAY_CN` → `PENDING` + REDIRECT；查询 → `PENDING`；对它退款 → `FAIL`（`INVALID_ORDER_STATUS`） | 否（测试单靠到期关闭；harness 另用直连脚本关单清理，不经 Platform） |
| `SBX_PAID_REFUND` | 用户在浏览器打开收银台付款 → 查询 `SUCCEEDED` → 部分退款 → 退款查询 | 是 |

## 8. 阶段建议

| 阶段 | 内容 |
|---|---|
| 1 | 需求、技术方案与沙箱直连探测（本稿） |
| 2 | T1 引擎函数（按用户选定的归属与方式）；若改代码：实现、`auto` 测试、提交与发布 mor-test 各自授权 |
| 3 | mor-test 配置：标准 API 调整（T2、T3）、接入方、脚本、原子 API、流程、验证账号；运行不需付款的用例 |
| 4 | 用户付款后跑已付款链路 |
