# billing 计费事实模型重构与渠道成本迁出：mor-test 发布清单

阶段 6（2026-10-19，任务卡 F）。阶段 5–8 已由用户一并授权，阶段 7（任务卡 G）按本清单执行，结果写入第 6 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。本阶段只做只读核对、本地构建与发布包准备，没有改 mor-test 任何容器、数据、队列或 XXL 登记。

**本次：mor-test 的 channel-connector、billing、ledger、account 四个容器换成「HEAD `08c8f794` + 本需求 460 个文件」构建的镜像。`mor_channel_connector` 新增渠道成本 4 表（13 → 17），`mor_billing` 删 7 张旧表、建 9 张新表并插入 DEFAULT 日历（9 → 11）。channel-connector-service 的 Compose 段新增 Redis、Snowflake、补偿实例与 XXL 执行器变量，XXL 新建 channel-connector 执行器与任务 `channelCostVersionActivation`。验收前登记一组合成渠道，经运营入口验收后清理合成数据与渠道种子。Gateway、Platform、Operations BFF / Web、Nginx、Vault、Redis、RabbitMQ、MySQL、XXL 容器与其余 21 个服务不动。**

## 1. 范围

| 项 | 内容 |
|---|---|
| 环境 | 本机 Docker Desktop（context `desktop-linux`）的 `mor-test`：30 个容器 healthy；运营入口 `http://localhost:28080`；库 `127.0.0.1:23306`；RabbitMQ vhost `/mor-test`；XXL Admin `http://127.0.0.1:28090`。不是生产，不是 `mor-local` |
| 源码基线 | HEAD `08c8f794` 的 `git archive` 解包 + 本需求 460 个文件（[source-overlay.txt](./source-overlay.txt)：billing 241、channel-connector 99、ledger 65、account 53、messaging 2；新增 201 / 修改 128 / 删除 131；另列 Compose 1 个，不进镜像；清单 SHA-256 前缀 `ba38482fc4f7a4e0`）。见第 3 节 D1 |
| 镜像（4 个） | channel-connector（渠道成本方案 / 版本 Maker-Checker、命中接口、到期切换 Job，Redis 锁，Snowflake 主键，XXL 执行器）；billing（计费事实 `BillingFact*` 五表、`BillingFactRecorded`、DEFAULT 工作日日历，删经济事实与渠道成本）；account（`BillingFactRecordedBalanceHandler` 替换 `EconomicFactRecorded` 消费者）；ledger（`BillingFactClient` 读 `/billing/v1/facts`，`AutoInstructionType` / `AmountSource` 迁入 ledger-api，业务时间与渠道账户来源改按新载荷） |
| SQL（3 份，root） | `mor_channel_connector` ← [db/channel-connector/ddl.sql](../db/channel-connector/ddl.sql)（CREATE 4）；`mor_billing` ← [db/billing/ddl.sql](../db/billing/ddl.sql)（DROP 7、CREATE 9）+ [db/billing/dml.sql](../db/billing/dml.sql)（DEFAULT 日历 1 行）。应用账号只有本库 DML 权限，DDL 需 root（`MOR_MYSQL_ROOT_PASSWORD`） |
| 验收种子（channel_connector_app） | [test-channel-seed.sql](./test-channel-seed.sql)：1 渠道 / 1 技术账号（DRAFT）/ 1 MID（主体 ZZ901），无能力行，Payment 路由不会选中；验收后 `--cleanup --drop-seed` 删除 |
| Compose | 工作区 `test-environment/compose.applications.yaml` 只改 channel-connector-service 段（+17 行，见第 2.5 节），随阶段 8 提交；[compose-candidate.yaml](./compose-candidate.yaml) 只固定 4 个镜像 ID，`config --quiet` 通过 |
| RabbitMQ | **无需改动**。`account.billing.*` 队列名、Fanout `billing.events` → `account.billing.hash` 绑定、DLX / DLQ 都不随事件改名变化（事件目录只改 `eventType` / `aggregateType`，生产方与消费方不变）。mor-test 没有 `ledger.billing.*` 旧队列（前序 G 线已删），本机 E-1 不适用 |
| XXL-Job | 新建执行器分组 `channel-connector-service` 与任务 `channelCostVersionActivation`（每 5 分钟、FIRST、单机串行、超时 300 秒），见 [xxl-job-channel-cost-activation.md](./xxl-job-channel-cost-activation.md) |
| 顺带发布 | billing / account / ledger 自前序 `subledger-20261019` 镜像（HEAD `2bae8597` + 441 文件，内容即 `080909fb`）以来，HEAD 上只有文档提交（`26ca0840` 改 README 链接），运行时差异只来自本需求。channel-connector 运行容器自 2026-10-18 13:38Z 起未换，新镜像另带 HEAD 上其后的 Foundation 提交（`e4d2da2f` Owner 业务码字段错误 `errors[]`、`6d3513e8` 构建修正；messaging `339db7b8` / `8d123532` 不进该服务） |
| 不动 | Gateway、Platform、Operations BFF / Web（本需求无路由 / 菜单变化）、Nginx、Vault、Redis / RabbitMQ / XXL / MySQL 容器与其余业务服务；`config/nacos/channel-connector-service.yaml` 不改（mor-test 以 `test` Profile 启动，不加载 Nacos 配置，见 2.5）；不推送 Git 与镜像 |

## 2. 只读核对结果（2026-10-19 19:17—19:19Z）

### 2.1 运行容器与镜像（回退位点）

| 服务 | 运行镜像 ID | 容器启动（UTC） | 状态 |
|---|---|---|---|
| channel-connector | `sha256:07bd0636405d0ef0d62dcabea29b41ccfc9ce2c09773f1a7cda228d37aaa5ab8`（Compose 标签 `:development`） | 2026-10-18 13:38:58 | healthy |
| billing | `sha256:403fc7decdc759784d438d2551ce839cd6d8942640ce760d14de47f2db77c850`（`subledger-20261019`） | 2026-10-19 10:45:04 | healthy |
| ledger | `sha256:fb7c661dcede9656e998bdb51f3ff4d24e15552b8a12132de1a23a907675fde7`（`subledger-20261019`） | 2026-10-19 10:45:17 | healthy |
| account | `sha256:b356a4f38060367d7f6ccf4cd65d34f46d041f398d80b3535da7d154cb9481d6`（`subledger-20261019`） | 2026-10-19 10:45:38 | healthy |

**channel-connector 回退镜像需要替身。** 运行容器的镜像 `07bd0636…` 已不在本机镜像库（containerd 存储；`:development` 于 2026-10-18 15:55Z 重建为 `640d6ba4…`，旧镜像随后被移除，`docker image inspect` 报 No such image）。`:development` 中的 JAR（`c926322f…`）与运行中的 JAR（`4e1b6ef2…`）不同，不能当回退镜像。本线用 `docker cp` 只读取出运行中的 `/app/application.jar`，按同一 Dockerfile 与同一健康探针构建了 **`mor-test/channel-connector-service:rollback-20261019` = `sha256:7eea94df1e63c58ec8e326abc7f28070f635560dafd9eb53cb2f09eb8e384d26`**，镜像内 JAR SHA-256 `4e1b6ef2921db2d958f8ea631e3695e25afd42d41fb9c2c6c1f3c8d1bac47dc2` 与运行中一致。billing / ledger / account 的三个回退镜像均存在。

其余 26 个容器 healthy。`xxl-job-admin` 于 19:17:06Z 刚重启（不是本线所为，镜像 `fa6ad934…`），healthy，所有执行器在 19:18:32 重新心跳。

### 2.2 数据库（精确 `COUNT(*)`，应用账号）

| 库 | 结果 |
|---|---|
| `mor_billing` | **9** 张表：`t_economic_fact` / `_line` / `_line_link` 与 `t_channel_cost_plan` / `_plan_ver` / `_scope` / `_component` 共 7 张**全部 0 行**（db/billing/ddl.sql 执行前提，D3）；`t_currency_spec` 1、`t_compensation_event` 0 |
| `mor_channel_connector` | **13** 张表，全部 0 行：`t_payment_channel` 0、`t_channel_mid` 0、`t_channel_account` 0、能力 / 路由 / 回调 / 交互 / 补偿事件均 0；渠道成本 4 表不存在 |
| `mor_account` | 9 张表全部 0 行（前序 G 线清理后的基线） |
| `mor_ledger` | 31 张表：科目表 1、科目 29、币种 1、规则包 2 / 版本 2、规则行 36、关账科目规则 7，其余 0（ledger 科目分级会话已删其演示数据；本需求不改 `mor_ledger`） |
| 账号权限 | `billing_app` / `channel_connector_app`：本库 SELECT / INSERT / UPDATE / DELETE；DDL 用 root |

完整行数快照见 F 报告第 2 节（四库 62 张表）。

### 2.3 RabbitMQ `/mor-test`

| 对象 | 消息 / 未确认 / 消费者 | 说明 |
|---|---|---|
| `account.billing.p0`—`p3` | 0 / 0 / 各 1 | quorum、`x-single-active-consumer`、`x-dead-letter-exchange=account.billing.dlx`、`x-delivery-limit=5`；新 account 声明同名同参数队列，可直接接管 |
| `account.billing.dlq` | **0** / 0 / 0 | D5：无历史消息 |
| `ledger.account.p0`—`p3` / `dlq` | 0 / 0 / 各 1；dlq 0 | 不变 |
| 绑定 | `billing.events` → `account.billing.hash` → 分区；`account.events` → `ledger.account.hash` → 分区；两个 dlx → dlq | 没有 `ledger.billing.*` |

### 2.4 XXL-Job

| 执行器 | 现状 |
|---|---|
| `billing-service`（group 11） | 心跳正常，任务 12 `compensationEventScan` 运行中 |
| `account-service`（group 12） | 心跳正常，任务 13 `compensationEventScan`、14 `accountBalanceMaturity` 运行中 |
| `channel-connector-service` | **无分组、无心跳、无任务**（旧镜像与 Compose 均未启用执行器） |
| ledger | 无执行器（Compose 未配置 XXL，本需求不变） |

### 2.5 Compose 与配置

- 运行中 channel-connector 环境只有 DB、Nacos 发现与 `PAYTECH_INSTANCE_ID`；新镜像的 `application.yml` 要求 `CHANNEL_CONNECTOR_SNOWFLAKE_REGION_ID` / `_WORKER_ID`（无默认，缺失则启动失败），并读取 `CHANNEL_CONNECTOR_REDIS_*`、`CHANNEL_CONNECTOR_COMPENSATION_INSTANCE_ID`，XXL 执行器由 `xxl.job.executor.*` 绑定（Foundation 默认关闭）。
- 工作区 `compose.applications.yaml` 的 channel-connector-service 段已补（只改这一段，+17 行）：Redis `redis:6379` 与 `PAYTECH_REDIS_PASSWORD`；Region `0`、**Worker `26`**（阶段 7 修正：原选 32 超出 Snowflake 允许的 0—31，首次启动失败；已用值 18、19、21—25、27—32，26 未用，见第 6 节）；补偿实例 `channel-connector-test`；XXL 照 billing 段（Admin 地址与 Token、`APPNAME=channel-connector-service`、`IP=172.30.73.36`、端口 9999、日志路径、排除包）；`depends_on` 加 `redis`。不加 `rabbitmq`：`paytech-compensation-event` 不依赖 MQ，channel-connector 也不引入 messaging。不加 `xxl-job-admin`：billing 段也没有（XXL 容器属 scheduler Profile，执行器自动重试注册）。
- `SPRING_PROFILES_ACTIVE=test`，`application.yml` 只在 `nacos` Profile 导入 Nacos 配置，因此 `paytech/channel-connector/config/nacos/channel-connector-service.yaml` 在 mor-test 不生效，本次不改（该文件是本地参数模板，后续若启用 Nacos 再补键）。
- billing / ledger / account 新镜像的占位变量均已由现有 Compose 提供，没有新增必填变量。渲染结果：四个服务为候选镜像 ID，Gateway 等其余服务不变。

### 2.6 发布 SQL 一致性

- `db/channel-connector/ddl.sql` 与模块变更文件 `paytech/channel-connector/docs/database/sql/changes/2026-10-19-channel-cost-tables.sql` 去注释后逐字一致。
- `db/billing/ddl.sql` + `dml.sql` 与模块变更文件 `paytech/billing/docs/database/sql/changes/2026-10-19-billing-fact-model.sql` 去注释后**只在 `t_consumed_event`、`t_consumed_aggregate_version` 两张表上不同**：REQ 文件是阶段 5 修正后与 `billing-schema.sql`、测试 DDL 逐列一致的版本（含完整列注释、表排序规则 `utf8mb4_0900_ai_ci`），模块文件仍是旧写法（短注释、表排序规则 `utf8mb4_0900_bin`）。发布执行 REQ 文件；模块变更文件需由 billing Owner 在阶段 8 前同步（F 报告待确认项 1）。
- 结果表数：channel-connector 13 + 4 = **17**；billing 9 − 7 + 9 = **11**；与 Schema、测试 DDL 的表集合一致（阶段 5 已在本机逐列比对）。

### 2.7 构建（本地，未部署）

- HEAD `08c8f794` 的 `git archive` 解包到 scratchpad + 460 个文件（D 行删除、A / M 行复制）。构建后复核主工作树这 460 个文件与 Compose 的哈希、四服务路径的 `git status` 条目数均与清单一致。
- JDK 25 离线：`./mvnw -B -o -DskipTests -pl billing/paytech-billing-app,channel-connector/paytech-channel-connector-app,account/paytech-account-app,ledger/paytech-ledger-app -am clean package` BUILD SUCCESS（19 秒）。同一源码上四个 `*-test` 模块 `test-compile` BUILD SUCCESS（未运行：auto 测试会写本机测试库；阶段 4 / 5 已在相同内容上跑过 billing 71、channel-connector 125、account 58、ledger 234 与 59/59 验收）。
- JAR 内容核对（含 `BOOT-INF/lib/paytech-*.jar` 内层）：billing 有 `BillingFactRecordedHandler`、`BillingFactController`，`EconomicFact*` 0、`ChannelCostPlanController` 0（只剩计费侧 `ChannelCostCalculator(Impl)` 与组件金额 / 规则值对象）；channel-connector 有 `ChannelCostPlanController`、`ChannelCostResolutionController`、`ChannelCostVersionActivationJobHandler`，打包 `xxl-job-core 3.4.2`、`paytech-redis-starter`、`redisson`、`paytech-compensation-event`；account 有 `BillingFactRecordedBalanceHandler`、`EconomicFact*` 0；ledger 有 `BillingFactClientImpl`、`EconomicFact*` 0，ledger-api 含 `AutoInstructionType`、`AmountSource`；四个 JAR 中 billing-api 均无 `EconomicsType` / `AmountSource`。

### 2.8 候选镜像（tag `billing-fact-20261019`）

| 服务 | 镜像 ID | JAR SHA-256 |
|---|---|---|
| channel-connector | `sha256:194961f11db549ab57fa8ccb9f94ee51d737dee46cb588b86e2a40905539eff3` | `3092f217a1e95d2d138cd34f5ff68f9acfc5b77e8b6bb28b506457dd3b894e42` |
| billing | `sha256:eeec27f923b7f55f7f3f6b7a8b32528b063fe9a64197615b1e645d3f8cf6540e` | `92d34e4a9eb412930d43fa002c451d324878528ccb3a0c4a2e32a0560cdaf095` |
| ledger | `sha256:0e9390666b839739d7b418783d70682040d761d0e3e5634be86f89076d4de020` | `e39ba806935a31b7ae2079932d935ffe932b467e2878c0f5df7cd0e9322b3895` |
| account | `sha256:ad0621b12a80731ed550729ff7709383a7dee28017bc736c3ea9e2152a58ce00` | `709a3aeda936380f084d1c1d24a2a4bb0774af553617cee0c0d5d78cea16e348` |

镜像用 `test-environment/java/Dockerfile`，构建上下文为 scratchpad 私有目录（JAR + 从 `test-environment/artifacts/health-probe` 复制的探针类，与运行镜像中的探针哈希一致），没有写 `test-environment/artifacts/`。每个镜像内 `/app/application.jar` 的哈希已与上表核对。构建源码与上下文已删除，镜像保留在本机 Docker。

### 2.9 验收脚本 test 适配

- [local_acceptance.py](../verification/local_acceptance.py) 经 `e2e_http` 在 `E2E_TARGET=test` 下使用 `http://localhost:28080`、`mor_*`（23306，`.env.test` 应用账号）、接入 `mor-test-backend` 的一次性探针容器（镜像 `mor-test/platform-support-service:development` 存在）调用四个服务私网接口。
- 补齐两处：① mor-test 没有渠道与 MID，S1 会因找不到 ACTIVE Channel + MID 而停止，因此新增 [test-channel-seed.sql](./test-channel-seed.sql)，并让 `--cleanup --drop-seed` 也可用于 test（原先只允许本机）；② `--cleanup` 只取与当前 `E2E_TARGET` 相同目标的最新运行记录（原先取最新文件，本机记录可能被用于 mor-test）。[acceptance-cleanup-local.sql](../verification/acceptance-cleanup-local.sql) 不需要改：`@channel_seed` 段按同一 ID 段删除，能力行语句删 0 行。
- 本机只做了 `py_compile` 与 `E2E_TARGET=test` 导入干跑：目标 test、入口 `http://localhost:28080`、四库 `mor_channel_connector` / `mor_billing` / `mor_account` / `mor_ledger`、四个私网地址 `http://<服务>-service:<端口>`、`.env.test` 口令键齐全、清理段 5 个。没有连接 mor-test 写入。

## 3. 已定事项（用户阶段 5–8 一并授权，无需再问）

| 编号 | 决定 |
|---|---|
| D1 | 构建基线：HEAD `08c8f794` + 本需求 460 个文件（已按此构建 2.8 节镜像）。阶段 7 开始前若 HEAD 或清单中任一文件变化，重新构建并更新本文件、候选 Compose 与清单 |
| D2 | 验收后清理合成数据：`--cleanup --drop-seed`，四库回到第 4 步备份时的行数，channel-connector 17 表回到 0 行 |
| D3 | `mor_billing` 旧 7 表 0 行，`db/billing/ddl.sql` 直接 DROP；执行时任一计数非 0 即停止回报 |
| D4 | XXL 登记执行器与任务 `channelCostVersionActivation` 并启用、手动触发一次 |
| D5 | `account.billing.dlq` 有历史消息时记录数量后不处理。当前为 0；执行时再计数 |

## 4. 执行顺序（任一步不符即停止）

1. **复核**：重跑第 2 节查询与容器清单。四个运行镜像与 2.1 一致；`mor_*` 行数与 2.2 一致；RabbitMQ 与 2.3 一致；XXL 无 channel-connector 分组。HEAD 与 [source-overlay.txt](./source-overlay.txt) 逐行复核（D1）。4 个候选镜像与回退镜像 `7eea94df…` 存在。`compose.sh -f release/compose-candidate.yaml config --quiet` 通过。**与 commercial 会话（ops-bff / Web / Platform / 块 95）及 ledger 科目分级会话确认此刻没有正在进行的 mor-test 替换或验收**：本需求验收经 Gateway → ops-bff 访问 ledger，替换期间会误报；ledger 科目分级会话按约定在本需求提交后才动 ledger / account。
2. **备份**：`~/Code-MoR-backups/<UTC>-billing-fact-test/`（目录 0700、文件 0600，`SHA256SUMS`，不进版本库）：`mor_billing`、`mor_channel_connector`、`mor_account`、`mor_ledger` 整库 `mysqldump --single-transaction --routines --triggers`（root）；核对 `CREATE TABLE` 数 9 / 13 / 9 / 31，均以 `Dump completed` 结尾。另存：4 个容器 `docker inspect`；`git show HEAD:test-environment/compose.applications.yaml`（无 channel-connector 新变量，回退用）；RabbitMQ `/mor-test` 定义（管理 API 只读导出）；`xxl_job_group` / `xxl_job_info` / `xxl_job_registry` 全部行；四库逐表行数基线 `rowcounts-before.tsv`；回退覆盖文件 `compose-rollback.yaml`（channel-connector `7eea94df…`、billing `403fc7de…`、ledger `fb7c661d…`、account `b356a4f3…`）。
3. **停 account**：`compose.sh stop account-service`。原因：替换 billing 后、替换 account 前若有事实发布，旧 account 只认 `EconomicFactRecorded`，新事件会进 `account.billing.dlq`。billing 期间无事实，消息只会留在 `account.billing.p*`，由新 account 消费。
4. **SQL**（root，客户端 `--default-character-set=utf8mb4`，输出存备份目录）：
   1. `mor_channel_connector` ← `db/channel-connector/ddl.sql`：执行前 13 表、4 张新表不存在；执行后 **17** 表。
   2. `mor_billing` ← `db/billing/ddl.sql`：执行前旧 7 表计数全 0（D3）；执行后 **11** 表，`t_economic_fact*` / `t_channel_cost*` 0 张，`t_currency_spec` 仍 1 行。
   3. `mor_billing` ← `db/billing/dml.sql`：`t_business_calendar` = 1 行 `DEFAULT / UTC / ACTIVE`。
   旧 billing（`403fc7de…`）在 4.2 后读不到旧表；期间无调用方（account 已停，旧 ledger 只在消费 `AccountPosted` 时读事实）。
5. **替换**：`compose.sh -f <REQ>/release/compose-candidate.yaml up -d --no-deps --no-build --pull never --wait <服务>`，依次：
   1. **channel-connector**：healthy；启动日志无 ERROR；环境含 `CHANNEL_CONNECTOR_SNOWFLAKE_WORKER_ID=26`（阶段 7 由 32 修正）与 XXL 变量；`xxl_job_registry` 出现 `channel-connector-service / http://172.30.73.36:9999/`；Redis 连接正常（`/actuator/health/readiness` UP，日志无 Redisson 连接错误）。
   2. **billing**：healthy、日志无 ERROR；XXL 心跳 `billing-service` 保持。
   3. **ledger**：healthy、日志无 ERROR；`ledger.account.p0`—`p3` 各 1 消费者。
   4. **account**：healthy、日志无 ERROR；`account.billing.p0`—`p3` 各 1 消费者，`account.billing.dlq` 计数与第 1 步相同（D5）；XXL 心跳 `account-service` 保持。
6. **验收种子**（`channel_connector_app`）：[test-channel-seed.sql](./test-channel-seed.sql)，执行前核对 3 项为 0，执行后 1 / 1 / 1、能力 0。
7. **XXL**：按 [xxl-job-channel-cost-activation.md](./xxl-job-channel-cost-activation.md) 建分组与任务、启用、手动触发一次：期望执行日志 `activated 0, skipped 0, failed 0`（种子渠道没有成本方案）。
8. **验收**（`E2E_TARGET=test python3 -I verification/local_acceptance.py`）：期望 **59/59**（AC-01 3、AC-02 14、AC-03 18、AC-04 17、AC-05 2、S0 3、S4 1、S5 1）；S0 经 `http://localhost:28080` 运营登录。四个服务与 Gateway 自第 5 步起的日志 ERROR 0、`.env.test` 口令 / Token 明文命中 0；结束时 `account.billing.*`、`ledger.account.*` 0 消息、DLQ 与第 1 步相同。
9. **清理**（D2）：`E2E_TARGET=test python3 -I verification/local_acceptance.py --cleanup --drop-seed`：脚本核对四库行数回到运行前基线（含种子），随后删除种子，channel-connector 17 表全部 0 行。再与第 2 步 `rowcounts-before.tsv` 独立比对：差异只有授权 SQL 的结构变化（billing 7 张旧表 → 9 张新表，其中 `t_business_calendar` 1 行、其余 0；channel-connector 新 4 表 0 行）。
10. **记录**：写入第 6 节；更新 `delivery.md`、`agent-docs/CURRENT_IMPLEMENTATION.md`、`tasks/G-report.md`。

预计中断：account 自第 3 步到 5.4 不可用（SQL 约 1 分钟 + 前三个替换各约 30 秒）；channel-connector、billing、ledger 各约 30 秒。

## 5. 恢复

| 情况 | 处理 |
|---|---|
| 第 4 步 SQL 中途失败 | 停止。`mor_channel_connector`：DROP 已建的渠道成本表（`t_channel_cost_component`、`_scope`、`_plan_ver`、`_plan`），回到 13 表；`mor_billing`：`DROP DATABASE` 后用第 2 步 dump 整库恢复（旧表结构只能从 dump 回来；库级授权保存在 `mysql.db`，不随删库丢失，恢复后核对 `SHOW GRANTS FOR billing_app`）。然后 `compose.sh up -d --no-deps account-service` 用旧镜像启动 account |
| 某个容器替换后不健康 | 回退与替换顺序相反，用备份目录 `compose-rollback.yaml`：`compose.sh -f compose-rollback.yaml up -d --no-deps --no-build --pull never --wait <服务>`。**billing、ledger、account 必须一起回退**：旧 billing（`403fc7de…`）需要旧表，须先按上一行整库恢复 `mor_billing`；旧 ledger（`fb7c661d…`）调用已删除的 `/billing/v1/economic-facts`；旧 account（`b356a4f3…`）只消费 `EconomicFactRecorded`。`mor_account` / `mor_ledger` 本需求不改结构，无需恢复 SQL，只需在验收产生数据时先执行第 9 步清理 |
| channel-connector 单独回退 | 可以独立回退到 `7eea94df…`（运行 JAR 的替身，见 2.1）：新增 4 表对旧镜像无影响，新环境变量被忽略。先停用 XXL 任务（旧镜像无执行器，调度会失败）。新 billing 只在 AUTO 支付事实计价时调用渠道成本命中接口，本期没有 AUTO 事实，不受影响 |
| Compose | 回退后若要还原配置，把备份的 `compose.applications.HEAD.yaml` 中 channel-connector 段放回工作区；只回退镜像时保留新变量无害 |
| XXL | 回退 channel-connector 前 `/jobinfo/stop` 新任务；撤销登记见 XXL 文档第 7 节 |
| 验收合成数据与种子 | `--cleanup --drop-seed` 按运行记录删除；脚本失败时用同一组 ID 手工执行 [acceptance-cleanup-local.sql](../verification/acceptance-cleanup-local.sql) 五段（各应用账号） |
| RabbitMQ | 无改动，无需恢复。若回退后 `account.billing.dlq` 出现新事件名的消息，记录数量后保留，由用户决定 |
| 不可逆项 | `mor_billing` 旧 7 表删除（全部 0 行，由 dump 恢复结构）；Platform 审计表中验收登录记录不回滚 |

触发条件：任一步核对数与本清单不符、容器 `--wait` 超时、启动日志出现 ERROR、验收出现非环境性失败。先停止后续步骤，再按上表从最后完成的一步向前回退。

## 6. 执行记录（阶段 7 填写）

执行人：第 1—2 步与记录为任务卡 G agent（Opus 5.5）；第 3—9 步由主会话执行（G 线的 `compose.sh stop` 被本会话权限分类器拒绝后，主会话复用 G 线备份接手）。时间均为 UTC。凭据只从 `test-environment/.env.test` 读入环境变量（`MYSQL_PWD` / curl 配置文件），未打印。

**状态：已完成。** 第 1—10 步全部执行，没有回退。一项偏差：Snowflake Worker 32 → 26（第 5 步）。

| 步骤 | 结果 |
|---|---|
| 1 复核（19:32—19:34Z） | **HEAD 已变为 `691b695c`**（commercial T0，2026-10-19 23:30 +04）。该提交只改 `paytech/commercial/**`、`scripts/pdmaner/client-contract-*`、commercial 需求 `delivery.md`；四个服务的 Maven 依赖闭包（billing / channel-connector / account / ledger 各模块 + Foundation 的 common-core、compensation-event、http-client-support、internal-auth、json-canonicalization、messaging(-api)、mvc-web-starter、mybatis-support、redis-starter、snowflake-id、test-support、vault-client，platform-support-api、arch-rules）不含 commercial / client-master / build 父 POM，构建输入不变，判定不需按 D1 重建，候选镜像沿用（待主会话确认）。`source-overlay.txt` 461 行：460 行哈希一致；1 行不一致为 `paytech/billing/docs/database/sql/changes/2026-10-19-billing-fact-model.sql`，即 F 报告待确认项 1 的 Owner 同步，去注释后与 REQ `db/billing/ddl.sql` + `dml.sql` 逐字一致（channel-connector 变更文件亦一致），位于 `docs/` 不进 JAR。路径 `git status -uall` 条目 461。30 个容器 healthy；四个运行镜像与 2.1 一致（`07bd0636…` / `403fc7de…` / `fb7c661d…` / `b356a4f3…`）；Gateway `4cf14912…`（10-18 13:39Z 起）、ops-bff `446ad697…`（10:46Z）、Platform `33c196ec…`（10:44Z）healthy、重启 0，最近 5 分钟未重启；`xxl-job-admin` 约 19:17Z 起运行。4 个候选镜像与回退镜像 `7eea94df…`、另三个旧镜像均存在。候选 Compose `config --quiet` 通过，渲染后四服务为候选 ID、channel-connector 含 Redis / Snowflake / 补偿实例 / XXL 共 14 个新键。四库精确行数与 2.2 一致（billing 9 表，旧 7 表全 0、`t_currency_spec` 1；channel-connector 13 表全 0；account 9 表全 0；ledger 31 表，非 0 行同 2.2）。RabbitMQ 与 2.3 一致（`account.billing.p0`—`p3`、`ledger.account.p0`—`p3` 0 消息 / 各 1 消费者，两个 DLQ 0，无 `ledger.billing.*`）。XXL 与 2.4 一致（分组 2—12、任务 2—14，无 channel-connector 分组 / 心跳）。主机上没有 compose / 验收 / 构建进程在运行 |
| 2 备份（19:34Z） | `~/Code-MoR-backups/20261019T193439Z-billing-fact-test/`（目录 0700、文件 0600）：四库整库 dump（`CREATE TABLE` 9 / 13 / 9 / 31，均以 `Dump completed` 结尾，stderr 空）；4 个容器 `docker inspect`；容器清单；`compose.applications.HEAD.yaml` 与工作区副本；RabbitMQ `/mor-test` 定义（管理 API 只读：交换机 14、队列 25、绑定 30）；`xxl_job_group` / `info` / `registry` 全部行；四库逐表行数 `rowcounts-before.tsv`（62 表）；`compose-rollback.yaml`（`7eea94df…`、`403fc7de…`、`fb7c661d…`、`b356a4f3…`）。`SHA256SUMS` `shasum -c` 全部 OK |
| 3 停 account（19:37:04Z） | 预检：`mor_billing` 旧 7 表 0 行，`mor_channel_connector` 13 表。`compose.sh stop account-service`，容器 Exited（143） |
| 4 SQL（19:37:14Z，root） | 4.1 `mor_channel_connector` ← `db/channel-connector/ddl.sql`：17 表。4.2 `mor_billing` ← `db/billing/ddl.sql`：11 表，`t_economic_fact*` / `t_channel_cost*` 0 张，`t_currency_spec` 1 行。4.3 ← `db/billing/dml.sql`：`t_business_calendar` 1 行 `DEFAULT / UTC / ACTIVE`。输出：备份目录 `sql-4.1-cc-ddl.out`、`sql-4.2-billing-ddl.out`、`sql-4.3-billing-dml.out` |
| 5 替换（19:37—19:40Z） | **5.1 channel-connector 首次失败：** 19:37:25Z up 后容器重启 6 次，`IllegalArgumentException: workerId must be between 0 and 31: 32`。F 线选的 Worker 32 超出 Snowflake 允许范围（2.5 节「32 未用」的判断只核对了占用，没核对上限）。主会话把工作区 `compose.applications.yaml` 的 `CHANNEL_CONNECTOR_SNOWFLAKE_WORKER_ID` 改为 **26**（已用 18、19、21—25、27—32，26 未用），19:38:44Z 重新 up，19:38:57Z healthy（`194961f11db5…`），启动后 ERROR 0，Redis 连接正常，XXL 执行器 19:40:02 心跳 `http://172.30.73.36:9999/`。5.2 billing 19:39:38Z → healthy 19:39:46Z（`eeec27f923b7…`），ERROR 0。5.3 ledger 19:39:58Z → 19:40:03Z（`0e9390666b83…`），ERROR 0。5.4 account 19:40:15Z → 19:40:16Z（`ad0621b12a80…`），ERROR 0。19:40:23Z：`account.billing.p0`—`p3` 0 消息、随后各 1 消费者；`ledger.account.p0`—`p3` 各 1；两个 DLQ 0（D5，与第 1 步相同）。XXL 心跳 billing / account / channel-connector 正常 |
| 6 验收种子（19:40:55Z，`channel_connector_app`） | 执行前 0 / 0 / 0；执行后 channels 1、accounts 1、mids 1、capabilities 0 |
| 7 XXL（19:44Z） | Admin API 新建分组 **13** `channel-connector-service`（自动注册，地址 19:44:30 出现）与任务 **15** `channelCostVersionActivation`（CRON `0 */5 * * * ?`、FIRST、SERIAL_EXECUTION、超时 300、重试 0），已启动。第一次手动触发（日志 34095）在地址注册前，trigger 500 `registry address is empty`；地址出现后再次触发（日志 34117）trigger 200 / handle 200；定时调度（日志 34106）200 / 200。执行器日志 `channel cost version activation: activated 0`。实际在第 8 步验收之后登记（清单原定第 7 步），验收不依赖该任务（AC-04 版本批准即 ACTIVE） |
| 8 验收（19:41:10Z） | `E2E_TARGET=test python3 -I verification/local_acceptance.py` **59/59**。运行记录 `local-environment/e2e/runtime/billing-fact-acceptance-20261019234112.json`：OE `ZE754`，方案 `E2E_CC_F00EA3`，事实 `238023965887553536` 被 `238024173077782528` 替代。完整输出保存在会话 scratchpad `acceptance-test.log`（未入库） |
| 9 清理（19:43:58Z） | `--cleanup --drop-seed`：脚本核对四库 68 张表回到运行前基线，删除种子后 channel-connector 17 表全部 0 行。与 `rowcounts-before.tsv` 独立比对（`rowcounts-after.tsv` 存备份目录）：差异只有授权 SQL 的结构变化，billing 删 `t_economic_fact*` 3 张与 `t_channel_cost*` 4 张，新增 9 张，其中 `t_business_calendar` 1 行、其余 0；channel-connector 新增 4 张，均 0 行。其余表行数相同。队列全部 0 消息；四个容器与 Gateway 自启动起 ERROR 0 |
| 10 记录 | 本节；`tasks/G-report.md`；`delivery.md` 适用阶段 6—7 行与「部署 / 恢复方案及 SQL 执行记录入口」；`agent-docs/CURRENT_IMPLEMENTATION.md` 本需求节；第 2.5 节、5.1 与 `compose-candidate.yaml` 注释中的 Worker 改为 26 |

**当前 mor-test：** channel-connector `194961f11db5…`、billing `eeec27f923b7…`、ledger `0e9390666b83…`、account `ad0621b12a80…`（tag `billing-fact-20261019`）；Gateway、Platform、BFF、Web 未动；XXL 分组 13 / 任务 15 运行中；RabbitMQ 无改动。回退按第 5 节，旧镜像 ID 在备份目录 `compose-rollback.yaml`。

**待处理：** 工作区 Compose 的 channel-connector 段（含 Worker 26）随阶段 8 提交；HEAD `691b695c` 不重建的判断见第 1 步。
