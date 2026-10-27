# Client 门户入驻（KYB / KYC）行业 UX 调研笔记

> 调研日期：2026-10-27。用途：Client 门户商户自助入驻高质量原型的设计输入。
> 可信度标记：**[官方]** 来自厂商公开文档；**[二手]** 第三方指南或帮助文章，需核实；**[经验]** 行业通行做法或调研者对公开产品的长期观察，没有逐条文档出处，原型阶段可直接用，上线前按需验证。
> 说明：Stripe Dashboard 与 Express onboarding 的具体屏幕文案没有公开逐屏文档。搜索 "Verify your business"、"Tell us more about your business" 等标题均无官方出处，因此本笔记以 Stripe 官方的**步骤标识、需求模型、外观变量**为骨架，屏幕级细节标为 [经验]。

---

## 1. Stripe

### 1.1 结论（可直接用于原型的规则）

**信息架构**
1. **按"需求驱动"生成步骤，不写死步骤表。** Stripe 的嵌入式入驻按账户的国家、业务类型、公司结构、所需能力动态生成表单。步骤可能以任意顺序出现，也可能重复 [官方]。我们的后端 Schema 按国家 × 组织类型下发，正好对应这一模型。前端只负责渲染"分组 → 字段"，步骤数不固定。
2. **Stripe 的步骤分组可直接借用为我们的分组骨架** [官方，step 名称]：
   - `business_type`：业务类型（个人 / 公司），部分情况下可选国家。
   - `business_details` → `additional_business_details`：公司基本信息 → 合规补充信息。
   - `representative_details` → `representative_document`：申请代表本人信息 → 其证件。
   - `owners` / `directors` / `executives`：受益所有人 / 董事 / 高管分开收集。
   - `proof_of_ownership_document`、`proof_of_authorization`：持股证明、授权证明（代表无签字权时）。
   - `confirm_owners`：对所有权信息完整、准确作出声明。**这对应我们的"所有权完整"声明，应放在人员步骤末尾，而不是混在最终条款里。**
   - `business_documents` / `business_verification`：公司文件、实体证明文件。
   - `external_account`：收款银行账户。
   - `summary`：最终汇总页，可从各段返回编辑（`summary_business`、`summary_persons` 等）；条款与隐私链接在此展示。
3. **汇总页即提交页。** Stripe 最后一步是 `summary`：分段展示已填信息，每段有"编辑"入口，从汇总进入的编辑会回到汇总，而不是重走向导 [官方]。条款接受放在汇总页底部 [官方]。
4. **预填信息也要确认。** 平台预填的数据在提交前仍需用户确认，并可修改 [官方]。我们入驻码带来的 PayFac 预填信息应同样处理：显示为已填并可编辑，提交前在汇总页确认。
5. **"稍后保存"是一等公民。** Hosted 流程任意步骤都有 **Save for later**，点击返回平台 [官方]。返回不代表完成，平台需要读状态决定是否继续提示 [官方]。
6. **两种收集策略。** `currently_due`（只收当前必需，快速通过）与 `eventually_due`（一次收齐）[官方]。我们是 PayFac 前置审核，建议一次收齐。补件场景则只渲染被退回的项（Stripe 用 `only` 限定，**且这种模式下不出现汇总页，完成即退出**）[官方]。补件页应是"只含问题项的短流程"，而不是把整个向导重新打开。

**需求清单与错误**
6. **需求模型分 5 类**，每类在 UI 上有不同的语气与位置 [官方]：
   | Stripe 字段 | 含义 | 我们的映射 | UI 表现 |
   |---|---|---|---|
   | `currently_due` + `current_deadline` | 截止日前必须补 | 退回待补（有期限） | 黄色"需要处理"，显示截止日 |
   | `past_due` | 已过截止，能力被停 | 退回超期 | 红色"已逾期" |
   | `errors[]`（requirement + code + reason） | 某项失败的原因 | 需求级错误 | 定位到具体字段 / 人 / 材料 |
   | `pending_verification` | 审核中，无需操作 | 审核中 | 中性灰蓝，"无需操作" |
   | `eventually_due` | 将来可能需要 | 不用于本期 | — |
7. **错误三元组：`requirement`（指向哪个字段）+ `code`（可本地化的机器码）+ `reason`（人话原因与修复方法）** [官方]。例：`person_xxx.verification.document` + `verification_document_failed_greyscale` + "灰度文件无法识别，请上传彩色版本"。**我们的退回项必须同样带"定位路径 + 原因码 + 修复说明"三件套**，前端才能精确跳转。原因码按语言本地化，`reason` 作为兜底。
8. **人员相关错误要指向人。** Stripe 的错误路径带人员 ID（`{{PERSON_ID}}.verification.document`），并有 `verification_missing_owners`、`verification_missing_directors`、`verification_document_directors_mismatch`、`verification_failed_representative_authority` 等关系类错误 [官方]。我们的退回需求应支持三种锚点：字段、人员（及其证件）、材料。
9. **文件类错误码表可直接复用为我们的上传校验文案**：损坏、复印件、灰度、不完整、不可读、未签名、缺背面、缺正面、过大、过期、缺签发或到期日 [官方]。文件要求：彩色、≤10 MB、证件 JPG/PNG、公司文件 JPG/PNG/PDF、公司文件需全部页、不得加密 [官方]。**同一文件验证失败后重复上传会被自动拒绝** [官方]，所以补件上传组件要提示"请上传新的文件"。
10. **"Action required"横幅** [官方，notification banner 组件]：只在提交后、存在待处理项时出现；列出所有待办任务；区分 `actionRequired` 数和 `total` 数（有些只是"审核中"提示）。横幅持久显示，需操作才消失 [官方，Stripe Apps]。**我们的门户首页顶部应放同样的横幅**，按"需要你处理 N 项 / 审核中 M 项"两级呈现。
11. **Banner 与 Toast 的分工** [官方，Stripe Apps]：Toast 只用于用户刚做完的动作的临时反馈，≤30 字符、少于 4 个词；Banner 用于系统级、需要用户行动的事项，含标题与正文，持久，必须有行动按钮。

**人员录入** [经验 + 官方步骤]
12. 人员按角色分组，但**一个人可以有多个角色**（Stripe 的关系前缀允许同一人同时是 owner、director、representative）[官方]。UI：先录入"人"，再勾选其角色（UBO / 董事 / 签字人 / 申请代表），避免同一人重复录两次。Adyen 也专门做了"疑似重复人员"提示（见 §2）。
13. 人员以**列表行**呈现（姓名、角色标签、持股比例、证件状态），点击进入抽屉或子页编辑；列表上方显示"持股合计"与"满足最低要求"状态 [经验，结合 Adyen 公开改版]。
14. 存在"申请人不是签字人"的情况时，Stripe 有 `designate_authorizer`（请有权限的人授权）与 `proof_of_authorization`（授权书）两条路 [官方]。我们可对应"签字人不是我 → 上传授权书 / 邀请签字人"。

**证件上传** [经验，Stripe Identity 公开行为 + 官方状态]
15. 证件采集在桌面端提供"**用手机继续**"（显示二维码，在手机上拍照，桌面实时同步状态）与"上传文件"两条路 [经验]。证件分正反面两个槽位，护照只需一面 [官方：missing_front / missing_back 错误码]。
16. 证件核验是异步的：状态 `requires_input` → `processing` → `verified` / 失败回到 `requires_input` 并附 `last_error.code + reason` [官方]。处理时间从几分钟到几个工作日不等 [官方]。UI 不要做"转圈等结果"，而是"已提交，正在核验"的静态状态行。
17. 中断后应复用同一核验会话，以便记录失败次数 [官方]。

**地址**
18. Stripe Address Element 支持 236 种地区地址格式（含 RTL 格式），**但自动补全只覆盖 26 个国家，不含 AE / SA / EG** [官方]。我们首批三国**不要依赖地址自动补全**，按国家渲染结构化字段才是正解（详见 §3）。
19. 移动端输入框字号不低于 16px，否则 iOS 会缩放页面 [官方，Appearance API 明确提示]。

**设计语言（可量化部分）** [官方，Connect 嵌入组件与 Elements 默认值]
20. 间距基于 8px 单位（嵌入组件 `spacingUnit` 示例 8px；Stripe Apps token：2/4/8/16/24/32/48）。
21. 字号阶梯：标题 28 / 24 / 20 / 16 / 12（粗 700），正文 16 / 14，标签 14 / 12，徽章 14 [官方，嵌入组件示例值]。
22. 圆角分层：按钮 4px、表单 6px、徽章 4px、浮层 8px [官方示例]。**控件圆角小、浮层稍大**是 Stripe 克制感的来源之一。
23. 颜色：主色 #0570DE（Elements）/ #0074D4（Connect），正文 #30313D / #444444，次要文本 #717171，边框 #D7D7D7，危险 #DF1B41 [官方]。
24. 状态徽章用"浅底 + 深字 + 同色系描边"三件套 [官方]：成功 #CEF6BB / #05690D，警告 #FCEEBA / #A82C00，危险 #F9E4F1 / #B3063D，中性 #E4ECEC / #545969。注意警告文字用的是深橙红而不是黄色，保证对比度。
25. 聚焦态：`0 0 0 2px 主色` 外环 + 极淡投影；错误态：`0 0 0 2px 危险色` [官方，Elements 默认规则]。
26. 标签位置：宽松布局用"标签在上"，紧凑布局用"浮动标签" [官方]。**入驻表单用标签在上**，长表单可读性更好，也更好支持 RTL。
27. 加载：按钮内置 pending 状态（禁用 + 指示），防重复提交；Spinner 延迟 200–300ms 再出现，避免闪烁；视图切换用 100ms 延迟；已知慢操作 0ms [官方，Stripe Apps]。整页加载才用大 Spinner，局部加载只占局部 [官方]。
28. 浮层可选 dialog 或 drawer [官方]。人员编辑适合 drawer（保留列表上下文）。
29. 多步任务放进聚焦视图，防止误离开；进度与主操作在底部；最后一步的主按钮为 primary [官方，Stripe Apps progress stepping]。
30. Elements 提供 `disableAnimations` 开关 [官方]，说明动画必须可整体关闭。

### 1.2 依据与来源
- 嵌入式入驻组件、step 名称、collection options、`only` 模式不出汇总页：https://docs.stripe.com/connect/supported-embedded-components/account-onboarding.md?platform=web
- 嵌入式入驻概述（预填需确认、错误状态、补件流程）：https://docs.stripe.com/connect/embedded-onboarding
- Hosted 入驻（Save for later、refresh/return、up-front vs incremental）：https://docs.stripe.com/connect/hosted-onboarding
- requirements 模型、errors 三元组、全部错误码与文件要求：https://docs.stripe.com/connect/handling-api-verification
- Notification banner：https://docs.stripe.com/connect/supported-embedded-components/notification-banner
- 嵌入组件外观变量（字号、圆角、徽章色）：https://docs.stripe.com/connect/embedded-appearance-options
- Elements Appearance API（默认色、聚焦阴影、标签位置、16px 移动端、disableAnimations）：https://docs.stripe.com/elements/appearance-api.md?api-integration=paymentintents
- Address Element（236 种格式、自动补全国家列表）：https://docs.stripe.com/elements/address-element
- Identity 会话状态与 last_error：https://docs.stripe.com/identity/how-sessions-work
- Stripe Apps 样式 token：https://docs.stripe.com/stripe-apps/style ；模式：https://docs.stripe.com/stripe-apps/patterns ；Banner vs Toast：https://docs.stripe.com/stripe-apps/patterns/communicating-state ；加载：https://docs.stripe.com/stripe-apps/patterns/loading ；分步：https://docs.stripe.com/stripe-apps/patterns/progress-stepping
- Express 本地化语言列表（**不含阿拉伯语**）、UAE 不支持自助 Express：https://docs.stripe.com/connect/express-accounts
- Stripe 官方 Sail 设计系统的动效 token 未公开，检索只找到第三方仿制值（150 / 200ms，cubic-bezier(0.2,0,0,1)），未采信。

---

## 2. Adyen、Checkout.com、Wise Business、Mercury、Revolut Business、Airwallex

### 2.1 结论

| 产品 | 结构 | 可借鉴的亮点 |
|---|---|---|
| **Adyen Hosted Onboarding** | **任务清单式**：公司信息与决策人、证件上传、银行账户核验、签署服务协议 [官方博客] | 每个任务有"Add / Sign → In Review → Verified"状态 [二手，用户指南]；决策人概览页显示"是否已满足最低要求"、**疑似重复人员提示**、**不必逐个打开即可审阅已加人员** [官方]；可锁定实体类型与国家 [官方]；链接 4 分钟过期、不得邮件发送 [官方]；名称与注册文件需完全一致（缺"Ltd"即失败）[二手] |
| **Checkout.com** | API 或预置表单 | 按地区与用户类型给"情境字段"、实时字段校验、自动身份核验、即时状态更新 [官方产品页] |
| **Wise Business** | 分步，先业务后人员 | 收集注册号、注册地址与经营地址、行业、线上渠道、董事与所有人 [官方帮助页，菲律宾]；文件需近 3 个月、整页、与录入名称一致，裁剪与截图常被拒 [二手] |
| **Mercury** | 分步申请，人员由本人完成核验 | UBO 阈值 25%，持股需**沿链追溯到自然人**（不能填控股公司）[二手]；另设"控制人"并在批准后获得管理员权限 [二手]；每位所有人收到安全邀请自行上传证件与活体 [二手] |
| **Revolut Business** | 五步：身份核验 → 业务详情 → 合伙信息 → 所有人 [官方指南] | 添加人员时可选"我来填"或"**发邀请让本人核验**"[官方帮助]；首页有"公司结构"磁贴，可在此确认结构并触发核验 [官方帮助]；需填持股与投票权比例，追溯到 UBO [官方]；24 小时内审核 [官方] |
| **Airwallex** | 先收集再"提交激活"；审核后可能发 RFI | **RFI（补充信息请求）是独立组件**：显示运营提出的追加问题并收集答复，可主题化 [官方]；RFI 两大来源：身份核验失败（需第二证件或更正）与风险评估追问 [官方]；资料齐全最快 48 小时激活 [官方博客] |

**可落到原型的规则**
1. **首页采用任务清单，内部再用分步表单。** Adyen 与 GOV.UK 都证明：长事务、可能跨多次会话完成的入驻，用任务清单作为"回家页"最稳；每个任务内部再是短向导。
2. **人员录入两条路并存：本人代填 / 邀请本人填写。** Revolut 与 Mercury 都这么做。本期若不做邀请，原型也应预留"邀请"入口（禁用态 + 说明）。
3. **人员概览要能"一眼审核"**：每行直接展示角色、持股、证件状态、缺失项，不必点进去（Adyen 改版重点）。
4. **持股校验在概览层做**：显示"已申报持股合计 x%"，以及"所有 ≥25% 的自然人均已添加"的确认项；控股公司不得作为 UBO，需提示"请追溯到最终自然人"。
5. **退回补件是一个独立、聚焦的视图**（Airwallex RFI、Stripe `only` 模式），只展示被要求的项，提交即结束，不重开整份表单。
6. **名称一致性提示前置**：在公司名称字段旁提示"须与商业登记文件完全一致，包括 LLC、Ltd 等后缀"，从源头减少退回。

### 2.2 依据与来源
- Adyen 入驻流程概述：https://www.adyen.com/knowledge-hub/platforms-onboarding-made-easy
- Adyen 决策人任务改版：https://www.adyen.com/the-latest/optimized-decision-maker-task-to-speed-up-onboarding
- Adyen 定制项与语言列表（**不含阿拉伯语**）：https://docs.adyen.com/platforms/onboard-users/customize-hosted-onboarding
- Adyen 用户指南（状态文案，二手）：https://www.britishrowing.org/wp-content/uploads/2025/10/Adyen-User-Guide.pdf
- Checkout.com：https://www.checkout.com/products/integrated-platforms ；https://www.checkout.com/docs/platforms/for-saas
- Wise：https://wise.com/help/articles/2769792/How%20do%20I%20verify%20my%20business%20in%20the%20Philippines
- Mercury（二手）：https://mercury.com/blog/open-us-bank-account ；https://ecommerceparadise.com/how-to-open-a-mercury-account/
- Revolut：https://help.revolut.com/business/help/setting-up-an-account/onboarding/directors-and-shareholders/what-requirements-are-there-for-beneficial-owners-and-directors/ ；https://www.revolut.com/en-CZ/business/business-resources-onboarding-guide/
- Airwallex RFI：https://www.airwallex.com/docs/connected-accounts/handle-rfis/handle-kyc-rfi/embedded-kyc-rfi-component ；KYC 博客：https://www.airwallex.com/en-hk/blog/understanding-of-kyc-certification

---

## 3. 中东本地与阿语 RTL

### 3.1 结论

**本地厂商**
1. **PayTabs** 的公开入驻页是"**先选国家 + 商户类别，再看对应材料清单**"[官方]。类别：自由职业者、单一所有人公司、多所有人公司、政府或上市公司、慈善组织。**我们可以在入驻开始前给一个"准备清单"页**，按所选国家与组织类型列出需备材料，减少中途离开去找文件。
2. PayTabs 材料差异可直接作为原型示例数据 [官方]：
   - AE：Trade License、MOA / AOA、持股 >25% 的所有人证件、签字人与董事证件、Emirates ID 与签证（非本国人）、银行信息。
   - SA：CR（商业登记）、AOA、**持股 >15% 的所有人证件**（注意不是 25%）、签字人与董事证件、**地址证明可用 National Address Certificate**、银行信息。
   - EG：Commercial Registration、MOA、所有人国民身份证、**Tax Card（税卡）**、银行对账单。
   - **结论：UBO 阈值不能写死 25%，应由后端 Schema 按国家下发。** 原型里阈值文案用变量。
3. **Tap Payments**：核心材料为营业执照或商业登记、授权签字人姓名、所有人与股东名单；**银行账户名须为英文并与执照上的法定名称一致** [官方]。平台模式通过 Connect URL 引导商户完成注册，之后可回到保存的面板 URL 管理 KYC [官方]。
4. **Geidea**：未找到公开入驻流程。同为沙特的 EdfaPay 公开要求：CR、IBAN 证明、身份证、Saudi Post 国家地址，**文件 <1 MB、PDF 或 JPG** [官方，EdfaPay]。说明本地收单方对文件大小限制可能远严于 Stripe 的 10 MB，上传组件的大小上限应由 Schema 下发。

**地址格式**（由后端 Schema 下发字段，原型按国家切换）
5. **SA 国家地址**由建筑号（4 位）、街道、区（District）、城市、邮编（5 位）、附加号（4 位）组成；**短地址为 4 个英文字母 + 4 位数字**，使用短地址时仍需城市与邮编 [官方 Saudi Post + 二手 DHL]。原型：先给"短地址"输入，附"展开完整地址"；完整地址按"建筑号 | 附加号"同一行、"街道"、"区 | 城市"、"邮编"排布。
6. **AE** 无普遍邮编，常用 PO Box；地址以酋长国（7 个，下拉）+ 城市 / 区域 + 街道 / 楼宇 + PO Box（可选）组成 [经验]。
7. **EG** 省（Governorate，27 个，下拉，必填）+ 城市 / 区 + 街道 + 楼号；邮编 5 位，可选 [经验]。

**阿语 RTL 表单**
8. **方向在根节点设置**：`<html dir="rtl" lang="ar">`，组件全部使用逻辑属性（`margin-inline-start`、`padding-inline-end`、`text-align: start`），不写 left / right [二手，多来源一致]。
9. **标签、输入文本、错误信息在 RTL 下右对齐** [二手]。
10. **以下字段在 RTL 页面中保持 LTR**：邮箱、电话、URL、IBAN、证件号、CR 号、邮编、短地址码。用 `dir="ltr"` 并在 RTL 布局中**保持右侧起始对齐**（视觉与其他字段对齐，内容方向为 LTR）[二手；对齐方式各来源不一，此处取"方向 LTR + 视觉对齐跟随页面"，更常见于本地银行 App，经验]。
11. 正文中混排号码时用 `<bdi>` 或 LTR 隔离，防止 bidi 算法打乱顺序 [二手]。用户输入的名称展示时用 `dir="auto"` [二手]。
12. **数字**：允许输入东阿拉伯数字（٠١٢٣٤٥٦٧٨٩），提交前统一转为西方数字 [经验]。显示时阿语界面可用西方数字，海湾地区金融产品普遍如此 [经验]。
13. **日期**：用分开的日 / 月 / 年三个字段或带格式提示的单字段，避免依赖浏览器原生日期控件在 RTL 下的不一致表现 [经验]。沙特部分证件（如旧版 Iqama）存在回历日期，是否支持回历输入需业务确认 [经验，TODO]。
14. **镜像规则** [经验，与 Material 双向性指南一致]：返回 / 前进箭头、进度条方向、步骤序列、抽屉进入方向镜像；对勾、上传图标、时钟、媒体播放、品牌标识、电话图标不镜像。
15. **双文字姓名输入**：同一人员卡片内并列两组姓名，"拉丁字母姓名"与"阿拉伯文姓名"，每组按证件结构（名 / 父名 / 祖父名 / 姓，后端按国家决定是否需要中间名）[经验]。阿拉伯文姓名输入框强制 `dir="rtl"` 并用阿语字体，即使界面语言是英文或中文；拉丁姓名框强制 `dir="ltr"`。可加"与证件一致"的提示。
16. 阿语字体字号需比拉丁字体大约 1–2px 或行高更大，以保证可读 [经验]。

### 3.2 依据与来源
- PayTabs 入驻材料（分国家与类别）：https://ai.paytabs.com/en/onboarding-documentation/
- Tap 所需材料：https://support.tap.company/en/support/solutions/articles/153000242639-what-are-the-documents-needed-to-get-started-with-tap-payments- ；平台入驻：https://support.tap.company/en/support/solutions/articles/153000140181-how-can-i-onboard-businesses-under-my-e-commerce-platform- ；https://developers.tap.company/docs/smartpos-onboarding-merchants
- EdfaPay（沙特同类参考）：https://docs.edfapay.com/docs/merchant-registration
- 沙特国家地址：https://splonline.com.sa/en/door-step/ ；UPU 格式表：https://www.upu.int/UPU/media/upu/PostalEntitiesFiles/addressingUnit/sauEn.pdf ；附加号：https://www.wafeq.com/en-sa/business-hub/for-business/additional-number-in-the-saudi-national-address ；DHL 短地址格式（二手）：https://www.dhl.com/discover/ko-kr/small-business-advice/starting-a-business/shipment-to-saudiarabia
- RTL 实践（二手，多来源）：https://rtlstyling.com/posts/rtl-styling/ ；https://jetruby.com/blog/right-to-left-design/ ；https://santa-media.co/blog/ux-design-principles-arabic-websites
- Material 双向性页面未能抓取正文，镜像规则按行业通行做法整理。

---

## 4. 交互模式清单

### 4.1 结论

**分步向导 vs 单页长表单**
1. **入驻用"任务清单首页 + 每个任务内的短向导"**。NN/g：向导适合低频、新手、后步依赖前步答案的流程；按用户路径只显示相关步骤；下一步按钮写成下一步的名字而不是泛泛的"下一步"；提供显式上一步；允许中途退出并保存 [官方 NN/g]。GOV.UK：长事务、跨多次会话时用任务清单，每次回来都先看到它；任务名用动词开头；尽量允许任意顺序 [官方 GOV.UK]。
2. **步骤依赖用"暂不可开始"状态表达**（灰字、不可点），例如"先选择组织类型，才能填写人员" [官方 GOV.UK]。
3. **保存**：字段失焦即自动保存草稿（静默），页脚显示"已保存 · 2 分钟前"；"保存并稍后继续"按钮放在主按钮旁，而不是藏在页头 [NN/g：保存入口靠近用户焦点]。

**任务清单首页**
4. 状态文案限 4–5 个：未开始、进行中、已完成、需要处理、暂不可开始 [GOV.UK]。**已完成用普通文字不加底色，让需要处理的项更突出** [GOV.UK]。红色只用于真正的问题 [GOV.UK]。
5. 每个任务行：任务名 + 一句说明（例如"3 位人员，1 位缺证件"）+ 右侧状态。整行可点。
6. 顶部摘要一行："已完成 4 / 6 项"。不用大号圆形进度环（见 §6 反面清单）。

**内联校验时机**
7. **失焦后校验，输入中不报错**；修正后实时清除错误（"输入中只做消除，不做报错"）[NN/g]。仅密码类复杂输入实时反馈 [NN/g]。
8. 错误信息紧贴字段下方，文字 + 图标（不能只靠颜色）；不用 tooltip 或仅悬停可见的错误 [NN/g]。长表单可给出错字段加浅色错误底 [NN/g]。
9. **提交时的错误汇总**：页面顶部（标题之上）出现"存在问题"汇总框，焦点移到汇总框，每条链接跳到对应字段（多字段组如日期跳第一个出错的子字段，单选跳第一个选项），汇总文案与字段旁文案一致，页面 `<title>` 前加"错误："[官方 GOV.UK]。汇总不能是唯一的错误提示 [NN/g]。

**字段显隐过渡**
10. 条件字段（例如"是否为 PEP → 是 → 职务与国家"）在触发控件下方展开，高度过渡 150–200ms，展开后不自动移动焦点，屏幕阅读器通过 `aria-expanded` 或 `aria-controls` 获知 [经验]。收起时若已有输入，保留值但不提交，并在再次展开时恢复 [经验]。

**上传组件状态机** [经验，状态名对齐 Stripe 错误码与 Identity 会话]
11. 状态：`空` → `拖入悬停` → `上传中（进度条 + 取消）` → `已上传，待提交` → `已提交，核验中` → `已通过` / `被退回（原因码 + 说明 + 重新上传）`；另有 `上传失败（网络 / 超限 / 格式）`，可重试。
12. 每个材料槽位写清：接受格式、大小上限、页数要求（"需全部页"）、彩色要求、有效期要求（地址证明近 3 或 6 个月）。
13. 证件双槽位：正面、背面；护照单槽位。桌面端提供"用手机拍照"二维码入口 [经验]。
14. 已上传文件显示为一行：文件类型图标、文件名（中间省略）、大小、上传时间、查看 / 替换 / 删除。**不显示大缩略图卡片墙**。
15. 被退回后要求上传新文件，不允许重新提交同一文件（Stripe 同文件自动失败）[官方]。

**人员卡片列表**
16. 用表格式列表行而不是卡片网格：头像首字母 / 姓名（拉丁 + 阿语小一号次行）/ 角色标签（可多个）/ 持股 / 证件状态 / 操作 [经验 + Adyen]。
17. 列表头部：持股合计、"最低要求"检查项（至少 1 位签字人、所有 ≥阈值 UBO、至少 1 位董事，阈值来自 Schema），每项为对勾或待完成 [Adyen 改版思路]。
18. 编辑人员在右侧抽屉（移动端全屏页）中进行，保存后列表行原位高亮 1 次（背景色淡入淡出约 600ms）[经验]。
19. 疑似重复（姓名 + 出生日期 + 证件号相同）在保存时提示合并 [Adyen]。

**审核时间线**
20. 提交后状态页：顶部状态标题与一句话（"审核中，通常 1–3 个工作日"），下方纵向时间线：提交 → 审核中 → 退回（含期限）→ 重新提交 → 批准 / 拒绝。每个节点带时间戳与操作人类型（你 / 平台审核）[经验]。
21. 审核中时**表单只读**，但仍可查看所有已提交内容，并提供"撤回申请"（次要危险操作，需二次确认，说明撤回后果）。

**退回时的问题定位**
22. 退回状态下首页顶部横幅："需要你处理 3 项 · 截止 2026-11-10"。下面是需求清单，每项：问题标题、所在位置（"人员 · Ahmed Al-Saud · 证件背面"）、原因说明、截止日、"去处理"按钮 [Stripe requirements + Airwallex RFI]。
23. 点"去处理"进入**只含问题项的补件视图**；字段带左侧色条（RTL 下在右侧）与原因说明；处理完一项即在清单中打勾，全部完成后主按钮"重新提交"才可用。
24. 也提供"在完整表单中查看"的入口：跳转到对应步骤，滚动到字段，聚焦，字段外框短暂高亮（约 1.2s 后衰减为常驻错误样式）[经验]。
25. 逾期：横幅转红色，文案说明后果（例如"申请已过期，需重新发起"或"可联系 PayFac 延期"，以业务规则为准）。

**空状态与成功状态**
26. 空状态（例如还没有人员）：一句说明 + 一个主操作，不放插画 [经验，Stripe 风格]。
27. 成功状态（批准）：页面标题直接写结果，下面列"接下来可以做什么"的 2–3 个链接。不撒彩带、不用大绿勾动画 [经验]。

**移动端 390px**
28. 单列；左右 16px 边距；任务清单行高 ≥56px；底部固定操作栏（主按钮全宽，次按钮为文字按钮）；步骤指示改为"第 2 步，共 5 步 · 人员"一行文字；人员编辑抽屉改为全屏页；二维码手机拍照入口在手机端隐藏，直接调起相机或文件选择 [经验]。输入框字号 16px [Stripe 官方]。

### 4.2 依据与来源
- NN/g 向导：https://www.nngroup.com/articles/wizards/
- NN/g 表单错误：https://www.nngroup.com/articles/errors-forms-design-guidelines/
- GOV.UK 多任务（任务清单）：https://design-system.service.gov.uk/patterns/complete-multiple-tasks/
- GOV.UK 错误汇总：https://design-system.service.gov.uk/components/error-summary/
- Stripe 与 Adyen 来源见 §1、§2

---

## 5. 动效

### 5.1 结论
1. **时长**：工作型产品动效集中在 70–240ms；400ms 以上只给大块展开或重要系统通知 [官方 Carbon]。
   - 70ms：按钮按下、开关
   - 110ms：淡入淡出、hover 色变
   - 150ms：小范围展开、短距移动（条件字段展开、下拉）
   - 240ms：较大展开、Toast 进入、抽屉进入、步骤切换
   - 400ms：仅用于大面积变化（例如整页状态从"审核中"变为"已批准"时的内容替换）
2. **缓动**：用"生产型"曲线，快进缓出，不弹跳 [官方 Carbon]。
   - 标准：`cubic-bezier(0.2, 0, 0.38, 0.9)`
   - 进入：`cubic-bezier(0, 0, 0.38, 0.9)`
   - 退出：`cubic-bezier(0.2, 0, 1, 0.9)`
   - 退出比进入快约 20–30% [经验]
3. **用在哪里**：
   - 步骤切换：内容区淡出 110ms → 新内容淡入并上移 4–8px，240ms；RTL 下若用水平位移则方向镜像 [经验]
   - 条件字段展开与收起：高度 + 透明度，150ms
   - 抽屉：从行内起始侧滑入 240ms，遮罩淡入 240ms
   - 上传进度：进度条宽度线性变化，完成时对勾图标淡入 110ms
   - 自动保存提示："保存中…" → "已保存"文字交叉淡入 110ms，不弹 Toast
   - 退回字段定位：滚动到字段（`scroll-behavior: smooth`，减少动效时瞬移）+ 外框高亮衰减
   - Spinner 延迟 200–300ms 出现 [Stripe 官方]
4. **不用在哪里**：输入框打字、错误文字本身（不抖动、不闪烁）[NN/g：不要动画化错误文本]；页面首次加载不做逐块入场；数字不做滚动计数；悬停不做缩放或弹跳；成功页不做彩带。
5. **无障碍**：
   - `prefers-reduced-motion: reduce` 下：所有位移与缩放归零，只保留 ≤100ms 的透明度变化；平滑滚动改瞬移；进度条保留（它是信息不是装饰）。
   - 焦点管理：步骤切换后焦点移到新步骤的 `<h1>`（`tabindex="-1"`）；提交错误时焦点移到错误汇总；抽屉打开时焦点进入抽屉第一个可交互元素，关闭时回到触发按钮；抽屉内焦点陷阱。
   - `aria-live="polite"`：自动保存状态、上传进度百分比（按 25% 节点播报，不逐帧）、核验状态变化；`role="alert"`（assertive）只用于提交失败与错误汇总出现。
   - 动画必须可整体关闭（Stripe Elements 也提供 `disableAnimations`）[官方]。

### 5.2 依据与来源
- Carbon 动效（时长 token 与生产型 / 表现型曲线）：https://carbondesignsystem.com/elements/motion/overview/
- Stripe 加载延迟与按钮 pending：https://docs.stripe.com/stripe-apps/patterns/loading
- Stripe disableAnimations：https://docs.stripe.com/elements/appearance-api.md?api-integration=paymentintents
- NN/g 错误动画建议：https://www.nngroup.com/articles/errors-forms-design-guidelines/
- GOV.UK 错误汇总焦点：https://design-system.service.gov.uk/components/error-summary/

---

## 6. 反面清单：避免"AI 生成感"

### 6.1 结论
| 典型问题 | 为什么显得廉价 | Stripe 式做法 |
|---|---|---|
| 卡片套卡片、每块都有边框加阴影 | 层级混乱，视觉噪音 | 页面用留白与分隔线分段；只有浮层（抽屉、下拉）有阴影；表单区无外框 |
| 紫蓝渐变、发光背景、玻璃拟态 | 颜色没有含义 | 一个主色，只用于主按钮、链接、聚焦环、当前步骤；其余是中性灰阶；彩色只表达状态 |
| 所有元素同一大圆角（12–16px） | 失去层级 | 控件 4–6px，浮层 8px，徽章 4px |
| 图标堆砌（每个标题、每张卡片前一个线性图标） | 装饰性，不传递信息 | 图标只用于状态（对勾、警告、错误）与操作（上传、删除）；任务清单不放图标 |
| 居中大标题 + 小药丸徽章 + 空洞副标题 | 营销页套路进了工具页 | 标题左对齐（RTL 右对齐），一句话说清楚这一步要做什么与为什么 |
| 千篇一律三列特性网格 | 与任务无关 | 入驻是线性任务，用单列表单 + 侧边步骤列表 |
| 大圆形进度环、百分比大数字 | 夸张，且百分比对不等长任务无意义 | "已完成 4 / 6 项"一行文字 + 任务状态 |
| 每个 hover 都弹跳缩放、统一淡入 | 动效没有目的 | 见 §5，只在状态变化处使用 |
| Inter 用于一切 | 被大量第三方文章列为 AI 默认标志 | 选择有阿语同族的字体（见 §8） |
| 插画空状态、彩带成功页 | 降低金融产品可信度 | 一句文字 + 一个操作 |
| 占位文字代替标签 | 输入后标签消失，可访问性差 | 标签在上，帮助文字在标签下，示例格式写在帮助文字里 |
| 灰色次要文字对比度不足 | 难读 | 次要文字不浅于 #6A7383 在白底（≥4.5:1） |

**原则**：先锁定 token 文件（颜色、字号、间距、圆角、阴影、动效），原型中禁止 token 外的值；颜色语义化而非装饰化；默认无边框，靠间距分组 [二手，多篇设计博客一致]。

### 6.2 依据与来源
- https://smoothui.dev/blog/ai-design-slop
- https://www.mania.design/blog/spot-the-slop-a-ui-designers-guide-to-fixing-ai-defaults/
- https://www.925studios.co/blog/ai-slop-design-tells
- Stripe 徽章与圆角分层见 §1 来源
（以上博客为观点性材料，非实证研究。）

---

## 7. 推荐信息架构与页面清单（Client 门户入驻）

### 7.1 总体结构
```
登录 / 注册
└── 入驻码兑换（PayFac 码）
    └── 入驻准备（国家 + 主体类型 + 材料准备清单）
        └── 入驻首页（任务清单 + 状态横幅）── 任务内分步表单
            ├── 1 主体信息
            ├── 2 地址与联系方式
            ├── 3 人员（仅机构：UBO / 董事 / 签字人）
            ├── 4 材料上传
            ├── 5 收款账户（若本期范围包含）
            ├── 6 声明
            └── 7 核对并提交（汇总页）
        └── 提交后状态页（审核中 / 退回 / 批准 / 拒绝 / 逾期 / 已撤回）
            └── 补件视图（只含退回项）
```
个人主体时隐藏任务 3，任务 1 变为"个人信息"，证件在任务 1 内完成。

### 7.2 页面清单

| # | 页面 | 目的 | 关键组件 | 状态 |
|---|---|---|---|---|
| P1 | 入驻码兑换 | 输入 PayFac 发放的入驻码，绑定 PayFac 与预填信息 | 单输入框（等宽字体、自动大写、分组显示如 `XXXX-XXXX`、可粘贴）；"码从哪里来"说明；主按钮"继续" | 默认 / 校验中（按钮 pending）/ 码无效 / 码已过期 / 码已使用（附联系 PayFac 指引）/ 成功（显示 PayFac 名称确认） |
| P2 | 入驻准备 | 选国家与主体类型，按 Schema 生成材料清单 | 国家单选列表（AE / SA / EG，旗帜可省，国家名 + 本地名）；主体类型单选（个人 / 机构，机构下再选组织形式）；材料准备清单（按所选组合实时刷新）；"预计用时 15–20 分钟" | 未选 / 已选（清单出现）/ Schema 加载中（骨架行）/ Schema 加载失败（重试）/ 已有进行中的申请（直接跳 P3） |
| P3 | 入驻首页 | 回家页，显示进度、待办与截止 | 顶部状态横幅（仅退回或逾期时）；"已完成 n / m 项"；任务清单行（名称、一句说明、状态）；页脚"保存于…"；右侧（桌面）"需要帮助"与 PayFac 联系方式 | 全新 / 进行中 / 全部完成可提交 / 审核中（只读，跳 P10）/ 退回 |
| P4 | 主体信息（机构） | 公司法定名称（拉丁 + 阿语）、商号、注册号（CR / Trade License No.）、注册日期、组织形式、行业、网站、税号（EG 税卡号） | 双文字名称字段组；注册号（LTR，按国家格式掩码）；日期三段式；行业搜索下拉；名称一致性提示 | 编辑 / 字段错误 / 退回定位态 |
| P4' | 个人信息（个人） | 姓名（拉丁 + 阿语）、出生日期、国籍、证件类型与号码、证件上传 | 同上 + 证件上传组件 | 同上 |
| P5 | 地址与联系方式 | 注册地址、经营地址（"与注册地址相同"复选）、电话、邮箱 | 按国家渲染的地址组件（SA 短地址优先、AE 酋长国 + PO Box、EG 省必填）；电话（国家区号前缀 + LTR）；邮箱（LTR） | 编辑 / 错误 / 退回定位 |
| P6 | 人员列表（机构） | 录入 UBO、董事、签字人，人可多角色 | 最低要求检查区（持股合计、各角色是否满足）；人员列表行；"添加人员"按钮；所有权完整性声明复选（对应 Stripe `confirm_owners`） | 空状态 / 部分 / 满足要求 / 有疑似重复 / 有人员缺证件 / 退回定位 |
| P6a | 人员编辑抽屉 | 单个人员详情 | 角色多选（UBO / 董事 / 签字人 / 申请人本人）；持股比例（UBO 时出现）；双文字姓名；出生日期；国籍；居住地址；证件类型 + 号码 + 有效期 + 正反面上传；PEP 问题（是 → 展开职务、国家、关系） | 新建 / 编辑 / 保存中 / 字段错误 / 证件核验状态 |
| P7 | 材料上传 | 按 Schema 列出公司级材料槽位 | 材料行：名称、要求说明（格式、大小、页数、有效期）、上传区、已上传文件行；必需与可选分组 | 每槽位按 §4 上传状态机 |
| P8 | 声明 | 信息真实、PEP 总体声明、所有权完整、平台条款与隐私 | 逐条复选框（每条一句完整陈述 + 可展开全文链接）；条款版本号与日期；签署人姓名自动带出 | 未勾 / 部分 / 全部勾选；条款加载失败 |
| P9 | 核对并提交 | 汇总全部信息，逐段编辑，最终提交 | 分段汇总（定义列表样式，左标签右值；RTL 镜像）；每段"编辑"链接（编辑后返回本页）；缺失项红色提示并链接；底部提交按钮 + "提交后在审核期间不可修改"说明 | 可提交 / 有缺失（提交禁用，列出缺失）/ 提交中 / 提交失败（错误汇总） |
| P10 | 提交后状态页 | 展示申请状态与时间线 | 状态标题 + 一句说明；时间线；只读查看入口；撤回按钮（二次确认对话框） | 审核中 / 退回（含截止，跳 P11）/ 退回超期 / 批准（下一步链接）/ 拒绝（原因摘要 + 可否申诉或重新申请的说明）/ 已撤回（可重新开始） |
| P11 | 补件视图 | 只处理被退回的需求 | 需求清单（问题、位置、原因、截止、处理状态）；每项内联表单或上传；"在完整表单中查看"链接；"重新提交"按钮（全部处理后可用） | 待处理 n 项 / 部分处理 / 全部处理 / 逾期（只读 + 说明）/ 提交中 |
| G | 全局 | 语言切换（en / zh-CN / 预留 ar）、保存状态、会话超时提醒 | 页头语言菜单；超时前 2 分钟对话框"继续编辑" | — |

**布局**：桌面端左侧固定步骤列表（任务名 + 状态点，当前项加粗与主色竖条），右侧内容区最大宽 640px；页面总宽 ≤1080px。任务内分步时，步骤列表展开显示子步骤。

---

## 8. 视觉与动效规格建议

### 8.1 字体
- **拉丁 / 中文**：`"IBM Plex Sans", -apple-system, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif`。
- **阿拉伯文**：`"IBM Plex Sans Arabic"`（与 Plex 同族，字重一致，可从 Google Fonts 加载）。
- **等宽**（入驻码、证件号、IBAN、CR 号）：`"IBM Plex Mono", ui-monospace, monospace`；数字一律 `font-variant-numeric: tabular-nums`。
- 字重只用 400 / 500 / 600。

### 8.2 字号阶梯（px / 行高）
| Token | 尺寸 | 用途 |
|---|---|---|
| `display` | 28 / 36，600 | 状态页结果标题（仅 P10） |
| `h1` | 24 / 32，600 | 页面标题 |
| `h2` | 18 / 26，600 | 分组标题 |
| `h3` | 16 / 24，600 | 子分组、抽屉标题 |
| `body` | 15 / 22，400 | 正文、汇总值 |
| `input` | 16 / 24，400 | 输入框（移动端不得小于 16） |
| `label` | 14 / 20，500 | 字段标签 |
| `help` | 13 / 18，400 | 帮助文字、错误文字 |
| `caption` | 12 / 16，500 | 徽章、时间戳 |
阿语界面：所有行高 +2px，`body` 与 `label` 字号 +1px。

### 8.3 间距栅格
- 基础单位 4px；常用：4 / 8 / 12 / 16 / 24 / 32 / 48 / 64。
- 字段之间 24px；标签与输入框 6px；帮助或错误文字与输入框 6px；分组之间 48px；分组标题与首个字段 16px。
- 内容区最大宽 640px；桌面侧栏宽 240px，侧栏与内容间距 64px；移动端左右 16px。

### 8.4 色板
**中性**
| Token | 值 | 用途 |
|---|---|---|
| `bg` | #FFFFFF | 页面 |
| `bg-subtle` | #F6F8FA | 侧栏、汇总页分段底、只读字段 |
| `border` | #D5DBE1 | 输入框边框、分隔线 |
| `border-strong` | #A3ACB9 | 输入框 hover |
| `text` | #1A1F36 | 主文字 |
| `text-secondary` | #545969 | 次要文字、帮助文字 |
| `text-disabled` | #A3ACB9 | 禁用 |

**品牌**：`primary` #2B5CD6（hover #2049B0，按下 #183A8F），`primary-subtle` #EEF3FD。品牌色只用于主按钮、链接、聚焦环、当前步骤指示。实际品牌色以 PayFac / 平台品牌为准，替换此 token 即可。

**语义**（浅底 / 文字 / 描边，沿用 Stripe 徽章三件套思路）
| 语义 | 底 | 文字 | 描边 | 用途 |
|---|---|---|---|---|
| success | #E7F6EC | #0E6245 | #B7E1C6 | 已完成、已通过、已批准 |
| warning | #FEF4E2 | #9A4A00 | #F5D9A8 | 需要处理、截止临近 |
| danger | #FDECEE | #B3093C | #F5C2CB | 错误、已逾期、被拒绝 |
| info | #EAF2FF | #1E4FB5 | #C3D7FB | 审核中、核验中 |
| neutral | #EFF1F4 | #545969 | #D5DBE1 | 未开始、已撤回、暂不可开始 |
`danger` 实色（错误文字与图标、错误边框）：#DF1B41。

### 8.5 控件尺寸与圆角
| 控件 | 规格 |
|---|---|
| 输入框 / 下拉 | 高 40px（移动端 44px），左右内边距 12px，边框 1px，圆角 6px |
| 按钮 | 高 40px（移动端 44px），左右内边距 16px，圆角 6px，字重 500；主按钮实色，次按钮白底 + 描边，文字按钮无底 |
| 复选框 / 单选 | 18px，复选圆角 4px；点击热区 ≥44px |
| 徽章 | 高 22px，左右内边距 8px，圆角 4px，`caption` 字号 |
| 任务清单行 | 高 ≥64px（桌面）/ ≥56px（移动），行间 1px 分隔线，无卡片框 |
| 上传区 | 高 96px，虚线边框 1px `border-strong`，圆角 8px；拖入时实线主色 + `primary-subtle` 底 |
| 抽屉 | 宽 480px，圆角 0（贴边），阴影 `shadow-overlay` |
| 对话框 | 宽 440px，圆角 8px |
| 聚焦环 | `0 0 0 3px rgba(43,92,214,0.28)` + 边框换成 `primary`；错误态边框 `danger`，聚焦环 `0 0 0 3px rgba(223,27,65,0.22)` |

### 8.6 阴影
| Token | 值 | 用途 |
|---|---|---|
| `shadow-none` | none | 页面内容一律无阴影 |
| `shadow-raised` | `0 1px 2px rgba(26,31,54,0.08)` | 仅底部固定操作栏（滚动时出现） |
| `shadow-popover` | `0 4px 12px rgba(26,31,54,0.12), 0 1px 3px rgba(26,31,54,0.08)` | 下拉菜单、气泡 |
| `shadow-overlay` | `0 12px 32px rgba(26,31,54,0.18)` | 抽屉、对话框 |

### 8.7 动效 token
| Token | 值 |
|---|---|
| `duration-instant` | 70ms |
| `duration-fast` | 110ms |
| `duration-base` | 150ms |
| `duration-moderate` | 240ms |
| `duration-slow` | 400ms |
| `ease-standard` | cubic-bezier(0.2, 0, 0.38, 0.9) |
| `ease-enter` | cubic-bezier(0, 0, 0.38, 0.9) |
| `ease-exit` | cubic-bezier(0.2, 0, 1, 0.9) |
| `delay-spinner` | 250ms |
| `highlight-fade` | 1200ms（退回字段定位高亮衰减） |

| 场景 | 组合 |
|---|---|
| 按钮按下与 hover | 背景色 `duration-instant` / `ease-standard` |
| 条件字段展开 | 高度 + 透明度 `duration-base` / `ease-enter`；收起 `duration-fast` / `ease-exit` |
| 步骤切换 | 旧内容淡出 `duration-fast`；新内容淡入 + 上移 6px `duration-moderate` / `ease-enter` |
| 抽屉 | 滑入 `duration-moderate` / `ease-enter`；滑出 `duration-base` / `ease-exit`；方向跟随 `inset-inline-end` |
| Toast | 进入 `duration-moderate`；停留 4s；退出 `duration-fast` |
| 自动保存文案 | 交叉淡入 `duration-fast` |
| 列表行保存高亮 | `primary-subtle` 底色淡出 600ms |
| 减少动效模式 | 所有位移与高度动画为 0ms，仅保留 ≤`duration-fast` 透明度；平滑滚动关闭 |

### 8.8 文案语气（附）
- 按钮写动作结果："继续填写人员"、"保存并稍后继续"、"提交审核"、"重新提交"，不写"确定"、"下一步"。
- 错误写原因与修复方法："证件背面缺失，请上传证件背面照片"，不写"上传失败"。
- 截止日写绝对日期 + 相对提示："2026-11-10 前（还剩 14 天）"。
