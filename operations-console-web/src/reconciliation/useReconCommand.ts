import { useRef, useState } from 'react';
import type { FormInstance } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { formFieldPath } from '../finance/financeWorkflow';
import { translator } from '../i18n/index';

const t = translator('recon');

export type ReconMethod = 'POST' | 'PUT' | 'DELETE';
interface ReconCommand { path: string; method: ReconMethod; body: string; key: string }

/** 同一目标、方法与正文的人工重试复用 Idempotency-Key；无请求体的命令不发送正文（Gateway 与 BFF 都拒绝多余 Body）。 */
function reconCommand(path: string, method: ReconMethod, payload: unknown, previous?: ReconCommand): ReconCommand {
  const body = payload === undefined ? '' : JSON.stringify(payload);
  const same = previous?.path === path && previous.method === method && previous.body === body;
  return { path, method, body, key: same ? previous.key : crypto.randomUUID() };
}

/**
 * 对账写命令：单飞；403 刷新授权；409 置冲突横幅；422 字段错误落到表单；BUSINESS_CHECK_FAILED 等业务拒绝把后端 detail 显示在表单顶部（AC-V6）；
 * 其余交给通用错误通知（同样展示后端 detail、code 与 requestId）。
 */
export function useReconCommand(onAuthorizationChanged: () => void, form?: FormInstance) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const busy = useRef(false); const last = useRef<ReconCommand>(undefined);
  async function run<T>(path: string, method: ReconMethod, body: unknown, decode: (value: unknown) => T, failureTitle: string): Promise<T | null> {
    if (busy.current) return null;
    busy.current = true; setSaving(true); setFormError(null);
    const command = reconCommand(path, method, body, last.current); last.current = command;
    try {
      const result = await session.request<T>(command.path, { method: command.method, ...(command.body ? { body: command.body } : {}),
        headers: { ...(command.body ? { 'Content-Type': 'application/json' } : {}), 'Idempotency-Key': command.key } }, decode);
      last.current = undefined; setConflict(false);
      return result;
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status === 409) { setConflict(true); return null; }
      if (form && error instanceof ApiError && error.fieldErrors.length) {
        form.setFields(error.fieldErrors.map(item => ({ name: formFieldPath(item.field), errors: [item.message] })));
        return null;
      }
      if (error instanceof ApiError && error.status === 400 && error.detail) {
        setFormError(t('common.detailWithCode', { detail: error.detail, code: error.code }));
        return null;
      }
      notify(error, failureTitle);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, conflict, formError, run, clearConflict: () => setConflict(false) };
}
