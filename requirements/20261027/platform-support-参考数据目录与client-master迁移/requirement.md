# platform-support：参考数据目录与 client-master 迁移 · 需求

> 状态：**阶段 1 评审稿（2026-10-27）**。依据 [2026-10-27 参考数据决定](../../../agent-docs/decisions/2026-10-27-platform-reference-data.md)（已定）与 [design-discuss/reference-data](../../../design-discuss/reference-data/design.md)。确认记录见 [delivery.md](./delivery.md)；技术方案在阶段 2 起写入 [technique.md](./technique.md)。

## 1. 背景

- 2026-10-27 用户确认全平台参考数据决定：Platform 持有，每类数据一张 `t_ref_*` 专表，字段带齐行业通用的标准属性；后端只管数据，前端负责国际化与展示。决定原定的 Q1 是"随 onboarding 首个用到行业 / MCC 的切片实施"。
- 但 [全球合规入驻切片 1](../client-master-全球合规入驻与资格切片1/delivery.md) 已于同日交付（mor-test 已部署，提交 `9f506f0f`）。核对后发现它与该决定有三处冲突：
  1. client-master 新建的通用目录表 `t_catalog_item` 里装了参考数据，包括 `INDUSTRY`（10 项，一级）、`CURRENCY`（USD / AED / SAR / EGP，等于又建了一份币种副本）和 `STATE_OR_PROVINCE`（AE 7 项、EG 27 项，实为 ISO 3166-2）。
  2. 国家字段只校验 `[A-Z]{2}` 格式，不核对编码是否存在。
  3. O03 运营分类的 MCC 只校验"四位数字"，因为没有 MCC 码表可核对。
- 用户据此确认（2026-10-27）：参考数据目录**单独立项**；`t_catalog_item` 保留为 client-master 的业务目录表，但不得再放参考数据。

## 2. 目标

1. Platform 按决定建齐首批参考数据：`t_ref_currency`、`t_ref_country`（这两张由现有表改名并补列）、`t_ref_country_subdivision`、`t_ref_industry`、`t_ref_mcc`，并提供只读查询接口。
2. client-master 不再保存参考数据，行业、币种、行政区改为读 Platform；国家、行业、MCC、行政区在写入时核对编码是否存在。
3. 行业改为两级（大类 → 细类）；细类给出建议 MCC，运营在 O04 确认。
4. 两个控制台（Client、Operations）按决定的分工显示名称，并补上行业选择与 MCC 选择。

## 3. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| Client（入驻） | 在入驻表单选择国家、地址省州、预计流水币种、行业（如果 Schema 采集行业） | 选项来自 Platform；名称按界面语言显示；提交的编码一定存在且有效 |
| 运营（O03 / O04 分类） | 给客户确认行业细类与 MCC | 先选大类再选细类，系统带出建议 MCC；MCC 可以按编码或名称搜索改选；不存在的 MCC 无法保存 |
| 运营（平台配置） | 查看与维护币种；查看国家、行业、MCC、行政区 | 币种页显示新字段；其他参考数据可以只读查看 |
| 其他系统（ledger、billing、card） | 继续读币种和国家 | 现有接口照常可用，只多了字段，不需要改代码 |

## 4. 本期范围

| # | 范围 |
|---|---|
| R1 | Platform：`t_currency_spec` → `t_ref_currency`，`t_country_region` → `t_ref_country`；按决定补列，并回填初始数据 |
| R2 | Platform：新建 `t_ref_country_subdivision`（首批 AE / SA / EG）、`t_ref_industry`（两级，细类带 `default_mcc_code`）、`t_ref_mcc`，装入初始数据 |
| R3 | Platform：新增只读查询接口 `/platform/v1/reference-data/{currencies, countries, country-subdivisions, industries, mccs}`；现有 `CurrencySpecQueryApi`、`/platform/v1/country-regions` 保留且**响应结构不变**（阶段 2 S-01：消费方使用严格 JSON，不能追加字段），只改为读新表名 |
| R4 | Platform：币种维护（草稿编辑）支持新字段；参考数据的英文名作为 i18n 的英文基础文案，建 `refdata` 命名空间并补中文翻译 |
| R5 | client-master：从 `t_catalog_item` 删除 `INDUSTRY`、`CURRENCY`、`STATE_OR_PROVINCE` 三类项；Schema 的 `SELECT` 字段与地址组件改为引用 Platform 参考数据；国家、币种、行政区在写入时核对 Platform |
| R6 | client-master：O03 / O04 分类改为读 `t_ref_industry` 与 `t_ref_mcc`；新写入必须选到细类；MCC 必须存在于 `t_ref_mcc`；返回细类的建议 MCC |
| R7 | client-master：`t_catalog_item` 注释与准入规则写明"参考数据不得放入"，并加 auto 测试守住 |
| R8 | client-console-bff / web：入驻表单的国家、币种、省州、行业选项改为经 BFF 读 Platform；名称按决定的前后端分工显示 |
| R9 | operations-console-bff / web：分类页改成两级行业选择加 MCC 搜索；币种页显示与编辑新字段；新增"参考数据"只读查看页（国家、行政区、行业、MCC 四个页签） |
| R10 | 文档与工具：arch-rules 表归属、Platform 数据库文档与场景文档、card 列注释（`t_country_region` → `t_ref_country`）、`CURRENT_IMPLEMENTATION.md` |

## 5. 不做项

- 不抽取快照组件。本需求的所有消费方都属于读法 A（直接调接口），不满足抽取条件。
- card、ledger、billing 不改代码（接口只追加字段）；card 改用新路径另行处理。
- tax 不接入行政区（tax 还未实施）。
- 不做 `t_ref_industry` / `t_ref_mcc` / `t_ref_country_subdivision` 的在线维护页，本期用 SQL 维护（决定 Q10）。
- 不做 OE 收单范围（`t_operating_entity_scope`）与 commercial 中行业 / MCC 的存在性校验（Q5）。
- 不建 channel-connector 的渠道类目映射（决定第 6 条，渠道需要时另立）。
- 不改 `t_catalog_item` 的结构；也不迁移经营模式、自贸区、登记机关、职业、资金来源、性别、城市（这些是业务目录）。
- 不建 6 位子 MCC。
- 不迁移存量数据中的行业编码（Q2）。

## 6. 业务规则（草案）

| # | 规则 |
|---|---|
| B1 | 参考数据只在 `mor_platform`；client-master 只保存编码字符串，不保存名称、副本或快照 |
| B2 | 写入时校验在事务外完成：先调 Platform 查询编码，再开本地事务；Platform 不可用时，本次写入失败并返回明确错误，不放行 |
| B3 | 只有 `ACTIVE` 编码能被新写入选中；`INACTIVE` 编码不影响已有数据的读取与展示 |
| B4 | 新的行业写入必须是细类（`level=2`）；只选到大类的提交被拒绝 |
| B5 | 建议 MCC 只是默认值：运营可以改选任一 `ACTIVE` MCC，客户资格最终冻结 `industry_code` + `mcc_code` |
| B6 | 行政区的选项按国家过滤，只列出该国 `ACTIVE` 的行政区；地址国家和省州必须属于同一国家 |
| B7 | 后端不返回本地化名称。前端显示名称的顺序：国家和币种用 `Intl.DisplayNames`；行业、MCC、行政区用 i18n `refdata.<type>.<code>`；取不到时回退后端的 `name`，再回退编码 |
| B8 | `t_catalog_item` 只放 client-master 自己的业务目录；新增目录前必须确认不属于参考数据 |

## 7. 逐系统变动点（阶段 1 清单，阶段 2 核对代码后修订）

### 7.1 platform-support（`mor_platform`）

| 类别 | 变动点 |
|---|---|
| 表 | `RENAME TABLE t_currency_spec → t_ref_currency`：列 `currency` → `currency_code`；新增 `numeric_code`、`name`、`symbol`、`logo_file_address`；唯一索引改为 `uk_ref_currency_code` |
| 表 | `RENAME TABLE t_country_region → t_ref_country`：新增 `alpha3_code`、`numeric_code`、`name`、`calling_code`、`default_currency_code`、`logo_file_address`、`status`、`version`；唯一索引改为 `uk_ref_country_code` |
| 表 | 新建 `t_ref_country_subdivision`、`t_ref_industry`、`t_ref_mcc`（字段见决定第 5 条与设计稿第 7 节） |
| 数据 | 回填 249 个国家的三位码、数字码、英文名、区号、法定货币；按需激活或新增币种（入驻要用 AED / SAR / EGP，Q6）；AE / SA / EG 的行政区；两级行业与建议 MCC；MCC 清单；国旗 logo（Q3） |
| 代码 | `referencedata` 模块：DataObject / Mapper / Repository 改名与新增；5 个新查询接口（`*QueryApi` + Controller + Processor）；`CurrencySpecQueryApi` 与 `CountryRegionQueryApi` 的响应追加字段；币种维护的草稿编辑支持新字段 |
| 模型 | PDManer `platform-support.pdma.json`、`platform-support-schema.sql`、`changes/` 变更脚本（含回退）、`platform-support-initial-data.sql`、测试 `ddl.sql` |
| 多语言 | 新建 `refdata` 命名空间，导入行业、MCC、行政区的英文基础文案和中文翻译，并发布语言包 |
| 注册 | ops-bff 新增的路由、菜单与权限（参考数据查看）；块号在阶段 2 核对 |
| 测试 | 新接口与改名表的 auto 测试；币种维护的回归测试 |

### 7.2 client-master（`mor_client_master`）

| 类别 | 变动点 |
|---|---|
| 数据 | 从 `t_catalog_item` 删除 `INDUSTRY`、`CURRENCY`、`STATE_OR_PROVINCE` 共 48 行；Schema 字段 `business.currency`（8 版）的 `catalog_code` 与地址组件 `stateOrProvince` 的 `catalog_code` 改为指向参考数据（引用方式在阶段 3 设计） |
| 代码 | 新增 Platform 参考数据客户端（`platform-support-api` 的 HttpExchange）；`ClientDraftPreparer` / `ClientDraftPatch` 的 COUNTRY / CURRENCY / 省州校验改为核对 Platform；`ReplaceClientClassificationProcessor` 改为校验细类和 MCC；分类选项接口（`ClientClassificationAdminController#options`）改为返回行业树与建议 MCC |
| 规则守卫 | `t_catalog_item` 注释更新；新增 auto 测试，禁止三类参考数据目录码再出现在 `t_catalog_item` 初始数据中 |
| 测试 | 外部 HTTP DTO Mock（Platform 参考数据接口）；覆盖不存在 / 停用编码、只选大类、Platform 不可用 |
| 模型 | PDManer、schema 注释、`client-master-initial-data.sql`、变更脚本 |

### 7.3 client-console-bff / client-console-web

| 类别 | 变动点 |
|---|---|
| BFF | 入驻 Schema 与选项接口：国家、币种、省州、行业的选项改为从 Platform 聚合；只返回编码、`name`、logo 地址，不返回本地化名称 |
| Web | `widgets.tsx` 的国家选项改用 Platform 返回的 `ACTIVE` 国家（名称仍用 `Intl.DisplayNames`，显示国旗）；币种选项与名称用 Intl；省州按国家过滤并用 i18n `refdata`；如果 Schema 采集行业，改为两级选择卡片 |
| 多语言 | 删除 `onboardingSchema.catalog.{INDUSTRY,CURRENCY,STATE_OR_PROVINCE}.*` 键，改用 `refdata` 命名空间 |
| 测试 | 单元测试与浏览器冒烟（en / zh-CN，1440 / 390） |

### 7.4 operations-console-bff / operations-console-web

| 类别 | 变动点 |
|---|---|
| BFF | 分类选项改为行业树与 MCC 搜索（转发 Platform）；新增参考数据只读接口（国家、行政区、行业、MCC）；币种接口追加字段 |
| Web | 分类页：先选大类再选细类，带出建议 MCC，MCC 可以搜索改选；平台配置 → 币种：显示与编辑新字段；新页"参考数据"（四个页签，只读、可搜索） |
| 多语言 | 新页面文案；名称走 `refdata` |
| 测试 | 单元测试、tsc、build、浏览器冒烟 |

### 7.5 不改代码但受影响

| 系统 | 影响 |
|---|---|
| ledger、billing | `CurrencySpecQueryApi` 响应结构不变（S-01），快照代码无需修改；需回归验证启动与刷新 |
| card | `/platform/v1/country-regions` 保留，响应结构不变（S-01）；回归 BIN 导入；schema 注释里的表名同步 |
| compliance | 目前不读行业或 MCC，不受影响 |
| arch-rules | `table-ownership.yaml` 更新表名与新表 |

## 8. 验收标准

### 功能

| # | 验收 |
|---|---|
| AC-01 | `mor_platform` 中不再有 `t_currency_spec` 和 `t_country_region`，改为 `t_ref_currency` 和 `t_ref_country`，列与索引和决定一致；PDManer、schema、测试 DDL 三者一致 |
| AC-02 | ledger、billing 重启后币种快照加载成功、过账正常；card 导入 BIN 仍能推导发卡区域 |
| AC-03 | 5 个新接口返回完整字段；`status` 筛选正确；不返回任何本地化名称 |
| AC-04 | 国家初始数据 249 行全部带三位码、数字码、英文名、区号；有法定货币的国家都填了 `default_currency_code` |
| AC-05 | 行业两级数据可用，每个细类都有存在且 `ACTIVE` 的建议 MCC |
| AC-06 | client-master 的 `t_catalog_item` 中不再有 `INDUSTRY`、`CURRENCY`、`STATE_OR_PROVINCE`；守卫测试能拦住重新加入 |
| AC-07 | 入驻提交时，不存在或停用的国家、币种、省州被拒绝，错误挂在对应字段上；省州与国家不匹配时被拒绝 |
| AC-08 | O03 / O04：只选大类被拒绝；不存在的 MCC 被拒绝；选细类后带出建议 MCC；改选后保存的是运营选择的 MCC |
| AC-09 | Platform 参考数据接口不可用时，入驻提交和分类保存都返回明确错误，不写入任何数据 |
| AC-10 | 存量客户资格、草稿里的旧行业编码（大类）仍能正常显示 |

### 视觉 / 交互

| # | 验收 |
|---|---|
| AC-11 | Client 入驻：国家下拉显示国旗和本地化名称；省州随国家切换；币种显示本地化名称；中英文、桌面与移动端都正确 |
| AC-12 | 运营分类页：两级行业选择清晰，建议 MCC 有明确标识，MCC 搜索支持编码和名称；保存失败时错误显示在对应控件上 |
| AC-13 | 运营"参考数据"页：四个页签可以搜索和筛选状态，空状态与加载失败有明确提示；币种页新字段可以编辑（仅 DRAFT） |
| AC-14 | 翻译缺失时显示英文 `name`，不显示原始的 i18n key |

## 9. 待确认问题（阶段 1）

| # | 问题 | 建议 |
|---|---|---|
| Q1 | 行业细类清单与建议 MCC 的初始版本 | 阶段 3 给出草案（在现有 10 个大类下各拆 2—5 个细类），MCC 取值以 Visa / Mastercard 公开清单为准；test 环境先用，生产前由合规确认（决定第 10 条） |
| Q2 | 存量数据中只到大类的行业编码怎么处理 | 不迁移，保留可读（AC-10）；下次修改分类时必须选到细类 |
| Q3 | logo 初始数据 | 国旗用 MIT 许可的开源 SVG 国旗集，上传到文件服务公开桶后写入地址；币种和行业图标本期留空，前端用内置图标兜底 |
| Q4 | 运营端是否要做"参考数据"只读页 | 做（R9），方便核对数据；维护仍用 SQL |
| Q5 | OE 收单范围与 commercial 是否也校验行业和 MCC 存在 | 本期不做，另立需求 |
| Q6 | 入驻的预计流水币种选项从哪里来 | 取 Platform 中全部 `ACTIVE` 币种；为此在 `t_ref_currency` 中激活 AED / SAR / EGP（激活只是允许新用途，不影响已有账簿）。如果需要按辖区限制，属于 client-master 的业务规则（`t_rule_setting`），本期不做 |
| Q7 | 改名需要 Platform 镜像与 SQL 同时切换，旧 Platform 无法读取新表名 | 发布按"SQL 与 Platform 镜像同一窗口切换"处理，并备好回退（反向 RENAME + 旧镜像）；阶段 6 写入运行卡 |

## 10. 阶段安排（建议）

默认八阶段。需要单独授权的动作：

- 阶段 4：在本地 `mor_platform`、`mor_client_master` 执行 SQL
- 阶段 6—7：mor-test 的 SQL、镜像、语言包和注册块
- 阶段 8：提交（不含推送）

涉及页面，阶段 3 交付原型：client-console 的入驻控件和 operations-console 的分类页与参考数据页。
