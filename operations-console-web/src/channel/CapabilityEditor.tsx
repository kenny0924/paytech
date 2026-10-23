import { useState } from 'react';
import { Button, Form, Input, Select, Switch } from 'antd';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  capabilityConditionIssues, capabilityDetailIssues, capabilityDetailsBody, channelPaths, decodeCapability, detailDrafts,
} from './channel';
import type { CapabilityDefinition, CapabilityFormValues, DetailDraft, MidCapability } from './channel';
import { ChannelNote, CommandModal } from './ChannelParts';
import { useChannelCommand } from './useChannelCommand';

interface Props {
  midId: string; definitions: CapabilityDefinition[]; capability?: MidCapability;
  onSaved: (capability: MidCapability) => void; onClose: () => void; onAuthorizationChanged: () => void;
}

const conditionRule = (field: keyof CapabilityFormValues) => ({
  validator: (_: unknown, value: string) => {
    const issue = capabilityConditionIssues({ paymentMethodCode: 'CARD', schemeCode: '*', countryCode: '*', currency: '*', mccCode: '0000', [field]: value })[field];
    return issue ? Promise.reject(new Error(issue)) : Promise.resolve();
  },
});

/** 一行明细：代码从目录选择，值控件按目录类型渲染（BOOLEAN 开关、DECIMAL 输入、CODE 下拉）。 */
function DetailRow({ draft, definitions, onChange, onRemove }: { draft: DetailDraft; definitions: CapabilityDefinition[]; onChange: (draft: DetailDraft) => void; onRemove: () => void }) {
  const t = useT('channel');
  const definition = definitions.find(d => d.capabilityCode === draft.capabilityCode);
  return <div className="channel-detail-row">
    <Select aria-label={t('capability.code')} showSearch placeholder={t('capability.selectCode')} value={draft.capabilityCode || undefined}
      options={definitions.map(d => ({ value: d.capabilityCode, label: d.capabilityCode }))}
      onChange={capabilityCode => onChange({ capabilityCode, booleanValue: definitions.find(d => d.capabilityCode === capabilityCode)?.valueType === 'BOOLEAN' ? true : undefined })} />
    <span className="channel-detail-type">{definition ? enumLabel(t, 'enum.valueType', definition.valueType) : '—'}</span>
    <div className="channel-detail-value">
      {definition?.valueType === 'BOOLEAN' ? <Switch aria-label={t('capability.supported')} checked={draft.booleanValue === true} onChange={booleanValue => onChange({ ...draft, booleanValue })}
        checkedChildren={t('capability.yes')} unCheckedChildren={t('capability.no')} />
        : definition?.valueType === 'DECIMAL' ? <Input aria-label={t('capability.decimal')} placeholder={t('capability.decimalPlaceholder')} maxLength={26} value={draft.decimalValue ?? ''}
          onChange={e => onChange({ ...draft, decimalValue: e.target.value.trim() })} />
        : definition?.valueType === 'CODE' ? <Select aria-label={t('capability.codeValue')} placeholder={t('capability.selectAllowed')} value={draft.codeValue}
          options={definition.allowedCodes.map(value => ({ value, label: value }))} onChange={codeValue => onChange({ ...draft, codeValue })} />
        : <span className="channel-cell-meta">{t('capability.pickCodeFirst')}</span>}
    </div>
    <Button type="text" size="small" danger onClick={onRemove}>{t('endpointEditor.delete')}</Button>
  </div>;
}

/**
 * 新建能力组（画板 08）或整组替换明细
 * <li>六维条件：支付方式精确；卡组 / 国家 / 币种可为 *；MCC 四位数字。六维组合在 MID 内唯一由 Owner 判定（重复 409）。</li>
 * <li>明细按能力目录渲染；每条只发送与目录类型一致的一个值。</li>
 */
export function CapabilityEditor({ midId, definitions, capability, onSaved, onClose, onAuthorizationChanged }: Props) {
  const t = useT('channel'), tCommon = useT('common');
  const [form] = Form.useForm<CapabilityFormValues>();
  const command = useChannelCommand(onAuthorizationChanged, form);
  const [drafts, setDrafts] = useState<DetailDraft[]>(capability ? detailDrafts(capability.details) : [{ capabilityCode: '' }]);
  const issue = capabilityDetailIssues(drafts, definitions);
  const save = async () => {
    if (issue) return;
    const details = capabilityDetailsBody(drafts, definitions);
    if (capability) {
      const saved = await command.run(channelPaths.capabilityDetails(capability.midCapabilityId), 'PUT', { details }, decodeCapability, t('capability.detailsSaveFailed'));
      if (saved) onSaved(saved);
      return;
    }
    const values = await form.validateFields();
    const saved = await command.run(channelPaths.capabilities, 'POST', { midId, ...values, details }, decodeCapability, t('capability.createFailed'));
    if (saved) onSaved(saved);
  };
  const upper = (value?: string) => value?.trim().toUpperCase();
  return <CommandModal title={capability ? t('capability.editTitle', { condition: [capability.paymentMethodCode, capability.schemeCode, capability.countryCode, capability.currency, capability.mccCode].join(' / ') })
    : t('capability.createTitle')} command={command} okText={tCommon('action.save')} width={820} okDisabled={!!issue} onOk={() => void save()} onClose={onClose} onReload={onClose}>
    {!capability && <Form form={form} name="channel-capability-create" layout="vertical" initialValues={{ schemeCode: '*', countryCode: '*', currency: '*' }}>
      <div className="channel-form-grid-3">
        <Form.Item name="paymentMethodCode" label={t('capability.paymentMethod')} normalize={upper} rules={[{ required: true, message: t('form.required') }, conditionRule('paymentMethodCode')]}>
          <Input maxLength={64} placeholder="CARD" /></Form.Item>
        <Form.Item name="schemeCode" label={t('capability.scheme')} normalize={upper} rules={[{ required: true, message: t('form.required') }, conditionRule('schemeCode')]}>
          <Input maxLength={64} placeholder={t('capability.schemePlaceholder')} /></Form.Item>
        <Form.Item name="countryCode" label={t('capability.country')} normalize={upper} rules={[{ required: true, message: t('form.required') }, conditionRule('countryCode')]}>
          <Input maxLength={2} placeholder={t('capability.countryPlaceholder')} /></Form.Item>
        <Form.Item name="currency" label={t('capability.currency')} normalize={upper} rules={[{ required: true, message: t('form.required') }, conditionRule('currency')]}>
          <Input maxLength={3} placeholder={t('capability.currencyPlaceholder')} /></Form.Item>
        <Form.Item name="mccCode" label="MCC" rules={[{ required: true, message: t('form.required') }, conditionRule('mccCode')]}>
          <Input maxLength={4} placeholder="5816" /></Form.Item>
      </div>
    </Form>}
    <div className="channel-kv-head"><strong>{t('capability.details')}</strong><span>{t('capability.detailsHint')}</span></div>
    {drafts.length > 0 && <div className="channel-detail-row channel-detail-head"><span>{t('capability.code')}</span><span>{t('capability.type')}</span><span>{t('accountConfig.value')}</span><span /></div>}
    {drafts.map((draft, index) => <DetailRow key={index} draft={draft} definitions={definitions}
      onChange={next => setDrafts(drafts.map((d, i) => i === index ? next : d))} onRemove={() => setDrafts(drafts.filter((_, i) => i !== index))} />)}
    <Button className="channel-detail-add" type="dashed" block disabled={drafts.length >= 64}
      onClick={() => setDrafts([...drafts, { capabilityCode: '' }])}>{t('capability.addDetail')}</Button>
    {issue && <ul className="channel-issues"><li>{issue}</li></ul>}
    <ChannelNote>{capability ? t('capability.replaceNote') : t('capability.createNote')}</ChannelNote>
  </CommandModal>;
}
