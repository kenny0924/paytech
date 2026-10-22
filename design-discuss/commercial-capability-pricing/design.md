# commercial 开通与定价模型调整（开通项 + 接受条件 + 标准价目模板）

> 状态：**已确认**（2026-10-20，Q1–Q7 按建议定稿；决定 [2026-10-20 开通项与接受条件、标准价目模板](../../agent-docs/decisions/2026-10-20-commercial-capability-conditions-and-price-templates.md)）；配图 `commercial-capability-pricing.pen` 五张画板（问题与结论、目标模型、一份合同的例子、跨系统执行、模型改动 · 迁移 · 待定）；定稿后登记决定记录，修订 [2026-10-19 六层模型决定](../../agent-docs/decisions/2026-10-19-commercial-six-layer-product-pricing.md) 的 L1 / L3 / L4。原六层设计见 [../commercial/design.md](../commercial/design.md)。

## 1. 为什么要改

讨论起点是合同页「产品与开通」的交互，往下追发现是模型问题：

| # | 问题 | 现状证据 |
|---|---|---|
| P1 | 开通项把「支付方式」和「卡组织」混成一个代码：CARD_VISA / CARD_MC 是「CARD + 卡组织」，APPLE_PAY 却没有卡组织；Apple Pay 里的 Visa、Amex 无法区分开通与定价 | `t_product_capability`：`payment_method_code` + 单个 `dimension_code / dimension_value` |
| P2 | 限制粒度不够：无法表达「只收 Visa 借记卡」；合同约束只有 CURRENCY / CONSUMER_COUNTRY / REQUIRE_3DS 三种 | `CapabilityLimitEntry` 只允许三种 `limitCode` |
| P3 | 价格与开通只单向校验：已开通必须有价（激活校验 3），但没开通也能定价并激活；某 OE 没有任何开通项时币种「不限制」 | `TermVersionActivationValidator.checkPriceReferences / currencyAllowed` |
| P4 | 每份合同从零维护全套价格，没有平台标准价，无法判断折扣幅度 | 只有合同级 `CLIENT_PRICING` 方案 |
| P5 | 价格变更只有内部复核，没有客户签署 | 条款版本状态 DRAFT → PENDING_APPROVAL → APPROVED → SCHEDULED → ACTIVE |
| P6 | 有计费事件却没有对应开通项：TOKEN_STORED（存卡）可定价，但没有「存卡」开通项 | `t_product_fee_event` 与 `t_product_capability` |
| P7 | 开通项目录只读、随发版维护，新增一个支付方式要发版 | 产品目录页「只读 · 随产品发版维护」 |

## 2. 已定结论（讨论中用户确认）

| # | 结论 |
|---|---|
| D1 | **开通项的开通单位是支付方式**（CARD、APPLE_PAY、GOOGLE_PAY、钱包、分期……），不再按卡组织拆；卡组织等降为开通项上的条件。 |
| D2 | **开通项上挂「接受条件」**，复用定价画像的维度词汇与写法：同维度多值 OR、不同维度 AND、未写的维度不限；「要求 3DS」保留为开关（是要求不是筛选）。 |
| D3 | **卡层面的接受范围是合同约定，归商务**；风控规则是另一套，互不相关，不做分工划分。 |
| D4 | **价目跟着开通走**：Client 合同的价格只能落在已开通的开通项范围内；没开通的产品 / 支付方式不应有价格。 |
| D5 | **标准价目是模板**：平台维护、版本化；合同价格「基于模板生成」后成为合同自己的副本，可自由调整；运行时取价只看合同价格。 |
| D6 | **标准价调整不影响已签合同**；合同价格的**任何变更都需客户重新签署价格协议**。 |
| D7 | **低于模板标【破标】**；本期只标出，不增加审批层级。 |
| D8 | 标准价目按 OE 建（如「标准价 · AE001」），允许多套命名模板（标准、优惠、行业套餐），生成时选择一套。 |
| D9 | 合同价格记录来源模板与版本，用于破标对比与「标准价已更新」提示；生成后与模板互不影响。 |
| D10 | **开通项目录可由运营动态维护**（新增 / 停用 / 归属产品 / 可配维度）；支付方式代码必须来自 Payment 的支付方式目录。 |
| D11 | **维度种类由开发登记**（每个维度声明事实来源与判定时点），运营在清单内组合与配值；开通条件与定价条件共用同一份维度清单。 |
| D12 | 开通项种类增加「功能」（存卡、周期扣款、DCC……），让每个计费事件都能挂到开通项或不需开通的产品上。 |

## 3. 目标模型

```
产品目录（运营维护）
  产品 ──┬── 开通项目录：支付方式 / 功能 / 出款通道 / 外汇 / 争议 / 周期服务
         │        └─ 可配维度（来自维度清单）
         └── 计费事件（按条款类型登记，挂到开通项种类或「无需开通」）

标准价目（平台模板，按 OE，多套，版本化，独立权限复核）
         │ 生成（只取合同已开通的开通项 × 币种）
         ▼
合同 ── 开通项（支付方式 + 接受条件 + 3DS 开关 + 保证金 / 生效）
     └─ 合同价格（模板副本 + 调整；每行记来源）── 破标标记
          └─ 条款版本：内部复核 → 客户签署价格协议 → 排期 → 生效
```

### 3.1 开通项目录

| 种类 | 例子 | 接受条件 | 典型计费事件 |
|---|---|---|---|
| 支付方式 PAYMENT_METHOD | CARD、APPLE_PAY、GOOGLE_PAY、STC_PAY、BNPL_TABBY | 有（卡组织、借贷记、发卡国、币种、消费者国家…） | AUTH、CAPTURE、SALE、REFUND、CHARGEBACK |
| 功能 FEATURE（新） | 存卡、周期扣款、DCC、Payment Link | 一般无，可带设置项 | TOKEN_STORED、DCC_APPLIED |
| 出款通道 PAYOUT_RAIL | PAYOUT_LOCAL、PAYOUT_SWIFT | 目的国、币种 | PAYOUT_SUBMITTED、PAYOUT_RETURNED |
| 外汇 FX / 争议 DISPUTE / 周期服务 | FX_SPOT、DISPUTE_HANDLING、ANALYTICS_SUBSCRIPTION | 币对 / 无 | FX_DEAL / CHARGEBACK / 周期事件 |

- 运营可新增、停用（不删除）、改名称与归属产品、维护「该开通项可配哪些维度」。
- 支付方式代码从 Payment 的支付方式目录选择（目前是规范约定的开放代码，没有可查询接口，见 Q4）；新增时提示技术可用性（是否有 MID 支持，来自 channel-connector 的 MID 能力）。
- 基础产品（税票、平台）无需开通，其计费事件（MONTHLY_CYCLE、SETUP、KYB_REVIEW）不挂开通项。

### 3.2 维度清单（开发登记）

| 维度 | 事实来源 | 判定时点 | 开通条件 | 定价条件 |
|---|---|---|---|---|
| CURRENCY | 订单 | 下单 / Checkout 解析 | ✓（必填，至少一个） | ✓ |
| CONSUMER_COUNTRY | 订单 | 下单 / Checkout 解析 | ✓ | ✓ |
| CARD_BRAND | BIN / 钱包令牌 | 输卡号 / 授权前 | ✓ | ✓ |
| FUNDING_TYPE | BIN | 输卡号 / 授权前 | ✓ | ✓ |
| ISSUER_COUNTRY / ISSUER_REGION | BIN | 输卡号 / 授权前 | ✓ | ✓ |
| CARD_LEVEL / CARD_PRODUCT | BIN | 授权前 | ✓ | ✓ |
| THREE_DS_RESULT | 3DS 认证 | 认证后 | ✗（只用于定价） | ✓ |
| TRANSACTION_TYPE | 交易 | 授权 | ✗ | ✓ |
| PAYOUT_RAIL / DESTINATION_COUNTRY | 出款指令 | 出款创建 | ✓（出款通道） | ✓ |
| CURRENCY_PAIR | 换汇 | 成交 | ✓（外汇） | ✓ |

维度取值（新增卡组织 MADA、新国家）由运营或目录维护；新增**维度种类**需开发登记事实来源与判定点。

### 3.3 合同开通项与接受条件

- 开通项 = OE × 支付方式（唯一），带状态、生效区间、滚动保证金、3DS 开关与一组接受条件。
- 例：只收 Visa 借记卡 = `CARD` + `CARD_BRAND=VISA` + `FUNDING_TYPE=DEBIT`；Apple Pay 只收 Visa / MC = `APPLE_PAY` + `CARD_BRAND=VISA,MASTERCARD`。
- 本期每个开通项一组条件；「Visa 借记或任意 MC」这类跨维度 OR 需要多组条件，表结构预留，本期不做（Q3）。

### 3.4 标准价目（模板）

- 平台拥有的价格方案：复用「方案 → 版本 → 价格 / 组件 / 阶梯」结构，Owner 类型为平台模板，按 OE；同一 OE 可有多套命名模板。
- 版本化、走复核；复核权限与合同价格分开（「标准价目维护 / 复核」）。
- 模板覆盖平台所有可售支付方式与功能，不受任何合同开通约束；它不参与运行时取价。

### 3.5 合同价格的生成与维护

- **生成**：新建条款版本时选「基于模板（某套 · 当前版本）」或「复制上一版本」；基于模板时只生成合同已开通的开通项 × 币种对应的价格行，并按开通项接受条件裁剪（例：只收 Visa 借记卡时，不生成 Amex 行）。
- **来源**：每行价格记录来源模板价格与模板版本；手工新增的行无来源。
- **维护**：可自由增删改；新开通一项后，在新版本里「从模板补齐该开通项」。
- **提示**：模板出新版本后，引用旧版本的合同显示「标准价已更新」，不自动改价。

### 3.6 校验规则（Client 条款）

| 规则 | 内容 | 时点 |
|---|---|---|
| 完整性 | 每个 ENABLED 开通项 × 其币种（及有条件的维度取值）都能命中价格；无条件维度需有兜底画像 | 激活拦截（已有，按新条件空间改写） |
| 相关性 | 每条价格能对应到一个已开通的开通项：画像支付方式 = 开通项支付方式、币种在范围内、画像条件不超出接受条件；功能类计费事件需对应功能开通项 | 保存提示，激活拦截 |
| 零开通 | 某 OE 无相关开通项时不再「不限制」，而是不允许有该 OE 的 Client 价格 | 激活拦截 |
| 破标 | 同一画像 × 币种 × 计费事件 × 组件与来源模板比较：费率、固定费、最低收费、最高收费低于模板，或阶梯任一档低于模板，标【破标】；无来源或模板无对应项标「无标准可比」 | 编辑器、版本详情、复核队列展示；不拦截 |

Partner 条款（分润、平台服务费、买入价）作用在 Client 交易上，自身无开通项，不受相关性约束。

### 3.7 客户签署价格协议

- 条款版本状态：DRAFT → PENDING_APPROVAL（内部复核）→ APPROVED → **CLIENT_SIGNED（新）** → SCHEDULED → ACTIVE。
- 每个 Client 条款版本须有一份价格附件（文件 ID + SHA-256）和 Client 一方的签署记录（方式、时刻、编号、证据），复用合同签署的登记方式；未签署不能排期。
- 全部价格变更都要签（含降价）。
- 存量已生效版本视为已签（迁移时补「历史版本，免签」标记）。

### 3.8 开通到交易的时序

开通 → 生成 / 补齐价格 → 内部复核 → 客户签署 → 排期生效。开通项在其价格生效前为「已开通 · 待定价」，Checkout 解析不输出它（不可交易），合同进度条与下一步卡片提示。停用开通项不动历史价格；下一个版本激活时，挂不上任何开通项的价格被相关性校验拦下。

## 4. 跨系统执行

商务只负责配置，看不到交易；接受条件要由执行交易的系统按冻结下来的条件判断，否则会出现「能成交、算不出费用」的交易（价格只覆盖接受范围）。

```
commercial：开通项 + 接受条件
  ↓ Checkout 解析输出（新增：本单可用支付方式、接受条件、3DS 要求）
Commerce：支付准备时冻结
  ↓
收银台（acquiring）：展示支持的卡组织；输卡号时按 BIN 提示「该卡暂不支持」；已存卡逐张判断、置灰
  ↓
Payment：授权前按 BIN 事实对照冻结条件，不符拒绝（真正拦截）
  ↑
Card：BIN 查询（新能力）；存卡时保存卡组织 / 借贷记 / 发卡国
```

- 前端提示只为体验，拦截在 Payment；消费者文案只说「该卡暂不支持」，不暴露合同信息。
- 已存卡不因合同变化被改状态或删除，每次收银台按本单 OE 与冻结条件实时判断。
- 支付方式代码归 Payment 目录，渠道路由使用同一套代码；商务只能从目录选择。

## 5. 模型改动清单（草案）

| 表 / 对象 | 改动 |
|---|---|
| `t_product_capability` | `capability_kind` 增 `FEATURE`；`dimension_code / dimension_value` 废弃（目录不再按维度拆项）；新增运营写接口 |
| 新 `t_product_capability_dimension` | 开通项可配维度：`capability_code`、`dimension_code`、是否必填（CURRENCY 必填） |
| `t_agreement_capability` | 增 `require_3ds`；唯一键保持 `agreement_id, entity_no, capability_code` |
| `t_agreement_capability_limit` | 改为接受条件：`dimension_code`（维度清单）+ `condition_value`，同维度多行 OR；原 CURRENCY / CONSUMER_COUNTRY 行迁为条件，REQUIRE_3DS 迁到开关 |
| `t_product_fee_event` | 增「所属开通项种类 / 开通项代码或无需开通」，闭合计费事件 → 开通项 |
| `t_commercial_term_plan` | Owner 增「平台模板」：`owner_type`（CLIENT / LOCAL_PARTNER / PLATFORM_TEMPLATE）、模板名称、适用 OE；模板方案无 `agreement_id` |
| `t_commercial_term_version` | 增 `source_template_version_id`；状态增 `CLIENT_SIGNED`；存量免签标记 |
| `t_commercial_term_price` | 增 `source_template_price_id`（可空） |
| 新 `t_term_version_acceptance` | 价格协议签署：版本、签署方 Party、方式、时刻、编号、附件文件 ID 与 SHA-256、登记人 |
| 维度清单 | `PricingDimension` 扩为维度清单：增「事实来源、判定时点、可用于开通 / 定价」元数据（代码枚举，开发维护） |
| Checkout 解析输出 | 增每个可用开通项的接受条件与 3DS 要求 |

## 6. 页面影响

- **产品目录**：可维护开通项（新增 / 停用 / 归属 / 可配维度），支付方式代码下拉来自 Payment 目录；层级展示沿用已上线版本。
- **标准价目**（新页面）：模板列表、版本、价格编辑（复用组件表格）、复核。
- **合同 · 产品与开通**：按产品分组；新增开通项弹窗按开通项可配维度渲染条件控件（标签输入、开关）。
- **编辑价格**：新建版本「基于模板 / 复制上一版」；价格按开通项分组，挂不上开通项的行标红；破标、无标准可比标记；「从模板补齐」按钮。
- **条款版本**：复核后新增「登记客户签署」步骤（上传价格附件信息、签署记录）；复核队列显示破标数。
- **合同进度**：「定价」步骤拆出「待客户签署」状态；开通项「待定价」在下一步卡片提示。

## 7. 迁移

- 目录：CARD_VISA / CARD_MC / CARD_AMEX / CARD_UNIONPAY → `CARD`（可配维度 CARD_BRAND、FUNDING_TYPE、ISSUER_*、CURRENCY、CONSUMER_COUNTRY）；APPLE_PAY / GOOGLE_PAY 增可配 CARD_BRAND；新增 FEATURE 类「存卡」并挂 TOKEN_STORED。
- 合同开通项：同一合同同一 OE 的 CARD_* 合并为一条 `CARD`，`CARD_BRAND` 取原各项；币种取并集（若原各项币种不同需人工确认）；保证金与生效区间取原项（不同则人工确认）。mor-test 涉及 CL-F1105E 与 DEMO-CL-001 / 002。
- 条款版本：存量 ACTIVE / SCHEDULED 版本标「历史版本，免签」；DRAFT / PENDING_APPROVAL 版本迁移后需走签署。
- 价格：存量价格无来源模板，破标显示「无标准可比」。

## 8. 待定问题

| # | 问题 | 建议 |
|---|---|---|
| Q1 | BIN 查不到属性时，限制性条件放行还是拒绝 | 拒绝（失败关闭） |
| Q2 | Partner 条款（分润、买入价）变更是否也要 Partner 签署 | 是，同一签署机制，签署方为 Local Partner |
| Q3 | 是否支持一个开通项多组条件（跨维度 OR） | 本期不做，表结构预留 |
| Q4 | Payment 支付方式目录的可查询接口由谁提供 | Payment 提供只读目录接口；channel-connector 提供「已支持的支付方式」作为技术可用性提示 |
| Q5 | 标准价目是否也要模板级「最低价（底价）」以外的破标阈值（如 90%） | 本期只比较是否低于模板 |
| Q6 | 停用开通项是否要强制出新价格版本 | 不强制；下一版本激活时相关性校验处理 |
| Q7 | 「已开通 · 待定价」是否需要显式状态字段，还是由「有无生效价格」推导 | 推导，不加状态字段 |

## 9. 分期与相关需求

| 期 | 范围 | 系统 |
|---|---|---|
| 一期 | 开通项改造与目录维护、接受条件、维度清单、标准价目模板、合同价格生成与来源、相关性 / 零开通 / 破标、客户签署、迁移、Checkout 解析输出接受条件；对应页面 | commercial、ops-bff、ops-web、Platform 注册 |
| 二期 | Card BIN 查询与存卡属性、收银台卡组织展示与输卡提示、已存卡置灰、Payment 授权前拦截、Commerce 冻结接受条件 | card、acquiring、payment、commerce |

- [合同页交互优化](../../requirements/20261020/commercial-合同页交互优化/requirement.md)：除「产品与开通」外不受影响，可先行；「产品与开通」与开通弹窗待本设计定稿后按新模型重画。
- 已上线的[价格组件表格维护](../../requirements/20261020/commercial-价格组件表格维护/delivery.md)沿用；一期在其上增加按开通项分组、来源与破标标记。
