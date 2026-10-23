# operations：运营控制台多语言迁移 · 技术方案

版本：v2 · 2026-10-23（S1—S6 已答复） · 阶段 2—3（现状差异、影响、方案、原型）。需求见 [requirement.md](./requirement.md)（v1 已确认，Q1—Q7 按建议），英文用词见 [glossary.md](./glossary.md)。

基线：main `9f210b5c`（Q7：code-mor-55 的数据查询改动已原样提交）。研发目录：`/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n`（detached，基于该提交）。

## 1. 现状差异

| 项 | 现状 | 目标 | 差距 |
|---|---|---|---|
| 页面文案 | 287 个文件、约 9,900 处中文字面量，去重约 7,200 条（按命名空间计） | 全部为 key，`en` 基准 + `zh-CN` 翻译 | 全量改写 |
| 模块级常量 | 160 个文件中有 357 个顶层常量块含中文（标签映射、列定义、选项） | 常量只存 key，渲染时翻译 | 改写为 key 映射或组件内构造 |
| 带插值文案 | 约 860 处模板字符串 | ICU 参数 | 改写，`en` 加复数规则 |
| 菜单、面包屑、页面标题 | 直接显示 Platform `display_name`（82 个菜单，中文） | 按资源编码取 key，缺 key 回退 `display_name` | 前端改；`nav` 命名空间 |
| 权限树（角色编辑） | BFF 节点只有 `nodeId=resource:<id>` 与中文名；"其他权限"写死在 BFF | 节点带资源编码或权限编码 | BFF 透传 Platform 已返回的 `resourceCode`，前端翻译 |
| 审计详情字段名 | BFF 用中文映射表生成 `label`，同时返回字段 `key` | 前端按 `key` 翻译 | BFF 删除映射表与 `label`，前端改 |
| 定价试算说明 | BFF 拼接中文 `note` 字符串（9 种） | 结构化说明 | BFF 改为 `notes: [{code, params}]`，前端翻译 |
| 语言 | antd 固定 `zh_CN`，dayjs 未设语言，无切换入口，运行时 `document: null` | 页头与登录页可切换，antd / dayjs / `<html lang>` 跟随 | 新增 |
| 运行时 | 仅多语言三页用 `I18nGate`，默认 `zh-CN`、不读浏览器语言 | 应用根部 Gate，登录前即加载 | 扩大覆盖面 |
| 测试 | 79 个测试文件约 1,700 处中文断言；多语言模块有禁中文扫描 | 全 `src` 禁中文扫描；断言 key 或英文 | 改写测试 |
| Platform | 多语言模块已上线；`operations-console` 有 `i18n-admin`、`enum` 两个命名空间、409 key | 新增约 19 个命名空间、约 7,500 key | 只需运营数据，代码不改 |

容量核对：单条文案上限 8000 字符、key 上限 191 字符、命名空间编码 `[a-z][a-z0-9-]{0,63}`、导入文件上限 2 MiB、单语言包 1 MB、批量保存每次 200 条，均够用。最大命名空间 `channel` 约 1,060 key，单个语言包约 80—100 KB。

## 2. 逐系统影响

| 系统 | 改动 | 合同影响 |
|---|---|---|
| operations-console-web | 全量 key 化；运行时与语言切换；antd / dayjs；`nav` 与模块命名空间；测试改写；全量禁中文扫描；导出脚本 | 无对外合同 |
| operations-console-bff | `PermissionNode` 增加 `code`；删除审计中文映射与 `label`；试算说明改为结构化 | BFF ↔ ops-web 内部合同，同批发布 |
| Platform Support | 无代码改动；mor-test 上新建命名空间、导入、发布（运营数据） | 无 |
| Platform 注册数据 / Gateway | 无（manifest 为公开路由，登录前可用） | 无 |
| 文档 | 决定 `2026-10-22-ui-text-localization.md` 与开发约定第 10 节写明 ops-web 全量接入及禁中文规则；`CURRENT_IMPLEMENTATION.md` | — |

数据库：**不涉及**（无 DDL、无注册 DML）。首批翻译导入与发布是通过现有接口的运营操作，不写 SQL。

## 3. 方案

### 3.1 命名空间

| 命名空间 | 范围 | 去重文案 |
|---|---|---|
| `common` | 根组件、通用组件、错误通知、加载 / 空状态、通用按钮（保存、取消、重试…） | ~70 + 通用词 |
| `auth` | 登录、强制改密、会话恢复、语言切换 | ~35 |
| `nav` | 菜单 / 按钮资源名 `nav.resource.<CODE>`、侧栏与面包屑（Platform 权限没有名称，未挂资源的权限显示编码，不建 key） | ~300 |
| `enum` | 跨模块共用的枚举（已有，保留） | 现有 + 少量 |
| `access` | 人员、角色、权限目录、审计、租户访问 | ~350 |
| `gateway` | API 路由、菜单与按钮、API Scope、API 凭证、网关配置组件 | ~210 |
| `channel` | 渠道、MID、渠道成本、标准 API、外部 API 组件 | ~1,060 |
| `commercial` | 商务全部页面 | ~720 |
| `finance` | 财务（不含对账） | ~740 |
| `recon` | 对账 8 页 | ~490 |
| `risk` | 风控全部页面 | ~1,050 |
| `workflow` | 工作流 5 页与编辑器 | ~910 |
| `data-query` | 数据源、查询模板 | ~320 |
| `file` | 存储管理、文件记录 | ~350 |
| `notification` | 通知 3 页 | ~230 |
| `webhook` | Webhook 管理 | ~130 |
| `vault` | 数据保护 6 页 | ~150 |
| `card` | 卡 BIN 库 | ~130 |
| `client-review` | 客户入驻审核 | ~160 |
| `currency` | 币种 | ~30 |
| `i18n-admin` | 多语言 3 页（已有，不动） | 372 |

模块私有的枚举放在本模块命名空间（`<ns>.enum.<Type>.<VALUE>`），只有跨模块使用的放 `enum`，避免并行研发时争用同一文件。

### 3.2 写法约定（研发硬性规则）

- **key：** `<ns>.<页面或区域>.<名称>`，小驼峰段名，例如 `finance.adjustments.column.amount`、`recon.exceptions.action.resolve`。同字不同义用不同 key；`common` 只放真正通用的词。
- **组件内：** `const t = useT('finance')`；**非组件代码**（模型、校验函数）用 `translator('finance')` 在调用时取值，不在模块加载时取值。
- **常量：** 顶层常量只存 key（如 `{ DRAFT: 'finance.status.draft' }`），渲染时 `t(key)`；表格列、选项等含文案的结构放进组件或 `useMemo`。
- **插值：** ICU 参数，`t('recon.lines.count', { count })`；`en` 用 `{count, plural, one {# line} other {# lines}}`，`zh-CN` 用 `共 {count} 条`。不拼接翻译片段。
- **枚举：** 新增 `enumLabel(t, prefix, value)` 小工具：有 key 显示翻译，没有则显示原始值。
- **中文原文：** 每个新 key 记录迁移前的原中文，写入 `i18n/operations-console.zh-CN.seed.json`；这就是 `zh-CN` 翻译，逐字不改（R2）。
- **不翻译：** 运营录入的业务数据、编码、ID、金额、`pageCapabilities.ts`（Platform 注册数据镜像，保留中文 `displayName`，扫描白名单只此一个文件）、`workflow/formModel.ts` 的拼音转换（处理用户输入的中文字段名）。

### 3.3 运行时与语言切换

- `main.tsx` 在渲染前启动运行时，应用根部用 `I18nGate` 一次加载**全部** ops 命名空间（有本地缓存时直接用缓存，不等网络），登录页与控制台都在 Gate 内。所有 key 都在首屏就绪，避免切页时闪烁。预估 `zh-CN` 语言包总计约 450 KB（gzip 后约 120 KB），从公开桶并行拉取并长期缓存。
- 内置英文 `builtin.ts` 静态引入全部 `en/*.json`（JS 包增加约 500 KB，gzip 约 110 KB），这是 Platform 或公开桶不可用时的回退。
- 运行时配置改为 `publishedOnly: true`（只列已发布语言）并传入 `document`（更新 `<html lang>`）。**默认语言 `en`**，不读浏览器语言；来源顺序 `?lang=` > 本地选择（`localStorage` 的 `i18n.locale`）> `en`。
- **本地缓存（S3 答复"切换后存本地，从本地加载"）：** 运行时新增可选 `cache`（ops 开启）。每次成功取到 manifest 与语言包后写入 `localStorage`：manifest 按 `应用 + 语言` 存一份，语言包按其不可变 URL（含发布号与摘要）存，只保留当前 manifest 引用的条目，超出配额时清空缓存并继续运行。启动时若本地有所选语言的 manifest 与语言包，**立即用缓存完成启动**，Gate 不等待网络；随后后台取 manifest，摘要变化则加载新语言包并替换（页面文字即时更新）。manifest 或公开桶不可用时继续用缓存；无缓存时按 B3 回退内置英文。`?release=` 预览不读写缓存。缓存只放公开语言包，不含任何用户数据。
- **切换即整页刷新：** 选择语言后先在新语言下取 manifest 与语言包写入缓存（失败也继续），再写入本地选择并 `location.reload()`；刷新后从缓存直接启动。antd `ConfigProvider`（`en_US` / `zh_CN`）、dayjs 语言、模块级派生数据都在启动时按当前语言一次确定；页面状态以 URL 为准，刷新后回到同一路由（AC-13）。
- **入口：** 页头右侧账号邮箱左边放语言按钮（地球图标 + 当前语言名，下拉列 English / 简体中文，当前项打勾）；登录页右上角放同一组件。无发布或只发布一种语言时隐藏。

### 3.4 菜单、面包屑、权限

- `navigation/menu.ts` 生成菜单项时 `label = tOr('nav.resource.' + resourceCode, displayName)`；面包屑、页面 `<title>`、菜单搜索使用同一 label。
- `nav` 的 key 由 `pageCapabilities.ts`（页面 `componentKey` 与动作 `resourceCode`）加父级菜单编码生成，`zh-CN` 种子取其中文名；导出脚本校验两者集合一致，防止新增页面漏 key。
- 角色编辑权限树：BFF `PermissionNode` 新增 `code`（资源节点为 `resourceCode`，未挂资源的权限为 `permissionCode`，"其他权限"分组为 `OTHER_PERMISSIONS`），前端资源节点取 `nav.resource.*`，权限节点显示编码，分组取 `access.permissionTree.other`，缺 key 回退 `displayName`。
- "菜单与按钮""权限目录"两个配置管理页显示和编辑的是 Platform 中的数据，原样显示 `display_name`，只翻译页面框架。

### 3.5 BFF 改动

| 位置 | 改动 | 测试 |
|---|---|---|
| `RolePermissionTree` / `RoleModels.PermissionNode` / `PermissionResource` | 透传 `resourceCode`，节点加 `code`；去掉中文分组名，`displayName` 对分组返回编码 | `RolePermissionTreeAutoTest` 等 |
| `AuditEventDetailsProcessor` | 删除中文 `LABELS` 与 `AuditChange` / `AuditProperty` 的 `label` 字段 | 审计详情测试 |
| `CommercialFeeIllustrator` 及试算响应模型 | `note` 字符串改为 `notes: [{code, params}]`，不支持原因改为 `{code, params}`；code 取值 `BAND_HIT`、`BAND_NONE`、`PROGRESSIVE_BANDS`、`MIN_APPLIED`、`MAX_APPLIED`、`BASE_DEPENDS_ON_FEE`、`COMPONENT_UNSUPPORTED`、`BAND_BASIS_PERIODIC`、`BAND_MODE_UNSUPPORTED` | 试算测试 |

校验信息（约 640 处）、错误码文案与 Problem `detail` 不动（需求不做项）。

### 3.6 测试

- 测试统一以英文运行：测试辅助先 `i18n` 初始化为内置 `en`，断言改为英文或 key。纯逻辑测试中作为业务数据的中文（如用户录入名称）保留。
- 全量扫描测试 `src/i18nSourceGuard.test.ts`：遍历 `src/**/*.ts(x)`（测试文件除外），去掉注释后不得含汉字；白名单仅 `navigation/pageCapabilities.ts`。替代多语言模块内的局部扫描。
- 一致性测试：每个 `en` key 在种子中有 `zh-CN`；代码中引用的 key 都在 `en` 中存在（静态扫描 `t('…')` 字面量）；`nav` key 与 `pageCapabilities` 一致。
- 中文零变化证据（AC-01）：阶段 5 在本机、阶段 7 在 mor-test，用 Playwright 逐个菜单页（含主要弹窗）抓取迁移前与迁移后 `zh-CN` 的可见文本，逐页比对；数据驱动的差异人工判读。另截全部页面英文截图（1440、1280 宽）检查溢出（AC-12）。

### 3.7 发布（阶段 6—7 细化）

只发布 ops-bff 与 ops-web 两个镜像。顺序：先在 mor-test 用现有接口为 `operations-console` 新建命名空间、导入 `en` key 与 `zh-CN` 翻译、发布并等待 READY（旧 ops-web 不读这些 key，无影响）→ 替换 ops-bff → 替换 ops-web。若先换 ops-web 而语言包未就绪，中文用户会看到英文。回滚：恢复两个镜像；语言包发布保留（多出的 key 不影响旧版本）。

## 4. 研发安排

阶段 4 由执行代理按卡实施，主会话复核。卡 F0 先完成并合入研发目录，其余卡并行，每卡只改本卡模块文件与本卡命名空间：

| 卡 | 内容 |
|---|---|
| F0 基础 | 运行时接线、根部 Gate、语言切换（页头、登录页）、antd / dayjs、`common` / `auth` / `nav`、菜单与面包屑、`enumLabel`、测试辅助、扫描测试（先报告模式）、导出脚本、BFF 三处改动 |
| M1 | `finance` + `recon` |
| M2 | `risk` |
| M3 | `workflow` |
| M4 | `channel` |
| M5 | `commercial` |
| M6 | `access` + `gateway` + `client-review` + `currency` + `card` |
| M7 | `data-query` + `file` + `notification` + `webhook` + `vault` |
| F9 收尾 | 扫描测试切为强制、英文用词按术语表统一复核、英文布局修正、全量测试与构建 |

冻结（Q6）：研发到合入期间，其他会话不改 ops-web。

## 5. 原型

只新增语言切换界面，其余页面布局不变。Pencil `operations-console-web/docs/operations-console.pen`，y=90400 一行三张，导出在 [assets/prototype](./assets/prototype/)：

| 画板 | 内容 |
|---|---|
| [10 页头语言切换](./assets/prototype/10-header-language-zh.png) | 中文界面，应用栏右侧"环境标记 · 语言按钮 · 账号 · 退出"；下拉标题"界面语言"，English / 简体中文（附语言代码），当前项打勾，底部提示"切换后页面会重新加载，停留在当前页面" |
| [11 英文界面示例](./assets/prototype/11-english-releases.png) | 多语言 › 发布记录的英文版：导航、页头、表头、状态、进度、摘要全部英文；表格里的发布说明与人员名是录入数据，保持原文 |
| [12 登录页](./assets/prototype/12-login-language.png) | 登录页右上角同款语言按钮（英文状态），表单文案为英文 |

英文版侧栏最长项（Platform Settings、Languages & Apps）在现有 212 px 宽度内不折行；菜单宽度不需要调整。

## 6. 验收映射

| AC | 验证方式 |
|---|---|
| AC-01 | 3.6 中文可见文本逐页比对（本机 + mor-test）；其他需求的中文选择器验收脚本抽样回归 |
| AC-02、AC-04、AC-05、AC-06 | 英文逐页 Playwright 遍历：可见文本不含汉字（业务数据与错误详情除外，按白名单区域判读）；权限树、审计详情、枚举专项用例 |
| AC-03、AC-13 | 浏览器用例：切换、刷新、日期选择器与分页文案 |
| AC-07 | 在多语言管理中改一条 ops 文案并发布，刷新后可见；切回 |
| AC-08 | 本机屏蔽 manifest 与公开桶请求后加载控制台 |
| AC-09 | `npm test`、`build`、ops-bff 测试 |
| AC-10 | mor-test 发布记录 READY、覆盖率 100% |
| AC-11、AC-12 | 原型对照、英文 1440 / 1280 截图检查 |

## 7. 已确认（2026-10-23）

| 编号 | 结论 |
|---|---|
| S1 | 切换语言后整页刷新，回到原路由 |
| S2 | 启动时一次加载全部命名空间 |
| S3 | **默认英文**；切换后选择存本地，下次从本地加载（含本地缓存的语言包，见 3.3） |
| S4 | "菜单与按钮""权限目录"配置页原样显示 Platform 数据 |
| S5 | `pageCapabilities.ts` 保留中文名，列为扫描唯一白名单 |
| S6 | 按第 4 节分卡，多代理并行；F0 先行 |

运行时 `src/i18n/runtime.ts` 的本地缓存只改 ops-web 副本（client-web 副本暂不同步，记为遗留：client-web 后续接入缓存时复制同一实现）。
