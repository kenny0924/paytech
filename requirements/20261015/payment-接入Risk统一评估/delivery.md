# Payment 接入 Risk 统一评估接口交付记录

## 适用阶段（已确认）

| 阶段 | 安排 |
|---|---|
| 1. 需求评审与范围确认 | 已确认 |
| 2+3. 现状差异与方案设计 | 已确认（合并） |
| 4. 研发实现 | 已确认 |
| 5. 综合验证与本地验收 | 已完成 |
| 6–7. test 发布准备与部署 | 本期不适用：Payment 尚未部署到 mor-test；运营激活 mor-test 支付发布后如需联调另行确认 |
| 8. 提交 | 已本地提交（未推送） |

## 确认记录

| 日期 | 阶段 | 用户原意 | 获准的下一步 |
|---|---|---|---|
| 2026-10-15 | 选题 | 转交 Risk 的 Payment 接入交接摘要（统一评估接口合同、17 个字段、结果处理、幂等与测试场景） | 登记需求并交付阶段 1 |
| 2026-10-15 | 选题补充 | 正式任务说明：删除草案客户端、改用 `RiskDecisionApi`；17 个字段全部补齐，包括卡指纹和卡 BIN；结果未知由恢复任务重试；只改 `payment-orchestration-service`，Risk 合同的疑问记下来问用户，不改 Risk 代码；受限值不进日志和 MQ；test 联调等运营激活后再做 | 维持阶段 1 评审稿，等待确认 Q-01～Q-04 |
| 2026-10-15 | 1 | 问“是否一定要恢复任务？”；对“不做恢复任务、请求内重试一次后失败关闭”及 Q-01、Q-03、Q-04 按建议回复“可以” | 阶段 2+3 方案设计 |
| 2026-10-15 | 2+3 | 对技术方案回复“可以” | 阶段 4 研发（含本机 `mvn install` risk-api） |
| 2026-10-15 | 4 | 对阶段 4 结果回复“可以” | 阶段 5 综合验证 |
| 2026-10-15 | 5 | 对“V-07 是否执行、R-01 选择、是否在阶段 5 结束”回复“可以”；按执行 V-07、R-01 采用建议 ② 理解（作为其他系统的后续事项，本需求不改代码） | 执行 V-07，完成后确认结束 |
| 2026-10-15 | 8 | “提交吧” | 本地提交本需求范围的改动；不推送、不部署、不执行 SQL |

## 当前阶段与下一确认点

已本地提交（提交号见 `git log`，提交说明含本需求目录名）。阶段 6–7 等运营激活 mor-test 支付发布后另行确认；R-01 作为 CCT/Commerce/Payment 后续需求。

## 阶段 4 证据（2026-10-15）

**代码（只改 `payment-orchestration-service`）：**

| 模块 | 改动 |
|---|---|
| card | `CardService.riskAttributes()`、`CardRiskAttributes`；Mock 申请时计算 BIN（前 8/6 位）与 HMAC-SHA256 指纹；`payment.card.mock-fingerprint-key`（Mock 开启时必填） |
| execution | `PaymentRiskCommand` 扩为 17 字段、`PaymentRiskResult` 改为 evaluationId；`PaymentRiskClient.evaluate`；`ActionTransition.fail` 与 `ActionFlow.failAttempt`；`CardPayFlow` 未放行走 fail；`PayRiskCheckActionActor` 组装输入、409/不可用重试一次、失败关闭与脱敏 WARN 日志，不再写 `channel_response_code` |
| web | 依赖 `risk-api`；`RiskDecisionApi` Bean；`PaymentRiskClientImpl` 改为统一评估；删除草案 `PaymentRiskAssessmentApi` 及请求/响应类；`risk-read-timeout` 默认 1s |
| test | `RiskMocker`/`MockRiskController` 改为 `RiskDecisionApi`（顺序响应、Problem、延迟、请求记录）；抽出 `AbstractPayCardTest`；新增 `PayRiskEvaluationTest`（20 例）；`PayCardChannelTest` 改用基类，风控不可用用例移入新类 |
| 数据库与配置 | PDManer、Schema、测试 init DDL 同步 `transaction_assessment_id` 注释；[db/ddl.sql](./db/ddl.sql)（只改注释，未执行）；`application.yml`、Nacos 模板、配置清单、README |

**验证：**

- `risk-api`：`./mvnw -o install -pl risk-api -am -DskipTests`，只安装到本机 Maven 仓库，未改 Risk 代码。工作区中 risk-api 另有其他会话未提交的改动，本次安装包含了它们，只影响本机编译依赖。
- Payment：`./mvnw -o verify`（JDK 25，本机 MySQL 13306、Redis 16379），BUILD SUCCESS，**83/83**，其中 `PayRiskEvaluationTest` 20、`PayCardChannelTest` 7。
- `npm --prefix requirements-site test`：137 项中 128 通过、9 失败；失败断言为 Risk 等模型统计行、Commerce 与公共规范内容，均不读取本次修改的内容，属于既有失败。

**验收映射：** AC-01 草案删除、`RiskDecisionApi`；AC-02 `test_allowWithoutAuthentication` 比较请求合同与全部输入；AC-03 同例及 `test_allowManualCapture`；AC-04 `test_allowWithThreeDs`；AC-05 `test_notAllowed`×4（含释放后重新支付成功）；AC-06 `test_definiteFailureWithoutRetry`×5 与 503 两次；AC-07 409 两次 / 409 后放行；AC-08 读超时两次 / 超时后放行，风控动作为 FAILED 而非 UNKNOWN；AC-09 `test_restrictedValuesNotLogged`；AC-10 以上全部从 pay HTTP 入口经真实客户端，`AfterEach` 检查意外调用。

**偏离方案：** 无。补充：Card 卡材料不存在时 `riskAttributes` 返回 null；Card 抛出的业务/系统异常同样失败关闭。

## 阶段 5 验证（2026-10-15）

| 编号 | 检查 | 结果 |
|---|---|---|
| V-01 | Payment 全量 `./mvnw -o verify`（含 `application.yml` 最终版本重跑） | BUILD SUCCESS，83/83 |
| V-02 | 场景矩阵：从 pay HTTP 入口经真实 `RiskDecisionApi` 客户端到测试 HTTP 服务 | AC-02～AC-10 共 20 例通过（见阶段 4 验收映射） |
| V-03 | 变更类覆盖率（JaCoCo 聚合） | `CardPayFlow`、`ActionTransition` 行 100%；`PayRiskCheckActionActor` 55/57 行（未覆盖：非卡工具、卡材料缺失两个分支，当前只有卡工具）；`PaymentRiskClientImpl` 22/22 行、分支 11/14；`ActionFlow` 87/91 行。Card 模块不在聚合报告范围（既有配置），其 BIN/指纹由 `test_allowWithoutAuthentication`、`test_cardFingerprintStableAcrossAttempts` 经入口断言 |
| V-04 | 受限值 | 日志断言通过；受限值不入库（无新字段）、不进 MQ（本需求无消息）、不进对外响应（PayResponse 未变）；承载对象 `toString` 脱敏 |
| V-05 | 事务边界 | Risk 调用在 `ActionFlow.execute` 的结果事务之外，结果写入复用原单个短事务；失败结束在同一结果事务内完成 Attempt 失败与 Intent 释放 |
| V-06 | 与 Risk 输入合同逐字段核对 | 16 个字段一致；`industryCode` 存在格式差异（V-07 已实测 422），见[技术方案 §8 R-01](./technique.md#8-记录给-risk-的问题)；用户选择 ②（平台统一大写编码并在入口校验），属 CCT/Commerce/Payment 后续需求，本需求不改代码 |
| V-07 | 真实 Risk 本机联调（经授权） | 只读确认本机 `mor_risk` 无任何生效发布、交易评估 0 行后，以 `risk-web-1.0.0-SNAPSHOT-exec.jar`（2026-10-15 09:32 构建）在 `127.0.0.1:18104` 启动（Nacos 关闭，环境 TEST）。按 Payment 生成格式发送 17 字段请求 → **503 `RISK_DECISION_NOT_CONFIGURED`**；只含必填字段 → 503；对照组 `industryCode=digital-goods` → **422，`errors[0].field=inputs.industryCode`**，证明校验先于发布查询执行，Payment 请求格式通过真实校验。503 为 `application/problem+json`，Header 与 Body 的 Request ID 一致，符合 Payment 严格解码器（归类为依赖不可用、重试一次）。完成后 `mor_risk` 交易评估、正式评估均为 0 行（未写入）；Risk 已停止 |
| V-08 | `requirements-site` | 137 项中 128 通过、9 项既有失败（与本次改动无关） |

## 证据、SQL、提交

SQL 未执行；已本地提交，未推送、未部署。共享文件 `requirements/README.md`、`agent-docs/CURRENT_IMPLEMENTATION.md` 只提交本需求的行。
