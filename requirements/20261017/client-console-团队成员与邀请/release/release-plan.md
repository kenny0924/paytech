# Client Console 团队成员与邀请：mor-test 发布清单（2026-10-17，阶段 6 评审稿，未执行）

> 本文件只列计划；执行在阶段 7，需用户确认第 7 节授权项。执行结果另写 `release.md`。
> 依据：[技术方案](../technique.md) 第 8、10 节；[交付记录](../delivery.md) 阶段 4、5。

## 1. 范围

| 项 | 内容 |
|---|---|
| Platform | `ClientTeamApi`（7）、`ClientInvitationApi`（3）；投影首次 ACTIVE 建 4 个角色；挑战用途 `TENANT_INVITATION_ACCEPT`；邀请邮件分派；`GET /workspaces` 排除 REVOKED；错误码、审计 |
| CCT | 工作区摘要去掉“申请人”条件 |
| Client BFF | 团队 7、邀请 3 个端点；能力 `TEAM_READ/MANAGE`；`CLIENT_ACCOUNT_CONTEXT_REQUIRED` |
| Client Web | `/workspace/team`、`/invitations/accept`、登录后回到接受页、工作区单项降级 |
| 数据库 `mor_platform` | [db/ddl.sql](../db/ddl.sql)：新建 `t_tenant_invitation`；[db/dml.sql](../db/dml.sql)：权限码 2、邀请模板（内容 + 路由到 `SES_PRIMARY`）、已开通工作区补 3 个角色与权限矩阵 |
| test 路由 | 新增并启用 T01—T07、I01—I03 共 10 条（`client-team-*`、`client-invitation-*`），其余路由不变 |
| 不含 | Gateway、Nginx、Risk、Vault、Commerce、Operations 镜像；Compose 配置；其他环境；Git 推送 |

## 2. test 现状（2026-10-17 只读核对）

| 项 | 结果 |
|---|---|
| Platform 镜像 | `sha256:5906497424ce…`，来自 `6461091`（含 Gateway 幂等 `b0c8b0e`/`69d868e`、SES `762b785`、Risk `2c144d3`）；HEAD `1ba34c8` 之后 Platform 只有本需求的未提交改动 |
| CCT / Client BFF / Client Web 镜像 | `sha256:ba062888b857…` / `sha256:d66a33c6bea3…` / `sha256:48c112d5dda3…`（均来自 `9ae668a`）；之后这三个目录没有其他提交 |
| `t_gateway_route.idempotency_mode` | 已存在（新 Platform 依赖，无需再执行） |
| `t_tenant_invitation` | 不存在 |
| `SES_PRIMARY` 发送账号 | 存在 |
| 本需求固定 ID | 权限 `…058105/106`、模板 `…070013`、内容 `…070023`、路由 `…070033` 均未被占用；高位区间 `≥ 9100000000000000000` 的角色 0 行 |
| 工作区 | 非 PENDING 的 CLIENT 工作区 15 个，其中有 `TENANT_OWNER` 角色的 14 个（DML 只回填这 14 个） |
| 团队 / 邀请路由 | 0 条 |

## 3. 制品

- **代码来源**：工作区中 Platform、CCT、Client BFF、Client Web 的未提交改动全部属于本需求（已逐文件核对共用文件只含本需求段落）。按第 7 节选择：
  - A（建议）：先本地提交本需求（只含本需求文件，私有暂存区），从该提交的干净 worktree 构建；镜像与提交一一对应。
  - B：不提交，从 HEAD `1ba34c8` 的干净 worktree 应用本需求补丁后构建；提交留到阶段 8。
- **构建**：与上次发布相同——临时聚合 POM（`paytech-vault-client`、`risk-api`、`paytech-payment-api`、Platform、CCT、Client BFF）JDK 25 离线 `-DskipTests -Djacoco.skip=true package`；Web `npm test`、`npm run build`。JAR 放 `test-environment/artifacts/client-team-20261017/`，记录 SHA-256。
- **镜像**：tag `mor-test/<service>:client-team-20261017`，ID 写入 `release/compose-candidate-20261017.yaml`；替换使用工作区 `compose.applications.yaml` 的环境变量（不改 Compose）。

## 4. 执行顺序

| 步 | 内容 | 检查 |
|---|---|---|
| 0 | 与在线会话约定窗口（code-mor-36 正在 test 做 SES 验收；其他会话按当时 `ListAgents`）；窗口内其他会话不替换 test 的 Platform / CCT / BFF / Web | 收到回复或说明的等待时间 |
| 1 | 只读预检：重复第 2 节查询，结果须一致 | 不一致则暂停 |
| 2 | 备份 `mor_platform` 相关表：`t_permission`、`t_role`、`t_role_permission`、`t_tenant_member_role`、`t_notification_template`、`t_notification_template_content`、`t_notification_route`、`t_gateway_route` 及其 Header / Guard / Query 子表；4 个容器 `docker inspect` → `~/Code-MoR-backups/20261017-client-team-test/<UTC>/`（0700/0600，SHA256SUMS） | 备份文件可读、行数与源表一致 |
| 3 | 执行 `db/ddl.sql`，再执行 `db/dml.sql`（已固定 `utf8mb4_0900_ai_ci`）；DML 再执行一次确认无新增 | 表与 6 个索引存在；权限 2、模板 1、内容 1、路由 1；14 个工作区各 4 个角色；OWNER/ADMIN 各 6、DEVELOPER 5、VIEWER 3 条权限 |
| 4 | 替换 Platform → CCT → Client BFF → Client Web（`compose.sh -f release/compose-candidate-20261017.yaml up -d --no-deps --no-build --pull never --wait <服务>`） | 每个服务 healthy；Platform 保留 `WEBHOOK_ENABLED`、`SPRING_APPLICATION_JSON` 等现有环境变量；日志无 ERROR |
| 5 | `E2E_TARGET=test register_onboarding.py`：新建并启用 10 条路由 | 10 条 ACTIVE；Gateway Snapshot `routeCount` 增加 10；其余路由版本不变 |
| 6 | 验收（第 5 节） | 全部 PASS |
| 7 | 写 `release.md`；更新交付记录与台账 | — |

## 5. 验收（经正式入口 `https://localhost:28443` / `http://localhost:28080`）

| 脚本 | 期望 |
|---|---|
| `E2E_TARGET=test team_flow.py` | 与本机相同的全部步骤 PASS（AC-01—14、16） |
| `E2E_TARGET=test team_browser_accounts.py` + `team_browser.mjs` | 9/9 PASS；唯一允许的 HTTP≥400 为未登录打开链接时的会话恢复 401 |
| `E2E_TARGET=test workspace_flow.py`、`developer_flow.py` | 回归：仍全部 PASS（上次 53/53、78/78） |
| Web | 正式入口返回新包，含“团队”“接受邀请” |
| 日志 | Platform、Client BFF、Gateway、Nginx、CCT、Client Web 窗口内：验收邮箱原值与链接 Secret 0 条；ERROR 0 条 |
| 邀请邮件 | test 认证邮件此前因 SES 发件身份未配置而失败；若仍失败，`emailDelivery=FAILED`、页面提示可重发，验收脚本按邀请 ID 在库内铸造链接（与入驻验收相同的兜底），并在记录中说明“真实邮件送达未验证” |

## 6. 回退

1. 停用 10 条新路由（先于回退 BFF）。
2. 按 Web → BFF → CCT → Platform 逆序，用第 2 节“发布前”镜像 ID 写覆盖文件后 `compose.sh -f <覆盖> up -d --no-deps --no-build --pull never --wait <服务>`。旧 Platform 不读 `t_tenant_invitation` 和新角色，保留这些数据不影响旧版本。
3. 必须撤销数据时：确认没有成员持有新角色、没有 PENDING 邀请引用新角色后，按 [db/dml.sql](../db/dml.sql) 头部的回滚语句删除新增行；`t_tenant_invitation` 无业务数据时按 [db/ddl.sql](../db/ddl.sql) 头部 `DROP TABLE`。
4. 回退后在 `release.md` 记录原因、执行步骤和回退后验收。

## 7. 待授权项（阶段 6 确认）

| 编号 | 动作 | 说明 |
|---|---|---|
| R1 | 代码来源：A 先本地提交（建议）或 B 不提交、按补丁构建 | A 只提交本需求文件，不推送 |
| R2 | 在 test `mor_platform` 备份后执行 `db/ddl.sql`、`db/dml.sql` | 第 4 节步 2—3 |
| R3 | 构建并替换 test 的 Platform、CCT、Client BFF、Client Web | 第 4 节步 4，约定窗口后执行 |
| R4 | 在 test 新建并启用 10 条路由 | 第 4 节步 5 |
| R5 | 在 test 写入验收数据（新建验收账号、入驻申请、邀请与成员），不清理 | 与以往 test 验收相同，记入 `release.md` 遗留数据 |
