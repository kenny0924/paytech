import { useRef, useState } from 'react';
import { App } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { configurationCommand } from '../configuration/configurationModel';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { useT } from '../i18n/react';

/**
 * 承保写命令：结果未知后同一路径与内容的重试沿用原 Idempotency-Key，内容变化或确定失败后换新键
 * <li>403 刷新导航权限；409 / 422 留在页面上（CommandAlert）展示原因并保留输入，其他失败弹出带请求编号的通知。</li>
 * <li>同一时间只发一个命令。</li>
 */
export function useUnderwritingCommand(onAuthorizationChanged: () => void) {
  const { message } = App.useApp(); const notify = useErrorNotification(); const t = useT('risk');
  const [saving, setSaving] = useState(false); const [failure, setFailure] = useState<ApiError>();
  const last = useRef<ConfigurationCommand>(undefined); const busy = useRef(false);
  async function run<T>(path: string, method: 'POST' | 'PUT', payload: unknown, decode: (value: unknown) => T, success: string | null): Promise<T | null> {
    if (busy.current) return null;
    last.current = configurationCommand(path, method, payload, last.current);
    busy.current = true; setSaving(true); setFailure(undefined);
    try {
      const command = last.current;
      const result = await session.request<T>(path, { method, headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key }, body: command.body }, decode);
      last.current = undefined;
      if (success) void message.success(success);
      return result;
    } catch (error) {
      if (error instanceof ApiError) {
        // 确定的 4xx 不会因重试改变结果：下一次命令换新键；网络中断与 5xx 结果未知，重试沿用原键。
        if (error.status >= 400 && error.status < 500) last.current = undefined;
        if (error.status === 403) onAuthorizationChanged();
        if (error.status === 409 || error.status === 422) { setFailure(error); return null; }
      }
      notify(error, t('underwriting.command.failed'));
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, failure, run, clear: () => { setFailure(undefined); last.current = undefined; } };
}
