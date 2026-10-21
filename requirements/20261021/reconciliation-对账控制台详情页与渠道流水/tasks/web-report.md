# Web 线交付报告（阶段 4）

> 范围：technique §2.3 Web 线。未提交、未推送、未部署、未执行 SQL；未调用 Superpowers skill；只改 `operations-console-web`（未碰 `paytech/**`）；共享文件 `pageCapabilities.ts`、`ConsolePage.tsx`、`menu.ts`、`configurationModel.test.ts` 只做局部增改，商务会话的 hunk（标准价目页、TERM_SIGN、CATALOG_MANAGE、CommercialTemplates 懒加载）原样保留；未使用共享 git index。2026-10-21 中途按主会话要求暂停一次，"继续"后完成全部剩余项。

## 1. 已读依据

本需求 requirement.md（§3、AC-01–AC-05、AC-V1–V3）、technique.md（§2.3）、delivery.md；后端线 `tasks/backend-report.md`（字段名、404 范围、渠道流水每页 ≤ 100）；前序 `20261020/reconciliation-渠道资金对账切片1/tasks/D-report.md`；现有 `src/pages/Recon*.tsx`、`src/reconciliation/*`；详情页先例 `menu.ts` `pageRoutePaths`（渠道、MID）、`pages/ChannelMids.tsx`、`commercial/AgreementDetail.tsx`；面包屑先例 `pages/FinanceSubledger.tsx`、`finance/LedgerDrilldown.tsx`；`finance/finance.css`。reconciliation 源码只读核对：`ChannelStatementLineApi`、`BankStatementApi`、各 `*QueryServiceImpl` 的「不存在」业务拒绝文案。

## 2. 路由（`menu.ts` `pageRoutePaths`，同一菜单资源下的子路由，不新增菜单）

| componentKey | 路由 |
|---|---|
| OPERATIONS_FINANCE_RECON_CLEARING | `/finance/reconciliation/clearing-accounts`、`/:clearingAccountId` |
| OPERATIONS_FINANCE_RECON_CASH | `/finance/reconciliation/cash-accounts`、`/:cashAccountId` |
| OPERATIONS_FINANCE_RECON_STATEMENTS | `/finance/reconciliation/statements`、`/:statementId`（`?line=<行号>` 翻页并高亮滚动到该行） |
| OPERATIONS_FINANCE_RECON_BANK | `/finance/reconciliation/bank`、`/statements/:bankStatementId`、`/lines/:bankStatementLineId` |
| OPERATIONS_FINANCE_RECON_EXCEPTIONS | `/finance/reconciliation/exceptions`、`/:exceptionId` |
| OPERATIONS_FINANCE_RECON_CHANNEL_LINES（新页） | `/finance/reconciliation/channel-lines` |

旧的查询串入口（`?account=`、`?statement=`、`?line=`、`?exception=`）重定向到对应详情页，已分享的旧链接仍能打开。

## 3. 从抽屉改为详情页的内容

| 原抽屉 | 详情页组件（`src/reconciliation/detail/`） | 内容 |
|---|---|---|
| ClearingDrawer | `ClearingAccountDetail.tsx` | 基本信息（到账银行账户链到银行账户详情）、相关数据入口（渠道账单 / 渠道流水 / 平台流水 / 未结异常，均带筛选）、配置表单、MID 挂 / 摘、启停（标题区按钮） |
| CashDrawer | `CashAccountDetail.tsx` | 信息、复核（通过 / 拒绝，提交人本人不显示）、申请关闭、相关数据入口（银行流水 / 对账单 / 预期流水 / 未结异常） |
| StatementDrawer | `ChannelStatementDetail.tsx` | 账单头（清算账户链接）、对平门禁、**全部账单行**（每页 20，按匹配状态 / 行类型筛选，用既有 `GET /channel-statements/{id}/lines`）、平台流水链到平台流水页、开放异常链到异常列表、作废（标题区按钮） |
| 无（原来只有列表） | `BankStatementDetail.tsx` | `GET /bank-statements/{id}` 对账单头、期初期末、全部流水（既有 lines 接口，按行号分页；该接口没有筛选参数，所以不提供筛选），点流水进流水详情 |
| LineDrawer | `BankLineDetail.tsx` | `GET /bank-statement-lines/{id}`：流水信息（所属对账单、银行账户链接）、匹配建议与已确认 / 已拒绝匹配（确认 / 拒绝；预期流水为渠道结算时链到渠道账单）、人工多选匹配（实时带符号合计）、流水上的未结异常（链到异常详情）、人工开异常（开立后跳异常详情） |
| ExceptionDrawer | `ExceptionDetail.tsx` | 异常信息（账户、渠道账单链接）、来源对象摘要并链到来源详情（AC-04）、处置记录；**提交处置**、**复核处置**改为从详情页打开的抽屉，完成后详情页重新读取 |

保留的抽屉：新建清算账户、新建银行账户、ImportDrawer（渠道账单 / 银行对账单上传，在列表页）、提交处置、复核处置。列表行点击一律进入详情路由（AC-V2）。

公共部件：
- `ReconDetail.tsx`：`ReconDetailPage` 骨架，包括面包屑「财务 / 对账 / 列表 / 当前对象」、标题区操作、「返回列表」、「未找到」结果页、加载失败重试。还有 `useOpenDetail` / `useBackTarget`：列表进详情时带上当前列表地址，返回时恢复筛选与页码（AC-01、AC-V1）。
- `useReconDetail.ts`：以下两种情况都显示「未找到」，不弹错误通知：
  - 404；
  - 既有接口 400 `BUSINESS_CHECK_FAILED` 且 detail 以「不存在: <当前主键>」结尾。
  主键非法时不发请求，直接显示未找到。
- `useReconListQuery.ts`：列表筛选和页码以 replace 方式写入 URL 查询串，挂载时从 URL 恢复；支持缺省筛选（例如银行流水默认只看未匹配，URL 中的空值表示用户清空了这个缺省）、固定附加筛选（银行账户）和受限页大小。
- `ClearingSettings.tsx`：配置字段，新建抽屉和详情页共用。
- `reconRoutes.ts`：路由、URL 读写、渠道流水查询校验、链接构造、未找到判定、返回目标。

## 4. 渠道流水页 `pages/ReconChannelLines.tsx`

- **筛选：** 清算账户（下拉）、账单状态（不选时由服务端排除作废、拒绝、接收中）、匹配状态（UNMATCHED / MATCHED / EXCEPTION）、行类型、交易号类型、外部交易号（精确，两者都可单独使用）、交易日起止（格式与先后校验）。
- **列：** 交易日、清算账户编码、渠道账单（批次号 + 版本，链到 `/statements/:id?line=`，带账单状态与结算日）、行号、类型、方向、金额、行费用、外部交易号、平台流水（链到平台流水页）、实际费用、匹配状态。点行进入所属账单详情并高亮该行。
- **其他：** 分页 10 / 20 / 50 / 100（接口上限），筛选与页码存 URL，空状态有说明（AC-V3）。接口 `GET /finance/reconciliation/v1/channel-statement-lines`。
- **注册：** `pageCapabilities.ts` 放在 PLATFORM_TXNS 之后，内容为 `{ componentKey: OPERATIONS_FINANCE_RECON_CHANNEL_LINES, displayName: 渠道流水, frontendRoute: /finance/reconciliation/channel-lines, actions: [] }`；`ConsolePage.tsx` 懒加载；`configurationModel.test.ts` 页数 56 → 57。

## 5. 其他列表页

- 清算账户、银行账户、渠道账单、平台流水、对账异常、银行对账三个页签的筛选与页码都改为存 URL。
- 银行对账：切换银行账户或页签时清掉页签内筛选。
- 平台流水：清算账户链到清算账户详情；已匹配的流水加「渠道流水」链接（按清算账户 + 交易号）；开异常后跳异常详情。
- 对账异常：账单过滤横幅可点进账单详情。
- 预期银行流水：来源为渠道结算时链到渠道账单。

## 6. 测试与验证

| 命令 | 结果 |
|---|---|
| `npx tsc --noEmit -p operations-console-web` | 通过 |
| `npm --prefix operations-console-web run build` | 通过（`ReconChannelLines` 独立 chunk 5.9 kB；只有既有的大 chunk 警告） |
| `npm --prefix operations-console-web test` | 1236 例，1235 通过，**1 例失败**，原因见下 |

**失败的一例：** `financeWorkflow.test.ts`「能力清单：Web 财务页面与动作与 Platform console-resource-capabilities.json 逐项一致」。原因是 Platform 的 `console-resource-capabilities.json` 还没有 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES`，由后端线负责（后端报告 §4.3 未开始）。后端在 JSON 中把该组件加到 `OPERATIONS_FINANCE_RECON_PLATFORM_TXNS` 之后、值与 §4 相同后，这一例应通过，需要复跑确认。

新增 `src/reconciliation/reconRoutes.test.ts`（10 例）覆盖：
- 6 个对账 componentKey 的 `pageRoutePaths` 子路由，以及路由构造（含 `?line=`）；
- 渠道流水页登记的位置与内容；新接口路径；
- URL 筛选读写往返：保留其他参数、缺省与「清空缺省」可区分、非法页码与页大小回默认、渠道流水页大小不接受 200；
- `compactFilters`；
- 渠道流水查询：去空白、未知键丢弃、日期格式与先后、交易号单独查询；
- 高亮行号解析与所在页计算；
- 链接：平台流水 ↔ 渠道流水、预期流水 → 账单、`withQuery`；六类异常来源链接；
- 返回目标；
- 未找到判定（404、既有 400「不存在: 主键」、主键不一致、其他业务拒绝、其他错误码）与主键校验；
- 渠道流水行和银行流水详情的解码。

修改 `reconModel.test.ts` 的对账页面清单断言（七页 → 八页，含渠道流水）。

浏览器走查（AC-01–AC-05、AC-V1–V3）需要后端 BFF 转发和 Platform 块 125 就绪后在阶段 5 进行，本线没有启动服务。

## 7. 偏差与注意

1. **「未找到」判定：** 既有按主键读取的接口（清算账户、银行账户、渠道账单、对账异常）不存在时仍返回 400 `BUSINESS_CHECK_FAILED`。前端按 detail 以「不存在: <当前主键>」结尾来识别，依据是 reconciliation 现有的「…不存在: id」文案。如果这些文案改了，需同步修改 `isNotFoundFailure`。新接口按 404 识别。后端报告提到 BFF 需在 `OperationsErrorStatusResolver` 中把两个 NOT_FOUND 映射为 404；如果 BFF 没映射、浏览器收到的是 400 `BANK_STATEMENT(_LINE)_NOT_FOUND`，当前判定不会识别为未找到，需要后端完成映射，或前端再加这两个错误码。
2. **银行对账单详情的流水不提供筛选：** 既有 `GET /bank-statements/{id}/lines` 只有分页参数，BFF 会拒绝未登记的查询参数。
3. **渠道账单行的高亮定位**假定账单行号从 1 连续、按行号升序分页（CSV 解析与 Mapper 都保证这一点）。用户在详情页加了筛选后，高亮行可能不在当前页，此时显示提示。
4. **清算账户详情的 MID** 不链到渠道 MID 页（对账角色可能没有渠道权限），只显示编码与主键。
5. **银行账户的复核**与原抽屉一样，在页面上二次确认后执行，没有另开抽屉（需求只要求异常的处置与复核用抽屉）。
6. **Pencil 设计稿：** 本线不能编辑 `.pen`，没有补画板；布局沿用渠道、MID、合同详情页与财务明细账的面包屑。
