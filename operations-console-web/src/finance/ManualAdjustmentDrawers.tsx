import { useMemo } from 'react';
import { App, Button, Descriptions, Drawer, Form, Input, Select, Spin, Alert, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { journalTotals, localTime, sideLabels } from './finance';
import type { AccountingPeriod, LedgerAccount, LedgerBook } from './finance';
import {
  adjustableAccountOptions, adjustmentReasonLabels, adjustmentStatusLabels, amountError, canReview, decodeAdjustmentReview, decodeManualAdjustmentDetail,
  decodeSubmittedAdjustment, draftTotals, journalEntryLink, ledgerWorkflowErrorTargets, manualAdjustmentBody, openPeriods, periodLabel, workflowPaths,
} from './financeWorkflow';
import type { AdjustmentFormValues, AdjustmentLineValues, ManualAdjustmentDetail, ManualAdjustmentLine } from './financeWorkflow';
import { Amount, FinanceNote } from './FinanceParts';
import { bookTitle } from './SetupBooksTab';
import { useFinanceCommand } from './useFinanceCommand';
import { useBookAccounts, useChartAccounts } from './useFinanceData';
import { ActorText, ApprovalTable, CommandFeedback, HashText, ReviewActions, useDetail, WorkflowTag } from './WorkflowParts';

const reasonOptions = Object.entries(adjustmentReasonLabels).map(([value, label]) => ({ value, label: `${label}（${value}）` }));
const sideOptions = (['DEBIT', 'CREDIT'] as const).map(value => ({ value, label: sideLabels[value] }));
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
  const { message } = App.useApp();
  const [form] = Form.useForm<AdjustmentFormValues>();
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
      decodeSubmittedAdjustment, '提交人工调整失败');
    if (!result) return;
    void message.success(`已提交 ${result.adjustmentNo}，等待复核`);
    onCreated(result.manualAdjustmentId);
  }
  return <Drawer open size="min(1080px, 100vw)" rootClassName="finance-drawer finance-page" title={`新建人工调整 · ${bookTitle(book)}`}
    onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden footer={<div className="finance-drawer-footer">
      <span className="finance-entry-totals">借方合计 <Amount value={totals.debit} minorUnit={minorUnit} /> · 贷方合计 <Amount value={totals.credit} minorUnit={minorUnit} />
        <Tag color={totals.balanced ? 'green' : 'red'}>{totals.balanced ? '借贷平衡' : totals.complete ? `差额 ${totals.difference}` : '分录未填写完整'}</Tag></span>
      <span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>取消</Button>
      <Button type="primary" loading={command.saving} disabled={!totals.balanced || command.conflict} onClick={() => void submit()}>提交复核</Button>
    </div>}>
    <CommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="finance-create-adjustment" layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ lines: [emptyLine('DEBIT'), emptyLine('CREDIT')] }}>
      <div className="finance-form-grid">
        <Form.Item name="accountingPeriodId" label="会计期间" rules={[{ required: true, message: '请选择开放期间' }]}
          extra={!open.length ? '该账簿主体没有开放期间，不能制单' : '只列开放期间'}>
          <Select placeholder="选择开放期间" options={open.map(item => ({ value: item.accountingPeriodId, label: `${periodLabel(item)} · ${item.startDate} — ${item.endDate}` }))} /></Form.Item>
        <Form.Item name="reasonCode" label="调整原因" rules={[{ required: true, message: '请选择调整原因' }]}><Select placeholder="选择原因" options={reasonOptions} /></Form.Item>
      </div>
      <h3 className="finance-subtitle">分录行 · {book.currency}</h3>
      <Form.List name="lines" rules={[{ validator: async (_, value: AdjustmentLineValues[]) => { if (!value || value.length < 2) throw new Error('至少需要两行分录'); } }]}>
        {(fields, { add, remove }, { errors }) => <>
          {fields.map(field => <div key={field.key} className="finance-line-row finance-adjustment-line">
            <Form.Item name={[field.name, 'accountId']} label={field.name === 0 ? '账户' : undefined} rules={[{ required: true, message: '请选择账户' }]}>
              <Select showSearch={{ optionFilterProp: 'label' }} placeholder="账户号 / 科目编号 / 名称" loading={accounts.loading || chartAccounts.loading}
                options={options.map(({ value, label }) => ({ value, label }))} notFoundContent={accounts.error || chartAccounts.error ? '账户加载失败' : '没有可调整的账户'} /></Form.Item>
            <Form.Item name={[field.name, 'entrySide']} label={field.name === 0 ? '方向' : undefined} rules={[{ required: true, message: '请选择' }]}>
              <Select options={sideOptions} /></Form.Item>
            <Form.Item name={[field.name, 'amount']} label={field.name === 0 ? '金额' : undefined}
              rules={[{ validator: async (_, value?: string) => { const error = amountError(value, minorUnit); if (error) throw new Error(error); } }]}>
              <Input inputMode="decimal" placeholder="0.00" /></Form.Item>
            <Form.Item name={[field.name, 'lineDescription']} label={field.name === 0 ? '说明（可写外部单据号）' : undefined}
              rules={[{ required: true, whitespace: true, message: '请填写说明' }]}><Input maxLength={512} /></Form.Item>
            <Button type="text" danger aria-label="删除行" className={field.name === 0 ? 'finance-line-remove is-first' : 'finance-line-remove'}
              disabled={fields.length <= 2} onClick={() => remove(field.name)}>删除</Button>
          </div>)}
          <Form.ErrorList errors={errors} />
          <Button type="dashed" disabled={fields.length >= 500} onClick={() => add(emptyLine('DEBIT'))}>添加分录行</Button>
        </>}
      </Form.List>
    </Form>
    <FinanceNote>账户列表已排除 Client / Partner 控制科目、停用账户与停用或不可记账科目；金额精度不超过币种小数位。提交时冻结内容摘要，复核人须确认同一摘要。</FinanceNote>
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
  const { message } = App.useApp();
  const decode = useMemo(() => decodeManualAdjustmentDetail(manualAdjustmentId), [manualAdjustmentId]);
  const detail = useDetail<ManualAdjustmentDetail>(workflowPaths.manualAdjustment(manualAdjustmentId), decode, onAuthorizationChanged, '人工调整详情加载失败');
  const accounts = useBookAccounts(book.ledgerBookId, onAuthorizationChanged);
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], ledgerWorkflowErrorTargets);
  const item = detail.item;
  async function review(action: 'approve' | 'reject') {
    if (!item) return;
    const result = await command.run(workflowPaths.reviewManualAdjustment(item.manualAdjustmentId, action), { approvedContentHash: item.contentHash },
      decodeAdjustmentReview(item.manualAdjustmentId, action), action === 'approve' ? '审批人工调整失败' : '驳回人工调整失败');
    if (!result) return;
    void message.success(action === 'approve' ? `已过账，分录序号 ${result.journalSequence}` : '已驳回，不生成分录');
    detail.reload(); onReviewed();
  }
  const period = item && periods.find(value => value.accountingPeriodId === item.accountingPeriodId);
  const totals = item ? journalTotals(item.lines) : null;
  const reviewable = !!item && canReview(canApprove, item.status, 'SUBMITTED', item.makerId, actorId);
  const selfNote = item && canApprove && item.status === 'SUBMITTED' && item.makerId === actorId ? '你是制单人，不能复核自己提交的调整' : undefined;
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  return <Drawer open size={860} rootClassName="finance-drawer finance-page" onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden
    title={`人工调整${item ? ` · ${item.adjustmentNo}` : ''}`}>
    {detail.error ? <Alert type="warning" showIcon title={detail.error} /> : !item || !totals ? <Spin /> : <>
      <CommandFeedback command={command} onReload={detail.reload} />
      <Descriptions column={2} size="small" className="finance-detail-head" items={[
        { key: 'status', label: '状态', children: <WorkflowTag status={item.status} labels={adjustmentStatusLabels} /> },
        { key: 'book', label: '账簿', children: bookTitle(book) },
        { key: 'period', label: '会计期间', children: period ? `${periodLabel(period)}（${period.status}）` : item.accountingPeriodId },
        { key: 'reason', label: '原因', children: adjustmentReasonLabels[item.reasonCode] ?? item.reasonCode },
        { key: 'maker', label: '制单人', children: <ActorText value={item.makerId} actorId={actorId} /> },
        { key: 'time', label: '提交时间', children: localTime(item.submittedTime) },
        { key: 'hash', label: '内容摘要', children: <HashText value={item.contentHash} /> },
        { key: 'journal', label: '过账分录', children: item.journalEntryId && item.lines[0]
          ? <Link to={journalEntryLink(book.ledgerBookId, item.lines[0].accountId, item.journalEntryId)}>序号 {item.journalSequence} · {item.postingDate} ↗</Link> : '—' },
      ]} />
      <section className="table-panel finance-entry-lines">
        <Table<ManualAdjustmentLine> rowKey="lineNo" size="small" tableLayout="fixed" dataSource={item.lines} pagination={false} scroll={{ x: 640 }} columns={[
          { title: '行号', dataIndex: 'lineNo', width: 56 },
          { title: '账户', render: (_, row) => accountLabel(row.accountId, accounts.byId) },
          { title: '方向', width: 56, render: (_, row) => sideLabels[row.entrySide] },
          { title: '金额', width: 130, align: 'right', render: (_, row) => amount(row.amount) },
          { title: '说明', dataIndex: 'lineDescription', width: 200 },
        ]} />
      </section>
      <div className="finance-entry-totals">
        <span>借方合计 {amount(totals.debit)}</span><span>贷方合计 {amount(totals.credit)}</span>
        <Tag color={totals.balanced ? 'green' : 'red'}>{totals.balanced ? '借贷平衡' : '借贷不平衡'}</Tag>
      </div>
      <h3 className="finance-subtitle">审批记录</h3>
      <ApprovalTable approvals={item.approvals} actorId={actorId} />
      <ReviewActions saving={command.saving} note={selfNote} actions={reviewable ? [
        { key: 'reject', label: '驳回', danger: true, confirm: '驳回后不生成分录，制单人需重新提交。确认驳回？', onConfirm: () => void review('reject') },
        { key: 'approve', label: '审批过账', primary: true, confirm: `确认内容摘要 ${item.contentHash.slice(0, 16)}… 并过账？`, onConfirm: () => void review('approve') },
      ] : []} />
    </>}
  </Drawer>;
}
