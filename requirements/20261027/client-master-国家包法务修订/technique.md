# 技术方案：client-master 国家包法务修订

- 状态：阶段 2+3 评审稿（2026-10-27）。需求见 [requirement.md](./requirement.md)。
- **规模（本次方案）：** 涉及 5 个系统（client-master、Client BFF / Web、Operations BFF / Web）及 Platform 语言包；新增接口 0，修改接口 3 个（表单 Schema、申请资料、声明写入，均为加字段或加校验）；新增表 0，修改表 2 张各加 1 列，索引 0；增量 DML 新增标识类型 3、属性定义 4、目录项 1、规则开关约 14、Schema 9 个版本及其字段约 300 行（阶段 4 精确统计），更新 Schema 8 行（v1 退役）；页面新增 0，修改 3 处（Client 声明步骤、Client 关系人 / 起步页文案、运营案件详情新增声明区块）。compliance 不改（见 2.2）。

## 1. 已读规范与入口

- 规范：[DEVELOPMENT_CONVENTIONS.md](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[JAVA_PROJECT_STRUCTURE.md](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)、[WRITE_API_EXAMPLE.md](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)、[DATABASE_DESIGN_GUIDE.md](../../../agent-docs/DATABASE_DESIGN_GUIDE.md)、[UNIT_TEST_GUIDE.md](../../../agent-docs/UNIT_TEST_GUIDE.md)、[多语言决定](../../../agent-docs/decisions/2026-10-22-ui-text-localization.md)。实施前（阶段 4）按 AGENTS.md 要求重新完整读取并列出。
- 代码入口：`RecordApplicationDeclarationProcessor`、`SubmitClientApplicationProcessor`、`OnboardingRequirementEvaluator`、`RelationRules`、`RuleCode`、`IdentifierRules`、`FormSchemaConverter`、8 个草稿写入 Processor（见 3.3）；Client Web `onboarding/DeclarationsTask.tsx`、`progress.ts`、`PeopleTask.tsx`、`StartFlow.tsx`；运营 Web `clientReview/ClientReviewDetail.tsx`。

## 2. 现状差异与影响

### 2.1 需求项差异

| 需求项 | 当前行为及证据 | 目标 | 缺口 | 处理 |
|---|---|---|---|---|
| S-1 AE ERN / 其他机关 | 签发机关目录 8 项（`REGISTRATION_AUTHORITY.AE_*`），无 ERN | ERN 可选；"其他"+机关名称 | 数据缺失 | DML；条件必填用现有 `visible_when`（后端 `OnboardingRequirementEvaluator.visible`、前端 `schema.ts` 均已支持） |
| S-2 SA 年度确认 | SA 机构 Schema 有 `organization.licenseExpiryDate`（OPTIONAL） | 去掉，换年度确认日 | 数据 | DML（v2 不含该字段） |
| S-3 SA Freelance | SA 个人只采集 NID / Iqama / 护照；`FREELANCER` 组织类型存在但无专用 Schema、无 FL 号 | 专用 Schema + FL 号必填 | 数据 | DML；规范化 `ClientIdentifierNormalizer` 去掉空格与连字符并转大写，`FL-ABC123` → `FL ABC123` → `FLABC123`，正则写 `FL[0-9A-Z]{1,30}` |
| S-4 EG 登记号 | 单一 `EG_COMMERCIAL_REGISTER`（`[0-9]{1,15}`） | 统一号 / 本地号至少一项 + 增值税号 | 数据 | DML；"至少一项"用现有 `requirement_group`（与 `PERSON_ID` 同机制） |
| S-5 声明拆分 | 声明类型来自 Schema 字段 `declaration.*`，代码无写死 | 三项 / 两项 | 数据 + 文案 | DML + 语言包 |
| S-6a 条款版本 | BFF 已接受可空 `termsVersion`，**前端从未发送**，库中全为空；服务端不知道"当前条款版本" | 记录实际展示的版本，空或过期拒绝 | 合同不支持 | 规则开关承载文档版本（3.2）；表单 Schema 回传；前端发送；服务端比对 |
| S-6b 资料版本 | `t_client_application` 无资料版本；声明行无绑定；声明后改资料，声明继续有效 | 声明绑定资料版本，改动后失效 | 模型与代码缺失 | 加 2 列 + 8 个写入点递增 + 评估器新错误码（3.3） |
| S-7 比较方式 | 门槛值可配，`>=` 写死于 `RelationRules.java:93`；前端文案写死"或以上" | `GTE` / `GT` 可配 | 代码 | 新规则码 + 表单 Schema 回传 + 文案两套 |
| AC-08 运营可见 | **运营案件详情目前完全不展示声明**；数据来自 client-master `GetClientReviewApplicationProcessor`（返回与 Client 相同的 `ClientDraftResponseBody`，含声明） | 运营能看到声明、版本、是否针对提交版本 | 页面缺失 | 运营 BFF 透传、Web 新增声明区块；不经 compliance |

### 2.2 与阶段 1 的两处出入（请确认）

1. **compliance 不改。** compliance 的审核登记接口虽然接收 `declarations`，但代码中没有任何读取、存储或展示（只在 API DTO 中出现）。给它加版本字段没有消费者，违反"字段要有消费者"的原则。运营查看声明本来就读 client-master。故 requirement 第 4 节 compliance 行改为"不改"，AC-08 改为"运营案件详情展示声明及其版本"。
2. **声明失效规则对 v1 申请也生效。** 阶段 1 的 AC-10 写了"v1 申请行为不变"。但如果只对 v2 生效，处于 RETURNED 的 v1 申请补件重提时会带着补件前的旧声明提交，正是法务第 23、25 项要堵的漏洞。建议：v1 申请的**表单**不变，**声明失效规则**对所有申请生效；条款版本校验只对配置了文档版本的声明（即 v2 新声明）生效，v1 的 `PLATFORM_TERMS` 不受影响。存量声明行资料版本为空，视为 0，与存量申请的初始资料版本 0 相等，部署后到下一次改资料之前仍然有效。

### 2.3 逐系统影响

| 系统 | 职责 | 影响点 | 改代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|---|
| client-master | 国家包、申请、声明的 Owner | 2 列 DDL；增量 DML；声明写入、提交校验、8 个写入点、规则码、表单 Schema 与申请资料响应 | 代码 + 模型 + DML | 与参考数据客户端组件需求同改 client-master，合并与部署需错开；Schema v2 复制自**线上当前 v1**（已含参考数据需求的 `REF_CURRENCY` 改动） |
| client-console-bff | 透传 | 声明响应、申请资料、表单 Schema 的新字段 | 代码（DTO 来自 client-master-api，重编译；如有本地映射则补字段） | 无新路由 |
| client-console-web | 申请页面 | 声明步骤（三项、可阅读文档、失效提示）、关系人与起步页门槛文案 | 代码 + 文案 | 法律文本为占位草案 |
| operations-console-bff | 透传 | 案件申请资料中的声明字段；如有响应形状校验器需放行新字段 | 代码 | 无新路由 |
| operations-console-web | 运营审核 | 案件详情新增"声明"区块；新 Schema 字段由现有 Schema 驱动渲染 | 代码 + 文案 | 布尔值以文字显示 |
| compliance | 案件 | 不改（2.2） | 无 | — |
| Platform | 语言包、路由 | client-console、operations-console 各发布一个新语言包版本；路由与权限 0 改动 | 语言包 DML | 版本号接在参考数据需求 #6 / #13 之后，届时以线上最新为准 |

## 3. 方案

### 3.1 国家包增量 DML（client-master）

ID 段：本需求固定使用 `9270000000900000`—`9270000000999999`，不与切片 1 的 `…100000—…899999` 重叠，回退可按段删除。

| 表 | 新增 / 更新 | 内容 |
|---|---|---|
| `t_identifier_type` | +3 | `SA_FREELANCE_DOCUMENT`（SA / INDIVIDUAL，`FL[0-9A-Z]{1,30}`，校验位 NONE，签发范围 NONE）；`EG_UNIFIED_CR`（EG / ORGANIZATION，`[0-9]{15}`，NONE）；`EG_LOCAL_CR`（EG / ORGANIZATION，`[0-9]{1,15}`，签发范围 AUTHORITY）。`EG_COMMERCIAL_REGISTER` 保留 ACTIVE 供 v1 申请 |
| `t_attribute_definition` | +4 | `AE_ERN`（TEXT，`[A-Z0-9-]{1,30}`，PUBLIC）；`AE_LICENSE_AUTHORITY_NAME`（TEXT，≤128，PUBLIC）；`SA_CR_ANNUAL_CONFIRMATION_DATE`（DATE）；`EG_VAT_REGISTRATION_NUMBER`（TEXT，`[A-Z0-9-]{1,30}`）。号码均不加未经确认的前缀或校验位 |
| `t_catalog_item` | +1 | `REGISTRATION_AUTHORITY.AE_OTHER`（排序最后） |
| `t_rule_setting` | 约 +14 | `UBO_THRESHOLD_COMPARISON`=GTE ×3 辖区；3 个新标识类型各 `ACQUIRE_MODE`=MANUAL_ENTRY、`VAULT_REQUIRED`=0、`EXPIRY_REQUIRED`（FL=1，EG 两种=0）；声明文档版本 `DOCUMENT_VERSION`=`2026-10-draft` ×2（`PLATFORM_TERMS_ACCEPTANCE`、`PRIVACY_NOTICE_ACKNOWLEDGED`，见 3.2） |
| `t_onboarding_schema` | +9，更新 8 | 8 个现有 Schema 各发布 `version_no=2`（ACTIVE，slot 1），对应 v1 改为 RETIRED、slot NULL；新增 SA / INDIVIDUAL / `FREELANCER` 的 v1 |
| `t_onboarding_schema_field` | 约 +300 | v2 字段 = 线上 v1 字段复制（`INSERT … SELECT`，保留参考数据需求改过的 `catalog_code`）＋下表差异 |

Schema 差异：

| Schema | 增 | 删 / 改 |
|---|---|---|
| AE 机构 `*`、`FZ_LLC` | `attributes.AE_ERN`（OPTIONAL）；`attributes.AE_LICENSE_AUTHORITY_NAME`（REQUIRED，`visible_when organization.registrationAuthorityCode=AE_OTHER`）；声明 `PLATFORM_TERMS_ACCEPTANCE`、`PRIVACY_NOTICE_ACKNOWLEDGED`、`RELATED_PERSON_DATA_AUTHORITY` | 删 `declaration.PLATFORM_TERMS` |
| AE 个人 `*` | 声明两项 | 删 `PLATFORM_TERMS` |
| AE 个人 `SOLE_ESTABLISHMENT` | `AE_ERN`；`AE_LICENSE_AUTHORITY_NAME`（`visible_when attributes.AE_LICENSE_AUTHORITY=AE_OTHER`）；声明两项 | 删 `PLATFORM_TERMS` |
| SA 机构 `*` | `attributes.SA_CR_ANNUAL_CONFIRMATION_DATE`（OPTIONAL）；声明三项 | 删 `organization.licenseExpiryDate`、`PLATFORM_TERMS` |
| SA 个人 `*` | 声明两项 | 删 `PLATFORM_TERMS` |
| SA 个人 `FREELANCER`（新） | 复制 SA 个人 `*` v2 ＋ `identifier.SA_FREELANCE_DOCUMENT`（REQUIRED，到期日必填） | — |
| EG 机构 `*` | `identifier.EG_UNIFIED_CR`、`identifier.EG_LOCAL_CR`（同组 `EG_CR`，组内至少一项）；`attributes.EG_VAT_REGISTRATION_NUMBER`（OPTIONAL）；声明三项 | 删 `identifier.EG_COMMERCIAL_REGISTER`、`PLATFORM_TERMS` |
| EG 个人 `*` | 声明两项 | 删 `PLATFORM_TERMS` |

**阶段 4 调整（2026-10-27，研发中发现的模型约束）：**

- 每个主体只有一个证件位（草稿分区 `identifier` 只有一条），因此：SA 自由职业者的 FL 文件号不能再作为第二个证件，改为属性 `SA_FREELANCE_DOCUMENT_NUMBER`（宽松正则 `[Ff][Ll][ -]?[0-9A-Za-z]{1,30}`，按原样保存；不用 `(?i)`，前端 JavaScript 正则不支持）与 `SA_FREELANCE_DOCUMENT_EXPIRY_DATE`，均必填，本人证件组 `PERSON_ID` 保留（与切片 1 AE 个人独资执照用属性的先例一致）；不新增 `SA_FREELANCE_DOCUMENT` 标识类型。EG 两种登记号为二选一，复用现有机构证件组 `ORGANIZATION_ID`。
- 属性代码按主体类型唯一（`party_type` 单值），AE 机构与个人独资各用一组：机构 `AE_ERN`、`AE_REGISTRATION_AUTHORITY_NAME`（`visible_when organization.registrationAuthorityCode=AE_OTHER`）；个人独资 `AE_LICENSE_ERN`、`AE_LICENSE_AUTHORITY_NAME`（`visible_when attributes.AE_LICENSE_AUTHORITY=AE_OTHER`）。属性定义合计 8 行，标识类型 2 行。
- 自由职业文件到期日"不得早于今天"按数据驱动实现：新增规则作用域 `ATTRIBUTE` 与规则 `DATE_NOT_PAST`（默认 0），`SA_FREELANCE_DOCUMENT_EXPIRY_DATE` 配 1；过期报 `IDENTIFIER_EXPIRED`。规则开关新增 1 行（合计 12 行）。代码不得写死国家包属性代码（`JurisdictionLiteralAutoTest`）。
- 响应中的 `current` 只表示声明是否与当前资料版本及文档版本一致，不含取值；提交仍要求取值为 TRUE。声明响应另加 `declaredByAccountId`，供运营展示声明账号。
- `t_rule_setting.scope_type` 列注释增加 `DECLARATION`、`ATTRIBUTE`（DDL 文件内 MODIFY 注释）；旧镜像的规则枚举不认识 `DECLARATION`，增量 DML 必须在新镜像之后执行。

文件：`paytech/client-master/docs/database/sql/changes/2026-10-27-legal-pack-revision.sql`（增量，幂等，预检 + 单事务写入 + 结果计数），同步 `client-master-initial-data.sql`（新环境）。不重跑 `2026-10-27-onboarding-country-pack.sql`。

### 3.2 声明文档版本（S-6a）

- `RuleScopeType` 新增 `DECLARATION`（scope_code 为声明类型），`RuleCode` 新增 `DOCUMENT_VERSION(DECLARATION, null)`。现有规则表结构不变。
- 表单 Schema 接口每条声明回传 `documentVersion`（无文档的声明为 null）。
- 写入声明时：该声明配置了文档版本 → 请求 `termsVersion` 必须与之相等；为空或不等返回 422，字段 `termsVersion`，码 `DECLARATION_DOCUMENT_OUTDATED`（前端提示"条款已更新，请重新阅读后确认"）。未配置的声明保持现状（可空）。
- 正式文本上线时只改 DML 版本号与语言包正文；版本变化后，旧版本的声明在提交时视为失效（与 3.3 同一判定）。
- 文本：占位草案正文放 Client 语言包（`onboarding` 命名空间 `legal.<声明类型小写>.<版本，连字符改下划线>.*`，如 `legal.platform_terms_acceptance.2026_10_draft.body`；键段须为小写 `[a-z0-9_]`），页面标"草案"。

### 3.3 声明绑定资料版本（S-6b）

DDL（client-master，2 列，均为 `ADD COLUMN`，MySQL 8 即时完成）：

```sql
ALTER TABLE `t_client_application`
  ADD COLUMN `draft_revision` BIGINT NOT NULL DEFAULT 0 COMMENT '申请资料版本：每次实际改动草稿（主体、证件、地址、业务、属性、关系人、关系、文件）在同一事务加一；声明绑定此值，不等即失效。';
ALTER TABLE `t_application_declaration`
  ADD COLUMN `draft_revision` BIGINT NULL COMMENT '声明针对的申请资料版本；2026-10-27 之前的存量行为空，按 0 解释。';
```

- **递增点（8 个，均在已有申请锁与写事务内）：** `SaveClientDraft`（`dirtyFields` 为空时不递增）、`CreateClientPerson`、`SaveClientPerson`、`CreateClientRelation`、`SaveClientRelation`、`RemoveClientRelation`、`AssociateClientDocument`、`RemoveClientDocument`。由 `ClientDraftRepository.bumpDraftRevision(applicationId)` 统一执行（`UPDATE … SET draft_revision = draft_revision + 1`），各写入 Service 在其事务末尾调用；不另开事务，不在事务内调外部服务。
- **写入声明：** 记录当时的 `draft_revision`；幂等短路（同值同版本直接返回）增加"资料版本相同"条件。
- **判定：** 某声明"当前有效" = 最新一行 `TRUE`、`COALESCE(行.draft_revision, 0) = 申请.draft_revision`、且（配置了文档版本时）`terms_version` 等于当前配置。
- **提交：** `OnboardingRequirementEvaluator.declarations` 对"最新为 TRUE 但已失效"的声明报新码 `DECLARATION_STALE`（与 `DECLARATION_MISSING` 区分，便于提示"请重新确认"）；提交被拒，返回现有需求错误结构。
- **响应：** `ClientDraftResponseBody` 加 `draftRevision`；`DeclarationResponseBody` 加 `draftRevision`、`current`（布尔）。Client 与运营共用这两个模型。

### 3.4 门槛比较方式（S-7）

- `RuleCode.UBO_THRESHOLD_COMPARISON(JURISDICTION, "GTE")`，取值 `GTE` / `GT`，其他值按现有规则开关处理方式报配置错误。
- `RelationRules.uboSatisfied` 按配置比较；表单 Schema `relationRules` 加 `uboThresholdComparison`。
- Client Web 三处文案（起步清单、关系人说明、受益人检查）按比较方式选用"{n}% 或以上" / "超过 {n}%" 两套键。

### 3.5 接口变化汇总

| 接口（Owner：client-master；BFF 同路径转发） | 变化 | 兼容 |
|---|---|---|
| 表单 Schema（C 端 `GET …/form-schema`，Owner 内部同名） | `declarations[].documentVersion`；`relationRules.uboThresholdComparison` | 只加字段 |
| 申请资料（C05，Client 与运营案件详情共用） | `draftRevision`；`declarations[].draftRevision`、`declarations[].current` | 只加字段 |
| 声明写入（CM-6 `POST …/applications/{app}/declarations`） | 配置了文档版本的声明强制校验 `termsVersion`；新错误码 `DECLARATION_DOCUMENT_OUTDATED` | 请求体不变；v1 声明不受影响 |
| 提交 / 需求检查 | 新需求错误码 `DECLARATION_STALE` | 只加码 |

新增 API 0；Gateway 路由、权限、Console Resource 注册 DML 0。

### 3.6 页面

- **Client 声明步骤：** 每项一行；带文档的声明后附"阅读"链接，打开侧滑面板显示草案正文与版本、"草案"标识；勾选时带上该版本。失效声明显示提示条"你在确认后修改了资料，请重新确认"，复选框回到未选，重新勾选即可。概览页与提交前检查把失效声明计为未完成。
- **Client 关系人 / 起步页：** 按比较方式显示"或以上"/"超过"。
- **运营案件详情：** 新增"声明"区块（位置在资料区块之后），每项显示：声明、是 / 否（文字）、声明时间、声明账号、条款版本、"针对提交版本 / 提交后已失效"。复用现有详情行样式。
- 不新增页面，不另画原型；视觉按 AC-V1 / V2 浏览器验收。

## 4. 研发与验证计划

| 顺序 | 系统 | 内容 | 对应 AC | 测试入口 |
|---|---|---|---|---|
| 1 | client-master | DDL、PDManer、DataObject / Mapper；规则码与比较方式；文档版本；资料版本递增与判定；评估器新码；响应字段 | AC-06—10 | HTTP 业务测试（真实链路、本地库）：声明后改资料→提交被拒→重新确认→提交通过；文档版本缺失 / 过期 422；`GT` / `GTE` 两种 25% 结果；v1 申请旧声明在改资料前有效 |
| 2 | client-master | 增量 DML 与 `initial-data` 同步 | AC-01—05 | HTTP：AE "其他"未填名称报错；SA 无到期日、有年度确认日；FL 号三种输入；EG 两号均空报组错误、填一项通过；v2 声明清单 |
| 3 | 两个 BFF | 新字段透传、形状校验放行 | AC-08 | BFF 现有接口测试补字段断言 |
| 4 | Client Web | 声明步骤、文案两套 | AC-05—07、V1 | 组件测试；浏览器 en / zh-CN、1440 / 390 |
| 5 | 运营 Web | 声明区块 | AC-08、V2 | 组件测试；浏览器 |
| 6 | 文档 | 设计 9.5a 逐项状态与引用修正；PDManer；CURRENT_IMPLEMENTATION | AC-11 | 人工复核 |

本地验证：需要在本地 `_v2e2e` 与 `mor_client_master` 测试库执行 2 个 ALTER 与增量 DML，阶段 4 开始前单独请示（列出文件、目标库与备份）。

## 5. 发布与回退要点（阶段 6 细化）

- 顺序：DDL → client-master 新镜像 → 增量 DML（v2 激活）→ 两个 BFF → 两个 Web → 语言包。旧镜像能读新增列（未映射字段被忽略），DDL 可先行。
- 回退：DML 回退 = v2 置 RETIRED、v1 恢复 ACTIVE（单事务），新增目录 / 类型 / 属性按 ID 段删除（无新申请引用时）；镜像按 sha256 回退；2 个新列保留（无害）。已用 v2 创建的申请在回退后仍按冻结的 v2 加载，需要在回退方案中说明处理（建议回退前确认无 v2 申请，或保留新增类型行）。
- 与参考数据客户端组件需求的 client-master 发布错开，互相通报。
