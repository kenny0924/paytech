# client-master：业务信息补采 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-28 | 来源 | design-discuss/client-activation 讨论：承保（REQ-03）需要期望支付方式、退款政策链接、交付方式作为输入；登记为 [2026-10-28 客户上线决定](../../../agent-docs/decisions/2026-10-28-client-go-live-orchestration.md) REQ-12 | 已确认（讨论） |
| 2026-10-28 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R7、规则 B1—B5、逐系统变动点、验收 AC-01—AC-08、待确认 Q1—Q3 | 已交付 |
| 2026-10-29 | 1 确认 | 用户"按建议，开始 REQ-12 阶段 2"：Q1 期望支付方式编码来自 commercial 产品能力目录；Q2 无网站的个人申请退款政策链接可选（Schema 条件，阶段 2 核实条件字段）；Q3 交付方式四值（单选，特殊情况写业务描述）。默认八阶段，进入阶段 2 | 已确认 |
| 2026-10-29 | 2 差异与影响 | [technique.md](./technique.md) 评审稿（只读核对代码与本机 / mor-test SELECT）：F-01—F-17、需求项差异、逐系统影响、待决 S-01—S-12、规模 M（偏小）。关键：全链路值为字符串、无多选 / 整数控件（F-02）；Schema 不能表达"无网站可选"（级别按字段固定）；EVENTUALLY 不阻断批准；批准摘要在 `t_party_profile`，`t_client_business_profile.content_hash` 无写入而 Checkout 分类要求它（F-07，存量缺口另报）；commercial 支付方式能力 4 个、无辖区维度；Schema 只能 DML 发布，mor-test 在途 11 个申请冻结在 v1 | 已交付 |
| 2026-10-29 | 2 确认 | 用户"按建议，进入阶段 3"：S-01—S-12 按建议（含 S-03 个人 Schema 退款政策 OPTIONAL / 机构 EVENTUALLY、S-04 EVENTUALLY 不阻断批准、S-05/S-06 client-master 读 commercial 支付方式能力且不按辖区过滤、S-08 改为主体资料摘要口径、S-10 不新增读接口）。F-07 存量缺口未立项 | 已确认 |
| 2026-10-29 | 3 方案 | [technique.md](./technique.md) 第 7—13 节评审稿：方案总览、接口（调整 4、复用 commercial `ProductCatalogApi#listCapabilities` 1、新增 0）、DDL（`t_client_business_profile` 4 列）、Schema v3 DML（9 新版本、约 327 复制 + 36 新字段，ID 段 `9281200000…`，先 DRAFT 后 ACTIVE、回滚拒绝已有 v3 申请）、文案 29 键、测试与验证、切分 T1—T9、发布顺序（DDL → client-master → 两个 Web → 语言包 → DML ACTIVE）；O-1—O-5；requirement 同步（R1/R2/R4—R6、B1、B5、新增 B6、AC-06）。Pencil 原型：门户 `client-console.pen` 板 `jA2it`、运营 `operations-console.pen` 板 `NhiN6`，导出见 [prototypes/](./prototypes/) | 已交付 |
| 2026-10-29 | 3 确认 | 用户"按建议，进入阶段 4"：方案第 7—13 节、O-1—O-5 与原型（门户 `jA2it`、运营 `NhiN6`）确认；阶段 4 含本机 auto 库 `mor_client_master` 执行前滚 DDL。实施工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-rq12`（基于 6753faba + 本需求文档） | 已确认 |
| 2026-10-29 | 4 补充授权 | 用户"可以"：本机 auto 库 `mor_client_master` 补执行已提交的 `2026-10-27-legal-pack-revision-ddl.sql`（存量缺口，基线 78/164 测试因缺 `draft_revision` 失败）；11:47:16Z 执行并核对 | 已完成 |
| 2026-10-29 | 4 实施与自测 | 工作树 `Code-MoR-rq12`（基于 6753faba）：后端 T1—T5、T9（client-master 176 / 0、client-console-bff 102 / 0，本机 `mor_client_master` 前滚 DDL），前端 T6—T8（门户 152、运营 1601），见下文"阶段 4 实施记录"；偏差 D-1—D-5；隐藏字段清空收窄为只清预计交付天数 | 已完成 |
| 2026-10-29 | 4 确认 / 5 授权 | 用户"继续"：接受阶段 4 结果与偏差 D-1—D-5、前端与原型差异；commercial 故障时表单请求重发（约 1 秒多后 503）按建议先接受；授权本机联调：`mor_client_master_v2e2e` 先补跑法务修订 DDL / DML 再执行 REQ-12 DDL 与 v3 DML、替换本机 e2e client-master JAR（备份 *.pre-rq12）、工作树门户与运营前端起本机服务、接口联调与浏览器走查 zh-CN / en × 1440 / 390；不含 mor-test、提交、推送 | 已确认 |
| 2026-10-29 | 5 本机联调（接口） | [verification/stage5-report.md](./verification/stage5-report.md)：`mor_client_master_v2e2e` 补跑法务修订 DDL / DML 后执行 REQ-12 DDL 与 v3 DML（v1 → v3）；本机 client-master、client-console-bff 换为工作树构建（`*.pre-rq12`，两者一起回滚，client-console-bff 经用户追加授权）；接口联调 179 PASS；四眼批准后主体资料摘要含新四键 | 已完成 |
| 2026-10-29 | 5 浏览器走查 | [verification/stage5-report.md](./verification/stage5-report.md) 第 9 节：本 worktree 门户 Vite 18312、运营 Vite 18313（停 18311 上 wfdq 进程被权限拒绝，未动）；本机语言包 client-console #5、operations-console #14；Playwright zh-CN / en × 1440 / 390 共 60 步通过（E 多选弹层 / 底部面板、标签移除、https 与 1—365 报错与错误汇总、改即时交付清空天数；D 新建个人草稿 241536278410047488 退款政策可选；F 冻结 v2 无新字段；运营 B 标签 / 链接新开页 / 14 天，C 与 v1 "未采集"，A Client 详情）；修复 4 项（EVENTUALLY 显示"批准前提供"、多选整行、运营 390 标签溢出、长链接悬停显示完整网址），门户测试 153、运营 1601 通过 | 已交付 |
| 2026-10-29 | 5 确认 / 6 启动 | 用户"继续"：接受阶段 5；commercial 不可用时门户收到 BFF 通用 `SERVICE_UNAVAILABLE` 按实际行为接受（方案随之修正）；门户分组顺序保持现有（经营情况在预计交易量前）；进入阶段 6（mor-test 发布准备：只读核对基线 + 本机构建镜像、v3 DML 用 mor-test 只读导出离线演练、两个语言包、compose 候选与回滚、runbook、验收脚本；不停 mor-test 容器、不执行 mor-test SQL） | 已确认 |
| 2026-10-29 | 6 mor-test 发布准备 | [release/stage6-report.md](./release/stage6-report.md)：只读基线（现网无基线外改动；client-console-bff、ops-bff 不发布）；候选 rq12-20261029a：client-master `2b12c2e2`、client-console-web `e17a9a0a`、operations-console-web `0b456b8a`；DDL + v3 DML 用 mor-test 只读导出离线演练 16 步通过（D-2 关闭）；语言包 client-console #8 / operations-console #22 只读计划；runbook 与验收脚本 | 已完成 |
| 2026-10-29 | 6 确认 / 7 授权 | 用户"可以，按建议，继续"：接受阶段 6；P-1 授权阶段 7（mor-test 停 / 起 client-master、client-console-web、operations-console-web，`mor_client_master` DDL + v3 DML，两个语言包发布，验收新账号注册挑战改写）；P-2 不在 mor-test 提交测试申请；P-3 选项顺序按现状（STC_PAY 在前）。主会话按 release/runbook.md 执行 | 已确认 |
| 2026-10-29 | 7 mor-test 发布 | 主会话按 release/runbook.md 执行（13:00—13:05Z）：备份 `Code-MoR-backups/rq12-20261029/mortest-20261029T130046Z`；预检同基线；DDL 4 列；client-master `2b12c2e2`（34 秒 healthy，无 ERROR）、client-console-web `e17a9a0a`（11 文件）、operations-console-web `0b456b8a`（256 文件）；语言包 client-console #8、operations-console #22 READY 且为当前；v3 DML 先 DRAFT 核对（退款政策机构 EVENTUALLY ×4、个人 OPTIONAL ×5），13:03:54Z ACTIVE（v3 ACTIVE 9、v2 RETIRED 9、字段 965）。API 验收 `rq12_api.py --env test` 全部 PASS（验收账号注册挑战改写、两份草稿已撤回）；浏览器走查 B1—B12 zh-CN / en × 1440 / 390 通过（76 张，[release/stage7-report-browser.md](./release/stage7-report-browser.md)），走查草稿 2 份已撤回、未提交；REQ-05 工作流页与合同详情回归通过；已通知 design-discuss-24 | 已完成 |
| 2026-10-29 | 7 确认 / 8 授权 | 用户"你提交吧，我来推送"：授权提交（私有索引）；推送由用户执行 | 已确认 |
| 2026-10-29 | 8 提交 | 工作树改动合入主树并提交（代码、SQL、PDManer、文档、验证与发布材料，不含本机测试数据备份 `verification/stage5/backup/`、`.vite-cache` 与 node_modules 链接；`client-console.pen` / `operations-console.pen` 混有其他会话改动未纳入，原型以 `prototypes/` PNG 为准） | 已完成 |

## 适用阶段

默认八阶段（2026-10-29 用户确认）。SQL、mor-test 部署、提交、推送分别授权。

## 当前阶段与下一确认点

阶段 1—8 已完成（2026-10-29）。mor-test 自 2026-10-29 13:01Z 运行 rq12-20261029a（client-master `2b12c2e2`、client-console-web `e17a9a0a`、operations-console-web `0b456b8a`），`mor_client_master` 4 列 + 入驻 Schema v3 ACTIVE，语言包 client-console #8、operations-console #22。推送由用户执行。本机 e2e 保留新 client-master / client-console-bff（`*.pre-rq12`，一起回滚）、门户 Vite 18312、运营 Vite 18313；本机语言包 client-console #5、operations-console #14。遗留：F-07 业务资料摘要列无写入（未立项）；compliance 登记体不含三项（随 REQ-03）；运营入驻配置页与 REQ-05 动作目录页 390 宽横向滚动（既有）；同一 Client 业务资料单行，撤回草稿值会带到新草稿（既有设计）。

## 阶段 4 实施记录（前端）

2026-10-29，前端执行代理，工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-rq12`；未提交、未推送、未部署、未写库、未触碰 mor-test，未发布语言包。

### 改动文件

| 任务 | 文件 | 内容 |
|---|---|---|
| T6 | `client-console-web/src/onboarding/model.ts` | `Widget` 增 `MULTI_SELECT` |
| T6 | `client-console-web/src/onboarding/schema.ts` | `splitCodeList` / `joinCodeList`（与 `ClientDraftPatch` CODE_LIST 同口径：去空白、转大写、去空、去重、升序、`,` 连接，≤ 20 项且每项 `[A-Z][A-Z0-9_]{0,63}`）；`fieldIssue`：多选格式、`^https://` 正则字段非 https 时报 `error.https_url`、正则等价于连续整数区间时报 `error.days_range`（区间由 `integerRange` 探测 Schema 正则得出，不按字段名分支）；`CLEARED_WHEN_HIDDEN`（只登记 `business.fulfillmentDays`，与 client-master 清空规则一致）与 `pathsHiddenByChange`（改值后该字段因 `visibleWhen` 变为不可见且有值时返回其路径） |
| T6 | `client-console-web/src/onboarding/MultiSelect.tsx`（新） | 多选控件：可移除标签（× 按钮带"移除 {label}"可访问名）、勾选框列表（名称 + 等宽编码）、勾选不关闭；键盘 ↑↓ / Home / End / Space / Enter / Esc / Tab，触发按钮 Backspace 删最后一个标签；≤640px 底部面板（已选数量 + "完成"）；不在目录的已存代码按编码显示且可移除 |
| T6 | `client-console-web/src/onboarding/widgets.tsx`、`RefSelect.tsx`、`FormTask.tsx`、`ReviewTask.tsx`、`onboarding.css` | 登记 `MULTI_SELECT`（目录缺失显示"选项暂不可用 · 重试"）；整数区间字段弹数字键盘并占半行；`useNarrow` 从 RefSelect 导出复用；`useDraftForm.set` 离开预售时同步清空预计交付天数（随同一 PATCH 发 `null`），其他带可见条件的字段隐藏后仍保留原值；核对页多选按目录顺序显示名称；多选样式 |
| T6 测试 | `client-console-web/src/onboarding/businessInfo.test.ts`（新）、`countrypack.test.ts` | 新增 10 个用例（含回归：发证机关改离 AE_OTHER 时机关名称不清空、不随 PATCH 发送，同时离开预售仍发 `fulfillmentDays: null`）；国家包回放补 commercial 支付方式目录夹具与 v3 四字段核对（8 个辖区 / 主体组合），并修正回放的目录过滤（与 `CatalogItemMapper` 一致包含 `'*'` 全局目录项，原回放漏掉全部全局目录） |
| T7 | `operations-console-web/src/onboardingConfig/schemaLabel.ts` | `FIELD_CATALOGS` 加 `business.fulfillmentType → catalog:FULFILLMENT_TYPE`；`valueView`：支付方式拆为"名称 编码"标签（无译文只显示编码）、网址（`websiteUrl` / `refundPolicyUrl`，仅 http/https）为链接、交付方式为标签、天数为"{count} 天"、四键全空为"未采集"、有任一项有值时空项为"未填写"、非预售不显示天数行 |
| T7 | `operations-console-web/src/onboardingConfig/ProfileValues.tsx`（新）、`clientReview/ReviewEvidence.tsx`、`masterData/ClientDetail.tsx`、`clientReview/clientReview.css` | 审核"申请"页签与 Client 详情共用 `ProfileDescriptions`；链接 `target="_blank" rel="noopener noreferrer"`、单行截断 + 新标签页图标（可访问名"在新标签页打开"） |
| T7 | `operations-console-web/src/clientReview/clientReview.ts`、`masterData/clients.ts` | 资料值长度上限 2000 → 2048（`MAX_VALUE_LENGTH`）：退款政策链接最长 2048，原上限会让整份申请 / Client 详情解码失败 |
| T7 测试 | `operations-console-web/src/onboardingConfig/businessInfo.test.ts`（新）、`onboardingSchemaCatalog.test.ts` | 新增 5 个用例；镜像命名空间允许的键族加 `paymentMethod` |
| T8 | `client-console-web/src/i18n/en/onboarding-schema.json`、`onboarding.json`、`i18n/client-console.zh-CN.seed.json`；`operations-console-web/src/i18n/en/{onboarding-schema,client-review}.json`、`i18n/zh-CN/` 同名 | 门户 23 键（technique 第 11 节 17 个 `onboarding-schema.*` + 4 个 `onboarding.*`，另增 `onboarding.form.multi_select_count`、`onboarding.form.multi_select_done`）；运营 22 键（`onboarding-schema` 镜像 13 个，不含 `help.*`；`client-review` 9 个，另增 `client-review.application.notFilled`） |

### 自测

| 项 | 结果 |
|---|---|
| client-console-web `npm test` | 152 / 152 通过（基线 141；新增 11：业务信息 10、v3 国家包回放 1，后者已对后端执行代理写入的 v3 初始化 DML 实跑） |
| client-console-web `tsc --noEmit`、`vite build` | 通过（构建输出到会话临时目录，未写工作树 `dist/`） |
| client-console-web `i18n:export-source` | 9 命名空间 1048 键；zh-CN 导入文件空译文 0 |
| operations-console-web `npm test` | 1601 / 1601 通过（基线 1596，新增 5） |
| operations-console-web `tsc --noEmit`、`vite build` | 通过 |
| operations-console-web `i18n:export-source`（含 en / zh-CN 键集一致与非空校验） | 通过，25 命名空间 10012 键 |
| 浏览器（本地临时自测页，渲染真实 `SchemaFields` / `useDraftForm` / `ProfileDescriptions`，验证后删除） | 门户 zh-CN 1440：多选弹层（勾选、编码）、`http://` 链接与 400 天的字段错误 + 错误汇总；键盘 ↓↓↓ Space Esc 勾选 STC Pay 并把焦点还给控件；PATCH 体为 `APPLE_PAY,CARD,STC_PAY`；改为即时交付后天数字段消失且同一 PATCH 发 `fulfillmentDays: null`；× 与 Backspace 移除标签。门户 en 390（iframe 视口）：底部面板、"1 selected / 2 selected"、Done 关闭后保存 `APPLE_PAY,CARD`，无横向滚动，个人退款政策显示 Optional。运营 en：v3 / 旧版 / 个人三种资料显示符合预期 |

未做：运营 zh-CN 浏览器截图（运营本地 zh 需要 Platform 语言包，只核对了键与导出）；真实 BFF / client-master 联调与 mor-test（阶段 5—7）。

### 与原型的差异

| 原型 | 实现 | 原因 |
|---|---|---|
| 门户分组标题"收款与售后" | 沿用现有 `OPERATIONS` 组文案（经营情况 / Operations） | technique 第 11 节："实现时以现有组文案为准" |
| 门户帮助文字在控件下方 | 帮助文字在标签下（现有 `FieldShell` 统一位置） | 与全表单其他字段一致 |
| 门户交付方式选项带括注（如"预售（先收款，稍后交付）"），天数输入框内带"天"后缀 | 选项用第 11 节短文案（即时交付 / 预售 / 订阅 / 服务交付），无后缀（标签已写"天数"） | 运营原型同一键显示短标签"预售"，两端共用 `onboarding-schema.catalog.FULFILLMENT_TYPE.*`，只能取一种 |
| 门户多选弹层底部目录说明 | 未加 | 字段帮助文字已说明；避免新增未评审文案 |
| 运营"交付方式：预售 · 预计 30 天交付"同一行 | 交付方式（标签）与预计交付天数（"30 天"）分两行 | client-master values 是两个独立键，审核页按键通用渲染 |
| 运营支付方式标签按目录顺序（银行卡在前） | 按存储顺序（代码升序：Apple Pay、银行卡、STC Pay） | 运营端不加载 commercial 目录，不新增读取 |
| 运营个人空退款政策"未填写（可选）" | "未填写" | 运营端不知道字段的要求级别；"未采集"与"未填写"按四项是否全空区分 |
| 运营右侧"网站审查"旁的退款政策链接 | 未改检查项面板 | 原型注明"不新增检查项"；退款政策链接已在"申请"页签可点开 |

### 需主会话知悉

1. 隐藏即清空只针对 `business.fulfillmentDays`（2026-10-29 主会话要求收窄）：现有可见条件字段（如 `organization.registrationAuthorityCode=AE_OTHER` 下的 `attributes.AE_REGISTRATION_AUTHORITY_NAME`）行为不变，隐藏后保留原值；回归用例见 `businessInfo.test.ts`。
2. 门户 `onboarding.error.days_range` 带参数 `{min}` / `{max}`（文案"请输入 {min}—{max} 的整数"），区间来自 Schema 正则，不写死 1—365。
3. 工作树 `client-console-web/node_modules` 指向 `/Users/kenny/Desktop/Workspace/Code-MoR-onboard/client-console-web/node_modules`（主树该目录缺 `intl-messageformat` 与 `@fontsource/*`，依赖版本与本工作树 `package.json` 一致）；`operations-console-web/node_modules` 指向主树。两个符号链接为未跟踪文件，提交时不要纳入。

## 阶段 4 实施记录（后端）

执行者：client-master 后端（T1—T5、T9），工作树 `/Users/kenny/Desktop/Workspace/Code-MoR-rq12`，2026-10-29。已读：`DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`examples/WRITE_API_EXAMPLE.md`、`UNIT_TEST_GUIDE.md` + `examples/UNIT_TEST_EXAMPLE.md`、`DATABASE_DESIGN_GUIDE.md`、`CURRENT_IMPLEMENTATION.md` client-master 段。未提交、未推送、未部署；未改 `CURRENT_IMPLEMENTATION.md`。

### 实现要点（对照写接口示例第 11 节）

- 接口：路径与 HTTP 形状均未变（第 8 节合同）。`FormSchemaResponseBody` / `ClientDraftModels` 只补 JavaDoc（`MULTI_SELECT`、`COMMERCIAL_PAYMENT_METHOD`、business 四键）。
- 草稿（`SaveClientDraftProcessor` → `ClientDraftPreparer`，事务外准备，无外部调用）：`ClientDraftPatch` 新增 `CODE_LIST`（唯一规范化：拆分、trim、大写、`[A-Z][A-Z0-9_]{0,63}`、去重、升序、≤ 20 项、全空为清空）、`HTTPS_URL`、`INTEGER`，以及 4 个路径。`ClientDraftPreparer.fulfillmentDays` 规则：改为非 PRE_ORDER 时在同一写入清空天数；非 PRE_ORDER 下单独填写天数返回 422。写入沿用 `saveBusiness` 的单一本地事务（Mapper XML 增 4 列）。
- 读取：`ClientDraftFields` / `ClientDraftValues` / `ClientDraftMapper.xml` 增 4 列，草稿、审核与 admin 的 values 自动带出，批准摘要 `t_party_profile.content_hash` 的输入也随之包含（AC-06）。
- 外部目录：新增端口 `ClientPaymentMethodCatalogClient` 与 `ClientPaymentMethodCatalogClientImpl`（`ProductCatalogApi#listCapabilities(null, "ACTIVE")`，本地过滤 `PAYMENT_METHOD` + `ACTIVE`，校验代码格式，去重保序）。任一失败或不合法响应返回 503 `ONBOARDING_DEPENDENCY_UNAVAILABLE`；调用前拒绝已有事务；不缓存（O-5）。Bean 在 `ClientDraftConfiguration`，传输同 `clientAgreementPartyClient`。
- `GetOnboardingFormSchemaProcessor` 与 `ClientSubmissionPreparer` 只在 `pack.usesCatalog(COMMERCIAL_PAYMENT_METHOD)` 时调用 commercial，调用在事务外。在途 v1 / v2 申请零调用。`FormSchemaConverter` 放入 `catalogs.COMMERCIAL_PAYMENT_METHOD`，`labelKey = onboardingSchema.paymentMethod.<代码>`；`OnboardingPackServiceImpl` 改用 `isExternal`，外部目录不从 `t_catalog_item` 加载。`RequirementInput.activePaymentMethods` 与 `OnboardingRequirementEvaluator` 对 `MULTI_SELECT` 逐项做目录校验。
- 常量：`WidgetCode.MULTI_SELECT`；`ReferenceCatalogCodes.COMMERCIAL_PAYMENT_METHOD` / `COMMERCIAL_PREFIX` / `isExternal`；`OnboardingPack.usesCatalog`。
- 不适用项：无锁变化（沿用原草稿锁）、无新表 / View / MQ / 补偿；DataObject 不出模块。

### 文件

- SQL：`changes/2026-10-29-business-info-collection-ddl.sql`（可重跑；4 列 + 3 处注释）与 `-ddl-rollback.sql`（v3 存在时拒绝；可重跑）；`changes/2026-10-29-business-info-collection.sql`（v3 DML，`@cm_bizinfo_status` ACTIVE / DRAFT，7 个停止条件）与 `-rollback.sql`（有 v3 申请或资料已填交付方式时拒绝）。`client-master-schema.sql`、测试 `init/ddl.sql`、`client-master-initial-data.sql`（最终状态：v2 / FREELANCER v1 退役，v3 9 个 ACTIVE；字段 327 复制 + 36 新增 = 363；`FULFILLMENT_TYPE` 4 项）同步更新；需求目录新增 `db/ddl.sql` 索引。
- 模型与文档：`client-master.pdma.json`（直接编辑，JSON 往返字节一致，4 个字段 + 3 处注释）、`CLIENT_MASTER_PHYSICAL_DECISIONS.md`（本次记录；第 1 节"只能由生成器"改注为 2026-10-22 起直接编辑）、`agent-docs/database/CLIENT_CONTRACT_DATABASE.md`、`paytech/client-master/README.md`。
- 生产代码：见上（core 15 个文件，含新增 2 个；app 2 个配置；api 2 个 JavaDoc）。
- 测试：新增 `BusinessInfoCollectionTest`（8 例）、`MockCommercialProductCatalogApi`（只实现列表，其余方法均为意外调用）、`CommercialProductCatalogMocker`（DTO / 503 / 断连 / 缺列表，记录调用参数）。扩展 `OnboardingFormSchemaTest`（v3 ID，新增 3 例）、`OnboardingAdminQueryTest`、`ClientApplicationAdminTest`、`ClientReviewLifecycleV2Test`（AC-06 独立复算摘要）、`ReferenceCatalogGuardTest`（禁止 `COMMERCIAL_` 目录码，`FULFILLMENT_TYPE` 恰为 4 项）、`ReapplyAfterRejectionTest`（新申请冻结 v3，用夹具补业务信息）。`ClientMasterMockTestBase` 全局基线加 4 个 ACTIVE 支付方式（同参考数据先例）；`OnboardingFixtures` / `OnboardingTestMapper` 增加辅助。client-console-bff `ClientFormSchemaHttpAutoTest` 新增 1 例（多选、外部目录、天数条件与正则原样透传，不多查 Platform）。

### 验证

| 命令（JDK 25，`-o -Dmaven.repo.local=/Users/kenny/.m3-rq12/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_`，密钥从 `local-environment/.env.local` 加载、未打印） | 结果 |
|---|---|
| `paytech/client-master`：`../mvnw … install -pl paytech-client-master-api,paytech-client-master-core,paytech-client-master-app,paytech-client-master-test`（串行，本机 MySQL 13306 `mor_client_master`） | **176 run，0 failures，0 errors，0 skipped** |
| `paytech/client-console-bff`：`../mvnw … test` | **102 run，0 failures，0 errors** |
| 本机临时库试跑（`rq12_scratch_a/b/c`，用后已删除） | 旧 Schema + 旧初始化 DML 起步：DML 先于 DDL 被停止条件 7 拦下；DDL；DML DRAFT（v3 9 行 DRAFT、目录项 DRAFT）；ACTIVE 发布；重跑为空操作；资料有交付方式时 DML 回滚被拒；v3 存在时 DDL 回滚被拒；DML 回滚并重跑；DDL 回滚并重跑；再次前滚。最终与新 `schema.sql` + `initial-data.sql` 全新建库比对：4 张表结构、国家包 7 表、目录、Schema、字段逐行一致（仅 v2 退役行的 `update_time` 为执行时间）；退款正则落库为 `^https://\S+$`（13 字符） |

未运行：全 reactor（共享本机库规则）、PDManer 生成器漂移脚本测试（按 2026-10-22 决定不再使用生成器）。

### 本机数据库变更（`mor_client_master`，13306，仅本机 auto 库）

1. 2026-10-29 本次 DDL `changes/2026-10-29-business-info-collection-ddl.sql`：执行前用 `mysqldump --no-data` 备份结构（scratchpad `backup/mor_client_master-structure-before-rq12.sql`）；执行两次（第二次跳过 ADD）。`information_schema` 核对 4 列（varchar(512) / varchar(2048) / varchar(32) / smallint，可空，位于 `business_description` 之后）与 3 处注释。
2. **存量缺口修复（用户授权，经主会话转达）**：2026-10-29T11:47:16Z 执行 `changes/2026-10-27-legal-pack-revision-ddl.sql` 一次。该库此前缺 `t_client_application.draft_revision` 与 `t_application_declaration.draft_revision`，导致基线 78 / 164 例 BadSqlGrammar。执行后核对：`t_client_application.draft_revision` bigint NOT NULL 默认 0，`t_application_declaration.draft_revision` bigint 可空。全库列（类型、可空、注释）与仓库 `client-master-schema.sql` 新建库比对为 0 差异。
3. 未触碰 `mor_client_master_v2e2e`、mor-test 或其他库。

### 与技术方案的偏差

- D-1：草稿错误消息按字段类型给出："须为以逗号分隔的代码，最多 20 项"、"须为 https 网址"、"须为整数"、"仅在交付方式为预售时填写"。天数超出 3 位时沿用现有"长度不能超过 3 个字符"。
- D-2：v3 新增字段的 `display_order` 按初始化 DML 中各 Schema BUSINESS 最大序号静态写入 DML（与法务修订脚本同为静态行），没有在执行时计算；停止条件 2 与"复制取线上字段"保证与线上 v2 对齐。若线上 v2 的 BUSINESS 序号与初始化 DML 不同，只影响新字段在分区内的排序，阶段 6 的 mor-test 试跑会比对。
- D-3：DML 停止条件 5 的实现为：本 ID 段内的已有行须与本脚本同 ID 同业务键，且业务唯一键不得被段外行占用。
- D-4：AC-06 没有调用被测算法：用例按审核端读到的 values 加 RFC 8785 + SHA-256 独立复算，并证明去掉四键后摘要不同。审核读取响应会省略个人申请的 `organization` 分区，复算时按全空分区补回。
- D-5：测试全局 BeforeEach 预设 commercial 4 个 ACTIVE 支付方式（与 Platform 参考数据基线同理），零调用与故障场景在用例内断言。

### 遗留 / 需主会话知悉

1. commercial 故障时同一次 form-schema 对 commercial 的调用不止一次：4 种故障共 10 次调用，用例耗时约 5 秒。推测是出站传输对 GET 的重发；生产配置未改，用例只断言"每种故障至少一次调用且 503"。是否限制重发、是否接受 commercial 不可用时 form-schema 的延迟，请在阶段 5 / 7 观察。
2. 存量缺口 F-07（`t_client_business_profile.content_hash` 无写入路径）未修，按 S-08 另议。
3. `ClientReviewRegistrationClientImpl` 送 compliance 的经营资料未加三项（S-10，随 REQ-03 定）。
4. 阶段 5：`mor_client_master_v2e2e` 须先补跑法务修订 DML（O-4），再执行本需求 DDL + DML；本机 e2e 换 JAR 时依赖 commercial-service 可达。
