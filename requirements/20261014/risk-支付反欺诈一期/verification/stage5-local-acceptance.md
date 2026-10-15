# 阶段 5 综合验证与本地验收报告

- 日期：2026-10-15
- 范围：risk-compliance-service（只在本机；不涉及 Payment、Operations BFF/页面、test 环境）
- 结论：**本地验收通过**，遗留项见第 5 节，待用户确认

## 1. 真实入口完整链路

用本次提交打包的 `risk-web-1.0.0-SNAPSHOT-exec.jar`，以生产配置文件（`application.yml`）启动在 `127.0.0.1:18104`：本机 `mor_risk`（应用账号 `risk_app`）、Redis、RabbitMQ，Nacos 注册关闭，发布环境 `TEST`。临时验收驱动只经 HTTP 调用，逐步记录请求与响应，见[证据](./stage5-evidence.md)，**61 项检查全部通过**。

| 步骤 | 验证内容 | 结果 |
|---|---|---|
| 未配置 | 没有生效发布时评估返回 503 `RISK_DECISION_NOT_CONFIGURED` | 通过 |
| 配置 | 拒绝原因、卡指纹黑名单与成员、名单命中变量、卡指纹 1 小时评估次数变量（实时累计）、拦截与认证两个规则集（创建 + 冻结）、决策流（含“放行（需要 3DS）”结束节点） | 通过；流程详情显示放行结束节点自动补齐 `NOT_REQUIRED` |
| 发布 | 创建执行发布 → 静态校验（SHADOWING）→ 首发生效（ACTIVE） | 通过 |
| 四种结果 | 小额放行无需认证；1500 放行并要求 3DS；6000 审核；黑名单卡指纹拒绝 | 通过 |
| 实时累计 | 同一卡指纹第 1～3 次放行，第 4 次（含本次 > 3）拒绝 | 通过 |
| 重放与参数 | 同一请求重放返回原 evaluationId；换风控 Action 返回 409；金额非法 422；未登记风控点 400 | 通过 |
| 影子与替代 | 第二个发布（3DS 门槛 50）影子运行 8 个历史样本完成：结论变化 0、认证要求变化 4、失败 0、超时 0；生效后原发布 SUPERSEDED，新规则立即生效 | 通过 |

**日志检查：** 应用日志中未出现 IP、User-Agent、卡指纹、消费者引用等受限原值（`/risk-decisions`、`/risk-lists` 等路径只记元数据）。三条 ERROR 日志均为“未配置”场景的 503，由 Foundation 的 5xx 统一记 ERROR，属预期。

**数据恢复：** 验收前记录 84 张表行数并做只读备份；验收后用测试套件自身的支付数据清理与按编码删除验收创建的拒绝原因，恢复后 84 张表行数与验收前逐表一致。验收期间持有本机库串行锁，未与测试并行。

## 2. 新增自动化测试

| 用例 | 覆盖 |
|---|---|
| `PaymentRiskAssessmentAvailabilityApiAutoTest#test_businessRuleViolation_timedOut` | 执行超过冻结决策点版本超时：TIMED_OUT 落库（失败代码 `EVALUATION_TIMEOUT`），无业务决定、无认证要求、无累计事件；重放返回原结果。用 Mocker 把决策点版本超时改为 -1 强制超时，Clear 先恢复原值 |
| `PaymentRiskAssessmentApiAutoTest#test_edgeCase_concurrentSameAttempt` | 同一支付尝试 6 个请求同时提交：只形成 1 次评估与 1 条累计事件；成功响应为同一 evaluationId，抢锁失败的返回 409 `CONCURRENT_REQUEST` |

两条用例连续执行 5 次均通过。

## 3. 测试与覆盖率

- Risk 全量 `auto`：**1991/1991** 通过（阶段 4 结束时 1989，新增 2）。
- Risk PDManer：模型与 Schema 测试 30/30；全部 Risk PDManer 测试 188 项中 17 项失败，与改动前相同，均为既有问题。
- JaCoCo（全量 `auto`）：

| 范围 | 行 | 分支 |
|---|---|---|
| 支付运行期相关 25 个类（引擎、评估器、快照、Feature、发布内容、影子） | 94.7% | 80.1% |
| DecisionEngine | 91.3% | 78.5% |
| PaymentAttemptEvaluator | 100% | 92.9% |
| EvaluateDecisionProcessor | 96.2% | 80.0% |
| EvaluationInputContract | 100% | 96.4% |
| ShadowRunSummaries / ShadowRunHandler | 94.9% / 87.9% | 81.6% / 50.0% |
| DecisionFlowValidatorImpl | 88.9% | 91.7% |
| DecisionFlowConditionNormalizer | 66.2% | 57.9% |
| 模块整体：risk-decision / risk-control / risk-compliance / risk-web | 87.9% / 92.1% / 94.6% / 95.8% | 64.5% / 67.9% / 86.3% / 87.2% |

阶段 4 补测后支付运行期为行 94.0% / 分支 76.7%，本次提升主要来自超时与并发用例。

## 4. 验收标准对照

| AC | 结论 |
|---|---|
| AC-1 | Risk 侧满足（三种结论 + 放行必带认证要求）；Payment 侧接入不在本会话范围 |
| AC-2 | 满足：重放同一身份与结论；并发同一 Attempt 只计一次累计 |
| AC-3 | 满足（阶段 4 窗口与算法测试） |
| AC-4 | 黑名单与成员失效满足；白名单放宽未单独编规则测试 |
| AC-5 | 满足：结束节点输出 `THREE_DS_REQUIRED` |
| AC-6 | 满足：影子运行汇总结论与认证要求变化，正式结论不受影响 |
| AC-7 | 满足：评估、快照、Feature 结果、规则轨迹落库（阶段 4 测试）；查询接口 RCO-I06/I10 尚未实现 |
| AC-8 | 满足：FAILED 与 TIMED_OUT 均落库并不放行 |
| AC-9 | 黑名单、单笔金额、频次、3DS 已经真实链路命中；累计金额与多卡关联只验证累计值计算；白名单未单独编规则测试 |

## 5. 遗留与建议

1. **规则集删除未检查执行发布引用（已修复，2026-10-15 用户确认后）：** 实施时发现更严重的既有缺陷——旧“发布引用”查询与“损坏发布计数”会把执行发布当作缺少决策点版本绑定的损坏发布，支付点一旦有生效执行发布，规则集删除、列表、版本历史与发布引用查询都返回 500。修复：两条旧查询只看旧候选发布（`decision_flow_version_id IS NULL`）；新增按决策流节点查询被 ACTIVE / SHADOWING 执行发布引用的规则集，并入“正在使用”判断，删除返回 409 `RISK_RULE_SET_IN_USE`，列表 `inUse` 同步。新增入口测试 `ExecutionReleaseApiAutoTest#test_businessRuleViolation_ruleSetInUse`（撤掉修复时该测试失败）。**仍未纳入：** 规则集“发布引用”分页列表与其 `inUse` 只展示旧候选发布，响应字段按决策点版本绑定设计，纳入执行发布需另行设计接口。
2. **旧格式流程条件未覆盖：** 条件规范化中结账旧格式（schemaVersion=1、来源 CONTEXT）的分支在自动化测试中未执行，`DecisionFlowConditionNormalizer` 行覆盖 66.2%。该格式只服务结账旧流程，建议在结账运行期立项时一并补测或评估下线。
3. **影子补偿任务的异常分支：** `ShadowRunHandler` 分支 50%，未覆盖批次不存在、格式错误、单样本失败与超时，属补偿任务防御分支。
4. **未实现的接口：** 查询风险决定（RCO-I06）与评估解释（RCO-I10）尚无接口；AC-7 目前只能通过库内记录核对。
5. **跨会话事项：** Payment 接入统一评估接口；运营后台（BFF/页面）配置结束节点附加输出。不在本会话范围。
