import { useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Table } from 'antd';
import { date } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { activeStatuses, canApprove, decodeCommand, fourEyesBlocked, isNotReady, recommendations, rejectionBlocker, unmetConditions, remediationCodes, requirementKeyPattern, returnDueDays,
  returnErrorCodes, returnErrorIssues, returnPayload, reviewApi, suggestedReturnErrors, withOnBehalf } from './clientReview';
import type { ReturnErrorDraft, ReviewCase } from './clientReview';
import { CommandFailure, ReviewCommandModal, StatusDot, tone } from './reviewParts';
import { useReviewCommand } from './useReviewCommand';

const CODE = /^[A-Z][A-Z0-9_]{0,63}$/;
type Decision = 'APPROVE' | 'REJECT';
/** 门禁项对应的工作台分区，用于“去处理”。 */
const blockerTab: Record<string, string> = {
  EVIDENCE_PENDING: 'materials', REQUIRED_MATERIAL_NOT_ACCEPTED: 'materials', CHECK_NOT_PASSED: 'checks', SCREENING_INCOMPLETE: 'screening',
  SCREENING_HIT_OPEN: 'screening', SCREENING_TRUE_MATCH: 'screening', CLASSIFICATION_NOT_RECEIVED: 'classification', RISK_RATING_MISSING: 'risk',
};

/**
 * 右侧决定面板：批准门禁（CP-6 以 compliance 为准）、CRR 摘要、推荐与四眼、退回 / 批准 / 拒绝
 * <li>门禁项可跳到对应分区；四眼时推荐人不能做最终决定（按钮禁用并说明原因），compliance 仍会以 422 拒绝。</li>
 */
export function DecisionPanel({ review, actorId, name, canRecommend, canReturn, canDecide, onBehalfOfPartnerId, onOpenTab, onAuthorizationChanged, onChanged }: {
  review: ReviewCase; actorId: string; name: string; canRecommend: boolean; canReturn: boolean; canDecide: boolean; onBehalfOfPartnerId: string | null;
  onOpenTab: (tab: string) => void; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review');
  const active = activeStatuses.includes(review.status);
  const [recommending, setRecommending] = useState<string | null>(null);
  const [decision, setDecision] = useState<Decision | null>(null);
  const [returning, setReturning] = useState(false);
  const blocked = fourEyesBlocked(review, actorId);
  const rejectBlocker = rejectionBlocker(review, actorId);
  return <aside className="obw-decision">
    <div className="obw-card"><div className="obw-card-title">{t('gate.title')}</div>
      {!active ? <StatusDot tone={tone(review.status)}>{t('decision.ended')}</StatusDot>
        : review.approvalBlockers.length === 0 ? <StatusDot tone="ok">{t('gate.ready')}</StatusDot>
          : <ul className="obw-gate">{review.approvalBlockers.map(code => <li key={code}><span className="obw-dot is-warn">{enumLabel(t, 'enum.blocker', code)}</span>
            {blockerTab[code] && <Button size="small" type="link" onClick={() => onOpenTab(blockerTab[code])}>{t('gate.open')}</Button>}</li>)}</ul>}
    </div>
    <div className="obw-card"><div className="obw-card-title">{t('risk.title')}</div>
      <div className="obw-kv"><span>{t('risk.current')}</span>{review.riskRating
        ? <StatusDot tone={tone(review.riskRating)}>{enumLabel(t, 'enum.riskRating', review.riskRating)}</StatusDot>
        : <Button size="small" type="link" onClick={() => onOpenTab('risk')}>{t('risk.rateNow')}</Button>}</div>
      <div className="obw-kv"><span>{t('field.tier')}</span>{review.tier ? enumLabel(t, 'enum.tier', review.tier) : '—'}</div>
      <div className="obw-kv"><span>{t('risk.factors')}</span>{review.riskFactors.length}</div>
    </div>
    <div className="obw-card"><div className="obw-card-title">{t('recommendation.title')}</div>
      {review.recommendation ? <div><StatusDot tone={review.recommendation === 'APPROVE' ? 'ok' : 'danger'}>{enumLabel(t, 'enum.recommendation', review.recommendation)}</StatusDot>
        <div className="cell-meta">{t('recommendation.by', { actor: review.recommendedBy === actorId ? t('recommendation.you') : review.recommendedBy ?? '—', time: date(review.recommendedTime) })}</div>
        {review.recommendationNote && <div className="obw-note">{review.recommendationNote}</div>}</div>
        : <span className="muted">{t('recommendation.none')}</span>}
      {review.fourEyesRequired && <div className="cell-meta obw-four-eyes">{blocked ? t('recommendation.fourEyesBlocked') : t('recommendation.fourEyes')}</div>}
      {active && canRecommend && <Space style={{ marginTop: 10 }} wrap>{recommendations.map(value =>
        <Button key={value} size="small" onClick={() => setRecommending(value)}>{enumLabel(t, 'recommendation.action', value)}</Button>)}</Space>}
    </div>
    {active && <div className="obw-card obw-decision-actions"><div className="obw-card-title">{t('decision.title')}</div>
      {canDecide && <Button type="primary" block disabled={!canApprove(review, actorId)} onClick={() => setDecision('APPROVE')}>{t('decision.approve')}</Button>}
      {canDecide && !canApprove(review, actorId) && <div className="cell-meta">{blocked ? t('recommendation.fourEyesBlocked') : t('decision.blocked')}</div>}
      {canReturn && <Button block onClick={() => setReturning(true)}>{t('decision.return')}</Button>}
      {canDecide && <Button danger block disabled={rejectBlocker !== null} onClick={() => setDecision('REJECT')}>{t('decision.reject')}</Button>}
      {canDecide && rejectBlocker && <div className="cell-meta">{enumLabel(t, 'enum.blocker', rejectBlocker)}</div>}
      {!canReturn && !canDecide && <span className="muted">{t('decision.noPermission')}</span>}
    </div>}
    {recommending && <ReviewCommandModal<{ note?: string }> title={enumLabel(t, 'recommendation.action', recommending)} caseId={review.caseId}
      path={`${reviewApi}/cases/${review.caseId}/recommendation`} onBehalfOfPartnerId={onBehalfOfPartnerId}
      build={values => ({ recommendation: recommending, note: values.note!.trim() })} success={t('recommendation.saved')}
      intro={<p>{t('recommendation.intro', { name })}</p>}
      onClose={() => setRecommending(null)} onDone={() => { setRecommending(null); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged}>
      <Form.Item name="note" label={t('field.noteInternal')} rules={[{ required: true, whitespace: true, message: t('recommendation.noteRequired') }]}>
        <Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
    </ReviewCommandModal>}
    {decision && <DecisionModal review={review} decision={decision} name={name} actorId={actorId} onBehalfOfPartnerId={onBehalfOfPartnerId}
      onClose={() => setDecision(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={() => { setDecision(null); onChanged(); }}
      onRefresh={onChanged} onOpenTab={tab => { setDecision(null); onOpenTab(tab); }} />}
    {returning && <ReturnModal review={review} name={name} onBehalfOfPartnerId={onBehalfOfPartnerId} onClose={() => setReturning(false)}
      onAuthorizationChanged={onAuthorizationChanged} onDone={() => { setReturning(false); onChanged(); }} />}
  </aside>;
}

/**
 * 批准 / 最终拒绝：内部原因与可选客户可见说明
 * <li>422 CLIENT_REVIEW_NOT_READY 时刷新案件，按刷新后的 approvalBlockers 逐项列出未满足条件并可跳到对应分区；输入保留。</li>
 */
function DecisionModal({ review, decision, name, actorId, onBehalfOfPartnerId, onClose, onDone, onAuthorizationChanged, onRefresh, onOpenTab }: {
  review: ReviewCase; decision: Decision; name: string; actorId: string; onBehalfOfPartnerId: string | null; onClose: () => void; onDone: () => void;
  onAuthorizationChanged: () => void; onRefresh: () => void; onOpenTab: (tab: string) => void;
}) {
  const t = useT('client-review');
  const unmet = unmetConditions(review, decision, actorId);
  const title = decision === 'APPROVE' ? t('decision.confirmApprove') : t('decision.confirmReject');
  return <ReviewCommandModal<{ reasonCode: string; reason: string; publicNote?: string }> title={title} danger={decision === 'REJECT'} caseId={review.caseId}
    path={`${reviewApi}/cases/${review.caseId}/${decision === 'APPROVE' ? 'approval' : 'rejection'}`} onBehalfOfPartnerId={onBehalfOfPartnerId}
    build={values => ({ reasonCode: values.reasonCode.trim(), reason: values.reason.trim(), publicNote: values.publicNote?.trim() || null })}
    success={decision === 'APPROVE' ? t('decision.approveSubmitted') : t('decision.rejectSubmitted')}
    intro={<><p>{t('decision.subject', { name, applicationNo: review.applicationId })}</p>
      {decision === 'REJECT' && <Alert showIcon type="warning" title={t('decision.rejectWarning')} style={{ marginBottom: 12 }} />}
      {decision === 'APPROVE' && <div className="obw-freeze">{t('decision.freezes', { tier: review.tier ? enumLabel(t, 'enum.tier', review.tier) : '—',
        rating: review.riskRating ? enumLabel(t, 'enum.riskRating', review.riskRating) : '—', jurisdiction: review.jurisdictionCode ?? '—' })}</div>}</>}
    onClose={onClose} onDone={onDone} onAuthorizationChanged={onAuthorizationChanged}
    onFailure={failure => { if (isNotReady(failure)) onRefresh(); }}
    renderFailure={failure => isNotReady(failure) ? <NotReady unmet={unmet} onOpenTab={onOpenTab} /> : undefined}>
    <Form.Item name="reasonCode" label={t('decision.reasonCode')} rules={[{ required: true, pattern: CODE, message: t('decision.reasonCodeInvalid') }]}>
      <Input maxLength={64} placeholder={t('decision.reasonCodePlaceholder')} /></Form.Item>
    <Form.Item name="reason" label={t('decision.reason')} rules={[{ required: true, whitespace: true, message: t('decision.reasonRequired') }]}>
      <Input.TextArea rows={3} maxLength={2000} showCount /></Form.Item>
    <Form.Item name="publicNote" label={t('decision.publicNoteOptional')}><Input.TextArea rows={2} maxLength={1000} showCount placeholder={t('decision.publicNotePlaceholder')} /></Form.Item>
  </ReviewCommandModal>;
}

/** 未满足条件：已刷新的案件门禁逐项列出，可跳到对应分区；刷新尚未返回新项时提示稍候。 */
function NotReady({ unmet, onOpenTab }: { unmet: string[]; onOpenTab: (tab: string) => void }) {
  const t = useT('client-review');
  return <div className="obw-notice" style={{ marginBottom: 12 }}><div><strong>{t('decision.notReadyTitle')}</strong></div>
    {unmet.length === 0 ? <div className="cell-meta">{t('decision.notReadyRefreshing')}</div>
      : <ul className="obw-gate">{unmet.map(code => <li key={code}><span className="obw-dot is-warn">{enumLabel(t, 'enum.blocker', code)}</span>
        {blockerTab[code] && <Button size="small" type="link" onClick={() => onOpenTab(blockerTab[code])}>{t('gate.open')}</Button>}</li>)}</ul>}
  </div>;
}

/**
 * 退回：逐条需求级错误（要求键 + 主体 + 错误码 + 补救 + 原因）+ 补件期限 + 客户可见说明
 * <li>“从被拒材料生成”按 document.&lt;requirementCode&gt; 填入草稿；Client 门户按 requirementKey 定位到字段或材料槽。</li>
 */
function ReturnModal({ review, name, onBehalfOfPartnerId, onClose, onDone, onAuthorizationChanged }: {
  review: ReviewCase; name: string; onBehalfOfPartnerId: string | null; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('client-review'), tCommon = useT('common');
  const [drafts, setDrafts] = useState<ReturnErrorDraft[]>(() => suggestedReturnErrors(review));
  const [dueDays, setDueDays] = useState(returnDueDays.initial);
  const [publicNote, setPublicNote] = useState('');
  const [reasonCode, setReasonCode] = useState('');
  const command = useReviewCommand(onAuthorizationChanged);
  const issues = [...returnErrorIssues(drafts), ...(CODE.test(reasonCode.trim()) ? [] : [t('return.reasonCodeRequired')]), ...(publicNote.trim() ? [] : [t('decision.publicNoteRequired')])];
  const keyOptions = [...new Set(review.subjects.flatMap(s => s.requirements.map(r => `document.${r.requirementCode}`)))].map(value => ({ value }));
  const subjects = review.subjects.map(s => ({ value: s.partyId, label: `${enumLabel(t, 'enum.role', s.roleType)} · ${s.partyId}` }));
  const update = (index: number, patch: Partial<ReturnErrorDraft>) => setDrafts(current => current.map((d, i) => i === index ? { ...d, ...patch } : d));
  async function submit() {
    const result = await command.run(`${reviewApi}/cases/${review.caseId}/return`, 'POST', withOnBehalf(returnPayload(drafts, dueDays, publicNote, reasonCode), onBehalfOfPartnerId),
      value => decodeCommand(value, review.caseId), t('decision.returnSubmitted'));
    if (result) onDone();
  }
  return <Modal open width={1040} title={t('return.title')} footer={null} closable={!command.saving} maskClosable={false} onCancel={() => { if (!command.saving) onClose(); }}>
    <p>{t('return.intro', { name })}</p>
    <div className="obw-return-toolbar"><Button size="small" onClick={() => setDrafts(suggestedReturnErrors(review))}>{t('return.fromRejected')}</Button>
      <Button size="small" onClick={() => setDrafts(current => [...current, { requirementKey: '', subjectPartyId: review.subjects[0]?.partyId ?? null, code: '', remediation: 'PROVIDE_FIELD', reason: '' }])}>{t('return.add')}</Button>
      <span className="obw-grow" /><span className="cell-meta">{t('return.count', { count: drafts.length })}</span></div>
    <Table<ReturnErrorDraft & { index: number }> size="small" rowKey="index" pagination={false} scroll={{ x: 980, y: 320 }}
      dataSource={drafts.map((d, index) => ({ ...d, index }))} locale={{ emptyText: t('return.issue.empty') }} columns={[
        { title: t('return.column.requirement'), width: 230, render: (_, row) => <Select showSearch mode={undefined} value={row.requirementKey || undefined} style={{ width: '100%' }}
          status={row.requirementKey && !requirementKeyPattern.test(row.requirementKey) ? 'error' : undefined} placeholder={t('return.keyPlaceholder')}
          options={[...keyOptions, ...(row.requirementKey && !keyOptions.some(o => o.value === row.requirementKey) ? [{ value: row.requirementKey }] : [])]}
          onSearch={value => { if (value) update(row.index, { requirementKey: value.trim() }); }} onChange={value => update(row.index, { requirementKey: value })} /> },
        { title: t('return.column.subject'), width: 190, render: (_, row) => <Select allowClear value={row.subjectPartyId ?? undefined} style={{ width: '100%' }}
          placeholder={t('return.applicationLevel')} options={subjects} onChange={value => update(row.index, { subjectPartyId: value ?? null })} /> },
        { title: t('return.column.code'), width: 200, render: (_, row) => <Select showSearch value={row.code || undefined} style={{ width: '100%' }} optionFilterProp="label"
          options={returnErrorCodes.map(value => ({ value, label: enumLabel(t, 'enum.errorCode', value) }))} onChange={value => update(row.index, { code: value })} /> },
        { title: t('return.column.remediation'), width: 160, render: (_, row) => <Select value={row.remediation} style={{ width: '100%' }}
          options={remediationCodes.map(value => ({ value, label: enumLabel(t, 'enum.remediation', value) }))} onChange={value => update(row.index, { remediation: value })} /> },
        { title: t('return.column.reason'), render: (_, row) => <Input value={row.reason} maxLength={1000} placeholder={t('return.reasonPlaceholder')} onChange={event => update(row.index, { reason: event.target.value })} /> },
        { title: '', width: 60, render: (_, row) => <Button size="small" type="link" danger onClick={() => setDrafts(current => current.filter((_, i) => i !== row.index))}>{t('classification.remove')}</Button> },
      ]} />
    <div className="obw-return-meta">
      <div><div className="obw-label">{t('return.dueDays')}</div><InputNumber min={returnDueDays.min} max={returnDueDays.max} precision={0} value={dueDays}
        onChange={value => setDueDays(value ?? returnDueDays.initial)} /> <span className="cell-meta">{t('return.dueHint', { days: dueDays })}</span></div>
      <div><div className="obw-label">{t('decision.reasonCode')}</div><Input value={reasonCode} maxLength={64} placeholder={t('decision.reasonCodePlaceholder')}
        onChange={event => setReasonCode(event.target.value.toUpperCase())} /></div>
      <div className="obw-grow"><div className="obw-label">{t('field.publicNote')}</div>
        <Input.TextArea rows={2} maxLength={1000} showCount value={publicNote} placeholder={t('decision.publicNotePlaceholder')} onChange={event => setPublicNote(event.target.value)} /></div>
    </div>
    <CommandFailure failure={command.failure} />
    <div className="obw-modal-foot"><span className="cell-meta">{issues.join(t('classification.issueSeparator'))}</span>
      <Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" disabled={issues.length > 0} loading={command.saving} onClick={() => void submit()}>{t('return.submit', { count: drafts.length })}</Button></Space></div>
  </Modal>;
}
