# 卡 BIN-B 报告：platform-support（CSV、国家区域、块 132）与 risk-decision

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-bin-plat`（detached，基于 4163f9f9）。卡片写的是 `Workspace/Code-MoR-bin-plat`，实际 worktree 在主工作区下一层，路径以 `git worktree list` 为准。
- 未提交、未推送、未建分支；没有改主工作区代码（只写了本报告）；没有调用 Superpowers。

## 1. 已读与开工自查

- 已读：`AGENTS.md`、`00-common.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`（含第 11 节）、`UNIT_TEST_GUIDE.md`、`examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`（第 4—8 节）、本需求 `technique.md`（第 1—3 节全文）、`db/platform-support/*`、`db/risk-decision/*`、任务卡 B 与 D。
- 所属入口与 Context：
  - Platform `GET /platform/v1/country-regions`：只读简单查询，Controller 直接委托 `CountryRegionQueryService`，不建 Processor、不开事务。
  - Platform CSV 识别：存储适配器（完成上传时识别类型）与 `FormatFileScanner`（扫描补偿 Handler 内的检查器）；不新增写接口。
  - Risk：既有 `POST /risk-decisions/v1/evaluations` → `PaymentAttemptEvaluator`；冻结输入 `PaymentRiskInput`，保存上下文 `PaymentRiskAssessmentContext`。
- 本地原子写入：Risk 沿用 `PaymentRiskAssessmentService.save` 的单一事务（评估、交易评估快照含新 3 列、轨迹、累计事件）。Platform 本卡没有新写入。
- 事务外步骤：Risk 读取生效发布与执行规则，都在事务外，和改造前一样；Platform 读对象存储在本地事务外，也和改造前一样。

## 2. 改动清单

**risk-decision**
- API：`PaymentAttemptRiskInput` 增加 `issuerCountryCode`、`cardBrand`、`fundingType`，`inputs()` 只在有值时写入。另保留一个 17 参数构造器，让 Payment 不改也能编译（见第 6 节裁决 3）。
- 核心：
  - `PaymentAttemptEvaluator` 的合同新增 3 个可选字段：`[A-Z]{2}`、`[A-Z0-9_]{1,32}`、`[A-Z0-9_]{1,16}`；同时更新快照还原、`inputs`、`toInput`。
  - `PaymentRiskInput` 增加 3 个组件；`contractValues` 新增可选的 `issuer_country_code`、`card_brand`、`funding_type`。
  - `PaymentRiskFacts`：3 列写入快照。稳定输入与受限输入摘要只在字段有值时纳入，所以旧请求的摘要不变。
  - `DecisionSourceContracts`：PAYMENT_ATTEMPT v1 新增 3 个 `optional`。
  - `DecisionPointAuthoringDescriptor`：支付运行期点版本号改为 2（新增常量 `PAYMENT_RUNTIME_POINT_VERSION_NO`）。
  - `TransactionRiskAssessment` 增加 3 个字段，`TransactionRiskAssessmentMapper.xml` 同步。
- 数据库：
  - `risk-decision.pdma.json` 增加 3 个字段；生成器已过期（见第 7 节），所以直接改 JSON，同时同步 `scripts/pdmaner/risk/manifests/03-evaluation.mjs`。
  - `risk-decision-schema.sql` 由 `generate-risk-compliance-schema.mjs` 重新生成，与草案 DDL 逐字一致。
  - `risk-decision-initial-data.sql` 追加「2026-10-22 BIN 库」块：点版本 220003 和变量 93014—016。
  - 新增增量文件 `docs/database/sql/2026-10-22-payment-card-attribute-inputs.sql`：DDL、DML、预检与回退。
- 测试：
  - 新增 `PaymentRiskCardAttributeApiAutoTest`（6 例）。
  - `PaymentRiskAssessmentAvailabilityApiAutoTest` 新增 6 组格式错误参数。
  - `EvaluationQueryApiAutoTest`：输入字段数 17 → 20。
  - `PaymentRiskRequestBuilder`：模板带上 AE、VISA、CREDIT，新增 `withoutCardAttributes()`。
  - `VariableAuthoringContextResponseChecker`：合同字段补齐。
  - `RiskDecisionDataCleaner`：目录行 +220003，变量 13 → 16。
  - `RiskDecisionDatabasePrecondition` 新增 `verifyPaymentCardAttributes`。
  - `PaymentRiskTestMapper`：保留变量区间改到 93016；点超时改为按 220003 读写。
  - `init/ddl.sql`：3 列加种子。

**platform-support**
- CSV 识别：
  - `StoredContentType` 开头抽样改为 8 KiB：没有 PDF、PNG、JPEG 文件头，非空，不含 NUL，且是合法 UTF-8（容忍末尾被截断的多字节字符）时识别为 `text/csv`。新增常量 `CSV`。
  - 新增 `Utf8TextInspector`：按块做严格 UTF-8 加 NUL 检查，内存有界。
  - `FormatFileScanner`：识别为 CSV 时，在计算摘要的同一遍读取中检查全文；不合法返回 `FORMAT_INVALID`。
  - `CompletedUploadVerifier` 不改，它比较的「检测值 = 声明值」已覆盖 CSV（technique 3.5.3）。
  - 没有碰 `FileAddress`、`FileErrorCode`、下载签发等文件第二段的文件。
- 国家区域：
  - API：`CountryRegionQueryApi`（`GET /platform/v1/country-regions`）、`CountryRegionListResponseBody`、`CountryRegionResponseBody`。
  - 核心：`CountryRegion`（DataObject）、`CountryRegionMapper` 加 XML、`CountryRegionRepository(+Impl)`、`CountryRegionQueryService(+Impl)`。
  - 应用：`CountryRegionController`。
- 数据库：
  - `platform-support.pdma.json` 新增 `t_country_region`，并加入 reference_data 图组。生成器已过期，直接改 JSON，同步 `manifests/10-reference-data.mjs`、`manifest.mjs`（图组数 1 → 2）和 `platform-support-test-oracle/schema.oracle.mjs`（总表数 63 → 64）。
  - `platform-support-schema.sql` 新增表，表头 53 → 54。
  - 新增 `changes/2026-10-22-country-region.sql`。
  - `platform-support-initial-data.sql` 末尾追加 3 个自包含块：国家区域种子 249 行；`CARD_BIN_CSV` 扫描策略（900000000000132901/132902）；块 132 共 111 行，默认停用，激活和回滚语句写在注释里。
- 注册与其他：
  - `console-resource-capabilities.json` 新增 `OPERATIONS_CARD_BIN_LIBRARY`，按钮为 `OPERATIONS_CARD_BIN_LIBRARY_MANAGE`。
  - 场景文档 `PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md` 第 10 节补 `t_country_region` 一行；否则场景脚本「覆盖每张批准表」会失败。
- 测试：
  - 新增 `CountryRegionQueryApiTest`。
  - `FileScanHandlerTest` 新增 `test_normalFlow_csv` 和 `test_businessRuleViolation_csvNotText`（第 8 KiB 之后才出现 NUL、二进制伪装成 CSV）。
  - `InternalFileApiTest` 新增 `test_normalFlow_csv` 和 `test_businessRuleViolation_binaryDisguisedAsCsv`。
  - `Minio/S3FileStorageTest` 识别用例补 csv、text，unknown 改用真正的二进制内容。
  - `OnboardingFileBuilder` 新增 `CSV`、`BINARY`、`csvWithLateNul()`；测试扫描策略允许 `text/csv`。
  - `ReferenceDataTestMapper` 新增两个查询；`init/ddl.sql` 加入表和 249 行常驻种子。

**arch-rules**：`table-ownership.yaml` 由生成器重新生成，mor_platform +1；`TableOwnershipAutoTest` 表数加 1，并断言 `t_country_region` 归 Platform。

## 3. 对应 AC

- AC-08（Risk 部分）：带卡属性和不带卡属性都合法；格式错误返回 422；可按发卡国、卡组织、借贷记类型写规则并命中；重放返回原评估。
- AC-01 前置（Platform 部分）：CSV 可走内部文件接口上传、完成并通过扫描，二进制伪装被拒；国家区域查询接口与种子可用；`CARD_BIN_CSV` 策略 DML 已就绪。
- AC-12 前置：块 132 已编入，能力清单已同步。

## 4. 执行的 SQL 与备份（只在本机 127.0.0.1:13306）

| 库 | 备份 | 执行 |
|---|---|---|
| `mor_risk_decision`（risk 测试配置的库） | `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/936397ef-ca1e-4532-b001-b27dfa1be0a4/scratchpad/backup/mor_risk_decision-20261022-161155.sql` | `paytech/risk-decision/docs/database/sql/2026-10-22-payment-card-attribute-inputs.sql`：ALTER 加 3 列、插入点版本 220003、插入变量 93014—016。第一次测试时，旧的 `PaymentRiskTestMapper` 区间把 3 个变量当测试数据删掉了；修正区间后重新插入了这 3 行变量（只有 INSERT）。 |
| `mor_platform`（platform 测试配置的库） | `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/936397ef-ca1e-4532-b001-b27dfa1be0a4/scratchpad/backup/mor_platform-20261022-163526.sql` | `changes/2026-10-22-country-region.sql` 加 initial-data 中的「Country region seed」块（249 行）。扫描策略和块 132 没有在本机执行，因为权限、路由等表是测试独占表，要求每例开始时为空。 |

执行后用 `SHOW CREATE TABLE` 和 `information_schema` 核对，列定义和注释与 Schema、PDManer 一致。

## 5. 测试命令与结果

均设置 `JAVA_HOME=jdk-25`，使用 `./mvnw -o -Dmaven.repo.local=~/.m3/repository`；platform 另加 `-Dpaytech.local-environment.dir=<主工作区>/local-environment`，因为 worktree 里没有 `.env.local`。

- risk-decision 整模块：`-pl risk-decision/paytech-risk-decision-test -am test -Dtest='com.paytech.risk.**.*Test'`，**1738 例全部通过**。其中新增类 6 例，Availability 28 例（含 6 例新增）。
- platform 定向：`-Dtest=CountryRegionQueryApiTest,FileScanHandlerTest,InternalFileApiTest,MinioFileStorageTest,S3FileStorageTest,CompleteOnboardingUploadTest,CurrencySpecQueryApiTest`，**68 例全部通过**。另跑 `ArchitectureAutoTest` 1 例，通过。
- platform 整模块：**两次都没有得到有效结果**。运行期间另有会话（`Code-MoR-fs2-int`，第二次还有主工作区）在同一个 `mor_platform` 上并行跑 platform 测试，首个失败都是「测试独占表存在数据 [t_permission, t_jwt_signing_key]」，属于共享库冲突，不是本卡回归。我的并行运行也可能干扰了对方那两次运行。
- Gateway 编译：
  - 用一个临时程序（scratchpad，不入库）从 initial-data 解析块 132 的 9 条路由、34 个 Header、8 个 Query、54 条 Guard，交给真实的 `GatewaySnapshotCompiler.compile`，**编译通过**。
  - api-gateway 的 `GatewaySnapshotCompilerAutoTest`、`GatewaySnapshotPolicyAutoTest`、`GatewaySnapshotCodeParserAutoTest` 共 **114 例通过**。
- arch-rules：68 例通过。
- 全 reactor `test-compile`：通过，payment 未改动也能编译。
- Node 脚本（与改动前基线对比）：
  - `platform-support-model` 22 过 / 9 败，失败集合与基线相同；数字差 1 是因为我把一项原本通过的检查改到了新表数后，它和其他项一样受过期生成器影响。
  - `platform-support-schema` 5 过 / 4 败；`platform-support-scenario-doc` 11 过 / 1 败；`risk-service-model` 19 过 / 6 败；`risk-decision-engine-contract` 86 过 / 10 败。以上与基线相同，都是既有失败。
  - `risk-decision-initial-data.test.mjs` 缺 fixture 文件，基线就无法运行。
  - `risk-service-schema`、`risk-variable-model`、`platform-file-upload-model` 全部通过。

## 6. 偏离与裁决

1. **支付点版本必须新增 220003。** technique 3.5.5 说「220002 及其内容哈希不变」，与代码不符：点版本内容摘要包含 `DecisionSourceContracts` 推导出的输入字段，`CreateExecutionReleaseProcessor.requirePointVersion` 会按工程合同重算并比对。只追加 3 个可选字段，220002 的重算值就会从 265ad… 变成 230eb…，新发布会被拒绝。
   - 裁决：合同仍是 v1 原地扩展，Payment 和传输格式不变。新增冻结点版本 220003（version_no 2，内容哈希 `b4336cc4…`），运行期点版本号改为 2。
   - 影响：已创建的发布继续引用 220002，运行期不重算点哈希，生效发布不受影响；新发布绑定 220003，不需要运营迁移。
   - 已通知 team-lead；没有选择改写 220002 的冻结哈希，那样会让已有的 DRAFT 发布在校验时失败。
   - **主会话已批准（方案 B）。**
   - **新规则什么时候能用这三个字段：** 只有运营基于 220003 发布新的执行发布之后，新规则才能使用发卡国、卡组织、借贷记类型。在此之前，现有 ACTIVE 发布（220002）照常运行，只是不读这三个字段；这期间 Payment 传入的卡属性仍会冻结在评估快照 3 列中。
   - 220003 的增量 DML 已写入需求目录 `db/risk-decision/dml.sql`，可以在 mor-test 执行，**尚未在 mor-test 执行**；和 `paytech/risk-decision/docs/database/sql/2026-10-22-payment-card-attribute-inputs.sql` 里的 DML 一致。
2. **输入键名是 `issuerCountryCode`，不是 `issuerCountry`（主会话已批准）。** `EvaluationInputContract.requireRuleFieldsCovered` 要求规则字段 `issuer_country_code` 的驼峰形式在评估合同里出现，Java 组件名随之取 `issuerCountryCode`，与 `consumerCountryCode` 一致。**卡 D 请用这个键名。**
3. **`PaymentAttemptRiskInput` 保留 17 参数构造器（主会话已批准，卡 D 切换后删除）**，三个卡属性填 null。这样 B 合并后、D 合并前 payment 也能编译；D 改用 20 参数构造器后可以删除它。
4. **`fundingType` 上限改为 16。** technique 写的是 `{1,32}`，但列是 `VARCHAR(16)`，所以格式收紧到 `[A-Z0-9_]{1,16}`，避免超长值入库失败。
5. **稳定输入和受限输入摘要只在三个字段有值时纳入。** 这样旧请求的摘要不变。代价是：同一支付尝试先以无卡属性的请求评估，再以有卡属性的请求重放，会返回 409（已有测试覆盖）；只有升级窗口内正好跨版本重放时才会出现。
6. **CSV 识别范围。** 任何非空、无已知文件头、不含 NUL 的合法 UTF-8 文本都识别为 `text/csv`，`CompletedUploadVerifier` 不改。这对其他策略没有放宽：它们的允许名单里没有 `text/csv`，原来报 `application/octet-stream` 的纯文本现在报 `text/csv`，结果仍是拒绝。
7. **国家区域种子在测试 DDL 中作为常驻参考数据**（与 USD 币种同一做法），用例不增删，所以 `CountryRegionQueryApiTest` 断言的是种子本身。
8. **增量文件位置。** Risk 没有 `changes/` 目录，按现有的 `sql/2026-09-11-…` 方式放在 `sql/` 根下。

9. **国家区域接口与 BIN-A 已定义的合同完全一致：**
   - `com.paytech.platform.support.api.referencedata.CountryRegionQueryApi`：`@HttpExchange(url="/platform/v1/country-regions", accept=JSON)`，方法 `@GetExchange CountryRegionListResponseBody listCountryRegions()`。
   - `response/CountryRegionListResponseBody { List<CountryRegionResponseBody> items }`，按国家代码升序。
   - `response/CountryRegionResponseBody { String countryCode; String regionCode }`。
   - 合并时 API 文件应当没有冲突，或者内容相同。

## 7. 遗留项

- **生成器已过期。** `generate-risk-decision-model.mjs --check` 和 `generate-platform-support-model.mjs --check` 在基线上就失败，模型已经手工演进过。本卡只手工改 JSON、同步 manifest，没有重新生成；重新生成会回退别人的改动。
- **需求目录草案：**
  - `db/risk-decision/dml.sql` 和 technique 3.5.5 由主会话最终修订。本卡曾在 dml.sql 中补过 220003，以主会话版本为准。
  - technique 3.5.5 中「点版本 / 哈希不变」和 `issuerCountry` 键名需要修订。
  - 区域分布实际是 GCC 6、MEA 67、EUROPE 56、NORTH_AMERICA 6、LATAM 51、APAC 63。
- **合并冲突提示：** `table-ownership.yaml` 卡 A 也会改（mor_card +4），合并后重新运行 `generate-table-ownership.mjs` 即可；`TableOwnershipAutoTest` 的计数也要相应累加。
- **不在本卡：**
  - ops-web 的 `pageCapabilities.ts` 等由卡 C 负责。
  - ops-bff 侧块 132 的路由与入口一致性静态测试（类似 `DataQueryRegistrationAutoTest`），建议卡 C 补上。
  - `PLATFORM_SUPPORT_DATABASE.md` 和 `PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md` 没有改。
- **待完整重跑：** platform 整模块回归由主会话合并后在独占时间窗内执行，本卡不再运行。
- **发布注意：** mor-test 执行 Risk 增量前，先按增量文件头部预检 220003 和 version_no 2 未被占用；`CARD_BIN_CSV` 写入路由在发布时到「存储管理」页登记。

## 8. WRITE_API_EXAMPLE 第 11 节核对

- **API 与 Body：**
  - 国家区域响应 Body 是普通 class，字段有中文注释。
  - Risk 输入是既有 record；它是 Payment 侧的输入工厂，不是 Body。字段校验在 `EvaluationInputContract` 里。
- **Controller / Converter：**
  - `CountryRegionController` 只做协议适配，加一个转换方法。
  - 本卡没有新增写接口，「写操作 Processor」不适用。
- **Processor 查询本模块 Repository：** 没有新增 Processor，不适用。国家区域是简单查询，由 Controller 委托 Service。
- **Context：** Risk 沿用 `PaymentRiskAssessmentContext`，只多了 3 个值，没有新增同义对象。
- **Service：** `CountryRegionQueryServiceImpl` 只调用 Repository，不调用其他 Service 或外部依赖。
- **事务：** 没有新增事务。Risk 沿用单一本地事务，事务内没有外部调用。
- **Repository：** 一张表对应一个 Repository，SQL 写在 XML 里。没有更新语句，行数与对象回写不适用。
- **锁：** 没有新增锁；Risk 原有的并发保护不变。
- **原请求比较：** Risk 重放仍比较风控 Action 与稳定输入摘要。新字段有值时才纳入，已有测试覆盖同内容重放、改卡属性返回 409、去掉卡属性返回 409。
- **Gateway 幂等：** 没有引入 `Idempotency-Key` 或命令幂等组件。
- **View 与 DataObject 边界：** 没有多表组合，不需要 View；DataObject 不跨出 Controller 或模块边界。
- **公共包与配置：** 没有新配置，没有生产 Mock，没有无消费者的字段；`CSV` 常量与 `Utf8TextInspector` 都有当前调用方。
- **验证：** 按测试规范从真实入口验证，结果见第 5 节；platform 整模块回归还没有有效结果。
