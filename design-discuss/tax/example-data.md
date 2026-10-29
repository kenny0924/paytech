# Tax 流程走数：同一笔 SaaS 从销售到缴税

状态：**讨论稿，待评审**。2026-10-27；2026-10-29 随主设计修订（事件登记、持有方操作、`managed_by`）。流程目录见 [flows.md](flows.md)，字段含义以 [database.md](database.md) 为准。本文展示字段投影，不是全字段插入语句。可读取的合成数据见 [sale-refund-filing.json](examples/sale-refund-filing.json)：19 个表名，18 张有行，36 条示例记录；没有抵税转用时，第19张表保持空数组。

所有 ID、注册、主体、快照、文件、批准和回执均为**虚构演练数据**。`DEMO_*` 栏目不是税局真实表格字段。规则全文、必填时间和批准证据等有意省略，不能把这些投影作为生产初始化数据。10月结束后的申报和缴款属于假设时间线，不表示这些事情在2026-10-27已经发生。

## 1. 先把业务讲清楚

假设 AE 法律主体向 SA 的未登记消费者销售自动化 SaaS；没有参与供应的 SA 本地机构，SA 收税义务及有效注册已经确认；本例采用15%销售税。地区研究及实际适用条件见 [地区规则](regional-policies.md)。本例的税点、月度申报、调整归属、开票及报送要求都是演练前提，不能只由买家国家推断。

| 顺序 | 发生了什么 | 业务金额（SAR） | Tax 中的锚点 |
|---|---|---|---|
| 1 | 准备付款，先算税 | 净100 + 税15 = 收款115 | calculation **1001**，尚不是正式交易 |
| 2 | 发生本例批准的付款税点 | 金额不变 | transaction **4001**，源事实 EV-P1 |
| 3 | 开票、形成税务底稿 | 销项税 +15 | document **6001**；entry **5001** |
| 4 | 部分退款，退原税前价40% | 净-40 + 税-6 = 退款-46 | calculation **1002**；transaction **4002** |
| 5 | 开贷项单据，调整底稿 | 销项税 -6 | document **6002**；entry **5002** |
| 6 | 本例期间结束、申报生效 | 净税基60；销项税9；应纳9 | period **7001**；return **9001** |
| 7 | 向税局缴9，并确认归属 | 银行付9；税局认领9；剩余0 | payment **10001**；allocation **11001** |

退款后消费者净支付 `115 - 46 = 69`，其中 `60` 是剩余未税销售额、`9` 是销售税。**这不是平台利润**：采购成本、支付费用及其他业务金额仍由各自 Owner 管理。

## 2. 上线准备：为什么这笔交易可以收税

| 表 / ID | 关键字段投影 | 解释 |
|---|---|---|
| category / 101 | `category_code=SAAS_AUTOMATED; status=ACTIVE` | 产品税类，不在分类里硬放税率 |
| registration / 201 | `subject_party_id=90001; jurisdiction_code=SA; tax_type=VAT; status=ACTIVE; reporting_currency=SAR` | AE主体拥有一份SA注册，不代表主体变成SA公司 |
| policy / 301 | `package_code=DEMO_SA_SAAS; revision=1; status=APPROVED` | 本次计算固定引用这个版本 |
| market / 401 | `subject_party_id=90001; jurisdiction_code=SA; obligation=REGISTRATION_REQUIRED; collection_status=ENABLED; managed_by=SUBJECT_HOLDER; registration_id=201; policy_id=301` | 已批准的责任评估及准入，不是仅看国家税率；由持有方审批 |

本例的 90001 是 PayFac Partner 持有的 AE Operating Entity 的 Party，合同是 C1（Partner OE × MoR），所以它既是卖方也是税务责任方；准入、申报与缴款由该 Partner 在 Partner 控制台操作，法定申报缴纳由 Partner 财务在税局系统完成，平台只提供工具，没有自己的纳税主体。Client → OE 的采购进项首期不随事件自动登记，由 Partner 财务按 Client 发票人工登记 `MANUAL_INPUT`（见 [design.md 第 10 节](design.md#10-人工处理的边界) 与 cases.md C42），本例未展示。本例的发票 6001 是面向未登记消费者的 B2C 简化税票，不含买方个人资料，由系统自动开具（`issuance_mode=SYSTEM`）；若买家是已核验的 B2B 纳税人，则进入待开票清单由 Partner 财务外部开具。若同一订单换成 PSP 合同，`TAX_RESPONSIBLE` 变为 Client 80001，它未在 `t_tax_market` 登记，计算结果将是 `NOT_MANAGED`，本文后续各表都不会产生记录。

本例交易的角色是：`seller_party_id=90001`、`tax_subject_party_id=90001`、`acquiring_entity_no=AE901`、`book_subject_ref=DEMO-BOOK-1`。不同字段即使本例对应同一主体，也分别保留自己的含义。Consumer 是 `buyer_client_id=80001` 命名空间下的访客，`customer_no=null`，身份引用为 `DEMO-BUYER-SNAPSHOT-1`；**80001 是 Client，不是最终买家 Party ID**。

## 3. 报价与证据：先保存判断，再确认事实

### 3.1 计算头、行与税组件

| 表 / ID | 关键字段 | 值 |
|---|---|---|
| calculation / 1001 | 源准备身份 | `acquiring / ORDER / DEMO-O1 / source_revision=3 / SALE-1` |
| calculation / 1001 | 类别 / 模式 / 状态 | `SALE / MOR / READY`；`tax_amount_source=COMPUTED` |
| calculation / 1001 | 金额 | `currency=SAR; net=100; charged_tax=15; gross=115; self_assessed_tax=0` |
| line / 2001 | 所属与商品 | `calculation_id=1001; line_no=1; category_code=SAAS_AUTOMATED` |
| line / 2001 | 价格与金额 | `quantity=1; unit_price=100; EXCLUSIVE; discount=0; net=100; tax=15; gross=115` |
| component / 3001 | 所属与法律处理 | `line_id=2001; VAT; jurisdiction=SA; TAXABLE; liable_role=SELLER` |
| component / 3001 | 计算与依据 | `tax_base=100; rate=0.15; charged_tax=15; market=401; registration=201; policy=301` |

公式是 `100 × 0.15 = 15`，`100 + 15 = 115`。若改成“含税价100”，应新建计算，结果是净86.96、税13.04、收款100，不能保持税前100再从中扣税。

### 3.2 证据行

| evidence ID | calculation_id | evidence_type | country_code | validation_status / result_code |
|---|---|---|---|---|
| 8001 | 1001 | BILLING_COUNTRY | SA | NOT_CHECKED / DECLARED_COUNTRY |
| 8002 | 1001 | USAGE_COUNTRY | SA | NOT_CHECKED / DECLARED_USAGE_COUNTRY |
| 8003 | 1001 | TAXPAYER_VALIDATION | SA | NOT_CHECKED / DECLARED_NON_REGISTERED |

三行均引用 counterparty 的受保护快照；没有姓名、完整地址、IP或税号原值。`NOT_CHECKED` 明确表示没有进行官方登记核验；本例由已批准规则接受消费者声明，不把未知核验结果自动归类为未登记。是否充分由实际地区包决定，**不是宣称所有地区统一要求两份地点证据**。退款计算1002另有8004–8006三行，冻结原来源事实，不用退款时的新地址重判原销售。

若报价后地址或金额变化：生成 `source_revision=4` 对应的新 calculation；旧1001保留，但不能供新准备版本继续付款。

## 4. 正式交易、单据和退款如何串起来

| 表 | 原销售记录 | 退款记录 |
|---|---|---|
| calculation | `1001: 100 / 15 / 115` | `1002: -40 / -6 / -46; original_transaction_id=4001` |
| line | `2001; calculation=1001` | `2002; calculation=1002; original_line_id=2001` |
| component | `3001; line=2001; rate=0.15` | `3002; line=2002; rate=0.15; original_component_id=3001` |
| transaction | `4001; calculation=1001; ORIGINAL` | `4002; calculation=1002; REFUND; original_transaction_id=4001` |
| 源事实身份 | `acquiring / PAYMENT_SUCCEEDED / EV-P1 / SALE-1` | `acquiring / REFUND_SUCCEEDED / EV-R1 / SALE-1` |
| document | `6001; transaction=4001; TAX_INVOICE` | `6002; transaction=4002; CREDIT_NOTE; original_document_id=6001` |
| document_number | `DEMO-INV-2026-0001` | `DEMO-CN-2026-0001` |
| reporting_entry | `5001; transaction=4001; component=3001; tax=15` | `5002; transaction=4002; component=3002; tax=-6; original_entry_id=5001` |

上表金额三元组是净额 / 收取税 / 含税金额。transaction 不复制第二份金额，而是引用不可变 calculation。4001 由 Tax 消费 acquiring 的 `OrderPaymentSucceeded`（携带 calculation 1001、税 15、源身份 EV-P1）建立；事件重投仍对应 4001，不新建 4003。billing 消费同一事件，直接按携带的 15 生成 `SELLER_TAX` 行并引用 calculation 1001，不读取 Tax、不等待 4001 建立。退款是新记录，原来的100、15、115始终不变；剩余普通可退净额60、税9、总69。

示例退款行用 `refund_basis_amount=40` 表达经确认的原价 40% 退款基数（原稿曾用 `quantity=-0.40`，2026-10-29 改正：quantity 只表示真实数量），不假设所有订阅都可以按日等比例退款。原税率不因后来配置变化而改变。贷项单据显示“退款46”的呈现方式可由模板决定，底层调整金额仍用负数。

两个单据各自的 sequence 为901和902，`next_number=2`。文件引用 `/files/v1/DEMO-FILE-6001` 与 `/files/v1/DEMO-FILE-6002` 是不可解析的示例。`ISSUED + NOT_REQUIRED` 只表达本例假定无需电子提交的批准适用条件，不能推广成所有SA交易都无需报送。

## 5. 从底稿到申报：不直接汇总订单金额

### 5.1 稳定期间与底稿

`period 7001` 属于 `registration 201`，示例起止2026-10-01至2026-10-31，申报币种SAR。本例使用月度只是方便走数，实际周期和截止日按真实注册维护。

| entry ID | transaction / component | role | source base / tax | fx_rate | reporting base / tax | deductible | period |
|---|---|---|---|---|---|---|---|
| 5001 | 4001 / 3001 | OUTPUT | SAR 100 / 15 | 1 | SAR 100 / 15 | 0 | 7001 |
| 5002 | 4002 / 3002 | OUTPUT | SAR -40 / -6 | 1 | SAR -40 / -6 | 0 | 7001 |

同币种仍明确 `source_currency=reporting_currency=SAR; fx_source_ref=SAME_CURRENCY`。跨币种时要冻结法定汇率、日期与来源；缺汇率时交易仍存在、reporting=PENDING，并阻止遗漏申报。

### 5.2 申报版本和栏目明细

`return 9001: period_id=7001; revision=1; status=EFFECTIVE; output_tax_amount=9; deductible_tax_amount=0; adjustments_amount=0; net_due_amount=9`。

| item ID | return_id | reporting_entry_id | box_code（教学代码） | amount_kind | signed_amount |
|---|---|---|---|---|---|
| 9101 | 9001 | 5001 | DEMO_OUTPUT_BASE | BASE | 100 |
| 9102 | 9001 | 5001 | DEMO_OUTPUT_TAX | TAX | 15 |
| 9103 | 9001 | 5002 | DEMO_OUTPUT_BASE | BASE | -40 |
| 9104 | 9001 | 5002 | DEMO_OUTPUT_TAX | TAX | -6 |

税基是 `100 - 40 = 60`；应纳是 `15 - 6 - 0 + 0 = 9`。不能把四行 signed_amount 全部相加得到69再当应纳税。这里退款已经通过销项底稿扣除，`adjustments_amount` 不能再减6。

## 6. 付款：同一笔9，四个观察时刻

| 时刻 | payment.execution_status | payment.authority_status | 银行确认额 | 在途占用 | 可再新建缴款 | 含义 |
|---|---|---|---|---|---|---|
| 尚未申请 | — | — | 0 | 0 | 9 | 应纳9，可申请9 |
| 已提交但结果未知 | UNKNOWN | UNCONFIRMED | 0 | 9 | 0 | 查原付款，不再付9 |
| 银行已经扣款 | BANK_CONFIRMED | UNCONFIRMED | 9 | 0 | 0 | 银行已付，待税局确认 |
| 税局确认本期归属 | BANK_CONFIRMED | CONFIRMED | 9 | 0 | 0 | 本例税款清偿；关期仍需其余完整性检查 |

最后一行是JSON快照的状态；前面各行是同一 payment 的演进，不是四笔付款。

| 表 / ID | 字段投影 |
|---|---|
| payment / 10001 | `registration_id=201; payer_party_id=90001; OUTBOUND_PAYMENT; PLANNED; currency=SAR; amount=9`；由 Partner 在其控制台创建、付款并上传证据 |
| payment / 10001 | `cash_reservation_ref=DEMO-RESERVATION-1; bank_match_ref=DEMO-MATCH-1; suspense_resolution_ref=null` |
| allocation / 11001 | `payment_id=10001; period_id=7001; based_on_return_id=9001; LIABILITY_PAYMENT; payment_amount=9; reporting_amount=9; fx=1; authority_status=CONFIRMED` |
| event / 12001 | `SUBMITTED; source=tax; source_event_id=DEMO-SUBMIT-1` |
| event / 12002 | `RESULT_UNKNOWN; source=tax; source_event_id=DEMO-UNKNOWN-1` |
| event / 12003 | `BANK_MATCHED; source=reconciliation; source_event_id=DEMO-MATCH-EVENT-1` |
| event / 12004 | `AUTHORITY_ALLOCATED; source=tax; source_event_id=DEMO-AUTHORITY-EVENT-1` |

四个事件各记录金额9是为了说明同一付款的证据，**不能把event.amount相加说已付36**。真实现金只以确认的 payment / allocation 计一次，并由 billing → account → ledger 处理。主例采用正常银行匹配路径；若此前已暂记，走重分类，不能再扣一次现金。

## 7. 在同一主例上继续：漏报、修订、抵税或退税

以下是主例完成后的**追加分支投影**，未合并进主例JSON，避免把先后状态混成同一快照。修订方式和调整归属期仍须符合实际制度；本例假设都依法归属7001并以完整替代版申报。

### A. 漏了一笔净20、税3的销售

追加 `transaction=4003 / calculation=1003 / entry=5003 / OUTPUT tax=3`，并保留真实税点和较晚登记时间。形成 `return=9002, revision=2, replaces_return_id=9001, net_due=12`；9002生效后9001为SUPERSEDED。

新版 return_item 包含5001、5002、5003的完整映射，销项 `15 - 6 + 3 = 12`。之前 payment10001 的9仍归7001，**只需补3**。假设第二笔 payment10002 + allocation11002 最终都确认3，则累计已付12、待缴0。不能把旧版9和新版12相加，也不能再缴12。

### B. 后来又依法冲回新增销售，形成超缴3

追加 `transaction=4004, REFUND, original_transaction_id=4003`、`calculation=1004, net=-20, tax=-3, gross=-23`、`entry=5004, tax=-3`。假设允许修订同一期，`return=9003, revision=3, replaces_return_id=9002, net_due=9`。

此时有效应纳9、银行已付12，派生余额 `9 - 12 = -3`。这只是超缴3，**不是已获准退税3**。取得本次合法认可后，下面两条是互斥的全额处置示例：

| 选择 | 示例新数据 | 结果 |
|---|---|---|
| B1 转用到下一期 | `credit_application 13001: application_no=DEMO-CREDIT-1; registration=201; source_period=7001; target_period=7002; source_return=9003; target_return=9004; currency=SAR; amount=3; status=APPLIED; authority_reference=DEMO-CREDIT-APPROVAL-1` | 假设7002应纳15，转入后待缴12；7001剩余可用0。没有银行付款或退税 |
| B2 税局把3退到银行 | `payment 10003: INBOUND_REFUND; IMPORTED_CREDIT; amount=3; BANK_CONFIRMED; CONFIRMED`；`allocation 11003: period=7001; based_on_return=9003; CREDIT_REFUND; amount=3`；真实到账event | 银行收到3；7001剩余可用0。7002仍待缴15 |

B1 与 B2 不能各用3；如需要拆分，只能在同注册的可用额与本次合法证据范围内分配。两者都不会凭空改写9003的申报应纳9。为解释表关系，这里的9004是假设下期已经存在的有效申报，不能在它还不存在时伪造引用。

## 8. 四种常见变体：哪些数据真的不同

| 场景 | 字段 / 金额 | 与主例区别 |
|---|---|---|
| SA跨境B2B满足反向征税条件 | `net=100; charged_tax=0; gross=100; treatment=REVERSE_CHARGE; liable_role=BUYER; rate=null` | 买方自行计税；不为外部买方写本平台RCM_OUTPUT；有本方披露义务才写DISCLOSURE |
| Partner OE 向境外 Client 采购进口服务，自计税示例 | `supply_kind=PURCHASE; net=100; charged_tax=0; gross=100; self_assessed_tax=5`；两条entry分别RCM_OUTPUT税5、RCM_INPUT税5，后者 `deductibility_status=PENDING`、可抵扣0，财务确认后可抵扣3 | Client 收100；OE 申报净税负2；抵扣资格3须有证据；与销售同一源事件、scope `PURCHASE-1` |
| PayFac 向 Client 收取的服务费 | `supply_kind=PLATFORM_SERVICE; net=10; charged_tax=0.50; gross=10.50`，示例税率5% | 使用新的供应及正式源身份；不把消费者销售再次算税；平台自身技术服务费不进入 |
| 下一月订阅续费 | 新ORDER / 周期源事实、新calculation、新transaction；金额相同也重新判断有效规则 | 旧交易不可复用为新期间正式事实；仍有效的证据可按规则引用 |

采购的5%和允许抵扣3、服务费的5%是金额教学假设，不是对未明确地区与合同的税务结论。消费者退款、供应商退款、税局退税也分别是交易调整、采购调整、银行清偿事实，不能只用一个没有方向和原事实的“refund”记录。

## 9. 本轮数据验证范围

仅核对合成数据的引用、唯一身份、计算与退款金额、申报栏目、付款分配和上述分支的算术；不代表法规适用、正式接口、申报提交或生产系统通过验收。进入实现时，按 [cases.md](cases.md) 的行为与并发用例验证。
