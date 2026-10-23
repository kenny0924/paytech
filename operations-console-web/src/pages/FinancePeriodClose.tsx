import { useMemo, useState } from 'react';
import { Alert, App, Button, Empty, Form, Popconfirm, Select, Space, Table } from 'antd';
import { useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { decimalIdPattern, entityLabel, localTime, replaceQuery } from '../finance/finance';
import type { FiscalCalendar } from '../finance/finance';
import {
  closablePeriodId, decodePeriodBoard, decodePeriodCloseResult, financeActions, hasAction, ledgerWorkflowErrorTargets,
  periodLabel, reopenablePeriodId, sortedBoard, workflowPaths,
} from '../finance/financeWorkflow';
import type { PeriodBoard, PeriodBoardItem, PeriodClose } from '../finance/financeWorkflow';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { PeriodCloseDrawer, PeriodCloseHistoryDrawer, ReopenPeriodModal } from '../finance/PeriodCloseDrawers';
import { useFinanceCommand } from '../finance/useFinanceCommand';
import { useFinanceEntities, useFiscalCalendars, useLedgerBooks } from '../finance/useFinanceData';
import { ActorText, CommandFeedback, useDetail, WorkflowTag } from '../finance/WorkflowParts';
import '../configuration/configuration.css';
import '../finance/finance.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/**
 * 期间关账：财务日历选择 → 期间看板（状态、最近关账 / 重开记录）→ 发起关账 / 重开 → 关账详情与复核
 * <li>日历、打开的期间与关账序号存于 URL（?calendar=&period=&close=）；看板由 BFF 组合日历期间与各期间最近一条关账记录。</li>
 * <li>「发起关账」只对最早的开放期间显示，「重开」只对最近已关账期间显示，均需 …_PERIOD_CLOSE_MANAGE；发起关账同步执行 7 步，执行期间按钮置为进行中，不能重复提交。</li>
 */
export function FinancePeriodClose({ resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('finance'), tNav = useT('nav');
  const { message } = App.useApp();
  const [params, setParams] = useSearchParams();
  const canManage = hasAction(resources, financeActions.periodCloseManage);
  const canApprove = hasAction(resources, financeActions.periodCloseApprove);
  const calendars = useFiscalCalendars(onAuthorizationChanged);
  const entities = useFinanceEntities(true, onAuthorizationChanged);
  const books = useLedgerBooks(onAuthorizationChanged);
  const bookMap = useMemo(() => new Map(books.items.map(item => [item.ledgerBookId, item])), [books.items]);
  const pick = (key: string) => { const value = params.get(key); return value && decimalIdPattern.test(value) ? value : null; };
  const calendarId = pick('calendar'); const periodId = pick('period'); const closeNo = params.get('close');
  const calendar = calendars.items.find(item => item.fiscalCalendarId === calendarId);
  const go = (updates: Record<string, string | null>) => setParams(new URLSearchParams(replaceQuery(params.toString(), updates)));
  const decode = useMemo(() => decodePeriodBoard(calendarId ?? ''), [calendarId]);
  const board = useDetail<PeriodBoard>(calendar ? `${workflowPaths.periodCloseBoard}?${new URLSearchParams({ fiscalCalendarId: calendar.fiscalCalendarId })}` : null,
    decode, onAuthorizationChanged, t('periodClose.boardLoadFailed'));
  const items = board.item ? sortedBoard(board.item) : [];
  const periods = items.map(item => item.period);
  const closable = closablePeriodId(periods); const reopenable = reopenablePeriodId(periods);
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], ledgerWorkflowErrorTargets);
  const [reopening, setReopening] = useState<string | null>(null); const [history, setHistory] = useState<string | null>(null);
  const opened = items.find(item => item.period.accountingPeriodId === periodId);
  const openClose = (close: PeriodClose) => go({ period: close.periodId, close: close.closeNo });
  async function startClose(item: PeriodBoardItem) {
    const close = await command.run(workflowPaths.periodCloses(item.period.accountingPeriodId), undefined,
      decodePeriodCloseResult(item.period.accountingPeriodId, ['PENDING_CHECK', 'FAILED', 'RUNNING']), t('periodClose.startFailed'));
    if (!close) return;
    void message[close.status === 'FAILED' ? 'warning' : 'success'](close.status === 'FAILED' ? t('periodClose.startNotPassed') : t('periodClose.started'));
    board.reload(); openClose(close);
  }
  const calendarLabel = (item: FiscalCalendar) => `${entityLabel(item.entityType, item.entityId, entities.byId)} / ${item.calendarCode}`;
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_PERIOD_CLOSE')}</h1></div>
    <Form layout="vertical" name="finance-close-calendar"><div className="finance-trial-head">
      <Form.Item label={t('periodClose.calendar')}><Select showSearch={{ optionFilterProp: 'label' }} placeholder={t('periodClose.selectCalendar')} loading={calendars.loading} value={calendar?.fiscalCalendarId}
        onChange={value => go({ calendar: value, period: null, close: null })} options={calendars.items.map(item => ({ value: item.fiscalCalendarId, label: calendarLabel(item) }))} /></Form.Item>
      {calendar && <Form.Item><Button onClick={board.reload} loading={board.loading}>{t('common.refresh')}</Button></Form.Item>}
    </div></Form>
    {command.saving && <Alert type="info" showIcon className="finance-banner" title={t('periodClose.running')} />}
    <CommandFeedback command={command} onReload={board.reload} />
    {calendars.error ? <LoadFailure message={calendars.error} onRetry={calendars.reload} />
      : !calendar ? <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('periodClose.selectCalendarHint')} /></div>
      : board.error ? <LoadFailure message={board.error} onRetry={board.reload} />
      : <section className="table-panel">
        <Table<PeriodBoardItem> rowKey={row => row.period.accountingPeriodId} tableLayout="fixed" dataSource={items} loading={board.loading} pagination={false}
          scroll={{ x: 1180 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('calendars.periodsEmpty')} /> }} columns={[
            { title: t('modals.period'), width: 130, render: (_, row) => periodLabel(row.period) },
            { title: t('periodClose.column.dates'), width: 200, render: (_, row) => `${row.period.startDate} — ${row.period.endDate}` },
            { title: t('periodClose.column.periodStatus'), width: 100, render: (_, row) => <WorkflowTag status={row.period.status} enumType="periodStatus" /> },
            { title: t('calendars.column.reopenCount'), width: 80, render: (_, row) => row.period.reopenCount },
            { title: t('periodClose.column.latest'), width: 230, render: (_, row) => row.latestClose ? <a onClick={() => openClose(row.latestClose!)}>
              {t('periodClose.latestRecord', { type: enumLabel(t, 'enum.periodCloseType', row.latestClose.closeType), closeNo: row.latestClose.closeNo })} <WorkflowTag status={row.latestClose.status} enumType="periodCloseStatus" /></a>
              : <span className="muted">—</span> },
            { title: t('periodClose.column.initiatorTime'), width: 220, render: (_, row) => row.latestClose ? <div><ActorText value={row.latestClose.makerId} actorId={actorId} />
              <div className="cell-meta">{localTime(row.latestClose.createTime)}</div></div> : '—' },
            { title: t('field.actions'), width: 240, render: (_, row) => <Space size={4} wrap>
              {canManage && row.period.accountingPeriodId === closable && <Popconfirm title={t('periodClose.confirmStartTitle', { period: periodLabel(row.period) })}
                description={t('periodClose.confirmStartDescription')} okText={t('periodClose.start')} cancelText={t('common.cancel')} disabled={command.saving}
                onConfirm={() => void startClose(row)}><Button size="small" type="primary" loading={command.saving}>{t('periodClose.startClose')}</Button></Popconfirm>}
              {canManage && row.period.accountingPeriodId === reopenable && <Button size="small" danger disabled={command.saving}
                onClick={() => setReopening(row.period.accountingPeriodId)}>{t('enum.periodCloseType.REOPEN')}</Button>}
              {row.latestClose && <Button size="small" type="link" onClick={() => setHistory(row.period.accountingPeriodId)}>{t('periodClose.allRecords')}</Button>}
            </Space> },
          ]} />
      </section>}
    <FinanceNote>{t('periodClose.pageNote')}{!canManage && ' ' + t('periodClose.noInitiatePermission')}{!canApprove && ' ' + t('adjustments.noCheckerPermission')}</FinanceNote>
    {reopening && (() => { const item = items.find(value => value.period.accountingPeriodId === reopening);
      return item && <ReopenPeriodModal period={item.period} onClose={() => setReopening(null)} onAuthorizationChanged={onAuthorizationChanged}
        onStarted={close => { setReopening(null); board.reload(); openClose(close); }} />; })()}
    {history && (() => { const item = items.find(value => value.period.accountingPeriodId === history);
      return item && <PeriodCloseHistoryDrawer period={item.period} actorId={actorId} onClose={() => setHistory(null)}
        onOpen={no => { setHistory(null); go({ period: item.period.accountingPeriodId, close: no }); }} />; })()}
    {opened && closeNo && /^[1-9][0-9]{0,8}$/.test(closeNo) && <PeriodCloseDrawer key={`${opened.period.accountingPeriodId}:${closeNo}`} period={opened.period}
      closeNo={closeNo} books={bookMap} entities={entities.byId} canApprove={canApprove} actorId={actorId} onClose={() => go({ period: null, close: null })}
      onReviewed={board.reload} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
