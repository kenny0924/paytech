import { useState } from 'react';
import { Alert, Button, Descriptions, Form, Input, Select, Space, Table, Tag } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { date } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { fieldLabel, valueLabel, valueText } from '../onboardingConfig/schemaLabel';
import { Link } from 'react-router-dom';
import { canWaive, decodeCommand, decodeFileDownload, evidenceRejectReasons, reviewApi, reviewRoute, withOnBehalf } from './clientReview';
import type { Application, Evidence, Requirement, ReviewDetail, Subject } from './clientReview';
import { ReviewCommandModal, StatusDot, tone } from './reviewParts';
import { useReviewCommand } from './useReviewCommand';

const CODE = /^[A-Z][A-Z0-9_]{0,63}$/;
interface EvidenceRow { key: string; req: Requirement; evidence: Evidence | null; first: boolean }

/** 申请人显示名：机构法定名称，或个人姓名，读不到时用申请编号。 */
export function displayName(t: Translate, application: Application | null): string {
  if (!application) return t('detail.applicationUnavailableName');
  const org = application.values.organization; const person = application.values.individual;
  return org?.legalName || [person?.givenName, person?.familyName].filter(Boolean).join(' ') || t('detail.applicationName', { applicationNo: application.applicationNo });
}

function Values({ values, jurisdictionCode }: { values: Application['values']; jurisdictionCode: string | null }) {
  const t = useT('client-review'), tSchema = useT('onboarding-schema');
  const groups = Object.entries(values).filter(([group, fields]) => group !== 'contact' && Object.keys(fields).length > 0);
  if (groups.length === 0) return <span className="muted">{t('application.empty')}</span>;
  return <>{groups.map(([group, fields]) => <div key={group} className="obw-values"><h3>{valueLabel(t, 'group', group)}</h3>
    <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }} items={Object.entries(fields).map(([field, value]) => ({ key: field, label: fieldLabel(t, tSchema, group, field),
      children: valueText(tSchema, group, field, value, jurisdictionCode) }))} /></div>)}</>;
}

/** 申请资料：client-master 冻结的提交内容；证件号只有掩码。 */
export function ApplicationPanel({ data, currentCaseId }: { data: ReviewDetail; currentCaseId?: string | null }) {
  const t = useT('client-review');
  const application = data.application;
  const relationText = (relationType: string, ownershipPercentage: string | null) => ownershipPercentage
    ? t('application.relationWithOwnership', { relation: enumLabel(t, 'enum.relation', relationType), percentage: ownershipPercentage })
    : enumLabel(t, 'enum.relation', relationType);
  if (!application && currentCaseId !== undefined && currentCaseId !== data.review.caseId) return <div className="obw-card"><HistoricalRound currentCaseId={currentCaseId} /></div>;
  if (!application) return <div className="obw-card"><Alert showIcon type="warning" title={t('application.unavailable')} description={t('application.unavailableHint')} /></div>;
  const jurisdiction = data.review.jurisdictionCode;
  return <div className="obw-card"><div className="obw-card-title">{t('application.title')}</div>
    <Values values={application.values} jurisdictionCode={jurisdiction} />
    {application.people.length > 0 && <><h3 className="obw-subhead">{t('application.people')}</h3>{application.people.map(person => {
      const relations = application.relations.filter(r => r.relatedPartyId === person.partyId);
      return <div key={person.partyId} className="obw-person"><div className="cell-meta">{t('application.personMeta', { partyId: person.partyId,
        relations: relations.map(r => relationText(r.relationType, r.ownershipPercentage)).join(t('application.listSeparator')) || t('application.noRelations') })}</div>
        <Values values={person.values} jurisdictionCode={jurisdiction} /></div>;
    })}</>}
  </div>;
}

/**
 * 历史轮次：退回重提后旧案件不再是申请的当前案件，client-master 只为当前案件提供申请资料与分类（设计如此）
 * <li>currentCaseId 为同一申请的最新案件；未知（null）时只说明不链接。</li>
 */
export function HistoricalRound({ currentCaseId }: { currentCaseId: string | null }) {
  const t = useT('client-review');
  return <div className="obw-notice">{t('detail.historicalRound')}
    {currentCaseId && <> <Link to={`${reviewRoute}/${currentCaseId}`}>{t('detail.openCurrentRound')}</Link></>}</div>;
}

type MaterialAction = { kind: 'reject'; evidence: Evidence; req: Requirement } | { kind: 'waive'; req: Requirement };

/** 材料核验：逐份接受 / 不接受（必选原因码），可豁免的要求可记原因豁免。 */
export function MaterialsPanel({ caseId, subjects, active, canReview, canWaiveAny, onBehalfOfPartnerId, onAuthorizationChanged, onChanged }: {
  caseId: string; subjects: Subject[]; active: boolean; canReview: boolean; canWaiveAny: boolean; onBehalfOfPartnerId: string | null;
  onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review');
  const command = useReviewCommand(onAuthorizationChanged);
  const download = useFileDownload(caseId, onAuthorizationChanged);
  const [action, setAction] = useState<MaterialAction | null>(null);
  async function accept(evidenceId: string) {
    const result = await command.run(`${reviewApi}/cases/${caseId}/evidence/${evidenceId}/review`, 'POST',
      withOnBehalf({ outcome: 'ACCEPTED', rejectReasonCode: null }, onBehalfOfPartnerId), value => decodeCommand(value, caseId), t('materials.accepted'));
    if (result) onChanged();
  }
  return <div className="obw-card"><div className="obw-card-title">{t('materials.title')}</div>
    {command.failure?.status === 409 && <Alert showIcon type="warning" title={t('materials.conflict')} style={{ marginBottom: 12 }} />}
    {subjects.map(subject => <div key={subject.caseId} className="obw-person">
      <div className="obw-subject-head"><strong>{enumLabel(t, 'enum.role', subject.roleType)}</strong>
        <span className="cell-meta">{t('materials.subjectMeta', { partyId: subject.partyId, qualification: enumLabel(t, 'enum.qualification', subject.qualificationType) })}</span>
        <StatusDot tone={tone(subject.caseStatus)}>{enumLabel(t, 'enum.caseStatus', subject.caseStatus)}</StatusDot></div>
      <Table<EvidenceRow> size="small" rowKey="key" pagination={false} style={{ marginTop: 8 }} scroll={{ x: 860 }}
        dataSource={subject.requirements.flatMap((req): EvidenceRow[] => req.evidence.length === 0
          ? [{ key: req.requirementId, req, evidence: null, first: true }] : req.evidence.map((evidence, index) => ({ key: evidence.evidenceId, req, evidence, first: index === 0 })))}
        columns={[
          { title: t('materials.column.requirement'), width: 260, render: (_, row) => row.first ? <div>
            <div>{enumLabel(t, 'enum.requirement', row.req.requirementCode)}{row.req.required && <Tag color="blue" style={{ marginLeft: 6 }}>{t('materials.required')}</Tag>}</div>
            <div className="cell-meta">{[...(row.evidence?.documentType ? [row.evidence.documentType] : row.req.documentTypes).map(d => enumLabel(t, 'enum.documentType', d)),
              row.req.maxAgeDays !== null ? t('materials.maxAge', { days: row.req.maxAgeDays }) : null, row.req.reusedFromCaseId ? t('materials.reused') : null].filter(Boolean).join(' · ')}</div>
            <StatusDot tone={tone(row.req.status)}>{enumLabel(t, 'enum.requirementStatus', row.req.status)}</StatusDot>
            {row.req.waivedReasonCode && <div className="cell-meta">{t('materials.waivedReason', { reason: row.req.waivedReasonCode })}</div>}
          </div> : null },
          { title: t('materials.column.file'), width: 190, render: (_, row) => row.evidence
            ? <Button size="small" type="link" loading={download.pending === row.evidence.fileId}
              onClick={() => void download.open(row.evidence!.fileId)}>{t('materials.download', { fileId: row.evidence.fileId })}</Button>
            : <span className="muted">{t('materials.notSubmitted')}</span> },
          { title: t('materials.column.result'), width: 200, render: (_, row) => row.evidence ? <div>
            <StatusDot tone={tone(row.evidence.status)}>{enumLabel(t, 'enum.evidence', row.evidence.status)}</StatusDot>
            {row.evidence.rejectReasonCode && <div className="cell-meta">{enumLabel(t, 'enum.rejectReason', row.evidence.rejectReasonCode)}</div>}
            {row.evidence.expiryDate && <div className="cell-meta">{t('materials.expires', { date: row.evidence.expiryDate })}</div>}
            {row.evidence.reviewedTime && <div className="cell-meta">{t('materials.reviewedMeta', { reviewer: row.evidence.reviewerId ?? '—', time: date(row.evidence.reviewedTime) })}</div>}
          </div> : '—' },
          { title: t('materials.column.actions'), width: 210, render: (_, row) => <Space wrap size={4}>
            {row.evidence && row.evidence.status === 'RECEIVED' && active && canReview && <>
              <Button size="small" loading={command.saving} onClick={() => void accept(row.evidence!.evidenceId)}>{t('materials.accept')}</Button>
              <Button size="small" danger disabled={command.saving} onClick={() => setAction({ kind: 'reject', evidence: row.evidence!, req: row.req })}>{t('materials.reject')}</Button></>}
            {row.first && active && canWaiveAny && canWaive(row.req) && <Button size="small" type="link" onClick={() => setAction({ kind: 'waive', req: row.req })}>{t('materials.waive')}</Button>}
          </Space> },
        ]} />
    </div>)}
    {action?.kind === 'reject' && <ReviewCommandModal<{ rejectReasonCode: string }> title={t('materials.rejectTitle')} danger caseId={caseId}
      path={`${reviewApi}/cases/${caseId}/evidence/${action.evidence.evidenceId}/review`} onBehalfOfPartnerId={onBehalfOfPartnerId}
      build={values => ({ outcome: 'REJECTED', rejectReasonCode: values.rejectReasonCode })} success={t('materials.rejected')}
      intro={<p>{t('materials.rejectIntro', { requirement: enumLabel(t, 'enum.requirement', action.req.requirementCode), fileId: action.evidence.fileId })}</p>}
      onClose={() => setAction(null)} onDone={() => { setAction(null); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged}>
      <Form.Item name="rejectReasonCode" label={t('materials.rejectReason')} rules={[{ required: true, message: t('materials.rejectReasonRequired') }]}>
        <Select options={evidenceRejectReasons.map(value => ({ value, label: enumLabel(t, 'enum.rejectReason', value) }))} /></Form.Item>
    </ReviewCommandModal>}
    {action?.kind === 'waive' && <ReviewCommandModal<{ reasonCode: string; note?: string }> title={t('materials.waiveTitle')} caseId={caseId}
      path={`${reviewApi}/cases/${caseId}/requirements/${action.req.requirementId}/waiver`} onBehalfOfPartnerId={onBehalfOfPartnerId}
      build={values => ({ reasonCode: values.reasonCode.trim(), note: values.note!.trim() })} success={t('materials.waived')}
      intro={<Alert showIcon type="info" style={{ marginBottom: 12 }} title={t('materials.waiveIntro', { requirement: enumLabel(t, 'enum.requirement', action.req.requirementCode) })} />}
      onClose={() => setAction(null)} onDone={() => { setAction(null); onChanged(); }} onAuthorizationChanged={onAuthorizationChanged}>
      <Form.Item name="reasonCode" label={t('field.reasonCode')} rules={[{ required: true, pattern: CODE, message: t('decision.reasonCodeInvalid') }]}>
        <Input maxLength={64} placeholder={t('materials.waiveReasonPlaceholder')} /></Form.Item>
      <Form.Item name="note" label={t('field.noteInternal')} rules={[{ required: true, whitespace: true, message: t('materials.waiveNoteRequired') }]}><Input.TextArea rows={3} maxLength={1000} showCount /></Form.Item>
    </ReviewCommandModal>}
  </div>;
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
