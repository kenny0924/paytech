import { Form, Input, Select } from 'antd';
import { reasonCodeLabels, ticketPattern } from './vault';

/** 理由码与工单号：人工加/解密必填，工单号按 Vault 规则校验并给出格式提示。 */
export function ReasonAndTicketFields({ disabled }: { disabled?: boolean }) {
  return <>
    <Form.Item name="reasonCode" label="理由" rules={[{ required: true, message: '请选择理由' }]}>
      <Select disabled={disabled} placeholder="选择理由" options={Object.entries(reasonCodeLabels).map(([value, label]) => ({ value, label }))} />
    </Form.Item>
    <Form.Item name="ticketRef" label="工单号" extra="字母、数字及 . _ # -，最长 64 位"
      rules={[{ required: true, message: '请填写工单号' }, { pattern: ticketPattern, message: '工单号格式不正确' }]}>
      <Input disabled={disabled} maxLength={64} autoComplete="off" placeholder="例如 OPS-1024" />
    </Form.Item>
  </>;
}
