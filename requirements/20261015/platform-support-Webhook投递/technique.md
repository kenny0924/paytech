# Platform Support Client Webhook 投递：现状差异与方案设计（阶段 2+3）

> 状态：评审稿（2026-10-15）。需求见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)。本文是方案，不表示已实现或已执行。

**规模（本次方案）：** 涉及 1 个系统（Platform Support；Commerce 与 Operations 本期不改代码）；新增 10 个接口（内部事件受理 1 个、运营管理 9 个）；新增 5 张表、56 个字段、8 个非主键索引；PDManer 删除从未建表的 8 张旧 Webhook 表；旧表 0 改动；注册 DML 4 行（权限 2、角色授权 2）；业务 DML 0 行；页面 0 个（Operations 页面另立需求）。

已读：[开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[公共写接口示例](../../../agent-docs/examples/WRITE_API_EXAMPLE.md)、[Commerce Client API 设计第 13 节](../../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#13-client-webhook)、[Platform 数据库文档](../../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md)，以及 Delivery 领域规划、通知内部 HTTP 接口、通知管理三份同类方案和 `NotificationDeliveryHandler` 源码。

## 1. 现状差异

| 需求项 | 当前状态与证据 | 缺口 | 处理 |
|---|---|---|---|
| 数据模型 | PDManer 有 8 张 `t_webhook_*`；`platform-support-schema.sql` 没有建表；数据库从未建过 | 旧模型带 Vault URL、修订/验证、Lease/Fencing 列，与已确认决定不符 | 按第 3 节替换为 5 张表 |
| 事件受理 | 无代码；Commerce 设计约定发布 `ClientWebhookRequested`，没有定义传输方式 | 缺内部 HTTP 合同 | 新增 `POST /webhooks/v1/events`（第 2.1 节） |
| Tenant 解析 | `t_access_tenant (tenant_type, owner_ref_id)` 唯一；`TenantProjectionMapper.find` 已有 SQL，但 `TenantProjectionService` 没有公开查询方法 | Delivery 模块不依赖 access-control | 给 `TenantProjectionService` 增加 `findClientTenant(clientId)`；由 Web 的 Processor 组合 |
| 投递执行 | `t_compensation_event` 与 Foundation 补偿机制已在用（`NotificationDeliveryHandler`） | 无 Webhook Handler | 新增 `WebhookDeliveryHandler` |
| 签名密钥 | Vault Client 已在 Delivery 模块使用（邮箱保护、SES 凭据） | 无密钥数据类型与策略 | 新增 Vault 数据类型 `WEBHOOK_SIGNING_SECRET_V1`（第 5 节，Vault 侧待执行） |
| RFC 8785 | `paytech-json-canonicalization`（`Rfc8785JsonCanonicalizer`）已由 CCT、Risk 使用 | Platform 未依赖 | Delivery 模块直接依赖 |
| 外发 HTTP | Delivery 只有 SES SDK；BOM 未单独管理 Apache HttpClient 5（Spring Boot 管理其版本） | 需要能指定 DNS 解析的客户端，防 DNS rebinding | Delivery 引入 `httpclient5`，使用自定义 `DnsResolver` |
| 运营管理 | 通知管理已有 `/notification-admin/v1`、`@Audit`、`OperationsAuthorizationPolicy`、`AbstractLockProcessor` 的成套做法 | 无 Webhook 管理 | 照同一套做法新增 `/webhook-admin/v1` |

逐系统影响：

| 系统 | 本期 | 后续 |
|---|---|---|
| Platform Support | 代码、模型、SQL 文件、文档 | — |
| Commerce | 不改。HTTP 合同变化见第 2.1 节末尾，由 Commerce 生产端需求接入 | 冻结快照、补偿任务调用本接口 |
| Operations BFF/Web | 不改 | 页面需求：BFF 转发、Gateway 路由与菜单注册 DML、页面 |
| Vault | 不改代码；数据类型与策略清单见第 5 节，待 Vault 侧执行 | — |

## 2. 接口

### 2.1 内部事件受理（Commerce → Platform）

`POST /webhooks/v1/events`。只走私网，不经过 Gateway，不注册公开 Route。

**调用方身份**：沿用通知接口的过渡信任，即私网 + `X-Caller-Service` + 部署配置白名单。

```yaml
webhook:
  http:
    callers:
      commerce-service: [order.paid, order.cancelled, order.expired, order.funds_available,
                         refund.created, refund.succeeded, refund.failed, refund.cancelled]
```

`source_system` 直接保存调用方服务名。

**请求体**：

```json
{
  "clientId": "300000000000000101",
  "sourceVersion": 5,
  "sourcePayloadHash": "9f2c…（64 位小写十六进制）",
  "payloadHash": "4b1a…",
  "envelope": {
    "eventId": "evt_3Bf8K2Qh6N9uA1",
    "eventType": "order.paid",
    "schemaVersion": "1",
    "createdAt": "2026-09-05T10:05:15Z",
    "data": { "object": { "orderNo": "ord_20260905_A7M4Q9", "version": 5 } }
  }
}
```

| 字段 | 规则 |
|---|---|
| `clientId` | 必填，十进制正整数字符串；Platform 解析为 CLIENT Tenant |
| `sourceVersion` | 必填，1..2^31-1；来源资源版本，只参与重复比较 |
| `sourcePayloadHash`、`payloadHash` | 必填，`[0-9a-f]{64}`；Platform 重新计算 `SHA-256(RFC8785(data.object))` 与 `SHA-256(RFC8785(envelope))`，必须一致 |
| `envelope` | 只允许 `eventId`、`eventType`、`schemaVersion`、`createdAt`、`data` 五个顶层键，`data` 只允许 `object`，类型为 JSON 对象 |
| `eventId` | `[A-Za-z0-9][A-Za-z0-9_-]{0,127}`；即 `source_event_key` |
| `eventType` | 必须在该调用方白名单内 |
| `schemaVersion` | 正整数的十进制字符串，即 `payload_schema_version` |
| `createdAt` | UTC RFC 3339，秒级或带小数，以 `Z` 结尾 |
| 大小 | 请求体 ≤ 8 KiB；规范化后的 Envelope ≤ 4096 字节 |

**响应**：

| 场景 | 返回 |
|---|---|
| 首次受理 | `204`。Event 与按订阅生成的 Delivery 同一事务提交；没有订阅也受理 |
| 同一 `(tenant, source_system, eventId)` 重复提交，且 `eventType`、`schemaVersion`、`sourceVersion`、两个 Hash 全部相同 | `204`，不新增记录 |
| 同上但任一不同 | `409 CONCURRENT_REQUEST`，不写库（Fail-Closed） |
| 缺少/未登记调用方、事件类型越权 | `400 BUSINESS_CHECK_FAILED`（同一提示，不暴露白名单） |
| 字段不合法、Hash 不符、超限、Envelope 结构不符 | `422` 或 `400`，不写库 |
| Client 没有 CLIENT Tenant | `400 BUSINESS_CHECK_FAILED`（"Client 未开通平台访问"） |
| 数据库不可用 | `503` |

**与 Commerce 设计 13.5 的差异**（Commerce 生产端需求按此接入）：

1. 传输方式从“发布 `ClientWebhookRequested`”改为本接口的同步调用。返回 204 即表示“Platform 已明确受理”，Commerce 据此完成本地补偿任务。
2. 请求体不再重复 `source.system`、`eventKey`、`eventType`、`payloadSchemaVersion`、`canonicalizationVersion`：前三项分别来自 `X-Caller-Service`、`envelope.eventId`、`envelope.eventType`，后两项来自 `envelope.schemaVersion` 和固定的 RFC 8785。Commerce 补偿 Payload 的内容可以保持不变，只是调用时按本合同组装请求体。

### 2.2 运营管理接口

Platform 路径前缀 `/webhook-admin/v1`，沿用通知管理的约定：

- 每个请求带 `X-Gateway-Actor-Subject`、`X-Gateway-Session-Id`。
- 写请求带 `reason`（1..500 字符）。修改已有对象时带 `expectedVersion`。
- 分页沿用公共 `PagingRequestBody`；时间为 UTC ISO-8601，ID 为字符串。
- 错误码：422 字段校验、400 业务规则、401、403、404、409 版本冲突。
- 整个前缀记入 `web.logging.metadata-only-path-prefixes`，因为创建和轮换的响应里含密钥明文。

| # | Method / Path | 权限 | 审计动作 | 说明 |
|---|---|---|---|---|
| 1 | `GET /clients/{clientId}/endpoints` | READ | — | 该 Client 的全部 Endpoint（最多 5 个，不分页） |
| 2 | `POST /clients/{clientId}/endpoints` | MANAGE | `WEBHOOK_ENDPOINT_CREATE` | 新建 Endpoint 并生成首个密钥，响应返回一次密钥明文 |
| 3 | `PUT /endpoints/{endpointId}` | MANAGE | `WEBHOOK_ENDPOINT_UPDATE` | 修改 URL 与订阅事件类型 |
| 4 | `PATCH /endpoints/{endpointId}/status` | MANAGE | `WEBHOOK_ENDPOINT_STATUS_CHANGE` | 启用 / 停用 |
| 5 | `POST /endpoints/{endpointId}/signing-secret/rotations` | MANAGE | `WEBHOOK_SECRET_ROTATE` | 轮换密钥，返回一次新密钥明文 |
| 6 | `POST /endpoints/{endpointId}/tests` | MANAGE | `WEBHOOK_ENDPOINT_TEST` | 同步发送 `webhook.ping`，不落事件/投递 |
| 7 | `GET /deliveries` | READ | — | 投递记录列表 |
| 8 | `GET /deliveries/{deliveryId}` | READ | — | 投递详情：事件 Envelope + 全部 Attempt |
| 9 | `POST /deliveries/{deliveryId}/replays` | MANAGE | `WEBHOOK_DELIVERY_REPLAY` | 生成新 Delivery 重新投递 |

**1. Endpoint 列表**

```json
[{"endpointId":"…","url":"https://hooks.example.com/code-mor","eventTypes":["order.paid","refund.succeeded"],
  "status":"ACTIVE","signingSecret":{"rotationNo":2,"createTime":"…",
  "previousValidUntil":"2026-10-15T09:00:00.000000Z"},"version":3,"createTime":"…","updateTime":"…"}]
```

`previousValidUntil` 表示上一把密钥宽限期的截止时间，不在宽限期时为 `null`。

**2. 新建** `{"url":"https://…","eventTypes":["order.paid"],"reason":"…"}`

响应 `201`：

```json
{"endpointId":"…","version":1,"signingSecret":"whsec_…"}
```

**3. 修改** `{"url":"https://…","eventTypes":[…],"expectedVersion":3,"reason":"…"}`，响应 `{"endpointId":"…","version":4}`。

- 修改 URL 会重新做安全校验。
- 已生成、尚未完成的投递在下一次尝试时使用新 URL。
- 取消订阅某事件类型后，该类型尚未完成的投递在下一次被取出时记为 CANCELLED。

**4. 启停** `{"status":"DISABLED","expectedVersion":4,"reason":"…"}`，响应 `{"endpointId":"…","version":5}`。

- 状态与当前相同时返回 400。
- 停用后，未完成的投递在被取出时记为 CANCELLED。
- 重新启用不会自动补发。

**5. 轮换** `{"immediate":false,"reason":"…"}`

响应：

```json
{"rotationNo":3,"signingSecret":"whsec_…","previousValidUntil":"…"}
```

- `immediate=true` 时旧密钥立即失效，`previousValidUntil` 为 `null`。
- 宽限期内再次轮换：更早那把密钥立即失效，只有刚被替换的那一把获得新的 1 小时宽限，所以任何时刻最多两个签名。

**6. 测试** 无请求体。响应：

```json
{"result":"SUCCEEDED","httpStatus":200,"failureCode":null,"durationMs":182}
```

- 使用当前密钥（宽限期内为两个签名）发送。
- 同样做 SSRF 校验；停用状态的 Endpoint 也可以测试。

**7. 投递列表** `GET /deliveries?clientId=…&page=1&size=20&createdFrom=…&createdTo=…&endpointId=&eventType=&status=&eventId=`

- `clientId`、`createdFrom`、`createdTo` 必填，跨度不超过 31 天。
- 按 `create_time`、`delivery_id` 倒序。

```json
{"items":[{"deliveryId":"…","eventId":"evt_…","eventType":"order.paid","endpointId":"…","status":"RETRY_WAIT",
  "attemptCount":2,"lastAttemptTime":"…","replayOfDeliveryId":null,"createTime":"…"}],"page":1,"size":20,"total":1}
```

**8. 投递详情**

列表字段，加上：

- `requestedBy`
- `event{eventId,eventType,schemaVersion,sourceSystem,envelope}`
- `attempts[{attemptNo,requestUrl,rotationNo,graceRotationNo,status,httpStatus,failureCode,durationMs,startedTime,finishedTime}]`

**9. Replay** `{"reason":"…"}`，响应 `201 {"deliveryId":"…"}`。

- 只允许对 SUCCEEDED、DEAD、CANCELLED 的投递 Replay，进行中的返回 400。
- Endpoint 必须是 ACTIVE。
- 新 Delivery 的 `replay_of_delivery_id` 指向原投递，`requested_by` 记操作人；Event ID 与 Body 不变，使用当前 URL 与密钥。

**校验规则（2、3）**：

| 规则 | 失败返回 |
|---|---|
| URL 为绝对 `https://`，有主机名，不含 userinfo 和 fragment，最长 2048 字符，主机名不能是 `localhost` | 422 |
| 保存时 DNS 解析主机名，全部地址都必须是公网地址（第 4.3 节）；解析失败也拒绝 | 400 |
| `eventTypes` 非空、不重复，只能是 V1 八种 | 422 |
| 每个 Client 最多 5 个 Endpoint（含已停用的） | 400 |

## 3. 数据设计

PDManer 删除 8 张旧表（从未建表，不需要 DROP），新增以下 5 张。所有表都不建 FK/CHECK；时间为 `DATETIME(6)` UTC；`version` 为 CAS 版本号。

### 3.1 `t_webhook_endpoint` Webhook 端点（8 个字段）

| 字段 | 类型 | 空 | 说明 |
|---|---|---|---|
| endpoint_id | BIGINT | N | 主键 |
| tenant_id | BIGINT | N | CLIENT 租户 |
| url | VARCHAR(2048) | N | HTTPS 地址明文（决定 D-01） |
| event_types | JSON | N | 订阅的事件类型数组，非空、不重复 |
| status | VARCHAR(64) | N | ACTIVE、DISABLED |
| version | INT | N | 并发版本 |
| create_time / update_time | DATETIME(6) | N | — |

索引：`idx_webhook_endpoint_tenant (tenant_id)`。

订阅直接作为端点上的数组保存，删除订阅表，理由如下：

- 订阅只是“这个端点要哪些类型”，不需要修订历史。
- 每个 Client 最多 5 个端点，分发时按 Tenant 读出 ACTIVE 端点，在内存里过滤即可。

### 3.2 `t_webhook_signing_secret` Webhook 签名密钥（10 个字段）

| 字段 | 类型 | 空 | 说明 |
|---|---|---|---|
| signing_secret_id | BIGINT | N | 主键 |
| endpoint_id | BIGINT | N | 所属端点 |
| rotation_no | INT | N | 端点内第几把，从 1 开始 |
| secret_vault_token | VARCHAR(255) | N | 密钥在 Vault 的引用 Token（决定 D-03） |
| status | VARCHAR(64) | N | ACTIVE、RETIRED |
| signing_slot | INT | Y | ACTIVE 时为 1，否则为 NULL；与 endpoint_id 组成唯一键，保证同时只有一把 ACTIVE |
| valid_until | DATETIME(6) | Y | RETIRED 后仍可签名的截止时间：宽限轮换为轮换时间 + 1h，立即作废为轮换时间 |
| version | INT | N | — |
| create_time / update_time | DATETIME(6) | N | — |

索引：`uk_webhook_secret_rotation (endpoint_id, rotation_no)`、`uk_webhook_secret_active (endpoint_id, signing_slot)`。

签名时使用 ACTIVE 那把，加上 `valid_until > now` 的 RETIRED 那把。到期不需要任务去改状态。

旧模型的 `secret_ref`、`secret_fingerprint`、`effective_from`、SCHEDULED/REVOKED 都没有消费者，删除。

### 3.3 `t_webhook_event` Webhook 事件（11 个字段）

| 字段 | 类型 | 空 | 说明 |
|---|---|---|---|
| event_id | BIGINT | N | 主键（内部 ID，不对外） |
| tenant_id | BIGINT | N | — |
| source_system | VARCHAR(64) | N | 调用方服务名 |
| source_event_key | VARCHAR(128) | N | 公开 `eventId` |
| event_type | VARCHAR(64) | N | — |
| payload_schema_version | INT | N | — |
| source_version | INT | N | — |
| source_payload_hash | CHAR(64) | N | — |
| payload_hash | CHAR(64) | N | 每次发送前用它校验重新规范化后的字节 |
| payload | JSON | N | 冻结的 Envelope（决定：只存明文 JSON，去掉 Vault 存储模式） |
| create_time | DATETIME(6) | N | 不可变，无 update_time/version |

索引：

- `uk_webhook_event_source (tenant_id, source_event_key, source_system)`：唯一。列的顺序让它同时支持“按公开 eventId 查询”。
- `idx_webhook_event_tenant_time (tenant_id, create_time)`。

MySQL JSON 不保留属性顺序，所以发送前统一重新做 RFC 8785 规范化，再与 `payload_hash` 比较；不一致则不发送（第 4.2 节）。

### 3.4 `t_webhook_delivery` Webhook 投递（13 个字段）

| 字段 | 类型 | 空 | 说明 |
|---|---|---|---|
| delivery_id | BIGINT | N | 主键；也是补偿任务的 `eventKey` |
| webhook_event_id | BIGINT | N | — |
| tenant_id | BIGINT | N | 列表查询 |
| endpoint_id | BIGINT | N | — |
| event_type | VARCHAR(64) | N | 冗余，用于列表筛选 |
| status | VARCHAR(64) | N | CREATED、PROCESSING、RETRY_WAIT、SUCCEEDED、DEAD、CANCELLED |
| attempt_count | INT | N | 已创建的尝试数，初始 0 |
| last_attempt_time | DATETIME(6) | Y | 最近一次尝试开始时间 |
| replay_of_delivery_id | BIGINT | Y | Replay 来源 |
| requested_by | BIGINT | Y | Replay 操作人账号 |
| version | INT | N | — |
| create_time / update_time | DATETIME(6) | N | — |

索引：`idx_webhook_delivery_event (webhook_event_id)`、`idx_webhook_delivery_tenant_time (tenant_id, create_time)`、`idx_webhook_delivery_endpoint_time (endpoint_id, create_time)`。

旧模型的 `subscription_id`、`generation_no`、`replay_reason_code`、`next_retry_time`、`lease_owner`、`lease_until`、`fencing_token` 删除，原因：

- 重试时间由补偿任务的 `next_execute_at` 决定。
- 并发由补偿任务的 Claim/Lease 加本表的 version CAS 保护。
- Replay 原因记在审计里。

### 3.5 `t_webhook_attempt` Webhook 投递尝试（14 个字段）

| 字段 | 类型 | 空 | 说明 |
|---|---|---|---|
| attempt_id | BIGINT | N | 主键 |
| delivery_id | BIGINT | N | — |
| attempt_no | INT | N | 从 1 开始 |
| request_url | VARCHAR(2048) | N | 本次实际请求的 URL（端点 URL 可能被修改，按此保留历史） |
| signing_secret_id | BIGINT | N | 主签名密钥 |
| grace_secret_id | BIGINT | Y | 宽限期内的第二个签名密钥 |
| status | VARCHAR(64) | N | PROCESSING、SUCCEEDED、FAILED |
| http_status | INT | Y | 收到响应时记录 |
| failure_code | VARCHAR(64) | Y | 见第 4.4 节 |
| duration_ms | INT | Y | — |
| started_time | DATETIME(6) | N | — |
| finished_time | DATETIME(6) | Y | — |
| create_time / update_time | DATETIME(6) | N | — |

索引：`uk_webhook_attempt_no (delivery_id, attempt_no)`。

### 3.6 SQL 清单

| 文件 | 内容 | 性质 |
|---|---|---|
| `platform-support-service/docs/database/sql/changes/2026-10-15-webhook-delivery.sql` | 5 条 `CREATE TABLE`；回退为 5 条 `DROP TABLE`（仅限建表后尚无数据时） | 待执行增量 |
| `platform-support-schema.sql` | 追加同样 5 张表 | 完整 Schema 快照 |
| `platform-support.pdma.json` | 删除 8 张旧表，新增 5 张 | 设计源 |
| `platform-support-initial-data.sql` | 独立块：`t_permission` 2 行（`WEBHOOK_READ` LOW、`WEBHOOK_MANAGE` HIGH）；`t_role_permission` 2 行（平台管理员，`REVOKED`，与先例一致） | 待执行注册 DML |
| 本需求 `db/ddl.sql`、`db/dml.sql` | 注释索引，指向上面两份文件 | 索引 |

- 注册 ID 块在阶段 4 开始前重新核对全仓库占用后再确定，吸取通知管理阶段 5 的教训。
- Gateway 路由、菜单、按钮的注册放到 Operations 页面需求里。
- 执行顺序：DDL → 注册 DML。本期不执行；本地测试库是否执行，在阶段 4 单独请求授权。

## 4. 执行设计

### 4.1 受理（`SubmitWebhookEventProcessor`，位于 Web）

由于需要组合 access-control（Tenant）和 delivery（Webhook），Processor 放在 Web，入口模板为 `AbstractProcessor`。

1. Controller 校验 Body 注解，Converter 解析 `clientId`；Processor 校验调用方白名单与事件类型。
2. 规范化 `data.object` 与 Envelope，校验大小和两个 Hash（纯内存）。
3. 通过 `TenantProjectionService.findClientTenant` 解析 Tenant。
4. 按 `(tenant, eventId, source_system)` 查询原事件。存在则比较五项：一致返回 204，不一致返回 409。
5. 读取该 Tenant 的 ACTIVE 端点，过滤出订阅了该类型的端点。
6. `WebhookService.accept(context)` 在一个事务里：插入 Event、为每个端点插入一条 Delivery（CREATED），并为每条 Delivery 登记补偿任务 `WEBHOOK_DELIVERY`。
7. 唯一键冲突时整个事务回滚，然后在事务外重新读取原事件并比较，结果同第 4 步。

只登记补偿任务，不同步发送；提交后由 Foundation 唤醒执行。

### 4.2 投递（`WebhookDeliveryHandler`，位于 Delivery 的 `ext/eventhandler`）

一次 Handler 执行最多做一次 HTTP 尝试。

| 步骤 | 内容 | 事务 |
|---|---|---|
| 1 | 读取 Delivery、Event、Endpoint 和可用密钥。已是终态则直接完成 | 无 |
| 2 | Endpoint 已停用，或已不再订阅该类型：CAS 改为 CANCELLED，完成 | 短事务 |
| 3 | 状态为 PROCESSING（上次执行者在发送中崩溃）：把遗留的 Attempt 记为 FAILED / `INTERRUPTED` | 短事务 |
| 4 | 重新做 RFC 8785 规范化，与 `payload_hash` 比较；不一致返回 `terminalFailure("WEBHOOK_PAYLOAD_INTEGRITY")`，留给人工处理 | 无 |
| 5 | 从 Vault Reveal 主密钥和宽限密钥。失败则返回可重试，不消耗尝试次数 | 无 |
| 6 | CAS 把 Delivery 改为 PROCESSING，`attempt_count + 1`，写 `last_attempt_time`；插入 PROCESSING 的 Attempt | 短事务 |
| 7 | 计算签名，解析 DNS 并校验地址，发送 HTTP | 无 |
| 8 | 写 Attempt 结果。2xx 记 SUCCEEDED；否则次数未满记 RETRY_WAIT，满了记 DEAD | 短事务 |

- 达到 SUCCEEDED、DEAD、CANCELLED 后，补偿任务记为成功：DEAD 是正常的业务终态，不需要补偿任务再报警。RETRY_WAIT 时返回可重试。
- 所有 CAS 都带 version；影响行数为 0 时返回 `retryableFailure("WEBHOOK_CONCURRENT")`，沿用通知的做法。

**重试节奏**：交给补偿策略控制，`CompensationExecutionPolicy(timeout 30s, maximumAttempts 20, initialBackoff 1m, maximumBackoff 24h, multiplier 3, jitter 0.2)`。

- 投递尝试上限 `webhook.delivery.maximum-attempts=10`。
- 两次尝试之间约为 1m、3m、9m、27m、81m、4h、12h、24h、24h，10 次总计约 2.8 天。
- 这与阶段 1 默认的“约 3 天”同一量级，但改为公共机制的等比退避，不另写一套计划。
- 补偿上限 20 次，大于 10 次，是为了 Vault 不可用、并发冲突等不消耗尝试次数的重试留出余量。

### 4.3 HTTP 发送与 SSRF 防护（`integration/webhook`）

- 使用 Apache HttpClient 5 classic，自定义 `DnsResolver`：解析出的所有地址都必须是公网地址，否则抛出 `BLOCKED_ADDRESS`；连接只使用这次解析的结果，避免 DNS rebinding。TLS 仍按主机名校验证书。
- 禁止跟随重定向。连接超时 5s，整体超时 10s。响应体最多读取 1 KiB 后丢弃。
- 拒绝的地址：
  - IPv4：`0/8`、`10/8`、`100.64/10`、`127/8`、`169.254/16`、`172.16/12`、`192.0.0/24`、`192.168/16`、`198.18/15`、`224/4`、`240/4`。
  - IPv6：`::`、`::1`、`fc00::/7`、`fe80::/10`、`ff00::/8`；IPv4 映射地址按其 IPv4 判断。
- 请求：`POST`，`Content-Type: application/json`，`User-Agent: Code-MoR-Webhook/1`，Body 为规范化后的字节。
- 签名 Header：`Code-MoR-Signature: t=<秒>,v1=<主签名>[,v1=<宽限签名>]`。
  - `HMAC-SHA256(key = 密钥字符串的 UTF-8 字节, data = t + "." + body)`，结果为小写十六进制。
- 自动测试需要访问本机的测试端点，所以保留两个部署配置：`webhook.delivery.allowed-schemes`（默认 `[https]`）和 `webhook.delivery.trusted-networks`（默认空，可放行指定网段）。
  - 只在测试配置中放开 `http` 和 `127.0.0.1/32`，生产配置不设置。
  - 这是网络策略配置，不是测试分支，生产代码路径相同。

### 4.4 Attempt 失败码

`HTTP_STATUS`（非 2xx，含 3xx）、`TIMEOUT`、`CONNECT_FAILED`、`TLS_FAILED`、`DNS_FAILED`、`BLOCKED_ADDRESS`、`INTERRUPTED`。

不保存响应体，也不保存异常原文。

### 4.5 密钥生成与轮换（`RotateWebhookSecretProcessor` 等，位于 Web）

- 生成 32 字节随机数，编码为 `whsec_` 加 43 位 Base64URL。在事务外以用途 `WEBHOOK_SIGNING` 做 Vault Protect，拿到 Token。
- 锁为 `webhook-endpoint:{endpointId}`（TRY_LOCK）。事务内依次：
  1. 把仍在宽限期的旧 RETIRED 密钥的 `valid_until` 改为当前时间。
  2. 把 ACTIVE 那把改为 RETIRED，`valid_until` 为当前时间 + 1h；立即作废时为当前时间。
  3. 插入新 ACTIVE 密钥，`rotation_no + 1`。
- 事务失败时，Vault 里会留下一条没有引用的记录。只会出现在异常情况下，接受这一点，不另做清理。
- 新建 Endpoint 时，同样先 Protect，再在同一事务插入 Endpoint 和第 1 把密钥。锁为 `webhook-client:{tenantId}`，用于限制每个 Client 5 个。

### 4.6 模块与类落位

| 模块 | 新增 |
|---|---|
| `platform-support-api` | `webhook/WebhookEventApi`（内部受理）、`webhookmanagement/WebhookManagementApi` 及请求/响应 Body；`api/enums/WebhookEventType`（V1 八种 + `webhook.ping`） |
| `platform-support-delivery` | 5 个 DataObject / Mapper / Repository；`WebhookService`、`WebhookManagementService`（interface + Impl）；`WebhookDeliveryHandler`；`integration/webhook/WebhookHttpSender`、`WebhookSecretVault`；`core/configuration/WebhookProperties`；`core/enums/*` |
| `platform-support-access-control` | `TenantProjectionService.findClientTenant`；权限常量 `WebhookManagementPermissions` |
| `platform-support-web` | `ext/rest/webhook/WebhookEventController`、`ext/rest/webhookmanagement/WebhookManagementController`；`service/processor/webhook/` 下的受理与管理 Processor；`WebhookCallerProperties`；`application.yml` 增加 `webhook.*` 配置和 metadata-only 前缀 |

`webhook.enabled` 默认为 false：Handler、受理接口和发送器随它启用。管理接口与通知管理一样，随 `operations.authentication.enabled` 启用。

## 5. Vault 变更清单（不在 Platform 范围，待执行）

| 项 | 内容 |
|---|---|
| 数据类型 | `WEBHOOK_SIGNING_SECRET_V1`：密钥类，`REFERENCE` Token，值格式 REGEX `^whsec_[A-Za-z0-9_-]{43}$`，不允许人工加密、人工解密 |
| 规则 | `platform-support-service / PROTECT / WEBHOOK_SIGNING_SECRET_V1 / WEBHOOK_SIGNING`；`platform-support-service / REVEAL / WEBHOOK_SIGNING_SECRET_V1 / WEBHOOK_SIGNING` |

自动测试使用 Vault Mock，不依赖上述策略。

## 6. 文档同步（阶段 4）

- **决定记录**：新增 `agent-docs/decisions/2026-10-15-platform-webhook-delivery.md`，写明 D-01 至 D-06 和第 2.1 节的传输/合同变化分别取代了哪些内容。
- **Platform 文档**：
  - `PLATFORM_SUPPORT_DATABASE.md` 第 9 节和表数统计、`PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`。
  - Delivery 领域规划，在顶部加修订说明。
  - Platform README、`CURRENT_IMPLEMENTATION.md`。
- **Vault 架构**：在 `VAULT_SERVICE_ARCHITECTURE.md` 中 Webhook URL 和 Secret 两处的描述上加取代说明。
- **Commerce 文档**：Commerce 设计 13.5、13.6 归 Commerce 所有。本需求只在第 13 节开头加一行，指向决定记录，不改正文；正文由 Commerce 生产端需求同步。

## 7. 研发与验证计划

| 顺序 | 内容 | 验证 | 对应 AC |
|---|---|---|---|
| 1 | PDManer、Schema、changes 文件、注册 DML | 模型与 Schema 一致性检查 | AC-12 |
| 2 | Delivery：DataObject/Mapper/Repository、Service、发送器、Vault 适配、Handler | — | — |
| 3 | access-control：Tenant 查询、权限常量 | 现有测试 | — |
| 4 | API/Web：受理与管理 Controller、Processor、配置 | — | — |
| 5 | 自动测试，按入口分类，全部走真实链路：<br>① `WebhookEventController` 受理：白名单、Hash、重复/冲突、无订阅、无 Tenant、不写库<br>② `WebhookDeliveryHandler`：成功、非 2xx/3xx/超时重试、第 10 次 DEAD、停用 CANCELLED、PROCESSING 恢复、并发 CAS、Hash 不符、Vault 不可用不计次数<br>③ 管理接口：权限 403、校验 422/400、409 冲突、5 个上限、轮换双签与立即作废、测试事件、Replay<br>④ SSRF：内网地址在保存和发送时都被拒绝 | 测试端点由测试模块提供：记录收到的 Header/Body，按配置返回状态码或延迟，用以验签。Vault 使用现有 Mock。本地 MySQL/Redis 使用 `local-environment`。`./mvnw -o verify` 全量通过 | AC-01—11 |
| 6 | 文档同步 | `npm --prefix requirements-site test`（区分已有失败） | AC-13 |

阶段 4 需要在本地测试库建这 5 张表。届时我会单独请求授权，在本机 `local-environment` 的 Platform 测试库执行 changes 文件。

## 8. 风险与取舍

| 项 | 说明 |
|---|---|
| 过渡信任 | 私网内的服务可以冒充 `commerce-service` 提交事件，影响是向该 Client 已配置的 Endpoint 推送伪造事件。Client 应以 GET 为准（Commerce 合同已有此要求）。强服务身份到位后只需替换身份识别层 |
| 至少一次 | 超时、崩溃后会重发，Client 必须按 `eventId` 去重（合同已有） |
| 测试用的网络放行配置 | 生产配置不设置；启动时打印生效的放行网段，便于核查 |
| Vault 孤儿记录 | 新建/轮换事务失败时可能留下未引用的密钥记录；不可被使用，接受 |
| 补偿与通知共用执行资源 | Webhook 积压时会和通知争抢扫描批次。阶段 4 核对 Foundation 是否支持按事件类型配置并发，不支持则记为遗留项 |

## 9. 实现偏差（阶段 4）

| # | 方案 | 实现 | 原因 |
|---|---|---|---|
| 1 | `url` 与 `request_url` 最长 2048 | 最长 1000 字符 | 审计属性值上限 1000 字符，端点地址要能原样进入配置审计 |
| 2 | 扩展 `TenantProjectionService.findClientTenant` | 新增 `ClientTenantQueryService`（interface + Impl） | `TenantProjectionService` 只在 `tenant.projection.enabled` 开启时存在 |
| 3 | Replay 按投递加锁 | 不加锁 | 原投递始终是终态，锁挡不住重复点击；重复 Replay 只多发一次，Client 按 eventId 去重 |
| 4 | 审计动作 `WEBHOOK_*` | `OPERATIONS_WEBHOOK_*` | 与其他 Operations 管理接口的审计动作命名一致 |
| 5 | 轮换响应为普通 Body | `ResponseEntity` 且 `Cache-Control: no-store` | 响应含密钥明文，与新建端点一致 |
| 6 | — | 管理接口同时要求 `operations.authentication.enabled` 与 `webhook.enabled` | 新建与轮换需要 Vault，测试事件需要发送器 |
| 7 | — | 写操作先在只读事务里确认 `WEBHOOK_MANAGE`，再做 Vault 与 DNS，写事务内重检 | 无权限请求不触发外部调用 |
| 8 | — | 事件受理 Body 解析开启重复键检测 | 重复键会让摘要与解析结果不一致 |
| 9 | 索引 `idx_webhook_delivery_tenant_time` 等 | `idx_webhook_delivery_tenant`、`idx_webhook_delivery_endpoint` | PDManer 生成器限制索引名 ≤ 32 字符 |
| 10 | — | Delivery 模块依赖 `platform-support-api` | 事件类型目录 `WebhookEventType` 发布在 api 模块，Integration 模块已有同样依赖 |
| 11 | — | PDManer 清单补入通知管理手改的 `idx_notify_request_create_time`，以及“通知发送账号维护”会话的 6 个 `t_notification_provider` 字段（`provider_type` 注释按生成规则加“取值来源…非固定枚举”，同步到该需求的 4 个 SQL 文件） | 模型由清单整体生成，未进清单的改动会在生成时丢失 |
| 12 | — | 测试配置 `webhook.maximum-attempts=3`、超时 1 秒，放行 `http` 与 `127.0.0.1/32` | 缩短 DEAD 与超时用例；接收端是本机 Mock 服务 |
