# 支付反欺诈一期：mor-test 部署与验收记录

- 日期：2026-10-15（UTC 06:20～06:25）
- 授权：用户确认 [发布清单](./release.md) 与 D1～D3 按建议（回复“可以”）
- 结果：**已部署 mor-test，验收通过**；只替换 Risk，其余 15 个容器镜像不变

## 1. 执行记录

| 步骤 | 结果 |
|---|---|
| 复核 | 与清单第 1 节一致：16 个容器 healthy；Risk image `9f9f0141be7c`；`mor_risk` 81 表；待改表为空；Nacos `risk-service` 一个健康实例 |
| 备份 | `~/Code-MoR-backups/20261015-risk-payment-fraud-test/20261015T062054Z/`（0700/0600，SHA256SUMS）：`mor_risk` 全库导出、执行前后容器与 image ID、各表行数、Compose 原文件、Risk 容器 inspect、SQL 回执。不进入版本库 |
| 停 Risk | 06:21:30 停止，`risk_app` 连接 0 |
| DDL | `db/ddl.sql`（utf8mb4 客户端）成功，84 表 |
| DML | `db/dml.sql` 成功；决策点 210002、点版本 220002（500ms、FROZEN）、动作 240006～240008、13 个支付变量、3 个成员；中文名称正常 |
| 迁移后核对 | 与仓库正式 Schema 对比：列 1034、索引行 295、表注释 84，差异 0；行数变化恰为 DML 新增行，结账与尽调数据不变 |
| Compose | `compose.applications.yaml` 的 `risk-service` 增加 `RISK_DECISION_ENVIRONMENT: TEST`（D2） |
| 应用 | 以候选 image `e0b7a8d484eb` 启动，06:22:43 healthy；Nacos 一个健康实例。Risk 停机约 73 秒 |
| 清理 | 验收后 `test-acceptance-cleanup.sql` 先 ROLLBACK 试运行再 COMMIT；各表行数回到迁移后、验收前状态；临时代理已删除 |

## 2. 验收

| 类别 | 结果 |
|---|---|
| 只读回归 | 经临时代理（D3）：结账规则集列表（4）、流程列表（1）、旧候选发布（10）、变量（17）、规则集版本历史、发布引用、两类创作上下文、拒绝原因字典、入驻材料要求均 200，数量与库内一致 |
| 业务验收 | 验收驱动 **61 项全部通过**（[证据](./test-acceptance-evidence.md)）：未配置 503 → 名单、变量、规则集、流程配置 → 发布生效 → 放行、放行需 3DS、审核、拒绝 → 卡高频第 4 次拒绝 → 重放、409、422、400 → 影子运行（8 样本，认证要求变化 4，失败 0）后替代 |
| 在用保护 | 删除生效发布引用的规则集返回 409 `RISK_RULE_SET_IN_USE`，规则集不变 |
| 日志 | 无 IP、User-Agent、卡指纹、消费者引用原值；唯一 ERROR 为验收第一步预期的未配置 503 |
| 写入范围 | 只写支付决策点配置与评估、1 个 `ACCEPT_REASON_` 拒绝原因；已全部清理 |

## 3. 当前状态与遗留

- test 的 Risk 为提交 `06025a2`，支付决策点已初始化但**没有生效发布**：调用统一评估接口返回 503 `RISK_DECISION_NOT_CONFIGURED`，直到运营配置并激活支付流程。
- test 未部署 Payment；Operations 后台尚不能配置结束节点附加输出。
- 回退方式见 [release.md 第 7 节](./release.md#7-恢复)：新镜像已处理请求，回退应使用 `test-rollback.sql` 而非整库还原。
