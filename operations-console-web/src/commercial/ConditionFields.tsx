import { useState } from 'react';
import { Form, Input, Select, Switch, Tag, Tooltip } from 'antd';
import type { FormInstance } from 'antd';
import {
  addConditionValue, conditionControl, conditionOptions, conditionSummary, conditionValueIssue, conditionValueLabel, countryLabels, currencySelectOptions,
  dimensionLabel, mergeCurrencies, normalizeValues, orderedDimensions, splitCurrencies, toggleConditionValue, unrestricted,
} from './capabilityConditions';
import type { ConditionForm, DimensionDefinition } from './capabilityConditions';
import type { CapabilityDimension } from './commercialConsole';
import { CommercialNote } from './CommercialParts';
import './capabilityPricing.css';

interface ValueControl { value?: string[]; onChange?: (values: string[]) => void }

/** 「+ 其他」小输入：回车或失焦时追加一个取值，格式问题由行校验提示。 */
function OtherValue({ onAdd }: { onAdd: (value: string) => void }) {
  const [open, setOpen] = useState(false);
  const [text, setText] = useState('');
  const commit = () => { if (text.trim()) onAdd(text); setText(''); setOpen(false); };
  if (!open) return <Tag className="cc-other-tag" onClick={() => setOpen(true)}>+ 其他</Tag>;
  return <Input size="small" className="cc-other-input" autoFocus value={text} placeholder="代码" maxLength={64}
    onChange={event => setText(event.target.value.toUpperCase())} onPressEnter={event => { event.preventDefault(); commit(); }} onBlur={commit} />;
}

/** 枚举型平铺可选标签：首个为「不限」（未选即高亮，点它清空），末尾「+ 其他」。 */
function TagChoices({ dimensionCode, value, onChange }: ValueControl & { dimensionCode: string }) {
  const values = normalizeValues(value);
  return <span className="cc-choice-tags">
    <Tag.CheckableTag checked={unrestricted(values)} onChange={() => onChange?.([])}>不限</Tag.CheckableTag>
    {conditionOptions(dimensionCode, values).map(option => <Tag.CheckableTag key={option} checked={values.includes(option)}
      onChange={() => onChange?.(toggleConditionValue(values, option))}>{conditionValueLabel(dimensionCode, option)}</Tag.CheckableTag>)}
    <OtherValue onAdd={text => onChange?.(addConditionValue(values, text))} />
  </span>;
}

/**
 * 币种：常用快捷标签 + 可搜索多选下拉「更多币种」。一个取值数组，快捷标签管快捷币种、下拉只管其余币种（含清单外已有值），
 * 合并时先快捷、后下拉选择的顺序，不丢不重。下拉已选全部展开显示，不折叠。
 */
function CurrencyChoices({ value, onChange }: ValueControl) {
  const { quick, more } = splitCurrencies(value);
  return <span className="cc-choice-tags">
    {conditionOptions('CURRENCY', []).map(option => <Tag.CheckableTag key={option} checked={quick.includes(option)}
      onChange={() => onChange?.(mergeCurrencies(toggleConditionValue(quick, option), more))}>{option}</Tag.CheckableTag>)}
    <Select mode="multiple" allowClear className="cc-currency-select" placeholder="更多币种" value={more} optionFilterProp="label"
      popupMatchSelectWidth={false} onChange={(next: string[]) => onChange?.(mergeCurrencies(quick, next))}
      options={currencySelectOptions(more)} />
  </span>;
}

/** 国家类：可搜索多选，选项「AE 阿联酋」，可输入其他合法代码；空即不限。 */
function CountrySelect({ value, onChange }: ValueControl) {
  const values = normalizeValues(value);
  const codes = [...Object.keys(countryLabels), ...values.filter(code => !countryLabels[code])];
  return <Select mode="tags" allowClear value={values} placeholder="不限" tokenSeparators={[',', ' ']} optionFilterProp="label"
    onChange={(next: string[]) => onChange?.(normalizeValues(next))}
    options={codes.map(code => ({ value: code, label: countryLabels[code] ? `${code} ${countryLabels[code]}` : code }))} />;
}

/** 维度行的控件：按 conditionControl 选择。 */
function ConditionControlFor({ dimensionCode, value, onChange }: ValueControl & { dimensionCode: string }) {
  const control = conditionControl(dimensionCode);
  if (control === 'CURRENCY') return <CurrencyChoices value={value} onChange={onChange} />;
  if (control === 'ENUM') return <TagChoices dimensionCode={dimensionCode} value={value} onChange={onChange} />;
  if (control === 'COUNTRY') return <CountrySelect value={value} onChange={onChange} />;
  return <Select mode="tags" allowClear value={normalizeValues(value)} tokenSeparators={[',', ' ']} placeholder="不限，输入后回车可多个"
    onChange={(next: string[]) => onChange?.(normalizeValues(next))} options={[]} />;
}

/**
 * 接受条件区：每个可配维度一行（必填在前；中文名，代码在悬停提示，必填标 *），最后一行为「要求 3DS」开关，底部实时汇总一句话。
 * 校验：必填维度有值、每个取值格式正确；提交形状由 capabilityBody 决定。
 */
export function ConditionFields({ form, dimensions, definitions }: {
  form: FormInstance; dimensions: readonly CapabilityDimension[]; definitions: readonly DimensionDefinition[];
}) {
  const conditions = Form.useWatch('conditions', form) as ConditionForm | undefined;
  const require3ds = Form.useWatch('require3ds', form) as boolean | undefined;
  const row = { layout: 'horizontal' as const, labelCol: { flex: '120px' }, wrapperCol: { flex: 1 }, colon: false, className: 'cc-condition-row' };
  return <div className="cc-conditions">
    {dimensions.length === 0 && <CommercialNote>该开通项没有可配维度，开通即全量接受。</CommercialNote>}
    {orderedDimensions(dimensions).map(dimension => {
      const label = dimensionLabel(dimension.dimensionCode, definitions);
      return <Form.Item key={dimension.dimensionCode} {...row} name={['conditions', dimension.dimensionCode]} required={dimension.required}
        label={<Tooltip title={dimension.dimensionCode}><span>{label}</span></Tooltip>} rules={[{ validator: async (_, values?: string[]) => {
          const normalized = normalizeValues(values);
          if (dimension.required && normalized.length === 0) throw new Error(`${label}必填`);
          const issue = normalized.map(value => conditionValueIssue(dimension.dimensionCode, value)).find(Boolean);
          if (issue) throw new Error(issue);
        } }]}>
        <ConditionControlFor dimensionCode={dimension.dimensionCode} /></Form.Item>;
    })}
    <Form.Item {...row} name="require3ds" label="要求 3DS" valuePropName="checked" extra="开启后该开通项的交易必须完成 3DS（二期由 Payment 执行）">
      <Switch /></Form.Item>
    <div className="cc-condition-summary">{conditionSummary(dimensions, conditions, require3ds, definitions)}</div>
  </div>;
}
