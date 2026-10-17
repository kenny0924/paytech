# 结账点实时累计：交付记录

## 1. 登记（2026-10-17）

- 来源：上一需求“结账点运行期评估”完成后，用户在 5 个风控候选中选择“1 结账点实时累计”。
- 适用阶段：默认 8 阶段。

## 2. 阶段 1 结果（2026-10-17，评审稿）

[需求](./requirement.md)：背景与目标、场景、范围与不做项、已确认事实（只读调研）、AC-1～AC-6、待决 Q1～Q7。只写文档。

用户 2026-10-17 确认阶段 1：Q1～Q7 按建议。

## 3. 阶段 2 结果（2026-10-17，评审稿）

[技术方案](./technique.md)：三路只读调研（Risk、Operations Web/BFF、一次性容器性能演练，210 万事件，容器已删除）；更正需求第 6 节（BFF 需改并同批发布）；待决 D1～D4。未修改代码或数据。

用户 2026-10-17 确认阶段 2：D1～D4 按建议（D1=A：Client、运营主体限制指标与窗口，改索引并固定查询计划；D2 BFF 纳入、Risk → BFF → Web 同批发布；D3 `CHECKOUT_INPUT_SNAPSHOT` + 快照 ID；D4 接受并发计数偏低）。

## 4. 阶段 3 结果（2026-10-17，评审稿）

[方案设计](./design.md)、[db/ddl.sql](./db/ddl.sql)、[db/ddl-rollback.sql](./db/ddl-rollback.sql)（未在任何环境执行）。未修改代码或数据。

用户 2026-10-17 确认阶段 3（方案按 design.md）。

### 本机 DDL（2026-10-17，用户授权“可以，执行 DDL”）

- 备份：`~/Code-MoR-backups/20261017-risk-checkout-velocity-local/20261017T052610Z/`（`mor_risk` 全库 dump、执行前三表 `SHOW CREATE`、行数，SHA256SUMS；目录 0700、文件 0600，不入库）。执行前 85 表，累计事件、主体、Feature 定义均 0 行，`idx_velocity_subject` 为旧 4 列。
- 执行 `db/ddl.sql`：成功。核对（information_schema）：85 表；`idx_velocity_subject` = `subject_type, subject_key, event_code, occurred_time, velocity_event_id`，注释一致；`event_code` 注释 4 个允许值；`t_list_feature_definition` 表注释为“风控点输入字段”。

### 查询计划复测（2026-10-17，一次性容器，已删除）

新 5 列索引 + 当前 mapper SQL（金额合计、最近一次为 STRAIGHT_JOIN），与阶段 2 同量数据（210 万事件）。D1 允许组合 5 次最大值：次数（≤24 小时）27.8 ms；金额合计（≤1 小时）13.1 ms；最近一次 / 距上次秒数 ≤0.33 ms（所有窗口，Backward index scan，无排序）。48 组查询均以事件主体表为驱动表，宽窗口金额合计不再全表扫描（热点运营主体 7 天 10.6 s → 0.99 s，仍在禁止范围）。

### 本机遗留数据清理（2026-10-17，用户授权“可以，删除后重跑”“可以”）

上一需求阶段 5 经接口建的结账配置无测试前缀，导致 Risk 全量 70 个失败（期望 N、实际 N+2）。均已无发布引用。删除（单事务，先 ROLLBACK 空跑核对）：流程 `CHECKOUT_BASELINE_ALLOW`、`CHECKOUT_STAGE5_BLOCK_FLOW`（流程 2、版本 2、节点 7、连线 5）；规则集 `CHECKOUT_STAGE5_BLOCK`（规则集 1、版本 1、规则 2、节点 2、操作数 4、动作 2）。驳回原因字典保留。可从 DDL 前全库备份恢复。阶段 5 经接口重建结账基线。

## 5. 阶段 4 结果（2026-10-17，评审稿）

- **Risk：**按 design 第 3～5 节实现（事件枚举与风控点归属、描述符目录、FeatureHandlers 按点校验与 D1 组合、通用事件构造、结账写事件、创作上下文 `featureCatalogs`、Mapper STRAIGHT_JOIN、PDManer/Schema/物理决策）。偏离见 design 第 11 节。
- **BFF：**模型、转换器、模拟器与边界测试；`RiskVariableResponseShape` 不改。
- **Web：**变量页按风控点目录选择事件、主体、指标、窗口与名单；点名称取本地映射。
- **审查：**独立只读审查无阻断问题；6 个中低级问题（点名称显示编码、Web 写死受限主体、D1 拒绝用例不全、结账非法用例编码前缀、测试字面量、常量位置与窗口回退）已全部修复。
- **测试：**Risk 全量 auto 2083/2083；BFF 全量 auto 1379/1379（审查修复未涉及 BFF）；Web 全量 1067/1067，`npm run build` 成功。
- **数据库：**本机 `mor_risk` 已执行 `db/ddl.sql`；遗留结账配置已清理（见上）。查询计划复测通过。
- 未提交、未推送、未部署。

用户 2026-10-17 确认阶段 4（“继续”，含阶段 5 本机写入授权）。

## 6. 阶段 5 结果（2026-10-17，评审稿）

[本机验收记录](./verification/stage5-local-acceptance.md)：AC-1～AC-6 通过（13 笔正式评估 / 15 条事件；同一 Client 第 4 笔审核、名单 Client 拒绝；5 类非法创建 400；影子回放按原评估时间；浏览器 11/11；Risk 评估 19～36 ms、Commerce 端到端 50～100 ms）。验收数据已清理，生效发布为默认放行基线 `237109668147642368`；Commerce 已停止，新 Risk（18104）、Ops BFF（18201）运行中。发现：`~/.m3` 中 platform-support-api 早于 14381f9，BFF 改用临时聚合 POM 同 reactor 构建；Commerce 清理脚本补 `t_order_legal`（另有 4 行历史孤立 `t_order_legal` 未删）。

用户 2026-10-17 确认阶段 5（“继续”）。

## 7. 阶段 6 结果（2026-10-17，评审稿）

[mor-test 发布清单](./release/release.md)（只读核对，未改 test）：test Commerce 已真实调用 Risk 结账评估（6 笔订单、6 条 ALLOW）；构建基线为 HEAD `14381f9` 的临时 worktree + 本需求改动，随带上一需求 Web `87fd57f`；`fe5a0f3` 不涉及 BFF，test Platform 已含。待决 D1～D4。

用户 2026-10-17 确认阶段 6（“继续”）：D1～D4 按建议；授权 test 构建、备份、执行 db/ddl.sql、依次替换 Risk/BFF/Web、写入验收数据并清理。

## 8. 阶段 7 结果（2026-10-17，评审稿）

[test 发布与验收记录](./release/test-acceptance.md)：DDL 07:27Z；Risk `725f7d12aef4`（07:36Z）→ BFF `4b702a37c151` → Web `03668fd01a22`（07:37Z），均 healthy、Nacos 单实例、无 ERROR。回归 35 个只读接口一致；featureCatalogs 经 BFF 与直调一致；5 类非法创建 400；合成订单第 4 笔审核、名单 Client 拒绝、事件 7 条正确、解释含累计值；影子按原评估时间；评估 11～17 ms；浏览器 13/13（含随带上线的 `87fd57f`）。审核规则实际生效 12 秒，窗口内无 Commerce 建单。清理后 test 无结账 Feature 变量（三者可一起回退）；结账生效发布为同一基线流程版本的新发布 `237117104682274816`（原 `236943228086095872` 已 SUPERSEDED）。备份 `~/Code-MoR-backups/20261017-risk-checkout-velocity-test/20261017T072650Z/`。已通知并行会话窗口结束。

用户 2026-10-17 确认阶段 7（“确认”）。

## 9. 阶段 8（2026-10-17）

用户确认阶段 7 后：更新[风控标准目录](../../../agent-docs/RISK_CONTROL_STANDARDS.md)（结账点状态、结账事件、低基数主体限制）与 [CURRENT_IMPLEMENTATION](../../../agent-docs/CURRENT_IMPLEMENTATION.md)；以私有 index 本地提交（未推送），只含本需求文件与共享文件中本需求片段。
