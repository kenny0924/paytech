import { useMemo, useState } from 'react';
import { Alert, Button, Descriptions, Form, Input, Modal, Radio, Select, Space, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { Loading, QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { blockerLabels, canApprove, caseStatusLabels, classificationIssues, decodeClassificationSet, decodeCommand, decodeDetail, decodeFileDownload, evidenceLabels,
  fieldLabels, groupLabels, qualificationLabels, relationLabels, reviewApi, reviewRoute, roleLabels, sectionLabels, activeStatuses } from './clientReview';
import type { Application, Classification, Evidence, Requirement, ReviewDetail, Subject } from './clientReview';

interface EvidenceRow { key: string; req: Requirement; evidence: Evidence | null }
import { useReviewCommand } from './useReviewCommand';

interface Props { caseId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const has = (resources: ConsoleResource[], code: string) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === code);
type Decision = 'RETURN' | 'APPROVE' | 'REJECT';

/** O02 审核详情：申请资料、材料核验、行业与 MCC、结论；通过资格只看 Risk 门禁。 */
export function ClientReviewDetail({ caseId, resources, onAuthorizationChanged }: Props) {
  const navigate = useNavigate();
  const decode = useMemo(() => (value: unknown) => decodeDetail(value, caseId), [caseId]);
  const detail = useDetail(`${reviewApi}/cases/${caseId}`, decode);
  const [decision, setDecision] = useState<Decision | null>(null);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const data = detail.data; const review = data.review; const active = activeStatuses.includes(review.status);
  const canReturn = active && has(resources, 'CLIENT_ONBOARDING_RETURN'); const canDecide = active && has(resources, 'CLIENT_ONBOARDING_DECIDE');
  const name = displayName(data.application);
  return <div className="configuration-page client-review-page">
    <div className="page-heading"><h1>入驻审核详情</h1><Space>
      <Button onClick={() => void navigate(reviewRoute)}>返回列表</Button><Button onClick={detail.refresh} loading={detail.loading}>刷新</Button></Space></div>
    <section className="review-section"><h2>{name} <Tag color={review.status === 'APPROVED' ? 'green' : review.status === 'REJECTED' ? 'red' : review.status === 'RETURNED' ? 'orange' : 'blue'}>{caseStatusLabels[review.status] ?? review.status}</Tag></h2>
      <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }} items={[
        { key: 'case', label: '审核任务', children: review.caseNo }, { key: 'app', label: '申请', children: data.application?.applicationNo ?? review.applicationId },
        { key: 'type', label: '客户类型', children: qualificationLabels[review.qualificationType] ?? review.qualificationType },
        { key: 'submitted', label: '提交时间', children: date(review.submittedAt) },
        ...(review.decidedAt ? [{ key: 'decided', label: '结束时间', children: date(review.decidedAt) }] : []),
        ...(review.returnSection ? [{ key: 'section', label: '退回分区', children: sectionLabels[review.returnSection] }] : []),
        ...(review.publicNote ? [{ key: 'note', label: '客户可见说明', children: review.publicNote }] : []),
      ]} /></section>
    <ApplicationSection data={data} />
    <MaterialsSection caseId={caseId} subjects={review.subjects} active={active} canReview={has(resources, 'CLIENT_ONBOARDING_REVIEW_EVIDENCE')}
      onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />
    <ClassificationSection key={`${data.classification?.confirmedAt}:${data.classification?.items.length}`} data={data} active={active}
      canEdit={has(resources, 'CLIENT_ONBOARDING_CLASSIFY')} onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />
    <section className="review-section"><h2>审核结论</h2>
      {!active ? <Alert showIcon type="info" title="审核任务已结束，不能再提交结论。" /> : <>
        {review.approvalBlockers.length > 0
          ? <Alert showIcon type="warning" title="暂不能通过" description={<ul className="review-blockers">{review.approvalBlockers.map(b => <li key={b}>{blockerLabels[b] ?? b}</li>)}</ul>} />
          : <Alert showIcon type="success" title="必需材料已核验、分类已确认，可以提交结论" />}
        <div className="review-actions" style={{ marginTop: 12 }}>
          {canReturn && <Button onClick={() => setDecision('RETURN')}>退回修改</Button>}
          {canDecide && <Button type="primary" disabled={!canApprove(review)} onClick={() => setDecision('APPROVE')}>通过</Button>}
          {canDecide && <Button danger onClick={() => setDecision('REJECT')}>最终拒绝</Button>}
          {!canReturn && !canDecide && <span className="muted">没有提交结论的权限</span>}
        </div></>}
    </section>
    {decision && <DecisionModal caseId={caseId} decision={decision} name={name} applicationNo={data.application?.applicationNo ?? review.applicationId}
      onClose={() => setDecision(null)} onAuthorizationChanged={onAuthorizationChanged} onDone={() => { setDecision(null); detail.refresh(); }} />}
  </div>;
}

function displayName(application: Application | null): string {
  if (!application) return '申请资料暂不可用';
  const org = application.values.organization; const person = application.values.individual;
  return org?.legalName || [person?.givenName, person?.familyName].filter(Boolean).join(' ') || `申请 ${application.applicationNo}`;
}

function Values({ values }: { values: Application['values'] }) {
  const groups = Object.entries(values).filter(([group, fields]) => group !== 'contact' && Object.keys(fields).length > 0);
  if (groups.length === 0) return <span className="muted">未填写</span>;
  return <>{groups.map(([group, fields]) => <div key={group}><h3>{groupLabels[group] ?? group}</h3>
    <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }} items={Object.entries(fields).map(([field, value]) => ({ key: field, label: fieldLabels[field] ?? field, children: value ?? '—' }))} /></div>)}</>;
}

function ApplicationSection({ data }: { data: ReviewDetail }) {
  const application = data.application;
  return <section className="review-section"><h2>申请资料</h2>
    {!application ? <Alert showIcon type="warning" title="申请资料暂时无法读取" description="CCT 当前不可用或该任务已不是申请的当前任务；请稍后刷新，不要据此判断资料为空。" /> : <>
      <Values values={application.values} />
      {application.people.length > 0 && <><h3>相关人员</h3>{application.people.map(person => {
        const relations = application.relations.filter(r => r.relatedPartyId === person.partyId);
        return <div key={person.partyId} className="review-subject"><div className="cell-meta">人员 {person.partyId} · {relations.map(r => `${relationLabels[r.relationType] ?? r.relationType}${r.ownershipPercentage ? ` ${r.ownershipPercentage}%` : ''}`).join('、') || '无有效关系'}</div>
          <Values values={person.values} /></div>;
      })}</>}
    </>}
  </section>;
}

function MaterialsSection({ caseId, subjects, active, canReview, onAuthorizationChanged, onChanged }: {
  caseId: string; subjects: Subject[]; active: boolean; canReview: boolean; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const command = useReviewCommand(onAuthorizationChanged);
  const download = useFileDownload(caseId, onAuthorizationChanged);
  async function review(evidenceId: string, outcome: 'ACCEPTED' | 'REJECTED') {
    const result = await command.run(`${reviewApi}/cases/${caseId}/evidence/${evidenceId}/review`, 'POST', { outcome },
      value => decodeCommand(value, caseId), outcome === 'ACCEPTED' ? '已接受该材料' : '已标记为未接受');
    if (result) onChanged();
  }
  return <section className="review-section"><h2>材料核验</h2>
    {command.failure?.status === 409 && <Alert showIcon type="warning" title="材料状态已变化，请刷新后核对。" style={{ marginBottom: 12 }} />}
    {subjects.map(subject => <div key={subject.caseId} className="review-subject">
      <div><strong>{roleLabels[subject.roleType] ?? subject.roleType}</strong> <span className="cell-meta">主体 {subject.partyId} · {qualificationLabels[subject.qualificationType] ?? subject.qualificationType}
        {subject.dependencyTypes.length > 0 && ` · ${subject.dependencyTypes.join('、')}`} · {caseStatusLabels[subject.caseStatus] ?? subject.caseStatus}</span></div>
      <Table<EvidenceRow> size="small" rowKey="key" pagination={false} style={{ marginTop: 8 }}
        dataSource={subject.requirements.flatMap((req): EvidenceRow[] => req.evidence.length === 0
          ? [{ key: req.requirementId, req, evidence: null }] : req.evidence.map(evidence => ({ key: evidence.evidenceId, req, evidence })))}
        columns={[
          { title: '材料要求', render: (_, row) => <div>{row.req.requirementCode}{row.req.required && <Tag color="blue" style={{ marginLeft: 6 }}>必需</Tag>}<div className="cell-meta">{row.req.documentType}</div></div> },
          { title: '文件', width: 200, render: (_, row) => row.evidence
            ? <Button size="small" type="link" loading={download.pending === row.evidence.fileId}
              onClick={() => void download.open(row.evidence!.fileId)}>下载文件 {row.evidence.fileId}</Button>
            : <span className="muted">未提交</span> },
          { title: '核验结果', width: 120, render: (_, row) => row.evidence ? <Tag color={row.evidence.status === 'ACCEPTED' ? 'green' : row.evidence.status === 'REJECTED' ? 'red' : 'default'}>{evidenceLabels[row.evidence.status] ?? row.evidence.status}</Tag> : '—' },
          { title: '操作', width: 170, render: (_, row) => row.evidence && row.evidence.status === 'RECEIVED' && active && canReview
            ? <Space><Button size="small" loading={command.saving} onClick={() => void review(row.evidence!.evidenceId, 'ACCEPTED')}>接受</Button>
              <Button size="small" danger loading={command.saving} onClick={() => void review(row.evidence!.evidenceId, 'REJECTED')}>不接受</Button></Space> : null },
        ]} />
    </div>)}
  </section>;
}

function ClassificationSection({ data, active, canEdit, onAuthorizationChanged, onChanged }: {
  data: ReviewDetail; active: boolean; canEdit: boolean; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const caseId = data.review.caseId; const confirmed = data.classification?.confirmed === true;
  const [items, setItems] = useState<Classification[]>(data.classification?.items.length ? data.classification.items : [{ industryCode: '', mccCode: '', primary: true }]);
  const [dirty, setDirty] = useState(false);
  const command = useReviewCommand(onAuthorizationChanged);
  const editable = active && canEdit && !confirmed && data.classificationStatus === 'AVAILABLE' && data.industriesStatus === 'AVAILABLE';
  const issues = classificationIssues(items); const labels = Object.fromEntries(data.industries.map(o => [o.code, o.label]));
  const update = (index: number, patch: Partial<Classification>) => { setDirty(true); setItems(current => current.map((item, i) => i === index ? { ...item, ...patch }
    : patch.primary ? { ...item, primary: false } : item)); };
  async function save() {
    const result = await command.run(`${reviewApi}/cases/${caseId}/classifications`, 'PUT', { items }, value => decodeClassificationSet(value, caseId), '分类已保存');
    if (result) { setDirty(false); onChanged(); }
  }
  async function confirm() {
    const result = await command.run(`${reviewApi}/cases/${caseId}/classification-confirmation`, 'POST', undefined, value => decodeClassificationSet(value, caseId), '分类已确认并交给 Risk');
    if (result) onChanged();
  }
  return <section className="review-section"><h2>行业与 MCC</h2>
    {data.classificationStatus === 'UNAVAILABLE' ? <Alert showIcon type="warning" title="分类暂时无法读取，请稍后刷新。" />
      : confirmed ? <><Alert showIcon type="success" title={`已确认（${date(data.classification!.confirmedAt)}），确认后不能修改。`} style={{ marginBottom: 12 }} />
        {data.classification!.items.map(item => <div key={`${item.industryCode}:${item.mccCode}`}>{labels[item.industryCode] ?? item.industryCode} · MCC {item.mccCode}{item.primary && <Tag color="blue" style={{ marginLeft: 6 }}>主要</Tag>}</div>)}</>
      : <>
        {data.industriesStatus === 'UNAVAILABLE' && <Alert showIcon type="warning" title="行业目录暂时无法读取，不能编辑分类。" style={{ marginBottom: 12 }} />}
        {items.map((item, index) => <div key={index} className="review-classification-row">
          <Select value={item.industryCode || undefined} placeholder="选择行业" disabled={!editable} options={data.industries.map(o => ({ value: o.code, label: o.label }))} onChange={value => update(index, { industryCode: value })} />
          <Input value={item.mccCode} placeholder="四位 MCC" maxLength={4} disabled={!editable} onChange={event => update(index, { mccCode: event.target.value.trim() })} />
          <Radio checked={item.primary} disabled={!editable} onChange={() => update(index, { primary: true })}>主要分类</Radio>
          {editable && items.length > 1 && <Button size="small" type="link" danger onClick={() => { setDirty(true); setItems(current => current.filter((_, i) => i !== index)); }}>删除</Button>}
        </div>)}
        {editable && <Space wrap>
          <Button disabled={items.length >= 20} onClick={() => { setDirty(true); setItems(current => [...current, { industryCode: '', mccCode: '', primary: false }]); }}>添加分类</Button>
          <Button type="primary" disabled={issues.length > 0} loading={command.saving} onClick={() => void save()}>保存分类</Button>
          <Button disabled={dirty || !data.classification?.items.length} loading={command.saving} onClick={() => void confirm()}>确认分类</Button>
        </Space>}
        {editable && issues.length > 0 && <div className="cell-meta" style={{ marginTop: 8 }}>{issues.join('；')}</div>}
        {editable && dirty && <div className="cell-meta" style={{ marginTop: 8 }}>请先保存再确认。</div>}
        {command.failure?.status === 409 && <Alert showIcon type="warning" title="分类已被确认或申请状态已变化，请刷新后核对。" style={{ marginTop: 12 }} />}
      </>}
  </section>;
}

function DecisionModal({ caseId, decision, name, applicationNo, onClose, onDone, onAuthorizationChanged }: {
  caseId: string; decision: Decision; name: string; applicationNo: string; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm(); const command = useReviewCommand(onAuthorizationChanged);
  const title = decision === 'RETURN' ? '退回修改' : decision === 'APPROVE' ? '确认通过' : '确认最终拒绝';
  async function submit(values: Record<string, string>) {
    const path = `${reviewApi}/cases/${caseId}/${decision === 'RETURN' ? 'return' : decision === 'APPROVE' ? 'approval' : 'rejection'}`;
    const payload = decision === 'RETURN'
      ? { reasonCode: values.reasonCode.trim(), section: values.section, publicNote: values.publicNote.trim() }
      : { reasonCode: values.reasonCode.trim(), reason: values.reason.trim(), publicNote: values.publicNote?.trim() || null };
    const result = await command.run(path, 'POST', payload, value => decodeCommand(value, caseId), `${title}已提交`);
    if (result) onDone();
  }
  return <Modal open title={title} footer={null} closable={!command.saving} maskClosable={!command.saving} onCancel={() => { if (!command.saving) onClose(); }}>
    <p>客户：{name} · 申请 {applicationNo}</p>
    {decision === 'REJECT' && <Alert showIcon type="warning" title="最终拒绝后申请不能再修改，客户需要重新申请。" style={{ marginBottom: 12 }} />}
    {decision === 'APPROVE' && <Alert showIcon type="info" title="通过后主任务与关联人员任务一起结束，客户工作区随后开通。" style={{ marginBottom: 12 }} />}
    <Form form={form} layout="vertical" onFinish={values => void submit(values)}>
      <Form.Item name="reasonCode" label="原因代码（内部）" rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: '大写字母开头，只含大写字母、数字和下划线' }]}>
        <Input maxLength={64} placeholder="例如 ADDRESS_UNCLEAR" /></Form.Item>
      {decision === 'RETURN' && <Form.Item name="section" label="需要修改的分区" rules={[{ required: true, message: '请选择分区' }]}>
        <Select options={Object.entries(sectionLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>}
      {decision !== 'RETURN' && <Form.Item name="reason" label="审核理由（内部，客户不可见）" rules={[{ required: true, whitespace: true, message: '请填写审核理由' }]}>
        <Input.TextArea rows={3} maxLength={2000} showCount /></Form.Item>}
      <Form.Item name="publicNote" label={decision === 'RETURN' ? '客户可见说明' : '客户可见说明（可选）'}
        rules={decision === 'RETURN' ? [{ required: true, whitespace: true, message: '请填写客户可见说明' }] : []}>
        <Input.TextArea rows={3} maxLength={1000} showCount placeholder="只写适合向客户公开的内容" /></Form.Item>
      {command.failure?.status === 422 && <Alert showIcon type="warning" title="审核尚未满足通过条件，请关闭后刷新核对。" style={{ marginBottom: 12 }} />}
      {command.failure?.status === 409 && <Alert showIcon type="warning" title="任务已被其他操作改变或已结束，请关闭后刷新核对；填写内容已保留。" style={{ marginBottom: 12 }} />}
      <Space><Button disabled={command.saving} onClick={onClose}>取消</Button>
        <Button type="primary" danger={decision === 'REJECT'} htmlType="submit" loading={command.saving}>{title}</Button></Space>
    </Form>
  </Modal>;
}

/**
 * 审核材料下载：每次点击向 BFF 申请最长 60 秒的原版本地址，随即以附件方式下载；地址不保存、不展示。
 * <li>403 刷新导航权限；文件尚不可用（409）等错误按统一提示展示。</li>
 */
function useFileDownload(caseId: string, onAuthorizationChanged: () => void) {
  const notify = useErrorNotification();
  const [pending, setPending] = useState<string>();
  async function open(fileId: string) {
    if (pending) return;
    setPending(fileId);
    try {
      const grant = await session.request(`${reviewApi}/cases/${caseId}/files/${fileId}/download`, { method: 'POST' },
        value => decodeFileDownload(value, fileId));
      const link = document.createElement('a');
      link.href = grant.url; link.rel = 'noopener noreferrer'; link.download = grant.displayName;
      document.body.appendChild(link); link.click(); link.remove();
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      notify(error, error instanceof ApiError && error.status === 409 ? '文件尚未通过检查，暂不可下载' : '文件下载未完成');
    } finally { setPending(undefined); }
  }
  return { pending, open };
}
