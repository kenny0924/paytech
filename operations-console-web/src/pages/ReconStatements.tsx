import { useMemo, useState } from 'react';
import { Alert, App, Button, Descriptions, Drawer, Form, Input, Select, Table } from 'antd';
import { Link, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime, replaceQuery } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ActorText, ReviewActions, useDetail } from '../finance/WorkflowParts';
import { usePaging } from '../hooks/usePaging';
import { ImportDrawer } from '../reconciliation/ImportDrawer';
import {
  decimalIdPattern, decodeStatementDetail, decodeWithId, directionLabels, hasReconAction, lineMatchStatusLabels, lineTypeLabels, pageOf, reconActions,
  reconPaths, statementStatusLabels,
} from '../reconciliation/reconModel';
import type { ChannelStatement, ChannelStatementDetail, ChannelStatementLine, ReconcileGate } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { useClearingAccounts } from '../reconciliation/useReconData';
import { useReconCommand } from '../reconciliation/useReconCommand';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

const decodePage = pageOf(decodeWithId<ChannelStatement>('statementId'));
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));

/** 对平门禁五项（AC-V3）：全部满足才对平。 */
const gateItems: readonly [keyof ReconcileGate, string][] = [
  ['transactionLinesMatched', '交易行都已匹配到平台流水'], ['adjustmentLinesClassified', '调整行与无交易号费用行都已归类'],
  ['noBlockingException', '没有阻塞对平的开放异常'], ['totalsConsistent', '各行回放与账单头合计一致'],
  ['settlementCashAccountConfigured', '清算账户已配置到账银行账户'],
];

/**
 * 渠道账单（UC-R06–R09）：上传（分段进度、被拒原因表）、列表、详情（行匹配、门禁）、作废
 * <li>打开的账单存于 URL（?statement=）；上传与作废按「对账操作」按钮资源显示，作废只限接收中与匹配中。</li>
 */
export function ReconStatements({ resources, actorId, onAuthorizationChanged }: Props) {
  const [params, setParams] = useSearchParams();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const [uploading, setUploading] = useState(false);
  const clearing = useClearingAccounts(onAuthorizationChanged);
  const page = usePaging<ChannelStatement>(reconPaths.channelStatements, decodePage);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  const statementId = params.get('statement');
  const open = (id: string | null) => setParams(new URLSearchParams(replaceQuery(params.toString(), { statement: id })));
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>渠道账单</h1></div>
    <Form form={form} name="recon-statement-filters" layout="vertical" onFinish={values => page.applyFilters(Object.fromEntries(
      Object.entries(values).filter(([, value]) => value?.trim()).map(([key, value]) => [key, value!.trim()])) as Record<string, string>)}>
      <div className="finance-search">
        <Form.Item name="clearingAccountId" label="清算账户"><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder="全部" loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="channelBatchRef" label="批次号"><Input allowClear maxLength={128} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={options(statementStatusLabels)} /></Form.Item>
        <Form.Item name="settlementDateFrom" label="结算日起" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="settlementDateTo" label="结算日止" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button>
          {canOperate && <Button type="primary" onClick={() => setUploading(true)}>上传渠道账单</Button>}</div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ChannelStatement> rowKey="statementId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1180 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} onRow={row => ({ onClick: () => open(row.statementId), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={canOperate ? '还没有渠道账单。按标准 CSV 上传后，系统逐行匹配平台流水。' : '没有符合条件的渠道账单'} /> }}
        columns={[
          { title: '批次号', width: 200, render: (_, row) => <a className="mono">{row.channelBatchRef}{row.revision > 1 ? ` · v${row.revision}` : ''}</a> },
          { title: '清算账户', width: 160, dataIndex: 'clearingAccountCode' },
          { title: '结算日', width: 110, dataIndex: 'settlementDate' },
          { title: '净额', width: 140, align: 'right', render: (_, row) => <><Money value={row.netAmount} /> {row.currency}</> },
          { title: '方向', width: 90, render: (_, row) => directionLabels[row.cashDirection] ?? row.cashDirection },
          { title: '行数', width: 110, align: 'right', render: (_, row) => `${row.importedLineCount} / ${row.declaredLineCount}` },
          { title: '状态', width: 100, render: (_, row) => <ReconTag status={row.status} labels={statementStatusLabels} /> },
          { title: '上传人', width: 160, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
          { title: '上传时间', width: 150, render: (_, row) => localTime(row.createTime) },
        ]} />
    </section>}
    <FinanceNote>同一清算账户 + 批次号只允许一个未作废版本：要更正，先作废匹配中的版本再重新上传；已对平的账单不能更正或作废。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
    {uploading && <ImportDrawer kind="channel" onClose={() => setUploading(false)} onImported={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
    {statementId && decimalIdPattern.test(statementId) && <StatementDrawer statementId={statementId} canOperate={canOperate} actorId={actorId}
      onClose={() => open(null)} onChanged={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function GateList({ gate }: { gate: ReconcileGate }) {
  return <div className="recon-gate">{gateItems.map(([key, label]) => <div key={key} className={`recon-gate-item ${gate[key] ? 'is-pass' : 'is-fail'}`}>
    <span aria-hidden="true">{gate[key] ? '✓' : '✗'}</span><span>{label}</span></div>)}</div>;
}

/** 账单详情：账单头、行计数、门禁（匹配中时）、按行的匹配状态 / 平台流水 / 实际费用、作废。 */
function StatementDrawer({ statementId, canOperate, actorId, onClose, onChanged, onAuthorizationChanged }: {
  statementId: string; canOperate: boolean; actorId: string; onClose: () => void; onChanged: () => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const decode = useMemo(() => decodeStatementDetail(statementId), [statementId]);
  const detail = useDetail<ChannelStatementDetail>(reconPaths.channelStatement(statementId), decode, onAuthorizationChanged, '渠道账单详情加载失败');
  const command = useReconCommand(onAuthorizationChanged);
  const [filters, setFilters] = useState<Record<string, string>>({});
  const lineDecode = useMemo(() => pageOf(decodeWithId<ChannelStatementLine>('lineId')), []);
  const lines = usePaging<ChannelStatementLine>(reconPaths.channelStatementLines(statementId), lineDecode, {});
  const item = detail.item; const statement = item?.statement;
  async function voidStatement() {
    const result = await command.run(reconPaths.channelStatementVoid(statementId), 'POST', undefined, decodeWithId('statementId', statementId), '作废渠道账单失败');
    if (!result) return;
    void message.success('已作废，账单行的匹配与费用已释放'); detail.reload(); lines.refresh(); onChanged();
  }
  const setFilter = (key: string, value?: string) => { const next = { ...filters }; if (value) next[key] = value; else delete next[key]; setFilters(next); lines.applyFilters(next); };
  return <Drawer open size="min(1180px, 100vw)" rootClassName="finance-drawer finance-page" destroyOnHidden onClose={onClose}
    title={statement ? `渠道账单 · ${statement.channelBatchRef}${statement.revision > 1 ? ` · v${statement.revision}` : ''}` : '渠道账单'}>
    {detail.error ? <LoadFailure message={detail.error} onRetry={detail.reload} /> : item && statement && <>
      <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
      {statement.status === 'REJECTED' && <Alert type="error" showIcon className="finance-banner" title={`整单被拒：${statement.rejectReason ?? ''}`} />}
      <Descriptions size="small" column={3} items={[
        { key: 'account', label: '清算账户', children: statement.clearingAccountCode },
        { key: 'date', label: '结算日', children: statement.settlementDate },
        { key: 'status', label: '状态', children: <ReconTag status={statement.status} labels={statementStatusLabels} /> },
        { key: 'gross', label: '总额', children: <Money value={statement.grossAmount} /> },
        { key: 'fee', label: '费用', children: <Money value={statement.feeAmount} /> },
        { key: 'net', label: '净额', children: <><Money value={statement.netAmount} /> {directionLabels[statement.cashDirection] ?? statement.cashDirection}</> },
        { key: 'confirmed', label: '已到账', children: <Money value={statement.cashConfirmedAmount} /> },
        { key: 'lines', label: '行（已匹配 / 未匹配 / 异常）', children: `${item.matchedLineCount} / ${item.unmatchedLineCount} / ${item.exceptionLineCount}` },
        { key: 'exceptions', label: '开放异常', children: item.openExceptionCount
          ? <Link to={`/finance/reconciliation/exceptions?statementId=${statementId}`}>{item.openExceptionCount} 条，去处置</Link> : '0' },
        { key: 'file', label: '文件', children: statement.fileName },
        { key: 'maker', label: '上传人', children: <ActorText value={statement.makerId} actorId={actorId} /> },
        { key: 'reconciled', label: '对平时间', children: statement.reconciledTime ? localTime(statement.reconciledTime) : '—' },
      ]} />
      {item.gate && <><h3 className="finance-subtitle">对平门禁</h3><GateList gate={item.gate} /></>}
      <h3 className="finance-subtitle">账单行</h3>
      <div className="finance-search-inline">
        <Select allowClear placeholder="匹配状态" style={{ width: 160 }} options={options({ UNMATCHED: '未匹配', MATCHED: '已匹配', EXCEPTION: '异常' })}
          onChange={value => setFilter('matchStatus', value)} />
        <Select allowClear placeholder="行类型" style={{ width: 160 }} options={options(lineTypeLabels)} onChange={value => setFilter('lineType', value)} />
      </div>
      <section className="table-panel"><Table<ChannelStatementLine> rowKey="lineId" size="small" tableLayout="fixed" dataSource={lines.data.items}
        loading={lines.loading} scroll={{ x: 1100 }} pagination={{ ...lines.pagination, showTotal: total => `共 ${total} 行` }}
        locale={{ emptyText: <EmptyHint text="没有符合条件的账单行" /> }} columns={[
          { title: '行号', width: 70, dataIndex: 'lineNo' },
          { title: '类型', width: 80, render: (_, row) => lineTypeLabels[row.lineType] ?? row.lineType },
          { title: '方向', width: 90, render: (_, row) => directionLabels[row.direction] ?? row.direction },
          { title: '金额', width: 120, align: 'right', render: (_, row) => <Money value={row.amount} /> },
          { title: '行费用', width: 100, align: 'right', render: (_, row) => <Money value={row.feeAmount} /> },
          { title: '外部交易号', width: 200, render: (_, row) => row.externalTransactionId
            ? <span className="mono">{row.externalTransactionId}<div className="cell-meta">{row.referenceType}</div></span> : <span className="muted">—</span> },
          { title: '匹配状态', width: 100, render: (_, row) => <ReconTag status={row.matchStatus} labels={lineMatchStatusLabels} /> },
          { title: '平台流水', width: 190, render: (_, row) => row.platformTxnId
            ? <span className="mono">{row.platformTxnId}<div className="cell-meta">{row.matchMethod === 'MANUAL' ? '人工' : '自动'}</div></span>
            : row.resolutionId ? <span className="cell-meta">已归类（处置 {row.resolutionId}）</span> : <span className="muted">—</span> },
          { title: '实际费用', width: 110, align: 'right', render: (_, row) => <Money value={row.actualFee} /> },
        ]} /></section>
      {canOperate && (statement.status === 'RECEIVING' || statement.status === 'MATCHING') && <ReviewActions saving={command.saving} actions={[{
        key: 'void', label: '作废', danger: true, confirm: '作废后本账单行的匹配和费用失效，平台流水回到未匹配，本账单的开放异常自动关闭。确认作废？',
        onConfirm: () => void voidStatement() }]} />}
    </>}
  </Drawer>;
}
