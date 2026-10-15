# Customer 与存卡支付交付记录

## 适用阶段（草案，待确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2+3. 现状差异与方案设计 | 已确认（第 1 版，G-01～G-04 按建议） |
| 4. 研发实现 | 已确认（P1 已本地提交；C1、C2 未提交） |
| 5. 综合验证与本地验收 | 已完成，待确认（跨服务整链联调未做，见阶段 5 证据） |
| 6–7. test 发布准备与部署 | 待定 |
| 8. 提交 | P1、C1、C2 已本地提交（不推送、不部署） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 选题 | “先做存卡吧，存卡的话就要设计 customer 的模型了”；参考 Stripe 分析 Customer 创建与管理 | 讨论 Customer 模型 |
| 2026-10-15 | 选题 | “思路我觉得没问题”；已存卡支付每次要 CVV（a）、存卡需要按 Client 开通、认可 `consumer_reference` 的 Client 责任 | 继续讨论 |
| 2026-10-15 | 选题 | 两种创建方式“两条都做，开始写需求吧” | 登记需求并交付阶段 1 评审稿 |
| 2026-10-15 | 1 | 存卡同意“不需要正式的法律接收记录”；删除“参考 Stripe 就好”；卡组前缀不准确；“其余的按照建议来” | 修订需求（D-08～D-11），Q-01～Q-07 全部定论，待确认阶段 1 |
| 2026-10-15 | 1 | “可以” | 阶段 1 确认；进入阶段 2+3 |
| 2026-10-15 | 2+3 | “继续”（方案第 1 版与 G-01～G-04 建议） | 阶段 4：本会话只做 Payment 部分 P1；Commerce 部分待指定会话；本机建表另行授权 |
| 2026-10-15 | 4 | 对“授权在本机测试库执行 `db/payment/ddl.sql`”回复“继续” | 执行 Payment 增量 SQL 并运行测试 |
| 2026-10-15 | 4 | “你直接改 commerce 是否可以？现在没有 commerce 的会话不会冲突” | 本会话在本需求范围内修改 commerce-service，C1、C2 分段确认 |
| 2026-10-15 | 4 | “P1 先提交吧” | 本地提交 P1（Payment 部分）及本需求文档；不推送、不部署 |
| 2026-10-15 | 4 | 旧 CCT 预检链导致 Commerce 无法编译，选“删除旧预检链（推荐）” | 删除 6 个只服务于已废弃预检的文件及测试断言 |
| 2026-10-15 | 4 | 对“授权在本机 `mor_commerce` 执行 `db/commerce/ddl.sql`”回复“继续” | 执行 Commerce 增量 SQL 并运行测试 |
| 2026-10-15 | 4 | 对 C1 结果回复“继续” | C1 确认，进入 C2 |
| 2026-10-15 | 4 | 对“授权在本机 `mor_commerce` 执行 `db/commerce/ddl-stored-card-setting.sql`”回复“继续” | 建开关表并运行两边全量测试 |
| 2026-10-15 | 4 | 对阶段 4（C2）结果回复“继续” | 阶段 4 确认，进入阶段 5 |
| 2026-10-15 | 5 | 多选：本地提交 C1、C2；做跨服务整链联调；登记后续需求 | 本地提交（不推送、不部署），随后整链联调与登记后续需求 |

## 当前阶段与下一确认点

P1、C1、C2 已本地提交（提交说明含本需求目录名），未推送、未部署；跨服务整链联调进行中。

## 阶段 4 证据：P1 Payment（2026-10-15）

### SQL 执行记录

| 时间 | 目标 | 文件 | 结果 |
|---|---|---|---|
| 2026-10-15 | 本机 `mor-local-mysql-1`（127.0.0.1:13306）库 `mor_payment_channel_test`，root 账号 | [db/payment/ddl.sql](./db/payment/ddl.sql) | 执行前两张卡表均为 0 行；凭证表 9 列、交易工具表 8 列、已存卡表 14 列及两个唯一索引已核对；未触碰 mor-test |

### 代码与工件

| 范围 | 改动 |
|---|---|
| 模型/SQL | PDManer：凭证、交易工具各加 3 个字段，新增 `t_payment_stored_card` 与关系，Card 图更新；Schema、测试 `init/ddl.sql` 同步 |
| API | `ApplyCardInstrumentRequestBody`：`card` 与 `storedCard{storedCardToken, cvv}` 二选一，新增 `customerId`、`saveCard` 及组合校验；新增 `StoredCardApi`（列出、删除、按 Customer 失效）及 DTO |
| Card | 凭证与交易工具 DAL 扩展；已存卡 DO/Mapper/Repository、`StoredCardStatus`；准备流程增加已存卡分支（核对归属与状态、有效期限制工具期限、取回 DEK 加密 CVV）；重放核对 Customer、是否保存与卡来源；`CardService.paymentSucceeded`（标记凭证已验证、按同意新建或更新已存卡，撞唯一键后加锁读取再更新）；`StoredCardService` |
| Execution | `ApplyCardInstrumentRequest` 与上下文传入新字段；`PayActionActor.doInTransaction` 在支付成功事务内调用 `CardService.paymentSucceeded` |
| Web | `ApplyCardInstrumentConverter`；`StoredCardController`、`StoredCardConverter` |
| Test | `StoredCardTest` 8 例；测试 Mapper 与清理加入已存卡；`AbstractPayCardTest` 支持指定 Client 建 Intent |

### 验收映射（Payment 侧）

| AC | 验证 |
|---|---|
| AC-05 存卡条件 | `test_saveCardOnSuccess`、`test_noSaveWithoutConsentOrSuccess` |
| AC-06 凭证验证 | 同上（成功验证、失败不验证、未同意也验证） |
| AC-07 同卡再存 | `test_saveSameCardUpdatesCredential` |
| AC-08 Token 按 Client | `test_tokenPerClient` |
| AC-10 已存卡支付卡值 | `test_payWithStoredCard`（渠道读到的卡值摘要与保存时一致、CVV 为本次输入） |
| AC-11 删除后不可用 | `test_storedCardRejected`（删除后申请 400）、`test_deleteAndInvalidate` |
| AC-12 按 Customer 失效 | `test_deleteAndInvalidate`（Payment 侧接口） |
| AC-14 返回字段 | `test_saveCardOnSuccess`（列表只有 Token、前 6 后 4、后四位、有效期、创建时间） |
| 重放 | `test_replayStoredCard` |

AC-01～AC-04、AC-09、AC-13、AC-15～17 属于 Commerce 或结账页，未在本部分实现。

### 开发检查

- `./mvnw -o verify`（JDK 25）：**116/116** 通过（原 108 + 新增 8）。日志在 scratchpad `verify-stored.log`。
- 测试后本机库卡相关四表与 Intent 均为 0 行，Redis 无 `card:cvv:*`。

### 偏离与说明

- 凭证表增加明文有效期两列（见技术方案 §2.4 补充）。
- 请求体组合校验失败（缺 Customer、卡与已存卡同时提供、已存卡再选保存）按项目约定返回 422；业务拒绝（已存卡不属于该 Customer、已删除）返回 400。
- 已存卡在支付成功事务内更新时不比较版本、不抛并发异常，避免存卡冲突回滚支付成功；已被删除的已存卡不会被恢复。
- 测试排查时临时关闭测试配置的日志脱敏定位异常，已还原。

## 阶段 4 证据：C1 Commerce Customer（2026-10-15）

### SQL 执行记录

| 时间 | 目标 | 文件 | 结果 |
|---|---|---|---|
| 2026-10-15 | 本机 `mor-local-mysql-1` 库 `mor_commerce`，root 账号 | [db/commerce/ddl.sql](./db/commerce/ddl.sql) | `t_commerce_order` 增加可空列 `customer_id`；新建 `t_customer`（10 列、`uk_customer_no`、`uk_customer_active_reference`）；库内已有的 1 条他人订单未改动；未触碰 mor-test |

### 代码与工件

| 范围 | 改动 |
|---|---|
| 模型/SQL | Commerce PDManer 新增 `t_customer`、订单 `customer_id` 字段与关系、Customer 图；Schema、测试 `init/ddl.sql` 同步 |
| Client API 契约 | `ClientCustomerApi`（6 个路由）、请求/响应 DTO；`CreateCheckoutRequestBody.consumerReference`；`ClientApiConstraints.CONSUMER_REFERENCE`；错误码 `CUSTOMER_NOT_FOUND`、`STORED_CARD_NOT_FOUND`（404）、`INVALID_CONSUMER_REFERENCE`（422）；OpenAPI `client-v1.yaml` 同步 |
| Web | `Customer` DO/Mapper/Repository、`CustomerStatus`；`CustomerService`（查或建、删除并登记任务）；五个 Processor；`ClientCustomerController`；`ConsumerReferencePolicy`；`PaymentStoredCardClient`（Payment `StoredCardApi`）；`StoredCardInvalidationHandler`；建单关联 Customer；订单 DO/Mapper 增加 `customer_id` |
| 前置修复 | 删除旧 CCT 预检链：`CctPrecheckConfiguration`、`CctPrecheckClient`、`CctPrecheckClientImpl`、`MockCheckoutEligibilityApi`、`CctCheckoutMocker`、`CctPrecheckResponseBuilder` 及 `MockTestBase` 与 13 处测试中的 `cctCheckoutMocker` 断言；README 同步 |
| Test | `CustomerTestMapper`、`StoredCardMocker`、`MockStoredCardApi`（Payment 路径前缀登记在 `MockPaymentIntentApi`）；`CheckoutDataMocker` 清理 Customer 与失效任务；`CustomerApiTest`（7）、`StoredCardApiTest`（3）、`CreateCheckoutCustomerTest`（2） |
| 本地依赖 | 本机 `mvn install` Payment `paytech-payment-api`（含 `StoredCardApi`）到 `~/.m3` |

### 验收映射

| AC | 验证 |
|---|---|
| AC-01 Customer 创建与复用、跨 Client | `CustomerApiTest.test_createAndReuse` |
| AC-02 建单关联 | `CreateCheckoutCustomerTest.test_attachCustomer` |
| AC-03 个人原值拒绝 | `CustomerApiTest.test_personalReferenceRejected`（4 例）、`CreateCheckoutCustomerTest.test_personalReferenceRejected` |
| AC-04 跨 Client 不可见 | `CustomerApiTest.test_get`、`StoredCardApiTest.test_deletedOrForeignCustomer` |
| AC-11 删除已存卡 | `StoredCardApiTest.test_deleteCard`（Payment 成功 204、不存在 404、不可用 503） |
| AC-12 删除 Customer | `CustomerApiTest.test_deleteAndRecreate`（状态、删除时间、任务调用 Payment 失效、重复删除不再调用、引用重建为新 Customer） |
| AC-14 返回字段 | `StoredCardApiTest.test_list`（只含 Token、掩码、有效期月年、保存时间） |

### 开发检查

- Commerce `./mvnw -o test`（JDK 25）：**743/743** 通过（含新增 12）。日志在 scratchpad `commerce-c1.log`。未运行 `verify`：既有 Hosted Checkout bundle 90% 覆盖率门禁在本需求之前已不通过（见实现台账）。
- 测试数据由 `CheckoutDataMocker` 按本次运行的两个 Client 清理。

### 偏离与说明

- 订单查询响应暂不增加 Customer 字段（见技术方案 §2.2 补充）。
- Customer 对外用 `customerNo`（`cus_`），不用内部主键。
- 删除 Customer 的失效任务成功后由公共组件移除，测试以 Payment 调用次数验证只执行一次。

## 阶段 4 证据：C2 Commerce 结账存卡与接通 pay（2026-10-15）

### SQL 执行记录

| 时间 | 目标 | 文件 | 结果 |
|---|---|---|---|
| 2026-10-15 | 本机 `mor_commerce`，root 账号 | [db/commerce/ddl-stored-card-setting.sql](./db/commerce/ddl-stored-card-setting.sql) | 新建空表 `t_client_stored_card_setting`（5 列）；未触碰 mor-test |

### 代码与工件

| 范围 | 改动 |
|---|---|
| 模型/SQL | PDManer 新增 `t_client_stored_card_setting` 并加入 Customer 图；Schema、测试 DDL 同步 |
| Customer API 契约 | `GetCheckoutResponseBody` 增加 `cardSaving`、`storedCards`；`SubmitPaymentRequestBody` 改为 `card` 与 `storedCard{storedCardToken,cvv}` 二选一并增加 `saveCard`；错误码 `STORED_CARD_NOT_AVAILABLE`（422） |
| Web | 开关 DAL；`CardSavingEligibility`；结账查询列出已存卡（Payment 不可用降级为空）；提交支付构造 `CardChoice` 与 `Payer`；`PaymentCheckoutClient.applyCardInstrument` 支持已存卡与保存；新增 `CheckoutCardPaymentPortImpl` 经 Payment pay 提交，删除配置类中的固定 Mock |
| Payment API | `ApplyCardInstrumentRequestBody` 组合校验方法加 `@JsonIgnore`（P1 补丁），本机重新 `install` |
| Test | `CheckoutCardMocker` 由 Java 层 `@Primary` 端口替身改为 `MockPaymentIntentApi.pay` 背后的 HTTP Mock，`CheckoutIntentCreationTest` 相应调整（事务外 Payment 调用由 2 次变为含 pay 的 3 次）；`CustomerTestMapper` 增加 Customer、订单关联与开关夹具，清理开关；新增 `StoredCardCheckoutTest`（6） |

### 验收映射

| AC | 验证 |
|---|---|
| AC-05 存卡前提（Commerce 侧） | `test_submitSaveCard`（申请工具带 Customer 与 `saveCard`）；生成已存卡见 P1 `StoredCardTest` |
| AC-09 已存卡支付（Commerce 侧） | `test_submitStoredCard`（只带已存卡与 CVV）、`test_getOffersStoredCards`；渠道卡值见 P1 `test_payWithStoredCard` |
| AC-13 开通开关 | `test_getWithoutEligibility`、`test_storedCardNotAvailable` |
| G-01 接通 pay | `CheckoutIntentCreationTest`（经 HTTP pay，回跳地址、自动捕获、事务外）、`test_guestPayerReference` |
| AC-15～17 视觉/交互 | 不适用：checkout-web 无代码（G-02），移至结账页建设需求 |

### 开发检查

- Commerce `./mvnw -o test`：**749/749**（含新增 6）；日志 scratchpad `commerce-c2.log`。
- Payment `./mvnw -o verify`：**116/116**；日志 scratchpad `verify-c2.log`。

### 已知遗留

- Commerce 结账“查询支付提交”仍为占位（Payment 无按来源提交查询接口）。
- 支付成功后 Commerce 订单状态推进依赖 Payment 结果回传，不在本需求范围。
- 订单查询响应未加 Customer 字段。

## 阶段 5 证据（2026-10-15）

| 检查 | 结果 |
|---|---|
| Payment `./mvnw -o verify` | 116/116 |
| Commerce `./mvnw -o test` | 749/749（`verify` 的既有 90% 覆盖率门禁不在本需求范围） |
| `npm --prefix requirements-site test` | 137 项：127 通过、10 失败。9 项与需求开始前基线一致；新增 1 项（“the current ledger owns the Platform snapshot…”）断言实现台账 API Gateway 行，该行为其他会话（Client 入驻）未提交的修改，与本需求无关 |
| 跨服务整链联调 | 未做：建单带 consumerReference → 勾选保存支付 → Payment 成功生成已存卡 → 第二单用已存卡支付，需要本机同时运行 Commerce、Payment、Risk、Platform（渠道执行）与 Vault；各段已分别由两边 auto 测试覆盖（Commerce 经 HTTP Mock 的 Payment，Payment 经 HTTP Mock 的 Platform/Vault/Risk） |
| 结账页视觉/交互（AC-15～17） | 不适用：checkout-web 无代码（G-02） |

### 部署前清单（各环境）

1. Payment 库执行 [db/payment/ddl.sql](./db/payment/ddl.sql)；Commerce 库依次执行 [db/commerce/ddl.sql](./db/commerce/ddl.sql)、[db/commerce/ddl-stored-card-setting.sql](./db/commerce/ddl-stored-card-setting.sql)。
2. 按需为 Client 插入 `t_client_stored_card_setting`（`stored_card_enabled=1`），需各环境另行授权。
3. Client API 新增路由的 Gateway 注册随 Commerce Client API 整体开放另行处理（当前 Commerce Client API 均未注册）。
