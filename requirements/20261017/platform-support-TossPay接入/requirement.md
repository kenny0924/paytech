# Toss Pay 收款接入（mor-test 联调）

- 日期：2026-10-17
- 状态：**已确认**（2026-10-17，用户：“可以”）
- 主入口 / Owner：Platform Support（外部 API 配置与执行）
- 关联：[dLocal 接入](../platform-support-dLocal接入/requirement.md)、[Alipay+ 接入](../../20261016/platform-support-AlipayPlus接入/requirement.md)、[渠道沙箱验证](../../20261014/platform-support-渠道沙箱验证/requirement.md)、[标准 API 与流程决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)
- 技术方案：[technique.md](./technique.md)；交付记录：[delivery.md](./delivery.md)

## 1. 背景

旧网关的韩国钱包渠道 `I_TOSS` 已整理为对接包（仓库外 `~/Desktop/channels/I_TOSS`，含测试 apiKey，不入库）。用户要求把它接入 mor-test 的外部 API 执行引擎。

本渠道是 **토스페이 Toss Pay**（`pay.toss.im`，文档 docs-pay.toss.im），不是 Toss Payments PG。只收 KRW；买家在 Toss 付款页 / Toss App 中认证付款。旧网关还有自动结算（billingKey 授权、扣款、解约）与回调。

## 2. 已实测事实（2026-10-17，本机直连，未经 Platform）

凭据只用对接包记录 0（`sk_test_` 测试 key，进程内读取，未输出）；记录 1、2 是生产 key，对接包已移除，未使用。探测单号前缀 `MORPROBE2`，共 4 次建单。

| 项 | 结果 |
|---|---|
| 地址与环境 | 测试与生产同为 `https://pay.toss.im`，以 `sk_test_` key 区分；查单响应 `mode=TEST`。官方说明测试 key 不发生真实资金移动，但**付款认证要用买家本人手机上的 Toss App**；短时大量请求可能被自动封 IP |
| 认证 | **只有 JSON body 中的 `apiKey`**，没有签名、Authorization 头和时间戳；应答不签名 |
| 错误形态 | 一律 HTTP 200，`code=-1` + `errorCode` + `msg`（韩文） |
| 建单 | `POST /api/v2/payments`（`amount` 整数、`amountTaxFree=0`、`autoExecute=true`、`resultCallback`、`callbackVersion=V2`）返回 `code=0`、`payToken`、`checkoutPage`。旧系统的 `1000.00`、字符串 `"0"` 写法也被接受 |
| 重复建单 | 待付款时同一 `orderNo` 再建单，返回同一 `payToken`（与官方“重复会失败”的描述不同） |
| 查单 | `POST /api/v2/status` 按 `orderNo`：待付款 `PAY_STANDBY`、`amount`、`refundableAmount`、`transactions[]`（退款记录在其中，按 `refundNo` 区分）|
| 查不存在的单 | `code=-1`、`errorCode=COMMON_INVALID_PARAMETER`、`msg=…orderNo` |
| apiKey 错误 | 同样是 `COMMON_INVALID_PARAMETER`（`msg=가맹점 정보를 찾을 수 없습니다`，商户不存在）——**errorCode 区分不了“单不存在”与“key 错误”** |
| 退款 | `POST /api/v2/refunds` 需要 Toss `payToken` 与必填 `refundNo`（官方）；未付款单 `COMMON_REFUND_ERROR`；未知 payToken `COMMON_INVALID_PARAMETER`（결제 정보를 찾을 수 없습니다） |
| 撤销未付款单 | **没有撤销接口**；未付款单按 `expiredTime`（默认 15 分钟，最多 60 分钟）到期 |
| 到期后状态 | `MORPROBE203` 设 1 分钟到期，过期 1 分钟后查单**仍为 `PAY_STANDBY`**：查单不会给出到期终态，未付款单的超时由 Payment 自行判定 |

## 3. 目标

在 mor-test 的 Platform 新增接入方 `TOSS_PAY`（出站地址 `https://pay.toss.im`，因用测试 key 标记为沙箱），实现：

| 标准 API | Toss Pay 接口 | 说明 |
|---|---|---|
| `PAY` | `POST /api/v2/payments` | `nextAction=REDIRECT` + `checkoutPage`；只收 KRW |
| `PAY_QUERY` | `POST /api/v2/status` | |
| `REFUND` | `/api/v2/status` → `/api/v2/refunds` | 先按我方单号取 `payToken`；发 `refundNo`（修正旧系统不发） |
| `REFUND_QUERY` | `POST /api/v2/status` | 在 `transactions[]` 中按 `refundNo` 匹配（修正旧系统 `code==0` 即成功） |

## 4. 业务场景

| 场景 | 期望 |
|---|---|
| 下单受理 | `SUCCESS`，`PENDING`，`nextAction=REDIRECT` + `checkoutPage`，`channelTransactionRef=payToken` |
| 下单被拒（`code=-1`） | `FAIL`，`FAILED`，`failureCode=errorCode` |
| 币种不是 KRW、金额有小数 | `ERROR`，不发请求 |
| 下单超时或应答无法识别 | `ERROR`，调用方 `PAY_QUERY`（修正旧系统“超时判失败”） |
| 查询 待付款/认证中（含已过到期时间仍为 `PAY_STANDBY`）/ 成功 / 取消 | `SUCCESS`，`PENDING` / `SUCCEEDED` / `FAILED` |
| 查询 已退款状态（`REFUND_*`、`SETTLEMENT_REFUND_COMPLETE`） | `SUCCESS`，`SUCCEEDED`（付款曾成功；退款另查）。修正旧系统判 PENDING |
| 查询订单不存在 | `FAIL`，`NOT_FOUND` |
| 退款受理 | `SUCCESS`，`SUCCEEDED`（Toss 同步返回退款结果） |
| 退款被拒（未付款、超额、原单不存在） | `FAIL`，`FAILED` |
| 退款查询 找到 / 找不到 | `SUCCESS` + `SUCCEEDED` / `FAIL` + `NOT_FOUND` |

## 5. 本期范围（建议）

- 引擎补“请求体凭据字段”（技术方案 T1）：改 Platform 代码、发布 mor-test，各自另行授权。
- mor-test 配置：接入方、原子 API、流程、验证账号与验证用例。
- 不需要付款的用例自动跑；已付款链路需要有 Toss 账号的手机付款，作为可选阶段。

## 6. 不做

- `PAY_CANCEL`：Toss Pay 没有撤销未付款单的接口，靠到期。
- 自动结算（billingKey 授权、扣款、解约）：多步流程且要保存 billingKey，另立需求。
- 支付结果回调与浏览器回跳（需要公网入站）。
- 现金收据、结算 API。
- 旧系统内嵌商户号的 `netease` 前缀、截断退款金额等疑似缺陷（不照抄）。
- 生产 key、生产环境、推送。

## 7. 验收标准

1. 引擎 T1 的 `auto` 测试通过；mor-test 发布后既有渠道回归通过。
2. mor-test 上 `TOSS_PAY` 全部配置保存成功、校验通过；不需要付款的用例全部 PASSED。
3. apiKey 不出现在仓库、回执、执行轨迹、Platform 日志与响应中（轨迹中请求体的 apiKey 为遮蔽值）；付款页 URL 不写入回执。
