import { useEffect, useRef, useState } from 'react';
import { ApiError, session } from '../api/session';
import { decodePostingProgress, postingOpsPaths } from './postingOps';
import type { PostingProgress } from './postingOps';

export interface PostingProgressState {
  data: PostingProgress | null; error: unknown; loading: boolean; checkedAt: string | null; reload: () => void;
}

/**
 * B1 账簿入账进度：账簿为空时不请求；刷新时保留上一份数据直到新结果返回，换账簿时清空
 * <li>403 触发授权刷新；错误交给页面整页展示（ledger 不可用时 B1 整体 503）。</li>
 */
export function usePostingProgress(ledgerBookId: string | null, onAuthorizationChanged: () => void): PostingProgressState {
  const [state, setState] = useState<{ ledgerBookId: string | null; data: PostingProgress | null; error: unknown; loading: boolean; checkedAt: string | null }>(
    { ledgerBookId: null, data: null, error: null, loading: false, checkedAt: null });
  const [token, setToken] = useState(0);
  const authorizationChanged = useRef(onAuthorizationChanged); authorizationChanged.current = onAuthorizationChanged;
  useEffect(() => {
    if (!ledgerBookId) { setState({ ledgerBookId: null, data: null, error: null, loading: false, checkedAt: null }); return; }
    const abort = new AbortController();
    setState(current => current.ledgerBookId === ledgerBookId ? { ...current, loading: true }
      : { ledgerBookId, data: null, error: null, loading: true, checkedAt: null });
    session.request(postingOpsPaths.progress(ledgerBookId), { signal: abort.signal }, value => decodePostingProgress(value, ledgerBookId))
      .then(data => { if (!abort.signal.aborted) setState({ ledgerBookId, data, error: null, loading: false, checkedAt: new Date().toISOString() }); })
      .catch(error => {
        if (abort.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) authorizationChanged.current();
        setState(current => ({ ...current, ledgerBookId, error, loading: false }));
      });
    return () => abort.abort();
  }, [ledgerBookId, token]);
  const current = state.ledgerBookId === ledgerBookId;
  return { data: current ? state.data : null, error: current ? state.error : null, loading: state.loading, checkedAt: current ? state.checkedAt : null,
    reload: () => setToken(value => value + 1) };
}
