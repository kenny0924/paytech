import { useMemo, useState } from 'react';
import { Alert, Button, Descriptions, Form, Input, Modal, Radio, Select, Space, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { Loading, QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { canApprove, classificationIssues, decodeClassificationSet, decodeCommand, decodeDetail, decodeFileDownload,
  reviewApi, reviewRoute, returnSections, activeStatuses } from './clientReview';
import type { Application, Classification, Evidence, Requirement, ReviewDetail, Subject } from './clientReview';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';

interface EvidenceRow { key: string; req: Requirement; evidence: Evidence | null }
import { useReviewCommand } from './useReviewCommand';

interface Props { caseId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const has = (resources: ConsoleResource[], code: string) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === code);
type Decision = 'RETURN' | 'APPROVE' | 'REJECT';

/** O02 审核详情：申请资料、材料核验、行业与 MCC、结论；通过资格只看 Risk 门禁。 */
export function ClientReviewDetail({ caseId, resources, onAuthorizationChanged }: Props) {
  const t = useT('client-review'), tCommon = useT('common');
  const navigate = useNavigate();
  const decode = useMemo(() => (value: unknown) => decodeDetail(value, caseId), [caseId]);
  const detail = useDetail(`${reviewApi}/cases/${caseId}`, decode);
  const [decision, setDecision] = useState<Decision | null>(null);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const data = detail.data; const review = data.review; const active = activeStatuses.includes(review.status);
  const canReturn = active && has(resources, 'CLIENT_ONBOARDING_RETURN'); const canDecide = active && has(resources, 'CLIENT_ONBOARDING_DECIDE');
  const name = displayName(t, data.application);
  return <div className="configuration-page client-review-page">
    <div className="page-heading"><h1>{t('detail.title')}</h1><Space>
      <Button onClick={() => void navigate(reviewRoute)}>{t('detail.backToList')}</Button><Button onClick={detail.refresh} loading={detail.loading}>{tCommon('action.refresh')}</Button></Space></div>
    <section className="review-section"><h2>{name} <Tag color={review.status === 'APPROVED' ? 'green' : review.status === 'REJECTED' ? 'red' : review.status === 'RETURNED' ? 'orange' : 'blue'}>{enumLabel(t, 'enum.caseStatus', review.status)}</Tag></h2>
      <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }} items={[
        { key: 'case', label: t('field.case'), children: review.caseNo }, { key: 'app', label: t('field.application'), children: data.application?.applicationNo ?? review.applicationId },
        { key: 'type', label: t('field.clientType'), children: enumLabel(t, 'enum.qualification', review.qualificationType) },
        { key: 'submitted', label: t('field.submittedAt'), children: date(review.submittedAt) },
        ...(review.decidedAt ? [{ key: 'decided', label: t('field.decidedAt'), children: date(review.decidedAt) }] : []),
        ...(review.returnSection ? [{ key: 'section', label: t('field.returnSection'), children: enumLabel(t, 'enum.section', review.returnSection) }] : []),
        ...(review.publicNote ? [{ key: 'note', label: t('field.publicNote'), children: review.publicNote }] : []),
      ]} /></section>
    <ApplicationSection data={data} />
    <MaterialsSection caseId={caseId} subjects={review.subjects} active={active} canReview={has(resources, 'CLIENT_ONBOARDING_REVIEW_EVIDENCE')}
      onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />
    <ClassificationSection key={`${data.classification?.confirmedAt}:${data.classification?.items.length}`} data={data} active={active}
      canEdit={has(resources, 'CLIENT_ONBOARDING_CLASSIFY')} onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />
    <section className="review-section"><h2>{t('decision.title')}</h2>
      {!active ? <Alert showIcon type="info" title={t('decision.ended')} /> : <>
        {review.approvalBlockers.length > 0
          ? <Alert showIcon type="warning" title={t('decision.blocked')} description={<ul className="review-blockers">{review.approvalBlockers.map(b => <li key={b}>{enumLabel(t, 'enum.blocker', b)}</li>)}</ul>} />
          : <Alert showIcon type="success" title={t('decision.ready')} />}
        <div className="review-actions" style={{ marginTop: 12 }}>
          {canReturn && <Button onClick={() => setDecision('RETURN')}>{t('decision.return')}</Button>}
          {canDecide && <Button type="primary" disabled={!canApprove(review)} onClick={() => setDecision('APPROVE')}>{t('decision.approve')}</Button>}
          {canDecide && <Button danger onClick={() => setDecision('REJECT')}>{t('decision.reject')}</Button>}
          {!canReturn && !canDecide && <span className="muted">{t('decision.noPermission')}</span>}
        </div></>}
    </section>
    {decision && <DecisionModal caseId={caseId} decision={decision} name={name} applicationNo={data.application?.applicationNo ?? review.applicationId}
      onClose={() => setDecision(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={() => { setDecision(null); detail.refresh(); }} />}
  </div>;
}

function displayName(t: Translate, application: Application | null): string {
  if (!application) return t('detail.applicationUnavailableName');
  const org = application.values.organization; const person = application.values.individual;
  return org?.legalName || [person?.givenName, person?.familyName].filter(Boolean).join(' ') || t('detail.applicationName', { applicationNo: application.applicationNo });
}

function Values({ values }: { values: Application['values'] }) {
  const t = useT('client-review');
  const groups = Object.entries(values).filter(([group, fields]) => group !== 'contact' && Object.keys(fields).length > 0);
  if (groups.length === 0) return <span className="muted">{t('application.empty')}</span>;
  return <>{groups.map(([group, fields]) => <div key={group}><h3>{enumLabel(t, 'application.group', group)}</h3>
    <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }} items={Object.entries(fields).map(([field, value]) => ({ key: field, label: enumLabel(t, 'application.field', field), children: value ?? '—' }))} /></div>)}</>;
}

function ApplicationSection({ data }: { data: ReviewDetail }) {
  const t = useT('client-review');
  const application = data.application;
  const relationText = (relationType: string, ownershipPercentage: string | null) => ownershipPercentage
    ? t('application.relationWithOwnership', { relation: enumLabel(t, 'enum.relation', relationType), percentage: ownershipPercentage })
    : enumLabel(t, 'enum.relation', relationType);
  return <section className="review-section"><h2>{t('application.title')}</h2>
    {!application ? <Alert showIcon type="warning" title={t('application.unavailable')} description={t('application.unavailableHint')} /> : <>
      <Values values={application.values} />
      {application.people.length > 0 && <><h3>{t('application.people')}</h3>{application.people.map(person => {
        const relations = application.relations.filter(r => r.relatedPartyId === person.partyId);
        return <div key={person.partyId} className="review-subject"><div className="cell-meta">{t('application.personMeta', { partyId: person.partyId, relations: relations.map(r => relationText(r.relationType, r.ownershipPercentage)).join(t('application.listSeparator')) || t('application.noRelations') })}</div>
          <Values values={person.values} /></div>;
      })}</>}
    </>}
  </section>;
}

function MaterialsSection({ caseId, subjects, active, canReview, onAuthorizationChanged, onChanged }: {
  caseId: string; subjects: Subject[]; active: boolean; canReview: boolean; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review');
  const command = useReviewCommand(onAuthorizationChanged);
  const download = useFileDownload(caseId, onAuthorizationChanged);
  async function review(evidenceId: string, outcome: 'ACCEPTED' | 'REJECTED') {
    const result = await command.run(`${reviewApi}/cases/${caseId}/evidence/${evidenceId}/review`, 'POST', { outcome },
      value => decodeCommand(value, caseId), outcome === 'ACCEPTED' ? t('materials.accepted') : t('materials.rejected'));
    if (result) onChanged();
  }
  return <section className="review-section"><h2>{t('materials.title')}</h2>
    {command.failure?.status === 409 && <Alert showIcon type="warning" title={t('materials.conflict')} style={{ marginBottom: 12 }} />}
    {subjects.map(subject => <div key={subject.caseId} className="review-subject">
      <div><strong>{enumLabel(t, 'enum.role', subject.roleType)}</strong> <span className="cell-meta">{t('materials.subjectMeta', { partyId: subject.partyId, qualification: enumLabel(t, 'enum.qualification', subject.qualificationType) })}
        {subject.dependencyTypes.length > 0 && ` · ${subject.dependencyTypes.join(t('application.listSeparator'))}`} · {enumLabel(t, 'enum.caseStatus', subject.caseStatus)}</span></div>
      <Table<EvidenceRow> size="small" rowKey="key" pagination={false} style={{ marginTop: 8 }}
        dataSource={subject.requirements.flatMap((req): EvidenceRow[] => req.evidence.length === 0
          ? [{ key: req.requirementId, req, evidence: null }] : req.evidence.map(evidence => ({ key: evidence.evidenceId, req, evidence })))}
        columns={[
          { title: t('materials.column.requirement'), render: (_, row) => <div>{row.req.requirementCode}{row.req.required && <Tag color="blue" style={{ marginLeft: 6 }}>{t('materials.required')}</Tag>}<div className="cell-meta">{row.req.documentType}</div></div> },
          { title: t('materials.column.file'), width: 200, render: (_, row) => row.evidence
            ? <Button size="small" type="link" loading={download.pending === row.evidence.fileId}
              onClick={() => void download.open(row.evidence!.fileId)}>{t('materials.download', { fileId: row.evidence.fileId })}</Button>
            : <span className="muted">{t('materials.notSubmitted')}</span> },
          { title: t('materials.column.result'), width: 120, render: (_, row) => row.evidence ? <Tag color={row.evidence.status === 'ACCEPTED' ? 'green' : row.evidence.status === 'REJECTED' ? 'red' : 'default'}>{enumLabel(t, 'enum.evidence', row.evidence.status)}</Tag> : '—' },
          { title: t('materials.column.actions'), width: 170, render: (_, row) => row.evidence && row.evidence.status === 'RECEIVED' && active && canReview
            ? <Space><Button size="small" loading={command.saving} onClick={() => void review(row.evidence!.evidenceId, 'ACCEPTED')}>{t('materials.accept')}</Button>
              <Button size="small" danger loading={command.saving} onClick={() => void review(row.evidence!.evidenceId, 'REJECTED')}>{t('materials.reject')}</Button></Space> : null },
        ]} />
    </div>)}
  </section>;
}

function ClassificationSection({ data, active, canEdit, onAuthorizationChanged, onChanged }: {
  data: ReviewDetail; active: boolean; canEdit: boolean; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review');
  const caseId = data.review.caseId; const confirmed = data.classification?.confirmed === true;
  const [items, setItems] = useState<Classification[]>(data.classification?.items.length ? data.classification.items : [{ industryCode: '', mccCode: '', primary: true }]);
  const [dirty, setDirty] = useState(false);
  const command = useReviewCommand(onAuthorizationChanged);
  const editable = active && canEdit && !confirmed && data.classificationStatus === 'AVAILABLE' && data.industriesStatus === 'AVAILABLE';
  const issues = classificationIssues(items); const labels = Object.fromEntries(data.industries.map(o => [o.code, o.label]));
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
  return <section className="review-section"><h2>{t('classification.title')}</h2>
    {data.classificationStatus === 'UNAVAILABLE' ? <Alert showIcon type="warning" title={t('classification.unavailable')} />
      : confirmed ? <><Alert showIcon type="success" title={t('classification.confirmedAt', { time: date(data.classification!.confirmedAt) })} style={{ marginBottom: 12 }} />
        {data.classification!.items.map(item => <div key={`${item.industryCode}:${item.mccCode}`}>{t('classification.item', { industry: labels[item.industryCode] ?? item.industryCode, mcc: item.mccCode })}{item.primary && <Tag color="blue" style={{ marginLeft: 6 }}>{t('classification.primaryTag')}</Tag>}</div>)}</>
      : <>
        {data.industriesStatus === 'UNAVAILABLE' && <Alert showIcon type="warning" title={t('classification.industriesUnavailable')} style={{ marginBottom: 12 }} />}
        {items.map((item, index) => <div key={index} className="review-classification-row">
          <Select value={item.industryCode || undefined} placeholder={t('classification.industryPlaceholder')} disabled={!editable} options={data.industries.map(o => ({ value: o.code, label: o.label }))} onChange={value => update(index, { industryCode: value })} />
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
  </section>;
}

function DecisionModal({ caseId, decision, name, applicationNo, onClose, onDone, onAuthorizationChanged }: {
  caseId: string; decision: Decision; name: string; applicationNo: string; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('client-review'), tCommon = useT('common');
  const [form] = Form.useForm(); const command = useReviewCommand(onAuthorizationChanged);
  const title = decision === 'RETURN' ? t('decision.return') : decision === 'APPROVE' ? t('decision.confirmApprove') : t('decision.confirmReject');
  async function submit(values: Record<string, string>) {
    const path = `${reviewApi}/cases/${caseId}/${decision === 'RETURN' ? 'return' : decision === 'APPROVE' ? 'approval' : 'rejection'}`;
    const payload = decision === 'RETURN'
      ? { reasonCode: values.reasonCode.trim(), section: values.section, publicNote: values.publicNote.trim() }
      : { reasonCode: values.reasonCode.trim(), reason: values.reason.trim(), publicNote: values.publicNote?.trim() || null };
    const result = await command.run(path, 'POST', payload, value => decodeCommand(value, caseId),
      decision === 'RETURN' ? t('decision.returnSubmitted') : decision === 'APPROVE' ? t('decision.approveSubmitted') : t('decision.rejectSubmitted'));
    if (result) onDone();
  }
  return <Modal open title={title} footer={null} closable={!command.saving} maskClosable={!command.saving} onCancel={() => { if (!command.saving) onClose(); }}>
    <p>{t('decision.subject', { name, applicationNo })}</p>
    {decision === 'REJECT' && <Alert showIcon type="warning" title={t('decision.rejectWarning')} style={{ marginBottom: 12 }} />}
    {decision === 'APPROVE' && <Alert showIcon type="info" title={t('decision.approveHint')} style={{ marginBottom: 12 }} />}
    <Form form={form} layout="vertical" onFinish={values => void submit(values)}>
      <Form.Item name="reasonCode" label={t('decision.reasonCode')} rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: t('decision.reasonCodeInvalid') }]}>
        <Input maxLength={64} placeholder={t('decision.reasonCodePlaceholder')} /></Form.Item>
      {decision === 'RETURN' && <Form.Item name="section" label={t('decision.section')} rules={[{ required: true, message: t('decision.sectionRequired') }]}>
        <Select options={returnSections.map(value => ({ value, label: enumLabel(t, 'enum.section', value) }))} /></Form.Item>}
      {decision !== 'RETURN' && <Form.Item name="reason" label={t('decision.reason')} rules={[{ required: true, whitespace: true, message: t('decision.reasonRequired') }]}>
        <Input.TextArea rows={3} maxLength={2000} showCount /></Form.Item>}
      <Form.Item name="publicNote" label={decision === 'RETURN' ? t('field.publicNote') : t('decision.publicNoteOptional')}
        rules={decision === 'RETURN' ? [{ required: true, whitespace: true, message: t('decision.publicNoteRequired') }] : []}>
        <Input.TextArea rows={3} maxLength={1000} showCount placeholder={t('decision.publicNotePlaceholder')} /></Form.Item>
      {command.failure?.status === 422 && <Alert showIcon type="warning" title={t('decision.notEligible')} style={{ marginBottom: 12 }} />}
      {command.failure?.status === 409 && <Alert showIcon type="warning" title={t('decision.conflict')} style={{ marginBottom: 12 }} />}
      <Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" danger={decision === 'REJECT'} htmlType="submit" loading={command.saving}>{title}</Button></Space>
    </Form>
  </Modal>;
}

/**
 * 审核材料下载：每次点击向 BFF 申请最长 60 秒的原版本地址，随即以附件方式下载；地址不保存、不展示。
 * <li>403 刷新导航权限；文件尚不可用（409）等错误按统一提示展示。</li>
 */
function useFileDownload(caseId: string, onAuthorizationChanged: () => void) {
  const t = useT('client-review');
  const notify = useErrorNotification();
  const [pending, setPending] = useState<string>();
  async function open(fileId: string) {
    if (pending) return;
    setPending(fileId);
    try {
      // 无请求体的 POST 仍按 BFF 写入口要求声明 JSON 内容类型。
      const grant = await session.request(`${reviewApi}/cases/${caseId}/files/${fileId}/download`,
        { method: 'POST', headers: { 'Content-Type': 'application/json' } },
        value => decodeFileDownload(value, fileId));
      const link = document.createElement('a');
      link.href = grant.url; link.rel = 'noopener noreferrer'; link.download = grant.displayName;
      document.body.appendChild(link); link.click(); link.remove();
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      notify(error, error instanceof ApiError && error.status === 409 ? t('materials.downloadNotReady') : t('materials.downloadFailed'));
    } finally { setPending(undefined); }
  }
  return { pending, open };
}
