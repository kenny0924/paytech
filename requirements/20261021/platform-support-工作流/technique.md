# platform-support：工作流 · 技术方案

状态：**阶段 2—3 合并评审稿**（2026-10-21），待用户确认。需求见 [requirement.md](./requirement.md)，讨论稿见 [design-discuss/workflow](../../../design-discuss/workflow/design.md)。

> **规模总结（本次方案）：**
>
> - 涉及 4 个工程：Platform Support、operations-console-bff、operations-console-web、Platform 注册 DML。
> - 接口：Platform 新增 30 个（管理 12、运行 18），BFF 同路径转发，不另计。
> - 数据库：新增 10 张表（9 张见第 6 节，`t_workflow_callback` 见 4.2），修改旧表结构 0 张。file-document 只新增受控 Owner / 用途代码，不改表结构。
> - 注册 DML：1 个块（块号 127，执行前按 live `mor_platform` 复核），含权限 7、路由 30、菜单 5。
> - 页面：6 个视图（流程定义列表、流程编辑器、动作目录、实例列表与发起、实例详情、我的待办）。
>
> 本节是方案口径，不表示已实现或已执行。

已读规范：`DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`、`database/PLATFORM_SUPPORT_DATABASE.md`（第 1—4 节），以及 Platform 现有币种维护、file-document、access-control、operations-console-bff 与 operations-console-web 的实现。

## 1. 现状差异

| 需求项 | 当前行为及证据 | 目标 | 缺口 | 处理 |
|---|---|---|---|---|
| R1 归属 | `paytech/workflow-approval/README.md` 只是占位；`paytech/pom.xml:57` 注释、`ServiceCatalog.java:30`、`YamlEventCatalog.java:37`、`services.yaml:23`、`LOCAL_DEVELOPMENT_ENVIRONMENT.md:287` 都登记了它 | 并入 Platform | 文档与目录待清理 | 新增决定记录；删除占位与坐标 |
| R2 流程定义 | 无 | 草稿、发布、版本、停用 | 未实现 | 新增 2 张表与管理接口 |
| R2 流程图编辑器 | ops-web 已依赖 `@xyflow/react` 12.11.6，`src/externalApi/FlowEditor.tsx` 有编辑器、原因弹窗与校验面板 | 节点配置与发布校验 | 无工作流编辑器 | 参照 FlowEditor 新建，复用画布组件 |
| R3 动作目录 | 无 | 维护四类模板，`DATA_QUERY` 预留 | 未实现 | 新增 1 张表与接口 |
| R5—R6 运行 | 无 | 实例、节点、动作、验证、审批、转交 | 未实现 | 新增 6 张表、运行引擎与接口 |
| 启动表单 / 上传动作的文件 | file-document 已有上传会话、MinIO/S3 适配、扫描与下载授权，但只开放 Client 入驻（`FileLifecycleMapper.xml:53` 用途写死 `CLIENT_ONBOARDING_MATERIAL`，`OnboardingFileApi` 只服务 CLIENT 会话） | Operations 在工作流中上传和下载 | 没有 Operations 通用上传入口 | 泛化 file-document 的 Owner / 用途，新增工作流上传与下载接口（第 5 节） |
| 选人 | `GET /operations/v1/accounts` 只分页、不能搜索，且需要账号管理权限；账号只有 `login_name`、`email_masked`，没有姓名 | 按关键字选 Operations 用户 | 没有选人查询 | access-control 新增窄查询服务；显示名用登录名，没有登录名时用脱敏邮箱（Q11） |
| R7 我的待办 | 无 | 按当前用户列出待办 | 未实现 | 用动作运行与审批行上的人员索引查询 |
| R9 注册 | 已用到块 126（`platform-support-initial-data.sql` 约 5634 行） | 权限、路由、菜单 | 未注册 | 块 127 |
| 现有 Maker-Checker | 散在各服务 | 本期不迁移 | — | 不改 |

| 系统 | 职责 | 影响点 | 改代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|---|
| Platform Support | 工作流事实 Owner | 新模块 `paytech-platform-support-workflow`；core 中的 Processor；file-document 泛化；access-control 选人查询；`PersistenceConfiguration` 增加 Mapper 扫描与 TypeHandler | 代码 + PDManer + Schema DDL + 注册 DML | 依赖 file-document 存储与扫描在目标环境可用（Q7） |
| operations-console-bff | 转发 | 30 条路径的 Controller、Client 与 Converter；`application.yml` 元数据日志前缀 | 代码 + 配置 | 无聚合 |
| operations-console-web | 页面 | 新增“工作流”菜单组与 6 个视图 | 代码 | 编辑器的复杂度是主要工作量 |
| Gateway | 路由 | 只靠注册 DML 生效，不改代码 | DML | — |
| 其他业务系统 | — | 无（`PAGE_TASK` 只跳转到它们的页面） | 否 | — |
| data-service | — | 无（`DATA_QUERY` 预留） | 否 | 以后 |

## 2. 模块与分层

- **新 Maven 模块 `paytech-platform-support-workflow`**，包根为 `com.paytech.platform.support.workflow`。
  - 依赖：`-api`、`paytech-common-core`。
  - 拥有内容：10 张表的 DataObject / Mapper / Repository；Domain Service（定义、模板、运行）；纯 Java 的流程图模型、发布校验器、导航器、条件求值器；内部枚举。
  - Mapper XML 放在 `src/main/resources/mapper/workflow/`。
- **Processor 放在 `-core` 的 `workflow/service/processor`**，与 Operations 现有用例一致（例如 `UpdateCurrencySpecProcessor`）。原因：
  - 一次写入要在同一本地事务里组合三件事：access-control 的会话与权限重检（`OperationsManagementPolicyService.requirePermission`）、工作流 Service、file-document 的 Owner 登记。
  - 还要接入 `OperationsTransactionExecutor` 的审计终态。
  - `-core` 本来就是这些模块的组合根，workflow 模块因此不需要依赖 access-control 或 file-document，依赖保持单向无环。
- **Controller / Converter** 放在 `-app` 的 `ext/rest/workflow`，实现 `-api` 中 `com.paytech.platform.support.api.workflow` 的四个接口：
  - `WorkflowActionTemplateManagementApi`
  - `WorkflowDefinitionManagementApi`
  - `WorkflowInstanceApi`
  - `WorkflowFileApi`
- **公共机制直接复用**，不重复建设：
  - 操作人：Header `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`。
  - 锁：`AbstractLockProcessor`。
  - ID：`IdGenerator`。
  - 审计：`OperationsTransactionExecutor` + `AuditContext`。
  - 分页：`PagingRequestBody`。
- 工作流推进本身全部同步完成。唯一的提交后异步步骤是 4.2 的通知与业务回调：使用 Platform 已有的 `compensation_event` 与 MQ（XXL-Job 只作兜底唤醒）。不使用 Redis 缓存，也不调用外部 HTTP。

### 2.1 实现期调整（2026-10-22，阶段 4 第二段）

- Processor 放在 workflow 模块的 `service/processor`，不放 core，DataObject 不出模块（按 2026-09-16 模块自有 Processor 决定）。
- 业务回调事件走框架交换机 `platform-support.events`，事件类型 `WorkflowCallbackRaised` v1，已在事件目录登记；不单建 `topic_platform_workflow_event`。订阅方按事件类型与 `callbackCode` 过滤。
- 发布校验不通过返回 400，消息带问题数与首个问题；完整问题列表从定义详情读取。
- 邮件通知收件邮箱复用 access-control 的登录邮箱 Vault（purpose `PLATFORM_LOGIN_EMAIL_V1`）Reveal。
- 列名避开保留字：`t_workflow_approval.decision_comment`、`t_workflow_callback.trigger_code`；`t_workflow_callback.target` 为 JSON。

## 3. 流程图与配置结构

三段 JSON 都有固定结构，由 workflow 模块的 Java 模型解析和校验，不是自由容器。

### 3.1 `graph_json`（流程版本）

```json
{
  "nodes": [
    {"key": "start", "type": "START", "name": "开始", "position": {"x": 0, "y": 0}},
    {"key": "approve", "type": "TASK", "name": "立项审批", "position": {"x": 0, "y": 120},
     "approval": {"mode": "ANY", "approverIds": ["900000000000000101"]},
     "actions": []},
    {"key": "commerce", "type": "TASK", "name": "商务：配置产品与价目",
     "actions": [{"key": "commerceProduct", "templateId": "880000000000000201", "required": true,
                  "executorId": "900000000000000102",
                  "inputs": {"channelCode": {"source": "VARIABLE", "value": "start.channelCode"}}}]},
    {"key": "applePay", "type": "EXCLUSIVE", "name": "含 Apple Pay？",
     "conditions": [{"edgeKey": "e7", "all": [{"variable": "start.paymentMethod", "operator": "EQ", "value": "APPLE_PAY"}]}],
     "defaultEdgeKey": "e8"},
    {"key": "fork", "type": "PARALLEL_SPLIT"}, {"key": "join", "type": "PARALLEL_JOIN"},
    {"key": "end", "type": "END"}
  ],
  "edges": [{"key": "e1", "from": "start", "to": "approve"}]
}
```

- 节点类型：`START`、`END`、`TASK`、`EXCLUSIVE`、`PARALLEL_SPLIT`、`PARALLEL_JOIN`。
- 条件比较符：`EQ`、`NE`、`IN`、`CONTAINS`、`GT`、`GTE`、`LT`、`LTE`、`IS_EMPTY`、`IS_NOT_EMPTY`。一条边内各子句是“且”关系；“或”用多条边表达。
- 文件字段只允许 `IS_EMPTY` / `IS_NOT_EMPTY`。
- 节点 `key` 与动作 `key` 在一个流程版本内全局唯一，由前端生成，用户可改。
- 上限：节点 ≤ 100，每个节点的动作 ≤ 20，`graph_json` ≤ 48 KiB、`start_form_json` ≤ 12 KiB（2026-10-22 因 Gateway 单请求 Body 上限 64 KiB 由 256 KiB 下调）。

### 3.2 字段定义（启动表单与 `FORM` 动作共用）

```json
{"fields": [
  {"key": "channelCode", "label": "渠道编码", "type": "TEXT", "required": true, "rules": {"maxLength": 64}},
  {"key": "paymentMethod", "label": "支付方式", "type": "ENUM", "required": true,
   "rules": {"options": [{"value": "CARD", "label": "卡"}, {"value": "APPLE_PAY", "label": "Apple Pay"}]}},
  {"key": "contractFiles", "label": "渠道合同", "type": "FILE", "required": true,
   "rules": {"minFiles": 1, "maxFiles": 5, "mimeTypes": ["application/pdf"]}}
]}
```

- 字段类型：`TEXT`（≤ 255）、`LONG_TEXT`（≤ 4000）、`NUMBER`（十进制字符串，带 `min` / `max` / `scale`）、`BOOLEAN`、`ENUM`、`DATE`（`YYYY-MM-DD`）、`FILE`（文件地址数组，如 `["/files/v1/123"]`：文件服务签发的 `/files/v1/{fileId}`，用 `FileAddress` 解析，不接受纯数字 ID；格式不合法返回 422 `VALIDATION_ERROR`，字段路径为 `startValues.{key}[i]` 或 `output.{key}[i]`）。业务数据只存文件地址、不存文件 ID（全局原则，见 `requirements/20261022/platform-support-文件多存储并存/segment2.md` 第 1 节 P1—P3；2026-10-22 用户确认工作流按此实现）。
- 每个表单最多 50 个字段。

### 3.3 动作模板 `config_json`（按类型）

| 类型 | 配置 | 输出（变量名） | 验证 |
|---|---|---|---|
| `FORM` | `{"fields":[…]}` | `<actionKey>.<field>` | 字段规则 |
| `FILE_UPLOAD` | `{"minFiles":1,"maxFiles":5,"mimeTypes":[…],"instruction":""}` | `<actionKey>.files`（地址数组，提交形如 `{"files":["/files/v1/{fileId}"]}`，非法地址按 `output.files[i]` 返回 422） | 地址合法、文件由提交人以工作流附件上传、未被其他实例引用、扫描通过（`AVAILABLE`）、数量与类型符合配置；验证证据 `{"files":[地址…]}` |
| `PAGE_TASK` | `{"pageRoute":"/commercial/agreements","parameters":[{"key":"channelCode","label":"渠道"}],"instruction":""}` | `<actionKey>.note` | 执行人确认 |
| `MANUAL_CONFIRM` | `{"instruction":"","noteRequired":true}` | `<actionKey>.note` | 执行人确认 |
| `DATA_QUERY`（预留） | `{"queryCode":"","parameters":[…],"passRule":{…},"evidenceColumns":[…]}` | 指定列 | 本期不能被发布引用 |

- `DATA_QUERY` 对接口径（2026-10-22 与 Platform 数据查询服务会话确认，以后启用时用）：`queryCode` 即对方的 `templateCode`；可用查询列表 `GET /data-query-admin/v1/templates`、详情 `GET /templates/{id}`（参数声明、列声明、版本号）；执行在同进程调用 `ExecuteDataQueryProcessor`（对外 `POST /data-query/v1/templates/{templateCode}/executions`，入参 params / page / size / withTotal / cursor，返回 columns / rows / hasMore / total / nextCursor / versionNo）。返回列以执行结果为准、按列名匹配 `passRule` 与 `evidenceColumns`。接口合同（研发中，可能小改）：`requirements/20261022/platform-support-数据查询服务/tasks/C-api-contract.md` 第 8、9、18 号接口；Java 合同在 `/Users/kenny/Desktop/Workspace/Code-MoR-dataquery` 的 `paytech-platform-support-api` `api/dataquery` 包（未合入 main）。本期不改动。
- `pageRoute` 只允许以 `/` 开头的控制台内部路由，不接受外部 URL。参数按 `inputs` 映射成查询串，由前端拼接。
- **实例变量不单独建表**。启动值存在实例上，动作输出存在动作运行上，变量在运行时从这两处计算得出：
  - `start.*` 取自实例的 `start_values_json`；
  - `<actionKey>.*` 取自该动作**最新一轮**、状态为 `VERIFIED` 的动作运行的 `output_json`。

### 3.4 发布校验（B1）

由纯 Java 的 `WorkflowGraphValidator` 执行：

- 保存草稿时返回问题列表，但不阻止保存；发布时有任何问题都拒绝发布。
- 每个问题带节点或边的 key，供画布标红。

校验项：

1. 恰好一个 `START`、至少一个 `END`；`START` 没有入边，`END` 没有出边。
2. 所有节点从开始可达，且都能到达结束。
3. `TASK` 只有一条出边；`EXCLUSIVE` 至少两条出边，且有且只有一条默认边，条件引用的边属于本节点。
4. 并行分叉与汇合成对且一一对应；分支内部不能跨分支连线，也不能连到块外；**不允许嵌套并行**（Q8）；`END` 不能位于并行块内部。
5. `TASK` 至少有一个审批人或一个动作；审批人与执行人都是 ACTIVE 的 Operations 账号，同一节点的审批人不重复。
6. 引用的动作模板都是 ACTIVE，且不是 `DATA_QUERY`。
7. 条件与输入映射引用的变量来自启动表单或上游动作输出，类型与比较符匹配；并行分支之间的输出不能互相引用，汇合后才可用。
8. 不能有环（驳回通过运行时规则实现，不靠图上的回边）。

## 4. 运行引擎

由 workflow 模块的 `WorkflowNavigator`（纯 Java）和 `WorkflowRuntimeService` 实现，所有推进都在**同一次请求的本地事务内同步完成**。

| 事件 | 行为 |
|---|---|
| 发起 | 校验启动表单与文件 → 冻结人员分配（版本默认值加上发起时的修改，存入 `assignment_json`）→ 写实例 → 写 `START` 运行（`PASSED`）→ 推进 |
| 推进到 `TASK` | 新建节点运行（`ACTIVE`，轮次 = 该节点已有最大轮次 + 1），为每个动作新建动作运行（`PENDING`）。节点没有动作时直接进入审批（`APPROVING`） |
| 推进到 `EXCLUSIVE` | 用当前变量求值，写运行（`PASSED`，记录 `selected_edge_key`）→ 沿选中的边继续推进 |
| 推进到 `PARALLEL_SPLIT` | 写运行（`PASSED`）→ 每条出边各自推进，后续节点运行的 `branch_key` 取该出边的 key |
| 推进到 `PARALLEL_JOIN` | 没有处于 `WAITING` 的汇合运行时新建一条。每个入边来源节点的最新一轮都是 `PASSED` 时，汇合 `PASSED` 并继续推进，后续节点的 `branch_key` 清空 |
| 推进到 `END` | 写运行（`PASSED`）；实例进入 `COMPLETED` |
| 提交动作 | 只有指派执行人可以提交，节点须为 `ACTIVE`。保存输出并同步验证，写验证记录，结果为 `VERIFIED` 或 `UNVERIFIED`。必选动作全部 `VERIFIED` 后：没有审批人则 `PASSED` 并推进；有审批人则进入 `APPROVING`，按冻结的审批人生成审批行（`PENDING`） |
| 重新验证 | 仅用于 `FILE_UPLOAD`（文件扫描是异步的，提交时可能还没通过）和以后的 `DATA_QUERY` |
| 审批同意 | 本审批行改为 `APPROVED`。`ANY` 模式下，其余审批行改为 `WITHDRAWN`，节点 `PASSED` 并推进；`ALL` 模式下，全部同意后才 `PASSED` |
| 审批退回修改 | 仅节点配有动作时可用。本审批行改为 `RETURNED`，其余审批行 `WITHDRAWN`，节点运行 `RETURNED`；同一节点新建一轮 `ACTIVE`，动作运行新建一轮，表单默认带出上一轮的值，执行人须重新提交、重新验证，之后重新开放审批。不触及上一节点，并行分支第一个节点也可使用 |
| 审批驳回 | 本审批行改为 `REJECTED`，其余审批行 `WITHDRAWN`，节点 `REJECTED`。沿 `previous_node_run_id` 跳过网关，回到最近的 `TASK` 运行，为该节点新建一轮 `ACTIVE`；新一轮的表单默认带出上一轮的值，但仍须重新提交和验证。跳过网关时如果越过了并行分叉，即为“分支第一个节点”，拒绝驳回。汇合后的第一个节点驳回时，必须指定目标分支：汇合运行回到 `WAITING`，目标分支最后一个 `TASK` 节点新建一轮 |
| 转交 | 修改动作运行的执行人或审批行的审批人，并写一条转交记录。不能转给自己，也不能转给同一节点的其他审批人 |
| 终止 | 实例 `TERMINATED`；进行中的节点运行改为 `CANCELLED`，未完成的动作运行改为 `CANCELLED`，`PENDING` 审批行改为 `WITHDRAWN` |

> **2026-10-22 修订**（[工作流体验优化 4.3](../../20261022/platform-support-工作流体验优化/technique.md)）：「提交动作」改为「完成动作」，只检查必填与技术保护（值类型、文件归属与地址格式、平台长度上限），不合格返回 422 字段错误、不改状态，合格直接 `COMPLETED`，不再写 `t_workflow_verification`；动作状态改为 `PENDING / COMPLETED / CANCELLED`（`VERIFIED` → `COMPLETED`，去掉 `UNVERIFIED`）。必选动作全部 `COMPLETED` 才进入审批或放行；必选动作在节点进入审批后不能再完成或修改（409），可选动作到节点放行前可完成；审批「同意」时再检查一次必选动作，未全部完成返回 409。「重新验证」已删除。

- **验证通过即完成（Q9）**：动作 `VERIFIED` 后不再接受提交；要改只能由审批人退回修改开新一轮。之后业务数据如何变化由业务系统负责，工作流不跟踪。实例的节点、动作和配置始终来自发起时冻结的流程版本，运行中只能变更人员（转交）和执行人提交的内容。
  - **2026-10-22 修订**：改为“完成即锁定”——必选动作在节点仍为 `ACTIVE` 时可再次完成（修改内容），进入审批后锁定；见上表后的修订说明。
- **同一人限制**：同一人不能既是该节点某个动作的提交人又审批该节点。在审批决定时校验，而不是在指派时校验，因为转交可能让一个人兼两职。

### 4.1 并发与事务

- **实例锁**：所有修改实例的入口（提交动作、重新验证、审批、转交、终止）都锁 `workflow-instance:{instanceId}`，使用 `TRY_LOCK`，等待时间沿用服务配置。事务内再做两道检查：实例 `version` CAS，以及节点运行、动作运行、审批行各自的状态条件更新，行数不符一律报 409。
  - **2026-10-22 修订**：“重新验证”入口已删除，其余入口不变。
- **定义与模板锁**：保存草稿、发布、启停流程锁 `workflow-definition:{definitionId}`；修改、启停动作模板锁 `workflow-action-template:{templateId}`。两者都同时做 `version` CAS。
- **发起不加锁**：发起新实例是新建记录，没有可竞争的资源。
- **事务内只写 `mor_platform`**。用户与权限校验、file-document 的 Owner 登记都是同库的本地 Service，可以在同一事务内完成。没有外部调用。
- **不做业务防重**：发起、提交、审批都没有业务唯一身份，HTTP 重放由 Gateway 的 `Idempotency-Key` 处理。审批、提交依靠状态条件更新天然防重，同一审批行不能被决定两次。

## 4.2 通知与业务回调（R10）

- **形态已确认（2026-10-21 用户“只走 MQ 没问题”）**：业务回调只发内部 MQ 事件，不回调外部 HTTP 地址；通知只走我的待办与邮件。

- **规则存储**：规则存在 `graph_json` 节点的 `notifications` 数组里（实例级规则存在 `settings.notifications`），随版本发布冻结。格式示例：`{"trigger":"RETURNED_FOR_REVISION","type":"NOTIFY","recipients":["NODE_EXECUTORS","STARTER"],"channels":["INBOX","EMAIL"]}`，或 `{"trigger":"PASSED","type":"CALLBACK","callbackCode":"COMMERCIAL_PRODUCT_READY"}`。
- **触发**：状态变化与匹配到的规则在**同一本地事务**内登记：
  - 每次执行写一行 `t_workflow_callback`（新增第 10 张表）；
  - 同时写一条 `compensation_event`。
  - 提交后由补偿 Handler 执行：通知调用 Platform Delivery 发邮件（“我的待办”本身就是查询结果，不需要另外投递）；回调发布到 `topic_platform_workflow_event`。
  - 失败按退避重试，上限 5 次，超过后标记 `FAILED` 并告警。有 `WORKFLOW_INSTANCE_MANAGE` 权限的人可以手动重试。
- **事件结构**：`eventId`、`callbackCode`、`trigger`、`definitionCode`、`versionNo`、`instanceId`、`businessKey`、`nodeKey`、`roundNo`、`fromStatus`、`toStatus`、`actorId`、`comment`、`occurredAt`。事件不携带表单值或文件。
- **订阅方**：订阅方按 `callbackCode` 过滤消费，自行做幂等（以 `eventId` 为键）。回调只是通知，不能作为业务执行开关。
- **新增表 `t_workflow_callback`**：
  - 字段：`callback_id`、`instance_id`、`node_run_id` NULL、`trigger`、`callback_type`（NOTIFY / CALLBACK）、`target`（收件人列表或回调编码）、`status`（PENDING、SENT、FAILED）、`attempt_count`、`last_error` VC512 NULL、`sent_time` NULL、`version`、`create_time`、`update_time`。
  - 索引：`idx_workflow_cb_inst(instance_id, create_time)`、`idx_workflow_cb_status(status, update_time)`。
- **新增接口 2 个**：
  - `GET /workflow/v1/instances/{instanceId}/activities`：活动记录，合并操作、状态变化、通知与回调。
  - `POST /workflow/v1/callbacks/{callbackId}/retry`：手动重试，需要 INSTANCE_MANAGE 权限。
- **影响**：技术方案的规模相应变为 10 张表、30 个接口。workflow 模块新增 MQ 发布与补偿 Handler；Platform 已有 `compensation_event` 与 messaging 依赖，本期新增一个 Topic，并在事件目录中登记。

## 5. 文件

> **2026-10-22 已确认的调整**（用户“可以”）（来自 Platform「文件多存储并存」需求，其决定 D3：内部系统不做文件级权限，文件只记 `source_system` 与 `reason_code`，后续删除 `t_file_upload.owner_*` 与 `t_file_owner_ref`）：
> - 本节的 owner_type / purpose 泛化不再做，工作流不写 `t_file_owner_ref`。
> - 上传走 file-document 的 Service 入口，`source_system=platform-support`、`reason_code=WORKFLOW_ATTACHMENT`；存储桶由对方的 `t_file_storage_route` 选择。
> - 附件归属不新增表：文件地址已在 `start_values_json` 与 `output_json` 中（业务数据不存文件 ID，见 5.1 第 11 条）。下载前工作流检查文件地址出现在本实例且请求人是参与人；提交时检查文件由提交人上传、`reason_code` 匹配、状态 `AVAILABLE`。对方已确认（2026-10-22）：保留可空的 `uploader_id`，开始上传入口带可选 `uploaderAccountId`（工作流传当前操作人）；按文件 ID 可查扫描状态、MIME、大小、摘要、`source_system`、`reason_code`、上传人与创建时间；另有完成上传与签发下载授权入口。接口（2026-10-22 对方定稿，代码在 `/Users/kenny/Desktop/Workspace/Code-MoR-filestore`，未合入主工作区）：
>   - 开始上传 `StartFileUploadProcessor.process(StartFileUploadRequest(sourceSystem, reasonCode, filename, mimeType, sizeBytes, uploaderAccountId))`；
>   - 完成上传 `CompleteFileUploadProcessor.process(CompleteFileUploadRequest(sourceSystem, uploadId))`，只有原来源系统能完成；
>   - 查元数据 `FileLifecycleService.describe(fileId)` → `StoredFileView`；
>   - 下载授权 `AuthorizeFileDownloadProcessor.process(AuthorizeFileDownloadRequest(fileId))`，须 `AVAILABLE`，返回 60 秒地址。
> - 工作流文件段在对方合入主工作区后，基于合入后的代码开工。

复用 file-document 的上传会话、存储适配、扫描与下载授权，只把 Owner / 用途泛化到工作流：

| 步骤 | 接口 | 规则 |
|---|---|---|
| 开始上传 | `POST /workflow/v1/attachments/uploads` | 上传会话的 `owner_system=PLATFORM`、`owner_type=WORKFLOW_UPLOADER`、`owner_ref_id` 为当前账号 ID，`tenant_id` 为 INTERNAL 租户。返回受控 PUT 授权 |
| 完成上传 | `POST /workflow/v1/attachments/uploads/{uploadId}/completion` | 只能由上传人完成；进入扫描 |
| 挂到实例 | 发起实例或提交 `FILE_UPLOAD` / 表单时内部完成 | 文件须由本人上传、扫描已通过（`AVAILABLE`）、未挂到其他实例。写 `t_file_owner_ref`：`owner_type=WORKFLOW_INSTANCE`、`owner_ref_id=instanceId`、`purpose_code=WORKFLOW_ATTACHMENT` |
| 下载 | `POST /workflow/v1/instances/{instanceId}/files/{fileId}/download` | 实例参与人或有 `WORKFLOW_INSTANCE_READ` 权限的人；文件须挂在该实例上。返回短时授权 |

- 发起时如果文件仍在扫描中，拒绝发起，提示“文件扫描中，请稍后再提交”。
- `FILE_UPLOAD` 动作允许先提交；文件还在扫描中时验证结果为 `UNVERIFIED`，扫描通过后再触发一次重新验证即可。
  - **2026-10-22 修订**：扫描状态不再挡动作完成，扫描中也能完成；发起仍拒绝扫描中与未通过扫描的文件；文件数量与类型不再校验。
- file-document 需要做的改动：
  - 新增按调用方给定 Owner 开始上传的 Service 入口；
  - 新增“为已有文件追加 Owner 引用”的入口；
  - 用途改为由调用方传入，不再写死。
  - 现有 Client 入驻路径保持原行为，只把写死的用途改成由它自己传入。

> 上表与"file-document 需要做的改动"为原方案，已被本节开头的确认调整取代；实际实现见 5.1。

### 5.1 文件段实现（2026-10-22，阶段 4 文件段）

1. **模块位置**：#26 / #27 / #28 的 Processor 放在 core 的 `service/processor/workflowattachment`（文件入口在 core，与 data-query 做法一致），core 新增对 workflow 的依赖；#20 与发起、提交的文件校验在 workflow 模块，workflow 新增对 file-document 的依赖（`FileLifecycleService.describe` 与上传记录查询）。arch-rules 的 Reactor 依赖图检查通过。
2. **接口形态**：#26 / #27 / #28 复用 `api/files` 已有的上传、完成、下载响应体；三者不开事务，先重检 `WORKFLOW_TASK_HANDLE`，再调用文件入口（登记短事务、存储核对与签发由文件入口负责）；不写工作流表，不登记 Operations 审计；文件能力未开启时返回 503。完成上传要求来源、原因、上传人都与当前操作人一致，否则按不存在返回 404。
3. **校验分两类**：归属问题（他人上传、不是工作流附件、已被其他实例引用）直接 400 拒绝且不保存，防止把别人的文件 ID 写进实例后借下载读取；内容问题（数量、类型、扫描状态）在动作提交时记 `UNVERIFIED`，在发起时拒绝，扫描中固定提示“文件扫描中，请稍后再提交”。
4. **下载归属范围**：启动值中的 `FILE` 字段，加本实例各轮动作运行的输出（含未通过的提交）；请求人须是参与人或有 `WORKFLOW_INSTANCE_READ`。读侧检查不开事务，通过后在事务外签发 60 秒地址。
5. **不能用于其他实例**：按地址字符串用 `JSON_SEARCH(…, 'one', 地址, '!', '$.*[*]')` 查实例启动值与动作输出（转义符 `!`，不受 NO_BACKSLASH_ESCAPES 影响），同一实例的不同轮次允许复用；按运营流程量级接受全表扫描，不加索引。
6. **重新验证（#20）**：只适用于已提交、`UNVERIFIED`、含文件的动作（`FILE_UPLOAD` 或含 `FILE` 字段的 `FORM`），只能当前执行人触发，不改提交内容与提交人；锁实例（TRY_LOCK）后在一个本地短事务内重检权限、验证、保存、审计（`OPERATIONS_WORKFLOW_ACTION_REVERIFY`）；验证证据为 `{"files":[地址…]}`（按第 11 条存地址）。
   - **2026-10-22 修订**：#20 已删除（Platform API、Controller、Processor、BFF 转发与路由 900000000000127429 及其 6 行 header、6 行 guard）；第 3 条中的“内容问题记 `UNVERIFIED`”改为：动作完成不检查数量、类型与扫描状态。
7. **实例详情**新增 `files`（文件地址 `fileAddress`、文件 ID（只用于拼下载路径）、名称、类型、大小、扫描状态、上传人）。
8. **草稿结构错误**：保存草稿与发布时 `graph` / `startForm` 的结构错误改为 422 `VALIDATION_ERROR` 字段错误，路径以 `graph`、`startForm` 为根（如 `graph.nodes[2].type`）；动作模板配置同理以 `config` 为根。
9. **路由（块 127 追加）**：900000000000127429—127432 对应 #20、#26、#27、#28，均绑定 `WORKFLOW_TASK_HANDLE`；Body 上限开始上传 4096、其余 1024；重新验证每人每分钟 10 次、并发 2，附件三条每人每分钟 30 次、并发 3。块 127 合计 32 条路由、416 行。已执行过块 127 的环境用 `verification/local-e2e/40-workflow-file-routes.sql` 增量补齐。
   - **2026-10-22 修订**：删除 127429（#20）后块 127 为 31 条路由；加上「我发起的」菜单 127311，块 127 合计 405 行。
10. **前端**：启动表单、表单动作与 `FILE_UPLOAD` 面板真实上传（开始 → 直传 → 完成 → 显示扫描状态），任务面板与实例详情按需签发预览和下载，`UNVERIFIED` 文件动作提供重新验证。已知待联调风险：存储下载地址若带 `Content-Disposition: attachment`，PDF 无法内嵌预览；发起前没有扫描状态轮询，靠后端拒绝提示。
11. **存文件地址（2026-10-22 用户确认）**：启动值与 `FORM` 的 `FILE` 字段、`FILE_UPLOAD` 输出、验证证据都存地址；提交与发起先用 `FileAddress.parse` 校验格式（非法 422），再用解析出的 ID 查元数据做归属、内容与扫描检查。下载 #28 的路径参数仍是 `{fileId}`（URL 寻址，不是业务存储），检查 `FileAddress.of(fileId).value()` 出现在本实例引用中。#27 完成上传的响应带 `fileAddress`（文件存储第二段），前端直接使用并核对与 `fileId` 一致，旧响应缺该字段时才按固定格式生成。`FileAddress` 归文件存储需求所有。
12. **下载方式（2026-10-22 跟进）**：#28 增加可选 Query `disposition`（`INLINE`、`ATTACHMENT`，缺省或为空按 `ATTACHMENT`，其他值 400 `INVALID_REQUEST`），Platform 原样传给文件入口 `AuthorizeFileDownloadRequest`；`INLINE` 只对扫描 `AVAILABLE` 且 MIME 为 `application/pdf`、`image/png`、`image/jpeg` 的文件生效，否则由文件入口静默降为 `ATTACHMENT`。响应 `FileDownloadResponseBody.disposition` 为实际生效的方式，BFF 同路径转发 Query 并原样返回。前端“查看”对 PDF / PNG / JPEG 请求 `INLINE`，返回 `INLINE` 时 iframe / img 内嵌（不加 sandbox，加 `referrerPolicy="no-referrer"`），降为 `ATTACHMENT` 时改为“下载查看”；“下载”一律请求 `ATTACHMENT`。第 10 条的内嵌预览风险由此关闭。

## 6. 数据设计（`mor_platform`，新增 10 张表）

下表列出 9 张，第 10 张 `t_workflow_callback` 的字段与索引见 4.2。

通用规则：

- 主键为 Snowflake `BIGINT`。时间字段为 `DATETIME(6)` UTC。
- JSON 列都有第 3 节定义的结构和大小上限。
- 不建 `CHECK` / `FOREIGN KEY`。
- 人员字段保存 `t_user_account.account_id`。
- 运行表不接入 BCR：它们本身就是追加或带状态的领域证据。

| 表 | 字段（类型简写） | 索引 |
|---|---|---|
| `t_workflow_definition` 流程定义 | `definition_id` PK；`definition_code` VC64；`definition_name` VC128；`description` VC512 NULL；`status` VC32（ACTIVE、INACTIVE）；`current_version_no` INT NULL（最新发布版本，未发布为 NULL）；`draft_version_no` INT NULL（当前草稿，没有草稿为 NULL）；`version` INT；`created_by`、`updated_by` BIGINT；`create_time`、`update_time` | `uk_workflow_def_code(definition_code)`；`idx_workflow_def_status(status, update_time)` |
| `t_workflow_definition_version` 流程版本 | `definition_version_id` PK；`definition_id`；`version_no` INT；`status` VC32（DRAFT、PUBLISHED）；`graph_json` JSON；`start_form_json` JSON；`action_snapshot_json` JSON NULL（发布时冻结引用模板的 ID、模板版本、类型、配置）；`published_by` BIGINT NULL；`published_time` NULL；`version` INT；`created_by`、`updated_by`；`create_time`、`update_time` | `uk_workflow_def_ver(definition_id, version_no)` |
| `t_workflow_action_template` 动作模板 | `action_template_id` PK；`template_code` VC64；`template_name` VC128；`action_type` VC32（FORM、FILE_UPLOAD、PAGE_TASK、MANUAL_CONFIRM、DATA_QUERY）；`description` VC512 NULL；`config_json` JSON；`status` VC32（ACTIVE、INACTIVE）；`version` INT；`created_by`、`updated_by`；`create_time`、`update_time` | `uk_workflow_tpl_code(template_code)`；`idx_workflow_tpl_type(action_type, status)` |
| `t_workflow_instance` 流程实例 | `instance_id` PK；`definition_id`；`definition_version_id`；`title` VC128；`business_key` VC128 NULL；`status` VC32（RUNNING、COMPLETED、TERMINATED）；`start_values_json` JSON；`assignment_json` JSON（冻结的节点审批人与动作执行人）；`started_by` BIGINT；`start_time`；`end_time` NULL；`terminated_by` BIGINT NULL；`termination_reason` VC512 NULL；`version` INT；`create_time`、`update_time` | `idx_workflow_inst_status(status, start_time)`；`idx_workflow_inst_def(definition_id, status)`；`idx_workflow_inst_starter(started_by, start_time)`；`idx_workflow_inst_bizkey(business_key)` |
| `t_workflow_node_run` 节点运行 | `node_run_id` PK；`instance_id`；`node_key` VC64；`node_type` VC32；`round_no` INT；`branch_key` VC64 NULL（并行分支内为分叉出边的 key）；`previous_node_run_id` BIGINT NULL（实际来源节点运行；汇合节点为 NULL）；`status` VC32（ACTIVE、APPROVING、WAITING、PASSED、RETURNED、REJECTED、CANCELLED）；`selected_edge_key` VC64 NULL（排他网关选中的边）；`activated_time`；`completed_time` NULL；`version` INT；`create_time`、`update_time` | `uk_workflow_node_round(instance_id, node_key, round_no)`；`idx_workflow_node_status(instance_id, status)` |
| `t_workflow_action_run` 动作运行 | `action_run_id` PK；`instance_id`；`node_run_id`；`action_key` VC64；`action_template_id`；`action_type` VC32；`required` TINYINT（1 必选，0 可选）；`assignee_id` BIGINT；`status` VC32（PENDING、VERIFIED、UNVERIFIED、CANCELLED）；`output_json` JSON NULL；`submitted_by` BIGINT NULL；`submitted_time` NULL；`version` INT；`create_time`、`update_time` | `uk_workflow_action_node(node_run_id, action_key)`；`idx_workflow_action_assignee(assignee_id, status)`；`idx_workflow_action_inst(instance_id)` |
| `t_workflow_verification` 验证记录 | `verification_id` PK；`action_run_id`；`instance_id`；`result` VC32（PASSED、FAILED）；`evidence_json` JSON NULL（对象类型、ID、版本、状态，或文件 ID 列表）；`failure_reason` VC512 NULL；`verified_by` BIGINT；`verified_time`；`create_time`（不可变，没有 `update_time`） | `idx_workflow_verify_action(action_run_id, verified_time)` |
| `t_workflow_approval` 审批 | `approval_id` PK；`instance_id`；`node_run_id`；`approver_id` BIGINT；`status` VC32（PENDING、APPROVED、RETURNED、REJECTED、WITHDRAWN）；`comment` VC512 NULL；`reject_target_node_key` VC64 NULL（汇合后驳回时指定的分支末节点）；`decided_time` NULL；`version` INT；`create_time`、`update_time` | `uk_workflow_approval(node_run_id, approver_id)`；`idx_workflow_approval_user(approver_id, status)` |
| `t_workflow_transfer` 转交记录 | `transfer_id` PK；`instance_id`；`target_type` VC32（ACTION_RUN、APPROVAL）；`target_id`；`from_user_id`；`to_user_id`；`reason` VC512 NULL；`transferred_by`；`create_time`（不可变） | `idx_workflow_transfer_inst(instance_id, create_time)`；`idx_workflow_transfer_target(target_type, target_id)` |

**删除检验：**

- 去掉 `t_workflow_verification`：只能看到最后一次结果，用户要求的“每次验证的证据与原因”丢失。
- 去掉 `t_workflow_transfer`：转交历史丢失。
- 去掉 `t_workflow_approval`：`ALL` 模式无法逐人记录，“我的待批”也无法按人查询。
- 变量表已按必要性门禁删除，改为运行时从实例和动作运行计算（第 3.3 节）。

**`current_version_no` / `draft_version_no` 两个指针**：

- 发起实例需要直接定位当前发布版本，编辑器需要直接定位唯一的草稿，查询频率都高。
- 两者都在定义锁加 CAS 下切换。
- “每个定义最多一份草稿”由 `draft_version_no` 保证，不需要再建 Active Slot。

**SQL**：

- PDManer 新增图组 `workflow`。
- 当前 Schema 文件 `platform-support-schema.sql` 追加 10 张表，同时新增增量文件 `docs/database/sql/changes/2026-10-21-workflow.sql`，供已有库执行。
- 本需求的 `db/ddl.sql` 只作为索引，指向增量文件。
- 回滚：10 张表都是新增，回滚即删表。执行前先确认表中没有需要保留的数据。

## 7. 接口（Platform，BFF 同路径转发）

- 路径前缀：管理类 `/workflow-admin/v1`，运行类 `/workflow/v1`。
- 写接口返回最小结果，ID 一律为字符串。
- 错误：普通业务拒绝返回 400 `BUSINESS_CHECK_FAILED` 并附中文说明；版本冲突或锁冲突返回 409。

| # | Method + Path | 权限 | 说明 |
|---|---|---|---|
| 1 | GET `/workflow-admin/v1/action-templates` | DEFINITION_READ | 分页；按类型、状态、关键字筛选 |
| 2 | GET `/workflow-admin/v1/action-templates/{templateId}` | DEFINITION_READ | 详情 |
| 3 | POST `/workflow-admin/v1/action-templates` | ACTION_TEMPLATE_MANAGE | 新建（ACTIVE）；按类型校验 `config` |
| 4 | PUT `/workflow-admin/v1/action-templates/{templateId}` | ACTION_TEMPLATE_MANAGE | 修改；带 `expectedVersion`；不影响已发布版本 |
| 5 | POST `/workflow-admin/v1/action-templates/{templateId}/status` | ACTION_TEMPLATE_MANAGE | 启用或停用 |
| 6 | GET `/workflow-admin/v1/definitions` | DEFINITION_READ | 分页；按状态、关键字筛选 |
| 7 | GET `/workflow-admin/v1/definitions/{definitionId}` | DEFINITION_READ | 定义、草稿（含校验问题）、版本列表 |
| 8 | POST `/workflow-admin/v1/definitions` | DEFINITION_MANAGE | 新建定义及草稿 v1（只有开始与结束节点） |
| 9 | PUT `/workflow-admin/v1/definitions/{definitionId}/draft` | DEFINITION_MANAGE | 保存名称、说明、`graph`、`startForm`；没有草稿时从最新发布版本复制出新草稿；返回校验问题 |
| 10 | POST `/workflow-admin/v1/definitions/{definitionId}/publication` | DEFINITION_MANAGE | 发布草稿；校验不通过返回 400 并附问题列表；冻结动作模板快照 |
| 11 | POST `/workflow-admin/v1/definitions/{definitionId}/status` | DEFINITION_MANAGE | 启用或停用；停用后不能发起，在途实例照常运行 |
| 12 | GET `/workflow-admin/v1/definitions/{definitionId}/versions/{versionNo}` | DEFINITION_READ | 查看已发布版本 |
| 13 | GET `/workflow/v1/launchable-definitions` | INSTANCE_START | ACTIVE 且已发布的流程 |
| 14 | GET `/workflow/v1/launchable-definitions/{definitionId}` | INSTANCE_START | 当前发布版本的启动表单、节点和默认人员 |
| 15 | POST `/workflow/v1/instances` | INSTANCE_START | 发起：`definitionId`、`versionNo`（防止发布中途换版本）、标题、业务关联键、启动值、人员修改 |
| 16 | GET `/workflow/v1/instances` | TASK_HANDLE | 分页。默认只看我参与的实例；有 INSTANCE_READ 权限可看全部；按流程、状态、发起人、业务关联键筛选 |
| 17 | GET `/workflow/v1/instances/{instanceId}` | TASK_HANDLE | 参与人或有 INSTANCE_READ 权限的人可看。返回流程图、节点运行、动作运行、验证、审批、转交、启动信息、人员显示名 |
| 18 | POST `/workflow/v1/instances/{instanceId}/termination` | TASK_HANDLE | 发起人或有 INSTANCE_MANAGE 权限的人；必须填原因 |
| 19 | POST `/workflow/v1/action-runs/{actionRunId}/submission` | TASK_HANDLE | 指派执行人提交输出并同步验证；返回本次验证结果 |
| 20 | POST `/workflow/v1/action-runs/{actionRunId}/verification` | TASK_HANDLE | 重新验证（`FILE_UPLOAD`） |
| 21 | POST `/workflow/v1/action-runs/{actionRunId}/transfer` | TASK_HANDLE | 当前执行人或有 INSTANCE_MANAGE 权限的人转交 |
| 22 | POST `/workflow/v1/approvals/{approvalId}/decision` | TASK_HANDLE | `APPROVE` / `RETURN` / `REJECT`、意见（退回修改与驳回必填意见）；汇合后驳回需带 `rejectTargetNodeKey` |
| 23 | POST `/workflow/v1/approvals/{approvalId}/transfer` | TASK_HANDLE | 当前审批人或有 INSTANCE_MANAGE 权限的人转交 |
| 24 | GET `/workflow/v1/my-tasks` | TASK_HANDLE | 分页；待执行动作与待审批节点合并，按激活时间倒序 |
| 25 | GET `/workflow/v1/assignee-candidates` | TASK_HANDLE | 按关键字匹配 ACTIVE Operations 账号的登录名或脱敏邮箱，最多 20 条 |
| 26 | POST `/workflow/v1/attachments/uploads` | TASK_HANDLE | 开始上传 |
| 27 | POST `/workflow/v1/attachments/uploads/{uploadId}/completion` | TASK_HANDLE | 完成上传 |
| 28 | POST `/workflow/v1/instances/{instanceId}/files/{fileId}/download` | TASK_HANDLE | 下载授权；可选 Query `disposition`（`INLINE` / `ATTACHMENT`，缺省 `ATTACHMENT`），响应带实际生效的 `disposition`（见 5.1 第 12 条） |

> **2026-10-22 修订**（[工作流体验优化 4.3](../../20261022/platform-support-工作流体验优化/technique.md)）：#19 路径与请求体不变，语义改为“完成动作”，响应只含 `actionRunId`、`status`（`COMPLETED`）、`instanceStatus`；必填缺失或值不合法返回 422（`output.{key}`、`output.files[i]`），必选动作所在节点已进入审批返回 409。#20 已删除。

**权限（7 项，块 127）**：

| 权限 | 用途 |
|---|---|
| `WORKFLOW_DEFINITION_READ` | 查看流程定义与动作模板 |
| `WORKFLOW_DEFINITION_MANAGE` | 维护流程定义 |
| `WORKFLOW_ACTION_TEMPLATE_MANAGE` | 维护动作目录 |
| `WORKFLOW_INSTANCE_START` | 发起流程 |
| `WORKFLOW_TASK_HANDLE` | 参与流程：待办、执行、审批、查看参与的实例 |
| `WORKFLOW_INSTANCE_READ` | 查看全部实例 |
| `WORKFLOW_INSTANCE_MANAGE` | 终止或转交任意实例 |

- Gateway 每条路由绑定一项权限。
- Platform 在事务内重检权限，并且要证明请求人是被指派的人或参与人；单凭路由权限不足以执行动作或审批。

**审计**：所有写接口沿用 `OperationsTransactionExecutor` 的审计终态登记。

## 8. 页面（operations-console-web）

新增菜单组“工作流”，下设：我的待办、流程实例、流程定义、动作目录。

```
我的待办          [类型 动作/审批] [流程]                     表格：实例标题 | 节点 | 待办 | 停留 | 去处理 →
流程实例          [流程][状态][发起人][我参与的/全部][关键字]  [发起流程]   表格 + 分页
  └ 发起流程（抽屉）  选流程 → 标题 / 业务关联键 → 启动表单（含上传）→ 节点人员（默认值，可改）→ 提交
  └ 实例详情（子路由） 顶部：标题 状态 发起人 启动信息（文件可下载）
                   左：流程图（xyflow，只读；绿 已完成 / 蓝 进行中 / 灰 未到达 / 红 被驳回）
                   右：选中节点 → 动作（执行人、状态、输出、验证记录）、审批记录、转交历史；
                       轮到我时显示「提交 / 重新验证 / 同意 / 驳回 / 转交」
流程定义          列表：编码 | 名称 | 状态 | 当前版本 | 草稿 | 更新时间      [新建]
  └ 流程编辑器（子路由） 左：节点面板 | 中：画布（xyflow 可编辑）| 右：属性面板（节点 / 动作 / 条件 / 审批人）
                   顶部：启动表单配置 | 保存草稿 | 发布（问题列表；点击问题定位节点）| 版本切换只读查看
动作目录          列表：编码 | 名称 | 类型 | 状态        [新建]  编辑抽屉按类型显示配置表单
```

- 页面模式沿用现有 Operations 配置页：筛选行、表格、抽屉、二次确认、公共错误提示与分页组件。
- 编辑器复用 `src/externalApi/FlowEditor.tsx` 的画布、校验面板和原因弹窗模式。
- 选人控件调用接口 25。
- `PAGE_TASK` 的“去处理”按钮在新标签页打开 `pageRoute`，并带上映射好的参数。
- **页面原型（2026-10-21 重做）**：在 `operations-console-web/docs/operations-console.pen` 的 y=73000 起三排，共 22 张 1920 宽画板（13—18 为 2026-10-21 补充的动作目录页面，19—22 为 2026-10-22 补充的操作页面，在 y=76900 起三排）：
  - 01—02 我的待办：左侧列表、右侧直接处理，分执行动作和审批决定两种；
  - 03 流程实例列表：带进度条和当前处理人；
  - 04 发起流程：分步弹窗，右侧是随填写内容变化的流程预览；
  - 05 实例详情：流程图铺满页面，点击节点滑出抽屉；
  - 06 活动记录：含通知与回调的投递状态；
  - 07 流程定义：卡片带缩略图和版本列表；
  - 08—11 流程编辑器：节点属性与动作、连线条件、通知与回调、启动表单设计器；
  - 12 动作目录：分类、列表、详情三栏，带执行人视角的预览；
  - 13 新建动作：选择类型（四类可用，数据查询标为预留）与基本信息，右侧说明所选类型的提交内容与验证方式；
  - 14 表单动作 · 字段设计：字段列表（拖动排序、类型、重复键报错）、字段属性（枚举选项、能否用于连线条件、被条件引用的选项提示）、执行人预览；
  - 15 文件上传动作 · 规则与验证：数量、MIME 类型、执行说明；四条自动验证、文件扫描状态、提交时未扫描完成的“重新验证”；
  - 16 数据查询（预留）：选 data-service 的查询编码、参数来源映射、通过规则、证据列，以及启用后执行人看到的多次验证记录；
  - 17 引用与影响：哪些流程版本引用、冻结的是哪个配置版本、与当前配置的差异，以及修改对运行中实例的影响；
  - 18 停用确认：说明停用后新发布不能引用、已发布版本照常运行，列出需要替换的草稿。

  - 19 退回修改后重新提交：顶部显示退回意见，带出上一轮内容并标出改动，提交栏汇总“与上一轮相比”的变化；
  - 20 审批第 2 轮：默认“只看变化”，左右对比上一轮，文件在右侧直接预览，底部一行完成决定；
  - 21 批量审批：只能勾选没有动作的审批，逐项提交、逐项显示结果；
  - 22 转交：浮层内按登录名或邮箱搜索，不能转交给自己，可写转交说明。

- **操作层面的交互规则（2026-10-22 补充，仅前端，不新增接口）：**
  - 处理完自动打开下一条（可关）；快捷键 J / K 切换，A 同意，R 退回修改，T 转交，Ctrl + Enter 提交。
  - 退回修改后的新一轮默认带出上一轮内容；执行人和审批人都能看到与上一轮的差异（前端比较两轮的 `output_json`）。
  - 审批页文件用下载授权接口（28）取地址后内嵌预览，不用另开页面。
  - 批量同意：前端对每一项调用审批接口（22），逐项展示成功或失败原因（如已被他人处理）。退回修改、驳回不提供批量。
  - 页面任务记住执行人本机是否打开过目标页面（浏览器本地存储，只作提示）。
  - 转交时未提交的填写内容不随转交带走。

  流程图按有向图绘制：开始和结束用圆形，排他网关和并行网关用菱形，连线带箭头和条件标签。“退回修改”按 Q6 的建议画出；风控分支按“同一节点既有动作又有审批”来示意，分支第一个节点的“驳回”置灰。

## 9. 研发与验证计划

| 顺序 | 内容 | 验证入口 |
|---|---|---|
| 1 | R1 决定记录与占位清理 | `arch-rules`、`instance-manifest`、`requirements-site` 测试 |
| 2 | PDManer + Schema / 增量 SQL | 本地 `mor_platform` 执行（需授权），用 `SHOW CREATE TABLE` 对照模型 |
| 3 | workflow 模块：模型、校验器、导航器、Repository、Service | `WorkflowGraphValidator` / `WorkflowNavigator` 纯 Java 测试，覆盖 B1 的 8 项校验和各种推进情形 |
| 4 | file-document 泛化 + access-control 选人 | 入驻上传的既有测试保持通过；新增工作流上传测试（本地 MinIO） |
| 5 | core Processor + app Controller | 4 个 HTTP 入口测试类，见下 |
| 6 | BFF 转发 | 30 条路径的转发测试（DTO Mock） |
| 7 | Web | 组件测试与构建；浏览器按第 8 节核对 |
| 8 | 注册 DML 块 127 | 注册数与断言测试 |

**HTTP 入口测试**（真实本地 MySQL / Redis / MinIO）：

- `WorkflowActionTemplateApiTest`：类型配置校验、版本冲突、停用后不能被发布引用。
- `WorkflowDefinitionApiTest`：草稿保存返回问题、B1 各项拒绝发布、发布冻结快照、发布后改模板不影响已发布版本、停用。
- `WorkflowInstanceApiTest`：
  - 纯审批流 `ANY` / `ALL`；
  - 必选与可选动作；
  - 排他网关的条件分支与默认分支；
  - 并行分支与汇合；
  - B5 驳回的四种位置；
  - 同一人限制、只有被指派者能操作；
  - 转交、终止、我的待办；
  - 锁冲突与版本冲突返回 409。
- `WorkflowFileApiTest`：上传、扫描中拒绝发起、`FILE_UPLOAD` 先提交后重新验证、只有参与人能下载。

## 10. 待确认问题

2026-10-21 用户确认 Q7、Q8、Q10、Q11 按建议；2026-10-22 确认 Q6、Q9。Q6—Q11 全部关闭。

| 编号 | 问题 | 建议 |
|---|---|---|
| Q6 ✅ 2026-10-22 用户确认“可以退回修改” | 节点既有动作又有审批人时，审批人发现**本节点执行人提交的内容**（表单值、文件、确认说明）有问题怎么办？按现有规则只能驳回到上一个节点，本节点执行人没有修改提交内容的机会。（实例的节点、动作和配置始终来自发起时冻结的流程版本，任何时候都不能改；能变的只有人员（转交）和执行人提交的内容） | 审批人增加第三个决定 **“退回修改”**：本节点重新进入 `ACTIVE`（新一轮），执行人修改后重新提交、重新审批，不触及上一节点。“驳回”保持回到上一节点 |
| Q7 | 文件依赖 file-document 的对象存储与扫描。本地有 MinIO；mor-test 是否启用了文件存储（`files.onboarding.enabled` 等）待阶段 6 核实 | 本期照常实现；阶段 6 核实 mor-test。如果未启用，文件类功能在 mor-test 上标为不可验收，或者另行授权开启 |
| Q8 | 是否允许并行嵌套（并行分支里再开并行）？ | 本期不允许，可以串联多个并行块。这样驳回与汇合规则保持简单 |
| Q9 | 节点进入审批后，执行人能否再修改已提交的内容？ | **已确认（2026-10-22 用户）**：动作验证通过即视为完成，工作流不再提供修改入口，也不跟踪之后业务数据的变化；业务数据后续怎么变是业务系统自己的事 |
| Q10 | 草稿编辑会不会有多人同时编辑？ | 用定义锁加 `version` CAS，后保存的人收到 409，提示刷新；不做协同编辑 |
| Q11 | 人员显示名 | Operations 账号没有姓名字段，显示登录名，没有登录名时显示脱敏邮箱。加姓名需另立 access-control 需求 |
