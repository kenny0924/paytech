# M5 报告：商务（commercial）文案迁移

执行日期 2026-10-23 · 研发目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-m5`（含 F0 成果），未提交、未推送，主工作区只写本报告。未调用 Superpowers skill，未派生子代理。

## 1. 迁移文件

**命名空间 `commercial`：785 个 key**（`src/i18n/en/commercial.json` 与 `i18n/zh-CN/commercial.json`，key 集合一致）。

- 模型（`translator('commercial')`，调用时取文案）：`commercial/commercial.ts`、`commercialConsole.ts`、`commercialPreview.ts`、`catalogTree.ts`、`catalogMaintenance.ts`、`componentTable.ts`、`capabilityConditions.ts`、`productCapabilities.ts`、`versionFlow.ts`、`benchmark.ts`
- 组件（`useT('commercial')`）：`commercial/` 下 `AgreementDetail`、`AgreementModals`、`CatalogModals`、`ClientIdInput`、`CommercialParts`、`ComponentTable`、`ConditionFields`、`PriceEditorDrawer`、`PriceMatrixView`、`ProductCapabilitySections`、`TemplateVersionWorkspace`、`TermModals`、`TermVersionEditor`、`VersionCompare`
- 页面：`pages/CommercialAgreements`、`CommercialCatalog`、`CommercialHistory`、`CommercialPriceSimulator`、`CommercialPricingProfiles`、`CommercialRateCards`、`CommercialTemplates`、`CommercialTerms`、`CommercialWorkbench`（h1 与菜单同名的用 `nav.resource.<CODE>`；工作台 h1「商务工作台」与菜单「工作台」不同，用本模块 key）
- 测试改为英文断言：`benchmark`、`capabilityConditions`、`catalogMaintenance`、`catalogTree`、`commercial`、`commercialConsole`、`commercialPreview`、`componentTable`、`productCapabilities`、`versionFlow`（`.test.ts`）；业务数据中文保留；未删测试，另补断言（挂接选项文字、币对格式、签署方式 / 时刻 / 编号问题、BUSINESS_CHECK_FAILED 无原因兜底、未登记维度用清单名称等）。
- `src/i18nSourceGuard.test.ts`：`MIGRATED_DIRS` 加入 `commercial/` 与 9 个 `pages/Commercial*.tsx`。

**主要改法**
- 标签映射表全部删除，改为 `commercial.enum.<种类>.<值>` + `enumLabel`：agreementStatus、versionStatus、profileStatus、capabilityStatus、agreementType、partyRole、acceptanceMethod、profileKind、pricingModel、feeTaxTreatment、componentType、termType、catalogTermType、capabilityKind、catalogStatus、aggregateRootType、diffChange、dashboardMetric、signatureState、generationMode、calculationBase、refundBehavior、bandBasis、bandMode、relevanceReason、dimension、currencyName、country、conditionValue.<维度>。`StatusTag` 的 `labels` 映射参数改为 `kind`（枚举种类）。
- 模块级含文案常量改为函数或移入组件：`idRule(t)`、`hashRule(t)`、`amountRule(t)`、`require3dsTag()`、`labeledOptions(values, kind)`、`catalogTermTypeLabel` / `capabilityKindLabel`、`currencyCodes` / `countryCodes` + `countryOptionLabel`；`priceErrorLabels` 改为 `priceErrorCodes` + `priceErrorLabel(code)`；版本推进 / 目录停启用的文案表改为 `versionTransition.<动作>.*`、`catalogStatus.*` key。
- 列表连接符（、/；）改为 `joinList(items, 'list' | 'clauses')`，用 `join.list`「{head}、{tail}」/「{head}, {tail}」与 `join.clauses`「{head}；{tail}」/「{head}; {tail}」，避免拼接片段与首尾空白。
- **价格试算说明**：`commercialPreview.ts` 新增 `illustrationNoteText` / `illustrationNotesText`，按 `simulator.note.<code>` + params 翻译，`count` 转数字走 ICU 复数，多条按「；」连接，未知编码显示「编码 参数」；种子为 F0 报告给出的 9 条原中文。已删除 `CommercialPriceSimulator.tsx` 的过渡函数 `noteCodes`；新增测试覆盖 9 个编码、复数、连接与未知编码。
- `toLocaleString('zh-CN')` 在本范围内没有；`localTime` 为固定格式（未改）。

## 2. 自检

| 项 | 结果 |
|---|---|
| `npm test` | 1476 个，全部通过（F0 基线 1475，新增 1） |
| `npx tsc --noEmit` | 通过 |
| `npx vite build` | 通过（大 chunk 警告为既有提示） |
| `node scripts/i18n-export-source.mjs` | 成功：21 个命名空间、1519 key，其中 commercial 785 |
| 守卫 | 本范围源码去注释后汉字为 0（严格检查已生效）；全局剩余 240 / 354 个文件、约 9445 处 |
| 一致性测试 | en/zh-CN key 一致、ICU 合法且参数一致、静态引用 key 均存在；另用脚本确认 785 个 key 全部被代码引用 |

## 3. 范围外文件

无。`channel/`、`card/`、`fileRecords/` 等同名的 `componentSummary`、`capabilityStatusLabels`、`versionStatusLabels`、`diffChangeLabels` 是各自模块的独立定义，不引用 commercial，未动。

## 4. 拿不准的英文用词（请主会话定）

1. 开通项 / 可开通项：合同上的「开通项」用 **Enablement**（术语表），目录里的「可开通项」用 **Capability**；「新增开通项」= Add Enablement，目录「+ 新增开通项」= + Add Capability。
2. 驳回（版本动作）：**Return**（术语表「已驳回 = Returned」）；标准价目「复核通过」= **Approve**。
3. 开通项状态 SUSPENDED「暂停」：**Suspended**（术语表「已暂停 = Paused」，这里按代码取 Suspended）。
4. 破标 **Below Standard**、无标准可比 **No Standard to Compare**、标准价已更新 **Standard price updated**。
5. 画像（列名简称）**Profile**，定价画像 **Pricing Profile**；具体度 **Specificity**；兜底画像 **fallback profile**。
6. 口径 **Basis**；含税口径 **Tax Treatment**（含税 / 不含税 = Tax Inclusive / Tax Exclusive）；混合费率 **Blended**。
7. 签署方式：电子签 **E-Signature**、点击接受 **Click Acceptance**、线下签署 **Offline Signature**；历史免签 **Exempt (Legacy)**。
8. 换汇币对 **FX Currency Pair**、周期费 **Periodic Fee**、Partner 底价 **Partner Buy Rate**、Partner 分润 **Partner Share**、外汇点差 **FX Spread**。
9. 保证金 **Reserve** / 滚动保证金 **Rolling Reserve**；商业时点 **Commercial As-Of Time**；版本令牌 **Version Token**。
10. 退款处理取值：全额退 **Refund in Full**、按比例退 **Refund Pro Rata**、不退 **Not Refunded**；阶梯模式：整笔适用 **Whole Base**、分段累进 **Progressive**、回溯 **Retroactive**。
11. 发卡地区取值 GCC「海湾国家」直接用 **GCC**；MEA **Middle East & Africa (excl. GCC)**。

## 5. 与指南 / 卡片不一致之处

1. **维度名的种子与回退顺序**：原代码先用维度清单（commercial `PricingDimension`）的中文名，清单缺失才用本地兜底中文。英文界面需要先取翻译，因此 `dimensionLabel` 改为 `enum.dimension.<代码>` 优先、清单 `displayName` 其次、代码最后（同 F0 菜单名 `tOr` 回退的做法）。为保持中文显示不变，4 个维度的 zh 种子取服务端名称而非原兜底常量：ISSUER_REGION「发卡区域」（原兜底「发卡地区」）、ISSUER_COUNTRY「发卡国家」（原「发卡国」）、DESTINATION_COUNTRY「出款目的国家」（原「目的国」）、CURRENCY_PAIR「币种对」（原「币对」）；其余维度两者相同。代价：费率预览页原来不带维度清单、显示兜底名，现在这 4 个维度显示服务端名称。`decodeDimensions` 缺名称时改为回退代码，不再把本地中文写进解码数据。请确认取舍。
2. 服务端默认消息「业务校验未通过」（Foundation `CommonErrorCode`，用于判断 BFF 是否透传了 Owner 原因）是比对数据，按 formModel 的约定以 `\u` 转义保留并注明原因。
3. 只有一个 key 带首尾空白：`commercial.compare.fieldSeparator` en「: 」（zh「：」），用于「字段：前 → 后」与费率卡「事件：组件」两处 JSX；已确认 Platform 导入不裁剪文本。若以后 Platform 编辑界面会裁剪，需要改成整句 key。
4. 同义复用：时点列「已排期」标签复用 `enum.versionStatus.SCHEDULED`，费率卡「生效合同」复用 `enum.dashboardMetric.ACTIVE_AGREEMENTS`（中文相同、含义相同）。
5. 目录条款类型标签：`catalogTermTypeLabel` 先查 `enum.termType`、再查 `enum.catalogTermType`（出款费 / 外汇点差 / 争议费 / 平台费），保持原来「目录页多 4 种，其他页面只认 4 种合同条款类型」的行为。
