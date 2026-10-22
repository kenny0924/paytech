import { useRef, useState } from 'react';
import type { FormInstance } from 'antd';
import { ApiError } from '../api/session';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { channelCommand, channelErrorMessage, fieldPath, sendChannelCommand } from './channel';
import type { CommandMethod } from './channel';

/**
 * 渠道写命令：单飞；403 刷新授权；409 置冲突横幅并保留 Owner 原因（版本冲突、引用保护、授权已存在）；422 字段清单就地显示；400 业务拒绝与 404 把 Owner 原因显示在页面 / 弹窗顶部；
 * 其余交给通用通知。同一目标与正文的重试复用 Idempotency-Key；body 为 undefined 时不发送请求体（提交审批）。
 */
export function useChannelCommand(onAuthorizationChanged: () => void, form?: FormInstance) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const [conflictDetail, setConflictDetail] = useState<string | null>(null);
  /** 最近一次失败的原始错误，供需要逐行定位字段错误的弹窗使用（例如出站地址）。 */
  const [lastError, setLastError] = useState<ApiError | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const busy = useRef(false); const last = useRef<ConfigurationCommand>(undefined);
  async function run<T>(path: string, method: CommandMethod, body: unknown, decode: (value: unknown) => T, failureTitle: string): Promise<T | null> {
    if (busy.current) return null;
    busy.current = true; setSaving(true); setFormError(null); setLastError(null);
    const command = channelCommand(path, method, body, last.current); last.current = command;
    try {
      const result = await sendChannelCommand(command, decode, body === undefined);
      last.current = undefined; setConflict(false);
      return result;
    } catch (error) {
      if (error instanceof ApiError) setLastError(error);
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status === 409) { setConflict(true); setConflictDetail(error.detail || null); return null; }
      if (form && error instanceof ApiError && error.fieldErrors.length) {
        form.setFields(error.fieldErrors.map(item => ({ name: fieldPath(item.field), errors: [item.message] })));
        return null;
      }
      if (error instanceof ApiError && error.fieldErrors.length) {
        setFormError(error.fieldErrors.map(item => `${item.field}：${item.message}`).join('；'));
        return null;
      }
      const message = channelErrorMessage(error);
      if (message) { setFormError(message); return null; }
      notify(error, failureTitle);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, conflict, conflictDetail, formError, lastError, run, clearError: () => setFormError(null) };
}
