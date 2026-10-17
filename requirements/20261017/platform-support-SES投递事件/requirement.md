# Platform 邮件投递事件（Amazon SES）需求

> 状态：阶段 1 已确认（2026-10-17，Q-01～Q-10 全部按建议；Q-08 在阶段 2 提出调整，见 [analysis.md](./analysis.md) R-01）。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

通知发送账号维护上线后，mor-test 已经能经 Amazon SES 真实发信（2026-10-16，`SES_PRIMARY`，`ap-southeast-1`，`noreply@payorigo.com`）。但 SES 受理之后发生了什么，平台目前一无所知：

| 项 | 现状（2026-10-17） |
|---|---|
| 完成态 | [SES 接入 Q-02](../../20261014/platform-support-邮件AmazonSES接入/requirement.md) 决定"SES 受理即完成"：普通通知停在 `SENT`，从不变成 `DELIVERED` |
| 状态模型 | `t_notification_delivery.status` 已有 `DELIVERED` 枚举，发送记录统计也有 `deliveredCount`，但没有任何来源写入它 |
| 确认来源 | `t_notification_attempt.resolution_source` 只有 `SEND_RESPONSE`、`PROVIDER_QUERY`；没有回调来源 |
| 回调与抑制 | [Delivery 领域规划](../../20260920/platform-support-Delivery领域规划/requirement.md) 第 11 条：本轮不接回调、不做地址级抑制，`t_notification_provider_callback`、`t_notification_suppression` 两张表已移除 |
| 认证邮件 | 注册验证、找回密码走"即时邮件"：不写库、无发送记录、不重试；退信或投诉平台完全看不到 |
| 发送账号 | `t_notification_provider.configuration_set` 字段已在页面维护，但 mor-test 为空；SES 只有配了 Configuration Set（或在发件身份上设默认）才会产生事件 |
| SES 账户 | 新加坡区域，沙箱中（生产访问申请中）；SES 要求退信率、投诉率维持在低位，否则会进入审查甚至暂停发送 |

结果：发送记录看不到邮件是否真的送达；收件地址失效或被投诉时，平台既不知道，也不会停止继续发送，发件信誉只能靠 SES 自身兜底。

## 2. 使用者与目标

| 使用者 | 目标 |
|---|---|
| 运营排查人员 | 在发送记录里看到每封邮件的最终结果：已送达、退信（硬 / 软）、被投诉，以及时间和 SES 给出的原因分类 |
| 平台（发件信誉） | 硬退信、投诉的地址不再继续投递，退信率、投诉率可观测 |
| 运营配置人员 | 在发送账号上配置事件来源，知道该账号的事件是否在正常接收 |

## 3. 本期范围（建议）

1. **事件接入方式：Platform 主动拉取 SQS**（见 Q-01）。AWS 侧链路：SES Configuration Set → 事件目标 SNS 主题 → 订阅到 SQS 队列。Platform 定时从队列拉取、处理成功后删除消息。不需要公网回调入口，mor-test（本机 Docker，无公网入口）也能接收。
2. **接收的事件类型**：`Delivery`（已送达）、`Bounce`（区分 Permanent / Transient）、`Complaint`（投诉）、`Reject`（SES 拒发，如含病毒）。`Send`、`DeliveryDelay`、`Open`、`Click`、`Rendering Failure`、`Subscription` 不处理，收到即确认删除（Q-03）。
3. **按发送尝试匹配**：事件里的 SES `messageId` 对应 `t_notification_attempt.provider_message_id`，匹配到后更新该尝试及所属投递：
   - `Delivery` → 投递 `SENT` → `DELIVERED`；
   - `Bounce`（Permanent）、`Reject` → 投递变为 `FAILED`（不可送达，不重试）；
   - `Bounce`（Transient）→ 记录软退信事实，投递状态不变（SES 已自行重试，Q-04）；
   - `Complaint` → 记录投诉事实；若此前已 `DELIVERED`，状态保持，标记"被投诉"。
   - 晚到事件按"投诉、硬退信优先于送达"处理，重复或乱序事件不使状态倒退。
4. **匹配不到的事件**：即时邮件（认证邮件）、测试发送不生成发送记录，它们的事件匹配不到；只计数（按账号、事件类型）后删除消息，不落库、不记收件地址（Q-05）。
5. **地址抑制：依赖 SES 账户级抑制列表**，平台不建自己的抑制表（Q-02）。SES 对硬退信、投诉地址自动抑制，之后发往该地址的邮件 SES 受理但不投递，并回发一条 `Bounce`（`OnAccountSuppressionList`），本需求据此把投递记为 `FAILED`。
6. **发送账号增加事件来源配置**：账号上新增"事件队列 URL"（可空）；Platform 用该账号已录入的同一对 IAM 密钥访问队列（IAM 策略需增加该队列的 `sqs:ReceiveMessage`、`sqs:DeleteMessage`）。页面显示该账号最近一次收到事件的时间与最近一次拉取失败原因（Q-06）。
7. **发送记录页**：投递明细显示最终结果（已送达 / 退信 / 被投诉）、事件时间、SES 原因分类（如 `Permanent/General`、`abuse`）；不显示收件地址原文（沿用现有规则）。
8. **可观测**：按账号、事件类型计数（含匹配不到、解析失败）；队列拉取失败计数并在页面显示；日志不含收件地址、邮件头、事件原文。
9. **mor-test 接入**：AWS 侧配置步骤写进发布记录，由你在 AWS 控制台操作；Platform 侧通过页面填写队列 URL。

## 4. 明确不做

| 项 | 说明 |
|---|---|
| SNS HTTPS 回调到 Platform 公网入口 | 需要公网入口、SNS 签名校验与 Gateway 路由；mor-test 无公网入口。以后需要时可作为第二种接入方式 |
| 平台自建地址抑制表、运营手动解除抑制 | 依赖 SES 账户级抑制列表；解除抑制在 AWS 控制台操作 |
| 打开、点击追踪 | 需要改写邮件内容，事务邮件不需要 |
| 认证邮件的发送记录 | 即时邮件不落库的规则不变；它们的退信、投诉只计数 |
| 事件原文存档 | 只保存解析后的必要字段；原文含收件地址与邮件头 |
| 向业务系统发布送达 / 退信事件（MQ） | 当前没有消费方 |
| SMS、其他服务商的事件 | 本期只有 Amazon SES |
| 告警（退信率超阈值通知） | 先提供计数；告警通道另议 |

## 5. 验收标准

**功能验收**

| 编号 | 验收标准 |
|---|---|
| AC-01 | 配置了事件队列的启用账号，Platform 定时拉取队列；事件处理成功后删除消息，处理失败的消息留在队列中由 SQS 重新投递 |
| AC-02 | `Delivery` 事件使对应投递从 `SENT` 变为 `DELIVERED`，发送记录统计的 `deliveredCount` 随之变化 |
| AC-03 | 硬退信、`Reject` 使投递变为 `FAILED` 且不再重试；软退信只记录事实、状态不变；投诉记录在尝试上，已送达的投递显示"被投诉" |
| AC-04 | 同一事件重复到达、事件乱序（如投诉先于送达、送达晚于硬退信）时结果确定，状态不倒退 |
| AC-05 | 匹配不到发送尝试的事件（认证邮件、测试发送、其他系统）只计数后删除，不写业务表 |
| AC-06 | 队列不可达、密钥无 SQS 权限、消息格式无法解析时：不影响发信；失败计数增加，账号页显示最近失败原因；无法解析的消息不删除（由 SQS 死信策略兜底） |
| AC-07 | 拉取 SQS 在事务外；每条事件的落库是独立短事务，事务内不调用 Vault、SQS、SES |
| AC-08 | 发送账号可填写、修改、清空事件队列 URL（格式校验、乐观锁、审计）；未填写时不拉取 |
| AC-09 | 日志、审计、异常、计数标签中不出现收件地址、邮件头或事件原文；凭据仍只从 Vault 读取 |
| AC-10 | Platform 自动化测试覆盖各事件类型、乱序与重复、匹配不到、拉取失败；SQS 用测试 HTTP 端点返回 DTO；既有通知测试全部通过 |
| AC-11 | mor-test 配置真实 SES Configuration Set + SNS + SQS 后，测试发送以外的一封真实通知能变为 `DELIVERED`；发往 SES 模拟地址 `bounce@simulator.amazonses.com`、`complaint@simulator.amazonses.com` 分别得到退信、投诉 |

**视觉 / 交互验收**

| 编号 | 验收标准 |
|---|---|
| AC-12 | 发送账号编辑弹窗增加"事件队列 URL"（可选，附说明）；列表显示事件接收状态（未配置 / 最近接收时间 / 最近失败原因） |
| AC-13 | 发送记录详情的投递 / 尝试明细显示送达、退信、投诉结果与时间、原因分类；列表状态筛选可选"已送达"（页面已有，阶段 4 核对后恢复原表述，见 technique.md 第 7 节） |

## 6. 待确认问题

| 编号 | 问题 | 建议 |
|---|---|---|
| Q-01 | 事件怎么进来？SNS 推 HTTPS 回调，还是 Platform 拉 SQS？ | **拉 SQS**：不需要公网入口和 SNS 签名校验，Platform 停机时事件在队列中保留（默认 4 天，可调至 14 天），mor-test 也能用 |
| Q-02 | 地址抑制平台自己做，还是用 SES 账户级抑制列表？ | **用 SES 的**：新账户默认对硬退信、投诉开启；认证邮件没有发送记录，平台自建抑制也覆盖不到它们。阶段 2 请你在 SES 控制台确认 Suppression list 设置已开启 BOUNCE、COMPLAINT |
| Q-03 | 处理哪些事件类型？ | `Delivery`、`Bounce`、`Complaint`、`Reject`；其他类型直接确认删除。SES 侧事件目标也只勾这四种 |
| Q-04 | 软退信怎么处理？ | 只记录、不改状态：SES 对软退信会自行重试一段时间，最终送达会再发 `Delivery`，最终失败会发硬退信 |
| Q-05 | 匹配不到的事件（主要是认证邮件）怎么办？ | 只按账号、事件类型计数后删除；不新建记录、不存收件地址 |
| Q-06 | 事件队列配置放哪里？用哪套凭据？ | **放在发送账号上**（新字段，页面维护），复用该账号已录入的 IAM 密钥；你需要给 IAM 用户追加该队列的两个 SQS 权限。不另建一套密钥 |
| Q-07 | Configuration Set 怎么挂？ | 建议在 SES 身份 `payorigo.com` 上设"默认 Configuration Set"，账号上的 Configuration Set 字段留空：这样不用给 IAM 加 Configuration Set 资源权限。若在账号上显式填写，IAM 策略需同时包含该 Configuration Set 的 ARN |
| Q-08 | 拉取节奏用什么驱动？ | 沿用项目的 XXL-Job（与补偿任务同一机制），每分钟唤醒，每次每账号最多拉若干批；具体批次与长轮询参数在阶段 3 定 |
| Q-09 | 编辑范围 | Platform（`platform-support-delivery`、`platform-support-web`、`platform-support-api`）、`operations-console-bff`、`operations-console-web` 中通知相关文件；共享文件只做增量修改。Webhook 等其他会话的文件不动 |
| Q-10 | 适用阶段 | 1—8 照常；阶段 3 含页面设计（发送账号弹窗与发送记录明细的增量，若 Pencil 中 `design.pen` 仍未保存，改用截图标注说明）；6—7 test 部署需要 AWS 侧配置，届时一起列清单 |

## 7. 依据

- [SES 接入需求](../../20261014/platform-support-邮件AmazonSES接入/requirement.md)：Q-02（受理即完成）、"不做"中的事件回调
- [Delivery 领域规划](../../20260920/platform-support-Delivery领域规划/requirement.md)：第 11 条（只主动查询、不接回调、不做抑制），本需求部分取代该条中"不接回调"
- [通知发送账号维护](../../20261015/operations-console-通知发送账号维护/requirement.md)：账号字段、Vault 密钥只写不读；[发布记录](../../20261015/operations-console-通知发送账号维护/release/release.md) 末尾的真实发信记录
- [补偿事件规范](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)：XXL-Job 只作唤醒、数据库保护多实例
- Platform 模型：`t_notification_delivery`（`DELIVERED` 已有）、`t_notification_attempt`（`provider_message_id`、`resolution_source`）、`t_notification_provider`
