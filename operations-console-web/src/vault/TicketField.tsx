import { Form, Input, Select } from 'antd';
import { useMemo } from 'react';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { reasonCodes, ticketPattern } from './vault';

/** 理由码与工单号：人工加/解密必填，工单号按 Vault 规则校验并给出格式提示。 */
export function ReasonAndTicketFields({ disabled }: { disabled?: boolean }) {
  const t = useT('vault');
  const reasonOptions = useMemo(() => reasonCodes.map(value => ({ value, label: enumLabel(t, 'enum.reasonCode', value) })), [t]);
  return <>
    <Form.Item name="reasonCode" label={t('ticket.reasonCode')} rules={[{ required: true, message: t('ticket.reasonCodeRequired') }]}>
      <Select disabled={disabled} placeholder={t('ticket.reasonCodePlaceholder')} options={reasonOptions} />
    </Form.Item>
    <Form.Item name="ticketRef" label={t('ticket.ticketRef')} extra={t('ticket.ticketRefHint')}
      rules={[{ required: true, message: t('ticket.ticketRefRequired') }, { pattern: ticketPattern, message: t('ticket.ticketRefPattern') }]}>
      <Input disabled={disabled} maxLength={64} autoComplete="off" placeholder={t('ticket.ticketRefPlaceholder')} />
    </Form.Item>
  </>;
}
