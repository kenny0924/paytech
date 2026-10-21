import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Descriptions, Popconfirm, Select, Table } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ActorText } from '../../finance/WorkflowParts';
import { usePaging } from '../../hooks/usePaging';
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import {
  channelLineMatchStatusLabels, decodeStatementDetail, decodeWithId, directionLabels, hasReconAction, lineMatchStatusLabels, lineTypeLabels, pageOf, reconActions, reconPaths,
  statementStatusLabels,
} from '../reconModel';
import type { ChannelStatement, ChannelStatementDetail as StatementDetail, ChannelStatementLine, ReconcileGate } from '../reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../ReconParts';
import { highlightLineNo, isReconId, linePageOf, platformTxnsOfLine, reconRoutes, withQuery } from '../reconRoutes';
import { useReconCommand } from '../useReconCommand';
import { useReconDetail } from '../useReconDetail';

interface Props { statementId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

const linePage = pageOf(decodeWithId<ChannelStatementLine>('lineId'));
const lineSize = 20;
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));

/** 对平门禁五项（AC-V3）：全部满足才对平。 */
const gateItems: readonly [keyof ReconcileGate, string][] = [
  ['transactionLinesMatched', '交易行都已匹配到平台流水'], ['adjustmentLinesClassified', '调整行与无交易号费用行都已归类'],
  ['noBlockingException', '没有阻塞对平的开放异常'], ['totalsConsistent', '各行回放与账单头合计一致'],
  ['settlementCashAccountConfigured', '清算账户已配置到账银行账户'],
];

function GateList({ gate }: { gate: ReconcileGate }) {
  return <div className="recon-gate">{gateItems.map(([key, label]) => <div key={key} className={`recon-gate-item ${gate[key] ? 'is-pass' : 'is-fail'}`}>
    <span aria-hidden="true">{gate[key] ? '✓' : '✗'}</span><span>{label}</span></div>)}</div>;
}

/**
 * 渠道账单详情页（/finance/reconciliation/statements/:statementId）：账单头、行计数、对平门禁（匹配中时）、全部账单行（分页、按匹配状态 / 行类型筛选）、作废
 * <li>?line= 给出行号时翻到该行所在页并高亮滚动到该行（渠道流水、对账异常跳转而来）。</li>
 * <li>作废按「对账操作」按钮资源显示，只限接收中与匹配中。</li>
 */
export function ChannelStatementDetail({ statementId, resources, actorId, onAuthorizationChanged }: Props) {
  const { message } = App.useApp();
  const location = useLocation();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const valid = isReconId(statementId);
  const decode = useMemo(() => decodeStatementDetail(statementId), [statementId]);
  const detail = useReconDetail<StatementDetail>(valid ? reconPaths.channelStatement(statementId) : null, decode, onAuthorizationChanged, '渠道账单详情加载失败');
  const command = useReconCommand(onAuthorizationChanged);
  const highlight = highlightLineNo(location.search);
  const item = detail.item; const statement = item?.statement;
  async function voidStatement() {
    const result = await command.run(reconPaths.channelStatementVoid(statementId), 'POST', undefined, decodeWithId('statementId', statementId), '作废渠道账单失败');
    if (!result) return;
    void message.success('已作废，账单行的匹配与费用已释放'); detail.reload();
  }
  const voidAction = statement && canOperate && (statement.status === 'RECEIVING' || statement.status === 'MATCHING') && <Popconfirm
    title="作废后本账单行的匹配和费用失效，平台流水回到未匹配，本账单的开放异常自动关闭。确认作废？" okText="作废" cancelText="取消"
    okButtonProps={{ danger: true }} onConfirm={() => void voidStatement()}><Button danger loading={command.saving}>作废</Button></Popconfirm>;
  return <ReconDetailPage list={{ label: '渠道账单', route: reconRoutes.statements }} objectLabel="渠道账单" state={detail} actions={voidAction}
    title={statement ? <>渠道账单 · <span className="mono">{statement.channelBatchRef}</span>{statement.revision > 1 ? ` · v${statement.revision}` : ''}</> : null}>
    {current => <>
      <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
      {current.statement.status === 'REJECTED' && <Alert type="error" showIcon className="finance-banner" title={`整单被拒：${current.statement.rejectReason ?? ''}`} />}
      <StatementHead detail={current} actorId={actorId} />
      {current.gate && <DetailSection title="对平门禁"><GateList gate={current.gate} /></DetailSection>}
      {/* 版本变化（作废等）后重新挂载账单行，读取最新匹配状态 */}
      <StatementLines key={current.statement.version} statement={current.statement} highlight={highlight} />
      <FinanceNote>同一清算账户 + 批次号只允许一个未作废版本：要更正，先作废匹配中的版本再重新上传；已对平的账单不能更正或作废。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
    </>}
  </ReconDetailPage>;
}

function StatementHead({ detail, actorId }: { detail: StatementDetail; actorId: string }) {
  const statement = detail.statement;
  return <Descriptions size="small" column={3} items={[
    { key: 'account', label: '清算账户', children: <Link className="mono" to={reconRoutes.clearingAccount(statement.clearingAccountId)}>{statement.clearingAccountCode}</Link> },
    { key: 'date', label: '结算日', children: statement.settlementDate },
    { key: 'status', label: '状态', children: <ReconTag status={statement.status} labels={statementStatusLabels} /> },
    { key: 'gross', label: '总额', children: <><Money value={statement.grossAmount} /> {statement.currency}</> },
    { key: 'fee', label: '费用', children: <Money value={statement.feeAmount} /> },
    { key: 'net', label: '净额', children: <><Money value={statement.netAmount} /> {directionLabels[statement.cashDirection] ?? statement.cashDirection}</> },
    { key: 'confirmed', label: '已到账', children: <Money value={statement.cashConfirmedAmount} /> },
    { key: 'lines', label: '行（已匹配 / 未匹配 / 异常）', children: `${detail.matchedLineCount} / ${detail.unmatchedLineCount} / ${detail.exceptionLineCount}` },
    { key: 'imported', label: '已导入 / 声明行数', children: `${statement.importedLineCount} / ${statement.declaredLineCount}` },
    { key: 'exceptions', label: '开放异常', children: detail.openExceptionCount
      ? <Link to={withQuery(reconRoutes.exceptions, { statementId: statement.statementId })}>{detail.openExceptionCount} 条，去处置</Link> : '0' },
    { key: 'movement', label: '预期到账', children: detail.expectedMovementId ? <span className="mono">{detail.expectedMovementId}</span> : '—' },
    { key: 'file', label: '文件', children: statement.fileName },
    { key: 'maker', label: '上传人', children: <ActorText value={statement.makerId} actorId={actorId} /> },
    { key: 'created', label: '上传时间', children: localTime(statement.createTime) },
    { key: 'reconciled', label: '对平时间', children: statement.reconciledTime ? localTime(statement.reconciledTime) : '—' },
  ]} />;
}

/** 全部账单行：分页、按匹配状态 / 行类型筛选；高亮行翻到所在页（行号从 1 连续）并滚动到可见。 */
function StatementLines({ statement, highlight }: { statement: ChannelStatement; highlight: number | null }) {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const lines = usePaging<ChannelStatementLine>(reconPaths.channelStatementLines(statement.statementId), linePage, {},
    { initialPaging: { page: highlight ? linePageOf(highlight, lineSize) : 1, size: lineSize } });
  const setFilter = (key: string, value?: string) => {
    const next = { ...filters }; if (value) next[key] = value; else delete next[key];
    setFilters(next); lines.applyFilters(next);
  };
  const highlighted = lines.data.items.some(row => row.lineNo === highlight);
  useEffect(() => {
    if (highlighted) document.querySelector('.recon-line-highlight')?.scrollIntoView({ block: 'center' });
  }, [highlighted]);
  return <DetailSection title={`账单行（${statement.importedLineCount}）`} extra={<span className="recon-filters">
    <Select allowClear placeholder="匹配状态" style={{ width: 160 }} options={options(channelLineMatchStatusLabels)} onChange={value => setFilter('matchStatus', value)} />
    <Select allowClear placeholder="行类型" style={{ width: 160 }} options={options(lineTypeLabels)} onChange={value => setFilter('lineType', value)} />
  </span>}>
    {highlight && !highlighted && !lines.loading && <Alert type="info" showIcon className="finance-banner" title={`第 ${highlight} 行不在当前页或筛选结果中`} />}
    <section className="table-panel"><Table<ChannelStatementLine> rowKey="lineId" size="small" tableLayout="fixed" dataSource={lines.data.items}
      loading={lines.loading} scroll={{ x: 1180 }} pagination={{ ...lines.pagination, showTotal: total => `共 ${total} 行` }}
      rowClassName={row => row.lineNo === highlight ? 'recon-line-highlight' : ''}
      locale={{ emptyText: <EmptyHint text={Object.keys(filters).length ? '没有符合条件的账单行' : '这张账单还没有导入的行'} /> }} columns={[
        { title: '行号', width: 70, dataIndex: 'lineNo' },
        { title: '类型', width: 80, render: (_, row) => lineTypeLabels[row.lineType] ?? row.lineType },
        { title: '方向', width: 90, render: (_, row) => directionLabels[row.direction] ?? row.direction },
        { title: '金额', width: 120, align: 'right', render: (_, row) => <Money value={row.amount} /> },
        { title: '行费用', width: 100, align: 'right', render: (_, row) => <Money value={row.feeAmount} /> },
        { title: '交易日', width: 110, render: (_, row) => row.transactionDate ?? '—' },
        { title: '外部交易号', width: 200, render: (_, row) => row.externalTransactionId
          ? <span className="mono">{row.externalTransactionId}<div className="cell-meta">{row.referenceType}</div></span> : <span className="muted">—</span> },
        { title: '匹配状态', width: 100, render: (_, row) => <ReconTag status={row.matchStatus} labels={lineMatchStatusLabels} /> },
        { title: '平台流水', width: 190, render: (_, row) => row.platformTxnId
          ? <Link className="mono" to={platformTxnsOfLine({ clearingAccountId: statement.clearingAccountId, externalTransactionId: row.externalTransactionId })}>
            {row.platformTxnId}<div className="cell-meta">{row.matchMethod === 'MANUAL' ? '人工' : '自动'}</div></Link>
          : row.resolutionId ? <span className="cell-meta">已归类（处置 {row.resolutionId}）</span> : <span className="muted">—</span> },
        { title: '实际费用', width: 110, align: 'right', render: (_, row) => <Money value={row.actualFee} /> },
      ]} /></section>
  </DetailSection>;
}
