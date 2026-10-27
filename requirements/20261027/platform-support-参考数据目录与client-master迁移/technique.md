# platform-support：参考数据目录与 client-master 迁移 · 技术方案

> 状态：**阶段 2 现状差异与影响分析（2026-10-27，待确认）**。阶段 3 再写接口、数据与页面设计。核对方式：五路只读核对（Platform 参考数据模块、client-master、Client BFF / Web、Operations BFF / Web、消费方兼容性）加主会话复核，均基于 main `aba76a1a`，未运行测试。

## 1. 关键发现

| # | 发现 | 证据 | 影响 |
|---|---|---|---|
| F-01 | **现有接口不能追加字段。** Foundation 的严格 JSON 模式（`FAIL_ON_UNKNOWN_PROPERTIES` 等）在 ledger、billing、card 调 Platform 时都开着；ledger 的响应上限只有 8192 字节 | `foundation/.../HttpServiceClientFactory.java:107-127`；`ledger/.../PlatformSupportClientConfiguration.java:27`（8192）；`billing/.../PlatformClientConfiguration.java:27`、`card/.../PlatformClientConfiguration.java:43`（65536） | 推翻需求 R3、7.5 中"旧接口只追加字段"的说法。Platform 一旦先发版，三个消费方都会解析失败 → S-01 |
| F-02 | ops-bff 调 Platform 用的是宽松模式，会忽略未知字段 | `operations-console-bff/.../PlatformClientConfiguration.java:114`（`migrationWithLegacyErrorResponse`） | 运营端可以直接用新接口 |
| F-03 | 币种维护的实际流程是新建 → 修改 → 改状态，状态值有 DRAFT / ACTIVE / INACTIVE，没有单独的"草稿编辑"接口。管理接口只有 ops-bff 一个调用方 | `CurrencySpecManagementController`、`Create/Update/ChangeCurrencySpecStatusRequestBody`；权限 `CURRENCY_SPEC_MANAGE` | 新字段加在新建和修改的请求体上即可 → S-02 |
| F-04 | client-master 的目录校验只在**提交时**做：用 `OnboardingPack.catalogContains` 查内存中的 `t_catalog_item`。保存草稿时只校验国家 `[A-Z]{2}`、币种 `[A-Z]{3}` 的格式 | `OnboardingPackServiceImpl:72,111`；`OnboardingRequirementEvaluator.valueField:113-125`、`address:162-182`；`ClientDraftPatch:264-266,310-311` | 参考数据校验放在提交前的准备步骤（事务外）调 Platform；保存草稿继续只校验格式 → S-03 |
| F-05 | 入驻表单的选项由 client-master 拼进 form-schema（`catalogs`，每项只有编码和文案键）；BFF 原样透传 | `FormSchemaConverter.catalogs():126-139`；client-bff `ClientOnboardingController:78-96` | 参考数据选项改由 BFF 从 Platform 取来补进去，符合"页面组装归 BFF"的约定 → S-04 |
| F-06 | 国家不在任何目录里：Client Web 内置了 249 个编码（`widgets.tsx:22-24`），后端只校验格式；涉及的字段有地址国家、证件签发国、国籍、居住国、出生国、注册国，以及 COUNTRY 类型的扩展属性 | `ClientDraftPatch:54-81`；`ClientDraftPreparer:111` | 国家选项和存在性校验都改为来自 Platform → S-05 |
| F-07 | 省州编码是**下划线格式**（`AE_DU`、`EG_C`），ISO 3166-2 是连字符（`AE-DU`）。地址列 `t_party_address.state_or_province` 中已有存量值（mor-test 浏览器验收时提交过） | `client-master-initial-data.sql` 中 STATE_OR_PROVINCE 共 34 行（AE 7、EG 27）；schema 第 108 行 | 新编码统一用 ISO 格式，存量值需要转换 → S-06 |
| F-08 | 币种只在 `business.currency`（8 版 Schema，SELECT + CURRENCY）中采集，选项是 USD / AED / SAR / EGP；Platform 初始数据里只有 USD 是 ACTIVE | `client-master-initial-data.sql:340,381,426,457`；`platform-support-initial-data.sql:2276` | 需要在 Platform 激活 AED / SAR / EGP（阶段 1 Q6 已定） |
| F-09 | 行业：10 个一级编码（RETAIL、DIGITAL_GOODS、SOFTWARE_SAAS、PROFESSIONAL_SERVICES、EDUCATION、TRAVEL、FOOD_BEVERAGE、MEDIA_ENTERTAINMENT、NON_PROFIT、OTHER），Client 表单不采集，只有 O03 分类使用 | `ClientClassificationAdminController#options:50-52`；`ReplaceClientClassificationProcessor:96` | 行业只影响运营端 |
| F-10 | 分类保存：`validate()` 在加锁前执行，事务只在 `ClientClassificationServiceImpl.replace/confirm` 内；MCC 只在请求 DTO 上校验四位数字；没有"建议 MCC"。ops-web 的行业是平铺下拉，MCC 是 4 位文本框 | `ReplaceClientClassificationProcessor:66-96`；ops-bff `ClientReviewModels:26`；ops-web `ReviewRisk.tsx:94,114-115` | Platform 校验放在 `validate()` 中（事务外）；行业选项改由 ops-bff 直接取 Platform → S-07 |
| F-11 | client-master 已依赖 `paytech-platform-support-api`，并以严格模式（8192 字节）调用 Platform；测试用进程内 Mock 控制器（`MockPlatformAuthenticationApi:40` 统一登记 `/platform-support` 前缀） | `ClientOnboardingRecoveryConfiguration:61-87`；`test/mock/external/onboarding/` | 国家列表（249 行，含新字段）可能超过 8192 字节 → S-08 |
| F-12 | Client BFF 已有 Platform 客户端（文件、多语言、认证），但没有参考数据客户端；前端没有显示国旗或 logo 的逻辑，也没有文件地址转链接的工具 | client-bff `PlatformClientConfiguration:36-114`；ops-web 只有 `fileUpload.ts:219-224` 的上传结果地址 | 需要新增客户端、国旗显示和文件地址解析 |
| F-13 | 多语言命名空间属于某个应用，编码规则 `[a-z][a-z0-9-]{0,63}`；目前没有 `refdata`；Client 控制台和运营控制台各需建一个 | `LocalizationCatalogApi:150`；`CreateNamespaceRequestBody:23` | 两个应用各建一个 `refdata` 命名空间 → S-09 |
| F-14 | 注册块目前最大到 138；币种页的注册写法见块 125（权限、角色授权、菜单、按钮、路由，默认都不启用） | `platform-support-initial-data.sql:5544-5579`；块 138 生成脚本 | 新页面候选用块 139，发布前核对 mor-test 线上库是否已占用 |
| F-15 | 运营端"入驻配置"页会列出 Schema 引用的目录；目录不能在线编辑 | `OnboardingConfigAdminController:53`；`onboardingConfig.ts:162` | 改为引用参考数据后，这里要显示"来自 Platform 参考数据"，而不是空列表 |
| F-16 | 旧表名在其他地方的引用：arch-rules 的 `table-ownership.yaml:285-286`、生成脚本中 `t_currency_spec` 的特殊处理（第 206 行）、`TableOwnershipAutoTest:35-47,65-69`（表名与表数断言）；card 的列注释；flink 演示管道 `demo-currency-spec.yaml`（指向已删除的 `mor_finance.t_currency_spec`，本身已失效）；ledger、billing 的历史变更 SQL | 消费方核对 | 历史 SQL 不改；flink 管道单列为遗留项 |
| F-17 | 测试受影响（client-master）：`OnboardingAdminQueryTest:59`（10 个行业）、`OnboardingFormSchemaTest:60-62`、`SubmitClientApplicationTest:255`、`ClientClassificationAdminTest:27-57`（RETAIL/5411 等）、`AbstractClientReviewTest`；`JurisdictionPackMocker` 加载初始数据；`zz-pack.sql` 夹具也引用 CURRENCY。其他服务：ops-bff `CurrencySpecBrowserAutoTest` + `currency-spec-boundary.json`、client-web `countrypack.test.ts`（解析初始数据 SQL）、两端的 i18n 守卫测试 | 各路核对 | 阶段 4 一并修改 |

## 2. 需求项差异

| 需求项 | 现状 | 差异 |
|---|---|---|
| R1 改名 + 补列 | 两张表、68 个相关文件 | 全部改名；**旧接口的响应保持不变**（S-01） |
| R2 新建三张表 | 没有 | 新建；初始数据：ISO 行政区（AE / SA / EG）、行业两级草案、MCC 清单 |
| R3 查询接口 | 只有 `/platform/v1/currency-specs`、`/platform/v1/country-regions` | 新增 `/platform/v1/reference-data/*` 5 个接口，返回完整字段；旧接口冻结 |
| R4 币种维护 + `refdata` | 新建 / 修改只有 4 个字段；没有 `refdata` | 新建 / 修改的请求体加字段（S-02）；两个应用各建 `refdata`（S-09） |
| R5 client-master 迁出 | 48 行在 `t_catalog_item`；Schema 与地址组件引用 CURRENCY、STATE_OR_PROVINCE | 删除这 48 行；`catalog_code` 改为参考数据引用；提交时向 Platform 校验（S-03、S-04） |
| R6 分类 | 平铺一级行业；MCC 只校验格式 | 行业两级 + MCC 存在性校验；选项由 ops-bff 取（S-07） |
| R7 守卫 | 无 | auto 测试：`t_catalog_item` 不得出现保留的参考数据目录码 |
| R8 Client 端 | 内置国家表；目录文案用 i18n 键 | BFF 补全参考数据选项；国家与国旗来自 Platform；币种名称用 Intl |
| R9 运营端 | 平铺下拉 + 文本框；币种页 4 个字段；没有参考数据页 | 两级选择 + MCC 搜索；币种页加字段；新页面（块 139） |
| R10 文档与工具 | 多处引用旧表名 | arch-rules 生成器、断言与表数；card 注释；数据库文档 |

## 3. 逐系统影响（修订阶段 1 第 7 节）

| 系统 | 新增 / 修改 | 发布依赖 |
|---|---|---|
| platform-support | 表改名与补列、3 张新表、5 个新查询接口、币种新建 / 修改加字段、旧接口改读新表名但响应不变；PDManer、schema、初始数据、变更脚本（含回退）、测试 DDL、`ReferenceDataTestMapper`、`CurrencySpecMocker`、3 个现有 API 测试 | 最先发布；SQL 与镜像在同一窗口切换 |
| client-master | 删除 48 行目录；8 版 Schema 的 `business.currency` 与 2 个地址组件的 `catalog_code` 改为参考数据引用；存量省州值转换（S-06）；新增参考数据客户端与校验（提交准备、扩展属性、分类 `validate()`）；`/classification-options` 停用（S-07）；新 Mock 控制器；约 6 个测试改写 | 在 Platform 之后；DML 必须与新镜像同时上线（旧镜像会把参考数据引用当成普通目录，导致选项为空） |
| client-console-bff | 新增参考数据客户端；form-schema 返回前补全参考数据选项和国家列表；测试 | 在 client-master 之后 |
| client-console-web | 选项类型加 `name`、logo；国家改用 BFF 返回的列表并显示国旗；币种名称用 Intl；省州名称用 `refdata`；删除 `onboarding-schema` 中三类目录的键；改测试 | 与 client-bff 一起 |
| operations-console-bff | 分类选项改为直接取 Platform 的行业树与 MCC 搜索；币种接口加字段（更新 `currency-spec-boundary.json`）；参考数据只读接口；`application.yml` 路由 | 与 ops-web 一起，在 Platform 之后 |
| operations-console-web | 分类改两级选择 + MCC 搜索；币种页加字段与 logo；新"参考数据"页（`pageCapabilities.ts`、`ConsolePage.tsx`、新页面与模型、`nav.json`）；入驻配置页标出参考数据来源 | 同上 |
| Platform 注册（数据） | 块 139：参考数据查看的权限、菜单、路由；两个应用的 `refdata` 命名空间、导入、翻译、发布 | 镜像上线后激活 |
| arch-rules | 生成器（含第 206 行特殊处理）、`table-ownership.yaml`、断言与 Platform / 总表数 | 随 Platform 提交 |
| ledger、billing、card | **不改代码**（S-01 保证旧接口不变）；card 只同步注释 | 回归验证 |

## 4. 待决项（阶段 2）

| # | 问题 | 建议 |
|---|---|---|
| S-01 | 现有接口能否追加字段（推翻阶段 1 的"只追加字段"） | **不追加**。`/platform/v1/currency-specs`、`/platform/v1/country-regions` 的响应结构冻结，只改为读新表名；新字段只出现在 `/platform/v1/reference-data/*`。ledger、billing、card 以后迁到新接口时，各自在自己的需求里调整严格模式的上限 |
| S-02 | 币种新字段在哪些状态下可以修改 | `numeric_code`、`name`、`symbol` 随标准属性一起，只有 DRAFT 可改，启用后冻结；`logo_file_address` 是展示资源，任何状态都可以改（写审计） |
| S-03 | 参考数据什么时候校验 | 与现有目录一致：保存草稿只校验格式，提交时在事务外调 Platform 校验存在且 ACTIVE；分类保存在 `validate()` 中校验。Platform 不可用时返回明确错误，不写入 |
| S-04 | client-master 的 Schema 怎样引用参考数据 | 用保留的目录码 `REF_CURRENCY`、`REF_COUNTRY_SUBDIVISION`（以 `REF_` 开头），由代码识别为"来自 Platform"，不从 `t_catalog_item` 加载；BFF 在返回 form-schema 时按这些码从 Platform 取选项补进去；守卫测试禁止 `t_catalog_item` 出现 `REF_` 开头的码 |
| S-05 | 国家选项与校验 | BFF 在 form-schema 中附带 Platform 的 ACTIVE 国家列表（编码、英文名、国旗地址），前端不再内置 249 个编码；client-master 在提交时校验所有国家字段（含 COUNTRY 类型的扩展属性） |
| S-06 | 存量省州值（下划线格式）怎么处理 | 发布时用 DML 把 `t_party_address.state_or_province` 中 `AE_*`、`EG_*` 格式的目录码转换为 ISO 连字符格式（存量都是测试数据，延续切片 1 的"存量按重写"）；在执行日志中记录转换前后的行数 |
| S-07 | 分类的行业选项由谁提供 | ops-bff 直接取 Platform 的行业树（含建议 MCC）和 MCC 搜索；client-master 的 `/classification-options` 停用并删除（唯一调用方是 ops-bff）；client-master 只负责保存时校验 |
| S-08 | client-master 调 Platform 的响应上限（8192）可能放不下国家列表 | client-master 只按需查询要校验的编码（新接口支持 `codes=` 批量过滤，返回很小），不拉全量；BFF 取全量列表时用较大的上限（65536），在阶段 3 计算实际大小 |
| S-09 | `refdata` 的翻译范围 | 两个应用各建 `refdata` 命名空间；行业与行政区全部提供中文；~~MCC 只翻译被细类引用的~~ → **MCC 全部翻译**（2026-10-27 阶段 5 修订：Platform 发布语言包要求中文覆盖率 100%，用户选择全部翻译） |
| S-10 | 失效的 flink 演示管道 `demo-currency-spec.yaml` | 本需求不改，记为遗留项（数据查询决定已退役 CDC 白名单，另行清理） |

## 5. 既有问题与风险

- **发布顺序**：Platform（SQL + 镜像同一窗口）→ client-master（DML + 镜像同时）→ 两个 BFF 与两个 Web → 语言包与注册块。改名后旧 Platform 镜像无法工作，回退必须同时执行反向 RENAME 和换回旧镜像（阶段 6 写入运行卡）。
- **初始数据**：行业细类与建议 MCC 是草案，需要合规确认（阶段 1 Q1）；MCC 全量清单要先确认许可。
- **国旗资源**：MIT 开源 SVG 共约 250 个文件，需要上传到 mor-test 文件服务的公开桶，再把地址回填进 DML。上传是对环境的写操作，在阶段 6 列入发布清单。
- **既有测试失败**：requirements-site 已有 10 个失败（与 HEAD 相同）；client-master 与 compliance 的既有失败在阶段 5 先在基线上实跑记录。

---

# 阶段 3：方案、接口与数据设计（2026-10-27，评审稿）

> 依据：阶段 1 需求（R1—R10、B1—B8、AC-01—AC-14）、阶段 2 确认的 S-01—S-10；接口开发前已读 [`DEVELOPMENT_CONVENTIONS.md`](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md) 第 2—5 节、[`JAVA_PROJECT_STRUCTURE.md`](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md) 第 4 节、[写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md) 第 11 节。本节新增的设计决定编号 D-01 起，需要确认的列在第 13 节。

## 6. 数据设计（`mor_platform`）

字段级 DDL 草案见 [db/platform-support/ddl.sql](./db/platform-support/ddl.sql)（评审稿，未执行）。

### 6.1 表与列

| 表 | 处理 | 列（新增或改名加粗） | 索引 |
|---|---|---|---|
| `t_ref_currency` | `t_currency_spec` 改名 | id、**currency_code**（原 currency）、**numeric_code** CHAR(3)、**name** VARCHAR(128)、**symbol** VARCHAR(8)、minor_unit、rounding_mode、**logo_file_address** VARCHAR(64) NULL、status、version、create/update_time | `uk_ref_currency_code` |
| `t_ref_country` | `t_country_region` 改名 | id、country_code、**alpha3_code** CHAR(3)、**numeric_code** CHAR(3)、**name**、region_code、**calling_code** VARCHAR(8) NULL、**default_currency_code** CHAR(3) NULL、**logo_file_address** NULL、**status**、**version**、create/update_time | `uk_ref_country_code` |
| `t_ref_country_subdivision` | 新建 | id、subdivision_code VARCHAR(6)、country_code、parent_subdivision_code NULL、category VARCHAR(32)、name、local_name NULL、status、version、时间 | `uk_ref_country_subdivision_code`、`idx_ref_subdivision_country(country_code,status)` |
| `t_ref_industry` | 新建 | id、industry_code VARCHAR(64)、parent_industry_code NULL、**industry_level** TINYINT、name、description VARCHAR(512) NULL、default_mcc_code CHAR(4) NULL、logo_file_address NULL、sort_no INT、status、version、时间 | `uk_ref_industry_code`、`idx_ref_industry_parent(parent_industry_code,sort_no)` |
| `t_ref_mcc` | 新建 | id、mcc_code CHAR(4)、name、description VARCHAR(512)、network_high_risk TINYINT(1)、status、version、时间 | `uk_ref_mcc_code` |

- **D-01 `level` 改名为 `industry_level`**：`LEVEL` 是 MySQL 关键字，数据库规范第 8 节禁止使用保留字。决定中的 `level` 在物理列上写作 `industry_level`，接口字段仍叫 `level`。
- **D-02 索引名不超过 32 个字符**：行政区按国家查询的索引用 `idx_ref_subdivision_country`。
- 编码列一律 `ascii_bin`；不建外键与 CHECK（数据库规范 5.1），细类的 `default_mcc_code` 和国家的 `default_currency_code` 由初始数据脚本和 auto 测试保证引用存在。
- 初始数据 ID 段：币种沿用 1001 起，国家沿用 2001—2249，行政区 3001 起，行业 4001 起，MCC 5001 起，都低于 Snowflake 范围。

### 6.2 初始数据来源（阶段 4 生成 DML，评审后执行）

| 数据 | 来源 | 规模 |
|---|---|---|
| 国家三位码、数字码、英文名 | Unicode CLDR `supplementalData`（`codeMappings`）与 ISO 3166-1 英文短名 | 249 行回填 |
| 国际区号 | CLDR `telephoneCodeData`（与 libphonenumber 一致） | 249 行，南极等无区号的为空 |
| 法定货币 | CLDR `currencyData` 中当前有效（无 `to` 日期、`tender` 不为 false）的第一个币种 | 无法定货币的为空 |
| 币种数字码、英文名、符号 | JDK `Currency`（数字码、英文名）+ CLDR 英文区域的标准符号；没有专用符号时使用币种代码 | 现有币种回填；新增 AED / SAR / EGP 为 ACTIVE |
| 行政区 | ISO 3166-2：AE 7 个酋长国、SA 13 个地区（`SA-01`—`SA-12`、`SA-14`）、EG 27 个省；`local_name` 取 ISO 公布的阿拉伯文名 | 47 行 |
| 行业 | 第 7 节草案 | 10 个大类 + 约 30 个细类 |
| MCC | ISO 18245，并与 Visa《Merchant Data Standards Manual》、Mastercard《Quick Reference Booklet》公开清单核对；`network_high_risk` 按两家公布的高风险类目标记 | 约 600 行；生产前合规确认许可 |
| 国旗 | `lipis/flag-icons`（MIT 许可）4x3 SVG，上传到文件服务公开桶后回填 `logo_file_address` | 约 250 个文件；阶段 6 列入发布清单 |

生成脚本放在 `db/platform-support/generator/`，只在开发机运行，输出评审用的 DML，不在应用启动时执行。

### 6.3 client-master（`mor_client_master`）DML

| 变动 | 说明 |
|---|---|
| 删除 48 行 `t_catalog_item` | `catalog_code IN ('INDUSTRY','CURRENCY','STATE_OR_PROVINCE')` |
| 8 版 Schema 的 `business.currency` | `catalog_code` 从 `CURRENCY` 改为 `REF_CURRENCY` |
| 2 个地址组件 `stateOrProvince`（`AE_POBOX`、`EG_STANDARD`，以及其他引用 `STATE_OR_PROVINCE` 的组件） | `catalog_code` 改为 `REF_COUNTRY_SUBDIVISION` |
| 存量地址省州值（S-06） | `t_party_address.state_or_province` 中匹配 `^(AE|EG)_[A-Z0-9]+$` 的值，把 `_` 换成 `-`；执行前后记录行数 |
| 初始数据文件、测试夹具 `zz-pack.sql` | 同步修改 |

不改表结构（`catalog_code` 列宽为 VARCHAR(64)，可以容纳 `REF_` 前缀）。

## 7. 行业两级草案（阶段 1 Q1：test 先用，生产前合规确认）

| 大类（现有编码） | 细类编码 | 英文名 | 建议 MCC |
|---|---|---|---|
| RETAIL | RETAIL_APPAREL | Apparel & accessories | 5651 |
| | RETAIL_ELECTRONICS | Electronics | 5732 |
| | RETAIL_HOME | Home & furniture | 5712 |
| | RETAIL_BEAUTY | Beauty & cosmetics | 5977 |
| | RETAIL_GENERAL | General merchandise | 5399 |
| DIGITAL_GOODS | DIGITAL_GAMES | Games & in-game items | 5816 |
| | DIGITAL_MEDIA | Books, music & video | 5815 |
| | DIGITAL_OTHER | Other digital goods | 5818 |
| SOFTWARE_SAAS | SAAS_SUBSCRIPTION | SaaS subscriptions | 5734 |
| | SOFTWARE_APPS | Apps & software downloads | 5817 |
| | IT_SERVICES | IT & development services | 7372 |
| PROFESSIONAL_SERVICES | PRO_CONSULTING | Consulting | 8999 |
| | PRO_MARKETING | Marketing & advertising | 7311 |
| | PRO_ACCOUNTING | Accounting & bookkeeping | 8931 |
| | PRO_LEGAL | Legal services | 8111 |
| EDUCATION | EDU_ONLINE_COURSES | Online courses | 8299 |
| | EDU_SCHOOLS | Schools | 8211 |
| | EDU_HIGHER | Colleges & universities | 8220 |
| TRAVEL | TRAVEL_AGENCY | Travel agencies & tours | 4722 |
| | TRAVEL_LODGING | Hotels & lodging | 7011 |
| | TRAVEL_TRANSPORT | Transportation services | 4789 |
| FOOD_BEVERAGE | FB_RESTAURANT | Restaurants | 5812 |
| | FB_FAST_FOOD | Fast food | 5814 |
| | FB_GROCERY | Grocery | 5411 |
| MEDIA_ENTERTAINMENT | MEDIA_STREAMING | Streaming & subscriptions | 4899 |
| | MEDIA_EVENTS | Events & tickets | 7922 |
| | MEDIA_PUBLISHING | Publishing | 2741 |
| NON_PROFIT | NP_CHARITY | Charities | 8398 |
| | NP_RELIGIOUS | Religious organizations | 8661 |
| | NP_MEMBERSHIP | Membership organizations | 8699 |
| OTHER | OTHER_SERVICES | Other services | 7299 |
| | OTHER_RETAIL | Other retail | 5999 |

现有一级编码全部沿用为大类，所以存量分类仍然可读（AC-10）。

## 8. 接口设计

### 8.1 Platform 新查询接口（`platform-support-api` 的 `api.referencedata`）

最终地址为 `/platform-support/platform/v1/reference-data/...`，内部调用，不经过 Gateway。

| 接口 | 方法与路径 | 参数 | 响应 |
|---|---|---|---|
| `ReferenceCurrencyQueryApi.listCurrencies` | GET `/currencies` | `status`、`codes`（可选，逗号分隔，最多 50 个） | `ReferenceCurrencyListResponseBody{items[]}` |
| `ReferenceCountryQueryApi.listCountries` | GET `/countries` | `status`、`codes` | `ReferenceCountryListResponseBody` |
| `CountrySubdivisionQueryApi.listSubdivisions` | GET `/country-subdivisions` | `countryCode`、`status`、`codes` | `CountrySubdivisionListResponseBody` |
| `IndustryQueryApi.listIndustries` | GET `/industries` | `level`、`parentCode`、`status`、`codes` | `IndustryListResponseBody`（按大类 sort_no、细类 sort_no 排序） |
| `MccQueryApi.searchMccs` | GET `/mccs` | `keyword`（匹配编码前缀或英文名）、`codes`、`status`、`page`、`size` | `PagingResponseBody<MccResponseBody>`（复用公共分页，按编码升序） |

- 每条记录都返回：编码、标准属性、`name`、`status`、`version`；有 logo 的再返回 `logoFileAddress` 和 `logoPublicUrl`。
- **D-03 由 Platform 一并返回 logo 的公开地址**：前端拿到文件地址后，若逐个找文件服务换链接，国家列表要请求约 250 次。Platform 的参考数据模块在同一进程内批量读取文件元数据（每次查询只读一次），附上 `logoPublicUrl`；文件不是公开文件或检查未通过时返回空，前端显示内置占位。数据库仍只存文件地址（符合文件地址原则）。
- **新增字段一律可空、使用包装类型**：消费方都开着严格 JSON 模式，可空字段方便以后演进（文件公开桶需求的教训）。
- 参数错误返回 400（统一的 Problem 错误格式）；`codes` 中不存在的编码不报错，只是不出现在结果里，由调用方判断。
- 旧接口 `CurrencySpecQueryApi`、`CountryRegionQueryApi` 的方法和响应完全不变，只把 Mapper 改为读新表（S-01）。

### 8.2 Platform 币种维护接口（`CurrencySpecManagementApi`，只有 ops-bff 调用）

- `CreateCurrencySpecRequestBody` 加 `numericCode`（3 位数字，必填）、`name`（必填，最长 128）、`symbol`（必填，最长 8）、`logoFileAddress`（可空，须为 `/files/v1/{fileId}` 格式且是公开文件）。
- `UpdateCurrencySpecRequestBody` 加同样 4 个字段。S-02 规则在 `UpdateCurrencySpecProcessor` 中检查：不是 DRAFT 时，`numericCode`、`name`、`symbol`、`minorUnit`、`roundingMode` 只要有一个与库中不同就拒绝（422，消息写明哪个字段冻结），只允许修改 `logoFileAddress`。
- 写审计：`CurrencySpecAuditFacts` 加入新字段的前后值。
- 列表与详情响应（管理接口的响应）加上新字段：ops-bff 是宽松模式，能兼容。

### 8.3 client-master

| 位置 | 设计 |
|---|---|
| 新出站端口 | `integration/ReferenceDataClient`（接口）+ `ReferenceDataClientImpl`，调用 `ReferenceCurrencyQueryApi`、`ReferenceCountryQueryApi`、`CountrySubdivisionQueryApi`、`IndustryQueryApi`、`MccQueryApi`，**只用 `codes` 查询**（S-08，单次最多 50 个编码，超过就分批）；沿用现有 `HttpServiceClientFactory.create(...)` 的严格模式，上限 8192 |
| `REF_` 目录码 | 常量类 `ReferenceCatalogCodes`（`REF_CURRENCY`、`REF_COUNTRY_SUBDIVISION`）。`OnboardingPackServiceImpl.catalogCodes` 跳过 `REF_` 开头的码，不从 `t_catalog_item` 加载 |
| 提交校验 | `ClientSubmissionPreparer` 在开事务前收集本次申请中的全部国家值（地址、证件签发国、国籍、居住国、出生国、注册国、COUNTRY 类型的扩展属性）、`REF_CURRENCY` 值和 `REF_COUNTRY_SUBDIVISION` 值，按类型各批量查一次 Platform，得到本次请求用的 `ReferenceCodeCheck`（只在内存中，用完即弃）。`OnboardingRequirementEvaluator` 对 `REF_` 字段和国家字段用它判断：不存在或不是 ACTIVE 时报 `FIELD_VALUE_NOT_IN_CATALOG`，挂在对应字段路径上；省州的 `countryCode` 与地址国家不一致时同样报错 |
| 保存草稿 | 只校验格式：国家 `[A-Z]{2}`、币种 `[A-Z]{3}`、省州 `[A-Z]{2}-[A-Z0-9]{1,3}` |
| 分类保存 | `ReplaceClientClassificationProcessor.processWithinLock` 先检查状态，再批量查 Platform（行业编码、MCC 编码），然后才调用 `classifications.replace`（事务在其内部）。规则：行业必须存在、ACTIVE 且 `level=2`；MCC 必须存在且 ACTIVE；不满足时 422 `CLASSIFICATION_INVALID`，消息写明编码。`validate()` 中的重复和"恰好一个主要分类"检查保持不变 |
| 删除 | `ClientClassificationAdminApi.listClassificationOptions`（`/classification-options`）及其 Controller 方法、响应体、测试（S-07） |
| 守卫 | auto 测试读取初始数据 SQL 与测试夹具：`t_catalog_item` 中不得出现 `INDUSTRY`、`CURRENCY`、`STATE_OR_PROVINCE` 或 `REF_` 开头的 `catalog_code` |
| 测试 Mock | 新增 `MockPlatformReferenceDataApi`（进程内控制器，实现 5 个 QueryApi，返回配置的 DTO 并记录调用），登记到 `MockPlatformAuthenticationApi` 的 `/platform-support` 前缀判断中 |

Platform 不可用时，提交与分类保存都由公共异常边界返回 503 系列 Problem，不写入任何数据（AC-09）。

### 8.4 client-console-bff

- 新增 `integration/PlatformReferenceDataClient` + `Impl`，响应上限 262144 字节（国家全量约 60 KB，留出余量）。
- **D-04 BFF 自有的 form-schema 页面模型**：BFF 新建 `ClientFormSchemaResponseBody`，内容 = client-master 的 Schema + 参考数据补全，不再原样透传 client-master 的类型。新增的 Processor `GetClientFormSchemaProcessor` 负责组装：
  - `catalogs` 中 `REF_CURRENCY` 补成 ACTIVE 币种列表，每项 `{itemCode, name, logoPublicUrl}`；`REF_COUNTRY_SUBDIVISION` 补成该辖区可用国家（地址允许的国家）的 ACTIVE 行政区，每项加 `countryCode`，前端按地址国家筛选。
  - 新增 `countries` 数组：ACTIVE 国家 `{code, name, logoPublicUrl, callingCode}`。
  - 原有 `itemCode + labelKey` 的目录项不变。

### 8.5 operations-console-bff

| 接口 | 设计 |
|---|---|
| `GET /client-onboarding-admin/v1/cases/{caseId}`（审核详情，路径不变） | `ClientReviewDetailsProcessor` 改为从 Platform 取 ACTIVE 行业树（含建议 MCC），放进 `industries`：每个大类带 `children`；`ClientReviewCctClientImpl.options` 删除 |
| `GET /reference-data-admin/v1/mccs`（新增） | 分类页的 MCC 搜索：`keyword`、`page`、`size`，转发 Platform |
| `GET /reference-data-admin/v1/{countries, country-subdivisions, industries, mccs}`（新增） | "参考数据"页的只读数据；需要权限 `REFERENCE_DATA_READ` |
| 币种管理接口（路径不变） | 请求和响应模型加 4 个字段；更新 `currency-spec-boundary.json` |

Gateway 路由、权限、菜单登记在 **块 139**（候选，阶段 6 先核对 mor-test 线上库）：权限 `REFERENCE_DATA_READ`，菜单 `OPERATIONS_REFERENCE_DATA`（挂在"平台配置"下），路由 `/api/operations/reference-data-admin/v1/**` 共 4 条 + MCC 搜索 1 条；默认不启用，部署后再激活。分类页的 MCC 搜索复用分类页已有的审核权限，不需要 `REFERENCE_DATA_READ`。

## 9. 多语言

- **D-05 命名空间**：client-console 与 operations-console 两个应用各建 `refdata`（编码规则 `[a-z][a-z0-9-]{0,63}`）。键：`refdata.industry.<CODE>`、`refdata.subdivision.<CODE>`、`refdata.mcc.<CODE>`。英文基础文案 = 表中的 `name`，由生成脚本导出；中文翻译范围按 S-09。
- 国家和币种不建键，用 `Intl.DisplayNames`。
- client-console-web 删除 `onboarding-schema` 中 `catalog.INDUSTRY.*`、`catalog.CURRENCY.*`、`catalog.STATE_OR_PROVINCE.*` 三组键；operations-console-web 分类页的行业名称改用 `refdata`。
- 前端显示顺序（B7）：i18n 键 → 后端 `name` → 编码。i18n 守卫测试补上 `refdata` 命名空间。

## 10. 页面设计

原型画在主原型文件中（2026-10-27 修订 D-07：最初画在需求目录的临时文件里，经用户指出后改画进主原型，临时文件已删除）：

- **运营端**：`operations-console-web/docs/operations-console.pen`，y≈94400 与 95600 两行，共 9 张画板（外壳沿用“卡 BIN 库”，组件与颜色沿用主原型）：参考数据 01 国家、02 行业、03 MCC、04 行政区；币种 05 编辑草稿、06 已启用只能换图标（弹窗）；客户入驻审核·风险与分类 01 两级行业 + MCC 搜索、02 保存校验未通过；客户入驻配置·目录（标出来自 Platform 的目录）。导出图：[assets/prototype/ops/](./assets/prototype/ops/)。
- **Client 端**：`client-console-web/client-console.pen` 的 C5 画板（x=15000, y=8300，沿用切片 1 v2 样式）：A 酋长国下拉、B 注册国家（国旗 + 搜索）、C 预计流水币种、D 提交时校验未通过（页顶 3px 竖线汇总 + 字段红框）、E 移动端底部面板。导出图：[assets/prototype/client/](./assets/prototype/client/)。

| 页面 | 要点 |
|---|---|
| Client 入驻：国家选择 | 可搜索下拉，每项显示国旗 + 本地化名称；输入编码或名称都能匹配；只列 ACTIVE 国家 |
| Client 入驻：地址省州 | 随地址国家切换选项；切换国家时清空已选省州；没有行政区的国家显示文本框 |
| Client 入驻：预计流水币种 | 下拉显示"本地化名称（代码）"，有币种图标时显示图标 |
| 运营分类页 | 每行分类：大类（带图标的下拉）→ 细类 → MCC。选了细类自动填入建议 MCC，并标"建议"；运营可以点开 MCC 搜索框改选，改选后标"已改"。保存失败时错误挂在对应控件上 |
| 运营"参考数据"页 | 平台配置下新菜单；四个页签：国家（国旗、编码、三位码、数字码、名称、区号、法定货币、区域、状态）、行政区（按国家筛选）、行业（树形表）、MCC（搜索、高风险标记）；均为只读，支持搜索与状态筛选；空状态与加载失败有提示 |
| 运营币种页 | 表格加数字码、名称、符号、图标列；编辑抽屉加 4 个字段；非 DRAFT 时这些字段只读，图标仍可更换 |

## 11. 研发与验证计划

| 系统 | 研发任务 | 验证（对应 AC） |
|---|---|---|
| platform-support | DDL 变更脚本（含回退）、PDManer、schema、测试 DDL；DataObject、Mapper、Repository 改名与新增；5 个 QueryApi + Controller + Service；币种维护加字段与冻结规则；logo 地址批量解析；初始数据生成脚本与 DML | API auto 测试（每个接口：筛选、`codes`、不存在编码、参数错误）；旧接口响应字段与改名前逐一比对（AC-02）；币种冻结规则；初始数据完整性测试（AC-04、AC-05：每个细类的建议 MCC 存在且 ACTIVE） |
| client-master | 第 8.3 节全部；DML；测试改写 | 提交：不存在 / 停用的国家、币种、省州；省州与国家不匹配；Platform 不可用（AC-07、AC-09）；分类：只选大类、不存在的 MCC、正常保存（AC-08）；守卫测试（AC-06）；存量大类可读（AC-10） |
| client-console-bff | 参考数据客户端、`GetClientFormSchemaProcessor`、页面模型 | auto 测试：补全后的 Schema 结构、Platform 不可用 |
| client-console-web | 国家选择（国旗）、省州联动、币种名称、`refdata` 文案、删除旧键 | 单元测试、i18n 守卫；浏览器冒烟（en / zh-CN，1440 / 390）（AC-11、AC-14） |
| operations-console-bff | 审核详情改取 Platform、MCC 搜索、参考数据接口、币种字段 | auto 测试、边界样例数据更新 |
| operations-console-web | 分类页两级选择 + MCC 搜索、参考数据页、币种页字段 | 单元测试、tsc、build、浏览器冒烟（AC-12、AC-13） |
| arch-rules | 生成器（含第 206 行特殊处理）、`table-ownership.yaml`、断言与表数 | `TableOwnershipAutoTest` |
| 回归 | ledger、billing、card 不改代码 | 本地 e2e：ledger / billing 快照加载、card BIN 导入（AC-02） |

本地验证沿用切片 1 的方式：单独的 worktree、私有 Maven 仓库；不在共享本地库上跑全量测试。

## 12. 交付前核对（写接口示例第 11 节）

本需求的写接口只有两处：币种新建 / 修改（在原 Processor 上加字段和冻结规则），以及分类保存（新增事务外校验）。阶段 4 交付时逐项说明：Processor 主流程、事务位置（外部查询都在事务外）、Context 不新增同义对象、Repository 条件更新与版本号、锁对象（分类沿用原锁）。其余都是只读查询。

## 13. 待确认（阶段 3）

| # | 问题 | 建议 |
|---|---|---|
| D-01 | `level` 是 MySQL 关键字 | 物理列命名为 `industry_level`，接口字段仍叫 `level` |
| D-03 | logo 怎样变成可访问的链接 | Platform 查询时在同一进程内批量算出 `logoPublicUrl` 一并返回；数据库只存文件地址 |
| D-04 | Client 的 form-schema 由谁补全参考数据 | BFF 新建自己的页面模型，用 Processor 组装，不再原样透传 client-master 的类型 |
| D-06 | 第 7 节行业草案 | 按草案在 test 环境使用，生产前由合规确认 |
| D-07 | 原型文件放在哪里 | ~~放在需求目录的临时文件~~ → 已改画进主原型 `operations-console.pen`（9 张）与 `client-console.pen`（C5），临时文件已删除 |
