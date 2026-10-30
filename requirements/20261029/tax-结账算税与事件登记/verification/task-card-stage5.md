# 阶段 5 任务卡：tax 本机联调（后端部分）

- 授权（用户 2026-10-29"继续"，确认阶段 4 并进入阶段 5）：替换本机 e2e 的 tax / acquiring / billing / account JAR；重建本机 e2e 的 tax 库、迁移 acquiring 库；修改演示数据的税类；跑一笔沙特订单全流程、验证 AC-10。**不碰 mor-test、不提交、不推送。**
- 阿拉伯语税票：按方案 A（本需求保持英文版，标注待补），无需处理。
- 代码来源：worktree `/Users/kenny/Desktop/Workspace/Code-MoR-tax`（阶段 4 完成态）。如联调中需要改代码，只改该 worktree，并重跑受影响模块测试。
- 依据：同目录 `stage4-report.md`、`task-card-stage4.md`（构建与测试纪律仍适用）、`../technique.md`、`../requirement.md`。
- 参照先例：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261028/commercial-开通项前置门禁/verification/stage5-report.md`（本机 e2e 操作方式、备份与回滚记录格式）。

## 1. 硬性约束

- **只用 `mor-local-*` 容器与本机 e2e 进程**；严禁连接 `mor-test-*` 容器。
- **本阶段不替换 ops-bff、不改 ops-web 运行实例**：本机 ops-bff 正运行另一会话（REQ-03）的版本，主会话在协调中。
- 本机 e2e 库是 `*_v2e2e`（例如 `mor_tax_v2e2e`、`mor_acquiring_v2e2e`、`mor_billing_v2e2e`、`mor_platform_v2e2e`），不要改 `mor_tax` / `mor_acquiring`（那是 auto 测试库）。
- 每次改库前 mysqldump 受影响的表到 `verification/stage5/backup/`；每次替换 JAR 前 `cp -p` 备份为 `*.pre-tax`，用 `cp new .tmp && mv -f .tmp target.jar` 安装，再按原进程的 argv 与环境（`ps eww <pid>` 捕获，凭据不得写入报告）重启。readiness 必须 UP 才进行下一步。
- 不运行全 reactor 测试；不 install。

## 2. 步骤

1. **现状记录**：本机 e2e 各服务 pid、端口、JAR 时间；acquiring 的商业解析模式（`ACQUIRING_COMMERCIAL_RESOLUTION_MODE`，MOCK 或 REAL）；`mor_tax_v2e2e` 现有表；`mor_client_master_v2e2e` 中 AE001 的 `party_id`。
2. **构建**：从 worktree 构建 tax / acquiring / billing / account 的 exec JAR（`package -DskipTests`，`-pl … -am`）。
3. **tax 库**：备份后用 `changes/2026-10-29-tax-rebuild.sql` 重建 `mor_tax_v2e2e`（把 `USE` 改为该库），再执行 `tax-initial-data.sql`，`@ae001_party_id` 取步骤 1 查到的实际值（若 acquiring 为 MOCK 模式，则取 MOCK 配置中的 `880000000702`，二者不一致时在报告中写明并以实际运行模式为准）。确认 `tax_app` 账号对该库有 DML 权限（参照 `local-environment/mysql/init/01-create-databases-and-users.sh` 与 e2e 的 `bootstrap.sql`）。
4. **acquiring 库**：备份后对 `mor_acquiring_v2e2e` 执行 `changes/2026-10-29-order-tax-calculation.sql`（`USE` 改库）。
5. **首次启动 tax-service**：本机 e2e 目前没有 tax 进程。参照其他服务（如 billing）的启动方式（`local-environment/e2e` 下的 runtime / staging / 启动脚本，或其进程 argv 与环境），为 tax 配置数据库、RabbitMQ、Platform 地址、内部认证模式、Snowflake worker、端口 18116 / 19116，写入 `tax.pid` / `tax.started`，日志进 `logs/`。readiness UP，`tax.acquiring.p0..p3` 队列各有消费者。
6. **Platform 本机注册（仅本机）**：在 `mor_platform_v2e2e` 为 `tax-service` 增加 PRIVATE 文件写路由（reasonCode `TAX_RECEIPT`），以及 tax 作为内部调用方 / 受众所需的白名单（若 internal-auth 非 OFF）。脚本写在 `verification/stage5/`，带回滚；备份相关表。
7. **替换 billing、account、acquiring**（顺序：tax 已运行 → billing、account → acquiring）。
8. **演示数据**：用 Client API（经本机 Gateway / Nginx，凭据在 `local-environment/e2e/secrets.env`，不抄写）创建演示订单，商品税类 `SAAS_AUTOMATED`。不修改共享的 e2e 脚本；如发现现有 e2e 脚本使用 `STANDARD` 等未登记税类，列出文件与行号即可。
9. **场景走查**（每个场景记录订单号、计算 ID、状态、金额，以及各服务库中的关键行）：
   - S1 沙特 MoR：建单（暂估，声明国家 AE）→ 消费者资料账单国家 SA（最终 15%）→ 法律接受 → 支付（本机 PayerMax mock）→ tax 正式事实 + OUTPUT 底稿（PENDING 待补汇率）+ 税票 ISSUED（文件上传成功，可经 Platform 下载）→ billing PAYMENT 事实含 `SELLER_TAX` 行、本金减税 → account → ledger `TAX_PAYABLE` 贷记。
   - S2 被阻断：账单国家 KW → BLOCKED，支付被拒，订单保持开放。
   - S3 NOT_MANAGED：若本机可造 PSP 或 CLIENT 来源的订单则走一笔，确认可付、无正式事实、无 `SELLER_TAX`；不可造则说明原因。
   - S4 AC-10：停 tax-service → 支付一笔沙特订单 → billing 照常入账含 `SELLER_TAX` → 启动 tax → 正式事实经消息重试补登记，金额与 billing 一致。
   - S5 存量订单：选一笔替换前已存在的已付订单，确认查询与（如可）回调正常。
10. **报告**：`verification/stage5-report-backend.md`（主工作树需求目录下），格式参照先例：环境变更清单（时间、动作、备份 / 回滚依据）、场景结果、发现的问题与在 worktree 中的修复（含重测）、恢复说明。新 JAR 与 tax-service **保持运行**，供后续浏览器走查与用户查看。

## 3. 遇到以下情况先停下报告，不自行处理

- 需要替换 ops-bff、Gateway 或 platform-support 的 JAR。
- 需要改动其他会话的 worktree 或共享 e2e 脚本。
- 本机环境与先例描述差异较大、无法确定启动方式。
- 卡住超过 10 分钟。
