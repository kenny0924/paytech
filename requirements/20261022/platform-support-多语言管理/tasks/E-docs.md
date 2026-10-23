# 任务卡 E：决定记录与规范文档

- 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-i18n`（detached worktree，基于 main `22ede435`）。**只改 `agent-docs/` 与 `AGENTS.md`**；卡 A 同时在本目录改 `paytech/`，不要动。
- 授权：普通文件编辑、运行 `npm --prefix requirements-site test`（只读检查，失败如与本卡无关照实记录）。不提交、不推送。
- 权威（主工作区，只读）：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-多语言管理/requirement.md` 与 `technique.md`（v3）。

## 开工前必读

`AGENTS.md`、`agent-docs/DOCUMENT_GOVERNANCE.md`、`agent-docs/README.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/TECHNOLOGY_STACK.md`（第 3 节与第 71 行前端例外写法）、最近两份决定记录作为格式参考：`agent-docs/decisions/2026-10-22-data-query-into-platform-support.md`、`agent-docs/decisions/2026-10-22-file-upload-address-principles.md`。

## 范围

1. 新增 `agent-docs/decisions/2026-10-22-ui-text-localization.md`：背景；三类文案的治理边界（前端 UI 文案 / 服务端报错与校验文案 / 业务内容），本决定只落地第一类，后两类另立；Platform 拥有 Locale 目录、应用、命名空间、文案与发布；key 来自代码、命名空间后台创建、导入只接受已创建命名空间；基准语言固定 `en`，本期只支持 `en`、`zh-CN`；无审核直接发布；发布不可变、语言包存公开桶、库只存文件地址、检查通过后自动上线、切回即回滚；前端运行时加载 + 构建内置英文回退；Problem `detail` 仍为中文是已知限制。链接需求目录。
2. `agent-docs/DEVELOPMENT_CONVENTIONS.md` 新增一节"前端界面文案与语言"（放在合适位置，保持编号连贯）：tsx 不写死界面文案，统一 `useT(namespace)` + key；key 命名 `<namespace>.<area>.<name>`，小写点分；英文基准写在 `src/i18n/en/<namespace>.json`；ICU MessageFormat 是唯一文案语法；枚举展示名放各应用的 `enum` 命名空间；硬编码检查以 node 测试实现；既有页面按任务迁移，不强制一次改完。
3. `agent-docs/TECHNOLOGY_STACK.md`：第 71 行前端例外处登记 `intl-messageformat`（client-console-web、operations-console-web 的 i18n 运行时，精确版本以各自 lock 文件为准）；服务端依赖表登记 ICU4J（仅 Platform 多语言模块用于 ICU 语法校验，版本由 BOM 管理）。
4. `AGENTS.md`：在 Start here 合适位置加一条路由（改前端文案、Locale、语言包、多语言模块前先读该决定与需求目录）。保持该文件既有风格：一句英文路由，不复制细节。
5. 不改 `CURRENT_IMPLEMENTATION.md`（实现与部署状态在发布阶段登记）。

## 交付报告（中文，写到 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-多语言管理/tasks/E-report.md`）

改动文件与要点；`npm --prefix requirements-site test` 结果。**完成后直接结束。**
