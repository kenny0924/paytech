import { useEffect, useRef, useState } from 'react';
import type { DragEvent, ReactNode, RefObject } from 'react';
import { Alert, Button, Form, Input, Select, Switch, Tag, Tooltip } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined, CloseOutlined, CopyOutlined, DeleteOutlined, HolderOutlined, PlusOutlined } from '@ant-design/icons';
import { fieldTypeIcons } from './workflowIcons';
import { designerLayout } from './catalogModel';
import { duplicateField, fieldKeyFromLabel, fieldProblems, insertField, moveField, removeField, updateField, valueProblems } from './formModel';
import { graphLimits } from './graphModel';
import { fieldTypeLabel } from './workflowLabels';
import { useT } from '../i18n/react';
import type { EnumOption, FieldRules, FieldType, FormDefinition, FormField } from './workflowTypes';
import { DynamicFields, Field } from './WorkflowParts';

export const fieldTypes: FieldType[] = ['TEXT', 'LONG_TEXT', 'NUMBER', 'BOOLEAN', 'ENUM', 'DATE', 'FILE'];
const typeData = 'text/x-wf-field-type';
const indexData = 'text/x-wf-field-index';

/** 宽度随容器变化（字段设计在页面与抽屉中都会出现）。 */
function useWidth<T extends HTMLElement>(): [RefObject<T | null>, number] {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(1200);
  useEffect(() => {
    const element = ref.current;
    if (!element || typeof ResizeObserver === 'undefined') return;
    const observer = new ResizeObserver(entries => setWidth(Math.round(entries[0].contentRect.width)));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  return [ref, width];
}

/**
 * 字段设计器（FORM 动作与启动表单共用，原型 08）：字段类型 | 表单画布 | 字段属性三栏。
 * 画布按填写人看到的样子渲染（与我的待办同一套 DynamicFields），选中字段描边，左侧拖动手柄排序，悬停复制 / 删除；
 * 字段类型可拖到画布某个字段之前，或点击加到末尾。属性只有后端接受的显示名称、字段键、类型、必填与枚举选项（需求 E1）。
 * 容器较窄时按 designerLayout 收窄两侧或改为上下排列，画布放不下两个字段时改单列（AC-04）。
 */
export function FieldDesigner({ form, onChange, readOnly, variablePrefix, references, previewTitle, previewDescription, savedKeys }: {
  form: FormDefinition; onChange: (form: FormDefinition) => void; readOnly?: boolean; variablePrefix: string;
  references?: (field: FormField) => ReactNode[]; previewTitle?: string; previewDescription?: string;
  /** 已保存配置中的字段键：这些字段的键不再随显示名称自动生成；缺省为打开时已有的字段。 */
  savedKeys?: ReadonlySet<string>;
}) {
  const t = useT('workflow');
  const [selected, setSelected] = useState(0);
  const [initialKeys] = useState(() => new Set(form.fields.map(field => field.key)));
  const [manualKeys, setManualKeys] = useState<ReadonlySet<string>>(new Set());
  const [dropAt, setDropAt] = useState<number | null>(null);
  const [container, width] = useWidth<HTMLDivElement>();
  const layout = designerLayout(width);
  const problems = fieldProblems(form);
  const field = form.fields[selected];
  const full = form.fields.length >= graphLimits.formFields;
  const persisted = savedKeys ?? initialKeys;

  function add(type: FieldType, index = form.fields.length) {
    if (readOnly || full) return;
    const result = insertField(form, type, index); onChange(result.form); setSelected(result.index);
  }
  function drop(event: DragEvent, index: number) {
    event.preventDefault(); setDropAt(null);
    if (readOnly) return;
    const type = event.dataTransfer.getData(typeData) as FieldType;
    if (fieldTypes.includes(type)) { add(type, index); return; }
    const from = Number(event.dataTransfer.getData(indexData));
    if (!event.dataTransfer.getData(indexData) || Number.isNaN(from)) return;
    const to = from < index ? index - 1 : index;
    onChange(moveField(form, from, to)); setSelected(to);
  }
  function patch(index: number, change: Partial<FormField>) {
    const current = form.fields[index];
    const next = { ...change };
    // 新字段在用户改键之前，键随显示名称生成（只取英文与数字）。
    if (change.label !== undefined && !persisted.has(current.key) && !manualKeys.has(current.key)) {
      const others = new Set(form.fields.filter((_, other) => other !== index).map(item => item.key));
      next.key = fieldKeyFromLabel(change.label, others, current.key);
    }
    onChange(updateField(form, index, next));
  }
  const columns = layout.single ? 'minmax(0, 1fr)' : layout.stacked ? `minmax(0, 1fr) ${layout.properties}px` : `${layout.palette}px minmax(0, 1fr) ${layout.properties}px`;
  return <div ref={container} className={`wf-designer${layout.stacked ? ' is-stacked' : ''}`} style={{ gridTemplateColumns: columns }}>
    <aside className="wf-designer-palette">
      <div className="wf-card-title">{t('designer.fieldTypes')}</div>
      <p className="muted">{t('designer.paletteHint', { count: form.fields.length, max: graphLimits.formFields })}</p>
      <div className="wf-palette-items">{fieldTypes.map(type => <button type="button" key={type} className="wf-palette-item" disabled={readOnly || full}
        draggable={!readOnly && !full} onDragStart={event => { event.dataTransfer.setData(typeData, type); event.dataTransfer.effectAllowed = 'copy'; }}
        onClick={() => add(type)}><span className="wf-palette-icon">{fieldTypeIcons[type]}</span><span className="wf-grow">{fieldTypeLabel(type)}</span>
        <span className="wf-grip" aria-hidden><HolderOutlined /></span></button>)}</div>
    </aside>
    <section className="wf-designer-canvas">
      <div className="wf-canvas-sheet">
        {previewTitle && <div className="wf-canvas-title">{previewTitle}</div>}
        {previewDescription && <div className="muted wf-canvas-desc">{previewDescription}</div>}
        <Form layout="vertical" component="div" className={`wf-canvas-fields is-${layout.columns}-columns`}>
          {form.fields.map((item, index) => <div key={index} role="button" tabIndex={0}
            className={`wf-canvas-field${index === selected ? ' is-selected' : ''}${problems.has(index) ? ' has-error' : ''}${dropAt === index ? ' is-drop-target' : ''}${item.type === 'LONG_TEXT' || item.type === 'FILE' ? ' is-wide' : ''}${readOnly ? '' : ' is-editable'}`}
            draggable={!readOnly} onClick={() => setSelected(index)} onKeyDown={event => { if (event.key === 'Enter') setSelected(index); }}
            onDragStart={event => { event.dataTransfer.setData(indexData, String(index)); event.dataTransfer.effectAllowed = 'move'; }}
            onDragOver={event => { if (readOnly) return; event.preventDefault(); setDropAt(index); }} onDragLeave={() => setDropAt(null)}
            onDrop={event => drop(event, index)}>
            {!readOnly && <span className="wf-canvas-handle" title={t('designer.dragToSort')}><HolderOutlined /></span>}
            {!readOnly && <span className="wf-canvas-tools" onClick={event => event.stopPropagation()}>
              <Tooltip title={t('designer.moveUp')}><Button size="small" type="text" disabled={index === 0} onClick={() => { onChange(moveField(form, index, index - 1)); setSelected(index - 1); }} icon={<ArrowUpOutlined />} aria-label={t('designer.moveUp')} /></Tooltip>
              <Tooltip title={t('designer.moveDown')}><Button size="small" type="text" disabled={index === form.fields.length - 1} onClick={() => { onChange(moveField(form, index, index + 1)); setSelected(index + 1); }} icon={<ArrowDownOutlined />} aria-label={t('designer.moveDown')} /></Tooltip>
              <Tooltip title={t('designer.duplicate')}><Button size="small" type="text" disabled={full} onClick={() => { onChange(duplicateField(form, index)); setSelected(index + 1); }} icon={<CopyOutlined />} aria-label={t('designer.duplicate')} /></Tooltip>
              <Tooltip title={t('designer.delete')}><Button size="small" type="text" danger onClick={() => { onChange(removeField(form, index)); setSelected(Math.max(0, index - 1)); }} icon={<DeleteOutlined />} aria-label={t('designer.delete')} /></Tooltip>
            </span>}
            <div className="wf-canvas-field-body"><DynamicFields form={{ fields: [{ ...item, label: item.label || t('designer.unnamedField') }] }} /></div>
            {problems.has(index) && <div className="wf-danger-text">{problems.get(index)}</div>}
          </div>)}
        </Form>
        {!readOnly && <div className={`wf-canvas-drop${dropAt === form.fields.length ? ' is-drop-target' : ''}`} role="button" tabIndex={0}
          onClick={() => add('TEXT')} onKeyDown={event => { if (event.key === 'Enter') add('TEXT'); }}
          onDragOver={event => { event.preventDefault(); setDropAt(form.fields.length); }} onDragLeave={() => setDropAt(null)}
          onDrop={event => drop(event, form.fields.length)}><PlusOutlined /> {t('designer.dropHint')}</div>}
        {readOnly && !form.fields.length && <div className="muted">{t('designer.noFields')}</div>}
      </div>
    </section>
    <aside className="wf-designer-props">
      {field ? <FieldProperties key={selected} field={field} readOnly={readOnly} variablePrefix={variablePrefix}
        saved={persisted.has(field.key)} references={references?.(field) ?? []}
        onChange={change => patch(selected, change)}
        onKeyEdited={key => setManualKeys(current => new Set(current).add(key))} />
        : <div className="muted wf-props-empty">{t('designer.selectField')}</div>}
    </aside>
  </div>;
}

function FieldProperties({ field, onChange, onKeyEdited, readOnly, variablePrefix, references, saved }: {
  field: FormField; onChange: (patch: Partial<FormField>) => void; onKeyEdited: (key: string) => void; readOnly?: boolean; variablePrefix: string;
  references: ReactNode[]; saved: boolean;
}) {
  const t = useT('workflow');
  const [editingKey, setEditingKey] = useState(false);
  const rules = field.rules ?? {};
  // 只写枚举选项；旧配置的长度、范围、文件规则在此丢弃（需求 E1）。
  const setRules = (patch: Pick<FieldRules, 'options'>) => onChange({ rules: { options: patch.options } });
  const options = rules.options ?? [];
  const setOption = (index: number, patch: Partial<EnumOption>) => setRules({ options: options.map((option, current) => current === index ? { ...option, ...patch } : option) });
  return <div className="wf-props">
    <div className="wf-props-head"><strong>{t('designer.properties')}</strong><Tag>{fieldTypeLabel(field.type)}</Tag></div>
    <Field label={t('designer.label')}><Input value={field.label} maxLength={64} disabled={readOnly} placeholder={t('designer.labelPlaceholder')} onChange={event => onChange({ label: event.target.value })} /></Field>
    <div className="wf-field">
      <div className="wf-key-line"><span className="wf-field-label">{t('designer.key')}</span>
        {!readOnly && !editingKey && <Button type="link" size="small" onClick={() => setEditingKey(true)}>{t('designer.editKey')}</Button>}</div>
      {editingKey ? <Input size="small" value={field.key} maxLength={64} autoFocus onBlur={() => setEditingKey(false)}
        onChange={event => { const key = event.target.value.trim(); onKeyEdited(key); onChange({ key }); }} />
        : <code className="wf-key-value">{field.key}</code>}
      <div className="wf-field-hint">{saved ? t('designer.keyHintSaved', { ref: variablePrefix + field.key }) : t('designer.keyHintNew', { ref: variablePrefix + field.key })}</div>
    </div>
    <Field label={t('designer.type')}><Select value={field.type} disabled={readOnly} style={{ width: '100%' }} options={fieldTypes.map(type => ({ value: type, label: fieldTypeLabel(type) }))}
      onChange={(type: FieldType) => onChange({ type, rules: type === 'ENUM' ? { options: options.length ? options : [{ value: 'OPTION_1', label: t('form.defaultOption', { index: 1 }) }] } : undefined })} /></Field>
    <div className="wf-props-switch"><span>{t('form.value.required')}</span><Switch size="small" checked={field.required} disabled={readOnly} onChange={required => onChange({ required })} /></div>
    {field.type === 'ENUM' && <Field label={t('designer.options')} hint={t('designer.optionsHint')}>
      {options.map((option, index) => <div key={index} className="wf-option-row">
        <Input value={option.value} disabled={readOnly} placeholder={t('designer.optionValue')} onChange={event => setOption(index, { value: event.target.value.trim() })} />
        <Input value={option.label} disabled={readOnly} placeholder={t('designer.optionLabel')} onChange={event => setOption(index, { label: event.target.value })} />
        {!readOnly && <Button type="text" danger aria-label={t('designer.deleteOption')} icon={<CloseOutlined />} onClick={() => setRules({ options: options.filter((_, current) => current !== index) })} />}
      </div>)}
      {!readOnly && <Button type="link" onClick={() => setRules({ options: [...options, { value: `OPTION_${options.length + 1}`, label: '' }] })} icon={<PlusOutlined />}>{t('designer.addOption')}</Button>}
    </Field>}
    {field.type === 'FILE' && <div className="muted">{t('designer.fileHint')}</div>}
    {references.length > 0 && <Alert type="warning" showIcon title={t('designer.references', { count: references.length })}
      description={<><ul className="wf-plain-list">{references.map((item, index) => <li key={index}>{item}</li>)}</ul>{t('designer.referencesHint')}</>} />}
  </div>;
}

/** 执行人 / 发起人看到的样子；填写只在本地校验，不提交。readOnly 时只展示、不可填写。 */
export function FormPreview({ form, readOnly }: { form: FormDefinition; readOnly?: boolean }) {
  const t = useT('workflow');
  const [values, setValues] = useState<Record<string, unknown>>({});
  const problems = valueProblems(form, values);
  return <Form layout="vertical" onValuesChange={(_, all) => setValues(all)} className="wf-preview-form">
    <div className="wf-form-grid"><DynamicFields form={form} disabled={readOnly}
      problems={readOnly ? {} : Object.fromEntries(Object.entries(problems).filter(([key]) => values[key] !== undefined))} /></div>
    {!readOnly && <div className="muted">{Object.keys(problems).length ? t('designer.previewPending', { count: Object.keys(problems).length }) : t('designer.previewFilled')}</div>}
  </Form>;
}
