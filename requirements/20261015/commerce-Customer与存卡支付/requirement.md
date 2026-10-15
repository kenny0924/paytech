# Customer 与存卡支付需求

> 状态：阶段 1 已确认（2026-10-15）；技术方案见 [technique.md](./technique.md)。确认记录见 [delivery.md](./delivery.md)。

## 1. 背景与来源

用户目标（2026-10-15）：

1. 用户（消费者）可以存卡；
2. 有一种支付方式可以用已存卡支付，不必每次输入卡信息；
3. 将来可以向外部商户暴露卡 Token 及前六后四、卡类型、卡组等基础要素，定价也会用到这些要素。

存卡需要一个跨订单稳定的消费者身份。讨论中确认：消费者信息目前在 Commerce，但只有订单级的 `t_consumer_context`，没有跨订单的 Customer；因此先设计 Customer，再在其上挂已存卡。设计参考了 Stripe 的 Customer 与已存支付方式模型（见 §8）。

依赖的已完成工作：[Payment Card 加密存储](../payment-Card加密存储/requirement.md) 已把卡分为“卡（按卡号唯一）/ 卡凭证（有效期与持卡人）/ 卡交易工具”三层，并在需求中预留了已存卡与凭证验证状态。

### 现状（证据）

| 事实 | 证据 |
|---|---|
| Commerce 只有订单级消费者上下文：每单至多一条，含可选的 `consumer_reference`（Client 命名空间内不透明、非 PII）、Email/Phone 稳定 Vault Token、姓名与账单地址 Vault Token 及派生维度 | `commerce-service/docs/database/sql/commerce-schema.sql` `t_consumer_context`；[Commerce 数据库](../../../agent-docs/database/COMMERCE_DATABASE.md) §5.2 |
| 建单接口 `POST /api/v1/checkouts` 目前不接收 `consumer_reference` | `commerce-service/api/openapi/client-v1.yaml` |
| Commerce Client API V1 已冻结路由集合 | [Client API V1 决定](../../../agent-docs/decisions/2026-09-05-commerce-client-api-v1.md) |
| 结账页提交支付时卡原值经 Commerce 转交 Payment 申请交易工具，Commerce 不落卡值 | `CustomerPaymentApi.submitPayment` |
| Payment 卡按卡号唯一，凭证尚无验证状态，没有已存卡 | [Card 加密存储技术方案第 3 版](../payment-Card加密存储/technique.md) |

## 2. 用户决定（2026-10-15）

| 编号 | 决定 |
|---|---|
| D-01 | Customer 由 Commerce 拥有，按（`client_id`, `consumer_reference`）唯一；每个 Client 一套，不做跨 Client 的平台级消费者 |
| D-02 | 两种创建方式都做：Client 主动创建；建单时带 `consumer_reference` 自动“有则复用、无则创建”。访客订单（不带引用）不创建 Customer |
| D-03 | Customer 的身份只认 Client 服务端传入的 `consumer_reference`；消费者在页面填写的 Email、电话、姓名、地址未经验证，不参与身份判断，不按 Email 合并或查找 Customer |
| D-04 | Client 责任：`consumer_reference` 只能用于其系统中已认证的用户，访客不得传；写入接口合同 |
| D-05 | 用已存卡支付时，每次要求消费者重新输入 CVV |
| D-06 | 存卡能力按 Client 开通；未开通的 Client 可以有 Customer，但不出现存卡与已存卡支付 |
| D-07 | Customer 首期只保存身份与状态，不保存联系资料；订单继续保存当次消费者快照。结账资料回写 Customer 放到后续 |
| D-08 | 存卡同意不作为正式法律接受记录；只在已存卡上记录同意时间与来源订单（Q-01） |
| D-09 | 删除参考 Stripe：删除不可撤销，被删 Customer 保留历史但不能再使用；同一 `consumer_reference` 再次建单或创建时新建一个 Customer，不恢复旧的（Q-04） |
| D-10 | 首期结账页与对外接口不展示卡组，只展示后四位与有效期；卡组、卡类型待 BIN 库需求（Q-05） |
| D-11 | 对外掩码统一“前 6 后 4”；开通配置放 CCT Client 配置（无合适位置时首期放 Commerce 本地）；渠道存卡交易标识先核实，不支持则按普通带 CVV 卡交易发送；本会话出需求与方案，实现按系统拆分（Q-02、Q-03、Q-06、Q-07） |

## 3. 使用者与目标

| 使用者 | 目标 |
|---|---|
| Client 服务端 | 创建、查询、删除自己的 Customer；建单时关联 Customer；查询、删除某个 Customer 的已存卡 |
| 消费者（结账页） | 首次支付时选择“保存卡”；之后在同一 Client 结账时看到自己的已存卡，选卡并输入 CVV 完成支付 |
| Payment | 授权成功后把本次使用的卡凭证标记为已验证；按消费者同意生成已存卡；用已存卡生成交易工具 |
| 运营 | 能按 Client 开通或关闭存卡 |

## 4. 本期范围

### 4.1 Customer（Commerce）

1. 新增 Customer：`client_id`、`consumer_reference`、状态（有效 / 已删除）、创建与更新时间；同一 Client 下同一 `consumer_reference` 同时最多一个有效 Customer。
2. Client API：
   - 创建 Customer（引用已存在时返回原 Customer，不报错）；
   - 按 Customer ID 或 `consumer_reference` 查询；
   - 删除 Customer：不可撤销；标记为已删除并保留历史，同时让其全部已存卡失效。按 ID 查询已删除的 Customer 返回已删除状态；同一 `consumer_reference` 之后再创建或建单时新建一个 Customer（D-09）。
3. 建单 `POST /api/v1/checkouts` 增加可选的 `consumerReference`：带上时按 D-02 关联或创建 Customer，订单记录 `customer_id`；不带时与现状一致。
4. `consumer_reference` 仍遵守现有约束：不透明、非 PII，拒绝 Email、电话、证件号等原值；不进入普通日志和 MQ。

### 4.2 已存卡（Payment Card）

1. 新增已存卡：属于一个 Customer（Payment 只记 Commerce 的 `customer_id` 与 `client_id`，不保存 `consumer_reference`），指向一张卡（卡号唯一的那层）和当前使用的已验证卡凭证；状态（有效 / 已删除）；消费者同意时间与来源订单（D-08）。
2. 同一 Customer 同一张卡只有一条有效的已存卡；同卡用新的有效期再次保存时，更新为新的已验证凭证。
3. 对外卡 Token：按 Client 生成、不透明，不同 Client 下同一张卡的 Token 不同；不暴露内部卡 ID 与指纹。
4. 卡凭证增加验证状态：该凭证的授权（或支付）成功后标记为已验证。只有已验证的凭证能用于存卡。
5. 对外展示要素：卡 Token、掩码卡号（前 6 后 4）、有效期；首期不展示卡组（D-10）。

### 4.3 结账流程（Commerce 结账页 + Payment）

1. **首次支付并存卡：** Client 已开通存卡、订单关联了 Customer 时，结账页显示“保存卡以便下次使用”（默认不勾选）。消费者勾选并支付成功后，生成已存卡。支付失败不存卡。
2. **用已存卡支付：** 订单关联的 Customer 有有效的已存卡时，结账页列出这些卡（后四位、有效期），消费者选一张并输入 CVV 后支付。Payment 用已存卡对应的卡与已验证凭证生成交易工具，CVV 按现有方式一次性暂存。
3. 已存卡过期（凭证有效期已过）时不出现在可选列表中。
4. 消费者在结账页不能删除已存卡（见“不做”）。

### 4.4 Client 管理已存卡

- 查询某个 Customer 的已存卡列表（Token、掩码卡号、有效期、创建时间）。
- 删除某张已存卡（标记为已删除，之后不能再用于支付）。

### 4.5 开通

- 按 Client 开通 / 关闭存卡能力；关闭后不再显示“保存卡”与已存卡列表，已存卡保留但不可使用。配置放 CCT Client 配置，无合适位置时首期放 Commerce 本地（D-11）。

## 5. 明确不做

| 项 | 说明 |
|---|---|
| 商户在消费者不在场时扣款（MIT）、订阅、分期 | 只做消费者在场时用已存卡支付（CIT）；MIT 需要渠道网络交易 ID 与另行的授权协议 |
| 免 CVV 的已存卡支付 | D-05 |
| 跨 Client 的平台级消费者与卡复用 | D-01 |
| Customer 联系资料、结账资料回写 Customer、结账预填 | D-07 |
| 消费者自助管理已存卡（类似 Stripe Customer Portal） | 后续 |
| 不经支付单独绑卡（类似 SetupIntent / 0 元验卡） | 后续 |
| BIN 库（卡组、卡类型、发卡国）、卡组展示与定价使用卡要素 | 另行需求；首期不展示卡组（D-10） |
| 网络 Token、Account Updater（卡换发自动更新） | 后续 |
| 3DS 接入 | 不在本需求；用已存卡支付与手输卡走同一风控与认证路径 |

## 6. 验收标准（草案）

### 功能

| 编号 | 验收标准 |
|---|---|
| AC-01 | Client 用新的 `consumer_reference` 创建 Customer 成功；同一引用再次创建返回同一 Customer；不同 Client 的相同引用是不同 Customer |
| AC-02 | 建单带 `consumerReference`：引用不存在时新建 Customer 并关联订单，已存在时复用；不带时不创建 Customer，行为与现状一致 |
| AC-03 | `consumer_reference` 为 Email、电话等明显个人原值时拒绝；日志、MQ 中不出现 `consumer_reference` |
| AC-04 | Client 只能查询、删除自己的 Customer 与已存卡；跨 Client 访问返回不存在 |
| AC-05 | 已开通 Client、订单有 Customer、消费者勾选保存：支付成功后生成一条已存卡，指向已验证凭证，记录同意时间与来源订单；未勾选、支付失败、未开通或访客订单都不生成 |
| AC-06 | 授权或支付成功后，本次交易工具使用的卡凭证变为已验证；失败不变 |
| AC-07 | 同一 Customer 用同一张卡再次勾选保存：不新增已存卡；有效期变化时更新为新的已验证凭证；对外 Token 不变 |
| AC-08 | 同一张卡在两个 Client 下保存，得到两个不同的对外 Token |
| AC-09 | 结账页对有已存卡的 Customer 列出有效且未过期的卡；选卡并输入 CVV 支付成功；未输入 CVV 不能提交 |
| AC-10 | 用已存卡支付时，渠道收到的卡号、有效期、持卡人与保存时一致，CVV 为本次输入；CVV 只能领取一次 |
| AC-11 | Client 删除已存卡后，该卡不再出现在结账页，用其 Token 支付被拒绝 |
| AC-12 | Client 删除 Customer 后，其全部已存卡失效；按 ID 查询返回已删除状态；同一 `consumer_reference` 再建单得到新的 Customer，且没有已存卡 |
| AC-13 | Client 关闭存卡能力后，结账页不显示保存选项与已存卡列表；重新开通后已存卡恢复可用 |
| AC-14 | 对外接口和结账页只返回卡 Token、掩码卡号（前 6 后 4）、有效期，不返回卡组、内部卡 ID、指纹或 8 位 BIN |

### 视觉/交互（结账页）

| 编号 | 验收标准 |
|---|---|
| AC-15 | 可存卡时，卡信息下方显示“保存卡以便下次使用”复选框，默认不勾选，附简短说明 |
| AC-16 | 有已存卡时，支付方式区域先展示已存卡列表（单选）及“使用新卡”选项；选中已存卡时只显示 CVV 输入框 |
| AC-17 | 已存卡支付失败时，提示错误并允许重输 CVV 或改用新卡 |

## 7. 问题与决定

| 编号 | 问题 | 决定（2026-10-15） |
|---|---|---|
| Q-01 | 存卡同意要不要作为法律接受记录 | 不需要；只记录同意时间与来源订单（D-08） |
| Q-02 | 对外掩码 | 前 6 后 4（D-11） |
| Q-03 | 存卡开通配置放在哪 | CCT Client 配置；无合适位置时首期放 Commerce 本地，由运营后台维护（D-11） |
| Q-04 | 删除的 Customer，同一引用再次使用 | 参考 Stripe，删除不可撤销；新建 Customer，不恢复旧的（D-09） |
| Q-05 | 结账页卡组标识来源 | 前缀规则不准确（本地卡组如 mada、联名卡、号段重叠）；首期不展示卡组，待 BIN 库（D-10） |
| Q-06 | 渠道是否支持存卡交易标识 | 阶段 2 核实 Platform 标准 API 与 PayerMax；不支持时首期按普通带 CVV 卡交易发送，并记录影响（D-11） |
| Q-07 | 由谁实现 | 本会话负责需求与技术方案；实现按系统拆分，由用户指定会话；Client API 新增路由需更新 V1 冻结决定与 Gateway 路由注册（D-11） |

假设：

- 结账页沿用现有 Hosted Checkout 与 Checkout Public Token 的安全机制；
- 已存卡支付与手输卡支付走相同的 Risk 评估与认证要求；
- 卡组展示之外，本期不向 Client 暴露 BIN。

## 8. 参考

- Stripe Customer 与 Checkout：Customer 为商户维度，按需创建；访客支付不创建；Customer 资料可变、交易保存快照；存卡需显式选择（`setup_future_usage`、`saved_payment_method_options`），并以 `allow_redisplay` 控制是否再次展示。见 [Create a Checkout Session](https://docs.stripe.com/api/checkout/sessions/create)、[Save payment details during payment](https://docs.stripe.com/payments/checkout/save-during-payment)、[Use saved payment methods](https://docs.stripe.com/checkout/custom-checkout/use-saved-payment-methods)。
- 差异：Stripe 在未传 Customer 时由 Checkout 现场新建 Customer（不去重，需商户自存 ID）；本需求只按 Client 传入的 `consumer_reference` 查找或创建，不做现场新建。
