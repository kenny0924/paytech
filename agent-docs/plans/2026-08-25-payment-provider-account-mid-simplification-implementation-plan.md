# Payment Provider Account 与 MID 精简实施计划

> **已被后续决策取代：** 本文中的旧 31 表/399 字段与后续 29 表/382 字段基线、`t_payment_execution_gate`/`t_payment_execution_gate_result` 持久化设计、`t_three_ds_context` 和 `provider_token_ref` 均已取消。当前权威依据为 [Payment Execution 精简设计](./2026-08-25-payment-execution-simplification-design.md)、[`PAYMENT_ORCHESTRATION_DATABASE.md`](../database/PAYMENT_ORCHESTRATION_DATABASE.md) 与 [`PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md`](../../payment-orchestration-service/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md)，当前基线为 29 表/376 字段/63 关系。本文仅对 Provider Account/MID 对应五表模型的历史决策仍有参考意义，其余内容仅供历史追溯。

状态：已完成；31 表 PDManer 工件、业务文档、现实场景、requirements、自动契约和导航已同步并通过验证，PDManer 4.9.3 应用内人工检查仍待用户完成。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把已确认的 Provider→Provider Account→MID、静态 Capability 和动态 Health State 设计同步到 Payment 权威文档、requirements、确定性生成器、自动测试和 PDManer 模型。

**Architecture:** Provider 保持渠道业务身份，Provider Account 统一承担技术认证账户，MID 同时冻结 Provider 业务归属和 Provider Account 执行绑定。Capability 使用固定 Scope 加有类型 Key-Value，Health State 保存当前短窗口聚合；不可变 Candidate 冻结当次路由实际使用的健康指标。

**Tech Stack:** Markdown、TypeScript/JavaScript 内容目录、Node.js `node:test`、PDManer 4.9.3 JSON、确定性 ESM 生成器。

**Spec:** `agent-docs/plans/2026-08-25-payment-provider-account-mid-simplification-design.md`

## Global Constraints

- Payment 物理表总数固定为 31；Provider/MID 分组固定为 5 表。
- 物理 Manifest 固定为 31 表、399 字段、43 个唯一索引、48 个访问索引、70 条本库关系、8 个强制 1:1 关系和 7 个 Diagram Groups；生成结果不一致时先逐项定位设计或生成缺口，不能直接改测试常量。
- 删除 `t_provider_connection`、`t_provider_connection_version`、`t_mid_limit`、`t_mid_credential_ref`、`t_mid_health`；新增 `t_provider_account`、`t_mid_health_state`。
- 删除所有 `connection_id`、`connection_version_id`、`selected_connection_version_id`、`mid_credential_ref_id` Payment 字段；对应交易事实改为冻结 `provider_account_id` 或 `selected_provider_account_id`。
- `t_provider_mid` 同时保存 `provider_id` 与 `provider_account_id`，并强制二者属于同一 Provider。
- Capability 固定五维 Scope，使用 `BOOLEAN/DECIMAL/INTEGER/CODE` 四种类型且每行恰有一个值列。
- Health State 只保存当前窗口聚合，原始 Metrics/Trace 不进入 MySQL；Candidate 冻结历史路由实际使用的指标。
- Provider Secret、Endpoint、证书、Webhook Secret 与轮换版本不进入 Payment 数据库；`integration_config_key` 只是非敏感配置定位键。
- 所有物理表使用 `t_`，唯一索引使用 `uk_`，普通索引使用 `idx_`；表名和字段说明使用直接中文。
- 只修改文档、requirements、测试、生成器和 PDManer 设计资产；不创建 Migration、Java、数据库、环境、分支、提交或外部资源。
- 保留工作区内现有用户修改；不重排共享模板，不手工编辑生成的 PDManer JSON。

---

### Task 1: 先把自动契约切换到 31 表目标模型

**Files:**
- Modify: `scripts/pdmaner/payment-model.test.mjs`
- Modify: `scripts/pdmaner/payment-scenario-doc.test.mjs`
- Modify: `requirements-site/tests/global-use-case-sequences.test.mjs`

**Interfaces:**
- Consumes: spec 中五表字段、索引、关系和下游冻结字段。
- Produces: 独立测试 Oracle；生成器、PDManer 和文档必须满足它，测试不得从生成器导入期望值。

- [ ] **Step 1: 将 Provider/MID 表集合改为五表并加入负向约束**

  精确集合为：

  ```js
  [
    "t_payment_provider",
    "t_provider_account",
    "t_provider_mid",
    "t_mid_capability",
    "t_mid_health_state",
  ]
  ```

  全模型表数断言改为 31。测试同时断言五张旧表不存在，并断言旧连接/凭据字段不在任何表中。

- [ ] **Step 2: 冻结新字段与关键约束 Oracle**

  测试必须逐字段覆盖：

  ```text
  t_provider_account:
    provider_account_id, provider_id, account_code, external_account_id,
    integration_config_key, account_status, create_time, update_time

  t_provider_mid:
    mid_id, provider_id, provider_account_id, external_mid_code,
    operating_entity_id, mid_status, activated_at, closed_at,
    create_time, update_time

  t_mid_capability:
    mid_capability_id, mid_id, payment_method_code, scheme_code,
    country_code, currency, mcc_code, capability_code, value_type,
    boolean_value, decimal_value, integer_value, code_value,
    capability_status, create_time, update_time

  t_mid_health_state:
    mid_health_state_id, mid_id, payment_method_code, health_status,
    circuit_status, health_score, sample_count, technical_success_count,
    provider_error_count, timeout_count, technical_success_rate,
    provider_error_rate, timeout_rate, p95_latency_ms,
    consecutive_failure_count, window_started_at, window_ended_at,
    observed_at, valid_until, create_time, update_time
  ```

  冻结 `uk_provider_account_code`、`uk_provider_account_external`、`uk_provider_account_config`、`uk_provider_account_owner`、`uk_provider_mid`、`uk_mid_capability_scope`、`uk_mid_health_state` 和对应访问索引。

- [ ] **Step 3: 冻结下游 Provider Account 与 Health Snapshot**

  断言 Decision、Candidate、Action、Callback、Dispute Action 均使用新 Account 字段；Candidate 还必须有 `circuit_status、health_score、technical_success_rate、timeout_rate、p95_latency_ms、sample_count、health_observed_at`。

- [ ] **Step 4: 更新场景与全局序列契约**

  场景测试要求标题为 `31 表覆盖核对`、覆盖 31 表且每张表只在覆盖目录出现一次。`PaymentAttemptUnknown` 的追踪 Tuple 固定为 `Provider ID + Provider Account ID + MID ID + Payment Action ID`，不得再包含 Connection Version。

- [ ] **Step 5: 运行 RED**

  Run:

  ```bash
  node --test scripts/pdmaner/payment-model.test.mjs scripts/pdmaner/payment-scenario-doc.test.mjs
  npm --prefix requirements-site test
  ```

  Expected: Payment 模型/场景因当前仍为 34 表和旧字段而失败；requirements 中 Connection Version 旧语义至少有一个精确失败。不得通过放宽断言获得 GREEN。

---

### Task 2: 同步权威业务文档、现实场景与 requirements

**Files:**
- Modify: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`
- Modify: `agent-docs/database/scenarios/PAYMENT_REAL_WORLD_SCENARIOS.md`
- Modify: `agent-docs/plans/2026-08-25-payment-balanced-physical-model-design.md`
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`

**Interfaces:**
- Consumes: Task 1 的 31 表和字段 Oracle。
- Produces: 生成器和物理决策使用的权威业务语义；全局事件契约使用 Provider Account Tuple。

- [ ] **Step 1: 把 Payment 逻辑目录和 Provider/MID 章节改为 31/5**

  用 spec 的五张表、字段、唯一键、状态、Capability 解析规则、Health 聚合规则和配置安全边界完整替换旧 8 表章节。其它 26 张表职责保持不变。

- [ ] **Step 2: 替换所有下游冻结字段和不变量**

  精确替换：

  ```text
  selected_connection_version_id -> selected_provider_account_id
  connection_version_id          -> provider_account_id
  删除 mid_credential_ref_id
  ```

  Decision、Candidate 与首个资金 Action 必须逐值一致；退款、Query、争议 Action 也冻结自己的 Provider Account。

- [ ] **Step 3: 重写 NI 现实数据**

  使用 `provider_account_id=81101`、`account_code=NI-API-AE`、`external_account_id=NI-TENANT-AE-01`、`integration_config_key=payment.ni.ae.primary`。Capability 使用同一 Scope 下的 `ACTION.SALE=1`、`ACTION.REFUND=1`、`SECURITY.3DS_MODE=SUPPORTED`、`AMOUNT.MIN=1`、`AMOUNT.MAX=5000` 行。Health State 为 MID-A/MID-B 填入完整窗口计数、Rate、Score、Circuit 和时间值，Candidate 冻结这些值。

- [ ] **Step 4: 标注旧平衡型设计被本设计局部覆盖**

  在旧设计开头增加醒目状态说明，并将其 34 表、Connection Version 和 Credential 相关章节指向新 spec；不删除历史文档。

- [ ] **Step 5: 同步 requirements 与全局序列**

  PAY-I03、PAY-I11、Payment Source of Truth 和 `PaymentAttemptUnknown` 使用 Provider Account 新语义；保持 Operating Entity 先冻结、MID 每 Attempt 选择和 UNKNOWN 占位不变。

- [ ] **Step 6: 运行文档/requirements 局部验证**

  Run:

  ```bash
  node --test scripts/pdmaner/payment-scenario-doc.test.mjs
  npm --prefix requirements-site test
  ```

  Expected: scenario 和 requirements 契约通过；模型测试仍因生成器/PDManer 未更新而保持 RED。

---

### Task 3: 更新确定性生成器并重新生成 PDManer

**Files:**
- Modify: `scripts/pdmaner/generate-payment-model.mjs`
- Modify: `scripts/pdmaner/payment-field-metadata.mjs`
- Generate: `payment-orchestration-service/docs/database/payment-orchestration.pdma.json`
- Test: `scripts/pdmaner/payment-model.test.mjs`

**Interfaces:**
- Consumes: Task 1 的独立 Oracle、Task 2 的权威语义。
- Produces: 31 表确定性 PDManer 模型及精确 totals/digests。

- [ ] **Step 1: 替换字段 Manifest**

  删除旧四表和旧 Health 定义，加入 spec 的 Account、Capability、Health State 字段；替换五张下游表的 Account 字段并扩展 Candidate Health Snapshot。

- [ ] **Step 2: 替换索引和关系 Manifest**

  Provider/MID 图固定表达：Provider→Account、Provider→MID、Account→MID、MID→Capability、MID→Health State。下游五条 Connection Version 关系改指 Provider Account，删除 Action→MID Credential 关系。预期全模关系数为 70、强制 1:1 关系数为 8、Diagram Group 为 7；如生成器得出不同值，必须逐关系核对而不是直接改常量。

- [ ] **Step 3: 添加物理 CHECK 契约**

  至少包含：Account 状态；MID 时间矩阵；Capability value-type XOR 与 Boolean；Health Score/Rate/Count/时间窗口；Decision SELECTED Account 非空；Candidate 静态淘汰允许健康字段空、进入健康阶段则指标完整；现有其它 CHECK 保持。

- [ ] **Step 4: 更新中文元数据和代码目录声明**

  `capability_code` 注明 MID Capability Catalog；`payment_method_code/scheme_code/mcc_code/country_code/currency` 保持现有权威目录。表中文名固定为 `支付服务商账户、服务商商户号、商户号静态能力、商户号动态健康状态`，字段说明不得带机械表名前缀。

- [ ] **Step 5: 生成并验证确定性**

  Run:

  ```bash
  node scripts/pdmaner/generate-payment-model.mjs
  cp payment-orchestration-service/docs/database/payment-orchestration.pdma.json /tmp/payment-orchestration-first.pdma.json
  node scripts/pdmaner/generate-payment-model.mjs
  cmp /tmp/payment-orchestration-first.pdma.json payment-orchestration-service/docs/database/payment-orchestration.pdma.json
  node scripts/pdmaner/generate-payment-model.mjs --check
  node --test scripts/pdmaner/payment-model.test.mjs
  ```

  Expected: 两次生成字节一致，`--check` exit 0，模型测试全部通过并输出 31/399/43/48/70/7 以及新的 digests。

---

### Task 4: 冻结物理结果、更新导航并执行完整回归

**Files:**
- Modify: `payment-orchestration-service/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/README.md`
- Modify: `agent-docs/PROJECT_CONTEXT.md`
- Modify: `agent-docs/DATABASE_DESIGN_GUIDE.md`
- Modify: `agent-docs/database/README.md`
- Modify: `README.md`
- Modify: `AGENTS.md`
- Modify: `agent-docs/plans/2026-08-25-payment-pdmaner-implementation-plan.md`

**Interfaces:**
- Consumes: Task 3 生成器实际输出的 totals、SHA-256、关系数和 CHECK 清单。
- Produces: 全仓唯一一致的 Payment 当前基线与人工 PDManer 门禁说明。

- [ ] **Step 1: 用生成结果刷新物理 Manifest 与 CHECK**

  totals 和三个 SHA-256 必须逐字来自生成器/测试输出，不手算摘要。删除旧 Connection/Limit/Credential CHECK，加入 Account/typed Capability/Health State/Account Freeze CHECK，并更新跨行事务不变量。

- [ ] **Step 2: 更新所有当前导航和计数**

  当前入口指向新设计；旧 34 表实施计划标为历史且被新计划局部取代。所有“Payment 当前”计数统一为 Task 3 实际值，仍注明 PDManer 4.9.3 应用内检查待人工完成，以及 Migration/Java/环境未授权。

- [ ] **Step 3: 扫描旧语义残留**

  Run:

  ```bash
  rg -n "t_provider_connection|t_provider_connection_version|t_mid_limit|t_mid_credential_ref|selected_connection_version_id|connection_version_id|mid_credential_ref_id|Provider/MID 8|34 表|34张表|34 tables" \
    AGENTS.md README.md agent-docs payment-orchestration-service requirements-site scripts/pdmaner \
    --glob '!agent-docs/plans/2026-08-25-payment-pdmaner-implementation-plan.md'
  ```

  Expected: 只允许新设计中明确描述“已删除”的历史说明、旧平衡型设计的历史段落和必要负向测试出现；任何当前态声明或模型字段残留必须修复。

- [ ] **Step 4: 运行完整验证**

  Run:

  ```bash
  node --check scripts/pdmaner/generate-payment-model.mjs
  node --check scripts/pdmaner/payment-field-metadata.mjs
  node --test scripts/pdmaner/payment-model.test.mjs scripts/pdmaner/payment-scenario-doc.test.mjs
  node scripts/pdmaner/generate-payment-model.mjs --check
  npm --prefix requirements-site test
  git diff --check
  ```

  Expected: 所有命令 exit 0；任何与本改动无关的既有失败必须单独列出，不能伪装为本轮通过。

- [ ] **Step 5: 独立审查**

  审查 31 表集合、Provider/Account/MID 关系、Capability typed-value XOR、Health Current-vs-History 边界、Candidate 历史快照、Secret 边界、中文说明、导航计数和无跨库关系。只在审查问题清零或有明确书面裁决后声明完成。
