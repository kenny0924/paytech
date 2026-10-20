# mor-test 发布记录：channel-connector 运营控制台（渠道成本方案 + 渠道基础配置）

阶段 6 准备稿（2026-10-20）。未执行任何部署、SQL 或镜像构建；阶段 7 按第 4 节顺序执行并填写第 6 节。

## 1. 范围

| 项 | 内容 |
|---|---|
| 源码基线 | main `b04b77f1`（含商务第二切片 d565f85f 与财务三项）+ 本需求未提交改动（channel-connector 152 文件、Platform 9 文件、ops-bff / ops-web 46 文件、arch-rules / 生成器、注册与 Vault SQL） |
| 镜像（4 个，固定 sha256） | channel-connector-service、platform-support-service、operations-console-bff、operations-console-web，tag `channel-20261020` |
| `mor_channel_connector` | DDL：新表 `t_channel_config_change`（[db/channel-connector/ddl.sql](../db/channel-connector/ddl.sql)，含 `USE mor_channel_connector;`）；17 → 18 张 |
| `mor_vault` | [db/vault/dml.sql](../db/vault/dml.sql)：channel-connector-service PROTECT `CHANNEL_ACCOUNT_CONFIG_V1` / `CHANNEL_CONFIGURATION`（规则 9100000000000720）+ 策略变更（版本 16 → 17） |
| `mor_platform` | [db/platform-support-service/registration-dml.sql](../db/platform-support-service/registration-dml.sql)（块 92，402 行，休眠）→ 容器替换后 [test-activate.sql](./test-activate.sql)（31 / 9 / 7 / 1，PLATFORM_ADMIN 35 → 36） |
| 不发 | gateway、payment、billing、ledger、account、commercial 等其他容器；Nacos 配置中心未用（`spring.cloud.nacos.config.enabled=false`），无配置变更；internal-auth 在 mor-test 未开启（OFF），不需要 Token 白名单 |

## 2. 执行前核对（只读）

### 2.1 2026-10-20 准备时的快照（阶段 7 第 1 步重跑）

| 项 | 结果 |
|---|---|
| 运行镜像（回滚目标） | channel-connector `sha256:194961f11db549ab57fa8ccb9f94ee51d737dee46cb588b86e2a40905539eff3`；platform-support `sha256:d800c3ce358637594d1357ec51e76003600baf7497eb3f09e552467c853ecfc9`；ops-bff `sha256:adec3f63f1ad1109373614417d1c014d88582cece2db4d1f4f5a49299d1e3a23`；ops-web `sha256:c6661078a8f1fdbe616109dada519c076b0511428848b6155676b3f9957ed7f9`（后三者 = 财务 `fin-20261020` 基线） |
| 容器环境 | channel-connector：Worker 26、Nacos 发现、XXL 执行器 `channel-connector-service`，无 internal-auth 变量；ops-bff：`OPERATIONS_LEDGER/ACCOUNT/BILLING_BASE_URL` 三个显式地址，其余 Owner 经 Nacos 发现（channel-connector 亦经 Nacos，不需新变量）；Platform：无 internal-auth 变量 |
| `mor_platform` 冲突 | 92xxx 七张表合计 0 行；`operations-channel-%` 路由码、`OPERATIONS_CHANNEL%` 资源码、`CHANNEL_%` 权限码均 0；PLATFORM_ADMIN 版本 **35**；已存在块 93、120–122（不与 92 交集） |
| `mor_channel_connector` | 17 张表，无 `t_channel_config_change`；渠道 / 账号 / MID / 成本方案均 0 行（验收需从空库登记） |
| `mor_vault` | channel-connector-service 只有 REVEAL / CHANNEL_EXECUTION；规则 720 不存在；策略版本最大 16；数据类型 `CHANNEL_ACCOUNT_CONFIG_V1` 存在；mor-test Vault 容器从库加载策略（`VAULT_DB_DSN`） |
| Platform 接入方 | `ALIPAY_PLUS, DLOCAL, PAYBY, PAYERMAX, TOSS_PAY`；验收渠道编码用 `PAYERMAX` |
| SQL 一致性 | `registration-dml.sql` 与主工作区 `platform-support-initial-data.sql` 末尾块 92 逐字一致（已核）；阶段 7 构建前再 diff 一次 |
| 本机演练 | 阶段 5 已在本机 `mor_platform_v2e2e` 执行块 92 + 激活（31 / 9 / 7 / 1）、`mor_vault` 规则、`mor_channel_connector_v2e2e` 建表，API 103 / 103、浏览器 35 / 35 |

### 2.2 前置（阶段 7 开始前完成）

1. 问 code-mor-9c：财务三项已提交 `b04b77f1`，本需求基线已含；确认此刻没有其他会话在替换这四个容器或执行 `mor_platform` / `mor_vault` DML。
2. 在发布源码（主工作区当前状态，或从其复制的干净 worktree）上重跑：channel-connector-test、arch-rules、platform-support-test 定向、ops-bff、ops-web test + build（阶段 5 已全绿；若主工作区又有他人改动则重跑）。
3. 核对 `test-environment/compose.applications.yaml` 四个服务段与运行容器环境键集合一致（只换 image 不丢配置）。

## 3. 风险与取舍

- **R1 路由先于镜像：** 块 92 休眠行对旧版本无影响，可先执行；test-activate 必须在四个容器 healthy 之后执行，否则 Gateway 转发到不存在的 BFF 端点。
- **R2 Platform 镜像：** 本次替换 Platform（新增内部接口 `IntegrationLookupApi`，并把 `console-resource-capabilities.json` 补到含商务、财务、渠道页）。Platform 重启约 60–90 秒，期间运营登录与 Gateway 快照刷新中断；选择低峰执行，先发 Platform 再发 BFF。
- **R3 DDL：** `t_channel_config_change` 为新增表，不改既有表；回滚为 `DROP TABLE`。
- **R4 Vault 策略：** 新增 PROTECT 规则后，Vault 容器按刷新间隔全量重载；在确认规则生效前，账号配置写入会被 Vault 拒绝（403），不写库，无副作用。
- **R5 channel-connector 容器：** 新 JAR 增加了 `IntegrationLookupApi` 客户端 Bean 与 Vault Protect；配置键无变化；XXL 任务 `channelCostVersionActivation`（既有）不变。
- **R6 mor-test 无渠道数据：** 验收从空库登记，清理按 ID；Payment 侧链路以 mor-test 现有 PAYERMAX 接入方配置为准，若其 PAY 不可执行则记录为环境限制。

## 4. 执行顺序（任一步不符就停）

1. **复核：** 重跑第 2.1 节核对与第 2.2 节前置。
2. **备份：** `~/Code-MoR-backups/<UTC>-channel-console-test/`（0700 / 0600，附 SHA256SUMS）：`mor_platform` 的 `t_permission`、`t_role_permission`、`t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_role`；`mor_vault` 的 `t_vault_access_rule`、`t_vault_policy_change`；`mor_channel_connector` 全库（17 表，均为空或小表）；四个容器 `docker inspect` 与 `images-before.txt`；`compose-rollback.yaml`（写入第 2.1 节四个运行镜像 ID）；`compose.applications.yaml` 副本。
3. **构建：** 从发布源码建干净 worktree（或直接用主工作区），JDK 25 离线：
   ```
   ./mvnw -B -o -DskipTests -pl channel-connector/paytech-channel-connector-app,platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am clean package
   ```
   Web：`npm ci && npm run build`。四个镜像用 `docker build` 与显式 tag `channel-20261020`（Java：`test-environment/java/Dockerfile`，`JAR_FILE` 指向各 `*-exec.jar`，构建上下文含 `health-probe/`；Web：`operations-console-web/Dockerfile`），不经过 `compose.sh build`，不改写 `:development`。核对：channel-connector JAR 含 `ChannelMidController` 等 4 个新 Controller 与 `ChannelConfigChangeMapper.xml`；Platform JAR 含 `IntegrationLookupController` 且 `console-resource-capabilities.json` 含 `OPERATIONS_CHANNEL_*`；BFF JAR 含 `ChannelCostController` / `ChannelConfigurationController`；Web `index.html` 引用新入口且有 `ChannelCostPlans` 等分块；与运行中的 JAR 比较条目只增不减。把四个 sha256 写入 compose-candidate.yaml，`config --quiet` 校验。
4. **数据库（旧版本仍在运行）：** 以 root（`MYSQL_PWD` 从 `test-environment/.env.test` 读入，utf8mb4）依次：
   - `mor_channel_connector`：`db/channel-connector/ddl.sql`，核对 18 张表、注释非乱码；
   - `mor_vault`：`db/vault/dml.sql` 先 `sed 's/^COMMIT;/ROLLBACK;/'` 试跑再正式执行，核对规则 720 存在、策略版本 17；
   - `mor_platform`：`registration-dml.sql` 先试跑（ROLLBACK 后 92xxx 仍为 0）再正式执行，核对 7 / 7 / 9 / 31 / 138 / 24 / 186。
5. **替换：** `compose.sh -f compose-candidate.yaml up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <service>`，顺序 platform-support-service → channel-connector-service → operations-console-bff → operations-console-web。每个容器 healthy、重启 0、启动日志无 ERROR、运行镜像 ID 等于候选 ID；channel-connector 日志确认 Nacos 注册、Redis、XXL 心跳；Platform 日志确认 Vault 策略加载与运营登录可用；Web `index.html` 引用新入口。
6. **启用：** `test-activate.sql` 先 ROLLBACK 试跑（31 / 9 / 7 / 1），再提交，PLATFORM_ADMIN 35 → 36；等 Gateway 快照刷新（5 秒）。
7. **验收：** 按 [acceptance.md](./acceptance.md)，结果写 `verification/stage7-acceptance.md`，截图 `assets/stage7/`。
8. **清理：** `verification/acceptance-cleanup-test.sql`（先 SELECT 后 DELETE，按 ID）；合成用户停用、合成角色清权限；Vault Token 不删（记录数量）。
9. **记录：** 第 6 节写时间、命令摘要、核对数、四个 sha256、JAR SHA-256、Web 入口、基线提交号；更新 delivery.md 与 `agent-docs/CURRENT_IMPLEMENTATION.md`；通知 code-mor-9c 新镜像基线。

## 5. 回滚

| 情况 | 处理 |
|---|---|
| 某容器替换后不健康 | 用备份目录 `compose-rollback.yaml` 回退该服务到第 2.1 节镜像 ID；未执行 test-activate 时不需处理 `mor_platform` |
| 启用后发现阻断缺陷 | 先执行 [test-deactivate.sql](./test-deactivate.sql)（31 / 9 / 7 / 1），再回退镜像（顺序 ops-web → ops-bff → channel-connector → platform） |
| 只回退 BFF 或 Platform | 必须先 test-deactivate，否则新路由 404 / BFF 调不到 Platform 新接口 |
| 注册数据 | 块 92 休眠时无影响，通常保留；删除需另行授权：按头注 ID 段删子表再删主表 |
| Vault 规则 | `DELETE FROM t_vault_access_rule WHERE access_rule_id=9100000000000720; DELETE FROM t_vault_policy_change WHERE policy_change_id=9100000000000720;`，Vault 自动重载 |
| DDL | `DROP TABLE mor_channel_connector.t_channel_config_change;`（验收清理后为空表） |
| 不可逆项 | 验收产生的 Vault Token 与 Platform 审计留存；Platform 审计不回滚 |

## 6. 执行记录（阶段 7 填写）

执行人：第 1–3 步 REL agent（Opus 5.5，`tasks/REL-report.md`）；第 4–6 步主会话；第 7–8 步验收 agent（VER）；记录主会话。时间 UTC。凭据只从 `test-environment/.env.test` 读入环境变量，未打印。

| 步 | 时间 | 结果 |
|---|---|---|
| 1 复核 | 07:18–07:20 | 与第 2.1 节完全一致（运行镜像、92xxx 0 行、编码 0 冲突、PLATFORM_ADMIN 35、17 表、Vault 规则 720 不存在 / 版本 16、SQL 逐字一致、容器环境键齐全） |
| 2 备份 | 07:20 | `~/Code-MoR-backups/20261020T072007Z-channel-console-test/`（0700 / 0600，SHA256SUMS 全部 OK；含 `compose-rollback.yaml`，`config --quiet` 通过） |
| 3 构建 | 07:20–07:23 | 基线 main `b04b77f1` + 本需求未提交改动，在主工作区构建（非干净 worktree，候选文件头注已过时）；JDK 25 离线 `clean package` 成功；`npm ci && run build` 成功。镜像 tag `channel-20261020`：channel-connector `sha256:4d31d529e1d3258e5e7976f1f98d4ef24bcb8e3d3eca24d1e2aef6c432e5f054`（JAR SHA-256 `e7ede2d4…c7aeb1`）；platform-support `sha256:e5a06814c6ff3b79530cbfd76dbf2c7df1d9f2cd813efc385f2c9b6859526e6b`（JAR `353408a1…1fae0fc`）；ops-bff `sha256:bec8582633e30a28df0c6630d05b724340741072e449ef4945c1155388e36758`（JAR `9a598dd9…fec747b`）；ops-web `sha256:be32382a7a0655dec187d6e88107bbd5354c687dd4230820c3fe7dde6d72598b`（入口 `/assets/index-CGMnwdGz.js`）。JAR 内容核对：所需 Controller / Mapper XML / capabilities 键齐全，与运行 JAR 相比条目只增不减（+118 / +5 / +144），Web 含 ChannelCostPlans / ChannelChannels / ChannelMids 分块 |
| 4 数据库 | 07:24:42 `mor_channel_connector` DDL → 18 张表、注释正常；07:24:55 `mor_vault` 规则 720（PROTECT / CHANNEL_CONFIGURATION / ACTIVE）、策略版本 17（试跑 ROLLBACK 后仍 0 / 16）；07:25:12 `mor_platform` 块 92 → 7 / 7 / 9 / 31 / 138 / 24 / 186，31 条路由 DISABLED，PLATFORM_ADMIN 仍 35（试跑 ROLLBACK 后 0） | 均 root、utf8mb4 |
| 5 替换 | 07:25:36 platform-support healthy（0 ERROR，Started）；07:26:15 channel-connector healthy（Nacos 注册、Started，0 ERROR）；07:27:06 ops-bff healthy（`OPERATIONS_BILLING_BASE_URL` 保留，0 ERROR）；07:27:35 ops-web healthy（入口 `index-CGMnwdGz.js`，3 个 Channel 分块，Nginx 200） | 运行镜像 ID = 候选 ID，重启均 0 |
| 6 启用 | 07:28:02 | 试跑 31 / 9 / 7 / 1（ROLLBACK 后仍 DISABLED / 35）；正式 31 / 9 / 7 / 1，PLATFORM_ADMIN 35 → 36；8 秒后 Gateway `/api/operations/channel-admin/v1/channels` 未登录返回 401（路由生效） |
| 7 验收 | 07:28–07:36 | VER agent（`verification/stage7-acceptance.md`，截图 `assets/stage7/`）：A 接口 105 / 105；B Payment 侧候选查询 2 / 2（渠道执行未跑：mor-test `PAYERMAX` 接入方指向真实 PayerMax UAT 且凭据为合成，环境限制）；C 浏览器 35 / 35；D 既有商务 8 页 + 财务 7 页回归 17 / 17（首轮被 Gateway 限流，间隔 3 秒重跑通过）。缺陷 D1：未配置账号的「配置」列显示 `REJECTED` 标签（`ChannelChannels.tsx`）|
| 7b D1 修复 | 07:37:17 | 改为红色「未配置」标签；Web 1155 例 + build；重建 ops-web `sha256:3ae27f3d3f75f0ba6ececa69a876f217d66121c594aa33d12791c9f329d7be09`（tag `channel-20261020b`，入口 `/assets/index-H8mAJBt-.js`）并替换，healthy、重启 0；07:38 重验 7 / 7 通过（`verification/stage7_d1_reverify.py`，截图 `D1-未配置账号-配置列.png`），重验数据已按 ID 清理 |
| 8 清理 | 07:36 | VER 以 `channel_connector_app` 执行 `verification/acceptance-cleanup-test.sql`（先 SELECT 后 DELETE）：4 渠道、4 账号、1 MID、2 能力组、1 方案 4 版本、27 变更记录，渠道各表回到 0 行；3 个合成 Platform 用户停用、3 个合成角色清空权限（IAM API）。残留：停用用户与空角色、1 条 Vault 记录、审计事件与登录会话 |
| 9 记录 | 07:40 | delivery.md、`agent-docs/CURRENT_IMPLEMENTATION.md`（新条目 `channel-connector-ops-console-20261020`）已更新；四镜像基线（含 D1 后的 ops-web）已发 code-mor-9c。偏差：镜像在主工作区（非干净 worktree）构建，候选文件头注已改 |
