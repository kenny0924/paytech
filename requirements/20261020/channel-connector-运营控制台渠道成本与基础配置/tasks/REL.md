# 任务卡 REL · 阶段 7 第 1–3 步：复核、备份、构建与核对（mor-test）

- 依据：[release/release.md](../release/release.md) 第 2、4 节第 1–3 步。执行者：Opus 5.5 agent，主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR`。
- 只做：只读复核、备份、构建镜像、核对内容、改写 `release/compose-candidate.yaml` 的四个 sha256、`compose.sh config --quiet` 校验。**不执行 SQL、不替换 / 停止任何容器、不改 `:development` tag、不 git 写命令、不调用 Superpowers skill。**
- 凭据只从 `test-environment/.env.test` 读入环境变量（`MYSQL_PWD`），不打印；mor-test MySQL 容器 `mor-test-mysql-1`，客户端 `--default-character-set=utf8mb4`。

## 步骤

1. **复核（只读）：** 重跑 release.md 第 2.1 节：四容器运行镜像 ID（`docker inspect --format '{{.Image}}'`）、`mor_platform` 92xxx 七表 0 行与三类编码 0 冲突、PLATFORM_ADMIN 版本、`mor_channel_connector` 17 表无新表、`mor_vault` 规则 720 不存在与策略最大版本、`registration-dml.sql` 与主工作区 `paytech/platform-support/docs/database/sql/platform-support-initial-data.sql` 末尾块 92 逐字一致（Python `in` 检查）。任一不符即停并报告。
2. **备份：** `~/Code-MoR-backups/<UTC 时间戳>-channel-console-test/`（目录 0700、文件 0600，`SHA256SUMS`）：`mysqldump` `mor_platform` 的 8 张表（见 release.md）、`mor_vault` 两张策略表、`mor_channel_connector` 全库（`--single-transaction`）；四容器 `docker inspect` JSON 与 `images-before.txt`（名称 + 镜像 ID）；`docker ps` 清单；`compose-rollback.yaml`（四服务 image 写当前运行 ID，格式同 compose-candidate.yaml）；`test-environment/compose.applications.yaml` 副本。
3. **构建：** 在主工作区 `paytech/` 下 JDK 25：`./mvnw -B -o -DskipTests -pl channel-connector/paytech-channel-connector-app,platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am clean package`；`npm --prefix operations-console-web ci && npm --prefix operations-console-web run build`。Java 镜像：为每个服务准备一个临时构建上下文目录（放 `<service>.jar` = 对应 `*-exec.jar`，`health-probe/` 从 `test-environment/artifacts/health-probe/` 复制），`docker build -f test-environment/java/Dockerfile --build-arg JAR_FILE=<service>.jar -t mor-test/<service>:channel-20261020 <ctx>`；Web：`docker build -t mor-test/operations-console-web:channel-20261020 operations-console-web`。不写 `test-environment/artifacts`。
4. **核对：** `unzip -l` 各 JAR：channel-connector 含 `ChannelMidController`、`PaymentChannelController`、`ChannelAccountController`、`MidCapabilityController`、`ChannelConfigChangeController`、`mapper/channel/ChannelConfigChangeMapper.xml`；Platform 含 `IntegrationLookupController`，且 `operations/console-resource-capabilities.json` 含 `OPERATIONS_CHANNEL_CHANNELS`；BFF 含 `ChannelCostController`、`ChannelConfigurationController`；Web 镜像 `index.html` 引用新入口且 `assets/` 有 `ChannelCostPlans` / `ChannelChannels` / `ChannelMids` 分块。与运行容器中的 JAR（`docker cp <容器>:/app/application.jar`）比较条目只增不减。记录四个镜像 `sha256:` ID 与 JAR SHA-256。
5. 把四个 sha256 写入 `release/compose-candidate.yaml`（替换 `TODO_*`），运行 `test-environment/compose.sh -f <绝对路径> config --quiet`。
6. 报告 `tasks/REL-report.md`：复核结果表、备份目录与清单、构建命令与结果、核对结果、四个 sha256、遗留。
