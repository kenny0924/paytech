# W2 任务卡：运营 Web「明细账运维」页面

## 工作位置与边界

- 只在工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-plops`（基于 main `20ffcd9f`）中修改 `operations-console-web/`。**不要**修改主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR`，也不要改 `paytech/**`（归 W1）。
- 不碰 mor-test，不提交、不推送。不启动会占用他人端口的长期服务；如需本地预览，用临时端口并在结束时关闭。

## 必读

1. 需求：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/operations-明细账运维/requirement.md`
2. 技术方案：同目录 `technique.md` 第 7.3 节（BFF 接口与响应字段，**前端按此合同编码**）、第 9 节（页面）
3. 原型：同目录 `prototypes/P1—P11*.png`（Pencil 源文件 `operations-console-web/docs/operations-console.pen` 中"财务 · 22—32"，只读，不要改 .pen）
4. `agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 10 节（前端文案与语言）、`agent-docs/UNIT_TEST_GUIDE.md` 中前端相关部分
5. 现有写法：`src/pages/FinanceSubledger.tsx`、`src/finance/SubledgerPostingDrawer.tsx`、`src/finance/finance.ts`（解码器、`Money`、`localTime`）、`src/pages/ReconExceptions.tsx` + `src/reconciliation/*`（列表 / 详情子路由、URL 状态）、`src/navigation/pageCapabilities.ts`、`ConsolePage.tsx`、`menu.ts`（详情子路由）、`src/i18n/**`（命名空间、en 内置包、`operations-console-web/i18n/zh-CN/`）、`src/i18nSourceGuard.test.ts`（禁止硬编码中文）。

## 任务

- 页面 `OPERATIONS_FINANCE_POSTING_OPS`，路由 `/finance/posting-ops`，子路由 `/finance/posting-ops/streams/:streamId`；在 `pageCapabilities.ts` 登记（动作 `OPERATIONS_FINANCE_POSTING_OPS_CATCH_UP`、`OPERATIONS_FINANCE_POSTING_OPS_REBUILD`，与 W1 的 `console-resource-capabilities.json` 一致）、`ConsolePage.tsx` 懒加载、`menu.ts` 子路由。
- API 模块：路径常量、类型、**严格解码器**（不合法响应抛 `ApiError(502,'INVALID_RESPONSE')`，参照 `finance.ts`）。接口：
  - B1 `GET /finance/posting-progress/v1/ledger-books/{ledgerBookId}`
  - B2 `GET /finance/posting-streams/v1/{streamId}/postings?fromSequence=&limit=`
  - B3 `POST /finance/posting-streams/v1/{streamId}/catch-up`（无请求体）
  - B4 `POST /finance/posting-rebuilds/v1`（`{entityType, entityId, currency}`）
  - 已有：批次详情 `GET /finance/account-postings/v1/{postingId}`（复用 `SubledgerPostingDrawer`）、账簿列表（复用财务现有的账簿列表接口与解码）。
- 页面状态与交互按原型 P1—P11：
  - 账簿选择写入 URL（`?ledgerBookId=`），返回时恢复；未选时显示 P7 空状态。
  - 三段卡片：`CONSISTENT` / `BEHIND` / `UNAVAILABLE` 三种样式；`UNAVAILABLE` 段内"重试"重新请求整页数据。ledger 503 时整页错误（P8 下半）。
  - 过账流表；`BEHIND` 的行显示"追平"（有 CATCH_UP 动作权限时）或"需执行权限"（无权限时，P10）；"卡住的批次"卡片（P2），`stuckPosting` 为 `null` 时隐藏卡片、只显示落后数。
  - 追平：点击 → 确认（简短）→ 调用 B3 → 结果条就地显示（P3 四种：已追平 / 部分追平"继续追平 · 剩余 N"/ 409 稍后重试 / 400 规则拒绝显示 `code` + `detail` 与"前往记账规则"链接、"重新追平"）→ 成功或部分成功后自动刷新 B1。剩余 N = account 最大序号 − 新 `lastSequence`（用刷新后的 B1 数据计算）。
  - 重建：有 REBUILD 动作权限时显示按钮；确认弹窗（P4）→ B4 → 页顶结果条（P5 四种：已纠正 N / 无需纠正（`changedAccountCount` = 0）/ `STREAM_NOT_FOUND` / 409）→ 刷新 B1。
  - 批次浏览子页（P6）：按序号升序，"加载后 N 批"追加（`nextFromSequence`），每页 50；"卡住"标签标在 `ledger 已消费 + 1` 那一批；点"查看流水"打开 `SubledgerPostingDrawer`；返回链接回到进度页并保留账簿。
  - 执行中按钮禁用防重复点击；不要对每一行并发发请求（运营路由每人并发上限 2）。
  - 窄屏 390（P11）：三段纵向、表格改卡片、无横向滚动。
- 文案：新增到 `finance` 命名空间（或新增命名空间时同步 `OPS_NAMESPACES`、内置 en 包与 `i18n/zh-CN`），菜单名 `nav.resource.OPERATIONS_FINANCE_POSTING_OPS`（en "Posting operations"，zh-CN "明细账运维"）；源码不得出现中文字面量。
- 单测：解码器（合法 / 非法）、剩余数计算、结果条状态映射、权限控制按钮显隐、URL 状态；`i18nCatalog.test.ts` 与 `i18nSourceGuard.test.ts` 通过。

## 构建与测试

- `cd operations-console-web && npm ci`（如 node_modules 不存在）`&& npx tsc --noEmit && npm test && npm run build`；按项目已有脚本为准。

## 交付

在工作树根目录写 `REPORT-W2.md`：改动文件清单、AC-V1—AC-V4 与各原型状态的实现对照、命令与结果、与合同不一致或待 W1 确认之处、未完成项（例如真实浏览器走查留给阶段 5）。
