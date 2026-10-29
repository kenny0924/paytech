import type { ReactNode } from 'react';
import { Alert, Tag } from 'antd';
import { CheckOutlined, CloseOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import type { ApiError } from '../api/session';
import { enumLabel, formatShortDateTime, tOr } from '../i18n/index';
import { useT } from '../i18n/react';
import { refdataName } from '../referenceData/referenceData';
import { reviewRoute } from '../clientReview/clientReview';
import { checkLines, decisionFieldOf, money, paymentMethodCodes, riskDayParts, riskDaysText, underwritingRoutes } from './underwriting';
import type { Crr, Exposure, Preview, Underwriting, UnderwritingDecision, UnderwritingInput, UnderwritingStatus } from './underwriting';

/** 卡片：标题 + 灰色副标题，右侧可放操作。 */
export function Card({ title, extra, actions, children, className }: { title?: ReactNode; extra?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string }) {
  return <section className={`uw-card${className ? ' ' + className : ''}`}>
    {title && <div className="uw-card-title"><h2>{title}</h2>{extra && <span className="uw-card-extra">{extra}</span>}{actions && <span className="uw-card-actions">{actions}</span>}</div>}
    {children}</section>;
}
/** 标签 + 值一行；窄屏时上下排列。 */
export function Row({ label, children }: { label: ReactNode; children: ReactNode }) {
  return <div className="uw-row"><div className="uw-row-label">{label}</div><div className="uw-row-value">{children}</div></div>;
}

const crrClass: Record<Crr, string> = { LOW: 'is-ok', MEDIUM: 'is-warn', HIGH: 'is-danger' };
/** CRR 徽标：LOW 绿 / MEDIUM 橙 / HIGH 红；未评为灰色"未评 CRR"。 */
export function CrrBadge({ crr }: { crr: Crr | null }) {
  const t = useT('risk');
  return crr ? <span className={`uw-badge ${crrClass[crr]}`}>{crr}</span> : <span className="uw-badge is-muted">{t('underwriting.crrNotRated')}</span>;
}
const statusClass: Record<UnderwritingStatus, string> = { DRAFT: 'is-info', SUBMITTED: 'is-warn', APPROVED: 'is-ok', DECLINED: 'is-danger', SUPERSEDED: 'is-muted' };
/** 状态徽标；已批准的当前有效版本显示"已批准 · 生效"。 */
export function StatusBadge({ status, effective = false }: { status: UnderwritingStatus; effective?: boolean }) {
  const t = useT('risk');
  return <span className={`uw-badge ${statusClass[status]}`}>{status === 'APPROVED' && effective ? t('underwriting.status.effective') : enumLabel(t, 'underwriting.status', status)}</span>;
}
export function HighRiskMark() {
  const t = useT('risk');
  return <span className="uw-badge is-danger">{t('underwriting.input.networkHighRisk')}</span>;
}
export const Missing = () => { const t = useT('risk'); return <span className="muted">{t('underwriting.input.notCollected')}</span>; };

/** MCC 显示："5967 名称"（refdata 文案，无文案时只显示编码）。 */
export function mccText(tRef: ReturnType<typeof useT>, code: string | null): string {
  if (!code) return '';
  const name = refdataName(tRef, 'mcc', code, null);
  return name === code ? code : `${code} ${name}`;
}

/** 尽调结论卡：CRR、档次、MCC（卡组织高风险标）、行业。 */
export function DueDiligenceCard({ input, crrMissingHint = true }: { input: UnderwritingInput; crrMissingHint?: boolean }) {
  const t = useT('risk'), tReview = useT('client-review'), tRef = useT('refdata');
  return <Card title={t('underwriting.input.ddTitle')} extra={<CaseLink input={input} />}>
    <Row label={t('underwriting.input.crr')}><CrrBadge crr={input.crr} />
      {input.crrMissing && crrMissingHint && <span className="uw-hint-inline">{t('underwriting.input.crrMissing')}</span>}</Row>
    <Row label={t('underwriting.input.ddTier')}>{input.ddTier ? enumLabel(tReview, 'enum.tier', input.ddTier) : <Missing />}</Row>
    <Row label={t('underwriting.input.mcc')}>{input.mccCode ? <span className="uw-inline">{mccText(tRef, input.mccCode)}{input.networkHighRiskMcc && <HighRiskMark />}</span> : <Missing />}</Row>
    <Row label={t('underwriting.input.industry')}>{input.industryCode ? refdataName(tRef, 'industry', input.industryCode, null) : <Missing />}</Row>
  </Card>;
}
function CaseLink({ input }: { input: UnderwritingInput }) {
  const t = useT('risk');
  return input.sourceCaseId ? <Link to={`${reviewRoute}/${input.sourceCaseId}`}>{t('underwriting.input.caseLink', { caseId: input.sourceCaseId })}</Link> : null;
}

/** 业务信息卡（REQ-12 四项 + 流水、客单价）：缺失显示"未采集"；网址只有 http(s) 才成链接。 */
export function BusinessCard({ input }: { input: UnderwritingInput }) {
  const t = useT('risk'), tSchema = useT('onboarding-schema');
  const currency = input.volumeCurrency;
  const amount = (value: string | null) => value && currency ? money(value, currency) : <Missing />;
  const methods = paymentMethodCodes(input.expectedPaymentMethods);
  const link = (value: string | null) => !value ? <Missing /> : /^https?:\/\/\S+$/i.test(value)
    ? <a href={value} target="_blank" rel="noopener noreferrer" className="uw-link">{value}</a> : <span className="uw-break">{value}</span>;
  return <Card title={t('underwriting.input.businessTitle')} extra={<span className="muted">{t('underwriting.input.businessSource')}</span>}>
    <Row label={t('underwriting.input.monthlyVolume')}>{amount(input.expectedMonthlyVolume)}</Row>
    <Row label={t('underwriting.input.ticket')}>{amount(input.expectedTicket)}</Row>
    <Row label={t('underwriting.input.paymentMethods')}>{methods.length ? <span className="uw-tags">{methods.map(code =>
      <Tag key={code} color="blue">{tOr(tSchema, `paymentMethod.${code}`, code)}{tSchema.has(`paymentMethod.${code}`) ? ` ${code}` : ''}</Tag>)}</span> : <Missing />}</Row>
    <Row label={t('underwriting.input.website')}>{link(input.websiteUrl)}</Row>
    <Row label={t('underwriting.input.refundPolicy')}>{link(input.refundPolicyUrl)}</Row>
    <Row label={t('underwriting.input.fulfillment')}>{input.fulfillmentType
      ? <>{tOr(tSchema, `catalog.FULFILLMENT_TYPE.${input.fulfillmentType}`, input.fulfillmentType)}
        {input.fulfillmentType === 'PRE_ORDER' && <span className="muted"> · {input.fulfillmentDays === null ? t('underwriting.input.daysMissing')
          : t('underwriting.input.fulfillmentDays', { days: input.fulfillmentDays })}</span>}</> : <Missing />}</Row>
  </Card>;
}

/**
 * 估算敞口卡（B10 / AC-12）：金额、公式（月流水 × 风险天数 ÷ 30）与风险天数说明；只作参考
 * <li>金额与风险天数以服务端为准；公式里的"(交付 + T+N)"只在各项相加等于服务端天数时展开。</li>
 * @param frozen 已提交：说明改为"冻结于提交时"
 */
export function ExposureCard({ exposure, unavailableReason, input, settlementDays, frozen, loading }: { exposure: Exposure | null; unavailableReason: string | null;
  input: UnderwritingInput | null; settlementDays: number | null; frozen: boolean; loading?: boolean }) {
  const t = useT('risk');
  const parts = input ? riskDayParts(input.fulfillmentType, input.fulfillmentDays, settlementDays) : null;
  const high = exposure && input?.expectedMonthlyVolume ? Number(exposure.amount) > Number(input.expectedMonthlyVolume) : false;
  return <Card className={loading ? 'is-loading' : undefined} title={t('underwriting.exposure.title')} extra={<span className="muted">{t('underwriting.exposure.hint')}</span>}>
    {exposure ? <>
      <div className="uw-exposure"><span className={`uw-exposure-amount${high ? ' is-high' : ''}`}>{money(exposure.amount, exposure.currency, 2)}</span>
        {input?.expectedMonthlyVolume && <span className="uw-exposure-formula">= {t('underwriting.exposure.formula', {
          volume: money(input.expectedMonthlyVolume, exposure.currency), days: riskDaysText(parts, exposure.riskDays) })}</span>}</div>
      <div className="uw-hint">{[input?.fulfillmentType ? t('underwriting.exposure.riskDays.' + input.fulfillmentType, {
        fulfillmentDays: input.fulfillmentDays ?? 0, settlementDays: settlementDays ?? 0 }) : null,
      exposure.settlementDaysAssumed ? t('underwriting.exposure.assumed') : null,
      frozen ? t('underwriting.exposure.frozen') : t('underwriting.exposure.live')].filter(Boolean).join(' ')}</div>
    </> : <div className="uw-exposure"><span className="uw-exposure-amount is-muted">{t('underwriting.exposure.unavailable')}</span>
      {unavailableReason && <span className="uw-hint">{tOr(t, 'underwriting.exposure.unavailableReason.' + unavailableReason, unavailableReason)}</span>}</div>}
  </Card>;
}

/** 输入来源卡（提交时冻结）：尽调案件 + 版本、入驻申请 + 资料版本、分类确认时间。 */
export function SourcesCard({ input }: { input: UnderwritingInput }) {
  const t = useT('risk');
  return <Card title={t('underwriting.sources.title')}>
    <Row label={t('underwriting.sources.case')}>{input.sourceCaseId
      ? <><Link to={`${reviewRoute}/${input.sourceCaseId}`} className="uw-mono">{input.sourceCaseId}</Link><span className="muted"> · {t('underwriting.sources.version', { version: input.sourceCaseVersion ?? '—' })}</span></> : '—'}</Row>
    <Row label={t('underwriting.sources.application')}>{input.sourceApplicationId
      ? <><span className="uw-mono">{input.sourceApplicationId}</span><span className="muted"> · {t('underwriting.sources.revision', { revision: input.sourceDraftRevision ?? '—' })}</span></> : '—'}</Row>
    <Row label={t('underwriting.sources.classification')}>{input.sourceClassificationConfirmedAt ? formatShortDateTime(input.sourceClassificationConfirmedAt) : '—'}</Row>
  </Card>;
}

/** 只读结论（原型 02 标准模板、04、05）。 */
export function DecisionRows({ decision }: { decision: UnderwritingDecision }) {
  const t = useT('risk');
  const deposit = decision.requireDeposit && decision.depositAmount && decision.depositCurrency
    ? [t('underwriting.decision.depositRequired', { amount: money(decision.depositAmount, decision.depositCurrency) }),
      decision.depositBlocksCollection ? t('underwriting.decision.blocksCollection') : null, t('underwriting.decision.byReq18')].filter(Boolean).join(' · ')
    : t('underwriting.decision.notRequired');
  return <>
    <Row label={t('underwriting.decision.startingTierLabel')}>{t('underwriting.startingTier')}</Row>
    <Row label={t('underwriting.decision.observation')}>{decision.observationDays !== null && decision.observationTransactions !== null
      ? t('underwriting.decision.observationValue', { days: decision.observationDays, transactions: decision.observationTransactions }) : '—'}</Row>
    <Row label={t('underwriting.decision.deposit')}>{deposit}</Row>
    <Row label={t('underwriting.decision.suggestedSettlementDays')}>{decision.suggestedSettlementDays === null ? <span className="muted">{t('underwriting.decision.notSet')}</span> : `T+${decision.suggestedSettlementDays}`}</Row>
    <Row label={t('underwriting.decision.networkRegistration')}>{decision.highRiskMcc ? t('underwriting.decision.registrationNeeded') : t('underwriting.decision.registrationNotNeeded')}
      {decision.highRiskMccReason && <div className="uw-note">{decision.highRiskMccReason}</div>}</Row>
    <Row label={t('underwriting.decision.limitNote')}>{decision.limitNote ?? '—'}</Row>
    {decision.underwritingNote && <Row label={t('underwriting.decision.underwritingNote')}><div className="uw-note">{decision.underwritingNote}</div></Row>}
    {decision.handoverNote && <Row label={t('underwriting.decision.handoverNote')}><div className="uw-note">{decision.handoverNote}</div></Row>}
  </>;
}

/**
 * 自动承保判定卡（原型 02 / 03）：三个条件逐条 ✓ / ✗，未通过时显示原因；前提项只在未通过时出现；底部结论提示
 * @param highNotice HIGH 时说明需四眼复核
 */
export function AutoCheckCard({ preview, input }: { preview: Preview; input: UnderwritingInput | null }) {
  const t = useT('risk'), tRef = useT('refdata');
  const lines = checkLines(preview.checks);
  const params = { crr: input?.crr ?? t('underwriting.crrNotRated'), volume: input?.expectedMonthlyVolume && input.volumeCurrency ? money(input.expectedMonthlyVolume, input.volumeCurrency) : '—',
    mcc: mccText(tRef, input?.mccCode ?? null) || '—',
    threshold: preview.thresholdAmount && preview.thresholdCurrency ? money(preview.thresholdAmount, preview.thresholdCurrency) : '' };
  // 有阈值时按原型显示"预计月流水 X ≤ 阈值 Y"，没有时用不带数值的说明。
  const groupKey = (key: string) => key === 'volume' && params.threshold ? 'underwriting.auto.group.volumeThreshold' : 'underwriting.auto.group.' + key;
  return <Card title={t('underwriting.auto.title')} extra={preview.ruleVersionNo !== null && <span className="muted">{t('underwriting.auto.ruleVersion', { version: preview.ruleVersionNo })}</span>}>
    <ul className="uw-checks">{lines.map(line => <li key={line.key} className={line.passed ? 'is-pass' : 'is-fail'}>
      {line.passed ? <CheckOutlined aria-label={t('underwriting.auto.passed')} /> : <CloseOutlined aria-label={t('underwriting.auto.failed')} />}
      <div><div>{tOr(t, groupKey(line.key), tOr(t, 'underwriting.auto.check.' + line.key, line.key), params)}</div>
        {line.failedCode && <div className="uw-hint">{tOr(t, 'underwriting.auto.reason.' + line.failedCode, line.failedCode, params)}</div>}</div></li>)}</ul>
    {preview.autoEligible ? <div className="uw-verdict is-ok"><strong>{t('underwriting.auto.eligible')}</strong><div>{t('underwriting.auto.eligibleHint')}</div></div>
      : <div className="uw-verdict is-warn"><strong>{t('underwriting.auto.notEligible')}</strong>
        <div>{preview.reviewLevel === 'HIGH' ? t('underwriting.highNotice') : t('underwriting.auto.manualHint')}</div></div>}
  </Card>;
}

/** 版本历史（原型 05）：版本号、状态、来源与操作人、时间、修订原因，链接到各版本。 */
export function HistoryCard({ history, currentId }: { history: readonly Underwriting[]; currentId: string }) {
  const t = useT('risk');
  if (!history.length) return null;
  return <Card title={t('underwriting.history.title')}>
    <ol className="uw-history">{history.map(item => {
      const next = history.find(other => other.supersedesUnderwritingId === item.underwritingId);
      return <li key={item.underwritingId} className={item.underwritingId === currentId ? 'is-current' : undefined}>
        <span className="uw-history-no">v{item.versionNo}</span>
        <div className="uw-history-body">
          <div className="uw-inline"><StatusBadge status={item.status} effective={item.status === 'APPROVED'} /><span>{sourceText(t, item)}</span></div>
          <div className="uw-hint">{[formatShortDateTime(item.decidedAt ?? item.submittedAt ?? null), item.revisionReason ? t('underwriting.history.revisionReason', { reason: item.revisionReason }) : null,
            item.status === 'SUPERSEDED' && next ? t('underwriting.history.supersededBy', { version: next.versionNo }) : null,
            item.status === 'DECLINED' && item.declineReason ? t('underwriting.history.declineReason', { reason: item.declineReason }) : null].filter(x => x && x !== '—').join(' · ')}</div>
        </div>
        {item.underwritingId !== currentId && <Link to={underwritingRoutes.detail(item.underwritingId)}>{t('underwriting.action.view')}</Link>}
      </li>; })}</ol>
  </Card>;
}

/** 来源与操作人说明："人工 · 王珂 提交即生效（MEDIUM）" / "自动规则 v1 · 触发人 林可"。 */
export function sourceText(t: ReturnType<typeof useT>, item: Underwriting): string {
  const person = (label: string | null, id: string | null) => label ?? id ?? '—';
  if (item.decisionSource === 'AUTO_RULE') return t('underwriting.source.autoBy', { version: item.ruleVersionNo ?? '?', person: person(item.createdByLabel, item.createdBy) });
  if (item.status === 'DRAFT') return t('underwriting.source.draftBy', { person: person(item.createdByLabel, item.createdBy) });
  if (item.status === 'SUBMITTED') return t('underwriting.source.submittedBy', { person: person(item.submittedByLabel, item.submittedBy) });
  if (item.reviewLevel === 'HIGH') return t('underwriting.source.reviewedBy', { submitter: person(item.submittedByLabel, item.submittedBy), reviewer: person(item.decidedByLabel, item.decidedBy) });
  return t('underwriting.source.manualBy', { person: person(item.decidedByLabel ?? item.submittedByLabel, item.decidedBy ?? item.submittedBy), level: item.reviewLevel });
}

/** 409 / 422：已知错误码翻译，否则服务端 detail；结论字段的 422 逐项列出。 */
export function CommandAlert({ failure }: { failure: ApiError | undefined }) {
  const t = useT('risk');
  if (!failure) return null;
  const known = tOr(t, 'underwriting.error.' + failure.code, '');
  const fields = failure.fieldErrors.map(error => {
    const field = decisionFieldOf(error.field);
    return `${field ? t('underwriting.field.' + field) : error.field}: ${error.message}`;
  });
  return <Alert showIcon type="warning" className="uw-alert" title={failure.status === 409 ? t('underwriting.command.conflict') : t('underwriting.command.rejected')}
    description={<>{known || failure.detail || failure.code}{fields.length > 0 && <ul className="uw-plain">{fields.map(text => <li key={text}>{text}</li>)}</ul>}
      {failure.requestId && <div className="uw-hint">{t('underwriting.command.requestId', { requestId: failure.requestId })}</div>}</>} />;
}
