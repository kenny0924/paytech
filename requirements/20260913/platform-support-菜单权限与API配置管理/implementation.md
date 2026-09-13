# 研发与开发回归计划（已确认）

本文件是已确认的实施计划，不另记运行状态。用户已确认阶段 3 并授权阶段 4，当前进度与实际证据见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-config-management-20260913)；接口、数据、页面分别以 [api.md](./api.md)、[db/README.md](./db/README.md)、[ui.md](./ui.md)为准。每个交付阶段仍停下确认，阶段内不对普通可逆代码编辑重复询问。

## 1. 研发顺序

1. **固定基线及最小失败证据。** 记录三个应用和 Gateway 的当前 HEAD/未提交差异，保留其他任务 Channel 命名修改；检查已注册 Header/Query/Guard 数量、资源树及能力清单是否符合新约束。先从现有公开 HTTP 测试入口补充缺失操作/首次授权失败场景，确认失败原因是缺功能。
2. **Platform 核心。** 扩展 GatewayConfigurationManagementApi/Service/Processor/Repository，增加 Console Resource、Permission 两个有实际职责的 Service 接口及 Impl。写 DTO 统一 RequestBody/ResponseBody，无版本类后缀。补全完整聚合保存、selector/capabilities、树与引用校验、内置管理保护和审计 Fact。复用 Transactions/ResourceLock/OperationsCommandIdempotencyService，不造通用 CRUD 引擎。
3. **闭合人事角色授权。** 同一政策函数服务目录查询和命令写入，覆盖 api.md 中 15 个 Platform 既有端点；显式首次授权资格、INACTIVE 历史关联、内置角色及最后管理员规则一起回归。HMAC/Vault 预处理在事务外；事务内不能经间接方法调用 Redis/HTTP。
4. **BFF。** 增加 18 个转发端点及 typed client、Problem 字段路径映射；保持 PLT-I02/Origin/可信上下文。A18 按 usage 的授权交由 Platform 并验证不能绕过，不把 HUMAN_AUTHENTICATED 当最终授权通过；角色权限树沿用现有 BFF 组装。
5. **Web。** 按 Pencil 完成两项新菜单、资源树/权限页、Target/Route 编辑和只读详情；实现同一能力注册映射、动态 options、完整子集合编辑、版本冲突草稿、browser history、权限刷新。保持风险页面行为。
6. **材料同步与开发回归。** 唯一初始化 DML 合入 227 行禁用注册，需求/权威入口同步实施证据。零 DDL；如研发发现确有结构缺口先提供具体差异，不静默新增字段。确认所有相称检查通过后交付阶段 4 结果，等待进入阶段 5 综合验证与本地验收。

## 2. 场景与证据

| 入口/场景 | 必须证明 | 对应验收 |
|---|---|---|
| Platform Target/Route HTTP | 六类创建编辑、完整回读、状态不偷改、停用配置完整校验、Scope/Permission XOR、无效矩阵拒绝 | AC-07/08/09/10/13 |
| 真实本地 DB/Redis 并发 | 相同 Code/Method-Path 竞争，子项失败全部回滚，CAS 失败不覆盖，同 key 同/异 payload，Redis 不可用，Lease 过期重入，树互挂竞争，权限停用与授权/引用并发 | AC-02/06/10/11 |
| Resource HTTP | 未知组件/按钮、父类型/跨 Channel/循环拒绝，目录/菜单切换含子项，停父保子状态；管理树与当前导航分别读取 | AC-02/03/06 |
| Permission+Role+Account HTTP | 创建不获权，管理员首次授予自定义角色再分配账号；普通管理员不能转授未持有权限；角色详情/删除/启用员工不绕上限；INACTIVE 保留/移除/恢复；系统保护直接调用拒绝 | AC-01/04/05/06/12 |
| BFF HTTP | 每个新路由转发、字段/Query/头、Problem/Request ID、幂等key不丢、无令牌泄露、A18 usage 无权拒绝、Role options 不漏新权限 | AC-01/11/13 |
| Web 浏览器 | 已授权/只读菜单，Target/Route 一套六类表单，Header/Query/Guard 全量保存，详情无保存，筛选/深链/回退、草稿和权限变更 | UI-01..06 |
| Gateway 本地集成 | 既有快照消费代码下新增从未硬编码过的 Operations Target/Route 可真实转发；一次请求绑定同一快照，非法候选保持上一可用配置 | AC-07/08/14 |
| 既有功能回归 | 人员、角色、登录、导航、审计、原配置启停及风控管理仍可用；旧客户端读取新增字段兼容 | AC-13 |

Java 使用项目 auto 入口 → 真实本服务链路 → local-environment MySQL/Redis；外部 HTTP 仅按已批准的测试侧 Simulator 合同，禁止生产 Mock、Mockito 方法替换、Testcontainers 和应用自动建表。各任务使用独立测试数据/命名空间，复用已有测试切片而不让所有测试共用清空库。

计划执行相应应用的 Maven 测试目标、Web typecheck/build 与相关浏览器场景；以当时 pom/package.json 的实际脚本为准，不假定所有仓库同一命令。Root 文档/全局目录使用 `npm --prefix requirements-site test`。只扩大测试到新增变化或未解决失败所需范围，记录实际命令/结果，不以源码中存在测试当作通过证据。

## 3. 后续阶段与发布边界

- 阶段 5 按上方场景矩阵综合验证与本地验收，复用适用于最终代码的开发证据，不为流程重复跑无关测试；完成后停下确认。
- 阶段 6 准备具体制品、环境 DML/激活清单和共同 Channel Wire 基线，说明部署顺序。本轮获准纳入的 Foundation HTTP 客户端 404 修复也必须列入依赖制品清单；本地 Maven install 不等于对外发布。Platform API/BFF 依赖同步升级，Web 后发布，新入口注册先禁用。
- 本期无 Gateway 必需生产修改，因此不会为“凑齐四系统”发布 Gateway；只有具体回归证明需要改它时补充影响说明。数据库全量动态路由机制仍必须验收。
- 阶段 7 按用户确认执行 test 部署/SQL/激活及环境验收；当前只是准备方案，不能借本次“可以”执行。业务验收方式按本次具体发布授权确定，不擅自增加大量备份或测试数据改写。完成后请用户验收和确认提交范围。
- 阶段 8 按明确请求提交与归档本需求变更。只提交已核对的路径/差异，不夹带同时进行的支付命名或其他系统改动。
