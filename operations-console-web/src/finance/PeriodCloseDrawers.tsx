import { useMemo } from 'react';
import { Alert, App, Descriptions, Drawer, Empty, Form, Input, Modal, Select, Spin, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { usePaging } from '../hooks/usePaging';
import { decodeTrialBalanceRun, entityLabel, financePaths, localTime, pageOf } from './finance';
import type { AccountingPeriod, FinanceEntity, LedgerBook, TrialBalanceRun } from './finance';
import {
  canReview, closeStepLabels, closeStepStatusLabels, decodePeriodClose, decodePeriodCloseDetail, decodePeriodCloseResult, failedStep, ledgerWorkflowErrorTargets,
  periodCloseStatusLabels, periodCloseTypeLabels, periodLabel, reopenBody, reopenReasonLabels, trialRunLink, workflowPaths,
} from './financeWorkflow';
import type { PeriodClose, PeriodCloseBook, PeriodCloseDetail, PeriodCloseStep, ReopenFormValues } from './financeWorkflow';
import { FinanceNote, LoadFailure } from './FinanceParts';
import { useFinanceCommand } from './useFinanceCommand';
import { ActorText, ApprovalTable, CommandFeedback, HashText, ReviewActions, useDetail, WorkflowTag } from './WorkflowParts';

const reasonOptions = Object.entries(reopenReasonLabels).map(([value, label]) => ({ value, label: `${label}（${value}）` }));
const reopenFields = ['reasonCode', 'requestRef'];
const runsPage = (ledgerBookId: string, periodId: string) => pageOf(decodeTrialBalanceRun(ledgerBookId, periodId));

/** 重开申请：原因下拉 + 申请单号；成功返回重开流程（待复核或失败）。 */
export function ReopenPeriodModal({ period, onClose, onStarted, onAuthorizationChanged }: {
  period: AccountingPeriod; onClose: () => void; onStarted: (close: PeriodClose) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<ReopenFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, reopenFields, ledgerWorkflowErrorTargets);
  async function submit() {
    const values = await form.validateFields();
    const close = await command.run(workflowPaths.periodReopens(period.accountingPeriodId), reopenBody(values),
      decodePeriodCloseResult(period.accountingPeriodId, ['PENDING_CHECK', 'FAILED', 'RUNNING']), '发起重开失败');
    if (close) onStarted(close);
  }
  return <Modal open title={`重开 ${periodLabel(period)}`} okText="发起重开" cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }} destroyOnHidden>
    <CommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="finance-reopen-period" layout="vertical" disabled={command.saving}>
      <Form.Item name="reasonCode" label="重开原因" rules={[{ required: true, message: '请选择重开原因' }]}><Select options={reasonOptions} /></Form.Item>
      <Form.Item name="requestRef" label="申请单号" rules={[{ required: true, whitespace: true, message: '请填写批准本次重开的申请单号' },
        { max: 128, message: '最长 128 位' }]}><Input maxLength={128} placeholder="Platform 工单或操作申请编号" /></Form.Item>
    </Form>
    <FinanceNote>重开会冲回本期损益结转，复核通过后期间回到开放；只有最近一期已关账期间可以重开。</FinanceNote>
  </Modal>;
}

/** 期间关账 / 重开历史：分页列表，点击进入详情。 */
export function PeriodCloseHistoryDrawer({ period, actorId, onOpen, onClose }: {
  period: AccountingPeriod; actorId: string; onOpen: (closeNo: string) => void; onClose: () => void;
}) {
  const decode = useMemo(() => pageOf(decodePeriodClose(period.accountingPeriodId)), [period.accountingPeriodId]);
  const page = usePaging<PeriodClose>(workflowPaths.periodCloses(period.accountingPeriodId), decode);
  return <Drawer open size={820} rootClassName="finance-drawer finance-page" title={`关账记录 · ${periodLabel(period)}`} onClose={onClose} destroyOnHidden>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<PeriodClose> rowKey="periodCloseId" size="small" tableLayout="fixed" dataSource={page.data.items} loading={page.loading}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} onRow={row => ({ onClick: () => onOpen(row.closeNo), className: 'clickable-row' })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该期间还没有关账记录" /> }} columns={[
          { title: '序号', width: 70, render: (_, row) => <a className="mono">{row.closeNo}</a> },
          { title: '类型', width: 70, render: (_, row) => periodCloseTypeLabels[row.closeType] },
          { title: '状态', width: 90, render: (_, row) => <WorkflowTag status={row.status} labels={periodCloseStatusLabels} /> },
          { title: '发起人', render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
          { title: '发起时间', width: 150, render: (_, row) => localTime(row.createTime) },
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
  const { message } = App.useApp();
  const periodId = period.accountingPeriodId;
  const decode = useMemo(() => decodePeriodCloseDetail(periodId, closeNo), [periodId, closeNo]);
  const detail = useDetail<PeriodCloseDetail>(workflowPaths.periodClose(periodId, closeNo), decode, onAuthorizationChanged, '关账详情加载失败');
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], ledgerWorkflowErrorTargets);
  const item = detail.item;
  async function review(action: 'approve' | 'reject') {
    if (!item?.closeHash) return;
    const result = await command.run(workflowPaths.reviewPeriodClose(periodId, closeNo, action), { approvedCloseHash: item.closeHash },
      decodePeriodCloseResult(periodId, action === 'approve' ? ['COMPLETED', 'APPROVED'] : ['REJECTED']), action === 'approve' ? '审批关账失败' : '驳回关账失败');
    if (!result) return;
    void message.success(action === 'approve' ? (item.closeType === 'CLOSE' ? '已审批，期间已关账' : '已审批，期间已重开') : '已驳回');
    detail.reload(); onReviewed();
  }
  const reviewable = !!item?.closeHash && canReview(canApprove, item.status, 'PENDING_CHECK', item.makerId, actorId);
  const selfNote = item && canApprove && item.status === 'PENDING_CHECK' && item.makerId === actorId ? '你是发起人，不能复核自己发起的关账 / 重开' : undefined;
  const failed = item && item.status === 'FAILED' ? failedStep(item) : null;
  const typeLabel = item ? periodCloseTypeLabels[item.closeType] : '关账';
  return <Drawer open size={980} rootClassName="finance-drawer finance-page" onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden
    title={`${typeLabel} · ${periodLabel(period)} · 序号 ${closeNo}`}>
    {detail.error ? <Alert type="warning" showIcon title={detail.error} /> : !item ? <Spin /> : <>
      <CommandFeedback command={command} onReload={detail.reload} />
      {failed && <Alert type="error" showIcon className="finance-banner" title={`失败步骤：${closeStepLabels[failed.stepType] ?? failed.stepType}`}
        description={failed.stepType === 'RESOLVE_EXCEPTIONS' ? '试算存在未处理异常，期间仍为开放。异常在下方账簿的试算记录中只读查看，处理异常需另行设计。'
          : '该步骤未通过，期间仍为开放；排除原因后可重新发起。'} />}
      {item.status === 'FAILED' && !failed && <Alert type="error" showIcon className="finance-banner" title={`执行失败${item.failedStepType ? `：${item.failedStepType}` : ''}`} />}
      <Descriptions column={2} size="small" className="finance-detail-head" items={[
        { key: 'status', label: '状态', children: <WorkflowTag status={item.status} labels={periodCloseStatusLabels} /> },
        { key: 'period', label: '期间', children: `${periodLabel(period)} · ${period.startDate} — ${period.endDate}` },
        { key: 'maker', label: '发起人', children: <ActorText value={item.makerId} actorId={actorId} /> },
        { key: 'checker', label: '复核人', children: <ActorText value={item.checkerId} actorId={actorId} /> },
        { key: 'time', label: '发起时间', children: localTime(item.createTime) },
        { key: 'hash', label: '关账摘要', children: <HashText value={item.closeHash} /> },
        ...(item.closeType === 'REOPEN' ? [
          { key: 'reason', label: '重开原因', children: item.reopenReasonCode ? reopenReasonLabels[item.reopenReasonCode] ?? item.reopenReasonCode : '—' },
          { key: 'ref', label: '申请单号', children: <span className="mono">{item.reopenRequestRef ?? '—'}</span> },
          { key: 'original', label: '原关账序号', children: item.reopensCloseNo ?? '—' },
        ] : []),
      ]} />
      <h3 className="finance-subtitle">执行步骤</h3>
      <section className="table-panel">
        <Table<PeriodCloseStep> rowKey="stepNo" size="small" tableLayout="fixed" dataSource={item.steps} pagination={false}
          rowClassName={row => row.status === 'FAILED' ? 'finance-step-failed' : ''} locale={{ emptyText: '尚无步骤' }} columns={[
            { title: '步骤', width: 56, dataIndex: 'stepNo' },
            { title: '名称', render: (_, row) => <span>{closeStepLabels[row.stepType] ?? row.stepType}<div className="cell-meta mono">{row.stepType}</div></span> },
            { title: '结果', width: 90, render: (_, row) => <WorkflowTag status={row.status} labels={closeStepStatusLabels} /> },
            { title: '结果摘要', width: 190, render: (_, row) => <HashText value={row.resultHash} /> },
            { title: '完成时间', width: 150, render: (_, row) => row.completedTime ? localTime(row.completedTime) : '—' },
          ]} />
      </section>
      <h3 className="finance-subtitle">各账簿</h3>
      <section className="table-panel">
        <Table<PeriodCloseBook> rowKey="ledgerBookId" size="small" tableLayout="fixed" dataSource={item.books} pagination={false} scroll={{ x: 820 }}
          locale={{ emptyText: '尚未锁定账簿' }} columns={[
            { title: '账簿', render: (_, row) => { const book = books.get(row.ledgerBookId);
              return book ? <span>{entityLabel(book.entityType, book.entityId, entities)} / {book.currency}<div className="cell-meta mono">{row.ledgerBookId}</div></span>
                : <span className="mono">{row.ledgerBookId}</span>; } },
            { title: '分录水位', width: 100, render: (_, row) => <span className="mono">{row.asOfSequence}</span> },
            { title: '最终试算', width: 220, render: (_, row) => <FinalTrialLink book={row} periodId={periodId} /> },
            { title: '结转指令', width: 190, render: (_, row) => row.postingInstructionId ? <span className="mono">{row.postingInstructionId}</span> : '—' },
            { title: '状态', width: 90, render: (_, row) => <WorkflowTag status={row.status} /> },
          ]} />
      </section>
      <h3 className="finance-subtitle">审批记录</h3>
      <ApprovalTable approvals={item.approvals} actorId={actorId} />
      <ReviewActions saving={command.saving} note={selfNote} actions={reviewable ? [
        { key: 'reject', label: '驳回', danger: true, confirm: item.closeType === 'CLOSE' ? '驳回后期间回到开放，不保留结转分录。确认驳回？' : '驳回后期间保持已关账。确认驳回？',
          onConfirm: () => void review('reject') },
        { key: 'approve', label: '审批通过', primary: true, confirm: `确认关账摘要 ${item.closeHash!.slice(0, 16)}… 并审批？`, onConfirm: () => void review('approve') },
      ] : []} />
      <FinanceNote>损益结转在关账执行时生成指令，审批后期间变为已关账；年末期间同时结转留存收益。试算异常本期只读，不提供处理入口。</FinanceNote>
    </>}
  </Drawer>;
}

/** 账簿最终试算：在该账簿本期试算记录中按 ID 找到序号后链接到试算平衡页签；无最终试算时链接到试算记录列表并显示最近一次的异常数。 */
function FinalTrialLink({ book, periodId }: { book: PeriodCloseBook; periodId: string }) {
  const decode = useMemo(() => runsPage(book.ledgerBookId, periodId), [book.ledgerBookId, periodId]);
  const path = `${financePaths.trialBalances(book.ledgerBookId, periodId)}?page=1&size=50`;
  const runs = useDetail(path, decode, () => undefined, '试算记录加载失败');
  const items: TrialBalanceRun[] = runs.item?.items ?? [];
  const run = book.finalTrialRunId ? items.find(value => value.trialBalanceRunId === book.finalTrialRunId) : items[0];
  const exceptions = run && run.exceptionCount > 0 ? <Tag color="red">异常 {run.exceptionCount}</Tag> : null;
  if (!run) return <Link to={trialRunLink(book.ledgerBookId, periodId)}>{book.finalTrialRunId ? <span className="mono">{book.finalTrialRunId}</span> : '试算记录'} ↗</Link>;
  return <span><Link to={trialRunLink(book.ledgerBookId, periodId, run.runNo)}>{book.finalTrialRunId ? '最终试算' : '最近试算'} 序号 {run.runNo} ↗</Link> {exceptions}</span>;
}
