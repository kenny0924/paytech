# platform-support：联合查询 · 技术方案

> 状态：**已实施（2026-10-30）**：第 7—13 节为确认后的方案；§8.7 为阶段 7 验收后的修订 b / c，均已实施并部署 mor-test。
> 设计输入：[2026-10-29 联合查询决定](../../../agent-docs/decisions/2026-10-29-data-query-joint-query.md)、[design-discuss/joint-query/design.md](../../../design-discuss/joint-query/design.md)、[数据查询技术方案](../../20261022/platform-support-数据查询服务/technique.md)、[REQ-05 技术方案](../../20261028/platform-support-workflow按钮发起与数据验证/technique.md)（公开执行入口）。

路径缩写：`dq/` = `paytech/platform-support/paytech-platform-support-data-query/src/main/java/com/paytech/platform/support/dataquery/`；`ps-core/` = `paytech/platform-support/paytech-platform-support-core/src/main/java/com/paytech/platform/support/dataquery/service/processor/`；`ps-api/` = `paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/dataquery/`；`init.sql` = `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`；`ops-bff/` = `paytech/operations-console-bff/paytech-operations-console-bff-app/`；`ops-web/` = `operations-console-web/src/`。

## 1. 核对范围与证据

| 范围 | 证据 |
|---|---|
| 公开执行入口 | `dq/service/processor/RunDataQueryTemplateProcessor.java:21-61`（模块内入口：按编码执行当前 ACTIVE 版本，不做会话 / 权限 / 白名单，模板或数据源非 ACTIVE 返回 400，不存在 404）；请求 `RunDataQueryTemplateRequest`（`params` JsonNode、`page`、`size`、`withTotal`、`cursor`、`operatorAccountId` 可空、`timeoutCapSeconds` 1—8、`callerLabel`）；HTTP 入口 `ps-core/ExecuteDataQueryProcessor.java:21-41`（`callers.authorize` + 会话 `DATA_QUERY_EXECUTE` 后委托前者） |
| 执行引擎与超时 | `dq/engine/DataQueryRunner.java`：参数绑定 → 渲染 → 只读校验 / 分页改写 → 借连接执行 → 屏蔽；超时 = min(模板, 系统上限, 调用方上限)；单条元数据日志与指标 `data.query.execution` |
| 系统上限 | `dq/core/configuration/DataQueryLimitsProperties.java`：`maxTimeout` 8s（注释：须低于 BFF 9s 与 Gateway 10s）、`maxRows` 10000、`maxResponseBytes` 10MB、`maxListSize` 1000、`maxPoolSize` 10 |
| Gateway 上限 | `paytech/api-gateway/.../routing/snapshot/GatewayRoutePolicyValidator.java:14-16,160-163`：路由 `response_timeout_millis` ≤ **10000**、`body_limit_bytes` ≤ **65536**，快照校验硬编码；`OperationsRoutingProperties.java:13` 同为 10s |
| BFF 超时 | `ops-bff/.../configuration/PlatformClientConfiguration.java:282-297`：data-query 客户端读超时 `operations.data-query-read-timeout` 默认 **9s** |
| 参数声明 | `dq/template/ParameterDeclarationReader.java`：属性白名单 `PROPERTIES`（name、type、elementType、required、default、maxLength、min、max、allowedValues、maxSize、choices、fields），**未知属性直接拒绝**；`dq/domain/QueryTemplateParameter.java`（签名只有 name / type / required） |
| 列声明 | `dq/domain/QueryTemplateJson.java` `writeColumns` / `readColumns`：只写读 name、label、mask、keepStart、keepEnd；API `ps-api/request/QueryTemplateColumn.java` 同 5 个字段；签名 `dq/domain/QueryTemplateColumn.java`（name、masked） |
| 结果值 | `dq/engine/JdbcQueryEngine.java:226-240`（REQ-05 记录）：整数 / DECIMAL 转字符串；`ps-api/response/DataQueryResultResponseBody.java`（`columns`、`rows: List<List<Object>>`、`versionNo`、`hasMore`） |
| 模板读取 | `dq/domainservice/repository/QueryTemplateVersionRepository.java:26` `findCurrent(Collection<QueryTemplate>)`；`QueryTemplateRepository` 无“全部 ACTIVE 模板”方法（`selectPage` 分页）；Mapper XML `resources/mapper/dataquery/*.xml` |
| 连接池 | `dq/integration/DataSourcePoolRegistry.java:1-25`：每数据源一个 Hikari 池（connectionTimeout 3s，maximumPoolSize 取数据源配置），定时刷新多实例收敛 |
| 保存响应 | `ps-api/response/SaveQueryTemplateResponseBody.java`：`warnings: List<QueryTemplateProblem>`，可承载“是否参与联合查询” |
| 注册数据 | `init.sql:6515-6658` 块 131：权限 `DATA_QUERY_READ` / `DATA_SOURCE_MANAGE` / `DATA_QUERY_TEMPLATE_MANAGE` / `DATA_QUERY_EXECUTE`（900000000000131101—104）、菜单组“数据查询”（131301，sort 65）下 数据源 / 查询模板、18 条路由，执行路由 131418 `POST /data-query/v1/templates/{templateCode}/executions`（响应超时 10000ms） |
| Web | `ops-web/dataQuery/`（数据源、模板编辑器、试运行、版本抽屉、`templateSync.ts` 列同步）、`ops-web/pages/QueryTemplates.tsx`；**没有单模板执行页**（执行 API 只有试运行与 workflow 使用） |
| BFF | `ops-bff/.../ext/rest/DataQueryExecutionController.java`、`DataQueryManagementController.java`（同路径转发） |
| 链路字段（A4） | payment 意图来源类型 `COMMERCE_ORDER`（`acquiring/.../CustomerCheckoutModels.java:199`、`PaymentSucceededMessageConverter.java:75`）；`t_account_posting.id` 即 ledger 指令 `source_event_key`（account-schema 注释、`ledger/.../TransactionPostingServiceImpl.java:698`），该列为 **VARCHAR**；`t_billing_fact_ref.ref_id`、`t_platform_txn.source_ref_id` 亦为 VARCHAR 十进制字符串 |
| mor-test（只读） | `data_query_demo_reader` 授权（SHOW GRANTS）：SELECT 于 `mor_acquiring`、`mor_channel_connector`、`mor_client_master`、`mor_commercial`、`mor_ledger`、`mor_reconciliation`；**无** `mor_payment`、`mor_billing`、`mor_account`。mor-test 服务库（`test-environment/compose.applications.yaml`）**没有 dispute、payout**。`mor_platform.t_data_source` 现状本次未读（读取需 Platform 库凭据，被权限策略拦下），以记录为准：`MOR_*_REPLICA` 6 个 ACTIVE + `MOR_CHANNEL_CONNECTOR` INACTIVE + `MOR_TEST_MYSQL_INSTANCE`（2026-10-22 记录，REQ-05 复用 `MOR_COMMERCIAL_REPLICA`） |

## 2. 现状发现

| # | 发现 | 影响 |
|---|---|---|
| F-01 | 联合执行可复用 `RunDataQueryTemplateProcessor`（A1 成立）：模块内、无会话、可传 `timeoutCapSeconds` 与 `size`；会话、权限、调用方白名单放在 `-core` 的新 Processor，与 `ExecuteDataQueryProcessor` 同形 | 不需改执行引擎 |
| F-02 | **单次同步调用最多约 8 秒**：Gateway 路由响应超时硬上限 10s（快照校验写死），BFF 读超时 9s，单查询上限 8s。决定 Q1 的 `totalTimeoutSeconds` 30 在一次 HTTP 调用内**无法实现**；6 层串行、每层至少一次往返被查库，8 秒预算偏紧 | 执行模型须调整（S-01） |
| F-03 | 请求体上限 65536 字节：若服务端无状态而由调用方回传 `visited` 全集，大链路可能超限；只回传“本层待查键”则很小 | S-01 方案按“调用方去重后只传本层键” |
| F-04 | 参数声明读取器拒绝未知属性；列声明读写只有 5 个固定字段 | `keyType` / `keyRole` 需在读取器、写入、API DTO、签名、前端模型同步增加；旧模板无新字段 = 不参与，无数据迁移。**阶段 3 修订**：改为模板版本新增 `joint_json` 列单独存放，参数读取器与列 JSON 不改 |
| F-05 | 被查列可能是 VARCHAR 存十进制 ID（`source_event_key`、`ref_id`、`source_ref_id`）。若 INTEGER 键只能绑 INTEGER 参数，SQL 变成 `varchar_col = 123`，MySQL 隐式转换导致索引失效 | B3 放宽：INTEGER 键可绑 INTEGER 或 STRING 参数（S-02） |
| F-06 | 屏蔽列的值已被改写，不能作为下一层键 | LINK 列不得配置屏蔽（S-03） |
| F-07 | 没有“取全部 ACTIVE 模板 + 当前版本”的方法；模板总量小（mor-test 两位数） | 每次请求现读 2 条 SQL 构图，不做进程缓存与多实例失效（S-04） |
| F-08 | Web 没有单模板执行页；需求 AC-V3 “截断节点跳转单模板查询”无处可跳 | 改为联合查询页内节点“加载更多”，调用既有执行路由 131418（S-05） |
| F-09 | ID 为 64 位 Snowflake，前端数字会丢精度；执行结果中整数已转字符串 | 键值在接口中一律为字符串 |
| F-10 | mor-test 演示：只读账号缺 `mor_payment`、`mor_billing`、`mor_account` 授权；没有 dispute、payout 库 | 演示链路去掉 dispute；阶段 7 需 root 授权 3 库 + 登记 3 个数据源 + 启用 `MOR_CHANNEL_CONNECTOR`（S-06） |
| F-11 | 结果列名比对不区分大小写（`QueryTemplateColumn` 注释）；`templateSync.ts` 已能按试运行结果同步列声明 | 带 keyType 的列在保存时按同一口径校验存在性 |
| F-12 | 现有 4 个权限可覆盖：执行 `DATA_QUERY_EXECUTE`、预览 `DATA_QUERY_READ`、键类型维护 `DATA_QUERY_TEMPLATE_MANAGE` | 注册只加路由与菜单，不加权限（Q6 成立） |
| F-13 | 数据源连接池每源默认最大 10 连接，`connectionTimeout` 3s；一步内同一数据源可能同时执行多个模板 | 单步并发上限 8，同源排队受 3s 借连接超时约束，失败按节点报错 |

## 3. 需求项差异

| 需求项 | 当前行为 | 目标 | 缺口 | 处理 |
|---|---|---|---|---|
| R1 键类型 | 无 | 注册表 + 管理 | 新表、DO / Mapper / Repository / Service / Processor / API | 新建 |
| R2 模板标注 | 声明不支持 keyType；未知属性被拒 | 参数 keyType；列 keyType + keyRole | 读取器白名单、列 JSON、API DTO、校验、警告 | 扩展 |
| R3 可达预览 | 无 | 按层图 | 构图 + 接口 | 新建 |
| R4 联合执行 | 只有单模板执行 | 按层展开 + 护栏 | 编排、并行、去重、节点 / 边 | 新建；**执行模型受 F-02 约束** |
| R5 BFF / 注册 | 块 131 | +路由 +菜单 | 转发、注册块 | 扩展 |
| R6 Web | 无联合查询 / 键类型页；编辑器无标注 | 两页 + 编辑器控件 | 新页面、原型 | 新建 |
| R7 演示模板 | 7 个 DEMO_* | JQ_* 链路 | 授权与数据源缺口（F-10） | 阶段 7 |
| B3 | — | 元素类型须一致 | F-05 | 放宽（S-02） |
| B8 总超时 30s | — | 单次 HTTP 内 | F-02 | 改为分步执行 + 前端总时长（S-01） |
| AC-V3 跳转单模板 | 无页面 | — | F-08 | 改为加载更多（S-05） |

## 4. 逐系统影响

| 系统 | 职责 | 影响点 | 改代码 / 模型 / 配置 | 依赖与风险 |
|---|---|---|---|---|
| Platform · data-query 模块 | 模板、执行引擎 | `t_query_key_type` + DO / Mapper / Repository；参数读取器与列 JSON 增加字段；`QueryTemplateParameter` / `QueryTemplateColumn` 签名增加键类型；保存校验与警告；键类型图构建；联合执行步骤引擎（复用 `RunDataQueryTemplateProcessor`，有界线程池） | 代码 + 表 | 旧模板 JSON 无新字段兼容；workflow 使用的签名不受影响（只加字段） |
| Platform · core | 会话、权限、白名单、审计 | 键类型管理 Processor（`AbstractLockProcessor` + 审计）、联合执行 / 预览 Processor（`callers.authorize` + 权限） | 代码 | 与 `DataQueryManagementSupport` 同口径 |
| Platform · api / app | HTTP 合同 | 新请求 / 响应体；`QueryTemplateColumn` 增 `keyType`、`keyRole`；`QueryParameterSummary` 视需要增 `keyType`；Controller | 代码 | BFF 严格反序列化需同步发布 |
| Platform · 数据库 / 注册 / 语言包 | `mor_platform` | PDManer、schema、增量 DDL、首批键类型 DML、注册块（约 5—6 路由、2 菜单）、ops 语言包 | DDL / DML / 注册 | 块号阶段 3 取 mor-test 现用最大号 + 1（需一次只读查询，见 S-08） |
| Operations BFF | 转发 | 新接口转发；模板 DTO 字段；读超时沿用 9s | 代码 | 与 Platform 同发 |
| Operations Web | 页面 | 联合查询页（分步驱动、去重、护栏、链路图、加载更多）、键类型页、编辑器标注控件、菜单、文案 | 代码 | 先出 Pencil 原型 |
| Gateway | 路由 | 无代码；快照编译新路由（≤10s、≤64KB） | 无 | 离线编译演练 |
| 业务系统 | 被查库 | 无代码；mor-test 只读授权与数据源（F-10） | 环境 | root GRANT 属阶段 7 授权动作 |

## 5. 待决事项

| # | 事项 | 建议 |
|---|---|---|
| S-01 | **执行模型**（F-02、F-03）。A：分步无状态接口——每次请求只展开一层：调用方传本层待查键（已去重），服务端执行该层所有参与模板，返回节点、边与新发现的链路键；Web 维护 visited、层数、累计执行数、总时长并逐层调用，结果逐层出现。B：单次调用、总预算压到约 8 秒，超出即截断。C：异步任务 + 轮询，需要存中间结果（新表或缓存），与“执行不写库”冲突 | **A**。展开规则（参与条件、键规范化、LINK 提取、单步并发与预算）都在 Platform；“下一层查什么”的循环状态在页面。BFF 仍只转发。J9 改述为“展开规则与执行在 Platform，逐层循环由页面驱动”；B8 的 `totalTimeoutSeconds` 改为页面总时长（默认 60 秒，可点“停止”），服务端单步预算 8 秒、单步最多执行 20 次模板、单键值数 ≤ 50；`maxDepth` / `maxTemplateExecutions` 由页面执行，服务端校验单步请求中的 `depth` ≤ maxDepth |
| S-02 | INTEGER 键与 STRING 参数（F-05） | 允许：INTEGER 键可绑 INTEGER 或 STRING 参数（传规范十进制字符串）；STRING 键只绑 STRING |
| S-03 | 屏蔽列作 LINK（F-06） | 保存报错：LINK 列的 mask 必须为 NONE；DIMENSION 列可屏蔽，屏蔽后不提供“另起查询” |
| S-04 | 键类型图缓存（F-07） | 不缓存：每次执行 / 预览现读 ACTIVE 模板与当前版本构图 |
| S-05 | 截断节点（F-08） | 页面内“加载更多”：OFFSET 分页模板用既有执行路由取下一页；NONE 分页模板只显示“已截断（上限 N 行）” |
| S-06 | mor-test 演示范围（F-10） | 演示链路覆盖 acquiring、payment、billing、account、ledger、reconciliation、channel-connector；不含 dispute、payout（键类型仍登记）。阶段 7：root 给 `data_query_demo_reader` 加 `mor_payment`、`mor_billing`、`mor_account` SELECT，登记 3 个 `MOR_*_REPLICA` 数据源，启用 `MOR_CHANNEL_CONNECTOR` |
| S-07 | 单步内同一模板被多个键类型命中 | 分别执行（Q5）；同一 `(模板, 键类型)` 合并 LIST 执行 |
| S-08 | mor-test `mor_platform` 只读核对（数据源清单、注册块最大号） | 阶段 3 需要一次只读 SELECT。我直接读 Platform 库凭据被权限策略拦下；请你在终端执行 `! <命令>`（我给出只读 SQL），或授权我读取 |
| S-09 | 单步并发与线程 | Platform 内有界执行器（8 线程，按请求共享上限），每个模板 `timeoutCapSeconds` = min(8, 单步剩余秒数)；步预算用尽未开始的模板标为未执行 |

## 6. 规模判断

M。Platform：1 张新表、约 6 个新接口（键类型 3、预览 1、执行步 1，键类型列表含详情）、模板 DTO 两字段、核心算法一套；BFF 约 6 条转发；Web 2 个新页面 + 编辑器扩展（链路图画布为主要前端工作量）；DDL 1 表、DML 首批键类型约 24 行、注册 1 块。风险集中在 S-01（执行模型改变需求措辞）与链路图前端实现。


## 7. 方案总览（阶段 3）

> 本次方案规模：涉及 Platform Support、Operations BFF、Operations Web 3 个系统（Gateway 只编译路由）；新增 8 个接口（键类型 5、联合查询 3），修改 4 个既有接口的请求 / 响应字段（模板新建、保存、详情、版本详情增加 `joint` 联合查询配置）；新增 1 张表 17 个字段；修改 1 张旧表（`t_query_template_version` 新增 1 列 `joint_json`）；新增 2 个索引；初始化 DML 键类型 26 行；注册 DML 1 块（7 路由、2 菜单）；Pencil 原型 8 张画板（联合查询页 5 个状态 + 键类型页 + 模板编辑器 1 个页签 + 390 宽）。

```
Web 联合查询页 ──(每层一次)──▶ BFF 同路径转发 ──▶ Platform core: JointQueryStepProcessor
   │  保存 visited / 层数 / 累计执行数 / 总时长                     │ 调用方白名单 + 会话 + DATA_QUERY_EXECUTE
   │  去重后只传本层键                                               ▼
   │                                          data-query 模块: JointQueryStepRunner
   │                                            ├ JointQueryGraphLoader：现读 ACTIVE 键类型 + ACTIVE 模板当前版本 → 图
   │                                            ├ 选出本层参与模板：(模板, 键类型) 合并 LIST
   │                                            ├ 有界执行器并行 → RunDataQueryTemplateProcessor（单步预算 8s）
   │                                            └ 取 LINK 列非空值 → links / discoveredKeys
   ◀──────────── nodes / links / discoveredKeys / truncations ───────────┘
```

- **分层**：会话、白名单、权限、审计在 `-core`；图、参与条件、执行步骤在 data-query 模块（同 REQ-05 的 `RunDataQueryTemplateProcessor` 分工）。键类型写入在 `-core` 的 `AbstractLockProcessor`，本地短事务只写 `mor_platform`。
- **事务**：执行步骤与预览只读 `mor_platform`（不开事务）并访问被查库（事务外）；不写任何表、不登记审计（与单模板执行一致）。键类型写入登记审计。
- **展开规则**（服务端）：参与条件、值规范化、入口正则、LINK 提取、单步并发与预算、单步上限。**循环状态**（页面）：visited、跳数、累计执行数、总时长、停止。
- **联合查询配置与模板分开**（2026-10-29 阶段 3 修订）：模板的 `parameters_json` / `columns_json` 不变；联合查询配置单独存在同一模板版本行的 `joint_json`，编辑器单独页签维护。
- **展示与跳数无关**：链路图按“行主体键类型”定位——泳道 = 该键类型的分组 `group_name`，泳道顺序 = 分组内键类型最小 `sort_no`，泳道颜色按泳道顺序从固定色板依次取（前端不维护系统名或颜色映射，新链路只需新增键类型与分组），泳道内按键类型 `sort_no`；无论从哪个单号进入，同一笔交易画法相同。跳数（离输入几步）只用于执行顺序和节点详情。

## 8. 接口

路径前缀同 data-query：管理 `/data-query-admin/v1`，执行 `/data-query/v1`；ID 与键值一律字符串；写接口带 `expectedVersion`，冲突 409；操作人 Header 同既有。

### 8.1 键类型管理（5 个）

| # | Method + Path | 权限 | 说明 |
|---|---|---|---|
| K1 | GET `/data-query-admin/v1/key-types?status=&keyword=&page=&size=` | DATA_QUERY_READ | 分页（`PagingRequestBody`），按 `sort_no, key_type_code`；每项附 `referencedTemplateCount`（ACTIVE 模板当前版本中引用该键类型的数量） |
| K2 | POST `/data-query-admin/v1/key-types` | DATA_QUERY_TEMPLATE_MANAGE | 新建，状态 ACTIVE |
| K3 | PUT `/data-query-admin/v1/key-types/{keyTypeId}` | DATA_QUERY_TEMPLATE_MANAGE | 修改（编码不可改）；被引用时 `valueType` 不能改（400，消息列出前 5 个模板编码）；`groupName`、`defaultRole` 可改（只影响展示与默认值） |
| K4 | POST `/data-query-admin/v1/key-types/{keyTypeId}/status` | DATA_QUERY_TEMPLATE_MANAGE | `targetStatus` + `expectedVersion`；停用时被 ACTIVE 模板引用则 400（同上列出模板） |
| K5 | PUT `/data-query-admin/v1/key-type-order` | DATA_QUERY_TEMPLATE_MANAGE | 拖动排序后保存整体顺序：`items[{keyTypeId, expectedVersion}]`（全部 ACTIVE 与 INACTIVE 键类型的新顺序）；一个事务内按位置重写 `sort_no = 序号 × 10`，任一版本不符 409；锁 `lock:data-query:key-type-order` |

请求体 `CreateQueryKeyTypeRequestBody` / `UpdateQueryKeyTypeRequestBody`：

| 字段 | 类型 | 必填 | 约束 |
|---|---|---|---|
| keyTypeCode | string | 新建必填 | `^[A-Z][A-Z0-9_]{1,63}$`，创建后不可改 |
| keyTypeName | string | 是 | ≤128 |
| groupName | string | 是 | ≤32，业务分组显示名，如“收单”“支付”“入驻”“出款”；同名即同组 |
| valueType | string | 是 | INTEGER / STRING |
| defaultRole | string | 是 | LINK / DIMENSION；只作为编辑器中标注该键类型时的默认角色 |
| entryAllowed | boolean | 是 | — |
| valuePattern | string | 否 | ≤256；保存时 Java 编译通过，且拒绝反向引用 `\1`、`\k<`、前后断言 `(?=` `(?!` `(?<=` `(?<!`、内联标志 `(?i` 等、所有格 / 原子组 `*+` `(?>`；执行时整串匹配 |
| inputHint | string | 否 | ≤128 |
| sortNo | integer | 是 | 0—9999 |
| description | string | 否 | ≤512 |
| expectedVersion | integer | 修改必填 | — |

响应 `QueryKeyTypeResponseBody`：上述字段 + `keyTypeId`、`status`、`version`、`referencedTemplateCount`、`updatedBy`、`updateTime`。

### 8.2 联合查询（3 个）

| # | Method + Path | 权限 | 说明 |
|---|---|---|---|
| J1 | GET `/data-query/v1/joint-query/settings` | DATA_QUERY_EXECUTE | 入口下拉：ACTIVE 且 `entryAllowed` 的键类型（code、name、groupName、valueType、valuePattern、inputHint、sortNo）+ 分组列表 `groups[{groupName, rank}]`（空状态与链路图列头用，前端不写死）+ 护栏 `limits`（maxEntryValues、maxValuesPerKey、maxRowsPerTemplate、maxExecutionsPerStep、maxDepth、maxTemplateExecutions、maxTotalSeconds） |
| J2 | GET `/data-query/v1/joint-query/graph?entryKeyType=` | DATA_QUERY_EXECUTE | 可达预览（按键类型推算，不含跳数编号）：`groups[{groupName, rank, templates[{templateCode, templateName, dataSourceCode, subjectKeyType, triggerKeyType, minSteps, linkKeyTypes[]}]}]`；`breaks[{keyType, reason}]`（可到达但没有任何参与模板以它为触发键，链路在此断开）；`excluded[{templateCode, reasonCode, reason}]`（有 `joint_json` 却不参与的模板） |
| J3 | POST `/data-query/v1/joint-query/steps` | DATA_QUERY_EXECUTE | 执行一层；请求 / 响应见下 |

J3 请求 `JointQueryStepRequestBody`：

```json
{"depth": 0,
 "keys": [{"keyType": "REFUND_REQUEST_NO", "values": ["rfd_7Hk2...Q9x"]}]}
```

- 可选 `templateCodes`（≤20）：只执行这些模板，供页面“重试这一项”；不传则执行本跳全部参与模板。
- `depth` 0—maxDepth-1。`depth = 0`：`keys` 恰好 1 项，键类型 ACTIVE 且 `entryAllowed`，值 1—`maxEntryValues`（20），逐个按值类型与 `valuePattern` 校验；不合法 422，`field` 形如 `keys[0].values[3]`。
- `depth > 0`：`keys` 1—20 项、同一键类型不重复，每项值 1—`maxValuesPerKey`（50）；不做正则校验（值来自上一层结果）；键类型须 ACTIVE。
- 值规范化：INTEGER 去前导零、校验 0—9223372036854775807；STRING 原样，长度 ≤128。规范化后在本次请求内去重。

J3 响应 `JointQueryStepResponseBody`：

```json
{"depth": 0,
 "nodes": [{"nodeId": "0:JQ_REFUND_BY_NO:REFUND_REQUEST_NO",
   "templateCode": "JQ_REFUND_BY_NO", "templateName": "退款申请 by 单号", "versionNo": 2,
   "dataSourceCode": "MOR_ACQUIRING_REPLICA", "subjectKeyType": "REFUND_REQUEST_ID", "groupName": "收单",
   "laneRank": 10, "subjectSortNo": 30, "triggerKeyType": "REFUND_REQUEST_NO",
   "keyParameter": "refundNos", "triggerValues": ["rfd_7Hk2...Q9x"],
   "status": "SUCCEEDED", "errorCode": null, "errorMessage": null,
   "pagingMode": "OFFSET", "truncated": false, "elapsedMillis": 41,
   "columns": [{"name": "order_id", "label": "订单 ID", "keyType": "ORDER_ID", "keyRole": "LINK"}],
   "rows": [["2051...3"]]}],
 "links": [{"nodeId": "0:JQ_REFUND_BY_NO:REFUND_REQUEST_NO", "rowIndex": 0, "column": "order_id",
   "keyType": "ORDER_ID", "value": "2051...3"}],
 "discoveredKeys": [{"keyType": "ORDER_ID", "values": ["2051...3"]}],
 "truncations": [],
 "elapsedMillis": 63}
```

- `nodes[].status`：SUCCEEDED / FAILED（`errorCode` 取 data-query 既有错误码或 `INTERNAL_ERROR`，`errorMessage` 为安全中文消息）/ SKIPPED（单步预算或单步执行数用尽，`errorCode` = `STEP_BUDGET_EXHAUSTED` / `STEP_EXECUTION_LIMIT`）。
- `subjectKeyType` / `groupName` / `laneRank` / `subjectSortNo`：页面定位节点（泳道与泳道内顺序），与 `depth` 无关；`depth` 只作为节点详情“第 n 跳”。
- `truncated`：行数达到 min(maxRowsPerTemplate, 模板上限)，或结果 `hasMore`。
- `links`：每个结果行中 LINK 列的非空值（规范化后），供页面画边（目标节点 = 下一层 `triggerValues` 含该值的节点）。
- `discoveredKeys`：本层 links 按键类型去重；页面再扣除 visited 得到下一层 `keys`。单键类型超过 `maxValuesPerKey` 时截取前 N 个并在 `truncations` 记 `{rule: "MAX_VALUES_PER_KEY", keyType, dropped}`。
- `truncations[].rule`：MAX_VALUES_PER_KEY、STEP_EXECUTION_LIMIT、STEP_BUDGET。页面另行产生 MAX_DEPTH、MAX_TEMPLATE_EXECUTIONS、MAX_TOTAL_SECONDS、USER_STOPPED。
- 结果列沿用模板屏蔽；`rows` 值与单模板执行相同（整数为字符串）。
- 错误：未知 / 停用键类型、参数不合法 422；无权限 403；调用方不在白名单 403；本层没有参与模板时 200 + 空 `nodes`。

### 8.3 既有模板接口的字段增量（4 个接口，兼容）

- `CreateQueryTemplateRequestBody` / `UpdateQueryTemplateRequestBody`：新增可空 `joint`（`QueryTemplateJointConfig`）；`parameters`、`columns` 不变。
  ```json
  "joint": {"subjectKeyType": "PAYMENT_INTENT_ID",
            "keyParameter": {"name": "orderIds", "keyType": "ORDER_ID"},
            "columns": [{"name": "payment_intent_id", "keyType": "PAYMENT_INTENT_ID", "keyRole": "LINK"},
                        {"name": "client_id", "keyType": "CLIENT_ID", "keyRole": "DIMENSION"}]}
  ```
- `QueryTemplateDetailResponseBody` / `QueryTemplateVersionResponseBody`：回显 `joint`；新增 `jointQuery: {participating, reasonCode, reason}`。
- `SaveQueryTemplateResponseBody.warnings`：新增参与情况提示（INFO：“参与联合查询，触发键 ORDER_ID”；WARNING：不参与原因）。
- 试运行接口不变；版本对比把 `joint` 作为独立区块比较；恢复版本连同 `joint_json` 一起恢复。旧客户端不传 `joint` = 不参与（**注意**：旧客户端保存会清空 `joint_json`，因此 Web 与 BFF 同发，BFF 严格模型同步新增字段）。

### 8.4 参与条件与保存校验

- `joint` 为空：模板不参与，不做任何联合查询校验。
- **错误（保存失败，问题定位到 `joint.*` 字段）**：
  - `subjectKeyType`、`keyParameter.keyType`、`columns[].keyType` 不存在或非 ACTIVE（保存时对引用的键类型行加共享锁，与停用互斥）；`subjectKeyType` 必填。
  - `keyParameter.name` 不是已声明参数；参数类型不匹配（INTEGER 键：INTEGER、STRING 或其 LIST；STRING 键：STRING 或其 LIST）。
  - `columns[].name` 重复；`keyRole` 缺失；LINK 列在 `columns` 声明中（不区分大小写）配置了屏蔽。
- **提醒（WARNING，不阻止保存）**：列标为 LINK 但键类型 `default_role = DIMENSION`（如 `client_id`）——“会按该键继续展开，可能查出大量数据”。
- **参与（INFO）/ 不参与（WARNING）**，原因码：`KEY_PARAMETER_NOT_REQUIRED`（键参数不是必填）、`OTHER_REQUIRED_PARAMETER`（其他必填参数无默认值）、`TEMPLATE_INACTIVE`、`DATA_SOURCE_INACTIVE`（预览时）。
- **执行时**：LINK 列不在结果列中 → 节点仍 SUCCEEDED，附 `warnings: ["LINK_COLUMN_MISSING:order_id"]`（服务端保存时不执行查询，编辑器试运行时提示）。
- 参与模板的执行行数：在 `RunDataQueryTemplateRequest` 增加可空 `rowCap`，NONE 分页时 `QueryPaging.none(min(模板 maxRows, 系统上限, rowCap))`；OFFSET / CURSOR 传 `page=1, size=min(rowCap, 模板 maxPageSize)`。

### 8.5 执行步骤与线程

- `JointQueryStepRunner`：构图 → 本层每个键类型找参与模板 → 生成任务（模板 × 键类型，LIST 参数一次传全部值；标量参数逐值一个任务）→ 超过 `maxExecutionsPerStep`（20）的任务标 SKIPPED → 提交执行器。
- 执行器：Platform 内 `dataQueryJointExecutor`（`ThreadPoolTaskExecutor`，线程 `data-query.joint.executor-threads` 默认 16，队列 64，满时拒绝 → 该任务 SKIPPED）；单步并发 Semaphore 8。
- 预算：步开始计时 8 秒（`data-query.joint.step-budget`，不得大于 `data-query.limits.max-timeout`）；每个任务 `timeoutCapSeconds = max(1, min(8, 剩余整秒))`；剩余 < 1 秒时不再启动，标 SKIPPED；全部任务结束或预算耗尽即返回。BFF 读超时 9s、Gateway 10s 不变。
- 操作人：`operatorAccountId` 传会话账号；`callerLabel` = `joint-query`；日志沿用 `DataQueryRunner` 单条元数据日志，另记 `data.query.joint.step{result}` 指标与一条步骤日志（depth、键类型数、任务数、跳过数、耗时；不含键值）。

### 8.6 BFF

`ops-bff`：`DataQueryKeyTypeController`（K1—K4）与 `DataQueryJointController`（J1—J3）同路径转发到 `PlatformDataQueryClient` 新方法；模板模型同步 `joint` / `jointQuery` 字段；无聚合逻辑；读超时沿用 `operations.data-query-read-timeout` 9s；`application.yml` 白名单前缀已含 `/data-query/v1`，补 `/data-query-admin/v1/key-types`。

### 8.7 已加载对象跳过（2026-10-30 阶段 7 验收后修订）

**问题**：每种对象既有“按别的单号查”的模板（订单按订单号、会话按订单），也有“按自身 ID 查”的模板（订单按订单 ID，供从下游反查上游）。正向展开时前者已查到某行，带出的自身 ID 又触发后者，把同一行再查一遍。mor-test 订单号入口 24 次执行中约 7 次是这种重复，既干扰阅读，也占用单跳 20 次 / 总计 60 次的护栏额度。原有去重只按“触发键类型 + 值”，不知道“这个对象的这一行已经拿到”。

**规则**：
- “按自身 ID 查”的模板 = 触发键类型等于行主体键类型（`triggerKeyType == subjectKeyType`）的参与模板。只有这类模板会被跳过；其他模板（按订单查退款 / 计费 / 支付等）照常执行，链路不受影响。
- 页面维护“已加载对象” `(subjectKeyType, 值)`：每个 SUCCEEDED 节点的结果行中，标注了 `keyType == 该节点 subjectKeyType` 的列的非空值（规范化后）。
- J3 请求新增可空 `loadedSubjects: [{keyType, values[]}]`（键类型 ≤ 30、每类值 ≤ 200）。服务端对“按自身 ID 查”的任务剔除已加载的值；剔除后为空则不执行，不计入单步执行数与预算；部分剔除则只用剩余值执行。
- J3 响应新增 `alreadyLoaded: [{templateCode, templateName, keyType, values[]}]`：因已加载而未执行的“按自身 ID 查”视图。
- 去重以“对象”为粒度（跨模板）。被跳过的模板列可能更多，因此不丢弃而改为**按需查看**：节点明细显示“另有 N 个视图未执行”，点击后用既有 `templateCodes` 机制、不带 `loadedSubjects` 单独执行该模板，结果显示在该节点明细中，不新增链路图节点、不继续展开。
- 入口跳（depth 0）与 `templateCodes` 指定的执行不做跳过。

**补充（2026-10-30 修订 b 验收后，修订 c）**：
- 修订 b 只消除“按自身 ID 查”造成的重复。同一对象经不同关系路径再次查到（如订单号入口里计费事实经“按订单”“按支付尝试”各到达一次；会计凭证 ID 入口里凭证 → 记账批次 → 计费事实 → 记账批次 → 凭证的回环）属于“按别的键查”，服务端执行前无法预知结果是否为已显示对象，跳过规则不覆盖。
- **页面按对象合并显示**：每跳返回后，页面按节点行主体值（`subjectKeyType` 列）去掉已在其他节点显示过的行；去重后没有新行的节点不在链路图单独绘制，改在首次显示该对象的节点明细中注明“也可经 <模板 / 路径> 到达”。查询仍执行（服务端跳过规则继续节省“按自身 ID 查”的预算）。表格、计数、未查全汇总按去重后的结果计算。
- **模板约定**：“查出来的每一行是”必须是该行自身的身份。子行模板（分录、明细）不能用父对象的键类型作行主体，否则会被误判为“按自身 ID 查”而跳过。为此新增 3 个键类型：`ACCOUNT_ENTRY_ID`（账户分录 ID，账户）、`JOURNAL_LINE_ID`（凭证分录 ID，总账）、`STATEMENT_LINE_ID`（对账单明细 ID，对账），默认角色 LINK、不作入口；初始化数据随之由 26 个增至 29 个。

**同时修复**：1440 宽度下 7 个分组时最后一列被截（首次渲染适配可视区域）；入口节点 0 行时显示“起点 · 未查到”而非“起点 · 0”。

**后续优化（不在本次）**：一个模板支持多个键参数（一种对象一个模板，同时按订单号 / 订单 ID / 客户订单号查），减少模板数量；届时本规则仍适用。

## 9. 数据设计

### 9.1 DDL（`mor_platform`，新增 1 表）

```sql
CREATE TABLE `t_query_key_type` (
  `key_type_id` BIGINT NOT NULL COMMENT '服务侧生成的查询键类型 Snowflake 主键。',
  `key_type_code` VARCHAR(64) NOT NULL COMMENT '全局唯一的键类型编码，创建后不可修改；模板参数与列声明的 keyType 引用它。格式为大写字母开头的大写字母、数字与下划线。',
  `key_type_name` VARCHAR(128) NOT NULL COMMENT '在 Operations 页面显示的键类型名称。',
  `group_name` VARCHAR(32) NOT NULL COMMENT '业务分组显示名，如 收单、支付、入驻、出款；以该键类型为行主体的节点画在同名分组的泳道，分组顺序取组内最小 sort_no，颜色按分组顺序取色板。',
  `value_type` VARCHAR(32) NOT NULL COMMENT '键值类型，决定入口值规范化与可绑定的参数类型。允许值：INTEGER、STRING。',
  `default_role` VARCHAR(32) NOT NULL COMMENT '在模板列上标注该键类型时的默认角色；列上的 keyRole 才是实际角色，可与默认值不同。允许值：LINK、DIMENSION。',
  `entry_allowed` TINYINT NOT NULL DEFAULT 1 COMMENT '是否出现在联合查询入口下拉：1 是，0 否。',
  `value_pattern` VARCHAR(256) NULL COMMENT '入口值的校验正则，整串匹配；为空不校验格式。只校验用户在入口输入的值，不校验展开得到的值；只允许 Java 与 JavaScript 行为一致的语法。',
  `input_hint` VARCHAR(128) NULL COMMENT '入口输入框占位文字与格式不匹配时的提示。',
  `sort_no` INT NOT NULL COMMENT '业务流程顺序号，升序；决定入口下拉与列表顺序，以及链路图泳道（分组取组内最小值）与泳道内节点顺序；页面拖动排序整体重写。',
  `description` VARCHAR(512) NULL COMMENT '键类型含义等补充说明。',
  `status` VARCHAR(32) NOT NULL COMMENT '键类型状态；被 ACTIVE 模板当前版本引用时不能停用。允许值：ACTIVE、INACTIVE。',
  `version` INT NOT NULL DEFAULT 1 COMMENT '键类型乐观并发版本，从 1 开始，每次修改或启停加一。',
  `created_by` BIGINT NOT NULL COMMENT '创建该键类型的操作人，取值为 t_user_account.account_id；初始化数据为系统账号。',
  `updated_by` BIGINT NOT NULL COMMENT '最后修改该键类型的操作人，取值为 t_user_account.account_id。',
  `create_time` DATETIME(6) NOT NULL COMMENT '该记录在 Platform Support 数据库中的 UTC 微秒创建时间。',
  `update_time` DATETIME(6) NOT NULL COMMENT '该可变记录最后一次持久化更新的 UTC 微秒时间。',
  PRIMARY KEY (`key_type_id`),
  UNIQUE KEY `uk_query_key_type_code` (`key_type_code`) COMMENT '保证业务唯一性：key_type_code',
  KEY `idx_query_key_type_status` (`status`, `sort_no`) COMMENT '支持访问路径：status、sort_no'
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci COMMENT='联合查询的业务键类型注册表；模板版本的 joint_json 按编码引用，用于推导跨模板的展开路径与链路图位置。';

ALTER TABLE `t_query_template_version`
  ADD COLUMN `joint_json` JSON NULL COMMENT '联合查询配置：行主体键类型 subjectKeyType、键参数 keyParameter{name,keyType}、列标注 columns[{name,keyType,keyRole}]；为空表示该版本不参与联合查询。与正文、参数、列声明同版本保存，不可变。' AFTER `constraints_json`;
```

- `sort_no` 语义修订：键类型的业务流程顺序，同时决定入口下拉顺序与链路图泳道顺序。


- PDManer：`data-query` 图组加表，`t_query_template_version` 加列；`platform-support-schema.sql` 追加；增量 `docs/database/sql/changes/2026-10-29-joint-query.sql`，回滚 `...-rollback.sql`（`ALTER TABLE ... DROP COLUMN joint_json` + `DROP TABLE`，会丢失已填的联合查询配置；执行前确认并先回退应用）。
- 历史版本 `joint_json` 为空，不迁移。

### 9.2 初始化 DML（键类型 26 行，写入 Platform 初始化 DML 单一文件）

`sort_no` 按业务流程编号；泳道顺序 = 分组内最小 `sort_no`：收单 10 → 支付 40 → 渠道 75 → 争议 90 → 计费 100 → 账户 110 → 总账 120 → 出款 130 → 对账 140 → 归属维度 900。入驻、税务等链路以后按需新增分组与键类型，不改代码。

| sort | 编码 | 名称 | 分组 | 值 | 默认角色 | 入口 | 正则 |
|---|---|---|---|---|---|---|---|
| 10 | CHECKOUT_SESSION_ID | 收银台会话 ID | 收单 | INTEGER | LINK | 1 | `[0-9]{1,19}` |
| 20 | ORDER_ID | 交易订单 ID | 收单 | INTEGER | LINK | 1 | `[0-9]{1,19}` |
| 21 | ORDER_NO | 交易订单号 | 收单 | STRING | LINK | 1 | `ord_([0-9A-Za-z]{30}\|[A-Za-z0-9_-]{22})` |
| 22 | CLIENT_ORDER_ID | 客户订单号 | 收单 | STRING | LINK | 1 | `[!-~]{1,128}` |
| 30 | REFUND_REQUEST_ID | 退款申请 ID | 收单 | INTEGER | LINK | 1 | `[0-9]{1,19}` |
| 31 | REFUND_REQUEST_NO | 退款单号 | 收单 | STRING | LINK | 1 | `rfd_[0-9A-Za-z]{30}` |
| 40 | PAYMENT_INTENT_ID | 支付意图 ID | 支付 | INTEGER | LINK | 1 | `[0-9]{1,19}` |
| 50 | PAYMENT_ATTEMPT_ID | 支付尝试 ID | 支付 | INTEGER | LINK | 1 | 同上 |
| 60 | PAYMENT_ACTION_ID | 支付动作 ID | 支付 | INTEGER | LINK | 1 | 同上 |
| 65 | CHANNEL_REFUND_ID | 渠道退款 ID | 支付 | INTEGER | LINK | 1 | 同上 |
| 70 | CHANNEL_TRANSACTION_REF_ID | 渠道交易引用 ID | 支付 | INTEGER | LINK | 1 | 同上 |
| 75 | CHANNEL_INTERACTION_ID | 渠道交互 ID | 渠道 | INTEGER | LINK | 0 | 空 |
| 80 | EXTERNAL_TRANSACTION_ID | 渠道交易号 | 渠道 | STRING | LINK | 1 | 空 |
| 90 | DISPUTE_CASE_ID | 争议案件 ID | 争议 | INTEGER | LINK | 1 | `[0-9]{1,19}` |
| 100 | BILLING_FACT_ID | 计费事实 ID | 计费 | INTEGER | LINK | 1 | 同上 |
| 110 | ACCOUNT_POSTING_ID | 记账批次 ID | 账户 | INTEGER | LINK | 1 | 同上 |
| 120 | JOURNAL_ENTRY_ID | 会计凭证 ID | 总账 | INTEGER | LINK | 1 | 同上 |
| 130 | PAYOUT_ID | 出款 ID | 出款 | INTEGER | LINK | 1 | 同上 |
| 140 | PLATFORM_TXN_ID | 平台流水 ID | 对账 | INTEGER | LINK | 0 | 空 |
| 900 | CLIENT_ID | 客户 ID | 归属维度 | INTEGER | DIMENSION | 1 | `[0-9]{1,19}` |
| 910 | ENTITY_NO | 运营主体编号 | 归属维度 | STRING | DIMENSION | 1 | `[A-Z0-9]{5}` |
| 920 | LOCAL_PARTNER_ID | 本地合作方 ID | 归属维度 | INTEGER | DIMENSION | 0 | 空 |
| 930 | CHANNEL_ID | 渠道 ID | 归属维度 | INTEGER | DIMENSION | 0 | 空 |
| 940 | CHANNEL_ACCOUNT_ID | 渠道账号 ID | 归属维度 | INTEGER | DIMENSION | 0 | 空 |
| 950 | MID_ID | MID ID | 归属维度 | INTEGER | DIMENSION | 0 | 空 |
| 960 | CLEARING_ACCOUNT_ID | 清算账户 ID | 归属维度 | INTEGER | DIMENSION | 0 | 空 |

- 正则为整串匹配，存储时不带 `^$`。`PAYOUT_NO` 暂不登记（mor-test 无 payout，生成规则未核对）。ID 段与主键取 Platform ID 块（同注册块号）。
- `CHANNEL_INTERACTION_ID`、`PLATFORM_TXN_ID` 主要作为行主体（渠道交互、平台流水 / 渠道账单行的分组归属），不作入口。
- 默认维度的键类型（客户、主体、渠道、MID、清算账户）放在“归属维度”分组；它们一般不作行主体，所以通常不形成泳道。入驻类模板可把 `client_id` 标为 LINK。
- 维度键可作入口：以维度键为入口时，入口层按该键查（如“客户的最近订单”模板），之后的 LINK 照常展开。
- 双向展开前提：每种链路键至少有一个“按自身 ID 查”的参与模板（如“支付意图 by ID”带出 `order_id`），否则该方向断开；J2 `breaks` 列出断点。演示模板按此补齐。

### 9.3 注册 DML（块 N）

- 2026-10-29：块 143 已被 REQ-03（risk-decision 承保记录）占用并正在发布 mor-test，本需求不早于 144。
- N = mor-test `mor_platform` 现用最大块号 + 1（S-08：阶段 3 确认时请执行第 9.4 节只读 SQL，或授权我读取）。格式沿用块 125 / 131：路由与菜单 DISABLED，另附激活与回滚脚本；`platform-support-initial-data.sql` 末尾追加。
- 路由 8 条：K1—K5 绑定块 131 既有权限（READ 131101 / TEMPLATE_MANAGE 131103），J1—J3 绑定 EXECUTE 131104；响应超时 10000ms；J3 请求体上限 65536；Target 同块 131。
- 菜单 2 项（父 131301“数据查询”）：“联合查询” `/data-query/joint-query`（sort 5，资源权限 EXECUTE），“键类型” `/data-query/key-types`（sort 30，资源权限 READ）。
- 不新增权限；管理员角色无需新授权。

### 9.4 只读核对 SQL（mor-test，阶段 3 需要）

```sql
SELECT data_source_code, engine_type, database_name, username, status FROM mor_platform.t_data_source ORDER BY 1;
SELECT MAX(FLOOR(route_id / 1000) % 1000) AS max_route_block FROM mor_platform.t_gateway_route WHERE route_id >= 900000000000000000;
SELECT MAX(FLOOR(console_resource_id / 1000) % 1000) AS max_menu_block FROM mor_platform.t_console_resource WHERE console_resource_id >= 900000000000000000;
```

（列名阶段 4 前按 schema 再核一次。）

## 10. 配置与文案

- `data-query.joint.*`（Platform，修改需重启）：`max-entry-values` 20、`max-values-per-key` 50、`max-rows-per-template` 100、`max-executions-per-step` 20、`max-depth` 6、`max-template-executions` 60（页面执行，经 J1 下发）、`max-total-seconds` 60（页面执行，经 J1 下发）、`step-budget` 8s、`step-concurrency` 8、`executor-threads` 16、`executor-queue` 64。启动校验：`step-budget` ≤ `data-query.limits.max-timeout`，各值为正。
- 文案：operations-console `data-query` 命名空间新增 joint / keyType 键（en 基准 + zh-CN）；服务端错误消息沿用中文。语言包随阶段 7 发布。

## 11. 页面（原型：`operations-console-web/docs/operations-console.pen` “数据查询 · 联合查询 01—08”，y=84300、x=4040—18580；导出见 [prototypes/](./prototypes/)）

设计原则（2026-10-29 用户要求“尊重简洁、尊重用户体验”）：只用业务语言（订单、支付、记账……），模板编码、键类型编码、跳数、错误码收进“技术详情”或悬停；一个组合搜索框；链路总览只负责导航，点节点在下方看明细，不重复列表；问题汇总成一行，可展开；能让用户自己解决的给出操作（重试、筛客户、以此查询）。

| 画板 | 内容 |
|---|---|
| 01 入口 · 空状态 | 组合搜索框（单号类型 ▾ / 输入 / 查询），类型菜单分“常用 / 更多”、带输入提示、可搜索；空状态以 7 个系统示意可查范围，“能查到哪些数据？”打开覆盖范围 |
| 02 结果 · 总览与明细 | 一句话概要（找到 n 项、涉及几个系统、耗时）；链路总览按系统分列、节点只显示名称与条数、起点标注；选中节点高亮其来源路径，下方显示该节点明细（路径、表格、链路值可“定位”、超出“显示全部”），可切换“全部 n 项” |
| 03 结果 · 未查全 | 概要旁“3 处未查全 ▾”展开为三行（失败 / 部分 / 未查）；命中多个客户时可按客户筛选；失败节点的明细给出原因与“重试这一项”“技术详情” |
| 04 键类型 | 按系统分组的列表，拖动排序（顺序决定链路图位置）；编辑抽屉只保留名称、入口开关、输入提示，格式校验折叠为可选项并可现场试；被使用时锁定值类型与角色，停用置灰 |
| 05 模板编辑器 · 联合查询页签 | 参数、约束、列页签不变；联合查询页签：参与开关、“查出来的每一行是”、“按什么查”（参数 → 单号类型）、“结果里带出的单号”（列 → 单号类型 + 链路 / 维度），错误就地提示，底部一句话说明保存后的效果 |
| 06 390 宽 | 搜索框上下排布；结果按系统折叠卡片，点行进入明细；“查看链路图”切换 |
| 07 从支付尝试进入 | 同一笔交易从下游进入，订单、收银台会话仍在最左；明细中维度值（客户）可“以此查询” |
| 08 格式提示与覆盖范围 | 多行输入中不合格的那一行标红并就近说明；覆盖范围抽屉按系统列出能查到的数据和暂时查不到的（断点） |

## 12. 研发与验证计划

| # | 系统 | 内容 | 验收 |
|---|---|---|---|
| T1 | Platform data-query | 键类型 DO / Mapper / Repository / Service；`QueryKeyTypePatternRules`（正则子集） | AC-01 |
| T2 | Platform data-query | `joint_json` 读写（`QueryTemplateJson`）、版本对比 / 恢复包含 `joint`；保存校验（8.4）、参与评估 `JointQueryParticipation`；保存对引用键类型加共享锁 | AC-02 |
| T3 | Platform data-query | `JointQueryGraphLoader`（含泳道顺序与断点）、`JointQueryStepRunner`、`rowCap`、执行器与预算 | AC-03—AC-09 |
| T4 | Platform core / api / app | 键类型管理 5 Processor（含整体排序）（锁 `lock:data-query:key-type:{id}`、审计）、联合查询 3 Processor、DTO、Controller、配置 | AC-01、AC-10、AC-11 |
| T5 | Platform DB | PDManer、schema、增量 / 回滚 DDL、初始化 DML（键类型、注册块 N 末尾 DISABLED） | 模型与 Schema 一致性测试 |
| T6 | Operations BFF | 7 条转发、模板模型字段 | BFF 测试 |
| T7 | Operations Web | 联合查询页（组合搜索、分步驱动、visited、护栏、链路总览 `@xyflow/react` 按系统分列 + 选中路径高亮、节点明细、重试、按客户筛选、覆盖范围抽屉）、键类型页、编辑器“联合查询”页签、菜单、文案 | AC-V1—AC-V6 |
| T8 | 测试 | Platform auto：以 HTTP 入口为主，本地 `mor_data_query_fixture` 建一组链路表（订单 → 意图 → 动作 → 外部号，含 VARCHAR 存 ID、维度列、屏蔽列、慢查询）覆盖 AC-03—AC-11；Web 单测覆盖驱动循环与去重 | `UNIT_TEST_GUIDE` |
| T9 | 阶段 7 环境 | mor-test：GRANT 3 库、登记 3 数据源、启用 `MOR_CHANNEL_CONNECTOR`、JQ_* 演示模板、块 N、语言包 | AC-04 真实链路 |

本机验证：auto 库 `mor_platform` 与 fixture 库执行增量 DDL / 测试表（阶段 4 授权项）；只使用 `mor-local-mysql-1`。

## 13. 风险与取舍

- 页面驱动循环意味着累计护栏可被绕过（直接调用 J3 多次）；但每步仍受服务端上限与 `DATA_QUERY_EXECUTE` 约束，与单模板执行能力相同，不扩大权限面。
- 不缓存图：每步多 2 条 `mor_platform` 读；模板量增长到上千时再引入派生索引或缓存。
- NONE 分页模板在联合查询中只取前 100 行且不能加载更多；建议参与模板使用 OFFSET 分页（编辑器提示）。
