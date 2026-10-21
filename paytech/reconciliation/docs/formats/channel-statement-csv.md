# 标准渠道账单 CSV

> 适用：reconciliation 切片 1，Operations 控制台「财务 / 对账 / 渠道账单」上传。依据 [需求](../../../../requirements/20261020/reconciliation-渠道资金对账切片1/requirement.md)、[technique §2.1 D-01、§2.5、§2.10 I-04 / I-09](../../../../requirements/20261020/reconciliation-渠道资金对账切片1/technique.md) 与 [2026-10-20 对账决定](../../../../agent-docs/decisions/2026-10-20-reconciliation-facts-via-billing.md)。示例：[examples/channel-statement-S1.csv](examples/channel-statement-S1.csv)。
>
> 实现位置：浏览器解析 `operations-console-web/src/reconciliation/reconModel.ts`（`parseChannelStatementCsv`）；接口合同 `paytech-reconciliation-api` 的 `StartChannelStatementImportRequestBody`、`AppendChannelStatementLinesRequestBody`；完成导入校验 `CompleteChannelStatementImportProcessor`、`ChannelStatementTotals`。三处不一致时以服务端为准，并修正本文。

## 1. 文件结构

| 项 | 规则 |
|---|---|
| 编码 | 严格 UTF-8（非法字节整单拒绝）；开头的 BOM 去掉后再解析，但文件 SHA-256 按原始字节计算 |
| 分隔与引号 | 逗号分隔；含逗号、引号或换行的值用双引号包裹，引号内 `""` 表示一个双引号；引号未闭合整单拒绝 |
| 换行 | CRLF 或 LF；只含空白的空行忽略 |
| 空白 | 列名与取值去掉首尾空白后使用 |

文件按顺序由四部分组成：

| 行 | 内容 |
|---|---|
| 第 1 行 | 账单头列名行：第一格固定为 `#HEADER`，其后是账单头列名 |
| 第 2 行 | 账单头取值行：第一格固定为 `#HEADER`，其后按第 1 行列名顺序给值；取值个数必须等于列名个数 |
| 第 3 行 | 明细列名行（不带 `#HEADER`） |
| 第 4 行起 | 明细行，每行一条，列数必须等于明细列名个数 |

前两行第一格不是 `#HEADER`、或总行数不足 3 行时整单拒绝。账单头和明细的列按列名识别，顺序不限；明细列名不得重复。

```csv
#HEADER,clearing_account_code,channel_batch_ref,settlement_date,currency,gross_amount,fee_amount,net_amount,cash_direction
#HEADER,NI-AE001-USD,NI-20261019-001,2026-10-19,USD,68.00,0.45,67.55,CREDIT
line_no,line_type,direction,amount,fee_amount,reference_type,external_transaction_id,transaction_date,terminal_id,remark
1,PAYMENT,CREDIT,100.00,0.45,NI_ORDER_REF,NI-ORD-0001,2026-10-17,T01,
2,REFUND,DEBIT,30.00,0,NI_REFUND_REF,NI-RFD-0001,2026-10-17,T01,"partial, customer"
3,ADJUSTMENT,DEBIT,2.00,0,,,2026-10-19,,monthly fee
```

## 2. 账单头列（全部必填）

| 列 | 类型 | 规则 |
|---|---|---|
| `clearing_account_code` | 字符串 | `[A-Z][A-Z0-9_-]{1,63}`；清算账户必须存在且 `ACTIVE` |
| `channel_batch_ref` | 字符串 | 1–128 位可见 ASCII；渠道结算批次号 |
| `settlement_date` | 日期 | `YYYY-MM-DD`，必须是真实日期 |
| `currency` | 字符串 | 三位大写 ISO 4217；必须等于清算账户币种（切片 1 不支持多币种结算） |
| `gross_amount` | 带符号十进制 | `-?(0\|[1-9][0-9]{0,15})(\.[0-9]{1,8})?`；= Σ CREDIT 行金额 − Σ DEBIT 行金额（不含 FEE 行），可为负 |
| `fee_amount` | 非负十进制 | 费用合计 = Σ 各行 `fee_amount` + Σ FEE 行 `amount` |
| `net_amount` | 非负十进制 | 净额绝对值，方向见 `cash_direction` |
| `cash_direction` | 枚举 | `CREDIT`（渠道付给平台）、`DEBIT`（平台付给渠道）、`NONE`（净额为 0）；**切片 1 完成导入时拒绝 `DEBIT`**（technique I-04，切片 2 支持） |

金额是币种主单位的普通十进制，不带千分位、不用科学计数法；服务端另校验小数位不超过币种精度（USD 为 2 位）。

## 3. 明细列

| 列 | 必填 | 类型 | 规则 |
|---|---|---|---|
| `line_no` | 是 | 整数 | 从 1 开始按文件顺序连续递增（第 n 条明细必须是 `n`）；也是分段导入的幂等键 |
| `line_type` | 是 | 枚举 | `PAYMENT`、`REFUND`、`CHARGEBACK`、`FEE`、`ADJUSTMENT` |
| `direction` | 是 | 枚举 | `CREDIT` 或 `DEBIT`（相对平台）。交易行方向固定：`PAYMENT` 必须 `CREDIT`，`REFUND` / `CHARGEBACK` 必须 `DEBIT`（完成导入时校验）；`ADJUSTMENT` 按实际方向填写 |
| `amount` | 是 | 非负十进制 | 行金额，方向单独由 `direction` 表达 |
| `fee_amount` | 是 | 非负十进制 | 本行直接扣的渠道费，没有填 `0`，不能留空 |
| `reference_type` | 条件 | 字符串 | `[A-Z][A-Z0-9_]{0,31}`，Payment 渠道交易引用类型目录中的 Code（非固定枚举） |
| `external_transaction_id` | 条件 | 字符串 | 1–128 位可见 ASCII，渠道外部交易号 |
| `transaction_date` | 否 | 日期 | `YYYY-MM-DD`，可空 |

`reference_type` 与 `external_transaction_id` 必须同时填写或同时为空：`PAYMENT`、`REFUND`、`CHARGEBACK` 必须填写；`ADJUSTMENT` 与不对应具体交易的 `FEE` 行可以为空。带交易号的 `FEE` 行计入同交易号交易行的实际费用。

切片 1 的平台流水只有 PAYMENT（应收）和 REFUND（应付）；`CHARGEBACK` 行格式上接受，但匹配不到平台流水，会按等待期转为异常，由人工处置。

### 3.1 其他列 → `raw_attributes`

标准列以外的明细列原样保存为该行的 `raw_attributes`（JSON Object，列名 → 原值），不参与匹配与校验：

- 单元格为空的列不带入；
- 每个文件最多 64 个其他列，列名最长 64 字符，单个值最长 1,024 字符；
- 一行的 `raw_attributes` 序列化后不得超过 4 KiB（服务端追加时校验）。

示例中第 2 行得到 `{"terminal_id":"T01","remark":"partial, customer"}`，第 3 行得到 `{"remark":"monthly fee"}`。

## 4. 恒等式（完成导入时校验）

```
gross_amount = Σ(非 FEE 行：CREDIT 为 +amount，DEBIT 为 −amount)
fee_amount   = Σ(全部行 fee_amount) + Σ(FEE 行 amount)
带方向净额   = gross_amount − fee_amount        （CREDIT 为 +net_amount，DEBIT 为 −net_amount，NONE 时 net_amount 必须为 0）
```

S1：gross = 100.00 − 30.00 − 2.00 = 68.00；fee = 0.45；净额 = 68.00 − 0.45 = 67.55 CREDIT。

## 5. 上传过程与上限（分段导入，D-01）

浏览器本地解析并对**整个原文件**算 SHA-256，然后分三步调用（BFF 路径 `/finance/reconciliation/v1/...`，reconciliation 路径 `/reconciliation/v1/...`）：

| 步 | 接口 | 内容 |
|---|---|---|
| 开始 | `POST /channel-statements` | 账单头、`fileName`（≤ 255）、`fileContentHash`（64 位小写十六进制）、`declaredLineCount`（1–20,000）→ 账单 `RECEIVING` |
| 追加 | `POST /channel-statements/{id}/lines` | 每段 1–100 行；按 `lineNo` 幂等：同号同内容忽略，同号不同内容拒绝 |
| 完成 | `POST /channel-statements/{id}/completion` | 无请求体；一次性校验，通过 → `MATCHING` 并登记匹配任务，不通过 → `REJECTED` 并返回 `rejections[{lineNo, reason}]` |

| 上限 | 值 |
|---|---|
| 每张账单明细行 | 1–20,000 |
| 每段行数 | ≤ 100 |
| 每段请求体 | Gateway 路由上限 64 KiB（65,536 字节）；页面按 ≤ 60,000 字节切段留余量 |
| 分段追加限流 | 每人每分钟 300 次（technique I-11） |

服务不保存原文件，只保存文件名、SHA-256 与解析后的行。只有开始导入的上传人可以追加和完成。

## 6. 拒绝原因

**浏览器解析（不发请求，页面列出行号 + 原因）：** 前两行不是 `#HEADER`；缺账单头列或明细列；账单头取值个数与列名不一致；明细列名重复；账单头字段格式不符（编码、批次号、日期、币种、金额、方向）；没有明细或超过 20,000 行；其他列超过 64 列；`line_no` 不连续；列数不符；`line_type` / `direction` 不在允许值；`amount`、`fee_amount` 不是非负十进制；交易号两列不同有同无；交易行缺交易号；`transaction_date` 格式不符；其他列的值超过 1,024 字符；引号未闭合；文件不是合法 UTF-8。

**开始导入（HTTP 400 业务错误）：** 清算账户不存在或已停用；账单币种不等于清算账户币种；金额小数位超过币种精度；同一清算账户 + 批次号已有 `RECONCILED` / `CASH_CONFIRMED` 版本（不能更正重传，差额走后续账单的调整行）；已有 `RECEIVING` / `MATCHING` 版本（须先作废）。否则 `revision` = 已有最大版本 + 1。

**追加（HTTP 400 业务错误）：** 账单不在 `RECEIVING`；非上传人；段内行号重复；行号超出声明行数；金额为负或超过币种精度；同一行号已接收不同内容；`raw_attributes` 超过 4 KiB。

**完成导入（整单 `REJECTED`，逐条返回）：** 已接收行数与声明行数不一致；`cash_direction` 为 `DEBIT`；明细总额 / 费用与账单头不一致；总额减费用与带方向净额不一致；`NONE` 时净额不为 0；某行交易号两列不同有同无；交易行缺交易号；交易行方向不符（`PAYMENT` 须 `CREDIT`，`REFUND` / `CHARGEBACK` 须 `DEBIT`）。`lineNo` 为空的是整单原因。

被拒或作废后，同一批次号可以重新上传为新的 `revision`。
