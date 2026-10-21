# E2 报告：billing 引用两列读写打通（决定 I-06）

## 1. 改动文件

billing（`paytech/billing/`）：

- `paytech-billing-core/.../core/dal/dataobject/BillingFactRef.java`：加 `externalReferenceType`、`externalTransactionId`（可空，仅 `CHANNEL_TRANSACTION_REF` 非空）。
- `paytech-billing-core/src/main/resources/mapper/billing/BillingFactRefMapper.xml`：`<sql id="columns">` 与 `batchInsert` 补两列（查询走 `resultType` 自动映射，无独立 resultMap）。
- `paytech-billing-core/.../domain/BillingFactPayloads.java`：`ref()` 把两列映射到 `BillingFactRefPayload`；`submissionHash` 的引用项改由 `refItem()` 生成，两列**只在非空时**写入摘要，既有人工提交的摘要不变。
- `paytech-billing-api/.../response/BillingFactRefResponseBody.java`：加两个字段。
- `paytech-billing-app/.../converter/BillingFactConverter.java`：`toRefBody` 带出两列。

ledger：

- `paytech-ledger-core/.../integration/billing/impl/BillingFactClientImpl.java`：`toRef` 透传两列（模型即 `BillingFactRefPayload`，无单独模型）。
- `paytech-ledger-test/.../mocker/external/BillingFactMocker.java`：DTO Mock 的 `refBody` 同步两列，保持与真实响应形态一致。

测试（`paytech-billing-test`）：

- `test/dal/BillingTestMapper.java`：`selectBillingFactRefs` 查询补两列；`insertBillingFactRef` 改为 6 参数显式列插入，原 4 参数签名保留为 default 方法（传 null），既有调用不变。
- `BillingFactRecordedHandlerTest.test_payloadCarriesChannelTransactionRef`（新增）：TestMapper 前置一条 AUTO / APPROVED PAYMENT（ORDER + 带外部交易号的 `CHANNEL_TRANSACTION_REF` + 一条渠道成本行），调真实 `BillingFactRecordedHandler` 发布到本地 Broker，断言信封载荷中渠道引用两项原样输出、ORDER 引用两项为 null，并过 `payloadShape()` 字段顺序检查。
- `BillingFactQueryTest`：`insertPaymentWithRefundLink` 给 PAYMENT 加 `CHANNEL_TRANSACTION_REF`；`test_getFactWithRuleEvidenceAndLinks` 经 HTTP 读接口断言引用按类型升序、渠道引用带两项、ORDER 两项为空。

account：未改。`BillingFactEventReader` 等读取方随 test-compile 编译通过（字段可空，读取不受影响）。

## 2. 验证（JDK 25，`./mvnw -B -o`，paytech/ 下，本机 local-environment）

| 命令 | 结果 |
|---|---|
| `-q -pl billing/{api,core,app,test},ledger/{core,test},account/{core,test} -am test-compile -DskipTests` | 编译成功（主代码与测试代码） |
| `-pl billing/paytech-billing-test -am verify -Dtest='com/paytech/billing/**/*' -Dsurefire.failIfNoSpecifiedTests=false` | 81 个测试全部通过（其中 BillingFactRecordedHandlerTest 6、BillingFactQueryTest 8、SubmitBillingFactTest 14） |
| `-pl ledger/paytech-ledger-test -am verify -Dtest='CatchUpPostingStreamTest,StartPeriodCloseTest'` | **环境阻塞，未通过**：本机 `mor_ledger` 缺表 `t_chart_account_change`，且保留数据行数 78 ≠ 期望 123（`LedgerDataCleaner.assertEmpty`），首个测试的 BeforeEach 即失败，后续全部被“上一次清理失败”拒绝。与本线改动无关（ledger 侧只改了两行透传）；需要有人按 ledger 现行 Schema 与 `ledger-initial-data.sql` 重建本机 `mor_ledger` 后重跑。本线按约束未执行 SQL。 |

没跑：account-test 的库测试（只编译）、全仓 reactor。

## 3. 出入

- **没有扩展人工提交命令 `SubmitBillingFactRequest.Ref`。** 现在唯一构造 `BillingFactRef` 的路径是人工登记，而人工登记按事实类型白名单拒绝 `CHANNEL_TRANSACTION_REF` / `RECON_RESOLUTION`（保持不变），在这里加两列只会是永远为空的字段。后续自动事实直接构造 DataObject，经 `batchInsert` 落库、经 `ref()` 进载荷、经 `submissionHash` 进摘要，三处都已就绪。
- 外部交易号类型在测试中用字面值 `PAY`：取值来自 Payment 渠道交易引用类型目录，billing 不持有该枚举。
