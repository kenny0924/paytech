import { useMemo } from 'react';
import { App, Descriptions } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ActorText, HashText, ReviewActions } from '../../finance/WorkflowParts';
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import { accountPurposeLabels, cashStatusLabels, decodeWithId, entityTypeLabels, hasReconAction, reconActions, reconPaths } from '../reconModel';
import type { CashAccount } from '../reconModel';
import { ReconCommandFeedback, ReconTag } from '../ReconParts';
import { isReconId, reconRoutes, withQuery } from '../reconRoutes';
import { useReconCommand } from '../useReconCommand';
import { useReconDetail } from '../useReconDetail';

interface Props { cashAccountId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/**
 * 银行账户详情页（/finance/reconciliation/cash-accounts/:cashAccountId）：信息、复核状态、复核（审批，提交人本人不显示）与申请关闭（操作）
 * <li>银行账号只显示末四位；复核以提交内容摘要防止审批已变化的内容。</li>
 */
export function CashAccountDetail({ cashAccountId, resources, actorId, onAuthorizationChanged }: Props) {
  const { message } = App.useApp();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const decode = useMemo(() => decodeWithId<CashAccount>('cashAccountId', cashAccountId), [cashAccountId]);
  const detail = useReconDetail<CashAccount>(isReconId(cashAccountId) ? reconPaths.cashAccount(cashAccountId) : null, decode, onAuthorizationChanged, '银行账户详情加载失败');
  const command = useReconCommand(onAuthorizationChanged);
  async function act(path: string, body: unknown, success: string, failure: string) {
    const result = await command.run(path, 'POST', body, decodeWithId<CashAccount>('cashAccountId', cashAccountId), failure);
    if (!result) return;
    void message.success(success); detail.reload();
  }
  const item = detail.item;
  return <ReconDetailPage list={{ label: '银行账户', route: reconRoutes.cashAccounts }} objectLabel="银行账户" state={detail}
    title={item ? `银行账户 · ${item.accountName}` : null}>
    {account => {
      const pending = account.status === 'PENDING_VERIFICATION' || account.pendingAction === 'CLOSE';
      const actions = !pending || !canApprove || account.makerId === actorId ? [] : [
        { key: 'approve', label: '通过', primary: true, confirm: account.pendingAction === 'CLOSE' ? '通过后账户关闭。确认？' : '通过后账户生效。确认？',
          onConfirm: () => void act(reconPaths.cashApproval(cashAccountId), { decision: 'APPROVED', approvedContentHash: account.contentHash }, '已通过', '复核失败') },
        { key: 'reject', label: '拒绝', danger: true, confirm: account.pendingAction === 'CLOSE' ? '拒绝后关闭申请撤销。确认？' : '拒绝后该账户关闭。确认？',
          onConfirm: () => void act(reconPaths.cashApproval(cashAccountId), { decision: 'REJECTED', approvedContentHash: account.contentHash }, '已拒绝', '复核失败') },
      ];
      return <>
        <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
        <Descriptions size="small" column={3} items={[
          { key: 'entity', label: '主体', children: `${entityTypeLabels[account.entityType] ?? account.entityType} · ${account.entityId}` },
          { key: 'purpose', label: '用途', children: accountPurposeLabels[account.accountPurpose] ?? account.accountPurpose },
          { key: 'status', label: '状态', children: <ReconTag status={account.status} labels={cashStatusLabels} /> },
          { key: 'bank', label: '开户银行', children: account.bankCode }, { key: 'currency', label: '币种', children: account.currency },
          { key: 'last4', label: '账号末四位', children: <span className="mono">{account.accountLast4}</span> },
          { key: 'pending', label: '待复核变更', children: account.status === 'PENDING_VERIFICATION' ? '新建' : account.pendingAction === 'CLOSE' ? '关闭' : '无' },
          { key: 'hash', label: '提交内容摘要', children: <HashText value={account.contentHash} /> },
          { key: 'id', label: '主键', children: <span className="mono">{account.cashAccountId}</span> },
          { key: 'maker', label: '提交人', children: <ActorText value={account.makerId} actorId={actorId} /> },
          { key: 'checker', label: '复核人', children: <ActorText value={account.checkerId} actorId={actorId} /> },
          { key: 'time', label: '更新时间', children: localTime(account.updateTime) },
        ]} />
        {account.status !== 'PENDING_VERIFICATION' && <DetailSection title="相关数据">
          <div className="recon-link-list">
            <Link to={withQuery(reconRoutes.bank, { account: account.cashAccountId })}>银行流水</Link>
            <Link to={withQuery(reconRoutes.bank, { account: account.cashAccountId, tab: 'statements' })}>银行对账单</Link>
            <Link to={withQuery(reconRoutes.bank, { account: account.cashAccountId, tab: 'movements' })}>预期银行流水</Link>
            <Link to={withQuery(reconRoutes.exceptions, { cashAccountId: account.cashAccountId })}>未结异常</Link>
          </div>
        </DetailSection>}
        {pending && <DetailSection title="复核">
          <ReviewActions saving={command.saving} actions={actions}
            note={!canApprove ? '当前账号没有对账审批权限。' : account.makerId === actorId ? '提交人不能复核自己的提交。' : undefined} />
        </DetailSection>}
        {canOperate && account.status === 'ACTIVE' && !account.pendingAction && <ReviewActions saving={command.saving} actions={[{ key: 'close', label: '申请关闭', danger: true,
          confirm: '关闭申请经复核后账户关闭，清算账户如以它为到账账户需另行更换。确认申请？',
          onConfirm: () => void act(reconPaths.cashClosure(cashAccountId), undefined, '已提交关闭申请，等待复核', '申请关闭失败') }]} />}
        <FinanceNote>同一主体 + 币种 + 用途只允许一个有效账户；换用途请关闭旧账户再新建。新建与关闭须经另一位有审批权限的同事复核。</FinanceNote>
      </>;
    }}
  </ReconDetailPage>;
}
