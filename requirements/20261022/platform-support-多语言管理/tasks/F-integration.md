# 任务卡 F：公开桶集成——语言包上传与读取、JSON 检查、文件路由与端到端测试

## 背景与基线

文件公开桶（code-mor-42）尚未提交，但其 mor-test 发布源码已冻结：`22ede435` + `/Users/kenny/Desktop/Workspace/Code-MoR-pubbucket-plat` 工作区中 `paytech/**` 与 `scripts/pdmaner/**` 的未提交改动（候选镜像 `pubbucket-20261023a`）。本卡基于这份冻结源码先完成集成；对方提交进 main 后，主会话再把整体重放到 main（届时另行处理 PDManer）。

## 工作目录（本卡第一步自己搭建）

1. `git worktree add --detach /Users/kenny/Desktop/Workspace/Code-MoR-i18n-int 22ede435`（**绝对路径**）。
2. 把公开桶改动带进来：在 `Code-MoR-pubbucket-plat` 中对 `paytech/` 生成 `git diff --binary` 并打包其未跟踪文件（`git ls-files --others --exclude-standard -z -- paytech`），在新目录 `git apply` 并解包。**只读**对方目录，不要在那里写任何东西。不要带 `scripts/pdmaner/**`，也不要带它对 `platform-support.pdma.json` 的改动（`git checkout -- paytech/platform-support/docs/database/platform-support.pdma.json` 恢复）。
3. 把本需求改动带进来：在 `/Users/kenny/Desktop/Workspace/Code-MoR-i18n` 对全部改动（`git diff --binary` + 未跟踪文件，排除各 web 的 `node_modules`、`dist`、`i18n-out`）同样搬过来。两边有重叠文件时（例如 `platform-support-schema.sql`、测试 DDL、`console-resource-capabilities.json`、`table-ownership.yaml`、`TableOwnershipAutoTest`、`paytech-platform-support-test` 中的基类 / Mocker、`InternalFileApi` 的 client-master 测试替身），手工三方合并，两边语义都保留，并在报告逐个列出。
4. 之后**只在** `Code-MoR-i18n-int` 改文件。`Code-MoR-i18n` 保持不动（作为本需求单独的原始版本）。

## 授权

普通文件编辑、编译、运行测试；本机 `mor_platform` 已有公开桶与本需求的表结构，可以运行 Platform HTTP 入口测试（测试会清理测试数据，按既有方式运行，不要跑整个 reactor）。**不提交、不推送、不执行任何新的 SQL、不改 / 起停容器、不碰 mor-test**（对方正在发布，ID 必须保持不变）。Maven 一律用私有仓库：`-Dmaven.repo.local=<你的 scratchpad>/m2 -Dmaven.repo.local.tail=$HOME/.m3/repository`（两个 -D 分开写，zsh 不会拆分变量）。Platform 测试需加 `-Dpaytech.local-environment.dir=/Users/kenny/Desktop/Workspace/Code-MoR/local-environment`。

## 必读

需求 / 技术方案（主工作区本需求目录，v3），`tasks/A-report.md`（第 5 节端口与留给集成卡的测试场景、第 10—11 节）、`tasks/B-report.md`（第 7 节第 2 条导入策略问题）；公开桶合同 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-文件公开桶/tasks/common.md`（`accessLevel=PUBLIC`、写入路由按来源系统 × 访问级别、检查通过后 `GET /files/{id}` 才返回 `publicUrl`）及其 `technique.md`；`agent-docs/decisions/2026-10-22-file-upload-address-principles.md`、`COMPENSATION_EVENT_GUIDE.md`、`UNIT_TEST_GUIDE.md`。

## 范围

1. **JSON 格式检查**（对方已同意由本需求负责）：`StoredContentType` 增加 `application/json`；`FormatFileScanner` 对 JSON 做格式校验（UTF-8、可解析为 JSON 对象、无 BOM），不合法即拒绝；补测试。注意 `supportedMimeTypes` 由 `StoredContentType` 派生，检查策略页会自动出现 JSON。
2. **端口实现**（A-report 第 5 节）：
   - `ReleaseBundleStore`：用 Platform 进程内文件服务开始上传（`accessLevel=PUBLIC`，原因码 `I18N_RELEASE_BUNDLE`，来源系统用 Platform 自身既有的来源系统编码——查文件服务现有约定，没有则定义一个并说明），在事务外把字节 PUT 到签发地址，再完成上传；返回文件地址。对象键必须含应用、发布号、语言、命名空间与摘要，保证每个文件只写一次。`publicUrl(fileAddress)` 走文件元数据查询。
   - `ReleaseBundleReader`：按文件地址读取已存语言包内容（复用卡 A 读取导入文件的进程内方式；若公开文件需不同路径，说明）。
   - 激活补偿 Handler 的检查状态读取与公开地址都基于真实文件服务。
3. **文件策略与路由数据**（写进主工作区本需求 `db/dml.sql` 块 135 的新段，ID 仍在 135 段内，不执行）：
   - 检查策略 `I18N_BUNDLE_JSON`（只允许 `application/json`，单文件 1 MB，格式检查）。
   - Platform 来源系统 × `PUBLIC` 写入路由 → 公开桶。mor-test 上对方已登记 PUBLIC 桶 `mor-platform-public`（MinIO），路由引用它；本机测试用测试基类的现有存储 Mocker。
   - Operations BFF 导入：新建策略 `OPERATIONS_BFF_IMPORT`（允许 `text/csv` 与 `application/json`，上限取两者较大值），把 `operations-console-bff` × `PRIVATE` 路由改指该策略（UPDATE 语句写清旧值，回滚 SQL 同步）。
   - 回滚 SQL 同步更新。
4. **端到端测试**（A-report 第 5、6 节列出的延后场景全部补上，走真实生产链路与本机 MinIO，不用测试替身绕过）：发布三步（生成 → 公开上传 → 登记）、检查通过后自动上线与指针切换、检查拒绝标 FAILED、发布期间文案变更 409、手动切到更新发布后旧发布上线不改指针、切回、两次发布差异、带发布的 manifest 返回公开地址、预检 `changeSummary` 与发布记录 `checkProgress` 有值、导入 key 真实上传 JSON 并经检查后预览成功、检查未完成时 409 `I18N_IMPORT_FILE_NOT_READY`。
5. 公开桶自身测试与本需求既有测试全部回归：file-document 模块测试、Platform `auto/ext/http/files/**`、`auto/ext/http/localization/**`、`auto/api|architecture|configuration/**`，localization 模块、arch-rules，两个 BFF 全量，两个 web `npm test` 与 build。

## 交付报告（写到主工作区 `.../tasks/F-report.md`）

工作区搭建过程与三方合并清单；端口实现要点；来源系统编码与对象键规则；`db/dml.sql` 新增 / 修改的行与回滚；测试命令与实际结果（逐模块）；遗留与待裁决。**完成后直接结束，不要等待后台事件。**
