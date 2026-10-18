# 注册与客户入驻：阶段 2 差异与系统影响

> 后继设计：用户在阶段 3 要求邮箱密码登录、入驻资料取消版本、批准后只读，并确认退回修改与最终拒绝分开。见 [简化方案](./simplification-design.md)。本文保留阶段 2 时点的源码证据，Google、旧 Profile 冻结和原分步流程不再代表本期目标。

本次分析确认需要新建 Client Web/BFF，补齐 Platform CUSTOMER 认证与支撑能力、Gateway 客户认证边界、CCT 入驻写流程，以及 Risk/Operations 最小人工审核链。用户已决定团队邀请延后；本轮不冻结 HTTP 端点、模型增量、SQL 行数或页面数量。

范围与验收见 [requirement.md](./requirement.md)，评审记录见 [delivery.md](./delivery.md)。以下源码观察是本阶段证据，实际实现、验证和部署状态由 [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-onboarding-20260914) 统一维护。静态清单见 [source-inventory.json](./assets/stage2/source-inventory.json)。

阶段 2 已获用户确认。阶段 3 的 [页面与交互设计](./ui-design.md)已按用户指定归入 [Client 设计文件](../../../client-console-web/design.pen)，Operations 审核图归入其原设计文件；该页面子包不替代尚待闭合的完整 API、数据和 SQL 设计。下表 E-01 是阶段 2 时点观察；用户随后创建了仅含设计文件的 Client Web 目录，不能继续将“目录不存在”作为现状。

## 1. 证据基线

| 证据 | 观察范围与结论 |
|---|---|
| E-01 | 工作区没有 `client-console-web/`、`client-console-bff/` 目录；独立入口名称由 [应用架构](../../../agent-docs/APPLICATION_ARCHITECTURE.md)确定 |
| E-02 | [OperationsAuthenticationApi](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/authentication/OperationsAuthenticationApi.java) 和 [OperationsJwksQueryApi](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/authentication/OperationsJwksQueryApi.java) 为 Operations 专属；现有 API/Controller 清单没有 CUSTOMER 注册、Challenge、Google 或邀请实现 |
| E-03 | [Platform 模型](../../../paytech/platform-support/docs/database/platform-support.pdma.json)已定义 Federated Identity、Authentication Challenge、File 等实体，但这些表未进入当前[人工 Schema 投影](../../../paytech/platform-support/docs/database/sql/platform-support-schema.sql)；模型存在不等于物理库可用 |
| E-04 | [TenantAccessManagementApi](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/tenantaccessmanagement/TenantAccessManagementApi.java)实现 Operations 租户安全管理及成员权限查询，不等于 CUSTOMER 的上下文选择、CCT Provision 或入驻授权链 |
| E-05 | [CCT README](../../../paytech/client-master/README.md)和[CheckoutQueryController](../../../paytech/commercial/paytech-commercial-app/src/main/java/com/paytech/commercial/ext/rest/CheckoutQueryController.java)仅提供 Checkout 查询切片；CCT-I15/I16、草稿持久化、提交/审核结果消费等尚无对应 HTTP 生产链 |
| E-06 | [Risk Compliance 模块](../../../paytech/compliance/paytech-compliance-core/src/main/java/com/paytech/compliance/package-info.java)当前只有模块说明；现有 Risk API/Controller 主要为决策配置和出款资格合同，没有尽调接收、材料核验、补件、人工决定与资格发布实现 |
| E-07 | [OperationsSecurityConfiguration](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/security/OperationsSecurityConfiguration.java)只匹配 OPERATIONS 的 PUBLIC/OPERATIONS_JWT；[ReactiveJwksProvider](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/security/ReactiveJwksProvider.java)固定读取 OPERATIONS JWKS。Catalog 支持 CLIENT 枚举不代表 CLIENT 运行认证可用 |
| E-08 | [VaultLoginEmailVault](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/integration/vault/VaultLoginEmailVault.java)已有稳定邮箱 Protect/Reveal 适配；[LocalLoginEmailVault](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/integration/vault)明确是 local/auto Mock，不能把它作为正式账号邮箱存储证据 |
| E-09 | [全局 E2E-01 时序](../../../requirements-site/content/global-use-case-sequences.mjs)已经规定 Account → CCT Draft → Platform Pending Tenant → 上传 → Submit → Risk → 激活；无需发明第二条由 BFF 双写业务库的链 |
| E-10 | [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md)记录 Operations/IAM 已有验证与 test 部署、CUSTOMER 认证及 CCT 完整写链仍待建设。已有合成 CLIENT Tenant 没有登录凭据，不是入驻审核完成的真实客户 |

本轮检查了相关仓库构建文件、生产 API/Controller、测试文件名、模型和 Schema；未连接数据库、启动服务或重跑 Java 功能测试。源码不存在的判断限定于清单所覆盖目录，不用旧 README 的测试数字证明新功能通过。

## 2. 需求差异表

| 需求项 | 当前行为及证据 | 目标行为 | 缺口/原因 | 处理建议 |
|---|---|---|---|---|
| F-01/F-02 邮箱注册验证 | 有账号与密码存储、Operations 登录；E-02/E-03 | CUSTOMER 注册、验证、重发 | 无 CUSTOMER 写入口及 Challenge 运行链；未验证邮箱与账号生命周期处理待实现 | 复用账号根及密码能力，新增 CUSTOMER 专属合同；按模型补齐实际所需表 |
| F-03 登录/重置/续期 | Operations 的登录、刷新、退出、改密可供复用思路；E-02 | CUSTOMER Account Context 与重置恢复 | 不能修改 Operations Audience 来冒充 Client；Reset 与改密语义不同 | 分离入口和 Realm 密钥，复用真正通用的密码/Session 机制，保留 Operations 回归 |
| F-04 Google | 已有规范，未发现生产入口；E-02/E-03 | 新账号、已有绑定登录、同邮箱显式绑定 | Federated Identity 表和 OIDC 状态/回调/恢复均缺失 | Google Provider 用后继静态配置；不恢复旧 Provider 表或自动合并 |
| F-05/F-10 登录后路由 | Operations 租户管理只读能力存在；E-04 | 本人申请、可用工作区及受限解释 | 无 CUSTOMER Context/Navigation、自身 Member 列表和 Client BFF | Platform 提供当前授权事实，BFF 聚合 CCT 本人进度；页面只消费允许的投影 |
| F-06 草稿 | CCT 模型和 Schema 存在申请/Profile；E-05 | 个人/组织草稿保存与恢复 | 无写链、启动/恢复并发保护及 Profile 映射 | 在 CCT 增加入驻应用能力，保留现有 Checkout 查询合同 |
| F-07 文件 | File 模型已有、运行投影缺失；E-03 | 受控上传、扫描、引用校验 | 需文件服务、对象存储/扫描与 Pending Tenant 授权；不能借现有 Operations 文件夹替代 | 按实际文件流程补齐 Platform 支撑；不复制文件到 CCT 数据库 |
| F-07 提交与 Risk 登记 | 只有设计时序；E-05/E-09 | 一次提交产生稳定主 Case，失败可恢复 | CCT 提交、补偿 Handler、Risk 接收与回填均缺失 | Owner 本地事务持久化，提交后调用；准确提交身份作为恢复依据 |
| F-08 补件与进度 | Risk 有模型和状态机、缺生产链；E-06 | 案件具体补件要求、客户响应及安全摘要 | 申请原始快照不可覆盖；需区分 Client Application 与 Risk Evidence | CCT 保持 UNDER_REVIEW，Risk 管补件；客户前端通过 BFF 取得最小授权投影 |
| F-09 人工审核 | Operations 可承载页面，但 Risk Compliance 未实现；E-06 | 真实审核人员核验材料、补件、批准/拒绝 | 不是给现有列表加按钮；缺 Case/Check/Evidence/Decision 完整 Owner 能力 | 本期新增最小审核业务及入口，不扩展规则引擎；保留程序/相关人/必需检查门禁 |
| F-10 激活 | 有 Tenant 双状态模型，没有本期 CCT 投影生产链；E-04/E-05 | Risk → CCT → Platform → 客户工作区 | 结果身份匹配、事件幂等、来源版本和角色切换待实现 | 风险决定与客户激活分层处理，Platform 安全暂停保持独立 |
| F-11 客户入口 | 动态 Catalog 已支持多 Channel；E-07 | CLIENT PUBLIC/CLIENT_JWT、可信上下文、Cookie 与授权 | 运行认证/JWKS/Origin/CSRF/头清理仍需补齐，注册 Route 本身不够 | 扩展 Gateway CLIENT 安全链和专属 BFF；回归 Operations、Client API 及未知入口拒绝 |
| 邮件/敏感值依赖 | Vault 适配已有，邮件实体以 Tenant 为中心；E-03/E-08 | 无 Tenant 的注册用户收到真实验证/恢复邮件 | `PLT-I09` Tenant-owned Notification 不能直接表达注册邮件；发送恢复材料与 Secret 存储尚未闭合 | 阶段 3 设计最小 Account 邮件契约与安全恢复方式；不创建假 Tenant，不把 Secret 放补偿 Payload |

## 3. 系统与仓库影响表

| 系统/仓库 | 业务职责 | 影响点 | 代码/模型/配置 | 依赖与兼容风险 |
|---|---|---|---|---|
| client-console-web（新建） | 客户页面 | 注册登录、验证恢复、入驻草稿、材料、进度、工作区 | 新前端；无业务库 | 依赖真实 BFF；不得用静态成功页替代状态 |
| client-console-bff（新建） | Client 通道适配与页面聚合 | 认证 Cookie/OIDC 适配、当前授权、申请与补件聚合、命令转发 | 新 Java BFF；无业务模型/数据库 | 固定 CLIENT/client-console；不代理通用 Vault Reveal、不编排跨系统业务事务 |
| Platform Support / api、access-control、web、test | Account/IAM、认证、文件、邮件与公共配置 | CUSTOMER Auth/Challenge/JWKS/Context、Tenant Provision、文件和注册邮件、权限/导航 | 改 API/业务/测试；补当前模型运行投影与注册配置，精确范围待设计 | 保留 Operations、PLT-I03 和租户安全管理语义；支撑模块形态按真实职责决定，不预建空模块 |
| API Gateway | 公网身份、路由、Guard 与可信头 | CLIENT 认证、Account/Tenant Claims、Customer JWKS、Origin/Cookie/预检与身份传递 | 改运行代码与测试；无数据库；配置来自 Platform Catalog | CLIENT_API 机器认证与 CLIENT 人类认证不同；不能复用 Operations Audience |
| CCT / api、web、test | Client/Application/Profile 与激活 | 草稿、提交、本人进度、材料引用、Tenant 投影、Risk 接收/结果消费 | 新业务写能力和测试；模型/DDL复用优先，精确差异待设计 | 不改变现有 Checkout 查询、OE/定价合同；CCT 新消费者不能直接套用仅授权 Commerce 的 Foundation 幂等迁移 |
| Risk / api、compliance、web、test | Case、Evidence、Check、Decision 与资格 | 接收申请、主/关联人案件、审核、补件、决定、资格事件 | 新业务实现及测试；复用既有 DD 模型，精确运行表待设计 | 保留现有决策配置与其他任务；人工入口不绕过 Required Check/Screening |
| operations-console-bff | 审核人员页面聚合与命令转发 | 申请/案件详情、材料核验、补件、决定 | 改 API Client/Processor/Controller/测试；无数据库 | 现有 Operations 登录/IAM复用；审核权限独立登记，不把登录等同于审核权限 |
| operations-console-web | 最小人工审核界面 | 申请/案件列表与审核详情、核验/补件/结论操作 | 新页面和交互测试；精确页面数待设计 | 原风控配置、租户和接入管理页面需要回归 |
| Vault | 批准的非卡敏感值保护 | 复用登录邮箱 STABLE Protect/Reveal；CCT 联系值按既有分类 | 当前未证明需要新 API/表；Allowlist/密钥/真实接入需要核对 | 现有 local Mock 不是正式接入；不存整份 Party/Profile 大 JSON |
| local-environment / test-environment | 开发与 test 拓扑 | Client Web/BFF、CUSTOMER 配置、邮件/文件依赖与健康检查 | 未来配置/脚本变化；当前不改环境 | SQL、外部邮件/Google配置和部署分别授权，自动测试不能自行建库/起替代容器 |
| Commerce / Payment / Finance | 交易、支付、资金 | 本期无交易、支付、账户或出款能力 | 计划不改业务代码/模型 | 不因入驻通过自动开通收款，不修改正在进行的 Payment 任务 |
| 根需求与权威目录 | 需求检索与状态 | 单项三文档、现行需求/时序路由、阶段证据 | 文档与检索指向 | 不复制协议、不改历史状态；其他任务未提交差异保持原样 |

## 4. 客户流程的页面组织建议

以下为阶段 2 的页面职责梳理，尚不是阶段 3 的路由与视觉设计。

| 页面族 | 进入条件 | 主要内容与下一步 |
|---|---|---|
| 注册与登录 | 未认证 | 邮箱密码、Google、登录/注册切换、找回入口 |
| 邮箱验证 | 注册提交/验证链接返回 | 待验证提示、脱敏邮箱、重发、过期/替代/完成反馈 |
| 密码恢复 | 找回入口/重置链接 | 统一请求反馈、链接结果、新密码、重新登录；不泄露是否存在账号 |
| Google 返回与绑定 | 认证回调 | 成功路由或需重新认证的显式绑定；失败可重新发起，不无限回跳 |
| 我的申请与工作区 | 已认证 Account Context | 继续草稿、申请进度、可用客户工作区与受限提示；不显示邀请管理入口 |
| 入驻资料 | 允许操作的本人 DRAFT | 个人/组织字段、相关人资料、草稿保存、材料上传及提交确认 |
| 申请进度与补件 | 本人已提交申请 | 当前状态、客户可见原因、具体补件要求、可见历史及激活等待 |
| 客户工作区起始页 | 当前有效 Tenant Context | 当前客户身份、基本资料及本期能力；合同/技术接入未就绪不显示为可收款 |
| Operations 审核列表 | 当前审核查询权限 | 筛选待处理案件、查看归属与处理状态 |
| Operations 审核详情 | 当前查询/操作权限 | 原始提交、补件和核验结果；按当前门禁请求补件或提交审核决定 |

## 5. 需要在阶段 3 闭合的合同缺口

### 5.1 邮箱验证与发送

现行 Authentication Challenge 只保存 Secret HMAC，重发创建新 Challenge；普通 Notification 依赖 Tenant。注册时尚无 Tenant，两者之间的发送、失败重试和安全材料恢复不能靠把原始 Secret 写进 `compensation_event` 解决。详细设计须给出 Account 级邮件发送边界、受控发送材料生命周期、原子性与重发语义，再判断需要复用或补充哪些模型。此次不选外部邮件厂商、不配置域名。

### 5.2 多申请与文件归属

阶段 2 曾发现仅有 Client 维度的 active_slot 不能保证同一账号只有一份申请。阶段 3 用户现已明确选择“一份”；后继[数据设计](./data-design.md)增加账号维度唯一索引并定义创建恢复，上传以明确申请目标核对 CCT 归属及唯一 Client Tenant。旧有投影歧义仍拒绝，Partner 数量规则不变。

### 5.3 补件与冻结提交

CCT 原始 Submission 与 Risk 后续 Evidence 不是同一份可变表单。补件替换必须追加 Risk Evidence 历史，并准确绑定 Case/Requirement/Subject/File；如果补件实际改变原身份或业务 Profile，应按 Owner 的新申请/提交规则处理，不能用补件入口绕过冻结。对客户只返回必要要求与公开原因，不返回内部命中详情或风控推理。

### 5.4 最小人工审核

用户已确认把最小人工审核纳入本期。建议使用既有 `decision_source=REVIEWER` 的合法人工决定路径，明确审核权限、材料接受、必需检查、相关人依赖、理由与证据门禁。只建设完成这条入驻链需要的案件操作，不建设通用 DD 配置平台或交易规则引擎。人工可完成的检查和真实外部依赖的边界必须写入经业务确认的程序；缺少证据时保持待处理。

### 5.5 结果回传与恢复

Risk 决定发布、CCT 激活、Platform 投影分别为 Owner 本地事务。设计需覆盖发布成功但确认丢失、重复消费、旧版本、同版本不同内容和角色切换失败。页面可以展示激活处理中，不得为缩短等待时间由 BFF 直接更新 Tenant。恢复身份和公开返回不能泄露原始文件内容、邮箱 Token 或 Secret。

### 5.6 既有合同漂移

统一人工认证设计中旧 `t_oidc_provider`、物理 FK/CHECK 和应用 mTLS 描述已分别被后继 Platform/数据库/传输规则取代。本期使用静态 Google 配置、代码级业务约束和私有 HTTP。已有 CCT Hash/Profile、Risk SOURCE Tuple 与 Foundation 命令幂等的迁移边界需在接口设计时逐项核对，不能由本次入驻开发顺手统一所有系统。

## 6. 建议实施顺序与验收关系

以下为供评审的依赖顺序，不授权开始研发或跳过阶段确认。

1. 冻结 Account/Challenge/邮件、文件上传、CCT 草稿/提交、Risk 审核与结果投影合同，完成页面设计和逐库 SQL 差异。
2. 完成 CUSTOMER 认证和 Client 入口，验收 AC-01—AC-05；现有 Operations 认证/IAM 同步回归。
3. 完成 PENDING Tenant 投影、文件与 CCT 草稿/提交，验收 AC-06—AC-08。
4. 完成 Risk 尽调/补件和 Operations 最小审核页面，验收 AC-09、AC-10、AC-13、AC-14。
5. 接通 Risk → CCT → Platform 激活及客户工作区，验收 AC-11、AC-12；覆盖 AC-15 的依赖故障与恢复，再按 UI-01—UI-08 做真实入口检查。

工作量主要来自八个应用落点（两个新 Client 应用，Platform、Gateway、CCT、Risk，以及两个现有 Operations 应用）的协作，还有注册邮件和文件等支撑依赖。当前不能根据“模型已有”承诺少量接口或几天完成；精确接口、表/字段、注册 DML 和页面数量在阶段 3 提供。

## 7. 本阶段验证边界

- 静态核对相关源码清单、模型/DDL 覆盖、仓库状态、文档链接、功能/验收编号和需求站目录路由。
- 需求站的内容与构建检查只证明文档投影可用，不证明注册、邮件、扫描、审核或激活已实现。
- 本轮无业务代码/模型/SQL 修改，无数据库观察/执行、外部资源配置、服务部署、Git 提交或推送。
- 详细检查结果见 [verification.json](./assets/stage2/verification.json)；阶段确认由 [delivery.md](./delivery.md)记录。
