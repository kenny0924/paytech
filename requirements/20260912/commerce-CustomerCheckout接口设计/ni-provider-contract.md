# NI Card 接入补充

> 历史接入研究：用户后续明确要求[自建存卡](../payment-自建CardVault/requirement.md)，本稿 Hosted Session/浏览器 NI 采集与 session_id 路径不再作为当前实施方案。NI 仍是首个收单 Provider；其适用卡数据提交协议和 3DS 需在新的卡数据环境边界内设计。保留以下内容供差异核对，不推断它已获实施批准。

2026-09-12：用户已选择 NI（Network International）为首个 Provider。结合既定嵌入式 Checkout UI，本稿采用 N-Genius Online Hosted Session / Web SDK 作为接入方案；仍为四个 Customer 操作，不新增初始化或 resume 操作。本稿补充 NI 参数及研发核验点，不代表已获得渠道账户、已完成沙箱支付或批准数据库变更。

公共字段沿 [Customer HTTP 合同](./customer-api-contract.md)。本次针对 NI 的新增类型、setup 绑定语义及沙箱门槛为技术补充，需随本轮方案评审。

## 1. 已核对的事实

NI 的详细指南说明：页面使用专门的 Hosted Session Key 与 outletRef 挂载托管字段，SDK 生成一次性 session_id，后台再使用独立支付凭据处理。页面专用 Key 不能执行付款；它与后台 API Key 不是同一个凭据。SDK 自认证默认五分钟，这不等于 Checkout 期限，也不能据此推断生成后 session_id 的准确期限。[官方 Web SDK 指南](https://docs.ngenius-payments.com/docs/web-sdk-integration-guide)

因此，本期 preparation 的 NI 部分主要是取得经过授权的采集配置和服务端绑定，不需要先向 NI 创建一个后台采集 Session。此前根据概览页提出的“后台先请求 Session”不能作为这一具体流程的必经步骤。浏览器点击 Pay 时才生成 session_id，映射到现有 card.token。

### SDK 参数的文档差异

同一指南的新示例使用 hostedSessionApiKey，另一示例使用 apiKey。本轮只读取得 NI 官方 Sandbox sdk.js，观察到 mountCardInput 实际读取 apiKey；这是当时 Sandbox 制品的证据，不推断所有环境永久相同。[官方 SDK](https://paypage.sandbox.ngenius-payments.com/hosted-sessions/sdk.js)

- 取证日期：2026-09-12；238647 bytes。
- SHA-256：dc244d05411266ead056f9fd323d1986762f3991eeda2f3768b7b6fe437d037b。
- 仅读取脚本，没有执行 SDK、提供真实 Key 或发起支付。未把第三方代码复制进仓库。
- 对外字段使用含义明确的 hostedSessionApiKey，Checkout Web 的 NI Adapter 映射到核验过的 SDK 参数 apiKey。实际沙箱/生产 SDK 应分别验证，不能直接复制指南的旧域名或某个示例的参数拼写。

## 2. cardSetup 的 NI 配置

adapterCode 的拟定目录值为 NI_HOSTED_SESSION。它选择前端受控适配器，不能用来由消费者选择支付路由。configuration 为具体 NiHostedSessionConfiguration 类型，不是 Map。

| 字段 | 必填 | 类型与来源 |
|---|---|---|
| hostedSessionApiKey | 是 | string，本项目接口暂定最多 4096；仅 NI Hosted Session Service Account 类型，由 Payment 的受控配置取得，不复用后台支付 API Key |
| outletRef | 是 | UUID 字符串，Payment 根据已固定 OE 与选中的渠道配置返回；提交时不以浏览器回传 outletRef 作为路由依据 |
| language | 是 | en / ar；由服务端 locale 映射，本期其他语言使用已批准的 en 回退 |

以下只是设计合成值，不是可运行配置：

```json
{
  "status": "READY",
  "setupId": "10482301",
  "adapterCode": "NI_HOSTED_SESSION",
  "expiresAt": "2026-09-12T14:30:00.000000Z",
  "configuration": {
    "hostedSessionApiKey": "synthetic-hosted-session-key",
    "outletRef": "00000000-0000-4000-8000-000000000001",
    "language": "en"
  }
}
```

SDK 脚本来源、mountId、样式、事件函数由 Checkout Web Adapter/部署配置管理，不添加到业务响应，不从任意返回 URL 动态执行脚本。NI-only 首片关闭多支付方式、DCC、Click to Pay 和分期扩展，不因为 SDK 支持就开放产品功能。

### setupId 的本期含义

NI 浏览器生成 Session 不代表服务器可以忘记它向页面展示了哪个 Outlet。这里提出 setupId 在 NI 下必填，表示 Payment 的“本订单采集绑定”，不是 NI session_id，也不是提前创建的 Attempt。它需要证明同一 Intent/OE、Provider/Account、Outlet 及令牌可用的 MID 范围；提交时重验并原子消费，防止初始化后后台改选不兼容渠道。

该收窄是对公共稿“只有动态服务端会话才需要 setupId”的补充。现有模型没有已批准的接受前采集绑定载体，需由 Payment 数据设计解决；不把它强塞进要求三份 Acceptance 的 Attempt，也不先造无用途的通用 Session 表。不能把 NI Outlet UUID 自动当成银行 MID 编号；必须核对真实账户的映射和同 Outlet 可用 MID 范围。

expiresAt 表达此绑定的服务端期限，最多到原 Checkout/Intent 到期；它不冒充 NI SDK 自认证时刻。页面按实际 mount 成功时间管理 SDK 自认证窗口，过期时重新挂载，重新输入卡信息，不发送已有不确定支付的新副本。有效绑定与原订单上下文保持不变。

## 3. 支付请求与 Payment Adapter

Customer Body 不变：legalConfirmation + card.setupId + card.token。NI 分支中，card.token 精确取 NI.generateSessionId() 结果的 session_id；不换成 savedCard.cardToken，不引入保存卡、MIT、订阅或再次使用一次性 Session 的流程。

Pay 交互：实际点击 → 禁止重复点击 → SDK 生成一次性值 → POST payments。SDK 生成失败时不发送支付命令，也不建立接受事实；一旦请求可能已被服务器受理，先查原命令/原支付，不再生成 Session 另付。

Payment Adapter 使用自己的后台认证凭据，向 NI 的 POST /transactions/outlets/{outletRef}/payment/hosted-session/{sessionId} 发送服务端最终金额。项目拟采用 PURCHASE 对应当前一次性 SALE 业务；准确允许动作和 USD 能力必须由实际 NI Outlet 验证。金额转换为最小货币单位只发生在 Provider Adapter，Customer 仍使用十进制字符串。凭据认证及媒体类型以实际批准 NI API 合同为准，不混用指南中的旧 PHP SALE 示例。[官方支付步骤](https://docs.ngenius-payments.com/docs/web-sdk-integration-guide#4-make-a-call-to-your-own-server-to-make-the-payment)

所有 NI 调用归 Payment，Commerce 不持有后台 NI Key，不自行拼渠道 URL。Session ID 不进数据库、日志、MQ 或永久回执；请求路径日志同样需要去除这一段。Payment 在发送前持久保存本地 Action/原执行身份，响应后只保存批准的稳定 NI Order/Payment 引用及权威结果。

## 4. 3DS instruction：具体类型候选

NI 官方示例在 AWAIT_3DS 时向 NI.handlePaymentResponse 传入支付响应；本轮 SDK 源码显示它会继续把响应交给 NI iframe。仅检查外层脚本不能证明删除字段后的 iframe 流程一定可运行。[NI 3DS 指南](https://docs.ngenius-payments.com/reference/alternative-emv-3ds-workflow-websdk-2)

不接受“完整 NI JSON 原样透传”作为实现方案。拟使用 NiThreeDsInstruction，仅投影认证必需字段；下面是供沙箱验证的固定类型候选，不是已验证的最小字段合同：

| 成员 | 候选类型 / 映射 |
|---|---|
| paymentReference / orderReference / outletRef | UUID string；从 NI 响应受控提取，绑定原 Payment Action |
| authenticationCode | string 或 null，拟上限 1024；仅响应中确有且 SDK 所需时传递 |
| authenticationUrl / challengeResponseUrl | HTTPS URL，拟上限 4096；从对应 NI link relation 提取，校验环境、路径及同 Order/Payment 坐标 |
| messageVersion | string，拟上限 16；不硬编码为唯一一个 3DS 小版本 |
| threeDSMethodUrl | HTTPS URL 或 null，拟上限 4096；可能来自发卡方 ACS，不能错误地限定为 NI 域名；不由 Commerce 下载 |
| threeDSServerTransId | UUID string |
| directoryServerId | string 或 null，拟上限 64 |

state=AWAIT_3DS、_id/self link 等可确定的 NI 结构由前端 Adapter 按获批映射组装，不让浏览器将这些内容反向宣告为支付事实。金额如 SDK 确需，取本次服务端响应的已冻结金额并作精确转换。是否还需其他字段必须由 NI 沙箱/支持确认后修改具体类型，不能添加任意扩展对象。

NI 示例中的 paymentMethod.pan/cvv、完整 paymentMethod、originIp、内部 MID、savedCard、其他链接不纳入该候选投影；不把带掩码的 cvc 字段也当作“可以整包转发”的理由。所有认证材料仅短暂响应，不能写到 t_payment_customer_action、幂等回执或日志。

Customer nextAction 仍使用原 actionId/type/expiresAt/instruction。SDK 完成只触发 GET 查询。GET 允许读已存在、仍可用的原指令，不重建挑战；无法安全复用时保持原支付待确认。当前资料没有证明所有刷新、中断都可恢复，不新增通用 resume 来掩盖这个缺口。

## 5. 响应丢失与后台收敛

NI 已知 Order Reference 时有正式 GET /transactions/outlets/{outletRef}/orders/{orderRef} 查询合同；不能据此推断“首次提交响应全部丢失、尚无 orderRef”也可以按 session_id 查询。[官方状态查询](https://docs.ngenius-payments.com/reference/retrieve-order-status)

本轮查阅资料未找到 Hosted Session 支付端点对 Idempotency-Key 的明确保证，也未证明单次 Session 重发返回原 Order 的语义。“一次性”只能证明一次性用途，不能替代响应丢失的恢复协议。因此不盲目透传本平台幂等头宣称 NI 已幂等，也不自动换 Session/Outlet 重试。

NI 官方 Webhook 文档说明每个事件只投递一次、失败不重试；因此 Payment 不能仅依靠 Webhook 收敛，仍需自身查询恢复。Webhook 身份、同订单关联及字段裁剪按真实渠道配置验证。[官方 Webhooks](https://docs.ngenius-payments.com/reference/consuming-web-hooks)

| 场景 | 处理 |
|---|---|
| NI 调用尚未开始，原进程丢失一次性材料 | 不能由补偿任务伪造 Session；只有持久执行状态能确定未发送时，才允许按明确恢复协议要求用户重新采集 |
| 发送可能已开始，响应丢失 | Payment 保持 UNKNOWN/原活动占用，不重发或换 Session；优先凭已知引用查回，引用全丢失的关联方案需 NI 确认或人工依据明确 Provider 证据收敛 |
| 已取得 NI Order/Payment 引用 | 保存稳定引用，后台定时查询；迟到 Webhook/查询结果通过原 Action CAS 收敛 |
| 3DS 完成而浏览器断开 | 后台查询/可信回调继续处理，不要求前端报告成功 |

这些恢复条件也是 202 的门槛：本地 Acceptance 与事件提交只证明本地记录成立，不能证明一次性支付输入已可靠交给 Owner。未建立安全恢复来源前，不宣称整条链路可后台自动重放。

## 6. 沙箱验收清单

1. 专用 Hosted Session Key 与后台 Key 分离；实际 Outlet 的 USD、PURCHASE、3DS、Card-only 配置和 MID/Account 映射。
2. mountCardInput 的实际 apiKey 参数、SDK 自认证过期及重新挂载、一次 Session 生成/重复提交语义。
3. NiThreeDsInstruction 裁剪后能完成 frictionless/challenge；SDK 返回失败、关闭、刷新及期限行为分别核验，不以外层源码代替 iframe 实测。
4. 首次提交响应丢失且未知 orderRef 时的稳定关联、可查询依据及人工处理边界；不能拿金额/时间近似匹配当付款证据。
5. Webhook 不达、重复/乱序输入、服务重启、UNKNOWN 占用、AUTHORIZED 与完成付款的区分。

没有执行上述真实沙箱测试。渠道凭据必须通过既有安全配置流程提供，不能写入本需求文档或通过聊天粘贴。当前可继续不依赖 NI 资金执行的 Commerce 设计和实现切片，实施顺序见[服务端方案](./implementation-design.md)。
