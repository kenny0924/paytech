# 任务卡 E 报告：STANDARD 改为三级科目树

工作位置：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-chart2`（基于 `cf39a1ee`）。没有提交、没有建分支、没有 stash、没有回滚、没有执行 `mvn install`、没有连接 mor-test。本卡的改动都留在工作区，没有改 worktree 的 index（`git status` 中显示为 `MM` / `AM`）。

## 1. 开工说明

- 合同：`technique.md` 2.1 三级表。一级 19 个（3101—3119，4 位）编号不变；新增二级 26 个（3201—3226，6 位，`posting_allowed=0`，类别和余额方向同所属一级）；29 个既有科目（3001—3029）的 ID、代码、名称不变，改为 8 位三级末级，上级改为对应的二级科目。共 74 个。
- 本卡没有新增或修改写接口，也没有改代码逻辑（编号规则本来就支持 4 级）。只改了初始化数据、变更 SQL、account 目录编号常量、测试断言 / 夹具和文档。
- 生成方式：先用脚本按 technique 2.1 的表格生成 74 行，并校验三件事：末级代码与原行一致；新二级的一级上级与原末级的一级上级一致；编号前缀和长度正确。然后同一段 `INSERT` 写入 `ledger-initial-data.sql` 和测试 DDL，同一段 dml 写入需求目录 `db/ledger/dml.sql` 和 change 文件。

## 2. 改动文件

ledger SQL（worktree）
- `paytech/ledger/docs/database/sql/ledger-initial-data.sql`：STANDARD 科目段改为 74 行三级树，同步修改头部说明。
- `paytech/ledger/paytech-ledger-test/src/test/resources/init/ddl.sql`：科目段与初始化数据逐字一致（已核对：initial-data 中 `SET NAMES` 之后的内容逐字包含在测试 DDL 里）。
- `paytech/ledger/docs/database/sql/changes/2026-10-19-ledger-chart-hierarchy.sql`：dml 段换成新的需求目录 `dml.sql`（逐字包含）。ddl 第 1 段和第 2 段不变。

需求目录（主工作区，本卡允许修改）
- `db/ledger/dml.sql`：插入 19 个一级和 26 个二级汇总科目；把 29 个末级更新为 8 位编号，上级改为二级；核对改为三列（`total` 和 `numbered` 都是 74，`level3_leaves` 是 29）；`bad_parent` 增加两项校验：`LEFT(c.account_no, LENGTH(p.account_no)) = p.account_no`、长度等于上级长度 + 2；恢复段增加删除 3201—3226。
- `db/ledger/ddl.sql`：**没有改**。这份文件里没有任何 STANDARD 科目编号，列注释写的是「4-2-2-2，最多 4 级」，三级树仍然符合。
- `db/account/ddl.sql`：回填 CASE 映射改为 8 位，与 account change 文件逐字一致（`diff` 无差异）。

account（worktree）
- `OwnerAccountType`：`CLIENT_PAYABLE` 22010101、`PARTNER_PAYABLE` 22010201、`CLIENT_SECURITY_DEPOSIT` 22110101、`CLIENT_RECOVERY_RECEIVABLE` 11310101、`PARTNER_RECOVERY_RECEIVABLE` 11310102、`BANK_CASH` 10010101。
- `docs/database/sql/changes/2026-10-19-owner-account-no.sql`：CASE 映射同上。
- `AccountPostingWriter` 的 JavaDoc 示例、`README.md` 的示例都改为 `AE001-USD-22010101-S000001`。
- 测试 `OwnerAccountNoTest`：期望的账户号字面量改为 8 位。其余断言本来就通过 `getChartAccountNo()` 拼接，不用改。

ledger 测试（worktree）
- `StandardChartTreeTest`：改写为三级完整性校验。检查共 74 个；只有 1、2、3 三个级次；二级 26 个；三级就是全部可记账末级，共 29 个；一级共 19 个，代码与表一致，覆盖五类会计要素，都是 4 位、无上级、不可记账，首位与类别一致，余额方向符合约定（3201 为借）；二级和三级的上级都是汇总科目，级次比上级多 1，编号以上级编号开头且多 2 位，类别和余额方向与上级相同；全部 ACTIVE。保留 `test_ownerAccountTypeNumbersMatchChart`。
- `ListChartOfAccountsTest`：科目数 48 改为 74，汇总科目数 19 改为 45；`BANK_CASH` 断言为 10010101、级次 3。
- `AccountBalancesQueryTest`：L6 期望行数从 9 行改为 13 行（包含二级汇总），逐行断言级次、是否可记账、金额，以及两段上下级关系。新增 `assertParent` 辅助方法。
- `RunTrialBalanceTest`：末级行编号改为 8 位、级次 3；M4 `summaryLines` 期望改为一级和二级共 6 行（`ChartBalanceRollup` 会输出所有级次的汇总行），断言每个一级都指向唯一的二级、两者金额相同，末级行的上级是二级。
- 账户号字面量（`-USD-XXXXXX-` 统一按新旧编号映射）：`TransactionPostingTest`、`StartPeriodCloseTest`、`ListAccountsTest`、`CreateAccountTest`；注释里的编号：`CreateAccountTest`、`AbstractManualAdjustmentApiTest`。
- `LedgerDataCleaner`：保留行数 97 改为 123（科目 48 改为 74），同步修改说明。
- 自建测试科目表（LOCAL_CHART、`ChartAccountNoRuleTest`、`ChartBalanceRollupTest` 等）用的 6 位编号与 STANDARD 无关，没有改。

BFF / Web 测试夹具（只改数据和对应断言，不改逻辑）
- `finance-ledger-boundary.json`：L6 和 T2 的末级改为 `22010101` / `40010101`（级次 3，上级 3207 / 3220；顺带把原来写错的科目 ID 改为真实的 3009 / 3023）；L6 `lines` 与 T2 `summaryLines` 补上二级汇总行 220101、400101；账户号改为 8 位。
- `finance-setup-boundary.json` R15：`22010101`，级次 3。`finance-subledger-boundary.json`：账户号改为 8 位。
- `FinanceLedgerBookBrowserAutoTest`：增加三级行的上级与级次断言；`summaryLines` 数量 2 改为 4；账户号改为 8 位。
- `operations-console-web/src/finance/finance.test.ts`：账户号改为 8 位；科目夹具改为级次 3；科目树测试改为三级夹具，增加二级下挂末级的断言，`expandableKeys` 期望为 `['3101','3201','3106','3207','3208']`；余额行改为 10010101。`SetupBooksTab.tsx`、`SubledgerAccounts.tsx` 只改了占位和提示文字里的示例账户号。

文档
- 决定记录：worktree 和主工作区两份 `agent-docs/decisions/2026-10-19-ledger-chart-hierarchy-and-account-numbers.md` 都改了，现在完全一致。改动是：把「两级树」改为三级描述（19 个一级、26 个二级、29 个三级末级，共 74 个；注明 2026-10-20 用户变更）；账户号示例改为 8 位。
- worktree `agent-docs/database/FINANCE_DATABASE.md`：§4.1.1 改为三级说明，示例为 `CLIENT_PAYABLE` 22010101，上级是 220101 / 2201；§账户号示例改为 8 位。
- `paytech/ledger/README.md` 改为三级描述，并更新示例账户号；`paytech/account/README.md` 更新示例账户号。

## 3. 对应 AC

- AC-02（STANDARD 完整树）：由 `StandardChartTreeTest` 的三级完整性校验覆盖，`OwnerAccountType` 编号与科目表一致的断言保留。
- AC-08（级次汇总）：`AccountBalancesQueryTest`、`RunTrialBalanceTest` 在三级下验证：汇总等于下级之和、一级借贷平衡。
- AC-11（账户号）：ledger 三条建户路径和 account 开户都用新的 8 位科目编号（相关测试见上）。

## 4. 实际执行的命令与结果

| 检查 | 结果 |
|---|---|
| 用 root 删除并重建 `mor_ledger_chart`、`mor_account_chart`（来源是新的测试 DDL，库名和 GRANT 改为 `_chart`，副本放在 scratchpad），口令从 `.env.local` 按键读取 | ledger 33 张表、account 10 张表；STANDARD 74 行：可记账 29，4 位 / 6 位 / 8 位分别为 19 / 26 / 29 |
| `./mvnw -o -pl ledger/paytech-ledger-test -am verify -Dtest='com/paytech/ledger/auto/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=…/Code-MoR/local-environment`（`LEDGER_TEST_DB_URL=…/mor_ledger_chart`、`MOR_RABBITMQ_VHOST=/mor-chart`、`REDIS_KEYPREFIX=ledger-chart`） | **294 个测试，0 失败，0 错误**，BUILD SUCCESS |
| 同上，account：`-Dtest='com/paytech/account/auto/**/*Test'`（`ACCOUNT_TEST_DB_URL=…/mor_account_chart`、`REDIS_KEYPREFIX=account-chart`） | **63 个，0 失败**，BUILD SUCCESS |
| `./mvnw -o -pl operations-console-bff/paytech-operations-console-bff-test -am verify -Dtest='com/paytech/operations/console/bff/**/*Test' …` | **1592 个，0 失败**，BUILD SUCCESS（其中 Finance 账簿 39、设置 61、明细账 14） |
| `./mvnw -o -pl tools/arch-rules -am verify -Dtest='com/paytech/arch/**/*Test' …` | 61 个，0 失败，BUILD SUCCESS |
| `node --test scripts/pdmaner/ledger-model.test.mjs scripts/pdmaner/account-model.test.mjs scripts/pdmaner/oe-client-model.test.mjs` | 21 个，21 个通过 |
| `npm --prefix operations-console-web test` | 1109 个，1109 个通过 |
| 一次性 Docker `mysql:8.4.11`（`docker run --rm`，用随机 root 口令，结束后容器已删除）：`cf39a1ee` 的旧 ledger 测试 DDL（31 张表）→ 执行新的 change 文件 → 导出全部 `SHOW CREATE TABLE`、科目行（不含时间列）、各表行数；然后删库，用新测试 DDL 重建并同样导出 | 两次导出**完全一致**。change 的核对输出：`books_adopting_standard=0`；`total=74`、`numbered=74`、`level3_leaves=29`；`bad_parent=0`；两项「无编号」都是 0 |
| 同一个容器里额外检查 account：旧 account DDL 插入 4 个样例账户，执行 account change 文件，再与新测试 DDL 对比结构 | 回填结果为 `AE001-USD-22010101-S000001/2`、`AE001-USD-22010201-S000001`、`FE8001-USD-10010101-S000001`，序列 last_value 正确；结构**完全一致** |

## 5. 偏离与说明

- 需求目录 `db/ledger/ddl.sql` 没有改，原因见第 2 节。
- T2 / M4 的 `summaryLines` 现在包含二级汇总行。这符合现有 `ChartBalanceRollup` 的行为（输出所有级次的汇总），technique 也没有限定只输出一级。BFF 和 Web 的断言按这个行为更新。
- BFF 夹具里原来的末级科目 ID（3014 / 3024）与 STANDARD 实际 ID 不一致，这次顺带改为 3009 / 3023。只是数据修正，没有改逻辑。
- 与 R 卡一样，Maven 用 `-Dtest` 限定在本服务的测试包，避免 `-am` 连带运行上游模块的测试，那些测试会用到其他会话共享的资源。BFF 测试跑的是该模块自己的测试，没有指定专用库。

## 6. 未完成项与注意

- 没有运行 `npm --prefix requirements-site test`（本卡没要求）。
- 没有在 `mor_ledger` / `mor_account` 或 mor-test 执行任何变更 SQL。
- `verification/test_acceptance.py` 已经按三级树写好（74 个科目、22010101 等），本卡没有改动。`release/release.md` 记录的是两级版本在 mor-test 上的发布情况（48 个科目、`AE001-USD-220101-S00000x`），属于历史记录，本卡没有改；三级版本重新发布时需要另写记录。
- 主工作区的 `technique.md` 已经是三级合同，本卡没有改它。
