# Toss Pay 接入技术方案

- 日期：2026-10-17
- 状态：**已确认**（2026-10-17）：T1 由本需求改引擎，D1～D5 按建议
- 需求：[requirement.md](./requirement.md)
- 依据（已读）：对接包 `README.md`、`notes.md`（凭据只按结构读取）；官方 [create](https://docs-pay.toss.im/reference/normal/create)、[status](https://docs-pay.toss.im/reference/common/status)、[refunds](https://docs-pay.toss.im/reference/common/refunds)、[step-6](https://docs-pay.toss.im/tutorial/step-6)、[faq-v2](https://docs-pay.toss.im/faq-v2)、[llms.txt 索引](https://docs-pay.toss.im/llms.txt)；引擎 `ChannelRequestBuilder`、`JsonValues`、`HttpWork`、`AtomicApiExecutor`、`IntegrationApiDefinitionValidator`、`ScriptFunctions`；dLocal、Alipay+ 的 harness。

## 1. 协议要点

| 项 | 内容 |
|---|---|
| 地址 | `https://pay.toss.im`（测试与生产同域，`sk_test_` / `sk_live_` 区分） |
| 认证 | JSON body `apiKey`；无签名、无认证头；应答不签名 |
| 结果 | HTTP 200；`code` 0 成功 / -1 失败，`errorCode`、`msg` |
| 金额 | KRW 整数 |
| 退款 | 需要 `payToken` 与唯一 `refundNo`；退款记录在 `/api/v2/status` 的 `transactions[]`（`stepType=REFUND`，`transactionAmount` 为负数） |

## 2. 引擎能力核对

| 需要 | 现状 |
|---|---|
| 凭据写入 JSON 请求体 | **不支持**：受控句柄只能用作 Header 或签名函数的密钥，`JsonValues.toJson` 遇到句柄即报错 |
| 整数金额 | `MONEY_MINOR_UNITS`（KRW 精度 0，非整数报错）返回整数 |
| 整数响应金额转主单位 | `MONEY_MAJOR_UNITS` |
| 数组按条件取元素 | RESPONSE 脚本有界 `for`（同 Alipay+、dLocal） |
| 字符串后缀判断 | 只读表达式有 `string.endsWith` |

### T1 请求体凭据字段（建议改代码）

参照卡字段 `cardFields` 的做法，原子 API 定义新增可选 `credentialFields`：

- 只能是 JSON 编码下的 BODY 字符串请求字段（不含数组段），不能有固定值；这些字段只接受受控句柄，其他字段仍拒绝句柄。
- 序列化：发送字节写原值；执行轨迹与沙箱验证证据写遮蔽值（`[redacted]`）。
- `sealJsonBody`：请求体含受控句柄时报错（节点 ERROR），避免脚本通过“锁定后的发送文本”读到凭据明文。需要对含凭据的请求体签名的渠道，届时另设计。
- 校验器给出 `CREDENTIAL_FIELD_*` 问题；编辑器目录随原子 API 结构文档更新。
- `auto` 测试（经标准 API 执行入口、真实出站 Client 与 Mock 渠道）：渠道收到明文 apiKey；轨迹中为遮蔽值；非凭据字段绑定句柄 → 校验或执行报错；`sealJsonBody` 遇到凭据 → ERROR；校验器规则。

改动点：`IntegrationApiDefinitionValidator`、`ExecutionApi`、`ChannelRequestBuilder`、`JsonValues`、`HttpWork`、`AtomicApiExecutor.traceBody` 及对应测试。需要发布 Platform 镜像到 mor-test（另行授权）。

**备选（不改代码）：** 把 apiKey 存成账号普通参数。参数在验证账号与执行轨迹中都是明文，会把凭据写进轨迹，不建议。

## 3. 账号材料（D1）

| 位置 | 键 | 来源 |
|---|---|---|
| 身份 | `channelCode=TOSS_PAY`、`channelAccountId`、`entityNo=UAT01` | 验证账号身份 |
| `credentials` | `apiKey` | 记录 0（`sk_test_`） |

**D1：** 只用测试 key；对接包中的生产 key 已移除，本需求不处理生产 key。

## 4. 原子 API 与流程

均为 POST JSON，连接 2000 ms / 响应 10000 ms，`decimalEncoding=NUMBER`，`credentialFields=["BODY:apiKey"]`。

| 原子 API | 路径 | 请求要点 | 状态规则 |
|---|---|---|---|
| `CREATE_PAYMENT` | `/api/v2/payments` | `orderNo=attemptId`；`amount=MONEY_MINOR_UNITS(KRW)`（整数）；`amountTaxFree=0`；`productDesc=subject`；`retUrl`、`retCancelUrl`=returnUrl；`autoExecute=true`；`resultCallback`=notifyUrl，缺省用 returnUrl（本期不接回调，Toss 要求必填）；`callbackVersion=V2` | `code=0` 且有 `payToken`、`checkoutPage` → SUCCESS；`code=-1` → FAIL；其他 → ERROR |
| `GET_STATUS` | `/api/v2/status` | `orderNo` | `code=0` 且有 `payStatus` → SUCCESS；`code=-1`、`COMMON_INVALID_PARAMETER` 且 `msg` 以 `orderNo` 结尾 → FAIL（不存在）；其他（含 apiKey 错误）→ ERROR |
| `GET_STATUS_REFUND` | `/api/v2/status` | 同上；RESPONSE 脚本按 `stepType=REFUND` 且 `refundNo=refundId` 取记录 | 有记录 → SUCCESS；`code=0` 无记录 → FAIL（`REFUND_NOT_EXIST`）；不存在 → FAIL；其他 → ERROR |
| `CREATE_REFUND` | `/api/v2/refunds` | `payToken`（取自 `GET_STATUS`）、`orderNo`、`refundNo=refundId`、`amount`（整数）、`amountTaxFree=0`、`reason` | `code=0` → SUCCESS；`code=-1` → FAIL；其他 → ERROR |

**D2：币种只收 KRW。** 流程把非 KRW 的币种传成空值，`MONEY_MINOR_UNITS` 报错，节点 ERROR、不发请求。

**D3：“单不存在”靠 `msg` 后缀识别。** `errorCode` 无法区分“单不存在”与“apiKey 错误”。只有 `msg` 以 `orderNo` 结尾时判 FAIL（`NOT_FOUND`），其余 `COMMON_INVALID_PARAMETER` 一律 ERROR，宁可让调用方重查也不误判不存在。Toss 改文案时会退化为 ERROR，不会误判。

| 标准 API | 节点 | 映射 |
|---|---|---|
| `PAY` | `CREATE_PAYMENT` | SUCCESS → `PENDING` + `nextAction{REDIRECT, checkoutPage}`；FAIL → `FAILED`、`failureCode=errorCode` |
| `PAY_QUERY` | `GET_STATUS` | `PAY_STANDBY`/`PAY_APPROVED`/`PAY_PROGRESS` → `PENDING`；`PAY_COMPLETE`/`SETTLEMENT_COMPLETE` 及退款相关状态 → `SUCCEEDED`（**D4**）；`PAY_CANCEL` → `FAILED`；其他值 → ERROR；FAIL → `NOT_FOUND`；`channelTransactionRef=payToken`，`amount` 经 `MONEY_MAJOR_UNITS` |
| `REFUND` | `GET_STATUS` → `CREATE_REFUND` | 退款 `code=0` → `SUCCEEDED`（Toss 同步完成退款，**D5**）；查单不存在或退款 `code=-1` → `FAILED`；`channelRefundRef=transactionId` |
| `REFUND_QUERY` | `GET_STATUS_REFUND` | 有记录 → `SUCCEEDED`；无记录或原单不存在 → `NOT_FOUND` |

**D4：** 退款后支付单 `payStatus` 变为 `REFUND_*`；付款本身已成功，查单按 `SUCCEEDED` 返回，退款结果由退款查询给出（修正旧系统判 `PENDING`）。

**D5：** 官方退款为同步结果，`code=0` 即退款完成；已付款链路验证前此项未实测。

不实现 `PAY_CANCEL`（没有接口）；未付款单靠 `expiredTime` 到期（本期不传，默认 15 分钟）。实测到期后查单仍为 `PAY_STANDBY`，`PAY_QUERY` 会一直返回 `PENDING`，未付款超时由 Payment 按下单时间自行判定。

## 5. 验证用例

| 用例 | 步骤 | 需要付款 |
|---|---|---|
| `SBX_PAY_QUERY_NOT_FOUND` | 查不存在的单 → `FAIL`、`NOT_FOUND` | 否 |
| `SBX_REFUND_NOT_EXIST` | 对不存在的单退款 → `FAIL`、`FAILED` | 否 |
| `SBX_REFUND_QUERY_NOT_FOUND` | 退款查询，原单不存在 → `FAIL`、`NOT_FOUND` | 否 |
| `SBX_PAY_PENDING` | KRW 1000 下单 → `PENDING` + REDIRECT；查询 `PENDING`、`1000 KRW`；退款 → `FAIL`（`COMMON_REFUND_ERROR`）；退款查询 → `NOT_FOUND`（`REFUND_NOT_EXIST`） | 否 |
| `SBX_PAY_NOT_KRW` | USD 下单 → `ERROR`，未发请求 | 否 |
| `SBX_PAID_REFUND` | 有 Toss 账号的手机在付款页付款 → 查询 `SUCCEEDED` → 部分退款 500 → 退款查询 `SUCCEEDED` | 是（可选） |

请求量很小（全部用例约 10 次调用），避免触发 Toss 的自动封 IP。

## 6. 阶段建议

| 阶段 | 内容 |
|---|---|
| 1 | 需求、技术方案与直连探测（本稿） |
| 2 | T1 引擎改动与 `auto` 测试；提交与发布 mor-test 各自授权 |
| 3 | mor-test 配置与不需付款的用例；既有渠道回归 |
| 4 | （可选）有 Toss 账号时跑已付款链路 |
| 5 | 文档回写与提交 |
