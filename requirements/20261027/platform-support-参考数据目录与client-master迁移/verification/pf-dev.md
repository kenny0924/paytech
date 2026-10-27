# pf-dev：platform-support 阶段 4 实施与验证记录（2026-10-27）

工作区：`/Users/kenny/Desktop/Workspace/Code-MoR-refdata`（detached `aba76a1a`），私有 Maven 仓库 `~/.m3-refdata`。未提交、未推送、未触碰 mor-test。

接口开发前已读：`agent-docs/DEVELOPMENT_CONVENTIONS.md`（2—5 节）、`agent-docs/examples/WRITE_API_EXAMPLE.md`（第 11 节）、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、需求 `requirement.md` / `technique.md`（1—13 节）/ `db/platform-support/ddl.sql`、`agent-docs/decisions/2026-10-27-platform-reference-data.md`。所属：Processor 在 `paytech-platform-support-core` 的 `referencedata/service/processor`；币种修改 Context 为 `UpdateCurrencySpecContext`；本地原子写入为 `CurrencySpecManagementService.update`（加入 Processor 的 `OperationsTransactionExecutor.write` 事务）；本需求没有事务外外部步骤（logo 核对读同库文件表）。

## 1. 改动文件

### 数据库（`paytech/platform-support/docs/database/`）
- `sql/changes/2026-10-27-reference-data.sql`（新）：预检过程 `ref_data_assert`（PRE / BACKFILL，SIGNAL 中止）→ 两表改名补列 → 新建三表 → 一个事务内回填与装数（内嵌生成的 `change-backfill.sql`）→ COMMIT 前断言 → 收紧 NOT NULL → 删除预检过程。不可重跑（预检拦截）。
- `sql/changes/2026-10-27-reference-data-rollback.sql`（新）：反向 RENAME、删列、删新表、删除固定 ID 1002—1004 的 AED / SAR / EGP；带预检。
- `sql/platform-support-schema.sql`：`t_ref_currency`、`t_ref_country` 替换原表定义，新增 `t_ref_country_subdivision`、`t_ref_industry`、`t_ref_mcc`。
- `sql/platform-support-initial-data.sql`：USD 种子与 249 行国家种子改为写 `t_ref_*` 全列；国家块后新增「Reference data catalog」块（AED / SAR / EGP、行政区、行业、MCC）；相关注释表名更新。
- `platform-support.pdma.json`：两实体改名补列、三实体新增、`reference_data` 图更新（直接编辑 JSON，未运行生成器）。另把 `region_code` 列注释补全为与 schema 一致（HEAD 已有的漂移）。
- `paytech-platform-support-test/src/test/resources/init/ddl.sql`：同 schema 与同种子。

### 代码
- file-document（文件 Owner）：`FileLifecycleService.describeAll(Collection<Long>)` + `FileLifecycleRepository/Mapper.describeFiles` + XML（把 `describeFile` 的查询抽成 `DescribeFileQuery` 片段，单条与批量共用）。
- audit-security：`OperationsAuditFact.ConfigurationField` 新增 `NUMERIC_CODE`、`SYMBOL`、`LOGO_FILE_ADDRESS`（名称沿用已有 `DISPLAY_NAME`）。
- core `referencedata`：
  - DataObject：`ReferenceCurrency`（原 `CurrencySpec`）、`ReferenceCountry`（原 `CountryRegion`）、`CountrySubdivision`、`Industry`、`Mcc`。
  - Mapper + XML：`ReferenceCurrencyMapper`、`ReferenceCountryMapper`、`CountrySubdivisionMapper`、`IndustryMapper`、`MccMapper`（SQL 全在 XML）。
  - Repository（interface + Impl）：同名五个；`MccRepositoryImpl` 用同条件 `COUNT` + `LIMIT/OFFSET`，关键字按 `!` 转义。
  - 领域：`CurrencyAttributes`（可维护属性与冻结字段比较）、`UpdateCurrencySpecContext`、`ChangeCurrencySpecStatusContext`、`CurrencySpecAuditFacts`（含新字段前后值）。
  - Service：`CurrencySpecQueryService`、`CurrencySpecManagementService` 改用新 DataObject；删除 `CountryRegionQueryService`（旧国家接口改走新的国家 Processor）。
  - Processor：新增 `ListReferenceCurrenciesProcessor`、`ListReferenceCountriesProcessor`、`ListCountrySubdivisionsProcessor`、`ListIndustriesProcessor`、`SearchMccsProcessor` 与 `ReferenceDataLogos`（批量换公开地址、核对 logo 是公开文件）；`CreateCurrencySpecProcessor`、`UpdateCurrencySpecProcessor`、`ChangeCurrencySpecStatusProcessor`、`CurrencySpecManagementSupport` 改造。
  - 内部请求 / 结果：五个查询 Request（Bean Validation：编码格式、≤50、level 1..2、keyword ≤64、分页）、五个 Result；`Create/UpdateCurrencySpecRequest` 加四个字段。
- app：`ReferenceCurrencyController`、`ReferenceCountryController`、`CountrySubdivisionController`、`IndustryController`、`MccController`、`ReferenceDataQueryConverter`（新）；`CountryRegionController` 改为经国家 Processor 读取、只投影两列；`CurrencySpecManagementConverter` 传入新字段（名称去首尾空白）；`PersistenceConfiguration` 注册 `ReferenceDataStatus` 的 `CodeEnumTypeHandler`。
- api：只改 JavaDoc 中的旧表名（`CurrencySpecQueryApi`、`CountryRegionQueryApi`、`CurrencySpecResponseBody`、`CurrencyRoundingMode`、`CurrencySpecStatus`）；阶段 A 的合同形状未改。已重新 `install`。
- 其他：arch-rules（`generate-table-ownership.mjs` 第 206 行特殊分支改写 `t_ref_currency`、`table-ownership.yaml`、`TableOwnershipAutoTest` 表名与表数 +3）；card 三处列注释 `t_country_region` → `t_ref_country`（只改注释）。

### 测试（`paytech-platform-support-test`）
- 新：`ReferenceCurrencyQueryApiTest`、`ReferenceCountryQueryApiTest`、`CountrySubdivisionQueryApiTest`、`IndustryQueryApiTest`、`MccQueryApiTest`、`ReferenceDataSeedIntegrityTest`、`ReferenceDataMocker`（logo 文件只登记元数据：停用的测试实例 + 公开桶 + 文件行；改写过的种子行逐行恢复）。
- 改：`ReferenceDataTestMapper`、`CurrencySpecMocker`（四条常驻种子 USD/AED/SAR/EGP）、`CurrencySpecQueryApiTest`、`CountryRegionQueryApiTest`（各加旧响应形状冻结断言）、`CurrencySpecManagementApiTest`（新字段、S-02 冻结 422、logo 公开文件核对、审计新字段）。

### 需求目录产出（由子代理生成，已复核）
- `db/platform-support/generator/`：`generate_reference_data.py`（标准库、确定性、带断言）、`README.md`（来源、版本、许可、取值规则）、`upload_flags.py`（国旗上传桩，未运行）、`sources/`（缓存的源数据）。
- `db/platform-support/generated/`：`change-backfill.sql`、`initial-data-blocks.sql`、`summary.json`、`expected.json`。
- `release/i18n-bundles/`：`{client-console,operations-console}.refdata.{en,zh-CN,plan}.json`。

## 2. 本地 SQL 执行

- 备份：`/Users/kenny/mor-backups/refdata-20261027/mor_platform.sql.gz`（mysqldump `--single-transaction --routines --triggers`，执行前）。
- 演练：在临时库按 HEAD 的旧表与种子（另加一条 Snowflake ID 的 JPY DRAFT 与一条 INACTIVE 的 SAR）执行变更脚本 → 五表 `SHOW CREATE TABLE` 与按新 schema 建的空库逐字一致；旧 SAR 被启用且版本 +1，JPY 被回填；回退脚本执行后两张旧表 `SHOW CREATE TABLE` 与原定义逐字一致；再次执行变更脚本在预检处停止。临时库已删除。
- 本地 `mor_platform` 执行 `changes/2026-10-27-reference-data.sql`：成功。核对：五表 `SHOW CREATE TABLE` 与 `platform-support-schema.sql` 一致；币种 4 行（USD 840 US Dollar $、AED 784、SAR 682、EGP 818，全部 ACTIVE）；国家 249 行全部有三位码 / 数字码 / 英文名（7 个无区号：AQ BV GS HM PN TF UM；1 个无法定货币：AQ）；行政区 AE 7 / SA 13 / EG 27；行业 1 级 10、2 级 32；MCC 298（高风险 14）。
- 测试跑完后核对：币种仍为 4 行，种子没有遗留的 logo 或非 ACTIVE 状态，文件表为空。

## 3. 数据来源与许可（详见 generator/README.md）

| 数据 | 来源 | 版本 | 许可 | 数量 |
|---|---|---|---|---|
| 国家三位码、数字码、英文名、法定货币；币种数字码、英文名、符号 | Unicode CLDR JSON（cldr-core / cldr-localenames-full / cldr-numbers-full） | 48.2.0 | Unicode License v3 | 249 国；回填临时表 149 个现行币种 |
| 国际区号 | google/libphonenumber `PhoneNumberMetadata.xml`（CLDR JSON 已不含 telephoneCodeData） | v9.0.42 | Apache-2.0 | 242 有区号 |
| MCC | greggles/mcc-codes `mcc_codes.csv` | commit `df792275` | The Unlicense | 298（排除 3000—3999 品牌码 683 个） |
| 高风险标记 | Visa VIRP 与 Mastercard SMRP 的公开摘要（LegitScript、CCBill、Nuvei 等，见 README） | 2026-10-27 | — | 14 个：4816 5122 5912 5962 5966 5967 5993 6051 7273 7800 7801 7802 7994 7995 |
| 行政区 | ISO 3166-2 拉丁名（按维基 ISO 3166-2:AE/SA/EG 表核对）；阿拉伯文名手工编写 | 2026-10-27 | 事实数据 | 47 |
| 行业 | technique.md 第 7 节草案 | — | 平台自有 | 10 + 32 |
| 国旗 | lipis/flag-icons 4x3 SVG（上传桩，未执行） | 7.5.0 | MIT | — |

国家 `default_currency_code` 是 ISO 代码，不要求存在于 `t_ref_currency`（148 个国家币种不在 USD/AED/SAR/EGP 中）；`t_ref_currency` 只保留已有 USD 并新增 AED / SAR / EGP。

## 4. 测试

环境：`JAVA_HOME=jdk-25`，`-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`（worktree 没有 `.env.local`）。运行前确认 `PlatformDataCleaner.OWNED_TABLES` 全部为空。

| 命令（在 `paytech/` 下，均带 `-o`） | 结果 |
|---|---|
| `./mvnw -o -pl platform-support/paytech-platform-support-test test -Dtest='com/paytech/platform/support/**/referencedata/**' -Dpaytech.local-environment.dir=… -Dsurefire.failIfNoSpecifiedTests=false` | 45 个，全部通过（9 个类） |
| 同上，`-Dtest='com/paytech/platform/support/auto/ext/http/files/**,InternalAuthDualPassThroughTest,FileRecordManagementTest'`（文件 describe 改动与旧币种接口的回归） | 47 个，全部通过 |
| `./mvnw -o -pl tools/arch-rules test` | 68 个，全部通过 |
| `./mvnw -o test-compile`（整个 reactor，含其他三个代理当时的改动） | BUILD SUCCESS |

覆盖点：五个新接口的全量、状态 / 编码筛选、编码去空白与转大写、不存在编码不报错、400（未知状态、编码格式、空白项、超过 50 个、level、parentCode、page/size、keyword 长度）、MCC 分页（默认页、相邻页衔接、越界空页保留总数、编码前缀与名称包含、`%` 按字面）、`logoPublicUrl`（公开文件有值；私有文件与无 logo 为空；国家、行业、币种三个接口）；旧接口响应形状冻结（外层只有 `items`；币种元素字段 `currency, minorUnit, roundingMode, status, updateTime, version`，国家元素 `countryCode, regionCode`，停用国家照常返回）；币种维护（新字段写入与审计、DRAFT 可改全部属性、非 DRAFT 改冻结字段 422 并逐字段报错、非 DRAFT 只换 / 清空 logo 成功、私有或不存在的 logo 在新建和修改时 422、无变化不加版本、版本冲突、404、启停转换、权限）；初始数据完整性（每个细类建议 MCC 存在且 ACTIVE、细类上级都是大类、249 国编码完整、行政区数量与编码前缀、四个币种 ACTIVE、必选高风险 MCC 已标记）。

未覆盖：并发同币种竞争（沿用既有资源锁与版本条件更新，本次未改锁）；国旗上传（桩）；ledger / billing / card 的本地 e2e 回归（AC-02，留给阶段 5）。

## 5. 写接口示例第 11 节核对（币种新建 / 修改）

- API 与 Body：`CreateCurrencySpecRequestBody` / `UpdateCurrencySpecRequestBody`（阶段 A，class + 字段 JavaDoc + Hibernate Validator）；内部 `Create/UpdateCurrencySpecRequest` 注解由 `AbstractProcessor.validateBasic` 触发。
- Controller / Converter 只做适配：`CurrencySpecManagementController` → `CurrencySpecManagementConverter.create/update`（只做 Code 解析与去空白）→ Processor。
- Processor 位于 `referencedata/service/processor`，主流程 `UpdateCurrencySpecProcessor.execute`：授权复核 → 行锁并核对版本（`CurrencySpecManagementSupport.lock`）→ 构造 Context → `requireNotFrozen`（S-02，422）→ 无变化提前返回 → logo 换成新地址时 `ReferenceDataLogos.requirePublicFile`（422）→ 冻结审计值 → `service.update` → 登记审计。只查询本模块 `ReferenceCurrencyRepository`，不调用 Mapper，不逐表写入。
- Context：`UpdateCurrencySpecContext` 持有已锁定的 `ReferenceCurrency` 与目标 `CurrencyAttributes`；纯方法 `unchanged()`、`frozenFieldChanges()`、`logoReplaced()` 无外部访问；没有同义 ID、Input 或 View。
- Service：`CurrencySpecManagementServiceImpl.update` 只调用 `ReferenceCurrencyRepository.updateAttributes`，不再查询、不调用其他 Service 或 Processor。
- 事务：`processWithinLock` → `OperationsTransactionExecutor.write(() -> execute(request))`，单个本地事务完成授权、行锁、logo 核对、保存与审计登记。logo 核对经文件 Owner 的 `FileLifecycleService.describeAll` 读取同一 `mor_platform` 的文件表，不是外部调用；没有 HTTP / Redis / MQ。保存失败抛出即回滚，不推进。
- Repository：`ReferenceCurrencyRepositoryImpl.updateAttributes` 按 `id + version + status` 条件更新，影响行数不为 1 抛 409；成功后直接同步原 DataObject（属性、版本、时间）。Context 不进入持久化层。
- 锁：沿用 `currency-spec:{code}` 的 `TRY_LOCK`（新建、修改、启停共用），未叠加其他机制；数据库版本条件与唯一索引仍为最终保护。
- 业务防重：币种代码是唯一业务身份——新建先查重，唯一键冲突转为 400“已存在”；修改按原版本，无变化返回原版本不写审计。没有 Idempotency-Key 处理或旧命令幂等组件。
- View：写接口没有新增 View；DataObject 不出模块（新查询都经 `*Result` 转换）。
- 公共包：复用 `AbstractLockProcessor`、`RequestBodyValidationException`、`PagingRequestBody/PagingResponseBody`、`CodeEnumTypeHandler`；没有生产 Mock 或无消费者字段。
- 验证：见第 4 节，均为真实 HTTP 入口 + 本地 MySQL / Redis。

## 6. 与 technique.md 的偏差

1. **MCC 298 行，不是约 600 行**：排除 3000—3999 品牌码（航空公司、租车、酒店，由收单方分配，不用于商户分类）。全部 32 个建议 MCC 都在清单中。
2. **符号用 CLDR 英文标准符号**：AED / SAR / EGP 在 CLDR en 中没有专用符号，值等于代码（DDL 注释示例中的 `د.إ` 是阿语区域符号）。
3. **区号来自 libphonenumber**：CLDR JSON 48 已不提供 `telephoneCodeData`；technique 6.2 本就要求与 libphonenumber 一致。
4. **行政区阿拉伯文名非 ISO 公布**：ISO 3166-2 对 AE/SA/EG 不公布阿拉伯文名称；`local_name` 列注释改为“本地文字官方名称；ISO 3166-2 未公布本地文字时取该国官方写法”（schema、PDManer、测试 DDL、变更脚本一致；需求目录的 `ddl.sql` 草案未改）。SA 类别取 ISO 现行的 `REGION`。
5. **国家英文名是 CLDR 英文短名**（如 “Hong Kong SAR China”、“Türkiye”），不是 ISO 3166-1 正式短名。
6. **非 DRAFT 冻结拒绝用 422 `VALIDATION_ERROR` + `errors[]`**（每个冻结字段一条，消息含币种与当前状态）；原实现是 400 `BUSINESS_CHECK_FAILED`。logo 不存在 / 非公开同为 422（字段 `logoFileAddress`）。
7. **公开地址批量读取用文件 Owner 新增的 `describeAll`**（同库一次 `IN` 查询），没有复用 `FileServiceReleaseBundleStore.publicUrl`（后者单条且不可用时抛 503）。
8. **不存在的服务层**：新表查询的 Processor 直接读本模块 Repository（规范允许），没有为每张表新建只转发的 Service；`CountryRegionQueryService` 删除。
9. **旧国家接口经新国家 Processor 读取全部状态**：响应仍只有两列；国旗上传后会多一次文件元数据批量读取（一次查询）。

## 7. 遗留与注意

- 发布：改名后旧 Platform 镜像不能读取，SQL 与新镜像同一窗口切换；回退为回退脚本 + 旧镜像（阶段 6 写入运行卡）。
- 国旗上传桩 `generator/upload_flags.py` 未实现上传；需先登记 PUBLIC 写入路由、原因码，并确认公开桶检查策略是否接受 `image/svg+xml`（可能要转 PNG）。
- 高风险 MCC 中 5962、5993、7273、7994 的依据只来自单一卡组织或二手摘要；行业细类与建议 MCC、MCC 文本修订（见 README `MCC_TEXT_OVERRIDES`）生产前需合规确认。
- 维基核对的少数行政区名中 ASCII 撇号（如 `'Asīr`、`Ḩā'il`）可能与 ISO OBP 的弯撇号不同，生产前对照 ISO OBP。
- `paytech-platform-support-access-control/.../console-resource-capabilities.json` 的改动不是本代理所做（属 ops-fe 的块 139 范围）。
- 运行 arch-rules 生成器（`node scripts/generate-table-ownership.mjs`）会丢掉 17 张手工登记的 client-master / compliance 表：本次已恢复为手工编辑的 YAML；该生成器与现状不一致是既有问题，未修。
- `CURRENT_IMPLEMENTATION.md`、Platform 数据库文档与场景文档（R10）不在本代理范围，未改。
