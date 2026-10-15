# 技术方案：Operations 接入支付风控配置与执行发布

- 状态：阶段 2 已确认（2026-10-15）；**阶段 3 方案评审稿**（第 7～14 节，待确认）
- **规模（本次方案）：** 改动 Operations BFF 与 Web 两个应用，并产出 Platform 注册数据；BFF 新增 11 个转发接口（名单 5、执行发布 6，其中执行发布列表依赖 Risk 新接口），调整 6 个既有接口的模型（流程 4、变量 2）；新增 0 表 0 字段、修改 0 表、0 索引、0 业务 DML；Platform 注册 DML 150 行 INSERT（权限 4、授权 4、菜单/按钮 5、路由 11、路由头 50、查询参数 10、限流 66；阶段 5 补齐写路由的 content-type/idempotency-key 头 28 行）+ 本机激活 4 条 UPDATE；页面新增 2、修改 3（Pencil 画板 13 张）
- 需求：[requirement.md](./requirement.md)；交付记录：[delivery.md](./delivery.md)
- 证据基线：工作区 HEAD `84b3174`；BFF/Web 源码即 mor-test 运行版本 `34ecac9`（Operations 目录此后无提交）；risk-api 取工作区当前源码。以下“当前行为”均为**代码静态核对**，未对 mor-test 做运行期调用；标注“推断”的项在阶段 5 本地联调中实测

## 1. 状态区分

| 能力 | 设计已有 | 代码已有 | 验证 | 环境 |
|---|---|---|---|---|
| Risk 支付点配置、名单、执行发布、影子运行、结束节点输出 | 是（一期设计 + 修订） | Risk 已有；risk-api 已含名单与执行发布接口 | Risk auto 测试 | mor-test Risk 已部署（容器 42 分钟前重启，版本以 Risk 发布记录为准） |
| Operations BFF 接入上述能力 | 否 | 仅规则集/流程/变量/旧候选/字典 | — | mor-test 运行 `34ecac9` |
| Operations Web | 否 | 同上，规则集写死结账点 | — | 同上 |
| Platform 注册（路由、权限、菜单） | 否 | — | — | mor-test 无新路由 |

## 2. 需求差异表

| 需求项 | 当前行为及证据 | 目标行为 | 缺口/原因 | 处理建议 |
|---|---|---|---|---|
| AC-1 risk-api 升级 | BFF 依赖 `risk-api 1.0.0-SNAPSHOT`（`operations-console-bff/pom.xml:30`），从工作区/`~/.m3` 解析。`34ecac9..HEAD` 的 risk-api 差异只有 10 个文件：流程 `Node.outputs`（`DecisionFlowModels`，`NON_EMPTY`）、支付评估接口改为统一评估（BFF 未使用）；名单、执行发布、Velocity/List 参数在 `34ecac9` 时已存在 | BFF 以当前 risk-api 编译、回归通过 | BFF 本地模型与转换器（`RiskFlowModels`、`RiskFlowConverter`）没有 `outputs`，保存会丢字段 | 扩展：本地模型、转换器、请求键白名单加入 `outputs` |
| AC-1 响应严格校验 | `RiskOwnerResponseShapeValidator.object()` 要求属性集**完全相等**（第 229～237 行）；流程节点只允许 8 个字段（第 147 行）；流程创作上下文字段固定（第 161 行）。Web `decodeNode`/`decodeFlowContext` 同样 `exact` | 支付流程详情、未来带 `outputs` 声明的创作上下文都能读 | **推断：** mor-test 中任何含放行结束节点的支付流程详情，BFF 会判为 Owner 响应异常（502），Web 也会拒绝；Risk 补上上下文 `outputs` 后，支付点的流程创作上下文也会 502；结账点不受影响（Risk 确认 `outputs` 为 NON_EMPTY，结账响应不变） | 扩展校验形状，接受节点 `outputs`（编码→值映射）与上下文顶层可选 `outputs` 数组；见风险 K1 部署顺序 |
| AC-2 风控点切换·规则集 | Web 规则集写死：`RiskRuleConfiguration.tsx:108/126/166/170`、`ruleBuilder.ts:31/217`（“当前仅支持结账准备规则”）、`riskApiModels.ts:15` 类型字面量、`RuleSetTab.tsx:45-47` 与 `riskNavigation.ts:22` 下拉只有结账；只读视图用结账静态目录 `CHECKOUT_PREPARE_AUTHORING_V1`（`RuleSetExplorer.tsx:38`、`RuleSetDetails.tsx`、`ReadRuleSetWorkspace.tsx`）。可编辑目录已有通用版 `riskAuthoringCatalog.matchAuthoringContext`（按变量引用格式，与点无关） | 下拉来自创作上下文 `supportedPoints`，编辑/只读都用所选点的上下文 | 写死点 + 只读视图绑定结账静态目录 | 替换为点参数；只读视图改用对应点上下文；结账保留旧格式兼容 |
| AC-2 风控点切换·决策流 | 列表筛选已有“决策点”下拉，来自上下文 `supportedPoints`（`RiskDecisionFlows.tsx:46`）；创建时带 `pointCode`；页脚“本期仅管理配置，保存不会启用流程执行”（第 49 行） | 两点均可配置；支付点文案说明需经执行发布生效 | 主要是 `outputs` 读写与严格解码；文案过时 | 保留并扩展 |
| AC-2 风控点切换·变量 | 变量页已有“适用决策点”选择（`RiskVariables.tsx:52`），列表显示 `pointCode` | 同上 + 新来源 | 见 AC-5 | 扩展 |
| AC-3 名单维护 | BFF/Web 均无 `/risk-lists`；risk-api `RiskListManagementApi` 5 个接口已存在；成员响应只有 `maskedSubjectKey`；查询参数 `subjectValue` 为 GET Query | 名单与成员管理页面 | 未实现 | 新增 BFF 5 个转发端点 + Web 页面 |
| AC-4 主体值不外泄 | BFF 日志：`/risk-lists` 不在 `web.logging.metadata-only-path-prefixes`（`application.yml:72-92`），默认会记录 Query 与请求体（`HttpRequestResponseLoggingFilter:148-151`）；元数据模式只记路径（第 102 行）。Gateway 访问日志只记路由编码，不记 Query（`GatewayAccessLogWebFilter`） | 页面只显示脱敏值；BFF 不记原值 | 未配置 | `/risk-lists` 加入元数据前缀；Risk 侧日志见 R-Q4 |
| AC-5 新变量来源 | BFF 变量响应校验只认 `INPUT/CONSTANT/COLLECTION/NODE_OUTPUT`（`RiskVariableResponseShape:87`），**推断：**支付点变量创作上下文（含 VELOCITY/LIST 来源能力）及含新来源变量的列表会被 BFF 判 502；BFF 创建请求转换器不传 `velocity`/`list`（`RiskVariableConverter:131`）；Web `VariableSource` 类型同样未含新来源 | 可创建、列表、详情查看 | 未实现；且 **Risk 变量详情/列表不返回 Velocity/List 参数**（`VariableDetailResponseBody` 无对应字段，Risk 查询侧无 `windowSeconds` 输出），创建后页面无法展示“参数只读” | 扩展 BFF/Web；Risk 缺口记为 R-Q3 |
| AC-5 参数可选值 | Risk 变量上下文只返回来源能力编码（`RiskVariableWireConverter:70`），不返回事件、算法、窗口、主体类型的可选值 | 表单下拉 | 枚举需前端写死 | 按《风控标准目录》第 2～4 节与 `VelocityFeatureParameters` 校验写死；记入 R-Q3 一并问是否提供 |
| AC-6 结束节点输出 | Web/BFF 节点模型无 `outputs`；创作上下文无输出声明 | 放行结束节点可选认证要求 | 未实现；声明暂无 | 先写死两个值；Risk 已同意在上下文增加 `outputs` 声明（R-Q2） |
| AC-7～9 执行发布 | BFF/Web 无 `/decision-releases/v1/executions`；risk-api `ExecutionReleaseManagementApi` 5 个接口已存在；无列表接口（Risk 正在补，`ListExecutionReleasesProcessor` 已出现在 Risk 工作区）；详情含 `checks`（八项）与 `shadowRun` | 列表、创建、详情、校验、影子运行轮询、激活确认 | 未实现；列表依赖 Risk 新接口 | 新增 BFF 端点 + Web 页面；列表接 Risk 新接口 |
| AC-10 规则集被引用 | `OperationsErrorStatusResolver:59` 已把 `RISK_RULE_SET_IN_USE` 映射 409 | 提示查看执行发布 | Web 文案未区分 | 只改 Web 文案 |
| AC-11 权限 | Web `pageCapabilities.ts` 有 6 个 Risk 页面能力（规则配置、决策流、拒绝原因、主体标签、变量）；Platform 现有 `RISK_DECISION_CONFIG_READ`、`RISK_RULE_SET_*`、`RISK_DECISION_FLOW_SAVE/DELETE`、`RISK_VARIABLE_MANAGE/DELETE` 等权限 | 名单、执行发布按阶段 3 拆分受控 | 无对应权限、菜单、路由 | 阶段 3 设计 + Platform 注册 DML（沿用 Vault 需求方式：需求目录内 SQL，DISABLED 登记 + 单独激活） |
| AC-12 mor-test 联调 | 无支付规则与生效发布，支付评估 503 | 有生效发布 | 依赖 BFF/Web 部署与注册 | 阶段 6/7 |
| UI-1 刷新保持风控点 | 规则集导航 `pointCode` 默认结账；决策流导航已带 `pointCode` 参数 | 刷新保持 | 规则集需改默认 | 扩展导航 |

## 3. 逐系统影响

| 系统/仓库 | 业务职责 | 影响点 | 是否改动 | 上下游依赖与兼容风险 |
|---|---|---|---|---|
| operations-console-bff | 运营入口适配、转发、响应形状校验 | risk-api 升级；流程 `outputs`；变量新来源与参数；名单 5 端点；执行发布 6 端点（含列表）；日志前缀；测试 Simulator 与 auto 测试 | **改代码**，无数据库 | 依赖 risk-api 当前源码与 Risk 列表/输出声明新版本；严格形状校验必须与 Risk 版本同步 |
| operations-console-web | 页面 | 规则集风控点切换与只读目录；决策流输出与解码；变量新来源；名单页；执行发布页；权限能力 `pageCapabilities.ts` | **改代码** | 依赖 BFF 新端点；新页面需 Platform 菜单资源才可见 |
| Platform Support（注册数据） | 菜单、权限、Gateway 路由 | 新权限、Console Resource、Gateway Route（含 Query 白名单、Body 上限） | **只产出注册 DML**（需求目录），不改 Platform 代码。Vault 需求未改 `console-resource-capabilities.json` 已正常运行，本需求沿用 | 执行需单独授权；mor-test 需备份后执行并激活 |
| Risk & Compliance | 规则、名单、发布事实 Owner | 列表接口、输出声明（已通知）；R-Q3/R-Q4 待问 | **不改**（由 Risk 会话处理） | Risk 发布新版本需 mor-test 重部署（Risk 用户单独授权） |
| api-gateway | 路由、鉴权 | 只消费 Platform 快照 | 不改 | 注册激活后 Snapshot 刷新 |
| Payment | 调用统一评估 | 联调方 | 不改 | 阶段 7 联调需 Payment 会话配合 |
| 需求站 `requirements-site` | 需求目录 | RCO-I12 已覆盖配置接口；是否新增运营页面用例条目在阶段 3 判断 | 待定 | — |
| `CURRENT_IMPLEMENTATION.md` | 状态台账 | 阶段 4 起记录 | 文档 | — |

## 4. 主要风险与难点

| 编号 | 风险 | 影响 | 应对 |
|---|---|---|---|
| K1 | **严格响应校验与 Risk 版本耦合**：支付流程详情带节点 `outputs`、支付点创作上下文带顶层 `outputs`，当前 BFF 对二者判 502；结账点不受影响 | 支付点决策流在 mor-test 不可用，直到新版 BFF 部署 | Risk 用户改为先部署 Risk（2026-10-15，`b26e857`），并以 9 个结账接口 433 条字段路径前后一致证明旧 BFF 读结账点不受影响；新版 BFF 同时接受有/无 `outputs`，可随时部署 |
| K2 | 变量详情不返回 Velocity/List 参数 | 运营创建后看不到口径，只能看名称 | R-Q3 Risk 决定不做；详情只显示来源类型并提示“参数以创建时为准”，靠编码命名规范与说明表达口径 |
| K3 | 规则集只读视图深度绑定结账静态目录（多处组件），改动面大 | 回归风险 | 只读视图改为按点取上下文，结账保留旧格式路径；以现有 Web 测试（996 项）回归 |
| K4 | 名单成员按原值查询经 GET Query | 日志泄露 | BFF 加元数据前缀；Risk 已只记元数据（R-Q4 关闭）；Gateway 不记 Query |
| K5 | 执行发布列表依赖 Risk 新接口 | 未就绪时页面缺“当前生效” | 阶段 4 先按 Risk 已给字段开发，联调前确认提交号；必要时阶段 5 等待 |
| K6 | 影子运行只能在已有生效发布时执行；mor-test 首个发布直接激活 | 首次无法演示影子差异 | 阶段 5 本地准备两版流程演示；mor-test 首发直接激活，第二版再走影子 |
| K7 | 本机未运行 Risk（只有 mor-test 容器） | 阶段 5 联调需要本机 Risk 最新代码 | 阶段 5 列出本机启动 Risk 的具体动作（需 `mor_risk` 已含一期增量，Risk 交付记录称已执行）再请求确认 |

## 5. 工作量依据（粗估）

| 部分 | 依据 | 估计 |
|---|---|---|
| BFF | 新增 11 个转发端点（名单 5、执行发布 6）+ 2 处模型扩展（流程输出、变量参数）+ 形状校验 + 日志前缀；按既有 Risk Controller → Client → Converter 模式与 Simulator auto 测试 | 中 |
| Web | 规则集风控点切换（约 10 个文件写死点）、决策流输出编辑与图上标识、变量新来源表单、名单页、执行发布页（列表/详情步骤条/影子轮询/激活确认） | 中偏大，主要在执行发布页与规则集只读目录 |
| 注册 DML | 新权限、菜单、约 12 条路由及 Query/Header/Guard 子表 | 小 |
| 联调与发布 | 本机 Risk、mor-test 三应用 + Risk 协调部署 + 规则配置 | 中 |

## 6. Risk 接口待问清单

| 编号 | 问题 | 状态 |
|---|---|---|
| R-Q1 | 执行发布列表接口 `GET /decision-releases/v1/executions?pointCode&status&page&size` | **已完成**：Risk `b26e857`，只接受这 4 个 Query（其他 400）；回滚＝用旧 `flowVersionId` 新建发布；已部署 mor-test（空页） |
| R-Q2 | 决策流创作上下文返回附加输出声明 | Risk 原则接受：响应**顶层** `outputs[{outputCode,name,allowedValues[{value,name}],defaultValue,allowedEndDecisions:["ALLOW"]}]`，仅有声明时输出（结账响应不变）；支付点 `AUTHENTICATION_REQUIREMENT`/认证要求/NOT_REQUIRED 无需认证、THREE_DS_REQUIRED 需要 3DS/默认 NOT_REQUIRED；**已完成** `b26e857`，已部署 mor-test |
| R-Q3 | 变量详情/列表不返回 Velocity/List 参数；变量上下文不返回事件、算法、窗口、主体类型可选值 | **不做**（Risk 用户决定，2026-10-15）：详情不回显参数、上下文不返回可选值；Web 按标准目录与 `VelocityFeatureParameters` 校验写死，以后需要另提需求 |
| R-Q4 | Risk 是否对 `/risk-lists` 只记元数据日志（成员查询 `subjectValue` 在 Query 中） | **已关闭**：Risk `/risk-lists`、`/risk-decisions` 已只记元数据，`WebLoggingBoundaryAutoTest` 覆盖 |
| R-Q5 | 部署顺序：上下文新增 `outputs` 会使旧 BFF 严格校验失败，Risk mor-test 部署需在 Operations BFF 新版之后 | **已调整**：Risk 用户决定先部署 Risk（`b26e857`，有结账接口兼容证据）；Operations BFF 新版不再受顺序约束 |

---

# 阶段 3：方案、接口与页面设计

## 7. 方案总览

| 交接单步骤 | 方案 | 对应 AC |
|---|---|---|
| ① risk-api 升级与风控点切换 | BFF 以工作区 risk-api 编译；流程节点与创作上下文接受可选 `outputs`；Web 规则集去掉写死结账点，按所选风控点加载创作上下文 | AC-1、AC-2、UI-1 |
| ② 名单与新变量 | BFF 新增名单 5 个转发；变量模型支持 `velocity`/`list`；Web 新增名单页、变量表单两种来源 | AC-3、AC-4、AC-5 |
| ③ 执行发布与结束节点输出 | BFF 新增执行发布 6 个转发；Web 新增执行发布页与激活确认；决策流放行结束节点配置认证要求 | AC-6～AC-10、UI-3、UI-4 |
| ④ mor-test 配置 | 阶段 6 列出部署、注册、规则与阈值清单；阶段 7 执行 | AC-12 |
| 权限 | 4 个新权限 + 2 个菜单 + 3 个按钮（第 10 节） | AC-11 |

**架构边界：** BFF 只做协议适配与转发（Controller → `integration` Client Port → Impl → risk-api 代理），沿用现有 Risk 适配模式；本需求没有跨接口聚合，**不新增 Processor/Service**（必要性门禁：激活确认需要的“当前生效发布 + 候选详情”由 Web 分别调用列表与详情获得，BFF 合并无额外价值）。BFF 无数据库、无事务、无锁；业务防重、状态机、并发全部由 Risk 负责；HTTP `Idempotency-Key` 由 Gateway 负责，BFF 不接收、不转发。

**失败分支：** Risk 4xx Problem 原样透传（`code`/`detail`/`errors`）；Risk 响应形状不符 → BFF 502（现有 `RiskOwnerResponseShapeValidator` 机制）；Risk 不可用 → 503。写命令不自动重试。

## 8. 接口设计

外部路径统一为 `/api/operations` + BFF 路径（Gateway 去前缀转发到 BFF）；BFF 路径与 Risk 路径相同。鉴权：Gateway `OPERATIONS_JWT` + `HUMAN_PERMISSION`（权限见第 10 节），BFF 入站拦截器完成 PLT-I02 后以 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id` 调 Risk。

### 8.1 新增：名单（`RiskListController` → `RiskListClient`）

| # | Method Path | 请求 | 响应 | 错误 |
|---|---|---|---|---|
| L1 | `GET /risk-lists/v1/lists?page&size` | 公共分页（默认 1/10，size∈10/20/50/100/200） | `PagingResponseBody<RiskList>`：`listId`、`listCode`、`listName`、`subjectType`、`purpose`（BLOCK/ALLOW）、`active` | — |
| L2 | `POST /risk-lists/v1/lists` | `listCode`（必填，`^[A-Z][A-Z0-9_]{0,63}$`）、`listName`（必填）、`subjectType`（必填）、`purpose`（必填） | `RiskList` | 编码已存在 400；并发 409 |
| L3 | `GET /risk-lists/v1/lists/{listCode}/entries?subjectValue&status&page&size` | `subjectValue` 可选（原值，精确匹配，≤256）、`status` 可选 | `PagingResponseBody<RiskListEntry>`：`entryId`、`listId`、`subjectType`、`maskedSubjectKey`、`sourceType`、`status`、`effectiveFrom`、`effectiveTo`（可空）、`reason`、`createdBy`、`deactivatedBy`、`deactivatedReason`（可空）、`createTime` | 名单不存在 400 |
| L4 | `POST /risk-lists/v1/lists/{listCode}/entries` | `subjectValue`（必填 ≤256）、`reason`（必填 ≤500）、`effectiveFrom`/`effectiveTo`（可选，UTC `yyyy-MM-ddTHH:mm:ss[.ffffff]Z`） | `RiskListEntry`（只含脱敏键） | 名单停用/格式不合法 400；已有生效成员 409 |
| L5 | `POST /risk-lists/v1/lists/{listCode}/entries/{entryId}/deactivate` | `reason`（必填 ≤500） | `RiskListEntry` | 不存在/已失效 400 |

请求示例（L4）：`{"subjectValue":"203.0.113.7","reason":"卡测试来源","effectiveTo":"2026-11-15T00:00:00Z"}`；响应 `maskedSubjectKey:"203.0.***"`。

**敏感值边界：** `web.logging.metadata-only-path-prefixes` 增加 `/risk-lists`（不缓存、不记 Query/Body）；BFF 本地模型不保存原值，响应模型没有原值字段；Web 查询框不写 URL、提交后清空。Risk 侧已只记元数据（R-Q4）；Gateway 访问日志只记路由编码。

### 8.2 新增：执行发布（`RiskExecutionReleaseController` → `RiskExecutionReleaseClient`）

| # | Method Path | 请求 | 响应 | 错误 |
|---|---|---|---|---|
| E1 | `GET /decision-releases/v1/executions?pointCode&status&page&size` | `pointCode` 必填、`status` 可选、公共分页 | `PagingResponseBody<ExecutionReleaseItem>`：`releaseId`、`releaseNo`、`pointCode`、`environment`、`status`、`active`、`flowId`、`flowCode`、`flowVersionId`、`flowVersionNo`、`contentHash`、`createdBy`、`createTime`、`activatedBy`/`activatedTime`/`supersededTime`（可空）；按创建时间倒序 | **依赖 Risk 新接口（R-Q1）** |
| E2 | `POST /decision-releases/v1/executions` | `pointCode`、`flowVersionId`（均必填） | `ExecutionRelease`：`releaseId`、`releaseNo`、`pointCode`、`flowVersionId`、`environment`、`status`、`version`、`contentHash`、`activatedTime`/`supersededTime`（可空）、`checks[]`、`shadowRun`（可空） | 点不支持/流程不属于该点/规则集未冻结 400 |
| E3 | `GET /decision-releases/v1/executions/{releaseId}` | — | `ExecutionRelease`；`checks[]`：`checkCode`、`checkStatus`、`errorCode`/`errorSummary`（可空）；`shadowRun`：`shadowRunId`、`baselineReleaseId`、`status`、`sampleTarget`、`sampleCount`、`allowToReviewCount`、`allowToDenyCount`、`denyToAllowCount`、`actionChangeCount`、`featureMissingCount`、`failedCount`、`timedOutCount`、`p95Millis`、`p99Millis`（计数类完成前可空）、`checkStatus` | 不存在 404 |
| E4 | `POST /decision-releases/v1/executions/{releaseId}/validate` | 无 Body | `ExecutionRelease`（SHADOWING 或 REJECTED；已校验返回原结果） | 400 |
| E5 | `POST /decision-releases/v1/executions/{releaseId}/shadow-runs` | `sampleTarget`（1～2000，Risk 默认 200） | `ExecutionRelease`（含运行中批次） | 无生效发布/已有运行中批次 400 |
| E6 | `POST /decision-releases/v1/executions/{releaseId}/activate` | 无 Body | `ExecutionRelease`（ACTIVE） | 前置不满足 400 `BUSINESS_CHECK_FAILED` |

**状态容错：** `status`、`shadowRun.status`、`checkStatus`、`checkCode` 在 BFF 只校验为非空编码字符串（不做闭合枚举），Web 未知值原样显示——Risk 已说明 REVIEW_PENDING/APPROVED 可能短暂出现。

### 8.3 调整：决策流（既有 4 个接口，路径不变）

| 接口 | 调整 |
|---|---|
| `GET /decision-flows/v1/authoring-context` | 响应顶层可选 `outputs[{outputCode,name,allowedValues[{value,name}],defaultValue,allowedEndDecisions}]`（Risk 新版，仅有声明时出现） |
| `GET /decision-flows/v1/flows/{flowId}`、`.../versions/{flowVersionId}` | `graph.nodes[].outputs` 可选（编码 → 值，最多 8 项，键/值为编码格式） |
| `POST /decision-flows/v1/flows`、`POST /decision-flows/v1/flows/validate` | 请求 `graph.nodes[].outputs` 可选并原样转发 |

兼容：不带 `outputs` 的请求/响应与现在完全一致（结账点不变）。**认证要求可选值：** 读上下文 `outputs`（Risk `b26e857` 已提供）；上下文没有该键时，支付点回退为前端常量 `AUTHENTICATION_REQUIREMENT: NOT_REQUIRED（无需认证，默认）/ THREE_DS_REQUIRED（需要 3DS）`，结账点不显示。Risk 发布 R-Q2 后不需要改代码即切换为读取；常量保留为过渡，在 Risk 新版部署到 mor-test 后的下一次 Operations 变更中删除（记入遗留项）。

### 8.4 调整：变量（既有 2 个接口及全部变量响应的来源取值，路径不变）

| 接口 | 调整 |
|---|---|
| `POST /decision-variables/v1/variables` | 请求增加可选 `velocity{eventCode,anchorSubjectType,metric,distinctSubjectType,windowSeconds,includeCurrent}`、`list{listCode,subjectType}`，按 risk-api `CreateVariableRequestBody` 原样转发 |
| `GET /decision-variables/v1/authoring-context` | 来源能力增加 `VELOCITY`、`LIST`（Risk 不返回参数可选值，R-Q3 不做） |
| 列表、详情等所有变量响应 | `sourceType` 允许 `VELOCITY`、`LIST` |

Web 常量（R-Q3 Risk 决定不做，长期写死）：事件 `PAYMENT_ATTEMPT_ASSESSED`/`PAYMENT_ATTEMPT_REJECTED`；统计主体 `CLIENT`/`OPERATING_ENTITY`/`CONSUMER`/`IP_ADDRESS`/`CARD_FINGERPRINT`；名单主体另加 `CARD_BIN`；算法五种；窗口 600/3600/86400/604800。依据：《风控标准目录》第 2～4 节、Risk 设计 4.2/4.3、`VelocityFeatureParameters` 校验。

### 8.5 保持不变 / 废弃

规则集、旧测试候选、字典、变量其余接口保持不变；无废弃接口。规则集请求的 `pointCode` 本来就是参数，Web 只是不再写死。

## 9. BFF 实现清单

| 文件（`com.paytech.operations.console.bff`） | 内容 |
|---|---|
| `ext/rest/RiskListController`、`ext/rest/RiskExecutionReleaseController` | 新 Controller，强类型请求/响应，只委托 Client Port |
| `integration/RiskListClient`、`integration/RiskExecutionReleaseClient` 及 `impl/*Impl`、`impl/*Converter` | 单 Owner 调用、逐字段转换；写结果核对 `listCode`/`releaseId` 与请求一致，不一致 502 |
| `service/model/RiskListModels`、`RiskExecutionReleaseModels` | BFF 本地页面模型（沿用现有 `Risk*Models` 写法） |
| `configuration/RiskClientConfiguration` | 注册 `RiskListManagementApi`、`ExecutionReleaseManagementApi` 代理（同一 `operationsRiskProxyFactory`） |
| `configuration/RiskOwnerResponseShapeValidator`、`RiskVariableResponseShape` | 新根形状；节点/上下文 `outputs`；变量新来源与可选参数 |
| `configuration/*RequestConfiguration` | 新路径的严格 Query/Body 键白名单；流程节点允许 `outputs`；变量创建允许 `velocity`/`list` |
| `RiskFlowModels`/`RiskFlowConverter`、`RiskVariableModels`/`RiskVariableConverter` | 增加 `outputs`、`velocity`、`list` |
| `application.yml` | 元数据日志前缀增加 `/risk-lists` |
| `README.md` | Risk 适配边界补充两组新接口 |

测试（`paytech-operations-console-bff-test`，auto，Risk 用现有测试 HTTP Simulator 返回 DTO）：
- `RiskListBrowserAutoTest`：5 个接口转发；授权拒绝零转发；Problem 透传（400/409）；非法 Query/Body 400；跨名单响应 502；**日志不含主体原值**（捕获日志断言 `subjectValue` 原文不出现）。
- `RiskExecutionReleaseBrowserAutoTest`：6 个接口转发；未知状态容错；`releaseId` 不一致 502；激活 400 透传。
- 既有 `RiskFlowBrowser*`、`RiskVariableBrowser*` 补充：带/不带 `outputs`、新来源变量、参数回显的读写。
- 回归：BFF 全量 `verify`。

## 10. 权限与菜单设计

| 权限 | 级别 | 控制 | 理由 |
|---|---|---|---|
| `RISK_DECISION_CONFIG_READ`（既有） | — | 执行发布列表/详情、执行发布菜单 | 与规则集、流程只读同级，均为配置元数据 |
| `RISK_LIST_READ`（新） | MEDIUM | 名单菜单、名单与成员查询 | 成员是 IP、卡指纹等受限主体（即使脱敏，且可用原值做存在性探测），不应随“风控配置只读”默认开放 |
| `RISK_LIST_MANAGE`（新） | HIGH | 新建名单、加入/失效成员 | 立即影响支付评估（名单命中实时读取） |
| `RISK_EXECUTION_RELEASE_PREPARE`（新） | HIGH | 新建、校验、影子运行 | 准备动作不影响线上，但会回放评估、占用资源 |
| `RISK_EXECUTION_RELEASE_ACTIVATE`（新） | CRITICAL | 激活 | 直接改变支付决策；与准备分离，可只授予负责人 |

菜单：风控管理下新增“风控名单”`/risk/lists`（权限 `RISK_LIST_READ`，排序 60）、“执行发布”`/risk/execution-releases`（`RISK_DECISION_CONFIG_READ`，排序 70）；按钮“维护名单”“创建与校验发布”“激活发布”。Web `navigation/pageCapabilities.ts` 增加两个页面能力：`OPERATIONS_RISK_LISTS`（按钮 `OPERATIONS_RISK_LIST_MANAGE`）、`OPERATIONS_RISK_EXECUTION_RELEASES`（按钮 `OPERATIONS_RISK_EXECUTION_RELEASE_PREPARE`、`..._ACTIVATE`）。规则集、流程、变量沿用既有权限。

一期 TEST 单人激活（Risk 合同）；Operations 不做双人审批。

## 11. 数据库与注册数据

| 类别 | 内容 |
|---|---|
| 业务 Schema DDL | **0**（BFF/Web 无数据库；Risk 不改） |
| 业务 DML | **0**；mor-test 的支付规则、名单、变量、发布通过运营页面（Risk 接口）创建，不写 SQL |
| Platform 注册 DML | [registration-dml.sql](./db/platform-support-service/registration-dml.sql)：150 行 INSERT（阶段 5 由 122 行补齐写路由请求头 28 行），全部 DISABLED/REVOKED；含执行前四项取值检查与 ID/编码冲突预检；不含 COMMIT |
| 本机激活 | [activation-local.sql](./db/platform-support-service/activation-local.sql)：11 路由 / 5 资源 / 4 授权 ACTIVE + 角色 version+1；仅 `mor_platform_e2e` |
| mor-test 激活 | 阶段 6 另出文件（同结构），连同备份与撤销方案一起请求授权 |

路由明细（11 条，均 `OPERATIONS_JWT`/`HUMAN_PERMISSION`，响应超时 10 秒，限流沿用 Vault 写法：共享 300/分钟、并发 50；IP 30/分钟、并发 3；账号 30/分钟、并发 2）：

| 路由编码 | Method | BFF 路径 | 权限 | Body 上限 | Query 白名单 |
|---|---|---|---|---|---|
| operations-risk-list-list | GET | `/risk-lists/v1/lists` | RISK_LIST_READ | 0 | page, size |
| operations-risk-list-create | POST | `/risk-lists/v1/lists` | RISK_LIST_MANAGE | 4096 | — |
| operations-risk-list-entries | GET | `/risk-lists/v1/lists/{listCode}/entries` | RISK_LIST_READ | 0 | subjectValue, status, page, size |
| operations-risk-list-entry-add | POST | `/risk-lists/v1/lists/{listCode}/entries` | RISK_LIST_MANAGE | 4096 | — |
| operations-risk-list-entry-deactivate | POST | `.../entries/{entryId}/deactivate` | RISK_LIST_MANAGE | 4096 | — |
| operations-risk-execution-list | GET | `/decision-releases/v1/executions` | RISK_DECISION_CONFIG_READ | 0 | pointCode, status, page, size |
| operations-risk-execution-create | POST | `/decision-releases/v1/executions` | RISK_EXECUTION_RELEASE_PREPARE | 4096 | — |
| operations-risk-execution-detail | GET | `/decision-releases/v1/executions/{releaseId}` | RISK_DECISION_CONFIG_READ | 0 | — |
| operations-risk-execution-validate | POST | `.../{releaseId}/validate` | RISK_EXECUTION_RELEASE_PREPARE | 0 | — |
| operations-risk-execution-shadow-run | POST | `.../{releaseId}/shadow-runs` | RISK_EXECUTION_RELEASE_PREPARE | 4096 | — |
| operations-risk-execution-activate | POST | `.../{releaseId}/activate` | RISK_EXECUTION_RELEASE_ACTIVATE | 0 | — |

既有路由不变：流程保存/校验、变量创建的 Body 上限（流程 65536、变量 16384）足够容纳新字段。影子运行详情轮询每 3 秒一次，单账号 30 次/分钟限流足够（20 次/分钟）。

## 12. 页面

见 [ui.md](./ui.md)：新增 P4 风控名单、P5 执行发布；修改 P1 规则集、P2 决策流、P3 变量。Web 实现落点：

| 页面 | 主要文件 |
|---|---|
| P1 | `pages/RiskRuleConfiguration.tsx`、`risk/ruleBuilder.ts`、`risk/riskApiModels.ts`、`risk/RuleSetTab.tsx`、`risk/riskNavigation.ts`、只读视图（`RuleSetExplorer`、`RuleSetDetails`、`ReadRuleSetWorkspace`）改为按点取上下文 |
| P2 | `risk/decisionFlow.ts`（节点 `outputs` 解码/保存）、`risk/decisionFlowApi.ts`（上下文 `outputs`）、`risk/FlowPropertyPanel.tsx`、`risk/FlowCanvas.tsx`、`pages/RiskDecisionFlows.tsx` 文案 |
| P3 | `risk/riskVariable.ts`、`pages/RiskVariables.tsx` |
| P4 | 新 `pages/RiskLists.tsx`、`risk/riskListApi.ts` |
| P5 | 新 `pages/RiskExecutionReleases.tsx`、`risk/executionReleaseApi.ts`、激活确认组件 |
| 导航 | `navigation/pageCapabilities.ts`、`navigation/menu.ts` 与路由注册 |

Web 测试（Vitest，沿用现有 `risk*.test.ts` 写法）：解码器（含未知状态、`outputs` 有/无）、名单页不渲染原值、查询原值不进 URL、激活确认在差异 >0 时必须勾选、按钮按权限与状态显示、风控点切换与 URL 保持；全量 `npm test` 与 `npm run build`。

## 13. 研发与验证计划

| 顺序 | 内容 | 验证 | 依赖 |
|---|---|---|---|
| 4-1 | BFF：risk-api 升级（`b26e857`）、`outputs`、变量新来源/参数、形状校验 | BFF auto 定向 + 全量 | risk-api `b26e857` |
| 4-2 | Web：风控点切换（P1）、决策流输出（P2）、变量来源（P3） | Vitest + build | 4-1 |
| 4-3 | BFF + Web：名单（P4） | auto + Vitest | — |
| 4-4 | BFF + Web：执行发布（P5） | auto + Vitest | Risk `b26e857` 已提供列表接口 |
| 5 | 本机联调：启动本机 Risk（含 R-Q1/R-Q2 提交）、BFF、Web；执行注册 DML + 本机激活；经 Nginx 运营入口 API 与浏览器验收（含配置支付规则、首发激活、第二版影子与激活） | 验收脚本 + 截图 | 本机 SQL 执行授权（阶段 5 前单独申请）；Risk 本机运行 |
| 6/7 | mor-test：Risk `b26e857` 已部署（镜像 `fcfc9457dab6`）；部署 Operations BFF/Web；注册 + 激活；配置并激活支付规则；Payment 调用验证 | 发布记录 | 用户授权；Payment 会话配合 |

## 14. 待确认事项（阶段 3）

| 编号 | 问题 | 建议 |
|---|---|---|
| D1 | 权限拆分：名单只读单独成权限、执行发布准备与激活分开 | 按第 10 节采用 |
| D2 | 执行发布与名单做成独立菜单页，而不是嵌入规则配置页的标签 | 采用（与发布生命周期、名单维护的使用者不同） |
| D3 | 激活确认在“拒绝→放行”或“认证要求变化”>0 时强制勾选确认 | 采用；不设自动阈值（与 Risk 设计一致，由激活人判断） |
| D4 | 认证要求、Velocity 选项先用前端常量，Risk 新字段上线后自动切换读取 | 采用 |
| D5 | 页面以 Pencil 画板评审（13 张，见 ui.md） | 用户要求，已绘制 |
