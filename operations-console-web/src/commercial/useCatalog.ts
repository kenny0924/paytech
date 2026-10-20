import { useEffect, useRef, useState } from 'react';
import { ApiError, session } from '../api/session';
import { consolePaths, decodeCapabilities, decodeFeeEvents, decodeProducts } from './commercialConsole';
import type { CatalogCapability, CatalogFeeEvent, CatalogProduct } from './commercialConsole';

export interface CatalogState<T> { items: T[]; loading: boolean; failed: boolean }

/** 读取一个目录接口；403 刷新授权，失败时 failed=true，表单退回手输。 */
function useCatalogList<T>(path: string, decode: (value: unknown) => T[], onAuthorizationChanged?: () => void): CatalogState<T> {
  const [state, setState] = useState<CatalogState<T>>({ items: [], loading: true, failed: false });
  const authorizationChanged = useRef(onAuthorizationChanged); authorizationChanged.current = onAuthorizationChanged;
  useEffect(() => {
    const controller = new AbortController();
    setState(current => ({ ...current, loading: true }));
    session.request(path, { signal: controller.signal }, decode)
      .then(items => { if (!controller.signal.aborted) setState({ items, loading: false, failed: false }); })
      .catch(error => {
        if (controller.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) authorizationChanged.current?.();
        setState({ items: [], loading: false, failed: true });
      });
    return () => controller.abort();
  }, [path, decode]);
  return state;
}

/** 产品目录（全部状态）。 */
export function useCatalogProducts(onAuthorizationChanged?: () => void): CatalogState<CatalogProduct> {
  return useCatalogList(consolePaths.products, decodeProducts, onAuthorizationChanged);
}

/** 可开通项目录（全部状态）。 */
export function useCatalogCapabilities(onAuthorizationChanged?: () => void): CatalogState<CatalogCapability> {
  return useCatalogList(consolePaths.capabilities, decodeCapabilities, onAuthorizationChanged);
}

/** 计费事件目录；termType 非空时只列该条款类型登记的事件。 */
export function useCatalogFeeEvents(termType?: string, onAuthorizationChanged?: () => void): CatalogState<CatalogFeeEvent> {
  const path = termType ? `${consolePaths.feeEvents}?termType=${encodeURIComponent(termType)}` : consolePaths.feeEvents;
  return useCatalogList(path, decodeFeeEvents, onAuthorizationChanged);
}
