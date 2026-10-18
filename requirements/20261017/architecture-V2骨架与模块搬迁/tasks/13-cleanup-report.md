# 第 13 步报告 · 收口：旧根清理、文档链接重写、搬迁状态登记

- 分支：`refactor/step-13-cleanup`（起点 `refactor-main@8c5b4d50`，任务卡提交 `1e8b2058`）。
- 第一轮完成第 2 节第 1–4、6–9 项；第二轮在 `git merge refactor-main`（11b 已合入）后完成第 5 项，并重跑第 3 节校验（第 3 节为第二轮数字）。
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
| `e7abf824` | 第一轮报告 |
| （第二轮） | merge refactor-main、CURRENT_IMPLEMENTATION 新节、报告第 5 节 |

## 3. 第 3 节验证

各项"起点"均在起点提交 `1e8b2058` 的 `git archive` 完整导出上用同一命令运行；requirements-site 复用同一份 `node_modules`。

1. **链接**：
   - `node scripts/verify-markdown-links.mjs`：7605 条相对链接、898 个文件，**0 broken**，另列 2 条 local-only，退出码 0。local-only 是 git 忽略的本地材料（`test-environment/private/access.md`、`*.log` 证据），脚本把指向被忽略目标的链接单列、不计失败。起点为 538 broken。
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

第二轮（`git merge refactor-main`，含 11b 合入 `46defae8` 与登记 `6b8a6672`，无冲突）写入 [`agent-docs/CURRENT_IMPLEMENTATION.md`](../../../../agent-docs/CURRENT_IMPLEMENTATION.md#v2-skeleton-module-migration-20261018)，全文如下；链接已按本报告位置改写。

<a id="v2-skeleton-module-migration-20261018"></a>
## V2 骨架与模块搬迁（阶段 0，refactor-main，2026-10-17—18，未合入 main、未推送、未部署）

- **范围：** 按 [V2 决定草案](../../../../agent-docs/decisions/2026-10-17-target-architecture-v2.md) D09、D11、D22、D24–D26、D31–D36 与[需求](<../../../../requirements/20261017/architecture-V2骨架与模块搬迁/requirement.md>)，把 12 个旧工程根并入单一 Maven Reactor `paytech/`（唯一 Wrapper `paytech/mvnw`）。各步任务卡与报告见 [tasks/](<../../../../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/>)。本节只登记事实，规范正文仍以 `DOCUMENT_GOVERNANCE.md` 路由的 Owner 为准。
- **Implemented：**
  - **目录与模块：** `paytech/` 下有 24 个服务目录。
    - 18 个已建工程：platform-support、api-gateway、operations-console-bff、client-console-bff、vault、client-master、commercial、acquiring、consumer、payment、channel-connector、card、risk-decision、compliance、billing、account、ledger、data-service。
    - 另有 reconciliation、tax 两个空壳服务。
    - dispute、payout 只有模型；fx、workflow-approval 只有 README 占位。
    - 另有 `paytech/build`（BOM、父 POM）、`paytech/foundation`（17 个模块，含 `paytech-messaging(-api)`、`paytech-internal-auth`）与 `paytech/tools/{arch-rules,arch-rules-reactor-test,instance-manifest,migration-runner}`。
  - **模块坐标：** `com.paytech:paytech-<服务>-{api,core,app,test}`（Gateway 与两个 BFF 无 api / core）。服务名、业务端口与管理端口见 [LOCAL_DEVELOPMENT_ENVIRONMENT.md](../../../../agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md) 8.2 节；服务目录见 `paytech/tools/instance-manifest/src/main/resources/catalog/services.yaml`。
  - **库：** 每个服务独立库：`mor_client_master`、`mor_commercial`、`mor_acquiring`、`mor_consumer`、`mor_payment`、`mor_channel_connector`、`mor_card`、`mor_risk_decision`、`mor_compliance`、`mor_ledger`、`mor_billing`、`mor_account`、`mor_reconciliation`、`mor_tax`；`mor_platform`、`mor_vault` 不变。模型为 `paytech/<服务>/docs/database/<服务>.pdma.json`（18 份），R4 表归属由 `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` 强制（265 张物理表 + 56 张保留表 + 6 张 step-added）。
  - **过渡与清理：** 第 07 步 account 对 `mor_ledger` 的共库只读已于第 11b 步结束，`transitionalSharedReads` 已删除，account 改用自有 `mor_account`。第 05 步卡表暂居 `mor_payment` 的过渡已于第 12 步结束。旧根在 git 中为 0 个文件；文档链接已改写到新路径。
  - **消息：** `<producer>.events` Fanout 加订阅方一致性哈希分区队列（D34）。payment、compliance 只发新信封；acquiring、client-master、card、ledger、account 为消费者；billing 发布 `EconomicFactRecorded`。旧 `topic_*` / `cg_*` 不再由代码声明。
  - **test-environment：** Compose 已按上述服务拆分（18 个 Java 服务，每个服务一个 `JAR_FILE`）；`build.sh` 单 Reactor 打包；`initialize.py` 新增 V2 库建库与 Schema 动作。只改了脚本，mor-test 未按 V2 部署。
- **Verified：**
  - **全 Reactor：** refactor-main@6b8a6672（11b 合入后）执行 `cd paytech && ./mvnw -B clean verify`，BUILD SUCCESS，用时 19:49，2026-10-18 10:16 完成（主会话）。
    - 结果：42 个含测试模块、8218 个测试，0 失败、0 错误、0 跳过。
    - 运行环境含 Vault Go 服务端二进制与 `VAULT_TEST_*`、`PAYTECH_REDIS_PASSWORD`、`MOR_RABBITMQ_*`。
    - 历史全 Reactor：02x 后 4642，03 后 6011（主会话）。
  - **各步搬迁前后测试数**（执行数，均 0 失败、0 跳过）：

    | 步骤 | 搬迁前 | 搬迁后 |
    |---|---|---|
    | 01 Foundation | 722 | 893 |
    | 02a Platform | 759 | 798 |
    | 02b Gateway | 1277 | 1306 |
    | 02c BFF | 1386 / 76 | 1390 / 80 |
    | 02d Vault | Go 顶层用例 148，Java 53 | Go 177，Java 58 |
    | 03 Commerce | 808 | acquiring 801 + consumer 21 |
    | 04 CCT | 53 | client-master 57 + commercial 24 |
    | 05 Payment | 127 | payment 72 + channel-connector 75 |
    | 06 Risk | 2083 | risk-decision 1726 + compliance 374 |
    | 07 Finance | 242 | 316 |

  - **后续各步终值：** 08 起：acquiring 803、client-master 58；11a：billing 89；11b：ledger 204、account 40；12：card-test 38、card-core 11、payment 62；12x：payment 63。arch-rules 56、data-service-test 16、migration-runner 27。
  - **app 启动：** 各步 app 以 mock Profile 启动后，管理端口健康检查均为 UP。
  - **第 13 步：** `scripts/verify-markdown-links.mjs` 0 broken；`scripts/rewrite-moved-links.mjs --check` 0 待改；`verify-java-foundation.mjs` 通过。requirements-site 137 / 127 / 10、PDManer 557 / 520 / 37 均与起点失败集相同。test-environment Python 336 项、3 failures、37 errors，均为搬迁前即有的 Risk rollout 工具钉值漂移。
  - **本机库 DDL 应用情况**（`mor-local-mysql-1`，root 执行各服务测试 `init/ddl.sql` 或评审增量 SQL）：

    | 库 | 表数 | 步骤 |
    |---|---:|---|
    | `mor_acquiring` | 16 | 03、08 |
    | `mor_consumer` | 2 | 03 |
    | `mor_client_master` | 29 | 04、08 |
    | `mor_commercial` | 15 | 04 |
    | `mor_channel_connector` | 13 | 05 |
    | `mor_payment` | 15，另有卡 4 表残留 | 05 重建；12 为 `t_payment_instrument_token` 加 4 列 |
    | `mor_risk_decision` | 55 | 06 |
    | `mor_compliance` | 25 | 06 |
    | `mor_ledger` | 31 | 07 建库；11b 执行拆表与调整规则 SQL |
    | `mor_billing` | 9 | 07 建库；11a 执行 maker-checker 增量 SQL |
    | `mor_reconciliation` | 18 | 07 |
    | `mor_tax` | 24 | 07 |
    | `mor_account` | 8 | 11b |
    | `mor_card` | 8 | 12 |
    | `mor_platform` | 新增 `t_currency_spec` 及 USD 行 | 02a |

    旧库 `mor_cct`、`mor_commerce`、`mor_risk`、`mor_finance` 保留，供 main 分支使用。
- **Pending：**
  - **design/04 修订：** §3.2 与 §6 按 [11b 报告](<../../../../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/11b-ledger-account-split-report.md>)偏差 1–3 修订：余额身份带科目并由 account 自有主键；新增 `t_balance_applied_entry`；试算不再比对账户投影，改由日终核对与关账 consistency 承担。主会话已接受这三项偏差，规范正文未改。design/03 的表数与 `t_account` 归属同样按 11b 报告回写。
  - **DRAFT 事实驳回 / 撤回切片：** 第 11a 步用户决定在 11b 之后作为独立小切片。
  - **撤销 account_app 对 mor_ledger 的只读授权：** `REVOKE SELECT ON mor_ledger.* FROM 'account_app'@'%';` 待执行（本机及已建该授权的环境）。
  - **旧 RabbitMQ 拓扑删除：** 待用户执行，见下文本机一次性操作。
  - **Vault REQUIRED 切换：** Vault 与各服务 `internal-auth.mode` 目前均为 `OFF`。前提是 Platform 签发端上线、所有调用方接入 `InternalTokenSupplier`，并以 DUAL 观察一段时间；DUAL 期限未定。
  - **Platform 内部 Token 签发：** 改为表驱动，待 `t_jwt_signing_key` 增加 `key_usage` 的 Schema 评审；草案见 [02a 报告](<../../../../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/02a-platform-support-report.md>)第 4 节。当前为配置注入的 ES256（`STATIC`，prod 拒绝启动）。
  - **规范修订：**
    - `t_schema_migration` / 迁移执行器替代"人工执行当前 Schema"，涉及 `DATABASE_DESIGN_GUIDE.md`、`TECHNOLOGY_STACK.md`、`DEVELOPMENT_CONVENTIONS.md` 与 AGENTS.md 的 Manual infrastructure rule（第 10 步待确认第 4 项）。
    - D11（实例内迁移执行器）、D26（内部签名 Token）、D09（每服务独立进程）的规范正文修订。
    - `agent-docs` 现行正文替换为 V2 草案，作为后续需求。
    - `LOCAL_DEVELOPMENT_ENVIRONMENT.md` 第 11 节 V2 消息命名约定的正文修订。
  - **配置待改：**
    - billing 的 `messaging.publisher.unroutable-policy` 仍为 `ACK_AND_WARN`，订阅者已接入，应改回 `FAIL`。
    - `local-environment/compose.yaml` 的 `mysql.environment` 未透传 V2 库密码，全新卷初始化会因必需变量缺失而失败。
  - **mor-test V2 部署：** 未执行。需要以下各项：
    - 补 `.env.test` 键、建库与 Schema。
    - Platform `t_gateway_target` 改为 acquiring 并新增 consumer Target。
    - Vault 访问规则登记 `card-service` 与 `channel-connector-service`；XXL 登记 `acquiring-service` 执行器与 `cardOrphanInstrumentPurge`。
    - RabbitMQ 按插件挂载重建；Docker 内存约需 20 GiB 以上。
    - 启动顺序见[第 13 步报告](<../../../../requirements/20261017/architecture-V2骨架与模块搬迁/tasks/13-cleanup-report.md>)第 6 节。
  - **其他遗留：**
    - test-environment 中 2026-09-07 Risk Operations rollout 工具的钉值需按当前 Platform 网关目录重定（3 failures、37 errors）。
    - acquiring 的 `CheckoutSessionStatus.PREPARING` 读容忍与 Context Path `/commerce` 是否改名。
    - `t_consumer_acceptance.command_idempotency_id` 是否删除。
    - Gateway 边界脚本的 `.subscribe()` 与 90% 分支覆盖率门禁；Platform、acquiring 的 `coverage-gate` 仍低于 0.90。
- **本机一次性操作汇总（不在仓库中）：**
  - **已执行：**
    - 01：在线启用 `mor-local-rabbitmq-1` 的 `rabbitmq_consistent_hash_exchange`，未重启。
    - 02a：在 `mor_platform` 建 `t_currency_spec` 并写入 USD 行。
    - 03–12：按上表新建各 V2 测试库与账号（仓库默认密码），`.env.local` 未改。
    - 05：`mor_payment` 的 13 张旧表 `RENAME` 到 `mor_payment_legacy_20261017`；临时库 `mor_payment_step05_tmp` 已删除。
    - 09：`.env.local` 追加 3 个 CDC / Doris 键；创建 `cdc_reader`；`mor_finance.t_currency_spec` USD 行 `update_time` 被演示更新；Doris / Flink 镜像约 14 GB 保留。
    - 11b：清理 `mor_account` 残留数据。
    - 12x：清理 `mor_card` 消费登记残留各 3 行。
    - 宿主磁盘写满期间 Redis AOF 出现 `MISCONF`、Docker 停止响应，已由他人恢复。
  - **安全提示：** 第 05 步会话输出中曾显示本机 MySQL root 密码，第 04 步曾显示本地 Redis 测试密码；均未写入仓库，建议轮换。
  - **待用户执行：**
    - 撤销 account_app 对 mor_ledger 的只读授权（见 Pending）。
    - 确认无用后 `DROP DATABASE mor_payment_legacy_20261017`，并删除 `mor_payment` 中残留的卡 4 表。
    - main 停用后删除 `mor_cct`、`mor_commerce`、`mor_risk`、`mor_finance` 及对应账号。
    - 按需清理 `mor_risk` 中来源不明的 `t_decision_flow` 残留行。
    - 删除 `/mor-local` 上的旧 RabbitMQ 拓扑。先确认旧进程已停，并按需导出队列消息（另一会话的旧 CCT 进程曾消费 `cg_cct_client_review`）：

      ```bash
      for q in cg_commerce_payment_result cg_commerce_payment_result_dlq cg_acquiring_payment_result cg_acquiring_payment_result_dlq \
               cg_cct_client_review cg_cct_client_review_dlq cg_client_master_client_review cg_client_master_client_review_dlq; do
        docker exec mor-local-rabbitmq-1 rabbitmqctl delete_queue --vhost /mor-local "$q"
      done
      # 交换机（rabbitmqctl 无删除交换机子命令，用容器内的 rabbitmqadmin 或管理台）：
      # dlx_commerce_payment_result dlx_acquiring_payment_result dlx_cct_client_review dlx_client_master_client_review
      # topic_payment_result topic_risk_client_review
      ```

第二轮其他改动：
- 修 1 条因 11b 删除 `BalanceAccountMapper.xml` 而失效的链接，并同步映射表，改指 `AccountBalanceMapper.xml`。
- `UNIT_TEST_GUIDE.md` 7.3 的已接入模块补上 ledger、account。
- 核对 test-environment 中 ledger、account、billing 的 Compose 变量与合入后的 app 配置一致，无未绑定变量。

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
