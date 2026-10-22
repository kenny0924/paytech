# 任务卡 C：mor-test 发布包（阶段 6 准备）

> 用户 2026-10-22 授权"发布到 mor-test"。本卡只做**准备**：干净工作目录构建、基线证明、发布包与脚本。**不得**停 / 起 / 替换 mor-test 任何容器，**不得**执行任何写 SQL（只读查询可以），**不得**调用 Vault / Platform 的写接口，不提交、不推送、不改 `:development` tag 与 `test-environment/artifacts`。环境变更步骤由主会话按本包执行。不得调用 superpowers skill。命令前台运行。

## 0. 参照

- 版式与做法照抄 `requirements/20261020/platform-support-币种主数据维护与读取缓存/release/`（release.md、compose-candidate/rollback、sql/、scripts/backup.sh、scripts/apply_sql.sh、acceptance/）以及 `requirements/20261020/channel-connector-统一对外渠道出口/release/`（最近一次替换 platform / ops-bff 的发布）。
- 记忆要点：钉 `sha256:` 镜像 ID；回滚目标取运行容器的 `.Image`；候选覆盖文件必须带上旧覆盖文件里该服务的全部 environment（先 `docker exec <c> printenv`，再 grep `requirements/*/release/compose-candidate*.yaml`）；`compose.sh` 已含基础文件，只传 `-f <override>`；不要 `docker cp` 进运行容器（读管理端口用 `--network container:<目标>` 旁路容器）。
- 需求材料：本目录 `requirement.md`、`technique.md`、`delivery.md`、`reports/`、`db/vault/dml.sql`；DDL `paytech/platform-support/docs/database/sql/changes/2026-10-22-file-storage-management.sql`；注册块 130 在 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 末尾（含激活语句注释）。

## 1. 线上基线（主会话已查，复核）

| 容器 | 运行镜像 | 来源 |
|---|---|---|
| mor-test-platform-support-service-1 | `sha256:2d40c1ba…` | channel-connector 第一段 `ccu-20261021`（main `1b60ae43` + 第一段，即 `ecab136c` 内容） |
| mor-test-operations-console-bff-1 | `sha256:4839c249…` | 同上 |
| mor-test-operations-console-web-1 | `sha256:d1e5619f…` | `capability-ui-20261022d`（见 `requirements/20261022/channel-能力组编辑弹窗优化/release/`，应即 `281a217d`） |

## 2. 源码与基线证明

1. 新建 detached worktree `/Users/kenny/Desktop/Workspace/Code-MoR-filestore-rel`，基于 `281a217d`。
2. **先证明基线：** 不加补丁，构建 platform-support-service、operations-console-bff 的 JAR 与 ops-web `dist/`，与运行容器逐项比较（Java：`BOOT-INF/classes` 及嵌套 `paytech-*` JAR 全部条目，排除 MANIFEST、`META-INF/maven`、build-info、classpath/layers 索引；Web：`dist/` 与 `nginx.conf` 逐字节，从容器 `docker cp <c>:/... <本地>` 读出是允许的）。有差异就查清来源并停下报告，不要自行取舍他人改动。
3. **加补丁：** 从 A、B 两个工作目录生成补丁（`/Users/kenny/Desktop/Workspace/Code-MoR-filestore`、`/Users/kenny/Desktop/Workspace/Code-MoR-filestore-ops`，相对 `281a217d` 的 `git diff` + 未跟踪文件；B 的 api 副本排除），应用到发布 worktree。再与主工作区中本需求涉及的文件逐个比较，必须一致（主工作区还有他人未提交改动，只比本需求文件）。
4. 构建：JDK 25、`~/.m3/repository`、`./mvnw -B -o -DskipTests -pl <两个 app> -am clean package`。**严禁 `install`**（会覆盖共用 SNAPSHOT）。Java 镜像用 `test-environment/java/Dockerfile`，`health-probe/` 照上次发布的做法取得；Web `npm ci`（如需）+ `npm test` + `npm run build` 后 `docker build`。tag 用 `filestore-20261022`。记录候选与运行版的类 / 文件级差异摘要。

## 3. 兼容性分析（写进 release.md 第 2 节，决定执行顺序）

至少回答：
- 增量 DDL 对**旧 Platform** 是否安全（新增 NOT NULL 列有无默认值？旧代码 insert `t_file_upload` / `t_file_object` 会不会失败？若会，给出最小窗口顺序，或拆成"加列可空+回填 → 换 Platform → 收紧 NOT NULL"并说明）。先查 mor-test `mor_platform` 两张文件表的现有行数、`storage_provider` / 容器字段取值（只读）。
- 新 Platform 启动条件：旧 `FILES_ONBOARDING_*` 存储键残留即拒绝启动 → 候选覆盖文件如何去掉这些键（Compose v5：验证 `!reset` 或 `!override` 写法，`compose.sh -f <override> config` 输出确认这些键已消失、其余 env 保留）；需要的新键（`files.enabled` 等）及默认值。
- 新 Platform 在**没有任何实例 / 路由**时的行为：启动是否健康、入驻上传是否报错（预期：直到登记完成前上传失败）。读已有文件：旧记录如何对应到实例（实例代码须为 `MINIO`？桶 `mor-platform-files`？）。
- Vault：mor-test Vault 的策略来源（数据库表还是文件）、刷新间隔；`db/vault/dml.sql` 的 ID 与策略版本在 mor-test `mor_vault` 是否可用（只读核对）。
- 旧 ops-bff 与新 Platform、新 ops-bff 与旧 Platform 的兼容；块 130 注册 / 激活的顺序。
- MinIO 实例登记所需参数：endpoint `https://minio.mor-test.localhost:29400`、region `us-east-1`、信任证书 `/run/mor-test/test-ca.crt` 在新模型中怎么表达（实例字段还是保留的环境变量？）；账号密钥取自 `MOR_MINIO_PLATFORM_ACCESS_KEY/SECRET_KEY`（在 test-environment 私有 secrets 中，脚本读取，不回显、不写进任何文件）。

## 4. 发布物（写到本目录 `release/`）

- `release.md`：照币种发布包的章节（源码与镜像、兼容性、只读核对、发布物、执行步骤、回滚、验收、执行记录空表、风险）。
- `compose-candidate.yaml` / `compose-rollback.yaml`：只含 platform-support-service、operations-console-bff、operations-console-web；`config --quiet` 通过。
- `sql/00-precheck.sql`、`sql/01-platform-file-storage.sql`（DDL 发布副本）、`sql/02-vault-file-storage-credential.sql`（Vault DML 副本）、`sql/10-registration-block130.sql`（块 130 休眠注册的逐字副本）、`test-activate.sql` / `test-deactivate.sql`、`sql/99-postcheck.sql`；每个写文件头带回滚。执行 `00-precheck.sql` 一次（只读）并把结果写入 release.md 第 3 节。
- `scripts/backup.sh`、`scripts/apply_sql.sh`（root 口令只经 `MYSQL_PWD`，不回显）；备份含 `mor_platform` 两张文件表、注册 8 表、`mor_vault` 三张策略表。
- `acceptance/`：
  - `stage7_register_storage.py`：经运营入口 http://localhost:28080 以管理员登录（`test-environment/private/access.md`），新建实例 `MINIO`（STATIC，密钥从私有 secrets 读）→ 登记桶 `mor-platform-files` → 检查 → 启用实例与桶 → 默认路由 `*` → `CLIENT_ONBOARDING_FORMAT`。幂等：已存在则跳过。运行记录写 `~/Code-MoR-backups/filestore-stage7/`（0600）。
  - `stage7_acceptance.py`：AC 映射到 requirement.md 的 AC-01—AC-12 中可在 mor-test 验证的项（列表 / 只读角色 403 / 内部文件接口上传→完成→查询→下载授权并实际下载、旧文件仍可读、凭据不出现在响应和日志、审计）；不可在 mor-test 验证的项注明由本机测试覆盖。合成数据带清理脚本 `stage7_cleanup.py`。内部接口经私网访问用旁路容器。
- 主工作区 `test-environment/compose.applications.yaml` 删除旧 `FILES_ONBOARDING_STORAGE_*` / `FILES_ONBOARDING_MINIO_*` 键并改用新开关——**只准备补丁文件** `release/compose-applications.patch`，不改主工作区。

## 5. 交付

在 release.md 写完后，回复主会话：基线证明结果、候选镜像 ID、执行顺序、需要主会话决定的问题。不要等待其他消息后停住。
