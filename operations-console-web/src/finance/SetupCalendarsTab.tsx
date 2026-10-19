import { useEffect, useMemo, useState } from 'react';
import { Button, Empty, Form, Input, Select, Table } from 'antd';
import { usePaging } from '../hooks/usePaging';
import {
  decodeAccountingPeriod, decodeFiscalCalendar, entityLabel, entityTypeLabels, financeErrorTarget, financePaths, ledgerEntityTypes, pageOf,
} from './finance';
import type { AccountingPeriod, FinanceEntity, FiscalCalendar } from './finance';
import { CreatePeriodModal, OpenPeriodModal } from './FinanceModals';
import { FinanceNote, LoadFailure, StatusTag } from './FinanceParts';

const calendarPage = pageOf(decodeFiscalCalendar);
const entityTypeOptions = ledgerEntityTypes.map(value => ({ value, label: `${entityTypeLabels[value]} ${value}` }));

/** 财年筛选：近五年到后两年。 */
const fiscalYearOptions = (now = new Date().getFullYear()) =>
  Array.from({ length: 8 }, (_, index) => now + 2 - index).map(year => ({ value: year, label: `财年 ${year}` }));

interface Props { canManage: boolean; reloadToken: number; entities: ReadonlyMap<string, FinanceEntity>; onAuthorizationChanged: () => void }

/** 日历与期间：上半日历列表（选中高亮），下半所选日历的期间；FUTURE 期间可二次确认后开放。 */
export function SetupCalendarsTab({ canManage, reloadToken, entities, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<{ entityType?: string; entityId?: string }>();
  const page = usePaging<FiscalCalendar>(financePaths.fiscalCalendars, calendarPage);
  const [selected, setSelected] = useState<FiscalCalendar | null>(null);
  useEffect(() => { if (reloadToken) page.refresh(); }, [reloadToken]);
  function search(values: { entityType?: string; entityId?: string }) {
    const filters: Record<string, string> = {};
    if (values.entityType) filters.entityType = values.entityType;
    if (values.entityId?.trim()) filters.entityId = values.entityId.trim();
    setSelected(null); page.applyFilters(filters);
  }
  return <>
    <Form form={form} name="finance-calendar-filters" layout="vertical" onFinish={search}><div className="finance-search">
      <Form.Item name="entityType" label="主体类型"><Select allowClear placeholder="全部" options={entityTypeOptions} /></Form.Item>
      <Form.Item name="entityId" label="主体标识"><Input allowClear maxLength={64} placeholder="请输入 entityNo 或财务主体主键" /></Form.Item>
      <div className="configuration-actions"><Button htmlType="submit">查询</Button></div>
    </div></Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<FiscalCalendar> rowKey="fiscalCalendarId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 880 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        rowClassName={row => row.fiscalCalendarId === selected?.fiscalCalendarId ? 'finance-row-selected' : 'clickable-row'}
        onRow={row => ({ onClick: () => setSelected(row) })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有财务日历" /> }} columns={[
          { title: '主体类型', width: 110, render: (_, row) => entityTypeLabels[row.entityType] },
          { title: '主体标识', render: (_, row) => entityLabel(row.entityType, row.entityId, entities) },
          { title: '日历代码', dataIndex: 'calendarCode', width: 160 },
          { title: '时区', dataIndex: 'timezone', width: 180 },
          { title: '财年起始月', dataIndex: 'yearStartMonth', width: 110 },
          { title: '操作', width: 100, render: (_, row) => <Button type="link" size="small" onClick={() => setSelected(row)}>查看期间</Button> },
        ]} />
    </section>}
    {selected ? <PeriodsPanel key={selected.fiscalCalendarId} calendar={selected} calendarLabel={`${entityLabel(selected.entityType, selected.entityId, entities)} / ${selected.calendarCode}`}
      canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} />
      : <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择一个日历查看会计期间" /></div>}
    <FinanceNote>每个主体只有一套日历；期间不得与同日历其他期间重叠，同财年期号唯一。只有 FUTURE 期间显示「开放」，开放后可过账且不能回到 FUTURE；关账与重开在第二批。</FinanceNote>
  </>;
}

function PeriodsPanel({ calendar, calendarLabel, canManage, onAuthorizationChanged }: {
  calendar: FiscalCalendar; calendarLabel: string; canManage: boolean; onAuthorizationChanged: () => void;
}) {
  const decode = useMemo(() => pageOf((value: unknown) => decodeAccountingPeriod(value, calendar.fiscalCalendarId)), [calendar.fiscalCalendarId]);
  const page = usePaging<AccountingPeriod>(financePaths.periods(calendar.fiscalCalendarId), decode);
  const [dialog, setDialog] = useState<{ kind: 'create' } | { kind: 'open'; period: AccountingPeriod } | null>(null);
  const done = () => { setDialog(null); page.refresh(); };
  const pageHint = financeErrorTarget(page.failure)?.message;
  return <section className="finance-section">
    <div className="finance-section-head">
      <h2>会计期间 · {calendarLabel}</h2>
      <Select allowClear placeholder="全部财年" className="finance-year" aria-label="财年" options={fiscalYearOptions()}
        onChange={(value?: number) => page.applyFilters(value ? { fiscalYear: String(value) } : {})} />
      <span className="finance-spacer" />
      {canManage && <Button type="primary" onClick={() => setDialog({ kind: 'create' })}>新建期间</Button>}
    </div>
    {page.error ? <LoadFailure message={pageHint ?? page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<AccountingPeriod> rowKey="accountingPeriodId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 800 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该日历还没有会计期间" /> }} columns={[
          { title: '财年', dataIndex: 'fiscalYear', width: 90 },
          { title: '期号', dataIndex: 'periodNo', width: 80 },
          { title: '开始日期', dataIndex: 'startDate', width: 130 },
          { title: '结束日期', dataIndex: 'endDate', width: 130 },
          { title: '状态', width: 110, render: (_, row) => <StatusTag status={row.status} /> },
          { title: '重开次数', dataIndex: 'reopenCount', width: 90 },
          ...(canManage ? [{ title: '操作', width: 90, render: (_: unknown, row: AccountingPeriod) => row.status === 'FUTURE'
            ? <Button type="link" size="small" onClick={() => setDialog({ kind: 'open', period: row })}>开放</Button> : <span className="muted">—</span> }] : []),
        ]} />
    </section>}
    {dialog?.kind === 'create' && <CreatePeriodModal calendar={calendar} calendarLabel={calendarLabel} onClose={() => setDialog(null)} onDone={done}
      onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'open' && <OpenPeriodModal period={dialog.period} calendarLabel={`${calendarLabel}（${calendar.timezone}）`}
      onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
  </section>;
}
