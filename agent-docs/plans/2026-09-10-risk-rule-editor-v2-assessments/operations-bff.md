# RCO-14 Operations BFF 影响评估

> 历史评估提示：本报告记录 2026-09-10 的组件分析。后续按[2026-09-11 V1 配置决策](../../decisions/2026-09-11-risk-rule-v1-configuration.md)收敛；独立 V2、拒绝字典及默认文案建议不再适用于本期。当前系统影响、接口/DML 和技术待办见[V1 配置草案](../2026-09-11-risk-rule-v1-configuration-contract-draft.md)。

- 日期：2026-09-10。
- 状态：Assessment / 合同待决；本文不是实现、验证通过或部署声明。
- 范围：四个规则编辑 V2 画板、`CHECKOUT_PREPARE` 配置/冻结/TEST 静态校验；不包含决策流 V2/V3、运行时冷却/标签执行、新决策点或生产发布。
- 输入：[需求与交付约束](../2026-09-10-risk-rule-editor-v2-requirements-assessment.md)。源码证据来自当前共享工作区，其中包含已有未提交 Risk 集成代码，不等于某个已发布制品。
- 本次只新增本报告；未修改业务代码、数据库、公共规范或状态台账，未提交、推送、部署。

## 1. 结论

V2-01/02/03/04/08 的纯交互部分可以复用现有 BFF 合同，主要需要回归验证。V2-05/06/07 必须先由 Risk 扩展并冻结合同，随后 BFF 同步扩展本地 Models、双向 Adapter、成功响应严格 Reader 和目录投影。不是简单给 DTO 加可选字段：当前请求和成功响应均要求精确属性集合，且 Authoring Descriptor 只接受 V1、单终态动作和空参数目录。

建议将交互调整与业务合同扩展拆开交付。后者建议采用显式版本化合同与配套 Descriptor，保留 V1 Reader/Writer；这是待 Owner 确认的建议，不预先指定新路径、版本号或权限代码。完整 RCO-14 的 BFF 配置适配工作量为中；若同时支持旧新 Wire/目录/错误路径共存，工作量为中偏大。真实副作用执行不属于 BFF，应另由 Risk 评估，不能计为 BFF 增加一个调用即可完成。

## 2. 可核对的证据

以下路径均相对于本报告所在目录，可直接打开；方法名用于定位，不复制源码作为第二套权威。

| 证据 | 当前约束及影响 |
|---|---|
| [Console 集成设计 §5.3、§8—9](../../../docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md) | 九条接口为单 Owner 调用；BFF 本地 Models 显式映射 risk-api；先 PLT-I02；不复制 AST/Hash/并发/状态机。保存 65,536 Bytes，其他命令 16,384 Bytes，GET 零 Body，由 Gateway 执行精确限额。 |
| [RiskRuleSetModels](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/service/model/RiskRuleSetModels.java) | `RuleRequestBody` 只有非空单个 `action`，`reasonCode` 可空且最长 64，无 Reject Message。动作只有 Code/Version；目录的 actionParameters/features/obligations/limitBindings 为 `List<Void>`。条件已有 GROUP/PREDICATE、AND/OR/NOT、CONTEXT/LITERAL；Literal 只接受 String/Boolean/null。 |
| [RiskRequestShapeValidator](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskRequestShapeValidator.java) | `RULE`、`ACTION`、`ROOTS` 定义精确属性集合；未知/遗漏成员不能静默吞掉。新增 message/actions/parameters 必须同步改变已批准的版本化 Shape。 |
| [RiskDecisionConfigurationClientImpl](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/integration/impl/RiskDecisionConfigurationClientImpl.java) | `ownerRule/rule` 双向映射单动作；`Validator.authoring` 固定 `CHECKOUT_PREPARE_AUTHORING_V1`、三种 Checkout 终态动作，要求字段不能作动作参数、四类扩展目录为空。`action` 验证 Handler 指纹后删除，不输出给浏览器。 |
| [RiskOwnerResponseShapeValidator](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskOwnerResponseShapeValidator.java) | 九种成功根精确读取；Rule Detail、Authoring Action、空目录等均为封闭 Shape。旧 Reader 会拒绝上游直接新增字段或非终态空 decisionCode，不能以默认 Jackson 忽略未知字段绕过。 |
| [RiskOwnerResponseHttpMessageConverter](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskOwnerResponseHttpMessageConverter.java) | 拒绝重复键/多根；关闭源内容诊断；解析错误安全化，I/O 原因保留给传输分类。新的成功类型必须显式注册。 |
| [Controller](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/ext/rest/RiskDecisionConfigurationController.java) | 当前 `/decision-rule-sets/v1`、`/decision-releases/v1`，拒绝未知 Query；完整新版本保存、Base ID/Hash、Idempotency-Key 原样提交。Validate 校验 `risk-validate-v1-{releaseId}-{expectedVersion}`。 |
| [OperationsRequestInterceptor](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/ext/rest/OperationsRequestInterceptor.java)、[OperatorContextArgumentResolver](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/ext/rest/OperatorContextArgumentResolver.java) | 可信 Gateway Header 形成 Actor/Session/Route Context；先 `client.authorize` 再进入命令绑定；统一 no-store、Origin 和媒体类型校验。不能从 Body 接收操作人或权限。 |
| [RiskClientConfiguration](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/RiskClientConfiguration.java)、[OperationsErrorStatusResolver](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/java/com/paytech/operations/console/bff/configuration/OperationsErrorStatusResolver.java) | HttpServiceProxyFactory Bean、Risk 专用严格成功 Reader、Foundation strict Problem Decoder；注册 Risk 错误枚举，超时/不可用安全分类，不跟随重定向。没有新增参数字段路径的显式映射配置。 |
| [application.yml](../../../operations-console-bff/paytech-operations-console-bff-web/src/main/resources/application.yml) | `/decision-rule-sets`、`/decision-releases` 已在 metadata-only 日志前缀内；新增不同 Root 需同步评估。 |

## 3. 八项需求的 BFF 支持边界

| 需求 | 支持与改动 | 相对工作量 |
|---|---|---|
| V2-01 列表收起/选中规则/未保存状态 | 属于 Web 内存与布局；不增加会话、缓存或草稿存储。BFF 无需生产代码变更，回归目录/详情查询与权限即可。 | 小 |
| V2-02 增删/排序/启停/名称 | 现有 rules、priorityNo、enabledFlag、名称字段足够；仍一次完整保存为新 DRAFT。不得新增即时启停/删除 Rule 命令或把 Base 更新为最新值。 | 小 |
| V2-03 条件编辑 | 保留现有 AST，不翻译为 BFF DSL。NOT、左右 Operand、Decimal 字符串和嵌套结构必须无损往返。若新增 FEATURE 或字段来源则是另一个明确合同扩展。 | 小；超出来源闭集另评 |
| V2-04 三态名称/默认 REVIEW | Web 标签映射 ALLOW→PASS、DENY→REJECT；当前 Wire Action Code 和 Decision Enum 不改。默认值来自获批 Descriptor，不由 BFF 推断或覆盖。 | 小 |
| V2-05 Reject Code/Message | 当前只有可空 reasonCode，无目录与 message。需要 Risk 定义拒绝配置字段及版本化目录；BFF 增加 Request/Detail/Authoring 投影、Shape 和安全校验。不得把业务拒绝文案写入 HTTP Problem detail，也不能用其替代 changeReason。 | 中 |
| V2-06 多附加动作/参数 | 单 action 要扩展成 Owner 批准的有序结构；参数目录不再是空集合。需要类型化 Request/Response Models、来源与值的传输结构、精确 Reader/Adapter 和参数错误路径。BFF 不实现动作合并、去重、时长归一化或主体资格判断。 | 中偏大 |
| V2-07 不产生决策 | 当前 action 非空且 Authoring 只接受三种 terminal。必须扩展零终态表达、允许的非终态 action/nullable decisionCode；不能用 PASS、空 actionCode 或虚构 CONTINUE Enum 冒充。零动作是否合法、至多一个终态等由 Risk 定义和验证。 | 中，与 V2-06 联动 |
| V2-08 描述/保存 | 自动生成只读规则描述由 Web 负责，无需新持久化字段。若要求手写描述，必须另确认 Risk DTO/Hash/历史回显；保存入口继续要求非敏感 changeReason 与 Base。 | 小；手写描述另评 |

## 4. 合同闭环与 BFF 分层

1. Risk 先给出原因目录、默认/覆盖消息、动作和参数的精确合同，以及新旧 Descriptor/Schema 的识别方式。目录不能由 BFF 自行注册冷却或标签 Handler。
2. BFF 继续使用 `*Models` 容器和 `*RequestBody/*ResponseBody`，与 risk-api DTO 分离。Actor、Session、Idempotency-Key、Base/Expected Hash 原样传递；BFF 不计算内容 Hash。
3. 新字段必须同时覆盖创建请求、Detail 回读、Authoring 目录与成功严格 Shape；新建回执/Freeze/Candidate 如果仍仅返回坐标与 Hash，不能为了“显示新字段”无故膨胀响应。但必须验证这些坐标指向包含新字段的冻结闭包。
4. Risk 冻结规则/动作/原因消息的具体版本、维护 Hash 与幂等。BFF 不按最新原因目录重写历史消息，也不把不支持的新数据裁剪成旧单 action 后允许保存。
5. 保持“验证上游合同并做安全投影”的边界；对未知类型、缺字段、不合法响应失败关闭。AST 解释、参数业务取值范围、合并结果、FIRST_TERMINAL 与系统义务仍归 Risk，不能借扩展 Reader 复制引擎。
6. 如 Risk 的 Authoring 查询已能返回全部获批目录，维持当前单调用形态；如果页面确需组合规则详情与独立目录查询，页面组合应放 BFF application/Processor 层，分别遵守权限和版本关联。不要把页面便利聚合推到核心服务，也不要在 BFF 编排多个业务写事务。

## 5. 路由、授权与兼容的两种条件

### A. 复用现有 Route

前提：Owner 明确批准版本协商/兼容方式，外部路径及方法未改变。

- 当前 READ/SAVE/FREEZE/CANDIDATE_CREATE/VALIDATE 权限可作为候选复用方案；仅当 Platform 确认新增动作配置仍属于同一能力授权时才能复用。不能把“已有保存权限”直接等同于“允许配置任意新动作”。
- 九条路由的 PLT-I02、当前 Actor/Session、no-store、日志前缀及 Body Limit 可复用；参数/代码不能变成未经注册的 Query/Header。增加协商字段同样需要 Gateway 审查。
- 同路径直接增加可选字段仍会破坏当前精确 Reader；仅换 Descriptor 也不能修复尚未能解析的新 Shape。需同路径显式版本分派或严格协调切换，并证明旧 Web/BFF/Risk 共存与回滚路径。不要删除 V1 校验来解决不兼容。

### B. 新增 Route / 显式新版 Wire

前提：Risk/Platform/Gateway 批准具体根路径、方法、版本、Permission/Route Code。

- 增加或版本化 Controller、本地 Models、risk-api Proxy 类型、Request Shape 与成功根注册；保留 V1 原行为。
- Platform 注册精确 Route→Permission→Console Resource，Gateway 注册 Approved Root/方法/Query/Header/限额。BFF 继续使用可信 Route Context 调 PLT-I02；未经注册不得上线入口。
- 新 Root 若不在现有 metadata-only 前缀内必须补充；受保护接口不得新增授权豁免。
- 独立原因/动作目录 GET 是否复用 READ 权限由 Platform 审定；敏感主体目录若要更细权限不能在 BFF 中凭按钮显隐替代授权。
- 新旧版本流/幂等空间的关联由 Risk 定义；不能把原 V1 的 Validate key 模板机械改成新版，也不能同一未知结果命令重试时换路径或换幂等键。

**建议：** 涉及单动作→多动作以及非终态表达时，优先明确新版 Wire + Descriptor，降低同路径滚动升级歧义。最终路径与权限未冻结，本报告不将建议写成批准决定。

### 历史数据与部署兼容

- BFF 无数据库迁移；Risk 如需 DDL/DML、历史默认值或新的 Hash Schema，由 Risk 按人工 SQL 规则评估，BFF 不回填或重算。
- 老 Rule Set/Candidate/冻结结果保留原 Hash、版本及同键重放语义。V1 请求仍按 V1 完整 Shape 验证。
- 新 BFF 应按批准版本读取旧事实；旧 BFF 对新结构失败关闭，不能承诺“天然兼容”。新版页面遇到未知 Descriptor 应只读/阻止编辑，不将缺失能力当空参数保存。
- 需覆盖旧详情+新目录、目录版本失效、动作停用、旧 Base 保存冲突、超时后同键重试等组合。快照读取与编辑能力是否分离，需要 Owner 给明确策略。

## 6. 错误、安全与容量

- 公共错误继续遵守 [Problem Profile §3.3、§6.4](../../../docs/superpowers/specs/2026-09-06-system-wide-rfc-9457-problem-details-design.md)。普通业务检查不为每项参数创建新错误码；只有 Web 有明确分支才注册 Owner Code。
- Body 字段错误应使用外部 JSON 路径，例如新版合同批准后的 `rules[0].actions[1].parameters[0].value`；这是示意路径，不是已冻结字段名。BFF 只映射可证明的本地属性路径，不能外泄 DB ID 路径、Handler、参数原值或上游任意 message。若新旧 DTO 不同构，需要明确白名单路径转换；无法安全转换则返回通用安全错误。
- 当前 Risk Client strict 配置没有新增字段路径映射；不能声称新增参数 errors 会自动原样到达 Web。要核对 Foundation 的安全字段投影接口和允许消息目录，复用公共出口，不新增一套 BFF 全局异常处理。
- 区分 JSON Shape/类型失败与成功绑定后的 Bean Validation；回归 400/422。404/409/413/502/503/504 使用既有契约分类。Risk 静态校验不通过可能是正常 200 业务结果，不转成 transport 500。
- 新的拒绝文案/动作参数按策略机密处理：仅授权配置响应可展示，禁止普通请求/响应日志、异常原文、遥测和 URL；继续验证并去除 Handler 实现指纹。新文案不能成为 Client/Consumer 的外部拒绝提示，外部映射另由消费合同决定。
- 参数增加会增大 UTF-8 请求体。保持 65,536/16,384/GET 0 基线，测量真实复杂样本后再提扩容；BFF 不增加第二套 Byte Limit，也不声称直连 BFF 测试证明 Gateway 413。
- 超时、连接断开、部分响应、重定向均不自动重试写命令；浏览器未知结果恢复必须保留原 Key/Base 和原协议版本。

## 7. 验证范围与建议门禁

本轮只做源码/文档检查，未运行 Java 测试，因此下表是后续验证计划，不是通过记录。复用测试目录：[BFF auto 测试](../../../operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto)。

| 入口 | 必须覆盖的行为 |
|---|---|
| RiskDecisionConfigurationModelAutoTest | 新旧请求序列化、显式 null/遗漏/未知键、数组顺序、数字字符串精度、长度边界和字段路径。 |
| RiskDecisionConfigurationClientAutoTest | 原 V1 无损往返；新版多动作/参数/原因回读；未知 Descriptor/版本/类型、不匹配的坐标和畸形目录失败关闭；指纹仍移除。 |
| RiskClientConfigurationAutoTest | 所有成功根的精确 Shape/媒体类型/重复键/多根、Problem 白名单、安全字段投影、超时/中断/重定向不重试。 |
| RiskDecisionConfigurationBrowserBoundaryAutoTest | 每条新旧 Route PLT-I02 拒绝后 Risk 零调用，授权先于命令绑定；防 Actor/Route 注入；Body 字段路径；安全 Request ID；策略 canary 不进日志。 |
| RiskDecisionConfigurationSimulator | 增加独立新版 fixtures，保留 V1；正常与畸形响应、目录变更、保存已成功但响应丢失，不能让模拟器帮 BFF 修复错误数据。 |
| 跨组件契约/端到端 | 新字段保存→详情→Freeze→Candidate→八项静态校验；参数/消息参与 Owner 冻结与幂等；409 不自动覆盖；UTF-8 上限和 413 经 Nginx/Gateway 验证；新旧客户端共存/回滚。 |

单动作合法、多个非终态动作、零终态但有动作、多个终态拒绝、重复动作/不合法主体、未知原因代码、消息默认/覆盖/恢复、旧规则无消息，是必需的业务样本。预期业务结果由 Risk 提供，BFF 测试验证投影和协议边界，不另立语义。

## 8. 依赖顺序、工作量与待决问题

实施顺序建议：

1. Risk 明确原因/动作/主体目录、参数类型、零终态规则、冻结闭包及 Wire/Descriptor/Hash 演进；Web 确认显示与编辑状态。
2. Platform/Gateway 确认复用或新增 Route/权限、协商方式、读取目录边界，保留当前 Body Limit。
3. Risk 提供 risk-api 与新旧成功/失败 fixtures；BFF 完成本地 Models、版本 Reader、Adapter、安全字段错误转换。
4. Web 对接新版目录和多动作编辑；BFF/Owner 分别回归 V1；联调保存、冻结、校验、未知结果恢复和授权/日志/容量。
5. 真正运行规则、冷却、标签或生产发布单独评估，不随配置 UI 暗中启用。

| 子项 | BFF 相对工作量 | 依据 |
|---|---|---|
| 纯 UI 兼容回归 | 小 | 合同不变，无需生产代码变更。 |
| Reject 目录与消息 | 中 | 多个双向模型、目录版本、安全披露与历史回读。 |
| 多动作/参数/零终态 | 中偏大 | 单对象转集合、类型/可空性变化、严格 Shape/Descriptor/响应关联全链修改。 |
| 新旧协议共存、错误路径与安全回归 | 中 | 封闭 Reader、协商/路由与权限联动；工作与上述重叠，不相加。 |
| 新目录页面聚合（如需要） | 小至中 | 取决于查询次数、版本一致性与权限粒度；不是必建层。 |
| 运行期副作用 | BFF 无直接实现授权 | Risk Owner 另评；不能通过增加 BFF 数据库或事务承担。 |

待 Owner/用户确认：

1. **Risk + 产品：** 原因代码是否仍为 rule.reasonCode；Message 是冻结原文、模板版本还是覆盖值？空值、长度、语言和外部披露规则是什么？
2. **Risk：** 首批动作及可绑定主体的 typed 字段清单是什么？现有字段 actionParameterFlag=false；没有资格前 BFF 必须拒绝把示例动作当已支持目录。
3. **Risk：** actions 的准确结构、顺序、参数来源、值编码、重复策略，以及零终态/零动作组合有哪些合法情况？
4. **Risk + Web：** 目录升级后旧 DRAFT/FROZEN/RETIRED 如何回显与继续编辑，哪些历史字段保持缺省而不补写？
5. **Risk + Gateway + Platform：** 同 Route 协商还是新版本路径；是否需要独立目录查询；保存附加动作是否沿用 SAVE Permission？没有确认前不注册通配权限或新 Route。
6. **Risk + Foundation/BFF：** 新错误的稳定业务 Code 与安全外部字段路径如何注册？静态检查 errorSummary 的现行闭集是否足够，新路径不得直接塞入旧 SUMMARY 正则。
7. **用户/主任务：** 是否确认本轮后续实施只包含配置、冻结、TEST 静态校验？本报告按该窄范围估算，未纳入新决策流或真实副作用。

这些问题影响后续合同冻结，但不阻止本次评估交付。
