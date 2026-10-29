# 地区规则包与首批 AE / SA / EG

状态：**讨论稿，待评审**，2026-10-26；2026-10-29 新增第 6 节注册与申报通用要求调研。业务模型见 [design.md](design.md)，字段见 [database.md](database.md)。下列为公开资料研究后的设计输入；实际启用以卖方机构、注册、供给性质和当地复核结果为准，不将参考税率当完整合规规则。

## 1. 首批适用基线

假设：数字产品 / 自动化 SaaS；AE 法律卖方；没有参与供应的 SA / EG 本地机构；非特殊免税 / 零税率产品；消费 / 使用地证据支持相应国家。各税率是在**存在相应收税义务且注册有效**的前提下展示。

| 买家 / 地点 | 通常的销售税处理 | 注册与单据影响 |
|---|---|---|
| AE 使用，个人或普通企业买家 | AE 卖方的普通应税电子服务通常 5%；有公司税号不自动免税 | 依据 AE 卖方实际 VAT 注册、发票及电子开票适用范围 |
| SA 使用，未注册消费者 | 非居民供应商普通应税销售通常收 15% | 必须核实非居民注册责任；不能套用本地企业起征点而默认免收 |
| SA 使用，满足条件的已注册纳税人 | 通常由买方反向征税，卖方不向其收取该 SA VAT | 保留登记核验证据与相应发票文字，不把结果记成 0% 税率 |
| EG 消费，未注册消费者 | 普通远程数字服务通常 14%，取决于适用注册义务 | 普通远程服务的 SVRR 阈值、登记和具体收据要求需要按事实落实 |
| EG 消费，满足条件的已注册纳税人 | 买方反向征税；保留符合要求的身份核验 | 应设计 TRN + UIN 核验扩展，验证接口能力与法定适用要求分别确认 |

AE 的电子服务规则强调**实际使用和享受地**；付款地、合同签订地不直接决定供应地。AE 卖方向境外使用者销售电子服务也不能一律写成“AE 出口零税率”，可能是 AE 不在征税范围，同时在目的国产生责任。[FTA E-Commerce Guide，4.4–4.5](https://tax.gov.ae/DataFolder/Files/Pdf/E-Commerce%20-%20VAT%20Guide%20-%20EN%20-%2009%2008%202020.pdf)

SA 标准税率与跨境 RCM 分别参考 [ZATCA VAT FAQ](https://zatca.gov.sa/en/HelpCenter/FAQs/Pages/FAQArchiveEservices.aspx?page=Tax-FAQs-9)、[RCM Circular](https://zatca.gov.sa/en/MediaCenter/Publications/Documents/VAT%20Circular%20-%20RCM-EN.pdf)。平台视同供应商规则与非居民注册责任见 [2025 VAT 实施条例修订说明](https://zatca.gov.sa/en/HelpCenter/guidelines/Documents/Amendments-to-the-Implementing-Regulation-of-%28VAT%29.PDF)。这些条件不能简化为 `business_mode=MOR` 的单一判断。

EG 官方说明区分 B2B 与 B2C，并对一般非居民远程服务给出任意 12 个月 EGP 500,000 注册条件；某些专业 / 咨询服务存在不同规则，不能把它们混进 SaaS 包。[ETA 公告](https://portal.eta.gov.eg/ar/content/egyptian-tax-authority-eta-has-recently-published-value-added-tax-vat-guidelines-digital)、[ETA Digital Services Guide](https://www.eta.gov.eg/sites/default/files/2023-03/digital-service-guide-english.pdf)。

## 2. 为什么不能只建国家税率表

同一 SA 买家，AE 非居民供应商与 SA 本地供应商的税务处理可能不同；同为企业买家，已验证登记与只填公司名称不同；同一国家，SaaS 与人工咨询未必同类；同一订单，更新买家信息可能改变处理；同一个税率，含税价与未税价产生不同收款金额。

因此判断顺序为（2026-10-29 补充第一步：先判断税务责任方是否为平台登记的纳税主体，不是则返回 `NOT_MANAGED`，后续规则包不再匹配；这一步不属于地区包配置）：

```text
确定税务责任方是否为平台登记的纳税主体
→ 确定供应双方和参与机构
→ 确定产品税类和供应事实
→ 根据证据确定供应地
→ 判断法律责任、注册制度与买方身份
→ 确定 tax point 和适用规则版本
→ 逐行计算与取整
→ 输出单据要求、申报映射和解释链
```

不是所有判断都能纯配置：例如“这是自动化电子服务还是主要由人工交付的服务”，需要明确产品定义和事实审核，不能由关键字自动分类。

## 3. 地区包的固定 Schema

`t_tax_policy` 头字段负责版本、地区、税种、制度与生效时间。`policy_json` 只包含下列已定义区块：

| 区块 | 允许配置什么 | 不能配置什么 |
|---|---|---|
| applicability | 供应类别、卖方参与机构类型、商品税类、接收方类别、供应地条件 | 跨服务 SQL、未经授权改卖方 / OE |
| placeOfSupply | 已支持算法代码及有限参数、证据优先级、冲突处理 | 自由代码 / 自然语言执行 |
| taxpayerEvidence | 标识类型、已支持核验能力代码、有效期、超时处理 | 外部密钥、任意 URL、原始个人资料 |
| taxPoint | 已实现的税点算法及必填事实，例如批准的供应 / 付款事件组合 | 默认取服务器当前日期 |
| treatments | 互斥条件、处理类型、责任角色、税率、理由码、所需注册 | 静默按规则顺序覆盖、默认零税 |
| calculation | 比例算法、行级 / 单据级合法取整、含税反算、折扣分配方式 | 未实现的复合税 / 阶梯税算法 |
| documents | 类型、语言、必填字段、固定法定文字、模板与提交能力 | 新建任意工作流、改变金额、绕过身份保护 |
| reporting | 申报制度、条目角色到栏目映射、法定汇率来源与日期、调整期规则 | 覆盖历史底稿或直接写账 |
| retention | 法定留存分类、期限、依据 | 立即销毁 Customer / Vault 数据 |

条件语法首版限定为已登记字段的 `EQ / IN` 合取；多规则表达分支，没有递归表达式、脚本、任意函数或跨字段运算。发布前用 Schema + 能力白名单 + 区间 / 条件互斥校验 + 地区场景集检验；发布后不可修改内容。金额计算能力的变更须发布代码和测试，旧算法版本仍能解释历史。

以下是 **EG 销售包 treatments 的说明性片段**，不是可直接上线的完整规则包；代码是本系统内部建议名：

```json
{
  "schemaVersion": 1,
  "treatments": [
    {
      "ruleCode": "EG_REMOTE_REGISTERED_BUYER",
      "when": {
        "supplyKind": ["SALE"],
        "categoryCode": ["SAAS_AUTOMATED"],
        "sellerEstablishment": ["NON_RESIDENT"],
        "placeOfSupply": ["EG"],
        "buyerTaxpayerStatus": ["VERIFIED_REGISTERED"]
      },
      "result": {
        "treatment": "REVERSE_CHARGE",
        "liableRole": "BUYER",
        "reasonCode": "EG_REMOTE_B2B_RCM",
        "rate": null
      }
    },
    {
      "ruleCode": "EG_REMOTE_CONSUMER",
      "when": {
        "supplyKind": ["SALE"],
        "categoryCode": ["SAAS_AUTOMATED"],
        "sellerEstablishment": ["NON_RESIDENT"],
        "placeOfSupply": ["EG"],
        "buyerTaxpayerStatus": ["NON_REGISTERED"],
        "collectionAssessment": ["REQUIRED_AND_REGISTERED"]
      },
      "result": {
        "treatment": "TAXABLE",
        "liableRole": "SELLER",
        "reasonCode": "EG_REMOTE_B2C_STANDARD",
        "rate": "0.14"
      }
    }
  ],
  "unmatchedBehavior": "BLOCK"
}
```

UNKNOWN 核验结果不等于 NON_REGISTERED。合法允许按 B2C 征收的降级策略必须明确配置并向买家解释；首版未批准时阻断该税务处理，不能因为 API 超时就认定免收税。

## 4. 三国特有能力放在哪里

| 差异 | 公共字段 | 动态配置 / 适配 |
|---|---|---|
| AE 电子服务使用地 | 地点证据、source、checked_at、country | AE placeOfSupply 策略及冲突处理；不是全球通用“两份证据就通过” |
| SA 企业跨境 RCM | buyer 核验状态、supplier 机构事实、treatment | SA 适用条件、核验来源与发票法定文字 |
| SA 阿拉伯语税票及 FATOORA 范围 | document 模板 / 提交状态 | 阿拉伯语 / 双语模板；联网能力按供应商实际适用范围启用 |
| EG TRN + UIN | evidence 类型、受保护快照引用、有效期 | `EG_TAXPAYER_UIN` 验证能力 + SERVICE 标准 API，协议单独实现 |
| 注册 / 申报制度差异 | registration、scheme、period、return | 报表栏目、法定汇率、税点、周期及截止安排 |
| 将来州 / 省税、多税组件 | jurisdiction_code、component、reporting_jurisdiction_code | 新包；若需要新复合算法，先开发适配能力 |

SA 电子发票法规明确区分居民与非居民；非居民排除不等于没有普通税务单据义务。不要只根据买家是 SA 就启用 FATOORA。[ZATCA E-invoicing Regulation，Article 3](https://zatca.gov.sa/en/E-Invoicing/Introduction/LawsAndRegulations/Documents/E-invoicing-Regulations.pdf)。阿拉伯语可读字段要求见 [ZATCA FAQ](https://www.zatca.gov.sa/en/E-Invoicing/Introduction/FAQ/Pages/default.aspx)。AE / EG 的联网开票义务也必须按供应方真实制度和生效安排确认，本稿不假定永久“不需联网”。

ETA 官方 SDK 给出 `validateEcommerceTaxpayersByUIN`，输入 TRN 与 UIN，可要求返回到期日。适配器必须校验业务结果，不能将 HTTP 200 自动认定为已验证；原始标识经 counterparty 的受控通道处理。[ETA SDK 入口](https://www.eta.gov.eg/en/ecomSDK)、[官方接口文档](https://portal.eta.gov.eg/sites/default/files/2024-10/e-commerce_integration_sdk_.pdf)。已确认的是接口存在及协议结构，具体强制适用交易、验证周期和有效期政策需在上线规则复核时确定。

## 5. 启用一个地区的最小完整动作

确认主体和供应类型 → 记录责任评估及注册 → 发布经审核的地区包 → 核对证据、单据、申报和缴款能力 → 跑通 [cases.md](cases.md) 对应案例 → 设置市场准入 ENABLED。

不把供应商控制台里“添加注册”当作税局注册已办成；[Stripe 注册文档](https://docs.stripe.com/tax/registering) 也区分真实注册与产品配置。尚未完成注册但已经有义务时要阻断新增销售并处置已发生交易，不能自动变成 NOT_COLLECTED。

## 6. 注册与申报的通用要求（2026-10-29 调研）

背景：首批纳税主体是 PayFac Partner 的 AE / SA / EG 主体，三国注册均尚未取得，由 Partner 财务办理与申报；系统只按注册结果配置 `t_tax_registration` 与 `t_tax_period`。以下是公开资料的通用要求，**不是法律意见**，标注"官方"的条目来自税局站点，其余来自第三方合规指南，启用前须由 Partner 的税务顾问复核。

两种主体路径的要求不同，Partner 需先确认走哪一条：

| 路径 | 含义 | 对注册与申报的影响 |
|---|---|---|
| A 非居民注册 | 一个 AE 主体在 SA / EG 以非居民身份注册 | SA 可能需要税务代表与担保；EG 走简化供应商登记（SVRR），仅 B2C；本地电子发票义务通常不同于居民 |
| B 本地主体 | 在 SA / EG 各设本地法人，各自注册 | 按居民规则：有起征点、本地申报周期与电子发票义务；同一 PayFac 在本模型中是三个 `subject_party_id` |

当前示例（AE 卖方、无 SA / EG 本地机构）按路径 A 写；模型两条路径都支持，差别只在 `t_tax_registration.scheme_code`、地区包与单据能力。

### 6.1 三国要点

| 项目 | AE（FTA） | SA（ZATCA） | EG（ETA） |
|---|---|---|---|
| 标准税率 | 5% | 15% | 14%（专业 / 咨询服务 10%） |
| 居民起征点 | 强制 AED 375,000、自愿 AED 187,500 | 强制 SAR 375,000、自愿 SAR 187,500 | 一般 EGP 500,000（专业服务无起征点） |
| 非居民起征点 | 无：当 UAE 没有其他人负责为该供应计税时，自首笔应税供应起登记；买方全部为已注册企业并适用反向征税时可不登记 | 无：须在首笔应税供应后 30 天内登记（第三方资料） | SVRR：任意连续 12 个月对埃及消费者远程服务销售 ≥ EGP 500,000；专业 / 咨询服务无起征点 |
| 税务代表 / 担保 | 非居民可指定税务代理，非强制 | **资料冲突**：部分指南称非强制，ZATCA 税务代理页被引述为"所有非居民须有一名税务代表"；另有"非居民登记前须提供担保，金额可达平均季度销项税的两倍"的说法。须向 ZATCA 核实 | SVRR 不要求财务代表，经 ETA 门户直接登记，资料齐全约 10 天发证 |
| 申报周期 | 通常按季，FTA 可指定按月 | 年应税供应 > SAR 40,000,000 按月，否则按季；ZATCA 可指定 | SVRR 按月 |
| 申报与缴款截止 | 税期结束后 28 天内申报并缴款（**官方**） | 税期结束后次月最后一日（ZATCA 2026 年 6 月通知：6 月与 Q2 申报截止 7 月 31 日，**官方**） | 税期结束后的次月内申报并缴款 |
| 缴款币种 | AED | SAR | 第三方资料未明确，需确认 SVRR 是否允许外币 |
| B2B 处理 | 已注册企业买方反向征税；有税号不等于已验证 | 已注册买方跨境反向征税；需核验买方 VAT 号 | B2B 不在 SVRR 内，由买方反向征税；2024-11-01 起非居民须核验买方 TRN + UIN |
| 电子发票 | B2B / B2G 分阶段：2026-07-01 试点与自愿；营收 ≥ AED 50M 于 2027-01-01 强制（ASP 指定截止 2026-10-30）；其余 2027-07-01；B2G 2027-10-01；B2C 暂不纳入。部令编号资料不一致（56 / 66 of 2026），须以 FTA 原文为准 | FATOORA 法规第 3 条排除非居民（见第 4 节）；但有指南称非居民一经登记即适用数字报告义务，两者**冲突**，须核实 | B2C 电子收据义务在扩展中；非居民 SVRR 供应商是否纳入未见官方说明；2025 年第 281 号决议把居民电子发票门槛降到 EGP 250,000，是否影响 SVRR 未验证 |

### 6.2 对模型的直接影响

- `t_tax_registration.filing_frequency` 与 `t_tax_period` 不能按国家写死：AE 按季或按月由 FTA 指定，SA 按年应税供应额分档，EG SVRR 按月。期间与截止日在注册取得后按税局分配录入。
- `t_tax_registration.payment_instruction_ref` 与 `t_tax_payment.currency`：AE / SA 以本币缴纳；EG 需确认 SVRR 的缴款币种与渠道。
- SA 的税务代表 / 担保若被证实为强制，属于 Partner 注册阶段的事项，不进入模型；但代表的联署责任会影响 `approved_by` 的人员范围。
- EG 的 TRN + UIN 核验是 B2B 反向征税的前提，对应第 4 节的 `EG_TAXPAYER_UIN` 能力；没有该能力前，EG 的 B2B 只能按 B2C 处理或阻断，由地区包明确。
- 三国电子发票义务都在变化：AE 的 B2C 暂不纳入、SA 排除非居民、EG 扩展中。`t_tax_document.submission_status=NOT_REQUIRED` 必须由地区包按主体路径与生效日给出，不能默认。
- 首期系统只自动开具不含买方个人资料的 B2C 简化税票 / 收据（[design.md 第 10 节](design.md#10-人工处理的边界)）。AE 与 SA 都有简化税票制度，但适用条件（买方未注册、金额上限等）与必填字段要在地区包的 `documents` 区块按官方规则配置并经复核；EG 的收据要求按 SVRR 实际规定确认。B2B 税票首期由 Partner 财务外部开具。

### 6.3 来源

官方：[FTA 申报与缴款](https://tax.gov.ae/en/taxes/vat/vat.topics/filing.vat.returns.and.making.payments.aspx)、[ZATCA 2026 年 Q2 申报通知](https://zatca.gov.sa/en/MediaCenter/News/Pages/VAT-Returns-submission-for-Q2-June-2026.aspx)、[ETA 数字服务指南](https://www.eta.gov.eg/sites/default/files/2023-03/digital-service-guide-english.pdf)、[ZATCA 电子发票法规](https://zatca.gov.sa/en/E-Invoicing/Introduction/LawsAndRegulations/Documents/E-invoicing-Regulations.pdf)。

第三方（需复核）：[Avalara UAE 指南](https://www.avalara.com/us/en/vatlive/country-guides/africa-and-middle-east/united-arab-emirates.html)、[Taxually UAE](https://www.taxually.com/manuals/uae)、[Fonoa SA 数字服务](https://www.fonoa.com/resources/country-tax-guides/saudi-arabia/tax-on-digital-services)、[Commenda SA 财务代表](https://www.commenda.io/saudi-arabia/fiscal-representation)、[ClearTax SA 申报](https://www.cleartax.com/sa/vat-return-filing-saudi-arabia)、[Deloitte EG 非居民指引](https://www.deloitte.com/middle-east/en/services/tax/perspectives/vat-guidelines-for-non-resident-providers-of-digital-and-remote-services.html)、[EY EG 2023 快讯](https://globaltaxnews.ey.com/news/2023-0848-egypt-introduces-vat-guidelines-for-nonresident-providers-of-remote-digital-and-electronic-services)、[VATabout EG](https://vatabout.com/egypts-vat-rules-for-marketplaces--digital-services-key-compliance-guide)、[e-invoicing.org UAE](https://e-invoicing.org/uae/)、[KPMG UAE 电子发票字段](https://kpmg.com/us/en/taxnewsflash/news/2026/02/uae-technical-guidance-mandatory-e-invoicing-fields.html)、[Thomson Reuters EG 更新](https://europe.thomsonreuters.com/news/e-invoicing-in-egypt/)。

初期不用自动向税局提交来证明闭环成立：人工注册、申报、缴款配合真实回执和完整追溯可以先交付。若具体制度要求法定联网发行 / 报送，这项能力就是该市场上线前置条件，不能以人工回填替代。
