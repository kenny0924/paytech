# Operations 通知发送账号维护：方案、接口与数据设计（阶段 3）

> 状态：阶段 3 已确认（2026-10-15）。需求见 [requirement.md](./requirement.md)，现状分析见 [analysis.md](./analysis.md)。本文是方案，不表示已实现或已执行。

**规模（本次方案）：** 涉及 3 个目录（Platform Support、Operations BFF、Operations Web）及 Vault 策略数据 1 条规则；新增 4 个接口、调整 2 个接口（列表响应加字段、启用加校验）；新增 0 张表，旧表 `t_notification_provider` 新增 6 个字段；业务 DML 1 行（`SES_PRIMARY` 补类型与区域）；注册 DML 56 行（权限 1、角色授权 1、按钮 2、Gateway 路由 4、路由 Header 24、限流规则 24）；页面：发送账号页改造 1 个，Pencil 修改 1 个画板、新增 4 个弹窗画板。

## 1. 业务与架构

### 1.1 账号模型

`t_notification_provider` 一行即一个可发信账号，Platform 是唯一 Owner：

| 字段 | 说明 |
|---|---|
| `provider_type` | 服务商类型，字符串；由已注册的发送适配器声明（本期生产只有 `AMAZON_SES`），创建后不可改 |
| `region` | 服务商区域；SES 必填，如 `me-central-1` |
| `from_address` | 发件地址；SES 必填，须为该区域已验证身份（本期不在线校验，用测试发送验证） |
| `configuration_set` | SES Configuration Set，可空 |
| `credential_vault_token` | 密钥对 `{"accessKeyId","secretAccessKey"}` 在 Vault 中的 Token；可空（未录入） |
| `credential_update_time` | 最近一次录入 / 更换密钥的时间；可空 |

**配置完整** = 类型对应的必填字段齐全且 `credential_vault_token` 非空（SES：`region`、`from_address`、Token）。只有配置完整的账号可以启用；已启用的旧账号（`SES_PRIMARY`）不强制停用，发送时缺 Token 失败关闭。

部署配置只保留环境级参数：`notification.ses.enabled`（是否在本环境注册 SES 适配器）、`endpoint`（测试终端覆盖）、`connect-timeout`、`read-timeout`。删除 `provider-code`、`region`、`from-address`、`configuration-set`、`credential-token` 及"Token 为空走 AWS 默认凭据链"。

### 1.2 发送适配器（R-01）

```java
/** 邮件服务商适配器；按 providerType 注册，账号配置随每次调用传入。 */
public interface EmailProvider {
    String providerType();                                   // 如 AMAZON_SES；测试模块注册测试类型
    default boolean supportsQuery() { return true; }
    String requestNo(long attemptId);
    List<String> missingConfiguration(NotificationProvider account);   // 空表示配置完整（含 Token）
    EmailSender prepare(NotificationProvider account);       // 事务外：Reveal 密钥，返回只用于本次的发送器
    EmailProviderResult query(NotificationProvider account, String requestNo, String messageId);
}
```

- `EmailProviders`：`require(NotificationProvider account)` 按 `provider_type` 精确匹配唯一适配器，匹配不到抛 `ServiceUnavailableException`（同现状）；`types()` 返回已注册类型；`isRegistered(type)`。
- 三个调用点改为传账号行：`NotificationDeliveryHandler`、`SendTransientEmailProcessor`、`NotificationConfigurationServiceImpl.requireProvider`（提交时校验适配器存在且配置完整）。
- 测试模块的 `MockEmailProvider` / `FallbackEmailProvider` 改为声明测试类型（如 `TEST_EMAIL`、`TEST_EMAIL_FALLBACK`），测试 Fixture 插入账号时写该类型；生产代码不出现测试类型。

### 1.3 SES 适配器

- `SesEmailConfiguration`：条件仍为 `notification.enabled && notification.ses.enabled`；注册一个 `SesEmailProvider`（类型 `AMAZON_SES`）与 `SesClientCache`。
- `SesClientCache`：按 `region` 缓存 `SesV2Client`（终端覆盖、超时来自部署配置），容器关闭时统一关闭；客户端级凭据一律拒绝（同现状），每次请求用 Vault 密钥覆盖。
- `prepare(account)`：校验配置完整 → 以 `NOTIFICATION_DELIVERY` Reveal 该账号 Token（`SesCredentialVault` 改为按 Token 调用，不再持有固定 Token）→ 返回带区域客户端、发件地址、Configuration Set 与本次凭据的发送器。缺 Token 或字段：抛出，尚未登记尝试，任务稍后重试（同 Vault 不可用的处理）。
- 错误分类、`SES_CREDENTIAL_REJECTED` 可重试、不重试 SDK 等规则不变。

### 1.4 写操作流程

| 操作 | 流程 |
|---|---|
| 新建 / 编辑 / 启停 | 同通知管理：`AbstractLockProcessor` 锁 `notification-provider:{code}` → 编程式事务（RC）→ 鉴权 → 读当前行 → 比较 `expectedVersion` → 规则校验 → `version = version + 1` 条件更新 → 审计 |
| 录入 / 更换密钥 | 锁 `notification-provider:{code}` → 只读事务：鉴权（`NOTIFICATION_CREDENTIAL_MANAGE`）、读行、比较版本 → **事务外** `VaultClient.protect(NOTIFICATION_PROVIDER_CREDENTIAL_V1, REFERENCE, json, NOTIFICATION_PROVIDER_SETUP, key)` → 短事务：按版本更新 `credential_vault_token`、`credential_update_time`、`version + 1`，记审计 |
| 测试发送 | 只读事务：鉴权（`NOTIFICATION_CREDENTIAL_MANAGE`）、读账号 → **事务外** `require(account).prepare(account).send(...)` 一次 → 审计（结果码、收件邮箱摘要） |

- 密钥 JSON 在 Platform 内存中组装，`ProtectCommand.toString()` 已脱敏；Vault 幂等键 `notification-provider-credential.{providerId}.{随机 UUID}`（Vault 幂等键只允许 `[A-Za-z0-9._~-]`）（Gateway 负责 HTTP 重放，Platform 不跨请求复用）。
- Protect 失败：数据库不变，返回 503「Vault 暂不可用或未授权」；Protect 成功但版本冲突：返回 409，新密文无人引用（Vault V1 无 Retire，接受），审计记失败。
- 锁覆盖 Vault 调用（Vault 超时 < 租约 30 s）；事务内不调用 Vault / SES。
- 测试发送不检查账号状态（允许启用前验证），但要求配置完整；不创建请求、投递、尝试记录；`requestNo` 用 Platform ID 生成器的新 ID。

### 1.5 校验规则

| 字段 | 规则 | 失败 |
|---|---|---|
| `providerCode` | `[A-Z][A-Z0-9_]{0,63}`，唯一，创建后不可改 | 422 / 400「账号编码已存在」 |
| `providerType` | 必须是本环境已注册的适配器类型 | 400「本环境未启用该服务商类型」 |
| `region` | `[a-z]{2}(-[a-z]+)+-[0-9]`，≤32 | 422 |
| `fromAddress` | 平台邮箱规则（沿用 `SesEmailProperties` 的规范化），≤254 | 422 |
| `configurationSet` | 可空；`[A-Za-z0-9_-]{1,64}` | 422 |
| `accessKeyId` | `[A-Z0-9]{16,128}` | 422（不回显值） |
| `secretAccessKey` | 1..128 个可见 ASCII 字符，无空白 | 422（不回显值） |
| 测试收件 `email` | 平台邮箱规则 | 422 |
| 启用 | 配置完整 | 400「账号配置不完整：缺少 …」（只列字段名） |

## 2. 接口

公共约定同[通知管理 technique.md 第 2 节](../operations-console-通知管理/technique.md)：前缀 `/notification-admin/v1`（BFF、Platform 已 metadata-only）、Gateway Actor / Session Header、`reason` 1..500、422/400/401/403/404/409、幂等归 Gateway。

### 2.1 接口清单

| # | Method / Path | 权限 | 审计动作 | 变化 |
|---|---|---|---|---|
| 9 | `GET /providers` | READ | — | **调整**：响应加字段 |
| 10 | `PATCH /providers/{providerCode}/status` | MANAGE | `NOTIFICATION_PROVIDER_STATUS_CHANGE` | **调整**：启用要求配置完整 |
| 13 | `POST /providers` | MANAGE | `NOTIFICATION_PROVIDER_CREATE` | 新增 |
| 14 | `PUT /providers/{providerCode}` | MANAGE | `NOTIFICATION_PROVIDER_UPDATE` | 新增 |
| 15 | `PUT /providers/{providerCode}/credential` | CREDENTIAL_MANAGE | `NOTIFICATION_PROVIDER_CREDENTIAL_REPLACE` | 新增 |
| 16 | `POST /providers/{providerCode}/test-send` | CREDENTIAL_MANAGE | `NOTIFICATION_PROVIDER_TEST_SEND` | 新增 |

### 2.2 请求 / 响应

**9. 账号列表** `GET /providers?page=1&size=20`

```json
{"items":[{"providerCode":"SES_PRIMARY","providerType":"AMAZON_SES","medium":"EMAIL","status":"ACTIVE",
  "region":"me-central-1","fromAddress":"no-reply@example.com","configurationSet":null,
  "credentialConfigured":false,"credentialUpdateTime":null,
  "missingConfiguration":["credential"],"adapterConfigured":true,"version":3,"updateTime":"...",
  "references":[{"templateCode":"CLIENT_EMAIL_VERIFICATION","role":"PRIMARY","routeStatus":"ACTIVE"}]}],
 "page":1,"size":20,"total":1}
```

- `missingConfiguration`：缺失字段名（`region`、`fromAddress`、`credential`），空数组即配置完整；类型未注册时为 `["providerType"]`。
- `adapterConfigured`：保留字段名，语义改为"本环境已注册该类型适配器"。
- 不返回 Token、密钥。

**13. 新建账号** `POST /providers`

```json
{"providerCode":"SES_BACKUP","providerType":"AMAZON_SES","region":"eu-west-1",
 "fromAddress":"no-reply@example.com","configurationSet":null,"reason":"新增备用区域"}
```
响应 `{"code":"SES_BACKUP","version":1}`；`medium` 由类型决定（`EMAIL`），状态 `DISABLED`。

**14. 编辑账号** `PUT /providers/{providerCode}`

```json
{"region":"eu-west-1","fromAddress":"noreply@example.com","configurationSet":"prod-events","expectedVersion":1,"reason":"更换发件地址"}
```
响应 `{"code":"SES_BACKUP","version":2}`。

**15. 录入 / 更换密钥** `PUT /providers/{providerCode}/credential`

```json
{"accessKeyId":"AKIAXXXXXXXXXXXXXXXX","secretAccessKey":"<secret>","expectedVersion":2,"reason":"轮换密钥"}
```
响应 `{"code":"SES_BACKUP","version":3}`。任何响应、错误、日志、审计都不含两个字段的值。

**16. 测试发送** `POST /providers/{providerCode}/test-send`

```json
{"email":"ops@example.com","reason":"验证新密钥"}
```
响应 `{"outcome":"ACCEPTED","resultCode":"SES_ACCEPTED"}`；`outcome` ∈ `ACCEPTED`、`REJECTED`、`UNKNOWN`，`resultCode` 为有界结果码（如 `SES_CREDENTIAL_REJECTED`、`SES_SENDER_NOT_VERIFIED`）。固定内容：标题「PAYTECH 发送账号测试」，正文含账号编码与发送时间（UTC），无链接。

### 2.3 BFF 与 Web

| 层 | 内容 |
|---|---|
| BFF | `NotificationManagementController` 加 4 个转发方法；`PlatformNotificationManagementClient` / Impl 与 `NotificationManagementModels` 加对应模型；请求路径与模型白名单（`ConfigurationManagementRequestConfiguration`）增量；`OperationsConsoleBffApplicationAutoTest` 路由全集增量 |
| Web | 见第 4 节；`notification.ts` 增加账号详情字段解码与 4 个请求 |

## 3. 数据库与注册数据

### 3.1 Schema DDL（待执行）

文件：[db/platform-support-service/ddl.sql](./db/platform-support-service/ddl.sql)（阶段 4 同步为 `docs/database/sql/changes/2026-10-15-notification-provider-account-fields.sql`，并更新 schema、PDManer、测试 `init/ddl.sql`）。

```sql
ALTER TABLE `t_notification_provider`
  ADD COLUMN `provider_type` VARCHAR(32) NOT NULL COMMENT '服务商类型，由发送适配器声明，如 AMAZON_SES；创建后不可改。' AFTER `medium`,
  ADD COLUMN `region` VARCHAR(32) NULL COMMENT '服务商区域，如 me-central-1；SES 必填。' AFTER `status`,
  ADD COLUMN `from_address` VARCHAR(254) NULL COMMENT '发件地址，须为该区域已验证身份；SES 必填。' AFTER `region`,
  ADD COLUMN `configuration_set` VARCHAR(64) NULL COMMENT 'SES Configuration Set，可空。' AFTER `from_address`,
  ADD COLUMN `credential_vault_token` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL COMMENT 'NOTIFICATION_PROVIDER_CREDENTIAL_V1 密钥对的 Vault Token；为空表示未录入；只在发送前以 NOTIFICATION_DELIVERY Reveal，禁止日志与接口返回。' AFTER `configuration_set`,
  ADD COLUMN `credential_update_time` DATETIME(6) NULL COMMENT '最近一次录入或更换密钥的 UTC 微秒时间。' AFTER `credential_vault_token`;
```

- 已有行的 `provider_type` 由 MySQL 填为空串，紧接执行 3.2 的 DML；无索引变化。
- 回退：`ALTER TABLE t_notification_provider DROP COLUMN provider_type, DROP COLUMN region, DROP COLUMN from_address, DROP COLUMN configuration_set, DROP COLUMN credential_vault_token, DROP COLUMN credential_update_time;`（会丢失已录入的 Token 引用，回退前需评估）。

### 3.2 业务 DML

| 场景 | 内容 |
|---|---|
| 新环境 | `platform-support-initial-data.sql` 认证邮件块中 `SES_PRIMARY` 的 INSERT 增加 `provider_type='AMAZON_SES'`、`region='me-central-1'`；注释改为"发件地址与密钥在运营后台录入" |
| 已执行环境（本机 e2e、mor-test） | `UPDATE t_notification_provider SET provider_type='AMAZON_SES', region='me-central-1', version=version+1, update_time=UTC_TIMESTAMP(6) WHERE provider_code='SES_PRIMARY' AND provider_type='';`（1 行），见 [db/platform-support-service/dml.sql](./db/platform-support-service/dml.sql) |

`from_address` 不预填：mor-test 当前部署配置中没有发件地址；由运营录入。

### 3.3 注册 DML（56 行，追加到 `platform-support-initial-data.sql`，独立块，待执行）

ID 块 `900000000000096xxx`（实现时改，见第 7 节第 1 条）。权限 `ACTIVE`，角色授权 `REVOKED`，按钮、路由 `DISABLED`，激活另行执行（本机、test 各一份激活 SQL）。

| 表 | 行数 | 内容 |
|---|---|---|
| `t_permission` | 1 | `NOTIFICATION_CREDENTIAL_MANAGE`（`NOTIFICATION`/`CREDENTIAL_MANAGE`/`HIGH`） |
| `t_role_permission` | 1 | 平台管理员 `900000000000000510`，`REVOKED` |
| `t_console_resource` | 2 | 按钮 `OPERATIONS_NOTIFICATION_PROVIDER_EDIT`「维护账号」（MANAGE，父 86303）、`OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL`「密钥与测试发送」（CREDENTIAL_MANAGE，父 86303） |
| `t_gateway_route` | 4 | 13—16；请求体上限 8192；超时同先例 |
| `t_gateway_route_header` | 24 | 每条写路由 6 条（authorization、origin、content-type、idempotency-key 两种用途，同先例） |
| `t_gateway_guard_rule` | 24 | 每条路由 6 条，数值同先例；测试发送另将每会话每分钟上限设为 5 |

### 3.4 Vault 策略（环境动作，单独授权）

| Caller | Action | Data Type | Purpose |
|---|---|---|---|
| `platform-support-service` | PROTECT | `NOTIFICATION_PROVIDER_CREDENTIAL_V1` | `NOTIFICATION_PROVIDER_SETUP` |

- 经运营后台 Vault 管理页（或其管理接口）在本机、mor-test 各添加一次；Vault 代码不改。
- 决定记录：`agent-docs/decisions/2026-10-15-platform-notification-credential-protect.md`（阶段 4 落文，格式同 [2026-10-13 渠道账号决定](../../../agent-docs/decisions/2026-10-13-platform-channel-account-reveal.md)），并在 `VAULT_SERVICE_ARCHITECTURE.md` 例外清单追加一行；标注 [SES 接入需求](../../20261014/platform-support-邮件AmazonSES接入/requirement.md)"Token 在部署配置"被本需求取代。

### 3.5 执行顺序

DDL → 业务 DML → 注册 DML → Vault 规则 → 部署新 Platform（旧 Platform 读不到新列也无影响，新 Platform 依赖新列）→ 激活注册 → 页面录入发件地址与密钥。

## 4. 页面设计

### 4.1 发送账号 `/notification-providers`（改造）

```
发送账号                                                 [+ 新建账号]  共 2 个  ⟳
┌───────────┬──────────┬──────┬──────────────┬──────────┬──────────────┬────────────┬───────────────────────────┐
│ 编码       │ 类型      │ 状态 │ 区域 / 发件地址 │ 密钥     │ 配置完整       │ 被引用      │ 操作                       │
├───────────┼──────────┼──────┼──────────────┼──────────┼──────────────┼────────────┼───────────────────────────┤
│SES_PRIMARY│Amazon SES│ 启用 │me-central-1   │ 未录入    │ 否：缺发件地址、密钥│ …（主）     │ 编辑 更换密钥 测试发送 停用   │
│           │          │      │ —             │          │              │            │                           │
└───────────┴──────────┴──────┴──────────────┴──────────┴──────────────┴────────────┴───────────────────────────┘
提示：密钥保存在 Vault，录入后无法查看，只能更换。
```

- 「新建账号」「编辑」按钮需 `OPERATIONS_NOTIFICATION_PROVIDER_EDIT`；「更换密钥」「测试发送」需 `OPERATIONS_NOTIFICATION_PROVIDER_CREDENTIAL`；「启用 / 停用」沿用现有按钮权限。
- 配置不完整时「启用」按钮禁用并提示缺失项；密钥列显示「已录入 · 更换于 2026/10/15 22:00」或「未录入」。
- 本环境未注册该类型适配器时，「配置完整」旁显示「本环境未启用该服务商」。

### 4.2 弹窗

| 弹窗 | 内容 |
|---|---|
| 新建账号 | 编码、类型（下拉，本期只有 Amazon SES）、区域、发件地址、Configuration Set（可选）、原因；说明"新建后为停用，录入密钥后才能启用" |
| 编辑账号 | 编码、类型只读；区域、发件地址、Configuration Set、原因；被启用路由引用时顶部警示"修改立即影响以下模板的发送"并列出模板 |
| 更换密钥 | Access Key ID、Secret Access Key（密码框，可切换显示）、原因；说明"保存后无法查看，只能更换；建议保存后先测试发送，再在 AWS 停用旧密钥" |
| 测试发送 | 收件邮箱、原因；发送后在弹窗内显示结果（成功 / 被拒绝 / 结果未知 + 结果码），不关闭弹窗 |

Pencil（`operations-console-web/docs/operations-console.pen`）：修改「通知管理 · 07 发送账号列表」，新增「07b 新建账号」「07c 编辑账号」「07d 更换密钥」「07e 测试发送结果」。

## 5. 研发与验证计划

| 顺序 | 内容 | 关联 AC | 验证 |
|---|---|---|---|
| 1 | Vault 决定与文档 | AC-03 | 文档评审 |
| 2 | Schema / PDManer / 测试 DDL / DML | AC-01、AC-09 | 模型与 Schema 一致性测试 |
| 3 | `EmailProvider` 合同、`EmailProviders`、SES 适配器与客户端缓存、三处调用点、删默认凭据链；测试适配器改类型 | AC-06、AC-07 | 既有通知测试全部通过；SES Mock HTTP 测试覆盖多账号、多区域、缺 Token 失败关闭 |
| 4 | 管理 Service 与 4 个 Processor、Controller、审计字段 | AC-01～05、AC-08、AC-10 | 管理接口测试：新建 / 编辑 / 冲突 / 启用校验 / 密钥 Protect 成功与失败 / 测试发送三种结果 / 403；日志与审计不含合成密钥 |
| 5 | 注册 DML、激活 SQL | AC-02 | 注册 DML 试跑 |
| 6 | BFF 转发与测试 | AC-02、AC-03 | BFF 自动化测试（含 JSON 字段） |
| 7 | Web 页面、弹窗与测试 | AC-12～14 | `npm test`、`npm run build` |
| 8 | 本机全链路（阶段 5） | 全部 | 需授权：本机 DDL/DML、本机 Vault 规则、合成密钥（SES 指向 Mock 或真实 SES 视你提供的账号） |

测试基础设施：`VaultMockController` 增加 `NOTIFICATION_PROVIDER_SETUP` 的 Protect；SES 仍指向 `SesMockController`，按账号区域记录调用以断言多账号。

## 6. 开放项

- mor-test 真实发信需要你提供 SES 账号（发件身份已验证）与 IAM 密钥，在页面录入；不在本需求自动完成。
- 阶段 6—7 是否执行 test 部署，阶段 5 后再定。

## 7. 实现偏差（阶段 4）

| # | 方案 | 实现 | 原因 |
|---|---|---|---|
| 1 | 注册 ID 块 `90xxx` | `96xxx`（路由 96001–96004、权限 96101、角色授权 96201、按钮 96301–96302、Header 96401–96424、限流 96501–96524） | `90xxx` 已被并行的 Client Webhook 需求占用（工作区未提交） |
| 2 | 新建时类型须为"本环境已注册的适配器类型" | 校验固定的可新建类型集合 `NotificationProviderTypes.CREATABLE`（`AMAZON_SES`）；列表另以 `adapterConfigured` 显示本环境是否注册了该类型 | 管理接口不依赖 `notification.enabled`；mor-test 目前未开启 SES，若按注册情况校验将无法新建账号 |
| 3 | — | 配置完整性在三处检查：启用账号、受理普通通知 / 认证邮件（`NotificationConfigurationServiceImpl`，400「邮件发送账号配置不完整」）、发送时渲染（`NotificationExecutionContext`，失败关闭为 `FAILED`，不读 Vault、不调用 SES） | 受理后账号被改为不完整时不应无限重试 |
| 4 | — | 测试发送不加资源锁；审计用 `ConfigurationChanged`，前后版本相同，字段 `TEST_RESULT`、`TEST_RECIPIENT_DIGEST` | 不修改账号；复用现有配置审计投影 |
| 5 | — | 环境需 `notification.ses.enabled=true` 才注册 SES 适配器；mor-test 当前为 `false`（`NOTIFICATION_SES_ENABLED`），部署时需改为 `true` | 适配器不再需要账号级配置，开关只表示环境是否允许 SES |
| 7 | — | 录入密钥、测试发送在事务外步骤之前用 `NotificationManagementSupport.precheck`（只读事务，不登记审计）鉴权；审计只绑定最后的写事务 | Platform 审计一次请求只能绑定一个事务，两个事务都登记会被丢弃（阶段 4 测试发现） |
| 8 | Vault 幂等键 `notification-provider-credential:{providerId}:{UUID}` | 改用点号分隔 | Vault 幂等键只允许 `[A-Za-z0-9._~-]`（阶段 4 测试发现） |
| 9 | — | 本环境未注册该服务商适配器时，「测试发送」按钮不可用并提示「本环境未启用该服务商」 | 否则只会得到 503（阶段 5 发现） |
| 6 | 页面列「密钥」「配置完整」分列 | 合并为「密钥 / 配置完整」一列 | 与 Pencil 画板 07 一致，表格宽度受限 |

**测试适配器：** `MockEmailProvider` / `FallbackEmailProvider` 改为声明测试类型 `TEST_EMAIL` / `TEST_EMAIL_FALLBACK`，Fixture 账号写该类型及合成配置；`NOTIFICATION_ADMIN_UNBOUND` 使用未注册类型 `UNREGISTERED_TEST`、字段为空，覆盖"适配器未注册""配置不完整"。生产代码不出现测试类型。

