# payout 收款账户与余额解释 · 阶段 5 运营页面浏览器走查报告

> 只在本机运行，未提交。路径：Playwright（`stage5/stage5_browser.mjs`，W7 编写，主会话复跑）→ 私有 Nginx `https://127.0.0.1:18454` → 主会话 Vite 18331（`Code-MoR-po-e2e-ops/operations-console-web`）→ `/api/` 经共享 Gateway 18080 → ops-bff（组合构建）→ payout（库 `mor_payout_v2e2e`）/ client-master / Platform（`mor_platform_v2e2e`，块 147 已激活）。
> W7 跑完四个组合后被主会话暂停停止，未写报告；本报告由主会话根据 `stage5/stage5-browser-result*.json` 与截图（`stage5/screens/`，56 张）整理。

## 结论

| 检查 | 结果 |
|---|---|
| zh-CN / en × 1440 / 390 四个组合 | 全部走完，60 个步骤无失败 |
| 对照原型 O1—O5 | 布局、关键交互与原型一致（见覆盖表） |
| 未翻译 key（`payout.*` / `nav.*` / `common.*`） | 0（见证据边界：语言包为浏览器内模拟发布） |
| 390 宽横向滚动 | 0 |
| 非预期 HTTP ≥ 400 | 首轮 **22 次 429**（核验队列，页面并发超出网关上限，缺陷，见发现 1；**修复后复验 0 次**）；1 次登录 503（首个组合启动时，重试成功）；ERR_ABORTED 6 次为切换详情时页面主动取消的请求 |
| 控制台 | 除 429 外均为已有 antd 弃用提示（Spin `tip` 48、Space `direction` 7、Modal `maskClosable` 4），与 payout 代码无关 |
| 写操作 | 6 次：zh-CN-1440 组合 Reveal、Maker 通过、Checker 批准同一条（run-6 残留 BA-0002）；其余组合的 Reveal |

## 覆盖情况（对照原型）

| 原型 | 走查内容 | 结果 |
|---|---|---|
| O1 待核验 | 页签计数、队列、详情；Reveal 显示原值并倒计时 60 秒、审计时间、隐藏；户名对照（合成数据户名不一致时显示"不一致"）；共用账号徽标与提示；Maker 意见通过 + 备注提交 | 通过 |
| O2 待复核 | Checker 账号看到 Maker 意见与备注，批准后详情为冷却中、Owner 页同步 | 通过 |
| O3 Owner 账户与余额 | 按 Client A 编号查询：AE001 · USD 待结算 250、可用 0，原因"没有可用余额""USD 收款账户冷却中，2026/10/31 14:38 可用"；账户表 7 行（待核验、冷却中 + 默认、已拒绝·户名不一致、已关闭），操作按状态显示；空状态 | 通过 |
| O4 四眼 | 同一管理员打开自己出具意见的条目：选项与"批准"置灰，底栏"你已出具本条核验意见，需由另一位同事复核" | 通过 |
| O5 拒绝弹窗 | 原因单选、给 Client 的说明（计数 0/500）、影响说明；取消不提交 | 通过 |
| 无权限 | 只有 DATA_QUERY_READ 的运营账号：出款菜单不可见，直接访问两页显示"无权限" | 通过 |
| 390 宽 | 列表与详情上下排列，导航收起，无横向滚动 | 通过 |

## 发现

1. **缺陷：核验页并发请求超出网关上限。** 网关路由 `operations-payout-admin-review-list` 每人并发 2（块 147 守卫，与其他路由一致），页面一次加载同时发"当前列表 + 两个页签计数"共 3 个请求，第 3 个被 429，页签计数时有时无。已交 W3 修改为列表先取、计数串行，两棵树同步修改；修复后用只读模式复跑（见"复验"）。
2. **与原型的已知差异（阶段 4 已记录）：** 提交人显示为"Client 成员 + 账号 ID"（无显示名合同）；Owner 搜索只按类型 + 编号。

## 证据边界

- **语言包：** 本机 Platform 发布 #20 不含 `payout` 命名空间，脚本在浏览器内改写 manifest，加载工作区 `i18n/{zh-CN,en}/payout.json` 与 `nav.json`（模拟阶段 6 发布）；真实发布在阶段 6 / 7 验证。
- **Origin：** 私有 Nginx 把 `/api/` 请求的 Origin 改写为 `https://127.0.0.1:18444`，以便在不改共享 Nginx 的情况下访问；其余链路与真实入口相同。
- **截图遮盖：** Reveal 后原值在截图中被遮盖，结果文件只记布尔与倒计时。
- **Client 门户：** 未做页面（原型暂缓），本报告不覆盖。

## 复验

W3 修复（`src/payout/payout.ts` 新增 `loadStageCounts` 等，`PayoutBeneficiaryReviews.tsx` 计数改为列表返回后串行获取、429 保留旧值；两棵树字节相同；ops-web tsc / 1631 单测 / 构建通过）后，主会话以只读模式复跑四个组合（`WRITE='' OUT=-recheck`，结果 `stage5/stage5-browser-result-recheck.json`，日志 `stage5/run-recheck.log`）：

| 检查 | 结果 |
|---|---|
| 四个组合 | 56 步，0 失败 |
| HTTP ≥ 400 | **0**（429 已消失）；1 次登录跳转时浏览器主动取消（ERR_ABORTED） |
| 控制台 | 只有 antd 弃用提示 |
| 写操作 | 4 次，均为 Reveal |
