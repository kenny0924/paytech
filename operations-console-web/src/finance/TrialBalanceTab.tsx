import { useMemo, useState } from 'react';
import type { Key } from 'react';
import { Alert, Button, Descriptions, Drawer, Empty, Form, Segmented, Select, Spin, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  balanceTotals, buildAccountTree, decodeAccountBalanceList, decodeTrialBalanceDetail, decodeTrialBalanceRun, expandableKeys, financeErrorTarget,
  financePaths, leafDrilldownSearch, localTime, pageOf, readTrialQuery, trialSearch,
} from './finance';
import type { AccountBalanceLine, LedgerBook, TrialBalanceDetail, TrialBalanceFinding, TrialBalanceRun, TreeNode, TrialQuery } from './finance';
import { ledgerWorkflowErrorTargets, workflowPaths } from './financeWorkflow';
import { Amount, ConflictBanner, FinanceNote, LoadFailure, StatusTag } from './FinanceParts';
import { bookTitle } from './SetupBooksTab';
import { useFinanceCommand } from './useFinanceCommand';
import { useBookPeriods, useCurrencies, useFinanceList, useLedgerBooks } from './useFinanceData';

/**
 * 账本查询「试算平衡」页签（technique 2.5）：账簿 + 期间选择，视图切换「科目余额（实时）」与「试算记录」
 * <li>科目余额（L6）与试算详情都按科目树展示末级与各级汇总行，一级科目合计校验借 = 贷；末级行点击进入账簿下钻的该科目账户。</li>
 * <li>有 OPERATIONS_FINANCE_LEDGER_MANAGE 时显示「发起试算」（无请求体，FUTURE 期间不可发起）；成功后切到试算记录并打开新记录。</li>
 */
export function TrialBalanceTab({ canRun, onAuthorizationChanged }: { canRun: boolean; onAuthorizationChanged: () => void }) {
  const t = useT('finance');
  const location = useLocation(); const navigate = useNavigate();
  const query = readTrialQuery(new URLSearchParams(location.search));
  const books = useLedgerBooks(onAuthorizationChanged);
  const book = books.items.find(item => item.ledgerBookId === query.book);
  const periods = useBookPeriods(book, onAuthorizationChanged);
  const currencies = useCurrencies(onAuthorizationChanged);
  const minorUnit = book ? currencies.minorUnits.get(book.currency) : undefined;
  const go = (next: Partial<TrialQuery>) => void navigate({ search: trialSearch(location.search, next) });
  const openLeaf = (line: AccountBalanceLine) => book && void navigate({ search: leafDrilldownSearch(book.ledgerBookId, line.accountCode) }, { state: { book } });
  const period = periods.items.find(item => item.accountingPeriodId === query.period);
  const ready = book && query.period && period;
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], ledgerWorkflowErrorTargets);
  const [runsToken, setRunsToken] = useState(0);
  async function runTrial() {
    if (!book || !period) return;
    const run = await command.run(workflowPaths.runTrialBalance(book.ledgerBookId, period.accountingPeriodId), undefined,
      decodeTrialBalanceRun(book.ledgerBookId, period.accountingPeriodId), t('trial.runFailed'));
    if (!run) return;
    setRunsToken(value => value + 1);
    void navigate({ search: trialSearch(trialSearch(location.search, { view: 'runs' }), { run: run.runNo }) });
  }
  return <>
    <Form layout="vertical" name="finance-trial-scope"><div className="finance-trial-head">
      <Form.Item label={t('field.book')}><Select showSearch={{ optionFilterProp: 'label' }} placeholder={t('trial.selectBook')} loading={books.loading} value={query.book ?? undefined}
        onChange={value => go({ book: value })} options={books.items.map(item => ({ value: item.ledgerBookId, label: `${bookTitle(item)} · ${item.chartCode}` }))} /></Form.Item>
      <Form.Item label={t('modals.period')} extra={book && !periods.loading && !periods.error && !periods.items.length ? t('trial.noPeriods') : undefined}>
        <Select placeholder={book ? t('trial.selectPeriod') : t('trial.selectBookFirst')} disabled={!book} loading={periods.loading} value={query.period ?? undefined}
          onChange={value => go({ period: value })} options={periods.items.map(item => ({ value: item.accountingPeriodId,
            label: t('trial.periodOption', { fiscalYear: String(item.fiscalYear), periodNo: String(item.periodNo), startDate: item.startDate, endDate: item.endDate,
              status: item.status }) }))} /></Form.Item>
      <Form.Item label={t('trial.view')}><Segmented value={query.view} onChange={value => go({ view: value as TrialQuery['view'] })}
        options={[{ value: 'balances', label: t('trial.viewBalances') }, { value: 'runs', label: t('trial.viewRuns') }]} /></Form.Item>
      {canRun && ready && <Form.Item><Button type="primary" loading={command.saving} disabled={period.status === 'FUTURE'}
        title={period.status === 'FUTURE' ? t('trial.futureNotAllowed') : undefined} onClick={() => void runTrial()}>{t('trial.run')}</Button></Form.Item>}
    </div></Form>
    {command.conflict && <ConflictBanner onReload={() => setRunsToken(value => value + 1)} />}
    {command.formError && <Alert type="error" showIcon className="finance-banner" title={command.formError} />}
    {books.error ? <LoadFailure message={books.error} onRetry={books.reload} />
      : periods.error ? <LoadFailure message={periods.error} onRetry={periods.reload} />
      : !ready || !book || !query.period ? <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('trial.selectScope')} /></div>
      : query.view === 'balances'
        ? <LiveBalances key={`${book.ledgerBookId}:${query.period}`} book={book} periodId={query.period} minorUnit={minorUnit} onOpenLeaf={openLeaf}
          onAuthorizationChanged={onAuthorizationChanged} />
        : <TrialRuns key={`${book.ledgerBookId}:${query.period}:${runsToken}`} book={book} periodId={query.period} minorUnit={minorUnit} />}
    {ready && book && query.period && query.run && <TrialRunDrawer book={book} periodId={query.period} runNo={query.run} minorUnit={minorUnit}
      onOpenLeaf={openLeaf} onClose={() => go({ run: null })} />}
    <FinanceNote>{t('trial.note')}</FinanceNote>
  </>;
}

/** L6 实时科目余额。 */
function LiveBalances({ book, periodId, minorUnit, onOpenLeaf, onAuthorizationChanged }: {
  book: LedgerBook; periodId: string; minorUnit?: number; onOpenLeaf: (line: AccountBalanceLine) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const balances = useFinanceList<AccountBalanceLine>(financePaths.accountBalances(book.ledgerBookId, periodId),
    (path, signal) => session.request(path, { signal }, value => decodeAccountBalanceList(value, book.ledgerBookId, periodId).lines),
    onAuthorizationChanged, t('trial.balancesLoadFailed'));
  if (balances.error) return <LoadFailure message={balances.error} onRetry={balances.reload} />;
  return <BalanceTree lines={balances.items} loading={balances.loading} minorUnit={minorUnit} currency={book.currency} onOpenLeaf={onOpenLeaf}
    emptyText={t('trial.balancesEmpty')} />;
}

/** 科目余额树（L6 与试算详情共用）：全部展开 / 折叠，末级行可点击；表尾为一级科目合计与借贷平衡结果。 */
function BalanceTree({ lines, loading, minorUnit, currency, onOpenLeaf, emptyText }: {
  lines: readonly AccountBalanceLine[]; loading?: boolean; minorUnit?: number; currency: string; onOpenLeaf: (line: AccountBalanceLine) => void; emptyText: string;
}) {
  const t = useT('finance');
  const tree = useMemo(() => buildAccountTree(lines), [lines]);
  const allKeys = useMemo(() => expandableKeys(tree), [tree]);
  const [expanded, setExpanded] = useState<readonly Key[] | null>(null);
  const totals = useMemo(() => balanceTotals(lines), [lines]);
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  const sided = (row: AccountBalanceLine, value: string) => <span><Amount value={value} minorUnit={minorUnit} /> {enumLabel(t, 'enum.side', row.normalSide)}</span>;
  return <>
    <div className="finance-section-head">
      <h2>{t('trial.balancesTitle', { currency })}</h2><span className="finance-spacer" />
      <Button size="small" onClick={() => setExpanded(allKeys)}>{t('charts.expandAll')}</Button>
      <Button size="small" onClick={() => setExpanded([])}>{t('charts.collapseAll')}</Button>
    </div>
    <section className="table-panel">
      <Table<TreeNode<AccountBalanceLine>> rowKey="chartAccountId" tableLayout="fixed" dataSource={tree} loading={loading} pagination={false} scroll={{ x: 980 }}
        expandable={{ expandedRowKeys: [...(expanded ?? allKeys)], onExpandedRowsChange: keys => setExpanded(keys) }}
        onRow={row => row.postingAllowed ? { onClick: () => onOpenLeaf(row), className: 'clickable-row' } : {}}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} /> }} columns={[
          { title: t('charts.changeField.accountNo'), width: 170, render: (_, row) => <span className="mono">{row.accountNo}</span> },
          { title: t('charts.changeField.name'), render: (_, row) => row.postingAllowed ? <a>{row.accountName}</a> : <strong>{row.accountName}</strong> },
          { title: t('trial.column.openingBalance'), width: 160, align: 'right', render: (_, row) => sided(row, row.openingBalance) },
          { title: t('trial.column.periodDebit'), width: 150, align: 'right', render: (_, row) => amount(row.periodDebit) },
          { title: t('trial.column.periodCredit'), width: 150, align: 'right', render: (_, row) => amount(row.periodCredit) },
          { title: t('trial.column.closingBalance'), width: 160, align: 'right', render: (_, row) => sided(row, row.closingBalance) },
        ]} />
    </section>
    {lines.length > 0 && <div className="finance-trial-totals">
      <span>{t('trial.totals.period')} {amount(totals.periodDebit)} · {t('trial.totals.credit')} {amount(totals.periodCredit)}</span>
      <span>{t('trial.totals.closingDebit')} {amount(totals.closingDebit)} · {t('trial.totals.credit')} {amount(totals.closingCredit)}</span>
      <Tag color={totals.balanced ? 'green' : 'red'}>{totals.balanced ? t('trial.totals.balanced') : t('journal.unbalanced')}</Tag>
    </div>}
  </>;
}

/** 试算记录列表，按序号降序；点击行打开详情。 */
function TrialRuns({ book, periodId, minorUnit }: { book: LedgerBook; periodId: string; minorUnit?: number }) {
  const t = useT('finance');
  const location = useLocation(); const navigate = useNavigate();
  const decode = useMemo(() => pageOf(decodeTrialBalanceRun(book.ledgerBookId, periodId)), [book.ledgerBookId, periodId]);
  const page = usePaging<TrialBalanceRun>(financePaths.trialBalances(book.ledgerBookId, periodId), decode);
  const hint = financeErrorTarget(page.failure)?.message;
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  if (page.error) return <LoadFailure message={hint ?? page.error} onRetry={page.refresh} />;
  return <section className="table-panel">
    <Table<TrialBalanceRun> rowKey="trialBalanceRunId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 900 }}
      pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
      onRow={row => ({ onClick: () => void navigate({ search: trialSearch(location.search, { run: row.runNo }) }), className: 'clickable-row' })}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('trial.runsEmpty')} /> }} columns={[
        { title: t('trial.column.runNo'), width: 80, render: (_, row) => <a className="mono">{row.runNo}</a> },
        { title: t('field.status'), width: 100, render: (_, row) => <StatusTag status={row.status} enumType="trialStatus" /> },
        { title: t('field.debitTotal'), width: 150, align: 'right', render: (_, row) => amount(row.totalDebit) },
        { title: t('field.creditTotal'), width: 150, align: 'right', render: (_, row) => amount(row.totalCredit) },
        { title: t('trial.column.exceptionCount'), width: 90, render: (_, row) => row.exceptionCount ? <Tag color="red">{row.exceptionCount}</Tag> : 0 },
        { title: t('trial.column.asOfSequence'), width: 110, render: (_, row) => <span className="mono">{row.asOfSequence}</span> },
        { title: t('trial.column.runAt'), render: (_, row) => localTime(row.createTime) },
      ]} />
  </section>;
}

/** 试算详情抽屉：摘要、科目树（末级试算行 + 级次汇总行）与异常。 */
function TrialRunDrawer({ book, periodId, runNo, minorUnit, onOpenLeaf, onClose }: {
  book: LedgerBook; periodId: string; runNo: string; minorUnit?: number; onOpenLeaf: (line: AccountBalanceLine) => void; onClose: () => void;
}) {
  const t = useT('finance');
  const detail = useFinanceList<TrialBalanceDetail>(financePaths.trialBalance(book.ledgerBookId, periodId, runNo),
    async (path, signal) => [await session.request(path, { signal }, value => decodeTrialBalanceDetail(value, book.ledgerBookId, periodId, runNo))],
    undefined, t('trial.detailLoadFailed'));
  const run = detail.items[0];
  const lines = useMemo(() => run ? [...run.summaryLines, ...run.lines] : [], [run]);
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  return <Drawer open size={980} rootClassName="finance-drawer finance-page" onClose={onClose} destroyOnHidden title={t('trial.detailTitle', { runNo })}>
    {detail.error ? <Alert type="warning" showIcon title={detail.error} /> : !run ? <Spin /> : <>
      <Descriptions column={2} size="small" className="finance-detail-head" items={[
        { key: 'book', label: t('field.book'), children: t('common.labelWithId', { label: bookTitle(book), id: book.ledgerBookId }) },
        { key: 'status', label: t('field.status'), children: <StatusTag status={run.status} enumType="trialStatus" /> },
        { key: 'debit', label: t('field.debitTotal'), children: amount(run.totalDebit) },
        { key: 'credit', label: t('field.creditTotal'), children: amount(run.totalCredit) },
        { key: 'sequence', label: t('trial.column.asOfSequence'), children: <span className="mono">{run.asOfSequence}</span> },
        { key: 'time', label: t('trial.column.runAt'), children: localTime(run.createTime) },
        { key: 'lines', label: t('trial.field.lineCount'), children: run.lineCount },
        { key: 'hash', label: t('trial.field.contentHash'), children: <span className="mono" title={run.contentHash}>{run.contentHash.slice(0, 16)}…</span> },
      ]} />
      <div className="finance-section"><BalanceTree lines={lines} minorUnit={minorUnit} currency={book.currency} onOpenLeaf={onOpenLeaf} emptyText={t('trial.detailLinesEmpty')} /></div>
      <h3 className="finance-subtitle">{t('trial.exceptionsTitle', { count: run.exceptions.length })}</h3>
      <section className="table-panel">
        <Table<TrialBalanceFinding> rowKey={row => `${row.exceptionType}:${row.referenceType}:${row.referenceId}`} size="small" tableLayout="fixed"
          dataSource={run.exceptions} pagination={false} locale={{ emptyText: t('trial.exceptionsEmpty') }} columns={[
            { title: t('trial.column.exceptionType'), dataIndex: 'exceptionType', width: 240 },
            { title: t('trial.column.reference'), render: (_, row) => <div><div>{row.referenceType}</div><div className="cell-meta mono">{row.referenceId}</div></div> },
            { title: t('trial.column.difference'), width: 140, align: 'right', render: (_, row) => amount(row.differenceAmount) },
            { title: t('field.status'), dataIndex: 'status', width: 120 },
          ]} />
      </section>
    </>}
  </Drawer>;
}
