# Platform 定位为技术服务商、PayFac 以 Partner 接入与商户对接模式决定

- Decision status: Proposed（2026-10-22—23 用户在讨论中逐条确认 D1—D12，其中 D3、D4 已按第二轮讨论修订，D6、D8 与第 9 节已按 2026-10-23 第三轮风险核对修订，D3、D4、D5 与第 9 节再按第四、五轮评审意见（第 10 节 R15—R21）修订；尚未据此修订 AGENTS 与各主题 Owner，见第 6 节）
- Implementation status: Not started（本记录不改代码、模型或数据）
- Deployment status: Not deployed
- Decision date: 2026-10-23
- 讨论来源：commercial 二期执行链路（开通项接受条件执行）的前置分析——acquiring 尚未接入真实商务解析、商务解析的资格检查依赖 Risk / compliance 接口；用户要求先自上而下厘清商户对接模式与 Platform / Client / Partner 三个角色。
- 修订 / 取代：[commercial 设计第 13 节（商业关系模式）](../../design-discuss/commercial/design.md)的“我们是 PayFac（现状）”一行与 OE 来源 `PLATFORM`；[2026-10-19 commercial 六层产品定价模型](./2026-10-19-commercial-six-layer-product-pricing.md)中平台服务费与 Partner 分润的承担方；AGENTS 中 OE 来源与 `PLATFORM_SERVICE_CHARGE` 双账本规则（待按第 6 节修订后生效）。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 1. 背景

讨论前系统有两条维度：

- **业务模式**（谁是卖方）：MoR / PSP，已落在 `t_agreement.business_mode`。
- **商业关系模式**（谁签约、谁持 MID、钱经过谁、谁定价）：设计稿列出“我们是 PayFac（现状）/ Direct Merchant / ISV·Referral / Client 是 PayFac（分账）/ 白标代运营”，其中“我们是 PayFac”又分平台自有 OE（OE 来源 `PLATFORM`）与合作方提供 OE（来源 `PARTNER`）。代码只实现了“经平台结算 + 平台定价”这一组合。

用户提出：平台在所有场景都是技术提供商并向商户收费；若我们自己要做 PayFac，应作为平台的一个 Partner 接入。本记录据此重新定义三个角色与模式组合。

## 2. 讨论中的问题与结论（便于以后查阅）

| # | 问题 | 结论 |
|---|---|---|
| P1 | Platform 指谁？ | 运营本系统、对外提供支付技术服务的我们自己（技术公司）。Platform Support 是技术系统，不是这个角色；白标多实例时指该实例的运营方 |
| P2 | 是否还需要“平台自有 OE”（R1）与“合作方 OE”（R2）两种？ | 不需要。Platform 只做技术服务商；我们要做 PayFac 时，以另一个法律主体作为 Partner 接入，R1 / R2 合并为“Partner 提供 OE” |
| P3 | 平台的钱怎么收？ | ~~商户直接付~~（第一轮结论，已被 P12 / D3 修订取代）：C1 / C2 由 PayFac 付，C3 由 Client 付；按账单或从服务费预存款扣 |
| P4 | 技术公司与 PayFac 是否同一主体？ | 两个法律主体，各有 Finance Entity 与账本（D2） |
| P5 | PayFac 需要向收单行申请吗？ | 需要：赞助收单行的 PayFac 协议与尽调、卡组织 PayFac 登记、子商户 KYC / 监控 / 拒付连带责任；多数地区另需本地支付牌照。也可通过 PSP 的子商户或聚合计划，本质仍在对方资质下运作 |
| P6 | 只有普通商户身份、申请不到 PayFac 的主体，能做 Partner 并做 MoR 吗？ | 能做 MoR（转售：以自己名义向消费者销售，商户是供应商；需收单行认可其转售模式与 MCC，并真正承担卖方责任）；不能做 PSP（无资质替他人收款属卡组织禁止的代收）。由此产生 D6 |
| P7 | 一个主体同时是 OE 与 Partner（甚至 Client）合规吗？ | 合规且常见；每个角色单独做资格认证（compliance `role_qualification` 按角色一行）；同时是 Client 时需利益冲突控制（不能给自己定价、审批）。见 D7 |
| P8 | Direct Merchant 想转为 Partner 走 MoR，开通 Partner 即可吗？ | 系统内是：开通 Partner 角色与资格、签 Partner 合同、其 OE 登记为 `PARTNER` 来源，原 Direct Merchant 关系可并存。外部前提：其商户号原为自营申请，转售模式需通知收单行，可能改 MCC 或另开 MID。见 D9 |
| P9 | C2（PSP）里 MID 不应归 Client 吗？ | PayFac 模式下 MID 归 PayFac（主商户），Client 只有随交易报送卡组织的子商户 ID，与收单行无直接合同。Client 自有 MID 即 C3；由 Partner 代办入网但 MID 归 Client 视为 C3 叠加 C4 |
| P10 | Partner 使用平台是否也付费？ | 暂不收费（D4） |
| P11 | 推荐方分成谁付？ | Platform 付，只按技术服务费计算（D8） |
| P12 | 费用如何在 PayFac 与 Platform 之间分？ | 用户原则：**能向 Partner 收就不向 Client 收**（Client 太多收不过来）。C1 / C2 下 Client 的全部费用由 PayFac 定价并在结算时扣，Platform 向 PayFac 收技术服务费；只有 C3 由 Platform 直接向 Client 收。原第一轮按费用逐项划分的问题因此在 C1 / C2 下消失，转为“Platform 向 PayFac 收什么”。见 D3（修订）、D10 |
| P13 | MoR 商户需要与 Platform 签合同吗？还需要签技术服务合同吗？ | C1 / C2 不需要商务合同或技术服务合同，只与 PayFac 签；Client 接受 Platform 使用条款（勾选、无费用），也可把使用与数据处理条款写进 PayFac 商户合同，但 Platform 直接面向 Client 的账号与 API 仍保留自己的使用条款。C3 由 Client 与 Platform 签收费的技术服务合同。见 D11 |
| P14 | 技术服务费的形态？ | 未定，但必须支持多种：每笔百分比 + 固定、按量阶梯、月 / 年固定费与开户费、最低承诺、按功能或用量（存卡令牌、3DS、KYB 等）。见 D12 |

## 3. 决定

| # | 决定 |
|---|---|
| D1 | **Platform 只做技术服务商**：所有模式下不持 MID、不当卖方、不经手消费者资金，只收技术服务费 |
| D2 | **我们自己的 PayFac 以 Partner 接入**，是独立于技术公司的另一法律主体，有自己的 Finance Entity 与账本；现有平台自有 OE（如 AE001，来源 `PLATFORM`）迁到该 Partner 名下。Partner 增加“关联关系”属性（`AFFILIATED` 关联主体 / `EXTERNAL` 外部），我们的 PayFac 为 AFFILIATED：其出款审批、争议处理、商户价目维护由平台运营人员在运营控制台代为操作，Partner 门户只读的现有规则对它不构成限制。**外部 PayFac 需要自行操作出款、争议与价目**，与 AGENTS“Partner 只读”规则冲突，列为后续需求（第 10 节 R3），本记录不改该规则 |
| D3（修订） | **技术服务费的付款方**：C1 / C2（及其叠加层）由 **PayFac Partner** 付给 Platform；只有 C3 由 **Client** 付。付款方式均为按账单（平台主体应收）或从付款方放在平台的服务费预存款扣（平台主体的预付款负债；避免使用“备付金”一词——该词在支付监管语境中专指支付机构的客户备付金）。预存款只能抵扣平台服务费，不得用于出款或结算，与 PayFac 的保证金（`CLIENT_SECURITY_DEPOSIT`）分开；合规性待法务确认（Q1）。第一轮“Client 在所有模式下付 Platform”的写法作废。**债务人与账簿分开**：技术费的合同债务人是 Partner 的法律主体；资金与账簿所属主体是收单 OE（见 D4、9.2）。**本期显式限制**：① 技术费按 OE 计费并记入该 OE 账本，跨 OE 聚合的月费 / 阶梯不支持（需 Partner 级账簿或分摊规则，另立决定）；② **收费合同的 Partner 法律主体必须与承担费用的 OE 法律主体一致**（OE 的 Party 即 Partner 的 Party），否则“平台应收 A、OE B 应付”之间缺少承担关系；commercial 保存平台价目 / 激活平台合同时按 OE 逐个校验，不一致则拒绝。允许不同主体（Partner 为 OE 代付、集团内往来）需另行明确费用承担、代付与往来规则（第 10 节 R19） |
| D4（修订） | **Partner 付平台服务费保留**：现有 `PLATFORM_SERVICE_CHARGE`（Partner OE 账本记费用 / 应付、平台主体账本记应收 / 收入）继续使用，计算口径改为 D12 的技术服务费；记账位置仍是收单 OE 账本（Partner 提供多个 OE 时，各 OE 分别记），平台主体账本对 Partner 法律主体记应收；第一轮“Partner 暂不收费、停用该分录”的写法作废 |
| D5 | **基础模式三种**：C1 Partner OE × MoR、C2 Partner OE × PSP、C3 Direct Merchant（Client 自有 OE × PSP）；**叠加层两种**：C4 推荐方（可叠加于 C1—C3）、C5 子商户 / 分账（**本期只允许叠加于 C2**；C1 + C5 涉及 MoR 主体替 Client 向其供应商付款，待法务确认后再开放，见第 10 节 R7）。Client 自有 OE × MoR 无效。**C2 的基础能力包含 Client 的子商户渠道身份**（收单侧子商户登记、生效状态、交易报送标识），与 C5 的分账收款方是两个角色，不随 C5 延期（第 10 节 R15） |
| D6（修订） | **OE 增加“收单资质”，三档**：`MERCHANT`（普通商户，只能为自身经营收款，不能承接任何 Client）、`MERCHANT_RESALE_APPROVED`（收单行书面认可其转售 / Marketplace 模式并完成卡组织所需登记，可签 MoR 合同即 C1）、`PAYFAC`（可签 PSP 合同即 C2，并可叠加 C5）。另加 **MCC 兼容约束**：C1 下 Client 的行业 / MCC 必须落在 Partner OE 获批的 MCC 范围内，由 commercial 组合校验与资格检查共同执行。第一轮“普通商户即可做 MoR”的写法作废（见第 10 节 R4） |
| D7 | 一个主体可同时是 OE、Partner、Client，**每个角色单独资格认证** |
| D8（修订） | **推荐分成按推荐合同配置，不写死付款方**：合同记“分成付款方”（PLATFORM 或 PAYFAC）与“分成基数”（Client 实付费用、平台技术费、买入价差）。默认建议：推荐给平台（C3 或平台技术费可按笔归属时）由 Platform 付、按技术费计；推荐给 PayFac 的由 PayFac 付、按 Client 实付费用计。推荐方定价权（`pricing_authority=PARTNER`）只对“付款方自己的价目”有效：Platform 付时限技术费，PayFac 付时限该 PayFac 的商户价目。第一轮“一律 Platform 付、只按技术费”的写法作废（与 D10 冲突，见第 10 节 R1） |
| D9 | Direct Merchant 转做 MoR：开通 Partner 角色并签 Partner 合同即可，前提是先与收单行确认转售模式 |
| D10 | **收费原则：能向 Partner 收就不向 Client 收。** C1 / C2 下 Client 的价目由 PayFac 定价（含 Platform 技术费的转嫁），在结算时扣；Platform 的价目对象是 Partner（相当于批发价） |
| D11 | **合同结构**：C1 / C2 的 Client 只与 PayFac 签商务合同，不与 Platform 签商务合同；Client 接受 Platform 使用条款（注册 / 开通时勾选，约定系统使用、数据处理与隐私、API 与账号安全、责任上限，无费用）。可选做法：在 PayFac 商户合同中写明“由 Platform 提供技术服务”并约定数据处理条款，但 Platform 直接面向 Client 的账号、API 密钥、控制台仍保留自己的使用条款。C3 的 Client 与 Platform 签收费的技术服务合同；PayFac Partner 与 Platform 签平台服务合同 |
| D12 | **技术服务费支持多种形态**：每笔百分比 + 固定、按量阶梯、月 / 年固定费与开户费、最低承诺、按功能或用量计费；具体价目未定 |
| D13 | **属性驱动、单点推导、下游只读快照**：系统不设“模式”枚举；商业关系由少数属性组合表达，只在 commercial 的 Checkout 解析处推导为“商业关系快照”（角色到主体的绑定），acquiring 只冻结不推导，下游系统只读快照中与自己相关的角色绑定，不判断 C1—C5。详见第 9 节 |

## 4. 模式总表

| | 卖方 | 谁持 MID | Client 在收单侧的身份 | 消费者资金先到 | 再给谁 | Partner 资质 | Partner 收入（谁付） | Platform 收入（谁付） |
|---|---|---|---|---|---|---|---|---|
| **C1** Partner OE × MoR | Partner OE | Partner OE | 供应商，不出现在收单侧 | Partner OE | Client（货款） | 普通商户 + 转售模式获收单行认可 | Client 的全部费用（含转嫁的平台费），结算时扣 | 技术服务费，**PayFac Partner** 按账单付或服务费预存款扣 |
| **C2** Partner OE × PSP | Client | Partner OE（PayFac） | 子商户（子商户 ID） | Partner OE | Client（结算款） | PayFac | 同 C1 | 同 C1 |
| **C3** Direct Merchant | Client | Client | 直接商户（自有 MID） | Client（收单行直结） | — | — | — | 技术服务费，**Client** 按账单付或服务费预存款扣 |
| **+C4** 推荐方 | 不变 | 不变 | 不变 | 不变 | 不变 | 无收单资质要求 | 推荐分成，Platform 付（按其技术费收入） | 不变（扣除分成） |
| **+C5** 子商户（本期仅 C2） | 不变 | Partner OE（PayFac） | 平台型商户，子商户各有子商户 ID | Partner OE | Client + 各子商户 | PayFac | 同 C2（分账费可打包进其价目） | 技术服务费（含分账功能），PayFac Partner 付 |

角色在各模式中提供的内容：

- **Platform**：系统、收银台、风控、运营（C5 另有子商户入驻 / KYC / 分账 / 余额出款系统）。
- **Partner（OE 提供方）**：C1 提供卖方主体、MID、对消费者开票与税、先承担退款与争议再向商户追偿；C2 提供 MID 与收单（PayFac），保管保证金。
- **Partner（推荐方）**：带来商户，可被授予技术服务费定价权。
- **Client**：C1 提供商品 / 服务并交保证金；C2 / C3 是卖方，承担税与退款；C3 另提供自有 OE、MID、收单合同与渠道凭据；C5 提供子商户与分账指令。

## 5. 系统支持现状（2026-10-23 只读核对）

图例：✅ 已支持　⚠️ 只有字段或部分实现　❌ 没有

| 系统 | 能力 | C1 | C2 | C3 | C4 | C5 |
|---|---|---|---|---|---|---|
| client-master | OE 来源（新模型去掉 `PLATFORM`，平台 OE 迁到自有 Partner） | ⚠️ | ⚠️ | ✅ `CLIENT` | — | — |
| | Partner 角色（OE 提供方 / 推荐方）、推荐关系 | — | — | — | ❌ | — |
| | OE 收单资质（D6） | ❌ | ❌ | — | — | ❌ |
| | 子商户实体 | — | — | — | — | ❌ |
| compliance | Client 资格（入驻审核） | ✅ | ✅ | ✅ | — | — |
| | OE / Partner 资格查询（选 OE 时用） | ❌ | ❌ | ❌ | ❌ | ❌ |
| commercial | 业务模式 MoR / PSP | ✅ | ✅ | ✅ | — | — |
| | 资金模式 `fund_mode` | ✅ 只接受经平台结算 | ✅ | ⚠️ `FEES_ONLY` 只有字段 | — | — |
| | 每项费用的收款方（Partner / Platform） | ⚠️ 平台服务费现由 Partner 承担 | ⚠️ | ⚠️ | — | — |
| | 定价权、买入价、价差分成 | — | — | — | ⚠️ 只有字段 | — |
| | 分账产品 | — | — | — | — | ❌ |
| acquiring | 建单真实调用商务解析并冻结 | ❌ Mock | ❌ | ❌ | ❌ | ❌ |
| | 税（MoR 由 OE 承担） | ⚠️ Mock | — | — | — | — |
| payment / channel-connector | MID 归 OE、路由、3DS、BIN | ✅ | ✅ | ⚠️ 缺 Client 提供凭据的渠道账号 | — | — |
| billing | 支付事实入账 | ⚠️ 进行中 | ⚠️ | ⚠️ | ⚠️ | ❌ |
| | 平台费计费：C1 / C2 向 Partner（D3、D4，现有 `PLATFORM_SERVICE_CHARGE` 口径需改）；C3 向 Client | ⚠️ | ⚠️ | ❌ | — | — |
| | 推荐分成 | — | — | — | ❌ | — |
| account / ledger | Partner OE 账本、结算应付、保证金与追偿 | ✅ | ✅ | — | — | — |
| | 平台主体对 Partner / Client 应收、服务费预存款负债 | ⚠️ Partner 应收已有 | ⚠️ | ❌ | — | — |
| payout | 结算出款给 Client | ✅ 人工 | ✅ | — | ❌ 推荐分成出款 | ❌ 子商户出款 |
| reconciliation | 渠道资金对账 | ✅ 切片 1 | ✅ | ❌ 只对账单回款 | — | — |
| 门户 | Client / 运营控制台 | ✅ | ✅ | ⚠️ 缺自有渠道凭据配置 | — | ❌ |

## 6. 生效前需修订的权威文档

| 文档 | 修订内容 |
|---|---|
| `AGENTS.md` Fixed architecture boundaries | OE 来源 `PLATFORM / CLIENT / PARTNER` → 去掉 `PLATFORM`（或保留为历史来源）；Partner 来源 OE 的 V5 Scope 与 `PLATFORM_SIGNATORY` 表述；`PLATFORM_SERVICE_CHARGE` 双账本分录保留但计算口径改为技术服务费（D4 修订）；Canonical terminology 中 Platform 的定义 |
| [commercial 设计第 13 节](../../design-discuss/commercial/design.md) | 模式表改为 C1—C5；去掉“我们是 PayFac（现状）”；`fund_mode` / `pricing_authority` 约束按 D3、D6、D8 调整 |
| [2026-10-19 六层定价决定](./2026-10-19-commercial-six-layer-product-pricing.md) | `PARTNER_SHARE` 只保留给推荐方；Client 价目归 PayFac 定价；平台价目对象改为 Partner（C1 / C2）与 Client（C3） |
| Finance / account / ledger 设计（[account-ledger V3 决定](./2026-10-18-account-ledger-subledger-control-account.md)） | 平台主体新增对 Client 应收与 Client 预付款负债；技术公司与 PayFac 两个 Finance Entity |
| client-master / compliance 数据库文档 | OE 收单资质三档（D6）；Partner 角色、关联关系与推荐关系；按角色资格；Client 行业 / MCC 与 OE MCC 范围的兼容约束 |
| `AGENTS.md` Canonical terminology | Local Partner 不再限定为“外部”伙伴：增加关联主体（我们的 PayFac）；Partner 门户只读规则对关联主体不适用（由平台运营代操作），对外部 PayFac 的操作权限列为后续需求 |
| 平台服务合同模板（法务） | 写明子商户 KYC / AML 为 PayFac 的卡组织义务，平台只提供工具与记录（第 10 节 R8）；技术公司作为 PayFac 服务商的卡组织登记与 PCI DSS 义务（R6） |

## 7. 待决问题

| # | 问题 | 当前倾向 |
|---|---|---|
| Q1 | 技术公司收取 Client（C3）/ PayFac 的服务费预存款是否需要牌照 | 限定为服务费预付款、只抵扣平台费用；请法务确认 |
| Q2 | ~~费用归属~~ 已由 D10、D11 解决：C1 / C2 下 Client 费用全归 PayFac 定价，Platform 向 PayFac 收技术费；剩余问题转为 Q7 | 已关闭 |
| Q3 | 外部 PayFac Partner 的收单费由谁定价（合作方自定、Partner 合同约定或平台代录） | 下一轮讨论 |
| Q4 | 现有平台 OE 迁移到自有 Partner 的数据迁移方案（mor-test 的 AE001、相关合同签约方、历史账本） | 随实施需求设计 |
| Q5 | 近期支持范围 | **已定（2026-10-23）**：本轮只实现 C1 / C2；快照、合同类型、价格组件角色、OE 收单资质与 Partner 角色字段按五种模式一次设计（取值先只允许 C1 / C2）；C3、C4、C5 按 9.5 顺序后置。前置需求登记为 [commercial-商业关系快照与真实解析接入](../../requirements/20261023/commercial-商业关系快照与真实解析接入/requirement.md) |
| Q8 | C1 转售模式在各目标司法辖区是否构成支付服务（PSD2 商业代理豁免、各地代收付认定）；Marketplace 卡组织登记门槛 | 请法务按辖区逐一确认；在确认前 C1 只对已取得 `MERCHANT_RESALE_APPROVED` 的 OE 开放 |
| Q9 | 外部 PayFac 接入时的操作权限（出款审批、争议、价目）与 KYC 责任边界 | 后续需求；本期只有关联 PayFac |
| Q7 | 技术服务费具体价目：形态组合、向 Partner 收费时用量 / 阶梯是否聚合该 Partner 名下全部 Client、是否把 3DS / 存卡 / KYB 等单独计价 | 待定（用户：未想好） |
| Q6 | 选 OE 时的资格检查对象统一为 Client、OE、OE 提供方（C4 再加推荐方，C5 再加子商户）；compliance 是否提供只读资格查询，OE / Partner 资格是否先由运营手工登记（`MANUAL` 来源） | 与 commercial 二期前置需求一并确认 |

## 8. 后续

- 用户确认本记录后：按第 6 节修订权威文档（单独提交），并在 [requirements/README.md](../../requirements/README.md) 登记的相关需求中引用。
- commercial 二期执行链路拆为两个需求：前置“acquiring 接入真实商务解析 + 资格查询”与二期“接受条件执行”（用户 2026-10-22 已确认拆分），均以本记录为准。

## 9. 统一设计：支持 C1—C5 的改造清单（D13 展开，方案稿）

### 9.1 设计原则

1. **模式不是代码分支，是数据。** C1—C5 不出现在任何下游代码里；差异只体现为“某个角色绑定到谁、是否存在”。
2. **角色绑定代替模式判断。** 全系统统一一组业务角色，下游只问“这个角色是谁 / 有没有”，例如“谁是资金托管方”，而不问“是不是 C3”。
3. **缺席即不发生，但缺席必须是显式的。** 快照用显式字段表达资金路径：`fund_flow = THROUGH_CUSTODIAN | DIRECT_TO_CLIENT`；只有 `DIRECT_TO_CLIENT` 时 `FUNDS_CUSTODIAN` 才允许为空。依赖托管方的行为（本金行、结算出款、渠道资金对账）按 `fund_flow` 决定是否发生，不写 `if (mode == C3)`，也不把 null 当语义：commercial 推导时按组合断言必填角色，缺则失败关闭；billing / reconciliation 收到某 OE 的渠道结算而快照为 `DIRECT_TO_CLIENT` 时必须报异常（第 10 节 R12）。
4. **约束只在两处校验，但快照不是执行许可。** 合法组合（D5、D6）只在 commercial 合同保存 / 激活与 Checkout 解析时校验；下游信任快照对**商业组合与历史责任**的解释（谁是卖方、谁收费、按哪个版本算）。支付与出款的执行仍须通过各自 Owner 的当前资格、资源状态（MID / 子商户绑定 / 账户状态）和风险门禁（Risk 评估、Manual Payout 四个检查点）；资格变化不改写历史快照，也不阻止已发生经济事实入账。
5. **关联关系不等于代操作权限。** `AFFILIATED` 只是事实属性；平台运营代关联 PayFac 出款、争议、价目操作的依据是 Partner 合同中的明确授权范围（委托事项清单），出款双人审批、Risk 门禁与审计留痕不因关联关系免除。
6. **双边价目代替单一 Client 价目。** 每份价目是“出价方 → 对手方”的双边关系，计费按快照里的角色找到适用价目，不按模式选价目。

### 9.2 统一的业务角色

| 角色 | 含义 | C1 | C2 | C3 | +C4 | +C5 |
|---|---|---|---|---|---|---|
| `CLIENT` | 商户 | Client | Client | Client | — | Client（平台型商户） |
| `SELLER` | 对消费者的卖方（MoR） | PayFac 的 OE | Client | Client | 不变 | 不变 |
| `ACQUIRING_OE` | 持 MID 的收单主体 | PayFac 的 OE | PayFac 的 OE | Client 的 OE | 不变 | PayFac 的 OE |
| `OE_PROVIDER` | 提供该 OE 的 Partner | PayFac | PayFac | 空 | 不变 | PayFac |
| `fund_flow`（字段） | 资金路径 | THROUGH_CUSTODIAN | THROUGH_CUSTODIAN | DIRECT_TO_CLIENT | 不变 | THROUGH_CUSTODIAN |
| `FUNDS_CUSTODIAN` | 消费者资金进入谁的账本 = **收单 OE 的账本**（不是 Partner 法律主体） | PayFac 的 OE | PayFac 的 OE | **空**（仅 DIRECT_TO_CLIENT 允许） | 不变 | PayFac 的 OE |
| `TAX_RESPONSIBLE` | 对消费者开票与税务责任 | PayFac 的 OE | Client | Client | 不变 | 不变 |
| `PLATFORM_FEE_PAYER` | 技术服务费的合同债务人（法律主体；本期记账落在收单 OE 账本，见 D3） | PayFac 法律主体 | PayFac 法律主体 | Client | 不变 | PayFac 法律主体 |
| `ACQUIRER_SUB_MERCHANT` | 在收单行眼中以子商户身份出现的主体；其渠道入网绑定由 channel-connector 持有，不进商务快照 | 空（OE 自身为商户） | **Client** | 空（Client 即直接商户） | 不变 | Client |
| `REFERRER` | 推荐方 | 空 | 空 | 空 | 推荐 Partner | 可选 |
| `SPLIT_PAYEE` | 分账收款方（Client 下的收款方，按分账指令逐单出现；原稿误名为 SUB_MERCHANT） | — | — | — | — | Client 的收款方 |
| `PLATFORM` | 技术服务商 | 平台主体 | 平台主体 | 平台主体 | 平台主体 | 平台主体 |

`SELLER`、`TAX_RESPONSIBLE` 由业务模式决定（MoR → 收单 OE；PSP → Client）；`fund_flow`、`FUNDS_CUSTODIAN`、`PLATFORM_FEE_PAYER` 由 OE 提供方决定（PARTNER → THROUGH_CUSTODIAN / 收单 OE / Partner 法律主体；CLIENT → DIRECT_TO_CLIENT / 空 / Client）；`ACQUIRER_SUB_MERCHANT` 由业务模式与 OE 提供方共同决定（PSP 且 PARTNER → Client）。推导规则只写一次，在 commercial，并按组合断言必填角色。

### 9.3 改造清单（按系统）

| # | 系统 | 改造 | 统一抽象 | 现状 |
|---|---|---|---|---|
| U1 | client-master | Party 多角色：同一法律主体可同时绑定 `CLIENT`、`OE`、`OE_PROVIDER`、`REFERRER`、`SPLIT_PAYEE`；OE 增加收单资质（`MERCHANT` / `PAYFAC`）；OE 来源去掉 `PLATFORM`，平台自有 OE 迁到我们的 PayFac Partner；Partner 角色区分 OE 提供方 / 推荐方；Client ↔ 推荐方关系 | 主体 + 角色，不按模式建表 | OE 来源三值；Local Partner 只有 OE 提供语义 |
| U2 | compliance | 只读资格查询：输入（角色, 主体 ID），返回当前资格是否有效；OE / Partner / 推荐方资格可由运营手工登记（`MANUAL` 来源）；子商户 KYC 复用同一流程 | 资格按角色，不按模式 | 只有 Client 入驻审核 |
| U3 | commercial · 合同 | 合同类型按“出价方 → 对手方”统一：PayFac → Client（商户价目）、Platform → PayFac（平台批发价）、Platform → Client（仅 C3）、Platform → 推荐方（分成规则 / 买入价）；`fund_mode` 由 OE 提供方推导，不再手填；组合约束集中校验 | 双边价目 | 只有 CLIENT / PARTNER 合同与 Client 价目；平台服务费挂在 Partner 来源 OE |
| U4 | commercial · 价格组件 | 组件的付方 / 收方统一用 9.2 的角色（`CLIENT`、`FUNDS_CUSTODIAN`、`PLATFORM`、`PLATFORM_FEE_PAYER`、`REFERRER`、`SPLIT_PAYEE`），由快照解析成具体主体 | 角色化付收方 | 已有 `payer_role / payee_role`，角色集需扩充 |
| U5 | commercial · Checkout 解析 | 唯一推导点：输出“商业关系快照”（9.2 的角色绑定 + `fund_flow` + 业务模式 + 开通项与接受条件 + 适用价目版本集合）。快照以“角色绑定行”承载而非固定列，本期只产出 C1 / C2 所需角色，C3—C5 的角色留待各自需求时追加（第 10 节 R13）；选 OE 时把快照中所有需资格的（角色, 主体）逐个交 U2 查询，并校验 OE 收单资质（D6）与 Client 行业 / MCC 落在 OE 获批 MCC 范围内，一个循环覆盖所有模式 | 单点推导 | 只输出 OE、合同、价目版本；资格客户端恒失败 |
| U6 | acquiring | 真实调用 U5（去掉 Mock）；冻结快照（替代零散的 entity_no、pricing_version 等列，或以快照子表承载）；创建 Intent 时把 Payment 需要的部分（收单 OE、`acquiring_role_of_client`：NONE / SUB_MERCHANT、接受条件、3DS 要求）传下去 | 只冻结不推导 | 商务端口是 Mock |
| U7 | payment / channel-connector | 路由只认“收单 OE”下的 MID（现状即如此）；**新增 Client × 渠道的子商户入网绑定**（channel-connector 持有：绑定的渠道 / 渠道账号或 MID、外部子商户代码、状态 REGISTERING / ACTIVE / SUSPENDED / CLOSED、登记时间），`acquiring_role_of_client=SUB_MERCHANT` 时路由只选 Client 在该渠道有 ACTIVE 绑定的 MID，**选路时把实际采用的绑定标识 / 版本与外部子商户代码随 Attempt 冻结，与现有 Channel / Channel Account / MID 冻结三元组同等对待**；后续 CAPTURE / REFUND / 争议动作沿用原交易冻结的子商户身份，绑定的当前状态只决定是否允许执行，不替换冻结身份（第 10 节 R21）；执行时把冻结的子商户代码交标准 API PAY / REFUND / CAPTURE 报送（按渠道映射；须逐渠道核对 PayerMax、NI/MPGS、Cybersource 是否支持子商户登记与报送，决定 C2 的可用渠道）；渠道账号增加归属主体与凭据来源（平台托管 / 归属方提供），C3 的 Client 自己维护凭据是“归属方提供”的一种 | 渠道账号归属主体；子商户绑定 | 凭据只由平台运营录入；无 Client × 渠道绑定 |
| U8 | tax | 对消费者开票主体 = `TAX_RESPONSIBLE`；费用发票的开票方 / 受票方 = 费用行的收方 / 付方 | 角色化开票 | Mock |
| U9 | billing | 计费事实由“快照 + 适用价目集合”生成费用行：每个价目组件按角色解析付收方；本金行只在 `FUNDS_CUSTODIAN` 存在时生成；推荐分成、分账行同样由组件生成 | 一套生成器，不按模式写分支 | 支付事实入账进行中（需求 B）；平台服务费按旧口径 |
| U10 | account / ledger | 账户统一为“（账本所属主体 = 收单 OE 或 Finance Entity, 对手主体, 角色, 账户类型）”；记账规则按费用行类型匹配，不感知模式。平台主体账本新增：对 Partner 法律主体 / Client 应收、服务费预存款负债、应付推荐分成；收单 OE 账本沿用结算应付、费用收入、应付平台费；Partner 级账簿本期不建（D3 限制） | 规则按行类型 | 账户 owner 类型有限；平台账本只有服务费应收 |
| U11 | payout | 出款对象 = 某账本里对某主体的应付余额（Client 结算款、推荐分成、子商户），一个流程 | 应付即可出 | 只有 Client / Partner 人工出款 |
| U12 | reconciliation | 渠道资金对账的对象 = `FUNDS_CUSTODIAN` 的账本；账单回款对账 = 平台应收 | 按账本对账 | 渠道资金对账切片 1 |
| U13 | 门户与权限 | 数据可见范围按“我在快照里扮演什么角色”：Partner 门户看自己作为 `OE_PROVIDER` / `REFERRER` 的商户；Client 看自己作为 `CLIENT`；C3 Client 可维护归属自己的渠道账号凭据 | 按角色绑定授权 | Partner 门户只读、范围按 OE 来源 |
| U14 | 分账模块（新） | 分账收款方（`SPLIT_PAYEE`）入驻（U1、U2）、分账指令（acquiring）、分账行（U9）、收款方余额与出款（U10、U11）；不要与 C2 的子商户渠道身份（U7）混淆 | 复用上述抽象 | 无 |

### 9.4 唯一需要“理解模式”的地方

- commercial：合同组合约束校验（D5、D6）与快照推导（9.2 的推导规则）。
- 其他系统只出现“某角色是谁 / 是否为空”的判断。若评审中发现下游代码需要写 C1—C5，说明快照缺字段，应补快照而不是加分支。

### 9.5 建议的落地顺序

1. **先立骨架（随前置需求，范围锁定为 U1、U2、U5、U6 四项）**：U1 的角色、关联关系与收单资质字段、U2 资格查询、U5 快照（角色绑定行，本期只产出 C1 / C2 角色，其余组合拒绝）、U6 去 Mock 冻结快照。**不迁移平台 OE，不做 U3 / U4 / U9 / U10**；快照带 `relationship_model_version=1` 并由开关 `commercial.relationship-snapshot.enabled` 控制，默认关闭（或仅非生产环境开启），第一步继续填充旧消费者依赖的字段（`entity_no`、定价版本等），下游行为不变。
2. **C1 / C2 跑通**：U3 / U4 双边价目与角色化组件（平台价目改为对 Partner）、U7 子商户绑定与报送、U9 / U10 按角色生成与记账、U8 开票主体。**全部验证通过后**，才执行平台 OE → 我们的 PayFac Partner 的迁移（Q4 方案经用户确认）并打开开关。

**启用门禁（新模式生效前必须全部满足）：** ① 双边合同与价目（U3 / U4）可保存、激活；② C2 子商户绑定与渠道报送（U7）至少在一个真实渠道验证；③ billing 按角色生成费用行且旧 `PARTNER_SHARE` / `PLATFORM_SERVICE_CHARGE` 口径已切换（U9）；④ 账务（U10）与开票（U8）链路验证；⑤ 平台 OE 迁移方案（Q4）确认并演练；⑥ 开关打开前 acquiring 冻结的快照只作记录、不改变下游计算。否则会出现新关系已生效、下游仍按旧费用承担方计算的错账（第 10 节 R18）。

**订单级模型冻结（开关语义，第 10 节 R20）：** 每笔订单在创建时冻结其**实际生效**的商业模型版本（`effective_relationship_model_version`：开关关闭时为 0 即旧模型，打开后为 1），该版本随订单、Intent 与后续支付 / 退款 / 争议 / 计费 / 记账全程沿用；第一步的观察快照不参与任何计算。开关只决定**新建订单**采用哪个模型，不改变既有订单：启用前创建、启用后才支付或退款的订单仍按旧模型；关闭开关也不把已采用新模型的订单切回旧算法。billing / ledger 以订单冻结的版本选择口径，不读全局开关。
3. **再开 C3**：放开快照的 `FUNDS_CUSTODIAN=空`、U7 归属方凭据、U12 账单回款对账、U13 C3 凭据维护。
4. **再开 C4**：推荐方角色、分成价目、U11 分成出款。
5. **最后 C5**：U14 分账模块。

## 10. 风险核对记录（2026-10-23 第三轮，Fable 5.1 复核 Opus 结论）

| # | 风险 | 处理 |
|---|---|---|
| R1 | D8（第一轮）与 D10 冲突：C1 / C2 下 Client 不付平台费，推荐方的“技术费定价权”与“按技术费分成”失去对象；平台向 PayFac 的技术费若按月 / 阶梯聚合，无法归到单个被推荐 Client | D8 改为按推荐合同配置付款方与基数（已修订） |
| R2 | P3 为第一轮过时结论，与修订后的 D3 矛盾 | P3 标注已被 P12 取代（已修订） |
| R3 | “Partner 门户只读”只对我们自己的 PayFac 成立；外部 PayFac 是法律上的收单主体，需要自行出款审批、争议处理、价目维护 | D2 增加关联关系属性；外部 PayFac 操作权限列为 Q9 后续需求 |
| R4 | “普通商户资质即可做 MoR”过于乐观：为众多无关供应商转售易被认定为聚合 / Marketplace（卡组织需登记）；在 PSD2 商业代理豁免等框架下，薄转售外壳可能被认定为支付服务；且缺少 Client 行业 / MCC 与 OE 获批 MCC 的兼容约束 | D6 改为三档资质并加 MCC 约束（已修订）；Q8 交法务按辖区确认 |
| R5 | “备付金”在支付监管语境中专指支付机构客户备付金，易引起法务误解 | 全文改为“服务费预存款”（已修订） |
| R6 | 技术公司作为 PayFac 的服务商，自身需由赞助行向卡组织登记（Visa TPA / Mastercard Service Provider）并受 PCI DSS 约束，记录原未提及 | 列入第 6 节平台服务合同与合规事项 |
| R7 | C1 + C5：MoR 主体按 Client 的分账指令直接付款给 Client 的供应商，可能构成替 Client 向第三方付款的支付服务；C2 + C5 才是标准 PayFac Marketplace | D5 改为 C5 本期只叠加 C2（已修订） |
| R8 | KYC / AML 责任：C1 / C2 下子商户 KYC 是 PayFac 的卡组织义务，compliance 模块只是工具；外部 PayFac 接入时若不写进合同，责任可能落在平台 | 列入第 6 节平台服务合同条款 |
| R9 | 服务边界：C1 / C2 下 Client 的结算扣费由平台的 billing / account 计算，因此外部 PayFac 对 Client 的价目也必须录入 commercial（运营代录）。平台实际提供的是 PayFac-as-a-Service | 明确为本决定的服务边界；commercial 仍是“谁签了什么”的权威，签约方可为外部 PayFac |
| R10 | 用 Local Partner 模型承载我们自己的 PayFac，会把外部伙伴的约束（PDD 入驻、只读门户、分润语义）套到自身 | D2 增加 `AFFILIATED / EXTERNAL`（已修订） |
| R11 | D10 之后平台收入全部来自 PayFac；接入外部 PayFac 后为应收账款集中风险 | 经营风险，记录备查 |
| R12 | 第 9 节“角色为空即不发生”会静默失败：若 bug 使 C1 交易的 `FUNDS_CUSTODIAN` 冻结为空，渠道资金到账却不生成本金行、不结算且无报错 | 改为显式 `fund_flow` 字段 + 推导断言 + billing / reconciliation 一致性异常（已修订 9.1、9.2） |
| R13 | 快照字段“一次设计完整”会在无业务依据时提前定错 C4 / C5 字段 | 快照改为角色绑定行，本期只产出 C1 / C2 角色（已修订 U5） |
| R14 | 第 9 节 U1—U14 几乎动到所有系统，前置需求有被撑大的风险 | 9.5 第 1 步范围锁定为 U1、U2、U5、U6（已修订） |

| R15 | （第四轮评审）C2 必需的 Client 子商户渠道身份未补齐：`t_channel_mid` 只挂 OE，无 Client × 渠道的入网绑定、状态与报送标识；原稿把 `SUB_MERCHANT` 留给 C5，混淆了“收单侧子商户”与“分账收款方” | 角色拆为 `ACQUIRER_SUB_MERCHANT`（C2 基础能力）与 `SPLIT_PAYEE`（C5）；U7 新增 Client × 渠道绑定与报送；U6 传 `acquiring_role_of_client`（已修订） |
| R16 | （第四轮评审）Partner、OE 与账簿主体未分清：D3 债务人为 Partner、D4 记 OE 账本、角色表写“PayFac 财务主体”；Partner 提供多个 OE 且平台按 Partner 总量收月费时无法落账 | 合同债务人 = Partner 法律主体，资金 / 账簿主体 = 收单 OE；本期显式限制技术费按 OE 计并记 OE 账本，跨 OE 聚合定价不支持（已修订 D3、D4、9.2、U10） |
| R17 | （第四轮评审）9.1“下游信任快照”可能被理解为永久执行许可，未排除当前资格、MID / 子商户停用、账户冻结、出款门禁；`AFFILIATED` 可能被理解为自动取得代操作权限 | 9.1 增加原则 4 边界与原则 5（已修订） |
| R18 | （第四轮评审）分步实施缺启用门禁：第一步迁移平台 OE 后，现有 billing / ledger 会按旧语义处理其交易（平台付 `PARTNER_SHARE`、Partner 付 `PLATFORM_SERVICE_CHARGE`），与新模型资金流相反 | OE 迁移移到第二步末尾；快照带版本与开关、默认不改变下游；增加六项启用门禁（已修订 9.5） |

| R19 | （第五轮评审）D3 / D4 的债务人（Partner）与记账主体（OE）可能不是同一法律主体：平台记“应收 A”、OE B 记“应付平台”，A、B 之间缺少承担关系 | 本期要求 Partner 法律主体与承担费用的 OE 法律主体一致并由 commercial 校验；不同主体的代付 / 往来规则另立（已修订 D3） |
| R20 | （第五轮评审）开关切换后旧订单语义不明：启用前创建、启用后支付或退款的订单可能误用观察快照计费 | 订单创建时冻结实际生效的模型版本并全程沿用；开关只影响新订单，关闭也不回切；观察快照不参与计算（已修订 9.5） |
| R21 | （第五轮评审）子商户绑定缺“原交易身份不可变”：支付后 Client 的子商户代码更新，后续 Capture / Refund 可能改用新身份 | 选路时把绑定标识 / 版本与外部子商户代码随 Attempt 冻结，后续动作沿用；当前状态只决定能否执行（已修订 U7） |

核对后无需修改的结论：D1、D2 主体、D5 三种基础模式、D7、D11、D13 总方向、P7、P9；第 5 节现状表与代码一致。
