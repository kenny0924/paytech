# 阶段 5 报告（ops 部分，W6）：合并 ops-bff、本机注册与语言包、税务查询页浏览器走查

> 只操作本机：`mor-local-*` 容器（MySQL 13306 的 `mor_platform_v2e2e`）、本机 e2e 的 ops-bff 进程、tax worktree 的独立 Vite 18315。未碰 mor-test，未提交、未推送；tax / acquiring / billing / account 进程与库未动（W5 负责）。**新 ops-bff（pid 8739）与 Vite 18315 保持运行。**

## 0. 访问方式

| 项 | 值 |
|---|---|
| 页面 | `http://127.0.0.1:18315/finance/tax`（财务 → 税务查询；`?orderNo=` 直达，`&lang=en` 切英文） |
| 账号 | 本机运营管理员，凭据在 `local-environment/e2e/secrets.env`（`E2E_ADMIN_EMAIL` / `E2E_ADMIN_PASSWORD`），不抄写 |
| 语言包 | 本机 MinIO 公开桶 CORS 未放行 18315：普通浏览器在 18315 只能用内置英文；中文走查由脚本在浏览器侧为 19400 响应补 ACAO（同 20261028 阶段 5 做法）。如需普通浏览器看中文，可按 20261028 报告第 11 节把 18315 加入 `MOR_MINIO_CORS_ALLOW_ORIGIN`（未执行，需另行同意） |
| 演示订单（W5 终版，acquiring 现为 REAL 模式） | S1 `ord_3LNgHd0TWGaDLjatKpMN9CnRwDu7TN`（r1 暂估 US 阻断 → r2 SA 最终，已登记，税票 SA-RCPT-2026-000003）；S4 `ord_3LNh7dqb0r8HtVN9eyLdmSSjiVw7Hp`（tax 重启后补登记，税票 SA-RCPT-2026-000004）；S2 `ord_3LNeFr4WMLAQVvoRXmXnUZLX3a6ARo`（KW 阻断）；S3 `ord_3LNeiE9WoN6f1SMqHv3iaNOfMSv7fc`（PSP 不由平台计税）；S5 存量 `ord_3L5VmtoflSHBWF4HfQIXjWPwa6Q4G6`（替换前已付，tax 404）；不存在 `ORD-NOTFOUND-20261029` |

## 1. 合并构建

- 临时 worktree：`git worktree add --detach /Users/kenny/Desktop/Workspace/Code-MoR-taxcombo e3e0c12f`。
- 先应用 tax worktree（`/Users/kenny/Desktop/Workspace/Code-MoR-tax`）的 `git diff --binary HEAD` 与未跟踪文件，在 combo 内做本地临时提交 `d3d3b779`（"tmp: tax stage4 state"，只在该 detached worktree，不推送）；再以 `git apply --3way` 应用 REQ-03（`/Users/kenny/Desktop/Workspace/Code-MoR-rq03`）的 diff 并复制其未跟踪文件。两边都排除 `requirements/`、`agent-docs/`、`node_modules`、`.vite-cache`。**rq03 worktree 只读，未修改。**两边未跟踪文件无重叠。
- 冲突 3 个，均保留双方：

| 文件 | 处理 |
|---|---|
| `operations-console-web/src/configuration/configurationModel.test.ts` | 页面能力数 79 → 80（两边各加一个），保留 REQ-03 的 `OPERATIONS_RISK_UNDERWRITING` 断言 |
| `paytech/tools/arch-rules/src/main/resources/table-ownership.yaml` | 头注释取 tax 版本（42 removed），step-added 75 + 3 = 78，注明 REQ-03 risk-decision 三表；表清单两边自动合并（含 `t_client_underwriting`、`t_underwriting_rule_*` 与 tax 新表） |
| `paytech/tools/arch-rules/.../TableOwnershipAutoTest.java` | 总表数表达式在 tax 的 `- 8 + 1` 后追加 REQ-03 的 `+ 3`；说明文字两段都保留 |

- ops-bff 构建与测试（combo，`-pl ops-bff app,test -am -Dtest='com/paytech/operations/**/*'`，不 install）：**2481 / 2481 通过**，含 `TaxOrderBrowserAutoTest` 20、`RiskUnderwritingBrowserAutoTest` 9。exec JAR 同时含 `paytech-tax-api` 与 `paytech-risk-decision-api`，sha256 `758ee628…0a8b87`。
- 能力清单：combo 的 `console-resource-capabilities.json` 含 `OPERATIONS_FINANCE_TAX`；REQ-03 的块 143 在 `platform-support-initial-data.sql`（combo 保留），但 REQ-03 diff 本身没有往能力清单加条目，所以清单里没有块 143——这是 REQ-03 的现状，非合并丢失（已告知 b8）。清单只用于 Console Resource 管理页的校验与"支持"标注，不影响用户导航，**本机无需替换 platform-support JAR**。
- 未跑 arch-rules 测试（不在本卡范围，冲突只是计数合并；全 reactor 不跑）。

## 2. ops-bff 替换记录

| 时间 | 动作 | 依据 / 回滚 |
|---|---|---|
| 22:23 | `cp -p` 现 JAR → `local-environment/e2e/jars/paytech-operations-console-bff-app-exec.jar.pre-tax`（即 REQ-03 版本）；`.pre-rq03` 未动 | 回滚：`cp .pre-tax .tmp && mv -f .tmp …exec.jar` 并按同样方式重启 |
| 22:23 | 新 JAR `cp → .tmp → mv -f` 安装 | — |
| 22:23:33 | 停旧进程 pid 27317 | 原 argv（21 项）与环境（101 项）经内核 procargs2 捕获到会话临时目录（权限 600），不进报告 |
| 22:23:44 | 以相同 argv / 环境 / 工作目录启动，输出追加 `local-environment/logs/ops-bff.log`；写 `ops-bff.pid`（8739）、`ops-bff.started` | 未新增环境变量：`OPERATIONS_TAX_BASE_URL` 未设置，默认 `http://127.0.0.1:18116` 即 W5 的 tax 端口 |
| 22:23:51 | readiness `http://127.0.0.1:19201/actuator/health/readiness` UP | — |

## 3. REQ-03 验证（经 Nginx 18444，账号 lead，只读）

脚本 `stage5/stage5_ops_rq03_check.py`（凭据读 `~/Code-MoR-backups/rq03-stage5/accounts.json`，不打印）。

| 接口 | 结果 |
|---|---|
| `GET /risk-underwriting/v1/underwritings?page=1&size=10` | 200，total 5 |
| `GET …/clients/{clientId}/workspace` | 200（canManage、current、input、preview、sources 等） |
| `GET …/clients/{clientId}/underwritings` | 200 |
| `GET …/underwritings/{id}` | 200 |
| `POST …/previews`（纯计算，不写库） | 200（autoEligible、estimatedExposure、reviewLevel 等） |
| `GET …/underwriting-statistics` | 200 |

已用 SendMessage 通知 `design-discuss-b8`：新 pid 8739、合并方式、测试与上述结果（不含凭据）。

## 4. 本机注册与语言包（`mor_platform_v2e2e`）

| 时间 | 动作 | 备份 / 回滚 |
|---|---|---|
| 22:21 | 备份 `t_permission`、`t_role_permission`、`t_console_resource`、`t_gateway_route`、`t_gateway_route_header`、`t_gateway_guard_rule`、`t_role` | `stage5/backup/mor_platform_v2e2e.ops-tax-registration.20261029.sql` |
| 22:21:12 | 执行 `stage5/30-ops-platform-tax-local.sql`：本机专用号段 `900000000001449xxx`（核对为空；非阶段 6 块号）。内容同 `db/platform-registration.sql` 第 A、B 段并直接 ACTIVE（等同第 D 段）：`TAX_VIEW` 授予 PLATFORM_ADMIN（900000000000000510）、菜单 财务 → 税务查询 `/finance/tax`（排序 75）、两条 Gateway 路由（订单详情 GET、税票下载 POST）及 8 个请求头、12 条 Guard；角色版本 +1。结果 1 / 1 / 1 / 2 / 8 / 12 | 回滚 `stage5/30-ops-platform-tax-local-rollback.sql` |
| 22:21:13 | Gateway 快照刷新成功，routeCount 532 → 534 | — |
| 22:24 | 备份 i18n 七表 | `stage5/backup/mor_platform_v2e2e.i18n.20261029.sql` |
| 22:24:46 | `stage5/stage5_ops_i18n_local.py`：创建 `tax` 命名空间（sortOrder 270）；英文基准 = 本机全部 ACTIVE key ∪ tax 命名空间 211 键 ∪ `nav.resource.OPERATIONS_FINANCE_TAX`（其他命名空间不覆盖）；预检 added 212、sourceChanged 0、**deprecated 0**；zh-CN 导入 212 条；发布 **#17 READY（current）** | 日志 `stage5/stage5-ops-i18n.log`；语言包只新增，无需回滚 |
| 23:20:47 | 同一脚本再执行一次（第 6 节修复 3 新增 2 个 key）：预检 added 2、deprecated 0；zh-CN 2 条；发布 **#18 READY（current）** | 日志 `stage5/stage5-ops-i18n-round2.log` |

tax-service 文件写路由（第 C 段）由 W5 处理（`30-platform-tax-file-route-local*.sql`），本部分未重复。

## 5. 浏览器走查

- Vite：tax worktree ops-web，`127.0.0.1:18315`（18311—18314 已被其他会话占用），配置 `stage5/stage5-vite.tax.config.mjs`，代理到 Nginx 18444；监听 pid 10081。`node_modules` 为指向主工作树的**临时符号链接**（两边 `package-lock.json` 一致，同阶段 4 做法），Vite 缓存放会话临时目录。
- 脚本 `stage5/stage5_ops_browser.mjs`（Playwright 无头 Chromium）。每组：初始态 → 各演示订单 → 未找到 → 最近查询 → S1 真实下载 → 浏览器侧把下载请求拦截为 503（带同值 `X-Request-Id`）模拟原型 21。
- **终版**（W5 终版订单，含修复 3，重拍全部四组）：日志 `stage5/stage5-ops-browser-run-final.log`，结果 `stage5/stage5-ops-browser-result-final.json`，截图 `stage5/screens/*.png`。S4 下载另跑一组 en 1440（修复 3 之前）：`stage5-ops-browser-result-S4-download.json`，下载 `SA-RCPT-2026-000004.pdf`。修复 3 之前的 S1 截图留在 `screens/before-step-text/`；只拍 S1 的中间一轮见 `stage5-ops-browser-run-step-text.log`（其中 en 390 最近查询一次 429：同一 IP 连续多轮触发 Gateway 每分钟 30 次限流，属预期，随后完整重跑无 429）。
- 早期轮次（W5 草稿订单，含第 6 节修复前后与 tax 重启期间）：截图 `stage5/screens/prelim-round3/`，结果 `stage5-ops-browser-result-prelim-round3.json`、`stage5-ops-browser-result-round2-with-tax-restart.json`，日志 `stage5-ops-browser-run-round2.log`、`-round3.log`。

| 状态（原型） | zh-CN 1440 | zh-CN 390 | en 1440 | en 390 | 截图 |
|---|---|---|---|---|---|
| 初始态（19） | 通过，侧栏菜单项 1 | 通过（窄屏侧栏为图标栏） | 通过，菜单项 1 | 通过 | `*-19-initial.png` |
| 最近查询（19） | 4 行（S4 / S3 / S2 / S1；S5 与不存在订单 404 不入列） | 通过 | 通过 | 通过 | `*-19-initial-recent.png` |
| 已登记 + 已开票（16，S1） | 通过：5 步进度（建单报价"暂估 · 阻断 · US"，修复 3 后）、汇率缺失提示、明细 1 行、报价记录 r2 最终 / r1 暂估阻断、下载按钮 | 通过 | 通过 | 通过 | `*-order-S1.png` |
| 已登记（16，S4 补登记） | 通过：已登记 · 已开票、明细 1 行、下载按钮 | 通过 | 通过 | 通过 | `*-order-S4.png` |
| 被阻断（17，S2） | 通过：MARKET_NOT_ENABLED、无下载 | 通过 | 通过 | 通过 | `*-order-S2.png` |
| 不由平台计税（18，S3） | 通过：PSP、自报 0.00、原因"非平台登记纳税主体" | 通过 | 通过 | 通过 | `*-order-S3.png` |
| 存量订单 S5 / 不存在订单（20） | 404 `TAX_ORDER_NOT_FOUND` → 未找到空状态 | 通过 | 通过 | 通过 | `*-order-S5.png`、`*-20-not-found.png` |
| 税票下载成功（16） | S1：200，返回 documentId / documentNumber / expiresAt / url（19400），浏览器下载 `SA-RCPT-2026-000003.pdf` | 同 | 同；S4 下载 `SA-RCPT-2026-000004.pdf` | 同 | `*-16-download-ok.png` |
| 下载失败（21，模拟） | 错误提示条：标题、说明、重试下载、复制请求编号 `b5e2…0021` | 同 | 同 | 同 | `*-21-download-failed.png` |

终版每组：页面上原始 i18n key 0 个，横向溢出 0。HTTP ≥ 400 只有预期的：订单 GET 404（S5、不存在订单）各组 2 次，下载 503（模拟）各组 1 次。控制台只有 antd `Spin tip`、`Space direction` 弃用告警（既有代码）与上述 404 / 503 对应的 "Failed to load resource"。

早期轮次另记：22:58 W5 为 S4 重启 tax-service 时，4 次订单 GET 得到真实 503（requestId `d689f5eb…`、`beea5b9c…`、`ecf01f29…`、`a33f29d6…`），ops-bff 正确返回 503，页面显示"查询失败 + 重试"状态（`prelim-round3/*-round2-lookup-failed-during-tax-restart.png`）；tax UP 后重跑通过。

## 6. 问题与修复

| # | 问题 | 处理 | 验证 |
|---|---|---|---|
| 1 | （早期轮次发现）390 宽度下报价记录时间（`flex: 0 0 auto`）占住横向空间，摘要正文被挤成几个字宽，页面高 4182 px；明细表商品列也偏窄 | tax worktree `operations-console-web/src/tax/tax.css` 的 760 px 媒体查询：报价行改为两列网格，时间放到摘要下方；明细表字号 12、数字列左距 8。已同步复制到 combo | ops-web `npm test` 1614 / 1614，`npm run build` 通过；第 2、3 轮 390 截图正常（en 390 页高 3385 px） |
| 2 | （早期轮次）第 1 轮失败提示没有"复制请求编号" | 不是页面问题：前端只采信与 `X-Request-Id` 响应头一致的 requestId（RFC 9457 合同），第 1 轮模拟响应没带该头。脚本改为带同值头 | 第 2、3 轮均显示复制入口 |
| 3 | S1 的建单报价被阻断（r1 暂估 US 阻断）、之后被 r2 取代：进度第一步显示"暂估 — USD · US"。主会话裁决改为"暂估 · 阻断 · US"（en "Estimate · Blocked · US"），同规则适用于其他依据报价为 BLOCKED 的步骤 | 文案归 web：BFF 的步骤状态 DONE 是正确的进度语义（已被取代，不再阻断），每条报价已带 stage / status。tax worktree `operations-console-web/src/tax/tax.ts` 新增 `stepFactKey`：步骤 DONE 且其依据报价（建单报价 = 修订号最小的报价；资料提交重算 = 修订号最大的 FINAL 报价，与 BFF 取值一致）为 BLOCKED 时用 `step.<KEY>.DONE_BLOCKED`；`TaxOrderView.tsx` 改用它；en / zh-CN 各新增 `tax.step.QUOTE.DONE_BLOCKED`、`tax.step.RECALCULATION.DONE_BLOCKED`（"最终 · 阻断 · {country}"）。当前仍阻断的步骤（状态 BLOCKED，如 S2）沿用原型 17 的"账单国家 KW · 无法计税"，本身不显示金额。ops-bff 未改动，未重新替换。同步到 combo | `tax.test.ts` 新增用例（取代的暂估阻断、取代的最终阻断、当前阻断、不由平台计税四种）；ops-web `npm test` 1615 / 1615，`npm run build` 通过，`npm run i18n:export-source` 通过（tax 213 键）；本机语言包 #18；四组 S1 重拍 `screens/*-order-S1.png` |

未修复、只记录（均非本需求代码或属数据差异）：
- zh-CN 下侧栏底部 "Operations Console / Hide Navigation" 为英文：仓库 `i18n/zh-CN/nav.json` 中 `nav.sidebar.*` 本身就是英文，既有问题。en 下侧栏"外部 API"为中文，同属既有资源名。
- 中文按钮"查 询"带字间空格（antd 默认 `autoInsertSpace`），原型为"查询"：主会话裁决保持现状，与控制台一致。
- 头部 meta 行在 1440 下因订单号与 Client 名称较长换行，换行段以"·"开头；与原型差异小。
- 与原型的数据差异：正式登记时间只显示 `22:29`（原型 `10:43:09`）；申报底稿 PENDING 时期间只显示"季报"、无截止日（W5：汇率源未接入，D-01 预期）；S3 自报税额 0.00（演示数据）。

## 7. 恢复方法

1. ops-bff：`cp -p jars/…exec.jar.pre-tax jars/…exec.jar.tmp && mv -f jars/…exec.jar.tmp jars/…exec.jar`，按原 argv / 环境重启（需要时由 REQ-03 会话决定）。
2. Platform：`mor_platform_v2e2e` 执行 `stage5/30-ops-platform-tax-local-rollback.sql`；语言包 #17、#18 只新增 key，无需回滚（必要时从 `backup/mor_platform_v2e2e.i18n.20261029.sql` 恢复）。
3. Vite：`kill 10081`，然后删除临时符号链接 `/Users/kenny/Desktop/Workspace/Code-MoR-tax/operations-console-web/node_modules`（**Vite 运行期间不要删**）。
4. combo worktree：用完后 `git worktree remove --force /Users/kenny/Desktop/Workspace/Code-MoR-taxcombo`（内含本地临时提交，不影响主仓库分支）。

## 8. 遗留

- 无待裁决项：按钮字间空格保持现状、MinIO CORS 不改（主会话裁决）；普通浏览器在 18315 只能看到内置英文。
- 本节之外的运行态：ops-bff pid 8739、Vite 18315 pid 10081 保持运行；tax worktree 的临时 `node_modules` 符号链接在停 Vite 后删除（第 7 节）。
