import { useMemo } from 'react';
import { App, Alert, Button, Descriptions, Drawer, Form, Input, Select, Table, Tag } from 'antd';
import { useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime, replaceQuery } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ActorText, HashText, ReviewActions, useDetail } from '../finance/WorkflowParts';
import { usePaging } from '../hooks/usePaging';
import {
  classificationLabels, classificationOptions, decimalIdPattern, decodeExceptionDetail, decodeWithId, directionLabels, exceptionStatusLabels,
  exceptionTypeLabels, hasReconAction, pageOf, reconActions, reconLayerLabels, reconPaths, rematchTargetType, resolutionStatusLabels,
  resolutionTypeLabels, sourceTypeLabels,
} from '../reconciliation/reconModel';
import type { ReconException, ReconExceptionDetail, ReconResolution } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { useCashAccounts, useClearingAccounts } from '../reconciliation/useReconData';
import { useReconCommand } from '../reconciliation/useReconCommand';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
interface ResolutionValues { resolutionType: string; classificationCode?: string; rematchTargetId?: string; rematchAmount?: string; note: string }

const decodePage = pageOf(decodeWithId<ReconException>('exceptionId'));
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));
const filterKeys = ['status', 'exceptionType', 'reconLayer', 'clearingAccountId', 'cashAccountId', 'statementId'] as const;

/**
 * 对账异常（UC-R15、R16、AC-V4）：按账龄排序的列表（默认只看未结），按类型、状态、清算账户、银行账户、账单筛选；详情中提交处置与复核
 * <li>筛选与打开的异常存于 URL（?status=&exceptionType=…&exception=）；提交按「对账操作」、复核按「对账审批」显示，提交人本人不显示复核按钮。</li>
 * <li>归类码只列来源对象允许且对账服务当前给出的那些（设计 F3）；归类金额固定为异常差异金额。</li>
 */
export function ReconExceptions({ resources, actorId, onAuthorizationChanged }: Props) {
  const [params, setParams] = useSearchParams();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const clearing = useClearingAccounts(onAuthorizationChanged);
  const cash = useCashAccounts(onAuthorizationChanged);
  const initial = Object.fromEntries(filterKeys.flatMap(key => { const value = params.get(key); return value ? [[key, value]] : []; })) as Record<string, string>;
  const page = usePaging<ReconException>(reconPaths.exceptions, decodePage, initial);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  const exceptionId = params.get('exception');
  const go = (updates: Record<string, string | null>) => setParams(new URLSearchParams(replaceQuery(params.toString(), updates)));
  function search(values: Record<string, string | undefined>) {
    const filters = Object.fromEntries(Object.entries(values).filter(([, value]) => value?.trim()).map(([key, value]) => [key, value!.trim()])) as Record<string, string>;
    if (initial.statementId) filters.statementId = initial.statementId;
    page.applyFilters(filters);
    go(Object.fromEntries(filterKeys.filter(key => key !== 'statementId').map(key => [key, filters[key] ?? null])));
  }
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>对账异常</h1></div>
    {initial.statementId && <Alert type="info" showIcon className="finance-banner" title={`只看渠道账单 ${initial.statementId} 的异常`}
      action={<Button size="small" onClick={() => { go({ statementId: null }); page.applyFilters(Object.fromEntries(Object.entries(initial).filter(([key]) => key !== 'statementId'))); }}>看全部</Button>} />}
    <Form form={form} name="recon-exception-filters" layout="vertical" initialValues={initial} onFinish={search}>
      <div className="finance-search">
        <Form.Item name="status" label="状态" extra="不选时只看未结（待处置、待复核）"><Select allowClear placeholder="未结" options={options(exceptionStatusLabels)} /></Form.Item>
        <Form.Item name="exceptionType" label="类型"><Select allowClear placeholder="全部" showSearch={{ optionFilterProp: 'label' }} options={options(exceptionTypeLabels)} /></Form.Item>
        <Form.Item name="reconLayer" label="对账层"><Select allowClear placeholder="全部" options={options(reconLayerLabels)} /></Form.Item>
        <Form.Item name="clearingAccountId" label="清算账户"><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder="全部" loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="cashAccountId" label="银行账户"><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder="全部" loading={cash.loading}
          options={cash.items.map(item => ({ value: item.cashAccountId, label: `${item.accountName} · 尾号 ${item.accountLast4}` }))} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); search({}); }}>重置</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ReconException> rowKey="exceptionId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1240 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条，按账龄从长到短` }}
        onRow={row => ({ onClick: () => go({ exception: row.exceptionId }), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text="没有符合条件的对账异常。系统匹配不上的账单行、费用偏差和人工开立的异常会出现在这里。" /> }} columns={[
          { title: '账龄', width: 80, align: 'right', render: (_, row) => `${row.ageDays} 天` },
          { title: '类型', width: 160, render: (_, row) => <>{exceptionTypeLabels[row.exceptionType] ?? row.exceptionType}{row.blocking && <Tag color="red" className="finance-self-tag">阻塞对平</Tag>}</> },
          { title: '对账层', width: 80, render: (_, row) => reconLayerLabels[row.reconLayer] ?? row.reconLayer },
          { title: '来源对象', width: 200, render: (_, row) => <span>{sourceTypeLabels[row.sourceType] ?? row.sourceType}<div className="cell-meta mono">{row.sourceId}</div></span> },
          { title: '账户', width: 170, render: (_, row) => row.clearingAccountId ? clearing.byId.get(row.clearingAccountId)?.clearingAccountCode ?? row.clearingAccountId
            : row.cashAccountId ? (cash.byId.get(row.cashAccountId)?.accountName ?? row.cashAccountId) : '—' },
          { title: '差异', width: 130, align: 'right', render: (_, row) => <><Money value={row.differenceAmount} /> {row.currency}</> },
          { title: '状态', width: 100, render: (_, row) => <ReconTag status={row.status} labels={exceptionStatusLabels} /> },
          { title: '开立时间', width: 150, render: (_, row) => localTime(row.openedTime) },
        ]} />
    </section>}
    <FinanceNote>处置三种：重新匹配、接受时间差、归类。提交后由另一位有审批权限的同事复核，批准即应用；平台流水、预期银行流水与银行流水的归类会推送 billing 记账。{!canOperate && ' 当前账号没有对账操作权限。'}{!canApprove && ' 当前账号没有对账审批权限。'}</FinanceNote>
    {exceptionId && decimalIdPattern.test(exceptionId) && <ExceptionDrawer exceptionId={exceptionId} canOperate={canOperate} canApprove={canApprove} actorId={actorId}
      onClose={() => go({ exception: null })} onChanged={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 处置请求体：只带所选处置方式需要的字段，其余为 null（reconciliation 按组合校验）。 */
function resolutionBody(values: ResolutionValues, item: ReconException) {
  const classify = values.resolutionType === 'CLASSIFY'; const rematch = values.resolutionType === 'RE_MATCH';
  return { resolutionType: values.resolutionType, classificationCode: classify ? values.classificationCode : null, amount: classify ? item.differenceAmount : null,
    rematchTargetType: rematch ? rematchTargetType(item.reconLayer) : null, rematchTargetId: rematch ? values.rematchTargetId?.trim() : null,
    rematchAmount: rematch && item.reconLayer === 'BANK' ? values.rematchAmount?.trim() : null, note: values.note.trim() };
}

/** 异常详情：来源对象摘要、所属账户、处置历史；提交处置（操作）与复核（审批，提交人本人不显示）。 */
function ExceptionDrawer({ exceptionId, canOperate, canApprove, actorId, onClose, onChanged, onAuthorizationChanged }: {
  exceptionId: string; canOperate: boolean; canApprove: boolean; actorId: string; onClose: () => void; onChanged: () => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const decode = useMemo(() => decodeExceptionDetail(exceptionId), [exceptionId]);
  const detail = useDetail<ReconExceptionDetail>(reconPaths.exception(exceptionId), decode, onAuthorizationChanged, '对账异常详情加载失败');
  const [form] = Form.useForm<ResolutionValues>();
  const command = useReconCommand(onAuthorizationChanged, form);
  const resolutionType = Form.useWatch('resolutionType', form);
  const item = detail.item; const exception = item?.exception;
  const pending = item?.resolutions.find(resolution => resolution.status === 'PENDING_CHECK');
  const codes = item && exception ? classificationOptions(exception.sourceType, item.availableClassificationCodes) : [];
  const done = (text: string) => { void message.success(text); form.resetFields(); detail.reload(); onChanged(); };
  async function submit() {
    if (!exception) return;
    const values = await form.validateFields();
    const result = await command.run(reconPaths.resolutions(exceptionId), 'POST', resolutionBody(values, exception),
      decodeWithId<ReconResolution>('resolutionId'), '提交处置失败');
    if (result) done('已提交处置，等待复核');
  }
  async function review(resolution: ReconResolution, decision: 'APPROVED' | 'REJECTED') {
    const result = await command.run(reconPaths.resolutionApproval(resolution.resolutionId), 'POST', { decision, approvedContentHash: resolution.contentHash },
      decodeWithId<ReconResolution>('resolutionId', resolution.resolutionId), '复核处置失败');
    if (result) done(decision === 'APPROVED' ? '已批准，处置已应用' : '已驳回，异常回到待处置');
  }
  const reviewActions = pending && canApprove && pending.makerId !== actorId ? [
    { key: 'approve', label: '批准并应用', primary: true, confirm: '批准后处置在同一事务内应用，异常结案。确认？', onConfirm: () => void review(pending, 'APPROVED') },
    { key: 'reject', label: '驳回', danger: true, confirm: '驳回后异常回到待处置。确认？', onConfirm: () => void review(pending, 'REJECTED') },
  ] : [];
  return <Drawer open size="min(1080px, 100vw)" rootClassName="finance-drawer finance-page" destroyOnHidden onClose={onClose}
    title={exception ? `对账异常 · ${exceptionTypeLabels[exception.exceptionType] ?? exception.exceptionType}` : '对账异常'}>
    {detail.error ? <LoadFailure message={detail.error} onRetry={detail.reload} /> : item && exception && <>
      <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
      <Descriptions size="small" column={3} items={[
        { key: 'status', label: '状态', children: <ReconTag status={exception.status} labels={exceptionStatusLabels} /> },
        { key: 'layer', label: '对账层', children: `${reconLayerLabels[exception.reconLayer] ?? exception.reconLayer}${exception.blocking ? ' · 阻塞对平' : ''}` },
        { key: 'age', label: '账龄', children: `${exception.ageDays} 天` },
        { key: 'difference', label: '差异金额', children: <><Money value={exception.differenceAmount} /> {exception.currency}</> },
        { key: 'account', label: '账户', children: item.account?.clearingAccountCode ? `${item.account.clearingAccountCode} · ${item.account.channelCode}`
          : item.account?.cashAccountName ? `${item.account.cashAccountName} · 尾号 ${item.account.accountLast4}` : '—' },
        { key: 'entity', label: '主体', children: exception.entityId },
        { key: 'opened', label: '开立', children: <>{localTime(exception.openedTime)} {exception.openedBy ? <ActorText value={exception.openedBy} actorId={actorId} /> : '（系统）'}</> },
        { key: 'detail', label: '说明', span: 2, children: exception.detail ?? '—' },
      ]} />
      <h3 className="finance-subtitle">来源对象</h3>
      <Descriptions size="small" column={3} items={[
        { key: 'type', label: '类型', children: sourceTypeLabels[item.source.sourceType] ?? item.source.sourceType },
        { key: 'reference', label: '引用', children: <span className="mono">{item.source.reference ?? item.source.sourceId}</span> },
        { key: 'status', label: '状态', children: item.source.status ?? '—' },
        { key: 'direction', label: '方向', children: item.source.direction ? directionLabels[item.source.direction] ?? item.source.direction : '—' },
        { key: 'amount', label: '金额', children: <Money value={item.source.amount} /> },
        { key: 'date', label: '业务日期', children: item.source.businessDate ?? '—' },
      ]} />
      <h3 className="finance-subtitle">处置记录</h3>
      <section className="table-panel"><Table<ReconResolution> rowKey="resolutionId" size="small" tableLayout="fixed" pagination={false} dataSource={item.resolutions}
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
          { title: '内容摘要', width: 150, render: (_, row) => <HashText value={row.contentHash} /> },
        ]} /></section>
      {pending && <ReviewActions saving={command.saving} actions={reviewActions}
        note={<>待复核：{resolutionTypeLabels[pending.resolutionType]}{pending.classificationCode ? ` · ${pending.classificationCode}` : ''}{pending.amount ? ` · 金额 ${pending.amount}` : ''}，提交人 {pending.makerId}
          {canApprove && pending.makerId === actorId ? '（提交人不能复核自己的提交）' : ''}</>} />}
      {canOperate && exception.status === 'OPEN' && item.availableResolutionTypes.length > 0 && <>
        <h3 className="finance-subtitle">提交处置</h3>
        <Form form={form} name="recon-resolution" layout="vertical" disabled={command.saving}>
          <div className="finance-form-grid">
            <Form.Item name="resolutionType" label="处置方式" rules={[{ required: true, message: '请选择处置方式' }]}>
              <Select options={item.availableResolutionTypes.map(value => ({ value, label: resolutionTypeLabels[value] ?? value }))} /></Form.Item>
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
          <Button type="primary" loading={command.saving} onClick={() => void submit()}>提交复核</Button>
        </Form>
      </>}
    </>}
  </Drawer>;
}
