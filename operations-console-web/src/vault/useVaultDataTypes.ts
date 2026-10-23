import { useEffect, useState } from 'react';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';
import { decodeDataTypeList, vaultAdminPath } from './vault';
import type { VaultDataTypeList } from './vault';

/** 读取全部数据类型与当前策略版本；reload 变化时重新读取。 */
export function useVaultDataTypes(reload = 0) {
  const notify = useErrorNotification(); const t = useT('vault');
  const [data, setData] = useState<VaultDataTypeList>({ currentPolicyVersion: 0, items: [] });
  const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setFailed(false);
    session.request(`${vaultAdminPath}/data-types`, { signal: abort.signal }, decodeDataTypeList)
      .then(value => { if (!abort.signal.aborted) setData(value); })
      .catch(error => { if (!abort.signal.aborted) { setFailed(true); notify(error, t('dataTypes.loadFailed')); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [reload, notify]);
  return { data, loading, failed };
}
