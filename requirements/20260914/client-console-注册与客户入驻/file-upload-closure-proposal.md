# 入驻材料上传与关联：已确认合同补充

本提案涉及 Platform、CCT、Risk 及 Client BFF/Web；拟新增 3 条私网只读查询，调整 F01、F02、C12 三条写接口，补充 C05/F03/F04/C11/C13 的查询和删除规则；本机候选 SQL 新建 7 张已有模型对应表，共 91 列、7 个主键、13 个唯一索引、11 个普通索引，初始化 DML 为 0，新增功能页面为 0。

**状态：2026-09-15 用户回复“可以”确认，进入研发。** 本文在明确列出的文件合同范围内取代前序表述；原评审 SQL/字段清单作为批准输入保留，现行模型及 Schema 同步实施。设计确认不授权数据库执行。适用范围只是在已批准入驻流程中补齐文件归属、完成回执及恢复机制。当前代码、验证和环境状态统一见[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

## 1. 需要补充的原因

| 现行合同或字段 | 不能完整表达的行为 | 本提案 |
|---|---|---|
| F01 只有 applicationId；C12 才有 subjectPartyId | 上传授权不能固定“这份申请的这个人/组织”，无法验证错主体复用 | F01 增加 subjectPartyId，CCT 授权后保存准确的申请与主体坐标 |
| Upload 只有 Tenant、上传人、预期大小/MIME、上传秘密 Hash | 中断恢复时没有原申请、文件展示名、存储目标和扫描策略 | 增加 8 个字段，完成时从原 Upload 恢复，不根据账号当前申请重选 |
| F02 返回当前 File、C12 返回当前材料及 allowedActions | 文件/材料后来改变或删除后，无法稳定重放首次写操作 | 写命令返回固定回执；当前状态由 F03/C05 查询 |
| 已建模扫描策略/结果和补偿表 | File 提交后必须能在进程重启后继续扫描；GET 不能成为触发器 | 本地提交原子登记持久化扫描任务，使用 Foundation 现有恢复机制 |
| C12 要求准确 Requirement/Subject，C03 要展示要求 | 必须由 Risk 的真实程序目录决定，不能把前端传入代码视为合法 | 补齐窄的 Risk 要求查询；未配置、歧义或不可用时不返回“无需材料” |

只读环境核对见[前置证据](./assets/stage4/file-prerequisites-preflight-20260915.json)。证据分别连接各 Owner 数据库，仅检查元数据和计数，不查询个人资料，不包含 DDL/DML。它是当时观察，不是后续执行授权。

## 2. 用户看到的流程

个人申请的材料默认属于主个人主体；组织申请先选择“组织本身”或已经添加的相关人员。选择文件后依次展示上传中、检查中、可关联、失败/不可用。检查未完成或依赖不可用时保留真实状态和重试入口，不显示“上传成功即可提交”。

文件达到 AVAILABLE 后才调用 C12 关联到当前申请的材料项。更换材料先上传并关联新文件，再移除旧引用；任一步失败时旧引用仍可查询，用户可以继续操作。C13 只移除申请材料引用，不删除存储对象。批准后材料集合也只读；退回后继续修改原申请。

沿用现有[页面与交互合同](./ui-design.md)中的材料区、上传/更换抽屉和预览状态，不新增页面。下一研发增量只补接真实状态与动作，不恢复多页填写或资料版本选择。

## 3. 各系统职责及新增私网查询

| 系统 | 本次职责 | 数据变化 |
|---|---|---|
| Platform | Session/Tenant 授权、Upload、不可变 File、扫描与受控下载 | Upload 字段补充；六张文件表和补偿表同步现行模型及 Schema |
| CCT | 申请/主体授权、材料引用、可编辑状态和提交前再次校验 | 复用 t_application_document，不新增材料表 |
| Risk | 按受控配置确定适用程序，提供真实 requirementCode/documentType/requiredFlag | 复用既有 Program/Program Version/Requirement，不创建客户资料版本 |
| Client BFF | 聚合 CCT 引用与 Platform 当前文件状态，转发命令 | 无业务库、不编排跨系统事务 |
| Client Web | 上传、扫描状态、关联/移除、准确主体选择 | 沿用现有页面与邮箱密码身份 |
| Gateway/Operations | 沿用既定边界；后续审核复用文件可用性查询 | 本提案不新增公共路径或运营页面，不激活路由 |

下列三个查询均走私网 Owner API 模块，由 HTTP Client 调用；BFF 不访问数据库。路径是本提案的已确认合同，纳入现行 API 清单。

| 查询 | 请求 | 响应及权限 |
|---|---|---|
| GET `/internal/client-onboarding/file-targets`（CCT） | applicationId、subjectPartyId、accountId；ID 均为规范正整数十进制字符串 | 返回 applicationId/clientId/subjectPartyId、clientType、canUpload、canRead；Platform 必须先验证真实 CLIENT Session。CCT 核对原账号与原申请，以及主主体/本申请允许的相关人员，返回最小事实，不包含 PII |
| GET `/internal/onboarding-files/files/{fileId}/verification`（Platform） | expectedApplicationId、expectedSubjectPartyId、expectedClientId、requestingAccountId、purpose=ASSOCIATE/READ；ID 同上 | 校验固定目标、原 Tenant 和调用者权限；返回 fileId、uploadId、contentHash、sizeBytes、mimeType、scanStatus、objectStatus、checkedAt。没有下载 URL、存储坐标或凭据。后续 Risk 审核用途按其已批准审核授权衔接，不把 Client Account 参数伪装成运营权限 |
| GET `/internal/due-diligence/material-requirements`（Risk） | qualificationType=INDIVIDUAL_KYC/ORGANIZATION_KYB/RELATED_PERSON_KYC、countryCode；值由 CCT 当前主体资料派生 | 返回精确 programId/programVersionId 与 items(requirementCode、documentType、requiredFlag)。Risk 使用明确的适用程序映射，不挑第一条 ACTIVE；缺国家、未配置、无有效修订或映射歧义返回明确 unavailable，不伪造空的合格清单 |

Risk 的适用程序映射由 Risk 持有，键为 qualificationType + countryCode，值为明确 programCode；本期只实现有界配置读取和程序/修订/要求查询，不增加通用程序编辑平台。未配置国家不自动继承其他国家要求。真实映射、必需材料及检查必须由业务确认后配置；测试使用标注为合成的程序夹具。用户没有授权任何国家材料规则或默认通过策略。

内部入口遵守现有监听隔离、身份上下文和错误合同；不能把可伪造的浏览器 Header 当成服务授权，也不能把私网 MVP 防护描述为生产强身份认证。查询不得递归形成 Platform→CCT→Platform 的授权循环：Platform 核验 Session/IAM，CCT 校验本库资源归属，各自保留决策。

## 4. 公共接口差异

沿用[API 合同](./api-contract.md)的 ID、UTC 时间、Problem Details 和 CLIENT 鉴权约定，未知字段拒绝。以下差异已确认，按本合同研发。

| 操作 | 拟议请求/响应 | 幂等及异常 |
|---|---|---|
| F01 POST `/onboarding-files/v1/uploads` | UploadStart 增加必填 subjectPartyId；其余仍为 applicationId、filename≤255、mimeType≤128、sizeBytes 正整数字符串。增加必填 UUID Idempotency-Key。返回 uploadId、临时上传授权、expiresAt；HTTP 201 | 相同账号/操作/键和相同请求定位原 Upload；不同目标或内容返回 409。临时授权可以重新签发，但只能使用原目标和原到期时间，不能延长上传生命周期。已完成或已失效时不再签发上传权限 |
| F02 POST `/onboarding-files/v1/uploads/{uploadId}/completion` | 请求继续为空。增加必填 UUID Idempotency-Key；HTTP 200 返回固定 FileCompletionReceipt：commandId、uploadId、fileId、completedAt | 不接收客户端 Hash/对象 Key/文件大小证明。相同 uploadId 只创建一个 File；原键重放原回执，扫描结果从 F03 读取。外部查询超时不能登记成功；已有成功后重放不重新校验或创建文件 |
| C12 POST `/client-onboarding/v1/applications/{applicationId}/documents` | DocumentReference 字段不变；HTTP 201 改为固定 DocumentCommandReceipt：commandId、applicationId、documentId、subjectPartyId、fileId、operation=ASSOCIATED、completedAt | UUID Idempotency-Key 沿用草稿命令约定。先核对当前账号及原资源，再重放已成功回执；引用已删除时同一键仍返回原回执，不恢复引用；新键重新校验当前状态 |
| C05 GET 当前申请 | documents 返回 CCT 当前引用；BFF 聚合 Platform 当前文件状态、展示名和动作 | File 查询失败显示 unavailable；不能使用缓存状态放行关联、下载、提交或批准 |
| F03 GET File / F04 POST download | 字段保持原合同；READ 校验准确原申请和主体。F04 只对 ACTIVE + AVAILABLE 的准确对象版本签发短时下载授权 | F03 纯读取，不驱动扫描；F04 每次重新授权，URL 不写普通日志/审计，不作为稳定命令回执 |
| C13 DELETE 原引用 | 无 Body；UUID Idempotency-Key；204 | 原键重放 204，不再次删除；删除后用新键访问不存在引用返回 404。只删除本申请可编辑引用 |

F01 示例（主体 ID 来自当前申请主主体或人员选择）：

```json
{
  "applicationId": "1001",
  "subjectPartyId": "2002",
  "filename": "identity.pdf",
  "mimeType": "application/pdf",
  "sizeBytes": "20480"
}
```

C12 成功示例：

```json
{
  "commandId": "9130966a-3d7e-4c80-af9b-d842b7b0b5b3",
  "applicationId": "1001",
  "documentId": "6001",
  "subjectPartyId": "2002",
  "fileId": "4001",
  "operation": "ASSOCIATED",
  "completedAt": "2026-09-15T12:00:00.000000Z"
}
```

校验类错误保持 RFC 9457：缺失/错误字段 400，失效身份 401，原资源不属于调用者 404，状态/目标/幂等冲突 409，违反明确材料要求 422，Owner/配置/扫描能力不可用 503。Platform 依据真实策略校验文件大小/MIME；客户端声明不替代服务端字节验证，安全检测失败只暴露安全文案。

## 5. 数据提案和兼容边界

精确类型、可空性、注释、索引和 SQL 指纹见[机器清单](./db/file-upload-closure-proposal.json)，完整定义见[七表候选 SQL](./db/platform-files-create-review.sql)。

### 5.1 Upload：增加 8 列、删除 1 列

| 增加字段 | 类型 | 原因 |
|---|---|---|
| owner_system | VARCHAR(64) NOT NULL | 可信来源 Owner，Client 入驻固定 CCT |
| owner_type | VARCHAR(64) NOT NULL | 受控资源类型 CLIENT_APPLICATION_SUBJECT |
| owner_ref_id | VARCHAR(128) NOT NULL | Owner 定义的准确坐标 applicationId:subjectPartyId；不包含姓名 |
| display_name | VARCHAR(255) NOT NULL | 清除路径与控制字符后的展示名，不能作为存储 Key |
| storage_provider | VARCHAR(64) NOT NULL | 固定原适配器代码，恢复不跟随默认配置变化 |
| storage_container_ref | VARCHAR(255) NOT NULL | 固定原存储容器，不含凭据 |
| storage_object_ref | VARCHAR(255) NOT NULL | Platform 生成的高熵写入目标，不使用原始文件名 |
| scan_policy_id | BIGINT NOT NULL | F01 固定策略修订，F02 的 File 继承原值 |

删除 upload_token_hash 及 uk_upload_token：实际上传授权由存储适配器签发；F02 使用当前 CLIENT Session 与原 Upload 授权，不再引入没有调用方的第二套上传秘密。新增唯一索引 uk_upload_storage_target(storage_provider, storage_container_ref, storage_object_ref)，防止两个 Upload 共享可写目标。其余原字段与访问索引保留。

保留 Upload/File 的技术并发字段、不可变存储对象版本及扫描策略修订，它们用于竞争保护、准确字节和扫描恢复；这些不是客户填写资料的版本，也不向客户提供版本选择。

File Owner Ref 固定继承 Upload 的 Owner 三元组，purpose_code 为受控入驻材料用途。File 的 displayName 读取本库原 Upload；不额外复制到 File 或 CCT。禁止通过追加另一 Owner Ref 把原上传文件转到另一申请/主体。CCT 的 content_hash 只记录 Platform 确认的准确文件内容。

Upload 的 MIME 配置编码由 Platform 持有：`rule_schema_version=1` 时，`allowed_mime_rules` 为仅含 `mimeTypes` 的 JSON 对象，例如 `{"mimeTypes":["application/pdf"]}`；列表须非空、去重，最多 64 项，每项为规范小写 MIME，JSON 最多 8 KiB。创建必须选择明确 `policy_code` 的唯一有效 ACTIVE 修订，并至少含一项扫描要求；缺配置、未知结构或损坏规则均返回不可用。示例仅解释编码，不是批准真实扫描策略或材料要求。已创建 Upload 的重放继续使用原策略与到期时间。

### 5.2 持久化扫描恢复

Platform 的补偿模型需要针对这个真实消费者对齐现行 Foundation 15 列运行合同；候选 SQL 复用 CCT 已验证的定义，不引入第二套任务状态机。相对旧 Platform 20 列模型：新增 sub_event_type，删除 execution_type/source_type/source_id/source_version/schema_version/payload_hash 共 6 列，并按现行合同对齐 payload_json、lock_version、默认值、索引及注释。确认后逐项同步生成器/PDManer/Schema，不能只加入一份孤立 SQL。

本机候选是七个 CREATE，因为目标验证库缺少这七张表；不是向现行业务模型新增七个概念。六张文件表中只有 Upload 结构改变，扫描补偿表另行对齐；CCT/Risk 本轮无 DDL。现行认证表已有后续修订，禁止整份重跑旧 platform-additions-review.sql。

本次无 INSERT/UPDATE/DELETE 初始化，无实际扫描策略、扫描通过结果、Risk 材料要求、权限或 Route 注册。批准设计后仍需另行核对精确数据库、缺表状态、备份和指纹，取得 SQL 动作授权；非空环境不能使用这个 CREATE 候选代替迁移方案。

## 6. 并发、事务及失败恢复

1. **申请目标授权。** Platform 先验证 CLIENT Session，CCT 校验原账号/申请/主体。写入只允许 DRAFT/RETURNED；相关人员必须通过当前申请合法关系可见。Platform 解析唯一 PENDING Client Tenant、ACTIVE Member 和入驻 Owner 角色，并核对 Tenant owner_ref_id 等于 CCT 返回的 Client；零个、多个、旧申请或不匹配均拒绝。
2. **创建 Upload。** 在 Platform 本地短事务固定目标、策略、存储坐标、有效期与幂等记录；事务提交后才请求存储适配器签发授权。授权失败重试原 Upload，不能创建替代目标。文件名、临时 URL、签名和文件原文不写普通日志或通用审计。
3. **确认完成。** 沿原 Upload 重新授权，事务外检查实际存储对象的大小、MIME、SHA-256、加密保护和准确不可变版本。对象必须已经不可变，或在受控完成操作中固定为不可改写版本；迟到的上传 URL 不能改变随后扫描/下载的字节。ETag 或浏览器 Hash 不作为 SHA-256 证明。
4. **原子登记 File 与扫描任务。** 以 uploadId 为资源锁，短事务内重查状态，唯一创建 File、Owner Ref、固定完成回执和扫描任务；失败全部回滚。File 初始 PENDING_SCAN，objectStatus=ACTIVE 不代表可用。事务只操作本库，不调用存储、扫描器、CCT 或 Redis。
5. **扫描恢复。** LOCAL_HANDLER 以 fileId 定位上下文、payload_json=NULL。先读本库固定策略和要求，事务外扫描准确版本，再短事务追加 Scan Result 并计算状态。外部请求使用稳定的 fileId/requirementId/attempt 身份；适配器必须能重放或恢复同一次检查。暂时不可用不写伪造终局结果；确定结束并需新检查时才增加 attempt。重启/租约失效后按 Foundation Claim/Lease/CAS 接管，旧执行者不得覆盖新结果。
6. **可用性。** 只有全部必需 Requirement 都有符合冻结策略的接受结果才变 AVAILABLE；拒绝结果按现行扫描状态表达，未知、超时、缺配置保持不可用。任务重试耗尽保留失败任务并告警，不变成自动通过。存储/扫描供应商未确定时，只完成通用 Port 及测试适配器，生产能力默认关闭或明确不可用。
7. **关联材料。** CCT 在事务外查询 Risk 适用要求及 Platform 文件验证，再以 applicationId 为资源锁，在本库短事务重查可编辑状态、关系与主体，写 t_application_document 和固定回执。File 必须属于同一申请/主体/Client，并且 ACTIVE + AVAILABLE。不能从浏览器接受 Hash、程序身份或可用性判断。
8. **后续变化。** 跨 Owner 的查询是当时有效证据，不承诺分布式原子锁定。关系、要求和文件可能随后变化；提交及最终批准必须再次读 Owner 权威并校验准确字节。C11 移除最后一条人员关系时保留原 Party/File/材料引用；C05 标记该材料主体已不在当前申请范围，提交拒绝，用户显式移除引用或重新建立合法关系，禁止悄悄改挂他人。

普通材料关联/移除不写表单历史或 BCR 快照。审核任务已有冻结证据继续归 Risk，不因草稿材料删除改写历史任务。F03、C05 及私网查询全部保持只读。

## 7. 研发顺序与验收

确认本提案后，继续当前阶段 4：先同步 API/模型/Schema/边界测试，再实现 Platform 文件 Owner 与持久化扫描恢复；随后实现 Risk 窄查询、CCT C12/C13/C05，以及 BFF/Web 接线。正式提交和 Operations 审核仍按原研发计划继续，不用文件测试替代审核验收。

Platform 上传配置使用 `files.onboarding`：`enabled` 默认关闭；新建 Upload 要求明确的 `storage-provider`、`storage-container-ref`、`scan-policy-code` 及唯一匹配的真实存储适配器。`upload-seconds` 默认为 900，范围 60—900 秒；重放不读取新默认目标来替换原值。CCT 通过私网服务发现调用，仅 `auto` 测试配置可使用显式 loopback HTTP Simulator。

对象存储/扫描供应商尚未确定时按通用 Port 设计，供应商 SDK 只能在适配器；不安装 MinIO/LocalStack、不配置外部资源。验证复用仓库真实 MySQL/Redis，存储与扫描使用测试作用域协议 Simulator；不将 Simulator 放入生产业务配置，也不声称真实上传扫描已上线。

| 验收项 | 必须观察的结果 |
|---|---|
| 归属隔离 | 错账号、错申请、错主体、残留 Tenant、唯一但不匹配 Tenant、旧申请文件均不能关联到新目标 |
| 字节与恢复 | 声明大小/Hash 伪造被拒；上传授权响应丢失沿原目标恢复；F02 重复/并发只创建一个 File；迟到覆盖不改变固定文件 |
| 扫描门禁 | 待扫描、部分要求通过、恶意/格式拒绝、依赖失败和策略缺失均不 AVAILABLE；全部要求完成才可用 |
| 持久化竞争 | File/OwnerRef/任务/回执原子回滚；进程重启、租约失效和重复结果不会漏任务、重复成功或覆盖新结果 |
| 材料行为 | C12 只接受准确要求和主体；C13 不删除 File；删除后旧命令只返回原回执；人员移除后材料不静默改挂且不能提交 |
| 授权与冻结 | 新写操作重查 DRAFT/RETURNED；批准、最终拒绝及提交中无法修改；下载每次重查权限和文件可用性 |
| 前后端一致 | C05/F03 当前状态与固定命令回执分开；上传等待/失败可恢复；个人与组织材料主体显示正确；日志无敏感内容和临时授权 |

真实扫描策略/程序要求的配置及供应商验证属于后续环境验收门禁。设计确认并不授权伪造这些事实；配置缺失时提交、批准和扫描通过保持关闭。

## 8. 确认范围与执行入口

用户已确认主体绑定、固定回执、Upload 字段和扫描恢复补充，按第 7 节继续研发并同步现行合同和模型。[本机七表执行清单](./db/implementation/platform-files-local-execution.md)单独申请 SQL 授权；外部服务配置、部署及提交仍不包含在此次确认内。

重新确认的原因是[需求交付流程第 2 节第 6 条](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md#2-每次确认的规则)要求：“若新增范围、改变合同、兼容策略或验收标准，更新对应成果并重新确认受影响部分”。这里改变了已批准 F01/F02/C12 的输入或响应及两张表模型；其他已批准决定和已完成代码继续有效。
