import { useEffect, useRef, useState } from 'react';
import { AutoComplete, Button, Drawer, Form, Input, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import type { AuditChange, AuditDetailResponseBody, AuditEvent } from '../api/types';
import { session } from '../api/session';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { auditActionCodes, auditFilters, auditResourceTypeCodes, auditResultCodes, auditValue, validateAuditDetail } from './auditView';
import type { AuditFilterValues } from './auditView';
import { RefreshButton } from '../components/RefreshButton';
import { enumLabel, formatDateTime, tOr } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';

function ResultTag({ result }: { result: string }) {
  const t = useT('access');
  return <Tag color={result === 'SUCCEEDED' ? 'green' : result === 'DENIED' ? 'orange' : 'red'}>{enumLabel(t, 'enum.auditResult', result)}</Tag>;
}
/** Field name of a change or attribute: `access.audit.field.<key>` when known, otherwise the raw key; the key stays visible below. */
function FieldName({ t, name }: { t: Translate; name: string }) {
  const label = tOr(t, 'audit.field.' + name, name);
  return <>{label}{label !== name && <div className="cell-meta">{name}</div>}</>;
}
function Values({ values }: { values: (string | null)[] }) {
  const t = useT('access');
  if (!values.length) return <span className="muted">{t('audit.notRecorded')}</span>;
  return <div className="audit-values">{values.map((value, index) => <div key={index} className={value === null ? 'muted' : undefined}>{auditValue(value)}</div>)}</div>;
}
function RequestId({ value }: { value: string }) {
  const t = useT('access');
  return value ? <Typography.Text className="audit-request-id" copyable={{ text: value, tooltips: [t('audit.copyRequestId'), t('audit.copied')] }}>{value}</Typography.Text> : <>—</>;
}
function Details({ detail }: { detail: AuditDetailResponseBody }) {
  const t = useT('access');
  const { event } = detail;
  return <>
    <dl className="audit-summary">
      <dt>{t('audit.detail.time')}</dt><dd>{formatDateTime(event.occurredTime)}</dd>
      <dt>{t('audit.detail.actorId')}</dt><dd>{event.actorId}</dd>
      <dt>{t('audit.detail.action')}</dt><dd>{enumLabel(t, 'enum.auditAction', event.actionCode)}<div className="cell-meta">{event.actionCode}</div></dd>
      <dt>{t('audit.detail.result')}</dt><dd><ResultTag result={event.result} /></dd>
      <dt>{t('audit.detail.object')}</dt><dd>{enumLabel(t, 'enum.auditResourceType', event.resourceType)}<div className="cell-meta">{event.resourceId}</div></dd>
      <dt>{t('audit.detail.auditEventId')}</dt><dd>{event.auditEventId}</dd>
      <dt>{t('audit.detail.requestId')}</dt><dd><RequestId value={event.requestId} /></dd>
      <dt>{t('audit.detail.reason')}</dt><dd className="audit-values">{event.reason || t('audit.notRecorded')}</dd>
    </dl>
    <section className="audit-detail-section"><h2>{t('audit.detail.changes')}</h2>
      {detail.changes.length ? <Table<AuditChange> size="small" rowKey="key" pagination={false} dataSource={detail.changes}
        scroll={{ x: 500 }} columns={[
          { title: t('audit.detail.field'), width: '30%', render: (_, change) => <div><FieldName t={t} name={change.key} /></div> },
          { title: t('audit.detail.before'), width: '35%', render: (_, change) => <Values values={change.before} /> },
          { title: t('audit.detail.after'), width: '35%', render: (_, change) => <Values values={change.after} /> },
        ]} /> : <p className="muted">{t('audit.detail.noChanges')}</p>}
    </section>
    <section className="audit-detail-section"><h2>{t('audit.detail.attributes')}</h2>
      {detail.attributes.length ? <dl className="audit-attributes">{detail.attributes.map(attribute => <div key={attribute.key}>
        <dt><FieldName t={t} name={attribute.key} /></dt><dd><Values values={attribute.values} /></dd>
      </div>)}</dl> : <p className="muted">{t('audit.detail.noAttributes')}</p>}
    </section>
  </>;
}

export function Audit({ objectFilter, embedded = false }: { objectFilter?: { resourceType: string; resourceId: string }; embedded?: boolean } = {}) {
  const t = useT('access'), tCommon = useT('common'), tNav = useT('nav');
  const { data, loading, error, refresh, applyFilters, pagination } = usePaging<AuditEvent>('/audit/v1/events', undefined, objectFilter);
  const notifyError = useErrorNotification();
  const [form] = Form.useForm<AuditFilterValues>();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<AuditDetailResponseBody | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailFailed, setDetailFailed] = useState(false);
  const detailRequest = useRef<AbortController | null>(null);
  useEffect(() => () => detailRequest.current?.abort(), []);

  async function loadDetail(eventId: string) {
    detailRequest.current?.abort();
    const controller = new AbortController(); detailRequest.current = controller;
    setSelectedId(eventId); setDetail(null); setDetailLoading(true); setDetailFailed(false);
    try {
      const result = await session.request<AuditDetailResponseBody>(`/audit/v1/events/${encodeURIComponent(eventId)}`, { signal: controller.signal });
      if (controller.signal.aborted) return;
      validateAuditDetail(result, eventId); setDetail(result);
    } catch (failure) {
      if (!controller.signal.aborted) { setDetailFailed(true); notifyError(failure, t('audit.detailLoadFailed')); }
    } finally { if (!controller.signal.aborted) setDetailLoading(false); }
  }
  function closeDetail() {
    detailRequest.current?.abort(); setSelectedId(null); setDetail(null); setDetailFailed(false);
  }
  function submitFilters(values: AuditFilterValues) {
    const now = new Date();
    const filters = auditFilters(values, now);
    if (values.timeRange) form.setFieldValue('timeRange', freezeTimeRange(values.timeRange, now));
    applyFilters({ ...filters, ...objectFilter });
  }

  return <>
    <div className="page-heading">{embedded ? <h2>{t('tenantDetails.tab.audit')}</h2> : <h1>{tNav('resource.OPERATIONS_AUDIT')}</h1>}</div>
    <Form form={form} layout="vertical" className="audit-filters" requiredMark={false}
      initialValues={objectFilter} onFinish={submitFilters}>
      <div className="audit-filter-grid">
        <Form.Item name="timeRange" label={t('audit.filter.timeRange')} className="audit-time-range" validateTrigger="onSubmit"
          rules={[{ validator: async (_, value) => { auditFilters({ timeRange: value }); } }]}>
          <TimeRangePicker />
        </Form.Item>
        <Form.Item name="actorId" label={t('audit.detail.actorId')}><Input maxLength={128} allowClear placeholder={t('audit.filter.exactMatch')} /></Form.Item>
        <Form.Item name="actionCode" label={t('audit.detail.action')}><AutoComplete allowClear
          options={auditActionCodes.map(value => ({ value, label: t('audit.filter.actionOption', { label: enumLabel(t, 'enum.auditAction', value), code: value }) }))}
          filterOption={(input, option) => String(option?.label).toLowerCase().includes(input.toLowerCase())}>
          <Input maxLength={64} placeholder={t('audit.filter.actionPlaceholder')} /></AutoComplete></Form.Item>
        <Form.Item name="result" label={t('audit.detail.result')}><Select allowClear placeholder={t('audit.filter.allResults')}
          options={auditResultCodes.map(value => ({ value, label: enumLabel(t, 'enum.auditResult', value) }))} /></Form.Item>
        {!embedded && <><Form.Item name="resourceType" label={t('audit.filter.objectType')}><Select allowClear placeholder={t('tenants.filter.allTypes')} options={auditResourceTypeCodes.map(value => ({ value, label: enumLabel(t, 'enum.auditResourceType', value) }))} /></Form.Item>
          <Form.Item name="resourceId" label={t('audit.filter.objectId')} dependencies={['resourceType']} rules={[{ validator: async (_, value) => { if (value?.trim() && !form.getFieldValue('resourceType')) throw new Error(t('audit.filter.selectObjectType')); } }]}><Input maxLength={128} allowClear placeholder={t('audit.filter.exactMatch')} /></Form.Item></>}
        <Form.Item name="requestId" label={t('audit.detail.requestId')}><Input maxLength={128} allowClear placeholder={t('audit.filter.exactMatch')} /></Form.Item>
      </div>
      <div className="audit-filter-actions"><span className="muted">{t('audit.filter.rangeHint')}</span>
        <Space><Button onClick={() => { form.resetFields(); applyFilters(objectFilter || {}); }}>{t('tenants.filter.reset')}</Button><Button type="primary" htmlType="submit">{t('tenants.filter.search')}</Button></Space>
      </div>
    </Form>
    <section className="table-panel"><div className="table-toolbar"><span className="table-count"><span className="table-caption">{t('audit.total', { total: data.total })}</span>
      <RefreshButton onClick={refresh} loading={loading} failed={!!error} /></span></div>
      <Table<AuditEvent> rowKey="auditEventId" dataSource={error ? [] : data.items} loading={loading} scroll={{ x: 1180 }}
        pagination={pagination}
        locale={{ emptyText: error ? t('audit.unavailable') : t('audit.empty') }} columns={[
          { title: t('audit.detail.time'), dataIndex: 'occurredTime', width: 190, render: (value: string) => formatDateTime(value) },
          { title: t('audit.column.actor'), dataIndex: 'actorId' },
          { title: t('audit.column.action'), dataIndex: 'actionCode', render: (value: string) => enumLabel(t, 'enum.auditAction', value) },
          { title: t('audit.column.object'), render: (_, row) => <div>{enumLabel(t, 'enum.auditResourceType', row.resourceType)}<div className="cell-meta">{row.resourceId}</div></div> },
          { title: t('audit.detail.result'), dataIndex: 'result', render: (value: string) => <ResultTag result={value} /> },
          { title: t('audit.column.reasonRequest'), render: (_, row) => <div>{row.reason || '—'}<div className="cell-meta">{row.requestId}</div></div> },
          { title: t('audit.column.details'), width: 76, fixed: 'right', render: (_, row) => <Button type="link" size="small" onClick={() => void loadDetail(row.auditEventId)}>{t('audit.action.view')}</Button> },
        ]} />
    </section>
    <Drawer title={t('audit.detailTitle')} size={760} className="audit-detail" open={!!selectedId} onClose={closeDetail} destroyOnHidden>
      {detailLoading ? <div className="content-loading"><Spin /></div> : detailFailed
        ? <p className="muted">{t('audit.detailUnavailable')}<Button type="link" onClick={() => selectedId && void loadDetail(selectedId)}>{tCommon('action.retry')}</Button></p>
        : detail && <Details detail={detail} />}
    </Drawer>
  </>;
}
