# 任务卡 E：operations-console-web「数据查询」页面

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-dataquery`，**只改** `operations-console-web/**`（其中 `docs/operations-console.pen` 不要改）。
- 授权：普通文件编辑、`npm install`（仅为新增 CodeMirror 依赖）、运行前端测试与构建。**不提交、不推送、不部署、不改其他目录。**
- 合同：同目录 `tasks/C-api-contract.md`；技术方案 `technique.md` 第 3 节（语法）、第 7 节（接口）、第 8 节（页面与原型说明）。
- 原型：Pencil 文件 `operations-console-web/docs/operations-console.pen` 中名为“数据查询 · 01—06”的画板（y=83100 起）。.pen 是加密格式，**不要用 Read/Grep 打开**；如果你有 Pencil MCP 工具（`mcp__pencil__*`），可以用 `execute` 的 `Get` 只读查看或 `TakeScreenshot` 截图；没有就以 technique.md 第 8 节的文字说明为准。

## 开工前必读

`AGENTS.md`；`operations-console-web` 的 README/约定文件（若有）；参照现有页面：`src/pages/CurrencySpecs.tsx`、`src/configuration/currencySpecModel.ts`、`ConfigurationStatusModal.tsx`、`src/navigation/ConsolePage.tsx`、`src/navigation/pageCapabilities.ts`，以及一个主从布局或详情抽屉较完整的页面（如渠道、对账相关页面）和流程编辑器 `src/externalApi/FlowEditor.tsx` 的错误面板模式。沿用现有 API 调用层、错误提示、分页组件与测试写法。

## 范围

1. **注册**：`ConsolePage.tsx` 组件键 `OPERATIONS_DATA_SOURCES`、`OPERATIONS_QUERY_TEMPLATES`（懒加载）；`pageCapabilities.ts` 两项（显示名“数据源”“查询模板”，路由 `/data-query/data-sources`、`/data-query/templates`，动作权限 `OPERATIONS_DATA_SOURCE_MANAGE` / `OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE` 等，按现有命名规则从权限码推导）。模板编辑器为子路由（`/data-query/templates/:id`、新建 `/data-query/templates/new`）。
2. **数据源页**（原型 01、02）：主从布局——左表格（编码/名称、引擎、地址/默认库或“整个实例”、状态、最后检查、引用模板数），右详情（连接、密码“已保存（Vault）”、最后检查三步、引用模板、编辑/测试连接/启用停用/删除）。新建/编辑弹窗：左表单（编码新建后锁定、引擎分段、主机端口、默认库可选+提示、账号、密码“不修改请留空”、连接参数只允许 5 个键、连接池上限 1—10），右侧检查结果（连通、账号只读含授权语句与违规权限标红、会话只读）；启用中的数据源修改后必须检查通过才能保存。删除前二次确认并列出引用模板。
3. **模板列表页**（原型 03）：筛选（数据源、状态、关键字），参数摘要标签（`*` 必填、`[]` 集合、`⟨片段⟩`）。
4. **模板编辑器**（原型 04—06）：
   - 正文用 CodeMirror 6（新增依赖固定精确版本，编辑器页面懒加载）：SQL 高亮叠加模板标记高亮（`#{}`、`@{}`、`{{#…}}`、`{{!-- --}}` 分色）；服务端问题（行、列、级别）显示为下划线与行号标记；悬浮提示；参数名/片段名/标签自动补全。
   - 输入防抖 500 ms 调用校验接口；问题面板（错误、警告、说明），点击定位。前端不重复实现解析器。
   - 右侧“参数 / 约束 / 列”标签页：参数表格可增删、拖动排序，类型选择，LIST 的 elementType 与 OBJECT 字段，FRAGMENT 的“取值 → 片段”编辑；约束（最大行数、超时、分页方式、页大小上限、允许总数、游标列）；列声明（标题、屏蔽规则 NONE/HIDE/FULL/KEEP_EDGES 及保留位数）。
   - 试运行面板：按参数声明生成输入控件（集合为标签输入，片段与枚举为下拉，日期时间选择器），分页与是否返回总数；结果摘要、实际 SQL 与绑定值、结果表格。
   - 顶栏：数据源选择、状态、当前版本、未保存提示、保存（填写变更说明，显示警告）、启用/停用、版本历史抽屉（勾选两版逐行对比正文与参数/约束差异，恢复为新版本；有未保存修改时先提示）。
   - 版本冲突 409 时提示刷新。
5. **测试**：按项目现有前端测试方式覆盖：模型转换（参数声明 ↔ 表单）、问题到编辑器标记的映射、参数摘要渲染、试运行表单生成、版本对比的行差异计算。

## 验证

在 `operations-console-web` 下运行项目已有的测试、类型检查/lint 与构建命令（看 package.json scripts），全部通过；在报告中贴出命令与结果。不要启动或部署到任何环境。

## 交付报告（中文，写入 `tasks/E-report.md` 并在最终回复给摘要）

改动文件；新增依赖与版本；与原型的差异及原因；测试/构建结果；需要主会话裁决的问题。**完成后直接结束，不要等待后台事件。**
