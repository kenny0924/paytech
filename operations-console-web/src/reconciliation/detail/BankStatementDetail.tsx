import { useMemo } from 'react';
import { Alert, Descriptions, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ActorText, HashText } from '../../finance/WorkflowParts';
import { usePaging } from '../../hooks/usePaging';
import { useT } from '../../i18n/react';
import { DetailSection, ReconDetailPage, useOpenDetail } from '../ReconDetail';
import { bankStatementStatusEnum, decodeWithId, directionEnum, lineMatchStatusEnum, pageOf, reconLabel, reconPaths } from '../reconModel';
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
  const t = useT('recon');
  const decode = useMemo(() => decodeWithId<BankStatement>('bankStatementId', bankStatementId), [bankStatementId]);
  const detail = useReconDetail<BankStatement>(isReconId(bankStatementId) ? reconPaths.bankStatement(bankStatementId) : null, decode,
    onAuthorizationChanged, t('bankStatement.loadFailed'));
  const item = detail.item;
  return <ReconDetailPage list={{ label: t('bank.listLabel'), route: reconRoutes.bank }} objectLabel={t('bankStatement.object')} state={detail}
    title={item ? <>{t('bankStatement.titlePrefix')}<span className="mono">{item.statementRef}</span></> : null}>
    {statement => <>
      {statement.status === 'REJECTED' && <Alert type="error" showIcon className="finance-banner" title={t('statement.rejected', { reason: statement.rejectReason ?? '' })} />}
      <Descriptions size="small" column={3} items={[
        { key: 'account', label: t('field.cashAccount'), children: <Link className="mono" to={reconRoutes.cashAccount(statement.cashAccountId)}>{statement.cashAccountId}</Link> },
        { key: 'period', label: t('field.period'), children: `${statement.periodStart} — ${statement.periodEnd}` },
        { key: 'status', label: t('field.status'), children: <ReconTag status={statement.status} kind={bankStatementStatusEnum} /> },
        { key: 'opening', label: t('bankStatement.openingBalance'), children: <><Money value={statement.openingBalance} /> {statement.currency}</> },
        { key: 'closing', label: t('bankStatement.closingBalance'), children: <><Money value={statement.closingBalance} /> {statement.currency}</> },
        { key: 'lines', label: t('bankStatement.lineCounts'), children: `${statement.declaredLineCount} / ${statement.importedLineCount} / ${statement.duplicateLineCount}` },
        { key: 'file', label: t('field.file'), children: statement.fileName },
        { key: 'hash', label: t('field.fileHash'), children: <HashText value={statement.fileContentHash} /> },
        { key: 'maker', label: t('field.uploadedBy'), children: <ActorText value={statement.makerId} actorId={actorId} /> },
        { key: 'created', label: t('field.uploadedAt'), children: localTime(statement.createTime) },
        { key: 'bank', label: t('bank.listLabel'), children: <Link to={withQuery(reconRoutes.bank, { account: statement.cashAccountId, tab: 'statements' })}>{t('bankStatement.accountStatements')}</Link> },
      ]} />
      <StatementLines statement={statement} />
      <FinanceNote>{t('bankStatement.note')}</FinanceNote>
    </>}
  </ReconDetailPage>;
}

function StatementLines({ statement }: { statement: BankStatement }) {
  const t = useT('recon');
  const openDetail = useOpenDetail();
  const lines = usePaging<BankStatementLine>(reconPaths.bankStatementLines(statement.bankStatementId), linePage, {}, { initialPaging: { page: 1, size: 20 } });
  return <DetailSection title={t('bankStatement.linesTitle', { count: statement.importedLineCount })}>
    <section className="table-panel"><Table<BankStatementLine> rowKey="bankStatementLineId" size="small" tableLayout="fixed" dataSource={lines.data.items}
      loading={lines.loading} scroll={{ x: 1100 }} pagination={{ ...lines.pagination, showTotal: total => t('common.total', { total }) }}
      onRow={row => ({ onClick: () => openDetail(reconRoutes.bankLine(row.bankStatementLineId)), className: 'clickable-row' })}
      locale={{ emptyText: <EmptyHint text={t('bankStatement.linesEmpty')} /> }} columns={[
        { title: t('field.lineNo'), width: 70, dataIndex: 'lineNo' },
        { title: t('field.bankTransactionRef'), width: 180, render: (_, row) => <a className="mono">{row.bankTransactionRef}</a> },
        { title: t('field.valueDate'), width: 110, dataIndex: 'valueDate' },
        { title: t('field.direction'), width: 90, render: (_, row) => reconLabel(t, directionEnum, row.direction) },
        { title: t('field.amount'), width: 120, align: 'right', render: (_, row) => <><Money value={row.amount} /> {row.currency}</> },
        { title: t('field.matchedAmount'), width: 110, align: 'right', render: (_, row) => <Money value={row.matchedAmount} /> },
        { title: t('field.counterpartyReference'), render: (_, row) => <>{row.counterpartyName ?? '—'}<div className="cell-meta">{row.referenceText ?? ''}</div></> },
        { title: t('field.status'), width: 100, render: (_, row) => <ReconTag status={row.matchStatus} kind={lineMatchStatusEnum} /> },
      ]} /></section>
  </DetailSection>;
}
