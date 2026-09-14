# Client API 查看与管控：阶段 3 实施方案

状态：用户以“可以，没啥问题”确认本方案并授权阶段 4；实际研发、验证与环境状态见 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913)。职责和验收以 [requirement.md](./requirement.md) 为准，逐接口见 [api.md](./api.md)，数据见 [data-design.md](./data-design.md)，页面见 [ui.md](./ui.md)。

**一句话规模：3 个应用新增 18 个端点（Platform/BFF 各 9）、调整 8 个既有端点（各 4），模型新增 1 表/7 字段/1 主键/1 唯一索引并在 2 张旧表各加 1 个查询索引，旧业务字段增加 0；test 另补两张原设计凭证表，共需建 3 表/28 字段；注册 DML 为 127 条 INSERT 和 2 条既有 Route 记录更新，不含启用、客户或凭证示例。**

## 1. 调用与模块归属

浏览器 → Gateway 动态 Operations Route → Operations BFF → Platform api/web → access-control；操作记录由 audit-security 拥有。本期只增加既有模块内的业务能力，不拆新 Maven 服务、不调用 CCT 获取名称、不扩展 Client Console。

| 层 | 研发改动及消费者 |
|---|---|
| platform-support-api | 增加 ApiCredentialManagementApi/Models 与 ApiScopeManagementApi/Models，维护 api-credential-admin / api-scope-admin 的 v1 HTTP 合同；改 Route 与审计查询合同；现有 PLT-I03 不变 |
| Platform web | 新建两个有实际事务/幂等职责的 ManagementProcessor、对应 Controller；复用 OperationsTransactionExecutor、OperationsCommandIdempotencyService、当前审计注解；Scope/凭证写请求使用专用轻量响应，不伪造 Scope 状态 |
| access-control | ApiCredentialManagementService + Impl 负责读取/撤销，ApiScopeManagementService + Impl 负责目录；Repository + Impl/Mapper 按真实用例组织，管理投影与认证 PHC 候选分离；Route 服务增加目录新绑定校验与查询过滤 |
| audit-security | 复用 ConfigurationChanged 的安全字段差异，补 DESCRIPTION 受控字段及三个命令的动作/对象；审计过滤增加 resourceType/resourceId，不新建历史表 |
| Operations BFF | 增加两组轻量 Client/Controller/DTO；只有 A06 使用 ApiScopeDetailsProcessor 组合目录与选中引用页；独立调用仍分别由 Owner 鉴权；错误/Origin/严格 Query/Body 合同沿用当前做法 |
| Operations Web | 新 api-credentials、api-scopes 页面与数据适配；Route Scope 选择器/筛选、审计对象筛选；对应页面能力与动作注册；保持历史导航、草稿、长文本省略、统一菜单 |
| API Gateway | 无业务代码改动；9 条新 Route 通过数据库配置加载，A09 直接绑定既有路由 READ 权限；无新 HUMAN_AUTHENTICATED 白名单、无撤销缓存/广播 |
| 模型与工件 | 阶段 4 从根 scripts/pdmaner/platform-support 的 Manifest/Policy/Oracle 生成唯一模型，更新服务完整 Schema/单一初始化 DML及本需求增量 SQL；实际生成与环境应用状态以 CURRENT_IMPLEMENTATION 为准 |

BFF 不把凭证 Query DTO 转成 PLT-I03 的 secret-bearing 认证输入；不预取全部目录或所有客户记录。Platform 的同模块业务 JOIN 可返回 Credential/Tenant 名称和授予 Scope 的目录名称，跨权限引用页由 BFF 组合。审计按已有独立 API 查询，不由 access-control 直读 audit 表。

## 2. 并发、授权与幂等

| 操作 | Redis 资源锁 | 本库最后保护 | 操作类型 / Hash 命名空间 |
|---|---|---|---|
| 创建 Scope | `api-scope` + scopeCode | uk_api_scope_code 防重复；生成 Snowflake，version=1 | CREATE_API_SCOPE / operations-create-api-scope |
| 修改 Scope | `api-scope` + scopeCode | UPDATE WHERE api_scope_id AND version；version+1 | UPDATE_API_SCOPE / operations-update-api-scope |
| 紧急撤销 | `api-credential` + credentialId | 锁定本条 Credential，WHERE credential_id AND version；保留子表 | REVOKE_API_CREDENTIAL / operations-revoke-api-credential |

Hash 原文固定为有类型 Command 的 JSON 字段：create(scopeCode,displayName,description,reason)，update(scopeCode,displayName,description,expectedVersion,reason)，revoke(credentialId,expectedVersion,reason)。使用当前 ObjectMapper/既有 HMAC 准备方式，不在本期引入 Foundation 通用命令幂等迁移或另一种 Canonical 协议。CallerScope 仍从当前 actorId 生成，OpaqueReferenceNamespace=null；命令类型可区分 same-key 的不同动作，同动作不同对象包含于 Hash。

执行顺序：解析/校验输入 → Foundation @ResourceLock → 事务外准备 HMAC → OperationsTransactionExecutor 短事务 → 当前普通会话及业务权限复核 → 幂等查重 → 查询目标/CAS → 完成幂等 → 提交 → Controller 独立尽力审计。事务内只访问本服务数据库；HMAC/Vault 等外部调用不能藏在事务内。写权限被收回时，重放也拒绝。

Scope 的代码不可变且无删除，Route 读取目录后不会遇到目录身份被撤销，不需要为 Route 保存同时取得 Scope 目录锁。新增尚未提交的目录不视作存在；用户先保存 Scope 后再选 Route，不能用默认通过处理目录不可用。Scope 唯一键和版本保护 Redis Lease 到期后的写入，凭证用行锁/CAS，复用当前有限 Wait/Lease 配置；不新增 Tenant 总锁。

沿用 `t_command_idempotency` 已有 requestHash + responseReference：Scope 引用只需 `API_SCOPE:<id>:<version>`，code 由匹配原请求获得；撤销只需 `API_CREDENTIAL:<id>:REVOKED:<version>`。满足现有 255 字符引用上限，不保存完整对象/PHC，也不增加回执表。错误不保存成功回执；同 key 不同 Hash 拒绝。完整成功响应由该固定引用重建，不回查后来已变化的版本。

## 3. 撤销生效与旧数据

本期不改变认证协议。PLT-I03 每次在自己的只读一致性事务检查 Credential/Tenant/有效 Scope，事务结束后进行 PHC 校验；Gateway 每次 Client API 认证调用它。撤销提交后才开始的新认证应失败，之前已读取快照的在途认证/请求仍可能完成，不增加 ACK 或强制中断。

ACTIVE 和已过期 Credential 均允许撤销；CLIENT Tenant 停用/终止不会使后台查询或撤销消失。授权行保留，其有效时间/状态与父凭证状态分开展示。根状态 REVOKED 时查询可显示“授权记录仍保留”，不能把剩余 Scope 标成当前可调用。

Route 新建/新绑定必须精确匹配 t_api_scope.scope_code；旧 API_SCOPE + 同一编码的更新、启停及 Snapshot 读保持兼容。旧未登记 Scope 显示编码，名称空，不默认补目录、更换大小写或授予权限。有存量环境先人工审核孤立/冲突代码；无目录的合法 Grant 仍按原 PLT-I03 语义认证。本期不启停/删除目录，不把目录变成第二个运行鉴权来源。

## 4. 审计与可见性

命令分别声明 `OPERATIONS_API_CREDENTIAL_REVOKE`、`OPERATIONS_API_SCOPE_CREATE`、`OPERATIONS_API_SCOPE_UPDATE`；资源凭证为 API_CREDENTIAL + credentialId，目录为 API_SCOPE + scopeCode。复用既有 beforeVersion/afterVersion、reason、actor/session/requestId 和 ConfigurationFieldChange，不新增请求正文审计或全部 DTO 序列化。

撤销只记录 STATUS；目录只记录 CODE、DISPLAY_NAME、DESCRIPTION 及版本。原因和说明各不超过 500 字符，满足既有单属性 1000 字符 / 4096 字节上限；仅投影这三项目录字段及版本，远低于 256 个属性预算，不新增截断策略。未实际改变的重复撤销不编造 before/after；同 key 重放沿用既有审计约定，不宣称每次都有一条可靠历史。审计失败不回滚业务、不给用户显示伪造的“已记录”。

Web 凭证/Scope 详情“操作记录”复用原审计接口，独立要求 OPERATIONS_AUDIT_READ；没有权限隐藏标签，直接调用仍拒绝。A06 只请求当前选择的引用类型；额外权限失败明确 403，不吞成空集合。关联 API 不代表目标可用、Scope 已生效或业务资源授权成功。

## 5. 页面能力与注册

两个新 componentKey：OPERATIONS_API_CREDENTIALS → /api-credentials；OPERATIONS_API_SCOPES → /api-scopes。按钮资源各一个：OPERATIONS_API_CREDENTIAL_REVOKE、OPERATIONS_API_SCOPE_MANAGE；新增/编辑共同使用后者，不为每个链接创建权限。挂在已有 OPERATIONS_PLATFORM_CONFIGURATION 下。

注册 SQL 只登记四个权限、四个菜单/按钮、九条 Operations Route 及 Header/Query/Guard。新 Route/资源先 DISABLED，四条 PLATFORM_ADMIN 关联先 REVOKED；不默认给其他角色授权。发布准备阶段核定操作者、角色当前版本及正式启用 SQL（9 Route + 4 Resource + 4 Grant，并推进 Role 版本）；该执行需另行确认。

原 Route 列表新增 1 个 Query、审计列表新增 2 个 Query，并在同事务更新这两条 Route 的版本；源码部署前不提前开放这些新增 Query。新 API_SCOPE 业务 Route/客户凭证不属于该注册 SQL，目录起始为空，可在管理 UI 由运营登记真实合同中的 Scope。

## 6. 研发与验证计划

遵循 [测试规范](../../../agent-docs/UNIT_TEST_GUIDE.md)，阶段 4 先构造最小失败证据，再完成三应用实现；本需求文档检查不冒充 HTTP/数据库回归。

| 用例组 | 必须验证的对外行为 |
|---|---|
| 查询与秘密隔离 | A01 筛选及 total 一致，inactive Tenant/过期/撤销可读；ID 边界、code 大小写、关键字转义；Response/日志/审计无 PHC 或完整 Credential；多 Grant 的凭证引用不重复 |
| 权限与 BFF | READ、REVOKE、MANAGE、Route READ、Audit READ 分离；A09 只有 Route READ 可用；A06 NONE/ROUTE/CREDENTIAL 返回 shape/403 正确，BFF 保留真实上游错误，不以未授权空页替代拒绝 |
| 目录写入 | 编码重复、名称同名允许、description 清空、unknown field、同键重放/异内容、旧版本；目录改名不改授权或 PLT-I03 Scope 集合 |
| 撤销与认证 | 真实 MySQL + PLT-I03 HTTP 撤销前认证成功、提交后失败；其他凭证/轮换来源/Grant 不变；并发撤销、Redis Lease/失败、同键异内容、权限收回、EXPIRED/失效 Tenant 均按合同 |
| Route 兼容 | 新/改绑未知 code 拒绝；原未知合法 code 保留/启停/Snapshot 可读；新目录补齐后可选；现有非 API_SCOPE Route 完整 Header/Query/Guard 不被改坏 |
| 审计 | resourceType/resourceId 精确过滤与权限；完整 reason 与允许字段可查；审计依赖故障不改变已提交业务；无第二套变更历史 |
| Web | 页面状态/长文本/操作菜单/撤销弹层/浏览器回退/草稿保护/局部错误；选择器分页搜索；生产构建；后续浏览器真实链视觉验收 |
| 模型与 SQL | Manifest/Policy/PDManer/Schema/增量一致，3 表补齐前置、7 新字段、2 查询索引和127注册行；本机真实 EXPLAIN 验证索引访问路径，不借空 test 数据声称性能通过 |

应用 auto 只用 local-environment。若本机需要补新目录表/索引或旧缺表，先准备目标与精确 SQL 单独申请；不得让测试或启动脚本建表。test 用户样例使用单独合成清单和受控临时秘密，发布前再确认，不与初始化权限/路由 SQL 混合。

## 7. 阶段交付边界

阶段 3 的交付为本方案、接口、数据/SQL 与页面；其设计确认已授权阶段 4 研发及开发回归。SQL 手工应用、制品发布、部署与 Git 提交仍遵循各自明确授权；发现超出三个应用的新能力或必要修复时说明具体范围。执行证据见 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913) 与 [交付记录](./delivery.md)。
