import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { translator } from '../i18n/index';

const t = translator('workflow');

export type WorkflowMethod = 'POST' | 'PUT';
interface Command { path: string; method: WorkflowMethod; body: string; key: string }

/** 同一目标、方法与正文的人工重试复用 Idempotency-Key（Gateway 请求幂等，2026-09-17 决定）。 */
export function workflowCommand(path: string, method: WorkflowMethod, payload: unknown, previous?: Command): Command {
  const body = payload === undefined ? '' : JSON.stringify(payload);
  const same = previous?.path === path && previous.method === method && previous.body === body;
  return { path, method, body, key: same ? previous.key : crypto.randomUUID() };
}

/** 直接发送一次写请求（批量同意逐项调用时使用，每项独立幂等键）。 */
export function sendWorkflowCommand<T>(command: Command, decode: (value: unknown) => T): Promise<T> {
  return session.request<T>(command.path, { method: command.method, ...(command.body ? { body: command.body } : {}),
    // 无正文的命令（如回调重试）也带 JSON Content-Type，BFF 路由按 JSON 媒体类型匹配。
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } }, decode);
}

/** 业务拒绝的说明：400 BUSINESS_CHECK_FAILED 用后端中文 detail，409 提示刷新。 */
export function failureText(error: unknown): string {
  if (error instanceof ApiError) {
    // 409 带业务说明时（如“还有必选动作未完成”）用后端说明，否则提示刷新。
    if (error.status === 409) return error.detail || t('command.conflict');
    if (error.detail) return error.detail;
    return error.message || error.code;
  }
  return t('command.unfinished');
}

/** 422 字段错误附在说明后，如“config.fields[2].type 取值不合法…”。 */
export function fieldErrorText(error: ApiError): string {
  return error.fieldErrors.length
    ? t('command.fieldErrors', { list: error.fieldErrors.map(item => `${item.field} ${item.message}`).join(t('list.semicolon')) }) : '';
}

/**
 * 工作流写命令：单飞；403 刷新授权；409 置冲突状态；400 业务拒绝把后端 detail 放在 formError（页面就地展示）；
 * 其余交给通用错误通知（展示 code 与 requestId）。
 */
export function useWorkflowCommand(onAuthorizationChanged: () => void) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  /** 最近一次 409 的后端说明（如同意时必选动作未完成、节点已进入审批不能再修改）；没有说明时为 null。 */
  const [conflictMessage, setConflictMessage] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  /** 最近一次 400/422 的请求体字段错误（如 graph.nodes[2].type），供页面就地定位。 */
  const [fieldErrors, setFieldErrors] = useState<readonly { field: string; message: string }[]>([]);
  const busy = useRef(false); const last = useRef<Command>(undefined); const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const run = useCallback(async <T>(path: string, method: WorkflowMethod, body: unknown, decode: (value: unknown) => T, failureTitle: string): Promise<T | null> => {
    if (busy.current) return null;
    busy.current = true; setSaving(true); setFormError(null); setFieldErrors([]); setConflictMessage(null);
    const command = workflowCommand(path, method, body, last.current); last.current = command;
    try {
      const result = await sendWorkflowCommand(command, decode);
      last.current = undefined; if (mounted.current) setConflict(false);
      return result;
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status === 409) { if (mounted.current) { setConflict(true); setConflictMessage(error.detail || null); } return null; }
      if (error instanceof ApiError && (error.status === 400 || error.status === 422) && (error.detail || error.fieldErrors.length)) {
        if (mounted.current) {
          setFormError(t('command.formError', { detail: error.detail || t('command.invalid'), code: error.code, fields: fieldErrorText(error) }));
          setFieldErrors(error.fieldErrors);
        }
        return null;
      }
      notify(error, failureTitle);
      return null;
    } finally { busy.current = false; if (mounted.current) setSaving(false); }
  }, [notify, onAuthorizationChanged]);
  return { saving, conflict, conflictMessage, formError, fieldErrors, run, clearConflict: () => { setConflict(false); setConflictMessage(null); },
    clearError: () => { setFormError(null); setFieldErrors([]); setConflictMessage(null); } };
}
