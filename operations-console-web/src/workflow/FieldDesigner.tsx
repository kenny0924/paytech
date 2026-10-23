import { useEffect, useRef, useState } from 'react';
import type { DragEvent, ReactNode, RefObject } from 'react';
import { Alert, Button, Form, Input, Select, Switch, Tag, Tooltip } from 'antd';
import { ArrowDownOutlined, ArrowUpOutlined, CloseOutlined, CopyOutlined, DeleteOutlined, HolderOutlined, PlusOutlined } from '@ant-design/icons';
import { fieldTypeIcons } from './workflowIcons';
import { designerLayout } from './catalogModel';
import { duplicateField, fieldKeyFromLabel, fieldProblems, insertField, moveField, removeField, updateField, valueProblems } from './formModel';
import { graphLimits } from './graphModel';
import { fieldTypeLabels } from './workflowLabels';
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
      <div className="wf-card-title">字段类型</div>
      <p className="muted">拖到右侧表单，或点击添加到末尾（{form.fields.length} / {graphLimits.formFields}）</p>
      <div className="wf-palette-items">{fieldTypes.map(type => <button type="button" key={type} className="wf-palette-item" disabled={readOnly || full}
        draggable={!readOnly && !full} onDragStart={event => { event.dataTransfer.setData(typeData, type); event.dataTransfer.effectAllowed = 'copy'; }}
        onClick={() => add(type)}><span className="wf-palette-icon">{fieldTypeIcons[type]}</span><span className="wf-grow">{fieldTypeLabels[type]}</span>
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
            {!readOnly && <span className="wf-canvas-handle" title="拖动排序"><HolderOutlined /></span>}
            {!readOnly && <span className="wf-canvas-tools" onClick={event => event.stopPropagation()}>
              <Tooltip title="上移"><Button size="small" type="text" disabled={index === 0} onClick={() => { onChange(moveField(form, index, index - 1)); setSelected(index - 1); }} icon={<ArrowUpOutlined />} aria-label="上移" /></Tooltip>
              <Tooltip title="下移"><Button size="small" type="text" disabled={index === form.fields.length - 1} onClick={() => { onChange(moveField(form, index, index + 1)); setSelected(index + 1); }} icon={<ArrowDownOutlined />} aria-label="下移" /></Tooltip>
              <Tooltip title="复制"><Button size="small" type="text" disabled={full} onClick={() => { onChange(duplicateField(form, index)); setSelected(index + 1); }} icon={<CopyOutlined />} aria-label="复制" /></Tooltip>
              <Tooltip title="删除"><Button size="small" type="text" danger onClick={() => { onChange(removeField(form, index)); setSelected(Math.max(0, index - 1)); }} icon={<DeleteOutlined />} aria-label="删除" /></Tooltip>
            </span>}
            <div className="wf-canvas-field-body"><DynamicFields form={{ fields: [{ ...item, label: item.label || '（未命名字段）' }] }} /></div>
            {problems.has(index) && <div className="wf-danger-text">{problems.get(index)}</div>}
          </div>)}
        </Form>
        {!readOnly && <div className={`wf-canvas-drop${dropAt === form.fields.length ? ' is-drop-target' : ''}`} role="button" tabIndex={0}
          onClick={() => add('TEXT')} onKeyDown={event => { if (event.key === 'Enter') add('TEXT'); }}
          onDragOver={event => { event.preventDefault(); setDropAt(form.fields.length); }} onDragLeave={() => setDropAt(null)}
          onDrop={event => drop(event, form.fields.length)}><PlusOutlined /> 拖入字段，或从左侧点击添加</div>}
        {readOnly && !form.fields.length && <div className="muted">还没有字段</div>}
      </div>
    </section>
    <aside className="wf-designer-props">
      {field ? <FieldProperties key={selected} field={field} readOnly={readOnly} variablePrefix={variablePrefix}
        saved={persisted.has(field.key)} references={references?.(field) ?? []}
        onChange={change => patch(selected, change)}
        onKeyEdited={key => setManualKeys(current => new Set(current).add(key))} />
        : <div className="muted wf-props-empty">选择画布中的字段查看属性</div>}
    </aside>
  </div>;
}

function FieldProperties({ field, onChange, onKeyEdited, readOnly, variablePrefix, references, saved }: {
  field: FormField; onChange: (patch: Partial<FormField>) => void; onKeyEdited: (key: string) => void; readOnly?: boolean; variablePrefix: string;
  references: ReactNode[]; saved: boolean;
}) {
  const [editingKey, setEditingKey] = useState(false);
  const rules = field.rules ?? {};
  // 只写枚举选项；旧配置的长度、范围、文件规则在此丢弃（需求 E1）。
  const setRules = (patch: Pick<FieldRules, 'options'>) => onChange({ rules: { options: patch.options } });
  const options = rules.options ?? [];
  const setOption = (index: number, patch: Partial<EnumOption>) => setRules({ options: options.map((option, current) => current === index ? { ...option, ...patch } : option) });
  return <div className="wf-props">
    <div className="wf-props-head"><strong>字段属性</strong><Tag>{fieldTypeLabels[field.type]}</Tag></div>
    <Field label="显示名称"><Input value={field.label} maxLength={64} disabled={readOnly} placeholder="如 公司注册名称" onChange={event => onChange({ label: event.target.value })} /></Field>
    <div className="wf-field">
      <div className="wf-key-line"><span className="wf-field-label">字段键</span>
        {!readOnly && !editingKey && <Button type="link" size="small" onClick={() => setEditingKey(true)}>修改</Button>}</div>
      {editingKey ? <Input size="small" value={field.key} maxLength={64} autoFocus onBlur={() => setEditingKey(false)}
        onChange={event => { const key = event.target.value.trim(); onKeyEdited(key); onChange({ key }); }} />
        : <code className="wf-key-value">{field.key}</code>}
      <div className="wf-field-hint">{saved ? '被引用后不建议修改' : '根据显示名称自动生成（取英文与数字），被引用后不建议修改'} · 引用为 {variablePrefix}{field.key}</div>
    </div>
    <Field label="类型"><Select value={field.type} disabled={readOnly} style={{ width: '100%' }} options={fieldTypes.map(type => ({ value: type, label: fieldTypeLabels[type] }))}
      onChange={(type: FieldType) => onChange({ type, rules: type === 'ENUM' ? { options: options.length ? options : [{ value: 'OPTION_1', label: '选项 1' }] } : undefined })} /></Field>
    <div className="wf-props-switch"><span>必填</span><Switch size="small" checked={field.required} disabled={readOnly} onChange={required => onChange({ required })} /></div>
    {field.type === 'ENUM' && <Field label="枚举选项（值 · 显示文字）" hint="选项值被连线条件引用，修改或删除会让引用失效">
      {options.map((option, index) => <div key={index} className="wf-option-row">
        <Input value={option.value} disabled={readOnly} placeholder="值" onChange={event => setOption(index, { value: event.target.value.trim() })} />
        <Input value={option.label} disabled={readOnly} placeholder="显示文字" onChange={event => setOption(index, { label: event.target.value })} />
        {!readOnly && <Button type="text" danger aria-label="删除选项" icon={<CloseOutlined />} onClick={() => setRules({ options: options.filter((_, current) => current !== index) })} />}
      </div>)}
      {!readOnly && <Button type="link" onClick={() => setRules({ options: [...options, { value: `OPTION_${options.length + 1}`, label: '' }] })} icon={<PlusOutlined />}>添加选项</Button>}
    </Field>}
    {field.type === 'FILE' && <div className="muted">文件字段只看「必填」：必填时至少上传一个，不限数量与类型；安全检查在后台进行。</div>}
    {references.length > 0 && <Alert type="warning" showIcon title={`被引用 ${references.length} 处`}
      description={<><ul className="wf-plain-list">{references.map((item, index) => <li key={index}>{item}</li>)}</ul>修改字段键或删除选项会让引用失效，保存时会提示。</>} />}
  </div>;
}

/** 执行人 / 发起人看到的样子；填写只在本地校验，不提交。readOnly 时只展示、不可填写。 */
export function FormPreview({ form, readOnly }: { form: FormDefinition; readOnly?: boolean }) {
  const [values, setValues] = useState<Record<string, unknown>>({});
  const problems = valueProblems(form, values);
  return <Form layout="vertical" onValuesChange={(_, all) => setValues(all)} className="wf-preview-form">
    <div className="wf-form-grid"><DynamicFields form={form} disabled={readOnly}
      problems={readOnly ? {} : Object.fromEntries(Object.entries(problems).filter(([key]) => values[key] !== undefined))} /></div>
    {!readOnly && <div className="muted">{Object.keys(problems).length ? `${Object.keys(problems).length} 个字段待填写` : '必填字段已填写'} · 预览不提交</div>}
  </Form>;
}
