# 任务卡 B：运营「存储管理」（Operations BFF / Web / Platform 注册块 130）

> 需求 [requirement.md](../requirement.md) R7；方案 [technique.md](../technique.md) 4.2；Platform 接口合同见[任务卡 A](./A-platform.md) 第 3 节（12 个接口，前缀 `/storage-admin/v1`，权限 `FILE_STORAGE_READ` / `FILE_STORAGE_MANAGE`）。任务卡 A 正由另一个 agent 并行实现。

## 0. 工作方式

- **只在** `/Users/kenny/Desktop/Workspace/Code-MoR-filestore-ops`（detached worktree）中修改；主工作区与 A 的 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-filestore` **只读**。不建分支、不提交、不推送、不部署、不执行任何 SQL。
- **范围**：`paytech/operations-console-bff/**`、`operations-console-web/**`、`paytech/platform-support/docs/database/sql/platform-support-initial-data.sql`（只在末尾追加块 130）、Platform 注册相关的能力清单 JSON（如 `console-resource-capabilities.json`）。**不改** Platform Java 代码（A 负责）。
- **必读**：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/APPLICATION_ARCHITECTURE.md` 中 BFF 部分、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`。
- **参照**：币种维护（块 125）的全套：`platform-support-initial-data.sql` 中 `ID block 125` 段、ops-bff 的币种管理 Controller 与 `PlatformClientConfiguration` 登记、ops-web `src/pages/CurrencySpecs.tsx`、`pageCapabilities.ts`、`ConfigurationStatusModal`。需求说明见 `requirements/20261020/platform-support-币种主数据维护与读取缓存/technique.md` 2.2 节。
- 命令前台运行，不要等待后台事件；不要回复"等待中"后停下。不得调用 superpowers skill。

## 1. 接口合同的获取

- A 会先在其 worktree 的 `paytech/platform-support/paytech-platform-support-api` 中写出 `FileStorageManagementApi` 及 DTO。主会话会在合同定稿后发消息告诉你类路径。
- 收到之前：先做第 2、4 节（注册数据、Web 页面，按任务卡 A 第 3 节的字段写前端类型），以及 BFF 中不依赖合同类的部分。
- 收到之后：把 A worktree 中这些 api 文件**原样复制**到本 worktree 的同一路径（不要修改；发现问题报告主会话），再完成 BFF 并编译测试。合并时以 A 的版本为准。

## 2. Platform 注册块 130（追加到 `platform-support-initial-data.sql` 末尾）

- ID 为 18 位：`900000000000130xxx`，分段沿用块 125 的注释格式（1301xx permission、1302xx role_permission、1303xx console_resource、1304xx route、1305xx route_header、1307xx guard_rule，按实际需要）。块头写回退说明。追加前确认文件末尾现有的最大块号，不要与 127（工作流）、128（Webhook）、129（channel-connector）冲突；若主工作区中这些块尚未出现，也要为它们保留。
- 权限：`FILE_STORAGE_READ`、`FILE_STORAGE_MANAGE`。
- 菜单页：「平台配置」下「存储管理」，路由 `/file-storage`，资源 / 组件键 `OPERATIONS_FILE_STORAGE`；按钮 `OPERATIONS_FILE_STORAGE_MANAGE`。
- 网关路由：12 条，指向 ops-bff，GET 用 READ，写用 MANAGE。新建实例与更换密钥两条路由请求体含密钥：按现有约定设置为 metadata-only 访问日志（不记录 Body）。
- 初始状态沿用惯例：菜单、按钮、路由 DISABLED，PLATFORM_ADMIN 授权 REVOKED；另在块注释中写出激活语句（不执行）。
- 能力清单 JSON 与 ops-web `pageCapabilities.ts` 同步登记。

## 3. Operations BFF

- 新增 `FileStorageAdminController`（`/storage-admin/v1/**`），只做转发并带 `OperatorContext`；在 `PlatformClientConfiguration` 登记 `FileStorageManagementApi`。
- BFF 不记录、不回显密钥：确认 BFF 的请求日志对这两条路径不输出 Body。
- 测试：按 UNIT_TEST_GUIDE，用 Platform 测试 HTTP 端点返回配置的 DTO，覆盖转发、Operator 头、错误透传、无权限路径。只跑 operations-console-bff 模块（及其必要的 `-am`），不跑全仓库；本机库被其他会话共用。

## 4. Operations Web「存储管理」页

一个页面三个页签（或分区），无管理按钮权限时只读：

1. **存储实例**：列表（代码、类型、地址、区域、凭据方式、凭据已配置、状态、桶数、被路由引用数、更新时间）；新建抽屉（类型切换时显示对应字段；STATIC 显示 Access Key / Secret Key 密码框；MinIO 只能 STATIC）；编辑；更换密钥（单独弹窗，提示"保存后立即生效"）；启用 / 停用（填原因；停用时若有路由引用，展示后端错误）。
2. **桶**：按实例展开或筛选；登记桶；"检查"按钮显示检查结果（`OK`、`NOT_FOUND`、`VERSIONING_DISABLED`、`ENCRYPTION_DISABLED`、`ACCESS_DENIED`、`UNAVAILABLE` 的中文说明与检查时间）；启用（需 10 分钟内检查通过）、只读、停用。
3. **写入规则**：列表（来源系统、实例、桶、检查策略、上传期限）；默认规则 `*` 显示为"默认"且不可删除；新建 / 编辑 / 删除。

- 页面文案说明：实例代码与桶名创建后不可修改；桶由基础设施预先创建，这里只登记和检查。
- 前端不缓存、不回显密钥；表单提交后清空密钥字段。
- 测试：组件 / 页面测试覆盖三个页签的主要交互与只读模式；`npm` 测试与构建通过。

## 5. 交付报告

写到 `/Users/kenny/Desktop/Workspace/Code-MoR-filestore-ops/requirements/20261022/platform-support-文件多存储并存/reports/B-operations-report.md`：已读路径、改动文件、注册块 130 行数统计、复制的 api 文件清单、测试命令与结果、截图（如可本地预览，放 `reports/assets/`）、偏差与遗留项。
