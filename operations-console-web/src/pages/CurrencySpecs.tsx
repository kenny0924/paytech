import { useEffect, useState } from 'react';
import { Alert, Button, Form, Input, InputNumber, Modal, Select, Space, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { ConfigurationStatusModal } from '../configuration/ConfigurationStatusModal';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import {
  createCurrencySpecBody, currencyCodePattern, currencySpecPath, currencyStatusChange, currencyStatusColors, currencyStatusLabels,
  decodeCurrencySpecList, decodeCurrencySpecMutation, editableCurrencySpec, manageCurrencySpecs, maxMinorUnit, roundingModeLabels,
  roundingModes, updateCurrencySpecBody,
} from '../configuration/currencySpecModel';
import type { CurrencySpec, CurrencySpecFormValues } from '../configuration/currencySpecModel';
import '../configuration/configuration.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const roundingOptions = roundingModes.map(value => ({ value, label: roundingModeLabels[value] }));

/** 币种：Platform 维护的全平台币种规则；有管理按钮时可新建、编辑草稿与启停，否则只读。 */
export function CurrencySpecs({ resources, onAuthorizationChanged }: Props) {
  const notify = useErrorNotification();
  const canManage = resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === manageCurrencySpecs);
  const [items, setItems] = useState<CurrencySpec[]>([]); const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const [editing, setEditing] = useState<CurrencySpec | 'new' | null>(null); const [changing, setChanging] = useState<CurrencySpec | null>(null);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    session.request(currencySpecPath, { signal: controller.signal }, decodeCurrencySpecList)
      .then(value => { if (!controller.signal.aborted) setItems(value); })
      .catch(error => { if (!controller.signal.aborted) { setItems([]); setFailed(true); notify(error, '币种列表加载失败'); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [reload, notify]);
  const refresh = () => setReload(value => value + 1);
  return <div className="configuration-page">
    <div className="page-header"><h1>币种</h1>{canManage && <Button type="primary" onClick={() => setEditing('new')}>＋ 新建币种</Button>}</div>
    {failed && <Alert type="warning" showIcon title="币种列表暂时无法加载" action={<Button onClick={refresh}>重试</Button>} />}
    <section className="table-panel" style={{ marginTop: 16 }}><div className="table-toolbar"><span>共 {items.length} 个币种</span>
      <Button loading={loading} onClick={refresh}>刷新</Button></div>
      <Table<CurrencySpec> rowKey="currency" tableLayout="fixed" dataSource={items} loading={loading} pagination={false} scroll={{ x: 820 }} columns={[
        { title: '币种', dataIndex: 'currency', width: 100 },
        { title: '小数位', dataIndex: 'minorUnit', width: 90 },
        { title: '舍入方式', width: 180, render: (_, row) => roundingModeLabels[row.roundingMode] },
        { title: '状态', width: 90, render: (_, row) => <Tag color={currencyStatusColors[row.status]}>{currencyStatusLabels[row.status]}</Tag> },
        { title: '最后更新', dataIndex: 'updateTime', width: 180, render: date },
        ...(canManage ? [{ title: '操作', width: 140, fixed: 'right' as const, render: (_: unknown, row: CurrencySpec) => <Space>
          {editableCurrencySpec(row) && <Button size="small" onClick={() => setEditing(row)}>编辑</Button>}
          <Button size="small" danger={row.status === 'ACTIVE'} onClick={() => setChanging(row)}>{currencyStatusChange(row).verb}</Button>
        </Space> }] : []),
      ]} /></section>
    {editing && <CurrencySpecEditor key={editing === 'new' ? 'new' : editing.currency} spec={editing === 'new' ? null : editing}
      onClose={() => setEditing(null)} onSaved={() => { setEditing(null); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {changing && <ConfigurationStatusModal kind="currency" code={changing.currency} version={changing.version} enabled={changing.status === 'ACTIVE'}
      notice={<Alert type={changing.status === 'ACTIVE' ? 'info' : 'warning'} showIcon title={currencyStatusChange(changing).notice} style={{ marginBottom: 16 }} />}
      onClose={() => setChanging(null)} onSaved={() => { setChanging(null); refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 新建或编辑草稿弹窗：编辑时币种代码只读；409 时保留草稿并提示刷新。 */
function CurrencySpecEditor({ spec, onClose, onSaved, onAuthorizationChanged }: {
  spec: CurrencySpec | null; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<CurrencySpecFormValues>(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  async function save(values: CurrencySpecFormValues) {
    const result = spec
      ? await editor.save(`${currencySpecPath}/${encodeURIComponent(spec.currency)}`, 'PUT', updateCurrencySpecBody(values, spec),
        value => decodeCurrencySpecMutation(value, spec.currency, 'DRAFT'))
      : await editor.save(currencySpecPath, 'POST', createCurrencySpecBody(values),
        value => decodeCurrencySpecMutation(value, createCurrencySpecBody(values).currency, 'DRAFT'));
    if (result) onSaved();
  }
  return <Modal open title={spec ? `编辑币种 ${spec.currency}` : '新建币种'} okText="保存" cancelText="取消" confirmLoading={editor.saving}
    okButtonProps={{ disabled: editor.conflict }} cancelButtonProps={{ disabled: editor.saving }} closable={!editor.saving} maskClosable={!editor.saving}
    onCancel={onClose} onOk={() => form.submit()}>
    <Form form={form} layout="vertical" requiredMark={false} disabled={editor.saving || editor.conflict} onFinish={save}
      onValuesChange={() => editor.setDirty(true)}
      initialValues={spec ? { currency: spec.currency, minorUnit: spec.minorUnit, roundingMode: spec.roundingMode, reason: '' } : { roundingMode: 'HALF_UP' }}>
      <Form.Item name="currency" label="币种代码" extra={spec ? undefined : 'ISO 4217 三位大写字母，创建后不可修改'}
        normalize={(value?: string) => value?.toUpperCase()}
        rules={[{ required: true, message: '请填写币种代码' }, { pattern: currencyCodePattern, message: '请输入三位大写字母，例如 AED' }]}>
        <Input disabled={!!spec} maxLength={3} placeholder="例如 AED" /></Form.Item>
      <Form.Item name="minorUnit" label="小数位" extra="金额允许保留的小数位数；启用后不可修改"
        rules={[{ required: true, message: '请填写小数位' }, { type: 'integer', min: 0, max: maxMinorUnit, message: `请输入 0 至 ${maxMinorUnit} 的整数` }]}>
        <InputNumber min={0} max={maxMinorUnit} precision={0} style={{ width: '100%' }} /></Form.Item>
      <Form.Item name="roundingMode" label="舍入方式" extra="启用后不可修改" rules={[{ required: true, message: '请选择舍入方式' }]}>
        <Select options={roundingOptions} /></Form.Item>
      <Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true, message: '请填写变更原因' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount /></Form.Item>
      {!spec && <p className="muted">新币种保存后为草稿，启用前不会被任何业务使用。</p>}
      {editor.conflict && <Alert type="warning" showIcon title="币种已有更新，草稿已保留。请关闭后刷新列表再编辑。" />}
    </Form>
  </Modal>;
}
