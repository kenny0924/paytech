# Operations 财务基础资料、账本与余额查询：mor-test 发布清单

阶段 6（评审稿，2026-10-19）。确认后执行阶段 7，结果写入第 6 节。当前状态以 [CURRENT_IMPLEMENTATION.md](../../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。本阶段只做只读核对与准备，未改 mor-test 任何容器、配置或数据。

**本次：mor-test 的 ledger、account、Platform、Operations BFF、Operations Web 五个容器换成「HEAD `26a6a217` + 本需求差异」构建的镜像；BFF 增加两个直连 ledger / account 的环境变量；`mor_platform` 追加并启用本需求的 2 个权限、5 个菜单 / 按钮与 18 条 Gateway 路由（R08 / R10 / R12 的登录人速率 30 / 分钟）；经运营入口验收后清理合成账务数据。Gateway、Nginx、Vault、Redis、RabbitMQ、XXL 与其余 20 个服务不动。**

## 1. 范围

| 项 | 内容 |
|---|---|
| 环境 | 本机 Docker Desktop（context `desktop-linux`）的 `mor-test`：30 个容器均 healthy；运营入口 `http://localhost:28080`；库 `127.0.0.1:23306`（`mor_platform` / `mor_ledger` / `mor_account`，应用账号 `platform_app` / `ledger_app` / `account_app`，口令在 `.env.test`）。不是生产，不是 `mor-local` |
| 源码基线 | HEAD `26a6a217`（2026-10-18）干净 worktree + 本需求 140 个文件（[source-overlay.txt](./source-overlay.txt)：ledger 75、BFF 38、Web 19、account 5、Platform 3）。见第 3 节 D1 |
| ledger | 新 `LedgerSetupQueryApi`（6 个只读端点）、15 个带 Code 的错误、六个命令端点要求 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`（无其他调用方：account 与其他服务不调用这六个命令接口）、命令 INFO 日志 |
| account | `BalanceQueryErrorCode.LEDGER_BOOK_NOT_FOUND`（两个余额查询 Processor） |
| Platform | `console-resource-capabilities.json` +3 组件键（菜单权限页的组件清单与资源校验）；注册 DML 见下 |
| Operations BFF | 18 个 `/finance/**` 转发（3 个 Controller）、固定地址直连 ledger / account、`/finance/**` 严格 JSON 边界；新依赖 `paytech-ledger-api`、`paytech-account-api`。Compose 增 `OPERATIONS_LEDGER_BASE_URL=http://ledger-service:18105`、`OPERATIONS_ACCOUNT_BASE_URL=http://account-service:18114`（镜像内默认 `127.0.0.1`，不加则 503） |
| Operations Web | 新一级菜单「财务」：账务基础资料（四标签页）、账本查询、余额查询 |
| 数据库 `mor_platform` | 初始化 DML 末尾「Operations finance accounting」块 97（204 行）+ 块 98（22 行）= 226 行（权限 2、授权 2、Console 5、路由 18、Header 60、Query 31、限流 108），路由 / 资源 DISABLED、授权 REVOKED → [test-activate.sql](./test-activate.sql)（18 / 5 / 2 / 1）。阅读副本 [registration-dml.sql](../db/platform-support-service/registration-dml.sql) 与初始化 SQL 逐字一致（含 30 / 分钟的 3 行） |
| 数据库 `mor_ledger` / `mor_account` | 无 DDL、无 DML；验收写入合成数据后清理回基线 |
| 顺带发布 | BFF 镜像含 HEAD 上尚未进 mor-test 的 `a6dbbeae`、`26a6a217`（Operations BFF 聚合 Processor 迁到 Foundation 模板，Risk 评估解释聚合）；五个镜像含 Foundation `339db7b8`、`8d123532`（messaging 连接恢复重声明 / 退避重试；运行中的 ledger / account 镜像无 build-info，无法判断是否已含）。Platform、ledger、account 自 V2 基线 `e5467400` 以来只有文档提交 `cf4a429c` |
| 不动 | Gateway（路由由快照动态加载，`refresh-interval` 5 秒）、Nginx（`/api/` 整体转 Gateway）、Vault、Redis、RabbitMQ、XXL、Client 链路、其余业务服务；不推送 Git 与镜像，不碰生产 |

## 2. 只读核对结果（2026-10-19）

| 项 | 结果 |
|---|---|
| 运行容器 / 镜像 | Platform `82085a4cac20`（容器 2026-10-18T14:30Z）、BFF `00e90315f6b5`（13:39Z）、Web `1d463a0ba80e`（13:39Z，入口 `assets/index-DJk7bRoW.js`）、ledger `97022a0b4e1c`（15:55Z）、account `dcc0acf3b5a0`（16:14Z）、Gateway `4cf14912680a`；均 healthy |
| 运行 JAR 内容 | 从容器复制的 `application.jar` SHA-256 前缀：ledger `6b96c987a5b7ceb5`、account `f02029094daa623a`、BFF `d33a84eb4520b8d9`、Platform `660b585dbbca2432`；四个都不含本需求任何类（`LedgerSetupQuery*`、`LedgerSetupErrorCode`、`BalanceQueryErrorCode`、`Finance*Controller`、`FinanceClientConfiguration`），BFF 不含 `paytech-ledger-api` / `paytech-account-api` |
| `:development` 标签 | Platform `144ec2bfd3e4`、BFF `d6d934d1a973`、Gateway `ad16d16c8562` 的标签已于 2026-10-18 15:55Z 被其他会话重建，与运行容器不同且内容未核实；**本次不用标签部署，候选文件固定镜像 ID** |
| `mor_platform` 冲突 | ID 段 `9000000000000970xx–989xx` 在权限、授权、资源、路由、Header、Query、限流 7 表均 0 行；`FINANCE_ACCOUNTING_*` 权限码 0；`operations-finance-*` 路由码 0 |
| `mor_platform` 基线 | 路由 277（全 ACTIVE）、Console 资源 86（全 ACTIVE）、权限 74；PLATFORM_ADMIN（`900000000000000510`）版本 29、64 个 ACTIVE 授权；Target `900000000000000001` ACTIVE；一级菜单 sort 10 / 20 / 25 / 30 / 35 / 40 / 40 / 300，「财务」sort 50 落在「通知」「外部 API」之后、「客户入驻审核」之前 |
| `mor_ledger` | 31 张表；科目表 1、科目 29、币种 1、规则行 44；财务主体 / 账簿 / 日历 / 期间 / 分录 / 人工调整均 0 行 —— 与本机阶段 5 起点一致，验收脚本的 fixture 口径（选未用的 entityNo、新建主体 / 日历 / 账簿）可直接复用 |
| `mor_account` | 8 张表全部 0 行 |
| 配置 | BFF 容器现有环境：`SPRING_PROFILES_ACTIVE=test`、`OPERATIONS_TEST_HTTP_ENABLED=true`、`OPERATIONS_CONSOLE_ORIGIN=http://localhost:28080`；无 ledger / account 地址；健康探针白名单已含 19201 / 19105 / 19114；候选覆盖文件 `compose.sh -f … config --quiet` 通过 |
| 工作区 | 与本需求无关的未提交改动已排除在差异之外：Platform core `ReviewFileDownloadProcessor` 与 compliance `ClientReviewAdminController`（其他会话）、`scripts/pdmaner/finance/*` 与 `account.pdma.json` / `account/README.md`（模型文档）、`design.pen`（本需求画板，单独提交） |
| 验收脚本可移植性 | `e2e_http` 支持 `E2E_TARGET=test`（运营入口 28080、`mor_ledger` / `mor_account` 走 `.env.test` 应用账号、ledger 私网接口经接入 `mor-test-backend` 的一次性探针容器）；`local_acceptance.py` 的 AC-08 操作人日志核对读本机 `logs/ledger.log`，test 需改为 `docker logs mor-test-ledger-service-1`（阶段 7 构建期间加 `E2E_TARGET` 分支，不改断言）；`acceptance-cleanup-local.sql` 由脚本经 `e2e_http.sql` 执行，注释里的 `_v2e2e` 库名不影响 test |

## 3. 需要你决定

**D1 源码基线。**
- 选项 A（建议）：HEAD `26a6a217` 干净 worktree + 本需求 140 个文件。不带其他会话未提交、未经本需求验证的 Platform `ReviewFileDownloadProcessor` 改动。
- 选项 B：当前工作区原样构建。会把其他会话的 Platform / compliance 未提交改动一起发到 mor-test。

**D2 验收写入范围。**
- 选项 A（建议）：与本机阶段 5 相同：接口脚本新建 1 个财务主体 + 日历 + 期间 + 账簿 + 账户，经 ledger 私网人工调整接口（Maker / Checker 两人）造 1 笔分录，余额页核对；完成后 `--cleanup` 把 `mor_ledger` / `mor_account` 删回基线并与备份行数核对。
- 选项 B：保留合成账簿与分录供你在 test 上演示；清理另行授权。

## 4. 执行顺序（任一步不符即停止）

1. **复核**：重跑第 2 节查询与容器清单，五个运行镜像不变；与其他会话确认没有正在进行的 test 部署或验收。
2. **备份**：`mor_platform` 的 `t_permission`、`t_role_permission`、`t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_route_query_param`、`t_gateway_guard_rule`、`t_role`；`mor_ledger`、`mor_account` 整库逻辑转储（清理基线）；`compose.applications.yaml` 副本；5 个容器 inspect。存 `~/Code-MoR-backups/20261019-finance-ops-test/<UTC>/`（0700 / 0600，SHA256SUMS），不进版本库。
3. **构建**：`26a6a217` 建干净 worktree，叠加 [source-overlay.txt](./source-overlay.txt) 的 140 个文件（记录差异 SHA-256）；JDK 25 离线 `./mvnw -B -o -DskipTests -pl ledger/paytech-ledger-app,account/paytech-account-app,platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am clean package`；在发布源码上跑 ledger-test（期望 221）、account-test（40）、BFF test（1473）`verify`；Web `npm ci && npm test && npm run build`（期望 1082）；探针从 `test-environment/java/*.java` 编译；镜像 tag `mor-test/<服务>:finance-ops-20261019`；记录镜像 ID、jar SHA-256、Web 入口资源名，把 [compose-candidate.yaml](./compose-candidate.yaml) 的五个 image 改为固定 ID。
4. **数据库（旧版本运行中，DISABLED / REVOKED 行不生效）**：注册块 97 + 98 先在事务内试跑并 ROLLBACK 核对 2 / 2 / 5 / 18 / 60 / 31 / 108，再执行提交。
5. **替换**：`compose.sh -f compose-candidate.yaml up -d --no-deps --no-build --pull never --wait` 依次 ledger → account → Platform → BFF → Web；各自 healthy、启动日志无 ERROR；BFF 日志确认 ledger / account 地址；Web 入口 `index.html` 引用新资源。各中断约 30 秒；ledger / account 期间 MQ 消息留在队列。同步把两个环境变量写入 `compose.applications.yaml`（随阶段 8 提交）。
6. **启用**：[test-activate.sql](./test-activate.sql) 试跑回滚核对 18 / 5 / 2 / 1 后提交（PLATFORM_ADMIN 29 → 30）；等 Gateway 快照加载（5 秒）。
7. **验收**（`E2E_TARGET=test`，经 `http://localhost:28080`）：接口 [local_acceptance.py](../verification/local_acceptance.py)（期望 67/67）；浏览器 [local_browser.py](../verification/local_browser.py)（期望 48/48，截图存 `assets/stage7/`）；财务以外的运营页面逐个只读回归（每页间隔 7 秒避开登录上下文限流；无 ≥ 400、无控制台错误）；Platform、BFF、ledger、account、Gateway 自验收开始的日志不含口令 / Token 与 ERROR。
8. **收尾**：按 D2 清理；两库行数与第 2 步转储一致；删除构建 worktree 与 `artifacts/` 中本次 JAR 副本。
9. **记录**：写入第 6 节，更新交付记录与台账。

## 5. 恢复

| 情况 | 处理 |
|---|---|
| 某个容器替换后不健康 | 用第 2 步 Compose 副本与原镜像 ID 回退该容器：ledger `97022a0b4e1c`、account `dcc0acf3b5a0`、Platform `82085a4cac20`、BFF `00e90315f6b5`、Web `1d463a0ba80e`。只回退 ledger 时新 BFF 多发的两个 Header 被旧 ledger 忽略；只回退 BFF 时新 ledger 的查询端点无调用方；均不互相阻塞 |
| 启用后发现阻断缺陷 | [test-deactivate.sql](./test-deactivate.sql)（18 / 5 / 2 / 1）先关入口，再按上一行回退镜像 |
| 注册数据 | 226 行休眠时对任何版本无影响，保留；删除另行授权 |
| 验收合成数据 | `--cleanup` 按运行记录中的账簿 / 日历 / 主体 / entityNo 删除；脚本失败时用同一组 ID 手工执行 [acceptance-cleanup-local.sql](../verification/acceptance-cleanup-local.sql) 两段；分录与余额无外部副作用（ledger / account 的补偿事件一并删除） |
| 不可逆项 | 无 DDL；无向外投递；Platform 审计表会留下验收操作记录（与既有发布一致，不回滚） |

## 6. 执行记录（2026-10-19 阶段 7，用户"继续"：D1=A、D2=A）

| 步骤 | 结果 |
|---|---|
| 1 复核 | 五个运行镜像与第 2 节一致；30 个容器 healthy |
| 2 备份 | `~/Code-MoR-backups/20261019-finance-ops-test/20261019T040013Z/`：`mor_platform` 注册相关 8 表、`mor_ledger` 与 `mor_account` 整库转储、Compose 副本、5 个容器 inspect、容器清单，SHA256SUMS |
| 3 构建 | 干净 worktree `26a6a217` + 本需求 140 个文件（差异 SHA-256 前缀 `72bbe68445de2a6d`）；JDK 25 离线 `clean verify`（4 个 app + 3 个 test 模块，`-am`）：ledger-test **221/221**、account-test **40/40**、BFF test **1473/1473**，BUILD SUCCESS（首轮因 worktree 无 `.env.local` 使 Foundation messaging 测试失败，改传 `-Dpaytech.local-environment.dir` 后从该模块续跑）；Web `npm test` **1082/1082**、`npm run build` 通过（node_modules 复用主仓库同一 lockfile）。镜像 tag `finance-ops-20261019`：ledger `sha256:0c0b6d4e…`（jar `774899b9165c4ac7`）、account `sha256:d341485d…`（jar `6891738e8c56f5ff`）、Platform `sha256:7c8af660…`（jar `5546e2a221578df8`）、BFF `sha256:ea20c4a3…`（jar `36b2bcc964479bbb`）、Web `sha256:a16abbcd…`（入口 `assets/index-DccQrG3w.js`）。核对：ledger / BFF JAR 含本需求类，BFF 含 ledger-api 与 account-api，Platform 能力 JSON 含 3 个 FINANCE 组件键。已写入 [compose-candidate.yaml](./compose-candidate.yaml) |
| 4 数据库 | 注册块 97 + 98 与阅读副本逐字一致；事务内试跑 ROLLBACK 核对 2/2/5/16/56/27/96 与 0/0/0/2/4/4/12 后提交（04:00:48Z）：2/2/5/18/60/31/108，全部休眠，3 行速率为 30 |
| 5 替换 | ledger 04:10:24Z → healthy 04:10:34Z；account 04:10:53Z → 04:11:08Z；Platform 04:11:19Z → 04:11:49Z；BFF 04:12:01Z → 04:12:20Z（环境含两个直连地址）；Web 04:12:34Z → 04:12:46Z；各启动日志 ERROR 0 行；入口 `index.html` 引用新资源；`compose.applications.yaml` 已加两个变量 |
| 6 启用 | [test-activate.sql](./test-activate.sql)：18 / 5 / 2 / 1，PLATFORM_ADMIN 29 → 30（04:13:21Z）；经入口 `GET /finance/currencies/v1` 返回 200 |
| 7 验收 | 接口 [local_acceptance.py](../verification/local_acceptance.py)（`E2E_TARGET=test`，AC-08 改读 `docker logs`）**67/67**，运行记录 `local-environment/e2e/runtime/finance-acceptance-20261019081410.json`；浏览器 [local_browser.py](../verification/local_browser.py) **48/48**，截图 [assets/stage7/](../assets/stage7/) 01—12；全页面只读回归 [test_regression.py](../verification/test_regression.py) 首轮 **31/33**：`/webhooks` 与 `/finance/setup` 各 1 次 `auth/v1/context` / `auth/v1/refresh` 504（Platform `TransactionTimedOutException`，同一秒 Platform 向 xxl-job-admin 的注册也 Read timed out；未动的 compliance / risk-decision / client-master / payment 容器 24 小时内各有 38—40 条同类超时，`xxl-job-admin` 内存 500/512 MiB），判定为 test 环境整体偶发停顿而非本次改动；重跑结果见下一行。日志：五个服务与 Gateway 自 04:10Z 起 ERROR 0 行（Platform 两条 `TransactionTimedOut` 为上述停顿）、无口令 / Token 明文（Platform 启动横幅回显既有 `JAVA_TOOL_OPTIONS` 的 truststore 口令参数属既有 Compose 配置，与本次无关） |
| 7b 回归重跑 | 合成数据清理后立即重跑 `test_regression.py`：**33/33** 通过（无 ≥ 400、无失败请求、无控制台错误；三个财务页截图 `regression-finance-*.png`） |
| 8 收尾 | `--cleanup`：ledger / account 39 张表行数回到运行前基线（账簿 / 日历 / 主体 / entityNo 由运行记录给出）；构建 worktree 与临时 JAR 已删除；备份目录保留 |

**当前 mor-test：** ledger `0c0b6d4e…`、account `d341485d…`、Platform `7c8af660…`、BFF `ea20c4a3…`、Web `a16abbcd…`（均 `finance-ops-20261019`）；「财务」菜单、2 个授权与 18 条路由 ACTIVE；30 个容器 healthy。回退按第 5 节。
