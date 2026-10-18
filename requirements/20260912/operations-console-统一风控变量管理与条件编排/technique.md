# 现状差异与系统影响分析

阶段 2 分析记录（已确认）。用户已确认[阶段 1 范围](./requirement.md)，本阶段只分析，不修改应用、模型、SQL 或环境。确认与证据见 [delivery.md](./delivery.md)；实现/部署状态由[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#rco19-variables-20260912)维护。

**一句话规模：需要修改 Risk、Operations BFF、Operations Web 3 个应用，并增加 Platform 的菜单/权限/Route 注册数据；Gateway 和 Foundation 暂无已证明的代码改动需要。持久化建议以“变量定义 + 集合成员”2 张新表为设计起点，复用现有规则/流程结构；准确接口、旧表字段、索引及 DML 数量在阶段 3 固定。** 两表是供确认的精简建议，不是已批准模型或已执行 DDL。

阶段 3 已形成[完整方案评审稿](./design.md)，接口/表字段/SQL/Pencil数量以该稿为准；以下保留阶段 2 的分析证据与设计起点，不再作为未进入方案的状态声明。

## 1. 结论与当前边界

1. 当前变量能力不能只靠改数据完成。Risk 的目录、规则操作数与流程条件、BFF 合同、Web 编辑和只读还原都有需要调整的代码。
2. 现有可配置输入是 `CHECKOUT_PREPARE / ORDER_PREPARATION / 4` 下的 10 个字段，其中 9 个可用于规则/流程条件，`client_id` 仅用于动作主体绑定。另有写死的 `matched` 节点输出。迁入统一目录不自动扩大这些字段的用途。
3. 字段变量、公共常量、集合和节点输出需要统一身份与元数据；通用类型、比较运算和实际输入/输出合同继续由代码实现。新增已支持来源的变量应仅需维护库中定义，不能再增加逐变量分支。
4. 共享常量/集合采用最新成功保存值已确认。规则/流程内容仍保存变量引用；修改变量值不应触发重存所有规则、改变原内容 Hash 或重建全部流程版本。
5. 本期配置管理保留 matched，并增加节点处理结果条件；真实取数执行、流程执行、计算/统计变量、跨系统新增取数仍延期。现有示例迁移安排在变量能力部署后。

## 2. 现状差异与能力缺口

证据编号见第 9 节，表内“建议”是阶段 2 的处理边界，不替代阶段 3 合同。

| 需求 / 验收 | 当前行为及证据 | 目标行为 | 缺口与建议 |
|---|---|---|---|
| 唯一变量库 AC-01/02/11 | Risk Descriptor 固定定义字段、类型、用途；Web 校验固定九字段顺序与类型后才允许编辑 [E1/E4] | 变量库统一拥有业务定义，两处编辑器消费相同目录 | 新增变量持久化及管理查询；移除新配置链路的第二份固定目录 |
| 仅维护数据即可新增变量 AC-12 | RuleAstValidator 构造时建立字段 Map；页面取回目录仍被静态字段白名单校验 [E2/E4] | 支持来源内新增定义无需改代码、发布或重启 | 校验器接收本次操作的库目录；前端用动态目录，刷新时保留草稿 |
| 字段元数据动态化 AC-02/06 | Risk Converter 用 fieldCode switch 生成名称/值域，RuleAstValidator 按具体字段校验国家、行业、币种等 [E2/E3] | 名称、来源映射、用途和值域关联由库管理 | 将具体字段关联数据化；保留通用格式/类型校验器，不把任意程序搬入数据库 |
| 公共常量 AC-03/09 | 仅支持条件内 LITERAL，值保存在每条操作数中 [E2/E8] | 多个条件引用一个常量，更新一次被所有引用采用 | 需要稳定变量引用、当前值查询；不得以保存时复制常量替代动态引用 |
| 集合 AC-04 | 规则/流程的 IN 仅支持最多 100 个直接字面量，没有命名集合及成员管理 [E2/E5] | 集合可查询、批量录入、去重和复用 | 需要成员持久化与管理；集合引用不能继续受“条件内 100 个字面量”的结构限制，集合容量另定 |
| 双侧操作数 AC-05 | 规则要求恰好 1 个 CONTEXT，其余为 LITERAL，FEATURE 被拒绝；流程为一个 source + values [E2/E5] | 左右各自独立选择变量或常量 | 调整 API、领域操作数、校验、持久化及 Web 模型；不能只更换下拉框 |
| 节点结果 AC-07 | Risk 校验 NODE_RESULT 仅 matched + Boolean，Web 直接插入 matched，响应解码亦仅接受它 [E3/E5] | matched 与处理结果都在库中登记，按当前节点过滤 | 目录、类型和可选值结构需要扩展；REJECT 后可继续的语义必须与旧终态含义分清 |
| 动作使用的变量 AC-11 | 当前 Client 主体字段来自同一个静态 Descriptor，目标绑定仍固定指向 client_id [E6] | 同一业务变量不在动作链另建目录 | 将现有字段映射/用途校验接入变量库；不新增冷却/标签执行器，也不把 client_id 变成默认规则条件 |
| 引用查询与删除保护 AC-09 | 已有字典引用和规则/流程版本查询，不存在通用变量引用查询 [E7/E8] | 查清变量被哪些规则/流程及版本引用 | 同时覆盖 AST、动作字段引用和流程条件树；当前/历史分别展示，不能漏掉 JSON 条件中的引用 |
| 历史详情 AC-08 / UI-04/06 | 后端按固定目录重建规则并校验原 Hash；Web 只读页也依赖静态展示目录 [E4/E8] | 原结构/直接常量/引用身份保留，动态展开值标为当前值 | 新引用与旧 CONTEXT/LITERAL 要有明确 Reader 和规范投影，不用最新目录重算旧协议快照 |
| 最新值 AC-09/12 | 当前没有共享值及其刷新机制 [E1/E4] | 成功更新后引用采用最新值，无需重存规则 | 明确每次查询/保存如何取得最新定义、页面刷新与多实例行为；优先评估直接查库和批量读取，避免先建复杂缓存平台 |
| test 示例 AC-13 | 示例为金额 > 8000、matched = true、currency != USD，前序真实接口核对通过 [E10] | 改为变量引用，初始分支含义不变 | 新变量注册后保存示例规则新版本，再保存引用它的流程新版本；不是修补损坏行 |

## 3. 逐系统影响

| 系统 / 工件 | 职责 | 本期影响 | 变更判断 / 依赖 |
|---|---|---|---|
| Risk / risk-api | Owner HTTP 合同 | 变量管理/成员/引用/可用目录；规则、流程操作数及详情元数据合同 | 改 API 代码；沿用原路径版本或默认 v1；更新 Risk API 制品供 BFF 使用 |
| Risk / risk-decision | 变量事实、校验、持久化、历史 | 变量及集合维护、类型/作用域/引用验证、目录读取、AST/条件编解码、原 Hash 兼容、字段用途接入 | 改业务代码、Mapper、模型/DDL/DML；集中在已有业务模块，不新增独立变量服务 |
| Risk / risk-web、risk-test | 入口、事务/并发/幂等、验证 | 新管理 Controller/Processor；现有目录装配改为库读取；保存与变量维护的并发保护、有限验证 | 改代码和测试；复用当前 Foundation 能力，不顺带迁移通用幂等体系 |
| operations-console-bff | 逐请求授权、Risk 转发及展示模型 | 新变量操作转发、元数据/双侧操作数适配、HTTP 错误字段映射、只读详情所需组合 | 改代码/合同/测试；无业务表、不缓存变量真值，不跨库查询 |
| operations-console-web | 管理页面、两处编辑及只读展示 | 新变量列表/编辑/集合维护/引用查看；两侧变量或常量；移除静态目录及默认 country/matched；详情历史共同适配 | 改页面、模型、解码和相关测试；沿用 React/Ant Design/React Flow；阶段 3 用 Pencil |
| Platform Support / mor_platform | Operations 菜单、权限和路由配置 | 新入口的 Console Resource、查询/维护权限、Route 和绑定注册 | 预计只新增/调整 DML，复用现有 Operations BFF Target；现有 API/应用代码无已证明改动需要 |
| API Gateway | 数据库 Snapshot 路由转发 | 消费 Platform 新 Route；核对入口 Body 限制和路由模板不冲突 | 预计 0 代码、0 新业务白名单；是否调整单条 Route 参数由阶段 3 的精确合同决定 |
| Foundation | 通用锁、JSON、HTTP 错误与基础能力 | 当前已有所需通用能力 | 暂不改代码或发布新公共能力；Risk 保留业务 Hash 投影和引用并发职责 |
| CCT / Commerce / Payment / Finance / Vault / Risk control、compliance | 原始业务事实及其他业务 | 本期复用已有批准输入，不新增远程取数、执行和敏感原值访问 | 不改业务实现；变量元数据不得冒充这些 Owner 新提供的数据 |
| 根工作区 / test-environment | 权威材料、模型生成、发布和示例 | Risk 模型生成源/Schema 检查，需求目录与全局时序，后续注册/迁移及部署材料 | 改相关工件；当前只有文档变更，不执行部署、迁移或提交 |

范围按部署单元概括为 **3 个应用代码改造 + 1 个系统注册数据调整**。Risk 内多个 Maven 模块不重复计算成多个应用。

## 4. 硬编码清理边界

| 位置 | 应调整内容 | 应保留 / 隔离内容 |
|---|---|---|
| CheckoutPrepareAuthoringDescriptor / RiskRuntimeConfiguration | 字段集合、类型和用途的固定装配，启动时固定的字段索引 | 当前业务支持的输入/输出合同；旧合同若作为历史解码依据保留，必须与新目录明确隔离 |
| RuleAstValidatorImpl | 单 CONTEXT 结构限制、字段专用值域分支、固定字段索引 | 条件组/深度/序号、不合法类型拒绝、通用格式转换、运算符语义 |
| DecisionFlowWireConverter / Validator / JsonCodec | label/literalDomain 的逐字段 switch、固定 matched、source+values 的单源结构 | 图拓扑、节点类型、条件优先级、ELSE、严格 JSON 键/类型校验及大小限制 |
| RuleActionConfigurationCatalog / Converter | 当前字段来源、主体字段映射及用途的固定定义依赖 | 已有动作类型、工程 Handler、真实输出语义及历史动作版本；变量管理不等于可任意新增动作 |
| BFF RiskDecisionConfigurationClientImpl | 固定 Descriptor/Point 的目录限制、required=true 等过窄字段假设、CONTEXT/LITERAL 来源结构 | ID、类型、请求/响应身份、分页、权限、错误协议校验；该实现并非完整复制九字段名单 |
| BFF RiskFlowResponsePolicy / Converter / Models | 目录与双操作数合同同步，支持新增变量来源/业务值域关联 | 字段格式、重复定义、类型/大小边界；不能为去硬编码改成透传任意 JSON |
| Web riskAuthoringCatalog / ruleBuilder / ConditionBuilder | 固定字段顺序/类型/标签、逐字段格式关联、静态默认值、模块级目录 Map | 通用控件、运算符显示与编辑操作；字段身份/选项从目录来 |
| Web decisionFlow / Api / FlowConditionEditor | 默认 matched 表达式、硬插 matched 选项、响应 matched-only 限制、单 source 编辑模型 | React Flow 画布、折线、浏览器返回、只读和未保存草稿交互 |
| Web 规则详情/历史/流程内规则弹层 | 静态目录还原、用当前值冒充历史值的可能路径 | 同一只读编辑布局，无保存入口，真实历史身份及直接常量 |

测试中的已知历史字段样本可以保留，不能为了“搜索不到字符串”删除历史回归。新功能验收要证明：加入此前代码不认识的合法变量，三个应用无重启/发布即可完成发现、保存与读取。

## 5. 数据模型复用与最小新增建议

### 5.1 现有模型为什么不能直接充当变量库

| 现有结构 | 当前真实用途 | 复用判断 |
|---|---|---|
| t_feature_version | 工程取数/计算 Handler 的准确执行版本，含实现版本/哈希/超时，输出为标量 | 保留供后续计算类变量接入。直接塞公共阈值/集合将需要虚构 Handler 或重写既有语义，不建议作为本期总变量表 |
| t_feature_dependency / t_decision_point_feature | Feature 执行依赖与旧 Point Version 的可执行绑定 | 不用作普通变量适用关系，不删除，也不启用未交付的依赖调度 |
| t_decision_point_input_field | 旧 Point Version 下的不可变输入字段快照 | 保留历史，不恢复每存规则就复制协议字段的机制，也不原地把历史快照改为实时目录 |
| t_rule_operand | 结构化的条件左右操作数、顺序、类型及 Feature/Context/直接常量值 | 复用 AST/类型值列；需增加或明确统一变量引用的表达。不能把 field_code 无说明地改成另一种身份；准确列变更待阶段 3 |
| t_rule_action_parameter | 已有动作参数的 Context/Feature/直接常量绑定 | 复用；现有主体字段接入变量身份/目录可能涉及列或模型注释变更，不扩展动作值配置范围 |
| t_rule_set / t_rule_set_version / t_risk_rule / t_rule_node | 管理身份、不可变版本、规则和条件树 | 保留，不复制新规则体系；内容格式标识/Hash 分派能否直接复用在阶段 3 明确 |
| t_decision_flow / version / node / edge | 流程管理身份、不可变图版本、准确规则集版本和连线 | 保留四表。condition_json 可以承载新条件，但当前 Reader 严格限定旧结构，必须改合同/Reader，不能认定“JSON 不改代码也支持” |
| t_rule_reject_reason / t_rule_subject_tag | 拒绝原因、主体标签的专门字典 | 继续拥有原事实；复用 CRUD/CAS/只读/引用展示的实现经验，不能混装国家集合或公共金额阈值 |
| 既有命令幂等与发布表 | 规则/流程命令回放、旧 Candidate 与发布事实 | 复用相关机制，保留原语义；不为每次变量维护新增通用回执体系 |

### 5.2 建议进入设计的两张表

- **变量定义：** 一个稳定变量身份，承载业务元数据、来源映射/适用范围、类型、状态、当前标量常量值及维护并发信息。
- **集合成员：** 归属某个集合变量的类型化成员，支撑查询、批量维护与去重。将它与变量主表分开，是为了成员管理的实际消费者；不为每个成员再建历史版本。

这是两个逻辑实体的建议。阶段 3 再确定表名、类型、字段和索引；不为了把总数固定在 2 而把多值关系塞入任意 JSON。适用关系、字典引用与反向查询若确有额外存储需要，应说明不可复用的原因和最小增加项后评审，不能默默扩表。

不预建变量版本表、变量变更回执、通用审批表、泛化依赖图或重复条件表。共享值采用最新值不需要自动维护一套不可变变量版本史。当前没有批准删除任何既有表。

现有 flow condition_json 的已批准用途是整树读取，不做数据库 JSON 路径检索。变量反向引用查询是新增消费者：需比较按已保存结构解析与必要的结构化索引方式，说明分页/完整性/性能成本；本阶段不默认增加引用影子表，也不静默修改原 JSON 查询约束。

## 6. 接口与注册数据影响边界

本阶段识别操作族，不冻结新增 API 数量或 Request/Response。

| 操作族 | 处理方向 |
|---|---|
| 变量列表、定义详情、维护、可用性与引用查询 | 需要新增 Risk 操作及 BFF 对应入口；可用目录读取权限与维护权限分开 |
| 集合成员查询和批量维护 | 需要业务操作；独立端点还是变量维护子操作由容量、分页与原子性决定 |
| 原规则 Authoring Context、保存及按版本详情 | 修改原接口，提供库中变量元数据并支持统一操作数；当前列表、历史入口按实际新增展示字段评估 |
| 原流程 Authoring Context、校验、保存、当前/版本详情 | 修改原接口和受控条件结构；保留原 Method/Path 版本，兼容明确的旧内容读取 |
| 流程内准确规则引用、只读原顺序图 | 返回或还原规则详情的消费链必须同步适配；历史坐标与所引用规则版本不变 |
| Freeze / Candidate / 原成功命令回放 | 优先保持旧功能语义，对新动态变量配置设置明确的能力门禁；未实现真实求值前不能自动放入旧执行就绪路径 |
| Gateway 注册 | 新 API 在 Platform 注册 Route、权限/资源绑定；复用 Operations BFF Target，不加静态 Gateway 业务名单 |

数据工件分为 Risk Schema、Risk 变量注册/必要迁移、Platform 菜单权限路由 DML，以及 test 限定示例迁移。将来按库分目录准备，逐项说明操作顺序；不能用全量初始化 SQL 覆盖现有 test 数据。

## 7. 主要兼容问题与阶段 3 必须解决的事项

| 问题 | 影响 | 建议设计方向 |
|---|---|---|
| 动态值与不可变内容/Hash | 若把展开后的当前值放入原规则内容，修改阈值可能导致历史读取报损坏，重试亦可能变成不同请求 | 规范内容固定变量引用与直接常量，当前值作为查询展示；保存命令回放不以最新值重建原请求。逐格式验证旧 Hash 不变 |
| 旧目录参与历史校验 | 旧九/十字段协议快照有准确 Hash，当前数据库新增一个字段不能影响它 | 精确旧 Reader/旧快照保持，新配置改用变量库。不能用“最新合同”兜底，也不能让新配置仍依赖静态白名单 |
| 类型或来源映射变化 | 名称改动与从金额改成国家字段的影响不同；最新值策略不等于任意改类型/来源 | 有引用的结构性属性保持稳定或经显式处理；值/名称等可更新。具体可编辑矩阵、停用/删除及历史展示在设计中列明 |
| 更新与保存/删除并发 | 新增引用与删除变量、批量更新集合成员可能互相穿插 | 复用现有资源锁、短事务、CAS；锁实际变量/规则/流程，确定顺序，避免租户大锁。集合成功修改原子可见，不暴露半批成员 |
| 目录新鲜度 | 后端启动缓存和前端静态目录都会让新增变量不能立即使用 | 建议首期 Risk 按操作批量查询需要的定义，BFF 无业务值缓存；Web 重新进入/刷新目录时更新且保留草稿。精确生效时点在阶段 3 约定 |
| 节点处理结果与原终态 | 原 ALLOW/REVIEW/DENY 终态与“REJECT 可继续另一个节点”不能混为同一执行语义 | 延续 RCO-18：matched 保留；唯一结果 Wire、无结果及多规则汇总给出简单明确方案，旧 Candidate 不被重解释。本期只配置 |
| 整理字段范围 | client_id 原本仅动作可用；变量登记不应无意变为可选规则输入 | 迁移保留用途和准确合同；新增受支持变量通过库配置范围，取不到的数据不得填默认值冒充已有能力 |
| 发布与旧客户端 | 旧 Web/BFF 严格解码会拒绝新来源；旧 Risk 无法读取新条件结构 | Schema/注册先准备、兼容 Reader/各应用按清单推进，功能启用后才写新引用；不能单独更新 test 数据或只发布 Web |
| 引用完整性与性能 | 只查规则 AST 会漏掉流程 JSON 或动作引用；每个控件逐个查询变量又会产生大量请求 | 集中批量加载所需定义；覆盖所有本期消费位置，按当前/历史分页，并明确查询实现与必要索引 |

全局规范中的“Rule Operand 只有 Feature/Context/Literal”“共享工程输入目录”“新增 Handler 需工程发布”需要在方案阶段作窄范围修订：新增统一变量引用替代新配置的工程字段目录；真实 Handler 的研发约束继续有效。旧 RCO-17 精简决定作为历史保留，不能把它误写为已经有动态变量库。

## 8. test 示例迁移与验证安排

目标沿用 `DEMO_FLOW_AMOUNT_CHECK` 与 `DEMO_CHECKOUT_BRANCH_FLOW`，细节以 [requirement.md 第 2.5 节](./requirement.md)为准。前序真实 API 核对显示两者均为第 1 版、可配置，引用完整，5 节点/4 连线静态校验通过；这是已有功能的检查，不是统一变量支持证据，执行迁移前需再读最新版本。

建议顺序：准备变量定义（包括已有输入/节点输出、金额阈值 8000、标准币种 USD）→ 保存示例规则新版本 → 保存引用该规则版本的流程新版本 → 检查两处引用/只读历史/分支含义 → 修改共享示例值并核对无重存即可看到新值 → 恢复演示值为 8000/USD。

规则名称、分支说明和拒绝文案应采用“配置阈值/标准币种”等表达，避免阈值已变化、文案仍写 8000。原 matched 分支保留，不随本次数据迁移替换成处理结果；新增处理结果另按 RCO-18 验收。

后续研发/综合验证至少覆盖：

| 验证组 | 要证明的行为 | 对应验收 |
|---|---|---|
| 库驱动能力 | 新增未在代码出现的合法字段映射、常量、集合，三个应用不重新发布/重启即可使用；空/停用目录不静态回退 | AC-01/02/11/12 |
| 条件与值类型 | 四种双侧组合、IN 集合引用、区间/一元条件、类型/作用域/值域错误、节点结果选项 | AC-03–07 |
| 历史与幂等 | 旧规则/协议 Hash、旧 matched、动作引用、旧 Candidate、原成功回放；动态值更新不改历史内容 | AC-08/09 |
| 并发与权限 | 保存引用和删除/停用竞争、集合批量失败回滚、CAS 冲突、读写权限及错误定位 | AC-06/09/10 |
| 页面与迁移 | Pencil 关键状态、变量列表/成员/引用跳转、草稿保留、只读和返回、两个示例迁移/值更新 | AC-13 / UI-01–06 |

已有 Risk 的 RuleAstValidator、RuleSetInputContract、DecisionFlowValidator/Http、Canonicalizer 测试，BFF 的 Risk 配置/Flow 浏览器边界测试，Web 的目录匹配/规则构建/流程解码测试可作为扩展入口。保留历史样本断言，为新目录增加真实库驱动用例。本阶段未运行应用测试、构建或新的 test 写入，不给出研发通过结论。

## 9. 证据入口

下列均为本次核对的工作区源码/模型，工作区已有未提交的 RCO-17 等改动；不能单凭 HEAD 认为已部署。test 证据单独引用台账。

- **E1 Risk 固定目录/装配：** [CheckoutPrepareAuthoringDescriptor](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/authoring) · [RiskRuntimeConfiguration](../../../paytech/risk-decision/paytech-risk-decision-app/src/main/java/com/paytech/risk/configuration/RiskRuntimeConfiguration.java)。
- **E2 规则操作数/校验：** [RuleAstValidatorImpl](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/rule/RuleAstValidatorImpl.java) · [Risk 规则 API 模型](../../../paytech/risk-decision/paytech-risk-decision-api/src/main/java/com/paytech/risk/decision/api/DecisionRuleSetManagementModels.java)。
- **E3 流程目录：** [DecisionFlowWireConverter](../../../paytech/risk-decision/paytech-risk-decision-app/src/main/java/com/paytech/risk/ext/rest/decision/DecisionFlowWireConverter.java) · [DecisionFlowServiceImpl](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domainservice/service/impl)。
- **E4 Web 规则目录/模型：** [riskAuthoringCatalog](../../../operations-console-web/src/risk/riskAuthoringCatalog.ts) · [ruleBuilder](../../../operations-console-web/src/risk/ruleBuilder.ts) · [ConditionBuilder](../../../operations-console-web/src/risk/ConditionBuilder.tsx) · [ReadRuleSetWorkspace](../../../operations-console-web/src/risk/ReadRuleSetWorkspace.tsx)。
- **E5 流程条件各层：** [Risk Validator](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/flow/DecisionFlowValidatorImpl.java) · [Risk JSON Codec](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/flow/DecisionFlowJsonCodec.java) · [Risk API 模型](../../../paytech/risk-decision/paytech-risk-decision-api/src/main/java/com/paytech/risk/decision/api/DecisionFlowModels.java) · [Web 编辑器](../../../operations-console-web/src/risk/FlowConditionEditor.tsx) · [Web 解码](../../../operations-console-web/src/risk/decisionFlowApi.ts) · [Web 条件模型](../../../operations-console-web/src/risk/decisionFlow.ts)。
- **E6 动作字段：** [RuleActionConfigurationCatalog](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/authoring/RuleActionConfigurationCatalog.java) · [RuleActionConfigurationConverter](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domainservice/convert/RuleActionConfigurationConverter.java)。
- **E7 BFF / 字典：** [RiskDecisionConfigurationClientImpl](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/integration/impl/RiskDecisionConfigurationClientImpl.java) · [RiskFlowResponsePolicy](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/integration/impl/RiskFlowResponsePolicy.java) · [DecisionDictionaryMapper](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/resources/mapper/decision/DecisionDictionaryMapper.xml)。
- **E8 持久化及历史：** [决策模型源](../../../scripts/pdmaner/risk/manifests/02-decision-engine.mjs) · [流程模型源](../../../scripts/pdmaner/risk/manifests/09-decision-flow.mjs) · [DecisionRuleSetServiceImpl](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domainservice/service/impl/DecisionRuleSetServiceImpl.java) · [RiskDecisionCanonicalizerImpl](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/hash/RiskDecisionCanonicalizerImpl.java) · [DecisionReleaseStaticValidatorImpl](../../../paytech/risk-decision/paytech-risk-decision-core/src/main/java/com/paytech/risk/decision/domain/release/DecisionReleaseStaticValidatorImpl.java)。
- **E9 既有决定：** [Risk 数据库权威](../../../agent-docs/database/RISK_COMPLIANCE_DATABASE.md) · [RCO-17 精简](../operations-console-决策流配置/simplification.md) · [Gateway 数据库路由](../../../agent-docs/decisions/2026-09-11-gateway-database-routing.md)。
- **E10 test 示例：** [原创建材料](../operations-console-决策流配置/assets/stage7/user-demo.json) · [前序真实接口核对](../../../agent-docs/CURRENT_IMPLEMENTATION.md#rco19-variables-20260912)。

## 10. 下一阶段交付

待确认本阶段后，阶段 3 给出明确的最小方案：每个 API 的 Method/Path/Request/Response/错误、变量及集合模型/旧表影响、注册和示例迁移 DML、历史/Hash/最新值/并发策略、Pencil 页面与研发验证计划。届时按业务操作去重统计接口数量，并区分新表/旧表结构变化、字段、索引、业务注册与权限路由 DML 行数；不以本阶段“两表建议”代替准确设计。
