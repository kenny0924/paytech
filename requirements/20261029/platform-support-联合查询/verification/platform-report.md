# 联合查询 · Platform 实现报告（jq-platform，2026-10-29）

Worktree `/Users/kenny/Desktop/Workspace/Code-MoR-jq`（detached e3e0c12f），未提交、未推送。api 模块公共合同**未改**。

## 已读规范与本次落位（WRITE_API_EXAMPLE §1）
已读：technique.md §1–§13、requirement.md、`.jq-notes/contract.md`、DEVELOPMENT_CONVENTIONS、JAVA_PROJECT_STRUCTURE、
WRITE_API_EXAMPLE、UNIT_TEST_GUIDE + UNIT_TEST_EXAMPLE、DATABASE_DESIGN_GUIDE §5.1/§11。
- 键类型写入（K2–K5）：core 模块 `*QueryKeyType*Processor`（AbstractLockProcessor）→ data-query `QueryKeyTypeService`；
  本地原子写入 = 一个 OperationsTransactionExecutor.write 事务内：复核权限 → 排他锁读键类型 → 引用检查（共享锁读 ACTIVE 模板与当前版本）
  → CAS 更新 → 审计登记。没有事务外步骤。
- 模板保存（既有 Create/Update/Restore/Status）：事务前校验（含 joint，键类型无锁读取）；事务内对数据源与引用键类型 `FOR SHARE` 重证。
- 联合查询（J1–J3）：core Processor 做调用方白名单 + DATA_QUERY_EXECUTE，委托 data-query `JointQueryGraphLoader`（J1/J2）
  或模块入口 `RunJointQueryStepProcessor`（J3）；不开事务、不写库、不登记审计；被查库访问全部在执行器线程（无事务）。

## 改动文件
**data-query 模块**（`paytech-platform-support-data-query`）
- 新：enums `QueryKeyValueType` `QueryKeyRole` `QueryKeyTypeStatus` `JointQueryExclusionReason` `JointQueryNodeStatus` `JointQueryTruncationRule`；
  DO `QueryKeyType`；`QueryKeyTypeMapper`(+XML)；`QueryKeyTypeRepository(+Impl)`；`QueryKeyTypeService(+Impl)`；
  `JointQueryGraphLoader(+Impl)`；validator `JointQueryStepValidator`；engine `JointQueryExecutor`（Bean `dataQueryJointExecutor`）、
  `JointQueryStepRunner`；processor `RunJointQueryStepProcessor` + request；config `DataQueryJointProperties`；
  domain `QueryJointConfig/KeyParameter/Column`、`QueryKeyType{Detail,Definition,Change,Reorder,PatternRules}`、`QueryKeyValues`、
  `JointQuery{Participation,Graph,Preview,Template,ExcludedTemplate,Group,Settings,Key,Step,StepResult}`、
  `Create/UpdateQueryKeyTypeCommand`、`ChangeQueryKeyTypeStatusCommand`、`ReorderQueryKeyTypesCommand`。
- 改：`QueryTemplateDraft`(+joint)、`QueryTemplateVersion` DO(+jointJson)、`QueryTemplateVersionDetail`(+jointParticipation)、
  `QueryTemplateJson`(read/writeJoint)、`DataQueryDetailConverter`(version 带模板状态与参与评估)、`QueryTemplateServiceImpl`
  （写 joint_json；保存 / 启用时 `FOR SHARE` 引用键类型）、`QueryTemplateDraftValidator`（joint 校验，签名加 keyTypes）、
  `QueryTemplateRepository`/`QueryTemplateVersionRepository`(+findAll / ForShare)、两份 Mapper XML、
  `RunDataQueryTemplateRequest` / `DataQueryRun` / `DataQueryRunner`（`rowCap`）、`RunDataQueryTemplateProcessor`。

**core**：新 Processor `List/Create/Update/ChangeQueryKeyTypeStatus/ReorderQueryKeyTypes`、`GetJointQuerySettings`、`PreviewJointQueryGraph`、
`ExecuteJointQueryStep` 及 8 个 request；改 `DataQueryManagementSupport`（锁 Key、referencedKeyTypes、withParticipation、
requireValidPattern）、`DataQueryAuditFacts`（keyType / keyTypeOrder；模板 definitionDigest 含 joint）、Create/Update/Restore/Validate/Trial
模板 Processor、`ExecuteDataQueryProcessor`（rowCap 位置参数）。

**app**：新 `QueryKeyTypeManagementController`、`JointQueryController`、`JointQueryConverter`；改 `DataQueryConverter`（joint ↔ DTO、
jointQuery）、`QueryTemplateManagementController`（透传 joint；试运行传 null）、`PersistenceConfiguration`（3 个 TypeHandler）、
`application.yml`（`data-query.joint.*` 默认值）。

**tools/arch-rules**：`table-ownership.yaml` 登记 `t_query_key_type`；`TableOwnershipAutoTest` 表数 +1（396 / platform 75）。

**workflow**：`CheckWorkflowDataQueryProcessor` 只在 `RunDataQueryTemplateRequest` 构造末尾补 `null`（rowCap）。

**DB 工件**（`paytech/platform-support/docs/database/`）
- `platform-support.pdma.json`：直接编辑，新增实体 `t_query_key_type`（17 列、uk/idx）、`t_query_template_version.joint_json`、图组 data_query 加表。
- `sql/platform-support-schema.sql`、测试 `init/ddl.sql`：同上 DDL。
- `sql/changes/2026-10-29-joint-query.sql`（可重跑）+ `-rollback.sql`。
- `sql/platform-support-initial-data.sql` 末尾块 144；同文 `changes/2026-10-29-joint-query-registration-144.sql`，
  另 `-activate.sql`、`-rollback.sql`。
- `local-environment/mysql/manual/2026-10-29-joint-query-fixture.sql`：被查库 7 张 `t_jq_*` 链路表与固定数据（root 人工执行，幂等）。

**测试**（`paytech-platform-support-test`）：新 `QueryKeyTypeManagementApiTest`、`QueryTemplateJointConfigApiTest`、`JointQueryApiTest`、
`JointQueryStepGuardrailApiTest`、`JointQueryApiTestBase`、`JointQueryMocker`；改 `DataQueryApiTestBase`、`DataQueryTestMapper`、
`DataQueryFixture`(assertJointReady)、`PlatformDataCleaner`(+t_query_key_type)、`application-mock.yml`(joint)、
`RunDataQueryTemplateProcessorTest`(rowCap 位置参数)。

## 本地执行的 SQL（只在 mor-local-mysql-1）
1. `mor_platform`：`changes/2026-10-29-joint-query.sql`（新表 + joint_json 列，SHOW CREATE TABLE 已核对字段、注释、索引）。注册块与初始化 DML **未执行**。
2. `mor_data_query_fixture`：`local-environment/mysql/manual/2026-10-29-joint-query-fixture.sql`（7 表，账号授权不变）。

## 注册块 144（DISABLED）
26 键类型（1441xx，ACTIVE）、菜单 2（144301 联合查询 /data-query/joint-query sort 5 EXECUTE 131104；144302 键类型
/data-query/key-types sort 30 READ 131101；父 131301）、路由 8（144401–144408：K1 READ 131101，K2–K5 TEMPLATE_MANAGE 131103，
J1–J3 EXECUTE 131104；超时 10000ms；J3 body 65536）、route_header 36、query_param 5（K1 status/keyword/page/size，J2 entryKeyType）、
guard 48（读 30/min、写 principal 10/min、steps 60/min）。共 125 行。未在任何库执行；Gateway 快照未离线编译。

## 测试（全部 `package -pl platform-support/paytech-platform-support-test -am`，串行，本地 mor-local-mysql-1）
- data-query 模块 48/48、workflow 模块 5/5、platform-test 97/97 通过（最终一轮，含 Restore 重构之后）：
  新增 `QueryKeyTypeManagementApiTest` 16、`QueryTemplateJointConfigApiTest` 13、`JointQueryApiTest` 11、`JointQueryStepGuardrailApiTest` 4；
  回归 DataQueryExecution 9、DataSourceManagement 8、QueryTemplateManagement 13、RunDataQueryTemplateProcessor 3、
  WorkflowDataQuery* 19、ArchitectureAutoTest 1。
- `-f platform-support/pom.xml -DskipTests package`（整个 aggregate）成功；`tools/arch-rules` 70/70。
- PDManer / schema node 测试：28 通过 / 14 失败，与改动前基线完全相同（14 项为既有漂移：表清单、Manifest 等）；
  “列/默认值/完整注释与 PDManer 一致”“主键与索引一致”两项含新表通过。
- 没有遇到共享库干扰。

## AC 覆盖
AC-01 K1–K5（创建+审计、列表顺序/引用数/筛选、重复 400、正则子集 422×11、版本 409、404、改值类型被引用 400、停用被引用 400 列 5 个+“等 6 个”、
同状态不加版本无审计、重排 400/409/成功+审计）；AC-02 保存回显、INFO/WARNING 与原因码、9 种 joint.* 422、维度标链路提醒、恢复连同配置、
编辑器校验、键类型与模板保存/启用互斥；AC-03 设置、预览泳道/最少步数/断点/不参与、停用模板后变化、入口 422；
AC-04 退款单号 5 层展开 10 模板、边可追溯、支付尝试入口泳道一致；AC-05 维度不展开 + 维度入口；AC-06 页面循环去重后订单/退款不重复；
AC-07 LIST 合并（含 05001 规范化去重）与标量逐值 nodeId 后缀；AC-08 STEP_BUDGET、STEP_EXECUTION_LIMIT、MAX_VALUES_PER_KEY、行数上限截断；
AC-09 超时/只读拒绝/SQL 错误各自 FAILED 不影响其他节点；AC-10 屏蔽、403、非白名单调用方 400；AC-11 10 种 422；AC-12 CO-1 命中两 Client。

## WRITE_API_EXAMPLE §11 对照
- API/Body：合同由 jq-contract 写好，入口 `@Valid`；内部 Request（`*KeyTypeRequest`、`*JointQuery*Request`、`RunJointQueryStepRequest`）
  注解由 AbstractProcessor.validateBasic 触发。
- Controller/Converter 只适配：`QueryKeyTypeManagementController`、`JointQueryController`、`JointQueryConverter`（Code/ID 解析、DTO 组装）。
- Processor：K1–K5、J1–J3 位于 core `dataquery/service/processor`，主方法分阶段；只调 data-query 公开 Service / 模块 Processor，不碰 Mapper。
- Context：无单独 Context；命令对象 `Create/UpdateQueryKeyTypeCommand` 等携带领域内容，DataObject 只在 data-query 模块内。
- Service：`QueryKeyTypeServiceImpl`、`JointQueryGraphLoaderImpl` 只用本模块 Repository，不调其他 Service/Processor/外部依赖
  （GraphLoader 复用 `QueryKeyTypeServiceImpl.detail` 静态纯转换）。
- 事务：K2–K5 在 `support.write` 一个本地事务内（服务内 TransactionTemplate 加入）；事务内无外部调用；J1–J3 无事务，被查库访问在执行器线程。
- Repository：`QueryKeyTypeRepositoryImpl` 单表、CAS + 行数检查、成功后回写原对象；唯一键冲突转 400。
- 锁：K2 `lock:data-query:key-type-code:{code}`，K3/K4 `lock:data-query:key-type:{id}`，K5 `lock:data-query:key-type-order`（TRY_LOCK）；
  数据库层 `FOR UPDATE`（键类型）/`FOR SHARE`（模板保存、启用对键类型；停用检查对模板与当前版本）为最终保护。
- 原请求/防重：无业务操作身份，靠 expectedVersion；同状态启停返回原事实并 suppressReplay。
- Gateway 幂等：未新增任何幂等处理。
- View：未新增；图/预览是领域结果对象。
- 配置：`DataQueryJointProperties`（`@Validated` + 启动校验 step-budget ≤ max-timeout）；无生产 Mock。
- 测试：见上；未覆盖项见“未决”。

## 与 technique.md 的差异及原因
1. `truncated` = 结果 `hasMore`（引擎多取 1 行精确判断），不再以“行数达到上限”判定，避免恰好 100 行时误报。
2. 新增 SKIPPED 码 `EXECUTOR_BUSY`（共享执行器队列满）；技术方案说“满时 SKIPPED”但未给码。**web 需按未知码兜底或识别它。**
3. 新增排除原因 `KEY_TYPE_INACTIVE`（防御：引用键类型被外部改为停用时）；保存与启用已拒绝此类配置，正常不会出现。
4. 预览 `excluded` 也包含 INACTIVE 模板（TEMPLATE_INACTIVE），符合“不限于可达范围”。
5. 校验接口的参与提示：参与时 INFO、不参与时 WARNING（与保存一致）；contract 文字写“以 WARNING 提示”，若需参与也用 WARNING 请告知。
6. 预算结束后再等 500ms 收集已启动任务；仍未返回的取消并记 FAILED `DATA_QUERY_TIMEOUT`（“本步预算内未完成”）。
7. 加载器为 `JointQueryGraphLoader` 接口 + Impl（项目 Service 形态规则）；J3 经 data-query 模块入口 `RunJointQueryStepProcessor`（同 REQ-05 模式）。
8. `rowCap`：OFFSET/CURSOR 在未传 size 时由 DataQueryRunner 取 min(maxPageSize, 系统上限, rowCap)，等价于调用方传 page=1/size。
9. 执行器自持 `ThreadPoolExecutor`（Bean 名 `dataQueryJointExecutor`），不注册为 Spring TaskExecutor，避免顶掉 Boot 默认执行器。
10. 停用/改值类型的引用检查用 `FOR SHARE` 读模板与当前版本（快照读会漏掉并发保存）。
11. 键类型 26 行放在注册块 144 内（ID 1441xx，ACTIVE），与菜单/路由一起自包含；steps 路由限流 60/min（页面每层一次）。
12. 模板审计 definitionDigest 在有 joint 时纳入 joint（无 joint 时摘要不变）。
13. mock 配置单步预算 3s、并发 2、线程 4（正式 8s/8/16），使预算用例 ~3 秒且不压满本地 MySQL。

## 未决 / 提醒
- 既有展开校验拒绝裸 `IN (#{list})`（即使 LIST 必填），经 API 保存的联合查询模板须写 `{{#if ids}}…{{/if}}`；阶段 7 演示模板注意。
- AC-09 “数据源停用”：图构建时直接排除（DATA_SOURCE_INACTIVE），不产生 FAILED 节点；只有构图后被停用才 FAILED BUSINESS_CHECK_FAILED。
  连接失败（DATA_SOURCE_UNAVAILABLE）未做用例：Hikari connectionTimeout 3s 超过 mock 单步预算。
- 被查库链路表只有 manual SQL，没有新卷用的 `mysql/init` 脚本，`verify-local-environment.mjs` 未扩展。
- Gateway 快照未离线编译块 144；注册与初始化 DML 未在任何库执行。
- `TableOwnershipAutoTest` 表数与 yaml 是共享计数，若块 143 会话也加表，合并时需手工合计。
- 无 api 合同改动。

## 追加（2026-10-29 team-lead 三项调整）
- J2 入口键类型缺失 / 不存在 / 停用 / 不可作入口 → 400 BUSINESS_CHECK_FAILED（`PreviewJointQueryGraphProcessor` 抛 BizCheckFailException，
  消息“入口键类型不存在、未启用或不允许作为入口: X”）。为让“缺失”也走这条路径，`JointQueryApi.getJointQueryGraph` 的
  `@RequestParam` 改为 `required = false`（**api 合同改动**，仅此一处；BFF 代理传 null 时不带该参数），并更新 JavaDoc。J3 仍为 422。
- J1 / J2 groups 只含泳道：`JointQueryGraphLoaderImpl` 构图后只保留有参与模板以组内键类型为行主体的分组；J1 改为从本次现读的图取分组
  （多读模板、版本、数据源 3 条 SQL）。`JointQuerySettingsResponseBody.groups` JavaDoc 同步。
- 路由编码与要求一致（operations-query-key-type-list/create/update/status/order、operations-joint-query-settings/graph/step）。
  没有块 131 的注册测试；仿块 127 新增 node 测试 `Joint query block 144 …`（ID 范围、各表行数、路由编码顺序、权限只用 131101/131103/131104、
  菜单行、路由 DISABLED、仅 26 个键类型 ACTIVE、不新增权限、changes 文件与初始化块逐字一致）。
- contract.md 已记录以上三项。
- 复测：JointQueryApiTest 11、Guardrail 4、KeyType 16、TemplateJointConfig 13、ArchitectureAutoTest 1 共 45/45 通过；
  schema/model node 测试 29 通过 / 14 失败（新增 1 项通过，14 项为既有漂移）。

## 追加（team-lead 偏差决定确认）
- 两项合同调整（J2 400、groups 只含泳道）已在上一轮落地并复测，见上节。
- `truncated` 只看 hasMore：NONE 分页同样准确——`JdbcQueryEngine` 按 `QueryPaging.none(min(模板 maxRows, 系统上限, rowCap))`
  多取 1 行判断 hasMore，rowCap 参与上限计算，因此被 rowCap 截断时 hasMore 为 true。已有用例覆盖：
  `JointQueryStepGuardrailApiTest#test_rowAndValueLimits`（NONE、模板 maxRows 1000、rowCap 100 → 100 行且 truncated）、
  `JointQueryApiTest#test_maskingTruncationAndMissingLinkColumn`（NONE maxRows 1 → truncated，nextCursor 为 null）。
- EXECUTOR_BUSY 及偏差 (3)–(9) 按接受记录；演示模板 IN 列表用 {{#if}} 包住；Gateway 离线编译与 arch-rules 计数合并在阶段 6。
