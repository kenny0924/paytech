# 任务卡 03 · Commerce → acquiring + consumer（两库两 app）

- 分支：`refactor/step-03-acquiring-consumer`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step03`；起点：`refactor-main@e11a2385`（00–02d、04、05、07、09、10 已合入）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) 第 03 步（顺序已调整到 04、05 之后）；决定 D29（消费者 Token 按订单）、D30、D31（收单 = `acquiring`，Checkout 为内部模块）、D42（已存卡开关归客户主档）、C7（死表死代码删除）、S6（`customer_id` 稳定、资格读失败降级为不可存卡）。
- 执行时另有第 06 步可能并行开始前的准备；本步不与其他搬迁步并行。

## 1. 输入文档（先完整读取）

- [design/01-skeleton-and-conventions.md](../design/01-skeleton-and-conventions.md)、[design/02-archunit-rules.md](../design/02-archunit-rules.md)、[design/03-module-and-table-ownership.md](../design/03-module-and-table-ownership.md) 第 1.2 节 acquiring / consumer 两行、第 2 节 Commerce 17 表（`t_client_stored_card_setting` 已归 client-master）、第 3 节 Commerce 枚举拆分行。
- [design/09-path-coupling-fixes.md](../design/09-path-coupling-fixes.md) 第 1.1、1.3、1.4、1.5（Commerce 行）、2.17、4.3（commerce 生成器与两个测试、`generate-oe-client-schemas.mjs`）；[design/10-test-baseline-and-cleanup.md](../design/10-test-baseline-and-cleanup.md) 第 1 节 Commerce 行、2.2、3.1（acquiring / consumer 行）、3.2、4.1–4.3、5.1、6。
- [analysis/commerce.md](../analysis/commerce.md) 全文（第 1 节命名陷阱、第 4 节事务、第 5 节 Java 调用、第 9 节 10 个热点）。
- 第 00、01、04、05 步报告（client-master-api 的已存卡开关接口合同、payment 的新发现名 `payment-service`、arch-rules 口径）。
- 现有 `commerce-service/`：`CreateCheckoutProcessor`、`CustomerService(Impl)`、`CardSavingEligibility`、`GetCustomerCheckoutProcessor`、`SubmitCustomerPaymentProcessor`、`PaymentStoredCardClient(Impl)`、`StoredCardInvalidationHandler`、`CheckoutDataMocker`、`CustomerTestMapper`、`application.yml`、`api/openapi/client-v1.yaml`、`docs/api/customer-checkout.openapi.json`。
- 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/decisions/2026-09-05-commerce-client-api-v1.md`（Client API 合同不变）。

## 2. 模块映射

| 现 | 目标 |
|---|---|
| `paytech-commerce-client-api`（订单、Checkout、退款、可退金额合同与 `CommerceCheckoutErrorCode`、`OrderCreationMode`、`Cancellation*`） | `paytech/acquiring/paytech-acquiring-api`；其中 Customer 合同（`POST/GET/DELETE /customers…`、`/customers/{no}/stored-cards…`）与 `CUSTOMER_NOT_FOUND`、`INVALID_CONSUMER_REFERENCE`、`STORED_CARD_NOT_FOUND` 三个错误码 → `paytech/consumer/paytech-consumer-api` |
| `paytech-commerce-customer-api`（`CustomerCheckoutApi`、`CustomerPaymentApi`、`CustomerCheckoutErrorCode`、`CustomerPaymentStatus`，**消费者端结账合同，属 checkout 模块**） | `paytech-acquiring-api`（包 `checkout`） |
| `paytech-commerce-web` 除消费者核心外全部（transaction / checkout / command 三个内部模块，按 03 §2 表归属分包 `transaction`、`checkout`、`command`，共用一个 `-core`） | `paytech-acquiring-core`；Controller（Client API 与 Customer API）、启动类、配置、MQ 监听、补偿 Handler 装配 → `paytech-acquiring-app` |
| 消费者核心（analysis §1 列表：`Customer` DO、`CustomerMapper`、`CustomerRepository(Impl)`、`CustomerService(Impl)`、`CustomerStatus`、`ConsumerReferencePolicy`、`CreateCustomer / GetCustomer / DeleteCustomer / ListStoredCards / DeleteStoredCard` Processor、`StoredCardInvalidationHandler`、`PaymentStoredCardClient(Impl)`、`ClientCustomerController`、`ClientCustomerConverter`） | `paytech-consumer-core` / `paytech-consumer-app` |
| `paytech-commerce-test` | 按 10 §2.2 拆：consumer 2 类 7 例整类搬入；acquiring 31 类 278 例（其中 2 类 9 例改写） |
| `docs/`、`api/openapi`、`README.md` | 随 acquiring；PDManer 拆 `acquiring.pdma.json`、`consumer.pdma.json`，Schema SQL 按归属切片（`t_client_stored_card_setting` 不再出现在任何 Commerce 后继 Schema） |

删除 `commerce-service/mvnw*`；旧根消失。`paytech/pom.xml` 取消 `acquiring`、`consumer` 两行注释。应用名 `acquiring-service`（18102 / 19102，现 `commerce-service`）、`consumer-service`（18109 / 19109）；库 `mor_acquiring`（现 `mor_commerce` 改名）、`mor_consumer`。context path：acquiring 保持 `/commerce`（Gateway 路由不变，改名放到第 13 步统一评估并在报告中列为待确认）。

## 3. 代码与逻辑改动清单

1. **consumer-api 新增内部合同**（供 acquiring）：
   - `POST /consumer/v1/customers/resolve` `{clientId, consumerReference}` → `{customerId, customerNo, status, created: boolean}`：现 `CustomerService.resolveActiveCustomer` 的语义（建或取，`uk(client_id, active_reference)` 幂等，DuplicateKey 重读）；类 B 命令，调用方在订单事务**之前**调用（现状即如此，非原子性保持，analysis §4）。
   - `GET /consumer/v1/card-saving-eligibility?clientId=&customerId=` → `{eligible, reason: CUSTOMER_NOT_FOUND|CUSTOMER_INACTIVE|CLIENT_MISMATCH|SETTING_DISABLED|null, customerNo}`：**消费者服务组合** Customer 状态（本库）与 Client 已存卡开关（经 `client-master-api` `GET /client-master/v1/clients/{clientId}/stored-card-setting`，第 04 步合同）；client-master 不可用 → 返回 `eligible=false, reason=SETTING_UNAVAILABLE`（失败关闭，S6）。
   - `GET /consumer/v1/customers/{customerId}` 供 `SubmitCustomerPaymentProcessor.buildPayer` 取 `customerNo`（作为 Payment `consumerReference`）。
2. **acquiring 侧**：`CardSavingEligibility` 改为调用上述资格接口（GET checkout 与 SubmitPayment 两处，均在事务外），任何失败降级为"不可存卡"并记 WARN（S6），GET checkout 的已存卡列表继续经 Payment `StoredCardApi` 读取且不可用时为空（现状）；`CreateCheckoutProcessor.attachCustomer` 改调 `resolve`；`buildPayer` 改调 `GET customers/{id}`。`ClientStoredCardSettingRepository`、`ClientStoredCardSettingMapper` 从 Commerce 后继中**删除**（表已归 client-master）。
3. **Payment 发现名**：`PaymentClientConfiguration` 的 `payment-orchestration-service` → `payment-service`（第 05 步已改名）；`CheckoutCommercialPort` 仍为 Mock，仅把 TODO 中的目标服务名改为 `commercial-service`（第 04 步）。
4. **死表与死代码（C7，analysis §9 第 5、10 点）**：删除 `t_command_idempotency`（Schema / 模型）；`t_commerce_order` 的 `preparation_command_id`、`context_format`、`termination_requested_at` 三列与 SQL 中 `preparation_command_id IS NULL` 守卫、`CheckoutSessionStatus.PREPARING` 取值一并删除（Schema、PDManer、DO、Mapper、测试 DDL）；删除 `CheckoutRepository(+Impl)`、`CheckoutSessionMapper.expire`、`CheckoutDocumentLinkClient.links(FrozenCheckoutRecord)`、未使用的 `client-contract-api` 依赖、过时的 `application-auto.yml`。列删除是 Schema 文本变化，只改评审 SQL 文本，不执行。
5. **MQ 消费者**：`PaymentSucceededListener` 随 acquiring，继续消费旧 `topic_payment_result`（第 08 步切到新信封）；补偿 Handler `CLIENT_WEBHOOK_REQUESTED`、退款提交随 acquiring，`STORED_CARD_INVALIDATION` 随 consumer（各自 `t_compensation_event`）。
6. **装配**：两个 app 各自 DataSource / 事务管理器 / `@MapperScan` / TypeHandler / Snowflake worker / 补偿配置 / Redis（acquiring：`order:{id}` TRY_LOCK；consumer：无锁则不引入 Redis）；配置前缀 `acquiring.*`、`consumer.*`。
7. **Enforcer**（09 §1.4）：Commerce 禁令复制到两个聚合 POM（`paytech-vault-client` 禁令两边保留），模块 parent = 服务聚合 POM；`failIfNoSpecifiedTests` 两边各一对。
8. **路径修复**（09 §4.3）：`generate-commerce-model.mjs` 拆两份；`commerce-model.test.mjs`、`commerce-scenario-doc.test.mjs` 两份；`generate-oe-client-schemas.mjs` / `oe-client-model.test.mjs` 的 Commerce 条目改为两个目录。
9. **OpenAPI**：`api/openapi/client-v1.yaml` 路径不变（Customer 路由仍由 Gateway 路由到 consumer-service；Gateway 路由配置属 Platform 数据，本步只在报告列出需要新增的 Route 目标）。

## 4. 测试要求（10 §2.2）

- 基线：搬迁前 `cd commerce-service && ./mvnw -B verify` 实际数（源码核实 285）。
- consumer：`CustomerApiTest`、`StoredCardApiTest` 整类搬入（`AbstractCustomerApiTest` 的 Client 隔离改用 `CustomerDataMocker.clientId()`）；新增 `resolve` 幂等（并发两次同 reference 只建一个）、资格接口四种 reason、client-master 不可用失败关闭 ≥ 6 例；`ClientMasterStoredCardSettingMocker`（DTO Mock）；`ArchitectureAutoTest`。
- acquiring：29 类整类搬入；`CreateCheckoutCustomerTest`、`StoredCardCheckoutTest` 改用 `ConsumerMocker`（consumer-api DTO）并新增"资格读失败降级为不可存卡"用例；`CustomerTestMapper` 不再出现在 acquiring；`SaveConsumer*` 留在 acquiring（命名陷阱）；`ArchitectureAutoTest`。
- 清理器：`CheckoutDataMocker.clear()` 去掉 Customer 三行与 `checkCustomerTables()`；consumer 新建 `CustomerDataMocker`（Clear）与收缩后的 `CustomerTestMapper`；`init/ddl.sql` 两份按 10 §4.2（去掉 `t_command_idempotency` 与三个残留列）。
- 本地库：`mor_commerce` → `mor_acquiring`（新建并应用测试 DDL，不删旧库）、新增 `mor_consumer` 与两个账号（init 脚本、`.env.example`、手工执行，命令写进报告）。
- 禁止 `when(...).thenReturn`、`@MockBean`。

## 5. 验证清单

1. 前后测试数；`cd paytech && ./mvnw -B verify -pl acquiring/paytech-acquiring-test,consumer/paytech-consumer-test -am` 全绿；全 Reactor 全绿。
2. 两个 app 以 `mock` Profile 启动 health UP。
3. `node --test scripts/pdmaner/commerce-*.test.mjs scripts/pdmaner/oe-client-model.test.mjs`（改名后）通过。
4. `git log --follow` 抽查 `CreateCheckoutProcessor.java` 与 `CustomerServiceImpl.java`；旧根不存在。
5. R4 零违规。

## 6. 禁止事项

- 不改 Client API / Customer API 对外合同与路径；不改订单、结账、退款、取消的状态机与 CAS 语义；不接通 CCT / 税务 / 文档 Mock。
- 不改 client-master、payment、platform 目录；不执行业务库 SQL（测试库除外，须记录）。
- 不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送。

## 7. 交付物

1. 短分支提交，前缀 `refactor(step-03): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/03-acquiring-consumer-report.md`：改动摘要 ≤ 30 行、第 5 节逐项、consumer-api 合同清单、Gateway 需新增的路由目标、与设计偏差、待确认项。
3. 最终回复两行。
