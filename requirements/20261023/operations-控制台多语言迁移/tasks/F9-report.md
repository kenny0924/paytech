# F9 报告：集成收尾

执行日期 2026-10-23 · 集成目录 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n`（未提交、未推送）；主工作区只写本报告与截图 `assets/local-en/`。mor-test 只读（遍历脚本拦截了登录 / 刷新以外的全部非 GET 请求，实际拦截 0 次）。未调用 Superpowers skill，未派生子代理。

## 1. 守卫强制

- `src/i18nSourceGuard.test.ts`：删除 `STRICT` 与 `MIGRATED_DIRS`，全部 `src` 生产源码（注释与测试除外）严格执行，白名单只有 `navigation/pageCapabilities.ts`。结果：**0 / 356 个文件含中文**。
- `src/i18nSource.ts` 的 CJK 正则改为 `\u` 转义写法，扫描器不再需要排除它本身；新增断言 emoji 不算中文（M4 报告的"emoji 被当成中文"在 `u` 模式下其实不成立，`lockMark` 保持现状即可）。
- 无主测试 `pages/pageHeading.test.ts` 的中文断言消息改为英文。其余测试中的中文是测试名称或 Platform / 业务数据（菜单 `displayName`、角色名等），不属于界面断言，保留。
- `docs/i18n-migration-guide.md` 同步：去掉 `MIGRATED_DIRS` 说明，写明新文案先加 en key 与 zh-CN 种子。

## 2. 英文用词统一（174 个 en 值变更，另删 1 个 key、增 1 个 key）

按 `tasks/english-decisions.md` 中标"F9 修改"的项：
- **驳回 → Reject / Rejected**：finance 13、recon 4、channel 6、commercial 6、workflow 2（含 "Return Version""Couldn't return the version""returned" 等变体，按句意改写为 reject）；"退回修改"仍为 Return for Changes。
- **内容摘要 → Content Hash**：risk 5 个 key（含"Content digest {hash}"两条 meta 句、Handler Digest → Handler Hash）；card "文件摘要"也随 recon 统一为 File Hash。
- **风控编辑器面包屑**：删除 `risk.ruleEditor.breadcrumb`，`RuleSetEditor` 改用 `nav.resource.OPERATIONS_RISK_RULE_CONFIGURATION`（Rules）。
- **句中"你"**：`workflow.people.you` 改为 `you`（"Waiting on you, 陈雪"）；活动记录行首另建 `workflow.people.youLead`（en You / zh 你），同步两处测试。
- **对账详情**：`recon.detail.notFound` 改为 "No matching {object} found."；6 个 `recon.*.object` 改为 Title Case（它们同时用作无数据时的 h1 / 面包屑）。
- **标签首字母大写**：通知路由角色 Primary / Fallback；数据查询 Expanded Validation、⟨Fragment⟩、"{marker} Fragment"、"Fixed Fragment"。

全量扫描（按 zh-CN 种子反查同一中文的不同英文，及同一英文短语的大小写差异）后的统一：
- 失败提示统一为多数写法 "Couldn't …"（原 "Failed to …" 66 条，集中在 data-query、file、notification、webhook、vault；另 "Save failed""Activation failed""Roll back failed"）。
- 同一标签的大小写统一为 Title Case：Add Rule、All Met / Any Met、More Actions、Close Properties Panel、Copy Full Hash、Delete Condition、Fixed Value、In Progress、Member Status、Not Applicable、Not Reached、Not Started、Not Submitted、Numeric Unit、Pending Validation / Confirmation、Read-Only、Replacement in Progress、Time Range、Validation Failed、Write Rules、Check Passed、Interface Language、Condition Field；筛选项 "All statuses / All types" 统一为句式小写（多数写法）。
- 其他：Config Version → Configuration Version；Condition pending → Condition not set；Not filled in → Not provided；nav "Run Static Validation" → Run Static Checks（与按钮一致）；`finance.journal.field.accountPosting` 原文 "account Batch" 改为 Account Posting Batch；en 中全角"＋"改半角"+"（12 条）。
- **保留不统一（语境不同）**：动词 / 状态（Enable / Enabled 等）、单复数（菜单复数、字段单数）、主体 Subject（风控）/ Entity（财务、对账）、流程 Process（工作流）/ Flow（渠道协议、风控决策流）、Reserve / Security Deposit、Grant Status / Authorization Status、Keys（存储）/ Credentials（通知）、`workflow.enum.operator.*` 小写短语、句中小写主语（`*.subject.*`、`missingConfiguration.*`）、行内原因（`risk.enum.evaluationFailure.*`、`webhook.failure.TIMEOUT` 用在括号或"·"后，曾改为 Title Case，测试显示是行内片段后已复原）；`i18n-admin`、`enum` 两个已上线命名空间未动。
- 未发现拼音残留；"Provider" 只出现在通知发送服务商（非支付渠道）语境，保留。
- **glossary.md 未回写**（主工作区文件，按边界未改）；需要主会话把裁决表合入术语表。

## 3. richText 移入 `src/i18n/`

两者是同一需求（整句 key + 节点作命名参数），M4 的 `richText` 更通用。新建 `src/i18n/richText.ts`：`richText(t, key, nodes, params)` 与 `code(text)`（等宽片段），并加 2 个测试；channel / externalApi 的 10 个调用文件改为从 `../i18n/richText` 引入，删除 `ChannelParts.richText`；data-query 的 `mono` / `MonoText` / `slot` / `SlotText` 删除，`DeclarationPanel`（2 处）与 `TrialPanel`（页码输入框）改用 `richText`。迁移指南 4.4 补一条用法。

## 4. nav 中文种子与 Platform 名称

对照 `tasks/ops-resources-mor-test.tsv`（172 个资源）：172 个 `nav.resource.<CODE>` 的 zh-CN 与 Platform `display_name` **全部一致，0 处改动**（"使用关系"所在的 `OPERATIONS_RISK_RULE_SET_REFERENCES` 种子已是"引用关系"，"菜单资源"所在的 `OPERATIONS_CONSOLE_RESOURCES` 种子已是"菜单与按钮"）。唯一不在 Platform 的是 componentKey `OPERATIONS_CLIENT_ONBOARDING_REVIEW`（Platform 用 `CLIENT_ONBOARDING_REVIEW`），保持 F0 处理。

## 5. 英文布局浏览器检查

- 方式：本地 Vite 18311（未动 18301）+ Playwright，`/api/operations/**` 中继到 `http://localhost:28080`（Origin 改写）；manifest 返回 503 以使用内置英文；页面间用前端路由跳转（整页 reload 会触发 Gateway `GATEWAY_RATE_LIMITED`，首次运行因此作废）。
- 覆盖：**70 个菜单页 × 1440 与 1280 两种宽度**全部截图（`assets/local-en/w1440-NN-*.png`、`w1280-NN-*.png`）；**34 个页面**打开了主要弹窗 / 抽屉并截图（`*--modal.png`），随后取消。每页自动检测横向溢出、按钮折行 / 溢出、表头溢出或超高、标签裁切、可见中文。
- 已修正：
  1. 侧栏子菜单标题与箭头重叠（"Data Protection (Vault)"）：`styles.css` 给 `.ant-menu-submenu-title > span[title]` 加省略号，并保留完整 title 提示。
  2. 渠道列表表头 "Call Authorizations" 断成 "Authorization / s"：`ChannelChannels.tsx` 该列宽 100 → 140。
- 可见中文：只有业务数据（角色名、渠道名、资源 display_name、录入名称等），未发现残留界面中文。
- 控制台：只有人为制造的 manifest 503，以及 antd 弃用警告（`Spin tip`、`Space direction`、`Modal maskClosable`、未挂载 `useForm`），都是迁移前就有的。
- 预期报错页（mor-test BFF 是旧合同）：角色权限树、审计详情字段名、定价试算说明。本次遍历只检查列表页，没有进入这三个详情，接口也没有返回 4xx/5xx；需要在新 BFF 上线后复核。

## 6. 全量自检（最终状态）

| 项 | 结果 |
|---|---|
| `npm test` | 1481 个，全部通过（新增 richText 2 个；守卫测试由 3 个合并为 2 个） |
| `npx tsc --noEmit` | 通过 |
| `npx vite build` | 通过（只有既有的大 chunk 提示） |
| `node scripts/i18n-export-source.mjs` | 21 个命名空间，**8513 key**：access 412、auth 42、card 141、channel 1157、client-review 163、commercial 785、common 42、currency 37、data-query 372、enum 8、file 403、finance 834、gateway 247、i18n-admin 401、nav 188、notification 278、recon 541、risk 1132、vault 194、webhook 147、workflow 989；`operations-console.en.json` 646.0 KiB、`operations-console.zh-CN.json` 599.8 KiB（单文件，低于 2 MiB） |
| operations-console-bff | 2345 个测试，0 失败，0 错误（`../mvnw -o test`，使用私有仓库参数） |

## 7. 文档（集成目录）

- `agent-docs/decisions/2026-10-22-ui-text-localization.md`：Supersedes 行、第 8 条与已知限制标注被取代；文末新增"2026-10-23 补充：operations-console-web 全量接入"（全量接入与 21 个命名空间、默认英文与切换、本地缓存、菜单与权限名、BFF 改编码、禁中文守卫与新文案流程）。
- `agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 10 节重写：接入范围；`useT` / `translator` / `richText`；key 改为小驼峰段名（枚举值与资源编码保留原大小写，已发布的蛇形 key 不改）；英文基准 + zh-CN 种子、新文案先加 en key 与种子再导入发布；ICU 与 `Intl`；枚举与 `nav.resource`；默认英文、选择顺序、整页刷新、本地缓存；全 `src` 禁中文守卫与唯一白名单、测试断言 key 或英文。
- `requirements-site` 在集成目录没有 `node_modules`，`npm --prefix requirements-site test` 未运行。

## 8. 未完成 / 待办

| 项 | 页面 / 宽度 | 截图 | 说明 |
|---|---|---|---|
| 筛选栏横向溢出 61 px | 文件记录 `/file-records` · 1440（1280 未检出） | `w1440-17-file-records.png` | 筛选行最后的 Reset 按钮超出内容区；"Replacement in Progress"复选框标签折成两行。需要调整筛选栏换行或缩短英文 |
| 页头按钮区横向溢出 67 px | 清算账户 `/finance/reconciliation/clearing-accounts` · 1440 | `w1440-41-finance-reconciliation-clearing-accounts.png` | 检测到 "New Clearing Account" 按钮超出视口，未人工复核 |
| 弹窗按钮多行（判为正常） | 动作目录新建弹窗 · 两种宽度 | `w1440-65-workflow-action-templates--modal.png` | 动作类型卡片按设计就是标题 + 说明两行；如需复核请看截图 |
| 人工逐张复核 | 140 张页面截图 + 70 张弹窗截图 | `assets/local-en/` | 自动检测只覆盖溢出、折行与表头；文字截断、对齐类问题没有逐张人工看图 |
| 弹窗覆盖不全 | 36 个页面 | — | 页面上没有"新建 / 添加 / 导入"类按钮，脚本没有打开弹窗或抽屉（例如详情抽屉、行内操作）；需要补点 |
| 三处依赖新 BFF 合同的页面 | 角色编辑权限树、审计详情、定价试算 | — | 本次没有进入详情；新 BFF 上线后复核 |
| zh-CN 抽查 | 计划 12 页 | — | 脚本已写好（`scratchpad/browse/zh_walk.py`：模拟 manifest，按命名空间拆分 `i18n-out/operations-console.zh-CN.json` 作语言包），因收尾时间不足**未运行** |
| glossary.md 回写 | — | — | 第 2 节的裁决与统一结果需主会话合入术语表 |

## 附录：en 值变更表（F9 前 → F9 后）

| key | zh-CN | 原英文 | 新英文 |
|---|---|---|---|
| `auth.language.title` | 界面语言 | Interface language | Interface Language |
| `card.activation.activateFailed` | 激活失败 | Activation failed | Couldn't activate |
| `card.activation.rollbackFailed` | 回切失败 | Roll back failed | Couldn't roll back |
| `card.detail.hash` | 文件摘要 | File Digest | File Hash |
| `channel.channels.configVersion` | 配置版本 | Config Version | Configuration Version |
| `channel.cost.createPlanFailed` | 方案创建失败 | Couldn't create plan | Couldn't create the plan |
| `channel.cost.lifecycleNote` | 状态流转：DRAFT → 提交 PENDING_APPROVAL → 批准（到期即 ACTIVE，否则 APPROVED 等待到期 Job 切换）/ 驳回 REJECTED。只有未到生效时间的 APPROVED 版本可「作废」为 INACTIVE；ACTIVE 版本只能被新版本替换。批准人不能是编制人，批准时确认内容摘要。 | Lifecycle: DRAFT → submit → PENDING_APPROVAL → approve (ACTIVE if already due, otherwise APPROVED until the scheduled job switches it) / return → REJECTED. Only APPROVED versions not yet effective can be voided to INACTIVE; an ACTIVE version can only be replaced by a new version. The approver can't be the maker and confirms the content hash when approving. | Lifecycle: DRAFT → submit → PENDING_APPROVAL → approve (ACTIVE if already due, otherwise APPROVED until the scheduled job switches it) / reject → REJECTED. Only APPROVED versions not yet effective can be voided to INACTIVE; an ACTIVE version can only be replaced by a new version. The approver can't be the maker and confirms the content hash when approving. |
| `channel.cost.reject` | 驳回 | Return | Reject |
| `channel.cost.rejectFailed` | 驳回失败 | Couldn't return | Couldn't reject the version |
| `channel.cost.rejectTitle` | 驳回版本 v{version} · {planCode} | Return Version v{version} · {planCode} | Reject Version v{version} · {planCode} |
| `channel.cost.reviewNote` | 批准人不能是编制人。生效时间已到时批准即切换为当前版本，否则进入 APPROVED 等待到期切换；驳回后版本为 REJECTED。 | The approver can't be the maker. If the effective time has passed, approval makes it the current version immediately; otherwise it waits as APPROVED until due. A returned version becomes REJECTED. | The approver can't be the maker. If the effective time has passed, approval makes it the current version immediately; otherwise it waits as APPROVED until due. A rejected version becomes REJECTED. |
| `channel.costEditor.createFailed` | 版本创建失败 | Couldn't create version | Couldn't create the version |
| `channel.enum.access.READ_ONLY` | 只读 | Read-only | Read-Only |
| `channel.enum.costVersionStatus.REJECTED` | 已驳回 | Returned | Rejected |
| `channel.field.source.fixed` | 固定值 | Fixed value | Fixed Value |
| `channel.flow.edge.conditionPending` | 条件待填 | Condition pending | Condition not set |
| `channel.load.failed` | 加载失败 | Failed to load | Couldn't load |
| `channel.overview.notApplicable` | 不适用 | Not applicable | Not Applicable |
| `channel.protocol.more` | 更多操作 | More actions | More Actions |
| `channel.save.failed` | 配置保存失败 | Couldn't save configuration | Couldn't save the configuration |
| `client-review.application.empty` | 未填写 | Not filled in | Not provided |
| `client-review.materials.notSubmitted` | 未提交 | Not submitted | Not Submitted |
| `commercial.catalog.mode.readOnly` | 只读 | Read-only | Read-Only |
| `commercial.version.action.reject` | 驳回 | Return | Reject |
| `commercial.versionTransition.reject.body` | 驳回后版本回到草稿，可继续编辑价格再提交。 | Once returned, the version goes back to draft, and prices can be edited and resubmitted. | Once rejected, the version goes back to draft, and prices can be edited and resubmitted. |
| `commercial.versionTransition.reject.failed` | 驳回版本失败 | Couldn't return the version | Couldn't reject the version |
| `commercial.versionTransition.reject.ok` | 驳回 | Return | Reject |
| `commercial.versionTransition.reject.title` | 驳回版本 | Return Version | Reject Version |
| `commercial.workbench.note` | 批准 / 驳回需要价目审批权限，且审批人不能是提交人（commercial 校验）。驳回后版本回到草稿；commercial 驳回接口目前不接收原因。 | Approving or returning requires the term approval permission, and the approver can't be the submitter (checked by commercial). A returned version goes back to draft; commercial's return API doesn't accept a reason yet. | Approving or rejecting requires the term approval permission, and the approver can't be the submitter (checked by commercial). A rejected version goes back to draft; commercial's reject API doesn't accept a reason yet. |
| `common.list.loadFailedTitle` | 列表加载失败 | Failed to load list | Couldn't load the list |
| `common.timeRange.label` | 时间范围 | Time range | Time Range |
| `data-query.columns.add` | ＋ 添加列 | ＋ Add Column | + Add Column |
| `data-query.dataSources.create` | ＋ 新建数据源 | ＋ New Data Source | + New Data Source |
| `data-query.dataSources.delete.failed` | 删除数据源失败 | Failed to delete data source | Couldn't delete data source |
| `data-query.dataSources.referencesLoadFailed` | 引用模板加载失败 | Failed to load referencing templates | Couldn't load referencing templates |
| `data-query.dataSources.status.disableFailed` | 停用数据源失败 | Failed to disable data source | Couldn't disable data source |
| `data-query.dataSources.status.enableFailed` | 启用数据源失败 | Failed to enable data source | Couldn't enable data source |
| `data-query.editor.addParam` | ＋ 添加 | ＋ Add | + Add |
| `data-query.editor.createFailed` | 数据源创建失败 | Failed to create data source | Couldn't create data source |
| `data-query.editor.saveFailed` | 数据源保存失败 | Failed to save data source | Couldn't save data source |
| `data-query.parameters.add` | ＋ 添加参数 | ＋ Add Parameter | + Add Parameter |
| `data-query.parameters.addChoice` | ＋ 添加取值 | ＋ Add Choice | + Add Choice |
| `data-query.parameters.addField` | ＋ 添加字段 | ＋ Add Field | + Add Field |
| `data-query.parameters.choicesTitle` | 取值 → 固定片段（不能含 {markers} 或注释） | Choice → Fixed fragment (can't contain {markers} or comments) | Choice → Fixed Fragment (can't contain {markers} or comments) |
| `data-query.parts.dataSourcesLoadFailed` | 数据源列表加载失败 | Failed to load data sources | Couldn't load data sources |
| `data-query.parts.detailLoadFailed` | 详情加载失败 | Failed to load details | Couldn't load details |
| `data-query.problems.expansion` | 展开校验 | Expanded validation | Expanded Validation |
| `data-query.restore.failed` | 恢复版本失败 | Failed to restore version | Couldn't restore version |
| `data-query.summary.fragmentSuffix` | ⟨片段⟩ | ⟨fragment⟩ | ⟨Fragment⟩ |
| `data-query.template.createFailed` | 模板创建失败 | Failed to create template | Couldn't create template |
| `data-query.template.legend.fragment` | {marker} 片段 | {marker} fragment | {marker} Fragment |
| `data-query.template.loadFailed` | 模板加载失败 | Failed to load template | Couldn't load template |
| `data-query.template.saveFailed` | 模板保存失败 | Failed to save template | Couldn't save template |
| `data-query.templateStatus.disableFailed` | 停用模板失败 | Failed to disable template | Couldn't disable template |
| `data-query.templateStatus.enableFailed` | 启用模板失败 | Failed to enable template | Couldn't enable template |
| `data-query.templates.create` | ＋ 新建模板 | ＋ New Template | + New Template |
| `data-query.versions.loadFailed` | 版本加载失败 | Failed to load version | Couldn't load version |
| `file.bucket.registerFailed` | 登记桶失败 | Failed to register bucket | Couldn't register bucket |
| `file.bucket.urlSaveFailed` | 保存对外地址失败 | Failed to save public URL | Couldn't save public URL |
| `file.buckets.disableFailed` | 停用桶 {name}失败 | Failed to disable bucket {name} | Couldn't disable bucket {name} |
| `file.buckets.enableFailed` | 启用桶 {name}失败 | Failed to enable bucket {name} | Couldn't enable bucket {name} |
| `file.buckets.notes.routeTitle` | 写入规则 | Write rules | Write Rules |
| `file.buckets.readOnlyFailed` | 只读桶 {name}失败 | Failed to set bucket {name} read-only | Couldn't set bucket {name} read-only |
| `file.buckets.register` | ＋ 登记桶 | ＋ Register Bucket | + Register Bucket |
| `file.credential.failed` | 更换密钥失败 | Failed to replace keys | Couldn't replace keys |
| `file.detail.loadFailed` | 文件详情加载失败 | Failed to load file details | Couldn't load file details |
| `file.enum.checkResult.OK` | 检查通过 | Check passed | Check Passed |
| `file.instance.createFailed` | 新建实例失败 | Failed to create instance | Couldn't create instance |
| `file.instance.saveFailed` | 保存实例失败 | Failed to save instance | Couldn't save instance |
| `file.instances.create` | ＋ 新建实例 | ＋ New Instance | + New Instance |
| `file.instances.disableFailed` | 停用存储实例 {code}失败 | Failed to disable storage instance {code} | Couldn't disable storage instance {code} |
| `file.instances.enableFailed` | 启用存储实例 {code}失败 | Failed to enable storage instance {code} | Couldn't enable storage instance {code} |
| `file.policy.activateFailed` | 激活 {code} r{revisionNo}失败 | Failed to activate {code} r{revisionNo} | Couldn't activate {code} r{revisionNo} |
| `file.policy.create` | ＋ 新建策略 | ＋ New Policy | + New Policy |
| `file.policy.createFailed` | 新建策略失败 | Failed to create policy | Couldn't create policy |
| `file.policy.detailLoadFailed` | 策略详情加载失败 | Failed to load policy details | Couldn't load policy details |
| `file.policy.discardFailed` | 废弃草稿 r{revisionNo}失败 | Failed to discard draft r{revisionNo} | Couldn't discard draft r{revisionNo} |
| `file.policy.loadFailed` | 检查策略加载失败 | Failed to load scan policies | Couldn't load scan policies |
| `file.policy.newRevisionFailed` | 新建修订 · {code}失败 | Failed to create revision · {code} | Couldn't create revision · {code} |
| `file.policy.retireFailed` | 停用 {code}失败 | Failed to retire {code} | Couldn't retire {code} |
| `file.policy.saveDraftFailed` | 保存草稿失败 | Failed to save draft | Couldn't save draft |
| `file.records.field.hasPendingReplacement` | 有替换进行中 | Replacement in progress | Replacement in Progress |
| `file.route.createFailed` | 新建写入规则失败 | Failed to create write rule | Couldn't create write rule |
| `file.route.deleteFailed` | 删除写入规则失败 | Failed to delete write rule | Couldn't delete write rule |
| `file.route.saveFailed` | 保存写入规则失败 | Failed to save write rule | Couldn't save write rule |
| `file.routes.configure` | ＋ 配置 | ＋ Configure | + Configure |
| `file.routes.create` | ＋ 新建规则 | ＋ New Rule | + New Rule |
| `file.storage.loadFailed` | 存储配置加载失败 | Failed to load storage configuration | Couldn't load storage configuration |
| `finance.adjustments.confirmReject` | 驳回后不生成分录，制单人需重新提交。确认驳回？ | Returning creates no journal entry, and the maker must resubmit. Return? | Rejecting creates no journal entry, and the maker must resubmit. Reject? |
| `finance.adjustments.rejectFailed` | 驳回人工调整失败 | Couldn't return the manual adjustment | Couldn't reject the manual adjustment |
| `finance.adjustments.rejected` | 已驳回，不生成分录 | Returned; no journal entry is created | Rejected; no journal entry is created |
| `finance.enum.adjustmentStatus.REJECTED` | 已驳回 | Returned | Rejected |
| `finance.enum.approvalDecision.REJECTED` | 驳回 | Returned | Rejected |
| `finance.enum.periodCloseStatus.REJECTED` | 已驳回 | Returned | Rejected |
| `finance.enum.ruleVersionStatus.REJECTED` | 已驳回 | Returned | Rejected |
| `finance.journal.field.accountPosting` | account 批次 | account Batch | Account Posting Batch |
| `finance.periodClose.confirmRejectClose` | 驳回后期间回到开放，不保留结转分录。确认驳回？ | Returning sends the period back to open and discards the closing entries. Return? | Rejecting sends the period back to open and discards the closing entries. Reject? |
| `finance.periodClose.confirmRejectReopen` | 驳回后期间保持已关账。确认驳回？ | Returning keeps the period closed. Return? | Rejecting keeps the period closed. Reject? |
| `finance.periodClose.rejectFailed` | 驳回关账失败 | Couldn't return the close | Couldn't reject the close |
| `finance.periodClose.rejected` | 已驳回 | Returned | Rejected |
| `finance.rules.action.reject.confirm` | 驳回后制单人可重新新建版本。确认驳回？ | After it's returned, the maker can create a new version. Return? | After it's rejected, the maker can create a new version. Reject? |
| `finance.rules.action.reject.done` | 已驳回 | Returned | Rejected |
| `finance.rules.action.reject.failure` | 驳回规则版本失败 | Couldn't return the rule version | Couldn't reject the rule version |
| `finance.rules.action.reject.label` | 驳回 | Return | Reject |
| `finance.workflow.copyHash` | 复制完整摘要 | Copy full hash | Copy Full Hash |
| `finance.workflow.reject` | 驳回 | Return | Reject |
| `gateway.scopeSelect.failed` | 加载失败 | Failed to load | Couldn't load |
| `nav.resource.OPERATIONS_RISK_TEST_CANDIDATE_VALIDATE` | 执行静态校验 | Run Static Validation | Run Static Checks |
| `notification.credential.saveFailed` | 保存密钥失败 | Failed to save credentials | Couldn't save credentials |
| `notification.enum.routeRole.FALLBACK` | 备 | fallback | Fallback |
| `notification.enum.routeRole.PRIMARY` | 主 | primary | Primary |
| `notification.providerForm.createFailed` | 新建账号失败 | Failed to create account | Couldn't create account |
| `notification.providerForm.saveFailed` | 保存账号失败 | Failed to save account | Couldn't save account |
| `notification.records.detail.loadFailed` | 记录详情加载失败 | Failed to load record details | Couldn't load record details |
| `notification.statusChange.disableFailed` | 停用失败 | Failed to disable | Couldn't disable |
| `notification.statusChange.enableFailed` | 启用失败 | Failed to enable | Couldn't enable |
| `notification.templates.content.saveFailed` | 保存内容失败 | Failed to save content | Couldn't save content |
| `notification.templates.createModal.failed` | 新建模板失败 | Failed to create template | Couldn't create template |
| `notification.templates.detail.loadFailed` | 模板加载失败 | Failed to load template | Couldn't load template |
| `notification.templates.route.providersLoadFailed` | 发送账号加载失败 | Failed to load sender accounts | Couldn't load sender accounts |
| `notification.templates.route.saveFailed` | 保存路由失败 | Failed to save route | Couldn't save route |
| `recon.bankLine.object` | 银行流水 | bank statement line | Bank Statement Line |
| `recon.bankStatement.object` | 银行对账单 | bank statement | Bank Statement |
| `recon.cash.object` | 银行账户 | bank account | Bank Account |
| `recon.clearing.object` | 清算账户 | clearing account | Clearing Account |
| `recon.detail.notFound` | 未找到该{object} | {object} not found | No matching {object} found. |
| `recon.enum.resolutionStatus.REJECTED` | 已驳回 | Returned | Rejected |
| `recon.exception.confirmReject` | 驳回后异常回到待处置。确认？ | Returning sends the exception back to pending resolution. Continue? | Rejecting sends the exception back to pending resolution. Continue? |
| `recon.exception.object` | 对账异常 | reconciliation exception | Reconciliation Exception |
| `recon.exception.reject` | 驳回 | Return | Reject |
| `recon.exception.rejected` | 已驳回，异常回到待处置 | Returned; the exception is back to pending resolution | Rejected; the exception is back to pending resolution |
| `recon.import.notPassed` | 未通过校验 | Validation failed | Validation Failed |
| `recon.statement.object` | 渠道账单 | channel statement | Channel Statement |
| `risk.candidate.field.contentHash` | 内容摘要 | Content Digest | Content Hash |
| `risk.candidate.field.pointHash` | 决策点内容摘要 | Decision Point Content Digest | Decision Point Content Hash |
| `risk.candidate.field.ruleSetHash` | 规则集内容摘要 | Rule Set Content Digest | Rule Set Content Hash |
| `risk.candidateList.allStatuses` | 全部状态 | All Statuses | All statuses |
| `risk.conditionBuilder.allTitle` | 满足全部 | All met | All Met |
| `risk.conditionBuilder.anyTitle` | 满足任一 | Any met | Any Met |
| `risk.conditionBuilder.delete` | 删除条件 | Delete condition | Delete Condition |
| `risk.conditionBuilder.field` | 条件字段 | Condition field | Condition Field |
| `risk.enum.staticCheck.HANDLER_HASH` | 处理器摘要 | Handler Digest | Handler Hash |
| `risk.flowPanel.close` | 关闭属性面板 | Close properties panel | Close Properties Panel |
| `risk.listPage.memberStatus` | 成员状态 | Member status | Member Status |
| `risk.releasePage.activeMeta` | {point} · {environment} · 激活人 {actor} · {time} · 内容摘要 {hash} | {point} · {environment} · Activated by {actor} · {time} · Content digest {hash} | {point} · {environment} · Activated by {actor} · {time} · Content hash {hash} |
| `risk.releasePage.detailMeta` | {point} · {environment} · 决策流版本 {flowVersionId} · 内容摘要 {hash} | {point} · {environment} · Decision flow version {flowVersionId} · Content digest {hash} | {point} · {environment} · Decision flow version {flowVersionId} · Content hash {hash} |
| `risk.releasePage.step.notStarted` | 待发起 | Not started | Not Started |
| `risk.releasePage.step.pendingConfirmation` | 待确认 | Pending confirmation | Pending Confirmation |
| `risk.releasePage.step.pendingValidation` | 待校验 | Pending validation | Pending Validation |
| `risk.ruleEditor.addRule` | 添加规则 | Add rule | Add Rule |
| `risk.ruleEditor.breadcrumb` |  | Rule Configuration | (removed) |
| `risk.variableControl.numericUnit` | 数值单位 | Numeric unit | Numeric Unit |
| `risk.variablePage.allTypes` | 全部类型 | All Types | All types |
| `risk.variablePage.saveFailed` | 保存失败 | Save failed | Couldn't save |
| `vault.accessRules.saveFailed` | 访问规则保存失败 | Failed to save access rule | Couldn't save access rule |
| `vault.dataTypes.loadFailed` | 数据类型加载失败 | Failed to load data types | Couldn't load data types |
| `vault.dataTypes.saveFailed` | 数据类型保存失败 | Failed to save data type | Couldn't save data type |
| `vault.policyChanges.detailLoadFailed` | 变更详情加载失败 | Failed to load change details | Couldn't load change details |
| `webhook.detail.loadFailed` | 投递详情加载失败 | Failed to load delivery details | Couldn't load delivery details |
| `webhook.endpointForm.createFailed` | 新建端点失败 | Failed to create endpoint | Couldn't create endpoint |
| `webhook.endpointForm.saveFailed` | 保存端点失败 | Failed to save endpoint | Couldn't save endpoint |
| `webhook.management.clientLoadFailed` | Client 加载失败 | Failed to load Client | Couldn't load Client |
| `webhook.rotate.failed` | 轮换密钥失败 | Failed to rotate secret | Couldn't rotate secret |
| `webhook.status.disableFailed` | 停用失败 | Failed to disable | Couldn't disable |
| `webhook.status.enableFailed` | 启用失败 | Failed to enable | Couldn't enable |
| `webhook.test.failed` | 测试事件发送失败 | Failed to send test event | Couldn't send test event |
| `workflow.batchPanel.hint` | 请核对下表。每项单独提交、单独记录，任一项失败不影响其他项；需要退回修改或驳回的请退出批量逐条处理。 | Check the table below. Each item is submitted and recorded separately, and one failure doesn't affect the others. To return or reject, exit bulk mode and handle items one by one. | Check the table below. Each item is submitted and recorded separately, and one failure doesn't affect the others. To return for changes or reject, exit bulk mode and handle items one by one. |
| `workflow.instance.node.notReached` | 未到达 | Not reached | Not Reached |
| `workflow.instance.stats.active` | 进行中 | In progress | In Progress |
| `workflow.instance.stats.pending` | 未到达 | Not reached | Not Reached |
| `workflow.parts.loadFailed` | 加载失败 | Failed to load | Couldn't load |
| `workflow.people.you` | 你 | You | you |
| `workflow.people.youLead` | 你 | (new) | You |
| `workflow.task.commentHint` | 退回修改或驳回时必填，执行人可见 | Required to return or reject; visible to assignees | Required to return for changes or reject; visible to assignees |
