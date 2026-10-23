import { useMemo, useState } from 'react';
import { App, Button, Empty, Form, Input, Select, Table, Tag } from 'antd';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { usePaging } from '../hooks/usePaging';
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { DeliveryDetailDrawer } from './DeliveryDetailDrawer';
import { ReplayModal } from './WebhookModals';
import {
  decodeDeliverySummary, defaultDeliveryRange, deliveryFilters, deliveryStatusColors, deliveryStatuses, eventTypes, hostAndPath,
  isReplayable, maxDeliveryDays, pageOf, webhookAdminPath,
} from './webhook';
import type { DeliveryFilterValues, DeliverySummary, Endpoint } from './webhook';

const deliveryPage = pageOf(decodeDeliverySummary);
const typeOptions = eventTypes.map(type => ({ value: type, label: type }));

/** 投递记录：默认最近 24 小时，按端点、事件类型、状态、eventId 筛选；详情抽屉；已结束的投递可 Replay。 */
export function DeliveriesTab({ clientId, endpoints, canManage, onAuthorizationChanged }: {
  clientId: string; endpoints: Endpoint[]; canManage: boolean; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp(); const t = useT('webhook'), tCommon = useT('common');
  const statusOptions = useMemo(() => deliveryStatuses.map(value => ({ value, label: enumLabel(t, 'enum.deliveryStatus', value) })), [t]);
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
    void message.success(t('deliveries.replayed', { deliveryId }));
  }
  return <>
    <Form form={form} layout="vertical" onFinish={search} initialValues={{ timeRange: initialRange }}><div className="webhook-search">
      <Form.Item name="timeRange" label={t('deliveries.field.createdAt')} className="webhook-time" validateTrigger="onSubmit"
        rules={[{ validator: async (_, value) => { deliveryFilters(clientId, { timeRange: value }); } }]}><TimeRangePicker /></Form.Item>
      <Form.Item name="endpointId" label={t('deliveries.field.endpoint')}><Select allowClear placeholder={t('deliveries.field.allEndpoints')}
        options={endpoints.map(endpoint => ({ value: endpoint.endpointId, label: hostAndPath(endpoint.url) }))} /></Form.Item>
      <Form.Item name="eventType" label={t('deliveries.field.eventType')}><Select allowClear placeholder={t('deliveries.field.all')} options={typeOptions} /></Form.Item>
      <Form.Item name="status" label={t('deliveries.field.status')}><Select allowClear placeholder={t('deliveries.field.all')} options={statusOptions} /></Form.Item>
      <Form.Item name="eventId" label={t('deliveries.field.eventId')}><Input allowClear maxLength={64} placeholder={t('deliveries.field.eventIdPlaceholder')} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('deliveries.search')}</Button><Button onClick={reset}>{t('deliveries.reset')}</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>{t('deliveries.count', { count: page.data.total, days: maxDeliveryDays })}</span>
      <Button onClick={page.refresh} loading={page.loading}>{page.error ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
      <Table<DeliverySummary> rowKey="deliveryId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading}
        scroll={{ x: 1000 }} pagination={page.pagination}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('deliveries.empty')} /> }} columns={[
          { title: t('deliveries.field.createdAt'), width: 170, render: (_, row) => formatDateTime(row.createTime) },
          { title: t('deliveries.column.eventIdAndType'), width: 260, render: (_, row) => <div>
            <a className="mono" onClick={() => setOpened(row.deliveryId)}>{row.eventId}</a>
            <div className="cell-meta">{row.eventType}{row.replayOfDeliveryId && <span> · {t('deliveries.replayOf', { deliveryId: row.replayOfDeliveryId })}</span>}</div></div> },
          { title: t('deliveries.field.endpoint'), ellipsis: true, render: (_, row) => urls.has(row.endpointId) ? hostAndPath(urls.get(row.endpointId)!)
            : <span className="mono">{row.endpointId}</span> },
          { title: t('deliveries.field.status'), width: 100, render: (_, row) => <Tag color={deliveryStatusColors[row.status]}>{enumLabel(t, 'enum.deliveryStatus', row.status)}</Tag> },
          { title: t('deliveries.column.attempts'), dataIndex: 'attemptCount', width: 70 },
          { title: t('deliveries.column.lastAttemptAt'), width: 170, render: (_, row) => formatDateTime(row.lastAttemptTime) },
          ...(canManage ? [{ title: t('deliveries.column.actions'), width: 90, render: (_: unknown, row: DeliverySummary) => isReplayable(row.status)
            ? <Button size="small" type="link" onClick={() => setReplaying(row)}>{t('deliveries.replay')}</Button> : <span className="muted">—</span> }] : []),
        ]} /></section>
    <div className="webhook-note">{t('deliveries.note')}</div>
    {opened && <DeliveryDetailDrawer deliveryId={opened} canManage={canManage}
      endpointUrl={urls.get(page.data.items.find(row => row.deliveryId === opened)?.endpointId ?? '')}
      onReplay={setReplaying} onClose={() => setOpened(null)} />}
    {replaying && <ReplayModal delivery={replaying} onClose={() => setReplaying(null)} onReplayed={replayed} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}
