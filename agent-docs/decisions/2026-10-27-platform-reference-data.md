# 2026-10-27 决定：全平台参考数据——Platform 持有、每类一张 `t_ref_*` 专表、带齐标准属性、后端管数据 / 前端管展示

状态：已定（2026-10-27，用户三轮讨论后确认，Q1—Q11 全部按建议；同日 Q1 修订为单独立项，见"实施方式"）。讨论与完整字段设计见 [design-discuss/reference-data](../../design-discuss/reference-data/design.md)（配图 `reference-data.pen` 五张画板）。本决定不证明任何实现；实施、验证与部署状态只记录在 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。

## 背景

client-onboarding 设计中发现：行业、组织类型等目录写在 client-master `application.yml`；7 个服务、10 余个 `mcc_code` 列的注释称"取值来源：MCC 目录"，但该目录不存在；约 30 个国家列没有统一的合法性来源；`t_currency_spec`、`t_country_region` 已在 Platform，但命名、字段与读取方式没有统一规则。

## 取代与修订范围

- 修订 [目标架构 V2 决定](./2026-10-17-target-architecture-v2.md) D41 的表述："全平台参考数据"的范围、命名、准入与字段规则以本决定为准；D41 的 Owner 归属（Platform 持有并提供查询 API）不变。
- [币种快照读取决定](./2026-10-20-currency-spec-platform-snapshot-read.md)继续有效，只是表名改为 `t_ref_currency`、增加标准属性列；其读取方式成为本决定的读法 B。
- 不取代 [Risk 规则字典决定](./2026-09-11-risk-rule-history-and-dictionaries.md)：Risk 拒绝原因、主体标签仍是 Risk 自有业务字典。
- 不取代 [多语言决定](./2026-10-22-ui-text-localization.md)：Locale 仍归 Platform 多语言模块；参考数据名称的翻译走其 i18n 机制。

## 决定

1. **不建通用 KV 字典。** 参考数据不建 `dict_type` + `dict_item` 式通用表；每类参考数据一张有类型的专表。业务 Owner 可以用自己的通用目录表放本系统的简单业务目录（如 client-master `t_catalog_item` 的经营模式、自贸区、登记机关），但**不得把参考数据放进业务 Owner 的目录表**。Oracle EBS 式"简单码表"通用表暂不建、保留为可选项：仅当出现 3 个以上除编码与名称外没有任何属性的参考码表时再评估。
2. **定义与准入。** 参考数据 = 外部标准（ISO、卡组织、IANA 等）或平台为全系统统一制定的分类，同时满足：至少两个系统真实消费；不带任何业务规则；没有更合适的标准库或已有 Owner。不满足的去向：单系统使用留在该系统；带规则的目录（支付方式、拒绝原因、证件类型、组织类型、登记机关、行业准入 / 禁止 / 风险等级）留业务 Owner；时区用 JDK / 浏览器，Locale 归多语言模块。
3. **归属与命名。** 只在 `mor_platform`，Platform 唯一持有。物理表 `t_ref_<单数名词>`，主编码 `<名词>_code`（`ascii_bin`，唯一索引 `uk_ref_<名词>_code`，发布后不改、不删、不复用）。业务 Owner 的目录不得使用 `t_ref_` 前缀。包 `com.paytech.platform.support.api.referencedata`，新接口路径 `/platform/v1/reference-data/<复数名词>`，每类一个 QueryApi，不做统一的多类查询接口。
4. **字段带齐标准属性。** 不只是 code + value：按标准本身定义的属性、主流系统（SAP、Odoo、Stripe、Adyen）普遍建的列和本平台已知场景设计字段。所有表统一有 `name`（标准英文名，是数据不是 UI 文案）、`status`（`DRAFT` / `ACTIVE` / `INACTIVE`）、`version`、`create_time` / `update_time`；有图形标识的表有 `logo_file_address`（文件服务地址 `/files/v1/{fileId}`，公开桶，遵守[文件地址原则](./2026-10-22-file-upload-address-principles.md)）；平台自定义顺序的表有 `sort_no`。不存翻译、格式化规则或业务规则。启用后冻结主编码与标准属性；停用只拦新用途。
5. **首批目录。**

   | 表 | 来源 | 处理 |
   |---|---|---|
   | `t_ref_currency` | ISO 4217 | 由 `t_currency_spec` 改名；列 `currency` → `currency_code`；增 `numeric_code`、`name`、`symbol`、`logo_file_address` |
   | `t_ref_country` | ISO 3166-1 + 平台区域 | 由 `t_country_region` 改名；增 `alpha3_code`、`numeric_code`、`name`、`calling_code`、`default_currency_code`、`logo_file_address`（国旗）、`status`、`version` |
   | `t_ref_country_subdivision` | ISO 3166-2 | 候选：client-master 地址与 tax 落地时建；含 `parent_subdivision_code`、`category`、`name`、`local_name`（ISO 公布的本地文字官方名）；首批 AE / SA / EG |
   | `t_ref_industry` | 平台行业分类 | 新建：两级自引用（`parent_industry_code`、`level`），细类带 `default_mcc_code`（建议 MCC）、`description`、`logo_file_address`、`sort_no`；取代 client-master yml `industries`，现有编码沿用为大类 |
   | `t_ref_mcc` | ISO 18245 / 卡组织清单 | 新建：`mcc_code` CHAR(4)、`name`、`description`、`network_high_risk`（卡组织公布的高风险 MCC，外部事实） |

6. **行业三级 = 大类 → 细类 → MCC。** 细类是平台最细的行业分类；MCC 不挂父子层级。细类只给建议 MCC，运营在 O04 确认或改选，客户资格冻结 `industry_code` + `mcc_code`。不建 PayerMax 式 6 位子 MCC：准入、费率、资质以 `industry_code` 为键由各 Owner 配置；渠道要求自有类目时，在 channel-connector 建"渠道 × `industry_code` → 渠道类目"映射，不进 `t_ref_*`。
7. **读取方式。** 消费方只存编码字符串（开放集合，不建 Java 枚举）。A 直接调 API（低频、事务外）；B 进程内快照（高频或事务内，沿用币种决定）；C 写入时冻结编码。校验在事务外完成。读法 B 一律使用 Platform 提供的参考数据客户端 `paytech-platform-support-reference-data-client`（2026-10-27 [参考数据客户端组件需求](../../requirements/20261027/platform-support-参考数据客户端组件/requirement.md)修订本条：原定"抽为 Foundation 组件"改由 Platform 持有）：按配置加载所需种类，默认每 1 小时全量刷新（可配置；首次成功前按短周期重试），读方法只读内存、查不到即报错、从不回源，不使用 Caffeine。读法 A 只保留给 BFF 转发与运营页面。
8. **维护。** 默认评审过的初始化 SQL 与 `changes/` 手工变更；logo 先上传文件服务，DML 只写文件地址。确有运营需要时才做维护页，沿用币种的草稿 / 启用 / 停用流程与 `t_audit_event` 审计；行业分类最可能先需要。
9. **前后端分工。** 后端（Platform 与业务系统）只管数据：存并原样返回编码、标准属性、`name`、logo 地址与状态，不返回本地化名称、不做格式化。前端负责国际化与展示：国家 / 币种名称用 `Intl.DisplayNames`，行业 / MCC / 行政区名称用 i18n `refdata.<type>.<code>`，缺失回退 `name`、再回退编码；金额用 `Intl.NumberFormat`；logo 经文件服务解析公开 URL 渲染。后端生成给人看的单据需要本地化时由生成单据的服务自行处理。
10. **初始数据。** MCC 以 ISO 18245 与 Visa / Mastercard 公开 MCC 清单的交集为准；行业分类参照 Adyen / Stripe 分组自拟；上线前需合规确认可商用。

## 实施方式

- **单独立项（2026-10-27 修订原 Q1）：** client-onboarding 切片 1 已于同日交付（`9f506f0f`），并在 client-master `t_catalog_item` 中放入了 INDUSTRY / CURRENCY / STATE_OR_PROVINCE，国家只校验格式、MCC 只校验四位数字。因此改名、加列、新建与 client-master 迁移统一在 [platform-support：参考数据目录与 client-master 迁移](../../requirements/20261027/platform-support-参考数据目录与client-master迁移/requirement.md) 中完成（DDL `RENAME TABLE` / `ADD COLUMN` + 回退、PDManer、DataObject / Mapper、测试 DDL、初始化 DML、arch-rules 表归属、数据库文档）。
- 已有 `CurrencySpecQueryApi`、`CountryRegionQueryApi` 只追加字段；`/platform/v1/country-regions` 保留到 Card 迁移到新路径后再删除。
- `t_ref_country_subdivision` 随上述需求一起建（client-master 地址省州已是真实消费者），tax 以后直接读取。

## 影响

- [`DATABASE_DESIGN_GUIDE.md`](../DATABASE_DESIGN_GUIDE.md) 第 8 节增加 `t_ref_` 前缀规则；[`DOCUMENT_GOVERNANCE.md`](../DOCUMENT_GOVERNANCE.md) 增加主题权威；`AGENTS.md` 增加路由。
- client-onboarding 设计中的行业、MCC、国家、行政区引用按本决定落位（见设计稿第 12 节）。
