# platform-support：workflow 按钮发起与数据验证 · 技术方案

> 状态：**阶段 2 已确认（2026-10-29）；第 7—13 节为阶段 3 评审稿（2026-10-29，设计稿，未改代码 / 未执行 SQL）**。页面原型由主会话在 `operations-console-web/docs/operations-console.pen` 绘制，确认前不写前端。
> 契约依据：[2026-10-28 客户上线决定 第 3.3 / 3.4 节](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md#3-契约附件req-00)。需求：[requirement.md](./requirement.md)（含第 9a 节 V-1—V-6，Q1—Q3 已按建议定）。

路径缩写：`wf/` = `paytech/platform-support/paytech-platform-support-workflow/src/main/java/com/paytech/platform/support/workflow/`；`dq/` = `paytech/platform-support/paytech-platform-support-data-query/src/main/java/com/paytech/platform/support/dataquery/`；`ps-core/` = `paytech/platform-support/paytech-platform-support-core/src/main/java/com/paytech/platform/support/`；`schema.sql` = `paytech/platform-support/docs/database/sql/platform-support-schema.sql`；`ops-web/` = `operations-console-web/src/`；`ops-bff/` = `paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/`。

## 1. 核对范围与证据

| 范围 | 证据位置 |
|---|---|
| 发起 | `wf/service/processor/StartWorkflowInstanceProcessor.java:71-105`（`launchable` 要求 `definitionId` 存在、ACTIVE、`currentVersionNo == versionNo`；不加锁、不查重）、`:165-183`（`businessKey` 原样写入）；`wf/service/processor/request/StartInstanceRequest.java:27-40`；API `paytech-platform-support-api/.../api/workflow/WorkflowInstanceApi.java:69-80`、`request/StartWorkflowInstanceRequestBody.java:15-37`（`definitionId` / `versionNo` 必填，`businessKey` ≤128 无格式）；响应 `WorkflowMutationResponseBody`（objectType / objectId / status / version，无"是否新建"） |
| 实例持久化 | `schema.sql:1036-1058`（`business_key` VARCHAR(128) NULL，表默认 `utf8mb4_0900_ai_ci`，注释"只用于检索和展示，工作流不解释其含义"，`idx_workflow_inst_bizkey` 普通索引）；`wf/../resources/mapper/workflow/WorkflowInstanceMapper.xml:45-56`（insert；update 按 `version` + `status='RUNNING'` CAS，终止 / 完成同走此句）；`wf/domainservice/service/impl/WorkflowRuntimeServiceImpl.java:39-52`（保存顺序：实例 → 节点 → 动作 → … → 补偿任务） |
| 运行引擎 | `wf/domainservice/process/WorkflowRuntimeEngine.java:81-103`（`completeAction`：执行人校验 + `ActionOutputChecker` + `advanceAfterActions`）、`:232-242`（变量 = `start.*` + 各动作最新 COMPLETED 的 `output_json`）、`:322-331`（`actorId` 为 `long`）；`wf/domainservice/process/RuntimeRecorder.java:79-89`（`apply(plan, Long actorId)` 可为空）、`:178-212`（节点激活在写事务内建动作运行，`assignee_id = executorFor(key)`）；`wf/domainservice/process/ActionOutputChecker.java:60`（DATA_QUERY 提交被拒"暂未开放"） |
| 写入口公共阶段 | `wf/service/processor/WorkflowInstanceCommands.java:60-74`（事务内 `authorize(actor, session, TASK_HANDLE)` + 加载 + 引擎 + `registerCallbackEvents` + 保存 + 审计）；`wf/service/processor/WorkflowProcessorSupport.java:91-110,214-230`（事务 15 秒、审计身份固定 `AuditActorType.USER`；`registerCallbackEvents` 是所有写入口登记补偿任务的唯一挂点）；全部写 Processor 与 Support 均 `@ConditionalOnProperty(operations.authentication.enabled)` |
| 变量与条件 | `wf/domain/variable/VariableDeclaration.java`（变量带 `FormFieldType`）、`WorkflowVariables.java:62-68`；`wf/domainservice/process/WorkflowConditionEvaluator.java` `compareTo` / `bool` / `decimal`（类型不符抛 `ErrorException`；BOOLEAN 只认 JSON 布尔，NUMBER 接受数字或十进制字符串）；`wf/core/enums/FormFieldType.java`（无列表类型，FILE 除外） |
| 动作模板 | `wf/domain/action/DataQueryActionConfig.java:15-33`（预留，outputs / inputKeys 为空）；`wf/domainservice/convert/ActionConfigJsonReader.java:103-106`（只允许 `queryCode / parameters / passRule / evidenceColumns`）；`wf/domainservice/validator/ActionConfigValidator.java:40-41`（DATA_QUERY 不校验 → **后端允许新建**）；`wf/service/processor/CreateWorkflowActionTemplateProcessor.java:35-43` |
| 发布校验 | `wf/domainservice/validator/TaskNodeRules.java:104-108`（每个动作必须有 ACTIVE 执行人）、`:152-155`（引用 DATA_QUERY → `TEMPLATE_RESERVED_TYPE`）、`:166-181`（输入映射 key 须在模板 `inputKeys`）；`wf/domainservice/validator/VariableFlowRules.java:182-183`（VARIABLE 输入须引用上游必经变量）；`wf/service/processor/WorkflowDraftChecker.java`（草稿保存与发布共用，只读本库）；`wf/service/processor/PublishWorkflowDefinitionProcessor.java:42-66`（冻结模板快照） |
| 动作运行表 / 验证表 | `schema.sql:1080-1100`（`assignee_id` NOT NULL；`action_type` 注释未含 DATA_QUERY；索引只有 `(assignee_id,status)`、`(instance_id)`）；`schema.sql:1102-1114` `t_workflow_verification`（`verified_by` NOT NULL，追加不可变）；`WorkflowVerificationMapper.xml:19-24` 只有 select，2026-10-23 体验优化后不再写入（台账 139 行），仅详情 / 活动只读展示 |
| 待办与可见性 | `WorkflowTaskMapper.xml:16`（我的待办 = `assignee_id` 的 PENDING 动作）；`wf/service/processor/WorkflowQueryProcessor.java:236-262,350`（详情 = 参与人或 `WORKFLOW_INSTANCE_READ`，路由权限 TASK_HANDLE）；`wf/domain/WorkflowInstanceContext.java:214-217`（参与人含 assignee / submitted_by） |
| data-query | `dq/domainservice/service/QueryTemplateService.java:53`（`findExecutableTemplate`，只读 mor_platform）；`dq/engine/DataQueryRunner.java:82-122`（`@Component`；参数绑定、渲染、`ReadOnlySqlGuard`、连接池、超时 = min(模板 `timeoutSeconds`, 8s)、屏蔽、日志 / 指标；注释"只能在数据库事务外调用"）；`dq/domain/DataQueryRun.java:40`（`operatorAccountId` 为 **基本类型 long**）；`dq/engine/JdbcQueryEngine.java:226-240`（整数 / DECIMAL → 字符串，BIT / TINYINT(1) → Boolean）；执行入口 `ps-core/dataquery/service/processor/ExecuteDataQueryProcessor.java:40-56`（**位于 `-core`**；`callers.authorize` 白名单 + 会话 + `DATA_QUERY_EXECUTE`） |
| 模块依赖 | `paytech-platform-support-workflow/pom.xml:15-28`（不依赖 data-query / core）；`paytech-platform-support-data-query/pom.xml:9-12`（只依赖 api / common-core / vault-client / http-problem）；`paytech-platform-support-core/pom.xml:41-45`（core → workflow、core → data-query）；arch-rules 无 Platform 模块边规则（`paytech/tools/arch-rules` 检索） |
| XXL 与补偿 | Platform 无自有 `@XxlJob`；XXL 由 `paytech-compensation-event`（`CompensationEventXxlJobHandler`）带入，enforcer 放行 `xxl-job-core`（`paytech/platform-support/pom.xml:113-133`）；mor-test `xxl_job_group` id 2 = `platform-support-service`，仅任务 2 `compensationEventScan`（30 秒）；样板 `paytech/commercial/.../ext/job/CapabilityPrerequisiteRecheckJobHandler.java`；`paytech/platform-support/compensation-event-catalog.yaml`（只有 5 类，**缺 WORKFLOW_NOTIFICATION / WORKFLOW_CALLBACK_EVENT**；R12 规则只扫 `-core`，`CompensationHandlerRule.java:21`） |
| 注册数据 | `platform-support-initial-data.sql:6114`（`POST /workflow/v1/instances` 路由 127415 绑 `WORKFLOW_INSTANCE_START`，`idempotency_mode=NONE`，响应超时 10000ms）、`:6116`（详情绑 TASK_HANDLE）；mor-test 现用块最大 141（REQ-01，尚未写入初始化文件） |
| BFF | `ops-bff/service/model/WorkflowModels.java:107-133`（严格 record，与 Platform 发起体一一对应）；`ops-bff/ext/rest/WorkflowInstanceController.java:65-67`；`ops-bff/integration/impl/PlatformWorkflowClientImpl.java` |
| Web | `ops-web/workflow/ActionTemplateEditor.tsx:95`（新建 DATA_QUERY 显示"不可用"）、`ops-web/workflow/catalogModel.ts`（`newTemplateType('DATA_QUERY') = null`）、`ops-web/workflow/TemplateEditors.tsx:93-130`（旧形状 `DataQueryEditor`，标"预留"）、`ops-web/workflow/EditorPanels.tsx:126,210`（节点动作选择器排除 DATA_QUERY）、`ops-web/workflow/graphModel.ts:452`（前端同口径报 `TEMPLATE_RESERVED_TYPE`）、`ops-web/workflow/TaskPanels.tsx:312-319`（未知类型默认提交 `{note}`）、`ops-web/workflow/workflowTypes.ts:89-93`；`ops-web/workflow/LaunchModal.tsx`（按 definitionId + versionNo 发起） |
| 数据现状（只读 SELECT） | mor-test `mor_platform`：实例 9（RUNNING 3 / COMPLETED 4 / TERMINATED 2，全部带 business_key），**RUNNING 中 `(definition_id, business_key)` 无重复**；动作模板 10，无 DATA_QUERY；data-query 模板 7 条 DEMO_*、无 `GOLIVE_*`；数据源含 `MOR_COMMERCIAL_REPLICA` 等 6 个 ACTIVE。本机 `mor_platform` 无实例；`mor_platform_v2e2e` RUNNING 26 无重复，有 1 条旧形状 DATA_QUERY 模板 |
| 台账 | `agent-docs/CURRENT_IMPLEMENTATION.md:132-139`（workflow 已部署 mor-test，块 127）、`:1328`（data-query 已部署，块 131，"工作流 DATA_QUERY 验证待工作流发布"）、`:20`（REQ-01 遗留："data-query 数据源复用 `MOR_COMMERCIAL_REPLICA` 与 3 条 `GOLIVE_*` 模板未登记（随 REQ-05）"） |

## 2. 现状发现

| 编号 | 发现 | 影响 |
|---|---|---|
| F-01 | 发起只认 `definitionId + versionNo`，版本号须等于当前发布版本；不加锁、不查重；`business_key` 是"只检索展示、不解释"的普通列，表排序规则 `utf8mb4_0900_ai_ci`（大小写、重音不敏感） | R1 需要：按编码解析定义与当前版本、防重语义改写列注释、唯一索引；排序规则会让 `CLIENT:abc` 与 `client:ABC` 被判为同一键（S-01） |
| F-02 | 存量数据无冲突：mor-test RUNNING 3 条、本机 e2e RUNNING 26 条，`(definition_id, business_key)` 均无重复 | 唯一索引可直接加；迁移脚本仍须带预检 SELECT（有重复则停止） |
| F-03 | 实例保存时实例行**第一个插入**（`WorkflowRuntimeServiceImpl:39`），完成 / 终止共用一条带 `status='RUNNING'` 的 CAS update | 槽位列在 insert 置 1、在同一条 update 随状态清空即可；并发重复发起在第一条 insert 即撞唯一索引，事务回滚干净，可在事务外回读既有实例返回。命名按 DATABASE_DESIGN_GUIDE §7.2 / 第 284 行用 `active_slot` |
| F-04 | DATA_QUERY 现状是"后端可建、不能发布、不能提交"：`ActionConfigValidator` 不校验、`TaskNodeRules:152` 拒发布、`ActionOutputChecker:60` 拒提交；Web 禁止新建、选择器过滤、前端图校验同口径拒绝。配置形状是 10-21 方案的 `queryCode / parameters / passRule / evidenceColumns`（按列比较），与决定 3.3 的单行约定（`satisfied / out_* / missing`）不一致 | 配置形状须替换（S-04）；mor-test 无存量 DATA_QUERY 模板，本机 e2e 1 条旧形状可作废 |
| F-05 | 每个动作必须有 ACTIVE 执行人（`TaskNodeRules:104-108`），`assignee_id` NOT NULL；我的待办按 assignee 列 PENDING 动作；实例可见性含 assignee | DATA_QUERY 若沿用执行人，会出现在执行人待办里但无"完成"按钮；若取消执行人需改列可空并波及转交、待办、可见性（S-06） |
| F-06 | 变量带类型；条件求值遇类型不符直接抛 `ErrorException`；BOOLEAN 只认 JSON 布尔；data-query 把整数 / DECIMAL 返回为字符串、`TINYINT(1)` 返回为布尔；变量体系没有列表类型 | 查询输出**必须在写入前按声明类型规整**，规整失败按"形状不符"记失败而不写入；否则写入后排他网关求值抛错，整个推进事务回滚、实例卡死。决定 3.3 中 `capabilities[]`、`missingMethods[]` 等列表输出无法按列表声明（S-05） |
| F-07 | 引擎完成路径以"被指派执行人提交"为前提（`requireCompletable`、`ActionOutputChecker`、`advanceAfterActions(long actorId)`），记录器已支持 `actorId = null`；写入口公共阶段强制会话鉴权与 USER 审计 | 需要新增"系统完成 DATA_QUERY"的引擎方法与不依赖会话的执行阶段；定时与激活路径没有操作人（S-09） |
| F-08 | 节点激活发生在各写入口（发起、完成、审批同意、退回、驳回）的**本地事务内**；所有写入口都经 `support.registerCallbackEvents` 一处登记补偿任务 | 事务公理下激活时不能直接查询（被查库属外部访问）。在同一挂点为新建的 DATA_QUERY 动作运行登记补偿任务，提交后执行首查，覆盖全部激活来源，且级联（完成 → 激活下一 DATA_QUERY）自然成立 |
| F-09 | data-query 执行入口 `ExecuteDataQueryProcessor` 在 `-core`，而 `-core` 依赖 workflow，workflow 不能反向依赖；该入口还要求调用方白名单与会话权限。data-query 模块对外只有 `QueryTemplateService`（本地读）和 `DataQueryRunner`（引擎内部步骤）；`DataQueryRun.operatorAccountId` 为 long；超时只能取模板值与 8 秒上限 | 10-21 工作流方案第 130 行"同进程调用 `ExecuteDataQueryProcessor`"不可行。需要 data-query 模块发布一个无会话的公开执行 Processor（复用 Runner 的只读守卫、连接池、屏蔽、日志指标），workflow 新增对 data-query 的单向依赖（无环）；需支持空操作人与调用方超时上限（S-10、S-11） |
| F-10 | 执行结果为 `columns + rows(List<List<Object>>)`；NONE 分页追加 `LIMIT maxRows+1`；列屏蔽 HIDE / FULL 照常生效 | 单行约定由 workflow 解读：0 行 = 未满足；>1 行、缺 `satisfied` 列、`satisfied` 非 0/1/布尔 = 形状不符（失败） |
| F-11 | `t_workflow_verification` 自 10-23 起只读不写，`verified_by` NOT NULL、追加不可变 | 不适合每 5 分钟的重查记录，也无系统身份可写；检查状态应落在动作运行行上（S-07）。旧表保持只读展示 |
| F-12 | 发布校验在草稿保存与发布时都跑，只读本库；`VariableFlowRules` 已校验 VARIABLE 输入引用的变量在上游必经路径上可用 | B7 第二半（参数映射变量已声明或由上游 outputs 提供）只要 DATA_QUERY 配置声明 `inputKeys` 与 `outputs` 即由现有规则覆盖；第一半（模板存在且 ACTIVE、参数与 data-query 声明一致）需读 data-query 事实（同库本地读，可在事务内） |
| F-13 | Platform 已是 XXL 执行器（mor-test 组 2），但只有补偿扫描；`compensation-event-catalog.yaml` 漏登工作流两类补偿任务（R12 只检查 `-core`，workflow 模块的 Handler 未被覆盖） | 新增一个 JobHandler 与一类补偿任务；目录文件顺带补齐存量两类；XXL 任务登记是发布动作 |
| F-14 | 发起路由 127415 绑 `WORKFLOW_INSTANCE_START`、无 Gateway 幂等；详情可见性 = 参与人或 `WORKFLOW_INSTANCE_READ` | 按编码发起复用同一路由（不新增路由）；"立即检查"是新写路由，按 Q2 绑 TASK_HANDLE 并在 Platform 内按"可见性"二次判定；注册块取 142（执行前在 mor-test 复核）。查询 5 秒 + 锁等待在 10 秒路由响应上限内 |
| F-15 | "流程未发布或已停用"现为 `BizCheckFailException` → 400 `BUSINESS_CHECK_FAILED`；workflow 无自有 Problem 编码 | B2 / AC-03 的 422 `DEFINITION_NOT_PUBLISHED` 是新增编码（S-03） |
| F-16 | BFF 发起体是严格 record，未知字段被拒；响应体复用 Platform api 类型。Web 任务卡对非 FORM / FILE_UPLOAD 一律按"说明 + 完成"提交；活动记录按 `submitted_by` 显示人 | BFF 加字段与一个转发；Web 任务卡须为 DATA_QUERY 单独分支（无完成按钮）；系统完成的活动需"系统"显示 |
| F-17 | 需求第 1 节与决定 3.4 称"WAITING 节点已有"；代码节点类型只有 START / END / TASK / EXCLUSIVE / PARALLEL_SPLIT / PARALLEL_JOIN，`WAITING` 只是并行汇合的节点运行状态 | 不影响本需求；REQ-07 的"等待节点"应表达为"只挂 DATA_QUERY 动作的 TASK 节点"。建议阶段 3 文档同步时更正决定措辞（S-18） |
| F-18 | data-query 模板 / 数据源是运行时登记数据；mor-test 无 `GOLIVE_*`；台账把 REQ-01 的 3 条 commercial 模板登记挂在本需求 | 发布阶段需登记模板（运营 API，非 DML）；验收用模板见 S-15 |
| F-19 | data-query 决定限定"结果不作为业务执行依据，只用于 Operations 查询和工作流验证" | 工作流推进属于"工作流验证"；DATA_QUERY 完成是单向的（之后事实回退不会重开节点），真正的执行门禁仍在各 Owner（如 REQ-01 开通项门禁）。须在方案中写明，避免把流程变量当执行依据 |

## 3. 需求项差异表

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 / 无法满足的原因 | 处理建议 |
|---|---|---|---|---|
| R1 按编码发起 | 只按 `definitionId + versionNo`（F-01） | `definitionCode` 取当前发布版本；与 ID + 版本二选一（Q3） | 未实现 | 扩展请求体与 `launchable`；按编码时不校验版本号 |
| R1 防重 + 并发 | 无约束；`business_key` 普通索引（F-01、F-03） | `(definition_id, business_key, active_slot)` 唯一；重复返回既有实例 | 未实现；排序规则大小写不敏感 | 新列 + 唯一索引 + 预检迁移；事务前先查、撞索引后事务外回读（S-01、S-02） |
| R1 返回既有实例 | 响应无"是否新建" | 200 + 既有 instanceId | 契约缺字段；非参与人回读后可能看不到详情 | S-02 |
| B2 / AC-03 无发布版本 | 400 `BUSINESS_CHECK_FAILED` | 422 `DEFINITION_NOT_PUBLISHED` | 无此编码（F-15） | S-03 |
| R2 模板创建 / 配置 | 后端可建旧形状；Web 禁建（F-04） | 模板编码 + 参数 + 输出（名、类型）+ 说明文案键 | 形状不符合单行约定 | 替换配置形状；Web 编辑器重做（S-04、S-05） |
| R2 / B7 发布 | `TEMPLATE_RESERVED_TYPE` 拒绝（F-04） | 可发布；模板不存在 / 非 ACTIVE / 参数不符拒绝 | 规则未实现；无 data-query 事实输入 | 去掉保留类型拒绝；`WorkflowDraftChecker` 读 data-query 事实注入校验输入；变量可用性复用 `VariableFlowRules`（F-12） |
| R3 激活即查 | 激活在事务内（F-08） | 激活后查一次 | 事务内不能查 | 同事务登记补偿任务，提交后首查 |
| R3 定时重查 | 无 Platform JobHandler（F-13） | XXL 每 5 分钟扫 PENDING DATA_QUERY，按实例分片 | 未实现；无扫描索引 | 新 JobHandler + 扫描索引；XXL 任务发布时登记（S-12） |
| R3 立即检查 | 无 | 任务查看者可触发（Q2） | 未实现；无路由 | 新写接口 + 路由 + BFF 转发 + 按钮（F-14） |
| R4 satisfied | 只有人工完成路径（F-07） | 动作 COMPLETED、输出写 `output_json`、节点按现有规则推进 | 无系统完成方法与无会话执行阶段 | 引擎新增系统完成；三个触发共用一个 Processor（S-08、S-09） |
| R4 未满足 / 失败 | 无记录位置；验证表不可用（F-11） | 记录 missing、检查时间、失败计数、最近成功时间 | 无列 | `t_workflow_action_run` 新增检查状态列（S-07） |
| R5 变量驱动网关 | 变量取 COMPLETED `output_json`，类型严格（F-06） | outputs 可被 EXCLUSIVE 引用 | 机制已有；需写入前类型规整 | 输出按声明类型规整，失败按形状不符（S-05） |
| R6 任务卡 / 详情返回检查信息 | 动作运行体无检查字段 | 说明、状态、检查时间、missing、失败信息 | 未实现 | 动作运行响应体加字段（详情、我的待办共用装配） |
| R7 通知 | 节点推进事件已登记通知（`RuntimeRecorder`） | 沿用，不新增触发 | 无缺口 | 系统完成走同一 `RuntimeRecorder`，触发人为空 |
| R8 文档 | design-discuss/workflow、`PLATFORM_SUPPORT_DATABASE.md:323`、schema 注释均写"DATA_QUERY 预留" | 改为可用 | 文档未更新 | 阶段 3 / 6 同步 |
| B3 无人工提交 | `ActionOutputChecker:60` 已拒绝 | 永久不开放人工提交 | 文案为"暂未开放" | 保留拒绝，改为明确原因 |
| B4 形状 | 无 | 单行 `satisfied / out_* / missing`，不符视为失败 | 未实现 | workflow 解读器（F-10） |
| B5 只写声明列 | 无 | 只写输出映射声明的 `out_*` | 未实现 | 输出声明即白名单 |
| B6 超时 / 并发 | 超时只能取模板值（F-09）；无检查并发控制 | 单次 5 秒可配置；同一动作只执行一次 | 未实现 | 调用方超时上限 + 检查锁 + 落库 CAS（S-08、S-11） |

## 4. 逐系统影响

| 系统 / 仓库 | 业务职责 | 影响点 | 是否改代码 / 模型 / 配置 | 上下游依赖及兼容风险 |
|---|---|---|---|---|
| Platform · workflow 模块 | 流程定义、实例、动作运行事实 Owner | 发起 Processor（编码、防重、回读）；`DataQueryActionConfig` / 读取器 / 校验器；`TaskNodeRules`、`WorkflowDraftChecker`（B7）；引擎系统完成方法；新检查 Processor（三触发共用）；补偿 Handler；实例与动作运行 Mapper / DataObject；响应装配 | 代码：是；模型：是（2 表）；配置：新增 `workflow.data-query.*`（超时、重查批量） | 新依赖 data-query 模块（单向无环）；存量 `definitionId + versionNo` 发起不变；旧形状 DATA_QUERY 模板读取失败（mor-test 0 条） |
| Platform · data-query 模块 | 数据源、查询模板、只读执行 Owner | 新增无会话的公开执行 Processor（模块内 `service/processor`）；`DataQueryRun` 操作人可空、调用方超时上限；发布校验用的模板事实读取（参数声明、状态） | 代码：是（小）；模型 / 配置：否 | 现有 `ExecuteDataQueryProcessor` 与 HTTP 白名单不变；只读守卫、连接池、屏蔽、日志指标自动复用 |
| Platform · app / api | 协议入口、XXL 入口 | api：发起体加 `definitionCode`（ID / 版本改条件必填）、响应加新建标记、动作运行体加检查字段、新"立即检查"接口；app：Controller 实现、`ext/job` JobHandler、`application.yml` 配置项 | 代码：是；配置：是 | 发起体字段放宽须保持旧调用方（Web LaunchModal）不变；内部 `@HttpExchange` 消费方仅 ops-bff |
| Platform · `mor_platform` | — | `t_workflow_instance`：`active_slot` + 唯一索引，`business_key` 注释（与排序规则，S-01）；`t_workflow_action_run`：检查状态列 + 扫描索引，`action_type` 注释补 DATA_QUERY；`t_workflow_action_template.action_type` 注释去"预留"；PDManer 同步 | 模型 / DDL：是 | 增量脚本含预检；应用与 DDL 部署顺序：DDL 先行（新列可空、旧代码不读新列，向前兼容）；回滚删索引与列 |
| Platform · 注册数据 | Gateway 路由 / 权限 | "立即检查"路由 1 条（TASK_HANDLE），可能 1 个按钮资源；发起路由不变 | DML：是（新块，约 142） | 先离线编译快照再执行（DISABLED 路由也编译进快照）；与块 141 未写入初始化文件的遗留一并处理 |
| Platform · 补偿目录 / arch-rules | 架构约束 | `compensation-event-catalog.yaml` 登记新类型并补登存量两类 | 文档：是；arch-rules 代码：否 | 无运行影响 |
| XXL-Job（mor-test） | 调度 | 组 2 新增任务（每 5 分钟） | 配置：发布动作 | XXL 写入在主会话执行 |
| data-query 运行时数据 | 模板 Owner 各需求 | 验收用模板与 REQ-01 遗留 3 条 `GOLIVE_*`（`MOR_COMMERCIAL_REPLICA`） | 运营 API 登记：发布动作 | 决定 3.3 其余模板随各 Owner 需求；S-15 |
| operations-console-bff | 页面聚合 / 转发 | 发起 record 加 `definitionCode`；"立即检查"转发；动作运行响应透传新字段 | 代码：是（小） | BFF 严格解析，字段须与 Platform 同步发布（先 Platform 后 BFF） |
| operations-console-web | 运营页面 | 动作目录：放开新建 DATA_QUERY，新编辑器（模板选择、参数、输出声明、说明键）；流程编辑器：选择器放开、节点动作输入映射复用、前端图校验去保留类型；任务卡 / 实例详情："系统检查"块 + "立即检查"；活动"系统"显示；en / zh-CN 文案与语言包发布 | 代码：是；Pencil 原型：是（阶段 3 先行，V-6） | 语言包需随发布推 Platform（bundle）；旧 `DataQueryEditor` 替换 |
| Gateway / 各 Owner 业务服务 | — | 无代码改动 | 否：Gateway 只消费注册快照；Owner 只交付模板文本 | — |
| 文档 | — | workflow design-discuss、`PLATFORM_SUPPORT_DATABASE.md`、`CURRENT_IMPLEMENTATION.md`、决定 3.3 / 3.4 措辞（S-05、S-18） | 是 | — |

## 5. 待决事项

| 编号 | 问题 | 建议 |
|---|---|---|
| S-01 | 唯一约束形态与 `business_key` 规范 | 新增可空 `active_slot TINYINT`：`RUNNING` 且 `business_key` 非空时为 1，其余为 NULL；唯一索引 `(definition_id, business_key, active_slot)`；应用在 insert 与现有 CAS update 中维护（不用生成列，便于 PDManer 与守则一致）。`business_key` 改为 `utf8mb4_bin`（与编码列一致，精确匹配；mor-test 9 行不受影响）。按 definitionId + 版本发起同样受约束 |
| S-02 | 重复发起的返回契约 | 先在事务前按 `(definition_id, business_key)` 查 RUNNING，命中直接返回；撞唯一索引时在事务外回读返回。响应保留 `WorkflowMutationResponseBody` 并加 `created`（布尔）。重复请求的标题 / 启动值 / 人员修改一律忽略，不比对。详情可见性不变（参与人或 `WORKFLOW_INSTANCE_READ`）：非参与人拿到 ID 后需有查看权限，REQ-07 上线角色配置 `WORKFLOW_INSTANCE_READ` |
| S-03 | AC-03 错误码 | 沿用 400 `BUSINESS_CHECK_FAILED` + 中文原因"流程未发布或已停用"，不新增 422 编码；同步改 B2 / AC-03 |
| S-04 | DATA_QUERY 配置形状 | 替换为 `{queryCode, parameters: [key], outputs: [{name, type, enumValues?}], descriptionKey}`：`queryCode` 即 data-query 模板编码；`parameters` 是模板要求的参数 key（编辑时从 data-query 声明带出），节点上的取值映射**复用现有节点动作 `inputs`**（VARIABLE / CONSTANT），不在模板里重复映射；`outputs[].name` 对应 `out_<name>` 列，变量名为 `<actionKey>.<name>`。删除 `passRule / evidenceColumns`；读取器不再接受旧键，本机 e2e 的 1 条旧模板作废 |
| S-05 | 输出类型与列表输出 | 允许 TEXT / NUMBER / BOOLEAN / ENUM / DATE；写入前规整（NUMBER 接受十进制字符串、BOOLEAN 接受 0/1/true/false、ENUM 须在 `enumValues`），任一声明列规整失败按"形状不符"记失败、不写入。决定 3.3 的 `capabilities[]` 等列表输出本期按 TEXT（逗号分隔）声明，网关用 CONTAINS；在决定 3.3 加注 |
| S-06 | DATA_QUERY 是否需要执行人 | 保留必填：执行人即"跟进人"，待办里显示该卡片（只有"立即检查"，无完成按钮），可转交，参与人可见性不变；不改 `assignee_id` 可空。REQ-06 的跟进人 / 队列再扩展 |
| S-07 | 检查状态存哪 | 存在 `t_workflow_action_run` 行上，新增 5 列（阶段 3 定名）：最近检查时间、检查结果（SATISFIED / UNSATISFIED / FAILED）、明细（missing 原因码或失败码，VARCHAR）、连续失败次数、最近成功查询时间；不复用 `t_workflow_verification`，不存结果行 JSON。另加扫描索引（如 `(action_type, status)`）。告警计数用 Micrometer 指标，不落库 |
| S-08 | "激活首查 / 定时 / 立即检查"共用路径与防并发 | 一个检查 Processor 服务三个入口：① 对 `workflow-action-check:{actionRunId}` TRY_LOCK（租约 ≥ 查询上限 + 余量），读动作运行，非 PENDING / 实例非 RUNNING 直接返回；② 锁外、事务外执行查询；③ 再取 `workflow-instance:{id}` 锁，短事务内以动作运行 `version + PENDING` CAS 写检查结果或系统完成并推进。实例锁不在查询期间持有，不阻塞人工操作。检查锁未取得：Job 跳过、补偿任务视为完成、"立即检查"返回当前已记录状态并标注"检查进行中" |
| S-09 | 系统完成的身份与审计 | `submitted_by` 为空、推进触发人为空，活动显示"系统"；data-query 一律以空操作人执行（结果不随点击人变化），`DataQueryRun.operatorAccountId` 改为可空。"立即检查"记一条用户审计（资源 WORKFLOW_ACTION_RUN，原因 CHECKED）；激活与 Job 路径不写 Operations 审计，只记日志与指标 |
| S-10 | workflow → data-query 调用边界与访问控制 | data-query 模块新增公开执行 Processor（无会话、无 HTTP 白名单，入参模板编码 / 参数 / 超时上限 / 调用方标签 `workflow`，返回 Owner 结果），workflow 依赖 data-query 模块，模块依赖清单同步。~~新建 / 修改 DATA_QUERY 动作模板时同时要求 `DATA_QUERY_EXECUTE`~~ **2026-10-29 用户裁定：不做该权限联动**，动作模板沿用 `WORKFLOW_ACTION_TEMPLATE_MANAGE`；运行时只取单行、只写声明的 `out_*` 列，列屏蔽照常生效 |
| S-11 | 超时 | 执行入参增加调用方超时上限，实际 = min(模板 `timeoutSeconds`, `workflow.data-query.timeout`(默认 5s), 8s)；不要求各 Owner 模板都写 5 秒 |
| S-12 | Job 放置与范围 | JobHandler 放 `-app` 的 `ext/job`（与 `DataSourcePoolRefreshScheduler` 一致），调用 workflow 检查 Processor；按 `instance_id` 取模分片、每轮上限可配；无全量档（COMPLETED 不再重查，见 S-13）；mor-test 组 2 每 5 分钟，发布时登记 |
| S-13 | 满足后事实回退 | 接受单向：COMPLETED 不再重查，节点不因事实回退重开；执行门禁仍在 Owner。写入 requirement 业务规则 |
| S-14 | data-query 模板在流程发布后被修改 / 停用 | 不冻结 data-query 版本：运行时按当前 ACTIVE 版本执行；模板不存在 / 停用 / 形状变化按失败记录并保持 PENDING；发布校验只在保存草稿与发布时检查一次 |
| S-15 | 验收用查询模板 | auto 测试用现有 data-query fixture 库（`DataQueryFixture` / `DataQueryMocker`）自建模板；mor-test 发布时登记 REQ-01 遗留的 3 条 commercial `GOLIVE_*`（`MOR_COMMERCIAL_REPLICA`）并建一个验收流程定义；其余 `GOLIVE_*` 随各 Owner 需求 |
| S-16 | missing 原因码文案 | 前端按 `workflow.dataQuery.missing.<CODE>` 取文案、缺失时显示原码；`GOLIVE_*` 原因码文案随各 Owner 需求 / REQ-07 补齐，本需求只交付机制与说明键 |
| S-17 | 补偿目录存量遗漏 | 本需求顺带在 `compensation-event-catalog.yaml` 补登 `WORKFLOW_NOTIFICATION`、`WORKFLOW_CALLBACK_EVENT` 与新类型；R12 覆盖范围（非 `-core` 模块）不在本需求改 |
| S-18 | 决定 / 需求中"WAITING 节点已有"表述 | 阶段 3 文档同步时更正为"等待 = 只挂 DATA_QUERY 动作的 TASK 节点"，供 REQ-07 建模参考 |

## 6. 工作量与风险

| 项 | 判断 |
|---|---|
| 规模 | **M（偏上限）**。Platform workflow 约 12—15 个新 / 改类（发起、配置三件套、发布规则、引擎方法、检查 Processor、补偿 Handler、Mapper / DataObject、装配）；data-query 2—3 处小改 + 1 个公开 Processor；api 4 处、app 2 处 + 1 Job；DDL 2 表（实例 1 列 1 唯一索引 + 排序规则，动作运行 5 列 1 索引）；注册 1 路由；BFF 2 处；Web 3 块（动作目录编辑器、流程编辑器放开、任务卡检查块）+ 文案；Pencil 原型 2—3 张。未到 L：不新增表、不新增节点类型、不改通知体系 |
| 最大风险 | 系统完成与人工操作并发、以及输出写入后网关求值失败导致实例卡死（F-06）。靠"查询在锁外、落库 CAS、写入前类型规整"三点兜底，auto 测试覆盖并发立即检查、检查期间终止实例、输出类型不符 |
| 第二风险 | workflow 首次依赖 data-query 与首个 Platform 自有 XXL Job：本机 auto 需要 data-query fixture 库可用；mor-test 需登记 XXL 任务与模板（发布动作） |
| 第三风险 | 访问控制（S-10）：持有动作模板管理权限的人可借流程变量读到声明的 `out_*` 列；用户 2026-10-29 接受，不做权限联动 |
| 依赖 | 无阻塞依赖；`GOLIVE_*` 真实模板随各 Owner 需求，本需求以 fixture 与 REQ-01 的 3 条验收 |
| 需要页面原型的点 | ① 动作目录 DATA_QUERY 编辑页（选 data-query 模板、参数列表、输出声明与类型、说明键、校验错误）；② 流程编辑器节点动作（DATA_QUERY 卡片样式、输入映射、发布问题提示）；③ 任务卡 / 实例详情"系统检查"块（满足 / 未满足 / 检查失败 / 检查中四态、missing 列表、最近检查与最近成功时间、"立即检查"加载与错误反馈）；④ 活动记录中的系统完成项 |

**确认点**：2026-10-29 用户确认：S-10 不做 `DATA_QUERY_EXECUTE` 联动，其余 S-01—S-18 按建议。进入阶段 3。

---

# 阶段 3：方案、接口与数据设计（评审稿，2026-10-29）

**一句话规模**：本次方案涉及 Platform（workflow、data-query、app / api）、operations-console-bff、operations-console-web 3 个系统；新增 2 个接口（立即检查、data-query 模板选择）、调整 4 个接口（发起、实例详情、我的待办、动作模板新建 / 修改的 DATA_QUERY 配置）；新增表 0；修改旧表 2 张：`t_workflow_instance` 新增 1 列、`business_key` 改排序规则、新增 1 个唯一索引，`t_workflow_action_run` 新增 5 列、新增 1 个索引，另改 2 列注释；存量数据更新 1 条 UPDATE（mor-test 预计 3 行）；注册 DML 1 块（块 142：路由 2、路由头 8、查询参数 3、守卫规则 12，共 25 行，无新权限与菜单）；页面原型 4 处（第 6 节所列，主会话绘制）。

已读规范：`DEVELOPMENT_CONVENTIONS.md`（第 4—6 节）、`JAVA_PROJECT_STRUCTURE.md`（跨模块与 Processor 章节）、`DATABASE_DESIGN_GUIDE.md`（§7.2、active_slot 规则）、`examples/WRITE_API_EXAMPLE.md`、`COMPENSATION_EVENT_GUIDE.md`、`UNIT_TEST_GUIDE.md`；代码链见第 1 节。

本地原子写入与事务外步骤：

| 用例 | 事务外 | 本地短事务（`mor_platform`） |
|---|---|---|
| 发起 | 无（只读本库） | 权限重检 → 查 RUNNING 同键实例 → 写实例与首批运行 → 登记补偿任务（含 DATA_QUERY 首查） |
| DATA_QUERY 检查 | 读实例上下文、解析参数、**执行 data-query**、解读结果 | 取实例锁后：CAS 写检查列；满足时完成动作、推进节点并登记补偿任务 |
| 立即检查 | 同上 | 同上 + 用户审计 |
| 定时重查 | 扫描到期动作（只读） | 每个动作同"DATA_QUERY 检查" |

## 7. 方案总览

```
发起（HTTP，按编码或 ID+版本）
  StartWorkflowInstanceProcessor ── support.write ──┐
    权限 → 解析定义 → 同键 RUNNING? ─是→ 返回既有（created=false）
                       └否→ 写实例(active_slot=1) + 运行 → registerCallbackEvents ─┐
    DuplicateKeyException（并发）→ 事务外回读既有 → created=false               │
                                                                               │（同事务登记）
任一写入口（发起 / 完成 / 审批 / 退回 / 驳回 / 系统完成）新建 DATA_QUERY 动作运行 ──┤
                                                                               ▼
                                      compensation_event WORKFLOW_DATA_QUERY_CHECK（提交后执行）
                                                                               │
 XXL workflowDataQueryRecheck（每 5 分钟，分片）── RecheckWorkflowDataQueryProcessor ──┤ 逐个
 运营"立即检查" POST …/action-runs/{id}/data-query-check ──────────────────────────────┤
                                                                               ▼
  CheckWorkflowDataQueryProcessor（AbstractLockProcessor，TRY_LOCK workflow-action-check:{actionRunId}，等待 0）
    1 读：queries.load(instanceId) —— 动作须 DATA_QUERY + PENDING、节点 ACTIVE/APPROVING、实例 RUNNING，否则返回现状
    2 参数：DataQueryParameterResolver(节点动作 inputs, engine.variables(context)) → JSON 对象
    3 执行（事务外、实例锁外）：RunDataQueryTemplateProcessor.process(模板编码, 参数, 超时上限, "workflow")
         异常 → FAILED + 失败码
    4 解读：DataQueryResultReader(结果, 输出声明) → SATISFIED(规整后的 outputs) / UNSATISFIED(missing) / FAILED(RESULT_SHAPE_INVALID)
    5 落库：ApplyWorkflowDataQueryCheckProcessor（AbstractLockProcessor，TRY_LOCK workflow-instance:{instanceId}）
         support.write：重载上下文 → 校验动作 version 未变且仍 PENDING、节点仍开放、实例 RUNNING（否则丢弃结果）
           SATISFIED   → engine.completeDataQuery(...)：output_json、COMPLETED、检查列 → 必选全部完成则开放审批或推进
                         → registerCallbackEvents（新激活的 DATA_QUERY 继续登记首查）→ runtime.apply（实例版本 CAS）
           UNSATISFIED → 只更新本动作检查列（动作 version CAS），连续失败清零
           FAILED      → 只更新本动作检查列，连续失败 +1，最近成功时间不变
```

- **锁顺序固定**：检查锁 → 实例锁；其他写入口只取实例锁，不会反向等待，无死锁。查询期间不持有实例锁，不阻塞人工操作。
- **防重复执行**：同一动作同一时刻只有一个检查（检查锁）；结果落库以动作 `version` + `status=PENDING` 为条件，期间被终止 / 转交 / 退回的动作结果自动丢弃。
- **规整在落库前**：输出按动作模板声明的类型规整，任一列不合格整体判 `RESULT_SHAPE_INVALID`，不写 `output_json`，保证排他网关求值不会因类型抛错而卡死实例（F-06）。
- **单向**：COMPLETED 后不再检查（B8）。

## 8. 接口

### 8.1 Platform：发起（调整）`POST /workflow/v1/instances`

请求体 `StartWorkflowInstanceRequestBody`（`paytech-platform-support-api` `api/workflow/request`）：

| 字段 | 变更 | 规则 |
|---|---|---|
| `definitionCode` | **新增**，String，可空 | `@Pattern("[A-Z][A-Z0-9_]{0,63}")`（与定义编码一致） |
| `definitionId` | 去掉 `@NotBlank`，保留十进制 ID 格式 | 可空 |
| `versionNo` | 去掉 `@NotNull`，保留 `@Positive` | 可空 |
| 其余（title、businessKey、startValues、approvers、executors） | 不变 | — |

二选一：`definitionCode` 非空时 `definitionId`、`versionNo` 必须都为空；否则二者都必填。违反返回 400 `INVALID_REQUEST`（"definitionCode 与 definitionId + versionNo 必须二选一"），在 `WorkflowConverter.start` 中判定。

`StartInstanceRequest`（`wf/service/processor/request/`）：`definitionId` 改为可空 `Long`、`versionNo` 可空 `Integer`，新增 `String definitionCode`；去掉两个 `@NotNull`。

`StartWorkflowInstanceProcessor.processRequest` 新流程：

1. `support.write` 内：`authorize(INSTANCE_START)` → `resolve(request)`：编码模式 `definitions.findByCode(code)`，ID 模式 `definitions.find(id)`；不存在 404 `NOT_FOUND`。
2. `launchable(definition, request)`：`status != ACTIVE` 或 `currentVersionNo == null` → `BizCheckFailException("流程未发布或已停用，不能发起：" + code)`（400，S-03）；ID 模式另校验 `currentVersionNo == versionNo`（原逻辑）。编码模式使用 `currentVersionNo`。
3. `businessKey != null` 时 `instances.findRunningByBusinessKey(definitionId, businessKey)`；命中则 `support.succeeded(session, INSTANCE, id, "REUSED")` 并返回 `new WorkflowMutation(INSTANCE, id, "RUNNING", version, false)`，不校验启动值与人员。
4. 未命中：原有构建与 `engine.start`；`instance(...)` 中 `instance.setActiveSlot(businessKey == null ? null : 1)`；保存；返回 `created=true`。
5. `support.write` 外捕获 `DuplicateKeyException`：再开一个 `support.write` → 重检权限、`findRunningByBusinessKey` → 命中按第 3 步返回；未命中（并发者已结束）→ `ConcurrentRequestException("流程实例正在发起，请稍后重试")`（409）。

响应 `WorkflowMutationResponseBody` 新增 `Boolean created`：发起时 `true` / `false`，其他接口为 `null`。`WorkflowMutation` 新增字段 `Boolean created`，保留原 4 参构造（`created = null`），`WorkflowConverter.mutation` 透传。

示例：

```json
// 请求
{"definitionCode":"CLIENT_GO_LIVE","title":"上线 · DEMO-CL-001","businessKey":"CLIENT:880000000101",
 "startValues":{"clientId":"880000000101"},"approvers":[],"executors":[]}
// 首次 200
{"objectType":"WORKFLOW_INSTANCE","objectId":"702918334455667788","status":"RUNNING","version":1,"created":true}
// 重复 200（同一 objectId）
{"objectType":"WORKFLOW_INSTANCE","objectId":"702918334455667788","status":"RUNNING","version":4,"created":false}
```

Gateway 路由 127415 不变（`idempotency_mode=NONE`，业务唯一性由 Platform 保证）。

### 8.2 Platform：立即检查（新增）`POST /workflow/v1/action-runs/{actionRunId}/data-query-check`

- `WorkflowInstanceApi` 新增方法 `checkDataQuery(actor, session, actionRunId, @RequestBody(required = false) JsonNode ignored)`；请求体可空或 `{}`（Web 固定发 `{}`，见 bodyless-write 教训），内容不读取。
- Controller `@Audit(action = "OPERATIONS_WORKFLOW_ACTION_CHECK", resourceType = "WORKFLOW_ACTION_RUN", resourceIdParameter = "actionRunId", fallbackResourceId = "WORKFLOW_ACTION_RUN")`。
- 权限：路由绑 `WORKFLOW_TASK_HANDLE`；Platform 重检 TASK_HANDLE，并要求"实例参与人或 `WORKFLOW_INSTANCE_READ`"（与 `WorkflowQueryProcessor.visible` 同规则），否则 404。
- 响应 `WorkflowDataQueryCheckResponseBody`（新增，`api/workflow/response/`）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `actionRunId` | String | — |
| `actionStatus` | String | 检查后的动作状态 PENDING / COMPLETED / CANCELLED |
| `instanceStatus` | String | RUNNING / COMPLETED / TERMINATED |
| `inProgress` | boolean | true：已有检查在进行（检查锁被占用），本次未执行查询 |
| `check` | `WorkflowDataQueryCheckBody` | 最新检查块（见 8.3） |

| 情形 | 返回 |
|---|---|
| 动作不存在 / 不可见 | 404 `NOT_FOUND` |
| 动作不是 DATA_QUERY | 400 `BUSINESS_CHECK_FAILED`（"该动作不是系统检查"） |
| 动作已 COMPLETED / CANCELLED 或实例已结束 | 200，不执行查询，返回现状 |
| 检查锁被占用 | 200，`inProgress=true` |
| 查询失败 / 超时 / 形状不符 | 200，`check.result=FAILED`、`check.failureCode` |
| 落库时实例锁 500ms 内未取得 | 409 `CONCURRENT_REQUEST`（查询结果丢弃，下一轮重查） |

示例响应：

```json
{"actionRunId":"702918334455669901","actionStatus":"PENDING","instanceStatus":"RUNNING","inProgress":false,
 "check":{"queryCode":"GOLIVE_AGREEMENT_ACTIVE","descriptionKey":"workflow.dataQuery.criteria.GOLIVE_AGREEMENT_ACTIVE",
  "instruction":null,"result":"UNSATISFIED","missing":["TERM_VERSION_NOT_ACTIVE"],"failureCode":null,
  "lastCheckedAt":"2026-10-29T08:10:03.123456Z","lastSucceededAt":"2026-10-29T08:10:03.123456Z",
  "consecutiveFailures":0,"checking":false}}
```

### 8.3 Platform：检查块（调整 `GET /workflow/v1/instances/{instanceId}`、`GET /workflow/v1/my-tasks`）

`WorkflowInstanceDetailResponseBody.ActionRunBody` 新增 `WorkflowDataQueryCheckBody dataQueryCheck`（非 DATA_QUERY 为 null）。我的待办页按现有方式加载实例详情，因此卡片与详情共用这一块。

`WorkflowDataQueryCheckBody`（新增）：

| 字段 | 类型 | 来源 |
|---|---|---|
| `queryCode` | String | 版本快照中 `DataQueryActionConfig.queryCode` |
| `descriptionKey` | String，可空 | 快照配置（完成标准文案键） |
| `instruction` | String，可空 | 快照配置（文案键无译文时显示） |
| `result` | String，可空 | `check_result`：SATISFIED / UNSATISFIED / FAILED；从未检查为 null |
| `missing` | List<String> | `check_result=UNSATISFIED` 时拆分 `check_detail`；其他为空列表 |
| `failureCode` | String，可空 | `check_result=FAILED` 时为 `check_detail` |
| `lastCheckedAt` | Instant，可空 | `last_check_time`（微秒截断） |
| `lastSucceededAt` | Instant，可空 | `last_check_success_time`（查询成功执行的最近时间，满足或未满足都算） |
| `consecutiveFailures` | int | `check_failure_count` |
| `checking` | boolean | 动作 PENDING 且 `lastCheckedAt == null`（首查尚未完成） |

`WorkflowTaskBody` 新增 `actionType`（ACTION 类待办为动作类型，APPROVAL 为 null）：`WorkflowTaskView` 加 `actionType`，`WorkflowTaskMapper.xml` `tasks` 片段第一段加 `a.action_type AS actionType`、第二段补 `NULL`。

活动记录：系统完成的动作 `submittedBy` 为 null，`WorkflowActivityBody` 不变，前端按 null 显示"系统"。

### 8.4 Platform：DATA_QUERY 动作模板配置（调整 `POST/PUT /workflow-admin/v1/action-templates`）

请求体结构不变（`actionType=DATA_QUERY`，`config` 为 JSON 对象），`config` 改为：

```json
{
  "queryCode": "GOLIVE_AGREEMENT_DRAFTED",
  "parameters": ["clientId"],
  "outputs": [
    {"name": "agreement_id", "type": "TEXT"},
    {"name": "business_mode", "type": "ENUM", "enumValues": ["MOR", "PSP"]},
    {"name": "capability_codes", "type": "TEXT"}
  ],
  "descriptionKey": "workflow.dataQuery.criteria.GOLIVE_AGREEMENT_DRAFTED",
  "instruction": "合同已起草且价格版本已批准"
}
```

`DataQueryActionConfig` 改为 `record DataQueryActionConfig(String queryCode, List<String> parameters, List<DataQueryOutput> outputs, String descriptionKey, String instruction)`，`DataQueryOutput(String name, FormFieldType type, List<String> enumValues)`（`wf/domain/action/`）。`outputs(actionKey)` 返回 `VariableDeclaration(actionKey + "." + name, type, enumValues)`；`inputKeys()` 返回 `Set.copyOf(parameters)`。

`ActionConfigJsonReader.readDataQuery`：`allowOnly(queryCode, parameters, outputs, descriptionKey, instruction)`，旧键 `passRule / evidenceColumns` 不再接受（结构错误 → 422 `config.<path>`）。

校验（`ActionConfigValidator` 纯规则，`ACTION_CONFIG_INVALID`，合并为一条 400 原因）：

| 规则 | 说明 |
|---|---|
| `queryCode` 必填 | 匹配 `[A-Z][A-Z0-9_]{1,63}` |
| `parameters` | 每个满足 `WorkflowValueFormats.isKey`，不重复，≤ 20 |
| `outputs` | ≤ 20；`name` 满足 `isKey`，不区分大小写不重复；`type` ∈ TEXT / NUMBER / BOOLEAN / ENUM / DATE；ENUM 至少 1 个不重复的 `enumValues`，其他类型不得带 `enumValues` |
| `descriptionKey` | 可空；`[a-z][A-Za-z0-9_.-]{0,127}` |
| `instruction` | 可空；≤ 2000 字 |

data-query 事实校验（`WorkflowTemplateConfigs.requireValid` 中，新建 / 修改时，读本库）：

| 问题码（新增 `WorkflowProblemCode`） | 条件 |
|---|---|
| `DATA_QUERY_TEMPLATE_NOT_FOUND` | data-query 模板不存在 |
| `DATA_QUERY_TEMPLATE_INACTIVE` | 模板或其数据源不是 ACTIVE |
| `DATA_QUERY_PARAMETER_UNDECLARED` | `parameters` 中有 data-query 未声明的参数 |
| `DATA_QUERY_PARAMETER_MISSING` | data-query 必填参数（无默认值）未列入 `parameters` |
| `DATA_QUERY_OUTPUT_UNDECLARED` | 模板声明了出参列（`columns_json` 非空）而 `satisfied`、`missing` 或某个 `out_<name>` 不在声明中（列名不区分大小写）；模板未声明出参列时不校验，运行时缺列按 `RESULT_SHAPE_INVALID` |
| `DATA_QUERY_OUTPUT_MASKED` | `satisfied`、`missing` 或任一 `out_<name>` 在模板中配置了屏蔽规则（屏蔽后的值不能用于判断与分支） |

发布校验（`TaskNodeRules`）：删除 `TEMPLATE_RESERVED_TYPE` 检查与枚举常量（前端同步删除）；对引用 DATA_QUERY 模板的动作，用 `WorkflowPublicationInput` 新增的 `Map<String, DataQueryTemplateFact> dataQueryTemplates` 重做上表 4 项（模板在快照后可能被改，B7）；另新增 `DATA_QUERY_INPUT_MISSING`：`parameters` 中的必填参数在节点动作 `inputs` 没有映射。VARIABLE 映射的变量可用性仍由 `VariableFlowRules` 报 `VARIABLE_UNAVAILABLE`；未在 `parameters` 中的输入仍报 `INPUT_KEY_UNDECLARED`。执行人仍必填（S-06）。

`DataQueryTemplateFact(String templateCode, boolean active, Map<String, Boolean> parameterRequired, Set<String> declaredColumns, Set<String> maskedColumns)`（列名统一小写）（`wf/domain/validation/`）由 `WorkflowDraftChecker` 从 data-query 的 `QueryTemplateSignature` 转换，`WorkflowPublicationInput` 增加该参数（保留原构造，默认空 Map）。

**配置校验接口（阶段 4 增补，2026-10-29 主会话确认）**：`POST /workflow-admin/v1/action-templates/validation`（`WORKFLOW_ACTION_TEMPLATE_MANAGE`，不保存），请求 `ValidateWorkflowActionTemplateRequestBody{actionType, config}`，响应 200 `WorkflowActionTemplateValidationResponseBody{problems: [WorkflowProblemBody]}`，与新建 / 修改同口径（先格式问题 ACTION_CONFIG_INVALID，无格式问题时再做上表 data-query 事实校验）。`itemKey`：PARAMETER_* 为参数 key，OUTPUT_* 为输出名（约定列为 `satisfied` / `missing`），TEMPLATE_* 为 null；nodeKey / edgeKey 为 null。结构错误仍 422。新建 / 修改的 400 不变（`BUSINESS_CHECK_FAILED`，detail 为合并中文原因）——Foundation `ApiProblemResponse` 为固定字段，无法在 400 中携带问题列表。示例：`{"problems":[{"code":"DATA_QUERY_OUTPUT_UNDECLARED","nodeKey":null,"edgeKey":null,"itemKey":"client_name","message":"查询模板 GOLIVE_CLIENT 没有返回列 out_client_name"}]}`。

人工完成 DATA_QUERY：`ActionOutputChecker` 对 DATA_QUERY 抛 `BizCheckFailException("系统检查动作由查询结果自动完成，不能手动完成")`（400，B3）。

### 8.5 Platform：data-query 模板选择（新增）`GET /workflow-admin/v1/data-query-templates`

- 新 API 接口 `WorkflowDataQueryTemplateApi`（`@HttpExchange("/workflow-admin/v1/data-query-templates")`）与 Controller `WorkflowDataQueryTemplateController`（app `ext/rest/workflow/`）；查询参数 `keyword`、`page`、`size`（默认 1 / 20，size ≤ 50）。
- 权限：`WORKFLOW_ACTION_TEMPLATE_MANAGE`（S-10 定稿：不要求 data-query 权限）。
- 实现：workflow `WorkflowQueryProcessor` 新方法 `listDataQueryTemplates`，调用 `QueryTemplateService.listQueryTemplates(null, QueryTemplateStatus.ACTIVE, keyword, paging)`，只返回模板编码、名称、说明、数据源名称与参数声明，不返回 SQL 正文与列声明。
- 响应 `PagingResponseBody<WorkflowDataQueryTemplateBody>`：`templateCode`、`templateName`、`description`、`dataSourceName`、`parameters: [{name, type, required}]`、`versionNo`（当前生效版本）、`timeoutSeconds`（模板自身超时，阶段 4 增补）。

### 8.6 data-query 模块：公开执行入口与模板签名

| 项 | 设计 |
|---|---|
| `RunDataQueryTemplateProcessor`（新增，`dq/service/processor/`，`AbstractProcessor<RunDataQueryTemplateRequest, DataQueryResult>`，无锁） | 按编码加载（`findExecutableTemplate`）：不存在 → `FailException(NOT_FOUND)`；模板或数据源非 ACTIVE → `BizCheckFailException`；然后 `DataQueryRunner.run(...)`。不做会话、权限与 HTTP 白名单；只能在事务外调用（AbstractProcessor 已检查） |
| `RunDataQueryTemplateRequest`（新增，`dq/service/processor/request/`） | `@NotBlank String templateCode`；`JsonNode params`（可空）；`Long operatorAccountId`（可空，workflow 固定传 null，S-09）；`Integer timeoutCapSeconds`（可空，1—8）；`@NotBlank String callerLabel`（只用于日志与指标，workflow 传 `workflow`） |
| `DataQueryRun` | `operatorAccountId` 改为 `Long`；新增 `Integer timeoutCapSeconds` |
| `DataQueryRunner.execute` | `QueryBudget` 超时取 `min(模板 timeoutSeconds, limits.maxTimeoutSeconds(), timeoutCapSeconds)`（cap 为 null 时忽略）；日志 `operator=` 为 null 时输出 `-` |
| `ExecuteDataQueryProcessor`（core） | 鉴权后改为委托 `RunDataQueryTemplateProcessor`（`operatorAccountId` = 会话账号，`callerLabel` = 调用方服务名，cap = null），删除重复的 `requireExecutable`；HTTP 行为不变 |
| `QueryTemplateService.findTemplateSignature(String templateCode)`（新增） | 返回 `QueryTemplateSignature`（`dq/domain/`，新增）：`templateCode`、`templateName`、`QueryTemplateStatus status`、`boolean dataSourceActive`、`List<QueryTemplateParameter> parameters`、`List<QueryTemplateColumn> columns`（`QueryTemplateColumn(String name, boolean masked)`，来自当前版本 `columns_json`，`masked` = 屏蔽规则非 NONE）；`QueryTemplateParameter(String name, QueryParameterType type, boolean required)`（`required` = 声明 required 且无 defaultValue）。不存在返回 null。只读本库，可在事务内调用 |

模块依赖：`paytech-platform-support-workflow/pom.xml` 增加 `paytech-platform-support-data-query`（单向；data-query 不依赖 workflow / access-control）。

### 8.7 operations-console-bff（转发）

| 项 | 变更 |
|---|---|
| `WorkflowModels.StartWorkflowInstanceRequestBody` | `definitionId` 去 `@NotBlank`（保留格式）、`versionNo` 去 `@NotNull`、新增 `@Pattern("[A-Z][A-Z0-9_]{0,63}") String definitionCode`；二选一由 Platform 判定 |
| `WorkflowInstanceController` | 新增 `@PostMapping("/action-runs/{actionRunId}/data-query-check")` → `PlatformWorkflowClient.checkDataQuery(context, actionRunId)`，调用 Platform 时发 `{}`；校验返回的 `actionRunId` 与路径一致（同 `submitAction`），否则 `UPSTREAM_INVALID_RESPONSE` |
| `WorkflowDataQueryTemplateController`（新增，`@RequestMapping("/workflow-admin/v1/data-query-templates")`） | `@GetMapping` 透传 keyword / page / size |
| 响应体 | 直接复用 Platform api 类型，`created`、`dataQueryCheck`、`actionType` 自动透传 |

### 8.8 Gateway 注册（块 142）

mor-test 只读核对（2026-10-29）：`t_gateway_route` 最大 `900000000000141403`，`t_permission` 最大 `…139101`，`t_console_resource` 最大 `…139301`，`t_gateway_route_header` 最大 `…141526`；块 142 未被任何需求占用。

| 表 | ID | 内容 |
|---|---|---|
| `t_gateway_route` | `900000000000142401` | `operations-workflow-data-query-check`，OPERATIONS，POST，`/api/operations/workflow/v1/action-runs/{actionRunId}/data-query-check` → `/workflow/v1/action-runs/{actionRunId}/data-query-check`，target `@workflow_console_target_id`（ops-bff），`OPERATIONS_JWT`、`HUMAN_PERMISSION`、permission `900000000000127105`（WORKFLOW_TASK_HANDLE），cookie DROP / NONE，`idempotency_mode=NONE`，body 1024，5000 / 10000 |
| `t_gateway_route` | `900000000000142402` | `operations-workflow-data-query-template-list`，GET，`/api/operations/workflow-admin/v1/data-query-templates` → `/workflow-admin/v1/data-query-templates`，permission `900000000000127103`（WORKFLOW_ACTION_TEMPLATE_MANAGE），body 0，5000 / 10000 |
| `t_gateway_route_header` | `…142501`—`…142506` | 142401：authorization CORS_REQUEST_ALLOWED、origin FORWARD_REQUEST、content-type CORS_REQUEST_ALLOWED + FORWARD_REQUEST、idempotency-key CORS_REQUEST_ALLOWED + FORWARD_REQUEST（同 127420） |
| `t_gateway_route_header` | `…142507`—`…142508` | 142402：authorization CORS_REQUEST_ALLOWED、origin FORWARD_REQUEST（同 127416） |
| `t_gateway_route_query_param` | `…142001`—`…142003` | 142402：keyword、page、size |
| `t_gateway_guard_rule` | `…142601`—`…142606` | 142401：RATE_LIMIT 主体 10/60s、IP 30/60s、路由 300/60s；CONCURRENCY 主体 2、IP 3、路由 50（同 127420） |
| `t_gateway_guard_rule` | `…142607`—`…142612` | 142402：RATE_LIMIT 主体 30/60s、IP 30/60s、路由 300/60s；CONCURRENCY 2 / 3 / 50（同 127416） |
| `t_gateway_route` | `900000000000142403` | 阶段 4 增补：`operations-workflow-template-validation`，POST，`/api/operations/workflow-admin/v1/action-templates/validation` → `/workflow-admin/v1/action-templates/validation`，permission 127103，body 65536，5000 / 10000 |
| `t_gateway_route_header` | `…142509`—`…142514` | 142403：同 127403（authorization、origin、content-type、idempotency-key） |
| `t_gateway_guard_rule` | `…142613`—`…142618` | 142403：同 127403，主体 30/60s（页面编辑时校验） |

无新权限、角色授权与菜单 / 按钮（"立即检查"跟随卡片可见性）。文件：`paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 追加块 142（路由按惯例 `DISABLED`）；增量 `requirements/20261028/platform-support-workflow按钮发起与数据验证/db/registration-142.sql`（插入）与 `registration-142-activate.sql`（两路由置 ACTIVE）、`registration-142-rollback.sql`（按 ID 删除，子表先删）。执行前先离线编译 Gateway 快照（DISABLED 路由同样参与编译）。

## 9. 数据库（`mor_platform`）

### 9.1 `t_workflow_instance`

| 列 / 索引 | 变更 | 定义 |
|---|---|---|
| `business_key` | 改排序规则与注释 | `VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '可选的业务关联键（如 CLIENT:880000000101），按原文精确匹配；给出时同一流程定义下只允许一个 RUNNING 实例（见 active_slot），重复发起返回已有实例；未填写时为空。'` |
| `active_slot` | **新增**，置于 `status` 之后 | `TINYINT NULL COMMENT '业务关联键防重槽位：实例 RUNNING 且 business_key 非空时为 1，完成或终止时置空；与 definition_id、business_key 组成唯一索引。'` |
| `uk_workflow_inst_active` | **新增** | `UNIQUE KEY (definition_id, business_key, active_slot) COMMENT '保证同一流程定义下同一业务关联键只有一个 RUNNING 实例'` |
| `idx_workflow_inst_bizkey` | 保留 | 按业务键检索（不带定义） |

### 9.2 `t_workflow_action_run`

| 列 / 索引 | 变更 | 定义 |
|---|---|---|
| `action_type` | 改注释 | `…允许值：FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY。` |
| `last_check_time` | 新增，置于 `submitted_time` 之后 | `DATETIME(6) NULL COMMENT 'DATA_QUERY 最近一次检查结果落库的 UTC 微秒时间；其他类型与尚未检查时为空。'` |
| `check_result` | 新增 | `VARCHAR(32) NULL COMMENT 'DATA_QUERY 最近一次检查结果：SATISFIED 已满足（动作随之完成）；UNSATISFIED 查询成功但未满足；FAILED 查询失败、超时或返回形状不符。允许值：SATISFIED、UNSATISFIED、FAILED。'` |
| `check_detail` | 新增 | `VARCHAR(512) NULL COMMENT 'UNSATISFIED 时为查询返回的原因码（逗号分隔，可空）；FAILED 时为失败码；SATISFIED 时为空。'` |
| `check_failure_count` | 新增 | `INT NOT NULL DEFAULT 0 COMMENT 'DATA_QUERY 连续检查失败次数；查询成功执行（满足或未满足）时清零。'` |
| `last_check_success_time` | 新增 | `DATETIME(6) NULL COMMENT 'DATA_QUERY 最近一次查询成功执行的 UTC 微秒时间（满足或未满足）；从未成功为空。'` |
| `idx_workflow_action_check` | 新增 | `KEY (action_type, status, last_check_time) COMMENT '支持访问路径：定时重查扫描待检查的 DATA_QUERY 动作'` |

`t_workflow_action_template.action_type` 注释改为 `动作的提交与验证方式；DATA_QUERY 由 data-query 查询结果自动完成。允许值：…`。

失败码（`check_detail`，workflow 枚举 `DataQueryCheckFailure`）：`TEMPLATE_UNAVAILABLE`、`PARAMETER_INVALID`、`QUERY_TIMEOUT`、`DATA_SOURCE_UNAVAILABLE`、`RESULT_TOO_LARGE`、`RESULT_SHAPE_INVALID`、`QUERY_FAILED`。结果枚举 `DataQueryCheckResult`：SATISFIED、UNSATISFIED、FAILED（`wf/core/enums/`，相邻常量空一行）。

### 9.3 增量脚本与执行顺序

文件：`paytech/platform-support/docs/database/sql/changes/2026-10-29-workflow-data-query.sql`（前滚）、`2026-10-29-workflow-data-query-rollback.sql`（回滚）；需求目录 `db/ddl.sql` 只写索引说明（同 REQ-01）。

前滚（可重跑：每步前查 `information_schema`）：

```sql
-- 0 预检（必须返回 0 行；有行则停止并人工处理）
SELECT definition_id, BINARY business_key AS business_key, COUNT(*) AS running
FROM t_workflow_instance
WHERE status = 'RUNNING' AND business_key IS NOT NULL
GROUP BY definition_id, BINARY business_key HAVING COUNT(*) > 1;

-- 1 实例：排序规则、槽位列
ALTER TABLE t_workflow_instance
  MODIFY COLUMN business_key VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT '<9.1 注释>',
  ADD COLUMN active_slot TINYINT NULL COMMENT '<9.1 注释>' AFTER status;
-- 2 回填槽位
UPDATE t_workflow_instance SET active_slot = 1 WHERE status = 'RUNNING' AND business_key IS NOT NULL;
-- 3 唯一索引
ALTER TABLE t_workflow_instance ADD UNIQUE KEY uk_workflow_inst_active (definition_id, business_key, active_slot)
  COMMENT '保证同一流程定义下同一业务关联键只有一个 RUNNING 实例';
-- 4 动作运行
ALTER TABLE t_workflow_action_run
  MODIFY COLUMN action_type VARCHAR(32) NOT NULL COMMENT '<9.2 注释>',
  ADD COLUMN last_check_time DATETIME(6) NULL COMMENT '<…>' AFTER submitted_time,
  ADD COLUMN check_result VARCHAR(32) NULL COMMENT '<…>' AFTER last_check_time,
  ADD COLUMN check_detail VARCHAR(512) NULL COMMENT '<…>' AFTER check_result,
  ADD COLUMN check_failure_count INT NOT NULL DEFAULT 0 COMMENT '<…>' AFTER check_detail,
  ADD COLUMN last_check_success_time DATETIME(6) NULL COMMENT '<…>' AFTER check_failure_count,
  ADD KEY idx_workflow_action_check (action_type, status, last_check_time) COMMENT '<…>';
-- 5 模板注释
ALTER TABLE t_workflow_action_template MODIFY COLUMN action_type VARCHAR(32) NOT NULL COMMENT '<9.2 注释>';
```

回滚（新代码停止后执行）：先人工处理仍 PENDING 的 DATA_QUERY 动作（旧代码不会再检查，按需终止所在实例）；然后 `DROP INDEX uk_workflow_inst_active`、`DROP COLUMN active_slot`、`business_key` 以原注释 MODIFY 回表默认排序规则（不写 COLLATE）、`DROP INDEX idx_workflow_action_check`、删除 5 列、两列 `action_type` 注释恢复原文。检查状态随列丢失。

执行顺序：前滚 SQL（新列可空或有默认值，旧代码照常读写）→ Platform 新镜像 → ops-bff → 注册块 142 插入、离线编译、激活 → ops-web 与语言包 → XXL 任务 → data-query 模板与验收定义。回滚反向：XXL 停用 → ops-web → 块 142 回滚 → ops-bff → Platform 旧镜像 → 回滚 SQL。

### 9.4 PDManer（`paytech/platform-support/docs/database/platform-support.pdma.json`，直接编辑，不用生成器）

- `t_workflow_instance`：`business_key` 字段 `comment` 改 9.1，`extProps` 设为 `{"characterSet": "utf8mb4", "collation": "utf8mb4_bin"}`（同 `definition_code`）；`status` 后新增字段 `active_slot`（`type` TINYINT，`baseType` 同 `t_workflow_action_run.required` 的 TINYINT 基类 `1D764C4A-…`，`notNull` false，新 UUID）；`indexes` 新增 `uk_workflow_inst_active`（`unique: true`，三列 `fieldDefKey` 指向对应字段 id）。
- `t_workflow_action_run`：`action_type` 注释；`submitted_time` 后新增 5 字段（DATETIME 用 `submitted_time` 的 baseType，VARCHAR 用 `status` 的 baseType，INT 用 `version` 的 baseType；`check_failure_count` `notNull` true、`defaultValue` `0`）；`indexes` 新增 `idx_workflow_action_check`。
- `t_workflow_action_template`：`action_type` 注释。
- 同步 `platform-support-schema.sql` 三张表定义；`agent-docs/database/PLATFORM_SUPPORT_DATABASE.md` 第 323 行起的说明。

### 9.5 Mapper 与 DataObject

| 文件 | 变更 |
|---|---|
| `WorkflowInstance`（DO） | 新增 `Integer activeSlot` |
| `WorkflowInstanceMapper.xml` | `columns` / `aliasedColumns` 加 `active_slot AS activeSlot`；`insert` 加 `active_slot`；`update` 的 SET 增加 `active_slot = IF(#{row.status} = 'RUNNING', active_slot, NULL)`（条件仍为 `version` + `status='RUNNING'`）；新增 `selectRunningByBusinessKey`：`WHERE definition_id = #{definitionId} AND business_key = #{businessKey} AND active_slot = 1` |
| `WorkflowInstanceRepository` / `Impl` | 新增 `WorkflowInstance findRunningByBusinessKey(long definitionId, String businessKey)` |
| `WorkflowActionRun`（DO） | 新增 `Instant lastCheckTime`、`DataQueryCheckResult checkResult`、`String checkDetail`、`Integer checkFailureCount`、`Instant lastCheckSuccessTime` |
| `WorkflowActionRunMapper.xml` | `columns`、`insert` 加 5 列（insert 时 `check_failure_count` 取 0）；`update` 的 SET 加 5 列（条件仍为 `version`）；新增 `selectDueDataQueryChecks`：`WHERE action_type = 'DATA_QUERY' AND status = 'PENDING' AND MOD(instance_id, #{shardTotal}) = #{shardIndex} AND (last_check_time IS NULL OR last_check_time < #{checkedBefore}) ORDER BY last_check_time IS NOT NULL, last_check_time, action_run_id LIMIT #{limit}` |
| `WorkflowActionRunRepository` / `Impl` | 新增 `List<WorkflowActionRun> findDueDataQueryChecks(int shardIndex, int shardTotal, Instant checkedBefore, int limit)` |
| `WorkflowRuntimeService` / `Impl` | 新增 `void recordDataQueryCheck(WorkflowActionRun row)`：只调用 `actionRuns.update(row)`（动作 version CAS，不动实例） |

## 10. 运行组件、配置与 Job

### 10.1 workflow 模块新增 / 调整类

| 类（`wf/` 下） | 职责 |
|---|---|
| `domainservice/process/DataQueryParameterResolver`（纯 Java） | `ObjectNode resolve(Map<String, InputMapping> inputs, WorkflowVariables variables)`：VARIABLE → 变量当前值（缺失为 JSON null）；CONSTANT → 文本 |
| `domainservice/process/DataQueryResultReader`（纯 Java） | `DataQueryCheckOutcome read(List<String> columnNames, List<List<Object>> rows, boolean hasMore, List<DataQueryOutput> outputs)`：0 行 → UNSATISFIED（无原因码）；>1 行或 `hasMore` → FAILED；列名不区分大小写匹配；`satisfied` 接受 Boolean 或 "1"/"0"/"true"/"false"，其他（含缺列、null）FAILED；`missing` 为空或逗号分隔，逐项 trim、去空，每项须 `[A-Z][A-Z0-9_]{0,63}`、≤ 20 项、合计 ≤ 512 字符，否则 FAILED；满足时逐个声明输出取 `out_<name>`：缺列 FAILED，null 写 JSON null，TEXT 取文本（布尔转 "true"/"false"），NUMBER 须为十进制文本（原样写字符串），BOOLEAN 接受 Boolean 或 1/0/true/false 写 JSON 布尔，ENUM 须在 `enumValues`，DATE 须 `YYYY-MM-DD`；任一不符 FAILED（`RESULT_SHAPE_INVALID`）；未声明的 `out_*` 忽略 |
| `domain/runtime/DataQueryCheckOutcome`（record） | `DataQueryCheckResult result`、`ObjectNode outputs`（仅 SATISFIED）、`List<String> missing`、`DataQueryCheckFailure failure` |
| `WorkflowRuntimeEngine.completeDataQuery(context, actionRunId, outputs, ids)` | 要求实例 RUNNING、动作 DATA_QUERY 且 PENDING、节点 ACTIVE / APPROVING；写 `output_json`、`status=COMPLETED`、`submitted_time=now`、`submitted_by=null`；节点 ACTIVE 且必选全部完成时 `advanceAfterActions(context, nodeRun, recorder, null)`（`actorId` 参数改为 `Long`）；`finish` |
| `service/processor/CheckWorkflowDataQueryProcessor` | 第 7 节步骤 1—5；请求 `CheckDataQueryRequest(long actionRunId, DataQueryCheckTrigger trigger, String actor, String session)`（`trigger` ∈ ACTIVATION / SCHEDULE / MANUAL，MANUAL 时 actor / session 必填并做可见性检查）；返回 `DataQueryCheckView`（供 8.2 响应）；锁 `workflow-action-check:{actionRunId}`，`tryLock(key, 0, checkLockLease)` |
| `service/processor/ApplyWorkflowDataQueryCheckProcessor` | 第 7 节步骤 5；锁 `WorkflowInstanceCommands.lock(instanceId)`，`tryLock(key, lockWaitMillis, lockLeaseMillis)`；MANUAL 时在事务内 `support.authorize(TASK_HANDLE)` 并 `support.succeeded(…, "WORKFLOW_ACTION_RUN", id, "CHECKED")`，ACTIVATION / SCHEDULE 不写 Operations 审计 |
| `service/processor/RecheckWorkflowDataQueryProcessor`（`AbstractProcessor`，无锁） | 输入 `shardIndex`、`shardTotal`；`findDueDataQueryChecks(…, now − recheckMinAge, recheckBatchSize)`，逐个调用检查 Processor（SCHEDULE），超过 `recheckRoundBudget` 停止；检查锁冲突计 skipped，实例锁冲突计 busy，其他异常计 failed 并继续；返回计数 |
| `service/handler/WorkflowDataQueryCheckHandler`（`CompensationEventHandler`） | `EVENT_TYPE = "WORKFLOW_DATA_QUERY_CHECK"`，`eventKey` = 动作运行 ID，`subEventType` = `WorkflowCallbackHandlerSupport.NO_SUB_EVENT_TYPE`；`handle` 调检查 Processor（ACTIVATION）：业务结果（含查询失败）与检查锁冲突 → `success()`；实例锁冲突 → `retryableFailure("WORKFLOW_INSTANCE_BUSY", …)`；其他异常 → `retryableFailure`。策略 `new CompensationExecutionPolicy(Duration.ofSeconds(20), 5, Duration.ofSeconds(5), Duration.ofMinutes(1), …)`（余下参数同 `WorkflowNotificationHandler`） |
| `WorkflowProcessorSupport.registerCallbackEvents(change)` | 追加：`change.getNewActionRuns()` 中 `actionType == DATA_QUERY` 的每一行登记 `WorkflowDataQueryCheckHandler.buildEvent(actionRunId)`（所有写入口共用，覆盖级联激活） |
| `WorkflowResponseAssembler.actionRun(...)` | 增加检查块装配（需版本快照中的模板配置） |
| `WorkflowDraftChecker` | 收集被引用 DATA_QUERY 模板的 `queryCode`，逐个 `QueryTemplateService.findTemplateSignature`，转为 `DataQueryTemplateFact` 注入发布校验输入 |
| `WorkflowTemplateConfigs` | 注入 `QueryTemplateService`；DATA_QUERY 时追加 8.4 的 data-query 事实校验 |

### 10.2 app 模块

| 类 | 职责 |
|---|---|
| `ext/job/WorkflowDataQueryRecheckJobHandler` | `@XxlJob("workflowDataQueryRecheck")`；`runOnce(int shardIndex, int shardTotal)` 调用 `RecheckWorkflowDataQueryProcessor`；摘要 `checked / completed / unsatisfied / failed / skipped / busy`；有 failed 时 `XxlJobHelper.handleFail`；`@ConditionalOnProperty(operations.authentication.enabled)`（同 workflow Processor）。仿 commercial `CapabilityPrerequisiteRecheckJobHandler` |
| `ext/rest/workflow/WorkflowInstanceController` | 新增 `checkDataQuery` |
| `ext/rest/workflow/WorkflowDataQueryTemplateController` | 新增 |
| `WorkflowConverter` | `start` 二选一判定；`mutation` 透传 `created` |

### 10.3 配置（`WorkflowProperties` 新增嵌套 `DataQuery dataQuery`，前缀 `workflow.data-query`）

| 键 | 默认 | 校验 | 说明 |
|---|---|---|---|
| `timeout` | `5s` | 1—8 秒整秒 | 单次查询超时上限，传给 `timeoutCapSeconds`（S-11） |
| `check-lock-lease` | `30s` | ≥ timeout + 10s | 检查锁租约 |
| `recheck-batch-size` | `100` | 1—1000 | 每分片每轮最多检查数 |
| `recheck-min-age` | `4m` | ≥ 0 | 最近检查早于此值的动作才进入本轮（避免与"立即检查"、首查重叠）；测试配置 `0s` |
| `recheck-round-budget` | `240s` | > 0 | 每轮耗时上限，超过即停（每 5 分钟调度，避免重叠） |

`application.yml` 的 `workflow:` 段与测试 `application-mock.yml` 同步增加；重查间隔由 XXL 调度表达式决定，不进配置。

### 10.4 XXL 任务与补偿目录

- mor-test：执行器组 2（`platform-support-service`）新增任务 `workflowDataQueryRecheck`，CRON `0 */5 * * * ?`，路由 `SHARDING_BROADCAST`，阻塞策略 `DISCARD_LATER`，超时 300 秒，参数空。XXL 写入在主会话执行（发布动作，单独授权）。
- `paytech/platform-support/compensation-event-catalog.yaml` 追加 `WORKFLOW_NOTIFICATION`、`WORKFLOW_CALLBACK_EVENT`（存量补登，S-17）、`WORKFLOW_DATA_QUERY_CHECK`。
- 指标：`workflow.data_query.check{trigger, result}` 计数（result 取 SATISFIED / UNSATISFIED / 失败码），作为告警计数来源；日志只记动作运行 ID、模板编码、结果与耗时，不记参数与结果行。

## 11. 文案键（`operations-console-web/src/i18n/en/workflow.json` 与 `operations-console-web/i18n/zh-CN/workflow.json`）

删除：`workflow.dataQuery.column / evidence / evidenceHint / firstRow / passRule / passRuleHint / queryCodeHint / reserved / reservedHint`、`workflow.actions.dataQueryUnavailable`、`workflow.graph.problem.TEMPLATE_RESERVED_TYPE`、`workflow.preview.dataQuerySoon`、`workflow.templateEditor.dataQueryUnavailable`。保留：`workflow.dataQuery.parameter / parameters / queryCode / source.*`、`workflow.enum.actionType.DATA_QUERY`。

新增（en / zh-CN）：

| Key | en | zh-CN |
|---|---|---|
| `workflow.dataQuery.template` | Query template * | 查询模板 * |
| `workflow.dataQuery.templateHint` | Only active data-query templates are listed. | 只列出启用中的数据查询模板。 |
| `workflow.dataQuery.templateEmpty` | No active query templates match. | 没有匹配的启用模板。 |
| `workflow.dataQuery.parametersHint` | Map each parameter on the process node. | 参数在流程节点上映射取值。 |
| `workflow.dataQuery.outputs` | Outputs | 输出变量 |
| `workflow.dataQuery.outputsHint` | Each output reads the column out_{name} and becomes the variable {actionKey}.{name}. | 每个输出读取列 out_{name}，写入变量 {actionKey}.{name}。 |
| `workflow.dataQuery.outputName` | Name | 名称 |
| `workflow.dataQuery.outputType` | Type | 类型 |
| `workflow.dataQuery.outputEnumValues` | Allowed values | 可选值 |
| `workflow.dataQuery.addOutput` | Add output | 添加输出 |
| `workflow.dataQuery.descriptionKey` | Criteria text key | 完成标准文案键 |
| `workflow.dataQuery.instruction` | Criteria description | 完成标准说明 |
| `workflow.dataQuery.check.title` | System check | 系统检查 |
| `workflow.dataQuery.check.SATISFIED` | Satisfied | 已满足 |
| `workflow.dataQuery.check.UNSATISFIED` | Not yet satisfied | 尚未满足 |
| `workflow.dataQuery.check.FAILED` | Check failed | 检查失败 |
| `workflow.dataQuery.check.checking` | Checking… | 检查中… |
| `workflow.dataQuery.check.lastChecked` | Last checked {time} | 最近检查 {time} |
| `workflow.dataQuery.check.lastSucceeded` | Last successful check {time} | 上次成功 {time} |
| `workflow.dataQuery.check.neverSucceeded` | No successful check yet | 尚无成功检查 |
| `workflow.dataQuery.check.failures` | {count} failures in a row | 连续失败 {count} 次 |
| `workflow.dataQuery.check.missing` | Still missing | 还差 |
| `workflow.dataQuery.check.autoComplete` | Completes automatically when the check passes; rechecked every 5 minutes. | 检查通过后自动完成，每 5 分钟重查一次。 |
| `workflow.dataQuery.check.now` | Check now | 立即检查 |
| `workflow.dataQuery.check.inProgress` | A check is already running. Refresh in a moment. | 已有检查在进行，请稍后刷新。 |
| `workflow.dataQuery.check.failed` | Couldn't run the check. | 无法执行检查。 |
| `workflow.dataQuery.check.completed` | "{name}" is satisfied and completed. | "{name}"已满足，已自动完成。 |
| `workflow.dataQuery.failure.TEMPLATE_UNAVAILABLE` | The query template or its data source is unavailable. | 查询模板或数据源不可用。 |
| `workflow.dataQuery.failure.PARAMETER_INVALID` | Query parameters are invalid. | 查询参数不合法。 |
| `workflow.dataQuery.failure.QUERY_TIMEOUT` | The query timed out. | 查询超时。 |
| `workflow.dataQuery.failure.DATA_SOURCE_UNAVAILABLE` | The data source can't be reached. | 数据源无法连接。 |
| `workflow.dataQuery.failure.RESULT_TOO_LARGE` | The query returned too much data. | 查询结果过大。 |
| `workflow.dataQuery.failure.RESULT_SHAPE_INVALID` | The query result doesn't match the expected shape. | 查询结果不符合约定形状。 |
| `workflow.dataQuery.failure.QUERY_FAILED` | The query failed. | 查询失败。 |
| `workflow.dataQuery.missing.<CODE>` | 各原因码文案，缺失时显示原码（S-16）；本需求随验收模板提供 `AGREEMENT_NOT_DRAFTED`、`TERM_VERSION_NOT_APPROVED`、`AGREEMENT_NOT_ACTIVE`、`TERM_VERSION_NOT_ACTIVE`、`CAPABILITY_NOT_ENABLED` 5 条 | 同左 |
| `workflow.dataQuery.criteria.<QUERY_CODE>` | 完成标准文案，本需求提供 REQ-01 三条 `GOLIVE_*` | 同左 |
| `workflow.activity.system` | System | 系统 |
| `workflow.task.dataQueryNoComplete` | This action completes automatically. | 该动作由系统检查自动完成。 |
| `workflow.graph.problem.DATA_QUERY_TEMPLATE_NOT_FOUND` | Query template {code} doesn't exist. | 查询模板 {code} 不存在。 |
| `workflow.graph.problem.DATA_QUERY_TEMPLATE_INACTIVE` | Query template {code} or its data source is disabled. | 查询模板 {code} 或其数据源已停用。 |
| `workflow.graph.problem.DATA_QUERY_PARAMETER_UNDECLARED` | Parameter {key} isn't declared by query template {code}. | 查询模板 {code} 未声明参数 {key}。 |
| `workflow.graph.problem.DATA_QUERY_PARAMETER_MISSING` | Required parameter {key} of query template {code} isn't listed. | 未列出查询模板 {code} 的必填参数 {key}。 |
| `workflow.graph.problem.DATA_QUERY_OUTPUT_UNDECLARED` | Query template {code} doesn't return column {column}. | 查询模板 {code} 没有返回列 {column}。 |
| `workflow.graph.problem.DATA_QUERY_OUTPUT_MASKED` | Column {column} of query template {code} is masked and can't be used. | 查询模板 {code} 的列 {column} 已屏蔽，不能用于判断。 |
| `workflow.graph.problem.DATA_QUERY_INPUT_MISSING` | Map a value for required parameter {key}. | 请为必填参数 {key} 映射取值。 |

最终措辞以原型确认为准；键随 operations-console 语言包发布到 Platform（新 bundle 号发布时取）。

## 12. 测试与验证计划

### 12.1 auto（Platform，本机 `local-environment` MySQL / Redis / RabbitMQ + 被查库 `mor_data_query_fixture`）

被查模板由 `DataQueryMocker.activeReaderSource` + `DataQueryMocker.template` 登记在 fixture 只读账号上，例如：
`SELECT IF(COUNT(*) > 0, 1, 0) AS satisfied, MAX(client_type) AS out_client_type, IF(COUNT(*) > 0, NULL, 'CLIENT_NOT_FOUND') AS missing FROM t_client_fixture WHERE client_id = #{clientId}`（1001 满足、9999 未满足）；超时用 `t_number_fixture` 三表 CROSS JOIN；改版本用 `QueryTemplateService.saveQueryTemplate` 切换满足 / 未满足。测试类放 `paytech-platform-support-test/.../auto/ext/http/workflow/`（HTTP 入口）与 `auto/ext/eventhandler/workflow/`、`auto/ext/job/`（新目录，Job 入口）。

| 验收 | 入口 | 场景 |
|---|---|---|
| AC-01 | HTTP 发起 | 编码发起 `created=true`；同键再发 `created=false` 同 ID；两线程同时发起只产生一个 RUNNING（DB 计数 1，另一个 `created=false`）；ID + 版本模式同键也被防重；`business_key` 大小写不同视为不同键 |
| AC-02 | HTTP 发起 + 终止 / 完成 | 终止后同键再发 → 新实例 `created=true`；完成后同理 |
| AC-03 | HTTP 发起 | 定义 INACTIVE、从未发布 → 400 `BUSINESS_CHECK_FAILED`；二选一违反 → 400 `INVALID_REQUEST`；编码不存在 → 404 |
| AC-04 | HTTP 动作模板 + 定义发布 | 合法 DATA_QUERY 模板可建、定义可发布；模板编码不存在 / 停用 / 参数未声明 / 必填参数缺失 / 输出列未声明 / 输出列被屏蔽 → 400 并列出原因；发布前把 data-query 模板停用 → `DATA_QUERY_TEMPLATE_INACTIVE`；节点未映射必填参数 → `DATA_QUERY_INPUT_MISSING`；映射不可用变量 → `VARIABLE_UNAVAILABLE`；旧键 `passRule` → 422 |
| AC-05 | HTTP 发起 → 补偿任务（`WorkflowFixture.awaitIdle`） | 首节点 DATA_QUERY 满足：动作 COMPLETED、`output_json` 只含声明输出、`submitted_by` 为空、节点推进；连续两个 DATA_QUERY 节点级联完成 |
| AC-06 | 补偿 + Job（`WorkflowDataQueryRecheckJobHandler.runOnce`） | 未满足：PENDING、`check_result=UNSATISFIED`、`check_detail=CLIENT_NOT_FOUND`、时间已记；模板改为满足后跑 Job → 完成推进；`recheck-min-age` 内的动作不被扫描；分片 0/2、1/2 不重复 |
| AC-07 | 补偿 + Job | 超时（cap 1 秒）→ FAILED `QUERY_TIMEOUT`、失败计数 1，再跑 Job → 2；两行 / 缺 `satisfied` / 输出类型不符 → `RESULT_SHAPE_INVALID`；data-query 模板停用 → `TEMPLATE_UNAVAILABLE`；参数缺失 → `PARAMETER_INVALID`；成功后计数清零；全程不登记 `VERIFICATION_FAILED` 等新通知、实例仍可人工操作 |
| AC-08 | HTTP 发起 → 补偿 | 输出 ENUM / BOOLEAN（0/1 规整为布尔）/ TEXT CONTAINS 驱动排他网关走对应边 |
| AC-09 | HTTP 立即检查 | 参与人执行并返回结果；非参与人无 `INSTANCE_READ` → 404、有则 200；持有检查锁时 → `inProgress=true`；持有实例锁时 → 409；已完成动作 → 200 不执行；非 DATA_QUERY → 400；两个并发请求只执行一次查询（被查侧以慢查询区分） |
| AC-10 | 补偿 | 模板多返回未声明的 `out_extra` → 不写入 |
| 并发 | 立即检查 + 终止 | 慢查询进行中终止实例 → 检查结果丢弃，动作 CANCELLED，无异常回滚 |
| B3 | HTTP 完成 | 人工完成 DATA_QUERY → 400 |
| 列表 | HTTP 我的待办 / 详情 | `actionType`、`dataQueryCheck` 字段；检查前 `checking=true` |

data-query：`RunDataQueryTemplateProcessor`（模块公开入口）空操作人、cap 生效；现有 `DataQueryExecutionApiTest` 全部回归（委托后 HTTP 行为不变）。workflow 模块纯 Java：`DataQueryResultReader`、`DataQueryParameterResolver`、配置读取与校验（`auto/domain`）。arch-rules：模块依赖与补偿目录。BFF：发起体新字段透传、立即检查转发与 ID 校验、模板选择透传（ops-bff test 模块现有测试方式）。Web：`catalogModel` / `templateModel` / `graphModel` / `runtimeModel` / `taskModel` 单测、tsc、build、i18n 校验。

### 12.2 本机联调（阶段 5）

`mor_platform_v2e2e` 执行前滚 SQL 与块 142（插入 + 激活）；Platform / ops-bff 按本机 JAR 替换规范（临时文件 + rename，原参数重启）；ops-web 本地构建。data-query 后台登记 fixture 数据源与 1 条满足 / 1 条未满足模板，建 DATA_QUERY 动作模板与含排他网关的定义；浏览器 en / zh-CN × 1440 / 390 走：动作目录新建、流程编辑与发布报错、发起与重复发起、任务卡四态与立即检查、实例详情活动"系统"。旧形状 DATA_QUERY 模板（e2e 库 1 条）先停用。

### 12.3 mor-test 验收（阶段 6—7，单独授权）

1. 前滚 SQL（预检 0 行）→ Platform → ops-bff → 块 142 → ops-web + 语言包 → XXL 任务（第 10.4 节）。
2. 运营在 data-query 后台于 `MOR_COMMERCIAL_REPLICA` 登记 REQ-01 三条模板（正文取 `requirements/20261028/commercial-开通项前置门禁/db/data-query-templates.sql`）：`GOLIVE_AGREEMENT_DRAFTED`（参数 `clientId` INTEGER 必填）、`GOLIVE_AGREEMENT_ACTIVE`、`GOLIVE_COLLECTION_CAPABILITIES_ENABLED`（参数 `agreementId` INTEGER 必填），约束 `pagingMode=NONE`、`maxRows=1`、`timeoutSeconds=5`。
3. 建 3 个 DATA_QUERY 动作模板（输出：`agreement_id` TEXT、`business_mode` ENUM、`capability_codes` TEXT；`pending_capabilities` TEXT）与验收定义 `ACC_REQ05_GOLIVE_COMMERCIAL`：启动表单 `clientId` → N3 起草检查 → 排他网关（`n3.business_mode`）→ N4 合同生效检查（`agreementId ← n3.agreement_id`）→ N8 收款开通检查 → END。
4. 对一个合同已生效的演示 Client 以 `businessKey=CLIENT:<clientId>` 发起两次（同一实例）；观察首查、等待 XXL 一轮、立即检查；对缺价格的演示合同确认停在 N4 并显示原因码；浏览器 en / zh-CN × 1440 / 390。
5. 回滚演练只做离线（块 142 编译、回滚 SQL 审阅）。

## 13. 实施切分（执行顺序）

| # | 任务 | 依赖 | 产物 |
|---|---|---|---|
| T1 | 数据模型：PDManer、`platform-support-schema.sql`、前滚 / 回滚脚本、需求目录 `db/ddl.sql`；本机 auto 库 `mor_platform` 执行前滚（本机授权内） | — | 9.1—9.4 |
| T2 | data-query：`DataQueryRun` / Runner cap、`RunDataQueryTemplateProcessor`、`QueryTemplateSignature` 与 `findTemplateSignature`、core `ExecuteDataQueryProcessor` 委托 | — | 8.6 + 回归 |
| T3 | workflow 配置与发布：`DataQueryActionConfig` / `DataQueryOutput`、读取器、校验器、`WorkflowTemplateConfigs`、`TaskNodeRules`、`WorkflowPublicationInput`、`WorkflowDraftChecker`、问题码、`ActionOutputChecker` 文案；pom 依赖 | T2 | 8.4 |
| T4 | 发起：请求 / API 体、`WorkflowConverter`、`StartWorkflowInstanceProcessor`、`WorkflowMutation.created`、实例 DO / Mapper / Repository | T1 | 8.1 |
| T5 | 检查运行：结果读取器、参数解析器、引擎 `completeDataQuery`、检查 / 落库 / 重查 Processor、补偿 Handler、`registerCallbackEvents` 扩展、动作运行 DO / Mapper / Repository / Service、配置类 | T1 T2 T3 | 第 7、10 节 |
| T6 | 接口与 app：立即检查与模板选择 API / Controller、检查块与 `actionType` 装配、JobHandler、`application.yml`、补偿目录 | T4 T5 | 8.2 / 8.3 / 8.5 / 10.2—10.4 |
| T7 | auto 测试（12.1）与 arch-rules；Platform 全模块测试串行跑（共享本机库） | T6 | 测试报告 |
| T8 | 注册块 142：`platform-support-initial-data.sql` 与需求目录增量 / 激活 / 回滚脚本；离线编译 | T6 | 8.8 |
| T9 | ops-bff：发起体字段、立即检查与模板选择转发、测试 | T6 | 8.7 |
| T10 | ops-web（**Pencil 原型确认前不开始**）：类型与 API、动作目录 DATA_QUERY 编辑器、流程编辑器放开与问题码、任务卡 / 详情检查块与立即检查、活动"系统"、文案 en / zh-CN | T9 + 原型确认 | 第 11 节 + 单测 / build |
| T11 | 文档：workflow design-discuss、`PLATFORM_SUPPORT_DATABASE.md`、`CURRENT_IMPLEMENTATION.md`（实现后） | T7 | — |

T1—T8 后端可由同一执行者顺序完成；T9 在 T6 后可并行；T10 等原型。

### 13.1 待主会话确认（2026-10-29 主会话审阅：O-1—O-4 均按本稿取舍；另补 `DATA_QUERY_OUTPUT_UNDECLARED` / `DATA_QUERY_OUTPUT_MASKED` 两条校验，对应原型 17 的列错误提示）

| 编号 | 问题 | 本稿取舍 |
|---|---|---|
| O-1 | 动作目录选择 data-query 模板需要列表；现有 `/data-query-admin/v1/templates` 要 `DATA_QUERY_READ` | 新增 `GET /workflow-admin/v1/data-query-templates`（`WORKFLOW_ACTION_TEMPLATE_MANAGE`，只回编码、名称、参数），与 S-10"不联动 data-query 权限"一致；代价是多 1 条路由 |
| O-2 | REQ-01 已交付的三条模板输出列为 snake_case（`out_agreement_id`），决定 3.3 写的是 camelCase（`agreementId`） | 不改 REQ-01 模板；输出名按列后缀声明（`agreement_id`），变量为 `n3.agreement_id`；后续 Owner 模板建议用 camelCase 别名 |
| O-3 | core `ExecuteDataQueryProcessor` 改为委托新入口会触及已上线的执行接口 | 委托，消除重复的模板可执行检查；以现有 HTTP 测试回归兜底 |
| O-4 | 块 142 路由在初始化文件中按惯例 DISABLED，mor-test 另执行激活脚本 | 按惯例 |
