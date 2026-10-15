import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Form, Input, Select, Spin, Table, Tag } from 'antd';
import { session } from '../api/session';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { usePaging } from '../hooks/usePaging';
import {
  attemptStatusColors, attemptStatusLabels, attentionStatus, decodeRequestDetail, decodeRequestSummary, deliveryStatusColors,
  deliveryStatusLabels, notificationAdminPath, pageOf, recordFilters, resolutionSourceLabels,
} from '../notification/notification';
import type { Delivery, RecordFilterValues, RequestDetail, RequestSummary } from '../notification/notification';
import '../configuration/configuration.css';
import '../notification/notification.css';

const date = (value: string | null) => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';
const requestPage = pageOf(decodeRequestSummary);
const deliveryTag = (status: string | null) => status
  ? <Tag color={deliveryStatusColors[status] ?? 'default'}>{deliveryStatusLabels[status] ?? status}</Tag> : <span className="muted">—</span>;

/** 发送记录：默认最近 7 天，按来源、模板、状态筛选；详情抽屉逐个查看投递与尝试，不显示邮箱与参数值。 */
export function NotificationRecords() {
  const [form] = Form.useForm<RecordFilterValues>();
  const [initial] = useState(() => recordFilters({}));
  const page = usePaging<RequestSummary>(`${notificationAdminPath}/requests`, requestPage, initial, { initialPaging: { page: 1, size: 20 } });
  const [opened, setOpened] = useState<string | null>(null);
  function search(values: RecordFilterValues) {
    if (values.timeRange) form.setFieldValue('timeRange', freezeTimeRange(values.timeRange));
    page.applyFilters(recordFilters(values));
  }
  return <div className="configuration-page notification-page"><div className="page-heading"><h1>发送记录</h1></div>
    <Form form={form} layout="vertical" onFinish={search} initialValues={{ timeRange: { preset: 'LAST_7_DAYS' } }}><div className="notification-search">
      <Form.Item name="timeRange" label="创建时间" className="notification-time" validateTrigger="onSubmit" rules={[{ validator: async (_, value) => { recordFilters({ timeRange: value }); } }]}><TimeRangePicker /></Form.Item>
      <Form.Item name="sourceSystem" label="来源系统"><Input allowClear maxLength={64} placeholder="精确匹配" /></Form.Item>
      <Form.Item name="sourceEventKey" label="来源事件键"><Input allowClear maxLength={128} placeholder="精确匹配" /></Form.Item>
      <Form.Item name="templateCode" label="模板编码"><Input allowClear maxLength={64} placeholder="精确匹配" /></Form.Item>
      <Form.Item name="deliveryStatus" label="投递状态"><Select allowClear placeholder="全部" options={Object.entries(deliveryStatusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button>
        <Button onClick={() => { form.resetFields(); page.applyFilters(recordFilters({})); }}>重置</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>共 {page.data.total} 条记录 · 时间跨度最多 31 天</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<RequestSummary> rowKey="requestId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={page.pagination} onRow={row => ({ onClick: () => setOpened(row.requestId), className: 'clickable-row' })} columns={[
          { title: '时间', width: 170, render: (_, row) => date(row.createTime) },
          { title: '来源 / 事件键', width: 300, render: (_, row) => <div><div>{row.sourceSystem}</div><div className="cell-meta">{row.sourceEventKey}</div></div> },
          { title: '模板', width: 260, ellipsis: true, render: (_, row) => <span className="mono">{row.templateCode ?? '—'}</span> },
          { title: '收件人', dataIndex: 'deliveryCount', width: 80 },
          { title: '状态', width: 110, render: (_, row) => deliveryTag(attentionStatus(row.statusCounts)) },
          { title: '请求 ID', width: 180, render: (_, row) => <a className="mono">{row.requestId}</a> },
        ]} /></section>
    <div className="notification-note">不显示收件邮箱和参数值；认证邮件（即时发送）不留发送记录，不会出现在此列表。多个收件人状态不同时，标签显示最需要关注的状态。</div>
    {opened && <RecordDetail requestId={opened} onClose={() => setOpened(null)} />}
  </div>;
}

/** 记录详情抽屉。 */
function RecordDetail({ requestId, onClose }: { requestId: string; onClose: () => void }) {
  const notify = useErrorNotification(); const [detail, setDetail] = useState<RequestDetail>(); const [failed, setFailed] = useState(false);
  useEffect(() => {
    const abort = new AbortController();
    session.request(`${notificationAdminPath}/requests/${requestId}`, { signal: abort.signal }, value => decodeRequestDetail(value, requestId))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, '记录详情加载失败'); } });
    return () => abort.abort();
  }, [requestId, notify]);
  return <Drawer title={`发送记录 ${requestId}`} open size={600} onClose={onClose} destroyOnHidden>
    {failed ? <Alert type="warning" showIcon title="记录详情暂时无法加载" /> : !detail ? <Spin /> : <>
      <Descriptions column={1} size="small" items={[
        { key: 'source', label: '来源系统', children: detail.sourceSystem },
        { key: 'event', label: '来源事件键', children: detail.sourceEventKey },
        { key: 'template', label: '模板', children: detail.templateCode ?? '—' },
        { key: 'locale', label: '默认语言', children: detail.defaultLocale },
        { key: 'params', label: '参数名', children: detail.parameterKeys.length ? detail.parameterKeys.join(' · ') : '—' },
        { key: 'time', label: '创建时间', children: date(detail.createTime) },
      ]} />
      <h3 className="notification-subtitle">投递（{detail.deliveries.length}）</h3>
      {detail.deliveries.map((delivery, index) => <DeliveryCard key={delivery.deliveryId} index={index + 1} delivery={delivery} />)}
      <div className="muted">收件邮箱、参数值和供应商流水号不在运营后台显示。</div>
    </>}
  </Drawer>;
}

/** 一个收件人的投递卡片，尝试按先后列出。 */
function DeliveryCard({ index, delivery }: { index: number; delivery: Delivery }) {
  const meta = [`语言 ${delivery.locale ?? '继承默认'}`, delivery.parameterKeys.length ? `参数名 ${delivery.parameterKeys.join('、')}` : null,
    `尝试 ${delivery.attemptCount} 次`, delivery.nextRetryTime ? `下次重试 ${date(delivery.nextRetryTime)}` : null, `更新 ${date(delivery.updateTime)}`];
  return <div className="notification-delivery"><div className="notification-delivery-head"><strong>投递 {index}</strong>{deliveryTag(delivery.status)}</div>
    <div className="cell-meta">{meta.filter(Boolean).join(' · ')}</div>
    {delivery.attempts.map((attempt, i) => <div key={attempt.attemptId} className="notification-attempt">
      <span className="muted">#{i + 1} {date(attempt.startedTime)}</span><span className="mono">{attempt.providerCode ?? '—'}</span>
      <Tag color={attemptStatusColors[attempt.status] ?? 'default'}>{attemptStatusLabels[attempt.status] ?? attempt.status}</Tag>
      <span className="muted">响应码 {attempt.responseCode ?? '—'}{attempt.resolutionSource ? ` · 来源 ${resolutionSourceLabels[attempt.resolutionSource] ?? attempt.resolutionSource}` : ''}
        {attempt.resolutionCode ? ` · 确认 ${attempt.resolutionCode}` : ''}</span>
    </div>)}
  </div>;
}
