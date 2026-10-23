import { useMemo, useState } from 'react';
import { App, Button, Descriptions, Form, Input, Popconfirm, Table, Tag } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { requestAllPages } from '../../api/paging';
import { session } from '../../api/session';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ActorText } from '../../finance/WorkflowParts';
import { useFinanceList } from '../../finance/useFinanceData';
import { useT } from '../../i18n/react';
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import {
  bankMatchStatusEnum, decodeBankLineDetail, decodeBankMatchList, decodeWithId, directionEnum, exceptionStatusEnum, exceptionTypeEnum,
  expectedRemaining, hasReconAction, lineMatchStatusEnum, manualMatchTotals, movementStatusEnum, pageOf, reconActions, reconLabel, reconPaths,
} from '../reconModel';
import type { Allocation, BankLineDetail as LineDetail, BankLineMatch, BankMatch, BankStatementLine, ExpectedMovement, ReconException } from '../reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../ReconParts';
import { isReconId, movementSourceLink, reconRoutes } from '../reconRoutes';
import { useReconCommand } from '../useReconCommand';
import { useReconDetail } from '../useReconDetail';

interface Props { bankStatementLineId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
type Command = ReturnType<typeof useReconCommand>;

const movementPage = pageOf(decodeWithId<ExpectedMovement>('expectedMovementId'));

/**
 * 银行流水详情页（/finance/reconciliation/bank/lines/:bankStatementLineId）：流水信息、匹配建议与已确认匹配（确认 / 拒绝）、人工多选匹配（AC-V5）、
 * 流水上的未结异常与人工开异常
 * <li>写操作按「对账操作」按钮资源显示；每次写操作后重新读取详情与预期银行流水。</li>
 */
export function BankLineDetail({ bankStatementLineId, resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('recon');
  const navigate = useNavigate();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const decode = useMemo(() => decodeBankLineDetail(bankStatementLineId), [bankStatementLineId]);
  const detail = useReconDetail<LineDetail>(isReconId(bankStatementLineId) ? reconPaths.bankLine(bankStatementLineId) : null, decode,
    onAuthorizationChanged, t('bankLine.loadFailed'));
  const command = useReconCommand(onAuthorizationChanged);
  const [reload, setReload] = useState(0);
  const done = () => { detail.reload(); setReload(value => value + 1); };
  const line = detail.item?.line;
  async function openException() {
    if (!line) return;
    const result = await command.run(reconPaths.exceptions, 'POST', { sourceType: 'BANK_STATEMENT_LINE', sourceId: line.bankStatementLineId },
      decodeWithId<ReconException>('exceptionId'), t('bankLine.openExceptionFailed'));
    if (result) void navigate(reconRoutes.exception(result.exceptionId));
  }
  const exceptionAction = line && canOperate && line.matchStatus !== 'MATCHED' && line.matchStatus !== 'EXCEPTION' && !detail.item?.openExceptions.length
    && <Popconfirm title={t('bankLine.confirmOpenException')} okText={t('bankLine.open')} cancelText={t('common.cancel')}
      onConfirm={() => void openException()}><Button loading={command.saving}>{t('bankLine.openException')}</Button></Popconfirm>;
  return <ReconDetailPage list={{ label: t('bank.listLabel'), route: reconRoutes.bank }} objectLabel={t('bankLine.object')} state={detail} actions={exceptionAction}
    title={line ? <>{t('bankLine.titlePrefix')}<span className="mono">{line.bankTransactionRef}</span></> : null}>
    {current => <>
      <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); done(); }} />
      <Descriptions size="small" column={3} items={[
        { key: 'date', label: t('field.valueDate'), children: current.line.valueDate },
        { key: 'direction', label: t('field.direction'), children: reconLabel(t, directionEnum, current.line.direction) },
        { key: 'amount', label: t('field.amount'), children: <><Money value={current.line.amount} /> {current.line.currency}</> },
        { key: 'matched', label: t('field.matchedAmount'), children: <Money value={current.line.matchedAmount} /> },
        { key: 'status', label: t('field.status'), children: <ReconTag status={current.line.matchStatus} kind={lineMatchStatusEnum} /> },
        { key: 'counterparty', label: t('field.counterparty'), children: current.line.counterpartyName ?? '—' },
        { key: 'statement', label: t('link.bankStatement'), children: <Link className="mono" to={reconRoutes.bankStatement(current.line.bankStatementId)}>
          {current.statement ? t('bankLine.statementLine', { statementRef: current.statement.statementRef, lineNo: current.line.lineNo }) : current.line.bankStatementId}</Link> },
        { key: 'account', label: t('field.cashAccount'), children: <Link className="mono" to={reconRoutes.cashAccount(current.line.cashAccountId)}>{current.line.cashAccountId}</Link> },
        { key: 'reference', label: t('field.reference'), children: current.line.referenceText ?? '—' },
      ]} />
      <MatchList matches={current.matches} canOperate={canOperate} actorId={actorId} command={command} onDone={done} />
      {canOperate && ['UNMATCHED', 'PARTIALLY_MATCHED'].includes(current.line.matchStatus)
        && <ManualMatch key={`${current.line.version}:${reload}`} line={current.line} command={command} reload={reload} onDone={done}
          onAuthorizationChanged={onAuthorizationChanged} />}
      <OpenExceptions exceptions={current.openExceptions} />
      <FinanceNote>{t('bankLine.note')}{!canOperate && ' ' + t('permission.noOperate')}</FinanceNote>
    </>}
  </ReconDetailPage>;
}

/** 匹配建议与已确认 / 已拒绝匹配：建议可确认或拒绝；预期银行流水来源为渠道结算时链到渠道账单。 */
function MatchList({ matches, canOperate, actorId, command, onDone }: {
  matches: readonly BankLineMatch[]; canOperate: boolean; actorId: string; command: Command; onDone: () => void;
}) {
  const t = useT('recon');
  const { message } = App.useApp();
  async function decide(match: BankMatch, confirm: boolean) {
    const result = await command.run(confirm ? reconPaths.bankMatchConfirmation(match.bankMatchId) : reconPaths.bankMatchRejection(match.bankMatchId), 'POST',
      undefined, decodeWithId<BankMatch>('bankMatchId', match.bankMatchId), confirm ? t('bankLine.confirmFailed') : t('bankLine.rejectFailed'));
    if (!result) return;
    void message.success(confirm ? t('bankLine.confirmed') : t('bankLine.rejected')); onDone();
  }
  const proposed = matches.some(item => item.match.status === 'PROPOSED');
  return <DetailSection title={t('bankLine.matchesTitle')}>
    <section className="table-panel"><Table<BankLineMatch> rowKey={row => row.match.bankMatchId} size="small" tableLayout="fixed" pagination={false}
      dataSource={[...matches]} scroll={{ x: 980 }}
      locale={{ emptyText: <EmptyHint text={t('bankLine.noProposal')} /> }}
      columns={[
        { title: t('link.expectedMovement'), render: (_, row) => { const movement = row.expectedMovement; const link = movement ? movementSourceLink(movement) : null;
          return <span className="mono">{row.match.expectedMovementId}<div className="cell-meta">{movement
            ? <>{movement.movementType} · {link ? <Link to={link}>{t('bankLine.statementLink', { sourceId: movement.sourceId })}</Link> : movement.sourceId}
              {' · '}{t('bankLine.expected', { amount: movement.expectedAmount })} · {movement.expectedDate}
              {' · '}{reconLabel(t, movementStatusEnum, movement.status)}</> : ''}</div></span>; } },
        { title: t('field.amount'), width: 120, align: 'right', render: (_, row) => <Money value={row.match.matchedAmount} /> },
        { title: t('bankLine.method'), width: 80, render: (_, row) => row.match.matchMethod === 'AUTO' ? t('enum.bankMatchStatus.PROPOSED') : t('match.manual') },
        { title: t('field.status'), width: 90, render: (_, row) => <ReconTag status={row.match.status} kind={bankMatchStatusEnum} /> },
        { title: t('field.operator'), width: 160, render: (_, row) => <ActorText value={row.match.operatorId} actorId={actorId} /> },
        { title: t('field.time'), width: 150, render: (_, row) => localTime(row.match.decisionTime ?? row.match.createTime) },
        ...(canOperate && proposed ? [{ title: '', width: 150, render: (_: unknown, row: BankLineMatch) => row.match.status !== 'PROPOSED' ? null : <>
          <Popconfirm title={t('bankLine.confirmMatch')} okText={t('common.confirm')} cancelText={t('common.cancel')} onConfirm={() => void decide(row.match, true)}>
            <Button type="link" disabled={command.saving}>{t('common.confirm')}</Button></Popconfirm>
          <Popconfirm title={t('bankLine.confirmReject')} okText={t('cashDetail.reject')} cancelText={t('common.cancel')} okButtonProps={{ danger: true }} onConfirm={() => void decide(row.match, false)}>
            <Button type="link" danger disabled={command.saving}>{t('cashDetail.reject')}</Button></Popconfirm>
        </> }] : []),
      ]} /></section>
  </DetailSection>;
}

/** 人工多选匹配：同一银行账户待到账的预期银行流水；实时带符号合计与流水剩余（AC-V5）。 */
function ManualMatch({ line, command, reload, onDone, onAuthorizationChanged }: {
  line: BankStatementLine; command: Command; reload: number; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('recon');
  const { message } = App.useApp();
  const movements = useFinanceList<ExpectedMovement>(`${reconPaths.expectedMovements}#${line.cashAccountId}:${reload}`,
    (_, signal) => requestAllPages(reconPaths.expectedMovements, signal, session, { cashAccountId: line.cashAccountId }, movementPage),
    onAuthorizationChanged, t('bankLine.movementsLoadFailed'));
  const open = useMemo(() => movements.items.filter(item => ['OPEN', 'PARTIALLY_CONFIRMED', 'EXCEPTION'].includes(item.status)), [movements.items]);
  const byId = useMemo(() => new Map(movements.items.map(item => [item.expectedMovementId, item])), [movements.items]);
  const [amounts, setAmounts] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<string[]>([]);
  const allocations: Allocation[] = selected.flatMap(id => { const movement = byId.get(id); return movement ? [{ expectedMovementId: id,
    direction: movement.direction, amount: amounts[id] ?? '', remaining: expectedRemaining(movement) }] : []; });
  const totals = manualMatchTotals(line, allocations);
  async function submit() {
    const result = await command.run(reconPaths.bankMatches, 'POST', { bankStatementLineId: line.bankStatementLineId,
      allocations: allocations.map(item => ({ expectedMovementId: item.expectedMovementId, amount: item.amount.trim() })) }, decodeBankMatchList, t('bankLine.manualMatchFailed'));
    if (!result) return;
    void message.success(t('bankLine.manualMatched', { count: result.length })); setSelected([]); setAmounts({}); onDone();
  }
  return <DetailSection title={t('bankLine.manualMatchTitle')}>
    <section className="table-panel"><Table<ExpectedMovement> rowKey="expectedMovementId" size="small" tableLayout="fixed" loading={movements.loading}
      pagination={open.length > 10 ? { pageSize: 10 } : false} dataSource={open} scroll={{ x: 900 }}
      rowSelection={{ selectedRowKeys: selected, onChange: keys => setSelected(keys.map(String)) }}
      locale={{ emptyText: <EmptyHint text={t('bankLine.noOpenMovements')} /> }} columns={[
        { title: t('link.expectedMovement'), width: 200, render: (_, row) => { const link = movementSourceLink(row);
          return <span className="mono">{row.expectedMovementId}<div className="cell-meta">{row.movementType} · {link ? <Link to={link}>{row.sourceId}</Link> : row.sourceId}</div></span>; } },
        { title: t('field.direction'), width: 90, render: (_, row) => <Tag color={row.direction === line.direction ? 'blue' : 'orange'}>{reconLabel(t, directionEnum, row.direction)}</Tag> },
        { title: t('field.expectedDate'), width: 110, dataIndex: 'expectedDate' },
        { title: t('field.remaining'), width: 120, align: 'right', render: (_, row) => <Money value={expectedRemaining(row)} /> },
        { title: t('bankLine.allocation'), width: 180, render: (_, row) => { const index = selected.indexOf(row.expectedMovementId);
          return index < 0 ? <span className="muted">{t('bankLine.selectFirst')}</span> : <Form.Item validateStatus={totals.errors[index] ? 'error' : undefined} help={totals.errors[index]}
            style={{ marginBottom: 0 }}><Input inputMode="decimal" placeholder={expectedRemaining(row)} value={amounts[row.expectedMovementId] ?? ''}
              onChange={event => setAmounts({ ...amounts, [row.expectedMovementId]: event.target.value })} /></Form.Item>; } },
      ]} /></section>
    <div className="finance-review-bar"><span className="recon-match-totals">
      <span>{t('bankLine.signedTotal')} <Money value={totals.signed} /></span>
      <span>{t('bankLine.lineRemaining')} <Money value={totals.remaining} /></span>
      {totals.exceeds && <Tag color="red">{t('bankLine.exceeds')}</Tag>}
      <span className="cell-meta">{t('bankLine.signHint')}</span>
    </span><span className="finance-spacer" />
      <Popconfirm title={t('bankLine.confirmAllocations', { count: allocations.length })} okText={t('common.confirm')} cancelText={t('common.cancel')} disabled={!totals.submittable}
        onConfirm={() => void submit()}><Button type="primary" disabled={!totals.submittable} loading={command.saving}>{t('bankLine.submitManualMatch')}</Button></Popconfirm>
    </div>
  </DetailSection>;
}

/** 流水上的未结异常：链到异常详情处置。 */
function OpenExceptions({ exceptions }: { exceptions: readonly ReconException[] }) {
  const t = useT('recon');
  if (!exceptions.length) return null;
  return <DetailSection title={t('bankLine.openExceptionsTitle', { count: exceptions.length })}>
    <section className="table-panel"><Table<ReconException> rowKey="exceptionId" size="small" tableLayout="fixed" pagination={false} dataSource={[...exceptions]} columns={[
      { title: t('bankLine.exception'), render: (_, row) => <Link to={reconRoutes.exception(row.exceptionId)}>{reconLabel(t, exceptionTypeEnum, row.exceptionType)}</Link> },
      { title: t('field.difference'), width: 130, align: 'right', render: (_, row) => <><Money value={row.differenceAmount} /> {row.currency}</> },
      { title: t('field.status'), width: 100, render: (_, row) => <ReconTag status={row.status} kind={exceptionStatusEnum} /> },
      { title: t('field.age'), width: 80, align: 'right', render: (_, row) => t('common.days', { days: row.ageDays }) },
    ]} /></section>
  </DetailSection>;
}
