import { useRef, useState } from 'react';
import type { FormInstance } from 'antd';
import { ApiError } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { sendStorageCommand, storageCommand } from './fileStorage';
import type { StorageCommand, StorageCommandMethod } from './fileStorage';

/**
 * 存储管理写请求：单飞、403 刷新授权、409 标记冲突（由弹窗横幅提示，不弹通知）、字段错误回填表单。
 * 同一目标与正文的重试复用 Idempotency-Key；改了内容才生成新 Key。业务拒绝的 Platform 原因由错误通知展示。
 */
export function useStorageCommand(onAuthorizationChanged: () => void, form?: FormInstance) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const busy = useRef(false); const last = useRef<StorageCommand>(undefined);
  async function run<T>(path: string, method: StorageCommandMethod, body: unknown, decode: (value: unknown) => T,
    failureTitle: string): Promise<T | null> {
    if (busy.current) return null;
    busy.current = true; setSaving(true);
    const command = storageCommand(path, method, body, last.current); last.current = command;
    try {
      const result = await sendStorageCommand(command, decode);
      last.current = undefined; setConflict(false);
      return result;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 403) onAuthorizationChanged();
        if (error.status === 409) setConflict(true);
        if (form && error.fieldErrors.length) form.setFields(error.fieldErrors.map(item => ({ name: item.field, errors: [item.message] })));
      }
      if (!(error instanceof ApiError && error.status === 409)) notify(error, failureTitle);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, conflict, run };
}
