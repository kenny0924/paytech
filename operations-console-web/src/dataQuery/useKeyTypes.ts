import { useEffect, useMemo, useState } from 'react';
import { requestAllPages } from '../api/paging';
import { session } from '../api/session';
import { dataQueryPaths } from './dataQueryCommon';
import { decodeKeyTypePage } from './keyTypeModel';
import type { QueryKeyType } from './keyTypeModel';

/**
 * 全部键类型（含停用，每页 200 分批读取），供模板编辑器选择、版本对比显示名称与键类型页使用。
 * 失败时为空列表并保留 failure，由调用方决定提示方式。
 */
export function useKeyTypes(enabled = true) {
  const [items, setItems] = useState<QueryKeyType[]>([]); const [loading, setLoading] = useState(enabled);
  const [failure, setFailure] = useState<unknown>(); const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController(); setLoading(true); setFailure(undefined);
    requestAllPages<QueryKeyType>(dataQueryPaths.keyTypes, controller.signal, session, {}, decodeKeyTypePage)
      .then(value => { if (!controller.signal.aborted) setItems(value); })
      .catch(error => { if (!controller.signal.aborted) setFailure(error); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [enabled, revision]);
  const byCode = useMemo(() => new Map(items.map(item => [item.keyTypeCode, item])), [items]);
  return { items, setItems, byCode, loading, failure, refresh: () => setRevision(value => value + 1) };
}
