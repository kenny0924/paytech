# platform-support：参考数据客户端组件 · 需求

> 状态：**阶段 1 已确认（2026-10-27）**：Q1 统一默认 1 小时；Q2—Q4 按建议；内存缓存可以使用 Caffeine。来源：用户在[参考数据目录需求](../platform-support-参考数据目录与client-master迁移/delivery.md)交付后提出"refdata 做成公共 JAR"，并确认设计方向（客户端 starter，不把数据打进 JAR；定时轮询周期可配置，默认 1 小时；所有读方法只读内存，查不到就报错）。确认记录见 [delivery.md](./delivery.md)；技术方案从阶段 2 起写入 [technique.md](./technique.md)。

## 1. 背景

- [2026-10-27 参考数据决定](../../../agent-docs/decisions/2026-10-27-platform-reference-data.md)第 7 条规定了三种读法：A 直接调接口；B 进程内快照；C 写入时冻结编码。第 7 条还约定：出现第三个 B 读法消费方，或出现第二种需要 B 读法的参考数据时，要把快照实现抽成公共组件。
- 现状是同一套逻辑写了 4 份：

| 消费方 | 读什么 | 现有实现 |
|---|---|---|
| ledger | 币种 | `PlatformCurrencyClientImpl`、`CurrencySpecSnapshot`、`CurrencySnapshotRefreshScheduler`、`CurrencySnapshotHealthIndicator`、`CurrencyRule(ServiceImpl)`；刷新周期 `PT60S`；查不到时会请求提前刷新 |
| billing | 币种 | 同样 6 个类，结构相同，细节已经改得不一样 |
| card | 国家 | `PlatformCountryRegionClient(Impl)`，导入 BIN 库时直接调用 |
| client-master | 国家、币种、行政区、行业、MCC | `ReferenceDataClient(Impl)` 和 `ReferenceCodeCheck`，提交 / 审核时在事务外直接调用 |

- ledger、billing、card 仍在调用已冻结的旧接口 `/currency-specs`、`/country-regions`（参考数据需求 S-01：因为 Foundation 严格 JSON，这两个旧接口不能追加字段）。参考数据需求把"迁到新接口"记为遗留项。
- 以后 commercial、acquiring、payment 等系统也会需要币种精度、国家、MCC。如果不收拢，这套实现还会继续被复制。

## 2. 目标

1. Platform 提供一个参考数据客户端 starter。消费方引入后，按配置把自己需要的参考数据加载到进程内存，并定时刷新。
2. 所有读方法只读内存，不发 HTTP。查不到就报错，不回退到实时调用，也不返回默认值。因此这些读方法在事务内也可以安全调用。
3. ledger、billing、card、client-master 改用 starter，删掉各自复制的客户端、快照、刷新和健康检查代码。
4. 消费方迁走后，下线已冻结的旧查询接口 `/currency-specs`、`/country-regions`。

## 3. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| 后端服务的研发 | 新服务要读币种 / 国家 / 行政区 / 行业 / MCC | 引入 starter、配置要加载的种类，即可注入查询接口；不用再写客户端、缓存、调度或健康检查 |
| ledger / billing | 过账、计费时在事务内读币种精度 | 行为不变：快照没加载好时服务不就绪，查不到币种时拒绝并报明确错误 |
| card | 导入 BIN 库时核对国家编码 | 从内存读，不再每次导入都调一遍 Platform |
| client-master | 提交 / 审核时核对国家、币种、行政区、行业、MCC | 校验结果不变，改为读内存 |
| 运营 | 在 Platform 改了参考数据（例如启用新币种） | 最迟一个轮询周期后（默认 1 小时）各服务生效 |
| 运维 | 服务启动或 Platform 不可用 | 首次加载成功前 readiness 为 DOWN；已加载后 Platform 不可用时保留旧快照继续服务，健康信息能看到最近一次成功的时间和失败次数 |

## 4. 本期范围

| # | 范围 |
|---|---|
| R1 | Platform 新增 Maven 模块 `paytech-platform-support-reference-data-starter`（命名阶段 3 定稿），依赖现有 `paytech-platform-support-api` 的 `@HttpExchange` 接口与 DTO |
| R2 | 加载种类可配置：币种、国家、行政区、行业、MCC 五类，消费方只开自己需要的；只读新接口 `/platform/v1/reference-data/*` |
| R3 | 定时全量轮询：周期可配置，**默认 1 小时**（`PT1H`）；启动时加载一次；刷新失败保留旧快照，成功后整体原子替换 |
| R4 | 读方法**只读内存**：按编码查一条、要求存在（并可要求 ACTIVE）、列出全部。查不到时抛明确异常；任何读方法都不触发 HTTP，也不触发提前刷新 |
| R5 | 就绪与观测：首次全部种类加载成功前，readiness 为 DOWN；健康信息给出每类的条数、最近一次成功时间和连续失败次数 |
| R6 | 测试支持：按 UNIT_TEST_GUIDE 提供消费方 auto 测试使用的测试 HTTP 端点 / DTO Mocker 和清理，消费方不再各自搭建 |
| R7 | ledger、billing 迁移：删除各自的币种客户端、快照、刷新调度和健康检查，`CurrencyRule` 的读取改为调 starter；原来的"查不到就请求提前刷新"按 R4 取消 |
| R8 | card 迁移：删除 `PlatformCountryRegionClient(Impl)`，BIN 导入时的国家核对改为调 starter |
| R9 | client-master 迁移：删除 `ReferenceDataClient(Impl)`，`ReferenceCodeCheck` 与提交 / 审核 / 分类校验改为调 starter；校验仍在事务外完成，规则不变 |
| R10 | 下线旧接口：确认没有服务再调用后，删除 Platform 的 `/currency-specs`、`/country-regions` 查询接口、对应的 `-api` 接口与内部路由注册（下线前要核对 BFF、e2e 脚本与 flink 管道） |
| R11 | 文档：修订参考数据决定第 7 条和 design-discuss 第 8.1 节（组件归属、读法 B 规则）；TECHNOLOGY_STACK / JAVA_PROJECT_STRUCTURE 登记 starter；`CURRENT_IMPLEMENTATION.md` |

## 5. 不做项

- 不把数据打进 JAR（不放静态 ISO 清单或资源文件）。数据只归 Platform。
- starter 不带数据库表，不提供显示名和多语言（这部分归前端），也不承载业务规则。例如"某 Client 能用哪些币种""某行业能否准入"仍归各自的 Owner。
- 不改 Money 类型，不改 ledger、billing 的舍入与过账规则。
- 不做按需刷新、查不到时回源、Redis 共享缓存。
- 不做 Platform 变更事件（`ReferenceDataChanged`）推送刷新；需要时另立。
- 两个 BFF 不使用 starter：它们给前端提供选项列表，继续直接转发 Platform 接口。
- 不改 `/platform/v1/reference-data/*` 的现有响应结构（如需调整，在阶段 3 单独提出）。
- 不改 Platform 的参考数据表和初始数据。

## 6. 业务规则（草案）

| # | 规则 |
|---|---|
| B1 | 读方法只读内存。快照里没有该编码就抛异常，异常信息带种类和编码；不返回 null 或默认值 |
| B2 | 快照同时保存 ACTIVE 和 INACTIVE 两种状态。按决定，"停用的编码不拦截既有数据的读取与后续处理，只拦新用途"，所以读接口要区分"存在即可"和"必须 ACTIVE" |
| B3 | 首次加载完成前，读方法统一抛"快照未就绪"异常，readiness 为 DOWN（fail closed） |
| B4 | 定时刷新是全量拉取、整体替换；任一种类拉取失败时，该种类保留旧快照并记失败，不出现半新半旧的单类数据 |
| B5 | 轮询周期由配置决定，默认 `PT1H`；各服务可以单独覆盖。运营改动参考数据后，最长一个周期后生效 |
| B6 | 刷新只在调度线程上发 HTTP，与业务事务无关，满足事务公理 |
| B7 | 消费方只存编码字符串，不建 Java 枚举（沿用决定第 7 条） |

## 7. 逐系统变动点

| 系统 | 变动点 |
|---|---|
| platform-support | 新增 starter 模块（自动配置、配置属性、快照、刷新调度、健康指标、查询接口、异常、测试支持）；父 POM / BOM 登记；R10 下线旧接口时删除 `CurrencySpecQueryApi`、`CountryRegionQueryApi`、对应 Controller / Processor / 测试及路由注册（是否需要 Platform 注册 DML 下线块，阶段 2 核对） |
| ledger | 删除币种客户端 / 快照 / 调度 / 健康检查 / 配置属性约 6—8 个类；`CurrencyRule(ServiceImpl)` 改为调 starter；`application.yml` 的 `ledger.currency-snapshot.*` 改为 starter 配置；测试改用 starter 的测试支持 |
| billing | 同 ledger |
| card | 删除 `PlatformCountryRegionClient(Impl)` 与 `PlatformClientConfiguration` 中的相关部分；`CardBinLibraryImportHandler` 改为调 starter；配置与测试 |
| client-master | 删除 `ReferenceDataClient(Impl)`、`ReferenceDataItem`；`ReferenceCodeCheck`、`ClientSubmissionPreparer`、`ReplaceClientClassificationProcessor`、`OnboardingRequirementEvaluator` 改为调 starter；三个 Configuration；测试 |
| operations-console-bff | 财务页币种下拉改调 `ReferenceCurrencyQueryApi`（阶段 2 F-08；仍是转发，不用 starter） |
| client-console-bff | 不改代码（继续转发） |
| Gateway / 注册数据 | 旧接口如果只是内部调用、未注册公开路由，就不涉及；阶段 2 核对 |
| 文档 / 工具 | 决定第 7 条与 design-discuss 第 8.1 节修订、TECHNOLOGY_STACK、JAVA_PROJECT_STRUCTURE、arch-rules（如有依赖规则）、`CURRENT_IMPLEMENTATION.md`、e2e / flink 脚本中对旧接口的引用 |

## 8. 验收标准

| # | 验收 |
|---|---|
| AC-01 | starter 按配置只加载指定种类；未开启的种类调用读方法时报"未配置"错误 |
| AC-02 | 启动时 Platform 不可用：readiness DOWN，读方法抛"未就绪"；Platform 恢复后下一次轮询成功，readiness 变为 UP |
| AC-03 | 已就绪后 Platform 不可用：保留旧快照继续服务，健康信息显示失败次数和最近成功时间 |
| AC-04 | 轮询周期可配置，默认 1 小时；在 Platform 改数据后，下一次轮询后读到新值（测试中用短周期验证） |
| AC-05 | 所有读方法不发 HTTP（测试端点在读方法调用期间记录到 0 次请求）；查不到时抛带种类和编码的异常 |
| AC-06 | "存在即可"与"必须 ACTIVE"两种读法对 INACTIVE 编码的结果分别正确 |
| AC-07 | ledger、billing 迁移后，过账 / 计费的币种精度行为与迁移前一致（现有测试全部通过），复制的类已删除 |
| AC-08 | card 的 BIN 导入国家核对行为不变，相关测试通过 |
| AC-09 | client-master 的提交 / 审核 / 分类校验结果不变（参考数据需求中的 152 个相关测试通过） |
| AC-10 | 旧接口 `/currency-specs`、`/country-regions` 下线后，全仓库没有调用方，Platform 测试与 arch-rules 通过 |
| AC-11 | mor-test 部署后，ledger、billing、card、client-master 健康检查 UP，代表性业务用例（过账、计费、BIN 导入或核对、入驻提交）通过 |

本需求不涉及页面，没有视觉 / 交互验收。

## 9. 待确认问题

| # | 问题 | 建议 |
|---|---|---|
| Q1 | ledger、billing 现在的刷新周期是 `PT60S`，查不到时还会请求提前刷新。改用 starter 后，是统一用默认 1 小时，还是在它们的配置里保留 60 秒？ | **统一用默认 1 小时**。币种变动很少，启用新币种本来就要先规划。代价是：启用新币种后，最长约 1 小时才能用于过账 |
| Q2 | 组件放在哪里：放 Platform（`platform-support` 下的 starter，Owner 是 Platform），还是按决定原文放 Foundation 通用组件？ | **放 Platform**。它直接绑定 Platform 的接口和 DTO，Foundation 不应依赖业务 `-api`。决定第 7 条随本需求修订 |
| Q3 | 旧接口下线是否放在本需求里做？ | **放进来**，作为最后一步。前提是阶段 2 核对确认没有其他调用方；如有遗漏的调用方，就推迟下线，不阻塞前面的迁移 |
| Q4 | Platform 以后给 `/reference-data/*` 追加字段时，消费方的严格 JSON 会失败（S-01 的同一问题）。starter 的客户端是否容忍未知字段？ | **容忍**：starter 只读自己定义的字段，忽略未知字段，Platform 追加字段时不用所有消费方同步发布。是否与 Foundation 严格 JSON 规则冲突，阶段 3 给出具体做法 |

## 10. 阶段安排

默认八阶段：

1. 需求（本稿）
2. 现状差异与影响：核对 4 个消费方、旧接口调用方、测试基建
3. 方案与接口：starter 接口、配置、异常、测试支持、迁移顺序
4. 研发
5. 本机验证
6. mor-test 发布准备
7. mor-test 部署与验收
8. 提交

本需求不涉及业务库 DDL。如果旧接口有路由注册，阶段 3 给出下线 DML。SQL、mor-test 部署、提交、推送分别授权。
