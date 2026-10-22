# 任务卡 B 交付报告：运营「存储管理」（Operations BFF / Web / 注册块 130）

> 工作区：detached worktree `/Users/kenny/Desktop/Workspace/Code-MoR-filestore-ops`（基于 281a217d）。未提交、未推送、未建分支、未部署、未执行任何 SQL。

## 1. 已读路径

- 任务与合同：主工作区 `requirements/20261022/platform-support-文件多存储并存/` 的 `tasks/B-operations.md`、`tasks/A-platform.md`（第 3 节）、`requirement.md`、`technique.md`。
- 规范：`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/UNIT_TEST_GUIDE.md`、`agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`（3.3 BFF、4 API 合同）、`agent-docs/APPLICATION_ARCHITECTURE.md`（3 BFF、Operations Console）。
- 参照（块 125 币种维护全套）：`platform-support-initial-data.sql` 块 125 / 126、`requirements/20261020/platform-support-币种主数据维护与读取缓存/release/test-activate.sql`、ops-bff `CurrencySpecManagementController`、`PlatformCurrencySpecClient(Impl)`、`PlatformClientConfiguration`、`ConfigurationManagementRequestConfiguration`、`ConfigurationValidationConverter`、`application.yml`；测试 `CurrencySpecBrowserAutoTest`、`FinanceBrowserAutoTestBase`、`PlatformOperationsSimulator`、`OperationsConsoleBffApplicationAutoTest`、`OperationsHttpBodyNamingAutoTest`；ops-web `CurrencySpecs.tsx`、`currencySpecModel(.test).ts`、`ConfigurationStatusModal.tsx`、`useConfigurationForm.ts`、通知发送账号页（`NotificationProviders.tsx`、`ProviderModals.tsx`、`useNotificationCommand.ts`）、`pageCapabilities.ts`、`ConsolePage.tsx`；Gateway `GatewayPathTemplate`（变量段允许 `*`）、Platform `GatewayConfigurationCompiler`（响应超时上限 10 秒）。

## 2. 改动文件

| 范围 | 文件 | 说明 |
|---|---|---|
| 注册 | `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` | 末尾追加块 130（+203 行，见第 3 节） |
| 注册 | `paytech/platform-support/paytech-platform-support-access-control/src/main/resources/operations/console-resource-capabilities.json` | 登记 `OPERATIONS_FILE_STORAGE` / `/file-storage` / 动作 `OPERATIONS_FILE_STORAGE_MANAGE` |
| BFF 新增 | `ext/rest/FileStorageAdminController.java` | `/storage-admin/v1` 12 个端点，只做转发，带 `OperatorContext`；路径变量按合同正则校验 |
| BFF 新增 | `integration/PlatformFileStorageClient.java`、`integration/impl/PlatformFileStorageClientImpl.java` | 本地端口；写操作捕获 Platform 422 并只投影本次请求体内的字段路径 |
| BFF 改 | `configuration/PlatformClientConfiguration.java` | 登记 `FileStorageManagementApi`（字段错误解码 + 外显 Owner 业务原因 + 读取超时 9 秒）；`platformProxy` 增加读取超时参数，其他代理仍为 5 秒 |
| BFF 改 | `configuration/ConfigurationManagementRequestConfiguration.java` | `/storage-admin/v1/**` 纳入严格边界（未知 / 重复 Query、查询带 Body、桶检查带 Body 拒绝）；Platform `filestorage.request` 包的请求模型走严格 JSON 解析 |
| BFF 改 | `resources/application.yml` | `/storage-admin` 加入 metadata-only 日志前缀 |
| BFF 文档 | `paytech/operations-console-bff/README.md` | 存储管理一段 |
| BFF 测试 | `auto/FileStorageBrowserAutoTest.java`、`resources/file-storage-boundary.json`（新增）；`OperationsConsoleBffApplicationAutoTest`（路由清单 +12、日志前缀 +1）、`OperationsHttpBodyNamingAutoTest`（登记 Controller 与 API） | |
| Platform api（复制） | 见第 4 节 | 原样复制 |
| Web 新增 | `src/pages/FileStorage.tsx`、`src/fileStorage/fileStorage.ts`、`StorageModals.tsx`、`useStorageCommand.ts`、`fileStorage.test.ts` | 一个页面三个页签 |
| Web 改 | `src/navigation/pageCapabilities.ts`、`src/navigation/ConsolePage.tsx`、`src/configuration/configurationModel.test.ts`（页面数 58→59）、`README.md` | |

## 3. 注册块 130

- 行数：permission 2、role_permission 2、console_resource 2、gateway_route 12、route_header 62、guard_rule 72，共 **152 行**（文件 +203 行，含块注释、回退说明与激活语句注释）。
- ID：`900000000000130101`—`900000000000130772`（1301xx permission、1302xx role_permission、1303xx console_resource、1304xx route、1305xx route_header、1307xx guard_rule）。
- 块号：主工作区与两个 worktree 中该文件末尾都是块 126，127（工作流）、128（Webhook）、129（channel-connector）尚未出现；块 130 不依赖它们，块头注明这三个号段保留、本块可先后独立执行。
- 权限 `FILE_STORAGE_READ`（LOW）、`FILE_STORAGE_MANAGE`（HIGH）；菜单「平台配置」下「存储管理」，`/file-storage`，资源 / 组件键 `OPERATIONS_FILE_STORAGE`，排序 80（币种 70 之后）；按钮 `OPERATIONS_FILE_STORAGE_MANAGE`「维护」。
- 路由 12 条（`operations-file-storage-{instance-list|instance-create|instance-update|instance-credential|instance-status|bucket-create|bucket-check|bucket-status|route-list|route-create|route-update|route-delete}`），指向 ops-bff Target `900000000000000001`；两条 GET 用 READ，其余用 MANAGE。写路由 Body 上限 65536；桶检查无 Body（上限 0，不登记 content-type）。限流沿用块 125：GET 每人 30 / 分钟，写 10 / 分钟。
- 初始状态：菜单、按钮、路由 DISABLED，PLATFORM_ADMIN 两项授权 REVOKED；块注释写出激活语句（预期影响 12 / 2 / 2 / 1 行），未执行。
- metadata-only：`t_gateway_route` 没有日志模式列，Gateway 访问日志本来就不记录 Body；按现有约定（通知、Webhook、币种）在 BFF `web.logging.metadata-only-path-prefixes` 登记整个 `/storage-admin` 前缀；Platform 侧由任务卡 A 负责。

## 4. 复制的 api 文件

先于定稿从 A worktree 原样复制草稿以编译 BFF；收到主会话定稿消息后再次 `diff`，以下文件与定稿**完全一致，无需重新复制**（`ConfigurationMutationResponseBody.java` 未改动，本 worktree 原有版本与 A 相同；`files/`（InternalFileApi）按指示未复制）：

- `paytech/platform-support/paytech-platform-support-api/src/main/java/com/paytech/platform/support/api/filestorage/`：`FileStorageManagementApi.java`；`request/` 下 8 个（`CreateStorageInstanceRequestBody`、`UpdateStorageInstanceRequestBody`、`ReplaceStorageCredentialRequestBody`、`ChangeStorageStatusRequestBody`、`RegisterStorageBucketRequestBody`、`CreateStorageRouteRequestBody`、`UpdateStorageRouteRequestBody`、`DeleteStorageRouteRequestBody`）；`response/` 下 6 个（`StorageInstanceListResponseBody`、`StorageInstanceResponseBody`、`StorageBucket`、`StorageBucketCheckResponseBody`、`StorageRouteListResponseBody`、`StorageRouteResponseBody`）。
- `.../api/enums/`：`FileStorageType`、`FileStorageCredentialMode`、`FileStorageInstanceStatus`、`FileStorageBucketStatus`、`FileStorageCheckResult`。

定稿核对后已重跑第 6 节全部命令，结果不变。合入时以 A 的版本为准。

定稿消息中的核对项：权限 `FILE_STORAGE_READ=900000000000130101`、`FILE_STORAGE_MANAGE=900000000000130102` 与块 130 一致（块 130 以本版本为准）；12 条路由均为 `/api/operations/storage-admin/v1/**` → BFF `/storage-admin/v1/**`，菜单 `/file-storage`；删除路由为 DELETE 带 `expectedVersion`、`reason` 请求体（路由登记 content-type、Body 上限 65536，Web 与 BFF 测试 S12 覆盖）；字段校验错误 422（BFF 本地校验与上游 422 投影均已测试）。写回执按 `ConfigurationMutationResponseBody` 严格解码：桶的 objectCode 为"实例代码/桶名"，路由的 status 为 null。

## 5. 实现说明

- **BFF**：Controller → 本地端口 `PlatformFileStorageClient` → `FileStorageManagementApi` 代理，每个入口只调用一次 Platform，不重试，不接收或转发 `Idempotency-Key`。页面请求与响应直接使用 Platform 管理合同的模型（同 Vault 管理页的做法），没有另建一套同义 BFF 模型；严格解析扩展到 Platform `filestorage.request` 包，未知字段、类型强转仍在访问 Platform 前拒绝；未知上游字段（如凭据 Token）因 DTO 没有该字段而不投影。合同规定查询也带操作人 Header，所以两个列表同样转发 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`。
- **超时**：桶检查在 Platform 事务外依次访问 Vault 与存储，5 秒容易超时；为该代理单独设读取超时 9 秒，留在 Gateway 响应上限 10 秒以内。
- **密钥**：BFF 不保存、不回显；`/storage-admin` 只记元数据日志；本地 422 和上游 422 的错误响应只含字段路径。Web 只用密码框输入，提交后清空，不放入列表状态；写命令对象只在弹窗生命周期内保留（用于同内容重试复用 Idempotency-Key），弹窗销毁即释放。
- **Web**：三个页签——存储实例（列表 + 新建 / 编辑抽屉，类型切换时显示对应字段，MinIO 只能固定密钥且地址必填；更换密钥弹窗提示"保存后立即生效"；启停填原因，停用被引用时展示 Platform 原因）、桶（按实例筛选、登记、检查结果弹窗显示 6 种结果的中文说明与检查时间；启用需 10 分钟内检查通过，被规则引用的桶不能只读或停用，这些预检用 Tooltip 说明，最终以 Platform 为准）、写入规则（默认规则 `*` 显示为「默认」且不可删除；目标只列启用实例下的启用桶）。没有维护按钮时隐藏所有写入口。页面说明实例代码与桶名创建后不可修改、桶由基础设施预先创建只做登记和检查。
- **`*` 路径**：默认规则在浏览器、Gateway 路径变量和 BFF→Platform 调用中都按 `*` 原样传递（`encodeURIComponent` 不编码 `*`，Gateway 变量段允许 `*`，BFF 测试断言 Platform 收到 `/storage-admin/v1/routes/*`）。

## 6. 测试命令与结果

| 命令 | 结果 |
|---|---|
| `cd paytech && JAVA_HOME=…/jdk-25.jdk/Contents/Home ./mvnw -B -o -pl operations-console-bff/paytech-operations-console-bff-test -am test -Dtest='com/paytech/operations/console/bff/**/*' -Dsurefire.failIfNoSpecifiedTests=false` | BUILD SUCCESS；BFF 测试模块 **2057** 例，0 失败 / 0 错误 / 0 跳过（含 `ArchitectureAutoTest`）。其中 `FileStorageBrowserAutoTest` 37 例：12 端点转发（方法、路径、Body、操作人 Header、Request ID，不转发 Authorization / Cookie / Idempotency-Key）、12 端点授权拒绝、本地参数拒绝、Platform 业务拒绝原因外显（400 / 404 / 409）、503 映射、上游 422 路径投影、日志与响应无密钥、上游多余字段不投影、默认路由 `*` 与 IAM 角色切换 |
| `cd operations-console-web && npm ci --prefer-offline`（worktree 首次安装依赖） | 成功 |
| `npm test` | 1252 例全部通过（新增 `fileStorage.test.ts` 10 例） |
| `npm run build`（含 `tsc --noEmit`） | 通过；仅既有的 chunk 超过 500 kB 提示 |

BFF 测试不连接数据库；`-Dtest` 限定后上游 Reactor 模块不执行测试，没有触碰共享本机库。

## 7. 偏差与遗留项

1. **api 文件**：已与定稿核对一致，见第 4 节。
2. **Web 测试形态**：项目 Web 测试只有 `node --test` 的纯模型测试，没有组件渲染测试库。三个页签的主要交互（只读判定、状态动作与预检、请求体构造与密钥处理、列表与回执严格解码、Idempotency-Key 复用、桶检查无 Body）都抽到 `fileStorage.ts` 并由模型测试覆盖，没有引入新的测试依赖。
3. **截图**：本地预览需要登录会话、Gateway 与已激活路由，本次没有截图；`reports/assets/` 未创建。
4. **mor-test**：激活语句只写在块 130 注释里；发布时需要另写 `test-activate.sql` / `test-deactivate.sql`，并确认块 127—129 在 mor-test 的实际执行情况。
5. **Platform 侧 metadata-only**：Platform 的 `/storage-admin` 日志前缀与审计不含密钥由任务卡 A 负责，本卡未改 Platform 代码。
6. **合并**：`platform-support-initial-data.sql` 与 `console-resource-capabilities.json` 只做了末尾追加和单处插入；如果并行需求同时追加块 127—129，合并时保持块顺序即可。
