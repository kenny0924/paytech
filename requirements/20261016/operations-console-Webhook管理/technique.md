# Operations Webhook 管理：方案、接口与数据设计（阶段 3）

> 状态：阶段 3 评审稿（2026-10-16）。需求见 [requirement.md](./requirement.md)，差异分析见 [analysis.md](./analysis.md)。

**规模（本次方案）：** 涉及 3 个目录（Platform Support、Operations BFF、Operations Web）；新增 1 个接口（`GET /webhook-admin/v1/clients`），BFF 转发 10 个接口（Platform 已有 9 个 + 新增 1 个，同一 Method + Path 不重复计数）；新增 0 张表、0 个字段、0 个索引；注册 DML 128 行（菜单与按钮 2、Gateway 路由 10、路由 Header 44、Query 参数 12、限流规则 60）；业务 DML 0 行；新增 1 个功能页面（两个标签页），Pencil 新增 10 个画板，原有画板不改。

## 1. 业务与架构

### 1.1 分层与落点

```
Browser ─ Gateway /api/operations/webhook-admin/v1/** ─ Operations BFF（简单转发，统一授权拦截）
        ─ Platform /webhook-admin/v1/**（Processor 内重检会话与 WEBHOOK_READ / WEBHOOK_MANAGE，写操作审计）
```

- Platform 已有 9 个接口不改合同；只新增 Client 搜索。
- BFF 不聚合、不缓存、不处理 `Idempotency-Key`；会话 Header 由 `OperatorContext` 填入。
- 密钥明文只经过：Platform 响应 → BFF 透传 → 浏览器弹窗内存。Platform 与 BFF 响应都是 `Cache-Control: no-store`；`/webhook-admin` 在两端都是元数据日志；Gateway 访问日志不含 Body。

### 1.2 Client 搜索（新接口）

- **模块落点**：access-control 的 `ClientTenantQueryService` 增加分页搜索 CLIENT 租户；delivery 的 `WebhookEndpointRepository` 增加"按租户 ID 批量计数"；web 新增 `WebhookClientsProcessor` 组合两者。两次本地查询，不跨模块联表，不开写事务。
- **匹配规则**：`keyword` 去首尾空白后 ≤255 字符；1—18 位数字时按 `owner_ref_id`（Client ID）精确匹配，否则按名称包含（`LIKE … ESCAPE '!'`，沿用租户管理的转义写法）。为空时返回全部 CLIENT 租户分页。
- **排序与分页**：按租户 ID 升序（复用租户管理的分页查询）；`size` 沿用公共分页 10 / 20 / 50 / 100 / 200，默认 10。
- **状态**：返回 `ownerStatus`、`securityStatus` 原值，页面据此提示"平台访问已暂停"等；不过滤非 ACTIVE 租户（运营仍需查看已暂停 Client 的端点和投递）。
- **审计**：与其他读接口一致，`@Audit(action = "OPERATIONS_WEBHOOK_CLIENTS", resourceType = "WEBHOOK_CLIENT", fallbackResourceId = "WEBHOOK_CLIENT_CATALOG", recordSuccess = false)`。

### 1.3 页面交互规则

| 场景 | 规则 |
|---|---|
| Client 选择 | 输入防抖 300ms 后调用搜索；选中后把 `clientId` 写入 URL 查询参数（`/webhooks?clientId=…&tab=deliveries`），刷新可恢复；不写 localStorage |
| 端点数 | 来自端点列表长度；已满 5 个时"新建端点"按钮禁用并提示 |
| 新建 / 修改 | 同一表单；地址、事件（至少 1 项）、原因必填；修改带 `expectedVersion`；409 显示冲突横幅并可重新加载 |
| 密钥弹窗 | 新建、轮换成功后打开；只在组件状态里保存明文，关闭即清空；"关闭"需先勾选"我已保存密钥并交给 Client"；复制用 Clipboard API，失败时提示手动选择 |
| 启停 | 停用二次确认（说明取消未完成投递、不补发）；启用不需要二次确认但需原因 |
| 轮换 | 默认宽限 1 小时；"立即作废"红字提示 |
| 测试事件 | 无请求体；结果在弹窗显示；失败代码中文映射见 1.4 |
| 投递列表 | 默认最近 24 小时；时间跨度 >31 天前端拦截；端点下拉来自当前 Client 的端点列表 |
| Replay | 只对 SUCCEEDED / DEAD / CANCELLED 显示；需原因；成功后刷新列表并提示新投递 ID |
| 只读账号 | 页面按按钮资源 `OPERATIONS_WEBHOOK_MANAGE` 隐藏新建、操作列、Replay |

### 1.4 状态与失败代码中文名（Web）

| 类别 | 映射 |
|---|---|
| 端点状态 | ACTIVE 启用、DISABLED 已停用 |
| 投递状态 | CREATED 待投递、PROCESSING 处理中、RETRY_WAIT 等待重试、SUCCEEDED 已送达、DEAD 已放弃、CANCELLED 已取消 |
| 尝试 / 测试失败 | HTTP_STATUS「对方返回 HTTP {status}」、TIMEOUT 超时、CONNECT_FAILED 连接失败、TLS_FAILED TLS 握手失败、DNS_FAILED 域名无法解析、BLOCKED_ADDRESS 地址解析到内网，已拦截、INTERRUPTED 发送中断（结果未知） |
| 审计动作 | `OPERATIONS_WEBHOOK_CLIENTS` 查询 Webhook Client、`_ENDPOINTS` 查询 Webhook 端点、`_ENDPOINT_CREATE` 新建 Webhook 端点、`_ENDPOINT_UPDATE` 修改 Webhook 端点、`_ENDPOINT_STATUS_CHANGE` 启停 Webhook 端点、`_SECRET_ROTATE` 轮换 Webhook 密钥、`_ENDPOINT_TEST` 发送 Webhook 测试事件、`_DELIVERIES` 查询 Webhook 投递、`_DELIVERY` 查看 Webhook 投递、`_DELIVERY_REPLAY` Replay Webhook 投递；资源类型 `WEBHOOK_CLIENT` Webhook Client、`WEBHOOK_ENDPOINT` Webhook 端点、`WEBHOOK_DELIVERY` Webhook 投递 |

## 2. 接口

### 2.1 约定

- Platform 前缀 `/webhook-admin/v1`；Gateway 外部 `/api/operations/webhook-admin/v1/...`；BFF 浏览器路径与 Platform 相同。
- 错误：字段 422；业务 400；不存在 404；版本冲突 409；BFF 原样透传，Platform 不可用 503。
- Platform 已有 9 个接口的请求、响应见 `WebhookManagementApi`，本期不改。

### 2.2 接口清单

| # | Method + Path | 权限 | 请求体上限 | 说明 |
|---|---|---|---|---|
| 1 | `GET /clients` | READ | 0 | **新增**：Client 搜索 |
| 2 | `GET /clients/{clientId}/endpoints` | READ | 0 | 端点列表 |
| 3 | `POST /clients/{clientId}/endpoints` | MANAGE | 8192 | 新建端点，201 返回密钥 |
| 4 | `PUT /endpoints/{endpointId}` | MANAGE | 8192 | 修改 |
| 5 | `PATCH /endpoints/{endpointId}/status` | MANAGE | 8192 | 启停 |
| 6 | `POST /endpoints/{endpointId}/signing-secret/rotations` | MANAGE | 8192 | 轮换，返回密钥 |
| 7 | `POST /endpoints/{endpointId}/tests` | MANAGE | 0 | 测试事件，无请求体 |
| 8 | `GET /deliveries` | READ | 0 | 投递分页 |
| 9 | `GET /deliveries/{deliveryId}` | READ | 0 | 投递详情 |
| 10 | `POST /deliveries/{deliveryId}/replays` | MANAGE | 8192 | Replay，201 |

### 2.3 新接口：Client 搜索

`GET /webhook-admin/v1/clients?keyword=acme&page=1&size=10`

| 参数 | 类型 | 必填 | 规则 |
|---|---|---|---|
| `keyword` | string | 否 | 去空白后 ≤255；1—18 位数字按 Client ID 精确匹配，否则名称包含 |
| `page` | int | 否 | 默认 1，≥1 |
| `size` | int | 否 | 10 / 20 / 50 / 100 / 200，默认 10 |

响应（`PagingResponseBody<WebhookClientResponseBody>`）：

```json
{"items":[{"clientId":"880000000000071001","clientName":"Acme Trading LLC","ownerStatus":"ACTIVE","securityStatus":"ACTIVE","endpointCount":3}],"page":1,"size":10,"total":1}
```

错误：`keyword` 超长或 `size` 非法 → 422（字段错误）；未登录 / 会话失效 → 401；无 `WEBHOOK_READ` → 403。

### 2.4 BFF 与 Web 映射

| 层 | 新增 | 共享文件（增量） |
|---|---|---|
| Platform | `WebhookManagementApi#clients`、`WebhookClientResponseBody`、Controller 方法、`WebhookClientsProcessor`、`ClientTenantQueryService#searchClients`、`WebhookEndpointRepository#countByTenants` 与 Mapper SQL；`console-resource-capabilities.json` 追加 Webhook 页面 | 无（Controller / API 为本需求先前提交的文件） |
| BFF | `WebhookManagementController`、`PlatformWebhookManagementClient(Impl)`、`WebhookManagementModels` | `PlatformClientConfiguration`（Bean）、`ConfigurationManagementRequestConfiguration`（路径与模型白名单）、`application.yml`（元数据日志前缀加 `/webhook-admin`）、`OperationsConsoleBffApplicationAutoTest`（路由全集）、`OperationsHttpBodyNamingAutoTest`（Controller 列表） |
| Web | `src/webhook/`（模型、解码、Hook、密钥弹窗、端点表单）、`pages/WebhookManagement.tsx` | `navigation/pageCapabilities.ts`（+1 页 1 按钮）、`navigation/ConsolePage.tsx`（懒加载）、`pages/auditView.ts`（动作与资源类型中文名）、`configuration/configurationModel.test.ts`（页面数 +1） |

## 3. 数据库与注册数据

### 3.1 Schema DDL

0 条。端点计数走现有 `idx_webhook_endpoint_tenant`。

### 3.2 业务 DML

0 行。

### 3.3 注册 DML（128 行，追加到 `platform-support-initial-data.sql`，独立块，待执行）

**ID 块调整**：阶段 2 建议用 `900000000000097xxx`；核对时发现 Webhook 投递已提交的权限块注释已预留 `900000000000090300+` 给本需求的菜单、按钮和路由。为让 Webhook 的注册数据集中在一个块、注释不失效，改用 `9000000000000903xx—0907xx`（全仓库未使用）。

沿用先例：菜单、按钮、路由 `DISABLED`，由激活 SQL 另行启用；权限与 PLATFORM_ADMIN 授权已在 Webhook 投递块中（test 已 ACTIVE），本块不重复。

| 表 | 行 | 内容 |
|---|---|---|
| `t_console_resource` | 2 | `090301` 菜单 `OPERATIONS_WEBHOOKS`「Webhook 管理」，父 `900000000000000562`（平台配置），路由 `/webhooks`，组件 `OPERATIONS_WEBHOOKS`，权限 `WEBHOOK_READ`，排序 60；`090302` 按钮 `OPERATIONS_WEBHOOK_MANAGE`「维护」，父 `090301`，权限 `WEBHOOK_MANAGE` |
| `t_gateway_route` | 10 | `090401—090410`，`operations-webhook-list-clients`、`-list-endpoints`、`-create-endpoint`、`-update-endpoint`、`-endpoint-status`、`-rotate-secret`、`-test-endpoint`、`-list-deliveries`、`-delivery-detail`、`-replay-delivery`；`OPERATIONS_JWT` + `HUMAN_PERMISSION`；体积见 2.2；超时 5000 / 10000（同先例） |
| `t_gateway_route_header` | 44 | GET 每条 2（`authorization` CORS、`origin` FORWARD）×4；写 每条 6（另加 `content-type`、`idempotency-key` 的 CORS 与 FORWARD）×6，测试事件虽无请求体也需 `content-type`（BFF 写请求统一要求） |
| `t_gateway_route_query_param` | 12 | `/clients`：`keyword`、`page`、`size`；`/deliveries`：`clientId`、`createdFrom`、`createdTo`、`page`、`size`、`endpointId`、`eventType`、`status`、`eventId` |
| `t_gateway_guard_rule` | 60 | 每条路由 6 条，数值同通知管理（共享 300/分、并发 50；单 IP 30/分、并发 3；单账号 10/分、并发 2） |

激活 SQL（阶段 6 写入发布目录）：菜单 2、路由 10 改 `ENABLED`。

## 4. 页面设计

Pencil：`operations-console-web/docs/operations-console.pen`，新增 10 个画板（`Webhook 管理 · 01—10`），导出见 `assets/stage3/pencil/`。

| 画板 | 内容 | 导出 |
|---|---|---|
| 01 端点列表 | 平台配置下新菜单；Client 选择、端点 / 投递记录标签；表格（地址与订阅、状态、密钥序号与宽限截止、更新时间、操作） | `01-endpoint-list.png` |
| 02 选择 Client | 搜索下拉（名称、Client ID、端点数、暂停提示）；未选 Client 空状态 | `02-select-client.png` |
| 03 新建端点（字段错误） | 地址、8 个事件勾选、原因；内网地址就地报错 | `03-create-endpoint-error.png` |
| 04 签名密钥（只显示一次） | 警示、密钥与复制、已复制反馈、签名头说明、确认勾选后关闭 | `04-secret-once.png` |
| 05 轮换密钥 | 宽限 1 小时（推荐）/ 立即作废 单选、原因 | `05-rotate-secret.png` |
| 06 停用端点确认 | 影响说明、原因、危险按钮 | `06-disable-endpoint.png` |
| 07 测试事件结果 | 失败示例与其他结果说明、再次发送 | `07-test-result.png` |
| 08 投递记录 | 时间 / 端点 / 事件类型 / 状态 / Event ID 筛选；状态标签；Replay 入口 | `08-delivery-list.png` |
| 09 投递详情 | 右侧抽屉：基本信息、Envelope、尝试记录（最新在前，可展开）、Replay | `09-delivery-detail.png` |
| 10 Replay 确认 | 说明新投递语义、原因 | `10-replay-confirm.png` |

与需求的差异：投递列表不显示"下次重试时间"——`t_webhook_delivery` 不保存该值（在补偿任务表中），为此跨模块读取不值得；列表改显示尝试次数与最近尝试时间。

## 5. 研发与验证计划

| 顺序 | 系统 | 内容 | 测试入口 | AC |
|---|---|---|---|---|
| 1 | Platform | Client 搜索：API、Controller、Processor、两个查询；能力清单；注册 DML | `auto/ext/http/operations/webhook/WebhookClientSearchTest`（继承 `OperationsApiTestBase`）：名称 / ID 匹配、分页、端点计数、无权限 403、参数 422；Webhook 现有 4 类测试回归 | AC-01、02、10 |
| 2 | BFF | Controller、Client 端口与实现、模型、5 个共享文件 | `WebhookManagementBrowserAutoTest` + `webhook-management-boundary.json`：10 个路由转发、错误透传（400 / 404 / 409 / 422）、未知字段拒绝、测试事件无 Body、响应 `no-store` | AC-01、03、09、11 |
| 3 | Web | 页面、模型与解码、弹窗、共享文件 | `src/webhook/webhook.test.ts`：解码与校验（密钥格式、状态映射、失败代码中文）、密钥只保存在弹窗状态、时间范围校验；`npm test`、`npm run build` | AC-02—08、11—14 |
| 4 | 本机联调 | 本机 Platform + BFF + Web，执行注册与激活 SQL（需授权），浏览器验收 10 个画板对应流程 | 浏览器截图 | 全部 |

## 6. 开放项

1. 本机联调（阶段 5）需要在本机 `mor_platform_e2e` 执行注册块与激活 SQL，届时单独请求授权。
2. Client Console 会话的未提交 Platform 改动可能影响本机全量 `verify`；阶段 4 前确认工作区可编译。

## 7. 实现偏差（阶段 4）

| # | 方案 | 实现 | 原因 |
|---|---|---|---|
| 1 | 纯数字关键字同时匹配 Client ID 或名称；按名称排序；size 10/20/50 | 1—18 位数字只按 Client ID 精确匹配；按租户 ID 升序；size 沿用公共分页 | 复用 Access Control 已有的租户分页查询（`TenantAccessRepository.tenants`，筛选条件为 AND），不为一个下拉新增 OR 查询与排序；18 位上限避免 Long 溢出 |
| 2 | 审计兜底资源 `WEBHOOK_CLIENT_CATALOG` | `WEBHOOK_CATALOG` | 与同 Controller 其他读接口一致 |
| 3 | BFF 已拒绝无 Body 路由的请求体 | BFF 原只拒绝 GET Body；新增 Webhook 无 `@RequestBody` 路由拒绝 Body；测试事件仍带 `Content-Type: application/json` | BFF 写请求统一要求 JSON Content-Type（与入驻分类确认路由相同） |
| 4 | BFF 分页沿用公共转换 | Webhook Client 分页本地校验上限 200 | 公共转换上限 100，Platform 允许 200；不改公共转换 |
| 5 | 测试事件审计由 @Audit 记录 | 发送后另开短事务重检权限并记录 | 审计组件只在事务内落库；原实现事务外记录被丢弃（阶段 5 发现） |
| 6 | Web 地址字段只允许 https | 前端只校验 http(s) URL 格式 | 协议放行由 Platform 配置决定（本机 / test 放行 http）；生产仍由 Platform 拒绝 http |
