# platform-support：workflow 按钮发起与数据验证 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-28 | 来源 | design-discuss/client-activation 讨论：用户确认流程由运营按钮发起、节点完成靠查 Owner 事实、M1 的 workflow 只补最小集；登记为 [2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md) REQ-05 | 已确认（讨论） |
| 2026-10-28 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R8、规则 B1—B7、逐系统变动点、验收 AC-01—AC-12、待确认 Q1—Q3 | 已交付 |
| 2026-10-29 | 1 核实 | REQ-01 完成后启动；按代码只读核对补充 requirement 第 9a 节 V-1—V-6：唯一性用 definition_id + business_key + 槽位、输出写入动作 output_json 复用 `<actionKey>.*` 变量、data-query 进程内调用、Platform 已有 XXL 执行器、单行返回约定与 `GOLIVE_*` 编码、阶段 3 须出 Pencil 原型 | 已交付 |
| 2026-10-29 | 1 确认 | 用户"按建议"：Q1 默认 5 分钟重查、单次超时 5 秒，可配置；Q2 任务查看者即可"立即检查"；Q3 保留 definitionId + versionNo 发起，与 definitionCode 二选一；接受第 9a 节范围修正。默认八阶段，进入阶段 2 | 已确认 |
| 2026-10-29 | 2 差异与影响 | [technique.md](./technique.md) 评审稿（只读核对，含本机与 mor-test 只读 SELECT）：F-01—F-19、需求项差异、逐系统影响、待决 S-01—S-18、规模 M（偏上限）。关键：存量 RUNNING 无重复 business_key；data-query 执行入口在 `-core` 且依赖会话与白名单，需 data-query 模块新增公开执行 Processor（F-09）；动作在写事务内激活，首查用补偿任务（F-08）；输出须按声明类型规整，否则网关求值失败卡住实例（F-06）；检查状态存动作运行表新列（F-11）；补偿目录缺两类存量（F-13）；代码无 WAITING 节点（F-17） | 已交付 |
| 2026-10-29 | 2 确认 | 用户：S-10 不做数据查询权限联动（建 / 改 DATA_QUERY 动作模板只需动作模板管理权限）；其余 S-01—S-18 按建议（含 S-03 改 AC-03 为 400 `BUSINESS_CHECK_FAILED`）。进入阶段 3 | 已确认 |
| 2026-10-29 | 3 方案 | [technique.md](./technique.md) 第 7—13 节评审稿：方案总览、接口 8.1—8.8（发起二选一与 `created`、立即检查、检查块、DATA_QUERY 配置与校验、模板选择、data-query 公开执行入口、BFF、注册块 142）、DDL（实例 `active_slot` + 唯一索引 + `utf8mb4_bin`，动作运行 5 列 + 扫描索引）与回滚、配置与 XXL `workflowDataQueryRecheck`、文案键、测试与验证计划、实施切分 T1—T11；主会话补输出列未声明 / 被屏蔽两条校验。Pencil 原型 `operations-console.pen` 工作流 V2 · 17—20（导出见 [prototypes/](./prototypes/)）；requirement 同步 B2 / AC-03、R1、B8、B9 | 已交付 |
| 2026-10-29 | 3 确认 | 用户"按建议，进入阶段 4"：方案第 7—13 节、O-1—O-4 取舍、两条补充校验与原型 17—20 确认；阶段 4 含本机 auto 库执行前滚 SQL。实施工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-wfdq`（基于 8d24df8a） | 已确认 |
| 2026-10-29 | 4 实施与自测 | 工作树 `Code-MoR-wfdq`（基于 8d24df8a）：后端 / BFF（T1—T9、T11）与前端（T10）实现，见下文"阶段 4 实施记录"；Platform 测试模块 971 例（含 26 个类因缺本机 MinIO 公钥证书先失败、补拷公钥后 187/187）、ops-bff 2450 例、ops-web 1596 例全部通过；本机 `mor_platform` 前滚 DDL；偏差 D-1—D-9 | 已完成 |
| 2026-10-29 | 4 确认 / 5 授权 | 用户"按建议，进入阶段 5"：接受阶段 4 结果与偏差（D-1—D-9、前端三处简化）；授权本机联调：`mor_platform_v2e2e` 执行前滚 DDL 与注册块 142（含启用）、本机网关实际编译、替换本机 e2e Platform / ops-bff JAR（备份 *.pre-wfdq）、wfdq 工作树 ops-web 接替 18311、本机 GOLIVE_* 演示模板与流程、浏览器走查 zh-CN / en × 1440 / 390；不含 mor-test、提交、推送 | 已确认 |
| 2026-10-29 | 5 本机联调（后端 / API） | [verification/stage5-report.md](./verification/stage5-report.md)：`mor_platform_v2e2e` 前滚 DDL 与块 142（插入 → 激活，Gateway 快照真实编译通过 510 → 513）；Platform / ops-bff 换 worktree JAR（pid 92711 / 92955，保持运行）；本机启用 data-query 后台并新建只读账号登记 GOLIVE_* 模板；API 联调 38 / 38 通过；本机无 XXL，定时重查由 auto 测试覆盖 | 已交付 |
| 2026-10-29 | 5 本机联调（浏览器） | [verification/stage5-report.md](./verification/stage5-report.md) 第 8 节：18311 改由 wfdq worktree 的 Vite 提供（gate 的 pid 3811 已停，恢复命令已记录；新 pid 97915）；本机语言包 #12、#13（workflow 命名空间，zh-CN 译文）；Playwright zh-CN / en × 1440 / 390 走查原型 17—20 与 REQ-01 合同详情回归，60 张截图，未翻译 key 0、390 横向滚动 0，HTTP ≥ 400 只有 REQ-01 既有的 dimensions 404；走查发现并修复 6 项（输出表溢出、窄屏编辑页、无位置定义的实例图叠在一处、抽屉检查行挤压、390 编辑器属性面板、步骤名），ops-web `npm test` 1596 / tsc / build / i18n 均通过；写操作：立即检查 5 次、为拍"检查中"发起 13 个实例 | 已交付（待确认） |
| 2026-10-29 | 5 确认 / 6 启动 | 用户"继续"：接受阶段 5 结果与剩余原型差异；终止 13 个"检查中"走查实例；进入阶段 6（mor-test 发布准备：只读核对基线 + 本机构建镜像、SQL 与网关离线编译演练、XXL / 语言包 / data-query 模板与验收流程、compose 候选与回滚、runbook、验收脚本；不停 mor-test 容器、不执行 mor-test SQL） | 已确认 |
| 2026-10-29 | 6 mor-test 发布准备 | [release/stage6-report.md](./release/stage6-report.md)：只读基线（现网无未提交内容，`8d24df8a` 干净构建与现网 ops-web 253 文件 / ops-bff 2815 项逐字节相同，card 已是 refclient 形态不发布）；候选 `wfdq-20261029a`（Platform `76717246`、ops-bff `6833f484`、ops-web `96992aba`，JAR 与阶段 5 联调 JAR 0 差异）；compose 6 个文件 env 与现网逐键逐值相同；前滚 / 回滚 DDL 与块 142 在 mor-test 只读导出上离线演练，真实编译 616 → 619 → 616 全部通过；XXL 分组 2 登记脚本；语言包 #21 只读计划（workflow 新增 123 / 变更 4 / 废弃 13）；data-query 复用 MOR_COMMERCIAL_REPLICA，登记与验收脚本本机演练通过；待裁决 D-1—D-4 | 已交付 |
| 2026-10-29 | 6 确认 / 7 启动 | 用户"继续"：接受阶段 6；D-1 接受废弃 13 个旧 workflow key、D-2 验收编码沿用 REQ05_* / ACC_REQ05_*、D-3 保留 3 个 RUNNING 验收实例作演示；进入阶段 7（mor-test 按 release/runbook.md 执行，主会话执行） | 已确认 |
| 2026-10-29 | 7 mor-test 发布 | 主会话按 release/runbook.md 执行（09:37—09:55Z）：备份 `Code-MoR-backups/wfdq-20261029/mortest-20261029T0937*`；预检同基线；前滚 DDL（3 槽位）；Platform `76717246`、ops-bff `6833f484`、ops-web `96992aba`（wfdq-20261029a）均 healthy 无 ERROR；注册块 142 休眠 616 → 激活 619；XXL 任务 27（每 5 分钟，09:50 / 09:55 自动执行 200）；ops 语言包 #21（发布请求 504，Platform 已完成，READY 且为当前）；GOLIVE_* 3 模板、4 动作模板、`ACC_REQ05_GOLIVE_COMMERCIAL` 发布。验收 setup 10/10、check 4/4、scenario 14/14、recheck 3/3（XXL 09:50 / 09:55 两轮 200）；浏览器走查 zh-CN / en × 1440 / 390 共 62 张通过（[release/stage7-report-browser.md](./release/stage7-report-browser.md)），mor-test 写入仅 1 次立即检查；接口 ≥400 与控制台错误 0；已通知 code-mor-2d、design-discuss-24 | 已完成 |
| 2026-10-29 | 7 确认 / 8 授权 | 用户"按建议，进入阶段 8，提交"：授权提交（私有索引，不推送）；保留 3 个 RUNNING 验收实例（D-3） | 已确认 |
| 2026-10-29 | 8 提交 | 工作树改动合入主树并提交（代码、SQL、注册块 142、验证与发布材料、本记录、CURRENT_IMPLEMENTATION）；未推送 | 已完成 |

## 适用阶段

默认八阶段（2026-10-29 用户确认）。SQL、mor-test 部署、提交、推送分别授权。

## 当前阶段与下一确认点

阶段 1—8 已完成（2026-10-29）。mor-test 自 2026-10-29 09:42Z 运行 `wfdq-20261029a`（Platform `76717246`、ops-bff `6833f484`、ops-web `96992aba`），注册块 142、XXL 任务 27、ops 语言包 #21；保留 3 个 RUNNING 验收实例作演示。推送另行授权。本机 e2e 保留新 Platform / ops-bff / card JAR（`*.pre-wfdq` 可回滚，card 与 Platform 须一起回滚）与 18311 wfdq Vite。范围外发现：活动记录"已完成 drafted / active / collection"显示动作结果原值未翻译（小问题，后续顺手改）；对账渠道明细页 en 390 横向滚动 27px（既有）。

## 阶段 4 实施记录（前端，T10 operations-console-web，2026-10-29）

工作树 `Code-MoR-wfdq/operations-console-web`；未提交、未部署；`node_modules` 为指向主工作树的只读符号链接（未跟踪，可删除）。

| 范围 | 实现 |
|---|---|
| 类型与接口 | `workflowTypes.ts`：`DataQueryConfig` 改为 8.4 形状（`queryCode / parameters: string[] / outputs[{name,type,enumValues}] / descriptionKey / instruction`）、`DataQueryCheck`、`DataQueryCheckResponse`、`DataQueryTemplateOption`、`ActionRun.dataQueryCheck`、`Task.actionType`、`Mutation.created`、`StartInstanceRequest.definitionCode`（与 ID + 版本二选一）。`workflowApi.ts`：路径 `dataQueryTemplates`、`dataQueryCheck`，解码 `decodeDataQueryCheck / decodeDataQueryCheckResponse / decodeDataQueryTemplate`，实例详情解码检查块 |
| 动作目录（原型 17） | 数据查询可新建（`creatableTypes` 五类，页签常驻）；`TemplateEditors.tsx` 新 `DataQueryEditor`：模板选择（`GET …/data-query-templates`，关键字远程搜索、数据源显示、模板不可用提示）、只读参数表（必填 / 选填，取值在节点映射；声明变化时可「按当前声明更新」）、输出表（名称 / 来源列 out_<名称> / 类型 / 可选值 / 流程变量 <动作键>.<名称>，增删）、完成标准文案键 + 当前语言预览 + 说明；右栏「查询返回约定 / 执行方式」。`templateModel.ts` 与后端同口径的本地校验与保存整理（只写 5 个允许键，旧 `passRule / evidenceColumns` 与对象参数被整理掉）；保存前校验的问题按问题码与 itemKey 标到行（见下方跟进实现） |
| 流程编辑器（原型 18） | 数据查询可加入节点；参数映射复用输入参数映射（默认展开）；输出变量列表；执行人显示为「跟进人」；系统检查说明行；节点提示框补数据查询两条。`graphModel.ts`：删除 `TEMPLATE_RESERVED_TYPE` 本地问题，`actionOutputs` 产出 DATA_QUERY 输出变量（ENUM 带可选值），因而进入连线条件变量选择；列表中无配置的数据查询与表单同样跳过变量可用性检查 |
| 任务卡与实例详情（原型 19、20） | 新 `DataQueryCheck.tsx`：`DataQueryRow`（无「完成」；状态标签 已满足 / 尚未满足 / 检查失败 / 检查中；完成标准；missing 列表 `workflow.dataQuery.missing.<CODE>` 缺译文显示原码；失败码文案、连续失败次数、上次成功时间；「立即检查」发 `{}`，加载态，`inProgress` / 仍未满足 / 失败 / 409 / 400 就地反馈后刷新，满足自动完成时提示并刷新）。我的待办与实例节点抽屉共用；全为检查的节点标题「我跟进的检查」、底部提示改为自动推进。`completionMode` 对 DATA_QUERY 恒为 LOCKED。活动记录：无操作人的提交显示「系统」，新增「系统完成」筛选与右栏「系统检查」计数；提交内容表与前序产出的提交人同样显示「系统」 |
| 发起 | 请求类型支持 `definitionCode`；`LaunchModal` 对 `created=false` 提示「已有运行中实例，已为你打开」（不新增发起按钮） |
| 文案 | en / zh-CN 同步：按 technique 第 11 节新增（含 5 条 missing、3 条 `GOLIVE_*` criteria、7 条 `DATA_QUERY_*` 问题码）并删除所列旧键；另按原型补充编辑器、右栏、任务卡与活动记录用的键（`dataQuery.contract.* / check.followTitle 等 / outputTypeName.* / actions.follower 等 / config.*Pattern 等 / activity.filter.SYSTEM / activity.systemNote / launch.reused`）；`dataQuery.parameters / parameter / queryCode` 改为新含义的措辞 |

验证（工作树内）：`npm test` 1594 通过 0 失败（原 1581；更新 `catalogModel.test.ts`、`graphModel.test.ts`，新增 `templateModel.test.ts` 5 例、`dataQueryModel.test.ts` 7 例，含 8.2 示例响应形状的解码与任务卡状态 / 反馈）；`tsc --noEmit` 通过；`npm run build` 通过（仅既有的大包体告警）；`npm run i18n:export-source` 通过（en / zh-CN 键集一致，workflow 1095 键）。未启动开发服务器，浏览器走查留待阶段 5。

与原型的差异：原型 18 的流程级「1 个问题」与卡片红字仍来自保存后的服务端问题（本地无法判断必填参数与模板状态）；原型 19 卡片头部的「由立即检查触发 · 开始时间」不在检查块中，未显示；原型 20 活动筛选「业务回调」沿用既有「回调」，右栏「本节点的检查（已结束）」未做（计数卡已做）。

合同决定（主会话 2026-10-29）：① 流程编辑器的问题继续显示服务端 `message`，`graph.problem.DATA_QUERY_*` 键保留不引用；② 动作模板保存改为先调用新接口 `POST /workflow-admin/v1/action-templates/validation`（只校验不保存，返回 `{problems:[{code,nodeKey,edgeKey,itemKey,message}]}`），按问题码 + `itemKey` 定位，删除按文本匹配 `out_<名称>` 的做法；③ 模板选择项增加 `versionNo`、`timeoutSeconds`。

跟进实现（同日）：`workflowApi.ts` 新增 `templateValidation` 路径与 `decodeTemplateValidation`，模板选择项解码 `versionNo / timeoutSeconds`（旧响应为 null）。`templateModel.ts` 以 `groupDataQueryProblems` 取代文本匹配：`DATA_QUERY_TEMPLATE_*` → 模板选择框标红并列出；`DATA_QUERY_PARAMETER_*` 与指向参数的 `ACTION_CONFIG_INVALID` → 参数行（不在声明表中的参数列在表下）；`DATA_QUERY_OUTPUT_*` 与指向输出的 `ACTION_CONFIG_INVALID` → 输出行（未声明 / 已屏蔽用本地文案，其余用服务端说明）；约定列 `satisfied / missing` 与无 `itemKey` 的问题 → 编辑区顶部横幅。`ActionTemplateEditor` 保存数据查询时先校验，有问题则跳到「查询与输出」、标红并不保存，无问题再新建 / 修改；有待清除的问题时，修改配置后 600ms 防抖重新校验以清除已修正的行。模板选择项显示「生效中 · v{n}」，已选模板下显示单次超时 min(模板, 5) 秒，模板值更大时注明「模板 N 秒，取小值」。新增键 `dataQuery.activeVersion / timeout / timeoutCapped / validationFailed`（en / zh-CN）。验证：`npm test` 1596 通过 0 失败（`templateModel.test.ts` 改为定位分组与超时两例，`dataQueryModel.test.ts` 新增校验响应解码与模板项新字段）；`tsc` 通过；`build` 通过；`i18n:export-source` 通过。原型 17 的版本与超时差异已消除。

## 阶段 4 实施记录（后端）

执行者：wfdq-backend（2026-10-29），工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-wfdq`（基于 8d24df8a，未提交）。范围 T1—T9 与 T11 后端文档；前端（T10）由 wfdq-web 并行实施。

### 已读规范

`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`（3.4 / 5 节）、`examples/WRITE_API_EXAMPLE.md`、`UNIT_TEST_GUIDE.md`、`examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`（5 / 11 / 13 节）、`COMPENSATION_EVENT_GUIDE.md`（2 / 4 / 6 / 8 节）。

### 改动（按任务）

| 任务 | 文件 / 内容 |
|---|---|
| T1 数据模型 | `paytech/platform-support/docs/database/platform-support.pdma.json`（直接编辑：`business_key` utf8mb4_bin 与注释、`active_slot`、`uk_workflow_inst_active`；动作运行 5 列与 `idx_workflow_action_check`；两处 `action_type` 注释）；`docs/database/sql/platform-support-schema.sql` 与测试 `init/ddl.sql` 同步；前滚 `docs/database/sql/changes/2026-10-29-workflow-data-query.sql`（含预检、information_schema 可重跑）、回滚 `…-rollback.sql`；需求 `db/ddl.sql` 索引 |
| T2 data-query | `DataQueryRun.operatorAccountId` 改 `Long`、新增 `timeoutCapSeconds`；`DataQueryRunner` 超时取 min(模板, 系统, 调用方)、日志空操作人为 `-`；新增 `service/processor/RunDataQueryTemplateProcessor` + `RunDataQueryTemplateRequest`；`QueryTemplateSignature / QueryTemplateParameter / QueryTemplateColumn` 与 `QueryTemplateService.findTemplateSignature`；`QueryTemplateSummary.timeoutSeconds`；core `ExecuteDataQueryProcessor` 鉴权后委托 |
| T3 配置与发布 | `DataQueryActionConfig`（新形状）/ `DataQueryOutput`；`ActionConfigJsonReader`（旧键 422）；`ActionConfigValidator` DATA_QUERY 规则；`DataQueryTemplateRules`（6 个事实问题码）；`DataQueryTemplateFact`；`WorkflowPublicationInput.dataQueryTemplates`；`TaskNodeRules`（去 `TEMPLATE_RESERVED_TYPE`，加 `DATA_QUERY_INPUT_MISSING`）；`WorkflowTemplateConfigs`（事实读取、`problems()`）；`WorkflowDraftChecker`；`ActionOutputChecker`（B3 文案）；workflow pom 增加 data-query / micrometer / slf4j |
| T4 发起 | `StartWorkflowInstanceRequestBody.definitionCode`（末位）、ID / 版本改可空；`WorkflowConverter.start` 二选一（400 INVALID_REQUEST）；`StartInstanceRequest`、`StartWorkflowInstanceProcessor`（事务内同键返回、`DuplicateKeyException` 仅限 `uk_workflow_inst_active` 时事务外回读，否则 409）；`WorkflowMutation / WorkflowMutationResponseBody.created`；`WorkflowInstance.activeSlot`、Mapper（insert、update 离开 RUNNING 清槽位、`selectRunningByBusinessKey`）、Repository |
| T5 检查运行 | 枚举 `DataQueryCheckResult / DataQueryCheckFailure / DataQueryCheckTrigger`（TypeHandler 登记 CheckResult）；`DataQueryParameterResolver`、`DataQueryResultReader`、`DataQueryCheckOutcome`；引擎 `completeDataQuery / recordDataQueryCheck / dataQueryCheckable`（`advanceAfterActions` 触发人可空）；`CheckWorkflowDataQueryProcessor`（检查锁 TRY_LOCK 0）、`ApplyWorkflowDataQueryCheckProcessor`（实例锁）、`RecheckWorkflowDataQueryProcessor`、`CheckWorkflowDataQueryNowProcessor`；`WorkflowDataQueryCheckHandler`（`WORKFLOW_DATA_QUERY_CHECK`）；`WorkflowProcessorSupport.registerCallbackEvents` 为新建 DATA_QUERY 动作运行登记首查；`WorkflowActionRun` 5 字段、Mapper（columns / insert / update / `selectDueDataQueryChecks`）、Repository；`WorkflowRuntimeService.recordDataQueryCheck`；`WorkflowProperties.DataQuery`（`workflow.data-query.*`） |
| T6 接口与 app | `WorkflowInstanceApi.checkDataQuery`；`WorkflowDataQueryTemplateApi` + `WorkflowDataQueryTemplateBody`（含 `versionNo`、`timeoutSeconds`）；`WorkflowActionTemplateManagementApi.validateActionTemplate` + `ValidateWorkflowActionTemplateRequestBody` / `WorkflowActionTemplateValidationResponseBody`（阶段 4 增补，见偏差 D-1）；`WorkflowDataQueryCheckBody / WorkflowDataQueryCheckResponseBody`；`ActionRunBody.dataQueryCheck`、`WorkflowTaskBody.actionType`（Task 视图 / Mapper）；app Controller（立即检查带 `@Audit OPERATIONS_WORKFLOW_ACTION_CHECK`、模板选择、配置校验）、`ext/job/WorkflowDataQueryRecheckJobHandler`（`workflowDataQueryRecheck`）、`application.yml`、`compensation-event-catalog.yaml`（补登 WORKFLOW_NOTIFICATION、WORKFLOW_CALLBACK_EVENT，新增 WORKFLOW_DATA_QUERY_CHECK） |
| T7 测试 | workflow 模块 `WorkflowDataQueryDomainTest`（新）、`WorkflowTaskValidationTest`、`WorkflowJsonReaderTest`、Fixture / Builder；test 模块新增 `WorkflowStartByCodeApiTest`、`WorkflowDataQueryTemplateApiTest`、`WorkflowDataQueryCheckApiTest`、`WorkflowDataQueryCheckNowApiTest`、`auto/ext/eventhandler/workflow/WorkflowDataQueryCheckHandlerTest`、`auto/ext/job/WorkflowDataQueryRecheckJobHandlerTest`、`auto/dataquery/RunDataQueryTemplateProcessorTest`、`test/base/WorkflowDataQueryTestBase`；调整 `WorkflowApiTestBase`（业务关联键逐次不同）、`WorkflowCallbackHandlerTest`、`WorkflowInstanceApiTest`、`WorkflowActionTemplateApiTest`、`WorkflowGraphJson`、`WorkflowTestMapper`、`DataQueryTestMapper`、`application-mock.yml`。被查库沿用 `mor_data_query_fixture` 现有三表，未扩展 |
| T8 注册块 142 | `platform-support-initial-data.sql` 末尾追加块 142（DISABLED）；需求 `db/registration-142.sql`、`registration-142-activate.sql`、`registration-142-rollback.sql`。3 条路由：142401 立即检查（TASK_HANDLE，body 1024）、142402 模板选择（ACTION_TEMPLATE_MANAGE，Query keyword/page/size）、142403 配置校验（ACTION_TEMPLATE_MANAGE，POST，body 65536，`idempotency_mode=NONE`，头部同 127403：authorization / origin / content-type×2 / idempotency-key×2，主体限流 30/min）；共 route 3、header 14、query 3、guard 18 = 38 行 |
| T9 ops-bff | `WorkflowModels.StartWorkflowInstanceRequestBody`（`definitionCode`，ID / 版本可空）、`ValidateWorkflowActionTemplateRequestBody`；`WorkflowInstanceController` 立即检查（转发时固定发 `{}`，核对返回 actionRunId）、`WorkflowActionTemplateController` 配置校验、新 `WorkflowDataQueryTemplateController`；`PlatformWorkflowClient(Impl)`；`PlatformClientConfiguration`（`WorkflowDataQueryTemplateApi` 代理；`WorkflowInstanceApi` 读超时 5s → 9s，见偏差 D-4）；测试 `workflow-boundary.json` WF33—WF36、`WorkflowBrowserAutoTest` 上游无效响应用例、`OperationsConsoleBffApplicationAutoTest` 路由清单 +3 |
| T11 文档 | `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`（工作流表说明）、`design-discuss/workflow/design.md`（DATA_QUERY 由预留改为可用）；本需求 `technique.md` 工作树副本 8.4 / 8.5 / 8.8 增补。未改 `CURRENT_IMPLEMENTATION.md` |

### 测试（全部串行，私有仓库 `-Dmaven.repo.local=/Users/kenny/.m3-wfdq/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_`，JDK 25，`-o`）

| 命令 | 结果 |
|---|---|
| `./mvnw -pl platform-support/paytech-platform-support-test -am test -Dtest='com/paytech/platform/support/**/auto/**/*Test*' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false` | data-query 模块 48/48、workflow 模块 71/71、localization 36/36；test 模块 832 项中 48 项失败（18 failures + 30 errors），**全部是同一环境原因**：工作树缺 git 忽略的本机证书 `local-environment/minio/certs/public.crt`（26 个文件 / 存储 / 多语言 / 参考数据类，类级前置检查失败） |
| 补齐证书后（只从主工作树复制公钥证书，未复制私钥）重跑上述 26 个类 | 187/187 通过 |
| 合计 Platform test 模块 | 784 + 187 = 971 项全部通过；含新增 REQ-05 类：StartByCode 5、DataQueryTemplate 4、DataQueryCheck 4、DataQueryCheckNow 5、CheckHandler 3、RecheckJob 3、RunDataQueryTemplateProcessor 3；回归 DataQueryExecutionApiTest 9、WorkflowInstanceApiTest 12、ArchitectureAutoTest 1 |
| `./mvnw -pl operations-console-bff/paytech-operations-console-bff-test -am test -Dtest='com/paytech/operations/console/bff/**/auto/**/*Test*' …` | 2450 项中 1 项失败：`OperationsConsoleBffApplicationAutoTest.test_applicationReusesFoundationAndExposesExactOperationsRoutes`（路由清单未登记 3 条新 BFF 路由）；补登后该类 8/8 通过，`WorkflowBrowserAutoTest` 86/86（WF33—WF36 前） / 全量运行时含 WF36 通过 |

未运行：全 reactor；mor-test 与 `mor_platform_v2e2e` 相关的一切（阶段 5/6）。

### 本机数据库变更

只改 `mor-local-mysql-1` 的 `mor_platform`（本机 auto 测试库）：先执行预检（0 行，实例 0 条），再以 root 执行 `changes/2026-10-29-workflow-data-query.sql` 两次（第二次全部 skipped，验证可重跑），`SHOW CREATE TABLE` 核对列、注释与索引一致。未改其他库；`mor_data_query_fixture` 未改。

### Gateway 快照离线编译

仓库内没有“把注册块加载到库后运行编译器”的现成工具（编译器为 `paytech-platform-support-access-control/.../GatewayConfigurationCompiler`，按活库配置运行）。已按其上限做静态核对：守卫 ≤ 600/min、并发 ≤ 50、每路由守卫 6 ≤ 8、body ≤ 65536、接收 / 响应超时 5000 / 10000、头部用途与 Query 名合法，形状逐条复制同类既有路由（127420 / 127416 / 127403）。**真实离线编译（活库备份 + 块 142 + 编译器）留待阶段 5/6 发布演练执行。**

### 与 technique.md 的偏差

| 编号 | 偏差 | 原因 |
|---|---|---|
| D-1 | 新增 `POST /workflow-admin/v1/action-templates/validation`（问题列表 200）与路由 142403；新建 / 修改的 400 仍只带合并中文 detail | Foundation `ApiProblemResponse` 为固定字段，400 无法携带问题列表；主会话 2026-10-29 选定方案 1 |
| D-2 | 模板选择项增加 `versionNo`、`timeoutSeconds`（`QueryTemplateSummary` 增 `timeoutSeconds`） | 主会话 2026-10-29 增补 |
| D-3 | `RunDataQueryTemplateRequest` 另含 page / size / withTotal / cursor | core `ExecuteDataQueryProcessor` 委托后 HTTP 分页行为保持不变 |
| D-4 | 立即检查（MANUAL）另设 `CheckWorkflowDataQueryNowProcessor` 负责可见性、检查锁被占用返回 `inProgress`、实例锁冲突转 409；`CheckWorkflowDataQueryProcessor` 内把实例锁 / 版本冲突转为 `busy` 结果而非异常；BFF `WorkflowInstanceApi` 读超时 9s | 锁模板在取锁失败时抛出，需在锁外分辨“检查进行中”与“实例忙”；查询 5s + 落库超过 BFF 原 5s 读超时 |
| D-5 | 查询前先读模板签名判 `TEMPLATE_UNAVAILABLE`；其余 BizCheck 记 `QUERY_FAILED` | data-query 的“未启用”与 SQL 错误同为 BizCheckFailException，无法从异常区分 |
| D-6 | 定时重查摘要另含 `queryFailed`；`failed` 只计处理异常（有则 XXL 记失败） | 查询失败属正常检查结果，不应让 XXL 每轮失败 |
| D-7 | `WorkflowApiTestBase.start` 业务关联键改为逐次不同 | 防重上线后同键第二次发起返回已有实例，原用例依赖多次发起 |
| D-8 | Mock `recheck-min-age: 0s`，用例把 `last_check_time` 移到过去构造到期 | 测试时钟固定为 `CredentialFixture.NOW` |
| D-9 | arch-rules `ArchitectureAutoTest` 的模块清单未含 workflow 模块（存量），R12 仍只扫 -core；本次未扩大 | 技术方案 S-17 已定不改 R12 覆盖范围 |

### TODO by CLAUDE

无。

### 遗留 / 下一步

- 阶段 5：`mor_platform_v2e2e` 执行前滚 SQL 与块 142（含 142403）+ 真实离线编译；e2e 库旧形状 DATA_QUERY 模板先停用。
- 工作树另含从主树复制的、被 git 忽略的 `local-environment/minio/certs/public.crt`（仅供本机测试，不会进入提交）。
