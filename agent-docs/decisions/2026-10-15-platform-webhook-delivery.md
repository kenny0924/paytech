# Platform Client Webhook 投递决定

- Decision status: Confirmed
- Implementation status: Implemented（Platform 本地；Commerce 生产端与 Operations 页面未实现）
- Deployment status: mor-test 已部署（2026-10-16，Vault 数据类型与规则仅在 mor-test 添加）；其他环境未部署
- Decision date: 2026-10-15
- 用户决定：URL 不经 Vault 保护；管理入口首期只做 Operations 代配置；密钥存 Vault；轮换宽限 1 小时；其余按 Claude 建议（不做 Challenge、内部 HTTP 同步受理、表收敛）。
- 需求与方案：[Webhook 投递需求](../../requirements/20261015/platform-support-Webhook投递/requirement.md)、[技术方案](../../requirements/20261015/platform-support-Webhook投递/technique.md)

## 1. 决定

| 编号 | 决定 |
|---|---|
| D-01 | Endpoint URL 以明文保存在 Platform 库（最长 1000 字符），普通日志只记 host |
| D-02 | 首期只提供 Operations 代 Client 配置；Client Console 自助后续单独设计 |
| D-03 | 签名密钥值保存在 Vault：数据类型 `WEBHOOK_SIGNING_SECRET_V1`（REFERENCE，值格式 `^whsec_[A-Za-z0-9_-]{43}$`），用途 `WEBHOOK_SIGNING`，Caller `platform-support-service` 的 PROTECT 与 REVEAL；本库只存 Token |
| D-04 | 不做 Endpoint Challenge 握手；以保存时与每次发送时的 DNS 公网地址校验加测试事件替代。删除 Endpoint Revision、Verification 与 Subscription 表，订阅作为 Endpoint 上的事件类型数组 |
| D-05 | 来源系统经私网内部 HTTP `POST /webhooks/v1/events` 同步提交冻结事件；204 表示已受理（含内容相同的重复提交），409 表示同一 eventId 内容不同。沿用 `X-Caller-Service` + 部署白名单的过渡信任 |
| D-06 | 轮换后旧密钥默认再参与签名 1 小时：宽限期内 Header 带 `v1=<新>,v1=<旧>`；可选立即作废。任何时刻最多两个签名 |
| D-07 | 重试节奏与执行租约复用公共 `compensation_event`（初始 1 分钟、×3、封顶 24 小时）；投递尝试上限默认 10 次后记 DEAD；超时与断连按失败重试，不做结果查询 |

## 2. 取代范围

- [Commerce Client API 设计](../../docs/superpowers/specs/2026-09-05-commerce-client-api-v1-design.md#13-client-webhook) 13.5“提交后发布 `ClientWebhookRequested`”的传输方式由 D-05 取代；请求体改为 `clientId`、`sourceVersion`、`sourcePayloadHash`、`payloadHash`、`envelope`，来源系统、eventKey、eventType、schemaVersion 分别取自调用方 Header 与 Envelope。事件内容、摘要算法与 Envelope 合同不变。
- 同一设计 13.6“每个 Attempt 只使用一个 ACTIVE Secret、双签需另行冻结”由 D-06 取代；Client 的验签方式不变。
- [Platform 数据库文档](../database/PLATFORM_SUPPORT_DATABASE.md)第 9 节的 Revision/Verification/Subscription、Payload `VAULT_TOKEN` 存储与 Lease/Fencing 描述由 D-01、D-04、D-07 取代，PDManer 由 8 表收敛为 5 表。
- [Vault 架构](../VAULT_SERVICE_ARCHITECTURE.md)中“Webhook URL 经 Vault 保护”“Webhook Secret 进 Secrets Manager”的描述分别由 D-01、D-03 取代。

## 3. 不变的边界

- Commerce 拥有事件业务内容，Platform 拥有 Endpoint、Secret、Delivery、Attempt、Retry、Replay 与审计。
- 至少一次投递；Client 按 `eventId` 去重，以 GET 为状态权威。
- 密钥明文只在新建与轮换的响应中出现一次，不进入日志、错误正文或补偿 Payload。
- 强服务身份到位后只替换调用方识别层，接口合同不变。
