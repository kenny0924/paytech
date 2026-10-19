# 任务卡 F · 阶段 6 mor-test 发布准备（只读核对 + 发布包；不改 mor-test 环境）

先读 `00-common.md`（禁令同样适用）、`delivery.md`（阶段 4 / 5 记录与发现项 E-1）、`verification/stage5-report.md`、`tasks/D-report.md`（块 99、`release/test-activate.sql` / `test-deactivate.sql`）、前序先例 `requirements/20261018/operations-console-财务基础资料与账本余额查询/release/{release.md,compose-candidate.yaml,source-overlay.txt}` 与其 `delivery.md` 阶段 6 / 7、`agent-docs/CURRENT_IMPLEMENTATION.md`「mor-test V2 部署」与「Operations 财务基础资料…」节、`test-environment/README.md`、`test-environment/scripts/initialize.py`、`test-environment/compose.sh`、`test-environment/compose.applications.yaml`。

## 允许的动作

- **只读核对 mor-test**：`docker ps` / `docker inspect`（镜像 digest、启动时间、环境变量）、`docker exec mor-test-rabbitmq-1 rabbitmqctl list_queues / list_bindings`（只列，不删）、经 `127.0.0.1:23306` 用 `test-environment/.env.test` 的只读方式 `SELECT`（表数、行数、块 97 / 98 / 99 状态、`t_accounting_rule_line` 计数、`t_ledger_account` Owner 类型分布、`t_account_*` 行数、billing 表数与事实数）。**不执行任何 UPDATE / DELETE / DDL，不重启、不替换容器，不删队列。**
- **构建**（安全，不部署）：照前序先例，从 `HEAD` 建干净 worktree（放 scratchpad 或 `/tmp`，用完删除），叠加本需求的源码文件（从 `git status` 取：`paytech/account/**`、`paytech/ledger/**`、`paytech/billing/**`、`paytech/foundation/paytech-messaging/**`、`paytech/tools/arch-rules/**`、`paytech/platform-support/**`、`paytech/operations-console-bff/**`、`operations-console-web/src/**`、`scripts/pdmaner/**`、`test-environment/compose.applications.yaml`；**不叠加** `paytech/compliance/**` 等其他会话的改动），写 `release/source-overlay.txt`（文件清单与差异 SHA-256）；JDK 25 离线 `./mvnw -B -o -DskipTests package` 六个 app（billing、account、ledger、platform-support、operations-console-bff）+ Web 生产构建；按 `test-environment` 的镜像构建方式（看 `build.sh` / `compose.sh` 与前序 release.md 第 3 步）构建 `mor-test/*` 镜像并打唯一 tag（如 `subledger-20261019`），记录 digest；写 `release/compose-candidate.yaml`（6 个镜像固定 digest；account-service 删 `ACCOUNT_LEDGER_SERVICE_URL`）。
- 在仓库内写文件：`REQ/release/release.md`、`compose-candidate.yaml`、`source-overlay.txt`；需要时补 `test-activate.sql` / `test-deactivate.sql`（D 线已写）。`verification/local_acceptance.py` / `local_browser.py` / `test_regression.py` / `acceptance-cleanup-local.sql` 若尚不支持 `E2E_TARGET=test`（库 `mor_*`、端口 23306、入口 `http://localhost:28080`、billing / account / ledger 私网接口经 `e2e_http` 的一次性探针容器、日志改 `docker logs`），按前序先例补齐并在本机只做语法 / 干跑检查（不得对 mor-test 写入）。

## `release/release.md` 内容（照前序结构）

1. **范围**：镜像 6 个（billing、account、ledger、Platform、BFF、Web；Gateway 不变）、SQL 4 份（`mor_account` ← `db/account/ddl.sql`；`mor_ledger` ← `db/ledger/ddl.sql` + `dml.sql`；`mor_platform` ← 初始化 SQL 块 99 阅读副本 + `release/test-activate.sql`）、Compose 变量删 1 行、RabbitMQ 旧队列删除（E-1）、XXL-Job 登记（见「需要你决定」）。
2. **只读核对结果**：mor-test 当前镜像 digest 与启动时间；`mor_ledger` 31 表、种子行、Owner 账户与引用分录计数（决定是否真有 Client / Partner 行要删）；`mor_account` 8 表行数；`mor_billing` 表数与事实数（有未发布 / 已发布事实则说明 account 新消费者启动后会消费历史事件的影响：`EconomicFactRecorded` 的 Fanout 不会重发，但 `account.billing.*` 队列里滞留的消息会被消费）；`mor_platform` 块 97 / 98 激活状态、块 99 是否已存在（期望 0 行）；RabbitMQ `ledger.billing.*`、`account.billing.*`、`*.dlq` 的消息数与绑定；XXL-Job Admin 上 account 执行器与任务登记现状；Compose 中 account / ledger 环境变量。
3. **需要你决定**：D1 构建基线（HEAD + 本需求叠加 vs 直接工作区）；D2 合成数据验收后清理；D3 `mor_ledger` 现有 Client / Partner 账户与分录（若 > 0）按 8.4 删除；D4 是否在 XXL-Job Admin 登记 `accountBalanceMaturity`（account 执行器）；D5 滞留在 `ledger.billing.*` / DLQ 的历史消息直接删除。
4. **执行顺序**（任一步不符即停止）：核对实际环境与本清单一致 → 备份（`mor_account`、`mor_ledger`、`mor_platform` 相关表、`mor_billing`；`~/Code-MoR-backups/<ts>-subledger-test/`，核验 dump 大小与可读）→ 停 account、ledger 容器（避免 SQL 期间消费）→ SQL：account、ledger（ddl → dml）、Platform 块 99（休眠）→ 替换：Platform → billing → ledger → account → BFF → Web（`compose.sh -f compose-candidate.yaml up -d --no-deps --no-build --pull never --wait`，各自 healthy、启动日志无 ERROR；**ledger 先于 account**）→ 删除 `ledger.billing.hash` / `ledger.billing.p0–p3` / `ledger.billing.dlq`（及对 `billing.events` 的绑定），核对 `ledger.account.*` 已声明且有消费者 → 执行 `test-activate.sql`（3 路由 + 1 菜单 + 角色版本）→ 验收 `E2E_TARGET=test`（接口期望 58/58、浏览器 31/31、回归全页面）→ 清理合成数据并核对基线 → 更新台账。
5. **恢复**：镜像回退到当前 digest（写明）；SQL 回滚：account 由 dump 恢复（表结构不兼容旧镜像，必须整库恢复）、ledger 由 dump 恢复（ALTER 可逆但分录删除不可逆）、Platform 块 99 按 `test-deactivate.sql` 停用或按 ID 段删除；旧队列删除不可逆但旧镜像重启会重新声明；触发条件与顺序。
6. 执行记录（阶段 7 填写，留空表）。

## 报告

`REQ/tasks/F-report.md`：核对命令与结果摘要、构建产物与 digest、发布包文件清单、待决定项。最终回复三行：报告路径、构建结果（镜像 / 测试模块计数）、阻塞项（无则写无）。
