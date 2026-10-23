import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Button, Form, Input, Segmented, Select, Switch, Table } from 'antd';
import { fieldDataTypes, fieldDepth, fieldHints, fieldLocations } from './externalApiModel';
import type { ApiField, FieldLocation, FieldHint } from './externalApiModel';
import { Mono } from './ui';
import { useT } from '../i18n/react';

interface Props {
  value: ApiField[];
  onChange: (next: ApiField[]) => void;
  /** 本方向允许的字段位置。 */
  locations: FieldLocation[];
  readOnly?: boolean;
  /** 表格上方的说明。 */
  hint: ReactNode;
  /** 服务端返回的出错行号（数组下标），与即时提示一起标红。 */
  serverErrorRows?: number[];
  /** 原子 API 请求字段的“来源”：键为 LOCATION:path（HEADER 小写），值为固定值表达式。未提供时显示“说明”列。 */
  fixedValues?: Map<string, string>;
  /**
   * 来源变化：字段从 previousKey（编辑前的键，新建为 null）改为 key 后，固定值为 expression（null 表示节点赋值）。
   * 与“调用参数与固定值”页签维护同一份固定值；删除字段时 key 为 null。
   */
  onSourceChange?: (previousKey: string | null, key: string | null, expression: string | null) => void;
}

const emptyField = (location: FieldLocation): ApiField =>
  ({ fieldLocation: location, fieldPath: '', fieldName: '', dataType: 'STRING', required: true, description: null });
const fieldKey = (field: Pick<ApiField, 'fieldLocation' | 'fieldPath'>) =>
  `${field.fieldLocation}:${field.fieldLocation === 'HEADER' ? field.fieldPath.toLowerCase() : field.fieldPath}`;

/** BODY 路径的父容器路径：a.b → a，items[].sku → items，items[] → items；顶层为空串。 */
function parentOf(field: ApiField): string {
  if (field.fieldLocation !== 'BODY') return '';
  const path = field.fieldPath;
  if (path.endsWith('[]')) return path.slice(0, -2);
  const dot = path.lastIndexOf('.');
  if (dot < 0) return '';
  const parent = path.slice(0, dot);
  return parent.endsWith('[]') ? parent.slice(0, -2) : parent;
}

/** 层级顺序：父字段之后紧跟其子字段，同级保持原有顺序；不再需要手动上移下移。 */
export function treeOrder(fields: ApiField[]): number[] {
  const order: number[] = []; const visited = new Set<number>();
  const visit = (index: number) => {
    if (visited.has(index)) return;
    visited.add(index); order.push(index);
    const field = fields[index];
    if (field.fieldLocation !== 'BODY') return;
    fields.forEach((child, childIndex) => { if (childIndex !== index && child.fieldLocation === 'BODY' && parentOf(child) === field.fieldPath) visit(childIndex); });
  };
  fields.forEach((field, index) => {
    const parent = parentOf(field);
    if (!parent || !fields.some(other => other.fieldLocation === 'BODY' && other.fieldPath === parent)) visit(index);
  });
  fields.forEach((_, index) => visit(index));
  return order;
}

/** 显示用的末段：a.b.c → c，items[].sku → [].sku，items[] → []。 */
function lastSegment(field: ApiField): string {
  if (field.fieldLocation !== 'BODY') return field.fieldPath;
  const path = field.fieldPath;
  if (path.endsWith('[]')) return '[]';
  const dot = path.lastIndexOf('.');
  if (dot < 0) return path;
  return path.slice(0, dot).endsWith('[]') ? `[].${path.slice(dot + 1)}` : path.slice(dot + 1);
}

/**
 * 字段表：按层级顺序展示，BODY 路径只显示末段并缩进；编辑在表格右侧的浮层中完成（无遮罩），正在编辑的行高亮。
 */
export function FieldTable({ value, onChange, locations, readOnly, hint, serverErrorRows = [], fixedValues, onSourceChange }: Props) {
  const t = useT('channel');
  const [editing, setEditing] = useState<{ index: number | null; field: ApiField } | null>(null);
  const hints = useMemo(() => fieldHints(value, locations), [value, locations]);
  const hintsOf = (index: number) => hints.filter(item => item.index === index);
  const order = useMemo(() => treeOrder(value), [value]);
  function apply(field: ApiField, fixed: string | null) {
    const next = [...value];
    const previous = editing?.index === null || editing === null ? null : fieldKey(value[editing.index]);
    if (editing?.index === null || editing === null) next.push(field); else next[editing.index] = field;
    onChange(treeOrder(next).map(index => next[index]));
    onSourceChange?.(previous, fieldKey(field), fixed);
    setEditing(null);
  }
  function remove(index: number) {
    onChange(value.filter((_, i) => i !== index));
    onSourceChange?.(fieldKey(value[index]), null, null);
  }
  const rows = order.map(index => ({ ...value[index], index }));
  return <div className="xapi-float-host">
    <div className="xapi-toolbar"><span className="xapi-desc">{hint}</span>
      {!readOnly && <Button onClick={() => setEditing({ index: null, field: emptyField(locations.includes('BODY') ? 'BODY' : locations[0]) })}>{t('field.add')}</Button>}</div>
    <div className="xapi-table-wrap" style={{ marginTop: 12 }}>
      <Table rowKey="index" tableLayout="fixed" pagination={false} dataSource={rows}
        rowClassName={row => editing?.index === row.index ? 'xapi-row-active'
          : hintsOf(row.index).length || serverErrorRows.includes(row.index) ? 'xapi-row-error' : ''}
        locale={{ emptyText: t('field.empty') }} columns={[
          { title: t('field.location'), dataIndex: 'fieldLocation', width: 90, render: (text: string) => <span className="xapi-mono-muted">{text}</span> },
          { title: t('field.path'), width: 280, render: (_, row) => <div style={{ paddingLeft: fieldDepth(row) * 18 }}>
            <Mono>{lastSegment(row)}</Mono><FieldHints hints={hintsOf(row.index)} /></div> },
          { title: t('field.displayName'), dataIndex: 'fieldName', ellipsis: true },
          { title: t('capability.type'), width: 110, render: (_, row) => <span className="xapi-mono-muted"><Mono>{row.dataType}</Mono></span> },
          { title: t('form.required'), width: 70, render: (_, row) => row.required ? t('field.yes') : t('field.no') },
          fixedValues
            ? { title: t('field.source'), width: 220, render: (_, row) => fixedValues.has(fieldKey(row))
              ? readOnly ? <span className="xapi-source-fixed">{t('field.fixedValue', { value: fixedValues.get(fieldKey(row)) ?? '' })}</span>
                : <button type="button" className="xapi-source-fixed xapi-source-button" title={t('field.changeSource')}
                  onClick={() => setEditing({ index: row.index, field: value[row.index] })}>{t('field.fixedValue', { value: fixedValues.get(fieldKey(row)) ?? '' })}</button>
              : row.fieldPath.includes('[]') ? '—' : row.required ? t('field.source.node') : t('field.source.nodeOptional') }
            : { title: t('mids.note'), dataIndex: 'description', ellipsis: true, render: (text: string | null) => text || '—' },
          ...(readOnly ? [] : [{ title: t('grants.actions'), width: 110, render: (_: unknown, row: ApiField & { index: number }) => <span className="xapi-ops">
            <button type="button" className="xapi-link" onClick={() => setEditing({ index: row.index, field: value[row.index] })}>{t('protocol.edit')}</button>
            <button type="button" className="xapi-link" onClick={() => remove(row.index)}>{t('endpointEditor.delete')}</button>
          </span> }]),
        ]} />
    </div>
    {editing && <FieldPanel key={editing.index ?? 'new'} field={editing.field} locations={locations} creating={editing.index === null}
      fixed={fixedValues ? fixedValues.get(fieldKey(editing.field)) ?? null : undefined} onCancel={() => setEditing(null)} onApply={apply} />}
  </div>;
}

function FieldHints({ hints }: { hints: FieldHint[] }) {
  const t = useT('channel');
  if (!hints.length) return null;
  return <div className="xapi-inline-hint">{hints.map(item => item.message).join(t('error.fieldSeparator'))}</div>;
}

/** 编辑字段浮层：位置与类型、字段路径（等宽）、显示名称、必填开关、说明；“应用”只更新页面草稿，保存时一并提交。 */
/**
 * 编辑字段浮层：位置与类型、字段路径（等宽）、显示名称、必填开关、说明；原子 API 请求字段另有“来源”：
 * 节点赋值，或协议固定值（常量表达式）。“应用”只更新页面草稿，保存时一并提交。
 */
function FieldPanel({ field, locations, creating, fixed, onCancel, onApply }: {
  field: ApiField; locations: FieldLocation[]; creating: boolean;
  /** undefined 表示本方向没有来源（标准 API、响应字段）；null 表示节点赋值。 */
  fixed?: string | null;
  onCancel: () => void; onApply: (field: ApiField, fixed: string | null) => void;
}) {
  const t = useT('channel'), tCommon = useT('common');
  const [form] = Form.useForm<ApiField>();
  const [source, setSource] = useState<'NODE' | 'FIXED'>(fixed ? 'FIXED' : 'NODE');
  const [expression, setExpression] = useState(fixed ?? '');
  const path = Form.useWatch('fieldPath', form) as string | undefined;
  const arrayElement = !!path?.includes('[]');
  const fixedInvalid = source === 'FIXED' && !arrayElement && !expression.trim();
  return <div className="xapi-float" role="dialog" aria-label={creating ? t('field.add') : t('field.edit')}>
    <h3>{creating ? t('field.add') : t('field.edit')}</h3>
    <Form form={form} layout="vertical" requiredMark={false} initialValues={field}
      onFinish={values => { if (!fixedInvalid) onApply({ ...field, ...values, fieldPath: values.fieldPath.trim(), fieldName: values.fieldName.trim(),
        description: values.description?.trim() ? values.description.trim() : null }, source === 'FIXED' && !arrayElement ? expression.trim() : null); }}>
      <div className="xapi-form-row">
        <Form.Item name="fieldLocation" label={t('field.location')} rules={[{ required: true }]}>
          <Select disabled={locations.length === 1} options={fieldLocations.filter(item => locations.includes(item)).map(item => ({ value: item, label: item }))} /></Form.Item>
        <Form.Item name="dataType" label={t('capability.type')} rules={[{ required: true }]}>
          <Select options={fieldDataTypes.map(type => ({ value: type, label: type }))} /></Form.Item>
      </div>
      <Form.Item name="fieldPath" label={t('field.path')} style={{ marginTop: 12 }} rules={[{ required: true, whitespace: true, message: t('field.pathRequired') }]}>
        <Input maxLength={512} className="xapi-mono-input" placeholder="paymentContext.amount.value" /></Form.Item>
      <Form.Item name="fieldName" label={t('field.displayName')} style={{ marginTop: 12 }} rules={[{ required: true, whitespace: true, message: t('field.displayNameRequired') }]}>
        <Input maxLength={128} /></Form.Item>
      <div className="xapi-inline-switch"><Form.Item name="required" valuePropName="checked" noStyle><Switch size="small" /></Form.Item><span>{t('form.required')}</span></div>
      <Form.Item name="description" label={t('mids.note')} style={{ marginTop: 12 }}><Input maxLength={512} /></Form.Item>
      {fixed !== undefined && <div className="xapi-field" style={{ marginTop: 12 }}><span className="xapi-field-label">{t('field.source')}</span>
        <Segmented value={source} disabled={arrayElement} options={[{ value: 'NODE', label: t('field.source.node') }, { value: 'FIXED', label: t('field.source.fixed') }]}
          onChange={value => setSource(value as 'NODE' | 'FIXED')} />
        {arrayElement ? <span className="xapi-field-hint">{t('field.source.arrayHint')}</span>
          : source === 'FIXED' ? <><Input value={expression} maxLength={4096} className="xapi-mono-input" status={fixedInvalid ? 'error' : undefined}
            placeholder="'application/json'" onChange={event => setExpression(event.target.value)} />
            <span className="xapi-field-hint">{t('field.source.fixedHint')}</span></>
            : <span className="xapi-field-hint">{t('field.source.nodeHint')}</span>}
      </div>}
    </Form>
    <p className="xapi-field-hint" style={{ margin: 0 }}>{t('field.pathHint')}</p>
    <div className="xapi-float-foot"><Button onClick={onCancel}>{tCommon('action.cancel')}</Button><Button type="primary" onClick={() => form.submit()}>{t('field.apply')}</Button></div>
  </div>;
}
