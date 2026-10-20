import { useState } from 'react';
import { Button, Form, Input, Select, Switch } from 'antd';
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
  const definition = definitions.find(d => d.capabilityCode === draft.capabilityCode);
  return <div className="channel-detail-row">
    <Select aria-label="能力代码" showSearch placeholder="能力代码" value={draft.capabilityCode || undefined}
      options={definitions.map(d => ({ value: d.capabilityCode, label: d.capabilityCode }))}
      onChange={capabilityCode => onChange({ capabilityCode, booleanValue: definitions.find(d => d.capabilityCode === capabilityCode)?.valueType === 'BOOLEAN' ? true : undefined })} />
    <span className="channel-cell-meta">{definition?.valueType ?? '—'}</span>
    {definition?.valueType === 'BOOLEAN' ? <Switch checked={draft.booleanValue === true} onChange={booleanValue => onChange({ ...draft, booleanValue })}
      checkedChildren="true" unCheckedChildren="false" />
      : definition?.valueType === 'DECIMAL' ? <Input aria-label="数值" placeholder="十进制，如 10.00" maxLength={26} value={draft.decimalValue ?? ''}
        onChange={e => onChange({ ...draft, decimalValue: e.target.value.trim() })} />
      : definition?.valueType === 'CODE' ? <Select aria-label="代码值" placeholder="选择允许值" value={draft.codeValue}
        options={definition.allowedCodes.map(value => ({ value, label: value }))} onChange={codeValue => onChange({ ...draft, codeValue })} />
      : <span className="channel-cell-meta">先选择能力代码</span>}
    <Button type="link" danger onClick={onRemove}>删除</Button>
  </div>;
}

/**
 * 新建能力组（画板 08）或整组替换明细
 * <li>六维条件：支付方式精确；体系 / 国家 / 币种可为 *；MCC 四位数字。六维组合在 MID 内唯一由 Owner 判定（重复 409）。</li>
 * <li>明细按能力目录渲染；每条只发送与目录类型一致的一个值。</li>
 */
export function CapabilityEditor({ midId, definitions, capability, onSaved, onClose, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<CapabilityFormValues>();
  const command = useChannelCommand(onAuthorizationChanged, form);
  const [drafts, setDrafts] = useState<DetailDraft[]>(capability ? detailDrafts(capability.details) : [{ capabilityCode: '' }]);
  const issue = capabilityDetailIssues(drafts, definitions);
  const save = async () => {
    if (issue) return;
    const details = capabilityDetailsBody(drafts, definitions);
    if (capability) {
      const saved = await command.run(channelPaths.capabilityDetails(capability.midCapabilityId), 'PUT', { details }, decodeCapability, '能力明细保存失败');
      if (saved) onSaved(saved);
      return;
    }
    const values = await form.validateFields();
    const saved = await command.run(channelPaths.capabilities, 'POST', { midId, ...values, details }, decodeCapability, '能力组创建失败');
    if (saved) onSaved(saved);
  };
  const upper = (value?: string) => value?.trim().toUpperCase();
  return <CommandModal title={capability ? `编辑明细 · ${[capability.paymentMethodCode, capability.schemeCode, capability.countryCode, capability.currency, capability.mccCode].join(' / ')}`
    : '新建能力组'} command={command} okText="保存" width={820} okDisabled={!!issue} onOk={() => void save()} onClose={onClose} onReload={onClose}>
    {!capability && <Form form={form} name="channel-capability-create" layout="vertical" initialValues={{ schemeCode: '*', countryCode: '*', currency: '*' }}>
      <div className="channel-form-grid-3">
        <Form.Item name="paymentMethodCode" label="支付方式" normalize={upper} rules={[{ required: true, message: '必填' }, conditionRule('paymentMethodCode')]}>
          <Input maxLength={64} placeholder="CARD" /></Form.Item>
        <Form.Item name="schemeCode" label="体系（可 *）" normalize={upper} rules={[{ required: true, message: '必填' }, conditionRule('schemeCode')]}>
          <Input maxLength={64} placeholder="VISA 或 *" /></Form.Item>
        <Form.Item name="countryCode" label="国家（可 *）" normalize={upper} rules={[{ required: true, message: '必填' }, conditionRule('countryCode')]}>
          <Input maxLength={2} placeholder="AE 或 *" /></Form.Item>
        <Form.Item name="currency" label="币种（可 *）" normalize={upper} rules={[{ required: true, message: '必填' }, conditionRule('currency')]}>
          <Input maxLength={3} placeholder="AED 或 *" /></Form.Item>
        <Form.Item name="mccCode" label="MCC" rules={[{ required: true, message: '必填' }, conditionRule('mccCode')]}>
          <Input maxLength={4} placeholder="5816" /></Form.Item>
      </div>
    </Form>}
    <div className="channel-kv-head"><strong>明细</strong><span>按能力目录渲染；ACTION.* 为是否支持，AMOUNT.MIN / MAX 为单笔上下限</span></div>
    {drafts.map((draft, index) => <DetailRow key={index} draft={draft} definitions={definitions}
      onChange={next => setDrafts(drafts.map((d, i) => i === index ? next : d))} onRemove={() => setDrafts(drafts.filter((_, i) => i !== index))} />)}
    <Button type="link" disabled={drafts.length >= 64} onClick={() => setDrafts([...drafts, { capabilityCode: '' }])}>+ 添加明细</Button>
    {issue && <ul className="channel-issues"><li>{issue}</li></ul>}
    <ChannelNote>{capability ? '保存会整组替换该能力组的全部明细。' : '新建后能力组为启用状态；六维组合在同一 MID 内唯一。'}</ChannelNote>
  </CommandModal>;
}
