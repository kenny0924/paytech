# 阶段 4 任务卡：多语言发布变更记录（Platform Support）

需求目录（绝对路径，worktree 中没有这些未提交文件，请从这里读）：
`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261030/platform-support-多语言发布变更记录/`

- `requirement.md`：目标、验收标准 AC-01—AC-09、决定 D1—D4
- `technique.md`：**第 1 节现行口径、第 4—8 节设计（本卡的权威）**
- `db/ddl.sql`：DDL 评审稿

## 1. 开工前必须实际读取（AGENTS.md「接口开发必读与自查」）

`AGENTS.md`、`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`（第 11 节自查）、`agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/UNIT_TEST_GUIDE.md` 与 `agent-docs/examples/UNIT_TEST_EXAMPLE.md`。开工前在报告里简述：已读路径、所属 Processor、本地原子写入（`registerRelease` 事务）与事务外步骤（还原、比较、上传语言包）。

## 2. 范围（只改 Platform Support）

1. **模型与 SQL**
   - PDManer `paytech/platform-support/docs/database/platform-support.pdma.json`：直接编辑 JSON（`json.dumps(indent=2, ensure_ascii=False)` + 结尾换行），在 `localization` 图加入新实体 `t_i18n_release_change`（technique 5.1），`t_i18n_release` 在 `key_count` 后加 `change_count`（5.2）。保留其他实体、图与 namingRules 不变；**不要运行 `scripts/pdmaner/*` 生成器或 `--check`**（已过时，会覆盖手工修改）。新字段 / 索引 ID 用新 UUID，字段风格照抄 `t_i18n_release_bundle`。
   - `paytech/platform-support/docs/database/sql/platform-support-schema.sql`：同步新表与新列（注释与模型逐字一致）。
   - 新增 `sql/changes/2026-10-30-i18n-release-change.sql`（由 `db/ddl.sql` 定稿，去掉文件头「评审稿」字样）与 `2026-10-30-i18n-release-change-rollback.sql`。
2. **持久化**：DataObject `I18nReleaseChange`、`I18nReleaseChangeMapper` + XML、`I18nReleaseChangeRepository`（interface + `I18nReleaseChangeRepositoryImpl`）：`findContent(applicationId, releaseNo)`（technique 6.1 的 SQL）、`insertAll`（每批 ≤ 1000 行）。`I18nRelease` 增加 `changeCount`，Mapper 读写；`I18nReleaseRepository` 增加「发布号 ≤ N 中 `change_count IS NULL` 的发布数」。
3. **领域**：纯 Java `ReleaseChanges`（`domain/release`），由 `ReleaseDiff` 生成变化行与条数；ADDED / CHANGED 带改后文字，REMOVED 文字为 NULL。
4. **还原**：一个组件负责「还原发布 N → 语言 → key → 文案」，还原前做完整性检查（technique 5.2），缺失时抛服务端错误「发布变更明细缺失」（`ErrorException`，带应用编码与发布号）。
5. **三个接口**（合同不变，见 technique 6.2）：
   - `PrecheckReleaseProcessor`：ACTIVE key 与译文只查一次，评估与摘要共用；摘要 = 还原线上发布（无线上发布时为空内容）对比 `ReleaseContent.current`。
   - `PublishReleaseProcessor` + `ReleaseServiceImpl.registerRelease`：同一份 key / 译文用于评估、摘要、生成语言包和变化行；事务外还原发布号 N-1 并计算变化行；`registerRelease` 在现有锁与 `I18N_CONTENT_CHANGED` 校验后，同事务写发布（带 `change_count`）、语言包登记、变化行、上线任务。
   - `DiffReleaseProcessor`：还原基准与目标后 `ReleaseDiff.between`；删除读包端口与 503 分支。
6. **清理**：`ReleaseChangeSummaries` 改为基于还原；核对 `ReleaseBundleReader` / `FileDocumentReleaseBundleReader` 是否还有其他使用方，没有则删除（`FileDocumentUploadedFileReader` 保留）。
7. **测试**：technique 第 8 节 T1—T10，放在 `paytech-platform-support-test` 现有 localization HTTP 测试（`ReleaseApiTest`、`ReleasePublicationApiTest` 等）中或同目录新类；遵守 UNIT_TEST_GUIDE（真实入口、不用 `when/thenReturn`、`@MockBean`）。若现有测试 Mocker / TestMapper 需要清理新表，一并补上（全局 AfterEach 清理）。T8：读包代码删除后，用断言确认预检 / 差异 / 发布期间文件服务读取与 Vault 调用计数为 0（沿用现有 Vault / 存储测试桩的调用记录；若无可观测点，说明理由并以结构删除为准）。

## 3. 不做

不改 Operations BFF、ops-web、client-console-web、Gateway / 注册 DML、语言包生成格式、覆盖率 / ICU 校验算法、文件服务。不写补录脚本（阶段 6）。

## 4. 环境与验证规则

- JDK：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；Maven 离线：`./mvnw -o -Dmaven.repo.local=$HOME/.m3/repository -Daether.enhancedLocalRepository.trackingFilename=_no_tracking_ …`。
- 本地数据库**只能**用 `mor-local-mysql-1`（13306）；**绝不能触碰任何 `mor-test-*` 容器或数据库**。在本机 auto 库执行本次 DDL（新表、新列）以便测试；执行前确认目标是 local-environment 的 Platform auto 库。
- 其他会话共享本机测试库：**不要跑整个 reactor 的测试**。整 reactor 只 `test-compile`；测试只跑 platform-support 相关模块，用 `package` + `-Dtest=` 限定 localization 测试与受影响的 Platform 测试类；从 `local-environment/.env.local` 导出所需变量（含 `MOR_RABBITMQ_USER/PASSWORD/VHOST`、Redis、Platform DB 密码），不打印密码。
- 命令在前台运行，不要等待后台事件或 Monitor；长命令用足够的超时。

## 5. 交付

- 在 worktree 中提交一个 commit（不推送），消息以 `feat(platform-support): 多语言发布变更记录` 开头，结尾加 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
- 报告：已读文档清单与开工前说明；改动文件清单；WRITE_API_EXAMPLE 第 11 节逐项自查（不适用项说明原因）；T1—T10 与实际测试方法名对应表；测试命令与结果（通过数 / 失败数）；本机执行的 SQL；未完成项与风险。
