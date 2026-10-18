# Notification 领域与执行规则

日期：2026-09-20。本文按用户已确认的[需求整理](./requirement.md)收敛，约定 Platform Support 内 Notification 的 7 张业务表与内部服务能力；Operations 管理和 Webhook 详细设计不在本轮。

字段以 [data-design.md](./data-design.md) 为准，模块合同与暂缓接口以 [api.md](./api.md) 为准；未决项统一维护在[需求第 6 节](./requirement.md#open-items)。本文不扩展实现或部署授权。

> **2026-10-14 修订：** 首个真实 Provider 为 Amazon SES（`integration/provider/ses`），用户确认普通通知接受“至少发一次”。Provider 通过 `supportsQuery()` 声明查询能力；不支持查询的 Provider 按 [2026-10-14 SES 接入方案](../../20261014/platform-support-邮件AmazonSES接入/technique.md) §3.3 执行：SENT 即完成，UNKNOWN 在安全窗口（默认 10 分钟）后于发送次数内重发，次数用尽终止任务待人工确认。支持查询的 Provider 继续适用本文原规则。下文第 6 节、第 8.2 节中“UNKNOWN 不能重发/不能换 Provider”“真实供应商必须可查询”的表述仅约束支持查询的 Provider。

> **2026-10-15 修订（认证邮件）：** 认证临时秘密的瞬时合同按 [认证邮件接入方案](../../20261015/platform-support-认证邮件接入Delivery/technique.md) 落地：模块入口 `SendTransientEmailProcessor` 只接受 `notification.transient-template-codes` 中的模板，事务外读取模板/路由/主账号、Vault 凭据并调用 Provider 一次，不写通知表、补偿任务、日志；普通通知提交与 HTTP 白名单不能使用即时模板。下文"认证邮件对接后续"的表述由该方案取代。
>
> **2026-10-15 修订：** 内部 HTTP 提交 `POST /platform-support/notifications/v1/requests` 已开放，按 [通知内部 HTTP 接口需求](../../20261015/platform-support-通知内部HTTP接口/requirement.md)采用与 Vault V1 相同的过渡信任：私网部署限制 + `X-Caller-Service` + 部署配置白名单（服务名 → 可用模板）。`source_system` 直接保存调用方服务名（如 `payment-orchestration-service`），不取自 Body。下文"HTTP 待强服务身份后开放、不信任自报 X-Caller-Service"的表述由该需求取代；强服务身份到位后只替换身份识别层。

首期仅处理 Email，Provider 的发送和结果查询先在外部调用边界使用 Mock，内部 Request / Delivery / Attempt 流程仍按本领域规则设计。Mock 用于开发验证，不连接真实邮件供应商，其模拟结果不代表真实投递；真实供应商选型与接入、SMS 实现后续再定。Mock 适配器仅位于测试模块；Vault 仍经正式 Client 调用独立 HTTP Mock。

## 1. 模块职责

Notification 拥有 Request、Delivery、Attempt、模板定义、语言内容、Provider 和 Route。业务调用方提供通知原因对应的模板、参数、收件人和受控归属；Notification 不读取其他系统数据库，不重新筛选一批 Client。

本期仅同服务消费者调用 SubmitNotificationProcessor.process；其他内部服务的 HTTP 暴露等待强服务身份方案。Access Control 继续生成、验证、失效和消费认证挑战；Notification 不生成 OTP，也不修改密码。

Webhook 是 Delivery 模块内的独立能力，不加入 Notification 的 medium，也不复用一套通用 Delivery/Attempt 业务表。

## 2. 事实关系与公共信息

- 一个 Request 对应一个模板和一条选定路由，保存来源、归属、默认语言和公共参数。
- 一个 Request 有一条或多条 Delivery；每条 Delivery 指向一个邮箱或手机号，承担收件人明细和执行状态。
- 每次实际发送创建新的 Attempt，固定实际 Provider 和 provider_request_no。查询原发送继续使用原 Attempt。
- Template 定义编码、媒介和启停；Template Content 为每种语言保存一份当前标题/正文。
- Route 按模板配置主备 Provider；不同语言共用路由。Provider 表示具体发送账号，而不是供应商品牌。

Request 不保存独立汇总状态；汇总查询草案按 Delivery 当前状态计算。单人/批量由明细数量表达，不增加 send_mode。

### 2.1 参数与语言

有效语言为 Delivery.locale，缺失时继承 Request.default_locale。有效参数为 Request.default_parameters 加上 Delivery.parameters 的同名覆盖；整个覆盖对象为空或空对象时没有覆盖。

不做自动语言回退；选定语言必须有对应内容。参数完整性仍需在渲染时验证，删除数据库 variable_schema 不等于接受缺失的必要参数。参数只允许一层字符串 Map，具体限额见第 8 节。

受理后来源、收件人集合和普通参数保持不变；不向旧 Request 追加收件人。规范化后重复邮箱整单拒绝；数据库同时使用请求与稳定地址引用的唯一键兜底。

### 2.2 当前模板与配置

不保留模板历史版本或正文快照。模板文案更新后，待发送和重试会重新读取当前内容；原“已受理通知永远使用旧文案”的方案已取消。参数仍保留原输入，若当前文案要求的参数与原输入不再兼容，应显式处理失败，不能伪造成功。

Provider 的外部账号、媒介和适配身份不能被替换成另一个账号来解释历史 Attempt；换账号应创建新的 Provider。技术连接和凭据配置不在本次 Provider 表设计中。

Request 记录选定的 route_id；当前 Route 的主备配置可影响后续新建 Attempt。已经发出的 Attempt 仍按自身 provider_id 查询，不随当前路由改变。Provider 停用阻止新发送，不阻止原 Attempt 的必要结果查询。

## 3. 归属、来源与敏感数据

tenant_id 是 Request 的归属，不是收件人的租户。平台跨 Client 通知允许 tenant_id 为空；租户归属和平台发送权限由服务端验证，不能仅凭 Body 空值获得平台权限。

source_system 来自服务端认可的调用上下文，source_event_key 标识该来源的一次通知意图。二者的业务唯一约束用于阻止重复创建，不是 Gateway HTTP Idempotency-Key，也不提供服务内响应重放框架。相同来源身份再次提交直接抛 ConcurrentRequestException，不返回旧响应，也不驱动发送。

首期仅接收 email，并要求模板媒介为 EMAIL；对应 dataType/Purpose 由服务端确定。规范化地址经 Vault 保护后保存到 address_vault_token。规范化及专用 Purpose 见第 8 节；真实 Vault 注册和授权需在正式接入时核对。

允许落库的普通业务参数可以保存；OTP、重置令牌和认证链接的可消费秘密不能进入参数 JSON、正文快照、补偿 Payload、普通日志或审计。当前不增加 content_mode 或 message_vault_token 来绕开认证秘密的瞬时边界。

## 4. 普通通知受理与执行

以下流程面向可持久化的普通通知参数。认证临时材料仍按 Q-01 单独细化，不能直接交给可恢复的通用参数流程。

1. 模块调用方完成权限和真实请求归属判断；Processor 拒绝已有事务，执行公共基础校验，再检查来源业务身份、模板/语言/路由及全部收件人。
2. 事务外按邮箱固定类型完成 Vault 保护。供应商和 Vault 调用不得隐藏在数据库事务内。
3. 本地事务原子保存 Request、全部 Delivery 及所需可靠任务；提交失败不返回已受理。
4. 本地提交后唤醒执行器，返回 requestId、acceptedCount；外部投递状态不改变这个受理承诺。
5. 发送前检查状态、发送时间和当前配置；按当前内容渲染，并在事务外 Reveal 地址。
6. 短事务通过 Delivery CAS 创建 Attempt、固定请求号、递增 attempt_count，提交后调用 Provider。
7. 另一个短事务通过持有的 Delivery version 保存 Attempt 与 Delivery；公共任务在非终态保留，继续重试或查询。

受理前的输入/配置错误整单拒绝，不留下半套收件人集合；受理后的各 Delivery 独立处理结果。首期每次最多 100 人，不承诺无限广播。

## 5. 状态与计数

### 5.1 Attempt

| status | 含义 |
|---|---|
| PROCESSING | 本次发送尝试已开始，尚未获得明确提交结果 |
| ACCEPTED | Provider 已明确受理 |
| FAILED | 已明确发送失败；是否可以再次发送由业务规则决定 |
| UNKNOWN | 无法确认 Provider 是否受理 |

Attempt 描述一次发送提交。Provider 受理之后的最终送达结果由 Delivery 表达，不能把 ACCEPTED 解释为接收人已收到。

### 5.2 Delivery

首期状态如下；暂缓查询草案中的 CANCELLED 不进入生产枚举。

| status | 含义 |
|---|---|
| PENDING | 等待首次发送 |
| PROCESSING | 正在执行发送尝试 |
| RETRY_WAIT | 明确失败后等待下一次发送 |
| SENT | Provider 已受理，尚未确认最终送达 |
| DELIVERED | 已取得有效送达证据 |
| FAILED | 已明确失败，不再自动发送 |
| UNKNOWN | 无法确认原发送是否被受理 |

不恢复 EXPIRED、SUPPRESSED 等已移除能力的状态。取消统计草案不构成取消接口的实现授权。

attempt_count 是已创建发送尝试的数量，包含首次，初始为 0。创建 Attempt 和增加该计数在同一事务执行。next_retry_time 只表示下一次允许重新发送的时间，结果查询不使用它。

## 6. UNKNOWN 与可靠恢复

Provider 请求号必须在外发前保存。超时、断连或进程在可能已外发后崩溃，都不能当作“明确未发送”；遗留 PROCESSING 的恢复必须识别外部结果不确定。

| 原发送的确认结果 | 允许动作 |
|---|---|
| 明确已受理 | 原 Attempt 收敛为 ACCEPTED；Delivery 记为 SENT，必要时继续查询送达 |
| 明确未受理且允许重试 | 原 Attempt 收敛为 FAILED；Delivery 进入 RETRY_WAIT，设置 next_retry_time |
| 明确失败且不允许重试 | 原 Attempt/Delivery 进入对应明确失败结果 |
| 仍无法确定 | 保持 UNKNOWN，继续有界查询或停止自动查询并告警 |

“查询不到”必须按 Provider 协议解释；只有证据能确认原请求不会继续被受理，才能安排新发送。UNKNOWN 或已经受理的发送不能直接换 Provider 重发。

查询使用原 provider_id、provider_request_no，必要时使用原 provider_message_id；不创建新 Attempt，也不增加 attempt_count。结果确认更新 resolution_source、resolution_code 及适用的 resolved_time。

复用服务级 compensation_event 登记与唤醒查询，查询次数/下次执行时间由公共机制维护。补偿任务耗尽或标记失败不改变 UNKNOWN 的业务事实。不支持查询的 Provider 不靠查询恢复，而是按 [2026-10-14 SES 接入方案](../../20261014/platform-support-邮件AmazonSES接入/technique.md) §3.3 在安全窗口后重发（至少发一次）；人工处置接口本轮不设计。

当前尝试指投递最新一次 Attempt（按 attempt_id 最大）；窗口重发后更早的 UNKNOWN 尝试只作历史，不再被查询或接管。

任务只携带业务 ID 等最小身份，不保存地址、正文、OTP 或 Vault Token。公共 Claim/Lease、重入、任务超时以及领域状态/CAS 要共同防止重复外发和旧执行者覆盖；具体执行与接管规则见第 8 节，不向领域表加入另一套 Lease/Fencing 字段。

## 7. 当前方案与前序材料的差异

| 前序规划内容 | 本轮已确认方向 |
|---|---|
| 九张通知业务表，包含回调、抑制 | 七张表；回调和抑制不纳入，语言内容使用 template_content |
| Request/Delivery 重复正文、模板、路由等信息 | Request 保存默认信息，Delivery 仅保存地址、差异参数和执行状态 |
| SINGLE/BROADCAST 字段 | 收件人列表数量表达；所有邮件独立发送 |
| scope_key、用途/媒介重复字段 | Request 保留 tenant_id；媒介从模板获取，用途由模板编码表达 |
| 模板历史版本、默认语言回退、参数 Schema | 模板公共定义加当前语言内容；不保留历史，不自动回退 |
| Provider 连接/凭据/能力字段 | 只保留业务账号配置，技术接入另行处理 |
| provider_request_key、Attempt 序号和重复账号编码 | provider_request_no；不保留 attempt_no 或 provider_code 副本 |
| 回调确认 UNKNOWN | 主动查询；未能确认时保留未知事实 |
| 管理接口与内部通知入口一起规划 | Operations 单独处理，本轮只整理内部服务契约 |
| Request MAC、领域 Lease/Fencing、地址展示等扩展建议 | 不纳入已确认字段，必要机制仍按未决项评审 |

该差异只适用于本需求的 Notification 目标。前序规范中未涉及的 Platform 能力继续有效，旧 PDManer/SQL/代码不能因此被视为已经迁移。

## 8. 普通 Email 实施合同

### 8.1 入口、输入与异常

- 入口为 Delivery 模块的 `SubmitNotificationProcessor.process(SubmitNotificationRequest)`，继承公共 AbstractProcessor。非 HTTP 入口仍触发 Hibernate Validator；调用方不得包裹数据库事务。
- sourceSystem 是受信任同进程业务模块填写的系统编码；tenantId 是其已授权的实际归属。该合同不能直接映射未认证 Body/Header。Notification 不提供外部租户身份校验入口。
- 一次 1..100 个邮箱；首尾去空白并转小写，与当前平台邮箱惯例一致，不折叠点号/加号别名。首期支持 ASCII 邮箱，地址最长 254、local-part 最长 64。语言规范化为最长 35 的 BCP 47 标签，不自动回退。
- 默认及每个覆盖 Map、合并后的 Map 均最多 64 个键、UTF-8 JSON 最多 16 KiB；键为 `[A-Za-z][A-Za-z0-9_]{0,63}`，值为非 null 字符串且最多 2048 字符。null/空 Map 表示没有参数或没有覆盖；不接受嵌套值。保留键拦截仅为防误用，业务 Owner 仍必须保证值可持久化。
- 模板为纯文本 `${name}` 插值，不运行脚本/表达式，不二次解释参数。缺失占位符拒绝；标题最多 255 字符且无 CR/LF，正文最多 65535 UTF-8 字节。
- 基础字段或参数不合法使用 InvalidParameterException；配置不满足使用 BizCheckFailException；相同来源意图、数据库唯一冲突和 CAS 冲突使用 ConcurrentRequestException。适配器缺失使用 ServiceUnavailableException；损坏的本地关联使用 ErrorException。没有 HTTP 入口，因此本期不发布 HTTP 状态或 Problem Wire。

### 8.2 事务、任务与旧执行者

- Processor 先验证全部输入和配置，再通过正式 VaultClient Protect 邮箱，随后构造完整 SubmitNotificationContext。
- NotificationAcceptanceService 仅在一个本地事务保存 Request、全部 Delivery 和每个收件人的 NOTIFICATION_DELIVERY 补偿任务；Payload 为空，eventKey 仅为 Delivery ID。事务失败不触发外发。
- NotificationService 专职读取本库一致性快照、准备发送/查询与保存结果，不反向依赖 Registrar，不调用 Vault/Provider；Handler 负责事务外步骤。
- 创建 Attempt 前 CAS Delivery 的 version/status，成功后才增加计数、插入 Attempt；结果回写先 CAS 同一个 Delivery，再更新 Attempt，一起回滚。上下文对象仅在 SQL 成功后同步更新。
- 查询接管推进 Delivery version，遗留 PROCESSING 转 UNKNOWN，并继续原账号/请求号。旧发送响应不再持有版本，不能覆盖新查询结果；该 CAS 冲突由 Handler 映射为可恢复结果，下一轮只读取新的权威事实。已 ACCEPTED 的 Attempt 查询仍未知时保留 SENT/ACCEPTED。
- CAS 防止重复领取造成两次新 Attempt，并保护本地回写；它不承诺对外绝对 exactly-once。支持查询的供应商必须保证原请求身份可查询，且 RETRYABLE_REJECTED 表示原请求永久关闭、不可能随后受理。单纯 NOT_FOUND、超时或中断一律 UNKNOWN，不能启用备用发送。
- 默认最多 3 次实际发送，明确拒绝后至少等待 30 秒；重试使用当前配置，存在备用 Provider 时选择备用，否则继续主 Provider。框架每任务最多执行 24 次（包含发送、查询和技术恢复），单次 20 秒，退避 30 秒至 15 分钟、倍数 2、抖动 0.2。耗尽只停止自动任务；保留 UNKNOWN/SENT，最后一次执行崩溃还可能保留 PROCESSING，不能伪称确定失败。监控复用公共补偿失败指标，人工恢复接口后续处理。

### 8.3 配置与真实接入前提

- `notification.enabled` 正式默认 false；启用还需 `compensation.enabled=true`、Vault 连接和精确绑定 providerCode 的 EmailProvider 适配器。无适配器时拒绝受理，不注入生产 Mock。周期恢复复用公共 XXL-Job 扫描器，生产调度需单独配置；测试通过正式 Scanner 显式触发到期扫描。
- 配置统一使用 NotificationProperties；maximum-send-attempts、retry-delay 为类型化执行配置。人工认证均关闭时，通知独立启用需显式分配 `notification.worker-id`（0..31）；否则复用认证的应用级唯一 IdGenerator。
- Vault 使用 `EMAIL_ADDRESS_V1 / STABLE / PLATFORM_NOTIFICATION_EMAIL_V1`。该 Purpose 不复用登录授权；代码声明不等于 Vault 已注册或已允许调用。Token 不进入日志或补偿 Payload。
- 测试 Mock Provider 在 EmailProvider 边界显式返回模拟证据；SES 适配器经真实 AWS SDK 访问测试 HTTP 终端（SesMockController），真实 SES 只由 manual 用例验证。
- SES 账号配置为 `notification.ses.*`（`enabled`、`provider-code`、`region`、`from-address`、`configuration-set`、`endpoint`、`credential-token` 与超时；密钥对在 Vault，每次发送前经 `EmailProvider.prepare()` 读取），需同时开启 `notification.enabled`；生产凭据走默认凭据链。窗口为 `notification.unknown-resend-delay`。补偿开关为 `compensation.enabled: ${PLATFORM_SUPPORT_COMPENSATION_ENABLED:${files.onboarding.enabled:false}}`，单独启用通知需显式设置该变量。
- 手工建表入口见 [notification-create.sql](./db/notification-create.sql)。脚本仅用于没有同名 Notification 表的库，不自动迁移旧九表。公共 t_compensation_event 是前置依赖，复用[正式 Schema](../../../paytech/platform-support/docs/database/sql/platform-support-schema.sql)中的已有定义，不创建专属任务表。正式初始化 DML 待业务配置确认；测试 Fixture 按例准备/清理自身配置，不由应用启动建表或 Seed。
- Document Delivery 的通知租户一致性应沿 Delivery → Request 校验；旧 `(notification_delivery_id, tenant_id)` 复合关系不再适用于七表目标。其业务接入尚不属于本期，不能只凭单列 ID 授权。

实际编译、测试、数据库执行及缺口统一见[当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-delivery-domain-plan-20260920)。
