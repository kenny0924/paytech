# Platform Support 普通邮件接入 Amazon SES 技术方案

> 状态：阶段 2+3 方案已确认（2026-10-14），阶段 4 已实现，实现偏差见第 8 节。需求与验收标准见 [requirement.md](./requirement.md)，确认记录见 [delivery.md](./delivery.md)。

**规模（本次方案）：** 只涉及 Platform Support 一个系统；新增 HTTP 接口 0 个，模块合同不变；新增表 0 张、字段 0 个，修改旧表结构 0 处，DDL/DML 0 行；新增 Maven 依赖 1 个（`software.amazon.awssdk:sesv2`），调整 Mapper 查询 1 条；无页面设计。

## 1. 现状差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理 |
|---|---|---|---|---|
| AC-01 真实 Provider | `EmailProvider` 只有测试实现（`MockEmailProvider`、`FallbackEmailProvider`）；`EmailProviders.require` 找不到就返回 503 | 按配置装配唯一的 SES 适配器 | 未实现 | 新增 `integration/provider/ses` |
| AC-02/03 受理即完成 | `SENT` 不是终态，Handler 会继续 `query()` 送达结果（`NotificationExecutionContext.isTerminal`） | 不支持查询的 Provider：`SENT` 即完成 | 合同要求必须能查询 | Provider 声明查询能力，Handler 据此判断是否完成 |
| AC-04 错误映射 | Mock 直接返回 Outcome | 把 SES 异常分类映射为 Outcome | 未实现 | 见 §3.2 |
| AC-05 等待后重发 | `UNKNOWN` 只能查询，不能重发、不能切备用（Delivery technique 第 6 节、第 8.2 节） | 不支持查询的 Provider：过 10 分钟窗口后重发 | 合同禁止 | 修订合同，见 §3.3 |
| AC-05 当前尝试 | `findOpen` 取 `status != 'FAILED'`，并要求恰好 1 条 | 允许留下旧的 `UNKNOWN` 尝试，以最新一条为准 | 重发后会出现 2 条非失败记录 | 改为取最新一条 |
| AC-08 开关 | `compensation.enabled: ${files.onboarding.enabled:false}` | 通知能独立开启补偿 | 被文件功能绑定 | 见 §3.4 |
| AC-09 测试 | 只在 `EmailProvider` 接口层 Mock；Delivery 分支覆盖 66.91% | 经真实 SDK 走测试 HTTP 端点；分支覆盖 ≥ 90% | 没有协议层测试 | 见 §5 |

## 2. 影响范围

| 系统/模块 | 影响点 | 改代码/模型/配置 | 依赖与兼容 |
|---|---|---|---|
| `platform-support-delivery` | `EmailProvider` 增加默认方法；新增 SES 适配器、配置类；Handler/Service 增加窗口重发；`NotificationProperties` 增加窗口配置；Attempt Mapper 改查询 | 改代码；模型和 SQL 不变 | 两个测试 Provider 继承默认的“支持查询”，行为不变 |
| `platform-support-web` | `application.yml` 的补偿开关和 SES 配置项；启动校验 | 改配置 | 不设置新环境变量时，文件功能的现有行为保持不变 |
| `platform-support-test` | SES Mock 端点与 Mocker、新测试类、manual 用例、`application-mock.yml` 的 SES 配置、TestMapper 时间平移 | 改测试 | 不影响现有用例 |
| Foundation / 其他系统 | 无 | 不改 | `awssdk` BOM 已在 `paytech-dependencies` 中管理 |
| 文档 | Delivery technique 第 6/8 节修订、`TECHNOLOGY_STACK.md`、Platform README、测试 README、`CURRENT_IMPLEMENTATION.md` | 改文档 | — |

## 3. 方案

### 3.1 Provider 能力声明

`EmailProvider` 增加 `default boolean supportsQuery() { return true; }`。SES 返回 `false`，其 `query()` 不会被调用；万一被调用，抛 `ErrorException`。两个测试 Provider 不改，继续走原来的查询链路（AC-06）。

### 3.2 SES 适配器

包：`com.paytech.platform.support.delivery.integration.provider.ses`。AWS SDK 只出现在这个包里。

| 类 | 职责 |
|---|---|
| `SesEmailProperties` | `notification.ses.*`，在构造器里校验，`toString` 不输出密钥 |
| `SesEmailConfiguration` | `notification.enabled=true` 且 `notification.ses.enabled=true` 时才装配；创建 `SesV2Client` 和适配器 |
| `SesEmailProvider` | 实现 `EmailProvider`：构造请求、映射结果 |

**配置项**（本期只支持单个 SES 账号；以后要做两区域主备，再改成按 `provider_code` 的 Map）：

| 配置 | 必填 | 默认 / 约束 |
|---|---|---|
| `notification.ses.enabled` | — | `false` |
| `notification.ses.provider-code` | 是 | 与 `t_notification_provider.provider_code` 一致，`[A-Z][A-Z0-9_]{0,63}` |
| `notification.ses.region` | 是 | 例如 `me-central-1` |
| `notification.ses.from-address` | 是 | 规范化邮箱，必须是 SES 已验证的身份；拒绝 CR/LF |
| `notification.ses.configuration-set` | 否 | 为后续接事件回调预留 |
| `notification.ses.endpoint` | 否 | 只允许 HTTPS 根地址；`http` 只允许 localhost/127.0.0.1/[::1]（测试用） |
| `notification.ses.credential-token` | 否 | （2026-10-15 变更，取代 access-key/secret-key）IAM 用户密钥对的 Vault Token，43 字符；为空时走默认凭据链 |
| `notification.ses.connect-timeout` | 否 | 2s，范围 1–10s |
| `notification.ses.read-timeout` | 否 | 5s，范围 1–15s；要小于补偿单次执行时限 20s |

**Client：** 照搬 `S3StorageConfiguration` 的做法，用 `UrlConnectionHttpClient` 并设置有界超时；**关闭 SDK 内置重试**（`RetryStrategy` 设为不重试），以满足“实现不得暗中重试”；另设 `apiCallTimeout`，等于连接超时加读超时。

**请求：** `requestNo = "ses-" + attemptId`。`SendEmail` 只放一个 `ToAddresses`；`Content.Simple` 的主题和纯文本正文使用 UTF-8；配置了 Configuration Set 就带上；`EmailTags` 带 `notification_request_no=<requestNo>`，供后续事件回调关联。

**结果映射：**

| SES 返回 | Outcome | code |
|---|---|---|
| 200，带 MessageId | `ACCEPTED` | `SES_ACCEPTED` |
| `TooManyRequestsException` | `RETRYABLE_REJECTED` | `SES_THROTTLED` |
| `LimitExceededException`（配额） | `RETRYABLE_REJECTED` | `SES_QUOTA_EXCEEDED` |
| `SendingPausedException` / `AccountSuspendedException` | `RETRYABLE_REJECTED` | `SES_ACCOUNT_PAUSED` / `SES_ACCOUNT_SUSPENDED` |
| `MailFromDomainNotVerifiedException` / `NotFoundException` | `RETRYABLE_REJECTED` | `SES_SENDER_NOT_VERIFIED` / `SES_CONFIGURATION_NOT_FOUND` |
| `MessageRejected` | `REJECTED` | `SES_MESSAGE_REJECTED` |
| `BadRequestException` 及其他 4xx | `REJECTED` | `SES_BAD_REQUEST` / `SES_CLIENT_ERROR` |
| 5xx | `UNKNOWN` | `SES_SERVER_ERROR` |
| `SdkClientException`（超时、断连）、200 但没有 MessageId | `UNKNOWN` | `SES_RESULT_UNKNOWN` |

上表中，账号或发件配置类错误归为“可重试”。原因是 4xx 说明 SES 没有受理这封邮件，换备用账号可能成功；没有备用账号时，最多也只额外尝试 2 次。SES 返回的异常原文和 message 一律丢弃，只保存上表的 code（AC-07）。

### 3.3 执行语义修订（只针对不支持查询的 Provider）

修订 Delivery technique 第 6 节和第 8.2 节，加入以下规则；支持查询的 Provider 保持原规则。

1. **受理即完成：** Delivery 为 `SENT` 且所属 Provider 不支持查询时，Handler 直接返回成功，不调用 SES（AC-03）。
2. **未知后窗口重发：** Delivery 为 `PROCESSING` 或 `UNKNOWN`，并且当前尝试所属的 Provider 不支持查询时：
   - `now < 当前尝试.started_time + notification.unknown-resend-delay`（默认 10 分钟）：不发起外部调用，返回“待处理”，等补偿任务退避后再来。
   - 窗口已过，且 `attempt_count < maximum-send-attempts`：在一个短事务里把尝试从 `PROCESSING` 改为 `UNKNOWN`（已经是 `UNKNOWN` 的不变，`resolution_*` 不写入，如实表示“结果未确认”），同时把 Delivery 通过 CAS 改为 `RETRY_WAIT`，`next_retry_time = now`。随后在同一次执行中重新读取并走正常的发送流程，新建尝试，`attempt_count + 1`，并按现有路由规则选主或备用账号。
   - 窗口已过，但次数已满：Delivery 保持 `UNKNOWN`，任务以 `terminalFailure("NOTIFICATION_UNKNOWN_EXHAUSTED")` 结束，不再自动处理，由公共补偿失败指标告警。
3. **当前尝试：** `NotificationAttemptMapper.findOpen` 改为 `findCurrent`，按 `attempt_id DESC` 取该投递最新的一条（Snowflake ID 单调递增，已有 `(delivery_id, status)` 索引可用）；最新一条是 `FAILED` 时抛 `ErrorException`，因为这和 Delivery 状态矛盾。旧的 `UNKNOWN` 尝试作为历史保留。
4. **配置：** `NotificationProperties` 增加 `unknownResendDelay`（默认 `10m`，至少 `1m`）。

这样做的效果是至少发一次：窗口内不会重复发送；重复只可能出现在“SES 实际已受理，但我们没收到响应”这种情况。

### 3.4 启用开关

- `application.yml` 改为 `compensation.enabled: ${PLATFORM_SUPPORT_COMPENSATION_ENABLED:${files.onboarding.enabled:false}}`。不设置这个环境变量时，行为与现在相同，mor-test 等现有部署不受影响。
- 在 Delivery 模块加启动校验：`notification.enabled=true` 而 `compensation.enabled` 不为 `true` 时启动失败，提示“通知启用需显式开启 compensation.enabled”（AC-08）。
- 两个开关都关闭时不创建 AWS Client（由装配条件保证）。
- 生产环境要让窗口重发能自动执行，还需要配置公共 XXL-Job 扫描调度。这属于部署前提，本期不配。

### 3.5 日志与安全

- 适配器不记录收件人、主题、正文和 SES 原文；把异常映射成 Outcome 时不保留 cause 链，以免下游打印。
- 凭据只来自 Vault（按配置的 Token 每次发送前 Reveal）或默认凭据链，不进数据库、版本库或日志。测试使用 Vault Mock 返回的合成密钥对，SES Mock 端点不校验签名。

## 4. 接口与数据

- 接口：HTTP 0 个；`SubmitNotificationProcessor` 的请求和响应不变；只在模块内部的 `EmailProvider` 增加一个默认方法。
- 数据库：0。没有 DDL/DML，也不改 PDManer；Attempt 和 Delivery 的状态取值都在现有范围内。不创建 `db/` 目录。

## 5. 测试与验证计划

**入口：** 补偿事件 Handler `NotificationDeliveryHandler.handle`，按现有方式用 `scanDue()` 触发；配置装配用 `ApplicationContextRunner` 验证。

**测试设施**（照搬 `ChannelMockController` 的做法）：
- `SesMockController`：挂在 Mock HTTP 服务的 `/ses/**`，记录收到的请求，并按配置返回 200 JSON 或 `MockHttpFault.raw(status, {"__type": ...})`，也能模拟延迟和断连。
- `SesMocker`：显式配置每次发送的结果，提供调用记录、意外调用断言和 `Clear` 清理。
- `application-mock.yml`：`notification.ses.endpoint=${test.mock-http.base-url}/ses`，配合假凭据和 `read-timeout=1s`。
- `NotificationTestMapper` 增加把尝试的 `started_time` 往前平移的方法，用来快进窗口。

**`SesNotificationDeliveryTest` 场景：**

| 场景 | AC |
|---|---|
| 正常受理：校验请求字段（单收件人、主题、正文、发件人、标签）；Attempt 为 `ACCEPTED`，Delivery 为 `SENT`，保存了 MessageId | AC-02 |
| 已 `SENT` 的投递重新执行：没有 SES 调用，任务成功 | AC-03 |
| 节流 → `RETRY_WAIT`；等待后切换到备用账号成功 | AC-04 |
| `MessageRejected` → `FAILED`，不重试 | AC-04 |
| 可重试错误达到 3 次上限 → `FAILED` | AC-04 |
| 读超时 → `UNKNOWN`；窗口内重新执行不调用 SES | AC-05 |
| 平移时间过窗口 → 新建尝试并成功；`attempt_count=2`，旧尝试保持 `UNKNOWN` | AC-05 |
| 5xx 和断连 → `UNKNOWN`；3 次都不确定 → 任务以 `NOTIFICATION_UNKNOWN_EXHAUSTED` 结束 | AC-05 |
| 进程在 `PROCESSING` 中崩溃，过窗口后恢复重发 | AC-05 |
| 现有 Mock Provider 的 `UNKNOWN` 仍然只查询（现有用例回归） | AC-06 |
| 日志和数据库里没有邮箱、正文或 SES 原文（检查 Attempt 的 code 字段和日志捕获） | AC-07 |
| 配置：缺字段或非法 endpoint 时启动失败；关闭时没有 AWS Bean；通知开启而补偿关闭时启动失败 | AC-01/AC-08 |

**分支覆盖：** 实现后对照 JaCoCo 报告，为 Delivery 模块里未覆盖的分支（输入规范化、渲染、结果校验等）补入口级场景，目标 ≥ 90%；不排除生产类，也不降低门槛（AC-09）。

**manual：** `manual/notification/SesRealSendTest`，读取 `SES_MANUAL_REGION`、`SES_MANUAL_FROM`、`SES_MANUAL_TO` 和默认凭据链，真实发送一封邮件并断言 `SENT`；环境变量缺失时直接失败并给出设置说明。默认 Maven 不运行。

**命令：** JDK 25 下全服务 `./mvnw test`，以及 `-DskipTests verify` 生成覆盖率报告；文档改动后运行 `npm --prefix requirements-site test`。

## 6. 实施顺序

1. 依赖与 `EmailProvider.supportsQuery`；SES Properties、Configuration、Provider 及其映射。
2. `findCurrent`、窗口重发、`SENT` 完成、启动校验、`unknownResendDelay`。
3. 测试设施和 `SesNotificationDeliveryTest`，现有通知用例回归。
4. 补分支覆盖；manual 用例。
5. 同步文档（Delivery technique 修订、TECHNOLOGY_STACK、README、台账）。

## 7. 风险与前提

- **依赖下载：** 本机 Maven 仓库还没有 `sesv2`，第一次构建需要从 Maven Central 下载，版本由 BOM 固定。
- **错误类型解析：** SES 的错误码从 `x-amzn-ErrorType` Header 或响应体 `__type` 字段解析，Mock 的返回格式以测试实际通过为准。
- **重复邮件：** 只发生在“已受理但响应丢失”的情况，并且最多重复到 3 次上限；这是已确认可以接受的。
- **上线前提（用户侧）：** AWS 账号；`me-central-1` 区域的发件域名已验证（SPF/DKIM/DMARC）；解除 SES 沙箱；IAM 用户仅具有 `ses:SendEmail` 权限（限定发件身份与 `ses:FromAddress`），密钥对 Protect 到 Vault 并配置 Token；Vault 新增 `NOTIFICATION_PROVIDER_CREDENTIAL_V1` 及两条规则；配置 XXL-Job 扫描；Vault 已授权 `PLATFORM_NOTIFICATION_EMAIL_V1`。

## 8. 阶段 4 实现记录与偏差

- **启动校验（§3.4）未新增类：** 补偿关闭时 Foundation 不创建 `CompensationEventRegistrar`，通知 Service 依赖它，`notification.enabled=true` 已会在启动时失败。按必要性门禁不再加同义 Guard；该失败路径未单独写测试，只在 `application.yml` 注释中说明。
- **manual Profile：** Platform 测试 POM 固定 `auto` include，父 POM 的 `manual-test` 选不到 manual 用例，因此在测试 POM 增加同名 Profile 覆盖 Surefire 选择。
- **错误类型：** SES v2 错误码以 `x-amzn-ErrorType` 与 `__type` 返回；内容被拒的错误码为 `MessageRejected`（SDK 类为 `MessageRejectedException`），已用 Mock 端点验证十类映射。
- **测试辅助：** `scanDue` 上移到 `NotificationTestBase` 供两个 Handler 测试类共用；`MockEmailProvider` 增加 `expectSendResult`，用于不合法证据场景。
- **覆盖率补齐：** 为达到 Delivery 分支 90%，在既有入口补充输入规范化边界、受理前配置拒绝、无适配器失败关闭、非法任务身份、不合法供应商证据、`UNDELIVERABLE`、重试前配置漂移、可查询账号次数用尽仍查询，以及配置校验；未排除生产类或降低门槛。剩余未覆盖分支主要是数据损坏防御（如插入行数 ≠ 1、记录缺失）。

### 8.1 2026-10-15 密钥管理变更（[需求 6.1](./requirement.md)）

- `EmailProvider.prepare()` 返回只用于本次发送的 `EmailSender`；Handler 在渲染后、地址 Reveal 与登记尝试之前调用。SES 实现在此以 `NOTIFICATION_DELIVERY` Reveal 密钥对（`SesCredentialVault`），经 `SendEmailRequest.overrideConfiguration` 只给该次请求签名；不缓存。Vault 不可用或值不合法时抛出，任务保留重试，不计发送次数。
- 配置 Token 时客户端级凭据直接拒绝，杜绝回退默认凭据链；配置 Token 而缺少 `VaultClient` 时拒绝启动。Token 格式对齐 Vault（43 字符无填充 Base64URL）。
- 凭据类 SES 错误码（`UnrecognizedClientException`、`InvalidClientTokenId`、`SignatureDoesNotMatch`、`InvalidSignatureException`、`IncompleteSignature`、`MissingAuthenticationToken`、`ExpiredTokenException`、`AccessDeniedException`）映射为可重试的 `SES_CREDENTIAL_REJECTED`。
- **Vault 策略变更清单（不在 Platform 范围，待执行）：** 数据类型 `NOTIFICATION_PROVIDER_CREDENTIAL_V1`（密钥类、REFERENCE、允许人工加密、不允许人工解密）；规则 `platform-support-service / REVEAL / NOTIFICATION_PROVIDER_CREDENTIAL_V1 / NOTIFICATION_DELIVERY`；人工 Protect 用途 `NOTIFICATION_PROVIDER_SETUP`。在 Operations 手动加解密上线前，首次 Protect 由研发调用 Vault 内部接口完成。
- **换密钥流程：** AWS 新建第二把密钥 → Protect 得新 Token → 更新 `NOTIFICATION_SES_CREDENTIAL_TOKEN` 并重启 Platform → 确认发信成功 → AWS 停用并删除旧密钥。
