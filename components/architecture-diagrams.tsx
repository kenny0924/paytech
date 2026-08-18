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
        <div><strong>Platform</strong><span>Checkout · 风控 · 账本 · 结算</span></div>
      </div>
      <div className="relationship-note note-bottom">扣除税费、成本和准备金后向 Client 结算</div>
    </div>
  );
}

export function CoarseArchitectureDiagram() {
  return (
    <div className="coarse-architecture">
      <div className="entry-row">
        <span>Hosted Checkout</span><span>Client Console</span><span>Partner Console</span>
        <span>Client API / Gateway</span><span>Operations Console</span><span>Provider Endpoint</span>
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
    </div>
  );
}

const domainGroups = [
  { service: "Client & Contract", domains: ["Client Account", "Operating Entity", "Local Partner", "Agreement", "Commercial Terms"] },
  { service: "Commerce", domains: ["Checkout Session", "Order Snapshot", "Consumer", "Order", "Fulfillment", "Refund", "Customer Care"] },
  { service: "Payment Orchestration", domains: ["Provider Registry", "Merchant Account", "Payment Routing", "Payment Execution", "Provider Callback", "Payment Reconciliation"] },
  { service: "Risk & Compliance", domains: ["Client Risk", "Business Policy", "Transaction Risk", "Risk Decision", "Case Management", "Dispute"] },
  { service: "Finance", domains: ["Fee Calculation", "Revenue Share", "Accounting Instruction", "Legal Entity Ledger", "Balance & Reserve", "Settlement", "Profitability", "Tax", "Finance Reconciliation"] },
  { service: "Platform Support", domains: ["IAM", "API Key", "Client Webhook", "Notification", "Configuration", "Audit", "File & Evidence"] },
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
    { name: "Merchant Account / MID", examples: ["MID", "MCC · 币种 · 限额"], detail: "绑定 Operating Entity" },
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
        <span>Pending</span><b>→</b><span>Available</span><b>→</b><span>Settlement Statement</span><b>→</b><span>人工付款 + 凭证</span>
      </div>
    </div>
  );
}
