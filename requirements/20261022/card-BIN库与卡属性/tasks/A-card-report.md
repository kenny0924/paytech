# 卡 BIN-A 报告：card 服务、acquiring / counterparty 透出、commercial 规范值

- 执行：impl-bin-card（2026-10-22）；工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR/Code-MoR-bin`（detached，基于 `4163f9f9`；卡片写的 `/Users/kenny/Desktop/Workspace/Code-MoR-bin` 不存在，实际 worktree 嵌在主工作区下，已告知 team-lead）。
- 未提交、未推送、未建分支；未改主工作区代码（只写本报告）；未动 `.pen`。

## 1. 开工自查

- 已读：`AGENTS.md`、`00-common.md`、本卡、`requirement.md`、`technique.md`（§2、§3 全文）、`db/card/ddl.sql`、`db/card/dml.sql`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`（含 §11）、`UNIT_TEST_GUIDE.md`、`examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`（相关节）、`COMPENSATION_EVENT_GUIDE.md`；参照代码：reconciliation（运营头、补偿 Handler + ObjectProvider）、ledger / acquiring（Platform 服务组、内部 Token 选项）。
- 所属 Processor / Context：card-core `CreateBinLibraryVersionProcessor`、`ActivateBinLibraryVersionProcessor`（均 `AbstractLockProcessor`，锁 `card-bin-library` TRY_LOCK）；`BinLibraryActivationContext`（目标版本、原生效版本、激活记录）。导入由补偿 Handler `CardBinLibraryImportHandler` 直接编排。
- 本地原子写入：创建版本 = 插入 VALIDATING 版本 + 登记 `CARD_BIN_LIBRARY_IMPORT` 一个事务；激活 = 原 ACTIVE→RETIRED（CAS、释放占位）+ 目标→ACTIVE（CAS、占位）+ 激活记录一个事务；导入 = 错误明细 + INVALID 一个事务、每 1000 行号段 + 进度 CAS 一个短事务、最后 VALID。
- 事务外：Platform `getFile`（创建前与每次导入）、`authorizeDownload` + 对象存储流式下载（两遍）、`GET /platform/v1/country-regions`；读流在事务外，每批读入内存后再开事务。

## 2. 改动清单

**card（BIN-A 主体）**
- api：`CardApiConstants`（`OPERATIONS_CONSOLE_BFF`、`ACTOR_HEADER`）；`binlibrary/CardBinLibraryApi` 7 个接口 + request（Create / Activate）+ response（Version、ImportError、Activation、Lookup）；`enums/` 新增 `BinLibraryVersionStatus`、`BinLibraryActivationType`、`BinImportErrorCode`、`CardBrand`、`CardFundingType`、`CardProduct`；`CreateCardInstrumentResponseBody.cardAttributes`（嵌套 `CardAttributes`，对象恒在）；`StoredCardListResponseBody.StoredCard.cardBrand`。
- core：4 个 DataObject、4 个 Mapper + XML（`selectMatch` 一条 SQL 左关联、8→7→6 位、不缓存）、4 个 Repository + Impl；`domain/binlibrary/`（`BinLibraryCsv` 两遍流式读取与校验、`BinLibraryValidation`、`BinRangeRow`、`BinImportRowError`、`CardAttributes`、`CardBinMatchView`、`BinLibraryActivationView`、`BinLibraryActivationContext`、`BinLibrarySourceFile`、`BinLibraryLock`）；Service `CardBinLibraryService(+Impl)`（写）、`CardBinLibraryQueryService(+Impl)`（读）；两个 Processor + request/response；Handler `CardBinLibraryImportHandler`；integration `PlatformFileClient(+Impl)`、`PlatformCountryRegionClient(+Impl)`；`CreateCardInstrumentProcessor` 返回快照；`StoredCardServiceImpl` 实时查 `cardBrand`；pom 加 `paytech-compensation-event`、`paytech-platform-support-api`。
- app：`CardBinLibraryController`、`CardBinLibraryConverter`；`PlatformClientConfiguration`（`@ImportHttpServices` InternalFileApi + CountryRegionQueryApi、注册 FILE_NOT_FOUND / FILE_STATE_CONFLICT、默认头 `X-Caller-Service: card-service`、`fileDownloadRestClient` 不跟随重定向）；`InternalTokenClientOptions`（同 acquiring）；`CardInstrumentConverter` / `StoredCardConverter`；`application.yml`（Platform 服务组、`redis.coordination.enabled=true`、`compensation.lease-duration=20m`、loadbalancer 不重试）；pom 加 `paytech-http-client-support`。
- 数据与模型：`scripts/pdmaner/card-model-additions.mjs`（INT/TINYINT/默认值支持、4 表、"BIN 库"图）、`card-model.test.mjs`；重新生成 `card.pdma.json`；`card-schema.sql`、测试 `init/ddl.sql`（12 表）与草案 DDL 逐字一致；新建 `card-initial-data.sql`（草案种子）；`compensation-event-catalog.yaml`；`tools/arch-rules/scripts/generate-table-ownership.mjs` + 生成的 `table-ownership.yaml`（mor_card +4）+ `TableOwnershipAutoTest`（总数 +4、卡域表清单）。
- 文档：`paytech/card/README.md`（BIN 库一节、配置与部署前置）。
- 测试：见 §5。

**platform-support-api（契约，非实现）**：`referencedata/CountryRegionQueryApi`、`response/CountryRegionListResponseBody`、`CountryRegionResponseBody`（见偏离 D1）。

**acquiring**：`PaymentStoredCardClient.StoredCard` + Impl（cardBrand 可空，有值须为 `CardBrand` Code，否则协议违例）；`GetCheckoutResponseBody.StoredCardInfo.cardBrand`；`GetCheckoutConverter`；`StoredCardCheckoutTest`。

**counterparty**：`StoredCardClient.StoredCard` + Impl（同上校验）；`ListStoredCardsResponseBody.StoredCardInfo.cardBrand`；`ClientCustomerConverter`；Client API OpenAPI（`paytech/acquiring/api/openapi/client-v1.yaml` 的 `StoredCardList` 加可空 `cardBrand`）；`StoredCardApiTest`。

**commercial**：`CommercialCodes.CardBrand` 补 MADA、DISCOVER、JCB、DINERS。

## 3. 对应 AC

| AC | 实现 / 证据 |
|---|---|
| AC-01 | C4 201 VALIDATING → 异步 VALID，列表 / 详情显示行数与进度；未激活前查询仍用 v1（`test_createVersionImportsValidFile`） |
| AC-02（Card 侧） | 激活后新工具快照用新版本（`test_newVersionAppliesToNewInstruments`）；冻结由 Payment 卡负责 |
| AC-03 | 8 位优先（`test_eightDigitRangeWins`、`test_lookupBin`），15 位卡只命中 6 位 |
| AC-04 | 未识别工具照常创建、属性为空带版本号；无生效版本时版本号也为空 |
| AC-05 | 重复前缀（两行都记）、非法值、缺列、国家未登记、超长数字 → INVALID + 行号明细，不能激活；表头 / 摘要 / 扫描失败为文件级原因 |
| AC-06 | 回切后查询回到旧版本，历史两条（`test_activateAndRollback`） |
| AC-07 | 工具响应 `cardAttributes` 9 字段；已存卡列表 `cardBrand`（card、acquiring、counterparty 三处测试） |
| AC-11 | 错误摘录对 ≥9 位连续数字存 NULL；日志捕获断言不含 16 位卡号样式值（`test_errorRowsAreCapped`）；补偿 Payload 为空、失败摘要固定英文 |
| AC-12 | 本卡只提供 Card 运营接口；BFF / 页面属卡 BIN-C |

## 4. 本机 SQL（已授权）

- 库：本机 `mor_card`（127.0.0.1:13306，local-environment）。
- 备份：`/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/936397ef-ca1e-4532-b001-b27dfa1be0a4/scratchpad/backup/mor_card-20261022-160758.sql`（root mysqldump，执行前）。
- 执行：`requirements/20261022/card-BIN库与卡属性/db/card/ddl.sql`（4 张 CREATE TABLE）。种子 DML 未执行：auto 测试要求 `mor_card` 空基线，测试自行插入固件。
- 未连接 mor-test 或任何非本机环境。

## 5. 测试命令与结果

均在 `Code-MoR-bin/paytech`，`JAVA_HOME=jdk-25`，`./mvnw -o -Dmaven.repo.local=~/.m3/repository`，`-Dpaytech.local-environment.dir=<主工作区>/local-environment`。

| 命令 | 结果 |
|---|---|
| `test -pl card/paytech-card-test -am -Dtest='com.paytech.card.**'` | 66 个：64 通过，2 失败（见下，为基线既有失败） |
| 其中新增：`BinLibraryVersionHttpTest` 9、`BinLibraryActivationHttpTest` 4、`CardBinLibraryImportHandlerTest` 8、`CardInstrumentAttributesHttpTest` 5；修改 `StoredCardHttpTest` 新增 1 | 新增 / 修改用例全部通过；`ArchitectureAutoTest`（R1–R15）通过 |
| `test -pl counterparty/paytech-counterparty-test,acquiring/paytech-acquiring-test -am -Dtest='StoredCardApiTest,StoredCardCheckoutTest'`（导出 MOR_RABBITMQ_* 等，不打印） | counterparty 4/4、acquiring 9/9 通过 |
| `test -pl tools/arch-rules` | 68/68 通过 |
| 全 reactor `test-compile` | 通过 |
| `node --test scripts/pdmaner/card-model.test.mjs`；`generate-card-model.mjs --check`；`generate-table-ownership.mjs --check` | 2/2 通过；模型与表归属为最新 |
| `npm --prefix requirements-site test` | 未运行（本卡未改需求站） |

基线既有失败（未改动相关文件）：`CardMaterialTicketTest.test_securityCodeClaimedRefused`、`StoredCardHttpTest.test_payWithStoredCard` 以 `platform-support-service` 读卡得 404——统一对外出口第二段（654cb14f）起读卡调用方改为 `channel-connector-service`，这两个旧用例未随之更新。不在本卡范围，未修。

## 6. 偏离与裁决

- D1 国家区域契约：Platform 实现属卡 BIN-B，但 Card 编译与 DTO Mock 需要 `CountryRegionQueryApi`。在本 worktree 的 `paytech-platform-support-api` 按 technique §3.5.3 新增接口与两个 Response（不含实现），并已消息 team-lead 请 BIN-B 用同名同路径，合并时取一份。
- D2 Mock 的 Context Path：两个 Mock Controller 各自 `addPathPrefix("/platform-support")` 会互相覆盖，改为在 `MockPlatformFileController` 一处登记、谓词覆盖两个接口。
- D3 补偿 Lease：Foundation 要求 Handler 超时短于 Lease；导入超时 15 分钟（技术方案），故 card 生产与测试 `compensation.lease-duration` 设为 20m（card 只有这一个补偿 Handler）。
- D4 资源锁：card 原先 `redis.coordination.enabled=false`，AbstractLockProcessor 需要 Redisson，生产与测试都改为 true。
- D5 CSV 只按物理行处理：引号字段内不允许换行（会判列数 / 引号错误）；单行超过 4096 字节判 `ENCODING_INVALID`；引号不闭合归 `COLUMN_COUNT_MISMATCH`（整行错误、列名为空）。
- D6 C7 指定版本：版本不存在 404；指定 VALIDATING / INVALID 版本 400（号段可能不完整）。缺 `X-Caller-Service` 头为 400（沿用 card 既有 required 头行为，不是 404）；请求体字段校验失败按公共合同为 422。
- D7 Handler 依赖写入 Service 用 `ObjectProvider`（补偿登记器 ← Handler 注册表 ← Handler ← Service ← 登记器 的启动循环），同 reconciliation 先例。
- D8 card-app Converter 读取 card-core DataObject（同一服务 core/app 拆分，沿用 reconciliation / commercial 先例，R9 允许）。
- D9 acquiring / counterparty 对 `cardBrand` 的处理：可空；有值但不是 card 发布的 `CardBrand` Code 时按上游协议违例失败关闭（counterparty 502 `UPSTREAM_PROTOCOL_ERROR`，acquiring 5xx），与两边对已存卡其他字段的严格校验一致。OpenAPI 用 `type: [string, 'null']` + 格式，描述当前取值并要求客户端容忍新增值。
- D10 下载客户端：独立 `RestClient`（JDK HttpClient，连接 2 秒、等待响应头 10 秒、不跟随重定向），不经 Foundation 出站边界（对象存储预签名地址不是第一方 Problem 合同）；R15 只限制直接调用 `HttpClient#send`，经 `JdkClientHttpRequestFactory` 允许。
- D11 首次构建时用了 `install` 把 `paytech-platform-support-api`、`paytech-card-api`、`paytech-card-core` 写入共享 `~/.m3`（均为新增字段 / 类，向后兼容）；之后改为 reactor `-am` 构建，未再安装。

## 7. 遗留项

- 导入任务达到 30 次上限后版本停在 VALIDATING（补偿记录 FAILED 可见、可人工重开），没有自动转 INVALID。
- 部署前置（README 已写）：Platform 内部文件接口 DUAL 与 CSV 识别、`t_country_region` 与查询接口先上线；Card 容器能访问对象存储下载地址并信任 test-ca；card 实例 Redis 需支持 Redisson。部署顺序仍是 Payment / acquiring / counterparty 先于 Card。
- Payment 冻结快照、Risk、ops-bff / ops-web、Platform 实现分属其他卡。

## 8. WRITE_API_EXAMPLE 第 11 节核对

- API 与 Body：`CardBinLibraryApi` + 独立 RequestBody / ResponseBody class、中文字段注释、`@Pattern/@Min/@Max/@NotBlank`；内部 Request 注解由 `validateBasic` 触发。✔
- Controller / Converter 仅适配；两个写 Processor 在 card-core `service/processor`，主方法显示原文件重放提前返回与新请求阶段。✔
- Processor 只查本模块 Repository（`findByFileAddress`、`findByVersionNo`、`findActive`、`nextVersionNo`），不调用 Mapper、不逐表写。✔
- Context：`BinLibraryActivationContext` 持有本模块 DataObject，构造完整，纯方法无外部访问；创建版本无多余 Context（单个 DataObject）。✔
- Service 不调其他 Service / Processor / 外部依赖；Handler 编排 Service 与 Integration。✔
- 事务位置明确，事务内无 HTTP（`transactions::assertOutsideTransactions` 断言通过）；保存失败不推进。✔
- Repository 按表，条件 SQL + 行数检查，成功后同步原对象。✔
- 锁：两个写入口同一 `card-bin-library` TRY_LOCK；数据库版本号 / 文件地址 / 生效占位唯一索引与 CAS 兜底；并发激活测试只成功一个。✔
- 原请求比较：同一 `fileAddress` 返回原版本（200），不重查 Platform；激活无业务操作身份，按当前状态 + 期望生效版本判断。✔
- 不处理 `Idempotency-Key`、未接入命令幂等组件。✔
- 多表查询用 View（`CardBinMatchView`、`BinLibraryActivationView`）；写接口不加 View。✔
- 无生产 Mock、无伪造成功、无无消费者字段（`issuerName` 只在运营查询返回）。✔
- 测试：真实 HTTP / Dispatcher / Scanner 入口、本地 MySQL / Redis / RabbitMQ、Platform DTO Mock + 意外调用检查 + 每例清理；未覆盖项见 §5、§7。✔
