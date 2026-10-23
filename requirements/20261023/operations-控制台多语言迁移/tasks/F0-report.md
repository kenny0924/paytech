# F0 报告：多语言迁移基础

执行日期 2026-10-23 · 研发目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n`（detached，基线 `9f210b5c`），未提交、未推送，主工作区只写本报告。未调用 Superpowers skill，未派生子代理。

## 1. 改动文件

**运行时与语言切换（`operations-console-web/src/i18n/`）**
- `runtime.ts`：新增 `cache`（manifest 与语言包本地缓存、缓存启动、后台核对新发布、配额异常清空、清理旧语言包、预览不读写）、`prefetch(code)`、`rememberLocale(code)`、`has(ns, key)`。
- `index.ts`：`defaultLocale: 'en'`、`publishedOnly: true`、`document`、`cache: localStorage`、不读浏览器语言；新增 `Translate`（带 `.has`）、`bindTranslate`、`tOr`、`enumLabel`、`formatDateTime`；导出 `OPS_NAMESPACES`。
- `react.tsx`：`useT` 返回 `Translate`；`useLocale` 只返回 `{ locale, locales }`；`I18nGate` 记录已就绪命名空间，嵌套 Gate 不再等待。
- 新增 `namespaces.ts`、`LanguageSwitcher.tsx`、`switchLanguage.ts`（+ 测试）、`testing.ts`；`builtin.ts` 引入全部 21 个 `en/<ns>.json`（新增 19 个文件，未迁移的为 `{}`）。
- `runtime.test.ts`：新增 8 个缓存 / prefetch / has 用例。

**框架层**：`main.tsx`（根部 Gate、antd `en_US`/`zh_CN`、dayjs 语言）、`App.tsx`、`auth/Login.tsx`（右上角语言按钮）、`auth/RequiredAction.tsx`（`passwordRules` 改为函数）、`auth/webmcp.ts`、`navigation/menu.ts`（新增 `resourceLabel`）、`navigation/SidebarMenu.tsx`、`navigation/NavigationCollapseControl.tsx`、`feedback/errorDetails.ts`、`feedback/useErrorNotification.tsx`、`components/DatePicker.tsx`、`components/TimeRangePicker.tsx`、`components/DeleteConfirmationModal.tsx`、`components/GatewayStatusModal.tsx`、`components/RefreshButton.tsx`、`hooks/usePaging.ts`、`api/session.ts`（`ApiError` 兜底消息）、`api/types.ts`、`styles.css`、`index.html`（`lang="en"`、英文 description）。

**样板 currency**：`pages/CurrencySpecs.tsx`、`configuration/currencySpecModel.ts`（+ 测试）。

**防回归与一致性**：新增 `src/i18nSource.ts`（扫描工具）、`src/i18nSourceGuard.test.ts`、`src/i18nCatalog.test.ts`；`localization/i18nCoverage.test.ts` 保留，改读新种子并只管 `i18n-admin` 与本模块的 `enum` key。

**种子与导出**：`i18n/operations-console.zh-CN.seed.json` 拆为 `i18n/zh-CN/<ns>.json`（21 个，扁平）并删除旧文件；`scripts/i18n-export-source.mjs` 重写。

**为接口同步而改的他人模块文件（最小改动，页面翻译仍归模块卡）**：`pages/Accounts.tsx`（`passwordRules()`）、`pages/Audit.tsx` / `pages/auditView.ts`(+test)（去掉 `label`，暂显示 key）、`commercial/commercialPreview.ts`(+test) / `pages/CommercialPriceSimulator.tsx`（`notes` 结构，暂显示编码）、`pages/roleEditor.test.ts`（节点补 `code`）、`risk/riskSecurity.test.ts`（依赖图在 `src/i18n` 处停止）、`risk/riskPageWiring.test.ts`、`risk/riskEvaluationApi.test.ts`、`api/session.test.ts`、`feedback/errorDetails.test.ts`（兜底消息断言改英文）。

**文档**：`operations-console-web/docs/i18n-migration-guide.md`。

**operations-console-bff**：`RoleModels`（`PermissionResource.resourceCode`、`PermissionNode.code`）、`RolePermissionTree`（`OTHER_PERMISSIONS`，资源缺编码视为上游无效）、`PlatformOperationsClientImpl`（透传 `resourceCode`）、`AuditModels` / `AuditEventDetailsProcessor`（删除 `LABELS` 与 `label`）、`CommercialPricePreviewModels`（`IllustrationNote`、`IllustrationNoteCode` 枚举，`notes` 替换 `note`）、`CommercialFeeIllustrator`；测试 `RolePermissionTreeAutoTest`（+1 用例）、`OperationsBrowserBoundaryAutoTest`、`CommercialPricePreviewAutoTest`。

## 2. 约定要点

- key：`<ns>.<区域>.<名称>`，段名小驼峰（方案 3.2）；模块私有枚举 `<ns>.enum.<Type>.<VALUE>`；菜单 `nav.resource.<资源编码>`。
- 组件内 `useT('<字面量命名空间>')`；非组件 `translator(ns)` 在调用时取文案；`tOr` 只用于有更好回退的场景（菜单名回退 `display_name`）；`enumLabel(t, prefix, value)` 未知值显示原代码。
- 语言：`?lang=`（本标签页）> 本地选择 `i18n.locale` > `en`；只用 / 只列 manifest 已发布语言，少于两种时不显示按钮。切换 = `prefetch` → 写本地选择 → 去掉 `?lang` → 整页刷新同一路由。
- 缓存：`i18n.cache.manifest.<locale>`、`i18n.cache.bundle.<url>`；有缓存时 Gate 不等网络，后台发现摘要变化后等新语言包加载完再替换并 `notify()`。
- 模块卡不改 `common`/`auth`/`nav`/`enum` 四个 JSON；`gateway.json` 已有 F0 的 `gateway.statusModal.*`。
- 种子 zh-CN = 原中文逐字；`nav.resource.*` 的中文取 mor-test Platform `display_name`（即今天菜单 / 权限树显示的文字），Platform 未登记的 `OPERATIONS_CLIENT_ONBOARDING_REVIEW`（componentKey）取 `pageCapabilities` 名称。

## 3. 验收结果

| 项 | 结果 |
|---|---|
| `npm test` | 1475 个，全部通过（基线 1455；新增 20） |
| `npx tsc --noEmit` / `npx vite build` | 通过 / 通过（大 chunk 警告为既有提示） |
| `node scripts/i18n-export-source.mjs` | 成功：21 个命名空间、734 key；`operations-console.en.json` 54.0 KiB、`operations-console.zh-CN.json` 51.1 KiB（远低于 2 MiB，单文件）。各命名空间：auth 42、common 42、currency 37、enum 8、gateway 16、i18n-admin 401、nav 188（174 个 `nav.resource.*` + 14 个框架 key），其余 14 个为 0 |
| 守卫报告 | 剩余 273 / 354 个源码文件含中文，约 10,523 处字面量（近似计数，含 JSX 文本段）；`MIGRATED_DIRS`（`main.tsx` `App.tsx` `auth/` `navigation/` `feedback/` `components/` `hooks/` `api/` `i18n/` `localization/` 与 currency 两个文件）为 0，严格执行 |
| 一致性测试 | en/zh-CN key 集合一致、ICU 合法且参数一致、静态引用的 key 均存在、`nav.resource.*` 覆盖全部 componentKey、动作编码与 15 个 Platform 独有编码（13 个目录菜单 + 2 个风控按钮），且无多余 |
| operations-console-bff | 2345 个测试，0 失败 0 错误（`RolePermissionTreeAutoTest` 6、`CommercialPricePreviewAutoTest` 7、`OperationsBrowserBoundaryAutoTest` 252） |
| 本地 Vite 验证 | 18301 已被主工作区另一会话的 Vite 占用（未动它），改用 18311。用 Playwright 拦截 manifest 与公开桶（数据取自本地 en / zh-CN 文件）、BFF 接口返回模拟登录与导航：`?lang=en` 登录页英文且右上角有按钮 → 下拉标题、两种语言、提示文案正确 → 选简体中文后整页刷新到原路由、去掉 `?lang`、`<html lang>`=zh-CN、本地选择已写入 → 断开 manifest 与公开桶后刷新仍为中文且按钮仍在 → 新浏览器无缓存且 manifest 不可用时为内置英文、不显示按钮；登录后页头"环境标记 · 语言按钮 · 账号 · 退出"、侧栏、面包屑、币种页英文 / 中文均正确（12 + 7 项检查全部通过）。截图在 scratchpad `shots/` |

## 4. 与方案 / 任务卡不一致之处

1. **不支持原因的位置**：卡要求"不支持原因改为 `{code, params}`"，实现为同一个 `notes` 数组——未计算时 `notes` 只有这一条（`computed=false` 区分），不另设字段；`code` 用 Java 枚举 `IllustrationNoteCode` 序列化。
2. **未挂资源的权限节点**：`code` 与 `displayName` 都是权限编码；"其他权限"分组 `code`=`displayName`=`OTHER_PERMISSIONS`。资源节点缺 `resourceCode` 时按上游无效响应（502），与已有字段校验一致。
3. **`useLocale` 去掉 `setLocale`**：切换语言统一走 `switchLanguage`（整页刷新），保留运行时的 `setLocale` 方法但页面不再用。
4. **key 写法与开发约定第 10 节冲突**：第 10 节写"全部小写"，方案 3.2 要求小驼峰且 `nav.resource.<CODE>`、枚举值保留大写；本卡按方案执行，原 `i18n-admin`、`enum` 的蛇形 key 不动。第 10 节需在文档阶段同步修改。
5. **过渡显示**：审计详情暂只显示字段 key，价格试算说明暂显示编码与参数，由 M6 / M5 完成翻译（见下）。
6. **导出文件包含 0 key 的命名空间**：首次导入前需在 Platform 先建好这些命名空间（或导入前剔除空命名空间），阶段 7 处理。
7. **验证端口**：用 18311 而非 18301（见上）。

## 5. 给模块代理的注意事项

- 先读 `operations-console-web/docs/i18n-migration-guide.md`，照抄 currency 样板；完成后把目录加进 `MIGRATED_DIRS`。
- `useT` / `translator` 的命名空间写字面量，`t('…')` 的 key 尽量写字面量，否则一致性测试看不到；`testT` 遇缺失 key 直接抛错。
- `ApiError` 兜底消息、`usePaging` 的错误、`DeleteConfirmationModal` 等已是英文：测试里断言这些中文的地方要改。
- `DeleteConfirmationModal` 的 `subject` 由调用方传**已翻译**的小写名词（英文句子形如 `Delete {subject}`、`Deleted {subject}.`）。现调用方（Accounts、Roles、文件等页面）仍传中文，迁移时改。
- 别再给 antd 组件传 `locale={zhCN}`，别再 `dayjs().locale('zh-cn')`；`toLocaleString('zh-CN', …)`（22 处）改 `formatDateTime`。
- 仍是中文、归 M6 的共享件：`configuration/ConfigurationStatusModal.tsx`、`useConfigurationForm.ts`（币种页的启停弹窗和保存冲突提示依赖它们）、`pages/gatewayConfigurationModel.ts`（`GatewayStatusModal` 用到的 `gatewayStatusSavedMessage`、Break-glass 警告）。
- **M6 审计详情**：按 `key` 翻译（建议 `access.audit.field.<key>`，用 `tOr` 回退 key）。原 BFF 中文名供种子逐字使用：CODE 编码、DISPLAY_NAME 名称、DESCRIPTION 说明、STATUS 状态、SECURITY_STATUS 安全状态、roleCode 角色编码、roleName 角色名称、roleId 角色 ID、roleCount 角色数量、permissionId 权限 ID、permissionCount 权限数量、version 版本、status 状态、accountStatus 账号状态、accountVersion 账号版本、memberId 成员 ID、memberStatus 成员状态、memberVersion 成员版本、authEpoch 认证版本。原页面"名称 + 灰色 key"的两行布局可恢复。
- **M6 权限树**：资源节点 `resourceLabel(tNav, { resourceCode: node.code, displayName: node.displayName })`；`OTHER_PERMISSIONS` 分组用 `access.permissionTree.other`（zh 种子"其他权限"）；未挂资源的权限显示编码。`roleEditor.ts` 目前仍用 `displayName` 作标题。
- **M5 价格试算说明**：按 `code` + `params` 翻译，多条之间原分隔符为"；"。原中文（种子逐字）：BAND_HIT「命中第 {bandNo} 档」、BAND_NONE「没有命中的档位」、PROGRESSIVE_BANDS「累进 {count} 档」、MIN_APPLIED「按最低金额 {amount}」、MAX_APPLIED「按最高金额 {amount}」、BASE_DEPENDS_ON_FEE「计算基数 {base} 依赖其他费用结果，未计算」、COMPONENT_UNSUPPORTED「组件类型 {type} 不支持示例计算」、BAND_BASIS_PERIODIC「阶梯基数 {basis} 依赖周期累计量，未计算」、BAND_MODE_UNSUPPORTED「阶梯模式 {mode} 不支持单笔示例计算」；再删掉 `CommercialPriceSimulator.tsx` 末尾的过渡函数 `noteCodes`。
- **M2**：`riskSecurity.test.ts` 的依赖图现在在 `src/i18n/` 处停止（共享运行时只存 `i18n.*` 键）；`riskPageWiring` 改为断言 key。
- **M3**：`workflow/formModel.ts` 没有扫描白名单，提示文案照常迁移，拼音转换若需中文字面量用 `\u` 转义。
- 页面 h1 与菜单中文同名时用 `nav.resource.<CODE>`，以免英文 h1 与面包屑不一致。
- 守卫的字面量数是近似值（模板串整体算一处），只看趋势；严格判定是"去掉注释后有无汉字"。
