# ledger 会计科目分级与维护：技术方案

状态：**阶段 2 已确认；阶段 3 按用户连续执行授权完成（2026-10-19）**。需求见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)。

本文只有第 1 节（现状差异与影响分析）；方案、接口与数据设计在阶段 3 补充为第 2 节。下文路径相对仓库根；标 **[M]** 的文件在工作区中有 billing 重构需求未提交的改动。

## 1. 现状差异与影响分析

### 1.1 总体结论

- 科目表现在是**平铺的**：STANDARD 29 个科目全部无上级、全部可记账；Java 代码从不读 `parent_account_id`，科目表没有任何写入路径（只有测试 Mapper 写），"被账簿采用后冻结"只写在文档和列注释里，代码没有执行。
- `account_code`（如 `CLIENT_PAYABLE`）是 ledger 规则行、account 账户类型目录、`AccountPosted` 事件和控制台共同使用的业务键，**必须保持不变**；科目编号只能另加一列。
- reconciliation、payout、tax、requirements-site 只在注释或文字中提到科目代码，没有代码依赖；保持代码不变后它们**不受影响**。
- 现行权威文档有 3 处与本需求冲突，需要在阶段 3 用一份决定记录修订（见 1.5）。
- 同日 billing 计费事实重构需求正在阶段 4，工作区 ledger 65 个、account 53 个文件未提交。经核对它**不改科目、账户、试算平衡和控制台**，但有若干文件与本需求重叠（见 1.6），本需求的研发应排在它之后。

### 1.2 差异表

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| R1 科目编号与级次 | `t_chart_account` 无编号、无级次列（`paytech/ledger/docs/database/sql/ledger-schema.sql:30-44`）；控制台按 `account_code` 排序 | 每个科目有 4-2-2-2 数字编号，下级以上级为前缀 | 未实现 | 新增编号列与唯一索引；级次由编号长度推出，不另存 |
| R1 树结构 | `parent_account_id` 有列有索引（`idx_chart_acct_parent`），全部为空；无代码读取 | 父子树，父科目不可记账 | 未实现 | 沿用 `parent_account_id`，应用层校验同表、无环、父不可记账（`FINANCE_PHYSICAL_DECISIONS.md:493` 已规定） |
| R2 STANDARD 重建 | `ledger-initial-data.sql:21-49` 29 个平铺科目，ID 3001–3029 | 补一级 / 二级汇总科目，29 个挂成末级 | 数据缺失 | 新的初始化 DML + 增量 SQL；已有账簿的环境先清理再执行（见 1.7） |
| R3 科目维护 | 无写接口；`ChartAccountMapper` 只读（`ChartAccountMapper.java`） | 新增、改名称、调上级 / 编号（未采用时）、停用 / 启用 | 未实现 | ledger 新增维护接口与 Processor；冻结规则首次在代码里执行 |
| R3 采用后冻结 | 只在文档与列注释；`CreateLedgerBookProcessor.java:76-82,103` 只要求科目表 ACTIVE | 采用后既有结构字段（含新编号）冻结 | 未执行 | 维护 Processor 判断"是否已有账簿采用"并拒绝结构修改 |
| R4 变更留痕 | ledger 无操作日志表；控制器只写 `LOG.info`（`LedgerBookController.java:43-48`） | 每次变更记录操作人、时间、变更前后 | 未实现 | ledger 新增本地科目变更记录表（31 → 32 表），不接 Platform 审计 |
| R3/AC-06 停用 | 科目无状态列；只有账户级 `account_status`（`ledger-schema.sql:84`）；记账校验只看 `posting_allowed`（`CreateAccountProcessor.java:68`、`CreateRuleVersionProcessor.java:164` **[M]**、`TransactionPostingServiceImpl.java:522` **[M]**） | 停用科目不能新建账户、不能被新规则引用 | 未实现，且与现行文档冲突 | 新增科目状态列；在上述三处加校验（见 1.5 C1、1.8 待定 D2） |
| R5 控制台科目树 | `SetupChartsTab.tsx:10-61` 只读平铺表；`:59` 写着"维护后续批次提供" | 树展示、维护、变更记录、编号 + 名称 | 未实现 | 改造该页签；建账户弹窗（`FinanceModals.tsx:181-213`）改为按编号 + 名称选择末级科目 |
| R6 科目余额级次汇总 | ledger 有账户级余额（`LedgerBookQueryApi:57`），无按科目汇总接口；`t_ledger_period_balance` 只写不读 | 账簿按科目、按级次汇总的余额 | 未实现 | 查询时按树向上汇总；不改存储 |
| R6 试算平衡级次汇总 | `TrialBalanceServiceImpl.java:87-176` 按末级科目一行；`TrialBalanceContentHash.java` 对行做哈希，关账审批用该哈希；BFF / Web 都没有试算平衡入口 | 试算平衡按级次展开 / 折叠，父科目 = 下级合计 | 未实现；控制台入口也没有 | **查询时汇总**，不把汇总行写进 `t_trial_balance_line`，以免改变内容哈希和关账审批；BFF / Web 新增试算平衡查询 |
| R8 ledger 可读账户号 | `t_ledger_account` 无账户号；三条建户路径都用雪花 ID（手工 `CreateAccountProcessor:75-89`、记账自动建户 `TransactionPostingServiceImpl:605-619` **[M]**、关账建户 `PeriodCloseServiceImpl:162-175`） | 建户时生成、不再改变、唯一、可搜索 | 未实现 | 新增列 + 唯一索引；三条路径统一生成；格式阶段 3 定 |
| R8 account 可读账户号 | `t_owner_account` 无账户号；只在 `AccountPostingWriter.resolveAccount`（`:103-132` **[M]**）按需建户；account 不依赖 ledger，不知道科目编号 | 同上 | 未实现；account 拿不到科目编号 | 把科目编号加进 account-api 的账户类型目录（`OwnerAccountType`），由 ledger 测试核对与科目表一致（见 1.5 C3） |
| R8 控制台显示与搜索 | 各列表按 `accountCode` 显示（`SetupBooksTab.tsx:93-94`、`SubledgerAccounts.tsx:69-70`、`JournalEntryDrawer.tsx:64`）；账户筛选只有科目代码 | 显示账户号，可按账户号精确搜索 | 未实现 | ledger、account 列表接口加 `accountNo` 筛选；BFF 放行新查询参数；Web 加列和搜索框 |
| R7 测试环境数据 | mor-test AE001 已采用 STANDARD（演示数据，`local-environment/e2e/runtime/finance-seed-20261019221825.json`） | 清理后按新科目表重建 | — | 阶段 6 列具体清理与重建步骤；本机 `mor_ledger` 同样需要处理 |

### 1.3 逐系统影响

| 系统 | 业务职责 | 影响点 | 代码 / 模型 / 配置 | 上下游依赖与兼容风险 |
|---|---|---|---|---|
| ledger | 科目表、账簿、账户、分录、试算平衡的 Owner | 科目表加编号、状态；科目维护接口 + 变更记录表；三条建户路径生成账户号；停用校验；科目余额与试算平衡的级次汇总查询；错误码 | 改代码、改 PDManer（手工维护，`scripts/pdmaner/ledger-model.test.mjs` 断言 31 张表要改为 32）、Schema SQL 由 JSON 生成并逐字节校验、测试 DDL（`paytech-ledger-test/src/test/resources/init/ddl.sql`）、初始化 DML、增量 SQL | 新增响应字段会让 BFF 严格解析失败（见 operations BFF 行）；与 billing 重构重叠的文件见 1.6 |
| account | Owner 明细账账户 Owner | `t_owner_account` 加账户号；`OwnerAccountType` 目录加科目编号；列表接口加账户号返回与筛选 | 改代码、改 PDManer（`paytech/account/docs/database/account.pdma.json`）、Schema、测试 DDL、增量 SQL（含存量账户号回填） | `AccountPosted` 载荷**不加**账户号；`AccountPostingWriter` **[M]** 与 billing 重构重叠 |
| operations-console-bff | 财务页面转发 | 新增科目维护、变更记录、科目余额、试算平衡转发；模型类加新字段；新查询参数放行；新错误码登记 | 改代码与测试 | `FinanceContractConverter.java:22-38` 开启 `FAIL_ON_UNKNOWN_PROPERTIES`：ledger / account 先加字段而 BFF 未更新时返回 502，**必须 BFF 先发或同发** |
| operations-console-web | 财务页面 | 科目表页签改树 + 维护 + 变更记录；建账户弹窗；各账户列表加账户号列与搜索；新增科目余额 / 试算平衡页面或页签 | 改代码与 `finance.test.ts`（`:38-44` 页面清单、`:151` 错误码数量 15） | 解码器不拒绝多余字段，新字段对 Web 安全 |
| platform-support | 路由、权限、菜单注册 | 新增 POST / GET 路由、查询参数与 Guard 行；可能新增菜单（试算平衡）；维护权限沿用或新增（待定 D4） | 只改注册 DML（唯一执行来源 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`），新块号建议用 91 起的空闲块 | 无代码改动 |
| test-environment | mor-test | 清理 AE001 演示数据、执行 ledger / account / platform SQL、替换 4 个镜像、重建演示数据 | 发布阶段的 SQL 与 Compose 覆盖文件 | 需与 billing 重构的 mor-test 发布错开（它会替换 ledger、account 镜像） |
| reconciliation、payout、tax | 引用科目代码 | 无（只在注释中出现） | 不改 | 无 |
| requirements-site | 需求与全局时序目录 | 更新 FIN-22（试算平衡级次汇总）、FIN-I05（按账户号查流水）、`:562` 科目规则描述；新增科目维护用例 | 改内容与测试 | `requirements.ts`、`global-use-case-sequences.mjs` 当前也被 billing 重构改动 **[M]** |
| 文档 | 规则 Owner | `FINANCE_DATABASE.md` §4.1、`FINANCE_PHYSICAL_DECISIONS.md` §4.11、新决定记录、`CURRENT_IMPLEMENTATION.md` | 改文档 | 两份数据库文档当前有 billing 重构未提交的改动（不涉及科目章节） |

### 1.4 已核实的设计约束

- 科目树只用 `parent_account_id` 表达，汇总用递归查询或应用层遍历，不建闭包表（`agent-docs/database/FINANCE_DATABASE.md:146`）。
- 父子同表、不成环、父科目必须不可记账（`FINANCE_PHYSICAL_DECISIONS.md:493`），并要求在本机 MySQL 上用真实测试证明。
- 采用后 `account_code`、上级、类别、余额方向、`posting_allowed` 冻结；`account_name` 不在冻结列表，可以改名（`FINANCE_DATABASE.md:139`）。
- 双人复核对象清单（`FINANCE_DATABASE.md:56,813`、`FINANCE_PHYSICAL_DECISIONS.md:470-475`）不含科目表；用户选择"直接生效"与现行规则**不冲突**，财务主体也是同样的直接维护先例（`FINANCE_DATABASE.md:137`）。
- 试算平衡只持久化末级科目，父级在查询时汇总（`FINANCE_DATABASE.md:495-496,519`），与 R6 的查询时汇总一致。
- 写接口按 [开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)与[写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)：单个 Processor + 单个 Context、`TransactionTemplate`、事务内不调外部、锁真实资源（可沿用 `AccountingLocks` 的科目表锁模式）、条件更新校验行数；阶段 3 按示例第 11 节 13 项逐条设计。

### 1.5 与现行权威文档的冲突（阶段 3 用决定记录修订）

| 编号 | 现行规则 | 本需求要求 | 建议 |
|---|---|---|---|
| C1 | 科目没有状态；"`posting_allowed=false` 是永久结构属性，不承担启停，停用通过账户 `account_status` 完成"（`FINANCE_DATABASE.md:146`、`FINANCE_PHYSICAL_DECISIONS.md:284-290`） | 科目可停用 / 启用（Q4） | 保留 `posting_allowed` 永久不变的规则；**另加**科目状态列承担启停。只修订"科目没有状态"这一句 |
| C2 | 冻结字段列表不含科目编号（因为还没有这一列） | 编号采用后不可改（AC-05） | 把编号加入冻结列表 |
| C3 | account 自持账户类型目录，"按需开户，运行时不读 ledger 科目"（`agent-docs/decisions/2026-10-18-account-ledger-subledger-control-account.md:29`） | account 账户号要包含科目编号 | 不改变方向：科目编号作为静态数据加进 account-api 目录，ledger 在测试中核对与科目表一致，**运行时仍不读 ledger**；决定记录补充一句 |

### 1.6 与 billing 计费事实重构的重叠

- 该需求阶段 1—3 已确认、阶段 4 进行中；ledger / account **无 DDL / DML 变更**，只做改名（EconomicFact → BillingFact）和枚举搬迁；计划 mor-test 替换 4 个镜像（含 ledger、account）。
- 本需求会改到的、它也改过的文件：ledger `CreateRuleVersionProcessor`、`TransactionPostingServiceImpl`、`AccountingRuleLine`、`LedgerPersistenceConfiguration`、`README.md`；测试 `MockTestBase`、`AccountingTestMapper`、`CreateRuleVersionValidationTest`、`AbstractTransactionPostingTest`；account `AccountPostingWriter`、`account-core/pom.xml`；文档 `FINANCE_DATABASE.md`、`FINANCE_PHYSICAL_DECISIONS.md`、`CURRENT_IMPLEMENTATION.md`、`requirements-site` 两份目录。
- **建议顺序：** 本需求阶段 3 只写文档与 SQL 文件，现在可以进行；阶段 4 研发等 billing 重构提交（其阶段 8）后在其提交之上开始；mor-test 发布排在它之后。

### 1.7 历史数据与环境

- **mor-test：** AE001 账簿已采用 STANDARD。用户已同意清理；阶段 6 给出清理范围（ledger / account / billing 三库中本次演示数据）、备份、执行 SQL 顺序与重建步骤。
- **本机 `mor_ledger` / `mor_account`：** 测试只清理 `id >= 1000000` 的行，固定 ID 的科目表可直接用增量 SQL 重建；阶段 3 确认本机是否残留采用了 STANDARD 的账簿。
- **重建方式：** 29 个既有科目 ID（3001–3029）与 `account_code` 不变，只补编号、上级并新增汇总科目，这样已有规则行和 account 目录都不用迁移。

### 1.8 阶段 3 需要定的事项

| 编号 | 事项 | 倾向 |
|---|---|---|
| D1 | 账户号格式（ledger、account 两类） | 主体 + 币种 + 科目编号 + 序号，例如 `AE001-USD-220101-0001`；序号按"账簿 + 科目"递增，参照 `t_book_sequence` 的加锁递增方式；余额桶不进账户号，由序号区分 |
| D2 | 科目停用的影响范围 | 不能新建账户、不能被新规则版本引用；已有账户照常记账与查询；父科目只有在下级全部停用后才能停用，不自动级联；启用下级时上级必须是启用状态 |
| D3 | 级次汇总接口的形态 | ledger 提供"账簿科目余额（按期间）"和"试算平衡明细"两个查询，返回树形或带层级的平铺行 |
| D4 | 科目维护的权限 | 沿用现有 `FINANCE_ACCOUNTING_MANAGE`（财务所有写操作已用它），不新增权限 |
| D5 | 控制台试算平衡入口 | 放在「账本查询」下新增页签，不新增一级菜单 |

### 1.9 工作量依据（粗估）

- ledger：Schema 两表加列、一张新表；4 个写接口（新增、修改、停用、启用）+ 2—3 个查询；三条建户路径；三处停用校验；约 6 个现有测试类需要调整，新增维护、汇总、账户号测试。
- account：一表加列与回填；目录加编号；列表接口加筛选。
- BFF：约 6—8 个转发、模型字段、错误码登记、对应自动测试。
- Web：科目树页签、维护弹窗、变更记录、账户号列与搜索（4 个页面）、试算平衡 / 科目余额页签。
- Platform：一个注册块（约 6—8 条路由及其 Header、查询参数、Guard 行）。

## 2. 方案、接口与数据设计（阶段 3 评审稿）

**规模（本次方案）：** 涉及 5 个系统（ledger、account、Operations BFF、Operations Web、Platform 注册数据）；ledger 新增 6 个接口、调整 6 个既有接口的返回或筛选（ledger 4 个、account 2 个），BFF 新增 8 条转发（6 个新接口 + 2 个既有试算平衡查询接口首次接入控制台）；新增 3 张表、28 个字段，修改 3 张旧表、新增 4 个字段与 3 个唯一索引；业务 DML 新增 19 行、更新 29 行，存量账户号回填按环境实际行数；Platform 注册 1 个块 96 行（8 条路由及其 Header、查询参数、Guard 行，另给 3 条既有路由补查询参数）；页面改造 1 个页签、新增 1 个页签、4 个账户列表加列与搜索。

决定依据：[2026-10-19 科目分级与可读账户号决定](../../../agent-docs/decisions/2026-10-19-ledger-chart-hierarchy-and-account-numbers.md)（Proposed，随本阶段确认）。

### 2.1 STANDARD 科目树（R1、R2）

**2026-10-20 用户变更：按常见财务系统改为三级**（一级 4 位、二级 6 位、三级 8 位；三级为末级可记账科目）。一级 19 个汇总科目（ID 3101—3119）编号不变；新增二级 26 个汇总科目（ID 3201—3226）；29 个既有科目 ID 3001—3029 与 `account_code` 不变，改为三级末级科目。共 74 个科目。

| 一级 | 二级（ID · 编号 · 代码 · 名称） | 三级末级（编号 · 代码） |
|---|---|---|
| 1001 现金及银行存款 `CASH_AND_BANK` | 3201 · 100101 · `BANK_DEPOSITS` · 银行存款 | 10010101 `BANK_CASH` |
| 1101 渠道应收款 `CHANNEL_RECEIVABLES` | 3202 · 110101 · `CHANNEL_CLEARING_RECEIVABLES` · 渠道清算应收 | 11010101 `CHANNEL_UNSETTLED_RECEIVABLE` · 11010102 `CHANNEL_SETTLEMENT_RECEIVABLE` |
| 1131 其他应收款 `OTHER_RECEIVABLES` | 3203 · 113101 · `RECOVERY_RECEIVABLES` · 追偿应收 | 11310101 `CLIENT_RECOVERY_RECEIVABLE` · 11310102 `PARTNER_RECOVERY_RECEIVABLE` |
| | 3204 · 113102 · `INTERCOMPANY_RECEIVABLES` · 关联方应收 | 11310201 `PLATFORM_SERVICE_RECEIVABLE` |
| 1171 应收税费 `TAX_RECEIVABLES` | 3205 · 117101 · `INPUT_TAX` · 进项税额 | 11710101 `TAX_RECOVERABLE` |
| 1901 待处理资产 `SUSPENSE_ASSETS` | 3206 · 190101 · `RECON_SUSPENSE` · 对账待处理 | 19010101 `RECON_SUSPENSE_ASSET` |
| 2201 应付客户款项 `CUSTOMER_FUNDS_PAYABLE` | 3207 · 220101 · `CLIENT_FUNDS_PAYABLE` · 应付 Client 款项 | 22010101 `CLIENT_PAYABLE` |
| | 3208 · 220102 · `PARTNER_FUNDS_PAYABLE` · 应付合作方款项 | 22010201 `PARTNER_PAYABLE` |
| | 3209 · 220103 · `CONSUMER_FUNDS_PAYABLE` · 应付 Consumer 款项 | 22010301 `CONSUMER_COMPENSATION_PAYABLE` |
| 2211 客户保证金 `CUSTOMER_DEPOSITS` | 3210 · 221101 · `CLIENT_DEPOSITS` · Client 保证金 | 22110101 `CLIENT_SECURITY_DEPOSIT` |
| 2221 应交税费 `TAXES_PAYABLE` | 3211 · 222101 · `OUTPUT_TAX` · 销项税额 | 22210101 `TAX_PAYABLE` |
| 2231 渠道应付款 `CHANNEL_PAYABLES` | 3212 · 223101 · `CHANNEL_CLEARING_PAYABLES` · 渠道清算应付 | 22310101 `CHANNEL_UNSETTLED_PAYABLE` · 22310102 `CHANNEL_SETTLEMENT_PAYABLE` |
| 2241 预提费用 `ACCRUED_EXPENSES` | 3213 · 224101 · `CHANNEL_FEE_ACCRUALS` · 渠道费用预提 | 22410101 `CHANNEL_FEE_ACCRUAL` |
| 2251 其他应付款 `OTHER_PAYABLES` | 3214 · 225101 · `INTERCOMPANY_PAYABLES` · 关联方应付 | 22510101 `PLATFORM_SERVICE_PAYABLE` |
| 2901 待处理负债 `SUSPENSE_LIABILITIES` | 3215 · 290101 · `UNIDENTIFIED_RECEIPTS` · 待识别款项 | 29010101 `UNIDENTIFIED_CASH` |
| 3001 实收资本 `SHARE_CAPITAL` | 3216 · 300101 · `ORDINARY_CAPITAL` · 普通股本 | 30010101 `CAPITAL` |
| 3101 留存收益 `RETAINED_EARNINGS_TOTAL` | 3217 · 310101 · `UNDISTRIBUTED_PROFIT` · 未分配利润 | 31010101 `RETAINED_EARNINGS` |
| 3103 本期利润 `PROFIT_FOR_PERIOD` | 3218 · 310301 · `PERIOD_RESULT` · 本期损益 | 31030101 `CURRENT_PERIOD_RESULT` |
| 3201 所有者分配 `DISTRIBUTIONS`（借） | 3219 · 320101 · `OWNER_DISTRIBUTIONS` · 股东分配（借） | 32010101 `OWNER_DISTRIBUTION` |
| 4001 营业收入 `OPERATING_REVENUE` | 3220 · 400101 · `TRANSACTION_SERVICE_REVENUE` · 交易服务收入 | 40010101 `CLIENT_FEE_REVENUE` |
| | 3221 · 400102 · `PLATFORM_SERVICE_INCOME` · 平台服务收入 | 40010201 `PLATFORM_SERVICE_REVENUE` |
| 5001 营业成本 `COST_OF_SERVICES` | 3222 · 500101 · `CHANNEL_COSTS` · 渠道成本 | 50010101 `CHANNEL_FEE_EXPENSE` |
| | 3223 · 500102 · `PARTNER_COSTS` · 合作方分成 | 50010201 `PARTNER_SHARE_EXPENSE` |
| | 3224 · 500103 · `PLATFORM_SERVICE_COSTS` · 平台服务费用 | 50010301 `PLATFORM_SERVICE_FEE_EXPENSE` |
| 5101 其他损失 `OTHER_LOSSES` | 3225 · 510101 · `ROUNDING_DIFFERENCES` · 舍入差异 | 51010101 `ROUNDING_ADJUSTMENT` |
| | 3226 · 510102 · `RECONCILIATION_LOSSES` · 对账差异损失 | 51010201 `RECON_ADJUSTMENT_EXPENSE` |

二级与三级的类别、余额方向同其一级（3201 / 3219 为借）。account 账户类型目录编号：`CLIENT_PAYABLE` 22010101、`PARTNER_PAYABLE` 22010201、`CLIENT_SECURITY_DEPOSIT` 22110101、`CLIENT_RECOVERY_RECEIVABLE` 11310101、`PARTNER_RECOVERY_RECEIVABLE` 11310102、`BANK_CASH` 10010101。账户号示例相应变为 `AE001-USD-22010101-0001` 与 `AE001-USD-22010101-S000001`。

### 2.2 业务规则与主流程

**编号校验（纯规则，ledger `ChartAccountNoRule`）：** 4、6、8、10 位数字；一级首位 1—5 且与类别一致；有上级时长度 = 上级长度 + 2 且以上级编号开头；无上级时必须是 4 位。

**新增科目：**
1. 基础校验（Body 注解）；科目表存在且 ACTIVE。
2. 取科目表资源锁（`AccountingLocks.chart(chartId)`，`LOCK`）；同一把锁也由新建账簿（采用科目表）使用。
3. 上级（按 `parentAccountNo` 查）必须存在、`posting_allowed = 0`、ACTIVE、类别相同；无上级时只能新增 4 位一级科目（采用后是否允许见 2.9 E1，暂按允许设计）。
4. 编号与代码在科目表内唯一（唯一索引兜底，冲突转 `CHART_ACCOUNT_NO_DUPLICATE` / `CHART_ACCOUNT_CODE_DUPLICATE`）。
5. 一个事务：插入科目（ACTIVE）+ 插入 `CREATE` 变更记录。

**修改科目：** 请求给出 `accountName`、`accountNo`、`parentAccountNo`（可空）。
- 名称任何时候可改。
- 编号或上级有变化时：科目表已被任一账簿采用 → `CHART_STRUCTURE_FROZEN`；科目有下级 → 拒绝（`CHART_PARENT_INVALID`，先移动下级）；新上级校验同新增，且不能是自己或自己的下级（无环）。
- 无任何变化 → 直接返回，不写变更记录。
- 一个事务：条件更新（`WHERE id = ? AND chart_id = ?`，校验行数）+ `UPDATE` 变更记录。

**停用：** 已停用直接返回。末级科目被当前生效规则版本的规则行、`t_close_account_rule`、account 账户类型目录（`StandardAccountCodes`）引用 → `CHART_ACCOUNT_IN_USE`；汇总科目存在 ACTIVE 下级 → `CHART_ACCOUNT_IN_USE`。条件更新 `account_status = 'ACTIVE'` → `INACTIVE` + `DISABLE` 变更记录。

**启用：** 已启用直接返回；上级必须 ACTIVE（否则 `CHART_PARENT_INVALID`）。条件更新 + `ENABLE` 变更记录。

**停用的生效点：** 手工建账户（`CreateAccountProcessor`）、记账自动建户（`TransactionPostingServiceImpl.resolveAccount` 中新建分支）、关账建户（`PeriodCloseServiceImpl.ensureResultAccount` 新建分支）、新规则版本校验（`CreateRuleVersionProcessor.checkPostingAccount`）四处拒绝 INACTIVE 科目；已有账户的记账、查询不检查科目状态。

**采用冻结在代码中执行：** "已采用" = 存在 `t_ledger_book.chart_id = 本科目表`。`CreateLedgerBookProcessor` 改为 `AbstractLockProcessor` 并取同一把科目表锁，使"检查未采用 → 修改结构"与"新建账簿"互斥；账簿唯一键保护不变。

**可读账户号生成（R8）：**
- ledger：三条建户路径在插入账户的同一事务里，先对 `t_account_no_sequence (ledger_book_id, chart_account_id)` 执行 `INSERT … ON DUPLICATE KEY UPDATE last_value = LAST_INSERT_ID(last_value + 1)` 取得序号，再按 `{主体段}-{币种}-{科目编号}-{序号补零到 4 位}` 写入 `account_no`。主体段：OE 为 `entityNo`，FE 为 `t_finance_entity.entity_code`（建账簿时已加载）。该序列行锁只串行"同账簿同科目的新开户"，不影响已有账户记账。记账建户遇唯一键冲突时整笔回滚、由原有重试路径重来，序号空洞允许。
- account：`AccountPostingWriter.resolveAccount` 新建分支同样先取 `t_owner_account_no_sequence (entity_type, entity_id, currency, account_code)` 序号，生成 `{主体段}-{币种}-{科目编号}-S{序号补零到 6 位}`；科目编号取自 `OwnerAccountType.chartAccountNo`；FE 主体段为 `FE{entity_id}`。
- 两边的 `account_no` 都有表内唯一索引；生成后任何接口都不修改。

**级次汇总（R6）：**
- 末级金额沿用 `JournalLineRepository.findTrialAggregates` 的按科目聚合（期初 = 期间开始前全部分录，本期借贷 = 期间内分录）。
- 汇总在内存中按 `parent_account_id` 自下而上累加借、贷发生额与期初 / 期末的借方、贷方部分，再按该节点自身余额方向表达期初与期末余额；一级科目合计借贷平衡。
- 试算平衡运行、持久化与 `TrialBalanceContentHash` 不变，只在详情查询结果中附加编号、上级、级次与汇总行。

### 2.3 接口

ledger 接口沿用现有 `/v1` 路径与 Gateway Header 约定；写接口读取 `X-Gateway-Actor-Subject` 作为变更记录操作人（现有接口只记日志，本次新增接口开始落库）。

**新增（ledger）**

| # | Method / Path | 说明 | 权限 |
|---|---|---|---|
| L1 | `POST /chart-of-accounts/v1/{chartCode}/accounts` | 新增科目；201 返回 `{chartAccountId}` | MANAGE |
| L2 | `POST /chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/update` | 修改名称 / 编号 / 上级；200 返回科目 | MANAGE |
| L3 | `POST /chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/disable` | 停用；200 返回科目 | MANAGE |
| L4 | `POST /chart-of-accounts/v1/{chartCode}/accounts/{chartAccountId}/enable` | 启用；200 返回科目 | MANAGE |
| L5 | `GET /chart-of-accounts/v1/{chartCode}/changes?chartAccountId=&page=&size=` | 变更记录，按时间倒序分页 | READ |
| L6 | `GET /ledger-books/v1/{ledgerBookId}/periods/{periodId}/account-balances` | 科目余额（末级 + 汇总行，按编号排序） | READ |

L1 请求：

```json
{"accountNo": "220104", "accountCode": "MERCHANT_RESERVE_PAYABLE", "accountName": "Client 风险准备金",
 "accountClass": "LIABILITY", "normalSide": "CREDIT", "postingAllowed": true, "parentAccountNo": "2201"}
```

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| accountNo | string | 是 | `[1-9][0-9]{3}([0-9]{2}){0,3}` + 2.2 编号规则 |
| accountCode | string | 是 | `[A-Z][A-Z0-9_]{0,63}`，科目表内唯一 |
| accountName | string | 是 | 1—255 字 |
| accountClass | string | 是 | ASSET / LIABILITY / EQUITY / REVENUE / EXPENSE，与上级相同 |
| normalSide | string | 是 | DEBIT / CREDIT |
| postingAllowed | boolean | 是 | 创建后不可改 |
| parentAccountNo | string | 否 | 为空表示一级科目 |

L2 请求：`{"accountName": "…", "accountNo": "…", "parentAccountNo": "…"|null}`，三项都必填语义（`parentAccountNo` 可显式为 null）。L3 / L4 无请求体。

科目响应（L2—L4 与既有科目列表共用 `ChartAccountResponseBody`，新增字段加粗）：`chartAccountId`、`chartId`、**`accountNo`**、`accountCode`、`accountName`、`accountClass`、`normalSide`、`postingAllowed`、**`parentChartAccountId`（可空）**、**`level`（1—4）**、**`accountStatus`**。

L5 元素 `ChartAccountChangeResponseBody`：`changeId`、`chartAccountId`、`accountNo`（变更后）、`changeType`（CREATE / UPDATE / DISABLE / ENABLE）、`beforeAccountNo`、`afterAccountNo`、`beforeParentAccountNo`、`afterParentAccountNo`、`beforeAccountName`、`afterAccountName`、`beforeAccountStatus`、`afterAccountStatus`、`operatorId`、`changeTime`。

L6 响应 `AccountBalanceListResponseBody`：`ledgerBookId`、`periodId`、`currency`、`lines[]`；行字段 `chartAccountId`、`accountNo`、`accountCode`、`accountName`、`accountClass`、`normalSide`、`parentChartAccountId`、`level`、`postingAllowed`、`openingBalance`、`periodDebit`、`periodCredit`、`closingBalance`（金额为十进制字符串，按账簿币种精度）。只返回有发生额或余额的末级科目及其各级上级。

**调整（既有接口，只增字段 / 可选筛选，兼容旧调用方）**

| # | 接口 | 变化 |
|---|---|---|
| M1 | ledger `GET /chart-of-accounts/v1/{chartCode}/accounts` | 响应加上述 4 个字段；新增可选筛选 `accountStatus`；排序由 `account_code` 改为 `account_no` |
| M2 | ledger `GET /ledger-books/v1/{id}/accounts` | 响应加 `accountNo`、`accountName`；新增可选精确筛选 `accountNo` |
| M3 | ledger `GET /ledger-books/v1/{id}/journal-entries/{journalEntryId}` | 行加 `accountNo`、`accountName` |
| M4 | ledger `GET …/trial-balances/{runNo}` | `lines[]` 加 `accountNo`、`parentChartAccountId`、`level`；新增 `summaryLines[]`（汇总行，字段同 lines，`postingAllowed=false`） |
| M5 | account `GET /owner-accounts/v1` | 响应加 `accountNo`；新增可选精确筛选 `accountNo` |
| M6 | account `GET /account-postings/v1/{postingId}` | 流水行加 `accountNo` |

`AccountPosted` 事件载荷、BalanceQueryApi、其余接口不变。

**错误码（`LedgerSetupErrorCode` 新增 8 个；消费者 Operations Web 按 Code 落到表单字段或页面提示）：**

| Code | 默认消息 | Web 落点 |
|---|---|---|
| `CHART_ACCOUNT_NOT_FOUND` | 科目不存在 | 页面提示 |
| `CHART_ACCOUNT_NO_INVALID` | 科目编号不符合分级规则 | accountNo |
| `CHART_ACCOUNT_NO_DUPLICATE` | 科目编号已存在 | accountNo |
| `CHART_ACCOUNT_CODE_DUPLICATE` | 科目代码已存在 | accountCode |
| `CHART_PARENT_INVALID` | 上级科目不存在、不是汇总科目、已停用、类别不同或会形成循环 | parentAccountNo |
| `CHART_STRUCTURE_FROZEN` | 科目表已被账簿采用，不能修改编号或上级 | 页面提示 |
| `CHART_ACCOUNT_IN_USE` | 科目仍被规则、结转规则、账户类型目录或启用中的下级引用，不能停用 | 页面提示 |
| `CHART_ACCOUNT_INACTIVE` | 科目已停用 | 建账户表单 accountCode |

**BFF（Operations）新增转发：** L1—L6 各一条，以及既有 `TrialBalanceApi` 的列表、详情 2 条（运行不接入，见 E2）（路径前缀 `/finance`，与现有转发一致）；M1—M6 的 BFF 模型同步加字段，`accountNo`、`accountStatus` 加入查询参数白名单；`FinanceClientConfiguration` 注册新错误码。BFF 不做聚合，全部为单上游转发。

### 2.4 数据库

文件：[db/ledger/ddl.sql](./db/ledger/ddl.sql)、[db/ledger/dml.sql](./db/ledger/dml.sql)、[db/account/ddl.sql](./db/account/ddl.sql)、[db/platform-support/registration-dml.sql](./db/platform-support/registration-dml.sql)。均为**待执行文件**，执行授权在阶段 5（本机）与阶段 6（mor-test）分别申请。

| 库 | 表 | 变化 |
|---|---|---|
| mor_ledger | `t_chart_account` | 加 `account_no VARCHAR(10)`、`account_status VARCHAR(16)`；加 `uk_chart_acct_no (chart_id, account_no)` |
| mor_ledger | `t_ledger_account` | 加 `account_no VARCHAR(48)`；加 `uk_ledger_account_no (account_no)`；存量回填 |
| mor_ledger | `t_chart_account_change`（新） | 14 字段：变更记录，只追加 |
| mor_ledger | `t_account_no_sequence`（新） | 6 字段：账簿 + 科目的账户号序列 |
| mor_account | `t_owner_account` | 加 `account_no VARCHAR(48)`；加 `uk_owner_account_no (account_no)`；存量回填 |
| mor_account | `t_owner_account_no_sequence`（新） | 8 字段：主体 + 币种 + 科目代码的账户号序列 |

- ledger DML：插入 19 个汇总科目、更新 29 个既有科目的编号与上级。**前置条件**：没有账簿采用 STANDARD（脚本开头检查，不满足即中止）；mor-test 先清理演示数据。
- 回填：先加可空列 → 按 `create_time, id` 顺序生成账户号并初始化序列 → 改为 `NOT NULL`。本机与 mor-test 清理后预计为 0 行。
- 同步工件：`ledger.pdma.json`（31 → 33 表）、`account.pdma.json`（9 → 10 表）、两边 Schema SQL（由模型生成并通过逐字节校验）、两边测试 DDL、`ledger-initial-data.sql`（STANDARD 改为树形）、`scripts/pdmaner` 两个表数断言。
- 恢复：DDL 均为加列 / 加表，回退为删列 / 删表（注明会丢失变更记录与账户号）；DML 回退为把 29 个科目的编号、上级置空并删除 3101—3119（仍要求无账簿采用）。

### 2.5 页面

沿用现有财务页面样式（antd 表格、抽屉、弹窗）；页面原型以结构线框给出，确认后在阶段 4 实现，阶段 5 浏览器验收截图。

**账务基础资料 →「科目表」页签（改造）**

```text
┌ 科目表：[STANDARD ▼]   [搜索 编号/名称/代码   ]  [□ 显示已停用]        [+ 新增科目]  ┐
│ 编号        名称              代码                    类别   方向  类型   状态   操作  │
│ ▾ 1001      现金及银行存款     CASH_AND_BANK           资产   借   汇总   启用   …     │
│     100101  银行现金           BANK_CASH               资产   借   末级   启用   …     │
│ ▸ 1101      渠道应收款         CHANNEL_RECEIVABLES     资产   借   汇总   启用   …     │
│ ▾ 2201      应付客户款项       CUSTOMER_FUNDS_PAYABLE  负债   贷   汇总   启用   …     │
│     220101  Client 应付款      CLIENT_PAYABLE          负债   贷   末级   启用   …     │
│ 提示条：本科目表已被 1 个账簿采用，编号与上级不可修改（未采用时不显示）              │
└──────────────────────────────────────────────────────────────────────────────────────┘
操作菜单（…）：编辑 · 新增下级（仅汇总科目） · 停用/启用 · 变更记录
```

- 新增 / 编辑弹窗：上级科目（树选择，只列启用的汇总科目）、编号（自动带出上级前缀）、代码（编辑时只读）、名称、类别（有上级时锁定为上级类别）、余额方向、科目类型（汇总 / 末级，编辑时只读）。已采用时编号与上级置灰并说明原因。错误按 2.3 表就地显示。
- 停用确认：说明"停用后不能新建账户、不能被新规则引用，已有账户不受影响"。被引用时显示返回的原因。
- 变更记录抽屉：时间、操作人、类型、变更前 → 变更后（只列有变化的字段）。
- 无维护权限：隐藏新增按钮与操作菜单中的写操作，只保留变更记录。

**账本查询 → 新增「试算平衡」页签**

```text
┌ 账簿：[AE001 / USD ▼]  期间：[2026-10 ▼]                  [发起试算]（需维护权限）┐
│ 视图：(•) 科目余额（实时）  ( ) 试算记录                                          │
│ 编号      名称            期初余额     本期借方     本期贷方     期末余额           │
│ ▾ 2201    应付客户款项      0.00        14.40       343.20      328.80 贷        │
│     220101 Client 应付款     0.00        14.40       343.20      328.80 贷        │
│ ▾ 4001    营业收入          0.00         0.00        14.40       14.40 贷        │
│ …                                                                                 │
│ 合计（一级）               借 = 贷 校验结果                                       │
└───────────────────────────────────────────────────────────────────────────────────┘
试算记录：列表（运行号、状态、借贷合计、异常数、时间）→ 详情（同上树形 + 异常列表）
```

- 展开 / 折叠全部；末级行可点击进入现有账户流水下钻。

**账户号列与搜索（4 处）：** 账簿账户列表（`SetupBooksTab`）、账本下钻账户与面包屑（`LedgerDrilldown`）、明细账账户列表（`SubledgerAccounts`）与流水 / 批次抽屉、分录详情抽屉（`JournalEntryDrawer`）显示账户号；两个账户列表筛选区加"账户号"精确搜索框。建账户弹窗的科目选择改为"编号 名称"、只列启用的末级科目。

### 2.6 Platform 注册

注册块 91（唯一执行来源仍为 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`，阅读副本见 [db/platform-support/registration-dml.sql](./db/platform-support/registration-dml.sql)），共 96 行，初始 DISABLED：

- 8 条路由 91401—91408：L1—L4（POST，`FINANCE_ACCOUNTING_MANAGE`，`OPERATIONS_JWT` + `HUMAN_PERMISSION`）、L5、L6、试算平衡列表与详情（GET，`FINANCE_ACCOUNTING_READ`）；Header 32 行、Guard 48 行（每路由 6 行，维护类按设置接口限额、查询类按查询接口限额）。
- 查询参数 8 行：新路由的 `chartAccountId` / `page` / `size`，以及给既有路由补 `accountStatus`（97415 科目列表）、`accountNo`（97410 账簿账户、99401 明细账账户）。
- 不新增权限、角色授权与菜单 / 按钮资源；科目维护沿用 `OPERATIONS_FINANCE_SETUP_MANAGE`；试算运行路由不注册（E2）。
- 启用脚本：8 条路由 ACTIVE，3 条既有路由 version + 1 以刷新 Gateway Snapshot，PLATFORM_ADMIN 角色 version + 1。commercial 会话今晚使用块 95，不冲突。

### 2.7 研发与验证计划

**前置：** billing 计费事实重构提交后，在其提交之上开始；工作区与之重叠的文件见 1.6。

| 任务 | 范围 | 主要验证（`auto`，本机 local-environment） |
|---|---|---|
| A ledger | 科目编号规则、4 个维护 Processor + 变更记录、停用四处生效、采用冻结与科目表锁、三路径账户号、L5/L6 查询、M1—M4、STANDARD 树初始化、PDManer / Schema / 测试 DDL | HTTP 入口：新增 / 修改 / 停用 / 启用的正常与全部拒绝分支、已采用冻结、成环、并发同科目表维护与新建账簿互斥；三条建户路径的账户号格式与唯一；停用科目拒绝建户和建规则；L6 与 M4 汇总等于下级合计、一级借贷平衡；STANDARD 树完整性与 account 目录编号一致 |
| B account | 目录加科目编号、开户生成账户号、M5/M6、PDManer / Schema / 测试 DDL | 消费入口：新开户账户号格式与序号递增；同一账户再入账不变；按账户号筛选 |
| C BFF + Web | 9 条转发、模型字段、白名单、错误码；科目树页签、试算平衡页签、账户号列与搜索 | BFF 浏览器自动测试与边界 JSON；`finance.test.ts`（错误码 15 → 23、页面能力清单） |
| D 注册与文档 | 块 91 注册 DML 与启停脚本、能力清单 JSON；`FINANCE_DATABASE.md`、`FINANCE_PHYSICAL_DECISIONS.md`、决定记录、`CURRENT_IMPLEMENTATION.md`、requirements-site（FIN-22、FIN-I05、科目规则、新增科目维护用例） | `npm --prefix requirements-site test`、`scripts/pdmaner` 模型测试 |

阶段 5 在本机执行 SQL（需单独授权）后做全链路验收：维护科目 → 建账簿与账户 → billing 事实入账 → 控制台科目树、试算平衡与账户号检查，浏览器逐项截图。

### 2.8 写接口自查预案（示例第 11 节）

| 项 | 本次落点 |
|---|---|
| 最小合同 | L1—L4 Body 为 class + Validator；L3/L4 无 Body |
| 适配层 | `LedgerSetupController`（已有类）新增方法，只转换与委托 |
| Processor 归属 | `CreateChartAccountProcessor`、`UpdateChartAccountProcessor`、`DisableChartAccountProcessor`、`EnableChartAccountProcessor`，均在 ledger `service/processor`，查本模块 Repository |
| Context | `ChartAccountChangeContext` 持有科目表、目标科目、上级、变更记录 DataObject |
| Service | `LedgerSetupService` 扩展 `saveChartAccountChange(context)`：一个 TransactionTemplate 内写科目与变更记录 |
| 事务与外部调用 | 无外部调用；事务只含两张表写入 |
| Repository | 新增 `ChartAccountChangeRepository`、`AccountNoSequenceRepository`；`ChartAccountRepository` 加插入与条件更新 |
| 锁 | 科目表锁，四个维护入口与新建账簿共用；数据库唯一索引与条件更新兜底 |
| 原请求比较 | 无业务操作身份：停用 / 启用按当前状态幂等返回；修改无变化直接返回；新增以编号 / 代码唯一键拒绝重复 |
| Gateway 幂等 | 不处理 `Idempotency-Key` |
| View | L6 / M4 汇总结果使用 `AccountBalanceView`（多表：科目 + 分录聚合） |
| 公共包与配置 | 复用 `AbstractLockProcessor`、`PagingRequestBody`；无新配置 |
| 测试 | 按 UNIT_TEST_GUIDE 从 HTTP 入口覆盖，见 2.7 |

### 2.9 待确认

| 编号 | 事项 | 建议 |
|---|---|---|
| E1 | 科目表已被采用后，是否允许新增一级科目 | **决定：允许**（只是追加，不改既有结构） |
| E2 | 控制台"发起试算"是否纳入本期 | **决定：本期不做**（需改 Platform 能力清单并重建 Platform 镜像，今晚多会话共用 mor-test）；试算平衡页签只展示科目余额与已有试算记录；运行路由与按钮资源不注册 |
