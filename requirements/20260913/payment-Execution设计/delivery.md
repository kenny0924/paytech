# Execution 需求交付记录

## 2026-09-17 查询 Intent 接口

用户确认提供 Intent ID 查询接口，并要求返回 `sourceId/sourceType`。本轮按[查询接口修订](./get-intent-amendment.md)开发既有 EX-I02，只读 Intent 并核对 Client，返回六个必要字段。沿用当前暂缓单元测试的安排，执行编译与静态检查；无数据库结构变更、外部调用或支付推进。实际验证见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-16 终止 Intent 接口

后续用户要求所有正常业务结果返回 HTTP 200，由上游自行判断 PENDING；本次仅调整终止接口成功响应及相关注释和合同，错误 HTTP 语义保持。

用户在确认安全终止、未决返回 PENDING、授权需撤销和已支付需退款的边界后，要求“帮我做一下这个接口”，随后确认继续并明确“单元测试可以暂时不写”。本轮执行既有 EX-I04 的研发与编译/静态检查，接口细节见[终止接口修订](./terminate-intent-amendment.md)。不接入 Commerce、Channel 或 MQ，不执行 Schema 变更、提交、推送或部署；实际验证及待办见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-15 ActionFactory 与直接执行调整

用户要求由 ActionFactory 管理、不通过补偿事件执行，并在保存上下文后创建 Action。本轮按此调整实现与[动作修订](./card-pay-action-flow-amendment.md)，保留同事务切换与原未决占位；没有新增恢复入口或数据库结构变更。验证与实际范围见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-15 CARD + PAY 动作开发确认

用户确认动作和 Attempt 状态设计后要求开始开发，授权实现[Action Flow 修订](./card-pay-action-flow-amendment.md)中的 CARD + PAY、条件对象、一次结果事务切换和公共补偿接入。其余 Owner 保留明确 TODO；不实现 Capture/Void/Refund 新入口。同步自有动作命名及模型注释，保留旧数据切换约束。本次不提交代码、不执行 SQL 或部署；按用户既有要求不新增或运行 Java 单元测试。实际检查结果见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-15 公共幂等包接入授权

用户在差异分析后明确“改造吧，使用公共包”，本轮进入原 pay 受理链的接入实施。范围包括 Java/POM/配置、命令表 PDManer、生成源和待人工执行的 SQL；保留一次受理事务、原 Hash、只读观察与动态响应。具体约定见[项目接入说明](../../../payment-orchestration-service/docs/development/command-idempotency-differences.md)，切换路径见[迁移说明](./db/command-idempotency-migration.md)。不新增业务流程、不修改其他系统实现，也不沿用此前提交或数据库执行授权。实现和验证证据见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-14 公共幂等包复用方向与差异分析

用户明确要求“保持一致，有公共包的就用公共的”，本轮指定交付差异分析。[项目分析稿](../../../payment-orchestration-service/docs/development/command-idempotency-differences.md)整理公共包与 Payment 的记录、表结构、事务、动态重放及代码归属差异；[Processor 规范](../../../payment-orchestration-service/docs/development/processor-conventions.md)补充公共能力优先复用规则。此确认不等于已完成接入或授权执行数据库迁移；当前进展与核对范围见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-14 数据库工件整批精简

用户要求“先帮我把数据库维护一下”，并明确选择“把此前整批字段精简一起整理并落模”。本轮合并字段差异分析、设计和模型/SQL 工件维护；范围见[字段修订](./execution-field-simplification-amendment.md)。同步生成器、独立模型清单、索引/关系、物理决策和场景；其余 Java/接口及实际库切换仍为下一阶段，不沿用此前 SQL 或提交授权。

正式模型与完整 SQL 是当前目标；前序空库迁移标注后继，并准备当前精简的创建/预检/迁移文件。状态历史、认证、路由和 Pricing Fact 的代码及下游消费缺口明确登记[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。


## 2026-09-14 pay 按已确认方案重构授权

用户明确要求“按照之前的调整，重构一下这块的代码，参考 transaction 系统”。本次进入已有需求的研发阶段，按三表状态、capture mode、单动作执行位、内部风险 Action 与原渠道操作恢复方案实施；参考 Code-PMX transaction 的入口、模板和具体 Processor 主流程组织。保留每接口独立 Processor，取消 pay/Capture/退款/冲正的重复受理 Processor；共享执行阶段不承担另一个 HTTP 用例。Card 保留方式专属代码流程，Wallet/Crypto 与真实 Owner 仍按既定 Mock + TODO 范围。

本次可以编辑代码、模型、SQL 工件和验证材料；不沿用此前提交或 SQL 执行授权。旧状态、旧 PAY_V1 和非空历史评估必须先收敛或经专项核验迁移，不能直接猜值转换。当前实现与实际验证结果见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-14 内部风控 Action 与工具身份确认

用户回复“可以，没问题”，确认 Internal Risk 作为 Action、评估字段迁移、Payment 不保留限额 Group ID，以及换工具/新支付建立新 Attempt。[本次修订](./internal-risk-action-amendment.md)细化 RISK_CHECK 的评估完成与放行区别、风险未知的原评估恢复、类型分派和单动作占位，并同步原 Card/Wallet Excel 中的流程与三表快照。

本轮交付设计和场景附件；其余字段清理建议不随此确认自动生效，也未进入 Java、模型或 SQL 实施。原提交重试与符合工具合同的同卡补材料仍复用身份；新工具不能绕过原未决资金、授权或成功事实。验证结果见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)，不沿用此前提交授权。

## 2026-09-14 限额字段移除与风控步骤讨论

用户明确要求先去掉 Payment Attempt 的 limit_reservation_group_id。本轮按[限额关联修订](./risk-limit-correlation-amendment.md)记录字段移除目标和 Risk 本地原关联消费边界，同步 Payment/Risk 数据库文档入口。Internal Risk 是否成为 Action、风控字段移动及其状态语义仍作为建议讨论；不将问题表述记录为实施确认。工具变更与同卡补输入沿用既有交易级 Token 合同。本轮没有进入 Java/SQL/模型实施，也没有更新 Excel 或沿用此前提交授权。

## 2026-09-14 状态与 Channel 恢复设计收敛

用户提出 capture mode、精简 Attempt/Action 状态及 active_action_id，并明确 QUERY 不属于业务 Action，由 Channel 标准操作内部组织原请求查询、重复响应处理与补偿；对该方向回复“可以”。本轮将这些决定收敛为[状态与恢复修订](./execution-state-channel-recovery-amendment.md)，同步原 [Card/Wallet 场景 Excel](../../../payment-orchestration-service/docs/payment-execution-state-scenarios.xlsx)及主题入口。

修订明确手动授权完成时 Attempt 仍 PROCESSING、清空 active_action_id 并保留 Intent 占用；只有实际收款才成功。恢复始终使用原 Action，查询不创建第二条业务 Action。Excel 增加授权 UNKNOWN 后由 Channel 确认原授权的场景，保留逐步三表快照，作为设计示例供评审。细化字段、接口及历史兼容影响见修订稿；旧文档受影响部分标为前序基线，其他合同保留。

本轮交付限设计与场景附件，不自动进入 Java、模型或 SQL 实施，也不沿用早前的提交授权。新设计与前序实现的差异统一登记[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-14 移除 Caller

用户明确要求“帮我去掉 caller 的逻辑”，并确定内部调用方完全可信。本次按[内部信任修订](./trusted-internal-amendment.md)移除授权端口与调用、由现有命令身份模型生成 Client 幂等空间，并保留全部业务资源归属及状态门禁。此请求授权相关代码、测试和文档修改，不沿用上一轮的提交授权；没有新增 API 或 Schema 变更，SQL、提交、推送、部署不在本次范围。实际结果统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-13 PI-I01 局部重构授权

用户明确要求重构 `PaymentInstrumentController#prepareCardInstrument` 的调用链。本次限该 Controller 方法、对应单个 Processor 和相关检查，整理首次准备与原绑定重放的控制流；保持既有接口、业务合同、Hash、资源锁和本地事务原子性，不扩展其他支付入口。实现与实际验证范围见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)；本次请求未授权提交、推送或执行 Token 迁移 SQL。

2026-09-14，用户明确要求“代码提交”，授权保存上述重构、Payment 当前 import 整理与关联文档。本次仅本地 Git 提交；此前关于 Caller 的讨论未形成改造指令，Caller 接口和默认不可用装配继续保留。SQL、推送和部署仍未获授权；提交结果与复验记录统一见当前台账。

## 2026-09-13 确认来源

用户在 Payment 模块拆分后，依次讨论 Execution 接口、表与状态机、移除 Customer Action 表、Card 认证/资金操作、Wallet/Crypto 扩展和编排方式。

本轮明确请求：

> 可以，生成需求文档吧，目前只做execution的设计，其余的暂时mock + todo

已确认的方向：

- 统一 Execution，按实际支付方式和业务目标组织代码流程。
- Card 首次支付保留一份流程，包含开始/结束节点、认证分支、立即支付/仅授权分支；后续操作使用原交易。
- 本期不采用数据库可配置流程或通用工作流执行器；前序关于数据库流程版本的建议不再适用。
- 以移除 t_payment_customer_action 为设计目标，同时保留必要交互与恢复能力；最终字段与状态尚待评审。
- 目前只做 Execution 设计，其他能力按 Mock + TODO 隔离；未来 Wallet/Crypto 不等于本期交付其业务能力。

## 本轮交付

- [需求文档](./requirement.md)：流程图、能力范围、模型要求、依赖边界与设计验收标准。
- [后续设计入口](./technique.md)：已识别差异、Mock 场景、逐 Owner 替换 TODO 和待定接口/数据问题。
- [范围决定](../../../agent-docs/decisions/2026-09-13-payment-execution-code-orchestration.md)：记录已确认方向及其准确取代范围。
- 需求目录与 Agent 导航增加检索链接。

当前交付是需求评审稿与设计入口，不是接口/字段/SQL 已全部批准的阶段 3 方案。没有创建应用代码、Mock 实现、数据库工件、公共路由或页面，也没有执行 SQL、部署、提交或推送。当前业务实现状态仍只见[项目台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-business-modules)。

## 文档核验

- 四份新增 Markdown、32 个本地链接（含锚点）、代码围栏、尾随空白及两个检索入口检查通过。
- Card 流程图的节点声明与连线引用结构检查通过；12 条设计验收编号唯一。此检查不等于流程执行或图形渲染验证。
- `git diff --check` 对本轮文档及入口范围通过；新文件的空白由独立脚本检查。
- 范围复核保留了 Commerce 原提交身份、Execution 卡数据边界、无数据库工作流及原渠道恢复要求；真实接口/字段仍标为待设计。
- 未运行 Java、数据库或渠道测试；没有应用和数据库工件变更，不将文档检查登记为业务能力验证。

## 后续范围

用户审阅本轮需求后，再进入 Execution 差异评审及详细接口、状态、数据、恢复与 Mock 合同设计；当前请求没有授权自动进入研发。

既有 Payment/Card 公共文档中仍有旧交互表和旧 Customer 入口表述，已在本需求明确定位；完整合同定稿后同步主题 Owner、全局需求/时序和模型，不在本轮将未完成的物理修改写成现状。

## 2026-09-13 需求确认与阶段 2 分析

用户先确认需求并询问下一步；收到“现状差异与影响分析，交付差异表和模块依赖图”的阶段范围后，回复：

> 可以，关于不同支付方式的处理流程这块我觉得你写代码要注意一下抽象，别的没啥说的了。

本次确认进入阶段 2，并增加了不同支付方式流程抽象的明确要求；没有授权立即写应用代码，也没有将详细接口/字段视为已批准。

本轮更新：

- [技术文档](./technique.md)补齐流程抽象、模块依赖图、Execution 逐表处置建议、其他 Owner 影响、状态精简约束及阶段 3 待办。
- [需求文档](./requirement.md)确认阶段状态并加入 PEX-13：方式分发、专属步骤及恢复应可扩展，不能以强制 Card 模板或静默兜底处理未来方式。
- [范围决定](../../../agent-docs/decisions/2026-09-13-payment-execution-code-orchestration.md)记录用户补充；需求目录和 Agent 导航同步指向本次分析。

工程核对使用当前 Execution POM、包职责、模块边界测试和 PDManer/Schema；发现的接口/持久化缺口在技术文档中逐项标记。此处只记录核对证据，不复制当前实施台账或把模型存在当作业务实现。

阶段 2 验证记录：

- 四份需求/决定文档的 42 个本地链接及锚点、围栏、尾随空白、两个检索入口检查通过。
- 两张 Mermaid 图的节点声明/引用结构检查通过；不声称已做图形渲染或运行验证。
- 逐表清单与 PDManer 及模块 Owner 检查中的 11 张 Execution 表逐项一致，无遗漏、重复或候选表混入；13 条验收编号唯一。
- `git diff --check` 通过；18 项 Payment POM、Java 源码、PDManer 和 Schema 的 SHA-256 与本轮核对前一致。
- 没有运行 Java、数据库、渠道或业务流程测试；本轮只验证分析材料及证据一致性。

阶段 2 分析已交付待评审。下一阶段为接口、状态、模型、恢复和 Mock 合同设计；没有应用、数据库、全局 Wire、SQL 执行、部署或提交动作。

## 2026-09-13 阶段 2 确认与阶段 3 设计

用户收到阶段 2 差异分析及下一阶段范围后回复“可以”，确认进入详细接口、状态、数据、恢复与 Mock 合同设计。此确认不表示候选字段或 SQL 已获实施/执行批准。

本轮阶段 3 交付为评审稿：

- [完整设计入口](./design.md)：一句话规模、流程图、不同支付方式的抽象与评审重点。
- [接口合同](./api-contract.md)与 [12 个接口样例](./api-examples.json)：内部消费者、字段、错误、身份、重放及逐接口表影响。
- [数据方案](./data-design.md)与 [DDL 草稿](./db/ddl.sql)：来源绑定、Attempt 提交身份、Action 认证/交互字段、交互表删除、Web 补偿表对齐及历史数据限制。
- [状态与恢复](./state-and-recovery.md)：取消/创建竞态、授权保留、资金发送断点、退款额度、重复成功及可靠发布。
- [Mock 合同](./mock-contract.md)与 [研发验收计划](./verification-plan.md)：六组 Owner 替换 TODO、21 个计划场景覆盖 PEX-01～13。

本次方案规模：Payment 1 系统；拟新增 12 个内部 HTTP 接口，现有 Wire 修改/废弃 0；新增 1 表 8 字段，调整 3 张旧表（新增 14 字段、修改 10 个字段结构、删除 6 字段，另改 4 个字段注释），删除 1 表 10 字段；二级索引新增 9、删除 5，主键新增/删除各 1；业务及注册 DML 0 行、页面 0。当前模型仍含旧交互表和旧补偿表，本轮没有将候选方案直接写成实施现状。

本轮核验：

- 需求目录与决定文档的本地链接（含显式 HTML 锚点）、围栏、尾随空白和检索入口检查通过。
- 六个 Mermaid 块的基本结构核对；流程图节点引用、时序参与者及状态图声明核对；未做图形渲染或流程运行验证。
- DDL 的 1 个 CREATE、3 个 ALTER、1 个 DROP 与当前 PDManer 对象逐项核对；新字段/索引未撞名，修改/删除目标存在，计数与数据方案一致，新增/修改字段都有注释；无 CHECK/FK/DML/连接或自动迁移语句。未在 MySQL 解析或执行，正式实施前仍需同步模型并验证 Schema。
- 12 组 Method/Path 与 JSON 示例逐项匹配，示例为合法 JSON；13 条需求标准、21 个计划场景编号唯一且覆盖关联完整。此项不是 API 集成测试。
- `git diff --check` 在本轮范围通过；18 项 Payment POM、Java、PDManer 和完整 Schema 的 SHA-256 与本轮核对前一致。其他任务的未提交修改保持。
- 未运行 Java、数据库、MQ、渠道或真实支付测试；未执行 SQL、部署、提交或推送。

阶段 3 已交付待确认；确认后下一步为[计划中的 Execution 本地切片研发](./verification-plan.md)，其余 Owner 继续 Mock + TODO。按[需求交付流程阶段 3](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md#阶段-3方案接口与数据设计)的“用户确认方案、接口、数据与页面设计及研发计划后，进入阶段 4”，本轮不自动开始业务实现。任何数据库执行仍需具体库、SQL 和备份范围的单独授权；当前无该授权。

## 2026-09-13 阶段 3 确认与阶段 4 研发记录

用户在阶段 3 设计、接口、数据与研发计划交付后回复“可以”，授权开始 Execution 本地切片研发；此前强调的不同支付方式抽象和其余 Owner Mock + TODO 持续有效。SQL、部署、提交和推送未获授权。

本轮交付代码、模型/人工 SQL、合成 Owner 与验收测试源文件；Card 流程与通用分发分离，未知方法不回退 Card，Capture/Refund/Reversal 使用各自入口。新增来源关闭墓碑，认证与交互归原 Action，退款 UNKNOWN 保留额度，Reversal 从 PENDING 收敛后绑定原资金分支。受理前通过 Card 端口核验稳定引用；瞬时材料不参与幂等 Hash 或恢复任务。

研发核对明确了三项接口内实现细节：409 使用 Foundation 的 `CONCURRENT_REQUEST`；任务用 Client + 本库资源 ID 的最小复合键核对归属；Foundation 登记接口没有首次执行时刻参数，到期 Handler 检查原 expiresAt 并使用有界退避，首次资金门禁本身直接检查到期，不依赖准点调度。仅 AUTHENTICATE 的原查询可把 UNKNOWN 收敛为已确认的挑战等待，资金未知不能据此重发。

已运行的独立 Java、模型/场景、需求站点检查及原始日志统一列在[当前实施台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。真实 HTTP 测试遇到本地 `payment_app` 鉴权失败，未进入业务测试；未自动读取 `.env.local`、建表或迁移。阶段 4 未完成验收，不能进入下一阶段部署。

下一步的具体制品已准备：[空库 13 表 SQL](./db/execution-create-empty-schema.sql)与[空旧表增量方案](./db/ddl.sql)。须先取得连接配置的使用授权并只读确认实际库，再选择适用 SQL；有历史数据或 UNKNOWN 时不能执行空表增量。完整运行与仍缺场景以当前台账为准。

## 2026-09-13 本机连接/空库 SQL 单独授权与阶段 4 交付

用户对“允许读取 Payment/Redis 本地配置，并在确认 `mor_payment` 的 13 张目标表均不存在后，执行 `execution-create-empty-schema.sql` 继续验收”回复“可以”。本轮仅使用该授权读取两个相关连接密码，未输出或写入仓库。只读前置确认整个目标库为空后，人工执行已准备的 13 表 CREATE；没有执行替代增量文件、其他库 SQL、账号变更或部署。

[开发检查与验收编号映射](./development-checks.md)提供可审阅的实现范围、代表场景和后续组合；[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)统一记录 SQL/清理/打包/测试/覆盖率的实际证据。前段“鉴权失败、未执行 SQL”是本次授权前的历史检查点，已由本次证据收敛。

研发检查修复 Redis 配置、错误响应 no-store、结果方式绑定和挑战标记校验。四类 Handler 有独立入口测试；实际进程退出、Redis Lease 失效、Claim 接管、确认丢失重投与业务/任务回滚均按具体用例验证。test 的直接 api/execution 依赖仅补齐公开类型和完整覆盖率汇总，生产依赖不变。

当前阶段为研发成果交付；完整覆盖率未达到项目目标，综合验收组合、真实 Owner/Broker/调度仍按映射保留。遵循需求交付流程的阶段 4 确认点，等待用户审阅后进入阶段 5；未自动进入发布、提交或其他系统实施。

## 2026-09-13 阶段 4 评审：独立 Processor 与开发规范

用户要求“每一个接口对应一个 Processor，而不是都写在一起”，并明确逻辑实现须符合开发规范。本次在已授权 Execution 范围内修正实现，未进入下一交付阶段。

删除集中式 API Processor 和集中式受理 Processor；12 个接口分别委托独立用例入口，七个写用例的短事务受理也分别隔离。REST 协议转换、业务查询、命令绑定与方式流程各归对应职责；Card 主流程按风控、路由、认证/资金执行整理，闭集枚举按稳定 Code 转换。具体映射与锁代理边界见[详细设计](./design.md#11-接口与-processor-一一对应)。

新增架构门禁阻止接口重新集中、Processor 引入 HTTP/持久化类型及 Converter 执行业务查询；通过真实 HTTP 核对原提交 Hash 与观察重放，通过闭集 Code 反例防止依赖 Java 常量名。实际测试、源码和制品证据只记录在[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

## 2026-09-13 交易级工具 Token 与统一 pay 修订评审

用户连续确认：上游统一 pay；支付工具 Token 绑定本次交易提交；Attempt 直接关联 Token、去掉旧 Instrument；Token 有效期统管材料使用上限，不加独立会话表；Execution 决定动作、Card 负责受控用卡与携卡数据的渠道适配。随后对“先修订现有需求的接口、数据和恢复合同，再改 Payment、后续同步 Commerce”回复“可以”。

本轮按受影响的阶段 3 完成[修订稿](./instrument-token-amendment.md)、[新合同样例](./instrument-token-api-examples.json)及[仅空旧表适用的 SQL 草稿](./db/instrument-token-empty-migration.sql)。旧合同增加准确后继路由，旧样例/SQL/运行证据保留；不重复登记一项同义需求。

代码核对发现，Commerce 在调用旧 fingerprint 后才以本地命令 ID 建立原 submissionId。为保持本地已受理、Payment 尚未绑定卡时的同键换卡拒绝，本稿保留该既有能力并明确 PI-I00 内部 HTTP；没有省略完整参数校验，也没有新增登记表。身份查询 keyRef 与原 Hash 版本、Token 领取/补输入、历史隔离属于本次新增技术评审内容，不冒称已经逐项获批。

文档静态检查与源码保持核对见[本轮证据](./assets/instrument-token-document-checks.json)及[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。没有修改 Java 运行逻辑、PDManer 或正式 Schema；没有读取数据库配置、连接数据库、执行 SQL、部署、提交或运行真实卡接口。

下一阶段拟为 Payment Execution 的工具绑定、统一 pay、查询/清理及依赖 Mock 研发；Commerce 代码和真实 Card 后续按范围接入。按[需求交付流程第 2.6 条及阶段 3 确认点](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md#阶段-3方案接口与数据设计)，交付本修订后等待用户审阅，不把原基线研发授权扩展成未评审新合同的连续实施。


## 2026-09-13 工具 Token 修订进入 Payment 研发

用户对“下一步按此稿改造 Payment Execution，其余 Owner 继续 Mock + TODO”回复“可以”，本修订进入阶段 4。范围包含 Payment API、独立 Processor、Execution 工具服务/持久化、统一方式输入、查询与清理以及合成 Owner 测试；Commerce 代码仍为后续阶段。

[研发导航与验收映射](./instrument-token-development-checks.md)用于本次代码评审。[空旧表迁移](./db/instrument-token-empty-migration.sql)和[新目标空库初始化](./db/instrument-token-create-empty-schema.sql)已与正式模型/Schema 对齐；旧 SQL、样例和验证记录保留为历史基线。两份新 SQL 互斥，不由应用或测试自动执行。

已另行询问本机 mor_payment 的备份、临时库恢复核验和本次 ALTER/DROP 执行授权。研发确认不等于 SQL 执行确认；具体编译、HTTP/模型测试和待数据库验证事项见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。未进入 Commerce 实施或真实 Card/Channel 接入。

- 本次源码、模型、SQL 和测试映射已整理到[工具 Token 研发检查](./instrument-token-development-checks.md)；实际运行结果和待执行 SQL 仅见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。本次独立 SQL 授权仍待回复，不能把旧空库 CREATE 授权扩展为 ALTER/DROP；获授权后继续本阶段完整数据库回归。

## 本地版本检查点授权（2026-09-13）

用户要求“先帮我提交一版本吧”，授权保存当前 Payment 研发和此前支付渠道命名关联工件。此次先保留现有结构，讨论中的 Processor 依赖与 Card 动作决策调整未实施；数据库 ALTER/DROP、完整数据库回归、推送和部署不随提交获批。提交与检查状态见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。

- 2026-09-14：用户明确 Payment 系统不允许调用 Commerce；在既有研发范围内按[调用方向修订](./payment-commerce-direction-amendment.md)移除所有 Commerce 出站端口，并补齐创建时传入/冻结国家及本地定价。实际检查与 SQL 待办只见当前台账；此次不提交、部署或执行 SQL。

- 2026-09-14：用户明确要求“提交”，本地提交范围为上述 Caller/Commerce 依赖移除、国家冻结及关联模型、SQL、测试和文档；提交和复验证据见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。排除其他需求修改，未执行 SQL、推送或部署。

## 2026-09-14 按精简模型重构 Pay 受理链

用户要求按修改后的模型重构 pay，并明确确认“先完成 pay 受理链，后续流程保持 TODO”。本轮范围为精简请求/幂等投影、Processor 上下文、一次本地事务及响应读取；沿用数据库对象上下文，不新增支付编排层。现行合同见[字段修订第 7 节](./execution-field-simplification-amendment.md#7-pay-受理链合同)，阅读入口见[受理流程](../../../payment-orchestration-service/docs/pay-interface-walkthrough.md)。

本次不进入 Create Intent、后续资金流程、Channel 或通知合同迁移；SQL 执行、提交、推送与部署不在授权范围。实际验证结果及旧库前置只记录于[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-execution-implementation)。
