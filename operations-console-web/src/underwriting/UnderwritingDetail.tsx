import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Input, Modal } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { formatShortDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { BusinessCard, Card, CommandAlert, DecisionRows, DueDiligenceCard, ExposureCard, HistoryCard, Row, SourcesCard, StatusBadge, sourceText } from './UnderwritingParts';
import { DraftEditor, UnderwritingHeader } from './UnderwritingWorkspace';
import { canRevise, decodeHistory, decodeUnderwriting, openVersion, permissionsOf, reviewAccess, reviewCommentIssue, underwritingPaths, underwritingRoutes } from './underwriting';
import type { Underwriting } from './underwriting';
import { hasMenu, limitTierMenu, limitTierRoutes } from '../limitTier/limitTier';
import { useUnderwritingCommand } from './useUnderwritingCommand';

interface Props { underwritingId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/**
 * 版本详情（/risk/underwriting/:id）
 * <li>DRAFT 且有维护权限：按最新输入编辑（原型 03）；其他情况只读。</li>
 * <li>SUBMITTED：冻结输入 + 复核区（原型 04），提交人本人按钮禁用。</li>
 * <li>APPROVED / DECLINED / SUPERSEDED：只读详情、版本历史与修订（原型 05）；APPROVED 可见限额档位菜单时链接到该 Client 的档位页（REQ-04）。</li>
 */
export function UnderwritingDetail({ underwritingId, resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('risk');
  const decode = useMemo(() => (value: unknown) => decodeUnderwriting(value, underwritingId), [underwritingId]);
  const detail = useDetail(underwritingPaths.detail(underwritingId), decode);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const record = detail.data;
  const permissions = permissionsOf(resources);
  const header = <DetailHeader record={record} />;
  if (record.status === 'DRAFT' && permissions.manage) return <DraftEditor key={record.version} record={record} header={header} onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />;
  return <FrozenView record={record} header={header} canManage={permissions.manage} canApprove={permissions.approve} canViewTier={hasMenu(resources, limitTierMenu)} actorId={actorId}
    onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} notice={record.status === 'DRAFT' ? t('underwriting.draftReadonly') : null} />;
}

function DetailHeader({ record }: { record: Underwriting }) {
  const t = useT('risk');
  const input = record.input;
  const person = (label: string | null, id: string | null) => label ?? id ?? '—';
  const meta = record.status === 'SUBMITTED'
    ? [input?.clientNo ?? null, t('underwriting.meta.submittedBy', { person: person(record.submittedByLabel, record.submittedBy) }),
      t('underwriting.meta.submittedAt', { time: formatShortDateTime(record.submittedAt) }), t('underwriting.meta.frozen')]
    : record.status === 'DRAFT' ? [input?.clientNo ?? null, input?.jurisdictionCode ?? null]
    : [input?.clientNo ?? null, input?.jurisdictionCode ?? null, t(record.status === 'DECLINED' ? 'underwriting.meta.declinedAt' : 'underwriting.meta.approvedAt',
      { time: formatShortDateTime(record.decidedAt ?? record.submittedAt) }), record.status === 'APPROVED' ? t('underwriting.meta.immutable') : null];
  return <UnderwritingHeader crumb={t('underwriting.crumb.' + record.status)} title={input?.clientDisplayName ?? record.clientId}
    badges={<><StatusBadge status={record.status} effective={record.status === 'APPROVED'} /><span className="uw-badge is-muted">v{record.versionNo}</span></>} meta={meta} />;
}

function FrozenView({ record, header, canManage, canApprove, canViewTier, actorId, onAuthorizationChanged, onChanged, notice }: { record: Underwriting; header: ReactNode;
  canManage: boolean; canApprove: boolean; canViewTier: boolean; actorId: string; onAuthorizationChanged: () => void; onChanged: () => void; notice: string | null }) {
  const t = useT('risk');
  const decode = useMemo(() => (value: unknown) => decodeHistory(value, record.clientId), [record.clientId]);
  const history = useDetail(underwritingPaths.history(record.clientId), decode);
  const items = history.data ?? [];
  const access = reviewAccess(record, actorId, canApprove);
  const open = openVersion(items);
  const input = record.input;
  return <div className="uw-page">{header}
    {notice && <Alert showIcon type="info" className="uw-alert" title={notice} />}
    <div className="uw-split">
      <div className="uw-column">
        {input ? <><DueDiligenceCard input={input} /><BusinessCard input={input} />
          <ExposureCard exposure={record.estimatedExposure} unavailableReason={record.exposureUnavailableReason} input={input}
            settlementDays={record.decision?.suggestedSettlementDays ?? null} frozen={record.status !== 'DRAFT'} />
          <SourcesCard input={input} /></> : <Alert showIcon type="info" title={t('underwriting.input.none')} />}
      </div>
      <div className="uw-column">
        <Card title={t('underwriting.decision.title')} extra={<span className="muted">{record.status === 'SUBMITTED' ? t('underwriting.decision.submittedReadonly') : sourceText(t, record)}</span>}>
          {record.decision ? <DecisionRows decision={record.decision} /> : <span className="muted">{t('underwriting.decision.empty')}</span>}
          {record.declineReason && <Row label={t('underwriting.declineReason')}><div className="uw-note is-danger">{record.declineReason}</div></Row>}
          {record.reviewComment && <Row label={t('underwriting.review.comment')}><div className="uw-note">{record.reviewComment}</div></Row>}
          {record.revisionReason && <Row label={t('underwriting.revisionReason')}><div className="uw-note">{record.revisionReason}</div></Row>}
        </Card>
        {record.status === 'APPROVED' && canViewTier && <Alert showIcon type="info" className="uw-alert" title={t('limitTier.fromUnderwriting.title')}
          action={<Link to={limitTierRoutes.detail(record.clientId)}>{t('limitTier.fromUnderwriting.open')}</Link>} />}
        {access !== 'none' && <ReviewPanel record={record} self={access === 'self'} onAuthorizationChanged={onAuthorizationChanged} onDone={onChanged} />}
        {record.status === 'SUBMITTED' && access === 'none' && <Alert showIcon type="info" className="uw-alert" title={t('underwriting.review.waiting')} />}
        {history.failure ? <QueryFailure failure={history.failure} retry={history.refresh} /> : <HistoryCard history={items} currentId={record.underwritingId} />}
        {record.status === 'APPROVED' && canManage && history.data && (canRevise(record, items, canManage)
          ? <ReviseCard record={record} onAuthorizationChanged={onAuthorizationChanged} />
          : open && <Alert showIcon type="info" title={t('underwriting.revisionOpen', { version: open.versionNo })}
            action={<Link to={underwritingRoutes.detail(open.underwritingId)}>{t('underwriting.action.view')}</Link>} />)}
      </div>
    </div></div>;
}

/**
 * 复核区（原型 04，HIGH 四眼）：复核意见拒绝时必填；提交人本人打开时按钮禁用并说明，服务端另以 SELF_REVIEW 兜底。
 */
function ReviewPanel({ record, self, onAuthorizationChanged, onDone }: { record: Underwriting; self: boolean; onAuthorizationChanged: () => void; onDone: () => void }) {
  const t = useT('risk');
  const [comment, setComment] = useState(''); const [issue, setIssue] = useState<string | null>(null);
  const command = useUnderwritingCommand(onAuthorizationChanged);
  async function review(decision: 'APPROVE' | 'DECLINE') {
    const problem = reviewCommentIssue(decision, comment);
    setIssue(problem === 'required' ? t('underwriting.review.commentRequired') : problem === 'tooLong' ? t('underwriting.validation.tooLong', { max: 500 }) : null);
    if (problem) return;
    const result = await command.run(underwritingPaths.review(record.underwritingId), 'POST', { expectedVersion: record.version, decision, ...(comment.trim() ? { comment: comment.trim() } : {}) },
      value => decodeUnderwriting(value, record.underwritingId), t(decision === 'APPROVE' ? 'underwriting.done.approved' : 'underwriting.done.declined'));
    if (result) onDone();
  }
  return <Card title={t('underwriting.review.title')}>
    <Input.TextArea aria-label={t('underwriting.review.comment')} placeholder={t('underwriting.review.placeholder')} autoSize={{ minRows: 3, maxRows: 6 }} maxLength={500}
      value={comment} disabled={self} status={issue ? 'error' : undefined} onChange={event => { setComment(event.target.value); setIssue(null); }} />
    {issue && <div className="uw-field-error" role="alert">{issue}</div>}
    <CommandAlert failure={command.failure} />
    <div className="uw-actions uw-gap">
      <Button danger disabled={self} loading={command.saving} onClick={() => void review('DECLINE')}>{t('underwriting.decline')}</Button>
      <Button type="primary" disabled={self} loading={command.saving} onClick={() => void review('APPROVE')}>{t('underwriting.review.approve')}</Button></div>
    <p className="uw-hint">{self ? t('underwriting.review.selfBlocked') : t('underwriting.review.allowed')}</p>
  </Card>;
}

/** 修订（原型 05）：结论复制自当前有效版本，输入由 ops-bff 按最新来源重新取得；修订原因必填。 */
function ReviseCard({ record, onAuthorizationChanged }: { record: Underwriting; onAuthorizationChanged: () => void }) {
  const t = useT('risk'); const navigate = useNavigate();
  const [open, setOpen] = useState(false); const [reason, setReason] = useState(''); const [touched, setTouched] = useState(false);
  const command = useUnderwritingCommand(onAuthorizationChanged);
  async function revise() {
    setTouched(true);
    if (!reason.trim()) return;
    const created = await command.run(underwritingPaths.revision(record.underwritingId), 'POST', { expectedVersion: record.version, revisionReason: reason.trim() },
      value => decodeUnderwriting(value), t('underwriting.done.revision'));
    if (created) void navigate(underwritingRoutes.detail(created.underwritingId));
  }
  return <Card>
    <div className="uw-actions"><Button type="primary" onClick={() => setOpen(true)}>{t('underwriting.revise')}</Button></div>
    <p className="uw-hint">{t('underwriting.reviseHint', { next: record.versionNo + 1, current: record.versionNo })}</p>
    {open && <Modal open title={t('underwriting.revise')} okText={t('underwriting.revise')} okButtonProps={{ loading: command.saving }} onCancel={() => setOpen(false)} onOk={() => void revise()}>
      <p className="uw-hint">{t('underwriting.reviseHint', { next: record.versionNo + 1, current: record.versionNo })}</p>
      <Input.TextArea autoFocus aria-label={t('underwriting.revisionReason')} placeholder={t('underwriting.revisionReason')} autoSize={{ minRows: 3, maxRows: 6 }} maxLength={500} showCount
        value={reason} status={touched && !reason.trim() ? 'error' : undefined} onChange={event => setReason(event.target.value)} />
      {touched && !reason.trim() && <div className="uw-field-error">{t('underwriting.validation.required')}</div>}
      <CommandAlert failure={command.failure} />
    </Modal>}
  </Card>;
}
