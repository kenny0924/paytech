# Client Console 团队成员与邀请：mor-test 发布记录（2026-10-17）

> 授权：2026-10-17 用户“继续”确认[发布清单](./release-plan.md)第 7 节：R1 选 A（先本地提交本需求、从该提交构建）、R2—R5。
> 提交：`f7b5500 feat(client-console): 团队成员与邀请`（144 个文件，只含本需求；未推送）。
> 不含：Gateway、Nginx、Risk、Vault、Commerce、Operations 镜像；Compose 配置；其他环境；Git 推送。

## 1. 制品

- 构建：干净 worktree `f7b5500`（基于 `1ba34c8`，包含 test 原 Platform 的来源 `6461091`）；在该 worktree 依次离线 install `paytech-vault-client`、`risk-api`、`paytech-payment-api`、Platform（`-pl platform-support-web -am`）、CCT，再 package Client BFF（JDK 25，`-DskipTests -Djacoco.skip=true`）；健康探针从同一 worktree 编译。Web 在该 worktree `npm ci`、`npm test`（61/61）、`npm run build`（`index-Cx4KM4No.js`）。Java 测试结果见交付记录阶段 4—5，未在构建 worktree 重跑。
- JAR SHA-256 前缀：Platform `14f8beda7b6e53c9`、CCT `a8220e0f23e219b3`、Client BFF `108d5ba2fec4f62b`；制品在 `test-environment/artifacts/client-team-20261017/`。
- 镜像 tag `mor-test/<service>:client-team-20261017`，ID 固定在 [compose-candidate-20261017.yaml](./compose-candidate-20261017.yaml)。

| 服务 | 发布前（回退用） | 发布后 |
|---|---|---|
| platform-support-service | `sha256:5906497424ce…`（`6461091`） | `sha256:58dcc90ba9f8…` |
| client-contract-service | `sha256:ba062888b857…`（`9ae668a`） | `sha256:01ec90654b20…` |
| client-console-bff | `sha256:d66a33c6bea3…`（`9ae668a`） | `sha256:903ecdb9d8d2…` |
| client-console-web | `sha256:48c112d5dda3…`（`9ae668a`） | `sha256:018a3b334492…` |

## 2. 执行（UTC 2026-10-17）

| 时间 | 步骤 | 结果 |
|---|---|---|
| 09:30 | 通知在线会话（SES 会话 code-mor-36 的验收受 IAM 策略阻塞，同意先发布；其余两个会话不使用 test） | 窗口确认 |
| 09:42 | 只读预检，与清单第 2 节一致：`idempotency_mode` 已存在、`t_tenant_invitation` 不存在、`SES_PRIMARY` 存在、固定 ID 与高位区间均未占用、`TENANT_OWNER` 14 个、团队 / 邀请路由 0、路由总数 267 | 通过 |
| 09:42 | 备份 `mor_platform` 11 张表（权限、角色、角色权限、成员授权、通知模板 / 内容 / 路由、Gateway 路由 / Header / Query / Guard）及 4 个容器 inspect、`compose.applications.yaml` → `~/Code-MoR-backups/20261017-client-team-test/20261017T094254Z/`（0700/0600，SHA256SUMS 校验通过；每张表 `--skip-extended-insert` 行数与源表一致） | 完成 |
| 09:43 | 执行 [db/ddl.sql](../db/ddl.sql)、[db/dml.sql](../db/dml.sql)，DML 再执行一次 | 表与 6 个索引存在；权限 2、模板 / 内容 / 路由各 1；14 个工作区各 4 个角色；权限行 OWNER 84、ADMIN 84、DEVELOPER 70、VIEWER 42（= 14 × 6/6/5/3）；第二次执行无新增 |
| 09:44 | 替换 Platform → CCT → Client BFF → Client Web（`compose.sh -f release/compose-candidate-20261017.yaml up -d --no-deps --no-build --pull never --wait <服务>`） | 29 / 18 / 17 / 12 秒 healthy；Platform 保留 `WEBHOOK_ENABLED`、`NOTIFICATION_SES_EVENTS_ENABLED`（SES 会话的工作区未提交配置）与 `SPRING_APPLICATION_JSON`；SES 会话叠加的临时验收变量随替换消失（其已知悉，会在本镜像上自行重启） |
| 09:45—09:56 | `E2E_TARGET=test register_onboarding.py` | T01—T07、I01—I03 创建并启用（ACTIVE 10）；路由总数 267 → 277；其余路由无变化 |
| 09:57—10:05 | 验收（第 3 节） | 全部通过 |
| 10:05 | 通知 SES 会话窗口结束 | — |

## 3. 验收（经正式入口 `https://localhost:28443` / `http://localhost:28080`）

| 脚本 | 结果 |
|---|---|
| `E2E_TARGET=test team_flow.py` | **123/123**：与本机相同的 AC-01—14、16 全部步骤（含两次完整入驻批准、三次注册；邀请链接按邀请 ID 在库内铸造） |
| `E2E_TARGET=test team_browser_accounts.py` + `team_browser.mjs` | **9/9**；唯一 HTTP≥400 为未登录打开链接时的会话恢复 401（预期） |
| `E2E_TARGET=test workspace_flow.py` | 回归 **53/53** |
| `E2E_TARGET=test developer_flow.py` | 回归 **78/78**（首轮在第一步停止：脚本断言所有者能力恰为 4 项开发者能力，本需求为所有者新增 `TEAM_READ/TEAM_MANAGE` 属预期变化；已更新断言后重跑通过，未改产品代码） |
| 邀请邮件 | test SES 账户仍在沙箱，只能发给已验证身份：发往 `@example.test` 验收地址的邀请返回 `emailDelivery=FAILED`（SES 拒绝，原因只记指标）。另向 SES 模拟地址 `success@simulator.amazonses.com` 发起并重发一封邀请，均返回 **SENT**，随后撤销。**真实收件箱送达与点开邮件链接未验证**（需要用户在 SES 已验证的地址） |
| Web | 正式入口返回新包 `index-Cx4KM4No.js` |
| 日志 | Platform、Client BFF、CCT、Gateway、Nginx、Client Web 自 09:43 起：验收邮箱原值 0、模拟地址原值 0、链接 Secret 0、ERROR 0 |

## 4. 回退

按[发布清单](./release-plan.md)第 6 节：先停用 10 条新路由，再按 Web → BFF → CCT → Platform 逆序用第 1 节“发布前”镜像 ID 写覆盖文件替换；新表、角色和邀请数据对旧版本无影响，可保留；必须撤销时按 `db/dml.sql`、`db/ddl.sql` 头部回滚语句执行（先确认无成员持有新角色、无 PENDING 邀请）。备份见第 2 节。

## 5. test 遗留数据（属 test 数据，不清理）

- 账号：`team-owner-*`、`team-invitee-*`、`team-revoked-*`、`team-stranger-*`、`browser-invitee-*`、`workspace-*@example.test` 前缀的验收账号及其已批准申请、Risk 审核任务和 MinIO 验收 PDF。
- 邀请：`t_tenant_invitation` ACCEPTED 3、REVOKED 2（含发往 SES 模拟地址的一封）；对应成员与角色授权（含一名已移除后恢复、一名已移除的成员）。
- 开发者回归新建的凭证与 1 个已停用的 `example.com` Webhook 端点（同 2026-10-16 发布）。
