# Client Console 团队成员与邀请 — 交付记录

## 适用阶段

完整 8 阶段（2026-10-17 随阶段 1 确认）。

## 确认记录

| 阶段 | 日期 | 成果 | 用户原意 | 获准的下一步 |
|---|---|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-17 | [requirement.md](./requirement.md) | “按建议”：范围 F-01—F-09、AC-01—16、UI-01—06 确认；Q1 固定三角色（管理员 / 开发者 / 只读）、Q2 管理员不能动 OWNER 与自己、Q3 无权限成员只读看成员、Q4 邀请 7 天、Q5 只移除可重邀、Q6 沿用能力列表、Q7 降级即时生效 | 阶段 2 现状差异与影响分析 |
| 2. 现状差异与影响分析 | 2026-10-17 | [technique.md](./technique.md) 第 1—5 节 | “继续”：确认差异判断；D1—D7 按建议（CCT 摘要去掉申请人条件、首次开通建角色并回填、角色权限矩阵、列表排除 REVOKED、未登录预览不显示工作区名称、新用户两封邮件、锁键前缀问题记遗留） | 阶段 3 方案、接口与数据设计 |
| 3. 方案、接口与数据设计 | 2026-10-17 | [technique.md](./technique.md) 第 6—10 节 | “继续”：确认设计（4 角色矩阵、邀请生命周期与 20 条上限、Platform `ClientTeamApi` 7 + `ClientInvitationApi` 3、BFF 10 端点、`t_tenant_invitation` DDL、权限 / 模板 / 存量角色 DML、10 条路由、页面线框、研发验证计划） | 阶段 4 研发实现（不含 SQL 执行、路由实际登记、提交、部署） |
| 4. 研发实现 | 2026-10-17 | 见下文“阶段 4 研发结果” | “继续”：接受阶段 4 结果，并授权阶段 5 本机动作 | 阶段 5 本机综合验证 |
| 5. 综合验证与本地验收 | 2026-10-17 | 见下文“阶段 5 本机验证” | “继续”：接受阶段 5 结果 | 阶段 6 test 发布准备（只出清单，不执行） |
| 6. test 发布准备 | 2026-10-17 | [release/release-plan.md](./release/release-plan.md) | “继续”：按建议，R1 选 A（先本地提交本需求、从该提交构建），授权 R2—R5 | 阶段 7 test 部署与环境验收 |
| 7. test 部署与环境验收 | 2026-10-17 | [release/release.md](./release/release.md) | “可以”：接受 test 验收结果，按清单本地提交归档文件（不推送） | 阶段 8 提交与归档 |
| 8. 代码提交与交付归档 | 2026-10-17 | 见下文“阶段 8 交付归档”；提交 `f7b5500`、`1c04aba`（用户已推送） | “可以，结束吧”：确认本次交付结束 | 无；后续要求另行登记 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 本机 `mor_platform_e2e` 执行 db/ddl.sql、db/dml.sql；本机路由登记与启用；替换本机全链路 Platform / CCT / Client BFF / Client Web 进程 | 2026-10-17 用户“继续”授权（阶段 5） |
| 本地提交（只含本需求文件，不推送）；test `mor_platform` 备份后执行 db/ddl.sql、db/dml.sql；构建并替换 test Platform / CCT / Client BFF / Client Web；test 新建并启用 10 条路由；test 写入验收数据不清理 | 2026-10-17 用户“继续”授权（阶段 6 R1=A、R2—R5） |
| 推送 | `f7b5500`、`1c04aba` 已由用户推送（2026-10-17） |

## 当前阶段与下一确认点

阶段 7 已完成（2026-10-17）：提交 `f7b5500`（未推送），mor-test 已部署，10 条路由已启用；test `team_flow.py` 123/123、浏览器 9/9、回归 `workspace_flow.py` 53/53、`developer_flow.py` 78/78。见 [发布记录](./release/release.md)。阶段 8 已完成（2026-10-17 用户确认结束）。

## 实现 / 验证 / 部署状态

见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-team-20261017)。

## 阶段 4 研发结果（2026-10-17，未提交）

| 系统 | 结果 | 验证（本会话复跑） |
|---|---|---|
| Platform | `ClientTeamApi`（7）、`ClientInvitationApi`（3）及 Controller / 9 个 Processor；`t_tenant_invitation` DataObject / Mapper / Repository；投影首次 ACTIVE 创建 4 个角色与权限矩阵；挑战用途 `TENANT_INVITATION_ACCEPT`（范围绑定邀请）；邮件按用途分派、模板 `CLIENT_TENANT_INVITATION`；`GET /workspaces` 排除 REVOKED；错误码映射、审计动作（含审计安全码）；Schema、测试 DDL、初始化 DML；需求目录 [db/ddl.sql](./db/ddl.sql)、[db/dml.sql](./db/dml.sql)（在自动测试库 `mor_platform` 用 ROLLBACK 演练，可重复执行） | 新增 `ClientTeamInvitationTest` 10、`ClientInvitationAcceptanceTest` 7、`ClientTeamMemberTest` 6，连同既有 Client 认证 / 工作区 / 开发者 / 邮件测试共 **65/65**；Platform 全量未跑（测试库与其他会话共用） |
| CCT | 工作区摘要去掉申请人条件 | 全量 **52/52**（新增非申请人成员用例） |
| Client BFF | 团队 7、邀请 3 个端点；租户上下文 + 权限拦截；详情聚合 CCT 名称（失败降级为空）；`CLIENT_ACCOUNT_CONTEXT_REQUIRED`；能力 `TEAM_READ/MANAGE`；空 Body、错误映射、日志前缀 | 全量 **76/76**（新增 `ClientTeamHttpAutoTest` 9、`ClientInvitationHttpAutoTest` 10） |
| Client Web | `/workspace/team`、`/invitations/accept`、return-to、`auth.ts` 白名单、`workspace.ts` 单项降级 | `npm test` **61/61**、构建通过 |
| 路由登记计划 | 入驻目录 `db/complete_registration_plan.py` 新增 T01—T07、I01—I03（均 DISABLED），重新生成计划与命令模板；既有路由无变化（差异只有新增） | 生成结果核对 |

偏离设计：
1. 工作区被暂停时发起邀请返回 401 `CLIENT_SESSION_INVALID`（沿用所有工作区接口的会话重验），不是 403 `CLIENT_WORKSPACE_UNAVAILABLE`；Web 收到 401 会刷新并按 F-07 回到“我的工作区”显示原因。接受邀请时工作区不可用仍为 403。
2. 账号上下文要求由 BFF 拦截（租户上下文调用详情 / 接受返回 403 `CLIENT_ACCOUNT_CONTEXT_REQUIRED`），Platform 本身不强制。
3. 20 条上限不加锁，极端并发可能多 1 条（设计即“发起邀请不加锁”）。
4. 重发在持有邀请锁期间、数据库事务外发信。
5. 新角色缺失时（DML 未执行的存量工作区）发起邀请返回 500；发布顺序必须先执行 DML。

未覆盖：新用户“先注册再接受”（AC-06）在 Platform 测试中以直接造账号代替，留待阶段 5 全链路；浏览器验收（UI-01—06）留待阶段 5；BFF metadata-only 日志前缀无自动测试。

## 阶段 5 本机验证（2026-10-17 12:20—12:55，待用户确认）

- **制品**：从当前工作区（HEAD `14381f9` 之上未提交改动，含其他会话已提交的 `b0c8b0e` Gateway 幂等、`762b785` SES 事件）`./mvnw -o -DskipTests -Djacoco.skip=true package` 构建 Platform、CCT、Client BFF，复制到会话 scratchpad `team-e2e-jars/` 后经 `run-service.sh`（`E2E_JAR_DIR`）启动；Client Web 为本机 Vite 开发服务（源码实时）。替换前通知了在线会话：原 Platform（pid 94841）属 SES 会话，其验收已结束并同意替换。
- **本机 SQL（`mor_platform_e2e`）**：[db/ddl.sql](./db/ddl.sql) 建表；[db/dml.sql](./db/dml.sql) 执行两次（第二次无新增）：权限码 2、模板 1、20 个已开通工作区各 4 个角色，权限 OWNER/ADMIN 各 120、DEVELOPER 100、VIEWER 60 行。本机没有 `SES_PRIMARY` 发送账号，邀请模板路由 0 行（邮件发送返回 FAILED，属本机预期）。首次执行 DML 在 mysql 命令行报 1267（`CAST(... AS CHAR)` 取连接排序规则 `utf8mb4_general_ci`），整段事务未提交；已在 DML 头部加 `SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci` 后重新执行。
- **额外授权的本机 SQL**：新 Platform 含 Gateway 幂等改动，读取 `t_gateway_route.idempotency_mode`，本机缺列导致 Gateway 管理接口 500。用户 2026-10-17 授权后，在 `mor_platform_e2e` 执行 `platform-support-service/docs/database/sql/changes/2026-10-17-gateway-route-idempotency-mode.sql`（预检列不存在；214 条路由取默认 NONE）。该 SQL 属 Gateway 幂等需求，原会话已退出，这里记录本机执行事实；mor-test 未动。
- **本机路由**：`register_onboarding.py` 新建并启用 T01—T07、I01—I03 共 10 条，其余路由无变化。
- **API 验收** `verification/local-e2e/team_flow.py`：**123/123 PASS**（含两次完整入驻批准与三次注册）。覆盖 AC-01—14、AC-16：发起 / 重复 / 已是成员、重发后旧链接失效、公开预览字段、新用户注册后接受、非申请人看到工作区名称、重复接受幂等、邮箱不一致、开发者权限与团队只读、降级立即生效、所有者与自己受保护、撤销后预览显示已撤销且不能接受、移除后 401 → 刷新降级 `MEMBER_INACTIVE` → 列表消失、重新邀请恢复原成员（memberId 相同、无重复）、跨工作区 404、账号上下文访问团队 403。日志检查：Platform / BFF / CCT / Gateway 日志中验收邮箱原值与链接 Secret 均 0；Platform 的 ERROR 全部在 12:28—12:31 缺列期间，加列后为 0。
- **浏览器验收** `verification/local-e2e/team_browser.mjs`（前置账号 `team_browser_accounts.py`，无头 Chromium）：**9/9 PASS**——所有者团队页、邀请弹窗（本机显示“邀请已创建，邮件未发出，可稍后重发”）、邀请区、390px 无横向溢出且操作收进“更多”、未登录打开链接（只显示角色 / 脱敏邮箱 / 到期，地址栏不留 Secret）、登录后自动回到接受页并显示工作区名称、接受后进入工作区、开发者只看成员无邀请区与操作、所有者行内改为只读成员、二次确认移除。唯一的 HTTP≥400 是受邀人未登录打开链接时 Web 恢复会话的刷新 401（尚无 Cookie，属预期）。截图在会话 scratchpad `team-browser/shots/`。
- **脚本修正**：两处预期写错已改正（邮箱不一致应在被接受前验证；撤销后预览按合同返回 200 + REVOKED）；浏览器脚本两处选择器歧义（所有者与受邀人脱敏邮箱相同、角色文字同时出现在下拉选项中）已改正。均未改产品代码。
- **Platform 全量 auto**（`./mvnw -o test`，本机 `mor_platform`，事先通知在线会话错开）：**759/759**，BUILD SUCCESS。
- **未覆盖**：AC-15 并发接受只在 Platform auto 测试覆盖；新用户在浏览器里走注册页再回接受页（API 级已覆盖注册后接受）；真实邀请邮件送达（本机无 SES）。
- **遗留数据**：本机留有 `team-owner-*`、`team-invitee-*`、`team-revoked-*`、`team-stranger-*`、`browser-invitee-*`、`workspace-*@example.test` 验收账号及其已批准申请、邀请和成员记录。

## 阶段 8 交付归档（2026-10-17，用户已确认结束）

| 项 | 内容 |
|---|---|
| 完成范围 | F-01—F-09；AC-01—14、16 经本机与 test 真实入口验收，AC-15 由 Platform auto 覆盖；UI-01—06 经浏览器验收（桌面 / 390px） |
| 关键决定 | Q1—Q7、D1—D7 均按建议；偏离设计见“阶段 4 研发结果” |
| 代码提交 | `f7b5500`（研发，144 个文件）；`1c04aba`（发布记录、台账、`developer_flow.py` 能力断言）；均已推送 |
| 部署 | mor-test：本需求 DDL / DML、4 个镜像、10 条路由，见[发布记录](./release/release.md)；本机全链路同步（含经授权的 Gateway 幂等加列） |
| 验证 | Platform 759/759、CCT 52/52、BFF 76/76、Web 61/61；本机 `team_flow.py` 123/123、浏览器 9/9；test 同样 123/123、9/9，回归 53/53、78/78 |
| 遗留 | 真实收件箱送达未验证（test SES 沙箱，仅模拟地址 SENT）；新用户在浏览器经注册页回接受页未单独验收；工作区暂停时发起邀请返回 401；20 条上限不加锁；即时邮件失败原因只进指标；投影与认证锁键前缀不一致（D7，既有） |
