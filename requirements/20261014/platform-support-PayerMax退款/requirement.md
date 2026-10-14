# PayerMax 退款与退款查询接入

- 日期：2026-10-14
- 状态：**已确认**（2026-10-14）
- 主入口 / Owner：Platform Support（外部 API 配置与执行）
- 关联：[PayerMax 收银台支付与交易查询](../platform-support-PayerMax接入/requirement.md)（本需求沿用其接入方、账号材料、签名与验签脚本）、[渠道沙箱验证](../platform-support-渠道沙箱验证/requirement.md)、[标准 API 与流程决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)
- 技术方案：[technique.md](./technique.md)；交付记录：[delivery.md](./delivery.md)

## 1. 背景

PayerMax 已接入 `PAY`（收银台下单）与 `PAY_QUERY`（交易查询），并在 UAT 经渠道沙箱验证跑通。用户要求接着接入 PayerMax 退款。Payment Execution 已有 `REFUND` Action 与 `t_channel_refund`，将来通过标准 API 调用渠道退款；本需求先在 Platform 提供渠道无关的退款标准 API，并给出 PayerMax 实现。

协议依据：PayerMax 官方文档 [退款申请](https://docs.payermax.com/en/202606-version/receipt/refund/application.html)、[退款查询](https://docs.payermax.com/en/202606-version/receipt/refund/inquiry.html)、[退款集成说明](https://docs.payermax.com/en/202606-version/acquiring/start-integration/related-capabilities-integration/refund-integration.html)、[错误码](https://docs.payermax.com/en/202606-version/acquiring/integration-testing-and-troubleshooting/troubleshooting/error-code.html)。

## 2. 目标

1. 新增两个渠道无关的标准 API：
   - **`REFUND` 退款**：对一笔已成功的支付发起全额或部分退款，返回归一化退款状态与渠道退款流水号。
   - **`REFUND_QUERY` 退款查询**：按我方退款号查询退款状态。
2. 在接入方 `PAYERMAX` / `PAYERMAX_MOCK` 下实现这两个标准 API：调用 PayerMax `/refund` 与 `/refundQuery`。
3. 用本机合成 PayerMax 覆盖全部场景，再经 Operations 渠道沙箱验证在 PayerMax UAT 跑通“已付款订单 → 退款 → 查询退款”。

## 3. 业务场景

| 场景 | 期望 |
|---|---|
| 退款受理 | `executeStatus=SUCCESS`，`refundStatus=PENDING`（PayerMax `REFUND_PENDING`），返回 `channelRefundRef`（PayerMax `refundTradeNo`） |
| 受理时已有终态 | `REFUND_SUCCESS` → `SUCCEEDED`；`REFUND_FAILED` → `FAILED` |
| 明确拒绝（原订单不存在、退款金额超额或过小、支付方式不支持退款或部分退款、余额不足、参数错误、签名错误等） | `executeStatus=FAIL`，`refundStatus=FAILED`，返回 PayerMax 错误码与描述；保证 PayerMax 没有创建这笔退款 |
| 系统繁忙、超时、未知错误码、验签失败、网络异常 | `executeStatus=ERROR`（结果未知）；调用方稍后用 `REFUND_QUERY` 确认，不得换一个退款号重新发起 |
| 查询退款中 / 成功 / 失败 | `executeStatus=SUCCESS`，`refundStatus` 为 `PENDING` / `SUCCEEDED` / `FAILED`；失败时带 PayerMax 失败原因 |
| 查询退款不存在 | `executeStatus=FAIL`，`refundStatus=NOT_FOUND` |
| 账号材料读取失败 | 503 `INTEGRATION_INITIALIZATION_FAILED`，保证没有请求 PayerMax |

退款号由调用方生成并保证唯一（Payment 将使用 Channel Refund ID）；同一笔退款的重试必须沿用原退款号。

## 4. 本期范围

- 标准 API `REFUND`、`REFUND_QUERY`；PayerMax 两个原子 API 与两条流程，复用现有 `PAYERMAX_SIGN`、`PAYERMAX_VERIFY` 脚本。
- 本机合成 PayerMax 增加 `/refund`、`/refundQuery`（真实 RSA 签名与报文格式）；`configure.py`、`verify_mock.py` 增加退款配置与场景。
- 渠道沙箱验证：PayerMax UAT 退款用例（`PAYERMAX` 接入方，账号 `UAT_MAIN`）。
- **不改 Platform、BFF、Gateway、前端代码，不改数据库结构**，配置全部经真实管理接口写入。

## 5. 不做

- 退款结果异步通知（`refundNotifyUrl` 需要公网回调地址）；标准 API 只透传调用方给出的通知地址。
- Payment 侧接入（Payment 会话负责）；标准 API 字段是草案，Payment 接入时可调整。
- 撤销、冲正等其他 PayerMax 接口；流程内不做“退款失败后自动查询或重试”。
- mor-test 写入 PayerMax 退款配置、共享环境 SQL、部署、提交与推送（需另行授权）。

## 6. 验收标准

1. 本机合成 PayerMax：退款受理、受理即成功、明确拒绝、`SYSTEM_ERROR` → ERROR、响应验签失败 → ERROR、查询三种状态、查询不存在，全部符合第 3 节；合成渠道确认请求签名、外层字段、`requestTime` 时效与 `refundAmount` 为 JSON 数字均有效。
2. PayerMax UAT（渠道沙箱验证）：对已付款订单退款得到受理并返回 `refundTradeNo`；随后查询返回 `PENDING` 或 `SUCCEEDED`；对不存在订单退款得到明确拒绝；PayerMax 对请求验签通过，Platform 对响应验签通过。
3. 私钥不出现在仓库、日志、响应与验证证据中。

## 7. 待确认

见 [technique.md 第 7 节](./technique.md#7-评审点)。
