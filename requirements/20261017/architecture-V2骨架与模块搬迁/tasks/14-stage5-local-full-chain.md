# 任务卡 14 · 阶段 5 全链路验证：宿主机起全部服务，连 local-environment，跑通已验收流程

- 分支：`refactor/step-14-stage5-local-full-chain`；worktree：`/Users/kenny/Desktop/Workspace/Code-MoR-step14`；起点：`refactor-main@1c5e708c`（全部搬迁步与 12x 已合入；全 Reactor 8218 例全绿；13 收口并行进行，本步不改 13 负责的文件：`test-environment/**`、`agent-docs/**`、仓库根 README、`.gitignore`）。
- 对应计划：[11-dev-plan.md](../design/11-dev-plan.md) §5；用户 2026-10-18 决定：**不用 mor-test compose**（Docker VM 内存不够），改为宿主机直接起 JVM 连 `local-environment`；卡直付 PayerMax 合成渠道与 Finance 账务内核**本机冒烟即可**，其余四条流程按既有验收脚本标准跑通。
- 本步只验证与修 wiring，不做新功能；发现的生产代码缺陷允许在本分支做最小修复（每处在报告列出：现象、根因、改动、对应测试），不改业务规则。

## 1. 输入（先完整读取）

- 既有本机全链路机制：`requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/`（`run-service.sh`、`e2e_http.py`（`E2E_TARGET=local` 默认）、`acceptance_flow.py`、`scenario_flow.py`、`workspace_flow.py`、`developer_flow.py`、`team_flow.py`、`*_browser.mjs`、`nginx.conf.template`、`bootstrap_admin.py`、`GenerateLocalE2eMaterial.java`）；主仓库工作区 `/Users/kenny/Desktop/Workspace/Code-MoR/local-environment/e2e/`（`secrets.env`、`bootstrap.sql`、nginx、pid 文件——只读参考，本 worktree 没有 `.env.local` / `e2e/`，运行时用 `-Dpaytech.local-environment.dir` 或显式路径指向主仓库的 `local-environment`）。
- Client API：`requirements/20261017/commerce-ClientAPI测试环境联通/verification/`（`client_api_flow.py`、`register_client_api.py`）；Webhook：`requirements/20261015/platform-support-Webhook投递/`（release/ 与 verification/）；卡直付：`requirements/20261014/payment-PayerMax卡直付对接/assets/harness/`（`configure_card.py`、`verify_card.py`）与 `requirements/20261013/platform-support-PayerMax接入/assets/harness/`（`start.py`、`PayerMaxMock.java`、`common.py`）；Finance：`requirements/20261015/finance-账务内核/delivery.md` 冒烟 13 步描述、11a / 11b 报告的接口清单。
- `agent-docs/CURRENT_IMPLEMENTATION.md` 中各流程的验收记录行（证据格式）；`paytech/tools/instance-manifest/src/main/resources/catalog/services.yaml`（服务、端口）；各 `paytech/*/paytech-*-app/src/main/resources/application.yml`（环境变量键）。
- 阶段 5 调研事实（主会话提供，见本卡附录）。

## 2. 改动清单

1. **启动器**：新建 `requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/run-service.sh`（以旧 `run-service.sh` 为蓝本），按服务名启动 / 停止 / 查状态：19 个 Spring Boot 应用（exec JAR 来自 `paytech/*/paytech-*-app/target/`，用 `cd paytech && ./mvnw -B -DskipTests package` 产出）、Go Vault（`paytech/vault` 构建，`/Users/kenny/sdk/go1.27.1/bin/go`）、nginx（沿用旧模板，上游改新端口）、两个 Web（vite）。环境变量从主仓库 `local-environment/.env.local` 与 `local-environment/e2e/secrets.env` 读取；每个服务一个 pid 与日志文件（放 `local-environment/e2e/` 同级的 `logs/`，不入 git）。JVM：`-Xms128m -Xmx384m`。
2. **一致性修正（只改配置 / 启动参数，不改代码，除非确属缺陷）**：所有注册 Nacos 的服务用同一 namespace（通过环境变量统一到 `mor-local`，报告列出每个服务的变量名）；Finance 五服务与 data-service 以 `*_SERVICE_URL` 直连；`internal-auth.mode` 全部 `"OFF"`（本步不验证 D26 切换）；Vault 策略文件 `local-environment/vault/policy.local.json` 需补 `card-service`、`channel-connector-service` 等新 Caller——这是主仓库文件，**写成评审稿放在本 worktree 对应路径并在报告给出 diff**，由主会话应用。
3. **数据库**：沿用旧 e2e 做法（`*_e2e` 专用库）还是直接用 `mor_*` 本地库，二选一并说明；需要新建库 / 账号 / 应用 Schema 的 SQL 写成评审文件并在回复中列出路径，由主会话执行（用户已一次性授权本机测试库 SQL）。
4. **流程脚本适配**（新目录 `verification/local-e2e/` 内复制并改，不改旧需求目录下的脚本）：
   - Client 入驻：`acceptance_flow.py`、`scenario_flow.py`（CCT → client-master；审核结论事件 compliance → client-master 新信封）。
   - Client Console：`workspace_flow.py`、`developer_flow.py`、`team_flow.py`（HTTP 版必跑；浏览器版可选，装了 playwright-core 就跑）。
   - Client API 下单 / 取消 / 退款：`client_api_flow.py` 改为 acquiring（库 `mor_acquiring`）；"订单已支付"不再用旧 Topic 夹具，改为真实支付：经 card-tokens 入口用卡直付合成 PayerMax 渠道完成一笔支付，让 `PaymentSucceeded` v2 真实走到 acquiring 与 card；退款走 PAY-I04 真实链路到 `RefundSucceeded`。
   - 通知与 Webhook：Platform Webhook 投递到本机临时接收端（旧 `commerce_webhook_flow.py` 改名适配）；邮件用 Mailpit。
   - 卡直付 PayerMax 合成渠道：`configure_card.py` / `verify_card.py` + `PayerMaxMock` 在本机全量环境跑通（冒烟标准：支付成功、查询回传、退款一笔）。
   - Finance 账务内核冒烟：新脚本 `finance_kernel_smoke.py`：billing 登记 MANUAL_ADJUSTMENT 事实（Maker/Checker）→ ledger 过账（查分录）→ account 余额（查 `/balances/v1/...`）→ `consistency` 追平；落脚本，记录步数与结果。
5. **证据**：每条流程的通过步数 / 总步数、关键 ID、日志路径；全部 19 JVM + Vault 的 Health UP 清单（端口、耗时、RSS）；宿主机内存峰值。

## 3. 验证清单

1. `run-service.sh start all` 后 20 个进程 Health UP（管理端口 `/actuator/health`，Vault 自身 health）。
2. 六条流程结果表（四条按既有脚本步数全通过；两条冒烟通过）。
3. `run-service.sh stop all` 后端口全部释放，本地 MySQL / Redis / RabbitMQ 中由本步产生的 e2e 数据按脚本清理（列出清理 SQL / 命令）。
4. 不跑全 Reactor（主会话已跑）；若改了生产代码，只跑受影响模块。

## 4. 禁止事项

- 不操作 mor-test；不 up / down / restart local-environment 容器；不杀不属于本步启动的进程（端口被占先回复主会话）；不执行 SQL（写文件交主会话）；不改 13 步负责的文件；不调用 Superpowers skill；不提交到 `refactor-main` / `main`；不推送；不把任何密码值写进脚本、报告或输出。

## 5. 交付物

1. 短分支提交，前缀 `refactor(step-14): `，末尾 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。
2. 报告 `tasks/14-stage5-report.md`：环境与启动器说明、一致性修正清单、六条流程结果表、发现并修复的 wiring 缺陷、未通过项与原因、delivery.md 阶段 5 行草稿、后续（Vault REQUIRED 切换、Nacos namespace 统一进配置等）。
3. 最终回复：六条流程结果 + Health UP 数 + 提交哈希。

## 附录 · 调研事实摘要（2026-10-18）

- 19 个 Spring Boot 应用及端口（业务 / 管理 = +1000）：client-master 18101、acquiring 18102（ctx `/commerce`）、payment 18103（ctx `/payment`）、risk-decision 18104、ledger 18105、platform-support 18106（ctx `/platform-support`，管理端口无独立配置）、commercial 18108、consumer 18109、channel-connector 18110、card 18111、compliance 18112、billing 18113、account 18114、reconciliation 18115、tax 18116、data-service 18120（依赖 Doris，本步可不起，报告说明）、api-gateway 18080/19080、operations-console-bff 18201、client-console-bff 18202；Go Vault 18107（与 local-environment compose 的 vault 容器端口相同，启动前确认本步用哪一个：建议沿用容器 Vault，不另起）。
- Nacos 默认 namespace 不一致：client-master / commercial / consumer / acquiring / gateway 为 public，其余 mor-local；Finance 五服务与 data-service 不注册。
- 本地库已存在：mor_account、mor_acquiring、mor_billing、mor_card、mor_cct、mor_channel_connector、mor_client_master、mor_commerce、mor_commercial、mor_compliance、mor_consumer、mor_ledger、mor_payment、mor_platform、mor_reconciliation、mor_risk、mor_risk_decision、mor_tax、mor_vault 及 `*_e2e`（platform、payment、vault）。`.env.local` 只有旧 10 个 `MOR_*_DB_PASSWORD` 键，新库密码键缺失（新库账号密码以各 `-test` 的 `init/ddl.sql` 为准）。
- 宿主机现被其他会话占用的端口：18080/19080、18101、18104、18106、18117、18201/19201、18202/19202、nginx 18443/18444、vite 18301/18302——主会话负责协调释放，释放前先做构建与脚本。
