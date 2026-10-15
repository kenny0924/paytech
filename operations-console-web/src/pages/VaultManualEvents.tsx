import { Button, Form, Input, Select, Table, Tag } from 'antd';
import { usePaging } from '../hooks/usePaging';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { actionLabels, manualEventFilters, outcomeLabels, reasonCodeLabels, vaultAdminPath } from '../vault/vault';
import type { ManualEventFilterValues, VaultManualEvent } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const outcomeColor: Record<string, string> = { SUCCESS: 'green', DENIED: 'orange', FAILED: 'red' };

/** 人工操作记录：按操作人、动作、数据类型和时间筛选；只显示审计元数据，不含 Token 与原值。 */
export function VaultManualEvents() {
  const [form] = Form.useForm<ManualEventFilterValues>(); const page = usePaging<VaultManualEvent>(`${vaultAdminPath}/manual-events`);
  function search(values: ManualEventFilterValues) {
    if (values.timeRange) form.setFieldValue('timeRange', freezeTimeRange(values.timeRange));
    page.applyFilters(manualEventFilters(values));
  }
  return <div className="configuration-page vault-page"><div className="page-heading"><h1>人工操作记录</h1></div>
    <Form form={form} layout="vertical" onFinish={search}><div className="vault-search">
      <Form.Item name="actorId" label="操作人"><Input allowClear maxLength={64} placeholder="运营账号 ID" /></Form.Item>
      <Form.Item name="action" label="动作"><Select allowClear placeholder="全部" options={Object.entries(actionLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="dataTypeCode" label="数据类型"><Input allowClear maxLength={64} placeholder="数据类型编码" /></Form.Item>
      <Form.Item name="timeRange" label="时间范围" validateTrigger="onSubmit"
        rules={[{ validator: async (_, value) => { manualEventFilters({ timeRange: value }); } }]}><TimeRangePicker /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>共 {page.data.total} 条记录</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<VaultManualEvent> rowKey="requestId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1190 }} pagination={page.pagination} columns={[
        { title: '时间', dataIndex: 'createTime', width: 190, render: date },
        { title: '操作人', dataIndex: 'actorId', width: 190, ellipsis: true },
        { title: '动作', width: 80, render: (_, row) => actionLabels[row.action] },
        { title: '数据类型', width: 220, ellipsis: true, render: (_, row) => row.dataTypeCode || '—' },
        { title: '理由', width: 130, render: (_, row) => reasonCodeLabels[row.reasonCode] ?? row.reasonCode },
        { title: '工单号', dataIndex: 'ticketRef', width: 140, ellipsis: true },
        { title: '结果', width: 200, render: (_, row) => <><Tag color={outcomeColor[row.outcome]}>{outcomeLabels[row.outcome] ?? row.outcome}</Tag>
          {row.outcome !== 'SUCCESS' && <span className="cell-meta">{row.resultCode}</span>}</> },
      ]} /></section>
  </div>;
}
