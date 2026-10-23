import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Drawer, Form, Input, Select, Space, Table, Tag } from 'antd';
import type { Account, ConsoleResource, Role } from '../api/types';
import { ApiError, session } from '../api/session';
import { requestAllPages } from '../api/paging';
import { passwordRules } from '../auth/RequiredAction';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { DeleteConfirmationModal } from '../components/DeleteConfirmationModal';
import { accountDeletionCommand, canDeleteAccount } from './accountDeletion';
import { accountCommand } from './accountCommand';
import type { AccountAction, AccountCommand } from './accountCommand';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';

export function Accounts({ resources, actorId }: { resources: ConsoleResource[]; actorId: string }) {
  const { message } = App.useApp();
  const t = useT('access'), tCommon = useT('common'), tNav = useT('nav');
  const notifyError = useErrorNotification();
  const { data, loading, error, refresh, pagination } = usePaging<Account>('/iam/v1/accounts');
  const [edit, setEdit] = useState<{ action: AccountAction; account?: Account } | null>(null);
  const [deletingAccount, setDeletingAccount] = useState<Account | null>(null);
  const [roles, setRoles] = useState<Role[]>([]);
  const [roleError, setRoleError] = useState('');
  const [rolesLoading, setRolesLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [conflicted, setConflicted] = useState(false);
  const [form] = Form.useForm();
  const command = useRef<AccountCommand | null>(null);
  const inFlight = useRef(false);
  const roleRequest = useRef<AbortController | null>(null);
  const can = (code: string) => resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);

  useEffect(() => () => roleRequest.current?.abort(), []);

  async function loadRoles() {
    roleRequest.current?.abort();
    const controller = new AbortController(); roleRequest.current = controller;
    setRolesLoading(true); setRoleError(''); setRoles([]);
    try {
      const roles = await requestAllPages<Role>('/iam/v1/roles', controller.signal);
      if (!controller.signal.aborted) setRoles(roles);
    } catch (error) {
      if (!controller.signal.aborted) {
        setRoleError(t('accounts.rolesNotLoaded'));
        notifyError(error, t('accounts.rolesLoadFailed'));
      }
    } finally { if (!controller.signal.aborted) setRolesLoading(false); }
  }
  async function open(action: AccountAction, account?: Account) {
    if (inFlight.current || deletingAccount) return;
    if (action === 'enable' && (!can('OPERATIONS_ACCOUNT_ENABLE') || account?.status !== 'DISABLED')) return;
    roleRequest.current?.abort();
    setEdit({ action, account }); setRoleError(''); setRoles([]); command.current = null;
    setRolesLoading(false); setConflicted(false);
    form.resetFields(); form.setFieldsValue({ roleIds: account?.roles.map(role => role.roleId) || [] });
    if (action === 'create' || action === 'roles') await loadRoles();
  }
  function openDelete(account: Account) {
    if (inFlight.current || deletingAccount || !can('OPERATIONS_ACCOUNT_DELETE') || !canDeleteAccount(account, actorId)) return;
    roleRequest.current?.abort(); setEdit(null); form.resetFields(); command.current = null;
    setRoles([]); setRoleError(''); setRolesLoading(false); setDeletingAccount(account);
  }
  function deleted() {
    setDeletingAccount(null);
    if (data.items.length === 1 && pagination.current > 1) {
      pagination.onChange(pagination.current - 1, pagination.pageSize);
    } else refresh();
  }
  async function save(values: Record<string, unknown>) {
    if (!edit || inFlight.current || conflicted || deletingAccount || rolesLoading || roleError) return;
    inFlight.current = true; setSaving(true);
    try {
      command.current = accountCommand(edit.action, edit.account, values, command.current);
      await session.request(command.current.path, {
        method: command.current.method,
        headers: { 'Idempotency-Key': command.current.key }, body: command.current.payload,
      });
      setEdit(null); form.resetFields(); command.current = null;
      refresh(); void message.success(t('accounts.saved'));
    } catch (error) {
      notifyError(error, t('accounts.actionFailed'));
      if (error instanceof ApiError && error.status === 409 && edit.action !== 'create') setConflicted(true);
      if (error instanceof ApiError && error.status === 403) refresh();
    } finally { inFlight.current = false; setSaving(false); }
  }

  return <>
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_ACCOUNTS')}</h1>
      {can('OPERATIONS_ACCOUNT_CREATE') && <Button type="primary" disabled={saving || !!deletingAccount} onClick={() => void open('create')}>{t('accounts.create')}</Button>}
    </div>
    <section className="table-panel"><div className="table-toolbar"><span className="table-caption">{t('accounts.total', { total: data.total })}</span>
      <Button size="small" disabled={!!deletingAccount} onClick={refresh} loading={loading}>{tCommon('action.refresh')}</Button></div>
      <Table<Account> rowKey="accountId" dataSource={error ? [] : data.items} loading={loading}
        scroll={{ x: 950 }} pagination={pagination}
        locale={{ emptyText: error ? t('accounts.unavailable') : t('accounts.empty') }} columns={[
          { title: t('accounts.column.account'), dataIndex: 'emailMasked', render: (value: string, row) => <div><span className="account-name">{value || t('accounts.bootstrapAccount')}</span><div className="cell-meta">{row.accountId}</div></div> },
          { title: t('accounts.column.roles'), render: (_, row) => <span className="role-label">{row.roles.map(role => role.roleName).join(t('accounts.roleSeparator'))}</span> },
          { title: t('accounts.column.status'), render: (_, row) => <Space direction="vertical" size={2}>
            <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.accountStatus', row.status)}</Tag>
            {row.passwordChangeRequired && <span className="cell-meta">{t('accounts.passwordChangeRequired')}</span>}
          </Space> },
          { title: t('accounts.column.actions'), width: 230, render: (_, row) => <Space>
            {can('OPERATIONS_ACCOUNT_ASSIGN_ROLES') && <Button type="link" size="small" disabled={saving || !!deletingAccount} onClick={() => void open('roles', row)}>{t('accounts.action.assignRoles')}</Button>}
            {can('OPERATIONS_ACCOUNT_DISABLE') && row.status === 'ACTIVE' && row.accountId !== actorId
              && <Button type="link" danger size="small" disabled={saving || !!deletingAccount} onClick={() => void open('disable', row)}>{t('accounts.action.disable')}</Button>}
            {can('OPERATIONS_ACCOUNT_ENABLE') && row.status === 'DISABLED'
              && <Button type="link" size="small" disabled={saving || !!deletingAccount} onClick={() => void open('enable', row)}>{t('accounts.action.enable')}</Button>}
            {can('OPERATIONS_ACCOUNT_DELETE') && canDeleteAccount(row, actorId)
              && <Button type="link" danger size="small" disabled={saving || !!deletingAccount} onClick={() => openDelete(row)}>{t('accounts.action.delete')}</Button>}
          </Space> },
        ]} />
    </section>
    {deletingAccount && <DeleteConfirmationModal key={deletingAccount.accountId} subject={t('accounts.deleteSubject')}
      allowed={can('OPERATIONS_ACCOUNT_DELETE') && canDeleteAccount(deletingAccount, actorId)}
      buildCommand={(reason, previous) => accountDeletionCommand(deletingAccount, reason, previous)}
      onClose={() => setDeletingAccount(null)} onDeleted={deleted}
      onRefresh={() => { setDeletingAccount(null); refresh(); }}>
      <p>{deletingAccount.emailMasked} · {deletingAccount.accountId}</p>
      <p>{t('accounts.deleteWarning')}</p>
    </DeleteConfirmationModal>}
    <Drawer open={!!edit} onClose={() => {
      if (!inFlight.current) { roleRequest.current?.abort(); setEdit(null); form.resetFields(); command.current = null; }
    }}
      title={edit?.action === 'create' ? t('accounts.drawer.createTitle') : edit?.action === 'roles' ? t('accounts.action.assignRoles') : edit?.action === 'enable' ? t('accounts.drawer.enableTitle') : t('accounts.drawer.disableTitle')} destroyOnHidden
      closable={!saving} keyboard={!saving} mask={{ closable: !saving }}
      extra={<Button type="primary" danger={edit?.action === 'disable'} onClick={() => form.submit()} loading={saving}
        disabled={conflicted || rolesLoading || !!roleError}>{edit?.action === 'disable' ? t('accounts.drawer.confirmDisable') : edit?.action === 'enable' ? t('accounts.drawer.confirmEnable') : tCommon('action.save')}</Button>}>
      {roleError && <p className="muted">{roleError}<Button type="link" onClick={() => void loadRoles()}>{tCommon('action.retry')}</Button></p>}
      {conflicted && <Alert type="warning" className="form-alert" showIcon title={t('accounts.drawer.conflict')}
        action={<Button onClick={() => { roleRequest.current?.abort(); setEdit(null); form.resetFields(); command.current = null; refresh(); }}>{tCommon('action.refreshList')}</Button>} />}
      {edit?.action === 'disable' && <Alert type="warning" title={t('accounts.drawer.disableWarning')} className="form-alert" showIcon />}
      {edit?.action === 'enable' && <Alert type="info" title={t('accounts.drawer.enableHint')} className="form-alert" showIcon />}
      {edit?.account && <p className="muted">{edit.account.emailMasked} · {edit.account.accountId}</p>}
      <Form form={form} layout="vertical" onFinish={save} requiredMark={false} disabled={saving || conflicted}>
        {edit?.action === 'create' && <>
          <Form.Item name="email" label={t('accounts.drawer.email')} rules={[{ required: true, type: 'email', message: t('accounts.drawer.emailInvalid') }]}><Input autoComplete="off" maxLength={320} /></Form.Item>
          <Form.Item name="initialPassword" label={t('accounts.drawer.initialPassword')} rules={passwordRules()} extra={t('accounts.drawer.initialPasswordHint')}>
            <Input.Password autoComplete="new-password" maxLength={512} />
          </Form.Item>
        </>}
        {(edit?.action === 'create' || edit?.action === 'roles') && <Form.Item name="roleIds" label={t('accounts.column.roles')} rules={[{ required: true, type: 'array', min: 1, message: t('accounts.drawer.rolesRequired') }]}>
          <Select mode="multiple" placeholder={t('accounts.drawer.rolesPlaceholder')} loading={rolesLoading} disabled={rolesLoading || !!roleError}
            options={roles.map(role => ({ label: role.roleName, value: role.roleId }))} />
        </Form.Item>}
        <Form.Item name="reason" label={tCommon('reason.label')} rules={[{ required: true, whitespace: true, message: tCommon('reason.required') }]}>
          <Input.TextArea rows={4} maxLength={500} showCount placeholder={t('accounts.drawer.reasonPlaceholder')} />
        </Form.Item>
      </Form>
    </Drawer>
  </>;
}
