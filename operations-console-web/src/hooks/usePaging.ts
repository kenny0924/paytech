import { useEffect, useMemo, useRef, useState } from 'react';
import { changePaging, createPagingLoader, defaultPaging, pageSizeOptions } from '../api/paging';
import type { PageDecoder, PagingRequestBody } from '../api/paging';
import type { PagingResponseBody } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';

export interface UsePagingResult<T> {
  data: PagingResponseBody<T>; loading: boolean; error: string; failure?: unknown; refresh: () => void;
  applyFilters: (filters: Readonly<Record<string, string>>) => void;
  pagination: { current: number; pageSize: number; total: number; showSizeChanger: true; pageSizeOptions: number[]; onChange: (page: number, size: number) => void; };
}
export function usePaging<T>(path: string, decodePage?: PageDecoder<T>,
  initialFilters: Readonly<Record<string, string>> = {}, options?: {
    initialPaging?: PagingRequestBody;
    enabled?: boolean;
    onQueryChange?: (query: { paging: PagingRequestBody; filters: Readonly<Record<string, string>> }, replace?: boolean) => void;
  }): UsePagingResult<T> {
  const notifyError = useErrorNotification();
  const loadPage = useMemo(() => createPagingLoader(path, decodePage), [path, decodePage]);
  const [{ paging, filters }, setQuery] = useState({ paging: options?.initialPaging ?? defaultPaging, filters: { ...initialFilters } });
  const [data, setData] = useState<PagingResponseBody<T>>({ ...defaultPaging, items: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [failure, setFailure] = useState<unknown>();
  const [reload, setReload] = useState(0);
  const pending = useRef<AbortController | null>(null);

  useEffect(() => {
    if (options?.enabled === false) { setLoading(false); return; }
    const controller = new AbortController(); pending.current = controller;
    setLoading(true); setError(''); setFailure(undefined);
    loadPage(paging, controller.signal, filters)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(error => {
        if (!controller.signal.aborted) {
          setFailure(error);
          setError(error instanceof Error ? error.message : '暂时无法加载列表，请重试。');
          notifyError(error, '列表加载失败');
        }
      }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [loadPage, paging.page, paging.size, filters, reload, notifyError, options?.enabled]);

  function onChange(page: number, size: number) {
    const next = changePaging(paging, page, size);
    if (next.page === paging.page && next.size === paging.size) return;
    pending.current?.abort();
    if (options?.onQueryChange) options.onQueryChange({ filters, paging: next });
    else setQuery(current => ({ ...current, paging: next }));
  }
  function refresh() {
    pending.current?.abort(); setReload(value => value + 1);
  }
  function applyFilters(next: Readonly<Record<string, string>>) {
    pending.current?.abort();
    const query = { paging: { ...paging, page: 1 }, filters: { ...next } };
    if (options?.onQueryChange) options.onQueryChange(query);
    else setQuery(query);
  }
  return { data, loading, error, failure, refresh, applyFilters, pagination: {
    current: paging.page, pageSize: paging.size, total: data.total,
    showSizeChanger: true, pageSizeOptions, onChange,
  } };
}
