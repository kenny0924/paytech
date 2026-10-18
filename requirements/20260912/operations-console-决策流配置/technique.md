# 决策流配置：技术评审入口

状态：原阶段 1–5 已确认，包含[输入定义与旧顺序编排解耦修订](./simplification.md)；[综合验证记录](./acceptance.md)已接受，[阶段 6 发布包](./release.md)已获授权，阶段 7 实际结果见[部署记录](./deployment.md)，等待用户功能验收。原开发证据见 [开发交付报告](./development-checks.md)，确认与实现状态分别见 [delivery.md](./delivery.md) 和 [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。

原阶段 3 规模：修改 Risk、Operations BFF、Operations Web 3 个应用；新增 10 个业务 API、调整 4 个原 API；Risk 新增 4 表/44 字段/11 索引（4 主键、5 唯一、2 普通），现有表 0 个结构改动；Platform 在 7 张现有表插入 118 行注册并更新 1 行角色版本，0 DDL、0 新 Target；新增 2 个页面容器、扩展 3 个既有页面，Pencil 补 6 张画板；Gateway 0 项生产代码改动。

本次精简修订规模：同 3 个应用，0 新 API、6 个原操作合同/适用范围调整；修改 Risk 3 张旧表，新增 4 列、删除 2 列及 2 个索引，不增删表，停止 3 张旧配套表随规则保存新增；0 注册/初始化 DML、0 页面设计变动。test 预检 462 行仍未迁移；获批本机 SQL 已执行，实际回填 0 行。精确合同及 SQL 只由 [simplification.md](./simplification.md) 拥有，不与原阶段 3 操作数简单相加。

## 阶段 3 设计包

| 材料 | 审阅内容 |
|---|---|
| [接口与合同](./api.md) | 10 新操作、4 原操作；Graph、条件、字段/错误、Owner/BFF 和权限映射 |
| [完整保存示例](./contracts/save-flow.example.json) · [JSON Schema](./contracts/save-flow.schema.json) | 后端可以解析的明确节点/连线/优先级/引用结构，示例未入库 |
| [最小数据方案](./data.md) | 4 表各字段用途、退休/删除资格、保存事务、CAS 与幂等 |
| [Risk SQL/模型材料](./db/risk-compliance-service/README.md) | PDManer 增量补丁、对应 DDL、0 必需 DML；实际应用见本机记录 |
| [Platform 注册清单](./db/platform-support-service/README.md) | 118 行明细、实际 Route 权限绑定、复用 Target、待开放状态 |
| [Pencil 页面](./ui.md) · [六页 PDF](./assets/pencil-final/export.pdf) | 首次新建、顺序、校验、历史与规则动作可选；完整状态和浏览器导航 |
| [研发与验证计划](./implementation-plan.md) | 按模块的改动批次、风险场景和 14 项验收映射 |

关键方案：配置图与旧 Companion/Release 分开；同节点原 RETIRED 引用可保留，已删除引用阻断新保存但历史可读；规则动作可留空，不新增类型或模式字段；含空动作规则的版本不能 Freeze；命令复用 Risk 本地幂等，不增回执表；条件 JSON 有严格 Schema/大小限制，节点和连线关系仍结构化保存。新图没有运行消费者，不存运行状态或内容 Hash，不预建发布/使用关系表。

设计包定义评审通过的目标；研发差异、检查证据和剩余验证范围见 [开发交付报告](./development-checks.md)。模型补丁保留提案证据，现行结构以原生成源、唯一模型和完整 Schema 为准。

原阶段 3 研发确认及四张流程表的本机 DDL 授权已记入 delivery.md。精简数据/可空性方案已确认，按修订稿取代原合同对应依赖；研发证据见修订开发检查。test SQL、制品发布、部署和提交仍依具体阶段授权。

本轮简化：统一原规则编辑器的必填条件与可选动作，取消“仅判断 / 配置决定或附加动作”的模式切换。流程引用摘要只保留规则总数，删除设计中未实施的三项动作分类计数。接口操作数、表字段及注册 SQL 数量均不变。

---

以下第 1–9 节保留为**已确认的阶段 2 分析快照**。其中“仅判断”是此前对空动作能力的工作称呼，现行页面不保留该模式或类型；其中“未定/阶段 3 需要设计”的事项已由上方设计包确认；当前接口/模型状态只见当前台账，不从以下历史快照推断。

## 1. 已确认边界与选型依据

已确认选型方向：前端使用 React Flow 承担画布交互，Risk 拥有独立的流程业务合同；未来执行器由 Risk 自定义，不引入 LiteFlow、Flowable 等后端流程框架。执行器方向限于同步、单路径、无环的顺序/条件分支与结束，不把并行、循环、人工等待恢复纳入默认能力。业务节点、连线条件、引用版本和结束决定与页面坐标/样式分开处理，不直接把 React Flow 的全部状态当作后端执行合同。本期只设计并实现经后续评审批准的配置存取与静态校验，依赖接入在研发阶段处理，执行器实现及真实业务调用不属于本期交付。

现有业务依据为 [Risk 有序规则与终态语义](../../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md#35-action-与系统义务) 及 [Decision Point / Release 模型](../../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md#42-decision-pointfeatureruleaction-与-release19-张)。当前 Candidate 请求接收一个 ruleSetVersionId，不能直接当作多规则集决策流管理接口。

现有数据对象已有决策点版本和有序规则集版本绑定，可作为旧顺序配置的兼容基础。本轮改为轻量分支后，有序绑定本身不能表达连线条件、出线顺序、节点连接和分支结束决定，后续必须评估 Risk 的配置表示、校验及历史快照，而不仅是 Web 连线样式。尚未确定新增表、字段或具体存储方式。

技术评审需覆盖：条件字段和运算符复用当前决策点配置目录；引用准确规则集版本；入口唯一性、节点/出口完整性、不可达节点与环路校验；终态短路与仅无最终决定时继续；历史顺序数据兼容；错误与缺失输入的明确处理。画板中的“校验配置”仅表示静态配置校验，不代表试算或运行执行能力。

预计 Owner：Risk 负责业务配置与校验，Operations BFF 负责授权后的转发和必要页面组合，Web 负责图编辑与只读呈现；如增加外部路由，再评估 Platform 的数据库路由注册。具体系统影响和接口数量在完整技术评审中确认，不据此宣称实现完成。

独立决策流身份、准确 DRAFT/FROZEN 引用、完整结构保存和删除边界已随需求稿确认；下文分析其现有支持程度。本阶段不生成 SQL。接口继续沿用现有版本或默认 v1，代码类型名不带版本。

条件连线修订进一步需要定义普通节点输出字段目录、结果来源的有效性、规则集输出汇总和同源多出口的冲突行为。截图中的“是否命中规则”仅是待设计字段，不能当成已有接口。已有 ALLOW / REVIEW / DENY 仍是最终决定；若要将其改为可继续执行的局部结果，需另行明确业务语义，不能通过 UI 调整静默改变。

阶段 1 已确认的业务规则见 [requirement.md 第 4 节](./requirement.md#4-已确认业务规则)：按显式顺序选择第一个匹配出口，只读取当前决策点输入及当前节点命中结果，只保存结构完整的配置，并允许准确的配置阶段规则集版本引用。精确字段、错误与持久化合同在阶段 3 设计。

原规则配置禁止无终态且无附加动作的规则；本次已批准补充“仅判断”配置，保留终态全流程短路，且不实现命中计算。取代边界见 [本期范围决定](../../../agent-docs/decisions/2026-09-12-risk-decision-flow-configuration.md)。下文将既有规则编辑器、Risk 保存/重建校验与历史兼容列入影响，不能将本期描述为只增加一个画布。

页面已先行评审，实际覆盖的画板和待细化状态见 [ui.md](./ui.md)，画板数不等于功能页面数。本轮不修改 Pencil 或应用代码。

## 2. 核对基线与证据边界

本阶段读取的应用提交基线：Risk `27cf50b`、BFF `6822928`、Web `9165b95`、Gateway `2f1f70e`、Platform `d40b268`。Risk 的 `DecisionDictionaryServiceImpl.java` 已有其他未提交改动，Web 的 `docs/design.pen` 保留前期页面设计；本阶段均不修改。根工作区的其他需求和规范改动同样保留。

当前代码已存在规则集整体管理、历史与字典能力；具体既有测试和 test 发布事实以 [CURRENT_IMPLEMENTATION 第 16/17 节](../../../agent-docs/CURRENT_IMPLEMENTATION.md#16-operations-规则集列表优化2026-09-11) 为准。本次没有访问 test、执行真实 HTTP 或应用 SQL，也没有把历史测试成绩当作决策流功能的验证结果。

源码、PDManer、完整 Schema 及 Web 依赖清单中未发现本期决策流图形管理能力。已有 `RuleNode` 是一条规则内部的条件树节点；已有 `DecisionPointRuleSet` 是顺序绑定；两者均不是开始/规则集/结束及条件连线组成的流程图。[E01]、[E02]、[E03]

## 3. 需求与现状差异

| 需求 / 验收 | 当前行为及证据 | 目标行为 | 缺口与处理建议 |
|---|---|---|---|
| 整体列表、新建编辑、删除；F01/F09 | `t_rule_set` 和规则集 API 按规则集管理根分页、CAS 删除；不存在独立流程编码、根或历史列表。[E01]、[E04] | 同一决策点可保存多条有独立编码的流，每流一行，整体删除并保留历史。 | 需要 Risk 的流程配置生命周期及对应接口；不能将 Rule Set 根改名充当 Flow，也不能把 TEST Candidate 当作流程。 |
| 节点和条件连线；F02/F03/U01 | `t_decision_point_rule_set` 只有点版本、规则集版本和顺序；无起点、终点、出线顺序或结束决定；Web 尚无 React Flow 依赖。[E02]、[E03] | 开始/规则集/结束、顺序直连、有序条件分支和其他情况出口，可提前结束。 | 新增流程业务结构和画布编辑适配；三类节点不等于必须建三张节点表，物理结构在阶段 3 做精简评审。 |
| 条件来源；F03/F04 | 现有规则 AST 的 HTTP 操作数仅 `CONTEXT/LITERAL`；条件校验器绑定 Checkout 字段；Web 构建器也固定字段与控件，不支持节点结果。[E05]、[E06] | 引用当前决策点字段或本规则集节点的布尔“是否命中”，保留分支顺序。 | 可以复用条件组/操作符概念和控件，但须适配数据来源、布尔值及条件校验入口；不能直接在旧字段目录塞一个没有输入含义的 `matched`。 |
| 图形静态校验；F04 | `RuleAstValidator` 校验单规则条件树；Candidate 静态检查固定一个规则集版本及其 Companion 闭包。[E05]、[E07] | 校验唯一开始、完整出口、无环、可达、路径结束、合法引用及条件。 | 新增流程配置校验；旧 AST 的“树无环”不能替代图校验，Candidate validate 不能复用为流程 validate。 |
| 仅判断；F05 | Risk 请求规范化、持久化聚合重建、Web 请求构建和响应解码均拒绝无决定/无附加动作；已有 HTTP 测试明确覆盖拒绝空动作。[E05]、[E08]、[E09] | 显式配置仅判断，保存和回显成功；不执行命中计算。 | 同时调整写入和回读路径及测试。现有 `action:null + additionalActions:[]` 已有字段表达能力，不能仅因增加这个选项就预定新表或新字段。 |
| 引用准确版本；F06 | 规则集创建会配套独立 Point Version；保存新规则版本会将旧 DRAFT 置 RETIRED，已有 FROZEN 保留；单个管理聚合要求一个绑定。[E08]、[E10] | 同一点下组合多个准确版本，后续规则编辑不静默改流。 | 不能把多个规则集写进旧的一对一 Companion。要分别核对点、输入合同和版本资格，区分新选择、已保存引用、历史读取。 |
| 保存与并发；F07 | 规则集有业务版本、内容 Hash、命令幂等和资源锁；这些键、回执和事务均以规则集为对象。[E10]、[E11] | 整份图保存为不可变版本，并发冲突明确且不覆盖他人。 | 复用现有公共机制及 Risk 本地模式，增加流程资源坐标与事务；不把流程写事务放到 BFF，不自动迁移 Foundation 幂等组件。 |
| 历史准确展示；F08/U02 | 现有历史是规则集版本列表和只读规则编辑器；根删除后公开规则集详情返回 404，底层历史仍保留。[E04]、[E10]、[E12] | 流程历史保留图、条件、结束配置与原引用，即使规则集后来退休或删除也可准确展示。 | 需要受流程历史上下文约束的 Risk 读取或已保存展示快照；仅让浏览器调用原规则详情接口会丢失展示。不能无条件开放被删除根的管理查询。 |
| 拒绝原因；F02/F08 | 字典选择、消息覆盖与历史保存规则可复用；引用查询只返回规则集/规则坐标，SQL 只遍历 Rule Action。[E13]、[E14] | 结束节点选拒绝原因并保存当时文案；字典变化不重写流程历史。 | 保存须接入既有原因可选性规则。新增消费者后，建议引用明细支持流程结束节点，避免“暂无引用”漏报；该接口/页面补齐作为本阶段影响建议供确认。 |
| 页面交互；U01–U04 | 原页面有查询参数导航、未保存保护、只读规则工作区；规则条件组件假设固定业务字段。Pencil 是设计材料，尚无图编辑器。[E06]、[E12] | 图编辑、点击连线侧栏、只读平移缩放、浏览器回退恢复筛选和历史层级。 | 复用导航/离开保护模式，不直接复用规则集页面状态机；补图选择状态、校验定位、空/失败状态及只读约束。 |
| 授权及公开入口；F10 | BFF 统一拦截器先按可信 Route 调用 PLT-I02；Gateway 按数据库 Snapshot 解析 Route/Target；Platform 注册有 Route、Guard、Header/Query、Permission 与 Console Resource。[E15]、[E16]、[E17] | 新流程操作有独立可核对的读/写授权，经原 BFF 到 Risk。 | 增加所需注册数据并复用原 BFF Target；Gateway 不增加业务路径名单。新 Route 的 Body/Query/Header 限制要与设计同步。 |
| 配置与执行隔离；F10 | 目前 Freeze 只显式阻止附加动作；Candidate 仍要求旧单规则集终态闭包。[E07]、[E10] | 保存/静态校验不执行，未就绪配置不能通过旧入口误当成可运行内容。 | 放开仅判断后必须补其冻结限制与 Candidate 兼容回归；不通过虚假 Handler、模拟业务成功或扩宽旧发布校验解决。 |

## 4. 逐系统影响

| 系统 / 仓库 | 本期职责与影响点 | 代码 / 数据 / 依赖判断 |
|---|---|---|
| Risk & Compliance | `risk-api` 增加流程存取与校验合同；`risk-web` 承担 HTTP、错误与本地事务编排；`risk-decision` 承担流程身份、版本、引用、图校验、仅判断配置及历史读取；`risk-test` 补公开入口回归。拒绝原因引用查询需要能识别流程来源。 | 必改应用代码；Risk 自有数据库需承载流程配置，具体 DDL 未定；同步 PDManer、完整 Schema、必要初始化与需求增量 SQL。既有条件和命令基础能力可复用。 |
| Operations Console BFF | 新流程 Controller/Client、浏览器模型与 Owner DTO 转换；注册 `@HttpExchange` 代理；成功响应结构、标量与错误投影；按需组合流程与引用的页面信息。 | 必改代码、升级所消费的同版本 `risk-api` 制品内容；无数据库。统一 PLT-I02 拦截器可复用，不复制 Risk 图校验或业务事务。 |
| Operations Console Web | 安装已选 React Flow；实现列表、图编辑、连线条件、准确版本选择、只读详情/历史、菜单删除及浏览器导航；修改既有规则编辑/构建/解码以支持仅判断；按确认的范围完善原因引用来源展示。 | 必改代码、依赖及锁文件；复用 Ant Design、已有条件组/字典选择的适用部分。当前“按字段名查固定目录”的逻辑需适配布尔节点结果。 |
| Platform Support | 注册流程操作 Route、权限、资源/菜单以及必要 Header/Query/Guard 关联；复用现有 Operations BFF Target、PLT-I02 和 Snapshot 能力。 | 预计只改初始化/增量 DML 及对应注册测试工件，不改业务 Java、Schema 或新建 Target。既有测试若固定注册数量/集合，更新其数据断言不代表新增业务逻辑。 |
| API Gateway | 接受 Platform 新 Snapshot，执行相同 Channel、认证、Body/Query/Header 约束后转发。 | 预计 0 项生产代码改动、0 新 Target；需要后续真实入口验证 Snapshot 收敛、路由匹配及边界。无需为新增流程路径重新硬编码白名单。 |
| Foundation / Build | 继续提供 HTTP、Problem、资源锁、事务支持、JCS 等现有消费者能力。 | 当前没有发现必须改 Foundation 或引入后端流程框架的缺口；不创建新的公共工作流基础组件。 |
| CCT / Commerce / Payment / Finance / Vault | 本期没有运行决策调用方、真实主体数据或动作写入。 | 不改应用接口、数据库或事件；不为本期补真实业务输入、标签事实、限制或评估记录。 |
| 根需求与规范材料 | 记录已确认 RCO-17、取代原空动作限制的窄范围，同步全局需求/时序目录及本需求分析。 | 文档及需求站内容变更；实际实现/验证/部署状态仍由当前台账维护，不能随需求确认标为完成。 |

## 5. 必须处理的兼容点

### 5.1 “仅判断”不能只删一条校验

写入前 `RuleAstValidatorImpl`、落库后 `DecisionRuleSetAggregate`、Web `editorToCreateRequest` 和 [响应解码的 decodeRule](../../../operations-console-web/src/risk/riskResponseValidation.ts) 都有空动作拒绝。只调整其中一个，会出现前端不能提交、服务端事务失败，或保存后页面把成功响应判坏等问题。

旧配置格式区分 `LEGACY` 与 `CONFIGURATION`，且有不同的 Hash 投影和历史回放。仅判断使用哪种受控表达、是否仅允许完整配置格式，在阶段 3 明确；不能把缺失字段、损坏动作引用或旧格式数据自动解释为合法的仅判断。当前配置字段已有可空 action 与附加动作列表，因此本阶段没有发现“仅判断必须新增数据库列”的依据。[E01]、[E05]、[E08]、[E18]

当前 Freeze 的执行就绪检查只看附加动作，放开空动作后需显式覆盖仅判断；否则配置会越过现有冻结边界，随后又被 Candidate 单终态校验拒绝。旧终态规则的冻结、Candidate、Hash Golden 和成功命令回放应继续通过。[E07]、[E09]、[E10]

### 5.2 流程是配置，不是旧 Point Version / Release 的替身

`DecisionPointVersion` 还拥有输入/输出合同、默认决定、FIRST_TERMINAL、FAIL_CLOSED 和系统能力证据；当前规则保存为每个规则集版本创建自己的 Companion。流程有独立编码并可组合多个规则集，不能直接占用某个 Companion，也不能把其 bindings 数量从 1 改为 N 就声称完成。

建议新增图配置与旧顺序闭包并存，旧默认决定、历史 Hash 和 Release 数据不回填重写。旧顺序数据只在读取时按有证据的顺序投影展示，不伪造独立流程编码、保存人或历史版本。准确入口和只读转换规则留在阶段 3；旧单规则集不自动变成新流程列表中的一行。[E02]、[E07]、[E10]、[E18]

### 5.3 版本退休、删除与历史展示

“引用 DRAFT”不等于“追随最新”。旧 DRAFT 在规则另存后会变成 RETIRED，所以流程保存后的引用可能合法地指向一个已退休版本。建议：历史原样展示；新选择排除 RETIRED/已删除规则集；编辑旧流时既有引用的保留/替换资格和提示须在阶段 3 给出明确矩阵，不修改旧规则生命周期以迁就画布。

已删除规则集的公开详情当前返回 404，不能用它来拼装完整流程历史。应由 Risk 在获准的流程读取上下文内提供必要历史事实，BFF 只组装页面模型，不绕过 Owner 保护或跨库读取。[E04]、[E10]

源码/模型中的 Release 只引用 Point Version，没有独立 Flow 引用；因此本期新流没有实际发布使用关系。建议不新增占用表或假的 `inUse` 查询；保存不写 Release，整体删除只管理配置与保留历史。现有规则集的 ACTIVE Release 删除门禁保持，不因普通流程引用就阻断规则集删除。将来接入真实发布时，必须先补准确的流版本引用和使用保护。[E01]、[E14]

### 5.4 条件输入能力保持窄范围

当前 Authoring Context、Risk Validator 和 Web 都限制在 CHECKOUT_PREPARE。建议本期只开放这个已有决策点，界面从 Risk 能力目录展示可用项，不提前接入 PAYMENT、PAYOUT 等决策点。多个同点规则集还需核对它们的准确输入合同与字段资格，不能仅凭 pointCode 相同就推断所有字段均可用。

本节点“是否命中”是新的配置来源声明；只定义布尔含义和可引用范围，不存运行命中结果。结构可保存、条件合法与未来真实求值成功是三个不同结论。金额继续使用现有十进制字符串规则，不能因画布坐标是 JSON Number 就把业务金额也改成浮点数。[E05]、[E06]

### 5.5 字典、接口解码及入口边界

结束节点会成为拒绝原因的新消费者；现有引用查询只认识 `ruleSetId/ruleSetVersionId/ruleCode`。建议将流程结束节点纳入引用明细的来源类型，查询直接从业务配置取得，不建字典回执、通用引用账本或标签适用关系表。这个补齐会涉及 Risk API、BFF 严格响应解码和 Web 跳转，需在阶段 3 明确兼容字段及统计为接口修改。[E13]、[E14]

BFF 的 [请求 Shape](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/configuration/RiskRequestShapeValidator.java) 和 [响应 Shape](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/configuration/RiskOwnerResponseShapeValidator.java) 使用精确属性集合，Web 也做严格解码。即使仍用 `/v1`，新能力字段或来源类型也必须成套更新，不能只更新后端并假设前端忽略未知内容。旧既有请求/响应不无故删字段或改变字段含义；新规则配置应在相应 Reader 就绪后开放。

目前规则保存 Route 的上限是 65,536 字节，Gateway 还拥有安全绝对上限。图配置应优先保存准确规则集引用而非嵌入全部规则；阶段 3 据实际 Payload 确定节点/连线/条件和字节边界，避免为一个画布先扩大网关全局限制。规则内容、条件值和变更原因延续元数据日志边界，不进入普通请求体日志。[E16]、[E17]

## 6. 数据与接口工作的边界

| 分类 | 分析结论 | 阶段 3 需要交付 |
|---|---|---|
| 流程业务 API | 现有 API 没有流程身份、图存取或图校验。列表、详情、历史、保存、删除、配置目录/校验均需有真实合同承载。 | 按业务 Method + Path 去重统计新增与修改；明确新建/编辑是否共用保存、目录与选择器是否复用原查询；Owner 与 BFF 落点另列，不重复计算业务操作。 |
| 原规则 API | 保存路径/已有结构可沿用；仅判断改变校验语义和回读，冻结保持执行就绪门禁。 | 旧/新格式、必填/可空、目录能力、错误及 Golden 兼容，不升级到 v2、不在类型名追加版本。 |
| 字典 API | 列表、选项和原因读取可复用；若接受本阶段建议，引用查询扩展流程来源。 | 明确响应来源判别、历史/删除归属和 UI 跳转，不让规则集字段假装流程字段。 |
| Risk DDL | 现有模型缺少流程配置事实；当前 `t_rule_node/t_rule_operand` 属于规则 AST，不直接挪作流程节点/条件。仅判断不天然需要新表列。 | 精简的数据方案，逐表字段用途、类型、索引、历史与查询示例；同步 PDManer、完整 Schema 和单项增量 SQL。不得预建运行表、执行日志表或流程框架表。 |
| Risk DML | 尚未确定必须新增的工程配置目录；不需要为了页面示例初始化真实业务流程。 | 区分必须的能力注册与可选合成验收数据，给出准确数量、执行顺序和目标库。 |
| Platform DML | 新公共操作需要数据库注册，复用已存在的 Operations BFF Target。 | Route、Permission/API 绑定、Console Resource、Header/Query/Guard 按实际复用后统计；不把注册数据行数误算作新增 API 数量。 |
| 平台/执行设施 | 本期不改 Platform Schema，不建运行引擎基础表，不改其他业务数据库。 | 如阶段 3 发现确切阻塞再给出证据调整范围，不能预先加表或发布制品。 |

本阶段没有承诺新增表的精确数量，也没有生成可执行 DDL/DML。所列两库变更材料是后续方案的工作范围，不表示已经获准应用数据库。

## 7. 工作量来源、依赖与后续验证

主要工作量在 Risk 的配置模型/静态校验/历史兼容，以及 Web 的图编辑和只读交互。BFF 以合同适配为主；Platform 以数据注册为主。不是只有增删查接口：引用退休/删除、完整保存、并发与旧 Reader 是决定开发量的部分，当前不在模型和接口未定时编造精确工期。

建议后续研发顺序：先冻结 Risk 业务结构与 API，再实现 Risk 配置闭环，随后 BFF 适配和 Web 消费，最后对齐 Platform 注册与真实入口。独立界面可按已冻结合同推进，但不提前开放尚无后端能力的保存入口。

| 后续验证范围 | 应覆盖行为 | 对应验收 |
|---|---|---|
| Risk 静态领域与真实 HTTP/MySQL | 图结构失败、来源和类型、仅判断往返、引用资格、删除/退休历史、版本并发、保存幂等及无运行副作用。 | F01–F10 |
| 既有规则/Candidate 回归 | 原终态/附加动作配置、旧格式和 Hash Golden、冻结保护、Candidate 单闭包、字典旧项兼容及删除回放。 | F05–F08/F10 |
| BFF 与 Owner 协议 Simulator | 授权失败 Risk 零调用、准确 DTO/错误转发、Body/Query Shape、新旧成功响应解码、依赖失败。 | F04/F07/F10 |
| Web 与浏览器 | 图保存后还原、分支顺序、只读限制、错误定位、未保存保护、浏览器返回恢复、原因引用跳转及空/失败状态。 | F01–F10/U01–U04 |
| 部署阶段真实入口 | 已注册 Route/权限/菜单可用，拒绝不合法 Body/Query，无新增硬编码 Target，静态资源与新接口相配。 | F10/U01–U04 |

本阶段只审阅了既有测试源码，没有运行上述应用回归；验证计划不是测试通过声明。研发测试继续遵循 [UNIT_TEST_GUIDE](../../../agent-docs/UNIT_TEST_GUIDE.md)，SQL 和部署依既定阶段另行列出具体执行清单。

## 8. 本阶段建议确认项

1. 接受“3 个应用代码 + Platform 注册数据”的影响范围，Gateway 预计只验证动态配置，不改业务路由代码。
2. 本期仅开放已有 CHECKOUT_PREPARE 配置目录，不借此次画布扩展其他业务决策点。
3. 配置图与旧规则集 Companion/Release 并存；保持旧历史，不新增真实流运行引用或使用占位。
4. 接受拒绝原因引用明细补齐流程结束节点来源，避免新增消费者后引用页漏报；不新增辅助引用表。
5. 进入阶段 3 完成 API/数据/页面细节与准确规模统计，再交付确认；本次确认不进入编码或执行 SQL。

## 9. 源码证据索引

以下链接固定到本次核对文件；精确基线见第 2 节。

[E01]: ../../../paytech/risk-decision/paytech-risk-decision-test/src/test/resources/init/ddl.sql
[E02]: ../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/core/dal/dataobject/DecisionPointRuleSet.java
[E03]: ../../../operations-console-web/package.json
[E04]: ../../../paytech/risk-decision/paytech-risk-decision-api/src/main/java/com/paytech/risk/decision/api/DecisionRuleSetManagementApi.java
[E05]: ../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/rule/RuleAstValidatorImpl.java
[E06]: ../../../operations-console-web/src/risk/ruleBuilder.ts
[E07]: ../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/release/DecisionReleaseStaticValidatorImpl.java
[E08]: ../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/rule/DecisionRuleSetAggregate.java
[E09]: ../../../paytech/risk-decision/paytech-risk-decision-test/src/test/java/com/paytech/risk/auto/ext/http
[E10]: ../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domainservice/service/impl/DecisionRuleSetServiceImpl.java
[E11]: ../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/service/processor
[E12]: ../../../operations-console-web/src/risk/ReadRuleSetWorkspace.tsx
[E13]: ../../../paytech/risk-decision/paytech-risk-decision-api/src/main/java/com/paytech/risk/decision/api
[E14]: ../../../paytech/risk-decision/paytech-risk-decision-core/src/main/resources/mapper/decision/DecisionConfigurationQueryMapper.xml
[E15]: ../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/ext/rest/OperationsRequestInterceptor.java
[E16]: ../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/routing/snapshot/GatewaySnapshotCompiler.java
[E17]: ../../../paytech/platform-support/docs/database/sql/platform-support-initial-data.sql
[E18]: ../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/hash/RiskDecisionCanonicalizerImpl.java
