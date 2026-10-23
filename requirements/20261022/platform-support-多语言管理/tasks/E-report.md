# 任务卡 E 交付报告：决定记录与规范文档

工作目录 `/Users/kenny/Desktop/Workspace/Code-MoR-i18n`（基于 `22ede435`），只改了 `agent-docs/` 与 `AGENTS.md`，未提交、未推送，未改 `CURRENT_IMPLEMENTATION.md`。

## 改动文件

| 文件 | 改动 |
|---|---|
| `agent-docs/decisions/2026-10-22-ui-text-localization.md` | 新增 |
| `agent-docs/DEVELOPMENT_CONVENTIONS.md` | 新增第 10 节；第 1 节加一条范围说明；最后更新日期改为 2026-10-22 |
| `agent-docs/TECHNOLOGY_STACK.md` | 第 3 节技术矩阵加 ICU4J 一行；表后前端例外段追加 `intl-messageformat` |
| `AGENTS.md` | Start here 在文件上传原则一行之后加一条英文路由 |

## 要点

**决定记录**沿用 2026-10-22 data-query 决定的格式：状态头、背景、决定、已知限制、影响。

- **三类文案。** 前端界面文案、服务端报错与校验文案、业务内容分开治理。本决定只落地第一类，后两类另立需求。
- **归属。** Platform 拥有 Locale 目录、应用、命名空间、文案与发布。
- **key 来源。** key 来自代码导入，导入前预览差异。命名空间在后台创建，导入只接受已创建的命名空间。
- **语言范围。** 基准语言固定为 `en`，本期只支持 `en` 与 `zh-CN`。
- **发布。** 无审核，直接发布。发布不可变，语言包写入公开桶，数据库只存 `/files/v1/{fileId}` 与摘要。
- **上线与回滚。** 文件检查通过后由补偿事件自动上线。切回历史发布即为回滚，不重新生成文件。
- **前端加载。** 运行时加载语言包，回退顺序依次为运行时目标语言、运行时 `en`、构建内置 `en`。
- **已知限制。** Problem `detail` 仍为中文，ops-web 中英混排，孤儿语言包由文件服务清理。
- **R8 的调整。** 需求原写 ESLint 规则，按 S5 改为 node 测试，原因是两个前端工程都没有 ESLint。
- **共享应用。** 本期不建 `shared` 应用（S3），枚举展示名放各应用的 `enum` 命名空间。

**开发约定第 10 节：前端界面文案与语言。** 追加在文末而不是插到中间，这样已被 AGENTS.md 和 README 链接的第 5.1、5.4 节锚点不变。内容包括：

- tsx/ts 不写死文案，统一用 `useT(namespace)` 加 key。
- key 为 `<namespace>.<area>.<name>`，全部小写、点分，首段为命名空间编码。
- 英文基准写在 `src/i18n/en/<namespace>.json`。
- ICU MessageFormat 是唯一文案语法，复数和选择分支必须含 `other`。
- 枚举展示名放在 `enum` 命名空间。
- 硬编码检查用 node 测试实现。
- 既有页面按任务迁移；页面一旦迁移，就要完整遵守本节。

**技术栈登记。** ICU4J 写作 `com.ibm.icu:icu4j`，版本由 `paytech-dependencies` 固定。它只用于多语言模块以 `MessagePattern` 校验 ICU 语法，不在服务端格式化文案。本卡开工时仓库里还没有 ICU4J 的版本号，所以表中没有写具体版本。`intl-messageformat` 登记为 client-console-web 与 operations-console-web 的 i18n 运行时，精确版本以各自锁文件为准。

## 需要主会话知道的事

- 决定记录和 AGENTS 路由链接的 `requirements/20261022/platform-support-多语言管理/` 与 `requirements/20261022/platform-support-文件公开桶/`，目前只在主工作区存在，worktree 的基线提交里没有。这两个目录随需求一起提交后，链接才能在提交内解析。
- 卡里写的 "TECHNOLOGY_STACK 第 71 行" 指表后的前端例外段。新增 ICU4J 一行后，该段现在在第 72 行。

## `npm --prefix requirements-site test` 结果

worktree 内没有 `requirements-site/node_modules`，直接运行时构建失败：

```text
sh: vinext: command not found
```

为了不往 worktree 的 requirements-site 写东西，我在 scratchpad 做了一个镜像来运行：

- `requirements-site` 从 worktree 复制，`node_modules` 链接到主工作区。
- 其余顶层目录链接到 worktree，所以测试读到的是本卡改过的 `agent-docs` 和 `AGENTS.md`。

镜像目录在 `/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR/0cc285ca-0776-4a38-9e68-e0b8c88eaaec/scratchpad/i18n-site-check`。

| 运行 | tests | pass | fail |
|---|---|---|---|
| 本卡改动后 | 137 | 127 | 10 |
| 基线 `22ede435`（agent-docs 与 AGENTS.md 取自 HEAD） | 137 | 127 | 10 |

两次失败的用例集合完全相同，所以这 10 个失败已在基线中存在，与本卡无关。它们是 Commerce、Payment、Platform 快照、测试规范等旧断言与现行文档不一致，例如：

- 期望 Commerce 模型统计行为 `| Commerce | 15 | 249 | …`。
- 期望开发约定含"公共命令幂等组件……不因为公共包存在而强制接入"。

本卡未修这些失败。
