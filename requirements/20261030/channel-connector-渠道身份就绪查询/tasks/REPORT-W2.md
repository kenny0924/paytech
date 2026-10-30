# REPORT-W2 · 阶段 4 前端（T5）

> 由主会话依 W2 最终回复原文落盘（W2 写主工作区文件被 harness 拦截）。主会话复核：工作树 9 个文件改动、`tsc` 通过、`npm test` 1688 / 1688、新增原因键存在、`CHANNEL_IDENTITY_NOT_CONNECTED` 两语言均为 0、MID 链接按 `hasMenu(resources, channelMenus.mids)` 门控。"改值 3 键"主会话接受（不改变行为），已告知用户。

工作树：`/Users/kenny/Desktop/Workspace/Code-MoR-req02/operations-console-web`（基于 main `331d8e0e`）

## 改动文件

| 文件 | 改动 |
|---|---|
| `src/commercial/capabilityGate.ts` | `PrerequisiteLink` 增加 `{ kind: 'mids'; entityNo }`。`lineLink`：五个渠道原因 → `mids`（entityNo 取参数，参数缺失时取开通项的 OE）；`CLIENT_MCC_MISSING` → `client`；`AWAITING_PRICE`（结果为 SKIPPED）无链接；UNKNOWN 仍为 `recheck`。ICU 参数新增 `paymentMethodCode`、`mccCode`、`prerequisite`（前置项代码）；`clientId` 先取 `params.clientId`。`prerequisiteReason` 和 `prerequisiteShortReason` 增加可选第 4 个参数：前置项代码 |
| `src/commercial/ProductCapabilitySections.tsx` | 新增 `CapabilityGateProps.canViewMids`。`mids` 链接跳 `/channel/mids?entityNo=<OE>`，运营没有 MID 菜单时不显示。UNKNOWN 图标从问号改为感叹号（沿用现有琥珀色），与原型一致。卡住原因的短文案带上前置项代码 |
| `src/commercial/AgreementDetail.tsx` | `canViewMids: hasMenu(resources, channelMenus.mids)`，复用现有 MENU `OPERATIONS_CHANNEL_MIDS`，没有新增权限码 |
| `src/channel/channel.ts` | 新增 `channelRoutes.midsOfEntity`、`channelMenus.mids`、`hasMenu`（写法同 underwriting / limitTier）。新增 `midListInitialFilters(search)`，只接受符合 `entityNoPattern` 的值 |
| `src/pages/ChannelMids.tsx` | 用 URL 里的 `entityNo` 初始化筛选表单和查询；加 `MidList key={location.search}`。"重置"清空全部筛选，不回到 URL 带来的值。不带参数时行为不变 |
| `src/i18n/en/commercial.json`、`i18n/zh-CN/commercial.json` | 见下节 |
| `src/commercial/capabilityGate.test.ts`、`src/channel/channel.test.ts` | 新增 2 个测试，修改 1 个测试数据 |

## i18n 键（commercial 命名空间，zh-CN 和 en 各一套）

**新增 15 键：**
- `commercial.enum.CapabilityPrerequisiteReason.*`：`MID_NOT_FOUND`、`PAYMENT_METHOD_NOT_SUPPORTED`、`MCC_NOT_COVERED`、`CURRENCY_NOT_SUPPORTED`、`PAYMENT_ACTION_NOT_SUPPORTED`、`CLIENT_MCC_MISSING`、`AWAITING_PRICE`
- `commercial.prerequisite.shortReason.*`：同上 7 个
- `commercial.prerequisite.link.mids`：中文"去配置 MID →"，英文"Configure MID →"

**删除 2 键**（发布时标记废弃）：`commercial.enum.CapabilityPrerequisiteReason.CHANNEL_IDENTITY_NOT_CONNECTED`、`commercial.prerequisite.shortReason.CHANNEL_IDENTITY_NOT_CONNECTED`

**改值 3 键（任务卡未列，主会话接受）：**
- `commercial.enum.CapabilityPrerequisiteResult.SKIPPED`："未接入" → "暂不核对"（en: Not Connected → Not Checked）。SKIPPED 现在表示"不适用"或"等待价格"。只用于图标读屏与悬停提示。
- `commercial.enum.CapabilityPrerequisiteReason.CHECK_UNAVAILABLE` 与 `commercial.prerequisite.shortReason.CHECK_UNAVAILABLE`：改为 `{prerequisite, select, CHANNEL_IDENTITY {…} other {原文案}}`；资格项文案不变（有测试覆盖），渠道身份项显示"暂时无法核对渠道身份，系统会自动重试"（AC-V3）。

## 验证

- `npm ci --offline` 后 `npx tsc --noEmit` 通过。
- `npm test`：1688 / 1688。新增测试一（capabilityGate）：渠道身份 8 种结论的图标、说明、链接与计数；7 个短原因；无币种文案分支；资格项 `CHECK_UNAVAILABLE` 不变；已删除原因码无文案。新增测试二（channel）：去配置 MID 路由；URL 预填合法 / 缺失 / 小写 / 超长 / 无关参数五种；MENU 判断。原测试数据 `CHANNEL_IDENTITY_NOT_CONNECTED` 改为 `AWAITING_PRICE`。
- `npm run build` 通过；`npm run i18n:export-source` 27 个命名空间 11142 键（commercial 889）。
- 浏览器走查（AC-V1—V4）留阶段 5。

## 与原型的差异

- `CLIENT_MCC_MISSING` 带 Client ID："Client {clientId} 的经营分类（MCC）尚未确认"（原型 B 无 ID）。
- `PAYMENT_METHOD_NOT_SUPPORTED`：有币种参数用原型 A 句式，无币种用原型 B 句式。
- 多币种用现有 `joinList`：中文"EUR、GBP"，英文"EUR, GBP"。
- 原型右侧灰色编码不在页面显示（原型注明仅供评审）。
