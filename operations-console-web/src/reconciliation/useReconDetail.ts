import { useEffect, useRef, useState } from 'react';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { isNotFoundFailure } from './reconRoutes';

export interface ReconDetailState<T> { item: T | undefined; loading: boolean; notFound: boolean; error: string; reload: () => void }

/**
 * 详情页读取：path 为 null 表示主键非法，直接「未找到」不发请求
 * <li>404 或既有接口的「不存在: 主键」业务拒绝只置 notFound（页面显示未找到并可返回列表），不弹通知；403 刷新授权；其余失败弹通知并显示重试。</li>
 * <li>重新加载时保留上一次的数据，避免操作后整页闪烁。</li>
 */
export function useReconDetail<T>(path: string | null, decode: (value: unknown) => T, onAuthorizationChanged: () => void, failureTitle: string): ReconDetailState<T> {
  const notify = useErrorNotification();
  const [state, setState] = useState<{ path: string | null; item: T | undefined; loading: boolean; notFound: boolean; error: string }>(
    { path: null, item: undefined, loading: !!path, notFound: !path, error: '' });
  const [reload, setReload] = useState(0);
  const decoder = useRef(decode); decoder.current = decode;
  const authorizationChanged = useRef(onAuthorizationChanged); authorizationChanged.current = onAuthorizationChanged;
  useEffect(() => {
    if (!path) { setState({ path: null, item: undefined, loading: false, notFound: true, error: '' }); return; }
    const abort = new AbortController();
    setState(current => ({ path, item: current.path === path ? current.item : undefined, loading: true, notFound: false, error: '' }));
    session.request(path, { signal: abort.signal }, value => decoder.current(value))
      .then(item => { if (!abort.signal.aborted) setState({ path, item, loading: false, notFound: false, error: '' }); })
      .catch(failure => {
        if (abort.signal.aborted) return;
        if (failure instanceof ApiError && isNotFoundFailure(failure, path.split('/').at(-1) ?? '')) {
          setState({ path, item: undefined, loading: false, notFound: true, error: '' });
          return;
        }
        if (failure instanceof ApiError && failure.status === 403) authorizationChanged.current();
        setState({ path, item: undefined, loading: false, notFound: false, error: failure instanceof Error ? failure.message : '暂时无法加载，请重试。' });
        notify(failure, failureTitle);
      });
    return () => abort.abort();
  }, [path, reload, notify, failureTitle]);
  return { item: state.path === path ? state.item : undefined, loading: state.loading, notFound: state.notFound, error: state.error,
    reload: () => setReload(value => value + 1) };
}
