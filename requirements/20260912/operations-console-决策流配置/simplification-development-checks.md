# RCO-17 精简修订：开发检查记录

2026-09-12。本记录保存修订后的开发证据，当前状态以 [CURRENT_IMPLEMENTATION.md](../../../agent-docs/CURRENT_IMPLEMENTATION.md) 为准。本次修订的阶段 4 研发及开发回归已完成，用户已确认恢复阶段 5 综合验证，进展见 [综合验证记录](./acceptance.md)。本机三表 SQL 已按具体授权备份后应用。原 [开发交付报告](./development-checks.md) 的完整回归属于精简前源码，不能替代本修订验证。

## 1. 本次代码准备

- Risk 使用同一输入目录，规则版本直接保存准确输入合同，Context 条件和动作参数直接保存字段代码。正常保存去掉旧协议、字段快照和顺序绑定的新增，普通内容读取不再要求旧组成。
- 已有绑定的冻结/退休保留窄状态兼容。旧 Release 复用同一规则内容投影，并从原 Point Version 的字段快照恢复原物理 ID；新规则不满足旧 Candidate 创建资格，返回现有 `BUSINESS_CHECK_FAILED`。
- BFF/Web 的四类规则响应及流程内嵌规则详情接受保留键的显式 null；Candidate 自身仍要求真实 ID。页面布局和接口版本保持。
- 唯一模型、完整 Schema 与 Mapper 对齐三表修改。无新增 API、初始化或路由注册数据；本轮不删除整张表。

## 2. 已运行检查

| 检查 | 结果 | 本机日志 |
|---|---|---|
| Risk 完整 reactor、真实 HTTP/MySQL/Redis | 208/208，0 失败/错误/跳过 | `/tmp/rco17-simplification-risk-full.log` |
| Risk 补充纯行为/协议/列表重建 | 52/52，与完整 reactor 中重叠用例不重复累计 | `/tmp/rco17-simplification-risk-pure.log` |
| Risk 最终冲突分类与 JDBC 合同回归 | 8/8；最终清理旧冲突识别及无用导入后复跑 | `/tmp/rco17-simplification-risk-final-targeted.log` |
| BFF 完整 reactor | 1,051/1,051，0 失败/错误/跳过 | `/tmp/rco17-simplification-bff-tests.log` |
| Web 全部测试 | 900/900 | `/tmp/rco17-simplification-web-tests.log` |
| Web TypeScript 与生产构建 | 通过；保留既有公共 chunk 体积提示 | `/tmp/rco17-simplification-web-build.log` |
| Risk 模型/Schema | 30/30，包括三表准确字段、旧索引移除和历史表保留 | `/tmp/rco17-simplification-model-tests.log` |
| 原流程设计/注册与需求内链接 | 通过；原四表/118 行注册未扩大 | `/tmp/rco17-simplification-design.log` |
| 四个工作区差异空白检查 | 通过 | `git diff --check` |

新增规则无旧组成的 HTTP 测试在修改前已观察到失败，旧保存返回非空 `pointVersionId`，见 `/tmp/rco17-simplification-risk-red.log`。BFF/Web 也先观察到旧严格解码拒绝合法 null；修订后连同精确键、非法 ID 和内嵌规则响应一起验证。BFF 通用损坏响应测试仅为对应规则路径排除合法 null，未放宽 Candidate 或缺键检查。

测试侧新增 [LegacyRuleSetTestMapper](../../../paytech/risk-decision/paytech-risk-decision-test/src/test/java/com/paytech/risk/test) 直接准备迁移后的历史事实；旧发布与原顺序图场景不再借新版保存制造旧编排。真实数据库回归已通过。旧 Release 投影准确还原原 Context ID，原九字段快照保持九字段，规范 Hash 与保存值一致，成功回放及静态校验通过。现有 golden vectors 继续校验旧规范字节/Hash；这些证据不等于对 test 现存全部历史逐条完成迁移验证。

## 3. 修订验收映射与后续阶段

| 范围 | 开发证据 |
|---|---|
| SIM-01 / SIM-05 | 新建、连续保存、退休、冻结及重放通过；规则版本独立生成，旧三表零新增，新 Candidate 请求返回 400/BUSINESS_CHECK_FAILED；既有绑定的冻结/退休及 Candidate 回放通过 |
| SIM-02 / SIM-03 | 新旧规则可在同一流程中保存、编辑、准确历史读取；新规则内嵌详情显式 null，旧规则保留原 Point ID；直接合同不匹配返回引用不可用；非法字段/用途、退休新引用及删除后新保存被拒绝 |
| SIM-04 | 旧规则、Point、Release 规范投影与 golden vectors、历史字段 ID 还原、原字段子集、旧 Candidate 读取/静态校验及成功回放均通过；真实 test 历史仍须在其迁移窗口逐条比对 |
| SIM-06 | BFF/Web 严格键、null/合法 ID、非法 ID、保存导航和历史来源验证通过；未改页面布局。完整视觉与真实入口串联归阶段 5 |
| SIM-07 | 真实 Redis 争用、并发保存/冻结/删除、成功回执稳定性、旧 ACTIVE Release 阻止整集删除通过；流程保存/校验无运行事实副作用 |
| SIM-08 | 模型/Schema/Mapper 对齐新增字段与移除列/索引，生产保存不含旧三表 INSERT；三表 44 列结构及索引吻合，新四列注释正确。39 个旧列注释差异与变更前相同，保留为既有环境差异 |

本机执行证据见 [应用记录](./db/risk-compliance-service/simplification/local-application.md)。回归结束后核对规则根/版本/条件/参数、旧组成/Release、流程根/版本/节点/边均为 0 行，测试数据已清理。阶段 4 中修正了历史测试造数列名、旧 Mapper/DO 约定、旧并发断言，以及两处字段代码的 nullable JDBC 类型；未恢复新保存对旧编排的依赖。

下一步按已获确认的阶段 5 完成原约定的视觉/交互、故障矩阵及真实 Gateway/Snapshot/权限联调。test 历史 462 行仅是此前只读快照；未迁移、未共享发布或部署。若后续需要这些环境动作，仍按具体范围授权。本轮没有 Git 提交/推送。
