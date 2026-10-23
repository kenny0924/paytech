# 任务卡 A：Platform Support（文件公开桶、同地址替换、检查策略与文件记录）

工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR-pubbucket-plat`（detached，基于 main `42879401`）。**不建分支、不提交、不 install、不推送、不碰 mor-test。** 主工作区由其他会话共用：代码与服务文档只改工作目录；**唯一例外**是本需求目录 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261022/platform-support-文件公开桶/`（它在主工作区，未纳入工作目录基线），`db/` 与 `reports/` 直接写到该绝对路径。

## 1. 必读

1. 根 `AGENTS.md`；`agent-docs/DEVELOPMENT_CONVENTIONS.md`、`agent-docs/JAVA_PROJECT_STRUCTURE.md`、`agent-docs/examples/WRITE_API_EXAMPLE.md`（完成前按第 11 节自查）、`agent-docs/UNIT_TEST_GUIDE.md` + `agent-docs/examples/UNIT_TEST_EXAMPLE.md`、`agent-docs/DATABASE_DESIGN_GUIDE.md`、`agent-docs/COMPENSATION_EVENT_GUIDE.md`。
2. 本需求：`requirements/20261022/platform-support-文件公开桶/` 下 `requirement.md`、`technique.md`、`tasks/common.md`（**合同以 common.md 为准**）。
3. 前置需求的实现方式：`requirements/20261022/platform-support-文件多存储并存/technique.md`、`segment2.md`、`tasks/A-platform.md`、`reports/A-platform-report.md`。
4. 开始改实现前，在报告开头用几句话写明：已读路径、各写接口所属 Processor / Context、本地原子写入与事务外步骤（WRITE_API_EXAMPLE 第 1 节）。

## 2. 范围

`paytech/platform-support` 下 api、file-document、core、app、test 模块，以及该服务的 `docs/database`（PDManer `platform-support.pdma.json`、`sql/platform-support-schema.sql`、`sql/changes/` 增量）、`compensation-event-catalog.yaml`、`paytech/tools/arch-rules` 的 `table-ownership.yaml`（新表归属）、`paytech/platform-support/README.md` 与 `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md` 的文件模块段落。**不做**：`platform-support-initial-data.sql` 注册块（B 卡负责）、Operations BFF / Web。

## 3. 第一步：API 合同（完成后立刻通知主会话）

按 `common.md` 第 1、2 节修改 / 新增 `paytech-platform-support-api` 的 `InternalFileApi`、`FileStorageManagementApi` 及 request / response 类（普通 class、Lombok、中文 JavaDoc、Hibernate Validator）。`./mvnw -o -q -pl paytech/platform-support/paytech-platform-support-api -am compile` 通过后，**用 SendMessage 发给 `main`：`API_READY` + 改动的 api 文件清单**，然后继续后续工作（不要等待回复）。之后若需改 api，同样发消息列出变化。

## 4. 实现要点

1. **DDL / DML**：按 technique.md 第 4 节写 `requirements/20261022/platform-support-文件公开桶/db/ddl.sql`、`db/dml.sql`（回填，幂等），同步 `sql/changes/2026-10-22-file-public-bucket.sql`（或该目录现有命名惯例）、schema SQL、PDManer（完整中文注释、无 CHECK / FK）。
2. **本机执行（已获用户授权）**：先 `mysqldump` 备份本机 `mor_platform` 到 `~/Code-MoR-backups/pubbucket-<时间>/`（0700），再执行 ddl.sql、dml.sql。连接参数取 `local-environment/.env.local`，不打印密码。只执行本需求两份文件，不碰 mor-test。执行前核对本机库已是 `42879401` 对应结构（第二段删除 DDL 若本机未执行，先报告，不自行补执行）。
3. **file-document**：
   - 新表 `t_file_version` 的 DataObject / Mapper / Repository；`t_file_object`、`t_file_upload`、`t_file_storage_bucket`、`t_file_storage_route`、`t_file_scan_result` 新列映射；新枚举 `FileAccessLevel`、`FileVersionStatus`（`core/enums`，显式注册 `CodeEnumTypeHandler`）。
   - 注册表路由改为 `(source, level)` → `(*, level)` → 503，不跨级别回落。
   - 存储适配器（S3 与 MinIO 两个都做）：桶检查追加匿名访问检查（technique.md 2.2）；替换签名（只签 Content-Type）；"比 base 新的版本恰好 1 个"的版本列举；按 versionId 复制成最新版本（公开恢复）；读取最新版本 ETag / 大小。
   - 版本化扫描：`FileScanHandler` 按版本行执行；v1 事件键仍为 `fileId`，替换为 `fileId:vN`；通过时在一个短事务中提升当前版本（镜像列、`current_version_no`、旧 CURRENT → SUPERSEDED）。
   - 新补偿事件 `FILE_PUBLIC_SYNC`（键 `fileId:uploadId`，开始替换时登记；替换未结束返回重试；结束后比较最新与当前，不一致则复制恢复）。登记到 `compensation-event-catalog.yaml`，执行策略按替换上传期限给足重试窗口。**与"开始替换"的竞态**（恢复复制与新替换同时发生）必须有明确保护（例如开始替换拒绝存在未完成 `FILE_PUBLIC_SYNC` 的文件，或共用 `file:replace:{fileId}` 资源锁），在报告中说明选择与证据。
   - 公开地址：`publicUrl = publicBaseUrl + "/" + objectRef`，`versionedUrl = publicUrl + "?v=" + currentVersionNo`；仅 PUBLIC 且当前版本 AVAILABLE 时返回。
   - 检查策略管理 Domain Service / Repository（写 `t_file_scan_policy`、`t_file_scan_requirement`）；文件记录分页查询（数据库 LIMIT/OFFSET + COUNT，复用 `PagingRequestBody` / `PagingResponseBody`）与详情多表查询（`*View`）。
4. **core / app**：替换上传（`AbstractLockProcessor`，锁 `file:replace:{fileId}`）、完成上传兼容替换、桶编辑、路由路径、策略 8 个、文件记录 3 个 Processor / Controller、审计动作（common.md）。Platform 内部 Service 入口（工作流用）同步支持 `accessLevel` 与替换。
5. **兼容**：不传 `accessLevel` 的现有调用（客户入驻经 client-console-bff、工作流附件、卡 BIN）行为完全不变；回归现有测试。

## 5. 测试（auto，本机 MySQL / Redis / MinIO，Vault 测试端点）

- 覆盖 AC-01—AC-08、AC-10—AC-15 中属于 Platform 的部分：公开 / 私有桶检查（MinIO 匿名只读桶策略作公开桶，基础地址用 MinIO 路径风格地址）、路由解析与不回落、公开地址只在 AVAILABLE 后返回、替换（私有检查前读旧版本、公开恢复、并发 409、多余 PUT 作废、过期、跨来源 404、改级别拒绝）、`FILE_SCAN` v1 / vN 与 `FILE_PUBLIC_SYNC` Handler 独立入口测试、策略管理全流程、文件记录查询 / 详情 / 下载审计、回填 DML 后的存量行为。
- **只跑本需求相关测试类**（文件、存储管理、内部文件接口、扫描与补偿 Handler、工作流附件、客户入驻相关），用 `-Dtest=...`；**不要跑 Platform 全量或全仓库测试**——本机数据库与其他会话共用，全量测试会清空共享表。全仓库只做 `test-compile`。
- JDK 25：`export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home`；Maven 仓库 `~/.m3`，离线 `-o`。命令在前台执行，不要后台等待事件。

## 6. 交付

写 `requirements/20261022/platform-support-文件公开桶/reports/A-platform-report.md`：开头的阶段说明、改动文件清单、DDL / DML 执行记录（备份路径、执行时间、行数）、测试命令与结果（数量）、WRITE_API_EXAMPLE 第 11 节逐项核对、与 common.md / technique.md 的偏差及原因、遗留项。不要提交。
