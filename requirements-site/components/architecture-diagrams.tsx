import { COARSE_ARCHITECTURE, FINANCE_COMPONENTS, TRANSACTION_FLOW } from "../content/requirements";

export function BusinessModelDiagram() {
  return (
    <div className="business-model-diagram" aria-label="MoR 业务关系">
      <div className="relationship-note note-top">发行 / 转售授权</div>
      <article className="entity-card entity-client">
        <span>SUPPLIER</span>
        <strong>Client</strong>
        <small>商品或服务供应方</small>
      </article>
      <div className="relation-arrow"><b>→</b><small>授权</small></div>
      <article className="entity-card entity-main">
        <span>LEGAL SELLER</span>
        <strong>Operating Entity</strong>
        <small>合同卖方 · 收款 · 税务 · 退款</small>
      </article>
      <div className="relation-arrow"><b>→</b><small>销售</small></div>
      <article className="entity-card entity-consumer">
        <span>BUYER</span>
        <strong>Consumer</strong>
        <small>托管 Checkout 最终付款人</small>
      </article>
      <div className="business-supports">
        <div><strong>Local Partner</strong><span>主体合作与独立分润</span></div>
        <div><strong>Acquirer</strong><span>NI / Geidea · MID</span></div>
        <div><strong>Platform</strong><span>Checkout · 风控 · 账本 · Provider 清算 · Manual Payout</span></div>
      </div>
      <div className="relationship-note note-bottom">扣除税费与成本后形成应付；实际出款再检查保证金</div>
    </div>
  );
}

export function CoarseArchitectureDiagram() {
  return (
    <div className="coarse-architecture">
      <div className="gateway-row">
        <span>Internet / Client / Consumer / Provider</span>
        <b>→</b>
        <strong>API Gateway</strong>
        <small>认证 · 限流 · 路由 · 访问日志</small>
      </div>
      <div className="entry-row">
        <span>Hosted Checkout</span><span>Client Console</span><span>Partner Console</span>
        <span>Client API</span><span>Operations Console</span><span>Provider Endpoint</span>
      </div>
      <div className="down-arrow">↓</div>
      <div className="architecture-grid">
        {COARSE_ARCHITECTURE.map((group, index) => (
          <article className="architecture-card" key={group.name}>
            <div className="architecture-card-head"><span>0{index + 1}</span><h3>{group.name}</h3></div>
            <div className="chip-list">
              {group.items.map((item) => <span key={item}>{item}</span>)}
            </div>
          </article>
        ))}
      </div>
      <p className="diagram-caption">
        Supporting infrastructure（不计入六个核心系统）：Vault 集中保护非卡敏感值；每个服务内嵌 compensation-event，并由 XXL-Job 兜底唤醒本地可靠任务。
      </p>
    </div>
  );
}

const domainGroups = [
  { service: "Client & Contract", domains: ["Client Account", "Business Profile", "Real-time Readiness Check", "Operating Entity", "Local Partner", "Agreement", "Commercial Terms"] },
  { service: "Commerce", domains: ["Checkout Session", "Consumer Contract", "Order Context", "Consumer", "Order", "Refund", "Customer Care"] },
  { service: "Payment Orchestration", domains: ["Provider Registry", "Provider Account / MID", "Payment Routing", "Payment Execution", "Provider Callback", "Payment Reconciliation"] },
  { service: "Risk & Compliance", domains: ["Client Risk", "Business Policy", "Transaction Risk", "Risk Decision", "Settlement Restriction", "Case Management", "Dispute"] },
  { service: "Finance", domains: ["Fee Calculation", "Tax & Fiscal Record", "Revenue Share", "Provider Cost & Settlement", "Accounting Instruction", "Legal Entity Ledger", "Account Balance", "Contract Maturity", "Beneficiary Account", "Security Deposit & Recovery Gate", "Manual Payout", "Payout Execution & Reconciliation"] },
  { service: "Platform Support", domains: ["IAM", "API Key", "Client Webhook", "Notification", "Consumer Document", "Configuration", "Audit", "File & Evidence"] },
];

export function DomainMapDiagram() {
  return (
    <div className="domain-map">
      {domainGroups.map((group, index) => (
        <article className="domain-group" key={group.service}>
          <header><span>{String(index + 1).padStart(2, "0")}</span><h3>{group.service}</h3></header>
          <div>
            {group.domains.map((domain) => <span className="domain-pill" key={domain}>{domain}</span>)}
          </div>
        </article>
      ))}
      <p className="diagram-caption">每个小块是独立 bounded context；外层分组是业务责任区，不强制等同于一个物理微服务。</p>
    </div>
  );
}

export function EndToEndFlow() {
  return (
    <div className="transaction-flow" aria-label="端到端交易流程">
      {TRANSACTION_FLOW.map((step, index) => (
        <div className="flow-step" key={step}>
          <span>{String(index + 1).padStart(2, "0")}</span>
          <strong>{step}</strong>
          {index < TRANSACTION_FLOW.length - 1 ? <b>→</b> : null}
        </div>
      ))}
    </div>
  );
}

export function PaymentTaxonomyDiagram() {
  const layers = [
    { name: "Provider / Gateway", examples: ["Network International", "Geidea"], detail: "连接外部收单能力" },
    { name: "Provider Account / MID", examples: ["MID", "MCC · 币种 · 限额"], detail: "绑定 Operating Entity" },
    { name: "Payment Method", examples: ["Card", "Wallet · Bank Transfer"], detail: "消费者使用的支付方式" },
    { name: "Scheme / Network", examples: ["Visa · Mastercard", "mada"], detail: "卡组织或本地支付网络" },
  ];
  return (
    <div className="taxonomy-flow">
      {layers.map((layer, index) => (
        <div className="taxonomy-stage" key={layer.name}>
          <article>
            <small>LAYER {index + 1}</small>
            <h3>{layer.name}</h3>
            {layer.examples.map((example) => <span key={example}>{example}</span>)}
            <p>{layer.detail}</p>
          </article>
          {index < layers.length - 1 ? <b>→</b> : null}
        </div>
      ))}
    </div>
  );
}

export function FinanceFlowDiagram() {
  return (
    <div className="finance-diagram">
      <div className="fund-source">
        <small>CONSUMER</small>
        <strong>Gross Payment</strong>
        <span>USD · 最小货币单位整数</span>
      </div>
      <div className="finance-connector">↓ 交易事实先计算，再生成记账指令；账本不决定分润公式</div>
      <div className="finance-components">
        {FINANCE_COMPONENTS.map((component, index) => (
          <div key={component}><span>{String(index + 1).padStart(2, "0")}</span><strong>{component}</strong></div>
        ))}
      </div>
      <div className="settlement-line">
        <span>Pending</span><b>→</b><span>Available</span><b>→</b><span>Locked for Payout</span><b>→</b><span>Paid + 凭证</span>
      </div>
      <div className="settlement-line settlement-recovery-line">
        <span>RefundSucceeded</span><b>→</b><span>Pending / Available</span><b>→</b><span>不足直接 Recovery Receivable</span><b>→</b><span>未来交易抵扣 / 人工追缴</span>
      </div>
      <div className="settlement-line settlement-recovery-line">
        <span>DisputeLost / DisputeOutcomeCorrected</span><b>→</b><span>outcome=LOST · principal_bearer=CLIENT · security_deposit_use_allowed=1</span><b>→</b><span>CLIENT_SECURITY_DEPOSIT/NONE</span><b>→</b><span>剩余 CLIENT_RECOVERY_RECEIVABLE/NONE</span>
      </div>
    </div>
  );
}
