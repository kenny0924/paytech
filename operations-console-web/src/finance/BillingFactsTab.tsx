import { useMemo, useState } from 'react';
import { Alert, Button, Empty, Form, Input, Select, Table } from 'antd';
import { TimeRangePicker } from '../components/TimeRangePicker';
import { resolveTimeRange } from '../components/timeRange';
import type { TimeRangeValue } from '../components/timeRange';
import { usePaging } from '../hooks/usePaging';
import { activeOnly, decimalIdPattern, entityNoPattern, localTime, pageOf } from './finance';
import { billingFactStatusLabels, billingFilters, decodeBillingFactSummary, factTypeLabels, workflowPaths } from './financeWorkflow';
import type { BillingFactSummary } from './financeWorkflow';
import { BillingFactDrawer, CreateBillingFactDrawer } from './BillingFactDrawers';
import { Amount, FinanceNote, LoadFailure } from './FinanceParts';
import { useCurrencies } from './useFinanceData';
import { ActorText, WorkflowTag } from './WorkflowParts';

interface Props { canManage: boolean; canApprove: boolean; actorId: string; onAuthorizationChanged: () => void }
interface SearchValues { entityNo?: string; currency?: string; clientId?: string; status?: string; factType?: string; timeRange?: TimeRangeValue }

const statusOptions = Object.entries(billingFactStatusLabels).map(([value, label]) => ({ value, label }));
const typeOptions = Object.entries(factTypeLabels).map(([value, label]) => ({ value, label: `${label}（${value}）` }));
const factPage = pageOf(decodeBillingFactSummary);

/**
 * 「计费事实」页签：经营主体 + 币种必填，可按 Client、状态、类型与登记时间筛选；列表 → 登记抽屉 / 详情抽屉
 * <li>本期只登记 MANUAL_ADJUSTMENT；批准后发布 BillingFactRecorded，account / ledger 入账。</li>
 */
export function BillingFactsTab({ canManage, canApprove, actorId, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<SearchValues>();
  const currencies = useCurrencies(onAuthorizationChanged);
  const [filters, setFilters] = useState<Record<string, string> | null>(null);
  const [searchError, setSearchError] = useState<string | null>(null);
  const [creating, setCreating] = useState(false); const [openFact, setOpenFact] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);
  function search(values: SearchValues) {
    const range = resolveTimeRange(values.timeRange);
    try {
      setFilters(billingFilters({ ...values, createdFrom: range.start, createdTo: range.end })); setSearchError(null);
    } catch (error) { setSearchError(error instanceof Error ? error.message : '查询条件不正确'); }
  }
  const minorUnit = filters ? currencies.minorUnits.get(filters.currency) : undefined;
  return <>
    <Form form={form} name="finance-billing-filters" layout="vertical" onFinish={search}><div className="finance-search">
      <Form.Item name="entityNo" label="经营主体（entityNo）" normalize={(value: string) => value?.toUpperCase()}
        rules={[{ required: true, message: '请填写 entityNo' }, { pattern: entityNoPattern, message: '两位大写国家码加三位序号，如 AE001' }]}>
        <Input maxLength={5} placeholder="AE001" /></Form.Item>
      <Form.Item name="currency" label="币种" rules={[{ required: true, message: '请选择币种' }]}>
        <Select placeholder="选择币种" loading={currencies.loading} options={activeOnly(currencies.items).map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
      <Form.Item name="clientId" label="Client ID" rules={[{ pattern: decimalIdPattern, message: '正十进制整数字符串' }]}><Input allowClear maxLength={19} /></Form.Item>
      <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={statusOptions} /></Form.Item>
      <Form.Item name="factType" label="事实类型"><Select allowClear placeholder="全部" options={typeOptions} /></Form.Item>
      <Form.Item name="timeRange" label="登记时间"><TimeRangePicker /></Form.Item>
      <div className="configuration-actions"><Button htmlType="submit">查询</Button>
        {canManage && <Button type="primary" onClick={() => setCreating(true)}>登记计费事实</Button>}</div>
    </div></Form>
    {searchError && <Alert type="warning" showIcon className="finance-banner" title={searchError} />}
    {!filters ? <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="填写经营主体与币种后查询计费事实" /></div>
      : <FactList key={`${JSON.stringify(filters)}:${reloadToken}`} filters={filters} actorId={actorId} minorUnit={minorUnit} onOpen={setOpenFact} />}
    <FinanceNote>计费事实草稿经非本人复核批准后才入账；填错的草稿可作废（不发布事件、不能再批准）。替代事实批准时原事实变为「已替代」。{!canManage && ' 当前账号没有登记 / 作废权限。'}{!canApprove && ' 当前账号没有复核权限。'}</FinanceNote>
    {creating && <CreateBillingFactDrawer defaults={{ entityNo: filters?.entityNo ?? form.getFieldValue('entityNo'), currency: filters?.currency ?? form.getFieldValue('currency') }}
      currencies={currencies.items} minorUnits={currencies.minorUnits} onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={factId => { setCreating(false); setReloadToken(value => value + 1); setOpenFact(factId); }} />}
    {openFact && <BillingFactDrawer factId={openFact} minorUnits={currencies.minorUnits} canManage={canManage} canApprove={canApprove} actorId={actorId}
      onClose={() => setOpenFact(null)} onChanged={() => setReloadToken(value => value + 1)} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

function FactList({ filters, actorId, minorUnit, onOpen }: { filters: Record<string, string>; actorId: string; minorUnit?: number; onOpen: (factId: string) => void }) {
  const initial = useMemo(() => filters, [filters]);
  const page = usePaging<BillingFactSummary>(workflowPaths.billingFacts, factPage, initial);
  return page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
    <Table<BillingFactSummary> rowKey="factId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1360 }}
      pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} onRow={row => ({ onClick: () => onOpen(row.factId), className: 'clickable-row' })}
      locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的计费事实" /> }} columns={[
        { title: '事实 ID', width: 190, render: (_, row) => <a className="mono">{row.factId}</a> },
        { title: '类型', width: 130, render: (_, row) => factTypeLabels[row.factType] ?? row.factType },
        { title: '来源引用', width: 200, render: (_, row) => <span className="mono finance-code">{row.sourceReference}</span> },
        { title: 'Client', width: 170, render: (_, row) => row.clientId ? <span className="mono">{row.clientId}</span> : '—' },
        { title: '总额', width: 130, align: 'right', render: (_, row) => <Amount value={row.grossAmount} minorUnit={minorUnit} /> },
        { title: '状态', width: 90, render: (_, row) => <WorkflowTag status={row.status} labels={billingFactStatusLabels} /> },
        { title: '制单人', width: 190, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
        { title: '复核人', width: 190, render: (_, row) => <ActorText value={row.checkerId} actorId={actorId} /> },
        { title: '登记时间', width: 150, render: (_, row) => localTime(row.createTime) },
        { title: '批准时间', width: 150, render: (_, row) => row.approvedTime ? localTime(row.approvedTime) : '—' },
      ]} />
  </section>;
}
