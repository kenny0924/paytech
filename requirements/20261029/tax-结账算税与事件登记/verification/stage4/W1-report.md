# W1（tax）阶段 4 研发报告

- worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-tax`（基于 `e3e0c12f`），未提交、未推送、未部署、未碰 mor-test。
- 已读规范：AGENTS.md、DEVELOPMENT_CONVENTIONS、JAVA_PROJECT_STRUCTURE、WRITE_API_EXAMPLE、UNIT_TEST_GUIDE + 示例、DATABASE_DESIGN_GUIDE、COMPENSATION_EVENT_GUIDE；需求、technique 第 7—17 节、design-discuss/tax 的 database.md 与 regional-policies.md。
- 归属：写接口 `CalculateTaxProcessor`（本地原子写：计算头 + 行 + 组件 + 证据）；MQ 入口 `OrderPaymentSucceededTaxHandler` → `RegisterTaxTransactionProcessor`（事务外确定期间与汇率，本地原子写：交易 + 底稿 + 开票补偿任务）；补偿 `TaxDocumentIssueHandler`（短事务分号 → 事务外渲染与上传 → 短事务 ISSUED）。

## 1. 改动文件清单

**paytech/tax（全部在范围内）**

| 模块 | 内容 |
|---|---|
| 父 pom | 描述更新；enforcer 按 billing 先例放开 AMQP、compensation-event、XXL-Job |
| `paytech-tax-api` | pom 加 spring-web、jakarta.validation、paytech-common-core；`TaxCalculationApi`、`TaxOrderQueryApi`；`request/` 3 个 Body；`response/` 12 个 Body；`enums/` 13 个（10.4 的 10 个 + `TaxPriceMode`、`TaxEvidenceType`、`TaxErrorCode`） |
| `paytech-tax-core` | pom（tax-api、platform-support-api、mybatis-support、snowflake-id、json-canonicalization、compensation-event、messaging-api 等）；`core/enums` 14 个、`core/constant/TaxCodes`；13 个 DataObject、13 个 Mapper + XML、13 个 Repository + Impl；`domain/policy`（规则模型、`TaxPolicyRulesParser`、`TaxPolicyInvalidException`）、`domain/calculation`、`domain/registration`、`domain/document`（`TaxReceiptContent`、`TaxReceiptPdf`）、`domain/view/TaxOrderView`；Service：`TaxComputationService`、`TaxPolicyService`、`TaxCalculationService`、`TaxQueryService`、`TaxPeriodService`、`TaxTransactionService`、`TaxDocumentService`（均 interface + Impl）；`service/processor/CalculateTaxProcessor`（+ `CalculationDraft`）、`RegisterTaxTransactionProcessor`；`ext/mq/OrderPaymentSucceededEventReader`、`OrderPaymentSucceededTaxHandler`；`service/handler/TaxDocumentIssueHandler`；`integration/file`（`PlatformTaxDocumentFileClient`）、`integration/fx`（`UnavailableTaxFxRateClient`） |
| `paytech-tax-app` | pom（mybatis、messaging、amqp、compensation-event、http-client-support、internal-auth、restclient）；`TaxPersistenceConfiguration`、`TaxPlatformClientConfiguration`、`InternalTokenClientOptions`、`TaxApiErrorConfiguration`；`TaxCalculationController`、`TaxOrderQueryController`、`TaxCalculationConverter`、`TaxOrderConverter`、`TaxAmounts`；`application.yml`（RabbitMQ、Platform 基址、MyBatis、compensation、messaging consumer、internal-auth） |
| `paytech-tax-test` | pom 加 messaging test-jar；`application-mock.yml`、`init/ddl.sql`；`MockTestBase`、`TaxDataCleaner` 重写；新增 `TaxTestMapper`、两个测试配置、`TaxSubjectMocker`、`PlatformFileMocker`、`MockPlatformInternalFileApi`、`MockObjectStorageController`、`AcquiringEventPublisher`、`CalculateTaxRequestBuilder`、`OrderPaymentSucceededEnvelopeBuilder`、`TaxTestConstants`；测试类见第 2 节；`TaxModuleBoundaryTest` 改为新依赖清单 |
| `docs/database` | `tax.pdma.json`（16 表，直接维护）；`sql/tax-schema.sql`（与模型 `renderSchema` 逐字节一致）；`sql/tax-initial-data.sql`；`sql/changes/2026-10-29-tax-rebuild.sql`、`-rollback.sql` |
| 其他 | `compensation-event-catalog.yaml`（`TAX_DOCUMENT_ISSUE`）；`README.md` |

**共享文件（任务卡授权）**

- `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml`：只手工改 `tax:` 段（24 → 16 张）。
- `paytech/foundation/paytech-messaging/catalog/events.yaml`：`OrderPaymentSucceeded.consumers` 改为 `[billing, tax]`，注释说明可空新增字段。
- `scripts/pdmaner/`（任务卡"移出 Finance 生成器"）：`finance/service-split.mjs`、`finance/generate-finance-model.mjs`、`finance-model.test.mjs`、`pdmaner-canonical-paths.test.mjs`。

**交接文件**：`verification/stage4/tax-api-ready.md`（顶部已追加两条语义澄清，签名与字段未变）。

## 2. 构建与测试

```text
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./mvnw -o -Dmaven.repo.local=$HOME/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ \
  -pl tax/paytech-tax-test -am test -Dtest='com/paytech/tax/**/*' -Dsurefire.failIfNoSpecifiedTests=false \
  -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment
```

结果：**58 个，全部通过**（worktree 没有 `.env.local`，需用 `-Dpaytech.local-environment.dir` 指向主工作树）。测试后 `mor_tax` 只剩 13 行初始化数据。

| 测试类（入口） | 数 | 覆盖 |
|---|---|---|
| `CalculateTaxTest`（POST /calculations） | 14 | AC-01 SA 100→15/115；AC-02 未税 AE/EG、含税 SA 86.96/13.04、AE 95.24/4.76、EG 87.72/12.28；AC-06 三行含折扣尾差（行尾差 0.0005 / 0.0005 / −0.001）；PROVISIONAL 用声明国家、FINAL 用账单国家；AC-04 NOT_MANAGED 自报税额；NO_COLLECTION_REQUIRED；同身份同内容返回原计算、不同内容 409；参数 400 / 422 |
| `CalculateTaxBlockedTest` | 6 | AC-05：供应地无准入、准入 BLOCKED、未知税类、无规则、缺账单国家证据、地区包规则重叠（POLICY_INVALID）；BLOCKED 不以零税成功 |
| `TaxQueryTest`（GET） | 4 | 计算与订单 404；按 ID 读取；订单两次报价按版本排序并带注册与地区包 |
| `OrderPaymentSucceededTaxTest`（真实 RabbitMQ） | 7 | AC-07 交易 + OUTPUT 底稿 + 期间（SA 当地日期 10-30、2026 Q4、截止 2027-01-31）；重复投递只一条；AC-08 税额不一致进 DLQ；同订单不同内容进 DLQ；AC-12 存量订单跳过；AC-04 NOT_MANAGED 跳过；跨币种缺汇率 PENDING(FX_RATE_MISSING) |
| `TaxDocumentIssueHandlerTest`（补偿 Handler） | 4 | AC-11 分号、PRIVATE 上传 PDF、地址与摘要落库、票面无消费者字段、金额一致；两笔不撞号；上传失败重试不换号；非法事件与缺来源为终态失败 |
| `TaxPolicyRulesParserTest`（纯 Java） | 6 | Schema、EQ/IN、互斥、税率规则、截止日规则 |
| `TaxApplicationTest`、`TaxModuleBoundaryTest`、`ArchitectureAutoTest` | 17 | 启动骨架、依赖清单、R1–R12 |

其他验证：

- 生成的税票 PDF 用 macOS QuickLook 成功渲染，交叉引用表偏移逐项核对正确。
- `node --test scripts/pdmaner/finance-model.test.mjs scripts/pdmaner/pdmaner-canonical-paths.test.mjs`：tax 相关断言通过；`finance-model` 中 payout 模型漂移两条失败、`oe-client-model` 中 client-master 等失败是已有问题（与 tax 无关，记忆中 PDManer 漂移测试为已知噪声）。另用 `renderSchema` 单独核对 `tax-schema.sql` 与 `tax.pdma.json` 逐字节一致。
- `-pl tools/arch-rules test`：70 个中 1 个失败，见第 5 节第 1 条。
- 没有跑全 reactor `test-compile`：W2/W3/W4 正在同一 worktree 改代码，结果不能归因；tax 链路（api → core → app → test）与 arch-rules 已编译通过。

**写接口示例第 11 节核对（摘要）**：API 与 Body 为 class + Validator；Controller/Converter 只适配；Processor 查本模块 Repository、不调 Mapper；Context（`CalculateTaxContext`、`RegisterTaxTransactionContext`）只持本模块 DataObject；Service 不调其他 Service / 外部依赖，事务用 `TransactionTemplate`，事务内无外部调用（文件上传、汇率端口都在事务外）；Repository 一表一个、条件更新检查行数并回写对象；业务防重为计算身份 + 内容比较、源事实身份 + 载荷 Hash，唯一键竞争回滚后重读比较；不涉及 `Idempotency-Key`；不加 Redis 锁（号码序列用数据库行锁 + CAS）。

## 3. 本机执行的 SQL（root，库 `mor_tax`）

1. 前置核对：旧 24 张表逐表 `COUNT(*)` 全为 0。
2. `changes/2026-10-29-tax-rebuild.sql` → 16 张表；`tax-initial-data.sql` → 13 行初始化数据。
3. **回滚验证**：`changes/2026-10-29-tax-rebuild-rollback.sql` → 恢复旧 24 张空表（表名逐一核对）；随后重新执行重建与初始化 → 16 张表。
4. 结构核对：把 `tax-schema.sql` 建到临时库 `tax_schema_check`，16 张表 `SHOW CREATE TABLE` 与 `mor_tax` 完全一致，临时库已删除。
5. 只读查询 `mor_client_master.t_operating_entity`：本机没有 AE001，初始化数据用演示值 `@ae001_party_id = 900000000001`（脚本顶部变量，已注明 DEMO；mor-test 执行前须替换为真实 party_id）。

没有动 `mor_acquiring` 或其他库。

## 4. 偏离技术方案之处

1. **请求行字段 `netAmount` 改名 `lineAmount`**：含税价时该值不是净额。已写入 tax-api-ready.md。
2. **响应顶层 `components[]` 为按辖区 + 注册 + 纳税主体的汇总**（无 componentId），逐组件明细在 `lines[].components[]`；目的是 acquiring 可原样写事件 `taxComponents`。
3. **新增枚举** `TaxPriceMode`、`TaxEvidenceType`、`TaxErrorCode`（404 / 409 需要专有 Code）；订单查询多返回 `registrations[]`、`policies[]` 供页面"计算依据"。
4. **`t_tax_registration` 新增 `registered_name`**：9.3 要求票面有卖方名称，但 8.1 的 13 张表没有任何名称来源。这是方案缺口，我按最小改动补了一列（注册登记名称，公开信息），请主会话确认；不接受的话需另定名称来源（例如 commercial 卖方披露随事件到达）。
5. **地区包 `policy_json` 用了 7 个区块**：8.1 说本期只用 6 块，但 9.4 的截止日规则写在 `reporting.dueDateRule`，所以 `reporting` 也作为必填区块；`unmatchedBehavior` 放顶层（与 regional-policies 示例一致）。
6. **缺汇率时期间仍会生成**：按 9.2 第 3 步字面执行（先确定期间再取汇率），只是不建底稿、交易 PENDING。
7. **币种精度取 `java.util.Currency`**（与 Foundation `Money` 一致），没有接 Platform 参考数据客户端，避免为计算再加一个启动依赖与快照就绪门槛。
8. **税票 PDF 自行生成**：仓库与 `~/.m3` 都没有 PDF 库，`TaxReceiptPdf` 按 PDF 1.4 最小结构输出单页、标准 Helvetica。标准字体只覆盖 ASCII，**SA / EG 原型的阿拉伯语双语票面没有实现**，非 ASCII 字符输出为 "?"；法定文字按键映射为英文句子。需嵌入字体或引入 PDF 库时请主会话决定。
9. **票面快照多一个 `orderReference`（订单号）**：不是个人资料，便于对账。
10. **同一主体在同一国家同时有两个有效评估**时按 MARKET_NOT_ENABLED 阻断（方案未定义）。
11. **AMBIGUOUS_RULE 在运行时实际不可达**：规则包解析时已拒绝重叠规则（POLICY_INVALID），运行时检查保留为防御。
12. **MQ 与开票测试经真实计算接口建立冻结计算**，没有用 TestMapper 逐表插入计算行；这是另一个入口，但比手写四张表的数据更贴近 acquiring 的真实前置。
13. 应用加了 `paytech-internal-auth`（默认 OFF）并固定发送 `X-Caller-Service: tax-service`，与 card 调 Platform 文件接口的做法一致。

## 5. 未完成项与建议

1. **`TableOwnershipAutoTest` 计数需要主会话改**：期望 395、现为 388（tax 24 → 16 为 −8，W3 的 `t_order_tax_calculation` 为 +1）。测试文件在 `tools/arch-rules/src/test`，不在我的授权范围。
2. **Platform 注册（technique 12）未做**：（tax-service, PRIVATE）文件写路由与 reasonCode `TAX_RECEIPT` 必须在联调前登记，否则开票任务会因 Platform 503 持续重试。XXL 执行器注册同属阶段 6。
3. **初始化数据的 AE001 party_id** 为本机演示值，mor-test 执行前按脚本头注释替换。
4. `TaxTransactionRecorded` 按 11.4 本期不发布，代码里没有发布者。
5. 阿拉伯语票面（第 4 节第 8 条）与卖方名称来源（第 4 节第 4 条）待确认。
6. 全 reactor `test-compile` 建议在 W2—W4 都完成后由主会话统一跑一次。

## 6. 追加变更（2026-10-29 晚，主会话批准）

- `taxSubjectPartyId` 可空 → NOT_MANAGED(TAX_RESPONSIBLE_UNRESOLVED)，新增 `TaxNotManagedReason` 与响应 `notManagedReason`；`t_tax_calculation.tax_subject_party_id` 改为可空。
- NOT_MANAGED 不再保存证据（按 technique 9.1 第 2 步，修正原实现）。
- 行 `description`（`t_tax_calculation_line.description VARCHAR(255) NULL`）；注册响应 `managedBy`（`TaxMarketRepository.findByIds`）。
- BLOCKED 路径 `lines[n]` 的 n 改为 lineNo（1 起）。
- PDManer、`tax-schema.sql`、`changes/2026-10-29-tax-rebuild.sql`、测试 `init/ddl.sql` 同步；`tax-schema.sql` 与模型 render 逐字节一致。本机 `mor_tax`（mor-local-mysql-1:13306）先 DROP 当时的 16 张空表，再执行重建脚本与初始化数据（16 表、13 行）。
- 测试改用 `package`；测试配置补 `publisher-confirm-type: correlated`（`package` 下 messaging test-jar 的 YAML 不在 classpath，测试发布端需要显式开启确认）。结果 **59 / 59 通过**（新增 `test_taxResponsibleUnresolved`，并在已有用例中断言 notManagedReason、证据为空、description、managedBy）。

## 4a. 后续变更（2026-10-29 晚，主会话第二批）

| 项 | 落点 | 验证 |
|---|---|---|
| taxSubjectPartyId 可空 → NOT_MANAGED，原因 `TAX_RESPONSIBLE_UNRESOLVED` 出现在响应 `notManagedReason`（`TaxNotManagedReason`，由 `tax_subject_party_id` 是否为空推导，不另存列） | 第 6 节已完成 | `CalculateTaxTest.test_taxResponsibleUnresolved` |
| 行 `description`（≤255） → `t_tax_calculation_line.description` → 计算与订单查询的行 | 第 6 节已完成 | `CalculateTaxTest`、`TaxQueryTest` 断言 |
| 订单查询 `registrations[].managedBy` | 第 6 节已完成 | `TaxQueryTest` 断言 |
| `lines[n]` 的 n 为 1 起的 lineNo，写入 Body JavaDoc 与 tax-api-ready.md | 第 6 节已完成 | `CalculateTaxBlockedTest` |
| `categoryCode` 自由文本：去 @Pattern，只校验非空与 ≤ 64；未知 → BLOCKED(UNKNOWN_TAX_CATEGORY)；只把可打印 ASCII 的值拿去查 ascii_bin 目录列，其余直接视为未知；`t_tax_calculation_line.category_code` 改为 utf8mb4 | API、`CalculateTaxProcessor.unknownCategories`、模型与 SQL | `CalculateTaxBlockedTest.test_freeTextCategoryBlocked`（"Online course (en)"、中文）与 `test_categoryTooLong`（422） |
| AE001 演示 Party 改为 880000000702（`tax-initial-data.sql`、测试 init/ddl.sql；变量保留） | SQL | 本机库核对：注册、准入、序列的主体均为 880000000702 |
| arch-rules `TableOwnershipAutoTest` 期望值 `- 8 + 1`（tax 24 → 16，acquiring `t_order_tax_calculation`），说明文字同步 | `tools/arch-rules/src/test/.../TableOwnershipAutoTest.java` | 单独运行：7 / 7 通过 |

本机 SQL：没有另写 ALTER 脚本。除本机外没有任何环境执行过本需求早先的 16 表版本（mor-test 仍是旧 24 表，阶段 6 直接用重建脚本），所以在 `mor-local-mysql-1:13306` 上核对 `mor_tax` 业务表为空后，DROP 当时的 16 张表，再执行 `changes/2026-10-29-tax-rebuild.sql` 与 `tax-initial-data.sql`（16 表、13 行）。`tax-schema.sql` 与模型 render 仍逐字节一致。

测试（`package`）：**61 / 61 通过**。

## 4b. 第三批确认（2026-10-29 晚）

- 第 5—7 项代码都在 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-tax`（未提交，所以主工作树 `Code-MoR` 仍是 `e3e0c12f`，看不到改动）。
- 第 5 项补充：`table-ownership.yaml` 头注释的计数同步——step-removed 23 → 42（tax 旧表里有 19 个表名不再使用；`t_compensation_event`、`t_tax_category`、`t_tax_registration`、`t_tax_evidence`、`t_tax_period` 5 个表名沿用），step-added 63 → 75（tax 新 11 个表名 + acquiring `t_order_tax_calculation`）；净 −7，物理表数 388，与 `TableOwnershipAutoTest` 期望值一致（单独运行 7 / 7 通过）。
- 第 6 项补充：新增 `CalculateTaxBlockedTest.test_lowercaseFreeTextCategoryBlocked`（`standard` → BLOCKED UNKNOWN_TAX_CATEGORY，`lines[1].categoryCode`）。
- 第 7 项：本机 `mor_tax`（mor-local-mysql-1:13306）注册、准入、号码序列的主体均为 880000000702。
- 测试（`package`）：**62 / 62 通过**。
