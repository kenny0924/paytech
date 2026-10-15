import { useState } from 'react';
import { Alert, App, Button, Drawer, Form, Input, Select, Space, Switch, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { ReasonField } from '../vault/ReasonField';
import { useVaultCommand } from '../vault/useVaultCommand';
import { useVaultDataTypes } from '../vault/useVaultDataTypes';
import { codePattern, dataClassLabels, decodeDataTypeMutation, statusLabels, tokenModeLabels, vaultAdminPath } from '../vault/vault';
import type { VaultDataType } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const yesNo = (value: boolean) => value ? <Tag color="blue">允许</Tag> : <Tag>不允许</Tag>;

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
      <Table<VaultDataType> rowKey="dataTypeCode" tableLayout="fixed" dataSource={data.items} loading={loading} scroll={{ x: 1100 }} pagination={false} columns={[
        { title: '编码', width: 260, render: (_, row) => <div><div>{row.dataTypeCode}</div><div className="cell-meta">{row.description}</div></div> },
        { title: 'Token 模式', width: 130, render: (_, row) => tokenModeLabels[row.tokenMode] },
        { title: '数据级别', width: 110, render: (_, row) => <Tag color={row.dataClass === 'SECRET' ? 'red' : 'default'}>{dataClassLabels[row.dataClass]}</Tag> },
        { title: '人工加密', width: 100, render: (_, row) => yesNo(row.manualProtectEnabled) },
        { title: '人工解密', width: 100, render: (_, row) => yesNo(row.manualRevealEnabled) },
        { title: '状态', width: 90, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{statusLabels[row.status]}</Tag> },
        { title: '更新时间', dataIndex: 'updateTime', width: 190, render: date },
        ...(canManage ? [{ title: '操作', width: 90, fixed: 'right' as const, render: (_: unknown, row: VaultDataType) => <Button size="small" onClick={() => setEditing(row)}>编辑</Button> }] : []),
      ]} /></section>
    {editing && <DataTypeEditor key={editing === 'new' ? 'new' : editing.dataTypeCode} dataType={editing === 'new' ? null : editing}
      onClose={() => setEditing(null)} onSaved={() => { setEditing(null); setReload(v => v + 1); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

interface EditorValues {
  dataTypeCode: string; tokenMode: string; dataClass: string; description: string;
  manualProtectEnabled: boolean; manualRevealEnabled: boolean; active: boolean; reason: string;
}

/** 新建或编辑抽屉：编辑时编码与 Token 模式只读；409 时提示刷新。 */
function DataTypeEditor({ dataType, onClose, onSaved, onAuthorizationChanged }: { dataType: VaultDataType | null; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<EditorValues>(); const { message } = App.useApp();
  const command = useVaultCommand(onAuthorizationChanged, form);
  async function save(values: EditorValues) {
    const common = { dataClass: values.dataClass, description: values.description.trim(), manualProtectEnabled: values.manualProtectEnabled,
      manualRevealEnabled: values.manualRevealEnabled, reason: values.reason.trim() };
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
      ? { ...dataType, active: dataType.status === 'ACTIVE', reason: '' }
      : { tokenMode: 'REFERENCE', dataClass: 'STANDARD', manualProtectEnabled: false, manualRevealEnabled: false, active: true }}>
      <Form.Item name="dataTypeCode" label="编码" rules={[{ required: true, message: '请填写编码' }, { pattern: codePattern, message: '大写字母开头，仅大写字母、数字和下划线，最长 64 位' }]}>
        <Input disabled={!!dataType} maxLength={64} placeholder="例如 CHANNEL_ACCOUNT_CONFIG_V1" /></Form.Item>
      <Form.Item name="tokenMode" label="Token 模式" extra="同值同 Token 适合需要按值关联的字段；创建后不可修改">
        <Select disabled={!!dataType} options={Object.entries(tokenModeLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="dataClass" label="数据级别" extra="密钥级别的人工加/解密需要单独的密钥类权限">
        <Select options={Object.entries(dataClassLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
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
