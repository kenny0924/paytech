# dLocal 收款接入（mor-test 联调沙箱）

- 日期：2026-10-17
- 状态：**已确认**（2026-10-17，用户：“可以，继续”）
- 主入口 / Owner：Platform Support（外部 API 配置与执行）
- 关联：[Alipay+ 接入](../../20261016/platform-support-AlipayPlus接入/requirement.md)、[PayBy 接入](../../20261014/platform-support-PayBy接入/requirement.md)、[渠道沙箱验证](../../20261014/platform-support-渠道沙箱验证/requirement.md)、[标准 API 与流程决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)
- 技术方案：[technique.md](./technique.md)；交付记录：[delivery.md](./delivery.md)

## 1. 背景

旧网关的 dLocal 收款渠道 `I_DLOCAL` 已整理为对接包（仓库外 `~/Desktop/channels/I_DLOCAL`，含沙箱真实凭据，不入库）。用户要求把它接入 mor-test 的外部 API 执行引擎。dLocal 是新兴市场支付聚合（巴西、墨西哥、哥伦比亚等的卡、本地钱包、PIX、Boleto、银行转账），支持托管跳转页（REDIRECT）与直连（DIRECT）两种流程。

旧网关在下单上有四条规则（通用 REDIRECT/卡、Nequi 二维码、SPEI 转账、SmartPix 免密代扣），另有授权、解约与三个回调。本期只取与现有标准 API 对应、且不涉及卡数据和代扣的托管跳转收款链路。

## 2. 已实测事实（2026-10-17，本机直连沙箱，未经 Platform）

凭据用对接包记录 6，只在进程内读取，未输出。探测单号前缀 `MORPROBE`。

| 项 | 结果 |
|---|---|
| 地址与认证 | `https://sandbox.dlocal.com`；`X-Login`、`X-Trans-Key`、`X-Date`、`Authorization: V2-HMAC-SHA256, Signature: hex(HMAC-SHA256(secretKey, xLogin + X-Date + body))`；GET 的 body 按空串签名。**X-Date 用 UTC 带毫秒 `Z` 被接受**；凭据或签名错误返回 `403 {code:3001}`。本机直连可用，未见 IP 白名单或 mTLS 要求 |
| 响应签名 | 出站响应**不带签名**（只有回调带签名），只能依赖 TLS |
| 托管跳转下单 | `payment_method_flow=REDIRECT`、`country=BR`、`currency=USD`、`payer={}`（空对象）即可，返回 `PENDING` + `redirect_url`，由 dLocal 页面收集付款人信息；可选 `payment_method_id`（如 `PQ` PIX 二维码）限定支付方式。**完全不传 `payer` 返回 `400 {code:5001, param:payer}`** |
| 重复下单 | 同一 `order_id` + `X-Idempotency-Key` 再次请求，返回同一笔支付 |
| 查询 | `GET /orders/{order_id}` 按我方单号查，返回 `payment_id`、`status`、原币种金额；不存在返回 `404 {code:4000}` |
| 沙箱强制状态 | `POST /sandbox-tools/payments {payment_id, status}` 可把待付款单改为 `PAID`/`REJECTED`（异步，数秒内生效）；`PATCH /sandbox-tools/refunds/update-status {id_refund, status}` 可把退款改为 `SUCCESS`。**已付款链路无需人工付款** |
| 退款 | `POST /refunds` **必须给 dLocal `payment_id`**，不接受 `order_id`（`5001 Missing parameter: payment_id`）。未付款单 `400 {5002}`；原单不存在 `404 {4000}`；超额 `400 {5007}`。PIX 单部分退款 0.50 USD 返回 `PENDING`，**退款金额与币种按本地币种返回（BRL 2.53）**，`status_code` 为数字 |
| 退款查询 | `GET /refunds/{dLocal 退款 id}` 与 `/status` 都可用（旧系统用前者）；不存在返回 `404 {4001}`。`GET /payments/{payment_id}` 带 `refunds[]`，每项有 `order_refund_id`（我方退款单号）与 `status` |
| 撤销待付款 | `POST /payments/{payment_id}/cancel`：待付款单变为 `CANCELLED`；重复撤销返回同样结果；**已付款单返回 `400 {5013 Unsupported operation}`**，不会撤销成功付款 |
| 错误体 | `{code: 数字, message, param?}`，HTTP 状态 400/403/404 |

探测单共 6 笔：`MORPROBE101`、`104`、`105` 为 `PAID`（`104` 有一笔 0.50 USD 退款已强制为 `SUCCESS`），`103` 为 `REJECTED`，`106` 为 `CANCELLED`；`102` 因缺少 `payer` 未创建。均为终态。

## 3. 目标

在 mor-test 的 Platform 新增接入方 `DLOCAL`（出站地址指向沙箱并标记为沙箱），实现下列标准 API，并用渠道沙箱验证跑通：

| 标准 API | dLocal 接口 | 说明 |
|---|---|---|
| `PAY` | `POST /payments`（REDIRECT） | `nextAction=REDIRECT` + `redirect_url` |
| `PAY_QUERY` | `GET /orders/{attemptId}` | |
| `PAY_CANCEL` | `GET /orders/{attemptId}` → `POST /payments/{payment_id}/cancel` | 只撤销待付款单 |
| `REFUND` | `GET /orders/{paymentAttemptId}` → `POST /refunds` | 先取 dLocal `payment_id` |
| `REFUND_QUERY` | `GET /orders/{paymentAttemptId}` → `GET /payments/{payment_id}` | 在 `refunds[]` 中按退款单号匹配 |

## 4. 业务场景

| 场景 | 期望 |
|---|---|
| 下单受理 | `SUCCESS`，`PENDING`，`nextAction=REDIRECT` + `redirect_url`，`channelTransactionRef=payment id` |
| 下单直接成功 / 失败（`PAID` / `REJECTED`、`CANCELLED`、`EXPIRED`） | `SUCCESS` + `SUCCEEDED` / `FAIL` + `FAILED` |
| 下单参数或业务拒绝（HTTP 4xx 带 `code`） | `FAIL`，`FAILED`，`failureCode=code` |
| 下单超时、5xx、无法识别的状态、`PENDING` 无跳转地址 | `ERROR`，调用方稍后 `PAY_QUERY`（修正旧系统“超时判失败”“PENDING 无 URL 判失败”） |
| 查询 待付款 / 成功 / 拒绝、取消或过期 | `SUCCESS`，`PENDING` / `SUCCEEDED` / `FAILED` |
| 查询订单不存在（`4000`） | `FAIL`，`NOT_FOUND` |
| 撤销待付款单（含重复撤销） | `SUCCESS`，`cancelStatus=CLOSED` |
| 撤销已付款单（`5013`） | `FAIL`，`cancelStatus=ALREADY_PAID`，调用方走退款 |
| 撤销不存在的单（`4000`） | `FAIL`，`cancelStatus=NOT_FOUND` |
| 退款受理 / 成功 | `SUCCESS`，`PROCESSING` / `SUCCEEDED` |
| 退款被拒（原单不存在、未付款、超额，或退款 `REJECTED`/`CANCELLED`） | `FAIL`，`FAILED` |
| 退款查询 成功 / 拒绝或取消 / 处理中 | `SUCCESS`，`SUCCEEDED` / `FAILED` / `PROCESSING` |
| 退款查询找不到原单或退款 | `FAIL`，`NOT_FOUND` |

## 5. 本期范围（建议）

- mor-test 配置：经真实管理接口保存接入方、公共脚本、原子 API、流程、验证账号与验证用例。**不改引擎代码、不改标准 API**（技术方案第 2 节）。
- 已付款链路由 harness 直连沙箱工具强制 `PAID`/退款 `SUCCESS`，与其他用例一起跑，不需要用户付款。

## 6. 不做

- 卡支付、3DS、`cvv_token`：卡数据归 Card，按项目 Card 边界另立需求。
- Nequi 二维码、SPEI 虚拟账户等 DIRECT 支付：标准 API `nextAction` 目前只有 `REDIRECT`，展示二维码 / 转账信息需另行扩展。
- SmartPix 授权（`applyAuth`）、扣款与解约（`revokeAuth`）：代扣多步流程且要保存 wallet token。
- 支付、退款、授权回调与浏览器回跳端点（需要公网入站能力）。
- 付款人信息（姓名、证件、邮箱、地址）：托管页收集，本期请求只发空 `payer`。
- 旧系统内嵌的灰度商户号、固定门牌地址、`" null"` description 等疑似缺陷（不照抄）。
- 生产环境、推送。

## 7. 验收标准

1. mor-test 上 `DLOCAL` 全部配置保存成功、校验通过。
2. 沙箱验证用例全部 PASSED，包括已付款链路（强制 `PAID` → 查询成功 → 部分退款 → 强制退款成功 → 退款查询成功）。
3. 凭据（`xTransKey`、`secretKey`）不出现在仓库、回执、Platform 日志与响应中；跳转 URL 不写入回执。
4. PayBy、PayerMax、Alipay+ 已有沙箱 / UAT 用例回归通过。
