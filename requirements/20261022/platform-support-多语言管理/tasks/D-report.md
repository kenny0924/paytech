# 卡 D 交付报告：client-console-web 多语言接入

工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n/client-console-web`（未提交、未推送，未启动或修改容器）。依据：requirement.md R8/R9/B6/B7/AC-07/AC-08/AC-13，technique.md 3.5–3.7、3.10，worktree 中 `DEVELOPMENT_CONVENTIONS.md` 第 10 节。

## 1. 结论

- `src` 全部界面文案已改为 `t()`，英文基准 8 个命名空间共 608 个 key，中文首批翻译逐条保留原中文。
- 测试 77 项全部通过（原 63 项 + 新增 14 项），`tsc` 与 `vite build` 通过，导出脚本生成 608 个 key。
- 浏览器验收（本地合成协议，见第 6 节）：en 与 zh-CN、1280 与 390 宽度共 52 个页面状态均无页面级横向溢出、无裸 key、`<html lang>` 正确；语言切换写入 localStorage，刷新后保持。

## 2. 依赖

- `intl-messageformat` **12.1.3**（发布于 2026-10-05，精确版本，已写入 package.json 与 package-lock.json）。
- 需由卡 E 在 TECHNOLOGY_STACK 前端例外中登记该版本。

## 3. 运行时 API 与文件结构（供卡 C 复制）

`src/i18n/` 与业务无关，可整体复制，只需改 `index.ts` 中的 `manifestUrl` 与 `appLocales`、`builtin.ts` 的命名空间清单。

| 文件 | 作用 |
|---|---|
| `runtime.ts`（约 200 行） | `createI18n(options)`：语言来源、manifest、按需懒加载语言包、查找回退、ICU 编译缓存（按 locale + 文案缓存）、`<html lang dir>`；不触碰浏览器全局，可在 node 测试 |
| `index.ts` | 应用单例 `i18n`、`translator(ns)`（组件外使用）、`formatDate` / `formatDateTime` / `formatNumber`（`Intl`，按当前 locale）、`appLocales` |
| `react.tsx` | `I18nProvider`、`useT(ns)` 返回 `t(key, params?)`、`useLocale()` 返回 `{locale, locales, setLocale}` |
| `builtin.ts` | 静态导入 `en/*.json`（`with { type: 'json' }`），构建期内置 |
| `en/<ns>.json` | 扁平完整 key → 文案，或 `{ text, description }` |
| `runtime.test.ts` | 运行时测试，随运行时一起复制 |

行为要点：

- `t('invite.submit')` 在 `useT('team')` 下查 `team.invite.submit`；JSON 与导入文件都存完整 key。
- 查找顺序：运行时目标语言 → 运行时 `en` → 内置 `en` → key 本身，开发环境每个 key 只 `console.warn` 一次。
- 启动：`i18n.start({search, navigatorLanguages})`，必须在清理 URL 前调用（见 `main.tsx`）。`manifest?locale=xx[&releaseNo=N]`，语言包直接用 manifest 的 `url`，均 `credentials: 'omit'`；任何失败只回退，不抛错、不阻塞渲染。
- 语言来源：`?lang=`（存 sessionStorage，仅本标签会话）> localStorage `i18n.locale` > `navigator.languages` 与启用语言匹配（先精确，再同主语言）> manifest `defaultLocale` / 应用默认。manifest 返回后按其启用语言重新确认。`setLocale` 写 localStorage 并清除 `?lang=` 覆盖。
- `?release=N` 透传为 `releaseNo`（只接受正整数），同样只在本标签会话保留。
- 命名空间在 `useT` 或 `t()` 首次使用时登记，manifest 就绪后并行拉取该命名空间的 `en` 与目标语言包；URL 带 hash，按 URL 缓存，切换发布不会混用。
- ICU 编译使用 `ignoreTag: true`，避免 `<` 被当成富文本标签。
- ops-web 需要另行同步 antd `ConfigProvider` 与 dayjs locale（technique 3.6），本运行时未包含。

## 4. 命名空间与 key 数

| 命名空间 | key 数 | 范围 |
|---|---|---|
| `common` | 18 | 通用状态、通用错误、密码显示切换、请求编号、语言选择 |
| `auth` | 64 | 登录、注册、找回 / 重置密码、邮箱验证、账号页、会话错误 |
| `onboarding` | 177 | 入驻开始页、申请表单字段、人员与关系、材料上传、提交核对、审核结果 |
| `workspace` | 28 | 工作区外壳、首页、降级原因 |
| `team` | 72 | 团队成员、邀请、角色说明、团队错误码 |
| `invitation` | 32 | 接受邀请页 |
| `developer` | 143 | API 凭证、Webhook 端点、投递记录 |
| `enum` | 74 | 枚举展示名：角色、成员 / 邀请 / 申请 / 扫描 / 凭证 / 端点 / 投递状态、客户类型与状态、经营模式、关系类型、材料类型、Webhook 事件与失败原因 |

命名空间编码需要运营在 Platform 后台先创建，否则 key 导入会被拒绝。

## 5. 改动文件

新增：

- `src/i18n/{runtime.ts, index.ts, react.tsx, builtin.ts, runtime.test.ts, en/*.json}`
- `src/LocaleSwitcher.tsx`：English / 简体中文 下拉，选项来自 manifest（失败时用内置清单）。
- `src/enum-label.ts`：枚举 Code 登记表与 `enumLabel(group, code)`，未登记 Code 返回 null，保持原"不透传未知代码"的语义。
- `src/i18n-coverage.test.ts`：硬编码扫描与一致性测试。
- `scripts/i18n-export-source.mjs`，以及 `i18n/client-console.zh-CN.seed.json`（需提交）。

修改：

- 所有页面与模块：`main.tsx`、`LoginForm.tsx`、`TextField.tsx`、`InvitationPage.tsx`、`OnboardingPage.tsx`、`TeamPage.tsx`、`WorkspacePage.tsx`、`DeveloperPages.tsx`，以及 `auth.ts`、`auth-form.ts`、`login-feedback.ts`、`invitation.ts`、`team.ts`、`workspace.ts`、`developer.ts`、`onboarding.ts`、`submission.ts`。
- 9 个原有测试文件，断言改为英文基准，语义不变。
- `style.css`：切换器样式；按钮允许换行；`dl` 标签列按内容放宽。
- `package.json`：test 增加 `src/i18n/*.test.ts`，新增 `i18n:export-source` 脚本。
- `tsconfig.json`（`resolveJsonModule`）、`.gitignore`（`i18n-out/`）、`index.html`（`lang="en"`）。

导出的接口形态变化，均只在本应用内使用：

- `roleLabels`、`memberTypeLabels`（原未使用）、`*StatusLabels`、`clientTypeLabels`、`businessModelLabels`、`homeNotices`、`statuses`、`scanLabels`、`failureLabels`、`credentialStatusLabels` 改为 `enumLabel`。
- `invitationProblemTexts` 改为 `invitationProblemText()`。
- `emailFailedText`、`replayRejectedText` 改为函数。
- `teamErrorTexts` 改为 `teamErrorCodes` 加 `teamErrorText()`。
- `assignableRoles`、`graceOptions`、`webhookEventTypes` 的 `label` / `description` 改为按当前语言读取的 getter。
- `developerMenu` 项去掉 `label`。
- 日期显示由固定 `zh-CN` 改为按当前语言格式化。

语言切换入口：

- 认证页、账号页、邀请页的页脚。
- 入驻页顶部栏，位于"账号"左侧。
- 工作区侧栏底部，窄屏收在菜单内。

## 6. 验证实际输出

```text
$ npm install                      # 新增 intl-messageformat@12.1.3
$ npm test
ℹ tests 77
ℹ pass 77
ℹ fail 0
$ npm run build
dist/assets/index-BJwKrgBi.js   416.54 kB │ gzip: 122.96 kB
✓ built in 130ms
$ npm run i18n:export-source
Wrote .../client-console-web/i18n-out/client-console.en.json: 8 namespaces, 608 keys
```

新增测试覆盖：

- 运行时：查找回退顺序与单次告警。
- ICU 复数与参数，复数规则按找到文案的语言生效。
- manifest 返回 503、网络异常或格式异常时回退内置英文。
- 目标语言包失败回退运行时 `en`；全部语言包失败回退内置。
- 只拉在用命名空间，且不带凭据。
- 语言来源优先级，包括 `?lang=` 不写 localStorage、存储被禁用时仍可工作。
- `setLocale` 持久化并更新 `<html lang dir>`，包括 RTL。
- `?release=` 透传为 `releaseNo`，非法值被忽略。
- 扫描：`src` 下 ts/tsx（排除 `src/i18n/` 与测试）去掉注释后不得出现中日韩字符；另有一项测试证明字符串与 JSX 文本不会被当作注释跳过。
- 一致性：英文基准与 seed 的命名空间、key 完全一致，seed 无空值，key 以命名空间开头且全小写；`t()` 静态 key 全部存在；动态 key 家族全部存在（见第 7 节）。

浏览器验收：

- 环境：Playwright 1.58.1 驱动 Chromium，使用 `verification/preview-onboarding.mjs` 的临时副本（在会话临时目录，未改仓库），补充了 manifest、按命名空间的语言包（en 来自基准、zh-CN 来自 seed，带 immutable 缓存头）和工作区、团队、开发者的合成数据。
- 覆盖页面：登录、注册、找回密码、查收邮件、接受邀请、入驻开始页、组织申请、个人申请、账号、工作区首页、团队、API 凭证、Webhook。
- 结果：两种语言在 1280 与 390 宽度下均无页面横向溢出、无裸 key、`lang` 正确，语言包按命名空间从 manifest 地址加载。
- 唯一例外：390 宽度的 API 凭证表格末两列和行内按钮需要在表格容器内横向滚动。这是原有设计，中英文相同，不是截断。
- 切换验证：登录页切换为简体中文后标题变为"欢迎回来"，localStorage 记录 `zh-CN`，刷新后保持。

## 7. 动态 key（静态扫描无法覆盖，已由一致性测试逐项核对）

- `enum.<group>.<code 小写>`：全部来自 `enumCodes` 登记表。
- `team.error.<错误码小写>`、`team.role_description.*`、`team.confirm.<kind>_title/_body`、`team.action.<kind>`。
- `workspace.fallback.*`、`workspace.notice.*`、`workspace.nav.<api_keys|webhooks>`。
- `onboarding.section.*`；`onboarding.field.*` 由资料路径换算，如 `individual.givenName` → `onboarding.field.individual.given_name`。
- `developer.grace.m<分钟>`、`invitation.problem.*`、`auth.<kind>.title/intro/done_title`、`auth.submit.<kind>`。

## 8. 已知限制与需裁决的问题

1. **后端 Problem `detail` 原样显示。** 英文界面下仍会出现中文报错，按 technique 3.10 属已知限制。
2. **没有 READY 发布时选简体中文仍显示英文。** manifest 不可用或尚无发布时，只有内置英文；`lang` 会变为 zh-CN、日期按中文格式，但文案是英文。这符合"运行时缺失回退内置基准"。是否在没有发布时隐藏中文选项，请裁决。
3. **已显示的消息不随切换重新翻译。** 错误、提示等在操作时生成并存入组件状态，切换语言后已显示的这类消息保持原语言，新的操作按新语言显示。
4. **扫描规则排除了注释。** 卡片写"不得出现中日韩字符"；本实现扫描时去掉注释，因为仓库约定注释用中文，界面文案约束只针对代码、字符串与 JSX 文本。若要求连注释也禁止中文，需要把这些文件的注释改为英文，请裁决。
5. **原生语言名在 `appLocales` 中写死。** "简体中文"作为数据放在 `src/i18n/index.ts`，在扫描范围之外；manifest 可用时以其 `nativeName` 为准。
6. **语言包地址。** 运行时直接使用 manifest 的 `url`，前提是公开桶已为 Console 域名开放跨域 GET（technique 第 8 节风险）。验收只在同源合成环境完成，真实公开桶与 CDN 缓存命中需在环境验收中检查。
7. **fixture 文本未翻译。** 合成数据与 CCT 目录 `label`、服务端 `reasonText` 等业务内容属于第二、三类文案，原样显示。
8. **ops-web 的额外适配。** 复制运行时后需另行加入 antd / dayjs 同步，并把 `manifestUrl` 改为 ops-bff 路径。

## 跟进（2026-10-23，按主会话裁决）

工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n/client-console-web`（未提交，lock 未变化，未重新 npm install）。

### 1. 裁决 1：扫描跳过注释

已接受，不改。

### 2. 裁决 2：切换器只列已发布语言

- **运行时开关。** `src/i18n/runtime.ts` 新增 `publishedOnly`，默认关闭；client-web 在 `src/i18n/index.ts` 中开启。开启后行为如下：
  - 只使用并只提供已发布的语言。判定条件：manifest 有发布号，`locales` 中列出该语言，且该语言在该发布中有语言包；`en` 视为恒有。
  - manifest 读取失败，或 `releaseNo` 为 null 时，静默使用英文。`?lang=`、localStorage 中的 zh-CN 偏好保留，但这次不生效。此时 `locales()` 为空。
  - 以英文启动时，也会逐个拉取其他已列语言的 manifest 核对一次（`verifyOffered`，串行化、每个语言只查一次），因此切换器能显示简体中文。
- **切换器。** `LocaleSwitcher` 在可选语言少于 2 个时整体隐藏。
- **新增测试（3 项）：**
  - 503 或无发布时，`?lang=`、localStorage、浏览器语言都是 zh-CN，仍为英文，切换器为空，原偏好不被清除；
  - 有发布时只提供 en 与 zh-CN，未发布语言包的 `ar` 不出现，`setLocale('ar')` 回退英文；
  - 英文启动时也能发现 zh-CN。
- **浏览器复验**（Playwright，本地合成协议）：

  | 情况 | `<html lang>` | 标题 | 切换器 |
  |---|---|---|---|
  | 有发布，`/login?lang=zh-CN` | zh-CN | 欢迎回来 | English / 简体中文 |
  | manifest 返回 503 | en | Welcome back | 不显示 |

### 3. 第 3 项：seed 可直接导入

- `i18n/client-console.zh-CN.seed.json` 改为 `code-mor-i18n-translation/1`，带 `locale: "zh-CN"`，值全部为字符串，共 608 个 key。
- 覆盖测试同时校验格式、`locale` 与值类型。
- `scripts/i18n-export-source.mjs` 与 ops-web 版本一致，只有应用编码不同。它先校验 seed 格式，再按英文基准对齐 key（缺的补空字符串，多余的丢弃），另写出 `i18n-out/client-console.zh-CN.json`。
- 本次生成结果：608 个 key，空值 0 个。

### 4. 第 4 项：与 ops-web 运行时对齐

- 改动前，`runtime.ts` 与 `runtime.test.ts` 两份完全相同。
- 从 ops-web 带回一处：`appLocales` 的原生语言名改为 Unicode 转义，使 `src/i18n` 也可以做中日韩字符扫描。
- 以下属于 ops 专用，未带回：
  - `I18nGate`、`ensureStarted`（ops 首屏等待，以及忽略浏览器语言）；
  - `formatShortDateTime`；
  - ops 的 `document: null` 与默认 `zh-CN`。
- client-web 新改的 `runtime.ts` 与测试，ops-web 尚未同步。`publishedOnly` 默认关闭，ops-web 直接复制这两个文件，行为不变、测试不受影响。建议卡 C 同步，使两份运行时保持一致。

### 5. 第 5 项：manifest 路径

client-web 调用 `/api/client/i18n/v1/manifest?locale=&releaseNo=`，与卡 B 的 M2（client-console-bff，外部路径 `/api/client/i18n/v1/manifest`）一致。只传 `locale` 和 `releaseNo`。

卡 B 7.1 指出 Gateway 会抹掉缓存头。前端行为不受影响，每次全量读取 manifest。

### 6. 验证输出

```text
$ npm test
ℹ tests 80
ℹ pass 80
ℹ fail 0
$ npm run build
dist/assets/index-BSUyDOku.js   417.27 kB │ gzip: 123.22 kB
✓ built in 104ms
$ npm run i18n:export-source
Wrote .../i18n-out/client-console.en.json: 8 namespaces, 608 keys
Wrote .../i18n-out/client-console.zh-CN.json: zh-CN translation import file
```

`tsc --noEmit` 已作为 build 的一部分通过。

### 7. 本轮改动文件

- `src/i18n/runtime.ts`、`src/i18n/runtime.test.ts`、`src/i18n/index.ts`
- `src/LocaleSwitcher.tsx`、`src/i18n-coverage.test.ts`
- `i18n/client-console.zh-CN.seed.json`、`scripts/i18n-export-source.mjs`

第 8 节第 2 条（无发布时选中文仍显示英文）已由本轮消除。
