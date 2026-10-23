import { useMemo } from 'react';
import { requestAllPages } from '../api/paging';
import { session } from '../api/session';
import { useFinanceList } from '../finance/useFinanceData';
import { translator } from '../i18n/index';
import { decodeUnassignedMids, decodeWithId, pageOf, reconPaths } from './reconModel';
import type { CashAccount, ClearingAccountSummary, UnassignedMid } from './reconModel';

const t = translator('recon');
const cashPage = pageOf(decodeWithId<CashAccount>('cashAccountId'));
const clearingPage = pageOf(decodeWithId<ClearingAccountSummary>('clearingAccountId'));

/** 全部银行账户（每页 200 分批读完），供到账账户、对账单上传与筛选的下拉；reloadToken 变化时重新加载。 */
export function useCashAccounts(onAuthorizationChanged?: () => void, reloadToken = 0) {
  const state = useFinanceList<CashAccount>(`${reconPaths.cashAccounts}#${reloadToken}`,
    (_, signal) => requestAllPages(reconPaths.cashAccounts, signal, session, {}, cashPage), onAuthorizationChanged, t('data.cashAccountsLoadFailed'));
  const byId = useMemo(() => new Map(state.items.map(item => [item.cashAccountId, item])), [state.items]);
  return { ...state, byId };
}

/** 全部清算账户（每页 200 分批读完），供筛选下拉与编码展示。 */
export function useClearingAccounts(onAuthorizationChanged?: () => void) {
  const state = useFinanceList<ClearingAccountSummary>(reconPaths.clearingAccounts,
    (path, signal) => requestAllPages(path, signal, session, {}, clearingPage), onAuthorizationChanged, t('data.clearingAccountsLoadFailed'));
  const byId = useMemo(() => new Map(state.items.map(item => [item.clearingAccountId, item])), [state.items]);
  return { ...state, byId };
}

/** 已激活但未归入清算账户的 MID + 币种（UC-R17 提示）。 */
export function useUnassignedMids(onAuthorizationChanged?: () => void, reloadToken = 0) {
  return useFinanceList<UnassignedMid>(`${reconPaths.unassignedMids}#${reloadToken}`,
    (_, signal) => session.request(reconPaths.unassignedMids, { signal }, decodeUnassignedMids), onAuthorizationChanged, t('data.unassignedMidsLoadFailed'));
}

/** 银行账户选项文案：名称 · 主体 · 币种 · 尾号。 */
export function cashAccountLabel(account: CashAccount): string {
  return t('cash.accountOption', { name: account.accountName, entityId: account.entityId, currency: account.currency, last4: account.accountLast4 });
}
