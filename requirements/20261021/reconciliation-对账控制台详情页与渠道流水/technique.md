# reconciliation 对账控制台详情页与渠道流水：技术方案

> 状态：阶段 1—3 已确认（2026-10-21），阶段 4—5 进行中。需求见 [requirement.md](./requirement.md)。

## 1. 现状差异

| 项 | 现状（main `2f5f37f7`） | 要改 |
|---|---|---|
| Web 详情 | `ReconClearingAccounts` / `ReconCashAccounts` / `ReconStatements` / `ReconBank`（`LineDrawer`）/ `ReconExceptions` 用 `Drawer` 显示详情 | 拆出详情页组件，经 `menu.ts` `pageRoutePaths` 加子路由（渠道 / MID 先例）；列表行点击改 `navigate` |
| 银行对账单详情 | 无（只在「对账单」标签列表） | 新详情页 + 接口 |
| 渠道流水 | 只有 `GET /channel-statements/{id}/lines` | 新接口 + 新菜单页 |
| reconciliation 接口 | 无 `GET /bank-statements/{id}`、`GET /bank-statement-lines/{id}`、跨账单行查询 | 新增 3 个只读接口 |
| Platform | 块 123：对账 7 页、38 路由 | 块 126：1 个菜单页 + 3 条路由（休眠 + 激活脚本） |

## 2. 设计

### 2.1 reconciliation（只读，不改表）

| 接口 | 参数 / 返回 | 说明 |
|---|---|---|
| `GET /reconciliation/v1/channel-statement-lines` | `clearingAccountId`、`statementStatus`（缺省排除 `VOIDED`、`REJECTED`、`RECEIVING`）、`matchStatus`、`lineType`、`referenceType` + `externalTransactionId`（精确）、`transactionDateFrom/To`、`page`、`size`（≤ 100） | 行字段 + 所属账单（批次号、版本、状态、结算日）+ 清算账户编码 + 匹配平台流水 ID；按 `transaction_date desc, id desc`；用现有索引 `idx_channel_line_ref`、`idx_channel_line_status`，必要时 join `t_channel_statement` |
| `GET /reconciliation/v1/bank-statements/{bankStatementId}` | 对账单头 | 404 `BANK_STATEMENT_NOT_FOUND` |
| `GET /reconciliation/v1/bank-statement-lines/{bankStatementLineId}` | 流水 + 已有匹配（建议 / 确认 / 拒绝）+ 开放异常 | 404 `BANK_STATEMENT_LINE_NOT_FOUND` |

### 2.2 Operations BFF

`/finance/reconciliation/v1/channel-statement-lines`、`/bank-statements/{id}`、`/bank-statement-lines/{id}` 三个转发；渠道流水不做聚合（reconciliation 已返回账单与清算账户编码）。

### 2.3 Web

- `pageRoutePaths` 为 6 个对账 componentKey 加子路由（见需求 §3.1）；`ConsolePage` 按路由参数渲染列表或详情。
- 详情页组件从现有抽屉内容抽出（共享片段放 `src/reconciliation/`），操作按钮与命令沿用；录入抽屉（新建、导入、处置、复核、手工匹配表单）保留，从详情页打开。
- 列表页保留筛选 / 页码在 URL 查询串，返回时恢复。
- 新页 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES`（`/finance/reconciliation/channel-lines`），登记 `pageCapabilities.ts` 与 `console-resource-capabilities.json`；点行跳 `/statements/:statementId?line=<lineNo>` 并高亮。
- 设计稿：沿用渠道 / MID 详情页布局；如需 Pencil 画板，在阶段 4 补到 `operations-console-web/docs/design.pen`。

### 2.4 Platform 块 126

`900000000000126nxx`：Console Resource 1（页，父为块 123 的对账分组）；路由 3（`HUMAN_PERMISSION`，权限 `FINANCE_RECONCILIATION_READ`）及其 header / query param / guard 行；初始 DISABLED，另出 `activation-local.sql`、`test-activate.sql`、`test-deactivate.sql`。前端详情子路由不需要 Platform 注册（同一菜单资源）。

### 2.5 数据库

不涉及。

## 3. 研发与验证

| 线 | 内容 |
|---|---|
| A | reconciliation 3 接口 + 测试（筛选、默认排除、排序、404） |
| B | BFF 转发 + 测试；Platform 块 126 注册与激活脚本 |
| C | Web 6 个详情页 + 渠道流水页 + 路由 + 测试（路由解析、URL 筛选恢复、链接） |

本机：reconciliation-test、BFF 相关测试、Web 全量 + tsc + build、arch-rules；本机 e2e 浏览器走查 AC-01—AC-05、AC-V1—V3。mor-test：替换 reconciliation、ops-bff、ops-web、platform-support（能力清单），块 126 注册与激活；用现有演示数据走查。ops-web / ops-bff / platform 以 mor-test 当时运行的镜像（商务会话）为上一版，发布前与其协调。
