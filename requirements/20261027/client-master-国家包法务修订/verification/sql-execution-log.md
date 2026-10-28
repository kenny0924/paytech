# SQL 执行记录：client-master 国家包法务修订

授权：用户 2026-10-27"继续"（阶段 4 本地测试库，见 delivery.md）。环境：本机 `local-environment` MySQL（容器 `mor-local-mysql-1`，127.0.0.1:13306），root 执行；未触碰 `mor_client_master`、`mor_client_master_v2e2e` 及 mor-test。

| 时间（2026-10-27） | 目标库 | 动作 | 结果 |
|---|---|---|---|
| 19:3x | `mor_client_master_legal`、`mor_client_master_legal_inc` | `CREATE DATABASE`（utf8mb4_0900_ai_ci）；`client_master_app` 授 SELECT/INSERT/UPDATE/DELETE（与 `mor_client_master` 相同） | 成功 |
| 19:3x | `mor_client_master_legal` | worktree `client-master-schema.sql` + `client-master-initial-data.sql`（全新库） | 45 表；Schema 17（RETIRED 8 / ACTIVE 9）、字段 602、规则 91、属性 18、标识类型 10、目录 62 |
| 19:3x | `mor_client_master_legal_inc` | `c4b99e2d` 版 schema + initial-data（模拟线上现状） → `changes/2026-10-27-legal-pack-revision-ddl.sql` → `changes/2026-10-27-legal-pack-revision.sql` | DDL 成功；DML 写入成功，末尾统计查询因别名 `schemas` 为保留字报 1064 → 改为 `schema_count`（两个文件）后重跑：退出 0，数据无变化（幂等） |
| 19:3x | 两库比对 | 列结构（含注释）全部一致；10 张国家包表除 create/update_time 外逐行 md5 一致 | 一致 |
| 19:3x | `mor_client_master_legal_inc` | `changes/2026-10-27-legal-pack-revision-rollback.sql` | 退出 0；回到 v1 ACTIVE 8、字段 275、规则 79、属性 10、标识类型 8、目录 61（与 `c4b99e2d` 初始数据一致）；两列 DDL 保留（按设计） |
| 19:3x | `mor_client_master_legal_inc` | 回退后再次执行增量 DML | 退出 0；字段 602，RETIRED 8 / ACTIVE 9 |

未验证：回退脚本在存在 v2 申请时的 SIGNAL 拒绝分支（需构造申请数据，留阶段 5 业务测试覆盖或说明）。

## 阶段 5：本机 e2e（2026-10-28，用户授权"可以"；参考数据会话与卡 BIN 会话均同意使用）

| 时间（+04） | 目标 | 动作 | 结果 |
|---|---|---|---|
| 00:13 | `mor_client_master_v2e2e` | 备份 `Code-MoR-backups/legal-20261028/local-mor_client_master_v2e2e-pre.sql`（mysqldump，single-transaction） | 成功 |
| 00:14 | 同上 | `changes/2026-10-27-legal-pack-revision-ddl.sql` | 两列存在；旧 client-master 健康 |
| 00:15 | client-master JAR | `verification/local-e2e/swap.py`（`.pre-legal` 备份参考数据会话的 refclient 构建）→ staging/legal-20261028a | ready |
| 00:15 | `mor_client_master_v2e2e` | `changes/2026-10-27-legal-pack-revision.sql` | 退出 0；RETIRED 8 / ACTIVE 9，字段 602，规则 91 |
| 00:20 | client-master、client-bff、ops-bff | 根 reactor `-am` 重建后的 JAR（sha256 81f706b7… / 45aa5ed5… / af96f767…）；ops-bff 原 JAR 属卡 BIN 会话（`.pre-legal`） | 均 ready |
| 00:21 | vite 18302 / 18301 | 停参考数据会话的两个 vite（`Code-MoR-refdata/{client,operations}-console-web`），在原端口启动 `Code-MoR-legal` 的；nginx 未改 | 18443 / 18444 返回 200 |

恢复计划：三个 JAR 换回 `.pre-legal` 并按捕获参数重启；两个 vite 按 `scratchpad/legal-e2e/vite-original.txt` 在原目录重启；`_v2e2e` 的 DML 用回退脚本（新列保留，或按需从备份恢复），完成后通知两个会话。

### 阶段 5 本机恢复（2026-10-28 00:50 前后，+04）

| 动作 | 结果 |
|---|---|
| vite 18302 / 18301 换回 `Code-MoR-refdata` 的两个 Web | 原目录、原参数，nginx 18443 / 18444 未改 |
| `mor_client_master_v2e2e` 先导出验收后状态 `local-mor_client_master_v2e2e-post-stage5.sql`，再 DROP / CREATE 并从 `-pre.sql` 还原 | 两列不存在，Schema 回到 ACTIVE 8，申请 18 行（验收期间新建的 5 份申请随还原移除）；`client_master_app` 授权保留 |
| client-master、client-bff、ops-bff 换回 `.pre-legal` 并按捕获参数重启 | 三个 ready，与原文件逐字节一致；`.pre-legal` 已删除，进程参数抓取文件已删除 |
| 未回收 | 本机 Platform 中 5 个 `legal-ui-*@example.test` 测试账号；`mor_compliance_v2e2e` 中案件 240934496831033344（其 client-master 申请已随还原移除，成为孤立测试数据） |

还原用回退脚本不可行：验收期间已有 v2 申请，回退脚本按设计拒绝执行；参考数据会话的 client-master 不认识 `DECLARATION` 规则作用域，必须回到无新规则的状态。

### 补记：阶段 5 浏览器验收期间对 `mor_platform_v2e2e` 的写入

legal-client-fe 为 5 个 `legal-ui-*@example.test` 测试账号完成邮箱验证（本机注册邮件走 SES，Mailpit 收不到），沿用 V2 本机 e2e 脚本 `mint_signup_link`：每个账号执行一次 `UPDATE t_authentication_challenge SET secret_hmac=… WHERE challenge_id=<该账号自己的 PENDING 注册挑战> AND status='PENDING'`，随后经 `POST /auth/v1/email-verifications` 验证。共 5 行，只涉及这 5 个测试账号自己的挑战；未改其他行，未输出口令或令牌。该写入发生在负责人"不要写凭据"的指示之前，事前未在本记录中列出，此处补记。测试账号凭据只在 scratchpad 的 0600 文件中。

## 阶段 6（2026-10-28）

| 时间（+04） | 库 | 动作 | 结果 |
|---|---|---|---|
| 09:30 | mor-test `mor_client_master` | `mysqldump --single-transaction`（只读）→ `Code-MoR-backups/legal-20261028/mortest-mor_client_master-stage6-readonly.sql` | 成功；未写入 |
| 09:3x | 本机 `mor_client_master_legal_mt`（新建，mor-local-mysql-1） | 由上述导出恢复 → DDL → DML → 重跑 DML → 把一个 DRAFT 申请的 `schema_id` 临时指向 v2（仅本临时库）→ 回滚（`STOP` 退出、无写入）→ 改回 → 回滚 → 再执行 DML | 全部按预期；最终与全新建库一致 |
| 09:3x | 本机 `mor_client_master_legal_fresh`（新建） | 发布工作树 `client-master-schema.sql` + `client-master-initial-data.sql` | 6 张配置表与 `_mt` 逐行相同（不含时间戳列） |
| 09:4x | mor-test `mor_client_master` | `release/sql/00-client-master-legal-precheck.sql`，READ ONLY 会话 | 输出见 `release/precheck-mortest-20261028.out` |
| 09:4x | mor-test `mor_platform` | 语言包计划（`release/legal_bundles.py`，READ ONLY 会话）与 2 个 key 的译文查询 | 只读 |

两个临时库保留到阶段 7 结束后删除（不含凭据，`client_master_app` 未授权）。

## 本机遗留清理（2026-10-29，用户"清理一下"）

范围只限阶段 5 浏览器验收产生的数据；按"引用本批 ID 的行"逐轮扩展求闭包，跨全部 `*_v2e2e` 库与 `mor_vault` 复核无其他引用。删除前备份到 `Code-MoR-backups/legal-20261028/local-cleanup-20261028T204919Z/`（逐表 mysqldump + MinIO 对象原始文件，目录权限 700）。每个库一个事务，删除行数与备份逐表相同。

| 库 / 存储 | 删除 |
|---|---|
| `mor_platform_v2e2e` | 5 个 `legal-ui-*@example.test` 账号（含 5 条被改过 `secret_hmac` 的注册挑战）、口令 5、会话 22、刷新令牌代 94、租户 5、角色 5、成员 5、成员角色 5、文件上传 / 对象 / 版本 / 扫描结果各 40、审计事件 109 及属性 689 |
| `mor_compliance_v2e2e` | 孤立案件 240934496831033344 及其 2 个子案件：案件 3、依赖 4、状态变更 3、主体 3、检查 5、要求 10、证据引用 10、筛查运行 9 |
| `mor_vault` | 上述账号邮箱 5 条 + 验收申请的证件号 / 邮箱 / 电话 14 条（由 `local-mor_client_master_v2e2e-post-stage5.sql` 与 `-pre.sql` 比对确认只属于这批申请）：记录 19、保护请求 43、访问事件 303 |
| 本机 MinIO `mor-platform-files` | 40 个对象（桶开启版本控制，已删除全部版本） |
| scratchpad | 测试账号凭据文件 `legal-ui-accounts.json` |
