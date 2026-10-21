# 标准银行对账单 CSV

> 适用：reconciliation 切片 1，Operations 控制台「财务 / 对账 / 银行对账」上传。依据 [需求](../../../../requirements/20261020/reconciliation-渠道资金对账切片1/requirement.md)、[technique §2.1 D-01、§2.3 #18、§2.5、§2.10 I-09、I-15–I-18](../../../../requirements/20261020/reconciliation-渠道资金对账切片1/technique.md)、[设计 F2 对账单连续性（G7）](../../../../design-discuss/reconciliation/design.md)。示例：[examples/bank-statement-S1.csv](examples/bank-statement-S1.csv)。
>
> 实现位置：浏览器解析 `operations-console-web/src/reconciliation/reconModel.ts`（`parseBankStatementCsv`）；接口合同 `paytech-reconciliation-api` 的 `BankStatementApi`、`StartBankStatementImportRequestBody`、`AppendBankStatementLinesRequestBody`、`BankStatementCompletionResponseBody`；服务端 `StartBankStatementImportProcessor`、`AppendBankStatementLinesProcessor`、`CompleteBankStatementImportProcessor`、`domain/BankStatementDuplicates`。三处不一致时以服务端为准，并修正本文。

## 1. 文件结构

与[渠道账单](channel-statement-csv.md)相同：UTF-8（去 BOM，SHA-256 按原始字节）、逗号分隔、双引号转义、CRLF / LF、空行忽略、取值去首尾空白；

| 行 | 内容 |
|---|---|
| 第 1 行 | 头部列名行：第一格固定为 `#HEADER`，其后是头部列名 |
| 第 2 行 | 头部取值行：第一格固定为 `#HEADER`，其后按第 1 行顺序给值；个数必须等于列名个数 |
| 第 3 行 | 流水列名行（不带 `#HEADER`），列名不得重复 |
| 第 4 行起 | 流水行，每行一条，列数必须等于流水列名个数；可以没有流水行 |

列按列名识别，顺序不限。银行对账单**没有** `raw_attributes`：标准列以外的流水列被忽略、不入库。

```csv
#HEADER,bank_account_last4,statement_ref,period_start,period_end,currency,opening_balance,closing_balance
#HEADER,4321,ENBD-20261019,2026-10-19,2026-10-19,USD,1000.00,1067.40
line_no,bank_transaction_ref,value_date,direction,amount,counterparty_name,reference_text
1,ENBD-TXN-0001,2026-10-19,CREDIT,67.40,NETWORK INTERNATIONAL,SETTLEMENT NI-20261019-001
```

示例对应设计第 3 节 S1 第 7 步：渠道账单净额 67.55，银行实际到账 67.40（中间行扣 0.15）。

## 2. 头部列（全部必填）

| 列 | 类型 | 规则 |
|---|---|---|
| `bank_account_last4` | 字符串 | 4 位字母或数字；必须等于页面所选银行账户的末四位（页面先选银行账户，文件不带完整账号） |
| `statement_ref` | 字符串 | 1–128 位可见 ASCII；同一银行账户内唯一 |
| `period_start` | 日期 | `YYYY-MM-DD`，期间开始日（含） |
| `period_end` | 日期 | `YYYY-MM-DD`，期间结束日（含），不早于 `period_start` |
| `currency` | 字符串 | 三位大写 ISO 4217；必须等于银行账户币种 |
| `opening_balance` | 带符号十进制 | 银行报告的期初余额，`-?(0\|[1-9][0-9]{0,15})(\.[0-9]{1,8})?`，可为负 |
| `closing_balance` | 带符号十进制 | 银行报告的期末余额，同上 |

## 3. 流水列

| 列 | 必填 | 类型 | 规则 |
|---|---|---|---|
| `line_no` | 是 | 整数 | 从 1 开始按文件顺序连续递增；分段导入的幂等键 |
| `bank_transaction_ref` | 是 | 字符串 | 1–128 位可见 ASCII；同一银行账户内唯一：已在其他对账单导入、或在本文件内重复，整张对账单被拒（technique I-15） |
| `value_date` | 是 | 日期 | `YYYY-MM-DD`，起息日，必须在 `period_start` 至 `period_end` 内（含两端），否则整单被拒（I-18） |
| `direction` | 是 | 枚举 | `CREDIT`（入账）或 `DEBIT`（出账），相对平台银行账户 |
| `amount` | 是 | 正十进制 | 必须大于 0（追加时拒绝 0），小数位不超过币种精度；方向单独由 `direction` 表达 |
| `counterparty_name` | 否（列必须存在） | 字符串 | 银行给出的对方名称，≤ 255 字符 |
| `reference_text` | 否（列必须存在） | 字符串 | 附言或参考号，≤ 500 字符，只用于人工识别与匹配建议参考 |

## 4. 完成导入时的校验

以下任一项不满足，整张对账单 `REJECTED`，已接收的流水删除，返回全部原因（整单原因在前，逐行原因按行号升序）：

1. **行数：** 已入库行数 + 交易号重复行数 = 声明行数，即全文件每一行都已接收。
2. **余额恒等式（文件内没有交易号重复行时核对，覆盖全文件）：**

   ```
   opening_balance + Σ(CREDIT 流水 amount) − Σ(DEBIT 流水 amount) = closing_balance
   ```

   S1：1000.00 + 67.40 = 1067.40。
3. **交易号不重复（I-15）：** 同一银行账户已在其他对账单导入的 `bank_transaction_ref`、或本文件内被另一行使用过的 `bank_transaction_ref`，追加时不入库、只记下，完成时整单拒绝并逐行给原因。请上传与已导入对账单不重叠的文件。
4. **起息日在期间内（I-18）：** 每行 `value_date` 必须在 `period_start` 至 `period_end` 内。

全部通过后：

- 状态 `IMPORTED`，登记 `BANK_MATCH_SUGGEST` 任务，对该银行账户未匹配流水出一对一匹配建议。
- **连续性（不拒绝）：** 与同一银行账户最近一张已导入对账单（期间结束日最晚）比较，要求上一张 `closing_balance` 等于本张 `opening_balance`，且上一张 `period_end` 的次日等于本张 `period_start`；不满足时导入仍成功，同一事务开一条不阻塞的 `BANK_STATEMENT_GAP` 异常（差异金额 = 本张期初 − 上一张期末），结果返回 `gapExceptionId`。没有上一张时不检查。

## 5. 上传过程与上限（分段导入，D-01）

| 步 | 接口（reconciliation 路径；BFF 路径前加 `/finance`） | 内容 |
|---|---|---|
| 开始 | `POST /reconciliation/v1/bank-statements` | `cashAccountId`（页面所选）、头部、`fileName`（≤ 255）、`fileContentHash`（64 位小写十六进制）、`declaredLineCount`（0–20,000）→ `RECEIVING` |
| 追加 | `POST /reconciliation/v1/bank-statements/{id}/lines` | 每段 1–100 行；按 `lineNo` 幂等：同号同内容忽略（重发同一段不重复计数），同号不同内容拒绝。没有流水时跳过本步 |
| 完成 | `POST /reconciliation/v1/bank-statements/{id}/completion` | 无请求体；返回 `status`（`IMPORTED` / `REJECTED`）、`importedLineCount`、`duplicateLineCount`（检出的交易号重复行数，`IMPORTED` 时为 0）、`gapExceptionId`、`rejectReason`、`rejections[{lineNo, reason}]`；已完成的对账单再次调用返回当前状态，`rejections` 为空 |

上限：每张 ≤ 20,000 行；每段 ≤ 100 行且请求体 ≤ 64 KiB（Gateway 路由上限 65,536 字节，页面按 ≤ 60,000 字节切段）；分段追加每人每分钟 300 次。服务不保存原文件，只保存文件名、SHA-256 与解析后的流水。只有开始导入的上传人可以追加和完成。

**被拒后重传（I-16）：** 同一银行账户 + `statement_ref` 已有 `REJECTED` 对账单时，再次开始导入会复用该记录，重置为 `RECEIVING`（期间、期初期末、文件名 / Hash、声明行数、上传人按新请求，计数与拒绝原因清空），返回原主键；已是 `RECEIVING` 或 `IMPORTED` 的同号对账单仍拒绝。

## 6. 拒绝原因

服务端原文（`{}` 为占位）；HTTP 400 的原文在 Problem `detail`，完成导入的原文在 `rejections[].reason`，`rejectReason` 为 `校验不通过，共 {k} 条原因：…`（截断到 1,000 字）。

**浏览器解析（不发请求）：** 前两行不是 `#HEADER`；缺头部列或流水列；头部取值个数不符；流水列名重复；末四位格式不符或与所选银行账户不一致；`statement_ref`、日期、币种、余额格式不符；`period_end` 早于 `period_start`；超过 20,000 行；`line_no` 不连续；列数不符；`bank_transaction_ref` 不是可见 ASCII；`value_date` 格式不符；`direction` 不在允许值；`amount` 不是正十进制；对方名称或附言超长；引号未闭合；文件不是合法 UTF-8。浏览器不检查起息日是否在期间内、交易号是否重复，这两项由服务端完成导入时判定。

**开始导入（HTTP 400）：**
- `银行账户不存在或不是 ACTIVE: {id}`
- `对账单的银行账号末四位与所选银行账户不一致: …`
- `对账单币种必须等于银行账户币种: …`
- `对账单期间结束日早于开始日: {statement_ref}`
- `同一银行账户的对账单编号已存在: 银行账户 {id}, 编号 {ref}, 状态 {RECEIVING|IMPORTED}`
- 期初 / 期末余额小数位超过币种精度（格式同下方金额精度原因）

**追加（HTTP 400）：**
- `银行对账单不在接收中，不能追加流水: 对账单 {id}, 状态 …`；`只有开始导入的上传人可以追加流水: 对账单 {id}`
- `同一段内行号重复: 对账单 {id} 第 {n} 行`
- `行号超出声明行数 {N}: 对账单 {id} 第 {n} 行`
- `流水金额必须大于 0: 对账单 {id} 第 {n} 行`
- `对账单 {id} 第 {n} 行 金额 的小数位超过币种 {CCY} 的精度 {d}: {amount}`
- `同一行号已接收不同内容: 对账单 {id} 第 {n} 行`

交易号重复不在追加时拒绝，见完成导入。

**完成导入（整单 `REJECTED`）：**
- 整单：`已接收 {a} 行、交易号重复 {d} 行，与声明行数 {N} 不一致`
- 整单：`期初余额 {o} 加流水合计 {s} 等于 {c}，与期末余额 {closing} 不一致`（金额按币种精度；有交易号重复行时不给本条）
- 整单：`另有 {k} 行银行交易号重复，原因超出记录长度未逐行列出`（逐行原因累计超过 1,000 字时）
- 逐行：`银行交易号 {ref} 已在对账单 {statement_ref} 导入，请上传不重叠的对账单`
- 逐行：`银行交易号 {ref} 与第 {m} 行重复`
- 逐行：`起息日 {date} 不在对账单期间 {start} 至 {end} 内`

`lineNo` 为空的是整单原因。被拒的对账单可按第 5 节用同一 `statement_ref` 重传。
