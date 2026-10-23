import { useEffect, useMemo, useRef, useState } from 'react';
import { App, Button, Drawer, Form, Input, Space, Spin, Table, Tag, Tooltip, Tree } from 'antd';
import { ApiError, session } from '../api/session';
import type { ConsoleResource, PermissionNode, RoleListItem, RoleDetailResponseBody, RolePermissionOptionsResponseBody, RoleResponseBody } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { usePaging } from '../hooks/usePaging';
import { DeleteConfirmationModal } from '../components/DeleteConfirmationModal';
import { roleDeletionCommand } from './roleDeletion';
import { permissionRemovalImpact, permissionSelection, permissionTree, roleCommand, togglePermissionNode } from './roleEditor';
import type { PermissionTreeItem, RoleCommand, RoleFormValues } from './roleEditor';
import { useT } from '../i18n/react';

export function Roles({ resources, onSaved }: { resources: ConsoleResource[]; onSaved: () => void }) {
  const { message, modal } = App.useApp();
  const t = useT('access'), tCommon = useT('common'), tNav = useT('nav');
  const notifyError = useErrorNotification();
  const { data, loading, error, refresh, pagination } = usePaging<RoleListItem>('/iam/v1/roles');
  const [edit, setEdit] = useState<{ roleId: string | null; viewOnly: boolean } | null>(null);
  const [deletingRole, setDeletingRole] = useState<RoleListItem | null>(null);
  const [role, setRole] = useState<RoleResponseBody | null>(null);
  const [nodes, setNodes] = useState<PermissionNode[]>([]);
  const [permissionIds, setPermissionIds] = useState<string[]>([]);
  const [editorLoading, setEditorLoading] = useState(false);
  const [editorFailed, setEditorFailed] = useState(false);
  const [conflicted, setConflicted] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<RoleFormValues>();
  const editorRequest = useRef<AbortController | null>(null);
  const command = useRef<RoleCommand | null>(null);
  const can = (code: string) => resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);
  const canCreate = can('OPERATIONS_ROLE_CREATE'); const canEdit = can('OPERATIONS_ROLE_EDIT');
  const canDelete = can('OPERATIONS_ROLE_DELETE');
  const viewing = !!edit?.viewOnly || !!role?.systemRole;
  const editable = !!edit && !viewing && (edit.roleId === null ? canCreate : canEdit && !!role);
  const treeData = useMemo(() => permissionTree(nodes), [nodes]);
  const checkedKeys = useMemo(() => permissionSelection(nodes, permissionIds), [nodes, permissionIds]);

  useEffect(() => () => editorRequest.current?.abort(), []);

  async function loadEditor(roleId: string | null) {
    editorRequest.current?.abort();
    const controller = new AbortController(); editorRequest.current = controller;
    setEditorLoading(true); setEditorFailed(false); setConflicted(false);
    setRole(null); setNodes([]); setPermissionIds([]); command.current = null;
    try {
      if (roleId === null) {
        const result = await session.request<RolePermissionOptionsResponseBody>('/iam/v1/role-permission-options', { signal: controller.signal });
        permissionTree(result?.nodes);
        if (!controller.signal.aborted) { setNodes(result.nodes); form.resetFields(); }
      } else {
        const result = await session.request<RoleDetailResponseBody>(`/iam/v1/roles/${encodeURIComponent(roleId)}`, { signal: controller.signal });
        permissionTree(result?.nodes);
        if (result.roleId !== roleId || typeof result.roleName !== 'string' || typeof result.roleCode !== 'string'
          || typeof result.systemRole !== 'boolean' || !Number.isSafeInteger(result.version) || result.version < 1
          || !Array.isArray(result.permissionIds) || result.permissionIds.some(id => typeof id !== 'string' || !id)) {
          throw new ApiError(502, 'INVALID_RESPONSE');
        }
        if (!controller.signal.aborted) {
          setRole(result); setNodes(result.nodes); setPermissionIds(result.permissionIds);
          form.setFieldsValue({ roleCode: result.roleCode, roleName: result.roleName, reason: '' });
        }
      }
    } catch (failure) {
      if (!controller.signal.aborted) { setEditorFailed(true); notifyError(failure, t('roles.editorLoadFailed')); }
    } finally { if (!controller.signal.aborted) setEditorLoading(false); }
  }

  function open(roleId: string | null, viewOnly = false) {
    if (saving || deletingRole) return;
    setEdit({ roleId, viewOnly }); void loadEditor(roleId);
  }
  function close() {
    if (saving) return;
    editorRequest.current?.abort(); setEdit(null); form.resetFields(); command.current = null;
  }
  function openDelete(row: RoleListItem) {
    if (saving || deletingRole || row.systemRole || row.employeeCount !== 0 || !canDelete) return;
    editorRequest.current?.abort(); setEdit(null); form.resetFields(); command.current = null;
    setDeletingRole(row);
  }
  function deleted() {
    setDeletingRole(null);
    if (data.items.length === 1 && pagination.current > 1) {
      pagination.onChange(pagination.current - 1, pagination.pageSize);
    } else refresh();
  }
  async function save(values: RoleFormValues) {
    if (!editable || editorLoading || editorFailed || conflicted || saving) return;
    setSaving(true);
    command.current = roleCommand(values, permissionIds, role, command.current);
    const current = command.current;
    try {
      await session.request<RoleResponseBody>(current.path, {
        method: current.method, headers: { 'Idempotency-Key': current.key }, body: current.payload,
      });
      setEdit(null); form.resetFields(); command.current = null;
      onSaved(); void message.success(t('roles.saved'));
    } catch (failure) {
      notifyError(failure, t('roles.saveFailed'));
      if (failure instanceof ApiError && failure.status === 409 && role) setConflicted(true);
    } finally { setSaving(false); }
  }
  function changePermission(nodeId: string) {
    const current = permissionIds;
    const impact = permissionRemovalImpact(nodes, current, nodeId);
    if (impact.removedPermissionIds.length === 0) {
      setPermissionIds(current => togglePermissionNode(nodes, current, nodeId, true)); return;
    }
    const remove = false;
    if (impact.removedDescendantPermissionIds.length === 0) {
      setPermissionIds(current => togglePermissionNode(nodes, current, nodeId, remove)); return;
    }
    modal.confirm({
      title: t('roles.removeChildren.title'),
      content: t('roles.removeChildren.content', { count: impact.removedDescendantPermissionIds.length }),
      okText: t('roles.removeChildren.confirm'), cancelText: t('roles.removeChildren.keep'), okButtonProps: { danger: true },
      onOk: () => setPermissionIds(current => togglePermissionNode(nodes, current, nodeId, false)),
    });
  }

  return <>
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_ROLES')}</h1>
      <Space><Button onClick={refresh} loading={loading} disabled={!!deletingRole}>{tCommon('action.refresh')}</Button>
        {canCreate && <Button type="primary" disabled={saving || !!deletingRole} onClick={() => open(null)}>{t('roles.create')}</Button>}</Space></div>
    <section className="table-panel">
      <Table<RoleListItem> rowKey="roleId" dataSource={error ? [] : data.items} loading={loading} scroll={{ x: 900 }}
        pagination={pagination} locale={{ emptyText: error ? t('roles.unavailable') : t('roles.empty') }} columns={[
          { title: t('roles.column.roleName'), dataIndex: 'roleName' },
          { title: t('roles.column.roleCode'), dataIndex: 'roleCode' },
          { title: t('roles.column.type'), dataIndex: 'systemRole', render: (value: boolean) => <Tag>{value ? t('roles.systemRole') : t('roles.customRole')}</Tag> },
          { title: <Tooltip title={t('roles.column.userCountHint')}>{t('roles.column.userCount')}</Tooltip>, dataIndex: 'employeeCount', width: 130 },
          { title: t('roles.column.actions'), width: 170, render: (_, row) => row.systemRole ? canEdit
            ? <Button type="link" disabled={saving || !!deletingRole} onClick={() => open(row.roleId, true)}>{t('roles.action.view')}</Button>
            : <span className="muted">{t('roles.builtIn')}</span>
            : <Space size={0}>
              {canEdit && <Button type="link" disabled={saving || !!deletingRole} onClick={() => open(row.roleId)}>{t('roles.action.edit')}</Button>}
              {canDelete && <Tooltip title={row.employeeCount > 0 ? t('roles.deleteBlocked') : undefined}>
                <span><Button type="link" danger disabled={saving || !!deletingRole || row.employeeCount !== 0}
                  onClick={() => openDelete(row)}>{t('roles.action.delete')}</Button></span></Tooltip>}
              {!canEdit && !canDelete && '—'}
            </Space> },
        ]} />
    </section>
    {deletingRole && <DeleteConfirmationModal key={deletingRole.roleId} subject={t('roles.deleteSubject')}
      allowed={canDelete && !deletingRole.systemRole && deletingRole.employeeCount === 0}
      buildCommand={(reason, previous) => roleDeletionCommand(deletingRole, reason, previous)}
      onClose={() => setDeletingRole(null)} onDeleted={deleted}
      onRefresh={() => { setDeletingRole(null); refresh(); }}>
      <p>{t('roles.deleteConfirm', { roleName: deletingRole.roleName })}</p>
    </DeleteConfirmationModal>}
    <Drawer open={!!edit} onClose={close} title={viewing ? t('roles.editor.viewTitle') : edit?.roleId ? t('roles.editor.editTitle') : t('roles.editor.createTitle')}
      className="role-editor" size={560} destroyOnHidden
      extra={editable && <Button type="primary" onClick={() => form.submit()} loading={saving}
        disabled={editorLoading || editorFailed || conflicted}>{tCommon('action.save')}</Button>}>
      {editorLoading ? <div className="content-loading"><Spin /></div>
        : editorFailed ? <p className="muted">{t('roles.editor.unavailable')}
          <Button type="link" onClick={() => edit && void loadEditor(edit.roleId)}>{tCommon('action.retry')}</Button></p>
          : <Form form={form} layout="vertical" onFinish={save} requiredMark={false} disabled={saving || !editable}>
            {viewing && <p className="muted">{t('roles.editor.systemRoleHint')}</p>}
            {conflicted && <p className="muted">{t('roles.editor.conflict')}
              <Button type="link" onClick={() => edit && void loadEditor(edit.roleId)}>{t('roles.editor.reload')}</Button></p>}
            <Form.Item name="roleName" label={t('roles.column.roleName')} rules={[{ required: true, whitespace: true, message: t('roles.editor.roleNameRequired') }]}>
              <Input maxLength={255} autoComplete="off" /></Form.Item>
            <Form.Item name="roleCode" label={t('roles.column.roleCode')} extra={viewing ? undefined : edit?.roleId ? t('roles.editor.roleCodeLocked') : t('roles.editor.roleCodeHint')}
              rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: t('roles.editor.roleCodeInvalid') }]}>
              <Input maxLength={64} disabled={saving || !editable || !!edit?.roleId} autoComplete="off" /></Form.Item>
            <Form.Item label={viewing ? t('roles.editor.rolePermissions') : t('roles.editor.permissionSettings')}>
              {!viewing && <p className="role-permission-help">{t('roles.editor.treeHelp')}</p>}
              {nodes.length > 0 ? <Tree<PermissionTreeItem> checkable checkStrictly selectable={false} defaultExpandAll
                disabled={saving || !editable || conflicted} treeData={treeData} checkedKeys={checkedKeys}
                onCheck={editable ? (_, info) => changePermission(info.node.key) : undefined} />
                : <p className="muted">{t('roles.editor.noPermissions')}</p>}
              <p className="role-permission-count">{viewing ? t('roles.editor.grantedCount', { count: new Set(permissionIds).size }) : t('roles.editor.selectedCount', { count: new Set(permissionIds).size })}</p>
            </Form.Item>
            {!viewing && <Form.Item name="reason" label={tCommon('reason.label')} rules={[{ required: true, whitespace: true, message: tCommon('reason.required') }]}>
              <Input.TextArea rows={3} maxLength={500} showCount placeholder={t('roles.editor.reasonPlaceholder')} /></Form.Item>}
          </Form>}
    </Drawer>
  </>;
}
