# M6 报告：access / gateway / client-review / card 文案迁移

执行日期 2026-10-23 · 研发目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n-m6`（基于 F0 成果），未提交、未推送，主工作区只写本报告。未调用 Superpowers skill，未派生子代理。

## 1. 迁移文件（`operations-console-web/src/` 下）

| 命名空间 | 文件 |
|---|---|
| `access`（412 key） | `tenantAccess/TenantAccessView.tsx`、`tenantAccess/TenantMemberAccess.tsx`、`tenantAccess/tenantAccess.ts`、`pages/TenantAccess.tsx`、`pages/Accounts.tsx`、`pages/Roles.tsx`、`pages/roleEditor.ts`(+test)、`pages/Permissions.tsx`、`pages/Audit.tsx`、`pages/auditView.ts`(+test) |
| `gateway`（247 key，含 F0 的 16 个 `statusModal.*`，含义未改） | `configuration/ConfigurationStatusModal.tsx`、`configuration/useConfigurationForm.ts`、`configuration/PermissionSelect.tsx`、`configuration/GatewayEditor.tsx`、`configuration/gatewayEditorModel.ts`、`configuration/configurationModel.ts`(+test)、`pages/GatewayConfiguration.tsx`、`pages/gatewayConfigurationModel.ts`(+`gatewayConfiguration.test.ts`)、`pages/ConsoleResources.tsx`、`pages/ApiCredentials.tsx`、`pages/ApiScopes.tsx`、`clientApi/clientApi.ts`、`clientApi/ScopeSelect.tsx` |
| `client-review`（163 key） | `clientReview/clientReview.ts`(+test)、`clientReview/ClientReviewDetail.tsx`、`clientReview/useReviewCommand.ts`、`pages/ClientOnboardingReview.tsx` |
| `card`（141 key） | `card/binLibrary.ts`(+test)、`card/BinLibraryModals.tsx`、`card/directUpload.ts`、`card/useBinLibraryCommand.ts`、`pages/CardBinLibrary.tsx` |

`pages/accountCommand.ts`、`accountDeletion.ts`、`roleDeletion.ts`、`configuration/types.ts` 只有注释中文，无需改动，已一并纳入守卫。

要点：
- **审计详情字段名**：`access.audit.field.<key>`，用 `tOr` 回退原 key；种子逐字取 F0 报告给出的 BFF 原中文（19 个）。恢复"名称 + 灰色 key"两行，但只在有翻译时显示第二行（未知 key 只显示一次）。审计动作 / 对象类型 / 结果改为 `access.enum.audit{Action,ResourceType,Result}.<CODE>`，代码清单 `auditActionCodes` 等保留原顺序。
- **角色权限树**：`roleEditor.ts` 新增 `permissionNodeTitle`：`OTHER_PERMISSIONS` → `access.permissionTree.other`；其余走 `resourceLabel(tNav, { resourceCode: code, displayName })`（资源节点取 `nav.resource.*`，未挂资源的权限节点 `code`=`displayName`=权限编码，自然显示编码）。解码同时要求 `code` 为非空字符串。
- **configuration 共享件**：`ConfigurationStatusModal` 标题 / 确认按钮复用 F0 的 `gateway.statusModal.{enable,disable}Title`、`confirm{Enable,Disable}`（zh 与原拼接结果逐字一致，如"停用币种""确认停用"），失败标题另建 `configStatus.*`（原句序"停用{subject}失败"与 statusModal 不同）。币种页启停弹窗与保存提示因此已英文化。
- `TenantAccessView` 的 `date` / `QueryFailure` / `useDetail` 被 commercial、channel、dataQuery、clientReview 共用，文案取自 `access` 命名空间（未改其他模块文件）。
- 顶层标签映射全部改为代码数组 + `enumLabel`（如 `ownerStatuses`、`credentialStatuses`、`caseStatuses`、`returnSections`）；`gatewayStatusSavedMessage`、`activationConflictMessage` 改为调用时取值的函数。
- `card/useBinLibraryCommand.ts` 中与 BFF 返回值逐字比较的"业务校验未通过"是服务端数据，按指南写成 `\u` 转义并注明原因。
- 页面 h1 与菜单同名的用 `nav.resource.*`：人员管理、角色管理、权限目录、审计日志、租户访问、API 路由管理、API 凭证、API Scope、客户入驻审核、卡 BIN 库。"菜单资源"（ConsoleResources）与菜单名"菜单与按钮"不同，保留本模块 key。

## 2. 范围外改动

1. `components/GatewayStatusModal.tsx`（F0 文件）一行：`gatewayStatusSavedMessage` → `gatewayStatusSavedMessage()`（常量改为调用时取值的函数所必需）。
2. `fileRecords/fileRecords.test.ts`（M7 文件）：import 行与最后一个用例（"审计中文名…"）改为断言 `auditActionCodes` / `access.enum.auditResourceType.*` 英文基准；已用 SendMessage 通知 i18n-M7 不要改这几行，合并时注意。

## 3. 自检

| 项 | 结果 |
|---|---|
| `npm test` | 1479 个，全部通过（F0 为 1475；新增审计标签、权限树标题、client-review 标签 / 校验、configuration 资源类型 4 个用例，其余为改写断言，未删测试） |
| `npx tsc --noEmit` | 通过 |
| `npx vite build` | 通过（仅既有大 chunk 提示） |
| `node scripts/i18n-export-source.mjs` | 成功：21 命名空间、1681 key；access 412、gateway 247、client-review 163、card 141 |
| 守卫 | M6 目录 / 文件已加入 `MIGRATED_DIRS`，范围内去注释后汉字数 0；全局剩余 242 / 354 个文件 |
| zh 种子核对 | 脚本把四个命名空间的 zh 种子按占位符切片后与 HEAD 源码比对：除 F0 给出的审计字段名与原拼接句（如"新增API 路由""确认通过已提交""{time} 由 {x} 导入 · 校验失败 {n} 行"）外全部逐字命中，拼接句已人工核对 |

未在浏览器里实际打开页面验证（只做了测试、类型检查与构建）。

## 4. 拿不准的英文用词（请主会话定）

| 中文 | 现用英文 | 说明 |
|---|---|---|
| 正常（租户来源业务状态 / 安全状态） | Active | 直译 Normal 不自然 |
| 已暂停（租户安全状态 SUSPENDED、IAM 状态） | Suspended | 术语表"已暂停=Paused"，但代码与菜单按钮"安全暂停与恢复"已用 Suspend |
| 已停用（租户来源业务状态 SUSPENDED） | Disabled | 按术语表；与代码值 SUSPENDED 不一致 |
| 来源业务状态 | Source Business Status | |
| 操作记录（Tab） | Activity | |
| 关联员工数 | Users | 列表头，提示"Includes disabled accounts" |
| 号段 / 号段数 | BIN range(s) / BIN Ranges | 数量句用"BIN ranges: {count}"，因参数是已格式化字符串无法 ICU 复数 |
| 回切 | Roll Back | |
| 可激活 / 生效中 / 校验失败（BIN 版本状态） | Ready to Activate / Active / Validation Failed | |
| 借贷记 | Funding Type | |
| 请求幂等 NONE / REQUIRED | Off / Required | |
| 服务发现名 | Discovery Service Name | |
| 安全策略（Security Profile 编码） | Security Profile | |
| 页面菜单 / 目录 | Page Menu / Directory | |
| 审核任务 / 结束时间 | Review Case / Closed At | |
| 申请主体 / 受益所有人 | Applicant / Ultimate Beneficial Owner | |
| 认证版本（authEpoch） | Auth Epoch | |
| 凭证{状态}。（授权列表提示） | "Credential {status}. Each grant below…" | 状态词首字母大写，读起来略生硬 |

## 5. 与指南不一致之处

1. **片段拼接**：`card.summary.fileSuffix`（" · 文件 {fileName}"）接在 `summary.activatedBy` 之后，沿用原页面两段拼接结构；另有几个只含标点的分隔符 key（`access.tenant.reasonSeparator`、`access.accounts.roleSeparator`、`client-review.application.listSeparator`、`client-review.classification.issueSeparator`、`card.command.fieldErrorSeparator`），英文为 "; " 或 ", "。
2. **校验提示无句号**：`client-review.classification.issue.*` 英文不带句号，因为它们在页面上以 "; " 连成一行。
3. **审计字段两行布局**：只在字段有翻译时显示灰色 key（原 BFF 时期始终两行）。
4. `VersionDetail` 调 `useResource` 时显式传入 `card.detail.loadFailed`，不再依赖 dataQuery（M7）中仍为中文的默认标题。
