# R 卡报告：A/B 改动重放到 cf39a1ee 后的验证

工作位置：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-chart2`（基于 `cf39a1ee`，含 billing 重构 `8b7b4e7a`）。未提交、未建分支、未 stash、未回滚、未 `mvn install`。Maven 从 `paytech/` 运行（仓库根没有 `mvnw`），`JAVA_HOME=jdk-25`。

## 1. 表归属 YAML

- `node paytech/tools/arch-rules/scripts/generate-table-ownership.mjs --check`：通过，YAML 已是最新（265 张物理表 + 56 张保留 − 7 张按步骤移除 + 29 张按步骤新增 = 343）。不需要重新生成。

## 2. 适配 billing 改名（只改我们新增的代码）

`-am test-compile` 最先报的是 account 测试编译错误；把下面几处改好后，ledger 和 account 的主代码与测试都能编译：

| 文件 | 改动 |
|---|---|
| `paytech/account/paytech-account-test/.../auto/mq/OwnerAccountNoTest.java` | `EconomicFactBuilder` 改为 `BillingFactBuilder`；`billing.api.enums.AmountSource` 改为 `FactComponentType`；删掉 `EconomicFactPayload` 的 import；`partner(id, oeId, feId)` 改为 `partner(id, feId)`；`component(AmountSource, amount)` 改为 `component(FactComponentType, payerRole, payeeRole, amount)`；`line(AmountSource…)` 改为 `line(FactComponentType…)`；类注释里的入口名改为 BillingFactRecorded |
| `paytech/account/paytech-account-test/.../auto/ext/http/account/OwnerAccountQueryTest.java` | `test_filterByAccountNo` 的事实构造改成 billing 的新 API，写法和同类里的 `test_listAccountsWithFilters` 一样 |
| `paytech/ledger/paytech-ledger-test/.../posting/TransactionPostingRejectionTest.java`、`TransactionPostingTest.java` | 我们新增的两个测试（科目停用）里，`EconomicFactPayload` 改为 `BillingFactPayload` |

对 billing 改过的、3-way 合并碰到的文件做了 diff 检查（对比 `cf39a1ee`）：`TransactionPostingServiceImpl`、`CreateRuleVersionProcessor`、`AccountPostingWriter` 都只多出我们新增的内容（新建账户时检查科目是否停用、按 `AccountNoSequenceRepository` / `OwnerAccountNoSequenceRepository` 分配账户号、规则行检查科目是否停用），billing 那边的内容都还在。合并后剩下的 `EconomicFact*` 字样在 `cf39a1ee` 里原本就有（Mapper、Repository、文档、旧的 changes SQL），不是我们引入的。

## 3. 测试库

- 用 root 把 `mor_ledger_chart` 和 `mor_account_chart` 删掉重建。DDL 用 worktree 的 `paytech-*-test/src/test/resources/init/ddl.sql`，复制到 scratchpad 后把库名和 GRANT 的目标库换成 `_chart`。建好后 ledger 33 张表、account 10 张表。
- 授权：`ledger_app` 对 `mor_ledger_chart`、`account_app` 对 `mor_account_chart` 都是 SELECT/INSERT/UPDATE/DELETE，和 `mor_ledger` / `mor_account` 上的授权一样。
- RabbitMQ vhost `/mor-chart` 本来就有，`mor_local` 有全部权限。口令都从 `.env.local` 按键读入，没有打印。

## 4. 测试结果

| 命令（在 `paytech/` 下） | 结果 |
|---|---|
| `./mvnw -o -pl ledger/paytech-ledger-test -am verify -Dtest='com/paytech/ledger/auto/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=…/Code-MoR/local-environment`，环境变量 `LEDGER_TEST_DB_URL=…/mor_ledger_chart`、`MOR_RABBITMQ_VHOST=/mor-chart`、`REDIS_KEYPREFIX=ledger-chart` | **294 个测试，0 失败，0 错误**，BUILD SUCCESS |
| 同上换成 account：`-Dtest='com/paytech/account/auto/**/*Test'`，环境变量 `ACCOUNT_TEST_DB_URL=…/mor_account_chart`、`REDIS_KEYPREFIX=account-chart` | **63 个测试，0 失败，0 错误**，BUILD SUCCESS |
| `./mvnw -o -pl tools/arch-rules -am verify -Dtest='com/paytech/arch/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false` | 61 个测试，0 失败，BUILD SUCCESS |
| `node --test scripts/pdmaner/ledger-model.test.mjs scripts/pdmaner/account-model.test.mjs scripts/pdmaner/oe-client-model.test.mjs`（worktree 根） | 21 个测试，21 个通过 |

和卡上命令不同的地方：arch-rules 加了 `-Dtest` 限定（A 卡也是这么做的）。原因是不加的话，`-am` 会连带跑 Foundation 等上游模块的测试，它们用的是其他会话共享的库、Redis 和 MQ。

另外，`application-mock.yml` 里 Redis 前缀写死为 `ledger-mock`。我按 A 卡的做法设了 `REDIS_KEYPREFIX` 环境变量，但没有检查它是否真的覆盖了这个值。

## 5. 整个 reactor 编译

- `./mvnw -o -fae -DskipTests package -pl '!operations-console-bff/paytech-operations-console-bff-app,!operations-console-bff/paytech-operations-console-bff-test'`：**BUILD SUCCESS**，这次没有别的模块被我们的 API 签名改动弄坏。
- 参考（留给 C 卡）：`-am -pl operations-console-bff/...` 编译 BFF app 时有 3 个错误：
  - `FinanceLedgerBookClientImpl:62` 调 `LedgerBookQueryApi.listAccounts`，参数不对；
  - `FinanceSetupClientImpl:127` 调 `LedgerSetupQueryApi.listChartAccounts`，参数不对；
  - `FinanceSubledgerClientImpl:37` 调 `OwnerAccountQueryApi.listOwnerAccounts`，参数不对。

  bff-test 模块因为 app 先失败了，没有编译到。

## 未运行

- 没有执行 `-am verify` 时不带 `-Dtest` 的上游模块测试（原因见第 4 节）。
- 没有运行 `npm --prefix requirements-site test`（本卡没要求）。
