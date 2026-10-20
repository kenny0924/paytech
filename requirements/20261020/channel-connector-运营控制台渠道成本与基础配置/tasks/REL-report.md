# REL 报告 · 阶段 7 第 1–3 步：复核、备份、构建与核对（mor-test，2026-10-20 07:18–07:24Z）

执行者：Opus 5.5 agent，主工作区 `/Users/kenny/Desktop/Workspace/Code-MoR`（main `b04b77f1` + 未提交工作区改动，未建 worktree）。
未执行任何写 SQL；未停止 / 替换 / 重启容器；未改 `:development` tag；未写 `test-environment/artifacts`；未执行 git 写命令；未调用 Superpowers skill。凭据只经 `.env.test` 读入环境变量，未打印。

## 1. 复核（release.md 第 2.1 节重跑，全部一致）

| 项 | 实测 | 预期 | 结论 |
|---|---|---|---|
| channel-connector 运行镜像 | `sha256:194961f11db549ab57fa8ccb9f94ee51d737dee46cb588b86e2a40905539eff3` | 同 | 一致 |
| platform-support 运行镜像 | `sha256:d800c3ce358637594d1357ec51e76003600baf7497eb3f09e552467c853ecfc9` | 同 | 一致 |
| ops-bff 运行镜像 | `sha256:adec3f63f1ad1109373614417d1c014d88582cece2db4d1f4f5a49299d1e3a23` | 同 | 一致 |
| ops-web 运行镜像 | `sha256:c6661078a8f1fdbe616109dada519c076b0511428848b6155676b3f9957ed7f9` | 同 | 一致 |
| `mor_platform` 92xxx 七表行数 | 0 | 0 | 一致 |
| `operations-channel-%` 路由码 / `OPERATIONS_CHANNEL%` 资源码 / `CHANNEL_%` 权限码 | 0 / 0 / 0 | 0 / 0 / 0 | 一致 |
| PLATFORM_ADMIN 版本 | 35 | 35 | 一致 |
| `mor_channel_connector` 表数 / `t_channel_config_change` | 17 / 不存在 | 17 / 不存在 | 一致 |
| `mor_vault` 规则 720、同组合规则、策略变更 720 | 0 / 0 / 0 | 均不存在 | 一致 |
| `mor_vault` 策略最大版本 | 16 | 16 | 一致 |
| channel-connector-service 既有 Vault 规则 | 仅 REVEAL / CHANNEL_ACCOUNT_CONFIG_V1 / CHANNEL_EXECUTION | 同 | 一致 |
| `registration-dml.sql` ⊂ `platform-support-initial-data.sql` | Python `in` = True，且为文件末尾块 | 逐字一致 | 一致 |
| 2.2-3 compose 四服务环境键 vs 运行容器 | compose 声明键全部在运行容器中；运行容器多出的仅为镜像自带键（JAVA_HOME、PATH、NGINX_VERSION 等） | 只换 image 不丢配置 | 一致 |

## 2. 备份

目录：`/Users/kenny/Code-MoR-backups/20261020T072007Z-channel-console-test/`（目录 0700，文件 0600，`shasum -a 256 -c SHA256SUMS` 全部 OK）

| 文件 | 内容 |
|---|---|
| `mor_platform-8tables.sql` | `t_permission`、`t_role_permission`、`t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_role`（`--single-transaction`，8 个 CREATE TABLE） |
| `mor_vault-policy.sql` | `t_vault_access_rule`、`t_vault_policy_change` |
| `mor_channel_connector-full.sql` | 全库（`--databases`，17 个 CREATE TABLE） |
| `inspect-<service>.json` ×4 | 四容器 `docker inspect`（含环境变量，按 0600 保存） |
| `images-before.txt` | 容器名 + 运行镜像 ID |
| `docker-ps.txt` | `docker ps -a` 清单 |
| `compose-rollback.yaml` | 四服务 image = 第 1 节运行 ID；`compose.sh -f … config --quiet` 通过 |
| `compose.applications.yaml` | `test-environment/compose.applications.yaml` 副本 |
| `SHA256SUMS` | 上述文件校验和 |

## 3. 构建

| 步骤 | 命令 | 结果 |
|---|---|---|
| Java | `paytech/`：`JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw -B -o -DskipTests -pl channel-connector/paytech-channel-connector-app,platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am clean package` | BUILD SUCCESS（18.8 s）；class 文件 major 69（Java 25） |
| Web | `npm --prefix operations-console-web ci && npm --prefix operations-console-web run build` | 成功（仅既有的 >500 kB 分块告警） |
| Java 镜像 ×3 | scratchpad 临时上下文（`<service>.jar` + `health-probe/` 从 artifacts 复制），`docker build -f test-environment/java/Dockerfile --build-arg JAR_FILE=<service>.jar -t mor-test/<service>:channel-20261020 <ctx>` | 成功 |
| Web 镜像 | `docker build -t mor-test/operations-console-web:channel-20261020 operations-console-web` | 成功 |

插曲：首次在 zsh 中 `$s:channel-…` 被解析为 `:c` 修饰符，三个 Java 镜像得到错误 tag `mor-test/<service>hannel-20261020:latest`；已用 `${s}` 补打正确 tag 并删除这三个错误 tag（镜像 ID 不变，只删本次创建的 tag）。镜像库中其他会话的类似错误 tag（如 `*hart3-20261020:latest`）未动。

## 4. 核对

| 镜像 | sha256 ID | 镜像内 JAR SHA-256（= 构建产物 `*-exec.jar`） |
|---|---|---|
| channel-connector-service | `sha256:4d31d529e1d3258e5e7976f1f98d4ef24bcb8e3d3eca24d1e2aef6c432e5f054` | `e7ede2d4e360f7d35375f5e412060a7f5127596822132f915a94e53d83c7aeb1` |
| platform-support-service | `sha256:e5a06814c6ff3b79530cbfd76dbf2c7df1d9f2cd813efc385f2c9b6859526e6b` | `353408a1cca793427982b0b6cd629614534dbb79a4515717ac2d4d5c31fae0fc` |
| operations-console-bff | `sha256:bec8582633e30a28df0c6630d05b724340741072e449ef4945c1155388e36758` | `9a598dd9fcdb1c28b728c3071d07400abd3a502aa64c82d64c36986a2fec747b` |
| operations-console-web | `sha256:be32382a7a0655dec187d6e88107bbd5354c687dd4230820c3fe7dde6d72598b` | `index.html` SHA-256 `b0ed59b620cfc854dd0e79cf4ec920c28210dc1d6fd6400efaedd07a01a4fde8` |

内容（JAR 从新镜像 `docker create` + `docker cp` 取出，旧 JAR 从运行容器 `docker cp`；嵌套 `paytech-*` JAR 展开比较，版本号归一）：

| 服务 | 必含条目 | 条目数 旧 → 新 | 新增 / 删除 |
|---|---|---|---|
| channel-connector | `ChannelMidController`、`PaymentChannelController`、`ChannelAccountController`、`MidCapabilityController`、`ChannelConfigChangeController`（`BOOT-INF/classes/.../ext/rest/`）、`mapper/channel/ChannelConfigChangeMapper.xml`（core JAR 内）全部存在 | 1403 → 1521 | 118 / 0 |
| platform-support | `IntegrationLookupController` 存在；access-control JAR 内 `operations/console-resource-capabilities.json` 含 `OPERATIONS_CHANNEL_CHANNELS` | 3225 → 3230 | 5 / 0 |
| ops-bff | `ChannelCostController`、`ChannelConfigurationController` 存在 | 2360 → 2504 | 144 / 0 |
| ops-web | `index.html` 入口 `/assets/index-CGMnwdGz.js`（旧 `index-DMncGpXB.js`），与本地 `dist/index.html` 相同；`assets/` 有 `ChannelCostPlans-Bywbjot6.js`、`ChannelChannels-BUHOtfzE.js`、`ChannelMids-CzCFV7mB.js` | 130 → 135（去哈希） | 5（三页面 + `channel.js/.css`）/ 0 |

结论：四个镜像内容符合预期，条目只增不减。

## 5. compose-candidate.yaml

四个 `TODO_*` 已替换为上表 sha256（0 个 TODO 残留）；`test-environment/compose.sh -f <绝对路径>/release/compose-candidate.yaml config --quiet` 通过，解析后四服务 image 等于候选 ID。

复核结束时四个运行容器仍为旧镜像、healthy、重启 0。

## 6. 遗留

- 构建源为主工作区当前状态（main `b04b77f1` + 未提交改动，任务卡允许），不是干净 worktree；compose-candidate.yaml 头注“干净 worktree 构建 / 未构建镜像”的文字未改，由主会话在第 6 节记录时更正。release.md 第 2.2 节第 2 项（发布源码上重跑测试）不在 REL 范围，未执行。
- 第 4 步之后（DDL、Vault DML、块 92、容器替换、启用、验收）未执行，等待主会话。
- 备份中的 `inspect-*.json` 含容器环境变量（可能含凭据），仅 0600 保存在备份目录，不应外传。
