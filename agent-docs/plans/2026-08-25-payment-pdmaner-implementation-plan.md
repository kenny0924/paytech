# Payment Orchestration PDManer Implementation Plan

> **已被后续决策取代：** 本文中的旧 31 表/399 字段与后续 29 表/382 字段基线、`t_payment_execution_gate`/`t_payment_execution_gate_result` 持久化设计、`t_three_ds_context` 和 `provider_token_ref` 均已取消。当前权威依据为 [Payment Execution 精简设计](./2026-08-25-payment-execution-simplification-design.md)、[`PAYMENT_ORCHESTRATION_DATABASE.md`](../database/PAYMENT_ORCHESTRATION_DATABASE.md) 与 [`PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md`](../../paytech/payment/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md)，当前基线为 29 表/376 字段/63 关系。Provider Account/MID 精简文档仅对对应五表模型的历史决策仍有参考意义；本文其余内容仅供历史追溯。

> **已被替代：** 本 34 表实施计划已由 [Payment Provider Account 与 MID 精简实施计划](./2026-08-25-payment-provider-account-mid-simplification-implementation-plan.md) 取代。保留本文仅用于追溯 34 表阶段的执行意图；不得据此恢复 Connection/Version、MID Credential Ref、MID Limit 或旧 Health 表。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把已确认的 Payment Orchestration 平衡型设计落成一套 34 表、可重复生成、可自动校验并可在 PDManer 4.9.3 中人工评审的第一版物理模型。

**Architecture:** 以共享 PDManer 模板为只读输入，用独立字段清单、中文元数据和关系清单确定性生成 Payment 模型。逻辑数据库文档、现实场景、需求目录与物理决策先收敛为同一套业务语言，自动测试再以独立 Oracle 校验表、字段、索引、关系、枚举、模板清洁度和跨域契约。

**Tech Stack:** Markdown、Node.js ESM、`node:test`、PDManer 4.9.3 JSON、requirements-site TypeScript/MJS 内容目录。

**Spec:** `agent-docs/plans/2026-08-25-payment-balanced-physical-model-design.md`

## Global Constraints

- 最终物理模型固定为 34 张表；全部表名以 `t_` 开头，唯一索引以 `uk_` 开头，普通索引以 `idx_` 开头。
- 表名、字段名的显示名称和说明必须为直接、可独立理解的中文；闭合枚举必须在字段说明中列全取值。
- 不创建跨数据库外键；Commerce、CCT、Risk、Finance、Platform、Vault、文件和 Actor 标识只保存业务 ID。
- 不引入通用 Hash、`active_slot`、机械 Version、History、Current Projection、Routing Rule Engine 或独立 Recovery Task。
- PAN、CVV/CVC、完整磁道、PIN/PIN Block 和卡有效期不得进入模型、文件、事件或日志。
- 本计划不创建 Java 模块、Migration、数据库实例或运行环境，不修改共享 PDManer 模板，不提交 Git Commit。
- 先写失败测试，再写最小实现；所有生成物必须支持 `--check` 字节级确定性校验。

---

### Task 1: 冻结 Payment 逻辑数据库文档

**Files:**
- Modify: `agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md`
- Create: `agent-docs/database/scenarios/PAYMENT_REAL_WORLD_SCENARIOS.md`
- Modify: `agent-docs/database/README.md`
- Modify: `agent-docs/README.md`

**Interfaces:**
- Consumes: 设计规范中的 34 表清单、状态机、Scope 优先级、Gate 和 UNKNOWN 恢复边界。
- Produces: Generator、测试和需求目录共同引用的权威表名、字段语义、关系与现实数据样例。

- [ ] **Step 1: 重写逻辑数据库文档的范围和 34 表目录**

  明确 Provider/MID 8、Routing/Gate 7、Payment 9、Refund 2、Callback 3、Dispute 3、Reliability 2；删除旧 58 表中的 Binding、Rule/Condition、History、Projection、Recovery、Refund Attempt/Ref 等已废弃概念。

- [ ] **Step 2: 为每张表写字段、枚举、唯一性、并发和删除理由**

  对 Intent/Attempt/Action/Refund/Callback/Dispute 给出合法状态跃迁；对不可变表明确只追加；对保留的 `version` 逐一解释真实竞争者。

- [ ] **Step 3: 写一份可逐表映射的现实场景**

  使用同一组固定 ID 演示：NI Provider、一个 Operating Entity、两个候选 MID、一次 SALE、一次 3DS、一次 UNKNOWN 查询恢复、一次退款、一次重复成功补偿、一个批量 Callback 和一次争议举证。每个场景都列出实际表行、关键字段和值。

- [ ] **Step 4: 更新两级文档导航**

  Payment 条目必须同时链接逻辑模型、现实场景、物理决策和最终 PDManer；仍标注 PDManer 4.9.3 手工检查未完成。

- [ ] **Step 5: 运行文档扫描**

  Run: `rg -n "routing_policy_binding|routing_rule_condition|payment_recovery_task|channel_refund_attempt|refund_transaction_ref|active_slot" agent-docs/database/PAYMENT_ORCHESTRATION_DATABASE.md agent-docs/database/scenarios/PAYMENT_REAL_WORLD_SCENARIOS.md`

  Expected: 只在“已删除/不采用”说明中出现，不得作为现行表或字段出现。

### Task 2: 同步跨系统契约和全局序列

**Files:**
- Modify: `requirements-site/content/requirements.ts`
- Modify: `requirements-site/content/global-use-case-sequences.mjs`
- Test: `requirements-site/tests/domain-decisions.test.mjs`
- Test: `requirements-site/tests/global-use-case-sequences.test.mjs`

**Interfaces:**
- Consumes: Task 1 的权威状态、接口与事件目录。
- Produces: Commerce、Payment、Risk、Finance 对 Cancel、Void、Refund、UNKNOWN、重复成功和争议的唯一跨域契约。

- [ ] **Step 1: 先增加失败断言**

  断言 PAY-I12 在处理中返回 `PENDING/PAYMENT_IN_PROGRESS`；PAY-I04 使用 `commerce_refund_request_id + refund_amount`；`RefundUnknown` 只通知 Finance；PAY-I11 为实时覆盖计算且不发布 CoverageChanged；Payment 不订阅 Client/Compliance 限制预热事件。

- [ ] **Step 2: 运行目标测试确认失败**

  Run: `node --test requirements-site/tests/domain-decisions.test.mjs requirements-site/tests/global-use-case-sequences.test.mjs`

  Expected: 至少一项新增 Payment 契约断言失败。

- [ ] **Step 3: 修改需求与全局序列**

  统一以下链路：Commerce Cancel 使用确定性子幂等键调用 PAY-I12；Operating Entity 关闭由 Payment 在活动/UNKNOWN 收敛后自行终止；Capture 后 Reversal 创建新的 Commerce Refund Request，经 Finance Reservation 再调用 PAY-I04；退款 UNKNOWN 不改变 Commerce 终态；Provider outcome 精确关联 Dispute Action/Request/Package Version。

- [ ] **Step 4: 运行目标测试确认通过**

  Run: `node --test requirements-site/tests/domain-decisions.test.mjs requirements-site/tests/global-use-case-sequences.test.mjs`

  Expected: PASS。

### Task 3: 先建立 Payment 模型失败测试

**Files:**
- Create: `scripts/pdmaner/payment-model.test.mjs`
- Create: `scripts/pdmaner/payment-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: Task 1 的 34 表、字段、索引和场景契约。
- Produces: 与 Generator 清单相互独立的 `APPROVED_FIELD_MANIFEST`、`APPROVED_INDEX_MANIFEST`、关系 Oracle 和场景覆盖 Oracle。

- [ ] **Step 1: 写 34 表精确集合断言**

  测试必须使用 `assert.deepEqual(actualTableNames, approvedTableNames)`，同时断言旧表集合完全不存在。

- [ ] **Step 2: 写关键字段与枚举断言**

  至少覆盖 Attempt 十状态、Action 六状态、Scope 三层、Gate 三来源结果、Callback/Dispute/Refund 状态；断言 `t_routing_decision.selected_connection_version_id`、Action `mid_credential_ref_id`、三个 Acceptance ID 和统一 Transition 字段存在。

- [ ] **Step 3: 写物理规范与安全断言**

  断言表/索引前缀、中文 displayName/comment、枚举说明、无连接凭证、无跨库关系、无 PAN/CVV/卡有效期、无 `active_slot`，并对白名单外的 `*_hash`、`version` 字段失败。

- [ ] **Step 4: 写确定性与结构摘要断言**

  Generator `--check` 必须成功；模型、模板和去除纯布局后的结构分别校验 SHA-256；关系图必须覆盖 Provider、Routing、Payment、Refund、Callback、Dispute、Reliability 七个主题。

- [ ] **Step 5: 写现实场景覆盖断言并运行失败测试**

  Run: `node --test scripts/pdmaner/payment-model.test.mjs scripts/pdmaner/payment-scenario-doc.test.mjs`

  Expected: FAIL，原因是 Payment metadata、generator、物理决策或 PDManer 尚不存在。

### Task 4: 实现中文字段元数据

**Files:**
- Create: `scripts/pdmaner/payment-field-metadata.mjs`

**Interfaces:**
- Consumes: Generator 使用的精确 `tableName.columnName`。
- Produces: `TABLE_DISPLAY_NAMES`、`TABLE_COMMENTS`、`FIELD_METADATA`、`CLOSED_ENUM_VALUES`、`OPEN_CODE_SOURCES`。

- [ ] **Step 1: 为 34 张表写中文显示名和职责说明**

  说明直接描述事实，例如“支付尝试状态”，不得出现“Payment 对象的”“字段用于”“for t_xxx”等机械前缀。

- [ ] **Step 2: 为全部字段写中文显示名和直接说明**

  ID 字段要明确其业务对象；外部 ID 要注明权威系统；金额、版本、时间和 Secret Ref 要注明口径，不能只翻译字段名。

- [ ] **Step 3: 为所有闭合枚举列出完整取值**

  字段自身说明单独表达中文业务语义；闭合枚举按数据库设计规范统一追加 `允许值：A、B、C。`，开放代码注明 `取值来源：<权威标准或目录>；非固定枚举。`。

- [ ] **Step 4: 运行 metadata 覆盖测试**

  Run: `node --test scripts/pdmaner/payment-model.test.mjs`

  Expected: 仍因 Generator/模型缺失失败，但不得再报 metadata 缺失。

### Task 5: 实现确定性 PDManer Generator

**Files:**
- Create: `scripts/pdmaner/generate-payment-model.mjs`

**Interfaces:**
- Consumes: 共享模板、Task 4 metadata，以及 Generator 内独立的字段/索引/关系清单。
- Produces: `payment-orchestration-service/docs/database/payment-orchestration.pdma.json`；CLI 支持普通生成和 `--check`。

- [ ] **Step 1: 克隆 Commerce Generator 的确定性骨架**

  保留模板 Hash 校验、UUIDv5 风格稳定 ID、字段类型映射、命名规则清洗、连接清空、JSON 稳定序列化和 `--check`；更换 MODEL_KEY 与输出路径。

- [ ] **Step 2: 写 34 表字段和索引清单**

  Generator 清单不得从测试或 Markdown 读取。金额使用 `DECIMAL(24,8)`，权重使用 `DECIMAL(18,10)`，时间使用 `DATETIME(6)`，主键使用非自增 `BIGINT`。

- [ ] **Step 3: 写本库关系清单**

  只建立 Payment 内部关系；Root `current_version_id` 与子版本形成受控循环时，关系名称和基数必须清晰。跨域 ID 保持无关系字段。

- [ ] **Step 4: 生成七张主题关系图**

  图名固定为 `Provider 与 MID`、`Routing 与执行门禁`、`支付执行`、`Channel Refund`、`Provider Callback`、`Provider Dispute`、`可靠执行`；布局只影响展示，不得影响结构摘要。

- [ ] **Step 5: 运行 Generator**

  Run: `node scripts/pdmaner/generate-payment-model.mjs`

  Expected: 创建 34 表模型并输出表、字段、索引、关系和关系图数量摘要。

- [ ] **Step 6: 运行模型测试并修正最小差异**

  Run: `node --test scripts/pdmaner/payment-model.test.mjs`

  Expected: 除尚未冻结的模型摘要 Hash 外全部通过；取得真实摘要后只更新测试 Oracle 一次。

### Task 6: 写 Payment 物理决策并闭合场景测试

**Files:**
- Create: `payment-orchestration-service/docs/database/PAYMENT_ORCHESTRATION_PHYSICAL_DECISIONS.md`
- Modify: `agent-docs/database/scenarios/PAYMENT_REAL_WORLD_SCENARIOS.md`
- Test: `scripts/pdmaner/payment-scenario-doc.test.mjs`

**Interfaces:**
- Consumes: Task 5 生成的实际字段、索引、关系和图。
- Produces: 用户在 PDManer 中评审时可追溯的物理取舍、CHECK 约束和现实数据映射。

- [ ] **Step 1: 记录 34 表物理边界和删表映射**

  逐组说明为何保留 Connection Version、Instrument、3DS、Callback Item、Dispute Event 和 Compensation Event，以及为何删除 Slot、History、Binding、Recovery Task、Refund Attempt 与 Projection。

- [ ] **Step 2: 记录数据库无法直接表达的事务不变量**

  包括 Intent 指针更新、Candidate/Decision/Action 连接版本一致、每个 Gate 恰好三类结果、版本激活原子切换、UNKNOWN 继续占位、退款 Position 守恒和第二笔成功不发布第二次 PaymentSucceeded。

- [ ] **Step 3: 对齐现实场景中的 34 表字段**

  场景文档的表名、列名和枚举必须全部存在于模型；不强制每个场景都命中全部表，但整份文档必须覆盖全部 34 表。

- [ ] **Step 4: 运行场景测试**

  Run: `node --test scripts/pdmaner/payment-scenario-doc.test.mjs`

  Expected: PASS。

### Task 7: 全量验证和交付门禁

**Files:**
- Modify only if verification finds a scoped defect: Task 1–6 files.

**Interfaces:**
- Consumes: 全部 Payment 设计产物。
- Produces: 可交给用户在 PDManer 4.9.3 中人工 Review 的确定性第一版。

- [ ] **Step 1: 校验 Payment 生成物与确定性**

  Run: `node --test scripts/pdmaner/payment-model.test.mjs scripts/pdmaner/payment-scenario-doc.test.mjs && node scripts/pdmaner/generate-payment-model.mjs --check`

  Expected: PASS，`--check` 报告当前文件与确定性生成结果一致。

- [ ] **Step 2: 回归 CCT 与 Commerce 模型**

  Run: `node --test scripts/pdmaner/client-contract-model.test.mjs scripts/pdmaner/client-contract-scenario-doc.test.mjs scripts/pdmaner/commerce-model.test.mjs scripts/pdmaner/commerce-scenario-doc.test.mjs`

  Expected: PASS，现有模型没有被 Payment 改动破坏。

- [ ] **Step 3: 回归需求网站**

  Run: `npm --prefix requirements-site test`

  Expected: PASS；如有与本次无关的既存失败，单独列出证据且不修改无关代码。

- [ ] **Step 4: 验证模板、JSON 和敏感内容**

  Run: `shasum -a 256 agent-docs/templates/pdmaner/template.pdma.json && node -e "JSON.parse(require('node:fs').readFileSync('payment-orchestration-service/docs/database/payment-orchestration.pdma.json','utf8'))" && rg -n -i "jdbc:|password|secret_key|private_key|\bpan\b|\bcvv\b|card_exp" payment-orchestration-service/docs/database/payment-orchestration.pdma.json`

  Expected: 模板摘要为 `51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`；JSON 可解析；扫描只命中明确的禁存说明或精确 Secret 引用字段名，不含凭证值和卡数据字段。

- [ ] **Step 5: 审阅限定 Diff**

  Run: `git diff -- agent-docs requirements-site/content requirements-site/tests scripts/pdmaner payment-orchestration-service/docs/database`

  Expected: 只包含本计划授权的 Payment 与必要跨域一致性改动，不覆盖用户的其他未提交修改。

- [ ] **Step 6: 标记人工门禁**

  在交付总结中明确：自动验证已完成；PDManer 4.9.3 的打开、关系图和应用内规范检查仍等待用户 Review，不能宣称该人工门禁已通过。
