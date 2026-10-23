import { useMemo } from 'react';
import { Alert, App, Descriptions, Drawer, Empty, Form, Input, Modal, Select, Spin, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { decodeTrialBalanceRun, entityLabel, financePaths, localTime, pageOf } from './finance';
import type { AccountingPeriod, FinanceEntity, LedgerBook, TrialBalanceRun } from './finance';
import {
  canReview, decodePeriodClose, decodePeriodCloseDetail, decodePeriodCloseResult, failedStep, ledgerWorkflowErrorTargets,
  periodLabel, reopenBody, reopenReasonCodes, trialRunLink, workflowPaths,
} from './financeWorkflow';
import type { PeriodClose, PeriodCloseBook, PeriodCloseDetail, PeriodCloseStep, ReopenFormValues } from './financeWorkflow';
import { FinanceNote, LoadFailure } from './FinanceParts';
import { useFinanceCommand } from './useFinanceCommand';
import { ActorText, ApprovalTable, CommandFeedback, HashText, ReviewActions, useDetail, WorkflowTag } from './WorkflowParts';

const reopenFields = ['reasonCode', 'requestRef'];
const runsPage = (ledgerBookId: string, periodId: string) => pageOf(decodeTrialBalanceRun(ledgerBookId, periodId));

/** 重开申请：原因下拉 + 申请单号；成功返回重开流程（待复核或失败）。 */
export function ReopenPeriodModal({ period, onClose, onStarted, onAuthorizationChanged }: {
  period: AccountingPeriod; onClose: () => void; onStarted: (close: PeriodClose) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const [form] = Form.useForm<ReopenFormValues>();
  const reasonOptions = useMemo(() => reopenReasonCodes.map(value =>
    ({ value, label: t('common.codeWithLabel', { code: enumLabel(t, 'enum.reopenReason', value), label: value }) })), [t]);
  const command = useFinanceCommand(onAuthorizationChanged, form, reopenFields, ledgerWorkflowErrorTargets);
  async function submit() {
    const values = await form.validateFields();
    const close = await command.run(workflowPaths.periodReopens(period.accountingPeriodId), reopenBody(values),
      decodePeriodCloseResult(period.accountingPeriodId, ['PENDING_CHECK', 'FAILED', 'RUNNING']), t('periodClose.reopenFailed'));
    if (close) onStarted(close);
  }
  return <Modal open title={t('periodClose.reopenTitle', { period: periodLabel(period) })} okText={t('periodClose.startReopen')} cancelText={t('common.cancel')} confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <CommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="finance-reopen-period" layout="vertical" disabled={command.saving}>
      <Form.Item name="reasonCode" label={t('periodClose.field.reopenReason')} rules={[{ required: true, message: t('periodClose.reopenReasonRequired') }]}><Select options={reasonOptions} /></Form.Item>
      <Form.Item name="requestRef" label={t('periodClose.field.requestRef')} rules={[{ required: true, whitespace: true, message: t('periodClose.requestRefRequired') },
        { max: 128, message: t('periodClose.requestRefMax') }]}><Input maxLength={128} placeholder={t('periodClose.requestRefPlaceholder')} /></Form.Item>
    </Form>
    <FinanceNote>{t('periodClose.reopenNote')}</FinanceNote>
  </Modal>;
}

/** 期间关账 / 重开历史：分页列表，点击进入详情。 */
export function PeriodCloseHistoryDrawer({ period, actorId, onOpen, onClose }: {
  period: AccountingPeriod; actorId: string; onOpen: (closeNo: string) => void; onClose: () => void;
}) {
  const t = useT('finance');
  const decode = useMemo(() => pageOf(decodePeriodClose(period.accountingPeriodId)), [period.accountingPeriodId]);
  const page = usePaging<PeriodClose>(workflowPaths.periodCloses(period.accountingPeriodId), decode);
  return <Drawer open size={820} rootClassName="finance-drawer finance-page" title={t('periodClose.historyTitle', { period: periodLabel(period) })} onClose={onClose} destroyOnHidden>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<PeriodClose> rowKey="periodCloseId" size="small" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }} onRow={row => ({ onClick: () => onOpen(row.closeNo), className: 'clickable-row' })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('periodClose.historyEmpty')} /> }} columns={[
          { title: t('trial.column.runNo'), width: 70, render: (_, row) => <a className="mono">{row.closeNo}</a> },
          { title: t('ledger.lines.column.type'), width: 70, render: (_, row) => enumLabel(t, 'enum.periodCloseType', row.closeType) },
          { title: t('field.status'), width: 90, render: (_, row) => <WorkflowTag status={row.status} enumType="periodCloseStatus" /> },
          { title: t('periodClose.initiator'), render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
          { title: t('periodClose.startedAt'), width: 150, render: (_, row) => localTime(row.createTime) },
        ]} />
    </section>}
  </Drawer>;
}

/**
 * 关账 / 重开详情：步骤结果、失败步骤与原因、各账簿最终试算（链接到试算平衡页签）、结转指令、关账摘要与审批记录
 * <li>有复核权限且不是发起人时显示审批 / 驳回，回传关账摘要；试算异常只读（异常明细在试算记录中查看）。</li>
 */
export function PeriodCloseDrawer({ period, closeNo, books, entities, canApprove, actorId, onClose, onReviewed, onAuthorizationChanged }: {
  period: AccountingPeriod; closeNo: string; books: ReadonlyMap<string, LedgerBook>; entities?: ReadonlyMap<string, FinanceEntity>;
  canApprove: boolean; actorId: string; onClose: () => void; onReviewed: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const { message } = App.useApp();
  const periodId = period.accountingPeriodId;
  const decode = useMemo(() => decodePeriodCloseDetail(periodId, closeNo), [periodId, closeNo]);
  const detail = useDetail<PeriodCloseDetail>(workflowPaths.periodClose(periodId, closeNo), decode, onAuthorizationChanged, t('periodClose.detailLoadFailed'));
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], ledgerWorkflowErrorTargets);
  const item = detail.item;
  async function review(action: 'approve' | 'reject') {
    if (!item?.closeHash) return;
    const result = await command.run(workflowPaths.reviewPeriodClose(periodId, closeNo, action), { approvedCloseHash: item.closeHash },
      decodePeriodCloseResult(periodId, action === 'approve' ? ['COMPLETED', 'APPROVED'] : ['REJECTED']), action === 'approve' ? t('periodClose.approveFailed') : t('periodClose.rejectFailed'));
    if (!result) return;
    void message.success(action === 'approve' ? (item.closeType === 'CLOSE' ? t('periodClose.approvedClosed') : t('periodClose.approvedReopened')) : t('periodClose.rejected'));
    detail.reload(); onReviewed();
  }
  const reviewable = !!item?.closeHash && canReview(canApprove, item.status, 'PENDING_CHECK', item.makerId, actorId);
  const selfNote = item && canApprove && item.status === 'PENDING_CHECK' && item.makerId === actorId ? t('periodClose.selfReview') : undefined;
  const failed = item && item.status === 'FAILED' ? failedStep(item) : null;
  const typeLabel = item ? enumLabel(t, 'enum.periodCloseType', item.closeType) : t('enum.periodCloseType.CLOSE');
  return <Drawer open size={980} rootClassName="finance-drawer finance-page" onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden
    title={t('periodClose.detailTitle', { type: typeLabel, period: periodLabel(period), closeNo })}>
    {detail.error ? <Alert type="warning" showIcon title={detail.error} /> : !item ? <Spin /> : <>
      <CommandFeedback command={command} onReload={detail.reload} />
      {failed && <Alert type="error" showIcon className="finance-banner" title={t('periodClose.failedStep', { step: enumLabel(t, 'enum.closeStep', failed.stepType) })}
        description={failed.stepType === 'RESOLVE_EXCEPTIONS' ? t('periodClose.failedExceptions') : t('periodClose.failedGeneric')} />}
      {item.status === 'FAILED' && !failed && <Alert type="error" showIcon className="finance-banner" title={item.failedStepType ? t('periodClose.executionFailedStep', { step: item.failedStepType }) : t('periodClose.executionFailed')} />}
      <Descriptions column={2} size="small" className="finance-detail-head" items={[
        { key: 'status', label: t('field.status'), children: <WorkflowTag status={item.status} enumType="periodCloseStatus" /> },
        { key: 'period', label: t('modals.period'), children: `${periodLabel(period)} · ${period.startDate} — ${period.endDate}` },
        { key: 'maker', label: t('periodClose.initiator'), children: <ActorText value={item.makerId} actorId={actorId} /> },
        { key: 'checker', label: t('workflow.checker'), children: <ActorText value={item.checkerId} actorId={actorId} /> },
        { key: 'time', label: t('periodClose.startedAt'), children: localTime(item.createTime) },
        { key: 'hash', label: t('periodClose.field.closeHash'), children: <HashText value={item.closeHash} /> },
        ...(item.closeType === 'REOPEN' ? [
          { key: 'reason', label: t('periodClose.field.reopenReason'), children: item.reopenReasonCode ? enumLabel(t, 'enum.reopenReason', item.reopenReasonCode) : '—' },
          { key: 'ref', label: t('periodClose.field.requestRef'), children: <span className="mono">{item.reopenRequestRef ?? '—'}</span> },
          { key: 'original', label: t('periodClose.field.originalCloseNo'), children: item.reopensCloseNo ?? '—' },
        ] : []),
      ]} />
      <h3 className="finance-subtitle">{t('periodClose.stepsTitle')}</h3>
      <section className="table-panel">
        <Table<PeriodCloseStep> rowKey="stepNo" size="small" tableLayout="fixed" dataSource={item.steps} pagination={false}
          rowClassName={row => row.status === 'FAILED' ? 'finance-step-failed' : ''} locale={{ emptyText: t('periodClose.stepsEmpty') }} columns={[
            { title: t('periodClose.column.step'), width: 56, dataIndex: 'stepNo' },
            { title: t('charts.changeField.name'), render: (_, row) => <span>{enumLabel(t, 'enum.closeStep', row.stepType)}<div className="cell-meta mono">{row.stepType}</div></span> },
            { title: t('periodClose.column.result'), width: 90, render: (_, row) => <WorkflowTag status={row.status} enumType="closeStepStatus" /> },
            { title: t('periodClose.column.resultHash'), width: 190, render: (_, row) => <HashText value={row.resultHash} /> },
            { title: t('periodClose.column.completedAt'), width: 150, render: (_, row) => row.completedTime ? localTime(row.completedTime) : '—' },
          ]} />
      </section>
      <h3 className="finance-subtitle">{t('periodClose.booksTitle')}</h3>
      <section className="table-panel">
        <Table<PeriodCloseBook> rowKey="ledgerBookId" size="small" tableLayout="fixed" dataSource={item.books} pagination={false} scroll={{ x: 820 }}
          locale={{ emptyText: t('periodClose.booksEmpty') }} columns={[
            { title: t('field.book'), render: (_, row) => { const book = books.get(row.ledgerBookId);
              return book ? <span>{entityLabel(book.entityType, book.entityId, entities)} / {book.currency}<div className="cell-meta mono">{row.ledgerBookId}</div></span>
                : <span className="mono">{row.ledgerBookId}</span>; } },
            { title: t('trial.column.asOfSequence'), width: 100, render: (_, row) => <span className="mono">{row.asOfSequence}</span> },
            { title: t('periodClose.column.finalTrial'), width: 220, render: (_, row) => <FinalTrialLink book={row} periodId={periodId} /> },
            { title: t('periodClose.column.closingInstruction'), width: 190, render: (_, row) => row.postingInstructionId ? <span className="mono">{row.postingInstructionId}</span> : '—' },
            { title: t('field.status'), width: 90, render: (_, row) => <WorkflowTag status={row.status} /> },
          ]} />
      </section>
      <h3 className="finance-subtitle">{t('workflow.approvalsTitle')}</h3>
      <ApprovalTable approvals={item.approvals} actorId={actorId} />
      <ReviewActions saving={command.saving} note={selfNote} actions={reviewable ? [
        { key: 'reject', label: t('workflow.reject'), danger: true, confirm: item.closeType === 'CLOSE' ? t('periodClose.confirmRejectClose') : t('periodClose.confirmRejectReopen'),
          onConfirm: () => void review('reject') },
        { key: 'approve', label: t('periodClose.approve'), primary: true, confirm: t('periodClose.confirmApprove', { hash: item.closeHash!.slice(0, 16) }), onConfirm: () => void review('approve') },
      ] : []} />
      <FinanceNote>{t('periodClose.detailNote')}</FinanceNote>
    </>}
  </Drawer>;
}

/** 账簿最终试算：在该账簿本期试算记录中按 ID 找到序号后链接到试算平衡页签；无最终试算时链接到试算记录列表并显示最近一次的异常数。 */
function FinalTrialLink({ book, periodId }: { book: PeriodCloseBook; periodId: string }) {
  const t = useT('finance');
  const decode = useMemo(() => runsPage(book.ledgerBookId, periodId), [book.ledgerBookId, periodId]);
  const path = `${financePaths.trialBalances(book.ledgerBookId, periodId)}?page=1&size=50`;
  const runs = useDetail(path, decode, () => undefined, t('periodClose.runsLoadFailed'));
  const items: TrialBalanceRun[] = runs.item?.items ?? [];
  const run = book.finalTrialRunId ? items.find(value => value.trialBalanceRunId === book.finalTrialRunId) : items[0];
  const exceptions = run && run.exceptionCount > 0 ? <Tag color="red">{t('periodClose.exceptionsTag', { count: run.exceptionCount })}</Tag> : null;
  if (!run) return <Link to={trialRunLink(book.ledgerBookId, periodId)}>{book.finalTrialRunId ? <span className="mono">{book.finalTrialRunId}</span> : t('trial.viewRuns')} ↗</Link>;
  return <span><Link to={trialRunLink(book.ledgerBookId, periodId, run.runNo)}>{t(book.finalTrialRunId ? 'periodClose.finalTrialLink' : 'periodClose.latestTrialLink', { runNo: run.runNo })}</Link> {exceptions}</span>;
}
