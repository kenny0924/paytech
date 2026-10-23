import { useRef, useState } from 'react';
import { App } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { configurationCommand } from '../configuration/configurationModel';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { useT } from '../i18n/react';

/**
 * 审核命令：同一路径与内容重试沿用原 Idempotency-Key，内容变化才换新键
 * <li>403 刷新导航权限；409/422 保留在当前上下文由页面展示原因，不静默覆盖。</li>
 */
export function useReviewCommand(onAuthorizationChanged: () => void) {
  const { message } = App.useApp(); const notify = useErrorNotification(); const t = useT('client-review');
  const [saving, setSaving] = useState(false); const [failure, setFailure] = useState<ApiError>();
  const last = useRef<ConfigurationCommand>(undefined); const busy = useRef(false);
  async function run<T>(path: string, method: ConfigurationCommand['method'], payload: unknown, decode: (value: unknown) => T, success: string) {
    if (busy.current) return null;
    last.current = configurationCommand(path, method, payload, last.current);
    busy.current = true; setSaving(true); setFailure(undefined);
    try {
      const command = last.current;
      const result = await session.request<T>(path, { method, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key },
        body: payload === undefined ? undefined : command.body }, decode);
      last.current = undefined; void message.success(success);
      return result;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 403) onAuthorizationChanged();
        setFailure(error);
      }
      notify(error, t('command.failed'));
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, failure, run, clear: () => { setFailure(undefined); last.current = undefined; } };
}
