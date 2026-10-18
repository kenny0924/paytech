# 阶段 4：Client API 管理研发审阅入口

已确认范围见 [design.md](./design.md)，当前跨仓库实施与环境状态只在 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-client-api-access-20260913) 维护。本页关联源码和开发检查，不代表阶段 4 已整体验收。

## 1. 源码入口

| 范围 | 入口与检查重点 |
|---|---|
| Platform Credential | [API 合同](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/apicredentialmanagement/ApiCredentialManagementApi.java)、[Processor](../../../paytech/platform-support/paytech-platform-support-core/src/main/java/com/paytech/platform/support/service/processor)、[业务 Service](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/ApiCredentialManagementServiceImpl.java)：只读列表/详情/全部授权，指定凭证撤销；当前权限先于幂等重放，HMAC 在事务外准备，数据库锁及版本保护目标行 |
| Platform Scope | [API 合同](../../../paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/apiscopemanagement/ApiScopeManagementApi.java)、[Processor](../../../paytech/platform-support/paytech-platform-support-core/src/main/java/com/paytech/platform/support/service/processor)、[业务 Service](../../../paytech/platform-support/paytech-platform-support-access-control/src/main/java/com/paytech/platform/support/accesscontrol/domainservice/service/impl/ApiScopeManagementServiceImpl.java)：目录查/建/改、路由选项，编码不变；无状态、删除、授权编辑或新回执表 |
| BFF | [Scope 详情聚合](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/service/ApiScopeDetailsProcessor.java)、[明确字段投影](../../../paytech/operations-console-bff/paytech-operations-console-bff-app/src/main/java/com/paytech/operations/console/bff/integration/ClientApiContractConverter.java)：先查 Scope，再只查用户选择的引用页；缺少引用权限返回 403；检查上游身份及分页元数据，不传秘密字段 |
| Web | [凭证页面](../../../operations-console-web/src/pages/ApiCredentials.tsx)、[Scope 页面](../../../operations-console-web/src/pages/ApiScopes.tsx)、[远程 Scope 选择器](../../../operations-console-web/src/clientApi/ScopeSelect.tsx)：只读凭证、单独撤销弹层、目录编辑、按权限显示引用/操作记录、浏览器导航及冲突草稿；名称查不全时保留原编码，不误标目录缺失 |
| 旧合同 | Route 列表新增 requiredScopeCode，新增/改绑检查目录；原未登记绑定更新仍兼容。审计查询新增 resourceType/resourceId，ID 必须配合类型。全部沿用 v1，无带版本号的类名 |
| 数据与注册 | [正式 Schema](../../../paytech/platform-support/docs/database/sql/platform-support-schema.sql)、[完整初始化](../../../paytech/platform-support/docs/database/sql/platform-support-initial-data.sql)、[增量入口](./db/README.md)。新目录来自 Manifest 生成模型；初始化新增禁用注册，环境启用另行确认。历史两表建表文件保持原样 |

## 2. 开发证据

机器可读记录见 [verification.json](./assets/stage4/verification.json)。Platform 用例分为不依赖新增表的架构/持久化合同检查，以及需要本机数据库准备的真实 HTTP 场景；BFF BrowserAutoTest 是真实 BFF HTTP 加 Owner Simulator，并非真实浏览器或真实 Platform 联调。

- 初始失败证据：新 Platform 入口在实现前返回 404；模型检查先识别新表缺失；BFF 新增不一致对象响应场景先暴露错误接受，随后补齐明确响应校验。
- [Platform 真实业务回归](../../../paytech/platform-support/paytech-platform-support-test/src/test/java/com/paytech/platform/support/auto)包含目录保存/清空/重放/对象审计、非活跃 Tenant 查询、全部授权区间、撤销前后 PLT-I03 认证、其他凭证保留、权限分离、并发和严格输入；补齐真实 Lease 到期后的行锁/唯一索引、轮换来源、异常历史数据、字面量筛选和授权区间端点。测试先检查手工 Schema 前置，不自动建表。
- [BFF HTTP 边界回归](../../../paytech/operations-console-bff/paytech-operations-console-bff-test/src/test/java/com/paytech/operations/console/bff/auto/ClientApiManagementBrowserAutoTest.java)覆盖九个端点的 Header/Query/Body、Origin、幂等键、NONE/ROUTE/CREDENTIAL 聚合、错误透传、无秘密投影、错误身份及分页拒绝。原入口清单和仅元数据日志的精确断言同步增加本期路径。
- Web 使用现有分页、错误、草稿和保存机制；页面能力清单同步新增两个组件。生产构建及原有 Web 检查属于开发证据，Pencil 的阶段 3 设计渲染不能代替真实浏览器验收。

## 3. 数据库与查询计划证据

[本机 SQL 申请及执行入口](./db/local-application.md)、[备份/文件 SHA/127 行逐项核对](./assets/stage4/local-database-application.json)与[执行前检查](./assets/stage4/local-database-preflight.json)保留人工应用记录。测试继续要求完整初始化目录，不自动补表或补共享权限。

[EXPLAIN 记录](./assets/stage4/local-explain.json)使用同一本机库和一个回滚事务中的 64 条 Scope、256 条凭证、512 条授权、256 条 Route；完整 [SQL](./assets/stage4/local-explain.sql)明确以 ROLLBACK 结束。Scope 精确读取采用 `uk_api_scope_code`，凭证反查的列表/count 都采用 `idx_api_scope_credential`，Route 反查的列表/count 都采用 `idx_gateway_route_scope`。小维表部分选择全扫描，完整优化器输出保留；这是访问路径验证，不是压力或延迟基准。事务结束后六类合成记录均为 0。

## 4. 开发审查与验收映射

| 验收项 | 开发证据与尚未执行的阶段 |
|---|---|
| AC-01 / AC-05 | 真实 HTTP 筛选/total/失效 Tenant/授权窗口，SQL EXISTS 反查不重复；BFF 按选择聚合一种引用。浏览器分页和布局留阶段 5 |
| AC-02 / AC-03 | SQL 与 DTO 只投影批准字段；真实响应排除完整凭证/PHC，日志使用元数据模式；端点清单没有凭证创建/轮换/授权编辑 |
| AC-04 / AC-08 | 真实创建/编辑/清空/名称同名/编码唯一、旧版本和幂等重放；Redis 租约到期后真实行锁与唯一索引保护，纯引用转换拒绝串对象/损坏引用 |
| AC-06 / AC-07 | 独立权限、权限撤销后重放拒绝、过期凭证/终止 Tenant 可紧急撤销；PLT-I03 前 200/后 401，另一凭证仍 200 |
| AC-09 | 真实对象审计筛选，成功变更与重放条数；复用既有异步审计组件及其完整回归，不引入另一套历史表 |
| AC-10 | 原 Route/权限/菜单/认证完整 auto，BFF 原接口与风控页面回归；旧 Scope 保留绑定可更新，新绑定查目录；同步原 Route 维护合同的 requiredScopeCode 最小查询项 |
| UI-01—UI-06 | Web 开发检查和生产构建，阶段 3 Pencil 已审阅；真实浏览器交互、视觉及完整链路验收在阶段 5 执行 |

本轮审查确认 Processor 在事务外准备 HMAC、短事务内仅调用本库能力，当前权限先于重放；接口沿用 v1、类型名不带版本号，Repository/Service 保留项目接口形态。没有修改 Gateway 转发或 Client Console 的生命周期边界。

下一确认点：阶段 4 必要检查完成后，提请用户确认进入阶段 5 综合验证。制品发布、test SQL/部署及提交仍按后续阶段具体授权执行。
