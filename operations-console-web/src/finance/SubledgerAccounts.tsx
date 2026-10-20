import { useMemo } from 'react';
import { Button, Empty, Form, Input, Select, Table } from 'antd';
import type { PagingRequestBody } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import {
  activeOnly, balanceBuckets, decodeOwnerAccount, entityTypeLabels, financeErrorTarget, financePaths, ledgerEntityTypes, ownerAccountNoPattern, ownerAccountStatusLabels,
  pageOf, subledgerAccountNames, subledgerEntityIdError, subledgerErrorTargets, subledgerFilters, subledgerOwnerTypes,
} from './finance';
import type { Currency, OwnerAccount, SubledgerScope } from './finance';
import { Amount, LoadFailure, SideTag, StatusTag } from './FinanceParts';

type Query = { paging: PagingRequestBody; filters: Readonly<Record<string, string>> };
const entityTypeOptions = ledgerEntityTypes.map(value => ({ value, label: value, title: entityTypeLabels[value] }));
const options = (values: readonly string[]) => values.map(value => ({ value, label: value }));
const accountCodeOptions = Object.entries(subledgerAccountNames).map(([value, name]) => ({ value, label: `${value} · ${name}` }));

/**
 * 明细账查询条件（画板 13）：主体类型 / 主体标识 / 币种必填一行，账户号 / 科目 / Owner 类型 / Owner ID / 余额桶筛选一行
 * <li>提交后整组条件写入 URL，由页面按 URL 重建账户列表；重置清空筛选但保留主体。</li>
 */
export function SubledgerSearchForm({ scope, filters, currencies, onSearch }: {
  scope: SubledgerScope | null; filters: Readonly<Record<string, string>>; currencies: readonly Currency[];
  onSearch: (filters: Record<string, string>) => void;
}) {
  const [form] = Form.useForm();
  const reset = () => {
    form.setFieldsValue({ accountNo: undefined, accountCode: undefined, ownerType: undefined, ownerId: undefined, balanceBucket: undefined });
    if (scope) onSearch(subledgerFilters(form.getFieldsValue()));
  };
  return <Form form={form} name="finance-subledger-filters" layout="vertical" initialValues={{ entityType: 'OPERATING_ENTITY', ...scope, ...filters }}
    onFinish={values => onSearch(subledgerFilters(values))}>
    <div className="finance-search finance-subledger-scope">
      <Form.Item name="entityType" label="主体类型" rules={[{ required: true, message: '请选择主体类型' }]}><Select options={entityTypeOptions} /></Form.Item>
      <Form.Item name="entityId" label="主体标识" dependencies={['entityType']} normalize={(value: string) => value?.toUpperCase()}
        rules={[({ getFieldValue }) => ({ validator: async (_, value) => {
          const message = subledgerEntityIdError(getFieldValue('entityType'), value);
          if (message) throw new Error(message);
        } })]}><Input maxLength={19} placeholder="entityNo 或财务主体主键" /></Form.Item>
      <Form.Item name="currency" label="币种" rules={[{ required: true, message: '请选择币种' }]}>
        <Select placeholder="启用币种" options={activeOnly(currencies).map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
    </div>
    <div className="finance-search">
      <Form.Item name="accountNo" label="账户号" normalize={(value: string) => value?.toUpperCase()}
        rules={[{ pattern: ownerAccountNoPattern, message: '大写字母、数字与连字符，如 AE001-USD-22010101-S000001' }]}>
        <Input allowClear maxLength={48} placeholder="精确匹配" /></Form.Item>
      <Form.Item name="accountCode" label="科目"><Select allowClear placeholder="全部" options={accountCodeOptions} /></Form.Item>
      <Form.Item name="ownerType" label="Owner 类型"><Select allowClear placeholder="全部" options={options(subledgerOwnerTypes)} /></Form.Item>
      <Form.Item name="ownerId" label="Owner ID" rules={[{ pattern: /^[1-9][0-9]{0,18}$/, message: '必须是正十进制整数字符串' }]}>
        <Input allowClear maxLength={19} placeholder="请输入标识" /></Form.Item>
      <Form.Item name="balanceBucket" label="余额桶"><Select allowClear placeholder="全部" options={options(balanceBuckets)} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={reset}>重置</Button></div>
    </div>
  </Form>;
}

/** Owner 账户列表（画板 13）：科目、Owner、余额桶、方向、状态、借贷合计与净余额；金额按币种精度右对齐，点击行进入流水。 */
export function SubledgerAccountList({ scope, filters, paging, minorUnit, onQueryChange, onSelect }: {
  scope: SubledgerScope; filters: Readonly<Record<string, string>>; paging: PagingRequestBody; minorUnit?: number;
  onQueryChange: (query: Query) => void; onSelect: (account: OwnerAccount) => void;
}) {
  const decode = useMemo(() => pageOf(decodeOwnerAccount(scope.currency)), [scope.currency]);
  const page = usePaging<OwnerAccount>(financePaths.ownerAccounts, decode, { ...scope, ...filters },
    { initialPaging: paging, onQueryChange });
  const amount = (value: OwnerAccount['netBalance']) => <Amount value={value.amount} minorUnit={minorUnit} />;
  const hint = financeErrorTarget(page.failure, [], subledgerErrorTargets)?.message;
  if (page.error) return <LoadFailure message={hint ?? page.error} onRetry={page.refresh} />;
  return <section className="table-panel">
    <Table<OwnerAccount> rowKey="accountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1340 }}
      pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} onRow={row => ({ onClick: () => onSelect(row), className: 'clickable-row' })}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={Object.keys(filters).length ? '没有符合条件的 Owner 账户' : '该主体与币种下还没有 Owner 账户'} /> }} columns={[
        { title: '账户号', width: 240, render: (_, row) => <span className="mono">{row.accountNo}</span> },
        { title: '科目', width: 240, render: (_, row) => <div><div className="finance-code">{row.accountCode}</div>
          {subledgerAccountNames[row.accountCode] && <div className="cell-meta">{subledgerAccountNames[row.accountCode]}</div>}</div> },
        { title: 'Owner', width: 200, render: (_, row) => <div><div>{row.ownerType}</div><div className="cell-meta">{row.ownerId}</div></div> },
        { title: '余额桶', dataIndex: 'balanceBucket', width: 110 },
        { title: '方向', width: 70, render: (_, row) => <SideTag side={row.normalSide} /> },
        { title: '状态', width: 80, render: (_, row) => <StatusTag status={row.accountStatus} labels={ownerAccountStatusLabels} /> },
        { title: '借方合计', width: 140, align: 'right', render: (_, row) => amount(row.debitTotal) },
        { title: '贷方合计', width: 140, align: 'right', render: (_, row) => amount(row.creditTotal) },
        { title: '净余额', width: 160, align: 'right', render: (_, row) => amount(row.netBalance) },
      ]} />
  </section>;
}
