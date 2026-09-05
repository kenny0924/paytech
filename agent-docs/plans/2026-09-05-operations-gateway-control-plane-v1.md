# Operations Gateway 控制面 V1 实施记录

日期：2026-09-05
决策状态：Confirmed
实现状态：Implemented and verified in workspace
部署状态：Deployed to local `mor-test`

## 1. 目标与范围

本切片在 Operations Console 增加“API 路由管理”，用于查询 Platform 当前持有的 Gateway Target/Route 配置，并在显式确认后切换状态。

V1 包含：

- Target 分页、精确筛选、详情和 `ACTIVE ↔ DISABLED`；
- Route 分页、精确筛选、完整聚合详情和 `ACTIVE ↔ DISABLED`；
- Target Kill Switch、Route/Target 有效状态的准确展示；
- Expected Version、Idempotency Key、Reason、资源锁、数据库 CAS 和字段级审计；
- Operations 导航、权限与六条公开 Gateway Route 的受控初始化数据。

V1 不包含 Target 创建或身份三元组修改，不包含 Route 创建、编辑、删除，不包含 Console Resource 管理，也不引入 Draft、Release、Publish、Snapshot、Ack 或 History 表。成功响应只表示 Platform 当前配置已经提交，不表示某个 Gateway 实例已经完成刷新。

## 2. 接口

Platform 内部能力使用 `platform-support-api` 的 `/gateway-admin/v1`：

```text
GET   /target-services
GET   /target-services/{targetServiceCode}
PATCH /target-services/{targetServiceCode}/status
GET   /routes
GET   /routes/{routeCode}
PATCH /routes/{routeCode}/status
```

Operations BFF 暴露相同能力路径；Gateway 对外前缀为 `/api/operations`。列表复用 Foundation `PagingRequestBody` 和 `PagingResponseBody`，Target 支持 `channel/status/targetServiceCode`，Route 支持 `routeCode/channel/status/targetServiceCode`，全部为精确过滤。

Gateway 的静态 `ApprovedTargetRegistry` 已为 `operations-console-bff` 增加 `/gateway-admin/` 下游根白名单。动态 Snapshot 只能在已批准的 Target 身份和该根路径内装载上述六条 Route；该白名单不替代 Route、Guard 或 Permission 校验。

状态命令 Body 为 `status + expectedVersion + reason`，并要求 `Idempotency-Key`。响应返回稳定 Code、最终状态与新版本。

## 3. Platform 分层

```text
GatewayConfigurationManagementController
  -> GatewayConfigurationManagementProcessor
    -> GatewayConfigurationManagementService
      -> GatewayConfigurationManagementRepository
        -> GatewayConfigurationManagementMapper
```

- Controller 只做 HTTP 参数适配、公共分页和 Request ID 获取。
- Processor 管理只读 Repeatable Read 事务、写事务、失败审计和资源锁。
- Domain Service 执行权限重检、幂等、状态规则、候选目录编译、CAS 和成功审计。
- Repository/Mapper 只负责当前配置的稳定查询、Root 锁和 CAS。
- 现有 `GatewayConfigurationCompiler` 是完整候选目录的唯一语义校验器；不复制 Path、Profile、Cookie、Header、Query、Guard 或全局冲突规则。

所有写命令先按固定顺序取得 `gateway-configuration:catalog`，再取得实际资源锁：Target 使用
`gateway-configuration:target:{targetServiceCode}`，Route 使用
`gateway-configuration:route:{routeCode}`。Catalog 锁序列化需要读取和编译完整候选目录的写事务，资源锁保留实际
Root 的并发身份；两层 Redis 锁均包住完整数据库事务，数据库再通过 Root `FOR UPDATE` 和 `version` CAS 提供最终
保护。查询不加分布式锁。

启用 Target 时将该 Target 下所有 ACTIVE Route 放回完整候选目录编译；启用 Route 时编译包含该 Route 的完整候选目录。停用同样编译变更后的完整目录，避免保存损坏或歧义的全局配置。

## 4. 权限、幂等与审计

- `GATEWAY_CONFIG_READ`：Target/Route 查询与详情；
- `GATEWAY_CONFIG_MANAGE`：Target/Route 状态切换；
- Platform 再次基于当前 Operations Session、Member 和 Permission 重检授权；
- BFF 仍先经过 PLT-I02 Route 权限校验，页面按钮不是授权依据；
- 状态命令复用 `t_command_idempotency`，同 Key 同规范载荷返回原结果，同 Key 异载荷返回并发冲突；
- 成功审计记录 Resource Type、稳定 Code、Reason、`before.status`、`after.status`、`before.version` 和 `after.version`；
- 明确失败由 Processor 使用现有公共异常和失败审计边界处理，不新增逐方法 catch。

## 5. BFF 与前端

V1 每个浏览器请求只依赖一个完整 Platform API，因此 BFF 使用独立 Controller、Client Port 和 Client Adapter 做本地 DTO 隔离、响应校验与页面模型适配，不增加只转发的 Processor。Route 详情由 Platform 一次返回 Root、Target、Permission、Header、Query 和 Guard 聚合。

前端 `/gateway-configuration` 页面使用两个 Tab：Route 为默认页，Target 为第二页。两页复用公共分页、错误通知和 Session 请求；详情使用只读 Drawer；状态切换使用独立确认 Modal，Reason 必填。写请求不做乐观更新、不自动重放，网络/503 的人工重试保留同一幂等键，输入、目标或版本变化后生成新键。

Target 列表和详情明确区分 Root 状态；Route 同时展示 Route 状态与 Target 状态，避免把 Target 已停用的 ACTIVE Route 显示为正在运行。

## 6. 数据与发布边界

现有六张控制面表无需 Schema 或 PDManer 变更。初始化 DML 增加：

- 两项 Permission 及系统角色授权；
- “API 路由管理”菜单和“管理配置”按钮；
- 六条 Operations Route、Header、Query 和 Guard 子项。

共享 test 环境使用单独、可审查的增量 SQL。2026-09-05 收到明确部署要求后，该 SQL 通过 MySQL batch 模式
显式执行；只定向更新 Platform、Gateway、Operations BFF 和前端，没有重放全量初始化或 Bootstrap。

## 7. 验证

- Platform `clean verify` 通过 138/138，分支覆盖率 90.097%、行覆盖率 96.249%：真实 HTTP → Controller →
  Processor → Domain Service → Repository/Mapper → 本地 MySQL，并使用本地 Redis 验证两层资源锁、幂等、CAS、
  全局候选冲突、Permission 失效关闭、损坏回放拒绝、真实数据库锁超时转换与审计；
- Operations BFF `clean verify` 通过 297/297：真实 HTTP → PLT-I02 → `@HttpExchange` → 有状态 Platform
  Simulator，包含分页总数与页窗口完整性、状态版本严格递增和 1000 字符 Path 上限；
- Operations Web 测试通过 88/88，生产构建通过，覆盖筛选/分页窗口完整性与安全整数边界、菜单、详情校验、
  状态命令与人工重试幂等语义；
- Gateway 的 `/gateway-admin/` 白名单聚焦测试通过 30/30；完整 Gateway `clean verify` 仍有未触及的 transport、
  health、JWKS 与 Guard 套件 10 个失败和 4 个错误，因此不声明完整 Gateway 门禁通过；
- Break-glass 静态合同测试通过 6/6，覆盖事务隔离、子项范围锁、全目录冲突提交前拒绝和失败必停；脚本未连接数据库、
  未执行任何 SQL；
- Requirements Site 测试通过 127/127；各相关仓库 `git diff --check` 在最终工作树上单独执行。

工作区实现仍未提交。`mor-test` 当前为 27 条 Route、15 个 Console Resource、14 个 ACTIVE Permission；
10 个容器 healthy，Gateway Snapshot 为 1 个 Target / 27 条 Route且连续失败为 0。公开 HTTP 验证通过登录、
导航、查询/详情、未认证拒绝、Route 启停、幂等重放、旧版本冲突以及 Snapshot 移除和恢复；验收 Route 已恢复 ACTIVE。
