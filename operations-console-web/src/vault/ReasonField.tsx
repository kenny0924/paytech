import { Form, Input } from 'antd';
import { useT } from '../i18n/react';

/** 策略变更理由：必填，写入 Vault 策略变更记录。 */
export function ReasonField() {
  const t = useT('vault');
  return <Form.Item name="reason" label={t('reason.label')} rules={[{ required: true, whitespace: true, message: t('reason.required') }]}>
    <Input.TextArea rows={2} maxLength={500} showCount />
  </Form.Item>;
}
