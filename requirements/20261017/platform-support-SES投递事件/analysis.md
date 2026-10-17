# Platform 邮件投递事件（Amazon SES）：现状差异与影响分析

> 阶段 2 评审稿（2026-10-17）。只读核对代码、Schema 与 mor-test 运行状态，未改代码、未执行 SQL。需求见 [requirement.md](./requirement.md)。

## 1. 关键发现

| 编号 | 发现 | 证据 | 影响 |
|---|---|---|---|
| F-01 | **每封 SES 邮件已带请求号标签**：`ses-<attempt_id>`，且这是 `t_notification_attempt` 的主键 | `SesEmailProvider.sendEmailRequest` 设置 `emailTags(REQUEST_NO_TAG = requestNo)`；`requestNo(attemptId) = "ses-" + attemptId` | SES 事件的 `mail.tags` 原样带回该标签，可**按主键**找到发送尝试，不需要给 `provider_message_id` 建索引；还能确认"发送超时、结果未知"的尝试其实已被 SES 受理（见 F-05） |
| F-02 | **XXL-Job 在全仓库都没有业务 Handler，mor-test 也没有运行 XXL-Job** | 全仓库无 `@XxlJob`；`application.yml` 注明"周期扫描的 XXL-Job 调度另行配置"；mor-test 容器中没有 xxl-job-admin | 已确认的 Q-08（用 XXL-Job 驱动拉取）在 mor-test 上**不会运行**，AC-11 无法验收。见 R-01 |
| F-03 | **AWS SDK 的 SQS 模块不在本机 Maven 仓库** | `~/.m3`、`~/.m2` 只有 `sesv2 2.31.50`，无 `sqs` | 构建一直用离线模式 `-o`；需要联网下载一次 `software.amazon.awssdk:sqs:2.31.50`（版本跟随现有 BOM）。见 R-02 |
| F-04 | `DELIVERED` 状态、页面文案与统计都已存在，只缺写入来源 | `NotificationDeliveryStatus.DELIVERED`；`complete()` 已支持 `DELIVERED`/`UNDELIVERABLE` 结果；Web `deliveryStatusLabels.DELIVERED = '已送达'`、`deliveredCount` | 状态模型不用扩展；新增"事件确认"路径即可 |
| F-05 | 结果未知的尝试会在 10 分钟后改用新尝试重发 | `releaseUnknown(resend=true)`，`unknownResendDelay = 10m` | 事件可能晚于重发到达，对应的是**旧尝试**。旧尝试送达 = 收件人其实已收到（可能收到两封）。处理规则见 R-03 |
| F-06 | 认证邮件、测试发送也用 `ses-<雪花 ID>` 作请求号，但该 ID 不是任何尝试的主键 | `SendTransientEmailProcessor`、`SendProviderTestEmailProcessor` 用 `ids.nextId()` | 它们的事件按主键查不到，自然落入"匹配不到只计数"（Q-05），不会误配到别人的投递 |
| F-07 | 结果确认来源只有 `SEND_RESPONSE`、`PROVIDER_QUERY` | `NotificationResolutionSource`；列注释列出允许值（无 CHECK） | 新增 `PROVIDER_EVENT`；只改枚举与列注释，不改列结构 |
| F-08 | 发送记录接口的尝试明细没有事件字段 | `AttemptItem`（API）、BFF `NotificationManagementModels`、Web `notification.ts` 都是逐字段类型化 | API、BFF、Web 三处都要增加事件字段（送达 / 退信 / 投诉、时间、分类） |
| F-09 | 发送账号的 `version` 是乐观锁，页面编辑带 `expectedVersion` | `t_notification_provider.version` | "最近收到事件时间、最近拉取失败"这类运行状态若写进账号行，**不能推进 version**，否则运营编辑会频繁冲突。见 R-04 |
| F-10 | mor-test 的 `SES_PRIMARY` 没有 Configuration Set，SES 身份上也还没有默认 Configuration Set | 2026-10-16 只读查询；发布记录 | AWS 侧需新建（阶段 6 清单） |

## 2. 需求项差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理建议 |
|---|---|---|---|---|
| AC-01 拉取与删除 | 无任何 SQS 代码与依赖（F-03） | 按账号拉取队列，成功后删除 | 未实现；缺依赖 | 新增 SQS 事件源适配器（外部集成层），事务外拉取；见 R-01、R-02 |
| AC-02 送达 | `SENT` 是终态，`DELIVERED` 无来源（F-04） | `Delivery` → `DELIVERED` | 未实现 | 新增事件确认服务方法，条件更新 |
| AC-03 退信、投诉 | 无 | 硬退信 / Reject → `FAILED`；软退信、投诉只记录 | 未实现；尝试表无事件字段 | 尝试表增加事件字段（阶段 3 定字段） |
| AC-04 重复与乱序 | 无 | 结果确定、不倒退 | 未实现 | 按"优先级只升不降"条件更新；见 R-03 |
| AC-05 匹配不到 | 无 | 只计数后删除 | 未实现 | 按标签主键查不到即计数（F-06） |
| AC-06 拉取失败 | 无 | 不影响发信、可观测 | 未实现 | 失败只记指标与账号运行状态；见 R-04 |
| AC-07 事务边界 | 发信路径已遵守 | 同 | — | 拉取、删除在事务外；每条事件一个短事务 |
| AC-08 队列配置 | 账号无该字段 | 页面维护队列 URL | 未实现 | 账号表加列；沿用现有编辑接口，**不新增接口** |
| AC-09 不泄露 | 发信路径已遵守；事件原文含收件地址与邮件头 | 同 | 新代码需同样处理 | 只解析白名单字段，原文不进日志、异常 |
| AC-10 自动化测试 | SES 已有测试 HTTP 端点 | SQS 同样用测试端点返回 DTO | 未实现 | 新增 SQS 测试端点（与 SES 测试端点同一方式） |
| AC-11 mor-test 真实链路 | SES 已能发信 | 送达、退信、投诉都能回写 | 依赖 F-02、F-10 | 阶段 6 列 AWS 侧清单；SES 提供模拟收件地址 |
| AC-12、AC-13 页面 | 发送账号页、发送记录页已有 | 增加字段与显示 | 未实现 | Web 增量修改 |

## 3. 逐系统影响

| 系统 / 模块 | 职责 | 影响点 | 代码 / 模型 / 配置 | 依赖与兼容风险 |
|---|---|---|---|---|
| `platform-support-delivery` | 通知领域 Owner | SQS 事件源适配器、事件解析、事件确认服务、定时拉取、指标 | 代码：是；依赖：新增 `sqs` | SQS 依赖需联网下载（R-02）；拉取驱动方式（R-01） |
| `platform-support-web` | 应用入口、配置、运营接口 | 账号编辑接口接收队列 URL；转换器输出事件字段；拉取开关与间隔配置 | 代码、配置：是 | 新配置默认关闭，不影响已部署环境 |
| `platform-support-api` | 运营接口 DTO | 账号响应 / 编辑请求加队列 URL 与运行状态；`AttemptItem` 加事件字段 | 代码：是（只加字段） | 只增字段；BFF 严格解析，须与 BFF 同批发布（阶段 4 核对，见 technique.md 第 7 节） |
| Platform Schema / PDManer | — | `t_notification_provider` 加队列 URL 与运行状态列；`t_notification_attempt` 加事件字段；`resolution_source` 注释加 `PROVIDER_EVENT` | DDL：是；DML：0；**注册 DML：0**（不新增接口与按钮） | 只加可空列，旧 Platform 不受影响 |
| `operations-console-bff` | 页面适配 | 账号与尝试模型加字段 | 代码：是 | 无 |
| `operations-console-web` | 页面 | 账号弹窗加队列 URL、列表显示事件接收状态；发送记录尝试明细显示事件结果 | 代码：是 | 无 |
| Gateway、Vault、Nginx | — | 不改：不新增公网入口与 Vault 规则（复用账号已有密钥） | 否 | 无 |
| AWS（你操作） | — | Configuration Set、事件目标 SNS、SQS 队列与订阅、死信队列、IAM 追加 SQS 权限、SES 身份默认 Configuration Set | 阶段 6 清单 | 见第 5 节 |
| 文档 | — | Delivery 领域规划第 11 条"不接回调"由本需求部分取代；当前状态台账 | 是 | 共享文件只做增量 |

## 4. 需要你确认的调整

**R-01 拉取驱动改为 Spring 定时任务（调整已确认的 Q-08）**
XXL-Job 在全仓库都没有业务 Handler，mor-test 也没有部署 XXL-Job（F-02），按 Q-08 做的话 test 上根本不会拉取。建议改为：
- 每个 Platform 实例用 Spring `@Scheduled` 定时拉取（Gateway 的快照刷新已是同一做法）；
- 多实例同时拉取是安全的：SQS 本身就是"多消费者竞争"模型，一条消息被某个实例取走后在可见性超时内对其他实例不可见；即使重复处理，数据库条件更新也保证结果一致；
- 配置开关默认关闭，开启后按账号逐个长轮询。

**R-02 需要联网下载一次 SQS SDK**
本机一直离线构建，`software.amazon.awssdk:sqs:2.31.50` 不在本地仓库（F-03）。阶段 4 开始时需要联网执行一次 Maven 下载（只这一个构件及其缺失的传递依赖，版本由现有 BOM 锁定）。不建议自己手写 SQS 协议和签名。

**R-03 事件晚于重发到达的规则**
结果未知的尝试 10 分钟后会重发（F-05），所以事件可能对应旧尝试。建议：
- 事件**总是记录在它对应的那次尝试上**（明细里能看到）；
- 投递状态只在"该尝试仍是当前尝试"时推进；旧尝试的送达事件额外把投递标为已送达（收件人确实收到了），旧尝试的退信、投诉不改变当前尝试的结果。
- 顺带收益：发送超时、结果未知的尝试，若 SES 其实已受理，事件到达后尝试由 `UNKNOWN` 变为 `ACCEPTED`（来源 `PROVIDER_EVENT`）。

**R-04 运行状态不推进账号版本**
"最近收到事件时间、最近拉取失败原因"写在账号行上但**不推进 `version`**，也不记审计（属运行状态，不是配置修改），避免运营编辑时频繁冲突。

## 5. AWS 侧前提（阶段 6 列具体步骤，现在不用操作）

1. 请先在 SES 控制台 **Configuration → Suppression list → Edit** 确认账户级抑制已勾选 BOUNCE、COMPLAINT（Q-02 前提）。
2. 新建 SNS 主题、SQS 标准队列（另配死信队列，最大接收次数如 5），SQS 订阅该主题（开启 Raw message delivery）。
3. 新建 Configuration Set，事件目标选 SNS，只勾 Delivery、Bounce、Complaint、Reject；设为身份 `payorigo.com` 的默认 Configuration Set（Q-07）。
4. IAM 用户追加该队列的 `sqs:ReceiveMessage`、`sqs:DeleteMessage`。

## 6. 工作量依据

| 部分 | 估计 |
|---|---|
| Platform：SQS 适配器、事件解析（4 类事件 + 标签匹配）、事件确认服务（含乱序 / 旧尝试规则）、定时拉取、指标、账号字段 | 主要工作量；状态机改动需配套完整的乱序矩阵测试 |
| Schema / PDManer | 两张表加列，约 8 列以内 |
| API / BFF / Web | 字段透传与两处页面增量 |
| 测试 | SQS 测试端点 + 事件场景矩阵；BFF、Web 单测 |
| 风险 | R-01、R-02 需确认；AWS 侧配置由你操作，阶段 7 才能完成真实链路验收 |
