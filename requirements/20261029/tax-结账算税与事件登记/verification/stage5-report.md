# 阶段 5 报告：tax 本机联调

- 日期：2026-10-29
- 范围：只操作本机（`mor-local-*` 容器与本机 e2e 进程）；未碰 mor-test，未提交、未推送。
- 分项报告：[后端（W5）](./stage5-report-backend.md)、[ops-bff 与浏览器（W6）](./stage5-report-ops.md)；任务卡 [后端](./task-card-stage5.md)、[ops](./task-card-stage5-ops.md)；截图 38 张在 `stage5/screens/`。

## 1. 结论

| 验收 | 结果 | 证据 |
|---|---|---|
| AC-01 沙特 15%：100 → 15 / 115 | 通过 | S1（MOCK 与 REAL 两笔）：暂估 AE 5% / US BLOCKED → 资料提交 SA 15% → 付款 115.00 |
| AC-03 暂估不可付、重算后可付、合同重渲染 | 通过 | S1、S2 |
| AC-04 NOT_MANAGED 可付、无事实无税票 | 通过（tax 侧） | S3 PSP 订单；billing 侧见第 2 节 |
| AC-05 BLOCKED 不可付 | 通过 | S2 科威特：MARKET_NOT_ENABLED，付款 409，订单保持开放 |
| AC-07 正式事实唯一 | 通过 | S1、S4 |
| AC-10 billing 不等 tax、tax 恢复后补登记 | 通过（tax 侧与"billing 不调用 tax"） | S4：tax 停机期间付款，事件在 `tax.acquiring.p3` 等待；重启后登记并开票 `SA-RCPT-2026-000004`，无死信；billing 在 tax 停机 5 秒后独立消费同一事件 |
| AC-11 税票 | 通过 | 票号连续、失败重试不换号，经 Platform 下载 PDF 的 SHA-256 与 `document_digest` 一致 |
| AC-12 存量订单 | 通过（读取） | 3 笔旧已付订单读取正常、tax 无记录；旧未付订单均已过期，无法测付款 |
| AC-13 / AC-14 运营页 | 通过 | zh-CN / en × 1440 / 390：初始与最近查询、已登记、被阻断、NOT_MANAGED、未找到、税票下载成功与失败提示；无原始 i18n key、无横向溢出 |
| REQ-03 不受影响 | 通过 | 合并 ops-bff 后承保只读接口 6 项均 200，已通知 design-discuss-b8 |

## 2. 本机未能验证的部分

**billing → account → ledger 入账段**（AC-09 端到端、AC-10"金额与 billing 一致"）。原因与 tax 无关：

- MOCK 商业解析写死的价格 / 合同 ID 在本机 commercial 库不存在；切到 REAL 后，billing 又因平台 Party 没有 ACTIVE 财务主体而失败。
- 本机 ledger 没有任何财务主体、账簿、日历、期间；billing 还需要渠道成本方案与对账清算账户，本机没有 reconciliation 进程。从零搭建超出授权，主会话决定不做。
- 覆盖：`SELLER_TAX` 行、本金减税、account 映射、ledger 合并一笔 `TAX_PAYABLE` 已由阶段 4 auto 测试覆盖（billing 142、account 68、ledger 332）。端到端放到阶段 7 在 mor-test 验证。

## 3. 联调中发现并修复的问题

| 问题 | 处理 | 验证 |
|---|---|---|
| acquiring 通过 Nacos 服务名调用 tax，但 tax 不注册 Nacos | worktree：改为固定地址 `ACQUIRING_TAX_SERVICE_URL`（默认 `http://127.0.0.1:18116`） | acquiring 916 / 916 |
| tax 服务端上传税票不信任本机 MinIO 自签证书 | 本机：tax 启动参数加 JVM 信任库（环境改动，非代码） | 重试后同票号开具成功 |
| 运营页 390 宽度下报价记录挤成几个字宽 | worktree：`tax.css` 窄屏时间换行到摘要下方，同步到合并 worktree | ops-web 1614 / 1614、build 通过 |
| 暂估被阻断时进度条第一步显示"暂估 — USD · US"，看不出被阻断 | worktree：web 增加 `DONE_BLOCKED` 文案（"暂估 · 阻断 · US" / "最终 · 阻断 · …"），同步到合并 worktree；本机语言包 #18（+2 键） | ops-web 1615 / 1615；最终走查矩阵复跑通过，S1 税票下载 200（S4 税票在修复前的单独一轮已验证 200） |
| tax 上传失败时日志无原因 | 未修（不影响功能），列为遗留 | — |

## 4. 本机环境变更（均有备份与回滚，详见分项报告）

- 本机 payment JAR 从 10 月 22 日的旧版本更新为 `e3e0c12f`（不在原授权清单，主会话单独批准；REQ-03 会话确认不受影响）。
- tax-service 首次在本机 e2e 启动（18116 / 19116）；`mor_tax_v2e2e` 重建、初始化，演示 AE001 Party 改为本机真实值 `900000000001380102`。
- `mor_acquiring_v2e2e` 迁移；acquiring、billing、account 替换为新 JAR；**acquiring 切到 REAL 商业解析并保持**（切回 MOCK 的步骤在后端报告第 8 节）。
- ops-bff 替换为 REQ-03 + tax 合并构建（合并 worktree `/Users/kenny/Desktop/Workspace/Code-MoR-taxcombo`，备份 `*.pre-tax`，REQ-03 的 `*.pre-rq03` 未动）。
- `mor_platform_v2e2e`：tax-service 私有文件写路由；税务菜单、`TAX_VIEW`、2 条 Gateway 路由（本机号段 `900000000001449xxx`）；语言包发布 #17（新增 212 键，未废弃）。
- 运行中：tax、acquiring（REAL）、billing、account、payment、合并 ops-bff；ops-web Vite `127.0.0.1:18315`。`billing.acquiring.dlq` 有 5 条本阶段消息（入账段失败所致）。
- 阶段 6 基线：REQ-03 已提交 main `9d272ae6`（+ `a90e4292`），mor-test 的 ops-bff / platform-support / ops-web / risk-decision 与之一致；阶段 6 需把 tax worktree 改动迁到该提交之上再构建。
- 新增临时 worktree：`Code-MoR-taxcombo`（合并构建）、`Code-MoR-tax-base`（为已删除的 account 运行 JAR 重建 `e3e0c12f` 回滚副本）。

## 5. 阶段 6 前置（mor-test 发布准备时必须处理）

- acquiring 配置 `ACQUIRING_TAX_SERVICE_URL`。
- tax 信任 mor-test 存储端点证书。
- tax 的 XXL 执行器环境变量与 `compensationEventScan` 注册（否则税票失败不会重试）。
- `tax-initial-data.sql` 使用 mor-test 上 AE001 的真实 Party；核对 mor-test acquiring 的商业解析模式（MOCK 时 Party 为 `880000000702`）。
- 共享 e2e 脚本与演示数据中的税类 `STANDARD` 会被判为未知税类而阻断（文件与行号见后端报告），发布前改为 `SAAS_AUTOMATED` 或在演示规则包登记别名。
- 其余见阶段 4 报告第 4 节（注册块号、`tax` 语言包命名空间、`OPERATIONS_TAX_BASE_URL`、文件路由策略允许 PDF、Gateway 快照离线编译、发布顺序）。
- ops-bff 与 ops-web 在 mor-test 上同样与 REQ-03 有重叠，发布时需按 REQ-03 已上线的版本合并。

## 6. 待用户决定

- 本机 MinIO CORS 是否加入 `http://127.0.0.1:18315`，以便普通浏览器在 18315 加载中文语言包（需要重建本机 MinIO 容器，会短暂影响其他会话；先例：REQ-01 曾加 18311）。
- 维持现状的小项：中文按钮"查 询"中间的空格是 antd 默认行为，运营台大多数页面相同，为一致性不单独改。
