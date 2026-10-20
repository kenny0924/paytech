import { Breadcrumb, Button, Empty, Form, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { DatePicker } from '../components/DatePicker';
import { usePaging } from '../hooks/usePaging';
import {
  decodeJournalLine, entryTypeColors, entryTypeLabels, financeErrorTarget, financePaths, journalLineSourceMeta, ledgerLevelSearch, ledgerQuerySearch,
  lineFilters, pageOf, readLedgerQuery, replaceQuery, sideLabels,
} from './finance';
import type { AccountRef, BookRef, JournalLine, LedgerAccount, LedgerBook, LedgerLevel, LedgerQuery } from './finance';
import { Amount, FinanceNote, LoadFailure, SideTag } from './FinanceParts';
import { JournalEntryDrawer } from './JournalEntryDrawer';
import { AccountList, BookList, bookTitle } from './SetupBooksTab';
import { useCurrencies } from './useFinanceData';

const linePage = pageOf(decodeJournalLine);
/** 下钻时随浏览器历史保存的展示信息；直接粘贴 URL 进入时不存在，页面只用主键降级展示。 */
interface DrillState { book?: LedgerBook; account?: LedgerAccount }

/** 账本查询：账簿 › 账户 › 流水逐级下钻，分录详情在抽屉中；各级选择、筛选与分页都在 URL，面包屑回退保留条件。 */
export function LedgerDrilldown({ onAuthorizationChanged }: { onAuthorizationChanged: () => void }) {
  const location = useLocation(); const navigate = useNavigate();
  const query = readLedgerQuery(new URLSearchParams(location.search));
  const state = (location.state ?? {}) as DrillState;
  const currencies = useCurrencies(onAuthorizationChanged);
  const book: BookRef | null = query.book ? (state.book?.ledgerBookId === query.book ? state.book : { ledgerBookId: query.book }) : null;
  const account: AccountRef | null = query.account ? (state.account?.accountId === query.account ? state.account : { accountId: query.account }) : null;
  const minorUnit = book?.currency ? currencies.minorUnits.get(book.currency) : undefined;
  const go = (search: string, next: DrillState, replace = false) => void navigate({ search }, { state: next, replace });
  const toLevel = (level: LedgerLevel) => go(ledgerLevelSearch(location.search, level), level === 'books' ? {} : { book: state.book });
  const writeQuery = (level: LedgerLevel) => (next: { paging: { page: number; size: number }; filters: Readonly<Record<string, string>> }) =>
    go(ledgerQuerySearch(location.search, level, next.filters, next.paging), state, true);
  const accountLabel = account ? accountTitle(account) : '';
  return <>
    {book && <Breadcrumb className="finance-breadcrumb" items={[
      { title: <a onClick={() => toLevel('books')}>账簿</a> },
      { title: account ? <a onClick={() => toLevel('accounts')}>{bookTitle(book)}{book.chartCode ? ` · ${book.chartCode}` : ''}</a>
        : <strong>{bookTitle(book)}{book.chartCode ? ` · ${book.chartCode}` : ''}</strong> },
      ...(account ? [{ title: <strong>{accountLabel}</strong> }] : []),
    ]} />}
    {query.level === 'books' && <BookList key={keyOf(query, 'books')} currencies={currencies.items} initialFilters={query.bookFilters} initialPaging={query.bookPaging}
      onQueryChange={writeQuery('books')} onSelect={next => go(ledgerLevelSearch(location.search, 'accounts', { book: next.ledgerBookId }), { book: next })} />}
    {query.level === 'accounts' && book && <>
      <AccountList key={keyOf(query, 'accounts')} book={book} minorUnit={minorUnit} initialFilters={query.accountFilters} initialPaging={query.accountPaging}
        onQueryChange={writeQuery('accounts')} onAuthorizationChanged={onAuthorizationChanged}
        onSelect={next => go(ledgerLevelSearch(location.search, 'lines', { account: next.accountId }), { book: state.book, account: next })} />
      <FinanceNote>点击账户行进入流水。总账只有以账簿主体为核算对象的控制账户（余额桶 NONE）；Client / Partner 明细与余额桶在「明细账查询」。账本查询只读，不显示新建按钮。</FinanceNote>
    </>}
    {query.level === 'lines' && book && account && <JournalLines key={keyOf(query, 'lines')} book={book} account={account} minorUnit={minorUnit} query={query}
      onQueryChange={writeQuery('lines')} onOpenEntry={entry => go(replaceQuery(location.search, { entry }), state)} />}
    {book && query.entry && <JournalEntryDrawer book={book} bookLabel={bookTitle(book)} journalEntryId={query.entry} minorUnit={minorUnit}
      onClose={() => go(replaceQuery(location.search, { entry: null }), state)} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

/** 每级列表按本级 URL 条件重建，条件变化后由 usePaging 以新的初始值重新加载。 */
function keyOf(query: LedgerQuery, level: LedgerLevel): string {
  if (level === 'books') return JSON.stringify([query.bookFilters, query.bookPaging]);
  if (level === 'accounts') return JSON.stringify([query.book, query.accountFilters, query.accountPaging]);
  return JSON.stringify([query.book, query.account, query.lineFilters, query.linePaging]);
}

/** 面包屑账户：有账户号时以账户号开头，其后科目与核算对象；直接粘贴 URL 进入时只有主键。 */
function accountTitle(account: AccountRef): string {
  if (!account.accountCode) return `账户 ${account.accountId}`;
  return [account.accountNo, account.accountCode, account.ownerType && account.ownerId ? `${account.ownerType} ${account.ownerId}` : null, account.balanceBucket]
    .filter(Boolean).join(' · ');
}

function JournalLines({ book, account, minorUnit, query, onQueryChange, onOpenEntry }: {
  book: BookRef; account: AccountRef; minorUnit?: number; query: LedgerQuery;
  onQueryChange: (next: { paging: { page: number; size: number }; filters: Readonly<Record<string, string>> }) => void;
  onOpenEntry: (journalEntryId: string) => void;
}) {
  const [form] = Form.useForm<{ postingDateFrom?: string; postingDateTo?: string }>();
  const page = usePaging<JournalLine>(financePaths.journalLines(book.ledgerBookId, account.accountId), linePage, query.lineFilters,
    { initialPaging: query.linePaging, onQueryChange });
  const accountName = account.accountName;
  const pageHint = financeErrorTarget(page.failure)?.message;
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  return <>
    {account.accountCode && <dl className="finance-summary">
      {account.accountNo && <div><dt>账户号</dt><dd className="mono">{account.accountNo}</dd></div>}
      <div><dt>科目</dt><dd>{account.accountCode}{accountName ? ` · ${accountName}` : ''}</dd></div>
      <div><dt>核算对象</dt><dd>{account.ownerType} · {account.ownerId}</dd></div>
      <div><dt>余额桶</dt><dd>{account.balanceBucket}</dd></div>
      {account.normalSide && <div><dt>方向</dt><dd>{sideLabels[account.normalSide]} {account.normalSide}</dd></div>}
      {account.netBalance && <div><dt>当前净余额</dt><dd><strong><Amount value={account.netBalance} minorUnit={minorUnit} suffix={book.currency} /></strong></dd></div>}
    </dl>}
    <Form form={form} name="finance-line-filters" layout="vertical" initialValues={query.lineFilters} onFinish={values => page.applyFilters(lineFilters(values))}>
      <div className="finance-search">
        <Form.Item name="postingDateFrom" label="过账日期从"><DatePicker placeholder="不限" /></Form.Item>
        <Form.Item name="postingDateTo" label="至" dependencies={['postingDateFrom']} rules={[({ getFieldValue }) => ({ validator: async (_, value) => {
          lineFilters({ postingDateFrom: getFieldValue('postingDateFrom'), postingDateTo: value });
        } })]}><DatePicker placeholder="不限" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { form.setFieldsValue({ postingDateFrom: undefined, postingDateTo: undefined }); page.applyFilters({}); }}>重置</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={pageHint ?? page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<JournalLine> rowKey={row => `${row.journalEntryId}:${row.lineNo}`} tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1120 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(query.lineFilters).length ? '该日期范围内没有流水' : '该账户还没有流水'} /> }} columns={[
          { title: '分录序号', width: 110, render: (_, row) => <a className="mono" onClick={() => onOpenEntry(row.journalEntryId)}>{row.journalSequence}</a> },
          { title: '过账日期', dataIndex: 'postingDate', width: 120 },
          { title: '类型', width: 80, render: (_, row) => <Tag color={entryTypeColors[row.entryType]}>{entryTypeLabels[row.entryType]}</Tag> },
          { title: '行号', dataIndex: 'lineNo', width: 64 },
          { title: '方向', width: 64, render: (_, row) => <SideTag side={row.entrySide} /> },
          { title: '金额', width: 130, align: 'right', render: (_, row) => amount(row.amount) },
          { title: '行后余额', width: 150, align: 'right', render: (_, row) => amount(row.balanceAfter) },
          { title: '指令类型', dataIndex: 'instructionType', width: 190, ellipsis: true },
          { title: '来源', render: (_, row) => <div><div>{row.originType} · {row.originType === 'MANUAL' ? '人工调整' : row.sourceSystem ?? '—'}</div>
            <div className="cell-meta">{journalLineSourceMeta(row)}</div></div> },
        ]} />
    </section>}
    <FinanceNote>行后余额按全量历史累计，筛选不改变起点；来源为 ACCOUNT_POSTING 的分录由 account 入账批次生成；点击分录序号打开分录详情。</FinanceNote>
  </>;
}
