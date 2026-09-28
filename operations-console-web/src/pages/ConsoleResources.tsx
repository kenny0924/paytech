import { useEffect, useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import { Alert, Button, Dropdown, Form, Input, InputNumber, Select, Spin, Tag, Tree, TreeSelect, Typography } from 'antd';
import type { DataNode } from 'antd/es/tree';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { PermissionSelect } from '../configuration/PermissionSelect';
import { ConfigurationStatusModal } from '../configuration/ConfigurationStatusModal';
import { useConfigurationForm } from '../configuration/useConfigurationForm';
import { descendants, editable, resourceTypeLabel, visibleResourceIds } from '../configuration/configurationModel';
import type { ManagedResource, ResourceCapabilities } from '../configuration/types';
import '../configuration/configuration.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
function ResourceIcon({ kind }: { kind: 'MENU' | 'BUTTON' | 'DIRECTORY' }) {
  return <svg viewBox="0 0 20 20" width="17" height="17" fill="none" stroke="currentColor" strokeWidth="1.4" aria-hidden="true">
    {kind === 'DIRECTORY' ? <path d="M2.5 6V4.5h5L9 6h8.5v10h-15z" />
      : kind === 'BUTTON' ? <><rect x="3" y="5" width="14" height="10" rx="3" /><path d="m8 8 3 2-3 2" /></>
        : <><rect x="4" y="2.5" width="12" height="15" rx="2" /><path d="M7 7h6M7 10h6M7 13h4" /></>}
  </svg>;
}

export function ConsoleResources({ resources: navigation, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const notify = useErrorNotification();
  const params = new URLSearchParams(location.search); const code = params.get('resource'); const mode = params.get('mode') || 'view';
  const canManage = navigation.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === 'OPERATIONS_CONSOLE_RESOURCE_MANAGE');
  const [data, setData] = useState<ManagedResource[]>([]); const [capabilities, setCapabilities] = useState<ResourceCapabilities>();
  const [loading, setLoading] = useState(true); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0);
  const [keyword, setKeyword] = useState(''); const [status, setStatus] = useState(''); const [expanded, setExpanded] = useState<React.Key[]>([]);
  const [changing, setChanging] = useState<ManagedResource | null>(null);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailed(false);
    Promise.all([session.request<{ items: ManagedResource[] }>('/console-admin/v1/resources', { signal: controller.signal }),
      session.request<ResourceCapabilities>('/console-admin/v1/resource-capabilities', { signal: controller.signal })])
      .then(([result, caps]) => { if (!controller.signal.aborted) {
        setData(result.items); setCapabilities(caps); setExpanded(previous => previous.length ? previous : result.items.filter(item => !item.parentResourceId).map(item => item.resourceId));
      } }).catch(error => { if (!controller.signal.aborted) { setFailed(true); notify(error, '菜单资源加载失败'); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [reload, notify]);
  const selected = data.find(resource => resource.resourceCode === code);
  function open(resource: ManagedResource, editableMode = canManage) {
    void navigate(`?${new URLSearchParams({ resource: resource.resourceCode, mode: editableMode ? 'edit' : 'view' })}`);
  }
  const visible = useMemo(() => visibleResourceIds(data, keyword, status), [data, keyword, status]);
  function nodes(parent: string | null, visited = new Set<string>()): DataNode[] {
    return data.filter(resource => resource.parentResourceId === parent && visible.has(resource.resourceId) && !visited.has(resource.resourceId))
      .toSorted((a, b) => a.sortOrder - b.sortOrder || (BigInt(a.resourceId) < BigInt(b.resourceId) ? -1 : 1)).map(resource => {
        const children = nodes(resource.resourceId, new Set([...visited, resource.resourceId]));
        const actionCount = data.filter(child => child.parentResourceId === resource.resourceId && child.resourceType === 'BUTTON').length;
        return { key: resource.resourceId, children, title: <div className="resource-node">
          <span className="resource-node-icon"><ResourceIcon kind={resource.resourceType === 'MENU' && !resource.componentKey ? 'DIRECTORY' : resource.resourceType} /></span>
          <span className="resource-node-label">
            <span className="resource-node-name" title={`${resource.displayName} · ${resource.resourceCode}`}>{resource.displayName}</span>
            {actionCount > 0 && <span className="resource-node-actions" role="img" aria-label={`${actionCount} 个按钮操作`} title={`${actionCount} 个按钮操作`}>
              <span>{actionCount}</span><ResourceIcon kind="BUTTON" />
            </span>}
          </span>
          {resource.status === 'ACTIVE' ? <span className="resource-status-dot" role="img" title="已启用" aria-label="已启用" /> : <Tag>停用</Tag>}
          <span onClick={event => event.stopPropagation()}><Dropdown trigger={['click']} menu={{ items: [
            { key: 'view', label: '详情', onClick: () => open(resource, false) },
            ...(canManage ? [{ key: 'edit', label: '编辑', onClick: () => open(resource) },
              { key: 'status', label: <span title={resource.managementProtection.reason || undefined}>{resource.status === 'ACTIVE' ? '停用' : '启用'}</span>,
                disabled: mode === 'edit' || mode === 'create' || !resource.managementProtection.statusChangeAllowed, onClick: () => setChanging(resource) }] : []),
          ] }}><Button type="text" size="small" aria-label={`${resource.displayName}的操作`}>···</Button></Dropdown></span>
        </div> };
      });
  }
  return <div className="configuration-page resource-management-page">
    <div className="page-header"><div><h1>菜单资源</h1><span className="muted">Operations · {data.length} 个资源</span></div>
      <div className="configuration-actions"><Button loading={loading} disabled={mode === 'edit' || mode === 'create'} onClick={() => setReload(v => v + 1)}>刷新</Button>
        {canManage && <Button type="primary" onClick={() => void navigate('?mode=create')}>＋ 新增资源</Button>}</div></div>
    {failed && <Alert type="warning" showIcon title="菜单资源暂时无法加载" action={<Button onClick={() => setReload(v => v + 1)}>重试</Button>} />}
    <div className="resource-workspace">
      <section className="resource-tree-panel">
        <div className="resource-tree-heading"><strong>资源结构</strong><Button type="link" size="small" onClick={() => setExpanded(expanded.length ? [] : data.map(r => r.resourceId))}>{expanded.length ? '收起全部' : '展开全部'}</Button></div>
        <Input.Search placeholder="搜索名称或编码" allowClear value={keyword} onChange={event => setKeyword(event.target.value)} />
        <Select aria-label="资源状态" className="resource-status-filter" value={status} onChange={setStatus}
          options={[{ value: '', label: '全部状态' }, { value: 'ACTIVE', label: '启用' }, { value: 'DISABLED', label: '停用' }]} />
        {loading && !data.length ? <Spin /> : <Tree blockNode showLine={{ showLeafIcon: false }} treeData={nodes(null)}
          expandedKeys={keyword || status ? [...visible] : expanded} onExpand={setExpanded}
          selectedKeys={selected ? [selected.resourceId] : []} onSelect={keys => { const item = data.find(r => r.resourceId === keys[0]); if (item) open(item); }} />}
      </section>
      {capabilities && (selected || mode === 'create') ? <ResourceEditor key={`${mode}:${code}:${reload}:${selected?.version}`} resource={selected}
        creating={mode === 'create'} readOnly={mode !== 'create' && mode !== 'edit' || !canManage} data={data} capabilities={capabilities}
        parentHint={params.get('parent')} onAuthorizationChanged={onAuthorizationChanged}
        onEdit={() => selected && open(selected)} canManage={canManage}
        onSaved={savedCode => { setReload(v => v + 1); onAuthorizationChanged(); void navigate(`?${new URLSearchParams({ resource: savedCode, mode: 'view' })}`, { replace: true }); }} />
        : <section className="configuration-editor resource-empty"><ResourceIcon kind="DIRECTORY" /><h2>{code ? '资源不存在或已不可访问' : '选择一个资源'}</h2><p className="muted">在左侧查看目录、页面菜单和按钮。</p></section>}
    </div>
    {changing && <ConfigurationStatusModal kind="resource" code={changing.resourceCode} version={changing.version} enabled={changing.status === 'ACTIVE'}
      onClose={() => setChanging(null)} onSaved={() => { setChanging(null); setReload(v => v + 1); onAuthorizationChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function ResourceEditor({ resource, creating, readOnly, data, capabilities, parentHint, onSaved, onEdit, canManage, onAuthorizationChanged }: {
  resource?: ManagedResource; creating: boolean; readOnly: boolean; data: ManagedResource[]; capabilities: ResourceCapabilities; parentHint: string | null;
  onSaved: (code: string) => void; onEdit: () => void; canManage: boolean; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const [kind, setKind] = useState<'DIRECTORY' | 'PAGE' | 'BUTTON'>(resource?.resourceType === 'BUTTON' ? 'BUTTON' : resource?.componentKey ? 'PAGE' : 'DIRECTORY');
  const parentId = Form.useWatch('parentResourceId', form); const componentKey = Form.useWatch('componentKey', form);
  const blocked = resource ? descendants(data, resource.resourceId) : new Set<string>();
  const parent = data.find(item => item.resourceId === parentId);
  const parentComponent = capabilities.components.find(component => component.componentKey === parent?.componentKey);
  const actions = parentComponent?.actions || [];
  const alreadyUsed = new Set(data.filter(item => item.resourceId !== resource?.resourceId).map(item => item.resourceCode));
  const canEditField = (field: string) => !readOnly && editable(resource?.managementProtection, field, creating) && !editor.saving && !editor.conflict;
  const parentOptions = data.filter(item => item.resourceType === 'MENU' && !blocked.has(item.resourceId)
    && (kind === 'BUTTON' ? !!item.componentKey : !item.componentKey));
  const parentTree = parentOptions.map(item => ({ id: item.resourceId, pId: parentOptions.some(p => p.resourceId === item.parentResourceId) ? item.parentResourceId : undefined,
    value: item.resourceId, title: item.displayName }));
  const selectedComponent = capabilities.components.find(item => item.componentKey === componentKey);
  async function save(values: Record<string, unknown>) {
    if (readOnly) return;
    const payload = { displayName: String(values.displayName).trim(), parentResourceId: values.parentResourceId || null,
      componentKey: kind === 'PAGE' ? values.componentKey : null, permissionId: values.permissionId, sortOrder: values.sortOrder,
      reason: String(values.reason).trim(), ...(creating ? { resourceCode: values.resourceCode, resourceType: kind === 'BUTTON' ? 'BUTTON' : 'MENU' } : { expectedVersion: resource!.version }) };
    const result = await editor.save(`/console-admin/v1/resources${creating ? '' : `/${encodeURIComponent(resource!.resourceCode)}`}`, creating ? 'POST' : 'PUT', payload);
    if (result) { flushSync(() => editor.setDirty(false)); onSaved(result.objectCode); }
  }
  return <section className="configuration-editor">
    <header className="configuration-editor-heading"><div><h2>{creating ? '新增资源' : resource?.displayName}
      {!creating && <Tag>{resourceTypeLabel(resource!)}</Tag>}</h2>
      {!creating && <Typography.Text className="configuration-code" copyable>{resource?.resourceCode}</Typography.Text>}</div>
      <div className="configuration-actions">{resource && <Tag color={resource.status === 'ACTIVE' ? 'green' : 'default'}>{resource.status === 'ACTIVE' ? '启用' : '停用'}</Tag>}
        {readOnly && canManage && <Button onClick={onEdit}>编辑</Button>}</div></header>
    {resource && !resource.supported && <Alert type="warning" showIcon title="当前组件或按钮不在已部署能力中，请选择支持的配置后再启用。" />}
    {resource?.managementProtection.reason && <div className="configuration-protection">{resource.managementProtection.reason}</div>}
    {editor.conflict && <Alert type="warning" showIcon title="配置已更新，草稿已保留。请重新打开详情核对最新版本后修改。" />}
    <Form form={form} layout="vertical" requiredMark={false} className="configuration-form" onFinish={save} onValuesChange={() => editor.setDirty(true)}
      initialValues={{ ...resource, parentResourceId: resource?.parentResourceId || parentHint || undefined, sortOrder: resource?.sortOrder ?? 0, reason: '' }}>
      {creating && <div className="configuration-grid two"><Form.Item label="资源类型"><Select value={kind} disabled={editor.saving} onChange={value => { setKind(value); form.setFieldsValue({ parentResourceId: undefined, componentKey: undefined, resourceCode: undefined }); editor.setDirty(true); }}
        options={[{ value: 'DIRECTORY', label: '目录' }, { value: 'PAGE', label: '页面菜单' }, { value: 'BUTTON', label: '按钮' }]} /></Form.Item>
        <Form.Item name="resourceCode" label="资源编码" rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,127}$/, message: '请输入大写字母、数字或下划线编码' }]}>
          {kind === 'BUTTON' ? <Select disabled={editor.saving} placeholder="先选择所属页面" options={actions.map(action => ({ value: action.resourceCode, label: `${action.displayName} · ${action.resourceCode}`, disabled: alreadyUsed.has(action.resourceCode) }))} />
            : <Input disabled={editor.saving} maxLength={128} placeholder="例如 OPERATIONS_REPORTS" />}</Form.Item></div>}
      <h3 className="configuration-section-heading">基本信息</h3>
      <div className="configuration-grid resource-basics">
        <Form.Item name="displayName" label="资源名称" rules={[{ required: true, whitespace: true, message: '请填写资源名称' }]}><Input maxLength={255} disabled={!canEditField('displayName')} /></Form.Item>
        <Form.Item name="parentResourceId" label={kind === 'BUTTON' ? '所属页面' : '父级资源'} rules={kind === 'BUTTON' ? [{ required: true, message: '请选择页面' }] : []}>
          <TreeSelect treeDataSimpleMode treeData={parentTree} allowClear={kind !== 'BUTTON'} disabled={!canEditField('parentResourceId')}
            placeholder={kind === 'BUTTON' ? '请选择页面菜单' : '根目录'} showSearch treeNodeFilterProp="title" /></Form.Item>
        <Form.Item name="sortOrder" label="排序" rules={[{ required: true }]}><InputNumber min={0} max={999999} precision={0} disabled={!canEditField('sortOrder')} /></Form.Item>
      </div>
      <h3 className="configuration-section-heading">页面与权限</h3>
      <div className="configuration-grid two">
        {kind !== 'BUTTON' && <Form.Item name="componentKey" label="页面组件" rules={kind === 'PAGE' ? [{ required: true, message: '请选择已部署组件' }] : []}>
          <Select allowClear disabled={!canEditField('componentKey')} placeholder="不选择组件时作为目录" options={capabilities.components.map(c => ({ value: c.componentKey, label: c.displayName }))}
            onChange={value => { setKind(value ? 'PAGE' : 'DIRECTORY'); }} /></Form.Item>}
        <Form.Item name="permissionId" label="可见性权限" rules={[{ required: true, message: '请选择权限' }]}><PermissionSelect usage="CONSOLE_RESOURCE"
          currentLabel={resource ? `${resource.permissionCode}${resource.permissionStatus === 'INACTIVE' ? '（已停用）' : ''}` : undefined} disabled={!canEditField('permissionId')} /></Form.Item>
      </div>
      {kind !== 'BUTTON' && <div className="configuration-readonly-field"><span>前端路径</span><code>{selectedComponent?.frontendRoute || resource?.frontendRoute || '—'}</code></div>}
      {!readOnly && <Form.Item name="reason" label="变更原因" rules={[{ required: true, whitespace: true, message: '请填写变更原因' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} placeholder="说明本次调整的原因" /></Form.Item>}
      {!readOnly && <footer className="configuration-save-bar"><span className="muted">{editor.dirty ? '有未保存的修改' : creating ? '新资源保存后为停用状态' : `当前版本 ${resource?.version}`}</span>
        <Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict || !canManage}>保存配置</Button></footer>}
    </Form>
  </section>;
}
