import { useState } from 'react';
import { Alert, App, Button, Drawer, Form, Input, Select, Space, Switch, Table, Tag, Typography } from 'antd';
import type { FormInstance } from 'antd';
import type { ConsoleResource } from '../api/types';
import { ReasonField } from '../vault/ReasonField';
import { useVaultCommand } from '../vault/useVaultCommand';
import { useVaultDataTypes } from '../vault/useVaultDataTypes';
import {
  codePattern, dataClassLabels, decodeDataTypeMutation, sampleValuesRequired, statusLabels, tokenModeLabels, valueFormatBody, valueFormatLabels,
  valueFormatOptions, vaultAdminPath,
} from '../vault/vault';
import type { VaultDataType, VaultValueFormat } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const yesNo = (value: boolean) => value ? <Tag color="blue">允许</Tag> : <Tag>不允许</Tag>;
/** 值格式：REGEX 下一行等宽显示正则，超长省略、悬停看全文。 */
const valueFormat = (row: VaultDataType) => row.valueFormat !== 'REGEX' ? valueFormatLabels[row.valueFormat]
  : <div><Tag color="purple">{valueFormatLabels.REGEX}</Tag><Typography.Text className="vault-pattern" ellipsis={{ tooltip: row.valuePattern }}>{row.valuePattern}</Typography.Text></div>;
const maxSampleValues = 5;

/** 数据类型目录：查看全部类型与当前策略版本，有维护权限时新建或编辑。 */
export function VaultDataTypes({ resources, onAuthorizationChanged }: Props) {
  const [reload, setReload] = useState(0); const { data, loading, failed } = useVaultDataTypes(reload);
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_VAULT_DATA_TYPE_MANAGE');
  const [editing, setEditing] = useState<VaultDataType | 'new' | null>(null);
  return <div className="configuration-page vault-page">
    <div className="page-heading"><h1>数据类型</h1>
      <Space><Tag>当前策略版本 {data.currentPolicyVersion}</Tag>{canManage && <Button type="primary" onClick={() => setEditing('new')}>新建数据类型</Button>}</Space></div>
    <section className="table-panel"><div className="table-toolbar"><span>共 {data.items.length} 个数据类型</span>
      <Button onClick={() => setReload(v => v + 1)} loading={loading}>{failed ? '重试' : '刷新'}</Button></div>
      <Table<VaultDataType> rowKey="dataTypeCode" tableLayout="fixed" dataSource={data.items} loading={loading} scroll={{ x: 1180 }} pagination={false} columns={[
        { title: '编码', width: 240, render: (_, row) => <div><div>{row.dataTypeCode}</div><div className="cell-meta">{row.description}</div></div> },
        { title: 'Token 模式', width: 120, render: (_, row) => tokenModeLabels[row.tokenMode] },
        { title: '数据级别', width: 100, render: (_, row) => <Tag color={row.dataClass === 'SECRET' ? 'red' : 'default'}>{dataClassLabels[row.dataClass]}</Tag> },
        { title: '值格式', width: 200, render: (_, row) => valueFormat(row) },
        { title: '人工加密', width: 90, render: (_, row) => yesNo(row.manualProtectEnabled) },
        { title: '人工解密', width: 90, render: (_, row) => yesNo(row.manualRevealEnabled) },
        { title: '状态', width: 80, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{statusLabels[row.status]}</Tag> },
        { title: '更新时间', dataIndex: 'updateTime', width: 180, render: date },
        ...(canManage ? [{ title: '操作', width: 90, fixed: 'right' as const, render: (_: unknown, row: VaultDataType) => <Button size="small" onClick={() => setEditing(row)}>编辑</Button> }] : []),
      ]} /></section>
    {editing && <DataTypeEditor key={editing === 'new' ? 'new' : editing.dataTypeCode} dataType={editing === 'new' ? null : editing}
      onClose={() => setEditing(null)} onSaved={() => { setEditing(null); setReload(v => v + 1); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

interface EditorValues {
  dataTypeCode: string; tokenMode: string; dataClass: string; description: string;
  manualProtectEnabled: boolean; manualRevealEnabled: boolean; active: boolean; reason: string;
  valueFormat: VaultValueFormat; valuePattern?: string; sampleValues?: string[];
}

/** 新建或编辑抽屉：编辑时编码与 Token 模式只读；409 时提示刷新。 */
function DataTypeEditor({ dataType, onClose, onSaved, onAuthorizationChanged }: { dataType: VaultDataType | null; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<EditorValues>(); const { message } = App.useApp();
  const command = useVaultCommand(onAuthorizationChanged, form);
  const format = Form.useWatch('valueFormat', form) ?? dataType?.valueFormat ?? 'TEXT';
  async function save(values: EditorValues) {
    const common = { dataClass: values.dataClass, description: values.description.trim(), manualProtectEnabled: values.manualProtectEnabled,
      manualRevealEnabled: values.manualRevealEnabled, reason: values.reason.trim(), ...valueFormatBody(values.valueFormat, values.valuePattern, values.sampleValues) };
    const result = dataType
      ? await command.run(`${vaultAdminPath}/data-types/${encodeURIComponent(dataType.dataTypeCode)}`, 'PUT',
        { ...common, expectedVersion: dataType.version, status: values.active ? 'ACTIVE' : 'DISABLED' },
        value => decodeDataTypeMutation(value, dataType.dataTypeCode), '数据类型保存失败')
      : await command.run(`${vaultAdminPath}/data-types`, 'POST', { ...common, dataTypeCode: values.dataTypeCode, tokenMode: values.tokenMode },
        value => decodeDataTypeMutation(value, values.dataTypeCode), '数据类型保存失败');
    if (result) { void message.success(`已保存，策略版本 ${result.policyVersion}`); onSaved(); }
  }
  return <Drawer title={dataType ? `编辑 ${dataType.dataTypeCode}` : '新建数据类型'} open size={520} onClose={() => { if (!command.saving) onClose(); }} maskClosable={!command.saving}>
    <Form form={form} layout="vertical" onFinish={save} disabled={command.saving || command.conflict} initialValues={dataType
      ? { ...dataType, active: dataType.status === 'ACTIVE', reason: '', sampleValues: [''] }
      : { tokenMode: 'REFERENCE', dataClass: 'STANDARD', valueFormat: 'TEXT', sampleValues: [''], manualProtectEnabled: false, manualRevealEnabled: false, active: true }}>
      <Form.Item name="dataTypeCode" label="编码" rules={[{ required: true, message: '请填写编码' }, { pattern: codePattern, message: '大写字母开头，仅大写字母、数字和下划线，最长 64 位' }]}>
        <Input disabled={!!dataType} maxLength={64} placeholder="例如 CHANNEL_ACCOUNT_CONFIG_V1" /></Form.Item>
      <Form.Item name="tokenMode" label="Token 模式" extra="同值同 Token 适合需要按值关联的字段；创建后不可修改">
        <Select disabled={!!dataType} options={Object.entries(tokenModeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="dataClass" label="数据级别" extra="密钥级别的人工加/解密需要单独的密钥类权限">
        <Select options={Object.entries(dataClassLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="valueFormat" label="值格式" extra="服务和人工加密时按此格式校验原值">
        <Select options={valueFormatOptions} optionRender={option => <div><div>{option.data.label}</div><div className="cell-meta">{option.data.description}</div></div>} /></Form.Item>
      {format === 'REGEX' && <ValueFormatPatternFields form={form} dataType={dataType} />}
      <Form.Item name="description" label="用途说明" rules={[{ required: true, whitespace: true, message: '请填写用途说明' }]}>
        <Input.TextArea rows={2} maxLength={256} showCount /></Form.Item>
      <Form.Item name="manualProtectEnabled" label="允许人工加密" valuePropName="checked"><Switch /></Form.Item>
      <Form.Item name="manualRevealEnabled" label="允许人工解密" valuePropName="checked"><Switch /></Form.Item>
      {dataType && <Form.Item name="active" label="启用" valuePropName="checked" extra="停用后服务与人工都不能再加/解密该类型的数据"><Switch /></Form.Item>}
      <ReasonField />
      {command.conflict && <Alert type="warning" showIcon title="该数据类型已被他人修改，请关闭后刷新列表再编辑。" />}
      <div className="configuration-actions"><Button disabled={command.saving} onClick={onClose}>取消</Button>
        <Button type="primary" htmlType="submit" loading={command.saving}>保存</Button></div>
    </Form>
  </Drawer>;
}

/** REGEX 的正则与测试样例；样例是否必填按 sampleValuesRequired 判断，空输入框不计入。 */
function ValueFormatPatternFields({ form, dataType }: { form: FormInstance<EditorValues>; dataType: VaultDataType | null }) {
  const pattern = Form.useWatch('valuePattern', form);
  const required = sampleValuesRequired(dataType, 'REGEX', pattern);
  return <>
    <Form.Item name="valuePattern" label="正则" extra="按整值匹配，无需写 ^ $；RE2 语法；保存后约 10 秒内对所有服务和人工加密生效"
      rules={[{ required: true, message: '请填写正则' }]}>
      <Input maxLength={256} autoComplete="off" spellCheck={false} className="vault-pattern-input" /></Form.Item>
    <Form.List name="sampleValues" rules={[{ validator: async (_, samples: (string | undefined)[] | undefined) => {
      if (sampleValuesRequired(dataType, 'REGEX', form.getFieldValue('valuePattern')) && !(samples ?? []).some(sample => sample)) throw new Error('新建、切换为正则或修改正则时至少填写 1 个测试样例');
    } }]}>
      {(fields, { add, remove }, { errors }) => <Form.Item label="测试样例" required={required} extra="样例只用于保存时校验正则，不会保存">
        {fields.map(field => <Space.Compact key={field.key} block className="vault-sample-row">
          <Form.Item name={field.name} noStyle><Input maxLength={4096} autoComplete="off" spellCheck={false} placeholder="示例值，请勿填写真实数据" /></Form.Item>
          <Button disabled={fields.length <= 1} onClick={() => remove(field.name)}>删除</Button>
        </Space.Compact>)}
        {fields.length < maxSampleValues && <Button type="dashed" block onClick={() => add('')}>添加样例</Button>}
        <Form.ErrorList errors={errors} />
      </Form.Item>}
    </Form.List>
  </>;
}
