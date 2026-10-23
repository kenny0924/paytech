import { useMemo } from 'react';
import { App, Button, Descriptions, Drawer, Form, Input, Select, Spin, Alert, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { journalTotals, localTime } from './finance';
import type { AccountingPeriod, LedgerAccount, LedgerBook } from './finance';
import {
  adjustableAccountOptions, adjustmentReasonCodes, amountError, canReview, decodeAdjustmentReview, decodeManualAdjustmentDetail,
  decodeSubmittedAdjustment, draftTotals, journalEntryLink, ledgerWorkflowErrorTargets, manualAdjustmentBody, openPeriods, periodLabel, workflowPaths,
} from './financeWorkflow';
import type { AdjustmentFormValues, AdjustmentLineValues, ManualAdjustmentDetail, ManualAdjustmentLine } from './financeWorkflow';
import { Amount, FinanceNote } from './FinanceParts';
import { bookTitle } from './SetupBooksTab';
import { useFinanceCommand } from './useFinanceCommand';
import { useBookAccounts, useChartAccounts } from './useFinanceData';
import { ActorText, ApprovalTable, CommandFeedback, HashText, ReviewActions, useDetail, WorkflowTag } from './WorkflowParts';

const emptyLine = (entrySide: 'DEBIT' | 'CREDIT'): AdjustmentLineValues => ({ entrySide });
const formFields = ['accountingPeriodId', 'reasonCode', 'lines'];

/** 账户展示：账户号 + 科目代码 · 名称；账户列表未加载到时显示主键。 */
function accountLabel(accountId: string, accounts: ReadonlyMap<string, LedgerAccount>) {
  const account = accounts.get(accountId);
  return account ? <div><div className="mono">{account.accountNo}</div><div className="cell-meta">{account.accountCode} · {account.accountName}</div></div>
    : <span className="mono">{accountId}</span>;
}

/**
 * 制单抽屉：期间只列开放期间，原因固定下拉；分录行账户按「账户号 · 科目编号 名称」选择（排除控制科目与停用科目）
 * <li>借贷合计随输入实时计算，不平衡或行不完整时禁止提交；外部单据号写在行说明中。</li>
 */
export function CreateManualAdjustmentDrawer({ book, periods, minorUnit, onClose, onCreated, onAuthorizationChanged }: {
  book: LedgerBook; periods: readonly AccountingPeriod[]; minorUnit?: number; onClose: () => void; onCreated: (manualAdjustmentId: string) => void;
  onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const { message } = App.useApp();
  const [form] = Form.useForm<AdjustmentFormValues>();
  const reasonOptions = useMemo(() => adjustmentReasonCodes.map(value =>
    ({ value, label: t('common.codeWithLabel', { code: enumLabel(t, 'enum.adjustmentReason', value), label: value }) })), [t]);
  const sideOptions = useMemo(() => (['DEBIT', 'CREDIT'] as const).map(value => ({ value, label: enumLabel(t, 'enum.side', value) })), [t]);
  const command = useFinanceCommand(onAuthorizationChanged, form, formFields, ledgerWorkflowErrorTargets);
  const accounts = useBookAccounts(book.ledgerBookId, onAuthorizationChanged);
  const chartAccounts = useChartAccounts(book.chartCode, {}, onAuthorizationChanged);
  const options = useMemo(() => adjustableAccountOptions(accounts.items, chartAccounts.items), [accounts.items, chartAccounts.items]);
  const lines = (Form.useWatch('lines', form) ?? []) as AdjustmentLineValues[];
  const totals = draftTotals(lines, minorUnit);
  const open = openPeriods(periods);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(workflowPaths.manualAdjustments, manualAdjustmentBody(values, book.ledgerBookId, book.currency),
      decodeSubmittedAdjustment, t('adjustments.submitFailed'));
    if (!result) return;
    void message.success(t('adjustments.submitted', { adjustmentNo: result.adjustmentNo }));
    onCreated(result.manualAdjustmentId);
  }
  return <Drawer open size="min(1080px, 100vw)" rootClassName="finance-drawer finance-page" title={t('adjustments.createTitle', { book: bookTitle(book) })}
    onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden footer={<div className="finance-drawer-footer">
      <span className="finance-entry-totals">{t('field.debitTotal')} <Amount value={totals.debit} minorUnit={minorUnit} /> · {t('field.creditTotal')} <Amount value={totals.credit} minorUnit={minorUnit} />
        <Tag color={totals.balanced ? 'green' : 'red'}>{totals.balanced ? t('journal.balanced') : totals.complete ? t('adjustments.difference', { difference: totals.difference }) : t('adjustments.incomplete')}</Tag></span>
      <span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>{t('common.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={!totals.balanced || command.conflict} onClick={() => void submit()}>{t('workflow.submitForReview')}</Button>
    </div>}>
    <CommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="finance-create-adjustment" layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ lines: [emptyLine('DEBIT'), emptyLine('CREDIT')] }}>
      <div className="finance-form-grid">
        <Form.Item name="accountingPeriodId" label={t('field.accountingPeriod')} rules={[{ required: true, message: t('adjustments.openPeriodRequired') }]}
          extra={!open.length ? t('adjustments.noOpenPeriod') : t('adjustments.openPeriodsOnly')}>
          <Select placeholder={t('adjustments.openPeriodPlaceholder')} options={open.map(item => ({ value: item.accountingPeriodId, label: `${periodLabel(item)} · ${item.startDate} — ${item.endDate}` }))} /></Form.Item>
        <Form.Item name="reasonCode" label={t('adjustments.field.reason')} rules={[{ required: true, message: t('adjustments.reasonRequired') }]}><Select placeholder={t('adjustments.reasonPlaceholder')} options={reasonOptions} /></Form.Item>
      </div>
      <h3 className="finance-subtitle">{t('adjustments.linesTitle', { currency: book.currency })}</h3>
      <Form.List name="lines" rules={[{ validator: async (_, value: AdjustmentLineValues[]) => { if (!value || value.length < 2) throw new Error(t('adjustments.twoLinesRequired')); } }]}>
        {(fields, { add, remove }, { errors }) => <>
          {fields.map(field => <div key={field.key} className="finance-line-row finance-adjustment-line">
            <Form.Item name={[field.name, 'accountId']} label={field.name === 0 ? t('adjustments.field.account') : undefined} rules={[{ required: true, message: t('adjustments.accountRequired') }]}>
              <Select showSearch={{ optionFilterProp: 'label' }} placeholder={t('adjustments.accountPlaceholder')} loading={accounts.loading || chartAccounts.loading}
                options={options.map(({ value, label }) => ({ value, label }))} notFoundContent={accounts.error || chartAccounts.error ? t('data.accountsLoadFailed') : t('adjustments.noAccounts')} /></Form.Item>
            <Form.Item name={[field.name, 'entrySide']} label={field.name === 0 ? t('field.side') : undefined} rules={[{ required: true, message: t('validation.selectRequired') }]}>
              <Select options={sideOptions} /></Form.Item>
            <Form.Item name={[field.name, 'amount']} label={field.name === 0 ? t('field.amount') : undefined}
              rules={[{ validator: async (_, value?: string) => { const error = amountError(value, minorUnit); if (error) throw new Error(error); } }]}>
              <Input inputMode="decimal" placeholder="0.00" /></Form.Item>
            <Form.Item name={[field.name, 'lineDescription']} label={field.name === 0 ? t('adjustments.field.description') : undefined}
              rules={[{ required: true, whitespace: true, message: t('adjustments.descriptionRequired') }]}><Input maxLength={512} /></Form.Item>
            <Button type="text" danger aria-label={t('workflow.removeLine')} className={field.name === 0 ? 'finance-line-remove is-first' : 'finance-line-remove'}
              disabled={fields.length <= 2} onClick={() => remove(field.name)}>{t('workflow.remove')}</Button>
          </div>)}
          <Form.ErrorList errors={errors} />
          <Button type="dashed" disabled={fields.length >= 500} onClick={() => add(emptyLine('DEBIT'))}>{t('adjustments.addLine')}</Button>
        </>}
      </Form.List>
    </Form>
    <FinanceNote>{t('adjustments.createNote')}</FinanceNote>
  </Drawer>;
}

/**
 * 人工调整详情：分录行、借贷合计、内容摘要、审批记录与分录跳转
 * <li>有复核权限且不是制单人时显示审批 / 驳回，回传内容摘要；制单人本人只看到说明。</li>
 */
export function ManualAdjustmentDrawer({ book, manualAdjustmentId, periods, minorUnit, canApprove, actorId, onClose, onReviewed, onAuthorizationChanged }: {
  book: LedgerBook; manualAdjustmentId: string; periods: readonly AccountingPeriod[]; minorUnit?: number; canApprove: boolean; actorId: string;
  onClose: () => void; onReviewed: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const { message } = App.useApp();
  const decode = useMemo(() => decodeManualAdjustmentDetail(manualAdjustmentId), [manualAdjustmentId]);
  const detail = useDetail<ManualAdjustmentDetail>(workflowPaths.manualAdjustment(manualAdjustmentId), decode, onAuthorizationChanged, t('adjustments.detailLoadFailed'));
  const accounts = useBookAccounts(book.ledgerBookId, onAuthorizationChanged);
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], ledgerWorkflowErrorTargets);
  const item = detail.item;
  async function review(action: 'approve' | 'reject') {
    if (!item) return;
    const result = await command.run(workflowPaths.reviewManualAdjustment(item.manualAdjustmentId, action), { approvedContentHash: item.contentHash },
      decodeAdjustmentReview(item.manualAdjustmentId, action), action === 'approve' ? t('adjustments.approveFailed') : t('adjustments.rejectFailed'));
    if (!result) return;
    void message.success(action === 'approve' ? t('adjustments.posted', { sequence: result.journalSequence }) : t('adjustments.rejected'));
    detail.reload(); onReviewed();
  }
  const period = item && periods.find(value => value.accountingPeriodId === item.accountingPeriodId);
  const totals = item ? journalTotals(item.lines) : null;
  const reviewable = !!item && canReview(canApprove, item.status, 'SUBMITTED', item.makerId, actorId);
  const selfNote = item && canApprove && item.status === 'SUBMITTED' && item.makerId === actorId ? t('adjustments.selfReview') : undefined;
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  return <Drawer open size={860} rootClassName="finance-drawer finance-page" onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden
    title={item ? t('adjustments.detailTitle', { adjustmentNo: item.adjustmentNo }) : t('journal.field.manualAdjustment')}>
    {detail.error ? <Alert type="warning" showIcon title={detail.error} /> : !item || !totals ? <Spin /> : <>
      <CommandFeedback command={command} onReload={detail.reload} />
      <Descriptions column={2} size="small" className="finance-detail-head" items={[
        { key: 'status', label: t('field.status'), children: <WorkflowTag status={item.status} enumType="adjustmentStatus" /> },
        { key: 'book', label: t('field.book'), children: bookTitle(book) },
        { key: 'period', label: t('field.accountingPeriod'), children: period ? t('common.labelWithId', { label: periodLabel(period), id: period.status }) : item.accountingPeriodId },
        { key: 'reason', label: t('adjustments.column.reason'), children: enumLabel(t, 'enum.adjustmentReason', item.reasonCode) },
        { key: 'maker', label: t('workflow.maker'), children: <ActorText value={item.makerId} actorId={actorId} /> },
        { key: 'time', label: t('workflow.submittedAt'), children: localTime(item.submittedTime) },
        { key: 'hash', label: t('trial.field.contentHash'), children: <HashText value={item.contentHash} /> },
        { key: 'journal', label: t('adjustments.field.journal'), children: item.journalEntryId && item.lines[0]
          ? <Link to={journalEntryLink(book.ledgerBookId, item.lines[0].accountId, item.journalEntryId)}>{t('adjustments.journalLink', { sequence: String(item.journalSequence), postingDate: String(item.postingDate) })}</Link> : '—' },
      ]} />
      <section className="table-panel finance-entry-lines">
        <Table<ManualAdjustmentLine> rowKey="lineNo" size="small" tableLayout="fixed" dataSource={item.lines} pagination={false} scroll={{ x: 640 }} columns={[
          { title: t('field.lineNo'), dataIndex: 'lineNo', width: 56 },
          { title: t('adjustments.field.account'), render: (_, row) => accountLabel(row.accountId, accounts.byId) },
          { title: t('field.side'), width: 56, render: (_, row) => enumLabel(t, 'enum.side', row.entrySide) },
          { title: t('field.amount'), width: 130, align: 'right', render: (_, row) => amount(row.amount) },
          { title: t('adjustments.column.description'), dataIndex: 'lineDescription', width: 200 },
        ]} />
      </section>
      <div className="finance-entry-totals">
        <span>{t('field.debitTotal')} {amount(totals.debit)}</span><span>{t('field.creditTotal')} {amount(totals.credit)}</span>
        <Tag color={totals.balanced ? 'green' : 'red'}>{totals.balanced ? t('journal.balanced') : t('journal.unbalanced')}</Tag>
      </div>
      <h3 className="finance-subtitle">{t('workflow.approvalsTitle')}</h3>
      <ApprovalTable approvals={item.approvals} actorId={actorId} />
      <ReviewActions saving={command.saving} note={selfNote} actions={reviewable ? [
        { key: 'reject', label: t('workflow.reject'), danger: true, confirm: t('adjustments.confirmReject'), onConfirm: () => void review('reject') },
        { key: 'approve', label: t('adjustments.approve'), primary: true, confirm: t('adjustments.confirmApprove', { hash: item.contentHash.slice(0, 16) }), onConfirm: () => void review('approve') },
      ] : []} />
    </>}
  </Drawer>;
}
