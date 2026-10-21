import { useMemo, useState } from 'react';
import { App, Button, Descriptions, Drawer, Form, Input, Popconfirm, Select, Table, Tabs, Tag } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { localTime, replaceQuery } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ActorText, ReviewActions } from '../finance/WorkflowParts';
import { useFinanceList } from '../finance/useFinanceData';
import { usePaging } from '../hooks/usePaging';
import { ImportDrawer } from '../reconciliation/ImportDrawer';
import {
  bankMatchStatusLabels, bankStatementStatusLabels, decodeBankMatchList, decodeWithId, directionLabels, expectedRemaining,
  hasReconAction, lineMatchStatusLabels, manualMatchTotals, movementStatusLabels, pageOf, reconActions, reconPaths,
} from '../reconciliation/reconModel';
import type { Allocation, BankMatch, BankStatement, BankStatementLine, CashAccount, ExpectedMovement, ReconException } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { cashAccountLabel, useCashAccounts } from '../reconciliation/useReconData';
import { useReconCommand } from '../reconciliation/useReconCommand';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
type BankTab = 'lines' | 'statements' | 'movements';

const statementPage = pageOf(decodeWithId<BankStatement>('bankStatementId'));
const linePage = pageOf(decodeWithId<BankStatementLine>('bankStatementLineId'));
const movementPage = pageOf(decodeWithId<ExpectedMovement>('expectedMovementId'));
const matchPage = pageOf(decodeWithId<BankMatch>('bankMatchId'));
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));

/**
 * 银行对账（UC-R11–R13）：先选银行账户，再看未匹配流水（建议确认 / 拒绝、人工多选匹配、开异常）、上传对账单、预期银行流水
 * <li>银行账户、页签与打开的流水存于 URL（?account=&tab=&line=）；写操作按「对账操作」按钮资源显示。</li>
 */
export function ReconBank({ resources, actorId, onAuthorizationChanged }: Props) {
  const [params, setParams] = useSearchParams();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const cash = useCashAccounts(onAuthorizationChanged);
  const accountId = params.get('account');
  const account = accountId ? cash.byId.get(accountId) : undefined;
  const tab: BankTab = params.get('tab') === 'statements' ? 'statements' : params.get('tab') === 'movements' ? 'movements' : 'lines';
  const go = (updates: Record<string, string | null>) => setParams(new URLSearchParams(replaceQuery(params.toString(), updates)));
  const selectable = cash.items.filter(item => item.status === 'ACTIVE' || item.status === 'SUSPENDED');
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>银行对账</h1></div>
    <Form layout="vertical" name="recon-bank-account"><div className="finance-trial-head">
      <Form.Item label="银行账户"><Select style={{ minWidth: 420 }} showSearch={{ optionFilterProp: 'label' }} placeholder="选择银行账户" loading={cash.loading}
        value={account?.cashAccountId} onChange={value => go({ account: value, line: null })}
        options={selectable.map(item => ({ value: item.cashAccountId, label: cashAccountLabel(item) }))} /></Form.Item>
    </div></Form>
    {cash.error ? <LoadFailure message={cash.error} onRetry={cash.reload} />
      : !account ? <div className="finance-empty"><EmptyHint text={selectable.length ? '选择银行账户后查看流水、对账单与预期银行流水' : '还没有有效的银行账户，先在「银行账户」新建并复核'} /></div>
      : <Tabs activeKey={tab} onChange={next => go({ tab: next, line: null })} destroyOnHidden items={[
        { key: 'lines', label: '银行流水', children: <LinesTab key={account.cashAccountId} account={account} canOperate={canOperate} actorId={actorId}
          openLineId={params.get('line')} onOpen={id => go({ line: id })} onAuthorizationChanged={onAuthorizationChanged} /> },
        { key: 'statements', label: '对账单', children: <StatementsTab key={account.cashAccountId} account={account} canOperate={canOperate} actorId={actorId}
          onAuthorizationChanged={onAuthorizationChanged} /> },
        { key: 'movements', label: '预期银行流水', children: <MovementsTab key={account.cashAccountId} account={account} canOperate={canOperate}
          onAuthorizationChanged={onAuthorizationChanged} /> },
      ]} />}
    <FinanceNote>系统只给一对一匹配建议（金额相等、方向和币种一致、日期在窗口内）；一笔到账对应多笔预期时用人工多选匹配，带符号合计不能超过流水金额。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
  </div>;
}

function StatementsTab({ account, canOperate, actorId, onAuthorizationChanged }: { account: CashAccount; canOperate: boolean; actorId: string; onAuthorizationChanged: () => void }) {
  const [uploading, setUploading] = useState(false);
  const page = usePaging<BankStatement>(reconPaths.bankStatements, statementPage, { cashAccountId: account.cashAccountId });
  return <>
    {canOperate && <div className="finance-search-inline"><Button type="primary" disabled={account.status !== 'ACTIVE'} onClick={() => setUploading(true)}>上传银行对账单</Button></div>}
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<BankStatement> rowKey="bankStatementId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <EmptyHint text="这个银行账户还没有对账单。上传标准 CSV 后，流水会出现在「银行流水」并得到匹配建议。" /> }} columns={[
          { title: '对账单编号', width: 180, render: (_, row) => <span className="mono">{row.statementRef}</span> },
          { title: '期间', width: 200, render: (_, row) => `${row.periodStart} — ${row.periodEnd}` },
          { title: '期初', width: 120, align: 'right', render: (_, row) => <Money value={row.openingBalance} /> },
          { title: '期末', width: 120, align: 'right', render: (_, row) => <Money value={row.closingBalance} /> },
          { title: '流水（跳过重复）', width: 130, align: 'right', render: (_, row) => `${row.importedLineCount}（${row.duplicateLineCount}）` },
          { title: '状态', width: 100, render: (_, row) => <>{<ReconTag status={row.status} labels={bankStatementStatusLabels} />}{row.rejectReason && <div className="cell-meta">{row.rejectReason}</div>}</> },
          { title: '上传人', width: 160, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
          { title: '上传时间', width: 150, render: (_, row) => localTime(row.createTime) },
        ]} />
    </section>}
    {uploading && <ImportDrawer kind="bank" cashAccount={account} onClose={() => setUploading(false)} onImported={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

function LinesTab({ account, canOperate, actorId, openLineId, onOpen, onAuthorizationChanged }: {
  account: CashAccount; canOperate: boolean; actorId: string; openLineId: string | null; onOpen: (id: string | null) => void; onAuthorizationChanged: () => void;
}) {
  const base = { cashAccountId: account.cashAccountId };
  const page = usePaging<BankStatementLine>(reconPaths.bankLines, linePage, { ...base, matchStatus: 'UNMATCHED' });
  const [form] = Form.useForm<Record<string, string | undefined>>();
  const line = page.data.items.find(item => item.bankStatementLineId === openLineId);
  return <>
    <Form form={form} name="recon-bank-line-filters" layout="vertical" initialValues={{ matchStatus: 'UNMATCHED' }} onFinish={values => page.applyFilters({ ...base,
      ...Object.fromEntries(Object.entries(values).filter(([, value]) => value?.trim()).map(([key, value]) => [key, value!.trim()])) })}>
      <div className="finance-search">
        <Form.Item name="matchStatus" label="匹配状态"><Select allowClear placeholder="全部" options={options(lineMatchStatusLabels)} /></Form.Item>
        <Form.Item name="direction" label="方向"><Select allowClear placeholder="全部" options={[{ value: 'CREDIT', label: '贷（收）' }, { value: 'DEBIT', label: '借（付）' }]} /></Form.Item>
        <Form.Item name="valueDateFrom" label="起息日起" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="valueDateTo" label="起息日止" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({ ...base, matchStatus: 'UNMATCHED' }); }}>重置</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<BankStatementLine> rowKey="bankStatementLineId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} onRow={row => ({ onClick: () => onOpen(row.bankStatementLineId), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text="没有符合条件的银行流水；未匹配的流水全部处理完后这里为空。" /> }} columns={[
          { title: '银行交易号', width: 180, render: (_, row) => <a className="mono">{row.bankTransactionRef}</a> },
          { title: '起息日', width: 110, dataIndex: 'valueDate' },
          { title: '方向', width: 90, render: (_, row) => directionLabels[row.direction] ?? row.direction },
          { title: '金额', width: 120, align: 'right', render: (_, row) => <><Money value={row.amount} /> {row.currency}</> },
          { title: '已匹配', width: 110, align: 'right', render: (_, row) => <Money value={row.matchedAmount} /> },
          { title: '对方 / 附言', render: (_, row) => <>{row.counterpartyName ?? '—'}<div className="cell-meta">{row.referenceText ?? ''}</div></> },
          { title: '状态', width: 100, render: (_, row) => <ReconTag status={row.matchStatus} labels={lineMatchStatusLabels} /> },
        ]} />
    </section>}
    {line && <LineDrawer account={account} line={line} canOperate={canOperate} actorId={actorId} onClose={() => onOpen(null)}
      onChanged={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

/** 银行流水：匹配建议（确认 / 拒绝）、已确认匹配、人工多选匹配（实时带符号合计与剩余，AC-V5）、开异常。 */
function LineDrawer({ account, line, canOperate, actorId, onClose, onChanged, onAuthorizationChanged }: {
  account: CashAccount; line: BankStatementLine; canOperate: boolean; actorId: string; onClose: () => void; onChanged: () => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const command = useReconCommand(onAuthorizationChanged);
  const [reload, setReload] = useState(0);
  const matches = useFinanceList<BankMatch>(`${reconPaths.bankMatches}#${line.bankStatementLineId}:${reload}`,
    (_, signal) => requestAllPages(reconPaths.bankMatches, signal, session, { bankStatementLineId: line.bankStatementLineId }, matchPage),
    onAuthorizationChanged, '银行匹配加载失败');
  const movements = useFinanceList<ExpectedMovement>(`${reconPaths.expectedMovements}#${account.cashAccountId}:${reload}`,
    (_, signal) => requestAllPages(reconPaths.expectedMovements, signal, session, { cashAccountId: account.cashAccountId }, movementPage),
    onAuthorizationChanged, '预期银行流水加载失败');
  const open = useMemo(() => movements.items.filter(item => ['OPEN', 'PARTIALLY_CONFIRMED', 'EXCEPTION'].includes(item.status)), [movements.items]);
  const byId = useMemo(() => new Map(movements.items.map(item => [item.expectedMovementId, item])), [movements.items]);
  const [amounts, setAmounts] = useState<Record<string, string>>({});
  const [selected, setSelected] = useState<string[]>([]);
  const allocations: Allocation[] = selected.flatMap(id => { const movement = byId.get(id); return movement ? [{ expectedMovementId: id,
    direction: movement.direction, amount: amounts[id] ?? '', remaining: expectedRemaining(movement) }] : []; });
  const totals = manualMatchTotals(line, allocations);
  const done = () => { setReload(value => value + 1); onChanged(); };
  async function decide(match: BankMatch, confirm: boolean) {
    const result = await command.run(confirm ? reconPaths.bankMatchConfirmation(match.bankMatchId) : reconPaths.bankMatchRejection(match.bankMatchId), 'POST',
      undefined, decodeWithId<BankMatch>('bankMatchId', match.bankMatchId), confirm ? '确认匹配失败' : '拒绝建议失败');
    if (!result) return;
    void message.success(confirm ? '已确认匹配' : '已拒绝建议'); done();
  }
  async function submitManual() {
    const result = await command.run(reconPaths.bankMatches, 'POST', { bankStatementLineId: line.bankStatementLineId,
      allocations: allocations.map(item => ({ expectedMovementId: item.expectedMovementId, amount: item.amount.trim() })) }, decodeBankMatchList, '人工匹配失败');
    if (!result) return;
    void message.success(`已确认 ${result.length} 条匹配`); setSelected([]); setAmounts({}); done();
  }
  async function openException() {
    const result = await command.run(reconPaths.exceptions, 'POST', { sourceType: 'BANK_STATEMENT_LINE', sourceId: line.bankStatementLineId },
      decodeWithId<ReconException>('exceptionId'), '开立异常失败');
    if (!result) return;
    navigate(`/finance/reconciliation/exceptions?exception=${result.exceptionId}`);
  }
  const proposed = matches.items.filter(item => item.status === 'PROPOSED');
  const matchable = canOperate && ['UNMATCHED', 'PARTIALLY_MATCHED'].includes(line.matchStatus);
  return <Drawer open size="min(1180px, 100vw)" rootClassName="finance-drawer finance-page" destroyOnHidden onClose={onClose}
    title={`银行流水 · ${line.bankTransactionRef}`}>
    <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); done(); }} />
    <Descriptions size="small" column={3} items={[
      { key: 'date', label: '起息日', children: line.valueDate }, { key: 'direction', label: '方向', children: directionLabels[line.direction] ?? line.direction },
      { key: 'amount', label: '金额', children: <><Money value={line.amount} /> {line.currency}</> },
      { key: 'matched', label: '已匹配', children: <Money value={line.matchedAmount} /> },
      { key: 'status', label: '状态', children: <ReconTag status={line.matchStatus} labels={lineMatchStatusLabels} /> },
      { key: 'counterparty', label: '对方', children: line.counterpartyName ?? '—' },
      { key: 'reference', label: '附言', span: 3, children: line.referenceText ?? '—' },
    ]} />
    <h3 className="finance-subtitle">匹配建议与已确认匹配</h3>
    <section className="table-panel"><Table<BankMatch> rowKey="bankMatchId" size="small" tableLayout="fixed" loading={matches.loading} pagination={false}
      dataSource={matches.items} locale={{ emptyText: <EmptyHint text="系统没有给出建议：没有金额相等、方向一致、日期在窗口内的预期银行流水。可用下方人工多选匹配。" /> }}
      columns={[
        { title: '预期银行流水', render: (_, row) => { const movement = byId.get(row.expectedMovementId);
          return <span className="mono">{row.expectedMovementId}<div className="cell-meta">{movement ? `${movement.movementType} · 预期 ${movement.expectedAmount} · ${movement.expectedDate}` : ''}</div></span>; } },
        { title: '金额', width: 120, align: 'right', render: (_, row) => <Money value={row.matchedAmount} /> },
        { title: '方式', width: 80, render: (_, row) => row.matchMethod === 'AUTO' ? '建议' : '人工' },
        { title: '状态', width: 90, render: (_, row) => <ReconTag status={row.status} labels={bankMatchStatusLabels} /> },
        { title: '操作人', width: 160, render: (_, row) => <ActorText value={row.operatorId} actorId={actorId} /> },
        ...(canOperate && proposed.length ? [{ title: '', width: 150, render: (_: unknown, row: BankMatch) => row.status !== 'PROPOSED' ? null : <>
          <Popconfirm title="确认这条匹配？确认后按匹配金额记到账。" okText="确认" cancelText="取消" onConfirm={() => void decide(row, true)}><Button type="link">确认</Button></Popconfirm>
          <Popconfirm title="拒绝这条建议？" okText="拒绝" cancelText="取消" okButtonProps={{ danger: true }} onConfirm={() => void decide(row, false)}><Button type="link" danger>拒绝</Button></Popconfirm>
        </> }] : []),
      ]} /></section>
    {matchable && <>
      <h3 className="finance-subtitle">人工多选匹配</h3>
      <section className="table-panel"><Table<ExpectedMovement> rowKey="expectedMovementId" size="small" tableLayout="fixed" loading={movements.loading}
        pagination={open.length > 10 ? { pageSize: 10 } : false} dataSource={open} scroll={{ x: 900 }}
        rowSelection={{ selectedRowKeys: selected, onChange: keys => setSelected(keys.map(String)) }}
        locale={{ emptyText: <EmptyHint text="这个银行账户没有待到账的预期银行流水" /> }} columns={[
          { title: '预期银行流水', width: 200, render: (_, row) => <span className="mono">{row.expectedMovementId}<div className="cell-meta">{row.movementType} · {row.sourceId}</div></span> },
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
          onConfirm={() => void submitManual()}><Button type="primary" disabled={!totals.submittable} loading={command.saving}>确认人工匹配</Button></Popconfirm>
      </div>
    </>}
    {canOperate && line.matchStatus !== 'MATCHED' && line.matchStatus !== 'EXCEPTION' && <ReviewActions saving={command.saving} actions={[{ key: 'exception', label: '开异常',
      confirm: '为这条银行流水开一个不阻塞的「银行流水未匹配」异常，差异金额由系统算出。确认？', onConfirm: () => void openException() }]} />}
  </Drawer>;
}

function MovementsTab({ account, canOperate, onAuthorizationChanged }: { account: CashAccount; canOperate: boolean; onAuthorizationChanged: () => void }) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const command = useReconCommand(onAuthorizationChanged);
  const base = { cashAccountId: account.cashAccountId };
  const page = usePaging<ExpectedMovement>(reconPaths.expectedMovements, movementPage, base);
  async function openException(row: ExpectedMovement) {
    const result = await command.run(reconPaths.exceptions, 'POST', { sourceType: 'EXPECTED_BANK_MOVEMENT', sourceId: row.expectedMovementId },
      decodeWithId<ReconException>('exceptionId'), '开立异常失败');
    if (!result) return;
    void message.success(`已开立异常，差异 ${result.differenceAmount}`);
    navigate(`/finance/reconciliation/exceptions?exception=${result.exceptionId}`);
  }
  return <>
    <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); page.refresh(); }} />
    <div className="finance-search-inline"><Select allowClear placeholder="状态" style={{ width: 180 }} options={options(movementStatusLabels)}
      onChange={value => page.applyFilters(value ? { ...base, status: value } : base)} /></div>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ExpectedMovement> rowKey="expectedMovementId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <EmptyHint text="没有预期银行流水；渠道账单对平后会生成一条预期到账。" /> }} columns={[
          { title: '来源', width: 200, render: (_, row) => <span className="mono">{row.movementType}<div className="cell-meta">{row.sourceId}</div></span> },
          { title: '方向', width: 90, render: (_, row) => directionLabels[row.direction] ?? row.direction },
          { title: '预期', width: 120, align: 'right', render: (_, row) => <><Money value={row.expectedAmount} /> {row.currency}</> },
          { title: '已确认', width: 110, align: 'right', render: (_, row) => <Money value={row.confirmedAmount} /> },
          { title: '已归类', width: 110, align: 'right', render: (_, row) => <Money value={row.classifiedAmount} /> },
          { title: '预期日', width: 110, dataIndex: 'expectedDate' },
          { title: '状态', width: 100, render: (_, row) => <ReconTag status={row.status} labels={movementStatusLabels} /> },
          ...(canOperate ? [{ title: '', width: 100, render: (_: unknown, row: ExpectedMovement) => ['CONFIRMED', 'CANCELLED', 'EXCEPTION'].includes(row.status) ? null
            : <Popconfirm title="为这条预期银行流水开一个不阻塞的「到账金额不符」异常，差异金额由系统算出。确认？" okText="开立" cancelText="取消"
              onConfirm={() => void openException(row)}><Button type="link" disabled={command.saving}>开异常</Button></Popconfirm> }] : []),
        ]} />
    </section>}
  </>;
}

