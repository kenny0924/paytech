# 入驻材料与表单通用化：合同差异稿

状态：**已确认（2026-10-13 用户回复“按照你的建议来”）**，第 4.2、5、6 节按建议值生效；经营模式列表由实现补充，2026-10-15 用户确认，见第 11 节。本稿不批准材料初始化 SQL 的执行、入口启用或部署。实现与验证状态只见[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

## 1. 来源与决定

2026-10-13 用户对“材料按哪个国家判断、首批开放哪些国家”回复：材料由人工判断，系统保持通用；不判断首批开放国家。随后对“按通用方案出差异稿”回复“可以”。

据此本稿取代以下前序内容（确认后生效）：

- [文件合同补充](./file-upload-closure-proposal.md)中“Risk 按 qualificationType + countryCode 映射适用程序”的部分；
- [API 合同第 10 节](./api-contract.md#10-c12c13c05-材料的-owner-组件)的国家映射假设与 `MATERIAL_COUNTRY_REQUIRED`；
- [API 合同第 11、12 节](./api-contract.md#11-资料与材料页面的-bff-投影边界)中按国家筛选材料要求、组织类型和签发机构的部分；
- 实现台账中“材料国家口径、首批开放国家”两个待答复问题。

上传、扫描、文件授权、单份申请、人员/关系、RETURNED/REJECTED 状态等其余合同不变。

## 2. 原则

1. **系统只做通用、确定的检查**：字段格式、必填项是否存在、文件扫描是否通过、主体归属是否正确。
2. **业务充分性由人工判断**：材料是否足够、证件是否真实、行业/MCC 分类，均由 Risk/Operations 审核人决定；不足时退回并写明补件说明。
3. **不出现国家维度的规则**：国家只作为资料事实（居住国、注册国、签发国）保存和展示，不决定材料清单、下拉选项或号码算法。
4. **以后按国家加强属于新需求**：届时新增可选规则，通用规则继续作为默认；本期不预留空的国家配置。

## 3. 差异总览

| 项 | 现状 | 通用方案 |
|---|---|---|
| 材料清单来源 | Risk `MaterialProgramResolver` 按 `qualificationType + countryCode` 映射 programCode；无映射返回 503 | 只按 `qualificationType`（INDIVIDUAL_KYC / ORGANIZATION_KYB / RELATED_PERSON_KYC）映射，每类一个全局 Program；仍由 Risk 持有 |
| 材料查询参数 | `qualificationType`、`countryCode` | 仅 `qualificationType` |
| 无国家时 | `MATERIAL_COUNTRY_REQUIRED` / `requirementsStatus=COUNTRY_REQUIRED`，不能上传 | 删除该错误和状态；资料未填国家也能上传材料 |
| 材料必需性 | 程序要求 `requiredFlag` 作为提交/批准门禁 | 仅保留少量“基础必需”项作为提交门禁；材料是否充分由审核人确认 |
| 组织类型目录 | 按注册国筛选 `organization-types[].country-code` | 一份全局列表，不按国家 |
| 证件目录 | 证件类型 + 签发机构（scopeKey/issuingCountry）配置 | 只有证件类型；签发国从完整 ISO 3166-1 国家中选；删除签发机构配置 |
| `issuerScopeKey` | 客户从机构目录选择 | 系统派生：有签发国时为该国代码，无签发国（如 LEI）时为 `GLOBAL`；客户端不再提交 |
| 证件号规范化（C14） | 待按“已批准各国规则”设计 | 通用规则：见第 6 节 |
| 审核批准门禁 | 必需证据/检查缺失不能批准 | 基础必需项缺失不能提交；批准需要审核人显式确认“材料充分” |

## 4. 材料目录（Risk）

### 4.1 合同

- `GET /internal/due-diligence/material-requirements?qualificationType=...`：删除 `countryCode` 参数，拦截器白名单同步收窄；带 `countryCode` 的请求按未知参数拒绝（400）。
- 响应结构不变：`programId/programVersionId/items(requirementCode, documentType, requiredFlag)`。
- `onboarding.materials.programs-json` 每行改为 `{qualificationType, programCode}`，三类各最多一行；重复、缺失或未知字段仍拒绝。未配置仍返回 503，**不**默认“无需材料”。
- 继续复用既有 Program / Program Version / Requirement 表，不增加表或字段。

### 4.2 建议的通用材料目录（待确认）

| 资格类型 | requirementCode | documentType | 基础必需 |
|---|---|---|:---:|
| INDIVIDUAL_KYC | IDENTITY | IDENTITY_DOCUMENT | ✅ |
| INDIVIDUAL_KYC | ADDRESS | PROOF_OF_ADDRESS | |
| INDIVIDUAL_KYC | OTHER | SUPPORTING_DOCUMENT | |
| ORGANIZATION_KYB | REGISTRATION | REGISTRATION_CERTIFICATE | ✅ |
| ORGANIZATION_KYB | OWNERSHIP | OWNERSHIP_STRUCTURE | |
| ORGANIZATION_KYB | AUTHORIZATION | AUTHORIZATION_LETTER | |
| ORGANIZATION_KYB | ADDRESS | PROOF_OF_ADDRESS | |
| ORGANIZATION_KYB | OTHER | SUPPORTING_DOCUMENT | |
| RELATED_PERSON_KYC | IDENTITY | IDENTITY_DOCUMENT | ✅ |
| RELATED_PERSON_KYC | OTHER | SUPPORTING_DOCUMENT | |

每个要求项允许关联多份文件（现有唯一键已包含 `file_id`）。`OTHER` 用于审核人退回时要求的任意补充材料。

这三个 Program 的数据需写入 Risk 数据库，属于初始化 DML：确认目录后单独形成 SQL 文件、执行范围和回滚方案，另取执行授权。测试继续使用标注为合成的夹具。

## 5. 表单目录（CCT C03）

- `GET` 参数删除 `countryCode`，只保留 `clientType`。响应删除 `countryCode`；`catalogStatus` 删除 `COUNTRY_REQUIRED`，只剩 AVAILABLE / UNAVAILABLE。
- `organization-types[]` 删除 `country-code`；`identifier-types[]` 删除 `issuers`，增加 `issuing-country-mode=REQUIRED|NONE`（如护照为 REQUIRED，LEI 为 NONE）。
- 建议通用默认值（待确认，确认后作为批准配置写入 `application.yml`，不再默认空集合）：
  - 组织类型：`LIMITED_COMPANY`、`PUBLIC_COMPANY`、`PARTNERSHIP`、`SOLE_PROPRIETORSHIP`、`NON_PROFIT`、`OTHER`
  - 个人证件：`PASSPORT`、`NATIONAL_ID`、`DRIVING_LICENSE`、`RESIDENCE_PERMIT`
  - 组织证件：`COMPANY_REGISTRATION_NUMBER`、`TAX_ID`、`LEI`（NONE）
  - 经营模式：保持现有合同，列表待确认
- 签发国、居住国、注册国下拉使用 Web 内置的完整 ISO 3166-1 alpha-2 列表，不由后端按开放范围裁剪。

## 6. 证件号通用规范化（C14 正式提交时使用）

`normalized_identifier_value` = 原值经 NFKC → 去首尾空白 → 删除空格、`-`、`.`、`/` → 转大写；结果须匹配 `[A-Z0-9]{1,128}`，否则 422 定位到该字段。原始输入另行保存供审核展示。

唯一键 `(identifier_type, issuer_scope_key, normalized_identifier_value)` 保持不变。代价：同一证件在不同国家有不同格式写法时，系统可能识别不出是同一身份，需要审核人发现；本期接受。

## 7. 审核门禁（尚未实现，进入后续设计）

- C14 提交前：主体、必填资料字段、每个适用主体的基础必需项至少一份 AVAILABLE 文件、无超出范围的人员材料。
- Risk 审核：审核人逐主体查看材料，记录“材料充分”确认；未确认不能 APPROVED。不足时 RETURNED，补件说明可指向 `OTHER` 或具体要求项。
- 行业/MCC 仍由 Operations 确认，规则不变。

## 8. 数据库

- **无 DDL**。`issuer_scope_key` 保持 NOT NULL，由系统派生填充；`requirement_code`/`document_type` 字段含义不变。
- `t_organization_profile.organization_type` 与 `t_party_identity_key.normalized_identifier_value` 的列注释仍写“司法辖区目录”“按签发作用域批准规则”，与新口径不符。按库表规范需同步 PDManer 与 Schema 注释；是否本期改注释（需要 ALTER，另取授权）待确认，不改不影响行为。
- 需要初始化 DML：Risk 三个通用 Program（第 4.2 节），另取授权。
- 本机已有数据只有测试夹具，没有需要迁移的业务数据。

## 9. 代码影响

| 系统 | 文件 / 范围 | 改动 |
|---|---|---|
| Risk | `MaterialProgramResolver`、`MaterialRequirementsRequestConfiguration`、`MaterialRequirementsController/Processor`、`MaterialRequirementsApi` | 删除国家维度；更新 `MaterialRequirementsHttpAutoTest` |
| CCT | `ClientMaterialAuthorityClient`、`ClientDocumentServiceImpl`（删除国家前置检查）、`ClientFormCatalogProperties/ServiceImpl`、`ClientFormOptionsProcessor/Controller/Api/ResponseBody`、`ClientFormCatalogStatus`、`ClientDraftErrorCode`（删除 `MATERIAL_COUNTRY_REQUIRED`）、草稿保存派生 `issuerScopeKey` | 同步测试 |
| BFF | `ClientOnboardingProcessor`、`ClientOnboardingModels`、`FormOptionsResponseBody`、`ClientErrorStatusResolver` | `materialSubjects` 删除 `countryCode` 和 `COUNTRY_REQUIRED`；C03 删除国家参数 |
| Web | `catalog.ts`、`onboarding.ts`、`OnboardingPage.tsx` | 删除“先填国家”提示和签发机构下拉；签发国改为全量国家；必需项标记改为“基础必需” |
| 文档 | `api-contract.md` 第 10—12 节、`file-upload-closure-proposal.md`、`ui-design.md`、数据库文档、实现台账 | 标注被取代内容 |
| UI 稿 | `client-console-web/design.pen` 材料区和证件字段 | 删除签发机构，调整材料提示 |

上述改动只删减国家依赖，不新增外部操作。按新规范顺手迁移请求幂等、Processor 形态的工作不包含在本稿中，另行出迁移方案。

## 10. 验证要求

- Risk：只按资格类型解析；带 `countryCode` 返回 400；未配置返回 503；三类各自返回正确目录。
- CCT：未填国家也能关联材料；未知 requirementCode 仍为 422；C03 不按国家过滤；`issuerScopeKey` 不接受客户端值并正确派生。
- BFF/Web：页面不再出现“需先填国家”；签发国可选任意国家；旧草稿中的机构值显示为待确认，不自动清空。
- 回归：原有上传、扫描、关联、移除、单份申请和人员/关系测试全部通过。

## 11. 确认结果

1. 第 4.2 节材料目录与基础必需项：按建议确认。初始化 SQL 已生成：[执行文件](./db/implementation/risk-generic-materials-init.sql)（由 [生成器](./db/implementation/generate-risk-generic-materials.py) 输出，`--check` 可复核），2026-10-14 经授权已在本机与 mor-test 执行，见[执行范围](./db/implementation/risk-generic-materials-local-execution.md)。
2. 第 5 节组织类型、证件类型：按建议确认。经营模式本稿未给建议，实现采用通用列表 PHYSICAL_GOODS / DIGITAL_GOODS / SERVICES / SUBSCRIPTION / MARKETPLACE（仅组织）/ OTHER，2026-10-15 用户复核确认。
3. 第 6 节规范化规则：按建议确认，在 C14 正式提交时实现。
4. 第 8 节列注释：本稿没有给出建议，**本期未修改**（不改不影响行为），等后续 ALTER 授权时一并处理。
5. 实现时一并删除 C03 路由 Query 白名单中的 `countryCode`（`db/complete_registration_plan.py` 重新生成，仅两处差异）。
