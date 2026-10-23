import { useEffect, useMemo } from 'react';
import { Button, Empty, Form, Select, Table } from 'antd';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { decodeFinanceEntity, financeEntityStatuses, financePaths, localTime, pageOf } from './finance';
import type { FinanceEntity } from './finance';
import { FinanceNote, LoadFailure, StatusTag } from './FinanceParts';

const entityPage = pageOf(decodeFinanceEntity);

/** 财务主体列表：按状态筛选、分页；新建按钮在标签栏右侧（仅维护权限）。reloadToken 变化即刷新。 */
export function SetupEntitiesTab({ reloadToken }: { reloadToken: number }) {
  const t = useT('finance');
  const [form] = Form.useForm<{ status: string }>();
  const statusOptions = useMemo(() => [{ value: '', label: t('placeholder.all') },
    ...financeEntityStatuses.map(value => ({ value, label: enumLabel(t, 'enum.financeEntityStatus', value) }))], [t]);
  const page = usePaging<FinanceEntity>(financePaths.financeEntities, entityPage);
  useEffect(() => { if (reloadToken) page.refresh(); }, [reloadToken]);
  return <>
    <Form form={form} name="finance-entity-filters" layout="vertical" initialValues={{ status: '' }} onFinish={values => page.applyFilters(values.status ? { status: values.status } : {})}>
      <div className="finance-search">
        <Form.Item name="status" label={t('field.status')}><Select options={statusOptions} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<FinanceEntity> rowKey="financeEntityId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 880 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('entities.empty')} /> }} columns={[
          { title: t('charts.column.code'), dataIndex: 'entityCode', width: 180 },
          { title: t('entities.column.displayName'), dataIndex: 'displayName', ellipsis: true },
          { title: t('entities.column.legalPartyId'), width: 200, render: (_, row) => <span className="mono">{row.legalPartyId}</span> },
          { title: t('field.status'), width: 100, render: (_, row) => <StatusTag status={row.status} enumType="financeEntityStatus" /> },
          { title: t('field.createdAt'), width: 160, render: (_, row) => localTime(row.createTime) },
        ]} />
    </section>}
    <FinanceNote>{t('entities.note')}</FinanceNote>
  </>;
}
