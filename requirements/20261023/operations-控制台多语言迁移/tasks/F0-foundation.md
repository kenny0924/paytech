# 任务卡 F0：多语言迁移基础（运行时、语言切换、框架层、BFF、样板模块）

你是执行代理。本卡完成后，7 个模块代理会在你的成果上并行迁移各自模块，所以**约定、工具与样板必须清楚、稳定、可照抄**。

## 必读

1. 需求：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261023/operations-控制台多语言迁移/requirement.md`（v2）
2. 技术方案：同目录 `technique.md`（v2，尤其 3.1—3.6、第 7 节已确认结论）
3. 英文术语表：同目录 `glossary.md`（英文用词必须遵守）
4. 原型：同目录 `assets/prototype/10-header-language-zh.png`、`12-login-language.png`
5. 现有运行时：研发目录 `operations-console-web/src/i18n/`（`runtime.ts`、`index.ts`、`react.tsx`、`builtin.ts`、`runtime.test.ts`）与多语言模块 `src/localization/`（已接入的写法参考）
6. 仓库规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md` 第 10 节、`agent-docs/UNIT_TEST_GUIDE.md`（BFF 测试部分）
7. Operations 资源清单（mor-test 只读导出）：同目录 `tasks/ops-resources-mor-test.tsv`（列：resourceCode、type、parentCode、中文名）

## 工作目录与边界

- 只在 `/Users/kenny/Desktop/Workspace/Code-MoR-opsi18n`（detached，基线 `9f210b5c`）中修改。**不要提交、不要推送、不要碰主工作区** `/Users/kenny/Desktop/Workspace/Code-MoR`（只读参考可以）。
- Maven：`JAVA_HOME` 用 JDK 25；一律 `-Dmaven.repo.local=/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/0cc285ca-0776-4a38-9e68-e0b8c88eaaec/scratchpad/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository`（两个 -D 分开写），不 `install` 进共享仓库；只跑 operations-console-bff 模块的测试，不跑全 reactor。
- 前端在研发目录的 `operations-console-web` 下 `npm test`、`npx tsc --noEmit`、`npx vite build`（`node_modules` 已安装）。
- 不调用任何 Superpowers skill。不使用 Workflow / 子代理。全程前台执行，完成后直接结束。

## 交付内容

### A. 运行时（`src/i18n/`）

1. `index.ts`：`defaultLocale: 'en'`；`publishedOnly: true`；传入 `document`（浏览器下为 `window.document`）以更新 `<html lang>`；启动时**不读浏览器语言**（`navigatorLanguages: []`）。更新文件头注释（不再写"只有多语言页面使用 / 默认 zh-CN / 无切换"）。
2. **本地缓存**（方案 3.3）：`createI18n` 新增可选 `cache?: KeyValueStore | null`（ops 传 `localStorage`）。
   - 成功取到 manifest（非 `?release=` 预览）后写缓存：键 `i18n.cache.manifest.<locale>`；语言包按其 URL 写 `i18n.cache.bundle.<url>`（URL 含发布号与摘要，不可变）。写入后清理当前 manifest 不再引用的 `i18n.cache.bundle.*`。写入抛错（配额）时清空全部 `i18n.cache.*` 并继续。
   - `start()`：若所选语言（及 `en`）在缓存中有 manifest 且其引用的语言包都在缓存里，**先用缓存完成激活并返回**（Gate 不等网络），再后台取 manifest；摘要（URL）变化则加载新语言包、更新缓存并 `notify()`。网络失败时保留缓存结果。无缓存时按现有流程。`publishedOnly` 的"已发布语言列表"在用缓存启动时也要可用（缓存的 manifest 视为已校验）。
   - 新增 `prefetch(code)`：取该语言 manifest 与全部 `needed` 命名空间语言包写入缓存（失败不抛）。语言切换时先 `await prefetch(code)` 再写本地选择并 `location.reload()`。
   - 单元测试覆盖：缓存启动不发网络请求即可翻译、后台发现新发布后替换、网络失败用缓存、配额异常清缓存、预览不读写缓存、清理旧语言包。
3. 新增 `src/i18n/namespaces.ts`：`OPS_NAMESPACES` 常量（方案 3.1 全部 21 个：common、auth、nav、enum、access、gateway、channel、commercial、finance、recon、risk、workflow、data-query、file、notification、webhook、vault、card、client-review、currency、i18n-admin）。
4. `builtin.ts` 静态引入全部 `en/<ns>.json`。为尚未迁移的命名空间创建 `en/<ns>.json`，内容 `{}`。
5. 辅助函数（导出自 `index.ts` 或单独文件，写 JSDoc 说明用法）：
   - `tOr(t, key, fallback)` 或运行时 `has(namespace, key)`：有 key 用翻译，否则用 fallback（菜单名回退 `display_name` 用）。
   - `enumLabel(t, prefix, value)`：`t(prefix + '.' + value)` 存在则返回翻译，否则返回原值。
   - 测试辅助 `src/i18n/testing.ts`：让 node 测试以内置英文运行（不发网络、不读存储），供各模块测试使用。
6. 中文种子改为**按命名空间分文件** `operations-console-web/i18n/zh-CN/<ns>.json`（扁平 `{"<完整 key>": "原中文"}`），把现有 `i18n/operations-console.zh-CN.seed.json` 中的内容拆过去并删除旧文件。`scripts/i18n-export-source.mjs` 改为合并全部命名空间输出 `i18n-out/operations-console.en.json`（`code-mor-i18n-source/1`）与 `i18n-out/operations-console.zh-CN.json`（`code-mor-i18n-translation/1`），并：校验 en 与 zh-CN key 集合完全一致、每个 key 以所在文件命名空间开头、输出文件不超过 2 MiB（超过则按命名空间分多个文件输出并说明）。

### B. 一致性与防回归测试

1. `src/i18nSourceGuard.test.ts`：扫描 `src/**/*.ts(x)`（排除 `*.test.ts`），去掉注释后统计含汉字的文件与字面量。白名单只有 `navigation/pageCapabilities.ts`。**本卡为报告模式**：测试通过，输出剩余文件数与字面量数；文件内放一个常量 `STRICT = false`，F9 收尾卡改为 `true`（届时有汉字即失败）。另外对**已迁移完成的命名空间清单**（常量 `MIGRATED_DIRS`，本卡填入你完成的目录/文件）立即严格执行。
2. `src/i18nCatalog.test.ts`：en 与 zh-CN 种子 key 集合一致；每个 en 值非空；ICU 语法可解析（`intl-messageformat`）且 en 与 zh-CN 的参数名一致；代码中 `useT('<ns>')` / `translator('<ns>')` 绑定后的 `t('<literal>')` 引用都存在于 `en/<ns>.json`（静态扫描，同一文件内按最近绑定的命名空间解析；动态 key 跳过）；`nav.resource.*` 覆盖 `pageCapabilities.ts` 的全部 `componentKey` 与动作 `resourceCode`，以及资源清单中的父级菜单编码。
3. 现有多语言模块内的局部禁中文扫描保留或并入，二者不冲突即可。

### C. 框架层迁移（你负责的文件）

迁移以下文件的全部界面文案（`common`、`auth`、`nav` 命名空间）：`main.tsx`、`App.tsx`、`auth/`、`navigation/`（除 `pageCapabilities.ts` 的数据）、`feedback/`、`components/`、`hooks/`、`api/`。包括：会话恢复、登录、强制改密、退出、菜单加载失败、错误通知（标题、code/requestId 标签、"复制错误信息"）、`TimeRangePicker` 等通用组件、加载与空状态。

- **菜单 / 面包屑 / 页面标题 / 菜单搜索**：`label = tOr('resource.' + resourceCode, displayName)`（`nav` 命名空间，即完整 key `nav.resource.<CODE>`）。为资源清单 173 条与 `pageCapabilities` 中出现的全部编码建 key：英文按 `glossary.md` 第 1 节，按钮资源按动作含义起英文（Create、Disable…），zh-CN 种子取中文名。
- **语言切换组件** `src/i18n/LanguageSwitcher.tsx`（或 `components/`）：按原型 10：地球图标 + 当前语言原生名 + 箭头；下拉标题"Interface language / 界面语言"，选项为原生名 + 语言代码，当前项打勾，底部说明"The page reloads and stays where you are"（zh：切换后页面会重新加载，停留在当前页面）。只列运行时 `locales()`；少于两个时不渲染。放在页头"环境标记"与账号之间、登录页右上角（原型 12）。
- `main.tsx`：启动运行时；根部 `I18nGate` 传 `OPS_NAMESPACES`；antd `ConfigProvider` 的 `locale` 按 `i18n.locale()` 取 `en_US` / `zh_CN`；dayjs 设置对应语言（`dayjs/locale/zh-cn`）；字体栈保留中文字体。多语言模块原有局部 Gate 可保留但不得重复等待（根部已就绪）。
- 角色权限树前端（如在 `access` 范围内的页面里）**不在本卡**，但 BFF 改动在本卡（见 D），并在 `api/types.ts` 更新类型。

### D. operations-console-bff（方案 3.5）

1. `RoleModels.PermissionResource` 增加 `resourceCode`（Platform `RoleResource` 已返回），`PermissionNode` 增加 `code`：资源节点为 `resourceCode`，未挂资源的权限节点为 `permissionCode`，"其他权限"分组 `code = "OTHER_PERMISSIONS"`、`displayName` 改为同一编码（不再有中文）。
2. `AuditEventDetailsProcessor`：删除中文 `LABELS` 与 `label` 字段（`AuditChange` / `AuditProperty` 只保留 key 与值），同步响应模型。
3. `CommercialFeeIllustrator` 与试算响应模型：`note` 字符串改为 `notes: [{code, params}]`；不支持原因改为 `{code, params}`。code：`BAND_HIT{bandNo}`、`BAND_NONE`、`PROGRESSIVE_BANDS{count}`、`MIN_APPLIED{amount}`、`MAX_APPLIED{amount}`、`BASE_DEPENDS_ON_FEE{base}`、`COMPONENT_UNSUPPORTED{type}`、`BAND_BASIS_PERIODIC{basis}`、`BAND_MODE_UNSUPPORTED{mode}`；`params` 为字符串字典。
4. 改相关测试（遵守 `UNIT_TEST_GUIDE.md`：不得 `when/thenReturn`、`@MockBean`），跑 operations-console-bff 全部测试。
5. 前端 `api/types.ts` 及使用处同步类型；页面上的翻译由对应模块卡完成（M5 定价试算、M6 审计与权限树）。你在类型上加注释说明 code 含义。

### E. 样板模块

完整迁移 `currency`（`pages/CurrencySpecs.tsx` 及其测试）作为模块代理的照抄样板：key 命名、模块级常量改 key、枚举 `enumLabel`、ICU 复数、测试用 `testing.ts` 以英文断言、种子写入 `i18n/zh-CN/currency.json`。

### F. 迁移指南

写 `operations-console-web/docs/i18n-migration-guide.md`（中文），供模块代理照做，至少包含：命名空间归属表（方案 3.1，按目录 / 文件前缀）、key 规则与示例、组件内 / 非组件代码写法、模块级常量改法（3 种典型改写前后对照）、模板字符串 → ICU（含 en 复数、zh 无复数）、枚举、`tOr` 使用场景、不翻译的内容（录入数据、编码、`pageCapabilities.ts`、`workflow/formModel.ts` 拼音）、测试改写（以英文断言、`testing.ts`）、种子写法（zh-CN = 原中文逐字）、英文写法约定（引用 `glossary.md` 第 4 节）、自检命令（`npm test`、`tsc`、`build`、`node scripts/i18n-export-source.mjs`、守卫测试中把自己目录加进 `MIGRATED_DIRS`）。样板引用 `currency`。

## 验收（交付前自检，写进报告）

- `npm test` 全部通过（报告总数）；`tsc --noEmit`、`vite build` 通过；`node scripts/i18n-export-source.mjs` 成功并报告各命名空间 key 数与输出文件大小。
- 守卫测试报告：剩余含汉字文件数、字面量数；你负责的目录与 `currency` 已为 0。
- BFF：operations-console-bff 测试数与结果。
- 本地 Vite（端口 18301，`?lang=en` / `?lang=zh-CN`）能打开登录页并看到语言按钮（无后端时 manifest 失败 → 不显示按钮是预期；在报告中说明你如何验证切换组件，例如测试或模拟 manifest）。

## 报告

写到 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261023/operations-控制台多语言迁移/tasks/F0-report.md`：改动文件清单、约定要点、验收结果、与方案不一致之处及原因、模块代理需要特别注意的坑。最终回复简述同样内容。
