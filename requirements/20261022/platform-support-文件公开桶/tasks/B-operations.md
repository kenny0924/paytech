# 任务卡 B：Operations BFF、Operations Web 与 Platform 注册块 134

工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-pubbucket-ops`（detached，基于 main `42879401`，已叠加主工作区按钮布局的 6 个未提交文件，其中包含 `src/components/RefreshButton.tsx`）。**不建分支、不提交、不 install、不推送、不碰 mor-test、不执行任何 SQL。** 代码只改工作目录；报告写到主工作区本需求目录 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-文件公开桶/reports/B-operations-report.md`。

## 1. 必读

1. 根 `AGENTS.md`；`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/APPLICATION_ARCHITECTURE.md`（BFF 职责）。
2. 本需求目录（主工作区绝对路径）下 `requirement.md`、`technique.md`、`tasks/common.md`（**合同以 common.md 为准**）。
3. 前置需求的 B 卡与报告：`requirements/20261022/platform-support-文件多存储并存/tasks/B-operations.md`、`reports/B-operations-report.md`、`release/sql/10-registration-block130.sql`、`release/test-activate.sql`。
4. 页面原型：Pencil 文件 `operations-console-web/docs/operations-console.pen`（加密，只能用 Pencil MCP 工具读取；当前已在 Pencil 中打开，只读，**不要修改**）。y=86700 一行 5 个画板：存储管理 01 检查策略、02 桶、03 写入规则、文件记录 01 列表、02 详情。用 `get_app_state` / `execute` 中的 `Get` 与 `TakeScreenshot` 查看结构和截图。

## 2. 工作顺序

1. **注册块 134 + 块 130 修改**：写入工作目录的 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`（自包含块，风格与块 130 一致，初始 DISABLED / REVOKED），同时在需求目录写 `release` 用的独立 SQL 草稿 `db/platform-support/registration-block134.sql`、`db/platform-support/block130-route-path.sql` 以及激活 / 停用脚本草稿（都不执行）。开工前核对 134 未被占用（主工作区与各 worktree 的 initial-data、`requirements/2026102*` 下的 SQL）。若 Platform 有校验注册块的 auto 测试，可运行其中只读的部分；不跑会清库的全量测试。
2. **Web**（不依赖 api）：按原型和 common.md 实现。`fileStorage.ts` 的严格解码器、请求体构建器同步新字段；新页面放 `src/pages/FileRecords.tsx` + `src/fileRecords/*`；`navigation/ConsolePage.tsx` 注册组件 `OPERATIONS_FILE_RECORDS`、`pageCapabilities.ts` 增加页面能力；审计页的动作 / 资源类型中文名补齐新增审计动作和 `FILE_SCAN_POLICY`、`FILE`；详情页的"最近下载"嵌入 `Audit`（`objectFilter={resourceType:'FILE', resourceId}`, `embedded`）。补单元测试（解码器、请求体、筛选参数、权限按钮）。`npm test` 与 `npm run build` 通过。
3. **BFF**：主会话会把 A 卡定稿的 api 文件复制到本工作目录并发消息通知你（消息内容含 `API_COPIED`）。在此之前先完成 1、2；收到后在 `FileStorageAdminController` / `PlatformFileStorageClient(Impl)` 同路径转发全部新增和修改路径，严格请求体解析配置登记新请求类，`application.yml` metadata-only 前缀确认覆盖。补 BFF 测试（参照既有 FileStorageAdmin 测试）。**如果完成 1、2 时还没收到通知，就在报告中写明"等待 api"并结束本轮（不要空等）**，主会话会再唤醒你。
4. JDK 25：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；Maven `~/.m3`，离线 `-o`；BFF 只跑 BFF 模块测试。命令在前台执行。

## 3. 页面要点（对照原型）

- 存储管理：页签"存储实例 / 桶 / 写入规则 / 检查策略"。桶：访问级别标签、对外地址列、登记弹窗（访问级别单选、公开时对外地址必填）、草稿公开桶可编辑对外地址、检查结果新文案（`PUBLIC_ACCESS_ALLOWED` = 桶允许匿名访问；`PUBLIC_READ_FAILED` = 对外地址不可访问），检查弹窗失败时显示公开只读策略指引（静态文案）。写入规则：来源系统 × 私有 / 公开矩阵，未配置格提示；编辑抽屉中来源与级别只读、桶只列同级别 ACTIVE 桶、检查策略下拉只列有生效修订的策略；删除按 `/{sourceSystem}/{accessLevel}`。
- 检查策略：列表 + 右侧详情（修订时间线；草稿在时间线内编辑允许类型与最大大小；激活 / 废弃 / 停用确认；只读检查要求；引用的写入规则；"含无法识别的类型"提示）。新建策略弹窗。
- 文件记录：一行筛选（时间范围必填，默认近 7 天，最长 31 天）+ 快捷筛选（全部 / 检查中 / 检查未通过 / 有替换进行中 / 公开文件，**不显示计数**）、分页表格、右侧快速预览（图片文件在已授权下载时才加载预览，否则只显示图标）；`?file=<id>` 详情：摘要、公开地址条（复制 / 打开 / 复制带版本地址）、版本历史（可下载的版本显示下载，需 `OPERATIONS_FILE_RECORD_DOWNLOAD` 按钮权限）、检查结果、上传记录、最近下载（嵌入审计）。
- 刷新统一用 `RefreshButton`；筛选尽量一行；写命令一律声明 JSON Content-Type（前置需求的教训）。

## 4. 交付

报告写明：注册块行数统计与 ID 段、块 130 改动、Web / BFF 改动文件、测试命令与结果、原型中未实现的部分及原因、偏差与遗留。不要提交。
