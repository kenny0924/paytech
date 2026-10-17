# 11 · 阶段 4 研发计划

分支：`refactor-main` 集成；每步一个短分支 `refactor/step-NN-<name>`，完成后由主会话审查并合入 `refactor-main`，随后把 `main` 合入 `refactor-main`。每步由一个执行 agent（Opus 5.5）在独立 worktree 完成；主会话负责任务卡、合并前门禁与裁决，不亲自搬代码。

## 1. 步骤与依赖

| 步 | 分支 | 内容 | 前置 | 可并行 |
|---|---|---|---|---|
| 00 | `refactor/step-00-skeleton` | `paytech/` 聚合 POM、`build/` 搬入、`mvnw`、`tools/arch-rules`、`tools/instance-manifest`（Schema + 校验器）、空服务目录骨架、CI 脚本 | — | 否（起点） |
| 01 | `refactor/step-01-foundation` | 13 个 Foundation 模块搬入 `paytech/foundation/`；新增 `paytech-messaging(-api)`；内部签名 Token（`http-client-support` 出站拦截器、`mvc-web-starter` 入站校验、Gateway WebFlux 版）；`RiskOperationRequestContextParser` 下沉；修 `${maven.multiModuleProjectDirectory}`、`relativePath`、脚本（09 文档 a、c、d 中 Foundation 相关项） | 00 | 否 |
| 02a | `refactor/step-02a-platform` | platform-support 搬入并拆 api / core / app / test；解除 AMQP 禁令；接收 `t_currency_spec` 为平台参考数据并提供 `GET /platform/v1/currency-specs`（03 §3.1）；同包修复；覆盖率门禁改 Profile；`LocalMockEnvironmentInitializer` 仓库根标记修复 | 01 | 与 02b、02c、02d 并行 |
| 02b | `refactor/step-02b-gateway` | api-gateway 搬入拆模块；Enforcer 模式改真实 artifactId；边界与 JaCoCo 脚本路径修复；WebFlux 内部 Token 校验接入 | 01 | 并行 |
| 02c | `refactor/step-02c-bffs` | 两个 BFF 搬入拆模块；相邻目录定位测试修复；`1.0.0-SNAPSHOT` 改 BOM 管理 | 01 | 并行 |
| 02d | `refactor/step-02d-vault` | Go 服务目录与 Java Client 搬入；`verify.sh`、`vault.sh`、`build-vault.sh`、`VaultEndToEndTest` 相对路径修复；Go 服务端读内部 Token 的 caller | 01 | 并行 |
| 03 | `refactor/step-03-acquiring-consumer` | commerce → `acquiring` + `consumer`：两库、两 app；资格判断经 `consumer-api`；补偿表与清理拆分；死表死代码删除（C7）；Commerce 对 CCT / Payment 的服务名改为新名 | 02a（Platform api） | 与 04、06 并行 |
| 04 | `refactor/step-04-client-master-commercial` | CCT → `client-master` + `commercial`：两库、两 app；06 文档的候选接口（含法律文档、卖方披露与已存卡开关随候选返回）、两段式解析、查询参数化、人员共享接口；`t_client_stored_card_setting` 自 Commerce 迁入 client-master；固件拆分 | 02a | 并行 |
| 05 | `refactor/step-05-payment-channel` | payment → `payment` + `channel-connector`（卡域此步仍留在 payment 进程与库）：渠道结果映射迁入 channel-connector；能力代码单处解析；`PaymentSucceeded` 改用 messaging 信封并双写旧 Topic | 01 | 与 03、04、06 并行 |
| 06 | `refactor/step-06-risk` | risk → `risk-decision`（并入 control）+ `compliance`：两库两 app；`risk-api` 一次性拆为 `risk-decision-api` + `compliance-api`，同分支改五个消费方的坐标与包名（C6，唯一跨系统步）；结算控制 3 表归 compliance 并定义 compliance-api 合同（C3）；Precondition 与 MockTestBase 拆分、PDManer 拆分 | 03、04、05 | 否（跨系统，串行） |
| 07 | `refactor/step-07-finance-modules` | finance → `billing` / `account` / `ledger` / `reconciliation` / `tax` 五个 core 与 app；**此步 account 与 ledger 仍共用 `mor_ledger` 库**（原 `mor_finance` 改名；为第 11 步做准备），其余三库独立；billing 库内经济事实三表更名为 `t_economic_fact*`（04 §2）；表归属按 03 文档 | 01 | 并行 |
| 08 | `refactor/step-08-messaging-topology` | 一致性哈希交换机与分区队列声明、`AggregateSerialExecutor` 接入补偿 Dispatcher、消费幂等拦截器接入 Commerce / client-master 消费者；本地与测试 compose 启用插件 | 01、03、04、05 | 否 |
| 09 | `refactor/step-09-data-service-skeleton` | `local-environment` 加 Doris + Flink CDC 容器；`data-service` app 骨架；白名单表约定文件；一张表的 CDC 演示 | 00 | 与 02 并行 |
| 10 | `refactor/step-10-migration-runner` | `tools/migration-runner`：读版本化 SQL 目录、按实例清单执行、记录到 `t_schema_migration`；不接运维平面 | 00 | 与 02 并行 |
| 11a | `refactor/step-11a-billing-minimal` | 04 文档 §2：billing 最小能力——`MANUAL_ADJUSTMENT` / `COST_ADJUSTMENT` 经济事实登记与批准接口、`EconomicFactRecorded` 事件、billing 不变量校验迁入、分页与 pending 查询接口（C1） | 07、08 | 与 11b 的表拆分部分并行，11b 的消费者依赖 11a |
| 11b | `refactor/step-11b-ledger-account-split` | 04 文档 §3–§7：`t_account` 拆表、ledger 过账不再写余额、ledger 与 account 两个 `EconomicFactRecorded` 消费者、人工调整 Owner 账户拒绝、跨服务读接口、关账一致性检查、重建命令、日终核对；两库分离 | 07、08、11a | 否 |
| 12 | `refactor/step-12-card-isolation` | 05 文档：`card` 独立库与 app、票据接口、两段创建、`PaymentSucceeded` v2 与 card 消费、Platform 读卡目标改 card、Commerce 已存卡改调 card | 05、08 | 与 11 并行 |
| 13 | `refactor/step-13-cleanup` | 删除空的旧根目录、旧 Topic 双写、文档链接重写（09 文档 f）、`CURRENT_IMPLEMENTATION.md` 搬迁状态登记 | 全部 | 否 |

关键路径：00 → 01 → 07 → 08 → 11 → 13。并行度最高的窗口是 02a–02d 与 03–07。

## 2. 任务卡模板（给执行 agent）

每张任务卡包含：

1. 分支名与 worktree 路径；起点提交。
2. 输入文档：本目录相关设计文件、对应 analysis 报告、03 文档中该服务的归属表切片。
3. 搬迁清单：`git mv` 源 → 目标（逐目录）；新建模块的 POM 骨架；需要删除的死代码清单。
4. 代码改动清单：接口迁移、包名、配置键、Mapper 扫描、DataSource；逻辑改动（仅 11、12 步）逐条列出并引用设计文档章节。
5. 测试要求：必须通过的现有测试类清单与基线数；必须新增的测试；清理器与 DDL 要求。
6. 禁止事项：不改业务行为（11、12 除外）、不动其他服务目录、不执行 SQL、不提交到 `refactor-main`、不推送。
7. 交付物：短分支上的提交、`mvn -pl <modules> -am verify` 输出、ArchUnit 结果、`git log --follow` 抽查两份文件、变更摘要（≤ 30 行）。

## 3. 合并前门禁（主会话执行）

- 该步涉及模块 `verify` 通过，测试数 ≥ 基线（10 文档的计数规则）。
- `tools/arch-rules` 全量通过；R4 表归属无违规。
- 旧目录中被搬走的路径为空；`git log --follow` 对抽样文件可追溯。
- 服务可独立启动（`-app` 以 `mock` Profile 起到 Health UP）。
- 对 11、12 步额外：新增一致性测试全部通过；设计文档中的接口与事件目录已更新。
- 合并后立即把 `main` 合入 `refactor-main`，冲突只允许出现在被搬迁路径，按"新位置为准"解决。

## 4. 其他会话协调

搬迁某系统期间（03–07、11、12 各步），该系统的其他会话暂停提交到 `main` 中对应目录；步骤完成并把 `main` 合入后恢复。主会话在开始每步前通过会话消息通知。

## 5. 阶段 5 全链路验证范围（预告）

全部步骤完成后在 `refactor-main` 上：全 Reactor `verify`；本地 compose 全量启动 25 个 JVM；跑通现有 mor-test 已验收过的流程清单（Client 入驻、Client Console、Client API 下单与退款、卡直付 PayerMax 合成渠道、通知与 Webhook、Finance 账务内核）；结果写入 `delivery.md` 后再申请整体合并 `main`。
