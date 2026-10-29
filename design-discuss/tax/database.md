# Tax 逻辑数据模型

状态：**讨论稿，待评审**，2026-10-26。范围与流程见 [design.md](design.md)。这是字段级逻辑模型，不是已批准或可直接执行的物理 DDL。

**2026-10-29 修订：** 随 [design.md](design.md) 同日修订——`t_tax_market` 增加 `managed_by` 并明确纳税主体由持有方操作；`t_tax_calculation.status` 增加 `NOT_MANAGED`；`t_tax_transaction` 由消费 acquiring 事件建立，不再由写命令建立。表数仍为 19。

**2026-10-29 第二轮：** 首批纳税主体只有 PayFac 的 AE / SA / EG 主体，`managed_by` 首期恒为 SUBJECT_HOLDER；`t_tax_calculation_line` 增加 `refund_basis_amount`；`t_tax_reporting_entry` 增加 `deductibility_status`；`t_tax_document_sequence` 唯一键并入 `registration_id`；明确哪些表有 `updated_at`；`t_tax_credit_application` 不向 billing 发事件；第 9 节补 billing / ledger 维度。

**2026-10-29 第三轮（人工处理降级，见 [design.md 第 10 节](design.md#10-人工处理的边界)）：** `t_tax_transaction` 增加 `origin` 与 `transaction_kind=MANUAL_INPUT`；`t_tax_calculation.tax_amount_source` 增加 `MANUAL`；`t_tax_evidence.source_code` 允许 `MANUAL`；`t_tax_document` 增加 `issuance_mode`、`submission_source`。表数仍为 19。

可视化：[UML总览与完整字段图](uml.md)（2026-10-29输出，包含可编辑PlantUML源文件）。本文仍是该讨论稿的字段来源。

## 1. 共同约定

- 本库独立，不跨库读写。跨服务字段是业务引用；模型关系不生成物理 `FOREIGN KEY` / `CHECK`。保留 PK、业务唯一索引、必填约束，应用层负责事务与关系校验。
- ID 沿用项目统一生成方式；内部 ID 建议 `BIGINT`，对外序列化为字符串。OE 使用 `entity_no CHAR(5)`；Party ID 与账簿主体引用分别建字段。
- 金额 `DECIMAL(24,8)`，数量 `DECIMAL(24,8)`，税率 `DECIMAL(18,10)`，汇率 `DECIMAL(24,12)`；币种 `CHAR(3)`。接口十进制字符串，禁止 float。实际收付款按币种精度；内部高精度不等于允许超精度结算。
- 国家、行政区与币种引用 [Platform 参考数据](../../agent-docs/decisions/2026-10-27-platform-reference-data.md) 的开放编码；Tax 不另建这些全局目录。Tax 产品税类属于业务目录，继续由本服务管理。
- 时间点 `DATETIME(6)` UTC；法定税点日期、期间日期用 `DATE`，明确注册地时区。不能以 UTC 日期替代当地法定日期。
- 下文所有表均有 `id` 主键、`created_at`；可变工作状态表增加 `updated_at`，具体为 market、registration、policy、calculation（status / expires_at）、transaction（reporting_status）、document、document_sequence、period、return、payment、payment_allocation（authority_status）、credit_application；calculation_line、calculation_component、evidence、reporting_entry（deductibility_status 例外，见该表）、return_item、payment_event 为追加写，不建。进入物理建模时统一为项目的 `create_time / update_time`。操作者 / 审批者 / 原因按业务列保存必要事实并接现有审计，不机械建立 history 表。
- `*_ref` 是明确 Owner 的不可变业务引用；`*_file_address` 使用 `/files/v1/{fileId}`。证据文件必须禁止原地址覆盖，或能按冻结版本取回同一字节；另存 SHA-256 校验证据，不把普通可替换文件地址当不可变归档。详见[文件约定](../../agent-docs/decisions/2026-10-22-file-upload-address-principles.md)。
- `subject_party_id` / `tax_subject_party_id` 指**平台登记的纳税主体**：首批只有 PayFac Partner 持有的 AE / SA / EG 主体；平台是纯技术提供方，不登记自身。平台不是这些主体的纳税人；准入、申报、缴款由主体持有方在其控制台操作（`t_tax_market.managed_by`，首期恒为 SUBJECT_HOLDER），法定申报缴纳由持有方财务在税局系统完成，平台运营只读或协助。
- 不给所有表通配 `ext_json`。本文 JSON 只用于有 Schema 的有限规则配置、输入诊断、单据法定快照，必须限制大小并拒绝未知字段；影响查询、去重、金额与状态的事实独立成列。

## 2. 表清单与存在理由

| # | 表 | 独立存在的业务原因 |
|---|---|---|
| 1 | `t_tax_category` | 税务产品分类；用于商品行映射和规则判断，不复用 MCC 或平台服务产品目录 |
| 2 | `t_tax_market` | 一主体进入一地区的责任评估与准入，区分未评估、应注册、获准按条件不收税；记录由谁操作该主体的税务 |
| 3 | `t_tax_registration` | 真实法定注册和申报制度，有自己的有效期与注销过程 |
| 4 | `t_tax_policy` | 一起生效和审查的地区包；解释历史计算所依据的规则 |
| 5 | `t_tax_calculation` | 一次不可变计算的主体、事实版本、币种和结果 |
| 6 | `t_tax_calculation_line` | 商品行独立定价、折扣、税类与部分退款 |
| 7 | `t_tax_calculation_component` | 一行可能受多个税务辖区 / 税组件约束，金额与处理原因分别保存 |
| 8 | `t_tax_evidence` | 地点与纳税人核验结果具有不同来源、时间、有效期和置信边界 |
| 9 | `t_tax_transaction` | 将计算确认成正式业务事实；去重、调整和归档的锚点；人工登记事实走同一张表并以 `origin` 区分 |
| 10 | `t_tax_reporting_entry` | 从交易币种转为申报口径，区分销项、进项、自计税及披露 |
| 11 | `t_tax_document` | 法定发票 / 贷项 / 借项单据的号码、内容、发行与提交状态 |
| 12 | `t_tax_document_sequence` | 原子分号，防重复，保留失败或作废号码 |
| 13 | `t_tax_period` | 注册下稳定的法定期间，不随修订或缴款改变身份 |
| 14 | `t_tax_return` | 一次冻结申报版本与回执；修订保留旧版 |
| 15 | `t_tax_return_item` | 申报栏目到逐笔底稿的追溯，避免只有不可解释总数 |
| 16 | `t_tax_payment` | 一次实际缴款或税局退税，与银行执行和匹配关联 |
| 17 | `t_tax_payment_allocation` | 一次付款跨多期、一期多次付款；依据申报版本与归属期间分离 |
| 18 | `t_tax_payment_event` | 外部提交、结果未知、银行匹配、迟到借记与税局认领的不可变证据 |
| 19 | `t_tax_credit_application` | 无银行现金流的跨期抵税转用，不能伪装成付款 |

表 19 在抵税场景实际接入时建立；并非计算切片的前置依赖。税局退税与转用未接入时允许记录可申请额度与人工状态，但不得自动冲销应纳或伪造已到账。

## 3. 主数据与地区配置（1–4）

### 1 — t_tax_category

| 字段 | 类型 / 含义 |
|---|---|
| category_code | VARCHAR(64)，稳定语义，例如 `SAAS_AUTOMATED` |
| name, description | VARCHAR，业务定义与边界，不放税率 |
| status | ACTIVE / RETIRED；停用后历史引用仍可读取 |

唯一：`category_code`。商品到税类的确定由商品 Owner 提供经过 Tax 认可的分类，初期无独立商品税映射表；不能通过把 SaaS 改名“咨询”改变税率。后续真实需要外部供应商分类转换时，再在适配配置中维护明确映射。

### 2 — t_tax_market

| 字段 | 类型 / 含义 |
|---|---|
| subject_party_id, jurisdiction_code, tax_type, scheme_code | 法律主体、地区、税种、适用制度 |
| assessment_no, revision | 同一评估的业务编号与修订；每个批准版本不改内容 |
| valid_from, valid_to | 批准判断的适用日期区间 |
| obligation | REGISTRATION_REQUIRED / NO_COLLECTION_REQUIRED / CUSTOMER_ACCOUNTED / REVIEW_REQUIRED |
| collection_status | BLOCKED / ENABLED / SUSPENDED |
| managed_by | SUBJECT_HOLDER / PLATFORM_OPERATIONS；谁操作该主体在该地区的准入、申报与缴款。首期恒为 SUBJECT_HOLDER（PayFac Partner 在 Partner 控制台操作）；PLATFORM_OPERATIONS 保留给将来平台自身或受托主体，首期不启用。决定控制台归属与审批人来源 |
| registration_id | 可空；需要收税时必须指向有效注册，不能伪造“无须注册”的注册记录 |
| policy_id, assessment_file_address, evidence_digest | 依据包、责任 / 阈值评估证据及其摘要 |
| assessed_at, approved_by, approved_at | 谁批准该法律判断及时间；审批人来自 `managed_by` 指定的一方（持有方 Maker-Checker 或平台运营），平台运营不替持有方批准 |
| threshold_currency, threshold_amount, observed_turnover, turnover_as_of | 仅阈值适用时填写的评估事实；不是实时营业额余额 |
| review_due_at | 到期复核；阈值业务另用正式交易滚动汇总，超出批准条件必须复核 |

唯一：`assessment_no + revision`；同主体 / 地区 / 税种 / 制度的生效批准区间不可重叠，由主体资源锁保护。未注册、尚未达到阈值、纯 B2B 买方自计税是不同事实。准入启用不代替真实税务注册。

计算时 `TAX_RESPONSIBLE` 在本表没有任何记录（任何地区、任何状态），表示该主体不是平台登记的纳税主体，计算结果为 `NOT_MANAGED`；有记录但 `collection_status=BLOCKED` 才是 `BLOCKED`。两者不能混用：前者不阻断支付，后者阻断。

### 3 — t_tax_registration

| 字段 | 类型 / 含义 |
|---|---|
| subject_party_id, jurisdiction_code, authority_code, tax_type, scheme_code | 一份法定注册的归属；authority_code 不是银行收款账户 |
| registration_no_ref, registration_file_address | 受控注册号 / 法定注册资料引用；不复制个人税号原值 |
| effective_from, effective_to, status | PENDING / ACTIVE / SUSPENDED / DEREGISTERED，保留实际生效区间 |
| reporting_currency, legal_timezone | 法定申报币种与日期解释 |
| filing_frequency, filing_calendar_code | 法定周期模式与适用日历代码；具体截止日落期间 |
| payment_instruction_ref | 已核验的税局收款指引，执行时再次冻结证据 |

唯一：来源注册业务身份（`subject_party_id + authority_code + registration_no_ref + tax_type + scheme_code`）。同主体可以有多国、多制度注册。变更税务号码或法律主体须建立新注册；小范围行政更新不改变已冻结单据和申报。

### 4 — t_tax_policy

| 字段 | 类型 / 含义 |
|---|---|
| package_code, revision, jurisdiction_code, tax_type, scheme_code | 包身份、版本和规则适用范围 |
| effective_from, effective_to, schema_version | 法定适用日期；结构校验版本 |
| status | DRAFT / APPROVED / RETIRED；APPROVED 内容不可改，RETIRED 不影响历史解释 |
| policy_json | 固定 Schema 的条件、税率、税点、证据、单据与申报映射，见 [regional-policies.md](regional-policies.md) |
| source_refs_json, approved_by, approved_at | 官方依据列表与复核者；不是任意附件袋 |

唯一：`package_code + revision`。同一匹配范围的批准版本不能重叠；显式例外条件必须互斥，不靠“最后一条赢”。追溯性法律更改建立新版本及经审批的适用处理，既有正式交易只通过调整修正。

## 4. 不可变计算与证据（5–8）

### 5 — t_tax_calculation

| 字段 | 类型 / 含义 |
|---|---|
| source_system, source_object_type, source_object_id, source_revision, tax_scope_key | 源对象及准备版本；同准备版本可有多次报价，不做 HTTP 请求重放 |
| supply_kind, business_mode | SALE / PLATFORM_SERVICE / PURCHASE；商业模式是可信合同快照，不直接决定税负 |
| seller_party_id, buyer_party_id | 卖方法律主体；买方为平台签约 Party 时填写后者（PURCHASE 时卖方是 Client、买方是 Partner OE；PLATFORM_SERVICE 时卖方是 PayFac、买方是 Client） |
| buyer_client_id, customer_no, buyer_snapshot_ref | Client 命名空间与交易时受保护快照；访客允许 customer_no 为空 |
| tax_subject_party_id, acquiring_entity_no, book_subject_ref, agreement_ref | 纳税主体（商业关系快照的 `TAX_RESPONSIBLE`）、OE、账簿、合同分别冻结；`NOT_MANAGED` 时仍冻结该责任方 Party 以便追溯 |
| seller_establishment_country, establishment_context_ref | 卖方机构参与事实，不能只看注册地址 |
| currency, tax_point_date, tax_point_basis, calculated_at, expires_at | 金额币种、候选税点及依据、计算有效期 |
| status, block_reasons_json | READY / BLOCKED / NOT_MANAGED；BLOCKED 保存固定错误码及所缺字段路径，无个人原值；NOT_MANAGED 表示 `TAX_RESPONSIBLE` 不是平台登记的纳税主体 |
| tax_amount_source | COMPUTED / DECLARED / MANUAL；READY 自动计算为 COMPUTED，NOT_MANAGED 恒为 DECLARED（源 Owner 传入的自报税额，可为零），人工登记事实对应的 calculation 为 MANUAL（金额由登记人填写、复核人确认，仍逐行逐组件保存） |
| net_amount, charged_tax_amount, gross_amount, self_assessed_tax_amount | 各行汇总；`gross = net + charged_tax`，自行计税不加到买家应付 |
| original_transaction_id | 调整计算引用原正式交易；普通计算为空 |

金额在 BLOCKED 时可空，不能用全零表示成功。NOT_MANAGED 的头与行金额取源 Owner 传入的自报值，不建 component 与 evidence，不生成 transaction、底稿或单据；READY 与 NOT_MANAGED 用 status 区分，不靠金额推断。索引：源对象及准备版本、subject + created_at。计算行冻结税点所依据的供应 / 付款 / 发票日期候选，不隐含取当前时间。

### 6 — t_tax_calculation_line

| 字段 | 类型 / 含义 |
|---|---|
| calculation_id, source_line_ref, line_no | 计算所属与源商品行；同计算内 line_no 唯一 |
| category_code, description, quantity, unit_price | 产品税类、非敏感商品说明、数量、单价；quantity 始终是真实数量，不用它表达退款比例 |
| refund_basis_amount | 仅退款 / 调整行：本次退回的原税前基数（正数）；按数量退款时等于 quantity × unit_price，按比例或总金额分摊时直接记分摊结果 |
| service_period_from, service_period_to, supply_date | 订阅 / 服务期间和实际供应日期；按场景必填 |
| price_mode, discount_amount | INCLUSIVE / EXCLUSIVE；折扣与该价格模式使用同一口径 |
| net_amount, charged_tax_amount, gross_amount, self_assessed_tax_amount | 行最终金额；折扣已分配后的基数 |
| rounding_delta | 本行明确分配的尾差，解释总额如何成立 |
| original_line_id | 调整时引用原计算行；调整金额使用带符号数 |

源订单级折扣先按确定规则分配到行，记录分配后金额；只保存百分比而不保存已分配金额不足以审计。不得把不同税类的多行合成一条“订单税”。

### 7 — t_tax_calculation_component

| 字段 | 类型 / 含义 |
|---|---|
| line_id, component_code, jurisdiction_code, reporting_jurisdiction_code | 征税地区与申报地区可不同；为未来 OSS 等制度留位置 |
| tax_type, treatment, reason_code, liable_role | TAXABLE / ZERO_RATED / EXEMPT / OUT_OF_SCOPE / REVERSE_CHARGE / NOT_COLLECTED；原因与承担角色明确 |
| market_id, registration_id, policy_id, rule_code | 冻结评估、真实注册、具体包版本及命中规则；按处理结果允许注册为空 |
| tax_base, rate, charged_tax_amount, self_assessed_tax_amount | 同一组件的税基、税率、实际收取和自行计税金额；无适用税率时 rate 为空而非虚构 0% |
| calculation_method, rounding_method, rounding_scale | 本次实际使用的算法与法定取整 |
| original_component_id | 调整的原组件；支持原税率和剩余可退金额封顶 |

唯一：`line_id + jurisdiction_code + component_code`。多个组件可能引用同一税基，不能把组件税基相加当订单净额。对外销售反向征税：卖方收取为零，法定承担角色 BUYER；平台不是该买方时不能为其登记本平台的 RCM_OUTPUT。

### 8 — t_tax_evidence

| 字段 | 类型 / 含义 |
|---|---|
| calculation_id, evidence_type, source_code, source_ref | BILLING_COUNTRY / USAGE_COUNTRY / NETWORK_COUNTRY / TAXPAYER_VALIDATION 等有限类型；来源是 counterparty 或批准服务 |
| country_code, subdivision_code, identifier_type | 仅批准的派生维度，不存完整 IP / 地址 / 税号 |
| validation_status, result_code | VALID / INVALID / UNKNOWN / NOT_CHECKED；区分格式校验与官方登记核验。`source_code=MANUAL` 表示 Partner 财务在税局门户人工核验后登记，此时 `evidence_file_address` 与 `evidence_digest` 必填、`checked_at` 为人工核验时间 |
| checked_at, valid_until, provider_reference | 结果的新鲜度与外部追溯号，不能把超时记为 INVALID |
| evidence_ref, evidence_file_address, evidence_digest | 受保护快照或归档证据；引用必须在授权通道读取 |

追加写，不覆写历史核验。重算时复制已冻结的可用证据事实并保留来源引用；不回查“现在有效”来解释过去计算。冲突证据不能盲目投票，按地区证据规则处理或阻断。

## 5. 正式事实、税务底稿与单据（9–12）

### 9 — t_tax_transaction

| 字段 | 类型 / 含义 |
|---|---|
| calculation_id | 唯一；一次计算最多成为一个正式事实 |
| source_system, source_fact_type, source_fact_id, tax_scope_key | 正式业务唯一身份；与报价准备身份不同 |
| origin | AUTO / MANUAL；AUTO 由事件消费建立，MANUAL 由 Partner 控制台人工登记并经 Maker-Checker，带 `approval_ref` 与证据文件 |
| transaction_kind, original_transaction_id | ORIGINAL / REFUND / ADJUSTMENT / MANUAL_INPUT；REFUND / ADJUSTMENT 必须指向原事实；MANUAL_INPUT 是人工登记的采购进项或自计税事实，无原事实，`source_system=tax-manual`、`source_fact_type=CLIENT_INVOICE` 等、`source_fact_id` 为 Client 发票号或审批单号 |
| tax_subject_party_id, currency | 正式归属与币种；必须与 calculation 一致 |
| occurred_at, tax_point_date, tax_point_basis, registered_at | 经济事件、法定日期与原因、本系统登记时间分别保存 |
| reporting_status, reporting_block_reason | PENDING / READY；仅控制底稿完整性，不否定已发生事实 |
| adjustment_reason, approval_ref | 更正或非自动税务处理的理由与批准证据 |

唯一：四个源身份字段组合。本表由 Tax 消费 acquiring 的 `OrderPaymentSucceeded` / `RefundSucceeded` 等交易生命周期事件建立，不提供对外登记写命令；事件重复投递对应同一行，事件内容与已冻结 calculation 冲突时进死信。消费去重复用项目公共消息库的 `t_consumed_event` / `t_consumed_aggregate_version`，与 billing 相同，不计入业务表数。`NOT_MANAGED` 的 calculation 不建本表记录。人工登记（`origin=MANUAL`）同样先建 calculation（`tax_amount_source=MANUAL`）、行与组件，再建本表记录；审批通过后执行时重新校验：同一 Client 发票号在同一注册内唯一、RCM_INPUT 不超过 RCM_OUTPUT、期间未关闭、金额与行汇总一致，不满足则拒绝执行（D13）。金额只在不可变 calculation 行上保存，不维护第二份可变金额。部分供应、分期税点分别建立独立源事实和对应计算，不将一份整单计算多次确认为全部金额。已确认交易不能取消 / 删除；退款或更正追加新事实。

### 10 — t_tax_reporting_entry

| 字段 | 类型 / 含义 |
|---|---|
| transaction_id, component_id, registration_id, period_id | 正式事实到法定注册和期间；一组件可产生不同申报角色 |
| entry_role, reporting_rule_code | OUTPUT / INPUT / RCM_OUTPUT / RCM_INPUT / DISCLOSURE；匹配具体申报规则 |
| reporting_policy_id | 采用的申报 / 汇率规则包版本；与计算包分别追溯 |
| source_currency, source_tax_base, source_tax_amount | 原币税基和本条申报角色的税额；不能把交易含税总额当税基 |
| reporting_currency, fx_rate, fx_rate_date, fx_source_ref | 从 source → reporting 的明确方向、法定日期和来源证据；同币种为 1 |
| reporting_tax_base, reporting_tax_amount, deductible_tax_amount | 法定换算及取整后的金额；进项可抵扣额独立且有证据 |
| deductibility_status, deductibility_confirmed_by, deductibility_confirmed_at | NOT_APPLICABLE / PENDING / CONFIRMED / DENIED；INPUT 与 RCM_INPUT 首期建立时为 PENDING 且 deductible_tax_amount 为 0，持有方财务按证据确认后写入可抵扣额；这是本表唯一允许更新的字段组，其他列不可变 |
| tax_point_date, attribution_reason, original_entry_id | 原税点、跨期 / 调整依据、被调整底稿引用 |
| eligibility_evidence_ref | 进项抵扣、减免或坏账调整等适用资格证据 |

唯一：`transaction_id + component_id + registration_id + entry_role + reporting_rule_code`。不能无故重复生成同一角色。每条分录固定期间；已冻结申报后发现错误，追加更正事实 / 分录，不迁走原分录。没有本方申报义务的外部买方反向征税无需造本方应纳条目；本方有披露义务时生成 DISCLOSURE。

各角色按自己的正负规则汇总：输出税增加应纳，允许抵扣的进项减少应纳，披露不计应纳；不能对所有 `reporting_tax_amount` 直接求和。自行计税采购可对应 OUTPUT 与 INPUT，两者不重复增加对供应商付款。

### 11 — t_tax_document

| 字段 | 类型 / 含义 |
|---|---|
| transaction_id, document_kind, document_scope_key | TAX_INVOICE / RECEIPT / CREDIT_NOTE / DEBIT_NOTE；同一事实可能按制度需要不同法定单据 |
| issuer_party_id, registration_id, series_code, document_number | 开票主体与号码范围；无当地注册情况下的商业凭证不能伪装当地税票 |
| original_document_id, issued_at, supply_date, currency | 调整依据、开具时间、供应日期与币种 |
| policy_id, template_code, template_revision | 法定内容与模板冻结依据 |
| recipient_snapshot_ref, issuer_snapshot_ref | 当时身份引用；不使用可变 Party / Customer 当前页面渲染历史 |
| content_schema_version, document_content_json | 固定 Schema 的单据内容：行引用、商品说明、数量、币种金额、税率 / 理由、法定文字；不含买家个人原值 |
| issuance_mode | SYSTEM / EXTERNAL；SYSTEM 为本系统分号并生成文件（首期仅不含买方个人资料的 B2C 简化税票 / 收据）；EXTERNAL 为 Partner 财务在税局系统或自有开票系统开具的 B2B 税票，此时 `document_number` 可空、`external_document_ref` 与回执文件必填，本系统只冻结金额与税务事实 |
| issue_status, submission_status, submission_source | RESERVED / ISSUED / VOID；NOT_REQUIRED / PENDING / ACCEPTED / REJECTED / UNKNOWN；submission_source=AUTO / MANUAL，MANUAL 表示人工在税局平台报送后回填，回执文件必填 |
| external_document_ref, external_status_at | 税局 / 指定网络接收事实；未知时查询同一身份 |
| document_file_address, document_digest, receipt_file_address | 不可覆盖的正式文件及外部回执；发行后修改只生成新法律单据 |
| void_reason | 号码已占用但最终未发行等合法作废原因 |

唯一：`transaction_id + document_kind + document_scope_key`；以及 `issuer_party_id + series_code + document_number`。自有单据初期一张对应一个正式事实，汇总月票需另行定义行集合和唯一覆盖规则，不先造通用多对多结构。此处 JSON 是正式展示快照，金额必须等于引用税务行，不能成为第二套算税来源。

### 12 — t_tax_document_sequence

字段：`issuer_party_id, series_code, registration_id, document_kind, fiscal_year, next_number, number_format`。唯一：`issuer_party_id + registration_id + series_code`（无当地注册的商业凭证序列 registration_id 取 0 占位，不用 NULL）；需要每年重开时年度属于 series_code。同一主体的两份注册不能共用序列，避免跨注册撞号。

锁定序列行，在本地同一短事务中增加号码并创建 RESERVED 单据。事务外渲染 / 联网；失败保留已占号码，不回收。对必须先 clearance 才允许合法发行的制度，只有对应已支持适配能力确认后才进入 ISSUED。

## 6. 期间与申报（13–15）

### 13 — t_tax_period

字段：`registration_id, period_start, period_end, filing_due_date, payment_due_date, reporting_currency, status`；状态 OPEN / PREPARING / FILED / CLOSED。日期来源于实际注册与税局安排，不能仅从国家代码猜测。

唯一：`registration_id + period_start + period_end`；同一注册期间不重叠。它是稳定的归属对象，不存可变应纳余额、不存“当前申报 ID”指针。有效申报按本期法定状态和版本关系确定；关闭前检查遗漏事实、待补底稿、税局分配及必要回执。

### 14 — t_tax_return

| 字段 | 类型 / 含义 |
|---|---|
| period_id, revision, replaces_return_id, amendment_mode | 同期间版本；首版模式 FULL_REPLACEMENT，初报无前序 |
| policy_id, status | DRAFT / FROZEN / SUBMITTED / EFFECTIVE / REJECTED / SUPERSEDED；EFFECTIVE 的法定判据由制度明确 |
| output_tax_amount, deductible_tax_amount, adjustments_amount, net_due_amount | 申报币种金额；明确符号规则后 `net_due = output - deductible + adjustments`，负数表示申报净抵税额 |
| prepared_at, approved_by, approved_at, filed_at, effective_at | 准备、复核、提交与法律生效时间 |
| external_return_ref, return_file_address, return_digest, receipt_file_address | 正式报表与税局回执；手工报送也需要证据 |

唯一：`period_id + revision`。FROZEN 后金额与明细不可改；更改建立新版本。在 period 锁下使一份修订生效并将前一份置 SUPERSEDED，不允许两份同时作为本期有效应纳。提交成功是否已经生效、是否要等正式接收必须按制度定义，不能一概等“审核通过”。

可抵退资格是独立行政事实，不从申报负数自动推导。首版不管理可反复提款的通用税局信用账户：每一笔转用按实际允许金额和证据审批，每一笔退税按真实到账及税局依据登记。相关证据保存在转用事实或 payment_event，不为了新的退税批复创建一份虚假的申报修订。

### 15 — t_tax_return_item

字段：`return_id, reporting_entry_id, box_code, amount_kind, signed_amount`。`amount_kind=BASE / TAX / DEDUCTIBLE_TAX / DISCLOSURE`，金额为申报币种。

唯一：`return_id + reporting_entry_id + box_code + amount_kind`。同条底稿可能合法进入不同栏目；映射规则明确哪些栏目只是展示，哪些影响合计，不重复计入应纳。完整替代版可以再次列入本期间历史底稿；同底稿不能被另一期当新增业务重复申报。冻结申报时校验完整覆盖、排除理由与角色金额平衡。

## 7. 缴款、退税和抵税转用（16–19）

### 16 — t_tax_payment

| 字段 | 类型 / 含义 |
|---|---|
| registration_id, payer_party_id, book_subject_ref | 同一税务注册、实际出款 / 收款主体与账簿；代缴必须有明确授权 |
| direction, origin | OUTBOUND_PAYMENT / INBOUND_REFUND；PLANNED / IMPORTED_DEBIT / IMPORTED_CREDIT |
| cash_account_id, currency, amount | reconciliation 管理的银行账户引用、银行金额币种；amount 始终正数 |
| authority_code, payment_reference, instruction_file_address, instruction_digest | 实际税局、缴款参考和冻结指引；不是自由输入银行地址后直接支付 |
| execution_status | DRAFT / APPROVED / RESERVING / READY / SUBMITTED / UNKNOWN / BANK_CONFIRMED / FAILED / CANCELLED |
| authority_status | UNCONFIRMED / PARTIAL / CONFIRMED / DISPUTED；税局是否正确归属 |
| cash_reservation_ref, external_payment_ref | account 现金预留和外部支付执行引用；入站退税无出款预留 |
| bank_match_ref, suspense_resolution_ref | 正常银行匹配 / 已暂记重分类引用，互斥的现金处理路径 |
| submitted_at, bank_confirmed_at, approved_by, approved_at | 证据时间与人工审批 |

银行借记已发生时不能再 CANCELLED；UNKNOWN 不能由定时超时直接 FAILED。入站退税按实际银行贷记确认，不适用出款锁定状态。字段条件随方向校验，禁止拿一笔缴款填写负金额表示退税。

`bank_match_ref`、`suspense_resolution_ref` 分别在非空时唯一，同一匹配 / 处置不能挂到两笔 payment。跨记录可能一笔银行流水覆盖多项业务时，应先由 reconciliation 给出经批准的分配事实，Tax 不自行拆分原始银行流水。

### 17 — t_tax_payment_allocation

字段：`payment_id, period_id, based_on_return_id, allocation_purpose, payment_currency_amount, reporting_currency_amount, fx_rate, fx_rate_date, fx_source_ref, authority_status, authority_reference, confirmed_at`。

`allocation_purpose=LIABILITY_PAYMENT / CREDIT_REFUND`，与 payment.direction 一致。唯一：`payment_id + period_id + allocation_purpose`。期间必须属于支付的同一注册，申报版本必须属于该期间。每笔 payment 的 payment_currency_amount 合计必须精确等于 payment.amount；申报币种不同的分配不能直接相加。分配执行后不可改金额，税局错误分配通过有证据的纠正事实处理。

OUTBOUND_PAYMENT 的已提交 / 未知 / 已银行确认分配均防止重复付款；INBOUND_REFUND 占用来源期间可抵退额度，不能在同一额度上同时退款和转用。实际退税汇差是财务事实，不悄悄改历史申报的税额。

### 18 — t_tax_payment_event

字段：`payment_id, event_type, source_system, source_event_id, occurred_at, recorded_at, amount, currency, evidence_file_address, evidence_digest, external_reference, reason_code`。

事件：SUBMITTED、RESULT_UNKNOWN、NO_DEBIT_CONFIRMED、BANK_MATCHED、LATE_DEBIT、SUSPENSE_RECLASSIFIED、AUTHORITY_ALLOCATED、REFUND_RECEIVED 等有限事实。唯一：`source_system + source_event_id + event_type`。这张表保存真实业务证据，不保存任意 API 请求响应或完整个人资料。只有成功改变状态的业务事件和可靠发布在同一本地事务提交。

### 19 — t_tax_credit_application

字段：`application_no, registration_id, source_period_id, target_period_id, source_return_id, target_return_id, reporting_currency, amount, status, authority_reference, evidence_file_address, evidence_digest, approved_by, approved_at, applied_at, reverses_application_id`。

状态：DRAFT / SUBMITTED / APPLIED / REJECTED；amount 为正；源期间与目标期间不同、同一注册和申报币种。只有税局认可或法律明确允许的转用才进入 APPLIED；需要时按明确制度记录相应证据。`reverses_application_id` 表示对已应用记录的独立冲回，不修改原应用事实。

唯一：`application_no`（所有来源必填）；另对非空的税局分配引用建立 `registration_id + authority_reference` 唯一约束，整批批复拆分时引用必须包含真实分配行身份。禁止每次重试生成新业务编号。源、目标与申报版本冻结，撤销 / 冲回也走同一额度保护。转用没有现金流、不改变应付总额，不向 billing 发布事件，只影响第 8 节的派生清偿视图。

## 8. 清偿计算与并发保护

以申报币种计算，每个期间：

```text
N = 当前有效完整申报的 net_due_amount
P = 本期已确认银行缴款分配
C_in  = 已生效转入抵税额
C_out = 已生效转出抵税额
R = 已确认退税
balance = N - P - C_in + C_out + R
provisional_due = max(balance, 0)
new_payment_capacity = max(provisional_due - 在途缴款 - 在途抵税转入, 0)
```

这是一组派生查询，不建立 `balance` 表。银行已扣但税局尚未归属的款项可降低“避免重复支付”的暂定待缴，却**不能证明法定清偿**；法律结清只计算税局已确认 / 制度认可的分配，争议款单独列示。

每笔转用不得超过系统净贷方余额扣除在途占用后的余量，也不得超过本次税局确认 / 法律允许且经审核的金额；已经应用的转用与退税已体现在 balance 中，不能再重复扣减。转用引用批准证据覆盖的申报版本，修订后需要重新核对。涉及两个期间时，在同 registration 资源锁下校验和写入，初期无需通用税局信用账户或多级额度系统。真实银行退税即使超出预期，也保留已到账事实并挂差异，不以容量校验丢弃真实现金。

所有缴款、转用与申报操作的执行人来自该注册所属 market 的 `managed_by` 一方；平台运营对 SUBJECT_HOLDER 管理的主体只读。

锁与唯一约束：注册变更 / 准入按 subject；规则发布按 package；正式登记按源事实身份；退款按原 transaction；号码按 sequence；申报按 period；支付分配与抵税额度按 registration。锁只负责竞争，业务唯一索引和本地事务仍是最终保护。锁内禁止远程调用。

## 9. 核心不变量与查询

1. READY 计算每行 `gross = net + charged_tax`；头金额等于行汇总。self-assessed 不计入对交易对方的应付。
2. 没有税务规则、没有证据与税率为零不同；任何零收税都必须有明确 treatment、reason 与依据。
3. 历史 calculation / line / component / evidence 不随配置变化；正式交易只追加调整。
4. 同源业务事实只登记一次；Gateway 请求幂等与消息重复投递都不能生成另一份税务事实。
5. 同原行累计普通退款的净额和收税额不能超过原额；授权税务更正使用另一处理路径，不能绕过资金退款上限。
6. 实际交易存在、底稿尚缺失的状态可查询，并阻止漏报；申报封存不能只扫描已生成的 entry。
7. 正式发票内容与税务事实一致、号码唯一；电子文件在留存期可还原为原始字节。
8. 修订申报不重置历史缴款；同一税款现金只经正常结算或暂记重分类其中一条路径。
9. 税款余额与财务账一致性按子分类账模式核对：ledger 每账本每币种一个 `TAX_PAYABLE` 控制科目，本模型的 `t_tax_reporting_entry` 是其子分类账；billing 的 `SELLER_TAX` 行按税组件逐行、带纳税主体 / 辖区 / 注册三个证据列并引用 calculation_id，`TAX_PAID` / `TAX_REFUND_RECEIVED` 引用 payment；期末按账本 + 币种汇总底稿与控制科目比对，差异进入关账检查，不跨库 SQL 聚合。
10. 初期所需查询：交易税务详情与证据链、待处理异常、逐注册税务底稿、申报差异与完整性、期间待缴及在途占用、银行与税局归属差异。面向页面的聚合由 BFF 完成。
