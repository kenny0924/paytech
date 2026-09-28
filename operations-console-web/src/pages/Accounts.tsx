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

export function Accounts({ resources, actorId }: { resources: ConsoleResource[]; actorId: string }) {
  const { message } = App.useApp();
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
        setRoleError('角色尚未加载，请重试。');
        notifyError(error, '角色加载失败');
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
      refresh(); void message.success('操作已完成，已记录审计。');
    } catch (error) {
      notifyError(error, '人员操作失败');
      if (error instanceof ApiError && error.status === 409 && edit.action !== 'create') setConflicted(true);
      if (error instanceof ApiError && error.status === 403) refresh();
    } finally { inFlight.current = false; setSaving(false); }
  }

  return <>
    <div className="page-heading"><h1>人员管理</h1>
      {can('OPERATIONS_ACCOUNT_CREATE') && <Button type="primary" disabled={saving || !!deletingAccount} onClick={() => void open('create')}>＋ 创建人员</Button>}
    </div>
    <section className="table-panel"><div className="table-toolbar"><span className="table-caption">共 {data.total} 位人员</span>
      <Button size="small" disabled={!!deletingAccount} onClick={refresh} loading={loading}>刷新</Button></div>
      <Table<Account> rowKey="accountId" dataSource={error ? [] : data.items} loading={loading}
        scroll={{ x: 950 }} pagination={pagination}
        locale={{ emptyText: error ? '暂时无法加载人员' : '暂无人员' }} columns={[
          { title: '账号', dataIndex: 'emailMasked', render: (value: string, row) => <div><span className="account-name">{value || '初始化账号'}</span><div className="cell-meta">{row.accountId}</div></div> },
          { title: '角色', render: (_, row) => <span className="role-label">{row.roles.map(role => role.roleName).join('，')}</span> },
          { title: '状态', render: (_, row) => <Space direction="vertical" size={2}>
            <Tag color={row.status === 'ACTIVE' ? 'green' : 'default'}>{({ ACTIVE: '启用', DISABLED: '停用', CLOSED: '已关闭', PENDING_VERIFICATION: '待验证' } as Record<string, string>)[row.status] || row.status}</Tag>
            {row.passwordChangeRequired && <span className="cell-meta">首次登录需改密</span>}
          </Space> },
          { title: '操作', width: 230, render: (_, row) => <Space>
            {can('OPERATIONS_ACCOUNT_ASSIGN_ROLES') && <Button type="link" size="small" disabled={saving || !!deletingAccount} onClick={() => void open('roles', row)}>分配角色</Button>}
            {can('OPERATIONS_ACCOUNT_DISABLE') && row.status === 'ACTIVE' && row.accountId !== actorId
              && <Button type="link" danger size="small" disabled={saving || !!deletingAccount} onClick={() => void open('disable', row)}>停用</Button>}
            {can('OPERATIONS_ACCOUNT_ENABLE') && row.status === 'DISABLED'
              && <Button type="link" size="small" disabled={saving || !!deletingAccount} onClick={() => void open('enable', row)}>启用</Button>}
            {can('OPERATIONS_ACCOUNT_DELETE') && canDeleteAccount(row, actorId)
              && <Button type="link" danger size="small" disabled={saving || !!deletingAccount} onClick={() => openDelete(row)}>删除</Button>}
          </Space> },
        ]} />
    </section>
    {deletingAccount && <DeleteConfirmationModal key={deletingAccount.accountId} subject="人员"
      allowed={can('OPERATIONS_ACCOUNT_DELETE') && canDeleteAccount(deletingAccount, actorId)}
      buildCommand={(reason, previous) => accountDeletionCommand(deletingAccount, reason, previous)}
      onClose={() => setDeletingAccount(null)} onDeleted={deleted}
      onRefresh={() => { setDeletingAccount(null); refresh(); }}>
      <p>{deletingAccount.emailMasked} · {deletingAccount.accountId}</p>
      <p>删除后，该人员不能再登录，角色关联会被解除，且不可恢复。</p>
    </DeleteConfirmationModal>}
    <Drawer open={!!edit} onClose={() => {
      if (!inFlight.current) { roleRequest.current?.abort(); setEdit(null); form.resetFields(); command.current = null; }
    }}
      title={edit?.action === 'create' ? '创建内部人员' : edit?.action === 'roles' ? '分配角色' : edit?.action === 'enable' ? '启用人员' : '停用人员'} destroyOnHidden
      closable={!saving} keyboard={!saving} mask={{ closable: !saving }}
      extra={<Button type="primary" danger={edit?.action === 'disable'} onClick={() => form.submit()} loading={saving}
        disabled={conflicted || rolesLoading || !!roleError}>{edit?.action === 'disable' ? '确认停用' : edit?.action === 'enable' ? '确认启用' : '保存'}</Button>}>
      {roleError && <p className="muted">{roleError}<Button type="link" onClick={() => void loadRoles()}>重试</Button></p>}
      {conflicted && <Alert type="warning" className="form-alert" showIcon title="人员已发生变化，请刷新列表获取最新版本后重新操作。"
        action={<Button onClick={() => { roleRequest.current?.abort(); setEdit(null); form.resetFields(); command.current = null; refresh(); }}>刷新列表</Button>} />}
      {edit?.action === 'disable' && <Alert type="warning" title="停用后，该人员的现有会话将被撤销。" className="form-alert" showIcon />}
      {edit?.action === 'enable' && <Alert type="info" title="启用后，该人员需要重新登录。" className="form-alert" showIcon />}
      {edit?.account && <p className="muted">{edit.account.emailMasked} · {edit.account.accountId}</p>}
      <Form form={form} layout="vertical" onFinish={save} requiredMark={false} disabled={saving || conflicted}>
        {edit?.action === 'create' && <>
          <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email', message: '请输入有效 Email' }]}><Input autoComplete="off" maxLength={320} /></Form.Item>
          <Form.Item name="initialPassword" label="一次性初始密码" rules={passwordRules} extra="请通过安全方式交付。新人员首次登录必须修改密码。">
            <Input.Password autoComplete="new-password" maxLength={512} />
          </Form.Item>
        </>}
        {(edit?.action === 'create' || edit?.action === 'roles') && <Form.Item name="roleIds" label="角色" rules={[{ required: true, type: 'array', min: 1, message: '请选择至少一个角色' }]}>
          <Select mode="multiple" placeholder="选择角色" loading={rolesLoading} disabled={rolesLoading || !!roleError}
            options={roles.map(role => ({ label: role.roleName, value: role.roleId }))} />
        </Form.Item>}
        <Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }]}>
          <Input.TextArea rows={4} maxLength={500} showCount placeholder="说明本次人员或权限变更的原因" />
        </Form.Item>
      </Form>
    </Drawer>
  </>;
}
