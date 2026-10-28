# 阶段 4 第 1 步：arch-rules 角色识别 + 参考数据客户端两个模块 · 研发与验证记录

> 工作区：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-refclient`（detached `5e1f16ab`），私有 Maven 仓库 `~/.m3-refclient`。未提交、未暂存、无 SQL、无 docker。
> 对应 technique.md 第 7—11 节、第 13 节（TECHNOLOGY_STACK / JAVA_PROJECT_STRUCTURE 登记）与第 14 节步骤 1。

## 1. 已读规范

AGENTS.md、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`UNIT_TEST_GUIDE.md` + `examples/UNIT_TEST_EXAMPLE.md`、`TECHNOLOGY_STACK.md`（6.2 http-client-support、6.13 test-support）；先例：`vault/paytech-vault-client`、ledger 的 `CurrencySpecSnapshot` / `PlatformCurrencyClientImpl` / `CurrencySnapshotRefreshScheduler` / `CurrencySnapshotHealthIndicator` / `CurrencySnapshotProperties`、client-master `InternalCallTokenOptions`、Foundation `HttpServiceClientFactory` / `HttpServiceClientOptions`、ops-bff `PlatformClientConfiguration`、`paytech-test-support`（MockHttpServer、MockHttpFault、Clear、EnableMockHttpServer）、ledger-test `MockCurrencySpecApi` / `CurrencySpecMocker`；Platform 合同 5 个 `*QueryApi` 与响应 DTO、`ReferenceDataStatus`、`CurrencyRoundingMode`、`t_ref_*` DDL 与 Platform 端编码校验正则。

本步不新增写接口，WRITE_API_EXAMPLE 第 11 节核对项不适用（没有 Processor / Context / 本地写入）。

## 2. 文件清单

### 新增

| 路径（相对 `paytech/`） | 内容 |
|---|---|
| `platform-support/paytech-platform-support-reference-data-client/pom.xml` | 依赖：platform-support-api、common-core、http-client-support、internal-auth（optional）、spring-cloud-commons（optional）、spring-boot-autoconfigure、spring-boot-health、spring-web、jackson-databind、slf4j-api。无 Caffeine / DB / Redis |
| `…-reference-data-client/src/main/java/com/paytech/platform/support/referencedata/client/` | 公共包：`ReferenceData`、`ReferenceCatalog`、`ReferenceItem`、`ReferenceCurrency` / `ReferenceCountry` / `ReferenceSubdivision` / `ReferenceIndustry` / `ReferenceMcc`、`ReferenceDataKind`、`ReferenceDataNotFoundException`、`ReferenceDataUnavailableException`、`ReferenceDataRefresher` |
| `…/client/autoconfigure/` | `ReferenceDataClientAutoConfiguration`、`ReferenceDataClientProperties` |
| `…/client/internal/` | `PlatformReferenceDataLoader`、`ReferenceDataSnapshot`、`ReferenceDataRefreshScheduler`、`ReferenceDataHealthIndicator`、`BoundedResponseInterceptor`、`LoadBalancedPlatformTarget` |
| `…-reference-data-client/src/main/resources/META-INF/spring/…AutoConfiguration.imports` | 登记自动配置 |
| `platform-support/paytech-platform-support-reference-data-client-test-support/pom.xml` | 依赖客户端、api、common-core、http-problem、paytech-test-support、spring-web、spring-boot-test、junit-jupiter-api；test 范围：junit-jupiter、internal-auth、spring-cloud-commons、assertj-core、awaitility |
| `…-test-support/src/main/java/…/client/test/` | `MockReferenceDataApi`、`ReferenceDataMocker`、`ReferenceDataBuilders` |
| `…-test-support/src/test/java/…/client/test/base/ReferenceDataClientTestBase.java`、`MockServerLoadBalancerClient.java` 与 `…/client/auto/*Test.java`（5 个类） | 客户端 auto 测试（见第 6 节） |
| `tools/arch-rules/src/main/java/com/paytech/arch/rule/ClientTestSupportDependencyRule.java` | 新规则 R16 |
| `tools/arch-rules/src/test/java/com/paytech/arch/fixture/r7/refdata/**`、`src/test/resources/fixtures/graph/r16-pass/**`、`r16-violation/**`、`fixtures/modules/platform-support/paytech-platform-support-reference-data-client/pom.xml` | R7 例外与 R16 fixture |

### 修改

| 路径 | 改动 |
|---|---|
| `paytech/build/paytech-dependencies/pom.xml` | BOM 登记两个新模块 |
| `paytech/platform-support/pom.xml` | `<modules>` 与 `dependencyManagement` 登记两个新模块 |
| `tools/arch-rules/…/model/ModuleIdentity.java`、`ModuleRole.java` | 后缀以 `-client-test-support` 结尾 → 新角色 `CLIENT_TEST_SUPPORT`（先判定）；以 `-client` 结尾 → `CLIENT`；vault-client 仍为 CLIENT |
| `tools/arch-rules/…/ArchitectureRules.java` | 依赖图规则加入 R16 |
| `tools/arch-rules/…/rule/TransactionExternalCallRule.java`（R7） | 例外：`paytech-platform-support-reference-data-client` 的 `ReferenceData`、`ReferenceCatalog` 可在事务类注入；同模块 `ReferenceDataRefresher` 仍按 client 接口拦截 |
| arch-rules 测试：`ModuleModelAutoTest`、`DependencyGraphRulesAutoTest`、`TransactionExternalCallRuleAutoTest`、`ArchitectureRulesAutoTest` | 新角色识别、R16 pass / violation、R7 例外、规则清单 |
| `tools/arch-rules-reactor-test/…/ReactorArchitectureAutoTest.java` | 依赖图规则集合加 R16；新增 `test_clientTestSupportDependency`（真实 Reactor 中两个新模块角色正确、零 R16 违规，注入违规后生效） |
| `platform-support/paytech-platform-support-test/…/architecture/ArchitectureAutoTest.java` | 服务级规则检查的模块列表加入两个新模块 |
| `agent-docs/TECHNOLOGY_STACK.md` | 技术基线表新增"进程内参考数据"一行 |
| `agent-docs/JAVA_PROJECT_STRUCTURE.md` | 第 3 节新增服务 Java Client / Client 测试支持的角色与依赖规则 |

未改决定文档（按要求留待后续）。

## 3. 公共 API（实际实现）

```java
public interface ReferenceData {
    ReferenceCatalog<ReferenceCurrency> currencies();
    ReferenceCatalog<ReferenceCountry> countries();
    ReferenceCatalog<ReferenceSubdivision> subdivisions();
    ReferenceCatalog<ReferenceIndustry> industries();
    ReferenceCatalog<ReferenceMcc> mccs();
}

public interface ReferenceCatalog<T extends ReferenceItem> {
    T require(String code);           // 不存在 → ReferenceDataNotFoundException（ErrorException, INTERNAL_ERROR）
    T requireActive(String code);     // 不存在 → NotFound；非 ACTIVE → BizCheckFailException
    Optional<T> find(String code);    // 不存在或 code==null → empty
    List<T> list();                   // 含全部状态，按编码升序，不可修改
}
// 未配置或未就绪：四个方法都抛 ReferenceDataUnavailableException（ErrorException, SERVICE_UNAVAILABLE）

public interface ReferenceItem { String code(); ReferenceDataStatus status(); }

public record ReferenceCurrency(String code, String numericCode, int minorUnit, CurrencyRoundingMode roundingMode, ReferenceDataStatus status)
public record ReferenceCountry(String code, String alpha3Code, String numericCode, String regionCode, String callingCode, String defaultCurrencyCode, ReferenceDataStatus status)
public record ReferenceSubdivision(String code, String countryCode, String parentCode, String category, ReferenceDataStatus status)
public record ReferenceIndustry(String code, String parentCode, int level, String defaultMccCode, ReferenceDataStatus status)
public record ReferenceMcc(String code, boolean networkHighRisk, ReferenceDataStatus status)

public enum ReferenceDataKind implements CodeEnum { CURRENCY, COUNTRY, SUBDIVISION, INDUSTRY, MCC }

public class ReferenceDataNotFoundException extends ErrorException {
    public ReferenceDataNotFoundException(ReferenceDataKind kind, String code); // "参考数据不存在: CURRENCY XYZ"
    public ReferenceDataKind getKind();
    public String getReferenceCode();   // getCode() 已被 ErrorException 用作错误码
}
public class ReferenceDataUnavailableException extends ErrorException {
    public static ReferenceDataUnavailableException notConfigured(ReferenceDataKind kind); // "参考数据未配置: MCC"
    public static ReferenceDataUnavailableException notReady(ReferenceDataKind kind);      // "参考数据未就绪: MCC"
    public ReferenceDataKind getKind();
}
// requireActive 消息："参考数据未启用: COUNTRY XX, 状态 INACTIVE"

public interface ReferenceDataRefresher {
    List<Result> refreshNow();   // 同步刷新全部已开启种类；按种类声明顺序返回
    record Result(ReferenceDataKind kind, boolean succeeded, int count, String failure) {}  // failure 只有异常类名
}
```

Bean：`referenceDataSnapshot`（类型 `ReferenceDataSnapshot implements ReferenceData`）、`referenceDataRefresher`（`ReferenceDataRefreshScheduler implements ReferenceDataRefresher, SmartLifecycle`）、`referenceData`（`ReferenceDataHealthIndicator`，健康组件名 `referenceData`）、`referenceDataLoader`。消费方按接口类型注入。

## 4. 配置键（`platform-support.reference-data.client.*`）

| 键 | 默认 | 校验 |
|---|---|---|
| `base-url` | 无（可选） | 配置时直连（自动测试 / 本机 / 现 ledger、billing 直连方式），须为 http/https、有主机、不含用户信息 / 查询 / 片段；未配置时经服务发现 |
| `service-id` | `platform-support-service` | 未配置 base-url 时的服务发现名，同时是内部调用 Token 的 `aud`；小写服务名格式 |
| `context-path` | `/platform-support` | 未配置 base-url 时拼成 `http://<service-id><context-path>`；为空或以 / 开头且不以 / 结尾 |
| `caller-service` | 取 `spring.application.name` | 每个请求的 `X-Caller-Service`；两者都没有时启动失败；小写服务名格式 |
| `kinds` | 无（必填） | 至少一项；未知值绑定失败 |
| `refresh-interval` | `PT1H` | ≥ 1 分钟 |
| `initial-retry-interval` | `PT10S` | ≥ 1 秒 |
| `scheduling-enabled` | `true` | — |
| `connect-timeout` | `2s` | > 0 |
| `read-timeout` | `5s` | > 0 |

不合法时上下文启动失败（record 构造器抛 `IllegalArgumentException`，消息含配置键）。未配置 base-url 而 classpath 或容器中没有 `LoadBalancerClient`（spring-cloud-starter-loadbalancer）时启动失败，消息说明"引入服务发现或配置 base-url"。

### 访问目标与调用身份（2026-10-27 追加，主会话裁决）

- 照 vault-client：未配置 base-url 时在客户端自建的 RestClient 上加 `LoadBalancerInterceptor(宿主 LoadBalancerClient)`，目标 `http://platform-support-service/platform-support`，与 card / client-master 现有 `lb://platform-support-service/platform-support` 一致，迁移不需新增环境变量、不改 compose。`spring-cloud-commons` 为客户端的 optional 依赖，LoadBalancer 类型只在 `internal/LoadBalancedPlatformTarget` 的内部类中引用（类不存在时不加载）。
- 每个请求始终带 `X-Caller-Service: <caller-service>`（Platform internal-auth DUAL 回退识别）；宿主存在 `InternalTokenSupplier` 时同时附加 `Authorization: Bearer`（`aud` = service-id），与 card `PlatformClientConfiguration` 的做法一致。宽松 JSON、1 MiB 上限、不重试、不跟随重定向不开放配置。

## 5. 与 technique.md 的偏差及原因

| # | 偏差 | 原因 |
|---|---|---|
| D1 | 客户端的 auto 测试放在测试支持模块的 `src/test`，不在客户端模块自己的 `src/test` | 测试支持依赖客户端（Mocker 的 `callCount(ReferenceDataKind)`），客户端测试再依赖测试支持会形成 Maven 循环；放在测试支持模块即"一个测试模块"，与 R16（本服务模块也不得依赖测试支持）一致 |
| D2 | 公共包比第 7 节清单多 `ReferenceItem`（第 8 节泛型上界已隐含）与 `ReferenceDataRefresher.Result`（嵌套 record） | 第 10 节要求 `refreshNow()` "返回每类的成功 / 失败结果"，需要一个结果类型；嵌套避免新增顶层类 |
| D3 | arch-rules 新增规则编号 R16（`ClientTestSupportDependencyRule`，只有依赖图部分，任何范围的依赖都检查） | R1 / R6 只看 core / app 的非 test 依赖，无法表达"只允许其他服务的 -test 依赖"；与 R13 同为依赖图专用规则 |
| D4 | R7 增加例外：参考数据客户端的 `ReferenceData` / `ReferenceCatalog` 可在事务类注入 | 改为 CLIENT 角色后，R7 会把 CLIENT 模块的接口一律视为"HTTP 接口"，ledger / billing 在事务内读币种（需求目标 2）会被误判；例外按类型名 + 模块坐标限定，`ReferenceDataRefresher` 仍拦截 |
| D5 | Platform `ArchitectureAutoTest` 的模块列表加入两个新模块 | 让服务级规则（含 R7、R15 类级部分）覆盖新代码 |
| D6 | AC-04 的"短周期"：`refresh-interval` 下限 1 分钟（第 9 节），测试不能把定时刷新缩到秒级 | 改为三点证明：`refreshNow()`（调度线程走同一入口）读到新值；开启调度时用 1 秒重试周期验证首次重试，并验证就绪后 2.5 秒内不再请求（已转入 1 小时周期）；默认值绑定为 PT1H |
| D7 | 校验比第 10 节第 4 条多几项：行政区代码须以所属国家开头；行业层级只能 1 / 2 且细类必须、大类不得有上级；国家区域 / 行政区类别必须为大写编码；MCC 的 `networkHighRisk` 必填；可空的外键编码（法定币种、上级行政区、建议 MCC）有值时校验格式 | 与 DDL 的 NOT NULL / 层级约束一致，避免把结构上不合法的条目装入快照；不合法只让该种类本次失败 |
| D8 | `spring-boot-health` 为客户端的 compile 依赖 | 健康指标是必需能力；四个消费方均已有 actuator |

### HTTP 客户端选项的选择

`HttpServiceClientFactory.create(restClient, HttpServiceClientOptions.strict())`（有 Token 时 `.withInternalToken(supplier, "platform-support-service")`）：

- Foundation 的 `strict()` 只表示严格的 RFC 9457 Problem 错误解码；**没有**调用 `withStrictJsonResponses(n)`，成功响应不走严格转换器。Platform 早已输出 RFC 9457，因此不用 `migrationWithLegacyErrorResponse()`（它额外接受旧四字段错误体，本客户端无此需要）。
- 成功响应用客户端自建的 `JsonMapper`（显式 `disable(FAIL_ON_UNKNOWN_PROPERTIES)`），Platform 追加字段被忽略（测试 `test_unknownFieldsTolerated`）。
- Foundation 只在严格 JSON 模式下限制响应大小（`strictJsonResponseBytes=0` 时不限），所以客户端加了自己的 `BoundedResponseInterceptor`（1 MiB，超限抛 UPSTREAM_INVALID_RESPONSE；测试 `test_responseTooLarge`）。
- 传输：JDK `HttpClient`，`Redirect.NEVER`，连接 / 读取超时取配置；`RestClient.builder()` 新建，不复用宿主的 Boot Builder，避免继承宿主拦截器或严格转换器；没有 Retry 拦截器。

## 6. 测试 → AC 对照

全部位于 `paytech-platform-support-reference-data-client-test-support/src/test/java/com/paytech/platform/support/referencedata/client/auto/`；真实自动配置（`ApplicationContextRunner`）+ 真实 HTTP 客户端 → 独立 `MockHttpServer`（`MockReferenceDataApi` + `ReferenceDataMocker`）；每例 AfterEach 先 `awaitIdle`，再断言 Mock 服务与 Mocker 无意外请求，最后清理。没有 Mockito / `@MockBean`。

| 测试 | 覆盖 | AC |
|---|---|---|
| `ReferenceDataReadTest.test_normalFlow` | 五类全量加载、各值对象字段、list 升序且不可修改、健康 UP 与明细 | AC-01 |
| `ReferenceDataReadTest.test_onlyConfiguredKinds` | 只开 CURRENCY：只请求 1 次；其他四类所有读法报"参考数据未配置"；健康明细只含 CURRENCY | AC-01 |
| `ReferenceDataReadTest.test_readsAreMemoryOnly` | 加载后 20 轮共 120 次读取（含查不到）前后 `totalCalls()` 不变；NotFound 消息 / kind / code / INTERNAL_ERROR | AC-05 |
| `ReferenceDataReadTest.test_statusReads` | ACTIVE / INACTIVE / DRAFT / 不存在 / null 下 require、requireActive、find 的结果与消息 | AC-06 |
| `ReferenceDataReadTest.test_internalToken` | 始终带 `X-Caller-Service`（取 spring.application.name）；无 Supplier 不带 Authorization；有 Supplier 同时带 `Bearer`（目标 platform-support-service，Purpose GENERAL） | — |
| `ReferenceDataTargetTest.test_normalFlow` | 不配 base-url：经测试内 `MockServerLoadBalancerClient`（实现 LoadBalancerClient，非 Mockito）解析 platform-support-service 到 Mock 服务并加载，带 X-Caller-Service | 追加 2 |
| `ReferenceDataTargetTest.test_customServiceIdentity` | 自定义 service-id 解析；caller-service 优先于 spring.application.name | 追加 1、2 |
| `ReferenceDataTargetTest.test_noDataFound` | 服务名没有实例：该种类失败、零请求 | 追加 2 |
| `ReferenceDataTargetTest.test_invalidParameter` | 不配 base-url 且没有 LoadBalancerClient Bean：启动失败，消息含 LoadBalancerClient 与 base-url | 追加 2 |
| `ReferenceDataRefreshTest.test_platformUnavailableAtStartup` | 开启调度、Platform 503：启动不阻塞、OUT_OF_SERVICE、读报"未就绪"（find 也报）、只重试未成功种类、恢复后 1 秒内重试成功变 UP、之后 2.5 秒无请求 | AC-02、AC-04 |
| `ReferenceDataRefreshTest.test_refreshFailureKeepsSnapshot` | 就绪后 503：返回失败结果、保留旧值、健康仍 UP、loadedAt 不变、consecutiveFailures=2、lastFailure 为类名；恢复后清零 | AC-03 |
| `ReferenceDataRefreshTest.test_refreshPicksUpChanges` | 修改 / 新增 / 删除编码后刷新读到新快照 | AC-04 |
| `ReferenceDataRefreshTest.test_defaultIntervals` | 默认 PT1H / PT10S / 调度开启 / 2s / 5s，调度首次加载成功 | AC-04 |
| `ReferenceDataRefreshTest.test_noDataFound` | 未刷新：OUT_OF_SERVICE、读报未就绪、零请求 | AC-02 |
| `ReferenceDataRefreshTest.test_failureIsolatedPerKind` | 币种不合法只让 CURRENCY 失败，其余四类装入 | B4 |
| `ReferenceDataValidationTest.test_invalidParameter`（19 组参数） | 小数位 9 / -1 / null、舍入方式未知、状态未知、编码空 / 格式非法 / 重复、缺 items、国家币种格式、国家缺区域、行政区国家不一致、细类缺上级、大类带上级、层级 3、MCC 缺高风险、MCC 非四位、MCC total 大于 / 小于实际 | 第 10 节第 4 条 |
| `ReferenceDataValidationTest.test_mccPaging` | 450 条 → 3 页请求，全量装入 | S-05 |
| `ReferenceDataValidationTest.test_edgeCase` | 恰好 200 条一页完成；空目录 0 条 | S-05 |
| `ReferenceDataValidationTest.test_unknownFieldsTolerated` | 条目与外层追加未知字段仍加载 | Q4 / S-06 |
| `ReferenceDataValidationTest.test_responseTooLarge` | 超过 1 MiB 本次失败 | S-06 |
| `ReferenceDataClientPropertiesTest.test_invalidParameter`（13 组）、`test_noDataFound`、`test_normalFlow` | base-url 空 / ftp / 带用户信息、kinds 空 / 未知、service-id 非小写服务名、context-path 无前导 / 有尾随斜杠、caller-service 非法、refresh 59s、retry 0.5s、超时 0 / 负；缺 kinds、缺调用服务名启动失败；种类去重、下限值允许、service-id / context-path 默认值与发现地址 | 第 9 节、追加 1 |

arch-rules：`ModuleModelAutoTest`（两个新后缀的角色）、`DependencyGraphRulesAutoTest.test_clientTestSupportDependency` + `r16-pass`（core 依赖 CLIENT 不违反 R1）、`TransactionExternalCallRuleAutoTest.test_edgeCase`（R7 例外与 Refresher 仍违规）、`ArchitectureRulesAutoTest`（规则清单）、`ReactorArchitectureAutoTest.test_clientTestSupportDependency`。

## 7. 执行命令与结果（JDK 25，`paytech/` 下，`.mvn/maven.config` → `~/.m3-refclient`）

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
./mvnw -o -q -pl platform-support/paytech-platform-support-reference-data-client,platform-support/paytech-platform-support-reference-data-client-test-support -am -DskipTests install
#   BUILD SUCCESS（含依赖模块安装到 ~/.m3-refclient）
./mvnw -o -pl platform-support/paytech-platform-support-reference-data-client,platform-support/paytech-platform-support-reference-data-client-test-support install
#   Tests run: 53, Failures: 0, Errors: 0（Read 5、Target 4、Properties 15、Refresh 6、Validation 23）BUILD SUCCESS（追加任务后重跑）
./mvnw -o -pl tools/arch-rules,tools/arch-rules-reactor-test install
#   arch-rules Tests run: 70, Failures: 0；arch-rules-reactor-test Tests run: 5, Failures: 0；BUILD SUCCESS
#   （首次运行 ArchitectureRulesAutoTest 因规则清单仍为 R1..R15 失败 1 例，更新期望后通过）
./mvnw -o -q -pl platform-support/paytech-platform-support-test -am -DskipTests install
./mvnw -o -pl platform-support/paytech-platform-support-test test -Dtest=ArchitectureAutoTest -Dsurefire.failIfNoSpecifiedTests=false
#   Tests run: 1, Failures: 0（Platform 服务级 R1–R15，含两个新模块；追加任务后重跑仍通过）BUILD SUCCESS
./mvnw -o -DskipTests test-compile
#   整个 Reactor BUILD SUCCESS
```

未运行整个 Reactor 的测试（共享本地数据库）。`~/.m3-refclient` 已安装：`paytech-platform-support-reference-data-client`、`…-client-test-support`、更新后的 `paytech-dependencies`（BOM）与 `paytech-platform-support` 父 POM、`paytech-arch-rules`。

## 8. 给消费方迁移（步骤 2、3）的接入要点

- core：`<artifactId>paytech-platform-support-reference-data-client</artifactId>`（版本由 BOM 管理）；注入 `ReferenceData`（事务内可用）；测试或运维手工刷新用 `ReferenceDataRefresher`（事务外）。
- 配置：走服务发现的服务（card、client-master）**不配 base-url**，默认 `platform-support-service` + `/platform-support`，需宿主已有 `spring-cloud-starter-loadbalancer`；用直连地址的服务（ledger、billing）把 `base-url` 指向已有 Platform 地址变量（含 `/platform-support` Context Path）。`caller-service` 一般不配（取 `spring.application.name`，应与原客户端发送的 X-Caller-Service 一致，如 `card-service`，不一致时显式配置）。`kinds` 按需；readiness `include: readinessState,referenceData`。
- test：`<artifactId>paytech-platform-support-reference-data-client-test-support</artifactId><scope>test</scope>`；把包 `com.paytech.platform.support.referencedata.client.test` 加入本服务 `@EnableMockHttpServer(basePackages=…)`，`base-url` 指向 Mock 服务根地址（Mock 无 Context Path）；`application-mock.yml` 设 `scheduling-enabled: false` 并列全 7 个键；全局 BeforeEach 中 `referenceDataMocker.defaults()`（或按需配置）后调用 `refreshNow()`；全局 AfterEach 调用 `referenceDataMocker.assertNoUnexpectedCalls()`（`clear()` 由 TestBase 统一执行）。
- Mocker：`currency/country/subdivision/industry/mcc(builders…)`、`empty(kind)`、`reset(kind)`、`fault(kind, MockHttpFault)`、`unavailable(kind)`、`recover(kind)`、`reportedMccTotal(n)`、`defaults()`、`callCount(kind)`、`totalCalls()`、`lastAuthorization()`、`lastCallerService()`。消费方测试用 base-url 直连 Mock 服务，不需要服务发现。未配置的种类被请求、或请求带筛选条件，记为意外调用。

## 9. 遗留与待确认

1. R7 例外是按类型名 + 模块坐标限定的点状例外；若以后别的 `-client` 也有"只读内存"的接口，需要再评审是否改为标记注解。
2. `ReferenceDataNotFoundException` 的编码访问器命名为 `getReferenceCode()`（`getCode()` 已被 `ErrorException` 用于错误码）；消费方迁移时注意。
3. card 现在经 `platform-support-service` 组调用 `InternalFileApi`，迁移后该组配置仍要保留；ledger / billing 若只为币种注册了该组，可整体删除（第 12 节）。
4. 决定文档（参考数据决定第 7 条、2026-10-20 币种快照决定、design-discuss 8.1）未改，按指示留到后续步骤。

## 步骤 5：Platform 删除旧接口（2026-10-27）

只改 worktree 的 `paytech/platform-support/**`；card / client-master 由 rdc-cc 并行修改，本步未动。

### 删除前核对

`grep -rnE "/platform/v1/currency-specs|/platform/v1/country-regions|CurrencySpecQueryApi|CountryRegionQueryApi" paytech`（排除 target 与 `docs/database/sql`）：Platform 以外没有调用方。ledger、billing、card、client-master、ops-bff 都已不引用；ops-bff 剩下的 `/currency-admin/v1/currency-specs` 是运营维护路由，不在本步范围；card 测试的 `setUpCountryRegions` 只是方法名。

### 删除（18 个文件）

| 模块 | 文件 |
|---|---|
| api | `CurrencySpecQueryApi`、`CountryRegionQueryApi`；`response/CurrencySpecListResponseBody`、`CurrencySpecResponseBody`、`CountryRegionListResponseBody`、`CountryRegionResponseBody`（核对后确认只有旧接口使用；ops-bff 用的同名类是它自己的 `CurrencySpecManagementModels` 内部类） |
| app | `CurrencySpecController`、`CountryRegionController`、`CurrencySpecConverter` |
| core | `CurrencySpecQueryService`、`CurrencySpecQueryServiceImpl`、`ListCurrencySpecsProcessor`、`GetCurrencySpecProcessor`、`request/ListCurrencySpecsRequest`、`request/GetCurrencySpecRequest`、`response/CurrencySpecResult` |
| test | `CurrencySpecQueryApiTest`、`CountryRegionQueryApiTest` |

国家区域旧接口原本复用 `ListReferenceCountriesProcessor`，没有单独的处理器。

### 保留

- `CurrencySpecStatus`、`CurrencySpecManagementApi` 与币种维护链路（`/currency-admin/v1/currency-specs`）保留。
- `ReferenceCurrencyRepository.find` 和 `findByCurrencyCode` 仍被 `ListReferenceCurrenciesProcessor` 和 `CreateCurrencySpecProcessor` 使用，没有删除任何仓储或 Mapper 方法。

### 修改

- `InternalAuthDualPassThroughTest`：样本改为 `GET /platform/v1/reference-data/currencies?codes=USD`。在 DUAL 模式下无身份读取 USD，响应体为 `ReferenceCurrencyListResponseBody`；`@see` 改为 `ReferenceCurrencyQueryApi#listCurrencies(String, String)`。
- JavaDoc：`ReferenceCurrencyQueryApi`、`ReferenceCountryQueryApi` 改为注明"旧接口已下线，后端经参考数据客户端读取"；`CurrencySpecManagementApi` 的查询说明指向 `ReferenceCurrencyQueryApi`；`ListReferenceCountriesProcessor` 删除旧接口说明。
- 文档：Platform 的 README、`docs` 和 resources 里没有旧接口的清单，无需修改。`console-resource-capabilities.json` 中的 `frontendRoute: /currency-specs` 是前端页面路由，保留。已执行的 SQL（`docs/database/sql/**`）不改。

### 验证

| 命令 | 结果 |
|---|---|
| `./mvnw -o -q -DskipTests install`（整个 reactor，含测试编译） | BUILD SUCCESS |
| `set -a; . local-environment/.env.local; set +a` 后执行 `./mvnw -o -pl platform-support/paytech-platform-support-test test` | 801 个测试中 18 个 Failure、30 个 Error。全部 48 个都是同一原因：worktree 中没有 `local-environment/minio/certs/public.crt`（gitignore），涉及 26 个类（files、filestorage、localization、referencedata 含 logo、workflow 附件、Minio / S3 存储） |
| 重跑上述 26 个类：加 `-Dtest=<26 类> -Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment` | **187 个测试，0 失败，0 错误**。合计 Platform 801 个 auto 测试全部通过，没有与改动相关的失败 |
| `./mvnw -o -DskipTests test-compile`（整个 reactor） | BUILD SUCCESS |
| `./mvnw -o -pl tools/arch-rules,tools/arch-rules-reactor-test test` | 70 + 5 个测试，0 失败 |
| 全仓库 grep 四个关键字（paytech 源码与测试，排除 `docs/database/sql`） | 只剩两处说明旧接口已下线的 JavaDoc，没有代码引用 |

环境说明：约 23:08，`~/.m2` 被外部重建，只剩 `wrapper/`，`settings.xml` 不再存在。之后离线构建报错"present, but unavailable"：本地仓库记录这些 POM 来自 `maven-central` / `maven-public`，现在的默认仓库是 `central`，记录对不上。因此第二轮起的命令加了 `-Daether.enhancedLocalRepository.trackingFilename=_no_tracking_`，只读取 `~/.m3-refclient`。`~/.m2` 没有恢复也没有修改；其他会话的离线构建可能受到同样影响。

发布注意（technique 第 5 节）：这一版 Platform 必须在所有消费方（ledger、billing、card、client-master、ops-bff）上线并验收之后才能发布。
