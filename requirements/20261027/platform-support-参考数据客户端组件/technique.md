# platform-support：参考数据客户端组件 · 技术方案

> 状态：**阶段 2 已确认（S-01—S-08 按建议）；阶段 3 评审稿（2026-10-27）**：第 1—5 节为现状核对（基于 main `5e1f16ab`），第 6 节起为方案。需求见 [requirement.md](./requirement.md)。

## 1. 核对发现

| # | 发现 | 证据 | 影响 |
|---|---|---|---|
| F-01 | ledger、billing 的快照都由三部分组成：检查周期 `PT5S` 加刷新间隔 `PT60S`；首次成功前每 5 秒重试；查不到时请求提前刷新。两家的配置键和 readiness 组 `currencySnapshot` 相同 | `ledger/.../CurrencySnapshotRefreshScheduler.java`、`application.yml:120-129`（ledger）、`:149-155`（billing）、readiness `include: readinessState,currencySnapshot` | 如果只按"每 1 小时刷新"实现，启动时 Platform 不可用的服务会 DOWN 将近 1 小时 → S-02 |
| F-02 | ledger "查不到币种" 抛 `ErrorException`（系统异常）：AccountPosted 消费按失败重投、不进死信；"已知但状态不允许" 才抛 `BizCheckFailException` | `ledger/.../CurrencyRuleServiceImpl.java:41-53` | starter 的"查不到"异常必须保持系统异常语义，否则 ledger 消息会进死信 → S-03 |
| F-03 | 币种有 DRAFT / ACTIVE / INACTIVE 三种状态。ledger 的 `requireUsable` 拒绝 DRAFT，`requireActive` 只接受 ACTIVE。新接口不传 status 时返回全部状态 | `ReferenceDataStatus`、`ReferenceCurrencyMapper.xml:17-23`、`CurrencyRuleServiceImpl` | 快照要保存全部状态，读方法要能区分状态 → S-04 |
| F-04 | client-master 有 5 个按编码批量查询的方法，"存在且 ACTIVE" 判定为在目录中；失败时转成 422（例如 `CLASSIFICATION_INVALID`），不抛 500 | `ReferenceDataClient.java:20-52`、`ReferenceCodeCheck.java:20-62`、`ReplaceClientClassificationProcessor.java:117` | client-master 需要"判断"式读法，不能只有抛异常的读法 → S-03 |
| F-05 | card 在 BIN 导入（补偿事件处理器）中整体读取 `国家 → 区域` 映射，用来校验 CSV | `CardBinLibraryImportHandler.java:119-126`、`PlatformCountryRegionClientImpl` | 新接口 `ReferenceCountryResponseBody` 已有 `regionCode`，可以直接替换 |
| F-06 | MCC 接口是分页的，每页最多 200 条；现有 298 条，需要翻 2 页。其余 4 类是整表列表 | `MccQueryApi`、`PagingRequestBody`（10/20/50/100/200） | starter 全量加载 MCC 时要翻页，并核对总数 → S-05 |
| F-07 | ledger、billing、card 调 Platform 用严格 JSON，遇到未知字段会失败；响应上限 ledger 为 8192 字节，billing / card 为 65536 字节。ops-bff 用宽松模式 | `HttpServiceClientOptions.strict()`、上一需求 technique F-01 / F-02 | starter 用宽松模式，并设置足够大的上限（Q4 已确认容忍未知字段） → S-06 |
| F-08 | **ops-bff 也是旧接口的调用方**：财务页的币种下拉仍调 `CurrencySpecQueryApi.listCurrencySpecs()`；币种页已改读新接口 | `PlatformCurrencySpecClientImpl.listCurrencies`、`FinanceSetupController` | 下线旧接口前，ops-bff 要改读 `ReferenceCurrencyQueryApi`（仍是转发，不用 starter） → S-07 |
| F-09 | 旧接口只供内部调用，没有 Gateway 路由或权限注册。初始数据中命中 `currency-specs` 的行都是运营页面路由 `/currency-admin/v1/...`，与本需求无关 | `platform-support-initial-data.sql:5547-5579` | 本需求**不需要注册 DML** |
| F-10 | Platform 的 `InternalAuthDualPassThroughTest` 用旧接口 `/platform/v1/currency-specs` 做匿名内部调用样本 | `InternalAuthDualPassThroughTest.java:61` | 下线时改用新接口作为样本 |
| F-11 | 各服务的测试基建各自 Mock 旧 / 新接口：ledger、billing 用 `MockCurrencySpecApi`，card 用 `MockCountryRegionController`，client-master 用 `ReferenceDataMocker`。ledger 测试关闭调度，用公开的刷新方法手动加载 | 各 `*-test` 模块 | starter 提供统一的测试端点、Mocker 和"立即加载"入口，替换这 4 套 → S-08 |
| F-12 | Caffeine 已是 Foundation 能力（`paytech-caffeine-starter`）。它是 Spring Cache 形态：按方法缓存、未命中时调用方法回源。TECHNOLOGY_STACK 规定 Caffeine "只加速可安全回源的数据"。ledger 配置注释明确写了"不用 Caffeine" | `TECHNOLOGY_STACK.md:59`、第 6.6 节；`ledger application.yml:120` | 与"只读内存、没有就报错、整体原子替换"语义不符 → S-01 |
| F-13 | 旧接口其他引用：mor-test compose 注释、`local-environment/flink/pipelines/demo-currency-spec.yaml`（上一需求已记遗留，读的是旧表）、历史需求目录里的验收脚本 | `test-environment/compose.applications.yaml:635,689` | compose 只改注释；flink 维持遗留；历史需求脚本不改 |

## 2. 需求项差异

| 需求项 | 当前 | 目标 | 处理 |
|---|---|---|---|
| R1 starter 模块 | 没有 | `platform-support` 下新增 starter | 新建 |
| R2 五类可配置 | ledger / billing 只有币种，card 只有国家，client-master 按需实时查 | 统一按配置加载 | 新建 |
| R3 轮询 1 小时 | 60 秒 + 5 秒检查周期 | 默认 `PT1H`，可配置 | 加上首次成功前的短周期重试（S-02） |
| R4 只读内存 | ledger / billing 已经只读内存；card / client-master 实时调用 | 全部只读内存，查不到就报错 | 取消"查不到时请求刷新"；读法见 S-03 |
| R5 就绪与观测 | ledger / billing 各有一个健康指标 | starter 提供统一的健康指标 | 配置键 / 组名迁移 |
| R6 测试支持 | 4 套各自的 Mock | 统一提供 | S-08 |
| R7—R9 迁移 | — | 删除复制的实现 | 见第 3 节 |
| R10 下线旧接口 | ledger、billing、card、ops-bff（F-08）在用 | 0 调用方后删除 | 增加 ops-bff 改读（S-07）；不需要注册 DML（F-09） |
| R11 文档 | — | — | 除参考数据决定外，还要修订 [2026-10-20 币种快照读取决定](../../../agent-docs/decisions/2026-10-20-currency-spec-platform-snapshot-read.md) |

## 3. 逐系统影响

| 系统 | 影响点 | 改代码 / 配置 / 模型 | 依赖与风险 |
|---|---|---|---|
| platform-support | 新增 starter 模块（自动配置、属性、快照、调度、健康指标、读接口、异常、测试支持）；最后删除 `CurrencySpecQueryApi`、`CountryRegionQueryApi`，以及对应 Controller、Processor、Request、Response、Converter、测试；`InternalAuthDualPassThroughTest` 换样本 | 代码；无 DDL / DML | 删除旧接口必须在所有消费方上线之后发布 |
| ledger | 删除 `PlatformCurrencyClient(Impl)`、`CurrencySpecSnapshot`、`CurrencySnapshotRefreshScheduler`、`CurrencySnapshotHealthIndicator`、`CurrencySnapshotProperties` / `Configuration`，以及 `PlatformSupportClientConfiguration` 中的币种部分；`CurrencyRuleServiceImpl` 改读 starter，保留 DRAFT / ACTIVE 判定；`CurrencyRule` 视 starter 返回类型决定保留或删除；yml 配置键与 readiness 组改名；测试改用 starter 支持 | 代码 + 配置 | 刷新周期从 60 秒变为 1 小时（Q1 已确认）；"查不到"仍须是可重投的系统异常（S-03） |
| billing | 同 ledger（6 个复制类） | 代码 + 配置 | 同上 |
| card | 删除 `PlatformCountryRegionClient(Impl)`，以及 `PlatformClientConfiguration` 中的 `CountryRegionQueryApi`；`CardBinLibraryImportHandler` 改为从 starter 取 `国家 → 区域` 映射；测试改用 starter 支持 | 代码 + 配置 | 新增依赖：card 启动就绪取决于首次加载成功 |
| client-master | 删除 `ReferenceDataClient(Impl)`、`ReferenceDataItem`；`ReferenceCodeCheck`、`ClientSubmissionPreparer`、`ReplaceClientClassificationProcessor`、`OnboardingRequirementEvaluator` 改读 starter；3 个 Configuration；`ReferenceDataMocker` 改为 starter 支持 | 代码 + 配置 | 行为变化：运营在 Platform 启用新 MCC / 行业后，最长 1 小时才能用于分类；client-master 新增启动就绪依赖 |
| operations-console-bff | 财务页币种下拉 `listCurrencies` 改调 `ReferenceCurrencyQueryApi`，在 BFF 内按状态筛选并投影成原有响应（F-08）；不用 starter | 代码 | 页面响应不变 |
| client-console-bff | 不涉及（只转发 `/reference-data/*`） | 无 | — |
| Gateway / 注册数据 | 不涉及（F-09） | 无 | — |
| arch-rules | 核对模块依赖规则是否允许业务服务依赖 Platform 的 starter（参照 `-api` 的允许方式） | 可能改规则 | 阶段 3 核对 |
| 文档 | 参考数据决定第 7 条、design-discuss 第 8.1 节、2026-10-20 币种快照决定、TECHNOLOGY_STACK / JAVA_PROJECT_STRUCTURE 登记 starter、`CURRENT_IMPLEMENTATION.md`、compose 注释 | 文档 | — |

## 4. 待决问题

| # | 问题 | 建议 |
|---|---|---|
| S-01 | 内存缓存用 Caffeine，还是用不可变 Map 整体替换？ | **用不可变 Map + 原子引用整体替换，不用 Caffeine**（沿用 ledger / billing 现有做法）。理由：① Foundation 的 Caffeine Starter 是 Spring Cache 形态，未命中会调用方法回源，正是本需求要禁止的；② 直接用 Caffeine `Cache` 时，全量替换要先清空再写入，中间会出现半空窗口，做不到原子替换；③ 数据总量不到 1000 条，不需要淘汰、过期或容量控制。如果你仍想用 Caffeine，可以只做"每类一个 Caffeine Cache、刷新时新建再整体替换引用"，但这样它只是一个 Map，没有额外收益 |
| S-02 | 首次加载失败后多久重试？ | **加载成功前按短周期重试**：新增配置 `initial-retry-interval`，默认 `PT10S`。首次全部成功后，再按 `refresh-interval` 刷新，默认 `PT1H`。否则 Platform 短暂不可用时，服务会将近 1 小时无法就绪 |
| S-03 | 读方法怎么"报错"？ | 提供两组读法，都只读内存：① `require*`：查不到时抛 `ReferenceDataNotFoundException`（系统异常，继承 `ErrorException`），ledger 消息重投语义不变；② `find*`：返回 `Optional`，给 client-master 这类要自行转成 422 的校验使用。快照未就绪、种类未配置时，两组读法都抛异常 |
| S-04 | 快照保存哪些状态？ | **保存全部状态**（DRAFT / ACTIVE / INACTIVE），返回对象带 `status`。starter 额外提供 `requireActive`；ledger 的"非 DRAFT 即可用"等业务判定留在 ledger |
| S-05 | MCC 怎么全量加载？ | 每页 200 条，翻到最后一页；翻页过程中如果总数变化，或编码重复，本次刷新整体失败，保留旧快照 |
| S-06 | HTTP 客户端选项 | starter 自建自己的 HTTP 客户端组（不借用消费方已有的 Platform 客户端），使用宽松 JSON，响应上限 1 MiB；Platform 地址用 starter 自己的配置键，默认读取消费方已有的 Platform URL 配置（阶段 3 定稿） |
| S-07 | ops-bff 财务页下拉 | 改读 `ReferenceCurrencyQueryApi`，响应结构不变。这是转发调整，不使用 starter |
| S-08 | 测试支持 | starter 另出一个测试支持 JAR（只在 `*-test` 依赖），包含：测试 HTTP 端点按种类返回配置的 DTO、记录调用次数、每个测试后清理；测试配置关闭调度，由 Mocker 配好数据后调用"立即加载"。AC-05 用调用次数证明读方法不发 HTTP |

## 5. 发布顺序与风险

1. **无 SQL、无注册 DML。** mor-test 上的 Platform 已经提供 `/reference-data/*`（refdata-20261027a）。
2. 消费方（ledger、billing、card、client-master、ops-bff）之间互不依赖，可以按任意顺序替换镜像。
3. **全部上线并验收后**，再发布删除了旧接口的 Platform。Platform 回滚到 `aec53586…` 就能恢复旧接口；如果某个消费方需要回滚到旧镜像，必须先回滚 Platform，或暂缓 Platform 这一步。
4. 风险：
   - ledger、billing 刷新从 60 秒变为 1 小时，新启用的币种最长约 1 小时后才可用于过账。查不到时消息会重投，最终能处理，不会丢。
   - card 和 client-master 新增"启动时必须能连上 Platform"的依赖。

---

## 6. 方案规模（本次方案）

| 项目 | 数量 |
|---|---|
| 涉及系统 | 6 个：platform-support、ledger、billing、card、client-master、operations-console-bff；另外 arch-rules |
| 新增 Maven 模块 | 2 个：客户端模块、测试支持模块 |
| 接口 | 新增 0；修改 0；删除 3 个（`GET /platform/v1/currency-specs`、`GET /platform/v1/currency-specs/{currency}`、`GET /platform/v1/country-regions`） |
| 数据库 | 新增表 0、修改表 0、DDL 0、DML 0、注册 DML 0 |
| 页面 | 0 |

## 7. 模块与归属

| 模块 | 位置 | 内容 | 谁依赖 |
|---|---|---|---|
| `paytech-platform-support-reference-data-client` | `paytech/platform-support/` | 公共读接口、值对象、异常、自动配置、配置属性、内部加载器 / 快照 / 调度 / 健康指标 | ledger-core、billing-core、card-core、client-master-core（`compile`） |
| `paytech-platform-support-reference-data-client-test-support` | `paytech/platform-support/` | 测试 HTTP 端点（实现 5 个 `*QueryApi`）、`ReferenceDataMocker`、值的 Builder、调用计数与清理 | 上述 4 个服务的 `*-test`（`test` 作用域） |

- 依赖：客户端模块只依赖 `paytech-platform-support-api`、`paytech-common-core`、`paytech-http-client-support`，以及可选的 `paytech-internal-auth`。**不依赖 Caffeine**（S-01），也不依赖数据库或 Redis。
- 版本：在 `paytech-dependencies` BOM 中登记。消费方必须显式引入，不通过其他模块传递。
- 先例：`vault/paytech-vault-client`（一个服务提供给其他服务使用的客户端；包分为公共包、`autoconfigure`、`internal`）。
- **arch-rules**：`ModuleIdentity.roleOfSuffix` 现在只把后缀正好是 `-client` 的模块识别为 CLIENT。改为：
  - 后缀以 `-client` 结尾 → CLIENT（其他服务可以依赖，与 `RemoteApiBindingRule` 现有的 API / CLIENT 放行一致）；
  - 后缀以 `-client-test-support` 结尾 → 新角色 CLIENT_TEST_SUPPORT，**只允许其他服务的 `-test` 模块依赖**。
  
  补充对应的 arch-rules 测试。

包结构（`com.paytech.platform.support.referencedata.client`）：

```text
client/                  ReferenceData、ReferenceCatalog、ReferenceCurrency / Country / Subdivision / Industry / Mcc、
                         ReferenceDataKind、ReferenceDataNotFoundException、ReferenceDataUnavailableException、
                         ReferenceDataRefresher
client/autoconfigure/    ReferenceDataClientAutoConfiguration、ReferenceDataClientProperties
client/internal/         PlatformReferenceDataLoader（HTTP 拉取 + 校验 + 转换）、ReferenceDataSnapshot、
                         ReferenceDataRefreshScheduler、ReferenceDataHealthIndicator
```

## 8. 读接口（只读内存）

```java
/** 进程内参考数据：所有方法只读内存，不发 HTTP、不触发刷新 */
public interface ReferenceData {
    ReferenceCatalog<ReferenceCurrency> currencies();
    ReferenceCatalog<ReferenceCountry> countries();
    ReferenceCatalog<ReferenceSubdivision> subdivisions();
    ReferenceCatalog<ReferenceIndustry> industries();
    ReferenceCatalog<ReferenceMcc> mccs();
}

public interface ReferenceCatalog<T extends ReferenceItem> {
    /** 查不到抛 ReferenceDataNotFoundException（系统异常，可重投） */
    T require(String code);
    /** 查不到或状态不是 ACTIVE 时抛异常：查不到 → NotFound；非 ACTIVE → BizCheckFailException */
    T requireActive(String code);
    /** 查不到返回 Optional.empty()，由调用方转换为自己的业务错误（如 client-master 的 422） */
    Optional<T> find(String code);
    /** 当前快照全部条目（含 DRAFT / INACTIVE），不可修改，按编码排序 */
    List<T> list();
}
```

- 上述**所有方法**在以下两种情况都会抛 `ReferenceDataUnavailableException`（系统异常）：该种类没有在配置中开启；或尚未首次加载成功。也就是说，`find` 在"未就绪"时也不会返回空，避免把未就绪误判成"编码不存在"。
- 值对象是 `record`，**只保留后端判定需要的字段**。名称、本地名、描述、logo 属于展示信息，归前端，不进快照：

| 值对象 | 字段 |
|---|---|
| `ReferenceCurrency` | `code`、`numericCode`、`minorUnit`、`roundingMode`（`CurrencyRoundingMode`）、`status` |
| `ReferenceCountry` | `code`、`alpha3Code`、`numericCode`、`regionCode`、`callingCode`、`defaultCurrencyCode`、`status` |
| `ReferenceSubdivision` | `code`、`countryCode`、`parentCode`、`category`、`status` |
| `ReferenceIndustry` | `code`、`parentCode`、`level`、`defaultMccCode`、`status` |
| `ReferenceMcc` | `code`、`networkHighRisk`、`status` |

- `status` 使用 `platform-support-api` 的 `ReferenceDataStatus`（DRAFT / ACTIVE / INACTIVE）。
- 异常信息带种类和编码，例如 `参考数据不存在: CURRENCY XYZ`、`参考数据未就绪: MCC`、`参考数据未启用: COUNTRY XX, 状态 INACTIVE`。

## 9. 配置

```yaml
platform-support:
  reference-data:
    client:
      # 必填：Platform 基址（含 Context Path）；各服务沿用自己已有的环境变量，例如 ${LEDGER_PLATFORM_SERVICE_URL}
      base-url: http://127.0.0.1:18106/platform-support
      # 必填，至少一项：CURRENCY / COUNTRY / SUBDIVISION / INDUSTRY / MCC
      kinds: [CURRENCY]
      # 首次全部加载成功后的全量刷新间隔
      refresh-interval: PT1H
      # 首次加载成功前的重试间隔（S-02）
      initial-retry-interval: PT10S
      # 关闭后不自动刷新，只能经 ReferenceDataRefresher.refreshNow() 触发（自动测试使用）
      scheduling-enabled: true
      connect-timeout: 2s
      read-timeout: 5s
```

- 缺少 `base-url`，或 `kinds` 为空、取值未知时，启动失败（配置校验）。
- 没有为 `refresh-interval` 等参数设置下限以外的硬编码；下限为 `initial-retry-interval ≥ 1s`、`refresh-interval ≥ 1m`。
- 下列项**不开放配置**：宽松 JSON、响应上限 1 MiB、不自动重试、不跟随重定向（S-06）。

## 10. 加载、刷新与就绪

1. **调度**：starter 自带一个单线程守护调度器（线程名 `reference-data-refresh`），不依赖宿主是否开启 `@EnableScheduling`，应用关闭时停止。启动后立即开始首次加载，**不阻塞启动**。
2. **首次加载**：每个已开启的种类各自拉取，成功一类就装入一类。只要还有种类未成功，就按 `initial-retry-interval` 重试未成功的种类。全部成功后转入 `refresh-interval` 周期。
3. **定时刷新**：每个周期全量拉取所有已开启的种类。每类独立进行：成功则整体原子替换（不可变 `Map` 加 `AtomicReference`）；失败则保留该类旧快照、记 WARN、累计连续失败次数。
4. **拉取与校验**：任一条不合法，该种类本次失败。不合法的情况包括：
   - 编码为空或格式非法；
   - 编码重复；
   - 状态或舍入方式无法识别；
   - 币种小数位不在 0..8；
   - MCC 翻页时总数变化（S-05：`size=200` 翻到最后一页，最后核对 `total` 等于实际条数）。
5. **HTTP**：使用 `HttpServiceClientFactory.create` 创建自己的客户端，不与宿主的 `platform-support-service` 组共用（宿主那边是严格 JSON）。宿主存在 `InternalTokenSupplier` 时附加 `platform-support-service` 的内部调用 Token，做法与 client-master 的 `InternalCallTokenOptions` 一致；不存在时行为与现在相同。
6. **事务公理**：HTTP 只在调度线程或 `refreshNow()` 中发生，不进入任何业务事务；读方法纯内存，可以在事务内调用。
7. **健康指标**：bean 名为 `referenceData`。
   - 有种类尚未首次成功 → `OUT_OF_SERVICE`；
   - 全部成功过 → `UP`（之后刷新失败不改变状态）。
   - 明细按种类给出：`count`、`loadedAt`、`consecutiveFailures`、`lastFailure`（只有异常类名，不带响应体）。

   消费方把它加入 readiness 组：`include: readinessState,referenceData`。
8. **`ReferenceDataRefresher.refreshNow()`**：同步刷新所有已开启的种类，返回每类的成功 / 失败结果。这是公开的生产方法，调度器也走它；测试在关闭调度后用它加载数据。它不是测试专用分支。

## 11. 测试支持

- **`MockReferenceDataApi`**：一个 `@RestController`，实现 5 个 `*QueryApi`（新路径），由消费方注册到自己的 `MockHttpServer`。它按 Mocker 中的数据返回 DTO：
  - 不在 Mocker 中的种类，记为意外调用；
  - MCC 按 `page` / `size` 真实分页；
  - 支持配置故障（返回 503 或非法数据），用于 AC-02 / AC-03。
- **`ReferenceDataMocker implements Clear`**：
  - `currency(builder)`、`country(...)` 等方法加入数据；
  - `defaults()` 装入一组最小默认数据：USD / AED / SAR / EGP、常用国家、AE / SA / EG 行政区、两级行业示例、若干 MCC；
  - `callCount(kind)`、`totalCalls()` 用于计数；
  - `clear()` 由全局 AfterEach 调用。
- **Builder**：`ReferenceDataBuilders.currency("USD").minorUnit(2).status(ACTIVE)` 等，用 `template()` 给出合理默认值。
- **消费方测试配置**：`scheduling-enabled: false`，`base-url` 指向自己的 Mock 服务；在 BeforeEach 中配好 Mocker 后调用 `refreshNow()`。
- **AC-05 的做法**：读方法调用前后 `totalCalls()` 不变。

## 12. 各系统改法

| 系统 | 改动 |
|---|---|
| platform-support | ① 新增两个模块，并在父 POM 和 BOM 中登记。② 客户端模块自己的 auto 测试覆盖 AC-01—AC-06：用 `MockHttpServer` 加测试支持模块，不借用 Platform 应用。③ 最后一步删除旧接口：`CurrencySpecQueryApi`、`CountryRegionQueryApi`、对应的 Controller、`ListCurrencySpecsProcessor`、`GetCurrencySpecProcessor`、`CountryRegion*` 处理器 / 请求 / 响应 / 转换，以及 `CurrencySpecQueryApiTest`、`CountryRegionQueryApiTest`。仍被币种维护使用的仓储方法和 `CurrencySpecStatus` 保留。`InternalAuthDualPassThroughTest` 的样本改为 `GET /platform/v1/reference-data/currencies` |
| ledger | ① 删除 `PlatformCurrencyClient(Impl)`、`CurrencySpecSnapshot`、`CurrencySnapshotRefreshScheduler`、`CurrencySnapshotHealthIndicator`、`CurrencySnapshotProperties`、`CurrencySnapshotConfiguration`、`PlatformSupportClientConfiguration`（只服务币种）。② `CurrencyRuleServiceImpl` 改读 `referenceData.currencies().find(code)`，查不到时抛 starter 的 `ReferenceDataNotFoundException`（系统异常，消息重投语义不变），再转换为 ledger 的 `CurrencyRule`；`requireUsable` / `requireActive` 判定保留在 ledger。③ `CurrencyRule` 保留（使用面广），状态类型改为 `ReferenceDataStatus` 或在转换时映射，阶段 4 按改动面选择小的一方。④ yml 中的 `ledger.currency-snapshot.*` 与 `spring.http.serviceclient.platform-support-service` 改为 starter 配置，`kinds: [CURRENCY]`；readiness 组 `currencySnapshot` 改为 `referenceData`。⑤ 测试：`MockCurrencySpecApi` / `CurrencySpecMocker` 改用测试支持模块 |
| billing | 同 ledger |
| card | ① 删除 `PlatformCountryRegionClient(Impl)`；`PlatformClientConfiguration` 只保留 `InternalFileApi`。② `CardBinLibraryImportHandler` 用 `referenceData.countries().list()` 构造 `国家 → 区域` 映射；保持现有行为，INACTIVE 国家是否纳入以现有旧接口的返回为准（阶段 4 核对后保持一致）。③ `kinds: [COUNTRY]`；readiness 组加入 `referenceData`。④ 测试：`MockCountryRegionController` 改用测试支持模块 |
| client-master | ① 删除 `ReferenceDataClient(Impl)`、`ReferenceDataItem`。② `ReferenceCodeCheck` 改用 `find(code)` + `status == ACTIVE`；行政区仍核对 `countryCode`；分类仍核对行业 `level == 2` 与 MCC 为 ACTIVE；错误码与 422 不变。③ 3 个 Configuration 去掉 5 个参考数据查询客户端；`kinds: [CURRENCY, COUNTRY, SUBDIVISION, INDUSTRY, MCC]`；readiness 组加入 `referenceData`。④ 测试：`ReferenceDataMocker`（本服务）改用测试支持模块 |
| operations-console-bff | `PlatformCurrencySpecClientImpl.listCurrencies(status)` 改调 `referenceApi.listCurrencies(status, null)`，投影成原有财务页响应（`currency`、`minorUnit`、`roundingMode`、`status`）；删除对 `CurrencySpecQueryApi` 的注册；相关测试改 Mock 新接口 |
| 文档 | 第 13 节 |

## 13. 文档修订（阶段 4 落地）

- **[参考数据决定](../../../agent-docs/decisions/2026-10-27-platform-reference-data.md)第 7 条**："抽为 Foundation 组件" 改为 "由 Platform 提供参考数据客户端（`paytech-platform-support-reference-data-client`）；读法 B 一律使用它：按配置加载、默认每 1 小时全量刷新、读方法只读内存、查不到即报错；不使用 Caffeine"。读法 A 保留给 BFF 转发与运营页面。
- **[2026-10-20 币种快照读取决定](../../../agent-docs/decisions/2026-10-20-currency-spec-platform-snapshot-read.md)**：注明"实现方式由参考数据客户端取代；刷新间隔由 60 秒改为默认 1 小时；取消查不到时提前刷新"。
- **design-discuss/reference-data 第 8.1 节**：注明已由本需求实施。
- **TECHNOLOGY_STACK / JAVA_PROJECT_STRUCTURE**：登记客户端模块与测试支持模块的角色和依赖规则。
- **`CURRENT_IMPLEMENTATION.md`**：在阶段 7 / 8 更新。
- **mor-test compose 注释**：ledger / billing 两处说明改为新行为；环境变量不变。

## 14. 研发与验证计划

| 步骤 | 内容 | 验证 | 对应 AC |
|---|---|---|---|
| 1 | arch-rules 角色识别 + 两个新模块 | 客户端模块 auto 测试（加载、未就绪、短周期重试、刷新失败保留、MCC 翻页、读方法零 HTTP、状态读法、配置校验）；arch-rules 测试 | AC-01—AC-06 |
| 2 | ledger、billing 迁移 | 两个服务的全部 auto 测试（本地 `local-environment`，逐个服务串行执行） | AC-07 |
| 3 | card、client-master 迁移 | card auto；client-master 上一需求的 152 个相关测试加全量 auto | AC-08、AC-09 |
| 4 | ops-bff 财务页下拉 | ops-bff auto（财务页币种用例） | AC-10 前置 |
| 5 | Platform 删除旧接口 | Platform auto、arch-rules、整个 reactor `test-compile`、全仓库 grep 无调用方 | AC-10 |
| 6 | 本机联调（阶段 5） | 替换本机 e2e 中的 6 个 JAR；健康检查；过账、计费、BIN 导入、入驻提交 / 分类；Platform 停机后启动消费方观察 readiness 与 10 秒重试 | AC-02、AC-11 预演 |
| 7 | mor-test（阶段 6—7） | 发布顺序见第 5 节 | AC-11 |

- 研发在独立 worktree（基于当时的 main）中进行，使用私有 Maven 仓库，避免与其他会话冲突。
- 本需求没有写接口，因此 WRITE_API_EXAMPLE 第 11 节的核对项**不适用**（只有删除只读接口和新增客户端）；阶段 4 交付时逐项说明。
