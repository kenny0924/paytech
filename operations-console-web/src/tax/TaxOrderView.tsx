import type { ReactNode } from 'react';
import { Button } from 'antd';
import {
  BulbOutlined, CheckOutlined, CloseOutlined, DownloadOutlined, ExclamationCircleOutlined, FileExcelOutlined, HourglassOutlined, InfoCircleOutlined,
  MinusOutlined, StopOutlined, TagOutlined,
} from '@ant-design/icons';
import { formatAmount } from '../finance/finance';
import { enumLabel, formatCountry, formatDate, formatDateTime, formatNumber } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { activeStepIndex, rateNumber, reasonOwner, signedAmount, stepFactKey } from './tax';
import type { TaxBlockReason, TaxLine, TaxOrderPage, TaxQuote, TaxStep } from './tax';

/** 时间：日期 + 时分 + 时区偏移（原型"2026-10-29 10:31 +04"）。 */
const stamp = (value: string | null) => formatDateTime(value, { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit',
  minute: '2-digit', timeZoneName: 'shortOffset' });
const clock = (value: string | null) => formatDateTime(value, { hour: '2-digit', minute: '2-digit' });
/** 金额按字符串原样分组，不经浮点。 */
const money = (value: string | null | undefined) => (value ? formatAmount(value).text : '—');
/** 税率小数显示为百分比。 */
export const percent = (rate: string | null | undefined) => {
  const value = rateNumber(rate);
  return value === null ? '—' : formatNumber(value, { style: 'percent', maximumFractionDigits: 4 });
};

/**
 * 税务查询结果页（原型 16—18、21）：标题 + 唯一状态徽标 + 一行归属说明 → 税务进度 → 一张提示卡 → 左栏"收了多少税、为什么"、右栏税票与依据
 * <li>页面只读；下载按钮只在税票已开具且有文件时出现，下载过程与失败提示由调用方提供的 onDownload 处理。</li>
 */
export function TaxOrderView({ page, search, onDownload, downloading }: {
  page: TaxOrderPage; search: ReactNode; onDownload: (documentId: string) => void; downloading: boolean;
}) {
  const t = useT('tax');
  return <div className="tax-result">
    <div className="tax-head">
      <div className="tax-head-main">
        <div className="tax-title-row"><h1 className="tax-order-no">{page.orderNo}</h1><StatusPill status={page.pageStatus} /></div>
        <div className="tax-meta">{metaLine(t, page).map((item, index) => <span key={index}>{item}</span>)}</div>
      </div>
      <div className="tax-head-search">{search}</div>
    </div>
    <Progress page={page} />
    <Callout page={page} />
    <div className="tax-columns">
      <div className="tax-column-main">{mainColumn(page)}</div>
      <div className="tax-column-side">{sideColumn(page, onDownload, downloading)}</div>
    </div>
    {page.pageStatus !== 'NOT_MANAGED' && <p className="tax-footnote">{t('footer.disclaimer')}</p>}
  </div>;
}

/** 一行归属说明：Client · 业务模式 · 纳税主体（或税务责任方 / 收单主体）；有名称时显示名称并在括号中保留编号。 */
function metaLine(t: Translate, page: TaxOrderPage): string[] {
  const mode = enumLabel(t, 'enum.businessMode', page.businessMode);
  const client = page.buyerClientName ? t('header.clientNamed', { name: page.buyerClientName, clientId: page.buyerClientId })
    : t('header.client', { clientId: page.buyerClientId });
  if (page.pageStatus === 'NOT_MANAGED') {
    const acquirer = page.entityName ? t('header.acquirerNamed', { name: page.entityName, entityNo: page.entityNo })
      : t('header.acquirer', { entityNo: page.entityNo });
    return [client, mode, t('header.clientResponsible'), acquirer];
  }
  const subject = page.taxSubjectName ? t('header.subjectNamed', { name: page.taxSubjectName, entityNo: page.entityNo })
    : t('header.subject', { partyId: page.taxSubjectPartyId, entityNo: page.entityNo });
  const operatedBy = page.managedBy ? enumLabel(t, 'enum.ManagedBy', page.managedBy) : null;
  return operatedBy ? [client, mode, subject, operatedBy] : [client, mode, subject];
}

/** 标题处唯一的状态徽标。 */
export function StatusPill({ status, compact = false }: { status: string; compact?: boolean }) {
  const t = useT('tax');
  return <span className={`tax-pill is-${status.toLowerCase()}`}>{compact ? enumLabel(t, 'recent.status', status)
    : enumLabel(t, 'enum.TaxPageStatus', status)}</span>;
}

/** 税务进度：一步一个节点，连接线到第一个未完成步骤为止。 */
function Progress({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  const active = activeStepIndex(page.steps);
  return <ol className={`tax-progress is-${page.steps.length}`} aria-label={t('progress.label')}>
    {page.steps.map((step, index) => <ProgressStep key={step.key} step={step} page={page}
      reached={active === -1 || index < active} />)}
  </ol>;
}

const stepIcons: Record<string, ReactNode> = {
  DONE: <CheckOutlined />, BLOCKED: <CloseOutlined />, ATTENTION: <HourglassOutlined />, NOT_APPLICABLE: <MinusOutlined />,
  UNTRACKED: <MinusOutlined />, PENDING: null,
};

/** 进度中的一步：标题、事实与发生时间。 */
function ProgressStep({ step, page, reached }: { step: TaxStep; page: TaxOrderPage; reached: boolean }) {
  const t = useT('tax');
  const notManaged = page.pageStatus === 'NOT_MANAGED';
  return <li className={`tax-step is-${step.state.toLowerCase()}${reached ? ' is-reached' : ''}`}>
    <span className="tax-step-dot" aria-hidden="true">{stepIcons[step.state]}</span>
    <div className="tax-step-title">{t(`step.${notManaged ? 'notManaged.' : ''}${step.key}.title`, { state: step.state })}</div>
    <div className="tax-step-fact">{stepFact(t, step, page)}</div>
    {step.occurredAt && <div className="tax-step-time">{stamp(step.occurredAt)}</div>}
  </li>;
}

/** 步骤事实文案：键见 stepFactKey，参数为金额、国家、单号与申报币种。 */
function stepFact(t: Translate, step: TaxStep, page: TaxOrderPage): string {
  const key = stepFactKey(step, page);
  return t.has(key) ? t(key, { amount: money(step.amount), currency: page.currency, country: step.countryCode ?? '—',
    documentNumber: step.documentNumber ?? '—', reportingCurrency: step.reportingCurrency ?? '—' }) : '';
}

const calloutIcons: Record<string, ReactNode> = {
  DANGER: <ExclamationCircleOutlined />, WARNING: <InfoCircleOutlined />, INFO: <InfoCircleOutlined />, NEUTRAL: <InfoCircleOutlined />,
  SUCCESS: <CheckOutlined />,
};

/** 唯一提示卡。 */
function Callout({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  const { callout } = page;
  const params = { taxAmount: money(callout.taxAmount), currency: page.currency, reportingCurrency: callout.reportingCurrency ?? '—',
    expiresAt: stamp(callout.expiresAt), reason: page.notManaged?.reason ?? 'other' };
  return <div className={`tax-callout is-${callout.tone.toLowerCase()}`} role={callout.tone === 'DANGER' ? 'alert' : 'status'}>
    <span className="tax-callout-icon" aria-hidden="true">{calloutIcons[callout.tone]}</span>
    <div><div className="tax-callout-title">{t(`callout.${callout.kind}.title`, params)}</div>
      <div className="tax-callout-body">{t(`callout.${callout.kind}.body`, params)}</div></div>
  </div>;
}

/** 左栏：不由平台计税为金额卡；阻断为原因卡；其余为税额构成。三者之后都是报价记录（不由平台计税只有一次报价，不显示）。 */
function mainColumn(page: TaxOrderPage): ReactNode {
  if (page.pageStatus === 'NOT_MANAGED') return <AmountsCard page={page} />;
  return <>{page.pageStatus === 'BLOCKED' ? <BlockReasonsCard page={page} /> : <AmountsCard page={page} />}<QuotesCard page={page} /></>;
}

/** 右栏：不由平台计税为说明卡；未登记为空状态（阻断时附本次匹配）；已登记为税票卡与分段卡。 */
function sideColumn(page: TaxOrderPage, onDownload: (documentId: string) => void, downloading: boolean): ReactNode {
  if (page.pageStatus === 'NOT_MANAGED') return <NotManagedCard page={page} />;
  return <>
    {page.receipt ? <ReceiptCard page={page} onDownload={onDownload} downloading={downloading} /> : <NotPaidCard />}
    {page.pageStatus === 'BLOCKED' ? <MatchingCard page={page} /> : <FactsCard page={page} />}
  </>;
}

/** 卡片外壳：标题与右侧副文字。 */
function Card({ title, extra, children, className = '' }: { title: ReactNode; extra?: ReactNode; children: ReactNode; className?: string }) {
  return <section className={`tax-card ${className}`}>
    <div className="tax-card-head"><h2>{title}</h2>{extra && <span className="tax-card-extra">{extra}</span>}</div>
    {children}
  </section>;
}

/** 税额构成（原型 16）或金额（原型 18）：三个大号数字 + 一句说明 + 精简明细。 */
function AmountsCard({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  const declared = page.amounts.taxAmountSource === 'DECLARED';
  const notManaged = page.pageStatus === 'NOT_MANAGED';
  const extra = notManaged ? t('amounts.sourceDeclared') : t('amounts.current', { revision: page.amounts.revision,
    stage: enumLabel(t, 'enum.TaxCalculationStage', page.amounts.stage) });
  return <Card title={t(notManaged ? 'amounts.titleNotManaged' : 'amounts.title')} extra={extra}>
    <div className="tax-figures">
      <Figure label={t('amounts.net')} value={page.amounts.netAmount} currency={page.currency} />
      <Figure label={t(declared ? 'amounts.declaredTax' : 'amounts.tax')} value={page.amounts.taxAmount} currency={page.currency}
        tone={declared ? 'muted' : 'accent'} />
      <Figure label={t('amounts.gross')} value={page.amounts.grossAmount} currency={page.currency} />
    </div>
    {notManaged ? <p className="tax-note">{t('amounts.notManagedNote', { calculationId: page.amounts.calculationId })}</p>
      : <><Explanation page={page} /><LinesTable page={page} /></>}
  </Card>;
}

/** 一个大号数字。 */
function Figure({ label, value, currency, tone }: { label: string; value: string | null; currency: string; tone?: 'accent' | 'muted' }) {
  return <div className={`tax-figure${tone ? ' is-' + tone : ''}`}>
    <div className="tax-figure-label">{label}</div>
    <div className="tax-figure-value">{money(value)}<span className="tax-figure-currency">{currency}</span></div>
  </div>;
}

/** 一句白话计税说明；没有可说明的组件时不显示。 */
function Explanation({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  const e = page.explanation;
  if (!e) return null;
  const sentence = e.mixed ? t('explanation.mixed') : t(e.rate ? 'explanation.single' : 'explanation.noRate', {
    jurisdiction: formatCountry(e.jurisdictionCode), taxType: e.taxType ?? '—', rate: percent(e.rate),
    treatment: enumLabel(t, 'enum.TaxTreatment', e.treatment), evidenceType: e.evidenceType ?? 'other', country: formatCountry(e.countryCode),
    scheme: e.schemeCode ? enumLabel(t, 'enum.scheme', e.schemeCode) : '—', packageCode: e.packageCode ?? '—', revision: e.policyRevision ?? '—',
  });
  return <div className="tax-explanation"><BulbOutlined aria-hidden="true" /><span>{sentence}</span></div>;
}

/** 精简明细：商品（规则码作副文字）、税率、税基、税额与合计。 */
function LinesTable({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  if (page.lines.length === 0) return null;
  return <table className="tax-lines">
    <thead><tr><th>{t('lines.item')}</th><th className="is-num">{t('lines.rate')}</th><th className="is-num">{t('lines.base')}</th>
      <th className="is-num">{t('lines.tax')}</th></tr></thead>
    <tbody>{page.lines.map(line => <LineRow key={line.lineNo} line={line} />)}</tbody>
    <tfoot><tr><td>{t('lines.total')}</td><td /><td className="is-num">{money(page.amounts.netAmount)}</td>
      <td className="is-num">{money(page.amounts.taxAmount)}</td></tr></tfoot>
  </table>;
}

/** 一行明细。 */
function LineRow({ line }: { line: TaxLine }) {
  const t = useT('tax');
  const meta = [line.categoryCode, line.treatment ? enumLabel(t, 'enum.TaxTreatment', line.treatment) : null, line.ruleCode,
    line.componentCount > 1 ? t('lines.components', { count: line.componentCount }) : null].filter(Boolean).join(' · ');
  return <tr>
    <td><div className="tax-line-item">{t('lines.itemValue', { item: line.description ?? line.lineRef, quantity: line.quantity })}</div>
      <div className="tax-line-meta">{meta}</div></td>
    <td className="is-num">{percent(line.rate)}</td><td className="is-num">{money(line.netAmount)}</td>
    <td className="is-num">{money(line.taxAmount)}</td>
  </tr>;
}

/** 报价记录：当前报价高亮，写明证据、税额与相对上次的变化。 */
function QuotesCard({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  return <Card title={t('quotes.title')} extra={t('quotes.note')}>
    <ul className="tax-quotes">{page.quotes.map(quote => <QuoteRow key={quote.calculationId} quote={quote} currency={page.currency} />)}</ul>
  </Card>;
}

/** 一次报价。 */
function QuoteRow({ quote, currency }: { quote: TaxQuote; currency: string }) {
  const t = useT('tax');
  const blocked = quote.status === 'BLOCKED';
  const stage = enumLabel(t, 'enum.TaxCalculationStage', quote.stage);
  const parts = [quote.current ? t('quotes.currentStage', { stage }) : stage,
    blocked ? enumLabel(t, 'enum.TaxCalculationStatus', quote.status) : t('quotes.tax', { amount: money(quote.taxAmount), currency }),
    t('quotes.evidence', { evidenceType: quote.evidenceType ?? 'other', country: formatCountry(quote.countryCode) })];
  return <li className={`tax-quote${quote.current ? ' is-current' : ''}${blocked ? ' is-blocked' : ''}`}>
    <span className="tax-quote-badge">r{quote.revision}</span>
    <div className="tax-quote-body">
      <div className="tax-quote-summary">{parts.join(' · ')}</div>
      <div className="tax-quote-detail">{quoteDetail(t, quote, currency)}</div>
    </div>
    <span className="tax-quote-time">{t(quote.revision === 1 ? 'quotes.event.created' : 'quotes.event.details', { time: clock(quote.calculatedAt) })}</span>
  </li>;
}

/** 报价说明：阻断 → 不可支付；国家变化 → 变化与重新生成条款；暂估 → 不可支付；其余为空。 */
function quoteDetail(t: Translate, quote: TaxQuote, currency: string): string {
  if (quote.status === 'BLOCKED') return t('quotes.blockedDetail');
  if (quote.previousCountryCode && quote.taxDelta) {
    return t('quotes.changed', { from: quote.previousCountryCode, to: quote.countryCode ?? '—', delta: signedAmount(money(quote.taxDelta)), currency });
  }
  if (quote.stage === 'PROVISIONAL') return t('quotes.estimateDetail');
  return '';
}

/** 为什么被阻断（原型 17）：逐条白话标题、说明（含原因码）与处理方。 */
function BlockReasonsCard({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  return <Card title={t('blocked.title')} extra={t('blocked.count', { revision: page.amounts.revision, count: page.blockReasons.length })}>
    <ul className="tax-reasons">{page.blockReasons.map((reason, index) => <ReasonRow key={index} reason={reason} />)}</ul>
  </Card>;
}

const reasonIcons: Record<string, ReactNode> = { MARKET_NOT_ENABLED: <StopOutlined />, UNKNOWN_TAX_CATEGORY: <TagOutlined /> };

/** 一条阻断原因。 */
function ReasonRow({ reason }: { reason: TaxBlockReason }) {
  const t = useT('tax');
  const known = t.has(`reason.${reason.code}.title`);
  const params = { country: formatCountry(reason.countryCode), countryCode: reason.countryCode ?? '—', lineNo: reason.lineNo ?? '—',
    category: reason.categoryCode ?? '—', path: reason.path ?? '—' };
  return <li className="tax-reason">
    <span className="tax-reason-icon" aria-hidden="true">{reasonIcons[reason.code] ?? <ExclamationCircleOutlined />}</span>
    <div className="tax-reason-body">
      <div className="tax-reason-title">{known ? t(`reason.${reason.code}.title`, params) : t('reason.unknown.title')}</div>
      <div className="tax-reason-text">{known ? t(`reason.${reason.code}.body`, params) : ''} {t('blocked.code', { code: reason.code })}</div>
    </div>
    <div className="tax-reason-owner"><div className="tax-label">{t('blocked.owner')}</div>
      <div>{t(`blocked.ownerValue.${reasonOwner(reason.code)}`)}</div></div>
  </li>;
}

/** 税票卡（原型 16）：缩略图、单号、开具状态与下载。 */
function ReceiptCard({ page, onDownload, downloading }: { page: TaxOrderPage; onDownload: (documentId: string) => void; downloading: boolean }) {
  const t = useT('tax');
  const receipt = page.receipt!;
  const issued = receipt.issueStatus === 'ISSUED';
  return <Card title={t('receipt.title')} extra={enumLabel(t, 'enum.TaxDocumentKind', receipt.documentKind)}>
    <div className="tax-receipt">
      <span className="tax-receipt-thumb" aria-hidden="true"><i /><i /><i /><i /><i /></span>
      <div><div className="tax-receipt-number">{receipt.documentNumber ?? t('receipt.numberPending')}</div>
        <div className="tax-receipt-meta">{issued ? t('receipt.issued', { time: formatDateTime(receipt.issuedAt, { year: 'numeric',
          month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }) }) : enumLabel(t, 'enum.TaxDocumentIssueStatus', receipt.issueStatus)}</div>
        <div className="tax-receipt-meta">{t('receipt.noPersonalData')}</div></div>
    </div>
    {receipt.downloadable && <Button type="primary" block icon={<DownloadOutlined />} loading={downloading}
      onClick={() => onDownload(receipt.documentId)}>{t('receipt.download')}</Button>}
  </Card>;
}

/** 尚未支付：没有正式登记和税票。 */
function NotPaidCard() {
  const t = useT('tax');
  return <Card title={t('receipt.titleWithRegistration')}>
    <div className="tax-empty-inline"><FileExcelOutlined aria-hidden="true" /><span>{t('receipt.notPaid')}</span></div>
  </Card>;
}

/** 键值行。 */
function Facts({ rows }: { rows: [string, ReactNode][] }) {
  return <dl className="tax-facts">{rows.map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value ?? '—'}</dd></div>)}</dl>;
}

/** 正式登记 / 申报底稿 / 计算依据合并的分段卡。 */
function FactsCard({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  const { registration, reporting, basis } = page;
  if (!registration && !reporting && !basis) return null;
  return <section className="tax-card tax-segments">
    {registration && <div className="tax-segment"><h2>{t('registration.title')}</h2><Facts rows={[
      [t('registration.registeredAt'), clock(registration.registeredAt)],
      [t('registration.taxPoint'), registration.legalTimezone ? t('registration.taxPointValue', { date: registration.taxPointDate,
        timezone: registration.legalTimezone }) : registration.taxPointDate],
      [t('registration.sourceFact'), t('registration.sourceFactValue', { type: enumLabel(t, 'enum.sourceFactType', registration.sourceFactType),
        id: registration.sourceFactId })],
      [t('registration.quote'), `r${registration.calculationRevision}`]]} /></div>}
    {reporting && <div className="tax-segment"><h2>{t('reporting.title')}</h2><Facts rows={reportingRows(t, page)} /></div>}
    {basis && <div className="tax-segment"><h2>{t('basis.title')}</h2><Facts rows={[
      [t('basis.package'), basis.packageCode ? t('basis.packageValue', { code: basis.packageCode, revision: basis.policyRevision ?? '—' }) : '—'],
      [t('basis.registration'), basis.jurisdictionCode ? t('basis.registrationValue', { jurisdiction: formatCountry(basis.jurisdictionCode),
        scheme: basis.schemeCode ? enumLabel(t, 'enum.scheme', basis.schemeCode) : '—' }) : '—'],
      [t('basis.market'), t(basis.marketEnabled ? 'basis.marketEnabled' : 'basis.marketMissing')],
      [t('basis.rounding'), basis.roundingMethod ? t('basis.roundingValue', { method: enumLabel(t, 'enum.roundingMethod', basis.roundingMethod),
        scale: basis.roundingScale ?? '—' }) : '—']]} /></div>}
  </section>;
}

/** 申报底稿行：PENDING 只有频率、销项税额与"待补汇率"；READY 增加期间、申报币种金额与截止日。 */
function reportingRows(t: Translate, page: TaxOrderPage): [string, ReactNode][] {
  const reporting = page.reporting!;
  const frequency = reporting.filingFrequency ? enumLabel(t, 'enum.filingFrequency', reporting.filingFrequency) : '—';
  const rows: [string, ReactNode][] = [
    [t('reporting.period'), reporting.periodStart ? t('reporting.periodValue', { start: reporting.periodStart, end: reporting.periodEnd ?? '—',
      frequency }) : frequency],
    [t('reporting.outputTax'), `${money(reporting.taxAmount)} ${page.currency}`],
    [t('reporting.reportingAmount'), reporting.status === 'PENDING'
      ? t('reporting.awaitingRate', { currency: reporting.reportingCurrency ?? '—' })
      : `${money(reporting.reportingTaxAmount)} ${reporting.reportingCurrency ?? ''}`],
  ];
  if (reporting.filingDueDate) rows.push([t('reporting.dueDate'), formatDate(reporting.filingDueDate + 'T00:00:00')]);
  return rows;
}

/** 本次匹配（原型 17 右栏）：证据国家、市场准入与规则包。 */
function MatchingCard({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  const matching = page.matching;
  if (!matching) return null;
  return <Card title={t('matching.title')}>
    <Facts rows={[
      [t('matching.evidence'), t('matching.evidenceValue', { countryCode: matching.countryCode ?? '—',
        evidenceType: enumLabel(t, 'enum.TaxEvidenceType', matching.evidenceType) })],
      [t('matching.market'), t(matching.marketMatched ? 'matching.marketMatched' : 'matching.marketMissing')],
      [t('matching.policy'), t(matching.policyMatched ? 'matching.policyMatched' : 'matching.policyMissing')]]} />
  </Card>;
}

/** 为什么不由平台计税（原型 18 右栏）。 */
function NotManagedCard({ page }: { page: TaxOrderPage }) {
  const t = useT('tax');
  const info = page.notManaged;
  return <Card title={t('notManaged.title')}>
    <Facts rows={[
      [t('notManaged.reason'), info?.reason ? enumLabel(t, 'enum.TaxNotManagedReason', info.reason) : '—'],
      [t('notManaged.mode'), enumLabel(t, 'enum.businessMode', info?.businessMode ?? page.businessMode)],
      [t('notManaged.responsible'), t('header.client', { clientId: info?.buyerClientId ?? page.buyerClientId })],
      [t('notManaged.registered'), t('notManaged.no')],
      [t('notManaged.receipt'), t('notManaged.receiptValue')]]} />
  </Card>;
}
