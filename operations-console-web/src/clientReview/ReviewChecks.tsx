import { useState } from 'react';
import { Alert, Button, Empty, Form, Input, Modal, Radio, Select, Space, Table, Tag } from 'antd';
import { date } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { checkAwaitingResult, checkOutcomes, closingResolutions, decodeCommand, hitResolutions, reviewApi, withOnBehalf } from './clientReview';
import type { Check, ReviewCase, ScreeningHit, ScreeningRun } from './clientReview';
import { CommandFailure, ReviewCommandModal, StatusDot, tone } from './reviewParts';
import { useReviewCommand } from './useReviewCommand';

const CODE = /^[A-Z][A-Z0-9_]{0,63}$/;
const subjectText = (review: ReviewCase, partyId: string | null) => {
  if (partyId === null) return null;
  return review.subjects.find(s => s.partyId === partyId) ?? null;
};

/** 人工检查：程序模板实例化的检查项；MANUAL 项由审核员录入结论，阻断项 FAIL 会阻止批准。 */
export function ChecksPanel({ review, active, canRecord, onBehalfOfPartnerId, onAuthorizationChanged, onChanged }: {
  review: ReviewCase; active: boolean; canRecord: boolean; onBehalfOfPartnerId: string | null; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review');
  const [recording, setRecording] = useState<Check | null>(null);
  const [form] = Form.useForm<{ outcome: string; reasonCode?: string; note?: string }>();
  const outcome = Form.useWatch('outcome', form);
  return <div className="obw-card"><div className="obw-card-title">{t('checks.title')}<span className="muted">{t('checks.subtitle')}</span></div>
    <Table<Check> size="small" rowKey="checkId" pagination={false} dataSource={review.checks} scroll={{ x: 900 }}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('checks.empty')} /> }} columns={[
        { title: t('checks.column.check'), render: (_, row) => <div>{enumLabel(t, 'enum.check', row.checkCode)}
          <div className="cell-meta">{[enumLabel(t, 'enum.checkMode', row.checkMode), row.blocking ? t('checks.blocking') : t('checks.nonBlocking')].join(' · ')}</div></div> },
        { title: t('checks.column.subject'), width: 200, render: (_, row) => { const subject = subjectText(review, row.subjectPartyId);
          return subject ? <div>{enumLabel(t, 'enum.role', subject.roleType)}<div className="cell-meta">{subject.partyId}</div></div> : t('checks.caseLevel'); } },
        { title: t('checks.column.result'), width: 200, render: (_, row) => <div>
          <StatusDot tone={row.outcome ? tone(row.outcome) : tone(row.status)}>{row.outcome ? enumLabel(t, 'enum.checkOutcome', row.outcome) : enumLabel(t, 'enum.checkStatus', row.status)}</StatusDot>
          {row.reasonCode && <div className="cell-meta">{row.reasonCode}</div>}
          {row.reviewedTime && <div className="cell-meta">{t('checks.reviewedMeta', { reviewer: row.reviewerId ?? '—', time: date(row.reviewedTime) })}</div>}
        </div> },
        { title: t('checks.column.note'), width: 200, ellipsis: true, render: (_, row) => row.note ?? '—' },
        { title: t('materials.column.actions'), width: 130, render: (_, row) => active && canRecord && checkAwaitingResult(row)
          ? <Button size="small" onClick={() => { form.resetFields(); setRecording(row); }}>{t('checks.record')}</Button> : null },
      ]} />
    {recording && <ReviewCommandModal<{ outcome: string; reasonCode?: string; note?: string }> form={form} title={t('checks.recordTitle')} caseId={review.caseId}
      path={`${reviewApi}/cases/${review.caseId}/checks/${recording.checkId}/result`} onBehalfOfPartnerId={onBehalfOfPartnerId}
      build={values => ({ outcome: values.outcome, reasonCode: values.reasonCode!.trim(), note: values.note?.trim() || null })} success={t('checks.recorded')}
      intro={<p>{t('checks.recordIntro', { check: enumLabel(t, 'enum.check', recording.checkCode) })}</p>}
      onClose={() => setRecording(null)} onDone={() => { setRecording(null); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged}>
      <Form.Item name="outcome" label={t('checks.outcome')} rules={[{ required: true, message: t('checks.outcomeRequired') }]}>
        <Radio.Group optionType="button" options={checkOutcomes.map(value => ({ value, label: enumLabel(t, 'enum.checkOutcome', value) }))} /></Form.Item>
      {outcome === 'FAIL' && recording.blocking && <Alert showIcon type="warning" style={{ marginBottom: 12 }} title={t('checks.blockingFail')} />}
      <Form.Item name="reasonCode" label={t('field.reasonCode')} rules={[{ required: true, pattern: CODE, message: t('decision.reasonCodeInvalid') }]}>
        <Input maxLength={64} placeholder={t('checks.reasonPlaceholder')} /></Form.Item>
      <Form.Item name="note" label={t('field.noteInternal')} rules={[{ required: outcome === 'FAIL', whitespace: true, message: t('checks.noteRequired') }]}>
        <Input.TextArea rows={3} maxLength={1000} showCount placeholder={t('checks.notePlaceholder')} /></Form.Item>
    </ReviewCommandModal>}
  </div>;
}

/** 筛查：每次执行一种筛查类型；命中逐条处置，POTENTIAL_MATCH / INSUFFICIENT_EVIDENCE 仍保持 OPEN。 */
export function ScreeningPanel({ review, active, canScreen, onBehalfOfPartnerId, onAuthorizationChanged, onChanged }: {
  review: ReviewCase; active: boolean; canScreen: boolean; onBehalfOfPartnerId: string | null; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review');
  const [resolving, setResolving] = useState<ScreeningHit | null>(null);
  const [rerun, setRerun] = useState(false);
  const [form] = Form.useForm<{ resolution: string; reasonCode?: string; note?: string }>();
  const resolution = Form.useWatch('resolution', form);
  const runs = [...review.screenings].sort((a, b) => (b.startedTime ?? '').localeCompare(a.startedTime ?? ''));
  return <div className="obw-card"><div className="obw-card-title">{t('screening.title')}<span className="muted">{t('screening.subtitle')}</span>
    <span className="obw-grow" />{active && canScreen && <Button size="small" onClick={() => setRerun(true)}>{t('screening.rerun')}</Button>}</div>
    {runs.length === 0 && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('screening.empty')} />}
    {runs.map(run => <RunBlock key={run.runId} run={run} review={review} canResolve={active && canScreen} onResolve={hit => { form.resetFields(); setResolving(hit); }} />)}
    {resolving && <ReviewCommandModal<{ resolution: string; reasonCode?: string; note?: string }> form={form} title={t('screening.resolveTitle')} caseId={review.caseId}
      path={`${reviewApi}/cases/${review.caseId}/screening-hits/${resolving.hitId}/resolution`} onBehalfOfPartnerId={onBehalfOfPartnerId}
      build={values => ({ resolution: values.resolution, reasonCode: values.reasonCode!.trim(), note: values.note?.trim() || null })} success={t('screening.resolved')}
      intro={<p>{t('screening.resolveIntro', { name: resolving.externalHitRef ?? resolving.hitId, lists: resolving.listTypes.map(l => enumLabel(t, 'enum.listType', l)).join(', ') || '—' })}</p>}
      onClose={() => setResolving(null)} onDone={() => { setResolving(null); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged}>
      <Form.Item name="resolution" label={t('screening.resolution')} rules={[{ required: true, message: t('screening.resolutionRequired') }]}>
        <Select options={hitResolutions.map(value => ({ value, label: enumLabel(t, 'enum.hitResolution', value) }))} /></Form.Item>
      {resolution && !closingResolutions.includes(resolution) && <Alert showIcon type="info" style={{ marginBottom: 12 }} title={t('screening.staysOpen')} />}
      {resolution === 'TRUE_POSITIVE_REJECT' && <Alert showIcon type="warning" style={{ marginBottom: 12 }} title={t('screening.rejectConsequence')} />}
      <Form.Item name="reasonCode" label={t('field.reasonCode')} rules={[{ required: true, pattern: CODE, message: t('decision.reasonCodeInvalid') }]}><Input maxLength={64} placeholder={t('screening.reasonPlaceholder')} /></Form.Item>
      <Form.Item name="note" label={t('field.noteInternal')} rules={[{ required: resolution?.startsWith('TRUE_POSITIVE') === true, whitespace: true, message: t('screening.noteRequired') }]}>
        <Input.TextArea rows={3} maxLength={1000} showCount placeholder={t('screening.notePlaceholder')} /></Form.Item>
    </ReviewCommandModal>}
    {rerun && <RerunModal caseId={review.caseId} onBehalfOfPartnerId={onBehalfOfPartnerId} onClose={() => setRerun(false)}
      onDone={() => { setRerun(false); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function RunBlock({ run, review, canResolve, onResolve }: { run: ScreeningRun; review: ReviewCase; canResolve: boolean; onResolve: (hit: ScreeningHit) => void }) {
  const t = useT('client-review');
  const subject = subjectText(review, run.subjectPartyId);
  return <div className="obw-run">
    <div className="obw-run-head"><strong>{enumLabel(t, 'enum.screeningType', run.screeningType)}</strong>
      <span className="cell-meta">{subject ? `${enumLabel(t, 'enum.role', subject.roleType)} · ${subject.partyId}` : t('checks.caseLevel')}</span>
      {run.providerCode && <Tag>{run.providerCode}</Tag>}
      {run.triggerSource && <span className="cell-meta">{enumLabel(t, 'enum.triggerSource', run.triggerSource)}</span>}
      <StatusDot tone={tone(run.status)}>{enumLabel(t, 'enum.screeningStatus', run.status)}</StatusDot>
      <span className="obw-grow" /><span className="cell-meta">{run.completedTime ? date(run.completedTime) : run.startedTime ? date(run.startedTime) : ''}</span></div>
    {run.hits.length === 0
      ? <div className="cell-meta obw-run-empty">{run.status === 'SUCCEEDED' ? t('screening.noHits') : t('screening.noResult')}</div>
      : <Table<ScreeningHit> size="small" rowKey="hitId" pagination={false} dataSource={run.hits} scroll={{ x: 820 }}
        expandable={{ rowExpandable: hit => hit.resolutions.length > 0, expandedRowRender: hit => <ul className="obw-plain-list">{hit.resolutions.map((r, i) =>
          <li key={i}>{t('screening.resolutionLine', { resolution: enumLabel(t, 'enum.hitResolution', r.resolution), by: r.resolvedBy, time: date(r.resolvedTime) })}
            {r.reasonCode && <span className="cell-meta"> · {r.reasonCode}</span>}{r.note && <div className="cell-meta">{r.note}</div>}</li>)}</ul> }}
        columns={[
          { title: t('screening.column.name'), render: (_, hit) => <div>{hit.externalHitRef ?? hit.hitId}<div className="cell-meta">{[enumLabel(t, 'enum.entityType', hit.entityType ?? ''), hit.matchType && enumLabel(t, 'enum.matchType', hit.matchType), hit.matchScore && t('screening.score', { score: hit.matchScore })].filter(Boolean).join(' · ')}</div></div> },
          { title: t('screening.column.lists'), width: 220, render: (_, hit) => <Space wrap size={4}>{hit.listTypes.map(l => <Tag key={l}>{enumLabel(t, 'enum.listType', l)}</Tag>)}</Space> },
          { title: t('screening.column.risk'), width: 100, render: (_, hit) => hit.riskLevel ? <StatusDot tone={tone(hit.riskLevel)}>{enumLabel(t, 'enum.riskLevel', hit.riskLevel)}</StatusDot> : '—' },
          { title: t('screening.column.status'), width: 190, render: (_, hit) => { const last = hit.resolutions.at(-1);
            return <div><StatusDot tone={tone(hit.status)}>{enumLabel(t, 'enum.hitStatus', hit.status)}</StatusDot>
              {last && <div className="cell-meta">{enumLabel(t, 'enum.hitResolution', last.resolution)}</div>}</div>; } },
          { title: t('materials.column.actions'), width: 110, render: (_, hit) => canResolve && hit.status === 'OPEN'
            ? <Button size="small" onClick={() => onResolve(hit)}>{t('screening.resolve')}</Button> : null },
        ]} />}
  </div>;
}

/** 重跑筛查：按程序的筛查项为所有主体新建执行，旧命中被取代。 */
function RerunModal({ caseId, onBehalfOfPartnerId, onClose, onDone, onAuthorizationChanged }: {
  caseId: string; onBehalfOfPartnerId: string | null; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('client-review'), tCommon = useT('common');
  const command = useReviewCommand(onAuthorizationChanged);
  async function submit() {
    const result = await command.run(`${reviewApi}/cases/${caseId}/screenings/run`, 'POST', withOnBehalf({}, onBehalfOfPartnerId),
      value => decodeCommand(value, caseId), t('screening.rerunSubmitted'));
    if (result) onDone();
  }
  return <Modal open title={t('screening.rerun')} footer={null} closable={!command.saving} maskClosable={!command.saving} onCancel={() => { if (!command.saving) onClose(); }}>
    <p>{t('screening.rerunIntro')}</p>
    <CommandFailure failure={command.failure} />
    <div className="obw-modal-foot"><Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
      <Button type="primary" loading={command.saving} onClick={() => void submit()}>{t('screening.rerunConfirm')}</Button></Space></div>
  </Modal>;
}
