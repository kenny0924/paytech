# 任务卡 M1—M7：模块文案迁移（共用说明）

你是执行代理，负责下表中**一张卡**的模块。主会话启动你时会告诉你卡号与独立工作目录。

| 卡 | 命名空间 | 文件范围（相对 `operations-console-web/src/`） |
|---|---|---|
| M1 | `finance`、`recon` | `finance/`、`reconciliation/`、`pages/Finance*.tsx`、`pages/Recon*.tsx` |
| M2 | `risk` | `risk/`、`pages/Risk*.tsx` |
| M3 | `workflow` | `workflow/`、`pages/Workflow*.tsx` |
| M4 | `channel` | `channel/`、`externalApi/`、`pages/Channel*.tsx`、`pages/ExternalStandardApis.tsx` |
| M5 | `commercial` | `commercial/`、`pages/Commercial*.tsx` |
| M6 | `access`、`gateway`、`client-review`、`card` | `tenantAccess/`、`configuration/`、`clientReview/`、`clientApi/`、`card/`、`pages/` 下 `Accounts`、`account*`、`Roles`、`role*`、`Permissions`、`TenantAccess`、`Audit*`、`audit*`、`GatewayConfiguration`、`gatewayConfiguration*`、`ConsoleResources`、`ApiScopes`、`ApiCredentials`、`ClientOnboardingReview`、`CardBinLibrary` |
| M7 | `data-query`、`file`、`notification`、`webhook`、`vault` | `dataQuery/`、`fileStorage/`、`fileRecords/`、`notification/`、`webhook/`、`vault/`、`pages/` 下 `DataSources`、`QueryTemplates`、`File*`、`Notification*`、`Webhook*`、`Vault*` |

范围包括这些文件的测试（`*.test.ts`）。若发现范围外的文件也只被本模块使用，可一并迁移并在报告中列出；**不得修改**其他卡的文件、`src/i18n/`、`common` / `auth` / `nav` / `enum` 的 JSON、`navigation/`、`App.tsx`、`main.tsx`、`localization/`。需要通用词时在自己的命名空间建 key，不往 `common` 加。

## 必读

1. `operations-console-web/docs/i18n-migration-guide.md`（F0 写的迁移指南，**照做**）与样板 `pages/CurrencySpecs.tsx`
2. `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261023/operations-控制台多语言迁移/glossary.md`（英文用词必须遵守）
3. 同目录 `technique.md` 第 3.2 节、`tasks/F0-report.md`
4. 额外：M5 要实现定价试算说明的翻译（BFF 已改为 `notes: [{code, params}]`，见 F0 报告）；M6 要实现审计详情字段名（按字段 key 翻译，未知 key 原样显示）与角色权限树（资源节点 `nav.resource.<code>` 用 `tOr` 回退 `displayName`——这是读 `nav` 命名空间，允许；权限节点显示编码；分组 `OTHER_PERMISSIONS` 显示 `access.permissionTree.other`）。

## 硬性要求

- **中文逐字不变**：每个 key 的 `zh-CN` 种子（`operations-console-web/i18n/zh-CN/<ns>.json`）必须是迁移前代码里的原中文，逐字（含标点、空格）保留；插值文案改为 ICU 后，zh-CN 用相同参数表达同一句话。
- **英文**：按术语表与指南的写法约定；数量句用 ICU 复数。
- 顶层常量不得含翻译后的文字，只存 key；不在模块加载时调用 `t`。
- 不翻译运营录入数据、编码、ID、金额、币种代码；`workflow/formModel.ts` 的拼音逻辑保留。
- 测试改为英文断言（用 `src/i18n/testing.ts`）；测试里作为业务数据的中文可以保留。不删除测试、不降低断言强度。
- 完成后把你的目录 / 文件加入守卫测试的 `MIGRATED_DIRS`，你范围内的源码汉字数必须为 0。

## 工作目录与边界

- 只在主会话给你的独立工作目录中修改（形如 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-mN`），**不提交、不推送**，不碰主工作区与其他工作目录。
- 自检：`npm test`（全部通过）、`npx tsc --noEmit`、`npx vite build`、`node scripts/i18n-export-source.mjs`（成功，报告你的命名空间 key 数）。
- 不调用 Superpowers skill，不派生子代理，全程前台执行，完成后直接结束。

## 报告

写到 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261023/operations-控制台多语言迁移/tasks/<卡号>-report.md`：迁移文件清单、各命名空间 key 数、自检结果（测试数、构建、守卫在你范围内为 0）、改动范围外文件（如有）、拿不准的英文用词（列出来给主会话定）、与指南不一致之处。最终回复简述同样内容。
