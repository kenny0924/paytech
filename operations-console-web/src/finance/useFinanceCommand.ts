import { useRef, useState } from 'react';
import type { FormInstance } from 'antd';
import { ApiError } from '../api/session';
import type { ConfigurationCommand } from '../configuration/configurationModel';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { financeCommand, financeErrorTarget, financeErrorTargets, sendFinanceCommand } from './finance';
import type { FinanceErrorRule } from './finance';
import { formFieldPath } from './financeWorkflow';

/**
 * 财务写命令：单飞；403 刷新授权；409 置冲突横幅；INVALID_REQUEST 的字段清单与已映射的业务错误码就地显示，
 * 字段不在表单内的映射错误显示在表单顶部；其余交给通用通知。同一目标与正文的重试复用 Idempotency-Key。
 * <li>字段清单中的列表路径（如 lines[0].amount）落到对应行；table 为错误码落点表，默认基础资料表。</li>
 */
export function useFinanceCommand(onAuthorizationChanged: () => void, form?: FormInstance, formFields: readonly string[] = [],
  table: Readonly<Record<string, FinanceErrorRule>> = financeErrorTargets) {
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const busy = useRef(false); const last = useRef<ConfigurationCommand>(undefined);
  async function run<T>(path: string, body: unknown, decode: (value: unknown) => T, failureTitle: string): Promise<T | null> {
    if (busy.current) return null;
    busy.current = true; setSaving(true); setFormError(null);
    const command = financeCommand(path, body, last.current); last.current = command;
    try {
      const result = await sendFinanceCommand(command, decode);
      last.current = undefined; setConflict(false);
      return result;
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status === 409) { setConflict(true); return null; }
      if (form && error instanceof ApiError && error.fieldErrors.length) {
        form.setFields(error.fieldErrors.map(item => ({ name: formFieldPath(item.field), errors: [item.message] })));
        return null;
      }
      const target = financeErrorTarget(error, formFields, table);
      if (target) {
        if (form && target.field) form.setFields([{ name: target.field, errors: [target.message] }]);
        else setFormError(target.message);
        return null;
      }
      notify(error, failureTitle);
      return null;
    } finally { busy.current = false; setSaving(false); }
  }
  return { saving, conflict, formError, run };
}
