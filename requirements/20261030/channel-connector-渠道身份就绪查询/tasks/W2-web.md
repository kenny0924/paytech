# W2 · 阶段 4 前端：运营控制台"还差什么"渠道身份一项与 MID 页预填

## 目标

在工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-req02/operations-console-web`（detached，main `331d8e0e`）按已确认原型实现 T5。**不提交、不推送、不碰 mor-test、不发布语言包。**

## 必读

- `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/channel-connector-渠道身份就绪查询/technique.md` 第 10.2 节（原因码与参数表）、第 12 节（前端改动表）；`requirement.md` AC-V1—AC-V4。
- 原型：`…/prototypes/ops/商务-18-还差什么-渠道身份.png`（`operations-console-web/docs/operations-console.pen` 画板「商务 · 18」，**只读，不要修改 .pen**）。
- `agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 10 节（前端文案与语言）；ops-web 现有 `src/commercial/capabilityGate.ts`、`capabilityGate.test.ts`、`ProductCapabilitySections.tsx`、`pages/ChannelMids.tsx`、`src/i18n/en/commercial.json`、`i18n/zh-CN/commercial.json`、页面能力判断（`navigation/pageCapabilities.ts` 等）。
- 经验：`~/.claude/projects/-Users-kenny-Desktop-Workspace-Code-MoR/memory/` 下 `ops-web-shared-tree-release.md`、`acceptance-needs-browser-pass.md`。

## 任务（T5）

1. `capabilityGate.ts`：`PrerequisiteLink` 增加 `{ kind: 'mids'; entityNo: string }`；`lineLink` 把 `MID_NOT_FOUND`、`PAYMENT_METHOD_NOT_SUPPORTED`、`MCC_NOT_COVERED`、`CURRENCY_NOT_SUPPORTED`、`PAYMENT_ACTION_NOT_SUPPORTED` → `mids`（entityNo 取参数）；`CLIENT_MCC_MISSING` → `client`；`AWAITING_PRICE` → 无链接（灰色减号，结果为 SKIPPED）；UNKNOWN 仍为 recheck。
2. `ProductCapabilitySections.tsx`：`mids` 跳 `/channel/mids?entityNo=<OE>`，链接文案"去配置 MID →"；当前运营没有 MID 页面能力时不渲染该链接（复用现有页面能力 / 菜单可见性判断，不新造权限常量）。
3. `pages/ChannelMids.tsx`：读取 URL `entityNo` 作为初始筛选并带入查询；无参数行为不变；参数非法（不符 `entityNoPattern`）时忽略。
4. i18n：zh-CN 与 en 的 `commercial` 命名空间新增 7 个原因（`MID_NOT_FOUND`、`PAYMENT_METHOD_NOT_SUPPORTED`、`MCC_NOT_COVERED`、`CURRENCY_NOT_SUPPORTED`、`PAYMENT_ACTION_NOT_SUPPORTED`、`CLIENT_MCC_MISSING`、`AWAITING_PRICE`）的完整说明（`enum.CapabilityPrerequisiteReason.*`，含 `{entityNo}`/`{paymentMethodCode}`/`{mccCode}`/`{currencies}`/`{clientId}` 占位，文案与原型一致）与短原因（`prerequisite.shortReason.*`）；"去配置 MID →"链接文案；删除 `CHANNEL_IDENTITY_NOT_CONNECTED` 两键（不留死键）。键的放置与命名沿用现有同类键。
5. 测试：扩展 `capabilityGate.test.ts`（每个原因的链接种类与文案插值、AWAITING_PRICE 无链接、UNKNOWN recheck）；MID 页 URL 预填的单测（若现有测试结构支持）；i18n 目录 / 键一致性测试若有失败按新键修。

## 验证

`npm ci --offline`（如 node_modules 缺）→ `npx tsc --noEmit` → `npm test` → `npm run build` → `npm run i18n:export-source`。浏览器走查放阶段 5，本阶段不起服务。

## 规则

- 只改工作树 `Code-MoR-req02/operations-console-web`；不改 `.pen`、不改主工作区（本需求 `tasks/` 下写报告除外）。
- 不调用任何 Superpowers skill。
- 完成后写 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/channel-connector-渠道身份就绪查询/tasks/REPORT-W2.md`（改动文件、新增 / 删除的 i18n 键清单、测试数字、与原型的差异），回复 ≤ 20 行摘要。
