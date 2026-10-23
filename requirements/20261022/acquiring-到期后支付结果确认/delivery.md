# acquiring-到期后支付结果确认 · 交付记录

| 日期 | 阶段 | 内容 | 状态 |
|---|---|---|---|
| 2026-10-23 | 来源 | [订单过期收尾](../acquiring-订单过期收尾/delivery.md) 阶段 5 行为发现 F1；用户 2026-10-23“你可以处理掉吗”，按交付流程另行登记 | 已确认 |
| 2026-10-23 | 1 需求评审 | [需求](./requirement.md) 评审稿：范围 R1—R4、规则 B1—B5、验收 AC-01—AC-08、待确认 Q1—Q4；现状证据见 [technique.md](./technique.md) 第 1 节 | 已修订 |
| 2026-10-23 | 1 需求评审 | 用户确认参照 Stripe 改版：Q1 不设宽限期（提交过支付即不受到期限制）、Q2 查看收银台推迟到 `checkout-web`、Q3 沿用 Payment 5 秒限频、Q4 直接修订设计第 523、622 行。[需求](./requirement.md) 第 2 版：范围 R1—R3、各系统改动点、规则 B1—B5、验收 AC-01—AC-06 | 已确认 |
| 2026-10-23 | 2 差异与影响 | [technique.md](./technique.md) 第 2 节：补充现状（支付记录早于支付尝试写入；Payment 按提交查询不看 Intent 状态，终止后仍返回最终尝试状态）、到期后 8 类场景的行为差异、影响面（过期扫描、Payment、Token 暴露面、Client API、路由、checkout-web、部署协调），待决定 D1 | 已确认（D1 到期后 Payment 未找到返回 410） |
| 2026-10-23 | 3 方案与测试设计 | [technique.md](./technique.md) 第 3 节：只改 `GetCustomerPaymentProcessor`（到期检查挪到两个未找到分支，删除响应前复查，不新增领域方法）、错误优先级、接口规范核对、设计文档修订措辞、6 个新增 auto 用例（AC-05/06 由现有用例覆盖）、本机 e2e 场景与回滚 | 已确认 |
| 2026-10-23 | 4 实现与 auto 测试 | 执行代理 f1-impl：`GetCustomerPaymentProcessor`（到期检查移入两个未找到分支、删除响应前复查、注释更新）、`GetCustomerPaymentTest` 新增 6 个方法 8 例、`PaymentResultTestMapper` 新增 `deletePayment` / `expireOrder`；主会话修订 Client API v1 设计第 523、622 行。验证：三类 19/19、24/24、74/74；acquiring `auto` 全量 847/847（工作区即 main `98210e0b` + 本需求 3 个文件；与 code-mor-78 确认无并发测试）。说明：3DS 交互截止与订单到期分开计算，用例中交互截止设在拨后时钟之后 | 已确认 |
| 2026-10-23 | 5 本机 e2e | [阶段 5 报告](./verification/stage5-report.md)（执行代理 f1-e2e，08:27—08:29Z）：主会话把本机 acquiring 换成 `dca404ec…`（main `a816bd14` + 本需求，旧 JAR `.pre-f1`，原参数与 XXL 环境重启），合成 PayerMax 临时换 3DS 扩展版、验后换回原版。S1 挑战中到期 → 查询 REQUIRES_ACTION → 成功 → PAID；S2 → FAILED → 扫描 EXPIRED 后仍 FAILED；S3 5 秒内只查单一次；S4 未提交到期 410（到期前 404）；S5 日志泄漏 0。AC-01—AC-06、B4 通过；B3、D1 本机无法制造，以 auto 用例为证。预期现象：本机无 billing，PAID 订单的 `ORDER_PAYMENT_SUCCEEDED` 补偿事件不可路由失败。残留：S3 订单 OPEN、尝试 PROCESSING | 已确认 |
| 2026-10-23 | 6 发布准备 | [发布包](./release/release.md)（执行代理 f1-rel，只读）：回滚目标 acquiring `27c36f4e…`（JAR `ec8487bd`，billing B 覆盖生效）；候选 `f6d55ade…`（本地 tag `mor-test/acquiring-service:f1-result-20261023`，现网镜像 + 阶段 5 JAR `dca404ec`）。JAR 差异 +19 −0 ~17：acquiring-core 只有 `GetCustomerPaymentProcessor.class`；另有 platform-support-api、http-client-support 两个库来自 main `f707f2da` / `be688db7`（现网 JAR 在 d3d39e40 基线 worktree 构建，缺这两个提交），acquiring 未引用或仅加法变更。环境 36/36 相同，只继承 `PAYTECH_INSTANCE_ID`。mor-test 无可演示本需求的现存订单。待用户决定 D1（接受两库差异）、D2（构建方式）、D3（P-B 合成订单） | 已确认（用户“继续”“你直接执行吧”：D1、D2 接受，D3 执行 P-A + P-B，授权阶段 7） |
| 2026-10-23 | 7 mor-test 部署与验收 | 主会话：S0 通知 code-mor-78 / db / d6；S1 基线全部一致；S2 快照备份 `~/Code-MoR-backups/20261023T1001Z-f1-result-mor-test/`；S3 10:03:04—10:03:35Z 替换 acquiring-service → `f6d55ade…`（JAR `dca404ec`），healthy、重启 0、ERROR 0、环境 SAME 36/36、执行器重新注册、分区消费者与 DLQ 不变，替换后任务 23 15/15、任务 4 29/30（1 次进行中）成功。S4 验收（执行代理 f1-s7，[记录](./release/release.md) 第 11 节、[acceptance/](./release/acceptance/)）：P-A1 未提交到期 410 且 Payment 0 次；P-A2 夹具 PAID 订单 410 且 Payment 恰 1 次刷新返回 404（D1）；P-B 合成 600.00 USD 订单 `ord_3L5iBmKka8K2co3YqrB8Ni5DWZU9yk` 卡支付 FAILED `THREE_DS_CHANNEL_UNAVAILABLE`（渠道 0 次），自然到期后扫描前 200 FAILED（旧版 410）、查看收银台与重新提交 410、Client GET 无链接，任务 23 扫描后订单 / 会话 / 投影 / Intent EXPIRED、`order.expired` 1 行，再查仍 200 FAILED；日志泄漏 0。偏差：P-B 的 Client 侧按阶段 5 做法私网直连 acquiring client-gateway-api（mor-test 无可用 Client 凭证）；P-A3 前后对照未做。AC-01、AC-03、B3 以阶段 5 与 auto 用例为证。回滚目标 `27c36f4e…` | 完成 |
| 2026-10-23 | 8 提交与台账 | 用户“继续”授权提交（不含推送）。私有索引提交本需求：acquiring 3 个文件、Client API v1 设计修订、需求目录、`requirements/README.md` 本需求行、`CURRENT_IMPLEMENTATION.md` 台账；提交 `a2b7f85a`（父 `28939303`，未推送）；提交后重建核对见下一行 | 完成 |
| 2026-10-23 | 8 重建核对 | 干净 worktree 按 `a2b7f85a` 构建 acquiring（JAR `5d81d9fd`），与 mor-test 运行的 `dca404ec` 逐条目比较：+50 −0 ~0，全部是 `paytech-platform-support-api` 中多语言管理（`ed361c26`，在 `a816bd14` 之后合入 main）新增的 `localization` 类与 `LocalizationErrorCode`，acquiring 不引用；acquiring 自身模块、配置与其他库完全相同。[差异](./release/snapshot/jar-diff-deployed-vs-commit-rebuild.txt)。下次替换 acquiring 时自然带上，不单独重发 | 完成 |

## 当前阶段与下一确认点

阶段 1—8 完成；推送另行授权。

## 实现 / 验证 / 部署状态

未开始；台账见 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md)。
