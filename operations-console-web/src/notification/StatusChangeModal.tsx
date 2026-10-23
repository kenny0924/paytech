import type { ReactNode } from 'react';
import { Alert, Form, Input, Modal } from 'antd';
import { useT } from '../i18n/react';
import { decodeMutation } from './notification';
import type { ConfigStatus, Mutation } from './notification';
import { useNotificationCommand } from './useNotificationCommand';

interface Props {
  title: string; path: string; code: string; target: ConfigStatus; version: number; impact?: ReactNode;
  onClose: () => void; onDone: (result: Mutation) => void; onAuthorizationChanged: () => void;
}

/** 启停确认：说明影响并要求原因；409 时禁用并提示刷新。 */
export function StatusChangeModal({ title, path, code, target, version, impact, onClose, onDone, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<{ reason: string }>(); const t = useT('notification'), tCommon = useT('common');
  const command = useNotificationCommand(onAuthorizationChanged, form);
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(path, 'PATCH', { status: target, expectedVersion: version, reason: reason.trim() },
      value => decodeMutation(value, code), target === 'ACTIVE' ? t('statusChange.enableFailed') : t('statusChange.disableFailed'));
    if (result) onDone(result);
  }
  return <Modal open title={title} okText={target === 'ACTIVE' ? t('statusChange.confirmEnable') : t('statusChange.confirmDisable')} cancelText={tCommon('action.cancel')}
    okButtonProps={{ danger: target === 'DISABLED', disabled: command.conflict }} confirmLoading={command.saving}
    onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    {impact && <Alert type="warning" showIcon title={impact} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}>
      <Form.Item name="reason" label={t('statusChange.reason')} rules={[{ required: true, whitespace: true, message: t('statusChange.reasonRequired') }]}>
        <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>
    </Form>
    {command.conflict && <Alert type="error" showIcon title={t('statusChange.conflict')} />}
  </Modal>;
}
