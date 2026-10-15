import { useState } from 'react';
import { Alert, App, Button, Descriptions, Form, Input, Modal, Select, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { ReasonField } from '../vault/ReasonField';
import { useVaultCommand } from '../vault/useVaultCommand';
import { useVaultDataTypes } from '../vault/useVaultDataTypes';
import { actionLabels, callerPattern, codePattern, decodeAccessRuleMutation, statusLabels, vaultAdminPath } from '../vault/vault';
import type { VaultAccessRule } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Filters { dataTypeCode?: string; callerService?: string; action?: string; status?: string }
const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });

/** 服务访问规则：按数据类型、调用服务、动作和状态筛选；有维护权限时新建或启停。 */
export function VaultAccessRules({ resources, onAuthorizationChanged }: Props) {
  const [form] = Form.useForm<Filters>(); const page = usePaging<VaultAccessRule>(`${vaultAdminPath}/access-rules`);
  const types = useVaultDataTypes();
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_VAULT_ACCESS_RULE_MANAGE');
  const [creating, setCreating] = useState(false); const [toggling, setToggling] = useState<VaultAccessRule | null>(null);
  const typeOptions = types.data.items.map(t => ({ value: t.dataTypeCode, label: t.dataTypeCode }));
  function search(values: Filters) {
    page.applyFilters(Object.fromEntries(Object.entries(values).flatMap(([k, v]) => typeof v === 'string' && v.trim() ? [[k, v.trim()]] : [])));
  }
  return <div className="configuration-page vault-page">
    <div className="page-heading"><h1>访问规则</h1>{canManage && <Button type="primary" onClick={() => setCreating(true)}>新建访问规则</Button>}</div>
    <Form form={form} layout="vertical" onFinish={search}><div className="vault-search">
      <Form.Item name="dataTypeCode" label="数据类型"><Select allowClear showSearch placeholder="全部" options={typeOptions} /></Form.Item>
      <Form.Item name="callerService" label="调用服务"><Input allowClear maxLength={128} placeholder="例如 commerce-service" /></Form.Item>
      <Form.Item name="action" label="动作"><Select allowClear placeholder="全部" options={Object.entries(actionLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={Object.entries(statusLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>共 {page.data.total} 条规则</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? '重试' : '刷新'}</Button></div>
      <Table<VaultAccessRule> rowKey="accessRuleId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1000 }} pagination={page.pagination} columns={[
        { title: '调用服务', dataIndex: 'callerService', width: 220, ellipsis: true },
        { title: '动作', width: 80, render: (_, row) => actionLabels[row.action] },
        { title: '数据类型', dataIndex: 'dataTypeCode', width: 240, ellipsis: true },
        { title: '用途', dataIndex: 'purposeCode', width: 200, ellipsis: true },
        { title: '状态', width: 80, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{statusLabels[row.status]}</Tag> },
        { title: '更新时间', dataIndex: 'updateTime', width: 190, render: date },
        ...(canManage ? [{ title: '操作', width: 90, fixed: 'right' as const, render: (_: unknown, row: VaultAccessRule) =>
          <Button size="small" danger={row.status === 'ACTIVE'} onClick={() => setToggling(row)}>{row.status === 'ACTIVE' ? '停用' : '启用'}</Button> }] : []),
      ]} /></section>
    {creating && <CreateRule typeOptions={typeOptions} onClose={() => setCreating(false)} onSaved={() => { setCreating(false); page.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {toggling && <ToggleRule rule={toggling} onClose={() => setToggling(null)} onSaved={() => { setToggling(null); page.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 新建规则弹窗。 */
function CreateRule({ typeOptions, onClose, onSaved, onAuthorizationChanged }: { typeOptions: { value: string; label: string }[]; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm(); const { message } = App.useApp(); const command = useVaultCommand(onAuthorizationChanged, form);
  async function save(values: { callerService: string; action: string; dataTypeCode: string; purposeCode: string; reason: string }) {
    const result = await command.run(`${vaultAdminPath}/access-rules`, 'POST', { ...values, callerService: values.callerService.trim(),
      purposeCode: values.purposeCode.trim(), reason: values.reason.trim() }, decodeAccessRuleMutation, '访问规则保存失败');
    if (result) { void message.success(`已保存，策略版本 ${result.policyVersion}`); onSaved(); }
  }
  return <Modal title="新建访问规则" open footer={null} onCancel={() => { if (!command.saving) onClose(); }} maskClosable={!command.saving}>
    <Form form={form} layout="vertical" onFinish={save} disabled={command.saving}>
      <Form.Item name="callerService" label="调用服务" rules={[{ required: true, message: '请填写调用服务' }, { pattern: callerPattern, message: '小写字母开头，仅小写字母、数字和连字符' }]}>
        <Input maxLength={128} placeholder="例如 commerce-service" /></Form.Item>
      <Form.Item name="action" label="动作" rules={[{ required: true, message: '请选择动作' }]}>
        <Select options={Object.entries(actionLabels).map(([value, label]) => ({ value, label }))} /></Form.Item>
      <Form.Item name="dataTypeCode" label="数据类型" rules={[{ required: true, message: '请选择数据类型' }]}><Select showSearch options={typeOptions} /></Form.Item>
      <Form.Item name="purposeCode" label="用途" extra="OPERATIONS_MANUAL 为人工操作保留，不能用于服务规则"
        rules={[{ required: true, message: '请填写用途' }, { pattern: codePattern, message: '大写字母开头，仅大写字母、数字和下划线' }]}>
        <Input maxLength={64} placeholder="例如 CHECKOUT_CONTACT" /></Form.Item>
      <ReasonField />
      <div className="configuration-actions"><Button disabled={command.saving} onClick={onClose}>取消</Button><Button type="primary" htmlType="submit" loading={command.saving}>保存</Button></div>
    </Form>
  </Modal>;
}

/** 启用或停用规则，需填写理由。 */
function ToggleRule({ rule, onClose, onSaved, onAuthorizationChanged }: { rule: VaultAccessRule; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm(); const { message } = App.useApp(); const command = useVaultCommand(onAuthorizationChanged, form);
  const next = rule.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
  async function save(values: { reason: string }) {
    const result = await command.run(`${vaultAdminPath}/access-rules/${rule.accessRuleId}`, 'PUT',
      { expectedVersion: rule.version, status: next, reason: values.reason.trim() }, decodeAccessRuleMutation, '访问规则保存失败');
    if (result) { void message.success(`已${statusLabels[next]}，策略版本 ${result.policyVersion}`); onSaved(); }
  }
  return <Modal title={`${statusLabels[next]}访问规则`} open footer={null} onCancel={() => { if (!command.saving) onClose(); }} maskClosable={!command.saving}>
    <Descriptions column={1} items={[{ key: 'caller', label: '调用服务', children: rule.callerService }, { key: 'action', label: '动作', children: actionLabels[rule.action] },
      { key: 'type', label: '数据类型', children: rule.dataTypeCode }, { key: 'purpose', label: '用途', children: rule.purposeCode }]} />
    <Form form={form} layout="vertical" onFinish={save} disabled={command.saving || command.conflict}><ReasonField />
      {command.conflict && <Alert type="warning" showIcon title="该规则已被他人修改，请关闭后刷新列表。" />}
      <div className="configuration-actions"><Button disabled={command.saving} onClick={onClose}>取消</Button>
        <Button type="primary" danger={next === 'DISABLED'} htmlType="submit" loading={command.saving}>确认{statusLabels[next]}</Button></div>
    </Form>
  </Modal>;
}
