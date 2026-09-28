# Notification 表结构

日期：2026-09-20。本文记录用户已确认的字段取舍与参考类型，目标为 7 张 Notification 业务表。2026-09-21 普通 Email 实施按本字段清单生成 PDManer 与[七表建表脚本](./db/notification-create.sql)；实际执行状态只见当前台账。

范围与未决项见 [requirement.md](./requirement.md)，执行语义见 [technique.md](./technique.md)，接口字段见 [api.md](./api.md)。

首期仅使用 EMAIL，邮件 Provider 先 Mock；表中 SMS 相关表达保留为后续规划。本次范围调整不新增 Mock 专用业务字段，也不改变下述七表结构。

## 1. 通用约定与清单

- 主键为 Snowflake BIGINT；内部 HTTP 示例中的 ID 使用十进制字符串。
- 每张表都有以下两个非空通用字段，后续各表不再重复列出。

| 字段 | 类型 | 说明 |
|---|---|---|
| create_time | DATETIME(6) | UTC 创建时间 |
| update_time | DATETIME(6) | UTC 更新时间 |

- 下文只有显式列出的表保留 version，它仅用于并发更新保护，不产生历史版本。
- 不创建物理 FOREIGN KEY 或 CHECK；关联、归属、字段组合和状态规则由应用与本地事务保证，保留主键、唯一约束及非空要求。
- Request 汇总、Provider 技术配置、正文快照和恢复任务不伪装成无约束的扩展 JSON。

| 表 | 事实 |
|---|---|
| t_notification_request | 一次通知请求的归属、来源和公共默认值 |
| t_notification_delivery | 一个收件人的差异参数和投递状态 |
| t_notification_attempt | 一次实际发送的外部身份和结果 |
| t_notification_template | 模板公共定义 |
| t_notification_template_content | 每种语言的一份当前内容 |
| t_notification_provider | 具体发送账号的业务配置 |
| t_notification_route | 模板对应的主备 Provider |

关系：Request 1:N Delivery，Delivery 1:N Attempt，Template 1:N Template Content，Template 1:1 Route；Route 引用主 Provider 和可选备用 Provider，Attempt 固定实际 Provider。

## 2. t_notification_request

| 字段 | 参考类型 | 可空 | 说明 |
|---|---|---|---|
| request_id | BIGINT | 否 | 请求主键 |
| tenant_id | BIGINT | 是 | 请求归属租户；平台通知为空 |
| source_system | VARCHAR(64) | 否 | 服务端认可的来源系统 |
| source_event_key | VARCHAR(128) | 否 | 该来源内的一次通知业务标识 |
| notification_template_id | BIGINT | 否 | 模板定义 ID，不是历史版本 ID |
| default_locale | VARCHAR(35) | 否 | 默认语言 |
| default_parameters | JSON | 是 | 允许持久化的公共业务参数 |
| notification_route_id | BIGINT | 否 | 本次选择的路由 |

唯一约束：(source_system, source_event_key)。

tenant_id 不参与这条唯一键；source_event_key 必须在整个来源系统内区分不同通知意图，不能只在某个租户内唯一。模块来源、权限前提及重复输入冲突见 technique.md 第 8 节。

受理后请求公共输入及收件人集合保持不变。Request 不保存汇总状态/计数、正文快照或参数的完整请求副本。medium 从 Template 取得，用途由 template_code 表达。

## 3. t_notification_delivery

| 字段 | 参考类型 | 可空 | 说明 |
|---|---|---|---|
| delivery_id | BIGINT | 否 | 投递主键 |
| request_id | BIGINT | 否 | 所属 Request |
| address_vault_token | VARCHAR(255) | 否 | 与模板媒介对应的邮箱或手机号稳定 Vault 引用 |
| locale | VARCHAR(35) | 是 | 收件人语言，缺失时继承 Request.default_locale |
| parameters | JSON | 是 | 收件人的差异参数，按参数名覆盖公共参数 |
| status | VARCHAR(32) | 否 | 当前投递状态 |
| attempt_count | INT | 否 | 已创建发送尝试的数量，包含首次，初始为 0 |
| next_retry_time | DATETIME(6) | 是 | 下一次允许重新发送的时间；无需重试或尚未安排时为空 |
| version | INT | 否 | 并发状态更新保护 |

用户已确认同一请求内重复邮箱整单拒绝；唯一键 (request_id, address_vault_token) 作为数据库保护，不执行隐式合并。

当前通过公共补偿表扫描任务，并按 Delivery 主键恢复；Request 的收件人访问复用唯一键左前缀，暂缓查询的额外索引不提前创建。不保存 recipient_no、last_error_code 或重复的 Template/Route/medium/tenant_id 字段。

address_masked 仅曾作为列表展示建议；对应列表接口暂缓，当前不纳入字段。整个 parameters 为 NULL 或空对象表示无覆盖；键级 null 和嵌套对象不允许，限额见 technique.md 第 8 节。

## 4. t_notification_attempt

| 字段 | 参考类型 | 可空 | 说明 |
|---|---|---|---|
| attempt_id | BIGINT | 否 | 发送尝试主键 |
| delivery_id | BIGINT | 否 | 所属 Delivery |
| provider_id | BIGINT | 否 | 本次实际使用的发送账号配置身份 |
| provider_request_no | VARCHAR(128) | 否 | 按 Provider 规则生成并在外发前保存的请求号 |
| provider_message_id | VARCHAR(255) | 是 | Provider 返回的消息标识 |
| status | VARCHAR(32) | 否 | PROCESSING、ACCEPTED、FAILED、UNKNOWN |
| response_code | VARCHAR(64) | 是 | 初次发送调用的安全响应码 |
| resolution_source | VARCHAR(32) | 是 | 结果确认来源，如 SEND_RESPONSE、PROVIDER_QUERY |
| resolution_code | VARCHAR(64) | 是 | 后续查询确认结果的安全响应码 |
| started_time | DATETIME(6) | 否 | 本次发送尝试开始时间 |
| finished_time | DATETIME(6) | 是 | 初次发送调用结束时间 |
| resolved_time | DATETIME(6) | 是 | 原本未知的结果被确认的时间 |

唯一约束及字段顺序固定为：(provider_request_no, provider_id)。请求号的合法格式和长度还需与具体 Provider 合同核对，不要求不同 Provider 使用相同生成规则。

不保留 attempt_no、provider_code 副本或 result 字段，执行状态统一使用 status。provider_message_id 不擅自增加全局唯一约束。

Attempt 的发送身份保持不变；初次响应与后续确认分别记录。查询、接管和恢复不能把原 Attempt 改成一次新的发送，也不能因为 finished_time 非空就断言结果已经明确。状态推进与 Delivery 的状态/版本保护在本地事务中协调，细节见 Q-04。

## 5. t_notification_template

| 字段 | 参考类型 | 可空 | 说明 |
|---|---|---|---|
| template_id | BIGINT | 否 | 模板定义主键 |
| template_code | VARCHAR(64) | 否 | 唯一模板编码，表达通知用途 |
| medium | VARCHAR(16) | 否 | EMAIL 或 SMS |
| status | VARCHAR(16) | 否 | 模板启停状态 |
| version | INT | 否 | 并发更新保护 |

唯一约束：(template_code)。Email 和 SMS 使用各自的模板编码。模板定义不再保存 locale，语言属于下方内容表；Request.default_locale 和 Delivery.locale 共同确定本次所用语言。

不保存 notification_type、revision_no、current_revision_no、allowed_scope、default_locale、allow_locale_fallback、variable_schema 或 active_slot。

## 6. t_notification_template_content

| 字段 | 参考类型 | 可空 | 说明 |
|---|---|---|---|
| content_id | BIGINT | 否 | 内容主键 |
| template_id | BIGINT | 否 | 所属模板定义 |
| locale | VARCHAR(35) | 否 | 内容语言 |
| subject_template | TEXT | 是 | 邮件标题模板；SMS 不使用 |
| body_template | TEXT | 否 | 正文模板 |
| version | INT | 否 | 当前内容的并发更新保护 |

唯一约束：(template_id, locale)。

每种语言只有一条当前内容，修改文案直接更新；不另建翻译表或保存历史版本。所需语言缺失时不自动回退。重试继续使用原业务参数，但读取当前文案，因而不保证与首次内容完全相同。

## 7. t_notification_provider

| 字段 | 参考类型 | 可空 | 说明 |
|---|---|---|---|
| provider_id | BIGINT | 否 | Provider 主键 |
| provider_code | VARCHAR(64) | 否 | 唯一编码，标识具体发送账号 |
| medium | VARCHAR(16) | 否 | EMAIL 或 SMS |
| status | VARCHAR(16) | 否 | 启用、停用 |
| version | INT | 否 | 并发更新保护 |

唯一约束：(provider_code)。

Provider 表示外部发送账号，不能仅按供应商品牌归并不同账号。已引用的 provider_id 继续解释原发送账号；换账号创建新的 Provider。

adapter_code、endpoint_ref、secret_ref、supports_query 等技术接入字段不纳入本表；不保留回调配置或配置历史版本。具体绑定及能力核对见 Q-05。

## 8. t_notification_route

| 字段 | 参考类型 | 可空 | 说明 |
|---|---|---|---|
| route_id | BIGINT | 否 | 路由主键 |
| notification_template_id | BIGINT | 否 | 对应模板 |
| primary_provider_id | BIGINT | 否 | 主 Provider |
| fallback_provider_id | BIGINT | 是 | 备用 Provider |
| status | VARCHAR(16) | 否 | 启用、停用 |
| version | INT | 否 | 并发更新保护 |

唯一约束：(notification_template_id)。

同一模板的语言内容共用路由，不保存重复 medium、notification_type 或租户差异路由字段。应用校验主备 Provider 媒介与模板一致，以及 Request 的 Template/Route 关系一致；不将已引用路由重新挂到另一个模板。

路由当前配置可影响后续新 Attempt，历史 Attempt 始终使用其 provider_id。UNKNOWN 不允许直接切换备用 Provider。

## 9. 明确未纳入的表与字段

| 位置 | 本轮不纳入 |
|---|---|
| 整张业务表 | t_notification_provider_callback、t_notification_suppression |
| 旧语言表命名 | t_notify_template_translation，当前目标使用 t_notification_template_content |
| Request | scope_key、notification_type、medium、send_mode、message_vault_token、priority、expires_time、content_mode、汇总状态及 MAC 扩展提案 |
| Delivery | recipient_no、last_error_code、address_masked、重复公共字段、正文快照及尚未确定的领域 Lease/Fencing 字段 |
| Attempt | attempt_no、provider_code、provider_request_key、result、完整报文摘要及尚未确定的领域 Fencing 字段 |
| 模板与配置 | 内容/配置历史版本、默认语言回退、模板参数 Schema、active_slot、Provider 技术接入字段 |
| 专属基础设施 | Notification Inbox/Outbox/重试任务表，可靠执行复用服务级 compensation_event |

provider_request_key 已明确改名为 provider_request_no，而不是两字段并存。version 只存在于各表显式列出的并发控制位置。

## 10. 数据与物理设计边界

地址保存稳定 Vault 引用；email 与 phoneNumber 分别按对应的已批准数据类型和用途处理，不由模板参数或调用方随意指定 dataType。具体授权和规范化仍见 Q-05，不假定 Vault 返回 Mask 或 Blind Index。

default_parameters / parameters 只保存允许落库的业务参数，不能保存 OTP、认证令牌、可消费认证链接或任意完整业务对象。认证临时输入见 Q-01，不通过增加通用密文/正文 Token 字段替代详细设计。

字段参考类型不构成自动建表或迁移授权。实际实施需同步 PDManer、手工 SQL、映射与相应验证，并保留主键/唯一索引/NOT NULL；本轮不生成或执行这些工件。

依据：[数据库规范](../../../agent-docs/DATABASE_DESIGN_GUIDE.md)、[Vault 边界](../../../agent-docs/VAULT_SERVICE_ARCHITECTURE.md)、[补偿规范](../../../agent-docs/COMPENSATION_EVENT_GUIDE.md)、[请求幂等职责](../../../agent-docs/decisions/2026-09-17-gateway-request-idempotency.md)。

## 10. 物理约束补充（2026-09-21）

Delivery 增加唯一键 `(request_id, address_vault_token)`。version 默认 1，attempt_count 默认 0；next_retry_time 只在 RETRY_WAIT 非空。来源编码、来源键、模板编码、语言、地址 Token、Provider 编码和请求号采用 utf8mb4_bin 精确比较。七表合计 65 个字段（包含时间字段），无物理 CHECK/FK。Document Delivery 的租户检查改沿 Delivery → Request，后续接入时验证。
