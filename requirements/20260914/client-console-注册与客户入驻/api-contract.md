# Client 入驻：接口合同

> **2026-09-15 用户决定：** 本期继续注册页填写邮箱和密码、邮件链接验证后登录；密码与邮箱所有权绑定的进一步优化延至对外开放前，详见[决定记录](./registration-password-binding-amendment.md)。

状态：已确认合同，研发中；实际实现与验证从当前台账读取。用户已接受[简化方案](./simplification-design.md)；本稿定义本期接口，不因合同确认推断注册或部署。方法与路径原清单见 [阶段 3 API inventory](./assets/stage3/api-operation-inventory.json)，已确认文件差异见[阶段 4 合同增量](./assets/stage4/file-operation-inventory-20260915.json)。

本稿有 37 个外部业务操作（Client 28、Operations 9）及 6 个内部登记/恢复/只读授权操作；BFF 到 Owner 的同义转发不重复计数。沿用 Platform 的 Tenant Provision/Status、文件存储和授权能力，不把其既有内部操作再算作新业务 API。审核结果用 MQ 事实回传，不新增 BFF 跨库事务或同步回调写库入口。

> **2026-09-15 响应决定：** C04/C06 返回固定 CommandReceipt，成功后 C05 查询最新资料。同键同参重放原回执和 HTTP 状态；详见[固定回执决定](./draft-command-response-proposal.md)。

## 1. 协议与可信身份

- 新路径沿用项目默认 `/v1`。它是 HTTP 合同路径，不是本期取消的客户资料版本。
- Client BFF 只服务 `CLIENT/client-console/CUSTOMER`；Operations 的 Realm/Audience 和现有认证路径保持独立。Browser 不提交 accountId、tenantId、requestedBy、approvedBy 或 Owner 系统代码来决定权限。
- 公共入口经 Gateway。BFF 的内部请求传真实上下文，Owner 再核对 Account/Member/申请/文件权限；公开请求中的同名 Header 必须被入口删除，不能据此建立信任。
- 所有 ID 以无前导零十进制字符串传输；提交命令 ID/幂等键使用 UUID 字符串；金额和持股比例用十进制字符串，时间 UTC 六位微秒字符串，业务日期用 YYYY-MM-DD。
- Client C04、C06—C14、F01/F02，以及 O03—O09 必须携带 `Idempotency-Key`（UUID）。内部提交的 submissionId 是 C14 的稳定命令身份。相同身份与同内容返回原结果，不同内容返回 409；超时后重试原键，不能自动换键。
- CCT 沿用本库 command_idempotency 业务语义，不在本期顺手迁移其他系统到 Foundation 命令幂等 JAR。请求摘要仅供防止同键异参，不能作为资料版本、页面冲突令牌或资料快照。
- GET 不改状态；验证/重置邮件链接的 GET 只展示页面，最终用 POST 消费，避免邮件扫描器提前激活或重置。
- Refresh Cookie 由 BFF 管理，名为 `__Secure-client-refresh`，HttpOnly、Secure、SameSite=Lax、host-only，Path=`/api/client/auth/v1`；写入与清理使用同一属性。Access Token 只在浏览器内存保存并用于 Authorization，浏览器不在 localStorage 保存访问/刷新 Token。写入校验固定部署 Origin，Refresh 单飞。敏感 Body 禁止普通请求/响应日志。
- 错误完全沿用[项目 Problem 合同](../../../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)：type/title/detail/code/requestId/timestamp，可选 errors；不增加 body.status 或旧 code/message 外壳。

## 2. 操作清单

表中为 BFF/内部监听路径；浏览器使用带 `/api/client` 或 `/api/operations` 前缀的外部路由，准确映射见 [注册计划](./db/registration-plan.json)。认证对外为 `/api/client/auth/v1/*`，入驻为 `/api/client/onboarding/v1/*`。C02 切换会话对外固定 `/api/client/auth/v1/workspace-context`，以便浏览器携带认证 Path 的 Cookie，下游路径不变；不把两种路径当作重复业务 API。

| ID | 方法与路径 | Owner/聚合 | 请求 → 响应 | 业务规则 |
|---|---|---|---|---|
| A01 | POST `/client-auth/v1/register` | Platform | Register → Accepted | 创建待验证账号；不签发工作区会话 |
| A02 | POST `/client-auth/v1/email-verification-requests` | Platform | Email → Accepted | 重发产生新链接，旧链接失效 |
| A03 | POST `/client-auth/v1/email-verifications` | Platform | LinkToken → VerificationResult | 单次消费验证链接，不自动免密登录 |
| A04 | POST `/client-auth/v1/login` | Platform | Login → AccountContext | 邮箱密码；BFF 设置 HttpOnly Cookie |
| A05 | POST `/client-auth/v1/refresh` | Platform | Empty → AccountContext | BFF 从 Refresh Cookie 取值，单飞轮换 |
| A06 | POST `/client-auth/v1/logout` | Platform | Empty → NoContent | 撤销当前 Session 并清 Cookie |
| A07 | POST `/client-auth/v1/password-reset-requests` | Platform | Email → Accepted | 统一响应，不暴露账号存在性 |
| A08 | POST `/client-auth/v1/password-resets` | Platform | ResetPassword → NoContent | 消费重置链接并撤销旧会话 |
| A09 | GET `/client-auth/v1/context` | Platform | Empty → AccountContext | 读取当前 Account 和会话事实 |
| C01 | GET `/client-onboarding/v1/start` | Client BFF aggregation | Empty → Start | BFF 聚合本人申请、上传就绪与工作区 |
| C02 | POST `/client-onboarding/v1/workspace-context` | Platform | WorkspaceSelection → AccountContext | Platform 校验实际 Member 并切换会话 |
| C03 | GET `/client-onboarding/v1/form-options` | Client BFF aggregation | FormOptionsQuery → FormOptions | CCT/Risk 提供字典与适用要求，缺配置不默认通过 |
| C04 | POST `/client-onboarding/v1/applications` | CCT | StartApplication → CommandReceipt | 每账号个人/组织合计一份未完成申请；恢复原单，类型不匹配返回冲突 |
| C05 | GET `/client-onboarding/v1/applications/{applicationId}` | Client BFF aggregation | Empty → Application | CCT 资料与 Risk 当前任务投影；只返回当前账号可见内容 |
| C06 | PATCH `/client-onboarding/v1/applications/{applicationId}/draft` | CCT | DraftPatch → CommandReceipt | 只允许 DRAFT/RETURNED；明确 dirtyFields；不接受分类/审核字段 |
| C07 | POST `/client-onboarding/v1/applications/{applicationId}/people` | CCT | CreatePerson → PeopleCommandReceipt | 组织专属；个人资料与首个关系同一事务保存 |
| C08 | PATCH `/client-onboarding/v1/applications/{applicationId}/people/{partyId}` | CCT | PersonPatch → PeopleCommandReceipt | 稳定 Party 归属；已批准共享资料只读 |
| C09 | POST `/client-onboarding/v1/applications/{applicationId}/relations` | CCT | Relation → PeopleCommandReceipt | 给本申请已关联的人员追加一个关系 |
| C10 | PATCH `/client-onboarding/v1/applications/{applicationId}/relations/{relationId}` | CCT | RelationPatch → PeopleCommandReceipt | 不能更换组织或相关人归属 |
| C11 | DELETE `/client-onboarding/v1/applications/{applicationId}/relations/{relationId}` | CCT | Empty → NoContent | 只移除本申请可编辑关系，不删 Party/旧审核事实 |
| C12 | POST `/client-onboarding/v1/applications/{applicationId}/documents` | CCT | DocumentReference → DocumentCommandReceipt | 验证 File/Requirement/Subject 全部准确归属 |
| C13 | DELETE `/client-onboarding/v1/applications/{applicationId}/documents/{documentId}` | CCT | Empty → NoContent | 仅当前可编辑材料引用；Platform 文件不在此删除 |
| C14 | POST `/client-onboarding/v1/applications/{applicationId}/submissions` | CCT | Empty → Submission | 完成校验并锁定资料，登记 Risk 后续任务 |
| C15 | GET `/client-onboarding/v1/applications/{applicationId}/review-history` | Risk | PageQuery → ReviewHistory | 当前账号可见审核动作与任务，不提供 Profile 版本比较 |
| F01 | POST `/onboarding-files/v1/uploads` | Platform + CCT 目标授权 | UploadStart → Upload | 核对本人当前可编辑申请，再由 Platform 唯一解析并核对对应 Tenant |
| F02 | POST `/onboarding-files/v1/uploads/{uploadId}/completion` | Platform | Empty → FileCompletionReceipt | 由 Platform 查询对象存储确认真实字节和 Hash，不能相信浏览器声明完成 |
| F03 | GET `/onboarding-files/v1/files/{fileId}` | Platform | Empty → File | 仅授权元数据、扫描状态和可执行动作 |
| F04 | POST `/onboarding-files/v1/files/{fileId}/download` | Platform | Empty → Download | 再次检查权限与可用性，返回短时受控下载授权 |
| O01 | GET `/client-onboarding-admin/v1/cases` | Risk | CaseQuery → CasePage | 查询经授权范围内的入驻案件 |
| O02 | GET `/client-onboarding-admin/v1/cases/{caseId}` | Operations BFF aggregation | Empty → ReviewCase | BFF 聚合 CCT 原申请、Risk 任务、文件授权投影 |
| O03 | PUT `/client-onboarding-admin/v1/cases/{caseId}/classifications` | CCT | ClassificationSet → ClassificationSetResult | CCT 独占此任务分类集合；仅未确认时可改 |
| O04 | POST `/client-onboarding-admin/v1/cases/{caseId}/classification-confirmation` | CCT | Empty → CommandResult | CCT 固定分类，提交后交给 Risk 检查同一任务 |
| O05 | POST `/client-onboarding-admin/v1/cases/{caseId}/evidence/{evidenceId}/review` | Risk | EvidenceReview → CommandResult | Risk 记录材料核验结果，不改原文件 |
| O06 | POST `/client-onboarding-admin/v1/cases/{caseId}/checks` | Risk | CheckResult → CommandResult | Risk 追加真实检查结果与证据 |
| O07 | POST `/client-onboarding-admin/v1/cases/{caseId}/return` | Risk | ReturnForCorrection → CommandResult | 结束当前任务，发事实给 CCT 退回原申请 |
| O08 | POST `/client-onboarding-admin/v1/cases/{caseId}/approval` | Risk | ReviewDecision → CommandResult | 分类、主/子任务、证据、检查全部满足后批准 |
| O09 | POST `/client-onboarding-admin/v1/cases/{caseId}/rejection` | Risk | ReviewDecision → CommandResult | 最终拒绝；不能改回草稿 |
| I01 | POST `/client-onboarding-review/v1/submissions` | CCT→Risk | ReviewSubmission → ReviewRegistration | 按提交命令身份幂等建立主/关联人任务 |
| I02 | POST `/client-onboarding-review/v1/cases/{caseId}/classifications` | CCT→Risk | ConfirmedClassifications → CommandResult | 幂等接收同任务固定分类 |
| I03 | GET `/client-onboarding-review/v1/submissions/{submissionId}` | CCT→Risk | Empty → ReviewRegistration | 登记结果未知时查询原任务，不造新任务 |

A01/A02/A03/A04/A05/A07/A08 为公开认证流程，仍受限流、Origin 和指定用途校验。A06/A09、C*、F* 要求有效 CLIENT Account Context；C02 完成后才建立 Tenant Context。O* 要求 OPERATIONS 上下文及对应查询/核验/分类/决定权限。内部 I* 只允许私网 CCT 调用，不能注册到公共 Gateway。

无 Body 的 Empty 不接收任意额外业务字段。路径中的资源必须属于同一申请/任务；跨归属按不可见资源处理，不返回他人资料。协议结构错误（含未知 DTO 顶层字段、重复 JSON 键、错误 JSON 类型）沿用框架 400；可解析请求中的非法 dirtyFields、关系属性和值返回 422。客户端均不得混入 classification、status、decision、owner 或 version 字段。

## 3. 输入字段

### 3.1 认证与导航

| 模型 | 必填字段 | 限制/可选字段 |
|---|---|---|
| Email | email:string | 最大 320 字符，沿用当前 Platform 的 NFC/IDN/邮箱规范化；不去掉 plus tag |
| Register / Login | email,password:string | 注册新密码 15—128 Unicode 字符，UTF-8 最多 512 bytes，NFC，不 trim；登录校验已有凭据。沿用现有 Argon2id 参数与可维护弱密码规则，不将开发示例弱密码集合当作生产能力 |
| LinkToken | challengeNo,secret:string | UUID 查找标识、高熵 URL-safe Secret；用途由接口固定，不能跨验证/重置使用 |
| ResetPassword | challengeNo,secret,newPassword | 与注册相同密码规则；密码不进入幂等摘要或普通日志 |
| WorkspaceSelection | memberId:ID | Platform 从实际 Member 解析 Tenant/Owner，验证当前 Account 拥有它 |
| StartApplication | clientType | INDIVIDUAL/ORGANIZATION，创建后不可改；已有未完成申请时类型相同返回原申请，类型不同返回 409/ACTIVE_APPLICATION_TYPE_MISMATCH |
| FormOptionsQuery | clientType | countryCode 可空；国家未选时仅返回通用目录，不虚构材料要求 |
| PageQuery | 无 | cursor 可空、最多 256 字符；limit 默认 20，范围 1—100 |
| CaseQuery | 无 | PageQuery + status 可空、applicationNo 可空且最多 64 字符；按授权范围查询 |

A01/A02/A07 成功均为 HTTP 202 与相同 Accepted：`{"accepted":true,"resendAfterSeconds":60}`。表示请求已受理，不承诺邮件实际投递。重发冷却/限额为服务端规则，本期拟采用 60 秒间隔；IP 和账号范围的独立保护不依赖前端倒计时。账号是否存在不能改变公开响应内容。

### 3.2 草稿与个人/组织字段

DraftPatch 使用 `dirtyFields:string[]` 和 `values`。dirtyFields 1—50 项且不得重复；values 的叶字段集合必须与 dirtyFields 精确一致。未列出的字段不变，显式 null 清空可空字段；不得用缺省值覆盖其他分区。首期不接受任意 JSON Patch 路径、数组索引或数据库列名。

允许字段由以下闭合集合定义；JSON 与数据库映射采用 [字段合同](./simplification-design.md#5-字段一致性合同)。所有草稿可选字段缺值为 null，正式提交按类型/适用要求校验。

| JSON 分区 | 字段 | 类型/上限 |
|---|---|---|
| individual | givenName,middleName,familyName | string，分别 64；middleName 可空 |
| individual | birthDate,nationalityCountry,residenceCountry | DATE、国家代码 CHAR(2) |
| organization | legalName,tradeName,incorporationCountry,organizationType,incorporationDate | 名称各 64；国家代码 2、类型代码 64、DATE |
| address | addressLine1,addressLine2,city,stateOrProvince,postalCode,countryCode | 255、255、128、128、32、2 字符；第二行可空，州省/邮编按国家要求 |
| identifier | identifierType,issuingCountry,issuerScopeKey,identifierValue,validFrom,validTo | 类型/机构代码 64，国家 2，号码 128，DATE；证件类型/机构/号码作为完整标识组合校验，不允许不明作用域号码进入稳定身份键 |
| business | businessModelCode,websiteUrl,businessDescription | 64、2048、2000 字符；网站可空，提交时说明必填 |
| business | expectedVolume,expectedTicket,currency | DECIMAL(24,8) 字符串、DECIMAL(24,8) 字符串、CHAR(3)；volume 固定月金额，提交时两项金额均 >0 |
| contact | email,phone | email 320、phone 32；服务端规范化国际号码并保存 Vault Token；不是账号绑定 |

个人和组织不同时写入两种基本资料。CreatePerson 使用 person（PersonPatch 的 dirtyFields/values）+ 首个 relation；PersonPatch 使用同一字段集合与 dirtyFields，不允许直接写 partyId 或已有身份键归属。业务联系人属于主 Party，新增相关人员不会创建登录 Account。未完整的证件标识保存在 Profile Identifier 的草稿列，identityKeyId 保持空；正式提交前完成组合校验、规范化和永久唯一身份绑定，不能补造号码或机构。遇到其他 Party 的相同身份不得自动合并或泄露其资料，详见[数据设计](./data-design.md)。

### 3.3 人员、材料、文件、运营

| 模型 | 字段与规则 |
|---|---|
| Relation | relatedPartyId、relationType 必填；ownershipPercentage、effectiveFrom、effectiveTo 同为顶层字段。类型为 OWNERSHIP/CONTROL/DIRECTOR/SIGNATORY/UBO；OWNERSHIP/UBO 草稿比例可空，填写时为 (0,100] 且最多 10 位小数，其余类型必须为空。effectiveFrom DATE 必填，effectiveTo 可空且严格晚于开始 |
| RelationPatch | dirtyFields 1—4 个不重复键，values 为同键集合的平铺对象，仅 relationType/ownershipPercentage/effectiveFrom/effectiveTo；显式 null 清空可空字段。不能改组织/人员 ID。日期按 UTC 日初解析，结束为排他边界；合并当前值后校验期间和比例，变更类型时须明确清空不适用的比例 |
| DocumentReference | subjectPartyId、requirementCode(64)、documentType(64)、fileId；Hash 从 Platform 权威元数据读取，不信任浏览器 Hash |
| UploadStart | applicationId:ID、subjectPartyId:ID、filename(255)、mimeType(128)、sizeBytes(十进制正整数字符串)；applicationId 仅表明目标，必须由 CCT 核对归属与可编辑状态；不接收 accountId/clientId/tenantId。具体 MIME/大小由有效 Platform Scan Policy 决定 |
| ClassificationSet | items 1—20 项，每项 industryCode(64)、mccCode(四位数字字符串)、primary:boolean；组合不重复、恰好一个 primary；确认人和时间由服务端生成 |
| EvidenceReview | outcome=ACCEPTED/REJECTED；Evidence 必须属于同一 Case；文件不可用不能核验为通过。客户可见说明统一在退回/最终决定时提交 |
| CheckResult | programCheckId、caseSubjectId、resultCode；只允许程序目录中的人工检查类型和结果代码。Risk 关联本 Case 已核验的材料与主体，不能用浏览器任意证据 ID 声称完成检查 |
| ReturnForCorrection | reasonCode、section=BASIC/BUSINESS/PEOPLE/MATERIALS、publicNote 1—1000；人工说明需要修改的字段/人员/材料。首期只自动定位分区，不建设通用字段级整改工作流 |
| ReviewDecision | reasonCode、reason 1—2000；从受控原因目录选择。操作与命令身份已确定批准/拒绝，不由 Body status 决定；publicNote 可空且最多 1000，是另行确认的安全说明 |
| ReviewSubmission | sourceNamespace、applicationId、submissionId、clientId、clientType、requestedByAccountId、主/相关人结构化资料与关系、受控材料引用、适用程序身份；无 Profile Version No |
| ConfirmedClassifications | applicationId、submissionId、caseId、固定分类集合、confirmedBy、confirmedAt；确认身份绑定当前 Case，只接受完全相同的重放 |

审核原始理由和内部核验细节不能自动作为 publicNote 暴露。没有已批准的客户可见文案时，返回安全通用说明和联系客服入口，不能透传内部错误或命中信息。

## 4. 响应及示例

CommandReceipt 固定返回 commandId/applicationId/applicationNo/clientId/clientType/operation/completedAt。operation 为 CREATED/RESTORED/SAVED；创建 201，恢复/保存 200；completedAt 是首次成功的 UTC 六位微秒时间。重放先核对当前账号和原资源归属，再返回原回执，不受后续表单更新或申请终态影响。读当前表单仍使用 C05。CCT 私网只提供自身的资料与可编辑状态，Risk 任务、文件就绪及页面聚合属于 BFF；本阶段投影边界见第 11 节。

Application 返回 applicationId/applicationNo/clientType/status、上述可见表单值、people、relations、documents、savedAt、currentReviewTask、uploadReadiness、allowedActions、fieldRequirements。allowedActions 由 Owner 当前事实计算；UI 不从“按钮能点”推断有权限。草稿可带缺项清单，不用整个 GET 422 表示未填完。

Upload 的临时授权统一放在 `authorization` 对象：`method="PUT"`、`url`、`headers`、`expiresAt`。外层 `expiresAt` 是原 Upload 不可延长的到期时间，内层是这次授权的到期时间且不得更晚；重放可更新授权，不能更换 uploadId、存储目标或生命周期。`headers` 只包含该存储适配器要求的上传 Header，不把 Platform/Client Session 凭据发送到对象存储。

AccountContext 返回 accountId、emailMasked、emailVerified、contextType、当前 tenant/member 可空、允许动作；登录/刷新/上下文切换额外返回 accessToken 与 expiresAt，前端仅保存在内存；Refresh Token 只由 BFF 写 HttpOnly Cookie，不进入响应 JSON。VerificationResult 为 verified=true 和 nextAction=LOGIN；不返回 Token。Start 仅聚合当前账号可见 application 摘要与 workspace/member 摘要，不复制所有 Role/Permission 到 Token。

C07—C10 返回 PeopleCommandReceipt，字段和重放规则见[固定回执决定](./draft-command-response-proposal.md#后续-c07c11-人员与关系命令)。Person/Relation 当前可见属性归入 C05；C12 返回固定 DocumentCommandReceipt；F02 返回固定 FileCompletionReceipt；字段及重放规则见[文件合同补充](./file-upload-closure-proposal.md#4-公共接口差异)，当前材料和文件状态分别从 C05/F03 读取。File 返回 fileId、displayName、sizeBytes、mimeType、scanStatus、objectStatus、allowedActions；Upload 返回 uploadId、受控临时上传授权、expiresAt。Download 返回授权 URL、expiresAt，不回传对象存储凭据。临时 URL 不进普通日志或通用审计。

Submission：HTTP 202，applicationId、submissionId、status=SUBMITTED；重放返回同一身份。Risk 登记成功后同一申请转 UNDER_REVIEW。CommandResult：commandId、caseId、accepted、processing；同步本地完成为 200，等待 Owner 衔接为 202。NoContent：204。创建人员/关系/材料为 201，重复幂等可返回原成功状态和原结果。

分页响应统一 items、nextCursor（可空），不伪造总数。ReviewCase 含当前 application/submission/case 精确身份、只读客户资料、材料、必要核验项、当前分类和可执行动作。ReviewHistory 只返回客户可见动作、时间、原因、审核任务 ID，不暴露内部 Reviewer 个人信息或 Profile 版本。

保存示例：
```json
{"dirtyFields":["business.businessDescription","business.expectedVolume","business.currency"],"values":{"business":{"businessDescription":"通过自有网站销售家居用品","expectedVolume":"50000.00000000","currency":"AED"}}}
```

提交受理：
```json
{"applicationId":"100001","submissionId":"6b4df4d5-e910-49eb-9695-3d083a3c4bbd","status":"SUBMITTED"}
```

只读状态下写入：
```json
{"type":"about:blank","title":"Conflict","detail":"申请正在审核，暂时不能修改。","code":"APPLICATION_NOT_EDITABLE","requestId":"req_example","timestamp":"2026-09-14T16:00:00.000000Z"}
```

字段错误：422/VALIDATION_ERROR，errors.field 使用外部 JSON 路径；非法 Header/Path/Query 为 400。未登录 401，已认证但不具备操作权限 403，不可见资源 404，状态/同键异参/旧任务 409，限流 429，依赖暂不可用 503。错误码另包括 IDENTITY_CONFLICT、FILE_NOT_USABLE、REVIEW_TASK_STALE、CLASSIFICATION_NOT_CONFIRMED、REVIEW_REQUIREMENTS_INCOMPLETE；均不可带敏感值。

## 5. 保存、重提与一致性

C04 按已认证 Account 的 Client 入驻名额协调，不从 Body 读取账号。DRAFT/SUBMITTED/UNDER_REVIEW/RETURNED 合计最多一份；个人与组织共用名额。首次创建返回 201，恢复返回 200；相同幂等键仍返回原操作结果，原申请结束后重放旧键也不能新建。用户主动发起下一申请才使用新键。前端恢复后按 Owner 当前状态和 allowedActions 导航；类型冲突只提示继续原申请，不暗中更换主体。

C01 Start 返回至多一项 activeApplication、历史申请摘要与已授权工作区；已有活动申请不展示新建入口。DRAFT/RETURNED 导向原单页，SUBMITTED/UNDER_REVIEW 导向进度。HTTP/页面禁用不能代替 CCT 的唯一索引和并发检查。

F01 将 applicationId + subjectPartyId 交给 CCT 的本人申请查询/授权能力核对，并取得当前 Client 与可上传事实；只允许 DRAFT/RETURNED。Platform 依据可信 Account、CLIENT Channel 和本地 Member/Onboarding Role 解析唯一 PENDING Client Tenant，再核对其 owner_ref_id 等于 CCT 授权结果。目标为旧申请、投影未就绪、残留多个候选或唯一候选不匹配时均拒绝，不依赖“只有一份”跳过归属检查。该内部目标核对补充 PLT-I04，普通 Tenant 文件和 Partner 流程不变。

上传完成沿用原 uploadId 的 Tenant 与 uploader，不按当前申请重新分配文件。C12 仍显式关联原 applicationId 并重新校验可编辑状态、主体与 File 归属；旧页面不能自动把待上传或待关联材料转到后来创建的申请。

CCT 草稿写命令按 applicationId 协调并在本地事务中检查当前状态和子对象归属；跨应用共用的 Party 同时按稳定 Party ID 协调。永久身份唯一索引、业务状态条件及必要行锁是数据库最终保护。客户端每个分区保存单飞，退出前等待已发保存返回；不承诺跨标签页字段级乐观冲突检测。

提交前先在事务外查文件/规则等下游事实；本地短事务再次检查申请状态和本库输入、绑定稳定 submissionId、登记后续任务。不可变 File 字节及可用授权合同必须支持提交门禁；发生撤销/扫描状态变化不能用过期浏览器缓存通过审核。Risk 最终批准仍重新校验当前文件可用性。

退回结束原 Risk 任务，CCT 接受准确任务结果后才进入 RETURNED。重提生成新任务，原申请编号不变。旧任务迟到的批准、退回和分类结果均不能影响新任务。只用申请状态校验不足以处理 RETURNED→SUBMITTED→UNDER_REVIEW 的重复状态，必须核对 submissionId + caseId。

## 6. 兼容与注册

本稿只新增 CLIENT 路径和入驻 Operations 路径，不改既有 Operations 登录、Partner 或 Checkout HTTP。CCT 单份资料迁移必须同时调整 Checkout 读取方式，保留已批准 Profile 的稳定 ID 与实际消费的内容 Hash。旧 Risk source/version 合同与新 Client source/submission 合同分支识别，不填造假 Version=1。

Gateway Route、Permission、Console Resource 与 BFF Target 逐行注册计划由数据稿关联的注册清单提供。I* 不对公网暴露，所有新路由初始禁用；没有真实 CLIENT 认证、Owner 实现与权限门禁前不得启用。具体 DML 不能使用编造的环境 ID 或自动授予真实用户权限。


## 7. C04/C05/C06 的 CCT 私网组件

响应修订由[固定回执决定](./draft-command-response-proposal.md)批准。CCT `ClientDraftApi` 提供 POST `/client-onboarding/v1/applications`、GET `/{applicationId}` 与 PATCH `/{applicationId}/draft`；前缀相同不表示 BFF/Gateway 已注册或浏览器已可用。C05 在此是 Owner 主资料与人员/关系查询，完整 Application 仍由 BFF 聚合。

CCT 读取响应为 applicationId/applicationNo/clientId/clientType/status/savedAt/editable/values/people/relations/documents。values 按 individual 或 organization、address、identifier、business、contact 分区；不适用的个人/组织分区不返回，其余未填值为 null。documents 的 Owner 字段见第 10 节；没有伪造 Risk 任务、材料可用性或提交资格。editable 只表明当前申请的主资料是否可保存，不能代替正式提交检查。

每次操作先向 Platform `ClientAuthenticationApi.context` 核对当前 CLIENT 会话与已验证账号，再由 CCT 核对原申请归属。Body 不接收账号或租户。Caller 固定为 client-console-bff，仅防止私网调用误用；部署网络和 Gateway Header 清理仍承担入口可信边界。只改当前账号的 DRAFT/RETURNED，且主资料 draft_application_id 必须匹配、approved_time 为空；未知或他人申请统一 404。

草稿字符串去掉首尾空白，空白字符串按 null 清空；国家/币种转大写。金额仍是最多 16 位整数、8 位小数的十进制字符串，允许零草稿，禁止负数和指数形式；正式提交仍要求大于零。邮箱去首尾空白并转小写，电话仅接受规范化后的国际 E.164，不猜默认国家。普通写入与受控 Reveal 的原值均不进入普通请求/响应日志。

两种类型均原子建立空白主资料、主地址及候选证件行。个人地址用途为 RESIDENTIAL，组织为 REGISTERED；页面不用再选择地址用途。个人申请 qualification_type 精确为 INDIVIDUAL_KYC，组织为 ORGANIZATION_KYB，与既有 Risk 资格词表相同。本切片不调用 Risk，也不提前绑定永久身份键。

Processor 在 Redis 账号占位/申请资源锁内安排本地短事务；账户查询、Vault Protect/Reveal 都在数据库事务外。CCT 本库账号 active_slot 唯一键、申请和资料行锁负责最终并发保护；唯一键竞争失败回滚整次创建，再读取胜出申请。保存时在落库事务中重验冻结状态，避免 Protect 等待期间申请已进入审核。

本期沿用 CCT t_command_idempotency：作用域 CLIENT_ACCOUNT:<accountId>，操作 CLIENT_APPLICATION_CREATE/CLIENT_DRAFT_SAVE，成功状态 SUCCEEDED，response_ref 为 CREATED/RESTORED/SAVED:<applicationId>，update_time 为首次完成时间。请求摘要对规范化字段、申请目标、账号及操作做带固定域的 JCS/SHA-256；不保存原表单。按既有 Application 类保留基线设置 90 天，不新增清理任务；已存在命令即使过期也按原引用重放，不自动创建替代申请。

联系人使用现有 Vault V1 的 EMAIL_ADDRESS_V1/PHONE_E164_V1、REFERENCE 模式和 CLIENT_ONBOARDING_CONTACT 用途。未绑定的 Protect 结果不作为 CCT 已保存资料；不新增 Vault Bind/Retire 协议。正式环境的用途授权、私网发现及真实双 Owner 验证需单独完成。运行配置 `onboarding.draft-enabled` 默认关闭，并要求已有 CCT 数据源、恢复能力、Redis 和 Vault 配置；应用不创建或迁移业务表。当前实现与验收范围只见 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。


## 8. C07—C11 的 CCT 私网组件

`ClientPeopleApi` 沿用同一私网前缀、Caller/账号/Session Header、默认关闭开关和每次 Platform 会话核验。全部写命令由 CCT 核对申请归属、ORGANIZATION 类型、DRAFT/RETURNED 与主资料未批准；个人申请返回 422/ORGANIZATION_REQUIRED。人员/关系不属于当前申请时返回 404/PERSON_NOT_FOUND 或 RELATION_NOT_FOUND，不泄露其他申请资料。

C07 在一个本地事务创建 INDIVIDUAL Party、单份 Profile、个人类型表、RESIDENTIAL 地址、候选证件行和首条 DRAFT 关系。person 必须按 PersonPatch 提交至少一个 dirtyField；不要求一次填完姓名/证件。它不创建登录 Account、Client 或业务资料，不绑定永久身份键。C08 只更新人员的 individual/address/identifier，同一 Party ID 保持不变，其他分区拒绝。

C09 只给本申请已经关联的人员或本申请创建后暂时没有关系的人员追加类型。不能用任意 Party ID 搜索/关联全局个人；已批准共享人员只有已有授权关联时可用，资料保持只读。C10 保留组织和人员归属，只更新声明的关系属性；组织/人员/类型重复时返回 409/RELATION_ALREADY_EXISTS，修改冲突不覆盖原行。OWNERSHIP/UBO 比例的正式必填条件与 UBO 适用门槛仍归正式提交/适用审核要求，草稿保存不自行推断司法辖区规则。

C11 只物理移除当前申请的 DRAFT 关系，不删除 Party、Profile、其他关系或已存在的审核事实。无 Body，传额外内容返回 422。移除最后一条关系后，本申请创建的人员仍出现在 people，可继续编辑和重新添加关系；重新添加会产生新的 relationId。ACTIVE/ENDED 关系不可修改或移除。批准共享人员不再有本申请关联时不再出现在本申请列表。

C05 的 people 为 partyId/editable/values（仅 individual/address/identifier）；relations 为 relationId/relatedPartyId/relationType/ownershipPercentage/effectiveFrom/effectiveTo/status/editable。个人申请两列表为空。人员独立检查 approved_time、draft_application_id、其他申请/非草稿关系及冻结合同、Partner/OE 批准引用；任一命中即只读，不允许通过新申请覆盖旧事实。引用关系不能授权读取另一申请仍在编辑的草稿。editable 仅表示对应资料/关系可保存，不是正式提交或审核通过的资格。

所有命令先协调 `client-onboarding:application:<applicationId>`；C08 与 C09 同时协调稳定 `cct:party:<partyId>`，Foundation 按完整 Key 的稳定顺序取得全部锁。事务中先锁申请/主资料，再核对人员/关系行、状态与唯一性，最后原子保存业务对象和固定回执。Redis 租约过期时仍依赖本库锁和唯一键；会话 HTTP 在事务外，不新增跨系统写调用。后续其他 CCT Party 写入口须使用同一 Party 协调域并锁定该 Party/Profile，不能以不同入口前缀绕过共享事实保护。

普通 C07—C11 不生成表单/关系版本、BCR 快照或补偿 Payload；既有 CLIENT 生命周期恢复机制保持原用途。命令操作精确为 CLIENT_PERSON_CREATE/CLIENT_PERSON_SAVE/CLIENT_RELATION_CREATE/CLIENT_RELATION_SAVE/CLIENT_RELATION_REMOVE，其余摘要、保留和原回执规则沿用第 7 节及固定回执决定。不新增表、字段或 SQL 迁移。当前验证及未完成范围仍只见[实施台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

新增人员示例：
```json
{"person":{"dirtyFields":["individual.givenName"],"values":{"individual":{"givenName":"示例人员"}}},"relation":{"relationType":"DIRECTOR","effectiveFrom":"2026-01-01"}}
```

同一人员追加关系示例：
```json
{"relatedPartyId":"100002","relationType":"OWNERSHIP","ownershipPercentage":"75.5","effectiveFrom":"2026-01-01"}
```

## 9. 文件授权与材料要求私网查询

2026-09-15 用户确认[文件合同补充](./file-upload-closure-proposal.md)。新增 GET `/internal/client-onboarding/file-targets`（CCT）、GET `/internal/onboarding-files/files/{fileId}/verification`（Platform）、GET `/internal/due-diligence/material-requirements`（Risk）；精确参数、权限、错误、固定回执及恢复语义以补充稿第 3—6 节为准，不通过公共 Gateway 暴露。CCT 目标查询不回调 Platform，以免循环授权。

## 10. C12/C13/C05 材料的 Owner 组件

`ClientDocumentApi` 沿用 CCT 的 BFF Caller、Account/Session 和 `onboarding.draft-enabled` 开关。C12 请求严格为 subjectPartyId、requirementCode、documentType、fileId；不接受客户端摘要、租户或存储坐标。主体只能是原申请主主体，或组织申请中仍有 DRAFT/ACTIVE 关系的可见人员。先在事务外核对 Platform 当前会话、Risk 真实要求和 Platform 原文件，最后在申请行锁内重新检查状态、主体、国家及幂等键，并原子保存引用和固定回执。

国家映射暂按已告知的实现假设：个人及关联人员使用 `residenceCountry`，组织使用 `incorporationCountry`；用户尚未确认此口径，国籍仍保留为审核资料。没有国家不调用默认规则；正式规则配置前需确认适用口径。该选择不代表已批准任何国家材料政策。

C05 的 documents 为 documentId、subjectPartyId、requirementCode、documentType、fileId、subjectInScope；不返回内部 contentHash，不复制 Platform 文件状态、下载 URL 或 Risk Program 修订。个人主主体始终在本申请范围内；组织关联人员需保留有效关系。移除人员关系后原材料仍可见，subjectInScope=false；不重新挂到其他人，也不再允许为该人员新增材料。保留材料可经原文件 READ 授权查询；移除最后一份材料且没有可见人员关系后，不继续以该旧引用授权读取。

C13 只删除本申请引用，不删除 File、存储字节、Party 或 Risk 审核证据。新命令只允许 DRAFT/RETURNED；所有终态、审核中均冻结。相同键始终返回原结果：C12 为首次 201 回执，C13 为 204；删除后重放 C12 不重建引用。不同键重复关联相同主体/要求/文件返回冲突。命令操作为 CLIENT_DOCUMENT_ASSOCIATE/CLIENT_DOCUMENT_REMOVE，内部回执引用为 `D:<documentId>:<subjectPartyId>:<fileId>`，最大 61 字符，复用既有 64 字符字段，不增加资料版本或表结构。

| CCT 错误 | HTTP | Client 的处理 |
|---|---:|---|
| DOCUMENT_NOT_FOUND | 404 | 刷新原申请；不展示其他申请或账号的材料存在性 |
| DOCUMENT_ALREADY_EXISTS | 409 | 刷新当前材料列表，保留已有引用 |
| MATERIAL_COUNTRY_REQUIRED | 422 | 先填写对应主体的国家，再读取材料要求 |
| MATERIAL_REQUIREMENT_INVALID | 422 | 刷新 Owner 材料要求，再选择对应材料项 |
| MATERIAL_FILE_UNAVAILABLE | 409 | 重新查询文件状态；扫描未通过或对象无效时不关联 |
| MATERIAL_CONTEXT_CHANGED | 409 | 原申请资料在外部查询期间改变，刷新后重新操作 |
| ONBOARDING_DEPENDENCY_UNAVAILABLE | 503 | 保留当前材料，提示稍后重试；不得视为无需材料 |

Platform 文件私网验证仅接受 `client-contract-service` Caller；Risk 非个人要求目录允许 CCT 与 Client BFF，准确范围见第 11 节。HTTP 状态、资格和用途使用稳定 String 代码，提供方在 API enums 发布定义，消费方显式拒绝未知代码。Platform 当前证据 checkedAt 超过 30 秒或领先本机超过 5 秒时，CCT 返回依赖不可用。ASSOCIATE/READ 均返回真实扫描/对象状态；能查询不等于可以关联。

F03 只读文件元数据，不触发扫描；F04 空 Body、逐次授权，仅对 ACTIVE + AVAILABLE 签发最长 60 秒的 HTTPS GET 下载授权。存储适配器必须使用原不可变版本，限制可信存储域和响应时间；授权返回后再次核对会话、原申请授权、IAM 和当前 File。下载 URL 不落库或进入普通日志，真实适配器验收仍是启用前条件。当前实现与验证范围统一见[实施台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

## 11. 资料与材料页面的 BFF 投影边界

本节限定 C01/C03/C05 的资料与材料阶段投影；上文完整目标中的 workspace/currentReviewTask/提交就绪仍由各自后续用例提供，不能补造默认值。实际完成和验证状态只见 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。本节不新增外部操作、资料版本或数据库表。

- CCT `ClientOnboardingStartApi` 的 `GET /client-onboarding/v1/start` 只读当前账号最近申请。可信 Caller 为 `client-console-bff`，每次先验证真实 CLIENT Account/Session；不接受 Query/Body。响应 `applications` 每项为 applicationId/applicationNo/clientType/status/active/savedAt，活动申请优先、随后 applicationId 降序，最多 20 项；`hasMore` 明确表示历史截断。BFF 增加 `activeApplicationId`，进入或刷新页面不能隐式调用 C04。该导航投影暂不提供工作区切换。
- CCT C05 增加真实 `subjectPartyId`（主 Party ID），与 `clientId` 分开，不允许浏览器用客户 ID 推算上传主体。其余 Owner 字段及固定命令回执保持；不会在 CCT 聚合 Risk 或 Platform 的页面数据。
- BFF C05 保留原表单、`editable`、人员和关系，增加 `materialSubjects`：subjectPartyId、qualificationType、countryCode、requirementsStatus、requirements。requirements 每项为 requirementCode/documentType/requiredFlag。状态固定 AVAILABLE / COUNTRY_REQUIRED / UNAVAILABLE；未填写国家或依赖不可用不能推断“不需要材料”。只为主主体与当前 DRAFT/ACTIVE 关系人员查询；移除关系后的材料引用仍保留。
- BFF `documents` 保留 CCT 的 documentId/subjectPartyId/requirementCode/documentType/fileId/subjectInScope，并增加 `fileStatus=AVAILABLE|UNAVAILABLE` 与 `file`（F03 的原元数据或 null）。这里 AVAILABLE 仅表示读取成功；允许使用还必须看 File scanStatus/objectStatus/allowedActions。某个文件不可读时不删除引用或保留过期 DOWNLOAD 权限；认证失效则整页失败。C05 不签发下载 URL，不返回 Program/修订 ID 或内容摘要。
- BFF 只读聚合前后核对 Platform 会话。使用最多 8 个并发读取、3 秒总等待预算；依赖响应最大 512 KiB。当前页面上限 100 个人员、500 个材料引用，超出则返回不可用，不能静默截断。相同 fileId 在本次聚合只读一次。无持久化页面缓存、数据库访问或跨系统写事务。
- C03 使用准确 clientType=INDIVIDUAL|ORGANIZATION 与可选 uppercase countryCode，返回 clientType/countryCode/requirementsStatus/requirements。受控组织类型、证件及签发机构、经营模式目录按第 12 节由 CCT 提供。草稿仍按既有字段合同保存，不能将草稿代码或目录 AVAILABLE 视为正式提交时已完成校验。国家选择用于资料采集，不代表业务准入名单或材料规则已配置。
- Risk `MaterialRequirementsApi` 仅对 `client-contract-service` 与 `client-console-bff` 开放非个人通用材料目录，不提供任意 Case/主体资料。Platform 私网文件 verification 仍仅允许 CCT；BFF 只使用 F01—F04。
- C04/C06—C13 由 BFF 将原命令键和可信账号/会话交给 CCT；F01—F04 交给 Platform。BFF 不把一个写请求扩展成多 Owner 业务事务。路径 ID 必须为规范正 BIGINT，写请求保持精确 HTTPS Origin，空 Body、唯一 Header/Query、JSON 结构和幂等键沿用原合同。

Web 为一次写意图冻结 Body 和幂等键，网络/依赖失败只重试原意图；成功写入后读取页面失败也先恢复原回执，不创建第二名人员。登录刷新不得把原账号命令带到另一账号。资料值只留内存，不存入浏览器持久化存储。

材料处理顺序为 F01 → 受控 PUT → F02 → F03 → C12。F02 未确认时只重试原 completion，不再 PUT；扫描未完成不关联，关联响应未知只重试原 C12。浏览器按账号＋申请隔离保存单个上传的非敏感恢复坐标（上传/文件/主体/要求 ID 和原命令键），仅在 PUT 成功后落 sessionStorage。文件字节、文件名、临时 URL/Header、Token 和表单不落存储。刷新可恢复完成确认/扫描/关联；尚未成功传输的阶段只支持本页原文件重试，关闭页面后需重新选择，旧未完成 Upload 按原到期规则处理。一个页面同时处理一份材料；替换恢复记录前明确提示。F04 每次重新取得短时 HTTPS 授权，拒绝过期、非 HTTPS、userinfo 或 fragment；对象存储 PUT 不携带 Client Bearer/Cookie，且禁止重定向。


## 12. C03 受控表单目录

本节细化已批准 C03，不新增外部操作、资料版本或数据库表。CCT 通过 `ClientFormOptionsApi` 提供相同私网 GET 路径及 clientType/countryCode 参数；必须校验可信 BFF 调用方、当前账号和 CLIENT 会话。目录查询不创建申请或绑定永久身份键，不增加本地数据库事务。实际完成范围仍只见当前实施台账。

CCT 响应为 `clientType/countryCode/catalogStatus/organizationTypes/businessModels/identifierTypes`：

- `organizationTypes`、`businessModels` 每项仅 code/label。组织类型按准确注册国筛选；个人始终没有组织类型。经营模式按个人/组织类型筛选，不替代行业/MCC。
- `identifierTypes` 每项 code/label/issuers；issuer 每项 scopeKey/label/issuingCountry。证件适用个人或组织；签发机构是明确配置的独立作用域，不能从居住国或注册国拼造。证件可能由不同于居住国的国家签发，所以不按资料国家裁剪签发机构。GLOBAL 必须显式配置且 issuingCountry=null，其余作用域必须明确签发国。
- `catalogStatus` 为 AVAILABLE、COUNTRY_REQUIRED 或 UNAVAILABLE。经营模式或证件集合缺失时不可用；组织未选注册国时 COUNTRY_REQUIRED，已选国家但无组织类型时 UNAVAILABLE。仍可返回确定的其他目录，不能解释为允许提交。
- BFF 使用有界并发查询 CCT 目录与 Risk 要求，保留两个独立状态。CCT/网络异常返回 catalogStatus=UNAVAILABLE 和空目录；Risk 异常返回 requirementsStatus=UNAVAILABLE。未选国家时不查询 Risk，材料状态为 COUNTRY_REQUIRED。当前 CLIENT 会话失效则整个请求失败，不降级成匿名目录。BFF 不返回 Risk 程序修订或规范化规则。

目录由 CCT 的 `onboarding.form-catalog` 启动配置维护：organization-types 使用 code/label/country-code；business-models 使用 code/label/client-types；identifier-types 使用 code/label/client-types/issuers，每个 issuer 使用 scope-key/label/issuing-country。默认三个空集合，修改后重启。每类最多 100 项，单类型最多 50 个机构，机构总计最多 300；代码最长 64、名称最长 128。重复组织类型（国家＋代码）、重复经营模式/证件代码、同证件重复作用域、非法客户类型和 GLOBAL/国家冲突都拒绝启动。不初始化真实国家政策，示例仅存在测试配置。

页面在主资料与关联人员编辑中使用准确目录下拉；变更注册国时清空原组织类型，变更证件类型时清空原机构和签发国，选择机构后带出其签发国。加载/重试不会自动清空表单；旧目录代码保留并显示“待确认”，不能被浏览器自动替换成第一个新选项。各次异步目录响应绑定原类型＋国家，旧响应不能覆盖新选择。所有变更仍经过原 dirtyFields 保存与 Owner 校验。

本节不定义或批准任何具体国家的证件号码规范化算法。正式提交必须另外验证完整组合、按已批准规则规范化并完成永久身份绑定；不能用目录可选、草稿保存成功或 HTTP 200 代替该门禁。国家材料口径和第一批国家范围尚待明确；人工审核仍由 Risk/Operations 拥有。
