# E3 文档线交付报告（阶段 4）

> 范围：technique §2.8 / §2.9 E 线的文档部分。未提交、未推送、未部署、未执行 SQL；未调用 Superpowers skill；未改任何 Java、pom、yml、测试与 `CURRENT_IMPLEMENTATION.md`；共享文件只做局部改动。

## 1. 文件

| 文件 | 改动 |
|---|---|
| `paytech/reconciliation/docs/formats/channel-statement-csv.md` | 新建：两行 `#HEADER`（列名、取值）+ 明细列名行 + 明细（I-09）；账单头 8 列、明细 8 列的类型 / 必填 / 允许值；其他列进 `raw_attributes`（空值不带、≤ 64 列、值 ≤ 1,024、行 ≤ 4 KiB）；恒等式；分段导入三步与上限（1–20,000 行、每段 ≤ 100 行、64 KiB / 页面 60,000 字节、每人每分钟 300 次）；浏览器 / 开始 / 追加 / 完成四处拒绝原因 |
| `paytech/reconciliation/docs/formats/bank-statement-csv.md` | 新建：同结构；头 7 列、流水 7 列；期初 + Σ = 期末（拒绝）、连续性开 `BANK_STATEMENT_GAP`（不拒绝）、重复交易号跳过计数；无 `raw_attributes` |
| `paytech/reconciliation/docs/formats/examples/channel-statement-S1.csv`、`bank-statement-S1.csv` | 新建：设计 §3 S1（PAYMENT 100 费 0.45、REFUND 30、ADJUSTMENT 月费 2.00 → 净额 67.55 CREDIT；银行入账 67.40，期初 1000.00 → 期末 1067.40）；编码、批次号沿用 Web 测试 `NI-AE001-USD` / `NI-20261019-001` |
| `paytech/reconciliation/README.md` | 重写：定位与边界（事实经 billing、不写分录）、模块、15 张表、入站 HTTP 与 MQ、出站（channel-connector、Vault、四类事件）、两个 XXL 任务、配置键、本机运行与测试；状态只指向台账 |
| `agent-docs/database/FINANCE_DATABASE.md` | §4.5、§6.2 顶部各加一段「2026-10-20 取代」说明，指向决定与 `reconciliation.pdma.json`；历史内容未改 |
| `requirements-site/content/requirements.ts` | FIN-11 按决定改写（不写 Journal、发事件交 billing）；FIN-12、FIN-24、E2E-08 前置一句取代说明（原文被测试正则钉住，未删）；FIN-I20 去掉「受控原始文件」，改为只存 SHA-256 与解析行 |
| `design-discuss/reconciliation/design.md` | 状态行追加「切片 1 已进入实施（阶段 4）」及需求链接 |
| `design-discuss/README.md` | reconciliation 行状态改为「已确认，切片 1 实施中（阶段 4）」 |

## 2. 验证

| 命令 | 结果 |
|---|---|
| 用 `reconModel.ts` 的 `parseChannelStatementCsv` / `parseBankStatementCsv` 解析两个示例（scratchpad 脚本，未改 src） | 两份均 0 条问题；渠道账单 3 行、rawAttributes 符合文档；服务端恒等式手算 68.00 / 0.45 / 67.55 成立；银行 1000.00 + 67.40 = 1067.40 |
| `node --experimental-strip-types --test src/reconciliation/reconModel.test.ts` | 12 / 12 通过 |
| `npm --prefix requirements-site test`（改动前基线） | 137 例：127 通过、10 失败 |
| 同上（改动后） | 137 例：127 通过、10 失败；失败用例名与基线逐条相同（Commerce Client API / 隐私、Payment refund、Project context、shared writing conventions、Java tests organized、台账快照两例等，均与本线无关的既有失败） |

## 3. 实现与文档的出入（请实现线确认）

1. **银行对账单服务端尚未实现。** 写作时 core 只有银行对账单的 Repository / Mapper，没有开始 / 追加 / 完成 Processor（C 线进行中）。`bank-statement-csv.md` 第 4–6 节按 `BankStatementApi` 合同与 technique 写，C 线完成后需核对拒绝原因措辞、是否校验 `value_date` 落在期间内。
2. **银行流水金额：** Web 要求 `amount > 0`，合同注释写「正数」，但 `AppendBankStatementLinesRequestBody.amount` 的正则允许 `0`；C 线应在服务端拒绝 0。
3. **交易行方向只在服务端校验：** `PAYMENT` 须 `CREDIT`、`REFUND` / `CHARGEBACK` 须 `DEBIT`，浏览器不拦，完成导入时才拒。文档已写明，可考虑页面提前提示。
4. **币种精度只在服务端校验：** 浏览器只校验最多 8 位小数，USD 超过 2 位要到开始导入 / 追加时才 400。
5. **其他列列名截断：** Web 用 `name.slice(0, 64)` 截断超长列名，服务端约束是 ≤ 64；不会被拒，但两个前 64 字符相同的长列名会互相覆盖。影响极小，文档写的是「列名最长 64 字符」。
6. **requirements-site FIN-12 / FIN-24 / E2E-08 与 `global-use-case-sequences.mjs` 的 E2E-08 序列仍是旧 Journal 口径，**被 `site-content.test.mjs`、`global-use-case-sequences.test.mjs` 的正则钉住；本线只加了取代说明。彻底改写需同时改这些测试与序列目录，建议另开一项。

## 4. 后续（C 线完成后对齐，2026-10-20）

依据 C 报告 §7、§8 与 technique I-15–I-18，并逐个核对 `StartBankStatementImportProcessor`、`AppendBankStatementLinesProcessor`、`CompleteBankStatementImportProcessor`（只读）。

| 文件 | 改动 |
|---|---|
| `paytech/reconciliation/docs/formats/bank-statement-csv.md` | 流水列：交易号与已导入流水或本文件重复 → 整单拒绝，起息日必须在期间内，金额 > 0 且不超过币种精度；第 4 节改为四项整单校验（行数 = 入库 + 重复、无重复时核对余额恒等式、交易号不重复、起息日在期间内）及连续性的准确条件（上一张最晚已导入的期末 = 本张期初，且上一张期间结束日次日 = 本张开始日）；第 5 节补 `duplicateLineCount` 含义与被拒后同编号重传（I-16）；第 6 节换成服务端原文拒绝文本（开始 / 追加 / 完成） |
| `paytech/reconciliation/README.md` | 银行对账一行补充整单拒绝条件、同编号重传、`BANK_STATEMENT_GAP` |
| `channel-statement-csv.md` | 不受影响，未改 |

第 3 节出入的处理：第 1、2 条已解决（服务端已实现，追加时拒绝金额 0；`AppendBankStatementLinesRequestBody.amount` 的正则仍允许 0，由 Processor 拒绝，合同注释与正则不一致可在后续顺手收紧）。第 3–6 条不变。浏览器仍不检查起息日是否在期间内、交易号是否重复，格式说明已写明这两项由服务端完成导入时判定。

未再跑测试（只改文档）。
