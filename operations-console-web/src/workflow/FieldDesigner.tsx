import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Empty, Form, Input, InputNumber, Segmented, Select, Space, Switch, Tag } from 'antd';
import { addField, duplicateField, fieldProblems, moveField, removeField, updateField, valueProblems } from './formModel';
import { graphLimits } from './graphModel';
import { fieldTypeLabels } from './workflowLabels';
import type { EnumOption, FieldRules, FieldType, FormDefinition, FormField } from './workflowTypes';
import { DynamicFields, Field } from './WorkflowParts';

const fieldTypes: FieldType[] = ['TEXT', 'LONG_TEXT', 'NUMBER', 'BOOLEAN', 'ENUM', 'DATE', 'FILE'];
const fieldIcons: Record<FieldType, string> = { TEXT: 'T', LONG_TEXT: '¶', NUMBER: '#', BOOLEAN: '◐', ENUM: '☰', DATE: '▦', FILE: '⎘' };

/**
 * 字段设计器（启动表单与 FORM 动作共用，原型 11、14）：左侧字段类型、中间字段列表（排序、复制、删除、重复键报错）与预览、
 * 右侧字段属性（枚举选项、数值范围、文件规则、被连线条件引用的提示）。
 */
export function FieldDesigner({ form, onChange, readOnly, variablePrefix, references, previewTitle }: {
  form: FormDefinition; onChange: (form: FormDefinition) => void; readOnly?: boolean; variablePrefix: string;
  references?: (field: FormField) => ReactNode[]; previewTitle?: string;
}) {
  const [selected, setSelected] = useState(0);
  const [mode, setMode] = useState<'edit' | 'preview'>('edit');
  const problems = fieldProblems(form);
  const field = form.fields[selected];
  function add(type: FieldType) {
    if (form.fields.length >= graphLimits.formFields) return;
    const result = addField(form, type); onChange(result.form); setSelected(result.index);
  }
  return <div className="wf-designer">
    <aside className="wf-card wf-palette">
      <div className="wf-card-title">字段类型</div><p className="muted">点击添加到表单（{form.fields.length} / {graphLimits.formFields}）</p>
      {fieldTypes.map(type => <button type="button" key={type} className="wf-palette-item" disabled={readOnly || form.fields.length >= graphLimits.formFields}
        onClick={() => add(type)}><span className="wf-palette-icon">{fieldIcons[type]}</span>{fieldTypeLabels[type]}</button>)}
      {problems.size > 0 && <Alert type="error" showIcon title={[...problems.values()][0]} />}
    </aside>
    <section className="wf-card wf-designer-center">
      <div className="wf-card-title">{previewTitle ?? '字段'}<Segmented size="small" value={mode} onChange={value => setMode(value as typeof mode)}
        options={[{ value: 'edit', label: '编辑' }, { value: 'preview', label: '预览' }]} /></div>
      {mode === 'preview' ? <FormPreview form={form} /> : !form.fields.length ? <Empty description="从左侧添加字段" />
        : form.fields.map((item, index) => <div key={index} role="button" tabIndex={0} onClick={() => setSelected(index)}
          className={`wf-field-row${index === selected ? ' is-selected' : ''}${problems.has(index) ? ' has-error' : ''}`}>
          <span className="wf-palette-icon">{fieldIcons[item.type]}</span>
          <div className="wf-grow"><strong>{item.label || '（未命名）'}{item.required && ' *'}</strong>
            <div className="muted"><code>{variablePrefix}{item.key}</code> · {fieldTypeLabels[item.type]}</div>
            {problems.has(index) && <div className="wf-danger-text">{problems.get(index)}</div>}</div>
          {!readOnly && <Space size={2} onClick={event => event.stopPropagation()}>
            <Button size="small" type="text" disabled={index === 0} onClick={() => { onChange(moveField(form, index, index - 1)); setSelected(index - 1); }}>↑</Button>
            <Button size="small" type="text" disabled={index === form.fields.length - 1} onClick={() => { onChange(moveField(form, index, index + 1)); setSelected(index + 1); }}>↓</Button>
            <Button size="small" type="text" onClick={() => { onChange(duplicateField(form, index)); setSelected(index + 1); }}>复制</Button>
            <Button size="small" type="text" danger onClick={() => { onChange(removeField(form, index)); setSelected(Math.max(0, index - 1)); }}>删除</Button>
          </Space>}
        </div>)}
    </section>
    <aside className="wf-card wf-designer-props">
      {field ? <FieldProperties key={selected} field={field} readOnly={readOnly} variablePrefix={variablePrefix}
        references={references?.(field) ?? []} onChange={patch => onChange(updateField(form, selected, patch))} />
        : <Empty description="选择一个字段查看属性" />}
    </aside>
  </div>;
}

function FieldProperties({ field, onChange, readOnly, variablePrefix, references }: {
  field: FormField; onChange: (patch: Partial<FormField>) => void; readOnly?: boolean; variablePrefix: string; references: ReactNode[];
}) {
  const rules = field.rules ?? {};
  const setRules = (patch: Partial<FieldRules>) => onChange({ rules: { ...rules, ...patch } });
  const options = rules.options ?? [];
  const setOption = (index: number, patch: Partial<EnumOption>) => setRules({ options: options.map((option, current) => current === index ? { ...option, ...patch } : option) });
  const decimal = (value: string | null) => value === null || value === '' ? undefined : String(value);
  return <div className="wf-props">
    <div className="wf-card-title">{field.label || field.key}<Tag>{fieldTypeLabels[field.type]}</Tag></div>
    <div className="wf-two-columns">
      <Field label="显示名称 *"><Input value={field.label} maxLength={64} disabled={readOnly} onChange={event => onChange({ label: event.target.value })} /></Field>
      <Field label="字段键 *" hint={`引用为 ${variablePrefix}${field.key}`}><Input value={field.key} maxLength={64} disabled={readOnly} onChange={event => onChange({ key: event.target.value.trim() })} /></Field>
    </div>
    <Field label="类型"><Select value={field.type} disabled={readOnly} style={{ width: '100%' }} options={fieldTypes.map(type => ({ value: type, label: fieldTypeLabels[type] }))}
      onChange={(type: FieldType) => onChange({ type, rules: type === 'ENUM' ? { options: options.length ? options : [{ value: 'OPTION_1', label: '选项 1' }] }
        : type === 'FILE' ? { minFiles: 1, maxFiles: 5, mimeTypes: [] } : undefined })} /></Field>
    <label className="wf-inline"><Switch size="small" checked={field.required} disabled={readOnly} onChange={required => onChange({ required })} /> 必填</label>
    {(field.type === 'TEXT' || field.type === 'LONG_TEXT') && <Field label="最大长度" hint={field.type === 'TEXT' ? '单行文本最多 255' : '多行文本最多 4000'}>
      <InputNumber min={1} max={field.type === 'TEXT' ? 255 : 4000} value={rules.maxLength} disabled={readOnly} onChange={value => setRules({ maxLength: value ?? undefined })} /></Field>}
    {field.type === 'NUMBER' && <div className="wf-three-columns">
      <Field label="最小值"><InputNumber stringMode value={rules.min} disabled={readOnly} onChange={value => setRules({ min: decimal(value) })} /></Field>
      <Field label="最大值"><InputNumber stringMode value={rules.max} disabled={readOnly} onChange={value => setRules({ max: decimal(value) })} /></Field>
      <Field label="小数位"><InputNumber min={0} max={10} value={rules.scale} disabled={readOnly} onChange={value => setRules({ scale: value ?? undefined })} /></Field>
    </div>}
    {field.type === 'ENUM' && <Field label="选项（值 · 显示文字）" hint="选项值保存后被连线条件引用，修改或删除会让引用失效">
      {options.map((option, index) => <div key={index} className="wf-option-row">
        <Input value={option.value} disabled={readOnly} placeholder="值" onChange={event => setOption(index, { value: event.target.value.trim() })} />
        <Input value={option.label} disabled={readOnly} placeholder="显示文字" onChange={event => setOption(index, { label: event.target.value })} />
        {!readOnly && <Button type="text" danger onClick={() => setRules({ options: options.filter((_, current) => current !== index) })}>✕</Button>}
      </div>)}
      {!readOnly && <Button type="link" onClick={() => setRules({ options: [...options, { value: `OPTION_${options.length + 1}`, label: '' }] })}>＋ 添加选项</Button>}
    </Field>}
    {field.type === 'FILE' && <>
      <div className="wf-two-columns">
        <Field label="最少份数"><InputNumber min={0} max={20} value={rules.minFiles} disabled={readOnly} onChange={value => setRules({ minFiles: value ?? undefined })} /></Field>
        <Field label="最多份数"><InputNumber min={1} max={20} value={rules.maxFiles} disabled={readOnly} onChange={value => setRules({ maxFiles: value ?? undefined })} /></Field>
      </div>
      <Field label="允许的 MIME 类型" hint="按 MIME 校验，不只看扩展名"><Select mode="tags" value={rules.mimeTypes ?? []} disabled={readOnly} style={{ width: '100%' }}
        onChange={mimeTypes => setRules({ mimeTypes })} options={['application/pdf', 'image/png', 'image/jpeg', 'application/zip'].map(value => ({ value }))} /></Field>
      <Alert type="info" showIcon title="文件字段可以配置；运行时上传要等 file-document 新入口接入" />
    </>}
    {references.length > 0 && <Alert type="warning" showIcon title={`被引用 ${references.length} 处`}
      description={<><ul className="wf-plain-list">{references.map((item, index) => <li key={index}>{item}</li>)}</ul>修改字段键或删除选项会让引用失效，保存时会提示。</>} />}
  </div>;
}

/** 执行人 / 发起人看到的样子；填写只在本地校验，不提交。 */
export function FormPreview({ form }: { form: FormDefinition }) {
  const [values, setValues] = useState<Record<string, unknown>>({});
  const problems = valueProblems(form, values);
  return <Form layout="vertical" onValuesChange={(_, all) => setValues(all)} className="wf-preview-form">
    <DynamicFields form={form} problems={Object.fromEntries(Object.entries(problems).filter(([key]) => values[key] !== undefined))} />
    <div className="muted">{Object.keys(problems).length ? `${Object.keys(problems).length} 个字段未通过` : '全部字段通过本地校验'} · 预览不提交，校验规则与服务端一致</div>
  </Form>;
}
