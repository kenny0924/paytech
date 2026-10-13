import { useMemo, useState } from 'react';
import { Button, Checkbox, Form, Input, Modal, Select, Space, Table, Tag, Tooltip } from 'antd';
import { dataTypeLabels, fieldDataTypes, fieldDepth, fieldHints } from './externalApiModel';
import type { ApiField, FieldLocation, FieldHint } from './externalApiModel';

interface Props {
  value: ApiField[];
  onChange: (next: ApiField[]) => void;
  /** 本方向允许的字段位置；只有一项时编辑弹窗不显示位置选择。 */
  locations: FieldLocation[];
  readOnly?: boolean;
  /** 服务端返回的出错行号（数组下标），与即时提示一起标红。 */
  serverErrorRows?: number[];
  /** 已设固定值的请求字段键（LOCATION:path，HEADER 小写），用于原子 API。 */
  fixedKeys?: Set<string>;
}

const emptyField = (location: FieldLocation): ApiField =>
  ({ fieldLocation: location, fieldPath: '', fieldName: '', dataType: 'STRING', required: true, description: null });
const fieldKey = (field: ApiField) =>
  `${field.fieldLocation}:${field.fieldLocation === 'HEADER' ? field.fieldPath.toLowerCase() : field.fieldPath}`;

/**
 * 字段表：按数组顺序展示，BODY 路径按层级缩进，数组元素以 [] 标出；增删改在弹窗中完成，上移/下移调整顺序。
 */
export function FieldTable({ value, onChange, locations, readOnly, serverErrorRows = [], fixedKeys }: Props) {
  const [editing, setEditing] = useState<{ index: number | null; field: ApiField } | null>(null);
  const hints = useMemo(() => fieldHints(value, locations), [value, locations]);
  const hintsOf = (index: number) => hints.filter(hint => hint.index === index);
  function move(index: number, offset: number) {
    const next = [...value]; const [item] = next.splice(index, 1); next.splice(index + offset, 0, item); onChange(next);
  }
  function save(field: ApiField) {
    const next = [...value];
    if (editing?.index === null || editing === null) next.push(field); else next[editing.index] = field;
    onChange(next); setEditing(null);
  }
  const rows = value.map((field, index) => ({ ...field, index }));
  return <div className="external-api-field-table">
    {!readOnly && <div className="table-toolbar"><span>共 {value.length} 个字段</span>
      <Button onClick={() => setEditing({ index: null, field: emptyField(locations.includes('BODY') ? 'BODY' : locations[0]) })}>添加字段</Button></div>}
    <Table rowKey="index" size="small" tableLayout="fixed" pagination={false} dataSource={rows} scroll={{ x: 900 }}
      rowClassName={row => hintsOf(row.index).length || serverErrorRows.includes(row.index) ? 'external-api-row-error' : ''}
      locale={{ emptyText: '暂无字段' }} columns={[
        { title: '位置', dataIndex: 'fieldLocation', width: 90 },
        { title: '字段路径', width: 300, render: (_, row) => <span style={{ paddingLeft: fieldDepth(row) * 16 }}>
          <code>{row.fieldPath}</code>{row.fieldPath.includes('[]') && <Tooltip title="数组元素：父对象存在时才校验必填"><Tag className="external-api-tag">[]</Tag></Tooltip>}
          {fixedKeys?.has(fieldKey(row)) && <Tag color="blue">固定</Tag>}
          <FieldHints hints={hintsOf(row.index)} /></span> },
        { title: '显示名称', dataIndex: 'fieldName', width: 160, ellipsis: true },
        { title: '类型', width: 90, render: (_, row) => dataTypeLabels[row.dataType] },
        { title: '必填', width: 70, render: (_, row) => row.required ? '是' : '否' },
        { title: '说明', dataIndex: 'description', ellipsis: true, render: (text: string | null) => text || '—' },
        ...(readOnly ? [] : [{ title: '操作', width: 210, fixed: 'right' as const, render: (_: unknown, row: ApiField & { index: number }) => <Space size={0}>
          <Button type="link" size="small" onClick={() => setEditing({ index: row.index, field: value[row.index] })}>编辑</Button>
          <Button type="link" size="small" disabled={row.index === 0} onClick={() => move(row.index, -1)}>上移</Button>
          <Button type="link" size="small" disabled={row.index === value.length - 1} onClick={() => move(row.index, 1)}>下移</Button>
          <Button type="link" size="small" danger onClick={() => onChange(value.filter((_, i) => i !== row.index))}>删除</Button>
        </Space> }]),
      ]} />
    {editing && <FieldDialog field={editing.field} locations={locations} creating={editing.index === null}
      onCancel={() => setEditing(null)} onSave={save} />}
  </div>;
}

function FieldHints({ hints }: { hints: FieldHint[] }) {
  if (!hints.length) return null;
  return <div className="external-api-field-hint">{hints.map(hint => hint.message).join('；')}</div>;
}

function FieldDialog({ field, locations, creating, onCancel, onSave }: {
  field: ApiField; locations: FieldLocation[]; creating: boolean; onCancel: () => void; onSave: (field: ApiField) => void;
}) {
  const [form] = Form.useForm<ApiField>();
  return <Modal open title={creating ? '添加字段' : '编辑字段'} onCancel={onCancel} onOk={() => form.submit()} destroyOnHidden>
    <Form form={form} layout="vertical" requiredMark={false} initialValues={field}
      onFinish={values => onSave({ ...field, ...values, fieldPath: values.fieldPath.trim(), fieldName: values.fieldName.trim(),
        description: values.description?.trim() ? values.description.trim() : null })}>
      {locations.length > 1 && <Form.Item name="fieldLocation" label="位置" rules={[{ required: true }]}>
        <Select options={locations.map(location => ({ value: location, label: location }))} /></Form.Item>}
      <Form.Item name="fieldPath" label="字段路径" extra="BODY 用点分层级，数组元素写成 items[].sku"
        rules={[{ required: true, whitespace: true, message: '请填写字段路径' }]}><Input maxLength={512} /></Form.Item>
      <Form.Item name="fieldName" label="显示名称" rules={[{ required: true, whitespace: true, message: '请填写显示名称' }]}>
        <Input maxLength={128} /></Form.Item>
      <Form.Item name="dataType" label="类型" rules={[{ required: true }]}>
        <Select options={fieldDataTypes.map(type => ({ value: type, label: dataTypeLabels[type] }))} /></Form.Item>
      <Form.Item name="required" valuePropName="checked"><Checkbox>必填</Checkbox></Form.Item>
      <Form.Item name="description" label="说明"><Input.TextArea rows={2} maxLength={512} /></Form.Item>
    </Form>
  </Modal>;
}
