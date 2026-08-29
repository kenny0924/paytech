# Finance Transaction Economics 与 Accounting Rule 精简实施计划

最后更新：2026-08-26  
状态：已完成；仅修改数据库设计产物，未创建 Migration、Java、数据库或运行环境

## 目标

把容易被误解为可刷新投影的 Economics Snapshot 改为明确的不可变交易经济结果，并把自动记账规则从通用条件 DSL 精简为“金额来源到借贷行”的受控映射。CCT Commercial Term 继续作为 Client Pricing 与 Partner Share 的权威配置，本次不改变其四表结构。

## 已确认契约

- `t_economics_snapshot/component/component_link` 分别改名为 `t_transaction_economics`、`t_transaction_economic_component`、`t_transaction_economic_comp_link`；最后一个名称使用 `comp` 缩写以满足物理名不超过 32 字符。
- Header 删除无独立消费者的商业上下文 Hash、计算 Hash、Agreement Root ID 和三类规则 Version/Scope 引用；保留来源事件完整性 Hash、交易锚、主体维度、Agreement-OE Association、币种与金额。
- Component 增加 `source_rule_scope_id`，规则 Version/Scope/Component 与实际计算参数在真正使用它的组件上冻结。
- `t_accounting_rule_bundle` 与精简后的 `t_accounting_rule_bundle_ver` 保留；`t_accounting_rule_mapping` 改为 `t_accounting_rule_line`。
- 删除 `t_accounting_rule_condition` 和 `t_accounting_rule_approval`；规则版本直接保存 Maker、Checker 与批准时间，不保存任意 SQL、脚本、JSON 条件或通用 Field/Operator DSL。
- Rule Line 使用 `instruction_type + line_no + line_code + amount_source_code + owner_role + account_code + balance_bucket + entry_side + required_flag`。同一个金额来源允许生成多条借贷行。
- 所有下游外键字段同步使用 `transaction_economics_id`、`origin_economics_id` 和 `accounting_rule_line_id`。
- `uk_econ_source(source_system,source_event_key)` 保证一个来源事件只能形成一条 Transaction Economics；Type、Version 或 Payload 不一致必须冲突。
- Component 来源证据固定为四类矩阵：CCT Pricing/Share 使用 Scope 且不自造 Hash，Tax 使用 Tax Line/权威 Hash，Provider Cost 使用 Scope/权威 Hash。
- Target Component 与完整 Component Link 集同事务创建，Target 分配金额守恒；部分退款按稳定顺序锁定 Source 并累计封顶。Provider Cost 差额使用 `PROVIDER_COST_INCREASE/DECREASE` 两个稳定代码决定相反借贷方向。
- Rule Version/Line 境界增加正版本号、半开有效期和正行号 CHECK；追加、提交、审批和 Current Pointer 切换统一使用 Bundle→Version 锁顺序。零金额来源存在但不生成零金额 Instruction Line。

## 执行顺序

1. 更新独立 Oracle 与模型测试，先固定新表名、字段、关系、删除项和命名规则。
2. 修改 Finance Manifest、CHECK Policy 和 Diagram 关系源。
3. 通过确定性生成器重建 `finance.pdma.json`，以实际生成结果确定表、字段、索引和关系总数。
4. 同步 Finance 逻辑文档、物理决策、现实数据场景、需求目录、全局序列和导航中的当前模型表述。
5. 运行 Finance 模型/场景测试、生成器 `--check`、PDManer 全量测试和 `npm --prefix requirements-site test`；扫描旧名称与被删除字段的当前语义残留。

## 边界

- 历史计划只补充“已被本决策替代”的说明，不把旧结构恢复为当前模型。
- 不手工编辑生成的 PDManer JSON；只通过生成器产出。
- 不修改共享 PDManer 模板，不创建分支或提交。
- PDManer 4.9.3 应用内关系图与规范检查仍是用户人工门禁。

## 完成结果与验证

- 最终模型为 121 表、1,469 字段、209 个唯一索引、187 个访问索引、210 条本库关系、41 条 1:1 关系和 8 个 Diagram Groups。
- Finance 模型与现实场景测试 17/17 通过，确定性生成器 `--check` 通过。
- Requirements Site 构建及测试 83/83 通过，`git diff --check` 通过，当前源中的旧名称与已删除字段残留扫描为零。
- PDManer 全量测试 135/138 通过；仅 Commerce 已存在的模型/生成器字节摘要不一致导致 3 项失败，Finance 17 项全部通过，本次未修改或重生成 Commerce 资产。
- 共享模板 SHA-256：`51d37256d7396bb3fab0ec20275098e30e148c85dfb506bc850990fd74859540`。
- Finance 模型 SHA-256：`3520d0efcc286b592a9559c0e2a841de324e19d0a9bb22acb74e31fbf3a3a885`。
- 测试独立结构 SHA-256：`e8736c2f60f1cd3c4fd9f8d4be4b8956a6cc121f0c7bf1a49d8de0d07c6c9596`。
