# commercial 开通与定价模型调整（一期）· 阶段 7 mor-test 验收

> 完成（UTC 2026-10-20 21:19–21:58）。**结论：13 项 AC 中 12 项通过；AC-09 按环境限制接受（risk-decision 资格占位，只有间接证据，主会话已确认）。AC-05 首轮失败（缺陷 D1）。D1、D2 分别在 capability-20261021b（`sha256:83a05343…`，9ed4c39c，21:30:17Z）与 capability-20261021c（`sha256:c6c9b8f6…`，9ed4c39c + a9828758，21:53:50Z）修复，均已复验通过；终版镜像 c6c9b8f6 上 D1 回归、D2 复验 8 / 8 通过，商务页 18 / 18 通过。**环境：mor-test 真实运营入口 `http://localhost:28080`，发布见 [release.md](../release/release.md)。不改代码、容器、镜像、compose 与 SQL 结构；数据只经运营入口写入，数据库只读核对。

## 1. 方法

- 脚本 [stage7_acceptance.py](./stage7_acceptance.py)：A（PLATFORM_ADMIN，maker）+ B（IAM 每次新建的合成审批人，权限 CONTRACT_READ / PRICING_READ / PRICING_APPROVE / TEMPLATE_APPROVE，结束时停用并清空角色权限）经 Gateway → ops-bff → commercial 逐项执行，记录 HTTP 状态、关键字段与 Owner `detail` 原文。重复运行按编号先查，已存在的目录项、画像、模板、DEMO2-* 合同跳过写入，排期中的版本续做激活。
- 运行三次（均在 `local-environment/e2e/runtime/`）：

| 次 | 记录 | 结果 | 说明 |
|---|---|---|---|
| 1 | `commercial-capability-stage7-run1-console.txt` | 目录、模板、DEMO2-CL-101 全流程 | DEMO2-CL-101 排期后脚本缺陷（证据含 tuple 键，JSON 序列化失败）中止；控制台原文保存为该文件；finally 已停用审批人 |
| 2 | `commercial-capability-stage7-20261020T212252Z.json` | 46 通过 / 2 失败 / 8 跳过 | 102 / 103 / PT-101 / 901 全流程；2 项失败为缺陷 D2（模板 v2 保存被拒）与脚本试算事实不全 |
| 3 | `commercial-capability-stage7-20261020T212457Z.json` | 30 通过 / 1 失败 / 13 跳过 | 补齐试算事实、模板 v2 续做；唯一失败为缺陷 D2 的探测项（预期失败，用于留证） |
| D1 复验 | `commercial-capability-stage7-recheck-d1-20261020T214751Z.json`（脚本 [stage7_recheck_d1.py](./stage7_recheck_d1.py)） | **10 / 10 通过** | 修复版 commercial：DEMO2-CL-101 v2 与 DEMO2-CL-901 v4，见 §3 D1 |
| D2 复验 + D1 回归 | `commercial-capability-stage7-recheck-d2-20261020T215433Z.json`（脚本 [stage7_recheck_d2.py](./stage7_recheck_d2.py)） | **8 / 8 通过** | 终版 commercial `sha256:c6c9b8f6…`，见 §3 D1 / D2 |
| 4、5 | `…-20261020T214852Z.json`、`…-20261020T214900Z.json` | 各 29 通过 / 1 失败 / 13 跳过 | 修复版上的只读回归（数据已存在全部跳过写入）；唯一失败为「DEMO2-CL-101 v1 到点 ACTIVE」，因 v1 已被 v2 取代为 SUPERSEDED，属预期。第 5 次为误重复执行，同样停用了其审批人 |

- 数据库核对（root 只读）：迁移前备份 `/Users/kenny/Code-MoR-backups/20261020T205752Z-commercial-capability-pricing/mor_commercial.sql` 解析后与当前表逐行比较。

## 2. 验收结果（API）

| AC | 结论 | 证据（运行 / 关键值） |
|---|---|---|
| AC-01 目录维护 | 通过（偏差 V1） | 新增 RECURRING（FEATURE）、STC_PAY（PAYMENT_METHOD，CURRENCY 必填 + CONSUMER_COUNTRY）均 201；重复 RECURRING 409；FEATURE 带支付方式 400；可配维度 THREE_DS_RESULT 400；编辑 200 且 changeVersion +1；停用 STC_PAY → RETIRED，DEMO2-CL-103 已开通的 STC_PAY 仍 ENABLED，DEMO2-CL-102 新开 STC_PAY 被拒 400；重新启用 → ACTIVE（运行 2）。代码格式错误 `recurring-x` 返回 **422 VALIDATION_ERROR**（V1） |
| AC-02 迁移后目录 | 通过 | ACQUIRING = CARD / APPLE_PAY / GOOGLE_PAY（PAYMENT_METHOD）+ TOKEN_STORAGE（FEATURE），无 CARD_*；CARD 可配 CARD_BRAND / FUNDING_TYPE / ISSUER_COUNTRY / ISSUER_REGION / CARD_LEVEL / CONSUMER_COUNTRY / CURRENCY(必填)；TOKEN_STORED → FEATURE / TOKEN_STORAGE，其余收单事件 → PAYMENT_METHOD；维度清单 14 项，THREE_DS_RESULT / TRANSACTION_TYPE / DISPUTE_STAGE 不可开通 |
| AC-03 开通条件 | 通过 | DEMO2-CL-101：缺 CURRENCY、DESTINATION_COUNTRY（CARD 不可配）、THREE_DS_RESULT（不可开通）、币种 `US` 均 400；「CARD + VISA,MASTERCARD + DEBIT + AED,USD + 3DS」201 读回一致；PUT 为 VISA + DEBIT + AED + 3DS 200 读回一致（运行 1） |
| AC-04 标准价目 | 通过 | 4 套模板（标准 / 优惠 / 平台费 / 外汇点差）：ownerType=PLATFORM_TEMPLATE、无合同；A 复核自己提交被拒；合同复核路由处理模板版本被拒；B（TEMPLATE_APPROVE）复核后 APPROVED 无需签署即排期，到点 ACTIVE（运行 1）。模板版本做价格试算（BFF simulate）与 commercial 直连 `/commercial-prices/v1/resolutions` 均不命中（运行 2、3） |
| AC-05 基于模板生成 | **通过（D1 修复后复验，终版镜像回归通过）；首轮失败 D1** | 首轮通过项：Amex 不在接受条件不生成；DEMO2-CL-102 生成 CARD 四类 + Apple Pay × AED/USD 共 10 行、无 Google Pay、无 TOKEN_STORED 组件（存卡未开通）；DEMO2-CL-103 8 行；DEMO2-CL-901 外汇未开通基于外汇模板生成 0 行；每行带 sourceTemplatePriceId，版本带 sourceTemplateVersionId。失败：DEMO2-CL-101（CARD 只开 AED）多生成 USD 的 CARD_VISA_DEBIT、CARD_STANDARD 两行，每行只有 TOKEN_STORED_FIXED 组件（D1，v1 保留作修复前证据）。**修复后**：DEMO2-CL-101 v2（238417967673577472）基于标准价 v2 只生成 AED 的 Visa 借记 / 境外卡 / 兜底三行，AED Visa 借记仍带 TOKEN_STORED；补齐也只追加 AED 境外卡。终版镜像回归：DEMO2-CL-901（CARD VISA/MC + AED、存卡已开通）基于标准价 v2 生成 v5（238419651502084096，DRAFT）只有 AED 三行、无 Amex、无 USD 存卡费行、无相关性提示 |
| AC-06 校验 | 通过（D1 已修复） | DEMO2-CL-901：零开通 v1 排期 400；完整性 v2（CARD 限 VISA / MC，仅 Visa 价）排期 400，detail 含 MASTERCARD；相关性 v3 保存 200 返回 `PAYMENT_METHOD_NOT_ENABLED`（AED/APPLE_PAY_STANDARD）与 `CURRENCY_NOT_ENABLED`（USD/CARD_STANDARD），读模型同样返回，排期 400 且 detail 含 APPLE_PAY；Partner 条款保存无提示。首轮例外：D1 的 USD 存卡费行保存无提示、排期通过。**修复后**：DEMO2-CL-901 开通 TOKEN_STORAGE（CARD 只开 AED），v4（238417973285556224）含「USD CARD_STANDARD 只带存卡费」行，保存 200 并返回 `CURRENCY_NOT_ENABLED`，签署后排期 400：「激活校验 2（价格引用）：价格与开通项不相关（CURRENCY_NOT_ENABLED）: AE001/USD/CARD_STANDARD，价格币种不在开通项币种条件内: USD」；DEMO2-CL-101 v1 读模型对两条 USD 行返回 `CURRENCY_NOT_ENABLED` |
| AC-07 破标 | 通过 | DEMO2-CL-101 Visa 借记 AED 费率 1.80% → 1.50%、SALE_FIXED 1.00 → 0.80：CAPTURE_PCT / SALE_PCT / SALE_FIXED 标 BELOW_STANDARD（standardRate 0.018），其余有来源组件 AT_OR_ABOVE；无来源 CARD_PREMIUM 行 NO_BENCHMARK；版本 belowStandardCount=3，待复核期间工作台队列与版本列表均显示 3（运行 1） |
| AC-08 签署 | 通过 | DEMO2-CL-101 v2 登记签署 PA-DEMO2-CL-101-V2 后排期，到点 ACTIVE（修复后）；DEMO2-CL-102 APPROVED 未签排期 400（detail 含「签署」），停在 APPROVED；DEMO2-CL-101 签署方为 OE Party 被拒，登记 CLIENT 签署（E_SIGNATURE、文件 ID、SHA-256、时刻、编号 PA-DEMO2-CL-101-V1）→ CLIENT_SIGNED，同编号同内容重放返回原签署记录，排期后到点 ACTIVE，列表 hasAcceptance=true、非免签；DEMO2-PT-101 未签排期 400、平台签约方签署被拒、LOCAL_PARTNER 签署 → CLIENT_SIGNED → ACTIVE；存量 DEMO-CL-001-ACQ v1 ACTIVE / v2 SCHEDULED 免签，DEMO-CL-002-ACQ v1 PENDING_APPROVAL 非免签。签署方式枚举为 E_SIGNATURE / CLICK_ACCEPTANCE / MANUAL_SIGNATURE（V2） |
| AC-09 Checkout 输出 | **环境阻塞（部分）** | mor-test 直连 `/checkout-context/v1/resolutions`（acquiring 调用方）对 DEMO2-CL-103 与 CL-F1105E 的 Client 均返回 422 `CLIENT_NOT_ELIGIBLE`（client-master 候选阶段）；commercial 装配的 Risk 资格客户端为 `UnavailableCheckoutQualificationClient`（总是失败关闭），mor-test 无法端到端产出 Checkout 响应。间接：DEMO2-CL-103 新开 GOOGLE_PAY 后已开通、生效版本无该开通项价格，费率卡按开通项分组 CARD 有价、GOOGLE_PAY 无价。输出字段与「无价不输出」由 commercial auto `CheckoutResolutionMatrixTest`（test_normalFlow_withPaymentMethod：conditions + require3ds；test_capabilityWithoutPriceIsNotReturned）覆盖，阶段 5 193/193 |
| AC-10 标准价已更新 | 通过（当时绕过 D2；D2 已修复） | 标准价 v2（复制 v1，Visa 借记 AED 1.80% → 1.70%）生效后，DEMO2-CL-101 v1 `templateUpdate = {latestTemplateVersionId: 238411696840970240, latestVersionNo: 2}`；合同价格逐字段不变（运行 3）。v2 保存需脚本补回结算四字段（D2） |
| AC-11 迁移 | 通过 | CL-F1105E（VISA；AED,USD；保证金 5% / 180 天保留）、DEMO-CL-001（AMEX,MASTERCARD,VISA；AED,USD）、DEMO-CL-002（VISA；AED）各一条 CARD，无 CARD_* 残留；DEMO-CL-001 BCR 历史仍含 CARD_VISA / CARD_MC / CARD_AMEX 开通记录；迁移前备份与当前表逐行一致：价格 50/50、组件 257/257、档位 6/6、画像 14/14、画像条件 16/16；DEMO-CL-001-ACQ v1 试算 6 组事实命中画像与语义一致（VISA 信用本地 → CARD_VISA_CREDIT_DOMESTIC，USD Amex → CARD_AMEX，AED Amex → CARD_STANDARD，MC 境外 → CARD_INTERNATIONAL，Visa 借记白金 → CARD_PREMIUM，AED Apple Pay → APPLE_PAY_STANDARD）。人工确认项见 release.md §3 |
| AC-12 页面 | 通过 | 商务页 17 / 17、全部运营页回归 49 / 49，控制台错误与 ≥400 均为 0（第 6 节）；D1 修复版与终版镜像上商务页各 18 / 18 通过；D2 修复后模板版本读回带结算字段，原样保存 200 |
| AC-13 测试数据 | 通过 | AE001 标准 / 优惠模板（另有平台费、外汇点差），覆盖「只收 Visa 借记卡」「Apple Pay 限卡组织」「基于模板生成并破标」「待客户签署」「已签署生效」「已开通待定价」「Partner 签署」及校验反例（第 5 节） |

## 3. 缺陷

**D1 基于模板生成 / 补齐与相关性校验：功能类组件让不相关的价格行通过。——已修复并复验通过。**
- 复现：DEMO2-CL-101 开通 CARD（VISA + DEBIT + AED）与 TOKEN_STORAGE；标准价 v1 的 USD CARD_VISA_DEBIT、USD CARD_STANDARD 含 TOKEN_STORED_FIXED。基于该模板生成 v1 后，除 AED 3 行外多出 USD 两行，每行只有 TOKEN_STORED_FIXED；保存 relevanceWarnings 为空，排期 / 激活通过；补齐同样保留。当前 v1（238411010732527616）已 ACTIVE，包含这两行。
- 原因：`TemplatePriceSelection.add` 逐组件判断相关性，不相关组件剔除后，只剩功能类组件的价格行仍然生成；`TermPriceRelevance.featureIssue` 只检查同代码功能是否开通，不检查价格画像的支付方式与币种是否落在已开通的 PAYMENT_METHOD 开通项内。technique.md §4.4（币种 ∈ 该开通项 CURRENCY 条件）与 §4.7（每条价格须对应一个 ENABLED 开通项）都要求拒绝这类行。
- 影响：合同可以对不能交易的币种 / 支付方式收存卡费；演示合同 DEMO2-CL-101 带有这两行。Web 版本编辑器按开通项分组时已把这两行列入「未对应开通项」，并提示「2 条价格未对应已开通的开通项……激活时将被拒绝」（截图 `assets/stage7/06-version-101-benchmark.png`），与 Owner 实际放行不一致。
- 修复：主会话交 A 线，相关性先校验价格行锚定已开通支付方式开通项，再校验功能；生成 / 补齐同步（commit 9ed4c39c，commercial 195/195），mor-test 于 21:30:17Z 部署 `sha256:83a05343…`（capability-20261021b）。
- 复验（`stage7_recheck_d1.py`，10 / 10 通过）：

| 检查 | 修复前（v1 证据） | 修复后 |
|---|---|---|
| DEMO2-CL-101 基于标准价生成 | v1 238411010732527616：AED 3 行 + USD 两行只带存卡费 | v2 238417967673577472：只 AED 3 行（Visa 借记 / 境外卡 / 兜底），Visa 借记保留存卡费 |
| 从标准价补齐 | 补回境外卡，USD 两行仍在 | 只追加 AED 境外卡 |
| 保存含「USD 只带存卡费」行 | relevanceWarnings 为空 | DEMO2-CL-901 v4：`CURRENCY_NOT_ENABLED`（USD/CARD_STANDARD） |
| 排期 / 激活 | 通过，v1 ACTIVE | DEMO2-CL-901 v4 排期 400（detail 原文见 §2 AC-06） |
| v1 读模型相关性提示 | 空 | 两条 USD 行均返回 `CURRENCY_NOT_ENABLED`（Owner 与 Web 分组一致） |
| DEMO2-CL-101 当前生效 | v1 | v2 ACTIVE（破标 3，签署 PA-DEMO2-CL-101-V2，无相关性提示，来源标准价 v2，无「标准价已更新」）；v1 SUPERSEDED 保留 |

- 页面：`assets/stage7/06c-version-101-v2-after-fix.png`，修复版上商务页 18 / 18 通过（`commercial-capability-stage7-browser-pages-after-fix.json`）。
- 终版镜像回归（`sha256:c6c9b8f6…`，`stage7_recheck_d2.py`）：DEMO2-CL-101 v2 ACTIVE、只有 AED 三行、无相关性提示；v1 SUPERSEDED 两条 USD 行返回 `CURRENCY_NOT_ENABLED`；DEMO2-CL-901 基于标准价 v2 生成 v5 只有 AED 三行。
- 说明：DEMO2-CL-101 v2 的生成、复核、签署、排期与激活在 b 镜像（D1 修复）上完成，终版镜像只修改模板读模型（D2），没有为 101 再出 v3（避免多余版本）；终版生成规则由 DEMO2-CL-901 v5 回归证明。

**D2 模板版本读模型不返回结算四字段。——已修复（a9828758）并复验通过。**
- 复现：GET `/commercial-terms/versions/{模板版本 ID}` 的每条价格 `settlement = null`（库中为 2 / AE_BUSINESS_DAYS / UTC / 86399）。按读回内容原样 PUT 模板版本价格（复制 v1 建 v2 后改价）返回 400「Client 条款价格结算四字段必须全部填写且时区为 UTC: AE001/AED/CARD_VISA_DEBIT」。
- 原因：`CommercialPriceConverter.java:63` 写的是 `view.plan().getClientId() == null ? null : settlement(price)`，模板方案的 clientId 为空。
- 影响：Web 价格抽屉以读回的 `settlement` 填表，四个结算字段为必填（`commercial.ts:518`、`PriceEditorDrawer.tsx:165`），因此编辑模板版本时每条价格都要重新录入结算字段才能保存；模板版本对比的结算字段为空。浏览器验收没有提交模板保存（不留 DRAFT 杂版本），结论依据 API 复现与源码。
- 修复前证据：运行 3 的探测项 `settlement: [null, null]`，原样 PUT 400（detail 见上）；AC-10 的标准价 v2 由脚本补回结算字段才保存成功。
- 修复后（终版 `sha256:c6c9b8f6…`，`stage7_recheck_d2.py` 8 / 8）：标准价 v2 每条价格读回 `settlement = {2, AE_BUSINESS_DAYS, UTC, 86399}`；外汇点差模板复制 v1 建 v2（238419652198338560），DRAFT 读回带结算字段，按读回内容原样 PUT（不补任何字段）返回 200，保存后价格与 v1 逐字段一致；复核 → 排期 → 到点 ACTIVE，v1 被取代，未留 DRAFT。

## 4. 偏差（与 technique.md 不一致但符合系统规则或可接受）

- V1（主会话确认接受，记为合同措辞偏差）：请求体字段格式错误（如 capabilityCode `recurring-x`）返回 422 `VALIDATION_ERROR`，technique.md 写 400；与 [RFC 9457 系统决定](../../../../agent-docs/decisions/2026-09-06-system-wide-rfc-9457-problem-details.md)「Body 字段错误用 VALIDATION_ERROR」一致。
- V2：价格协议签署方式实现为 E_SIGNATURE / CLICK_ACCEPTANCE / MANUAL_SIGNATURE，technique.md §3.2 写 WET_SIGNATURE。
- V3：DEMO2-PT-101 使用新的合成 Local Partner 770000001002 / Party 880000001002 / 881000001002：DEMO-PT-001 已占用 770000001001 的 PARTNER_SHARE 方案，同 Partner 同条款类型方案唯一；mor-test 无 Local Partner 主档。

## 5. 测试数据（AC-13，mor-test `mor_commercial`）

| 对象 | ID | 状态 / 内容 |
|---|---|---|
| 目录 RECURRING「周期扣款」 | 238410801050882048 | ACQUIRING / FEATURE / ACTIVE |
| 目录 STC_PAY「STC Pay」 | 238410801365454848 | ACQUIRING / PAYMENT_METHOD / ACTIVE（停用再启用过），CURRENCY 必填 + CONSUMER_COUNTRY |
| 画像 CARD_VISA_DEBIT「Visa 借记卡」 | 238410802716020736 | ACTIVE（VISA + DEBIT） |
| 画像 CARD_VISA「Visa 卡（全部）」 | 238410803034787840 | ACTIVE（VISA；完整性反例用） |
| 模板 TPL-AE001-STANDARD「标准价 · AE001」 | 方案 238410803311611904 | v1 238410803500355584 SUPERSEDED；v2 238411696840970240 ACTIVE（Visa 借记 AED 1.70%）；CARD 兜底 / Visa 借记 / 境外卡 / Amex / Apple Pay / Google Pay × AED、USD，Visa 借记与兜底含 TOKEN_STORED 存卡费 |
| 模板 TPL-AE001-PROMO「优惠价 · AE001」 | 方案 238410804934807552 | v1 238410805534593024 ACTIVE（标准价各费率 −0.30%） |
| 模板 TPL-AE001-PLATFORM「平台费标准价 · AE001」 | 方案 238410806251819008 | v1 238410806360870912 ACTIVE（PLATFORM_FEE：月费 99、开户 500、KYB 150 USD） |
| 模板 TPL-AE001-FX「外汇点差标准价 · AE001」 | 方案 238410806939684864 | v1 238410807065513984 SUPERSEDED；v2 238419652198338560 ACTIVE（D2 复验：原样复制保存，FX_SPREAD USD/AED 0.50%） |
| DEMO2-CL-101 只收 Visa 借记卡 + 存卡（Client 237573476041465857） | 合同 238411008014618624 | CARD（VISA + DEBIT + AED + 3DS）+ TOKEN_STORAGE；方案 DEMO2-CL-101-ACQ 238411010594115584：v2 238417967673577472 **ACTIVE**（基于标准价 v2 生成、破标 3、已签署 PA-DEMO2-CL-101-V2，3 行 AED）；v1 238411010732527616 SUPERSEDED（基于标准价 v1、破标 3、已签署 PA-DEMO2-CL-101-V1、显示「标准价已更新 v2」，含 D1 修复前的两条 USD 存卡费行，按主会话要求保留） |
| DEMO2-CL-102 Apple Pay 限 Visa / MC + 银行卡全卡组织（237573382420406273） | 合同 238411678889349120 | APPLE_PAY（VISA,MASTERCARD；AED,USD）+ CARD（AED,USD）；方案 238411679979868160，v1 238411680076337152 **APPROVED（待客户签署）**，基于优惠价 10 行 |
| DEMO2-CL-103 已开通待定价（237573267559391233） | 合同 238411681049415680 | CARD（AED,USD）+ 生效后新开 GOOGLE_PAY（AED）与 STC_PAY（AED）均无价；方案 238411681900859392，v1 238411682005716992 **ACTIVE**（基于标准价 v1 8 行，已签署） |
| DEMO2-PT-101 Partner 分润（合成 Partner 770000001002） | 合同 238411682894909440 | 方案 DEMO2-PT-101-SHARE 238411683318534144，v1 238411683410808832 **ACTIVE**（CARD_STANDARD AED/USD 分润 35%，LOCAL_PARTNER 签署 PA-DEMO2-PT-101-V1） |
| DEMO2-CL-901 校验反例（237573040244891649） | 合同 238411684283224064 | CARD（VISA,MASTERCARD；AED）；方案 DEMO2-CL-901-ACQ 238411684908175360：v1 零开通、v2 完整性、v3 相关性、v4 存卡费锚定（D1 复验，238417973285556224）均 CLIENT_SIGNED 且排期被拒（不会生效）；v5 238419651502084096 DRAFT（终版镜像生成回归，AED 三行）；另开通 TOKEN_STORAGE（复验用）；DEMO2-CL-901-FX 238411687491866624 v1 DRAFT 0 行 |

迁移示例（保留）：CL-F1105E、DEMO-CL-001～004、DEMO-PT-001。合成审批人每次运行新建、结束停用并清空角色权限（运行 1–5 与 D1 / D2 复验共 7 个，账号 / 角色 ID 见各运行记录 `ids.checkerAccountId / checkerRoleId`）。

## 6. 浏览器验收（AC-12）与观察

脚本 [stage7_browser.py](./stage7_browser.py)，A 登录真实入口，所有非 GET 请求一律拦截（本次 0 次触发），弹窗打开截图后取消。

| 项 | 结果 | 证据 |
|---|---|---|
| 商务页 17 项（含新页与新交互；修复后加 06c 共 18 项，b / c 两个镜像各复跑 18 / 18：`…-browser-pages-after-fix.json`、`…-browser-pages-after-d2.json`） | **17 / 17 通过**，控制台错误 0、HTTP ≥ 400 0、无权限 0 | `local-environment/e2e/runtime/commercial-capability-stage7-browser-pages.json`；截图 `assets/stage7/01–17*.png` |
| 全部运营页只读回归（pageCapabilities.ts 49 个路由，每页 7 秒） | **49 / 49 通过** | `commercial-capability-stage7-browser-regression.json` |

截图要点：
- `01-workbench`：待审批版本表含「破标」列；`02-catalog` + `02b`：产品区块「+ 新增开通项」、开通项「可配维度」列（CURRENCY * 必填）、种类「功能」、计费事件「挂接开通项」（TOKEN_STORED → 功能 · 存卡）、编辑 / 停用操作及新增弹窗。
- `03-templates`、`04-template-version-std-v2`：四套模板列表；标准价 v2 工作区（无合同、无签署）。
- `05a` / `05b`：DEMO2-CL-101 开通项标签「卡组织 VISA · 币种 AED · 借贷记类型 DEBIT · 3DS」，编辑弹窗按可配维度渲染条件。
- `06-version-101-benchmark`：来源「标准价 · AE001 v1」、破标 3、客户已签署 PA-DEMO2-CL-101-V1、「标准价已更新（v2）」提示、按开通项分组、组件「破标」红标与「无标准可比」，以及 D1 的「未对应开通项」分组。
- `07` / `07b`：DEMO2-CL-102 「已批准 · 待客户签署」，「价格协议尚未签署」提示，登记客户签署弹窗（签署方由合同 CLIENT 参与方带出，电子签 / 点击接受 / 线下签署，文件 ID、SHA-256、编号）。
- `08`：DEMO2-CL-901 v3 相关性提示与「未对应开通项」；`09`：DRAFT 版本「从标准价目补齐」；`10b`：新建版本弹窗含「基于标准价目」。
- 风格与现有商务页一致（同一套表格、标签、提示条与抽屉组件）。观察 O1：模板版本工作区对每个组件都显示「无标准可比」，模板本身不需要比较，标签属冗余（不影响功能）。

观察（发布后至 21:58Z，含 21:30Z、21:53Z 两次 commercial 重部署后）：

| 项 | 结果 |
|---|---|
| commercial / ops-bff / platform / ops-web 日志 ERROR | 0 / 0 / 0 / 0（commercial 为终版容器 c6c9b8f6）；重启 0。WARN 只有启动期 Bean 后处理与 LoadBalancer 缓存提示（commercial 14、platform 14、ops-bff 1） |
| `mor_commercial.t_compensation_event` | 0 行（无残留；发布后无新增行） |

## 7. 遗留

1. ~~D1~~：已修复、复验通过，DEMO2-CL-101 v2 已生效。
2. ~~D2~~：已修复、复验通过。
3. AC-09（主会话按环境限制接受）端到端待 Risk 资格合同接入且 client-master 有可用的 Client 资格后，在 mor-test 复验 Checkout 解析输出（DEMO2-CL-103：CARD 输出、GOOGLE_PAY / STC_PAY 不输出）。
4. 合成审批人 7 个已停用、角色权限已清空，未删除（IAM 无删除接口）。

## 8. 验收反馈 F1–F3（ops-web capability-20261021d）

> 2026-10-21 03:30–03:44Z。ops-web 03:25:35Z 替换为 `sha256:333e7f2b…`（入口 `index-DPjes7gL.js`，源码 cp-web `41f129c8`）。脚本 [stage7_feedback_f.py](./stage7_feedback_f.py)，A 登录真实入口；除第 3 步外所有非 GET 请求一律拦截（本次 0 次触发）。**结论：17 / 17 通过；商务页 18 / 18、全部运营页 49 / 49 回归通过；控制台错误与 HTTP ≥ 400 均为 0。**

| 项 | 结果 | 证据 |
|---|---|---|
| F1 已开通项置灰、点击编辑 | 通过 | DEMO2-CL-101「新增开通项」抽屉列出 ACQUIRING 下全部 ACTIVE 项（RECURRING、STC_PAY、APPLE_PAY、GOOGLE_PAY、CARD、TOKEN_STORAGE）；CARD、TOKEN_STORAGE 置灰标「已开通」，其余未置灰；点 CARD 切换为「编辑开通项 · AE001 / 银行卡」（`f-01`、`f-02`） |
| F2 接受条件逐维度一行 | 通过 | 回填卡组织 VISA、借贷记类型 借记、币种 AED、3DS 开（末行「要求 3DS」）；维度标签全为中文（代码在 Tooltip）；枚举行首个「不限」并有「+ 其他」，币种行无「不限」有「+ 其他」；消费者国家输入「阿联」出现「AE 阿联酋」；汇总「接受：卡组织 VISA · 卡等级不限 · 消费者国家不限 · 币种 AED · 借贷记类型 借记 · 发卡国家不限 · 发卡区域不限 · 要求 3DS」；点卡组织「不限」→「卡组织不限」，再选 MASTERCARD →「卡组织 MASTERCARD」（未保存，取消）（`f-02`–`f-04`）。Apple Pay：币种 / 消费者国家 / 卡组织 / 借贷记类型 + 要求 3DS；不填币种点保存提示「币种必填」，未发出写请求（`f-05`） |
| F3 右侧抽屉 | 通过 | 合同新增 / 编辑开通项抽屉宽 760、分段（基本 / 接受条件 / 有效期与保证金）、底部固定「取消 / 保存」；产品目录新增抽屉（基本 / 可配维度，取消 / 创建）、编辑「编辑开通项 · RECURRING」（取消 / 保存），均取消未保存（`f-08`、`f-09`）；390px 窄屏抽屉宽 390，抽屉体与页面横向溢出均为 0（`f-10`） |
| 抽屉提交链路（真实保存一次） | 通过 | DEMO2-CL-901（反例合同）经「新增开通项」点已开通的「存卡」切换为编辑后直接保存：PUT `/commercial-admin/v1/agreements/238411684283224064/capabilities/238417973038092288` → 200，请求体 `{expectedChangeVersion: 8, effectiveFrom: "2026-10-21T00:47:54+04:00", effectiveTo: null, rollingReserveRate: null, rollingReserveDays: null, conditions: [], require3ds: false, status: "ENABLED"}`（内容与原值一致），抽屉关闭、列表刷新（`f-06`、`f-07`）；库中该行内容与 update_time 未变 |
| 数据未被改动 | 通过 | root 只读核对：DEMO2-CL-101 CARD 仍为 VISA + DEBIT + AED + 3DS、TOKEN_STORAGE 未变；DEMO2-CL-101/102/103/PT-101 无写请求 |
| 回归 | 通过 | 商务页 18 / 18（`commercial-capability-stage7-browser-pages-feedback-f.json`）；全部运营页 49 / 49（`…-browser-regression-feedback-f.json`，对账会话同期清理数据未造成错误）；F 验收记录 `commercial-capability-stage7-feedback-f.json` |
| 观察 | 正常 | 03:25Z 起 commercial / ops-bff / ops-web 日志 ERROR 0、重启 0 |

- 回归脚本 [stage7_browser.py](./stage7_browser.py) 随 F3 调整两处断言：弹窗定位增加抽屉区块 `.ant-drawer-section`（开通项已由弹窗改为抽屉）；合同开通项编辑弹窗文本接受「借记」（F2 改为中文取值）。
- 本节未使用合成审批人。

## 9. 验收反馈 F4–F6（ops-web capability-20261021e）

> 2026-10-21 04:00–04:09Z。ops-web 03:58:51Z 替换为 `sha256:139c157e…`（入口 `index-DmG4YUUF.js`，源码 cp-web `32d256a7`、`c9229fb2`）。脚本 [stage7_feedback_f2.py](./stage7_feedback_f2.py)（复用 stage7_feedback_f.py 的登录、拦截与抽屉辅助），A 登录真实入口，**全程只读**：非 GET 请求一律拦截，本次 0 次触发，写入 0。**结论：15 / 15 通过；商务页 18 / 18、全部运营页 49 / 49 回归通过；控制台错误与 HTTP ≥ 400 均为 0。**

| 项 | 结果 | 证据 |
|---|---|---|
| F4 必填维度在前 | 通过 | DEMO2-CL-101「产品与开通」收单区块编辑 CARD：接受条件第一行为必填「币种」（带必填标记），汇总以「接受：币种 AED · 卡组织 VISA · …」开头（`f2-01`） |
| F5 币种快捷标签 + 可搜索下拉 | 通过 | 快捷标签 AED / USD / SAR / EUR / GBP（无「不限」）+「更多币种」下拉；搜「科威特」出现「KWD 科威特第纳尔」（`f2-02`）；选中后快捷标签 AED 仍选中、下拉已选 AED + KWD、汇总「币种 AED、KWD」；再点快捷标签 USD，下拉同步含「USD 美元」，汇总「币种 AED、KWD、USD」（`f2-03`）；取消，无写请求 |
| F6 页签与分组 | 通过 | DEMO2-CL-103 页签依次为「参与方与签署（2）」「产品与开通（3）」「条款版本」，无「订阅产品」「开通项」页签；「Acquiring ACQUIRING · 3 个开通项」区块列银行卡 CARD（币种 AED / USD）、Google Pay GOOGLE_PAY（币种 AED）、STC Pay STC_PAY（币种 AED），状态「开通」、各有「编辑」；页底「未订阅：Payout、FX、Dispute、Data Analytics；默认具备：Tax and Invoice、Platform」（`f2-04`） |
| F6 区头开通抽屉 | 通过 | 收单区头「+ 开通该产品的项」→「开通 · Acquiring」抽屉只列收单 6 项，CARD / GOOGLE_PAY / STC_PAY 置灰（`f2-05`）；CL-F1105E 出款区头抽屉只列 PAYOUT_LOCAL / PAYOUT_SWIFT（`f2-08`）；均取消 |
| F6 已订阅无开通项提示 | 通过 | 只读查得 CL-F1105E（238132079668301824）已订阅 PAYOUT 但无出款开通项，无需在 DEMO2-CL-901 制造数据；出款区块「0 个开通项」，黄色提示「已订阅，尚未开通任何项 · 可开通：Local Payout、SWIFT Payout」（`f2-07`） |
| F6 修改订阅 | 通过 | 页签头「修改订阅」打开「订阅产品」弹窗，取消（`f2-06`） |
| 回归 | 通过 | 商务页 18 / 18（`commercial-capability-stage7-browser-pages-feedback-f2.json`）；全部运营页 49 / 49（`…-browser-regression-feedback-f2.json`；对账会话同期演示数据只读，未改动）；F2 验收记录 `commercial-capability-stage7-feedback-f2.json` |
| 观察 | 正常 | 03:58Z 起 commercial / ops-bff / ops-web 日志 ERROR 0、重启 0 |

观察（不影响通过，供后续优化）：
- O2：「更多币种」下拉较窄，已选多个币种时折叠为「+3 …」，下拉框本身看不到已选代码（快捷标签与汇总句可见）。
- O3：产品区块列表的条件标签显示原始取值代码（如「借贷记类型 DEBIT」），抽屉与汇总句显示中文「借记」，两处口径不一致。

- 回归脚本 [stage7_browser.py](./stage7_browser.py) 随 F6 调整一处定位：合同详情页签由「开通项」改为匹配「产品与开通|开通项」。
- 本节未写数据，未使用合成审批人。

## 10. 观察 O2 / O3 修复（ops-web capability-20261021f）

> 2026-10-21 05:20–05:34Z。ops-web 05:17:27Z 替换为 `sha256:46227f3d…`（入口 `index-BrDxuwBS.js`，源码 main `5a103f36`）。脚本 [stage7_feedback_f3.py](./stage7_feedback_f3.py)，A 登录真实入口，**全程只读**：非 GET 请求一律拦截，本次 0 次触发，写入 0。**结论：O2、O3 修复通过（7 / 7 项）；另发现 O4（费率预览开通项条件被 3DS 遮蔽，见下）；商务页 18 / 18、运营页 57 / 57 回归通过；控制台错误与 HTTP ≥ 400 均为 0。**

| 项 | 结果 | 证据 |
|---|---|---|
| O2 下拉不含快捷币种 | 通过 | DEMO2-CL-101 编辑 CARD：「更多币种」选项无 AED / USD / SAR / EUR / GBP（`f3-01`） |
| O2 多选展开 | 通过 | 选 KWD、QAR、BHD 后下拉内三项全部显示，无「+N」折叠（`f3-02`） |
| O2 汇总同步 | 通过 | 汇总「币种 AED、KWD、QAR、BHD」 |
| O2 切换快捷标签不丢下拉选择 | 通过 | 选 USD、取消 AED 后快捷标签只剩 USD，下拉三项仍在，汇总「币种 USD、KWD、QAR、BHD」（`f3-03`）；取消，未保存 |
| O3 产品与开通条件标签中文 | 通过 | DEMO2-CL-101 收单区块 CARD 行：「卡组织 VISA」「币种 AED」「借贷记类型 借记」「要求 3DS」（蓝色 `ant-tag-blue`），无 DEBIT 原码（`f3-04`） |
| O3 费率预览 3DS 蓝色 | 通过 | 费率预览 Client 237573476041465857：CARD 行「要求 3DS」为蓝色 `ant-tag-blue`（`f3-05`） |
| 回归 | 通过 | 商务页 18 / 18（`commercial-capability-stage7-browser-pages-feedback-f3.json`）；运营页按**已部署包**的页面清单回归 **57 / 57**（`…-browser-regression-feedback-f3.json`）。已部署包 57 个页面：比此前 49 页多出币种会话与对账会话的页面；与 main `pageCapabilities.ts`（也是 57 条）相比，部署包多 `/currency-specs`、少 `/finance/reconciliation/channel-lines`，故以部署包为准 |
| 观察 | 正常 | 05:17Z 起 commercial / ops-bff / ops-web / platform 日志 ERROR 0、重启 0 |

**新发现 O4（费率预览开通项条件被遮蔽，不属本次 O2 / O3 范围）：** 费率预览页 DEMO2-CL-101 的 CARD 行「接受条件」只显示「要求 3DS」，缺少卡组织 VISA、币种 AED、借贷记类型 借记（`f3-05`）。
- 原因：BFF `GET /commercial-admin/v1/rate-cards` 的开通项仍返回旧形态 `limits[]`（CARD_BRAND=VISA、CURRENCY=AED、FUNDING_TYPE=DEBIT），`conditions` 为 null；Web `CommercialRateCards.tsx` 在 `row.conditions.length || row.require3ds` 时走新分支，`require3ds=true` 使 `limits` 被忽略。
- 影响：所有开启 3DS 的开通项在费率预览中条件不全；未开 3DS 的开通项仍按 `limits` 显示（原码形态）。
- 建议：BFF 费率卡返回 `conditions`（与合同详情一致），或 Web 在 `conditions` 为空时把 `limits` 换成条件标签再追加「要求 3DS」。
- 运行记录 `commercial-capability-stage7-feedback-f3.json` 中「费率预览 … 条件标签中文」一项为失败，即此发现；3DS 蓝色本身通过。
- **O4 已修复并复验通过（05:37–05:39Z）**：ops-web 05:36:06Z 替换为 capability-20261021g（`sha256:c6d2118f…`，入口 `index-Bl5tVr7r.js`；费率卡开通项只有 `limits` 时前端按维度合并为接受条件）。脚本 [stage7_feedback_o4.py](./stage7_feedback_o4.py)，只读，3 / 3 通过（`commercial-capability-stage7-feedback-o4.json`）：
  - DEMO2-CL-101（Client 237573476041465857）费率预览 CARD 行：「卡组织 VISA」「币种 AED」「借贷记类型 借记」「要求 3DS」，仅「要求 3DS」为蓝色（`f3-06`）。
  - DEMO2-CL-103（Client 237573267559391233，不开 3DS）：CARD「币种 AED / USD」、GOOGLE_PAY「币种 AED」、STC_PAY「币种 AED」，无 3DS 标签（`f3-07`）。
  - 商务页回归 18 / 18（`commercial-capability-stage7-browser-pages-o4.json`）；控制台错误、HTTP ≥ 400、写请求均为 0；05:36Z 起 ops-web / ops-bff / commercial 日志 ERROR 0。

- 回归脚本 [stage7_browser.py](./stage7_browser.py) 随 O3 调整一处断言：合同开通项表格接受「借记」或「DEBIT」。
- 本节未写数据，未使用合成审批人。
