# 任务卡 B 交付报告：Operations BFF、Operations Web 与 Platform 注册块 134

> 工作区：detached worktree `/Users/kenny/Desktop/Workspace/Code-MoR-pubbucket-ops`，**已变基到 main `22ede435`**（卡 BIN + 3DS，提交 `de4f7ba4`），含按钮布局未提交文件。未建分支、未提交、未 install、未推送、未执行任何 SQL、未碰 mor-test。主工作区只写了本需求目录下 `db/platform-support/*.sql` 草稿与本报告。

## 1. 已读路径

- 任务：本需求 `tasks/B-operations.md`、`tasks/common.md`（合同）、`requirement.md`、`technique.md`；前置需求 `platform-support-文件多存储并存` 的 B 卡、B 报告、`release/sql/10-registration-block130.sql`、`release/test-activate.sql` / `test-deactivate.sql`。
- 规范：`AGENTS.md`（已在上下文）；按前置 B 报告已核对的同一组约定（DEVELOPMENT_CONVENTIONS、JAVA_PROJECT_STRUCTURE 3.3、UNIT_TEST_GUIDE、APPLICATION_ARCHITECTURE 中 BFF 部分），本卡改动沿用块 130 / 131 与既有 BFF / Web 实现的形态。
- 原型：Pencil `operations-console.pen` y=86700 五个画板（`k9v5n` 检查策略、`sZkfg` 桶、`orHrv` 写入规则、`S66G09` 文件记录列表、`bTmME` 详情），只用 `get_app_state` 与 `execute`（Get 文本树 / TakeScreenshot），未修改。
- 接口：worktree 中已复制的 A 卡 api 文件（`FileStorageManagementApi`、`InternalFileApi` 及请求 / 响应类）。**说明：截至本报告未收到含 `API_COPIED` 的消息，但 api 文件已出现在 worktree，故按其完成了第 3 步；若定稿还会变化，请再通知我对比。**

## 2. 注册块 134 与块 130 修改

- 占用核对：主工作区与全部 `Code-MoR-*` worktree 的 `platform-support-initial-data.sql`、`requirements/2026102*` 下 SQL 中均无 `900000000000134xxx`，块 133 已被 3DS 使用、132 被卡 BIN 使用；134 空闲。
- 位置：worktree `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 末尾，自包含块，风格同块 130 / 131。
- 行数：permission 2、role_permission 2、console_resource 2、gateway_route 12、route_header 56、query_param 11、guard_rule 72，共 **157 行**。
- ID：1341xx 权限（134101—134102）、1342xx 角色授权（134201—134202）、1343xx 资源（134301—134302）、1344xx 路由（134401—134412）、1345xx Header（134501—134556）、1346xx Query（134601—134611）、1347xx Guard（134701—134772）。
- 权限：`FILE_RECORD_READ`（FILE_RECORD / READ / LOW）、`FILE_RECORD_DOWNLOAD`（FILE_RECORD / **DOWNLOAD** / HIGH）。动作选择说明：`t_permission.action_code` 列注释为"受控代码目录，非固定枚举"，Platform 只校验 `[A-Z][A-Z0-9_]{0,63}`；同资源下 `READ` 已被占用（`uk_resource_action`），现有值中没有表达"取得内容"的代码（EXECUTE / OPERATE / HANDLE 语义都不贴切），故用 `DOWNLOAD`。如主会话认为必须复用现有值，建议改为 `EXECUTE`（只改一处值与 BFF 注册测试的一处断言）。
- 资源：菜单 `OPERATIONS_FILE_RECORDS`「文件记录」，父 `900000000000000562`，`/file-records`，组件同名，READ 权限，**sort 85**（存储管理 80 与卡 BIN 库 90 之间）；按钮 `OPERATIONS_FILE_RECORD_DOWNLOAD`「下载」。PLATFORM_ADMIN 授权 REVOKED，菜单 / 按钮 / 路由 DISABLED。
- 路由 12 条：`operations-file-storage-bucket-update`、`operations-file-scan-policy-{list|detail|create|revision-create|revision-update|revision-discard|activate|retire}`、`operations-file-record-{list|detail|download}`。存储类沿用 130101 / 130102，文件记录用 134101 / 134102。Header / Guard 照抄块 130：GET 只有 authorization + origin、读限流 30/分钟；写加 content-type 与 idempotency-key、写限流 10/分钟；废弃草稿 DELETE 带 Body（同块 130 的路由删除）；下载 POST 无 Body（上限 0）但登记 content-type（同块 130 的桶检查）。Query 白名单：列表 10 个（createdFrom、createdTo、fileId、sourceSystem、reasonCode、accessLevel、scanStatus、hasPendingReplacement、page、size），下载 `versionNo`。
- 块 130 修改：`operations-file-storage-route-update` / `route-delete`（130411 / 130412）外部与下游路径改为 `.../routes/{sourceSystem}/{accessLevel}`（改既有行），块 130 头注释补一句说明。
- 能力清单：`paytech-platform-support-access-control/.../operations/console-resource-capabilities.json` 与 Web `pageCapabilities.ts` 同步登记 `OPERATIONS_FILE_RECORDS` / `/file-records` / `OPERATIONS_FILE_RECORD_DOWNLOAD`「下载」（Platform 资源，A 卡未改该文件，合并时注意）。
- 需求目录草稿（未执行）：`db/platform-support/registration-block134.sql`（与 initial-data 块 134 逐字相同，加执行前核对说明）、`block130-route-path.sql`（UPDATE 2 行，带旧路径条件与注释形式的回滚）、`activate-block134.sql`（预期 12 / 2 / 2 / 1）、`deactivate-block134.sql`。
- 校验：新增 BFF 只读测试 `FileRecordRegistrationAutoTest`（解析 initial-data，不连库）覆盖行数 / ID 段、权限与菜单、块 130 + 134 路由恰好覆盖 `FileStorageAdminController` 每个入口一次（含 130 两条路径已带 `/{accessLevel}`）、Body 上限、Query 白名单、Header 与 Guard、边界样例的路由 Code。Platform 侧没有只读的注册块测试可跑（Platform auto 测试会清库，未运行）。

## 3. Operations Web

| 文件 | 改动 |
|---|---|
| `src/fileStorage/fileStorage.ts` | 访问级别、两种新检查结果与中文、公开 / 私有排查指引；桶与路由解码加 `accessLevel` / `publicBaseUrl`（私有桶地址必须为空，公开桶地址须 HTTPS、无查询串 / 片段、不以 / 结尾）；路由唯一键改为来源 + 级别、`routeMatrix`、`missingRouteNotice`、`routePath(source, level)`、`routeObjectCode`；`registerBucketBody`、`updateBucketBody`、`editablePublicBaseUrl`、`filterBucketRows`；`routeTargets` 按级别过滤；默认私有规则不可删，默认公开规则可删 |
| `src/fileStorage/scanPolicy.ts`（新） | 检查策略路径、严格解码（列表 / 详情 / 回执 objectCode `{策略}/{修订}`）、请求体（MB 录入换算字节字符串，类型去重并限定可识别类型）、新建修订 / 停用的前置原因、激活影响说明、规则下拉可选策略 |
| `src/fileStorage/ScanPolicyTab.tsx`（新） | 检查策略页签：左列表（含"无法识别的类型"提示）+ 右侧详情（修订时间线、草稿就地编辑与保存 / 废弃 / 激活确认、只读检查要求、引用的写入规则）、新建策略 / 新建修订 / 停用弹窗 |
| `src/fileStorage/StorageModals.tsx` | 登记桶加访问级别单选与对外地址；新增 `BucketUrlModal`；检查弹窗显示访问级别、对外地址、失败时静态指引（公开桶 AWS 只读策略示例可复制、私有桶说明），公开地址失败时提供"编辑对外地址"；写入规则弹窗：编辑时来源与级别只读、桶只列同级别 ACTIVE 桶、策略下拉只列有生效修订的策略、保存后预览；删除按 `/{source}/{level}` |
| `src/pages/FileStorage.tsx` | 四个页签；桶表访问级别标签、对外地址列、访问级别筛选、三栏级别说明；写入规则改为来源 × 私有 / 公开矩阵，未配置格提示回落或 503 并可"＋ 配置"；刷新统一用 `RefreshButton`；检查策略单独加载，失败不影响其他页签 |
| `src/fileStorage/fileStorage.css`（新） | 页签样式 |
| `src/fileRecords/fileRecords.ts`（新） | 文件记录路径、权限判定、严格解码（列表 / 详情 / 下载授权）、筛选参数（时间必填默认近 7 天、≤31 天、转 UTC；文件 ID / 来源 / 原因代码预检）、URL 读回、快捷筛选 |
| `src/fileRecords/FileRecordList.tsx`、`FileRecordDetailView.tsx`、`FileRecordParts.tsx`、`useFileRecordDownload.ts`、`fileRecords.css`（新） | 列表（一行筛选 + 快捷筛选 + 分页表格 + 右侧快速预览）与 `?file=<id>` 详情（摘要卡、公开地址条、版本历史与按版本下载、检查结果、上传记录时间线、嵌入审计的最近下载） |
| `src/pages/FileRecords.tsx`（新）、`src/navigation/ConsolePage.tsx`、`pageCapabilities.ts` | 注册组件 `OPERATIONS_FILE_RECORDS` 与页面能力 |
| `src/pages/auditView.ts` | 补齐块 130 与本次全部存储 / 策略 / 文件记录审计动作及 `FILE_DOWNLOAD_AUTHORIZE` 的中文名；资源类型补 `FILE_STORAGE_INSTANCE/BUCKET/ROUTE/CATALOG`、`FILE_SCAN_POLICY`、`FILE` |
| `src/configuration/configurationModel.test.ts` | 页面数 64→65 |
| `README.md` | 新增一段说明 |

写命令（含无 Body 的下载）一律带 `Content-Type: application/json`。下载与私有图片预览每次申请一次授权（会写下载审计），地址不保存、不展示；公开图片预览用带版本的公开地址，不产生授权。

## 4. Operations BFF

| 文件 | 改动 |
|---|---|
| `ext/rest/FileStorageAdminController.java` | 新增桶对外地址修改、检查策略 8 个、文件记录 3 个入口；写入路由编辑 / 删除改为 `/routes/{sourceSystem}/{accessLevel}`；路径变量与 Query 按合同正则校验（列表 size ≤200、版本号 ≥1） |
| `integration/PlatformFileStorageClient.java`、`impl/PlatformFileStorageClientImpl.java` | 同名转发，写操作沿用 422 字段路径投影；列表条件用端口内 `FileRecordQuery` record 原样转发 |
| `configuration/PlatformClientConfiguration.java` | `FileStorageManagementApi` 代理额外登记 `FileErrorCode`（否则 Platform 返回 FILE_NOT_FOUND / FILE_STATE_CONFLICT 会被当作上游无效响应 502） |
| 严格解析 / 日志 | 新请求类都在 `filestorage.request` 包，已被现有包级规则覆盖，无需改；`/storage-admin` 已在 metadata-only 前缀，确认覆盖（下载地址不进日志有测试） |
| `README.md` | 新增一段 |
| 测试 | `FileStorageBrowserAutoTest`（S01—S24 转发含 Query、授权拒绝、本地参数拒绝、Owner 拒绝原因含 409 FILE_STATE_CONFLICT、503 映射、下载地址不进日志、默认路由 `*/PRIVATE`）；`file-storage-boundary.json` 加 12 条样例并按新字段更新旧样例；`OperationsConsoleBffApplicationAutoTest` 入口清单 +12 / 改 2；新增 `FileRecordRegistrationAutoTest` |
| api 变化连带的测试夹具 | `InternalFileApi` 响应新增必填字段后，`ClientOnboardingReviewBrowserAutoTest` 的 StoredFile 样例与 `workflow-boundary.json` WF31 完成回执样例缺字段导致 502，已补 `accessLevel` / `versionNo` 等字段 |

## 5. 测试命令与结果

| 命令 | 结果 |
|---|---|
| `cd operations-console-web && npm test` | 变基后 1358 例全部通过（本卡新增 18 例：`fileStorage.test.ts` +3、`scanPolicy.test.ts` 6、`fileRecords.test.ts` 9；变基前为 1345） |
| `npm run build`（含 `tsc --noEmit`） | 通过；只有既有的 chunk >500 kB 提示 |
| `cd paytech && JAVA_HOME=…/jdk-25.jdk/Contents/Home ./mvnw -B -o -pl operations-console-bff/paytech-operations-console-bff-test -am test -Dmaven.repo.local=~/.m3/repository -Dtest='com/paytech/operations/console/bff/**/*' -Dsurefire.failIfNoSpecifiedTests=false` | 变基后 BUILD SUCCESS，**2272** 例 0 失败（含 `FileStorageBrowserAutoTest` 68、`FileRecordRegistrationAutoTest` 5、`CardBinLibraryBrowserAutoTest` 28、`ArchitectureAutoTest`；变基前为 2244）。变基前首轮有 4 例因 api 夹具缺字段失败，补夹具后全过。BFF 测试不连库，上游模块不执行测试 |

## 6. 原型中未实现的部分

1. 策略说明文字（如"客户入驻材料"）、快捷筛选计数、写入规则来源系统的中文说明（如"客户控制台"）：无后端字段（common.md 已约定不做）。
2. 检查策略列表下方"最近变更"：无对应接口；审计组件的嵌入筛选要求资源 ID，不支持只按资源类型列出。可在审计日志页按对象类型「检查策略」查看。
3. 桶检查弹窗的逐步结果（桶存在 / 版本控制 / 加密 / 写入 / 读回 / 匿名读取 / 删除各一行及 HeadBucket、版本号等细节）：检查接口只返回一个结果码，只显示最终结果与静态指引。
4. 上传记录中"发现 2 个新版本，已作废并恢复公开内容为 v2"之类的过程说明：接口只有上传状态，没有作废原因字段，显示状态（已作废 / 已过期等）。
5. 详情页最近下载中的"运营控制台 / 内部接口"渠道列：沿用嵌入审计组件的统一列，未单独定制。

## 7. 偏差与遗留

1. **权限动作**用了 `DOWNLOAD`，见第 2 节说明；如需改为现有值请告知。
2. 收到两次 `API_COPIED` 通知（第二次为重新复制：`StartFileReplacementRequestBody` 加 `@Null` 字段，与 BFF 无关；回执 objectCode 路由 `{来源}/{级别}`、策略 `{策略代码}/{修订号}`，与已实现的 Web 解码一致）。重新复制后重跑相关 BFF 测试（FileStorage、FileRecordRegistration、ApplicationAutoTest、HttpBodyNaming、ClientOnboardingReview、Workflow）229 例全部通过。
3. 下载 409 的错误码：前端与 BFF 均按 Platform 文件错误码 `FILE_STATE_CONFLICT` 处理；若 A 用了其他码（如 `CONCURRENT_REQUEST`），BFF 也能透传（已登记），Web 只看状态码。
4. **client-console-bff 可能受同样影响**：`InternalFileApi` 的 `FileUploadCompletionResponseBody` / `StoredFileResponseBody` 新增必填字段，client-console-bff 测试夹具若是旧形状会 502（本卡未跑、未改该模块）。
5. Platform 侧 `console-resource-capabilities.json` 由本卡修改，A 卡如也改该文件合并时注意顺序。
6. mor-test 发布：块 134 依赖本需求 Schema 变更；`block130-route-path.sql` 必须与 Platform / ops-bff / ops-web 新镜像同一窗口执行（否则新旧路径互相 404）；ops-web 现网的按钮布局未提交改动需一并带上。
7. 截图：本地预览需要登录会话与已激活路由，未截图。

## 8. 变基到 main `22ede435`（2026-10-22）

- 备份：`~/Code-MoR-backups/pubbucket-B-20261022225239.patch`（`git diff HEAD --binary`）、`.untracked.txt` 与 `.untracked.tgz`（未跟踪文件，不含 `node_modules`，含 `RefreshButton.tsx` 与本卡新文件、A 的 api 新文件）。未用 git stash、未提交。
- 步骤：`git reset --hard 22ede435` → 解压未跟踪文件 → `git apply --3way` 补丁 → 解决冲突 → `git reset -q`（只清 worktree 自己的 index，改动保持未暂存）。
- 冲突（均保留双方）：
  - `platform-support-initial-data.sql`：main 新增块 133（3DS 回调）与块 132（卡 BIN 库），块 134 接在块 132 之后。核对：块 134 之前没有任何 `900000000000134xxx`；块 130 的 130411 / 130412 仍为 `/{sourceSystem}/{accessLevel}` 路径；卡 BIN 库菜单 sort 90，文件记录 85 不冲突。需求目录的 `registration-block134.sql` 与 initial-data 块 134 仍逐字一致。
  - `ConsolePage.tsx`、`pageCapabilities.ts`：卡 BIN 库与文件记录两项都保留。`console-resource-capabilities.json` 自动合并，两项都在。
  - `OperationsConsoleBffApplicationAutoTest`：入口清单保留卡 BIN 库 9 条与本卡 14 条。
  - `configurationModel.test.ts`：自动合并后两边都是 65，实际页面 66（卡 BIN 库 + 文件记录），已改为 66。
- 连带修复（main 新代码与 A 的 api 变化交叉）：
  - `BinLibraryFileClientImpl` 用 `StartFileUploadRequestBody` 全参构造（5 参）编译失败，因为 api 新增了 `accessLevel`。已补第 6 参 `null`，不声明访问级别，按私有。
  - `CardBinLibraryBrowserAutoTest` 的完成回执与文件元数据夹具补 `versionNo` / `accessLevel` 等新字段，与前文 ClientOnboardingReview / Workflow 夹具同样处理。
  - 仓库其他模块也直接构造这些 DTO：`card` 测试 `PlatformMocker`、`client-master` 测试 `OnboardingOwnerMocker`、`client-console-bff` 的 `ClientOnboardingUploadProcessor`、Platform 自身。这些不在本卡范围，未改，请主会话 / A 卡确认。
- 可识别类型：前端从未写死可识别类型列表。「无法识别」提示、新建策略与草稿编辑的类型选项全部取自接口 `supportedMimeTypes`，所以接口加入 `text/csv` 后 CSV 自动变为可选、不再标红。已补测试：supportedMimeTypes 含 `text/csv` 时不标红、可建 CSV 策略。说明文案（第 3 节、第 6 节第 1 条中关于 `CARD_BIN_CSV` 的举例）以接口返回为准。
- 最终 api 复制（A 变基到 `22ede435` 后，`versionNo` 在 `StoredFileResponseBody` / `FileUploadCompletionResponseBody` 改为 `Integer`，`supportedMimeTypes` 含 `text/csv`）后重跑，均未改代码：BFF 模块 2272 例 0 失败（`FileRecordRegistrationAutoTest` 5 例通过）；`npm test` 1358 例全部通过；`npm run build` 通过。
