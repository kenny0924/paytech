# 2026-10-22 决定：前端界面文案由 Platform 维护，发布为不可变语言包

- Decision status: Confirmed
- Implementation status: In progress（需求 [platform-support：多语言管理（前端 UI 文案）](../../requirements/20261022/platform-support-多语言管理/requirement.md) 研发中）
- Deployment status: Not deployed
- Decision date: 2026-10-22（用户）
- Supersedes / Superseded sections：第 8 条中 `operations-console-web` "既有页面不迁移、不提供语言切换入口"与已知限制"ops-web 中英混排"由文末 [2026-10-23 补充](#2026-10-23-补充operations-console-web-全量接入) 取代；其余不变。此前无。此前系统没有多语言基础设施；CCT 法律文档与 Platform 通知模板各自保存的 BCP 47 `locale` 字段不受本决定影响，是否改为引用 Locale 目录另立需求。
- 详细材料：[需求](../../requirements/20261022/platform-support-多语言管理/requirement.md)（第 8 节 C1—C9、A1—A4）、[技术方案](../../requirements/20261022/platform-support-多语言管理/technique.md)（第 9 节 S1—S9）、[确认记录](../../requirements/20261022/platform-support-多语言管理/delivery.md)。
- 实现、验证与部署状态只见[当前台账](../CURRENT_IMPLEMENTATION.md)。

## 背景

2026-10-22 全仓库核对时，系统没有任何多语言基础设施：没有 `Accept-Language` 处理，没有 `MessageSource`，前端没有 i18n 库。`operations-console-web` 把 antd 写死为 `zh_CN`，约 24k 处中文硬编码，枚举 Code 到中文名称的映射散落在页面中；`client-console-web` 约 1.4k 处中文硬编码。各端也没有统一的"系统支持哪些语言"目录。

业界做法（Crowdin Distribution、Tolgee Content Delivery、Lokalise OTA）的共同点是：key 和基准文案由代码产生，翻译在后台维护，发布为不可变版本，客户端拉取静态 JSON，运行时不逐 key 查库。本决定按该模式在 Platform 内自建，不引入 Tolgee、Weblate 等独立产品，因为它们需要独立数据库与界面，审批和审计会脱离 Platform 体系。

## 决定

1. **三类文案分开治理：**
   - **前端界面文案：** 页面上的按钮、标题、提示、枚举展示名等静态文案。本决定只落地这一类。
   - **服务端报错与校验文案：** 服务端产生、经 Problem `detail` 或字段错误返回给人看的文字。另立需求；本期 Problem 合同不变，Platform 多语言模块的存储与发布模型为它预留。
   - **业务内容：** 运营录入的产品名、模板名、法律文档等业务数据。另立需求，不进入本模块。
2. **归属（C1）：** Platform Support 拥有 Locale 目录、应用、命名空间、文案与发布。多语言是 Platform 的业务模块 `paytech-platform-support-localization`（包根 `com.paytech.platform.support.localization`），表放 `mor_platform`，由 Platform 进程承载，不独立部署。运营在 Operations Console 维护，权限分查看、编辑、发布三项。
3. **key 来自代码（C2、C9）：**
   - 开发在代码旁维护英文基准文件，经控制台上传导入；导入先预览差异（新增、基准变更、将废弃、恢复在用），确认后生效。首期不做 CLI 或 CI 推送（C6）。
   - 控制台不能新建或删除 key。导入代表应用的完整 key 集合，文件里没有的 key 标为废弃，不物理删除。
   - 命名空间由运营在后台创建和查看，导入不创建命名空间；文件中出现未创建的命名空间时拒绝导入。
4. **语言范围（C3）：** 基准语言全系统固定为 `en`，不按应用配置。本期只登记并启用 `en`、`zh-CN`；Locale 目录保留文字方向字段，不实现 RTL 布局，不登记其他语言。
5. **无审核直接发布（C4）：** 翻译没有审核状态，保存即可发布；不做 Maker-Checker，不做多条发布线。保存与导入时校验 ICU MessageFormat 语法、参数集合与基准一致、`plural`/`select`/`selectordinal` 含 `other` 分支，失败不能保存。发布前按应用配置的必须覆盖语言与覆盖率阈值（默认 100%，C8）检查，不足则拒绝发布。
6. **发布与回滚（S1、S8）：**
   - **不可变：** 每次发布冻结当时全部在用 key 与各语言已填写的文案，按"语言 × 命名空间"生成 JSON 语言包。发布后内容、文件与摘要不再改变，修正文案必须新建发布。
   - **公开桶：** 语言包以公开访问级别经文件服务写入公开桶，对象键带发布号与摘要，每个文件只写一次。数据库只存文件地址 `/files/v1/{fileId}` 与内容摘要，不存语言包正文，符合[文件上传与地址原则](./2026-10-22-file-upload-address-principles.md)；公开访问地址由文件服务在读取时计算。
   - **自动上线：** 文件服务对语言包完成检查后，补偿事件把发布标为可用，并自动把应用的当前发布指向它；任一文件被拒绝时发布标为失败，不上线。
   - **回滚：** 每个应用只有一个当前发布指针（C7）。切回任一可用的历史发布即为回滚，不重新生成文件，并写审计。
   - **跨环境：** 发布记录属于各环境自己的 Platform，mor-test 与生产各自发布，不做跨环境同步。
7. **前端加载与回退（C5、S5）：**
   - **基准内置：** 英文基准文件在构建时打进前端产物。
   - **运行时加载：** 启动时经本应用 BFF 匿名读取 manifest，再从公开地址按命名空间直接拉取语言包，语言包不经 BFF 与 Platform。Checkout 首期只用构建内置文案。
   - **回退顺序：** 运行时目标语言 → 运行时 `en` → 构建内置 `en` → key 本身（只在开发环境告警）。manifest 或语言包读取失败时只用内置英文，不阻塞首屏。
   - **接入约定：** 统一写法、key 命名与硬编码检查见 [`DEVELOPMENT_CONVENTIONS.md` 第 10 节](../DEVELOPMENT_CONVENTIONS.md#10-前端界面文案与语言)。需求 R8 原写的 ESLint 规则改为 node 测试（S5），因为两个前端工程都没有 ESLint。
8. **首期接入范围（R9）：** `client-console-web` 全量接入并提供 `en` 与 `zh-CN`；`operations-console-web` 首期只有本需求新增的多语言页面按新约定编写（2026-10-23 起全量接入，见文末补充）；`checkout-web`、`partner-console-web` 只登记应用。本期不建各端共享的 `shared` 应用（S4），枚举展示名放在各应用自己的 `enum` 命名空间。
9. **新依赖：**
   - `intl-messageformat`：client-console-web 与 operations-console-web 的 i18n 运行时，用于解析 ICU 文案，见 [`TECHNOLOGY_STACK.md`](../TECHNOLOGY_STACK.md) 第 3 节前端例外登记。
   - ICU4J：只用于 Platform 多语言模块校验 ICU 语法，不在服务端格式化文案，版本由统一 BOM 管理（S2）。

## 已知限制

- **报错仍为中文：** 服务端 Problem `detail` 本期仍是中文。英文界面下，后端返回的报错与校验提示仍显示中文，待服务端文案需求处理。
- **ops-web 中英混排（已解决）：** 首期 `operations-console-web` 只有新页面 key 化；2026-10-23 全量接入后不再存在，见文末补充。
- **孤儿语言包：** 发布登记失败时已上传的语言包成为不被引用的公开小文件，不影响正确性，由文件服务的通用孤儿清理处理，本需求不做（S9）。

## 影响

- **Platform：** 新增多语言模块、7 张表、内部接口、文件原因码与检查策略、补偿事件及审计事件；发布依赖[文件公开桶](../../requirements/20261022/platform-support-文件公开桶/requirement.md)先上线。
- **BFF：** operations-console-bff 增加管理转发与匿名 manifest 转发；client-console-bff 增加匿名 manifest 转发。BFF 只做转发，把应用编码固定为自身应用。
- **Gateway：** 只增加路由数据，manifest 路由为 `PUBLIC`，不改代码。
- **前端：** 两个 web 各自新增 `src/i18n/` 运行时与英文基准文件；公开桶需允许各 Console 域名跨域读取，属于环境配置。
- **规范：** [`DEVELOPMENT_CONVENTIONS.md`](../DEVELOPMENT_CONVENTIONS.md) 新增第 10 节；[`TECHNOLOGY_STACK.md`](../TECHNOLOGY_STACK.md) 登记 ICU4J 与 `intl-messageformat`；根 `AGENTS.md` 增加路由。
- **后续：** 服务端报错文案、业务内容多语言、账号偏好语言与登录态 locale 解析、既有 ops-web 页面迁移、Checkout 与 Partner Console 接入，均另立需求。

## 2026-10-23 补充：operations-console-web 全量接入

依据：需求 [operations：运营控制台多语言迁移](../../requirements/20261023/operations-控制台多语言迁移/requirement.md) 与[技术方案](../../requirements/20261023/operations-控制台多语言迁移/technique.md)（S1—S6，用户 2026-10-23 确认）。本补充取代第 8 条与已知限制中关于 `operations-console-web` 的首期限制，其余条款不变。

1. **全量接入：** `operations-console-web` 全部既有页面、弹窗、抽屉、菜单、面包屑、表头、按钮、空状态、提示、确认框、表单校验与通知标题改为 key；按模块划分 21 个命名空间（`common`、`auth`、`nav`、`enum`、`access`、`gateway`、`channel`、`commercial`、`finance`、`recon`、`risk`、`workflow`、`data-query`、`file`、`notification`、`webhook`、`vault`、`card`、`client-review`、`currency`、`i18n-admin`）。`zh-CN` 翻译取迁移前的原中文，切换到简体中文后界面不变。
2. **默认英文与语言切换：** 默认语言为 `en`，不读浏览器语言；页头用户菜单与登录页提供语言切换，只列 manifest 已发布的语言，少于两种时隐藏。选择保存在浏览器本地；切换整页刷新并回到原路由，antd、dayjs、日期与数字格式和 `<html lang>` 一并切换。启动时根部一次加载全部命名空间。
3. **本地缓存：** 用过的 manifest 与语言包缓存在浏览器 `localStorage`，启动时有缓存即直接渲染、不等网络，后台核对新发布并替换；manifest 或公开桶不可用时继续用缓存，无缓存时退回内置英文（第 7 条回退顺序不变）。缓存只放公开语言包，`?release=` 预览不读写缓存。`client-console-web` 暂未接入缓存，后续接入时复用同一实现。
4. **菜单与权限名称：** 菜单、面包屑、页面标题与角色权限树按资源编码取 `nav.resource.<CODE>`，`zh-CN` 等于 Platform `display_name`；没有 key 时回退 `display_name`。Platform 菜单数据不改；"菜单与按钮""权限目录"配置页原样显示 Platform 数据。
5. **BFF 展示文案改编码：** operations-console-bff 不再返回中文展示名：权限树节点带资源或权限编码（"其他权限"分组为 `OTHER_PERMISSIONS`），审计详情只返回字段 key，定价试算说明改为 `notes: [{code, params}]`，由前端翻译。BFF 的校验信息与 Problem `detail` 不变（仍属服务端报错文案的另立需求）。
6. **禁中文守卫：** `src/i18nSourceGuard.test.ts` 对全部 `src` 生产源码（注释与测试除外）严格执行，出现中日韩字符即失败；唯一白名单是 Platform 注册数据镜像 `navigation/pageCapabilities.ts`。之后新增或修改的界面文案必须先加英文基准 key 与 `i18n/zh-CN/<namespace>.json` 中文种子，再在多语言管理中导入、翻译并发布；写法见 [`DEVELOPMENT_CONVENTIONS.md` 第 10 节](../DEVELOPMENT_CONVENTIONS.md#10-前端界面文案与语言)。
