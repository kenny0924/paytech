# 菜单权限与 API 配置详细设计

阶段 3 评审稿，尚未授权研发。承接已确认的[范围](./requirement.md)和[差异分析](./technique.md)。接口见 [api.md](./api.md)，数据见 [db/README.md](./db/README.md)，页面见 [ui.md](./ui.md)，研发顺序见 [implementation.md](./implementation.md)。当前实现状态只见[台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-config-management-20260913)。

**一句话总结：本次方案涉及 Platform、BFF、Web 三个应用，新增 18 项能力/36 个落地端点，既有合同或授权行为调整为 Platform 15 个、BFF 14 个端点；复用七张配置表，新增/修改表结构、字段、索引均为 0，准备 227 行注册 INSERT（7 张表），新增 2 个功能页面并扩展 API 管理页，共 7 张 Pencil 画板。**

## 1. 分工与保存链路

Operations Web 管理四类对象；BFF 适配浏览器合同并调用 PLT-I02，Platform access-control 拥有目录、引用、权限和配置校验。BFF 的现有角色权限树继续负责组装；新引用页按标签分别调用有界查询，无需新增空聚合层。

Platform 使用现有 api/access-control/web/audit-security/core 模块。Gateway 继续消费 PLT-I20 完整快照，不增加业务 Target 名单，不新增发布接口。Framework/Foundation 暂无生产代码变更；不切换到 Commerce 专属的新命令幂等 JAR。

保存顺序：协议校验 → Redis 资源协调 → 事务外准备规范化 Hash/HMAC → 本地事务内重新鉴权、幂等核对、读取当前事实、候选校验、CAS/写入、审计、幂等完成 → 提交 → 释放 Redis 锁。现有幂等 prepare 会间接调用 Crypto/Vault HTTP，必须在 TransactionTemplate 之前完成，不能因方法名像本地计算而放进事务。事务内仅访问 Platform 本库，编译候选为纯内存计算；Gateway 刷新独立执行。所有 Route 子项整体替换并随主记录同事务提交。

普通管理列表/引用不新开显式事务；既有详情一致性读取仅在返回主记录和子项必须来自同一版本时保留。完整 Snapshot 和候选写入继续使用已有一致性边界。

## 2. 四类对象的编辑规则

| 对象 | 创建 | 可编辑 | 固定身份 |
|---|---|---|---|
| Target | Code、Channel、Discovery；状态固定 DISABLED | Discovery；启停独立命令 | Code、Channel、ID |
| Route | 完整 Root + Header/Query/Guard；固定 DISABLED | Method、两个路径、同 Channel Target、安全/授权/传输配置及子项 | Code、Channel、ID |
| Console Resource | MENU/BUTTON、编码、名称、父级、组件、权限、排序；固定 DISABLED | 名称、父级、排序、可见性权限；页面组件受能力清单和子项约束 | Code、Channel=OPERATIONS、MENU/BUTTON 身份、ID |
| Permission | 编码、资源类型、动作、风险；固定 INACTIVE | riskLevel；启停独立命令 | Code、resourceType、actionCode、ID |

PUT 不携带 status，状态另行修改；不会因为编辑保存自动启用。无变化的 PUT 返回当前版本、不追加重复审计；有变化时版本加一。身份改动请求在字段层拒绝，不偷偷忽略。

不新增物理删除、历史版本表、审批、发布、回执或 Scope 字典。对有引用的停用给予影响提示，保留引用关系；受保护内置对象见第 5 节。

### 2.1 资源树和页面能力

- 纯目录是 MENU 且 componentKey/frontendRoute 同为空；页面 MENU 的组件与路径同时有值；BUTTON 无路径/组件，必须归属页面 MENU。页面 MENU 只可拥有 BUTTON，目录 MENU 可包含目录或页面；根只能是 MENU。
- permissionId 必填，包括目录。父级存在、同为 OPERATIONS、不能为自身/后代；排序为 0..999999，同序按 resourceId 稳定排序。树深度最多 8，资源总量最多 2000；超过上限明确拒绝，管理树不悄悄截断。存量违反新结构规则先列出待修复项，不能凭空修改。
- 前端路径从选定组件确定，不开放任意字符串。现有九个 component key 加本期两个页面共 11 个页面能力。页面和按钮清单通过 A17 获取，Platform 的小型 JSON 资源随应用制品交付；不建能力表、不读 Web 文件系统。
- 能力清单的变更必须随实际页面/事件代码交付。Web 保留真实组件及 action handler 的注册映射，菜单过滤和 App 挂载共用它；构建回归检查它与 A17 清单一致。清单是部署能力说明，数据库拥有实例、名称、层级、排序、权限和状态。两者不互相伪装成业务事实。
- BUTTON 从该页面已支持的 action resourceCode 选择，现有代码按此编码识别操作；未知编码/错父组件由 Platform 拒绝。一个已注册按钮编码只有一个实例。页面换组件或转目录时有任何子项则拒绝，先人工移动子项；不能让已有按钮失去处理代码。
- 已部署组件暂时从新版清单移除时，保留旧实例可读，标记不受支持；禁止新绑定或启用，只能改为合法配置或停用。不靠批量删除“修复”历史。
- 目录可见性继续要求当前人具有目录权限。角色配置必须显式包含所需父目录权限；当前“平台配置”父项使用 GATEWAY_CONFIG_READ，本期不自动修改此关系或静默授权。

### 2.2 六类 Route 配置

| Channel | Profile | Authorization Mode |
|---|---|---|
| OPERATIONS | PUBLIC / OPERATIONS_JWT | PUBLIC / HUMAN_AUTHENTICATED / HUMAN_PERMISSION（按 Profile 成对匹配） |
| CLIENT | PUBLIC / CLIENT_JWT | 同上 |
| PARTNER | PUBLIC / PARTNER_JWT | 同上 |
| CLIENT_API | CLIENT_API_CREDENTIAL | API_SCOPE |
| CHECKOUT | CHECKOUT_SESSION | CHECKOUT_RESOURCE |
| CHANNEL_CALLBACK | CHANNEL_CALLBACK | CHANNEL_CALLBACK |

A16 从现有编译器合同导出合法组合、方法、Guard/Cookie 约束与容量上限，Web 不复制一份可漂移矩阵。HUMAN_PERMISSION 仅 permissionId，API_SCOPE 仅 requiredScopeCode，其他模式两者均为空。Scope 按已有业务合同填写，不查询 Credential 授权集作为字典。

沿用现有模板编译器：方法集合 GET/POST/PUT/PATCH/DELETE/HEAD；路径大小写敏感，变量名集合一致，无主机/Query/Fragment/通配。**Method + 外部路径唯一性是全局的，不按 Channel 拆开**；有效模板歧义也全局拒绝，固定路径优先规则保持。DISABLED 仍要求完整合法；仅有效路由参与模板重叠判断，重新启用时再编译全量有效候选。

新绑定 Permission 必须 ACTIVE；存量绑定权限被停用后允许读取、保持原绑定及修正其他字段，不能把停用权限新绑给另一资源/Route。Permission 停用不会删掉 Snapshot 所需的身份投影，授权端读取当前状态拒绝请求。

公共容量上限沿用当前实现：Body ≤65536 bytes，接收超时 ≤5000ms，响应 ≤10000ms，Target ≤128，Route ≤4096；Rate capacity/refill ≤600、period ≤60000ms、concurrency ≤50，正数/零值条件由 A16 显式给出。SHARED_ROUTE 必须 Rate/Concurrency 成对；Operations JWT 还必须 CLIENT_IP 和 AUTHENTICATED_PRINCIPAL 两对；TENANT 若使用必须成对。PUBLIC/CHANNEL_CALLBACK 不允许 Principal/Tenant。禁止可信身份/代理/Header 通配注入。

配置能力覆盖六类，已实现业务认证链仍以实际制品为准。本期真转发验收使用已接通的 Operations；其他四类缺失的认证链不在这里补做，也不能改成 PUBLIC 绕开。Channel 命名以专门决定（跨专题来源：`agent-docs/decisions/2026-09-13-payment-channel-terminology.md`，由该专题单独归档）为准，发布准备必须先核对 Provider→Channel 的制品和数据迁移基线；本需求不暗中承担该迁移。

## 3. 权限目录、选择和首次授权

新增 CONSOLE_RESOURCE_READ/MANAGE、PERMISSION_READ/MANAGE 四个权限。Target/Route 继续 GATEWAY_CONFIG_READ/MANAGE。读写分开，管理界面通常同时授予 READ+MANAGE；写权限不会隐式包含读权限。

权限管理目录能看全部 Permission，包括未绑定、停用及非 Operations 引用；它不等于当前用户持有权限。A18 只返回 ACTIVE 身份选项：usage=GATEWAY_ROUTE 要求 GATEWAY_CONFIG_READ，usage=CONSOLE_RESOURCE 要求 CONSOLE_RESOURCE_READ，不能由调用者改 usage 越权。A18 的 Route 为 HUMAN_AUTHENTICATED，BFF 保持 PLT-I02，Platform 再按 usage 校验对应真实权限；无权限不返回候选。它不返回角色或人员信息。

**首次授权采用现有平台管理员角色的窄扩展：** 当前有效 PLATFORM_ADMIN（systemRole + platformRole + 精确代码，仍需正常 Operations 会话和对应管理权限）可以在已有角色管理中显式分配 ACTIVE 且被 Operations Route/Resource 引用的权限，即使本人尚未持有。引用配置允许为 DISABLED，以便先配授权再启用；不把 CLIENT/PARTNER 独有权限引入 Operations 授权目录。其他管理员继续只转授已持有且符合既有 Operations 范围的权限。

典型顺序：创建停用权限 → 启用权限 → 新建停用 Route/Resource 并关联 → 平台管理员在自定义角色中显式勾选 → 通过既有人事管理分配角色 → 启用配置。内置 PLATFORM_ADMIN 仍不可通过普通角色 PUT 编辑；新建权限不自动加入该系统角色，管理员需要使用该功能时同样显式分配自定义角色。

首次授权资格必须同时用于权限选项、角色详情/创建/编辑/删除，以及创建员工、分配角色、启用员工的授权上限，防止“能创建角色却无法分配给人”。不会把 PLATFORM_ADMIN 改为业务 API 通配授权。对他人已授予角色的重新委派依旧校验每项权限，不能借角色跨域提权。

停用权限的既有 role_permission 保留。角色编辑允许保留或移除已存在的 INACTIVE 关联，但禁止新增 INACTIVE 关联；恢复角色/账号涉及的潜在权限也要校验转授资格，普通管理员仍需持有该权限的未撤销关联，平台管理员仍须满足 Operations 引用范围。权限重新启用恢复原关联，不重写角色、不自动新增持有人。权限创建/启停/角色分配分别记录审计。

## 4. 引用、并发与审计

引用按 Permission ID 查询当前 Resource、Route、Role；A15 一次只查一个 kind，分页最大 100，默认 10。角色只返回角色身份、状态和授权状态，不查询成员/Email；详情页仅在调用者拥有目标详情权限时给出跳转。Target 引用复用已有 routes?targetServiceCode 查询。引用页允许相邻提交视图，不把引用数量作为执行前提；写入时重验当前事实。

| 冲突域 | Redis 协调 | 数据库最终保护 |
|---|---|---|
| Target/Route 创建、编辑、启停 | 复用 gateway-configuration:catalog | 既有 SERIALIZABLE 完整候选读写、业务唯一键和 version CAS；禁止只靠 Redis |
| Resource 树新增/编辑/状态 | console-resource:operations | SERIALIZABLE 读取该 Channel 的父子候选及范围、唯一键、CAS；防并发交叉挂载形成环 |
| Permission 新增/编辑/启停 | permission:{permissionCode} | 唯一 code/resource-action；当前权限行 CAS，事务内重验关联资格 |
| Role/人员写入 | 保留现有 role/account/member 资源锁 | 原有授权角色共享锁与目标角色排他锁；补充 Permission 状态与 Operations 引用资格的锁定读 |

跨目录引用时用 DB 锁定读与确定顺序协调，不把所有人事命令改为 catalog Redis 大锁。统一顺序：已有 actor/member/role 授权锁 → Permission ID 升序共享/排他锁 → 配置行/范围；Permission 状态排他写与引用绑定共享读相互排斥。人员/角色命令保留现有 READ_COMMITTED 事务：先读取可委派权限候选，再按 ID 顺序共享锁定 Permission 及其 Operations Resource、Route/Target 引用行，最后重查资格，并与已经锁定的候选取交集。重查不能授予新出现但未受保护的权限；引用停用或变更提交后必须使用新事实重新判断。配置树和 Gateway 完整候选写入仍使用上表 SERIALIZABLE 边界，不能把该隔离级别扩大到全部人事命令。锁超时/死锁回滚并返回现有 409，不在事务里无界重试；实际并发证据见当前台账。

复用 Platform OperationsCommandIdempotencyService：caller 为当前 actor，operationType 按命令区分，客户端同一次保存重试保持 Idempotency-Key；同 key 不同规范化 payload 拒绝。Hash 包含对象身份、完整可变字段、expectedVersion（修改时）、reason、规范化子集合；不含 Token/Session/Request ID。Header 小写去重并按名称/用途排序，Query/Guard 按身份排序；重复子项直接拒绝。所有新操作使用独立 operationType，不解释旧状态命令 Hash。

完成引用保存对象稳定身份及该次结果版本/状态；重放先重新验证当前会话与操作权限，再返回该次稳定轻量回执，不把已改成第 3 版的对象冒充第 1 次保存结果。新操作的既有 response_ref 使用 `objectType,objectId,objectCode,status,version`，各项不允许逗号，最长不足 200 字符，适配现有 VARCHAR(255)；operationType 区分 Reader，旧幂等记录解释不变。审计记录对象、操作者、前后版本、变化字段/安全配置、原因；新增受控 Fact/Projector，不建审计表，不记录凭据 Header 或完整会话。

## 5. 内置管理对象的维护边界

本节是待本阶段确认的管理防误操作规则，不是 Gateway 转发白名单。由 Platform 根据管理合同和关系判断，Gateway 仍从 DB Snapshot 取全部业务路由。

- 受保护 Route：Operations 登录/刷新/退出、auth context/password/bootstrap、authorization navigation，以及本期前已部署和新增的 `/iam/v1/`、`/gateway-admin/v1/`、`/console-admin/v1/`、`/permission-admin/v1/` 管理入口。匹配实际外部或下游管理路径，编辑前后两份都判断，不能先换路径绕过。
- 这些 Route 不允许在页面改 Method、Path、Target、Profile、Mode、Permission/Scope、Cookie 或停用；只允许在编译器既有上限内修改 Header/Query/Guard/Body/Timeout，且不得移除本合同必需 Header/Query、降低本端点请求体容量至合法请求无法通过。权限选项那个例外仍必须保留 usage 的服务端鉴权。
- 被受保护 Route 引用的 Target 不允许停用或改 Discovery。确需迁移 Operations BFF 服务身份时采用单独评审的人工配置变更。
- 受保护 Resource：系统/平台配置目录、Accounts/Roles/Audit/Gateway/本期两个管理页及其管理按钮。允许名称和排序，禁止更换父级/组件/权限、停用。保护清单以现有这些精确组件/action code 及其祖先求闭包；其他风控/业务资源照常编辑。
- 受保护 Permission：以上 Resource 或 HUMAN_PERMISSION 管理 Route 所引用的权限。不允许停用或替换身份；riskLevel 可编辑。系统角色仍按原规则不可改、最后管理员保护保持。
- 返回 `managementProtection{editableFields,statusChangeAllowed,reason}` 给详情和 Gateway 列表；页面禁用相应动作并给原因，直接调用服务端同样拒绝。审计和人工修复沿用现行方式，不增加恢复引擎。

## 6. 本阶段确认内容

确认本稿及接口/SQL/Pencil 后才进入代码研发。需要一并确认的具体选择是：全部对象先存停用态；菜单路径随已支持组件确定；平台管理员显式首次授权但不自动获权；内置管理入口按第 5 节保护。SQL 执行、制品发布、test 部署及提交仍按后续阶段分别处理。


## 内置管理端点的传输维护实现边界

管理 Route 的 Header、Query、Body 下限遵守已确认的“不能移除协议必需项”规则。Platform 使用 `operations/management-route-contracts.json` 保存 43 个已部署管理端点的最小请求合同，并由目录合同测试与初始化数据对齐。该清单只负责拒绝会破坏管理入口的编辑，不提供 Target/Discovery，不用于 Gateway 请求匹配或转发，也不决定 Route 启停；业务 Route 和 Target 仍从数据库完整快照读取。

下限独立于人工编辑后的当前值：额外增加的 Header/Query 后续允许移除，曾调高的 Body 容量允许回调到已部署端点要求的最小值。新增管理 API 时同步其请求合同及注册 SQL，普通业务 Route 不进入该管理保护清单。
