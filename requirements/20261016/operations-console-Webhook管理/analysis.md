# Operations Webhook 管理：现状差异与影响分析（阶段 2）

> 状态：阶段 2 已确认（2026-10-16，第 5 节按建议）。只读分析，未改代码。需求见 [requirement.md](./requirement.md)。

## 1. 结论摘要

- Platform 的 9 个管理接口、权限、审计、错误码都已实现并部署在 mor-test，**后端只缺 Q-01 的 Client 搜索**（1 个接口）。
- 其余工作是"接线"：BFF 转发 10 个接口、Web 1 个页面（两个标签页 + 若干弹窗 / 抽屉）、Platform 初始化 SQL 追加菜单 / 按钮 / Gateway 路由注册块。
- 密钥明文安全边界在各层已具备：Platform 响应 `no-store` 且 `/webhook-admin` 为元数据日志；BFF 对所有响应统一加 `Cache-Control: no-store`；Gateway 访问日志不记 Body。只需把 `/webhook-admin` 加入 BFF 的元数据日志前缀。
- 主要风险是并行修改：Client Console 会话在 Platform 有大量未提交改动（含初始化 SQL、`application.yml`），Operations Web 的页面计数测试与注册表当前已不一致（30 项 / 断言 29）。

## 2. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| Client 选择（范围 1，Q-01 B） | Webhook 接口只收 Client ID（`WebhookManagementApi`）；`ClientTenantQueryService` 只有 `findClientTenantId(clientId)`；按名称搜租户的 SQL 在 `TenantAccessManagementMapper.xml:10`、`ApiCredentialManagementMapper.xml:14`（`tenant_name LIKE … ESCAPE '!'`），但都挂在各自权限下 | `WEBHOOK_READ` 即可按 Client ID 或名称搜索 CLIENT 租户，返回 Client ID、名称、端点数 | 无接口；`t_webhook_endpoint` 只有 `find`、`findTenant` 查询 | 新增 `GET /webhook-admin/v1/clients`：access-control 的 `ClientTenantQueryService` 增加分页搜索；delivery 的端点 Repository 增加"按租户 ID 批量计数"；Processor 在 web 组合，**不跨模块写联表 SQL** |
| 端点列表 / 新建 / 修改 / 启停（范围 2） | 已实现（`WebhookManagementController` 第 56—95 行，`service/processor/webhook/`） | 页面调用 | 无 BFF、Web | 简单转发 |
| 密钥一次性展示与轮换（范围 3） | 已实现；Platform 响应 `no-store`；BFF 拦截器对所有响应加 `no-store`（`OperationsRequestInterceptor.java:32`） | 弹窗展示一次、复制、关闭确认 | Web 弹窗；BFF 元数据日志前缀缺 `/webhook-admin`（`application.yml:73-98`） | Web 不把密钥写入状态管理以外的任何地方（不进 URL、localStorage）；BFF 加前缀 |
| 测试事件（范围 4） | 已实现，无请求体 | 显示 HTTP 状态或失败原因 | Web 需要失败代码的中文说明 | Web 映射失败代码（地址被拦截、超时、连接失败、HTTP 状态等）；无请求体调用（Client Console 会话已踩过"带 `{}` 被 BFF 拒绝"） |
| 投递记录与 Replay（范围 5） | 已实现：列表要求 `clientId`、`createdFrom/To`（≤31 天）；详情含 Envelope 与尝试；Replay 带 `reason` | 默认最近 24 小时（Q-03） | 无 BFF、Web | 简单转发；默认时间在 Web 计算 |
| 权限、菜单、审计（范围 6） | 权限 `WEBHOOK_READ/MANAGE`（`900000000000090101/102`）及授权已在初始化 SQL 和 mor-test；审计动作 `OPERATIONS_WEBHOOK_*`（9 个，含读操作）与资源类型 `WEBHOOK_CLIENT/ENDPOINT/DELIVERY` 已由 Controller `@Audit` 产生 | 菜单、按钮、Gateway 路由；审计页中文名 | 无 Console Resource、按钮、Gateway 路由；`console-resource-capabilities.json` 无 Webhook；Web `auditView.ts` 无 Webhook 动作与资源类型中文名 | 追加注册块（ID 见 5.2）；按钮按读 / 写两级 |
| BFF 转发（范围 7） | BFF 已依赖 `platform-support-api`；通知管理是同类先例（`NotificationManagementController`、`PlatformNotificationManagementClient(Impl)`、`NotificationManagementModels`） | `/webhook-admin/v1/**` 转发 | 全部新增 | 按通知管理先例；会话 Header 由 `OperatorContext` 填入 |

## 3. 逐系统影响

| 系统 / 目录 | 职责 | 影响点 | 改动类型 | 依赖与风险 |
|---|---|---|---|---|
| `platform-support-api` | 内部合同 | `WebhookManagementApi` 加 1 个方法；新增响应 Body | 代码 | BFF 依赖此 jar；Client Console 会话也在该 jar 新增文件（不同包，未改 Webhook 管理文件） |
| `platform-support-access-control` | 租户事实 | `ClientTenantQueryService` 增加分页搜索；Mapper 增加查询；`console-resource-capabilities.json` 追加 Webhook 页面 | 代码、资源 | 该会话的 access-control 有大量未提交改动，但不涉及这两个文件 |
| `platform-support-delivery` | Webhook 事实 | 端点按租户批量计数 | 代码 | 无 |
| `platform-support-web` | 入口与 Processor | Controller 加 1 个方法 + 1 个 Processor（`@Audit` 读操作，与现有读接口一致） | 代码 | Client Console 会话在同一包 `service/processor/webhook/` **新增**了 9 个 `Client*` 文件，不改现有文件；无文件重叠 |
| Platform 初始化 SQL | 注册数据 | 追加独立块：Console Resource（菜单 1 + 按钮）、Gateway 路由 10 条及 Header / Query / 限流行 | DML（只写不执行） | 该会话正在同一文件追加自己的块（`900000000000058xxx`），只做尾部追加并避开其 ID |
| Platform 测试 | 验证 | 新接口的 HTTP 测试；Console 能力清单相关断言 | 测试 | 共用本地 `mor_platform`，须与其他会话串行 |
| `operations-console-bff` | 转发 | 新 Controller、Client 端口与实现、模型；共享文件：`PlatformClientConfiguration`（Bean）、`ConfigurationManagementRequestConfiguration`（路径与模型白名单）、`application.yml`（元数据前缀）、应用路由全集测试、Body 命名测试 | 代码、配置 | 与通知管理同样的 5 个共享文件 |
| `operations-console-web` | 页面 | 新页面与 `src/webhook/` 模型、解码、弹窗；共享文件：`pageCapabilities.ts`、`ConsolePage.tsx`、`auditView.ts`、`configurationModel.test.ts`（页面计数） | 代码 | **当前已不一致**：注册表 30 项而测试断言 29，说明有其他会话的未完成改动；本需求只 +1，阶段 4 前再确认基线 |
| Gateway | 路由 | 无代码改动；路由来自注册数据 | 无 | DML 执行后才能从真实入口访问 |
| Vault、Commerce、其他系统 | — | 无 | 无 | — |

## 4. 现有约定（阶段 3 直接沿用）

| 项 | 约定 | 证据 |
|---|---|---|
| 路径 | Platform `/webhook-admin/v1/...`；Gateway 外部 `/api/operations/webhook-admin/v1/...`；BFF 浏览器路径与 Platform 相同 | 通知管理先例 |
| 身份 | `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id` 由 BFF 从会话填入；Platform Processor 内重检会话与权限 | `WebhookManagementApi` |
| 错误 | 字段 422；业务 400；不存在 404；冲突 409；BFF 原样透传、依赖不可用 503 | 通知管理 analysis 第 4 节 |
| 幂等 | Gateway 负责；BFF / Platform / Web 不处理 `Idempotency-Key` | AGENTS.md |
| 注册状态 | 菜单、按钮、路由 `DISABLED`，激活 SQL 另行执行；权限已 ACTIVE | 通知管理、Webhook 投递 test-activate |
| 测试 | Platform `OperationsApiTestBase`；BFF `PlatformOperationsSimulator` + JSON 用例；Web Node 内置测试只测模型与解码 | 各测试目录 |

## 5. 阶段 3 需要确定的点（附建议）

### 5.1 Client 搜索接口形态

- **建议**：`GET /webhook-admin/v1/clients?keyword=&page=&size=`。`keyword` 为纯数字时按 Client ID 精确匹配，否则按名称包含匹配；只返回 CLIENT 租户；每项 `clientId`、`clientName`、`tenantStatus`（来源与安全状态）、`endpointCount`。权限 `WEBHOOK_READ`，审计动作 `OPERATIONS_WEBHOOK_CLIENTS`（与其他读接口一致）。
- 不带 keyword 时返回全部 CLIENT 租户分页（便于浏览）；是否只列"已有端点"的 Client 用筛选项 `withEndpoints=true`。

### 5.2 注册 ID 块

`900000000000090xxx` 已用于 Webhook 权限；初始化 SQL 中 `95xxx`、`97xxx` 未使用（`91xxx—94xxx` 被 Risk 库使用，虽不同库也避开）。**建议**用 `900000000000097xxx`。

### 5.3 其他（按此处理，不需要你决定）

- 菜单名"Webhook 管理"，前端路由 `/webhooks`；按钮：新建、修改、启停、轮换密钥、测试、Replay（均对应 `WEBHOOK_MANAGE`）。
- 页面计数与路由全集测试只 +1 / +10，不顺手修其他会话造成的不一致；阶段 4 前重新看基线。
- 失败代码中文映射放 Web；Platform 不改错误合同。

## 6. 依赖与风险

| 风险 | 影响 | 应对 |
|---|---|---|
| Client Console 会话在 Platform 的未提交改动（初始化 SQL、`application.yml`、`PlatformApiErrorConfiguration`、access-control 多个文件） | 同文件冲突；本地构建 / 测试包含对方半成品 | 只尾部追加初始化 SQL；不改对方文件；测试前确认工作区可编译；提交时用私有 index 只放本需求的行 |
| Operations Web 页面计数测试当前已失败（30 / 29） | 阶段 5 无法用"全部通过"判断 | 阶段 4 开始前确认归属；只对自己的 +1 负责并在交付中说明 |
| mor-test 部署需要同时替换 Platform（新接口）、BFF、Web | 发布面大于通知管理 | 阶段 6 列出顺序：Platform → BFF → Web，注册 DML 在激活前执行 |
| test 无 Commerce 事件 | 投递记录页在 test 只能看到测试 / 验收数据 | 阶段 7 用合成租户 + 临时接收端（同 Webhook 投递验收方式）造数据，结束后清理 |
| 密钥明文在浏览器内存 | 页面关闭前可被截图 / 复制 | 只在弹窗中显示，关闭即丢弃；不写入任何持久存储 |

## 7. 工作量依据（粗估，阶段 3 细化）

| 系统 | 内容 |
|---|---|
| Platform | 1 个接口（API、Controller、Processor、2 个 Repository / Service 方法、1 个 HTTP 测试类） |
| BFF | 1 个 Controller（10 个方法）、Client 端口与实现、模型；JSON 驱动边界测试 1 类；5 个共享文件增量 |
| Web | 1 个页面（Client 选择 + 端点 / 投递两个标签）、端点抽屉、密钥弹窗、启停 / 轮换 / Replay 确认弹窗、投递详情抽屉；模型与解码测试；4 个共享文件增量 |
| SQL | 注册 DML：菜单 1、按钮约 6、Gateway 路由 10 条及 Header / Query / 限流行（阶段 3 给精确数） |
| Pencil | 约 6—8 个画板 |
