# Customer 接口简化复核

> 历史复核记录：后续已确认自建 Card 模块及 Payment 单请求入口，准确取代下表 Commerce 提交编排与 Channel 初始化部分；当前方案见[Payment Card 合同](../payment-自建CardVault/api-contract.md)。去掉前端版本、文档链接和真实点击确认等未受影响结论保留。

2026-09-12 用户提出五项复核：去掉 expectedOrderVersion、legal 直接返回文档链接、点击支付时确认三份文档、Card 初始化是否必要、消费者动作恢复是否需要独立接口。随后“可以，设计接口字段”确认了四个基础操作的简化方向；本文件保留理由，最新字段以 [HTTP 合同](./customer-api-contract.md) 为准，前版八操作已被取代。

## 1. 收敛建议

| 问题 | 建议 | 保留的业务责任 |
|---|---|---|
| Customer 携带 expectedOrderVersion | 去掉 Customer 请求/展示中的该并发字段 | 内部数据库 version/CAS、Order 锁和命令幂等保留。资料冻结前以最后一次成功提交为准，准备开始后禁止修改；支付精确绑定当前展示的不可变 consumerContractId，不能默默接受另一份金额/合同 |
| 独立法律文档 GET | 从 Customer 业务接口清单删除，legal.documents 返回 documentType、title、url、urlExpiresAt；内部文档/文件 ID 和 Hash 不再传给浏览器 | 链接指向冻结文件版本，不能指向会更新的“最新条款”；订单专属/受限文件走受控短期链接。文件基础设施读取能力仍存在，不等于消失。链接刷新只更新访问材料，不改冻结文件 |
| 独立 acceptances POST | 合并进 POST payments | 用户真实点击具有明确条款说明的支付按钮时，支付请求声明接受所展示的 consumerContractId。服务端先在同一本地事务保存三条原有 Acceptance、同 Session 网络证据及必要提交恢复状态，提交后调用 Payment；重试不覆盖原接受时间与证据。不需要三次 HTTP，也不因合并接口删除三条数据库记录 |
| Card 初始化 | 初始化能力按真实 Channel 保留；独立 payment-contexts 外部操作暂不预设为必需 | 固定 Card 时可在准备完成后的命令路径初始化并返回 cardSetup；不能由普通 GET 每次重建 Channel 会话。采集会话过期、重新选择渠道、刷新恢复的真实生命周期需明确后，再决定独立窄初始化接口是否必要 |
| 消费者动作 resume | 不预建通用恢复命令；先用支付结果的 nextAction 和 Channel SDK 处理正常 3DS | 刷新后的 GET 可返回同一次动作已有的可复用指令；读取不得重新扣款或重建挑战。Channel 需要重新签发材料或执行继续命令时才设计窄 POST；不支持恢复时保持原支付待确认直至权威终态，不能自动另付 |

基础业务清单已收敛为 GET Checkout、PUT Consumer、POST Preparation、POST Payments 共 4 个；Card 初始化和动作恢复的窄辅助操作仅在真实 Channel 证明必要后另评审，现阶段不承诺四个接口覆盖所有渠道。

## 2. 点击支付与三份文档

合并 HTTP 不要求默认勾选复选框。建议在支付按钮旁清晰展示三份可访问文档及说明：“点击支付，即表示你同意《消费者条款》《退款政策》，并确认已获告知《隐私声明》。”以实际点击记录接受/知悉，不在页面加载时预先创建证据。三份文件仍分别可追溯，PRIVACY_NOTICE 的知悉不自动变成对任意个人信息处理的同意。

这是已认可的点击确认方向，不是各法域合同效力的结论。涉及需要独立隐私同意的处理时不得以预勾选或笼统接受条款替代；[ICO 对 UK GDPR 的指导](https://ico.org.uk/for-organisations/uk-gdpr-guidance-and-resources/lawful-basis/consent/how-should-we-obtain-record-and-manage-consent/)明确区分隐私告知与请求同意，并否定预勾选作为同意。本轮设计接口字段，不修改此前 Pencil 中的复选框图形。

## 3. Channel 核对证据

- [NI Hosted Session SDK](https://docs.ngenius-payments.com/reference/hosted-session-sdk)：后端先申请 Session，浏览器嵌入托管字段并 Tokenize；因此 Card 本身也可能有初始化过程，与是否支持 Wallet 无关。
- [NI EMV 3DS WebSDK](https://docs.ngenius-payments.com/reference/alternative-emv-3ds-workflow-websdk-2)：SDK 可承担 Card 的 3DS 交互；该资料不证明所有中断都能无条件恢复，也不证明必须暴露通用 resume 接口。

以上为当时的公开能力核对记录。用户随后已明确选择 NI，最新的详细指南/SDK 差异与字段见 [NI 接入补充](./ni-provider-contract.md)；特别是所选 Web SDK 流程由浏览器生成 Session，不再把概览中的后台申请作为必经步骤。尚未真实对接或执行沙箱/生产调用。
