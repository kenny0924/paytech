import { useEffect } from 'react';
import { Button, Empty, Form, Select, Table } from 'antd';
import { usePaging } from '../hooks/usePaging';
import { decodeFinanceEntity, financeEntityStatusLabels, financePaths, localTime, pageOf } from './finance';
import type { FinanceEntity } from './finance';
import { FinanceNote, LoadFailure, StatusTag } from './FinanceParts';

const entityPage = pageOf(decodeFinanceEntity);
const statusOptions = [{ value: '', label: '全部' }, ...Object.entries(financeEntityStatusLabels).map(([value, label]) => ({ value, label }))];

/** 财务主体列表：按状态筛选、分页；新建按钮在标签栏右侧（仅维护权限）。reloadToken 变化即刷新。 */
export function SetupEntitiesTab({ reloadToken }: { reloadToken: number }) {
  const [form] = Form.useForm<{ status: string }>();
  const page = usePaging<FinanceEntity>(financePaths.financeEntities, entityPage);
  useEffect(() => { if (reloadToken) page.refresh(); }, [reloadToken]);
  return <>
    <Form form={form} name="finance-entity-filters" layout="vertical" initialValues={{ status: '' }} onFinish={values => page.applyFilters(values.status ? { status: values.status } : {})}>
      <div className="finance-search">
        <Form.Item name="status" label="状态"><Select options={statusOptions} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<FinanceEntity> rowKey="financeEntityId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 880 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有财务主体" /> }} columns={[
          { title: '代码', dataIndex: 'entityCode', width: 180 },
          { title: '显示名称', dataIndex: 'displayName', ellipsis: true },
          { title: '法律 Party ID', width: 200, render: (_, row) => <span className="mono">{row.legalPartyId}</span> },
          { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.status} labels={financeEntityStatusLabels} /> },
          { title: '创建时间', width: 160, render: (_, row) => localTime(row.createTime) },
        ]} />
    </section>}
    <FinanceNote>财务主体是 Finance 内部核算身份，只能新建、不能修改或关闭；法律 Party ID 来自 CCT，本期手工输入并校验为正十进制。无维护权限的账号看不到「新建财务主体」按钮。</FinanceNote>
  </>;
}
