import { useEffect, useMemo, useRef, useState } from 'react';
import { changePaging, createPagingLoader, defaultPaging, pageSizeOptions } from '../api/paging';
import type { PageDecoder, PagingFilters, PagingRequestBody } from '../api/paging';
import type { PagingResponseBody } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';

/** 列表 Hook 的结果；F 为筛选类型，默认单值字符串，多选筛选可用 PagingFilters（数组展开为重复参数）。 */
export interface UsePagingResult<T, F extends PagingFilters = Readonly<Record<string, string>>> {
  data: PagingResponseBody<T>; loading: boolean; error: string; failure?: unknown; refresh: () => void;
  applyFilters: (filters: F) => void;
  pagination: { current: number; pageSize: number; total: number; showSizeChanger: true; pageSizeOptions: number[]; onChange: (page: number, size: number) => void; };
}
export function usePaging<T, F extends PagingFilters = Readonly<Record<string, string>>>(path: string, decodePage?: PageDecoder<T>,
  initialFilters: F = {} as F, options?: {
    initialPaging?: PagingRequestBody;
    enabled?: boolean;
    /** 失败时是否弹出错误通知；默认弹出。页面自行展示失败信息（含请求编号）时传 false。 */
    notifyOnError?: boolean;
    onQueryChange?: (query: { paging: PagingRequestBody; filters: F }, replace?: boolean) => void;
  }): UsePagingResult<T, F> {
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
          if (options?.notifyOnError !== false) notifyError(error, '列表加载失败');
        }
      }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [loadPage, paging.page, paging.size, filters, reload, notifyError, options?.enabled, options?.notifyOnError]);

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
  function applyFilters(next: F) {
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
