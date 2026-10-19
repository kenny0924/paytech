# commercial 六层产品定价模型 · mor-test 发布准备（阶段 6 草案，2026-10-19 夜间）

> 状态：阶段 6 完成（2026-10-20，任务卡 G）；阶段 7 执行记录见第 6 节。跨会话约束见 delivery.md。

## 1. 发布范围

| 组件 | 动作 | 说明 |
|---|---|---|
| commercial-service 镜像 | 重建并替换，钉 sha256 | 基线 main `cf39a1ee`；`sha256:88dbf741…`（见第 2 节） |
| operations-console-bff 镜像 | 重建并替换，钉 sha256 | 同上；发完把 sha256 与改动文件列表发 code-mor-9c |
| Operations Web | 重建并替换 | 同上 |
| mor_commercial | 执行 [db/commercial/ddl.sql](../db/commercial/ddl.sql)（round2 + round3）与 [db/commercial/dml.sql](../db/commercial/dml.sql)（目录初始化） | 前提：14 张旧表且全空（2026-10-19 核对）；不含 DROP |
| mor_platform | 执行 E 线产出的 `db/platform-support-service/registration-dml.sql`（块 95） | 权限、角色绑定、Console Resource、OPERATIONS Route |
| compose | commercial-service 补环境变量与 depends_on | 只写在 [compose-candidate.yaml](./compose-candidate.yaml)，不改 `test-environment/compose.applications.yaml`（任务卡 G 硬约束）；Snowflake Worker 改 17 |
| XXL-Job Admin | 新建执行器 commercial-service 与任务 commercialTermVersionActivation | 不碰其他执行器 |
| 不动 | billing、channel-connector、account、ledger、acquiring、card、consumer / counterparty、Gateway、RabbitMQ 容器 | 其他会话今晚的范围 |

## 2. 执行前核对（只读，2026-10-19 UTC 阶段 6，任务卡 G）

| 项 | 结果 |
|---|---|
| 运行容器 / 镜像（回退目标） | commercial `sha256:e2d71de3edb3a4cc5ecfca6b4ee5fb39a8f900b8d0c5c242eef872faf2685800`（2026-10-18T13:38Z 起，JAR 前缀 `6b60ba93a8e4baef`）；ops-bff `sha256:446ad6977e6e77725d3c2a517d5419b9e22ead9ad92b72827e09789a4ca1a314`（10-19T10:46Z，JAR 前缀 `a41dfb0990f44f6c`）；ops-web `sha256:12e2e021014e13d0d61d81d711b56c3af051ff546cf8d8cd89c4bd0cb35e55b2`（10-19T10:46Z，入口 `assets/index-BBzYeyTZ.js`）；重启 0 |
| `docker ps` | mor-test 30 个容器全部 healthy（billing / channel-connector / ledger / account 为其他会话约 2 小时前的发布） |
| `mor_commercial` | 14 张旧表，精确 `COUNT(*)` 全部 0 行 |
| `mor_platform` | ID 段 `900000000000095000–95999` 在权限 / 授权 / 资源 / 路由 / Header / Query / 限流 7 表均 0 行；`COMMERCIAL_%` 权限码、`OPERATIONS_COMMERCIAL%` 资源码、`operations-commercial-%` 路由码均 0。基线：路由 298、资源 92、权限 76；PLATFORM_ADMIN 版本 31、66 个 ACTIVE 授权；Target `900000000000000001` ACTIVE |
| 运营账号 | ACTIVE 的 PLATFORM_ADMIN 人员 2 个：`o***@example.test`（`private/access.md`，验收用）与 `1***@xx.com`（无凭据）。第二审批人改由验收脚本经 IAM 接口建合成角色 / 人员（见 test-activate.sql 注释） |
| XXL（`xxl_job` 库只读） | 分组 2—13、任务 2—15、11 个执行器心跳；**无** `commercial-service` 分组 / 心跳 / 任务 |
| Snowflake Worker | mor-test 已用 18—31（channel-connector 已占 26，草案值不可用）；改用 **17** |
| 发布 SQL 一致性 | `db/platform-support-service/registration-dml.sql` 与 `platform-support-initial-data.sql` 末尾块 95 逐字一致（`cf39a1ee`） |
| DDL 演练 | 一次性 `mysql:8.4.11` 容器：导入 mor-test 当前 14 表结构 → `db/commercial/ddl.sql` → `dml.sql`，得 **20** 张表、目录 7 / 11 / 21；与 `commercial-schema.sql` 逐列（类型、可空、默认值、排序规则、索引）**结构一致**；11 处注释不同（7 列、4 表，第一、二轮脚本沿用旧注释；不影响运行，见第 6 节遗留）。任务卡写的「21 张」把测试初始化 DDL 的 `t_consumed_event`（messaging 消费端表）算入；commercial 只发布不消费，canonical Schema 为 20 张，本发布核对值为 **20** |
| 构建 | worktree `/Users/kenny/Desktop/Workspace/Code-MoR-release` @ `cf39a1ee`；JDK 25 离线 `./mvnw -B -o -DskipTests -pl commercial/paytech-commercial-app,operations-console-bff/paytech-operations-console-bff-app -am clean package` BUILD SUCCESS（14 秒）；Web `npm ci && npm run build` 通过 |
| JAR 核对 | commercial exec JAR SHA-256 `fa656f403f8b39686e8b0dc17227e064d80527132c18deda38b8423385feec55`，内层含 `PricingProfileMapper`、`CommercialTermVersionActivationJobHandler`（Handler 名 `commercialTermVersionActivation`）、`TermVersionActivationValidator`、`CommercialTermApi`；BFF exec JAR `2578b29475bef1427dbf59d3e81b721a33ff5224e469518db112412f048fac73`，含 `CommercialAgreementController`、`CommercialPricingController` 与 `paytech-commercial-api`；与运行中 BFF 比较只增 66 个条目、无删除 |
| Web 核对 | 新入口 `assets/index-CZvT-J3j.js`；新增 `commercial`、`CommercialAgreements`、`CommercialPricingProfiles`、`CommercialTerms`、`TermModals`、`DatePicker` 分块；运行版有而新版无的只有 `SetupBooksTab` CSS 分块（DatePicker 样式被抽成共享分块） |
| 镜像（tag `commercial-20261020`） | commercial-service `sha256:88dbf74155a9ceb5c82f01376b1bb3ddec7a9fe7ba9077bf42b50b7da04cd71d`；operations-console-bff `sha256:2bd2afee26092ff41ad69b19af40c04c97a053921c1eb5b61cde13fcdc1706aa`；operations-console-web `sha256:edf140b9e25b1a682e9b60fd8954fa36b4906334d26fd924a66ba2e5a5882bc2`。用 `docker build` 显式 tag 构建（构建上下文为会话临时目录：exec JAR + 由 `test-environment/java/*.java` 编译的健康探针），**未**复制到 `test-environment/artifacts/`、**未**执行 `compose.sh build`，因而不改写共享的 `:development` tag 与 artifacts（偏离任务卡第 2 步的做法，结果等价且更隔离） |
| 候选 Compose | [compose-candidate.yaml](./compose-candidate.yaml) `compose.sh -f … config --quiet` 通过；渲染后三服务为上列 ID，commercial 含 19 个新键与 rabbitmq / redis `service_healthy` |

## 3. 执行顺序

1. 构建三个镜像（基线提交号），记录 sha256。
2. 停 commercial-service；执行 ddl.sql → dml.sql；核对 21 张表与目录行数（7 / 11 / 21）。
3. 执行 registration-dml.sql；核对块 95 行数。
4. 改 compose（环境变量、depends_on），`compose.sh` 只 up commercial-service、operations-console-bff、ops-web。
5. 健康检查 readiness；XXL Admin 登记执行器与任务。
6. 验收：运营入口登录 → 合同 / 画像 / 价目页面走查；商务接口冒烟（建合同 → 订阅 → 开通 → 建 Plan / Version / Price → 排期激活 → 命中）。
7. 通知 code-mor-9c：镜像 sha256、基线提交号、ops-bff / Web 改动文件列表。

## 4. 回退

镜像回退到记录的旧 ID；mor_commercial 按 ddl.sql 注释逆序回退（全空表，可整段重建旧形状）；mor_platform 删除块 95 行；compose 还原副本。

## 6. 执行记录（阶段 7，任务卡 G agent；时间 UTC）

凭据只从 `test-environment/.env.test` 读入环境变量，不打印。

| 步骤 | 结果 |
|---|---|
| 阶段 6 | 2026-10-19 约 21:15—21:31Z：第 2 节核对、构建、DDL 演练；未改 mor-test 任何容器、配置或数据 |
| 1 备份（21:32Z） | `/Users/kenny/Code-MoR-backups/20261019T213228Z-commercial-test/`（0700 / 0600，SHA256SUMS）：`mor_commercial` 整库转储（14 表）、`mor_platform` 注册相关 8 表、`compose.applications.yaml` 副本、三容器 `docker inspect` 与镜像 ID（`images-before.txt`）、容器清单、`compose-rollback.yaml`（旧镜像 ID） |
| 2 停服务与 DDL / DML（21:32—21:34Z） | 复核 14 表 0 行；`compose.sh stop commercial-service`（21:32:53Z，Exited 143）。root 执行 `db/commercial/ddl.sql`（21:32:59Z，rc 0，无错误）→ **20** 张表；`commercial_app` 执行 `dml.sql` → `t_product` **7** / `t_product_capability` **11** / `t_product_fee_event` **21**；mor-test 结构与 `commercial-schema.sql` 逐列一致。输出：备份目录 `sql-2.1-commercial-ddl.out`、`sql-2.2-commercial-dml.out` |
| 3 Platform 块 95（21:33—21:34Z） | `platform_app` 事务内试跑 + ROLLBACK：5 / 5 / 8 / 24 / 128 / 5 / 144，回滚后 0；21:34:04Z 执行 canonical 块（与阅读副本逐字一致）并 COMMIT：5 / 5 / 8 / 24 / 128 / 5 / 144，路由 24 DISABLED、资源 8 DISABLED、授权 5 REVOKED。输出：`sql-3.1-block95-trial.out`、`sql-3.2-block95-commit.out` |
| 4 替换（21:34—21:35Z） | commercial 21:34:11Z up → 21:34:24Z healthy（`sha256:88dbf741…`，容器内 JAR `fa656f40…`），启动日志 ERROR 0；RabbitMQ `rabbitmq:5672` 连接建立、Redisson 连接 `redis:6379`、Nacos 注册 `172.30.73.34:18108`；XXL 注册 Handler `commercialTermVersionActivation` 与 `compensationEventScan`，21:34:30 心跳 `http://172.30.73.34:9999/`。ops-bff 21:34:46Z → 21:35:05Z healthy（`sha256:2bd2afee…`，JAR `2578b294…`），ERROR 0，ledger / account 直连变量保留。ops-web 21:35:11Z → 21:35:23Z healthy（`sha256:edf140b9…`），入口 `index.html` 引用 `assets/index-CZvT-J3j.js`，`/commercial/agreements`、`/commercial/pricing-profiles`、`/commercial/terms` 与 `CommercialTerms` 分块均 200。各重启 0 |
| 5 XXL（21:35—21:38Z） | Admin API（脚本 `register_xxl_executors.py` 的 `Admin` 客户端，口令读 `.env.test`）新建分组 **14** `commercial-service`（Commercial，自动注册，地址 `http://172.30.73.34:9999/`）与任务 **16** `commercialTermVersionActivation`（CRON `0 */5 * * * ?`、FIRST、SERIAL_EXECUTION、超时 300、重试 0、DO_NOTHING），已启动。插入后 `xxl-job-admin` 于 21:36:01Z 重启一次（OOMKilled=false，容器累计重启 17 次，非本线所为，前序 billing 发布亦记录其内存接近上限），重启后 healthy；21:38:30 手动触发（日志 36413）trigger 200 / handle 200。未登记 `compensationEventScan`（任务卡范围外，见遗留） |
| 6 启用（21:38—21:39Z） | [test-activate.sql](./test-activate.sql) 事务内试跑 ROLLBACK：24 / 8 / 5 / 1，回滚后角色版本仍 31、ACTIVE 路由 0；21:39:02Z 提交：**24 / 8 / 5 / 1**，PLATFORM_ADMIN 版本 31 → **32**，ACTIVE 授权 66 → 71。输出 `sql-6.1-activate-trial.out`、`sql-6.2-activate-commit.out` |
| 7 验收（21:41—21:49Z） | 接口 [stage7_acceptance.py](../verification/stage7_acceptance.py) 首轮 **46/47**：唯一失败为脚本调用 `GET versions/{id}/prices` 漏传必填查询参数（400 `INVALID_REQUEST`，属正确校验）；修正脚本后用同一版本补测 200、USD 价格 2 条。合成审批人 B 经 IAM 接口新建，A 自批 400、B 批准 200；收尾已停用 B、清空合成角色。商务直连 `/commercial-prices/v1/resolutions` 200 命中具体画像，`/actuator/health` UP。页面只读回归 [stage7_regression.py](../verification/stage7_regression.py) **37/37**（含商务三页，截图 `assets/stage7/`）。自 21:34Z 起 commercial / ops-bff / ops-web / Gateway / Platform 日志 ERROR 0，无管理员口令或 Bearer Token；`t_compensation_event` 0 行（两类事件已发布）；XXL 任务 16 定时 21:40、21:45 均 200 / 200。详见 [stage7-acceptance.md](../verification/stage7-acceptance.md) |
| 8 清理（21:49Z） | [acceptance-cleanup-test.sql](../verification/acceptance-cleanup-test.sql)（`commercial_app`）：SELECT 核对全部符合期望后同一事务删除 26 / 7 / 0 / 12 / 4 / 1 / 1 / 2 / 1 / 1 / 1 / 2 / 0 / 1 / 3 / 2；`mor_commercial` 只剩目录 7 / 11 / 21，其余 17 表 0 行。Platform：合成审批人员已停用、合成角色已清空（IAM 接口），审计不回滚。输出 `sql-8-acceptance-cleanup.out` |
| 9 收尾（21:50Z） | 构建 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-release` 已删除；会话临时构建上下文已删除；`test-environment/artifacts/` 与 `compose.applications.yaml` 自始未改动（无需恢复）。镜像 tag `mor-test/<服务>:commercial-20261020` 保留供固定 ID 使用 |

**当前 mor-test：** commercial `sha256:88dbf741…`、ops-bff `sha256:2bd2afee…`、ops-web `sha256:edf140b9…`（tag `commercial-20261020`），30 个容器 healthy；`mor_commercial` 20 表（仅目录数据）；块 95 已启用（24 路由、8 资源、5 授权，PLATFORM_ADMIN 版本 32）；XXL 分组 14 / 任务 16 运行中。回退按第 4 节，旧镜像 ID 在备份目录 `compose-rollback.yaml`。

**偏差：** ① Snowflake Worker 17（草案 26 已被 channel-connector 占用）；② 表数核对 20（任务卡「21」含测试专用 `t_consumed_event`）；③ 镜像以 `docker build` 显式 tag 构建，未经 `test-environment/artifacts` 与 `compose.sh build`，不改写共享 `:development` tag；④ 第二审批人用 IAM 接口新建的合成人员，未在 test-activate.sql 中授权。

**遗留（交主会话）：** ① `compose.applications.yaml` 的 commercial-service 段仍是旧形状，新环境变量与 depends_on 只在 [compose-candidate.yaml](./compose-candidate.yaml)；以后用 `deploy.sh` 或不带覆盖文件的 `compose.sh up` 会让 commercial 缺必填的 Snowflake 变量而启动失败，需择机把该段写回（其他会话同晚在改此文件）。② mor-test 有 11 处表 / 列注释沿用第一、二轮旧文字（结构与 canonical 一致），需另行授权的仅注释 ALTER。③ ~~未登记 commercial 的 `compensationEventScan`~~ 已按主会话指示补登（见下方补充记录）。④ 通知 code-mor-9c：ops-bff / Web 新基线为上列两个镜像（main `cf39a1ee`）。⑤ `xxl-job-admin` 在本次登记后约 30 秒重启一次（累计 17 次，非 OOM），属既有不稳定。

**补充记录（21:51Z，主会话指示，只动 XXL Admin）：** Admin API 在分组 **14** `commercial-service` 新建任务 **17** `compensationEventScan`（「Commercial 本地补偿扫描（compensation_event）」），形状与 billing 任务 12、account 任务 13 相同：CRON `*/30 * * * * ?`、SHARDING_BROADCAST、SERIAL_EXECUTION、超时 60、重试 0、DO_NOTHING，已启动。21:51:40 手动触发（日志 36692）trigger 200 / handle 200，分片 0/1，执行器 `http://172.30.73.34:9999/`；随后定时调度（日志 36703）亦 200 / 200。
