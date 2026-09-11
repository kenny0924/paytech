# Risk Owner 影响评估：运营规则编辑 V2（RCO-14）

> 历史评估提示：本报告记录 2026-09-10 的组件分析。后续按[2026-09-11 V1 配置决策](../../decisions/2026-09-11-risk-rule-v1-configuration.md)收敛；独立 V2、拒绝字典及默认文案建议不再适用于本期。当前系统影响、接口/DML 和技术待办见[V1 配置草案](../2026-09-11-risk-rule-v1-configuration-contract-draft.md)。

- 评估日期：2026-09-10。
- 状态：评估结论，尚未形成已批准 Risk V2 合同。
- 范围：`CHECKOUT_PREPARE` 的 Operations 规则配置、读取、Freeze 与 TEST Candidate 静态校验。
- 不在范围：生产激活、Shadow、运行期评估、真实冷却/标签副作用、跨系统业务执行、数据库实施或部署。
- 需求来源：[运营规则编辑 V2 需求与链路评估](../2026-09-10-risk-rule-editor-v2-requirements-assessment.md)及四个画板预览：[展开](../../../operations-console-web/docs/design-previews/bi8Au.png)、[收起](../../../operations-console-web/docs/design-previews/wO9RC.png)、[REJECT 原因](../../../operations-console-web/docs/design-previews/AXojG.png)、[附加动作](../../../operations-console-web/docs/design-previews/fulZG.png)。

## 1. 结论

Risk 不能只通过放宽 DTO 来支持 V2。现行 V1 在 Wire、领域聚合、保存/回读、Canonical Hash、幂等恢复和八项静态检查中都明确采用“每条 Rule 恰好一个、零参数、终态 Decision Action”。`reasonCode` 只是可空 Code，没有可查询的原因目录、默认消息或覆盖消息。

可以复用的核心是：多 Rule、排序和启用标记、类型化条件 AST、完整不可变 DRAFT、Base ID/Hash 并发保护、Freeze、TEST Candidate、固定八项检查框架以及持久幂等。当前 71 表模型也已经提供多行 `rule_action` 和类型化 Action 参数表，但这些表目前只是结构能力；现行 Java、目录数据、Hash 与校验仍主动拒绝多动作和参数，不能据此声称 V2 已具备。

建议把需求拆成两期：

1. **配置 V2**：原因/Action 目录、安全 V2 Wire、多动作与参数保存/回读、允许零终态 Rule、V2 Hash、Freeze 和 TEST 静态校验。Candidate 全部通过后仍停留 `VALIDATING`，不调用 Handler、不产生 Action Intent/Effect。
2. **运行期与副作用**：另行批准输入合同、Handler、合并与幂等 Effect、真实冷却/标签写入、失败恢复、审计和事件。它不能由本次画板或“模型中已有表”推导为已授权范围。

Risk 配置 V2 的相对工作量为**中到大**，主要成本在不可变闭包、V1/V2 历史兼容和恢复门禁，而不是建基础 Action 表。真实运行期与副作用为**大**，且是独立需求。

## 2. 当前合同与实现证据

| 主题 | 当前事实 | 对 V2 的含义 |
|---|---|---|
| Rule Wire | [`RuleRequestBodyV1`](../../../risk-compliance-service/risk-api/src/main/java/com/paytech/risk/api/decision/DecisionRuleSetManagementModels.java) 只有单个 `action`；Action 只有 `actionCode + actionVersionNo` | 无法无损提交或回读多个动作、参数和 Reject Message |
| Authoring Context | `CHECKOUT_PREPARE_AUTHORING_V1` 固定 9 个字段、3 个终态 Action；9 个字段的 `actionParameterFlag` 全为 `false` | 当前没有可作为“冷却主体/标签主体”的已批准 Action 参数字段 |
| Decision 语义 | Point 为 `FIRST_TERMINAL`、默认 `REVIEW`；三个 Action 分别映射 `ALLOW/REVIEW/DENY` | UI 可展示 `PASS/REVIEW/REJECT`，Risk 内部 Code 不应改名 |
| Rule 聚合 | [`DecisionRuleSetAggregate`](../../../risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/rule/DecisionRuleSetAggregate.java) 强制一条 `sequenceNo=1` 的 Action | 零终态、多非终态或多 Action 都需要 V2 聚合 |
| 保存/回读 | 保存固定物化一条 `t_rule_action`，Wire Mapper 固定重建一个 Action | 需要端到端的 V2 列表和参数重建，不能只改请求模型 |
| 原因 | `t_risk_rule.reason_code` 可空，当前测试只证明 null 可接受、空白拒绝 | 没有 Reject 目录、默认消息、覆盖消息或历史文案版本语义 |
| Action 目录 | 初始化 DML 只有 `ALLOW_CHECKOUT`、`REVIEW_CHECKOUT`、`DENY_CHECKOUT`；工程 Registry 对 ID、Handler、版本、Hash 和终态属性做精确核对 | 画板中的冷却/标签只是产品示例，不是已注册 Handler |
| 物理模型 | [`t_action_version`、`t_action_parameter_definition`、`t_rule_action`、`t_rule_action_parameter`](../../../risk-compliance-service/docs/database/sql/risk-compliance-schema.sql) 已存在 | 多动作/参数可能不需新基础表，但仍需目录 DML、Java 写读、Hash、校验和测试；原因目录与 Hash Schema 识别仍可能需要 Schema 变化 |
| Hash | [`RiskDecisionCanonicalizerImpl`](../../../risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/hash/RiskDecisionCanonicalizerImpl.java) 的 V1 Rule Hash 是单个 `action`；Release V1 要求参数等集合为空 | 不能把新字段塞入 `*_V1` 后重算历史值，必须显式演进 Schema |
| 静态检查 | [`DecisionReleaseStaticValidatorImpl`](../../../risk-compliance-service/risk-decision/src/main/java/com/paytech/risk/decision/domain/release/DecisionReleaseStaticValidatorImpl.java) 对参数“出现即失败”，并要求每 Rule 恰好一个终态 DECISION Action | 保留八项检查框架与顺序，但 V2 必须重写其中的参数和终态语义 |
| 当前状态 | [CURRENT_IMPLEMENTATION](../../CURRENT_IMPLEMENTATION.md) 只声明配置、Freeze、TEST Candidate 与八项静态检查；Shadow、审批、激活和运行期未完成 | 本次配置扩展不得被描述为真实决策或副作用已经可用 |

逻辑数据库权威长期允许“一条 Rule 有多个非终态 Action、最多一个终态 Action；没有终态时使用 Point 默认决定”，见 [Risk & Compliance 数据库设计](../../database/RISK_COMPLIANCE_DATABASE.md)。这可以作为设计方向，但不改变上述当前实现事实。

## 3. V2-01 至 V2-08 支持判断

| 需求 | Risk 判断 | Risk 变化 |
|---|---|---|
| V2-01 展开/收起、选中编辑、未保存提示 | 页面态，不需要 Risk 生产代码变化 | 历史只读与当前 DRAFT 仍由状态字段表达；Risk 回归读取稳定性即可 |
| V2-02 增删、排序、重命名、启用 | 基础能力可复用 | 继续整份保存新 DRAFT；删除是新快照中省略，排序/启用不是即时生效命令；保留 Rule Set Code、Base ID/Hash 和 `changeReason` |
| V2-03 ALL/ANY 与嵌套条件组 | 现有 typed AST 基本可复用 | 必须验证 V1/V2 对 AND/OR/NOT、比较方向、Decimal/Datetime/Duration 和节点顺序无损往返；UI 单条件折叠不能改变 AST |
| V2-04 PASS/REVIEW/REJECT | 仅展示映射 | `PASS → ALLOW`、`REJECT → DENY`，Risk 继续保存 `ALLOW/REVIEW/DENY`；未出现终态时才在完整评估结束后使用默认 `REVIEW` |
| V2-05 Reject Code/Message | 必须扩展 | 增加 Risk-owned 受控目录、版本/冻结语义、默认消息、可空覆盖和历史回显；当前 `reasonCode` 单字段不足 |
| V2-06 多个附加动作 | 必须扩展 | 增加工程白名单 Action、参数目录、安全 Wire、领域模型、持久化、读回、Hash 与静态检查；示例不自动成为已批准 Action |
| V2-07 不产生终态决定 | 必须扩展 | Rule 改为 0..1 个终态 Action；零终态命中只收集附加动作并继续；多终态继续 Fail-Closed |
| V2-08 规则描述 | 只读生成可不改 Risk | 描述应由条件/决定/动作投影生成；若允许人工编辑，则是新的持久化字段，必须进入保存、回读、Hash 与历史闭包，需另行批准 |

## 4. 建议的最小 V2 合同

### 4.1 版本隔离

建议保留全部 V1 DTO、严格 Decoder、Canonical Schema、Golden Vector 和历史读取/重放行为，新增显式 V2 Wire 与 Descriptor。不要在名称仍为 `V1` 的对象上悄悄增加可选字段，因为现行合同要求未知属性失败，且同一结构无法可靠判断应使用哪套 Hash。

推荐的 V2 Rule 形态是：

- Rule 的 Code、Name、Priority、Enabled 和条件树继续保留；
- 用有序 `actions[]` 表达 0..N 个工程注册 Action；
- 每个 Action 固定 `actionCode + actionVersionNo`，参数使用有序、类型化绑定；
- 每条 Rule 最多一个 `terminalFlag=true` 的 Decision Action，其余必须是允许在该 Point 使用的非终态 Action；
- Reject 原因使用独立的受控对象，而不是把自由文本塞回现有 `reasonCode`；其精确字段取决于第 11 节待决事项。

上述是最小语义形态，不批准具体字段名。若采用单独 `terminalDecision` 加 `adjunctActions[]` 也可以，但持久化和 Hash 最终仍必须形成唯一确定的 Action 顺序，且不能让 BFF 自行推断终态属性。

Authoring Context V2 至少需要安全投影：

- Point 默认决定与评估策略；
- Reason Code、不可变版本、默认消息和状态；
- Action Code/Version、显示名、类别、是否终态、Decision Code；
- 参数 Code、值类型、是否必填、允许来源、是否目标参数、值域/选项/单位；
- 可用于 Action 参数的 Context 字段及其类型。

Handler Code、Implementation Version/Hash 仍只在 Risk/BFF 严格上游闭包中使用，禁止返回浏览器。Operations 仍不能提交 SQL、脚本、类名、URL、表达式或自定义 Handler。

### 4.2 Reject Reason 目录和消息

当前 Schema 注释提到“Risk 决策原因目录”，但 71 表及唯一初始化 DML 中没有可供本切片查询的版本化目录。要同时满足“下拉选择、带出默认文案、允许恢复默认、历史精确回显”，推荐由 Risk 保存不可变的 Reason Version，并让 Rule 冻结引用该版本；覆盖消息为该 Rule Version 的可空值。这样旧规则读取时不依赖当前目录文案，也不需要重算旧 Hash。

最低校验应包括：

- 仅 DENY/REJECT 终态允许或要求 Reject Reason；ALLOW/REVIEW 以及无终态 Rule 如何处理 Reason 必须明确，不能静默保留脏值；
- 新 DRAFT 只能引用当前允许创作的 Reason Version；历史读取/重放允许引用已 RETIRED 但内容未漂移的版本；
- 默认消息和覆盖消息有明确长度、Unicode 规范化、非空白规则和展示用途；
- 对外错误/拒绝提示继续由消费接口独立映射，内部 Reject Message 不自动向 Client/Consumer 暴露。

若选择“不建目录，只把有效文案保存为 Rule 内 literal”，DDL 更小，但无法自然表达目录维护、默认文案版本和恢复默认来源。该简化只有在产品明确放弃这些治理能力后才成立。

### 4.3 Action 白名单和类型化参数

已有参数表可承载 `CONTEXT/LITERAL/FEATURE` 与 `STRING/NUMBER/BOOLEAN/DATETIME/DURATION`，但配置 V2 首期建议继续禁用 FEATURE 参数，避免把 Feature 执行一并带入。画板示例可按以下方向评估，不能直接当成合同：

- “冷却主体”的目标必须来自明确标为 `actionParameterFlag=true` 的 typed Context 字段；当前九字段全部不具备该资格，不能用 Country/Currency 等字段猜出主体；
- 冷却时长可规范化为 `duration_value`。若历史回显必须保留运营选择的“分钟/小时”单位，单位还需要独立受控参数；只保存毫秒无法还原原选择；
- 风险标签可以是受控 STRING literal，但当前参数定义表只表达类型/来源，不表达允许值目录。标签若可维护，需版本化目录或与 Action Version 绑定的不可变工程目录；
- 参数的 required、cardinality、source、type、range、target 资格和 XOR 字段矩阵均由 Risk 验证，不能只依赖前端控件。

Action Version 仍由工程注册并冻结 Handler、Implementation Version/Hash、终态属性和 Merge Policy。新增 Action 必须经过实现、自动测试、部署注册和版本发布；配置页面不能创建 Action。

### 4.4 多动作与零终态语义

建议沿用逻辑模型的上限：每条 Rule 允许 0..N 个非终态 Action，并最多一个终态 Decision Action。无终态 Rule 命中后只形成待收集动作并继续后续 Rule；整个 Point 没有命中终态时才使用 Point Version 的默认 `REVIEW`。技术失败或超时不能回退到默认业务决定。

配置合同还必须固定：

- 同一 Rule 内动作顺序是否影响语义，以及终态 Action 在列表中的合法位置；
- 同一 Action/目标在同一 Rule 和跨 Rule 重复时的去重与冲突规则；
- 合并只使用 Action Version 固定的闭集 Merge Policy，运营不能改；
- 到达第一个终态后，已收集的附加动作是保留、丢弃还是仅在某些决定下保留；
- Candidate 静态通过只证明配置闭包有效，不代表上述动作已可运行。

这些问题未批准前，可以完成 Schema/Wire 评估，但不能安全实现真实执行。

## 5. 保存、读回和并发

V2 继续遵守现行完整快照语义：

1. 每次保存提交完整 Rule Set，并创建新的不可变 DRAFT；不对某个 Rule 做原地 PATCH。
2. 新增/删除/排序/开关/重命名都只是新快照内容；旧 FROZEN/RETIRED 版本不改写。
3. `ruleSetCode` 保持版本流身份；读取所得 Base Version ID/Content Hash 原样回传。基准变化返回 409，不自动覆盖、换 Base 或合并。
4. `changeReason` 仍是独立、必填的非敏感变更原因，不能用 Reject Message 替代。
5. V2 Detail 必须精确读回 Action 顺序、每个参数、Reason Version、默认/覆盖关系和零终态状态；不能用当前目录补齐历史字段。
6. 保存上限继续以 65,536 UTF-8 Bytes 为基线，Freeze/Create Candidate/Validate 继续 16,384 Bytes，五个 GET 继续零 Body。只有真实请求样本证明不足时才另行评估提高上限。

## 6. PDManer、DDL 和目录 DML

多 Action 和基础类型化参数可以复用现有四表，因此不应为了画板再建一套 JSON/EAV 表。不过至少还有两类可能的物理变化：

1. **Reject Reason 历史闭包**：若采用推荐的版本化目录，需要新增目录版本及 Rule 冻结引用/覆盖消息的准确结构。
2. **Hash Schema 识别**：当前 `t_rule_set_version`、`t_decision_point_version` 和 `t_decision_release` 只保存 Hash，没有保存 Canonical Schema。V2 与 V1 可能在内容上看起来相同，例如只含一个零参数终态 Action，因此不能靠行形态猜版本。凡 Canonical Object 发生变化的不可变根都需要可靠的 Schema/Contract 判别；推荐显式保存，并把存量行回填为 V1 常量而不重算 Hash。具体落在哪些根表需在 Hash 设计中批准。

如发生物理变化，必须同步 Risk Manifest/Policy/生成器、PDManer 模型、完整 Schema SQL、已有数据库的人工增量 SQL、唯一初始化 DML、模型/Schema 契约测试和 DataObject/Mapper。继续保持零数据库 `CHECK`、零物理 `FOREIGN KEY`，跨行不变量由事务锁/CAS、唯一索引、Owner 校验和真实 MySQL 反例测试证明。PDManer 4.9.3 人工 Review 和人工 SQL 应用仍是独立门禁；应用启动不得建表、迁移或 Seed。

如果最终 Reason 只采用 Rule 内 literal，或 Action 参数限制完全由已版本化的工程 Descriptor 表达，所需 DDL 可以减少；但这不能省略 V2 Hash Schema 判别和历史回放设计。

## 7. Canonical Hash、幂等和历史重放

### 7.1 Hash

以下 V1 Schema 和 Golden Vector 必须保持逐字节不变：Rule Set、Point Version、Release、Create Rule Set、Freeze、Create Candidate、Validate。不得给 `RISK_RULE_SET_VERSION_V1` 增字段，也不得用 V2 投影重算旧 `content_hash`。

至少需要新的 Rule Set V2 与 Release V2 Canonical Object；若 Point Descriptor/Input Field 投影变化，Point Version 也需要 V2。V2 Hash 应覆盖：

- Rule 元数据和完整 AST；
- 按确定规则排序或保序的全部 Rule Actions；
- 每个精确 Action Version 的目录身份、终态/决定、Merge Policy、Handler Version/Hash；
- Action Parameter Definition 与每个类型化绑定；
- Reject Reason Version、默认消息内容以及 Rule 覆盖值或其明确 Null；
- Feature/Obligation/Limit 等仍为空的闭包证明。

集合的“排序不影响 Hash”与“顺序属于业务语义”必须逐项写入规范：Rule Priority、同级 AST Sequence 和 Action Sequence 应保持顺序敏感；目录投影可按稳定业务键排序。

### 7.2 命令幂等

现有 `t_command_idempotency` 可以复用，但 V2 Create 请求形态变化，必须使用 V2 Request Hash。实现需要在以下方案中明确选择，不能让现有 `CommandOperationType → *_V1 Schema` 映射暗中处理 V2：

- 新增 V2 operation type，使持久幂等记录天然区分 V1/V2；或
- 增加显式 request-hash schema/contract discriminator，并规定跨版本复用同一 Idempotency-Key 的冲突行为。

Freeze/Create Candidate/Validate 的请求如果仍只含 ID、Expected Hash/Version，可以在批准为版本无关命令后继续使用原投影；否则也应显式 V2 化。无论采用哪种方案，同键同请求必须返回原稳定响应，同键不同请求必须 Fail-Closed，Validate 的两阶段 A/B 恢复规则不能退化。

### 7.3 历史读取与回放

- V1 Rule Set、Candidate、检查结果、命令记录与 Golden Vector 原样保留；不迁移为 V2，也不重算。
- V2 新建 DRAFT/Companion Point Version/Candidate，历史版本不原地补动作或消息。
- 读取和重放按持久化的合同/Hash Schema 选择投影；不得按“当前 Authoring Context”解释历史行。
- 被后来 RETIRED 的已引用 Action/Reason Version 仍可历史读取和重放，但其冻结内容若漂移或缺失必须 Fail-Closed。

## 8. Freeze、Candidate 与八项检查

八项检查名称、执行顺序、“始终执行完并返回全部八项”和 HTTP 200 + `REJECTED` 的失败形态可继续复用。V2 的检查语义建议如下：

| 检查 | V2 需要覆盖 |
|---|---|
| `REFERENCE_INTEGRITY` | Rule/AST/Action/Parameter/Context Field/Reason Version 的精确归属、无孤儿、无重复、闭包完整；Release 引用准确 V2 根 |
| `AST_SHAPE` | 继续验证唯一 Root、父子、Sequence、AND/OR/NOT 基数和节点上限 |
| `AST_TYPES` | 继续验证 Operator、Operand Source、Value Type、Literal 规范化和值域 |
| `FEATURE_DEPENDENCY` | 配置 V2 首期仍要求 Feature 及其依赖为空；若以后允许 FEATURE 参数，需单独扩展而非绕过此项 |
| `HANDLER_HASH` | 对所有终态及附加 Action 验证工程 Registry、精确 Version/Hash、状态和历史 RETIRED 规则 |
| `ACTION_PARAMETERS` | 从“出现即失败”改为校验定义/绑定的 required、cardinality、source、type、XOR、范围、选项、单位、target 资格和 Context `actionParameterFlag` |
| `TERMINAL_ACTION` | 每 Rule 允许 0..1 个终态 Decision；多个终态、未知 Decision、非法顺序或不允许的类别失败；零终态表示继续而非配置错误 |
| `SYSTEM_OBLIGATION` | 本切片仍要求 Obligation/Limit Binding 为空，不借 V2 引入系统义务 |

Reject Reason 的引用完整性放入 `REFERENCE_INTEGRITY`，其“仅 DENY 必填/其他状态禁止”的条件规则可由 `TERMINAL_ACTION` 校验；也可以在不改变八项名称的前提下由 Owner 选择更清晰的归属。错误摘要只能包含稳定 Code、路径、Rule/Action 标识和计数，不回显消息、Literal、目标值或完整参数。

## 9. 配置与运行期边界

配置 V2 完成后，可以声明的只有：运营可保存和读取新闭包、Freeze、创建 TEST Candidate，并由八项静态检查验证其结构和目录引用。不能声明：

- Rule 已参与真实 `CHECKOUT_PREPARE` 评估；
- 默认 REVIEW 已在运行期执行；
- 冷却或标签 Handler 已部署；
- Action Intent/Effect、Merge、补偿、MQ、Shadow、审批、激活或生产 Release 已完成；
- Commerce 或其他业务系统已经提供“主体”字段。

真实执行至少还需要：准确运行输入和最小消费者合同、所有 Action Handler 及旧版本保留策略、目标解析、跨 Rule Merge、Effect Hash/幂等、失败与恢复、审计、SIMULATED/REAL 隔离、外部系统写入和端到端回归。若“主体”需要当前 RCO-I03 没有的稳定 ID，必须由 Commerce/Risk 输入合同另行评估，配置 V2 不得先造假字段。

## 10. 权限、安全与跨系统依赖

### 10.1 权限与 Route

若 V2 仍只是读取、保存、Freeze、创建/验证 TEST Candidate，现有五类最小权限的业务动作可以复用，不需要 Risk 自行扩大权限。若采用独立 `/v2` 路径，Gateway 与 Platform 必须新增精确 Route/Snapshot 配置并绑定同等最小权限；Operations BFF 仍须逐 Route 先执行 PLT-I02 再单次调用 Risk。

Reason/Action 目录若只作为 Authoring Context 的只读投影，可复用 READ。若未来允许运营维护目录或注册 Action，则必须新增独立高风险权限、审核和生命周期，这不在 RCO-14 范围，不能借用 `RISK_RULE_SET_SAVE`。

### 10.2 安全

- AST、Literal、Reject Message、Action 参数和目标均属于受控配置内容，不进入 URL、普通日志、浏览器持久存储或遥测。
- Risk/BFF 只记录 Request ID、Actor、命令类型、稳定资源 ID、状态、耗时和脱敏错误 Code；不记录完整 Body。
- Reject Message 只能按批准用途展示，默认不得外发；外部消费者继续使用独立、安全的业务映射。
- Risk 必须独立验证目录、版本、参数和值域，不能信任 Web/BFF 已校验。
- BFF 给浏览器的目录继续移除 Handler Code、Implementation Version/Hash。
- Body 超限、未知字段、未知/已退役目录版本、Base 冲突和目录在加载后漂移均应 Fail-Closed，不做自动降级或当前目录替换。

### 10.3 依赖

推荐顺序：

1. Risk Owner 先批准第 11 节的业务语义、V2 Wire/Descriptor、Reason/Action 目录、Hash 和历史兼容设计。
2. Risk 同步演进 PDManer/SQL/DML、API/领域/Repository/Mapper、Canonicalizer/Golden Vector、Freeze/Candidate 闭包与测试。
3. Operations BFF 基于已冻结 V2 Risk API 做严格 Decoder、安全目录投影、错误路径映射和 PLT-I02。
4. Operations Web 再实现 V2 编辑模型和完整往返；Gateway/Platform 仅在路径、Body 上限或权限确有变化时更新配置。
5. 本地全链路通过后仍需 PDManer 人工 Review、人工 SQL 与部署授权；配置验收不关闭运行期 Gate。
6. 真实动作执行作为后续独立设计，届时再评估 Commerce 输入、Risk Handler/Effect、事件及副作用 Owner。

## 11. 验证门禁

Risk V2 实施时至少需要以下证据：

1. **V1 不变**：V1 API 反射合同、严格 JSON、全部 Canonical Golden Vector、历史 Rule Set/Candidate/RETIRED Action 重放逐字节回归。
2. **V2 Wire/领域**：多非终态、零/一终态、多个终态拒绝、动作顺序、Reason 条件矩阵、参数必填/多余/重复/类型/范围/来源/目标反例。
3. **Hash**：V2 黄金向量、Unicode/Decimal/Datetime/Duration 边界、语义顺序敏感、非语义目录顺序稳定、旧 Hash 不重算。
4. **持久化**：真实本地 MySQL 保存并完整读回 Action/参数/Reason；事务回滚无孤儿；新 DRAFT 退役规则、Base 409 和并发唯一性。
5. **幂等/恢复**：同键同 V2 请求稳定重放、同键不同请求冲突、V1/V2 跨版本 Key 行为、Freeze/Create/Validate A/B 恢复和非原 Actor Fail-Closed。
6. **八项检查**：每项至少一条正反例；缺目录行、Handler Hash 漂移、参数闭包破损、零终态通过、多个终态失败、全部八项始终返回且摘要脱敏。
7. **HTTP/安全**：五个 GET 无 Body、Body 限额、未知字段、RFC 9457 Problem、无完整配置日志、浏览器投影无实现指纹。
8. **数据库资产**：生成器 `--check`、模型/Schema 契约、完整 SQL 与增量 SQL 对齐，PDManer 4.9.3 人工 Review；禁止 Flyway、Testcontainers 和启动迁移。

## 12. 待决问题

以下仍是未批准问题，不能在实现中自行选择：

1. Reject Reason 目录由谁维护；是否采用不可变 Version；旧规则展示当时默认文案还是当前文案。
2. Reject Message 是否允许自由覆盖、最大长度和内容规则；它是否严格只用于内部决策记录；外部接口是否永远只使用独立映射。
3. 第一批附加 Action 白名单及精确 Handler 是什么。画板中的“冷却主体”“添加风险标签”目前只是示例。
4. “主体”是哪个稳定 typed 字段；当前九个 Authoring Field 均不可作 Action Parameter，运行输入是否已有该字段。
5. 冷却范围、单位保存方式、标签目录和值域如何版本化；是否允许同一参数多值。
6. 同 Rule/跨 Rule 的动作顺序、重复、冲突、Merge 和“遇到终态后保留哪些已收集动作”的精确语义。
7. V2 采用独立路径，还是同路径显式 Contract Discriminator；Hash Schema 保存在哪些不可变根；Create 命令幂等采用新 Operation Type 还是显式 Schema 字段。
8. 本期是否严格止于配置/Freeze/TEST 静态校验。若加入 Shadow 或真实执行，应重新评估范围和依赖。
9. 手工规则描述是否需要保存；若仅从结构生成，只读描述不进入 Risk 数据。

在这些问题批准前，Risk 可以把本报告作为后续合同设计输入，但不能据此开始真实 Handler、副作用或生产发布实现。
