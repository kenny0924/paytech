import { useEffect, useState } from 'react';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { decodeDataTypeList, vaultAdminPath } from './vault';
import type { VaultDataTypeList } from './vault';

/** 读取全部数据类型与当前策略版本；reload 变化时重新读取。 */
export function useVaultDataTypes(reload = 0) {
  const notify = useErrorNotification();
  const [data, setData] = useState<VaultDataTypeList>({ currentPolicyVersion: 0, items: [] });
  const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setFailed(false);
    session.request(`${vaultAdminPath}/data-types`, { signal: abort.signal }, decodeDataTypeList)
      .then(value => { if (!abort.signal.aborted) setData(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, '数据类型加载失败'); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [reload, notify]);
  return { data, loading, failed };
}
