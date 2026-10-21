import { useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { App, Button, Form, Input, Modal } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { configurationCommand } from './configurationModel';
import { currencySpecPath, decodeCurrencySpecMutation } from './currencySpecModel';
import type { ConfigurationCommand } from './configurationModel';
import type { ConfigurationMutation } from './types';

const subjects = { resource: '资源', permission: '权限', currency: '币种' } as const;
const paths = { resource: '/console-admin/v1/resources', permission: '/permission-admin/v1/permissions', currency: currencySpecPath } as const;

/** 启停确认：notice 替换默认说明，币种回执按本次币种与目标状态严格解码。 */
export function ConfigurationStatusModal({ kind, code, version, enabled, notice, onClose, onSaved, onAuthorizationChanged }: {
  kind: keyof typeof subjects; code: string; version: number; enabled: boolean; notice?: ReactNode;
  onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm(); const { message } = App.useApp(); const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const busy = useRef(false); const previous = useRef<ConfigurationCommand>(undefined);
  const verb = enabled ? '停用' : '启用'; const subject = subjects[kind];
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
      void message.success('Platform 当前配置已保存'); onSaved();
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) setConflict(true);
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      notify(error, `${verb}${subject}失败`);
    } finally { busy.current = false; setSaving(false); }
  }
  return <Modal open title={`${verb}${subject}`} okText={`确认${verb}`} cancelText="取消" confirmLoading={saving}
    okButtonProps={{ danger: enabled, disabled: conflict }} cancelButtonProps={{ disabled: saving }}
    closable={!saving} onCancel={onClose} onOk={() => form.submit()}>
    <p><strong>{code}</strong></p>
    {notice ?? <p className="muted">{kind === 'resource' ? '原有子项和权限绑定会保留。' : '原有角色及配置关联会保留，启用后按原关联生效。'}</p>}
    {conflict ? <p>配置已有更新。<Button type="link" onClick={onSaved}>重新加载</Button></p> : null}
    <Form form={form} layout="vertical" disabled={saving || conflict} onFinish={save}>
      <Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount />
      </Form.Item>
    </Form>
  </Modal>;
}
