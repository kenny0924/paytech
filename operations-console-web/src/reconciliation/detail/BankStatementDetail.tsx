import { useMemo } from 'react';
import { Alert, Descriptions, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ActorText, HashText } from '../../finance/WorkflowParts';
import { usePaging } from '../../hooks/usePaging';
import { DetailSection, ReconDetailPage, useOpenDetail } from '../ReconDetail';
import { bankStatementStatusLabels, decodeWithId, directionLabels, lineMatchStatusLabels, pageOf, reconPaths } from '../reconModel';
import type { BankStatement, BankStatementLine } from '../reconModel';
import { EmptyHint, Money, ReconTag } from '../ReconParts';
import { isReconId, reconRoutes, withQuery } from '../reconRoutes';
import { useReconDetail } from '../useReconDetail';

interface Props { bankStatementId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

const linePage = pageOf(decodeWithId<BankStatementLine>('bankStatementLineId'));

/**
 * 银行对账单详情页（/finance/reconciliation/bank/statements/:bankStatementId）：对账单头、期初期末、全部银行流水（按行号分页）
 * <li>点流水进入银行流水详情页处理匹配；对账单本身只读（导入在银行对账页的抽屉中完成）。</li>
 */
export function BankStatementDetail({ bankStatementId, actorId, onAuthorizationChanged }: Props) {
  const decode = useMemo(() => decodeWithId<BankStatement>('bankStatementId', bankStatementId), [bankStatementId]);
  const detail = useReconDetail<BankStatement>(isReconId(bankStatementId) ? reconPaths.bankStatement(bankStatementId) : null, decode,
    onAuthorizationChanged, '银行对账单加载失败');
  const item = detail.item;
  return <ReconDetailPage list={{ label: '银行对账', route: reconRoutes.bank }} objectLabel="银行对账单" state={detail}
    title={item ? <>银行对账单 · <span className="mono">{item.statementRef}</span></> : null}>
    {statement => <>
      {statement.status === 'REJECTED' && <Alert type="error" showIcon className="finance-banner" title={`整单被拒：${statement.rejectReason ?? ''}`} />}
      <Descriptions size="small" column={3} items={[
        { key: 'account', label: '银行账户', children: <Link className="mono" to={reconRoutes.cashAccount(statement.cashAccountId)}>{statement.cashAccountId}</Link> },
        { key: 'period', label: '期间', children: `${statement.periodStart} — ${statement.periodEnd}` },
        { key: 'status', label: '状态', children: <ReconTag status={statement.status} labels={bankStatementStatusLabels} /> },
        { key: 'opening', label: '期初余额', children: <><Money value={statement.openingBalance} /> {statement.currency}</> },
        { key: 'closing', label: '期末余额', children: <><Money value={statement.closingBalance} /> {statement.currency}</> },
        { key: 'lines', label: '流水（声明 / 导入 / 跳过重复）', children: `${statement.declaredLineCount} / ${statement.importedLineCount} / ${statement.duplicateLineCount}` },
        { key: 'file', label: '文件', children: statement.fileName },
        { key: 'hash', label: '文件摘要', children: <HashText value={statement.fileContentHash} /> },
        { key: 'maker', label: '上传人', children: <ActorText value={statement.makerId} actorId={actorId} /> },
        { key: 'created', label: '上传时间', children: localTime(statement.createTime) },
        { key: 'bank', label: '银行对账', children: <Link to={withQuery(reconRoutes.bank, { account: statement.cashAccountId, tab: 'statements' })}>该账户的对账单</Link> },
      ]} />
      <StatementLines statement={statement} />
      <FinanceNote>流水按银行交易号去重：与之前对账单重复的流水在导入时跳过，只计入「跳过重复」。</FinanceNote>
    </>}
  </ReconDetailPage>;
}

function StatementLines({ statement }: { statement: BankStatement }) {
  const openDetail = useOpenDetail();
  const lines = usePaging<BankStatementLine>(reconPaths.bankStatementLines(statement.bankStatementId), linePage, {}, { initialPaging: { page: 1, size: 20 } });
  return <DetailSection title={`银行流水（${statement.importedLineCount}）`}>
    <section className="table-panel"><Table<BankStatementLine> rowKey="bankStatementLineId" size="small" tableLayout="fixed" dataSource={lines.data.items}
      loading={lines.loading} scroll={{ x: 1100 }} pagination={{ ...lines.pagination, showTotal: total => `共 ${total} 条` }}
      onRow={row => ({ onClick: () => openDetail(reconRoutes.bankLine(row.bankStatementLineId)), className: 'clickable-row' })}
      locale={{ emptyText: <EmptyHint text="这张对账单没有导入的流水" /> }} columns={[
        { title: '行号', width: 70, dataIndex: 'lineNo' },
        { title: '银行交易号', width: 180, render: (_, row) => <a className="mono">{row.bankTransactionRef}</a> },
        { title: '起息日', width: 110, dataIndex: 'valueDate' },
        { title: '方向', width: 90, render: (_, row) => directionLabels[row.direction] ?? row.direction },
        { title: '金额', width: 120, align: 'right', render: (_, row) => <><Money value={row.amount} /> {row.currency}</> },
        { title: '已匹配', width: 110, align: 'right', render: (_, row) => <Money value={row.matchedAmount} /> },
        { title: '对方 / 附言', render: (_, row) => <>{row.counterpartyName ?? '—'}<div className="cell-meta">{row.referenceText ?? ''}</div></> },
        { title: '状态', width: 100, render: (_, row) => <ReconTag status={row.matchStatus} labels={lineMatchStatusLabels} /> },
      ]} /></section>
  </DetailSection>;
}
