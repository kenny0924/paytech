import { useMemo, useState } from 'react';
import { App, Button, Descriptions, Drawer, Form, Input, Select, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ActorText, HashText, ReviewActions } from '../../finance/WorkflowParts';
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import {
  classificationLabels, classificationOptions, decimalIdPattern, decodeExceptionDetail, decodeWithId, directionLabels, exceptionStatusLabels,
  exceptionTypeLabels, hasReconAction, reconActions, reconLayerLabels, reconPaths, rematchTargetType, resolutionStatusLabels, resolutionTypeLabels,
  sourceTypeLabels,
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
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const decode = useMemo(() => decodeExceptionDetail(exceptionId), [exceptionId]);
  const detail = useReconDetail<ReconExceptionDetail>(isReconId(exceptionId) ? reconPaths.exception(exceptionId) : null, decode,
    onAuthorizationChanged, '对账异常详情加载失败');
  const [step, setStep] = useState<Step>(null);
  const item = detail.item; const exception = item?.exception;
  const pending = item?.resolutions.find(resolution => resolution.status === 'PENDING_CHECK');
  const actions = item && exception && <>
    {canOperate && exception.status === 'OPEN' && item.availableResolutionTypes.length > 0 && <Button type="primary" onClick={() => setStep('resolve')}>提交处置</Button>}
    {pending && canApprove && <Button type="primary" onClick={() => setStep('review')}>复核处置</Button>}
  </>;
  const done = () => { setStep(null); detail.reload(); };
  return <ReconDetailPage list={{ label: '对账异常', route: reconRoutes.exceptions }} objectLabel="对账异常" state={detail} actions={actions}
    title={exception ? `对账异常 · ${exceptionTypeLabels[exception.exceptionType] ?? exception.exceptionType}` : null}>
    {current => <>
      <ExceptionHead detail={current} actorId={actorId} />
      <SourceSummary detail={current} />
      <DetailSection title="处置记录"><ResolutionTable resolutions={current.resolutions} actorId={actorId} /></DetailSection>
      {pending && <FinanceNote tone="warning">待复核：{resolutionTypeLabels[pending.resolutionType] ?? pending.resolutionType}
        {pending.classificationCode ? ` · ${pending.classificationCode}` : ''}{pending.amount ? ` · 金额 ${pending.amount}` : ''}，提交人 {pending.makerId}
        {canApprove && pending.makerId === actorId ? '（提交人不能复核自己的提交）' : ''}</FinanceNote>}
      <FinanceNote>处置三种：重新匹配、接受时间差、归类。提交后由另一位有审批权限的同事复核，批准即应用；平台流水、预期银行流水与银行流水的归类会推送 billing 记账。
        {!canOperate && ' 当前账号没有对账操作权限。'}{!canApprove && ' 当前账号没有对账审批权限。'}</FinanceNote>
      {step === 'resolve' && <ResolutionDrawer detail={current} onClose={() => setStep(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
      {step === 'review' && pending && <ReviewDrawer resolution={pending} actorId={actorId} onClose={() => setStep(null)} onDone={done}
        onAuthorizationChanged={onAuthorizationChanged} />}
    </>}
  </ReconDetailPage>;
}

function ExceptionHead({ detail, actorId }: { detail: ReconExceptionDetail; actorId: string }) {
  const exception = detail.exception; const account = detail.account;
  const accountText = account?.clearingAccountCode ? `${account.clearingAccountCode} · ${account.channelCode}`
    : account?.cashAccountName ? `${account.cashAccountName} · 尾号 ${account.accountLast4}` : null;
  const accountLink = exception.clearingAccountId ? reconRoutes.clearingAccount(exception.clearingAccountId)
    : exception.cashAccountId ? reconRoutes.cashAccount(exception.cashAccountId) : null;
  return <Descriptions size="small" column={3} items={[
    { key: 'status', label: '状态', children: <ReconTag status={exception.status} labels={exceptionStatusLabels} /> },
    { key: 'layer', label: '对账层', children: `${reconLayerLabels[exception.reconLayer] ?? exception.reconLayer}${exception.blocking ? ' · 阻塞对平' : ''}` },
    { key: 'age', label: '账龄', children: `${exception.ageDays} 天` },
    { key: 'difference', label: '差异金额', children: <><Money value={exception.differenceAmount} /> {exception.currency}</> },
    { key: 'account', label: '账户', children: accountLink ? <Link to={accountLink}>{accountText ?? accountLink.split('/').at(-1)}</Link> : '—' },
    { key: 'entity', label: '主体', children: exception.entityId },
    { key: 'statement', label: '渠道账单', children: exception.statementId ? <Link className="mono" to={reconRoutes.statement(exception.statementId)}>{exception.statementId}</Link> : '—' },
    { key: 'opened', label: '开立', children: <>{localTime(exception.openedTime)} {exception.openedBy ? <ActorText value={exception.openedBy} actorId={actorId} /> : '（系统）'}</> },
    { key: 'closed', label: '结案', children: exception.closedTime ? localTime(exception.closedTime) : '—' },
    { key: 'detail', label: '说明', span: 3, children: exception.detail ?? '—' },
  ]} />;
}

function SourceSummary({ detail }: { detail: ReconExceptionDetail }) {
  const source = detail.source;
  const link = exceptionSourceLink(detail.exception, source);
  const reference = <span className="mono">{source.reference ?? source.sourceId}</span>;
  return <DetailSection title="来源对象" extra={link && <Link to={link}>查看{sourceTypeLabels[source.sourceType] ?? '来源'}</Link>}>
    <Descriptions size="small" column={3} items={[
      { key: 'type', label: '类型', children: sourceTypeLabels[source.sourceType] ?? source.sourceType },
      { key: 'reference', label: '引用', children: link ? <Link to={link}>{reference}</Link> : reference },
      { key: 'status', label: '状态', children: source.status ?? '—' },
      { key: 'direction', label: '方向', children: source.direction ? directionLabels[source.direction] ?? source.direction : '—' },
      { key: 'amount', label: '金额', children: <Money value={source.amount} /> },
      { key: 'date', label: '业务日期', children: source.businessDate ?? '—' },
    ]} />
  </DetailSection>;
}

function ResolutionTable({ resolutions, actorId }: { resolutions: readonly ReconResolution[]; actorId: string }) {
  return <section className="table-panel"><Table<ReconResolution> rowKey="resolutionId" size="small" tableLayout="fixed" pagination={false} dataSource={[...resolutions]}
    scroll={{ x: 1000 }} locale={{ emptyText: '还没有处置' }} columns={[
      { title: '#', width: 50, dataIndex: 'resolutionNo' },
      { title: '处置', width: 200, render: (_, row) => <>{resolutionTypeLabels[row.resolutionType] ?? row.resolutionType}
        {row.classificationCode && <div className="cell-meta">{classificationLabels[row.classificationCode] ?? row.classificationCode}（{row.classificationCode}）</div>}
        {row.rematchTargetId && <div className="cell-meta mono">→ {row.rematchTargetType} {row.rematchTargetId}{row.rematchAmount ? ` · ${row.rematchAmount}` : ''}</div>}</> },
      { title: '金额', width: 110, align: 'right', render: (_, row) => <Money value={row.amount} /> },
      { title: '说明', render: (_, row) => row.note },
      { title: '提交人', width: 160, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
      { title: '复核人', width: 160, render: (_, row) => <ActorText value={row.checkerId} actorId={actorId} /> },
      { title: '状态', width: 90, render: (_, row) => <ReconTag status={row.status} labels={resolutionStatusLabels} /> },
      { title: '提交时间', width: 150, render: (_, row) => localTime(row.createTime) },
    ]} /></section>;
}

/** 提交处置抽屉：归类码只列来源对象允许且对账服务当前给出的（设计 F3），归类金额固定为差异金额。 */
function ResolutionDrawer({ detail, onClose, onDone, onAuthorizationChanged }: {
  detail: ReconExceptionDetail; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<ResolutionValues>();
  const command = useReconCommand(onAuthorizationChanged, form);
  const resolutionType = Form.useWatch('resolutionType', form);
  const exception = detail.exception;
  const codes = classificationOptions(exception.sourceType, detail.availableClassificationCodes);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(reconPaths.resolutions(exception.exceptionId), 'POST', resolutionBody(values, exception),
      decodeWithId<ReconResolution>('resolutionId'), '提交处置失败');
    if (!result) return;
    void message.success('已提交处置，等待复核'); onDone();
  }
  return <Drawer open size="min(720px, 100vw)" rootClassName="finance-drawer finance-page" title="提交处置" destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }} footer={<div className="finance-drawer-footer"><span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>取消</Button>
      <Button type="primary" loading={command.saving} onClick={() => void submit()}>提交复核</Button></div>}>
    <ReconCommandFeedback command={command} onReload={onDone} />
    <Form form={form} name="recon-resolution" layout="vertical" disabled={command.saving}>
      <div className="finance-form-grid">
        <Form.Item name="resolutionType" label="处置方式" rules={[{ required: true, message: '请选择处置方式' }]}>
          <Select options={detail.availableResolutionTypes.map(value => ({ value, label: resolutionTypeLabels[value] ?? value }))} /></Form.Item>
        {resolutionType === 'CLASSIFY' && <Form.Item name="classificationCode" label="归类码" extra={`金额固定为差异金额 ${exception.differenceAmount}`}
          rules={[{ required: true, message: '请选择归类码' }]}>
          <Select placeholder={codes.length ? '选择归类码' : '该来源对象没有可用的归类码'}
            options={codes.map(value => ({ value, label: `${classificationLabels[value] ?? value}（${value}）` }))} /></Form.Item>}
        {resolutionType === 'RE_MATCH' && <Form.Item name="rematchTargetId" label={exception.reconLayer === 'BANK' ? '目标预期银行流水主键' : '目标平台流水主键'}
          rules={[{ required: true, pattern: decimalIdPattern, message: '正十进制主键' }]}><Input maxLength={19} /></Form.Item>}
        {resolutionType === 'RE_MATCH' && exception.reconLayer === 'BANK' && <Form.Item name="rematchAmount" label="分配金额"
          rules={[{ required: true, pattern: /^(0|[1-9][0-9]{0,15})(\.[0-9]{1,8})?$/, message: '正的十进制金额' }]}><Input inputMode="decimal" /></Form.Item>}
      </div>
      <Form.Item name="note" label="处置说明" rules={[{ required: true, whitespace: true, message: '请填写说明' }]}><Input.TextArea maxLength={1000} rows={3} showCount /></Form.Item>
    </Form>
  </Drawer>;
}

/** 复核抽屉：展示待复核处置与内容摘要；提交人本人不显示批准 / 驳回。 */
function ReviewDrawer({ resolution, actorId, onClose, onDone, onAuthorizationChanged }: {
  resolution: ReconResolution; actorId: string; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const command = useReconCommand(onAuthorizationChanged);
  async function review(decision: 'APPROVED' | 'REJECTED') {
    const result = await command.run(reconPaths.resolutionApproval(resolution.resolutionId), 'POST', { decision, approvedContentHash: resolution.contentHash },
      decodeWithId<ReconResolution>('resolutionId', resolution.resolutionId), '复核处置失败');
    if (!result) return;
    void message.success(decision === 'APPROVED' ? '已批准，处置已应用' : '已驳回，异常回到待处置'); onDone();
  }
  const self = resolution.makerId === actorId;
  return <Drawer open size="min(720px, 100vw)" rootClassName="finance-drawer finance-page" title="复核处置" destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }}>
    <ReconCommandFeedback command={command} onReload={onDone} />
    <Descriptions size="small" column={2} items={[
      { key: 'type', label: '处置方式', children: resolutionTypeLabels[resolution.resolutionType] ?? resolution.resolutionType },
      { key: 'code', label: '归类码', children: resolution.classificationCode ? `${classificationLabels[resolution.classificationCode] ?? ''}（${resolution.classificationCode}）` : '—' },
      { key: 'amount', label: '金额', children: <Money value={resolution.amount ?? resolution.rematchAmount} /> },
      { key: 'target', label: '重新匹配目标', children: resolution.rematchTargetId ? <span className="mono">{resolution.rematchTargetType} {resolution.rematchTargetId}</span> : '—' },
      { key: 'maker', label: '提交人', children: <ActorText value={resolution.makerId} actorId={actorId} /> },
      { key: 'hash', label: '内容摘要', children: <HashText value={resolution.contentHash} /> },
      { key: 'note', label: '说明', span: 2, children: resolution.note },
    ]} />
    <ReviewActions saving={command.saving} note={self ? '提交人不能复核自己的提交。' : undefined} actions={self ? [] : [
      { key: 'approve', label: '批准并应用', primary: true, confirm: '批准后处置在同一事务内应用，异常结案。确认？', onConfirm: () => void review('APPROVED') },
      { key: 'reject', label: '驳回', danger: true, confirm: '驳回后异常回到待处置。确认？', onConfirm: () => void review('REJECTED') },
    ]} />
  </Drawer>;
}
