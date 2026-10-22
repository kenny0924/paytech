import { useRef, useState } from 'react';
import type { FormInstance } from 'antd';
import { ApiError, session } from '../api/session';
import { configurationCommand } from '../configuration/configurationModel';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { useErrorNotification } from '../feedback/useErrorNotification';

export type CommandMethod = 'POST' | 'PUT' | 'DELETE';
const genericBusinessDetail = '业务校验未通过';

/** Owner 拒绝原因：400 业务拒绝、404 与专有错误码显示在页面或弹窗顶部；其余交给通用通知。 */
export function dataQueryErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  const owner = error.detail && error.detail !== genericBusinessDetail ? error.detail : null;
  if (error.code === 'DATA_QUERY_TIMEOUT') return `查询超时，请缩小查询范围后重试。${owner ? `（${owner}）` : ''}`;
  if (error.code === 'DATA_QUERY_RESULT_TOO_LARGE') return `结果过大，请减少返回列或缩小范围。${owner ? `（${owner}）` : ''}`;
  if (error.code === 'DATA_SOURCE_UNAVAILABLE') return `数据源暂时无法连接。${owner ? `（${owner}）` : ''}`;
  if (error.status === 404) return owner ?? '目标不存在或已删除。';
  if (error.status === 400 && (error.code === 'BUSINESS_CHECK_FAILED' || error.code === 'INVALID_REQUEST')) return owner ? `操作被拒绝：${owner}` : '操作被拒绝，请检查输入。';
  return null;
}

/** 表单字段路径：connectionParams.sslMode → ['connectionParams', 'sslMode']；a[0].b → ['a', 0, 'b']。 */
export function fieldPath(field: string): (string | number)[] {
  return field.split('.').flatMap(part => {
    const match = /^([A-Za-z][A-Za-z0-9]*)\[(\d+)]$/.exec(part);
    return match ? [match[1], Number(match[2])] : [part];
  });
}

/**
 * 数据查询写命令：单飞；403 刷新授权；409 置冲突状态（页面提示刷新）；422 字段错误交给表单或调用方；
 * 业务拒绝原因显示在顶部；其余交给通用通知。同一目标与正文的人工重试复用 Idempotency-Key。
 */
export function useDataQueryCommand(onAuthorizationChanged: () => void, form?: FormInstance) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<readonly { field: string; message: string }[]>([]);
  const busy = useRef(false); const last = useRef<ConfigurationCommand>(undefined);
  async function run<T>(path: string, method: CommandMethod, body: unknown, decode: (value: unknown) => T, failureTitle: string): Promise<T | null> {
    if (busy.current) return null;
    busy.current = true; setSaving(true); setFormError(null); setFieldErrors([]);
    const withBody = method !== 'DELETE';
    const command = configurationCommand(path, method === 'DELETE' ? 'POST' : method, body ?? null, last.current); last.current = command;
    try {
      const result = await session.request<T>(path, { method, ...(withBody ? { body: command.body } : {}),
        headers: { ...(withBody ? { 'Content-Type': 'application/json' } : {}), 'Idempotency-Key': command.key } }, decode);
      last.current = undefined; setConflict(false);
      return result;
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status === 409) { setConflict(true); return null; }
      if (error instanceof ApiError && error.fieldErrors.length) {
        setFieldErrors(error.fieldErrors);
        // 表单字段就地显示；同时给出汇总，避免表单中没有对应控件（例如 Map 键）的错误被忽略。
        if (form) form.setFields(error.fieldErrors.map(item => ({ name: fieldPath(item.field), errors: [item.message] })));
        setFormError(error.fieldErrors.map(item => `${item.field}：${item.message}`).join('；'));
        return null;
      }
      const message = dataQueryErrorMessage(error);
      if (message) { setFormError(message); return null; }
      notify(error, failureTitle);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, conflict, formError, fieldErrors, run, clearError: () => setFormError(null), resetConflict: () => setConflict(false) };
}

export type DataQueryCommand = ReturnType<typeof useDataQueryCommand>;
