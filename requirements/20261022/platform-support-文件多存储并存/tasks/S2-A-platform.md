# 任务卡 S2-A：Platform（文件服务）

先读 [S2-common.md](./S2-common.md)。worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-fs2-platform`。

## 范围

1. **先交付合同（完成后立即回复主会话路径）：** `FileAddress`（S2-common 1.1）及单元测试；`InternalFileApi` 各响应增加 `fileAddress`；下载授权的 `X-Operator-Account-Id` 与审计。
2. **DUAL：** 确认 Platform 在 `internal-auth.mode=DUAL` 下能启动。如果 DUAL 需要 `jwks-uri` 或 `static-key`，查清 Platform 作为签发方应怎样配置，写进 `application.yml` 的环境变量占位，并在报告中说明 mor-test / 本机需要的变量。补用例：
   - DUAL 下现有内部入口（无 Token、无头）行为不变，至少挑 2 个有代表性的；
   - 内部文件接口在 DUAL + 头时可用（第一段已有 8 个，保留）。
3. **删除**（segment2.md 4.1，按引用逐个清干净，编译通过）：
   - `OnboardingFileApi`（F01—F04）及其 DTO、枚举、`OnboardingFileController`、`OnboardingFileConverter`、`OnboardingFileConfiguration`、6 个 Processor、`ClientOnboardingAccessService`、`OnboardingFileLocks`；
   - `OnboardingFileVerificationApi` / Controller / `VerifyOnboardingFileProcessor`；
   - `ReviewFileApi` / Controller / `ReviewFileDownloadProcessor`；
   - `CctFileTargetClient` 及 `ClientFileTargetMockController`、`CctFileTargetMocker`；
   - `files.onboarding.*` 中只服务于这些入口的配置键、日志前缀、请求过滤与错误映射；
   - 对应测试类与夹具。
   - 删除 API 类会影响 client-console-bff、ops-bff、CCT 的编译，那由 S2-B / S2-C 在各自 worktree 处理，你只需在报告中列出被删的 API 类全名。
   - `FILE_SCAN` 与扫描策略保留。
4. **Owner 数据：**
   - 代码中去掉 `owner_system / owner_type / owner_ref_id` 与 `t_file_owner_ref` 的全部读写，包括 Mapper XML、DataObject、Repository、Service、测试清理器。
   - Schema DDL、测试 `init/ddl.sql`、PDManer 模型及其测试、`table-ownership.yaml` 同步删除。
   - 增量 DDL `paytech/platform-support/docs/database/sql/changes/2026-10-2x-file-owner-removal.sql`：删三列与 `t_file_owner_ref`，文件头写执行前备份与回退建表语句。**只写不执行。**本机库仍保留这些列和表，代码不读写它们即可。
5. **本机 e2e：** `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/run-service.sh` 删掉旧 `files.onboarding.storage-*` / `minio.*` 启动参数，Platform 加 DUAL 所需参数（第一段遗留）。
6. **文档：**
   - `paytech/platform-support/README.md` 的文件一节；
   - `agent-docs/database/PLATFORM_SUPPORT_DATABASE.md`、场景文档中的 Owner 描述。

## 验证

- `paytech-platform-support-*` 受影响测试类先跑。最后跑 Platform 模块全量时先向主会话申请独占窗口。
- arch-rules、PDManer 相关测试；`npm --prefix requirements-site test` 失败项与 `a5479950` 基线相同（需求站文字由 S2-C 改，你不改）。
