import { useState } from 'react';
import type { ReactNode } from 'react';
import { Button, Checkbox, Input, InputNumber, Segmented, Select, Switch, Tag, Tooltip } from 'antd';
import {
  constraintKinds, elementTypes, emptyColumnRow, emptyFieldRow, emptyParameterRow, fieldTypes, maskLabel, maskModes, moveRow, nextRowKey,
  pagingModeLabel, pagingModes, parameterHint, parameterTypes, templateLimits, typeLabel,
} from './templateModel';
import type { ChoiceRow, ColumnRow, FieldRow, ParameterRow, QueryConstraints } from './templateModel';
import { useT } from '../i18n/react';
import { Note } from './DataQueryParts';
import { code, richText } from '../i18n/richText';

export type DeclarationTab = 'parameters' | 'constraints' | 'columns';

const typeOptions = parameterTypes.map(value => ({ value, label: value }));

/**
 * 参数表格：可增删、拖动排序（⋮⋮ 手柄）；展开行编辑元素类型、对象字段、片段取值与约束。
 * 按正文自动添加的行标“自动”，修改后转为手工行；手工行在正文不再引用时标“未引用”。
 */
export function ParameterList({ rows, onChange, readOnly, focusIndex, unreferenced }: {
  rows: ParameterRow[]; onChange: (rows: ParameterRow[]) => void; readOnly: boolean; focusIndex?: number; unreferenced: ReadonlySet<string>;
}) {
  const [expanded, setExpanded] = useState<string | null>(null); const t = useT('data-query');
  const [dragging, setDragging] = useState<number | null>(null);
  const update = (index: number, patch: Partial<ParameterRow>) => onChange(rows.map((row, i) => i === index ? { ...row, ...patch, auto: false } : row));
  const focusKey = focusIndex !== undefined ? rows[focusIndex]?.key : undefined;
  return <div className="dq-param-table">
    <Note>{richText(t, 'parameters.intro', { value: code('#{}'), fragment: code('@{}'), ifTag: code('{{#if}}'), eachTag: code('{{#each}}') })}</Note>
    <div className="dq-param-head"><span /><span>{t('parameters.column.name')}</span><span>{t('parameters.column.type')}</span><span>{t('parameters.column.required')}</span><span>{t('parameters.column.defaultAndConstraints')}</span><span /></div>
    {rows.map((row, index) => {
      const open = expanded === row.key || focusKey === row.key;
      return <div key={row.key} className={`dq-param-item${focusKey === row.key ? ' is-focused' : ''}${dragging === index ? ' is-dragging' : ''}`}
        onDragOver={event => { if (dragging !== null) event.preventDefault(); }}
        onDrop={() => { if (dragging !== null) onChange(moveRow(rows, dragging, index)); setDragging(null); }}>
        <div className="dq-param-line">
          <span className="dq-drag" draggable={!readOnly} onDragStart={() => setDragging(index)} onDragEnd={() => setDragging(null)}
            title={t('parameters.drag')} aria-label={t('parameters.dragLabel', { name: row.name })}>⋮⋮</span>
          <span className="dq-param-name">
            <Input size="small" value={row.name} disabled={readOnly} placeholder={t('parameters.namePlaceholder')} maxLength={64} className="mono"
              onChange={event => update(index, { name: event.target.value })} />
            {row.auto && <Tooltip title={t('parameters.autoHint')}><Tag color="blue">{t('parameters.auto')}</Tag></Tooltip>}
            {!row.auto && unreferenced.has(row.name.trim()) && <Tooltip title={t('parameters.unreferencedHint')}><Tag color="gold">{t('parameters.unreferenced')}</Tag></Tooltip>}
          </span>
          <Select size="small" value={row.type} disabled={readOnly} options={typeOptions} popupMatchSelectWidth={false}
            onChange={type => update(index, { type, elementType: type === 'LIST' ? row.elementType ?? 'STRING' : null })} />
          <Checkbox checked={row.required} disabled={readOnly} onChange={event => update(index, { required: event.target.checked })} aria-label={t('parameters.column.required')} />
          <button type="button" className="dq-link dq-hint" onClick={() => setExpanded(open ? null : row.key)}>
            {parameterHint(row) || (row.type === 'LIST' ? typeLabel(row.type, row.elementType) : t('parameters.configure'))} {open ? '▴' : '▾'}</button>
          {!readOnly ? <Button size="small" type="text" aria-label={t('parameters.remove')} onClick={() => onChange(rows.filter((_, i) => i !== index))}>✕</Button> : <span />}
        </div>
        {open && <ParameterDetail row={row} readOnly={readOnly} onChange={patch => update(index, patch)} />}
      </div>;
    })}
    {!readOnly && <Button type="dashed" size="small" onClick={() => { const row = emptyParameterRow(); onChange([...rows, row]); setExpanded(row.key); }}>{t('parameters.add')}</Button>}
    <Note>{richText(t, 'parameters.typesNote', { operator: code('__operator.accountId'), cursor: code('__cursor.*') }, { types: parameterTypes.join(' · ') })}</Note>
  </div>;
}

function Labeled({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return <label className="dq-labeled"><span>{label}</span>{children}{hint && <small className="muted">{hint}</small>}</label>;
}

function ScalarConstraints({ type, value, onChange, readOnly }: {
  type: string | null; value: { maxLength: number | null; min: string; max: string; allowedValues: string[] };
  onChange: (patch: Partial<{ maxLength: number | null; min: string; max: string; allowedValues: string[] }>) => void; readOnly: boolean;
}) {
  const kinds = constraintKinds(type); const t = useT('data-query');
  return <>
    {kinds.length && <Labeled label={t('parameters.maxLength')}><InputNumber size="small" min={1} precision={0} value={value.maxLength} disabled={readOnly}
      onChange={maxLength => onChange({ maxLength: maxLength ?? null })} /></Labeled>}
    {kinds.range && <Labeled label={t('parameters.min')}><Input size="small" value={value.min} disabled={readOnly} onChange={event => onChange({ min: event.target.value })} /></Labeled>}
    {kinds.range && <Labeled label={t('parameters.max')}><Input size="small" value={value.max} disabled={readOnly} onChange={event => onChange({ max: event.target.value })} /></Labeled>}
    {kinds.allowed && <Labeled label={type === 'ENUM' ? t('parameters.allowedValuesRequired') : t('parameters.allowedValues')}><Select size="small" mode="tags" value={value.allowedValues} disabled={readOnly}
      tokenSeparators={[',']} open={false} onChange={allowedValues => onChange({ allowedValues })} /></Labeled>}
  </>;
}

/** 展开行：默认值、类型约束、LIST 元素类型与对象字段、FRAGMENT 的“取值 → 固定片段”。 */
function ParameterDetail({ row, onChange, readOnly }: { row: ParameterRow; onChange: (patch: Partial<ParameterRow>) => void; readOnly: boolean }) {
  const constrained = row.type === 'LIST' ? row.elementType : row.type; const t = useT('data-query');
  return <div className="dq-param-detail">
    {row.type === 'LIST' && <>
      <Labeled label={t('parameters.elementType')}><Select size="small" value={row.elementType ?? 'STRING'} disabled={readOnly}
        options={elementTypes.map(value => ({ value, label: value }))} onChange={elementType => onChange({ elementType })} /></Labeled>
      <Labeled label={t('parameters.maxSize')}><InputNumber size="small" min={1} precision={0} value={row.maxSize} disabled={readOnly} onChange={maxSize => onChange({ maxSize: maxSize ?? null })} /></Labeled>
    </>}
    {row.type !== 'FRAGMENT' && constrained !== 'OBJECT' && <ScalarConstraints type={constrained} value={row} onChange={onChange} readOnly={readOnly} />}
    <Labeled label={t('parameters.default')} hint={row.type === 'LIST' ? (row.elementType === 'OBJECT' ? t('parameters.defaultHint.json') : t('parameters.defaultHint.list'))
      : row.type === 'BOOLEAN' ? t('parameters.defaultHint.boolean') : row.type === 'FRAGMENT' ? t('parameters.defaultHint.fragment') : undefined}>
      <Input size="small" value={row.defaultText} disabled={readOnly} onChange={event => onChange({ defaultText: event.target.value })} /></Labeled>
    {row.type === 'FRAGMENT' && <ChoiceList rows={row.choices} readOnly={readOnly} onChange={choices => onChange({ choices })} />}
    {row.type === 'LIST' && row.elementType === 'OBJECT' && <FieldList rows={row.fields} readOnly={readOnly} onChange={fields => onChange({ fields })} />}
  </div>;
}

function ChoiceList({ rows, onChange, readOnly }: { rows: ChoiceRow[]; onChange: (rows: ChoiceRow[]) => void; readOnly: boolean }) {
  const update = (index: number, patch: Partial<ChoiceRow>) => onChange(rows.map((row, i) => i === index ? { ...row, ...patch } : row));
  const t = useT('data-query');
  return <div className="dq-choice-list">
    <div className="dq-sub-title">{t('parameters.choicesTitle', { markers: '#{} @{} {{ ;' })}</div>
    {rows.map((row, index) => <div key={row.key} className="dq-choice-row">
      <Input size="small" className="mono" value={row.value} placeholder="NEWEST" disabled={readOnly} onChange={event => update(index, { value: event.target.value })} />
      <span>→</span>
      <Input size="small" className="mono" value={row.fragment} placeholder="created_at DESC" disabled={readOnly} onChange={event => update(index, { fragment: event.target.value })} />
      {!readOnly && <Button size="small" type="text" aria-label={t('parameters.removeChoice')} onClick={() => onChange(rows.filter((_, i) => i !== index))}>✕</Button>}
    </div>)}
    {!readOnly && <Button size="small" type="link" onClick={() => onChange([...rows, { key: nextRowKey(), value: '', fragment: '' }])}>{t('parameters.addChoice')}</Button>}
  </div>;
}

function FieldList({ rows, onChange, readOnly }: { rows: FieldRow[]; onChange: (rows: FieldRow[]) => void; readOnly: boolean }) {
  const update = (index: number, patch: Partial<FieldRow>) => onChange(rows.map((row, i) => i === index ? { ...row, ...patch } : row));
  const t = useT('data-query');
  return <div className="dq-field-list">
    <div className="dq-sub-title">{t('parameters.fieldsTitle')}</div>
    {rows.map((row, index) => <div key={row.key} className="dq-field-row">
      <div className="dq-field-main">
        <Input size="small" className="mono" value={row.name} placeholder={t('parameters.fieldNamePlaceholder')} disabled={readOnly} onChange={event => update(index, { name: event.target.value })} />
        <Select size="small" value={row.type} disabled={readOnly} options={fieldTypes.map(value => ({ value, label: value }))} onChange={type => update(index, { type })} />
        <Checkbox checked={row.required} disabled={readOnly} onChange={event => update(index, { required: event.target.checked })}>{t('parameters.column.required')}</Checkbox>
        {!readOnly && <Button size="small" type="text" aria-label={t('parameters.removeField')} onClick={() => onChange(rows.filter((_, i) => i !== index))}>✕</Button>}
      </div>
      <div className="dq-field-constraints"><ScalarConstraints type={row.type} value={row} readOnly={readOnly} onChange={patch => update(index, patch)} /></div>
    </div>)}
    {!readOnly && <Button size="small" type="link" onClick={() => onChange([...rows, emptyFieldRow()])}>{t('parameters.addField')}</Button>}
  </div>;
}

/** 约束：最大行数、超时、分页方式、页大小上限、允许总数（偏移）、游标列（游标）。 */
export function ConstraintsForm({ value, onChange, readOnly, focusField }: {
  value: QueryConstraints; onChange: (value: QueryConstraints) => void; readOnly: boolean; focusField?: string;
}) {
  const cls = (field: string) => `dq-constraint${focusField === field ? ' is-focused' : ''}`;
  const set = (patch: Partial<QueryConstraints>) => onChange({ ...value, ...patch });
  const t = useT('data-query');
  return <div className="dq-constraints">
    <div className={cls('maxRows')}><Labeled label={t('constraints.maxRows')} hint={`1–${templateLimits.maxRows}`}>
      <InputNumber min={1} max={templateLimits.maxRows} precision={0} value={value.maxRows} disabled={readOnly} onChange={maxRows => set({ maxRows: maxRows ?? 1 })} /></Labeled></div>
    <div className={cls('timeoutSeconds')}><Labeled label={t('constraints.timeoutSeconds')} hint={`1–${templateLimits.maxTimeoutSeconds}`}>
      <InputNumber min={1} max={templateLimits.maxTimeoutSeconds} precision={0} value={value.timeoutSeconds} disabled={readOnly}
        onChange={timeoutSeconds => set({ timeoutSeconds: timeoutSeconds ?? 1 })} /></Labeled></div>
    <div className={cls('pagingMode')}><Labeled label={t('constraints.pagingMode')} hint={t('constraints.pagingModeHint')}>
      <Segmented value={value.pagingMode} disabled={readOnly} options={pagingModes.map(mode => ({ value: mode, label: pagingModeLabel(mode) }))}
        onChange={pagingMode => set({ pagingMode })} /></Labeled></div>
    {value.pagingMode !== 'NONE' && <div className={cls('maxPageSize')}><Labeled label={t('constraints.maxPageSize')} hint={t('constraints.maxPageSizeHint')}>
      <InputNumber min={1} max={value.maxRows} precision={0} value={value.maxPageSize} disabled={readOnly} onChange={maxPageSize => set({ maxPageSize })} /></Labeled></div>}
    {value.pagingMode === 'OFFSET' && <div className={cls('countAllowed')}><Labeled label={t('constraints.countAllowed')} hint={t('constraints.countAllowedHint')}>
      <Switch checked={value.countAllowed === true} disabled={readOnly} onChange={countAllowed => set({ countAllowed })} /></Labeled></div>}
    {value.pagingMode === 'CURSOR' && <div className={cls('cursorColumns')}><Labeled label={t('constraints.cursorColumns')} hint={t('constraints.cursorColumnsHint', { max: templateLimits.maxCursorColumns, tag: '{{#if __cursor}}' })}>
      <Select mode="tags" open={false} value={value.cursorColumns ?? []} disabled={readOnly} tokenSeparators={[',']}
        onChange={cursorColumns => set({ cursorColumns })} /></Labeled></div>}
  </div>;
}

/** 列声明：标题与屏蔽规则；没有声明的列原样返回，标题使用列名。正文 SELECT 与试运行结果中的列自动加入。 */
export function ColumnList({ rows, onChange, readOnly, focusIndex }: { rows: ColumnRow[]; onChange: (rows: ColumnRow[]) => void; readOnly: boolean; focusIndex?: number }) {
  const update = (index: number, patch: Partial<ColumnRow>) => onChange(rows.map((row, i) => i === index ? { ...row, ...patch, auto: false } : row));
  const t = useT('data-query');
  return <div className="dq-column-list">
    <div className="dq-column-head"><span>{t('columns.name')}</span><span>{t('columns.label')}</span><span>{t('columns.mask')}</span><span /></div>
    {rows.map((row, index) => <div key={row.key} className={`dq-column-row${focusIndex === index ? ' is-focused' : ''}`}>
      <span className="dq-param-name">
        <Input size="small" className="mono" value={row.name} placeholder="email" disabled={readOnly} onChange={event => update(index, { name: event.target.value })} />
        {row.auto && <Tooltip title={t('columns.autoHint')}><Tag color="blue">{t('parameters.auto')}</Tag></Tooltip>}
      </span>
      <Input size="small" value={row.label} placeholder={t('columns.labelPlaceholder')} disabled={readOnly} onChange={event => update(index, { label: event.target.value })} />
      <div className="dq-mask">
        <Select size="small" value={row.mask} disabled={readOnly} options={maskModes.map(value => ({ value, label: maskLabel(value) }))}
          onChange={mask => update(index, { mask })} />
        {row.mask === 'KEEP_EDGES' && <span className="dq-keep">{t('columns.keepStart')} <InputNumber size="small" min={0} max={templateLimits.maxKeep} precision={0} value={row.keepStart}
          disabled={readOnly} onChange={keepStart => update(index, { keepStart })} /> {t('columns.keepEnd')} <InputNumber size="small" min={0} max={templateLimits.maxKeep} precision={0}
          value={row.keepEnd} disabled={readOnly} onChange={keepEnd => update(index, { keepEnd })} /></span>}
      </div>
      {!readOnly ? <Button size="small" type="text" aria-label={t('columns.remove')} onClick={() => onChange(rows.filter((_, i) => i !== index))}>✕</Button> : <span />}
    </div>)}
    {!readOnly && <Button type="dashed" size="small" onClick={() => onChange([...rows, emptyColumnRow()])}>{t('columns.add')}</Button>}
    <Note>{t('columns.note')}</Note>
  </div>;
}
