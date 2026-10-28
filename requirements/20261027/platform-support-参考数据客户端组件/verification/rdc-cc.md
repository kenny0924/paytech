# 阶段 4 第 3 步（部分）：card 与 client-master 迁移到参考数据客户端 · 研发与验证记录

> 工作区：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-refclient`，只改 `paytech/card/**` 与 `paytech/client-master/**`。私有 Maven 仓库 `~/.m3-refclient`。未提交、未暂存、无 SQL、无 docker。
> 对应 technique.md 第 12 节 "card"、"client-master" 两行。

## 1. 已读

requirement.md、technique.md（第 6—14 节）、verification/rdc-core.md；上一需求 `platform-support-参考数据目录与client-master迁移` 的 technique.md 与 verification/cm-dev.md；worktree 内 AGENTS.md、DEVELOPMENT_CONVENTIONS.md、UNIT_TEST_GUIDE.md + examples/UNIT_TEST_EXAMPLE.md。核对了 Platform `CountryRegionController`（只读）与 Foundation `CompensationEventDispatcher` 的异常分类。

本步不新增写接口，没有新 Processor / Context / 本地写入；WRITE_API_EXAMPLE 第 11 节不适用。改动只把"事务外 HTTP 查询"换成"读进程内快照"，事务边界不变。

## 2. card

### 2.1 改动

| 文件（相对 `paytech/card/`） | 改动 |
|---|---|
| `paytech-card-core/.../integration/PlatformCountryRegionClient.java`、`impl/PlatformCountryRegionClientImpl.java` | 删除 |
| `paytech-card-core/pom.xml` | 增加 `paytech-platform-support-reference-data-client`（版本由 BOM 管理） |
| `paytech-card-core/.../service/handler/CardBinLibraryImportHandler.java` | 注入 `ReferenceData`；新方法 `countryRegions()` = `referenceData.countries().list()` → `code → regionCode`（不按状态过滤）；类注释与 `handle` 注释补充"国家参考数据未就绪为可重试" |
| `paytech-card-app/.../configuration/PlatformClientConfiguration.java` | `@ImportHttpServices` 只保留 `InternalFileApi`；注释更新 |
| `paytech-card-app/src/main/resources/application.yml` | 新增 starter 配置；readiness 组；serviceclient 注释去掉"国家区域映射" |
| `README.md` | 模块说明与导入流程改为读快照 |

### 2.2 行为保持证据

- **状态过滤**：旧接口 `GET /platform/v1/country-regions` 由 `CountryRegionController.listCountryRegions()` 实现，调用 `ListReferenceCountriesProcessor` 时传 `converter.countries(null, null)`（status 为 null），类注释写明"读取全部国家（不限状态）"。新实现用 `countries().list()`（含 DRAFT / INACTIVE），同样不过滤。新增用例 `test_regionFromAnyCountryStatus` 证明 INACTIVE（JP）与 DRAFT（CN）国家仍推导出区域 APAC。
- **映射合法性**：旧客户端校验国家 `[A-Z]{2}`、区域 `[A-Z_]{1,16}`、拒绝重复国家。快照加载时同样拒绝格式非法、重复编码与缺区域（rdc-core D7），不合法时该种类加载失败、保留旧快照。区域列宽 16 的约束仍由号段写入侧保证。
- **未收录国家**：仍是行错误 `COUNTRY_REGION_MISSING`（既有 `test_errorRowsAreCapped` 覆盖 ZZ）。
- **快照未就绪**：`ReferenceDataUnavailableException extends ErrorException`。`CompensationEventDispatcher.finishInvocation` 把 `ErrorException` 一律交给 `finishRetryable`（错误码取异常码 `SERVICE_UNAVAILABLE`），按 Handler 策略重试（30 次，10 秒起 ×2 退避至 2 分钟），不会终态拒绝版本。与旧行为一致：旧客户端在 Platform 不可用时抛的也是 `ErrorException`。因此**没有加显式 catch**。共享测试上下文的快照一旦加载就不能回到未就绪，这条路径没有 card 端用例，依据是框架代码。
- **HTTP 次数**：导入期间不再请求 Platform 国家数据。用例断言参考数据请求总数仍只有 BeforeEach 刷新的那 1 次。

### 2.3 配置差异

```yaml
# application.yml 新增
platform-support:
  reference-data:
    client:
      kinds: [COUNTRY]          # 不配 base-url：服务发现 platform-support-service + /platform-support
management.endpoint.health.group.readiness.include: readinessState,referenceData   # 原先没有显式 readiness 组
```

- `spring.http.serviceclient.platform-support-service`（lb://…）保留：`InternalFileApi` 仍使用它。
- 不配 `base-url`，按 starter 默认经服务发现访问 `platform-support-service` + `/platform-support`，与原来的 `lb://` 一致；不新增环境变量，compose 不改。
- `X-Caller-Service` 取 `spring.application.name=card-service`，与原客户端发送的 `CardApiConstants.SERVICE_ID` 一致，不需要显式配置 `caller-service`。用例 `test_importLargeFile` 断言了 `lastCallerService() == card-service`。

### 2.4 测试改动

- 删除 `MockCountryRegionController`；`PlatformMocker` 去掉国家区域配置、计数与断言；`MockPlatformFileController` 的 `/platform-support` 前缀只套 `InternalFileApi`。
- `CardTestConfiguration` 的 `@EnableMockHttpServer` 扫描加入 `com.paytech.platform.support.referencedata.client.test`。
- `application-mock.yml`：`base-url: ${test.mock-http.base-url}`（Mock 根地址，无 Context Path），列出全部 7 个键，`scheduling-enabled: false`。
- `CardMockTestBase` 注入 `ReferenceDataMocker` 与 `ReferenceDataRefresher`；全局 AfterEach 加 `referenceData::assertNoUnexpectedCalls`。
- `AbstractBinLibraryTest` 新增 `@BeforeEach setUpCountryRegions()`：按原 6 国 `REGIONS` 配置国家并 `refreshNow()`，结果必须全部成功。两个 BIN 库测试类原来的类级 BeforeEach（`platform.mockCountryRegions`）删除。
- `CardBinLibraryImportHandlerTest`：`countryRegionQueryCount()` 断言改为 `referenceData.totalCalls()`；新增 `test_regionFromAnyCountryStatus`。

## 3. client-master

### 3.1 改动

| 文件（相对 `paytech/client-master/`） | 改动 |
|---|---|
| `…-core/.../integration/ReferenceDataClient.java`、`impl/ReferenceDataClientImpl.java`、`domain/onboarding/pack/ReferenceDataItem.java` | 删除 |
| `…-core/pom.xml` | 增加 `paytech-platform-support-reference-data-client` |
| `…/domain/onboarding/requirement/ReferenceCodeCheck.java` | 持有 `Map<String, ReferenceCountry/ReferenceCurrency/ReferenceSubdivision>`；新静态方法 `lookup(ReferenceData, Codes)`，按本次编码 `find(code)`；`active()` 改为 `status() == ReferenceDataStatus.ACTIVE`；行政区仍核对 `countryCode` |
| `…/service/processor/ClientSubmissionPreparer.java` | 注入 `ReferenceData`；`references()` 调用 `ReferenceCodeCheck.lookup`，`ReferenceDataUnavailableException` → `ErrorException(ONBOARDING_DEPENDENCY_UNAVAILABLE)` |
| `…/service/processor/ReplaceClientClassificationProcessor.java` | 注入 `ReferenceData`；新方法 `referenceProblems()` 逐行 `industries().find` / `mccs().find`；行业须 ACTIVE 且 `level == 2`，MCC 须 ACTIVE；消息与 422 `CLASSIFICATION_INVALID` 不变；未就绪 → 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE` |
| `…-app/.../ClientDraftConfiguration.java` | 删除 `referenceDataClient` Bean 与 5 个 `*QueryApi` import（会话、commercial、Tenant 等其他客户端不变） |
| `…-app/.../ClientSubmissionConfiguration.java`、`ClientReviewConfiguration.java` | 参数类型 `ReferenceDataClient` → `ReferenceData` |
| `…-app/src/main/resources/application.yml` | 新增 starter 配置；readiness 组加 `referenceData` |
| `docs/database/CLIENT_MASTER_PHYSICAL_DECISIONS.md` | O03 校验说明改为读进程内快照 |

`OnboardingRequirementEvaluator` 没有改动：它只调用 `ReferenceCodeCheck.countryActive / contains`，这两个方法的签名与语义不变。

### 3.2 行为保持证据（错误码 / 状态）

| 场景 | 旧 | 新 | 用例 |
|---|---|---|---|
| 国家 / 币种 / 行政区不存在、非 ACTIVE、行政区国家不符 | 字段 `FIELD_VALUE_NOT_IN_CATALOG`，C14 422 `SUBMISSION_INCOMPLETE` | 相同 | `SubmitReferenceDataTest` 的 countryNotFound、countryAndCurrencyInactive、subdivisionNotFound、subdivisionCountryMismatch |
| 行业大类 / 不存在 / 停用，MCC 不存在 / 停用 | 422 `CLASSIFICATION_INVALID`，errors[] 原文 | 相同（消息逐字一致） | `ClientClassificationAdminTest` 的 industryNotDetail、mccNotInCatalog |
| 格式非法编码 | 不发往 Platform，视为不存在 | `find` 查不到，视为不存在 | 同上（`54A1` 在输入校验已拒绝） |
| Platform 不可用 | 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE` | 快照已加载：沿用旧快照，不报错（AC-03）；快照未配置 / 未就绪：503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`，不写入 | `test_platformUnavailableKeepsSnapshot`（两个类各一）、`ReferenceDataUnavailableTest` |
| 新辖区国家只改数据 | 有效 | 有效 | `NewJurisdictionByDataTest` |

### 3.3 配置差异

```yaml
# application.yml 新增
platform-support:
  reference-data:
    client:
      kinds: [CURRENCY, COUNTRY, SUBDIVISION, INDUSTRY, MCC]   # 不配 base-url：服务发现
management.endpoint.health.group.readiness.include: readinessState,referenceData   # 原为 readinessState
```

生产配置不写 `base-url`，经服务发现访问，与原 `platformTransport` 的目标相同。`X-Caller-Service` 取 `spring.application.name=client-master-service`，与原 `ClientOnboardingRecoveryConfiguration.CALLER_SERVICE` 一致，不需要显式配置。`SubmitReferenceDataTest.test_normalFlow` 断言了这个值。`validate-group-membership: true` 下 `referenceData` 组件由 starter 无条件注册，启动校验通过（测试上下文启动即证明）。

### 3.4 测试改动

- 删除本服务的 `test/mocker/platform/ReferenceDataMocker`、`test/mock/external/platform/MockPlatformReferenceDataApi`；`MockPlatformAuthenticationApi` 的前缀谓词去掉它；`ClientMasterTestApplication` 扫描加入测试支持包。
- 新增 `test/mocker/platform/ReferenceCatalogFixture`：按编码维护期望条目（同码覆盖，避免向 Mocker 追加重复编码让该种类加载失败），每次变更后整类写入测试支持模块的 `ReferenceDataMocker` 并 `refreshNow()`。保留旧 Mocker 的方法名（`mockStandardCatalog`、`mock`、`mockSubdivision`、`mockIndustry`、`remove`、`count`、`mockUnavailable`）。`mockIndustry` 的第二个参数由层级改为上级大类。
- `ClientMasterMockTestBase`：字段 `referenceData` 改为该 Fixture；全局 AfterEach 加 `ReferenceDataMocker::assertNoUnexpectedCalls`。
- `application-mock.yml`：`base-url: ${client-master.test.external.base-url}`，7 个键，`scheduling-enabled: false`。
- 断言"按编码查询 Platform 几次 / 带哪些 codes"的地方，改为"操作前后参考数据请求数不变"（读快照不发 HTTP）。改动的类：SubmitReferenceDataTest.normalFlow、ClientClassificationAdminTest.normalFlow / invalidParameter、DraftAddressFormatTest.subdivisionFormat。
- 两个 `test_platformUnavailable` 按新语义改为 `test_platformUnavailableKeepsSnapshot`：刷新失败，但保留旧快照，业务成功。
- 新增 `ReferenceDataUnavailableTest`（2 例）：使用独立上下文，`kinds=CURRENCY,COUNTRY,INDUSTRY`，用 `@DirtiesContext(AFTER_CLASS)` 在类结束后关闭，避免两个上下文同时消费分区队列。它验证 C14 与提交核对返回 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`，申请仍为 DRAFT，没有提交和审核登记；O03 返回 503，不写分类。

## 4. 执行命令与结果

均在 worktree `paytech/` 下执行，`JAVA_HOME=jdk-25`。环境变量从 `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/.env.local` 导入，未打印；另设 `PAYTECH_REDIS_PASSWORD=$MOR_REDIS_PASSWORD`。card 与 client-master 的测试串行运行。

```bash
./mvnw -o -q -pl card/paytech-card-api,card/paytech-card-core,card/paytech-card-app -DskipTests install          # 成功
./mvnw -o -pl card/paytech-card-test test
#   Tests run: 67, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS（starter 新版安装后复跑，结果相同）
#   （CardBinLibraryImportHandlerTest 9、BinLibraryVersionHttpTest 9、BinLibraryActivationHttpTest 4、ArchitectureAutoTest 1 …）
./mvnw -o -q -pl client-master/paytech-client-master-api,client-master/paytech-client-master-core,client-master/paytech-client-master-app -DskipTests install   # 成功
./mvnw -o -pl client-master/paytech-client-master-test test
#   Tests run: 154, Failures: 0, Errors: 0, Skipped: 0  BUILD SUCCESS（starter 新版安装后复跑，结果相同）
#   （SubmitReferenceDataTest 6、ClientClassificationAdminTest 6、ReferenceDataUnavailableTest 2、NewJurisdictionByDataTest 1、
#     DraftAddressFormatTest 7、ArchitectureAutoTest 1、ReferenceCatalogGuardTest 1 …）
./mvnw -o -DskipTests test-compile        # 整个 Reactor BUILD SUCCESS（当时其他 Agent 的进行中改动也能编译）
```

grep（card、client-master 的 java / yml / yaml / md / xml，排除 target）：`CountryRegionQueryApi`、`/country-regions`、`PlatformCountryRegionClient`、`ReferenceDataClient`、`ReferenceDataClientImpl`、`ReferenceDataItem`、`ReferenceCurrencyQueryApi`、`ReferenceCountryQueryApi`、`CountrySubdivisionQueryApi`、`IndustryQueryApi`、`MccQueryApi`、`PLATFORM_SERVICE_URL`，**均无命中**。

## 5. 偏差

| # | 偏差 | 原因 |
|---|---|---|
| E1 | card 没有为"快照未就绪"加显式 catch | 补偿框架已把 `ErrorException` 当作可重试，行为与旧客户端不可用时相同；加 catch 只会多一条测试覆盖不到的分支 |
| E2 | client-master 的未就绪用例用"种类未配置"触发 | 与"未就绪"抛同一个 `ReferenceDataUnavailableException`，走同一条转换路径；共享上下文的快照无法回到未就绪。代价是多启动一个上下文（约 8 秒，类结束关闭） |
| E3 | client-master 保留一个本服务的 `ReferenceCatalogFixture` | 它不是 HTTP Mock，只把"按编码覆盖"的期望条目写入测试支持模块的 Mocker 并刷新。用例需要在基线上改单个编码的状态，而测试支持 Mocker 只能追加，重复编码会让该种类整体加载失败 |
| E4 | 旧"Platform 不可用 → 503"用例改为"快照保留，业务成功" | 这是本需求的目标行为（AC-03）；503 只在快照未配置或未就绪时出现，由 E2 覆盖 |

## 6. 遗留与待确认

1. 已解决：rdc-core 已为 starter 增加"始终发送 `X-Caller-Service`"和"不配 base-url 时走服务发现"。两个服务改为不配 base-url，原先新增的两个环境变量已撤回，compose 不需要修改。
2. 自动测试都配置了 base-url 指向 Mock 服务，生产的服务发现路径在 card / client-master 侧没有用例覆盖；该路径由 starter 的 `ReferenceDataTargetTest` 覆盖。
3. client-master 的 DDL / PDManer 注释仍写着"提交时向 Platform 校验"。语义仍然成立（数据来自 Platform），本步按"无 SQL"要求没有改。
4. card 的"快照未就绪 → 可重试"没有 card 端自动化用例（见 E1）。
