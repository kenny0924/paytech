import { useRef, useState } from 'react';
import { App } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { configurationCommand } from '../configuration/configurationModel';
import type { ConfigurationCommand } from '../configuration/configurationModel';

/**
 * 主体资格、经营主体与 Local Partner 的写命令：同一路径与内容重试沿用原 Idempotency-Key，内容变化才换新键。
 * <li>403 刷新导航权限；409 / 422 保留在弹窗内由页面按错误码展示原因（版本冲突提示刷新），不静默覆盖。</li>
 * <li>failedTitle 是调用方命名空间里的“操作未完成”标题。</li>
 */
export function useAdminCommand(onAuthorizationChanged: () => void, failedTitle: string) {
  const { message } = App.useApp(); const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [failure, setFailure] = useState<ApiError>();
  const last = useRef<ConfigurationCommand>(undefined); const busy = useRef(false);
  async function run<T>(path: string, method: ConfigurationCommand['method'], payload: unknown, decode: (value: unknown) => T, success: string) {
    if (busy.current) return null;
    last.current = configurationCommand(path, method, payload, last.current);
    busy.current = true; setSaving(true); setFailure(undefined);
    try {
      const command = last.current;
      const result = await session.request<T>(path, { method, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key },
        body: command.body }, decode);
      last.current = undefined; void message.success(success);
      return result;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 403) onAuthorizationChanged();
        setFailure(error);
        if (error.status === 409 || error.status === 422 || error.status === 404) return null;
      }
      notify(error, failedTitle);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, failure, run };
}
