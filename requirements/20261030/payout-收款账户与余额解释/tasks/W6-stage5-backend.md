# W6 任务卡：阶段 5 本机后端端到端验收（真实入口，无 Mock）

- 环境（已由主会话就绪，不要重启任何进程）：本机共享栈 `local-environment/e2e`。运营入口 `https://127.0.0.1:18444`，Client 入口 `https://127.0.0.1:18443`（Nginx → api-gateway 18080 → BFF）；Platform `127.0.0.1:18106`（库 `mor_platform_v2e2e`，块 147 已激活，网关 routeCount 582）；payout `127.0.0.1:18117`（管理 19117，库 `mor_payout`）；account 18114；ops-bff 18201；client-bff 18202；Vault、MySQL `mor-local-mysql-1`、Redis、RabbitMQ、Mailpit 本机容器。本机没有 SES 发送账号：通知只能验证到 Platform 受理与投递记录（不会真正发信），在报告中写明证据边界。
- 可复用的脚本与经验：`requirements/20260914/client-console-注册与客户入驻/verification/local-e2e/`（`e2e_http.py`、`bootstrap_admin.py`、`team_flow.py`、`team_browser_accounts.py` 等登录 / 会话 / Client 团队成员流程）、`requirements/20261029/platform-support-联合查询/verification/stage5*`、`requirements/20261029/tax-结账算税与事件登记/release/acceptance/`。运营管理员账号与本机凭据在本机环境变量 / `local-environment/e2e/secrets.env` 与 `local-environment/.env.local`，只读取不打印。
- 合同：`requirements/20261030/payout-收款账户与余额解释/technique.md` 第 8 节（含 8.7 阶段 4 修订）；验收标准 `requirement.md` AC-01—AC-10。
- 脚本写到 `requirements/20261030/payout-收款账户与余额解释/verification/local-e2e/`（Python 3，`python3 -I` 运行，不写密钥；合成数据用 `S1E2E_` 前缀便于识别），报告写 `…/verification/stage5-backend-report.md`。

## 场景（全部经真实入口）

1. **准备：** 运营侧需要两个不同的运营账号（Maker、Checker）都持有 `PAYOUT_BENEFICIARY_*` 权限（平台管理员角色已在块 147 激活中授予；若只有一个管理员账号，经 Platform 运营管理接口新建第二个并授予管理员角色，记录其 ID）。Client 侧找一个已开通（ACTIVE）的 Client 工作区和一个 TENANT_OWNER 或 TENANT_ADMIN 成员（没有就按入驻脚本建一个）；为该 Client 在 `mor_account` 准备 OE × 币种余额只允许经正式事件或现有测试工具，**不得直接写 account 表**——如无法造余额，余额解释场景验证"无可用余额"原因与空 positions 即可，并在报告中说明。
2. **Client 提交（AC-01 / 02）：** 经 18443 `/api/client/payout/v1/beneficiary-requirements` 取字段，提交 AE 本地 USD 与 SWIFT 各 1；同账号重复 409；不支持组合 422 / 400；核对 `mor_payout` 无原值、Vault 有两类 Token。
3. **运营核验（AC-03 / 07）：** 18444 队列 → 详情（`sharedOwners`、法定名聚合）→ Reveal（审计事件）→ Maker 意见（带 note）→ 同一人 Checker 403 `FOUR_EYES_REQUIRED` → 另一人批准 → `cooling_until` = 决定 + 24h；另一条走拒绝（`clientNote`）。
4. **冷却与余额解释（AC-04 / 08）：** Client `/balances` 显示冷却原因；运营 `/payout-admin/v1/balances`。
5. **默认 / 停用 / 恢复 / 关闭（AC-05 / 06）。**
6. **通知（AC-09）：** 核验通过 / 拒绝 / 停用 / 关闭后，`mor_payout.t_compensation_event` 处理完成；`mor_platform_v2e2e.t_notification_request`（source_system=payout-service）与 `t_notification_delivery` 行数等于该 Client 持 `CLIENT_PAYOUT_ACCOUNT_MANAGE` 的有效已验证成员数；模板参数无原值。
7. **权限与边界（AC-10、R10）：** 无 `CLIENT_PAYOUT_ACCOUNT_MANAGE` 的成员提交 403；未登录 401；运营无权限账号 403（如可造）。
8. **他方回归（必须）：** 经 18444 GET REQ-04 限额档位列表（路径在 `/Users/kenny/Desktop/Workspace/Code-MoR-rq04` 的 ops-bff 控制器中查，`/risk-limit-tiers…`）返回 200；联合查询（data-query 键类型列表或预览）200；多语言发布页接口 200。
9. **清理：** 验收完成后，主会话还要用这些数据做浏览器检查——**不要清理**，在报告末尾列出全部合成数据的 ID 与清理 SQL（只读草稿，不执行）。

## 约束

不重启 / 停止任何进程；不改代码；不碰 mor-test；不打印密钥；只读查询可用 `docker exec mor-local-mysql-1 mysql`（root 密码从 `.env.local` 读入变量，避免任何会把变量展开到输出的命令）。前台执行。完成后回复一句摘要与报告路径。
