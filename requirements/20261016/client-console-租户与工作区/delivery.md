# Client Console 租户与工作区 — 交付记录

## 适用阶段

完整 8 阶段（2026-10-16 随阶段 1 确认）。

## 确认记录

| 阶段 | 日期 | 成果 | 用户原意 | 获准的下一步 |
|---|---|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-16 | [requirement.md](./requirement.md) | “按建议”：范围 F-01—F-07、AC-01—11、UI-01—04 确认；Q1 导航本期写死、Q2 不列 PENDING 工作区、Q3 名称取 CCT 已批准资料、Q4 不自动进入、Q5 SameSite 保持现状 | 阶段 2 现状差异与影响分析 |
| 2. 现状差异与影响分析 | 2026-10-16 | [technique.md](./technique.md) 第 1—5 节 | “继续租户与工作区”：按阶段规则视为确认阶段 2；D1—D6 按建议 | 阶段 3 方案、接口与数据设计 |
| 3. 方案、接口与数据设计 | 2026-10-16 | [technique.md](./technique.md) 第 6—10 节 | “继续用agent开发”：确认设计并要求多 agent 研发 | 阶段 4 研发实现（不含路由实际登记、提交、部署） |
| 4—5. 研发与本机验证 | 2026-10-16 夜 | 见下文 | 用户要求“发布test”，视为接受阶段 4—5 结果 | 阶段 6—7 |
| 6—7. test 发布与验收 | 2026-10-16 | [发布记录](../client-console-接入自助/release/release-20261016.md)（与接入自助合并发布） | “发布test” + “授权提交并部署” | 用户验收、推送授权 |
| 8. 代码提交与交付归档 | 2026-10-17 | 见下文“阶段 8 交付归档”；test 浏览器验收 10/10，提交 `6a55fc2` | “可以”：确认本次交付结束（未授权推送 `6a55fc2`） | 无；后续要求另行登记 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 本地提交、部署 mor-test、test 路由登记 | 2026-10-16 用户“发布test”并选择“授权提交并部署”（不推送；本需求无 SQL） |
| 推送 | `9ae668a` 已推送（2026-10-17）；`6a55fc2` 及之后的归档提交未授权 |

## 当前阶段与下一确认点

阶段 6—7 已完成（2026-10-16）：与接入自助合并，提交 `9ae668a`（2026-10-17 已推送），mor-test 已部署，C02/A05 已更新、W01/W02 已启用，test `workspace_flow.py` 53/53；2026-10-17 补做 test 浏览器验收 `workspace_browser.mjs` 10/10。见[发布记录](../client-console-接入自助/release/release-20261016.md)。

阶段 8 已完成（2026-10-17 用户确认结束）。`6a55fc2` 与本次确认记录为本地提交，推送未授权。

## 阶段 8 交付归档（2026-10-17，用户已确认结束）

| 项 | 内容 |
|---|---|
| 完成范围 | F-01—F-07；AC-01/02/04—11、UI-01—04 经本机与 test 真实入口验收 |
| 关键决定 | Q1—Q5、D1—D6 均按建议（见确认记录）；偏离设计见“阶段 4 研发结果” |
| 代码提交 | `9ae668a`（与接入自助合并，已推送）；验收记录与 `workspace_browser.mjs` 的 test 目标支持另行本地提交 |
| 部署 | mor-test 镜像、路由与回退见[发布记录](../client-console-接入自助/release/release-20261016.md)；本需求无 SQL（同次发布的 `credential_name` / 权限 DML 属接入自助） |
| 验证 | 单测 Platform 651、CCT 51、BFF 47、Web 43；本机 `workspace_flow.py` 53/53、浏览器 10/10；test `workspace_flow.py` 53/53、浏览器 10/10 |
| 遗留 | AC-03（批准后投影未完成窗口）与并发切换 409 未覆盖；Platform 全库覆盖率门禁（既有）；CCT `approved_time` 未写入，`approvedAt` 取申请更新时间；旧 `browser_acceptance.mjs` 登录等待标题可能已失效；`developer_browser.mjs`（接入自助）尚不支持 test |

## 实现 / 验证 / 部署状态

见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md#client-console-workspace-20261016)。

## 阶段 4 研发结果（2026-10-16 夜，未提交）

| 系统 | 结果 | 验证 |
|---|---|---|
| Platform | `ClientAuthenticationApi` 新增 `GET /workspaces`、`POST /workspace-context`、`POST /account-context`；会话写入支持租户上下文；`ClientMember*` 查询；`SwitchClientContextProcessor`（进入与返回共用，锁账号 + 会话）；`ClientAccountPolicy.validContext` 租户重验；刷新降级（`fallbackReason`）；错误码 `CLIENT_WORKSPACE_UNAVAILABLE`；审计动作 | 新增 `ClientWorkspaceContextTest` 4 项；全量测试 651/651 通过，verify 被全库覆盖率门禁拦截（指令 60%、分支 42%，未与改动前对照） |
| CCT | `ClientWorkspaceApi` `GET /client-workspace/v1/client-summaries`；只返回本人申请且 APPROVED 的客户 | 全量 51/51（新增 `ClientSummariesTest` 5 项） |
| Client BFF | C02 / W01 端点、刷新透传 `fallbackReason`、C01 `workspaces` 聚合、W02 工作区首页；登记计划新增 W01/W02、修改 C02/A05 | 全量 47/47（新增 `ClientWorkspaceHttpAutoTest` 12 项） |
| Client Web | `auth.ts` 进入 / 返回 / 降级；开始页“我的工作区”；`/workspace` 侧边栏外壳与首页；结果页入口 | `npm test` 43/43、构建通过 |

偏离设计：进入与返回账号共用一个 Processor；`approvedAt` 取最新 APPROVED 申请的 `update_time`（无专门结论时间列）；准备阶段有效、事务内失效的租户刷新走原失效路径（需重新登录）；并发切换 409 未单测。


## 阶段 5 本机验证（2026-10-16 01:30—02:15，e2e-workspace 执行；待用户确认）

- **制品**：从当前工作区（HEAD `096cb08` 之上未提交改动）`./mvnw -o -DskipTests -Djacoco.skip=true package` 构建 Platform、CCT、Client BFF exec JAR，复制到会话 scratchpad `e2e-artifacts/` 后启动（`run-service.sh` 新增可选 `E2E_JAR_DIR`，不设时行为不变）；Client Web 为本机 Vite 开发服务（源码实时），`npm run build` 通过。
- **本机进程替换**（经 10 分钟通知后放行）：停止另一会话的本机 Platform（pid 64323）及原 CCT、Client BFF，用新制品启动；Vault 实际由 Docker `mor-local-vault-service-1` 提供（健康），原进程式 Vault 不再使用。
- **本机路由**：`sync_route_headers.py`、`register_onboarding.py` 已运行；核对 C02 下游 `/client-auth/v1/workspace-context`、A05 响应 Cookie `ISSUE_OR_CLEAR`、W01、W02 均 ACTIVE。注意：登记计划已含接入自助的 14 条 `client-developer-*` 路由（另一 worker 同时修改计划），本次一并在本机登记并启用，BFF 尚无对应处理器。
- **API 验收** `verification/local-e2e/workspace_flow.py`：**53/53 PASS**（含完整入驻至批准 35 步 + 工作区 18 步）。覆盖 AC-01/02/04/05/06/07/08/09/10/11；AC-03（批准后投影未完成窗口）无法稳定构造，未覆盖。旧账号 Refresh 返回 401（A05 改 `ISSUE_OR_CLEAR` 后不再是非法上游响应）。日志 scratchpad `workspace_flow.log`。
- **浏览器验收** `verification/local-e2e/workspace_browser.mjs`（无头 Chromium 1208）：**10/10 PASS**——开始页工作区、申请结果页“进入工作区”、首页内容与提示（无余额/付款/邀请）、390px 菜单无横向溢出、切换工作区（当前标记）、再次进入、返回账号、受限原因显示、恢复后进入并退出；HTTP≥400 与控制台错误均为 0。截图 scratchpad `browser-ws/shots/`。
- **夹具说明**：本机运营租户访问路由（`operations-tenant-access-*`）为 DISABLED，安全暂停 / 恢复改为直接更新本机 `mor_platform_e2e.t_access_tenant`（只改目标租户一行，版本 +1）；其余链路走真实入口。
- **遗留**：本机留有验收账号 `workspace-*`、`workspace-other-*`、`browser-ws-*@example.test` 及其已批准申请；旧脚本 `browser_acceptance.mjs` 的登录等待标题“我的入驻申请”已因开始页标题改为“我的工作区与申请”而可能失效（未修改）。
