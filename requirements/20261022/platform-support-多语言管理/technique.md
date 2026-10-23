# platform-support：多语言管理（前端 UI 文案）· 技术方案

> 状态：**阶段 2—3 评审稿 v3**（v3：命名空间改为后台创建与查看，7 张表、24 个接口）。原 v2 说明：**阶段 2—3 评审稿 v2**（2026-10-22）。v1 的 S2—S7 已按建议确认；S1 按用户意见改为"语言包文件存公开桶，库里只存文件地址"，本稿据此修改第 2、3.2、3.3、3.7—3.9、4—9 节。需求见 [requirement.md](./requirement.md)（v2），确认记录见 [delivery.md](./delivery.md)。

**前置依赖：** [文件公开桶与固定访问地址](../platform-support-文件公开桶/requirement.md)（阶段 4 研发中）。本需求的发布依赖其公开桶、`publicUrl` 与按版本扫描能力，必须在它之后上线。

## 1. 现状差异

| 需求项 | 当前行为及证据 | 目标行为 | 缺口 | 处理 |
|---|---|---|---|---|
| R1 Locale 目录 | 无。CCT 法律文档与 Platform 通知模板各自存 BCP 47 字符串，没有受控目录（`client-master-schema.sql`、`platform-support-schema.sql` `t_notification_template_content.locale`） | Platform 维护启用的 locale 与文字方向 | 未实现 | 新表 `t_i18n_locale`；既有 locale 字段暂不改为引用（另立） |
| R2 应用与命名空间 | 无 | 4 个应用，各有默认语言、必须覆盖语言、阈值、当前发布；命名空间在后台创建、查看、维护名称与说明 | 未实现 | 新表 `t_i18n_application`、`t_i18n_namespace` |
| R3 key 导入 | 前端文案直接写在 tsx 里（ops-web 约 24k 处中文、client-web 约 1.4k 处） | 代码旁 `en` 基准 JSON，经文件服务上传后预览差异再确认 | 未实现；Gateway 请求 Body 上限 65536 字节，整应用 JSON 可能超过 | 走文件上传（第 3.5 节），不走请求 Body |
| R4 翻译维护 | 无 | 工作台筛选、编辑、批量保存、ICU 校验、JSON 导出 / 导入 | 未实现；后端无 ICU 解析能力 | 新表 `t_i18n_key`、`t_i18n_translation`；ICU4J 校验（S2） |
| R5—R6 发布与回滚 | 无 | 不可变发布、语言包文件、manifest、指针回滚 | 未实现；私有文件只能签 60 秒临时地址，不能长期缓存 | 语言包文件写公开桶，库里存文件地址；新表 `t_i18n_release`、`t_i18n_release_bundle`；文件检查通过后自动上线 |
| R7 交付接口 | Gateway 已支持 `PUBLIC/PUBLIC` 路由（Operations 登录路由即此形态，见 `platform-support-initial-data.sql`） | 各 Console 经自己 BFF 匿名读 manifest；语言包由浏览器直接从公开地址拉取 | Platform、ops-bff、client-bff 均无端点；公开桶尚未上线 | 新增 1 个内部只读接口，两个 BFF 各转发 1 条；语言包不经 BFF |
| R8 前端基线 | ops-web 写死 antd `zh_CN`；client-web 无 antd、无 i18n；两者均无 ESLint | 运行时加载、内置回退、语言切换、禁止硬编码中文 | 未实现 | 各 web 新增 `src/i18n/` 小运行时；硬编码检查改为 node 测试（S5） |
| R9 client-web 接入 | 30 个源文件约 5.6k 行，中文文案硬编码 | 全部页面 key 化，`en` 基准、`zh-CN` 翻译 | 未实现 | 全量改写，中文原文作为 `zh-CN` 首批翻译导入 |
| R10 决定与规范 | 开发约定第 4 节只规定异常中文消息 | 三类文案治理边界、前端文案约定 | 无 | 新决定记录 + 开发约定新节 + AGENTS 路由 |

## 2. 系统影响

| 系统 / 仓库 | 职责 | 影响点 | 改动 | 依赖与风险 |
|---|---|---|---|---|
| Platform Support | Locale 目录、文案与发布的 Owner | 新模块 `paytech-platform-support-localization`；7 张表；24 个内部接口；文件原因码 3 个（2 个导入、1 个语言包）；补偿事件 1 个；审计事件 2 类 | 代码 + PDManer + Schema + 增量 DDL | 复用 file-document 的上传、读取与公开地址；依赖文件公开桶；ICU4J 进 BOM |
| operations-console-bff | Operations 管理页适配 | 23 条管理转发 + 1 条匿名 manifest 转发；文件上传沿用既有 BFF 直调文件服务 | 代码 | 无业务编排 |
| client-console-bff | Client 端交付 | 1 条匿名 manifest 转发 | 代码 | 无 |
| api-gateway | 无代码改动 | 路由来自 Platform 注册数据 | 不改代码 | manifest 路由用 `PUBLIC/PUBLIC`，`ETag` / `If-None-Match` 需原样透传 |
| 存储与 CDN（环境配置） | 公开桶对外地址 | 语言包公开桶需允许各 Console 域名跨域 GET；对象设置长期缓存 | 环境配置，不改代码 | 本地 MinIO 与 mor-test 各配一次；生产 CDN 另行配置 |
| Platform 注册数据 | 权限、菜单、路由、组件 | 新块（拟 135，使用前向其他会话登记） | DML | 块 134 已被文件公开桶占用 |
| operations-console-web | 管理页面 | 新增 `src/localization/` 4 个页面；新增 `src/i18n/` 运行时，只用于新页面 | 代码 | 既有页面不迁移；新页面需英文基准 + 中文翻译 |
| client-console-web | 首个全量接入方 | 新增 `src/i18n/` 运行时与 `i18n/en/*.json`；全部页面 key 化；语言切换入口 | 代码 | 改动面覆盖所有页面，回归测试量大 |
| checkout-web / partner-console-web | 未实现 | 只登记应用 | 仅 DML | 无 |
| agent-docs | 规范 | 决定记录、开发约定、AGENTS、TECHNOLOGY_STACK 前端例外登记 | 文档 | 无 |

## 3. 方案

### 3.1 模块与分层

新模块 `paytech/platform-support/paytech-platform-support-localization`，结构与 data-query 相同：

```text
com.paytech.platform.support.localization
├── core/{configuration,enums,dal/{dataobject,mapper}}
├── domain/          # 发布计算、ICU 校验、差异计算等纯 Java 对象
├── domainservice/{service,repository,validator,convert}
└── integration/     # 读取已上传文件内容（调用 file-document 进程内服务）
```

Processor 与 Controller 放 `paytech-platform-support-app`，合同放 `paytech-platform-support-api` 的 `localization` 包。全部写 Processor 用 `AbstractLockProcessor`，锁资源为应用编码；数据库侧用 `version` CAS 兜底。

### 3.2 数据模型（`mor_platform`，7 张表）

| 表 | 关键字段 | 唯一约束 | 说明 |
|---|---|---|---|
| `t_i18n_locale` | `locale_id`、`locale_code` VARCHAR(35)、`native_name`、`english_name`、`text_direction`（LTR / RTL）、`status`（ENABLED / DISABLED）、`sort_order`、`version`、审计四列 | `uk_i18n_locale_code` | 初始数据只有 `en`、`zh-CN`。`en` 不可停用 |
| `t_i18n_application` | `application_id`、`application_code`、`application_name`、`default_locale`、`required_locales` JSON、`coverage_threshold_percent` INT、`current_release_id` NULL、`key_set_revision` INT、`content_revision` INT、`status`、`version`、审计四列 | `uk_i18n_app_code` | `default_locale` 取代需求中的"渠道默认语言"：应用与渠道一一对应，不另建渠道表 |
| `t_i18n_namespace` | `namespace_id`、`application_id`、`namespace_code` VARCHAR(64)、`namespace_name` VARCHAR(64)、`description` VARCHAR(512) NULL、`sort_order` INT、`version`、审计四列 | `uk_i18n_namespace`(`application_id`,`namespace_code`) | 后台创建；编码创建后不可改；还有在用或已废弃 key 时不能删除 |
| `t_i18n_key` | `key_id`、`application_id`、`namespace_id`、`key_code` VARCHAR(191)、`source_text` TEXT、`description` VARCHAR(512)、`source_revision` INT、`status`（ACTIVE / DEPRECATED）、`version`、审计四列 | `uk_i18n_key`(`namespace_id`,`key_code`) | `key_code` 为完整 key（如 `team.invite.submit`），必须以 `命名空间编码.` 开头 |
| `t_i18n_translation` | `translation_id`、`key_id`、`locale_code`、`text` TEXT、`based_on_source_revision` INT、`version`、审计四列 | `uk_i18n_translation`(`key_id`,`locale_code`) | 不存状态。"基准已变更"= `based_on_source_revision < key.source_revision`，查询时计算 |
| `t_i18n_release` | `release_id`、`application_id`、`release_no` INT、`based_on_content_revision` INT、`status`（PENDING_CHECK / READY / FAILED）、`key_count`、`coverage` JSON、`report` JSON、`release_note` VARCHAR(512)、`failure_reason` VARCHAR(512) NULL、`ready_time` NULL、`created_by`、`create_time`、`update_time` | `uk_i18n_release`(`application_id`,`release_no`) | 文案内容只增不改；只有状态从 PENDING_CHECK 走到 READY 或 FAILED |
| `t_i18n_release_bundle` | `bundle_id`、`release_id`、`locale_code`、`namespace_code`、`file_address` VARCHAR(64)（`/files/v1/{fileId}`）、`content_hash` CHAR(64)、`byte_size` INT、`create_time` | `uk_i18n_bundle`(`release_id`,`locale_code`,`namespace_code`) | 只存文件地址与摘要，不存语言包正文 |

不建 CHECK / FK。`en` 不在 `t_i18n_translation` 中存储，`en` 语言包直接由 `source_text` 生成。

**语言包文件存公开桶，库里只存地址（用户 2026-10-22）。** 语言包是浏览器要长期缓存的静态文件，放进公开桶后可以通过固定地址直接拉取，不经过 BFF 和 Platform。库里每个语言包只存文件地址 `/files/v1/{fileId}` 和内容摘要，符合[文件上传与地址原则](../../../agent-docs/decisions/2026-10-22-file-upload-address-principles.md)。公开地址由文件服务在读取时计算，不落库。

**数据与文件的分工。** 文案在库里始终是一条条记录：`t_i18n_key` 一行是一个 key 及其英文基准，`t_i18n_translation` 一行是一个 key 在一个语言下的文案。编辑、筛选、导入都只改这些行。只有发布时才把当时的行打成语言包文件上传，`t_i18n_release` 与 `t_i18n_release_bundle` 只记录这次发布的统计和文件地址，不再复制文案。某次发布的内容以它的文件为准；"两次发布的差异"接口读取两次发布的文件对比，不依赖库里的当前行。

`content_revision` 在每次导入 key、保存或导入翻译时加 1，用于判断"生成语言包期间文案有没有被改"。

### 3.3 发布与回滚

上传文件是对外部存储的调用，不能放进数据库事务。发布因此分三步，每步都可以安全重试：

1. **生成与上传（事务外）。** 记下应用当前的 `content_revision`，读全部 ACTIVE key 与翻译，按应用 `required_locales` 计算覆盖率（`en` 恒为 100%）。任一必须覆盖语言低于阈值时返回 422 `I18N_COVERAGE_INSUFFICIENT` 并附缺口清单（最多 200 条与总数）。通过后对 `en` 和每个已启用且有翻译的语言，按命名空间生成扁平 `{"key": "text"}` JSON（key 按字典序，UTF-8，无多余空白），计算 SHA-256，经文件服务以 `PUBLIC` 访问级别、原因码 `I18N_RELEASE_BUNDLE` 逐个上传。对象键带发布号和摘要，每个文件只写一次，永不覆盖。
2. **登记发布（短事务）。** 锁应用，核对 `content_revision` 没变；变了就返回 409 `I18N_CONTENT_CHANGED`，让操作人重新发布，已上传的文件成为孤儿文件（见第 8 节）。没变则插入 `t_i18n_release`（`release_no` = 应用内最大值 + 1，状态 `PENDING_CHECK`）、全部 `t_i18n_release_bundle`、写审计 `I18N_RELEASE_PUBLISH`，并在同一事务登记补偿事件 `I18N_RELEASE_ACTIVATE`（事件键 `releaseId`）。接口返回发布号与 `PENDING_CHECK` 状态。
3. **检查通过后上线（补偿事件）。** 文件服务对新文件异步检查。补偿事件读该发布全部文件的检查状态：仍有未完成的返回重试；任一被拒绝，发布标为 `FAILED` 并记原因；全部 `AVAILABLE` 时在短事务里把发布标为 `READY`，并在应用当前发布号小于它时把 `current_release_id` 指向它。若此期间操作人已手动切到更新的发布，则只标 `READY`，不改指针。

语言包使用专用检查策略 `I18N_BUNDLE_JSON`：只允许 `application/json`、单文件上限 1 MB、只做格式检查，通常几秒内完成。发布页轮询发布状态，显示"检查中 / 已上线 / 失败"。

回滚：把 `current_release_id` 改为任一 `READY` 的历史发布，写审计 `I18N_RELEASE_ACTIVATE`，不生成新文件。

### 3.4 ICU 校验

保存翻译、导入翻译、导入 key 时都做校验，规则见需求 B5：

- 语法可解析；
- 参数名集合与基准文案一致（`plural` 的 `#` 不算参数）；
- `plural`、`select`、`selectordinal` 都要有 `other` 分支。

后端用 ICU4J 的 `MessagePattern` 解析（S2），只用于校验，不在服务端格式化文案。前端编辑器用同一套规则做即时提示，后端结果为准。错误返回字段级 `errors[{field,message}]`，`field` 为 `translations[i].text`，消息带字符位置。

### 3.5 key 导入与翻译导入、导出

导入文件经既有文件上传链路（浏览器 → ops-bff → Platform 文件服务，按 2026-10-22 文件上传原则），新增上传原因码 `I18N_KEY_IMPORT`、`I18N_TRANSLATION_IMPORT`，单文件上限 2 MB，只允许 `application/json`。这样避开 Gateway 65536 字节的请求 Body 上限。

**key 导入文件格式**（由前端脚本 `npm run i18n:export-source` 从 `src/i18n/en/*.json` 合并生成）：

```json
{
  "format": "code-mor-i18n-source/1",
  "application": "client-console",
  "namespaces": {
    "team": {
      "team.invite.submit": "Send invitation",
      "team.member.count": { "text": "{count, plural, one {# member} other {# members}}", "description": "Header above member table" }
    }
  }
}
```

导入分两步，都是无状态接口：

1. 预览：传 `fileId`，返回新增 / 基准变更 / 将废弃 / 恢复在用四类清单、未创建的命名空间清单和 `previewDigest`（由文件 Hash 与当前 `key_set_revision` 计算）。文件里出现未创建的命名空间，或 key 不以所在命名空间编码加 `.` 开头时，预览标为不可导入并列出问题；前端在弹窗内提供"创建"直达。
2. 确认：传 `fileId` 与 `previewDigest`；若期间有人导入过，`key_set_revision` 已变，返回 409 要求重新预览。确认时重新校验命名空间；仍有未创建的命名空间返回 422 `I18N_NAMESPACE_NOT_FOUND`。确认在一个事务内完成：新增 key、变更 key 的 `source_text` 与 `source_revision` + 1、文件里没有的 key 标为 DEPRECATED、文件里重新出现的 DEPRECATED key 恢复为 ACTIVE；`key_set_revision` + 1。

导入代表整个应用的完整 key 集合，文件里没有的 key 一律废弃，代码删 key 能同步反映。命名空间不会被导入创建或删除；文件中没有任何 key 的命名空间保持不变，只是其下 key 全部废弃。

**翻译导出**：`GET .../translations/export?locale=zh-CN` 返回与上面相同结构的 JSON，值为该语言翻译；未翻译的 key 值为空字符串。

**翻译导入**：上传同结构文件。全部校验通过才写入，任一失败整体拒绝并返回逐 key 错误（最多 200 条）。空字符串表示不改动。未知 key 报错。

### 3.6 前端运行时与接入约定

两个 web 各自放一份 `src/i18n/`（两者没有共享包，代码约 200 行，复制比新建 npm 包简单）：

- **基准文件。** `src/i18n/en/<namespace>.json`，扁平 key → 英文文案，可用对象形式加 `description`。构建时由 Vite 静态导入，打进产物。
- **运行时。** 启动时读 `GET {bff}/i18n/v1/manifest?locale=xx`，按当前页面所需命名空间并行拉语言包；文案查找顺序为"运行时目标语言 → 运行时 `en` → 内置 `en` → key 本身（开发环境 console.warn）"。manifest 失败时只用内置 `en`，不阻塞首屏。
- **格式化。** 用 `intl-messageformat` 解析 ICU，按 locale 缓存已编译消息；日期与数字用浏览器 `Intl`。精确版本在阶段 4 锁定，按 TECHNOLOGY_STACK 第 71 行登记前端例外。
- **API。** `useT(namespace)` 返回 `t(key, params?)`；`<I18nProvider>` 提供当前语言、`setLocale()`。
- **语言来源。** 需求 B6：`?lang=` > localStorage > `navigator.language` 匹配 > 应用默认语言。切换时同步 `<html lang dir>`；ops-web 另外同步 antd `ConfigProvider locale` 与 dayjs locale，但只在新页面所在路由生效前不改全局（既有页面仍是中文，全局保持 `zh_CN`）。
- **预览。** `?release=7` 时 manifest 请求带 `releaseNo`，读指定发布（需求 C7）。
- **禁止硬编码。** 新增 node 测试扫描接入范围内的 tsx / ts，出现中日韩字符即失败；client-web 扫全部源码，ops-web 只扫 `src/localization/` 与 `src/i18n/`（S5）。

### 3.7 交付与缓存

- **manifest**：`GET {bff}/i18n/v1/manifest?locale=&releaseNo=`，经 BFF 匿名读取。响应 `Cache-Control: no-cache`，`ETag` 为"发布号 + locale"，支持 304。
- **语言包**：浏览器按 manifest 中的公开地址直接从公开桶 / CDN 拉取，不经 BFF。每个文件的对象键带发布号与摘要，内容永不变，桶上对该前缀配置 `Cache-Control: public, max-age=31536000, immutable`，并允许各 Console 域名跨域 GET。

manifest 响应：

```json
{
  "application": "client-console",
  "releaseNo": 7,
  "publishedAt": "2026-10-23T08:00:00Z",
  "locales": [{ "code": "en", "nativeName": "English", "direction": "LTR" }, { "code": "zh-CN", "nativeName": "简体中文", "direction": "LTR" }],
  "defaultLocale": "en",
  "bundles": { "zh-CN": { "team": { "hash": "…", "url": "https://<公开桶基础地址>/…/team.<hash>.json" } } }
}
```

Platform 生成 manifest 时按文件地址向文件服务（进程内）取 `publicUrl`。发布内容不可变，所以按发布号做进程内缓存。`locale` 参数只裁剪 `bundles`，只返回 `en` 与目标语言两组。`releaseNo` 只能指定 `READY` 的发布。BFF 把 `application` 固定为自身应用，前端读不到其他应用的文案。应用还没有 `READY` 发布时返回 `releaseNo: null` 和空 `bundles`，前端只用内置文案。

### 3.8 内部接口清单（`/internal/localization/v1`，共 24 个）

| 分组 | 接口 |
|---|---|
| Locale（3） | 列表；新增；修改（名称、方向、状态、排序，带 `expectedVersion`） |
| 应用（4） | 列表；详情（含当前发布、覆盖率摘要）；新增；修改（名称、默认语言、必须覆盖语言、阈值） |
| 命名空间（4） | 列表（含在用 key 数）；新增；修改（名称、说明、排序，带 `expectedVersion`）；删除（无任何 key 时） |
| key 导入（2） | 预览；确认 |
| 翻译（4） | 工作台查询（应用、命名空间、目标语言、填写状态、关键字、分页；返回 key、基准、说明、参数、翻译、版本、是否基准已变更）；批量保存（每次最多 200 条，各带 `expectedVersion`，全部成功或全部失败）；导出；导入 |
| 发布（6） | 发布预检；发布；发布历史列表（含状态）；发布详情（覆盖率、报告、状态、失败原因）；两次发布的差异；切换当前发布（回滚） |
| 交付（1） | manifest |

所有写接口带 `X-Operator-Account-Id`。错误码新增 `I18N_COVERAGE_INSUFFICIENT`（422，前端需要展示缺口清单）、`I18N_IMPORT_PREVIEW_STALE`（409，前端提示重新预览）、`I18N_CONTENT_CHANGED`（409，前端提示文案已变、重新发布）与 `I18N_NAMESPACE_NOT_FOUND`（422，前端列出缺失的命名空间并提供创建入口）；其余用公共 `BUSINESS_CHECK_FAILED`、`INVALID_REQUEST`、`CONCURRENT_REQUEST`。

### 3.9 Operations 控制台页面与权限

页面结构参照工作流 V2：导航新增一级模块「多语言」，下设三页；每页一个主面板（列表 + 详情），导入、发布、切回都在弹窗中完成，不另开页面。

| 页面 | 结构 | 主要操作 |
|---|---|---|
| 翻译 | 页头选择应用与目标语言，页签「全部 / 未翻译 / 基准已变更」；左侧 key 列表（按命名空间筛选），右侧 key 详情：英文基准与参数、中文输入（ICU 高亮）、带示例值的预览；底栏显示校验结果 | 保存并下一条（⌘↵）；页头「导入 key」「发布」；有阻止发布的问题时，页头显示"N 个问题"，展开可逐条定位 |
| 发布记录 | 页头选择应用并显示线上版本；左侧发布表（检查中 / 线上 / 已下线 / 检查失败），右侧发布详情：上线进度、相对上一版的变更摘要、语言包 | 切回此版本（弹窗确认，填原因写审计） |
| 语言与应用 | 页签「应用 / 语言」；左侧应用表，右侧应用设置：默认语言、必须翻译的语言、发布要求（覆盖率）并即时显示是否满足，以及命名空间列表（名称、编码、说明、key 数；点击进入翻译页并按该命名空间筛选） | 新建应用（弹窗）、新建命名空间（弹窗：编码、名称、说明）、保存设置 |

原型：`operations-console-web/docs/operations-console.pen` y=87900 与 y=89100 两行共 9 张画板：01 翻译｜逐条编辑与实时预览；02 翻译｜校验错误与发布前问题；03 导入 key（弹窗）；04 发布确认（弹窗）；05 发布记录｜列表与详情（#8 检查中）；06 切回确认（弹窗）；07 语言与应用｜应用设置（含命名空间）；08 新建应用（弹窗）；09 新建命名空间（弹窗）。03 导入弹窗展示了"未创建的命名空间"阻止导入的状态。

权限 3 项：`I18N_VIEW`（查看）、`I18N_EDIT`（Locale、应用、命名空间、导入、翻译）、`I18N_PUBLISH`（发布、切回）。路由：Operations 管理 23 条（`HUMAN_PERMISSION`）+ manifest 1 条（`PUBLIC`）；Client manifest 1 条（`PUBLIC`）。文件：导入原因码 2 个走私有桶；语言包原因码 `I18N_RELEASE_BUNDLE` 路由到 Platform 的公开桶，检查策略 `I18N_BUNDLE_JSON`。初始数据：2 个 locale、4 个应用（`operations-console` 默认 `zh-CN`，必须覆盖 `zh-CN`；`client-console`、`partner-console`、`checkout` 默认 `en`，必须覆盖 `zh-CN`），全部在新注册块内。

### 3.10 client-console-web 接入

- 现有中文文案逐个替换为 key，同时写英文基准到 `src/i18n/en/`。
- 原中文整理成 `zh-CN` 翻译导入文件，作为首批翻译，不需要运营重新录入。
- 顶部栏增加语言切换（English / 简体中文），登录页同样提供。
- 后端返回的 Problem `detail` 仍是中文，本期不处理（第二类文案另立）。英文界面下报错仍会显示中文，这是已知限制。

## 4. 数据库变更

- PDManer：`paytech/platform-support/docs/database/platform-support.pdma.json` 新增图组 `localization`，7 张表。
- Schema：`platform-support-schema.sql` 追加 7 张表；增量 `changes/2026-10-2x-localization.sql`；回滚为 7 条 `DROP TABLE`。
- 注册与初始数据：`db/dml.sql`（新块），含 locale、应用、权限、菜单、路由、组件、3 个文件原因码与路由、检查策略 `I18N_BUNDLE_JSON`、补偿事件目录项。
- 公开桶本身（存储实例、桶、对外地址、跨域与缓存配置）属于环境配置，由文件公开桶需求的管理页面登记，本需求只引用。
- 不涉及其他数据库。所有 SQL 手工执行，阶段 5 本地、阶段 7 mor-test 各自授权。

## 5. 兼容性与上线顺序

- 既有接口、表、页面不变；ops-web 既有页面不受影响。
- Gateway 只增加路由数据，不改代码。
- 上线顺序：文件公开桶 → Platform 本需求 → 配置语言包公开桶（跨域、缓存）→ 导入 key 与中文翻译 → 发布 → client-bff → client-web。
- client-web key 化后，即使没有任何发布也能以内置英文运行。先上线 client-web 而未发布时，用户看到英文界面，不会出错。

## 6. 验证计划

| 验收 | 验证方式 |
|---|---|
| AC-01—AC-06、AC-09、AC-10 | Platform HTTP 入口 auto 测试（真实链路，本地 MySQL 与本地对象存储）：Locale、应用、导入预览 / 确认 / 过期、翻译保存与 ICU 校验矩阵、发布阈值拒绝、发布期间文案变更返回 409、检查通过后自动上线、检查拒绝标 FAILED、手动切到更新发布后旧发布上线不改指针、回滚、差异、导出再导入无差异 |
| ICU 规则 | 纯 Java 测试覆盖 B5 全部规则与常见错误位置 |
| BFF | 转发测试：manifest 固定应用编码、`ETag` 与 304 |
| AC-07、AC-08、AC-13 | client-web node 测试：查找回退顺序、manifest 或语言包失败回退、无中日韩字符扫描；浏览器验收两种语言全部页面，并确认语言包直接从公开地址加载且命中缓存 |
| AC-11、AC-12 | ops-web node 测试 + mor-test 浏览器验收脚本与截图 |

## 7. 实施拆分（阶段 4）

| 卡 | 内容 | 依赖 |
|---|---|---|
| A | Platform 模块：表、Repository、ICU 校验、发布三步与补偿事件、24 个 Processor 与 Controller、文件导入与语言包上传集成、auto 测试 | 文件公开桶的公开上传与 `publicUrl` 合同 |
| B | 注册块 DML、ops-bff 24 条与 client-bff 1 条转发 | A 的接口合同 |
| C | 前端运行时 `src/i18n/`（两份）与 ops-web 4 个页面 | A 的接口合同 |
| D | client-web 全量 key 化、语言切换、中文首批翻译文件 | C 的运行时 |
| E | 决定记录、开发约定新节、AGENTS 路由、TECHNOLOGY_STACK 前端例外 | 无 |

A 与 E 可以先并行，接口合同定稿后 B、C 并行，D 最后。A 的发布部分要等文件公开桶合并到主线后才能联调。

## 8. 风险

- **依赖文件公开桶。** 它在阶段 4 研发中，并且改动了扫描流程。本需求的发布联调与上线都排在它之后。
- **孤儿文件。** 发布第 2 步因文案变更失败，或进程在第 1、2 步之间退出时，已上传的语言包没有发布引用。它们只是公开桶里不被引用的小文件，不影响正确性；清理放到文件服务的通用孤儿清理里，本需求不做。
- **跨域与缓存配置。** 公开桶不允许 Console 域名跨域时，前端拉不到语言包，只能显示内置英文。环境验收要专门检查。
- **client-web 改动面。** 所有页面都要改，靠现有 node 测试和浏览器验收兜底；遗漏会被硬编码扫描测试发现。
- **ops-web 中英混排。** 只有新页面支持英文。本期 ops-web 不提供语言切换入口，新页面跟随默认 `zh-CN`。
- **报错仍是中文。** 见第 3.10 节。
- **共享注册数据文件。** 新块号使用前需向其他会话登记。

## 9. 已确认与待确认

- S1 语言包文件存公开桶，库里只存文件地址与摘要（用户 2026-10-22，修改了 v1 的存库方案）。
- S2 后端引入 ICU4J，只用 `MessagePattern` 校验语法，版本在阶段 4 由 BOM 锁定（用户 2026-10-22"没问题，你看着来"）。
- S3—S5、S7 按建议：导入走文件上传；本期不建共享应用；硬编码检查用 node 测试；ops-web 不提供语言切换。
- S6 改为：命名空间建表，在后台创建与查看；导入只接受已创建命名空间下的 key（用户 2026-10-22"命名空间还是要能创建和查看"）。
- **S8（新）发布改为异步上线。** 存公开桶后，文件要先通过检查才能对外，所以发布返回"检查中"，检查通过后自动上线，通常几秒。建议接受。
- **S9（新）孤儿语言包不在本需求清理。** 建议接受，理由见第 8 节。
