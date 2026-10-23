import { useMemo } from 'react';
import { Button, Empty, Form, Input, Select, Table } from 'antd';
import type { PagingRequestBody } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  activeOnly, balanceBuckets, decodeOwnerAccount, financeErrorTarget, financePaths, ledgerEntityTypes, ownerAccountNoPattern,
  pageOf, subledgerAccountCodes, subledgerAccountName, subledgerEntityIdError, subledgerErrorTargets, subledgerFilters, subledgerOwnerTypes,
} from './finance';
import type { Currency, OwnerAccount, SubledgerScope } from './finance';
import { Amount, LoadFailure, SideTag, StatusTag } from './FinanceParts';

type Query = { paging: PagingRequestBody; filters: Readonly<Record<string, string>> };
const options = (values: readonly string[]) => values.map(value => ({ value, label: value }));

/**
 * 明细账查询条件（画板 13）：主体类型 / 主体标识 / 币种必填一行，账户号 / 科目 / Owner 类型 / Owner ID / 余额桶筛选一行
 * <li>提交后整组条件写入 URL，由页面按 URL 重建账户列表；重置清空筛选但保留主体。</li>
 */
export function SubledgerSearchForm({ scope, filters, currencies, onSearch }: {
  scope: SubledgerScope | null; filters: Readonly<Record<string, string>>; currencies: readonly Currency[];
  onSearch: (filters: Record<string, string>) => void;
}) {
  const t = useT('finance');
  const [form] = Form.useForm();
  const entityTypeOptions = useMemo(() => ledgerEntityTypes.map(value => ({ value, label: value, title: enumLabel(t, 'enum.ledgerEntityType', value) })), [t]);
  const accountCodeOptions = useMemo(() => subledgerAccountCodes.map(value => ({ value, label: `${value} · ${enumLabel(t, 'enum.subledgerAccount', value)}` })), [t]);
  const reset = () => {
    form.setFieldsValue({ accountNo: undefined, accountCode: undefined, ownerType: undefined, ownerId: undefined, balanceBucket: undefined });
    if (scope) onSearch(subledgerFilters(form.getFieldsValue()));
  };
  return <Form form={form} name="finance-subledger-filters" layout="vertical" initialValues={{ entityType: 'OPERATING_ENTITY', ...scope, ...filters }}
    onFinish={values => onSearch(subledgerFilters(values))}>
    <div className="finance-search finance-subledger-scope">
      <Form.Item name="entityType" label={t('field.entityType')} rules={[{ required: true, message: t('validation.entityTypeRequired') }]}><Select options={entityTypeOptions} /></Form.Item>
      <Form.Item name="entityId" label={t('field.entityId')} dependencies={['entityType']} normalize={(value: string) => value?.toUpperCase()}
        rules={[({ getFieldValue }) => ({ validator: async (_, value) => {
          const message = subledgerEntityIdError(getFieldValue('entityType'), value);
          if (message) throw new Error(message);
        } })]}><Input maxLength={19} placeholder={t('subledger.search.entityIdPlaceholder')} /></Form.Item>
      <Form.Item name="currency" label={t('field.currency')} rules={[{ required: true, message: t('validation.currencyRequired') }]}>
        <Select placeholder={t('placeholder.activeCurrency')} options={activeOnly(currencies).map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
    </div>
    <div className="finance-search">
      <Form.Item name="accountNo" label={t('field.accountNo')} normalize={(value: string) => value?.toUpperCase()}
        rules={[{ pattern: ownerAccountNoPattern, message: t('validation.ownerAccountNoPattern') }]}>
        <Input allowClear maxLength={48} placeholder={t('placeholder.exactMatch')} /></Form.Item>
      <Form.Item name="accountCode" label={t('field.accountCode')}><Select allowClear placeholder={t('placeholder.all')} options={accountCodeOptions} /></Form.Item>
      <Form.Item name="ownerType" label={t('field.ownerType')}><Select allowClear placeholder={t('placeholder.all')} options={options(subledgerOwnerTypes)} /></Form.Item>
      <Form.Item name="ownerId" label={t('field.ownerId')} rules={[{ pattern: /^[1-9][0-9]{0,18}$/, message: t('validation.decimalId') }]}>
        <Input allowClear maxLength={19} placeholder={t('placeholder.enterId')} /></Form.Item>
      <Form.Item name="balanceBucket" label={t('field.balanceBucket')}><Select allowClear placeholder={t('placeholder.all')} options={options(balanceBuckets)} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('common.search')}</Button><Button onClick={reset}>{t('common.reset')}</Button></div>
    </div>
  </Form>;
}

/** Owner 账户列表（画板 13）：科目、Owner、余额桶、方向、状态、借贷合计与净余额；金额按币种精度右对齐，点击行进入流水。 */
export function SubledgerAccountList({ scope, filters, paging, minorUnit, onQueryChange, onSelect }: {
  scope: SubledgerScope; filters: Readonly<Record<string, string>>; paging: PagingRequestBody; minorUnit?: number;
  onQueryChange: (query: Query) => void; onSelect: (account: OwnerAccount) => void;
}) {
  const t = useT('finance');
  const decode = useMemo(() => pageOf(decodeOwnerAccount(scope.currency)), [scope.currency]);
  const page = usePaging<OwnerAccount>(financePaths.ownerAccounts, decode, { ...scope, ...filters },
    { initialPaging: paging, onQueryChange });
  const amount = (value: OwnerAccount['netBalance']) => <Amount value={value.amount} minorUnit={minorUnit} />;
  const hint = financeErrorTarget(page.failure, [], subledgerErrorTargets)?.message;
  if (page.error) return <LoadFailure message={hint ?? page.error} onRetry={page.refresh} />;
  return <section className="table-panel">
    <Table<OwnerAccount> rowKey="accountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1340 }}
      pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }} onRow={row => ({ onClick: () => onSelect(row), className: 'clickable-row' })}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
        description={Object.keys(filters).length ? t('subledger.accounts.emptyFiltered') : t('subledger.accounts.empty')} /> }} columns={[
        { title: t('field.accountNo'), width: 240, render: (_, row) => <span className="mono">{row.accountNo}</span> },
        { title: t('field.accountCode'), width: 240, render: (_, row) => { const name = subledgerAccountName(row.accountCode);
          return <div><div className="finance-code">{row.accountCode}</div>{name && <div className="cell-meta">{name}</div>}</div>; } },
        { title: t('field.owner'), width: 200, render: (_, row) => <div><div>{row.ownerType}</div><div className="cell-meta">{row.ownerId}</div></div> },
        { title: t('field.balanceBucket'), dataIndex: 'balanceBucket', width: 110 },
        { title: t('field.side'), width: 70, render: (_, row) => <SideTag side={row.normalSide} /> },
        { title: t('field.status'), width: 80, render: (_, row) => <StatusTag status={row.accountStatus} enumType="ownerAccountStatus" /> },
        { title: t('field.debitTotal'), width: 140, align: 'right', render: (_, row) => amount(row.debitTotal) },
        { title: t('field.creditTotal'), width: 140, align: 'right', render: (_, row) => amount(row.creditTotal) },
        { title: t('field.netBalance'), width: 160, align: 'right', render: (_, row) => amount(row.netBalance) },
      ]} />
  </section>;
}
