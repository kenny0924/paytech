# I2 线任务书（合同列表 / 工作台实数据 / 价目总览与版本对比 / 产品目录 / 变更历史 / Client 选择器）

> 由主会话（设计与把关）起草，交 Opus 5.5 agent 执行。范围以 `requirement.md` F1–F5、F8 为准；本文件只定 I2 的实现边界与已做的设计决定，不改需求。

## 0. 基线与分支

- 工作分支 `commercial/line-i2`，worktree `/Users/kenny/Desktop/Workspace/Code-MoR-lineI2`，起点 `commercial/line-i1`（95c9834b），先 `git merge commercial/line-h`（285c15d4，两线无文件重叠）。
- 合并 agent 正在建 `commercial/second-slice`（= main `e73764d1` + H + I1）。I2 完成后 rebase 到 `commercial/second-slice`，冲突只会在 Platform 初始 SQL / BFF 测试（9c 的科目分级用块 91）。
- 不碰主工作树 `/Users/kenny/Desktop/Workspace/Code-MoR`；本机 commercial 测试库用 `mor_commercial_lined`（凭据在 `local-environment/.env.local`，不要打印）；ops-bff、ops-web 测试按 I1 的方式跑。
- Platform 登记 **ID 块 93**（`9000000000000 93xxx`，按块 95 的分段约定：931xx permission、932xx role_permission、933xx console_resource、934xx route、936xx header、937xx param、939xx guard；块 95 已 356 行、不再追加）。同样追加 `platform-support-initial-data.sql`、`console-resource-capabilities.json`，以及本需求目录 `db/platform-support-service/registration-dml.sql`、`activation-local.sql`（I1 已建，续写）。权限复用 I1/E 线已有的商务权限码，不新建权限除非确有必要。

## 1. 上游接口（H 线，已在 `paytech-commercial-api`）

| 接口 | 用途 |
|---|---|
| `AgreementQueryApi` `GET /agreements/v1?clientId&localPartnerId&agreementType&status[]&entityNo&agreementNo&page&size` → `PagingResponseBody<AgreementSummary>` | 合同列表、工作台计数 |
| `CommercialTermQueryApi` `GET /commercial-terms/v1/plans?agreementId&clientId&localPartnerId&termType&status&page&size`、`GET /commercial-terms/v1/versions?planId&agreementId&status[]&effectiveFrom&effectiveTo&page&size` | 价目总览、审批队列、工作台计数 |
| `ProductCatalogApi` `GET /products/v1`、`/capabilities?productCode&status`、`/fee-events?productCode&termType` | 产品目录页、表单下拉 |
| `BusinessChangeQueryApi` `GET /business-changes/v1?aggregateRootType&aggregateRootId&page&size` → `BusinessChangeSummary{historyId,recordType,recordId,versionNo,operationType,createBy,createTime,meta{triggerType,triggerId,reasonCode},data}` | 变更历史 |
| 已有写接口 `CommercialTermApi`（版本提交 / 审批 / 驳回 / 激活等）与 `/commercial-term-versions/v1/{id}/prices` | 工作台审批动作、版本对比取价 |

分页公共约定：`page` 默认 1，`size` 只允许 10/20/50/100/200，响应 `{items,page,size,total}`。详见 `paytech/commercial/README.md`「运营控制台只读接口」一节。

## 2. 交付项与设计决定

### F1 合同列表 + Client 选择器
- BFF `GET /commercial-admin/v1/agreements`，参数与 `AgreementQueryApi` 一致，Processor 直接转发（简单转发可省 Processor，但保持与 I1 同一风格）。
- Web 页「合同」改为列表页：筛选栏（合同号前缀、Client ID、Local Partner ID、类型、状态多选、OE entityNo）、表格（合同号、名称、类型、业务模式 / 资金模式、Client / Partner、OE、状态、生效区间、更新时间），行点击进入现有合同详情 / 编辑视图；原「新建合同」入口保留在列表页右上。
- **Client 选择器（本期决定）**：没有运营可用的 Client 搜索接口（见 `I1-technique-additions.md`）。本期做成「ID 输入 + 本机最近使用（localStorage，最多 10 条）+ 从审核任务选（compliance `ClientReviewAdminApi.listCases` `status=APPROVED`，BFF 已接入，只给 clientId / caseNo）」。不新增 client-master 接口；按名称搜索、Party / OE 下拉记为遗留项交 client-master 需求。Party ID、OE entityNo 仍为手输。

### F2 / F8 工作台实数据
- BFF `GET /commercial-admin/v1/dashboard` 把 I1 的占位计数换成真数据：`PENDING_APPROVAL` 版本数、`SCHEDULED` 版本数、`DRAFT` / `PENDING_SIGNATURE` / `ACTIVE` 合同数（各调一次 `size=10` 取 `total`）；并返回审批队列前 10 条（`listVersions status=PENDING_APPROVAL`，按提交时刻升序）。没有全局 BCR 流，「最近变更」卡片本期不做。
- Web 工作台：四个计数卡 + 审批队列表（方案、版本、合同、Maker、提交时刻、价格条数），行内「审批 / 驳回」调用已有写接口；驳回需填原因。

### F3 价目总览 + 版本对比
- BFF `GET /commercial-admin/v1/commercial-term-plans`、`GET /commercial-admin/v1/commercial-term-versions`（转发 H 的两个列表）。
- BFF `GET /commercial-admin/v1/commercial-term-versions/{baseId}/diff/{targetId}`：各取两版价格（已有 `/commercial-term-versions/v1/{id}/prices`），按价格的业务键（画像码 + entityNo + 币种 + 支付方式 + 费用事件；以价格模型实际唯一键为准，核对 `t_commercial_term_price` 的 uk）配对，输出 `added / removed / changed`，`changed` 项列出字段级差异（组件、参数、档位、退款行为、结算参数）。两版必须属于同一方案，否则 400。
- Web 「价目」改为总览页：方案列表（筛选 agreementId / clientId / termType / status），展开显示 ACTIVE / SCHEDULED 版本；版本列表页签；选两版进入「版本对比」页（左右两列 + 差异高亮）。

### F4 产品目录
- BFF `GET /commercial-admin/v1/products`、`/products/capabilities`、`/products/fee-events` 转发。
- Web 产品目录页（I1 已建壳）：三个页签。合同开通项、价格表单里硬编码的产品 / 能力 / 费用事件下拉改从目录接口取；保留 `CommercialCodes` 常量用于类型，不再作为下拉数据源。

### F5 变更历史
- BFF `GET /commercial-admin/v1/business-changes?aggregateRootType&aggregateRootId&page&size` 转发，并在 BFF 为每条快照补 `changedFields`（与同 `recordType + recordId` 的上一版本快照做字段级比较，首版为全部字段）。
- Web 变更历史页（I1 已建壳）：筛选（聚合根类型 AGREEMENT / COMMERCIAL_TERM_PLAN / PAYMENT_METHOD_PROFILE、聚合根 ID 或合同号——合同号经合同列表接口 `agreementNo` 前缀查询换成 ID）、时间线（时间、原因码、聚合版本、操作者、摘要、展开查看快照与差异）。合同详情页、价目页加「变更历史」入口直达。

### 设计稿
主工作树 `operations-console-web/docs/operations-console.pen` 内画板「商务 · 01 工作台」「02 合同列表」「03 合同详情」「04 价目总览」「05 版本对比」「06 产品目录」「07 变更历史」（08/09 为 I1 已做页面）。可用 pencil MCP `get_app_state` / `execute` 的 `Get` 读结构；样式沿用现有商务页 CSS。

## 3. 验证与提交

- 测试：BFF auto（含每个新端点的正常 / 空数据 / 参数错误 / Owner 4xx 透传）、Web 单测 + build、platform-support 模型 / 数据一致性测试、`node scripts/pdmaner/generate-oe-client-schemas.mjs --check`（若有改 commercial）。
- 不改 commercial 服务代码；若 H 线接口缺字段，记到 `notes/I2-gaps.md`，不要绕过。
- 完成后 rebase 到 `commercial/second-slice`（若已存在），一次提交 `feat(operations): 商务控制台第二切片 I2 合同列表 / 工作台 / 价目总览与版本对比 / 产品目录 / 变更历史`，不 push、不合入 main。汇报：分支头、端点清单、块 93 行数、各模块测试数、遗留项。
