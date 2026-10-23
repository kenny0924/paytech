import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Button, Empty, Form, Input, Select, Table } from 'antd';
import type { TableColumnsType } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { PagingRequestBody } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import { enumLabel, translator } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  accountFilters, balanceBuckets, bookFilters, decodeLedgerAccount, decodeLedgerBook, entityLabel,
  financePaths, ledgerEntityTypes, ownerTypes, pageOf,
} from './finance';
import type { BookRef, Currency, FinanceEntity, LedgerAccount, LedgerBook } from './finance';
import { CreateAccountModal } from './FinanceModals';
import { Amount, FinanceNote, LoadFailure, SideTag, StatusTag } from './FinanceParts';
import { useChartAccounts } from './useFinanceData';

const bookPage = pageOf(decodeLedgerBook);
const accountPage = pageOf(decodeLedgerAccount);
const options = (values: readonly string[]) => values.map(value => ({ value, label: value }));
type Query = { paging: PagingRequestBody; filters: Readonly<Record<string, string>> };
const tFinance = translator('finance');

/** 账簿标题：主体 / 币种。 */
export function bookTitle(book: BookRef, entities?: ReadonlyMap<string, FinanceEntity>) {
  if (!book.entityType || !book.entityId || !book.currency) return tFinance('books.bookById', { ledgerBookId: book.ledgerBookId });
  return `${entityLabel(book.entityType, book.entityId, entities)} / ${book.currency}`;
}

/** 账簿列表（基础资料与账本查询共用）：按主体类型、主体标识、币种筛选并分页；点击行或「查看账户」进入下一级。 */
export function BookList({ entities, currencies, initialFilters = {}, initialPaging, onQueryChange, selectedId, onSelect, reloadToken = 0 }: {
  entities?: ReadonlyMap<string, FinanceEntity>; currencies: readonly Currency[]; initialFilters?: Readonly<Record<string, string>>;
  initialPaging?: PagingRequestBody; onQueryChange?: (query: Query) => void; selectedId?: string | null; onSelect: (book: LedgerBook) => void; reloadToken?: number;
}) {
  const t = useT('finance');
  const [form] = Form.useForm();
  const entityTypeOptions = useMemo(() => ledgerEntityTypes.map(value => ({ value, label: `${enumLabel(t, 'enum.ledgerEntityType', value)} ${value}` })), [t]);
  const page = usePaging<LedgerBook>(financePaths.ledgerBooks, bookPage, initialFilters, { initialPaging, onQueryChange });
  useEffect(() => { if (reloadToken) page.refresh(); }, [reloadToken]);
  return <>
    <Form form={form} name="finance-book-filters" layout="vertical" initialValues={initialFilters} onFinish={values => page.applyFilters(bookFilters(values))}><div className="finance-search">
      <Form.Item name="entityType" label={t('field.entityType')}><Select allowClear placeholder={t('placeholder.all')} options={entityTypeOptions} /></Form.Item>
      <Form.Item name="entityId" label={t('field.entityId')}><Input allowClear maxLength={64} placeholder={t('books.entityIdPlaceholder')} /></Form.Item>
      <Form.Item name="currency" label={t('field.currency')}><Select allowClear placeholder={t('placeholder.all')} options={currencies.map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
      <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
        <Button onClick={() => { form.resetFields(); form.setFieldsValue({ entityType: undefined, entityId: undefined, currency: undefined }); page.applyFilters({}); }}>{t('common.reset')}</Button></div>
    </div></Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<LedgerBook> rowKey="ledgerBookId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 820 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        rowClassName={row => row.ledgerBookId === selectedId ? 'finance-row-selected' : 'clickable-row'}
        onRow={row => ({ onClick: () => onSelect(row) })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initialFilters).length ? t('books.emptyFiltered') : t('books.empty')} /> }} columns={[
          { title: t('field.entityType'), width: 110, render: (_, row) => enumLabel(t, 'enum.ledgerEntityType', row.entityType) },
          { title: t('field.entityId'), render: (_, row) => entityLabel(row.entityType, row.entityId, entities) },
          { title: t('field.currency'), dataIndex: 'currency', width: 90 },
          { title: t('field.chart'), dataIndex: 'chartCode', width: 160 },
          { title: t('field.status'), width: 110, render: (_, row) => <StatusTag status={row.bookStatus} /> },
          { title: t('field.actions'), width: 100, render: (_, row) => <Button type="link" size="small" onClick={event => { event.stopPropagation(); onSelect(row); }}>{t('books.viewAccounts')}</Button> },
        ]} />
    </section>}
  </>;
}

/**
 * 账户列表（共用）：按科目、核算对象类型 / 标识、余额桶筛选并分页；金额按币种精度右对齐，负净额标识反向。
 * <li>基础资料只维护开户，传 showBalances=false 隐藏借贷合计与净余额，并以 rowAction 跳转账本查询看流水。</li>
 */
export function AccountList({ book, minorUnit, initialFilters = {}, initialPaging, onQueryChange, onSelect, reloadToken = 0, heading, extra, showBalances = true, rowAction, onAuthorizationChanged }: {
  book: BookRef; minorUnit?: number; initialFilters?: Readonly<Record<string, string>>; initialPaging?: PagingRequestBody;
  onQueryChange?: (query: Query) => void; onSelect?: (account: LedgerAccount) => void; reloadToken?: number; heading?: ReactNode; extra?: ReactNode;
  showBalances?: boolean; rowAction?: (account: LedgerAccount) => ReactNode; onAuthorizationChanged?: () => void;
}) {
  const t = useT('finance');
  const [form] = Form.useForm();
  const page = usePaging<LedgerAccount>(financePaths.accounts(book.ledgerBookId), accountPage, initialFilters, { initialPaging, onQueryChange });
  const chartAccounts = useChartAccounts(book.chartCode ?? null, { postingAllowed: true }, onAuthorizationChanged);
  useEffect(() => { if (reloadToken) page.refresh(); }, [reloadToken]);
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  const columns: TableColumnsType<LedgerAccount> = [
    { title: t('field.accountNo'), width: 220, render: (_, row) => <span className="mono">{row.accountNo}</span> },
    { title: t('field.accountCode'), width: 250, render: (_, row) => <div><div className="finance-code">{row.accountCode}</div>
      <div className="cell-meta">{row.accountName}</div></div> },
    { title: t('field.accountingDimension'), width: 200, render: (_, row) => <div><div>{row.ownerType}</div><div className="cell-meta">{row.ownerId}</div></div> },
    { title: t('field.balanceBucket'), dataIndex: 'balanceBucket', width: 110 },
    { title: t('field.side'), width: 70, render: (_, row) => <SideTag side={row.normalSide} /> },
    { title: t('field.status'), width: 80, render: (_, row) => <StatusTag status={row.accountStatus} enumType="accountStatus" /> },
    ...(showBalances ? [
      { title: t('field.debitTotal'), width: 140, align: 'right' as const, render: (_: unknown, row: LedgerAccount) => amount(row.debitTotal) },
      { title: t('field.creditTotal'), width: 140, align: 'right' as const, render: (_: unknown, row: LedgerAccount) => amount(row.creditTotal) },
      { title: t('field.netBalance'), width: 160, align: 'right' as const, render: (_: unknown, row: LedgerAccount) => amount(row.netBalance) },
    ] : []),
    ...(rowAction ? [{ title: t('field.actions'), width: 100, render: (_: unknown, row: LedgerAccount) => rowAction(row) }] : []),
  ];
  return <>
    <Form form={form} name="finance-account-filters" layout="vertical" initialValues={initialFilters} onFinish={values => page.applyFilters(accountFilters(values))}>
      <div className="finance-section-head finance-account-head">
        {heading}
        <div className="finance-search finance-search-inline">
          <Form.Item name="accountNo" label={t('field.accountNo')} normalize={(value: string) => value?.toUpperCase()}>
            <Input allowClear maxLength={48} placeholder={t('books.accountNoPlaceholder')} /></Form.Item>
          <Form.Item name="accountCode" label={t('field.accountCode')}><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={t('placeholder.all')} loading={chartAccounts.loading}
            options={chartAccounts.items.map(item => ({ value: item.accountCode, label: `${item.accountNo} ${item.accountName}` }))} /></Form.Item>
          <Form.Item name="ownerType" label={t('field.accountingDimensionType')}><Select allowClear placeholder={t('placeholder.all')} options={options(ownerTypes)} /></Form.Item>
          <Form.Item name="ownerId" label={t('field.accountingDimensionId')}><Input allowClear maxLength={64} placeholder={t('placeholder.enterId')} /></Form.Item>
          <Form.Item name="balanceBucket" label={t('field.balanceBucket')}><Select allowClear placeholder={t('placeholder.all')} options={options(balanceBuckets)} /></Form.Item>
          <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
            <Button onClick={() => { form.setFieldsValue({ accountNo: undefined, accountCode: undefined, ownerType: undefined, ownerId: undefined, balanceBucket: undefined }); page.applyFilters({}); }}>{t('common.reset')}</Button></div>
        </div>
        {extra}
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<LedgerAccount> rowKey="accountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: showBalances ? 1320 : 930 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        onRow={row => onSelect ? { onClick: () => onSelect(row), className: 'clickable-row' } : {}}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initialFilters).length ? t('books.accountsEmptyFiltered') : t('books.accountsEmpty')} /> }} columns={columns} />
    </section>}
  </>;
}

interface Props {
  canManage: boolean; reloadToken: number; entities: ReadonlyMap<string, FinanceEntity>; currencies: readonly Currency[];
  minorUnits: ReadonlyMap<string, number>; onAuthorizationChanged: () => void;
}

const ledgerRoute = '/finance/ledger';

/** 账簿与账户（开户维护）：上半账簿列表（选中高亮），下半所选账簿的总账账户，不显示余额，「查看流水」跳转账本查询；新建账户仅维护权限可见。 */
export function SetupBooksTab({ canManage, reloadToken, entities, currencies, minorUnits, onAuthorizationChanged }: Props) {
  const t = useT('finance');
  const [selected, setSelected] = useState<LedgerBook | null>(null);
  const [creating, setCreating] = useState(false); const [accountReload, setAccountReload] = useState(0);
  const navigate = useNavigate();
  // 跳到账本查询的流水层级；随历史状态带上账簿与账户，面包屑直接显示名称。
  const openLines = (book: LedgerBook, account: LedgerAccount) => void navigate(
    { pathname: ledgerRoute, search: `?book=${book.ledgerBookId}&account=${account.accountId}` }, { state: { book, account } });
  return <>
    <BookList entities={entities} currencies={currencies} selectedId={selected?.ledgerBookId} onSelect={setSelected} reloadToken={reloadToken} />
    {selected ? <section className="finance-section">
      <AccountList key={selected.ledgerBookId} book={selected} minorUnit={minorUnits.get(selected.currency)} reloadToken={accountReload}
        showBalances={false} rowAction={row => <Button type="link" size="small" onClick={() => openLines(selected, row)}>{t('books.viewEntries')}</Button>}
        onAuthorizationChanged={onAuthorizationChanged} heading={<div className="finance-account-title"><h2>{t('books.glAccountsTitle', { book: bookTitle(selected, entities) })}</h2><span className="cell-meta">{t('books.subledgerHint')}</span></div>}
        extra={canManage && <Button type="primary" className="finance-head-action" onClick={() => setCreating(true)}>{t('books.createAccount')}</Button>} />
    </section> : <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('books.selectBook')} /></div>}
    <FinanceNote>{t('books.note')}</FinanceNote>
    {creating && selected && <CreateAccountModal book={selected} entities={entities} onClose={() => setCreating(false)}
      onDone={() => { setCreating(false); setAccountReload(value => value + 1); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}
