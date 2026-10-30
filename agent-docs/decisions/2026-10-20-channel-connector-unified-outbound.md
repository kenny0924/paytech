# 2026-10-20 决定：channel-connector 作为统一对外渠道出口，渠道分支付渠道与服务渠道

状态：已定（2026-10-20，用户确认需求阶段 1 "按建议"）。实施未开始；实施、验证与部署状态只记录在 [`CURRENT_IMPLEMENTATION.md`](../CURRENT_IMPLEMENTATION.md)。需求与方案见 [channel-connector：统一对外渠道出口](../../requirements/20261020/channel-connector-统一对外渠道出口/requirement.md)。

## 取代范围

- [2026-09-21 Platform Integration 决定](./2026-09-21-platform-integration-http-execution.md)：Platform 拥有外部 API 配置与执行、业务系统经 Platform 内部 HTTP 调用的 **Owner 与调用方式**。
- [2026-10-13 标准 API 与流程决定](./2026-10-13-platform-standard-api-flow.md)：其中"Platform 对内提供标准 API、Payment 等经 Platform 内部 HTTP 调用"的 **Owner 部分**；标准 API / 原子 API / 流程分层、条件分支、资金反向操作不在流程中编排、不保存执行记录等**能力规则继续有效**，Owner 改为 channel-connector。
- [2026-10-13 Platform 读取渠道账号决定](./2026-10-13-platform-channel-account-reveal.md)：Platform 以 `CHANNEL_EXECUTION` Reveal 渠道账号的例外整体撤销，由 channel-connector 自行 Reveal。
- [2026-09-13 Channel 术语决定](./2026-09-13-payment-channel-terminology.md)："Channel 仅指支付渠道、非支付供应商保留 Provider"的边界；该决定中 Provider → Channel 的支付命名迁移结果不变。

## 背景

对外请求原本分散：外部 API 配置与执行在 Platform，支付渠道业务身份在 channel-connector，一次渠道调用经 `Payment → channel-connector → Platform → 外部` 两跳；渠道账号 Token 归 channel-connector、明文却由 Platform 还原；卡原值进入 Platform 进程；非支付第三方服务没有归属，业务系统只能各自直连外部。用户目标是统一管理对外渠道的请求以提高安全性，并选择由 channel-connector 承担。

## 决定

1. **唯一出口：** 面向外部第三方的业务调用统一由 channel-connector 发出。其他服务不新增直连第三方的出站客户端；已登记的例外（Platform 通知 SES/SQS、文件 S3、Client Webhook 推送、Vault/KMS 与云 SDK 等，以需求技术方案的例外清单为准）保留在原 Owner，各自维持专项安全控制。网络层出口强制另立需求。
   - 例外清单与架构门禁 R15 白名单逐项对应，见 [唯一出口例外清单](./2026-10-22-unified-outbound-exception-list.md)。
2. **术语：** Channel / 渠道指任何外部第三方对接方，分两类：**支付渠道**（`PAYMENT`，NI、Geidea、PayerMax 等）与**服务渠道**（`SERVICE`，地图、KYC 筛查、短信、汇率等第三方服务）。类型创建后不可改。（**2026-10-30 修订**，[payout 结算出款决定](./2026-10-30-payout-settlement-payout-model.md) P3：增加第三类**出款渠道**（`PAYOUT`，自有银行、出款服务商、钱包），有绑定 OE 的出款账户、能力、路由策略与成本方案，只经出款入口 `PayoutOperationApi` 执行、只供 payout 调用；既收单又出款的服务商建两个渠道身份。类型仍创建后不可改。）Platform 的访问 Channel（`OPERATIONS`/`CLIENT`/`PARTNER`）与 Gateway Route Channel 是另外的概念，不受影响。
3. **Owner 迁移：** 标准 API、共用字段、接入方、原子 API、公共脚本、标准 API 实现、沙箱验证及执行引擎、注册函数、出站与读卡客户端整体从 Platform 迁入 channel-connector；Platform 不再拥有外部 API 定义与执行。
4. **一个渠道一个身份：** 接入方与渠道合并为一张渠道表（含类型、状态、出站配置），支付渠道的技术账号、MID、能力、成本挂在其下；不再跨库按编码对齐。
5. **凭据：** 支付渠道与服务渠道都使用技术账号保存凭据（Vault Token）。服务渠道的账号不绑定 OE，每个服务渠道限一个 ACTIVE 账号。channel-connector 同时是账号 Owner 与 Reveal 方。
6. **两个入口：** 支付入口 `ChannelOperationApi` 只接受支付渠道、只供 Payment；通用执行入口只接受服务渠道，调用支付渠道一律拒绝且不发出请求。
7. **调用授权：** 按"调用方服务 × 渠道 × 标准 API"白名单放行，白名单存表、在运营控制台维护并同事务留痕；未授权不发出请求。
8. **隔离：** 支付渠道与服务渠道的出站连接池与并发上限分开。
9. **Card：** card-service 读卡接口的允许调用方由 Platform 改为 channel-connector；卡原值只在内存使用、不落日志的约束不变。

## 影响

- channel-connector 成为外部 API 与渠道配置的唯一 Owner；Operations「外部 API」页面并入「渠道」菜单，BFF 转发改指 channel-connector。
- Platform 删除 integration 模块、相关表、`StandardApiCommandApi` 与 `IntegrationLookupApi` 合同；Vault 策略与 Card 允许调用方随之调整。
- [文档治理](../DOCUMENT_GOVERNANCE.md)、根 `AGENTS.md`、`PROJECT_CONTEXT.md`、`APPLICATION_ARCHITECTURE.md`、Platform 与 channel-connector 数据库文档、[Vault 架构](../VAULT_SERVICE_ARCHITECTURE.md)顶部例外按本决定更新。
