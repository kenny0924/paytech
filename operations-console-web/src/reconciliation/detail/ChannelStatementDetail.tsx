import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Descriptions, Popconfirm, Select, Table } from 'antd';
import { Link, useLocation } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ActorText } from '../../finance/WorkflowParts';
import { usePaging } from '../../hooks/usePaging';
import { useT } from '../../i18n/react';
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import {
  channelLineMatchStatusEnum, decodeStatementDetail, decodeWithId, directionEnum, hasReconAction, lineMatchStatusEnum, lineTypeEnum, pageOf, reconActions, reconLabel,
  reconOptions, reconPaths, statementStatusEnum,
} from '../reconModel';
import type { ChannelStatement, ChannelStatementDetail as StatementDetail, ChannelStatementLine, ReconcileGate } from '../reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../ReconParts';
import { highlightLineNo, isReconId, linePageOf, platformTxnsOfLine, reconRoutes, withQuery } from '../reconRoutes';
import { useReconCommand } from '../useReconCommand';
import { useReconDetail } from '../useReconDetail';

interface Props { statementId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

const linePage = pageOf(decodeWithId<ChannelStatementLine>('lineId'));
const lineSize = 20;

/** 对平门禁五项（AC-V3）：全部满足才对平；文案为 recon.gate.<项>。 */
const gateItems: readonly (keyof ReconcileGate)[] = [
  'transactionLinesMatched', 'adjustmentLinesClassified', 'noBlockingException', 'totalsConsistent', 'settlementCashAccountConfigured',
];

function GateList({ gate }: { gate: ReconcileGate }) {
  const t = useT('recon');
  return <div className="recon-gate">{gateItems.map(key => <div key={key} className={`recon-gate-item ${gate[key] ? 'is-pass' : 'is-fail'}`}>
    <span aria-hidden="true">{gate[key] ? '✓' : '✗'}</span><span>{t(`gate.${key}`)}</span></div>)}</div>;
}

/**
 * 渠道账单详情页（/finance/reconciliation/statements/:statementId）：账单头、行计数、对平门禁（匹配中时）、全部账单行（分页、按匹配状态 / 行类型筛选）、作废
 * <li>?line= 给出行号时翻到该行所在页并高亮滚动到该行（渠道流水、对账异常跳转而来）。</li>
 * <li>作废按「对账操作」按钮资源显示，只限接收中与匹配中。</li>
 */
export function ChannelStatementDetail({ statementId, resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('recon');
  const { message } = App.useApp();
  const location = useLocation();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const valid = isReconId(statementId);
  const decode = useMemo(() => decodeStatementDetail(statementId), [statementId]);
  const detail = useReconDetail<StatementDetail>(valid ? reconPaths.channelStatement(statementId) : null, decode, onAuthorizationChanged, t('statementDetail.loadFailed'));
  const command = useReconCommand(onAuthorizationChanged);
  const highlight = highlightLineNo(location.search);
  const item = detail.item; const statement = item?.statement;
  async function voidStatement() {
    const result = await command.run(reconPaths.channelStatementVoid(statementId), 'POST', undefined, decodeWithId('statementId', statementId), t('statementDetail.voidFailed'));
    if (!result) return;
    void message.success(t('statementDetail.voided')); detail.reload();
  }
  const voidAction = statement && canOperate && (statement.status === 'RECEIVING' || statement.status === 'MATCHING') && <Popconfirm
    title={t('statementDetail.confirmVoid')} okText={t('statementDetail.void')} cancelText={t('common.cancel')}
    okButtonProps={{ danger: true }} onConfirm={() => void voidStatement()}><Button danger loading={command.saving}>{t('statementDetail.void')}</Button></Popconfirm>;
  return <ReconDetailPage list={{ label: t('link.statements'), route: reconRoutes.statements }} objectLabel={t('statement.object')} state={detail} actions={voidAction}
    title={statement ? <>{t('statementDetail.titlePrefix')}<span className="mono">{statement.channelBatchRef}</span>{statement.revision > 1 ? ` · v${statement.revision}` : ''}</> : null}>
    {current => <>
      <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
      {current.statement.status === 'REJECTED' && <Alert type="error" showIcon className="finance-banner" title={t('statement.rejected', { reason: current.statement.rejectReason ?? '' })} />}
      <StatementHead detail={current} actorId={actorId} />
      {current.gate && <DetailSection title={t('statementDetail.gate')}><GateList gate={current.gate} /></DetailSection>}
      {/* 版本变化（作废等）后重新挂载账单行，读取最新匹配状态 */}
      <StatementLines key={current.statement.version} statement={current.statement} highlight={highlight} />
      <FinanceNote>{t('statementDetail.note')}{!canOperate && ' ' + t('permission.noOperate')}</FinanceNote>
    </>}
  </ReconDetailPage>;
}

function StatementHead({ detail, actorId }: { detail: StatementDetail; actorId: string }) {
  const t = useT('recon');
  const statement = detail.statement;
  return <Descriptions size="small" column={3} items={[
    { key: 'account', label: t('field.clearingAccount'), children: <Link className="mono" to={reconRoutes.clearingAccount(statement.clearingAccountId)}>{statement.clearingAccountCode}</Link> },
    { key: 'date', label: t('field.settlementDate'), children: statement.settlementDate },
    { key: 'status', label: t('field.status'), children: <ReconTag status={statement.status} kind={statementStatusEnum} /> },
    { key: 'gross', label: t('field.grossAmount'), children: <><Money value={statement.grossAmount} /> {statement.currency}</> },
    { key: 'fee', label: t('field.feeAmount'), children: <Money value={statement.feeAmount} /> },
    { key: 'net', label: t('field.netAmount'), children: <><Money value={statement.netAmount} /> {reconLabel(t, directionEnum, statement.cashDirection)}</> },
    { key: 'confirmed', label: t('field.cashConfirmed'), children: <Money value={statement.cashConfirmedAmount} /> },
    { key: 'lines', label: t('statementDetail.lineCounts'), children: `${detail.matchedLineCount} / ${detail.unmatchedLineCount} / ${detail.exceptionLineCount}` },
    { key: 'imported', label: t('statementDetail.importedDeclared'), children: `${statement.importedLineCount} / ${statement.declaredLineCount}` },
    { key: 'exceptions', label: t('statementDetail.openExceptions'), children: detail.openExceptionCount
      ? <Link to={withQuery(reconRoutes.exceptions, { statementId: statement.statementId })}>{t('statementDetail.resolveExceptions', { count: detail.openExceptionCount })}</Link> : '0' },
    { key: 'movement', label: t('statementDetail.expectedCash'), children: detail.expectedMovementId ? <span className="mono">{detail.expectedMovementId}</span> : '—' },
    { key: 'file', label: t('field.file'), children: statement.fileName },
    { key: 'maker', label: t('field.uploadedBy'), children: <ActorText value={statement.makerId} actorId={actorId} /> },
    { key: 'created', label: t('field.uploadedAt'), children: localTime(statement.createTime) },
    { key: 'reconciled', label: t('field.reconciledAt'), children: statement.reconciledTime ? localTime(statement.reconciledTime) : '—' },
  ]} />;
}

/** 全部账单行：分页、按匹配状态 / 行类型筛选；高亮行翻到所在页（行号从 1 连续）并滚动到可见。 */
function StatementLines({ statement, highlight }: { statement: ChannelStatement; highlight: number | null }) {
  const t = useT('recon');
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
  return <DetailSection title={t('statementDetail.linesTitle', { count: statement.importedLineCount })} extra={<span className="recon-filters">
    <Select allowClear placeholder={t('field.matchStatus')} style={{ width: 160 }} options={reconOptions(t, channelLineMatchStatusEnum)} onChange={value => setFilter('matchStatus', value)} />
    <Select allowClear placeholder={t('field.lineType')} style={{ width: 160 }} options={reconOptions(t, lineTypeEnum)} onChange={value => setFilter('lineType', value)} />
  </span>}>
    {highlight && !highlighted && !lines.loading && <Alert type="info" showIcon className="finance-banner" title={t('statementDetail.highlightMissing', { lineNo: highlight })} />}
    <section className="table-panel"><Table<ChannelStatementLine> rowKey="lineId" size="small" tableLayout="fixed" dataSource={lines.data.items}
      loading={lines.loading} scroll={{ x: 1180 }} pagination={{ ...lines.pagination, showTotal: total => t('common.totalLines', { total }) }}
      rowClassName={row => row.lineNo === highlight ? 'recon-line-highlight' : ''}
      locale={{ emptyText: <EmptyHint text={Object.keys(filters).length ? t('statementDetail.linesEmptyFiltered') : t('statementDetail.linesEmpty')} /> }} columns={[
        { title: t('field.lineNo'), width: 70, dataIndex: 'lineNo' },
        { title: t('field.type'), width: 80, render: (_, row) => reconLabel(t, lineTypeEnum, row.lineType) },
        { title: t('field.direction'), width: 90, render: (_, row) => reconLabel(t, directionEnum, row.direction) },
        { title: t('field.amount'), width: 120, align: 'right', render: (_, row) => <Money value={row.amount} /> },
        { title: t('field.lineFee'), width: 100, align: 'right', render: (_, row) => <Money value={row.feeAmount} /> },
        { title: t('field.transactionDate'), width: 110, render: (_, row) => row.transactionDate ?? '—' },
        { title: t('field.externalTransactionId'), width: 200, render: (_, row) => row.externalTransactionId
          ? <span className="mono">{row.externalTransactionId}<div className="cell-meta">{row.referenceType}</div></span> : <span className="muted">—</span> },
        { title: t('field.matchStatus'), width: 100, render: (_, row) => <ReconTag status={row.matchStatus} kind={lineMatchStatusEnum} /> },
        { title: t('link.platformTxns'), width: 190, render: (_, row) => row.platformTxnId
          ? <Link className="mono" to={platformTxnsOfLine({ clearingAccountId: statement.clearingAccountId, externalTransactionId: row.externalTransactionId })}>
            {row.platformTxnId}<div className="cell-meta">{row.matchMethod === 'MANUAL' ? t('match.manual') : t('match.auto')}</div></Link>
          : row.resolutionId ? <span className="cell-meta">{t('statementDetail.classified', { resolutionId: row.resolutionId })}</span> : <span className="muted">—</span> },
        { title: t('field.actualFee'), width: 110, align: 'right', render: (_, row) => <Money value={row.actualFee} /> },
      ]} /></section>
  </DetailSection>;
}
