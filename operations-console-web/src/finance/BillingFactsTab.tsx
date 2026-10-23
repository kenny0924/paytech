import { useMemo, useState } from 'react';
import { Alert, Button, Empty, Form, Input, Select, Table } from 'antd';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { resolveTimeRange } from '../components/timeRange';
import type { TimeRangeValue } from '../components/timeRange';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { activeOnly, decimalIdPattern, entityNoPattern, localTime, pageOf } from './finance';
import { billingFactStatuses, billingFilters, decodeBillingFactSummary, factTypes, workflowPaths } from './financeWorkflow';
import type { BillingFactSummary } from './financeWorkflow';
import { BillingFactDrawer, CreateBillingFactDrawer } from './BillingFactDrawers';
import { Amount, FinanceNote, LoadFailure } from './FinanceParts';
import { useCurrencies } from './useFinanceData';
import { ActorText, WorkflowTag } from './WorkflowParts';

interface Props { canManage: boolean; canApprove: boolean; actorId: string; onAuthorizationChanged: () => void }
interface SearchValues { entityNo?: string; currency?: string; clientId?: string; status?: string; factType?: string; timeRange?: TimeRangeValue }

const factPage = pageOf(decodeBillingFactSummary);

/**
 * 「计费事实」页签：经营主体 + 币种必填，可按 Client、状态、类型与登记时间筛选；列表 → 登记抽屉 / 详情抽屉
 * <li>本期只登记 MANUAL_ADJUSTMENT；批准后发布 BillingFactRecorded，account / ledger 入账。</li>
 */
export function BillingFactsTab({ canManage, canApprove, actorId, onAuthorizationChanged }: Props) {
  const t = useT('finance');
  const [form] = Form.useForm<SearchValues>();
  const statusOptions = useMemo(() => billingFactStatuses.map(value => ({ value, label: enumLabel(t, 'enum.billingFactStatus', value) })), [t]);
  const typeOptions = useMemo(() => factTypes.map(value =>
    ({ value, label: t('common.codeWithLabel', { code: enumLabel(t, 'enum.factType', value), label: value }) })), [t]);
  const currencies = useCurrencies(onAuthorizationChanged);
  const [filters, setFilters] = useState<Record<string, string> | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false); const [openFact, setOpenFact] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  function search(values: SearchValues) {
    const range = resolveTimeRange(values.timeRange);
    try {
      setFilters(billingFilters({ ...values, createdFrom: range.start, createdTo: range.end })); setSearchError(null);
    } catch (error) { setSearchError(error instanceof Error ? error.message : t('billing.searchInvalid')); }
  }
  const minorUnit = filters ? currencies.minorUnits.get(filters.currency) : undefined;
  return <>
    <Form form={form} name="finance-billing-filters" layout="vertical" onFinish={search}><div className="finance-search">
      <Form.Item name="entityNo" label={t('billing.field.entityNo')} normalize={(value: string) => value?.toUpperCase()}
        rules={[{ required: true, message: t('modals.entityNoRequired') }, { pattern: entityNoPattern, message: t('billing.entityNoPattern') }]}>
        <Input maxLength={5} placeholder="AE001" /></Form.Item>
      <Form.Item name="currency" label={t('field.currency')} rules={[{ required: true, message: t('validation.currencyRequired') }]}>
        <Select placeholder={t('billing.currencyPlaceholder')} loading={currencies.loading} options={activeOnly(currencies.items).map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
      <Form.Item name="clientId" label={t('billing.field.clientId')} rules={[{ pattern: decimalIdPattern, message: t('validation.decimalId') }]}><Input allowClear maxLength={19} /></Form.Item>
      <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('placeholder.all')} options={statusOptions} /></Form.Item>
      <Form.Item name="factType" label={t('subledger.entries.column.factType')}><Select allowClear placeholder={t('placeholder.all')} options={typeOptions} /></Form.Item>
      <Form.Item name="timeRange" label={t('billing.field.createdAt')}><TimeRangePicker /></Form.Item>
      <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
        {canManage && <Button type="primary" onClick={() => setCreating(true)}>{t('billing.create')}</Button>}</div>
    </div></Form>
    {searchError && <Alert type="warning" showIcon className="finance-banner" title={searchError} />}
    {!filters ? <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('billing.selectScope')} /></div>
      : <FactList key={`${JSON.stringify(filters)}:${reloadToken}`} filters={filters} actorId={actorId} minorUnit={minorUnit} onOpen={setOpenFact} />}
    <FinanceNote>{t('billing.note')}{!canManage && ' ' + t('billing.noMakerPermission')}{!canApprove && ' ' + t('adjustments.noCheckerPermission')}</FinanceNote>
    {creating && <CreateBillingFactDrawer defaults={{ entityNo: filters?.entityNo ?? form.getFieldValue('entityNo'), currency: filters?.currency ?? form.getFieldValue('currency') }}
      currencies={currencies.items} minorUnits={currencies.minorUnits} onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={factId => { setCreating(false); setReloadToken(value => value + 1); setOpenFact(factId); }} />}
    {openFact && <BillingFactDrawer factId={openFact} minorUnits={currencies.minorUnits} canManage={canManage} canApprove={canApprove} actorId={actorId}
      onClose={() => setOpenFact(null)} onChanged={() => setReloadToken(value => value + 1)} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

function FactList({ filters, actorId, minorUnit, onOpen }: { filters: Record<string, string>; actorId: string; minorUnit?: number; onOpen: (factId: string) => void }) {
  const t = useT('finance');
  const initial = useMemo(() => filters, [filters]);
  const page = usePaging<BillingFactSummary>(workflowPaths.billingFacts, factPage, initial);
  return page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
    <Table<BillingFactSummary> rowKey="factId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1360 }}
      pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }} onRow={row => ({ onClick: () => onOpen(row.factId), className: 'clickable-row' })}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('billing.emptyFiltered')} /> }} columns={[
        { title: t('posting.field.factId'), width: 190, render: (_, row) => <a className="mono">{row.factId}</a> },
        { title: t('ledger.lines.column.type'), width: 130, render: (_, row) => enumLabel(t, 'enum.factType', row.factType) },
        { title: t('posting.field.movementRef'), width: 200, render: (_, row) => <span className="mono finance-code">{row.sourceReference}</span> },
        { title: t('billing.column.client'), width: 170, render: (_, row) => row.clientId ? <span className="mono">{row.clientId}</span> : '—' },
        { title: t('billing.column.grossAmount'), width: 130, align: 'right', render: (_, row) => <Amount value={row.grossAmount} minorUnit={minorUnit} /> },
        { title: t('field.status'), width: 90, render: (_, row) => <WorkflowTag status={row.status} enumType="billingFactStatus" /> },
        { title: t('workflow.maker'), width: 190, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
        { title: t('workflow.checker'), width: 190, render: (_, row) => <ActorText value={row.checkerId} actorId={actorId} /> },
        { title: t('billing.field.createdAt'), width: 150, render: (_, row) => localTime(row.createTime) },
        { title: t('workflow.approvedAt'), width: 150, render: (_, row) => row.approvedTime ? localTime(row.approvedTime) : '—' },
      ]} />
  </section>;
}
