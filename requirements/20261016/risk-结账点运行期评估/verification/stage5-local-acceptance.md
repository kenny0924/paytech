# 阶段 5 本机验收记录（2026-10-16）

## 环境

- Risk：本需求工作区构建的 `risk-web` jar（SHA-256 `baa1a685…292d`），在 18104 按原环境变量重启，注册到 Nacos `mor-local`；健康 200，启动无 ERROR。
- Commerce：本需求工作区构建的 jar（SHA-256 `15b40ae9…62dc1`），临时在 18102 启动（Nacos `mor-local`，snowflake worker 5，`command-idempotency.enabled=false`），验收后已停止。创建结账中只有 Risk 是真实调用（CCT、税、文件为进程内固定 Mock：AE001 / PLATFORM / RETAIL / 5999）。
- Operations Console：https://127.0.0.1:18444（Nginx → Gateway → Ops BFF → Risk），前端为 vite dev server（含本需求改动）。

## 结账基线与验收发布（经 Risk 接口）

| 发布 | 流程 | 结果 |
|---|---|---|
| 236937225848242176 | `CHECKOUT_BASELINE_ALLOW`（开始 → 放行） | 首次激活成功（220001 校验摘要在真实运行中通过）→ 后被替代 |
| 236938662812598272 | `CHECKOUT_STAGE5_BLOCK_FLOW`：规则集 `CHECKOUT_STAGE5_BLOCK`（净额 > 1000 拒绝，原因 `CHECKOUT_STAGE5_HIGH_NET`；国家 KP 审核） | 校验通过；影子回放 2 个结账正式评估（结论变化 0）；激活 |
| 236939885468991488 | `CHECKOUT_BASELINE_ALLOW` 同一版本 | 影子回放 4 个样本（拒绝 → 放行 1）后激活；**当前生效：默认放行基线** |

## Commerce → Risk 端到端

| 场景 | Client 收到 | 订单 | Risk |
|---|---|---|---|
| 放行（2 行，44.90 / 2.00 / 42.90） | 201 | 落库，`risk_checkout_assessment_id` = Risk 评估 ID | ALLOW；快照 44.9 / 2 / 42.9、2 行、RETAIL、5999、无 Local Partner |
| 净额 1003.90 | 422 `CHECKOUT_NOT_ELIGIBLE` | 不落库 | DENY（原因码） |
| 消费者国家 KP | 422 `CHECKOUT_NOT_ELIGIBLE` | 不落库 | REVIEW |
| Risk 停止 | 503 `DEPENDENCY_UNAVAILABLE` | 不落库 | Commerce 日志“重试一次”“重试后仍未知” |
| Risk 重启后 | 201 | 落库 | ALLOW |
| Risk 直调：同订单同内容重放 / 内容不一致 | 原结果 / 409 | — | — |

Commerce 日志只含 orderId、clientId、evaluationId、结论、异常类名。

## 浏览器（[stage5_browser.py](./stage5_browser.py)，结果 [stage5-browser-results.txt](./stage5-browser-results.txt)）：9/9 通过

按订单 ID 查询（URL `type=order`）、结账结论卡片无“认证要求”、业务身份“订单”、12 行输入、路径与规则命中、影子表无“认证要求变化”、执行发布页可选结账准备且无“暂不支持”、决策流提示、控制台与 HTTP 无错误。**未覆盖：**本机支付点没有执行发布，支付侧“认证要求变化”指标未在浏览器复核（源码由 `hasAuthenticationOutput` 控制，单元测试覆盖）。

## 清理

- `mor_risk`：[stage5-cleanup-risk.sql](./stage5-cleanup-risk.sql) 先 ROLLBACK 核对后 COMMIT：评估 12（正式 6、影子 6）、流程轨迹 30、规则轨迹 22、快照 6；补跑一次清掉最后的冒烟订单（评估 1）。结账评估与快照回到 0。
- `mor_commerce`：[stage5-cleanup-commerce.sql](./stage5-cleanup-commerce.sql)：订单 3（会话、合同、投影、明细、上下文同步删除），补跑 1。
- 保留：结账执行发布与流程/规则集/拒绝原因 `CHECKOUT_STAGE5_HIGH_NET`（配置历史）；生效发布为默认放行基线。注意：在本机 `mor_risk` 跑 Risk 自动测试会清掉结账执行发布，之后需按本记录重新创建基线。
- Commerce 已停止（避免与 Commerce 自动测试共用 `mor_commerce` 和 MQ 队列冲突）；Risk 新 jar 继续运行在 18104。
