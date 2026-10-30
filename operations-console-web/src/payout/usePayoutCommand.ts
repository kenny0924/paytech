import { useRef, useState } from 'react';
import { App } from 'antd';
import { ApiError, session } from '../api/session';
import { configurationCommand } from '../configuration/configurationModel';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';

/** payout 合同中的业务拒绝：留在当前上下文展示原因，不弹通用错误。 */
const businessCodes = new Set(['FOUR_EYES_REQUIRED', 'BENEFICIARY_DUPLICATE', 'BENEFICIARY_STATUS_CONFLICT', 'BENEFICIARY_UNSUPPORTED_ROUTE',
  'CONCURRENT_REQUEST', 'BUSINESS_CHECK_FAILED', 'INVALID_REQUEST', 'VALIDATION_ERROR']);

/**
 * 收款账户写命令：单飞；同一路径与内容的人工重试沿用 Idempotency-Key，内容变化才换新键
 * <li>授权 403 刷新导航；四眼 403、409 冲突、400 / 422 业务拒绝保留在页面（failure），其余交给通用错误通知。</li>
 */
export function usePayoutCommand(onAuthorizationChanged: () => void) {
  const { message } = App.useApp(); const notify = useErrorNotification(); const t = useT('payout');
  const [saving, setSaving] = useState(false); const [failure, setFailure] = useState<ApiError>();
  const last = useRef<ConfigurationCommand>(undefined); const busy = useRef(false);
  async function run<T>(path: string, payload: unknown, decode: (value: unknown) => T, success?: string): Promise<T | null> {
    if (busy.current) return null;
    last.current = configurationCommand(path, 'POST', payload, last.current);
    busy.current = true; setSaving(true); setFailure(undefined);
    try {
      const command = last.current;
      const result = await session.request<T>(path, { method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key },
        body: command.body }, decode);
      last.current = undefined;
      if (success) void message.success(success);
      return result;
    } catch (error) {
      if (error instanceof ApiError && businessCodes.has(error.code)) { setFailure(error); return null; }
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      notify(error, t('command.failed'));
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, failure, run, clear: () => { setFailure(undefined); last.current = undefined; } };
}
