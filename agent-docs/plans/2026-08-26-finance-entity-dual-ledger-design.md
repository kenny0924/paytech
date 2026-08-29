# Finance Entity 与 Partner OE 双账簿设计

最后更新：2026-08-27
状态：业务方向与核心模型已确认并完成 PDManer、权威文档和 Requirements 同步；实施结果见 [Finance Entity 双账簿实施记录](./2026-08-26-finance-entity-dual-ledger-implementation-plan.md)，退款 Funding 后续精简见 [Finance 退款 Funding 移除实施记录](./2026-08-27-finance-refund-funding-removal-implementation-plan.md)；Migration、Java、物理数据库和运行环境仍未授权

## 1. 目标与边界

当前 Finance 把 `Operating Entity` 同时作为交易 Seller/MoR、MID 持有人、账簿所有者和利润归属主体。这一假设只适用于 Operating Entity 的法律 Party 同时也是平台利润主体的场景。当 Partner 来源的 Operating Entity 由 Partner 法律主体拥有，而平台签约公司是另一法律 Party 时，现有模型无法为不持 MID 的平台公司建立独立账簿，也会把平台收入、Partner OE 收入和双方利润错误混入同一本账。

本设计引入 Finance 本地人工维护的 `t_finance_entity`，让账簿可以归属 `OPERATING_ENTITY` 或 `FINANCE_ENTITY`。Partner OE 继续作为交易 Seller/MoR 和 MID 持有人；平台 Finance Entity 作为真实的平台法律服务主体，通过明确的 Platform Service Charge 获得收入。两个主体分别生成平衡 Journal、分别试算和关账。

本设计不把 Finance Entity 当作部门、利润中心、虚构 OE 或通用核算维度，也不建设跨主体资金拆借、集团合并报表或通用 Intercompany Transfer。

## 2. 已确认的业务结论

- Partner OE 是使用 Partner MID 的交易 Seller/MoR、交易 Operating Entity 和该交易主账簿主体。
- Finance Entity 是我司人工维护的真实平台法律公司，可以没有 Operating Entity 和 MID，但可以拥有自己的账簿、收入、费用、资产、负债和权益。
- 每个 Finance Entity 必须绑定 CCT 中真实的 `PLATFORM_SIGNATORY` Party；Finance 不创建第二套法律 Party 主档。
- 平台收入来自 Partner OE 按合同应付的明确 Platform Service Charge，不从 `Client Fee - Partner Share - Provider Cost` 机械倒算。
- Partner OE 与 Finance Entity 各自在自己的 Ledger Book 生成借贷平衡的 Journal Entry；不存在跨 Ledger Book 的单条 Journal。
- 两侧 Journal 可以在同一 Finance 本地数据库事务中原子提交，但它们仍是两个独立会计主体的两条会计事实。
- Partner OE 的利润只在 Partner OE Book 结转；平台利润只在 Finance Entity Book 结转。

## 3. `t_finance_entity`

`t_finance_entity` 是 Finance 拥有的内部建账配置，不是 CCT Party 或 Operating Entity 的复制主档。

字段固定为：

```text
id
entity_code
legal_party_id
display_name
status
create_time
update_time
```

约束：

- `uk_fin_entity_code(entity_code)`；
- `uk_fin_entity_party(legal_party_id)`；
- `status IN (ACTIVE,CLOSED)`；
- `legal_party_id` 非空，指向 CCT 中获准作为平台签约和服务主体的真实 Party，但不建立跨库外键；
- `display_name` 只用于 Finance 内部展示，不作为发票、税务申报或法律文件中的权威法定名称；
- 数据通过受控初始化或 Finance 运维操作人工维护，不开放给 Client、Local Partner 或普通交易接口；
- `entity_code`、`legal_party_id` 创建后不可修改；`CLOSED` 后不得重新激活；
- 关闭只阻止新建账簿和承接新的 Platform Service Charge，不删除历史 Book、Account、Journal、Tax 或 Close 事实；
- V1 不增加业务 Version、Content Hash、History、Approval 或 Active Slot。

## 4. Ledger Book 所有者

`t_ledger_book` 删除当前强制的 `operating_entity_id`，改为一组非空多态所有者：

```text
entity_type
entity_id
currency
chart_id
book_status
version
```

枚举固定为：

```text
entity_type = OPERATING_ENTITY | FINANCE_ENTITY
```

唯一约束：

```text
uk_ledger_book_entity(entity_type, entity_id, currency)
```

强制语义：

- `OPERATING_ENTITY` 时，`entity_id` 是 CCT `operating_entity_id`；建账时必须同步校验 OE 存在、法律 Party 和当前建账资格；
- `FINANCE_ENTITY` 时，`entity_id` 是本库 `t_finance_entity.id`；建账事务必须校验该行存在且为 `ACTIVE`；
- `entity_type/entity_id` 创建后不可修改；主体变化必须创建新 Book，历史 Journal 不迁移；
- 多态 `entity_id` 无法建立同时指向跨库 OE 和本库 Finance Entity 的物理外键，因此由受控解析器、应用事务、模型测试和未来 Testcontainers 测试保证；
- 不增加 `operating_entity_id + finance_entity_id` 两个可空字段，不增加 `t_accounting_entity`。

Partner OE Book 示例：

```text
entity_type = OPERATING_ENTITY
entity_id   = partner_operating_entity_id
currency    = USD
```

平台 Book 示例：

```text
entity_type = FINANCE_ENTITY
entity_id   = platform_finance_entity_id
currency    = USD
```

## 5. 统一账户与科目

`t_account.owner_type` 增加 `FINANCE_ENTITY`，并继续保留 `OPERATING_ENTITY`、`CLIENT`、`LOCAL_PARTNER`、`PROVIDER_ACCOUNT`、`CASH_ACCOUNT` 和 `TAX_AUTHORITY`。`owner_type + owner_id` 仍表示账户的核算对象，不替代 Ledger Book 所有者。

Book 自身的收入、费用、权益和内部调节账户必须使用与 Book 所有者一致的 Owner：

- OE Book 使用 `owner_type=OPERATING_ENTITY, owner_id=ledger_book.entity_id`；
- Finance Entity Book 使用 `owner_type=FINANCE_ENTITY, owner_id=ledger_book.entity_id`。

平台服务费至少增加下列叶子科目：

| 科目代码 | 类别 / 正常方向 | 用途 |
|---|---|---|
| `PLATFORM_SERVICE_RECEIVABLE` | ASSET / DEBIT | Finance Entity 对 Partner OE 的服务费应收 |
| `PLATFORM_SERVICE_REVENUE` | REVENUE / CREDIT | Finance Entity 的平台服务收入 |
| `PLATFORM_SERVICE_FEE_EXPENSE` | EXPENSE / DEBIT | Partner OE 承担的平台服务费成本 |
| `PLATFORM_SERVICE_PAYABLE` | LIABILITY / CREDIT | Partner OE 对 Finance Entity 的服务费应付 |

Partner OE Book 的 `PLATFORM_SERVICE_PAYABLE` 账户以对手 Finance Entity 为 Owner；Finance Entity Book 的 `PLATFORM_SERVICE_RECEIVABLE` 账户以对手 Partner OE 为 Owner。四类账户的 `balance_bucket` 均为 `NONE`。

## 6. Transaction Economics 与服务费来源

`t_transaction_economics` 继续冻结交易 `operating_entity_id`，证明原交易 Seller/MoR 和 MID 范围。只有 `entity_source_type=PARTNER` 的交易同时冻结实际承接本次服务收入的 `finance_entity_id`；`PLATFORM`、`CLIENT` 来源交易在本设计中保持该字段为空。Finance 必须用该 Finance Entity 的 `legal_party_id` 与交易 Partner Agreement 中获准的唯一 `PLATFORM_SIGNATORY` Party 逐值一致后才能创建经济结果。

CCT 在现有 `CLIENT_PRICING` 和 `PARTNER_SHARE` 之外增加第三类 Commercial Term `PLATFORM_SERVICE_CHARGE`。它归属 Local Partner、引用同一 Partner Agreement，并复用 `commercial_term_component` 的结构化百分比和固定金额组件。百分比的 `calculation_base` 必须来自受控目录并由 CCT 返回、Commerce 冻结准确 Scope 后由 Finance 逐值校验；现实场景可使用 `GROSS_PAYMENT_AMOUNT`，但不得把单一基数硬编码成所有合同的法定规则。Partner Agreement 只有恰好一个 `PLATFORM_SIGNATORY` 时才允许该条款激活和被 CCT-I13 返回。

Commerce 在不可变 Order Context 中冻结实际命中的 `platform_service_charge_scope_id` 和 `platform_service_party_id`，但不认识 Finance 内部 `finance_entity_id`。Finance 通过 COM-I04 读取这两个外部稳定 ID，使用 Scope 调用 CCT-I14，并按 Party 唯一映射本地 ACTIVE Finance Entity；不得从当前默认配置猜测主体。

交易经济组件增加明确的 `PLATFORM_SERVICE_CHARGE` 金额来源。该金额必须来自已签合同或结构化商业条款的明确百分比、固定金额或二者组合，并冻结实际采用的规则 Version/Scope/Component 与计算参数。禁止使用以下方式产生平台服务费：

- 用 Client Fee、Partner Share 和 Provider Cost 的剩余金额倒算；
- 从某个默认 Finance Entity 或当前活动配置猜测收款主体；
- 在 Accounting Rule 中重新计算费率；
- 用人工 Journal 绕过 Transaction Economics。

V1 先复用 `t_transaction_economic_component` 保存逐笔服务费金额和来源证据，不新增 `t_platform_service_charge`。只有未来确认需要逐笔 Open Item、汇总开票、收款分配、核销、Credit Note 或独立状态机时，才另行设计 Charge/Invoice/Receipt 表；不得提前建立无消费者的发票层级。

Refund 或服务费更正通过新的 Transaction Economics、Component Link 和相反效果的组件处理，不修改原 Economics 或原 Journal。

## 7. 双账簿记账

一个 `PLATFORM_SERVICE_CHARGE` 生成两个 Accounting Instruction，每个 Instruction 只属于一本 Ledger Book。

Partner OE Book：

```text
Dr PLATFORM_SERVICE_FEE_EXPENSE
Cr PLATFORM_SERVICE_PAYABLE
```

Finance Entity Book：

```text
Dr PLATFORM_SERVICE_RECEIVABLE
Cr PLATFORM_SERVICE_REVENUE
```

Accounting Rule 只把已经计算好的 `PLATFORM_SERVICE_CHARGE` 映射到 Book、Owner、Account、Bucket 和借贷方向，不计算服务费。两个 Instruction 使用不同的稳定 `instruction_type`，并共同引用同一 Transaction Economics；每个 Instruction 单独满足借贷相等并生成自己的 Journal Entry。

两侧 Posting 在同一本地事务中按 `ledger_book.id` 升序锁定 Book、Period 和 Book Sequence，再按 `account.id` 升序锁定账户。任一侧 Book/Period 不允许过账、账户不可用、规则缺失、金额不一致或 Journal 写入失败时，两侧 Instruction、Sequence、Journal、余额投影和 Compensation Event 整体回滚。已提交的两条 Journal 各自拥有本 Book 的严格递增 Sequence。

## 8. 试算、关账与利润

- Trial Balance、`t_ledger_period_balance` 和 Final Trial 始终按单个 Ledger Book 计算；不建立跨主体试算平衡。
- `CURRENT_PERIOD_RESULT` 只接收本 Book 的 Revenue/Expense 结转；`RETAINED_EARNINGS` 只接收本 Book 的历史利润。
- Partner OE 的 Client Fee、Provider Cost、Partner Share、Platform Service Fee 和其他交易经济是否属于其收入或费用，必须按该 OE 的合同和会计政策逐项决定。
- Finance Entity Book 只确认属于该平台法律公司的 Platform Service Revenue 和自身承担的成本，不接收 Partner OE 的利润结转。
- 两本账金额镜像不等于两个 Book 的 Trial Balance 必须互相抵销；每本账只要求自身借贷平衡。集团合并抵销不属于 V1。

## 9. 主体归属的后续逐表收紧

Finance Entity 是真实法律建账主体，因此当前只接受 `operating_entity_id` 的下列法定财务对象必须在实施计划中逐表选择明确归属方式：

- Fiscal Calendar；
- Tax Registration、Tax Period、Tax Return 与 Tax Payment；
- Cash Account、Cash Account Binding、Bank Statement 与 Bank Cash；
- Funding 中服务 Finance Entity Tax Payment 或 Bank Cash 的主体范围；
- Trial Balance、Period Close 和相关对账引用。

优先原则是：已经由 `ledger_book_id` 或稳定父链唯一确定主体的表不重复保存 `entity_type/entity_id`；需要跨币种共享且不能由单一 Book 推导的法律主体 Root 才保存同样的多态主体对。不得机械把全部 `operating_entity_id` 全局替换成 `entity_id`。

下列交易执行对象继续只属于 Operating Entity，不泛化为 Finance Entity：

- Payment Provider Account、MID 与 Provider Settlement；
- 原交易 Provider Receivable/Debit Item；
- Client/Local Partner Account、Settlement Profile、Settlement 和付款；
- 交易 Assignment、Risk Restriction、Payment Refund Position、Channel Refund 与 Provider 退款执行的原 OE Scope。

## 10. 不变量与失败语义

- `entity_source_type=PARTNER` 的交易必须具有一个 Partner OE、一个 Partner Agreement、一个 Platform Service Charge Scope、一个平台服务法律 Party 和一个明确 Finance Entity；`PLATFORM`、`CLIENT` 来源交易的 Platform Service Charge Scope、Party 与 `finance_entity_id` 必须成组为空。
- Partner OE 与 Finance Entity 不得使用同一类型或同一 ID 互相冒充；Commerce 只冻结法律 Party 和 Scope，Finance Entity ID 只在 Finance 内部产生。
- Finance Entity 必须为 `ACTIVE`，且其 `legal_party_id` 必须等于合同证明中的平台服务法律 Party；缺失、并列或不一致时 Fail Closed。
- 两侧服务费绝对金额和币种必须完全相等；一侧不得成功而另一侧失败。
- 两侧应收应付不得通过同一 `t_account`、同一 Ledger Book 或同一 Journal Entry 表达。
- Partner OE 关闭或 Finance Entity 关闭后，禁止承接新交易服务费；历史 Refund、更正、对账和受控关账仍按原冻结主体处理。
- Source Event Key、Transaction Economics、双 Instruction 和双 Journal 的幂等关系必须阻止重复入账；同 Key 不同 Payload/金额/主体必须冲突且不产生 Journal。
- Projection Rebuild 只能从各 Book Journal 重建账户余额，不得追加虚假服务费 Journal 修复漂移。

## 11. 明确不做

- 不增加 `t_accounting_entity`；
- 不为平台公司创建虚构 Operating Entity 或 MID；
- 不恢复通用 `t_intercompany_transfer`；Partner 与平台不一定属于同一集团；
- 不设计集团 Consolidation、Elimination、Transfer Pricing 或跨主体 Cash Pool；
- 不在本轮增加 Platform Service Invoice、Open Item、Receipt Allocation 或自动收款执行表；
- 不改变 Journal 是唯一不可变会计事实的原则；
- 不创建 Java、Migration、物理数据库、部署环境或 Git Commit。

## 12. 后续实施门禁

本文书面复核通过后，下一步仅编写逐表实施计划，明确：

1. Finance Manifest、Oracle、CHECK、关系图和生成器的准确变更；
2. `FINANCE_DATABASE.md`、物理决策、现实场景、Requirements 和全局序列的同步范围；
3. CCT Partner Agreement/Commercial Term 如何明确返回平台服务法律 Party和服务费计算输入；
4. 所有受影响 `operating_entity_id` 的保留、泛化或由父链推导决定；
5. 表、字段、索引、关系和 Diagram Group 的生成后实算基线；
6. Finance 专项测试、生成器检查、Requirements Site 和全量 PDManer 验证命令。

实施计划获确认前，不修改 `finance.pdma.json` 或当前权威数据库文档。
