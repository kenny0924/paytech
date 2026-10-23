import { Breadcrumb, Button, Empty, Form, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { DatePicker } from '../components/DatePicker';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  decodeJournalLine, entryTypeColors, financeErrorTarget, financePaths, journalLineSourceMeta, ledgerLevelSearch, ledgerQuerySearch,
  lineFilters, pageOf, readLedgerQuery, replaceQuery,
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
  const t = useT('finance');
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
  const accountLabel = account ? accountTitle(t, account) : '';
  return <>
    {book && <Breadcrumb className="finance-breadcrumb" items={[
      { title: <a onClick={() => toLevel('books')}>{t('field.book')}</a> },
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
      <FinanceNote>{t('ledger.accountsNote')}</FinanceNote>
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
function accountTitle(t: Translate, account: AccountRef): string {
  if (!account.accountCode) return t('ledger.accountById', { accountId: account.accountId });
  return [account.accountNo, account.accountCode, account.ownerType && account.ownerId ? `${account.ownerType} ${account.ownerId}` : null, account.balanceBucket]
    .filter(Boolean).join(' · ');
}

function JournalLines({ book, account, minorUnit, query, onQueryChange, onOpenEntry }: {
  book: BookRef; account: AccountRef; minorUnit?: number; query: LedgerQuery;
  onQueryChange: (next: { paging: { page: number; size: number }; filters: Readonly<Record<string, string>> }) => void;
  onOpenEntry: (journalEntryId: string) => void;
}) {
  const t = useT('finance');
  const [form] = Form.useForm<{ postingDateFrom?: string; postingDateTo?: string }>();
  const page = usePaging<JournalLine>(financePaths.journalLines(book.ledgerBookId, account.accountId), linePage, query.lineFilters,
    { initialPaging: query.linePaging, onQueryChange });
  const accountName = account.accountName;
  const pageHint = financeErrorTarget(page.failure)?.message;
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  return <>
    {account.accountCode && <dl className="finance-summary">
      {account.accountNo && <div><dt>{t('field.accountNo')}</dt><dd className="mono">{account.accountNo}</dd></div>}
      <div><dt>{t('field.accountCode')}</dt><dd>{account.accountCode}{accountName ? ` · ${accountName}` : ''}</dd></div>
      <div><dt>{t('field.accountingDimension')}</dt><dd>{account.ownerType} · {account.ownerId}</dd></div>
      <div><dt>{t('field.balanceBucket')}</dt><dd>{account.balanceBucket}</dd></div>
      {account.normalSide && <div><dt>{t('field.side')}</dt><dd>{enumLabel(t, 'enum.side', account.normalSide)} {account.normalSide}</dd></div>}
      {account.netBalance && <div><dt>{t('subledger.entries.currentNetBalance')}</dt><dd><strong><Amount value={account.netBalance} minorUnit={minorUnit} suffix={book.currency} /></strong></dd></div>}
    </dl>}
    <Form form={form} name="finance-line-filters" layout="vertical" initialValues={query.lineFilters} onFinish={values => page.applyFilters(lineFilters(values))}>
      <div className="finance-search">
        <Form.Item name="postingDateFrom" label={t('ledger.lines.postingDateFrom')}><DatePicker placeholder={t('placeholder.any')} /></Form.Item>
        <Form.Item name="postingDateTo" label={t('ledger.lines.postingDateTo')} dependencies={['postingDateFrom']} rules={[({ getFieldValue }) => ({ validator: async (_, value) => {
          lineFilters({ postingDateFrom: getFieldValue('postingDateFrom'), postingDateTo: value });
        } })]}><DatePicker placeholder={t('placeholder.any')} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
          <Button onClick={() => { form.setFieldsValue({ postingDateFrom: undefined, postingDateTo: undefined }); page.applyFilters({}); }}>{t('common.reset')}</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={pageHint ?? page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<JournalLine> rowKey={row => `${row.journalEntryId}:${row.lineNo}`} tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1120 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(query.lineFilters).length ? t('ledger.lines.emptyFiltered') : t('subledger.entries.empty')} /> }} columns={[
          { title: t('ledger.lines.column.journalSequence'), width: 110, render: (_, row) => <a className="mono" onClick={() => onOpenEntry(row.journalEntryId)}>{row.journalSequence}</a> },
          { title: t('field.postingDate'), dataIndex: 'postingDate', width: 120 },
          { title: t('ledger.lines.column.type'), width: 80, render: (_, row) => <Tag color={entryTypeColors[row.entryType]}>{enumLabel(t, 'enum.entryType', row.entryType)}</Tag> },
          { title: t('field.lineNo'), dataIndex: 'lineNo', width: 64 },
          { title: t('field.side'), width: 64, render: (_, row) => <SideTag side={row.entrySide} /> },
          { title: t('field.amount'), width: 130, align: 'right', render: (_, row) => amount(row.amount) },
          { title: t('ledger.lines.column.balanceAfter'), width: 150, align: 'right', render: (_, row) => amount(row.balanceAfter) },
          { title: t('journal.field.instructionType'), dataIndex: 'instructionType', width: 190, ellipsis: true },
          { title: t('subledger.entries.column.source'), render: (_, row) => <div><div>{row.originType} · {row.originType === 'MANUAL' ? t('journal.field.manualAdjustment') : row.sourceSystem ?? '—'}</div>
            <div className="cell-meta">{journalLineSourceMeta(row)}</div></div> },
        ]} />
    </section>}
    <FinanceNote>{t('ledger.lines.note')}</FinanceNote>
  </>;
}
