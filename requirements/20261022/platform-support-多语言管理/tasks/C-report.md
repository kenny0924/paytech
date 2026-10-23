# 卡 C 交付报告：operations-console-web 多语言管理页面

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n/operations-console-web`，只改了 `operations-console-web/**`。未提交、未推送，未启动或修改容器，未访问 mor-test。
- 已读：卡片 C、`requirement.md`、`technique.md` v3（3.4—3.9）、worktree 中 `DEVELOPMENT_CONVENTIONS.md` 第 10 节、Platform 合同 `paytech-platform-support-api/.../localization/**` 与 `LocalizationErrorCode`、卡 A 的 ICU 校验器与导入文件读取实现、卡 B 报告与 BFF 上传请求类、卡 D 报告与 client-web `src/i18n/`、ops-web `App.tsx`、`src/navigation/`、`src/api/session.ts`、`src/workflow/`、`src/dataQuery/`。9 张设计稿逐张看过，并用本地合成后端截图逐张对照（第 3 节）。

## 1. 结论

- 三个页面（翻译、发布记录、语言与应用）与 6 个弹窗按设计稿 01—09 实现，全部文案经 `t()`，英文基准 394 个 key，`zh-CN` 首批翻译逐条对应。
- `npm test` 1,379 项全部通过，原有 1,340 项保持通过，新增 39 项。`tsc` 与 `vite build` 通过。
- 组件键与按钮资源码已和卡 B 的注册块 135 完全对齐，上传接口按卡 B 实际合同实现。

## 2. 改动文件

新增：

- `src/i18n/`：运行时，从 client-web 复制。
  - `runtime.ts`、`runtime.test.ts` 原样复制。
  - `index.ts`：manifest 地址 `/api/operations/i18n/v1/manifest`，默认 `zh-CN`；不传 `document`，不改 `<html lang>`；`ensureStarted()` 首次进入本需求页面时才启动，并忽略浏览器语言。
  - `react.tsx`：复制后新增 `I18nGate`，等语言包就绪再渲染，最多等 2.5 秒，避免中文页面先闪英文。
  - `builtin.ts`。
  - `en/i18n-admin.json`（386 个 key）、`en/enum.json`（8 个 key）。
- `src/localization/`：页面与模型。
  - 页面：`TranslationsPage.tsx`、`ReleasesPage.tsx`、`SettingsPage.tsx`。
  - 弹窗：`ImportKeysModal.tsx`、`PublishModal.tsx`、`NamespaceModal.tsx`、`TranslationImportModal.tsx`、`BatchPasteModal.tsx`；新建应用、语言、切回、改名弹窗在页面文件内。
  - 编辑器：`IcuEditor.tsx`，高亮层叠透明 textarea。
  - 共用部件：`LocalizationParts.tsx`。外框只在本页面范围内按语言切换 antd 文案，还包括图标、分段选择、加载失败。
  - 接口：`localizationApi.ts`（路径、类型、解码）、`commandModel.ts` 与 `useLocalizationCommand.ts`（`Idempotency-Key` 复用、409 / 422 分类、上传）。
  - 纯模型：`icu.ts`、`translationModel.ts`、`releaseModel.ts`、`importModel.ts`、`settingsModel.ts`。
  - 测试：`icu.test.ts`、`models.test.ts`、`i18nCoverage.test.ts`。
  - 样式：`localization.css`。
- `i18n/operations-console.zh-CN.seed.json`：中文首批翻译，格式与 client-web seed 相同（`code-mor-i18n-source/1`）。
- `scripts/i18n-export-source.mjs`：输出 key 导入文件 `i18n-out/operations-console.en.json`，另把 seed 转成可在翻译页直接导入的翻译文件 `i18n-out/operations-console.zh-CN.json`（`code-mor-i18n-translation/1`）。

修改：

- `package.json` / `package-lock.json`：新增 `intl-messageformat` **12.1.3**（精确版本，同 client-web）与 `i18n:export-source` 脚本。
- `tsconfig.json`：开启 `resolveJsonModule`。
- `.gitignore`：忽略 `i18n-out/`。
- `src/navigation/pageCapabilities.ts`：新增 3 个页面与 7 个按钮。
- `src/navigation/ConsolePage.tsx`：懒加载 3 个页面。
- `src/configuration/configurationModel.test.ts`：页面总数由 65 改为 68。

未改：`src/main.tsx` 的全局 antd `zh_CN`、dayjs、`index.html` 和任何既有页面。

## 2a. 主会话追加的两项要求（已完成）

**1. 批量保存按体积切分。** 入口是编辑器"更多 → 批量粘贴{语言}译文"（需求 R4"批量粘贴"）。

- 输入：粘贴"key<Tab>译文"逐行内容、扁平 JSON，或导出的翻译文件。
- 检查：先读取该语言全部条目对比。只保存有变化、key 存在且通过前端 ICU 检查的条目。不存在的 key、有错误的条目和无法识别的行逐项列出原因。
- 切分：`splitBatches` 按请求体 ≤ 48 KB（`TextEncoder` 计字节）且 ≤ 200 条切分，保持原顺序。各批依次保存，并显示"第 i / N 批"进度。
- 部分失败：某批失败即停止，并报告四项：已保存条数、失败批号与条数、未尝试条数、原因。
  - 422 的 `errors[].field` 形如 `translations[i].text`（含 `I18N_MESSAGE_INVALID`），映射回对应 key 逐条显示。
  - 409 提示期间有人修改。
- 续存：修正后点"检查"即可保存剩余部分，已保存的条目会归入"无变化"。
- 逐条编辑每次只发 1 条，不会超限。

**2. 浏览器端 ICU 检查附位置。** 保存前做与 Platform 相同的检查：语法、参数集合与英文基准一致、plural / select / selectordinal 必须有 other。

- 语法与结构类问题附"（第 n 个字符）"。参数类问题已点名参数，不再附位置。
- 服务端 422（`I18N_MESSAGE_INVALID` 或 `VALIDATION_ERROR`）中 `translations[0].text` 的 message 显示在当前条的错误行；批量时按下标映射到对应 key。

**按钮码：** 主会话消息里写的是 `_NAMESPACE_CREATE`。卡 B 最终注册与 B-report 均为 `OPERATIONS_I18N_NAMESPACE_MANAGE`，前端与之一致。

## 3. 与设计稿逐张对照

对照方式：本地 Vite 加合成 BFF（会话脚本留在会话临时目录，未进仓库），用 Playwright 1.58.1 截取 1920×1080 截图；另截了 1280 宽与 `?lang=en` 英文界面。

**01 翻译 · 逐条编辑：一致。** 页头、页签计数、左侧状态图标与草稿蓝字、ICU 高亮、参数标签、"简体中文不区分单复数"提示、可改值预览（1 / 5 / 0）、底栏"保存并下一条 ⌘↵"均已实现。差异：

- 页头"N 处修改未发布"按文案修订号计算，即线上发布之后保存或导入的次数，不是逐 key 计数，因为合同没有逐 key 差异。
- 设计稿 01 同时有 38 条未翻译却没显示问题入口，与 02 自相矛盾。实现按 02：预检有阻止项时显示"N 个问题"，否则显示未发布状态。

**02 校验错误与问题：一致。** 包括红框、参数名下划线、"参数名不能翻译：基准里是 {name}，这里写成了 {名字}"、"改为 {name}"一键修正、预览占位、底栏"修正后才能保存"，以及问题浮层的定位、逐条处理和基准已变更复核。

- 预检只看已保存文案，所以正在编辑的错误也并入浮层，否则稿中"1 条译文有错误"不会出现。
- 前端校验规则与卡 A 的 `IcuMessageValidator` 一致：语法、`other` 分支、只允许 number / date / time、参数集合。服务端结果仍为准。

**03 导入 key：一致。** 包括选文件、本地核对格式与所属应用、上传、四类差异、查看明细、未创建命名空间、过期提示与底栏提示。

- 点"创建"打开命名空间弹窗并带上编码，建好后自动重新预览。
- 409 时提示重新预览；422 `I18N_NAMESPACE_NOT_FOUND` 时自动重新预览。
- 文件行的命名空间数与 key 数取自浏览器本地解析。

**04 发布确认：有偏差。** "检查结果"、发布说明、提示条与确认按钮一致，有阻止项时逐条列出并禁用确认。

- **偏差：**"变更摘要 与 #7 相比"改为"发布内容"，展示各语言覆盖、回退英文条数与基准已变更条数。原因是预检合同不返回与线上版本的差异。真正的差异在发布记录里按两次发布对比展示。
- 422 `I18N_COVERAGE_INSUFFICIENT` 按卡 B 的 `missing.{语言}` 形态列出缺口；409 `I18N_CONTENT_CHANGED` 提示重新发布并刷新预检。
- 发布成功后跳到发布记录并选中新发布。

**05 发布记录：基本一致。** 表格、状态徽标、详情、上线进度、每 2 秒刷新、变更摘要、语言包均已实现；READY / FAILED 时停止刷新，5 分钟仍在检查则停止并提示。差异：

- 列表"改动"列显示 key 数与发布说明，因为列表合同没有逐发布变更数。
- "检查文件"只显示检查中 / 已通过 / 失败原因，没有"9 / 12 已通过"，因为详情合同没有逐文件检查状态。
- 发布人显示账号 ID 末 6 位，因为 BFF 不返回姓名。最近修改人同理。

**06 切回确认：一致。** 影响行来自 `diff(目标, baseReleaseNo=当前)`，原因必填（256 字），比当前新的发布按钮文案为"上线此版本"。

**07 语言与应用：一致。** 应用表、默认语言、必须翻译语言、发布要求及即时是否满足、命名空间列表（点击进入翻译页并按命名空间筛选）均已实现。

- "中文覆盖"列改为"必须语言覆盖"，取必须语言中最低的覆盖率。
- 命名空间行多了"···"菜单，用于编辑；没有 key 时可删除。
- 面板底部"···"可修改名称、复制编码。

**08 新建应用：基本一致。** 编码即时校验格式与重复。

- **偏差：**说明条改为"创建后先建命名空间，再由开发导入 key"。设计稿写的"命名空间随导入自动生成"已被需求 C9 取代。

**09 新建命名空间：一致。** 包括格式、重复与"可用 · 代码中的 key 写作 billing.xxx"提示。排序值自动取最大值加 10。

**语言页签：** Locale 表含启停开关（`en` 不可停用）、文字方向、排序、编辑与新增语言。

**额外功能：** 编辑器"更多"菜单提供批量粘贴（见 2a）、导出 / 导入当前语言的翻译（需求 R4、AC-09），以及"把英文复制到输入框"和"还原"。

**状态覆盖：** 加载中、空（无应用、无目标语言、无 key、筛选为空、全部已翻译）、加载失败（带请求编号与重试）、403 无权限、只读（按钮资源缺失时隐藏写入口，编辑器只读）、写操作带 `expectedVersion` 且 409 提示刷新、离开有未保存修改时确认（切换 key、筛选、路由与关闭页面）。

## 4. 依赖的 BFF 端点

全部在 `/api/operations` 下，以卡 B 报告为准：

- 管理接口：`/localization-admin/v1` 下 23 个，C01—C11、T01—T06、R01—R06，路径与卡 B 第 2 节一致。
- 上传：`POST /localization-admin/v1/uploads`，请求 `{purpose: KEY_IMPORT|TRANSLATION_IMPORT, fileName, sizeBytes(字符串)}`。之后浏览器按授权直传 PUT，不带凭据；最后调 `POST /localization-admin/v1/uploads/{uploadId}/completion`。完成时扫描为 REJECTED / QUARANTINED 立即失败；仍在检查时，导入调用在约 16 秒内对 400 `BUSINESS_CHECK_FAILED` 递增重试。
- manifest：`GET /i18n/v1/manifest?locale=&releaseNo=`，匿名。
- 组件键：`OPERATIONS_I18N_TRANSLATIONS` / `_RELEASES` / `_SETTINGS`。
- 按钮：`OPERATIONS_I18N_TRANSLATION_EDIT`、`_KEY_IMPORT`、`_RELEASE_PUBLISH`、`_RELEASE_ACTIVATE`、`_APPLICATION_CREATE`、`_NAMESPACE_MANAGE`、`_SETTINGS_EDIT`。与卡 B 注册块一致，显示名也已对齐。

## 5. 测试与构建实际输出

```text
$ npm install --save-exact intl-messageformat@12.1.3
$ npm test
ℹ tests 1379
ℹ pass 1379
ℹ fail 0
$ npm run build
✓ built in 687ms   （TranslationsPage 39.7 kB、SettingsPage 21.9 kB、ReleasesPage 15.8 kB，均为懒加载分包）
$ npm run i18n:export-source
Wrote .../i18n-out/operations-console.en.json: 2 namespaces, 394 keys
Wrote .../i18n-out/operations-console.zh-CN.json: zh-CN translation import file
```

新增测试共 39 项：运行时 9 项、ICU 7 项、模型 17 项、覆盖与扫描 6 项。

- **运行时：** 回退顺序、ICU 复数、manifest 失败回退、按需拉包、语言来源、`?release=`。
- **ICU：** 与 intl-messageformat 的语法判定一致；每类错误码与位置；参数改名合并与一键修正；高亮切分；示例值。
- **模型：**
  - 页签计数、列表状态、可保存条件（含基准已变更时原样确认）、上下条。
  - 问题汇总（按命名空间分布、合并草稿错误）、未发布次数。
  - 轮询终止（READY / FAILED / 超时）、进度步骤、变更摘要不重复计数、切换影响。
  - 导入阻止条件（缺命名空间 / 问题 / 过期 / 无变化）、本地文件核对。
  - 编码校验、设置即时满足、幂等键复用、失败分类、解码。
- **覆盖与扫描：**
  - `src/localization/**` 与 `src/i18n/**` 无中日韩字符（排除注释与测试，同 client-web）。
  - 英文基准与 seed 的 key 集合一致且无空值。
  - 每条基准与 seed 都能被 intl-messageformat 解析，且参数集合一致。
  - 页面用到的静态 key 与动态 key 家族全部存在，基准里没有未使用的 key。

## 6. 待裁决问题

1. **首次发布前，Operations 新页面显示英文。** 运行时缺语言包时回退内置英文（B7）。要让新页面显示中文，需要依次完成：
   1. 在 `operations-console` 应用下创建命名空间 `i18n-admin` 与 `enum`。
   2. 导入 `i18n-out/operations-console.en.json`。
   3. 用"导入简体中文翻译文件"导入 `i18n-out/operations-console.zh-CN.json`。
   4. 发布。

   是否接受这段过渡期，或在部署时把这一步列为必做，请裁决。
2. **发布确认弹窗没有"与线上版本相比"的差异。** 如需按设计稿展示，需要卡 A 在预检返回中加入与当前发布的差异计数，即新增 key、各语言修改、英文改动、废弃。
3. **发布记录缺两项展示数据。** 一是逐文件检查进度"9 / 12 已通过"，二是列表逐发布变更数。需要合同补字段：详情返回已检查文件数，列表返回变更数。否则维持现状。
4. **页面只显示人员 ID。** 发布人、最近修改人目前只显示账号 ID 末 6 位。是否由 BFF 补充姓名（类似工作流的 people），请裁决。
5. **导入文件"尚未通过检查"没有专用错误码。** 前端只能对 400 `BUSINESS_CHECK_FAILED` 限时重试，真正的业务拒绝会晚约 16 秒才显示。建议卡 A 增加 `I18N_IMPORT_FILE_NOT_READY` 一类专用 Code。
6. **上传被文件策略拦下（卡 B 问题 2）。** operations-console-bff 的私有写入路由只允许 `text/csv`。修好之前，导入 key 与导入翻译会在完成上传阶段失败，页面显示"文件未通过检查"。
7. **seed 格式与 client-web 相同，不能直接导入。** 两个 seed 都是 `code-mor-i18n-source/1`，翻译导入接口要求 `code-mor-i18n-translation/1`。本卡的导出脚本已额外生成可导入的翻译文件，建议卡 D 的脚本做同样处理。
8. **ICU 原因说明依赖前端校验。** 卡 B 问题 4 指出，服务端 422 `VALIDATION_ERROR` 只带字段定位。页面的具体原因由前端校验给出，规则已与 Platform 对齐，后续若规则改动需两边同步。

本卡已完成，无后台任务。

## 跟进 2（2026-10-23，接线卡 A 新字段，A-report 11.6）

结果：`npm test` 1,381 项全部通过（比上一轮多 2 项），`tsc` 与 `vite build` 通过。`npm run i18n:export-source` 输出 2 个命名空间、409 个 key（i18n-admin 401、enum 8）。合成后端补上新字段后，重新截图对照原型 01、04、05，均与设计稿一致。

1. **发布确认（04）。** "变更摘要 与 #N 相比 · M 处"取自预检的 `changeSummary`，分四类：新增 key、各语言修改、英文改动、废弃。每行"查看"展开样例 key（每类最多 20 条），并注明"还有 n 条"。没有线上发布时显示"首次发布"。`changeSummary` 为 null 时退回原来的发布内容展示（覆盖率、回退英文、基准已变更），并加一行灰字"暂时无法与线上版本对比，先显示发布内容"，不报错。
2. **发布记录（05）。**
   - 列表"改动"列显示"M 处 · 发布说明"；早期发布没有摘要时仍显示 key 数。
   - 详情的变更摘要优先用发布自带的 `changeSummary`，展开显示样例 key。为 null 时才调用两次发布的差异接口，保留原来的完整对比。
   - "检查文件"按 `checkProgress` 显示"{available} / {total} 已通过"；`rejected` 大于 0 时追加红字"n 个未通过"。
3. **翻译页头（01）。**"N 处修改未发布"改用 `application.pendingChangeCount`，即去重后的 key 数。只有旧响应没有该字段时，才回退到按修订号估算，也只有这时才读取线上发布详情。
4. **导入时文件尚在检查。** 只对 409 `I18N_IMPORT_FILE_NOT_READY` 重试，界面显示"文件检查中…"，间隔依次为 1、2、3、4、5、5 秒，合计约 20 秒。
   - 重试用尽后提示"文件仍在检查，请稍后重新预览"，并提供"重新预览"按钮。
   - 其他错误立即显示，已去掉对通用 `BUSINESS_CHECK_FAILED` 的重试。
   - 确认导入时遇到该 409，会自动重新预览，不会误报"预览已过期"。
   - 导入翻译使用同样的逻辑。
   - 重试函数 `withScanRetry` 移到可在 node 中测试的 `commandModel.ts`，并新增测试：只重试该错误码、间隔正确、其他错误不重试、用尽后抛出最后一次错误。
5. **seed 格式。** `i18n/operations-console.zh-CN.seed.json` 改为 `code-mor-i18n-translation/1`（带 `locale: zh-CN`），在翻译页"导入简体中文翻译文件"可直接导入。
   - 覆盖测试同时校验格式与 locale。
   - 导出脚本校验 seed 格式，再按当前英文基准对齐 key（缺的补空字符串，即不改动；多余的丢弃），写出 `i18n-out/operations-console.zh-CN.json`。

**第 3 节原有偏差的变化**

- 04 的"变更摘要"偏差已消除。
- 05 的"改动"列与"9 / 12 已通过"偏差已消除。
- 01 的未发布数已改为去重 key 数。卡 A 11.9 说明：它按写入时间统计，把译文改回原值也会计入。
- 仍未解决：人员只显示账号 ID（第 6 节第 4 项）。

**第 6 节待裁决问题的变化**

- 第 2、3、5 项已由本轮解决。
- 第 7 项（seed 不能直接导入）在 ops-web 一侧已解决。client-web 的 seed（卡 D）仍是旧格式。

本轮新增或修改的文件全部在 `operations-console-web/` 下：`src/localization/` 的 `localizationApi.ts`、`releaseModel.ts`、`commandModel.ts`、`useLocalizationCommand.ts`、`PublishModal.tsx`、`ReleasesPage.tsx`、`TranslationsPage.tsx`、`ImportKeysModal.tsx`、`TranslationImportModal.tsx`、`models.test.ts`、`i18nCoverage.test.ts`，以及 `src/i18n/en/i18n-admin.json`、`i18n/operations-console.zh-CN.seed.json`、`scripts/i18n-export-source.mjs`。
