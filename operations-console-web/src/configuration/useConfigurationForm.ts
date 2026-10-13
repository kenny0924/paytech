import { useCallback, useRef, useState } from 'react';
import { App } from 'antd';
import type { FormInstance } from 'antd';
import { ApiError, session } from '../api/session';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { configurationCommand } from './configurationModel';
import type { ConfigurationCommand } from './configurationModel';
import type { ConfigurationMutation } from './types';

export function useConfigurationForm(onAuthorizationChanged: () => void, form?: FormInstance) {
  const { modal, message } = App.useApp(); const notify = useErrorNotification();
  const [dirty, setDirty] = useState(false); const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false); const busy = useRef(false); const last = useRef<ConfigurationCommand>(undefined);
  const [fieldErrors, setFieldErrors] = useState<ApiError['fieldErrors']>([]);
  const confirmLeave = useCallback(() => new Promise<boolean>(resolve => {
    modal.confirm({ title: '离开当前编辑？', content: '尚未保存的修改将丢失。', okText: '离开', cancelText: '继续编辑',
      onOk: () => resolve(true), onCancel: () => resolve(false) });
  }), [modal]);
  useUnsavedRuleGuard(dirty, confirmLeave);
  async function save<T = ConfigurationMutation>(path: string, method: ConfigurationCommand['method'], payload: unknown, decode?: (value: unknown) => T) {
    if (busy.current || conflict) return null;
    last.current = configurationCommand(path, method, payload, last.current);
    busy.current = true; setSaving(true);
    try {
      const command = last.current;
      const wire = await session.request<unknown>(path, { method,
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key }, body: command.body });
      const result = decode ? decode(wire) : wire as ConfigurationMutation;
      const configuration = result as ConfigurationMutation;
      if (!decode && (!configuration || !configuration.objectId || !configuration.objectCode || !Number.isInteger(configuration.version) || configuration.version < 1)) {
        throw new ApiError(502, 'INVALID_RESPONSE');
      }
      setDirty(false); setConflict(false); setFieldErrors([]); last.current = undefined;
      void message.success('Platform 当前配置已保存');
      return result as T;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 403) onAuthorizationChanged();
        if (error.status === 409) setConflict(true);
        setFieldErrors(error.fieldErrors);
        if (form && error.fieldErrors.length) form.setFields(error.fieldErrors.map(item => ({
          name: item.field.replace(/\[(\d+)\]/g, '.$1').split('.').map(part => /^\d+$/.test(part) ? Number(part) : part),
          errors: [item.message] })));
      }
      notify(error, '配置保存失败'); return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { dirty, setDirty, saving, conflict, fieldErrors, save, reset: () => { setDirty(false); setConflict(false); setFieldErrors([]); last.current = undefined; } };
}
