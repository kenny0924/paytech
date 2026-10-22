import { useRef, useState } from 'react';
import { ApiError, session } from '../api/session';
import { configurationCommand } from '../configuration/configurationModel';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { useErrorNotification } from '../feedback/useErrorNotification';

const genericBusinessDetail = '业务校验未通过';

/** Owner 拒绝原因：400 业务拒绝、422 文件策略拒绝、404 显示在弹窗顶部；字段错误汇总为一行；其余交给通用通知。 */
export function binLibraryErrorMessage(error: unknown): string | null {
  if (!(error instanceof ApiError)) return null;
  const owner = error.detail && error.detail !== genericBusinessDetail ? error.detail : null;
  if (error.fieldErrors.length) return error.fieldErrors.map(item => `${item.field}：${item.message}`).join('；');
  if (error.status === 404) return owner ?? '目标不存在，请刷新后重试。';
  if (error.status === 400 || error.status === 422) return owner ? `操作被拒绝：${owner}` : error.message;
  return null;
}

/**
 * 卡 BIN 库写命令：单飞；403 刷新授权；409 置冲突状态（页面提示刷新）；业务拒绝原因显示在弹窗顶部；其余交给通用通知。
 * 同一目标与正文的人工重试复用 Idempotency-Key。
 */
export function useBinLibraryCommand(onAuthorizationChanged: () => void) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const busy = useRef(false); const last = useRef<ConfigurationCommand>(undefined);
  async function run<T>(path: string, body: unknown, decode: (value: unknown) => T, failureTitle: string, signal?: AbortSignal): Promise<T | null> {
    if (busy.current) return null;
    busy.current = true; setSaving(true); setFormError(null);
    const command = configurationCommand(path, 'POST', body ?? {}, last.current); last.current = command;
    try {
      const result = await session.request<T>(path, { method: 'POST', body: body === undefined ? undefined : command.body, signal,
        // 无 Body 的命令（完成上传）同样声明 JSON，BFF 入站对写方法统一核对媒体类型。
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } }, decode);
      last.current = undefined; setConflict(false);
      return result;
    } catch (error) {
      if (signal?.aborted) return null;
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status === 409) { setConflict(true); return null; }
      const message = binLibraryErrorMessage(error);
      if (message) { setFormError(message); return null; }
      notify(error, failureTitle);
      setFormError(`${failureTitle}，请重试。`);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, conflict, formError, run, clearError: () => setFormError(null) };
}

export type BinLibraryCommand = ReturnType<typeof useBinLibraryCommand>;
