# 任务卡 E 交付报告：operations-console-web「数据查询」页面

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-dataquery/operations-console-web`（未改 `docs/design.pen`，未提交、推送或部署）。
- 已读：任务卡 E、`tasks/C-api-contract.md`、`technique.md` 第 3、4.4、7、8、9 节，以及 Java 合同 DTO（`api/dataquery/response/*`）和参数声明读取器 `ParameterDeclarationReader`（确认 min/max 可以是字符串，maxLength/maxSize 为正整数，default 可以是任意 JSON）。参照了 `CurrencySpecs`、`currencySpecModel`、`ConfigurationStatusModal`、`ConsolePage`、`pageCapabilities`、渠道页（`ChannelChannels`、`useChannelCommand`、`ChannelParts`）以及外部 API 编辑器的校验面板（`EditorParts.ValidationPanel`、`FlowEditor`）。
- 原型：用 Pencil MCP 只读提取“数据查询 · 01—06”六张画板的文字，并截取了缩略图。

## 改动文件

修改：
- `package.json`、`package-lock.json`：新增 CodeMirror 依赖。
- `src/navigation/ConsolePage.tsx`：懒加载注册 `OPERATIONS_DATA_SOURCES`、`OPERATIONS_QUERY_TEMPLATES`。
- `src/navigation/pageCapabilities.ts`：新增两项。数据源对应路由 `/data-query/data-sources`、按钮 `OPERATIONS_DATA_SOURCE_MANAGE`；查询模板对应路由 `/data-query/templates`、按钮 `OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE`。两个按钮的显示名都是“维护”。
- `src/navigation/menu.ts`：查询模板登记子路由 `/:templateId`，`new` 也走这条路由。
- `src/navigation/menu.test.ts`、`src/configuration/configurationModel.test.ts`：同步更新注册数（58 → 60）和路由断言。

新增：
- `src/dataQuery/dataQueryCommon.ts`：路径、路由、按钮，以及解码工具、写回执解码和时间格式。
- `src/dataQuery/dataSourceModel.ts`：数据源与连接测试的解码、表单和请求体转换、`SHOW GRANTS` 违规权限拆分、保存门槛、状态动作和删除前置条件。
- `src/dataQuery/templateModel.ts`：模板合同类型与解码；参数声明与表单行的双向转换；参数摘要；约束和列的请求体；草稿和未保存指纹；问题到编辑器标记的换算与定位；补全词；试运行表单的生成和参数转换。
- `src/dataQuery/versionDiff.ts`：正文逐行对比（LCS，先剥离公共前后缀）、并排视图，以及参数、约束、列和数据源的差异。
- `src/dataQuery/useDataQueryCommand.ts`：写命令单飞执行。同一请求重试复用 `Idempotency-Key`；403 刷新授权，409 进入冲突状态，422 显示字段错误，400/404 及三个专有错误码显示中文原因。
- `src/dataQuery/DataQueryParts.tsx`：`useResource`、`useDataSourceOptions`、状态标签、冲突横幅、命令弹窗外壳和提示框。
- `src/dataQuery/DataSourceEditor.tsx`：新建/编辑数据源弹窗，以及连接检查三步组件。
- `src/dataQuery/BodyEditor.tsx`：基于 CodeMirror 6 的正文编辑器。
- `src/dataQuery/DeclarationPanel.tsx`：参数、约束、列三个标签页。
- `src/dataQuery/TrialPanel.tsx`：试运行面板。
- `src/dataQuery/VersionDrawer.tsx`：版本历史抽屉，负责对比与恢复。
- `src/dataQuery/TemplateEditor.tsx`：编辑器页面，包含顶栏、问题面板、保存和启停。
- `src/dataQuery/dataQuery.css`。
- `src/pages/DataSources.tsx`、`src/pages/QueryTemplates.tsx`：模板编辑器在后者中通过 `lazy` 二次懒加载。
- 测试：`src/dataQuery/dataSourceModel.test.ts`、`templateModel.test.ts`、`versionDiff.test.ts`。

## 新增依赖（精确版本，均已发布超过两周）

| 包 | 版本 |
|---|---|
| @codemirror/state | 6.7.6 |
| @codemirror/view | 6.43.14 |
| @codemirror/language | 6.13.1 |
| @codemirror/lang-sql | 6.10.0 |
| @codemirror/autocomplete | 6.20.3 |
| @codemirror/lint | 6.9.7 |
| @codemirror/commands | 6.11.1 |

`npm ls` 显示 state 和 view 各只有一份，已去重。CodeMirror 只打进懒加载的 `TemplateEditor-*.js` 包（423 kB，gzip 138 kB），没有进入首屏包；`DataSources` 包约 18 kB，`QueryTemplates` 包约 5 kB。

## 实现要点

- **数据源页（01/02）**
  - 主从布局：左侧表格列出编码/名称、引擎、地址（有默认库时显示“主机:端口 / 库”，否则显示“整个实例”）、状态、最后检查和引用模板数；右侧显示选中项详情。
  - 详情包括连接信息，密码固定显示“已保存（Vault）· 不回显”；还显示最后一次检查、前 10 个引用模板，以及跳转到“查看全部 N 个 →”的模板列表（带 `?dataSourceId=` 筛选）。
  - 详情中的动作：编辑、测试连接、启用/停用、删除。
  - 编辑弹窗：
    - 左侧表单：编码在新建后锁定；引擎用分段控件；默认库可选，并提示“不填则连接整个实例”；密码提示“不修改请留空”；连接参数只能从 5 个键中选择，且不能重复；连接池上限为 1—10。
    - 右侧显示检查三步。`SHOW GRANTS` 原文中，USAGE、SELECT、SHOW VIEW 以外的权限标红，角色授权整行标红。
    - 启用中的数据源，必须用当前表单配置测试通过才能保存（`saveGate`）。表单修改后，上一次检查结果失效，并提示“原配置继续生效”。
  - 删除前做二次确认，并列出引用模板（最多 50 个）；未停用或仍有启用中的模板引用时，禁止确认。
- **模板列表（03）**：可按数据源、状态、关键字筛选。参数摘要标签使用 `*` 表示必填、`[]` 表示集合、`⟨片段⟩` 表示片段参数，并在表格底部说明含义。点击行进入编辑器。
- **模板编辑器（04—06）**
  - CodeMirror 6 正文编辑：
    - 使用 MySQL 方言，但关闭了 `#` 行注释，以免 `#{}` 被当成注释。
    - `#{}`、`@{}`、`{{#…}}`、`{{!-- --}}` 四类标记分别着色。
    - 服务端问题通过 `@codemirror/lint` 显示为下划线、行号旁标记和悬浮提示。标记范围按“标签到 `}}`、值或片段到 `}`、否则到标识符词尾”计算，越界时收敛到行内。
    - 输入 `#{`、`@{`、`{{` 后分别补全参数名或系统参数、片段名、标签。
  - 实时校验：输入停顿 500 ms 后调用接口 16，有新输入时取消旧请求。问题面板显示错误、警告和固定的“展开校验”说明，点击“定位”可跳到正文行列，或切换到参数、约束、列标签页并高亮对应行或项；`dataSourceId` 问题会高亮顶栏的数据源选择。保存失败返回的 422（含 `body` 的“第 N 行第 M 列：”前缀）也会解析后进入问题面板。前端不解析模板。
  - 右侧三个标签页：
    - 参数：可增删，用 ⋮⋮ 拖动排序，可选择类型；展开后可编辑 LIST 的元素类型和最多个数、OBJECT 字段、FRAGMENT 的“取值 → 固定片段”，以及各类型适用的约束和默认值。类型切换后，请求体只输出新类型适用的属性。
    - 约束：只输出当前分页方式适用的字段。
    - 列：标题和屏蔽规则；KEEP_EDGES 可填写首尾保留位数。
  - 试运行：
    - 按参数声明生成输入：集合用标签输入，OBJECT 集合用 JSON，片段和枚举用下拉，日期和时间用公共 DatePicker；数字按字符串传，以免丢失精度。
    - 偏移分页可选页码、页大小和是否返回总数；游标分页用 `nextCursor` 取下一页。
    - 结果显示摘要、实际 SQL（含 countSql）、绑定值和结果表。`params.xxx` 字段错误显示在对应输入下方。
  - 顶栏：可选数据源，显示状态、当前版本和“未保存”标记；“保存为 vN+1”会弹出变更说明，并预先列出当前警告。保存成功后以通知显示服务端返回的警告；新建成功后跳转到 `/:templateId`。
  - 启用/停用前做二次确认；有未保存修改时会额外提示。离开页面时，如有未保存修改会拦截确认，复用 `useUnsavedRuleGuard`。
  - 版本历史抽屉：最多勾选两个版本，左右对比正文，并列出参数、约束、列、数据源的变化；“以 vX 恢复为 vN+1”在有未保存修改时先确认，再填写变更说明。
  - 409 冲突时显示横幅并提供刷新按钮。

## 测试与构建（在 `operations-console-web` 下执行）

| 命令 | 结果 |
|---|---|
| `npm test`（`node --experimental-strip-types --test 'src/**/*.test.ts'`） | 1265 项，pass 1265，fail 0。基线为 1242 项，新增 23 项 |
| `npx tsc --noEmit`（用一个故意写错的类型确认能报错） | 无错误 |
| `npm run build`（`tsc --noEmit && vite build`） | ✓ built |

项目没有单独的 lint 脚本。

新增测试覆盖：
- 参数声明与表单行往返一致、类型切换后不残留旧约束，以及 FRAGMENT、OBJECT 的默认值。
- 问题到编辑器标记的偏移换算（值、标签、标识符、列越界、行越界、无行号），以及 422 行列前缀解析和定位目标。
- 参数摘要渲染。
- 试运行控件生成、参数转换（未填不传、数字转字符串、JSON 校验）和分页字段。
- 版本对比的行差异（修改、插入、删除、全新增）、并排对齐，以及参数、约束、列、数据源差异。
- 数据源解码、地址与检查展示、GRANT 违规拆分（含列级权限和角色授权）、请求体、保存门槛、状态与删除条件。
- 模板详情、校验结果、试运行结果的解码。
- 菜单子路由与注册数。

需要说明的情况：
- 构建报告 `jsx-runtime-*.js`（511 kB）超过 500 kB。该包不含 CodeMirror，也不含本次新增代码（已 grep 确认），但我没有在本工作区单独构建基线来证明它是原有问题。
- `npm install` 时 `npm audit` 报 vite 8.2.2 和 source-map-js 两项 high，都是已有依赖，与 CodeMirror 无关，本次没有处理。
- 按任务卡要求没有启动 dev server，页面没有经过浏览器实测。

## 与原型的差异及原因

1. **最后一次检查的三步结果**：原型 01 在详情中直接显示三步，但 `DataSourceResponseBody` 只有 `lastCheckResult/Message/Time`。因此详情只显示总结论和原因，点击“测试连接”后才显示三步；编辑弹窗中始终显示三步。
2. **会话只读步骤**：连接测试响应没有单独的会话只读字段。按“连通且账号只读通过”时取总结果推断：总结果通过视为通过，总结果失败视为未通过；前两步失败时显示“未执行”。
3. **“操作人 kenny”和“最近修改”的人名**：接口只返回账号 ID（`updatedBy`、`createdBy`），页面直接显示 ID，没有另外查询账号名。
4. **引擎分段**：只提供 MySQL 和 Doris，没有原型中的“PostgreSQL（预留）”，因为合同只有这两种引擎。
5. **快速修复浮层**：原型 04 中的“声明为 LIST<STRING> / 改为 clientId”没有实现。服务端问题只有文本，前端不解析模板，无法可靠生成修复建议。现在只提供 lint 悬浮提示和定位，另有参数名补全。
6. **SELECT 1 耗时、MySQL 版本、“只读校验通过（单条 SELECT）”等细节**：接口没有返回这些数据，页面不显示。
7. **删除与启停的原因输入**：数据查询合同没有 `reason` 字段，所以确认弹窗只做二次确认，不收集原因。

## 需要主会话裁决的问题

1. **已解决：BUTTON 资源**：D 卡已在块 131 增加 `900000000000131304 OPERATIONS_DATA_SOURCE_MANAGE`（父 131302，权限 131102）和 `900000000000131305 OPERATIONS_DATA_QUERY_TEMPLATE_MANAGE`（父 131303，权限 131103），状态为 DISABLED，与 Web 注册表一致。我已核对 `platform-support-initial-data.sql:5752-5753`。
2. **已解决：`Idempotency-Key`**：D 卡确认块 131 的写路由与块 125 一样接受该 Header。
3. **已裁决：编辑器顶栏的数据源列表**：下拉只列出启用中的数据源。例外是模板当前正在使用的停用数据源：已保存版本或当前草稿选中的那一项会保留，标注“（停用）”，并继续显示“保存与试运行会被拒绝”的提示。实现为 `dataSourceModel.templateSourceOptions`，已补测试。

裁决后复跑：`npm test` 共 1265 项，全部通过；`npx tsc --noEmit` 无错误；`npm run build` 成功。
