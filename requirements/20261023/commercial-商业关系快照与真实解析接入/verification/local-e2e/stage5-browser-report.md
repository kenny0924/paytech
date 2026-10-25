# 阶段 5 浏览器验收报告（AC-12，2026-10-25）

结论：30 项检查全部通过（英文 15 项，简体中文 15 项）。明细见 `stage5-browser-result.json`，截图在 `screenshots/`，脚本是 `stage5_browser.mjs`。本轮修了两个页面问题（第 5 节）。种子数据已恢复到验收前的业务状态（第 6 节）。

## 1. 运行方式

- **页面**：worktree 的 ops-web 单独运行在 `http://127.0.0.1:18311`，配置文件是 `stage5-vite.relsnap.config.mjs`。`/api/operations` 由 Vite 转给 nginx `https://127.0.0.1:18444`，再到 Gateway 和 ops-bff，Origin 头改写为运营站点 `https://127.0.0.1:18444`。这样 Gateway 收到的入口通道、Origin 和 X-Real-IP 都与 18301 实例一样。主仓库 18301 的 Vite 没有停，也没有改。验收后 18311 已关闭。
- **语言包的跨域问题**：本地公开桶 MinIO 19400 的 CORS 只放行 `https://127.0.0.1:18444`。脚本因此在浏览器上下文里拦截 19400 的响应，补上测试端口的 `Access-Control-Allow-Origin`，内容、状态码和其他响应头不变（脚本中的 `bundleCors`）。应用和存储的配置都没有改。
- **登录账号**：E2E 管理员，口令从 `local-environment/e2e/secrets.env` 读取，不打印。块 137 已在 `mor_platform_v2e2e` 激活。
- **接口辅助**：`stage5_browser_api.py`。它负责读取种子状态，用接口抢先登记来制造版本冲突，并在最后把 PSP 资格的有效期起点恢复为 2026-10-01。

## 2. 本地多语言准备（只在本地 e2e Platform 上做）

本地 `mor_platform_v2e2e` 原来只有语言和应用两类初始数据，没有命名空间、key、翻译，也没有发布记录。所以 manifest 不提供任何语言，页面无法切换到中文。

1. **文件检查策略和写入路由**（导入文件和语言包上传的前提）：执行 `requirements/20261022/platform-support-多语言管理/db/dml.sql` 第 3 部分的本地版 `stage5-i18n-file-part3-local.sql`，有两处改动：
   - ops-bff × PRIVATE 路由 ID 改为本地的 `239049429963771904`；
   - 桶条件改为字面量，以避开本地会话变量的排序规则冲突（ERROR 1267）。

   结果：新增策略 `I18N_BUNDLE_JSON` 和 `OPERATIONS_BFF_IMPORT`，新增路由 `900000000000135025`（platform-support × PUBLIC，指向 mor-platform-public 桶）；ops-bff × PRIVATE 路由的检查策略由 `CARD_BIN_CSV` 改为 `OPERATIONS_BFF_IMPORT`（version 2）。回滚用同目录 `dml-rollback.sql` 第 3 部分，把 route_id 换成本地值。执行前的备份是 scratchpad 下的 `backup/relsnap-stage5-browser/mor_platform_v2e2e-file-policy-route.sql`。
2. **语言包**：用 `stage5_i18n_local.py` 走真实运营接口，依次完成以下步骤：
   - 应用 operations-console 的默认语言由 zh-CN 改为 en，与 mor-test 一致；
   - 新建 23 个命名空间，其中包括 `qualification` 和 `master-data`；
   - 导入 8813 个 key，分 5 批累积导入，每批不超过 2500 个；
   - 按命名空间逐个导入 zh-CN 翻译；
   - 预检缺口为 0，发布 #1，状态 READY（46/46）。

   第 5 节改了文案后，又以更新模式重导：2 个 key 的英文变更，2 条翻译更新，然后发布 #2（READY，46/46）。i18n 表执行前的备份是 `backup/relsnap-stage5-browser/mor_platform_v2e2e-i18n.sql`。
3. **6 个菜单资源** `nav.resource.OPERATIONS_{QUALIFICATIONS,OPERATING_ENTITIES,LOCAL_PARTNERS}[_MANAGE]` 已随 nav 命名空间发布。中文菜单显示为“主体资格”“经营主体”“Local Partner”，按钮显示为“登记与撤销”“维护收单资质”“维护角色”。

## 3. 检查结果

英文默认：所有写操作都在英文阶段完成。

| ID | 检查 | 结果 | 截图 |
|---|---|---|---|
| M1 | E2E 管理员可见三个菜单：Risk › Qualifications，Commercial › Operating Entities / Local Partners | PASS | en-M1-menu-local-partners |
| Q1 | 资格列表共 4 行。筛选 Client + Organization KYB + Approved 得到 2 行，且写入 URL；重置后恢复 4 行 | PASS | en-Q1-list, en-Q1-filtered-client-kyb-approved |
| Q2 | 查找主体：OE `ae001` 自动转大写；Local Partner 和 PSP Client 的主体卡显示名称、状态，以及该角色允许的资格类型：OE 1 种、Partner 1 种、Client 2 种 | PASS | en-Q2-subject-operating_entity / local_partner / client |
| Q3 | AE001 历史抽屉：时间线显示状态变化，带触发方式和来源两个标签、时间、操作人、版本、有效期、原因码和依据 | PASS | en-Q3-history-drawer-ae001 |
| Q4 | 登记对话框按角色限制资格类型：OE 只有 Entity Underwriting（已有记录，类型锁定）；Partner 只有 Partner Due Diligence；Client 未登记的槽位可选 Individual KYC 或 Organization KYB | PASS | en-Q4-decision-dialog-* |
| Q5 | 真实撤销 PSP Client 的 ORGANIZATION_KYB：对话框提示撤销的影响，接口状态由 APPROVED 变为 REVOKED，页面显示 Revoked | PASS | en-Q5-revoke-dialog, en-Q5-after-revoke |
| Q6 | 登记确认页复述影响：“New order resolutions will treat this subject as qualified. Existing orders are not affected.”。接口抢先登记后再确认，返回 409，对话框提示“This qualification changed after you opened it. Refresh and try again.”；点“Refresh”后显示 Eligible | PASS | en-Q6-decision-confirm-impact, en-Q6-stale-409 |
| Q7 | 页面再撤销一次、再登记一次，最终 APPROVED / ELIGIBLE | PASS | en-Q7-revoke-dialog, en-Q7-after-revoke, en-Q7-after-register |
| E1 | 经营主体列表：AE001、Partner 来源、PayFac 徽标、提供方链接。筛选 Partner + `ae`（自动转为 AE）得到 1 行；筛选 Client 时显示空态 | PASS | en-E1-entity-list, en-E1-entity-list-partner-ae |
| E2 | AE001 详情：三档资质卡，当前为 PayFac；提供方卡显示 LP-PAYFAC-AE001 和 Open Partner；准入范围 1 行（US / USD / RETAIL / 5999）；卖方披露 1 行（US / en），附精确匹配说明；资格卡显示 Eligible | PASS | en-E2-ae001-detail |
| E3 | 修改收单资质：选 Merchant 时出现警告“MERCHANT entities no longer accept Client orders…”。依次改为 MERCHANT_RESALE_APPROVED 和 PAYFAC，两次都保存成功，最终为 PayFac | PASS | en-E3-merchant-warning, en-E3-after-merchant_resale_approved, en-E3-after-payfac |
| E4 | 提供方卡的 Open Partner 跳到 `/master-data/local-partners/900000000001370001`；资格卡的 Open in Qualifications 跳到 `?roleType=OPERATING_ENTITY&roleId=AE001` | PASS | — |
| P1 | Local Partner 列表按角色 OE Provider 筛选，得到 1 行（LP-PAYFAC-AE001，提供 1 个主体） | PASS | en-P1-partner-list-oe-provider |
| P2 | PayFac LP 详情：角色卡显示锁定说明；提供的经营主体为 AE001；资格卡显示 Partner Due Diligence Eligible | PASS | en-P2-payfac-detail |
| P3 | 角色对话框：OE Provider 已勾选但不可操作，有提示条和悬停说明原因。先加 REFERRER 再去掉，两次都保存成功，最终只剩 OE_PROVIDER | PASS | en-P3-roles-dialog-locked, en-P3-after-add-referrer, en-P3-after-remove-referrer |

简体中文：通过页头语言菜单切换。这一阶段只核对展示和对话框，对话框都取消，不写入。

| ID | 检查 | 结果 | 截图 |
|---|---|---|---|
| L1 | 页头选择“简体中文”后整页刷新，h1 为“主体资格”，`html lang` 为 zh-CN，加载了 23 个 zh-CN 语言包 | PASS | zh-CN-L1-after-switch |
| M1 | 菜单显示“风控管理 › 主体资格”“商务 › 经营主体 / Local Partner” | PASS | zh-CN-M1-menu-local-partners |
| Q1—Q4 | 同英文的列表、筛选、查找主体、历史抽屉和登记对话框，资格类型显示为“主体承保审核”“Partner 尽调”“个人 KYC / 企业 KYB” | PASS | zh-CN-Q1-*, zh-CN-Q2-*, zh-CN-Q3-history-drawer-ae001, zh-CN-Q4-* |
| Q6 | 登记确认页显示“登记后新订单解析会把该主体视为合格。已有订单不受影响。”；撤销对话框显示警告 | PASS | zh-CN-Q6-decision-confirm-impact, zh-CN-Q6-revoke-dialog |
| E1—E4 | 经营主体列表和详情；选“商户”时警告“改为 MERCHANT 后该主体不再承接 Client 订单…”；页面跳转正常 | PASS | zh-CN-E1-*, zh-CN-E2-ae001-detail, zh-CN-E3-merchant-warning |
| P1—P3 | Local Partner 列表和详情；角色对话框中“OE 提供方”锁定，提示“该 Partner 仍在提供生效的经营主体，不能取消 OE 提供方角色。” | PASS | zh-CN-P1-*, zh-CN-P2-payfac-detail, zh-CN-P3-roles-dialog-locked |
| Z1 | 12 处扫描（页面内容区和对话框 / 抽屉）没有原样 key，也没有白名单以外的英文 | PASS | — |

Z1 的白名单只有三类：规范术语和种子中保留的英文（Client、Local Partner、Partner、KYB / KYC、MERCHANT、MoR、PSP、PayFac、Party、locale、MCC），编码，以及业务数据（合成主体名称、阶段 5 后端联调写入的依据原文“AE001 again”）。

## 4. 控制台与网络

- **HTTP 4xx / 5xx 都有原因**：
  - `POST …/qualifications/manual-decisions → 409` 是 Q6 有意制造的版本冲突。
  - 中文阶段有一次 `GET /api/operations/auth/v1/context → 429`。原因是脚本整页导航太频繁，触发了每分钟限流；脚本等待后重载就恢复了，与新页面无关。
  - 另有若干 `net::ERR_ABORTED`，是页面跳转时浏览器取消了还没完成的请求，属正常现象。
- **控制台错误**：
  - 409 和 429 各一条资源加载失败，原因同上。
  - antd 弃用警告：`Spin tip` 来自既有的 `App.tsx` 会话检查，`Space direction` 来自既有的 `pages/Accounts.tsx`（登录后的默认页）。两者都不在新页面里。
  - 新页面原来会报 `Modal maskClosable` 弃用警告，已修复，见第 5 节。

## 5. 发现并修复的页面问题（worktree ops-web）

1. **Modal 弃用警告**：四个新对话框在控制台报 `[antd: Modal] maskClosable is deprecated`。这四个对话框是：
   - `qualification/QualificationDialogs.tsx` 中的登记和撤销；
   - `masterData/MasterDataModals.tsx` 中的收单资质和角色。

   处理：`maskClosable={!command.saving}` 改为 `mask={{ closable: !command.saving }}`，与 `webhook/WebhookModals.tsx` 的现有写法一致。
2. **历史时间线标签重复**：英文显示“Manual Manual”，中文显示“手工 / 手工登记”。原因是触发方式 OPERATIONS_MANUAL 和决定来源 MANUAL 的文案相同或几乎相同。
   - 处理：只改触发方式的文案。`qualification.enum.triggerType.OPERATIONS_MANUAL` 改为 en“Operations Action”/ zh“运营操作”，`OPERATIONS_REVIEW` 改为 en“Operations Review”/ zh“运营审核”。
   - 涉及文件：`src/i18n/en/qualification.json` 和 `i18n/zh-CN/qualification.json`。
   - 本地已重新发布 #2，Q3 现在核对两个标签是否不同。

复验结果：`npm test` 1519 个通过，`npx tsc --noEmit` 通过，`node scripts/i18n-export-source.mjs` 校验通过（23 个命名空间，8813 个 key）。整套浏览器验收重跑一遍，30 项全部通过。

## 6. 种子数据状态

| 项 | 验收前（阶段 5 后端结束时） | 验收后 |
|---|---|---|
| PSP Client ORGANIZATION_KYB | APPROVED / ELIGIBLE，validFrom 2026-10-01T00:00:00Z，version 3 | APPROVED / ELIGIBLE，validFrom 2026-10-01T00:00:00Z，version 33 |
| AE001 收单资质 | PAYFAC，changeVersion 4 | PAYFAC，changeVersion 14 |
| PayFac LP 角色 | [OE_PROVIDER]，changeVersion 3 | [OE_PROVIDER]，changeVersion 15 |

- 版本号上升，是因为脚本调试时整套跑了 6 次，每次都有真实写入并且都已复原，业务值都已回到原状。页面登记的有效期起点是当时时间，脚本最后用接口重新登记一次，把起点恢复为 2026-10-01。
- 验收后直连 commercial 解析：MOR 返回 200 / MOR / 8 个角色，PSP 返回 200 / PSP / 9 个角色，与阶段 5 后端结论一致。
- 合规和主档中多了这次验收的历史记录：资格变化、BCR 和原因码 `STAGE5_BROWSER_E2E`。按设计这些记录只追加，不删除。

## 7. 与设计（technique.md §3.5）对照

- **资格登记**：主体查找、主体卡、列表筛选（角色 / 资格类型 / 存储状态，实时状态作为列）、历史时间线（来源、操作人、依据）、登记两步确认并复述影响、撤销需要依据、版本冲突提示刷新，都与设计一致。设计要求筛选“实时状态”，实现改为按存储状态筛选，属于 4.2 已接受的偏差。
- **经营主体**：
  - 列表按状态 / 来源 / 国家筛选，列与设计一致，只是不显示资格摘要（4.2 已接受，列表接口不返回资格）。
  - 详情的头部、收单资质三档说明和修改对话框（含 MERCHANT 警告）、提供方卡（Partner 名称、资格、链接）、准入范围（只读）、卖方披露（只读，附精确匹配说明）、资格卡（链到资格登记并预填主体）都与设计一致。
- **Local Partner**：列表按状态 / 角色筛选，同样不显示资格摘要（4.2 已接受）。详情的角色卡、角色对话框（OE_PROVIDER 锁定并说明原因）、提供的经营主体、资格卡都与设计一致。
- **小的视觉差异**，没有修改，供评审参考：
  - 角色对话框的“原因”标签没有必填星号，收单资质对话框有，但两处都做了必填校验；
  - 查找主体的“查找 / 清除主体”按钮比输入框略低，与既有 configuration 页搜索栏的对齐方式相同。

## 8. 后续注意（阶段 6）

- **侧栏中文会被覆盖**：mor-test 上侧栏底部的中文（`nav.sidebar.product/hide/show`）是 fc00257f 在 Platform 里直接改的，仓库的 zh-CN 种子 `i18n/zh-CN/nav.json` 仍是英文。这次本地按种子发布，所以侧栏底部仍是英文。阶段 6 在 mor-test 导入 nav 命名空间（为了 6 个新菜单 key）时，如果整份导入 nav 的 zh-CN 种子，会把这三条改回英文。应只导入新增的 key，或者先把仓库种子改成中文。
- **mor-test 语言包**：需要先新建 `qualification` 和 `master-data` 两个命名空间，再导入 key 和翻译并发布。第 5 节修改的两条 triggerType 文案已经包含在 worktree 的 JSON 中。
- **本地环境保留的改动**：本地 e2e Platform 保留了 operations-console 发布 #2（默认语言 en）以及第 2 节的文件策略和路由。主仓库 18301 页面现在也会显示语言菜单。
