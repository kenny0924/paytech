import { useMemo } from 'react';
import { Button, Form, Input, Select, Table, Tag } from 'antd';
import { usePaging } from '../hooks/usePaging';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { freezeTimeRange } from '../components/timeRange';
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { manualEventFilters, vaultActions, vaultAdminPath } from '../vault/vault';
import type { ManualEventFilterValues, VaultManualEvent } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

const outcomeColor: Record<string, string> = { SUCCESS: 'green', DENIED: 'orange', FAILED: 'red' };

/** 人工操作记录：按操作人、动作、数据类型和时间筛选；只显示审计元数据，不含 Token 与原值。 */
export function VaultManualEvents() {
  const t = useT('vault'), tCommon = useT('common'), tNav = useT('nav');
  const actionOptions = useMemo(() => vaultActions.map(value => ({ value, label: enumLabel(t, 'enum.action', value) })), [t]);
  const [form] = Form.useForm<ManualEventFilterValues>(); const page = usePaging<VaultManualEvent>(`${vaultAdminPath}/manual-events`);
  function search(values: ManualEventFilterValues) {
    if (values.timeRange) form.setFieldValue('timeRange', freezeTimeRange(values.timeRange));
    page.applyFilters(manualEventFilters(values));
  }
  return <div className="configuration-page vault-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_VAULT_MANUAL_EVENTS')}</h1></div>
    <Form form={form} layout="vertical" onFinish={search}><div className="vault-search">
      <Form.Item name="actorId" label={t('manualEvents.field.actor')}><Input allowClear maxLength={64} placeholder={t('manualEvents.field.actorPlaceholder')} /></Form.Item>
      <Form.Item name="action" label={t('manualEvents.field.action')}><Select allowClear placeholder={t('filters.all')} options={actionOptions} /></Form.Item>
      <Form.Item name="dataTypeCode" label={t('manualEvents.field.dataType')}><Input allowClear maxLength={64} placeholder={t('manualEvents.field.dataTypePlaceholder')} /></Form.Item>
      <Form.Item name="timeRange" label={t('manualEvents.field.timeRange')} validateTrigger="onSubmit"
        rules={[{ validator: async (_, value) => { manualEventFilters({ timeRange: value }); } }]}><TimeRangePicker /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('filters.search')}</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('filters.reset')}</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>{t('manualEvents.count', { count: page.data.total })}</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
      <Table<VaultManualEvent> rowKey="requestId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1190 }} pagination={page.pagination} columns={[
        { title: t('manualEvents.column.time'), dataIndex: 'createTime', width: 190, render: (value: string) => formatDateTime(value) },
        { title: t('manualEvents.field.actor'), dataIndex: 'actorId', width: 190, ellipsis: true },
        { title: t('manualEvents.field.action'), width: 80, render: (_, row) => enumLabel(t, 'enum.action', row.action) },
        { title: t('manualEvents.field.dataType'), width: 220, ellipsis: true, render: (_, row) => row.dataTypeCode || '—' },
        { title: t('manualEvents.column.reason'), width: 130, render: (_, row) => enumLabel(t, 'enum.reasonCode', row.reasonCode) },
        { title: t('manualEvents.column.ticketRef'), dataIndex: 'ticketRef', width: 140, ellipsis: true },
        { title: t('manualEvents.column.outcome'), width: 200, render: (_, row) => <><Tag color={outcomeColor[row.outcome]}>{enumLabel(t, 'enum.outcome', row.outcome)}</Tag>
          {row.outcome !== 'SUCCESS' && <span className="cell-meta">{row.resultCode}</span>}</> },
      ]} /></section>
  </div>;
}
