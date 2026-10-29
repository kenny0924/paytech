# REQ-03 阶段 5 · 本机联调报告（后端与 API，2026-10-29 20:26—20:41 +04）

> 只操作本机：`local-environment` MySQL 13306（`mor_risk_decision_v2e2e`、`mor_platform_v2e2e` 块 143、`mor_compliance_v2e2e` 一行测试数据），本机 risk-decision / ops-bff JAR 进程，本机 Gateway 快照；compliance 入驻案件经真实运营 API 审批。未碰 mor-test，未提交、未推送；data-query 数据源未登记（模板只在 `mor_risk_decision_v2e2e` 以只读事务试跑）。**新 JAR 保持运行。** 浏览器走查由 rq03-web 另行进行。
> 方法：真实令牌经 Nginx 18444 → Gateway（块 143 路由）→ ops-bff → client-master / compliance / Platform 参考数据 → risk-decision。脚本、备份、日志在 [stage5/](./stage5/)。

## 1. 结论

| 项 | 结果 |
|---|---|
| `mor_risk_decision_v2e2e` 前滚 DDL + DML | 3 张新表，库内共 57 张表；规则 290001 ACTIVE，阈值 USD 50000（`logs/01`） |
| 注册块 143 插入 → 激活 | 预检全部为 0，依赖的角色 510、菜单 565、目标 1 都在。插入 153 行（DISABLED）后快照照常刷新（routeCount 520）；激活后 **routeCount 532（+12）**，此后每 5 秒刷新成功（`logs/02`、`logs/03`） |
| risk-decision / ops-bff 换 worktree JAR | 换前核对：运行中的 JAR 不比基线新（两者提交均为 e3e0c12f 的祖先，类与依赖列表是本次构建的子集）。readiness 都是 UP，用时各 6 秒；替换后新进程里没有承保相关的 ERROR |
| API 联调 `stage5/stage5_e2e.py` | **全部场景通过**。完整执行在 run1–run3（`logs/20`—`22`），终跑 `logs/23` 结果为 44 PASS / 0 FAIL / 5 SKIP（SKIP 都是"已完成，重跑跳过"）/ 1 INFO。过程中出现的 FAIL 均出自脚本预期写错，已更正后重跑通过，见第 4 节 |
| 输入来源不可用（停本机 compliance） | **未执行**：停共享 e2e 栈中 compliance 的命令被权限分类器拒绝（会影响其他工作负载），没有绕过。改由 ops-bff auto `RiskUnderwritingBrowserAutoTest` 的 SOURCE_UNAVAILABLE / 503 用例覆盖。脚本保留了 `U` 场景（停 → 校验 → 按原 argv / 环境恢复），需要时可由主会话授权后执行 |

## 2. 环境变更清单

| 时间（+04） | 动作 | 备份 / 回滚 |
|---|---|---|
| 20:26:42 | mysqldump 备份 4 个库：`mor_risk_decision_v2e2e` 全库、`mor_platform_v2e2e` IAM 与 Gateway 8 表、`mor_compliance_v2e2e`、`mor_client_master_v2e2e` | `stage5/backup/*-pre-rq03.sql`（目录权限 700） |
| 20:26:51 | 在 `mor_risk_decision_v2e2e` 执行前滚：`paytech/risk-decision/docs/database/sql/changes/2026-10-29-client-underwriting.sql` | `…-client-underwriting-rollback.sql`，须在新 risk-decision 停止后执行 |
| 20:27:08 | 注册块 143 插入：`db/platform/registration-143.sql`，153 行 | `db/platform/registration-143-rollback.sql`（子表先删，并删除引用 1431xx 的角色授权） |
| 20:27:43 | 块 143 激活：`registration-143-activate.sql`，路由 ACTIVE，管理员角色授权 ACTIVE | 同上回滚脚本；回滚后快照自动刷新 |
| 20:29 | JAR 先 `cp -p` 备份，新 JAR 经 `.tmp` + `mv -f` 安装，再按原 argv / 环境重启（`stage5/swap.py`） | `local-environment/e2e/jars/paytech-risk-decision-app-exec.jar.pre-rq03`（10-25 relsnap 版）、`paytech-operations-console-bff-app-exec.jar.pre-rq03`（REQ-05 版）。risk-decision 新 pid **27215**，ops-bff 新 pid **27317**，`e2e/*.pid` 已更新 |
| 20:32—20:34 | 经运营 API 审批 3 个已提交的个人入驻案件（`stage5/prepare_cases.py`）：材料接受、人工检查 PASS、分类确认、CRR、推荐、复核人批准 | 业务数据，不回滚（运营 API 产生，可参照 `backup/mor_compliance_v2e2e-pre-rq03.sql`） |
| 20:32 | 切片 1 脚本复用已存的合成复核人时，登录遇到一次 504（见第 4 节），于是自动新建了一个合成复核人（IAM 角色 + 账号 241600175011794944）。`~/Code-MoR-backups/onboarding-slice1-acceptance/reviewer-local.json` 已改为指向新账号；旧账号 240674286610681856 仍在 | 不需要回滚 |
| 20:34:14 | **测试数据**：`stage5/10-no-crr-test-data.sql` 把案件 240674554333122560（Client 240674482170114049）的 `risk_rating` 由 LOW 置空。原因：真实流程在批准前必须评 CRR，"未评 CRR"无法经 API 构造 | `stage5/10-no-crr-test-data-rollback.sql`（改前快照：`logs/10-no-crr-before.log`） |
| 20:36—20:41 | `stage5_e2e.py` 经运营 API：IAM 新建 3 个角色和账号（第 3 节），并产生 8 个承保版本（第 3 节） | 承保版本可随 risk-decision 回滚 SQL 一并删除；账号可在 IAM 停用 |

**整体回滚顺序**：先恢复 ops-bff 和 risk-decision 的 `.pre-rq03` JAR 并重启（`swap.py install <name> <jar>.pre-rq03` → `swap.py restart <name>`），再执行块 143 回滚，然后在 `mor_risk_decision_v2e2e` 执行 risk-decision 回滚 SQL，最后执行 compliance 测试数据回滚。

## 3. 浏览器走查数据（交给 rq03-web）

**账号**：凭据文件 `~/Code-MoR-backups/rq03-stage5/accounts.json`（0600），不打印。管理员账号见 `local-environment/e2e/secrets.env`。

| 键 | accountId | 权限 | 用途 |
|---|---|---|---|
| `uw`（承保员） | 241601254088773632 | READ + MANAGE | 新建、保存、提交；复核按钮应不可见或返回 403 |
| `lead`（负责人） | 241601257913978880 | READ + MANAGE + APPROVE | 复核；**C 是他本人提交的**，打开时应看到"本人不能复核"的提示 |
| `ro`（只读） | 241601262703874048 | READ | 列表与详情可看；不能写（`canManage=false`） |
| 管理员 | 见 secrets.env | 全部（含块 143） | **C 的复核人视角**：可批准或拒绝 |

**Client 与页面状态**（路由 `/risk/underwriting`）：

| Client | 名称（快照） | 状态 | 页面状态 |
|---|---|---|---|
| 241533217348268033 | Bizinfo Trading 1029120613 LLC | LOW、INSTANT、MCC 5651、USD 50000（恰好等于阈值），**没有承保记录** | 原型 02"新建 · 符合自动承保"：`/risk/underwriting/new?clientId=241533217348268033`，8 项检查全部通过，可一键自动承保（会生成新记录） |
| 241532599334350849 | Layla Bizinfo0319 | MEDIUM、PRE_ORDER 14 天：v1 APPROVED（MANUAL；保证金 5000 USD，阻断收款；T+7；敞口 35000.00 USD、21 天） | 已批准详情（含敞口公式） |
| 240674874987655169 | Acceptance Trading 1027031549 LLC | v1 AUTO_RULE 已作废，v2 APPROVED（MANUAL，修订原因"rq03 stage5：补充 T+N 与保证金"，T+7） | 原型 05"已批准详情 · 版本历史"，可再次修订 |
| 241533316505808897 | Layla Bizinfo0613 | HIGH、MCC 5967（卡组织高风险）：v1 DECLINED（负责人拒绝）；**v2 SUBMITTED，提交人是 lead** | 原型 04"待复核"：管理员可复核，lead 打开时按钮禁用 |
| 241533481480368129 | Layla Bizinfo0613（与上一行同名，是另一个 Client） | HIGH、MCC 5651：v1 APPROVED（uw 提交，lead 复核批准，复核意见"同意，观察期 60 天"） | HIGH 已批准详情，显示提交人和复核人 |
| 240674482170114049 | Acceptance Trading 1027031415 LLC | **CRR 为空**（测试数据），按 MEDIUM 处理：v1 DECLINED，v2 DRAFT（T+5） | 草稿编辑；"未评 CRR"提示 |

统计（U12）此刻为：生效中 3、草稿 1、待复核 1、本月已批准 4、本月已拒绝 2。

## 4. 联调明细与发现

| 场景 | 覆盖 | 结果 |
|---|---|---|
| S 账号 | IAM 建角色和账号、首登改密、登录 | 通过 |
| P 权限（AC-11） | 只读账号：列表、统计、工作台 200（`canManage` / `canSubmit` 为 false）；建草稿、自动承保、预览返回 403。承保员复核返回 403。未声明的查询参数被拒 | 11 / 11 |
| A 自动承保 → 修订（AC-02 / 07 / 10） | 预览 8 项固定顺序、阈值 50000 USD、无履约方式时 `FULFILLMENT_TYPE_MISSING`；U9 生成 AUTO_RULE 版本（规则 v1，创建人为承保员，无提交人 / 决定人）；修订原因为空返回 422；修订产生 DRAFT v2；再次修订返回 409 OPEN_EXISTS；草稿期间旧版本仍生效；批准后 v2 的 `supersedes`=v1，v1 为 SUPERSEDED，历史按版本倒序 | 通过 |
| B MEDIUM 人工（AC-03 / 04 / 05） | 预览敞口：21 天对应 35000.00；T+N 为空时为 14 天、23333.33 并标注；自动承保返回 409；建草稿 / 重复建草稿返回 409；过期版本返回 409；保证金组合与范围校验返回 422（字段级）；保存时冻结敞口；拒绝未填原因返回 422；提交即批准；对 APPROVED 保存或复核返回 409 STATE_INVALID | 通过 |
| C HIGH（AC-06 / 08 / 10） | 改判 MCC 未填原因返回 422；HIGH 提交 DECLINE 返回 409；提交后进入 SUBMITTED；"待我复核"包含他人提交、不含本人提交；复核拒绝未填意见返回 422；拒绝后可重建；本人复核返回 409 SELF_REVIEW | 通过 |
| D HIGH 批准（AC-08） | 复核时版本过期返回 409；负责人批准，显示名与复核意见正确 | 通过 |
| E CRR 为空（AC-09） | 工作台 `crr=null`，按 MEDIUM 处理；自动承保返回 409；草稿 `crrMissing=true`；非 HIGH 可直接拒绝；之后重建草稿 | 通过 |
| L 列表与统计（AC-12） | 统计与库计数一致；总数一致、按更新时间倒序；status / crr / decisionSource / clientId / keyword 筛选；keyword 可按 clientId 精确匹配；列表行带快照列和显示名；非法状态返回 400 | 通过 |
| Q 模板只读试跑（AC-10） | 6 个 Client 与一个不存在的 Client 的 `satisfied` / `missing` 符合四态约定（有效、待复核、已拒绝、未开始） | 7 / 7 |
| G 日志 | 两个服务自重启以来的日志都不含 Client 名称（承保请求只记元数据），也没有承保相关的 ERROR | 通过 |

**脚本预期更正**（运行中出现的 FAIL，都不是实现缺陷）：
- `size=5` 返回 400：分页只接受 10 / 20 / 50 / 100 / 200，脚本改为 10。
- 修订草稿的 `supersedesUnderwritingId` 为 null：列注释写明"本版本**批准后**替代的上一有效版本"，批准时才写入，实测批准后等于 v1。脚本改为在批准后检查。
- 非法 `status` 查询参数返回 **400 INVALID_REQUEST**，脚本原本预期 422。查询参数错误按系统约定返回 400（422 只用于请求体），脚本已改。
- `RISK_UNDERWRITING_AUTO_NOT_ELIGIBLE` 的 `detail` 是该错误码的固定公开文案，不包含未通过的 check code（Problem 只公开错误码文案，check code 只在服务端异常消息里）。**technique.md §8.1 原写法有误，已更正**；页面本来就靠 U11 预览展示原因。
- ops-bff 有一条 ERROR（16:32:48Z，`/auth/**` 登录 504 DEPENDENCY_TIMEOUT，Platform 超过 5 秒）：与承保无关，发生在切片 1 脚本登录合成复核人时。G 场景把它单独列为 INFO。

**环境观察（既有行为，非本需求范围）**：compliance 在确认分类后立刻评 CRR，本机两次返回 409 CONCURRENT_REQUEST，稍等后重试即成功。`prepare_cases.py` 已在两步之间加 3 秒等待。

## 5. 增补：U1 列表口径（21:03 +04）

主会话决定："全部"只列每个 Client 的最新版本，按状态筛选时列出该状态的全部版本。risk-decision 改完后全模块 1801 / 1801，新 JAR 已换上（pid 46315）。本机核对：全部 5 行、已作废 1、已拒绝 2、草稿 1，统计不变（`stage5/logs/31-u1-check.log`）。按新口径，不带状态的"来源 = 自动规则"筛选为 0 行：240674874987655169 的自动承保版本已被 v2 替代，只能在"已作废"页签里看到。


## 5. 浏览器走查（rq03-web，2026-10-29 20:50—21:45 +04）

> 只操作本机：本 worktree 的 Vite、本机 Platform 语言包（`mor_platform_v2e2e`，真实本地化 API）、3 个本机测试角色的权限（用户选 A 后由主会话批准）。承保数据未写：不点一键自动承保（241533217348268033）、不复核 241533316505808897、修订弹窗打开后取消、草稿只改 T+N 看预览不保存。走查后 `t_client_underwriting` 仍 8 行、各行 `version` 与最后更新时间（20:38 +04）不变。未碰 mor-test，未提交、未推送。**Vite 18314 保持运行。**

### 5.1 环境变更

| 时间（+04） | 动作 | 回滚 / 停止 |
|---|---|---|
| 20:47 | 运营 Web：本 worktree 的 Vite 跑在 **18314**（`stage5/stage5-vite.rq03-ops.config.mjs`，`/api/operations` → Nginx 18444，`cacheDir` 在 worktree `operations-console-web/.vite-cache`；`operations-console-web/node_modules` 为指向主工作树的符号链接），**pid 33315**，日志 `stage5/logs/vite-18314.log`；18311—18313 未动 | `kill 33315`，删除 `.vite-cache` 与 `node_modules` 符号链接 |
| 20:49 | 语言包 **operations-console #15**（`stage5/stage5_i18n_local.py`，由 REQ-12 同名脚本改写；命名空间 risk、nav、master-data、client-review）：新增 247、改基准 0、废弃 0，zh-CN 更新 247；READY 并为当前版本 | 切回 #14 |
| 21:10 | 语言包 **#16**：只改 1 条基准（`risk.underwriting.list.total` 改为"共 N 条"）；READY 并为当前版本。日志 `stage5/logs/i18n-publish.log` | 切回 #15 / #14 |
| 20:58 | **测试角色加目录权限（用户选 A）**：`stage5/stage5_role_directory.py` 经 IAM `PUT /iam/v1/roles/{roleId}` 给 uw（241601252784345088）、lead（241601256873791488）、ro（241601260292149248）加 `RISK_DECISION_CONFIG_READ`（900000000000000536），各角色 version 1 → 2；前后权限集合见 `stage5/logs/role-directory.log`（uw：143101、143102 → +536；lead：143101—143103 → +536；ro：143101 → +536） | `python3 -I stage5_role_directory.py rollback`（只移除 536） |

语言包：Vite 端口不在 MinIO CORS 放行列表（只放行 18311），走查脚本沿用 REQ-05 / REQ-12 做法，在浏览器内为 19400 的语言包响应补 `Access-Control-Allow-Origin`；普通浏览器直接打开 18314 时语言包会被 CORS 拦截、回落内置英文。

### 5.2 走查结果（Playwright 无头 Chromium，`stage5/stage5_browser.mjs`）

zh-CN / en × 1440 / 390；管理员 15 步 + uw / lead / ro 9 步 = 每组合 24 步，四组合全部通过。截图 `stage5/screens/<语言>-<宽度>-<账号>-<步骤>.png`（96 张），结果 `stage5/stage5-browser-result-<账号>-<组合>.json`。列表截图在 rq03-backend 21:03 上线新 U1 口径（"全部" = 每 Client 最新版本，状态页签 = 该状态全部版本）后重拍。

| 步骤 | 账号 | 内容 | 结果 |
|---|---|---|---|
| L1 | admin / uw / ro | 统计卡 生效中 3、草稿 1、待复核 1、本月已批准 4、本月已拒绝 2（与库一致）；6 个状态页签；"全部"共 5 条（5 个有记录的 Client）；"新建承保"按钮：admin、uw 有，ro 无；"待我复核"开关只对有复核权限的 admin 出现 | 通过 |
| L2 / L2b / L3 | admin | 草稿页签 1 行（无 CRR 的 v2，徽标"未评 CRR"、操作"继续"）；已作废页签 1 行（240674874987655169 v1）；CRR = HIGH 2 行；MCC 名称来自 refdata，5967 带"卡组织高风险" | 通过 |
| L4 | admin / lead | 待我复核：admin 1 行（C v2，操作"复核"）；lead 0 行（C 是本人提交） | 通过 |
| P02（原型 02） | admin / uw | 241533217348268033：三项全通过，流水行"预计月流水 USD 50,000 ≤ 阈值 USD 50,000"，符合自动承保；标准模板结论；"改为人工承保""一键自动承保"可用（**未点击**） | 通过 |
| P03（原型 03） | admin | 同一 Client 点"改为人工承保"：人工表单；T+N 填 7 → 敞口由 USD 0.00 实时变为 USD 11,666.67（`USD 50,000 × 7 ÷ 30`，U11 预览，未保存） | 通过 |
| P03 草稿 | admin / uw | 240674482170114049 v2 草稿：CRR 徽标"未评 CRR"+"尽调未评 CRR，按 MEDIUM 处理"；MEDIUM → 按钮"保存草稿 / 拒绝 / 提交"；未采集交付方式 → 敞口"无法估算 · 未采集交付方式"，改 T+N 后仍无法估算（符合 B10）；未保存 | 通过 |
| P04（原型 04） | admin | C v2 待复核：冻结输入、来源坐标、敞口 `USD 50,000 × (14 + 14) ÷ 30 = 46,666.67`、结论只读、复核区"拒绝 / 批准"可用（**未复核**）、版本历史含 v1 拒绝原因 | 通过 |
| P04 本人 | lead | 同一记录：两按钮**禁用**，提示"你是提交人，须由另一位具有复核权限的人员复核（四眼）" | 通过 |
| P04 无复核权 | uw | 无复核区，显示"等待风控负责人复核" | 通过 |
| P05（原型 05） | admin / ro | 240674874987655169 v2：已批准 · 生效、修订原因、版本历史 v1"已作废 · 自动规则 v1 · 被 v2 取代"；admin 有"修订"，打开弹窗空原因确定 → "必填"，随后**取消**；ro 无"修订" | 通过 |
| D1 / D2 | admin | MEDIUM 已批准：保证金"要求 USD 5,000 · 未到账前阻塞收款 · 由 REQ-18 执行"、T+7、敞口 35,000.00（`(14 + 7)`）；HIGH 已批准：提交人与复核人、复核意见 | 通过 |
| P02 只读 | ro | 无任何按钮，提示"你可以查看输入，但没有新建承保的权限" | 通过 |
| E1 / E2 | admin | Client 详情（241533217348268033）与入驻审核已批准案件（241533301817364480）页头均有"承保"入口 | 通过 |

- 未翻译 key：0；390 横向滚动：0；HTTP ≥ 400：0；承保写请求：0（只有 U11 预览）。
- 控制台：只有运营既有 antd `Spin tip` / `Space direction` 弃用告警；脚本的应用内跳转在少数页面触发 react-router "blocker on a POP navigation" 提示（来自走查脚本的 `pushState` 跳转，不是页面问题）。
- 提交人 / 复核人显示为本机账号的掩码邮箱（ops-bff 显示名解析结果）。

### 5.3 发现与处理

| # | 发现 | 处理 |
|---|---|---|
| 1 | 只有承保三项权限的角色看不到"风控"菜单：目录 `OPERATIONS_RISK_MANAGEMENT` 受 `RISK_DECISION_CONFIG_READ` 控制，Platform 导航不返回无权限目录 | 用户选 A：承保角色同时授予 `RISK_DECISION_CONFIG_READ`；requirement.md 第 9 节与 Q-8、technique.md S-11 与 9.4 已写明角色组合与原因；本机测试角色已加（5.1） |
| 2 | U1"全部"原为列出所有版本（8 行），与原型 01"每客户只列当前版本"不符；页面合计文案也写了该口径，且在 390 被截断 | 主会话定口径：全部 = 每 Client 最新版本，状态页签 = 该状态全部版本；rq03-backend 改 U1（21:03 上线）；页面合计改为"共 N 条"（语言包 #16），列表重拍 |
| 3 | 走查脚本：整页重载触发 Gateway 限流（`GATEWAY_RATE_LIMITED`）；登录表单在语言包到达时重渲染导致填入的账号被清空；承保页聚合较慢时过早截图 | 均为脚本问题：改为应用内跳转、登录前等表单稳定并重试一次、等页面内容出现再截图；受影响组合全部重跑 |

重测（worktree）：operations-console-web `npm test` **1617 / 1617**、`tsc --noEmit` 通过。
