# Platform Support Notification 需求

日期：2026-09-20；实施范围确认：2026-09-21。用户已要求开始实现，并确认普通 Email 首期、拒绝重复邮箱、只开放模块内调用。实现及验证状态只见当前台账。

本次涉及 Platform Support 一个系统：目标为 7 张 Notification 业务表，保留 1 个内部提交接口及 1 个已认可但暂缓继续细化的汇总查询草案；不纳入 Operations 管理接口、页面、HTTP 暴露或部署；建表 SQL 单独审阅后手工应用。

## 1. 目标与阅读入口

Notification 位于 Platform Support 的 Delivery 模块中，统一承接 Email、SMS 通知。OTP、邮箱验证、密码重置和普通业务通知共用通知能力；验证码生成、验证、失效、消费及密码修改仍由认证模块负责。

首期范围收窄为 Email，邮件 Provider 先使用 Mock；真实邮件供应商的选型和接入后续再定。SMS 保留在统一领域规划中，暂不实现。Mock 仅用于测试适配器，不在正式应用提供模拟成功，不发送真实邮件。认证邮件对接后续处理。（2026-10-15 修订：已按[认证邮件接入 Delivery 需求](../../20261015/platform-support-认证邮件接入Delivery/requirement.md)接入：Client 验证/重置邮件经 Delivery 即时发送入口 `SendTransientEmailProcessor` 使用模板与 SES 账号发送一次，链接不落库、不重投。）

首期同服务消费者使用模块公开 Processor；其他内部服务的 HTTP 接口待强服务身份合同确定后再开放，不信任自报 X-Caller-Service。（2026-10-15 修订：已按[通知内部 HTTP 接口需求](../../20261015/platform-support-通知内部HTTP接口/requirement.md)以过渡信任开放内部 HTTP 提交。）Notification 不单独成为微服务，也不成为浏览器或外部 Client 的任意发信入口。

| 文档 | 唯一维护内容 |
|---|---|
| [领域与执行规则](./technique.md) | 职责、受理/发送/恢复流程、状态及前序设计差异 |
| [表结构](./data-design.md) | 7 张表的字段、关系和已讨论约束 |
| [内部接口](./api.md) | submitNotification Demo、收件人字段、响应及查询草案状态 |
| [确认记录](./delivery.md) | 本轮确认、删除和暂缓决定 |
| [当前台账](../../../agent-docs/CURRENT_IMPLEMENTATION.md#platform-delivery-domain-plan-20260920) | 文档验证、实现及部署状态 |

本文拥有范围、验收标准与未决项；其他文档引用这些结论，不另行扩大范围。

## 2. 已确认的需求

1. 统一模型面向 Email 和 SMS，首期只处理 Email。调用方通过 templateCode 选择模板，媒介由模板确定。
2. 一个 Request 可以包含一个或多个收件人，每个收件人对应独立 Delivery；每封邮件只有一个收件人，不共享 To/CC/BCC。
3. 调用方提供本次确定的收件人列表，Notification 不查询 Client 名单，不在重试时重新选择收件人。
4. Request 保存默认语言和公共参数；Delivery 可覆盖语言，并按参数名覆盖公共参数。普通参数在受理后保持不变。
5. 首期输入只有 email，服务端按 EMAIL_ADDRESS_V1 校验、规范化和保护，调用方不指定 dataType；后续 SMS 将使用独立 phoneNumber 字段。
6. tenant_id 表示请求归属，不表示收件人所属租户。平台跨 Client 通知允许为空；租户请求保存真实 tenant_id，并校验调用权限。
7. 不保留模板历史版本。模板定义与按语言的当前内容分表；待发送及重试使用当前内容重新渲染，不承诺沿用受理时的旧文案。
8. Delivery 保留 attempt_count、next_retry_time；Attempt 表示一次发送，查询原发送结果不会产生新 Attempt，也不会增加发送次数。
9. Provider 仅保存发送账号的业务配置；技术连接、凭据和能力声明暂不进入该表。
10. 路由按模板配置主 Provider 和可选备用 Provider。明确失败且允许重试时才能切换，UNKNOWN 先查询原发送。
11. 仅通过主动查询确认供应商结果，本轮不接回调、不做地址级抑制。
12. 同一请求内规范化后重复邮箱整单拒绝，不静默去重；不同 Client 共用邮箱时由调用方先整理名单。
13. 提交成功代表通知已受理，不等于供应商已受理，也不等于接收人已收到。

## 3. 范围与不做项

本期实现普通通知的模块提交、独立发送和原发送恢复，并同步七表物理设计。配置表是发送所需的领域数据，不意味着本轮提供配置管理接口。

| 内容 | 本轮处理 |
|---|---|
| submitNotification | 保留统一内部提交入口，普通通知请求/响应见 api.md |
| 邮件 Provider | 首期 Mock，暂不选择或接入真实供应商 |
| SMS | 后续能力，首期不实现 |
| getNotification | 已认可汇总查询草案；按最新要求暂不继续展开查询类接口 |
| findNotificationBySource、listNotificationDeliveries、getDelivery | 暂不提供，不把讨论过的 Demo 当作当前合同 |
| Operations 模板/内容/Provider/路由管理 | 单独处理，本轮不纳入 |
| 取消、人工重试及人工刷新接口 | 后续单独讨论，不作为已批准接口 |
| Webhook | 保留独立模块边界，不细化表、接口或执行流程 |
| Provider Callback、Suppression | 从当前 Notification 方案移除 |
| 历史模板版本、正文快照、统一投递期限、优先级、发送模式字段 | 不纳入当前模型 |
| Java、Maven 模块、PDManer、建表 SQL、自动测试 | 进入实现范围；不自动执行 DDL |
| HTTP API、基础设施配置、认证邮件接入、部署 | 暂不处理 |

## 4. 业务与安全边界

- 业务 Owner 决定通知原因、接收对象和业务权限；Notification 负责通知投递事实，不接管认证、订单或其他领域状态。
- 来源业务身份用于阻止重复创建通知，不引入服务内 HTTP 请求重放组件；Gateway 的 Idempotency-Key 职责保持不变。
- 邮箱、手机号分别使用已批准的数据类型与用途进行 Vault 保护；Delivery 统一保存地址引用，不在普通查询、日志或补偿 Payload 暴露 Token。
- 只有允许持久化的业务参数才能进入 default_parameters / parameters。OTP、密码重置令牌和可消费认证链接不能进入这些字段、正文持久化、MQ、日志或审计。
- 普通通知可恢复发送与认证临时材料的恢复能力不同；统一入口方向不代表当前普通通知 Demo 已定义完整 OTP 合同。
- Provider、Vault 等外部调用均在数据库事务外；可靠调度复用服务级 compensation_event，不另建专属任务框架。
- UNKNOWN 保留未知事实，不能因为查询耗尽或任务失败而改写为“确定发送失败”。

## 5. 验收标准

| 编号 | 可审阅的需求结果 |
|---|---|
| AC-01 | 一个或多个收件人统一表达，邮件独立发送，各 Delivery 的结果相互独立 |
| AC-02 | 默认语言/参数与收件人覆盖规则明确，Email/Phone 输入和 dataType 分开处理 |
| AC-03 | 7 张表字段及来源、Provider 请求号等约束一致；重复邮箱拒绝与数据库唯一保护一致 |
| AC-04 | 模板定义与语言内容分表，不保留历史版本；重试读取当前文案的影响明确 |
| AC-05 | 模块提交 Demo、提交成功的受理含义和最小响应明确，不增加未采纳的 deliveryIds |
| AC-06 | 已认可查询草案、暂缓查询和 Operations 管理边界分开，不混为本期接口清单 |
| AC-07 | 发送次数、发送重试时间、查询任务及 UNKNOWN 确认规则明确 |
| AC-08 | 认证秘密不持久化；临时材料合同、执行参数及配置准备等缺口明确，不把文档确认当成运行能力 |

<a id="open-items"></a>

## 6. 本期落定与后续事项

| 编号 | 结论或待办 |
|---|---|
| Q-01 | 用户确认认证邮件后续对接；OTP、重置令牌等瞬时合同不属于普通参数接口。2026-10-15 由[认证邮件接入 Delivery 需求](../../20261015/platform-support-认证邮件接入Delivery/requirement.md)细化：独立即时发送入口，临时参数只在内存渲染，不建发送记录；即时模板不能经普通通知使用 |
| Q-02 | 用户确认重复邮箱拒绝，增加 (request_id, address_vault_token) 唯一保护 |
| Q-03 | 用户确认只开放模块内调用。业务调用方先完成权限判断并生成来源/归属；不提供 Header 身份入口。输入限额及重复来源冲突见 technique.md 第 8 节 |
| Q-04 | 本期采用公共补偿领取/超时与 Delivery version CAS，明确拒绝才能新建发送；预算和恢复规则见 technique.md 第 8 节 |
| Q-05 | 首期使用 EMAIL_ADDRESS_V1/STABLE 与专用 PLATFORM_NOTIFICATION_EMAIL_V1 Purpose；真实 Vault 用途授权及真实 Provider 接入仍需后续配置，不能假定已生效 |
| Q-06 | 自动测试按例创建配置 Fixture 并清理；正式模板、语言、账号及路由初始化需后续业务配置审批，不提供生产默认模板或 Mock 账号 |

address_masked 和响应 deliveryIds 不纳入本期。强服务身份、HTTP 入口、真实 Provider、SMS、认证临时材料和 Operations 管理分别作为后续工作，不能通过本次模块实现自动启用。

## 7. 依据与取代范围

- [Platform 模块设计](../../../docs/superpowers/specs/2026-09-06-platform-support-modular-code-design.md)：Delivery 模块边界。
- [Platform 数据库文档](../../../agent-docs/database/PLATFORM_SUPPORT_DATABASE.md)及[前序精简设计](../../../docs/superpowers/specs/2026-09-01-platform-support-database-simplification-design.md)：本轮是 Notification 局部后继需求，具体差异见 technique.md；不宣称前序模型、物理库或其他 Platform 能力已经迁移。
- [认证首期约束](../../20260914/client-console-注册与客户入驻/data-design.md#5-邮箱链接会话与文件)：认证临时秘密和新挑战重发的边界。
- [开发规范](../../../agent-docs/DEVELOPMENT_CONVENTIONS.md)、[工程规范](../../../agent-docs/JAVA_PROJECT_STRUCTURE.md)、[本地补偿规范](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)、[Vault 边界](../../../agent-docs/VAULT_SERVICE_ARCHITECTURE.md)：事务、模块公开能力、恢复与敏感值保护。
