import { useEffect, useState } from 'react';
import { Breadcrumb, Empty } from 'antd';
import { session } from '../api/session';
import { useLocation, useNavigate } from 'react-router-dom';
import {
  decodeOwnerAccountLookup, ownerAccountLookupPath, readSubledgerQuery, replaceQuery, subledgerLevelSearch, subledgerQuerySearch,
} from '../finance/finance';
import type { AccountPosting, AccountPostingEntry, OwnerAccount, OwnerAccountRef, SubledgerQuery, SubledgerScope } from '../finance/finance';
import { FinanceNote } from '../finance/FinanceParts';
import { SubledgerAccountList, SubledgerSearchForm } from '../finance/SubledgerAccounts';
import { SubledgerEntries } from '../finance/SubledgerEntries';
import { SubledgerPostingDrawer } from '../finance/SubledgerPostingDrawer';
import { useCurrencies } from '../finance/useFinanceData';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../finance/finance.css';

/** 从账户列表下钻时随浏览器历史保存的账户摘要；直接粘贴 URL 进入时只有主键。 */
interface DrillState { account?: OwnerAccountRef }

/**
 * 明细账查询（只读）：主体 + 币种 › Owner 账户 › 明细流水，入账批次在抽屉中
 * <li>主体、筛选、账户选择、分页与打开的批次都在 URL，面包屑逐级返回保留筛选。</li>
 * <li>总账分录行「流水 …」跳转只带 posting / entry：先经批次详情定位，再从抽屉进入所属账户流水。</li>
 */
export function FinanceSubledger({ onAuthorizationChanged }: { onAuthorizationChanged: () => void }) {
  const t = useT('finance'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate();
  const query = readSubledgerQuery(new URLSearchParams(location.search));
  const state = (location.state ?? {}) as DrillState;
  const currencies = useCurrencies(onAuthorizationChanged);
  const drilled: OwnerAccountRef | null = query.account
    ? (state.account?.accountId === query.account ? state.account : { accountId: query.account }) : null;
  const account = useAccountSummary(query.scope, drilled);
  const minorUnit = query.scope ? currencies.minorUnits.get(query.scope.currency) : undefined;
  const go = (search: string, next: DrillState = {}, replace = false) => void navigate({ search }, { state: next, replace });
  const openAccount = (next: OwnerAccount) => go(subledgerLevelSearch(location.search, 'entries', { account: next.accountId }), { account: next });
  const openFromPosting = (posting: AccountPosting, entry: AccountPostingEntry) => {
    const scope: SubledgerScope = { entityType: posting.entityType, entityId: posting.entityId, currency: posting.currency };
    go(subledgerLevelSearch(location.search, 'entries', { account: entry.accountId, scope }), { account: {
      accountId: entry.accountId, accountNo: entry.accountNo, accountCode: entry.accountCode, ownerType: entry.ownerType, ownerId: entry.ownerId,
      balanceBucket: entry.balanceBucket } });
  };
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_SUBLEDGER')}</h1></div>
    <SubledgerBreadcrumb query={query} account={account} onLevel={level => go(subledgerLevelSearch(location.search, level))} />
    {query.level !== 'entries' && <SubledgerSearchForm key={JSON.stringify([query.scope, query.accountFilters])} scope={query.scope}
      filters={query.accountFilters} currencies={currencies.items}
      onSearch={filters => go(subledgerQuerySearch(location.search, 'accounts', filters, { page: 1, size: query.accountPaging.size }))} />}
    {query.level === 'idle' && <div className="finance-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE}
      description={t('subledger.idle')} /></div>}
    {query.level === 'accounts' && query.scope && <>
      <SubledgerAccountList key={JSON.stringify([query.scope, query.accountFilters, query.accountPaging])} scope={query.scope}
        filters={query.accountFilters} paging={query.accountPaging} minorUnit={minorUnit} onSelect={openAccount}
        onQueryChange={next => go(subledgerQuerySearch(location.search, 'accounts', next.filters, next.paging), state, true)} />
      <FinanceNote>{t('subledger.accountsNote')}</FinanceNote>
    </>}
    {query.level === 'entries' && account && <>
      <SubledgerEntries key={JSON.stringify([query.account, query.entryPaging])} account={account} currency={query.scope?.currency}
        paging={query.entryPaging} minorUnit={minorUnit}
        onQueryChange={next => go(subledgerQuerySearch(location.search, 'entries', next.filters, next.paging), state, true)}
        onOpenPosting={(posting, entry) => go(replaceQuery(location.search, { posting, entry }), state)} />
      <FinanceNote>{t('subledger.entriesNote')}</FinanceNote>
    </>}
    {query.posting && <SubledgerPostingDrawer postingId={query.posting} highlightEntryId={query.entry} minorUnits={currencies.minorUnits}
      onOpenAccount={openFromPosting} onClose={() => go(replaceQuery(location.search, { posting: null, entry: null }), state)}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/**
 * 流水摘要所需的完整账户：从账户列表下钻时直接使用；从批次抽屉进入时只有科目、Owner 与余额桶，按 R19 精确筛选补齐方向与净余额
 * <li>补齐失败只保留已有摘要字段，流水列表照常加载。</li>
 */
function useAccountSummary(scope: SubledgerScope | null, account: OwnerAccountRef | null): OwnerAccountRef | null {
  const path = ownerAccountLookupPath(scope, account);
  const [loaded, setLoaded] = useState<OwnerAccount>();
  useEffect(() => {
    setLoaded(undefined);
    if (!path || !account || !scope) return;
    const abort = new AbortController();
    session.request(path, { signal: abort.signal }, value => decodeOwnerAccountLookup(value, account.accountId, scope.currency))
      .then(value => { if (!abort.signal.aborted) setLoaded(value); }).catch(() => undefined);
    return () => abort.abort();
    // 只在查询路径变化时重新加载。
  }, [path]);
  return loaded && loaded.accountId === account?.accountId ? loaded : account;
}

/** 面包屑：明细账 › 主体 / 币种 › 科目 · Owner · 余额桶；上级可点击返回，筛选条件保留。 */
function SubledgerBreadcrumb({ query, account, onLevel }: {
  query: SubledgerQuery; account: OwnerAccountRef | null; onLevel: (level: 'idle' | 'accounts') => void;
}) {
  const t = useT('finance');
  if (!query.scope) return null;
  const scope = `${query.scope.entityType} · ${query.scope.entityId} / ${query.scope.currency}`;
  return <Breadcrumb className="finance-breadcrumb" items={[
    { title: <a onClick={() => onLevel('idle')}>{t('subledger.breadcrumb')}</a> },
    { title: account ? <a onClick={() => onLevel('accounts')}>{scope}</a> : <strong>{scope}</strong> },
    ...(account ? [{ title: <strong>{account.accountCode
      ? [account.accountCode, `${account.ownerType} ${account.ownerId}`, account.balanceBucket].join(' · ') : t('ledger.accountById', { accountId: account.accountId })}</strong> }] : []),
  ]} />;
}
