# W3 报告：acquiring 接入 tax（阶段 4）

> 2026-10-29 第二轮（按主会话四条指示）：CLIENT 来源改为 NOT_MANAGED（不再失败关闭）；事件组件带 `taxType`；按 tax-api 变更 2 处理可空责任方、NOT_MANAGED 行金额为空与行 `description`；新增 billing 死信规则的载荷形态断言；测试改用 `package` 阶段，数据库只连 `mor-local-mysql-1`（13306）。第 2 节数字为第二轮结果。
>
> 范围：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-tax` 的 `paytech/acquiring/**` 与 `paytech/tools/arch-rules` 的 `acquiring:` 段。未提交、未推送、未部署、未碰 mor-test、未改其他服务与 `events.yaml`。

## 1. 改动文件（按模块）

**数据库与模型（`paytech/acquiring/docs`）**

- `database/sql/changes/2026-10-29-order-tax-calculation.sql`（新）与 `-rollback.sql`（新）。
- `database/sql/acquiring-schema.sql`、`database/acquiring.pdma.json`（直接改 JSON，未跑生成器；新实体加入 order_context 图）。
- `paytech-acquiring-test/src/test/resources/init/ddl.sql`（与 Schema 同步）。
- `api/openapi/client-v1.yaml`、`docs/api/customer-checkout.openapi.json`：amountStatus 三值、taxBlockReason、SaveConsumer 响应新增报价字段、金额字段语义。
- `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml`：`acquiring:` 段加 `t_order_tax_calculation`（手工编辑）。

**paytech-acquiring-api**

- 新 `api/client/v1/enums/OrderAmountStatus`（ESTIMATED / FINAL / BLOCKED）。
- `CreateCheckoutResponseBody`、`GetCheckoutResponseBody` 加 `taxBlockReason`；`SaveConsumerResponseBody` 加 `amountStatus / taxBlockReason / currency / amounts`。

**paytech-acquiring-core**

- tax 接入：`integration/tax/impl/CheckoutTaxPortImpl`、`CheckoutTaxConverter`（新）；`CheckoutTaxPort` 改为 `calculate(TaxQuoteRequest)`；`CheckoutDocumentPort.render(request, seller, taxCalculation)`。
- 模型：`CustomerCheckoutModels` 去掉 `TaxFacts`，新增 `TaxQuoteRequest / TaxParties / TaxCalculation / TaxLine / TaxComponent`；新 `domain/customer/OrderTaxQuotes`（纯规则：结果与商品行对齐、金额恒等式、计算行 JSON、组件按固定顺序重建）。
- 持久化：新 `OrderTaxCalculation` DO、Mapper + XML、Repository；`CommerceOrder.amountStatus`；`FrozenCheckoutRecord`（旧快照可空、`initialTaxCalculationId`、`contractTaxCalculationId`）；`ConsumerContract.taxCalculationId`；合同相关查询改为取最新一份合同；`OrderRelationshipBinding` 按订单读取。
- 流程：`CreateCheckoutProcessor`（PROVISIONAL 报价、金额状态、BLOCKED 仍建单）、`CheckoutInitializationContext`、`CheckoutCreationServiceImpl`；`SaveConsumerProcessor` + `CustomerCheckoutServiceImpl`（事务外 FINAL 重算与合同重渲染，事务内追加计算、更新当前报价、追加合同）；`SubmitCustomerPaymentProcessor.checkTaxQuote`（支付门禁）；`GetCustomerCheckoutProcessor / CheckoutQueryContext`、`GetOrderProcessor`（金额状态取订单列）。
- 事件：`OrderPaymentSucceededPayload` 末尾可空新增 `taxCalculationId / taxStatus / taxScopeKey / taxAmount / taxComponents[]`，`OrderPaymentEventSource` 带当前计算。

**paytech-acquiring-app**

- 新 `TaxClientConfiguration`（`tax-service` 组，登记 `TaxErrorCode`，依赖故障映射 503/504）；`application.yml` 加 `tax-service` 客户端。
- `CustomerCheckoutIntegrationConfiguration` 删除零税 Mock；`CommercialMockConfiguration` 输出 MOR 8 行角色绑定（S-03）；`PersistenceConfiguration` 注册三个枚举 TypeHandler；三个 Converter 输出金额状态与原因。
- pom：acquiring 父 POM `dependencyManagement` 与 core / app / test 加 `paytech-tax-api`（版本 `${project.version}`，未改根 BOM）。

**paytech-acquiring-test**

- 新：`MockTaxCalculationApi`（实现 `TaxCalculationApi`）、`TaxCalculationMocker`（按阶段配置、同订单 + revision 同计算 ID、未配置记意外调用）、`TaxCalculationResponseBuilder`、`OrderTaxCalculationBuilder`、`OrderTaxTestMapper`、`OrderTaxChecker`。
- 新用例类：`CreateCheckoutTaxTest`（6）、`CheckoutTaxQuoteTest`（9）；`OrderPaymentSucceededHandlerTest` +2；`GetOrderSuccessTest` +1。
- 调整：`MockTestBase`（tax 意外调用检查）、`AbstractCreateCheckoutApiTest`（类级默认 tax NOT_MANAGED）、`CheckoutPaymentDataMocker`（新形态订单 Fixture）、`OrderContextDataMocker`、`CheckoutDataMocker`（清理计算表）、`CreateCheckoutContextBuilder` / `GetOrderDataBuilder`（金额状态；前者保持存量形态用于 AC-12）、`OrderPaymentEnvelopeChecker`（新字段、组件数组）、`SaveConsumerResponseChecker`、三个测试 Mapper 的 INSERT、`CreateCheckoutMockResolutionTest`、`CreateCheckoutRiskTest.test_clientSource`、`CreateCheckoutCommercialResolutionTest.test_snapshotAbsent`、`CheckoutCreationTransactionTest`、`SaveConsumerTransactionTest`。

## 2. 构建与测试

环境：JDK 25，Maven 离线 `~/.m3/repository` + `_no_tracking_`；本机 MySQL 13306 / Redis 16379 / RabbitMQ（只从 `.env.local` 读需要的键）。

| 命令 | 结果 |
|---|---|
| `./mvnw -o ... -pl acquiring/paytech-acquiring-test -am package -Dtest='com/paytech/acquiring/auto/ext/http/clientapi/**/*'` | 680 通过，0 失败 |
| 同上 `-Dtest='com/paytech/acquiring/auto/ext/http/customerapi/**/*'` | 154 通过，0 失败 |
| 同上 `-Dtest='.../auto/ext/job/**/*,.../auto/ext/mq/**/*,.../auto/domainservice/**/*,.../auto/architecture/**/*'` | 82 通过，0 失败 |
| 全 reactor `./mvnw -o ... test-compile` | 成功（含其他 W 进行中的改动） |
| `-pl tools/arch-rules -Dtest=TableOwnershipAutoTest*` | 1 失败：表总数期望 395、实际 388（见第 5 节） |
| `node --test scripts/pdmaner/acquiring-scenario-doc.test.mjs` | 失败，改动前即失败（表清单缺 2026-10-24 的 `t_order_relationship_binding`） |

acquiring 合计 916 个用例全部通过；数据库为 `mor-local-mysql-1`（127.0.0.1:13306），未连接任何 `mor-test-*` 容器。验收对照：

| AC | 覆盖 |
|---|---|
| AC-01 | `CheckoutTaxQuoteTest.test_finalRecalculation`：未税 100，账单国家 SA 15%，READY，税 15.00、总额 115.00，Intent 金额 115.00 |
| AC-03 | `CreateCheckoutTaxTest.test_estimated`（ESTIMATED、声明国家证据、revision 1）；`test_finalRecalculation`（两份计算都保留）；`test_provisionalRejected`、`test_expiredRejected`（暂估或过期被拒） |
| AC-04 | `CreateCheckoutTaxTest.test_notManaged`；`CheckoutTaxQuoteTest.test_notManagedPays`；`OrderPaymentSucceededHandlerTest.test_notManagedOrder`（NOT_MANAGED、组件空数组） |
| AC-05 | `CreateCheckoutTaxTest.test_blocked`（建单仍成功）；`CheckoutTaxQuoteTest.test_blockedCannotPay`（原因码、无 PAY、支付 409、不建 Intent）；`GetOrderSuccessTest.test_blockedQuote` |
| AC-06 | acquiring 侧：`CreateCheckoutTaxTest.test_multiLineQuote`（两行含折扣，行税额落 `lines_json`、合计等于头税额）；尾差算法属 tax |
| AC-07 | acquiring 侧事件字段：`OrderPaymentSucceededHandlerTest.test_taxedOrder`（计算 ID、READY、SALE、税额、组件；`financeTaxSnapshotId` 为 null） |
| AC-12 | 现有支付、退款、取消、事件用例都用存量形态 Fixture（旧快照、无计算行）全部通过；`CheckoutTaxQuoteTest.test_legacyOrderNotRecalculated`；Handler 存量用例五个税务字段为 null |
| AC-14 | Customer API：`test_blockedCannotPay` 中 SaveConsumer 与 GetCheckout 返回 BLOCKED 与原因；`test_finalRecalculation` 返回重算税额 |

另覆盖：CLIENT 来源建单与重算均为 NOT_MANAGED 且可正常付款（`CreateCheckoutCommercialResolutionTest.test_snapshotAbsent`、`CreateCheckoutRiskTest.test_clientSource`、`CheckoutTaxQuoteTest.test_clientSourceRecalculation`）；事件满足 billing 死信规则（`OrderPaymentEnvelopeChecker.assertBillingTaxShape`，用于存量、READY、NOT_MANAGED 三种事件）；行说明超长截断到 255（`CreateCheckoutTaxTest.test_estimated`）；tax 不可用（建单与资料提交均 503、不落库）、缺 `TAX_RESPONSIBLE`（502、不调用 Risk 与 tax）、合同未绑定最新计算（409 LEGAL_DOCUMENT_CHANGED）、金额不变时沿用合同文件、Service 事务回滚连带计算行。

## 3. 本机执行的 SQL

| 库 | 文件 | 结果 |
|---|---|---|
| `mor_acquiring`（root） | `changes/2026-10-29-order-tax-calculation.sql` | 成功：新表、`amount_status NOT NULL`、两列改可空、新增两列、唯一键改为 `(order_id, tax_calculation_id)` |
| `mor_acquiring`（root） | `changes/2026-10-29-order-tax-calculation-rollback.sql` | 成功：结构恢复原状（空库上验证） |
| `mor_acquiring`（root） | 前滚脚本再次执行 | 成功，本机库当前为前滚后状态 |

回滚的"有新单时被约束阻断"只按设计写在脚本里（第一条 ALTER 恢复 NOT NULL 会失败），没有插入真实新单去验证。

## 4. 偏离技术方案之处

1. **`t_order_tax_calculation` 增加 `expires_at DATETIME(6) NULL`（主会话已批准）。** PROVISIONAL / NOT_MANAGED 为空，FINAL 取 tax 响应；门禁据此判断过期，付款时不回查 tax。
2. **载荷定义在 core（主会话已批准）。** acquiring-api 不发布载荷类，已更新 core `domain/order/OrderPaymentSucceededPayload` 与 JavaDoc；`events.yaml` 未动（W1 所有）。
3. **CLIENT 来源按主会话决定处理为 NOT_MANAGED。** 没有商业关系快照时请求不传 `taxSubjectPartyId` 与卖方，传 commercial 的合同业务模式，tax 返回 NOT_MANAGED（`TAX_RESPONSIBLE_UNRESOLVED`）并给出真实计算 ID。只有快照存在却缺少 TAX_RESPONSIBLE 法律 Party 时才 502。资料提交重算时本库没有合同业务模式，这一分支按 ID 读取原计算（`GET /tax/v1/calculations/{id}`）取回冻结的主体，是唯一新增的 tax 读取调用。两个 CLIENT 来源测试恢复为成功建单。
3a. **事件组件带 `taxType`。** `components_json` 与 `taxComponents[]` 字段顺序为 jurisdictionCode、taxType、registrationId、taxSubjectPartyId、taxAmount。报价落库前校验与 billing 死信规则一致：READY 组件合计等于头税额且只有一个纳税主体、每个组件有注册、NOT_MANAGED 没有组件；BLOCKED 被支付门禁挡住，不会发布。
3b. **NOT_MANAGED 行金额。** tax 对 NOT_MANAGED 只在头部给税额、行金额为空；本库按行税额 0、含税额等于净额记录（acquiring 不自报税额，头税额恒为 0）。
3c. **行 description 取商品名称**（主会话最终决定：票据打印的内容）。名称为空时取 `item_description`，截断到 255，两者都空不传。
4. **每个 FINAL revision 都追加一份合同。** 金额不变时沿用原合同文件，只换绑定的计算。否则门禁"合同绑定最新计算"在金额不变时无法成立。唯一键因此是 `(order_id, tax_calculation_id)`，与 8.2 一致。
5. **`amount_status` 回填后设为 NOT NULL**（存量：有总额为 FINAL，否则 ESTIMATED）。新旧镜像不能与迁移交错，脚本头部已写明。
6. **MOCK 商业配置写死 AE001 的 Party `880000000702`**（取自 mor-test 演示数据），生效模型版本记 0。tax 的演示 market / registration 必须用同一个 Party，否则 mor-test 会落到 NOT_MANAGED。
7. **事件 `taxComponents[]` 读库后按固定字段顺序重建。** MySQL JSON 列会重排对象键，测试发现后修正。
8. **自报税额不传。** Client API 没有自报税额字段，NOT_MANAGED 时税额为 0。
9. **错误码复用 acquiring 已有 Code。** tax 不可用为 503 / 504 DEPENDENCY_*（Client 与 Customer API 共用），tax 409 转 409 CONCURRENT_REQUEST，tax 400 与畸形响应转 502 UPSTREAM_PROTOCOL_ERROR。
10. **只纠正 amountStatus。** CreateCheckout 的 `preparationStatus=READY` 与 OpenAPI 的 PENDING 仍不一致（原有问题，S-07 只要求 amountStatus）。Client GetOrder 没有加 `taxBlockReason`（10.2 未列）。

## 4a. 第二轮调整（主会话指示与 tax-api 变更 2、3）

| 指示 | 实现 | 验证 |
|---|---|---|
| CLIENT 来源不失败关闭 | 没有商业关系快照时 `TaxParties.unresolved(businessMode)`：请求不带 `taxSubjectPartyId` 与卖方，tax 返回 NOT_MANAGED（`TAX_RESPONSIBLE_UNRESOLVED`），订单金额 FINAL、可支付；快照存在却缺 TAX_RESPONSIBLE 法律 Party 仍 502。资料提交重算时按 ID 读取原计算取回业务模式（本库不存） | `test_snapshotAbsent`、`test_clientSource` 恢复成功建单；`CheckoutTaxQuoteTest.test_clientSourceRecalculation`（重算 NOT_MANAGED 并付款）；`CreateCheckoutTaxTest.test_taxSubjectMissing`（有快照缺责任方 502） |
| 未知税类映射为 BLOCKED | tax 变更 3 起未知税类（含小写）返回 200 BLOCKED `UNKNOWN_TAX_CATEGORY`；acquiring 对任何 BLOCKED 都记 `amountStatus=BLOCKED`、原因码原样返回，不会是 502。税类代码原样传，不做格式转换 | BLOCKED 映射由 `CreateCheckoutTaxTest.test_blocked`、`CheckoutTaxQuoteTest.test_blockedCannotPay` 覆盖（原因码不同、路径相同） |
| 行 `description` | 取 `t_order_item.item_name`，名称为空时取 `item_description`，截断到 255，都空不传；同一订单行每次重算取值相同，满足 tax 同身份比较 | `CreateCheckoutTaxTest.test_estimated` 断言传的是名称而非说明 |
| billing 死信五条规则 | 报价落库前校验：READY 组件合计等于头税额、所有组件同一纳税主体、组件都有注册；NOT_MANAGED 无组件；BLOCKED 被支付门禁挡住不发布；有税务字段必有 taxCalculationId。组件带 `taxType` | `OrderPaymentEnvelopeChecker.assertBillingTaxShape` 用于存量、READY、NOT_MANAGED 三种事件 |
| 测试纪律 | `package` 阶段；只连 `mor-local-mysql-1`（13306） | 改为商品名称后全量复跑：680 + 154 + 82 = 916 个 acquiring 用例全部通过 |

主会话已接受：CLIENT 来源重算时按 ID 读取原计算（GET）；NOT_MANAGED 行税额按 0 记录。

AE001 Party：W1 已把本机 tax 演示种子改为 `880000000702`，与 MOCK 商业配置一致。arch-rules 表总数由 W1 负责。

## 5. 未完成项与建议

- **arch-rules 表总数**：已交由 W1 统一修改（W1 的 tax 24→16 为 −8，W3 为 +1，现为 388）。
- **PDManer 场景测试**：`acquiring-scenario-doc.test.mjs` 的表清单早已过期，需补 `t_order_relationship_binding` 与 `t_order_tax_calculation`（不属于本次 W3 范围，未改）。
- **`notManagedReason` 未落本库**：acquiring 不保存 NOT_MANAGED 原因，运营页直接从 tax 读取。
- **税类代码**：W1 变更 3 已改为自由文本，未知值返回 BLOCKED。现有测试与演示用的 `STANDARD` 在真实 tax 上会是 `UNKNOWN_TAX_CATEGORY`（S-13），阶段 6 需统一演示数据的税类。
- **部署前置（阶段 6）**：Nacos 中 `tax-service` 可被发现；`internal-auth` 非 OFF 时需在 Platform 签发白名单登记受众 `tax-service`；核对 mor-test AE001 的 Party 仍是 `880000000702`；mor-test 先执行迁移再上 acquiring 镜像。
- **并发提交**：同一订单两个不同国家的资料提交争用同一 revision 时，后到者得到 409，需要重新提交。这是有意的行为，没有专门的并发测试。
- **合同渲染**仍是批准的固定 Mock（文件 ID 等于计算 ID），真实 Platform 渲染仍为 TODO。
- **文档**：`ACQUIRING_PHYSICAL_DECISIONS.md`、`CURRENT_IMPLEMENTATION.md` 未改，由主会话汇总。
