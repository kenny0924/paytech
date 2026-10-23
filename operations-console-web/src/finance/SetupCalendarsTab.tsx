import { useEffect, useMemo, useState } from 'react';
import { Button, Empty, Form, Input, Select, Table } from 'antd';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  decodeAccountingPeriod, decodeFiscalCalendar, entityLabel, financeErrorTarget, financePaths, ledgerEntityTypes, pageOf,
} from './finance';
import type { AccountingPeriod, FinanceEntity, FiscalCalendar } from './finance';
import { CreatePeriodModal, OpenPeriodModal } from './FinanceModals';
import { FinanceNote, LoadFailure, StatusTag } from './FinanceParts';

const calendarPage = pageOf(decodeFiscalCalendar);

/** 财年筛选：近五年到后两年。 */
const fiscalYearOptions = (t: Translate, now = new Date().getFullYear()) =>
  Array.from({ length: 8 }, (_, index) => now + 2 - index).map(year => ({ value: year, label: t('calendars.fiscalYearOption', { year: String(year) }) }));

interface Props { canManage: boolean; reloadToken: number; entities: ReadonlyMap<string, FinanceEntity>; onAuthorizationChanged: () => void }

/** 日历与期间：上半日历列表（选中高亮），下半所选日历的期间；FUTURE 期间可二次确认后开放。 */
export function SetupCalendarsTab({ canManage, reloadToken, entities, onAuthorizationChanged }: Props) {
  const t = useT('finance');
  const [form] = Form.useForm<{ entityType?: string; entityId?: string }>();
  const entityTypeOptions = useMemo(() => ledgerEntityTypes.map(value => ({ value, label: `${enumLabel(t, 'enum.ledgerEntityType', value)} ${value}` })), [t]);
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
      <Form.Item name="entityType" label={t('field.entityType')}><Select allowClear placeholder={t('placeholder.all')} options={entityTypeOptions} /></Form.Item>
      <Form.Item name="entityId" label={t('field.entityId')}><Input allowClear maxLength={64} placeholder={t('books.entityIdPlaceholder')} /></Form.Item>
      <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button></div>
    </div></Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<FiscalCalendar> rowKey="fiscalCalendarId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 880 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        rowClassName={row => row.fiscalCalendarId === selected?.fiscalCalendarId ? 'finance-row-selected' : 'clickable-row'}
        onRow={row => ({ onClick: () => setSelected(row) })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('calendars.empty')} /> }} columns={[
          { title: t('field.entityType'), width: 110, render: (_, row) => enumLabel(t, 'enum.ledgerEntityType', row.entityType) },
          { title: t('field.entityId'), render: (_, row) => entityLabel(row.entityType, row.entityId, entities) },
          { title: t('calendars.column.calendarCode'), dataIndex: 'calendarCode', width: 160 },
          { title: t('calendars.column.timezone'), dataIndex: 'timezone', width: 180 },
          { title: t('calendars.column.yearStartMonth'), dataIndex: 'yearStartMonth', width: 110 },
          { title: t('field.actions'), width: 100, render: (_, row) => <Button type="link" size="small" onClick={() => setSelected(row)}>{t('calendars.viewPeriods')}</Button> },
        ]} />
    </section>}
    {selected ? <PeriodsPanel key={selected.fiscalCalendarId} calendar={selected} calendarLabel={`${entityLabel(selected.entityType, selected.entityId, entities)} / ${selected.calendarCode}`}
      canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} />
      : <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('calendars.selectCalendar')} /></div>}
    <FinanceNote>{t('calendars.note')}</FinanceNote>
  </>;
}

function PeriodsPanel({ calendar, calendarLabel, canManage, onAuthorizationChanged }: {
  calendar: FiscalCalendar; calendarLabel: string; canManage: boolean; onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const decode = useMemo(() => pageOf((value: unknown) => decodeAccountingPeriod(value, calendar.fiscalCalendarId)), [calendar.fiscalCalendarId]);
  const page = usePaging<AccountingPeriod>(financePaths.periods(calendar.fiscalCalendarId), decode);
  const [dialog, setDialog] = useState<{ kind: 'create' } | { kind: 'open'; period: AccountingPeriod } | null>(null);
  const done = () => { setDialog(null); page.refresh(); };
  const pageHint = financeErrorTarget(page.failure)?.message;
  return <section className="finance-section">
    <div className="finance-section-head">
      <h2>{t('calendars.periodsTitle', { calendar: calendarLabel })}</h2>
      <Select allowClear placeholder={t('calendars.allFiscalYears')} className="finance-year" aria-label={t('calendars.column.fiscalYear')} options={fiscalYearOptions(t)}
        onChange={(value?: number) => page.applyFilters(value ? { fiscalYear: String(value) } : {})} />
      <span className="finance-spacer" />
      {canManage && <Button type="primary" onClick={() => setDialog({ kind: 'create' })}>{t('calendars.createPeriod')}</Button>}
    </div>
    {page.error ? <LoadFailure message={pageHint ?? page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<AccountingPeriod> rowKey="accountingPeriodId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 800 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('calendars.periodsEmpty')} /> }} columns={[
          { title: t('calendars.column.fiscalYear'), dataIndex: 'fiscalYear', width: 90 },
          { title: t('calendars.column.periodNo'), dataIndex: 'periodNo', width: 80 },
          { title: t('field.startDate'), dataIndex: 'startDate', width: 130 },
          { title: t('field.endDate'), dataIndex: 'endDate', width: 130 },
          { title: t('field.status'), width: 110, render: (_, row) => <StatusTag status={row.status} /> },
          { title: t('calendars.column.reopenCount'), dataIndex: 'reopenCount', width: 90 },
          ...(canManage ? [{ title: t('field.actions'), width: 90, render: (_: unknown, row: AccountingPeriod) => row.status === 'FUTURE'
            ? <Button type="link" size="small" onClick={() => setDialog({ kind: 'open', period: row })}>{t('calendars.openPeriod')}</Button> : <span className="muted">—</span> }] : []),
        ]} />
    </section>}
    {dialog?.kind === 'create' && <CreatePeriodModal calendar={calendar} calendarLabel={calendarLabel} onClose={() => setDialog(null)} onDone={done}
      onAuthorizationChanged={onAuthorizationChanged} />}
    {dialog?.kind === 'open' && <OpenPeriodModal period={dialog.period} calendarLabel={t('common.labelWithId', { label: calendarLabel, id: calendar.timezone })}
      onClose={() => setDialog(null)} onDone={done} onAuthorizationChanged={onAuthorizationChanged} />}
  </section>;
}
