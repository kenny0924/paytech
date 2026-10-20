# 任务卡 VER · 阶段 5 本机综合验证（channel-connector 运营控制台）

- 依据：[technique.md](../technique.md) 第 10 节「验证（阶段 5）」、[requirement.md](../requirement.md) 第 7 节验收标准、[delivery.md](../delivery.md) 阶段 4 报告与遗留。
- 执行者：Opus 5.5 agent，**直接在主工作区** `/Users/kenny/Desktop/Workspace/Code-MoR` 工作（阶段 4 的全部代码已集成在主工作区，未提交）。不执行任何 git 写命令；不调用 Superpowers skill；不碰 mor-test / Nacos 共享实例；不用 Mockito。
- 本机数据库授权（用户已确认阶段 5）：只允许对本机 `local-environment` MySQL 的 **`*_v2e2e` 库与 `mor_vault`** 执行本需求 SQL；客户端一律 `--default-character-set=utf8mb4`；root 密码变量 `MOR_MYSQL_ROOT_PASSWORD` 在 `local-environment/.env.local`（`docker exec -e MYSQL_PWD=...`），不打印。每次执行记录到报告。

## 1. 先读

`requirements/20261017/architecture-V2骨架与模块搬迁/verification/local-e2e/run-service.sh` 头注与 `sql/apply-e2e.sh`、`sql/50-channel-connector-payermax-mock.sql`、`channel_setup.py`、`acceptance_flow.py`、`e2e_http.py`、`browser_acceptance.mjs`；`requirements/20261018/operations-console-财务基础资料与账本余额查询/verification/`（`local_acceptance.py`、`local_browser.py`、`acceptance-cleanup-local.sql`）作为验收脚本形态；`requirements/20261014/platform-support-渠道沙箱验证/` 的本机验收记录（标准 API / 接入方如何在本机登记 PayerMax mock）；`agent-docs/CURRENT_IMPLEMENTATION.md` 中「本机 Vault」「local-e2e」相关条目；本需求 `db/` 全部文件与 `tasks/*-report.md`。

## 2. 步骤

1. **单元与门禁（主工作区）：** `channel-connector-test`（已 187/187）、`arch-rules`（已 61+3）、ops-web（已 1136 + build）、ops-bff（主会话正在跑，结果见 delivery.md）；你负责补：Platform `paytech-platform-support-test` 的 PLT 定向 11 类 + 全量 auto 回归（命令见 `tasks/PLT-report.md` 第 4 节；先 `pgrep -f platform-support-test` 确认没有其他会话的 Platform 测试进程，有则等待并每 5 分钟重查，不要清库）；`node scripts/pdmaner/generate-channel-connector-model.mjs --check`；`npm --prefix requirements-site test`（本需求未改站点，只需确认不是本需求导致失败）。
2. **构建制品：** `paytech/` 下 JDK 25 `./mvnw -o -q -pl channel-connector/paytech-channel-connector-app,platform-support/paytech-platform-support-app,operations-console-bff/paytech-operations-console-bff-app -am -DskipTests package`；`npm --prefix operations-console-web run build`。
3. **本机 SQL：** 按 run-service.sh 的隔离约定对 `mor_platform_v2e2e` 执行 `db/platform-support-service/registration-dml.sql`（块 92）与 `activation-local.sql`（核对预期行数 31 / 9 / 7 / 1）；对 `mor_vault` 执行 `db/vault/dml.sql`（先核对规则不存在；本机 Vault 容器从库加载策略，等待其刷新间隔后验证 Protect 可用）；`mor_channel_connector_v2e2e` 执行 `db/channel-connector/ddl.sql`（若该库存在且无此表）。回滚语句写在报告。
4. **启动与冒烟：** 用 `run-service.sh jars` + `start` 起 platform、channel-connector、ops-bff、ops-web、nginx、payermax-mock 及链路所需服务（按 `check` 结果补齐），Health UP。
5. **控制台接口验收（API，经 Nginx 运营入口 `https://127.0.0.1:18444`，登录方式照 `local_acceptance.py`）：** 以管理员会话覆盖 31 条路由的正向与关键拒绝场景：新建渠道 PAYERMAX_E2E（编码须等于本机已登记的 PayerMax 接入方编码；若本机接入方编码不同，用现有编码），激活（接入方存在）、另建一个无接入方的渠道激活被拒并核对 detail；新建账号 → 写入参数与凭据（真实 Vault Protect，核对 `account_vault_token` 落库、详情只有末 4 位）→ 激活；新建 MID → 新建能力组（ACTION.PAY 等）→ 激活；成本方案：建方案、建版本、提交、用另一账号批准（或演示同人被拒）、未到期 APPROVED 作废；变更记录查询；版本对比；MID 列表 externalMidCode 筛选；未授权账号看不到「渠道」菜单 / 调路由 403。写成 `verification/local_acceptance.py`，输出逐项通过数。
6. **替代种子的全链路：** 用上一步经控制台登记的渠道 / 账号 / MID / 能力，跑 `acceptance_flow.py`（或其最小支付路径）证明 Payment 候选查询返回该 MID、渠道执行走到 Platform 标准 API（payermax-mock）；不再执行 `50-channel-connector-payermax-mock.sql` 与 `channel_setup.py`。若既有脚本硬编码了种子 ID，说明差异并给出最小改法（不改既有脚本，写新的）。
7. **浏览器验收：** 照 `local_browser.py` / `browser_acceptance.mjs` 形态，覆盖 AC-V01–V04 与三页的主要操作，截图到 `assets/stage5/`（每张对照 Pencil 画板编号），写 `verification/local_browser.py`。
8. **清理：** `verification/acceptance-cleanup-local.sql` 清除验收产生的渠道 / 账号 / MID / 能力 / 成本 / 变更记录与 Platform 会话数据（只删本次创建的 ID），Vault 侧只记录产生的 Token 数（不删）。保留块 92 与激活状态（本机）。

## 3. 交付

`tasks/VER-report.md`：每一步的实际命令、结果与证据路径；SQL 执行记录（库、时间、行数、回滚语句）；AC-A01–A06、AC-B01–B06、AC-C01、AC-V01–V04 逐条映射（通过 / 未覆盖及原因）；发现的缺陷（含复现步骤，不自行修改产品代码，列给主会话）；遗留。不要提交。
