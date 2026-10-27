# 全平台参考数据（Reference Data）

> 状态：**已确认（2026-10-27）**，决定记录 [2026-10-27 参考数据决定](../../agent-docs/decisions/2026-10-27-platform-reference-data.md)；Q1—Q11 全部按建议。起因是 client-onboarding 设计中发现国家、行政区、行业、MCC、组织类型等"字典"数据散落在 yml、注释与各服务列定义里，没有统一归属。本稿给出分类、准入条件、命名、字段设计、读取与维护方式，以及 onboarding 用到的数据归属清单。讨论定稿后登记到 `agent-docs/decisions/`（修订 [目标架构 V2 决定](../../agent-docs/decisions/2026-10-17-target-architecture-v2.md) D41 的表述），实现与部署状态只记在 `agent-docs/CURRENT_IMPLEMENTATION.md`。配图：[reference-data.pen](reference-data.pen)（五张画板：01 分类、准入与行业做法；02 t_ref_* 目录与消费方矩阵；03 读取方式、快照组件、维护与展示分工；04 onboarding 数据归属、改名影响与待定问题；05 行业三级分类）。
>
> 已与用户确认（2026-10-27 第一轮）：① 不建通用 KV 字典（`dict_type` + `dict_item`），每类参考数据一张有类型的表；② 参考数据表固定前缀 **`t_ref_`**；③ 默认只用评审过的初始化 SQL 维护，确有运营需要才做维护页；④ 通用"简单码表"表**暂不建，保留为可选项**（第 11 节）。
>
> 第二轮（2026-10-27）用户提出：① 行业应有三级分类（行业 → 细类 → MCC）；② 国家、币种要有 logo；③ 参考数据不只是 code + value，应按行业通用做法把常用元素建成字段；④ **后端关心数据，前端关心国际化和展示**。本稿据此修订第 4–7、10、12、14 节，新增第 7.6 节行业分类；第 1 点的表结构为建议（Q8），待确认。

## 1. 一句话

**参考数据 = 来自外部标准（或平台统一分类体系）、跨系统共用、变化很慢、不带业务规则的数据集合。** 它由 Platform Support 唯一持有、每类一张 `t_ref_*` 表、以编码为业务键，**把行业通用的标准属性都建成字段**（标准英文名、其他编码体系、符号、logo 等），经 `platform-support-api` 提供只读查询；其他系统只保存编码字符串，不建副本。**后端只管数据，前端负责国际化与展示。** 带业务规则的目录（支付方式、拒绝原因、证件类型、组织类型、行业准入……）不是参考数据，留在业务 Owner。

## 2. 起因与现状（2026-10-27 只读核对）

| 数据 | 现在 | 问题 |
|---|---|---|
| 币种 | `mor_platform.t_currency_spec`，有维护页与启停；ledger、billing 读进程内快照（[2026-10-20 决定](../../agent-docs/decisions/2026-10-20-currency-spec-platform-snapshot-read.md)） | 已是目标形态的雏形；只有精度属性，缺数字码、名称、符号、logo |
| 国家 | `mor_platform.t_country_region`（249 行，国家 → 区域），只经 SQL 维护，`GET /platform/v1/country-regions` 供 Card 导入 BIN 时读取 | 名字是"映射"，实际就是国家表；8 个服务约 30 个 `*_country*` CHAR(2) 列没有统一的合法性来源 |
| MCC | **没有目录**。acquiring、billing、channel-connector、client-master、compliance、payment、risk-decision 共 10 余个 `mcc_code` 列，注释写着"取值来源：CCT MCC 目录 / 平台四位 MCC 目录"，但这个目录不存在 | 最大的缺口；onboarding O04 指派 MCC 时无处校验 |
| 行业 | client-master `application.yml` 的 `onboarding.form-catalog.industries`：一级平铺 `RETAIL`、`DIGITAL_GOODS`、`SOFTWARE_SAAS`、`PROFESSIONAL_SERVICES`、`EDUCATION`、`TRAVEL`…，中文 label 写在 yml | 只有一级，与 MCC 无关联；客户选的行业推不出 MCC |
| 行政区 | 没有；onboarding 地址 `state_or_province`、tax 的 `subdivision_code` 都需要 ISO 3166-2 | onboarding 与 tax 两个真实消费者即将出现 |
| 组织类型、标识类型、经营模式 | client-master `application.yml` 的 `onboarding.form-catalog`，无辖区维度 | 业务目录，onboarding 设计已改为入库（`t_organization_type`、`t_identifier_type`），**不是参考数据** |
| Risk 拒绝原因 / 主体标签 | Risk 自有字典（[2026-09-11 决定](../../agent-docs/decisions/2026-09-11-risk-rule-history-and-dictionaries.md)） | 正确归属，不动 |
| 时区、语言 | 时区以 IANA 名存 VARCHAR(64)；语言由 Platform 多语言模块的 Locale 目录负责 | 有成熟标准或已有 Owner，不需要建表（6.4） |

## 3. 行业做法

| 做法 | 代表 | 结论 |
|---|---|---|
| 通用 KV 字典 | RuoYi `sys_dict_type` / `sys_dict_data`，国内后台框架普遍采用 | 只适合"编码 + 名称"的展示下拉。数据库社区普遍把它称为 One True Lookup Table 反模式：无法约束某列只能用某一类编码、值退化为字符串、所有查询压一张表 |
| 每类一张专表 + 统一前缀 | SAP（`T005` 国家含 ISO 二位 / 三位码与多项属性、`T005T` 国家名、`TCURC` 币种，配置表统一 T 开头，上千张）；Odoo（`res_country` 含 code、name、phone_code、currency_id、image；`res_country_state`；`res_currency` 含 symbol、rounding、decimal_places） | 字段有类型、可校验；**专表普遍带齐标准属性**，不只是 code + name |
| 混合 | Oracle EBS：`FND_LOOKUP_VALUES` 放无属性的简单码表，国家等另建 `FND_TERRITORIES` | 无属性进通用表，一有属性就升专表 |
| 独立参考数据服务 | 银行业 BIAN 的 Public Reference Data Management 服务域：外部来源导入、内部统一分发币种、国家等 | 与 D41"Platform 持有并提供查询 API"一致 |
| 行业 → MCC | Adyen：商户在入驻时选平台定义的 Industry Code，Adyen 再把它映射到 MCC；Stripe：账户上直接设 MCC，MCC 列表按类别分组 | 客户面对的是"行业"，MCC 由行业推出并经审核确认（7.6） |

本平台取"专表 + 统一前缀 + 一个 Owner 分发"，混合方案的通用表作为可选项保留（第 11 节）。

## 4. 四类数据与归属

| 类别 | 判断标准 | 例子 | 归属 |
|---|---|---|---|
| ① 封闭取值 | 取值由代码控制，新增一个值必须改代码 | 订单状态、方向、`party_type` | 各服务 `api/enums` 或 `core/enums` |
| ② **参考数据** | 外部标准或平台统一分类、跨系统、慢变、无业务规则 | ISO 国家、ISO 4217 币种、ISO 18245 MCC、ISO 3166-2 行政区、平台行业分类 | **Platform 参考数据模块，`t_ref_*`** |
| ③ 业务目录 | 带业务规则、属性或生命周期，由某业务决定取值 | 支付方式目录、Risk 拒绝原因、税务分类、证件类型、组织类型、登记机关、行业准入 / 禁止行业 | 业务 Owner 自己的表 |
| ④ 纯展示选项 | 没有业务语义，只给页面下拉用 | 无业务含义的提示选项 | 前端 + i18n |

展示一律由前端负责（第 10 节）。

**同一个概念可能拆在两类里。** 例：MCC 码表本身、行业分类与"细类 → 建议 MCC"是 ②；"某 OE 允许哪些 MCC""某 MCC / 行业的风险等级""禁止或需审批的行业"是 ③，分别归 client-master / compliance / risk。国家同理：ISO 国家是 ②；"支持哪些国家入驻""国家风险等级"是 onboarding 的 `t_jurisdiction` 与 compliance（③）。

## 5. 准入条件

一类数据进入 `t_ref_*` 需**同时满足**：

1. **外部标准，或平台统一的分类体系**：取值由 ISO、卡组织、IANA 等外部机构定义；或者是平台为全系统统一制定、不带规则的分类（如行业分类）。业务系统各自的分类不算。
2. **至少两个系统真实消费**：只有一个系统用，就留在那个系统。
3. **不带任何业务规则**：表里只有"这个东西是什么"，没有"平台允许 / 禁止 / 收多少 / 风险多高"。
4. **没有更合适的标准库或已有 Owner**：JDK / 浏览器已提供权威数据（时区）或已有模块负责（Locale）时不建表（6.4）。

不满足时的去向：只有 1 个消费者 → 留在该系统；带规则 → 业务 Owner 的目录；有标准库或 Owner → 用库 / 归该 Owner。

## 6. 表的形态

### 6.1 命名

- 物理表 `t_ref_<名词>`，名词用单数、按数据本身命名：`t_ref_currency`、`t_ref_country`、`t_ref_country_subdivision`、`t_ref_industry`、`t_ref_mcc`。仍符合 `DATABASE_DESIGN_GUIDE` 的 `t_` 前缀规则，只是再分一组。
- 不用 `td_`：`ref` 与 BIAN、Platform 现有 `referencedata` 包名一致；"字典"一词在本仓库已被 Risk 业务字典占用，用 `ref` 可区分。
- 业务字典（类别 ③）**不使用** `t_ref_` 前缀，按 Owner 的业务名命名（如 `t_rule_reject_reason`、`t_identifier_type`）。前缀即"这张表是全平台参考数据、只在 `mor_platform`"的声明。
- 包名沿用 `com.paytech.platform.support.api.referencedata`；接口路径 `/platform/v1/reference-data/<复数名词>`。

### 6.2 字段原则：带齐行业通用的标准属性

参考数据不是 code + value。每张表按"行业通用做法里这类数据都有哪些元素"设计字段，判断来源依次是：标准本身定义的属性（ISO 数字码、三位码、行政区类别）→ 主流系统普遍建的列（SAP、Odoo、Stripe、Adyen）→ 本平台已知的消费场景（3DS / ISO 8583 报文用数字码、护照 MRZ 用三位码、电话输入用国际区号、后端生成单据用币种符号）。每个字段在第 7 节写明用途。

不进表的：翻译（前端 i18n）、格式化规则（前端 `Intl`）、任何业务规则（③）。

### 6.3 统一列模板

| 列 | 何时有 | 说明 |
|---|---|---|
| `id` BIGINT PK | 总是 | 初始化数据用固定小 ID（低于 Snowflake 范围），运营新建用 Snowflake |
| `<名词>_code` | 总是 | 主编码，`ascii_bin`；`uk_ref_<名词>_code` 唯一；发布后不改、不删、不复用 |
| `name` VARCHAR(128) | 总是 | **标准英文名**（ISO 官方短名或平台分类的英文名），是数据而不是 UI 文案；用于日志、导出、运营排查、后端单据，以及前端缺少翻译时的回退 |
| 标准属性列 | 按表 | 见第 7 节 |
| `logo_file_address` VARCHAR(64) NULL | 有图形标识的表 | 文件服务地址 `/files/v1/{fileId}`（[文件地址原则](../../agent-docs/decisions/2026-10-22-file-upload-address-principles.md)），文件放公开桶，公开 URL 由文件服务在读取时计算、不落库；为空时前端用内置图标。国家 = 国旗，币种 = 币种图标，行业 = 行业图标 |
| `sort_no` INT | 平台自定义顺序的表 | 只有行业分类需要；ISO 类由前端按本地化名称排序 |
| `status` VARCHAR(16) | 总是 | `DRAFT` / `ACTIVE` / `INACTIVE`。外部标准也会废止编码（如 2023 年 HRK 并入 EUR），统一用 status 表达；启用后冻结主编码与标准属性，停用只拦新用途 |
| `version` INT | 总是 | 乐观并发，便于以后统一维护页 |
| `create_time` / `update_time` | 总是 | UTC `DATETIME(6)` |

### 6.4 不建表、用标准库或已有 Owner 的

| 数据 | 用什么 | 理由 |
|---|---|---|
| 时区 | IANA 时区名，Java `ZoneId` / 浏览器 `Intl` | JDK 随版本更新 tzdata，自建表会过期 |
| 语言 / Locale | Platform 多语言模块已有 Locale 目录 | 已有 Owner（[多语言决定](../../agent-docs/decisions/2026-10-22-ui-text-localization.md)） |
| 电话号码格式校验 | libphonenumber（若引入，按 TECHNOLOGY_STACK 评审） | 国际区号进 `t_ref_country.calling_code`，号码格式规则复杂且常更新，交给库 |

## 7. 首批目录与字段

| 表 | 来源 | 消费者 | 维护 | 状态 |
|---|---|---|---|---|
| `t_ref_currency`（原 `t_currency_spec`） | ISO 4217 | ledger、billing（快照）、ops-bff；payment / card 报文数字码 | 维护页（已有） | 改名 + 加列 |
| `t_ref_country`（原 `t_country_region`） | ISO 3166-1 + 平台区域 | card（BIN 导入）、client-master / compliance（入驻）、tax、risk-decision、payment（3DS） | SQL | 改名 + 加列 |
| `t_ref_country_subdivision` | ISO 3166-2 | client-master（地址省州）、tax（`subdivision_code`） | SQL | **候选**：两个消费者落地时一起建；首批只装 AE / SA / EG |
| `t_ref_industry` | 平台行业分类（两级） | client-master（入驻选择）、compliance（CRR 行业因子）、commercial / risk（按行业统计与配置） | SQL | **新建** |
| `t_ref_mcc` | ISO 18245 / 卡组织 MCC 清单 | client-master、compliance（O04 指派）、acquiring、billing、payment、risk-decision、channel-connector（均已有 `mcc_code` 列） | SQL | **新建** |

### 7.1 `t_ref_currency`

| 字段 | 类型 | 用途 |
|---|---|---|
| `currency_code` | CHAR(3) | ISO 4217 字母码（原列名 `currency`，Q2） |
| `numeric_code` | CHAR(3) | ISO 4217 数字码，保留前导零；ISO 8583、EMV 3DS `purchaseCurrency` 用数字码 |
| `name` | VARCHAR(128) | ISO 英文名，如 `UAE Dirham` |
| `symbol` | VARCHAR(8) | 标准符号，如 `$`、`د.إ`、`﷼`；后端生成 PDF / 邮件等单据时用。前端页面优先用 `Intl.NumberFormat` |
| `minor_unit` | TINYINT | 已有：小数位 0–8 |
| `rounding_mode` | VARCHAR(64) | 已有：`HALF_UP` |
| `logo_file_address` | VARCHAR(64) NULL | 币种图标 |
| `status`、`version`、时间列 | | 已有 |

### 7.2 `t_ref_country`

| 字段 | 类型 | 用途 |
|---|---|---|
| `country_code` | CHAR(2) | ISO 3166-1 alpha-2，全平台存储与传输都用它 |
| `alpha3_code` | CHAR(3) | ISO alpha-3；护照 / 身份证 MRZ、部分渠道与筛查供应商接口使用 |
| `numeric_code` | CHAR(3) | ISO 数字码，保留前导零；EMV 3DS 的账单 / 商户国家字段用数字码 |
| `name` | VARCHAR(128) | ISO 英文短名，如 `Saudi Arabia` |
| `region_code` | VARCHAR(16) | 已有：平台区域 GCC / MEA / EUROPE / NORTH_AMERICA / LATAM / APAC |
| `calling_code` | VARCHAR(8) | 国际电话区号（不含 `+`），如 `966`；电话输入框默认前缀 |
| `default_currency_code` | CHAR(3) NULL | 法定货币，引用 `t_ref_currency`；入驻预计流水币种、出款账户币种的默认值。多币种国家取主币种 |
| `logo_file_address` | VARCHAR(64) NULL | 国旗 |
| `status`、`version`、时间列 | | 新增 |

### 7.3 `t_ref_country_subdivision`（候选）

| 字段 | 类型 | 用途 |
|---|---|---|
| `subdivision_code` | VARCHAR(6) | ISO 3166-2，如 `SA-01`、`AE-DU` |
| `country_code` | CHAR(2) | 所属国家 |
| `parent_subdivision_code` | VARCHAR(6) NULL | 有两级行政区的国家使用 |
| `category` | VARCHAR(32) | ISO 定义的类别：`EMIRATE`、`PROVINCE`、`GOVERNORATE`、`REGION` 等 |
| `name` | VARCHAR(128) | ISO 官方拉丁名 |
| `local_name` | VARCHAR(128) NULL | ISO 公布的本地文字官方名（如阿拉伯文）；它是标准数据，用于地址打印与登记信息比对，不是 UI 翻译 |
| `status`、`version`、时间列 | | |

### 7.4 `t_ref_mcc`

| 字段 | 类型 | 用途 |
|---|---|---|
| `mcc_code` | CHAR(4) | 保留前导零 |
| `name` | VARCHAR(128) | 英文短名，如 `Computer Software Stores` |
| `description` | VARCHAR(512) | 标准英文描述全文 |
| `network_high_risk` | TINYINT(1) | 卡组织公布的高风险 MCC（Visa High-Brand Risk / Mastercard 高风险类目），是外部事实；平台自己的禁止 / 审批 / 风险等级仍在 compliance / risk（Q9） |
| `status`、`version`、时间列 | | |

MCC 不放行业层级（7.6）。

### 7.5 `t_ref_industry`

见 7.6。

### 7.6 行业三级分类：行业 → 细类 → MCC

业界做法：客户面对的是"行业"，不是 MCC。Adyen 让商户选平台定义的 Industry Code 再映射到 MCC；Stripe 的 MCC 列表也按类别分组。所以三级是：**行业大类（Industry）→ 行业细类（Sub-industry）→ MCC**。客户在入驻时选到细类，系统给出建议 MCC，运营在 O04 确认或改选。

**建议：行业两级放 `t_ref_industry`（自引用父子），MCC 单独一张表，由细类指向 MCC；不把层级放进 MCC 表。** 理由：

1. **来源不同**：MCC 是 ISO / 卡组织标准，行业分类是平台自己制定的，修改节奏不同；混在一张表里，主编码格式也不统一（4 位数字和平台编码混用）。
2. **对应关系是多对一，方向是细类 → MCC**：多个细类会落到同一个 MCC（如"SaaS"和"在线软件下载"都可能是 5734 / 5817），反过来有些 MCC 不对应任何客户能选的细类（航空公司 3000–3299 段、平台禁止的类目）。如果在 MCC 表上挂 `parent`，一个 MCC 只能属于一个细类，表达不了这种关系。
3. **审核语义**：建议 MCC 只是默认值，最终 MCC 由运营确认（O04）并冻结在客户资格上，细类和 MCC 必须能独立变化。

`t_ref_industry` 字段：

| 字段 | 类型 | 用途 |
|---|---|---|
| `industry_code` | VARCHAR(64) | 平台编码，如 `RETAIL`、`RETAIL_APPAREL`；现有 yml 的一级编码沿用 |
| `parent_industry_code` | VARCHAR(64) NULL | 大类为空，细类指向大类 |
| `level` | TINYINT | 1 = 大类，2 = 细类；只有两级 |
| `name` | VARCHAR(128) | 英文名 |
| `description` | VARCHAR(512) NULL | 英文说明（给客户选择时的提示：包括什么、不包括什么） |
| `default_mcc_code` | CHAR(4) NULL | 只有细类有：建议 MCC，引用 `t_ref_mcc` |
| `logo_file_address` | VARCHAR(64) NULL | 行业图标（入驻页选择卡片） |
| `sort_no` | INT | 同级显示顺序 |
| `status`、`version`、时间列 | | |

例子：

| 大类 | 细类 | 建议 MCC |
|---|---|---|
| `RETAIL` Retail | `RETAIL_APPAREL` Apparel & accessories | 5651 |
| `RETAIL` | `RETAIL_ELECTRONICS` Electronics | 5732 |
| `DIGITAL_GOODS` Digital goods | `DIGITAL_GAMES` Games & in-game items | 5816 |
| `SOFTWARE_SAAS` Software & SaaS | `SAAS_SUBSCRIPTION` SaaS subscriptions | 5817 |
| `EDUCATION` Education | `EDU_ONLINE_COURSES` Online courses | 8299 |
| `TRAVEL` Travel | `TRAVEL_AGENCY` Travel agencies | 4722 |

（MCC 取值为示意，初始数据随 Q6 一起由合规确认。）

#### 7.6.1 PayerMax 式"子 MCC"（6 位）与本设计的关系（2026-10-27 第三轮）

用户看到 PayerMax 的分类是 industry → MCC → subMCC，subMCC 为 6 位、明显是 PayerMax 自定义（卡组织 MCC 只有 4 位，公开文档未查到 PayerMax 的定义，以下按行业通行做法推断）。自定义子 MCC 通常为了三件事：

1. **MCC 太粗**：同一个 MCC 下风险差异很大（如 5816 数字游戏同时覆盖普通游戏与抽卡 / 虚拟币类；5967 覆盖多种高风险直销），PSP 需要更细的编码来区分准入、风控限额、保证金与费率。
2. **对接多套行业编码**：本地钱包与 APM（如支付宝、微信支付的经营类目，部分中东 / 东南亚钱包）有各自的行业编码与资质要求，PSP 用自己的细分码做中间键，分别映射到每个渠道的类目。
3. **资质要求**：不同细分行业要求不同证照（如医药、教育、旅游），细分码直接决定材料清单。

本设计的**行业细类**承担的就是同一个角色——比 MCC 更细的平台自定义分类，只是层级方向不同：PayerMax 把 MCC 放中间（子码挂在 MCC 下，一个子码只属于一个 MCC）；本设计把 MCC 放最后（细类给建议 MCC，运营在 O04 可改选）。选择后者是因为本平台是 MoR：交易实际使用的 MCC 是 OE 在渠道下的 MID 上批准的 MCC，客户的行业只决定能用哪些 MID，因此细类与最终 MCC 要能独立变化。

结论：**不另建 6 位子 MCC。** 行业细类（`industry_code`）就是平台最细的分类，风控准入、费率、资质要求都以它为键（③，各 Owner 配置）。将来接入要求自有类目的渠道（例如 PayerMax 作为上游需要报送其 subMCC，或本地钱包类目），在 channel-connector 建"渠道 × `industry_code` → 渠道类目编码"的映射，属于渠道配置，不进 `t_ref_*`（Q11）。

不进 `t_ref_industry` 的：行业是否允许入驻、是否需要审批、行业风险等级——这些是 compliance / risk 的业务目录（③），以 `industry_code` 为键另行配置。客户资格上冻结 `industry_code` 与运营确认的 `mcc_code` 两个值。

## 8. 读取方式

消费方一律只存编码字符串（开放集合，按 `DEVELOPMENT_CONVENTIONS` 第 4 节不建 Java 枚举），按使用场景选一种读法：

| 读法 | 何时用 | 例子 |
|---|---|---|
| **A. 直接调 API** | 低频、不在事务内、Platform 暂不可用时可以失败重试 | Card 导入 BIN 读国家；onboarding 提交 / 审核时校验国家、行业、MCC、行政区；ops-bff、client-bff 下拉 |
| **B. 进程内快照** | 在高频路径或**事务内**读取，或 Platform 不可用时业务也不能停 | ledger / billing 过账与舍入读币种 |
| **C. 写入时冻结编码** | 业务事实需要固定当时的编码 | 订单冻结 `mcc_code`、Payment Intent 冻结 MCC、客户资格冻结 `industry_code` + `mcc_code` |

规则：

- 校验在**事务外**完成（事务公理）；事务内只能读快照或已冻结的编码。
- 停用的编码不拦截既有数据的读取与后续处理，只拦新用途（与币种一致）。
- 未知编码：A 读法拒绝本次请求并给出明确错误；B 读法拒绝并请求刷新（与币种一致）。

### 8.1 什么时候抽通用快照组件

现在 ledger 与 billing 各有一份几乎相同的快照实现（快照类、`@Scheduled` 刷新、健康检查，合计约 350 行）。满足**任一条件**时，在触发它的那个需求里抽成 Foundation 小组件（如 `ReferenceSnapshot<T>`：启动加载、首次成功前不就绪、定时全量刷新、失败保留旧快照、原子替换、健康指标），并同时迁移 ledger、billing：

1. 出现**第三个**需要 B 读法的消费方；或
2. 出现**第二种**需要 B 读法的参考数据（例如国家或 MCC 也要在事务内读）。

onboarding 读国家 / 行业 / MCC / 行政区都在提交与审核时，属于 A 读法，**不触发**抽组件。

## 9. 维护

- **默认 SQL**：初始化 DML 归入 `platform-support-initial-data.sql`（每服务一个初始化文件），变更按 `changes/` 手工评审执行并记录回退。logo 文件先经文件服务上传到公开桶，DML 里只写 `/files/v1/{fileId}`。
- **维护页**：只有运营确实需要在线增改时才做，沿用币种流程——新建草稿、修改草稿、启用、停用、再启用；Platform 再次校验权限并写 `t_audit_event`。行业分类是平台自定义、调整频率最高的一类，最可能先需要维护页（Q10）。
- 主编码一旦发布不可改、不可删、不复用；外部标准撤销某编码时改为 `INACTIVE`。

## 10. 前后端分工：后端管数据，前端管国际化与展示

| | 后端（Platform 与各业务系统） | 前端（Web 与 BFF 的展示层） |
|---|---|---|
| 存什么 | 编码、标准属性、标准英文名 `name`、`logo_file_address`、状态 | 不存参考数据；不维护"编码 → 中文名"映射表 |
| 返回什么 | 原样返回上述数据，**不返回本地化名称**，不做格式化 | — |
| 名称 | — | 国家、币种：`Intl.DisplayNames`；行业、MCC、行政区：i18n `refdata` 命名空间 `refdata.<type>.<code>`；都缺失时回退后端 `name`，再缺回退编码 |
| 金额 / 符号 | 后端生成单据时用 `symbol` 与 `minor_unit` | 页面金额用 `Intl.NumberFormat(locale,{currency})` |
| logo | 只存文件地址 | 由文件服务解析公开 URL 渲染；为空用内置图标或占位 |
| 排序 | 行业给 `sort_no` | ISO 类按本地化名称排序 |

后端生成给人看的单据（税务发票、通知邮件）需要本地化时，由生成单据的那个服务自行处理，不在参考数据范围。

## 11. 通用"简单码表"表（暂不建，保留）

Oracle EBS 式的 `t_ref_code_list`（`list_code` + `item_code` + `name` + `status` + `sort_no`）只在**同时满足**以下条件时考虑：

- 出现 **3 个以上**符合第 5 节准入、但**除编码与名称外没有任何属性**的参考码表；且
- 为每个各建一张表的代价（DDL、PDManer、Mapper、API）明显超过收益。

任何码表一旦需要属性（哪怕一列），就升级为专表。目前所有候选都有属性，所以不建。

## 12. client-onboarding 用到的数据归属

| 数据 | 类别 | 归属 | 说明 |
|---|---|---|---|
| 国家（国籍、居住国、注册国、出生国、税务居民地） | ② | `t_ref_country` | onboarding 存 CHAR(2) |
| 电话国际区号 | ② | `t_ref_country.calling_code` | 号码格式校验用库 |
| 支持入驻的国家 / 辖区 | ③ | client-master `t_jurisdiction` | `country_code` 引用 `t_ref_country` |
| 行政区（地址省州） | ② | `t_ref_country_subdivision`（候选） | 与 tax 一起建 |
| 地址格式组件 | ③ | client-master `t_address_format_component` | 平台采集规则 |
| 组织类型、标识类型、登记机关、经营模式 | ③ | client-master | 各辖区自定义，带校验规则 |
| 证件类型、材料槽、核验来源 | ③ | compliance / client-master | 程序版本的一部分 |
| 行业大类 / 细类 | ② | `t_ref_industry` | 取代 yml `industries`；客户选到细类 |
| MCC 码表 | ② | `t_ref_mcc` | 细类给建议 MCC；O04 由运营确认并校验存在 |
| 行业准入、需审批行业、行业 / MCC 风险等级 | ③ | compliance（CRR 因子）/ risk | 以 `industry_code`、`mcc_code` 为键，不进 `t_ref_*` |
| 国家风险等级 | ③ | compliance | 评估结论，不是国家属性 |
| 币种（预计流水币种） | ② | `t_ref_currency` | 默认取 `t_ref_country.default_currency_code` |
| 时区 | 库 | `ZoneId` | 不建表 |

## 13. 改名与影响

两张现有表都只在 `mor_platform`，其他系统都经 HTTP 读取，所以改名和加列只影响 Platform 内部（以及读取新增字段的消费方）：

| 改动 | 范围 |
|---|---|
| `t_currency_spec` → `t_ref_currency`；`t_country_region` → `t_ref_country`；唯一索引改为 `uk_ref_currency_code`、`uk_ref_country_code` | Platform DDL（`RENAME TABLE` + `ADD COLUMN` 变更脚本 + 回退）、PDManer、DataObject / Mapper XML、测试 `ddl.sql`、初始化 DML（补齐新列 249 + N 行） |
| 列名统一为 `<名词>_code`（`currency` → `currency_code`） | Q2 |
| 新增列对已有接口 | `CurrencySpecQueryApi`、`CountryRegionQueryApi` 的 DTO 只追加字段，现有消费方不受影响 |
| 接口路径 `/platform/v1/country-regions` | 保留或迁到 `/platform/v1/reference-data/countries`（Q5） |
| client-master `onboarding.form-catalog.industries` | 改读 `t_ref_industry`，yml 中删除该段 |
| arch-rules 表归属、`agent-docs/database/` 与 Platform 数据库文档、D41 表述、data-query 白名单（若已登记） | 同步修订 |

`t_ref_industry`、`t_ref_mcc`、`t_ref_country_subdivision` 随 client-onboarding 对应切片新建，不单独起需求。

## 14. 待定问题

| # | 问题 | 建议 |
|---|---|---|
| Q1 | 改名放哪个需求里做 | ~~随 onboarding 首个需要行业 / MCC 的切片~~ → **单独立项**（2026-10-27 修订：切片 1 已交付且在 client-master `t_catalog_item` 放入了行业 / 币种 / 省州）：[platform-support：参考数据目录与 client-master 迁移](../../requirements/20261027/platform-support-参考数据目录与client-master迁移/requirement.md) |
| Q2 | 列名是否统一为 `<名词>_code`（`currency` → `currency_code`） | 统一；DTO 字段名不变，只改 DataObject 映射 |
| Q3 | 行业、MCC、行政区名称的多语言 | 表存英文；其他语言放 i18n `refdata` 命名空间，key `refdata.<type>.<code>`，缺失回退英文 |
| Q4 | 行政区本地名 | 存 ISO 公布的 `local_name`（标准数据）；UI 翻译仍走 Q3；首批只装 AE / SA / EG |
| Q5 | 接口路径统一到 `/platform/v1/reference-data/*` | 新接口按此路径；`country-regions` 保留到 Card 迁移完再删 |
| Q6 | MCC 与行业分类的初始数据来源 | MCC 以 ISO 18245 + Visa / Mastercard 公开 MCC 清单的交集为准；行业分类参照 Adyen / Stripe 的分组自拟；需合规确认 |
| Q7 | 是否需要统一的 `ReferenceDataQueryApi`（一个接口返回多类） | 不需要；每类一个 QueryApi，类型清楚 |
| Q8 | 行业三级的表结构 | 建议 `t_ref_industry` 两级自引用 + `t_ref_mcc` 独立，细类 `default_mcc_code` 指向 MCC（7.6）；不在 MCC 表上挂父子 |
| Q9 | `t_ref_mcc.network_high_risk` 是否进表 | 进：它是卡组织公布的外部事实；平台自己的禁止 / 审批 / 风险等级仍归 compliance / risk |
| Q10 | logo 与行业维护是否要维护页 | 首期 SQL + 文件服务手工上传；行业分类调整频繁时再做维护页 |
| Q11 | 是否学 PayerMax 建 6 位子 MCC | 不建；行业细类即平台最细分类；渠道自有类目在 channel-connector 按 `industry_code` 映射（7.6.1）。若拿到 PayerMax 实际清单，再核对其细分粒度，用于校准我们的细类划分 |

## 15. 下一步

1. 用户确认本稿与 Q1–Q10。
2. 登记决定记录（修订 D41 表述、定义 `t_ref_` 前缀、准入条件、字段原则与前后端分工），在 `DATABASE_DESIGN_GUIDE` 命名一节加一行前缀规则，`AGENTS.md` 加一条路由。
3. 在 client-onboarding 对应切片的需求中执行改名、加列与新建（Q1）。
