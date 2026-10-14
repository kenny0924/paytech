# Client Console 注册与客户入驻：交付记录

需求正文见 [requirement.md](./requirement.md)，当前完整方案见 [design.md](./design.md)，前序阶段分析见 [technique.md](./technique.md)。实现、验证和部署状态的唯一入口为 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)。

## 1. 本轮授权与适用阶段

- 2026-09-14，用户明确本对话处理 Client Console 业务，要求讨论平台规划，并补充“注册也得考虑进去呢”。
- 对注册/邮箱验证/登录与恢复、个人/组织入驻、提交审核和工作区的讨论，用户回复“可以，帮我开始下一步设计”。据此登记范围方向，并进行阶段 2 的现状差异与影响分析。未把这句话解释为研发、SQL、部署或提交授权。
- 本轮补齐的 F-01—F-11、AC-01—AC-15、UI-01—UI-08 是待审阅的细化清单，不登记为用户逐项批准。
- 用户在本轮范围问题中选择“客户注册入驻＋最小人工审核闭环，团队邀请后续做（推荐）”。据此纳入 Risk/Operations 最小人工审核，团队邀请的发起/接受/撤销全部延后；不改变全系统已批准的邀请模型。
- 阶段 2 交付后，用户回复“可以”，据此进入阶段 3 的方案、接口、数据及页面设计；后续研发、验证、环境操作和提交仍分别按阶段确认。
- 用户随后明确“我创建了一个client-console-web的设计文件，client的ui图应该放到这个下面”。已核对目标为 `client-console-web/design.pen`，Client 界面图以该文件为唯一可编辑入口；Operations 审核界面仍归属 Operations 设计文件。

## 2. 确认记录

| 日期 | 对象 | 用户原意 | 授权范围与状态 |
|---|---|---|---|
| 2026-09-14 | 注册与入驻方向 | “可以，帮我开始下一步设计” | 范围方向认可；启动下一步分析，不代表精确字段/接口已批准 |
| 2026-09-14 | 人工审核/团队邀请范围 | “客户注册入驻＋最小人工审核闭环，团队邀请后续做（推荐）” | 本期包含客户申请和最小人工审核；邀请延后 |
| 2026-09-14 | 本轮细化需求与阶段 2 分析 | “可以” | 接受阶段 2，进入阶段 3；不包含研发或 SQL 执行 |
| 2026-09-14 | Client UI 文件归属 | “client的ui图应该放到这个下面” | 使用 `client-console-web/design.pen`，不另建竞争设计文件 |
| 2026-09-14 | 认证页简化 | “左侧这个太丑了，页面简约点” | 移除认证页左侧色块与宣传说明，统一白底居中表单，并同步手机注册稿；属于阶段 3 页面修订 |
| 2026-09-14 | 个人与组织流程划分 | “帮我把组织和个人的做一下划分，我看一下有什么区别” | 明确两类基础资料与材料对象，个人 4 步、组织 5 步，增加个人页面变体与设计对照；不改变并行申请数量或审核门禁 |
| 2026-09-14 | 展开子页面 | “编辑人员这些页面我怎么没看到？有一些子页面也画一下” | 补画人员、关系、材料、只读详情、补件记录及未保存退出的 9 张状态稿，继续沿用既有 Client 设计文件 |
| 2026-09-14 | 按填写流程整理 | “按照入驻填写的流程整理一下” | 画布及图层按 8 个流程阶段分组，子页面归入对应步骤；保留原画板 ID、功能页数量与业务内容 |
| 2026-09-14 | 未完成申请数量 | “一份” | 同一账号个人/组织 Client 入驻合计一份未完成申请；据此收口恢复、唯一索引和上传归属，不改变多个已有工作区授权；仍在阶段 3 |
| 2026-09-14 | 详细设计收口 | “下一步” | 补齐阶段 3 剩余共享迁移、注册配置、Cookie 路径和全局需求/时序；交付完整评审稿，不登记为用户已确认阶段 4 |
| 2026-09-14 | 进入研发 | “可以” | 接受完整设计与研发顺序，进入阶段 4；先做数据模型/查询兼容和邮箱认证闭环，再完成入驻审核。未授权数据库执行、外部配置、部署或提交 |
| 2026-09-15 | 组织人员与关系业务 | “继续吧” | 沿已批准阶段 4 补充 C07—C11 及 C05 Owner 人员/关系查询；固定回执原则继续适用，不恢复资料版本；不含 SQL 执行、对外开放或部署 |
| 2026-09-15 | 材料上传与关联 | “继续” | 继续已批准阶段 4 的材料链；核对出 F01 主体绑定、写接口回执及 Upload/扫描恢复模型缺口，形成具体补充稿；该回复不预先批准后来提出的合同差异或新 SQL 执行 |
| 2026-09-15 | 文件合同补充 | “可以” | 批准原申请＋主体绑定、F01/F02/C12 合同及 Upload/扫描恢复模型，继续阶段 4；不包含七表 SQL 执行、供应商配置或部署 |
| 2026-09-15 | 草稿写接口固定回执 | “可以” | 接受 C04/C06 固定操作回执、C05 查询当前资料；继续 CCT 创建/恢复、主资料查询和增量保存，不恢复资料版本；不含部署、外部入口或 SQL 执行 |
| 2026-09-15 | 注册密码流程 | “暂时邮箱密码注册吧。对外开放前会优化” | 保留 A01 邮箱密码、A03 邮件秘密验证；优化延期到对外开放前，继续阶段 4，不授权外部开放或环境操作 |
| 2026-09-15 | 当前四工程批次 | “可以，开始做当前的任务” | 继续 Platform、Client BFF/Web、Gateway 认证批次及相应设计同步；仍在阶段 4，不含 SQL 执行、部署或提交 |
| 2026-09-15 | 本机认证单表与回归 | “可以” | 对精确执行候选授权：仅在 `mor_platform_config_validation` 新增认证挑战表并运行隔离认证测试；不扩大至其他 SQL、共享环境或部署 |
| 2026-09-15 | 本机 HTTPS 联调 | “可以” | 继续临时本机 Web/Nginx/Gateway/BFF/Platform 验证；不授权公共入口激活、共享配置、DDL 或部署 |
| 2026-09-15 | CCT 入驻后端前置 | “继续” | 继续阶段 4；本轮完成本机 CCT 只读预检、已有 HTTP 边界回归及十表执行候选。未将其解释为新 CCT SQL 执行授权 |
| 2026-09-15 | CCT 本机十表与回归 | “可以” | 针对上一轮精确候选，仅批准本机 mor_cct 十条 ALTER 和隔离 Checkout 回归；不包含旧补偿表、其他系统、部署或提交 |
| 2026-09-15 | CCT 开通恢复与草稿写链 | “可以” | 继续阶段 4；已补齐补偿模型、独立单表 SQL 与只读预检，新增 ALTER 尚待该表精确执行授权 |
| 2026-09-15 | CCT 补偿单表执行 | “可以” | 针对原指纹 SQL，仅批准本机 mor_cct.t_compensation_event 一条 ALTER 与相关验证；已执行核验及完成 CCT 40/40 回归，不包含其他环境或上线 |
| 2026-10-13 | 材料与表单国家口径 | “我觉得由人工判断，系统保持通用性吧”“不要判断首批开放哪些国家”；对出差异稿回复“可以” | 方向确认：取消按国家确定材料、目录和号码规则，材料充分性由人工审核判断；形成[通用化差异稿](./generic-material-form-proposal.md)待审阅。原两个国家问题不再待答复；差异稿内具体目录与规则尚未批准，不含代码、SQL 或配置修改 |
| 2026-10-13 | 通用化差异稿 | “按照你的建议来” | 按建议确认材料目录与基础必需项、组织/证件类型和通用规范化规则，继续阶段 4 实施 Risk/CCT/BFF/Web 改动；经营模式列表与列注释在稿中无建议，分别以实现补充待复核和本期不改处理。Risk 初始化 SQL 只生成[执行范围](./db/implementation/risk-generic-materials-local-execution.md)，未授权执行；不含部署、入口启用或提交 |
| 2026-10-13 | C14 下一步 | 对“先出 C14 合同修订稿”回复“可以” | 形成 [C14 修订稿](./submission-contract-amendment.md) 待审阅：服务端生成 submissionId、按状态防重、不接入本地请求重放；不含代码、SQL 或配置修改 |
| 2026-10-13 | C14 修订稿 | “可以” | 确认修订稿第 8 节三项（组织至少一名关联人员、服务端 submissionId 与状态防重、先 4a 后 4b），开始 4a 研发；不含 SQL 执行、Risk 迁移、入口启用、部署或提交 |
| 2026-10-13 | 4a 实施修订 | （研发发现，未另行请示） | 公共 HTTP Client 不传递空 Body 请求的字段级 422，C14 改为 `SUBMISSION_INCOMPLETE` 并新增只读 C16 提交核对；修订已写入 [C14 修订稿第 2.1 节](./submission-contract-amendment.md#21-实施修订c16-提交核对2026-10-13)，交付时向用户说明 |
| 2026-10-13 | 开始 4b | “可以，继续做” | 开始 4b：完成本机 mor_risk 与 Vault 只读核对，生成 [Risk 本机迁移执行范围](./db/implementation/risk-onboarding-local-execution.md)；SQL 未执行，待精确授权；Vault 快照数据类型未配置，待用户决定 |
| 2026-10-13 | 4b 决定 | 选择“授权执行（推荐）”“本期先不做快照”“由我继续（推荐）” | 执行本机 mor_risk 迁移（[回执](./assets/stage4/risk-onboarding-table-execution-20261013.json)）；实现 Risk I01 服务端，快照暂缓；Risk 重构会话确认由本会话实现。不含其他环境、入口启用、部署或提交 |
| 2026-10-13 | Operations 审核方案 | “可以” | 形成 [Operations 审核实施方案](./operations-review-plan.md) 待审阅；前一轮关系映射与 409 限制视为接受。不含代码、SQL 或配置修改 |

## 3. 当前成果与待确认事项

阶段 3 收口时已补齐[迁移设计](./migration-plan.md)与[注册设计](./registration-design.md)，当时业务待决项为 0；具体环境的非空历史处置、实例/邮件/扫描配置和角色授予列为阶段 6 发布门禁。注册复用现有 Owner 管理接口，48 个模板按真实 ID 回读绑定；无需伪造初始化 ID。37 条公共 Route 保持禁用，运营为 1 菜单 + 4 按钮，Client 入驻前采用固定页面入口。

本轮修订 C02 外部地址，使其处于认证 Cookie Path；将旧清单中的 15 秒响应超时改为平台硬上限 10 秒，补齐 Header、Query 和 Guard。全局需求目录和 E2E-01 同步邮箱密码、单份申请、submissionId、退回/拒绝、运营分类及批准只读；Partner/共享认证的后续能力仍保留。

阶段 3 完整设计已获用户确认，当前进入阶段 4；按[研发计划](./implementation-plan.md)先实现数据模型与 Checkout 读取兼容、注册/邮件验证/登录/密码恢复闭环，再完成入驻和审核链。下一阶段确认点为研发结果审阅后进入综合验证；研发阶段授权本身不包含数据库执行、部署、配置外部资源或提交，另获准的本机单表 SQL 范围见下方记录。

2026-09-15 用户确认暂时保留邮箱密码注册，密码与邮箱所有权绑定优化延至对外开放前，[决定记录](./registration-password-binding-amendment.md)已更新。本期 A01/A03 及两张页面保持原合同，恢复研发；不再将此事项列为当前流程待决。阶段 4 进度与实际验证从[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)读取，首批尚未完成。

2026-09-15 用户回复“下一步”，继续当前认证批次；完成本机只读数据库前置核对，并提交[单表执行范围](./db/implementation/platform-auth-local-execution.md)供精确 SQL 授权。该回复未被解释为数据库变更授权；当时未执行 CREATE。随后用户回复“可以”，授权这一张本机表及真实数据库测试；[执行回执](./assets/stage4/platform-auth-table-execution-20260915.json)与[认证回归记录](./assets/stage4/platform-auth-http-verification-20260915.json)已归档，当前仍在阶段 4，未进入阶段 5。

2026-09-15 继续阶段 4 的 CCT 工作区恢复。来源机制见[恢复合同](./cct-workspace-recovery-contract.md)。C04/C06 原完整响应幂等重放与单份资料的冲突已按用户“可以”采用[固定回执方案](./draft-command-response-proposal.md)，后续 C07—C11 延续该原则；实现与回归结果从[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)读取，不再将这个前序决定列为待确认。

2026-09-15 材料链补充审阅包为[上传与材料关联提案](./file-upload-closure-proposal.md)，附[字段与索引清单](./db/file-upload-closure-proposal.json)、[七表候选 SQL](./db/platform-files-create-review.sql)及[只读前置证据](./assets/stage4/file-prerequisites-preflight-20260915.json)。新增 subjectPartyId、固定完成/关联回执与持久化 Upload 目标会改变已批准合同，用户随后回复“可以”确认这部分差异，恢复研发；原入驻、人员与认证范围不重新评审。存储/扫描供应商待确定，稿件按通用 Port 和测试 Simulator 界定研发，不批准任何真实供应商配置、程序材料规则或扫描通过数据。

### 前序阶段 3 记录

以下按当时交付范围保留，旧的“未闭合”描述由上述本轮成果取代；不能将旧画板/接口基线重新视为当前方案。

- 用户回复“可以，下一步”，继续阶段 3：形成[详细设计入口](./design.md)、[API 合同](./api-contract.md)、[数据设计](./data-design.md)、[实施计划](./implementation-plan.md)、精确目标字段投影与评审 SQL。没有进入研发或执行数据库。
- 本轮新增 Client S05—S08 与 Operations 分类确认页，共 5 张；加上前轮简化 4 张和历史 40 张，总索引 49 张。Client 44、Operations 入驻 5；新图是状态，不自动新增功能路由。两个设计文件均使用原有入口。
- 同账号申请数量已确认“一份”。本轮在评审生成器/目标 SQL 中增加账号活动申请唯一索引，补齐按状态恢复、跨类型冲突及上传目标核对；目标 CREATE 仍只供评审，不作为迁移执行。共享迁移与最终环境注册材料继续保留实施前置。

- 用户追加四点简化反馈：讨论合并页面/放宽草稿约束、行业与 MCC 由运营确认、取消本期入驻资料版本且批准后不可修改、仅邮箱密码登录且 OTP 暂不做。据此形成 [简化评审稿](./simplification-design.md)，附字段映射、共享消费者影响和数据库目标差异；随后用户“可以，下一步”认可简化方向并授权继续详细设计。
- 用户随后明确选择“区分退回修改和最终拒绝，只有退回后客户才能修改”。新目标区分 RETURNED 与 REJECTED，审核中只读，退回继续原申请，重提隔离旧任务结果。
- 本轮新增 S01—S04 四张核心页面评审稿，写入同一 Client 文件顶部；旧 36 张保留作前轮基线。此为前轮记录；本轮增量见下项。

- 已形成需求、功能/视觉验收、源码差异表、系统影响表及页面职责建议。
- 已识别注册邮件无 Tenant、文件归属多申请歧义、补件不可覆盖提交、人工核验必要证据、审核结果与激活恢复等阶段 3 必须闭合的合同问题。
- 阶段 3 页面子包见 [ui-design.md](./ui-design.md) 和 [画板索引](./assets/stage3/ui-manifest.json)：Client 36 张画板写入用户指定文件（35 张状态/适配稿与 1 张设计对照图），Operations 追加 4 张审核画板；分别映射 11 / 2 个拟定功能页。当前按 8 个填写流程阶段分组，28—36 归入相应主步骤；分组容器不计作产品画板。PNG 是同源只读预览，不是第二套可编辑稿。
- 本次审核范围已由用户选择，不再等待是否包含审核/邀请的重复确认。
- 当前进入阶段 3；先完成不依赖业务待决项的页面和交互设计。阶段 3 的完整接口、数据、SQL 及研发计划尚需继续闭合，不能把页面图交付登记为阶段 3 全部完成。
- 数量选择已按用户答复收口，不再等待重复确认。旧画板暂不重画，本轮更新其交互合同；既有静态图不作为新并发和上传行为已实现的证据。

## 4. 本轮验证记录

2026-09-15 完成 Platform PLT-I17 的 CLIENT 工作区开通、顺序状态投影及来源查询，真实成员/角色写入、租约竞争、整体回滚、幂等与授权审计已验证。完整 **clean verify 572/572（57 Suite）**，覆盖率门禁通过；依据见[组件证据](./development-progress.md#2026-09-15-platform-工作区开通与完整回归)及[回归回执](./assets/stage4/platform-tenant-projection-verification-20260915.json)。独立开关默认关闭，未迁移或部署；CCT 草稿和业务补偿 Handler 未连接，阶段 4 未完成。

2026-09-15 获准 CCT 十表执行 **10/10**，99 列/27 索引与目标 Schema 一致，其余 28 表结构未变。实际数据库测试发现并修复预检查遗漏 Client Party Profile 的问题，新增批准/草稿与分类场景，最终 **clean verify 35/35** 通过，测试数据全部回收。详见[组件证据](./development-progress.md#2026-09-15-cct-十表获准执行与数据库回归)、[十表回执](./assets/stage4/cct-onboarding-table-execution-20260915.json)及[回归记录](./assets/stage4/cct-checkout-mysql-verification-20260915.json)。补偿表和完整入驻写链仍待后续实施，阶段 4 未完成。

2026-09-15 CCT 本机只读预检与 4/4 HTTP 边界回归见[组件证据](./development-progress.md#2026-09-15-cct-本机预检与-http-边界)和[检查记录](./assets/stage4/cct-onboarding-preflight-20260915.json)。38 表为空、十张目标表源结构匹配，已留结构备份；本机十表 ALTER 尚未授权/执行，完整 Checkout 数据库回归与草稿写链仍待完成。另记录现行 Foundation 与旧 CCT 补偿表不兼容的必要依赖，未追加未审查的第十一条迁移。浏览器验收仍保留上轮范围。

2026-09-15 本机 HTTPS 协议链 28/28 请求、49 项 Java 针对性回归和 Web 4 项/构建通过，修复 422 标题与 Cookie 互通问题；真实数据库审计及精确清理已核验。浏览器停在临时证书提示，待用户本人处理后另行验收，不能登记为浏览器链完成。详见[最新组件证据](./development-progress.md)及[HTTPS 记录](./assets/stage4/client-https-verification-20260915.json)。本轮临时服务已停止，当前仍在阶段 4。

2026-09-15 用户回复“继续”，Gateway 请求链验证见[最新组件证据](./development-progress.md#2026-09-15-gateway-真实-http--redis-增量)及[检查记录](./assets/stage4/gateway-http-verification-20260915.json)：真实 Gateway HTTP/Redis 全量 1,261 项通过；指令 96.624745%、分支 90.051323%，覆盖率与架构门禁通过。修复 CLIENT Cookie、Origin/预检、Guard 缺项和关闭入口的错误合同。HEAD 对照副本也复现 Netty ByteBuf 警告，列为既有待处理项。真实 Platform/MySQL 与 HTTPS 联通仍未完成，阶段 4 首批未验收；没有执行 SQL、启用入口或部署。

2026-09-15 四工程认证接入见[当前组件证据](./development-progress.md#2026-09-15-四工程认证接入增量)及[检查记录](./assets/stage4/client-entry-verification-20260915.json)：Platform 79、Gateway 56、BFF 10、Web 4 项通过，前端构建与限定范围浏览器/Pencil 检查通过。该前轮真实 MySQL/Redis 回归缺凭据；Gateway/Redis 已由上方增量继续验证，认证首批未登记为完成。

2026-09-15 阶段 4 认证增量见[组件进度](./development-progress.md)及[检查记录](./assets/stage4/authentication-verification-20260915.json)：47 项聚焦检查通过，包含真实 SMTP 协议 Simulator 投递；真实 MySQL/Redis HTTP 用例在数据库前置检查失败，业务用例未运行。首批尚未完成，未进入阶段 5。

阶段 3 收口证据见[design-closure-verification.json](./assets/stage3/design-closure-verification.json)：四份生成器可重复输出；37 Route 与 40 操作对应；配置 Body 字段及绑定、Cookie Path、Header/Query/Guard、三个 Owner 只读预检引用和文档链接通过；需求站构建与 136 项测试通过，变更文件 ESLint 通过。SQL 未连接 MySQL 执行，该轮没有重画 Pencil 或运行业务链。

以下为前轮证据，按各自工件版本保留：

单份申请规则的增量静态检查见[single-application-verification.json](./assets/stage3/single-application-verification.json)。它取代前轮记录中的数量/上传待决项及受影响生成物 Hash；前轮详细工件检查见[detail-design-verification.json](./assets/stage3/detail-design-verification.json)，历史证据不改写为新行为已验证。

简化稿增量证据见 [simplification-verification.json](./assets/stage3/simplification-verification.json)：4 张核心评审画板、44 个总画板索引及新旧范围分别记录；前序检查仍只适用于前序画板。此次未改或运行业务代码、模型、SQL。

静态源码/模型/Schema 清单见 [source-inventory.json](./assets/stage2/source-inventory.json)，文档、范围和需求站检查见 [verification.json](./assets/stage2/verification.json)。清单保存选定源码 Hash 和仓库基线，不含密钥、数据库连接信息、账号原值或完整配置。

上述阶段 2/3 未运行注册/邮件/Google/上传/审核业务测试，未把已有其他切片测试视为本需求测试；阶段 4 的当前证据以上方增量记录为准。

阶段 3 页面子包的布局、保存、PNG 尺寸、文档和索引验证见 [ui-verification.json](./assets/stage3/ui-verification.json)。静态画板不能证明键盘、回调、扫描或真实审核链已经通过；完整状态仍以当前台账为准。

## 文件增量的单独执行授权

用户在查看 [Platform 七表执行范围](./db/implementation/platform-files-local-execution.md)后回复“可以”，授权在 `127.0.0.1:13306/mor_platform_config_validation` 应用指定 SHA-256 的 7 个 CREATE，并运行相关本机验证。原 26 表、其他 Owner 数据库、真实扫描/材料规则、外部资源和路由启用不在这次写入范围内。该确认沿用已批准阶段 4 的研发权限，继续受控上传实现，不需要重新确认普通代码编辑。

执行原始证据及只读复核由执行清单路由；本次与后续业务代码、测试、数据库实际状态只在 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)维护。下方保留首批启动时的历史环境边界，不能据此覆盖最新台账。

## BFF / Web 接线的继续授权

用户在“下一步接通 BFF 聚合和 Web 材料页面”后回复“可以”，继续已批准阶段 4。范围包含材料页面依赖的只读申请导航、个人/组织表单、人员/关系及 F01—F04/C12/C13 交互；CCT 增加主 Party 坐标及原申请摘要，BFF 负责页面组合。该确认不扩大到正式提交、真实供应商配置、公共路由开放、数据库迁移或部署。本批没有调用 Superpowers 或子 Agent。

合同增量统一进入[接口合同第 11 节](./api-contract.md#11-资料与材料页面的-bff-投影边界)，当前结果与验证范围只在[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)维护。

## 受控目录与提交链的继续授权

用户在“下一步补受控字典、正式提交和人工审核闭环”后回复“可以”，继续已批准阶段 4 研发。本轮先补 C03 受控目录的 Owner/BFF/Web 链，合同细化进入[第 12 节](./api-contract.md#12-c03-受控表单目录)。具体国家材料口径、首批开放国家及证件规范化政策没有从这句确认中推断；当前尚待答复的问题和实际验证均在[实现台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914)维护。该继续授权不包含 SQL 执行、公共入口启用或部署。

## 5. 环境与提交边界

阶段 2 仅修改单项需求与根目录检索/状态路由。阶段 3 增加设计工件与设计说明；不把 `.pen` 画板视为已实现前端。阶段 4 已修改批准范围内的模型、Schema、查询与认证代码，并尝试连接本地测试数据库；缺少安全注入的凭据，未执行 DDL/DML。未配置外部邮件或对象存储等资源、部署、创建分支、提交或推送。开始时存在的 Payment 及其他需求未提交修改保留。
