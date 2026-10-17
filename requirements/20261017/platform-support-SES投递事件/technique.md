# Platform 邮件投递事件（Amazon SES）：方案、接口与数据设计（阶段 3）

> 状态：阶段 3 已确认（2026-10-17）；阶段 4 实现偏差见第 7 节。需求见 [requirement.md](./requirement.md)，现状分析见 [analysis.md](./analysis.md)（R-01～R-04 已确认）。本文是方案，不表示已实现或已执行。

**规模（本次方案）：** 涉及 3 个目录（Platform Support、Operations BFF、Operations Web）；新增 0 个接口，调整 4 个接口（账号列表、新建、编辑加字段；发送记录详情加字段）；新增 0 张表，旧表 `t_notification_provider` 新增 4 个字段、`t_notification_attempt` 新增 5 个字段，`resolution_source` 只改列注释；索引 0；业务 DML 0 行；注册 DML 0 行；页面：发送账号页与发送记录详情各 1 处增量（不新增页面）。新增 1 个外部依赖 `software.amazon.awssdk:sqs`。

## 1. 业务与架构

### 1.1 端到端链路

```text
Platform ──SendEmail(标签 notification_request_no=ses-<attempt_id>)──▶ SES
SES ──Configuration Set 事件目标──▶ SNS 主题 ──订阅(Raw)──▶ SQS 队列（死信队列兜底）
Platform 定时任务 ──ReceiveMessage──▶ SQS
   └─ 逐条：解析 → 短事务回写尝试 / 投递 → 提交后 DeleteMessage
```

- 事件来源只认 SES 事件 JSON；Raw 投递与 SNS 信封（`Type=Notification` 时取 `Message`）都接受。不校验 SNS 签名：队列访问已由 IAM 控制，且只读取白名单字段。
- 不新增公网入口、Gateway 路由或 Vault 规则；访问队列复用账号已录入的 IAM 密钥（`NOTIFICATION_DELIVERY` Reveal，Platform 已有权限）。

### 1.2 定时拉取（R-01）

| 项 | 设计 |
|---|---|
| 驱动 | `delivery` 模块 Job 类 `NotificationEventPollJob`，Spring `@Scheduled(fixedDelay)`；`notification.ses.events.enabled=true` 时才注册（默认关闭） |
| 范围 | 所有 `provider_type=AMAZON_SES`、`event_queue_url` 非空、已录入密钥的账号，**不论启停**（停用前发出的邮件仍会回事件） |
| 每轮每账号 | 事务外 Reveal 密钥 1 次 → 最多 `max-batches`（默认 5）次 `ReceiveMessage`（每次最多 10 条、长轮询 `wait-time` 默认 10 秒、可见性超时 60 秒）；某批不足 10 条即停止 |
| 多实例 | 各实例独立拉取；SQS 可见性超时保证同一条消息同一时间只在一个实例上；重复到达由条件更新兜底 |
| 失败 | 拉取失败（权限、队列不存在、网络、Vault）只记该账号运行状态与指标，下一轮继续；不影响发信 |
| 配置 | `notification.ses.events.enabled`（默认 false）、`poll-interval`（默认 PT60S，≥PT10S）、`wait-time`（默认 PT10S，0～20 秒）、`max-batches`（默认 5，1～20）、`visibility-timeout`（默认 PT60S）、`endpoint`（测试终端覆盖，可空）、`connect-timeout`、`read-timeout`（须大于 `wait-time`，启动校验） |

Vault 负载：每账号每轮 1 次 Reveal（默认每分钟 1 次）；不缓存密钥原文，与发信规则一致。

### 1.3 单条消息处理

| 步骤 | 规则 |
|---|---|
| 1 解析 | 读取 `eventType`、`mail.messageId`、`mail.tags.notification_request_no[0]` 及该类型的时间与分类字段；其余字段（收件地址、邮件头、诊断原文）不读取、不保存。解析失败 → **不删除**（计 `INVALID`），由死信队列兜底 |
| 2 过滤 | 类型不在 `Delivery`、`Bounce`、`Complaint`、`Reject` → 删除（计 `IGNORED`） |
| 3 匹配 | 标签形如 `ses-<数字>` 且该 `attempt_id` 存在、`provider_message_id` 为空或等于事件 `messageId` → 匹配；否则删除（计 `UNMATCHED`，认证邮件、测试发送走这里） |
| 4 回写 | 一个短事务（RR，同现有通知事务模板）：按 1.4 规则条件更新尝试与投递 |
| 5 删除 | 事务提交后 `DeleteMessage`；删除失败只计数（消息会再次到达，回写幂等） |

- 尝试仍是 `PROCESSING`（发送调用尚未回写）→ 本轮**不删除**（计 `DEFERRED`），60 秒后重新可见。
- 版本冲突 / 数据库异常 → 不删除，下次再处理。
- 每批处理完，若收到过任何消息，更新账号 `event_receive_time`。

### 1.4 状态规则（R-03）

**尝试上的事件结果**（`event_outcome`，优先级只升不降）：

| 事件 | 写入 | 优先级 |
|---|---|---|
| `Bounce`（Transient、Undetermined） | `SOFT_BOUNCE` | 1 |
| `Delivery` | `DELIVERED` | 2 |
| `Bounce`（Permanent） | `HARD_BOUNCE` | 3 |
| `Reject` | `REJECTED` | 3 |
| `Complaint` | 不改 `event_outcome`，写 `complaint_time`、`complaint_code`（首个为准） | 独立 |

- 低于或等于当前优先级的事件不覆盖（重复、乱序到达结果确定）；同优先级保留先到的。
- 硬退信晚于送达到达（异步退信）会覆盖 `DELIVERED`，与 [Platform 数据库约定](../../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md)"晚到 HARD_BOUNCE 优先"一致。
- 尝试状态为 `UNKNOWN` 时，任何匹配事件都证明 SES 已受理：改为 `ACCEPTED`，`provider_message_id` 补为事件 `messageId`，`resolution_source=PROVIDER_EVENT`、`resolved_time=now`。

**投递状态**：

| 事件结果 | 该尝试是当前尝试 | 该尝试是旧尝试（已重发） |
|---|---|---|
| `DELIVERED` | `SENT`、`UNKNOWN`、`RETRY_WAIT` → `DELIVERED` | 同左（收件人确实收到了，停止待重发） |
| `HARD_BOUNCE`、`REJECTED` | `SENT`、`UNKNOWN`、`RETRY_WAIT`、`DELIVERED` → `FAILED` | 不变 |
| `SOFT_BOUNCE`、投诉 | 不变 | 不变 |

- 投递 `PROCESSING`（新尝试正在发送）、`PENDING`、`FAILED` 时不改投递状态，只记尝试。
- 投递从 `RETRY_WAIT` 变 `DELIVERED` 后，待执行的补偿任务加载到终态即结束，不会再发（`NotificationServiceImpl.load` 现有行为）。
- 投递变更使用现有 `deliveries.transition` 的版本条件更新；冲突即本条消息不删除，下次重试。

### 1.5 账号运行状态（R-04）

`event_poll_time`、`event_poll_error`、`event_receive_time` 由拉取任务直接更新：**不推进 `version`、不改 `update_time`、不记审计**。

| 结果 | 写入 |
|---|---|
| 一轮拉取全部成功 | `event_poll_time=now`、`event_poll_error=NULL` |
| 失败 | `event_poll_error=<错误码>`；`event_poll_time` 保留上次成功时间 |
| 收到消息 | `event_receive_time=now` |

错误码（有界）：`SQS_ACCESS_DENIED`、`SQS_QUEUE_NOT_FOUND`、`SQS_THROTTLED`、`SQS_UNAVAILABLE`（网络 / 超时 / 5xx）、`SQS_CLIENT_ERROR`、`CREDENTIAL_UNAVAILABLE`（Vault 不可用或未录入）。

### 1.6 指标

| 指标 | 标签 |
|---|---|
| `notification.provider.events` | `provider`、`event`（DELIVERY/BOUNCE/COMPLAINT/REJECT/OTHER）、`result`（APPLIED/DUPLICATE/UNMATCHED/IGNORED/INVALID/DEFERRED） |
| `notification.provider.event.polls` | `provider`、`result`（SUCCESS 或 1.5 的错误码） |

标签不含收件地址、`messageId`、尝试 ID。

### 1.7 校验

| 字段 | 规则 | 失败 |
|---|---|---|
| `eventQueueUrl` | 可空；`^https://sqs\.[a-z]{2}(-[a-z]+)+-[0-9]\.amazonaws\.com/[0-9]{12}/[A-Za-z0-9_-]{1,80}$`，≤512；队列区域取自 URL，可与账号区域不同 | 422 |

## 2. 接口

公共约定同[通知发送账号维护 technique.md 第 2 节](../../20261015/operations-console-通知发送账号维护/technique.md)。**不新增接口**，只在原接口上加字段；旧调用方不传新字段时行为不变。

| # | Method / Path | 变化 |
|---|---|---|
| 9 | `GET /notification-admin/v1/providers` | 响应每项加 `eventQueueUrl`、`eventPollTime`、`eventPollError`、`eventReceiveTime`（均可空） |
| 13 | `POST /notification-admin/v1/providers` | 请求加可选 `eventQueueUrl` |
| 14 | `PUT /notification-admin/v1/providers/{providerCode}` | 请求加可选 `eventQueueUrl`；传空或缺省 = 清空（与 `configurationSet` 一致）；审计记"事件队列已变更"，不记 URL 原文以外的值 |
| 6 | `GET /notification-admin/v1/requests/{requestId}` | `deliveries[].attempts[]` 加 `eventOutcome`、`eventOutcomeCode`、`eventOutcomeTime`、`complaintCode`、`complaintTime`；`resolutionSource` 可能为 `PROVIDER_EVENT` |

**账号列表项（新增字段）**

```json
{"providerCode":"SES_PRIMARY", "...":"...",
 "eventQueueUrl":"https://sqs.ap-southeast-1.amazonaws.com/698446905765/mor-test-ses-events",
 "eventPollTime":"2026-10-17T08:00:12.000000Z","eventPollError":null,
 "eventReceiveTime":"2026-10-17T07:58:40.000000Z"}
```

**编辑账号请求**

```json
{"region":"ap-southeast-1","fromAddress":"noreply@payorigo.com","configurationSet":null,
 "eventQueueUrl":"https://sqs.ap-southeast-1.amazonaws.com/698446905765/mor-test-ses-events",
 "expectedVersion":5,"reason":"接入投递事件"}
```

**发送尝试（新增字段）**

```json
{"attemptId":"…","providerCode":"SES_PRIMARY","status":"ACCEPTED","responseCode":"SES_ACCEPTED",
 "resolutionSource":"SEND_RESPONSE","resolutionCode":null,"startedTime":"…","finishedTime":"…","resolvedTime":"…",
 "eventOutcome":"HARD_BOUNCE","eventOutcomeCode":"Permanent/General","eventOutcomeTime":"…",
 "complaintCode":null,"complaintTime":null}
```

- `eventOutcome`：`DELIVERED`、`SOFT_BOUNCE`、`HARD_BOUNCE`、`REJECTED`，可空。
- `eventOutcomeCode`：退信为 `<bounceType>/<bounceSubType>`（如 `Permanent/OnAccountSuppressionList`），拒发为 `Reject/<reason>`，送达为空；只保留 `[A-Za-z0-9/_-]`，≤64。
- `complaintCode`：`complaintFeedbackType`（如 `abuse`），没有时取 `complaintSubType`，再没有为 `unknown`。

BFF：`NotificationManagementModels` 账号、尝试模型与编辑请求增加同名字段，原样透传；无聚合变化。

## 3. 数据库

文件：[db/platform-support-service/ddl.sql](./db/platform-support-service/ddl.sql)（待执行，设计批准不改变任何环境）。

| 表 | 变化 |
|---|---|
| `t_notification_provider` | 新增 `event_queue_url` VARCHAR(512) NULL、`event_poll_time` DATETIME(6) NULL、`event_poll_error` VARCHAR(64) NULL、`event_receive_time` DATETIME(6) NULL |
| `t_notification_attempt` | 新增 `event_outcome` VARCHAR(32) NULL、`event_outcome_code` VARCHAR(64) NULL、`event_outcome_time` DATETIME(6) NULL、`complaint_code` VARCHAR(64) NULL、`complaint_time` DATETIME(6) NULL；`resolution_source` 列注释加 `PROVIDER_EVENT`（类型不变） |

- 只加可空列，按主键查尝试，无新索引；旧 Platform 不读新列，可先执行 DDL 再换镜像。
- 业务 DML 0 行（`SES_PRIMARY` 的队列 URL 在页面填写）；注册 DML 0 行。
- PDManer 模型、`platform-support-schema.sql`、DataObject、Mapper 同步修改。
- 回退：新列保留不影响旧版本；不提供删列脚本（删列会丢失已收到的事件结果）。

## 4. 页面

不新增页面；两处增量，沿用现有组件与样式（`design.pen` 仍有其他未保存改动，本次不改 Pencil，用下列结构说明并在阶段 5 截图验收）。

**发送账号**

```text
编辑账号弹窗（新增一项，放在 Configuration Set 之后）
  事件队列 URL（可选）  [ https://sqs.ap-southeast-1.amazonaws.com/…/… ]
  说明：SES 送达、退信、投诉事件经 SNS 发到该 SQS 队列，平台用本账号密钥定时拉取；留空则不接收事件。

列表新增列「投递事件」（在「密钥 / 配置完整」之后）
  未配置                      → 灰色「未配置」
  已配置、无错误               → 「接收正常」+ 最近拉取 10:21:05 + 最近事件 10:20:40（无事件时「暂无事件」）
  已配置、有错误               → 红色「拉取失败：SQS_ACCESS_DENIED」+ 最近成功拉取时间
  已配置、从未拉取过           → 灰色「等待首次拉取」（本环境未开启拉取开关时也显示此状态）
```

列表响应不增加"环境是否开启拉取"的字段；已配置但 `eventPollTime`、`eventPollError` 都为空即显示「等待首次拉取」，弹窗说明里注明"需本环境开启事件拉取"。

**发送记录详情（尝试行）**

```text
尝试 2  [已受理]  SES_PRIMARY · 开始 10:20:01 · 受理 SES_ACCEPTED
        投递结果：[已送达] 10:20:40                         ← eventOutcome=DELIVERED
        投递结果：[硬退信] Permanent/General 10:20:41        ← 红色
        投递结果：[软退信] Transient/MailboxFull 10:20:41    ← 橙色
        投递结果：[被拒发] Reject/Bad content                ← 红色
        [被投诉] abuse 10:31:02                              ← 红色，独立一行
```

需求 AC-13 中"列表状态筛选可选'已送达'"：页面已有「投递状态」筛选且含「已送达」，无需改动（阶段 3 曾误判为没有筛选，见第 7 节）。

## 5. 研发与验证计划

| 顺序 | 系统 | 内容 | 对应验收 |
|---|---|---|---|
| 1 | 依赖 | 联网下载 `software.amazon.awssdk:sqs`（R-02，版本由 BOM 锁定）；排除 apache / netty 客户端，沿用 `url-connection-client` | — |
| 2 | Platform 数据 | DDL、PDManer、schema、DataObject、Mapper（尝试事件回写、账号运行状态更新） | AC-03、AC-08 |
| 3 | Platform 集成 | `integration/provider/ses/SesEventQueue`（SqsClient 按区域缓存、Receive / Delete、错误分类）、`SesEventParser`（白名单解析，含 SNS 信封） | AC-01、AC-06、AC-09 |
| 4 | Platform 服务 | `NotificationEventService.apply(event)`（1.4 规则，短事务）；`NotificationEventPollJob`（1.2）；账号新增 / 编辑接收 `eventQueueUrl`；转换器输出新字段 | AC-02～AC-08 |
| 5 | BFF / Web | 模型字段；账号弹窗与列表列；尝试行事件结果 | AC-12、AC-13 |
| 6 | 本机 SQL | 对本机 `mor_platform`（测试库）、`mor_platform_e2e` 执行 ddl.sql——**需你在阶段 4 授权** | — |

**测试**（Platform `auto`，从真实入口触发）：
- 新增 `SqsMockController` + `SqsMocker`（与 `SesMockController` 同一方式：测试 HTTP 端点按 `X-Amz-Target` 返回配置的 `ReceiveMessage` 响应、记录 `DeleteMessage` 调用，未配置的调用即失败）。
- `NotificationEventPollTest`：经 Job 入口触发一轮拉取，覆盖送达、硬 / 软退信、拒发、投诉、乱序（投诉先到、硬退信晚于送达、重复送达）、旧尝试送达、`UNKNOWN` 尝试被确认、`PROCESSING` 延后、匹配不到（认证邮件标签）、非白名单类型、无法解析（不删除）、SNS 信封、拉取失败五类错误码与账号运行状态、删除失败。
- `NotificationProviderManagementTest`：新建 / 编辑 `eventQueueUrl` 的校验、清空、审计；列表字段。
- BFF：模型透传测试；Web：解码与渲染测试。
- 既有通知测试全部通过；Delivery 模块覆盖率不低于现状（指令 95.96%、分支 89.74%）。

本机验收（阶段 5）：无真实 SQS，本机 Platform 指向测试终端无法常驻；改为**经运营入口验证页面字段**（填写 / 清空队列 URL、尝试行展示用本机库插入的合成事件结果），真实链路在 mor-test 验收（AC-11）。

## 6. 兼容与风险

| 项 | 处理 |
|---|---|
| 旧 BFF / Web 读新 Platform | **不兼容**：BFF 严格解析，遇新字段失败；Platform 与 BFF 同批发布（见第 7 节 #2） |
| 环境未开开关 | 不注册 Job，不拉取；字段可正常维护 |
| 同一队列被多个账号配置 | 各账号都拉同一队列，竞争消费，结果一致；运行状态各记各的 |
| 队列积压（Platform 长时间停机） | SQS 保留期内恢复后继续处理；超出保留期的事件丢失，投递停留在 `SENT` |
| 送达事件比发送调用回写还快 | 尝试为 `PROCESSING` 时延后处理（1.3） |
| Vault 不可用 | 本轮该账号不拉取，记 `CREDENTIAL_UNAVAILABLE` |

## 7. 阶段 4 实现偏差

| # | 偏差 | 原因与影响 |
|---|---|---|
| 1 | AC-13 恢复原表述 | 阶段 3 误判发送记录页没有状态筛选；实际已有「投递状态」筛选且含「已送达」，不需要改动 |
| 2 | **Platform 与 Operations BFF 必须同批发布** | BFF 对 Platform 响应做逐字段严格解析（未知字段即失败）。新 Platform 返回的账号与尝试新字段会让旧 BFF 的账号列表、发送记录详情返回 502；第 6 节"旧 BFF 忽略新字段"的判断不成立。发布时先换 Platform 再立即换 BFF（与上一需求相同做法），Web 随后 |
| 3 | 拉取入口拆为 Job + Processor | `ext/job/NotificationEventPollJob`（调度入口，逐账号委托）+ `service/processor/PollNotificationEventsProcessor`（单账号一轮：Vault、接收、回写、删除），符合"ext 只做入口适配、编排在 Processor"的规范；设计第 1.2 节只写了 Job |
| 4 | 结果未知尝试被事件确认时的结果码 | `resolution_code` 写 `EVENT_<类型>`（如 `EVENT_DELIVERY`），便于区分由哪类事件确认 |
| 5 | 删除失败也记入拉取错误 | 删除被拒（如 IAM 缺 `sqs:DeleteMessage`）会导致消息反复到达，视同拉取失败记入 `event_poll_error` |
| 6 | 测试 Fixture 改为显式列名 | `NotificationTestMapper.recordAttempts` 原为按位置 `INSERT ... VALUES`，表加列后失效，改为显式列名。历史文件 `requirements/20261015/operations-console-通知管理/db/platform-support-service/acceptance-fixture-local.sql` 同样按位置插入，已执行过的历史工件不改；如需重跑须先补列名 |
| 7 | 新增 `micrometer-core` 显式依赖 | 原为经补偿组件传递获得；指标代码直接使用，改为显式声明（版本由 BOM 管理） |
