import { useRef, useState } from 'react';
import type { FormInstance } from 'antd';
import { ApiError } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { vaultCommand } from './vault';

/**
 * Vault 管理写请求：单飞、403 刷新授权、409 标记冲突、字段错误回填表单。
 * 不发送 Idempotency-Key；人工加密的防重由 submissionId 承担。
 */
export function useVaultCommand(onAuthorizationChanged: () => void, form?: FormInstance) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false); const busy = useRef(false);
  async function run<T>(path: string, method: 'POST' | 'PUT', body: unknown, decode: (value: unknown) => T, failureTitle: string): Promise<T | null> {
    if (busy.current) return null;
    busy.current = true; setSaving(true);
    try {
      const result = await vaultCommand(path, method, body, decode);
      setConflict(false);
      return result;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 403) onAuthorizationChanged();
        if (error.status === 409) setConflict(true);
        if (form && error.fieldErrors.length) form.setFields(error.fieldErrors.map(item => ({ name: item.field, errors: [item.message] })));
      }
      notify(error, failureTitle);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, conflict, run, resetConflict: () => setConflict(false) };
}
