import { useMemo, useState } from 'react';
import { App, Button, Descriptions, Drawer, Form, Input, Select, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ActorText, HashText, ReviewActions } from '../../finance/WorkflowParts';
import { useT } from '../../i18n/react';
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import {
  classificationEnum, classificationOptions, decimalIdPattern, decodeExceptionDetail, decodeWithId, directionEnum, exceptionStatusEnum,
  exceptionTypeEnum, hasReconAction, reconActions, reconLabel, reconLayerEnum, reconPaths, rematchTargetType, resolutionStatusEnum, resolutionTypeEnum,
  sourceTypeEnum,
} from '../reconModel';
import type { ReconException, ReconExceptionDetail, ReconResolution } from '../reconModel';
import { Money, ReconCommandFeedback, ReconTag } from '../ReconParts';
import { exceptionSourceLink, isReconId, reconRoutes } from '../reconRoutes';
import { useReconCommand } from '../useReconCommand';
import { useReconDetail } from '../useReconDetail';

interface Props { exceptionId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
interface ResolutionValues { resolutionType: string; classificationCode?: string; rematchTargetId?: string; rematchAmount?: string; note: string }
type Step = 'resolve' | 'review' | null;

/** 处置请求体：只带所选处置方式需要的字段，其余为 null（reconciliation 按组合校验）。 */
export function resolutionBody(values: ResolutionValues, item: Pick<ReconException, 'reconLayer' | 'differenceAmount'>) {
  const classify = values.resolutionType === 'CLASSIFY'; const rematch = values.resolutionType === 'RE_MATCH';
  return { resolutionType: values.resolutionType, classificationCode: classify ? values.classificationCode : null, amount: classify ? item.differenceAmount : null,
    rematchTargetType: rematch ? rematchTargetType(item.reconLayer) : null, rematchTargetId: rematch ? values.rematchTargetId?.trim() : null,
    rematchAmount: rematch && item.reconLayer === 'BANK' ? values.rematchAmount?.trim() : null, note: values.note.trim() };
}

/**
 * 对账异常详情页（/finance/reconciliation/exceptions/:exceptionId）：异常信息、来源对象摘要（链到来源详情，AC-04）、所属账户、处置历史
 * <li>提交处置（对账操作）与复核（对账审批，提交人本人不能复核）在抽屉中完成，完成后详情页重新读取。</li>
 */
export function ExceptionDetail({ exceptionId, resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('recon');
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const decode = useMemo(() => decodeExceptionDetail(exceptionId), [exceptionId]);
  const detail = useReconDetail<ReconExceptionDetail>(isReconId(exceptionId) ? reconPaths.exception(exceptionId) : null, decode,
    onAuthorizationChanged, t('exception.loadFailed'));
  const [step, setStep] = useState<Step>(null);
  const item = detail.item; const exception = item?.exception;
  const pending = item?.resolutions.find(resolution => resolution.status === 'PENDING_CHECK');
  const actions = item && exception && <>
    {canOperate && exception.status === 'OPEN' && item.availableResolutionTypes.length > 0 && <Button type="primary" onClick={() => setStep('resolve')}>{t('exception.submitResolution')}</Button>}
    {pending && canApprove && <Button type="primary" onClick={() => setStep('review')}>{t('exception.reviewResolution')}</Button>}
  </>;
  const done = () => { setStep(null); detail.reload(); };
  return <ReconDetailPage list={{ label: t('exception.listLabel'), route: reconRoutes.exceptions }} objectLabel={t('exception.object')} state={detail} actions={actions}
    title={exception ? t('exception.title', { type: reconLabel(t, exceptionTypeEnum, exception.exceptionType) }) : null}>
    {current => <>
      <ExceptionHead detail={current} actorId={actorId} />
      <SourceSummary detail={current} />
      <DetailSection title={t('exception.resolutionsTitle')}><ResolutionTable resolutions={current.resolutions} actorId={actorId} /></DetailSection>
      {pending && <FinanceNote tone="warning">{t('exception.pendingNote', { summary: [reconLabel(t, resolutionTypeEnum, pending.resolutionType),
        pending.classificationCode, pending.amount ? t('exception.amountPart', { amount: pending.amount }) : null].filter(Boolean).join(' · '), maker: pending.makerId })}
        {canApprove && pending.makerId === actorId ? t('exception.selfReviewNote') : ''}</FinanceNote>}
      <FinanceNote>{t('exception.note')}
        {!canOperate && ' ' + t('permission.noOperate')}{!canApprove && ' ' + t('permission.noApprove')}</FinanceNote>
      {step === 'resolve' && <ResolutionDrawer detail={current} onClose={() => setStep(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
      {step === 'review' && pending && <ReviewDrawer resolution={pending} actorId={actorId} onClose={() => setStep(null)} onDone={done}
        onAuthorizationChanged={onAuthorizationChanged} />}
    </>}
  </ReconDetailPage>;
}

function ExceptionHead({ detail, actorId }: { detail: ReconExceptionDetail; actorId: string }) {
  const t = useT('recon');
  const exception = detail.exception; const account = detail.account;
  const accountText = account?.clearingAccountCode ? `${account.clearingAccountCode} · ${account.channelCode}`
    : account?.cashAccountName ? t('cash.nameWithLast4', { name: account.cashAccountName, last4: account.accountLast4 }) : null;
  const accountLink = exception.clearingAccountId ? reconRoutes.clearingAccount(exception.clearingAccountId)
    : exception.cashAccountId ? reconRoutes.cashAccount(exception.cashAccountId) : null;
  return <Descriptions size="small" column={3} items={[
    { key: 'status', label: t('field.status'), children: <ReconTag status={exception.status} kind={exceptionStatusEnum} /> },
    { key: 'layer', label: t('field.reconLayer'), children: exception.blocking
      ? t('exception.layerBlocking', { layer: reconLabel(t, reconLayerEnum, exception.reconLayer) }) : reconLabel(t, reconLayerEnum, exception.reconLayer) },
    { key: 'age', label: t('field.age'), children: t('common.days', { days: exception.ageDays }) },
    { key: 'difference', label: t('field.differenceAmount'), children: <><Money value={exception.differenceAmount} /> {exception.currency}</> },
    { key: 'account', label: t('field.account'), children: accountLink ? <Link to={accountLink}>{accountText ?? accountLink.split('/').at(-1)}</Link> : '—' },
    { key: 'entity', label: t('field.entity'), children: exception.entityId },
    { key: 'statement', label: t('link.statements'), children: exception.statementId ? <Link className="mono" to={reconRoutes.statement(exception.statementId)}>{exception.statementId}</Link> : '—' },
    { key: 'opened', label: t('exception.opened'), children: <>{localTime(exception.openedTime)} {exception.openedBy ? <ActorText value={exception.openedBy} actorId={actorId} /> : t('exception.system')}</> },
    { key: 'closed', label: t('exception.closed'), children: exception.closedTime ? localTime(exception.closedTime) : '—' },
    { key: 'detail', label: t('field.note'), span: 3, children: exception.detail ?? '—' },
  ]} />;
}

function SourceSummary({ detail }: { detail: ReconExceptionDetail }) {
  const t = useT('recon');
  const source = detail.source;
  const link = exceptionSourceLink(detail.exception, source);
  const reference = <span className="mono">{source.reference ?? source.sourceId}</span>;
  return <DetailSection title={t('exception.source')} extra={link && <Link to={link}>{t('exception.viewSource', { source: reconLabel(t, sourceTypeEnum, source.sourceType) })}</Link>}>
    <Descriptions size="small" column={3} items={[
      { key: 'type', label: t('field.type'), children: reconLabel(t, sourceTypeEnum, source.sourceType) },
      { key: 'reference', label: t('exception.reference'), children: link ? <Link to={link}>{reference}</Link> : reference },
      { key: 'status', label: t('field.status'), children: source.status ?? '—' },
      { key: 'direction', label: t('field.direction'), children: source.direction ? reconLabel(t, directionEnum, source.direction) : '—' },
      { key: 'amount', label: t('field.amount'), children: <Money value={source.amount} /> },
      { key: 'date', label: t('field.businessDate'), children: source.businessDate ?? '—' },
    ]} />
  </DetailSection>;
}

function ResolutionTable({ resolutions, actorId }: { resolutions: readonly ReconResolution[]; actorId: string }) {
  const t = useT('recon');
  return <section className="table-panel"><Table<ReconResolution> rowKey="resolutionId" size="small" tableLayout="fixed" pagination={false} dataSource={[...resolutions]}
    scroll={{ x: 1000 }} locale={{ emptyText: t('exception.resolutionsEmpty') }} columns={[
      { title: '#', width: 50, dataIndex: 'resolutionNo' },
      { title: t('exception.resolution'), width: 200, render: (_, row) => <>{reconLabel(t, resolutionTypeEnum, row.resolutionType)}
        {row.classificationCode && <div className="cell-meta">{t('common.labelWithId', { label: reconLabel(t, classificationEnum, row.classificationCode), id: row.classificationCode })}</div>}
        {row.rematchTargetId && <div className="cell-meta mono">→ {row.rematchTargetType} {row.rematchTargetId}{row.rematchAmount ? ` · ${row.rematchAmount}` : ''}</div>}</> },
      { title: t('field.amount'), width: 110, align: 'right', render: (_, row) => <Money value={row.amount} /> },
      { title: t('field.note'), render: (_, row) => row.note },
      { title: t('field.maker'), width: 160, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
      { title: t('field.checker'), width: 160, render: (_, row) => <ActorText value={row.checkerId} actorId={actorId} /> },
      { title: t('field.status'), width: 90, render: (_, row) => <ReconTag status={row.status} kind={resolutionStatusEnum} /> },
      { title: t('field.submittedAt'), width: 150, render: (_, row) => localTime(row.createTime) },
    ]} /></section>;
}

/** 提交处置抽屉：归类码只列来源对象允许且对账服务当前给出的（设计 F3），归类金额固定为差异金额。 */
function ResolutionDrawer({ detail, onClose, onDone, onAuthorizationChanged }: {
  detail: ReconExceptionDetail; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('recon');
  const { message } = App.useApp();
  const [form] = Form.useForm<ResolutionValues>();
  const command = useReconCommand(onAuthorizationChanged, form);
  const resolutionType = Form.useWatch('resolutionType', form);
  const exception = detail.exception;
  const codes = classificationOptions(exception.sourceType, detail.availableClassificationCodes);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(reconPaths.resolutions(exception.exceptionId), 'POST', resolutionBody(values, exception),
      decodeWithId<ReconResolution>('resolutionId'), t('exception.submitFailed'));
    if (!result) return;
    void message.success(t('exception.submitted')); onDone();
  }
  return <Drawer open size="min(720px, 100vw)" rootClassName="finance-drawer finance-page" title={t('exception.submitResolution')} destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }} footer={<div className="finance-drawer-footer"><span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>{t('common.cancel')}</Button>
      <Button type="primary" loading={command.saving} onClick={() => void submit()}>{t('exception.submitForReview')}</Button></div>}>
    <ReconCommandFeedback command={command} onReload={onDone} />
    <Form form={form} name="recon-resolution" layout="vertical" disabled={command.saving}>
      <div className="finance-form-grid">
        <Form.Item name="resolutionType" label={t('exception.resolutionType')} rules={[{ required: true, message: t('exception.resolutionTypeRequired') }]}>
          <Select options={detail.availableResolutionTypes.map(value => ({ value, label: reconLabel(t, resolutionTypeEnum, value) }))} /></Form.Item>
        {resolutionType === 'CLASSIFY' && <Form.Item name="classificationCode" label={t('exception.classificationCode')} extra={t('exception.classificationAmount', { amount: exception.differenceAmount })}
          rules={[{ required: true, message: t('exception.classificationRequired') }]}>
          <Select placeholder={codes.length ? t('exception.selectClassification') : t('exception.noClassification')}
            options={codes.map(value => ({ value, label: t('common.labelWithId', { label: reconLabel(t, classificationEnum, value), id: value }) }))} /></Form.Item>}
        {resolutionType === 'RE_MATCH' && <Form.Item name="rematchTargetId" label={exception.reconLayer === 'BANK' ? t('exception.targetMovementId') : t('exception.targetTxnId')}
          rules={[{ required: true, pattern: decimalIdPattern, message: t('validation.decimalId') }]}><Input maxLength={19} /></Form.Item>}
        {resolutionType === 'RE_MATCH' && exception.reconLayer === 'BANK' && <Form.Item name="rematchAmount" label={t('bankLine.allocation')}
          rules={[{ required: true, pattern: /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/, message: t('validation.positiveAmount') }]}><Input inputMode="decimal" /></Form.Item>}
      </div>
      <Form.Item name="note" label={t('exception.resolutionNote')} rules={[{ required: true, whitespace: true, message: t('exception.noteRequired') }]}><Input.TextArea maxLength={1000} rows={3} showCount /></Form.Item>
    </Form>
  </Drawer>;
}

/** 复核抽屉：展示待复核处置与内容摘要；提交人本人不显示批准 / 驳回。 */
function ReviewDrawer({ resolution, actorId, onClose, onDone, onAuthorizationChanged }: {
  resolution: ReconResolution; actorId: string; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('recon');
  const { message } = App.useApp();
  const command = useReconCommand(onAuthorizationChanged);
  async function review(decision: 'APPROVED' | 'REJECTED') {
    const result = await command.run(reconPaths.resolutionApproval(resolution.resolutionId), 'POST', { decision, approvedContentHash: resolution.contentHash },
      decodeWithId<ReconResolution>('resolutionId', resolution.resolutionId), t('exception.reviewFailed'));
    if (!result) return;
    void message.success(decision === 'APPROVED' ? t('exception.approved') : t('exception.rejected')); onDone();
  }
  const self = resolution.makerId === actorId;
  return <Drawer open size="min(720px, 100vw)" rootClassName="finance-drawer finance-page" title={t('exception.reviewResolution')} destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }}>
    <ReconCommandFeedback command={command} onReload={onDone} />
    <Descriptions size="small" column={2} items={[
      { key: 'type', label: t('exception.resolutionType'), children: reconLabel(t, resolutionTypeEnum, resolution.resolutionType) },
      { key: 'code', label: t('exception.classificationCode'), children: resolution.classificationCode
        ? t('common.labelWithId', { label: reconLabel(t, classificationEnum, resolution.classificationCode), id: resolution.classificationCode }) : '—' },
      { key: 'amount', label: t('field.amount'), children: <Money value={resolution.amount ?? resolution.rematchAmount} /> },
      { key: 'target', label: t('exception.rematchTarget'), children: resolution.rematchTargetId ? <span className="mono">{resolution.rematchTargetType} {resolution.rematchTargetId}</span> : '—' },
      { key: 'maker', label: t('field.maker'), children: <ActorText value={resolution.makerId} actorId={actorId} /> },
      { key: 'hash', label: t('field.contentHash'), children: <HashText value={resolution.contentHash} /> },
      { key: 'note', label: t('field.note'), span: 2, children: resolution.note },
    ]} />
    <ReviewActions saving={command.saving} note={self ? t('cashDetail.selfReview') : undefined} actions={self ? [] : [
      { key: 'approve', label: t('exception.approve'), primary: true, confirm: t('exception.confirmApprove'), onConfirm: () => void review('APPROVED') },
      { key: 'reject', label: t('exception.reject'), danger: true, confirm: t('exception.confirmReject'), onConfirm: () => void review('REJECTED') },
    ]} />
  </Drawer>;
}
