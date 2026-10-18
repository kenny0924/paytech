# 规则集列表优化：差异分析与技术方案

状态：2026-09-11 阶段 3 方案已获用户确认。第 1–9 节是阶段 2 历史诊断，第 10 节起为本轮设计；明确选择的方案取代前文对应备选。实现、验证与 SQL 执行只见交付记录和当前台账。

输入：[requirement.md](./requirement.md) 的 AC-01 至 AC-08。确认及诊断证据见 [delivery.md](./delivery.md)；历史实现/部署状态仍由 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 第 15–16 节维护。

## 1. 结论

本轮涉及 Web、Risk、Operations BFF 三个应用的代码，以及 Platform 的权限/路由注册数据。Gateway 预计复用动态路由机制，需验证新配置，没有发现必须新增运行算法的依据。Risk 需要持久表达规则集整体的管理生命周期，最终表结构及迁移在阶段 3 定义。

工作量主要在规则集分页、整体删除的使用保护/历史兼容，以及 Web 导航。移除说明与表格列、增加菜单只是其中较小的一部分。继续在原 V1 上调整，不新增 V2 页面，不扩展规则执行、Shadow、审批或激活流程。

## 2. 编码查询：复现及边界

通过 `http://localhost:28080` 的实际 Gateway/BFF/Risk 链路，使用既有 test 数据查询：

| 场景 | 实际结果 | 判断 |
|---|---|---|
| 默认列表 | HTTP 200，总数 23 | 当前按版本统计 |
| 合法且存在的完整大写编码 | HTTP 200，返回匹配记录 | 已测样例正常 |
| 同一编码与状态组合 | HTTP 200 | 该组合未复现故障 |
| 合法但不存在的编码 | HTTP 200，空列表、总数 0 | 正常空结果 |
| 将存在编码转为小写 | HTTP 400，`INVALID_REQUEST`，`请求参数不正确` | 已复现格式错误被笼统展示 |
| 数字开头 `123` 或含横杠 `TEST-CODE` | 同上 | 当前合同不接受该格式 |
| 清空编码条件 | HTTP 200，恢复总数 23 | 清空正常 |

浏览器同样复现了小写查询的 400；输入框旁没有格式错误提示，页面将其作为列表加载失败展示。BFF 同一 Request ID `c273d714-de2a-4c5b-a7d3-4a25f0f28dfa` 的日志有 `HandlerMethodValidationException` 和 400，定位到入口参数校验。

[RuleSetTab.tsx](../../../operations-console-web/src/risk/RuleSetTab.tsx) 只有 `maxLength` 与提交时 `trim`，没有格式校验。[BFF Controller](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/ext/rest/RiskDecisionConfigurationController.java) 要求 `^[A-Z][A-Z0-9_]{0,63}$`：大写字母开头，后续为大写字母、数字、下划线，总长 1–64。[Risk Controller](../../../paytech/risk-decision/paytech-risk-decision-app/src/main/java/com/paytech/risk/ext/rest/decision/DecisionRuleSetManagementController.java) 也校验 Upper Code。

test Platform 参数白名单实际包含 `page / size / pointCode / ruleSetCode / status`，不能把已复现问题归因于 Gateway 缺少 `ruleSetCode` 注册。修复方向是前端就地校验、明确提示，并保留服务端校验及完整编码精确匹配。

**原始样例仍待补充：** 已通过会话询问用户当时输入的具体编码和报错。当前能确认上述缺陷，但不能断言它是原始报错的唯一原因，也不能据一个合法样例声明所有编码均正常。若用户提供的是合法编码的另一故障，应更新本节及影响范围。

## 3. 浏览器回退：已复现根因

| 操作 | 地址 / 历史证据 | 实际结果 |
|---|---|---|
| 审计日志 → 规则列表 | `/audit` → `/risk/rule-configuration?tab=rule-sets`，历史长度 2 → 3 | 外层页面正常入栈 |
| 列表 → 详情 | URL 不变，历史长度仍为 3 | Drawer 打开，未新增内部历史 |
| 从详情回退 | 回到 `/audit` | 跳过列表，复现用户问题 |
| 随后前进 | 回到列表，详情未打开 | 详情视图丢失 |
| 详情 → 编辑未修改 → 回退 | 编辑时 URL/历史长度不变；回到 `/audit` | 跳过详情与列表 |
| 随后前进 | 回到列表，编辑器未打开 | 编辑视图丢失 |
| 编码筛选 → 详情 → 编辑 → 返回列表 | 编码输入恢复为空 | 筛选状态丢失 |
| 有未保存修改 → 回退 → 留在当前页 | 仍在规则页，草稿内容保留 | 现有离开保护有效 |
| 再次回退 → 确认离开 | 回到 `/audit` | 确认没有修复缺失历史 |
| 列表 → TEST Candidate → 回退 | Tab 更换查询参数，历史长度不增加；回到 `/audit` | Tab 使用替换导航 |

[RiskRuleConfiguration.tsx](../../../operations-console-web/src/pages/RiskRuleConfiguration.tsx) 将详情选择、编辑器保存为 React 状态；Tab 使用 `navigate(..., { replace: true })`。因此浏览器只有外层页面历史，没有列表→详情→编辑的记录。[usePaging.ts](../../../operations-console-web/src/hooks/usePaging.ts) 将筛选/分页保存在组件内存；列表在编辑时卸载，再挂载后恢复默认。

修复需要明确的列表/详情/编辑导航状态，并保存来源、列表筛选和分页。实际进入新视图增加历史，地址规范化和旧 Candidate 地址收敛才使用替换。不能无条件把 Back 固定跳到列表，否则会破坏详情→编辑的返回及直接打开页面时的正常历史。URL 只放非敏感定位/筛选，不放编辑内容或命令 Payload。

现有 [useUnsavedRuleGuard.ts](../../../operations-console-web/src/risk/useUnsavedRuleGuard.ts) 应保留；其职责是离开保护。刷新恢复、直接打开编辑页、前进恢复、页面返回按钮与浏览器 Back 的区别在阶段 3 定义。

## 4. 逐项差异与处理方向

| 需求 | 当前行为 / 缺口 | 目标与处理方向 |
|---|---|---|
| AC-01 蓝色说明 | 非编辑状态渲染共享 Alert | Web 移除指定说明并调整间距，其他错误提示保留 |
| AC-02 编码查询 | 合法样例成功；非法格式提交后泛化报错 | 就地格式校验/反馈；继续核对用户原始样例 |
| AC-03 整体删除 | API 无删除命令；仅有版本状态 | Risk 增加整体生命周期、实际使用检查、历史保护及命令；BFF 转发；Platform 注册 |
| AC-04 回退与前进 | 内部视图没有历史；列表状态随卸载丢失 | Web 导航、筛选/分页恢复与离开保护共同调整 |
| AC-05 内容摘要 | 列表渲染 ContentHash | 移除列表列；详情及保存并发所需 Hash 保留 |
| AC-06 操作菜单 | 每个版本只有查看详情；编辑先打开详情 | 点击/悬停菜单，含详情/直接编辑/红色删除；复用原保存新版本命令 |
| AC-07 一集一行 | 版本 ID 为行标识；SQL 逐版本 COUNT/LIMIT | 按规则集确定当前配置、过滤/统计/分页；同步 BFF、Web 并移除业务版本列 |
| AC-08 Candidate 延期 | Tab、详情创建、校验及恢复入口仍可见 | 隐藏相关 UI，旧地址落到规则列表；保留后端能力、权限与历史 |

## 5. Risk 当前不能直接满足的能力

### 5.1 规则集分页和当前配置

[DecisionRuleSetMapper.xml](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/resources/mapper/decision/DecisionRuleSetMapper.xml) 的 `RuleSetListFilter / selectRuleSetVersions / countRuleSetVersions` 直接查询版本表，按版本创建时间排序。本次只读查询 test 库得到 **23 个版本、21 个 `(decision_point_id, rule_set_code)` 组合**；HTTP 按 20 条读取的一页只有 18 个不同规则集。因此不能只在前端对一页去重。

后续查询需先确定每集当前配置，再应用状态筛选及稳定分页。例如旧版 FROZEN、最新 DRAFT 时，不能为了匹配 FROZEN 筛选而选回旧版。实际使用状态独立检查整个版本集合。

存在两个隐藏消费者必须同步：

- [ruleSetHead.ts](../../../operations-console-web/src/risk/ruleSetHead.ts) 按每页 200 条遍历版本列表、校验总数与唯一性、选最高版本再读详情；新建编码检查、编辑、冻结和冲突恢复依赖它。改变列表语义时必须明确兼容或改写方式。
- [BFF Client](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/integration/impl/RiskDecisionConfigurationClientImpl.java) 校验版本 ID、创建时间排序及过滤条件。新投影/排序必须同步类型和严格校验，不能只改 Risk SQL。

### 5.2 整体生命周期与历史

[现有 Schema](../../../paytech/risk-decision/paytech-risk-decision-test/src/test/resources/init/ddl.sql) 只有版本表及绑定，没有规则集根记录。`DRAFT / FROZEN / RETIRED` 是版本状态，旧草稿被新草稿替代也会退役，不能把 RETIRED 偷换为“整个规则集已删除”。

建议阶段 3 设计明确的规则集管理生命周期与稳定身份，与不可变版本分开；是否新增根表及如何回填由模型评审决定。[Risk 数据库规范](../../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md) 不使用通用软删除，应以有明确业务含义的状态表达退出管理，而非为所有历史记录泛加 `is_deleted`。

不采用物理删除整个版本闭包：它会破坏历史发布、检查证据、内容 Hash 和成功命令回放。删除后列表、详情编辑及新命令都应受整体生命周期约束，避免旧版本重新出现或旧编辑窗口继续保存。

### 5.3 实际使用保护

关系链为 `t_decision_release.point_version_id → t_decision_point_rule_set.rule_set_version_id → t_rule_set_version`。判断应覆盖该规则集所有版本与所属库内相关发布环境，不能只看最新版本、当前页面环境或 FROZEN 状态。

现有发布模型包含 `ACTIVE` 和 `active_slot`。本次 test 数据只有 2 条 DRAFT、7 条 VALIDATING 的 TEST Candidate，没有 ACTIVE 发布；这只是诊断时快照，不能据此硬编码为未使用，也不表示激活流程已实现。后续设计需明确状态/槽位一致性与异常数据处理，无法可靠判断时不得放行删除。

Candidate 与历史失效发布不独立构成使用中。删除后保留其固定配置、检查和审计，同时禁止从已删除规则集创建新配置/候选。冻结继续保持 CONFIGURATION_ONLY 原有门禁，不扩展执行。

### 5.4 并发与幂等

[现有 Processor](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/service/processor) 编排本地事务和 Risk 自有幂等；保存/冻结锁决策点、当前版本和必要绑定。Candidate 创建也锁关联资源，已有校验则读取固定闭包。

删除需与保存、冻结、新建 Candidate 采用一致的资源协调和事务顺序，在命令执行时重新检查使用事实。列表返回的可删除提示不能代替最终检查。锁范围、CAS 和必要数据库保护在阶段 3 按规范设计，不借此迁移 Risk 幂等框架。

历史成功命令依靠稳定资源引用回放。管理入口隐藏已删除对象后，历史闭包和成功回放仍需可用，但不能重新激活规则集。新命令拒绝、重复删除、超时重试、旧成功回放及并发配置变化应分别定义。

## 6. 每个系统的影响

| 系统 / 工件 | 影响点与改动类型 | 依赖及兼容风险 |
|---|---|---|
| operations-console-web | 列表/计数、格式校验、操作菜单/直接编辑/删除、路由及列表状态、Candidate 隐藏、类型与响应校验、交互测试 | 与新 BFF 合同同步；保留未保存保护及命令重试上下文 |
| risk-compliance-service | `risk-api` 合同；`risk-web` 命令/错误/幂等；`risk-decision` 列表/生命周期/使用保护/历史/并发；Mapper、模型、SQL 和测试 | 核心 Owner；不能改写不可变内容或引入真实执行 |
| operations-console-bff | DTO、Client、Controller/适用处理层、严格投影校验、删除授权/身份/幂等/错误转发、协议测试 | 依赖更新的 Risk API；保持无状态，使用判断和删除事务在 Risk |
| platform-support-service | 删除专用 Permission、按钮 Console Resource、角色授权及 Route/子配置；新查询 Route 视方案而定 | 初始化/增量 DML 和合同验证；预计无需新业务 Java；编辑复用 SAVE，Candidate 权限/路由保留 |
| api-gateway | 接收 Platform Snapshot，校验/转发新 Method、Path、参数及头 | 预计配置兼容验证/fixture；复用 BFF Target，不增加风控判断逻辑 |
| Risk 模型及根文档 | PDManer、数据库说明、V1 合同与需求/时序入口 | 阶段 3 及以后按批准方案同步；分析稿不替换现行合同 |
| 环境/验收脚本 | 两库增量前置检查、权限曝光、新数据样例、公开入口验收与发布清单 | 复用现有基础设施；本阶段不迁移、不部署 |
| Commerce、Payment、Finance、CCT、Vault | 未发现本轮需要改变的接口或业务事实 | 预计无改动；本期是 Risk 配置管理 |

## 7. API、DDL 与 DML 的设计输入

### API 影响

| 能力 | 已确定的影响 | 阶段 3 需要定义 |
|---|---|---|
| 规则集分页 | 必须提供规则集级投影 | 原 V1 查询修改或新增投影的选择；旧消费者兼容、身份/当前配置、筛选/排序/计数及操作提示 |
| 整体删除 | 至少新增一个 Owner 命令及对应 BFF 公开入口 | Method/Path、目标、并发预期、幂等、回执、使用中/已删除/无权限/冲突等错误 |
| 直接编辑 | 复用原保存新版本 API，不需要单独新增编辑保存 API | 当前配置读取与跳转；独立当前配置查询是否必要 |
| 保存/冻结 | 原合同为基础，增加整体生命周期检查 | 新命令与历史成功回放的区别 |
| Candidate | 保留后端，隐藏 UI | 已删除规则集的新建候选限制；历史详情/校验/回放的兼容 |
| Authoring Context / 动作目录 | 本期未改变配置内容和目录 | 预计不改；不新增标签/拒绝字典 |

不能沿用上一需求“新增 API、注册 DML 为 0”的结论。本轮新增删除命令已确定，查询 API 及公开 Route 的确切数量在阶段 3 收敛；仍在原 V1 内演进。

### 数据库影响

| 所属系统 / 目标库 | DDL | DML |
|---|---|---|
| Risk / `mor_risk` | 生命周期持久结构及必要索引，最终表/字段数量待模型设计 | 历史规则集身份/当前配置回填及核验取决于结构；保留旧版本、Hash、发布/检查和幂等记录 |
| Platform / `mor_platform` | 现有表可承载，预计无需新增结构 | `t_permission`、适用 `t_role_permission`、`t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_guard_rule`；新查询参数涉及 `t_gateway_route_query_param` |
| Web / BFF / Gateway | 不新增业务数据库 | Gateway 消费 Platform 配置 |

后续按两库分别整理 `db/risk-compliance-service/` 与 `db/platform-support-service/`，定义唯一执行来源、标识、前置检查、回填、完整授权/Guard/头规则与核验。只插 Route 不足以完成 API 注册。本阶段未生成可执行 SQL，旧 Candidate 注册数据不删除。

## 8. 风险与验证重点

| 风险 | 后续验证要求 |
|---|---|
| 漏行/错误分页 | 多版本跨页、规则集总数、当前状态过滤、稳定排序、不同决策点同编码隔离 |
| 漏查旧版本使用 | 最新草稿但旧版 ACTIVE 拒绝；仅 Candidate / FROZEN / 失效历史引用时允许退出管理并保留历史 |
| 删除后重现或继续使用 | 所有管理入口及新保存/冻结/候选受限；旧书签不能复活；旧版本不回落列表 |
| 并发、重试及历史破坏 | 删除与保存/引用竞态、重复删除、超时重试、旧命令回放、闭包/Hash/检查记录完整 |
| 路由/授权不完整 | 有权成功、无权 Owner 零调用，必要头/Body/Guard、RFC 9457 错误逐层传播 |
| 导航只修单一路径 | 列表→详情→编辑、直接编辑、返回/前进、筛选分页、未保存取消/同意、刷新/直达及旧 Candidate 地址 |
| 原 V1 配置回归 | LEGACY/CONFIGURATION 无损回读、附加动作保存、冻结门禁及 Candidate 历史读取 |

删除尚未实现，test 没有 ACTIVE 数据。上述删除、并发、迁移和新导航行为都是后续验收目标，不是本阶段已通过结果；可以在批准的本地自动测试中构造使用事实，不需要实现完整激活流程。

## 9. 下一阶段的设计工作

1. 规则集身份、生命周期与历史回填；明确删除后同编码新建及恢复规则。建议保留编码身份、不隐式复活，不能仅因列表查不到就判定可新建；该项须写入待审合同。
2. V1 列表演进及 `ruleSetHead` 兼容，当前配置/使用状态/删除提示、稳定排序与筛选。
3. 删除 Method/Path、权限、前置条件、幂等回执与错误；历史闭包、Candidate 校验和旧命令回放的边界。
4. 页面地址/导航状态、刷新恢复、菜单权限、删除确认和旧 Candidate URL 落点。
5. 两库 DDL/DML、实现顺序、验收映射和发布兼容顺序。

这是阶段 3 的范围，不重开已确认的“一集一行、整体删除、实际使用保护、Candidate UI 延期”。阶段 2 交付后按用户要求停下，确认后再继续设计。

## 10. 阶段 3：方案选择与版本规则

用户已确认进入本阶段，并明确默认沿用原接口版本，新接口默认 V1，代码类型名不带 V1 等后缀。该规则已记录为[公共命名与版本决策](../../../agent-docs/decisions/2026-09-11-api-version-and-type-naming.md)，不是仅本需求的临时约定。

本方案选择：

- 直接修改原 `GET /decision-rule-sets/v1/rule-sets`，返回每个规则集的当前配置；不新增另一套列表 API。
- 新增一个 `DELETE /decision-rule-sets/v1/rule-sets/{ruleSetId}`。Risk 内部 API 与 BFF 各提供同一能力，公网多一条 Route。
- 原详情仍按 `ruleSetVersionId` 读取，原保存/冻结的 HTTP 请求、成功响应和业务内容 Hash 合同保留；不把原 GET Path 中的版本 ID 静默解释为根 ID。
- 增加 `t_rule_set` 管理根，与不可变内容分离。规则集身份永久保留，整体删除为终态；本期不开放恢复或同一决策点下复用已删除编码。
- 删除不修改任何历史版本状态、内容、绑定和 Release/检查记录；新管理操作拒绝已删除根，历史闭包与成功幂等重放保留。
- 新接入 Foundation 资源锁，仅用于本轮涉及的规则集写入协调；不引入新 Redis 服务，不迁移 Risk 幂等到 Foundation。

“编码删除后不复用”和上述根表/锁方案是本阶段供用户确认的具体设计，不声称此前已逐项批准。

## 11. 数据模型与不变量

### 11.1 规则集根

Risk `risk-decision` 拥有新增 `t_rule_set`，Java DataObject 为 `RuleSet`，枚举为 `RuleSetManagementStatus`。精确字段、类型、注释与索引见 [Risk DDL 评审稿](./db/risk-compliance-service/ddl.sql)。

| 字段 | 类型 / 可空 | 真实用途 |
|---|---|---|
| rule_set_id | BIGINT，必填 | 新 Snowflake 稳定根 ID；区别于任意业务版本 ID |
| decision_point_id / rule_set_code | BIGINT / VARCHAR(64)，必填 | 永久唯一的业务身份，删除后也占用编码 |
| current_rule_set_version_id | BIGINT，必填 | 当前最高业务版本的入口；删除后保留 |
| status | VARCHAR(64)，必填 | `CONFIGURABLE → DELETED`，只表达是否可继续管理，不表达是否发布生效 |
| version | BIGINT，必填，默认 1 | 管理 CAS 版本；保存新配置、首次冻结当前版本、删除各增加 1 |
| deleted_by / deleted_time | BIGINT / DATETIME(6)，可空 | 删除操作者及 UTC 微秒时间；删除后不可改写 |
| delete_reason_code / delete_trigger_id | VARCHAR(64) / CHAR(64)，可空 | 固定原因 `OPERATOR_REQUEST` 与不可逆命令触发标识；供追责和稳定回放 |
| create_time / update_time | DATETIME(6)，必填 | 首次形成配置时间，以及根最后变更时间 |

根共 12 列，1 个主键、3 个唯一索引、1 个普通索引。`(decision_point_id, rule_set_code)` 永久唯一；当前版本指针唯一归属一个根；非空删除触发标识唯一。已有版本通过原决策点/编码归属根，不给历史版本批量追加根 ID，也不改写旧 Hash。

不变量：CONFIGURABLE 的四个删除字段全空；DELETED 的四个字段全非空，固定原因正确且不得再改变。当前指针必须属于同一决策点/编码并指向最高 `version_no`；可管理当前版本只能为 DRAFT 或 FROZEN。新建在一个事务内创建完整版本、关联及根，禁止提交空根。没有根但存在历史版本是迁移缺口，服务失败关闭，不自动补建。

首次创建根 version=1；新保存与首次冻结在同一本地事务中更新根并 CAS 增加版本。Candidate 创建/校验不修改根的管理版本。整体删除只更新根及本地幂等记录，版本 AST、Point、Release 与审计/检查表没有删除或覆盖。

删除事实只有一次，因此直接由终态根的删除字段和最终管理版本保存命令来源、原因和操作者，复用既有命令幂等记录关联；本期不新增通用审计框架、删除事件表、恢复工作流或异步清理。

### 11.2 一集一行的查询

在同一只读 REPEATABLE READ 快照内，查询 CONFIGURABLE 根并 JOIN 当前版本，基于相同条件分别 COUNT 和 LIMIT/OFFSET。按当前版本 `create_time DESC, rule_set_version_id DESC` 稳定排序，保存后更新原规则集行的位置，总数不增加。删除后总数减少一。

`pointCode` 和 `ruleSetCode` 继续精确匹配；`status` 过滤当前版本状态。历史 RETIRED 不参与分页，原 `status=RETIRED` 仍作为合法查询返回空结果，页面只提供草稿/已冻结选项。不同决策点同编码不合并。

批量核对本页根的使用关系，避免每行串行 HTTP 或 N+1 查询。普通列表不取得 Redis/数据库排他锁。BFF 只转换投影和执行严格校验，不自己去重或计算数据库 total。

### 11.3 使用状态与历史边界

使用检查直接依据 Risk 库的发布及绑定事实，不调用只服务 TEST Candidate 的列表 API。覆盖 TEST 与 PRODUCTION，以及根所有业务版本。ACTIVE 发布必须占用 `active_slot=1`，非 ACTIVE 不得占活动槽位；出现非法槽位、损坏/缺失的活动发布绑定或跨决策点错绑时，不能判为未使用，按内部数据错误失败关闭。

检查目标决策点的活动发布完整绑定，以及指向该规则集版本的活动引用；缺失绑定不能被 INNER JOIN 静默吞掉。存在合法 ACTIVE 引用时 `inUse=true`，删除拒绝；普通 Candidate、FROZEN 或已失效历史引用不构成使用中。

普通管理 GET 详情对已删除根返回不存在；创建新版本、冻结、新建 Candidate 则返回已删除的业务冲突。Candidate 历史详情/校验以及旧成功命令回放走明确的历史闭包读取方法，不套管理列表的 CONFIGURABLE 过滤。它们不改变根状态。未来激活能力必须检查根仍可配置，并采用相同锁协议；本期不实现激活。

## 12. API 精确定义

### 12.1 路由及改动数量

下表 Path 为 BFF 和 Risk 的应用内相对路径；公网统一增加 `/api/operations`，Risk 的部署 context-path 继续按当前服务配置。

| Method / Path | 改动 | 权限 / 兼容 |
|---|---|---|
| GET `/decision-rule-sets/v1/rule-sets` | 改为规则集分页，元素增加 3 个字段 | 原 `RISK_DECISION_CONFIG_READ`，查询白名单不变 |
| GET `/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}` | 保留版本 ID 及原 JSON，增加管理生命周期限制 | 原读权限；历史回放不借普通详情入口恢复管理 |
| POST `/decision-rule-sets/v1/rule-sets` | 保留原请求/响应，维护根/当前指针与状态检查 | 原 SAVE 权限、64 KiB；原幂等 Hash 输入不变 |
| POST `/decision-rule-sets/v1/rule-sets/{ruleSetVersionId}/freeze` | 保留合同，首次冻结增加根管理版本 | 原 FREEZE 权限及配置执行门禁 |
| DELETE `/decision-rule-sets/v1/rule-sets/{ruleSetId}` | **新增**整体删除命令 | 新 `RISK_RULE_SET_DELETE`，16 KiB |
| Authoring Context、Candidate 四个既有入口 | HTTP Method/Path、请求/响应保留 | Candidate UI 隐藏；新建 Candidate 增加根状态检查，历史回读保留 |

新增业务能力 1 个，Risk 与 BFF 各新增 1 个处理入口，Platform 新增公开 Route 1 条。新增列表/编辑保存/当前配置专用 API 均为 0；BFF 的编辑仍调用原保存新版本命令。

### 12.2 列表请求与响应

查询参数继续为 `page`、`size`、`pointCode`、`ruleSetCode`、`status`。缺失 page/size 默认 1/10；page 1–100000，size 仅 10/20/50/100/200；非法值 400，不截断。两种 Code 为大写字母开头的 1–64 字符代码。status 可为 DRAFT/FROZEN/RETIRED，缺失表示不按当前状态筛选。Query 不承载 Body。

`RuleSetListItemResponseBody` 保留现有所有字段，增加：

| 新字段 | JSON 类型 | 约束 / 用途 |
|---|---|---|
| ruleSetId | string，非空 | 正 signed BIGINT 的规范十进制文本，供整体删除与行身份使用 |
| managementVersion | string，非空 | 正 signed BIGINT 文本，删除确认的 CAS 预期；与业务 versionNo 分开 |
| inUse | boolean，非空 | 任一业务版本是否被当前 ACTIVE 发布使用；不是权限判断 |

外壳仍为 `{items,page,size,total}`，page/size/total 保留原类型和范围约束；根身份与版本身份均必须唯一，total 按根计数。原 ID、名称、状态、Hash、人员与时间字段格式保持现行 V1 合同，字段完整/可空规则不变。

示例中的标识均为合成值：

```json
{"items":[{"ruleSetId":"101","managementVersion":"3","inUse":false,"ruleSetVersionId":"203","pointVersionId":"303","pointCode":"CHECKOUT_PREPARE","ruleSetCode":"CHECKOUT_BASE","ruleSetName":"结账基础规则","versionNo":2,"status":"DRAFT","contentHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","createdBy":"501","changeReason":"调整配置","createTime":"2026-09-11T10:00:00.000000Z","frozenBy":null,"frozenTime":null}],"page":1,"size":10,"total":1}
```

`loadObservedRuleSetHead` 改为按 pointCode+ruleSetCode 读取第一页、size=10，只允许 total 为 0 或 1，不再遍历版本页；再按响应的当前版本 ID 读详情并核对身份、版本/Hash。详情和编辑均先观察当前配置；保存继续用 baseRuleSetVersionId/baseContentHash 检查并发。BFF 将通用版本内容校验与根列表校验分开，避免给历史详情或保存回执伪造根元数据。

编码预检查只是开始配置前的观察，不保证保存成功。已删除编码不会出现在正常列表；如果用于新建，最终保存返回 `RISK_RULE_SET_DELETED` 并明确“编码已保留，请使用新编码”，不产生新根。

### 12.3 删除请求与回执

```http
DELETE /api/operations/decision-rule-sets/v1/rule-sets/101
Authorization: Bearer <runtime-access-token>
Content-Type: application/json
Idempotency-Key: <one-command-key>

{"expectedVersion":"3"}
```

- `ruleSetId` 是根 ID，不是业务版本 ID；Path 必须是正 signed BIGINT 规范十进制文本。
- `DeleteRuleSetRequestBody.expectedVersion` 必填、非 null、JSON string，范围 1–9223372036854775807；不接受数字替代文本、前导零、负号或未知 JSON 属性。空/缺失或错误 JSON 结构按现行绑定规则为 400，成功绑定后的 Body 约束违反为 422。
- 无 Query 参数；沿用现有 Idempotency-Key 格式/长度约束。同一次确认重试复用同 key 和完整请求；用户重新读取并再次确认后才创建新 key。
- Gateway 注入可信 Route/Actor/Session 上下文，BFF 先按 Route Code 执行 PLT-I02，失败时不调用 Risk；成功只转发一次 Owner 命令。Actor 不放 Body，Risk 保持内部身份 Header 合同。
- 不新增用户手填删除原因字段；确认点击对应固定原因 OPERATOR_REQUEST。服务端保存操作者、时间和删除触发身份。

首次成功及同命令重放均 HTTP 200，返回 `DeleteRuleSetResponseBody`：

```json
{"ruleSetId":"101","status":"DELETED","version":"4","deletedBy":"501","deletedTime":"2026-09-11T10:05:00.000000Z"}
```

五个字段全部非空；ID/version 为正 signed BIGINT 文本，status 固定 DELETED，时间为 UTC、六位小数。响应不包含规则内容、版本集合或触发 Hash。回放返回首次冻结的相同删除事实，不更新时间/操作者/版本。

新增 Owner 业务错误及 HTTP 状态：

| Code | 状态 | 含义 / 页面处理 |
|---|---|---|
| RISK_RULE_SET_IN_USE | 409 | 任一版本正在使用；说明无法删除并刷新使用状态 |
| RISK_RULE_SET_CHANGED | 409 | expectedVersion 与当前管理版本不同；刷新并要求重新确认 |
| RISK_RULE_SET_DELETED | 409 | 新 key 删除已删除根，或对已删除根执行新写入；清理旧页面/提示编码保留 |
| RISK_DECISION_CONFIGURATION_NOT_FOUND | 404 | 未找到目标；沿用现有错误码 |

已存在的认证/授权、公共参数、幂等冲突、并发等待、依赖不可用、限流和未预期错误继续使用现行公共 Code/Status，BFF 增加上述三个 Owner 错误的安全映射。损坏的状态/引用作为 500 内部错误，不伪装成“未使用”。所有 4xx/5xx 遵循现行 [Problem Profile](../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)，不增加私有错误外壳或 Query 字段 errors。

### 12.4 删除幂等与并发顺序

复用 Risk 的本地 `t_command_idempotency` 与现行 Caller Scope，新增 operation `DELETE_RULE_SET`。请求 Hash Schema 为 `RISK_DELETE_RULE_SET_COMMAND_V1`，JCS Object 内容为 `schema`、`ruleSetId`、`expectedVersion` 三个字符串字段，再取 SHA-256；不修改任何旧 Schema。

成功响应引用为 `rule-set-deletion:<ruleSetId>`。删除触发标识使用 SHA-256：UTF-8 的 `RISK_DELETE_TRIGGER_V1`、NUL、callerScope、NUL、operation、NUL、原 idempotencyKey，领域分隔独立于现有 Validate Trigger。字段只有内部追溯用途，原 key 不进根或日志。

无副作用准备步骤形成稳定业务坐标和 `command.resourceKey`；已知身份后通过 Spring 代理进入命令。Hash/语法准备不因根已删除而提前拒绝，使旧成功命令有机会回放。事务内先 claim/replay，同键异请求按旧幂等冲突拒绝；成功 replay 直接重建原回执，首次才做生命周期/expectedVersion/使用检查。

写入保护顺序：Redis 规则集资源锁 → 本地事务 → 幂等记录 → 决策点行 → 规则集根 → 必要当前版本/Point 绑定及发布事实 → 使用复验 → 根 CAS 删除 → 幂等成功 → 提交 → 释放 Redis。任何失败回滚根和幂等记录；无 MQ、补偿任务或后台删除。

## 13. 锁与运行配置

保存、冻结、整体删除、新建 Candidate 对同一 `(pointCode,ruleSetCode)` 共用 `@ResourceLock(prefix="decision:rule-set", key="#command.resourceKey")`；key 在准备阶段由两个已校验 Code 用冒号组成，最大 129 字符。不存在根的初次创建也用相同业务坐标协调，禁止分别用动作名或操作者加锁。历史 Candidate 校验只读不可变闭包，保留自己的 Release/幂等协议，不要求持有规则集锁贯穿静态计算。

采用已有 Foundation `paytech-redis-starter`，命令 Processor 改为可代理的非 final Bean，注解位于公开具体方法，事务在方法内部；不通过同类自调用、嵌套代理或带锁方法引用拼装锁。针对按 ID 输入的冻结/删除/Candidate，先查不可变身份，再锁后重读状态与坐标，准备步骤不作为授权或状态事实。

Risk 显式启用 `redis.coordination.enabled=true`，初始画像 wait-time=500ms、lease-time=30s；使用既有服务 Namespace 和当前环境安全注入的 Redis 地址/凭据。Risk 当前未依赖 Redis Starter，本期增加依赖、配置及真实 Redis 测试，test 环境需在后续发布清单中增加 Risk 的 Redis 配置，不新建基础设施或第二客户端。命令 write TransactionTemplate 设置有界超时 5s，SQL/连接等待结合现有连接配置验证；正常写事务不包含外部 HTTP，较重静态计算仍在事务外。

保留决策点的局部数据库行锁，因为现有保存还分配同点 Point Version 序号；它不是新增系统/租户级 Redis 大锁。保存/冻结/候选/删除都按相同数据库顺序锁根。删除在持有决策点与根锁后使用当前读核对活动发布，覆盖零使用记录的并发窗口；未来激活必须同样锁决策点及关联根、检查根状态。Redis Lease 失效时根 CAS、唯一键、数据库行锁和事务仍保护不变量，不能仅依赖 Redis 互斥。

## 14. 页面与导航合同

### 14.1 列表布局与菜单

```text
规则配置                                               [新建规则集]
决策点 [结账准备]  规则集编码 [              ]  状态 [全部] [查询]
共 N 个规则集                                             [刷新]
规则集名称 / 编码     状态     最近保存人 / 时间     冻结人 / 时间     操作 ▾
                                                        详情
                                                        编辑
                                                        删除（红色）
                                                   [分页]
```

移除蓝色说明、内容摘要、业务版本列和 Candidate Tab；保留现有保存人/时间、冻结人/时间等其余信息列并调整宽度。保留原详情 Drawer 内容，不新增版本选择器/历史列表；保留适用的冻结操作和配置门禁。

菜单同时支持 click/hover，键盘 Enter/Space 打开、方向键选择、Esc 关闭。详情需读权限；编辑复用 SAVE 权限；删除需新 DELETE Resource，未授权项隐藏。有权但 `inUse=true` 时删除项红色禁用，并提供“规则集正在使用，无法删除”的可访问说明；服务端仍再次检查。进入编辑时按最新观察到的配置读取，目录不允许编辑时保留现有说明。

删除确认显示名称、编码，以及“将删除整个规则集，历史配置保留且编码不再复用”；使用红色确认按钮，无额外原因输入。点击确认时固定 row.ruleSetId 和 managementVersion。成功关闭对话框并刷新；当前页因删除为空时退到仍有效的最后一页。失败保留安全错误，409 先刷新再允许新确认，不自动换 key 重试。

编码输入以同一 Upper Code 规则就地校验，先 trim，不自动大写、不改成模糊搜索；非法格式不发请求。清空后移除过滤，查询/条数变化回第一页。用户原始合法编码样例如另有故障仍按阶段 2 的证据继续处理。

### 14.2 URL、返回与刷新

沿用 `/risk/rule-configuration`，查询参数表达 `view=list|detail|edit|create`（缺省 list）、列表的 pointCode/ruleSetCode/status/page/size，以及打开对象的 `selectedRuleSetCode`。pointCode 同时确定对象命名空间；详情/编辑查当前配置时忽略列表状态和分页过滤，仅按 pointCode+selectedRuleSetCode 查询。根和版本 ID 来自服务返回，不由 URL 推算。

示例：`?pointCode=CHECKOUT_PREPARE&status=DRAFT&page=2&size=20&view=edit&selectedRuleSetCode=CHECKOUT_BASE`。规则集名称、完整规则、幂等 key/Payload 都留在页面内存，不写 URL、localStorage 或 sessionStorage。

- 列表→详情、列表→编辑、详情→编辑均 push 新历史，Browser Back/Forward 对称恢复实际视图。提交新的搜索/分页条件 push 更新后的列表地址；相同查询只刷新，不堆叠相同历史。
- 详情使用 Drawer 表达 URL 视图。由列表进入时关闭 Drawer 返回上一列表；直接打开详情时关闭则 replace 为同条件列表，不跳外部来源。浏览器 Back 本身保留正常历史语义，不无条件固定落点。
- 编辑页面“返回列表”经现有未保存确认后，replace 当前编辑条目为同筛选/分页列表；浏览器 Back 从详情进入的编辑仍回详情。来源标记只接受应用内部已知视图，不读取任意外部 return URL。
- 未保存或命令结果未知时，Back、Forward、页面离开均经过原 guard。取消保留内容；确认离开后丢弃内存草稿/重试上下文。仅切换视图不触发保存命令。
- 刷新详情/编辑按 URL 重新读当前已保存配置；未保存变更无法恢复，刷新前保留 beforeunload 提示。create 刷新只恢复编码并重建空草稿，不自动创建/提交。前进到已放弃的编辑也只重读已保存配置。
- 旧 `tab=test-candidates` 或 Candidate 定位参数统一 replace 到合法列表，删除 Candidate 参数；旧 `tab=rule-sets` 也规范化为列表地址。保留合法列表过滤，不出现空白、重定向循环或隐藏入口复活。
- 当前对象已删除/不可访问时显示安全提示并提供返回原列表，保留普通浏览器历史，不把 404 转换为空编辑器。

## 15. SQL 清单与迁移边界

| 文件 | 目标库 | 变更 / 前置条件 |
|---|---|---|
| [Risk ddl.sql](./db/risk-compliance-service/ddl.sql) | mor_risk | 新增根表，12 列、5 个索引（含 PK）；旧表无结构修改 |
| [Risk dml.sql](./db/risk-compliance-service/dml.sql) | mor_risk | 一次性回填根；需要该环境完整、审阅过的 Snowflake 身份 manifest，不能直接盲跑 |
| [Platform dml.sql](./db/platform-support-service/dml.sql) | mor_platform | 16 行插入，PLATFORM_ADMIN Role CAS 版本更新 1 行；无 DDL |

Platform 插入精确清单：Permission `900000000000000541 / RISK_RULE_SET_DELETE`；RolePermission `900000000000000587` 只授予既有 PLATFORM_ADMIN；Console Resource `900000000000000571 / OPERATIONS_RISK_RULE_SET_DELETE`；Route `900000000000001033 / operations-risk-rule-set-delete`；Header ID 5140–5145、Guard ID 6164–6169 使用文件中完整 18 位值。未给 OPERATIONS_VIEWER 或所有 SAVE 用户自动授权。

新 Route 使用现有 Operations BFF Target、OPERATIONS_JWT/HUMAN_PERMISSION、Cookie DROP/NONE、16 KiB Body、5s 接收和 10s 响应边界；六条头规则覆盖 Authorization、Origin、Content-Type、Idempotency-Key 的原有用途；六条 Guard 沿用 Shared Route、Client IP、Principal 的限速/并发基线。删除无 Query，原列表五个参数不变；新增 Query 白名单/Target/编辑权限均为 0。

注册增量先将 Route 与按钮设为 DISABLED；后续发布阶段交付并审阅具体启用操作，校验服务可用和完整 Snapshot 后才曝光。旧 Candidate 权限/Route/记录不删除。新库的服务单一初始化 DML 在阶段 4 同步定义，不能重复运行本次增量。

Risk 回填 manifest 每项为 `ruleSetId/decisionPointId/ruleSetCode`，ID 均为字符串。按实际目标环境和项目 Snowflake 能力生成离线身份清单并核对，不从版本 ID 派生、不用 MAX(id)+1、随机数或 SQL 时间公式。test 清单在阶段 6 准备；local 研发验证需要时，可在阶段 4 准备其独立清单并单独确认应用。dml.sql 要求 manifest 精确覆盖所有历史规则集，根表必须空，当前版本/决策点有效；缺少输入、旧根已存在或历史异常即停止。空新库也要显式 `[]`。回填取首版 create_time、最高业务版本指针及其 update_time，管理 version=1；旧内容、Hash、Release、检查及幂等表完全不更新。

迁移时停止 Risk 写入，核对目标环境并备份；先模型/Schema对齐，再 DDL、根回填与核验。SQL 只允许 mysql batch，禁止 --force。DDL 隐式提交，不能把两份脚本当作一个可回滚事务；DML 失败断开会话回滚后分析，不启动旧服务自动修复。

发布采用维护窗口内的 Risk → BFF → Web 协调替换并刷新浏览器静态资源；列表 JSON 和 Java 类型存在协调升级要求，禁止旧 BFF 与新 Risk 混用。启用删除后不能直接回滚到不理解根生命周期的旧 Risk，否则可能重新显示/使用已删除内容；优先修复或使用理解新根的回退制品。具体镜像、备份、manifest、维护窗口和曝光步骤在阶段 6 交付，本阶段没有部署授权。

## 16. 类型重命名与研发范围

`DecisionRuleSetManagementModels` 和相关 `DecisionReleaseManagementModels` 内所有带 V1 后缀的 record 去掉后缀，API 返回值、Controller、转换器、BFF Owner Client、Simulator 和测试同步编译调整；具体类型清单见 [重命名清单](./assets/type-renames.json)。新增 `DeleteRuleSetRequestBody`、`DeleteRuleSetResponseBody` 不带版本后缀。

Java 同名 Owner/BFF DTO 使用各自 `*Models` 容器限定名区分，不另加 V1 或随意改 JSON。现有非持久化 `RiskDecisionWireMapper` 在该范围同步按公共约定改名 `RiskDecisionWireConverter`。业务 `RuleSetVersion`、versionNo/baseRuleSetVersionId、LEGACY/CONFIGURATION、Hash Schema 字符串均保留。

研发按以下顺序：

1. 同步批准的 Risk 数据模型/PDManer、Schema、身份回填工件与模型约束；本地 SQL 应用另行取得具体文件/环境/备份授权。
2. Risk API 类型重命名、列表 DTO 和删除合同、错误/幂等枚举及映射；根持久化、列表、管理/历史读取、资源锁、保存/冻结/Candidate 状态保护。
3. BFF 编译新 API，适配列表与删除协议、错误/严格校验及 PLT-I02 链路，保持单 Owner 写入。
4. Web 列表、当前配置查找、导航/guard、菜单、删除、Candidate 隐藏及键盘/移动交互。
5. Platform 唯一初始化数据、控制平面 fixture 和环境前置/曝光检查；同步批准后的需求/数据库/V1 合同入口。

## 17. 验证与本阶段确认

| 验收组 | 研发及综合验证的核心证据 |
|---|---|
| AC-01/05/06/08 | 列表截图：说明/版本/摘要/Candidate Tab 消失，菜单 click/hover/键盘一致，删除红色，权限与禁用状态正确；旧地址落点 |
| AC-02/07 | 合法/非法/不存在/清空；组合过滤；多版本跨页但一集一行；总数、保存后更新、删除最后一行、不同点同码；当前配置只读一次分页 |
| AC-03 | 当前/旧版 ACTIVE 均拒绝；Candidate-only/FROZEN-only/失效历史可删除；损坏活动关系失败关闭；删除后各新入口拒绝且历史闭包/Hash/回放不变 |
| 并发/事务 | 删除与保存/冻结/新 Candidate、同 key 重试/异请求、不同 key 已删除、陈旧 expectedVersion；真 Redis 竞争/失效、代理与事务顺序、Lease 重叠后数据库保护及回滚 |
| AC-04 | 列表→详情→编辑/直接编辑；Back/Forward、过滤分页、确认取消/同意、刷新/直达、删除后旧地址；未保存内容不持久化 |
| 跨系统 | BFF 无权限 Owner 零调用，三个新增业务错误及公共 Problem、请求/回执严格传播；DELETE Body/Header/Guard/路由 Snapshot，旧 Candidate 合同继续回归 |
| 数据/命名 | 模型/DDL/注释一致、manifest 覆盖与重复执行拒绝、旧 Hash/幂等摘要保持；指定 API 类型无协议版本后缀、全部消费者编译 |

Java 验证遵循 [UNIT_TEST_GUIDE](../../../agent-docs/UNIT_TEST_GUIDE.md)，使用仓库 local-environment 的真实 MySQL/Redis 和协议 Simulator，不用 Testcontainers/H2 或测试自动迁移。沿用各项目构建/检查入口：Risk `./mvnw -q clean verify`、BFF `./mvnw -q verify`、Web 的 test/build、相关 Gateway/Platform 协议门禁、根模型及 Requirements Site 检查；按修改范围运行，不把目录隐藏当作删除后端测试。

本阶段交付的是可审阅设计、SQL 草稿和类型清单；没有声称 SQL 已在 MySQL 执行、模型已落库或新行为已经通过。下一确认是是否接受上述方案并进入阶段 4 研发；Risk 环境身份 manifest 与实际 SQL 应用仍按明确的环境/备份清单单独确认。


## 18. 研发中确认的必要补充

用户同意将 Foundation `paytech-redis-starter` 的监控初始化缺陷修复纳入本轮。`ResourceLockObservation` 在 Advisor 创建时保留 Registry 查找函数，实际记录锁事件时再解析，避免监控注册器跳过 Spring Boot 后处理器。查找或计数失败仍不影响业务结果；不改变资源锁键、等待/租约、数据库事务或资源锁公共注解合同。范围包括该组件测试及 Risk 真实启动回归，不授权远程制品发布、提交或部署。

列表读取在同一只读事务中先核对限定命名空间的管理根与最高版本一致性。根缺失、悬空指针、指向旧版或非法状态必须返回内部一致性错误，不能被列表 JOIN 静默隐藏为“没有数据”。这落实第 11 节的损坏数据失败关闭要求，不改变正常查询合同。
