import { useRef, useState } from 'react';
import { App, Button, Form, Input, Modal } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { configurationCommand } from './configurationModel';
import type { ConfigurationCommand } from './configurationModel';
import type { ConfigurationMutation } from './types';

export function ConfigurationStatusModal({ kind, code, version, enabled, onClose, onSaved, onAuthorizationChanged }: {
  kind: 'resource' | 'permission'; code: string; version: number; enabled: boolean;
  onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm(); const { message } = App.useApp(); const notify = useErrorNotification();
  const [saving, setSaving] = useState(false); const [conflict, setConflict] = useState(false);
  const busy = useRef(false); const previous = useRef<ConfigurationCommand>(undefined);
  const verb = enabled ? '停用' : '启用'; const subject = kind === 'resource' ? '资源' : '权限';
  async function save(values: { reason: string }) {
    if (busy.current || conflict) return;
    const path = kind === 'resource' ? '/console-admin/v1/resources' : '/permission-admin/v1/permissions';
    previous.current = configurationCommand(`${path}/${encodeURIComponent(code)}/status`, 'PATCH', {
      status: enabled ? kind === 'resource' ? 'DISABLED' : 'INACTIVE' : 'ACTIVE', expectedVersion: version, reason: values.reason.trim(),
    }, previous.current);
    busy.current = true; setSaving(true);
    try {
      const command = previous.current;
      const result = await session.request<ConfigurationMutation>(command.path, { method: command.method, body: command.body,
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } });
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
    <p className="muted">{kind === 'resource' ? '原有子项和权限绑定会保留。' : '原有角色及配置关联会保留，启用后按原关联生效。'}</p>
    {conflict ? <p>配置已有更新。<Button type="link" onClick={onSaved}>重新加载</Button></p> : null}
    <Form form={form} layout="vertical" disabled={saving || conflict} onFinish={save}>
      <Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount />
      </Form.Item>
    </Form>
  </Modal>;
}
