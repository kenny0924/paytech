# 明细账运维 · mor-test 现网基线（阶段 6，2026-10-30 16:55—17:10Z）

> 只读：`docker inspect / exec（sha256sum、printenv 经 envcmp）/ cp 取出`、`rabbitmqctl list_queues`、mor-test MySQL READ ONLY 会话 SELECT、`mysqldump --single-transaction` 导出到本机。未停 / 起 / 替换容器，未写 SQL / MQ / XXL / Nacos。取出物在 `~/Code-MoR-backups/plops-stage6/`（0700，不入库）：`live/`（现网 JAR、ops-web dist、Gateway JAR）、`base/`（main 构建）、`candidate/`、`export-20261030T170501Z/mor_platform.sql.gz`。
> 对照构建：`/Users/kenny/Desktop/Workspace/Code-MoR-plops-base`（detached `331d8e0e`，本次新建，只用于构建；`operations-console-web/node_modules` 是指向发布树的**符号链接**，删 worktree 前先删链接）。Java 用 `-o -DskipTests clean package`（4 个 app `-am`），Web 用 `npm run build`。

## 1. 环境身份

| 项 | 值 |
|---|---|
| 环境 | mor-test（本机 Docker，compose project `mor-test`，入口 Nginx `http://localhost:28080` 运营 / `https://localhost:28443` Client） |
| 数据库 | `mor-test-mysql-1`（mysql:8.4.11）：本需求只写 `mor_platform`（块 148）；只读 `mor_ledger`、`mor_account` |
| MQ | `mor-test-rabbitmq-1` vhost `/mor-test`；`ledger.account.p0—p3` 各 1 个消费者、0 积压，`ledger.account.dlq` 0 |
| 不是 | 本机开发库 `mor-local-mysql-1`（`mor_*` 自动测试库、`*_v2e2e` 联调库）；生产 |

## 2. 镜像与制品

与任务卡记录（payout 会话 14:08Z）一致；main 仍为 `331d8e0e`。

| 服务 | 容器镜像 ID | tag | JAR / dist（sha256 前 16 位） | 与 main `331d8e0e` 构建比对（jarcmp：去掉 MANIFEST / classpath.idx / layers.idx / pom.properties，paytech-* 内层 JAR 逐条目 CRC） | 现网独有 |
|---|---|---|---|---|---|
| ledger-service | `sha256:51a57a3b7218…` | refclient-20261028b（2026-10-28 构建，源 c4ff0e20 + 参考数据客户端补丁） | `8dca8af87933ead2` | 现网 1401 / main 1441：现网独有 0、main 新增 40、改动 22，**全部**在提供方 / 公共 JAR：`paytech-platform-support-api`（联合查询、工作流数据查询、通知 tenantRecipients）、`paytech-account-api` `BalanceQueryApi`（payout positions）、`paytech-billing-api` `FactRefType` 多 `TAX_CALCULATION`、`paytech-messaging` `catalog/events.yaml`（commercial 订阅 / AgreementCapabilityStatusChanged / tax 订阅，均非 ledger 事件）。**ledger 自身类 0 差异** | **0**（落后 main 只在提供方 api） |
| account-service | `sha256:8dfd1060d136…` | payout-s1-20261030a | `8d19a1c7384229e0` | 556 = 556，0 改动 | **0** |
| platform-support-service | `sha256:0ea9cc2d6e89…` | payout-s1-20261030a | `f4782de42eeb781e` | 3249 = 3249，0 改动 | **0** |
| operations-console-bff | `sha256:626a74a740ce…` | payout-s1-20261030a | `2081c6a988461a47` | 3119 = 3119，0 改动 | **0** |
| operations-console-web | `sha256:acb33e948c65…` | payout-s1-20261030a | dist 284 个文件 | 与 `331d8e0e` `npm run build` **284 个文件逐字节相同**（[live](./evidence/ops-web-dist-live-acb33e94.sha256) = [main](./evidence/ops-web-dist-main-331d8e0e.sha256)） | **0** |
| api-gateway | `sha256:d51daee3c4ca…` | rq04-20261030b-gw | `d3dd5aa279ea6672` | 本需求不发；只取出 JAR 用于快照解码演练 | — |

明细：[evidence/jar-live-vs-main-331d8e0e.txt](./evidence/jar-live-vs-main-331d8e0e.txt)、[evidence/live-containers.txt](./evidence/live-containers.txt)。容器内 `sha256sum /app/application.jar` 与取出文件一致（4 个 JAR 逐一核对）。

health-probe：现网 6 个 Java 容器 `/app/health-probe/*` 相同（`LocalHttpHealthProbe.class` = `3466e45e…`）；main 源码已加 payout 管理端口 19117，编译结果为 `8b8d8348…`。本次候选镜像**沿用现网探针**（从现网 ledger 容器取出），与现网逐文件相同。

ledger 随发说明：现网 ledger 落后 main 的部分只在提供方 api JAR（对应提供方均已在现网），随本次一起带上；ledger 读 billing 事实时 `FactRefType` 多认一个值，属于向前兼容。

## 3. 环境变量（键名；值只在 envcmp 中比较，不抄写）

| 服务 | 键数 | 来源 |
|---|---|---|
| ledger-service | 39 | 全部来自 base compose（历史覆盖 20261019—20261027 只换镜像，未加键） |
| account-service | 35 | base |
| platform-support-service | 59 | base + `SPRING_APPLICATION_JSON`（webhook acquiring-service 8 事件 + notification payout-service 4 模板，来自 payout 覆盖） |
| operations-console-bff | 21 | base + `OPERATIONS_TAX_BASE_URL=http://tax-service:18116`（只在历史覆盖文件里） |
| operations-console-web | 7 | nginx 镜像自带 |

本需求不新增 env：ops-bff 新键 `operations.account-long-running-read-timeout` 默认 9s（`OPERATIONS_ACCOUNT_LONG_RUNNING_READ_TIMEOUT` 不设）；ledger 的读响应上限是代码常量。渲染比对 [evidence/env-check.txt](./evidence/env-check.txt)：候选 5 份、回滚 5 份全部 `missing=[] extra=[] valuediff=[]`。

## 4. 数据侧读数

| 项 | 读数 | 结论 |
|---|---|---|
| `mor_platform` 块 148（7 张表 ID 区间、编码、路径） | 全 0（[precheck](./precheck/)） | 空闲 |
| 最新块 | permission / resource / route / guard 最大块号均为 147；块 147 路由 19 ACTIVE、资源 7 ACTIVE；149+ 区间 0 | 147 最新且已激活 |
| 依赖 | 角色 510 PLATFORM_ADMIN ACTIVE（version 54）、财务菜单 97301、`FINANCE_ACCOUNTING_READ` 97101、目标 1 均 ACTIVE；财务子菜单排序 40 明细账查询 / 50 调整，45 空闲 | 满足 |
| Gateway 路由 | ACTIVE 671、DISABLED 6；快照 1 080 333 B（离线编译，与 payout 激活后读数相同） | 基线 |
| `mor_ledger` 2026-10-19 科目分级迁移 | `t_chart_account.account_no / account_status`、`t_ledger_account.account_no`、`t_chart_account_change`、`t_account_no_sequence` 均存在 | 已迁移，**不需要**任何 ledger SQL |
| `mor_ledger` 账簿 | 2 个：OE AE001 · USD（`238137396670066688`）、FE `238324786164494336` · USD（`238324787049492480`），均 OPEN | 验收用 |
| AE001 过账流 | account `OE.AE001.USD.0` last_sequence 12（最后入账 2026-10-30 05:53Z）；ledger `t_consumed_aggregate_version` = 12 | 一致（验收"已追平 / 无变化"的前提） |
| 运营语言包 | 现网 #29，28 个命名空间，11 558 个 ACTIVE key | 下一号 #30 |
