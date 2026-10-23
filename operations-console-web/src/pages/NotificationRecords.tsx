import { useEffect, useMemo, useState } from 'react';
import { Alert, Button, Descriptions, Drawer, Form, Input, Select, Spin, Table, Tag } from 'antd';
import { session } from '../api/session';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { usePaging } from '../hooks/usePaging';
import { enumLabel, formatDateTime } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  attemptStatusColors, attentionStatus, decodeRequestDetail, decodeRequestSummary, deliveryStatusColors, deliveryStatuses,
  eventOutcomeColors, joinList, maxRecordDays, notificationAdminPath, pageOf, recordFilters,
} from '../notification/notification';
import type { Attempt, Delivery, RecordFilterValues, RequestDetail, RequestSummary } from '../notification/notification';
import '../configuration/configuration.css';
import '../notification/notification.css';

const requestPage = pageOf(decodeRequestSummary);
const deliveryTag = (t: Translate, status: string | null) => status
  ? <Tag color={deliveryStatusColors[status] ?? 'default'}>{enumLabel(t, 'enum.deliveryStatus', status)}</Tag> : <span className="muted">—</span>;

/** 发送记录：默认最近 7 天，按来源、模板、状态筛选；详情抽屉逐个查看投递与尝试，不显示邮箱与参数值。 */
export function NotificationRecords() {
  const t = useT('notification'), tCommon = useT('common'), tNav = useT('nav');
  const statusOptions = useMemo(() => deliveryStatuses.map(value => ({ value, label: enumLabel(t, 'enum.deliveryStatus', value) })), [t]);
  const [form] = Form.useForm<RecordFilterValues>();
  const [initial] = useState(() => recordFilters({}));
  const page = usePaging<RequestSummary>(`${notificationAdminPath}/requests`, requestPage, initial, { initialPaging: { page: 1, size: 20 } });
  const [opened, setOpened] = useState<string | null>(null);
  function search(values: RecordFilterValues) {
    if (values.timeRange) form.setFieldValue('timeRange', freezeTimeRange(values.timeRange));
    page.applyFilters(recordFilters(values));
  }
  return <div className="configuration-page notification-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_NOTIFICATION_RECORDS')}</h1></div>
    <Form form={form} layout="vertical" onFinish={search} initialValues={{ timeRange: { preset: 'LAST_7_DAYS' } }}><div className="notification-search">
      <Form.Item name="timeRange" label={t('records.field.createdAt')} className="notification-time" validateTrigger="onSubmit" rules={[{ validator: async (_, value) => { recordFilters({ timeRange: value }); } }]}><TimeRangePicker /></Form.Item>
      <Form.Item name="sourceSystem" label={t('records.field.sourceSystem')}><Input allowClear maxLength={64} placeholder={t('records.field.exactMatch')} /></Form.Item>
      <Form.Item name="sourceEventKey" label={t('records.field.sourceEventKey')}><Input allowClear maxLength={128} placeholder={t('records.field.exactMatch')} /></Form.Item>
      <Form.Item name="templateCode" label={t('records.field.templateCode')}><Input allowClear maxLength={64} placeholder={t('records.field.exactMatch')} /></Form.Item>
      <Form.Item name="deliveryStatus" label={t('records.field.deliveryStatus')}><Select allowClear placeholder={t('records.field.all')} options={statusOptions} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('records.search')}</Button>
        <Button onClick={() => { form.resetFields(); page.applyFilters(recordFilters({})); }}>{t('records.reset')}</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>{t('records.count', { count: page.data.total, days: maxRecordDays })}</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
      <Table<RequestSummary> rowKey="requestId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={page.pagination} onRow={row => ({ onClick: () => setOpened(row.requestId), className: 'clickable-row' })} columns={[
          { title: t('records.column.time'), width: 170, render: (_, row) => formatDateTime(row.createTime) },
          { title: t('records.column.source'), width: 300, render: (_, row) => <div><div>{row.sourceSystem}</div><div className="cell-meta">{row.sourceEventKey}</div></div> },
          { title: t('records.column.template'), width: 260, ellipsis: true, render: (_, row) => <span className="mono">{row.templateCode ?? '—'}</span> },
          { title: t('records.column.recipients'), dataIndex: 'deliveryCount', width: 100 },
          { title: t('records.column.status'), width: 110, render: (_, row) => deliveryTag(t, attentionStatus(row.statusCounts)) },
          { title: t('records.column.requestId'), width: 180, render: (_, row) => <a className="mono">{row.requestId}</a> },
        ]} /></section>
    <div className="notification-note">{t('records.note')}</div>
    {opened && <RecordDetail requestId={opened} onClose={() => setOpened(null)} />}
  </div>;
}

/** 记录详情抽屉。 */
function RecordDetail({ requestId, onClose }: { requestId: string; onClose: () => void }) {
  const notify = useErrorNotification(); const [detail, setDetail] = useState<RequestDetail>(); const [failed, setFailed] = useState(false);
  const t = useT('notification');
  useEffect(() => {
    const abort = new AbortController();
    session.request(`${notificationAdminPath}/requests/${requestId}`, { signal: abort.signal }, value => decodeRequestDetail(value, requestId))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, t('records.detail.loadFailed')); } });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [requestId, notify]);
  return <Drawer title={t('records.detail.title', { requestId })} open size={600} onClose={onClose} destroyOnHidden>
    {failed ? <Alert type="warning" showIcon title={t('records.detail.unavailable')} /> : !detail ? <Spin /> : <>
      <Descriptions column={1} size="small" items={[
        { key: 'source', label: t('records.field.sourceSystem'), children: detail.sourceSystem },
        { key: 'event', label: t('records.field.sourceEventKey'), children: detail.sourceEventKey },
        { key: 'template', label: t('records.column.template'), children: detail.templateCode ?? '—' },
        { key: 'locale', label: t('records.detail.defaultLocale'), children: detail.defaultLocale },
        { key: 'params', label: t('records.detail.parameterKeys'), children: detail.parameterKeys.length ? detail.parameterKeys.join(' · ') : '—' },
        { key: 'time', label: t('records.field.createdAt'), children: formatDateTime(detail.createTime) },
      ]} />
      <h3 className="notification-subtitle">{t('records.detail.deliveries', { count: detail.deliveries.length })}</h3>
      {detail.deliveries.map((delivery, index) => <DeliveryCard key={delivery.deliveryId} index={index + 1} delivery={delivery} />)}
      <div className="muted">{t('records.detail.privacyNote')}</div>
    </>}
  </Drawer>;
}

/** 尝试的投递事件结果与投诉；没有事件时不显示。 */
function AttemptEvents({ attempt }: { attempt: Attempt }) {
  const t = useT('notification');
  if (!attempt.eventOutcome && !attempt.complaintCode) return null;
  return <div className="notification-attempt-events">
    {attempt.eventOutcome && <span>{t('records.attempt.eventOutcome')} <Tag color={eventOutcomeColors[attempt.eventOutcome] ?? 'default'}>{enumLabel(t, 'enum.eventOutcome', attempt.eventOutcome)}</Tag>
      {attempt.eventOutcomeCode && <span className="mono">{attempt.eventOutcomeCode} </span>}
      {attempt.eventOutcomeTime && <span className="muted">{formatDateTime(attempt.eventOutcomeTime)}</span>}</span>}
    {attempt.complaintCode && <span><Tag color="red">{t('records.attempt.complained')}</Tag><span className="mono">{attempt.complaintCode} </span>
      {attempt.complaintTime && <span className="muted">{formatDateTime(attempt.complaintTime)}</span>}</span>}
  </div>;
}

/** 一个收件人的投递卡片，尝试按先后列出。 */
function DeliveryCard({ index, delivery }: { index: number; delivery: Delivery }) {
  const t = useT('notification');
  const meta = [delivery.locale ? t('records.delivery.locale', { locale: delivery.locale }) : t('records.delivery.localeInherited'),
    delivery.parameterKeys.length ? t('records.delivery.parameterKeys', { keys: joinList(delivery.parameterKeys) }) : null,
    t('records.delivery.attempts', { count: delivery.attemptCount }),
    delivery.nextRetryTime ? t('records.delivery.nextRetry', { time: formatDateTime(delivery.nextRetryTime) }) : null,
    t('records.delivery.updated', { time: formatDateTime(delivery.updateTime) })];
  return <div className="notification-delivery"><div className="notification-delivery-head"><strong>{t('records.delivery.title', { index })}</strong>{deliveryTag(t, delivery.status)}</div>
    <div className="cell-meta">{meta.filter(Boolean).join(' · ')}</div>
    {delivery.attempts.map((attempt, i) => <div key={attempt.attemptId} className="notification-attempt">
      <span className="muted">#{i + 1} {formatDateTime(attempt.startedTime)}</span><span className="mono">{attempt.providerCode ?? '—'}</span>
      <Tag color={attemptStatusColors[attempt.status] ?? 'default'}>{enumLabel(t, 'enum.attemptStatus', attempt.status)}</Tag>
      <span className="muted">{t('records.attempt.responseCode', { code: attempt.responseCode ?? '—' })}
        {attempt.resolutionSource ? ' · ' + t('records.attempt.resolutionSource', { source: enumLabel(t, 'enum.resolutionSource', attempt.resolutionSource) }) : ''}
        {attempt.resolutionCode ? ' · ' + t('records.attempt.resolutionCode', { code: attempt.resolutionCode }) : ''}</span>
      <AttemptEvents attempt={attempt} />
    </div>)}
  </div>;
}
