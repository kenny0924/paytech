# 租户访问与成员权限查询：现状差异及系统影响

阶段 1/2 已由用户“可以”确认，包含脱敏邮箱与账号 ID 的成员显示/筛选细化。本稿保留阶段 2 分析；精确 API/数据/SQL/Pencil 与研发计划由 [阶段 3 方案](./design.md) 接续，当前待确认。业务范围见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)，实施和环境状态见 [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-tenant-access-20260914)。

**一句话总结：预计修改 Platform Support、Operations BFF、Operations Web 3 个应用，新增约 8 项 API 能力；复用现有 7 张 IAM 表，预计新增业务表 0、增改业务字段 0；Gateway 无需改代码，注册 DML、索引及最终接口数量在阶段 3 冻结。**

本稿区分已有设计、现有代码、已有测试和环境观察；不把阶段 1 确认或读取到旧测试源码视为本期实现/验证完成。以下 API 数量为拆分估算，未定义可执行合同。

## 1. 需求差异与处理建议

| 需求项 | 当前行为及证据 | 目标行为 | 缺口或无法满足的原因 | 处理建议 |
|---|---|---|---|---|
| F-01 租户列表 | 模型、Schema、本机及 test 均有 t_access_tenant；现有凭证查询只附带 CLIENT 信息 [E01/E02/E05] | CLIENT/PARTNER 分页、名称/身份/双状态筛选，包含失效记录 | 无独立租户查询合同；凭证列表不能反推全部租户，PARTNER 也没有机器凭证 | 增加只读查询，使用已投影名称和 Owner ID，不查 CCT 数据库 |
| F-02 租户详情 | 现有表已有来源状态、安全状态、来源版本及并发版本 [E01/E02] | 展示租户身份和租户层面的访问条件 | 无管理详情；ACTIVE + ACTIVE 只是租户门槛，不是所有人/凭证可用 | 复用现有事实派生限制原因，不加状态表/字段 |
| F-03 安全暂停/恢复 | PLT-I03 已检查租户双状态；只有测试夹具直接更新安全状态，没有生产管理命令 [E05/E10] | 单租户暂停/恢复，原因、并发及重复提交可识别 | 账号 disable/enable 改全局 Operations 账号；凭证 revoke 不可恢复且只针对单凭证，均不能代替租户操作 | 新增安全命令，复用事务、幂等、资源锁及审计，不级联修改账号/授权/凭证 |
| F-04 成员查询 | Account/Member 模型支持 CUSTOMER 及 CLIENT/PARTNER，现有员工接口/SQL 仅覆盖 OPERATIONS/INTERNAL [E02/E03] | 指定租户内查看成员及账号状态，保留失效成员 | 直接扩展员工页可能顺带开放编辑/授角色；账号没有姓名字段 | 新建只读查询，保留旧写接口范围；显示采用脱敏邮箱和账号 ID，见第 7 节已确认细化 |
| F-05 成员角色 | Role、Member Role 有作用范围、状态和有效期；旧角色接口用于 Operations 管理 [E03] | 展示当前成员授权记录及生效状态 | 旧有效角色查询过滤了失效记录并绑定 INTERNAL，无法解释过期/撤销/错误范围 | 独立只读投影，不跳入旧角色编辑流程 |
| F-06 权限与来源 | 旧 permissions SQL 只返回有效权限，不能返回被过滤原因及全部来源 [E03] | 权限合并、来源展开、已知未生效原因 | 全局权限目录/操作者可授予目录不代表目标成员授权 | Platform 按同一时刻和一致性快照计算，来源按需分页；BFF/Web 不计算业务授权 |
| F-07 权限/审计 | BFF 每次请求调用 PLT-I02，Platform 有管理策略、审计和命令基础设施 [E04/E06/E07] | 独立查看/管理权限及可定位操作记录 | 缺本期权限/路由/页面登记和租户审计展示 | 登记本期关联，复用既有机制，不加审批、回执或可靠审计表 |

## 2. 系统与仓库影响

| 系统/仓库 | 职责及影响点 | 代码/模型/配置判断 | 依赖及兼容风险 |
|---|---|---|---|
| Platform Support | API 新合同；access-control 租户读写和成员授权查询；web Controller/Processor、事务/幂等；审计动作/受控属性；页面能力及注册数据 | 改代码、初始化/增量 DML；预计业务字段不变，索引按查询评估 | 当前管理策略用于验证 Operations 操作者；目标 CUSTOMER 成员另行限定租户，不能放宽旧员工/角色写接口 |
| Operations BFF | 新 Controller/Client/DTO、Owner API Bean；按需组合租户/成员基础信息与当前授权页，必要审计展示适配 | 改代码并消费同版本新 Platform API 制品；无数据库 | 保持实时 PLT-I02；目标 Tenant ID 不替换操作者 INTERNAL Context；无权限不发起相应子查询 |
| Operations Web | 一个租户菜单；只读详情、成员列表、角色/权限视图；暂停/恢复原因弹窗、分页、导航、错误、审计文案及 Pencil | 改代码及页面能力清单，阶段 3 改设计 | 不复用有保存/授予能力的旧员工编辑页；按需取来源，保持 URL 与浏览器回退 |
| API Gateway | 动态读取新增 Operations Route；Client API 继续逐次 PLT-I03 | 预计无代码/镜像变更，Route 由 Platform 登记 | 复用已有 OPERATIONS Target、HUMAN_PERMISSION，不加业务白名单或直接查数据库 |
| CCT | 客户/合作伙伴主档及来源投影 | 不改代码或数据 | 仅读 Platform 已投影事实；不手工 Provision，不扩展真实入驻/投影链 |
| Client/Partner Console、CUSTOMER 认证 | 后续客户日常成员管理及人工访问 | 不改代码、不新增应用 | 当前人工授权仅 Operations；本期不宣称 CUSTOMER 登录、切租户、会话链通过 |
| Foundation / Vault | 既有锁、HTTP、安全基础设施 | 预计无改动/新制品发布 | 只读取脱敏值，不 Reveal 邮箱；无已知基础库扩展需求 |
| 需求与模型工件 | 同步已批准 PLT-15/E2E-16；后续按需索引/Schema/初始化/增量 | 本阶段只改需求/目录/时序和观察材料，不改模型/SQL | 不把预计数量写成部署统计，保留其他任务改动 |
| local-environment / test | 已有库可支持切片，后续需合成 CLIENT/PARTNER/CUSTOMER 示例和注册数据 | 不改拓扑，本阶段只读 | 本机回归使用验证库；test 示例/替换应用/启用另列清单，不重建全库 |

## 3. 数据模型与环境观察

### 3.1 已有业务表

| 表 | 现有字段数 | 本期用途 | 本期业务写入 |
|---|---|---|---|
| t_access_tenant | 11 | 租户身份、来源/安全状态、版本 | 目标行 security_status、version、update_time |
| t_user_account | 16 | 账号 ID、Realm、类型、脱敏邮箱、状态 | 无 |
| t_tenant_member | 9 | 租户成员身份、类型、状态 | 无 |
| t_role | 11 | 角色名称/代码、状态、作用范围 | 成员查询无写入；后续 Operations 管理角色授权可能需按惯例推进版本 |
| t_tenant_member_role | 12 | 成员角色授权状态、有效期 | 无 |
| t_role_permission | 7 | 角色到权限的授权关系 | 目标成员授权无写入；Operations 新入口的显式角色授权归注册/启用 DML |
| t_permission | 9 | 权限代码、资源类型、操作、状态 | 仅登记本期 Operations 管理权限，不给被查询成员自动授权 |

这 7 张表共 75 个已有字段，**不是新增 7 表或新增 75 字段**。账号表没有姓名/昵称/显示名，普通 HUMAN 的 login_name 为空，不能当客户姓名。

另外复用现有 t_command_idempotency 和审计表，不新增。租户安全命令更新三列的值，不属于 Schema 改三字段；来源 owner_status/source_version/source_payload_hash 保持。也不恢复旧设计中已删除的 owner_ref_type。

### 3.2 只读观察

2026-09-14 对 Docker desktop-linux 两库观察了字段、索引及聚合计数，证据见 [database-observation.json](./assets/stage2/database-observation.json)。仅 SELECT，没有导出账号明细、敏感值或凭据，没有数据写入。

| 环境 | 身份 | 相关 7 表 | CLIENT/PARTNER Tenant | CUSTOMER Account/成员 | CLIENT 凭证 |
|---|---|---|---|---|---|
| test | mor-test-mysql-1；127.0.0.1:23306/mor_platform | 全部存在，列名/数量与 Schema 一致 | 0 / 0 | 均为 0 | 0 |
| 本机验证库 | mor-local-mysql-1；127.0.0.1:13306/mor_platform_config_validation | 全部存在，列名/数量与 Schema 一致 | 0 / 0 | 均为 0 | 0 |

PDManer 和 Schema 对七表的字段名称/数量一致；观察不等于完整库一致性或性能验证。两库目前只有 INTERNAL Tenant；test 已有 Operations 账号/成员，必须保留。未来执行前重新核对，不能沿用“当前为空”覆盖后来新增业务。

### 3.3 索引与注册数据

- Tenant 有主键及 (tenant_type, owner_ref_id) 唯一索引；名称包含/状态组合筛选和稳定分页是否补索引，在阶段 3 根据确定的 SQL/排序评估，不预加全部筛选排列的索引。
- Member 有 (tenant_id, account_id) 等唯一索引，Member Role 有成员/租户和角色/范围索引，Role Permission 有 (role_id, permission_id) 唯一索引。按页查询、COUNT 同口径，不将七表无界关联为整页全量结果。
- 预计登记约 **8 条 Operations Route、3 类 Permission、1 个菜单及必要按钮**，配套 Header/Query/Guard、显式角色授权；普通角色不默认得到新权限，首次禁用登记和启用分开。
- 登记涉及既有 t_gateway_route、t_gateway_route_header、t_gateway_route_query_param、t_gateway_guard_rule、t_permission、t_console_resource、t_role_permission，按惯例推进授权角色版本。目标业务 Tenant 不是初始化目录种子。
- 不新增 Target、认证资源表、权限诊断表、暂停历史表或变更回执表。阶段 3 提供精确 SQL，当前没有待执行 SQL。

## 4. API 能力拆分估算

按业务操作去重，预计 **7 项读取 + 1 项安全状态变更，共 8 项新 API 能力**。若 Owner/BFF 均逐项提供，各约 8 个端点，合计约 16 个落点；不重复计成 16 项业务能力。Method/Path、字段、上限、错误和最终聚合方式在阶段 3 冻结。

| 分析编号 | 能力 | 拆分理由 |
|---|---|---|
| A01 | 租户分页 | 客户/合作伙伴统一目录，不依赖凭证存在 |
| A02 | 租户详情 | 当前身份/租户门槛，支持直接打开/刷新 |
| A03 | 安全状态变更 | 暂停/恢复同一能力，原因、期望版本、幂等身份，不任意编辑字段 |
| A04 | 租户成员分页 | 固定目标 Tenant，独立成员查看权限 |
| A05 | 成员只读详情 | 校验 Tenant+Member，读取当前账号/成员状态 |
| A06 | 成员角色授权分页 | 保留未来生效和已失效授权 |
| A07 | 成员权限分页 | 按 Permission 合并来源，解释当前授权状态 |
| A08 | 某权限的来源分页 | 展开时查询，避免每个权限嵌入无限来源 |

旧 Operations 员工/角色 CRUD、PLT-I02、PLT-I03 及审计查询预计保持原有合同。审计展示适配、部署能力目录属于旧端点内容扩展，不算新业务 API。沿用既有 v1/默认 v1，类型名不加 V1/V2。

BFF 有实际组合时在自己的 Processor 组装基础信息和当前选中页；权限生效规则仍由 Platform 计算。不提前查询未打开的成员页、权限来源或审计页。

## 5. 关键实现影响与兼容

### 5.1 操作者与查询目标

请求仍由 Operations Account/Session 发出。BFF 先以可信 Context 调 PLT-I02，Platform 再校验具体权限。URL 目标 Tenant/Member 不能替换操作者 Context。

复用管理策略时，不能把目标 Tenant ID 填入其 targetId 参数：该参数目前按 Operations 账号解析。只复用操作者权限保护，独立校验/锁目标 Tenant，保留旧员工/角色写入口的 INTERNAL 限制。

角色来源允许合法 PLATFORM 角色或同租户 TENANT 角色；核对 Member、Grant 的 tenant_id、Role 的 tenant_scope/tenant_id/scope_key，以及 Grant 冻结的 role_scope_key，不能只凭 role_id 或名称判断有效。OWNER 名称不推导法定主体或合同权利。

### 5.2 状态、并发及重放

本次写资源就是一个 Tenant，暂停/恢复共用目标 tenant_id 的现有 Foundation 资源锁。普通查询不加排他锁，不批量锁成员、全局账号或所有凭证。

事务外准备既有幂等上下文；本库短事务内验证操作者、重验目标类型/版本、CAS 更新安全状态、保存既有幂等结果；提交后尽力审计。数据库保护覆盖租约失效竞争，不新建锁表，也不迁移 Foundation 幂等组件。事务内不调用 CCT、Vault 或其他外部服务。

阶段 3 定义同状态、同键重放、同键异内容、旧版本及反向操作响应。特别是“暂停→恢复后重放旧暂停”不能再执行一次暂停，重放仍检查当前权限。来源更新目前没有生产入口，本期保护既有来源字段/版本，不顺带实现 PLT-I17。

### 5.3 权限解释

单次查询固定时刻，授权期遵循 [effective_from,effective_to)，COUNT、分页与生效状态一致。同一权限至少一条完整有效来源才有效；一条失效来源不能覆盖另一条有效来源，也不能过滤失效记录后说“没有授权”。

合法失效记录可查；缺记录、跨租户关联或未知状态不得判为有效，损坏数据的错误与可读原因在阶段 3 定义。一致性快照不代表多次分页永久冻结历史，刷新允许反映变化。

账号临时登录锁定、Session、Route、具体业务资源校验属于不同层。本期查询 IAM 授权事实和已知限制，不复制登录认证或模拟下单；存在权限不承诺业务可调用，无对应 Route 也不能据此删除角色授权事实。

### 5.4 已有消费者与未来能力

PLT-I03 在只读快照中读取 Credential/Tenant/Scope，Gateway Credential Client 零缓存/零重试。提交后开始的新认证会读取安全暂停，恢复无需重启或刷新路由。提交前已读快照的在途认证/业务请求不承诺追回，异步任务不属于入口开关。

CUSTOMER 登录/Session/切租户及 CCT Provision/Status 生产接口不在当前 Platform API 范围。本期通过真实管理 API 和 PLT-I03 验证安全开关，成员查询使用本地合成 IAM 数据；不追加客户登录链、伪登录或默认通过。未来入口消费同一安全状态，无需迁移本期数据。

### 5.5 页面能力与权限

Web 当前使用 React、Ant Design、React Router；菜单实例来自数据库，组件实现由 Web 挂载。新增页面同步 Web pageCapabilities.ts 和 Platform console-resource-capabilities.json，这是制品页面能力声明，不是 Gateway 业务路由名单。

阶段 3 明确租户查看、成员权限查看、安全管理之间的页面前置与只读 Header。租户查看不能顺带取得成员数据，成员查询失败不能显示为“没有成员”。审计复用对象筛选和自身查看权限，补安全状态/操作文案。

## 6. 验证范围和工作量依据

| 验收 | 后续验证入口 | 覆盖差异 |
|---|---|---|
| AC-01/02、F-01/02 | Platform HTTP+本机 MySQL；BFF/Web | 两类租户、来源/安全限制、中文筛选、总数、空页、INTERNAL 拒绝 |
| AC-03/04/06、F-03/07 | Platform HTTP+本机 MySQL/Redis/幂等/审计 | 无权限、原因、版本竞争、同键重放、反向操作、只改目标安全字段 |
| AC-05 | 真实管理命令→PLT-I03 | 暂停前通过/后拒绝，恢复仍检查其他条件；其他 Tenant、撤销/过期凭证正确 |
| AC-07/08、F-04/05/06 | 成员/角色/权限查询公开入口 | 多租户、多来源、未来/过期/撤销/停用、错误范围、异常数据、分页 |
| AC-09/10、UI-01–UI-06 | BFF Simulator 后接本机真实链；Web 检查与浏览器 | 无成员写入口、脱敏/错误、按需查询、只读页、确认、回退/筛选、长文本、空/错状态 |

已有 [PLT-I03 测试](../../../paytech/platform-support/paytech-platform-support-test/src/test/java/com/paytech/platform/support/auto) 覆盖非 CLIENT、来源/安全暂停拒绝，但用夹具修改状态，不能替代本期管理命令后的认证测试。本阶段仅读测试，未跑 Java 或应用功能回归。

工作量主要在授权来源/原因查询、操作者与目标隔离、状态命令并发和三应用集成，无需新 IAM 存储或执行引擎。合成数据需覆盖两租户类型、同账号多租户、不同授权状态及可验证 CLIENT 凭证；test 是否创建、具体内容和清理边界在阶段 6 列清。

## 7. 阶段 2 已确认及后续入口

用户最新“可以”确认 3 应用、约 8 项 API、0 新业务表/字段的分析，以及成员显示/搜索使用脱敏邮箱和账号 ID。已同步 requirement.md 的 F-04，不新增姓名或明文邮箱搜索。

后继 [阶段 3](./design.md) 提供精确接口、0 DDL、105 行禁用登记 SQL、Pencil 八画板、研发/验证计划。阶段 2 的“预计”“待阶段 3”保留为当时分析口径，精确数量以阶段 3 为准；未进入研发或环境写入。

## 8. 证据入口

- E01：[Platform 数据库](../../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md)、[统一人工身份设计](../../../docs/superpowers/specs/2026-08-30-platform-unified-human-authentication-design.md)。旧物理 CHECK/FK 表述服从 [现行数据库规范](../../../agent-docs/DATABASE_DESIGN_GUIDE.md)。
- E02：[PDManer](../../../paytech/platform-support/docs/database/platform-support.pdma.json)、[Schema](../../../paytech/platform-support/docs/database/sql/platform-support-schema.sql)、[只读观察](./assets/stage2/database-observation.json)。
- E03：[旧管理 API](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/operations/OperationsManagementApi.java)、[目录 SQL](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/resources/mapper/access-control/OperationsDirectoryMapper.xml)、[人工授权 API](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/authorization/OperationsAuthorizationApi.java)。
- E04：[管理策略](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/OperationsManagementPolicyServiceImpl.java)、[凭证管理 Processor](../../../paytech/platform-support/paytech-platform-support-core/src/main/java/com/paytech/platform/support/service/processor)、[事务执行器](../../../paytech/platform-support/paytech-platform-support-core/src/main/java/com/paytech/platform/support/service/processor/OperationsTransactionExecutor.java)。
- E05：[PLT-I03](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl)、[凭证查询](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/resources/mapper/access-control/ApiCredentialManagementMapper.xml)。
- E06：[BFF 拦截器](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/ext/rest/OperationsRequestInterceptor.java)、[BFF 职责](../../../paytech/operations-console-bff/README.md)。
- E07：[审计决定](../../../agent-docs/decisions/2026-09-05-platform-controller-best-effort-audit.md)、[现有命令审计](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/ApiCredentialManagementServiceImpl.java)。
- E08：[动态路由](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/routing/DynamicRouteWebFilter.java)、[Credential Client](../../../paytech/api-gateway/paytech-api-gateway-app/src/main/java/com/paytech/gateway/security/clientapi/PlatformClientApiCredentialValidationClient.java)。
- E09：[Web 挂载](../../../operations-console-web/src/navigation/ConsolePage.tsx)、[页面能力](../../../operations-console-web/src/navigation/pageCapabilities.ts)、[审计展示](../../../operations-console-web/src/pages/auditView.ts)。
- E10：[认证测试](../../../paytech/platform-support/paytech-platform-support-test/src/test/java/com/paytech/platform/support/auto)、[资源级锁](../../../agent-docs/plans/2026-09-04-platform-resource-lock-refactoring.md)、[Foundation 资源锁](../../../agent-docs/decisions/2026-09-05-redis-first-resource-lock.md)。
