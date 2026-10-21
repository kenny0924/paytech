import { useState } from 'react';
import { App, Button, Form, Input, Popconfirm, Select, Table, Tabs } from 'antd';
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime, replaceQuery } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { BankLineDetail } from '../reconciliation/detail/BankLineDetail';
import { BankStatementDetail } from '../reconciliation/detail/BankStatementDetail';
import { ImportDrawer } from '../reconciliation/ImportDrawer';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import {
  bankStatementStatusLabels, decodeWithId, directionLabels, lineMatchStatusLabels, movementStatusLabels, pageOf, reconActions, reconPaths, hasReconAction,
} from '../reconciliation/reconModel';
import type { BankStatement, BankStatementLine, CashAccount, ExpectedMovement, ReconException } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { compactFilters, isReconId, movementSourceLink, reconRoutes } from '../reconciliation/reconRoutes';
import { cashAccountLabel, useCashAccounts } from '../reconciliation/useReconData';
import { useReconCommand } from '../reconciliation/useReconCommand';
import { resetForm, useReconListQuery } from '../reconciliation/useReconListQuery';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
type BankTab = 'lines' | 'statements' | 'movements';

const statementPage = pageOf(decodeWithId<BankStatement>('bankStatementId'));
const linePage = pageOf(decodeWithId<BankStatementLine>('bankStatementLineId'));
const movementPage = pageOf(decodeWithId<ExpectedMovement>('expectedMovementId'));
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));

const lineKeys = ['matchStatus', 'direction', 'valueDateFrom', 'valueDateTo'] as const;
const lineDefaults = { matchStatus: 'UNMATCHED' };
const movementKeys = ['status'] as const;
/** 切换银行账户或页签时清掉各页签的筛选与页码。 */
const tabQueryReset = Object.fromEntries([...lineKeys, ...movementKeys, 'page', 'size'].map(key => [key, null])) as Record<string, null>;

/**
 * 银行对账（UC-R11–R13）：先选银行账户，再看银行流水、上传对账单、预期银行流水；
 * 点流水进入银行流水详情页（/bank/lines/:bankStatementLineId）处理匹配与开异常，点对账单进入对账单详情页（/bank/statements/:bankStatementId）
 * <li>银行账户、页签、页签内筛选与页码存于 URL；旧链接 ?line= 转到流水详情页。写操作按「对账操作」按钮资源显示。</li>
 */
export function ReconBank(props: Props) {
  const { bankStatementId, bankStatementLineId } = useParams();
  const [params] = useSearchParams();
  const legacy = params.get('line');
  if (bankStatementId !== undefined) return <BankStatementDetail key={bankStatementId} bankStatementId={bankStatementId} {...props} />;
  if (bankStatementLineId !== undefined) return <BankLineDetail key={bankStatementLineId} bankStatementLineId={bankStatementLineId} {...props} />;
  if (isReconId(legacy ?? undefined)) return <Navigate replace to={reconRoutes.bankLine(legacy!)} />;
  return <BankOverview {...props} />;
}

function BankOverview({ resources, onAuthorizationChanged }: Props) {
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
        value={account?.cashAccountId} onChange={value => go({ ...tabQueryReset, account: value })}
        options={selectable.map(item => ({ value: item.cashAccountId, label: cashAccountLabel(item) }))} /></Form.Item>
    </div></Form>
    {cash.error ? <LoadFailure message={cash.error} onRetry={cash.reload} />
      : !account ? <div className="finance-empty"><EmptyHint text={selectable.length ? '选择银行账户后查看流水、对账单与预期银行流水' : '还没有有效的银行账户，先在「银行账户」新建并复核'} /></div>
      : <Tabs activeKey={tab} onChange={next => go({ ...tabQueryReset, tab: next === 'lines' ? null : next })} destroyOnHidden items={[
        { key: 'lines', label: '银行流水', children: <LinesTab key={account.cashAccountId} account={account} /> },
        { key: 'statements', label: '对账单', children: <StatementsTab key={account.cashAccountId} account={account} canOperate={canOperate}
          onAuthorizationChanged={onAuthorizationChanged} /> },
        { key: 'movements', label: '预期银行流水', children: <MovementsTab key={account.cashAccountId} account={account} canOperate={canOperate}
          onAuthorizationChanged={onAuthorizationChanged} /> },
      ]} />}
    <FinanceNote>系统只给一对一匹配建议（金额相等、方向和币种一致、日期在窗口内）；一笔到账对应多笔预期时用人工多选匹配，带符号合计不能超过流水金额。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
  </div>;
}

function StatementsTab({ account, canOperate, onAuthorizationChanged }: { account: CashAccount; canOperate: boolean; onAuthorizationChanged: () => void }) {
  const openDetail = useOpenDetail();
  const [uploading, setUploading] = useState(false);
  const page = useReconListQuery<BankStatement>(reconPaths.bankStatements, statementPage, [], { fixed: { cashAccountId: account.cashAccountId } });
  return <>
    {canOperate && <div className="finance-search-inline"><Button type="primary" disabled={account.status !== 'ACTIVE'} onClick={() => setUploading(true)}>上传银行对账单</Button></div>}
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<BankStatement> rowKey="bankStatementId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.bankStatement(row.bankStatementId)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text="这个银行账户还没有对账单。上传标准 CSV 后，流水会出现在「银行流水」并得到匹配建议。" /> }} columns={[
          { title: '对账单编号', width: 180, render: (_, row) => <a className="mono">{row.statementRef}</a> },
          { title: '期间', width: 200, render: (_, row) => `${row.periodStart} — ${row.periodEnd}` },
          { title: '期初', width: 120, align: 'right', render: (_, row) => <Money value={row.openingBalance} /> },
          { title: '期末', width: 120, align: 'right', render: (_, row) => <Money value={row.closingBalance} /> },
          { title: '流水（跳过重复）', width: 130, align: 'right', render: (_, row) => `${row.importedLineCount}（${row.duplicateLineCount}）` },
          { title: '状态', width: 100, render: (_, row) => <>{<ReconTag status={row.status} labels={bankStatementStatusLabels} />}{row.rejectReason && <div className="cell-meta">{row.rejectReason}</div>}</> },
          { title: '上传时间', width: 150, render: (_, row) => localTime(row.createTime) },
        ]} />
    </section>}
    {uploading && <ImportDrawer kind="bank" cashAccount={account} onClose={() => setUploading(false)} onImported={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

function LinesTab({ account }: { account: CashAccount }) {
  const openDetail = useOpenDetail();
  const page = useReconListQuery<BankStatementLine>(reconPaths.bankLines, linePage, lineKeys, { defaults: lineDefaults, fixed: { cashAccountId: account.cashAccountId } });
  const [form] = Form.useForm<Record<string, string | undefined>>();
  return <>
    <Form form={form} name="recon-bank-line-filters" layout="vertical" initialValues={page.filters} onFinish={values => page.applyFilters(compactFilters(values))}>
      <div className="finance-search">
        <Form.Item name="matchStatus" label="匹配状态"><Select allowClear placeholder="全部" options={options(lineMatchStatusLabels)} /></Form.Item>
        <Form.Item name="direction" label="方向"><Select allowClear placeholder="全部" options={[{ value: 'CREDIT', label: '贷（收）' }, { value: 'DEBIT', label: '借（付）' }]} /></Form.Item>
        <Form.Item name="valueDateFrom" label="起息日起" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="valueDateTo" label="起息日止" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { resetForm(form, lineDefaults); page.applyFilters(lineDefaults); }}>重置</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<BankStatementLine> rowKey="bankStatementLineId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} onRow={row => ({ onClick: () => openDetail(reconRoutes.bankLine(row.bankStatementLineId)), className: 'clickable-row' })}
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
  </>;
}

function MovementsTab({ account, canOperate, onAuthorizationChanged }: { account: CashAccount; canOperate: boolean; onAuthorizationChanged: () => void }) {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const command = useReconCommand(onAuthorizationChanged);
  const page = useReconListQuery<ExpectedMovement>(reconPaths.expectedMovements, movementPage, movementKeys, { fixed: { cashAccountId: account.cashAccountId } });
  async function openException(row: ExpectedMovement) {
    const result = await command.run(reconPaths.exceptions, 'POST', { sourceType: 'EXPECTED_BANK_MOVEMENT', sourceId: row.expectedMovementId },
      decodeWithId<ReconException>('exceptionId'), '开立异常失败');
    if (!result) return;
    void message.success(`已开立异常，差异 ${result.differenceAmount}`);
    void navigate(reconRoutes.exception(result.exceptionId));
  }
  return <>
    <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); page.refresh(); }} />
    <div className="finance-search-inline"><Select allowClear placeholder="状态" style={{ width: 180 }} options={options(movementStatusLabels)}
      defaultValue={page.filters.status} onChange={value => page.applyFilters(value ? { status: value } : {})} /></div>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ExpectedMovement> rowKey="expectedMovementId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <EmptyHint text="没有预期银行流水；渠道账单对平后会生成一条预期到账。" /> }} columns={[
          { title: '来源', width: 200, render: (_, row) => { const link = movementSourceLink(row);
            return <span className="mono">{row.movementType}<div className="cell-meta">{link ? <Link to={link}>{row.sourceId}</Link> : row.sourceId}</div></span>; } },
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

