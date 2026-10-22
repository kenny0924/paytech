# MPGS（Network International）卡收单接入（mor-test 联调沙箱）

- 日期：2026-10-21
- 状态：**范围已确认**（2026-10-21）；阶段 2 决定 D1～D5 已确认，验收标准已按决定修订，见 [delivery.md](./delivery.md)。2026-10-22 追加预授权与请款（第 6 节，已在 mor-test 实施）
- 主入口 / Owner：channel-connector（渠道协议：标准 API、接入方、原子 API、流程与沙箱验证）
- 关联：[dLocal 接入](../../20261017/platform-support-dLocal接入/requirement.md)、[PayerMax 卡直付对接](../../20261014/payment-PayerMax卡直付对接/requirement.md)、[统一对外渠道出口决定](../../../agent-docs/decisions/2026-10-20-channel-connector-unified-outbound.md)、[标准 API 与流程决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)
- 技术方案：[technique.md](./technique.md)；交付记录：[delivery.md](./delivery.md)

## 1. 背景

旧网关的 MPGS 渠道 `I_MPGS_NI`（Mastercard Payment Gateway Services，收单方 Network International，中东卡收单）已整理为对接包（仓库外 `~/Desktop/channels/I_MPGS_NI`，含沙箱真实凭据，不入库）。用户要求把它接入 mor-test 的渠道执行引擎，深度与已接入的 PayerMax、PayBy、Alipay+、dLocal、TossPay 相同：渠道协议配置加沙箱验证。

MPGS 只有服务端卡直付（REST JSON，HTTP Basic），没有托管跳转页；下单需要卡数据。

## 2. 已实测事实（2026-10-21，本机直连沙箱，未经 channel-connector）

凭据只在进程内读取，未输出。探测只用只读 GET（`MORPROBE0001`，不存在的订单），未创建任何沙箱交易。

| 项 | 结果 |
|---|---|
| 地址 | `https://test-gateway.mastercard.com` 与 `https://test-network.mtf.gateway.mastercard.com` 对同一商户结果一致 |
| 认证 | `Authorization: Basic base64("merchant." + merchantId + ":" + apiPassword)`；错误凭据返回 `401 {"error":{"cause":"INVALID_REQUEST","explanation":"Invalid credentials."},"result":"ERROR"}` |
| 订单不存在 | `400 {"error":{"cause":"INVALID_REQUEST","explanation":"Unable to find order … and transaction … for merchant …"},"result":"ERROR"}` |
| 凭据有效性 | 24 个测试商户中 20 个有效；**USD 商户 `JOLLYMAX_USD` 与 3 个 PF 商户（`PF_MPGS_NI_AED_*`）返回 401，凭据已失效**。AED 商户 `JOLLYMAX_AED`（记录 `MPGS_TEST200900000049`）有效 |
| 网络 | 本机直连可用，未见 mTLS 或 IP 白名单要求 |

## 3. 目标

在 mor-test 新增接入方 `I_MPGS_NI`（API v100，出站地址指向沙箱并标记为沙箱），实现下列标准 API 并用渠道沙箱验证跑通：

| 标准 API | MPGS 接口 |
|---|---|
| `PAY`（卡直付，不含 3DS） | `PUT …/order/{attemptId}/transaction/{attemptId}`，`apiOperation=PAY` |
| `PAY_QUERY` | `GET …/order/{attemptId}/transaction/{attemptId}` |
| `REFUND` | `PUT …/order/{paymentAttemptId}/transaction/{refundId}`，`apiOperation=REFUND` |
| `REFUND_QUERY` | `GET …/order/{paymentAttemptId}/transaction/{refundId}` |

## 4. 不做

- 3DS（INITIATE_AUTHENTICATION、AUTHENTICATE_PAYER、中间页 HTML、浏览器回跳、认证后再下单）：引擎合同只承载跳转 URL，Payment 认证动作未接入，收银台无前端。
- Webhook（`X-Notification-Secret`）与 3DS 回跳：系统没有渠道入站入口，mor-test 不接受公网入站。
- Payment 发起的退款、查询与恢复：Payment 只调用 PAY。
- Apple Pay、Google Pay、网络 token、MIT、PF 模式（`subMerchant`）、`PAY_CANCEL`、`VOID`。预授权与请款已在 2026-10-22 追加，见第 6 节。
- 旧系统 v69 / v100 灰度：只做 v100。
- 对接包“疑似缺陷”不照抄（日志打印密码、年份格式不一等）。
- 生产环境、推送。

## 5. 验收标准

1. AC1：mor-test 上 `PAY` 标准 API 增加可选卡字段且其他接入方流程不受影响；`I_MPGS_NI` 的接入方、脚本、原子 API 与 `PAY`、`PAY_QUERY`、`REFUND`、`REFUND_QUERY` 流程保存成功并通过静态校验。
2. AC2：沙箱验证用例全部 PASSED（[技术方案第 5 节](./technique.md#5-沙箱验证)）：卡支付成功 → 查询成功 → 部分退款成功 → 退款查询成功 → 退款后支付查询仍成功；卡被拒；查询订单不存在；退款原单不存在；退款查询不存在。验证币种 USD，商户 `JOLLYMAX_AED`（沙箱接受 USD）。
3. AC3：Authorization、URL 与请求体按对接包 `test-vectors.json` 核对（v100 卡下单、退款、查询）。
4. AC4：凭据与测试卡号、CVV 原值不出现在仓库、回执、channel-connector 日志与响应中（验证账号详情的 `credentials` 回显按渠道沙箱验证已确认设计除外）。
5. AC5：PayerMax、PayBy、dLocal、Alipay+、TossPay 已有沙箱用例回归通过。
6. AC6：`CURRENT_IMPLEMENTATION.md` 回写，需求索引登记。

## 6. 追加：预授权与请款（2026-10-22）

用户：“帮我增加auth和capture的标准接口，把NI的加上”。

mor-test 现有标准 API 中没有 `AUTHORIZE` 和 `CAPTURE`。channel-connector 代码已有这两个渠道操作（`ChannelOperation`、能力 `ACTION.AUTHORIZE` / `ACTION.CAPTURE`），Payment 的动作类型也有 `AUTHORIZE` 和 `CAPTURE`，但都还没有对应的标准 API。

| 标准 API | 含义 | MPGS 接口 |
|---|---|---|
| `AUTHORIZE`（新增） | 卡预授权，冻结额度不扣款；不含 3DS | `PUT …/order/{attemptId}/transaction/{attemptId}`，`apiOperation=AUTHORIZE` |
| `CAPTURE`（新增） | 对已成功的预授权请款，可部分、可多次 | `PUT …/order/{paymentAttemptId}/transaction/{captureId}`，`apiOperation=CAPTURE` |

**不做：** 预授权和请款的单独查询标准 API、`VOID`（撤销预授权）、Payment 侧调用。

**验收追加：**
- AC7：mor-test 新增 `AUTHORIZE`、`CAPTURE` 两个标准 API，`I_MPGS_NI` 实现两条流程并通过静态校验；已有标准 API 与其他接入方不受影响。
- AC8：沙箱用例全部 PASSED：预授权成功 → 部分请款 → 超额请款被拒 → 余额请款 → 重发早先的请款仍报成功 → 已请款部分退款；预授权被拒且不能请款；对不存在的订单请款（[技术方案第 7 节](./technique.md#7-追加预授权与请款2026-10-22)）。
- AC9：凭据、卡号检查与回归同 AC4、AC5。
