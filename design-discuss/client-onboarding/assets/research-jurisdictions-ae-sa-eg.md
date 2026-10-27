# AE / SA / EG 商户入驻与 KYC/KYB 监管调研笔记

调研日期：2026-10-26。只写事实，每条附来源；不确定项标"待核实"。

---

# 一、阿联酋（AE）

## A. 自然人标识

| 项目 | 事实 | 来源 |
|---|---|---|
| Emirates ID 号码 | 15 位数字，格式 `784-YYYY-NNNNNNN-C`；`784` 为阿联酋 ISO 3166-1 数字国码；`YYYY` 通常为出生年但"可能有例外，不可作为校验依据"；末位为校验位 | https://smarttrekassist.ae/emirates-id-format/ ；https://checkidstatusonline.com/emirates-id-format/ |
| 校验位算法 | 多个来源（含 Microsoft Purview 实体定义）称末位为 Luhn/Mod 10 校验；但 GitHub 验证器讨论中有人报告真实样例 Luhn 不通过，怀疑校验基于截断序列 → **Luhn 校验仅作格式提示，待核实** | https://learn.microsoft.com/en-us/purview/sit-defn-uae-identity-card-number ；https://gist.github.com/geordee/e51d111426de675c0c0f8503c2003047 |
| 有效期 | 公民 <21 岁 5 年、≥21 岁 10 年；GCC 公民 5 年；外籍居民"与居留签证期限一致"（第三方称 1–3 年，上限 5 年，待核实） | https://en.wikipedia.org/wiki/Emirates_national_identity_card ；https://www.bayut.com/mybayut/all-about-emirates-id/ |
| 签发机构 | ICP（Federal Authority for Identity, Citizenship, Customs and Port Security），旧称 ICA/FAIC | 同上 |
| 芯片字段（官方） | `Full Name`（阿/英各一字段，最长 126 字符，含 6 个逗号分隔符）、`Mother Full Name`（阿/英）等 | https://icp.gov.ae/ica_files/vg_downloads/fields_stored_in_uae_id_v2_card_chip.pdf |
| 护照号 | Purview 定义：9 位字母数字，字母限 C,F,G,H,J,K,L,M,N,P,R,T,V,W,X,Y,Z，不可全为数字（第三方 DLP 规则，待核实） | https://learn.microsoft.com/en-us/purview/sit-defn-uae-passport-number |
| 居留签证 | 与 Emirates ID 绑定；UID（Unified Number）为另一移民编号，格式未取得官方说明（待核实） | https://henryclub.ae/visa-immigration/uid-number-check |
| 官方核验 | ICP Validation Gateway：验卡真伪与状态、读取个人信息、指纹比对、PIN 重置、数字签名、OCSP、时间戳；需 ICP 审批并签合作合同，分 Initiation/Pilot/Implementation/Go-Live 四阶段 | https://icp.gov.ae/en/icp-validation-gateway/ |
| 监管强制 | CBUAE 2021 AML 指引：持牌金融机构核验 Emirates ID "**must use the online validation gateway** of the Federal Authority for Identity & Citizenship and keep a copy of the UAE ID and its digital verification" | https://centralbank.ae/media/zhgjanp0/cbuae-guidelines-on-aml-cft-for-fis-june-2021_1.pdf（§6.3.1） |
| UAE PASS | OAuth 2.0 / OpenID Connect；需经 DIGI 门户注册并由 UAE PASS 团队审批用例；返回 Emirates ID 号、姓名、国籍、出生日期（供应商描述，待核实） | https://www.smartscreendxb.com/en/blog/uae-pass-integration-app ；https://didit.me/blog/uae-pass-integration/ |

## B. 机构标识

| 项目 | 事实 | 来源 |
|---|---|---|
| 大陆执照 | 各酋长国经济部门签发（迪拜 DED 现名 DET）；执照号格式无官方公开说明（待核实）；迪拜有 Dubai Unified License (DUL) 号 | https://www.daftime.ae/new-article/dubai-trade-license-check |
| 联邦登记 | National Economic Register (ner.ae)：提供 Unified Economic ID (CBL)、执照号、登记机关、到期日，覆盖七个酋长国 | https://www.nxtg.ae/how-to-check-trade-license-online/ |
| 核验渠道 | invest.dubai.ae "Search License Information"；eservices.dubaided.gov.ae；DED Trader App；MoHRE 公司查询；自贸区各自登记处（DMCC、DIFC 等公开程度不一） | https://bestaxca.com/check-trade-license-online-uae/ |
| 自贸区清单（部分） | 阿布扎比：ADGM、ADAFZ、KEZAD、Masdar、twofour54；迪拜：DIFC、DMCC(JLT)、JAFZA、DAFZ、Dubai South/DWC、Dubai Silicon Oasis、Meydan、DIC/DMC(TECOM)、Dubai CommerCity、DUQE 等；沙迦：SAIF、Hamriyah、Shams、SPC、SRTIP；阿治曼：AFZ、AMCFZ；RAK：RAKEZ、RAK ICC、RMCFZA；富查伊拉：FFZ、Creative City；乌姆盖万：UAQFTZ | https://en.wikipedia.org/wiki/List_of_free-trade_zones_in_the_United_Arab_Emirates |
| 金融自贸区 | DIFC、ADGM 有独立法律/监管体系（含独立数据保护法、独立公司登记） | 同上；https://www.dlapiperdataprotection.com/countries/uae-general/law.html |
| TRN（增值税号） | 15 位纯数字，无字母/空格；多来源称以 `100` 开头（FTA 未公开结构，**待核实**）；核验：tax.gov.ae 公开 TRN 查询（输入 TRN + 验证码，免登录，返回注册法定名称） | https://www.cleartax.com/ae/trn-verification-uae ；https://velmontcrest.ae/tools/uae-trn-verification/ |

## C. 组织类型目录与人员角色

| 形态 | 要点 | 典型人员角色 | 来源 |
|---|---|---|---|
| LLC（大陆） | CCL 32/2021 Art.71：1–50 名股东；独立法人；须有 MOA、审计师；经理由 MOA 指定或股东大会任命，经理任命须在 DED 登记，任期届满无继任最多延 6 个月 | 股东（MOA 列明持股比例）、经理（Manager）、授权签字人（未列于执照者需 POA） | https://qawi.ae/blog/uae-llc-sole-establishment-branch ；https://www.lexismiddleeast.com/eJournal/2025-10-10_31/en |
| Sole Establishment | 一名自然人持牌经营，无限责任；无需 MOA/审计；部分专业执照外籍需 Local Service Agent | 业主（Owner）；若执照仅列经理则经理为代表（Stripe 规则） | https://aedbs.com/blogs/news/sole-establishment-vs-llc-vs-civil-company-in-dubai-mainland-2026-which-legal-structure-saves-the-most |
| Civil Company | 专业人士合伙，签 MOA，合伙人可对债务承担个人责任；部分需 LSA | 合伙人、经理 | 同上 |
| FZE | 自贸区，单一股东（个人或公司），有限责任 | 股东、经理/董事 | https://www.uniwide.com/fze-fz-llc-or-fzco-in-the-uae/ |
| FZCO / FZC | 2–50 名股东 | 股东、董事、经理 | 同上 |
| FZ-LLC | DMCC/DIFC/ADGM 等采用，通常 ≤50 股东 | 同上 | https://www.alaan.com/blog/how-to-set-up-a-free-zone-company-in-the-uae |
| Branch of foreign company | 无独立法人，母公司承担责任 | 母公司、分支经理（POA） | https://www.investindubai.gov.ae/en/business-setup/free-zone-companies |
| PJSC | 大陆公众股份公司（细节本次未取得） | 董事会 | — |

## D. UBO 规则

| 项目 | 事实 | 来源 |
|---|---|---|
| 法规 | Cabinet Decision No. 109/2023，取代 Cabinet Resolution 58/2020；生效 2023-11-06（另有来源称 11-16，**待核实**） | https://mbgcorp.legal/insights/uae-cabinet-decision-no-109-of-2023-regulating-the-beneficial-owner-procedures/ ；https://rulebook.centralbank.ae/en/rulebook/cabinet-decision-no-109-2023-regulating-beneficial-owner-procedures |
| 定义/门槛 | 直接或间接（含所有权链）持有 **≥25%** 资本或投票权的自然人；无人达标→通过其他方式控制者（如任免多数董事）；仍无→高级管理职位持有人 | 同上 |
| 时限 | 变更通知 **15 天**；向登记机关提交登记 **60 天**（自生效/注册起）；Nominee 董事须在任命后 15 天内告知 | mbgcorp |
| 适用范围 | 所有在 UAE 持牌/注册的法人，含商业自贸区；**豁免**政府全资实体与金融自贸区（DIFC/ADGM 自有制度） | mbgcorp |
| 保密 | 登记数据保密，部委/登记机关非经书面批准不得披露（法定/AML/税务交换除外）→ 无公开查询 | https://www.almaazmilawyers.com/insights/beneficial-ownership-compliance-uae |
| 罚则 | 来源冲突：AED 100,000 上限 vs AED 50,000（**待核实**） | https://gostride.ventures/accounting-services/blog/ubo-register-uae/ |
| 金融机构侧 BO 识别 | Cabinet Res. 134/2025 Art.10：①≥25% 实际控制权益；②有疑或无人达标→法律或实际控制人；③仍无→高级管理人员。Nominee Shareholder / Nominee Director 明确"不视为 BO" | https://uaefiu.gov.ae/media/3ssbp5d5/cabinet-134-2025.pdf |

## E. AML/CFT 与 KYC

| 项目 | 事实 | 来源 |
|---|---|---|
| 现行法 | Federal Decree-Law No. 10/2025（取代 20/2018）；执行条例 Cabinet Resolution No. 134/2025（取代 10/2019），一说 2025-12-14 生效（**待核实**） | https://uaefiu.gov.ae/media/3ssbp5d5/cabinet-134-2025.pdf ；https://amluae.com/guide-to-new-cabinet-resolution-no-134-of-2025-on-aml-law-no-10-of-2025/ |
| 监管机构 | CBUAE（金融机构含 PSP）、Ministry of Economy（DNFBP）、UAE FIU（goAML） | https://www.fluxforce.ai/jurisdictions/united-arab-emirates |
| 支付监管 | Retail Payment Services and Card Schemes Regulation（CBUAE Circular 15/2021）：9 类零售支付服务，含 Merchant Acquiring、Payment Aggregation；卡组织须持牌；银行豁免另持牌但须通知。"Merchant acquiring" 定义：PSP 与 Payee 签约受理处理支付交易并向其转移资金。Category III 资本 AED 500k/1M（第三方，待核实） | https://rulebook.centralbank.ae/en/rulebook/retail-payment-services-and-card-schemes-regulation ；https://www.centralbank.ae/media/4came3rh/2021-c-15-2021-retail-payment-services-and-card-schemes-reg_0.pdf |
| CDD 时点 | Art.6：建立关系/开户前或期间核验；低风险可延后。Art.7：建立关系、怀疑犯罪、怀疑资料准确性；**偶发交易 ≥ AED 55,000**（单笔或关联）；**偶发电汇 ≥ AED 3,500** | 134/2025 PDF |
| 自然人必采（Art.9(1)(a)） | 身份证/旅行证件上的姓名、国籍、地址、出生日期与地点、（如适用）雇主名称与地址，并留存有效身份证/旅行证件真实副本 | 同上 |
| 法人必采（Art.9(1)(b)） | 名称、法律形式、MOA、企业所得税纳税人的税务登记号、唯一参考号（如有）；注册地/主要营业地址，外国法人需在阿联酋的法定代表姓名地址及证明；章程；高管姓名；验证代理人授权；了解业务性质与股权控制结构 | 同上 |
| PEP 定义 | 在本国或任何国家担任/曾担任显要公职的自然人（国家元首、政府首脑、高级政客、高级官员含司法军事、国企高管、政党高官、国际组织管理层等）；含**直系亲属**（配偶、子女及其配偶、父母）与**密切关联人**（共同实益拥有法人者、为 PEP 利益设立法人的单独实益人、其他密切职业/社交关系） | 同上（Definitions） |
| PEP 措施（Art.16） | 外国 PEP：风险系统识别、**高管批准**、识别资金与财富来源、强化持续监控；本国 PEP 与国际组织要员：高风险时适用相同措施 | 同上 |
| EDD | 高风险客户：验证客户身份与职业、BO、资金金额、关系目的等；PF（扩散融资）风险客户 EDD 强制 | amluae 指南 |
| 记录保存 | Art.25：交易记录与 CDD 记录、账户文件、往来函件、身份证件副本、STR、分析结果等 **≥5 年**（自交易完成或关系终止起） | 134/2025 PDF |
| 复审周期 | 无固定年限，风险为本；CBUAE 指引 §6.3.6：须按风险类别设定最长复审间隔；身份证件到期为更新触发事件；高风险更频繁 | CBUAE 2021 指引 PDF |
| 地址证明（CBUAE 指引） | 公用事业账单、政府文件、登记的租赁/购房合同、受监管金融机构对账单；无法提供者可用雇主/学校信函等 | 同上 |
| BO 门槛（FI 侧） | ≥25%（CBUAE 指引 §6.3.3 与 Res.134 Art.10）；上市公司/多数股东控股子公司可免采 BO | https://rulebook.centralbank.ae/en/rulebook/guidance-licensed-financial-institutions-providing-services-legal-persons-and-arrangements |

## F. 地址与姓名格式

| 项目 | 事实 | 来源 |
|---|---|---|
| 邮编 | 阿联酋无邮政编码；表单常填 `00000`（或 `99999`）占位 | https://www.edarabia.com/postal-codes-zip-codes-dubai/ |
| PO Box | 官方文件/银行/政府函件主用 Emirates Post PO Box：`P.O. Box 12345, Dubai, UAE` | https://www.melissa.com/global-address-formatting-examples/united-arab-emirates |
| Makani（迪拜） | 迪拜市政 10 位建筑入口码，书写为两组 5 位（示例 `30032 95320`），绑定 GPS，牌匾带 QR；不能替代 PO Box | https://www.mcconeproperties.com/guides/makani-number |
| Onwani（阿布扎比） | DMT 系统：建筑号、街道名、城市、区域、邮政编码五要素（是否真有邮编，来源冲突，待核实）；覆盖阿布扎比市、艾因、Al Dhafra | https://www.edarabia.com/postal-codes-zip-codes-abu-dhabi/ |
| 阿拉伯姓名结构 | 本名-父名-祖父名-家族/部落名；Emirates ID 芯片存储阿/英 Full Name 单字段（最长 126 字符含 6 逗号）；MRZ 只能 A–Z 与 `<`，ICAO 9303 要求非拉丁文字须提供转写，阿语持证人通常可自选拼写；VIZ 与 MRZ 可不同，MRZ 姓名区 39 字符截断 | https://icp.gov.ae/ica_files/vg_downloads/fields_stored_in_uae_id_v2_card_chip.pdf ；https://platform.keesingtechnologies.com/transliteration-of-arabic-in-mrtds/ |
| 姓名不一致风险 | Emirates ID 与护照英文转写不同常导致银行/政府业务受阻 | https://hyring.com/free-hr-toolkit/hr-glossary/emirates-id-uae |

## G. 个人数据保护

| 项目 | 事实 | 来源 |
|---|---|---|
| 联邦 PDPL | Federal Decree-Law 45/2021，2022-01-02 生效；**不适用**于政府数据、健康数据、银行/信贷数据（各有部门法）及 DIFC/ADGM；执行条例截至 2025-01（DLA Piper）及 2026 多数来源仍未发布（一来源称已发布并设 2027-01-01 合规日，**待核实**）；UAE Data Office 未实际运作，2026-06 新设联邦机构接管（待核实） | https://www.dlapiperdataprotection.com/countries/uae-general/law.html ；https://business-ciso.com/blog/uae-pdpl-executive-regulations-status |
| 敏感数据 | 揭示家庭、种族、政治/哲学观点、宗教、犯罪记录、生物特征、健康的数据（不含金融数据） | DLA Piper |
| 跨境 | 允许路径：充分性认定/适当保障（合同）/同意等；无充分性清单；法律本身不要求本地化 | https://trytrustable.com/uae-pdpl |
| DIFC | DP Law No.5/2020（2025 修订，需书面充分性评估后方可用 SCC）；充分性清单 Appendix 3：EU/EEA、UK、瑞士、日本、韩国、加州；**阿联酋大陆不在清单**，DIFC→大陆需 SCC/BCR | https://www.legal500.com/intelligence/united-arab-emirates/corporate-commercial-law/taking-personal-data-across-borders-with-adequacy-decisions |
| ADGM | DPR 2021 Art.41（充分性）/Art.42（适当保障）；清单跟随 EU，大陆与其他自贸区不视为充分 | https://abspartners.ae/cross-border-data-transfers-from-adgm-dpr-2021/ |
| **支付数据本地化** | CBUAE Consumer Protection Standards §6.1.6.3：持牌金融机构须**在阿联酋境内保存全部消费者与交易数据**并异地备份；RPSCS Regulation (2021)："personal and payment data must be stored and maintained in the UAE"；SVF Regulation Art.10 同；Outsourcing Regulation：Master System of Record 须在境内，向境外分享机密数据需 CBUAE 批准+客户书面同意 | https://chambers.com/articles/customer-data-protection-in-the-uae-banking-sector-a-practical-guide-to-the-cbuae-framework ；https://www.cliffordchance.com/insights/resources/blogs/talking-tech/en/articles/2025/03/doing-business-in-the-middle-east--data-transfers-in-the-uae-and-ksa.html |

## H. 典型入驻材料清单

| PSP | 个人/独资 | 机构 | 来源 |
|---|---|---|---|
| **Stripe UAE** | Sole establishment/FZE：UAE 境内有效未过期 trade license 或 freelancer permit；账户个人须为业主（执照仅列经理则经理）；个人银行证明（姓名匹配 Owner Name） | LLC/FZ-LLC：trade license + MOA（显示公司名、全部股东、持股比例）+ 活跃银行账户证明（对账单或 6 个月内银行函，含户名与账号）；多股东 LLC 须公司账户；≥25% 个人股东须加为 Owner 并验证；执照上经理/高管须加为 Controller；法人股东≥25% 需上传其 MOA 并穿透；代表不在 MOA/执照上需 6 个月内 POA；证件：护照（所有人）、Emirates ID（公民/居民）、居留签证（外籍居民）；证件过期后 28 天内更新 | https://support.stripe.com/questions/uae-business-verification-requirements ；https://stripe.com/en-it/docs/connect/required-verification-information |
| **PayTabs UAE** | — | Civil ID（多股东各一）+ 护照副本 + 签证副本；Trade License（匹配网站业务）；MOA（仅多股东）；银行证明（户名+IBAN）；税务文件（如有）；Merchant Application Form | https://support.paytabs.com/en/support/solutions/articles/60000716510 |
| **Telr** | 独资：Trade License、业主护照+签证、Share certificate/LSA 合同/MOA、近 3 个月银行对账单或新户银行函（含 IBAN、账号、户名、银行名、分行、SWIFT） | 合伙/公司：Trade License 全页、全部合伙人护照+签证、MOA 及全部修订、同上银行文件；条款保留要求政府 ID 与 BO 信息权利 | https://telr.com/documents-checklist |
| **Tap Payments** | 通用清单（不分国别）：营业执照/CR、授权签字人姓名、股东清单（CR/MOA/VAT 证书）、公司地址证明（Ejari/账单/对账单）；**≥20%~25%** 个人股东与签字人国民身份证正反面，外籍护照；UBO 与签字人住址；公司银行账户证明（IBAN 证书/对账单，户名须英文且与执照一致）；在线店面（商品、价格、About、T&C、退款、隐私、结账页） | 同左；法人股东≥20–25% 需额外文件 | https://support.tap.company/en/support/solutions/articles/153000242639 |
| **Network International** | POS：trade license、POA（如适用）、授权签字人护照/Emirates ID/签证、近期公司对账单 | 数字支付：trade license、**≥25% 股东、控制人、授权签字人**护照+Emirates ID、章程文件、IBAN 证明（支票复印件+银行函或对账单）、VAT 证书、MOA；签字人不在执照上需 POA | https://www.network.ae/en/faqs ；https://www.network.ae/en/merchant-solutions/in-person-payments/n-genius-pos-terminal |
| Checkout.com | 无公开清单，需客户经理承保（待核实） | — | https://www.skimbox.us/en/resources/blogs/uae-payment-gateway-comparison-telr-stripe-checkout |

---

# 二、沙特阿拉伯（SA）

## A. 自然人标识

| 项目 | 事实 | 来源 |
|---|---|---|
| National ID / Iqama 号 | 均 10 位数字；公民以 `1` 开头，Iqama（居民）以 `2` 开头；Border Number 以 `3`/`4` 开头；全 10 位 Luhn 校验（开源验证器一致实现；样例 1000000008、2000000006 有效，1000000009 Luhn 失败）；一来源称"发证方不强制执行 Luhn"→ 仅作结构校验 | https://github.com/aalgrou/SaudiId-validator ；https://github.com/identique/idnumbers/issues/335 ；https://testpattern.dev/patterns/sa-iqama |
| 国民身份证有效期 | 10 年（仅 wiki 类来源，**待核实**）；Absher 办理续期 | https://grokipedia.com/page/Saudi_Arabian_identity_card |
| Iqama/Muqeem 卡 | 2025 起实体卡 5 年有效，但居留须每年（或 3/6/9/12 月增量）续期（待核实）；居留续期窗口：商业雇佣剩余 <6 个月 | https://blog.wego.com/iqama-renewal/ ；https://landinfo.no/wp-content/uploads/2023/02/Query-response-Saudi-Arabia-ID-documents-for-foreign-nationals-16122022.pdf |
| 卡面语言 | 国民身份证字段为阿拉伯文，仅标题双语；含光学条、芯片、指纹、面像；无 MRZ | https://en.wikipedia.org/wiki/Saudi_Arabian_identity_card |
| 护照号格式 | 未取得官方/可靠来源，**待核实**（题干所称"字母+9 位"未获确认） | https://www.consilium.europa.eu/prado/en/prado-documents/sau/a/d/docs-per-type.html（403 未读） |
| 核验服务 | **Yakeen**（Elm）：对接 NIC 数据，以 ID/Iqama/签证号（+出生日期）核验，返回阿/英全名、性别、出生日期、国籍；SAMA 规则引用为合规来源。**Nafath**：国家 SSO/MFA（SDAIA/NIC），用户在 App 内生物识别确认，状态 waiting/completed/rejected/expired；对机构免费（待核实）。**Absher** 个人/企业门户。SAMA AML 指引 3.9 允许用 NIC 批准的电子认证做远程更新 | https://lenderkit.com/ecosystem/yakeen/ ；https://documentation.azakaw.com/docs/apis/core/nafath ；https://rulebook.sama.gov.sa/en/due-diligence-measures-0 |

## B. 机构标识

| 项目 | 事实 | 来源 |
|---|---|---|
| 新商业登记法 | 2024-10-04 公报，**2025-04-03 生效**；全国统一一个 CR，取消分支登记，现有分支 5 年过渡至 2030-04-03；年度确认取代续期（90 天未确认暂停，待核实） | https://www.spa.gov.sa/en/N2292296 ；https://www.charlesrussellspeechlys.com/en/insights/expert-insights/corporate/2024/new-commercial-register-law-of-saudi-arabia/ |
| CR / 统一国家号 | CR 号 10 位；新法下 CR 号即 Unified National Number，以 **7** 开头（700…）；旧 CR 号按城市前缀（1010 利雅得等）为第三方说法，**待核实** | https://www.daftra.com/en/hub/commercial-registration ；https://box4.dev/en/saudi-arabia/cr-validator |
| 核验 | 商务部免费查询（按名称/统一号，返回名称、统一号、CR 号、状态、资本、签发日、期限、活动；不含股东/经理）；证书 QR；**Wathq API**（developer.wathq.sa）：`/fullinfo/{id}`、`/status/{id}`（status 0 SAR）、Full CR 12 SAR、Basic 5 SAR、Managers 5 SAR、Owners/Partners 5 SAR（价格待核实）；SAMA 2025-04-28 通函列明银行核验渠道：Wathq、MC 网站、证书 QR | https://developer.wathq.sa/en/Pricing ；https://developer.wathq.sa/en/api/31 ；https://companydata.com/company-register/saudi-arabia/ |
| Freelance Work Document | HRSD 经 freelance.sa 签发，有效 1 年，140+ 职业；面向沙特（及 GCC？）国民，外籍不可（来源冲突，待核实）；编号格式未公开（待核实） | https://www-hrsd-gov-sa.translate.goog/en/ministry-services/services/1065686 ；https://noblecoreksa.com/freelance-certificate-license-saudi/ |
| 电商登记 | Maroof 已于 2023-03 并入 business.sa；电商认证接受 CR 或 Freelance Work Document | https://blog.wcart.io/open-online-store-saudi-arabia/ |
| VAT 号 | 15 位，**首位 3、末位 3**（ZATCA e-invoice 规则 BR-KSA-40）；示例 `300000432310003`；第 11 位为 1 表示集团登记（单一来源，待核实）；核验 zatca.gov.sa VAT Taxable Person Lookup | https://www.cleartax.com/sa/ksa-vat-number-verification ；https://lookuptax.com/validate/saudi-arabia/tin |

## C. 组织类型目录与人员角色

| 形态 | 要点 | 人员角色 | 来源 |
|---|---|---|---|
| Companies Law（Royal Decree M/132，2022-06-30，2023-01-19 生效）Art.4 五种形态 | General Partnership、Limited Partnership、JSC、Simplified JSC (SJSC)、LLC | — | https://misa.gov.sa/app/uploads/2025/07/Companies-Law.pdf |
| LLC | 1 人即可设立；50 人上限是否保留来源冲突 | 股东、经理（Manager） | https://corpsec.io/jurisdiction/saudi-arabia/guides/company-types |
| JSC | 最低发行资本 SAR 500,000；董事会 ≥3 人（Art.76） | 董事会、董事长 | 同上 |
| SJSC | 1+ 发起人，无最低资本，不得公开发行 | 董事/经理 | 同上 |
| Establishment（مؤسسة فردية） | 自然人独资，非公司法形态，无限责任 | 业主 | https://alyarubi.com/llc-vs-branch-office-vs-sole-proprietorship-in-saudi-arabia-which-structure-actually-fits-your-business/ |
| Professional company | 非独立形态，由持牌专业人士以 LLC/JSC/合伙设立并满足行业主管要求 | 合伙人 | https://incorp-ksa.com/blog/company-types-possible-in-saudi-arabia/ |
| Branch of foreign company | 无独立法人；MISA 投资许可 + MOC CR | 母公司、分支经理 | 同上 |
| UBO 兜底角色 | 经理 / 董事会成员 / 董事长（见 D） | — | — |

## D. UBO 规则

| 项目 | 事实 | 来源 |
|---|---|---|
| 首版 | 商务部 UBO Rules 2025-02-21 发布，**2025-04-03（5 Shawwal 1446H）生效**；定义：直接/间接 ①≥25% 股本 ②≥25% 投票权 ③可任免多数董事/经理/总裁 ④可影响决策；均无→经理/董事/董事长；变更 15 日历日内通知；年度申报（成立周年前 30 天内）；豁免上市公司、国家全资、破产清算、部令豁免；罚款最高 **SAR 500,000** | https://www.addleshawgoddard.com/en/insights/insights-briefings/2025/commercial-services/update-kingdom-saudi-arabias-newly-adopted-ultimate-beneficial-ownership-rules/ |
| 新版 | Ministerial Decision No. 267，2025-11-26（05/06/1447H），**2026-01-04 生效**（Lexis 引作 99/1447，编号待核实）；三层测试：≥25% 资本 → 其他方式有效控制 → 经理/董事/董事长；登记册存于总部；通过 Saudi Business Center 账户 15 天内更新并说明理由；年度确认；豁免收窄至在沙特上市公司之子公司；罚则：草案 SAR 500（未颁布） | https://www.mondaq.com/saudiarabia/corporate-and-company-law/1723054/new-ultimate-beneficial-owner-ubo-rules-issued ；https://www.lexismiddleeast.com/news/2025-12-09_29/en |
| 登记字段 | 全名、国籍、出生日期与地点、身份证件（非居民护照）、地址、联系方式、所有权/控制性质与程度、成为 UBO 日期 | Lexis |
| 公开性 | MC 2025-12-09 声明：登记册保密，仅监管/主管机关可查，MC 须通知被登记的 UBO；BCLP 称新规删除保密条款并授权向监管及外国机构共享 → **冲突，待核实**；无公开查询 | https://mc.gov.sa/en/mediacenter/News/Pages/09-12-25-01.aspx ；https://www.bclplaw.com/en-US/events-insights-news/saudi-arabia-new-ultimate-beneficial-owner-ubo-rules-issued.html |

## E. AML/CFT 与 KYC

| 项目 | 事实 | 来源 |
|---|---|---|
| 法律 | AML Law（Royal Decree M/20, 1439H/2017）；Art.12 记录保存 **≥10 年**（自关系终止起；检察机关可延长） | https://rulebook.sama.gov.sa/en/anti-money-laundering-law |
| 监管 | SAMA（银行、PSP、金融公司）、CMA、SAFIU（FIU）、Saudi Payments（mada/技术许可） | 同上 |
| SAMA AML/CTF Guide（2019-11-17 版）CDD 3.3 | 自然人：官方证件全名、住宅/国家地址、出生日期地点、国籍、收入来源；法人：名称、法律地位、成立证明、治理权力、**全部经理与高管姓名**、注册地址、营业地（如不同）、收入来源；代理人身份与授权；关系目的；股权控制结构；3.6 无法核验姓名不得开户；3.11 无法完成 CDD 不建立/终止并考虑向 SAFIU 报告 | https://rulebook.sama.gov.sa/en/due-diligence-measures-0 |
| 核验来源 | 3.3(a) 须独立权威来源（政府签发证件）；3.9 NIC 批准的电子认证可远程更新；3.10 其他可靠电子服务 | 同上 |
| 复审 | 3.7–3.8 持续、风险为本，由机构设定频率，高风险更频繁，无固定年限 | 同上 |
| BO 门槛 | SAMA 指引 B 节未取得正文（**待核实**，业界普遍按 25%） | — |
| PEP | 4.9 判断客户/BO 是否 PEP（外国或本国）；4.13 外国 PEP：高管批准、识别财富与资金来源、强化持续监控；本国 PEP 高风险时同样适用；4.14 家属与密切关联人同；4.11 时点：建关系前、交易前、更新信息时、怀疑时 | https://rulebook.sama.gov.sa/en/b-politically-exposed-persons-peps |
| 支付监管 | SAMA Payment Services Provider Regulations（2020，有更新版 PDF）；**Rules for Dealing with E-Commerce Payment Service and Support Providers**（2021，2024 修订）：PSP 须直接与商户签约、登记、匹配并核验商户资料以满足 KYC/AML/CFT/反欺诈；技术对接公司**不得**签约商户、做 KYC 或结算入商户账户，仅需 Saudi Payments 技术许可；银行只能与持牌支付公司合作，发现无牌即报 SAMA；外包须遵守 Rules on Outsourcing 与 mada 要求 | https://rulebook.sama.gov.sa/en/rules-dealing-e-commerce-payment-service-and-support-providers ；https://sama.gov.sa/en-US/payment/Documents/PSPs%20Regulations%20111.pdf |
| 收单 | POS Acquirer 传统为 SPAN 成员银行；Geidea 2021-03 为首家非银行收单牌照；Geidea KSA 条款：商户须为沙特公民/居民且业务在沙特合法授权，须提供授权签字人身份证明 | https://d23r9m22xg868b.cloudfront.net/public/files/KSA-Terms-and-Conditions-CP-CNP-English-Version-(17-08-2025).pdf |

## F. 地址与姓名格式

| 项目 | 事实 | 来源 |
|---|---|---|
| National Address 六要素 | Building Number **4 位**；Street Name；District；City；Postal Code **5 位**；Secondary/Additional Number **4 位**（SPL 英文称 Secondary，Absher/Tawakkalna 称 Additional） | https://splonline.com.sa/en/door-step/ ；https://www.qoyod.com/en/blog/e-invoicing/additional-number-saudi-national-address/ |
| Short Address | **8 字符 = 4 字母 + 4 数字**；字母 = 区域码、分支码、分区码、唯一码；数字 = 建筑号；示例 `RAGI2929` | https://www.upu.int/UPU/media/upu/PostalEntitiesFiles/addressingUnit/sauEn.pdf（2025-04 版） |
| UPU 官方格式 | 行1 收件人；行2 短地址（可选）；行3 建筑号+街道；行4 单元/办公室/信箱（可选）；行5 附加号+区名；行6 邮编；行7 国家。示例：`RAGI2929 / 2929 Rayhanah Bint Zaid / 8118 Al Arid District / 13337 / RIYADH`；PO Box 格式：`P.O. Box 15501 / 21454 / MAKKAH` | 同上 |
| 邮编语义 | 5 位：postal region / sector / branch / section / block | 同上 |
| 姓名 | 本名-父名-祖父名-家族/部落名（可加曾祖父名）；护照英文栏按 first/middle/last 填"本名/父名/姓"以匹配 Iqama；英文转写不唯一（Majid/Majeed/Mejeed） | https://culturalatlas.sbs.com.au/saudi-arabian-culture/saudi-arabian-culture-naming ；https://www.arabnews.com/expats-grapple-differing-names-iqamas-passports |

## G. 个人数据保护

| 项目 | 事实 | 来源 |
|---|---|---|
| PDPL | Royal Decree M/19 (2021)，M/148 (2023) 修订；2023-09-14 生效，宽限至 2024-09-14；监管 SDAIA | https://www.recordinglaw.com/world-laws/world-data-privacy-laws/saudi-arabia-data-privacy-laws/ |
| 跨境（Art.29 + Transfer Regulation 2024-09） | 条件：不损害国家安全/公共秩序、目的地充分保护（SDAIA 认定）、数据最小化、保障措施；机制：充分性（尚无任何认定）、SCC（SDAIA 模板 2024-09-01）、Binding Common Rules、认证、行为准则；例外（Art.6）仅在保障"确实无法适用"时；Art.8 风险评估（依赖保障/例外，或持续大规模涉敏感数据时强制）；Risk Assessment Guideline 2025-02-01 | https://www.recordinglaw.com/world-laws/world-data-privacy-laws/saudi-arabia-data-privacy-laws/data-transfers-and-localization/ ；https://www.dentons.com/en/insights/alerts/2025/may/15/saudi-arabias-framework-for-cross-border-data-transfers |
| 本地化 | PDPL 本身**无**存储境内要求 | 同上 |
| 罚则 | 行政罚款最高 SAR 5,000,000（累犯翻倍）；故意泄露敏感数据刑责最高 2 年 / SAR 3,000,000 | 同上 |
| **金融部门本地化** | SAMA Cyber Security Framework 3.4.3：原则上只用位于沙特的云服务，境外需 SAMA **明确事前批准**（签约前，不以重要性为门槛）；Rules on Outsourcing (Circular 41027017) 重大外包需事前无异议；CMA 规定云须在境内；NCA ECC-2 (2024-10) 取消政府/关键基础设施境内存储要求 | https://rulebook.sama.gov.sa/en/343-cloud-computing-0 ；https://www.cliffordchance.com/insights/resources/blogs/talking-tech/en/articles/2025/03/doing-business-in-the-middle-east--data-transfers-in-the-uae-and-ksa.html |

## H. 典型入驻材料清单

| PSP | 要求 | 来源 |
|---|---|---|
| **PayTabs KSA** | 各股东 Saudi Civil ID；Trade License（匹配网站业务）；MOA（仅多股东）；银行证明（户名+IBAN）；税务文件（如有）；护照仅非居民 | https://support.paytabs.com/en/support/solutions/articles/60000716510 |
| **Tap** | 通用清单同 UAE 节；第三方称接受 Freelance Certificate（待核实） | https://support.tap.company/en/support/solutions/articles/153000242639 ；https://raghdan.sa/en/news/payment-gateways-in-saudi-arabia-guide-2025-requirements-fees-registration-steps-and-comprehensive-comparison/ |
| **Moyasar** | CR 或 Freelance Document + 关联沙特银行账户（来源冲突，待核实） | https://www.ijjad.com/saudi-payment-gateway-comparison |
| **Telr KSA** | 有 /sa-en/documents-checklist 页，内容未读取（待核实） | https://telr.com/documents-checklist |
| **Geidea** | 无公开清单；条款要求授权签字人身份证明、沙特居民/公民、合法授权经营 | 见 E |
| 第三方通用说法 | CR、沙特银行账户（户名与实体一致）、业主/签字人 ID、含价格/条款/退款政策的上线网站 | raghdan.sa |

---

# 三、埃及（EG）

## A. 自然人标识

| 项目 | 事实 | 来源 |
|---|---|---|
| National ID 14 位结构 | 第1位世纪（2=1900–1999，3=2000–2099）；第2–7位 YYMMDD；第8–9位出生登记省份码（迁居不变）；第10–13位序列号，**第13位奇数男/偶数女**；第14位校验位，**算法未公开**（离线只能校验格式） | https://vlenseg.com/en/egyptian-national-id-decoder/ ；https://api.signme.it/blog/egyptian-id-fields-explained |
| 省份码表 | 01 Cairo, 02 Alexandria, 03 Port Said, 04 Suez, 11 Damietta, 12 Dakahlia, 13 Sharqia, 14 Qalyubia, 15 Kafr El Sheikh, 16 Gharbia, 17 Monufia, 18 Beheira, 19 Ismailia, 21 Giza, 22 Beni Suef, 23 Faiyum, 24 Minya, 25 Asyut, 26 Sohag, 27 Qena, 28 Aswan, 29 Luxor, 31 Red Sea, 32 New Valley, 33 Matruh, 34 North Sinai, 35 South Sinai, 88 境外出生；05–10、20、30 未分配 | vlenseg |
| 有效期 | **7 年**（内政部长令）；首次办理须在埃及 CSO，境外领馆只能续期 | https://en.wikipedia.org/wiki/Egyptian_National_Identity_Card ；https://egyptconsulatesydney.com/en/national-id-card-renewal/ |
| 护照 | 有效 ID → 7 年；ID 过期 → 3 年；无 ID → 1 年；护照号格式（题干"A+8 位"）未获确认，**待核实** | https://sis.gov.eg/en/egyptians-abroad-portal/government-services/consular-service/ |
| 税务 UIN | Vertex 称个人需 39 字符 UIN 且每 12 个月更新（待核实） | https://developer.vertexinc.com/vertex-e-commerce/docs/eg-validation |
| 核验 | 无公开 API，权威核验需 Civil Status Organization 记录（通过供应商如 VLens）；CBE 2026-08 数字金融身份（e-KYC）规则已发布、平台未上线（待核实） | https://vlenseg.com/en/egypt-national-id-api/ ；https://www.broadnet.me/blog/egypt-cbe-ekyc-otp-2026 |

## B. 机构标识

| 项目 | 事实 | 来源 |
|---|---|---|
| 商业登记 | 由 ITDA（内贸发展局）下属登记处管理，GAFI 公司在"استثمار القاهرة"等投资登记处；供应部长 2022 称每家企业将有 **15 位**唯一统一号（实施状态待核实）；传统登记号结构未取得可靠来源（待核实） | https://www.youm7.com/story/2022/8/9/%D9%88%D8%B2%D9%8A%D8%B1-%D8%A7%D9%84%D8%AA%D9%85%D9%88%D9%8A%D9%86-%D8%B1%D9%82%D9%85-%D9%85%D9%88%D8%AD%D8%AF-%D8%BA%D9%8A%D8%B1-%D9%85%D9%83%D8%B1%D8%B1-%D9%84%D9%83%D9%84-%D9%85%D9%86%D8%B4%D8%A3%D8%A9-%D8%A8%D9%85%D9%86%D8%B4%D8%A2%D8%AA-%D8%A7%D9%84%D8%B3%D8%AC%D9%84/5865046 ；https://www.itda.gov.eg/addres.aspx |
| 税卡/税务登记号 | **9 位**，印作 `xxx-xxx-xxx`（例 100-492-657），由 ETA 顺序签发，同号用于所有税种含 VAT 及 e-invoice 发行方 ID；无校验位规则 | https://lookuptax.com/docs/tax-identification-number/egypt-tax-id-guide ；https://www.fonoa.com/resources/country-tax-guides/egypt |
| 投资法公司 | GAFI 为公司设立/备案机关；Law 72/2017 不创设新形态，仅叠加优惠于 Law 159/1981 形态 | https://eg.andersen.com/companies-law-159-investment-law-72/ |
| 在线核验 | ETA 网站有注册实体查询页（待核实）；ITDA 提供电子摘录/查询/续期服务；GAFI 无确认的公开搜索；部分来源称 Digital Egypt 可按登记号/税号查 CR（单一来源，待核实） | https://www.businessdataguide.com/blog/jurisdictions/egypt-company-search-guide ；https://www.legalmondo.com/product/how-find-company-information-egypt/ |

## C. 组织类型目录与人员角色

| 形态 | 要点 | 人员角色 | 来源 |
|---|---|---|---|
| JSC (SAE) | Law 159/1981；≥3 股东 | 董事会、董事长、董事总经理 | https://youssrysaleh.com/en/llc-vs-joint-stock-company-in-egypt-which-business-structure-fits-your-plans/ |
| LLC | ≥2 合伙人（可 100% 外资） | 经理（Manager，可多名）、合伙人 | https://www.bylawme.com/post/types-of-companies-egypt-legal-structures-registration-foreign-ownership |
| One-Person Company | 2018 修法新增；单一自然人/法人 100%；最低资本 EGP 1,000；不得公开发行/从事银行保险 | 业主、经理 | https://almasriagroup.com/2025/08/17/one-person-company-in-egypt-a-comprehensive-guide-for-entrepreneurs/ |
| Partnership limited by shares | Law 159 | 无限责任合伙人、股东 | Andersen |
| General / Limited Partnership | 商法典；普通合伙人无限责任 | 普通合伙人、有限合伙人 | bylawme |
| Sole Proprietorship | 非公司，个人经营，在当地商业登记处（非 GAFI）登记，无限责任；最低资本来源冲突（EGP 1,000 vs 100,000，待核实）；外籍需居留许可 | 业主 | https://www.usemultiplier.com/egypt/sole-proprietorship |
| Branch / Rep office | 经 GAFI，需安全审查 | 母公司、分支经理 | https://ig-bs.com/articles/company-types-egypt |

## D. UBO 规则

| 项目 | 事实 | 来源 |
|---|---|---|
| 法规 | MoSIT Ministerial Decree 41/2020（2020-03-01 发布，03-08 公报，**03-09 生效**），修订 Commercial Registry Law 34/1976 执行条例 | https://www.legal500.com/intelligence/egypt/corporate-commercial-law/new-legal-requirement-for-maintaining-a-beneficial-owners-register-in-egypt |
| 义务人 | 所有在商业登记处登记的自然人与法人 | 同上 |
| 登记字段 | 实益拥有人姓名、国籍、埃及人国民 ID 号 / 外国人护照号 / 法人登记号；变更"立即"更新并通知登记处；保存至注销后 **5 年** | 同上；https://adsero.me/ultimate-benificial-owner-disclosure-requirments-in-egyptian-companies/ |
| 门槛 | 法令**未定义** BO；援引 AML Law 80/2002 定义"最终拥有或控制的自然人"；**25%** 为部分法律评论说法，未在法条确认（**待核实**） | https://www.lexology.com/library/detail.aspx?g=ac3294e4-36a9-4ec7-9e24-fadf974906f6 |
| 公开性 | CR 摘录新增 UBO 栏 → 可通过摘录获取（Lexology）；但 Global Database 列为无公开 UBO 登记；电子登记尚未全国实施（冲突，待核实） | https://www.globaldatabase.com/beneficial-ownership-registers-which-countries-publish-ubo-data |
| 罚则 | 适用一般罚则（基准极低，EGP 10 起，累犯翻倍，待核实） | adsero |

## E. AML/CFT 与 KYC

| 项目 | 事实 | 来源 |
|---|---|---|
| 法律 | AML Law 80/2002；执行条例 PM Decree 951/2003，经 457/2020、**3331/2023** 修订（对齐 FATF，扩展至房产经纪、≥USD 15,000 现金的贵金属商） | https://www.fatf-gafi.org/content/dam/fatf-gafi/fsrb-mer/MENAFATF-MER-Egypt-2021-English.pdf ；https://www.glaco.com/blog/egypts-latest-developments-in-anti-money-laundry-regulations/ |
| 监管 | EMLCU（FIU，独立单位）、CBE（银行与 PSP）、FRA（非银行金融） | 同上 |
| CDD/PEP/记录 | 记录保存 ≥5 年（多个二手来源，条款号待核实）；PEP 需 EDD（法定定义未取得，待核实）；CDD 需识别 BO | https://www.signzy.com/regulation-glossary/anti-money-laundering-law-80 |
| CBE 法 | Law 194/2020（中央银行与银行业法）为 PSP 许可依据 | https://www.lexismiddleeast.com/news/2025-06-20_38/en |
| CBE 2019 支付促进者规则 | 银行与 PSP 签约需 CBE 批准；银行须监督 PSP 的**商户选择**，禁止虚拟货币、博彩、众筹；Payment Facilitator（以自己名义收款）须在埃及有实体办公室并登记于商业登记、有地址与网站；银行须收取相当于 **3 日收款额**的担保；独立账户；最迟 **3 个工作日**内转付商户；以本币结算 | https://www.lexology.com/library/detail.aspx?g=94d0181d-e322-4cb1-aea5-fad1186012dc ；https://www.ibanet.org/article/6FC3317B-4F9C-4DFC-BDF1-80CF98B68B21 |
| CBE 2025 PSP/PSO 许可规则 | 2025-06 发布（一说 2025-06-17 生效），12 个月过渡至 2026-06；PSP Cat A（月均 >EGP 7.5 亿）资本 EGP 3,000 万；Cat B EGP 1,000 万；PSO EGP 50 万；AIS/PIS EGP 2,000 万；申请人须为专营支付的埃及 JSC，披露全部股权与 UBO，与银行合作；许可活动含现金处理、支付执行、支付工具发行、**数字受理**、本币汇款、账户信息 | https://shehatalaw.com/law-update/cbe-regulations-licensing-payment-operators/ ；https://www.dailynewsegypt.com/2025/06/21/cbe-issues-new-licensing-framework-for-electronic-payment-service-providers/ |

## F. 地址与姓名格式

| 项目 | 事实 | 来源 |
|---|---|---|
| 邮编 | **5 位**；一说首位省份、第二位邮区、第三位地区、末两位邮局；另一说前两位省份（11 开罗、21 亚历山大）（冲突，待核实）；Egypt Post 实际依赖省/市/区路由，邮编使用不一致 | https://www.geopostcodes.com/country/egypt/address-format/ ；https://www.postgrid.com/egypt-post-address-validation/ |
| 行序 | 收件人 / 楼号+街道 / 区 / 城市 / **省（必须）** / 邮编 / EGYPT | https://addressguard.io/address-format/egypt/ |
| 姓名 | 本名-父名-祖父名(-家族名)；国民身份证为阿拉伯文（待核实无英文）；护照含 MRZ 转写 | https://certtranslate.com/cases/egyptian-birth-certificate-encoded-national-id |

## G. 个人数据保护

| 项目 | 事实 | 来源 |
|---|---|---|
| 法律 | PDPL Law 151/2020；执行条例 **Ministerial Decree 816/2025**，2025-11-01 签发、约 11-10 公报并次日生效；1 年合规期至约 2026-11-01（待核实） | https://www.tamimi.com/law_update_articles/from-policy-to-practice-egypt-issues-executive-regulations-of-the-personal-data-protection-law/ ；https://id.com.eg/overview-of-the-executive-regulations-of-the-egyptian-personal-data-protection-law/ |
| 监管 | Personal Data Protection Centre (PDPC) | 同上 |
| 敏感数据 | 法律第 1 条定义含健康、生物特征、**财务数据**、宗教、政治观点、安全状态、儿童数据（本次未读原文，待核实） | https://eg.andersen.com/wp-content/uploads/2025/06/Law-No.-151-OF-2020-1.pdf |
| **跨境** | 任何跨境传输须：PDPC **许可/许可证** + 数据主体**同意**（条例 Art.16）；目的地充分性由 PDPC 认定；非充分国仅在明示同意且属列举情形（生命健康、法律主张、为数据主体利益的合同等） | id.com.eg |
| 许可费 | 控制者/处理者合并许可按记录数分级：1–10,000 条免费；10,001–200,000 条 EGP 200；…至 500 万条 EGP 500,000；超 500 万条上限 EGP 2,000,000/年（3 年）；单角色许可减半；临时许可 ≤1 年 | id.com.eg |
| 其他 | 违规通知 72 小时；无本地存在的外国控制者须指定本地代表；许可申请须列明 DPO | 同上 |
| 支付数据本地化 | 二手来源冲突：Lexology 称 2025 CBE 规则含数据本地化；合规追踪器称仅要求披露云数据位置；Baker McKenzie（旧）称托管客户数据的外包商须向 CBE 登记且 CBE 不接受境外登记 → **待核实** | https://www.lexology.com/pro/content/egypts-digital-payment-overhaul-new-licensing-brings-regulatory-clarity-and-opportunity ；https://resourcehub.bakermckenzie.com/en/resources/cloud-compliance-center/emea/egypt |

## H. 典型入驻材料清单

| PSP | 个人/自由职业 | 机构 | 来源 |
|---|---|---|---|
| **PayTabs Egypt** | Freelancers：业主 ID 或护照 + 银行对账单/账户证明；律所/工程所/诊所：Work permit + 税卡 + 业主 ID/护照 + 银行证明 | 多股东公司：商业登记、税卡、MOA（多股东 LLC）、**≥25% 股东**国民 ID 或公司文件、银行证明；单一股东公司：商业登记、税卡、业主 ID/护照、银行证明 | https://support.paytabs.com/en/support/solutions/articles/60000716510 |
| **Paymob** | 第三方：国民 ID（14 位，正反面）、税卡、埃及银行账户；有的要求账单/租约地址证明、手持 ID 自拍 | 第三方：商业登记（GAFI 签发，阿语或认证译本）、税卡、业主 ID、章程、地址证明、网站政策；审批 2–5 个工作日（待核实） | https://xpay.app/blog/documents-needed-payment-processing-egypt ；https://road9media.com/blog/well-known-payment-gateways-in-egypt |
| **Fawry** | 仅面向埃及境内公司（待核实） | 商业登记、税 ID；SME 在线表单，约 2 日回复 | https://www.fawry.com/sme-registration-form/ ；https://developer.fawrystaging.com/docs/get-started |
| **Geidea Egypt** | 无公开清单 | — | https://qodebites.com/integrations/geidea/ |
| 通用提示 | 商业登记、税卡、银行户名三者名称须一致，否则人工审核；文件需阿语或认证翻译 | https://payatlas.com/countries/egypt-eg |

---

# 四、三国对照表

| 维度 | 阿联酋 AE | 沙特 SA | 埃及 EG |
|---|---|---|---|
| 自然人主证件 | Emirates ID 15 位 `784-YYYY-NNNNNNN-C`，末位校验（Luhn 待核实）；公民 5/10 年，居民随签证 | National ID / Iqama 10 位，首位 1（公民）/2（居民），Luhn；ID 10 年（待核实），Iqama 卡 5 年+年度续居留 | National ID 14 位 `C YYMMDD GG SSSS(G) K`，第 13 位性别，校验算法未公开；7 年 |
| 护照号 | 9 位字母数字（Purview，待核实） | 待核实 | 待核实 |
| 机构主标识 | 各酋长国/自贸区 Trade License（格式未公开）、NER Unified Economic ID、DUL | CR 号 10 位 = 统一国家号，7 开头（2025-04-03 新法） | 商业登记号（15 位统一号规划中，待核实）+ 税卡 9 位 |
| 增值税号 | TRN 15 位（100 开头待核实） | 15 位，首尾均为 3 | 与税务登记号同，9 位 |
| 机构在线核验 | NER、invest.dubai.ae、各自贸区登记处、FTA TRN 查询 | MC 免费查询、证书 QR、Wathq API（owners/managers 付费）、ZATCA VAT 查询 | ETA 注册实体页（待核实）、ITDA 电子摘录；无确认的免费公开搜索 |
| 个人在线核验 | ICP Validation Gateway（需审批）、UAE PASS | Yakeen（Elm/NIC）、Nafath、Absher、National Address API | 无公开 API（CSO 记录，经供应商）；CBE 数字身份 2026（待上线） |
| UBO 门槛 | ≥25% 资本/投票 → 其他控制 → 高管（Cab.Dec.109/2023） | ≥25% 资本（首版另含≥25% 投票、任免权、影响力）→ 其他控制 → 经理/董事/董事长（MD 267，2026-01-04） | 法令未定义；AML 法"最终拥有或控制"；25% 待核实 |
| UBO 登记/查询 | 向登记机关提交（60 天），变更 15 天；保密 | MC 登记（Saudi Business Center），变更 15 天，年度确认；保密（新规是否解除冲突待核实） | 企业自持登记册并通知登记处；CR 摘录含 UBO 栏（公开性冲突） |
| AML 记录保存 | ≥5 年（Cab.Res.134/2025 Art.25） | ≥10 年（AML Law Art.12） | ≥5 年（待核实条款） |
| 偶发交易 CDD 阈值 | AED 55,000；电汇 AED 3,500 | 未取得 | 未取得 |
| PEP | 外国 PEP 强制高管批准+资金财富来源+强化监控；本国/国际组织高风险时 | 外国 PEP 同；本国 PEP 高风险时 | EDD 强制（定义待核实） |
| 收单/PSP 监管 | CBUAE RPSCS Regulation 2021（9 类含 Merchant Acquiring） | SAMA PSP Regulations + E-Commerce PSP Rules（PSP 必须自行签约并 KYC 商户） | CBE 2019 PF/聚合规则（银行监督商户选择；3 日结算；3 日担保）+ 2025 PSP/PSO 许可规则（Cat A/B） |
| 个人商户必交（主流 PSP） | 境内 trade license 或 freelancer permit、护照+Emirates ID+签证、个人银行证明 | Civil ID、CR 或 Freelance Document（部分 PSP）、IBAN 证明 | 国民 ID 14 位正反面、税卡、埃及银行账户（Freelancer 在 PayTabs 可仅 ID+银行） |
| 机构商户必交 | Trade license、MOA（股东与持股）、≥25% 股东与经理/签字人 ID+护照、公司银行证明/IBAN、VAT 证书、POA | CR、MOA（多股东）、股东 Civil ID、IBAN 证明、税务文件 | 商业登记、税卡、MOA、≥25% 股东 ID、银行证明、章程、地址证明 |
| 地址格式 | 无邮编；PO Box；Makani 10 位（迪拜）；Onwani（阿布扎比） | 建筑号 4 位 + 街道 + 附加号 4 位 + 区 + 邮编 5 位 + 城市；短地址 4 字母+4 数字 | 楼号+街道、区、市、省、邮编 5 位 |
| 姓名 | 本名-父名-祖父名-家族名；ID 芯片阿/英 Full Name 单字段 126 字符 | 四段名；护照 first/middle/last 匹配 Iqama | 四段名；ID 阿文 |
| 数据保护法 | PDPL 45/2021（执行条例未发，待核实）+ DIFC Law 5/2020 + ADGM DPR 2021 | PDPL 2023-09-14 生效，宽限至 2024-09-14；Transfer Regulation 2024-09 | Law 151/2020 + 执行条例 816/2025（2025-11 生效，1 年合规） |
| 跨境传输 | 充分性/合同/同意（清单未发）；DIFC/ADGM 自有清单，大陆不充分 | 充分性（尚无）、SCC、BCR、认证；Art.8 风险评估 | PDPC 许可 + 数据主体同意（每次传输） |
| 支付/金融数据本地化 | **强**：CBUAE Consumer Protection Standards 6.1.6.3、RPSCS Regulation、SVF Reg Art.10、Outsourcing Reg——消费者与交易数据须存于 UAE | **强（审批制）**：SAMA CSF 3.4.3 云默认境内，境外需 SAMA 事前明确批准；PDPL 本身无本地化 | **不明**：CBE 2025 规则是否含本地化来源冲突；旧规外包托管商须向 CBE 登记（待核实） |

**主要未决/待核实项汇总：** Emirates ID 校验算法；TRN 前缀；UAE PDPL 执行条例状态与 Data Office 继任机构；Cab.Dec.109/2023 生效日与罚则；沙特护照号格式、国民 ID 有效期、旧 CR 前缀、Freelance Document 编号、UBO 登记册保密条款（MD 267）、SAMA BO 门槛原文；埃及护照号格式、商业登记号结构、UBO 25% 法源、AML 记录条款号、PEP 法定定义、CBE 2025 规则本地化条款、PDPL 敏感数据原文。

---

## 附：本次直接读取的原始文本（已转为纯文本，供复核）

- Cabinet Resolution 134/2025（UAE FIU PDF 提取）：`/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/01ea5128-6d5a-487b-a761-584136eae2a1/scratchpad/cab134.txt`
- CBUAE AML/CFT Guidelines for FIs（2021-06）：`/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/01ea5128-6d5a-487b-a761-584136eae2a1/scratchpad/cbuae_aml_guide.txt`
- UPU 沙特地址格式（2025-04）：`/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/01ea5128-6d5a-487b-a761-584136eae2a1/scratchpad/upu_sau.txt`
