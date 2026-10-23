import { useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { App, Button, Form, Input, Modal } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { configurationCommand } from './configurationModel';
import { currencySpecPath, decodeCurrencySpecMutation } from './currencySpecModel';
import type { ConfigurationCommand } from './configurationModel';
import type { ConfigurationMutation } from './types';
import { useT } from '../i18n/react';

/** Subject of the sentence, `gateway.configStatus.subject.<kind>` (lowercase noun in English). */
const subjects = { resource: 'configStatus.subject.resource', permission: 'configStatus.subject.permission', currency: 'configStatus.subject.currency' } as const;
const paths = { resource: '/console-admin/v1/resources', permission: '/permission-admin/v1/permissions', currency: currencySpecPath } as const;

/** 启停确认：notice 替换默认说明，币种回执按本次币种与目标状态严格解码。 */
export function ConfigurationStatusModal({ kind, code, version, enabled, notice, onClose, onSaved, onAuthorizationChanged }: {
  kind: keyof typeof subjects; code: string; version: number; enabled: boolean; notice?: ReactNode;
  onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('gateway'), tCommon = useT('common');
  const [form] = Form.useForm(); const { message } = App.useApp(); const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const busy = useRef(false); const previous = useRef<ConfigurationCommand>(undefined);
  const subject = t(subjects[kind]);
  const target = enabled ? kind === 'resource' ? 'DISABLED' : 'INACTIVE' : 'ACTIVE';
  async function save(values: { reason: string }) {
    if (busy.current || conflict) return;
    previous.current = configurationCommand(`${paths[kind]}/${encodeURIComponent(code)}/status`, 'PATCH', {
      status: target, expectedVersion: version, reason: values.reason.trim(),
    }, previous.current);
    busy.current = true; setSaving(true);
    try {
      const command = previous.current;
      const result = await session.request<ConfigurationMutation>(command.path, { method: command.method, body: command.body,
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } },
        kind === 'currency' && target !== 'DISABLED' ? value => decodeCurrencySpecMutation(value, code, target) : undefined);
      if (result.objectCode !== code || result.version !== version + 1) throw new ApiError(502, 'INVALID_RESPONSE');
      void message.success(t('config.saved')); onSaved();
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) setConflict(true);
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      notify(error, t(enabled ? 'configStatus.disableFailed' : 'configStatus.enableFailed', { subject }));
    } finally { busy.current = false; setSaving(false); }
  }
  return <Modal open title={t(enabled ? 'statusModal.disableTitle' : 'statusModal.enableTitle', { subject })}
    okText={t(enabled ? 'statusModal.confirmDisable' : 'statusModal.confirmEnable')} cancelText={tCommon('action.cancel')} confirmLoading={saving}
    okButtonProps={{ danger: enabled, disabled: conflict }} cancelButtonProps={{ disabled: saving }}
    closable={!saving} onCancel={onClose} onOk={() => form.submit()}>
    <p><strong>{code}</strong></p>
    {notice ?? <p className="muted">{kind === 'resource' ? t('configStatus.resourceNotice') : t('configStatus.permissionNotice')}</p>}
    {conflict ? <p>{t('configStatus.conflict')}<Button type="link" onClick={onSaved}>{t('configStatus.reload')}</Button></p> : null}
    <Form form={form} layout="vertical" disabled={saving || conflict} onFinish={save}>
      <Form.Item name="reason" label={tCommon('reason.label')} rules={[{ required: true, whitespace: true, message: tCommon('reason.required') }]}>
        <Input.TextArea rows={3} maxLength={500} showCount />
      </Form.Item>
    </Form>
  </Modal>;
}
