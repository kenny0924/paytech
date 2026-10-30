# payout 收款账户与余额解释 · 阶段 5 本机后端验收报告（W6）

- 日期：2026-10-30（UTC 10:20—10:42）；执行：W6（只读核对 + 真实入口调用；未启停进程、未改代码、未碰 mor-test、未写 account 表）
- 入口：Client `https://127.0.0.1:18443/api/client/payout/v1/**`、运营 `https://127.0.0.1:18444/api/operations/payout-admin/v1/**`（Nginx → Gateway → BFF → payout）
- 脚本：[local-e2e/stage5_backend.py](./local-e2e/stage5_backend.py)（`python3 -I`；`--balances` 只重跑余额解释，`--notify <UTC>` 只复核通知）、只读库工具 [local-e2e/rodb.py](./local-e2e/rodb.py)
- 结果：[local-e2e/stage5-backend-result.json](./local-e2e/stage5-backend-result.json)（最终一轮 run-7），各轮输出 `local-e2e/run-1.log`—`run-7.log`
- 凭据：新建运营账号与 Client 成员口令只在 `~/Code-MoR-backups/payout-s1-stage5/state.json`（0600，仓库外）；报告与日志不含口令、令牌、原值

## 1. 结论

最终一轮（run-7，payout pid 14254，库 `mor_payout_v2e2e`）**79 PASS / 5 FAIL**：

| 验收 | 结果 | 说明 |
|---|---|---|
| AC-01 字段清单与校验、库内无原值、Vault 两类 Token | 通过（14/14） | |
| AC-02 同 Owner 查重 / 跨 Owner 共用提示 | 通过，1 项合同偏差 | 409 不含已有账户 ID（见 3.4） |
| AC-03 Maker / Checker 四眼、冷却 24h、拒绝说明 | 通过（13/13） | |
| AC-04 冷却中显示与阻断原因 | 通过 | 到期后实时判定只在 auto 测试覆盖（本机不能等 24h） |
| AC-05 默认唯一 | 通过（6/6） | |
| AC-06 停用 / 恢复重核 / 关闭 | 通过（7/7） | 冻结字段修改：无修改接口，不适用 |
| AC-07 Reveal 与审计、无权限 403 | 通过（4/4） | |
| AC-08 余额解释 | 通过（5/5），RECOVERY_OUTSTANDING 未触发 | 见证据边界 |
| AC-09 通知 | **本机未验证到 Platform 受理**（4 FAIL） | 本机 payout 模板无启用的邮件路由；见 3.5 |
| AC-10 幂等与版本冲突 | 通过（5/5） | "跨服务调用在事务外"由 auto 测试断言 |
| R10 权限与边界 | 通过（5/5） | |
| 他方回归（REQ-04、联合查询、多语言） | 通过（4/4） | |

中途发现并由主会话修复 3 个缺陷 + 2 个环境问题（第 3 节），最终一轮在修复后的进程上执行。

## 2. 场景与证据（run-7）

**准备**
- Maker = E2E 管理员 `236033080844881920`（PLATFORM_ADMIN）；Checker = 新建运营 `241869362766548992`（经 `/iam/v1/accounts` 授予 PLATFORM_ADMIN `900000000000000510`）；无权限运营 `241869370471485440`（自定义角色 `241869367774547968`，只有 DATA_QUERY_READ）。
- Client A `241533217348268033`（所有者账号 `241533082010652672`，TENANT_OWNER）、Client B `241533316505808897`（所有者 `241533126336057344`）：REQ-12 阶段 5 已批准的 ACTIVE Client，凭据取自 `~/Code-MoR-backups/rq12-stage5/accounts.json`。
- Client A 新增 TENANT_VIEWER 成员 `241869378344194048`（member `241869379812200448`，邀请 `241869377308200960`）：经 `/team/v1/invitations` 真实入口，邀请与注册链接按既有夹具在 Platform 认证挑战上铸造已知秘密（本机无 SES）。
- 余额：billing 计费事实 MANUAL_ADJUSTMENT（Maker 管理员、Checker 新运营）→ 正式 `BillingFactRecorded` → account 明细账：A `241872762900099072` 250 USD、B `241872779794755584` 80 USD；account 入账为 `CLIENT_PAYABLE / PENDING`（映射规则固定 PENDING，到期转可用由 XXL-Job 完成，本机未运行，因此 AVAILABLE = 0）。

**AC-01 / 02 Client 提交**：字段清单 LOCAL×AE×USD = holderName / iban / bankName，SWIFT×GB×USD 走 `*` 条目，LOCAL×EG×USD 404；AE 本地与 SWIFT 各 1 条 201 PENDING_REVIEW（户名脱敏 `S**** T****** L**`、末四位一致）；不支持组合 422 `BENEFICIARY_UNSUPPORTED_ROUTE`；IBAN 格式 / 清单外字段 / 缺必填 400 `INVALID_REQUEST`；railType 非法 422 `VALIDATION_ERROR`；同内容新键 200 返回原账户；同账号不同户名 409 `BENEFICIARY_DUPLICATE`；Client B 共用同账号 201，两条记录的 STABLE Token 相同；`mor_payout_v2e2e` 三表检索户名 / IBAN 原值 0 命中；Vault 两类 Token（PAYOUT_BENEFICIARY_V1 REFERENCE、PAYOUT_BANK_ACCOUNT_KEY_V1 STABLE）。

**AC-03 / 07 运营核验**：Maker 队列含新账户；队列与详情 `sharedOwners` 列出 B 的 `BA-0002`，`sharedOwnerProfiles` 带 B 法定名（"Layla Bizinfo0613"），Owner 法定名 "Bizinfo Trading 1029120613 LLC"（client-master 聚合）；Reveal 返回原值字段，payout `REVEAL` 事件 + Vault 访问事件（payout-service / REVEAL / PAYOUT_BENEFICIARY_REVIEW / SUCCESS）；Reveal 原因非法 422，无权限运营 Reveal / 读队列 403 `OPERATIONS_PERMISSION_DENIED`；无 Maker 意见时 Checker 409；Maker APPROVE 带 note（详情与库内可见；命令响应里 `reviewMakerNote` 为空，见 3.4）；重复 Maker 409；同一人 Checker 403 `FOUR_EYES_REQUIRED`；contentHash 不一致 409 `CONCURRENT_REQUEST`；版本过期 409；另一人批准 → VERIFIED，`decisionTime` 10:38:46.200741Z、`coolingUntil` 次日同刻（库内差 86400 秒）；Maker 建议拒绝时 Checker 批准 409，拒绝 → REJECTED `HOLDER_NAME_MISMATCH`，`reasonNote` 沿用 Maker 的 clientNote。

**AC-04 / 08 冷却与余额解释**：Client 详情 VERIFIED、`usable=false`、`coolingUntil` 有值；Client A `/balances` AE001×USD `pending 250.00 / available 0.00 / locked 0.00 / securityDeposit 0.00 / recoveryReceivable 0.00`，原因 `[NO_AVAILABLE_BALANCE, ACCOUNT_COOLING]`，USD 可用账户 0；Client B 原因 `[NO_AVAILABLE_BALANCE, NO_USABLE_ACCOUNT]`（只有待核验账户）；运营 `/payout-admin/v1/balances` 与 Client 视图逐字段一致；第三个 ACTIVE Client `241533481480368129`（无 account 账户）返回空 positions、200。

**AC-05 / 06 生命周期**：REJECTED / CLOSED 设默认 409；冷却中的 VERIFIED 可设默认；第二个 USD VERIFIED 账户（SA 本地）设默认后旧默认取消，库内 `is_default=1` 仅 1 行；停用默认账户后该币种无默认；停用原因非法 400；恢复 → PENDING_REVIEW 并清 Maker / Checker / 冷却，重核后新 `coolingUntil`；Client 关闭 OWNER_REQUEST、再关闭 409；运营关闭待核验账户（OPS）；事件时间线 SUBMIT → MAKER_REVIEW → CHECKER_DECIDE → SET_DEFAULT → SUSPEND → RESUME → MAKER_REVIEW → CHECKER_DECIDE → CLOSE。

**AC-10 / R10**：同 Idempotency-Key 重放返回原账户（201 → 200）；已是默认再设默认为幂等无操作（200、版本不变，不校验旧版本号）；停用 / Checker 版本过期 409；TENANT_VIEWER 提交与关闭 403 `CLIENT_PERMISSION_DENIED`、可读列表；未登录 401 `GATEWAY_UNAUTHORIZED`。

**回归**：REQ-04 `GET /risk-limit-tiers/v1/client-tiers` 200（6 条）、`/tier-quotas` 200；联合查询 `GET /data-query-admin/v1/key-types` 200（27）；多语言 `GET /localization-admin/v1/applications/operations-console/releases` 200（20）。

## 3. 发现

### 3.1 缺陷：payout → account 走服务发现，account 不注册（已修）
payout `spring.http.serviceclient.account-service.base-url: lb://account-service`，account 没有 Nacos discovery 依赖，余额解释 503（payout 日志 `No servers available for service: account-service`）。主会话改为 `${PAYOUT_ACCOUNT_SERVICE_URL:http://127.0.0.1:18114}`（与 ledger 相同），重建 JAR 并重启（pid 2832 起）。修复后 Client / 运营 `/balances` 200。mor-test 需要同一修复。

### 3.2 缺陷：payout → Platform 基址缺 context path（已修）
`lb://platform-support-service` 缺 `/platform-support`，通知 POST `/notifications/v1/requests` 404 → 补偿事件 ERROR `BENEFICIARY_NOTIFICATION_UNAVAILABLE` 重试（探测：错误路径 404，正确路径空体 422）。card / acquiring 用 `lb://platform-support-service/platform-support`。主会话修复后重启（pid 13623 → 14254）。auto 测试未发现，说明其 Platform 替身端点不含 context path。

### 3.3 环境：本机 `mor_account_v2e2e` 未执行 2026-10-19 账户号变更（已修）
缺 `t_owner_account.account_no` 与 `t_owner_account_no_sequence`，account 每次入账 `Unknown column 'account_no'`，前两条事实进入 `account.billing.dlq`。主会话已执行 `paytech/account/docs/database/sql/changes/2026-10-19-owner-account-no.sql`（表为空、先备份），未重放 DLQ；之后新建的两条事实正常入账。

### 3.4 合同偏差（未修，待决）
- **409 不含已有账户 ID**：technique P1 写"409 `BENEFICIARY_DUPLICATE`（含已有账户 ID）"；payout 只把 ID 写进内部异常消息，对外 Problem `detail` 为通用文案"已有相同账号的收款账户"。需决定改实现（Problem 扩展字段）还是改合同。
- **Maker 命令响应不带 `reviewMakerNote`**：详情 / 队列 / 库内都有，命令响应为空；前端若用命令响应刷新会丢备注（低）。
- **设默认对已默认账户不校验 `expectedVersion`**：幂等无操作，返回当前视图（可接受，记录在案）。
- **Platform 拒绝即终态**：payout 把 Platform 业务拒绝（如路由未启用）记为 FAILED `BENEFICIARY_NOTIFICATION_REJECTED` 不再重试；配置修好后这些通知不会补发。

### 3.5 AC-09 证据边界
本机 Platform 的 4 个 payout 模板（及其他模板）均无 ACTIVE `t_notification_route`（无 SES 发送账号，块 147 激活时路由 0），Platform `NotificationConfigurationServiceImpl` 抛"邮件路由未启用"，请求在落库前被拒：`t_notification_request` 无任何 payout-service 行，补偿事件 7 条全部 FAILED `BENEFICIARY_NOTIFICATION_REJECTED`（核验通过 ×3、拒绝、停用、关闭 ×2）。已证实：通知在业务事务提交后由补偿任务执行、确实调用 Platform（修复 3.2 后）、失败不影响账户状态。**未证实**：Platform 受理记录、投递数 = 持 `CLIENT_PAYOUT_ACCOUNT_MANAGE` 的有效已验证成员数（Client A 期望 1：所有者 1 人，VIEWER 不计）、投递地址为邮箱 Token、模板参数无原值——结果文件里"模板参数无原值""Client B 无通知"两项 PASS 是空集上的结论，不作为证据。需在有路由的环境（mor-test 邮件沙箱）复验，可用 `--notify <UTC>` 复核。

### 3.6 环境：payout auto 测试与在用 e2e 服务共用库
10:33Z 主会话在 `Code-MoR-po-e2e` 跑 payout auto 测试时，测试清理清空了当时 e2e payout 在用的 `mor_payout`，run-2—run-4 的账户全部消失（run-4 刚创建的账户即 404）。主会话随后把 e2e payout 改到 `mor_payout_v2e2e`。**需修复**：payout auto 测试应使用独立库（如 `mor_payout_test`），不与任何在用服务共用。
**Vault 实际观察**：按创建时间统计 `mor_vault.t_vault_record`，10:26（7 条）、10:28（10 条）、10:34（8 条）、10:38 的 PAYOUT_BENEFICIARY_V1 / PAYOUT_BANK_ACCOUNT_KEY_V1 记录都仍在——测试清理**没有**删除 Vault 记录，只删除了 payout 行；早期轮次的 Vault 记录因此成为无 payout 引用的孤儿记录（无法按 Token 逐条对应，因为引用它们的 payout 行已被删除；10:34 批次可能混有测试用例自身的记录）。

### 3.7 其他证据边界
- 计费事实没有走运营网关：本机 Platform 未登记 `/finance/billing-facts/v1` 路由（404 `GATEWAY_ROUTE_NOT_FOUND`），按 ops-bff `FinanceBillingFactClientImpl` 的转发方式直连 billing 私网 `/billing/v1/facts`（sourceSystem=OPERATIONS、真实运营账号作编制人 / 复核人）；billing 处理器、批准、补偿发布与 account 消费均为生产链路。
- RECOVERY_OUTSTANDING：account 映射规则没有 Client 追偿应收组件，无法经正式入口造数据；由 payout auto 测试覆盖。
- AVAILABLE 余额：手工调整入 PENDING，到期转可用依赖 XXL-Job `accountBalanceMaturity`（本机未调度），所以"有可用余额、无阻断"路径未在本机验证。
- 中途 run-5 因 payout 第二次重启（10:37:42Z）503 中断，run-6 因库切换导致库内核对落空中断；只以 run-7 为结论。

## 4. 合成数据（保留供浏览器核对，未清理）

| 类别 | ID / 说明 |
|---|---|
| 运营账号 | Checker `241869362766548992`（s1e2e-checker-1030142229@example.test）、无权限 `241869370471485440`（s1e2e-noperm-…）、自定义角色 `241869367774547968`（S1E2E_NOPERM_*） |
| Client 成员 | Client A VIEWER 账号 `241869378344194048`、member `241869379812200448`、邀请 `241869377308200960` |
| 收款账户（`mor_payout_v2e2e`，run-7） | A：`241873440762589184` BA-0004 AE 本地 VERIFIED 默认（冷却至 2026-10-31T10:38:46Z）、`241873441081356288` BA-0005 SWIFT REJECTED、`241873441366568960` BA-0006 SWIFT CLOSED（OPS）、`241873463546048512` BA-0007 SA 本地 CLOSED（全生命周期）；B：`241873443329503232` BA-0002 共用 A 的 IBAN，待 Checker |
| 收款账户（run-6 残留） | A：`241873284461850624` BA-0001、`241873291814465536` BA-0002、`241873292779155456` BA-0003；B：`241873296881184768` BA-0001（均 PENDING_REVIEW，无 Maker 意见） |
| payout 补偿事件 | `mor_payout_v2e2e.t_compensation_event` 7 条 FAILED |
| 计费事实（`mor_billing_v2e2e`） | 已入账：`241872762900099072`（A 250 USD）、`241872779794755584`（B 80 USD） |
| 计费事实（死信） | `241869769920262144`（A 250）、`241870036384395264`（B 80）：billing APPROVED 但从未入账 account（在 `account.billing.dlq`）；保留，或经 billing 更正作废 |
| account 明细账 | `mor_account_v2e2e.t_owner_account` A、B 各一行 AE001 / USD / CLIENT_PAYABLE / PENDING，及对应批次、流水、处理记录 |
| Vault | 10:26 起 PAYOUT_* 记录（含早期轮次孤儿记录） |
| Platform | Vault 访问事件、认证挑战（邀请 / 注册铸造）；无通知请求 |

清理 SQL 草稿（**只读草稿，未执行**；执行前需授权并先备份；account / billing 数据建议以业务更正代替删除）：

```sql
-- payout（mor_payout_v2e2e）：本次全部为 S1E2E 数据
DELETE FROM mor_payout_v2e2e.t_beneficiary_account_event
 WHERE beneficiary_account_id IN (SELECT id FROM mor_payout_v2e2e.t_beneficiary_account
                                   WHERE owner_type='CLIENT' AND owner_id IN (241533217348268033, 241533316505808897));
DELETE FROM mor_payout_v2e2e.t_compensation_event WHERE event_type='BENEFICIARY_NOTIFICATION' AND create_time >= '2026-10-30 10:30:00';
DELETE FROM mor_payout_v2e2e.t_beneficiary_account WHERE owner_type='CLIENT' AND owner_id IN (241533217348268033, 241533316505808897);

-- Vault（mor_vault）：payout 数据类型、今日创建（含孤儿记录；核对没有其他会话的同类型记录后再执行）
DELETE FROM mor_vault.t_vault_record WHERE data_type IN ('PAYOUT_BENEFICIARY_V1','PAYOUT_BANK_ACCOUNT_KEY_V1') AND create_time >= '2026-10-30 10:20:00';

-- Platform（mor_platform_v2e2e）：运营账号与角色、Client 成员（建议经运营 IAM / 团队接口停用而非删除）
--   运营账号 241869362766548992、241869370471485440；角色 241869367774547968
--   Client A 成员 241869379812200448（账号 241869378344194048，邀请 241869377308200960）

-- billing / account：不建议物理删除；以 billing 反向 MANUAL_ADJUSTMENT（REVERSAL）冲回 241872762900099072、241872779794755584；
-- 死信事实 241869769920262144、241870036384395264 留在 DLQ 或经 billing 更正作废，不要重放。
```
