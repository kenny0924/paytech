export type Status = "confirmed" | "proposed" | "mvp" | "later" | "open";

export interface NavigationItem {
  id: string;
  label: string;
  group?: string;
}

export interface Actor {
  id: string;
  name: string;
  description: string;
}

export interface UseCase {
  id: string;
  title: string;
  actorIds: string[];
  result: string;
  status: Status;
}

export interface SystemDefinition {
  code: string;
  name: string;
  responsibility: string;
  sourceOfTruth: string;
  useCases: UseCase[];
  provides: string[];
  consumes: string[];
  publishes: string[];
}

export const NAV_ITEMS: NavigationItem[] = [
  { id: "overview", label: "项目概览" },
  { id: "business-model", label: "业务模型" },
  { id: "architecture", label: "原始架构图" },
  { id: "domain-map", label: "细化领域地图" },
  { id: "end-to-end", label: "端到端流程" },
  { id: "use-cases", label: "平台级 Use Case" },
  { id: "systems", label: "系统职责与 Use Case" },
  { id: "payment-model", label: "支付模型" },
  { id: "finance-model", label: "财务模型" },
  { id: "decisions", label: "已确认决策" },
  { id: "open-questions", label: "待讨论事项" },
];

export const ACTORS: Actor[] = [
  { id: "client", name: "Client", description: "通过平台发行商品、创建 Checkout、发起退款并收取结算款的个人或企业。" },
  { id: "consumer", name: "Consumer", description: "在托管 Checkout 中付款并接受 Operating Entity 条款的最终消费者。" },
  { id: "operations", name: "Operations", description: "执行客户、业务准入、渠道、退款和结算运营。" },
  { id: "compliance", name: "Compliance", description: "负责 KYC/KYB、制裁筛查、复审和合规处置。" },
  { id: "finance", name: "Finance", description: "处理计费分润、账本、成本对账、准备金和人工付款。" },
  { id: "partner", name: "Local Partner", description: "参与当地 Operating Entity 经营并获得约定分润的合作方。" },
  { id: "acquirer", name: "Acquirer", description: "Network International、Geidea 等收单或网关服务方。" },
  { id: "client-system", name: "Client System", description: "通过 API 和 Webhook 创建交易并完成履约的客户后端。" },
];

export const GLOBAL_USE_CASES: UseCase[] = [
  { id: "E2E-01", title: "客户入驻并获得交易资格", actorIds: ["client", "compliance", "operations"], result: "客户、协议和配置化业务政策允许创建交易", status: "mvp" },
  { id: "E2E-02", title: "创建并完成一次 Checkout", actorIds: ["client-system", "consumer", "acquirer"], result: "下单时锁定不可变订单快照，支付确认后请求履约", status: "mvp" },
  { id: "E2E-03", title: "处理全额或部分退款", actorIds: ["client", "operations", "acquirer"], result: "渠道退款、订单更新、账本冲销与 Webhook 完成", status: "mvp" },
  { id: "E2E-04", title: "处理拒付", actorIds: ["acquirer", "client", "operations"], result: "完成举证或认损并记录最终财务影响", status: "mvp" },
  { id: "E2E-05", title: "计算多方费用与分润", actorIds: ["finance", "partner"], result: "Client 应付、Partner 应付、平台收入和预计成本形成计算快照", status: "mvp" },
  { id: "E2E-06", title: "向 Client 人工结算", actorIds: ["finance", "operations", "client"], result: "结算审批、付款凭证与余额闭环", status: "mvp" },
  { id: "E2E-07", title: "计算 Local Partner 分润", actorIds: ["finance", "partner"], result: "按独立策略生成合作方应付", status: "mvp" },
  { id: "E2E-08", title: "渠道清算与银行到账对账", actorIds: ["finance", "acquirer"], result: "确认实际成本并以调整分录处理交易、费用与实收差异", status: "mvp" },
  { id: "E2E-09", title: "生成多主体复式账务", actorIds: ["finance"], result: "各法律主体和币种的收入、成本及多方应付独立且平衡", status: "mvp" },
];

const uc = (id: string, title: string, actors: string[], result: string, status: Status = "mvp"): UseCase => ({ id, title, actorIds: actors, result, status });

export const SYSTEMS: SystemDefinition[] = [
  {
    code: "CLIENT",
    name: "Client System",
    responsibility: "管理平台客户主档、成员和业务状态，不负责给出合规结论。",
    sourceOfTruth: "Client Profile",
    useCases: [
      uc("CLI-01", "注册个人或企业 Client", ["client"], "创建 PENDING 客户档案"),
      uc("CLI-02", "维护基础资料和联系方式", ["client"], "形成版本化资料变更"),
      uc("CLI-03", "管理团队成员与角色", ["client"], "完成邀请、禁用和授权"),
      uc("CLI-04", "查询当前业务状态", ["client-system"], "返回能否创建 Checkout"),
      uc("CLI-05", "暂停、恢复或终止 Client", ["operations"], "状态变化且原因可审计"),
      uc("CLI-06", "关联外部收款资料", ["client", "finance"], "绑定已验证的付款账户"),
    ],
    provides: ["Client 查询", "状态校验", "成员管理"],
    consumes: ["KycApproved", "KycRejected", "ContractActivated"],
    publishes: ["ClientCreated", "ClientSuspended", "ClientProfileChanged"],
  },
  {
    code: "COMPLIANCE",
    name: "Compliance System",
    responsibility: "负责身份审核和持续合规，不处理实时交易欺诈。",
    sourceOfTruth: "KYC Case",
    useCases: [
      uc("CMP-01", "创建 KYC/KYB 申请", ["client"], "生成材料清单"),
      uc("CMP-02", "采集身份证明、活体与税务信息", ["client", "compliance"], "保存验证结果与证据"),
      uc("CMP-03", "执行制裁、PEP 与负面信息筛查", ["compliance"], "生成命中和风险等级"),
      uc("CMP-04", "批准、拒绝或要求补件", ["compliance"], "形成不可覆盖的审核决定"),
      uc("CMP-05", "执行周期复审", ["compliance"], "更新有效期和持续风险"),
      uc("CMP-06", "处理重大资料变更", ["client", "compliance"], "重新审核或限制交易"),
      uc("CMP-07", "查询完整审核轨迹", ["compliance"], "获得材料、结果和决定时间线"),
    ],
    provides: ["KYC 申请", "合规状态", "审核决定"],
    consumes: ["ClientCreated", "ClientProfileChanged"],
    publishes: ["KycSubmitted", "KycApproved", "KycExpired", "ComplianceRestrictionApplied"],
  },
  {
    code: "CONTRACT",
    name: "Contract System",
    responsibility: "管理发行授权、客户费率与合作公司分润协议。",
    sourceOfTruth: "Agreement",
    useCases: [
      uc("CON-01", "生成发行协议", ["operations"], "按模板创建待签版本"),
      uc("CON-02", "签署或接受协议", ["client"], "记录签署人、时间、IP 和版本"),
      uc("CON-03", "配置 Client 百分比与固定费用", ["operations", "finance"], "生成有生效期的商业条款"),
      uc("CON-04", "配置 Local Partner 分润策略", ["operations", "finance"], "形成独立策略版本"),
      uc("CON-05", "变更、续签或终止协议", ["operations"], "历史交易保持原条款"),
      uc("CON-06", "查询交易适用协议", ["client-system", "finance"], "返回唯一有效版本"),
    ],
    provides: ["协议签署", "商业条款", "分润条款"],
    consumes: ["KycApproved"],
    publishes: ["AgreementActivated", "AgreementAmended", "AgreementTerminated"],
  },
  {
    code: "CHECKOUT",
    name: "Checkout System",
    responsibility: "接收客户下单信息，负责托管支付会话、订单报价锁定和消费者交互。",
    sourceOfTruth: "Checkout Session",
    useCases: [
      uc("CHK-01", "下单时传入商品与金额", ["client-system"], "接收外部订单号、金额、币种、Items、Customer、ReturnUrl 与 Metadata"),
      uc("CHK-02", "验证 Client、业务政策、金额和回调", ["client-system"], "校验明细合计、币种、签名和 IdempotencyKey"),
      uc("CHK-03", "计算并锁定消费者报价", ["client-system", "finance"], "确定金额、税额、币种和适用条款"),
      uc("CHK-04", "请求创建待支付订单", ["client-system"], "Order 保存不可变订单快照并返回 orderId"),
      uc("CHK-05", "执行幂等创建 Checkout Session", ["client-system"], "Order 持久化成功后才创建可支付 Session 并返回 checkoutUrl"),
      uc("CHK-06", "展示卖方、商品和法律条款", ["consumer"], "消费者明确接受交易主体"),
      uc("CHK-07", "采集账单和支付上下文", ["consumer"], "形成支付与税务必要信息"),
      uc("CHK-08", "发起支付并展示处理中", ["consumer"], "创建 Payment Intent 且避免重复付款"),
      uc("CHK-09", "处理成功、失败、取消和过期页面", ["consumer"], "正确展示但不越权确认支付"),
      uc("CHK-10", "查询 Checkout 状态", ["client-system"], "返回统一状态与关联订单"),
    ],
    provides: ["Create Checkout", "Get Checkout", "Hosted Checkout UI"],
    consumes: ["AgreementActivated", "ClientSuspended", "OrderCreated", "PaymentSucceeded", "PaymentFailed"],
    publishes: ["CheckoutCreated", "CheckoutCompleted", "CheckoutExpired"],
  },
  {
    code: "ORDER",
    name: "Order & Fulfillment System",
    responsibility: "保存消费者购买合同并协调履约；订单、支付和履约状态相互独立。",
    sourceOfTruth: "Order / Order Item Snapshot",
    useCases: [
      uc("ORD-01", "创建待支付订单", ["client-system"], "验证 Checkout 请求并生成唯一 PENDING_PAYMENT 订单"),
      uc("ORD-02", "保存不可变订单快照", ["client-system"], "锁定商品、数量、单价、卖方和协议，历史订单不受配置修改影响"),
      uc("ORD-03", "确认订单已支付", ["client-system"], "消费 PaymentSucceeded 并独立更新订单状态"),
      uc("ORD-04", "请求 Client System 履约", ["client-system"], "发送可重试履约事件"),
      uc("ORD-05", "确认履约成功或失败", ["client-system"], "记录结果、时间和证据"),
      uc("ORD-06", "查询订单聚合视图", ["client", "operations"], "展示付款、退款与履约"),
      uc("ORD-07", "取消未付款订单", ["operations"], "不产生财务分录"),
      uc("ORD-08", "投影退款与售后状态", ["client", "operations"], "订单视图保持最新"),
    ],
    provides: ["Create Pending Order", "订单查询", "履约确认", "履约证据"],
    consumes: ["CreateOrder Request", "PaymentSucceeded", "RefundSucceeded"],
    publishes: ["OrderCreated", "FulfillmentRequested", "OrderFulfilled", "FulfillmentFailed"],
  },
  {
    code: "PAYMENT",
    name: "Payment System",
    responsibility: "统一支付状态机和渠道执行，不计算可结算余额。",
    sourceOfTruth: "Payment / Refund",
    useCases: [
      uc("PAY-01", "创建幂等 Payment Intent", ["client-system"], "一项业务支付只有一个逻辑意图"),
      uc("PAY-02", "发起渠道支付会话", ["consumer", "acquirer"], "返回跳转、Token 或 3DS 动作"),
      uc("PAY-03", "处理授权、扣款与失败", ["acquirer"], "映射统一内部状态"),
      uc("PAY-04", "接收渠道 Webhook", ["acquirer"], "验签、留档、去重并异步处理"),
      uc("PAY-05", "主动查询不确定支付", ["operations", "acquirer"], "解决 TIMEOUT/UNKNOWN"),
      uc("PAY-06", "发起全额或部分退款", ["client", "operations"], "创建幂等 Refund"),
      uc("PAY-07", "处理退款最终结果", ["acquirer"], "发布确定退款事实"),
      uc("PAY-08", "执行撤销或 Void", ["operations"], "清算前取消交易"),
      uc("PAY-09", "保存渠道原始响应", ["acquirer"], "支持调查、审计和对账"),
      uc("PAY-10", "执行支付交易级对账", ["finance", "acquirer"], "内部状态与渠道明细一致"),
    ],
    provides: ["Create/Query Payment", "Create/Query Refund", "Provider Webhook"],
    consumes: ["RouteSelected", "RiskApproved"],
    publishes: ["PaymentAuthorized", "PaymentSucceeded", "PaymentFailed", "RefundSucceeded", "PaymentMismatchDetected"],
  },
  {
    code: "CHANNEL",
    name: "Channel & MID System",
    responsibility: "管理 Provider、MID、支付方式、卡组织和渠道能力。",
    sourceOfTruth: "Provider / MID Capability",
    useCases: [
      uc("CHN-01", "登记收单 Provider", ["operations"], "创建 NI、Geidea 等档案"),
      uc("CHN-02", "配置渠道 Adapter 与凭证引用", ["operations"], "绑定能力和安全凭证"),
      uc("CHN-03", "登记 MID 与 Operating Entity", ["operations"], "MID 具备明确法律主体"),
      uc("CHN-04", "配置 MCC、币种、地区和限额", ["operations"], "形成可执行能力矩阵"),
      uc("CHN-05", "配置支付方式与 Scheme", ["operations"], "Card/Wallet 与 Visa/mada 分层"),
      uc("CHN-06", "启用、暂停或关闭 MID", ["operations"], "实时影响新交易资格"),
      uc("CHN-07", "维护渠道预计成本", ["finance"], "保存成本策略版本"),
      uc("CHN-08", "监测渠道健康与额度", ["operations"], "为路由提供实时信号"),
    ],
    provides: ["Provider 能力", "MID 资格", "健康度", "成本配置"],
    consumes: ["ChannelCallbackObserved"],
    publishes: ["MidActivated", "MidSuspended", "ChannelHealthChanged", "ChannelCostChanged"],
  },
  {
    code: "ROUTING",
    name: "Routing System",
    responsibility: "只在已获批候选 MID 中选择，不突破主体、MCC、网站和业务范围。",
    sourceOfTruth: "Routing Decision",
    useCases: [
      uc("RTE-01", "获取合规候选 MID", ["client-system"], "过滤无资格渠道"),
      uc("RTE-02", "按币种、地区、MCC 和支付方式过滤", ["client-system"], "只保留能力匹配 MID"),
      uc("RTE-03", "应用 Client 与业务政策允许列表", ["client-system"], "根据订单快照阻止未获批业务"),
      uc("RTE-04", "应用限额与风险约束", ["operations"], "控制交易和风险暴露"),
      uc("RTE-05", "按优先级、成功率与成本排序", ["operations"], "选择最佳合法候选"),
      uc("RTE-06", "保存路由决策快照", ["operations"], "候选和淘汰原因可解释"),
      uc("RTE-07", "执行受控失败切换", ["acquirer"], "仅在允许条件下尝试下一渠道", "later"),
      uc("RTE-08", "模拟并解释路由", ["operations"], "上线前验证配置"),
    ],
    provides: ["Route Payment", "Explain Route", "Simulate Policy"],
    consumes: ["ChannelHealthChanged", "MidSuspended", "RiskRestrictionApplied"],
    publishes: ["RouteSelected", "NoEligibleRoute"],
  },
  {
    code: "RISK",
    name: "Risk & Dispute System",
    responsibility: "处理交易风险、限额、人工案件与拒付，不替代身份合规。",
    sourceOfTruth: "Risk Decision / Dispute",
    useCases: [
      uc("RSK-01", "执行 Checkout 资格预检查", ["client-system"], "阻止无资格交易"),
      uc("RSK-02", "执行实时交易风险评估", ["consumer"], "返回 ALLOW、REVIEW 或 DENY"),
      uc("RSK-03", "运行金额、频率、设备与名单规则", ["consumer"], "生成规则命中和风险分"),
      uc("RSK-04", "创建人工审核 Case", ["operations"], "冻结动作等待决定"),
      uc("RSK-05", "批准、拒绝或限制交易", ["operations"], "形成可审计最终决定"),
      uc("RSK-06", "配置 Client、业务分类与 MID 限额", ["operations"], "风险政策版本化且不 Hard Code 行业"),
      uc("RSK-07", "登记拒付并冻结余额", ["acquirer", "finance"], "创建 Dispute"),
      uc("RSK-08", "收集履约证据", ["client"], "形成举证材料包"),
      uc("RSK-09", "提交、跟踪并结案拒付", ["operations", "acquirer"], "记录 WON/LOST 和损失"),
    ],
    provides: ["Risk Evaluate", "Limit Check", "Case", "Dispute"],
    consumes: ["CheckoutCreated", "PaymentSucceeded", "DisputeNoticeReceived"],
    publishes: ["RiskApproved", "RiskDenied", "DisputeOpened", "DisputeWon", "DisputeLost", "ReserveHoldRequested"],
  },
  {
    code: "ECONOMICS",
    name: "Fee & Revenue Share System",
    responsibility: "计费与分润计算负责算钱，不直接写账本或移动资金。",
    sourceOfTruth: "Transaction Economics Snapshot",
    useCases: [
      uc("ECO-01", "加载交易时点费率版本", ["finance"], "锁定 Client、Partner、渠道和主体适用条款"),
      uc("ECO-02", "计算 Client 百分比和固定费用", ["finance"], "确认平台服务费收入与 Client 应付"),
      uc("ECO-03", "计算 Local Partner 分润", ["finance", "partner"], "按合作方独立策略确认 Partner 应付"),
      uc("ECO-04", "计算渠道预计成本", ["finance"], "根据实际路由、MID 和支付方式形成预计成本"),
      uc("ECO-05", "生成多方交易分配", ["finance"], "记录 Client 应付、Partner 应付、平台收入、税费和预计 Provider 成本"),
      uc("ECO-06", "接收并替换实际成本", ["finance", "acquirer"], "保留预计成本、实际成本及差异"),
      uc("ECO-07", "计算交易预计与实际利润", ["finance"], "利润由平台收入减成本得到，不直接记利润账户"),
      uc("ECO-08", "生成初始或调整 Accounting Instruction", ["finance"], "成本差异生成新的 Accounting Instruction，并引用原计算快照"),
    ],
    provides: ["Calculate Economics", "Explain Calculation", "Issue Accounting Instruction"],
    consumes: ["PaymentSucceeded", "RefundSucceeded", "DisputeLost", "ActualChannelCostConfirmed"],
    publishes: ["EconomicsCalculated", "AccountingInstructionIssued", "EconomicsAdjusted"],
  },
  {
    code: "LEDGER",
    name: "Ledger & Balance System",
    responsibility: "复式账本与余额负责记账和守恒，不决定合同费率及分润公式。",
    sourceOfTruth: "Journal / Ledger Account",
    useCases: [
      uc("LED-01", "建立法律主体独立账簿", ["finance"], "每个 Operating Entity 与币种拥有独立账簿和科目"),
      uc("LED-02", "校验并提交记账指令", ["finance"], "同一主体、同一币种的借贷金额保持平衡"),
      uc("LED-03", "支付成功复式入账", ["finance"], "记录渠道待结算款、Client 应付、Partner 应付和平台收入"),
      uc("LED-04", "记录渠道实际成本", ["finance"], "银行实收、渠道成本和待结算款完成闭环"),
      uc("LED-05", "记录跨主体往来", ["finance", "partner"], "使用应收、应付往来科目而非混用账簿"),
      uc("LED-06", "退款、拒付与费用反向入账", ["finance"], "追加冲销或调整分录，不修改历史"),
      uc("LED-07", "建立、释放或使用准备金", ["finance", "operations"], "Pending、Available、Reserved 和 Negative 可解释"),
      uc("LED-08", "执行受控人工调整", ["finance"], "双人审批后追加分录"),
      uc("LED-09", "查询权威余额与账本", ["client", "finance"], "按法律主体、账户和币种追溯"),
    ],
    provides: ["Post Journal", "Get Balance", "Get Ledger", "Reserve Balance"],
    consumes: ["AccountingInstructionIssued", "ApprovedManualJournal"],
    publishes: ["LedgerPosted", "BalanceChanged", "ReserveReleased"],
  },
  {
    code: "RECONCILIATION",
    name: "Finance Reconciliation System",
    responsibility: "匹配渠道清算、银行到账和内部交易，确认实际成本并推动差异调整。",
    sourceOfTruth: "Reconciliation Batch / Match Result",
    useCases: [
      uc("REC-01", "导入渠道清算明细", ["finance", "acquirer"], "保存交易、退款、手续费和渠道净额"),
      uc("REC-02", "导入银行到账明细", ["finance"], "形成实际入账资金记录"),
      uc("REC-03", "匹配支付与清算交易", ["finance"], "识别成功、缺失、重复和金额差异"),
      uc("REC-04", "确认实际渠道成本", ["finance", "acquirer"], "输出实际手续费、附加费与汇兑成本"),
      uc("REC-05", "比较预计成本与实际成本", ["finance"], "生成可解释的成本差异"),
      uc("REC-06", "处理短款和未匹配项", ["finance", "operations"], "创建调查任务或调整建议"),
      uc("REC-07", "关闭对账批次", ["finance"], "全部差异已处理且结果可审计"),
    ],
    provides: ["Import Statement", "Match Transaction", "Confirm Actual Cost"],
    consumes: ["PaymentSucceeded", "RefundSucceeded", "PaymentMismatchDetected"],
    publishes: ["ActualChannelCostConfirmed", "ReconciliationMismatchDetected", "ReconciliationClosed"],
  },
  {
    code: "SETTLEMENT",
    name: "Settlement System",
    responsibility: "将可结算余额转成 Client 与合作方付款义务，第一版不自动移动资金。",
    sourceOfTruth: "Settlement",
    useCases: [
      uc("SET-01", "查询可结算余额", ["client", "finance"], "区分 Pending、Available、Reserved 与 Negative"),
      uc("SET-02", "创建 Client 结算单", ["finance"], "锁定本期交易和调整"),
      uc("SET-03", "校验最低额、冻结与负余额", ["finance"], "阻止不合格结算"),
      uc("SET-04", "审批结算单", ["finance", "operations"], "进入待线下付款"),
      uc("SET-05", "登记人工付款和银行费用", ["finance"], "记录实际金额、日期和渠道"),
      uc("SET-06", "上传付款凭证", ["finance"], "形成可审计证据"),
      uc("SET-07", "完成、失败或撤销结算", ["finance"], "驱动对应账本分录"),
      uc("SET-08", "创建合作方分润结算单", ["finance", "partner"], "与 Client 结算分开核算"),
      uc("SET-09", "生成结算明细", ["client"], "解释订单、费用、退款和准备金"),
    ],
    provides: ["Create/Approve/Complete Settlement", "Settlement Statement"],
    consumes: ["BalanceChanged", "ReserveReleased"],
    publishes: ["SettlementCreated", "SettlementPaid", "SettlementFailed", "AccountingInstructionIssued"],
  },
  {
    code: "PLATFORM",
    name: "Platform Support",
    responsibility: "提供认证、通知、文件、审计、配置与报表，不拥有核心业务状态。",
    sourceOfTruth: "Supporting Records",
    useCases: [
      uc("PLT-01", "统一用户与 API Key 授权", ["client", "operations"], "资源访问受到控制"),
      uc("PLT-02", "签名投递、重试与回放 Webhook", ["client-system"], "可靠通知客户系统"),
      uc("PLT-03", "发送消费者与 Client 通知", ["consumer", "client"], "多语言消息可追踪"),
      uc("PLT-04", "保存 KYC、合同和证据文件", ["compliance", "finance"], "加密且权限隔离"),
      uc("PLT-05", "记录操作和敏感访问审计", ["operations", "compliance"], "形成不可抵赖轨迹"),
      uc("PLT-06", "配置草稿、审批、生效与回滚", ["operations"], "白名单与规则不 Hard Code"),
      uc("PLT-07", "聚合只读经营报表", ["operations", "finance"], "不反向修改核心数据"),
    ],
    provides: ["IAM", "Webhook", "Notification", "File", "Audit", "Configuration", "Reporting"],
    consumes: ["All Published Business Events"],
    publishes: ["WebhookDelivered", "NotificationSent", "ConfigurationActivated"],
  },
];

export const COARSE_ARCHITECTURE = [
  { name: "Client & Contract", items: ["Client", "Onboarding", "Contract", "Business Profile", "Operating Entity", "Acceptance Policy"] },
  { name: "Commerce", items: ["Checkout Session", "Order Snapshot", "Consumer", "Order", "Fulfillment", "Refund"] },
  { name: "Payment Orchestration", items: ["Provider Registry", "Merchant Account", "Routing", "Execution", "Callback", "Payment Reconciliation"] },
  { name: "Risk & Compliance", items: ["Client Risk", "Business Policy", "Transaction Risk", "Decision", "Case", "Dispute"] },
  { name: "Finance", items: ["Fee Calculation", "Revenue Share", "Accounting Instruction", "Ledger & Balance", "Settlement", "Profitability", "Tax", "Reconciliation"] },
  { name: "Platform", items: ["IAM", "Client Webhook", "Notification", "Configuration", "Audit", "File & Evidence"] },
];

export const DECISIONS = [
  "一个阿联酋 Operating Entity 先运行，数据模型预留多主体。",
  "Client 可以是中国个人，不在核心模型中强调具体行业或作者身份。",
  "采用平台托管跳转式 Checkout。",
  "第一版只开放 USD，一次性支付，金额模型原生支持多币种。",
  "接入多个 Provider/MID，但路由不得突破其获批能力。",
  "Client 和内部运营均可发起全额或部分退款。",
  "第一版人工结算；系统计算、审批、回填线下付款凭证。",
  "Client 收费为百分比加固定金额；Local Partner 分润独立配置。",
  "行业仅通过配置白名单与政策管理，不在代码中 Hard Code。",
  "第一版不创建 Offering；Client System 下单时传入商品、金额和币种，系统保存不可变订单快照。",
  "计费与分润负责计算，复式账本只执行平衡记账，两者职责分离。",
  "每个法律主体和币种使用独立账簿，跨主体通过往来科目处理。",
  "渠道成本区分预计成本与实际成本，差异通过新记账指令和调整分录确认。",
  "平台利润由平台收入减实际成本得到，不直接记入交易利润账户。",
  "技术栈计划为 Java 微服务、Nacos 和 Spring Cloud，但本阶段不展开。",
];

export const OPEN_QUESTIONS = [
  "主交易状态机及异常状态恢复。",
  "各系统同步调用与异步事件边界。",
  "税务计算与开票是否形成独立领域。",
  "拒付是否从 Risk 中独立。",
  "多方费用的舍入规则及尾差归属。",
  "多 Operating Entity 的主体选择规则。",
  "自动结算及跨境付款供应商。",
  "订阅、周期扣款和账期变更。",
  "具体 Java 微服务技术架构与部署方案。",
];

export const TRANSACTION_FLOW = [
  "Client 注册",
  "KYC / 审核",
  "签署发行协议",
  "下单并提交商品 / 金额",
  "Order 保存不可变快照",
  "创建可支付 Checkout",
  "消费者付款",
  "Client 履约",
  "退款 / 拒付",
  "计费分润 / 复式记账",
  "渠道对账 / 成本确认",
  "人工结算",
];

export const FINANCE_COMPONENTS = [
  "支付 / 退款 / 拒付事实",
  "合同与费率版本快照",
  "计费与分润计算",
  "Accounting Instruction",
  "复式账本与余额",
  "渠道清算与银行对账",
  "预计成本 → 实际成本",
  "收入减成本 → 实际利润",
];
