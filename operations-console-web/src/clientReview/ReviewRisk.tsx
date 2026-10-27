import { useState } from 'react';
import { Alert, Button, Empty, Input, Radio, Segmented, Select, Space, Tag, Timeline } from 'antd';
import { date } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { schemaLabel } from '../onboardingConfig/schemaLabel';
import { classificationIssues, decodeClassificationSet, decodeCommand, partnerName, reviewApi, riskFactorCodes, riskRatingIssues, riskRatings, withOnBehalf } from './clientReview';
import type { Classification, ResponsiblePartner, ReviewCase, ReviewDetail } from './clientReview';
import { CommandFailure, StatusDot, tone } from './reviewParts';
import { HistoricalRound } from './ReviewEvidence';
import { useReviewCommand } from './useReviewCommand';

interface FactorDraft { factorCode: string; factorValue: string }

/** 手工 CRR：评级 + 因子必填；保存即整体替换当前评级与因子。 */
export function RiskPanel({ review, active, canRate, onBehalfOfPartnerId, onAuthorizationChanged, onChanged }: {
  review: ReviewCase; active: boolean; canRate: boolean; onBehalfOfPartnerId: string | null; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review');
  const editable = active && canRate;
  const [rating, setRating] = useState<string | undefined>(review.riskRating ?? undefined);
  const [factors, setFactors] = useState<FactorDraft[]>(review.riskFactors.length
    ? review.riskFactors.map(f => ({ factorCode: f.factorCode, factorValue: f.factorValue })) : [{ factorCode: '', factorValue: '' }]);
  const [note, setNote] = useState('');
  const [dirty, setDirty] = useState(false);
  const command = useReviewCommand(onAuthorizationChanged);
  const issues = riskRatingIssues(rating, factors, note);
  const update = (index: number, patch: Partial<FactorDraft>) => { setDirty(true); setFactors(current => current.map((f, i) => i === index ? { ...f, ...patch } : f)); };
  async function save() {
    const payload = { riskRating: rating, factors: factors.map(f => ({ factorCode: f.factorCode, factorValue: f.factorValue.trim() })), note: note.trim() };
    const result = await command.run(`${reviewApi}/cases/${review.caseId}/risk-rating`, 'PUT', withOnBehalf(payload, onBehalfOfPartnerId),
      value => decodeCommand(value, review.caseId), t('risk.saved'));
    if (result) { setDirty(false); onChanged(); }
  }
  return <div className="obw-card"><div className="obw-card-title">{t('risk.title')}<span className="muted">{t('risk.subtitle')}</span></div>
    <div className="obw-risk-current">
      <div><div className="obw-label">{t('risk.current')}</div>{review.riskRating
        ? <StatusDot tone={tone(review.riskRating)}>{enumLabel(t, 'enum.riskRating', review.riskRating)}</StatusDot> : <span className="muted">{t('risk.notRated')}</span>}</div>
      <div><div className="obw-label">{t('field.tier')}</div>{review.tier ? enumLabel(t, 'enum.tier', review.tier) : '—'}</div>
      {review.riskRatedAt && <div><div className="obw-label">{t('risk.recordedAt')}</div>{date(review.riskRatedAt)}<div className="cell-meta">{review.riskRatedBy ?? ''}</div></div>}
      {review.riskRatingNote && <div className="obw-grow"><div className="obw-label">{t('field.noteInternal')}</div>{review.riskRatingNote}</div>}
    </div>
    <div className="obw-label">{t('risk.rating')}</div>
    <Segmented disabled={!editable} value={rating} onChange={value => { setDirty(true); setRating(String(value)); }}
      options={riskRatings.map(value => ({ value, label: <span className={`obw-dot is-${tone(value)}`}>{enumLabel(t, 'enum.riskRating', value)}</span> }))} />
    <div className="obw-label" style={{ marginTop: 14 }}>{t('risk.factors')}</div>
    {factors.map((factor, index) => <div key={index} className="obw-factor-row">
      <Select disabled={!editable} value={factor.factorCode || undefined} placeholder={t('risk.factorPlaceholder')}
        options={riskFactorCodes.map(value => ({ value, label: enumLabel(t, 'enum.riskFactor', value) }))} onChange={value => update(index, { factorCode: value })} />
      <Input disabled={!editable} value={factor.factorValue} maxLength={128} placeholder={t('risk.valuePlaceholder')} onChange={event => update(index, { factorValue: event.target.value })} />
      {editable && factors.length > 1 && <Button type="link" danger onClick={() => { setDirty(true); setFactors(current => current.filter((_, i) => i !== index)); }}>{t('classification.remove')}</Button>}
    </div>)}
    {editable && <>
      <Button size="small" disabled={factors.length >= 50} onClick={() => setFactors(current => [...current, { factorCode: '', factorValue: '' }])}>{t('risk.addFactor')}</Button>
      <div className="obw-label" style={{ marginTop: 14 }}>{t('field.noteInternal')}</div>
      <Input.TextArea rows={2} maxLength={1000} value={note} onChange={event => { setDirty(true); setNote(event.target.value); }} />
      <CommandFailure failure={command.failure} />
      <div className="obw-inline-foot"><span className="cell-meta">{dirty && issues.length > 0 ? issues.join(t('classification.issueSeparator')) : dirty ? t('risk.unsaved') : ''}</span>
        <Button type="primary" disabled={!dirty || issues.length > 0} loading={command.saving} onClick={() => void save()}>{t('risk.save')}</Button></div>
    </>}
    {!editable && active && <div className="cell-meta" style={{ marginTop: 8 }}>{t('risk.noPermission')}</div>}
  </div>;
}

/** 案件时间线：compliance 状态变更与操作记录，代操作时显示 PayFac。 */
export function TimelinePanel({ review, partners }: { review: ReviewCase; partners: readonly ResponsiblePartner[] }) {
  const t = useT('client-review');
  const entries = [...review.timeline].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
  if (entries.length === 0) return <div className="obw-card"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('timeline.empty')} /></div>;
  return <div className="obw-card"><div className="obw-card-title">{t('timeline.title')}</div>
    <Timeline items={entries.map(entry => ({ key: entry.changeId, color: tone(entry.toStatus) === 'danger' ? 'red' : tone(entry.toStatus) === 'ok' ? 'green' : 'blue',
      content: <div className="obw-timeline-item">
        <div><strong>{enumLabel(t, 'enum.action', entry.actionType)}</strong>
          {entry.toStatus && <span className="cell-meta"> · {entry.fromStatus ? `${enumLabel(t, 'enum.caseStatus', entry.fromStatus)} → ` : ''}{enumLabel(t, 'enum.caseStatus', entry.toStatus)}</span>}</div>
        <div className="cell-meta">{[date(entry.occurredAt), entry.actorId ? t('timeline.actor', { actor: entry.actorId }) : t('timeline.system'),
          entry.operatedBy ? enumLabel(t, 'enum.operatedBy', entry.operatedBy) : null,
          entry.onBehalfOfPartnerId ? t('timeline.onBehalf', { partner: partnerName(partners, entry.onBehalfOfPartnerId) ?? '' }) : null,
          entry.reasonCode].filter(Boolean).join(' · ')}</div>
      </div> }))} />
  </div>;
}

/** 行业与 MCC：编辑、保存后确认；确认后不可改。归 client-master，不带代操作字段。 */
export function ClassificationPanel({ data, active, canEdit, currentCaseId, onAuthorizationChanged, onChanged }: {
  data: ReviewDetail; active: boolean; canEdit: boolean; currentCaseId?: string | null; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review');
  const caseId = data.review.caseId; const confirmed = data.classification?.confirmed === true;
  const [items, setItems] = useState<Classification[]>(data.classification?.items.length ? data.classification.items : [{ industryCode: '', mccCode: '', primary: true }]);
  const [dirty, setDirty] = useState(false);
  const command = useReviewCommand(onAuthorizationChanged);
  const editable = active && canEdit && !confirmed && data.classificationStatus === 'AVAILABLE' && data.industriesStatus === 'AVAILABLE';
  const tSchema = useT('onboarding-schema');
  const industryLabel = (o: ReviewDetail['industries'][number]) => schemaLabel(tSchema, o.labelKey, o.label ?? o.code);
  const issues = classificationIssues(items); const labels = Object.fromEntries(data.industries.map(o => [o.code, industryLabel(o)]));
  const update = (index: number, patch: Partial<Classification>) => { setDirty(true); setItems(current => current.map((item, i) => i === index ? { ...item, ...patch }
    : patch.primary ? { ...item, primary: false } : item)); };
  async function save() {
    const result = await command.run(`${reviewApi}/cases/${caseId}/classifications`, 'PUT', { items }, value => decodeClassificationSet(value, caseId), t('classification.saved'));
    if (result) { setDirty(false); onChanged(); }
  }
  async function confirm() {
    const result = await command.run(`${reviewApi}/cases/${caseId}/classification-confirmation`, 'POST', undefined, value => decodeClassificationSet(value, caseId), t('classification.confirmed'));
    if (result) onChanged();
  }
  return <div className="obw-card"><div className="obw-card-title">{t('classification.title')}</div>
    {data.classificationStatus === 'UNAVAILABLE' && currentCaseId !== undefined && currentCaseId !== caseId ? <HistoricalRound currentCaseId={currentCaseId} />
      : data.classificationStatus === 'UNAVAILABLE' ? <Alert showIcon type="warning" title={t('classification.unavailable')} />
      : confirmed ? <><div className="cell-meta" style={{ marginBottom: 8 }}><StatusDot tone="ok">{t('classification.confirmedAt', { time: date(data.classification!.confirmedAt) })}</StatusDot></div>
        {data.classification!.items.map(item => <div key={`${item.industryCode}:${item.mccCode}`}>{t('classification.item', { industry: labels[item.industryCode] ?? item.industryCode, mcc: item.mccCode })}{item.primary && <Tag color="blue" style={{ marginLeft: 6 }}>{t('classification.primaryTag')}</Tag>}</div>)}</>
      : <>
        {data.industriesStatus === 'UNAVAILABLE' && <Alert showIcon type="warning" title={t('classification.industriesUnavailable')} style={{ marginBottom: 12 }} />}
        {items.map((item, index) => <div key={index} className="obw-classification-row">
          <Select value={item.industryCode || undefined} placeholder={t('classification.industryPlaceholder')} disabled={!editable} options={data.industries.map(o => ({ value: o.code, label: industryLabel(o) }))} onChange={value => update(index, { industryCode: value })} />
          <Input value={item.mccCode} placeholder={t('classification.mccPlaceholder')} maxLength={4} disabled={!editable} onChange={event => update(index, { mccCode: event.target.value.trim() })} />
          <Radio checked={item.primary} disabled={!editable} onChange={() => update(index, { primary: true })}>{t('classification.primary')}</Radio>
          {editable && items.length > 1 && <Button size="small" type="link" danger onClick={() => { setDirty(true); setItems(current => current.filter((_, i) => i !== index)); }}>{t('classification.remove')}</Button>}
        </div>)}
        {editable && <Space wrap>
          <Button disabled={items.length >= 20} onClick={() => { setDirty(true); setItems(current => [...current, { industryCode: '', mccCode: '', primary: false }]); }}>{t('classification.add')}</Button>
          <Button type="primary" disabled={issues.length > 0} loading={command.saving} onClick={() => void save()}>{t('classification.save')}</Button>
          <Button disabled={dirty || !data.classification?.items.length} loading={command.saving} onClick={() => void confirm()}>{t('classification.confirm')}</Button>
        </Space>}
        {editable && issues.length > 0 && <div className="cell-meta" style={{ marginTop: 8 }}>{issues.join(t('classification.issueSeparator'))}</div>}
        {editable && dirty && <div className="cell-meta" style={{ marginTop: 8 }}>{t('classification.saveFirst')}</div>}
        {command.failure?.status === 409 && <Alert showIcon type="warning" title={t('classification.conflict')} style={{ marginTop: 12 }} />}
      </>}
  </div>;
}
