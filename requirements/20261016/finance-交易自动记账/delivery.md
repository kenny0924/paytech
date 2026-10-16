# Finance 交易自动记账 — 交付记录

## 阶段确认

| 阶段 | 日期 | 结论 |
|---|---|---|
| 1. 需求评审与范围确认 | 2026-10-16 | 用户在“支付成功入账”评审中选择“方案 1”（Finance 规则与过账引擎先行 + 跨系统依赖清单），D1—D4 按建议执行 |
| 2. 现状差异与影响分析 | 2026-10-16 | 用户确认 |
| 3. 方案、接口与数据设计 | 2026-10-16 | 用户回复“可以”确认，并授权本地 DML |
| 4. 研发实现 | 2026-10-17 | 用户回复“继续”确认 |
| 5. 综合验证与本地验收 | 2026-10-17 | 结果已提交，待用户确认结束 |

## 动作授权

| 动作 | 状态 |
|---|---|
| 本地 SQL（P5 初始化 DML） | 2026-10-16 已执行（见下） |
| 提交、推送、部署 | 未授权 |

### 本地 SQL 执行（2026-10-16）

- 环境：`mor-local-mysql-1` / `mor_finance`，账号 `finance_app`。
- 前置核对：`t_accounting_rule_bundle` / `_ver` / `_line` 均 0 行。
- 执行：[`db/dml.sql`](./db/dml.sql)。
- 执行后核对：1 / 1 / 12 行；Bundle 5001 `ACTIVE`、`current_ver_id=5101`；规则行 ID 5201—5212。同步追加到 `finance-service/docs/database/sql/finance-initial-data.sql`。

## 阶段 1 调研记录（2026-10-16）

- Payment `PaymentSucceeded` v1 已发布（`topic_payment_result` / `payment.succeeded.v1`），不含定价、费用、订单上下文与定价版本；`t_payment_pricing_fact` 已于 2026-09-14 删除，替代合同待定。
- Commerce COM-I04、CCT 价格解析均未实现；Finance 税务快照未实现。
- `finance-service/pom.xml` enforcer 禁止 RabbitMQ 与补偿事件组件；`t_compensation_event` 为旧形态。
- 结论与分派见 [跨系统依赖清单](./dependencies.md)。

## 阶段 4 研发验证（2026-10-17）

- 主会话先建共享骨架（5 个枚举并注册 TypeHandler、规则三表 DataObject 与 Repository 接口），再由三个并行 agent 实现：A 规则 Bundle 接口（R1—R6）与数据库设计文档目录段；C 交易经济/组件/渠道清算账户只读持久化与 `TransactionPostingService`；B 测试。主会话转达三方合同细节并合并核对。
- 实施中澄清：`t_channel_account` 配置必须预先存在且 ACTIVE；命中后 `CHANNEL_ACCOUNT` 角色的 `t_account` 与其他角色一样自动创建（technique.md §6 已同步）。
- 实现中比方案更严的校验（均已由测试覆盖）：平台服务费组件 `party_id` 必须等于 `finance_entity_id`；渠道清算账户 `entity_no` 必须等于交易主体；Bundle 本身须 ACTIVE；负金额拒绝；规则行桶组合复用账户身份矩阵（`CLIENT_PAYABLE` 也不允许 NONE）；批准生效时新版本 `effective_from` 必须晚于当前版本。账簿“可用”按 `BookStatus.OPEN` 判断（枚举无 ACTIVE）。
- 合并核对修正：两处测试常量 `"ACTIVE"` 改为 `ReferenceDataStatus.ACTIVE.getCode()`。
- `JAVA_HOME=JDK 25 ./mvnw -o clean verify`：**BUILD SUCCESS，172/172 通过**（新增 38 个：规则生命周期 10、规则校验 11、过账 4、过账拒绝 13；原 134 个保持通过）。
- 测试基础设施：`init/ddl.sql`（管理员手工执行的测试环境脚本）同步 6 张表；清理器把规则三表作为保留表并恢复 Bundle 5001/版本 5101 初始状态，保留行 31 → 45。

## 阶段 5 综合验证与本地验收（2026-10-17）

- `clean verify` 172/172；JaCoCo（命令行，未改 pom）accounting 行 95.9%/分支 81.0%，api 100%，web 行 99.4%/分支 88.9%；合计行 **96.6%**、分支 81.4%。`TransactionPostingServiceImpl` 行 95.2%/分支 80.8%，`CreateRuleVersionProcessor` 96.2%，`SubmitRuleVersionProcessor`/`RejectRuleVersionProcessor` 100%，`ApproveRuleVersionProcessor` 80.0%（未覆盖为指针 CAS 冲突等并发防御分支）。
- 本地 `-exec.jar` 规则接口真实 HTTP 冒烟 **20/20**：预置 Bundle 与版本 1（12 行有序）查询；非法金额来源、Client 应付配 NONE 桶被拒（400），缺 makerId 422；创建版本 2 为 DRAFT、已有草稿时再创建被拒、非编制人提交被拒、编制人提交 PENDING_APPROVAL、Maker 自审被拒、Checker 批准即 ACTIVE 并切换指针、版本 1 RETIRED 且失效时间等于版本 2 生效时间；未来生效的版本 3 只到 APPROVED 且指针不变；版本 4 驳回；列表降序（REJECTED/APPROVED/ACTIVE/RETIRED）；已驳回版本不可再批准；不存在的 Bundle/版本被拒。
- 过账引擎没有 HTTP 入口，验收以领域服务入口的 17 个业务测试为准（含事务回滚与幂等）。
- 冒烟后应用停止；删除冒烟写入的版本与规则行，Bundle 5001 恢复 ACTIVE/当前版本 5101/version 0、版本 5101 恢复 ACTIVE 且无失效时间；规则三表 1/1/12、科目 29、结转规则 7，其余业务表为空，Redis 无残留锁。
- `agent-docs/CURRENT_IMPLEMENTATION.md` 已新增交易自动记账段落，并更新 Finance 行。
