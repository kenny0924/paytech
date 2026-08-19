export type Status = "confirmed" | "proposed" | "mvp" | "later" | "open";
export type ContentView = "architecture" | "use-cases";

export interface NavigationItem {
  id: string;
  label: string;
  group: ContentView;
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

export interface InterfaceUseCase {
  id: string;
  title: string;
  consumers: string[];
  result: string;
  status: Status;
}

export interface FunctionalSystemDefinition {
  code: string;
  name: string;
  responsibility: string;
  sourceOfTruth: string;
  internalDomains: string[];
  useCases: UseCase[];
  interfaceUseCases: InterfaceUseCase[];
  dependsOn: string[];
  subscribes: string[];
  publishes: string[];
  exclusions: string[];
}

export interface EntryApplicationDefinition {
  code: string;
  name: string;
  audience: string;
  purpose: string;
  kind: "human" | "machine";
  status: Status;
  capabilities: string[];
  restrictions: string[];
  connectsTo: string[];
}

export const NAV_ITEMS: NavigationItem[] = [
  { id: "overview", label: "项目概览", group: "architecture" },
  { id: "business-model", label: "业务模型", group: "architecture" },
  { id: "architecture", label: "原始架构图", group: "architecture" },
  { id: "domain-map", label: "细化领域地图", group: "architecture" },
  { id: "end-to-end", label: "端到端流程", group: "architecture" },
  { id: "entry-applications", label: "入口应用", group: "architecture" },
  { id: "payment-model", label: "支付模型", group: "architecture" },
  { id: "finance-model", label: "财务模型", group: "architecture" },
  { id: "decisions", label: "已确认决策", group: "architecture" },
  { id: "open-questions", label: "待讨论事项", group: "architecture" },
  { id: "use-cases", label: "全局用例", group: "use-cases" },
  { id: "systems", label: "系统用例", group: "use-cases" },
];

export const ACTORS: Actor[] = [
  { id: "client", name: "Client", description: "通过平台发行商品、创建 Checkout、发起退款并收取结算款的个人或企业。" },
  { id: "consumer", name: "Consumer", description: "在托管 Checkout 中付款并接受 Operating Entity 条款的最终消费者。" },
  { id: "operations", name: "Operations", description: "执行客户、业务准入、渠道、退款和结算运营。" },
  { id: "compliance", name: "Compliance", description: "负责 KYC/KYB、制裁筛查、复审和合规处置。" },
  { id: "finance", name: "Finance", description: "处理计费分润、账本、成本对账、准备金和人工付款。" },
  { id: "partner", name: "Local Partner", description: "提交当地主体资料并按照独立合作协议获得分润的合作方。" },
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

const iuc = (id: string, title: string, consumers: string[], result: string, status: Status = "mvp"): InterfaceUseCase => ({ id, title, consumers, result, status });

export const FUNCTIONAL_SYSTEMS: FunctionalSystemDefinition[] = [
  {
    code: "CLIENT_CONTRACT",
    name: "Client & Contract System",
    responsibility: "管理交易参与方、主体关系、商业协议及其版本化费率条款。",
    sourceOfTruth: "Client / Operating Entity / Partner / Agreement",
    internalDomains: ["Client Account", "Operating Entity", "Local Partner", "Agreement", "Commercial Terms"],
    useCases: [
      uc("CCT-01", "注册个人或企业 Client", ["client"], "创建待审核 Client 主档"),
      uc("CCT-02", "维护 Client 资料与业务状态", ["client", "operations"], "资料和状态变更可审计"),
      uc("CCT-03", "暂停、恢复或终止 Client", ["operations"], "实时影响新交易资格"),
      uc("CCT-04", "维护 Operating Entity", ["operations"], "记录法律主体及可经营状态"),
      uc("CCT-05", "维护 Local Partner", ["operations"], "建立合作方主档"),
      uc("CCT-06", "建立参与方关系", ["operations"], "关联 Client、Operating Entity 与 Local Partner"),
      uc("CCT-07", "创建和管理 Client 协议", ["client", "operations"], "协议完成签署、变更和终止"),
      uc("CCT-08", "创建和管理 Partner 协议", ["partner", "operations"], "合作公司分润约定版本化"),
      uc("CCT-09", "配置商业费率及生效期", ["finance", "operations"], "Client 费用与 Partner 分润条款可追溯"),
      uc("CCT-10", "接收和审核 Partner 主体资料", ["partner", "operations"], "形成可用于 Provider 开户的已审核资料包"),
    ],
    interfaceUseCases: [
      iuc("CCT-I01", "查询 Client 基本信息", ["Commerce", "Risk & Compliance", "Finance"], "返回权威 Client 主档"),
      iuc("CCT-I02", "检查 Client 交易状态", ["Commerce", "Payment Orchestration"], "返回是否允许创建新交易"),
      iuc("CCT-I03", "查询可用 Operating Entity", ["Commerce", "Payment Orchestration"], "返回具备商业关系的候选主体"),
      iuc("CCT-I04", "查询交易适用协议", ["Commerce", "Finance"], "返回交易时点唯一有效协议"),
      iuc("CCT-I05", "查询 Client 费率条款", ["Finance"], "返回百分比、固定金额及版本"),
      iuc("CCT-I06", "查询 Partner 分润条款", ["Finance"], "返回合作方独立分润版本"),
      iuc("CCT-I07", "查询主体与合作方关系", ["Payment Orchestration", "Finance"], "返回权威参与方关系"),
      iuc("CCT-I08", "提交 Partner 主体资料", ["Partner Console"], "形成待内部审核的主体资料申请"),
      iuc("CCT-I09", "查询 Partner 主体及开户进度", ["Partner Console"], "返回资料审核、开户和主体状态"),
    ],
    dependsOn: ["Platform Support：IAM、文件和审计能力"],
    subscribes: ["KycApproved", "KycRejected", "ComplianceRestricted"],
    publishes: ["ClientCreated", "ClientSuspended", "AgreementActivated", "AgreementChanged", "OperatingEntityChanged", "PartnerAgreementChanged"],
    exclusions: ["不作出 KYC/KYB 结论", "不执行交易风控", "不选择 MID", "不计算费用或写账"],
  },
  {
    code: "COMMERCE",
    name: "Commerce System",
    responsibility: "管理消费者交易合同、Checkout、不可变订单快照、履约和退款业务申请。",
    sourceOfTruth: "Checkout Session / Order / Fulfillment / Refund Request",
    internalDomains: ["Checkout Session", "Order Snapshot", "Order", "Fulfillment", "Refund Request", "Consumer Context"],
    useCases: [
      uc("COM-01", "下单时传入商品与金额", ["client-system"], "接收外部订单号、商品、金额、币种和回调信息"),
      uc("COM-02", "执行下单幂等校验", ["client-system"], "相同外部订单和幂等键不会重复创建"),
      uc("COM-03", "保存不可变订单快照", ["client-system"], "锁定商品、数量、金额、卖方和协议"),
      uc("COM-04", "创建和管理 Checkout Session", ["client-system", "consumer"], "Order 持久化成功后才创建可支付 Session"),
      uc("COM-05", "过期或取消未付款 Checkout", ["consumer", "operations"], "结束会话且不产生支付事实"),
      uc("COM-06", "接收支付结果并更新订单", ["client-system"], "订单支付状态与 Payment 事实一致"),
      uc("COM-07", "请求 Client System 履约", ["client-system"], "发送可重试履约通知"),
      uc("COM-08", "记录履约结果和证据", ["client-system"], "保存成功、失败、时间和证据引用"),
      uc("COM-09", "校验并创建退款申请", ["client", "operations"], "全额或部分退款不超过可退金额"),
    ],
    interfaceUseCases: [
      iuc("COM-I01", "创建 Checkout", ["Client System", "入口应用"], "返回 checkoutUrl、orderId 和过期时间"),
      iuc("COM-I02", "查询 Checkout", ["Client System", "入口应用"], "返回统一会话状态"),
      iuc("COM-I03", "查询订单聚合视图", ["Client System", "Client Console", "入口应用"], "返回订单、支付、退款与履约投影"),
      iuc("COM-I04", "查询不可变订单快照", ["Payment Orchestration", "Risk & Compliance", "Finance"], "返回交易时点商品与金额事实"),
      iuc("COM-I05", "确认履约结果", ["Client System"], "更新履约状态并保存证据"),
      iuc("COM-I06", "创建退款申请", ["Client System", "Client Console", "Operations Console"], "生成已校验的退款业务请求"),
      iuc("COM-I07", "查询可退款金额", ["Client System", "Client Console", "Operations Console"], "返回当前最大可退金额"),
      iuc("COM-I08", "查询履约证据", ["Risk & Compliance", "Operations Console"], "返回履约状态、时间和受控证据引用"),
    ],
    dependsOn: ["Client & Contract：客户、主体和协议资格", "Risk & Compliance：Checkout 资格", "Payment Orchestration：支付和渠道退款执行"],
    subscribes: ["ClientSuspended", "ComplianceRestricted", "PaymentSucceeded", "PaymentFailed", "RefundSucceeded", "RefundFailed"],
    publishes: ["OrderCreated", "CheckoutCreated", "CheckoutExpired", "OrderPaid", "FulfillmentRequested", "OrderFulfilled", "RefundRequestCreated"],
    exclusions: ["不执行渠道支付", "不选择 MID", "不拥有支付最终状态", "不维护账本或结算余额"],
  },
  {
    code: "PAYMENT",
    name: "Payment Orchestration System",
    responsibility: "管理 Provider、MID、支付路由以及支付和渠道退款的完整执行生命周期。",
    sourceOfTruth: "Provider / MID / Routing Decision / Payment / Channel Refund",
    internalDomains: ["Provider Registry", "MID Capability", "Payment Routing", "Payment Intent", "Payment Attempt", "Channel Refund"],
    useCases: [
      uc("PAY-01", "登记 Provider 与渠道 Adapter", ["operations"], "建立 NI、Geidea 等渠道能力"),
      uc("PAY-02", "登记 MID 及其 Operating Entity", ["operations"], "MID 绑定唯一法律主体"),
      uc("PAY-03", "配置 MID 能力和限额", ["operations"], "维护 MCC、币种、地区、支付方式和限额"),
      uc("PAY-04", "配置、模拟和解释路由规则", ["operations"], "只在合格候选 MID 中排序"),
      uc("PAY-05", "创建幂等 Payment Intent", ["client-system"], "一项业务支付只有一个逻辑意图"),
      uc("PAY-06", "执行授权、扣款和 3DS", ["consumer", "acquirer"], "完成渠道支付动作"),
      uc("PAY-07", "接收并处理渠道回调", ["acquirer"], "验签、留档、去重和统一状态映射"),
      uc("PAY-08", "主动查询不确定支付", ["operations", "acquirer"], "解决 TIMEOUT 或 UNKNOWN"),
      uc("PAY-09", "执行已批准的 Void 或渠道退款", ["operations", "acquirer"], "必须引用 Commerce refundRequestId，并生成可追踪渠道结果"),
      uc("PAY-10", "执行受控失败切换", ["acquirer"], "仅在业务和渠道允许时尝试下一候选", "later"),
      uc("PAY-11", "保存渠道原始交互", ["acquirer"], "支持审计、调查和对账"),
      uc("PAY-12", "监测渠道健康度", ["operations"], "为路由提供实时可用性信号"),
    ],
    interfaceUseCases: [
      iuc("PAY-I01", "创建支付意图", ["Commerce"], "返回 paymentIntentId"),
      iuc("PAY-I02", "发起支付", ["Hosted Checkout"], "返回跳转、Token 或 3DS 动作"),
      iuc("PAY-I03", "查询支付", ["Commerce", "Operations Console"], "返回统一支付及尝试状态"),
      iuc("PAY-I04", "执行渠道退款", ["Commerce"], "校验 refundRequestId 并创建幂等 Channel Refund"),
      iuc("PAY-I05", "查询渠道退款", ["Commerce", "Operations Console"], "返回最终或处理中状态"),
      iuc("PAY-I06", "查询可用支付方式", ["Hosted Checkout", "Commerce"], "返回当前交易可展示方式"),
      iuc("PAY-I07", "模拟并解释路由", ["Operations Console"], "返回候选、排序和淘汰原因"),
      iuc("PAY-I08", "接收 Provider Webhook", ["Provider"], "可靠接收外部渠道通知"),
      iuc("PAY-I09", "查询 Partner 授权的 MID", ["Partner Console"], "按 Partner 与主体关系返回只读 MID 列表"),
      iuc("PAY-I10", "查询 Partner 授权的支付交易", ["Partner Console"], "返回脱敏且不可操作的逐笔交易明细"),
    ],
    dependsOn: ["Commerce：订单快照和退款业务请求", "Client & Contract：主体关系", "Risk & Compliance：交易风险决定"],
    subscribes: ["ClientSuspended", "ComplianceRestricted"],
    publishes: ["PaymentAuthorized", "PaymentSucceeded", "PaymentFailed", "PaymentUnknown", "RefundSucceeded", "RefundFailed", "DisputeNoticeReceived", "RouteSelected", "ChannelHealthChanged"],
    exclusions: ["不拥有订单", "不批准退款资格", "不作出 KYC 决定", "不确认实际渠道成本或客户余额"],
  },
  {
    code: "RISK_COMPLIANCE",
    name: "Risk & Compliance System",
    responsibility: "管理客户合规资格、配置化业务准入、实时交易风险和争议案件。",
    sourceOfTruth: "KYC Case / Compliance Decision / Risk Decision / Dispute",
    internalDomains: ["KYC/KYB", "Business Policy", "Transaction Risk", "Limit", "Case Management", "Dispute"],
    useCases: [
      uc("RCO-01", "创建和审核 KYC/KYB 申请", ["client", "compliance"], "形成可审计身份审核决定"),
      uc("RCO-02", "执行制裁、PEP 和负面信息筛查", ["compliance"], "生成命中、风险等级和处置"),
      uc("RCO-03", "执行持续复审", ["compliance"], "更新有效期和持续风险状态"),
      uc("RCO-04", "配置业务分类白名单", ["operations", "compliance"], "行业和材料要求完全配置化"),
      uc("RCO-05", "配置 Client、主体、MID 和交易限额", ["operations"], "限额策略版本化"),
      uc("RCO-06", "执行 Checkout 资格检查", ["client-system"], "无资格交易在付款前被阻止"),
      uc("RCO-07", "执行实时交易风险评估", ["consumer"], "返回 ALLOW、REVIEW 或 DENY"),
      uc("RCO-08", "处理人工审核案件", ["operations"], "形成最终风险处置"),
      uc("RCO-09", "登记和管理拒付", ["operations", "acquirer"], "跟踪 OPEN、WON、LOST 和 CLOSED"),
      uc("RCO-10", "收集并提交拒付证据", ["client", "operations"], "形成可追踪举证材料包"),
    ],
    interfaceUseCases: [
      iuc("RCO-I01", "查询 Client 合规状态", ["Client & Contract", "Commerce"], "返回当前 KYC/KYB 资格"),
      iuc("RCO-I02", "检查业务是否允许交易", ["Commerce", "Payment Orchestration"], "基于配置化政策返回准入结果"),
      iuc("RCO-I03", "执行 Checkout 资格检查", ["Commerce"], "返回允许或拒绝及原因"),
      iuc("RCO-I04", "执行实时交易风险评估", ["Payment Orchestration"], "返回风险决定和命中规则"),
      iuc("RCO-I05", "检查和占用交易限额", ["Payment Orchestration"], "原子返回限额结果"),
      iuc("RCO-I06", "查询风险决定", ["Commerce", "Operations Console"], "返回决策和审计轨迹"),
      iuc("RCO-I07", "提交拒付证据", ["Client System", "Operations Console"], "保存并关联争议案件"),
      iuc("RCO-I08", "查询争议案件", ["Client System", "Finance"], "返回案件状态和最终结果"),
    ],
    dependsOn: ["Client & Contract：客户和协议主档", "Commerce：不可变订单快照和履约证据", "Platform Support：文件和审计"],
    subscribes: ["ClientCreated", "ClientSuspended", "PaymentSucceeded", "RefundSucceeded", "DisputeNoticeReceived"],
    publishes: ["KycApproved", "KycRejected", "ComplianceRestricted", "RiskApproved", "RiskDenied", "DisputeOpened", "DisputeWon", "DisputeLost"],
    exclusions: ["不维护 Client 主档", "不执行支付", "不直接冻结账务余额", "不计算或确认财务损失"],
  },
  {
    code: "FINANCE",
    name: "Finance System",
    responsibility: "管理交易经济计算、复式账本、余额准备金、渠道对账和人工结算义务。",
    sourceOfTruth: "Economics Snapshot / Journal / Balance / Reconciliation / Settlement",
    internalDomains: ["Fee & Revenue Share", "Accounting Instruction", "Ledger & Balance", "Reserve", "Reconciliation", "Settlement"],
    useCases: [
      uc("FIN-01", "加载交易时点费率版本", ["finance"], "锁定 Client、Partner、渠道和主体条款"),
      uc("FIN-02", "计算 Client 应付和平台收入", ["finance"], "应用百分比与固定费用"),
      uc("FIN-03", "计算 Local Partner 分润", ["finance", "partner"], "形成独立 Partner 应付"),
      uc("FIN-04", "计算预计渠道成本", ["finance"], "根据 MID、支付方式和渠道费率估算"),
      uc("FIN-05", "确认实际渠道成本及差异", ["finance", "acquirer"], "保留预计和实际成本，成本差异生成新的 Accounting Instruction"),
      uc("FIN-06", "计算预计和实际利润", ["finance"], "利润由平台收入减成本得到"),
      uc("FIN-07", "生成 Accounting Instruction", ["finance"], "所有业务事实转换为平衡记账指令"),
      uc("FIN-08", "执行不可变复式记账", ["finance"], "每个主体和币种借贷平衡"),
      uc("FIN-09", "管理多层余额和准备金", ["finance", "operations"], "区分 Pending、Available、Reserved 和 Negative"),
      uc("FIN-10", "处理退款、拒付和人工调整", ["finance"], "追加冲销或调整分录"),
      uc("FIN-11", "导入和匹配渠道清算", ["finance", "acquirer"], "识别交易、费用和渠道净额差异"),
      uc("FIN-12", "导入和匹配银行到账", ["finance"], "确认实际入账资金"),
      uc("FIN-13", "创建 Client 与 Partner 结算单", ["finance"], "锁定本期可结算交易和调整"),
      uc("FIN-14", "审批并登记人工付款", ["finance", "operations"], "记录实际付款和凭证"),
      uc("FIN-15", "接收和处理出款申请", ["client", "partner", "operations"], "仅基于申请方可用余额创建待审核结算申请"),
    ],
    interfaceUseCases: [
      iuc("FIN-I01", "查询 Client 余额", ["Client System", "Client Console"], "返回分类余额及币种"),
      iuc("FIN-I02", "查询 Partner 余额", ["Operations Console", "Partner Console"], "返回合作方应付余额"),
      iuc("FIN-I03", "查询交易费用明细", ["Commerce", "Operations Console"], "解释税费、分润、收入和成本"),
      iuc("FIN-I04", "查询交易利润", ["Operations Console"], "返回预计与实际利润"),
      iuc("FIN-I05", "查询账本流水", ["Client Console", "Operations Console"], "按账户、主体和币种追溯"),
      iuc("FIN-I06", "请求冻结或释放准备金", ["Risk & Compliance"], "执行余额变动并返回结果"),
      iuc("FIN-I07", "创建结算单", ["Operations Console"], "生成待审批结算义务"),
      iuc("FIN-I08", "查询结算单", ["Client Console", "Partner Console", "Operations Console"], "返回权限范围内的状态和明细"),
      iuc("FIN-I09", "登记人工付款", ["Operations Console"], "完成付款回填及凭证关联"),
      iuc("FIN-I10", "查询对账差异", ["Operations Console"], "返回未匹配和待处理项目"),
      iuc("FIN-I11", "提交 Partner 出款申请", ["Partner Console"], "仅基于 Partner 可用分润余额创建待审核申请"),
      iuc("FIN-I12", "查询 Partner 出款申请", ["Partner Console", "Operations Console"], "返回审批、付款和凭证状态"),
      iuc("FIN-I13", "提交 Client 出款申请", ["Client Console"], "仅基于 Client 可用余额创建待审核申请"),
      iuc("FIN-I14", "查询 Client 出款申请", ["Client Console", "Operations Console"], "返回审批、付款和凭证状态"),
    ],
    dependsOn: ["Client & Contract：费率、分润及主体关系", "Commerce：订单快照", "Risk & Compliance：争议案件查询"],
    subscribes: ["PaymentSucceeded", "RefundSucceeded", "DisputeLost"],
    publishes: ["EconomicsCalculated", "LedgerPosted", "BalanceChanged", "ReserveHeld", "ReserveReleased", "ActualChannelCostConfirmed", "ReconciliationMismatchDetected", "SettlementCreated", "SettlementPaid", "SettlementFailed"],
    exclusions: ["不维护商业协议", "不执行渠道支付", "不作出风险判断", "第一版不自动发起银行付款"],
  },
  {
    code: "PLATFORM",
    name: "Platform Support System",
    responsibility: "向其他五个系统提供通用身份、通知、文件、审计和配置能力，不拥有核心业务事实。",
    sourceOfTruth: "Identity / API Credential / Delivery / File / Audit Record",
    internalDomains: ["IAM", "API Key", "Client Webhook", "Notification", "File & Evidence", "Audit", "Configuration"],
    useCases: [
      uc("PLT-01", "管理用户、角色和权限", ["client", "operations"], "资源访问受到角色控制"),
      uc("PLT-02", "管理 Client API Key", ["client", "operations"], "完成创建、轮换和撤销"),
      uc("PLT-03", "管理 Client Webhook 地址", ["client"], "验证并启用回调终点"),
      uc("PLT-04", "订阅并转换业务 MQ 通知", ["client-system"], "生成面向 Client 的回调任务"),
      uc("PLT-05", "签名、投递、重试和回放 Webhook", ["client-system"], "可靠通知客户系统"),
      uc("PLT-06", "发送邮件及其他通知", ["consumer", "client"], "消息状态可追踪"),
      uc("PLT-07", "保存文件和证据", ["compliance", "finance"], "文件加密且权限隔离"),
      uc("PLT-08", "记录操作和敏感访问审计", ["operations", "compliance"], "形成不可抵赖轨迹"),
      uc("PLT-09", "管理通用配置生命周期", ["operations"], "支持草稿、审批、生效和回滚"),
    ],
    interfaceUseCases: [
      iuc("PLT-I01", "执行身份认证", ["入口应用", "全部业务系统"], "返回可信身份上下文"),
      iuc("PLT-I02", "执行权限校验", ["入口应用", "全部业务系统"], "返回资源操作授权结果"),
      iuc("PLT-I03", "校验 API Key", ["API Gateway", "全部业务系统"], "返回 Client 身份和权限"),
      iuc("PLT-I04", "保存和读取文件", ["Client & Contract", "Risk & Compliance", "Finance"], "返回受控文件引用"),
      iuc("PLT-I05", "写入审计记录", ["全部业务系统"], "保存操作主体、对象和上下文"),
      iuc("PLT-I06", "查询审计记录", ["Operations Console"], "返回权限过滤后的审计轨迹"),
      iuc("PLT-I07", "管理 Webhook 订阅", ["Client Console"], "配置事件订阅关系"),
      iuc("PLT-I08", "查询 Webhook 投递状态", ["Client Console", "Operations Console"], "返回尝试和最终结果"),
      iuc("PLT-I09", "请求发送通知", ["全部业务系统"], "创建可追踪通知任务"),
    ],
    dependsOn: [],
    subscribes: ["其他五个系统发布的业务事实通知"],
    publishes: ["WebhookDelivered", "WebhookFailed", "NotificationSent", "FileStored", "ConfigurationActivated"],
    exclusions: ["不作出业务准入或风险决定", "不修改订单或支付", "不计算费用", "不维护账本和余额"],
  },
];

export const ENTRY_APPLICATIONS: EntryApplicationDefinition[] = [
  {
    code: "HOSTED_CHECKOUT",
    name: "Hosted Checkout",
    audience: "Consumer",
    purpose: "消费者完成付款并查看当前支付结果的托管页面。",
    kind: "human",
    status: "mvp",
    capabilities: ["展示订单、金额、币种和交易主体", "选择支付方式并完成 3DS 或渠道跳转", "展示支付处理中、成功、失败和取消状态"],
    restrictions: ["不以 successUrl 确认最终支付成功", "不拥有订单或支付事实"],
    connectsTo: ["Commerce", "Payment Orchestration", "Risk & Compliance", "Platform Support"],
  },
  {
    code: "CLIENT_CONSOLE",
    name: "Client Console",
    audience: "Client",
    purpose: "Client 查询交易、管理接入并提交退款或结算申请的管控台。",
    kind: "human",
    status: "mvp",
    capabilities: ["查询订单、支付、退款和履约历史", "发起全额或部分退款", "查看余额并下载结算单和付款凭证", "提交 Client 出款申请", "管理 API Key、Webhook 和团队权限"],
    restrictions: ["只能访问当前 Client 的授权数据", "不直接修改支付状态或账本分录"],
    connectsTo: ["Client & Contract", "Commerce", "Risk & Compliance", "Finance", "Platform Support"],
  },
  {
    code: "PARTNER_CONSOLE",
    name: "Partner Console",
    audience: "Local Partner",
    purpose: "合作方提交主体资料，并查询其授权主体、MID、交易和分润。",
    kind: "human",
    status: "mvp",
    capabilities: ["提交主体信息与开户材料", "查看主体审核和 MID 开户进度", "查询关联主体、MID 和脱敏交易明细", "查询分润余额与结算单", "提交 Partner 出款申请"],
    restrictions: ["仅限已形成的 Partner 可用分润余额", "禁止操作订单、支付、退款、路由和账本", "不能启停 MID 或修改渠道凭证"],
    connectsTo: ["Client & Contract", "Risk & Compliance", "Payment Orchestration", "Finance", "Platform Support"],
  },
  {
    code: "CLIENT_API",
    name: "Client API & API Gateway",
    audience: "Client System",
    purpose: "客户后端创建交易、查询状态并接收平台通知的机器入口。",
    kind: "machine",
    status: "mvp",
    capabilities: ["创建 Checkout 并查询订单和支付", "创建退款申请并查询退款", "查询结算状态", "认证、限流、幂等和 Webhook 投递"],
    restrictions: ["Gateway 只执行接入控制和路由", "不拥有任何核心业务事实"],
    connectsTo: ["Client & Contract", "Commerce", "Payment Orchestration", "Risk & Compliance", "Finance", "Platform Support"],
  },
  {
    code: "OPERATIONS_CONSOLE",
    name: "Operations Console",
    audience: "Operations / Compliance / Finance",
    purpose: "平台内部统一处理客户、主体、MID、风险、支付和财务运营。",
    kind: "human",
    status: "later",
    capabilities: ["审核主体资料并推进 Provider 开户", "管理 MID、路由和渠道配置", "处理退款、拒付和异常交易", "执行对账、准备金、结算审批和付款回填", "查询操作与敏感访问审计"],
    restrictions: ["按岗位和资源执行最小权限控制", "高风险财务操作需要双人审批"],
    connectsTo: ["全部六个核心系统"],
  },
  {
    code: "PROVIDER_ENDPOINT",
    name: "Provider Callback Endpoint",
    audience: "Provider / Acquirer",
    purpose: "可靠接收渠道回调、拒付通知和清算数据的机器入口。",
    kind: "machine",
    status: "mvp",
    capabilities: ["接收支付与退款结果回调", "接收拒付和渠道状态通知", "导入清算文件或调用清算 API", "触发不确定交易主动查询"],
    restrictions: ["所有通知必须验签、去重并留存原文", "外部状态必须映射为内部统一事实"],
    connectsTo: ["Payment Orchestration", "Risk & Compliance", "Finance", "Platform Support"],
  },
  {
    code: "CONSUMER_SERVICE",
    name: "Consumer Service Portal",
    audience: "Consumer",
    purpose: "消费者自助查询订单、收据和退款进度的售后入口。",
    kind: "human",
    status: "later",
    capabilities: ["安全查询订单和付款记录", "下载收据或发票", "查看退款进度", "提交售后联系请求"],
    restrictions: ["第一版不开放消费者自助退款", "查询必须通过强校验防止订单枚举"],
    connectsTo: ["Commerce", "Payment Orchestration", "Finance", "Platform Support"],
  },
];

export const COARSE_ARCHITECTURE = [
  { name: "Client & Contract", items: ["Client", "Onboarding", "Contract", "Business Profile", "Operating Entity", "Acceptance Policy"] },
  { name: "Commerce", items: ["Checkout Session", "Order Snapshot", "Consumer", "Order", "Fulfillment", "Refund"] },
  { name: "Payment Orchestration", items: ["Provider Registry", "Merchant Account", "Routing", "Execution", "Callback", "Payment Reconciliation"] },
  { name: "Risk & Compliance", items: ["Client Risk", "Business Policy", "Transaction Risk", "Decision", "Case", "Dispute"] },
  { name: "Finance", items: ["Fee Calculation", "Revenue Share", "Accounting Instruction", "Ledger & Balance", "Settlement", "Profitability", "Tax", "Reconciliation"] },
  { name: "Platform Support", items: ["IAM", "Client Webhook", "Notification", "Configuration", "Audit", "File & Evidence"] },
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
  "六个业务系统各自拥有独立数据库，禁止跨库查询和写入；跨系统数据只能通过接口获得。",
  "同步命令和查询通过系统接口；MQ 仅用于内部回调和业务事实通知。",
  "Hosted Checkout、Client Console、Partner Console、Operations Console 和 API Gateway 是入口应用，入口应用不拥有核心业务数据。",
  "平台自有主体不创建 Partner 关系，由 Operations Console 管理；外部合作方通过 Partner Console 提交资料、查询授权数据和申请分润出款。",
  "技术栈计划为 Java 微服务、Nacos 和 Spring Cloud，但本阶段不展开。",
];

export const OPEN_QUESTIONS = [
  "主交易状态机及异常状态恢复。",
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
