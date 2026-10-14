# PayBy（阿联酋）收款接入（mor-test 联调 UAT）

- 日期：2026-10-14
- 状态：**已确认**（2026-10-14；用户改为以 DIRECTPAY 卡直付为主，其余评审点按建议）
- 主入口 / Owner：Platform Support（外部 API 配置与执行）
- 关联：[PayerMax 接入](../platform-support-PayerMax接入/requirement.md)、[PayerMax 退款](../platform-support-PayerMax退款/requirement.md)、[渠道沙箱验证](../platform-support-渠道沙箱验证/requirement.md)、[标准 API 与流程决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)
- 技术方案：[technique.md](./technique.md)；交付记录：[delivery.md](./delivery.md)

## 1. 背景

旧网关的 PayBy 收款渠道 `I_PAYBY` 已整理为对接包（仓库外 `~/Desktop/channels/I_PAYBY`，含 UAT 真实凭据，不入库）。用户要求把 PayBy 接入 mor-test 的外部 API 执行引擎并实际验证；范围按官网（[developers.payby.com](https://developers.payby.com/docs/integration-guide)）补齐下单等接口。

## 2. 已实测事实（2026-10-14，本机直连 UAT，未经 Platform）

| 项 | 结果 |
|---|---|
| 网络与签名 | 出口 IP 已在 PayBy 白名单；`Sign` = SHA256withRSA(商户私钥, Body 原文) 被接受 |
| PayBy 公钥 | 对接包 **`channelPublicKey_bak`** 同时用于响应验签与卡字段加密（与官网“同一把 PayBy 公钥”一致）；`channelPublicKey` 两者都不通过 |
| 签名覆盖 | 业务响应与部分 `applyStatus=ERROR`（如卡字段解密失败）带 `sign`；时间偏差、JSON 格式错误不带 |
| `requestTime` | 必须是毫秒时间戳 JSON 数字；偏差超过约 15 分钟返回 `REQUESTTIME_TOO_EARLY` |
| 金额 | 请求 `totalAmount.amount` 用 JSON 数字（`1.00`）被接受；响应金额是 JSON 数字（`1.0`、`669.91`） |
| 卡直付 | UAT 已开通 `DIRECTPAY`；测试卡 5123 4500 0000 0008（05/31，CVV 100）下单返回 `CREATED` 与 `interActionParams.threeDSecureDom`：自动 POST 的 HTML 表单（实际键名大写 A，官网写作 `interactionParams`） |
| 收银台下单 | `PAYPAGE` 返回 `CREATED` 与 `tokenUrl`；本期不作为 `PAY` 实现（用户以卡支付为主） |
| 币种 | USD 下单返回 `code=0` 但 `status=FAILURE`（"Status error"），UAT 商户只开通 AED |
| 关单 | `cancelOrder` 把 CREATED 订单变为 `FAILURE`；重复关单返回 `62002 ORDER_FAILURE` |
| 不存在 | 查单/退款 `62004`，退款查询 `62007`，均为 `applyStatus=SUCCESS` 且带签名 |
| 余额 | `getBalance(AED)` 返回 `code=0` 与 `balanceList[].availableBalance` |

## 3. 目标

在 mor-test 的 Platform 新增接入方 `PAYBY`（出站地址指向 UAT 并标记沙箱），实现下列标准 API，并用渠道沙箱验证实际跑通：

| 标准 API | 现状 | PayBy 接口 |
|---|---|---|
| `PAY` | 已有，卡直付会话正在扩展卡字段 | `/acquire2/placeOrder`，`DIRECTPAY` 卡直付（依赖 Platform 卡能力，见[补充要求](./card-capability-addendum.md)） |
| `PAY_QUERY` | 已有 | `/acquire2/getOrder` |
| `PAY_CANCEL`（新，草案） | 无 | `/acquire2/cancelOrder`，只关闭未支付订单 |
| `REFUND` | 已有（PayerMax 退款） | `/acquire2/refund/placeOrder` |
| `REFUND_QUERY` | 已有 | `/acquire2/refund/getOrder` |
| `BALANCE_QUERY`（新，草案） | 无 | `/member/getBalance` |

## 4. 业务场景

| 场景 | 期望 |
|---|---|
| AED 卡直付需要 3DS | `SUCCESS`，`paymentStatus=PENDING`，`nextAction` = REDIRECT + 我们托管渠道表单的 URL，`channelTransactionRef` = PayBy `orderNo` |
| 卡直付直接成功 | `SUCCESS`，`paymentStatus=SUCCEEDED` |
| 下单同步给出 `FAILURE`（如币种未开通） | `FAIL`，`paymentStatus=FAILED`，`failureCode` 为 `failCode`（无则 `FAILURE`） |
| 下单明确拒绝码（参数、场景、风控等） | `FAIL` |
| 系统错误、超时、限流、协议错误、验签失败、未知码、同号已存在 | `ERROR`，调用方稍后 `PAY_QUERY` |
| 查询 CREATED / PAID_SUCCESS、SETTLED / FAILURE | `SUCCESS`，`PENDING` / `SUCCEEDED` / `FAILED` |
| 查询订单不存在 | `FAIL`，`paymentStatus=NOT_FOUND` |
| 关闭未支付订单 | `SUCCESS`，`cancelStatus=CLOSED`；已关闭或已失败的重复关单同样 `CLOSED` |
| 关闭已支付订单 | `FAIL`，`cancelStatus=ALREADY_PAID`，调用方走退款 |
| 退款受理 / 不存在 / 超额等拒绝 | 同 PayerMax 退款口径；不存在 → `FAIL`、`refundStatus=FAILED` |
| 退款查询不存在 | `FAIL`，`refundStatus=NOT_FOUND` |
| 余额查询 | `SUCCESS`，返回请求币种的可用余额；币种不在列表中 → `FAIL`、`CURRENCY_NOT_FOUND` |

## 5. 本期范围

- 仅 **配置**：经 mor-test 真实管理接口（Nginx → Gateway → BFF → Platform）保存标准 API、接入方、公共脚本、原子 API、流程、验证账号与验证用例；不改代码、镜像、SQL、Nacos 或基础设施。
- 新标准 API `PAY_CANCEL`、`BALANCE_QUERY` 的字段为草案，Payment/Finance 接入时可调整。
- 用 mor-test 渠道沙箱验证对 UAT 运行验证用例；能自动完成的全部跑完；已付款路径需用户在浏览器打开收银台付款。

## 6. 不做

- 本会话不改 Platform 代码：DIRECTPAY 所需的卡字段加密、有效期格式、渠道表单托管由卡直付会话实现（用户决定，见[补充要求](./card-capability-addendum.md)）；能力发布到 mor-test 后再配置 `PAY`。
- `PAYPAGE` 收银台作为 `PAY` 实现。
- `revokeOrder`（已付即退款、未付即关单的冲正语义）：项目规定渠道流程不编排冲正类资金反向操作，Reversal 由 Commerce/Payment 编排。
- 支付/退款/VA 来账异步通知、页面回跳（需要公网回调地址与入站能力）。
- 分账、促销、二级商户、Transfer/Payout、对账文件下载。
- 新增毫秒时间戳注册函数（用户选择本期用脚本换算，见技术方案 T1）。
- 提交、推送；生产环境。

## 7. 验收标准

1. mor-test 上 `PAYBY` 全部配置保存成功、校验通过，Operations 外部 API 页面可见。
2. 沙箱验证用例（技术方案第 6 节）全部 PASSED；Platform 对每个带签名响应验签通过，PayBy 侧无签名/时间错误。
3. 已付款链路（下单 → 浏览器付款 → 查询 `SUCCEEDED` → 部分退款 → 退款查询）在用户付款后跑通。
4. 私钥不出现在仓库、回执、Platform 日志与响应中；`tokenUrl` 不写入回执。
