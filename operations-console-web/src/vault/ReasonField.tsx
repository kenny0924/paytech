import { Form, Input } from 'antd';

/** 策略变更理由：必填，写入 Vault 策略变更记录。 */
export function ReasonField() {
  return <Form.Item name="reason" label="变更理由" rules={[{ required: true, whitespace: true, message: '请填写变更理由' }]}>
    <Input.TextArea rows={2} maxLength={500} showCount />
  </Form.Item>;
}
