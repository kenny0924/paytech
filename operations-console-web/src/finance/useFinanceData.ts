import { useEffect, useMemo, useRef, useState } from 'react';
import { requestAllPages } from '../api/paging';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import {
  decodeChartAccount, decodeChartOfAccounts, decodeCurrency, decodeFinanceEntity, financePaths, listOf, pageOf,
} from './finance';
import type { ChartAccount, ChartOfAccounts, Currency, FinanceEntity } from './finance';

export interface ListState<T> { items: T[]; loading: boolean; error: string; reload: () => void }

/**
 * 只读下拉 / 字典数据：path 为空时不请求；403 触发授权刷新；失败时通知并保留内联错误供重试。
 * 上层每次渲染会传入新的回调，放进 ref 避免触发重新加载。
 */
export function useFinanceList<T>(path: string | null, load: (path: string, signal: AbortSignal) => Promise<T[]>,
  onAuthorizationChanged?: () => void, failureTitle = '加载失败'): ListState<T> {
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
        setState({ path, items: [], loading: false, error: failure instanceof Error ? failure.message : '暂时无法加载，请重试。' });
        notify(failure, failureTitle);
      });
    return () => abort.abort();
  }, [path, reload, notify, failureTitle]);
  const items = state.path === path ? state.items : [];
  return { items, loading: state.loading, error: state.error, reload: () => setReload(value => value + 1) };
}

const currencyList = listOf(decodeCurrency);
const chartList = listOf(decodeChartOfAccounts);
const chartAccountList = listOf(decodeChartAccount);
const entityPage = pageOf(decodeFinanceEntity);

/** 全部币种（含非启用项，用于金额精度）；下拉只取 ACTIVE。 */
export function useCurrencies(onAuthorizationChanged?: () => void) {
  const state = useFinanceList<Currency>(financePaths.currencies,
    (path, signal) => session.request(path, { signal }, currencyList), onAuthorizationChanged, '币种加载失败');
  const minorUnits = useMemo(() => new Map(state.items.map(item => [item.currency, item.minorUnit])), [state.items]);
  return { ...state, minorUnits };
}

export function useCharts(onAuthorizationChanged?: () => void) {
  return useFinanceList<ChartOfAccounts>(financePaths.charts,
    (path, signal) => session.request(path, { signal }, chartList), onAuthorizationChanged, '科目表加载失败');
}

/** 某科目表下的科目；postingAllowed 为 true 时只取可过账科目。 */
export function useChartAccounts(chartCode: string | null, postingAllowed?: boolean, onAuthorizationChanged?: () => void) {
  const path = chartCode ? `${financePaths.chartAccounts(chartCode)}${postingAllowed ? '?postingAllowed=true' : ''}` : null;
  return useFinanceList<ChartAccount>(path, (url, signal) => session.request(url, { signal }, chartAccountList),
    onAuthorizationChanged, '科目加载失败');
}

/** 全部财务主体（每页 200 分批读完），供主体下拉与主体代码展示。 */
export function useFinanceEntities(enabled = true, onAuthorizationChanged?: () => void) {
  const state = useFinanceList<FinanceEntity>(enabled ? financePaths.financeEntities : null,
    (path, signal) => requestAllPages(path, signal, session, {}, entityPage), onAuthorizationChanged, '财务主体加载失败');
  const byId = useMemo(() => new Map(state.items.map(item => [item.financeEntityId, item])), [state.items]);
  return { ...state, byId };
}
