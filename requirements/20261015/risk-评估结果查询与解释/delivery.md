# 评估结果查询与解释：交付记录

## 1. 登记（2026-10-15）

- 来源：支付反欺诈一期遗留第 4 项（RCO-I06、RCO-I10 未实现）；用户暂缓授权结果回流后选择本项，回复“可以”开始阶段 1。
- 适用阶段：默认 8 阶段。

## 2. 阶段 1 结果（2026-10-15，评审稿）

[需求](./requirement.md)：目标、场景、本期范围 4 项、不做项、已确认事实、AC-1～AC-8、待决 Q1～Q4。只写文档，未修改代码、配置或数据库。

用户 2026-10-15 确认阶段 1：Q1 改为**输入原值原样返回**（“可以展示吧，又不敏感”），Q2～Q4 按建议。

## 3. 阶段 2 结果（2026-10-15，评审稿）

[技术方案](./technique.md)第 1～6 节：需求项差异、系统影响、待决 D1～D3、工作量与风险。只读核对了 mor-test `mor_risk`（4 条支付正式评估、1 个生效发布），未修改代码、配置或数据库。

用户 2026-10-15 确认阶段 2：D1～D3 按建议（常量与集合显示当前值并标注；失败、超时补写过程记录；影子补写轨迹）。

## 4. 阶段 3 结果（2026-10-15，评审稿）

[方案设计](./design.md)：运行期补记、新表 `t_evaluation_flow_trace`、3 个 GET 接口合同与错误、代码改动、测试与 AC 映射、部署与恢复。未修改代码、配置或数据库；`db/ddl.sql` 在阶段 4 随 PDManer 一并生成。

用户 2026-10-15 确认阶段 3（回复“继续”），进入阶段 4。

## 5. 阶段 4 结果（2026-10-15，已确认）

- 代码、PDManer、完整 Schema（85 表）、`db/ddl.sql`、文档与交接单完成；实施偏离见 [design.md 第 9 节](./design.md#9-阶段-4-实施偏离2026-10-15)。
- **本机 SQL（用户授权“执行”）：**备份 `~/Code-MoR-backups/20261015-risk-evaluation-query-local/20261015T184500Z/`（0700/0600，84 表，含 SHA256SUMS，不入库）；执行 `db/ddl.sql` 后 `mor_risk` 为 85 表，新表结构与完整 Schema 一致。回滚：`DROP TABLE t_evaluation_flow_trace`。
- **测试：**新增 `EvaluationQueryApiAutoTest` 21 例；扩展评估与影子运行测试断言流程与规则轨迹；数据库前置检查改为 85 表。首轮发现常量当前值返回 `2.00000000`，已改为数值统一去尾零后重跑通过。Risk 全量 auto **2022/2022**（原 2001 + 新增 21）。
- 未执行：打包 jar 真实 HTTP 验收与评估耗时对比（阶段 5）；未提交、未部署。

用户 2026-10-15 确认阶段 4（回复“继续”），进入阶段 5。

## 6. 阶段 5 结果（2026-10-15，已确认）

[验收报告](./verification/stage5-local-acceptance.md)：打包 jar 经真实 HTTP 71 项全部通过；评估耗时新版与基线持平；日志无受限原值；验收数据已清理，85 表行数与验收前一致。

用户 2026-10-15 确认阶段 5（回复“可以，继续”），进入阶段 6。

## 7. 阶段 6 结果（2026-10-15，已确认）

[发布清单](./release/release.md)：test 现状只读核对、候选镜像 `727d51127e61`（本机容器冒烟通过）、SQL 演练（结构与正式 Schema 逐项一致，回滚可回到原结构）、清理 SQL 演练、执行顺序、验收与恢复；待决 D1（验收写入 3 个合成评估后清理）、D2（临时代理）。未对 test 做任何写入。

用户 2026-10-15 确认发布清单及 D1、D2 按建议（回复“可以”），执行阶段 7。

## 8. 阶段 7 结果（2026-10-15，已确认）

[部署与验收记录](./release/test-acceptance.md)：备份后在 test `mor_risk` 执行 ddl（85 表，与 Schema 一致），Risk 换为 image `727d51127e61`（约 24 秒，其他容器不变）；20 个只读接口回归一致；验收 34 项通过；日志无受限原值；合成数据已清理，临时代理已删除。

**待提交差异：**`risk-compliance-service` 52 个文件（与部署镜像构建源码一致）；需求目录本身；`agent-docs/database/RISK_COMPLIANCE_DATABASE.md`、`agent-docs/RISK_CONTROL_STANDARDS.md`、`requirements-site/content/requirements.ts` 各自本需求的行；`requirements/README.md` 与 `agent-docs/CURRENT_IMPLEMENTATION.md` 只提交本需求的行。

## 9. 阶段 8 提交（2026-10-15，用户回复“可以”）

- `a55b3b1` feat(risk): evaluation decision and explanation queries（代码 52 + 需求目录 + 3 个文档 + README 本需求行，64 个文件）
- `20dc65c` release(risk): deploy evaluation queries to mor-test（发布清单、验收记录与证据、清理 SQL、候选 Compose + CURRENT_IMPLEMENTATION 本需求一节）
- 核对：`20dc65c` 的 `risk-compliance-service` 与 mor-test 部署镜像的构建源码逐文件一致；暂存区未夹带其他会话改动；未推送。

## 10. 遗留

- Operations 页面与 BFF 接入（[交接单](./handoff/operations.md)）。
- 部署前写入的评估没有流程路径。
- 常量与集合不随评估冻结，解释中按当前值显示。
- 按影子批次列出样本、按订单列出评估：未做，需要时另提需求。
