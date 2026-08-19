"use client";

import { useEffect, useMemo, useState } from "react";
import { ACTORS, DECISIONS, NAV_ITEMS, OPEN_QUESTIONS, type ContentView, type NavigationItem } from "../content/requirements";
import { navigationForView, resolveContentView } from "../lib/content-navigation.mjs";
import {
  BusinessModelDiagram,
  CoarseArchitectureDiagram,
  DomainMapDiagram,
  EndToEndFlow,
  FinanceFlowDiagram,
  PaymentTaxonomyDiagram,
} from "./architecture-diagrams";
import { StatusBadge } from "./status-badge";
import { EntryApplicationCatalog } from "./entry-application-catalog";
import { GlobalUseCases, UseCaseCatalog, UseCaseViewOverview } from "./use-case-catalog";

function SectionHeading({
  index,
  title,
  description,
  sectionId,
  onCopy,
}: {
  index: string;
  title: string;
  description: string;
  sectionId: string;
  onCopy: (id: string) => void;
}) {
  return (
    <div className="section-heading">
      <div className="section-title-block">
        <span className="section-index">{index}</span>
        <div><h2>{title}</h2><p>{description}</p></div>
      </div>
      <button className="copy-link" type="button" onClick={() => onCopy(sectionId)} aria-label={`复制${title}章节链接`}>
        # 复制链接
      </button>
    </div>
  );
}

export function RequirementsSite() {
  const [activeView, setActiveView] = useState<ContentView>("architecture");
  const [activeSection, setActiveSection] = useState("overview");
  const [copied, setCopied] = useState("");
  const [fallbackUrl, setFallbackUrl] = useState("");
  const visibleNavItems = useMemo<NavigationItem[]>(() => navigationForView(NAV_ITEMS, activeView), [activeView]);

  useEffect(() => {
    const syncViewFromHash = () => {
      const view = resolveContentView(window.location.hash, NAV_ITEMS) as ContentView;
      setActiveView(view);
      const sectionId = window.location.hash.replace(/^#/, "");
      if (NAV_ITEMS.some((item) => item.id === sectionId)) setActiveSection(sectionId);
      window.requestAnimationFrame(() => document.getElementById(sectionId)?.scrollIntoView());
    };

    syncViewFromHash();
    window.addEventListener("hashchange", syncViewFromHash);
    return () => window.removeEventListener("hashchange", syncViewFromHash);
  }, []);

  useEffect(() => {
    const sections = visibleNavItems
      .map((item) => document.getElementById(item.id))
      .filter((element): element is HTMLElement => Boolean(element));
    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
        if (visible?.target.id) setActiveSection(visible.target.id);
      },
      { rootMargin: "-15% 0px -70% 0px", threshold: [0, 0.1, 0.5] },
    );
    sections.forEach((section) => observer.observe(section));
    return () => observer.disconnect();
  }, [visibleNavItems]);

  function switchContentView(view: ContentView) {
    const sectionId = view === "architecture" ? "overview" : "use-cases";
    setActiveView(view);
    setActiveSection(sectionId);
    window.history.replaceState(null, "", `#${sectionId}`);
    window.requestAnimationFrame(() => document.getElementById(sectionId)?.scrollIntoView());
  }

  async function copyLink(id: string) {
    const url = `${window.location.origin}${window.location.pathname}#${id}`;
    window.history.replaceState(null, "", `#${id}`);
    try {
      await navigator.clipboard.writeText(url);
      setFallbackUrl("");
      setCopied(id);
      window.setTimeout(() => setCopied(""), 1800);
    } catch {
      setFallbackUrl(url);
    }
  }

  return (
    <div className="site-shell">
      <aside className="sidebar" aria-label="需求文档目录">
        <a className="brand" href="#overview">
          <span className="brand-mark">M</span>
          <span>MoR Blueprint</span>
        </a>
        <nav>
          {visibleNavItems.map((item) => (
            <a key={item.id} href={`#${item.id}`} aria-current={activeSection === item.id ? "location" : undefined}>
              {item.label}
            </a>
          ))}
        </nav>
        <div className="sidebar-note">
          <span className="signal" />
          Requirements v0.1
        </div>
      </aside>

      <main className="document">
        <details className="mobile-toc">
          <summary>浏览文档目录</summary>
          <nav>{visibleNavItems.map((item) => <a key={item.id} href={`#${item.id}`}>{item.label}</a>)}</nav>
        </details>

        <div className="primary-tabs" role="tablist" aria-label="一级内容导航">
          <button
            type="button"
            role="tab"
            data-content-tab="architecture"
            aria-selected={activeView === "architecture"}
            aria-controls="architecture-panel"
            onClick={() => switchContentView("architecture")}
          >
            <span>架构</span><small>业务模型 · 六系统 · 入口应用</small>
          </button>
          <button
            type="button"
            role="tab"
            data-content-tab="use-cases"
            aria-selected={activeView === "use-cases"}
            aria-controls="use-cases-panel"
            onClick={() => switchContentView("use-cases")}
          >
            <span>用例</span><small>全局用例 · 系统用例</small>
          </button>
        </div>

        <div id="architecture-panel" role="tabpanel" data-view-panel="architecture" hidden={activeView !== "architecture"}>

        <header className="hero" id="overview">
          <div className="eyebrow">MERCHANT OF RECORD · REQUIREMENTS</div>
          <h1>MoR Platform Blueprint</h1>
          <p className="hero-copy">
            一个面向全球数字商品与在线服务的轻量 MoR 平台需求文档，覆盖业务模型、支付编排、风控、账本、分润和人工结算。
          </p>
          <div className="hero-meta">
            <StatusBadge status="confirmed" />
            <span>Version 0.1</span>
            <span>2026-08-18</span>
          </div>
          <div className="scope-grid" aria-label="当前 MVP 范围">
            <article><small>OPERATING ENTITY</small><strong>UAE · 单一主体</strong></article>
            <article><small>CHECKOUT</small><strong>Hosted · USD</strong></article>
            <article><small>PAYMENT</small><strong>多 Provider / MID</strong></article>
            <article><small>SETTLEMENT</small><strong>系统核算 · 人工付款</strong></article>
          </div>
        </header>

        <section className="section" id="business-model">
          <SectionHeading index="01" title="业务模型与参与者" description="Client 提供商品或服务；Operating Entity 作为真实消费者合同卖方。" sectionId="business-model" onCopy={copyLink} />
          <BusinessModelDiagram />
          <h3 className="subheading">系统参与者</h3>
          <div className="actor-grid">
            {ACTORS.map((actor) => (
              <article className="actor-card" key={actor.id}>
                <span className="actor-initial">{actor.name.slice(0, 1)}</span>
                <h3>{actor.name}</h3>
                <p>{actor.description}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="section" id="architecture">
          <SectionHeading index="02" title="原始粗粒度架构" description="保留最初的六个责任区视图，用于理解平台的最高层级能力。" sectionId="architecture" onCopy={copyLink} />
          <CoarseArchitectureDiagram />
        </section>

        <section className="section" id="domain-map">
          <SectionHeading index="03" title="六系统内部功能域" description="六个系统是边界；细粒度领域作为系统内部职责，不再等同于独立系统。" sectionId="domain-map" onCopy={copyLink} />
          <DomainMapDiagram />
        </section>

        <section className="section" id="end-to-end">
          <SectionHeading index="04" title="端到端业务流程" description="从 Client 入驻到交易、履约、争议、账本和人工结算的完整生命周期。" sectionId="end-to-end" onCopy={copyLink} />
          <EndToEndFlow />
          <div className="principle-grid">
            <article><strong>支付成功</strong><p>只能以渠道服务端通知和主动查询确认，不能以 successUrl 确认。</p></article>
            <article><strong>履约独立</strong><p>订单、支付和履约分别维护状态，Client System 回传履约证据。</p></article>
            <article><strong>财务不可变</strong><p>退款、拒付和人工调整追加冲销分录，不修改历史账本。</p></article>
          </div>
        </section>

        <section className="section" id="entry-applications">
          <SectionHeading index="05" title="入口应用与管控台" description="入口应用服务不同用户和外部系统，但不计入六个核心系统，也不拥有核心业务事实。" sectionId="entry-applications" onCopy={copyLink} />
          <EntryApplicationCatalog />
        </section>

        <section className="section" id="payment-model">
          <SectionHeading index="06" title="支付渠道模型" description="避免把 Provider、MID、支付方式和 Scheme 混成一个无法解释的渠道字段。" sectionId="payment-model" onCopy={copyLink} />
          <PaymentTaxonomyDiagram />
          <div className="boundary-box">
            <div><strong>路由资格</strong><span>Operating Entity + MCC + 币种 + 地区 + 支付方式 + 订单快照 + 业务政策 + 风险 + 限额</span></div>
            <p>路由器只能在全部条件匹配的 MID 中排序；成功率或成本不能覆盖法律与渠道合同边界。</p>
          </div>
        </section>

        <section className="section" id="finance-model">
          <SectionHeading index="07" title="计算、账本与结算" description="计费与分润负责计算，复式账本负责记账，对账负责确认实际成本。" sectionId="finance-model" onCopy={copyLink} />
          <FinanceFlowDiagram />
          <div className="money-rules">
            <article><small>CLIENT PRICING</small><strong>百分比 + 固定金额</strong><p>合同费率按版本和交易时间生效。</p></article>
            <article><small>PARTNER SHARE</small><strong>每个合作方独立配置</strong><p>可按 GMV、平台收入或净收入计算。</p></article>
            <article><small>MONEY MODEL</small><strong>整数最小货币单位</strong><p>保存 ISO 4217 币种，禁止浮点金额。</p></article>
            <article><small>LEGAL ENTITY BOOK</small><strong>主体与币种独立账簿</strong><p>跨主体通过应收、应付往来科目处理。</p></article>
            <article><small>COST FINALIZATION</small><strong>预计成本 → 实际成本</strong><p>渠道清算和银行对账确认差异并追加调整分录。</p></article>
            <article><small>PROFIT MODEL</small><strong>收入减成本</strong><p>利润通过平台收入减实际成本得到，不直接记利润账户。</p></article>
          </div>
        </section>

        <section className="section" id="decisions">
          <SectionHeading index="08" title="已确认决策" description="这些内容已在讨论中确认，后续设计以此为当前基线。" sectionId="decisions" onCopy={copyLink} />
          <div className="decision-list">
            {DECISIONS.map((decision, index) => (
              <article key={decision}><span>{String(index + 1).padStart(2, "0")}</span><p>{decision}</p><StatusBadge status="confirmed" /></article>
            ))}
          </div>
        </section>

        <section className="section" id="open-questions">
          <SectionHeading index="09" title="后续待讨论" description="不阻碍当前需求文档，但需要在详细业务或技术设计阶段继续确定。" sectionId="open-questions" onCopy={copyLink} />
          <div className="question-grid">
            {OPEN_QUESTIONS.map((question, index) => (
              <article key={question}><span>Q{String(index + 1).padStart(2, "0")}</span><p>{question}</p><StatusBadge status="open" /></article>
            ))}
          </div>
        </section>
        </div>

        <div id="use-cases-panel" role="tabpanel" data-view-panel="use-cases" hidden={activeView !== "use-cases"}>
          <UseCaseViewOverview />

          <section className="section" id="use-cases">
            <SectionHeading index="U01" title="全局用例" description="从业务参与者的目标出发，描述跨越多个系统的完整成功结果。" sectionId="use-cases" onCopy={copyLink} />
            <GlobalUseCases />
          </section>

          <section className="section systems-section" id="systems">
            <SectionHeading index="U02" title="系统用例" description="按六个核心系统查看独立用例、接口用例、同步依赖、MQ 通知和明确边界。" sectionId="systems" onCopy={copyLink} />
            <UseCaseCatalog />
          </section>
        </div>

        <footer>
          <strong>MoR Platform Blueprint</strong>
          <span>Requirements v0.1 · 2026-08-18</span>
          <a href={activeView === "architecture" ? "#overview" : "#use-cases"}>返回本页顶部 ↑</a>
        </footer>

        {copied ? <div className="toast" role="status">章节链接已复制</div> : null}
        {fallbackUrl ? (
          <div className="copy-fallback" role="dialog" aria-label="复制章节链接">
            <label>浏览器阻止自动复制，请手动复制<input readOnly value={fallbackUrl} onFocus={(event) => event.currentTarget.select()} /></label>
            <button type="button" onClick={() => setFallbackUrl("")}>关闭</button>
          </div>
        ) : null}
      </main>
    </div>
  );
}
