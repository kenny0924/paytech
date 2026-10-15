# Operations 通知管理：方案、接口与数据设计（阶段 3）

> 状态：阶段 3 已确认（2026-10-15）。需求见 [requirement.md](./requirement.md)，现状分析见 [analysis.md](./analysis.md)。本文是方案，不表示已实现或已执行。

**规模（本次方案）：** 涉及 3 个目录（Platform Support、Operations BFF、Operations Web）；新增 12 个接口（Platform 与 BFF 同一 Method + Path，不重复计数）；新增 0 张表、0 个字段，旧表新增 1 个索引（`t_notification_request.create_time`）；注册 DML 160 行（权限 2、角色授权 2、菜单与按钮 6、Gateway 路由 12、路由 Header 52、Query 参数 14、限流规则 72）；业务 DML 0 行；新增 3 个功能页面（模板列表含详情子页、发送账号、发送记录），Pencil 新增 10 个画板（含弹窗、抽屉与状态图），原有画板不改。

## 1. 业务与架构

### 1.1 分层与落点（按阶段 2 决定 5.1、5.2）

```
Browser ─ Gateway /api/operations/notification-admin/v1/** ─ Operations BFF（简单转发，统一授权拦截）
   └─ Platform /notification-admin/v1/**
        platform-support-web       Controller（@Audit）→ 管理 Processor（鉴权、锁、事务、审计事实）
        platform-support-delivery  NotificationManagementService（配置规则）→ Repository → Mapper
```

| 模块 | 新增内容 |
|---|---|
| `platform-support-api` | `notificationmanagement/NotificationManagementApi` 与请求、响应 Body |
| `platform-support-delivery` | 7 个 Repository 增加查询与写入方法；新增 `NotificationManagementService`（interface + Impl）承载配置规则与渲染校验；`EmailProviders` 增加 `isConfigured(code)` |
| `platform-support-web` | `NotificationManagementController`；管理 Processor（`service/processor/notification/`）；锁配置 `NotificationManagementProperties` |
| `platform-support-access-control` | 权限常量 `NotificationManagementPermissions`；`console-resource-capabilities.json` 追加 3 项 |

**不依赖 `notification.enabled`：** 7 个 Repository 实现和新的 `NotificationManagementService` 去掉 `notification.enabled` 条件（只做本库读写，没有外部调用）；管理 Controller 与 Processor 只依赖 `operations.authentication.enabled=true`（与现有管理接口一致）。`EmailProviders` 仍随 `notification.enabled` 启用，管理侧以 `ObjectProvider` 注入，缺失时"已配置适配器"一律为 `false`。发送链路（Submit、即时发送、投递 Handler）的条件不变。

### 1.2 写操作统一流程

1. Controller：`@Audit(action, resourceType, resourceIdParameter, recordSuccess=true)`，转换 Body 后调用 Processor。
2. Processor（`AbstractLockProcessor`）：`LockCommand.tryLock("notification-template:{templateCode}")`；账号状态用 `notification-provider:{providerCode}`。等待 500ms、租约 30s（`ps.notification-management.*`，默认值同 API Scope）。
3. 锁内开编程式事务（READ_COMMITTED、15s）：`policy.authorizeManagement(actor, session, NOTIFICATION_MANAGE)` → 读当前行 → 比较 `expectedVersion` → 调 `NotificationManagementService` 校验 → 写入（`version = version + 1` 条件更新，影响 0 行视为冲突）→ `audit.record(ConfigurationChanged)`。
4. 事务内只操作本库，不调用 Vault、SES 或其他服务。
5. 返回 `{code, version}`。

读操作：`AbstractProcessor` + 只读事务（REPEATABLE_READ），`policy.requirePermission(actor, session, NOTIFICATION_READ)`。

### 1.3 配置规则（`NotificationManagementService`，与发送规则一致）

| 规则 | 失败返回 |
|---|---|
| 模板编码 `[A-Z][A-Z0-9_]{0,63}`，唯一；介质本期固定 EMAIL | 422 字段错误 / 400 `模板编码已存在` |
| 新建模板状态为 `DISABLED`；**启用**时须至少有 1 条语言内容 | 400 `模板没有语言内容，不能启用` |
| 语言 `NotificationInput.locale` 规则；每模板每语言一条 | 422 |
| 内容按 `NotificationInput.render` 校验：先取出全部 `${name}` 作为示例参数渲染，规则与发送时完全相同（标题非空 ≤255 且无换行，正文非空 ≤64 KiB，占位符语法） | 400，`errors[].field` 定位到 `subjectTemplate` / `bodyTemplate` |
| 即时发送模板（`notification.transient-template-codes`）正文必须含 `${link}` | 400 `认证邮件模板正文必须包含 ${link}` |
| 路由主账号必填，备用可空；两者不同；必须存在、EMAIL、`ACTIVE` | 400 |
| 不提供删除 | — |

允许但需前端二次确认的操作（后端不拦截）：停用被启用路由引用的账号；停用即时发送模板或其路由。

### 1.4 并发与一致性

- 同一模板的模板状态、语言内容、路由修改共用一把锁，避免"启用模板"与"删内容"交叉；`version` 条件更新是最终保护。
- 路由保存时读取账号状态，不对账号加锁；与账号停用并发时可能保存到刚被停用的账号，后果是发送时报"邮件发送账号未启用"，不会误发。接受此窗口。
- 所有修改立即生效：发送链路每次读库，无缓存（阶段 2 已核实）。

## 2. 接口

公共约定（沿用 `ApiScopeManagementApi`）：

- Platform 路径前缀 `/notification-admin/v1`；Gateway 外部路径 `/api/operations/notification-admin/v1/...`；BFF 浏览器路径与 Platform 相同。
- 每个方法带 Header `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`（BFF 从会话上下文填入）。
- 写请求都带 `reason`（1..500 字符）和 `expectedVersion`；`expectedVersion=0` 表示"目前不存在，新建"（仅语言内容和路由使用）。
- 分页：`page` 默认 1，`size` ∈ {10, 20, 50, 100}；响应 `{items, page, size, total}`。
- 错误：422 字段校验、400 业务规则、401 会话无效、403 无权限、404 不存在、409 版本冲突；时间为 UTC ISO-8601。
- 幂等归 Gateway；Platform 与 BFF 不处理 `Idempotency-Key`。

### 2.1 接口清单

| # | Method / Path | 权限 | 审计动作 | 说明 |
|---|---|---|---|---|
| 1 | `GET /templates` | READ | — | 查询：`keyword`（编码包含）、`status`；按编码升序 |
| 2 | `GET /templates/{templateCode}` | READ | — | 模板 + 全部语言内容 + 路由 |
| 3 | `POST /templates` | MANAGE | `NOTIFICATION_TEMPLATE_CREATE` | 新建，状态 `DISABLED` |
| 4 | `PATCH /templates/{templateCode}/status` | MANAGE | `NOTIFICATION_TEMPLATE_STATUS_CHANGE` | 启用 / 停用 |
| 5 | `PUT /templates/{templateCode}/contents/{locale}` | MANAGE | `NOTIFICATION_CONTENT_SAVE` | 新建（`expectedVersion=0`）或修改 |
| 6 | `POST /template-preview` | MANAGE | —（不记成功） | 渲染草稿，不发送、不落库 |
| 7 | `PUT /templates/{templateCode}/route` | MANAGE | `NOTIFICATION_ROUTE_SAVE` | 新建或修改主备账号 |
| 8 | `PATCH /templates/{templateCode}/route/status` | MANAGE | `NOTIFICATION_ROUTE_STATUS_CHANGE` | 启用 / 停用路由 |
| 9 | `GET /providers` | READ | — | 账号列表，含适配器配置情况与路由引用 |
| 10 | `PATCH /providers/{providerCode}/status` | MANAGE | `NOTIFICATION_PROVIDER_STATUS_CHANGE` | 启用 / 停用账号 |
| 11 | `GET /requests` | READ | — | 发送记录列表 |
| 12 | `GET /requests/{requestId}` | READ | — | 请求 + 投递 + 尝试 |

### 2.2 请求 / 响应

**1. 模板列表** `GET /templates?page=1&size=20&keyword=REFUND&status=ACTIVE`

```json
{"items":[{"templateCode":"PAYMENT_REFUND_SUCCEEDED_EMAIL","medium":"EMAIL","status":"ACTIVE",
  "locales":["en-US","zh-CN"],"transient":false,"routeStatus":"ACTIVE","version":3,
  "updateTime":"2026-10-15T08:00:00.000000Z"}],"page":1,"size":20,"total":1}
```

`routeStatus` 无路由时为 `null`；`transient` 来自 `notification.transient-template-codes`。

**2. 模板详情** `GET /templates/{templateCode}`

```json
{"templateCode":"CLIENT_PASSWORD_RESET","medium":"EMAIL","status":"ACTIVE","transient":true,"version":1,
 "requiredParameters":["link"],
 "contents":[{"locale":"zh-CN","subjectTemplate":"重置密码","bodyTemplate":"请打开 ${link} 重置密码","version":1,
   "updateTime":"..."}],
 "route":{"primaryProviderCode":"SES_PRIMARY","fallbackProviderCode":null,"status":"ACTIVE","version":1,"updateTime":"..."},
 "updateTime":"..."}
```

`requiredParameters`：即时发送模板为 `["link"]`，其他为 `[]`。`route` 无路由时为 `null`。

**3. 新建模板** `POST /templates` → `{"templateCode":"ORDER_PAID_EMAIL","reason":"新增付款成功通知"}`，响应 `{"code":"ORDER_PAID_EMAIL","version":1}`。

**4 / 8 / 10. 状态变更** `PATCH .../status` → `{"status":"DISABLED","expectedVersion":3,"reason":"..."}`，响应 `{"code":"...","version":4}`。状态未变化（目标与当前相同）返回 400。

**5. 保存语言内容** `PUT /templates/{templateCode}/contents/zh-CN`

```json
{"subjectTemplate":"退款成功","bodyTemplate":"您的退款 ${refundNo} 已成功。","expectedVersion":0,"reason":"新增中文文案"}
```

响应 `{"code":"zh-CN","version":1}`。接口限制：`subjectTemplate` ≤1024 字节、`bodyTemplate` ≤60000 字节（UTF-8），使整个请求不超过 Gateway 的 64 KiB 请求体上限；渲染后的长度仍按发送规则校验。

**6. 预览** `POST /template-preview`

```json
请求 {"subjectTemplate":"退款成功","bodyTemplate":"您的退款 ${refundNo} 已成功。","parameters":{"refundNo":"R-001"}}
响应 {"subject":"退款成功","body":"您的退款 R-001 已成功。","missingParameters":[]}
```

参数最多 64 个、值 ≤2048 字符；缺少的占位符不报错，原样保留并列在 `missingParameters`，方便边写边看。预览不调用 Vault、SES，不写库，不记成功审计。

**7. 保存路由** `PUT /templates/{templateCode}/route` → `{"primaryProviderCode":"SES_PRIMARY","fallbackProviderCode":null,"expectedVersion":0,"reason":"..."}`，响应 `{"code":"ORDER_PAID_EMAIL","version":1}`。新建的路由状态为 `ACTIVE`。

**9. 账号列表** `GET /providers?page=1&size=50`

```json
{"items":[{"providerCode":"SES_PRIMARY","medium":"EMAIL","status":"ACTIVE","adapterConfigured":true,"version":1,
  "references":[{"templateCode":"CLIENT_PASSWORD_RESET","role":"PRIMARY","routeStatus":"ACTIVE"}],
  "updateTime":"..."}],"page":1,"size":50,"total":1}
```

`role` ∈ `PRIMARY`、`FALLBACK`。不返回发件地址、区域和凭据。

**11. 发送记录列表** `GET /requests?page=1&size=20&sourceSystem=&sourceEventKey=&templateCode=&deliveryStatus=FAILED&createdFrom=...&createdTo=...`

- `createdFrom`、`createdTo` 必填，跨度不超过 31 天（页面默认最近 7 天）；按 `create_time` 倒序、`request_id` 倒序。
- `deliveryStatus` 匹配"至少一个投递处于该状态"的请求。

```json
{"items":[{"requestId":"880000000000000001","sourceSystem":"payment-orchestration-service","sourceEventKey":"refund:R-001",
  "templateCode":"PAYMENT_REFUND_SUCCEEDED_EMAIL","tenantId":null,"defaultLocale":"zh-CN","deliveryCount":1,
  "statusCounts":{"SENT":1},"createTime":"..."}],"page":1,"size":20,"total":1}
```

**12. 发送记录详情** `GET /requests/{requestId}`

```json
{"requestId":"...","sourceSystem":"...","sourceEventKey":"...","templateCode":"...","tenantId":null,"defaultLocale":"zh-CN",
 "parameterKeys":["refundNo"],"routeId":"...","createTime":"...",
 "deliveries":[{"deliveryId":"...","locale":null,"parameterKeys":[],"status":"SENT","attemptCount":1,
   "nextRetryTime":null,"updateTime":"...",
   "attempts":[{"attemptId":"...","providerCode":"SES_PRIMARY","status":"ACCEPTED","responseCode":"ACCEPTED",
     "resolutionSource":"SEND_RESPONSE","resolutionCode":null,"startedTime":"...","finishedTime":"...","resolvedTime":"..."}]}]}
```

不返回：收件邮箱及其 Vault Token、参数值、`provider_request_no`、`provider_message_id`。ID 用字符串。

### 2.3 BFF 与 Web 映射

| 层 | 内容 |
|---|---|
| BFF | `NotificationManagementController`（`/notification-admin/v1`）12 个方法，全部简单转发：`PlatformNotificationManagementClient` 端口 + Impl，经 `operationsConfigurationPlatformProxyFactory` 创建 `NotificationManagementApi`；422 字段路径用 `ConfigurationValidationConverter.local` 投影；本地模型 `NotificationManagementModels` |
| BFF 共享文件（增量） | `PlatformClientConfiguration`（新 Bean）、`ConfigurationManagementRequestConfiguration`（路径与模型白名单）、`application.yml`（`/notification-admin/v1/` 加入 metadata-only 前缀，模板正文不进访问日志）、`OperationsConsoleBffApplicationAutoTest`（前缀与路由全集）、`OperationsHttpBodyNamingAutoTest`（Controller 列表） |
| Web | 见第 4 节 |

## 3. 数据库与注册数据

### 3.1 Schema DDL（1 条，待执行）

```sql
ALTER TABLE `t_notification_request`
  ADD KEY `idx_notify_request_create_time` (`create_time`) COMMENT '支持访问路径：create_time（运营按时间查询发送记录）';
```

- 同步：`platform-support-schema.sql` 表定义、`platform-support.pdma.json` 索引、新增 `docs/database/sql/changes/2026-10-15-notification-request-create-time-index.sql`（含回退：`ALTER TABLE t_notification_request DROP KEY idx_notify_request_create_time;`）。
- 目标库：Platform Support 库；在线加索引，不改数据。

### 3.2 业务 DML

0 行。不改现有模板、账号、路由数据。

### 3.3 注册 DML（160 行，追加到 `platform-support-initial-data.sql`，独立块，待执行）

ID 块 `900000000000086xxx`～`89xxx`（全仓库未使用；`81xxx`～`84xxx` 已被 Vault 管理的注册 SQL 占用）。沿用先例：权限 `ACTIVE`，角色授权 `REVOKED`，菜单、按钮、路由 `DISABLED`；激活另行执行。

| 表 | 行数 | 内容 |
|---|---|---|
| `t_permission` | 2 | `NOTIFICATION_READ`（`NOTIFICATION`/`READ`/`LOW`）、`NOTIFICATION_MANAGE`（`MANAGE`/`HIGH`） |
| `t_role_permission` | 2 | 平台管理员 `900000000000000510` 各一条，`REVOKED` |
| `t_console_resource` | 6 | 顶层分组 `OPERATIONS_NOTIFICATION`「通知管理」（READ，排序 40）；菜单 `OPERATIONS_NOTIFICATION_TEMPLATES`「通知模板」`/notification-templates`、`OPERATIONS_NOTIFICATION_PROVIDERS`「发送账号」`/notification-providers`、`OPERATIONS_NOTIFICATION_RECORDS`「发送记录」`/notification-records`（READ）；按钮 `OPERATIONS_NOTIFICATION_TEMPLATE_MANAGE`「维护模板」、`OPERATIONS_NOTIFICATION_PROVIDER_MANAGE`「启停账号」（MANAGE） |
| `t_gateway_route` | 12 | `operations-notification-*`，对应 2.1 的 12 个接口；GET 体积 0；内容保存与预览 65536 字节（Gateway 上限）；其余写 8192；超时同先例 |
| `t_gateway_route_header` | 52 | GET 5 条 × 2（authorization、origin）；写 7 条 × 6（再加 content-type、idempotency-key 各两种用途） |
| `t_gateway_route_query_param` | 14 | 模板列表 4（page、size、keyword、status）；账号列表 2；记录列表 8（page、size、sourceSystem、sourceEventKey、templateCode、deliveryStatus、createdFrom、createdTo） |
| `t_gateway_guard_rule` | 72 | 每条路由 6 条，数值同先例 |

> 写路由体积：Gateway 与 Platform 路由编译器的请求体上限都是 65536 字节（`GatewayConfigurationCompiler.MAX_BODY_LIMIT_BYTES`、`GatewayRoutePolicyValidator`）。内容保存与预览取该上限，接口层把正文模板限制为 60000 字节、标题模板 1024 字节。

执行顺序：3.1 DDL → 3.3 注册 DML → 按需激活。回退：注册 DML 按 ID 块删除；DDL 按 changes 文件回退。

## 4. 页面设计

Pencil 设计：`operations-console-web/docs/design.pen` 新增 10 个画板（名称前缀「通知管理 ·」，位于画布下方 y≈47960 起两行），导出见 [assets/stage3/pencil](./assets/stage3/pencil/)（10 张 PNG 与 `export.pdf`）。

| 画板 | 内容 | 导出 |
|---|---|---|
| 01 通知模板列表 | 筛选、状态 / 用途 / 路由标签、分页 | `01-template-list.png` |
| 02 模板详情｜内容编辑与预览 | 认证提示、发送路由卡片、语言标签页、左右分栏编辑与预览、原因与保存 | `02-template-detail.png` |
| 03 新建模板 | 编码、介质、原因；新建后为停用 | `03-create-template.png` |
| 04 修改路由 | 主 / 备账号选择与规则提示 | `04-edit-route.png` |
| 05 停用认证模板确认 | 影响说明、必填原因 | `05-disable-auth-template.png` |
| 06 状态｜保存冲突与字段错误 | 冲突横幅（复制草稿 / 重新加载）、缺 `${link}` 字段错误、预览缺参提示 | `06-conflict-field-error.png` |
| 07 发送账号列表 | 本环境已配置、被引用、启停 | `07-provider-list.png` |
| 08 停用发送账号确认 | 列出受影响路由、必填原因 | `08-disable-provider.png` |
| 09 发送记录列表 | 时间范围等筛选、状态标签、分页 | `09-record-list.png` |
| 10 发送记录详情 | 抽屉：请求信息、逐个投递与尝试 | `10-record-detail.png` |

文字线框如下，以 Pencil 画板为准。三个页面都在顶层分组「通知管理」下，样式沿用现有运营配置页（`.page-heading`、`.table-panel`、`Tag`、`Drawer`、错误通知）。没有维护按钮权限时，写操作按钮不显示（沿用 Vault 页面做法）。

### 4.1 通知模板 `/notification-templates`

```
通知模板                                                  [新建模板]
[编码搜索____] [状态 ▾全部]                                 共 12 条 ⟳
┌──────────────────────────────┬──────┬──────┬──────────┬──────────┬──────────┐
│ 编码                          │ 状态 │ 用途 │ 语言      │ 路由     │ 更新时间  │
├──────────────────────────────┼──────┼──────┼──────────┼──────────┼──────────┤
│ CLIENT_PASSWORD_RESET        │ 启用 │ 认证 │ zh-CN    │ 启用     │ 10-15 16:00│
│ PAYMENT_REFUND_SUCCEEDED_EMAIL│ 停用 │ 普通 │ zh-CN en-US│ 未配置 │ ...      │
└──────────────────────────────┴──────┴──────┴──────────┴──────────┴──────────┘
```

- "新建模板"弹窗：编码、原因。
- 点击行进入详情 `/notification-templates/{templateCode}`：

```
通知模板 / CLIENT_PASSWORD_RESET   [认证邮件] [启用]            [停用模板]
┌ 发送路由 ─────────────────────────────────────────────────────┐
│ 主账号 SES_PRIMARY  备用账号 —  状态 启用   [修改路由] [停用路由] │
└───────────────────────────────────────────────────────────────┘
┌ 语言内容 ─ [zh-CN] [en-US] [+ 新增语言] ──────────────────────┐
│ 标题  [重置密码______________________________]                 │
│ 正文  ┌──────────────────────────┐  预览                       │
│       │请打开 ${link} 重置密码    │  参数 link [https://…]      │
│       └──────────────────────────┘  标题：重置密码              │
│ 占位符：${link}（必填）              正文：请打开 https://… 重置密码│
│                                     未提供参数：—               │
│                                               [原因___] [保存] │
└───────────────────────────────────────────────────────────────┘
```

- 预览：编辑时停顿约 500ms 自动调用预览接口；参数输入框按正文里出现的占位符自动生成。
- 认证邮件模板：顶部显示"认证邮件"标签与提示"注册验证 / 找回密码使用，正文必须包含 ${link}"；停用模板或路由时弹窗说明"停用后注册验证（或找回密码）邮件将无法发送"，必须填写原因。
- 保存冲突（409）：显示横幅"该内容已被他人修改"，保留草稿，提供"重新加载"。
- 字段错误（400 / 422）：显示在对应输入框下。
- 离开未保存的编辑时确认。

### 4.2 发送账号 `/notification-providers`

```
发送账号                                                        共 1 条 ⟳
┌──────────────┬──────┬──────┬────────────┬───────────────────────────┬──────┐
│ 编码          │ 介质 │ 状态 │ 本环境已配置 │ 被引用                     │ 操作 │
├──────────────┼──────┼──────┼────────────┼───────────────────────────┼──────┤
│ SES_PRIMARY  │ EMAIL│ 启用 │ 是          │ CLIENT_PASSWORD_RESET（主）… │ 停用 │
└──────────────┴──────┴──────┴────────────┴───────────────────────────┴──────┘
提示：发件地址、区域与凭据由部署配置维护，不在此修改。
```

停用被启用路由引用的账号时，弹窗列出受影响的模板并要求原因。

### 4.3 发送记录 `/notification-records`

```
发送记录
[时间 最近 7 天 ▾][来源系统___][来源事件键___][模板___][投递状态 ▾]  [查询]
┌────────────┬──────────────────┬────────────┬──────────┬──────┬──────────┐
│ 时间        │ 来源 / 事件键      │ 模板        │ 收件人数 │ 状态 │ 请求 ID   │
├────────────┼──────────────────┼────────────┼──────────┼──────┼──────────┤
│ 10-15 16:00│ payment… / refund…│ PAYMENT_…  │ 1        │ 已发送│ 8800…   │
└────────────┴──────────────────┴────────────┴──────────┴──────┴──────────┘
```

点击行打开右侧抽屉：请求信息、参数名列表；每个投递一张卡片（状态、语言、尝试次数、下次重试时间），卡片内按时间列出尝试（账号、状态、响应码、确认来源、时间）。邮箱不显示。

状态标签：待发送 / 处理中 / 等待重试（蓝）、已发送 / 已送达（绿）、失败（红）、结果未知（橙）。

### 4.4 Web 共享文件（增量）

`navigation/pageCapabilities.ts`（3 项及按钮）、`navigation/ConsolePage.tsx`（3 个懒加载组件）、`navigation/menu.ts`（模板详情子路由）、`configuration/configurationModel.test.ts`（页面数量 +3）、`pages/auditView.ts`（6 个审计动作的中文名）。新增目录 `src/notification/`（模型、解码、Hook、内容编辑器）与 `src/pages/Notification*.tsx`。

## 5. 研发与验证计划

| 顺序 | 系统 | 内容 | 验证 | 对应 AC |
|---|---|---|---|---|
| 1 | Platform Delivery | Mapper / Repository 方法、`NotificationManagementService`、`EmailProviders.isConfigured`、Repository 去掉启用条件 | 现有通知测试全部通过 | AC-02、AC-08 |
| 2 | Platform API / Web | `NotificationManagementApi`、Controller、12 个 Processor、权限常量、锁配置 | `auto/ext/http/operations/notification/` 下按入口分 4 个测试类（模板与内容、路由、账号、记录），继承 `OperationsApiTestBase`，覆盖权限 403、校验 422/400、冲突 409、即时模板 `${link}`、预览、改后发送立即生效（经 `MockEmailProvider` 断言）、记录不含邮箱与参数值、审计记录 | AC-01—07 |
| 3 | Platform SQL | DDL、changes 文件、PDManer、注册 DML；`OperationsCatalog` 加入新权限 | Schema 与 PDManer 一致性检查；测试数据与 DML ID 对齐 | AC-09 |
| 4 | BFF | Controller、Client 端口与实现、模型、共享文件 | 基于 `PlatformOperationsSimulator` 的 JSON 驱动边界测试（12 个路由的转发、错误透传、未知字段拒绝） | AC-01、AC-06 |
| 5 | Web | 3 个页面、模型与解码、内容编辑器 | Node 测试（解码、状态标签、占位符提取、错误映射）；`tsc` 与 `vite build`；阶段 5 用浏览器对照第 4 节核对 | AC-10—12 |

- 测试数据：通知配置用 `NotificationFixture` / `NotificationTestMapper`，ID 使用新的保留段，每个测试前后清理。
- 不执行 SQL；本地测试库的表结构由测试环境既有方式提供。若新索引需要在本地测试库生效才能跑测试，阶段 4 开始前会单独请求授权，否则测试不依赖该索引（查询结果相同，只是没走索引）。

## 6. 开放项

无需要你决定的业务问题。以下按本文处理，如有异议请在确认时指出：

1. 新建模板默认停用，启用前必须有至少一条语言内容。
2. 预览缺少参数时不报错，列出缺失项。
3. 发送记录时间跨度最多 31 天，默认最近 7 天。
4. 正文模板最多 60000 字节、标题模板 1024 字节（受 Gateway 64 KiB 请求体上限约束）；预览参数同样计入该上限。

## 7. 实现偏差（阶段 4）

| # | 方案 | 实现 | 原因 |
|---|---|---|---|
| 1 | 内容不符合发送规则返回 400 并定位字段 | 返回 **422 VALIDATION_ERROR**，`errors[].field` 为 `subjectTemplate` / `bodyTemplate` | Foundation 的字段错误机制（`RequestBodyValidationException`）固定为 422；400 不带字段 |
| 2 | 响应字段 `transient` | 字段名为 `transientTemplate` | `transient` 是 Java 关键字 |
| 3 | 审计对象均为模板 | 内容保存记为 `NOTIFICATION_CONTENT`（对象 `模板编码:语言`），路由记为 `NOTIFICATION_ROUTE`，新建模板的对象为模板编码 | 审计切面只接受声明过的资源类型、对象标识不允许 `/`；用写入时的真实对象比占位编码更准确 |
| 4 | BFF 本地模型按规范用 class，逐字段转换 | BFF 模型沿用周围代码的 **record**；转换用开启"缺少基本类型字段即失败"的严格 Jackson | BFF 严格 Body 解析器按 record 字段检查（`getRecordComponents`），改为 class 需改共享解析器；字段与 Owner 合同一一对应 |
| 5 | — | Owner 响应多出的字段在 HTTP 客户端解码时即被丢弃，不投影到浏览器（返回 200），不视为 502 | 与 Client API 页面的既有断言一致 |
| 6 | 页面字段错误显示具体文案 | BFF 沿用既有规则，422 只保留字段路径、文案统一为安全提示；`${link}` 缺失由页面按 `requiredParameters` 自行提示并禁止保存 | 不外露上游文本的既有安全边界 |
| 7 | 离开未保存的编辑时确认 | 只在切换语言标签页时确认；路由跳转不拦截 | 现有页面框架没有通用离开拦截；避免为此改共享导航 |
| 8 | — | 只读人员看不到预览区（预览需要 `NOTIFICATION_MANAGE`） | 预览接口按方案需要维护权限 |
| 9 | — | `platform-support-api` 增加 Lombok（optional、仅编译期）用于新请求 / 响应 class | 12 个接口的 Body 手写访问器代价过高；不传递给依赖方 |
| 10 | — | 管理服务实现随 `operations.authentication.enabled` 装配（需要该配置下的 ID 生成器）；7 个通知仓储去掉 `notification.enabled` 条件 | 方案 1.1 的"不依赖 notification.enabled" |
| 11 | — | `NotificationProviderRepositoryImpl.java` 中其他会话的未提交改动（仅删除未用 import）随本次重写一并包含 | 本次需要改写该文件；改动内容与本需求一致，无功能影响 |
| 12 | — | 删除转换器中"请求 ID 为空"的不可达判断 | Path 变量不会为空；按规范删除不影响正确性的代码 |
| 13 | 注册 ID 块 `81xxx`～`84xxx` | 改为 `86xxx`～`89xxx`（阶段 5 发现） | 原段已被 Vault 管理需求目录中的注册 SQL 占用并已在本机 e2e 与 mor-test 执行；选段时只核对了初始化 SQL 文件 |
| 14 | 冲突时显示横幅 | 409 只显示页面横幅，不再弹错误通知（阶段 5 发现） | 右上角错误通知遮挡横幅中的"重新加载"按钮，且与横幅重复 |
| 15 | — | 发送记录页时间范围占两列宽（阶段 5 发现） | 单列宽度下开始、结束时间被截断 |
| 16 | — | 去掉弹窗 `maskClosable`、`Space direction` 等 antd 已废弃写法 | 浏览器控制台出现弃用警告 |

DDL、注册 DML 已于阶段 5 在本机 `mor_platform_e2e` 执行（注册块先试跑回滚核对 160 行），其他环境未执行。

