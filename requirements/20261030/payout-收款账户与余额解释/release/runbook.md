# payout S1 · mor-test 发布 runbook（阶段 7 用）

> 阶段 7 须用户明确授权后由主会话执行。数据侧命令细节见 [W9-report.md](./W9-report.md) 第 9 节（D1—D10，含公共变量 `REQ` / `TP` / `sqlr` / `sqlro` / `gwlog`），容器动作见 [W8-report.md](./W8-report.md) 第 6 节。任何一步不符合预期就停在该步，按"回滚"列处理。
> 工作目录：`/Users/kenny/Desktop/Workspace/Code-MoR`；`prepare.py`、`initialize.py`、`scripts/xxl_payout.py`、`scripts/source_drift_check.sh`、`scripts/env_check.sh` 一律用候选树 `/Users/kenny/Desktop/Workspace/Code-MoR-po-rel` 中的版本。

## 1. 硬性顺序

1. 提供方先于消费方：account、Platform 候选先上，payout 再起，两个 BFF 在 payout 之后。
2. Platform 候选（`tenantRecipients` + `notification.http.callers.payout-service`）必须先于 payout 处理任何核验决定，否则通知终态失败、不自动重试（W9 R-1）。
3. 块 147 先以休眠写入，所有服务就位后最后激活。
4. 不用 `initialize.py --apply-service-sql`（`payout-schema.sql` 仍是旧形状，W9 R-2）；DDL 只走 `sql/12`。`compose.sh up` 不加 `--remove-orphans`。

## 2. 步骤

| # | 动作 | 预期 / 停止条件 | 回滚 |
|---|---|---|---|
| 0 | 复核：main 仍为 `9eab368c`；`source_drift_check.sh` NO DRIFT；7 个现网容器镜像 ID 与 [baseline.md](./baseline.md) 一致；`env_check.sh` 通过；与其他会话确认无并行发布、块 147 与语言包下一号空闲 | 任一变化即停，重建候选 | — |
| 1 | D1 备份 `mor_platform`、`mor_vault` | 文件非空、`Dump completed` | — |
| 2 | D2 只读预检（01 / 02 / 03 + `payout_bundles.py`） | 库 / 账号不存在；Vault 版本 21（变化则记录）；块 147 全 0、ACTIVE 路由 652 | 不一致即停 |
| 3 | `prepare.py`（追加 `MOR_PAYOUT_DB_PASSWORD`）→ D3 建库与账号：`sql/10` **或** `initialize.py --create-service-databases`，二选一，只执行一个；之后 `sql/11` 核对 | 授权两行、0 表 | `92` |
| 4 | D4 DDL（`12`、`13`） | 3 表 31 / 13 / 15 列、0 行 | `91` → `92` |
| 5 | D5 Vault DML（`20`、`21`），等 ≥ 10 s | 规则 3、版本 = D2 + 1 且最新 | `90` |
| 6 | D6 块 147 休眠（`30`、`31`） | 计数按 W9 第 9 节；Gateway routeCount 仍 652 | `93` |
| 7 | 替换 account-service（`compose-candidate.account-service.yaml`） | healthy；中断约 35 s | `compose-rollback.account-service.yaml` |
| 8 | 替换 platform-support-service | healthy；日志无 notification callers 绑定错误；中断约 60 s | `compose-rollback.platform-support-service.yaml` |
| 9 | 新起 payout-service（`up -d --no-deps`）→ `xxl_payout.py plan` / `apply` / `trigger` | healthy、Nacos 已注册、XXL 分组与 `compensationEventScan` 存在 | `xxl_payout.py remove`，停止并删除容器（保留库） |
| 10 | 替换 operations-console-bff、client-console-bff | healthy；各中断约 30 s | 各自 rollback 文件 |
| 11 | 替换 operations-console-web | 约 5 s | rollback 文件 |
| 12 | D7 语言包（`payout_bundles.py --apply`） | 新命名空间 payout、223 键、发布号为下一个空闲号 | 以 `9eab368c` 源码重发 |
| 13 | D8 激活块 147（`32`、`31`） | routeCount 671（+19），快照约 1 080 333 B | `33` |
| 14 | D9 验收（API + 浏览器 zh-CN / en × 1440 / 390） | 0 FAIL；AC-09 按 D-3 判定 | 按服务回滚，数据按 cleanup |
| 15 | D10 清理本次验收数据 | 本次账户为 0 | — |

**整体回滚（倒序）：** `33` 停用块 147 → ops-web / 两个 BFF 回滚 → `xxl_payout.py remove`、删除 payout 容器（保留库）→ Platform 回滚 → account 回滚 → `93`（有通知请求时拒绝，则保留休眠）→ `90`（有 PAYOUT 记录时只停用规则）→ `91` / `92`（有数据时保留库）。
