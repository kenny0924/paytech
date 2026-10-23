# M4 报告：channel 命名空间迁移

执行日期 2026-10-23 · 研发目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-m4`（基于 F0 成果），未提交、未推送；主工作区只写本报告。未调用 Superpowers skill，未派生子代理。

## 1. 迁移文件清单（相对 `operations-console-web/src/`）

- `channel/`：`channel.ts`、`channelWorkspace.ts`、`useChannelCommand.ts`、`ChannelParts.tsx`、`ChannelChanges.tsx`、`ChannelResolve.tsx`、`ChannelOverview.tsx`、`ChannelCredential.tsx`、`AccountConfigurationModal.tsx`、`CapabilityEditor.tsx`、`CostVersionDiff.tsx`、`CostVersionEditor.tsx`、`ChannelGrants.tsx`、`ChannelProtocol.tsx`；测试 `channel.test.ts`、`channelWorkspace.test.ts`
- `externalApi/`：`editorModel.ts`、`externalApiModel.ts`、`flowModel.ts`、`integrationApiModel.ts`、`mappingModel.ts`、`externalApiHooks.tsx`、`ui.tsx`、`EditorParts.tsx`、`FieldTable.tsx`、`FieldMapping.tsx`、`ScriptEditor.tsx`、`IntegrationApiEditor.tsx`、`FlowCanvas.tsx`、`FlowEditor.tsx`、`FlowPanels.tsx`、`verify/verifyModel.ts`、`verify/VerifyParts.tsx`、`verify/VerifyTab.tsx`、`verify/VerifyRunDetail.tsx`、`verify/VerifyAccountEditor.tsx`、`verify/VerifyCaseEditor.tsx`；测试 `externalApiModel.test.ts`、`flowModel.test.ts`、`integrationApiModel.test.ts`、`mappingModel.test.ts`（`verify/verifyModel.test.ts` 无需改）
- `pages/`：`ChannelChannels.tsx`、`ChannelMids.tsx`、`ChannelCostPlans.tsx`、`ExternalStandardApis.tsx`
- 种子：`src/i18n/en/channel.json`、`i18n/zh-CN/channel.json`
- 守卫：`i18nSourceGuard.test.ts` 的 `MIGRATED_DIRS` 加入 `channel/`、`externalApi/` 与上述 4 个页面文件

## 2. key 数

`channel` 1157 个（en / zh-CN 同一组 key）。页面 h1 与面包屑中与菜单同名的文字读 `nav.resource.*`（`OPERATIONS_CHANNEL`、`OPERATIONS_CHANNEL_CHANNELS`、`OPERATIONS_CHANNEL_MIDS`、`OPERATIONS_CHANNEL_COST_PLANS`、`OPERATIONS_EXTERNAL_STANDARD_APIS`），未改 `nav.json`。

## 3. 自检结果

| 项 | 结果 |
|---|---|
| `npm test` | 1475 个，全部通过（channel + externalApi 64 个） |
| 守卫 | 范围内源码去注释后汉字为 0（已入 `MIGRATED_DIRS` 严格检查）；全局剩余 234 / 354 个文件、约 8735 处 |
| `npx tsc --noEmit` | 通过 |
| `npx vite build` | 通过（大 chunk 警告为既有提示） |
| `node scripts/i18n-export-source.mjs` | 成功：21 个命名空间、1891 key，channel 1157 |
| 中文逐字核对 | 脚本把每个 zh-CN 值按 `{参数}` 切开，逐段对照 HEAD 原源码；29 处不在原文中的片段逐条复核，都是条件拼接合并成整句（如 `${'驳回'/'批准'}版本 v…`）或 JSX 跨行文本合并（JSX 会把换行折成一个空格，种子按渲染结果写入），没有错字 |

## 4. 改动范围外文件

无。只改了 `i18nSourceGuard.test.ts` 的 `MIGRATED_DIRS`（卡片要求）。范围外没有其他文件引用本模块的标签导出（`App.tsx` 只懒加载 `ChannelResolve`，未改）。

## 5. 拿不准的英文用词（请主会话定）

1. **驳回 / 已驳回**（成本版本审批）：术语表只给出 已驳回 = Returned，按此把状态写成 Returned、动作写成 Return；审批场景里 Reject / Rejected 可能更自然，状态码本身是 REJECTED。
2. **放行**（调用授权 ACTIVE）写成 Allowed，图例中“拒绝（保留记录）”写成 Denied (record kept)。
3. **技术账号**写成 Channel Account（页签 Channel Accounts），变更对象 CHANNEL_ACCOUNT 写成 Channel Account / Credential；**协议配置**页签写成 Protocol。
4. **沙箱验证**写成 Sandbox Test，**需重新验证**写成 Needs Re-test，**未通过**写成 Failed。
5. 流程编辑器用词：出口 → Exit，必经节点结果 → Required Node Results，受控句柄 → Protected Handle，初始化项 → Initializer，取值 → Value，字段映射 → Field Mapping。
6. 变更类型：配置写入 → Configuration Write，改绑账号 → Account Rebind，作废 → Void。
7. 调用方显示名：交易对手 → Counterparty，托管收银台 → Hosted Checkout，支付编排 → Payment Orchestration，收单 → Acquiring，客户主数据 → Client Master Data。
8. 编制人 / 复核人 → Maker / Checker，内容摘要 → Content Hash，经营主体编号 → Operating Entity No.

## 6. 与指南不一致之处 / 需要注意

1. **句中带样式片段**：运行时 `t` 只返回字符串，而不少句子里嵌有等宽编码或链接（如“保存后立即生效：`caller` 可经通用入口调用 `NI · PAY`”）。为了不拆句，新增 `channel/ChannelParts.tsx` 的 `richText(t, key, nodes, params)`：整句一个 key，片段作为命名参数传入，翻译后按位置拼回节点。M4 内 externalApi 也复用它。其他模块有同类需要时，F9 可以考虑把它移进 `src/i18n/`。
2. **守卫正则缺陷（已发消息给 team-lead）**：`src/i18nSource.ts` 的 CJK 区间 `豈-﫿` 起点是 U+8C48（统一汉字“豈”），不是 U+F900，所以区间覆盖了 UTF-16 代理对，任何 emoji 都会被当成中文。M4 没改该文件，在 `externalApi/ui.tsx` 新增 `lockMark = '\u{1F512}'`（🔒）供 4 个文件使用。正则修正后可以改回字面量。
3. **内部标识改为代码**：字段映射的目标分组原来直接用中文“调用参数”“响应字段”作为分组标识，现改为 `PARAMS` / `RESPONSE`，显示时翻译（`channel.mapping.group.*`）；PATH / HEADER 等位置代码原样显示。
4. **标签表改为函数**：`lifecycleStatusLabels` 等改为 `lifecycleStatusLabel(value)` 等函数（`enumLabel`，调用时翻译）；验证状态表改为色调表 `*StatusTones` 加 `caseStatusTag` / `runStatusTag` / `stepStatusTag`；`scriptObjects().access` 改为 `READ_WRITE` / `READ_ONLY` 代码；`templateVariables` 改为函数。
5. **首尾空格**：少数英文文案带首尾空格，用于接在其他片段之后（如 `' · Initializers {ids}'`、`conflict.kept`、`flow.owner.*`）。如果 Platform 导入时会裁剪空白，这些位置的英文间距会受影响。
6. **数字按字符串传参**：原中文不带千分位的数字（超时 100～30000、字节数等）以字符串传给 ICU，避免 zh-CN 显示变成 “30,000”。
7. **服务端原文比较**：`channel.ts` 中与服务端默认消息比较的“业务校验未通过”不是界面文案，改为 `\u` 转义并加注释。
8. 测试名称仍为中文（只改断言）；4 处在 effect 中只用于失败通知标题的 `t`，按样板注释说明不作依赖，并加了 `eslint-disable-line`。
