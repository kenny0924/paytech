# Client Console 接入自助 — 技术方案

> 状态：2026-10-16 夜，阶段 2 与阶段 3 按用户夜间授权连续完成，待用户醒后复核。待决项决定见 [requirement.md](./requirement.md) 第 6 节与本文第 4、10 节。

# 阶段 2：现状差异与影响分析

## 1. 需求项差异

| 需求项 | 当前行为及证据 | 缺口 | 处理 |
|---|---|---|---|
| F-01 权限底座 | PLT-I02 只有 Operations 版（`OperationsAuthorizationApi` `/authorization/v1/operations/authorize`）；`TENANT_OWNER` 是开通时按租户创建的系统角色（`ProjectTenantStatusProcessor:83-87`），没有任何 role_permission；Gateway CLIENT 路由不算权限（`ClientChannelAuthorizationManager:32-39`） | 无 CLIENT 权限判断、无客户权限码、OWNER 无授权 | 新增 CLIENT 权限判断接口；权限码与 OWNER 授权（开通时写入 + 已开通工作区补录） |
| F-02—F-05 API 凭证 | 表 `t_api_credential`（按 tenant、前缀唯一、Argon2id PHC、到期必填、`rotated_from_id`、version）与 `t_api_credential_scope`；Gateway Bearer → PLT-I03 校验已上线 mor-test；运营管理只有查看、Scope 授权查询与撤销（`ApiCredentialManagementApi`，锁 `api-credential:{id}`、CAS、重复撤销返回原结果）；**生产代码无生成凭证逻辑**（只有测试 `ApiCredentialMocker` 会 encode）；表无名称列 | 生成、轮换、客户侧列表 / 详情 / 撤销、名称、上限 | Platform 新增客户侧凭证接口；`t_api_credential` 加名称列；生成复用现有 Argon2id 编码（密码模块已有） |
| F-06—F-08 Webhook | `ee469e0` 已提交：管理 API `/webhook-admin/v1` 9 个操作、5 张表按 `tenant_id` 归属、SSRF 守卫、Vault 保存签名密钥、测试 28 例；管理 Processor 绑定 Operations 会话与权限（`WebhookManagementSupport`），按端点 / 投递 ID 的操作**不校验租户归属**（对运营的全平台视角合理）；响应含运营账号、运营备注、内部系统名、内部事件主键 | 客户侧入口、租户归属校验、脱敏 DTO、客户审计 | Platform 新增客户侧 Webhook 接口，复用 delivery 模块服务；每个按 ID 的操作先比对租户 |
| F-09 导航 | 工作区外壳写死“工作区首页” | 无开发者分组 | Web 加分组，按 BFF 返回的权限能力显示 |
| 依赖 | 租户上下文、工作区外壳在 [租户与工作区](../client-console-租户与工作区/technique.md) 研发中；Webhook 的 Vault 规则 `WEBHOOK_SIGNING_SECRET_V1` 未在任何环境登记 | — | 本机验证前在本机 Vault 登记 |

## 2. 逐系统影响

| 系统 | 影响点 | 改动类型 | 风险 |
|---|---|---|---|
| Platform access-control | CLIENT 权限判断；客户权限码；OWNER 授权；客户侧凭证接口（创建、轮换、撤销、列表、详情、Scope 目录） | 代码 + DDL（名称列）+ 初始化 DML（权限码）+ 补录 DML | 凭证生成必须使用安全随机数，密钥只出现在一次响应 |
| Platform delivery / web（Webhook） | 客户侧 Webhook 接口；服务层补“按租户读取 / 校验归属”方法 | 代码，不改表 | 文件属于另一会话已提交的模块；只新增类与少量查询方法，不改投递逻辑 |
| Gateway | 新增约 15 条 CLIENT 路由（经管理接口） | 只登记 | 无代码改动 |
| Client BFF | 开发者接口组；租户上下文 + 权限判断拦截；一次性密钥响应 no-store；工作区首页返回权限能力 | 代码、配置 | 日志必须 metadata-only，避免记录密钥 |
| Client Web | 开发者分组、凭证页、Webhook 页、一次性密钥弹窗 | 代码 | 密钥不得写入任何浏览器存储 |
| Commerce、CCT、Risk、Operations | 不改 | 无 | — |

## 3. 风险

1. 没有二次认证：会话被盗即可创建凭证（已在需求 N6 接受，建议后续补 step-up）。
2. 只有 `webhook.ping` 可收：Commerce 事件源未实现。
3. Webhook 模块正由另一会话部署 mor-test；本需求的 Platform 改动在发布时要与其版本合并。
4. 本机 `mor_platform` 测试库可能被其他会话并发使用。

## 4. 阶段 2 决定（夜间按建议）

| 编号 | 决定 |
|---|---|
| S1 | 客户侧接口由 Platform 从会话本身解析租户（重验租户上下文 Session），不信任请求头里的租户 ID |
| S2 | OWNER 授权：开通（首次 ACTIVE 投影）时写入 4 个权限；已开通工作区用一次性补录 DML |
| S3 | Webhook 客户入口放在 Platform web 模块新包 `clientwebhook`，复用 delivery 服务；不改 Operations 入口 |

# 阶段 3：方案、接口与数据设计

**规模（本次方案）：** 涉及 4 个系统改代码（Platform、Client BFF、Client Web）加 Gateway 路由登记；按业务操作去重新增 17 个接口（权限判断 1、凭证 6、Webhook 9、工作区首页能力为调整 1）；修改 1 张表、新增 1 个字段，新增表 0、索引 0；业务 DML：新增 4 个权限码，开通授权逻辑写 4 行 / 工作区，已开通工作区补录 4 行 / 工作区；Gateway 路由新增 15 条；页面新增 2 个（API 凭证、Webhook，含详情抽屉与一次性密钥弹窗），修改 1 处（工作区侧边栏）。

## 5. 架构

```
浏览器（租户 Token）──► Gateway（CLIENT_JWT）──► Client BFF
   BFF 拦截器：上下文必须 TENANT → Platform CLIENT 权限判断（PLT-I02）→ 放行
   BFF ──► Platform 客户侧凭证 / Webhook 接口（只带账号 + 会话 Header）
            Platform：重验租户 Session → 取 tenantId → 业务操作（按 ID 的操作先校验归属）
```

## 6. Platform 接口（私网）

公共 Header：`X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`（由 BFF 透传）。所有接口要求当前 Session 为有效的 CLIENT 租户上下文，否则 401 `CLIENT_SESSION_INVALID` 或 403 `CLIENT_WORKSPACE_UNAVAILABLE`。

### 6.1 权限判断

`POST /authorization/v1/client/authorize` `{"permissionCodes":["CLIENT_API_CREDENTIAL_MANAGE"]}` → `{"granted":["CLIENT_API_CREDENTIAL_MANAGE"]}`。判定：Session 有效 → 成员 ACTIVE → 成员的有效角色授予 → 角色权限包含该码且权限 ACTIVE。BFF 用它做单次授权（要求全部在 granted 内），也用它批量取能力给导航。

### 6.2 API 凭证（`/client-api-credentials/v1`）

| 操作 | Method / Path | 权限 |
|---|---|---|
| 列表 | `GET /credentials?status=` | READ |
| 详情 | `GET /credentials/{credentialId}` | READ |
| Scope 目录 | `GET /scopes` | READ |
| 创建 | `POST /credentials` | MANAGE |
| 轮换 | `POST /credentials/{credentialId}/rotation` | MANAGE |
| 撤销 | `POST /credentials/{credentialId}/revocation` | MANAGE |

- 创建请求 `{"name":"Production server","scopeCodes":["…"],"validityDays":365}`；name 1—64 字符（去首尾空白，不允许控制字符），scopeCodes 1—64 个且都在 ACTIVE 目录内、不重复，validityDays ∈ {30,90,180,365}。
- 创建 / 轮换响应 `{"credential":{…摘要…},"secret":"<prefix>.<secret>"}`，HTTP 201，`Cache-Control: no-store`。密钥格式：前缀 `ck_` + 20 位 base62（共 23 字符，满足 8—32），`.` 后 43 位 base64url（256 位随机）；PHC 用现有 Argon2id 参数（m=19456, t=2, p=1）。
- 轮换请求 `{"expectedVersion":3,"graceMinutes":1440}`，graceMinutes ∈ {0,60,1440,10080}：新凭证继承名称、Scope 与原有效期天数；旧凭证 0 → REVOKED，否则 `expires_time = min(原到期, now+宽限)`；`rotated_from_id` 指向旧凭证。
- 撤销请求 `{"expectedVersion":3}`；已撤销返回原结果（同现有运营撤销）。
- 上限：同租户 ACTIVE 且未到期凭证 ≤ 10；创建与轮换都在锁 `api-credential:tenant:{tenantId}` 内检查（轮换新凭证计入，旧凭证宽限期内仍计入时也不超过 11——轮换不受上限阻止）。
- 按 ID 的操作：凭证不属于当前租户 → 404 `API_CREDENTIAL_NOT_FOUND`。
- 摘要字段：credentialId、name、keyPrefix、status（ACTIVE / REVOKED / EXPIRED，到期按时间推导）、scopeCodes、createdAt、expiresAt、rotatedFromId、version。
- 审计：`CLIENT_API_CREDENTIAL_CREATE/ROTATE/REVOKE`，记录账号、租户、凭证 ID、前缀，不记录密钥。

### 6.3 Webhook（`/client-webhooks/v1`）

| 操作 | Method / Path | 权限 |
|---|---|---|
| 端点列表 | `GET /endpoints` | READ |
| 新建端点 | `POST /endpoints` | MANAGE |
| 修改端点 | `PUT /endpoints/{endpointId}` | MANAGE |
| 启用 / 停用 | `POST /endpoints/{endpointId}/status` | MANAGE |
| 轮换密钥 | `POST /endpoints/{endpointId}/secret-rotation` | MANAGE |
| 测试事件 | `POST /endpoints/{endpointId}/test-events` | MANAGE |
| 投递列表 | `GET /deliveries?endpointId=&eventType=&status=&from=&to=&page=&size=` | READ |
| 投递详情 | `GET /deliveries/{deliveryId}` | READ |
| 重发 | `POST /deliveries/{deliveryId}/replay` | MANAGE |

请求字段与校验沿用运营管理接口（地址、订阅事件、说明、expectedVersion、宽限选项），去掉运营专属字段（如重发理由）；租户来自会话。按 ID 操作先查对象的 tenant_id 与会话租户比对，不一致返回 404。响应 DTO 去掉 `requestedBy`、运营备注、`sourceSystem` 与内部事件主键，公开 eventId 用来源事件键。审计动作 `CLIENT_WEBHOOK_*`。

## 7. 数据库与注册数据

| 类别 | 内容 |
|---|---|
| DDL | `t_api_credential` 新增 `credential_name VARCHAR(64) NULL COMMENT '客户为凭证填写的显示名称；历史凭证为空。'`（PDManer 模型、Schema、本机测试 DDL 同步）；增量文件 `db/ddl.sql` |
| 初始化 DML | 4 个权限码：`CLIENT_API_CREDENTIAL_READ`、`CLIENT_API_CREDENTIAL_MANAGE`、`CLIENT_WEBHOOK_READ`、`CLIENT_WEBHOOK_MANAGE`（资源类型 `CLIENT_DEVELOPER`），加入 Platform 初始化 DML |
| 补录 DML | 为已开通（owner_status ≠ PENDING）的每个 CLIENT 租户 `TENANT_OWNER` 角色补 4 行角色权限；`db/dml.sql`，可重复执行（按不存在才插入） |
| Gateway 路由 | 15 条 CLIENT 路由（凭证 6、Webhook 9，外部前缀 `/api/client/developer/v1/…`），经管理接口登记；加入登记计划脚本 |

本机执行：本机 `mor_platform` / `mor_platform_e2e` 执行 DDL 与 DML 在夜间授权内，执行记录写 `delivery.md`；mor-test 不执行。

## 8. BFF（对外经 Gateway，前缀 `/api/client/developer/v1`，BFF 路径 `/client-developer/v1`）

- 与 Platform 一一对应的 15 个转发接口；拦截器：上下文必须 TENANT，按“读 / 写”映射权限码调 Platform 权限判断，失败 403 `CLIENT_PERMISSION_DENIED`。
- 一次性密钥响应加 `Cache-Control: no-store`、`Pragma: no-cache`；新路径全部 metadata-only 日志。
- 工作区首页（W02）响应新增 `capabilities`：`["API_CREDENTIAL_READ","API_CREDENTIAL_MANAGE","WEBHOOK_READ","WEBHOOK_MANAGE"]` 中被授予的项。

## 9. 页面

- 侧边栏：工作区首页 / 开发者（API 凭证、Webhook），按 capabilities 显示。
- API 凭证页：列表（名称、前缀、Scope、状态、到期）+ 新建按钮；行操作“轮换”“撤销”；新建与轮换后弹出一次性密钥弹窗（复制、勾选“我已安全保存”才能关闭）；无 MANAGE 权限时只读。
- Webhook 页：端点列表（地址、状态、订阅数）+ 新建；端点详情抽屉（修改、启停、轮换密钥、发送测试事件）；投递列表（筛选、分页）与详情（尝试记录）+ 重发。
- 二次确认文案写清影响（撤销后立即失效、停用后待发投递取消）；空状态与失败状态分开显示；390px 可用。

## 10. 阶段 3 决定（夜间按建议）

| 编号 | 决定 |
|---|---|
| T1 | 凭证接口前缀 `/client-api-credentials/v1`、Webhook `/client-webhooks/v1`、权限判断 `/authorization/v1/client/authorize`；对外统一 `/api/client/developer/v1` |
| T2 | 密钥前缀 `ck_` + 20 位 base62；secret 256 位 base64url |
| T3 | 轮换不受 10 个上限阻止 |
| T4 | 投递列表按创建时间倒序，size ≤ 50 |

## 11. 研发与验证计划

| 顺序 | 系统 | 内容 | 验证 |
|---|---|---|---|
| 1 | Platform | 权限码与 OWNER 授权（开通写入 + 补录 DML）；CLIENT 权限判断；凭证生成 / 轮换 / 撤销 / 查询；客户侧 Webhook 接口与归属校验；DDL 与模型同步 | HTTP 入口 auto：创建后能经 PLT-I03 校验通过、轮换宽限、撤销、上限、跨租户 404、无权限 403；Webhook 跨租户 404、脱敏字段、测试事件与重发 |
| 2 | Client BFF | 开发者接口组、拦截器、no-store、capabilities | loopback 上游 auto |
| 3 | Client Web | 侧边栏分组、两个页面、一次性密钥弹窗 | `node --test` + 构建 + 浏览器 |
| 4 | 本机全链路 | 本机 Vault 登记 Webhook 规则、执行 DDL / DML、登记路由；创建凭证后用它经 Gateway 调 Client API | 场景脚本 + 浏览器 |

验收映射：AC-01/06/08 → 归属与上下文校验；AC-02—05 → 凭证接口与 Gateway 实测；AC-07/09/10 → Webhook 接口；AC-11 → 权限判断与 capabilities；AC-12 → 日志与 no-store 检查；UI-01—04 → Web 与浏览器。
