import { useMemo } from 'react';
import { App, Descriptions } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ActorText, HashText, ReviewActions } from '../../finance/WorkflowParts';
import { useT } from '../../i18n/react';
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import { accountPurposeEnum, cashStatusEnum, decodeWithId, entityTypeEnum, hasReconAction, reconActions, reconLabel, reconPaths } from '../reconModel';
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
  const t = useT('recon');
  const { message } = App.useApp();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const decode = useMemo(() => decodeWithId<CashAccount>('cashAccountId', cashAccountId), [cashAccountId]);
  const detail = useReconDetail<CashAccount>(isReconId(cashAccountId) ? reconPaths.cashAccount(cashAccountId) : null, decode, onAuthorizationChanged, t('cashDetail.loadFailed'));
  const command = useReconCommand(onAuthorizationChanged);
  async function act(path: string, body: unknown, success: string, failure: string) {
    const result = await command.run(path, 'POST', body, decodeWithId<CashAccount>('cashAccountId', cashAccountId), failure);
    if (!result) return;
    void message.success(success); detail.reload();
  }
  const item = detail.item;
  return <ReconDetailPage list={{ label: t('cash.listLabel'), route: reconRoutes.cashAccounts }} objectLabel={t('cash.object')} state={detail}
    title={item ? t('cashDetail.title', { name: item.accountName }) : null}>
    {account => {
      const pending = account.status === 'PENDING_VERIFICATION' || account.pendingAction === 'CLOSE';
      const actions = !pending || !canApprove || account.makerId === actorId ? [] : [
        { key: 'approve', label: t('cashDetail.approve'), primary: true, confirm: account.pendingAction === 'CLOSE' ? t('cashDetail.confirmApproveClose') : t('cashDetail.confirmApproveCreate'),
          onConfirm: () => void act(reconPaths.cashApproval(cashAccountId), { decision: 'APPROVED', approvedContentHash: account.contentHash }, t('cashDetail.approved'), t('cashDetail.reviewFailed')) },
        { key: 'reject', label: t('cashDetail.reject'), danger: true, confirm: account.pendingAction === 'CLOSE' ? t('cashDetail.confirmRejectClose') : t('cashDetail.confirmRejectCreate'),
          onConfirm: () => void act(reconPaths.cashApproval(cashAccountId), { decision: 'REJECTED', approvedContentHash: account.contentHash }, t('cashDetail.rejected'), t('cashDetail.reviewFailed')) },
      ];
      return <>
        <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
        <Descriptions size="small" column={3} items={[
          { key: 'entity', label: t('field.entity'), children: `${reconLabel(t, entityTypeEnum, account.entityType)} · ${account.entityId}` },
          { key: 'purpose', label: t('field.purpose'), children: reconLabel(t, accountPurposeEnum, account.accountPurpose) },
          { key: 'status', label: t('field.status'), children: <ReconTag status={account.status} kind={cashStatusEnum} /> },
          { key: 'bank', label: t('field.bank'), children: account.bankCode }, { key: 'currency', label: t('field.currency'), children: account.currency },
          { key: 'last4', label: t('field.last4'), children: <span className="mono">{account.accountLast4}</span> },
          { key: 'pending', label: t('cashDetail.pendingChange'), children: account.status === 'PENDING_VERIFICATION' ? t('cashDetail.pendingCreate')
            : account.pendingAction === 'CLOSE' ? t('cashDetail.pendingClose') : t('common.none') },
          { key: 'hash', label: t('cashDetail.contentHash'), children: <HashText value={account.contentHash} /> },
          { key: 'id', label: t('field.id'), children: <span className="mono">{account.cashAccountId}</span> },
          { key: 'maker', label: t('field.maker'), children: <ActorText value={account.makerId} actorId={actorId} /> },
          { key: 'checker', label: t('field.checker'), children: <ActorText value={account.checkerId} actorId={actorId} /> },
          { key: 'time', label: t('field.updatedAt'), children: localTime(account.updateTime) },
        ]} />
        {account.status !== 'PENDING_VERIFICATION' && <DetailSection title={t('detail.related')}>
          <div className="recon-link-list">
            <Link to={withQuery(reconRoutes.bank, { account: account.cashAccountId })}>{t('link.bankLines')}</Link>
            <Link to={withQuery(reconRoutes.bank, { account: account.cashAccountId, tab: 'statements' })}>{t('link.bankStatements')}</Link>
            <Link to={withQuery(reconRoutes.bank, { account: account.cashAccountId, tab: 'movements' })}>{t('link.expectedMovements')}</Link>
            <Link to={withQuery(reconRoutes.exceptions, { cashAccountId: account.cashAccountId })}>{t('link.openExceptions')}</Link>
          </div>
        </DetailSection>}
        {pending && <DetailSection title={t('cashDetail.review')}>
          <ReviewActions saving={command.saving} actions={actions}
            note={!canApprove ? t('permission.noApprove') : account.makerId === actorId ? t('cashDetail.selfReview') : undefined} />
        </DetailSection>}
        {canOperate && account.status === 'ACTIVE' && !account.pendingAction && <ReviewActions saving={command.saving} actions={[{ key: 'close', label: t('cashDetail.requestClose'), danger: true,
          confirm: t('cashDetail.confirmRequestClose'),
          onConfirm: () => void act(reconPaths.cashClosure(cashAccountId), undefined, t('cashDetail.closeRequested'), t('cashDetail.closeRequestFailed')) }]} />}
        <FinanceNote>{t('cashDetail.note')}</FinanceNote>
      </>;
    }}
  </ReconDetailPage>;
}
