# 第 13 步报告 · 收口：旧根清理、文档链接重写、搬迁状态登记

- 分支：`refactor/step-13-cleanup`（起点 `refactor-main@8c5b4d50`，任务卡提交 `1e8b2058`）。
- 本报告为**第一轮**：第 2 节第 1–4、6–9 项已完成并提交；第 5 项（`CURRENT_IMPLEMENTATION.md` 状态台账）按 team-lead 安排在 11b 合入、`git merge refactor-main` 之后的第二轮完成，届时补入第 5 节全文并重跑第 3 节链接校验。
- 全 Reactor `verify`（第 3 节第 5 项）由主会话执行。本步只改 `.gitignore`，未改 Java / POM；为核对 `build.sh` 的模块清单，离线执行过一次 `./mvnw -o -q -pl <18 个 app> -am validate`（退出码 0，不构建、不安装）。
- 没有执行 SQL，没有操作 Docker 容器或队列，没有部署；只读的 `docker compose config --no-interpolate --services` 用于校验 Compose 模型。

## 1. 改动摘要

1. **旧根**：12 个旧根在 git 与磁盘上均已不存在（`git ls-files` 0 行），无残留文件需要搬入或删除。根 `README.md` 新增「工程结构」一节，说明 `paytech/` 单 Reactor、唯一 Wrapper `paytech/mvnw` 与目录分工，并在「验证」中登记链接检查。
2. **链接工具**：新增 `scripts/markdown-links.mjs`（共享解析，跳过代码块与行内代码）、`scripts/verify-markdown-links.mjs`、`scripts/rewrite-moved-links.mjs`（`--check` / `--write`）与映射表 `scripts/moved-links-map.json`，另有 5 例解析测试 `scripts/markdown-links.test.mjs`。
3. **映射表**：由 `4ae80aca`（搬迁前）到起点的 git 改名记录压缩生成，共 1138 条：目录内全部文件同向搬迁的写目录条目，其余写文件条目；再补 78 条人工条目，覆盖拆分、删除与合并文件，以及搬迁前就已失效的目标。
4. **链接改写**：全仓改写 530 条，涉及 142 个文件，只改链接目标；另修 6 条与旧根无关、搬迁前就已失效的相对链接，并重写 3 处 `cd <旧根>` 命令文本。`agent-docs/` 只改链接目标与命令路径。
5. **PDManer**：`pdmaner-canonical-paths.test.mjs` 的合同清单早已是 17 个 Java 持库服务加 Vault（dispute、payout 以模块库登记）。本步新增闭合断言：清单等于 `paytech/*/docs/database/*.pdma.json` 的实际集合。`DATABASE_DESIGN_GUIDE.md` §4 的模型路径清单与 `database/README.md` 的模型入口列改为按服务的新路径。
6. **环境事实登记**：`LOCAL_DEVELOPMENT_ENVIRONMENT.md` 登记以下内容：
   - analytics Profile 及其端口 19030 / 18030 / 18091；
   - V2 服务业务端口与管理端口表（18101–18121、19xxx）；
   - design/10 §5.1 的 14 个库与账号；
   - RabbitMQ 一致性哈希插件与分区拓扑、MySQL binlog 参数与 `cdc_reader`；
   - `paytech.local-environment.dir` 用法与 `.env.example` 新键。

   `UNIT_TEST_GUIDE.md` 7.3 补一条 `PartitionQueueBaseline` 事实登记。两份文档都不改规则语义。
7. **从不执行的三个测试类**：均已在第 01、02b 步归位到 `auto/`，本步核对，无需改动。
8. **旧 Topic 残留**：代码与 Compose 中零引用，只剩 7 处历史注释与 README 说明（第 3 节第 7 项）。
9. **test-environment**：
   - `compose.applications.yaml` 按 V2 拆为 18 个 Java 服务，每个服务一个镜像与 `JAR_FILE`，健康检查走管理端口。
   - `compose.yaml` 向 MySQL 透传 14 个 V2 库密码与 `MOR_CDC_DB_PASSWORD`，`--max-connections` 调为 400。
   - `build.sh` 改为单 Reactor 一次 `clean package`；`deploy.sh` 第一阶段改为替换 risk-decision 与 compliance 两个服务；健康探针白名单同步。
   - `prepare.py` 对已有 `.env.test` 只追加缺失的 V2 键。
   - `initialize.py` 新增 `--create-service-databases` 与 `--apply-service-sql`，并把 Risk 目录核对更新为 54 表与当前初始化数据。
   - 测试同步更新，新增 `test_service_database_initialization.py`（8 例）。
10. **`.gitignore`**：PDManer 备份规则并入 `paytech/.gitignore`。compliance、risk-decision 两份只剩通用规则，已删除；vault 与两个 BFF 只保留服务特有规则。用 168 个探测路径核对，忽略结果与改动前完全一致。
11. **附带修复**：`verify-java-foundation.mjs` 只按主 jar 坐标核对 BOM 版本治理。原因是第 12x 步的 `paytech-messaging` test-jar 条目被误计为重复，导致脚本在起点上就失败。

## 2. 提交

| 提交 | 内容 |
|---|---|
| `cef30c7f` | 链接校验与改写工具、映射表、解析测试 |
| `4d3464bf` | 全仓链接改写（530 条），另修 6 条既有失效链接与 3 处 `cd` 命令文本 |
| `7ee0cb86` | 根 README：工程结构与链接检查 |
| `8f7d1075` | PDManer 规范路径闭合断言与两份模型路径表 |
| `317ddfcc` | LOCAL_DEVELOPMENT_ENVIRONMENT / UNIT_TEST_GUIDE 环境事实登记 |
| `abcf1d62` | `.gitignore` 收口 |
| `456656ae` | `verify-java-foundation.mjs` BOM 治理按主 jar 坐标核对 |
| `c46b460d` | test-environment：Compose、构建、部署、初始化脚本与测试 |
| （本报告） | `tasks/13-cleanup-report.md` |

## 3. 第 3 节验证

各项"起点"均在起点提交 `1e8b2058` 的 `git archive` 完整导出上用同一命令运行；requirements-site 复用同一份 `node_modules`。

1. **链接**：
   - `node scripts/verify-markdown-links.mjs`：7595 条相对链接、896 个文件，**0 broken**，另列 2 条 local-only，退出码 0。local-only 是 git 忽略的本地材料（`test-environment/private/access.md`、`*.log` 证据），脚本把指向被忽略目标的链接单列、不计失败。起点为 538 broken。
   - `node scripts/rewrite-moved-links.mjs --check`：`0 pending; 0 unmappable; 0 command texts`，退出码 0。
   - `node --test scripts/markdown-links.test.mjs`：5 / 5。
2. **requirements-site**：`npm --prefix requirements-site ci && npm --prefix requirements-site test`，137 项、127 通过、10 失败。起点同样是 137 / 127 / 10，失败测试名逐条相同（diff 为空），本步未引入新失败。10 项都是 site-content 对 agent-docs 正文与历史文案的断言，例如"Java tests are organized by executable entry…""the current ledger owns the Platform snapshot…"。
3. **Node 脚本**：

   | 命令 | 现在 | 起点 | 说明 |
   |---|---|---|---|
   | `node --test scripts/pdmaner/*.test.mjs` | 557 项，520 通过，37 失败 | 556 / 519 / 37 | 失败测试名集合相同；多出的 1 项是本步新增的闭合断言（通过） |
   | `node scripts/verify-local-environment.mjs` | 退出码 1，4 行 | 退出码 1，同样 4 行 | 4 行均为"active Gateway contract does not reference …"，要求 `JAVA_PROJECT_STRUCTURE.md`、`UNIT_TEST_GUIDE.md` 正文引用 Gateway 加固决定，属规范正文修改，本步不做 |
   | `node scripts/verify-java-foundation.mjs`（及 `--self-test`） | 退出码 0 | 退出码 1，22 行，均为 `paytech-messaging is missing from paytech-dependencies version governance` | 本步修复，见第 1 节第 11 项 |
   | `node scripts/verify-local-nexus-settings.mjs ~/.m2/settings.xml` | 退出码 0 | — | "Local Maven Settings routes dependency resolution and deployment through Nexus." |

4. **`cd test-environment/scripts && python3 -m unittest`**：**未全部通过**，结果为 336 项，3 failures、37 errors。逐项说明如下：
   - **起点**：327 项，3 failures、54 errors。
     - 51 个 error 来自 SQLite 不认识 `UTC_TIMESTAMP`，分布在 catalog 41 个、smoke-stage 10 个。
     - 3 个 error 来自 Risk 初始化器仍按拆分前的 71 表与 4 条 DML 核对。
     - 3 个 failure 来自 inventory 测试对 Compose 形态与审阅源摘要的过期钉值。
   - **本步修复**：
     - fixture 为 SQLite 注册固定值的 `UTC_TIMESTAMP`。这些时间列随后会被 fixture 剔除，不影响断言。
     - Risk 初始化器改为 54 表，并从全部初始化 DML 读出 2 个 Point、8 个 Action、4 个 Parameter。
     - inventory 测试的钉值改为 18 个 Java 服务；Dockerfile 集合加入 client-console-web；9 个审阅源重新取摘要，其中 8 个在本步之前就已漂移。
   - **仍失败的 3 failures + 37 errors**：全部在 `test_risk_rule_configuration_catalog`（27 项）与 `test_smoke_stage_contract`（10 项 error）中。
     - 原因：2026-09-07 的 Risk Operations rollout 工具钉住的 Platform 网关目录，与当前 Platform 初始化 DML 不一致。当前 DML 已有 `t_gateway_route.idempotency_mode` 列，且 Route 数量变了，于是报出 `Operations Route catalog is missing, unknown, or partial`、`no such column: idempotency_mode`。
     - 对照：在搬迁前的 `4ae80aca` 导出上只加同一个 SQLite 修复运行，结果同样是 3 failures + 37 errors，测试名逐条相同。可见这是既有漂移，不是搬迁引入。
     - 未修复的原因：要修就得按当前 Platform 目录重新定义该 rollout 工具的阶段分类与钉值，属于独立的业务工具修订，不在本步范围。
5. **全 Reactor**：由主会话执行。
6. **旧根**：`git ls-files | grep -E "^(paytech-build|…|finance-service)/"` 为空（0 行），磁盘上也不存在。

09 §8 的其余检查：
- (f) `](../…<旧根>` grep 结果为 0。
- (g) 代码与配置中的绝对路径：0 个文件（按 09 §8 的排除口径）。
- (c) `$workspace_dir/<旧根>` grep 结果为 0。
- (d) `scripts/` 下仍有 22 处 `<旧根>/docs/database` 文本：映射表的 `from` 键 18 处、生成器与测试中的历史说明注释 2 处、`client-contract-scenario-doc.test.mjs` 中旧路径到新路径的对照键 2 处，都不是读取路径。
- (e) 18 个 `.pdma.json` 全部位于 `paytech/<svc>/docs/database/`；仓库其他位置只剩 `agent-docs/templates/pdmaner/template.pdma.json`。

第 7 项（旧 Topic）：`grep -r "topic_payment_result\|topic_risk_client_review\|cg_commerce_payment_result\|cg_cct_client_review" paytech/ test-environment/ local-environment/` 共 7 行，全部是历史说明，test-environment 与 local-environment 为 0：
- `foundation/paytech-messaging/catalog/events.yaml` 2 行 YAML 注释；
- `compliance/README.md` 1 行；
- compliance 的 4 行 Javadoc / 注释（`ClientReviewDecidedEnvelopeChecker`、`ClientReviewDecisionApiAutoTest`、`ClientReviewDecidedEventPublisher`、`ClientReviewDecidedPayload`）。

改这些注释需要触碰业务模块源码，本步不改。

第 6 项（三个从不执行的测试类）：
- `PlatformSnapshotControlClientAutoTest`、`GatewaySnapshotCodeParserAutoTest` 位于 `paytech/api-gateway/paytech-api-gateway-test/.../auto/`。
- `RedisLocalEnvironmentPreconditionAutoTest` 位于 `paytech/foundation/paytech-redis-starter/.../auto/infrastructure/`。
- `paytech/**/src/test/java` 下 `auto/`、`manual/` 之外没有非抽象测试类。

## 4. 链接改写统计

| 类别 | 数量 | 说明 |
|---|---:|---|
| 入向（`paytech/` 外的文档指向旧根） | 526 | agent-docs 188、requirements 330、docs 6、AGENTS.md 1、test-environment 1；09 §6.1 用 `](../…/<旧根>` 文本 grep 统计为 520，本步按解析后的目标路径计数，两种口径不同 |
| 出向与旧根互链（`paytech/` 内的文档） | 4 | 09 §6.1 统计的 351 条出向链接已由第 01–12 步随搬迁改写（各步报告的 55 / 81 / 108 等），本步只剩 4 条：Foundation README 互链 1、test-support README 少一层 `../` 1、Payment `command-idempotency-differences.md` 互链 2 |
| 其中经人工条目改写 | 95 条链接 / 78 个目标 | 6 个目标在搬迁时被删除或拆分（4 份 README、Finance 模型与 Schema），其余 72 个在搬迁前就已不存在；按唯一同名文件或最近的现存目录改写 |
| 搬迁前已失效、与旧根无关的链接 | 6 | 文件改名 2、相对层级错误 4 |
| `cd <旧根>` 命令文本 | 3 处已改写 | `LOCAL_DEVELOPMENT_ENVIRONMENT.md` 的 Nexus 发布命令、`paytech/api-gateway/README.md`、Commerce 合同模块设计 spec |
| 按 09 §6.2 第 5 条保留 | 20 处 | 本需求自身的任务卡与报告中记录的旧根基线命令，在 `commandTextExcludes` 中登记；`docs/superpowers/plans/` 与 `assets/` 的命令文本不改写 |

09 §6.1 在设计时统计到 25 处 `cd <旧根>`。本步起点时，`--check` 在排除路径之外只报出上表 3 处，另有 20 处属保留范围。

## 5. CURRENT_IMPLEMENTATION 新节全文

第二轮补入（11b 合入并 `git merge refactor-main` 之后）。

## 6. 待确认项

1. **Python 套件的既有失败**（第 3 节第 4 项）：是否另立任务，按当前 Platform 网关目录重新定义 2026-09-07 Risk Operations rollout 工具的阶段分类与钉值？
2. **审阅源摘要重新钉值**：inventory 测试的 9 个摘要已改为当前内容，其中 8 个在本步之前就已漂移。这相当于对当前 test-environment 脚本的重新确认。
3. **`--create-service-databases` 与 `--apply-service-sql`**：两个动作都是 root 执行的显式操作员动作，不由部署脚本调用。
   - 建库账号授予最小权限，已有账号与密码不改。
   - 带初始化数据的库（client-master、acquiring、consumer、ledger、billing）在 PREPARED 状态下不会重放初始化数据。
   - 需要确认的是：是否沿用这一"只在空库上执行"的口径，等到第 10 步的迁移执行器接入后再统一。
4. **mor-test 的 `mor_payment`**：如果它保留旧 Payment 表，`--apply-service-sql` 会报"Unexpected/partial"并拒绝写入。处理方式需按第 05 步本机的做法（迁走旧表）另行授权。
5. **Snowflake worker 分配**：risk-decision 25、compliance 29、client-master 26、acquiring 28、consumer 27、payment 21、card 22、ledger 23、billing 30、account 31、reconciliation 19、tax 18。现有占用为 Platform 运营认证 20、Vault 24。5 位 worker（0–31）已用去 14 个，需要实例清单统一登记。
6. **deploy.sh 的覆盖范围**：deploy.sh 仍只编排 Operations 链路的两个阶段（第一阶段换成两个 Risk 服务）。其余 13 个业务服务的上线顺序见下文第 7 项，阶段 5 是否扩成独立阶段需要确认。
   - `build.sh` 现在会打包全部 18 个 Java 服务，Operations 部署的构建时间随之变长。
7. **阶段 5 全链路验证的 Compose 启动清单**（每次 `up --no-deps --wait`，按顺序）：
   1. 基础设施：`mysql redis rabbitmq nacos xxl-job-admin minio mailpit`。RabbitMQ 需要按 `enabled_plugins` 挂载重建一次，一致性哈希插件才生效。
   2. `vault-service`。
   3. `platform-support-service`。
   4. `client-master-service`、`commercial-service`、`compliance-service`、`risk-decision-service`。
   5. `consumer-service`、`channel-connector-service`、`card-service`、`payment-service`。
   6. `billing-service`、`ledger-service`、`account-service`、`reconciliation-service`、`tax-service`。
   7. `acquiring-service`。
   8. `operations-console-bff`、`client-console-bff`。
   9. `api-gateway`。
   10. `operations-console-web`、`client-console-web`。
   11. `nginx`。

   启动前先执行 `prepare.py`、`--create-service-databases`、`--apply-risk-sql`、`--apply-service-sql`。部署数据需要同步以下各项：
   - Platform `t_gateway_target`：Commerce Target 改为 `acquiring-service`，并新增 consumer Target。
   - Vault 访问规则：登记 `card-service` 与 `channel-connector-service`。
   - XXL Admin：登记 `acquiring-service` 执行器与 `cardOrphanInstrumentPurge`。
   - Platform 内部 Token 签发白名单：`internal-auth` 若非 OFF。
8. **阶段 5 资源估算**：
   - 内存：
     - 18 个 Java 服务按 `mem_limit 768m` 计，上限合计约 13.5 GiB；基础设施与 Vault、Web、Nginx 的上限另约 3 GiB。合计约 16.5 GiB，超过 Docker Desktop 的 15.84 GiB 上限。
     - 按 JVM 实际占用每个 400–500 MiB 估算，常驻约 8–9 GiB，加基础设施约 11 GiB。
     - mor-local 与 Nexus 已占约 11 GiB（第 09 步实测），因此需要先停 mor-local 应用进程，或把 Docker 内存调到 20 GiB 以上。analytics Profile 另需约 2.7 GiB。
   - MySQL：16 个持库 Java 服务的连接池合计约 160 个连接，已把 `--max-connections` 调为 400，重建 MySQL 容器后生效。
   - 磁盘：18 个镜像共用同一个 JRE 基础层，每个服务的增量只是其 exec JAR（估算每个约 60–120 MB，合计约 1.5 GB，未实测）。按 LOCAL_DEVELOPMENT_ENVIRONMENT 的 15 GiB 磁盘门禁执行；当前宿主可用约 44 GiB。
9. **Compose 中服务间 HTTP 依赖未写入 `depends_on`**：acquiring 调用 payment、consumer 与 commercial，ledger 与 account 互相调用并调用 billing，等等。这些依赖由上面的启动顺序保证；仍保持原有写法，只写基础设施依赖。
10. **local-environment 的 `compose.yaml`**（不在本步改动清单内，与 11b 的 `mysql/init` 相邻）：`mysql.environment` 仍未透传 `MOR_CHANNEL_CONNECTOR_DB_PASSWORD` 等 V2 变量。初始化脚本已把 client-master、commercial、channel-connector 列为必需变量，因此全新卷初始化会失败。test-environment 已在本步透传，local-environment 的这一处需要另行改。
11. **`PREPARING` 枚举值的读容忍**（第 03 步待确认第 11 项）与 acquiring 的 Context Path `/commerce` 是否改名：本步未处理，属于后续切片。
12. **本机 RabbitMQ 旧拓扑删除**：命令列入第二轮 CURRENT_IMPLEMENTATION 的"待用户执行"清单，本步不执行。
