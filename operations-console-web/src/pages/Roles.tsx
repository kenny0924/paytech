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

export function Roles({ resources, onSaved }: { resources: ConsoleResource[]; onSaved: () => void }) {
  const { message, modal } = App.useApp();
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
      if (!controller.signal.aborted) { setEditorFailed(true); notifyError(failure, '角色配置加载失败'); }
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
      onSaved(); void message.success('角色配置已保存，已记录审计。');
    } catch (failure) {
      notifyError(failure, '角色保存失败');
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
      title: '确认移除子项权限',
      content: `取消该节点将同时移除 ${impact.removedDescendantPermissionIds.length} 项已选子项权限。`,
      okText: '确认移除', cancelText: '保留权限', okButtonProps: { danger: true },
      onOk: () => setPermissionIds(current => togglePermissionNode(nodes, current, nodeId, false)),
    });
  }

  return <>
    <div className="page-heading"><h1>角色管理</h1>
      <Space><Button onClick={refresh} loading={loading} disabled={!!deletingRole}>刷新</Button>
        {canCreate && <Button type="primary" disabled={saving || !!deletingRole} onClick={() => open(null)}>＋ 创建角色</Button>}</Space></div>
    <section className="table-panel">
      <Table<RoleListItem> rowKey="roleId" dataSource={error ? [] : data.items} loading={loading} scroll={{ x: 900 }}
        pagination={pagination} locale={{ emptyText: error ? '暂时无法加载角色' : '暂无可分配角色' }} columns={[
          { title: '角色名称', dataIndex: 'roleName' },
          { title: '角色编码', dataIndex: 'roleCode' },
          { title: '类型', dataIndex: 'systemRole', render: (value: boolean) => <Tag>{value ? '系统角色' : '自定义角色'}</Tag> },
          { title: <Tooltip title="关联员工数包含停用账号">关联员工数</Tooltip>, dataIndex: 'employeeCount', width: 130 },
          { title: '操作', width: 170, render: (_, row) => row.systemRole ? canEdit
            ? <Button type="link" disabled={saving || !!deletingRole} onClick={() => open(row.roleId, true)}>查看</Button>
            : <span className="muted">系统内置</span>
            : <Space size={0}>
              {canEdit && <Button type="link" disabled={saving || !!deletingRole} onClick={() => open(row.roleId)}>编辑</Button>}
              {canDelete && <Tooltip title={row.employeeCount > 0 ? '请先去人员管理调整角色关联' : undefined}>
                <span><Button type="link" danger disabled={saving || !!deletingRole || row.employeeCount !== 0}
                  onClick={() => openDelete(row)}>删除</Button></span></Tooltip>}
              {!canEdit && !canDelete && '—'}
            </Space> },
        ]} />
    </section>
    {deletingRole && <DeleteConfirmationModal key={deletingRole.roleId} subject="角色"
      allowed={canDelete && !deletingRole.systemRole && deletingRole.employeeCount === 0}
      buildCommand={(reason, previous) => roleDeletionCommand(deletingRole, reason, previous)}
      onClose={() => setDeletingRole(null)} onDeleted={deleted}
      onRefresh={() => { setDeletingRole(null); refresh(); }}>
      <p>删除角色“{deletingRole.roleName}”？删除后不可恢复，也不能再分配给人员。</p>
    </DeleteConfirmationModal>}
    <Drawer open={!!edit} onClose={close} title={viewing ? '查看角色' : edit?.roleId ? '编辑角色' : '创建角色'}
      className="role-editor" size={560} destroyOnHidden
      extra={editable && <Button type="primary" onClick={() => form.submit()} loading={saving}
        disabled={editorLoading || editorFailed || conflicted}>保存</Button>}>
      {editorLoading ? <div className="content-loading"><Spin /></div>
        : editorFailed ? <p className="muted">暂时无法加载角色配置。
          <Button type="link" onClick={() => edit && void loadEditor(edit.roleId)}>重试</Button></p>
          : <Form form={form} layout="vertical" onFinish={save} requiredMark={false} disabled={saving || !editable}>
            {viewing && <p className="muted">系统内置角色由平台维护，以下为当前权限配置。</p>}
            {conflicted && <p className="muted">角色已更新，请重新加载后编辑。
              <Button type="link" onClick={() => edit && void loadEditor(edit.roleId)}>重新加载</Button></p>}
            <Form.Item name="roleName" label="角色名称" rules={[{ required: true, whitespace: true, message: '请输入角色名称' }]}>
              <Input maxLength={255} autoComplete="off" /></Form.Item>
            <Form.Item name="roleCode" label="角色编码" extra={viewing ? undefined : edit?.roleId ? '创建后不可修改。' : '使用大写字母、数字和下划线，以字母开头。'}
              rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,63}$/, message: '请输入有效角色编码' }]}>
              <Input maxLength={64} disabled={saving || !editable || !!edit?.roleId} autoComplete="off" /></Form.Item>
            <Form.Item label={viewing ? '角色权限' : '权限配置'}>
              {!viewing && <p className="role-permission-help">勾选目录会选择全部子项，勾选操作会自动保留上级访问权限。半选表示仅授权部分子项。</p>}
              {nodes.length > 0 ? <Tree<PermissionTreeItem> checkable checkStrictly selectable={false} defaultExpandAll
                disabled={saving || !editable || conflicted} treeData={treeData} checkedKeys={checkedKeys}
                onCheck={editable ? (_, info) => changePermission(info.node.key) : undefined} />
                : <p className="muted">暂无可配置权限。</p>}
              <p className="role-permission-count">{viewing ? '已授予' : '已选择'} {new Set(permissionIds).size} 项权限</p>
            </Form.Item>
            {!viewing && <Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }]}>
              <Input.TextArea rows={3} maxLength={500} showCount placeholder="说明本次角色或权限变更的原因" /></Form.Item>}
          </Form>}
    </Drawer>
  </>;
}
