# reconciliation 对账控制台详情页与渠道流水 交付记录

## 适用阶段（用户 2026-10-21 选择"合并阶段"）

| 阶段 | 安排 |
|---|---|
| 1—3 需求、现状、方案 | 已确认（2026-10-21）：[requirement.md](./requirement.md)、[technique.md](./technique.md) |
| 4—5 研发与本机验证 | 已确认（2026-10-21）：[tasks/backend-report.md](./tasks/backend-report.md)、[tasks/web-report.md](./tasks/web-report.md)、[verification/stage5-report.md](./verification/stage5-report.md) |
| 6—7 mor-test 发布与验收 | 已确认（2026-10-21）：[release/release.md](./release/release.md) 第 8 节；候选 reconciliation `b31a853d…`、platform `3d8bc375…`、ops-bff `e822301c…`、ops-web `70829a3a…`（tag `recon-detail-20261021`）；块 126 激活 |
| 8 本地提交 | 已完成（2026-10-21）：见“提交记录”；不推送 |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-21 | 立项 | "那优化一下，作为二级页面展示吧，不要从右侧抽屉拉出来"；AskUserQuestion：所有对账详情改二级页面（录入表单仍用抽屉）、加「渠道流水」页、合并阶段 | 登记需求，交付阶段 1—3 合并评审稿（只写文档） |
| 2026-10-21 | 1—3 | "可以" | 进入阶段 4—5（研发与本机验证，不提交、不部署） |
| 2026-10-21 | 4—5 | "准备去公司了，帮我找个安全点暂停下" | 两线收尾到可编译后暂停 |
| 2026-10-21 | 4—5 | "继续" | 恢复两线研发 |
| 2026-10-21 | 4—5 → 6—7 | "继续"：接受研发与本机验证，授权 mor-test 发布（替换 reconciliation、ops-bff、ops-web、platform-support，块 126 注册与激活，演示数据走查）。切片 1 后续材料的小提交不视为已授权，并入阶段 8 一并请示 | 阶段 6—7 |
| 2026-10-21 | 6—7 → 8 | "继续"：接受 mor-test 发布结果，授权按两个本地提交（切片 1 后续材料、本需求）以当前 main 为父、私有索引组装，不推送 | 阶段 8 |
| 2026-10-21 | 5 | AskUserQuestion 授权本机 `mor_platform_v2e2e` 块 126 注册与激活、本机合成数据走查 | 本机浏览器走查 |

## 当前阶段与下一确认点

当前：阶段 1—8 已完成。推送另行授权。

**暂停点核对（主会话）：** Web `tsc` 通过、`npm test` 1225/1225（Web 线）；reconciliation-test 93/93（surefire 复核），三个新接口生产代码完成、尚无专项测试；BFF、Platform 块 126、渠道账单 / 银行 / 异常详情页、渠道流水页未开始或未接线；`platform-support-initial-data.sql` 无块 126 行。

**恢复时：** 读两份报告 → 核对 `tsc` / 测试 → 续做未完成部分 → 本机验证 → 交阶段 4—5 确认。

## 协调

- 2026-10-21 03:58:51Z 商务会话把 mor-test ops-web 换为 `sha256:139c157e…`（capability-20261021e），为本需求发布基线；块 126 对方未占用；`pageCapabilities.ts`、`configurationModel.test.ts` 含其未提交 hunk（标准价目页等），本需求修改时须保留。

- 2026-10-21 币种主数据会话（code-mor-50）将在 mor-test 替换 ledger、billing、platform、ops-bff、ops-web（基于 `2f5f37f7` + 商务集成头 + 币种补丁；ledger / billing 改读 Platform 币种快照、删除 `t_currency_spec` 副本）。已回复可以，并提醒其从干净 worktree 构建、排除本需求在研文件。本需求发布时以其新镜像为基线。

- 2026-10-21 币种会话发布完成，mor-test 现网（本需求发布基线与回滚目标）：platform `b2a63ff4…`、ops-bff `fddb1993…`、ops-web `366f8b95…`（另 ledger `fa21520d…`、billing `90d7401a…`，本需求不动）；源码 `Code-MoR-currency-rel`（只读）。ledger / billing 已依赖 Platform 币种快照、`t_currency_spec` 副本已删。

- 2026-10-21 商务会话：mor-test ops-web 将换为 `sha256:c6d2118f…`（capability-20261021g = 币种源码 + 商务修复），为本需求 ops-web 基线；其需求已本地提交 main `5a103f36`（父 `2f5f37f7`，未推送）。本需求后续提交以当时 HEAD 为父。

## 测试证据

**阶段 4 门禁（2026-10-21 本机）**

| 范围 | 结果 |
|---|---|
| reconciliation-test | 100/100（含 `ReconciliationDetailQueryTest` 6 例；主会话按 surefire 复核） |
| ops-bff 对账相关 3 个测试类 | 152/152（含新接口 404 透传） |
| ops-web | 1236/1236、`tsc` 通过、`npm run build` 通过（主会话复跑 test / tsc） |
| arch-rules | 63/63 |
| `platform-support-schema.test.mjs` | 4 例失败，与切片 1 时相同，来自其他会话的 Platform 模型改动 |
| 本机走查（私有栈 + 合成数据） | 浏览器 82/83、接口 27/27；W-1（渠道流水筛选后空状态文案取挂载时筛选）与 W-2（银行流水详情 Descriptions span 告警）为低级缺陷，主会话已修复：`useReconListQuery` 增加 `activeFilters`、附言去掉 `span: 3`；修复后 Web 1237/1237、`tsc` 通过。观察：Gateway 每路由每人每分钟 30 次，快速连续筛选可能触发 429（沿用块 123 GET 路由限流） |
| 块 126 | `platform-support-initial-data.sql` 末尾完整一块（38 行，COMMIT 结尾，休眠）；文件、本机、mor-test 均未占用 |

## 环境操作记录

| 时间 | 环境 | 动作 | 依据 |
|---|---|---|---|
| 2026-10-21 05:14Z | 本机 `mor_platform_v2e2e` | 备份到 `~/Code-MoR-backups/20261021T051428Z-recon-detail-local/`；执行 db/platform-support/registration-dml.sql（块 126 休眠行）与 activation-local.sql（路由 3 / 资源 1 / 角色版本 1） | 用户 2026-10-21 AskUserQuestion 授权 |
| 2026-10-21 05:53—06:1xZ | mor-test | 备份；替换 reconciliation、platform-support、ops-bff、ops-web；`mor_platform` 块 126 注册 + 激活（3 / 1 / 1，PLATFORM_ADMIN v40）；冒烟与浏览器只读走查（演示数据），未写业务数据 | 用户 2026-10-21 阶段 6—7 授权 |

## 变更记录 / 提交记录

无。
