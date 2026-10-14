# C14 正式提交：合同修订稿

状态：**已确认（2026-10-13 用户回复“可以”）**：第 8 节三项均按建议执行，组织提交至少一名关联人员；先做 4a。实现与验证状态只见[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。本稿修订[API 合同](./api-contract.md)第 1、4、5 节中的 C14 部分，以及[数据设计](./data-design.md)第 2、4 节中的证件绑定与提交恢复；其余合同不变。依据是[请求幂等职责决定](../../../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)和[通用化差异稿](./generic-material-form-proposal.md)。

## 1. 要改的两点

| 项 | 原合同 | 修订后 |
|---|---|---|
| 重复提交 | C14 必须带 `Idempotency-Key`，CCT 用本库 `command_idempotency` 保存请求摘要并重放原响应 | CCT 不读取 `Idempotency-Key`，也不保存请求摘要或响应。按申请当前状态防重：已是 SUBMITTED/UNDER_REVIEW 时直接返回当前提交；HTTP 层的重复请求交给 Gateway |
| 提交身份 | 由幂等键派生 submissionId | **服务端在状态迁移时生成** submissionId（UUID），写入 `t_client_application.current_submission_id`；之后的重试、Risk 登记和恢复都用它 |

为什么不让页面生成 submissionId：页面重试和用户第二次点击，对业务来说是同一件事，都应该得到同一次提交。按状态防重已经覆盖这两种情况，不需要浏览器提供身份，也不会出现“换个 ID 就能重复提交”的漏洞。

C04、C06—C13 和 F01/F02 现有的本地重放机制属于存量，不在本稿范围；单独出迁移方案。C14 不接入这套机制。

## 2. 接口

`POST /client-onboarding/v1/applications/{applicationId}/submissions`，Body 为空（不接受任何字段）。

| 情况 | 结果 |
|---|---|
| DRAFT/RETURNED 且检查全部通过 | **202**，`{applicationId, submissionId, status:"SUBMITTED"}`；生成新 submissionId |
| 已是 SUBMITTED 或 UNDER_REVIEW | **200**，返回当前 submissionId 和当前状态，不重新检查、不生成新提交 |
| APPROVED / REJECTED | 409 `APPLICATION_NOT_EDITABLE` |
| 资料或材料不完整（含文件已不可用） | 422 `SUBMISSION_INCOMPLETE`；逐项问题由 C16 提交核对返回（见第 2.1 节） |
| 证件号已属于其他主体 | 409 `IDENTITY_CONFLICT`，不透露是谁、属于哪个申请 |
| 申请不存在或不属于当前账号 | 404 `APPLICATION_NOT_FOUND` |
| Platform、Risk 查询暂不可用 | 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`，申请状态不变 |

BFF 只做转发；Gateway C14 路由去掉 `idempotency-key` 的转发配置（注册计划重新生成）。

### 2.1 实施修订：C16 提交核对（2026-10-13）

研发中发现公共 HTTP Client 只对带 JSON Body 的请求传递字段级 422（`VALIDATION_ERROR` + `errors[]`），空 Body 的 C14 无法把逐项问题传到 BFF；而且这些问题是已保存资料的问题，不是请求 Body 字段错误。因此改为：

- C14 不完整时返回 422 `SUBMISSION_INCOMPLETE`（业务错误码，无 `errors[]`）；文件不可用并入这一项，不再单列 `FILE_NOT_USABLE`。
- 新增只读 **C16** `GET /client-onboarding/v1/applications/{applicationId}/submission-check`，返回 `{ready, issues:[{field, message}]}`，与 C14 使用同一套准备和检查，不加锁、不写入；只适用于 DRAFT/RETURNED，其他状态 409。页面先核对再提交，C14 收到 `SUBMISSION_INCOMPLETE` 时重新核对并展示列表。
- C16 在注册计划中新增一条禁用路由（38 条路由、49 个管理命令模板）。

## 3. 处理顺序

由 CCT 的 `SubmitClientApplicationProcessor` 处理，使用 `AbstractLockProcessor`，按 applicationId 加锁（与草稿保存同一把锁，提交期间不能再保存）。

1. **事务外：** 向 Platform 核对当前 CLIENT 会话；读取申请并核对归属。
2. **按状态分支：** SUBMITTED/UNDER_REVIEW 直接返回当前提交；终态拒绝；只有 DRAFT/RETURNED 继续。
3. **事务外读取下游事实：**
   - 按资格类型向 Risk 读取通用材料要求（每种类型一次）。
   - 对每份材料引用，向 Platform 核验文件当前可用，且内容 Hash 与关联时记录的一致。
4. **本地检查**（第 4 节），并计算每个主体的证件规范化值（第 5 节），一次构造完整 Context。
5. **一个本地短事务：**
   - 条件更新申请：`status IN (DRAFT, RETURNED)` → SUBMITTED，写 `current_submission_id`、`submitted_time`；影响行数不为 1 则回滚。
   - 为每个主体绑定永久身份键：已属于同一 Party 则复用；唯一索引冲突则整体回滚并返回 IDENTITY_CONFLICT。
   - 登记补偿任务 `CLIENT_REVIEW_REGISTRATION`，eventKey 为 submissionId。
6. **提交后：** 补偿 Handler 调 Risk I01 登记审核任务（第 6 节）。C14 不等待 Risk，直接返回 202。

## 4. 提交检查

| 检查 | 规则 | issues[].field 示例 |
|---|---|---|
| 必填字段 | 个人：姓名、出生日期、国籍、居住国、地址、证件；组织：法定名称、注册国、组织类型、成立日期、地址、证件；两者都要经营模式、业务说明、预计月交易额和客单价（> 0）、币种、联系邮箱和电话 | `individual.givenName` |
| 目录代码 | 组织类型、经营模式、证件类型必须在当前目录中；旧代码视为未填 | `organization.organizationType` |
| 证件 | 类型要求签发国时必须填；有效期结束不早于今天 | `identifier.issuingCountry` |
| 组织人员 | 每名在范围内的人员都满足个人必填与证件规则；**至少一名关联人员**（见第 8 节问题 1） | `people[31].identifier.identifierValue` |
| 基础必需材料 | 每个主体每个 `requiredFlag=true` 的要求至少关联一份文件 | `materials[30].REGISTRATION` |
| 范围外材料 | 存在挂在已移除人员上的材料时拒绝，用户须先移除 | `materials[33].IDENTITY` |
| 文件可用 | 每份引用的文件为 ACTIVE + AVAILABLE，Hash 一致 | `documents[50]` |

只检查“有没有、格式对不对”；材料是否充分由审核人判断。

## 5. 证件号规范化与身份绑定

- `normalized_identifier_value`：原值经 NFKC → 去首尾空白 → 删除空格、`-`、`.`、`/` → 转大写；结果须匹配 `[A-Z0-9]{1,128}`，否则作为提交问题返回。
- `issuer_scope_key`：证件类型 `issuingCountryMode=REQUIRED` 时为签发国代码，`NONE` 时为 `GLOBAL`。签发方式以提交时的 CCT 目录为准。
- 原始号码继续保存在 `t_party_profile_identifier`，绑定后写入 `identity_key_id`。
- 唯一索引 `(identifier_type, issuer_scope_key, normalized_identifier_value)` 是最终保护；同一 Party 重复提交复用原键。
- RETURNED 后修改证件再提交，绑定新键；旧键保留原归属，不自动删除。

## 6. 登记 Risk 审核（补偿 Handler）

- `ClientReviewRegistrationHandler` 读取申请当前事实（SUBMITTED 后资料只读，所以当前事实就是提交时的事实），组装 I01 请求：submissionId、申请、主体及关联人员的结构化资料、关系、材料引用（fileId + contentHash）、各主体的资格类型和程序身份。
- Risk 按 `(source_namespace, applicationId, submissionId, role…)` 幂等建任务；返回 caseId 后，CCT 用短事务把申请从 SUBMITTED 更新为 UNDER_REVIEW 并记录 `due_diligence_case_id`，条件为 `current_submission_id` 相同。
- 结果未知时用同一 submissionId 重试 I01 或查询 I03，不生成新提交；重试耗尽保留任务供人工处理，申请保持 SUBMITTED。
- 页面上 SUBMITTED 和 UNDER_REVIEW 都显示“审核中”。

## 7. 范围与实施顺序

| 增量 | 内容 | 依赖 |
|---|---|---|
| 4a | CCT C14 Processor、提交检查、身份绑定、补偿登记及 Handler（I01 按合同调用，测试中用 DTO Mock）；BFF 转发；Web 核对页、提交按钮、逐项问题定位、审核中状态；CCT 入驻测试基础设施 | 本机 CCT 表结构已有 `current_submission_id`，执行前只读核对 |
| 4b | Risk I01/I03：建主任务、关联人子任务、主体、证据引用，按 submissionId 幂等 | Risk 本机表结构需按 `risk-target-tables.sql` 迁移（单独授权）；任务主体快照需 Vault Protect，需核对 `RISK_DD_PARTY_SNAPSHOT_V1` 类型在 Vault 中已配置 |

4a 完成后申请能提交并停在 SUBMITTED（Risk 登记任务持续重试）；4b 完成后才进入 UNDER_REVIEW。

## 7.1 4b 实施记录（2026-10-13）

- 用户确认：4b 由本会话实现；本机 mor_risk 迁移已授权执行（[执行范围与回执](./db/implementation/risk-onboarding-local-execution.md)）；**资料快照本期不做**，不写 `t_dd_party_snapshot`、不接 Vault，入口不把主体个人资料原值传入 risk-compliance。后续接入快照前需在 Vault 登记 `RISK_DD_PARTY_SNAPSHOT_V1` 并定义快照 Schema。
- 建模：主案件 `role_type=CLIENT`；每名关联人员一个 `RELATED_PERSON_KYC` 子案件，主案件通过 `t_dd_case_dependency` 依赖子案件。每个案件只有 PRIMARY 主体；材料要求按当前通用程序修订实例化，有材料的要求为 RECEIVED，每份材料成为 RECEIVED 证据引用；初始状态变更 无 → SUBMITTED，`trigger_id` 为提交编号。
- 关系到依赖类型：UBO→UBO_KYC、DIRECTOR→DIRECTOR_KYC、SIGNATORY→SIGNATORY_KYC；Risk 模型没有 OWNERSHIP/CONTROL 依赖类型，按受益所有人（持股或控制）口径映射为 UBO_KYC；同一人员多种关系按依赖类型去重。
- 防重：同一来源提交已登记时比较案件集合与证据（文件+摘要）集合，一致返回原主任务，不一致 409；唯一键 `uk_dd_case_source` 是最终保护，不加 Redis 锁。
- 已知限制：关联人员若已有进行中的 `RELATED_PERSON_KYC` 任务（例如同时是另一家组织的关联人员），登记返回 409，CCT 登记任务进入终态失败等待人工处理；跨申请复用子案件留待后续设计。

## 8. 待确认

1. **组织提交时是否要求至少一名关联人员？** 建议要求至少一名（任意关系类型），具体是否满足 UBO/董事等要求由审核人判断。
2. 第 1 节的修订方式（服务端生成 submissionId、按状态防重、C14 不再要求 `Idempotency-Key`）。
3. 第 7 节先做 4a、再做 4b 的顺序。
