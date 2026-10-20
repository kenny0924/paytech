import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { Button, Empty, Form, Input, Select, Table } from 'antd';
import type { PagingRequestBody } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import {
  accountFilters, accountStatusLabels, balanceBuckets, bookFilters, decodeLedgerAccount, decodeLedgerBook, entityLabel, entityTypeLabels,
  financePaths, ledgerEntityTypes, ownerTypes, pageOf,
} from './finance';
import type { BookRef, Currency, FinanceEntity, LedgerAccount, LedgerBook } from './finance';
import { CreateAccountModal } from './FinanceModals';
import { Amount, FinanceNote, LoadFailure, SideTag, StatusTag } from './FinanceParts';
import { useChartAccounts } from './useFinanceData';

const bookPage = pageOf(decodeLedgerBook);
const accountPage = pageOf(decodeLedgerAccount);
const entityTypeOptions = ledgerEntityTypes.map(value => ({ value, label: `${entityTypeLabels[value]} ${value}` }));
const options = (values: readonly string[]) => values.map(value => ({ value, label: value }));
type Query = { paging: PagingRequestBody; filters: Readonly<Record<string, string>> };

/** 账簿标题：主体 / 币种。 */
export function bookTitle(book: BookRef, entities?: ReadonlyMap<string, FinanceEntity>) {
  if (!book.entityType || !book.entityId || !book.currency) return `账簿 ${book.ledgerBookId}`;
  return `${entityLabel(book.entityType, book.entityId, entities)} / ${book.currency}`;
}

/** 账簿列表（基础资料与账本查询共用）：按主体类型、主体标识、币种筛选并分页；点击行或「查看账户」进入下一级。 */
export function BookList({ entities, currencies, initialFilters = {}, initialPaging, onQueryChange, selectedId, onSelect, reloadToken = 0 }: {
  entities?: ReadonlyMap<string, FinanceEntity>; currencies: readonly Currency[]; initialFilters?: Readonly<Record<string, string>>;
  initialPaging?: PagingRequestBody; onQueryChange?: (query: Query) => void; selectedId?: string | null; onSelect: (book: LedgerBook) => void; reloadToken?: number;
}) {
  const [form] = Form.useForm();
  const page = usePaging<LedgerBook>(financePaths.ledgerBooks, bookPage, initialFilters, { initialPaging, onQueryChange });
  useEffect(() => { if (reloadToken) page.refresh(); }, [reloadToken]);
  return <>
    <Form form={form} name="finance-book-filters" layout="vertical" initialValues={initialFilters} onFinish={values => page.applyFilters(bookFilters(values))}><div className="finance-search">
      <Form.Item name="entityType" label="主体类型"><Select allowClear placeholder="全部" options={entityTypeOptions} /></Form.Item>
      <Form.Item name="entityId" label="主体标识"><Input allowClear maxLength={64} placeholder="请输入 entityNo 或财务主体主键" /></Form.Item>
      <Form.Item name="currency" label="币种"><Select allowClear placeholder="全部" options={currencies.map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
      <div className="configuration-actions"><Button htmlType="submit">查询</Button>
        <Button onClick={() => { form.resetFields(); form.setFieldsValue({ entityType: undefined, entityId: undefined, currency: undefined }); page.applyFilters({}); }}>重置</Button></div>
    </div></Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<LedgerBook> rowKey="ledgerBookId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 820 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        rowClassName={row => row.ledgerBookId === selectedId ? 'finance-row-selected' : 'clickable-row'}
        onRow={row => ({ onClick: () => onSelect(row) })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initialFilters).length ? '没有符合条件的账簿' : '还没有账簿'} /> }} columns={[
          { title: '主体类型', width: 110, render: (_, row) => entityTypeLabels[row.entityType] },
          { title: '主体标识', render: (_, row) => entityLabel(row.entityType, row.entityId, entities) },
          { title: '币种', dataIndex: 'currency', width: 90 },
          { title: '科目表', dataIndex: 'chartCode', width: 160 },
          { title: '状态', width: 110, render: (_, row) => <StatusTag status={row.bookStatus} /> },
          { title: '操作', width: 100, render: (_, row) => <Button type="link" size="small" onClick={event => { event.stopPropagation(); onSelect(row); }}>查看账户</Button> },
        ]} />
    </section>}
  </>;
}

/** 账户列表（共用）：按科目、核算对象类型 / 标识、余额桶筛选并分页；金额按币种精度右对齐，负净额标识反向。 */
export function AccountList({ book, minorUnit, initialFilters = {}, initialPaging, onQueryChange, onSelect, reloadToken = 0, heading, extra, onAuthorizationChanged }: {
  book: BookRef; minorUnit?: number; initialFilters?: Readonly<Record<string, string>>; initialPaging?: PagingRequestBody;
  onQueryChange?: (query: Query) => void; onSelect?: (account: LedgerAccount) => void; reloadToken?: number; heading?: ReactNode; extra?: ReactNode;
  onAuthorizationChanged?: () => void;
}) {
  const [form] = Form.useForm();
  const page = usePaging<LedgerAccount>(financePaths.accounts(book.ledgerBookId), accountPage, initialFilters, { initialPaging, onQueryChange });
  const chartAccounts = useChartAccounts(book.chartCode ?? null, { postingAllowed: true }, onAuthorizationChanged);
  useEffect(() => { if (reloadToken) page.refresh(); }, [reloadToken]);
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  return <>
    <Form form={form} name="finance-account-filters" layout="vertical" initialValues={initialFilters} onFinish={values => page.applyFilters(accountFilters(values))}>
      <div className="finance-section-head finance-account-head">
        {heading}
        <div className="finance-search finance-search-inline">
          <Form.Item name="accountNo" label="账户号" normalize={(value: string) => value?.toUpperCase()}>
            <Input allowClear maxLength={48} placeholder="精确匹配，如 AE001-USD-22010101-0001" /></Form.Item>
          <Form.Item name="accountCode" label="科目"><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder="全部" loading={chartAccounts.loading}
            options={chartAccounts.items.map(item => ({ value: item.accountCode, label: `${item.accountNo} ${item.accountName}` }))} /></Form.Item>
          <Form.Item name="ownerType" label="核算对象类型"><Select allowClear placeholder="全部" options={options(ownerTypes)} /></Form.Item>
          <Form.Item name="ownerId" label="核算对象标识"><Input allowClear maxLength={64} placeholder="请输入标识" /></Form.Item>
          <Form.Item name="balanceBucket" label="余额桶"><Select allowClear placeholder="全部" options={options(balanceBuckets)} /></Form.Item>
          <div className="configuration-actions"><Button htmlType="submit">查询</Button>
            <Button onClick={() => { form.setFieldsValue({ accountNo: undefined, accountCode: undefined, ownerType: undefined, ownerId: undefined, balanceBucket: undefined }); page.applyFilters({}); }}>重置</Button></div>
        </div>
        {extra}
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<LedgerAccount> rowKey="accountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1320 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        onRow={row => onSelect ? { onClick: () => onSelect(row), className: 'clickable-row' } : {}}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initialFilters).length ? '没有符合条件的账户' : '该账簿还没有账户'} /> }} columns={[
          { title: '账户号', width: 220, render: (_, row) => <span className="mono">{row.accountNo}</span> },
          { title: '科目', width: 250, render: (_, row) => <div><div className="finance-code">{row.accountCode}</div>
            <div className="cell-meta">{row.accountName}</div></div> },
          { title: '核算对象', width: 200, render: (_, row) => <div><div>{row.ownerType}</div><div className="cell-meta">{row.ownerId}</div></div> },
          { title: '余额桶', dataIndex: 'balanceBucket', width: 110 },
          { title: '方向', width: 70, render: (_, row) => <SideTag side={row.normalSide} /> },
          { title: '状态', width: 80, render: (_, row) => <StatusTag status={row.accountStatus} labels={accountStatusLabels} /> },
          { title: '借方合计', width: 140, align: 'right', render: (_, row) => amount(row.debitTotal) },
          { title: '贷方合计', width: 140, align: 'right', render: (_, row) => amount(row.creditTotal) },
          { title: '净余额', width: 160, align: 'right', render: (_, row) => amount(row.netBalance) },
        ]} />
    </section>}
  </>;
}

interface Props {
  canManage: boolean; reloadToken: number; entities: ReadonlyMap<string, FinanceEntity>; currencies: readonly Currency[];
  minorUnits: ReadonlyMap<string, number>; onAuthorizationChanged: () => void;
}

/** 账簿与账户：上半账簿列表（选中高亮），下半所选账簿的统一账户；新建账户仅维护权限可见。 */
export function SetupBooksTab({ canManage, reloadToken, entities, currencies, minorUnits, onAuthorizationChanged }: Props) {
  const [selected, setSelected] = useState<LedgerBook | null>(null);
  const [creating, setCreating] = useState(false); const [accountReload, setAccountReload] = useState(0);
  return <>
    <BookList entities={entities} currencies={currencies} selectedId={selected?.ledgerBookId} onSelect={setSelected} reloadToken={reloadToken} />
    {selected ? <section className="finance-section">
      <AccountList key={selected.ledgerBookId} book={selected} minorUnit={minorUnits.get(selected.currency)} reloadToken={accountReload}
        onAuthorizationChanged={onAuthorizationChanged} heading={<h2>统一账户 · {bookTitle(selected, entities)}</h2>}
        extra={canManage && <Button type="primary" className="finance-head-action" onClick={() => setCreating(true)}>新建账户</Button>} />
    </section> : <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择一个账簿查看账户" /></div>}
    <FinanceNote>账簿按主体 + 币种唯一，币种和科目表只能选 Finance 已启用项；账户只按核算对象开立（经营主体、财务主体、渠道账户、银行账户、税务机关），余额桶一律 NONE。Client / Partner 应付、保证金、追偿在总账是以账簿主体为核算对象的控制账户，明细与余额桶见「明细账查询」。净余额按正常方向计算，负数表示反向余额。</FinanceNote>
    {creating && selected && <CreateAccountModal book={selected} entities={entities} onClose={() => setCreating(false)}
      onDone={() => { setCreating(false); setAccountReload(value => value + 1); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}
