# Alipay+ 收款接入（mor-test 联调沙箱）

- 日期：2026-10-16
- 状态：**已确认**（2026-10-16，用户：按建议，引擎函数由本会话实现）
- 主入口 / Owner：Platform Support（外部 API 配置与执行）
- 关联：[PayBy 接入](../../20261014/platform-support-PayBy接入/requirement.md)、[PayerMax 接入](../../20261014/platform-support-PayerMax接入/requirement.md)、[渠道沙箱验证](../../20261014/platform-support-渠道沙箱验证/requirement.md)、[标准 API 与流程决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)
- 技术方案：[technique.md](./technique.md)；交付记录：[delivery.md](./delivery.md)

## 1. 背景

旧网关的 Alipay+ 收款渠道 `I_ALIPAY` 已整理为对接包（仓库外 `~/Desktop/channels/I_ALIPAY`，含沙箱真实凭据，不入库）。用户要求把它接入 mor-test 的外部 API 执行引擎。Alipay+ 是跨境钱包聚合（Alipay CN/HK、GCash、TrueMoney、DANA、KakaoPay 等），消费者在钱包收银台扫码或跳转付款。

## 2. 已实测事实（2026-10-16，本机直连沙箱，未经 Platform）

| 项 | 结果 |
|---|---|
| 地址与网络 | 沙箱与生产同域名 `https://open-sea.alipayplus.com`，以 `SANDBOX_` 开头的 Client-Id 区分；本机直连可用，未见 IP 白名单或 mTLS 要求 |
| 请求签名 | 签名原文 `POST {path}\n{clientId}.{Request-Time}.{body}`，SHA256withRSA，Base64 后**必须 URL 编码**；未编码的签名返回 `PARAM_ILLEGAL`（签名字段异常） |
| Request-Time | `2026-10-16T06:28:55Z`、`…55.177Z`（UTC 带毫秒）与 `+08:00` 偏移三种写法都被接受，不需要旧系统的国家时区表 |
| 响应验签 | 响应头 `signature: algorithm=RSA256,keyVersion=0,signature=<URL 编码的 Base64>`、`response-time`（UTC `Z`）、`client-id`；按 `POST {path}\n{client-id}.{response-time}.{响应原文}` 用 Alipay+ 公钥验签通过 |
| 未签名响应 | 签名错误、接口不存在（`NO_INTERFACE_DEF`）的应答不带签名 |
| 收银台下单 | `ALIPAY_CN`/USD、`ALIPAY_HK`/HKD、`TRUEMONEY`/THB 都返回 `resultStatus=U`、`PAYMENT_IN_PROCESS` 与 `paymentId`。`terminalType=WEB` 返回 `normalUrl`（收银台页）和二维码；`WAP` 返回 `normalUrl` 与 `schemeUrl`。**不返回旧系统判定用的 `paymentUrl`**（旧逻辑会误判为失败） |
| 钱包类型 | `paymentMethod.paymentMethodType` 必填，缺少时返回 `PARAM_ILLEGAL` |
| 金额 | `value` 是最小货币单位的字符串（USD `"100"` = 1.00） |
| 重复下单 | 同一 `paymentRequestId` 重复请求，返回与第一次相同的响应（同一 `paymentId`） |
| 查询 | 订单不存在：`result F ORDER_NOT_EXIST`；处理中：`result S` + `paymentResult U PAYMENT_IN_PROCESS`；关闭后：`paymentResult F ORDER_IS_CLOSED` |
| 退款 | 原单不存在：`F ORDER_NOT_EXIST`；未付款订单：`F INVALID_ORDER_STATUS` |
| 退款查询 | 沙箱没有 `inquiryRefund`（`NO_INTERFACE_DEF`），只能按原支付单号调 `inquiryPayment`，在 `transactions[]` 里按退款单号匹配（旧系统做法） |
| 关单 `cancelPayment` | 未付款订单返回 `S` 并变为 `ORDER_IS_CLOSED`；重复关单、关不存在的单也返回 `S`。官方文档：窗口为 T+1 日 00:15（UTC+8）前，超窗返回 `CANCEL_WINDOW_EXCEED` 并要求改走退款，说明窗口内可撤销已成功的付款 |

探测单号前缀 `MORPROBE`，共 7 笔，均已关单并查询确认 `ORDER_IS_CLOSED`。

## 3. 目标

在 mor-test 的 Platform 新增接入方 `ALIPAY_PLUS`（出站地址指向沙箱并标记为沙箱），实现下列标准 API，并用渠道沙箱验证跑通：

| 标准 API | Alipay+ 接口 | 说明 |
|---|---|---|
| `PAY` | `/aps/api/v1/payments/pay`（收银台） | `nextAction=REDIRECT` + `normalUrl`，与 PayerMax 收银台一致 |
| `PAY_QUERY` | `/aps/api/v1/payments/inquiryPayment` | |
| `REFUND` | `/aps/api/v1/payments/refund` | |
| `REFUND_QUERY` | `/aps/api/v1/payments/inquiryPayment` | 需要原支付单号（见技术方案 T3） |

## 4. 业务场景

| 场景 | 期望 |
|---|---|
| 收银台下单受理 | `SUCCESS`，`paymentStatus=PENDING`，`nextAction=REDIRECT` + `normalUrl`，`channelTransactionRef=paymentId` |
| 下单明确失败（`resultStatus=F`，如参数非法、商户未开通） | `FAIL`，`paymentStatus=FAILED`，`failureCode=resultCode` |
| 下单结果未知、无签名、验签失败、超时 | `ERROR`，调用方稍后 `PAY_QUERY` |
| 查询 处理中 / 成功 / 已关闭或失败 | `SUCCESS`，`PENDING` / `SUCCEEDED` / `FAILED` |
| 查询订单不存在 | `FAIL`，`paymentStatus=NOT_FOUND` |
| 退款受理 / 成功 | `SUCCESS`，`PROCESSING` 或 `SUCCEEDED`（按 `resultStatus` U/S） |
| 退款明确拒绝（原单不存在、状态不对、超额） | `FAIL`，`refundStatus=FAILED` |
| 退款查询 成功 / 失败 / 处理中 | `SUCCESS`，`SUCCEEDED` / `FAILED` / `PROCESSING` |
| 退款查询找不到退款单 | `FAIL`，`refundStatus=NOT_FOUND` |

## 5. 本期范围（建议）

- mor-test 配置：经真实管理接口保存标准 API 调整、接入方、公共脚本、原子 API、流程、验证账号与验证用例。
- 引擎补 3 个小函数（技术方案 T1），需要改 Platform 代码、发布镜像，按阶段另行授权。
- 已付款链路（付款 → 查询成功 → 部分退款 → 退款查询）需用户在浏览器打开沙箱收银台付款。

## 6. 不做

- `cancelPayment` 作为 `PAY_CANCEL`：窗口内可撤销已成功付款，属于冲正语义；项目规定渠道流程不编排冲正类资金反向操作（技术方案 T5）。
- 授权代扣（`authorizations/prepare`、`applyToken`、`cancelToken` 与 `CONNECT_WALLET` 代扣支付）：多步流程且要保存 accessToken，另立需求。
- 支付通知 `notifyPayment`、授权通知、浏览器回跳端点（需要公网入站与签名应答能力）。
- 旧系统的国家时区表、金额 `amountType/supportDecimal` 配置与已列出的疑似缺陷（统一用 UTC 与最小单位换算，不照抄）。
- 生产环境、推送。

## 7. 验收标准

1. mor-test 上 `ALIPAY_PLUS` 全部配置保存成功、校验通过。
2. 不依赖付款的沙箱验证用例全部 PASSED；每个带签名响应验签通过。
3. 用户付款后，已付款链路跑通。
4. 私钥不出现在仓库、回执、Platform 日志与响应中；收银台 URL 不写入回执。
