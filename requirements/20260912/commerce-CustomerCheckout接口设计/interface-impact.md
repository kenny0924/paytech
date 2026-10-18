# Customer Checkout 接口影响与实施门槛

> 2026-09-12 当前Card实施路由：[Payment接口字段稿](../payment-自建CardVault/api-contract.md)与[数据/事务稿](../payment-自建CardVault/data-design.md)。用户已确认 Payment 的同一个 payments 请求接收卡与协议确认，调用 Commerce 内部确认后继续执行；公开提交 Owner、Commerce SubmitPaymentProcessor/登记回调、NI setup/Hosted Session 的前序段落均不再是当前方案，其余 Consumer/准备分层证据保留。

> 最新变更：用户要求[自建存卡](../payment-自建CardVault/requirement.md)。本表中 NI 托管采集/Session 绑定分析保留为前序证据，新的 Card Vault、平台 Token 和受控执行边界需专项分析；其他 Commerce/Owner 职责不变。

状态：四个基础操作及[公共字段](./customer-api-contract.md)已确认，首个 Channel 已选 NI。本轮技术补充见 [NI 合同](./ni-provider-contract.md)和[服务端方案](./implementation-design.md)。内部 Owner 合同、数据调整与研发拆分仍是设计建议，未实施业务代码、PDManer、DDL/DML 或路由注册。原八操作影响分析由本文取代。

## 1. 差异与 Owner 能力

| 需求 | 当前证据 | 待落实能力 |
|---|---|---|
| Customer HTTP | 既有 Client 入口、Public Token、Order/Session | 四个 CHECKOUT 操作，独立 Customer DTO/授权；复用统一错误、MDC、HTTP Client、锁、幂等和补偿，不另造中间件 |
| Consumer 保存/回填 | 目标模型有 Email/Profile Vault Token；consumer_reference NOT NULL，但现行 Client/Customer 输入都没有来源 | Vault Protect/Reveal 的 Commerce Data Type、Purpose、allowlist 及安全幂等；消费者引用可空提案见第 2 节 |
| 支付准备 | 基础创建只有 CCT precheck；CCT Context 合同已有，完整真实 Owner 链未验收 | Commerce 可靠准备编排，CCT 最终选择、Risk、Tax、真实法律呈现及唯一 Intent |
| 卖方与 legal 链接 | CCT 返回法律引用；Commerce 有三份 fileId/Hash，Consumer Contract 还要求 rendered_file_id/hash | CCT 准确卖方公示读取；Platform 真实不可变文件、受控访问链接/媒体类型及消费者合同呈现。不能拿模板 ID 或假链接补齐 |
| 点击支付与确认合并 | 三条 Acceptance 和同 Session 网络证据已有模型/逻辑合同 | POST payments 同一本地事务记录三项事实和恢复状态，事务外调用 Payment；COM-I10 继续实时推导，不新增接受汇总表 |
| Card 初始化 | NI SDK 在浏览器生成一次性 Session；现有 Attempt 创建仍需要 Readiness | Payment 在 preparation 提供受限采集配置及固定 Outlet 绑定；不提前创建 Attempt，也不强制先向 NI 发后台 Session 创建请求 |
| 支付与 3DS | Payment 有 Attempt/Action/Customer Action，临时参数不能持久存储 | typed token 消费和可靠受理合同；nextAction 指令；GET 只读取已有可安全复用的材料。按真实需要再评审窄辅助命令 |
| AUTHORIZED/未知/成功延迟 | Commerce 旧投影不足以表示全部当前 Payment 事实 | 查询事务外读取 Payment 权威受限视图；不新增 PaymentAuthorized MQ，GET 不代替成功事件写订单 |

实际实现、验证和部署只见 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 的专项记录及 [Commerce README](../../../paytech/acquiring/README.md)。本稿的源码/模型证据不等于真实支付链已部署。

| 提供方 → 消费方 | 所需内部合同 |
|---|---|
| CCT → Commerce | 已有 POST /checkout-context/v1/resolutions，读取固定 Client/Order/国家/币种/locale/商业时点对应的 entityNo、Affiliation/Scope/Source、Agreements/Term Versions、法律引用；卖方公示缺口需由 CCT 准确提供 |
| Vault → Commerce | 已有 POST /vault/v1/records/protect、POST /vault/v1/records/reveal；Email 与姓名/地址 Profile 分离，全在事务外。核对 V1 和用途配置，不新增业务化 Vault 表 |
| Risk → Commerce/Payment | 订单与 Attempt 的权威风险/资格检查；本期 Risk 不改，缺失或技术不可用不能默认 ALLOW，不造数字 OE 替身 |
| Finance → Commerce | 原 Tax 计算和原结果恢复，返回与固定 Order/Items/国家/币种/OE 一致的快照及总额；费用晚到定价不重算 Consumer 应付金额 |
| Platform → Commerce | 准确冻结文件读取、消费者合同实际呈现、受控文件链接；不能由 CCT 引用凭空推断文件服务已可用 |
| Payment → Commerce | 唯一 Intent 创建、Card 采集能力、支付提交和原受理结果、只读 Checkout View。可读取的临时材料只短暂响应，不写永久回执；Channel 专有字段需具体类型 |
| Commerce → Payment | COM-I10：三个 Acceptance ID、Contract、接受时间、内部 Order Version、expiry；COM-I13：同 Order/Session 下批准的最小 Consumer/网络风险输入，不暴露浏览器 |

以上除标为“已有”的路径外是逻辑能力清单，不宣称已有可调用 Method/Path。内部服务直连使用现行 API JAR 的 @HttpExchange 和公共负载均衡能力。

## 2. 数据建议

| 表/字段 | 当前 | 建议及影响 |
|---|---|---|
| Commerce t_consumer_context.consumer_reference | VARCHAR(128) NOT NULL | 建议允许 null；非空仍为 Client 命名空间内非 PII 引用。同步调整真正消费此字段的内部合同，不自动生成假引用或使用 Email。本建议尚未获得独立模型实施授权 |
| Commerce t_order_payment_projection.last_attempt_status | 说明和映射有旧状态，缺 AUTHORIZED 等 | 对照 Payment 当前枚举收敛白名单/映射，是否写投影取决于实际事件。AUTHORIZED 经当前查询补齐，不为填表新发事件 |

资料采用既有 Order 的内部 CAS，不增加前端版本字段。法律表复用既有三文件及 Consumer Contract/Acceptance；不新增文档类型、接受汇总或第二套 Consumer Session。敏感资料的规范化、幂等指纹与恢复必须匹配实际 Vault 合同，不能把每次随机生成的引用当作稳定请求相等依据。

NI 本轮核对表明，即使 SDK 在浏览器创建 Session，服务端仍需证明此前返回的 Outlet/Account 与最终消费渠道一致。因此提议使用 setupId 表示 Payment 的采集绑定；它不是 NI Session，也不保存一次性 session_id。最小事实为原 Intent/OE、准确渠道作用范围、期限、失效/消费状态及唯一消费 Attempt/并发保护。是否新增表及精确字段需由 Payment 数据方案确定，当前不预建通用 context 表。

`cardSetup.status`、`payment.status=SUBMITTING`、allowedActions 和页面骨架属于响应投影，不直接扩展数据库枚举。现有准备模型与实际环境还需分别核对，以上建议不等于全部迁移量。

## 3. 本次合并后的关键验证点

- Consumer 保存与准备竞争：后一次成功写入语义明确；准备占有订单后拒绝修改，不污染已发送 Risk/Tax 的输入。无外部 expectedOrderVersion。
- preparation 同键恢复原准备，新键不能重选已冻结 OE/金额/合同；动态 Card setup 过期后的新准备只影响采集绑定。
- legal 三链接都对应冻结文件；个性化文件不能变成公开永久链接；链接失效重取不换内容，接受不依赖会变的 URL 字符串。
- 缺失/false 确认、错误合同、部分 Acceptance 历史记录；三条确认和证据原子保存，重试不覆盖原时间。
- 接受事务提交后才调用 Payment；业务 Processor 和补偿 Handler 各自作为独立入口验证。所有外部调用都在数据库事务外。
- 不同 Key 不能消费同一动态 setup 两次；初始化、过期与提交竞争符合真实 Channel 绑定；不能把令牌从一个不兼容 MID 转给另一个。
- 原输入或响应丢失时先证明可靠恢复；没有稳定材料来源不能仅凭本地事件返回 202，也不能保存 bearer 到 JSON。
- Payment UNKNOWN/已授权/动作在途无新付款；Channel 响应丢失、动作完成/过期、不可恢复材料都收敛原支付。浏览器回跳不是成功证据。
- AUTHORIZED 不显示 PAID；成功事件延迟期间关闭 PAY；支付权威不可用时不使用陈旧失败放开重试。Token 到期不扩权。

后续测试按项目真实公开入口、独立 Processor/Handler、本地 MySQL/Redis 与测试侧 HTTP Simulator 规范实施，不在业务 src/main 添加 Mock 成功逻辑。本轮仅文档静态验证，非上述行为测试。

## 4. 后续研发建议

1. 公共字段已认可，首个 Channel 已选 NI；继续完成 NI 绑定及沙箱恢复/3DS 核验、Platform 文件链接表示和具体数据方案，不重复公共字段评审。
2. 明确授权的 Commerce/Payment/其他 Owner 切片及数据变更后，实施四操作和必要内部能力。可以先落 Consumer/准备的明确部分，但不得把缺失真实依赖的全流程标为完成。
3. 绑定 Checkout Web 已认可页面，验证骨架、资料锁定、Pay 确认、SDK 和权威状态展示；Gateway/Nginx/Platform 注册与环境激活按单独批准清单进行。Risk 本期保持不改。

本轮应用代码、模型、DDL/DML、环境 SQL 执行和路由注册均未变更。四个 Method/Path 是目标公开操作，不据此猜测最终数据库注册行数。确认记录及静态证据见 [delivery.md](./delivery.md)。
