import { useMemo, useState } from 'react';
import { Alert, App, Button, Descriptions, Form, Input, Modal, Select, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { enumLabel, formatDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { ReasonField } from '../vault/ReasonField';
import { useVaultCommand } from '../vault/useVaultCommand';
import { useVaultDataTypes } from '../vault/useVaultDataTypes';
import { callerPattern, codePattern, decodeAccessRuleMutation, vaultActions, vaultAdminPath, vaultStatuses } from '../vault/vault';
import type { VaultAccessRule } from '../vault/vault';
import '../configuration/configuration.css';
import '../vault/vault.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Filters { dataTypeCode?: string; callerService?: string; action?: string; status?: string }

/** 服务访问规则：按数据类型、调用服务、动作和状态筛选；有维护权限时新建或启停。 */
export function VaultAccessRules({ resources, onAuthorizationChanged }: Props) {
  const t = useT('vault'), tCommon = useT('common'), tNav = useT('nav');
  const [form] = Form.useForm<Filters>(); const page = usePaging<VaultAccessRule>(`${vaultAdminPath}/access-rules`);
  const types = useVaultDataTypes();
  const canManage = resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === 'OPERATIONS_VAULT_ACCESS_RULE_MANAGE');
  const [creating, setCreating] = useState(false); const [toggling, setToggling] = useState<VaultAccessRule | null>(null);
  const typeOptions = types.data.items.map(type => ({ value: type.dataTypeCode, label: type.dataTypeCode }));
  const actionOptions = useMemo(() => vaultActions.map(value => ({ value, label: enumLabel(t, 'enum.action', value) })), [t]);
  const statusOptions = useMemo(() => vaultStatuses.map(value => ({ value, label: enumLabel(t, 'enum.status', value) })), [t]);
  function search(values: Filters) {
    page.applyFilters(Object.fromEntries(Object.entries(values).flatMap(([k, v]) => typeof v === 'string' && v.trim() ? [[k, v.trim()]] : [])));
  }
  return <div className="configuration-page vault-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_VAULT_ACCESS_RULES')}</h1>{canManage && <Button type="primary" onClick={() => setCreating(true)}>{t('accessRules.create')}</Button>}</div>
    <Form form={form} layout="vertical" onFinish={search}><div className="vault-search">
      <Form.Item name="dataTypeCode" label={t('accessRules.field.dataType')}><Select allowClear showSearch placeholder={t('filters.all')} options={typeOptions} /></Form.Item>
      <Form.Item name="callerService" label={t('accessRules.field.callerService')}><Input allowClear maxLength={128} placeholder={t('accessRules.field.callerServicePlaceholder')} /></Form.Item>
      <Form.Item name="action" label={t('accessRules.field.action')}><Select allowClear placeholder={t('filters.all')} options={actionOptions} /></Form.Item>
      <Form.Item name="status" label={t('accessRules.field.status')}><Select allowClear placeholder={t('filters.all')} options={statusOptions} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('filters.search')}</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('filters.reset')}</Button></div>
    </div></Form>
    <section className="table-panel"><div className="table-toolbar"><span>{t('accessRules.count', { count: page.data.total })}</span><Button onClick={page.refresh} loading={page.loading}>{page.error ? tCommon('action.retry') : tCommon('action.refresh')}</Button></div>
      <Table<VaultAccessRule> rowKey="accessRuleId" tableLayout="fixed" dataSource={page.error ? [] : page.data.items} loading={page.loading} scroll={{ x: 1000 }} pagination={page.pagination} columns={[
        { title: t('accessRules.field.callerService'), dataIndex: 'callerService', width: 220, ellipsis: true },
        { title: t('accessRules.field.action'), width: 80, render: (_, row) => enumLabel(t, 'enum.action', row.action) },
        { title: t('accessRules.field.dataType'), dataIndex: 'dataTypeCode', width: 240, ellipsis: true },
        { title: t('accessRules.field.purpose'), dataIndex: 'purposeCode', width: 200, ellipsis: true },
        { title: t('accessRules.field.status'), width: 80, render: (_, row) => <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.status', row.status)}</Tag> },
        { title: t('accessRules.column.updatedAt'), dataIndex: 'updateTime', width: 190, render: (value: string) => formatDateTime(value) },
        ...(canManage ? [{ title: t('accessRules.column.actions'), width: 90, fixed: 'right' as const, render: (_: unknown, row: VaultAccessRule) =>
          <Button size="small" danger={row.status === 'ACTIVE'} onClick={() => setToggling(row)}>{row.status === 'ACTIVE' ? t('accessRules.disable') : t('accessRules.enable')}</Button> }] : []),
      ]} /></section>
    {creating && <CreateRule typeOptions={typeOptions} onClose={() => setCreating(false)} onSaved={() => { setCreating(false); page.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {toggling && <ToggleRule rule={toggling} onClose={() => setToggling(null)} onSaved={() => { setToggling(null); page.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 新建规则弹窗。 */
function CreateRule({ typeOptions, onClose, onSaved, onAuthorizationChanged }: { typeOptions: { value: string; label: string }[]; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm(); const { message } = App.useApp(); const command = useVaultCommand(onAuthorizationChanged, form);
  const t = useT('vault'), tCommon = useT('common');
  const actionOptions = useMemo(() => vaultActions.map(value => ({ value, label: enumLabel(t, 'enum.action', value) })), [t]);
  async function save(values: { callerService: string; action: string; dataTypeCode: string; purposeCode: string; reason: string }) {
    const result = await command.run(`${vaultAdminPath}/access-rules`, 'POST', { ...values, callerService: values.callerService.trim(),
      purposeCode: values.purposeCode.trim(), reason: values.reason.trim() }, decodeAccessRuleMutation, t('accessRules.saveFailed'));
    if (result) { void message.success(t('policy.saved', { version: result.policyVersion })); onSaved(); }
  }
  return <Modal title={t('accessRules.create')} open footer={null} onCancel={() => { if (!command.saving) onClose(); }} maskClosable={!command.saving}>
    <Form form={form} layout="vertical" onFinish={save} disabled={command.saving}>
      <Form.Item name="callerService" label={t('accessRules.field.callerService')} rules={[{ required: true, message: t('accessRules.field.callerServiceRequired') }, { pattern: callerPattern, message: t('accessRules.field.callerServicePattern') }]}>
        <Input maxLength={128} placeholder={t('accessRules.field.callerServicePlaceholder')} /></Form.Item>
      <Form.Item name="action" label={t('accessRules.field.action')} rules={[{ required: true, message: t('accessRules.field.actionRequired') }]}>
        <Select options={actionOptions} /></Form.Item>
      <Form.Item name="dataTypeCode" label={t('accessRules.field.dataType')} rules={[{ required: true, message: t('accessRules.field.dataTypeRequired') }]}><Select showSearch options={typeOptions} /></Form.Item>
      <Form.Item name="purposeCode" label={t('accessRules.field.purpose')} extra={t('accessRules.field.purposeHint')}
        rules={[{ required: true, message: t('accessRules.field.purposeRequired') }, { pattern: codePattern, message: t('accessRules.field.purposePattern') }]}>
        <Input maxLength={64} placeholder={t('accessRules.field.purposePlaceholder')} /></Form.Item>
      <ReasonField />
      <div className="configuration-actions"><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button><Button type="primary" htmlType="submit" loading={command.saving}>{tCommon('action.save')}</Button></div>
    </Form>
  </Modal>;
}

/** 启用或停用规则，需填写理由。 */
function ToggleRule({ rule, onClose, onSaved, onAuthorizationChanged }: { rule: VaultAccessRule; onClose: () => void; onSaved: () => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm(); const { message } = App.useApp(); const command = useVaultCommand(onAuthorizationChanged, form);
  const next = rule.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE'; const enabling = next === 'ACTIVE';
  const t = useT('vault'), tCommon = useT('common');
  async function save(values: { reason: string }) {
    const result = await command.run(`${vaultAdminPath}/access-rules/${rule.accessRuleId}`, 'PUT',
      { expectedVersion: rule.version, status: next, reason: values.reason.trim() }, decodeAccessRuleMutation, t('accessRules.saveFailed'));
    if (result) { void message.success(enabling ? t('accessRules.toggle.enabled', { version: result.policyVersion }) : t('accessRules.toggle.disabled', { version: result.policyVersion })); onSaved(); }
  }
  return <Modal title={enabling ? t('accessRules.toggle.enableTitle') : t('accessRules.toggle.disableTitle')} open footer={null} onCancel={() => { if (!command.saving) onClose(); }} maskClosable={!command.saving}>
    <Descriptions column={1} items={[{ key: 'caller', label: t('accessRules.field.callerService'), children: rule.callerService }, { key: 'action', label: t('accessRules.field.action'), children: enumLabel(t, 'enum.action', rule.action) },
      { key: 'type', label: t('accessRules.field.dataType'), children: rule.dataTypeCode }, { key: 'purpose', label: t('accessRules.field.purpose'), children: rule.purposeCode }]} />
    <Form form={form} layout="vertical" onFinish={save} disabled={command.saving || command.conflict}><ReasonField />
      {command.conflict && <Alert type="warning" showIcon title={t('accessRules.toggle.conflict')} />}
      <div className="configuration-actions"><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
        <Button type="primary" danger={next === 'DISABLED'} htmlType="submit" loading={command.saving}>{enabling ? t('accessRules.toggle.confirmEnable') : t('accessRules.toggle.confirmDisable')}</Button></div>
    </Form>
  </Modal>;
}
