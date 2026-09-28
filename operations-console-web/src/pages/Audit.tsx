import { useEffect, useRef, useState } from 'react';
import { AutoComplete, Button, Drawer, Form, Input, Select, Space, Spin, Table, Tag, Typography } from 'antd';
import type { AuditChange, AuditDetailResponseBody, AuditEvent } from '../api/types';
import { session } from '../api/session';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { auditActions, auditFilters, auditResourceTypes, auditResults, auditValue, validateAuditDetail } from './auditView';
import type { AuditFilterValues } from './auditView';

function ResultTag({ result }: { result: string }) {
  return <Tag color={result === 'SUCCEEDED' ? 'green' : result === 'DENIED' ? 'orange' : 'red'}>{auditResults[result] || result}</Tag>;
}
function Values({ values }: { values: (string | null)[] }) {
  if (!values.length) return <span className="muted">未记录</span>;
  return <div className="audit-values">{values.map((value, index) => <div key={index} className={value === null ? 'muted' : undefined}>{auditValue(value)}</div>)}</div>;
}
function RequestId({ value }: { value: string }) {
  return value ? <Typography.Text className="audit-request-id" copyable={{ text: value, tooltips: ['复制 Request ID', '已复制'] }}>{value}</Typography.Text> : <>—</>;
}
function Details({ detail }: { detail: AuditDetailResponseBody }) {
  const { event } = detail;
  return <>
    <dl className="audit-summary">
      <dt>时间</dt><dd>{new Date(event.occurredTime).toLocaleString('zh-CN', { hour12: false })}</dd>
      <dt>操作者 ID</dt><dd>{event.actorId}</dd>
      <dt>操作类型</dt><dd>{auditActions[event.actionCode] || event.actionCode}<div className="cell-meta">{event.actionCode}</div></dd>
      <dt>结果</dt><dd><ResultTag result={event.result} /></dd>
      <dt>操作对象</dt><dd>{auditResourceTypes[event.resourceType] || event.resourceType}<div className="cell-meta">{event.resourceId}</div></dd>
      <dt>审计记录 ID</dt><dd>{event.auditEventId}</dd>
      <dt>Request ID</dt><dd><RequestId value={event.requestId} /></dd>
      <dt>操作原因</dt><dd className="audit-values">{event.reason || '未记录'}</dd>
    </dl>
    <section className="audit-detail-section"><h2>字段变更</h2>
      {detail.changes.length ? <Table<AuditChange> size="small" rowKey="key" pagination={false} dataSource={detail.changes}
        scroll={{ x: 500 }} columns={[
          { title: '字段', width: '30%', render: (_, change) => <div>{change.label}<div className="cell-meta">{change.key}</div></div> },
          { title: '变更前', width: '35%', render: (_, change) => <Values values={change.before} /> },
          { title: '变更后', width: '35%', render: (_, change) => <Values values={change.after} /> },
        ]} /> : <p className="muted">未记录字段变更</p>}
    </section>
    <section className="audit-detail-section"><h2>其他记录</h2>
      {detail.attributes.length ? <dl className="audit-attributes">{detail.attributes.map(attribute => <div key={attribute.key}>
        <dt>{attribute.label}<span className="cell-meta">{attribute.key}</span></dt><dd><Values values={attribute.values} /></dd>
      </div>)}</dl> : <p className="muted">未记录其他属性</p>}
    </section>
  </>;
}

export function Audit({ objectFilter, embedded = false }: { objectFilter?: { resourceType: string; resourceId: string }; embedded?: boolean } = {}) {
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
      if (!controller.signal.aborted) { setDetailFailed(true); notifyError(failure, '审计详情加载失败'); }
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
    <div className="page-heading">{embedded ? <h2>操作记录</h2> : <h1>审计日志</h1>}
      <Button onClick={refresh} loading={loading}>刷新</Button></div>
    <Form form={form} layout="vertical" className="audit-filters" requiredMark={false}
      initialValues={objectFilter} onFinish={submitFilters}>
      <div className="audit-filter-grid">
        <Form.Item name="timeRange" label="时间范围" className="audit-time-range" validateTrigger="onSubmit"
          rules={[{ validator: async (_, value) => { auditFilters({ timeRange: value }); } }]}>
          <TimeRangePicker />
        </Form.Item>
        <Form.Item name="actorId" label="操作者 ID"><Input maxLength={128} allowClear placeholder="精确匹配" /></Form.Item>
        <Form.Item name="actionCode" label="操作类型"><AutoComplete allowClear
          options={Object.entries(auditActions).map(([value, label]) => ({ value, label: `${label} · ${value}` }))}
          filterOption={(input, option) => String(option?.label).toLowerCase().includes(input.toLowerCase())}>
          <Input maxLength={64} placeholder="选择或输入操作代码" /></AutoComplete></Form.Item>
        <Form.Item name="result" label="结果"><Select allowClear placeholder="全部结果"
          options={Object.entries(auditResults).map(([value, label]) => ({ value, label }))} /></Form.Item>
        {!embedded && <><Form.Item name="resourceType" label="对象类型"><Select allowClear placeholder="全部类型" options={Object.entries(auditResourceTypes).map(([value, label]) => ({ value, label }))} /></Form.Item>
          <Form.Item name="resourceId" label="对象 ID" dependencies={['resourceType']} rules={[{ validator: async (_, value) => { if (value?.trim() && !form.getFieldValue('resourceType')) throw new Error('请同时选择对象类型'); } }]}><Input maxLength={128} allowClear placeholder="精确匹配" /></Form.Item></>}
        <Form.Item name="requestId" label="Request ID"><Input maxLength={128} allowClear placeholder="精确匹配" /></Form.Item>
      </div>
      <div className="audit-filter-actions"><span className="muted">快捷范围按本地自然日计算；结束时间不包含在内。</span>
        <Space><Button onClick={() => { form.resetFields(); applyFilters(objectFilter || {}); }}>重置</Button><Button type="primary" htmlType="submit">查询</Button></Space>
      </div>
    </Form>
    <section className="table-panel"><div className="table-toolbar"><span className="table-caption">共 {data.total} 条记录</span>
      {error && <Button size="small" onClick={refresh}>重试</Button>}</div>
      <Table<AuditEvent> rowKey="auditEventId" dataSource={error ? [] : data.items} loading={loading} scroll={{ x: 1180 }}
        pagination={pagination}
        locale={{ emptyText: error ? '暂时无法加载审计记录' : '暂无审计记录' }} columns={[
          { title: '时间', dataIndex: 'occurredTime', width: 190, render: (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false }) },
          { title: '操作者', dataIndex: 'actorId' },
          { title: '操作', dataIndex: 'actionCode', render: (value: string) => auditActions[value] || value },
          { title: '对象', render: (_, row) => <div>{auditResourceTypes[row.resourceType] || row.resourceType}<div className="cell-meta">{row.resourceId}</div></div> },
          { title: '结果', dataIndex: 'result', render: (value: string) => <ResultTag result={value} /> },
          { title: '原因 / 请求编号', render: (_, row) => <div>{row.reason || '—'}<div className="cell-meta">{row.requestId}</div></div> },
          { title: '详情', width: 76, fixed: 'right', render: (_, row) => <Button type="link" size="small" onClick={() => void loadDetail(row.auditEventId)}>查看</Button> },
        ]} />
    </section>
    <Drawer title="审计详情" size={760} className="audit-detail" open={!!selectedId} onClose={closeDetail} destroyOnHidden>
      {detailLoading ? <div className="content-loading"><Spin /></div> : detailFailed
        ? <p className="muted">暂时无法加载审计详情。<Button type="link" onClick={() => selectedId && void loadDetail(selectedId)}>重试</Button></p>
        : detail && <Details detail={detail} />}
    </Drawer>
  </>;
}
