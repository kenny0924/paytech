import { useRef, useState } from 'react';
import type { FormInstance } from 'antd';
import { ApiError } from '../api/session';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { sendCommand, webhookCommand } from './webhook';

/**
 * Webhook 写请求：单飞、403 刷新授权、409 标记冲突（由弹窗横幅提示，不弹通知）、字段错误回填表单。
 * fieldError 可把特定业务错误（如地址指向内网）回填到对应字段；同一目标与正文的重试复用 Idempotency-Key。
 */
export function useWebhookCommand(onAuthorizationChanged: () => void, form?: FormInstance,
  fieldError?: (error: unknown) => { field: string; message: string } | null) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const busy = useRef(false); const last = useRef<ConfigurationCommand>(undefined);
  async function run<T>(path: string, method: ConfigurationCommand['method'], body: unknown, decode: (value: unknown) => T,
    failureTitle: string): Promise<T | null> {
    if (busy.current) return null;
    busy.current = true; setSaving(true);
    const command = webhookCommand(path, method, body, last.current); last.current = command;
    try {
      const result = await sendCommand(command, decode);
      last.current = undefined; setConflict(false);
      return result;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 403) onAuthorizationChanged();
        if (error.status === 409) setConflict(true);
        if (form && error.fieldErrors.length) form.setFields(error.fieldErrors.map(item => ({ name: item.field, errors: [item.message] })));
      }
      const mapped = fieldError?.(error);
      if (form && mapped) { form.setFields([{ name: mapped.field, errors: [mapped.message] }]); return null; }
      // 409 由横幅说明；字段错误已就地显示，不再叠加错误通知。
      if (error instanceof ApiError && (error.status === 409 || (form && error.fieldErrors.length))) return null;
      notify(error, failureTitle);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, conflict, run };
}
