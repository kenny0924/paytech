# Operations 财务基础资料、账本与余额查询交付记录

## 适用阶段（已确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认（2026-10-18，Q-01—Q-06 按建议） |
| 2. 现状差异与影响分析 | 已确认（2026-10-18） |
| 3. 方案、接口与数据设计 | 已确认（2026-10-18）；Pencil 12 个画板（`operations-console-web/docs/design.pen`，未提交），导出见 [assets/stage3/pencil](./assets/stage3/pencil/) |
| 4. 研发实现 | 已确认（2026-10-18）；Opus 5.5 五个 agent（T0、A、B、C、D）并行，主会话（Fable 5.1）拆任务、合并核对与独立重跑；SQL 只写不执行 |
| 5. 综合验证与本地验收 | 已确认（2026-10-19）：接口 67/67、浏览器 48/48、清理回基线；限流按建议提到 30 |
| 6. test 发布准备 | 已确认（2026-10-19，"继续"：D1=A、D2=A） |
| 7. test 部署与验收 | 已确认（2026-10-19，"按清单本地提交"）：五个容器替换、注册 226 行并激活 18 / 5 / 2 / 1，接口 67/67、浏览器 48/48，回归见 [release.md 第 6 节](./release/release.md#6-执行记录2026-10-19-阶段-7用户继续d1ad2a) |
| 8. 提交 | 已完成（2026-10-19）：`7acbcf4f`（197 个文件：ledger / account / BFF / Web / Platform 注册数据 / Compose / 需求目录 / 台账）、`b2e9156b`（`design.pen` 画板）、归档提交（本记录与台账状态）；未推送 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-18 | 立项 | "帮我分析一下财务模块有哪些内容现在可以做到 operations 上面了"；"可以，先做第一批，设计和分析用当前模型，开发用 opus5.5 的多 agent 去开发" | 登记需求并交付阶段 1 评审稿 |
| 2026-10-19 | 7 | "按清单本地提交"：确认 mor-test 结果并授权阶段 8 本地提交 | 私有索引提交 `7acbcf4f` 与 `b2e9156b`，不含其他会话未提交改动；不推送、不建分支、不发 PR |
| 2026-10-19 | 6 | "继续"：确认发布清单，按建议 D1=A（HEAD `26a6a217` + 本需求 140 个文件）、D2=A（合成数据验收后清理） | 阶段 7：备份、构建、`mor_platform` 注册与激活、替换 ledger / account / Platform / BFF / Web、Compose 加两个变量、验收与清理 |
| 2026-10-19 | 5 | "可以，限流按建议提到 30，进阶段 6"：确认阶段 5 结果；R08 / R10 / R12 的登录人速率改 30 / 分钟 | 限流改动落地（两份 SQL 各 3 行、本机库 UPDATE 3 / 3）；阶段 6 mor-test 只读核对与发布清单，不改环境 |
| 2026-10-18 | 4 | "可以"：确认阶段 4 结果与偏差；授权本机 `mor_platform_e2e` 执行注册与激活 SQL、重新打包并重启本机 Platform / BFF / ledger / account、起 Web 开发服务器、接口与浏览器验收并清理合成数据 | 阶段 5 本机综合验证；不涉及 mor-test、不提交 |
| 2026-10-18 | 3 | "继续，pencil画了吗" → 补 11 个画板；"有科目维护吗" → 讨论后"可以"：本期增加只读"科目表与币种"标签页（画板 12），科目 / 币种维护登记第三批 | 阶段 4 研发：T0 合同先行，再 A（ledger/account）、B（BFF）、C（Web）、D（Platform 注册数据）Opus 5.5 agent 并行；SQL 只写不执行；不提交 |
| 2026-10-18 | 2 | "可以"：接受 [analysis.md](./analysis.md) 缺口判断、影响范围、固定 base-url 下游形态与并行会话处理边界 | 阶段 3 方案、接口、注册数据与页面设计（只写文档与设计） |
| 2026-10-18 | 1 | "按建议"：确认范围与 AC-01—13；Q-01 手工输入 entityNo / legalPartyId、Q-02 两级权限、Q-03 本期操作人只记 Header 与日志、Q-04 一级"财务"三个二级、Q-05 允许新增文件与共享文件增量、Q-06 阶段 1—7 适用且阶段 4 用 Opus 5.5 多 agent | 阶段 2 现状差异与影响分析（只读分析与文档） |

## 当前阶段与下一确认点

全部阶段完成。代码与文档已本地提交（`7acbcf4f`、`b2e9156b` 及归档提交），未推送。后续批次（科目表 / 币种维护、账簿 / 账户停用等）另立需求。

## 阶段 4 研发结果（2026-10-18）

执行方式：T0 合同先行（Opus 5.5），随后 A（ledger/account）、B（BFF）、C（Web）、D（Platform 注册数据）四个 Opus 5.5 agent 并行；主会话（Fable 5.1）拆任务、跨线一致性核对、独立重跑全部测试。agent 均已读 `DEVELOPMENT_CONVENTIONS.md`、`JAVA_PROJECT_STRUCTURE.md`、`WRITE_API_EXAMPLE.md`（含第 11 节自查）与 `UNIT_TEST_GUIDE.md`；未使用 Mockito / MockBean，测试经真实 HTTP 入口；未执行 SQL，未提交。

| 系统 | 改动 | 开发检查（主会话独立重跑，JDK 25，`-o`） |
|---|---|---|
| ledger | api：`LedgerSetupQueryApi`（6 端点）+ 7 个响应体 + `LedgerSetupErrorCode`（15 Code）；六个命令方法加 `X-Gateway-Actor-Subject` / `X-Gateway-Session-Id`。core：3 个查询对象、6 个 Mapper SQL、Repository/Service 新方法；15 个场景改为带 Code 的 `FailException`（DuplicateKey 在 Repository 转换）。app：`LedgerSetupQueryController`、Converter、四个命令 Controller 接 Header 并记 INFO 日志。test：5 个新 `List*Test`、`postAsOperator` / `assertRejected(code)` 助手、既有命令测试补 Header 与新 Code | `verify -pl ledger/paytech-ledger-test -am`：ledger-test **221/221**（用例方法 198→215），BUILD SUCCESS |
| account | api：`BalanceQueryErrorCode.LEDGER_BOOK_NOT_FOUND`；两个余额 Processor 改抛该 Code；测试断言同步 | `verify -pl account/paytech-account-test -am`：**40/40**，BUILD SUCCESS |
| Operations BFF | `FinanceClientConfiguration`（固定 base-url，两条 RestClient，无 LoadBalancer / Discovery）、`FinanceRequestConfiguration`（`/finance/**` 严格 JSON、8192 上限、GET 与 R07 拒 Body）、3 个 Client Port + impl、`FinanceContractConverter`、3 个 Models、3 个 Controller（18 路由）；测试：Ledger / Account Simulator、3 个 Browser 测试 + 边界 JSON、2 个不可用测试；共享文件增量：两个 pom、`application.yml`、README、测试基座、Platform Simulator 路由权限、精确路由集合（+18）、命名测试 | `verify -pl operations-console-bff/paytech-operations-console-bff-test -am`：**1473/1473**（1390→1473），BUILD SUCCESS |
| Operations Web | `pages/FinanceSetup.tsx`（四标签页）、`FinanceLedger.tsx`、`FinanceBalances.tsx`；`src/finance/`（模型 / 解码 / 命令 Hook / 四个标签页 / 弹窗 / 下钻 / 抽屉 / 公共件 / 数据 Hook / css / 测试）；共享：`pageCapabilities.ts` +3、`ConsolePage.tsx` +3、计数断言 30→33 | `npm test` **1082/1082**（1068→1082）；`npm run build` 通过（仅既有 >500 kB 提示） |
| Platform 注册数据 | 初始化 SQL 追加块 97（204 行）与块 98（22 行），共 226 行，全部休眠；`console-resource-capabilities.json` +3；`OperationsCatalog` +2 权限；需求目录 `db/platform-support-service/registration-dml.sql` 阅读副本 | `OperationsRoleManagementTest`、`OperationsAuthorizationTest`、`OperationsAccountManagementTest` **44/44**，BUILD SUCCESS |

跨线一致性核对（主会话）：18 条 Gateway 路由的方法与外部路径 = Web 路径常量 = BFF Controller 路由；Web 发送的查询键（账簿 / 账户 / 流水 / 科目 / 币种 / 余额）全部在各路由 Query 白名单内；Web 错误码表覆盖 15 个 ledger Code 与 account Code；三页能力键 / 路由与 Console Resource 一致；Web 期间开放请求空 Body + JSON Content-Type 满足 BFF 的无 Body 校验（按输入流判空）。

验收映射：AC-01 BFF 403 用例 + Web 按钮门控源码扫描；AC-02 ledger 15 个 Code 用例 + BFF 400 透传 + Web Code→字段表；AC-03 ledger 开放用例 + BFF R07 拒 Body；AC-04 ledger L4/L5/L6 + Web 下拉数据源；AC-05 ledger 分页既有用例 + BFF `size=7`、分页回显；AC-06 ledger 既有 + Web 解码测试；AC-07 account `LEDGER_BOOK_NOT_FOUND` + BFF R17/R18；AC-08 ledger 缺 Header 400 + BFF Header 转发与不转发断言；AC-09 SQL 行数人工核对 + Catalog 基线；AC-10 四条线测试；AC-11—13 留阶段 5 浏览器验收。

环境注意：`-am` 构建先跑 Foundation redis-starter 测试，需 `set -a; source local-environment/.env.local; set +a; export PAYTECH_REDIS_PASSWORD=${PAYTECH_REDIS_PASSWORD:-$MOR_REDIS_PASSWORD}`。

实现偏差见 [technique.md 第 7 节](./technique.md#7-实现偏差阶段-4)。未做：本机联调与浏览器验收（阶段 5）。

## 阶段 5 本机验证（2026-10-18）

**环境事实与已执行动作（本机，已授权）**

| 动作 | 结果 |
|---|---|
| 现状 | 本机没有任何 V2 全链路服务在跑，`*_v2e2e` 专用库已在任务卡 14 结束时按其回滚说明删除；残留的 4 个旧根 Java 进程属其他会话的旧架构测试启动器，未动 |
| 重建 V2 本机全链路基础（`architecture-V2骨架与模块搬迁/verification/local-e2e/sql/apply-e2e.sh`） | `rabbitmq`：新建 vhost `/mor-e2e` 并授权；`databases`：15 个 `*_v2e2e` 库与授权；`platform`：`mor_platform_e2e` 整库复制到 `mor_platform_v2e2e` + delta（57 张表）。其余 12 个服务库本次未建 Schema（不需要） |
| ledger / account e2e 库 | `mor_ledger_v2e2e` ← `ledger-schema.sql` + `ledger-initial-data.sql`（31 表；科目 29、币种 1、规则行 44）；`mor_account_v2e2e` ← `account-schema.sql`（8 表） |
| Finance 注册块（`db/platform-support-service/registration-dml.sql`，与初始化 SQL 末尾两块逐字一致） | `mor_platform_v2e2e`：权限 2、授权 2、Console 5、路由 18、Header 60、Query 31、限流 108（共 226） |
| [activation-local.sql](./db/platform-support-service/activation-local.sql) | 18 / 5 / 2 / 1 |
| 启动（`run-service.sh start`，JAR 取各模块 `target/*-exec.jar`，含本需求改动） | platform 11s、ledger 4s、account 4s、ops-bff 4s、gateway 5s、nginx、ops-web（vite 18301）全部 readiness UP；Vault 沿用容器 |

**本机 Platform e2e 库名更正：** 阶段 4 确认记录里写的是 V1 的 `mor_platform_e2e`；V2 本机全链路使用 `mor_platform_v2e2e`（由前者复制），本次只写后者，`mor_platform_e2e` 未改。

**验收结果（主会话独立冒烟 + Opus 5.5 agent 脚本，2026-10-18 22:50 最终轮）**

| 项 | 结果 | 证据 |
|---|---|---|
| 主会话冒烟 | 登录 → 导航含 `OPERATIONS_FINANCE` 目录、三页与维护按钮；币种 / 科目表 / 29 个可过账科目经 Gateway → BFF → ledger 返回；余额查询错误经 account → BFF 为 400 `LEDGER_BOOK_NOT_FOUND` 带中文 detail | 会话记录 |
| 接口验收 AC-01～AC-09 | **67/67** | [local_acceptance.py](./verification/local_acceptance.py)，运行记录 `local-environment/e2e/runtime/finance-acceptance-20261018225022.json` |
| 浏览器验收 AC-02～AC-07、AC-11～AC-13 | **48/48**，无意外 HTTP ≥ 400、无 429、无控制台错误 | [local_browser.py](./verification/local_browser.py)，截图 [assets/stage5/](./assets/stage5/) 01—12（与阶段 3 画板同名） |
| 清理 | ledger / account 共 39 张表行数回到运行前基线；`mor_platform_v2e2e` 未动 | [acceptance-cleanup-local.sql](./verification/acceptance-cleanup-local.sql) |

验收口径说明：AC-06 的分录用 ledger 私网人工调整接口（Maker/Checker 两人）造一笔 OE 自有科目的借贷（人工调整禁止触碰 Client / Partner 账户）；AC-02 的"科目不可过账"用不存在的科目代码触发（STANDARD 29 个科目全部可过账，无汇总科目）；AC-08 验证了浏览器伪造的 `X-Gateway-Actor-Subject` 不改变 ledger 日志中的 actor（仍为会话账号），缺 Header 的直连 POST 为 400，无会话为 401；AC-09 核对 226 行均 ACTIVE。弹窗画板 03 / 05 / 06 为视口截图。

**阶段内修复（同合同实现缺陷）：** 浏览器验收发现新建账户 / 新建账簿弹窗与背后筛选表单重复 DOM id（label 指向错误输入框）。Web 为 `src/finance` 与三页的 11 个 antd Form 加唯一 `name`（`npm test` 1082/1082、构建通过），重跑后新增检查"弹窗内 4 个 label 全部指向弹窗内输入框、页面无重复 id"通过。

**观察（未改，待决）：** 一次中间轮次在接口脚本刚用过同一路由后，浏览器账户列表请求收到 429（每登录人每路由 10 次 / 60 秒，沿用 Webhook 先例）。人工在账本页反复筛选、翻页、回退可能在一分钟内对 R10 / R12 达到 10 次。可选：把 R08 / R10 / R12 的 AUTHENTICATED_PRINCIPAL 速率提到 30 / 分钟（改两份 SQL 中 6 行，本机需 UPDATE），或保持先例。antd 弃用警告（`Spin tip`、`Space direction`）为开发服务器噪音，不计失败。

**本机服务状态：** platform / ledger / account / ops-bff / gateway / nginx / ops-web 仍在运行（`run-service.sh status`），便于你自己打开 `https://127.0.0.1:18444` 查看；确认后可 `run-service.sh stop` 这 7 个。

## 阶段 6 mor-test 发布准备（2026-10-19）

**限流决定落地：** 用户确认后，`platform-support-initial-data.sql` 与阅读副本 `registration-dml.sql` 中 R08 / R10 / R12 的 AUTHENTICATED_PRINCIPAL 行（`…097747` / `…097759` / `…097771`）由 10 改 30；本机 `mor_platform_v2e2e` 同步 UPDATE（限流 3 行 + 三条路由版本 +1），经 Gateway 对账簿列表连发 14 次全部 200。mor-test 直接以 30 注册。

**只读核对（未改环境）：** 运行镜像、JAR 内容（四个运行 JAR 均不含本需求类）、`mor_platform` 7 表 ID 冲突 0、PLATFORM_ADMIN 版本 29、`mor_ledger` 与 `mor_account` 处于与本机阶段 5 相同的起点基线；细节与数字见 [release.md 第 2 节](./release/release.md#2-只读核对结果2026-10-19)。发现 `:development` 标签已被其他会话重建且与运行容器不同，候选文件改为固定镜像 ID。

**发布包：** [release.md](./release/release.md)（范围、核对、决定 D1 / D2、执行顺序、恢复）、[test-activate.sql](./release/test-activate.sql)、[test-deactivate.sql](./release/test-deactivate.sql)、[compose-candidate.yaml](./release/compose-candidate.yaml)（五个镜像 + BFF 两个环境变量，`config --quiet` 通过）、[source-overlay.txt](./release/source-overlay.txt)（140 个文件）。

**遗留到阶段 7：** `local_acceptance.py` 的 AC-08 日志核对加 `E2E_TARGET=test` 分支（改读 `docker logs`）；`compose.applications.yaml` 写入两个环境变量。本机 7 个服务仍在运行，供对照；可随时 `run-service.sh stop`。

## 阶段 7 mor-test 部署与验收（2026-10-19）

用户"继续"后按 D1=A、D2=A 执行，完整步骤、时间点、镜像 ID、JAR SHA-256、回归分析与恢复位点见 [release.md 第 6 节](./release/release.md#6-执行记录2026-10-19-阶段-7用户继续d1ad2a)。要点：

- **构建：** 干净 worktree `26a6a217` + 本需求 140 个文件，发布源码上 ledger-test 221 / account-test 40 / BFF test 1473、Web 1082 全部通过；镜像 tag `finance-ops-20261019`，Compose 候选固定 sha256 ID。
- **mor-test：** `mor_platform` 注册 226 行（04:00:48Z）→ ledger、account、Platform、BFF、Web 依次替换（04:10—04:13Z，均 healthy，启动日志 ERROR 0）→ 激活 18 / 5 / 2 / 1（PLATFORM_ADMIN 29 → 30）。`compose.applications.yaml` 已加 `OPERATIONS_LEDGER_BASE_URL` / `OPERATIONS_ACCOUNT_BASE_URL`。
- **验收：** 接口 67/67、浏览器 48/48（[assets/stage7](./assets/stage7/)）；全页面只读回归首轮 31/33（两次 504 为 test 环境整体偶发停顿，详见 release.md），重跑 **33/33**。合成数据已清理回基线。
- **验收脚本 test 化（Opus 5.5 agent）：** `local_acceptance.py` AC-08 在 `E2E_TARGET=test` 下读 `docker logs`；新增 `verification/test_regression.py`。
- **遗留：** test 环境偶发超时与 `xxl-job-admin` 内存接近上限为环境观察，不属本需求；本机 7 个服务仍在运行。
