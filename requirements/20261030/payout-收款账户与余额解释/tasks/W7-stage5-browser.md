# W7 任务卡：阶段 5 运营页面浏览器走查（Playwright，本机）

- 入口：`https://127.0.0.1:18454`（主会话起的私有 nginx → 主会话自己的 Vite 18331，代码 = `/Users/kenny/Desktop/Workspace/Code-MoR-po-e2e-ops/operations-console-web`，含 payout 两页；`/api/` 经同一网关 18080，nginx 把 Origin 改写为 `https://127.0.0.1:18444`——证据边界写进报告）。自签证书：Playwright `ignoreHTTPSErrors: true`。
- 参考脚本：`/Users/kenny/Desktop/Workspace/Code-MoR/requirements/20261029/platform-support-联合查询/verification/stage5/stage5_browser.mjs`（Playwright 写法、登录、截图、i18n / 横向滚动检查）；Playwright 依赖可用该会话或 ops-web 的 node_modules（不要全局安装；如需安装放在 scratch 目录）。
- 账号与数据：见 `requirements/20261030/payout-收款账户与余额解释/verification/stage5-backend-report.md` 第 2、4 节与其 `verification/local-e2e/` 脚本（W6 写的登录与账号凭据读取方式）。Maker = E2E 管理员；Checker = W6 新建运营账号；数据在 `mor_payout_v2e2e`（run-7 账户与 run-6 残留的 4 条 PENDING_REVIEW）。凭据只在内存读取，不打印、不写入文件。
- 原型（判定标准）：`requirements/20261030/payout-收款账户与余额解释/prototypes/ops/O1—O5*.png`；合同修订见 `technique.md` 8.7。

## 走查（zh-CN 与 en，各 1440 与 390）

1. **核验工作台 O1：** 管理员登录 → 财务 · 出款 · 收款账户核验；待核验 / 待复核 / 已处理页签计数；选一条 run-6 残留 PENDING_REVIEW（Client A 的）→ 详情：账户信息、Reveal（点击后显示原值与倒计时、再隐藏）、法定名对照、共用账号提示（如有）；填写 Maker 意见"通过"+ 备注 → 提交。
2. **四眼 O4：** 同一管理员切到待复核，打开刚才那条 → 选项与"批准"禁用并有说明。
3. **拒绝弹窗 O5：** 待核验另选一条 → 选拒绝 → 弹窗：原因单选、给 Client 的说明、影响提示；**点取消，不提交**（保留数据给 Checker 场景）。
4. **复核 O2：** Checker 账号登录 → 待复核 → 打开第 1 步那条 → 看到 Maker 意见与备注 → 批准 → 详情状态变冷却中。
5. **Owner 账户与余额 O3：** 按 Client A 编号查询 → 余额卡片（AE001 · USD：待结算 250、可用 0、阻断原因"没有可用余额""冷却中"）、账户表（状态徽标、默认标记、最近动作、操作按钮按权限显示）。
6. **无权限：** 用 W6 的无权限运营账号登录 → 出款菜单不可见；直接访问两页 URL 显示无权限。
7. **通用检查：** 每屏未翻译 key 数（`payout.*`、`nav.*`、`common.*`）、390 宽 `scrollWidth`、非预期 HTTP ≥ 400、控制台错误。

## 交付

脚本写 `requirements/20261030/payout-收款账户与余额解释/verification/stage5/stage5_browser.mjs`，截图 `…/stage5/screens/`，报告 `…/verification/stage5-browser-report.md`（结论表、对照原型的覆盖表、发现 / 与原型差异、证据边界）。不改代码、不重启进程、不碰 mor-test、不清理数据；前台执行。回复一句摘要与报告路径。
