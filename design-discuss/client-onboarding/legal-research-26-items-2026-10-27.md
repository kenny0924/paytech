# Client Onboarding：AE / SA / EG 法务问题核查（26 项）

- 核查基准日：2026-10-27。
- 文档性质：本次对话法源研究答复的 Markdown 导出，供设计评审参考；不是已批准的设计决定、当地律师签署的法律意见或上线批准。
- 对应清单：[design.md](./design.md) 第 9.5a 节。
- 证据口径：优先采用监管机构、政府法源和卡组织公开规则；只能查到历史材料或二手解释的地方明确标注。链接指向外部现行页面，后续内容可能变化。

**卡组织登记的 PayFac 身份，不等于当地金融监管牌照。** 判断谁负责 KYC、谁可以审批、谁需要数据本地化，必须对应实际法人、牌照及收单关系。以下可作为设计评审依据，但涉及具体业务许可和外包安排的结论，仍需当地律师结合合同与操作流程出具意见。

**1. AE：Emirates ID 是否采用 Luhn 校验？**

**目前不能确认，不能把 Luhn 不通过作为拒绝真实 Emirates ID 的依据。**

官方资料确认号码为 15 位、具有唯一性；但本次未找到 ICP 正式公布的 Luhn 算法、校验参数和权威测试样例。网上代码、身份核验供应商文档或 Microsoft 的敏感信息识别规则，都不能代替签发机关的号码规范。

另外，官方明确说明：号码中的四位数字**不必然等于出生年份**，因此也不能据此校验生日。建议只做已确认的格式检查，真实性和有效性通过获准的官方核验渠道确认。[阿联酋通讯社转述身份管理局说明](https://www.wam.ae/en/article/hszjujia-emirates-id-the-number-may-not-amended-and-does)

**2. AE：外籍居民 Emirates ID 有效期**

**应跟随实际居留类别和签发记录，不能统一写成两年或三年。**

普通居留、长期居留的期限不同；ICP 的服务流程将居留与 Emirates ID 申请关联。系统应分别保留证件到期日与居留状态，并识别注销、取消等事件，不能仅以实体卡尚未到期推定居留持续有效。[ICP 常见问题](https://icp.gov.ae/en/faq/)、[ICP 居留与身份证关联说明](https://icp.gov.ae/en/media-center/residence-and-id-card-procedures-to-be-linked-in-sharjah-from-december/)

实施建议：读取证面及官方核验结果，不根据签发日自行推算有效期；行政宽限期也不要直接解释为证件继续有效。

**3. AE：Trade License 格式、签发机关及 NER 统一标识**

**不存在本次能够确认的、覆盖所有酋长国和自贸区的统一许可证号码格式。**

本土经济许可由各地主管机关签发，自贸区另有注册及许可机关。“注册号”“许可证号”“分支机构号”也可能不是同一个编号，不能只存一个无类型的 `registrationNumber`。

经济部已推出 Growth 国家经济登记系统，使用统一经济登记编号 ERN，并整合地方及自贸区登记信息。**ERN 可以作为统一关联标识，但不能替代许可证本身的活动范围、有效状态和签发机关核验。**[经济部 Growth 上线公告](https://www.moet.gov.ae/en/-/ministry-of-economy-launches-national-economic-registry-growth-to-support-the-uae-s-efforts-in-providing-proactive-government-services)

建议至少区分：签发机关、号码类型、原始号码、许可证状态、许可活动及 ERN。各机关的完整格式目录仍需取得官方接口规范，不能根据少量样本制定强校验。

**4. AE：TRN 是否固定以 100 开头？**

**官方可确认的是 15 位 TRN；本次没有找到“所有 TRN 必须以 100 开头”的普遍规则。**

因此，`100` 不应成为硬性前缀。核验还应包括登记主体、税种及状态，而不只是位数。[FTA 官方服务中的 TRN 说明](https://tax.gov.ae/en/services/vat.and.excise.tax.refunds.for.missions.diplomatic.bodies.and.international.organizations.aspx)

还需区分 VAT 登记与企业税登记。VAT 税组、同一主体的多个经营登记可能使用共同税务身份，TRN 不适合直接作为所有法律主体的唯一主键。[FTA VAT 登记说明](https://tax.gov.ae/en/services/vat.registration.aspx)

**5. AE：Cabinet Decision 109/2023 生效、罚则与 UBO 登记核对**

**109/2023 自 2023 年 11 月 16 日生效；行政处罚另见 132/2023。**[CBUAE 官方法源状态](https://rulebook.centralbank.ae/en/rulebook/article-3-scope-application-8)

罚款按具体违法行为区分。以“未建立并维护 UBO 登记册”为例，处罚表采用书面通知、再次违反 AED 50,000、第三次 AED 100,000 的递进安排；不能概括为任何 UBO 违规都直接罚十万元。[132/2023 官方处罚表](https://www.uaelegislation.gov.ae/en/legislations/2314/download)

对于商户尽调，应区分：

- 商户履行公司法上的 UBO 登记义务；
- 金融机构履行独立识别和核验 UBO 的义务。

登记回执、登记册摘录可以作为证据，但不能代替股权链和实际控制核验。本次未确认存在一项要求所有 PayFac 对所有商户均通过同一个公开登记库查询的普遍义务；具体查询渠道及证据要求应由承担 CDD 责任的机构确定。[CBUAE CDD 指引](https://rulebook.centralbank.ae/en/rulebook/guidance-licensed-financial-institutions-customer-due-diligenceknow-your-customer-and)

**6. AE：是否必须经 ICP 在线核验，义务及申请主体是谁？**

**监管指引的直接对象是持牌金融机构 LFI，不能直接扩展为所有技术平台。**

较早指引有强制性措辞；较新的 CDD 指引表述为 LFI 核验 Emirates ID 时应使用 ICP Validation Gateway，并保留证件副本和数字核验凭证。因此，不宜把要求降格为“上传身份证照片即可”。[CBUAE CDD 指引](https://rulebook.centralbank.ae/en/rulebook/guidance-licensed-financial-institutions-customer-due-diligenceknow-your-customer-and)

具体分工应是：承担受监管客户关系的机构负责合规结果；平台可以在获准的外包及数据处理安排下提供技术连接。**网关由谁签约、使用谁的凭证及允许处理哪些客户，取决于 ICP 的准入和服务协议**，没有依据认定必然由平台或必然由 PayFac 单独申请。[ICP Validation Gateway](https://icp.gov.ae/en/icp-validation-gateway/)

**7. AE：134/2025 Article 9 是否就是最小采集集？外国 PEP 谁批准？**

**Article 9 是基础要求，不是完整尽调清单，也不是允许收集数据的上限。**该决议自 2025 年 12 月 14 日生效。

Article 9 涵盖自然人的身份、国籍、地址、出生日期及地点、适用时的雇主信息；法人涉及法律形式、设立文件、注册地址、高管及适用的税务登记信息。尤其注意：其中税号要求涉及**受企业税约束的法人**，不能直接替换为“所有公司必须有 VAT 号”。授权代表、业务目的、所有权及控制结构也在其要求内。[Article 9 原文](https://rulebook.centralbank.ae/en/rulebook/article-9-23)

外国 PEP 根据 Article 16，建立或继续关系前需要高级管理层批准，并采取财富及资金来源核实、强化持续监控措施。**批准应归属于承担该法定义务的机构的高级管理层**；PayFac 本身属于该义务主体时，才可简化为“PayFac 高管批准”，普通平台管理员不能替代。[134/2025 Article 16](https://rulebook.centralbank.ae/en/entiresection/43)

**8. AE：CPS、RPSCS 本地化是否适用于技术平台？**

**不能因平台自称技术服务商，就认定可以把受监管数据放到境外。**

CPS 对 LFI 的消费者及交易数据提出境内保存要求；RPSCS Article 14 对个人和支付数据的境内保存、维护及备份也有明确要求。[CPS Article 6](https://rulebook.centralbank.ae/en/rulebook/article-6-protection-consumer-data-and-assets)、[RPSCS Article 14](https://rulebook.centralbank.ae/en/rulebook/article-14-obligations-towards-retail-payment-service-users)

平台即使不是直接持牌主体，仍可能通过外包合同承担这些要求。RPSCS 的外包规则涵盖第三方及集团内实体，持牌机构继续承担责任，并涉及事先监管批准。[RPSCS 外包规定](https://rulebook.centralbank.ae/en/entiresection/1616)

应分别审查主库、备份、日志、证据文件、境外运维访问和跨境处理；“服务器在 UAE”本身不足以完成全部判断。

**9. AE：PDPL 执行条例状态，DIFC／ADGM 是否需独立条款？**

**截至本次核查，尚未取得足以确认联邦 PDPL 执行条例已正式公布的官方文本。**这不等于联邦 PDPL 不存在或可以不处理其要求。联邦法已经颁布，其中的适用范围、排除项和后续执行安排需要分开理解。[联邦 PDPL 官方文本](https://www.uaelegislation.gov.ae/en/legislations/1972/download)

DIFC 和 ADGM 有各自的数据保护制度，包括处理者合同和数据出区安排。因此，适用这些制度的处理活动需要对应条款；可以通过主 DPA 加地区附录实现，未必必须签三份独立合同。是否适用取决于实际处理活动和主体角色，不能只看客户注册地。[DIFC 数据输出指引](https://www.difc.com/business/registrars-and-commissioners/commissioner-of-data-protection/data-export-and-sharing)、[ADGM 国际传输指引](https://www.adgm.com/documents/office-of-data-protection/guidance/adgm-dpr-2021-guidance-part-6.pdf)

**10. SA：平台提供系统、PayFac 决定，是否符合技术公司限制？**

**有可能成立，但“PayFac 最后点击批准”不足以使平台实际执行 KYC 的安排合规。**

SAMA 2024 年 7 月 24 日现行规则将相关技术公司的范围限制在技术连接和支持，并排除直接商户签约、注册及为 KYC／AML 等目的开展的商户信息核验，以及最终结算活动；同时要求相关技术许可和外包合规。[SAMA 原文](https://rulebook.sama.gov.sa/en/rules-dealing-e-commerce-payment-service-and-support-providers)

据此，本次法律分析是：系统展示资料、提供工具，由获准机构实际控制尽调政策、执行核验和作出决定，有较清晰的合规基础；平台人员实质审查证据、解决命中、判断风险，再让 PayFac 形式签字，则存在明显问题。

当前设计中的“平台 Operations 代全部 PayFac 操作、用 `on_behalf_of_partner_id` 标记”，**不能仅凭该标记认定责任已经合法转移**。关联公司关系也不自动消除主体边界。此项应由沙特持牌 PSP／收单银行结合实际岗位、合同和流程确认，必要时取得 SAMA 意见。

**11. SA：新 CR 号码、旧前缀及“执照到期”口径**

**应把“CR 到期续期”改为“年度确认及登记状态”。**

2025 年 4 月 3 日实施的新制度取消 CR 到期日，改为年度确认；逾期达到规定期限会暂停，持续未纠正可被注销。其他行业许可证仍可能有自己的到期日，不能一并取消。[沙特商务部新制度说明](https://mc.gov.sa/ar/mediacenter/News/Pages/03-04-25-01.aspx)、[年度确认说明](https://mc.gov.sa/en/mediacenter/News/Pages/06-07-25-03.aspx)

新证书采用 **7 开头的统一国家编号**作为主要且唯一的企业号码；官方查询指南说明其为 10 位。旧 CR 应保留用于历史映射，不宜以若干旧地区前缀建立封闭白名单。[商务部通知转发](https://www.chamber.org.sa/sites/Arabic/InformationsCenter/Memos/Pages/MemosDetails.aspx?Title=vfv65)、[官方查询指南](https://mc.gov.sa/DOC/Mcsbc10.pdf)

**12. SA：UBO 规则编号、保密／共享与是否核对登记**

**需要更新原清单：现行商务部规则是第 99 号决定，取代第 235 号决定；不能继续把 MD 267 当作当前权威。**[商务部 2025 年 12 月公告](https://mc.gov.sa/en/mediacenter/News/Pages/08-12-25-01.aspx)

更重要的是，**SAMA 第 472047799 号通函自 2026 年 3 月 9 日生效，要求相关受监管金融机构技术接入 Wathq，核验 UBO；发现未披露 UBO 或登记差异时，通过平台通知商务部，并遵守可疑交易报告及数据保护要求。**[SAMA 通函及现行状态](https://rulebook.sama.gov.sa/en/entiresection/9574)、[通函正文](https://rulebook.sama.gov.sa/ar/الربط-التقني-مع-خدمة-واثق-للتحقق-من-هوية-المستفيد-الحقيقي)

因此，“登记信息不是公开查询资料”不能推导为“不需要核验”。平台应通过有资格的金融机构及其获准接入安排处理，不能自行假设拥有全量登记库访问权。

**13. SA：SAMA BO 门槛原文**

**可以确认：25% 或以上，而非严格大于 25%。**

SAMA AML 指引第 3.18 段要求识别直接或间接拥有、控制法人 **25% 或以上**权益的自然人，并采取合理措施核验。但达到比例的人未必是真正最终控制人；没有达到该比例者，也不意味着没有 UBO。

第 3.19 段进一步要求，不能主要依靠客户书面声明确定 UBO，应理解和核验所有权、控制结构。[SAMA 指引第 3.18—3.19 段](https://rulebook.sama.gov.sa/ar/entiresection/1362)

因此，比例筛选、其他控制识别和无法识别时的管理层回退，应保留不同法律依据，不能混成一个“持股超过 25%”字段。

**14. SA：国民身份证及 Iqama 到期触发**

国民身份证通常按年龄档适用 **5 年、10 年、20 年**有效期：15—30 岁、31—50 岁、51 岁以上。实际判断仍应采用证件及官方核验的到期日，不自行重算。[沙特官方百科说明](https://saudipedia.com/en/national-identity-card)

**Iqama 的实体卡使用期限与居留许可期限必须分开。**“卡片可使用五年”不能证明居留五年均有效；“必须每年续”也不能作为全部类别的统一规则，现有官方服务支持从三个月起的续期安排。[Absher 续期服务](https://www.absher.sa/wps/portal/individuals/static/guide/service-contents/%21ut/p/z0/fYyxCsIwFEV_pUtHeS9Ci46FSkWLi6A1SwlNKE9LkiZp8PMN4iAObuccLhc4dMC1iDSKQEaLKfmNlz1j9Wa_brDFU11hecRid9m2DFkBZ-HgAPz_KL3QfZ55BXwwOqhngO4NOvgcSUuKJBcxJVFeuUiD8tmSKBsXkuqr5miF99a4sPpJLvj-kwqwj-b6AlSVI6c%21/?presentationTemplate=service+detail)

实施上，应以官方居留有效状态和实际到期日触发复核，而不是卡片签发日加五年。

**15. SA：Freelance Work Document 格式及个人商户资格**

官方平台说明，文件统一编号使用 **`FL-` 前缀加唯一序列**，文件有效期为一年。本次没有确认后缀固定长度，不建议按旧样本硬编码。[自由职业官方平台 FAQ](https://freelance.sa/faqs)、[阿拉伯语 FAQ](https://freelance.sa/ar/faqs)

**可以在其覆盖的活动范围内作为个人经营资格，但不是通用商业牌照。**商务部的电商认证安排接受有效 CR 或有效自由职业文件，并要求相关商业银行账户。仍须核对人员资格、文件活动与实际经营是否一致，以及收单机构是否接受该类型业务。[商务部电商认证说明](https://mc.gov.sa/ar/mediacenter/News/Pages/29-03-23-02.aspx)

**16. SA：CSF 3.4.3 境外云审批是否适用于平台／PayFac？**

**直接适用对象是受框架约束的机构；其外包技术平台会受到合同及监管要求的传导。**

CSF 3.4.3 要求相关云服务使用或签约前取得 SAMA 批准，并以沙特境内云服务为原则，境外使用需要明确批准。该节对云部署模式还有适用限定，不能将所有托管服务机械视为相同情形。[SAMA CSF 3.4.3](https://rulebook.sama.gov.sa/en/343-cloud-computing-0)

实际审批应由适用该要求的持牌机构牵头；平台提供部署、分包商、数据流、访问控制及退出方案。PDPL 跨境机制和 SAMA 云／外包审批是不同要求，完成其中一个不代表另一个自动满足。

**17. SA：AML 十年留存如何覆盖证据与案件？**

**不能只保留最终决定，也不能统一从案件关闭日算十年。**

AML Law Article 12 分别规定交易记录与 CDD、账户文件、业务往来和分析资料的保存期限及起算点，涉及交易完成、账户关闭、业务关系终止等不同事件；主管机关可以要求更长保存。[沙特 AML Law Article 12](https://www.rulebook.sama.gov.sa/en/article-12-24)

对本系统，建议保存足以重建决策的证据版本、查询结果、名单命中处理、风险理由及审批记录，并分别设置关系终止、交易结束和法律保全触发。被拒申请的期限需要另定政策，不能未经分析套用所有客户的同一起算方式。

**18. EG：UBO 的 25% 是否只有评论依据？**

**不是纯粹的市场评论，但本次证据不足以把它表述为“所有埃及主体统一适用的法定门槛”。**

MENAFATF 对埃及的官方评估第 644 段记录了 GAFI 使用 **25% 或以上资本权益**识别控制人的实践；同一报告又指出，相关商业登记部长决定缺少完整 BO 定义。这是两个不同层面的事实。[MENAFATF 埃及评估，正文第 160 页及 Recommendation 24](https://www.fatf-gafi.org/content/dam/fatf-gafi/fsrb-mer/MENAFATF-MER-Egypt-2021-English.pdf)

可把 25% 作为有官方依据的候选规则，但生产规则仍需与承担责任的银行／PSP 当前 CDD 文件对应，并保留其他控制方式及穿透核验。**本项仍需当前适用规范的书面确认。**

**19. EG：商业登记号、15 位统一号及税号／VAT**

**15 位统一商业登记号已经实施，不能再写成仅处于规划阶段。**

2024 年公布的主管部门说明称，统一号码服务已经覆盖登记企业，用于关联企业及其分支。该事实有埃及国家媒体对主管部门公告的报道支持；它不意味着旧登记摘录中的本地登记号已经全部消失。[主管部门公告报道](https://akhbarelyom.com/news/newdetails/4343297/1/%D8%A7%D9%84%D8%AA%D9%85%D9%88%D9%8A%D9%86)

税务机关的电子票据规范确认企业税务登记号为 **9 位**。但“有税卡／税号”不能证明已经登记 VAT；应另核 VAT 证书、登记状态和生效信息。统一商业登记号、传统 CR 和税号需要区分保存。[ETA 官方号码校验规范](https://sdk.invoicing.eta.gov.eg/main-calculations/)

**20. EG：2019 PF 要求与 2025 PSP／PSO 规则**

关于 2019 年安排，可以核实的专业解释包括：

- 银行监督 PF 活动及商户选择，并审查 AML 等合规；
- 收取商户资金的 PF 需要埃及实体办公地址等安排；
- 使用独立资金账户，收到款项后最多三个工作日转付；
- 银行要求相当于三日收款额的担保。

**“三日担保”指担保金额口径，不是三天内提交担保。**这些细节本次主要依据国际律师协会刊载的当地法律分析，原 CBE PDF 获取受限，因此不把它当作已完成全部现行原文复核。[IBA 法律分析](https://www.ibanet.org/article/6FC3317B-4F9C-4DFC-BDF1-80CF98B68B21)

2025 年 CBE 已建立 PSP／PSO 许可和登记制度，覆盖境内主体及向埃及居民提供相关服务的境外主体。**旧分析中的境外服务空间不能直接沿用。**[CBE 2025 年规则发布](https://www.cbe.org.eg/en/news-publications/news/2025/06/19/08/20/psos-and-psps-licensing-rules)

对于“2025 规则是否规定全部数据必须本地化”，本次没有取得足够的原文依据给出普遍肯定结论；也不能反向认定境外托管允许。需要核对具体牌照、信息安全／外包要求及批准条件。

**21. EG：816/2025 跨境许可、同意及财务敏感数据**

**敏感个人数据包括自然人的财务数据。**公司自身不涉及自然人的财务数据，则不能仅因“财务”二字自动归入个人敏感数据。[PDPL 定义及现行法律说明](https://www.dlapiperdataprotection.com/index.html?c=EG&t=world-map)

816/2025 执行条例已经颁布。其跨境安排涉及 PDPC 许可／批准、目的地保护水平及数据主体同意；境外控制者、处理者还涉及境内代表安排。**一份笼统隐私政策或 Client 代表点击，不足以替代这些要求。**条例细节本次交叉参考当地律所全文译本，阿拉伯语公报应为最终依据。[条例 Article 16—17 译本](https://consortiolawfirm.com/egypt-data-protection-law-executive-regulations-2025-english/)、[PDPC 官方指引确认条例依据](https://pdpc.gov.eg/assets/pdf-data/Guidelines/DSConsent.pdf)

还有重要适用例外：PDPL 对 CBE 及其监管实体涉及的数据设有排除安排，同时保留特定例外。平台不能仅因客户是银行，就自动继承该排除；需逐项判断实际处理主体和数据活动。[PDPL 官方文本](https://mcit.gov.eg/Upcont/Documents/Reports%20and%20Documents_1232021000_Law_No_151_2020_Personal_Data_Protection.pdf)

**22. EG：AML 留存期限与 PEP 定义**

**已核实的 AML 基础期限为至少五年。**CBE 当前 FAQ 明确覆盖交易、客户及 BO 信息、CDD 文件、业务往来、风险分析和相关调查资料；其他法律或要求可以延长。[CBE 官方 FAQ](https://www.cbe.org.eg/en/contact-us/website-faqs)

AML Law Article 9 及执行规定按交易结束、账户关闭等事件区分起算；涉及可疑交易的文件还可能需要保留至最终决定／判决，取较长期间。不能只从开户日或审核完成日计算。[MENAFATF 对相关条文的核查](https://www.fatf-gafi.org/content/dam/fatf-gafi/fsrb-mer/MENAFATF-MER-Egypt-2021-English.pdf)

PEP 范围包括国内、外国重要公共职务人员，并延伸至相关家庭成员、密切关系人；监管框架也涉及国际组织重要职务人员。不能只问“是不是政治人物”，也不能把 PEP 自动等同禁止准入。具体职务目录和关系范围应依据适用规则及机构政策配置。[CBE PEP 说明](https://www.cbe.org.eg/en/contact-us/website-faqs)

**23. 跨国：点击接受＋IP／UA／时间，法律效力如何？**

**可以形成电子证据，但这些字段本身不足以保证合同有效或获得等同手签的证明力。**

AE 与 SA 承认符合条件的电子交易、电子记录及签名；EG 也承认电子签名，但与手写签名等效的证明力取决于法律及技术条件。普通勾选不能自动当作合格数字签名。[AE 46/2021](https://www.uaelegislation.gov.ae/en/legislations/1539/download)、[SA 电子交易法](https://laws.boe.gov.sa/BoeLaws/Laws/LawDetails/6f509360-2c39-4358-ae2a-a9a700f2ed16/1)、[埃及 ITIDA 说明](https://www.itida.gov.eg/English/Pages/E-Signature.aspx)

建议证据同时绑定：已核验账户、公司代表权限、实际展示的条款全文和版本、明确接受动作、可靠时间及防篡改记录。IP／UA 是辅助证据。

还要区分**接受使用条款、收到隐私告知、同意特定数据处理**；三者不能用一个勾选结果代表。

**24. 跨国：Client 代填关系人 PII，是否需要本人同意和告知？**

**Client 可以提交资料，但不能因此当然代表所有股东、UBO、董事作出个人同意。**

应按数据用途确定法律基础。受监管机构执行必要 CDD，可能适用法定义务等依据；平台自己的营销、产品分析或其他额外目的，不能一并借用该基础。公司授权代表提交资料，也不同于代表每个自然人放弃隐私权。[AE PDPL](https://www.uaelegislation.gov.ae/en/legislations/1972/download)

SA 对间接收集有明确告知规则：原则上不无故拖延且不超过 30 天，告知处理信息、数据类别及来源，同时存在法定例外，须逐项记录理由。[SDAIA 执行条例 Article 4、15](https://dgp.sdaia.gov.sa/wps/portal/pdp/knowledgecenter/details/PDPL2/%21ut/p/z0/04_Sj9CPykssy0xPLMnMz0vMAfIjo8ziPR1dzTwMgw2MDMOcTA3MjH39TE29jY0MQsz1g1Pz9L30o_ArAppiVOTr7JuuH1WQWJKhm5mXlq8fEeAS4GOkX5DtHg4A45XaIQ%21%21/)

建议让 Client 确认资料来源和提交权限，并建立向关系人提供适用隐私告知的机制；依法需要本人同意时取得本人同意，尤其不能把 Client 的承诺替代埃及适用的跨境同意。[埃及 PDPC 隐私告知指引](https://pdpc.gov.eg/assets/pdf-data/Guidelines/Privacy%20Notice.pdf)

**25. 跨国：所有权完整、信息真实、PEP 自述等电子声明**

**具有证据价值，但证明的是“谁作出了什么声明”，不当然证明内容真实。**

所有权声明可形成补充证据和合同承诺，不能取代股权穿透、登记核验或名单筛查。SAMA 明确要求不要主要依赖客户书面声明识别 UBO。[SAMA 第 3.19 段](https://rulebook.sama.gov.sa/ar/entiresection/1362)

建议声明绑定具体资料版本，并记录声明人身份、权限、适用范围及后续变更告知义务。PEP 问题应解释职务、家庭成员及密切关系人的范围。

电子形式可被法院考虑，但具体证明力仍取决于身份、内容完整性及法律条件；不能将普通点击声明标为“已公证”或“不可争议”。[沙特证据法数字证据规定](https://laws.boe.gov.sa/BoeLaws/Laws/SearchDetails/?Id=2716057c-c097-4bad-8e1e-ae1400c678d5&Query=+)

**26. 卡组织：留存期限及 USD 1M 直签**

**必须按卡组织分别处理，USD 1M 不是统一门槛。**

| 事项 | 本次核验结论 |
|---|---|
| Visa 商户调查档案 | 2026-04-18 版规则 1.5.2.2：完整商户档案至少保存至商户协议终止后两年。当地 AML 要求更长时，不能只保留两年。 |
| Visa 直签 | 规则 5.3.1.4 以年度 **Visa 交易额超过 USD 1M** 为基本门槛，附有例外及处理期限，不能写成无条件立即直签。 |
| Mastercard 直签 | 2026-06-02 版规则 7.8 基本门槛为年度 **Mastercard＋Maestro 合计交易额超过 USD 10M**，并有 Sponsored Merchant Threshold Conditions 等例外。 |
| Mastercard 尽调留存 | 规则明确调查记录保管及向收单机构提供的责任；本次未确认一个可泛化为所有情形的统一终止后年限，应结合完整适用规则和收单合同确定。 |

来源：[Visa 公开规则，1.5.2.2、5.3.1.4](https://usa.visa.com/content/dam/VCOM/download/about-visa/visa-rules-public.pdf)、[Mastercard 公开规则，7.6.5、7.8](https://www.mastercard.com/content/dam/mccom/shared/business/support/rules-pdfs/mastercard-rules.pdf)。

这两套全球规则均不能仅因商户位于 AE、SA 或 EG 就认定不适用；还需检查地区附则、商户类别、例外资格及收单合同。门槛计算的是相应卡组织交易额，**不是商户总营业额，也不是平台全部流水**。

对当前设计，优先需要落实的四项是：**沙特实际 KYC 操作主体、Wathq 核验及差异处理、三国数据部署与跨境安排，以及按卡组织分别维护直签规则。**尚缺权威依据的号码算法、完整签发机关格式目录和埃及具体许可条件，应继续标记为待确认，避免提前变成拒绝准入的硬规则。
