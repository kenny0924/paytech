# M3 报告：workflow 命名空间迁移

执行日期 2026-10-23 · 研发目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-m3`（在 F0 成果之上），未提交、未推送，主工作区只写本报告。未调用 Superpowers skill，未派生子代理。

## 1. 迁移文件

全部在 `operations-console-web/src/` 下，均为本卡范围：

- `workflow/`：`workflowLabels.ts`、`catalogModel.ts`、`templateModel.ts`、`formModel.ts`、`graphModel.ts`、`definitionModel.ts`、`runtimeModel.ts`、`taskModel.ts`、`fileModel.ts`、`prefetchModel.ts`、`useWorkflowCommand.ts`、`WorkflowCanvas.tsx`、`WorkflowFiles.tsx`、`WorkflowParts.tsx`、`PublishDialog.tsx`、`LaunchModal.tsx`、`FieldDesigner.tsx`、`ActionTemplateEditor.tsx`、`TemplateEditors.tsx`、`EditorPanels.tsx`、`DefinitionEditor.tsx`、`InstanceDetailView.tsx`、`TaskPanels.tsx`
- `pages/`：`WorkflowActionTemplates.tsx`、`WorkflowDefinitions.tsx`、`WorkflowInstances.tsx`、`WorkflowMyTasks.tsx`、`WorkflowStarted.tsx`
- 测试（改为英文断言，`testT('workflow')`）：`catalogModel.test.ts`、`definitionModel.test.ts`、`fileModel.test.ts`、`graphModel.test.ts`、`runtimeModel.test.ts`、`taskModel.test.ts`
- 目录：`src/i18n/en/workflow.json`、`i18n/zh-CN/workflow.json`；`src/i18nSourceGuard.test.ts` 的 `MIGRATED_DIRS` 加入 `workflow/` 与 5 个 `pages/Workflow*.tsx`

`workflowApi.ts`、`workflowTypes.ts`、`workflowIcons.tsx`、`workflow.css` 只有注释含中文，未改。

## 2. 做法要点

- **枚举标签**：`workflowLabels.ts` 的 `xxxLabels` 映射表改为调用时取文案的函数（`nodeTypeLabel(v)`、`actionRunStatusLabel(v)`…，内部 `enumLabel(translator('workflow'), 'enum.<类型>', v)`），未知编码原样显示；颜色表保持常量。活动记录里"按审批 / 节点 / 动作 / 实例依次解释状态"的链式查找收成 `anyStatusLabel`。新增 `operators` 数组替代原来 `Object.keys(operatorLabels)`。
- **模型函数**（校验、问题清单、变更摘要、时长、相对时间等）用模块级 `translator('workflow')` 在调用时取值；组件用 `useT('workflow')`；页面 h1 用 `nav.resource.OPERATIONS_WORKFLOW_*`。
- **拼接句**全部合成 ICU 整句；英文数量句用 plural。`；` `、` 分隔符建为 `workflow.list.semicolon` / `workflow.list.comma`（zh 原符号，en `; ` / `, `）。
- **新建时写入数据的默认文案**（新节点名"开始 / 结束 / 新任务 / 条件判断…"、枚举默认项"选项 1"、复制字段"（副本）"）按创建时语言取值，存进流程数据后不再翻译。
- `formModel.ts` 的拼音转换无中文字面量（用 `\p{Script=Han}`），未动。
- **不翻译**：模板名、流程名、节点名、字段名、服务端 Problem detail / 422 字段信息、编码、`pageCapabilities` 的 `displayName`（页面任务的目标页面下拉改为 `tOr(nav.resource.<componentKey>, displayName)`）。
- 测试：英文断言或 `t(key)`；业务数据中文保留。`SCAN_FAILED_TEXT` 原断言"前端提示包含于后端中文 detail"，改为读取 zh-CN 种子断言同一关系，并另断言英文基准，强度不降。

## 3. 自检

| 项 | 结果 |
|---|---|
| `npm test` | 1475 个，全部通过（含 i18nCatalog 一致性、守卫严格检查） |
| `npx tsc --noEmit` | 通过 |
| `npx vite build` | 通过（大 chunk 警告为既有提示） |
| `node scripts/i18n-export-source.mjs` | 成功；**workflow 988 key**（全部 21 个命名空间合计 1722） |
| 守卫 | 本卡范围源码汉字数 0（已加入 `MIGRATED_DIRS`，严格执行）；全局剩余 245 / 354 个文件、约 9266 处 |

## 4. 范围外改动

无。

## 5. 拿不准的英文用词（请主会话定）

1. **驳回 → Reject / 已驳回 → Rejected**：术语表把"已驳回"译为 Returned、"已拒绝"为 Rejected；但工作流里"退回修改（Return for Changes）"与"驳回"是两种不同决定，若驳回也译 Returned 会撞车，所以驳回用了 Reject / Rejected，已办结果"已退回"用 Returned。
2. **执行人 → Assignee**：术语表只有"处理人 → Assignee"，执行人（动作的执行者）也用了 Assignee；审批人 Approver。
3. **条件比较符用小写短语**：等于 equals、不等于 does not equal、属于 is one of、包含 contains、大于 is greater than、大于等于 is at least、小于 is less than、小于等于 is at most、为空 is empty、不为空 is not empty（便于在条件文字里成句"Amount is at least 100"；下拉里也是小写）。
4. 节点类型：排他网关 Exclusive Gateway、并行分叉 Parallel Split、并行汇合 Parallel Join、任务节点 Task Node；新节点默认名"条件判断"→ Condition。
5. 我的待办页签：待办 To Do、已办 Handled；任务类型"填写"→ Fill In；批量审批 Bulk Approval；会签 → "All must approve"。
6. 通知触发"进入待审批"→ Approval Opened；"转交"触发 → Reassigned；接收人"本节点执行人 / 审批人"→ Node Assignees / Node Approvers。
7. 回调状态：重试中 Retrying、已送达 Delivered。
8. "人员"本人显示"你"→ You（列表中间也是大写 You，如 "Waiting on You, 陈雪"）。
9. 规则行"当 [触发] 时 [类型]"英文为 "When [trigger] then [type]"（`workflow.rules.then` 不能为空，所以用 then）。

## 6. 与指南不一致 / 需要知会

- 删除了 `workflowLabels.ts` 中无引用的死代码 `actionTypeGuides`（原型说明，全仓库无使用），未为它建 key。`TemplateEditors.tsx` 的导出常量 `actionTypeSummaries` 改为 key `workflow.picker.summary.<TYPE>`（仅本文件使用）。
- `fileModel.ts` 的 `SCAN_FAILED_TEXT` 常量改为函数 `scanFailedText()`，`scanStatusLabels` 改为 `scanStatusLabel()`。
- `prefetchModel.ts` 的 `DetailRequestCancelled` 内部错误消息（不展示，调用方静默忽略）改为英文字面量，未建 key。
- 停用动作确认框"当前被 **N 个流程**引用。"合成一个 ICU 句后不再加粗数字（避免拆片段拼接）。
- 部分自定义 `Modal` / `modal.confirm` 补了 `cancelText`（原来依赖 antd 默认语言），中文显示仍为"取消"。
- 动态 key（`enumLabel` 前缀、`picker.summary.${type}`）一致性测试看不到，已人工核对全部取值都有 key；本命名空间无未使用 key。
