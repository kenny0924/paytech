# Customer Checkout 接口设计入口

> 最新方向变更：[自建 Card Vault](../payment-自建CardVault/requirement.md)取代 Provider 托管卡采集方向。原四个基础路径保持，前三个归 Commerce、payments 改由 Payment 接收卡与确认；后者调用 Commerce 内部保存接受后执行，准确字段/查询见该专项接口稿；下文 NI Hosted Session 部分仅保留前序讨论。

基础路径保持四个，payments 改由 Payment 承接；加上 Payment 原提交查询，当前公开操作共五个。请求/响应、字段约束、示例及错误由 [HTTP 合同](./customer-api-contract.md) 统一管理；首个渠道已选 NI，[NI 接入补充](./ni-provider-contract.md)和[前序服务端方案](./implementation-design.md)保留历史证据，当前支付实施顺序以 Payment 专项为准。本页说明流程，不复制另一套 Wire。变更理由见[简化复核](./simplification-review.md)，确认记录见 [delivery.md](./delivery.md)。

## 1. 路径与职责

`B=/customer-gateway-api/v1/checkouts/{checkoutToken}`。Checkout Web → Gateway 按操作进入 Commerce 或 Payment；不新增独立 Checkout BFF。可信 Channel 为 CHECKOUT，与 Platform 人类 CUSTOMER Realm 无关。公开 Gateway 路径映射、Target/Guard 和激活属于后续单独实施。

| 操作 | 主要职责 |
|---|---|
| GET B | Commerce 的订单/资料/最终卖方/法律链接/支付概要，复用于加载、刷新和查询结果 |
| PUT B/consumer | 保存姓名、Email 和账单地址，准备前可更新，准备启动后锁定 |
| POST B/preparation | Commerce：无 Body，冻结 OE/合同/税额/最终金额，创建唯一 Intent，不初始化卡会话 |
| POST B/payments | Payment：一次接收卡与当前合同确认；调用 Commerce 保存三接受后执行原支付 |

不再单独定义法律文档、acceptances、payment-contexts 和通用 resume Customer 操作。Payment 原提交查询提供已有3DS指令；不再单独提供 /card 或 Card 初始化操作，更多辅助接口按渠道证据评审。本期不预建 Wallet/Bank/BNPL 参数。

## 2. 页面调用顺序

1. GET 加载页面；请求过程中显示骨架。需要回填资料时 includeConsumer=true，轮询默认 false。
2. Continue 串行执行 PUT consumer → POST preparation；保存成功但准备超时，恢复原准备，不重复创建 Order。
3. 等待服务端准备完成，展示最终金额、真实法律卖方和三份准确文档链接。自建组件不依赖 Commerce Card 配置；READY 不代表已收卡或已确认协议。
4. 自建组件收集卡输入；用户真实点击带条款说明的 Pay 后，一次 POST payments 向 Payment 提交合同确认和 Card 输入；页面加载和预勾选不创建接受事实。
5. 需要 3DS 时使用原支付返回的 nextAction 执行 SDK。刷新、银行回跳或 SDK 完成后通过 Payment GET 查询原支付、Commerce GET 查询订单权威状态；GET 不提交付款或重新创建挑战。
6. PROCESSING/UNKNOWN/AUTHORIZED 及存在待处理动作时禁止新付；Payment 已成功但 Commerce 尚未 PAID 时显示确认中。只有 Commerce 已收敛为 PAID 才显示最终成功页。

## 3. 核心实现边界

没有 Customer 版本字段；内部锁/CAS/幂等保留。资料准备前采用最后一次成功保存，准备占有订单后禁止改写。固定计税国家、币种、商品与最终上下文不接受浏览器覆盖。账单国家不自动覆盖计税国家，其允许组合由明确的业务校验决定，不以一条新字段正则代替 Owner 规则。

法律只返回精确冻结文件链接；consumerContractId 用于核对本次确认对应的已展示合同，不是版本控制或用户路由选择。三条 Acceptance 与同 Session 网络证据仍各自保存，COM-I10 的三个显式 Acceptance ID 和内部版本不变。

所有外部调用在事务外；本地业务状态和必要 compensation_event 同事务写入，提交后触发，Handler 负责独立入口及可靠恢复。一次性材料没有安全恢复来源时不能仅凭本地事件返回可靠受理。Provider bearer 材料、个人原文不进通用快照或永久回执。

Commerce GET 在事务外读取 Payment 概要，不新增 PaymentAuthorized MQ。已有可安全复用的指令仅由 Payment POST/GET 返回，不重建会话。指令不能恢复时保持原支付收敛责任，不自动新建扣款。

## 4. 当前产物与后续缺口

本次仅修改设计文档及已有架构/需求索引。当前实际实现与部署见 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 和 [Commerce README](../../../commerce-service/README.md)，不能由字段设计推断 Customer API 已上线。

前序 NI Hosted Session configuration/session_id 映射已被自建方向取代。统一支付请求、Commerce 内部确认、卡数据与恢复详见[Payment 专项](../payment-自建CardVault/technique.md)；NI 直接支付/3DS 仍需沙箱核验。Platform 冻结文件能力和 Consumer 引用可空等原准备链缺口保留。前序[影响分析](./interface-impact.md)只在未被专项取代的范围继续适用。本轮不修改页面、应用代码、模型、SQL、Gateway 或 Risk。
