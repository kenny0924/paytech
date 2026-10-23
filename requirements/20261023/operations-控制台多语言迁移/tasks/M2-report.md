# M2 报告：风控（risk）文案迁移

执行日期 2026-10-23 · 工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-m2`（含 F0 成果），未提交、未推送，主工作区只写本报告。未调用 Superpowers skill，未派生子代理。

## 1. 迁移文件

**页面（7）**：`pages/RiskDecisionFlows.tsx`、`RiskDictionary.tsx`、`RiskEvaluations.tsx`、`RiskExecutionReleases.tsx`、`RiskLists.tsx`、`RiskRuleConfiguration.tsx`、`RiskVariables.tsx`。h1 与菜单同名的（规则配置、决策流、评估查询、执行发布、风控名单、变量管理、拒绝原因 / 主体标签）用 `nav.resource.<CODE>`。

**组件（16）**：`risk/` 下 `AdditionalActionEditor`、`ConditionBuilder`、`DictionarySelect`、`FeatureVariableFields`、`FlowCanvas`、`FlowConditionEditor`、`FlowPropertyPanel`、`FlowRuleSetPicker`、`ReadRuleSetWorkspace`、`RuleSetDetails`、`RuleSetEditor`、`RuleSetExplorer`、`RuleSetTab`、`TestCandidateDetails`、`TestCandidateTab`、`VariableControls`。

**模型（16）**：`decisionFlow`、`legacyRiskAuthoringCatalog`、`riskAuthoringCatalog`、`riskControl`、`riskDictionary`、`riskEvaluation`、`riskNavigation`、`riskPageModel`、`riskPoints`、`riskVariable`、`ruleBuilder`、`ruleEditorPresentation`、`ruleSetHead`、`testCandidateState`、`variableEditing`；以及测试夹具 `riskFixtures`、`riskEvaluationFixtures`、`variableFixtures`、`riskContractFixtures`（见第 5 节）。

**模型改法要点**（指南 4.3 A/C）：
- 标签映射表改为"代码数组 + 标签函数"：`listPurposeLabel` / `entryStatusLabel` / `releaseStatusLabel` / `checkLabel`（riskControl）、`releaseLabel` + `releaseStatusCodes`（riskDictionary）、`decisionLabel` / `nodeTypeLabel` / `edgeLabel` + `flowDecisions` / `edgeTypes`（decisionFlow）、`variableSourceLabel` / `variableTypeLabel` / `usageLabel` / `operatorLabel` / `subjectLabel` / `velocityMetricLabel` / `velocityWindowLabel`（riskVariable）、`ruleSetStatusLabel` + `ruleSetStatuses`（RuleSetDetails）、`candidateStatusLabel` + `candidateStatuses`（TestCandidateDetails）。原 `statusLabel(labels, status)` 删除。
- 含文案常量改函数：`PAYMENT_OUTPUT_FALLBACK` → `paymentOutputFallback()`，`KNOWN_POINTS` → `knownPoints()`，`ID_FORMAT_MESSAGE` / `PATH_UNAVAILABLE` / `CURRENT_VALUE_NOTE` / `NOT_PROVIDED` → `idFormatMessage()` / `pathUnavailableText()` / `currentValueNote()` / `notProvidedText()`，`queryTypeLabels` → `queryTypes` + `queryTypeLabel()`；`PAYMENT_INPUT_FIELDS` / `CHECKOUT_INPUT_FIELDS` 改为字段键数组，`inputFieldsOf()` 调用时翻译。
- `riskSubjects` / `velocityMetrics` / `velocityWindows` 改为纯代码数组。
- 抛给页面的错误信息（ruleBuilder、riskVariable、variableEditing、riskPageModel 等）在抛出时用 `translator('risk')` 取文案。
- `formatRiskTime` 改用 `formatDateTime`。

**测试（17 个改写）**：`flowOutputs`、`paymentRuleSet`、`riskAuthoringCatalog`、`riskCheckoutRuntimeWiring`、`riskControl`、`riskEvaluation`、`riskEvaluationPage`、`riskPageModel`、`riskPageWiring`、`riskVariable`、`ruleSetHead`、`ruleWorkspaceNavigation`、`testCandidateState`、`useUnsavedRuleGuard`、`variableControls`、`velocityVariable`。断言改英文基准；源码扫描类断言改为断言 key（如 `/rulePage\.retrySame/`）；内存渲染测试的依赖注入补 `useT: testT`。未删除测试、未放宽断言。业务数据中文（服务端返回名称、用户输入）保留。

**范围外文件**：只改了 `src/i18nSourceGuard.test.ts`（卡要求，`MIGRATED_DIRS` 加 `risk/` 与 7 个 `pages/Risk*.tsx`）。

## 2. key 数

`risk` 命名空间 **1132** 个 key（en 与 zh-CN 同一组）。导出脚本合计 21 个命名空间 1866 key。

## 3. 自检

| 项 | 结果 |
|---|---|
| `npm test` | 1475 个，全部通过（与 F0 基线同数） |
| `npx tsc --noEmit` | 通过 |
| `npx vite build` | 通过（大 chunk 警告为既有提示） |
| `node scripts/i18n-export-source.mjs` | 成功，risk 1132 |
| 守卫 | 我范围内 0（已加入 `MIGRATED_DIRS`，严格检查通过）；全局剩余 231 / 354 个文件、约 8979 处（F0 时 273 / 10,523） |

## 4. 拿不准的英文用词（请主会话定）

| 中文 | 我用的英文 | 说明 |
|---|---|---|
| 风控点 / 决策点 | Risk Checkpoint / Decision Point | 按术语表，两词在页面里混用，未统一 |
| 执行发布状态 待激活 / 校验未通过 | Ready to Activate / Checks Failed | 后端值为 SHADOWING / REJECTED |
| 规则集发布状态 影子运行 | Shadow Running | |
| 结束决定 人工复核 vs 评估结论 审核 | Manual Review / Review | 原中文不同，保留两个 key |
| 名单用途 拦截 / 放行 | Block / Allow | |
| 实时累计 / 名单命中 | Real-Time Aggregate / List Match | 变量来源 |
| 字段 / 常量 / 集合变量 | Field / Constant / Set Variable | |
| 去重主体 / 不同主体数 | Distinct Subject / Distinct Subjects | |
| 内容摘要 | Content Digest | |
| 终态 / 终态决定 | Terminal / Terminal Decision | |
| 已退役（规则集）/ 已退休（版本） | 都译为 Retired | 原中文不同，key 分开 |
| 结账准备 / 支付尝试（风控点名） | Checkout Preparation / Payment Attempt | |
| 规则配置（编辑器面包屑） | Rule Configuration | 菜单为 Rules |
| 处理器摘要（静态检查 HANDLER_HASH） | Handler Digest | 候选详情页同代码译 Action Implementation Consistency（原中文不同） |
| 列表分隔符"、" | ", " | `risk.evaluation.valueSeparator` |

## 5. 与指南不一致 / 需要知道的地方

1. **旧版目录常量用 getter**：`CHECKOUT_PREPARE_AUTHORING_V1` 被多处当冻结常量引用（且测试校验 `Object.isFrozen`），改为字段 / 动作 / 运算符对象的 `label`、`helpText` 是读取时翻译的 getter，常量本身不存文案。
2. **测试夹具改为英文业务数据**：`riskFixtures.ts`、`riskEvaluationFixtures.ts`、`variableFixtures.ts` 不是 `*.test.ts`，守卫会扫描，所以夹具中的中文业务名称改成了英文（如"支付金额"→"Payment amount"）；`riskContractFixtures.ts` 的 emoji 用例改为 `'\u{1F600}'`。相关测试期望同步改。
3. **默认节点 / 连线名**：新建决策流的默认节点名（开始 / 结束 / 规则集检查）和连线名（继续）是会保存到 Risk 的数据，现在按当前语言生成；"规则集检查"被选版本替换的判断也按当前语言比较。
4. **全角符号**：组件里作为图标的"＋"用 `'＋'` 保持原样；条件值输入的分隔符"，"用 `'，'`（用户输入的数据分隔）；英文文案用半角"+"。
5. `RuleSetEditor` 侧栏圆点的 `className` 仍用决定标签拼接（`decision-PASS` 等，PASS/REVIEW/REJECT 是代码不受影响；"未配置动作"在英文下变成英文类名，原来也无对应样式）。
6. 同一 key 在多页面复用（如 `candidateList.status`、`ruleSetList.action.*`、`flowPanel.outputValue` 用作"{name}：{value}"），都在 `risk` 命名空间内，没有往 `common` 加。
