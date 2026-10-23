# M7 报告：data-query / file / notification / webhook / vault 文案迁移

执行日期 2026-10-23 · 研发目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-m7`（含 F0 成果），未提交、未推送；主工作区只写本报告。未调用 Superpowers skill，未派生子代理。

## 1. 迁移文件

全部相对 `operations-console-web/src/`，均已加入 `i18nSourceGuard.test.ts` 的 `MIGRATED_DIRS`。

| 命名空间 | 文件 |
|---|---|
| `data-query` | `dataQuery/` 全部：`dataQueryCommon.ts`、`DataQueryParts.tsx`、`useDataQueryCommand.ts`、`dataSourceModel.ts`、`DataSourceEditor.tsx`、`templateModel.ts`、`versionDiff.ts`、`TemplateEditor.tsx`、`DeclarationPanel.tsx`、`TrialPanel.tsx`、`VersionDrawer.tsx`、`BodyEditor.tsx`（`templateSync.ts` 只有注释含中文，未改）；`pages/DataSources.tsx`、`pages/QueryTemplates.tsx`；测试 `dataSourceModel.test.ts`、`templateModel.test.ts`、`versionDiff.test.ts` |
| `file` | `fileStorage/` 全部：`fileStorage.ts`、`scanPolicy.ts`、`ScanPolicyTab.tsx`、`StorageModals.tsx`（`useStorageCommand.ts` 只有注释）；`fileRecords/` 全部：`fileRecords.ts`、`FileRecordParts.tsx`、`FileRecordList.tsx`、`FileRecordDetailView.tsx`、`useFileRecordDownload.ts`；`pages/FileStorage.tsx`（`pages/FileRecords.tsx` 只有注释）；测试 `fileStorage.test.ts`、`scanPolicy.test.ts`、`fileRecords.test.ts` |
| `notification` | `notification/`：`notification.ts`、`ProviderModals.tsx`、`StatusChangeModal.tsx`（`useNotificationCommand.ts` 只有注释）；`pages/NotificationProviders.tsx`、`NotificationRecords.tsx`、`NotificationTemplates.tsx`；测试 `notification.test.ts` |
| `webhook` | `webhook/`：`webhook.ts`、`ClientSelector.tsx`、`DeliveriesTab.tsx`、`DeliveryDetailDrawer.tsx`、`WebhookModals.tsx`（`useWebhookCommand.ts` 只有注释）；`pages/WebhookManagement.tsx`；测试 `webhook.test.ts` |
| `vault` | `vault/`：`vault.ts`、`ReasonField.tsx`、`SensitiveValue.tsx`、`TicketField.tsx`、`useVaultDataTypes.ts`（`useVaultCommand.ts` 只有注释）；`pages/Vault*.tsx` 六个页面；测试 `vault.test.ts` |

另改：`src/i18nSourceGuard.test.ts`（加入上述目录与文件）；5 个命名空间的 `src/i18n/en/<ns>.json` 与 `i18n/zh-CN/<ns>.json`。范围外文件未改。

## 2. 各命名空间 key 数

`data-query` 372 · `file` 403 · `notification` 278 · `vault` 194 · `webhook` 147，合计 1394。

页面 h1 与菜单同名的全部改用 `nav.resource.<CODE>`（Vault 六页、Webhook、发送账号 / 发送记录 / 通知模板、存储管理、文件记录；模板与文件详情的返回链接 / 面包屑也用同一 key）。

## 3. 自检

| 项 | 结果 |
|---|---|
| `npm test` | 1475 个，全部通过（未删测试；原断言中文的改为断言英文或 `testT` key，并补了若干断言：未知字段回显、Client 访问各状态、保存门槛原因、空检查结果等） |
| `npx tsc --noEmit` | 通过 |
| `npx vite build` | 通过（大 chunk 警告为既有提示） |
| `node scripts/i18n-export-source.mjs` | 成功：21 个命名空间、2128 key（本卡 5 个命名空间见上） |
| 守卫 | 本卡范围源码去注释后汉字（含全角标点）为 0，已在 `MIGRATED_DIRS` 严格执行；全仓剩余 226 / 354 个文件 |
| 浏览器验证 | 未做（需要 BFF / Platform 数据；留给 F9 / 阶段 5 的逐页比对） |

## 4. 与指南不一致或指南未覆盖的做法（请主会话确认）

1. **并列项分隔符**：原中文用"、"连接的动态列表（缺失配置字段、参数名、未提供参数、MIME 类型、通过结果码）改用 `Intl.ListFormat(locale, { style: 'narrow', type: 'conjunction' })`（模块内 `joinList`），zh-CN 输出仍是"a、b、c"，en 为"a, b, c"；没有建分隔符 key。字段错误汇总原用"；"，`Intl` 没有对应格式，建了 `data-query.error.fieldErrorSeparator`（en `"; "`，带尾随空格，参照 `i18n-admin.issues.separator` 先例）。
2. **首尾空白**：除上条外，en 还有两处尾随空格：`vault.manualProtect.regexHint`（"…regex: " 后接等宽正则）、`data-query.trial.bindings`（"Bindings: "）。若 Platform 导入会裁剪空白需改为拆分渲染。
3. **句中等宽代码 / 句中输入框**：`DataQueryParts` 新增 `mono()` + `<MonoText>`（参数说明、系统参数说明里的 `#{}`、`__cursor.*` 等保持等宽）与 `slot` + `<SlotText>`（"第 {input} 页"中嵌入页码输入框），均以控制字符作 ICU 参数占位再拆分渲染。
4. **服务端原文用 `\u` 转义**：`useDataQueryCommand.ts` 中用于比较的 Platform 通用 detail"业务校验未通过"、`templateModel.ts` 解析服务端位置前缀"第 N 行第 M 列："的正则，都是服务端数据而非界面文案，按指南第 5 节写法转义并注明原因。
5. **标签映射改为调用时函数**：`fileStorage.ts` / `scanPolicy.ts` / `fileRecords.ts` / `dataQueryCommon.ts` / `templateModel.ts` 中被多个文件（含 `fileRecords` 引用 `fileStorage`）复用的 `*Labels` 映射改成 `accessLevelLabel(v)`、`pagingModeLabel(v)` 等函数（内部 `enumLabel(translator(ns), …)`），其余模块用组件内 `enumLabel(t, …)`。
6. **"未配置 / 默认规则"等嵌入访问级别的句子**：用 ICU `select`（`{level, select, PUBLIC {公开} other {私有}}`），en 得以使用小写 public / private；zh 逐字不变。
7. **`ConflictBanner` 的 `subject`** 由自由中文改为 `'data' | 'dataSource' | 'template'`，三句各一个 key；`useResource` 第三参数改为可选，缺省用 `data-query.parts.detailLoadFailed`（M6 的 `CardBinLibrary.tsx` 不传该参数，签名兼容、未改它）。
8. **写入规则编辑的只读说明**（"来源系统：X · 访问级别：…"）合成一句，来源系统不再单独等宽显示（文字不变）。
9. **数据源 / 实例 / 桶 / 模板等启停弹窗** 原来用"动词 + 名词"拼接（`${verb}存储实例 X`、`确认${verb}`、`${title}失败`）；改为每个目标一句完整 key（`StatusModal` / `ReasonModal` 增加 `failureTitle` 参数）。
10. **跨卡依赖**：`fileRecords/fileRecords.test.ts` 的"审计中文名"用例从 `pages/auditView.ts`（M6）导入 `auditActions` / `auditResourceTypes` 并断言 `FILE_SCAN_POLICY === '检查策略'`。本目录中 M6 尚未改动，测试通过；M6 改造 `auditView` 后该用例需按其新导出调整。M6 已回复：它在自己的目录中重写了该文件的 auditView 导入行与"审计中文名"用例（改用 `auditActionCodes` / `auditResourceTypeCodes` 与 `testT('access')`），本目录保持这些行不动，合并冲突时取 M6 版本。**注意**：M6 与本卡都在该文件新增了 `import { testT } from '../i18n/testing.ts';`（本卡另有 `const t = testT('file')` 与 `enumLabel` 导入），合并后需去掉重复的 `testT` 导入，否则 tsc 报重复声明。

## 5. 拿不准的英文用词（请主会话定）

| 命名空间 | 中文 | 现用英文 |
|---|---|---|
| vault | 每次新 Token / 同值同 Token（Token 模式） | New Token Each Time / Same Value, Same Token |
| vault | 普通敏感 / 密钥（数据级别） | Standard Sensitive / Secret |
| vault | 核对客户信息（理由码 CUSTOMER_VERIFICATION） | Client Information Check |
| vault | 初始化（变更类型 SEED）、整体策略（对象类型 POLICY） | Initial Seed / Entire Policy |
| vault | 操作人、工单号、用途 / 用途说明 | Operator / Ticket Number / Purpose / Purpose Description |
| webhook | 待投递 / 已放弃（投递状态 CREATED / DEAD） | Pending / Abandoned |
| webhook | 签名密钥第 N 号 | Signing Secret · No. N（列表单元格 "No. N"） |
| webhook | 发起方式：自动投递 / 人工 Replay | Triggered By: Automatic delivery / Manual Replay |
| notification | 发件地址 / 密钥（SES 凭据） | From Address / Credentials |
| notification | 主 / 备（路由角色） | primary / fallback |
| notification | 被拒发 / 被投诉 / 软退信 / 硬退信 | Rejected / Complaint / Soft Bounce / Hard Bounce |
| notification | 发送记录 {id}（详情抽屉标题） | Delivery Record {id} |
| file | 写入规则 / 存储实例 / 凭据方式 / 固定密钥（Vault） | Write Rule / Storage Instance / Credential Mode / Static Keys (Vault) |
| file | 更换密钥 / 录入密钥 | Replace Keys / Enter Credentials（notification 用 Credentials，file 用 Keys，与原中文"密钥"各自页面语境对应） |
| file | 检查（桶检查、文件格式检查）/ 检查中 / 未通过 | Check / Checking / Failed |
| file | 修订 / 生效中（检查策略修订） | Revision / Active |
| file | 已作废（上传状态 CANCELLED） | Voided |
| file | 首次上传 / 替换 / 候选 / 已替换 | First Upload / Replacement / Candidate / Superseded |
| data-query | 片段 / ⟨片段⟩ | fragment / ⟨fragment⟩ |
| data-query | 不屏蔽 / 不返回 / 全部掩码 / 保留首尾（列掩码） | No Masking / Hidden / Fully Masked / Keep Edges |
| data-query | 试运行 | Trial Run |
| data-query | 展开校验 | Expanded validation |
| data-query | 账号（数据库账号）/ 默认库 | Account / Default Database |
| data-query | 引用模板（列）/ 允许总数 | Templates / Total Allowed |
