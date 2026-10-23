import { useMemo, useState } from 'react';
import { Button, Empty, Form, Input, Select, Table } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { decimalIdPattern, localTime, pageOf, replaceQuery } from './finance';
import type { AccountingPeriod, LedgerBook } from './finance';
import { decodeManualAdjustmentSummary, periodLabel, workflowPaths } from './financeWorkflow';
import type { ManualAdjustmentSummary } from './financeWorkflow';
import { Amount, FinanceNote, LoadFailure } from './FinanceParts';
import { CreateManualAdjustmentDrawer, ManualAdjustmentDrawer } from './ManualAdjustmentDrawers';
import { bookTitle } from './SetupBooksTab';
import { useBookPeriods, useCurrencies, useLedgerBooks } from './useFinanceData';
import { ActorText, WorkflowTag } from './WorkflowParts';

interface Props { canManage: boolean; canApprove: boolean; actorId: string; onAuthorizationChanged: () => void }

const listStatuses = ['SUBMITTED', 'POSTED', 'REJECTED'] as const;

/**
 * 「人工调整」页签：账簿选择 → 列表（期间、状态、制单人筛选，分页）→ 制单抽屉 / 详情抽屉
 * <li>账簿与打开的调整存于 URL（?book=&adjustment=）；只列非 Owner 科目的总账调整，Owner 余额走「计费事实」。</li>
 */
export function ManualAdjustmentsTab({ canManage, canApprove, actorId, onAuthorizationChanged }: Props) {
  const t = useT('finance');
  const [params, setParams] = useSearchParams();
  const books = useLedgerBooks(onAuthorizationChanged);
  const bookId = params.get('book'); const adjustmentId = params.get('adjustment');
  const book = books.items.find(item => item.ledgerBookId === bookId);
  const periods = useBookPeriods(book, onAuthorizationChanged);
  const currencies = useCurrencies(onAuthorizationChanged);
  const [creating, setCreating] = useState(false); const [reloadToken, setReloadToken] = useState(0);
  const go = (updates: Record<string, string | null>) => setParams(new URLSearchParams(replaceQuery(params.toString(), updates)));
  const minorUnit = book ? currencies.minorUnits.get(book.currency) : undefined;
  return <>
    <Form layout="vertical" name="finance-adjustment-book"><div className="finance-trial-head">
      <Form.Item label={t('field.book')}><Select showSearch={{ optionFilterProp: 'label' }} placeholder={t('trial.selectBook')} loading={books.loading} value={book?.ledgerBookId}
        onChange={value => go({ book: value, adjustment: null })} options={books.items.map(item => ({ value: item.ledgerBookId, label: `${bookTitle(item)} · ${item.chartCode}` }))} /></Form.Item>
      <span className="finance-spacer" />
      {canManage && book && <Form.Item><Button type="primary" onClick={() => setCreating(true)}>{t('adjustments.create')}</Button></Form.Item>}
    </div></Form>
    {books.error ? <LoadFailure message={books.error} onRetry={books.reload} />
      : !book ? <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('adjustments.selectBook')} /></div>
      : <AdjustmentList key={`${book.ledgerBookId}:${reloadToken}`} book={book} periods={periods.items} actorId={actorId} minorUnit={minorUnit}
        onOpen={id => go({ adjustment: id })} />}
    <FinanceNote>{t('adjustments.note')}{!canManage && ' ' + t('adjustments.noMakerPermission')}{!canApprove && ' ' + t('adjustments.noCheckerPermission')}</FinanceNote>
    {creating && book && <CreateManualAdjustmentDrawer book={book} periods={periods.items} minorUnit={minorUnit} onClose={() => setCreating(false)}
      onCreated={id => { setCreating(false); setReloadToken(value => value + 1); go({ adjustment: id }); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {book && adjustmentId && decimalIdPattern.test(adjustmentId) && <ManualAdjustmentDrawer book={book} manualAdjustmentId={adjustmentId} periods={periods.items}
      minorUnit={minorUnit} canApprove={canApprove} actorId={actorId} onClose={() => go({ adjustment: null })}
      onReviewed={() => setReloadToken(value => value + 1)} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

function AdjustmentList({ book, periods, actorId, minorUnit, onOpen }: {
  book: LedgerBook; periods: readonly AccountingPeriod[]; actorId: string; minorUnit?: number; onOpen: (manualAdjustmentId: string) => void;
}) {
  const t = useT('finance');
  const [form] = Form.useForm<{ accountingPeriodId?: string; status?: string; makerId?: string }>();
  const statusOptions = useMemo(() => listStatuses.map(value => ({ value, label: enumLabel(t, 'enum.adjustmentStatus', value) })), [t]);
  const decode = useMemo(() => pageOf(decodeManualAdjustmentSummary(book.ledgerBookId)), [book.ledgerBookId]);
  const base = { ledgerBookId: book.ledgerBookId };
  const page = usePaging<ManualAdjustmentSummary>(workflowPaths.manualAdjustments, decode, base);
  const periodMap = useMemo(() => new Map(periods.map(item => [item.accountingPeriodId, item])), [periods]);
  const search = (values: { accountingPeriodId?: string; status?: string; makerId?: string }) => {
    const filters: Record<string, string> = { ...base };
    if (values.accountingPeriodId) filters.accountingPeriodId = values.accountingPeriodId;
    if (values.status) filters.status = values.status;
    if (values.makerId?.trim()) filters.makerId = values.makerId.trim();
    page.applyFilters(filters);
  };
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  return <>
    <Form form={form} name="finance-adjustment-filters" layout="vertical" onFinish={search}><div className="finance-search">
      <Form.Item name="accountingPeriodId" label={t('field.accountingPeriod')}><Select allowClear placeholder={t('placeholder.all')} options={periods.map(item => ({ value: item.accountingPeriodId, label: periodLabel(item) }))} /></Form.Item>
      <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('placeholder.all')} options={statusOptions} /></Form.Item>
      <Form.Item name="makerId" label={t('workflow.maker')} rules={[{ pattern: decimalIdPattern, message: t('workflow.makerIdPattern') }]}>
        <Input allowClear maxLength={19} placeholder={t('workflow.makerIdPlaceholder')} /></Form.Item>
      <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
        <Button onClick={() => { form.setFieldsValue({ makerId: actorId }); form.submit(); }}>{t('workflow.onlyMine')}</Button>
        <Button onClick={() => { form.resetFields(); page.applyFilters(base); }}>{t('common.reset')}</Button></div>
    </div></Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ManualAdjustmentSummary> rowKey="manualAdjustmentId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1180 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }} onRow={row => ({ onClick: () => onOpen(row.manualAdjustmentId), className: 'clickable-row' })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('adjustments.emptyFiltered')} /> }} columns={[
          { title: t('adjustments.column.adjustmentNo'), width: 190, render: (_, row) => <a className="mono">{row.adjustmentNo}</a> },
          { title: t('field.accountingPeriod'), width: 140, render: (_, row) => { const period = periodMap.get(row.accountingPeriodId); return period ? periodLabel(period) : row.accountingPeriodId; } },
          { title: t('adjustments.column.reason'), width: 130, render: (_, row) => enumLabel(t, 'enum.adjustmentReason', row.reasonCode) },
          { title: t('field.debitTotal'), width: 140, align: 'right', render: (_, row) => amount(row.totalDebit) },
          { title: t('field.creditTotal'), width: 140, align: 'right', render: (_, row) => amount(row.totalCredit) },
          { title: t('field.status'), width: 100, render: (_, row) => <WorkflowTag status={row.status} enumType="adjustmentStatus" /> },
          { title: t('workflow.maker'), width: 200, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
          { title: t('workflow.submittedAt'), width: 150, render: (_, row) => localTime(row.submittedTime) },
        ]} />
    </section>}
  </>;
}
