# Operations 规则编辑 V2 需求与链路评估

> 后继决策（2026-09-11）：[V1 配置增量](../decisions/2026-09-11-risk-rule-v1-configuration.md) 已确认原 V1 调整、仅规则配置、动态主体绑定、手工标签和拒绝原因/文案。本报告保留为 2026-09-10 的历史评估；下文的 Proposed、V2 和字典建议不是本期目标。当前接口/DML 分析见[后继草案](./2026-09-11-risk-rule-v1-configuration-contract-draft.md)。

- 需求编号：`RCO-14`。
- 需求状态：Proposed / 待确认；链路影响评估已完成，扩展合同尚未批准。
- 日期：2026-09-10。
- 本次交付：需求、组件影响分析、依赖顺序与待决问题；不执行功能实现或部署。
- 权威：业务需求条目维护在 [requirements.ts](../../requirements-site/content/requirements.ts)；本文是本次需求的详细评估和交接材料，不替代现行 Risk 或公共接口合同。
- 现行合同：[Risk 后端设计](../../docs/superpowers/specs/2026-09-05-risk-operations-rule-configuration-backend-design.md)、[Console 集成设计](../../docs/superpowers/specs/2026-09-06-risk-operations-rule-configuration-console-integration-design.md)。评估完成不等于扩展合同已批准。
- 实现、验证与部署状态仍以 [CURRENT_IMPLEMENTATION.md](../CURRENT_IMPLEMENTATION.md) 为准；组件报告中的代码路径只作为本次影响评估证据。

## 1. 需求来源和范围

用户要求以 [operations-console-web/docs/design.pen](../../operations-console-web/docs/design.pen) 中的四个“规则编辑 V2”画板为目标，评估现有链路的改动。

| 画板 | ID | 预览 |
|---|---|---|
| 规则编辑 V2 · 列表展开 | `bi8Au` | [预览](../../operations-console-web/docs/design-previews/bi8Au.png) |
| 规则编辑 V2 · 列表收起 | `wO9RC` | [预览](../../operations-console-web/docs/design-previews/wO9RC.png) |
| 规则编辑 V2 · REJECT 原因配置 | `AXojG` | [预览](../../operations-console-web/docs/design-previews/AXojG.png) |
| 规则编辑 V2 · 附加动作配置 | `fulZG` | [预览](../../operations-console-web/docs/design-previews/fulZG.png) |

同文件的“决策流 · 顺序编排”和“决策流 · 添加规则集”不在本次范围。首轮以现有 `CHECKOUT_PREPARE` 配置入口为边界；增加决策点、生产激活或真实副作用执行不能从设计画板推导授权。

评估链路是 Operations Web → API Gateway → Operations BFF → Risk 私有 API；Platform 提供权限、PLT-I02 授权与公共 Route/Console Resource。Nginx 的请求体外层边界作为集成依赖一起核对。

## 2. 待评估需求

| 编号 | 用户目标 | 评估重点 |
|---|---|---|
| V2-01 | 左侧规则列表可展开/收起；右侧仅编辑选中规则；显示顺序、条件摘要、决策标记及未保存状态 | 内存状态、切换时保留编辑、离开提示、权限与历史只读状态 |
| V2-02 | 增删规则、拖动排序、编辑规则/规则集名称和基本信息、切换规则启用状态 | 复用完整不可变新版本保存；开关与排序不作为独立即时生效命令 |
| V2-03 | 用全部/任一条件、条件行和嵌套条件组配置国家/地区、币种、金额等条件 | 与现有 AST 无损往返；单条件折叠；已有 NOT、比较方向、精确 Decimal 不丢失 |
| V2-04 | 以 PASS / REVIEW / REJECT 单选展示终态决定，展示未产生最终决定时的默认 REVIEW | 展示名称映射既有 ALLOW/REVIEW/DENY；不得直接把 UI 名称写入旧 Wire Enum |
| V2-05 | REJECT 时从受控目录选择 Reject Code，带出默认 Reject Message，可按当前规则调整并恢复默认文案 | 目录 Owner、编码与消息的版本/冻结、可空性/必填规则、长度、历史回显及内部/外部披露边界 |
| V2-06 | 为规则配置多个受控附加动作；画板示例包括冷却主体（对象、时长和单位）及添加风险标签（对象、标签） | 动作/参数目录、对象字段可绑定性、类型和范围、顺序/合并/去重、持久化和静态校验；示例不自动成为已注册 Handler |
| V2-07 | 允许规则“不产生决策”，命中后只收集附加动作并继续匹配 | 非终态表达、零终态/多终态约束、与 FIRST_TERMINAL 的关系及默认决定；配置/静态验证和实际执行分开评估 |
| V2-08 | 可折叠查看规则描述，底部显示未保存状态并保存新版本 | 先评估由现有条件生成只读描述；手工编辑描述如有需要，单列持久化扩展，不能假定画板已定义 |

### 保存与兼容必须覆盖的约束

- 规则集与规则的编码、名称及非敏感变更原因必须保留输入/验证入口；现有 Change Reason 必填，不能用拒绝消息代替。
- 规则集编码在首次保存后保持版本流身份；后续保存仍使用读取时的 Base ID/Hash，409 不自动覆盖或换 Base。
- 一次保存产生完整新 DRAFT；Freeze、TEST Candidate 与旧版查询/同键重放需要覆盖新字段的不可变闭包。
- 原 V1 Rule Set/Candidate、Hash、幂等和恢复记录必须保持可读；接口/Descriptor/Hash Schema 如何演进由 Risk 给出方案，不能机械重算历史。
- 当前 65,536 UTF-8 Bytes 保存限额、16,384 Bytes 其他命令限额及零 Body GET 是评估基线；新增字段不能自动授权提高上限。
- AST、Literal、策略阈值、拒绝配置和动作参数不能进入浏览器持久存储、URL、普通日志或遥测；BFF 不泄漏 Handler 实现指纹。

## 3. 分系统评估交付

每个系统应以当前源码和现行 Owner 文档为证据，回答：可复用能力、必须改动的合同/文件/数据、兼容与迁移、权限和安全影响、验证门禁、依赖顺序、待决问题及相对工作量（小/中/大并说明原因）。区分“本系统无需生产代码变更但需要回归”和“必须扩展业务能力”。

| 负责方 | 具体评估范围 | 评估输出 |
|---|---|---|
| Operations Web（本任务） | 页面结构、编辑模型、字段控件、三态映射、目录匹配、旧数据往返、错误与保存体验 | `2026-09-10-risk-rule-editor-v2-assessments/operations-web.md` |
| operations bff | Browser DTO / risk-api Adapter、严格 Decoder、目录投影、PLT-I02、错误与字段路径、安全日志、旧新版本兼容 | `2026-09-10-risk-rule-editor-v2-assessments/operations-bff.md` |
| risk | 原因目录/消息、多个动作/参数、非终态规则、数据模型/Hash、Freeze/Candidate 八项静态校验、原 V1 兼容、运行期边界 | `2026-09-10-risk-rule-editor-v2-assessments/risk.md` |
| Gateway | Approved Root、精确 Route、Body/Header/Query、Problem/413、Metadata-only、安全上限与版本共存 | `2026-09-10-risk-rule-editor-v2-assessments/gateway.md` |
| Platform Support | 权限粒度、PLT-I02、Console Resource、公共 Route/Guard、初始化/增量 SQL 与 Snapshot；Risk 原因/动作目录不转移给 Platform | `2026-09-10-risk-rule-editor-v2-assessments/platform-support.md` |

组件任务仅维护分配的评估输出，不修改本需求、业务源码、公共 Owner 规范、Schema/数据或当前状态台账，不提交、推送或部署。共享工作区已有改动需保留。其他业务系统只有在评估证明存在真实输入/消费契约变化时才列为后续依赖，不因“链路评估”而扩大实现范围。

## 4. 必须由评估澄清的决策

1. 拒绝原因目录由谁维护，是否随版本冻结；历史默认文案变化时，旧规则展示什么。
2. Reject Message 是否允许自由覆盖，是否仅作内部解释；外部提示是否继续由消费接口独立映射。
3. 附加动作第一批白名单是什么；“主体”具体指哪些 typed 字段，当前九字段没有动作参数绑定资格时如何扩展。
4. 同一规则/多条规则的重复动作、冲突参数、顺序和合并语义是什么；终态决定与已收集动作如何组合。
5. 本次是否只覆盖配置、冻结与 TEST 静态校验；真实冷却/标签副作用、运行期评估及发布生命周期的后续前置是什么。
6. 同一路径扩展、独立 V2 Wire 或新 Descriptor 的兼容方案如何选；哪些历史数据不迁移，哪些字段需要手工模型/SQL 演进。
7. 如需新增查询 Route 或改变请求体上限，是否具有实际请求样本/测量证据，精确权限和 Guard 应怎样同步。

评估方应给出建议及理由；未达成一致的问题保持待决，不写成已批准合同。

## 5. 评估验收

- 四个画板中的所有 V2-01 至 V2-08 均有明确的系统归属和支持结论。
- 新字段能否保存、回读、冻结、参与 Hash/幂等和静态检查都有说明，不以“DTO 能加字段”代替闭环评估。
- 明确原 V1 向后兼容及历史不可变策略，并列出跨系统失败场景。
- 给出最小实现路径和依赖顺序；配置能力与真实副作用执行的工作量分开。
- 主任务汇总各系统反馈与分歧，登记需确认项；本轮完成的定义是需求与影响评估完成。

## 6. 反馈汇总

五个组件的评估报告均已形成。以下是影响评估结论与待确认建议，不取代现行 V1 合同。

### 6.1 系统影响与相对工作量

| 系统 / 报告 | 可复用部分 | 完整 V2 的主要增量 | 相对工作量 |
|---|---|---|---|
| [Operations Web](./2026-09-10-risk-rule-editor-v2-assessments/operations-web.md) | 条件树、顺序、启用状态、完整保存、权限与未保存保护 | 分栏编辑、原因/消息控件、typed 多动作参数、严格版本读取、历史回显与字段错误定位 | 布局中；完整合同适配中到大 |
| [Operations BFF](./2026-09-10-risk-rule-editor-v2-assessments/operations-bff.md) | PLT-I02、身份传播、现有路由与单 Owner 调用、Problem/日志边界 | 本地 Models、双向 Adapter、请求/成功响应 Shape、版本目录投影、错误字段路径 | 中；旧新协议共存时中偏大 |
| [Risk](./2026-09-10-risk-rule-editor-v2-assessments/risk.md) | 条件 AST、规则排序/启用、不可变版本、Base 并发保护、Freeze/TEST 检查框架 | 拒绝目录/消息、多个动作/参数和零终态、完整持久化/回读、Hash Schema/幂等兼容、八项检查语义 | 配置中到大；真实运行期独立为大 |
| [Gateway / Nginx](./2026-09-10-risk-rule-editor-v2-assessments/gateway.md) | 对业务 JSON 原字节转发、现有身份/Guard/Problem、大小和超时边界 | 同路径主要回归；根内新增 Route 需精确 Snapshot/路由/共存测试；新根才条件性改 Registry | 同路径小；根内新路由小至中 |
| [Platform Support](./2026-09-10-risk-rule-editor-v2-assessments/platform-support.md) | 五项权限、PLT-I02、同一页面/按钮、公共 Snapshot 协议 | 同路径主要回归；新目录或 V2 路径需完整 Route/Header/Query/Guard 的初始化 DML、Oracle 和人工增量 SQL | 同路径小；新增少量路由小至中，成套版本路由中 |

工作量是各组件的相对复杂度，不是工期承诺，不能把重叠项相加。既有 RCO-I12 的提交、数据库应用、部署与验证缺口不属于 V2 增量，也不会因本评估被视为已完成。

### 6.2 已收敛的评估结论

1. V2-01/02/03/04/08 的布局及既有能力交互可以先沿用 V1 完成。单条件、NOT、比较方向、Decimal、旧 reasonCode、最新 Base 与 Change Reason 必须保持无损。
2. V2-05/06/07 不能只增加 UI 字段。完整闭环是 Authoring 目录 → Save → Detail → Hash/幂等 → Freeze → TEST Candidate → 八项静态检查；Risk 是业务合同 Owner，BFF/Web 显式适配。
3. Risk 已有多行 Action 和基础 typed 参数表，不等于已有可用多动作能力。需要改领域聚合、目录、写读、Hash 和校验；拒绝消息/目录及可靠 Schema 判别仍可能要求物理模型变化。
4. 所有评估均要求保留 V1 历史内容和 Hash、稳定重放及 Validate 恢复，不通过放松旧 Reader、裁剪新字段或重算旧数据实现兼容。
5. 规则配置仍可按 READ/SAVE/FREEZE/CREATE/VALIDATE 五类权限评估复用；原因/动作目录归 Risk，页面内控件不逐个增加 Platform Resource。立即冷却/打标签是另一个高风险业务用例，不能复用保存命令执行。
6. Save 65,536 Bytes、其他命令 16,384 Bytes、GET 零 Body、Nginx 128 KiB 保持评估基线；必须以最终 UTF-8 样本说明容量，不能由画板或动作数量推断需要扩容。
7. 同路径纯业务 JSON 变化通常没有 Gateway/Platform 生产逻辑增量；显式 V2/目录接口仍在批准根内时，通常是精确目录与回归增量。新 Root、特殊 Header/Query、提高硬上限分别单独评估。

### 6.3 方案差异与仍需确认的事项

| 事项 | 评估差异 / 风险 | 汇总建议（未批准） |
|---|---|---|
| Wire 与路径演进 | Risk/BFF 倾向显式新版 Wire + Descriptor；Platform 从自身 delta 考虑倾向复用九条路径。旧 Web/BFF 都会拒绝新增字段，仅换 Descriptor 不够 | 先冻结业务 Wire、旧新分派和历史识别，再决定路径；同路径方案必须证明滚动兼容。不能把“零 Platform delta”当成全链路最低成本已成立 |
| 拒绝文案 | Risk 建议不可变原因版本 + Rule 级覆盖；目录治理、覆盖/默认/语言/长度和对外披露尚未冻结 | 保留当时的默认来源及覆盖关系，不用当前目录重写历史；原 V1 非 DENY reasonCode 的回读/编辑不能静默丢弃 |
| 附加动作 | 当前九字段都不允许动作参数绑定，冷却/标签示例没有自动取得工程注册资格；重复、顺序、目标及合并尚未冻结 | 明确首批动作与 typed 目标，证明合法来源、参数和值域，确定零终态/零动作和到达终态后的收集结果 |
| 配置与 Handler 门禁 | 新 Action 的工程注册/实现证据与“本期只做配置、静态验证”的分期需要明确衔接 | HANDLER_HASH 不能靠占位 Handler 或虚构 Hash 通过；未具备可验证工程证据的能力必须明确列为前置，不开放为可冻结通过的动作 |
| Hash / 幂等版本 | Risk 提出新 Canonical Schema 和可靠的持久化判别；新 Operation Type 或显式 Hash Schema 字段尚未选择 | V1 Golden Vector 和旧命令不变；批准具体字段/Schema/Operation 演进后才制定人工 SQL 和迁移计划 |
| 版本路由与 Guard | V1/V2 不同 Route Code 使用独立 Redis 计数，合计调用容量可能变化 | 明确精确 Route Manifest 与并存容量；不自动新增跨 Route 限流机制 |
| 输入/消费方 | 冷却/标签目标是否已在完整运行输入中存在尚需 Risk 证明 | 若需要新的 Commerce 等输入/消费合同，再追加对应系统评估；本轮不扩大到真实交易或副作用执行 |

### 6.4 建议实施顺序与验收交接

1. 先完成 UI 兼容部分的交互细节设计；同时由 Risk 收敛 §4 和 §6.3 的业务语义、工程能力证据、Wire/Descriptor、Hash 与历史兼容决定。
2. Risk/BFF/Web 提供同一套新旧请求/响应和失败样本；Platform/Gateway 基于准确 Method/Path/Header/Query/Permission 与 UTF-8 计量确认目录差异。
3. 获得后续实施授权后，Risk 完成必要模型/目录/保存回读/Hash/检查扩展，BFF 和 Web 按冻结合同适配；需要的 Gateway/Platform 目录与回归同步完成。
4. 联合验证新字段完整往返、旧 V1 不变、409/422/未知结果恢复、授权拒绝后 Risk 零调用、Body/日志边界与 Snapshot 合法启停。SQL 应用和部署另按精确环境授权处理。
5. 真实运行期、冷却/标签副作用、Shadow/审批/激活作为后续独立范围评估和验收，不由本次配置报告关闭。

本轮已完成的交付是需求登记与组件影响评估。具体合同和后续实现方案仍待确认。

## 7. 本次需求登记的验证

- `RCO-14` 在需求目录中唯一，状态为 `proposed`；未改动既有 `RCO-I12` 和 E2E-15 的现行合同/时序。
- `npm --prefix requirements-site test` 的站点构建通过；测试为 133/136，三项失败分别涉及 CCT seller tax registration、V5 commercial bundle 和数据库指南的旧 Version/Scope 文字。
- 在隔离 Node 进程中仅从读取的 requirements.ts 文本移除 RCO-14（未修改工作区文件），内容测试仍出现完全相同的三个失败，说明本次新增需求条目未引入这些失败；没有越界修复 CCT 文档或测试。
- 新增条目的 ESLint 检查通过。需求稿与五份组件报告共 105 个相对链接均存在；五份报告均覆盖 V2-01 至 V2-08，空白与差异检查通过。
- 本节仅记录本轮文档交付验证，不表示 V2 功能实现、接口联调或环境部署完成。
