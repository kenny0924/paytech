import { useMemo, useState } from 'react';
import { App, Button, Empty, Form, Input, Select, Table, Tag } from 'antd';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { usePaging } from '../hooks/usePaging';
import { DeliveryDetailDrawer } from './DeliveryDetailDrawer';
import { ReplayModal } from './WebhookModals';
import {
  decodeDeliverySummary, defaultDeliveryRange, deliveryFilters, deliveryStatusColors, deliveryStatusLabels, eventTypes, hostAndPath,
  isReplayable, pageOf, webhookAdminPath,
} from './webhook';
import type { DeliveryFilterValues, DeliverySummary, Endpoint } from './webhook';

const date = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
const deliveryPage = pageOf(decodeDeliverySummary);
const statusOptions = Object.entries(deliveryStatusLabels).map(([value, label]) => ({ value, label }));
const typeOptions = eventTypes.map(type => ({ value: type, label: type }));

/** 投递记录：默认最近 24 小时，按端点、事件类型、状态、eventId 筛选；详情抽屉；已结束的投递可 Replay。 */
export function DeliveriesTab({ clientId, endpoints, canManage, onAuthorizationChanged }: {
  clientId: string; endpoints: Endpoint[]; canManage: boolean; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<DeliveryFilterValues>();
  const [initialRange] = useState(() => defaultDeliveryRange());
  const [initial] = useState(() => deliveryFilters(clientId, { timeRange: initialRange }));
  const page = usePaging<DeliverySummary>(`${webhookAdminPath}/deliveries`, deliveryPage, initial, { initialPaging: { page: 1, size: 20 } });
  const [opened, setOpened] = useState<string | null>(null); const [replaying, setReplaying] = useState<DeliverySummary | null>(null);
  const urls = useMemo(() => new Map(endpoints.map(endpoint => [endpoint.endpointId, endpoint.url])), [endpoints]);
  function search(values: DeliveryFilterValues) {
    if (values.timeRange) form.setFieldValue('timeRange', freezeTimeRange(values.timeRange));
    page.applyFilters(deliveryFilters(clientId, values));
  }
  function reset() {
    const timeRange = defaultDeliveryRange();
    form.resetFields(); form.setFieldValue('timeRange', timeRange);
    page.applyFilters(deliveryFilters(clientId, { timeRange }));
  }
  function replayed(deliveryId: string) {
    setReplaying(null); setOpened(null); page.refresh();
    void message.success(`已生成新投递 #${deliveryId}`);
  }
  return <>
    <Form form={form} layout="vertical" onFinish={search} initialValues={{ timeRange: initialRange }}><div className="webhook-search">
      <Form.Item name="timeRange" label="创建时间" className="webhook-time" validateTrigger="onSubmit"
        rules={[{ validator: async (_, value) => { deliveryFilters(clientId, { timeRange: value }); } }]}><TimeRangePicker /></Form.Item>
      <Form.Item name="endpointId" label="端点"><Select allowClear placeholder="全部端点"
        options={endpoints.map(endpoint => ({ value: endpoint.endpointId, label: hostAndPath(endpoint.url) }))} /></Form.Item>
      <Form.Item name="eventType" label="事件类型"><Select allowClear placeholder="全部" options={typeOptions} /></Form.Item>
      <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={statusOptions} /></Form.Item>
      <Form.Item name="eventId" label="Event ID"><Input allowClear maxLength={64} placeholder="输入完整 eventId" /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={reset}>重置</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>共 {page.data.total} 条记录 · 时间跨度最多 31 天</span>
      <Button onClick={page.refresh} loading={page.loading}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<DeliverySummary> rowKey="deliveryId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading}
        scroll={{ x: 1000 }} pagination={page.pagination}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该时间范围内没有投递" /> }} columns={[
          { title: '创建时间', width: 170, render: (_, row) => date(row.createTime) },
          { title: 'Event ID / 事件类型', width: 260, render: (_, row) => <div>
            <a className="mono" onClick={() => setOpened(row.deliveryId)}>{row.eventId}</a>
            <div className="cell-meta">{row.eventType}{row.replayOfDeliveryId && <span> · 重发自 #{row.replayOfDeliveryId}</span>}</div></div> },
          { title: '端点', ellipsis: true, render: (_, row) => urls.has(row.endpointId) ? hostAndPath(urls.get(row.endpointId)!)
            : <span className="mono">{row.endpointId}</span> },
          { title: '状态', width: 100, render: (_, row) => <Tag color={deliveryStatusColors[row.status]}>{deliveryStatusLabels[row.status]}</Tag> },
          { title: '尝试', dataIndex: 'attemptCount', width: 70 },
          { title: '最近尝试', width: 170, render: (_, row) => date(row.lastAttemptTime) },
          ...(canManage ? [{ title: '操作', width: 90, render: (_: unknown, row: DeliverySummary) => isReplayable(row.status)
            ? <Button size="small" type="link" onClick={() => setReplaying(row)}>Replay</Button> : <span className="muted">—</span> }] : []),
        ]} /></section>
    <div className="webhook-note">点 Event ID 查看详情。已送达、已放弃、已取消的投递可 Replay：生成新的投递，Event ID 与内容不变，使用当前地址和密钥。测试事件不进入此列表。</div>
    {opened && <DeliveryDetailDrawer deliveryId={opened} canManage={canManage}
      endpointUrl={urls.get(page.data.items.find(row => row.deliveryId === opened)?.endpointId ?? '')}
      onReplay={setReplaying} onClose={() => setOpened(null)} />}
    {replaying && <ReplayModal delivery={replaying} onClose={() => setReplaying(null)} onReplayed={replayed} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}
