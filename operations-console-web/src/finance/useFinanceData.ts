import { useEffect, useMemo, useRef, useState } from 'react';
import { requestAllPages } from '../api/paging';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { translator } from '../i18n/index';
import {
  decodeAccountingPeriod, decodeChartAccount, decodeChartOfAccounts, decodeCurrency, decodeFinanceEntity, decodeFiscalCalendar, decodeLedgerAccount, decodeLedgerBook,
  financePaths, listOf, pageOf,
} from './finance';
import type { AccountingPeriod, ChartAccount, ChartAccountStatus, ChartOfAccounts, Currency, FinanceEntity, LedgerAccount, LedgerBook } from './finance';

const t = translator('finance');

export interface ListState<T> { items: T[]; loading: boolean; error: string; reload: () => void }

/**
 * 只读下拉 / 字典数据：path 为空时不请求；403 触发授权刷新；失败时通知并保留内联错误供重试。
 * 上层每次渲染会传入新的回调，放进 ref 避免触发重新加载。
 * <li>notifyFailure 为 false 时不弹通知，只保留 error，由页面统一展示（如明细账运维的整页报错）。</li>
 */
export function useFinanceList<T>(path: string | null, load: (path: string, signal: AbortSignal) => Promise<T[]>,
  onAuthorizationChanged?: () => void, failureTitle = t('data.loadFailed'), notifyFailure = true): ListState<T> {
  const notify = useErrorNotification();
  const [state, setState] = useState<{ path: string | null; items: T[]; loading: boolean; error: string }>({ path: null, items: [], loading: false, error: '' });
  const [reload, setReload] = useState(0);
  const loader = useRef(load); loader.current = load;
  const authorizationChanged = useRef(onAuthorizationChanged); authorizationChanged.current = onAuthorizationChanged;
  useEffect(() => {
    if (!path) { setState({ path: null, items: [], loading: false, error: '' }); return; }
    const abort = new AbortController();
    setState(current => ({ path, items: current.path === path ? current.items : [], loading: true, error: '' }));
    loader.current(path, abort.signal)
      .then(items => { if (!abort.signal.aborted) setState({ path, items, loading: false, error: '' }); })
      .catch(failure => {
        if (abort.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 403) authorizationChanged.current?.();
        setState({ path, items: [], loading: false, error: failure instanceof Error ? failure.message : t('common.loadFailed') });
        if (notifyFailure) notify(failure, failureTitle);
      });
    return () => abort.abort();
  }, [path, reload, notify, failureTitle, notifyFailure]);
  const items = state.path === path ? state.items : [];
  return { items, loading: state.loading, error: state.error, reload: () => setReload(value => value + 1) };
}

const currencyList = listOf(decodeCurrency);
const chartList = listOf(decodeChartOfAccounts);
const chartAccountList = listOf(decodeChartAccount);
const entityPage = pageOf(decodeFinanceEntity);
const bookPage = pageOf(decodeLedgerBook);
const calendarPage = pageOf(decodeFiscalCalendar);
const accountPage = pageOf(decodeLedgerAccount);

/** 全部币种（含非启用项，用于金额精度）；下拉只取 ACTIVE。 */
export function useCurrencies(onAuthorizationChanged?: () => void) {
  const state = useFinanceList<Currency>(financePaths.currencies,
    (path, signal) => session.request(path, { signal }, currencyList), onAuthorizationChanged, t('data.currenciesLoadFailed'));
  const minorUnits = useMemo(() => new Map(state.items.map(item => [item.currency, item.minorUnit])), [state.items]);
  return { ...state, minorUnits };
}

export function useCharts(onAuthorizationChanged?: () => void) {
  return useFinanceList<ChartOfAccounts>(financePaths.charts,
    (path, signal) => session.request(path, { signal }, chartList), onAuthorizationChanged, t('data.chartsLoadFailed'));
}

/** 科目筛选（R15）：postingAllowed 为 true 只取末级科目，accountStatus 为 ACTIVE 只取启用科目；省略时不筛选。 */
export interface ChartAccountFilter { postingAllowed?: boolean; accountStatus?: ChartAccountStatus }

/** 某科目表下的科目，按编号升序；reloadToken 变化时重新加载（科目维护后刷新）。 */
export function useChartAccounts(chartCode: string | null, filter: ChartAccountFilter = {}, onAuthorizationChanged?: () => void, reloadToken = 0) {
  const query = new URLSearchParams();
  if (filter.postingAllowed) query.set('postingAllowed', 'true');
  if (filter.accountStatus) query.set('accountStatus', filter.accountStatus);
  const path = chartCode ? `${financePaths.chartAccounts(chartCode)}${query.size ? `?${query}` : ''}` : null;
  const state = useFinanceList<ChartAccount>(path, (url, signal) => session.request(url, { signal }, chartAccountList),
    onAuthorizationChanged, t('data.chartAccountsLoadFailed'));
  const { reload } = state;
  useEffect(() => { if (reloadToken) reload(); }, [reloadToken]);
  return state;
}

/** 采用该科目表的账簿数（R08 全部账簿按科目表计数）；采用后既有科目的编号与上级冻结。 */
export function useChartAdoption(chartCode: string | null, onAuthorizationChanged?: () => void) {
  const state = useFinanceList<LedgerBook>(chartCode ? financePaths.ledgerBooks : null,
    (path, signal) => requestAllPages(path, signal, session, {}, bookPage), onAuthorizationChanged, t('data.booksLoadFailed'));
  return { ...state, adoptedBy: state.items.filter(book => book.chartCode === chartCode).length };
}

/** 全部账簿（每页 200 分批读完），供试算平衡的账簿选择。 */
export function useLedgerBooks(onAuthorizationChanged?: () => void, notifyFailure = true) {
  return useFinanceList<LedgerBook>(financePaths.ledgerBooks, (path, signal) => requestAllPages(path, signal, session, {}, bookPage),
    onAuthorizationChanged, t('data.booksLoadFailed'), notifyFailure);
}

/** 账簿主体的会计期间：先按主体找唯一日历，再读完该日历全部期间（按财年、期号升序）；账簿未知时不请求。 */
export function useBookPeriods(book: LedgerBook | undefined, onAuthorizationChanged?: () => void) {
  const path = book ? `${financePaths.fiscalCalendars}#${book.entityType}:${book.entityId}` : null;
  return useFinanceList<AccountingPeriod>(path, async (_, signal) => {
    if (!book) return [];
    const calendars = await requestAllPages(financePaths.fiscalCalendars, signal, session,
      { entityType: book.entityType, entityId: book.entityId }, calendarPage);
    const calendar = calendars.find(item => item.entityType === book.entityType && item.entityId === book.entityId);
    if (!calendar) return [];
    return requestAllPages(financePaths.periods(calendar.fiscalCalendarId), signal, session, {},
      pageOf(value => decodeAccountingPeriod(value, calendar.fiscalCalendarId)));
  }, onAuthorizationChanged, t('data.periodsLoadFailed'));
}

/** 全部财务主体（每页 200 分批读完），供主体下拉与主体代码展示。 */
export function useFinanceEntities(enabled = true, onAuthorizationChanged?: () => void, notifyFailure = true) {
  const state = useFinanceList<FinanceEntity>(enabled ? financePaths.financeEntities : null,
    (path, signal) => requestAllPages(path, signal, session, {}, entityPage), onAuthorizationChanged, t('data.entitiesLoadFailed'),
    notifyFailure);
  const byId = useMemo(() => new Map(state.items.map(item => [item.financeEntityId, item])), [state.items]);
  return { ...state, byId };
}

/** 账簿全部账户（每页 200 分批读完），供人工调整分录行选择与详情行展示；账簿未知时不请求。 */
export function useBookAccounts(ledgerBookId: string | null, onAuthorizationChanged?: () => void) {
  const state = useFinanceList<LedgerAccount>(ledgerBookId ? financePaths.accounts(ledgerBookId) : null,
    (path, signal) => requestAllPages(path, signal, session, {}, accountPage), onAuthorizationChanged, t('data.accountsLoadFailed'));
  const byId = useMemo(() => new Map(state.items.map(item => [item.accountId, item])), [state.items]);
  return { ...state, byId };
}

/** 全部财务日历（每页 200 分批读完），供期间关账的日历选择。 */
export function useFiscalCalendars(onAuthorizationChanged?: () => void) {
  return useFinanceList(financePaths.fiscalCalendars, (path, signal) => requestAllPages(path, signal, session, {}, calendarPage),
    onAuthorizationChanged, t('data.calendarsLoadFailed'));
}
