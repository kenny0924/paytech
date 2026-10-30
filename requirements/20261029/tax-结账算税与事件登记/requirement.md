# tax：结账算税与事件登记 · 需求

> 状态：**阶段 1 已确认（2026-10-29）**：Q3 改为 Partner 控制台不做、税务页面只做 Operations（决定第 17 条），其余按建议。来源：[2026-10-29 tax 决定](../../../agent-docs/decisions/2026-10-29-tax-domain-fact-service.md)（Confirmed）与详细设计 [design-discuss/tax](../../../design-discuss/tax/design.md) 第 11 节切片 2。确认记录见 [delivery.md](./delivery.md)；技术方案从阶段 2 起写入 [technique.md](./technique.md)。

## 1. 背景

- tax 决定已确认：tax 是领域层事实服务。acquiring 在支付准备时同步取得不可变税务计算并冻结；tax 与 billing 各自消费支付成功事件，billing 不等 tax。服务对象是平台登记的纳税主体，首批是 PayFac Partner 的 AE / SA / EG 主体。
- 现状（阶段 1 只读核对，阶段 2 补全证据）：
  - `paytech/tax` 是空壳，只有启动类与旧 Finance 来源的 24 张表 Schema，没有业务代码。
  - acquiring 的 `CheckoutTaxPort` 由固定 Mock 实现，一律零税，`snapshotId` 取订单 ID，`snapshotHash` 为 64 个 `d`（`CustomerCheckoutIntegrationConfiguration`）。订单表 `finance_tax_snapshot_id`、`finance_tax_snapshot_hash` 为 NOT NULL。
  - 税额在 Client 建单时（`CreateCheckoutProcessor`）一次算定；消费者在 Hosted Checkout 提交资料（`SaveConsumerProcessor`）后不重算。
  - `OrderPaymentSucceeded` 携带 `financeTaxSnapshotId`。billing 只把它记为 `TAX_SNAPSHOT` 引用，不生成 `SELLER_TAX` 行；自动 PAYMENT 事实的不变量目前不接受 `SELLER_TAX` 行。
  - account 已把 `SELLER_TAX` 映射到 `TAX_AUTHORITY` 付收方角色，ledger 已有 `TAX_PAYABLE` 科目和 `SELLER_TAX` 过账规则。
  - acquiring 没有发布退款成功事件，billing 的 REFUND 事实也未实现。
  - Partner 控制台只登记了应用，没有页面或 BFF。counterparty 只有 `t_customer` 一张业务表，档案与快照（切片 2）未开始。

## 2. 目标

1. 消费者在结账时付的税由 tax 按规则包真实算出，不再是零税 Mock。
2. 每笔支付成功形成 tax 的不可变正式事实与申报底稿，按源事实身份幂等。
3. billing 的 PAYMENT 事实按事件携带的冻结结果生成 `SELLER_TAX` 行，经 account → ledger 进入 `TAX_PAYABLE`。
4. PSP / Direct Merchant 的订单返回 `NOT_MANAGED`，支付不受影响。
5. 向未登记消费者自动开具不含个人资料的 B2C 简化税票 / 收据。

## 3. 使用者与场景

| 使用者 | 场景 | 成功结果 |
|---|---|---|
| Client | 用 C1（Partner OE × MoR）合同建单 | 建单响应带按 Client 声明国家计算的税额（暂估），订单不可支付，直到消费者提交资料后重算 |
| 消费者 | 在 Hosted Checkout 提交账单地址后付款 | 看到按账单国家重算的税额与含税总额，按该金额付款，付款后可下载简化税票 / 收据 |
| Client | 用 PSP 合同建单并自报税额 | 订单税额等于自报值，`NOT_MANAGED`，支付正常 |
| 平台运营 | 查看某订单的税务计算与正式事实 | 能看到计算版本、处理原因、依据规则包、正式事实与底稿状态（只读） |
| PayFac Partner 财务 | 期末核对 | 本期正式事实与底稿完整、可按注册与期间导出（导出方式本期见待确认 Q3） |
| billing / account / ledger | 支付成功入账 | PAYMENT 事实含逐组件 `SELLER_TAX` 行，TAX_PAYABLE 余额与 tax 底稿合计一致 |

## 4. 本期范围

| # | 范围 |
|---|---|
| R1 | tax 服务落地：按决定重建本期所需的表（规则与准入：category、market、registration、policy；计算：calculation、line、component、evidence；事实：transaction、reporting_entry、period；单据：document、document_sequence），其余申报 / 缴款表随后续需求建；旧 24 张表 Schema 作废 |
| R2 | `CalculateTax` 内部接口：READY / BLOCKED / NOT_MANAGED；未税与含税价、逐行计算与确定性尾差；证据为国家维度（Client 声明国家、消费者账单国家）；规则包按 Schema 校验 |
| R3 | 首批地区包与主数据：AE / SA / EG 的数字产品 SaaS 销售包（B2C）、税类 `SAAS_AUTOMATED`；mor-test 演示主体的 market / registration 以评审过的 DML 初始化（见 Q2） |
| R4 | acquiring 接入真实 `CalculateTax`：建单时按 Client 声明国家报价（暂估，不可支付）；消费者提交资料后按账单国家重算并绑定新 `source_revision`；支付只能基于最新 READY 计算；`finance_tax_snapshot_*` 改为 calculation 引用 |
| R5 | acquiring `OrderPaymentSucceeded` 载荷：可空新增 calculation ID、税务状态、冻结税额、逐组件税额与 `tax_scope_key`，`financeTaxSnapshotId` 新单置空；保持 schema 1（阶段 2 S-08） |
| R6 | tax 消费 `OrderPaymentSucceeded`：建立正式交易与申报底稿（期间按注册自动生成），按源事实身份幂等，内容冲突进死信；`NOT_MANAGED` 不建事实 |
| R7 | billing：按事件携带的冻结结果逐组件生成 `SELLER_TAX` 行（带纳税主体 / 辖区 / 注册三个证据列），引用 calculation；去掉 `TAX_SNAPSHOT` 引用；account / ledger 映射核对 |
| R8 | B2C 简化税票 / 收据：正式交易后本地分号、生成不含个人资料的文件（`issuance_mode=SYSTEM`）并存入 file 服务；本需求只由运营在税务页下载，消费者与 Client 入口随 checkout-web 实现（阶段 2 S-02） |
| R9 | 运营只读查询：Operations 新增独立"税务"菜单页，按订单号查看计算、处理原因、正式事实、底稿与税票；BFF 只调 tax（阶段 2 S-01，不新建订单详情页） |
| R10 | 文档：tax 数据库文档与 PDManer、`CURRENT_IMPLEMENTATION.md`、事件目录、design-discuss/tax 实施回写 |

## 5. 不做项

- 退款的税务登记与贷项单据：acquiring 尚未发布退款成功事件，billing REFUND 也未实现；随退款事实需求一起做（见 Q1）。
- B2B：买方税号采集与核验、反向征税、B2B 税票。本期所有买家按 B2C 处理（见 Q4）。
- counterparty 税务身份快照与受控渲染（随 counterparty 切片 2）。
- 人工登记通道（`MANUAL_INPUT`、`ADJUSTMENT`）、Partner 控制台税务工作台与待处理清单、申报版本、缴款、退税、抵税转用（后续需求，见 Q3）。
- PayFac 向 Client 的服务费税、采购侧进项。
- 电子发票报送 / clearance、外部税号核验接口。
- 不对历史订单补算税额；存量订单的零税 Mock 快照不导入为正式事实。

## 6. 业务规则

| 编号 | 规则 |
|---|---|
| B1 | `TAX_RESPONSIBLE` 来自 commercial 商业关系快照；在 tax 没有任何 market 记录 → `NOT_MANAGED`（税额取 Client 自报值，可为零）；有记录但 `collection_status=BLOCKED` 或无匹配规则 → `BLOCKED`，订单不可支付 |
| B2 | 建单报价的证据来源记为 Client 声明，结果标记为暂估；只有消费者提交资料后、`source_revision` 最新的 READY 计算可供支付 |
| B3 | 价格模式按订单行：未税价 `tax = round(net × rate)`；含税价 `tax = round(gross × rate / (1 + rate))`；取整按地区包，尾差确定性分配并逐行记录 |
| B4 | 计算不可变；地址、商品、金额、卖方任一变化都建新计算，旧计算保留 |
| B5 | 正式事实唯一身份 `source_system + source_fact_type + source_fact_id + tax_scope_key`；事件重复投递对应同一事实；事件税额与冻结计算不一致 → 死信并告警 |
| B6 | billing 不读 tax、不等 tax；`NOT_MANAGED` 不生成 `SELLER_TAX` 行；`SELLER_TAX` 按税组件逐行，付方为渠道 / 消费者资金、收方为 `TAX_AUTHORITY`（以 account 现有映射为准，阶段 2 核实） |
| B7 | 事务公理：tax 计算时取 commercial 快照、规则包读取在事务外；本地事务只写 tax 库；对外事件经补偿事件表 |
| B8 | 简化税票不含消费者姓名、地址、邮箱；号码按主体 + 注册 + 序列唯一，失败不回收号码 |

## 7. 逐系统变动点

| 系统 | 变动点 | 类型 |
|---|---|---|
| **tax** | 新 PDManer 模型与 Schema DDL（R1 所列 13 张表 + 补偿事件 + 消费去重两张标准表）；旧 24 张表作废；初始化 DML（税类、三国地区包、演示主体 market / registration） | 模型 / 表 / DML |
| | `-api`：`CalculateTax`（POST，内部）、按订单 / calculation 查询（GET，内部，供 Operations BFF） | 接口 |
| | 计算引擎：规则包 Schema 校验、EQ / IN 条件匹配、互斥校验、比例算法、含税反算、尾差分配 | 代码 |
| | `OrderPaymentSucceeded` 消费者（首个 MQ 消费者）；正式交易、底稿、期间自动生成；补偿任务生成税票（`TaxTransactionRecorded` 本期不发布，technique 11.4） | 事件 |
| | B2C 简化税票：序列分号、文件生成（经 file 服务存 `FileAddress`）、下载查询 | 代码 / 集成 |
| | auto 测试：三国 × 未税 / 含税 × READY / BLOCKED / NOT_MANAGED；多行尾差；两阶段报价；事件幂等与冲突死信；票据分号并发 | 测试 |
| **acquiring** | `CheckoutTaxPort` 真实 HTTP Client 替换 Mock；建单报价（暂估）与 `SaveConsumerProcessor` 后重算；支付前校验最新 READY 计算 | 代码 |
| | 订单表 `finance_tax_snapshot_id / hash` 改为 calculation 引用列（迁移与回滚阶段 3 定）；`tax_calculation_status` 等必要列 | 表 |
| | `OrderPaymentSucceeded` 载荷（schema 1 可空新增）：calculation ID、冻结税额、`tax_scope_key`；Client / Customer API 响应的税额字段语义（暂估 / 最终） | 事件 / 接口 |
| | auto 测试：Mock 端点返回三种状态、重算路径、旧 Mock 删除 | 测试 |
| **billing** | `OrderPaymentSucceededEventReader` 读新字段；`PaymentFactAssembler` 逐组件生成 `SELLER_TAX` 行；`BillingFactInvariants` 允许自动 PAYMENT 含 `SELLER_TAX`；`FactRefType.TAX_SNAPSHOT` 改为 calculation 引用；明细行三个证据列 | 代码 / 表 |
| **account / ledger** | 核实现有 `SELLER_TAX` → `TAX_AUTHORITY` 映射与 `TAX_PAYABLE` 过账规则可直接承接；如需调整只改映射与规则包 | 核实 / 配置 |
| **commercial** | 无改动；核实 Checkout 解析已输出 `TAX_RESPONSIBLE` 角色绑定（`t_order_*_role` 已有该角色） | 核实 |
| **Platform** | file 服务存放税票文件；Operations 订单详情税务分区的 Route / 权限注册；语言包 | 注册数据 |
| **foundation messaging** | 事件目录：`OrderPaymentSucceeded` 新增消费方 tax（保持 v1、可空新增字段，technique S-08） | 配置 |
| **operations-console-bff / web** | 订单详情"税务"分区（只读）：计算版本、状态、逐行税额、处理原因、规则包、正式事实与底稿状态；i18n | 页面 |
| **checkout-web / acquiring Customer API** | 显示重算后的税额；付款后简化税票下载入口（见 Q5） | 页面 / 接口 |
| **mor-test 环境** | 新服务 tax-service 首次部署（库、账号、compose、MQ、XXL 如需） | 部署 |
| **文档** | tax 数据库文档、`CURRENT_IMPLEMENTATION.md`、events.yaml、design-discuss/tax 实施回写、AGENTS（不改） | 文档 |

## 8. 验收标准

功能：

| 编号 | 标准 |
|---|---|
| AC-01 | C1 合同、AE 演示主体、SA 账单国家、未税价 100 USD：消费者提交资料后计算 READY，税 15，总额 115；付款金额为 115 |
| AC-02 | tax 接口级：含税价 100 得净额 86.96、税 13.04；AE 本地 5%、EG 14% 各一例结果与 [cases.md](../../../design-discuss/tax/cases.md) 第 1 节一致（acquiring 本期只发未税价，阶段 2 S-06） |
| AC-03 | 建单时按 Client 声明国家得到暂估税额；消费者账单国家不同则重算，两份计算都保留，只有最新 READY 可支付；用暂估计算发起支付被拒绝 |
| AC-04 | PSP 合同订单返回 `NOT_MANAGED`，税额等于 Client 自报值；支付成功后 tax 无正式事实、billing 无 `SELLER_TAX` 行 |
| AC-05 | 主体在 market 有记录但该地区 `BLOCKED` 或无匹配规则：返回 `BLOCKED` 与缺失原因，订单不可支付；不返回零税成功 |
| AC-06 | 多行订单（三行、含折扣）逐行税额之和等于头税额，尾差记录在行上 |
| AC-07 | 支付成功后 tax 建立唯一正式交易与底稿，期间归属正确；同一事件重复投递不产生第二条 |
| AC-08 | 事件税额与冻结计算不一致时 tax 进死信并告警，不建事实 |
| AC-09 | billing PAYMENT 事实含逐组件 `SELLER_TAX` 行并引用 calculation；account / ledger 入账成功，`TAX_PAYABLE` 增加 15 |
| AC-10 | tax 不可用时 billing 照常入账；tax 恢复后补登记，金额与 billing 一致 |
| AC-11 | 支付成功后生成简化税票：号码唯一、文件不含消费者个人资料、金额与正式事实一致；并发两笔不撞号 |
| AC-12 | 存量订单（零税 Mock 快照）发布后可正常查询与支付回调，不导入为正式事实 |

视觉 / 交互：

| 编号 | 标准 |
|---|---|
| AC-13 | Operations"税务"页按订单号查询：显示计算状态徽标（READY / BLOCKED / NOT_MANAGED）、逐行税额与税率、处理原因、规则包版本、正式事实与底稿状态；无计算时空状态；en / zh-CN |
| AC-14 | 运营在税务页可下载该订单的简化税票（经 BFF 授权）；消费者侧页面不在本需求（checkout-web 无代码，阶段 2 S-02），Customer API 返回重算后的税额与 BLOCKED 原因 |

## 9. 已确认事实、假设与待确认

已确认：tax 决定全文（2026-10-29）；首批纳税主体为 PayFac Partner 的 AE / SA / EG 主体；平台不是纳税主体；申报缴纳由 Partner 财务处理。

假设：

- A1 mor-test 的演示 OE AE001 视为 PayFac 的 AE 主体，演示注册 SA / EG 为非居民注册（路径 A）。仅用于演示，不代表真实注册。
- A2 首批产品均为 `SAAS_AUTOMATED` 税类；订单行 `tax_category_code` 已存在，取值映射在阶段 2 核实。
- A3 税点为付款成功（SaaS 预付），期间按注册的申报周期自动生成。

待确认：

| 编号 | 问题 | 建议 |
|---|---|---|
| Q1 | 退款的税务登记是否放入本需求？acquiring 尚无退款成功事件，billing REFUND 也未实现 | 不放。随 billing 需求 C（REFUND 事实）一起做，届时 acquiring 发布退款成功事件，tax 与 billing 同时消费 |
| Q2 | market / registration 本期怎么维护？Partner 控制台不存在，决定又规定平台运营对 Partner 主体只读 | 本期用评审过的初始化 DML 写入演示主体；维护界面随 Partner 控制台税务工作台需求交付 |
| Q3 | Partner 控制台税务工作台（人工登记、待处理清单、申报、缴款）作为下一个需求，是否先做 Partner 控制台的应用骨架（partner-console-web / bff、登录、权限）？ | **已定（用户）：** Partner 控制台不做；后续税务工作台做在 Operations，由平台运营代 Partner 操作（决定第 17 条）。本需求仍只到运营只读查询 |
| Q4 | 本期 B2B 买家怎么处理？没有税号采集，SA / EG 的 B2B 反向征税无法判定 | 一律按 B2C 收税，并在规则包标注"B2B 未支持"；B2B 随 counterparty 快照与人工核验一起做 |
| Q5 | 简化税票下载入口放在哪里：Hosted Checkout 完成页、邮件、还是只给 Client 通过 API 下载转交？ | Checkout 完成页下载 + Client API 按订单查询；不发邮件 |
| Q6 | 地区包与税率的业务内容由谁复核？ | 本期按 regional-policies.md 起草演示包，标为"未经税务顾问复核"；真实启用前由 Partner 税务顾问复核 |

## 10. 阶段安排

默认八阶段。阶段 2 核实：acquiring 建单与资料提交的完整调用链、订单税额字段的对外语义；`OrderPaymentSucceeded` 的版本规则与消费方；billing 组装与不变量改动面；account / ledger 映射能否直接承接多行 `SELLER_TAX`；commercial 快照是否已输出 `TAX_RESPONSIBLE`；file 服务的私有文件与下载授权方式；tax 新服务在 mor-test 的部署前置。本需求涉及新页面（Operations 订单税务分区、Checkout 税票入口），阶段 3 须先出 Pencil 画板再写前端。SQL 应用、部署、提交分别授权。
