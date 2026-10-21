# reconciliation 渠道资金对账切片 1：mor-test 发布清单

阶段 6（2026-10-21）。只做了只读核对、干净 worktree 构建镜像和发布包准备，**没有改 mor-test 的任何容器、数据、队列或 XXL 登记**；阶段 7 须用户另行授权后按本清单执行，结果写第 8 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。

**本次：mor-test 的 reconciliation、ledger、billing、operations-console-bff、operations-console-web、platform-support 六个容器换成「c6eea71a + 本需求 448 个文件」构建的镜像。`mor_reconciliation` 删除旧 18 张空表、建新 15 张表；`mor_billing.t_billing_fact_ref` 加两列；`mor_vault` 加 1 个数据类型、1 条 PROTECT 规则、1 条策略变更；`mor_platform` 注册并激活块 123（512 行）。reconciliation 的 Compose 段新增 RabbitMQ、Redis、补偿实例与 XXL 执行器变量，BFF 新增 `OPERATIONS_RECONCILIATION_BASE_URL`；XXL 新建 reconciliation 分组与两个任务。验收后清理合成数据。其余 24 个容器不动。**

> 与任务范围的出入：原计划四个镜像（reconciliation、billing、ops-bff、ops-web）。核对后加了两个：
> - **ledger（必须）**：ledger 读 billing `GET /billing/v1/facts/{id}` 用 Foundation 严格 JSON（`HttpServiceClientOptions.strict().withStrictJsonResponses(8192)`，`FAIL_ON_UNKNOWN_PROPERTIES`）。新 billing 的引用项多出 `externalReferenceType` / `externalTransactionId`（为 null 也输出），运行中的 ledger（旧 billing-api）会读事实失败，AccountPosted 消费与追平随之失败。ledger 必须**先于 billing** 换成同一源码构建的镜像（JAR 只差 billing-api 引用类、`BillingFactClientImpl.toRef` 两行透传与 events.yaml）。account 读 MQ 载荷用默认 Jackson 3 映射（实测容忍未知字段），不受影响。
> - **platform-support（建议）**：只为 `console-resource-capabilities.json` 加对账 7 页 + 2 个按钮（配置页新增绑定时按部署能力校验）。不换也能用 SQL 激活块 123，但配置页会把对账资源当作未部署组件。

## 1. 范围

| 项 | 内容 |
|---|---|
| 环境 | 本机 Docker Desktop 的 `mor-test`：30 个容器 healthy（2026-10-21 02:38Z）；运营入口 `http://localhost:28080`；库 `127.0.0.1:23306`；RabbitMQ `127.0.0.1:25672` / 管理 `25673`，vhost `/mor-test`；XXL Admin `http://127.0.0.1:28090`。不是生产 |
| 源码 | `c6eea71a`（商务会话 code-mor-98 集成 worktree `cp-int` 头 = main `4ab205e0` + commercial A/B/C + 两次缺陷修复）+ 本需求 448 个文件（[source-overlay.txt](./source-overlay.txt)：A 381 / M 60 / D 7；reconciliation 363、ops-bff 41、billing 17、ops-web 18、arch-rules 3、foundation 2、ledger 2、platform 2）。见第 2 节 D1 |
| 镜像（6 个） | reconciliation（切片 1 全部能力，含 D-1 集合化匹配）；ledger（billing-api 引用两列）；billing（引用两列读写、载荷带出）；ops-bff（38 个对账端点、两个聚合 Processor、`/finance/reconciliation/v1/cash-accounts` 只记元数据）；ops-web（对账 7 页）；platform（能力清单） |
| SQL（root） | `mor_billing` ← [db/billing/ddl.sql](../db/billing/ddl.sql)；`mor_reconciliation` ← [sql/01-reconciliation-drop-old-tables.sql](./sql/01-reconciliation-drop-old-tables.sql) + [db/reconciliation/ddl.sql](../db/reconciliation/ddl.sql)；`mor_vault` ← [db/vault/dml.sql](../db/vault/dml.sql)；`mor_platform` ← [db/platform-support/registration-dml.sql](../db/platform-support/registration-dml.sql) + [test-activate.sql](../db/platform-support/test-activate.sql)。应用账号只有本库 DML 权限，DDL 用 root |
| 不动 | Gateway、Nginx、account、commercial、channel-connector、Vault 容器、MySQL / Redis / RabbitMQ / Nacos / XXL 容器；`test-environment/compose*.yaml` 与脚本不改（发布用本目录覆盖文件）；健康探针白名单已含 `http://127.0.0.1:19115/actuator/health/readiness`，不需补丁 |

## 2. 制品与决定

| 服务 | 上一版（运行中，回滚目标） | 候选（tag `recon-slice1-20261021`，部署只用 ID） | 候选 JAR SHA-256 前缀 | 与运行版逐类比较 |
|---|---|---|---|---|
| reconciliation-service | `sha256:a3642134d364…`（2026-10-18 空壳，JAR `327b753762a906c8`） | `sha256:f181a04cad2db3fc41eaadca51d04c7a3ca586264d08c62bd9e7a264f7b4b353` | `c66e59bd935d64e5` | 删旧 `ChannelAccount*` 6 项，改 `application.yml`、持久化配置，新增业务类与 Redis / Rabbit / XXL / Nacos / Vault 依赖 |
| ledger-service | `sha256:ff20e3358a57…`（88551da9，JAR `162be07a93350d0b`） | `sha256:b84f8f97a1ec740fbd0b08bdb11463516acfde301f3afbd20dc32dbc3b10a294` | `b984c18bd2cd2d27` | 只差 6 项：billing-api 引用类 4、`BillingFactClientImpl`、`events.yaml` |
| billing-service | `sha256:998391567701…`（fin-20261020，JAR `6e86cb6f0ec25b43`） | `sha256:b773d2cdb7a8077ecac181619093d2466cd05172810ecc83060174349a42dc2f` | `5a70aaaaf0b9adb2` | 改 9 项（引用两列链路 + `events.yaml`）；另多 2 个 platform-support-api 类（5f12cf3e 已提交的 `IntegrationLookupApi`，billing 不调用） |
| operations-console-bff | `sha256:9708ccdb8fa7…`（商务 capability-20261021，JAR `7d88741e6a886734`） | `sha256:9520a54538277ce7f4b90bca1f61712bbbce3559601d20b17697e028709e8971` | `d3db3584e2113511` | 新增 168 项全部为对账类；改 14 项（`application.yml`、`FinanceRequestConfiguration`、计费事实模型引用两列、billing-api 引用类） |
| operations-console-web | `sha256:abab7475643d…`（商务 capability-20261021，入口 `index-DFtXS91w.js`） | `sha256:edfb66284e2507727b4fe8707653ae71456c3dae2ec054e543b140985aea4007` | 入口 `assets/index-CanEcPBr.js` | **基线证明**：同一流程只用 `c6eea71a` 构建的 `dist/` 与运行容器 143 个文件逐字节一致；候选多 14 个对账资源（7 页、ImportDrawer、recon css / 模型、useRecon*），其余文件名因 Vite 内容哈希级联变化；`nginx.conf` 一致 |
| platform-support-service | `sha256:95348566542d…`（商务 capability-20261021，JAR `37ca10630c770f78`） | `sha256:79e33d43a2c3d4e64ac8e2a95109f46b86b8a1beeba6ba987e5d2f9b8f7de3b1` | `51c153676e54da98` | 只差 2 项：`console-resource-capabilities.json`、`events.yaml` |

构建：`git worktree add --detach … c6eea71a` 于会话 scratchpad，按 source-overlay.txt 复制本需求文件；`./mvnw -B -o -DskipTests -pl <五个 app> -am clean package`（JDK 25）；`npm ci && npm run build`；Java 镜像用 `test-environment/java/Dockerfile`，`health-probe/` 从运行容器 `docker cp`（5 个服务逐字节一致，摘要 `21251080f6292cc0`）；Web 用 `operations-console-web/Dockerfile`。镜像内 JAR 与构建产物 SHA-256 一致；未改 `:development` tag 与 `test-environment/artifacts`。模块测试未在 worktree 重跑（需要共享本机库）：阶段 4/5 证据为 reconciliation 93/93、billing 81/81、ledger 319/319、BFF 1939、Web 1194（[delivery.md](../delivery.md)）。

**D1 源码基线取 `c6eea71a` 而不是 HEAD `4ab205e0`。** 商务会话的改动（301 个文件）还没提交，但已发布在 mor-test 的 ops-bff / ops-web / platform 上（由 `6570ef73` 构建；`6570ef73..c6eea71a` 只改 commercial）。若以 HEAD 为基线，商务的 290 个文件会被回退。核对：主工作区这 301 个文件中 290 个与 `c6eea71a` 逐字节相同，其余 11 个正是 delivery.md 列出的共享文件，相对 `c6eea71a` 的差异只含本需求的对账 hunk（逐文件人工核对）。所以「`c6eea71a` + 主工作区本需求文件（含这 11 个共享文件的整文件）」= 商务已发布内容 + 本需求，ops-web 基线逐字节证明成立。主工作区里其他会话的未提交文件（agent-docs、design-discuss、`paytech/commercial/technique.md`、`operations-console-web/docs/design.pen` 等）不在叠加清单，也不进镜像。

**D2 platform-support-initial-data.sql 缺一行 `COMMIT;`（只影响文档文件，不影响本次执行）。** 三方合并后块 123 末尾的 `COMMIT;` 被块 124 的注释顶掉（第 5271/5272 行之间）。MySQL 遇到下一个 `START TRANSACTION` 会隐式提交，所以整文件执行结果不变，但块 123 不再自包含。mor-test 执行用阅读副本 `registration-dml.sql`（带 `COMMIT;`，与主文件块 123 的 INSERT 逐行一致）。阶段 8 提交时补回。

**D3 Snowflake 与 IP。** reconciliation 沿用主文件的 Worker 19、IP 172.30.73.41。运行容器的 Worker 为 17–31，各不相同，19 只属 reconciliation。XXL 执行器 IP 同为 172.30.73.41:9999。

**D4 内存档位不变。** reconciliation 仍是 small（堆 160m、容器 448m）。阶段 5 的 2 万行性能是在 512m 堆下测的，mor-test 验收只用几行数据。如果以后要在 mor-test 测大账单，需要另外把档位调到 medium。

## 3. 只读核对结果（2026-10-21 02:31–02:45Z）

| 项 | 结果 |
|---|---|
| 运行镜像 | reconciliation `a3642134…`（10-20 03:11Z 起，重启 0）、billing `998391567701…`（06:10Z）、ledger `ff20e3358a57…`（16:41Z）、ops-bff `9708ccdb…`、ops-web `abab7475…`、platform `95348566…`（21:01–21:02Z，商务会话），与 delivery.md 记录的上一版一致 |
| 运行 JAR 内容 | `docker cp` 后逐类比较，结果见第 2 节，没有发现回退 |
| 渲染后环境变量 | 用候选覆盖文件渲染，逐键比较运行容器（只比较键名，不输出值）：billing / ledger / web / platform **完全一致**；reconciliation 只新增 18 个键（RabbitMQ 5、Redis 3、补偿实例 / 实例 ID 2、XXL 8）；BFF 只新增 `OPERATIONS_RECONCILIATION_BASE_URL`。`compose.sh -f <候选 / 回滚> config --quiet` 校验通过 |
| `mor_reconciliation` | 18 张旧表，`COUNT(*)` 全部为 0；无 `t_clearing_account`（[sql/00-precheck-20261021.out](./sql/00-precheck-20261021.out)）；`reconciliation_app` 只有本库 `SELECT, INSERT, UPDATE, DELETE` |
| `mor_billing` | `t_billing_fact_ref` 5 列、0 行，没有两列新列；`t_billing_fact` 10 行、`t_billing_fact_line` 18 行（其他需求的人工事实，不受影响） |
| `mor_platform` 块 123 | 七张表 0 行（含按 code / 路径模式查）；对账外部路径 0 条；财务菜单 `900000000000097301` ACTIVE；Target `900000000000000001` 存在；PLATFORM_ADMIN 版本 **37**（激活后应为 38）；块 124 路由 17 条 ACTIVE（商务） |
| `mor_vault` | ID `9100000000000730` 在三张表都没有；没有 `CASH_ACCOUNT_NUMBER_V1`，也没有 reconciliation-service 规则；策略版本最大 17（执行后 18）。mor-test Vault 只从数据库读策略（test-environment/README.md），`test-environment/vault/policy.json` 只是转写来源 |
| RabbitMQ `/mor-test` | `billing.events`（fanout）只绑 `account.billing.hash`；还没有 `reconciliation.*` 交换机和队列（新镜像启动时声明）；用户 `mor_test` 的 configure / write / read 权限都是 `.*` |
| XXL | 分组 14 个（id 2–16），没有 reconciliation；任务 2–19；执行器心跳 13 个；`xxl-job-admin` 约 02:17Z 重启过（不是本线所为）；`scripts/xxl_reconciliation.py plan` 返回 `MISSING` |
| 网络 | backend 172.30.73.41 = reconciliation；vault（.24）、redis（.3）、rabbitmq（.4）、nacos（.5）、xxl-job-admin（.6）都在同一网络，Compose 服务名可以解析 |
| mor-test 渠道 | `mor_channel_connector` 只有他人建的 DRAFT 渠道 `AAAA`，没有 MID；验收会新建合成渠道（默认编码 `PAYERMAX`，mor-test Platform 已登记这个接入方，channel-connector 阶段 7 用过），验收后删除 |

## 4. SQL 清单

| 步 | 目标 | 文件 | 前置核对 | 预期 | 回滚 |
|---|---|---|---|---|---|
| SQL-1 | `mor_billing` | [db/billing/ddl.sql](../db/billing/ddl.sql) | 两列不存在（00-precheck） | 引用表 7 列，两列可空；不可重跑 | [sql/rollback.sql](./sql/rollback.sql) R-billing（先核对两列全为空） |
| SQL-2a | `mor_reconciliation` | [sql/01-reconciliation-drop-old-tables.sql](./sql/01-reconciliation-drop-old-tables.sql) | 脚本内先断言 18 张表合计 0 行，非空就 SIGNAL 中止 | 剩 0 张表；库和授权保留 | 备份 `mor_reconciliation.sql` 整库恢复 |
| SQL-2b | `mor_reconciliation` | [db/reconciliation/ddl.sql](../db/reconciliation/ddl.sql) | 库内 0 张表 | 15 张表 | R-reconciliation |
| SQL-3 | `mor_vault` | [db/vault/dml.sql](../db/vault/dml.sql)（文件自带 `USE` 和事务） | ID 和编码都不存在 | INSERT 3 行；策略版本 17 → 18；Vault 按刷新间隔重载 | R-vault |
| SQL-4 | `mor_platform` | [db/platform-support/registration-dml.sql](../db/platform-support/registration-dml.sql)（自带事务，休眠） | 块 123 七张表 0 行 | 3 / 3 / 10 / 38 / 160 / 70 / 228 = 512 行 | 休眠行可以留着；要删就按 R-platform |
| SQL-5 | `mor_platform` | [db/platform-support/test-activate.sql](../db/platform-support/test-activate.sql) | SQL-4 已提交；BFF / Web / reconciliation 已经 healthy | 38 / 10 / 3 / 1；PLATFORM_ADMIN 37 → 38 | [test-deactivate.sql](../db/platform-support/test-deactivate.sql)（38 / 10 / 3 / 1） |
| 核对 | 全部 | [sql/99-postcheck.sql](./sql/99-postcheck.sql) | — | 每个键的期望值写在键名里 | — |

执行包装：[scripts/apply_sql.sh](./scripts/apply_sql.sh) `<备份目录> billing|recon-drop|recon-ddl|vault|platform-reg|platform-act|platform-deact|postcheck`，root 口令从 `.env.test` 读，经 `MYSQL_PWD` 传，不回显；输出写到备份目录的 `sql-<步>.out`。

## 5. 执行步骤（阶段 7，主会话执行）

按 [mor-test-deploy-lessons]：停容器、起容器、root SQL、XXL 写入都由主会话执行。子 Agent 只做备份、只读核对和记录。命令中的 `$O` 指本目录 `compose-candidate.yaml` 的绝对路径，`$B` 指备份目录。

0. **协调**：先 SendMessage 给商务会话 **code-mor-98**，本次会换掉 ops-bff、ops-web、platform（它们的 capability-20261021 是上一版），确认对方当前不在部署、主工作区没有新的未发布改动，约定一个窗口；完成后再通知对方新的 ID。如果对方在这期间又发布了 ops-bff / ops-web / platform，本包作废，要在对方新的基线上重建。
1. **复核基线**：`docker inspect` 六个容器的 `.Image`，必须等于 [compose-rollback.yaml](./compose-rollback.yaml)；30 个容器 healthy；`sql/00-precheck.sql` 结果和第 3 节一致。有任何不同就停下。
2. **备份**：`B=$(release/scripts/backup.sh)` → `~/Code-MoR-backups/<UTC>-recon-slice1-mor-test/`（0700 / 0600，SHA256SUMS）：`mor_reconciliation`、`mor_billing` 整库，`mor_platform` 注册 8 表，`mor_vault` 策略 3 表，运行镜像 ID、运行 JAR SHA-256、两个覆盖文件、precheck 输出。
3. **ledger → billing**（顺序不能反，见开头说明）：
   `apply_sql.sh $B billing`（SQL-1 是在线 ALTER，旧 billing 用显式列名，不受影响）→
   `./test-environment/compose.sh -f $O up -d --no-deps --no-build --pull never --wait --wait-timeout 180 ledger-service` →
   同一命令换 `billing-service`。每次换完都要核对：healthy、重启 0、启动日志 ERROR 0、`.Image` = 候选；ledger 与 billing 的执行器约 30 秒后重新注册。
4. **reconciliation**：`./test-environment/compose.sh stop reconciliation-service` → `apply_sql.sh $B recon-drop` → `apply_sql.sh $B recon-ddl` → `apply_sql.sh $B vault` → `up … reconciliation-service`。核对：healthy；日志里出现 RabbitMQ 连接、`reconciliation.billing.p0..p3` 消费者 4 个、XXL 执行器启动；RabbitMQ 出现 `reconciliation.billing.hash`（已绑定 `billing.events`）、`reconciliation.billing.p0..p3`、`reconciliation.billing.dlq`、`reconciliation.events`；Nacos 出现 reconciliation-service 实例。
5. **Platform 注册**：`apply_sql.sh $B platform-reg`（休眠，结果是 512 行）。
6. **platform → ops-bff → ops-web**：依次 `up … platform-support-service`、`operations-console-bff`、`operations-console-web`，每次都做第 3 步那组核对。
7. **激活**：`apply_sql.sh $B platform-act`（38 / 10 / 3 / 1），然后 `apply_sql.sh $B postcheck`，全部等于期望值。Gateway Snapshot 刷新后（≤ 60 秒）再做冒烟。
8. **冒烟（管理员）**：`GET /api/operations/authorization/v1/navigation` 里有 `OPERATIONS_FINANCE_RECON` 与 7 个页面；`GET /api/operations/finance/reconciliation/v1/clearing-accounts?page=1&size=10`、`/summary?entityNo=AE001&currency=USD`、`/recon-exceptions?page=1&size=10` 都返回 200 且为空；浏览器打开财务 → 对账，7 页都能进，空列表有说明文字。**回归**：商务 8 个菜单页、财务原有 7 页、渠道 3 页各打开一次（换了 ops-bff / ops-web / platform，要证明商务和财务页面没有被回退）。
9. **XXL**：`python3 -I release/scripts/xxl_reconciliation.py apply`，然后对两个任务各 `trigger` 一次（[xxl-job-reconciliation.md](./xxl-job-reconciliation.md) 第 4 节）。
10. **验收**：见第 7 节。
11. **记录**：把第 8 节填完，`delivery.md` 补上环境操作记录，再通知 code-mor-98 新的镜像 ID。

## 6. 回滚

| 范围 | 做法 |
|---|---|
| 激活 | `apply_sql.sh $B platform-deact`（38 / 10 / 3 / 1）；菜单和路由立即失效，数据不受影响 |
| ops-web / ops-bff / platform | `compose.sh -f <compose-rollback.yaml> up … <service>`，回到商务 capability-20261021 的镜像（先停用激活，避免出现能看到入口但调用失败的状态） |
| reconciliation | 先 `xxl_reconciliation.py remove`，再用回滚文件 up，回到旧空壳；旧镜像只认旧表，所以还要做 R-reconciliation（删新 15 张表、从备份恢复旧 18 张）。RabbitMQ 里留下的 `reconciliation.*` 交换机和队列，要在管理页面里先解除 `reconciliation.billing.hash` 与 `billing.events` 的绑定再删除，否则 billing 的事实会在没人消费的队列里堆积 |
| ledger + billing | 一起回滚，**先 billing 后 ledger**（旧 ledger 不能读新 billing 的响应），然后做 R-billing（两列在 billing 需求 B 之前全为空） |
| Vault | R-vault：只在 reconciliation 已回滚、没有 `CASH_ACCOUNT_NUMBER_V1` 记录时删除那 3 行；有记录就保留类型、只停用规则 |
| 数据 | 验收数据由 `stage7_cleanup.py` 清除；结构回滚以备份目录为准 |

## 7. mor-test 验收（AC-16，另含 AC-02–AC-14 主要项）

脚本在 [acceptance/](./acceptance/)，由阶段 5 的 `verification/s1_flow.py` 改写到 mor-test：

- `stage7_acceptance.py`：管理员（`private/access.md`）经 IAM 新建两名合成人员：MAKER（对账读 + 操作 + 财务读）和 CHECKER（对账读 + 审批 + 财务读）。管理员经 channel-admin 建合成渠道（默认 `PAYERMAX`，可用 `RECON7_CHANNEL_CODE` 改）、AE001 技术账号（随机合成参数，走 mor-test Vault 真实 Protect）、ACTIVE MID 和 USD 能力组。流程是：
  银行账户 Maker / Checker（AC-03、AC-14）→ 清算账户与挂 MID（AC-02）→ 合成事实 → 平台流水（AC-04，含重复、其他类型、缺引用进 DLQ）→ S1 账单（AC-05/06/08/11/12）→ 银行对账单与手工匹配（AC-09/10/11）→ AC-13。
  - 合成事实中 PAYMENT 的预估渠道成本是 0.40，账单实际是 0.45，所以会发 `ActualChannelCostConfirmed`。
  - AC-13 用一个等待 0 天的清算账户，导入 1 行没有对应流水的账单，经 XXL 手动触发 `channelLineWaitExpiry` 两次，异常只开 1 条。
  - 最后核对四类事件都出现在 MQ。
- `ReconBillingFactPublisher.java`：用正式发布器编码，只投到 `reconciliation.billing.hash`，不经 `billing.events`，所以 mor-test 的 account 收不到合成事实。脚本会核对 `account.billing.dlq` 仍为 0。
- 观察队列 `recon7.probe.reconciliation-events`（非持久，2 小时无人使用自动过期）绑定 `reconciliation.events`。
- 运行记录写 `~/Code-MoR-backups/recon-slice1-stage7/run-*.json`（0600，含合成人员口令）。
- `stage7_cleanup.py <run.json> [--dry-run]`：先核对 `mor_reconciliation` 里所有行都属于本次运行，有外来行就中止；然后清空 15 张表，按渠道 ID 删 channel-connector 的合成数据，删除 Platform 合成人员和角色（审计记录保留），`reconciliation.billing.dlq` 只含本次缺引用事实时才清空，删除观察队列。Vault 记录按先例只计数、不删。
- 期望：全部检查通过；清理后 `mor_reconciliation` 15 张表都是 0 行。四类事件在观察队列删除之后再产生的话，会因为没有订阅方发布失败（I-08，留存 FAILED），所以验收结束后不要再操作对账页面。
- 内部接口（`/reconciliation/v1/cash-accounts/current`、`/clearing-accounts/resolutions`）在 mor-test 宿主机访问不到，本次不验。阶段 5 本机已覆盖。

## 8. 执行记录（阶段 7 填写）

| 时间（UTC） | 步骤 | 结果 |
|---|---|---|
| 03:0x | 0 协调 | code-mor-98 回复可替换 platform / ops-bff / ops-web，其 ops-web 验收修改不必等；要求做商务不回退核对并回告镜像与基线 |
| 03:11 | 1 复核基线 | 六个容器 `.Image` 均等于 compose-rollback.yaml；mor-test 容器全部 healthy |
| 03:11:39 | 2 备份 | `~/Code-MoR-backups/20261021T031139Z-recon-slice1-mor-test/`（含 SHA256SUMS） |
| 03:1x | 3 billing ALTER → ledger → billing | `t_billing_fact_ref` 7 列；ledger `b84f8f97…`、billing `b773d2cd…` healthy、重启 0、启动 ERROR 0 |
| 03:12 | 4 reconciliation | stop → 旧 18 表全空后删除（tables_left 0）→ 15 表 → Vault 3 行（规则 ACTIVE）→ up `f181a04c…` healthy、ERROR 0；RabbitMQ `reconciliation.billing.p0..p3`（各 1 消费者）、`.hash`、`.dlx`、`.dlq`、`reconciliation.events` |
| 03:13 | 5 Platform 注册 | 块 123 休眠行：路由 38、资源 10、权限 3、授权 3（REVOKED） |
| 03:13 | 6 前核对 | 候选 platform 的 console-resource-capabilities.json 相比现网只多 7 个 `OPERATIONS_FINANCE_RECON_*`，原 40 键（含块 124 商务键）完全相同 |
| 03:13–03:14 | 6 platform → ops-bff → ops-web | `79e33d43…`、`9520a545…`、`edfb6628…` 均 healthy、重启 0、ERROR 0；已回告 code-mor-98 镜像与源码基线（c6eea71a + source-overlay.txt） |
| 03:14 | 7 激活 + postcheck | 38 / 10 / 3 / 1；postcheck 16 项全部等于期望（PLATFORM_ADMIN 版本 37 → 38） |
| 03:14:58–03:15:20 | 9 XXL | 分组 id 17（地址 `http://172.30.73.41:9999/`），任务 20 `compensationEventScan`、21 `channelLineWaitExpiry` 已启动；首次触发扫描 500（执行器地址尚未入组），03:15:20 重试 200 / 200；等待期满触发 200 / 200 |
| 03:15:36–03:17 | 10 验收 | `stage7_acceptance.py` **69/69 通过**（AC-02–AC-14、AC-16、F5；四类事件均在 MQ；合成事实未进入 account）；运行记录 `~/Code-MoR-backups/recon-slice1-stage7/run-20261021071536.json` |
| 03:2x | 8 浏览器冒烟与回归（只读） | 27/27：对账 7 页 + 商务 9 个路由、财务 7 页、渠道 3 页；控制台 0 错误、网络 0 个 4xx/5xx；截图 33 张见 [acceptance/stage7-browser.md](./acceptance/stage7-browser.md)。报告里"银行对账三个标签 0 行"为脚本计数时机问题：截图 r05-bank-statements 显示 2 条，银行流水标签默认只看未匹配 |
| 03:3x | 清理 | `stage7_cleanup.py`：`mor_reconciliation` 15 表回 0；channel-connector 合成渠道 / 账号 / MID / 能力删除；Platform 两名合成人员与两个角色删除（审计保留）；`reconciliation.billing.dlq` 清空 1 条；观察队列已删；Vault 记录按先例保留（CASH_ACCOUNT_NUMBER_V1 2、CHANNEL_ACCOUNT_CONFIG_V1 2）。已通知 code-mor-98 走查结束，可替换其 ops-web |

## 9. 风险与遗留

- 六个服务分 6 次重启，ops-bff / ops-web / platform 和商务会话共用，必须先协调（第 5 节第 0 步）。
- D-2（补偿超时只中断、不等待）没有修。mor-test 数据量小，不会触发。
- 四类事件没有订阅方（I-08），验收以外的事件会留存 FAILED，等 billing 需求 B 接入后补发。
- 阶段 8 提交前要补回 `platform-support-initial-data.sql` 块 123 的 `COMMIT;`（D2），共享文件要按 hunk 拆分提交。
