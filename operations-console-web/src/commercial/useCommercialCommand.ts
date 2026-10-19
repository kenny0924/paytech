import { useRef, useState } from 'react';
import type { FormInstance } from 'antd';
import { ApiError } from '../api/session';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { commercialCommand, commercialErrorMessage, fieldPath, sendCommercialCommand } from './commercial';
import type { CommandMethod } from './commercial';

/**
 * 商务写命令：单飞；403 刷新授权；409 置冲突横幅；422 字段清单就地显示；BUSINESS_CHECK_FAILED 显示在表单 / 弹窗顶部；
 * 其余交给通用通知。同一目标与正文的重试复用 Idempotency-Key。
 */
export function useCommercialCommand(onAuthorizationChanged: () => void, context: 'agreement' | 'profile' | 'version', form?: FormInstance) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const busy = useRef(false); const last = useRef<ConfigurationCommand>(undefined);
  async function run<T>(path: string, method: CommandMethod, body: unknown, decode: (value: unknown) => T, failureTitle: string): Promise<T | null> {
    if (busy.current) return null;
    busy.current = true; setSaving(true); setFormError(null);
    const command = commercialCommand(path, method, body, last.current); last.current = command;
    try {
      const result = await sendCommercialCommand(command, decode);
      last.current = undefined; setConflict(false);
      return result;
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status === 409) { setConflict(true); return null; }
      if (form && error instanceof ApiError && error.fieldErrors.length) {
        form.setFields(error.fieldErrors.map(item => ({ name: fieldPath(item.field), errors: [item.message] })));
        return null;
      }
      const message = commercialErrorMessage(error, context);
      if (message) { setFormError(message); return null; }
      notify(error, failureTitle);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, conflict, formError, run, clearError: () => setFormError(null) };
}

