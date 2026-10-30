# W2 报告（前端，2026-10-31，主会话据代理回报整理）

代理回报"无法写报告文件"，以下为其回报内容的整理。

## 状态

- 页面已完成。`npx tsc --noEmit`、`npm run build` 通过；`npm test` 1683 例中 1682 通过，唯一失败是 Web 能力清单与 Platform `console-resource-capabilities.json` 的一致性测试（等 W1 加入新页面条目）。
- 只改了工作树 `Code-MoR-plops` 的 `operations-console-web/`；未提交、未推送、未碰 mor-test、未启动开发服务。

## 本次改动

- 合同变更：从类型、解码器、测试和界面中删除 `ledgerLastConsumedTime`；解码器逐字段复制流字段，BFF 多发该字段也不会进入页面（有测试）。
- 第 ③ 段原"最后消费"指标改为：已消费序号、落后数；有落后时增加"卡住批次生成于"（取最早的 `stuckPosting.postedTime`），新键 `postingOps.metric.stuckSince`（en / zh-CN），旧键删除。
- 两个既有登记测试随新页面更新：`configurationModel.test.ts` 页面数 83 → 84；`finance.test.ts` 财务页面顺序加入新页及两个动作。

## 文件

- 新增：`src/finance/postingOps.ts`、`usePostingProgress.ts`、`PostingProgressParts.tsx`、`PostingStreamBrowse.tsx`、`postingOps.css`、`postingOps.test.ts`、`src/pages/FinancePostingOps.tsx`。
- 修改：`pageCapabilities.ts`、`ConsolePage.tsx`、`menu.ts`（页面与 `/streams/:streamId` 子路由）；en / zh-CN 的 `finance.json`、`nav.json`；上述两个登记测试。

## 原型状态对照

| 原型 | 实现 |
|---|---|
| P1 | 三段卡片、过账流表、检查时间与刷新；"重建余额"仅在有 REBUILD 权限时显示 |
| P2 | 第 ③ 段与落后行警示色、行内"追平"、卡住批次卡片（`stuckPosting` 为 null 时隐藏） |
| P3 | 四种结果条：已追平；部分（"继续 · 剩余 N"，N = 刷新后 account 最大序号 − 新 `lastSequence`）；409；400 规则拒绝（`code` + `detail`、记账规则链接、"重新追平"）。除 400 外都会刷新 B1 |
| P4 / P5 | 确认弹窗 → 页顶结果条：已纠正 / 无需纠正 / `STREAM_NOT_FOUND` / 409；之后刷新 B1 |
| P6 | 按序号升序、每页 50、"加载后 50 批"、已消费 + 1 标"卡住"、通过 URL 打开批次抽屉、返回保留账簿 |
| P7 | 账簿写入 `?ledgerBookId=`；未选时空状态，重建按钮置灰 |
| P8 | 不可用段落有单独重试（重新加载整页）；B1 503 整页错误可重试 |
| P9 / P10 | 服务费 OE 流有标注；只读账号看到"需执行权限"，无按钮 |
| P11 | 小于 390 px 时三段纵向、表格改卡片 |

追平和重建一次只发一个请求，执行中其他行禁用，不会并发。

## 待确认

1. 第 ② 段第三项：原型是"未发出 AccountPosted"数量，但 B1 没有该字段，改为显示流数量。
2. 菜单英文名用 "Posting Operations"（与其他菜单大小写一致），任务卡写的是 "Posting operations"。
3. 额外结果状态：增加了 504 超时结果条（合同有、原型未画）；重建响应缺少 `changedAccountCount` 时显示"已重算"。
4. 400 规则拒绝的错误码按 ledger 原样显示，不翻译。
