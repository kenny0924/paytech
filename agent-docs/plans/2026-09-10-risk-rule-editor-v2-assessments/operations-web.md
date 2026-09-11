# RCO-14 Operations Web 影响评估

- 日期：2026-09-10。
- 状态：组件评估完成；实现方案与扩展合同待确认。
- 范围：[规则编辑 V2 需求](../2026-09-10-risk-rule-editor-v2-requirements-assessment.md) 的四个画板，不含决策流页面。
- 本报告按当前工作区源码判断改动影响；不证明部署状态，也不修改既有接口合同。

## 1. 结论与工作量

纯布局和现有条件/单终态动作的交互重构可复用保存接口，前端工作量为中；完整拒绝配置、多个附加动作和非终态规则属于合同扩展，前端工作量为中到大，取决于 Risk 冻结的目录/类型/兼容方案。后者不能只在页面加几个控件：编辑模型、保存转换、严格响应读取、历史版本详情和字段错误都要一起升级。

| 需求 | Web 改动 | 依赖 |
|---|---|---|
| V2-01 | 拆成规则导航、选中规则编辑区与固定保存栏；新增当前选中 ID、展开状态和摘要展示 | 无新后端能力；切换规则只切视图，不替换整个 editor |
| V2-02 | 复用增删/排序/启用/名称字段；基本信息移入集中编辑入口；拖动保留键盘上移/下移操作 | 当前完整新版本保存和最新 Base 检查 |
| V2-03 | 条件行更紧凑，国家/币种可用展示字典和选择控件；单条件与组的转换补齐 | 当前九字段、十一操作符；不把展示字典当作业务支持国家/币种的授权目录 |
| V2-04 | Radio 映射 PASS→ALLOW_CHECKOUT、REVIEW→REVIEW_CHECKOUT、REJECT→DENY_CHECKOUT；默认决定从已验证目录读取 | 不改变 Risk 的 ALLOW/REVIEW/DENY 与 FIRST_TERMINAL |
| V2-05 | 拒绝目录选择、默认文案、覆盖/恢复、长度校验、只读历史展示与字段错误定位 | 原因目录/消息冻结合同；旧 reasonCode 不一定属于新目录，不能丢弃或改写 |
| V2-06 | typed 动作列表和参数控件、对象字段选择、增删/排序、摘要和详细回显 | Risk 提供已注册动作及参数/字段绑定合同；当前 actionParameterFlag 全为 false |
| V2-07 | 明确无终态动作状态，与附加动作组合验证；不把空值强转成 REVIEW | Risk 定义非终态与至少一个动作等约束，BFF 同步合同 |
| V2-08 | 复用条件表达视图生成折叠只读描述 | 如要手工编辑描述，再由 Risk 定义独立持久字段 |

## 2. 源码证据与预计文件范围

- [RuleSetEditor.tsx](../../../operations-console-web/src/risk/RuleSetEditor.tsx)：现为全部规则卡片纵向排列，已经含增删、拖动、上移/下移、编码/名称、启用、原因编码与一次完整保存；V2 主要重构其渲染组织。
- [ruleBuilder.ts](../../../operations-console-web/src/risk/ruleBuilder.ts)：EditorRule 只有一个 actionCode/actionVersionNo 和可选 reasonCode；排序转 priorityNo；详情重建保留条件方向；编辑模型和 Wire 转换是多动作扩展的主要改动点。
- [ConditionBuilder.tsx](../../../operations-console-web/src/risk/ConditionBuilder.tsx) 与 [RuleSetDetails.tsx](../../../operations-console-web/src/risk/RuleSetDetails.tsx)：已有 NOT、字段左右侧、递归条件组和可读条件预览，应保留语义。
- [riskApiModels.ts](../../../operations-console-web/src/risk/riskApiModels.ts)：RuleDefinition 为单 action，Authoring actionParameters 为 never[]，没有拒绝消息；需随 Risk/BFF 合同演进。
- [riskResponseValidation.ts](../../../operations-console-web/src/risk/riskResponseValidation.ts)：精确属性集合读取；拒绝非空 actionParameters 等扩展集合；在旧路径多回字段会使旧浏览器失败关闭，不是自动向后兼容。
- [riskAuthoringCatalog.ts](../../../operations-console-web/src/risk/riskAuthoringCatalog.ts)：唯一 CHECKOUT_PREPARE_AUTHORING_V1、九字段、三项 V1 终态动作和空扩展集合；新目录必须有显式版本分支。
- [RiskRuleConfiguration.tsx](../../../operations-console-web/src/pages/RiskRuleConfiguration.tsx)、[ruleSetHead.ts](../../../operations-console-web/src/risk/ruleSetHead.ts)、[riskPageModel.ts](../../../operations-console-web/src/risk/riskPageModel.ts)：保存、最新版本、权限、失败状态和字段错误；V2 导航与基本信息入口不能绕过这些检查。
- [riskCommands.ts](../../../operations-console-web/src/risk/riskCommands.ts) 与 [useUnsavedRuleGuard.ts](../../../operations-console-web/src/risk/useUnsavedRuleGuard.ts)：实际 UTF-8 Body 限额、未知结果同键恢复及离开保护继续复用。
- [riskRuleConfiguration.css](../../../operations-console-web/src/risk/riskRuleConfiguration.css)：分栏、折叠轨道、选中态、底部保存栏、长表单滚动和窄屏适配的主要样式范围。

无需为纯 UI 改动增加业务数据库、浏览器持久 Draft 或新的 BFF 聚合端点。页面“保存新版本”不能变成规则级 PATCH 或启用开关即时生效。

## 3. 条件与保存的兼容风险

1. 图中单个条件仍有全部/任一切换。提交一个 Predicate 即可，不生成只有一个 Child 的 AND/OR；多条件时才生成 Group，删除至一个条件也要收拢。
2. V1 历史支持 NOT 和字段在右。V2 不能无条件隐藏或翻转；若选择等价改写，必须有每种操作符的等价证明，尤其 IN/NOT_IN/BETWEEN，不能套普通大小比较的翻转规则。
3. 金额以十进制字符串提交。图上的千分位只属于显示，不能发送 `1,000` 或先经 JavaScript Number 丢精度；历史 16 位整数/8 位小数仍须往返一致。
4. 保存前必须输入新 Change Reason；现行页面开始后续编辑时会主动清空旧理由。基本信息移入弹窗后不能遗漏这个必填入口。
5. 规则集编码首次创建后只读；Rule Code 仍需创建/验证入口。规则选择用稳定内存 ID，排序后不能把其他规则的输入/错误挂到当前规则。
6. REJECT 以外的 V1 规则也可能有 reasonCode。新的 REJECT 专区不能静默删除其旧值；迁移编辑策略需要 Risk 认可。
7. 任一字段有错误时保存应定位到对应规则和参数控件，即使该规则未选中或列表已收起。422 结构路径需与新数组/参数合同一致。
8. 保存失败、权限撤销、目录不可用、409 及 413 保留原本的安全状态；未知结果继续使用原 Body/Key，不因切换规则或改写序列化顺序制造新命令。

## 4. 推荐边界与依赖顺序（待确认）

1. 可先实现 V2-01/02/03/04/08 的现有能力界面。该阶段沿用 V1，尚未接入的新业务控件不作为可保存能力开放。
2. Risk 冻结原因/动作目录、非终态、对象类型、请求/响应及历史兼容方案；给 Web/BFF 同一套正反例和回显样本。
3. BFF 支持新合同后，Web 显式支持新版本 Decoder、目录匹配和转换；保留旧版本读取路径，未知版本继续失败关闭。
4. 完成新版本 Save→Detail→Freeze→TEST Candidate→八项静态检查与旧数据回归，再讨论对应环境启用。

新接口路径、新权限、新上限和动作实际执行时机均不由 Web 自行决定。冷却/标签的对象信息应来自 Risk 的 typed 可绑定字段，不让运营输入任意敏感标识。

## 5. 验证要求

- 纯函数：V1 回读再提交语义不变；单条件/嵌套 NOT/比较方向、顺序和 Decimal 边界；新旧动作/目录/消息参数的精确键集合与非法组合。
- 交互：列表展开/收起、切换规则保留输入、拖动与键盘排序、删除选中规则、校验错误定位、名称/基本信息和未保存提示。
- 保存：必填理由、64 KiB 实际字节边界、409/413/422/未知结果、权限撤销、只读历史、新旧 Descriptor 切换。
- 安全：不持久化策略内容；日志/URL/遥测不包含原因消息、阈值和参数；不渲染可执行 HTML；Handler 指纹不进入浏览器。
- 代码实现后运行 Web 的 `npm test` 与 `npm run build`；浏览器验证四画板布局、长内容、键盘操作和滚动。当前评估不声明 V2 UI 已实现或完成视觉验收。

## 6. 待确认项

- 原因目录来自哪个 Risk 合同，默认文案与覆盖值怎样冻结，历史未知编码如何展示/继续编辑。
- 无终态动作的 Wire 表达；附加动作允许数量、顺序、重复/冲突、typed 对象字段、单位和上限。
- 新旧 Browser/Owner Wire/Descriptor 的协商或路径隔离方案；不建议仅放松旧 Decoder 为任意对象。
- “规则描述”按本次最小范围仅生成只读文字；用户如需要手工维护，再补充需求。

本任务前一轮已运行现有 ruleBuilder、riskAuthoringCatalog、riskCommands、riskResponseValidation 四个测试文件，命令退出码为 0；这些只证明既有转换和校验基线，不证明本次新增能力。
