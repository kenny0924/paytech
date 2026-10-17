# 第 03 步报告 · Commerce → acquiring + consumer（两库两 app）

- 分支：`refactor/step-03-acquiring-consumer`（起点 `refactor-main@572d7bb9`）。提交：`ec6dfbe9` 纯 `git mv` 搬迁（内容未改，单独不可编译）、`6ebc4489` 包名与逻辑拆分及测试、`5f0ce892` PDManer / Node / 调用方名 / 本地库 / 文档链接；本报告单独提交。
- 结论：第 5 节各项已真实执行。任务卡命令 BUILD SUCCESS，acquiring 801 例、consumer 21 例，0 失败 0 跳过，合计 822 例，不少于搬迁前基线 808 例。commercial-test 24 / 24、platform-support-test 798 / 798 均已重跑。两个 app 以 mock Profile 启动，health 为 UP。Node 测试全部通过，其中 Commerce 相关 4 个测试文件在起点有 13 项失败，本步后全部通过。R4 零违规。全 Reactor 按 team-lead 决定不在本步运行，合入后由主会话统一执行。
- **需要先看**：任务卡要求删除 `t_commerce_order.termination_requested_at` 和 `CheckoutSessionStatus.PREPARING`，本步**均保留**，team-lead 已接受。前者是取消状态机正在读写的列；后者只保留"读容忍"，用于兼容历史数据读取，列为后续删除候选。依据见第 5 节第 1、2 项。

## 1. 改动摘要

1. 模块：
   - `paytech/acquiring/` 下四个模块：`paytech-acquiring-api` 合并 client-api 与 customer-api，消费者端合同在包 `checkout`；`-core` 中 transaction / checkout / command 共用一个模块；另有 `-app`、`-test`。
   - `paytech/consumer/` 同样四个模块。
   - 包根改为 `com.paytech.acquiring` 与 `com.paytech.consumer`；`paytech/pom.xml` 只取消两行注释。
   - 旧根 `commerce-service/`（含 `mvnw`、`.mvn`）已不存在。
2. 应用：
   - `acquiring-service`：18102 / 19102，Context Path 仍为 `/commerce`，库 `mor_acquiring`。
   - `consumer-service`：18109 / 19109，无 Context Path，库 `mor_consumer`。
   - 配置前缀改为 `acquiring.*` / `consumer.*`。Snowflake 节点各自独立，测试中分别为 worker 0 / 1。
3. consumer-api 新增三个内部接口（合同见第 3 节）：
   - 建或取：复用原 `CreateCustomerProcessor`，按 `uk(client_id, active_reference)` 幂等，DuplicateKey 时重读获胜行。
   - 按主键读取：包含已删除的 Customer。
   - 存卡资格：组合本库 Customer 与 client-master 的开关，client-master 不可用时返回 `SETTING_UNAVAILABLE`，失败关闭。
4. acquiring 侧改动：
   - `CreateCheckoutProcessor.attachCustomer` 在订单事务前调用 resolve。
   - `CardSavingEligibility` 改调资格接口，用于 GET checkout 和 SubmitPayment 两处，都在事务外。任何失败都降级为不可存卡并记 WARN。
   - `buildPayer` 改调 GET customer；返回 404 时按访客处理，用订单号作付款人引用，与原来"无 Customer"一致。
   - 已存卡列表仍经 Payment `StoredCardApi` 读取，acquiring 保留只读列表端口。
   - 删除 `ClientStoredCardSetting` 的 Mapper 与 Repository。
5. 出站与调用方名：
   - 出站：Payment 发现名改为 `payment-service`；新增 `consumer-service` 客户端，登记 `ConsumerErrorCode`。
   - 两个 app 都引入 internal-auth，`mode: "OFF"`，所有出站客户端经 `InternalTokenClientOptions` 接线。
   - `CheckoutCommercialPort` 仍为 Mock，TODO 中改为 `commercial-service`。
   - acquiring 提交 Platform Webhook 时，`X-Caller-Service` 与载荷 `source.system` 改为 `acquiring-service`。
   - commercial 的 Checkout 解析只接受 `acquiring-service`。
6. MQ 与补偿：
   - `PaymentSucceededListener` 随 acquiring 迁移，仍消费 `topic_payment_result`；队列按 design/10 §5.3 改名为 `cg_acquiring_payment_result`，DLX / DLQ 同步改名。
   - `CLIENT_WEBHOOK_REQUESTED` 与退款提交补偿归 acquiring，`STORED_CARD_INVALIDATION` 归 consumer，各用本库 `t_compensation_event`；两个服务各有 `compensation-event-catalog.yaml`。
7. C7 删除项：`t_command_idempotency`、`preparation_command_id`、`context_format` 三项，涉及 Schema、模型、DO、Mapper、守卫 SQL 与测试 DDL；另删 `CheckoutRepository(+Impl)`、`CheckoutSessionMapper.expire`、`CheckoutDocumentLinkClient.links(FrozenCheckoutRecord)`、`client-contract-api` 依赖与 `application-auto.yml`。
8. Enforcer：两个聚合 POM 复制 Commerce 禁令（`paytech-vault-client` 禁令保留），并加 `*-core` / `*-app` 通配禁令；consumer 不放行 Redis 与 AMQP。`failIfNoSpecifiedTests` 两边各一对：聚合为 false，test 为 true。
9. 覆盖率门禁：Hosted Checkout 0.90 行覆盖率门禁在起点即失败（0.85，见第 2.1 节），与 Platform 同口径移入 `-Pcoverage-gate`，classfiles 同时取 core 与 app。
10. PDManer：
    - 生成器改为共享 `commerce-model-core.mjs`，加 `generate-acquiring-model.mjs` 与 `generate-consumer-model.mjs` 两个入口，两份模型 `--check` 通过。
    - Schema 拆为 `acquiring-schema.sql`（14 表）与 `consumer-schema.sql`（2 表）。
    - 两份测试 DDL 与 Schema 逐表逐字一致，由 Node 测试强制。
11. Node 与本地环境：
    - 测试 `commerce-model` 与 `commerce-scenario-doc` 拆为 acquiring / consumer 各一份；`oe-client-model`、`pdmaner-canonical-paths`、`client-contract-model` 改用新路径。
    - local-environment 初始化脚本与 `.env.example` 增加两库两账号，沿用第 07 步的"可选创建"写法；compose 文件未改。
12. arch-rules：
    - 登记 R2、R3 过渡偏差（第 5 节第 6 项）。
    - Reactor 依赖图测试的"过期登记"只核对 Maven 坐标主体，类级登记交给服务 ArchitectureAutoTest。
13. 文档：搬入文档的出向相对链接按新深度重写 81 处；两份 README 补充拆分说明；物理决策改名为 `ACQUIRING_PHYSICAL_DECISIONS.md`，新增 `CONSUMER_PHYSICAL_DECISIONS.md`。

## 2. 第 5 节验证（实际命令与结果）

所有 Maven 命令前执行 `export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`，并从主工作区 `local-environment/.env.local` 导出 `MOR_RABBITMQ_*`、`MOR_REDIS_PASSWORD` 与 `PAYTECH_REDIS_PASSWORD`。凭据未打印，也没有 up / down / restart 任何容器。

### 2.1 测试数与 Reactor

**搬迁前基线**：用 `git archive 572d7bb9 commerce-service` 导出到 scratchpad，在其中执行 `./mvnw -B verify`。

- Surefire 执行 **808** 例，0 失败 0 错误 0 跳过；源码方法数 **285**，与 design/10 一致。
- 构建结果为 BUILD FAILURE，唯一原因是 `enforce-commerce-checkout-coverage`：行覆盖率 0.85 低于 0.90。这是既有失败，不是测试失败。

**任务卡命令**：

```bash
cd paytech && ./mvnw -B verify -pl acquiring/paytech-acquiring-test,consumer/paytech-consumer-test -am \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
```

结果为 **BUILD SUCCESS**（3:15 min，2026-10-17T20:48:58Z–20:52:15Z）；上游 Foundation、arch-rules 等模块全部通过。

| 模块 | `<testcase>` | `<skipped>` | 源码方法 | 方法下限（10 §1.2） |
|---|---:|---:|---:|---:|
| paytech-acquiring-test | 801 | 0 | 281 | 278 |
| paytech-consumer-test | 21 | 0 | 16 | 7 |
| 合计 | **822**（基线 808） | 0 | **297**（基线 285） | 285 |

**类映射**（旧 → 新，执行数）：

| 旧类（Commerce） | 新类（服务） | 旧 → 新 | 说明 |
|---|---|---|---|
| `CustomerApiTest`、`StoredCardApiTest` | 同名，consumer | 7 → 7、3 → 3 | 整类搬入，只改包、基类（`ConsumerMockTestBase`）、错误码枚举（`ConsumerErrorCode`，Code 值不变）；Client 隔离改用 `CustomerDataMocker.clientId()` |
| `CreateCheckoutCustomerTest` | 同名，acquiring | 3 → 4 | 改用 `ConsumerMocker`（consumer-api DTO），断言订单只存 Customer 主键、resolve 调用次数；新增 `test_consumerUnavailableCreatesNoOrder`（503，不建订单） |
| `StoredCardCheckoutTest` | 同名，acquiring | 6 → 8 | 改用 `ConsumerMocker` 提供 Customer、编号与资格；新增 `test_eligibilityFailureDegrades`（consumer 503 / 响应无效两例）：不可存卡、不调用 Payment 列表，保存卡 422，普通卡支付不受影响 |
| `SaveConsumerTest` | 同名，acquiring | 75 → 74 | 删除参数 `"preparation"`：它通过 `preparation_command_id` 制造旧准备锁，该列已按 C7 删除；同类锁定由仍保留的 `"accepted"` 覆盖 |
| `CancelOrderTest.test_pendingLegacyPreparation` | `test_pendingAfterAcceptanceWithoutPayment` | 1 → 1 | 原例以 `preparation_command_id` 阻止本地取消；改为"已接受法律文件、尚无支付关联"，这是取消仍走 Payment 终止的真实路径，断言不变 |
| 其余 29 类 | 同名，acquiring | 同数 | 只改包名、基类与注入名 |
| — | consumer `ResolveCustomerApiTest` / `CardSavingEligibilityApiTest` | 新增 4 / 6 | 覆盖幂等、并发两次同 reference 只建一个、个人原值、按主键读取（有效 / 已删除 / 404）；资格可用、四种原因、client-master 不可用 / 断连 / 响应无效时失败关闭 |
| — | 两个 `ArchitectureAutoTest` | 新增 1 / 1 | R1–R12，含 R4 |

**全 Reactor：未运行。** 按 team-lead 决定，合入后由主会话统一运行。本步改动影响到的其他模块已单独运行：

- commercial-test：`./mvnw -B -o verify -pl commercial/paytech-commercial-test -am '-Dtest=com/paytech/commercial/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false`，BUILD SUCCESS，**24 / 24**、0 跳过，与第 04 步相同。改动只有调用方名与测试常量。
- platform-support-test：`./mvnw -B -o verify -pl platform-support/paytech-platform-support-test -am '-Dtest=com/paytech/platform/**/auto/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false`，BUILD SUCCESS（3:58 min），**798 / 798**、0 跳过，与第 02a 步相同。
  - 第一次运行时 `-Dtest` 写成 `com/paytech/platform/**/*Test`，误把 `manual` 用例 `SesRealSendTest` 选进来，它因缺少 SES 环境变量失败（另 798 例全过）。改为只选 `auto` 后重跑通过。
- arch-rules：57 / 57；arch-rules-reactor-test：3 / 3。
- Payment 与两个 BFF 没有改动，未单独运行。

**覆盖率门禁**：`-Pcoverage-gate` 下复测为 0.86（起点 0.85），仍低于 0.90，见第 2.6 节。

### 2.2 两个 app 以 mock Profile 启动

从 target 复制两个 exec jar 到 scratchpad 后执行：

```bash
java -jar paytech-acquiring-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock \
  --spring.config.additional-location=file:<acquiring-test>/application-mock.yml \
  --server.port=28102 --management.server.port=29102 --acquiring.test.external.base-url=http://127.0.0.1:9
java -jar paytech-consumer-app-1.0.0-SNAPSHOT-exec.jar --spring.profiles.active=mock \
  --spring.config.additional-location=file:<consumer-test>/application-mock.yml \
  --server.port=28109 --management.server.port=29109 --consumer.test.external.base-url=http://127.0.0.1:9
```

- `Started AcquiringApplication in 2.806 seconds`，`Started ConsumerApplication in 1.88 seconds`；两份日志中 ERROR 行均为 0。
- 两个服务的 `/actuator/health` 都返回 `{"groups":["liveness","readiness"],"status":"UP"}`，`/health/liveness` 与 `/health/readiness` 都返回 200 `{"status":"UP"}`。
- 验证后已停止进程并删除复制的 jar。

### 2.3 Node

命令为 `node --test scripts/pdmaner/<文件>`。"起点"一列是在 `git archive 572d7bb9` 的完整导出上运行同一命令的结果。

| 文件（改名后） | 起点（改名前文件） | 现在 |
|---|---|---|
| `acquiring-model.test.mjs` | `commerce-model.test.mjs`：6 / 17，11 项失败 | 9 / 9 |
| `consumer-model.test.mjs` | — | 9 / 9 |
| `acquiring-scenario-doc.test.mjs` | `commerce-scenario-doc.test.mjs`：6 / 7 | 7 / 7 |
| `consumer-scenario-doc.test.mjs` | — | 4 / 4 |
| `oe-client-model.test.mjs` | 9 / 10，失败为 commerce schema drift | 10 / 10 |
| `pdmaner-canonical-paths.test.mjs` | 2 / 2 | 2 / 2 |
| `client-contract-model.test.mjs` | 83 / 83 | 83 / 83 |

附加检查：
- `node scripts/pdmaner/generate-acquiring-model.mjs --check` 输出 up to date（14 tables），`generate-consumer-model.mjs --check` 输出 up to date（2 tables）。
- `node scripts/pdmaner/generate-oe-client-schemas.mjs --check` 12 份 Schema 全部 Verified。
- 起点失败的根因：生成器缺 `customer_id` 与两张 Customer 表；`t_consumer_acceptance.command_idempotency_id` 的模型可空性与已执行 Schema 不一致；旧测试 Oracle 停留在更早版本。新的共享清单已与已执行 Schema 逐表逐字一致。
- `node scripts/verify-local-environment.mjs`：退出码 1，输出与改动前逐字相同，只有 4 行既有的 Gateway 合同文档引用失败。

### 2.4 历史与旧根

- `git log --follow` 追溯 `paytech/acquiring/.../service/processor/checkout/CreateCheckoutProcessor.java`，输出 `6ebc4489`、`ec6dfbe9`、`5ed14379`、`9653d816`、`fff35d2e`。
- `git log --follow` 追溯 `paytech/consumer/.../domainservice/service/impl/CustomerServiceImpl.java`，输出 `6ebc4489`、`ec6dfbe9`、`9653d816`。
- `ls commerce-service` 返回 No such file or directory；`git ls-files commerce-service` 为 0 行。

### 2.5 R4 零违规

- `node paytech/tools/arch-rules/scripts/generate-table-ownership.mjs --check` 输出 `table-ownership.yaml is current (265 physical tables + 56 retained).`。白名单本身未改：acquiring 14 表，consumer 为 `t_customer` 与 `t_compensation_event`。
- 两个服务的 `ArchitectureAutoTest` 通过，R4 无违规、无新增 R4 登记。acquiring 的 Mapper XML 只引用本库 14 表，consumer 只引用 `t_customer`。

### 2.6 覆盖率门禁（`-Pcoverage-gate`）

命令：`./mvnw -B -o verify -Pcoverage-gate -pl acquiring/paytech-acquiring-test -am '-Dtest=com/paytech/acquiring/**/*Test' -Dsurefire.failIfNoSpecifiedTests=false -Dpaytech.local-environment.dir=…`。

- 结果：801 例全部通过；门禁 `enforce-acquiring-checkout-coverage` 报 `lines covered ratio is 0.86, but expected minimum is 0.90`，BUILD FAILURE。
- 统计范围与搬迁前相同（core 与 app 的同一组包），比起点的 0.85 略高。门禁按设计不在默认 `verify` 中执行，覆盖率不足属既有问题，留待单独修复。

### 2.7 本机测试库（root，运行中的 `mor-local-mysql-1`）

2026-10-17T20:29:03Z 执行，root 密码取自 `.env.local` 的 `MOR_MYSQL_ROOT_PASSWORD`，经 `docker exec -e MYSQL_PWD` 传入，未出现在命令文本或输出中：

```bash
MYSQL_PWD="$(grep -E '^MOR_MYSQL_ROOT_PASSWORD=' …/local-environment/.env.local | cut -d= -f2- | tr -d '"')" \
  docker exec -i -e MYSQL_PWD mor-local-mysql-1 mysql --default-character-set=utf8mb4 -uroot \
  < paytech/acquiring/paytech-acquiring-test/src/test/resources/init/ddl.sql
# consumer 同一写法，输入 paytech/consumer/paytech-consumer-test/src/test/resources/init/ddl.sql
```

- 两份 DDL 的内容：`CREATE DATABASE IF NOT EXISTS`、`CREATE USER IF NOT EXISTS`（仓库默认密码 `local-acquiring-change-me-2026` / `local-consumer-change-me-2026`）、`GRANT SELECT, INSERT, UPDATE, DELETE` 与全部 `CREATE TABLE`。
- 核对结果：`mor_acquiring` 14 表，`mor_consumer` 2 表；`SHOW GRANTS` 显示两个账号都只有本库的四项权限。
- `mor_commerce`（16 表）保留未动；没有执行任何业务库 SQL。

## 3. consumer-api 合同清单

服务发现名为 `consumer-service`，无 Context Path。ID 一律是规范正十进制 BIGINT 字符串。

| 合同 | 方法与路径 | 调用方 | 请求 → 响应 |
|---|---|---|---|
| `ClientCustomerApi`（Client API，原 Commerce 合同原样迁入） | `POST /client-gateway-api/v1/customers`、`GET /client-gateway-api/v1/customers/{customerNo}`、`GET /client-gateway-api/v1/customers?consumerReference=`、`DELETE /client-gateway-api/v1/customers/{customerNo}`、`GET …/{customerNo}/stored-cards`、`DELETE …/{customerNo}/stored-cards/{storedCardToken}` | Gateway（Client API Channel） | 与原 Commerce 相同：201 + Location 或 200、404 `CUSTOMER_NOT_FOUND`、422 `INVALID_CONSUMER_REFERENCE`、404 `STORED_CARD_NOT_FOUND`、Payment 不可用 503 |
| `ConsumerCustomerApi#resolveCustomer` | `POST /consumer/v1/customers/resolve` | acquiring（订单事务前） | `{clientId, consumerReference}` → `{customerId, customerNo, status, created}`；字符集不符或像电话号码时 422 |
| `ConsumerCustomerApi#getCustomer` | `GET /consumer/v1/customers/{customerId}` | acquiring（`buildPayer`） | → `{customerId, customerNo, clientId, status}`，含已删除，不返回引用原值；不存在时 404 `CUSTOMER_NOT_FOUND` |
| `ConsumerCustomerApi#cardSavingEligibility` | `GET /consumer/v1/card-saving-eligibility?clientId=&customerId=` | acquiring（GET checkout、SubmitPayment） | → `{eligible, reason, customerNo}` |

`cardSavingEligibility` 的 `reason` 取值如下，判断顺序与表格从上到下一致：

| reason | 条件 | 是否返回 `customerNo` | 是否读取 client-master 开关 |
|---|---|---|---|
| `CUSTOMER_NOT_FOUND` | Customer 不存在 | 否 | 否 |
| `CLIENT_MISMATCH` | Customer 属于其他 Client | 否 | 否 |
| `CUSTOMER_INACTIVE` | Customer 已删除 | 是 | 否 |
| `SETTING_DISABLED` | Client 未开通存卡 | 是 | 是 |
| `SETTING_UNAVAILABLE` | client-master 不可用、拒绝或响应无效（失败关闭） | 是 | 是 |
| null（`eligible=true`） | 以上都不成立 | 是 | 是 |

- 枚举：`ConsumerErrorCode`（`CUSTOMER_NOT_FOUND`、`INVALID_CONSUMER_REFERENCE`、`STORED_CARD_NOT_FOUND`、`UPSTREAM_PROTOCOL_ERROR`）、`CustomerStatus`、`CardSavingIneligibleReason`。
- 规则类：`ConsumerReferencePolicy`，同时被 acquiring 建单校验使用（design/03 §3）。
- consumer 的出站：Payment `StoredCardApi`（`payment-service`，`/payment`），以及 client-master `ClientStoredCardSettingApi`（`client-master-service`，第 04 步合同）。

## 4. Gateway 需新增 / 修改的路由目标（Platform 数据，本步未改）

| 项 | 现值 | 应改为 |
|---|---|---|
| `t_gateway_target` Hosted Checkout（`commerce-checkout-api`，CHECKOUT Channel） | `discovery_service_name=commerce-service` | `acquiring-service`；Context Path 仍为 `/commerce`，路径不变 |
| `t_gateway_target` Client Public API（`commerce-client-api`，CLIENT_API Channel） | `discovery_service_name=commerce-service` | `acquiring-service`，承载订单、Checkout、取消、退款路由 |
| **新增** Target，如 `consumer-client-api`（CLIENT_API Channel） | — | `discovery_service_name=consumer-service`。Customer 路由 `/client-gateway-api/v1/customers`、`/customers/{customerNo}`、`/customers/{customerNo}/stored-cards`、`/stored-cards/{storedCardToken}` 改指向它。consumer 无 Context Path，下游路径是 `/client-gateway-api/v1/customers…`，不再带 `/commerce` 前缀 |
| Route Scope / Guard | 原 Customer 路由挂在 Commerce Target 下 | 迁到新 Target 后保持原 API Scope、Guard 与限流配置 |

- consumer 的内部合同 `/consumer/v1/**` 只走私网服务调用，不经 Gateway。
- Platform 侧还有一项相关配置：Webhook 内部调用方白名单 `webhook.http.callers`（服务名 → 可提交的事件类型；`application.yml` 的注释示例为 `commerce-service: [order.paid, refund.succeeded]`）需把登记的服务名改为 `acquiring-service`，否则 Client Webhook 会被 Platform 拒绝。默认值为空，即全部拒绝，实际值来自部署配置。

## 5. 与设计 / 任务卡的偏差

1. **`termination_requested_at` 未删除（team-lead 已接受）。** 取消状态机读写这一列，下列位置均在 `paytech-acquiring-core` 中：
   - 写入：`CommerceOrderMapper.xml:102` 的 `requestTermination`，执行 `SET termination_requested_at=#{now}`，条件为第 104 行的 `termination_requested_at IS NULL`；同时 `CommerceOrderRepositoryImpl.java:92` 更新对象。
   - CAS 条件：
     - `CommerceOrderMapper.xml:115`：`cancel` 要求 `termination_requested_at IS NOT NULL`，即先登记终止意图，Payment 终止后才能取消。
     - `CommerceOrderMapper.xml:7`：`recordPaymentSubmission` 要求该列 `IS NULL`，终止后不得再受理支付。
     - `CommerceOrderMapper.xml:29`：`recordConsumerChange` 要求该列 `IS NULL`，终止后不得改资料。
   - Java 读取：
     - `OrderCancellationServiceImpl.java:34`、`:53`：判断是否已登记终止意图，决定是否补登。
     - `SubmitCustomerPaymentProcessor.java:187`：`checkNewSubmission`。
     - `CheckoutQueryContext.java:58`：`isOpen`。
     - 另有 `CommerceOrder.checkConsumerEditable` / `allowsPayment`。
   - 查询投影：`OrderQueryMapper.xml:12`、`:68` 与 `BasicCheckoutMapper.xml:26`、`:57`。
   - 删除这一列会取消两段式取消，与"取消的状态机与 CAS 语义一律不改"冲突。analysis/commerce.md §2 把它与准备期残留列并列，判断有误。
2. **`CheckoutSessionStatus.PREPARING` 只保留"读容忍"（team-lead 已接受）。**
   - 没有任何生产写入路径产生 PREPARING：会话在 `CreateCheckoutProcessor.java:238` 直接以 `OPEN` 创建，其余状态写入只有 `PAYMENT_PROCESSING`、`COMPLETED`、`CANCELLED`。
   - 与之配套的 SQL 守卫 `preparation_command_id IS NULL` 已随该列删除，原在 `recordConsumerChange` 与 `cancel` 的 localOnly 分支。
   - 枚举值保留，只为兼容历史数据读取：库中若有存量 PREPARING 行，`CodeEnumTypeHandler` 仍能映射，并按原语义被接受。接受它的位置如下：
     - SQL：`CommerceOrderMapper.xml:10`、`:35`；`CheckoutSessionMapper.xml:58`、`:63`。
     - Java：`CheckoutSession.java:53`、`:60`；`CancelOrderProcessor.java:133`；`GetOrderProcessor.java:131`、`:216`。
   - 测试也依赖这个"读容忍"：`GetOrderDataBuilder` 的"待准备"夹具（GetOrder、Cancel、可退金额等测试类共用）、GetOrderSuccessTest 对 `checkout.status=PREPARING` 的断言、SaveConsumerTest 的 `@EnumSource` 排除项。
   - **列为第 13 步或后续切片的删除候选**：删除时需要一并去掉上述 SQL `IN` 列表与 Java 判断，确认存量数据中没有 PREPARING 行，并把相关测试夹具改为当前可产生的状态。
   - 本步只删了 `preparation_command_id`（从不被写入，所有守卫恒为真，删除后对所有可产生的数据逻辑等价）与 `context_format`。
3. **`t_consumer_acceptance.command_idempotency_id` 保留，并改为可空的历史引用列。**
   - 拆分前模型写的是 NN，已执行的 Schema 与测试 DDL 写的是 NULL，注释为"仅历史请求幂等记录引用；新支付提交不写入"。
   - 模型已与可执行 Schema 对齐。任务卡没有列出这一列，未删除，见待确认。
4. **未新增 `paytech-commercial-api` 依赖。** 任务卡第 1 节说 `client-contract-api` 改为 `paytech-commercial-api`，第 3 节第 4 项又要求删除未使用的 `client-contract-api`。Commerce 主代码没有引用它，`CheckoutCommercialPort` 仍是固定 Mock，所以只删除不替换，把 TODO 目标写为 `commercial-service`。
5. **`ConsumerErrorCode` 多一个 `UPSTREAM_PROTOCOL_ERROR`。** 原 Client API 在 Payment 已存卡响应无效时返回 502 `UPSTREAM_PROTOCOL_ERROR`，这个码原在 `CommerceCheckoutErrorCode`。consumer 需要同 Code 值才能保持 Client API 行为不变，因此在三个码之外加入。acquiring 保留自己的同名码。
6. **新登记两组过渡偏差（`known-deviations.yaml`）。**
   - R2：`paytech-acquiring-api` 依赖 `jackson-annotations` 与 `jackson-databind`，`paytech-consumer-api` 依赖 `jackson-annotations`。这些 DTO 沿用 Commerce Client API 的线上合同，口径与 Platform api 相同。
   - R3：consumer（领域层）的 core 经 `paytech-payment-api` 的 `StoredCardApi` 列出、删除、失效已存卡，共 1 条模块级、26 条类级登记。该合同属卡域，第 12 步迁入 card-api 后消除。
   - 类级登记的消息中带源码行号，修改 `PaymentStoredCardClientImpl` 时需同步。
   - Reactor 依赖图测试的"过期登记"判定改为只看 Maven 坐标主体的登记，因为依赖图规则不产出类级违规。
7. **队列改名为 `cg_acquiring_payment_result`（依据 design/10 §5.3）。** 部署切换时需要排空旧队列 `cg_commerce_payment_result`。交换机与路由键未变。
8. **调用方名改名（D42 改名口径，team-lead 已接受）。**
   - acquiring 提交 Platform Webhook 的 `X-Caller-Service` 与载荷 `source.system` 改为 `acquiring-service`，见 `ClientWebhookSubmissionHandler.CALLER_SERVICE`。切换前已登记的补偿载荷中冻结的仍是 `commerce-service`。
   - commercial：只改了调用方名与测试常量，没有改逻辑，涉及以下位置：
     - `CheckoutQueryController.authorize` 的白名单改为 `acquiring-service`。
     - `CheckoutContextApi` 的 JavaDoc 与 README 文案。
     - `AbstractCheckoutApiTest` 与 `AgreementPartyQueryHttpTest` 的常量。
     - 重跑结果为 24 / 24。
   - Platform：测试配置中的 Webhook 调用方白名单（`webhook-test`、`webhook-test-peer`）没有 `commerce-service` 样本。只同步了两处样本：`application.yml` 中 `webhook.http.callers` 注释示例改为 `acquiring-service`；`NotificationCallerPropertiesTest` 中"未登记调用方被拒绝"的样本改为 `acquiring-service`，用例语义不变。重跑结果为 798 / 798。
9. **环境变量名未改。** `COMMERCE_*`、`MOR_COMMERCE_DB_*` 沿用，只改了库名默认值，以免影响 test-environment 部署描述。
10. **consumer 不复制 Redis 与 AMQP 放行**，因为它没有锁，也没有 MQ 消费者，比"复制 Commerce 禁令"更严。补偿事件不经 MQ，consumer 不需要 RabbitMQ；design/10 §1.2 的表述有误。
11. **PDManer 模型由生成器重新生成。** 两份模型由共享清单生成，`--check` 通过；没有像第 05 步那样从已提交模型分区。`t_customer` 的字段、索引与注释与拆分前逐字一致，只是 PDManer 内部 ID 改为确定性 UUID（原 ID 是手写的）。拆分前的跨表关系 `t_commerce_order.customer_id → t_customer` 因跨库不再建模。
12. **测试 DDL 不含 `t_consumed_event`。** 两个服务都还不使用 messaging，第 08 步接入时再加。第 04 步曾加入它。
13. **`tools/run-customer-checkout-mock.sh` 只改了路径。** 它启动的类 `CustomerCheckoutMockApplication` 在仓库历史中从未存在，脚本本来就不可用。
14. **两个 api 的 POM 继续携带 Jackson 依赖**（见第 6 项）。consumer-api 已去掉未使用的 `jackson-databind`。

## 6. 待确认

1. `termination_requested_at` 保留（已接受）。`PREPARING` 的删除时机见第 11 项。
2. `t_consumer_acceptance.command_idempotency_id`：它引用的 `t_command_idempotency` 已删除，是否随后删除这一列。
3. Gateway Target 与 Route 的调整（第 4 节），以及 Platform Webhook 调用方白名单改为 `acquiring-service`：需要在 Platform 数据与部署配置中登记。
4. `cg_commerce_payment_result` 旧队列的排空与删除时机。
5. acquiring Context Path `/commerce` 是否改名：按任务卡留到第 13 步评估。
6. compose 文件未改：`local-environment/compose.yaml` 的 `mysql.environment` 需要透传 `MOR_ACQUIRING_DB_PASSWORD` 与 `MOR_CONSUMER_DB_PASSWORD`。在此之前，全新初始化的容器会跳过这两个库（可选创建），本机已手工建好。
7. 生产配置需要 `CONSUMER_SNOWFLAKE_REGION_ID` / `CONSUMER_SNOWFLAKE_WORKER_ID`，与 acquiring 分配不同的 worker，由实例清单分配；另外需要 consumer 的 Nacos、管理端口 19109 与健康检查地址。
8. Vault 测试样本 `vault/test/auto/pkg/policy/policy_test.go` 与 Payment `PayCardChannelTest` 中的 `commerce-service` 只是样本字符串，未改；Operations BFF 测试样本同样未改。
9. 搬入文档之外，仓库其他文档中指向 `commerce-service/` 的入向链接（design/09 §6）留到第 13 步统一重写；`agent-docs/` 正文未改。
10. 全 Reactor `verify` 由主会话在合入后统一运行，本步未执行。
11. `PREPARING` 枚举值与相关 SQL / Java 的读容忍列为第 13 步或后续切片的删除候选，见第 5 节第 2 项。
