# 结账点运行期评估：技术方案

- 阶段 2：现状差异与影响分析（2026-10-16，已确认：D1～D4 按建议）
- 需求：[requirement.md](./requirement.md)（Q1 由本会话实现 Commerce，Q2～Q7 按建议）
- 证据：三路只读调研（Risk、Commerce、Operations）与 mor-test/本机只读查询

## 1. 结论摘要

- 涉及 **Risk、Commerce、Operations Web** 三处；Operations BFF 只改 1 行注释；Platform 不改。
- **Risk 是主体工作量**：运行期链路目前按支付写死（引擎、Feature 计算、影子回放的入参都是支付输入类型），需要抽出通用的“运行期输入”，再加结账评估器、结账快照、点版本种子。评估接口、评估器登记、执行发布的检查与激活、评估查询都已通用，可直接复用。
- **Commerce 改动集中**：风控端口换成真实实现、补两个金额字段、出站客户端与测试替身；目前 Commerce 没有未提交的改动。
- 有 4 处需要你定口径（第 4 节 D1～D4），其中 D1 建议**修订阶段 1 的 Q4**：不新增错误码，沿用 Client API 合同中已有的 `CHECKOUT_NOT_ELIGIBLE`。

## 2. 需求项差异

| 需求项 | 当前行为及证据 | 缺口 | 处理建议 |
|---|---|---|---|
| 结账评估器（AC-1、AC-2） | 统一评估按“风控点:合同版本”登记评估器，只有支付（`EvaluateDecisionProcessor:28-35`）；结账请求返回 400 | 缺评估器 | 新增 `CheckoutPrepareEvaluator`：合同、重放（按订单 ID）、执行、保存、查询用的业务身份与输入 |
| 运行期入参 | `DecisionEngine.evaluate`、`FeatureValueReader.read` 入参是 `PaymentRiskInput`（实际只用 `contractValues()`、`subjectKey()`、金额币种） | 绑死支付 | 抽出通用运行期输入接口，支付、结账各自实现；结账不计算实时累计与名单 Feature（结账点不开放这两类变量） |
| 评估事实构造 | `PaymentRiskFacts` 中 Feature 结果、规则轨迹、流程轨迹、`Run` 实际通用，评估行/快照/累计事件为支付专属 | 混在一起 | 通用部分拆到共用类；结账另建评估行与快照构造；结账不写累计事件（不做项） |
| 执行发布支持结账（AC-3） | 唯一的闸：结账 descriptor `runtimeSupported=false`（`DecisionPointAuthoringDescriptor:51`）；执行发布还要求点版本种子（只有支付 220002） | 闸关闭；缺点版本 | 打开结账运行期；新增结账执行用的 FROZEN 点版本种子（DML，hash 由 descriptor 计算） |
| 附加动作 | descriptor 中“支持附加动作”= 非运行期；运行期点禁止附加动作，带附加动作的规则集版本发布检查失败 | 打开运行期后结账失去冷却/标签 | 见 D3 |
| 输入合同 v5（AC-4） | `ORDER_PREPARATION` v4 有 locale；变量 91001～91010 引用 v4；变量每次加载按代码合同复核（`RiskVariableDefinition.verifyIntegrity`）；旧编排链路（旧候选、旧格式）多处写死 v4 | 直接删 v4 会使现有变量与规则读不出 | 见 D2 |
| 影子运行 | 样本输入从 `t_transaction_risk_assessment` 还原支付输入；认证要求变化按支付比较 | 只认支付 | 按风控点分派：结账从结账快照还原，结账没有附加输出，“输出变化”恒为否 |
| 结账快照（AC-1） | `t_checkout_input_snapshot` 为旧结构（25 个非空列含 assignment、协议、文件 ID；单一 amount），**无代码读写**；mor-test 与本机均 0 行 | 结构不可用 | 按 v5 字段重建该表（DROP + CREATE，同一期对支付快照的做法） |
| 旧测试候选发布 | 与执行发布同表，靠 `decision_flow_version_id` 是否为空区分；旧候选不会占用生效槽位 | 无 | 保持只读（Q3），不迁移 |
| Commerce 风控端口（AC-5） | 桩直接返回订单 ID（`CustomerCheckoutIntegrationConfiguration:49-54`）；风控在事务外调用，异常时不落订单；评估 ID 冻结到 `t_order_context.risk_checkout_assessment_id`（BIGINT NOT NULL） | 未接入 | 新增 `CheckoutRiskPortImpl` 调 `RiskDecisionApi`；删除桩；表结构不变 |
| Commerce 输入事实 | 端口入参没有商品金额、折扣金额（订单上已算好）；消费者国家非空；Local Partner 仅 PARTNER 来源有值 | 缺两个金额 | `InitializationRequest` 补商品金额、折扣金额（或由明细汇总） |
| Commerce 错误码 | Client API 已确认合同：Risk 拒绝收敛为 `422 CHECKOUT_NOT_ELIGIBLE`（决定文档第 142 行、设计第 1176 行），错误码已存在 | 无 | 见 D1 |
| Commerce 结果未知 | 出站客户端关闭自动重试；端口抛 503 类异常时返回 503 | 需按 Q5 实现 | 同一订单 ID 重试一次，仍未知返回 503，订单不落库 |
| Commerce 测试 | 测试目前直接用生产桩；规范要求外部 HTTP DTO Mock | 缺 Risk 替身 | 新增 Risk Mock Controller 与 Mocker（参照 Payment 的 `MockRiskController`），默认放行 |
| 评估查询（AC-6） | BFF 不限风控点；Web 写死支付尝试、17 个支付输入字段、认证要求行 | Web 只认支付 | Web 增加“订单 ID”查询类型，输入字段与认证要求按风控点显示 |
| Operations 执行发布页 | Web 常量 `EXECUTION_POINTS = ['PAYMENT_ATTEMPT_RISK']` 拦住结账；决策流页对非支付点提示“不会启用执行” | 一行常量与提示文案 | 加入结账点，改提示 |
| 本机/test 可用性（Q6） | 结账点没有生效发布时评估 503 → Commerce 全部拒绝 | 需要基线 | 阶段 5/6 准备“开始 → 放行”的默认结账流程并激活 |

## 3. 系统影响

| 系统 | 影响点 | 改动 | 风险 |
|---|---|---|---|
| risk-decision | 运行期输入接口、引擎与 Feature 入参、评估事实拆分、结账评估器、结账快照 DO/Mapper/Repository/保存服务、影子分派、descriptor 运行期开关、合同 v5 | 代码 | 运行期链路重构影响支付：以支付全量测试兜底 |
| risk 数据库 | 重建 `t_checkout_input_snapshot`；DML：结账点版本种子、v5 变量迁移（见 D2） | DDL + DML、PDManer | 两张表都无数据；DML 需在 test 执行 |
| risk-test | 结账测试基类、Builder、TestMapper、Mocker；支付 TestMapper 的全表清理改为按风控点过滤 | 测试 | 清理范围变化需全量回归 |
| commerce-service | 端口实现、删除桩、请求模型补字段、出站客户端配置（`risk-service`，读超时约 1 秒）、pom 依赖 risk-api、测试替身与用例 | 代码、配置 | Commerce 的 `pom.xml`、`application.yml`、`CreateCheckoutProcessor` 近期被其他需求改过，当前无未提交改动 |
| operations-console-web | 执行发布可选结账点；决策流提示；评估查询增加订单 ID 与结账输入字段、认证要求按点显示 | 代码（约 60～100 行 + 测试） | 无他人未提交改动 |
| operations-console-bff | 无（只改一行注释） | — | — |
| 文档 | Commerce Client API 合同（若采用 D1 建议则只补说明）；风控标准目录 RCO-I03 状态；Risk 数据库设计；CURRENT_IMPLEMENTATION | 文档 | 共享文件只改本需求的行 |

## 4. 需要你定的口径

| 编号 | 问题 | 选项 | 建议 |
|---|---|---|---|
| D1 | Client 看到的拒绝错误码（**修订 Q4**） | A. 沿用已确认合同的 `422 CHECKOUT_NOT_ELIGIBLE`（Risk 拒绝或审核都返回它）<br>B. 按 Q4 新增 `CHECKOUT_RISK_REJECTED` | **A**。合同早已约定“Risk 拒绝 → `CHECKOUT_NOT_ELIGIBLE`”，不改 Client API，不暴露风控原因 |
| D2 | 输入合同 v5 如何落地 | A. 新增 v5（结账运行期用），**保留 v4 只供旧数据读取**；用 DML 把现有结账输入变量（除 locale）的合同版本改到 v5，`INPUT_LOCALE` 停用；旧编排链路改用固定的 v4 旧版 descriptor<br>B. 不升版本，沿用 v4，Commerce 传一个固定 locale | **A**。B 等于伪造输入；mor-test 结账规则与流程都没有引用 locale，迁移无数据影响 |
| D3 | 结账点的附加动作（冷却、打标签） | A. 与支付一致：运行期点不支持附加动作，已有带附加动作的结账规则集版本仍可读，但不能进入执行发布（发布检查会给出原因）<br>B. 保留附加动作配置，执行时忽略 | **A**。B 会让运营误以为动作会执行；附加动作本就“只配置、不执行”。mor-test 现有结账规则集中有 11 个附加动作，运营需另存不带附加动作的版本后再发布 |
| D4 | 结账评估的执行明细输入 | 合同中“明细行数”之外，是否把明细（商品类型、税类）也送给 Risk | **只送行数**。明细级规则本期不做，避免合同变成数组结构（评估查询与 BFF 目前只支持扁平字段） |

## 5. 工作量与风险

- **Risk：**约 15～20 个类新增或改动（含运行期重构），1 张表重建、1 组 DML；新增结账测试类 2～3 个，支付测试回归。
- **Commerce：**约 6～8 个类与配置；新增测试替身 2 个与用例若干。
- **Web：**约 10 处改动、60～100 行业务代码与测试。
- **风险 1（重构支付链路）：**运行期输入抽象会改动支付评估的引擎与 Feature 代码；以 Risk 全量自动测试（当前 2022 项）兜底。
- **风险 2（合同迁移）：**旧编排链路多处写死 v4，需要统一改为读取固定的旧版 descriptor；以旧候选、旧规则集的既有测试兜底。
- **风险 3（环境可用性）：**激活结账评估后，没有生效结账发布的环境里 Commerce 创建结账全部失败；本机与 test 都要先激活默认放行基线（test 目前无 Commerce，不受影响）。
- **部署顺序：**Risk 先（DDL/DML、镜像），再 Commerce；test 只部署 Risk。

## 6. 下一阶段（阶段 3）范围

v5 合同字段与格式、结账快照表 DDL、DML（点版本种子、变量迁移）、Risk 运行期抽象与新类清单、Commerce 端口与错误处理、Web 改动点、测试矩阵与 AC 映射、部署与恢复。
