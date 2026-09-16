# Client 注册入驻：单份资料与审核任务数据设计

本稿是阶段 3 目标设计。已批准方向见[简化设计](./simplification-design.md)，外部 JSON 字段见[API 合同](./api-contract.md)。以下 CREATE TABLE 是受影响表的目标定义，既不是完整建库脚本，也不是可以直接应用到现有库的迁移。

## 1. Owner 与表差异

| Owner | 目标变化 | 精确工件 |
|---|---|---|
| CCT | 修改 10 张既有表；新增 12 列、删除 27 列、重命名 1 列、调整 21 项可空性 | [CCT 目标表](./db/cct-target-tables.sql) |
| Risk | 修改 3 张既有表、新增 1 张 8 列任务分类表；既有表新增 5 列、调整 2 项可空性 | [Risk 目标表](./db/risk-target-tables.sql) |
| Platform | 7 张已经建模但当前 Schema 未包含的认证/文件表，共 90 列；未新增 Google/OTP 依赖 | [Platform 补充表](./db/platform-additions-review.sql)、[清单](./db/platform-additions-inventory.json) |

完整列名、类型、长度、默认值、注释、原字段和索引见[字段投影](./db/target-field-projection.json)。所有物理表仅使用主键、唯一索引、普通索引和必需的 NOT NULL；不创建 CHECK 或 FOREIGN KEY。模型关系不是跨库外键，任何 Owner 均不得直接访问另一个系统数据库。

| CCT 表 | 调整理由 |
|---|---|
| t_party_profile | 每个 Party 唯一一份；删除 Profile 版本/状态/生效区间，增加 draft_application_id 和 approved_time；批准 Hash 保留真实 Checkout 消费者 |
| t_individual_profile / t_organization_profile | 基本字段允许 NULL 支持分区保存；正式提交执行类型对应的完整校验 |
| t_party_address | 允许未填写的地址、城市、国家为空；不得用占位国家满足约束 |
| t_party_contact | 删除无本期消费者的 Vault Record Version/Metadata Hash，保留授权 Reveal 所需 Token 与显示 Mask |
| t_party_profile_identifier | 新增四个可空草稿列，identity_key_id 可空；保存半份证件输入不会污染永久身份键 |
| t_party_identity_key | 保留稳定去重键及规范化号码；原始填报号码移到 Profile Identifier，不在两处竞争维护 |
| t_party_relation | relation_version_id 改 relation_id；去掉资料版本与摘要；保留真实关系日期、组织/人员/关系类型唯一性，新增申请归属 |
| t_client_business_profile | 每个 Client 一份；经营说明新增 VARCHAR(2000)，金额等可空；删除版本/生效列，增加当前分类确认的 Case/Account/时间 |
| t_client_application | 只做本期首次入驻，去掉 application_type、Base/Target 指针和资料版本摘要；新增 current_submission_id，保留审核 Case/Decision 稳定引用 |

未出现在目标 CREATE 中的其他表继续存在，例如 CCT Client、业务分类、Document、补偿任务和必要审计。不能把这组文件单独用来创建整个 CCT/Risk 数据库。

## 2. 草稿、身份与字段映射

CCT 创建申请时建立真实 PENDING Client、主 Party 和单份资料头。draft_application_id 表示这份未批准资料由谁编辑。批准后写 approved_time，清空草稿编辑归属；所有入口检查批准锁定，不能从人员编辑或材料更换绕过。

同一已认证 Account 的个人/组织申请共用一个未完成名额。t_client_application 在既有 Client 唯一索引之外增加 `uk_client_application_account_active(requested_by_account_id, active_slot)`。DRAFT、SUBMITTED、UNDER_REVIEW、RETURNED 的 active_slot=1，APPROVED、REJECTED、WITHDRAWN 为 NULL，禁止 0；requested_by_account_id 必填。同一账号可以保留多份终态历史，RETURNED 不能释放名额。

创建/恢复按“该账号的 Client 入驻名额”这一实际资源协调，不锁全部账号业务或整个 Tenant。事务内复查活动申请：相同类型返回原单，另一类型明确冲突；不存在时才在同一 CCT 事务创建申请及必要业务对象、登记投影任务。数据库唯一索引处理并发最后一道竞争，冲突方回滚完整创建事务后重新查询原单，不留下无主 Client 或可执行投影任务。终态与 active_slot=NULL 在同一事务写入；没有真实终态时不能手工清空名额来绕开限制。

姓名、地址、经营信息有值即校验格式，但完整性只在提交时要求。PATCH 仅接受白名单 dirtyFields，未提及字段保持不变。没有资料 Version/ETag，不承诺跨标签页字段冲突提示；服务端按申请资源串行化，前端同一资源保存单飞。写入返回服务端保存结果，不能仅凭本地输入显示“已保存”。

证件输入按以下顺序处理：

1. identifierType、issuingCountry、issuerScopeKey、identifierValue 允许部分为空，保存在 t_party_profile_identifier；不写永久身份键。
2. 完整组合经 CCT 类型/国家/机构规则校验后计算规范化值。正式提交前必须完成此步骤，并绑定 t_party_identity_key。
3. 相同永久键归属当前 Party 时复用；归属其他 Party 时返回安全冲突，不自动转移身份或披露其他申请。共享已批准 Party 仅能经已有授权关联，资料只读。
4. 已绑定永久键的组成字段不可原位改写。未批准阶段更正为另一个有效标识时重新核验并绑定，旧永久身份键仍保持原 Party 归属；是否清退误填标识属于人工身份治理，不随保存静默删除。

原始证件值的存储分类沿用 CCT 当前批准合同；本次只移动原有字段，不新增敏感信息收集或未经批准的 Vault 类型。字段不能进入普通日志、通用错误或 MQ。Profile Identifier 的有效日期从页面 DATE 按 UTC 日初映射 DATETIME(6)，结束日期为排他边界，页面明确“不含当日”。

JSON individual / organization 分别对应类型表；address 对应 Party Address；identifier 对应 Profile Identifier 与稳定 Identity Key；business 对应 Client Business Profile；contact 由 CCT 在事务外调用既有 Vault 合同后，仅保存 Token/Mask。手机号不是账号绑定，相关人员不是登录账号。

业务金额统一 DECIMAL(24,8)，接口传十进制字符串。expectedVolume 固定为月交易金额；expectedTicket 为客单价，二者使用同一 currency。行业和 MCC 独立选择，客户 JSON 一律不得写入。

### 2.1 组织人员与关系的草稿归属

人员字段继续共用主表单 individual/address/identifier 的列定义；C07 新建个人 Party 及候选证件，C08 只改该人员原单份资料。t_party_relation 的 client_application_id 与 organization_party_id 同时限定当前申请，关系状态初始 DRAFT；组织/人员/类型唯一性由现有唯一索引兜底。关系的 effective_from/effective_to 是真实关系期间，接口日期固定 UTC 日初和排他结束，与资料版本无关。

移除 DRAFT 关系保留 Party/Profile 和旧审核引用；本申请创建的零关系人员仍由 draft_application_id 归属并可重新关联。共享已批准人员只能沿既有授权关系读取，不提供全局 Party 选择或自动合并。修改人员同时检查批准标记、草稿归属、其他申请/已生效关系、Agreement Party 冻结引用、Partner/OE 已批准申请引用；存在旧事实引用即只读，即使某个批准标记因历史数据不完整而缺失也不能绕过。

C07—C11 的固定命令记录仅保存稳定 ID 短引用与首次完成时间；删除后的重放不要求原关系仍存在。不新增资料/关系快照或通用审计版本，未绑定的证件仍由正式提交流程完成永久唯一身份核验。准确请求和响应见[API 合同第 8 节](./api-contract.md#8-c07c11-的-cct-私网组件)。

## 3. 审核任务与分类

第一次提交和每次 RETURNED 后重提各生成一个 submissionId。同次请求重试复用原 ID。t_client_application.current_submission_id + due_diligence_case_id 标识当前处理；旧任务返回的结果不能改变新任务，即使两个任务的状态都叫 UNDER_REVIEW。

Risk 的 t_due_diligence_case 新增 source_submission_id、public_note、resolution_note、return_section。原 source 唯一键必须替换为含 submissionId 的准确来源键，否则同一申请无法重提。其他既有 APPLICATION 来源迁移为稳定 legacy:application_id；新 Client 使用真实提交 UUID。旧来源适配器必须同步提供该键，不能利用 NULL 逃过唯一性。定时复审仍使用其原有独立键。

t_dd_case_subject 对新 Client 使用稳定 Profile ID 和新增 source_relation_id；source_profile_version_no 可空，旧关系版本列只为未迁移来源保留。新 Client 不填伪造 Version=1。t_qualification_decision 对新 Client 使用稳定 Decision ID，每个 Case 最多一个最终决定，由 Case 锁、终态条件及本地事务保护；旧来源的决定修订路径不在本期批量删除。

运营分类保存到 CCT 现有分类子表；确认时记录同一 Case、确认人及 UTC 时间。确认后集合不可修改。CCT 在提交本地事务后通过 I02 交给 Risk；Risk 写 t_dd_case_classification，唯一键保证组合不重复、至多一个主分类，应用校验至少一个且恰好一个主分类。Risk 尚未收到完整确认集合时不能批准。

新增任务分类行是一次真实审核形成的证据，不提供 Profile 版本选择或历史版本恢复。Risk 既有程序配置、内部并发字段、任务 Party Snapshot 仍有审核/追溯消费者：每个任务固定当时受审事实，退回后原任务不能改为显示新表单。Snapshot 使用既有 Vault 类型与受控目的，不新增第二套 Client 可编辑资料。Snapshot 协议 schema_version 不是入驻资料版本。

运营输入与落库对应：

- 材料核验 outcome 更新准确 t_dd_evidence_ref.status；不另存未定义的字段级批注。
- 人工 CheckResult 对应 t_dd_check。provider_code 取批准的人工检查目录，request_ref 使用稳定命令身份，performed_time 由服务端生成，有效期来自程序；不能随意构造“通过”。
- 退回 reasonCode 写既有 t_dd_case_state_change.reason_code，actor_id 和 trigger_id 写真实操作人与命令；publicNote/section 写 Case 新列。
- 批准/拒绝 reasonCode 写状态变化，reason 写 resolution_note，单独确认的 publicNote 写客户可见列。内部理由不得自动复制到客户页面。

RETURNED 是当前审核任务终态，同时终结仍活动的相关人子任务并释放对应 active_slot；已完成子任务的证据/决定不删除。CCT 消费准确任务的退回事实后才允许修改。新任务根据最新人员/材料重新确定依赖，不自动继承上一任务的人员通过结果。REJECTED 是申请终态；APPROVED 后全部资料、关系、材料集合和分类只读。

## 4. 本地事务与恢复

各 Owner 只操作本库。下游查询、Vault 和邮件调用在事务外；本地短事务提交后发出下一步，失败用既有 Owner 本地补偿任务恢复，不把跨系统事务放进 BFF。

| 中断点 | 恢复行为 |
|---|---|
| CCT 提交已落库，Risk 登记未知 | 申请保持 SUBMITTED；按同一 submissionId 调用 I03 查询或幂等 I01，不能让客户重提新任务 |
| 分类已在 CCT 确认，Risk 未收到 | 用原 Case/提交身份重试 I02；Risk 批准门禁保持关闭 |
| Risk 退回/决定已提交，CCT 未消费 | 重发稳定事件；CCT 核对当前 submissionId/Case，再更新原申请 |
| 旧任务结果晚到 | 记录幂等消费或过期结果，不能覆盖当前任务 |
| 已批准但 Tenant/Member 未就绪 | 申请保持批准且资料只读；既有身份开通流程按稳定 Owner 恢复，页面显示正在开通，不显示可用工作区按钮 |

最终批准还需满足适用程序、主/子任务、必要材料和检查，且文件当前可用；不得把“人工审核”解释为跳过实际门禁。文件服务不可用时拒绝执行批准，不能依赖浏览器缓存。

## 5. 邮箱链接、会话与文件

Platform 负责 CUSTOMER Realm + CLIENT Channel；认证 Challenge 使用既有 EMAIL_VERIFICATION/PASSWORD_RESET 用途，数据库只留 HMAC 校验材料，原始 Secret 不进数据库、通用补偿 Payload、日志或审计。

首期采用认证服务提交 Challenge 后直接调用邮件投递适配器：原始 Secret 只在该次内存和受控邮件内容中存在。验证码链接拟有效 30 分钟，重置链接拟有效 20 分钟；重发冷却 60 秒，重发会使旧 Challenge 失效。投递失败或进程中断不回滚账号，也不宣称已送达；统一 202 表示受理，用户可重发并获取新链接。此方案明确不承诺旧秘密的后台耐久重投，避免为此新增密钥存储表。真实邮件通道与故障指标仍属于实施配置。

验证/重置链接 GET 只显示页面，POST 单次消费。重置后撤销旧会话；验证成功仍用密码登录。Access Token 在浏览器内存，Refresh Token 仅在 BFF 的 HttpOnly/Secure Cookie；不修改现有 Operations 登录协议，不将其 Token Body 行为默认为新 Client Cookie 行为已存在。

Platform 补充表包括认证 Challenge 及上传、扫描策略/要求、对象、Owner 引用和扫描结果。上传完成由 Platform 查询存储字节/Hash；CCT Document 引用及 Risk Evidence 引用准确 File，移除草稿引用不等于删文件。

用户已确认每账号最多一份未完成 Client 申请。UploadStart.applicationId 只是目标断言，必须由 CCT 核对当前账号、当前活动申请、DRAFT/RETURNED 状态并返回准确 Client；不能直接拿浏览器 ID 当授权。Platform 在事务外取得该授权事实，在本地短事务中按 CLIENT Channel、可信 Account、PENDING Client Tenant、ACTIVE Member 与 Onboarding Role 重新校验唯一候选及 owner_ref_id 一致后创建 Upload。上述目标一致性作为本期 PLT-I04 内部补充，按已确认[文件合同补充](./file-upload-closure-proposal.md#5-数据提案和兼容边界)将原申请＋主体、展示名、存储目标和扫描策略持久化到 Upload；浏览器仍不能传 Tenant。

一份业务申请不保证异步投影已同步：批准/拒绝后遗留的 Onboarding Role、下一份申请的 Provision 未完成、Partner 候选或停用 Member 都不能误选。Client 请求只检查 Client 类型；零个/多个合法候选或与预期 Client 不符时返回未就绪，保持拒绝，恢复由既有 CCT→Platform 身份投影处理，不能自动取第一条或绕过安全暂停。上传完成仍使用原 Upload 的 Tenant/uploader；CCT 关联文件时再次核对显式申请、主体和归属，旧上传不自动移动到后来申请。

## 6. 迁移与现有消费者

现有 CCT CheckoutQueryMapper.xml 实际读取 Business Profile 的 status/active_slot/effective_from/effective_to 和 Party Profile 的 profile_status/content_hash。表简化必须同批修改 Mapper、查询记录和资格门禁。新查询使用 Client 当前资格及单份已批准资料，不能只删 WHERE 条件把草稿送入 Checkout。

批准 Profile ID 和内容 Hash 保持稳定，既有 Checkout 法律事实继续可解析。t_business_change_record.current_version 的生命周期投影及其他系统真实并发消费者保留；本期不因取消表单版本而删除所有技术修订字段。

Party/Profile/Relation 同时被 Partner、Operating Entity、Agreement Party 和历史订单引用。非空环境不能用 MAX(version_no)、最新时间或物理删除旧行自动“合并”：先盘点引用及批准语义，再逐一制定历史读取兼容。新建空环境与旧环境迁移分开交付。

[preflight.sql](./db/preflight.sql)检查多份资料、关系重复及申请分布，不是迁移充分条件；它尚未执行。补充的[迁移方案](./migration-plan.md)及三个 Owner 预检定义空闭包路径、Risk 来源重复、共享引用和回退门禁。非空环境保留旧读链，待准确映射评审后切换，不自动选取最新资料。

新增账号唯一索引前，必须检查同账号多条未完成申请、发起账号缺失，以及状态与 active_slot 不一致。任一命中都阻止建索引，交由明确的数据处置方案处理；不得取最新一条、静默置 NULL 或删除历史来使检查通过。

## 7. 尚未执行的事项

PDManer 与三个系统当前 Schema 未改；本目录 SQL 为评审投影。注册采用[现有 Owner 管理命令](./registration-design.md)，真实 ID 由服务生成，尚未调用命令或授予账号权限。阶段 3 设计评审后按[实施计划](./implementation-plan.md)进入开发；非空环境迁移、实际配置绑定与启用属于后续发布门禁，不要求在研发前操作数据库。
