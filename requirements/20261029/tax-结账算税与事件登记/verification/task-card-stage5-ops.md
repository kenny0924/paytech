# 阶段 5 任务卡：合并 ops-bff 与浏览器走查（W6）

- 授权：同 `task-card-stage5.md`（用户"继续"）；本机 ops-bff 替换已与 REQ-03 会话（design-discuss-b8）协商同意，条件见第 1 节。**不碰 mor-test、不提交。**
- 与 W5 并行：W5 负责 tax / acquiring / billing / account 与本机库。你**不要**动这些进程和库；浏览器走查需要 W5 的 tax-service 已运行（检查 `local-environment/e2e/tax.pid` 与 readiness `http://127.0.0.1:19116/actuator/health/readiness`），未就绪就先做 1、2 两步。

## 1. REQ-03 的条件（必须全部满足）

- 合并来源：REQ-03 当前未提交改动在 `/Users/kenny/Desktop/Workspace/Code-MoR-rq03`（基于 `e3e0c12f`）——**只复制，不修改该 worktree**。
- 本机 jars 里已有的 `paytech-operations-console-bff-app-exec.jar.pre-rq03` 不得改动；本次备份名用 `*.pre-tax`（备份的就是 REQ-03 的版本）。
- 安装用 `cp new .tmp && mv -f .tmp target.jar`，按原进程 argv + 环境（`ps eww`）重启；凭据不进报告。
- 替换后通过 Nginx 18444 只读验证 REQ-03：`GET /api/operations/risk-underwriting/v1/...` 的 workspace / list 与敞口预览仍可应答（账号 `lead`，凭据在 `~/Code-MoR-backups/rq03-stage5/accounts.json`，不打印，只读不写）。
- 合并后的 platform-support 能力清单（`console-resource-capabilities.json`）必须保留 REQ-03 的块 143 条目和 tax 的 `OPERATIONS_FINANCE_TAX`。
- 完成后用 SendMessage 通知 `design-discuss-b8`：新 pid 与验证结果（不含凭据）。

## 2. 合并构建

1. 用绝对路径建临时 worktree：`git worktree add --detach /Users/kenny/Desktop/Workspace/Code-MoR-taxcombo e3e0c12f`。
2. 把 tax worktree（`/Users/kenny/Desktop/Workspace/Code-MoR-tax`）与 REQ-03 worktree 的改动都应用进去（`git diff` + 新增文件；排除 `node_modules`、`.vite-cache`、`requirements/`、文档）。ops-bff 需要 REQ-03 的 `risk-decision` API 改动与 tax 的 `paytech-tax-api`，两边都要带上。冲突文件（预计 ops-bff 测试基类 / 配置、`pom.xml`、能力清单 JSON、ops-web 导航与能力文件）手工合并，保留双方内容。
3. 构建 ops-bff exec JAR；跑 ops-bff 模块测试（`package`，DB-free）确认合并无误（REQ-03 与 tax 的测试都在内）。
4. 按第 1 节替换本机 ops-bff，readiness `http://127.0.0.1:19201/actuator/health/readiness` UP，完成 REQ-03 验证并通知 b8。

## 3. 本机注册（仅 `mor_platform_v2e2e`，备份后执行，脚本与回滚放 `verification/stage5/`）

参照 `db/platform-registration.sql`，生成**本机专用**版本：注册块号用本机未占用的号段并在脚本顶部说明；Console Resource（财务 → 税务查询 `/finance/tax`）、权限 `TAX_VIEW` 授予本机运营管理员角色、两条 Gateway 路由（`/api/operations/tax/v1/orders/{orderNo}`、`.../documents/{documentId}/download` → ops-bff `/tax/v1/...`）。文件写路由由 W5 处理，不要重复。确认 Gateway 快照刷新成功。若菜单或能力需要替换 platform-support JAR 才能生效，**先停下报告**。

本机语言包：参照先例 `/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261028/commercial-开通项前置门禁/verification/stage5/stage5_i18n_local.py` 的做法，为 operations-console 导入并发布包含 `tax` 命名空间的 en / zh-CN 语言包（不废弃任何既有 key；先备份 i18n 相关表）。

## 4. 浏览器走查（需 W5 的 tax-service 与演示订单）

- 用 tax worktree 的 ops-web 起一个独立 Vite（端口用本机未占用的，例如 18312），代理到 Nginx 18444；不要动其他会话的 Vite。
- 演示订单号从 W5 的报告 `verification/stage5-report-backend.md` 获取（S1 已登记、S2 被阻断、S3 NOT_MANAGED 如有、S4 补登记）；再加一个不存在的订单号。
- Playwright 无头 Chromium：zh-CN / en × 1440 / 390，对照原型 `prototypes/16-registered.png` … `21-download-failed.png`：初始态与最近查询、已登记、被阻断、NOT_MANAGED、未找到、税票下载（成功拿到链接；再模拟失败——例如临时让下载请求失败的方式只能在浏览器侧拦截，不得改环境）。记录控制台错误、失败请求 requestId、原始 i18n key、溢出与布局问题；截图放 `verification/stage5/screens/`。
- 发现问题在 tax worktree 修复（同时同步到 taxcombo 重建 ops-bff 时要带上），重跑受影响测试；每轮修复都要在报告里列出。

## 5. 报告

`verification/stage5-report-ops.md`：合并方式与冲突处理、ops-bff 替换记录（时间、pid、备份）、REQ-03 验证结果、本机注册与语言包（脚本、备份、回滚）、浏览器走查矩阵与截图索引、问题与修复、Vite 访问方式（凭据位置，不抄写）。新 ops-bff 与 Vite 保持运行。

## 6. 停下报告的情形

需要替换 platform-support / Gateway JAR；合并冲突无法确定取舍；REQ-03 验证失败；卡住超过 10 分钟。
