# 任务卡 D：client-console-web 多语言接入（含前端 i18n 运行时）

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n`（detached worktree）。**只改 `client-console-web/`**；卡 A 改 `paytech/`、卡 E 改 `agent-docs/` 与 `AGENTS.md`，不要动。
- 授权：普通文件编辑；在 `client-console-web` 内 `npm install`（只为新增 `intl-messageformat`）、`npm test`、`npm run build`。不提交、不推送、不启动或修改任何容器。
- 权威（主工作区，只读）：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-多语言管理/requirement.md`（R8、R9、B6、B7、AC-07、AC-08、AC-13）与 `technique.md` 第 3.6、3.7、3.10 节。若主工作区 `agent-docs/DEVELOPMENT_CONVENTIONS.md` 新增的"前端界面文案与语言"一节已出现在本 worktree，按它执行。

## 开工前

读 `AGENTS.md`、`agent-docs/UNIT_TEST_GUIDE.md` 中前端相关部分、`client-console-web/README.md`，通读 `client-console-web/src` 全部文件，理解现有页面、`auth.ts` 的请求封装（BFF 前缀 `/api/client`）与现有 node 测试写法。

## 范围

1. **运行时 `src/i18n/`**（这份代码之后会被复制到 operations-console-web，写得通用、无业务依赖，约 200 行内）：
   - 依赖 `intl-messageformat`：精确版本（不带 `^`/`~`），选一个发布至少两周的稳定版，写进 package.json 与 lock。
   - `I18nProvider`、`useT(namespace)` 返回 `t(key, params?)`、`useLocale()` 与 `setLocale()`；按 locale 缓存已编译消息。
   - 内置基准：`src/i18n/en/<namespace>.json`（扁平 `key → 文案`，可写成 `{ "text", "description" }`），构建期静态导入。
   - 运行时加载：`GET /api/client/i18n/v1/manifest?locale=<locale>[&releaseNo=]`（响应格式见 technique 3.7）；拿到后按需并行拉当前页面所需命名空间的 `en` 与目标语言语言包（直接用 manifest 里的 `url`，不带凭据）。manifest 或语言包任一失败都不阻塞渲染。
   - 查找顺序：运行时目标语言 → 运行时 `en` → 内置 `en` → key 本身（开发环境 `console.warn` 一次）。
   - 语言来源（需求 B6）：`?lang=`（仅本次会话）> localStorage（try/catch 包裹）> `navigator.language` 与 manifest 中启用语言的匹配 > 应用默认语言；`?release=N` 透传为 `releaseNo`。切换时更新 `<html lang dir>`。
   - 日期、数字用 `Intl`，按当前 locale。
2. **全量 key 化**：`src` 下所有界面文案改为 `t()`；命名空间按页面划分（如 `common`、`auth`、`onboarding`、`team`、`developer`、`workspace`，可按实际调整，在报告列出），枚举展示名放 `enum` 命名空间。**英文基准**由你根据原中文写出准确、自然的产品英文；原中文逐条保留为首批中文翻译。后端返回的 Problem `detail` 原样显示，不翻译（已知限制）。
3. **导入文件**：
   - `npm run i18n:export-source` 脚本：把 `src/i18n/en/*.json` 合并成 technique 3.5 的 `code-mor-i18n-source/1` 格式，输出到 `client-console-web/i18n-out/client-console.en.json`（该目录加入 .gitignore）。
   - 首批中文：`client-console-web/i18n/client-console.zh-CN.seed.json`（同结构，值为中文），供首次导入翻译使用，需提交。
4. **语言切换**：顶部栏与登录页提供 English / 简体中文 切换；两种语言下布局不溢出、不截断（以中文和英文中较长者检查按钮与表头）。
5. **测试**（node test，沿用现有风格）：
   - 运行时：查找回退顺序、ICU 复数与参数、manifest 失败回退内置英文、语言包失败回退、语言来源优先级、`?release=` 透传。
   - 硬编码扫描：`src` 下 `.ts/.tsx`（排除 `src/i18n/` 与测试文件）不得出现中日韩字符。
   - 一致性：`src/i18n/en` 与 seed 中文的 key 集合完全一致；代码中 `t()` 引用的静态 key 都存在于英文基准（可用简单正则扫描，动态 key 在报告说明）。
   - 现有测试全部继续通过；因文案改为英文导致的断言调整要保持原有语义。

## 验证

```bash
cd /Users/kenny/Desktop/Workspace/Code-MoR-i18n/client-console-web
npm install
npm test
npm run build
npm run i18n:export-source
```

## 交付报告（中文，写到 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-多语言管理/tasks/D-report.md`）

改动文件；`intl-messageformat` 版本；命名空间清单与 key 数；运行时 API 与文件结构（供卡 C 复制到 operations-console-web）；测试与构建实际输出；未覆盖或需裁决的问题。**完成后直接结束，不要等待后台事件。**
