# W2 报告：billing / account / ledger（阶段 4）

- worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-tax`（未提交）
- 依据：technique.md 8.3、11.1、11.2、11.3；F-10—F-13
- 已读规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 4、5、7 节，以及 billing / account / ledger 现有入口测试的写法。

## 1. 改动文件

### billing
| 文件 | 改动 |
|---|---|
| `paytech-billing-api/.../enums/FactRefType.java` | 新增 `TAX_CALCULATION`。`TAX_SNAPSHOT` 保留，注释改为只读兼容 |
| `paytech-billing-core/.../core/enums/OrderTaxStatus.java`（新） | `READY / BLOCKED / NOT_MANAGED`，与 tax 的 `TaxCalculationStatus` 同码。billing 不依赖 `paytech-tax-api` |
| `paytech-billing-core/.../domain/OrderTaxCalculation.java`、`OrderTaxComponent.java`（新） | 事件 tax 组的本地模型 |
| `paytech-billing-core/.../domain/OrderCommercialContext.java` | 增加可空 `taxCalculation`。`financeTaxSnapshotId` 继续读取 |
| `paytech-billing-core/.../ext/mq/OrderPaymentSucceededEventReader.java` | 读取 `taxCalculationId / taxStatus / taxScopeKey / taxAmount / taxComponents[]`，schema 仍为 1。缺 `taxCalculationId` 时整组为空；此时若出现其他税务字段，按载荷不合法进入死信 |
| `paytech-billing-core/.../service/processor/RecordPaymentFactProcessor.java` | 读取上游前新增 `checkTaxFacts`。READY 时校验税额合计等于 Σ 组件，且正税额组件带注册；`BLOCKED` 拒绝；无计算或 NOT_MANAGED 跳过 |
| `paytech-billing-core/.../impl/PaymentFactAssemblerImpl.java` | READY 时每个正税额组件生成一行 `SELLER_TAX`：`CHANNEL(clearingAccountId)` 付给 `TAX_AUTHORITY(taxSubjectPartyId)`，规则族 `TAX_RULE`，`source_rule_version_id = taxCalculationId`。证据含 `taxCalculationId / taxScopeKey / jurisdictionCode / registrationId / taxSubjectPartyId`。本金 = 毛额 − Σ手续费 − Σ税额，须为正。有计算时引用 `TAX_CALCULATION`；只有存量快照时引用 `TAX_SNAPSHOT` |
| `paytech-billing-core/.../validator/fact/BillingFactInvariants.java` | 行目录加 `SELLER_TAX → (CHANNEL, TAX_AUTHORITY)`。`TAX_AUTHORITY` 标识必须是十进制 Party ID。毛额恒等式包含 `SELLER_TAX`。同一事实所有 `SELLER_TAX` 收方必须相同 |
| `docs/database/sql/billing-schema.sql`、`docs/database/billing.pdma.json`、`paytech-billing-test/src/test/resources/init/ddl.sql` | 只改 `t_billing_fact_ref.ref_type` 注释，加 `TAX_CALCULATION` |
| `docs/database/sql/changes/2026-10-29-billing-fact-ref-tax-calculation.sql`（新，未执行） | 同一注释的 `MODIFY COLUMN`，可重跑，回退写在文件头。见第 4 节 |
| 测试：`OrderPaymentSucceededTaxTest.java`（新）、`BillingFactInvariantsTest.java`、`OrderPaymentSucceededEnvelopeBuilder.java`、`PaymentFactConstants.java` | 见第 2 节 |

### account
| 文件 | 改动 |
|---|---|
| `paytech-account-core/.../validator/FactBalanceMappingRule.java` | `CHANNEL` 平台侧允许组件加 `SELLER_TAX`，不产生 Owner 流水 |
| 测试：`BillingFactBalanceConsumeTest.java`、`test/builder/BillingFactBuilder.java` | 新增 `sellerTax(...)`，由 `paymentLines` 在手续费后生成并从本金扣除。新增用例 `test_autoPaymentFactChannelPaysSellerTax` |

### ledger
- 生产代码无改动。
- 测试：`TransactionPostingTest.test_twoSellerTaxLinesPostOneTaxPayableCredit`，以及 `BillingFactLineBuilder.channelSellerTax(...)`。

## 2. 构建与测试（对照 AC-09、AC-12）

环境：JDK 25，离线 Maven，`-pl <模块>-test -am`，只导出 `.env.local` 中的 `MOR_RABBITMQ_USER/PASSWORD/VHOST`。

```
./mvnw -o -Dmaven.repo.local=$HOME/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ \
  -pl billing/paytech-billing-test -am -Dtest='com/paytech/billing/**/*' -Dsurefire.failIfNoSpecifiedTests=false package
（account、ledger 同理）
```

| 模块 | 结果 |
|---|---|
| billing | 142 通过，0 失败（`OrderPaymentSucceededTaxTest` 10、`BillingFactInvariantsTest` 13、`OrderPaymentSucceededRecordTest` 6） |
| account | 68 通过，0 失败（`BillingFactBalanceConsumeTest` 16） |
| ledger | 332 通过，0 失败（`TransactionPostingTest` 6） |

新增 billing MQ 入口用例，经真实本机 RabbitMQ 和正式 Handler：
- 单组件：手续费 3.00、税 5.00、本金 92.00、渠道成本 0.50，并核对证据列、`TAX_CALCULATION` 引用和发布的 `BillingFactRecorded`。
- 两组件（3.00 + 2.00）加一个零税额组件：生成两行税，零税额组件不生成行。
- NOT_MANAGED：无税行，本金 97.00。
- 存量载荷（无税务字段、无快照）：八类引用，三行。原 `test_platformPaymentRecorded` 继续覆盖带 `financeTaxSnapshotId` 的旧载荷，仍记 `TAX_SNAPSHOT`。
- 本金不为正：死信。
- 纳税主体不同：死信，由不变量拒绝。
- 读取上游前进入死信：税额与组件不一致、`BLOCKED`、征税组件缺注册、无 `taxCalculationId` 却有 `taxStatus`。

account 用例：渠道付两行税给同一税务机关、本金 85。只记 Client 应付一条流水，不开税务账户。

ledger 用例：两行 `SELLER_TAX`（3 + 2，同一收方）合并为规则行 5204 的一笔 `TAX_PAYABLE` 贷记 5，借贷平衡。

### 测试运行注意
- 必须用 `package` 而不是 `test`。用 `-am` 跑到 `test` 阶段时，reactor 会把 `paytech-messaging` 的 test-jar 解析成整个 `target/test-classes`，连同 foundation 自己的 `application-mock.yaml` 一起带入。该配置会执行 `DROP TABLE t_consumed_event`，而 `billing_app` 只有 DML 权限，上下文启动失败。跑到 `package` 时用的是只含 testkit 的 jar。`package` 只写各模块 `target/`，不安装到本地仓库。
- 测试配置读取 `MOR_RABBITMQ_PASSWORD`，需从 `.env.local` 导出。

## 3. 本机 SQL
- 未执行任何 SQL。新增的注释变更文件未执行，回滚也未验证。
- 查询本机库授权时，第一次误选了 `mor-test-mysql-1` 容器。root 登录被拒绝，没有执行任何语句。之后只访问 `mor-local-mysql-1`，并且只执行了 `SHOW GRANTS`。

## 4. 偏离技术方案之处
1. **新增注释变更 SQL 文件。** 8.4 节没有列 billing SQL 文件，但已部署库的列注释需要手工同步，否则与 `billing-schema.sql` 不一致。文件只改注释、未执行，由主会话决定保留还是删除。
2. **`OrderTaxStatus` 放在 billing 本地。** 按规范，消费方显式解析 Code。billing 没有依赖 `paytech-tax-api`，避免对 W1 交付顺序的编译期依赖。
3. **事件组内一致性校验。** 技术方案没有写明，按"数据不一致即业务拒绝"补充：READY 时 Σ组件必须等于 `taxAmount`，正税额组件必须有 `registrationId`，`BLOCKED` 拒绝；无 `taxCalculationId` 却带其他税务字段，按载荷不合法处理。四种情况都进入死信，不登记事实。
4. **税行的数值字段。** 事件不携带组件税基，所以 `basis_amount = raw_amount = posted_amount = 组件税额`，`rate_value` 和 `source_rule_component_id` 为空，与资金类行的写法一致。如果以后需要真实税基和税率，11.1 节的组件字段要加 `taxableAmount / rate`。
5. **行顺序。** 现在依次是手续费、税、本金、合作方、渠道成本。无税事实的行号与原来相同。
6. **NOT_MANAGED 也记 `TAX_CALCULATION` 引用。** 只要有计算 ID 就记，便于追溯。11.2 节只写了"新引用替代 TAX_SNAPSHOT"。

## 4a. 合同更新：taxComponents 增加 taxType（主会话 2026-10-29 通知）
- reader 把 `taxType` 作为可空 CODE 读入 `OrderTaxComponent`，并写入税行证据 `source_evidence.taxType`。组件内的未知字段一律忽略。
- 本期只发布 `taxAmount > 0` 的组件；billing 仍然容忍零税额组件，零税额不生成行。
- 测试构造器的组件改为带 `taxType=VAT` 和一个未知字段 `componentLabel`。`test_singleComponentSellerTax` 断言证据中的 `taxType`。
- 复跑 billing：142 通过，0 失败。account 和 ledger 不读组件字段，未复跑。

## 5. 未完成项与建议
- 无未完成项。
- W3 发布事件时要保证：`taxComponents` 按 jurisdiction + registration 汇总，`taxAmount` 等于 Σ组件，正税额组件带 `registrationId`，同一订单所有组件的 `taxSubjectPartyId` 相同。否则 billing 会把事件送入死信。
- 建议主会话在 `CURRENT_IMPLEMENTATION.md` 记录第 2 节的测试运行注意。
