# 渠道 API 逻辑定义评审记录

2026-10-13 起现行设计见[需求](./requirement.md)、[数据设计](./data-design.md)、[流程设计](./flow-design.md)及[第 23 节](#23-迁移至-platform-与现行设计整理2026-10-13)；第 1～22 节保留原阶段证据，其中的路径、范围和验证结果按当时目录与版本理解。

## 1. 本轮阶段与授权

本次按用户明确收窄后的单阶段“渠道 API 逻辑定义”请求交付概念评审稿，遵循[需求交付流程](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)的指定阶段处理规则。用户确认了概念范围并同意继续细化三个模型；本轮新写的字段与规则仍待评审，不登记为完整方案已批准。

本轮不进入应用研发、管理 HTTP/数据库合同、页面实现、SQL、部署或提交。尚未约定后续完整阶段安排，不因用户回复“可以”而推定其余阶段全部授权。

## 2. 用户确认记录（2026-09-14）

| 对话事实 | 本轮采用的范围 |
|---|---|
| 用户提出 `Channel → 支付方式 → 所需 API`，API 包括传输、请求/响应头体、签名/验签、加密/解密等 | 保留该主层级，编解码只作抽象 |
| 用户要求考虑同 Channel 的签名/加密复用，并补充不同支付方式也可能不同 | Channel 中多套方案，API 显式选择；同方式不同 API 也可不同 |
| 用户说明未来提供 authorize、sales、query 等标准 API，但“这个先不考虑……现在想做的就是把渠道的api逻辑定义好” | 标准 API、跨接口编排不进入本轮 |
| 助手提出 API 定义、处理步骤定义、协议方案定义三个模型及单次渠道通信边界，用户回复“可以” | 授权继续细化本轮逻辑评审稿，不表示之后生成的详细规则已确认 |

本表记录可见对话的日期与原意，不编造确认时间或将此前建议全部认定为用户决定。

## 3. 本轮交付

- [需求范围与验收标准](./legacy-requirement.md)
- [三个逻辑模型及复用/失败示例](./legacy-technique.md)
- [需求目录检索入口](../../README.md)

下一评审点：本轮 API 输入输出、处理步骤、协议方案及复用规则是否成立。若有修改先修订本稿；管理页面、存储和执行器等下一阶段范围在本稿评审后再确定，不自动开始。

## 4. 首稿验证记录

- 三份新文档的 20 个本地文件链接存在；代码围栏成对、文件有结尾换行且无尾随空白；十项验收编号与技术稿对照一一对应，三份文件均有需求索引入口。
- `git diff --check -- requirements/README.md requirements/20260914/payment-渠道API逻辑定义` 通过；未跟踪新文件的空白由独立脚本检查。
- `npm --prefix requirements-site test` 完成构建并通过 136/136，失败/跳过均为 0；日志：`/tmp/codemor-channel-api-definition.0Yshut`。这是执行时共享工作区的需求站验证，不是渠道协议执行测试。
- 自查补充了按结果分支校验输出，避免把拒绝响应缺少成功字段误判为无效结果；同时明确步骤组合与单能力方案的内容形态。

本轮只修改本目录及 requirements/README.md，保留已存在的 Execution 与其他任务改动。逻辑评审未运行 Java、数据库或真实渠道交易测试，不能声明配置引擎已经实现；未提交、推送或部署。

应用实现、验证和部署状态仍由[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#payment-business-modules)管理；本轮评审稿不新增实现状态。

## 5. 用户反馈后的简化与图解（2026-09-14）

用户提出三点：询问 paymentMethodRefs 的原因；要求用图或更直观的方式解释字段；明确“暂时不考虑版本发布的问题，修改直接生效”。

- paymentMethodRefs 是助手为 API 跨支付方式共用而引入的扩展，并非用户明确要求。当前稿回到用户原先的树形层级，以单值 paymentMethodCode 表达 API 归属；签名、加密等方案继续跨方式复用。共享 Token API 的独立归属暂不展开。
- 技术稿新增第 0 节，用一笔合成订单调用对应“输入、请求处理、HTTP、响应处理、输出”，逐项解释输入绑定、输出绑定、方案和参数。对话内增加同主题可点击示意；它不发送网络请求，不是 Operations 产品页面。
- 移除 definitionRevision、schemeRevision 及持久化依赖修订设计；用户已明确的目标是保存后直接生效。稿中细化为保存成功后的新调用读取新配置，当前调用使用开始时完整读取的数据，不在执行中途切换；这不增加配置版本或发布流程。
- contextRequirements 不再单列为需要用户配置的块，运行数据来源归入步骤输入绑定说明。字段表新增实际例子，不把每个技术术语当成必须独立持久化的字段。

本次文档检查通过：三份文档的 20 个本地文件链接存在，十项验收映射完整；移除的字段不再出现在模型字段表中；围栏、空白及 git diff --check 通过。

需求站 `npm --prefix requirements-site test` 构建与 136/136 检查通过，0 失败/跳过；日志 `/tmp/codemor-channel-api-simplification.fdlq0c`。本次没有应用代码或数据库变更，站点检查不能证明配置执行能力已实现。

图解在隔离的本机 Chrome 中通过 20 组步骤切换检查，覆盖 736/360/320 像素内容宽度与明暗主题，无脚本异常或横向溢出；已检查对应截图。证据目录 `/var/folders/t2/xvr6hr7x4yq2c64fx_216r8c0000gn/T/codemor-channel-api-visual-ElLl5d`。首次尝试的 Playwright 默认浏览器包缺失，随后使用已安装 Chrome 验证，未安装浏览器或更改用户浏览器资料。

当前仍为逻辑评审；本次反馈没有授权应用研发、真实渠道接入、SQL、提交或部署。后续根据用户对图解和简化稿的反馈继续修订。

## 6. 业务上下文与查询补数（2026-09-14）

用户补充：调用方会传入支付上下文或退款上下文；数据不足以满足渠道 API 定义时，会提供动态查询方法，让 API 在组装过程中获取补充数据。

本次同步内容：

- inputDefinition 改为描述所需业务上下文及数据来源，不要求调用方为每个渠道预先填齐所有字段；区分上下文直接取值、显式查询、缺失时调用指定查询以及前序步骤结果。
- 查询作为请求处理能力，配置方法标识、入参与命名结果；开发者提供有类型的 Owner 查询，配置不直接填写 SQL、任意 Bean 方法或 URL。查询具体方法清单与实现尚未设计。
- 补充“基础上下文/查询入参校验 → 按需补数 → 完整请求校验 → 编码/签名 → 渠道调用”的顺序，避免在查询前就拒绝可以补齐的字段。
- 查询结果在当次调用内显式复用，不原地修改上下文，不覆盖已冻结的金额、币种、OE、Account/MID 及原交易身份。必需查询超时、失败或未得到有效数据时停止发送。
- 单次限制改为最多一次目标渠道通信；注册查询可以包含合法的 Owner 内部同步调用，不把它误判为跨渠道 API 编排。跨模块数据仍由 Owner 能力提供，远程调用位于事务外。
- 新增 CAPI-11/12 及退款查询原渠道交易号的合成示例；保存直接生效、单一支付方式归属、标准 API 编排暂缓等此前边界保持。

这些是用户新输入的逻辑细化，未实施查询执行器或改变应用模块依赖。三份文档的 20 个本地文件链接、十二项验收映射、围栏/空白及 git diff --check 通过；此前移除的多方式/版本字段仍未出现在模型字段表中。需求站 `npm --prefix requirements-site test` 构建及 136/136 检查通过，0 失败/跳过，日志 `/tmp/codemor-channel-api-context.7yjffh`。未执行 Java、数据库或真实业务查询测试，不把静态及需求站通过当作查询能力已实现。

### 本节确认

用户在审阅“业务上下文直接取值、指定查询按需补齐、补齐后校验请求”的说明与退款示例后回复“可以”。确认范围包括查询方法由开发者提供、API 配置选择方法及参数并使用返回值；查询结果在当次调用内复用，不覆盖原上下文；必需数据查询失败时停止发送。保存直接生效及标准 API 编排暂缓的边界继续保持。

该次确认结束这项逻辑补充的评审，不将它扩展为具体查询方法清单、上下文类字段或执行器实现已获批准。该时点仍停留在渠道 API 逻辑定义范围；之后阶段授权见第 7 节。

## 7. 现状差异与 NI 退款样例（2026-09-14）

### 授权与范围

用户询问“下一步做什么”，助手提出先做现状差异分析，并用真实渠道退款 API 验证模型，交付完整配置、调用过程图及能力缺口，再进入 Operations 页面、管理接口与数据设计。用户回复“可以”，本次按这个明确范围开展阶段 2，并把协议样例作为该阶段的验证材料。

本次仅阅读公开 NI 文档与本地代码，修改本需求目录及需求索引。不运行真实资金请求、不实现配置执行器、不修改业务代码或模型，不执行 SQL、提交或部署。

### 本阶段结果

- [NI 退款样例](./ni-refund-example.md)：真实 Method/Path/Header/Body、公共 Bearer 方案、上下文/注册查询取值、完整逻辑配置、过程图、保守结果规则及失败行为。
- [合成输入输出](./assets/ni-refund-fixture.json)：10.00 AED 精确转换为 1000，以及两条同金额 SUCCESS 记录仍不能认定本次退款的例子。所有身份和报文都是合成数据，没有真实 Token。
- [现状差异与影响](./gap-analysis.md)：十项缺口及逐系统影响，明确 Channel 工程骨架、已有 Execution 接入点、原交易协议坐标、结果关联、发送边界与存储规则冲突。

关键结论：逻辑模型能够描述请求与解析，但不能凭当前资料完成本次 NI 退款唯一关联和成功认定。样例方法/能力名尚未实现，预算和渠道上限未定；不能直接导入或替换真实适配器。公开文档未确认的协议事项列为待验证，不用配置猜测补齐。

### 验证记录

- 五份 Markdown 的 50 个本地链接存在；十二项 CAPI 验收与逻辑稿对照完整，围栏成对、结尾换行和空白检查通过。
- 两段 YAML 解析通过；公共方案引用、八个请求/响应步骤的唯一名称和前序输出引用有效。JSON 样例解析通过；核对身份一致、四段路径坐标、10.00 AED → 1000、双退款记录投影及 UNRESOLVED 分类。这些是文档/样例静态检查，没有运行配置引擎。
- `git diff --check -- requirements/README.md requirements/20260914/payment-渠道API逻辑定义` 通过；未跟踪文件的空白另行检查。
- `npm --prefix requirements-site test` 完成构建与 136/136 检查，0 失败/跳过；日志 `/tmp/codemor-channel-ni-refund.SQ2dna`。该证据仅适用于执行时共享工作区的需求站，不是 NI 联调或 Payment Java 测试。
- 只读检查 Payment/Operations 当前源码与官方文档，未修改应用仓库、模型或当前实现台账，未调用真实 Owner 查询或 NI API。Markdown 链接指向查询时可见源码；其他任务继续修改时应在研发前重新核对。

### 当时的下一确认点

本阶段结果待用户审阅。接受差异与影响边界后，下一阶段为方案、接口与数据设计：先确定配置存储和原交易补数合同，再给出 Operations 编辑流程、Owner/BFF 管理接口、数据变更与研发验证计划。标准 API 编排仍暂缓；阶段确认不包含研发、SQL 执行、部署或提交。

按[需求交付流程](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md)第 2 节“完成后停下”要求，本阶段交付后等待用户确认再进入上述设计阶段。

### 本节确认

用户在上述阶段 2 交付及明确的下一步范围之后回复“可以”。因此按该范围接受差异/影响判断，进入方案、接口与数据设计；不解释为真实 NI、应用研发、SQL 执行或部署已获授权。

## 8. 方案、接口、数据与 Operations 页面（2026-09-14）

### 授权与交付规模

本阶段完成上一节已获授权的设计工作。**提案涉及 Payment、Operations BFF、Operations Web、Platform Support 共 4 个应用，新增 13 项管理 API（Owner/BFF 各 13 个端点，修改/废弃 0）、2 张表17字段、4 个索引含2个主键；旧表及字段结构修改0，注册 INSERT 145 行/更新删除0，新增1个功能页面。**

### 可评审结果

- [详细设计与研发顺序](./design.md)：两表整体定义、Channel 级并发、主库一致读取、保存后可见性、模块职责和后续 Execution 接入边界。
- [配置字段合同](./definition-contract.md)：严格类型/来源/步骤、组合输出、HTTP/查询预算、结果分支、受控材料。
- [注册查询与调用合同](./query-contract.md)：现有原交易只读投影、NI 原退款目标缺口、prepare/execute 及发送事实。
- [管理接口](./api.md)及[机器可核对的路由清单](./assets/management-routes.json)：13项操作、权限、输入输出、分页/上限、并发冲突、失联保存和错误 Profile。
- [数据与 SQL 清单](./db/README.md)：Payment 两表 DDL、Platform 145 行禁用入口注册草稿、实际执行前置及恢复限制。
- [页面设计](./ui.md)：一页内 API/公共方案列表、详情和编辑；交互原型使用合成数据，不连接应用后端。

### 本阶段细化的关键点

1. 数据库存储是明确提案：仅放宽非敏感相对路径、目标用途别名和 API 超时；绝对地址、账户身份与凭据沿用运行配置。尚未改写现行权威、正式 PDManer 或完整 Schema，待本阶段批准后同步。
2. 保存立即生效通过每次从主库完整读取实现；旧更新时间仅防并发覆盖，不增加版本号或发布动作。
3. 已注册但未实现的能力允许结构有效的配置保存，页面显示缺口；运行前必须拒绝执行。NI 多级原交易导航和本次退款唯一关联仍未闭合，不伪造资金结果。
4. 新页面同时需要 Platform 随制品能力清单和前端组件目录登记，不能只依赖菜单 INSERT；不修改 Platform 权限模型或 Gateway 程序。
5. 早期样例仍是概念 YAML，正式拟实现结构由本阶段字段合同拥有；合成 fixture 的 OE 改为当前格式 AE001，不重新解释历史 Wire。
6. 保留 CAPI-01～12，补充 CAPI-13～16，分别映射管理、存储、类型/查询、视觉交互设计。原型不覆盖全部生产 UI 行为，冲突、权限失效等完整预期由页面稿明确。

### 验证记录

本阶段仅有文档、合成数据、SQL 草稿和本地页面原型检查，汇总见[验证记录](./assets/stage3/verification.json)。

- 最终检查11份 Markdown 的115个本地文件链接，其中14个带锚点链接，全部通过；16项验收编号完整，CAPI-01～12与逻辑稿映射保持。JSON、两段示例 YAML、围栏、结尾换行、空白及 `git diff --check` 通过。
- 13项操作的Method/Path/权限/Query/Body上限与机器清单、DML一致。145个注册ID互不重复，各INSERT列名与当前Platform完整Schema匹配。DDL核对为2表17列、2主键2唯一索引，无旧表结构变更、CHECK/FK或自动初始化。没有连接目标数据库核验ID冲突，也没有执行SQL或修改正式PDManer。
- `npm --prefix requirements-site test` 完成构建及136/136检查，0失败/跳过；日志 `/tmp/codemor-channel-api-stage3.log`。这是执行时共享工作区的需求站结果，不是渠道配置运行测试。
- 合成原型在隔离的本机Chrome验证5个配置区域×4种宽度×明暗主题，共40组布局检查；10项交互检查包括类型错误、跨方式方案引用、保留草稿、保存后只读、新建代码校验和空目录，无脚本异常。原型脚本 `node --check` 通过。
- 已查看并保留[桌面API](./assets/stage3/api-light-1024.png)、[窄屏暗色API](./assets/stage3/api-dark-320.png)、[公共方案](./assets/stage3/scheme-light-1024.png)。截图工具的固定iframe高度曾截断长页面，已只在本地验证包装中扩高后重拍；不是把截断截图作为通过证据。一次检查脚本的空状态预期文案与原型不符，改为实际明确文案后完整重跑通过。
- 原型只演示有限的本地状态，不包含完整生产页面、真实权限/保存后端或API执行。未运行Java、真实Owner查询、数据库或NI资金测试；未改应用代码、当前实现台账、正式模型，未提交、推送或部署。

### 下一确认点

请评审本阶段的存储窄修订、字段/查询合同、13项接口、两表与页面设计。接受后进入阶段 4：同步经批准的规则/模型，在 Payment、Operations BFF/Web、Platform 能力目录完成配置管理及通用 prepare/execute 边界，用合成查询和本地 Simulator 验证。真实 NI、标准 API 编排与 Execution 资金适配继续另行评审；本次确认不包括 SQL 执行、提交、推送或部署。

依据[需求交付流程](../../../agent-docs/REQUIREMENT_DELIVERY_WORKFLOW.md#2-每次确认的规则)“先交付，再确认”“完成后停下”，本阶段交付后等待上述明确范围的确认，不提前进入研发。

## 9. Operations Web Pencil 设计修订（2026-09-14）

用户明确要求“直接改 operation-console-web 下面的设计文件”，说明 Channel 后续由 Operations 维护，并要求完成 UI 设计。本次直接修改实际目录 [operations-console-web/docs/design.pen](../../../operations-console-web/docs/design.pen)，在同一个文件内新增12张 `CAPI ·` 画板，保留原有设计和工作区其他任务修改。此授权属于阶段3的设计修订，不代表完整设计包已批准或进入应用研发。

### 交付范围

- 一组3列、4行的设计，从画布 `(0,23460)` 开始；11张1440px桌面画板及1张390px窄屏画板，节点与截图见[UI索引](./ui.md#5-pencil-画板与视觉验收)。
- 覆盖 API 列表、新建、请求步骤、上下文与补数、传输、响应头/体及结果、公共方案列表/编辑/跨方式引用、字段校验、并发冲突、保存待确认、只读窄屏。
- 沿用现有应用栏、导航、ops 字体与颜色变量；新增内容均为可编辑 Pencil 图层。未把旧截图贴入画布代替设计，也没有新增发布流程。
- 编辑区收敛为5个页签，“响应与结果”中同时展示处理和输出，未改变 API 数据合同。NI 缺口和合成方案仍明确标注。

### 验证与保存

- 每组画板完成后通过 Pencil 截图核对；最终逐节点检查12张画板，无裁切问题，placeholder 全部清除，原有节点未删除。
- [Pencil 检查记录](./assets/pencil/verification.json)保存画板ID、坐标、尺寸与逐画板布局检查结果。同步后的需求文档链接、锚点、围栏/空白和 `git diff --check` 通过。
- 12张画板已由 Pencil 导出 PNG，保存在本目录 assets/pencil，供不打开编辑器时查看；这不是运行页面截图。
- 在 Pen 原生窗口执行保存，标题由 `design.pen — Edited` 变为 `design.pen`；已打开 Layers，选中 `CAPI · 01 渠道 API 列表` 并执行 Zoom to selection。
- 本次不修改 React/后端功能、不执行 SQL、不运行资金调用；只做设计与文档检查，不把前序需求站或 HTML 原型测试算作本次应用验收。

本次 UI 设计请求完成；完整阶段3取舍仍以设计包评审为准，尚未进入研发、部署或提交。

## 10. 按维护流程整理渠道画板（2026-09-14）

用户反馈页面排列较乱，要求按流程整理。本次针对当前渠道设计组调整画布位置、画板编号和 Layers 阅读顺序，保留12张页面的节点ID、内部内容与所有其他业务画板。

- A主流程：入口 → 基本信息 → 上下文与补数 → 请求处理 → HTTP传输 → 响应与结果，分两行、每行三张，从左到右阅读。
- B保存分支：校验错误、并发冲突、结果待确认分别对应独立异常场景；导览注明保存成功返回详情，避免误认为三张必须顺序操作。
- C公共方案：明确从请求/响应处理进入，选择或维护方案后返回原API；不混在API配置主线中间。
- D补充适配：窄屏只读单独标注，不计作操作步骤。
- 新增4条流程导览，仅辅助评审，不增加功能页面、路由或发布操作；当前坐标与名称见[画板索引](./ui.md#5-pencil-画板与视觉验收)。

本次验证画板/导览互不重叠、内容无裁切、编号和 Layers 阅读顺序一致，并同步文档索引；[布局检查记录](./assets/pencil/flow-layout-verification.json)保存12张页面、4条导览和阅读顺序。通过原生 Pen 保存后，Edited 标记清除，并定位到01入口。仅整理设计，没有变更业务合同或进入应用研发。

## 11. 按接入任务重构 API 维护流程（2026-09-14）

用户指出“退款上下文”“数据来源”“请求处理”难以理解，整体 API 维护流程不清晰；并确认维护者是熟悉渠道文档的接入人员或开发者。本次在已授权的 UI 设计范围内直接修订原 Pencil 画板，未进入应用研发。

- 主流程改为接口信息、请求参数、认证与报文处理、响应与结果、检查保存五步，配有步骤导航及下一步按钮。画板01为列表入口，02～06对应五步。
- 将“业务上下文”解释为调用方传入的字段集合，移到请求参数旁，提供字段目录和输入类型切换；选择 REFUND 不代表执行退款或标准 API 动作。
- 取消独立数据来源/请求处理主页面。请求字段直接显示取值和转换，补数查询从字段旁展开，说明触发条件、参数、返回字段及共用关系；03a为同一步的展开状态。
- 认证、签名和加密按 API 显式选择公共方案；保留高级处理顺序及作用范围。底层仍使用同一份 InputDefinition/ValueBinding/Step 合同，复杂步骤不会因 UI 分组而丢失或被重排。
- 最后一步集中核对请求模板、调用方输入和配置检查结果。静态示例明确不执行试算、业务查询或真实退款；NI 目标查询缺失实现和本次退款关联缺口继续保留。
- 原12张画板ID保持不变，更新PNG；保存冲突、结果待确认、方案维护及窄屏作为独立分支，与五步主流程分开。其他71个顶层画板保留。

通过 Pencil 截图检查主要重构视图和导览，逐节点布局检查12张页面及4条导览，无重叠、无裁切，placeholder均清除；记录见 [task-flow-verification.json](./assets/pencil/task-flow-verification.json)。通过 Pen 原生保存，Edited标记清除，并定位“03 请求参数”。关联文档检查通过：11份Markdown、141个本地链接、16个锚点；既有验收映射和结构化设计样例检查无失败，git diff --check通过。本次只修改设计和关联说明，不表示真实UI、协议执行器或NI退款已经实现。

## 12. 添加请求字段与取值交互（2026-09-14）

用户结合 Body 参数表截图追问“取值如何设置、点添加字段后如何处理”。本次补齐同一请求参数页的两个展开状态，以 amount.value 为例说明目标字段、字段来源、转换、币种参数及添加动作；不增加功能路由或真实调用。

- 03a 展示“添加请求体字段”面板及来源选择器，调用方数据来自已选输入合同字段目录。选择的是 refundAmount 字段引用，不是实际退款金额。
- 03b 展示完整配置：目标 JSON 整数、最小货币单位转换、币种取自 currency、必需及 null 约束。添加成功生成表格摘要并更新本地草稿，整份 API 最后统一保存；编辑回填同一面板。
- UI 文档明确重复路径、对象/标量冲突、未选来源、转换参数缺失和类型不兼容时阻止添加；嵌套字段合并保留已有 amount.currencyCode。固定值、查询结果、前序输出及获准普通运行字段均有对应选择方式。
- 新增2张可编辑画板，原查询画板改名03c，保持节点ID。参数展开、保存异常、公共方案分区排列；目前14张页面画板及5条导览，其他71个顶层画板保留。

新增画板已通过 Pencil 截图检查；全部相关画板及导览无重叠、无裁切、无残留 placeholder，证据见 [field-flow-verification.json](./assets/pencil/field-flow-verification.json)。已导出新状态 PNG，并通过原生 Pen 保存，Edited 标记清除，定位03a。仍为阶段3设计稿，未实现 React 表单、运行试算或渠道请求。

## 13. 查询改为系统函数取值（2026-09-14）

用户明确指出查询应是系统已经实现并注册的函数，API 配置直接调用函数，并通过工具取得返回数据。这是对此前查询专用配置的修正，不继续维护“什么时候查询 / 先检查哪个字段”的表单和触发策略。

- Pencil 03c 改为“系统函数｜选择函数与返回字段”：选择注册函数、为其入参绑定来源，再从返回 Schema 中选择 orderRef。调用方数据/固定值/系统函数/已处理结果/运行数据沿用同一个取值入口。
- 函数说明、参数、返回类型和缺失/失败行为由开发者注册，Operations 只引用；多个字段可通过已有处理结果引用同一次函数调用。不同调用不隐式合并，不新增查询对象管理页。
- 设计合同同步采用 FunctionDescriptor、functionCode 和 CALL_SYSTEM_FUNCTION，仍用现有 Step 与 STEP ValueBinding 存储调用及返回字段绑定；移除 existing/ALWAYS/WHEN_MISSING 查询专用配置。A02目录字段调整为 functions，能力缺口为 missingFunctions，不新增管理操作。
- CAPI-12、技术说明、NI逻辑示例和验证目标同步修订；NI示例通过原交易引用直接调用函数，去掉已有 refundTarget 的条件分支。注册函数仍受只读、Owner授权、事务外执行、当次复用及一次目标渠道通信边界约束。
- 函数选择器仅用元数据展示返回字段，运行时才调用函数。NI缺少真实协议导航事实及本次退款关联的限制继续保留，没有把函数示例声明为真实实现。

原有14张画板和5条导览保留，其他71个顶层画板不变。Pencil截图与逐节点检查确认无裁切、无重叠、无残留placeholder，旧查询控件检索为空；证据见 [function-flow-verification.json](./assets/pencil/function-flow-verification.json)。已更新预览PNG，通过Pen原生保存，Edited标记清除并定位03c。本轮仅修订设计及对应合同，未改React/Java实现、未执行SQL或真实渠道请求。


## 14. 三种取值方式与 Aviator 处理（2026-09-14）

用户要求上下文按 xxxContext.abc 读取 JSON，支持 Aviator 表达式，系统函数返回值也可用 Aviator 选字段或加工；取消独立的转换渠道格式选择。这是已授权设计范围内的取值模型与 UI 修订。

- 在原 Operations Pencil 文件中重做 03 请求参数及 03a/03b/03c；三张展开状态现在分别为上下文路径、Aviator 表达式、系统函数与结果表达式，是并列选项。主表只显示渠道字段、方式、规则与操作。
- 上下文可手输路径或从目录插入。表达式提供变量、白名单纯函数和参数模板。固定值使用表达式字面量，金额处理在表达式内完成；示例 money.minorUnits 是拟注册项目纯函数，不是 Aviator 内置或已实现函数。
- 系统函数先绑定入参，成功后用 result 引用返回值；选字段插入 result.orderRef，可继续编辑。多个字段可选择同一次已有调用，各自保留结果表达式；修改共享入参显示全部受影响字段。
- 删除独立转换选择器，同步检查保存、窄屏及导览。认证、签名、加密、JSON 编码和处理顺序继续由协议方案负责，不混入普通取值表达式。
- 定义合同增加 EXPRESSION、明确变量依赖和有限数据投影，规定精确数值、空值、目标类型及执行预算。表达式只调用已登记纯函数，业务数据查询保留显式 Step。官方 Aviator 能力与 sandbox 修复依据已链接，实际依赖与运行引擎尚未实现。
- A02 增加 expressionProfile 元数据，“检查表达式”复用 A13 的静态检查；管理端点仍为 13 项。同步需求、UI、逻辑稿及 NI 示例，两表/17字段/4索引和145行注册 SQL 草稿不变。

14张画板和5条导览保留，其他71个顶层节点保留。截图复核主要视图；逐节点布局无裁切、无重叠、无 placeholder；三个面板均只有规定的三种选项，旧转换/查询控件检索为空。7份相关 PNG 已更新，已通过 Pen 原生保存并定位03b，证据见 [expression-mapping-verification.json](./assets/pencil/expression-mapping-verification.json)。文档及结构检查通过：11份 Markdown、153个本地链接、19个锚点，16项验收映射、2段 YAML 及管理路由/SQL草稿结构均无失败，结果已并入上述证据；本轮未运行表达式引擎、React 表单、系统查询、Java/SQL或真实渠道调用。


## 15. 技术配置界面与结构化结果规则（2026-09-14）

用户指出结果配置的引导式设计不必要，要求更技术化。本轮在已授权的 Operations 设计范围内修订：新增功能页、管理接口、表及 SQL 均为0，修改原有画板与设计说明。

- 主编辑器改为接口定义、请求映射、协议处理、响应映射、校验五个无编号页签，自由切换；校验与整份保存进入统一工具栏。取消上一步/下一步向导，保留完整性校验与局部字段未应用时的保存约束。
- 结果映射采用序号、条件摘要、结果码、操作表格；展开后编辑左值、操作符、右值类型/来源和值。条件组合沿用 ALL/ANY/NOT，序号只是数组顺序，不增加 priority 存储字段。
- 默认结果固定 UNRECOGNIZED；输出按结果码分组，展示名称、类型、必填和取值路径。业务输出明确使用已校验投影，HTTP Header 保留多值结构。结构、身份和保护校验依旧是结果规则前置要求，未扩展结果表达式语言或成功判定。
- 移除问答式标题、常驻教学卡片和冗长说明，保留技术诊断、约束、函数可用性与 NI 本次退款关联缺口。上下文路径/Aviator/系统函数及 result 取值保持，变量和函数目录继续作为编辑工具。
- 原14张画板及5条索引导览保留；本轮开始时另有75个非本需求顶层节点，其内容摘要核对无变化。同步当前 UI/设计文档，保留之前记录的历史范围。

Pencil 主界面和局部截图复核通过，全部 CAPI 节点无裁切、无重叠、无 placeholder，旧引导文案检查为空。校验及保存证据见 [technical-editor-verification.json](./assets/pencil/technical-editor-verification.json)，结果规则局部预览见 [zrpvI.png](./assets/pencil/zrpvI.png)。本次为设计修改，不是 React 页面、表达式或真实渠道执行测试。

本次最终静态检查通过：11份 Markdown、155个本地链接、20个锚点、16项验收、2段 YAML 及13项管理路由/SQL草稿结构无失败。发现阶段2历史分析链接的 PaymentActionProcessor 源码已不存在，已保留历史核查事实并改为当前台账路由。Pen 原生保存完成，Edited 标记清除，定位05响应映射。

### 本节确认

用户审阅本版技术配置界面后回复“看起来没啥问题了。”本次记录为当前 UI 设计通过：包括无编号配置页签、统一校验/保存、上下文路径/Aviator/系统函数取值、结构化条件与结果/输出映射，以及移除问答式引导。

后续页面按本版设计推进。该反馈针对 UI，不扩大解释为所有技术/数据取舍已完成阶段确认；上下文、系统函数及 Aviator 纯函数的最小注册目录仍需细化，真实 NI 原交易定位和本次退款关联合同仍待补齐。本次只记录确认，不进入应用研发、SQL 执行或部署。

## 16. 最小注册目录与 NI 取值核对（2026-09-14）

用户对“下一步细化上下文字段、系统函数、Aviator 纯函数的最小注册目录，再用 NI 退款样例核对完整配置”回复“可以”。本次完成这一设计细化；新增管理操作、功能页面、表和 SQL 均为 0，既有 13 项管理操作、2 表/17 字段/4 索引、145 行 INSERT 草稿不变。

- 新增[最小注册目录](./registry-contract.md)及[机器可读描述符](./assets/registry-catalog.example.json)：PAYMENT 两个普通字段、REFUND 三个字段，可信身份与运行材料另行注入；两个只读系统函数按当前 Action 与冻结路由核验；首批纯函数仅 money.minorUnits。成员语义集中在新附件，query-contract 路由过去，避免并行维护两份合同。
- 金额沿用 Execution 的 DECIMAL(24,8)，ID 为精确字符串；函数参数、返回字段、缺失/null、身份检查和失败代码均明确。Q2 限 NI Capture，Q1 可供其他符合合同的渠道使用；注册描述符存在与真实实现可用分别表达。
- 补齐函数参数顺序 argumentOrder、系统函数 allowedChannelCodes 和上下文投影 capabilityCode；复用 A02 与既有 missingCapabilities/missingFunctions，不增加维护入口。编译器缺失不能声称 A13 valid=true，仅运行实现缺失且静态检查完整通过时才允许按警告保存。
- 新增[准确绑定片段](./assets/ni-refund-mapping.example.json)，含一个函数调用和八个字段绑定；四个 Path 参数共享 originalTarget，Body 币种取上下文，金额经 Aviator 精确处理，两个普通 Header 使用常量表达式。完整 NI 协议仍逐项在[样例](./ni-refund-example.md)核对；剩余 codec/保护/投影能力的注册参数合同未伪装成完整可导入 Wire。
- 同步旧 NI 逻辑样例和合成数据：移除“已有 refundTarget 则查询零次”的残留场景，可信身份不再混在 context，结果码与已确认 UI 统一为 UNRESOLVED / UNRECOGNIZED，第一方精确数值按字符串输出。已有公开协议材料继续沿用，本次没有新增 NI 产品事实或发送真实请求。

[目录/样例静态核查](./assets/registry-verification.json)通过：2 个 Context/5 个字段、2 个系统函数、1 个纯函数、8 个映射字段、1 次显式函数调用；8 个金额预期向量通过，8 个变异样例被拒绝（非法路径、未注册纯函数、参数反序、错误渠道、未知函数、前向引用、目标类型错误、重复参数顺序）。这不是 Aviator 编译/执行、真实 Owner 查询、完整 API Wire 校验或 NI 联调。

仍需真实接入的事项是 NI 原 Capture 导航事实提供方、本次请求与退款记录的唯一关联、获准支付方式/协议接纳边界及受控运行材料；通用执行能力也尚待实现。设计例子明确会在依赖预检返回 NOT_SENT，假定后续响应只用于核对 UNRESOLVED 输出，不宣称可完成真实退款。

本轮仅修改本需求目录的设计、示例及其静态检查脚本，未修改 Pencil 画板或 Java/React/SQL、其他任务文件、实现台账；未提交、推送或部署。

最终文档检查通过：12 份 Markdown、182 个本地链接、24 个锚点、16 项验收映射与 2 段 YAML；13 项管理操作、145 行 DML、2 表/17 字段/4 索引的草稿结构核对通过，未跟踪文件的结尾换行/空白检查及 git diff --check 通过。

## 17. 协议处理、完整定义与数据库收口（2026-09-14）

用户对补齐协议处理能力、顺序/复用及完整 NI 配置的建议回复“可以”，并询问数据库设计何时进行。本轮在同一个阶段 3 中完成上述设计及数据库承载核对；没有推迟到应用开发之后，也不把这次回复理解为建表或部署授权。

整体规模保持：4 个应用、13 项管理操作、Payment 新增 2 表/17 列/4 索引（含 2 主键）、旧表/旧列结构变更 0、Platform 145 行 INSERT、1 个功能页面。本轮没有追加表/列/索引/DML 或新页面。

- [协议处理合同](./capability-contract.md)：10 个 STEP 能力与 4 个上下文/表达式能力，明确 FIXED/SPECIALIZED 类型、字段映射参数、JSON 字节、受控响应句柄、投影、认证和密码 Profile 必填合同；真实算法 Profile 在 NI 无对应需求时保持空集合。
- [完整 API](./assets/ni-refund-api-definition.json)与[公共方案](./assets/ni-payment-bearer-scheme-definition.json)：4 个请求步骤、2 个响应步骤，7 处 Aviator 绑定、1 次系统函数调用；公共方案显式引用，固定参数不可覆盖。它们是完整 definition 内容，不含虚构的获准支付方式元数据。
- [数据库设计](./db/README.md#7-完整配置的落库核对)：补齐关系图、17 列与各段 JSON 的对应、共享引用、保存/读取/并发/删除场景和容量核查。示例紧凑 JSON 分别为 4,938/600 字节，原有两表满足本轮定义，DDL 结构未变化。
- 管理目录增加 protocolProfiles/runtimeInputs 的只读类型元数据，没有材料原值或新管理端点。结果条件以 op 明确判别；缺失响应 Header 返回 []，使非 200 响应可进入默认分支而不被成功媒体类型要求提前阻断。
- NI 文档以完整 JSON 替代旧逻辑 YAML，移除重复可编辑的 validateTarget 步骤，原目标校验归入函数固定合同，响应校验归入 Profile。上轮取值片段继续作为局部示例，不作为完整 API 的第二份权威。

[完整定义静态检查](./assets/complete-definition-verification.json)通过，15 个变异场景按预期拒绝：未知字段/方案/Profile、覆盖固定参数、前向引用、路径冲突、非字节 Body、伪造 Authorization 来源、扩张保护位置、阶段错误、非法条件/默认结果、遗漏结果分支、输出类型错误及超时越界。上轮目录、金额预期与错误配置检查继续通过。这些只证明设计工件在所列静态范围内一致，没有执行 Aviator、真实密码算法、Owner 查询、数据库 SQL 或 NI 请求。

完整设计仍需整体评审，尤其是允许非敏感相对路径、目标别名和 API 超时入 Payment DB 的窄修订。方案确认后再同步正式 PDManer/完整 Schema 并进入相应研发；真实 NI 导航事实、获准支付方式/材料/产品边界和本次退款唯一关联继续作为接入前置。本文不把这些外部缺口写为已解决。

本轮仅修改本需求目录内的设计和验证工件，保留其他任务工作区改动；Pencil、Java/React、正式 PDManer/Schema、原有 DDL/DML 和当前实现台账均未修改，未提交、推送或部署。

最终文档静态检查通过：13 份 Markdown、205 个本地链接、26 个锚点、16 项验收映射和既有路由/SQL 草稿结构均无失败；旧 YAML 已由完整 JSON 取代。新文件格式、JSON 解析与 git diff --check 通过。

## 18. 模型脚本与字段映射整理（2026-09-14）

### 用户范围

用户担心一个 JSON 承载签名、加密脚本过大，明确“脚本都只是处理一些简单的逻辑，比如组装、转换、判断等等，但是复杂的都是代码写函数来实现”。后续要求解释 JSON、请求与响应字段映射和领域对象，最后表示“目前的这个设计我觉得也还行把。先整理成文档”。本轮据此保留设计方向并整理文档，不把该回复扩大为完整脚本运行合同已批准或进入应用研发。

### 本轮整理

- 新增[模型、脚本与字段映射设计](./model-and-mapping-design.md)：作为阅读入口，集中解释三个独立配置对象、聚合内字段/取值/步骤结构、代码能力与运行对象。
- 明确普通请求 Body 优先使用 OBJECT_MAPPING，独立脚本是需要复用简单处理时的可选步骤；响应的保护/解析在 responseSteps，分类在 resultRules，调用方输出映射在 outputDefinition。
- 补充 SCRIPT、scriptCode、显式输入及统一返回值包装；源码独立文本保存，复杂函数保持代码注册。保留现有类型、数据分类、同 Channel 引用、事务外执行及单次发送边界。
- 数据说明调整为 API、公共方案、独立脚本三表方向，列出脚本逻辑字段及复用、并发、删除、当次完整加载关系。原两表 DDL 的语句不变，仅加覆盖范围注释；脚本物理字段/索引和完整 SQL 仍待补齐。
- 同步需求、设计、定义/协议/注册/调用合同、管理接口和页面说明的当前范围；标清脚本 CRUD、A02/A13 增量和 Pencil 脚本维护状态尚未完成，旧稿统计不代表当前完整交付量。
- 新增[JSON 阅读样例](./assets/api-mapping-and-script.example.json)，展示普通请求映射、同位置脚本替代、方案引用、响应输出和预期数据。样例显式标注非完整定义、非真实目录、未运行；不将虚构 DEMO Profile 当作已注册能力。

### 验证与边界

本轮只做文档/JSON 静态核对：文件格式、链接与锚点、示例中的前序引用/脚本替代输出/请求体来源/响应输出路径，以及原 DDL 与注册草稿覆盖范围。结果记录见[文档整理检查](./assets/model-documentation-verification.json)。不执行 Aviator、代码函数、密码算法、HTTP 或数据库；既有 NI/注册检查不作为新增 SCRIPT 验证。

修改范围为本需求文档、阅读样例、DDL 的一行范围注释和需求索引中的本条入口。未改 Java/React、Pencil、正式 PDManer/完整 Schema 或 SQL 语句，未提交、推送或部署。

## 19. 通用执行边界修订（2026-09-14）

### 用户范围

用户逐项要求：支持支付以外的地图/邮件等接入；移除支付方式和不必要的 invocationIdentity；业务自行幂等；API 执行过程不记录数据或处理重试；返回 executeStatus 的 SUCCESS/FAIL/ERROR，删除 sendState、failureStage、diagnosticCode、protocolEvidence、resultCode；outputs 自定义，不同渠道同类业务输出保持一致。本轮据此修改设计，未解释为直接实施新的工程落点或删除原 Payment 业务事实。

### 本轮设计

- 新增[通用执行修订](./generic-execution-amendment.md)，拥有新的分组、输入、执行、状态与输出边界；建议 Integration 命名，接口只需 integrationCode、apiCode 和 API 声明的 context。Payment 方法选择及冻结路由留在业务适配层。
- 删除固定 InvocationIdentity、必填 channelIdempotencyKey 及公开 prepare/execute 拆分。第三方协议确实要求的请求键由业务方作为普通映射输入；受控连接解析不允许任意 context 绕过业务权限。
- 配置管理可以落库，单次运行仅加载一致定义、执行处理并返回，不建执行流水、状态、幂等或重试记录，不写原业务表，也不隐式重试目标请求。既有业务 Owner 的持久化和恢复责任不变。
- 用 statusMapping 的首个匹配规则得到 SUCCESS/FAIL/ERROR，再映射并校验 outputs；ERROR 默认返回空对象。ERROR 不证明外部未执行，不能让调用者据此自动重试或释放资金预占。
- outputs 按业务 Owner 共同合同约束，跨渠道同类业务保持名称、类型、单位和语义一致；API 各自映射来源，可选字段承载小范围差异。共享合同载体尚待详细设计，不机械新增一张表。
- 更新需求及本条索引，给前版模型、调用/配置/管理/注册/协议合同、页面、数据库和 NI 样例添加后继边界。SQL 仅加范围注释，不改变语句。通用 Owner、分组/唯一键、完整管理 Wire、Pencil 和物理模型尚未同步；不继续声明完整设计只有三张表。
- 新增[通用阅读 JSON](./assets/generic-execution.example.json)，展示输入路径、共同输出合同引用、状态/输出映射以及成功、已识别失败、未匹配、超时四类合成结果。它不是完整可导入定义，不是实际第三方协议或 Payment Wire。

### 验证与边界

本轮验证只覆盖文档链接/锚点、JSON 与示例字段形状、合成状态和输出预期以及文件格式，记录见[通用设计静态检查](./assets/generic-execution-verification.json)。没有运行 Aviator、脚本、密码函数、真实查询、网络或 SQL；旧报告保留其原有范围，不作为新版执行验证。

修改只涉及本需求文档/阅读样例/静态检查、旧 SQL 范围注释和需求索引本条。未改 Java/React、Pencil、正式 PDManer/完整 Schema、现有业务持久化、当前实现台账或其他任务文件，未提交、推送或部署。

## 20. 公共模型层级对象与响应场景（2026-09-15）

### 用户范围

用户询问 refundContext/paymentContext 等公共对象和不同响应场景的 outputs 在哪里维护。助手指出前版缺少公共维护入口，提出数据模型页、响应场景及 API 引用设计；用户回复“可以，对象可以有层级，不是完全铺开的，帮我做一下设计”。本轮按已授权的设计范围推进，细化对象层级并修改原 Operations Pencil 文件，不进入应用研发、数据库设计落库或部署。

### 本轮设计工件

本次新增 1 个数据模型功能页、调整 1 个现有 API 配置页，交付 8 张画板与 1 张索引；新增已冻结 HTTP 端点、物理表/列/索引及 SQL/DML 语句均为 0。画板数量不等于功能页面数量，完整通用管理与物理方案仍待确定。

- 新增[模型与响应场景设计](./model-library-and-response-scenes.md)：对象模型维护真实父子字段，支持 OBJECT、ARRAY 与无循环模型引用；上下文名称与模型身份分别定义。
- 数据模型页包含对象模型与响应合同两个页签；输入/输出结构跨接入分组复用。代码函数及其真实返回合同继续由代码注册，模型维护不提供函数实现或真实数据。
- 响应按 sceneCode 配置，共同场景约定 executeStatus 和输出模型；同为 SUCCESS 的完成/需要交互可返回不同嵌套结构。API 只配置匹配条件和字段来源，返回不新增 sceneCode/resultCode。
- 在 [Operations 原设计文件](../../../operations-console-web/docs/design.pen)新增 M01 目录、M02 嵌套树、M03 数组、M04 响应合同、M05 输入绑定、M06 响应映射、M07 引用影响、M08 内嵌字段与 M00 索引。旧 CAPI 画板保留，其五条导览标为前版参考。
- 树形输入示例采用 originalTransaction.referenceId、amount.value/currency、items[] 引用 RefundItem；明确是新结构示意，不假称旧平铺调用方已自动兼容。对象属性与数组元素可空性、同级/子字段添加、引用跳转和破坏性修改定位均有具体界面。
- 同步需求、通用执行、配置字段/注册/管理和 UI 入口，明确替代范围；需求索引只更新本条，未改其他任务内容。

### 验证与交付边界

原生 Pencil 已保存，Edited 标记清除并定位 M02。8 张画板及索引均已截图/导出，字段树和嵌套输出还做了完整分辨率预览检查。结构检查包括节点边界、根画板重叠、placeholder、旧结果术语及四段 JSON 示例解析；本轮开始时的 76 个非 CAPI 顶层节点摘要核对一致，未修改其他需求的画板。证据与文档链接检查见[静态验证记录](./assets/model-library-design-verification.json)。

这次交付的是可编辑视觉稿与设计说明，不是已运行的表单、模型管理接口、表达式、校验器或渠道执行器。Java/React、正式 PDManer/Schema、SQL、业务数据、当前实现台账均未修改，未提交、推送或部署。完整通用 Owner/Wire/物理模型、脚本维护和实际工程实现仍需后续收敛。

## 21. 公共脚本与 HTTP 工作对象页面（2026-09-15）

用户提出公共方案直接使用脚本，并访问组装后的请求头/体及响应头/体；在方案讨论后回复“可以，页面改一下”。本轮按页面设计授权执行，未进入应用研发。

本次调整公共脚本维护和 API 引用两个设计区域：替换原 3 张画板，新增 2 张画板，更新索引及 M01～M08 导航；没有新增已冻结的 HTTP 端点、物理表/字段/索引或 SQL。画板数不是新增功能页面数。

- 原公共方案列表、编辑和协议处理分别替换为 S01 脚本目录、S02 请求脚本编辑和 S04 有序脚本引用，保留节点及位置；新增 S03 响应脚本和 S05 参数绑定状态。
- 公共脚本维护源码、参数结构和 REQUEST/RESPONSE 阶段；对象目录说明 http 的可读写范围，函数目录展示参数和调用插入。API 维护自己的参数绑定和执行顺序。
- 响应脚本示例包含原文验签、Body 整体替换与 Header 修改。M06 输出取值同步为处理后的 http.responseBody，公共模型和响应场景合同保留。
- 新增[公共脚本设计](./public-script-http-context-design.md)，明确替代旧方案/独立脚本分离和公共脚本只读规则；字段表达式仍只读。同步当前需求、通用调用、UI 和旧合同入口，旧 JSON/SQL 保留前版范围。

原生 Pencil 已保存，Edited 标记清除，并选中 S02 请求脚本编辑。S01～S05 及索引导出至 assets/pencil-scripts，M01～M08/M00 预览同步更新。静态结构检查未发现溢出、相关画板重叠、残留 placeholder 或当前脚本页的旧方案/结果字段；本轮开始时 76 个无关根画板摘要均未变化。源码和参数面板已按完整分辨率核对；检查记录见[公共脚本验证](./assets/public-script-design-verification.json)。

源码及函数签名为明确标注的交互示意，未运行 Aviator、代码函数、HTTP、密码操作或 SQL。完整脚本管理 Wire、物理设计、运行时预算和实现适配仍待后续设计，未修改应用代码或声明功能已实现。


## 22. Platform 配置管理与 HTTP 执行（2026-09-21）

用户明确选择：通用 API 配置及执行放在 Platform，Payment 通过 Platform 暴露的 HTTP 接口完成渠道调用。该选择取代“Platform 只管理配置、Payment 引入公共 JAR 执行”的建议。

决定见[Owner 决定](../../../agent-docs/decisions/2026-09-21-platform-integration-http-execution.md)，调用、存储、函数及材料边界见[HTTP 执行修订](./platform-http-execution-amendment.md)。Payment 仍拥有账号/MID/能力、冻结选择、交易状态和业务恢复；配置五表按 Platform Owner 继续逐表评审。

本轮同步架构与需求路由，不把 Owner 确认等同完整字段、HTTP Wire、凭据交接或实现已批准。具体交付与验证状态只见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-integration-http-execution-20260921)。

## 23. 迁移至 Platform 与现行设计整理（2026-10-13）

- **来源：** [渠道 API 配置资料迁移需求](../../20261013/platform-support-渠道API配置资料迁移/requirement.md)，用户确认分析结论并要求执行；执行记录见其[交付记录](../../20261013/platform-support-渠道API配置资料迁移/delivery.md)。
- **用户决定：** 对内提供标准 API，渠道可以进行接口编排；冲正属于危险操作，不在流程中编排。记录于[2026-10-13 决定](../../../agent-docs/decisions/2026-10-13-platform-standard-api-flow.md)。
- **目录：** 原 `requirements/20260914/payment-渠道API逻辑定义/` 81 个文件整体迁入本目录，原目录仅保留迁移说明；原 `requirement.md`、`technique.md` 改名为 `legacy-requirement.md`、`legacy-technique.md`，新增现行 `requirement.md`、`technique.md`、`data-design.md`、`flow-design.md`。
- **历史资料：** 历史 Markdown 只在标题后增加后继说明；SQL、JSON、PNG、Python 内容未改。`registry-contract.md` 的检查命令路径更新为新目录。
- **范围：** 未修改应用代码、PDManer、SQL、Pencil、环境或权限；未提交、推送或部署。

## 24. flow_definition_json 结构评审稿（2026-10-13）

- **请求：** 迁移交付后用户回复“可以”，按交付末尾的建议开始细化 `flow_definition_json`。该回复不包含提交授权，本轮未提交。
- **交付：** [结构评审稿](./flow-definition-contract.md)，覆盖顶层结构、三种取值绑定、初始化、节点/返回出口/连线、运行时缺失处理、静态校验、预算与合成示例。
- **待用户确认：** 评审稿第 9 节 Q1～Q6（无环、请求值在节点配置、节点只用纯函数、资金反向操作标记、条件异常返回 ERROR、预算数值）。确认前不作为实现依据。
- **范围：** 只改文档；没有代码、模型、SQL、环境变更，没有提交、推送或部署。
- **用户确认（2026-10-13）：** Q1 流程暂时不能有环；Q2 按建议在节点配置请求值；Q4 冲正防护采用人工评审，不加标记列；Q5 按建议返回 `ERROR`；Q6 预算上限可以。Q3 用户询问读取外部数据的难度，已答复复杂度与“显式函数节点”替代方案，待用户决定。
- **Q3 确认（2026-10-13）：** 用户回复“按照建议以及进行下一步”，节点内不读取外部数据，外部数据只在初始化读取。流程结构 Q1～Q6 全部确认。

## 25. 原子 API definition_json 结构评审稿（2026-10-13）

- **请求：** 同一回复要求进入下一步。
- **交付：** [结构评审稿](./integration-api-definition-contract.md)，覆盖职责划分、调用参数、协议固定值、HTTP 传输与编解码、脚本引用、执行顺序、状态规则、响应字段校验、输出映射与合成示例。
- **待用户确认：** 第 9 节 A1～A7。其中 A3 会给已确认的流程节点结构增加 `params` 绑定，确认后同步修订流程结构稿。
- **范围：** 只改文档；没有代码、模型、SQL、环境变更，没有提交、推送或部署。
- **用户确认（2026-10-13）：** 回复“按建议”，A1～A7 全部确认。同步修订流程结构稿第 4.1、6 节：节点增加 `params` 绑定，静态校验增加固定值冲突与必填参数覆盖。

## 26. 标准 API 内部调用接口评审稿（2026-10-13）

- **请求：** 用户回复“继续”，按建议合并待决第 3、4 项。
- **交付：** [内部接口评审稿](./internal-api.md)，覆盖路径与 DTO、请求字段、200 与 Problem 的区分、结果未知情形、弱信任调用授权、两段超时和日志边界。
- **发现的冲突：** MVC 公共日志组件按既有用户决定完整记录 Body，与标准 API 输入输出中可能出现的临时敏感值冲突，列为 W5 由用户决定。
- **待用户确认：** W1～W6。其中 W2 会修改已确认流程结构第 3 节的必需初始化失败返回方式。
- **范围：** 只改文档；没有代码、模型、SQL、环境变更，没有提交、推送或部署。
- **W5 用户决定（2026-10-13）：** “敏感日志暂时不考虑，后续在统一处理。”本接口沿用现行完整 Body 日志，冲突记为已知遗留风险；W1～W4、W6 尚未确认。
- **用户确认（2026-10-13）：** 回复“按建议”，W1～W4、W6 确认。同步修订流程结构第 3、5、7 节与流程设计第 2 节：第一个节点开始前的失败（输入校验、必需初始化、预算耗尽）返回 Problem，保证未发出渠道请求。

## 27. 渠道账号材料交接评审稿（2026-10-13）

- **请求：** 用户回复“继续”，按建议进入待决第 5 项。
- **交付：** [交接评审稿](./material-handoff.md)，比较三种交接方案，建议 Payment 只传 Vault 引用 Token 与账号身份、Platform 直接向 Vault Reveal；定义初始化函数、凭据句柄使用边界与 Payment 改造需求。
- **待用户确认：** M1～M5。M1 会扩大 Vault 授权（新增 Platform 的 Reveal 权限），确认后需补决定记录。
- **范围：** 只改文档；没有修改 Vault 策略、Payment 或 Platform 源码，没有提交、推送或部署。
- **用户确认（2026-10-13）：** M1 按方案 C，其余按建议。新增[授权决定](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)与本目录策略片段；同步 AGENTS.md、APPLICATION_ARCHITECTURE.md、VAULT_SERVICE_ARCHITECTURE.md、09-21 两份决定、Payment 实施合同第 4 节与当前台账。Vault 策略未部署。

## 28. 六表物理设计评审稿（2026-10-13）

- **请求：** 用户回复“继续”，按建议进入待决第 7 项。
- **交付：** [物理设计评审稿](./physical-design.md)，按数据库规范与 Platform 现有写法给出六表列、类型、唯一/普通索引、容量上限和运行时读取一致性。
- **待用户确认：** P1～P7（表名、状态列、并发与审计列、白名单存放、基础地址放应用配置、字段约束列、容量上限）。
- **范围：** 只改需求文档；没有修改 Platform PDManer、Schema SQL 或初始化数据，没有执行 SQL、提交、推送或部署。
- **用户确认（2026-10-13）：** P4 调用方白名单暂时不做；P5 基础地址维护到 Platform 数据库；其余按建议。同步修改：内部接口第 4 节取消 `X-Caller-Service` 与 403（取代原 W3），物理设计删除白名单列并在 `t_integration` 增加 `endpoints` JSON 列，原子 API 结构 A6 写明存放位置。PDManer/SQL 未修改。

## 29. 六表数据库设计落地（2026-10-13）

- **请求：** 用户回复“先做数据库设计”。按数据库规范理解为：PDManer 模型、Schema DDL 与需求增量 SQL 同步；不执行 SQL，不写 Java。
- **完成：** 新增 `scripts/pdmaner/platform-support/manifests/09-integration.mjs` 并注册 `integration` 图组；重新生成 `platform-support.pdma.json`（68 表，已有 62 表、图组与类型映射逐项未变）；更新模型测试期望值与 [Platform 现实场景](../../../agent-docs/database/scenarios/PLATFORM_SUPPORT_REAL_WORLD_SCENARIOS.md)第 9 节；新增 [增量建表 SQL](./db/platform-support-service/integration-create.sql)。
- **验证：** 生成器 `--check` 通过；Platform 模型/Schema/场景/路径/文件上传 Node 测试 55/55 通过；增量 SQL 与模型 6 表 64 列 7 索引逐项一致，不含删除、修改、外键或 CHECK。
- **未完成：** 向完整 Schema `platform-support-service/docs/database/sql/platform-support-schema.sql` 追加六表时被权限检查以“修改共享资源”拦截，文件未改动，需要用户处理（已在下方补充中完成）。
- **范围：** 没有执行 SQL、修改测试 DDL、DataObject/Mapper/Repository，没有提交、推送或部署。
- **补充（2026-10-13）：** 用户确认“可以改，PDManer 的文件也可以改，但是注意别的 chat 冲突”。修改前通知其他三个本机会话，并确认 Schema 文件自 2026-09-21 后无改动；随后向 `platform-support-schema.sql` 追加六表并把文件头更新为 46 张已批准表，`platform-support-schema.test.mjs` 增加六表期望和“增量 SQL 与完整 Schema 一致”测试。Platform 相关 Node 测试 56/56 通过，生成器 `--check` 通过。仍未执行 SQL。

## 30. Operations 页面方案评审稿（2026-10-13）

- **请求：** 用户回复“设计页面吧”。
- **交付：** [页面方案评审稿](./page-design.md)：菜单与路由、两个列表、标准 API 编辑、接入方工作区四个页签、原子 API 七个页签、脚本编辑、流程画布与属性面板、取值/条件编辑器、通用状态与权限，附文字线框图。流程编辑复用 Risk 决策流的画布交互。
- **待用户确认：** U1～U7；确认后再在 Operations design.pen 绘制画板（绘制前与其他会话确认该文件无人同时编辑）。
- **范围：** 只写需求文档；没有修改 design.pen、前端、BFF、权限注册或 Gateway Route，没有提交。
- **第一版画板（2026-10-13）：** 用户要求“先出一版本”。用户在 Pencil 中打开 design.pen 后，先通知其他三个本机会话，再新增 9 张画板（名称以“外部 API 第一版”开头，未改已有画板），导出 PNG 到 `assets/pencil-v2/`，索引见[页面方案第 11 节](./page-design.md#11-第一版画板2026-10-13)。U1～U7 仍待确认。
- **删除旧画板（2026-10-13）：** 用户确认“如果没用就删除”。经核对，30 张 CAPI / GAPI 前序画板对应已被取代的单页、支付方式层级、数据模型与响应场景结构，仓库内只有本目录历史文档引用其 ID，新画板也没有引用它们；已从 design.pen 删除。历史 PNG 保留，原稿可从 git `fff35d2` 恢复。
- **补齐画板（2026-10-13）：** 用户要求“继续把图补全”。新增 10～24 共 15 张：原子 API 剩余四个页签、接入方工作区三个列表页签（含新建实现弹窗）、删除保护、并发冲突、空状态、加载失败、只读详情、未保存离开，以及流程编辑的返回出口与初始化项属性面板；PNG 导出到 `assets/pencil-v2/`。仍未修改任何已有画板。
- **用户确认（2026-10-13）：** 回复“按建议”，页面方案 U1～U7 全部确认。截至确认时 Pencil 尚未保存，design.pen 磁盘文件仍为 2026-09-15 版本。

## 31. 管理接口与注册数据评审稿（2026-10-13）

- **请求：** 用户选择“设计管理接口和注册数据”。
- **交付：** [管理接口评审稿](./admin-api.md)（30 个 Platform 端点，BFF 一一对应）、[机器清单](./admin-api-inventory.json)、[注册 SQL](./db/platform-support-service/registration-dml.sql)（344 行 INSERT，路由与菜单 DISABLED、角色授权 REVOKED）。写法沿用 20260913 菜单权限与 20260914 租户访问需求；请求幂等按 2026-09-17 决定由 Gateway 处理，`idempotency-key` 只允许 CORS、不转发给 BFF。
- **检查：** 生成脚本逐行核对列数；344 个 ID 唯一且与仓库内全部 SQL 不冲突；路由编码、外部路径、权限编码、菜单编码均无冲突；机器清单 JSON 有效。
- **待用户确认：** R1～R7。R1 建议把菜单改为“外部 API”一级分组下挂两个菜单，与第一版画板一致，确认后同步修正页面方案第 2 节。
- **范围：** 只写需求文档与待执行 SQL；没有执行 SQL、修改代码、`console-resource-capabilities.json` 或初始化数据，没有提交。
- **用户确认（2026-10-13）：** 回复“R1-R7我看了，按照你的来吧”，管理接口与注册数据全部确认；按 R1 修订页面方案第 2 节菜单为“外部 API”一级分组。设计阶段完成，待决第 6 项（路径转义、根数组、多值 Header、条件必填）留到研发时按真实渠道细化。

## 32. 研发计划 · 第 1 步评审稿（2026-10-13）

- **请求：** 用户回复“出计划”。
- **交付：** [研发计划](./dev-plan-step1.md)：新模块与包、数据访问、写/查/校验接口流程、静态校验器、测试设计、实施顺序与风险。动笔前已读开发约定、Java 工程规范第 4 节、公共写接口示例与测试规范，并以 Platform API Scope 管理为代码样板。
- **待用户确认：** D1～D6，其中 D5（本地建表）与 D6（改代码）是动作授权。
- **范围：** 只写计划；没有修改代码或执行 SQL，没有提交。
- **用户确认（2026-10-13）：** 回复“可以”，D1～D6 按建议，授权本地 `mor_platform` 执行 integration-create.sql 与修改 Platform 代码；开始实施 1a。

## 33. 研发计划 · 第 2 步评审稿（2026-10-13）

- **请求：** 第 1 步（含测试）提交为 `bcd38a5` 后，用户回复“可以”，进入第 2 步计划。
- **交付：** [研发计划 · 第 2 步](./dev-plan-step2.md)：BFF 6 组转发、Platform 组件能力登记、前端两个菜单与全部页面、注册数据本机执行与端到端验收、测试与 2a～2e 实施顺序。
- **待用户确认：** F1～F7，其中 F3（本机建库与执行注册 SQL）与 F6（修改 BFF、前端与 Platform 能力登记）是动作授权。
- **范围：** 只写计划；没有修改代码或执行 SQL，没有提交。
- **用户确认（2026-10-13）：** 回复“可以”，F1～F7 按建议；开始实施 2a。
- **2a 实施中的调整（2026-10-13）：**
  - **F2 细化：** Foundation HTTP 客户端按设计只保留上游 422 的字段路径、不保留说明文字，BFF 因此只投影路径（以请求体顶层属性开头），说明统一为安全文案；具体问题由 E16/E22/E28 校验接口的 200 结果提供，与页面方案“校验与保存都展示问题列表”一致。
  - **路由编码改名：** [registration-dml.sql](./db/platform-support-service/registration-dml.sql) 与[机器清单](./admin-api-inventory.json)中 30 条路由编码由 `external-api-*` 改为 `operations-external-api-*`，与现有全部 Operations 路由编码前缀一致（SQL 尚未执行，无数据影响）。
  - **BFF 既有编译问题：** risk-api 于 2026-10-13 重构（响应类型改为独立 class、访问方法改为 getter），BFF 的 Risk 变量适配随之无法编译。经用户同意，只把 `RiskVariableConverter`、`RiskVariableClientImpl`、`RiskVariableResponsePolicy`、`RiskDecisionConfigurationClientImpl`、`RiskVariableResponseShape` 适配到新类型（包路径与 getter，字段集合改由实例字段反射），不改行为；BFF 测试模块中 5 个 Risk 测试文件（174 处编译错误）按用户选择本次未迁移。
- **2b（2026-10-13）：** 用户回复“继续”。完成标准 API 列表与编辑、接入方列表、新建与工作区（基本信息、原子 API/公共脚本/标准 API 实现列表）、导航与能力登记。标准 API 没有独立校验接口且 BFF 422 只保留出错位置，字段表增加与服务端同口径的即时提示（位置、路径格式、标量类型、重复、父字段），最终以保存结果为准。前端测试 947/947、构建通过；浏览器走查待 2e。
- **2c（2026-10-13）：** 用户回复“继续”。完成原子 API 编辑（七页签）与公共脚本编辑，校验、保存、问题定位与冲突处理；422 只带位置时自动调用校验接口展示具体问题。前端测试 957/957、构建通过。
- **2d（2026-10-13）：** 用户回复“继续”。完成流程编辑：初始化项侧栏、画布、节点/出口/初始化项属性面板、出口顺序、取值与条件编辑、必经变量提示、校验定位；画布位置每次按图结构自动布局（F4）。前端测试 967/967、构建通过。
- **2e（2026-10-13）：** 用户回复“确认开始”。在本机独立库 `mor_platform_external_api` 执行建库、完整 Schema、初始化目录、注册 SQL（344 行）与启用 SQL；经真实入口 → Gateway → BFF → Platform 完成 30 项接口检查与 22 个页面的无头浏览器走查（控制台错误 0），见 [回执](./assets/stage2e/e2e-receipt.json)。发现注册数据中 18 条路由请求体上限 262,144 超出 Gateway 绝对上限 65,536（会使整份快照失败），已在注册 SQL、[管理接口](./admin-api.md)第 6 节与 BFF 统一改为 65,536；整个请求体受 64 KiB 约束，单个定义实际可用空间略小于 64 KiB。

## 34. 研发计划 · 第 3 步评审稿（2026-10-13）

- **请求：** 第 2 步提交为 `927e248` 后，用户回复“可以”，进入第 3 步计划。
- **交付：** [研发计划 · 第 3 步](./dev-plan-step3.md)：执行接口与调用链、模块与包、取值/组装/脚本/传输/出口规则、Aviator 引入与白名单剖面、首批注册函数、测试与 3a～3e 实施顺序。
- **待用户确认：** G1～G8，其中 G1（引入 Aviator 依赖）与 G8（修改 Platform 代码与技术栈文档）是动作授权。
- **范围：** 只写计划；没有修改代码、Vault 策略或执行 SQL，没有提交。本机第 2 步验证服务仍在运行。
- **用户确认（2026-10-13）：** 回复“可以”，G1～G8 按建议；开始实施 3a。

## 35. 第 3 步 · 3a Aviator 白名单与保存时编译（2026-10-13）

- **依赖：** Platform 根 POM 固定 `com.googlecode.aviator:aviator:5.3.3`（无运行时传递依赖），只由 `platform-support-integration` 引入；[技术栈](../../../agent-docs/TECHNOLOGY_STACK.md)第 3 节登记版本与使用边界。
- **引擎：** `ExpressionEngine` 为只读、脚本两个剖面各建一个解释执行实例；不开放任何 Java 类；浮点字面量按十进制解析；编译结果 LRU 缓存 1024 条。编译前静态检查函数调用只能在剖面白名单内、禁止 `=~`；实例中移除白名单外的具名函数（保留 Aviator 编译 for 语句所需的内部 `with_meta`），防止以函数值间接调用绕过；白名单内置函数缺失时启动即失败。脚本函数（`bearerAuth` 等）已列入白名单，实现在 3c 注册。
- **保存时校验：** `ValueBindingValidator` 的表达式（EXPRESSION、FUNCTION result、出边条件、状态规则条件）按只读剖面编译并拒绝上下文根以外的变量；`IntegrationScriptValidator` 按脚本剖面编译源码。问题码 `EXPRESSION_INVALID`，位置指向具体表达式或 `sourceCode`。
- **偏差：** 5.3.3 无求值超时，见[第 3 步计划](./dev-plan-step3.md)第 7 节 3a 结果。
- **测试：** 新增 `auto/domain/ExpressionEngineTest`（7 个：只读拒绝清单、只读求值与十进制、脚本剖面、循环上限、函数值绕过、白名单函数、保存时问题码）；测试样例脚本由未开放的 `functions.sign(...)` 改为 `hmacSha256Hex(params.key, http.requestBody.orderRef)`。Platform `./mvnw -o install`：314/314 通过。
- **已知：** `platform-support-test` 的 JaCoCo “Phase A runtime” 门禁（不含 integration 模块）在 `verify` 阶段报覆盖率 0.40/0.25 不达标，与本步无关，属既有问题。第 2 步 2e 端到端脚本里的样例源码 `functions.sign(...)` 在 3a 之后无法保存，3e 重建端到端时一并替换。
- **范围：** 未修改 BFF、前端、Vault 策略，未执行 SQL，未提交。

## 36. 第 3 步 · 3b 执行引擎核心（2026-10-13）

- **请求：** 用户对 3a 回复“可以”，进入 3b。
- **快照加载：** `ExecutionSnapshotLoader` 用一次 REPEATABLE_READ 只读事务（5 秒）读取标准 API、接入方、实现流程、流程引用的原子 API 及其字段与脚本；事务外用保存时的同一套校验器（字段、出站地址、脚本、原子 API 定义、流程）重新校验，未通过记录问题代码与位置并返回 `INTEGRATION_CONFIG_INVALID`；标准 API 或实现不存在返回对应 404 代码。HTTP 状态映射在 3d 接入。
- **取值与结构：** `FieldSchema` 校验并转换标准 API 输入、校验组装出的输出；`ValueResolver` 实现 PATH/EXPRESSION/FUNCTION 的缺失、null、类型规则；`MONEY_MINOR_UNITS` 无损换算。规则细节见[第 3 步计划](./dev-plan-step3.md)第 7 节 3b 结果。
- **流程引擎：** `FlowEngine` 从入口逐个执行节点（节点异常记为该节点 ERROR），按优先级判断出边（非布尔或异常整次 ERROR），预算耗尽或节点重复整次 ERROR，到出口后按输出结构映射（失败改为 ERROR、输出空对象）。日志只记编码、节点/出口 ID、状态与耗时。
- **合同修正：** Aviator 5.3.3 不支持 `a.b[0].c`，[原子 API 结构](./integration-api-definition-contract.md)第 8、10 节示例改为 `#http.responseBody.errors[0].code` 并加说明。
- **测试：** 新增 `auto/domain/ExecutionValueTest`（4 个：输入结构、PATH 缺失与 null、表达式类型、金额换算）与 `FlowEngineTest`（4 个：出边优先级、非布尔条件、出口映射、出口回退 ERROR）。Platform `./mvnw -o install`：322/322 通过；JaCoCo Phase A 门禁同 3a 记录，属既有问题。
- **范围：** 未接入执行接口与出站 HTTP，未修改 BFF、前端或 Vault 策略，未执行 SQL，未提交。

## 37. 第 3 步 · 3c-1 执行入口提前（2026-10-13）

- **请求：** 用户指出 3b 的 `ExecutionSnapshotLoader` 没有调用方；同意把执行入口提前（“可以，按照你的计划来吧”）。[第 3 步计划](./dev-plan-step3.md)第 7 节改为 3c-1 / 3c-2 / 3d。
- **实现：** `StandardApiCommandApi`（`POST /standard-apis/v1/{standardApiCode}/execute`）、`ExecuteStandardApiRequestBody` / `ExecuteStandardApiResponseBody`、`StandardApiExecutionController`（只做转换）、`ExecuteStandardApiProcessor`（加载快照 → 运行前校验 → 校验 input），`PlatformApiErrorConfiguration` 映射 404/500/503。
- **差异：** input 字段错误返回 422（[内部接口](./internal-api.md)第 3.2 节已加实现说明）；5xx 代码改用 `ErrorException` 抛出（Foundation 只把 `FailException` 映射为 4xx，首次运行测试时发现 500 被映射成 400）。
- **临时状态：** input 校验通过后暂时返回 500“尚未接入”，代码中有 `TODO by CLAUDE`，3c-2 删除。
- **测试：** 新增 `auto/ext/http/standardapi/StandardApiExecutionTest`（5 个：标准 API 不存在、实现不存在、流程或原子 API 被写坏、input 字段错误、请求体缺字段）。Platform `./mvnw -o install`：327/327 通过；JaCoCo Phase A 门禁同前，属既有问题。
- **范围：** 未修改 BFF、前端、Vault 策略，未执行 SQL，未提交。

## 38. 第 3 步 · 3c-2 初始化、出站 HTTP 与脚本（2026-10-13）

- **请求：** 用户回复“可以，继续 3c-2”。
- **实现：** 见[第 3 步计划](./dev-plan-step3.md)第 7 节 3c-2 结果。标准 API 现在可以完整执行：Vault 读取渠道账号 → 节点组装请求 → REQUEST 脚本 → 发送 → 解码 → RESPONSE 脚本 → 状态规则 → 输出 → 出边 → 出口。
- **测试设施：** `ChannelMocker` + `ChannelMockController`（独立 Mock HTTP 服务上的 `/channel/**`，按“方法 + 路径”返回配置的响应、记录请求；错误状态经 `MockHttpFault` 写出）；`ChannelAccountVaultMocker`（`VaultMockController` 新增 `CHANNEL_EXECUTION` 用途）；两者的意外调用检查加入 `MockTestBase` 全局 AfterEach；`ExternalApiMocker.integration(code, baseUrl)`；测试配置 `allowed-schemes: [https, http]`。
- **新增测试：** `StandardApiFlowExecutionTest`（5 个）：成功（核对路径段编码 `outlet%201`、固定值 Content-Type、脚本用受控句柄算出的 HMAC 签名与请求体）、拒绝、节点 ERROR（渠道 500 无规则命中、成功响应缺必填字段、连接断开）、响应超时、初始化失败（Vault 503、账号身份不一致，均 503 且渠道零请求）。日志检查：测试输出中未出现合成密钥。
- **既有测试调整：** 能力目录用例纳入脚本函数；出站地址非法协议用例由 `http://` 改为 `ftp://`（mock 配置已允许 http）。
- **验证：** Platform `./mvnw -o install`：332/332 通过；JaCoCo Phase A 门禁同前，属既有问题。
- **未覆盖（留给 3d）：** FORM/NONE 编码、QUERY、RESPONSE 脚本与 `json.*`、`basicAuth`/`bearerAuth`、执行前预算耗尽 503、非必需初始化项失败。
- **范围：** 未修改 BFF、前端（能力目录新增 `SCRIPT` 类别的函数，脚本编辑器会列出它们，未做浏览器验证），未修改共享或线上 Vault 策略，未执行 SQL，未提交。

## 39. 第 3 步 · 3d 边界场景与收尾（2026-10-13）

- **请求：** 用户回复“继续”。
- **实现：** 只补测试与测试设施，没有修改生产代码；场景与发现见[第 3 步计划](./dev-plan-step3.md)第 7 节 3d 结果。测试设施新增 `ExternalApiMocker.integrationApi(…, requestFields, responseFields)`、`ExternalApiMocker.script(…)`、`ChannelAccountVaultMocker.delay(…)`。
- **第一个节点前后的承诺：** 404/500/422/503 用例均断言渠道零请求（500/422 另断言 Vault 零读取）；节点异常、渠道错误、断连、超时、出边非布尔均返回 200 + ERROR。
- **日志：** Platform 自有日志只含编码、节点/出口/初始化项 ID、状态、渠道 HTTP 状态、耗时与固定原因；测试断言日志不含合成凭据与渠道响应标记。入站请求与响应 Body 仍按 W5 由 MVC 公共日志完整记录。
- **验证：** Platform `./mvnw -o install`：337/337 通过；JaCoCo Phase A 门禁同前，属既有问题（integration 模块不在该门禁统计内，本步未单独统计覆盖率）。
- **范围：** 未修改 BFF、前端、Vault 策略，未执行 SQL，未提交。

## 40. 测试数据改为资源文件（2026-10-13）

- **请求：** 用户指出单元测试中的脚本与参数数据过大，建议放到 `platform-support-test/src/test/resources`。
- **调整：** 新增 `src/test/resources/externalapi/`：`definition/`（create-order、query-order、form-refund）、`flow/`（pay、call-create-order、call-query-order、call-form-refund、diamond、self-reference、initializer-cycle、initializer-self）、`script/`（sign、bearer、basic、flatten，`.av` 源码）、`vault/channel-account.json`；加载工具 `ExternalApiFixtures` 每次返回新对象。`ExternalApiSamples` 的下单定义、支付流程与签名脚本改为读取文件；场景测试、完整流程测试与流程校验测试改为“加载文件 + 只改本场景相关属性”（如 timeoutMs、required、出边条件、门店）。
- **保留内联：** 一行的取值绑定与请求输入，以及 `FlowEngineTest` 的 4 行出边样例、`ExpressionEngineTest` 的 3 行脚本——它们就是用例要检验的规则本身。
- **验证：** Platform `./mvnw -o install`：337/337 通过，用例行为不变；JaCoCo Phase A 门禁同前。未提交。

## 41. 第 3 步 · 3e 本机端到端（2026-10-13）

- **请求：** 用户回复“可以”。
- **环境：** 沿用 2e 本机独立库 `mor_platform_external_api` 与 Web 入口；停止 2e 的 Platform、BFF、Gateway 后用当前代码重启。Platform 使用 [stage3e 启动器](./assets/stage3e/harness/PlatformE2e.java)（在 Vault Mock 中登记 3 个合成渠道账号，API Key 每次随机生成、只写入 0600 运行文件）；新增本机合成渠道 [channel.py](./assets/stage3e/harness/channel.py)；BFF、Gateway 沿用 2e 启动器。
- **结果：** [verify.py](./assets/stage3e/harness/verify.py) 15 项全部通过，回执见 [e2e-receipt.json](./assets/stage3e/e2e-receipt.json)：成功、拒绝、补查三条路径正确；渠道流量为 POST 201、POST 201、POST 504、GET 200，下单签名均有效；前置 404/422 无渠道请求；Gateway 对执行路径返回 404；日志不含 API Key。
- **其他：** [2e 验证脚本](./assets/stage2e/harness/verify.py)的示例脚本源码由 `functions.sign(...)` 改为 `hmacSha256Hex(params.key, http.requestBody.orderRef)`（2e 回执为历史记录，未重跑）。
- **数据：** 独立库新增本次合成配置（E3_ 前缀）与合成人员，不清理；未执行任何 SQL 文件，测试库与共享环境未改动。
- **运行状态：** Web、Platform、渠道、BFF、Gateway 仍在本机运行，可随时停止。
- **范围：** 未修改生产代码、BFF、前端或 Vault 策略，未提交。第 3 步 3a～3e 全部完成。
