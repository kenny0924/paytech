# channel-connector 统一对外渠道出口（第一段）：mor-test 发布清单

阶段 6 评审稿（2026-10-21，核对时间 09:39–10:10Z）。本阶段只做了只读核对、在干净 worktree 里构建镜像、写发布包，**没有改 mor-test 的任何容器、数据库、Vault、Nacos、XXL 或 compose 主文件**。阶段 7 须用户确认第 11 节后，由主会话按第 6 节执行，结果写第 13 节。当前实现状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：** mor-test 的 card、channel-connector、operations-console-bff、platform-support 四个容器换成 `main 1b60ae43 + 本需求改动` 构建的镜像（tag `ccu-20261021`，部署只用 sha256）。`mor_channel_connector` 加 10 张协议表与 `change_detail` 列，并从 `mor_platform` 原样复制 10 表数据；验收通过后 `mor_platform` 删除这 10 张表。不改环境变量、Vault 规则、Gateway 注册、Nacos 和 XXL。

## 1. 环境与范围

| 项 | 内容 |
|---|---|
| 环境 | 本机 Docker Desktop（context `desktop-linux`）的 Compose 项目 `mor-test`；运营入口 `http://localhost:28080`；MySQL `mor-test-mysql-1`（宿主 `127.0.0.1:23306`，8.4.11，GTID OFF）。不是生产，也不是本机 `mor-local` / e2e 库 |
| 数据库身份 | 结构与数据迁移：root（口令从 `test-environment/.env.test` 读入，经 `MYSQL_PWD` 传给容器，不回显）。应用账号 `channel_connector_app` 对 `mor_channel_connector.*` 已有库级 SELECT / INSERT / UPDATE / DELETE，新表无需追加授权 |
| 替换的容器 | `card-service`、`channel-connector-service`、`operations-console-bff`（第 6 节步骤 4、8），`platform-support-service`（步骤 11，验收通过后） |
| 库 | `mor_channel_connector`：DDL + 数据导入（18 → 28 表）；`mor_platform`：验收后 DROP 10 表（57 → 47） |
| 不动 | Gateway、Nginx、Payment、ops-web 等其他容器；Vault 规则（channel-connector 的 REVEAL 规则已存在，见第 4 节）；`t_gateway_route` 等 Platform 注册（`/external-api-admin` 路由仍指向 BFF，路径不变，`EXTERNAL_API_*` 权限保留）；`test-environment/compose*.yaml`（只用本目录覆盖文件）；Nacos 配置中心（mor-test 为 `test` profile，`spring.cloud.nacos.config.enabled=false`） |

## 2. 制品：当前与目标

| 服务 | 当前运行（回滚目标） | 当前的源码基线（JAR 逐项比对） | 候选（tag `ccu-20261021`） | 候选 JAR SHA-256 前缀 |
|---|---|---|---|---|
| card-service | `sha256:68d676840ac2d5331c49637f9522c9f714548bed6aec60d2812318d5823d77c0`（counterparty-20261020，10-20 03:22Z 起） | c661e9fc。与 1b60ae43 构建只差 1 项：`paytech-messaging` 的 `catalog/events.yaml`（2f5f37f7 新增对账事件，card 不收发这些事件） | `sha256:aee937997621b6246ca4fed8eed7233019246f2a1ecaa3d6e4056b7823b8adb5` | `bb118a6b5c1d71b7` |
| channel-connector-service | `sha256:4d31d529e1d3258e5e7976f1f98d4ef24bcb8e3d3eca24d1e2aef6c432e5f054`（channel-20261020，10-20 07:26Z 起） | 5f12cf3e。与 1b60ae43 构建只差嵌入的 `paytech-platform-support-api` 中 5 个币种类（13c02c87；新版本已不依赖该 JAR） | `sha256:1571ce18b74987b4566ce1786af1a2bc50da14b2bd9cf96d688c69c438702abb` | `3f3f6e326030aca5` |
| operations-console-bff | `sha256:e822301cf35cab0dbce8ea87619d37d86c6ca5bed51c04fb2b3fa5b16968eab4`（recon-detail-20261021，10-21 05:55Z 起） | **1b60ae43，0 差异**（36,606 项） | `sha256:4839c249e59c86d1f507b75ed25858ad7dbeabcfd816c1885fcb62d200ff206b` | `274fed6fb50ed779` |
| platform-support-service | `sha256:3d8bc3752c37da4f57e6bfad764a6712758bef45b484980262ddbe68414ebd72`（recon-detail-20261021，10-21 05:54Z 起） | **1b60ae43，0 差异**（76,974 项） | `sha256:2d40c1ba7f9aa1cc0f4da838b23e880f414201574cba36fc4d4dc1587d7ba330` | `aa66765d3bf1139c` |

**基线结论。** 任务卡提到的 platform `95348566…` / ops-bff `9708ccdb…`（商务 cp-int `c6eea71a`）已不在运行：之后币种会话（currency-20261021）和对账会话（recon-detail-20261021，源 `13c02c87 + 对账改动`，已提交为 `1b60ae43`）先后替换过。逐项比对（含嵌套 JAR 内每个条目的内容 SHA-256）证明 `1b60ae43` 构建的 platform、ops-bff 与运行版完全相同，card、channel-connector 只差上表所列的已提交公共库内容，因此统一取 **main `1b60ae43`** 作基线：不会回退商务开通定价（ops-bff `Commercial*Controller` 5 个类与运行版逐字节相同；Platform `console-resource-capabilities.json` 49 个组件，含 `OPERATIONS_COMMERCIAL_TEMPLATES`、`OPERATIONS_CURRENCY_SPECS`、`OPERATIONS_FINANCE_RECON_CHANNEL_LINES`，与运行版逐字节相同）、币种或对账功能。

**发布 worktree。** `/Users/kenny/Desktop/Workspace/Code-MoR-ccu-release`（detached `1b60ae43`）+ `Code-MoR-ccu` 相对 `4ab205e0` 的本需求改动（1010 个路径：561 新增或修改、449 删除；需求目录下 2 个文档已移除，不进构建）。

- 999 个路径在 `4ab205e0..1b60ae43` 之间未被他人改动，直接取 `Code-MoR-ccu` 版本或删除。
- 11 个共享路径用 `git merge-file` 三方合并（base `4ab205e0`，ours `1b60ae43`，theirs `Code-MoR-ccu`）：5 个无冲突且与主工作区逐字节相同；`PlatformClientConfiguration.java`、Platform `application.yml`、`application-mock.yml`、`table-ownership.yaml`、`TableOwnershipAutoTest.java` 有冲突，`generate-table-ownership.mjs` 无冲突但顺序不同。这 6 个文件取主工作区阶段 4 已人工合并的版本：逐个确认它们相对 `1b60ae43` 的差异只含本需求的 hunk（删 7 个 externalapi Bean、删 `ps.integration.*` 与 `/external-api-admin` 前缀、表归属 57→47 / 18→28 等），对账、币种的内容都保留。`generate-table-ownership.mjs --check` 通过。
- 结果：发布 worktree 相对 `1b60ae43` 的 1008 个改动路径与主工作区逐字节相同（0 差异）；主工作区另有 2 个文档（`paytech/commercial/technique.md`、`PLATFORM_SUPPORT_PHYSICAL_DECISIONS.md`）属其他会话或文档，不进 JAR。所以阶段 8 按主工作区提交，代码与本镜像一致。

**测试（发布 worktree，JDK 25，`-o`）。**

| 命令 | 结果 |
|---|---|
| card / channel-connector / platform-support / ops-bff 四个 test 模块与 `tools/arch-rules`，`-am clean test-compile` | 通过 |
| channel-connector `ArchitectureAutoTest, ChannelConnectorArchitectureTest, auto.domain.*Test` | 53 / 53 |
| platform-support `ArchitectureAutoTest` | 1 / 1 |
| card `ArchitectureAutoTest` | 1 / 1 |
| operations-console-bff 全模块 | 2018 / 2018（阶段 5 为 1984，增量为对账会话新增用例） |
| `tools/arch-rules` 全部 | 63 / 63 |
| `tools/arch-rules-reactor-test` `ReactorArchitectureAutoTest` | 3 / 3 |

需库测试（channel-connector 330、Platform 734、card 7）没有在发布 worktree 重跑：本阶段不建隔离库。它们在阶段 4 对同一份需求代码（与本次叠加内容逐字节相同）跑过；`1b60ae43` 相对阶段 4 合入基线的增量是对账详情页（reconciliation / ops-bff / Platform capabilities），上表无库测试已覆盖 ops-bff 与架构规则。

**镜像。** 四个 `*-exec.jar` 由 `./mvnw -B -o -DskipTests -pl <四个 app> -am clean package` 产出，`docker build -f test-environment/java/Dockerfile --build-arg JAR_FILE=<服务>.jar -t mor-test/<服务>:ccu-20261021 <上下文>` 构建。上下文只含 JAR 与 `health-probe/`（取自运行容器，四个服务逐字节相同）。没有使用 `build.sh`，没有改 `:development` 或 `test-environment/artifacts`。核对结果：

- 镜像内 `/app/application.jar` 与构建产物 SHA-256 相同；
- Entrypoint、Cmd、User、WorkingDir、Env 与对应运行镜像相同。

**候选 JAR 与运行 JAR 的差异（全部属于本需求）。**

| 服务 | 差异 |
|---|---|
| card | 改 2 项：`CardApiConstants`（新增 `CHANNEL_CONNECTOR_SERVICE`）、`RevealChannelCardMaterialProcessor`（读卡同时接受 channel-connector-service 与 platform-support-service）。另外 `events.yaml` 随基线更新 |
| channel-connector | 新增 protocol 模块（302 项）、`aviator-5.3.3`（391）、`card-api`（22）、api `externalapi` 与枚举（72）、app 13 个类（7 个外部 API Controller、3 个 Converter、`ExternalApiInputs`、`PaymentCardMaterialConfiguration`、`ChannelConnectorApiErrorConfiguration`）、core 2 个（`PayOutputs`、`ChannelConfigTransactions`）。删除 `paytech-platform-support-api`（341）与 core 中迁往 protocol 的 14 项（变更记录 DataObject / Mapper / Repository / Service、`Platform*Client`、`PlatformPayOutputs`）、`ChannelConnectorOutbound*` 2 个类。渠道成本、渠道 / 账号 / MID / 能力组、候选与执行的 Controller 全部保留（`ChannelCostPlanController`、`ChannelMidController` 等）。`application.yml` 去掉 `channel-connector.outbound.*`，加入 `channel-connector.protocol.*`，默认值不变 |
| ops-bff | 改 12 个类（`ChannelConnectorClientConfiguration`、`PlatformClientConfiguration`、7 个外部 API Controller、`ExternalApiContractConverter`），`integration/` 下 14 个 Port / Impl 改名（删 14、增 14）。删除 `platform-support-api`（78 项），新增 `channel-connector-api`（72 项） |
| platform-support | 删除 integration 模块（288 项）、`aviator`（391）、`card-api`（22）、api 的 `externalapi` / `standardapi` / 8 个枚举（78）、app 13 个类（外部 API Controller、`StandardApiExecutionController`、`IntegrationLookupController`、`PaymentCardMaterialConfiguration`）。改 `application.yml`、`PersistenceConfiguration`、`PlatformApiErrorConfiguration`。`console-resource-capabilities.json` 不变（`OPERATIONS_EXTERNAL_*` 菜单仍在） |

## 3. 环境变量变更

**结论：四个服务的环境变量都不需要新增、删除或修改。** 候选覆盖文件只换 image。按 `compose.sh -f compose-candidate.yaml config` 渲染，再与运行容器逐键比较，只比较键与值是否相等，不输出值：

| 服务 | 渲染键数 = 运行键数 | 不一致 |
|---|---|---|
| channel-connector-service | 33 | 无 |
| platform-support-service | 59 | 无 |
| operations-console-bff | 20 | 无 |
| card-service | 38 | `CARD_FINGERPRINT_KEY` 字符串不同，见下 |

**逐项核对。**

| 关注项 | 现状 | 新代码 | 需改 |
|---|---|---|---|
| channel-connector 读卡客户端 | 无相关变量 | `PaymentCardMaterialConfiguration` 经 Nacos 发现 `card-service`（card 已注册 172.30.73.37），`channel-connector.protocol.execution.card-context-path: ""`，与 card 无 context path 一致 | 否 |
| channel-connector Vault | 无相关变量；`vault.client.caller-service=channel-connector-service` 已在 5f12cf3e 用于 PROTECT | 同一客户端增加 REVEAL（规则已存在，第 4 节） | 否 |
| internal-auth 模式 | 四个容器都没有 `*_INTERNAL_AUTH_MODE`，即 OFF | `CHANNEL_CONNECTOR_INTERNAL_AUTH_MODE` 默认 OFF；P1 报告遗留第 9 项：开启时 channel-connector 无 Token 来源，会启动失败 | 否（保持 OFF） |
| `channel-connector.outbound.platform-*` | 运行版只用 JAR 默认值（`/platform-support`、30s），无环境变量 | 已删除 | 否 |
| `channel-connector.protocol.execution.allowed-schemes` | — | 默认 `[https]`，与 Platform 原默认一致（mor-test Platform 从未覆盖）；mor-test 接入方地址都是 https | 否 |
| Platform `ps.integration.*` | 运行容器无此类变量，`SPRING_APPLICATION_JSON` 只含 webhook 调用方 | 已删除 | 否 |
| ops-bff → channel-connector | 经 Nacos 发现 `channel-connector-service`，无固定地址变量 | 外部 API 管理改由独立代理工厂调用 channel-connector（连接 3s / 读取 5s，与原 Platform 工厂相同） | 否 |
| JVM 信任库 | Platform 有 `JAVA_TOOL_OPTIONS` 信任库（JDK cacerts + test CA）；channel-connector 没有，使用 JDK 默认 cacerts | 出站改由 channel-connector 发出 | 否：mor-test 的接入方地址都是公网 CA 证书（PayerMax / PayBy / dLocal / Alipay+ / Toss UAT）；`DEMO_NI` 的 `demo-ni.example.test` 本来就不可达。若以后有接入方用 test CA 签发的地址，需给 channel-connector 加信任库 |

**`CARD_FINGERPRINT_KEY` 观察（与本需求无关，但阶段 7 会经 `compose.sh` 重建 card 容器）。**

- 运行容器的值有 44 个字符，与 `.env.test` 相同，以 Base64 填充 `=` 结尾；`compose.sh` 渲染出的值是 43 个字符。
- 原因：`compose.sh` 用 `IFS='=' read -r variable value` 导出变量，bash 会去掉行尾那个 `=`；导出的变量又优先于 `--env-file`。`.env.test` 中 `OPERATIONS_ACCESS_LOG_HMAC_MATERIAL` 也以 `=` 结尾，有同样现象。
- 影响：card 用 `java.util.Base64.getDecoder()` 解码，填充可省略。两串解码后的 32 字节已核对相同，所以卡指纹不变，不影响已存卡（mor-test `mor_card` 目前 0 行）。
- 建议接受；`compose.sh` 的修正留给环境 Owner。

## 4. mor-test 数据现状（只读，2026-10-21 09:4x–10:0xZ）

输出见 [sql/00-precheck-20261021.out](sql/00-precheck-20261021.out)（由 [sql/00-precheck.sql](sql/00-precheck.sql) 生成）。

| 项 | 结果 |
|---|---|
| 表数 | `mor_platform` 57；`mor_channel_connector` **18**；`mor_card` 8 |
| 目标库 | 10 张协议表都不存在；`t_channel_config_change` 尚无 `change_detail`（现有 13 条变更记录） |
| `mor_platform` 10 表行数 | t_standard_api 6、t_api_field 444、t_integration 6、t_integration_api 22、t_integration_script 12、t_integration_standard_api 22、t_integration_verify_account 5、t_integration_verify_case 27、t_integration_verify_run 94（PASSED 91 / FAILED 3，RUNNING 0）、t_integration_verify_run_step 141；合计约 1.3 MB |
| 10 表结构 | 与增量 DDL 中的 CREATE 语句逐列、逐索引、表注释一致；只有 `DEFAULT NULL` 写法与索引列空格的格式差异，CHECKSUM 对照有效 |
| 接入方 | ALIPAY_PLUS、DLOCAL、PAYBY、PAYERMAX（`https://pay-gate-uat.payermax.com/...`，真实 UAT）、TOSS_PAY（公网沙箱），以及 **DEMO_NI**（`https://demo-ni.example.test/api`，**10-21 03:46Z 由对账会话的演示数据脚本经运营 API 新建，没有 API / 脚本 / 实现**） |
| 未完成的验证运行补偿 | `mor_platform.t_compensation_event` 中 `INTEGRATION_VERIFY_RUN` 为 0（成功即删除） |
| channel-connector 渠道 | `AAAA`（DRAFT，AA001，未配置）；`DEMO_NI`（ACTIVE，AE001 账号已配置，MID `DEMO-NI-AE001-01/02` ACTIVE，CARD/*/*/USD/5999，ACTION.PAY + REFUND）。没有 PAYERMAX 等真实渠道 |
| Vault 规则 | `channel-connector-service` **REVEAL `CHANNEL_ACCOUNT_CONFIG_V1` [CHANNEL_EXECUTION] ACTIVE 已存在**（还有 PROTECT [CHANNEL_CONFIGURATION]）；`platform-support-service` 的同名 REVEAL 规则仍在，第二段再收回 |
| Vault 事件 | 历史上没有任何 `CHANNEL_ACCOUNT_CONFIG_V1` REVEAL，即 mor-test 从未执行过 Payment → 渠道的真实执行 |
| card | `t_card_material_ticket` 0 行，卡相关 5 表全为 0：mor-test 从未签发或读过读卡票据 |
| XXL | `channel-connector-service` 执行器只登记了 `channelCostVersionActivation`（id 15），**没有 `compensationEventScan`**；Platform 有（id 2，`*/30 * * * * ?`，分片广播）。另外 `xxl-job-admin` 容器 RestartCount 129，最近一次重启在 09:50Z（环境既有现象，与本需求无关） |
| 其他会话动向 | 四个容器最近一次替换是对账会话 05:54–05:55Z（platform、ops-bff）。`requirements/` 两小时内没有其他发布记录。最近发布这几个服务的会话：对账 code-mor-93（platform / ops-bff / ops-web）、币种 code-mor-50、商务 code-mor-98 |

## 5. SQL 清单（只写未执行；按序号执行）

| 序号 | 文件 | 目标 | 前置 | 预期 | 回滚 |
|---|---|---|---|---|---|
| 00 | [sql/00-precheck.sql](sql/00-precheck.sql) | 只读 | — | 与第 4 节一致 | — |
| 10 | [sql/10-channel-connector-ddl.sql](sql/10-channel-connector-ddl.sql) | `mor_channel_connector` | 10 表不存在、无 `change_detail` | `t_channel_config_change` 加 `change_detail`，并扩展两列与表的注释；CREATE 10 表；18 → 28 | 文件头的回退语句（DROP 10 表、去掉列并恢复注释），一般不需要：旧代码不读新表与新列 |
| 20 | [sql/20-freeze-check.sql](sql/20-freeze-check.sql) | 只读 | 已通知冻结 | RUNNING 0、待执行补偿 0、指纹在导出前、导入后、切换后三次相同 | — |
| 复制 | `scripts/migrate.sh copy`（命令见下） | `mor_channel_connector` | 10 表为空 | 10 表行数等于第 4 节 | [sql/90-rollback-truncate-channel-connector.sql](sql/90-rollback-truncate-channel-connector.sql) |
| 30 | [sql/30-verify-copy.sql](sql/30-verify-copy.sql) | 只读（root 跨库核对） | 已导入 | V1 10 表行数相等；V2 10 对 CHECKSUM 相同；V3 15 个 JSON / 脚本列逐行 SHA-256 不一致 0；V4 28 表、`change_detail` 存在 | — |
| 40 | [sql/40-platform-drop-integration.sql](sql/40-platform-drop-integration.sql) | `mor_platform` | **验收通过**、删表前再备份、platform 已是候选镜像 | DROP 10 表；57 → 47 | `migrate.sh restore-platform`（从删表前备份恢复）+ 回滚 platform 镜像 |
| 99 | [sql/99-postcheck.sql](sql/99-postcheck.sql) | 只读 | — | 表数、协议表行数、`change_detail` 填充、补偿、Vault REVEAL 调用方、读卡票据 | — |

- `10` 与仓库 `paytech/channel-connector/docs/database/sql/changes/2026-10-20-protocol-tables.sql` 逐字节相同（SHA-256 `9c6233bb5cef403c…`，与发布 worktree 相同）。`40` 与 `db/platform-support-service/drop-integration.sql` 逐字节相同（`a8577fd28085…`）。两者都不带 `USE`，库名由执行包装传入。
- 阶段 5 已在本机按同一文件演练：DDL、数据导入、`30` 口径核对（行数、CHECKSUM、15 列 SHA-256 一致），以及在临时库试跑删表。
- 阶段 6 已在 mor-test 用只读方式核对 `20` 与 `30` 的语法：把目标库替换为 `mor_platform` 自比，10 / 10 行数相等，15 列不一致为 0。

**迁入表注释（待用户确认，建议本次不改）。** 技术方案第 4 节要求 10 张表“原样复制”。增量 DDL 中有 13 处注释还写着 Platform：

- 10 张表的 `create_time` 都是“该记录在 Platform Support 数据库中的 UTC 微秒创建时间”；
- `t_standard_api.standard_api_code`、`t_integration.integration_code` 的取值来源写为 Platform 目录；
- `t_standard_api` 表注释为“Platform 对内提供的标准 API 定义…”。

建议本次不改，理由：

- 改注释会让 mor-test 与仓库 Schema、PDManer、`channel-connector-schema.sql` 不一致，或者需要在发布前再改模型和生成器；
- 注释不影响 CHECKSUM 核对和行为；
- P1 报告遗留第 10 项已登记，随第二段合表改名一并修正。

如果选择本次就改，需要另出 ALTER 文件并同步 PDManer 与完整 Schema，这超出本发布包。

**数据复制命令**（`scripts/migrate.sh copy` 实际执行的内容；`$MYSQL_PWD` 从 `.env.test` 读入，不回显）：

```bash
docker exec -i -e MYSQL_PWD="$MYSQL_PWD" mor-test-mysql-1 mysqldump -uroot \
  --single-transaction --set-gtid-purged=OFF --no-tablespaces --default-character-set=utf8mb4 \
  --no-create-info --skip-triggers --complete-insert \
  mor_platform t_standard_api t_api_field t_integration t_integration_api t_integration_script \
  t_integration_standard_api t_integration_verify_account t_integration_verify_case t_integration_verify_run \
  t_integration_verify_run_step > "$B/mor_platform-10-tables-data.sql"
docker exec -i -e MYSQL_PWD="$MYSQL_PWD" mor-test-mysql-1 mysql -uroot --default-character-set=utf8mb4 \
  mor_channel_connector < "$B/mor_platform-10-tables-data.sql"
```

导出文件若含 `CREATE TABLE` / `DROP TABLE` 会被包装拒绝；导入前目标 10 表必须为空。ID、JSON、脚本源码原样保留（雪花 ID 由 Platform 生成，channel-connector 的 Worker 26 不会与之冲突）。

## 6. 执行步骤与顺序（阶段 7；任一步不符即停）

包装：[scripts/migrate.sh](scripts/migrate.sh) `<步骤> [备份目录]`；`up` 指 `./test-environment/compose.sh -f "$PWD/requirements/20261020/channel-connector-统一对外渠道出口/release/compose-candidate.yaml" up -d --no-deps --no-build --pull never --wait --wait-timeout 180 <服务>`。停启容器与 root SQL 由主会话执行。

| 步 | 动作 | 核对 |
|---|---|---|
| 0 协调 | SendMessage 给 code-mor-93（对账）、code-mor-50（币种）、code-mor-98（商务）：本次将替换 card / channel-connector / ops-bff / platform，候选以 `1b60ae43` 为基线；请确认没有待发布改动，窗口内不运行对账演示脚本（`demo_seed.py` / `demo_cleanup.py` 会经外部 API 管理增删 `DEMO_NI` 接入方）。**期间若有人发布了这四个服务之一，本包作废，须在新基线上重建** | 双方回复 |
| 1 复核 | `docker inspect` 四个容器的 `.Image` 必须等于 [compose-rollback.yaml](compose-rollback.yaml)，全部 healthy；`git -C <主工作区> log -1` 仍为 `1b60ae43`（或新提交不涉及这四个服务） | 有不同即停 |
| 2 备份 | `B=$(release/scripts/migrate.sh backup)`（第 7 节） | SHA256SUMS、两份备份都含 CREATE |
| 3 基线验收（旧版本） | 按第 8 节：`write before` → `rerun before` → `chain before`（授权 C2 时先 `setup-payermax`）→ 冻结开始 → `snapshot before` | 结果写 `acceptance/results/` |
| 4 card | `up card-service` | healthy、重启 0、启动日志 ERROR 0、`.Image` = 候选、Nacos 注册 172.30.73.37；XXL 执行器约 30 秒后回到分组 10 |
| 5 冻结 | `migrate.sh freeze $B`（第 1 次） | running 0、pending 0，记下指纹 |
| 6 DDL | `migrate.sh ddl $B` | 28 张表；`change_detail` 存在 |
| 7 复制与核对 | `migrate.sh copy $B` → `migrate.sh verify $B` → `migrate.sh freeze $B`（第 2 次） | verify 输出“一致”；第 2 次指纹与第 1 次相同 |
| 8 channel-connector → ops-bff | `up channel-connector-service`，然后 `up operations-console-bff` | 都 healthy、重启 0、ERROR 0；channel-connector 日志有 Started、Nacos 注册，XXL 执行器回到分组 13，`channelCostVersionActivation` 仍在；`.Image` = 候选 |
| 9 解冻前确认 | `migrate.sh freeze $B`（第 3 次，Platform 10 表在切换期间没有写入）；通知解冻 | 指纹不变 |
| 10 回归验收（新版本） | 按第 8 节：`snapshot after` → `compare` → `chain after` → `rerun after` → `write after`；`migrate.sh postcheck $B` | 第 8 节期望 |
| — | **确认点：** 把第 10 步结果交用户确认，用户确认验收通过后才进入第 11 步 | — |
| 11 platform 与删表 | `migrate.sh predrop $B` → `up platform-support-service` → 冒烟（运营登录、导航中外部 API 两页仍在、私网 `GET /platform-support/external-api-admin/v1/standard-apis` 返回 404、`/api/operations/external-api-admin/v1/standard-apis` 返回 200）→ `migrate.sh drop $B` → `migrate.sh postcheck $B` | healthy、ERROR 0；47 张表；外部 API 页面正常 |
| 12 清理 | 若做过 C2：`ccu_mortest.py cleanup-sql` 打印按 ID 删除的 SQL，核对 SELECT 后以 `channel_connector_app` 执行 | 渠道表回到 DEMO_NI / AAAA 两个渠道 |
| 13 记录 | 填第 13 节；`delivery.md` 补环境操作记录；更新 `CURRENT_IMPLEMENTATION.md`；通知 code-mor-93 / 50 / 98 新的镜像 ID 与基线（`1b60ae43` + 本需求） | — |

**中断面。**

- card、channel-connector 各重启约 40–60 秒：读卡、Payment 渠道执行、渠道配置页暂时不可用。mor-test 没有真实交易。
- ops-bff 重启约 30 秒：运营控制台整体不可用。
- platform 重启约 60–90 秒：运营登录与 Gateway 快照刷新中断。安排在低峰。

## 7. 备份

`scripts/migrate.sh backup` 在本机生成 `~/Code-MoR-backups/<UTC>-ccu-outbound-mor-test/`（目录 0700、文件 0600，含 SHA256SUMS，不进版本库）：

| 文件 | 内容 |
|---|---|
| `mor_platform-10-tables.sql` | mor_platform 10 表结构与数据（`--single-transaction --set-gtid-purged=OFF --no-tablespaces --skip-triggers`） |
| `mor_channel_connector-full.sql` | mor_channel_connector 全库 18 表（含 `t_channel_config_change` 迁移前结构） |
| `images-before.txt`、`jars-before.txt` | 四个运行镜像 ID、启动时间、重启数、运行 JAR SHA-256 |
| `compose-candidate.yaml`、`compose-rollback.yaml`、`00-precheck.out` | 发布包副本与执行前核对 |
| `mor_platform-10-tables-data.sql` | 第 7 步复制用的纯数据导出（`copy` 时生成） |
| `freeze-1/2/3.out`、`sql-*.out`、`sql-verify.tsv` | 各步输出 |
| `predrop/` | 第 11 步删表前再备份：mor_platform 10 表 + mor_channel_connector 全库 |

## 8. 验收（[acceptance/ccu_mortest.py](acceptance/ccu_mortest.py)）

**设计约束。**

- mor-test 的 PayerMax 指向真实 UAT，而 channel-connector 侧没有任何真实渠道，只有 DEMO_NI（无实现）；PayerMax 的账号凭据只能是合成的；mor-test 也从未执行过渠道操作。
- 不能像本机那样接合成 PayerMax Mock：mock 必须是 https，channel-connector 又不信任 test CA，接入会改环境变量。所以不采用。
- 因此验收分为默认四项（A–D）与两项可选（C2、C3）。默认项要么只读，要么只发查询类沙箱请求，前后对照判断等价。
- 脚本复用 20261017 `e2e_http.py`（`E2E_TARGET=test`）。私网调用用一次性探针容器（`docker run --rm`，接入 `mor-test-backend`，镜像固定为 `sha256:3d8bc375…`），沿用渠道运营控制台阶段 7 的做法。输出写 `acceptance/results/`，写盘前扫描 `.env.test` 中的密钥类值与管理员口令，命中即中止。

| 编号 | 命令 | 内容 | 期望 |
|---|---|---|---|
| A 管理读取 | `snapshot before`（第 3 步冻结时）/ `snapshot after`（第 10 步，任何新写入之前）/ `compare snapshot-before.json snapshot-after.json` | 经 Nginx → Gateway → ops-bff 调用外部 API 管理的全部 GET 类型：编辑能力；标准 API 列表与详情；接入方列表与详情；原子 API、脚本、实现、流程编辑上下文（6 接入方 × 6 标准 API）；验证账号、用例、运行的列表与详情。约 260 次，受 Gateway 每路由 30 次/分钟限制，约 8–10 分钟。参数、凭据、轨迹只比较 SHA-256 | 全部 200；**逐字段 0 差异（含 ID）**。旧版本由 Platform 读 `mor_platform`，新版本由 channel-connector 读 `mor_channel_connector`，内容相同即证明迁移与接口等价 |
| B 沙箱重跑 | `rerun before` / `rerun after` | 7 个非变更型用例各运行一次：PAYERMAX `UAT_QUERY_NOT_FOUND`、`UAT_REFUND_QUERY_NOT_FOUND`、`UAT_REFUND_NOT_EXIST`；PAYBY `UAT_PAY_QUERY_NOT_FOUND`；DLOCAL、ALIPAY_PLUS、TOSS_PAY 各 `SBX_PAY_QUERY_NOT_FOUND`。外部沙箱只收到查询或退款不存在类请求，不创建支付 | 两次 runStatus 与每步 stepStatus、executeStatus、returnId、断言结果相同（历史上全部 PASSED）；before 的运行行在 `mor_platform`，after 的在 `mor_channel_connector`。若外部沙箱自身不可用导致两次都失败，按步骤结果判断，并记录为环境限制 |
| C1 执行链路（只读） | `chain before` / `chain after` | 以 DEMO_NI 的冻结坐标（AE001）经 Payment 同一私网合同 `POST /channel-connector/v1/operations` 发：REFUND（接入方无实现）、QUERY（标准 API 不存在） | 两次结果相同，均为 HTTP 200、`NOT_SENT` 且代码相同；无 Vault REVEAL；不写库。证明执行入口已从 Platform HTTP 改为进程内，未发出类失败的语义不变 |
| C2 真实出站（可选，需授权） | 第 3 步前 `setup-payermax`，之后 `chain before/after` 自动追加 | 登记验收渠道 PAYERMAX：AE001 技术账号，合成 RSA 密钥与随机 appId / merchantNo，经 Vault 真实 PROTECT；MID `PMX-CCU-<时间>`；能力组 CARD/USD/**MCC 0742**，选不常用的 MCC，避免被其他会话的真实下单选中。然后对真实 PayerMax UAT 发 REFUND | 两次结论相同（预期 PayerMax 以签名或商户无效拒绝，映射为 FAIL 或 ERROR）。Vault REVEAL 调用方由 platform-support-service 变为 **channel-connector-service**；不出现 NOT_SENT。证明 Vault Reveal、签名、出站 HTTP 与响应映射都已在 channel-connector 内完成 |
| C3 读卡（可选，需授权，依赖 C2） | `chain before --with-card` / `chain after --with-card` | 以 payment-service 身份在 card 建一次性手输卡工具（PayerMax 公布的 UAT 测试卡，不保存，30 分钟过期），签发读卡票据，再经 `/operations` 发 PAY | 两次票据都 `used=1`。旧版本由 Platform 读卡，新版本由 channel-connector-service 读卡且被新 card 接受。PayerMax 预期拒绝合成商户。输出不含卡号 |
| D 写入抽样 | `write before` / `write after` | 复制 PAYERMAX `UAT_QUERY_NOT_FOUND` 的步骤，新建用例 `CCU_S7_BEFORE` / `CCU_S7_AFTER` → 校验 → 运行一次 → 按版本删除 → 再 GET 应为 404 | 两次各接口状态相同。after 在 `t_channel_config_change` 产生 `VERIFY_CASE` CREATE 与 DELETE 两条，`change_detail` 非空；before 的变更进 Platform 审计（原机制） |
| 冒烟 | 第 11 步 | Platform 新镜像：运营登录、导航含外部 API 两页（`OPERATIONS_EXTERNAL_STANDARD_APIS` / `_INTEGRATIONS`）；Platform 私网 `/external-api-admin` 404；控制台外部 API 页 200；删表后 A 的 `snapshot after2` 与 after 相同 | 全部满足 |

**未在 mor-test 覆盖的部分。**

- 44 个管理接口中的其他写接口（标准 API、接入方、原子 API、脚本、实现、验证账号的增改删）不在 mor-test 上写，避免改动其他会话使用的真实沙箱配置。它们已由以下测试覆盖：channel-connector 330 个需库测试、ops-bff `ExternalApiBrowserAutoTest`（44 个边界用例）、阶段 5 本机端到端。
- 浏览器页面没有改动，不做视觉验收。外部 API 两个页面的可达性由第 11 步冒烟覆盖。

## 9. 验收写入范围

| 项 | 写入 | 保留或清理 |
|---|---|---|
| A、C1 | 无（只读）。C1 不触发 Vault | — |
| B | 14 次验证运行与 14 个步骤：before 7 次写在 `mor_platform` 并随迁移带入，after 7 次写在 `mor_channel_connector`。外部沙箱共 14 次查询类请求 | 运行记录按设计不删除，保留 |
| D | 2 个用例（建后即删）、2 次运行与步骤；after 产生 2 条 `t_channel_config_change`；before 产生 Platform 审计事件 | 用例已删；运行、变更记录、审计保留 |
| C2（可选） | channel-connector：1 个渠道、1 个账号、1 个 MID、1 个能力组（4 条明细）及约 8 条变更记录；Vault：1 条 `CHANNEL_ACCOUNT_CONFIG_V1` 记录与若干访问事件；真实 PayerMax UAT 收到 2 次 REFUND（合成商户） | 第 12 步按 ID 删除渠道数据与变更记录；Vault 记录与事件不删（记录数量） |
| C3（可选） | `mor_card`：2 个一次性卡工具、2 张已用票据、card 的 Vault PAYMENT_CARD_DEK 记录；PayerMax UAT 收到 2 次带测试卡的 PAY（合成商户） | 卡工具 30 分钟后由 `cardOrphanInstrumentPurge` 清理；票据与 Vault 记录保留 |
| 部署本身 | `mor_channel_connector` 28 表与迁入数据；`mor_platform` 删 10 表 | 见第 10 节 |

## 10. 回滚

| 触发 | 处理 | 数据影响 |
|---|---|---|
| 第 4 步 card 不健康 | `compose.sh -f compose-rollback.yaml up … card-service` 回到 `68d67684…` | 无 |
| 第 6–7 步 DDL 失败、复制失败或核对不一致 | 停止；`migrate.sh rollback-truncate $B` 清空 10 表后重新 copy，或保留空表放弃本次（旧代码不读）。需要完全撤销结构时，执行 `10` 文件头的回退语句 | Platform 原表未动，无损失 |
| 第 8 步 channel-connector 或 ops-bff 不健康，或第 10 步验收失败 | 先回滚 ops-bff，再回滚 channel-connector（回滚文件；两者须一起回到旧版，否则 BFF 会调用旧 channel-connector 不存在的 `/external-api-admin`）。然后视需要 `rollback-truncate` | Platform 10 表仍是冻结时的状态，仍然权威。解冻后若在新版本写过外部 API 配置，回滚会丢失这些写入。需要保留时，先 `mysqldump --no-create-info --replace` 导出 channel-connector 的 10 表，经用户确认后导入 `mor_platform`（反向复制，需单独授权） |
| 第 11 步删表前 platform 不健康 | 回滚 platform 到 `3d8bc375…` | 无（表还在） |
| 第 11 步删表后需要回退 Platform | `migrate.sh restore-platform $B`（用 `predrop/` 备份恢复 10 表）+ 回滚 platform 镜像；若还要让 Platform 重新成为执行方，再按上一行回滚 channel-connector 与 ops-bff | 删表后 channel-connector 的新写入不会回到 Platform，除非反向复制 |
| 不可逆 | Vault 访问事件与 C2 / C3 的 Vault 记录、Platform 审计事件、外部沙箱与 PayerMax UAT 已收到的请求、验证运行记录、`t_channel_config_change` 新记录 | 只能记录，不能撤回 |

## 11. 需要用户确认的事项

1. **发布包：** 四个镜像（第 2 节 sha256）、基线 `1b60ae43` + 本需求改动、只换 image 不改环境变量。阶段 7 开始前若有人再发布这四个服务之一，本包作废并重建。
2. **SQL：** 按第 5 节在 mor-test 以 root 执行 `10` DDL、数据复制与 `30` 核对（第 6 步、第 7 步）。`40` 删表放在第 10 步验收结果经你确认之后（第 11 步）；如果你希望本次确认就一并授权删表，请明确说明。
3. **迁入表注释：** 建议本次不改，随第二段改名修正（第 5 节）。
4. **冻结窗口：** 第 3 步末到第 9 步，大约 15–25 分钟。冻结期间运营不改外部 API 配置、不跑校验与沙箱；对账会话不运行演示脚本。
5. **验收写入：** 默认 A、B、C1、D（第 8、9 节）。其中 B 会向 5 个外部沙箱发 14 次查询类请求。
   - **可选 C2：** 登记验收渠道 PAYERMAX（合成凭据、Vault PROTECT），向真实 PayerMax UAT 发 2 次 REFUND。这是唯一能在 mor-test 证明 channel-connector 自己完成 Vault Reveal 与真实出站的用例，建议做。
   - **可选 C3：** 在 card 建测试卡工具，向 PayerMax UAT 发 2 次带 UAT 测试卡的 PAY。
   - 请逐项确认。
6. **私网探针容器：** 验收会在 `mor-test-backend` 上起一次性 `docker run --rm` 容器，不是 mor-test 服务。
7. **XXL（可选）：** channel-connector 没有 `compensationEventScan` 兜底任务。验证运行在提交后立即执行，不依赖它；但执行中途进程退出时，没有兜底重扫。建议登记，参照其他服务：`*/30 * * * * ?`、分片广播、执行器分组 13。这属于 XXL 写入，需单独授权，也可以留到第二段。
8. **`CARD_FINGERPRINT_KEY` 渲染差异：** 解码后相同，建议接受（第 3 节）。

## 12. 风险

- **R1 基线漂移：** 四个服务都是多会话共享容器，见第 6 节第 0、1 步。
- **R2 磁盘：** 宿主磁盘剩余约 6.8 GB。四个新镜像已占用；备份很小，不足 10 MB。阶段 7 前不要再大量构建。
- **R3 外部沙箱：** B、C2、C3 依赖公网沙箱可用。before 与 after 间隔短，可降低沙箱状态变化的影响。
- **R4 行为差异（已知、已接受）：**
  - 进程内执行不再有 Platform 30s 读超时，只受流程预算约束（PayerMax 25s；Payment 读超时 35s）；
  - channel-connector 的 `FailException(NOT_FOUND)` 现在返回 404，core 的渠道配置接口仍为 400；
  - `ChannelConfigChangeApi` 的 targetType 白名单暂不开放 7 个新类型（P1 D9），新变更记录只能查库。
- **R5 DEMO_NI 归属：** 迁移后对账演示清理脚本删除 DEMO_NI 接入方的请求会经 BFF 到 channel-connector，仍然可用；Platform 删表后 Platform 侧不再有这行。需告知 code-mor-93。
- **R6 XXL 不稳定：** `xxl-job-admin` 已重启 129 次（环境既有）。执行器重新注册会有延迟，核对 XXL 时以分组地址为准。

## 13. 执行记录（阶段 7 填写）

| 步 | 时间（UTC） | 结果 |
|---|---|---|
| 0 协调 | 10:0x | 通知 code-mor-93（对账）、code-mor-98（商务）、code-mor-50（币种），三方都回复不动这四个服务、不跑演示脚本；按其提醒核对 ops-bff `OPERATIONS_RECONCILIATION_BASE_URL`（主 compose 已含，候选渲染含）与 platform 能力 `OPERATIONS_FINANCE_RECON_CHANNEL_LINES`（新镜像含） |
| 1 复核 | 10:09 | 四个容器 `.Image` = compose-rollback，全部 healthy、重启 0；main 仍为 `1b60ae43` |
| 2 备份 | 10:10:05 | `~/Code-MoR-backups/20261021T101005Z-ccu-outbound-mor-test/`（SHA256SUMS、两份备份含 CREATE） |
| 3 基线验收 | 10:10–10:20 | D：CCU_S7_BEFORE 校验 200 / 运行 PASSED / 删除 200 / 再 GET 404；B：7 例全部 PASSED；C2 登记验收渠道 PAYERMAX（MCC 0742）；C1：两项 NOT_SENT（INTEGRATION_IMPLEMENTATION_NOT_FOUND / STANDARD_API_NOT_FOUND），无 Vault；C2：ERROR，Vault REVEAL 调用方 platform-support-service；A：snapshot-before 1.3 MB |
| 4 card | 10:11:33 | `aee93799…` healthy、重启 0、ERROR 0（与 A 快照并行，快照不经 card） |
| 5–7 冻结、DDL、复制与核对 | 10:20–10:22 | freeze-1 运行 0 / 待补偿 0；DDL 后 28 表；导出 10 条 INSERT 并导入；verify：V1 10 表、V2 10 对 CHECKSUM、V3 15 列、V4 28 + `change_detail` 全部一致；freeze-2 = freeze-1 |
| 8 channel-connector → ops-bff | 10:21–10:23 | `1571ce18…`、`4839c249…` healthy、重启 0、ERROR 0；XXL 执行器回到分组 13。**新增 XXL 任务 22**（用户确认）：分组 13 `compensationEventScan`，`*/30 * * * * ?`、分片广播、单机串行、超时 60、不重试，参数照 Platform 任务 2；10:23 起每 30 秒 trigger 200 / handle 200 |
| 9 解冻前确认 | 10:23 | freeze-3 = freeze-1，解冻 |
| 10 回归验收 | 10:23–10:33 | A：277 项 before / after 逐字段 0 差异（含 ID）；C1 与基线相同；C2 与基线相同（ERROR），Vault REVEAL 调用方变为 **channel-connector-service**；B：7 例 PASSED，每步 stepStatus / executeStatus / returnId / 断言与基线相同；D 与基线相同，after 在 `t_channel_config_change` 留 VERIFY_CASE CREATE / DELETE 两条且 `change_detail` 非空；postcheck：28 表，验证运行 102→110、步骤 149→157（= B 7 + D 1） |
| 11 platform 与删表 | 10:33–10:35 | predrop 备份；`2d40c1ba…` healthy、重启 0、ERROR 0；冒烟 [smoke_after_platform.py](acceptance/smoke_after_platform.py) 14/14（登录、导航含外部 API 两页与块 126 渠道流水页、控制台外部 API 200、Platform 私网 `/external-api-admin` 404、币种含 USD、对账三列表 200、商务合同列表 / 详情 / 费率预览 / 看板 200）；10:35:04 执行 40 删表，mor_platform 57 → 47；删表后冒烟仍 14/14；billing 仅在 platform 重启窗口 10:33:45 有 1 次币种刷新失败（保留旧快照），之后恢复，ledger 无失败 |
| 12 清理 | 10:35:38 | 按 ID 删除 C2 渠道 / 账号 / MID / 能力组 / 8 条变更记录（channel_connector_app，先 SELECT 核对 1/1/1/1/8）；渠道表回到 AAAA、DEMO_NI。Vault 记录与事件保留 |
| 13 记录 | 10:36 | 本节、`delivery.md`、`CURRENT_IMPLEMENTATION.md`；通知三方新镜像 ID |

**验收偏差与观察：**

- **C2 未真正出站。** 前后两次都在流程节点 `applyRefund` 的公共脚本 `PAYERMAX_SIGN` 失败（验收脚本生成的合成 RSA 私钥格式不被签名脚本接受），请求没有到达 PayerMax UAT。C2 只证明了 Vault Reveal 身份切换与前后等价；真实出站由 B 证明：7 个沙箱用例由 channel-connector 以真实沙箱凭据签名并发往 PayerMax / PayBy / dLocal / Alipay+ / Toss，结果与基线逐步相同；带签名的出站也在阶段 5 本机证明过。
- **观察（既有行为，非本次引入）：** 签名脚本在发送前失败时，结果映射为 ERROR（结果未知）而不是 NOT_SENT，新旧版本一致。第二段可评估把"发送前的脚本失败"归为 NOT_SENT。
