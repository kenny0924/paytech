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
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import {
  bankMatchStatusLabels, decodeBankLineDetail, decodeBankMatchList, decodeWithId, directionLabels, exceptionStatusLabels, exceptionTypeLabels,
  expectedRemaining, hasReconAction, lineMatchStatusLabels, manualMatchTotals, movementStatusLabels, pageOf, reconActions, reconPaths,
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
  const navigate = useNavigate();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const decode = useMemo(() => decodeBankLineDetail(bankStatementLineId), [bankStatementLineId]);
  const detail = useReconDetail<LineDetail>(isReconId(bankStatementLineId) ? reconPaths.bankLine(bankStatementLineId) : null, decode,
    onAuthorizationChanged, '银行流水加载失败');
  const command = useReconCommand(onAuthorizationChanged);
  const [reload, setReload] = useState(0);
  const done = () => { detail.reload(); setReload(value => value + 1); };
  const line = detail.item?.line;
  async function openException() {
    if (!line) return;
    const result = await command.run(reconPaths.exceptions, 'POST', { sourceType: 'BANK_STATEMENT_LINE', sourceId: line.bankStatementLineId },
      decodeWithId<ReconException>('exceptionId'), '开立异常失败');
    if (result) void navigate(reconRoutes.exception(result.exceptionId));
  }
  const exceptionAction = line && canOperate && line.matchStatus !== 'MATCHED' && line.matchStatus !== 'EXCEPTION' && !detail.item?.openExceptions.length
    && <Popconfirm title="为这条银行流水开一个不阻塞的「银行流水未匹配」异常，差异金额由系统算出。确认？" okText="开立" cancelText="取消"
      onConfirm={() => void openException()}><Button loading={command.saving}>开异常</Button></Popconfirm>;
  return <ReconDetailPage list={{ label: '银行对账', route: reconRoutes.bank }} objectLabel="银行流水" state={detail} actions={exceptionAction}
    title={line ? <>银行流水 · <span className="mono">{line.bankTransactionRef}</span></> : null}>
    {current => <>
      <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); done(); }} />
      <Descriptions size="small" column={3} items={[
        { key: 'date', label: '起息日', children: current.line.valueDate },
        { key: 'direction', label: '方向', children: directionLabels[current.line.direction] ?? current.line.direction },
        { key: 'amount', label: '金额', children: <><Money value={current.line.amount} /> {current.line.currency}</> },
        { key: 'matched', label: '已匹配', children: <Money value={current.line.matchedAmount} /> },
        { key: 'status', label: '状态', children: <ReconTag status={current.line.matchStatus} labels={lineMatchStatusLabels} /> },
        { key: 'counterparty', label: '对方', children: current.line.counterpartyName ?? '—' },
        { key: 'statement', label: '银行对账单', children: <Link className="mono" to={reconRoutes.bankStatement(current.line.bankStatementId)}>
          {current.statement ? `${current.statement.statementRef} · 第 ${current.line.lineNo} 行` : current.line.bankStatementId}</Link> },
        { key: 'account', label: '银行账户', children: <Link className="mono" to={reconRoutes.cashAccount(current.line.cashAccountId)}>{current.line.cashAccountId}</Link> },
        { key: 'reference', label: '附言', children: current.line.referenceText ?? '—' },
      ]} />
      <MatchList matches={current.matches} canOperate={canOperate} actorId={actorId} command={command} onDone={done} />
      {canOperate && ['UNMATCHED', 'PARTIALLY_MATCHED'].includes(current.line.matchStatus)
        && <ManualMatch key={`${current.line.version}:${reload}`} line={current.line} command={command} reload={reload} onDone={done}
          onAuthorizationChanged={onAuthorizationChanged} />}
      <OpenExceptions exceptions={current.openExceptions} />
      <FinanceNote>系统只给一对一匹配建议（金额相等、方向和币种一致、日期在窗口内）；一笔到账对应多笔预期时用人工多选匹配，带符号合计不能超过流水金额。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
    </>}
  </ReconDetailPage>;
}

/** 匹配建议与已确认 / 已拒绝匹配：建议可确认或拒绝；预期银行流水来源为渠道结算时链到渠道账单。 */
function MatchList({ matches, canOperate, actorId, command, onDone }: {
  matches: readonly BankLineMatch[]; canOperate: boolean; actorId: string; command: Command; onDone: () => void;
}) {
  const { message } = App.useApp();
  async function decide(match: BankMatch, confirm: boolean) {
    const result = await command.run(confirm ? reconPaths.bankMatchConfirmation(match.bankMatchId) : reconPaths.bankMatchRejection(match.bankMatchId), 'POST',
      undefined, decodeWithId<BankMatch>('bankMatchId', match.bankMatchId), confirm ? '确认匹配失败' : '拒绝建议失败');
    if (!result) return;
    void message.success(confirm ? '已确认匹配' : '已拒绝建议'); onDone();
  }
  const proposed = matches.some(item => item.match.status === 'PROPOSED');
  return <DetailSection title="匹配建议与已确认匹配">
    <section className="table-panel"><Table<BankLineMatch> rowKey={row => row.match.bankMatchId} size="small" tableLayout="fixed" pagination={false}
      dataSource={[...matches]} scroll={{ x: 980 }}
      locale={{ emptyText: <EmptyHint text="系统没有给出建议：没有金额相等、方向一致、日期在窗口内的预期银行流水。可用下方人工多选匹配。" /> }}
      columns={[
        { title: '预期银行流水', render: (_, row) => { const movement = row.expectedMovement; const link = movement ? movementSourceLink(movement) : null;
          return <span className="mono">{row.match.expectedMovementId}<div className="cell-meta">{movement
            ? <>{movement.movementType} · {link ? <Link to={link}>账单 {movement.sourceId}</Link> : movement.sourceId} · 预期 {movement.expectedAmount} · {movement.expectedDate}
              {' · '}{movementStatusLabels[movement.status] ?? movement.status}</> : ''}</div></span>; } },
        { title: '金额', width: 120, align: 'right', render: (_, row) => <Money value={row.match.matchedAmount} /> },
        { title: '方式', width: 80, render: (_, row) => row.match.matchMethod === 'AUTO' ? '建议' : '人工' },
        { title: '状态', width: 90, render: (_, row) => <ReconTag status={row.match.status} labels={bankMatchStatusLabels} /> },
        { title: '操作人', width: 160, render: (_, row) => <ActorText value={row.match.operatorId} actorId={actorId} /> },
        { title: '时间', width: 150, render: (_, row) => localTime(row.match.decisionTime ?? row.match.createTime) },
        ...(canOperate && proposed ? [{ title: '', width: 150, render: (_: unknown, row: BankLineMatch) => row.match.status !== 'PROPOSED' ? null : <>
          <Popconfirm title="确认这条匹配？确认后按匹配金额记到账。" okText="确认" cancelText="取消" onConfirm={() => void decide(row.match, true)}>
            <Button type="link" disabled={command.saving}>确认</Button></Popconfirm>
          <Popconfirm title="拒绝这条建议？" okText="拒绝" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => void decide(row.match, false)}>
            <Button type="link" danger disabled={command.saving}>拒绝</Button></Popconfirm>
        </> }] : []),
      ]} /></section>
  </DetailSection>;
}

/** 人工多选匹配：同一银行账户待到账的预期银行流水；实时带符号合计与流水剩余（AC-V5）。 */
function ManualMatch({ line, command, reload, onDone, onAuthorizationChanged }: {
  line: BankStatementLine; command: Command; reload: number; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const movements = useFinanceList<ExpectedMovement>(`${reconPaths.expectedMovements}#${line.cashAccountId}:${reload}`,
    (_, signal) => requestAllPages(reconPaths.expectedMovements, signal, session, { cashAccountId: line.cashAccountId }, movementPage),
    onAuthorizationChanged, '预期银行流水加载失败');
  const open = useMemo(() => movements.items.filter(item => ['OPEN', 'PARTIALLY_CONFIRMED', 'EXCEPTION'].includes(item.status)), [movements.items]);
  const byId = useMemo(() => new Map(movements.items.map(item => [item.expectedMovementId, item])), [movements.items]);
  const [amounts, setAmounts] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<string[]>([]);
  const allocations: Allocation[] = selected.flatMap(id => { const movement = byId.get(id); return movement ? [{ expectedMovementId: id,
    direction: movement.direction, amount: amounts[id] ?? '', remaining: expectedRemaining(movement) }] : []; });
  const totals = manualMatchTotals(line, allocations);
  async function submit() {
    const result = await command.run(reconPaths.bankMatches, 'POST', { bankStatementLineId: line.bankStatementLineId,
      allocations: allocations.map(item => ({ expectedMovementId: item.expectedMovementId, amount: item.amount.trim() })) }, decodeBankMatchList, '人工匹配失败');
    if (!result) return;
    void message.success(`已确认 ${result.length} 条匹配`); setSelected([]); setAmounts({}); onDone();
  }
  return <DetailSection title="人工多选匹配">
    <section className="table-panel"><Table<ExpectedMovement> rowKey="expectedMovementId" size="small" tableLayout="fixed" loading={movements.loading}
      pagination={open.length > 10 ? { pageSize: 10 } : false} dataSource={open} scroll={{ x: 900 }}
      rowSelection={{ selectedRowKeys: selected, onChange: keys => setSelected(keys.map(String)) }}
      locale={{ emptyText: <EmptyHint text="这个银行账户没有待到账的预期银行流水" /> }} columns={[
        { title: '预期银行流水', width: 200, render: (_, row) => { const link = movementSourceLink(row);
          return <span className="mono">{row.expectedMovementId}<div className="cell-meta">{row.movementType} · {link ? <Link to={link}>{row.sourceId}</Link> : row.sourceId}</div></span>; } },
        { title: '方向', width: 90, render: (_, row) => <Tag color={row.direction === line.direction ? 'blue' : 'orange'}>{directionLabels[row.direction] ?? row.direction}</Tag> },
        { title: '预期日', width: 110, dataIndex: 'expectedDate' },
        { title: '剩余', width: 120, align: 'right', render: (_, row) => <Money value={expectedRemaining(row)} /> },
        { title: '分配金额', width: 180, render: (_, row) => { const index = selected.indexOf(row.expectedMovementId);
          return index < 0 ? <span className="muted">先勾选</span> : <Form.Item validateStatus={totals.errors[index] ? 'error' : undefined} help={totals.errors[index]}
            style={{ marginBottom: 0 }}><Input inputMode="decimal" placeholder={expectedRemaining(row)} value={amounts[row.expectedMovementId] ?? ''}
              onChange={event => setAmounts({ ...amounts, [row.expectedMovementId]: event.target.value })} /></Form.Item>; } },
      ]} /></section>
    <div className="finance-review-bar"><span className="recon-match-totals">
      <span>带符号合计 <Money value={totals.signed} /></span>
      <span>流水剩余 <Money value={totals.remaining} /></span>
      {totals.exceeds && <Tag color="red">超出流水金额</Tag>}
      <span className="cell-meta">与流水同向的预期为正、反向为负</span>
    </span><span className="finance-spacer" />
      <Popconfirm title={`确认 ${allocations.length} 条分配？确认后直接生效。`} okText="确认" cancelText="取消" disabled={!totals.submittable}
        onConfirm={() => void submit()}><Button type="primary" disabled={!totals.submittable} loading={command.saving}>确认人工匹配</Button></Popconfirm>
    </div>
  </DetailSection>;
}

/** 流水上的未结异常：链到异常详情处置。 */
function OpenExceptions({ exceptions }: { exceptions: readonly ReconException[] }) {
  if (!exceptions.length) return null;
  return <DetailSection title={`未结异常（${exceptions.length}）`}>
    <section className="table-panel"><Table<ReconException> rowKey="exceptionId" size="small" tableLayout="fixed" pagination={false} dataSource={[...exceptions]} columns={[
      { title: '异常', render: (_, row) => <Link to={reconRoutes.exception(row.exceptionId)}>{exceptionTypeLabels[row.exceptionType] ?? row.exceptionType}</Link> },
      { title: '差异', width: 130, align: 'right', render: (_, row) => <><Money value={row.differenceAmount} /> {row.currency}</> },
      { title: '状态', width: 100, render: (_, row) => <ReconTag status={row.status} labels={exceptionStatusLabels} /> },
      { title: '账龄', width: 80, align: 'right', render: (_, row) => `${row.ageDays} 天` },
    ]} /></section>
  </DetailSection>;
}
