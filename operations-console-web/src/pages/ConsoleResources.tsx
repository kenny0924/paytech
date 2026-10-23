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
import { descendants, editable, resourceKind, resourceKinds, resourceTypeLabel, visibleResourceIds } from '../configuration/configurationModel';
import type { ResourceKind } from '../configuration/configurationModel';
import type { ManagedResource, ResourceCapabilities } from '../configuration/types';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
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
  const t = useT('gateway'), tCommon = useT('common');
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
      } }).catch(error => { if (!controller.signal.aborted) { setFailed(true); notify(error, t('resources.loadFailed')); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
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
            {actionCount > 0 && <span className="resource-node-actions" role="img" aria-label={t('resources.buttonCount', { count: actionCount })} title={t('resources.buttonCount', { count: actionCount })}>
              <span>{actionCount}</span><ResourceIcon kind="BUTTON" />
            </span>}
          </span>
          {resource.status === 'ACTIVE' ? <span className="resource-status-dot" role="img" title={t('resources.enabled')} aria-label={t('resources.enabled')} /> : <Tag>{enumLabel(t, 'enum.resourceStatus', 'DISABLED')}</Tag>}
          <span onClick={event => event.stopPropagation()}><Dropdown trigger={['click']} menu={{ items: [
            { key: 'view', label: t('resources.action.details'), onClick: () => open(resource, false) },
            ...(canManage ? [{ key: 'edit', label: t('resources.action.edit'), onClick: () => open(resource) },
              { key: 'status', label: <span title={resource.managementProtection.reason || undefined}>{resource.status === 'ACTIVE' ? t('resources.action.disable') : t('resources.action.enable')}</span>,
                disabled: mode === 'edit' || mode === 'create' || !resource.managementProtection.statusChangeAllowed, onClick: () => setChanging(resource) }] : []),
          ] }}><Button type="text" size="small" aria-label={t('resources.nodeActions', { name: resource.displayName })}>···</Button></Dropdown></span>
        </div> };
      });
  }
  return <div className="configuration-page resource-management-page">
    <div className="page-header"><div><h1>{t('resources.title')}</h1><span className="muted">{t('resources.count', { count: data.length })}</span></div>
      <div className="configuration-actions"><Button loading={loading} disabled={mode === 'edit' || mode === 'create'} onClick={() => setReload(v => v + 1)}>{tCommon('action.refresh')}</Button>
        {canManage && <Button type="primary" onClick={() => void navigate('?mode=create')}>{t('resources.create')}</Button>}</div></div>
    {failed && <Alert type="warning" showIcon title={t('resources.unavailable')} action={<Button onClick={() => setReload(v => v + 1)}>{tCommon('action.retry')}</Button>} />}
    <div className="resource-workspace">
      <section className="resource-tree-panel">
        <div className="resource-tree-heading"><strong>{t('resources.structure')}</strong><Button type="link" size="small" onClick={() => setExpanded(expanded.length ? [] : data.map(r => r.resourceId))}>{expanded.length ? t('resources.collapseAll') : t('resources.expandAll')}</Button></div>
        <Input.Search placeholder={t('resources.searchPlaceholder')} allowClear value={keyword} onChange={event => setKeyword(event.target.value)} />
        <Select aria-label={t('resources.statusFilter')} className="resource-status-filter" value={status} onChange={setStatus}
          options={[{ value: '', label: t('resources.allStatuses') }, { value: 'ACTIVE', label: enumLabel(t, 'enum.resourceStatus', 'ACTIVE') }, { value: 'DISABLED', label: enumLabel(t, 'enum.resourceStatus', 'DISABLED') }]} />
        {loading && !data.length ? <Spin /> : <Tree blockNode showLine={{ showLeafIcon: false }} treeData={nodes(null)}
          expandedKeys={keyword || status ? [...visible] : expanded} onExpand={setExpanded}
          selectedKeys={selected ? [selected.resourceId] : []} onSelect={keys => { const item = data.find(r => r.resourceId === keys[0]); if (item) open(item); }} />}
      </section>
      {capabilities && (selected || mode === 'create') ? <ResourceEditor key={`${mode}:${code}:${reload}:${selected?.version}`} resource={selected}
        creating={mode === 'create'} readOnly={mode !== 'create' && mode !== 'edit' || !canManage} data={data} capabilities={capabilities}
        parentHint={params.get('parent')} onAuthorizationChanged={onAuthorizationChanged}
        onEdit={() => selected && open(selected)} canManage={canManage}
        onSaved={savedCode => { setReload(v => v + 1); onAuthorizationChanged(); void navigate(`?${new URLSearchParams({ resource: savedCode, mode: 'view' })}`, { replace: true }); }} />
        : <section className="configuration-editor resource-empty"><ResourceIcon kind="DIRECTORY" /><h2>{code ? t('resources.notFound') : t('resources.selectOne')}</h2><p className="muted">{t('resources.selectHint')}</p></section>}
    </div>
    {changing && <ConfigurationStatusModal kind="resource" code={changing.resourceCode} version={changing.version} enabled={changing.status === 'ACTIVE'}
      onClose={() => setChanging(null)} onSaved={() => { setChanging(null); setReload(v => v + 1); onAuthorizationChanged(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function ResourceEditor({ resource, creating, readOnly, data, capabilities, parentHint, onSaved, onEdit, canManage, onAuthorizationChanged }: {
  resource?: ManagedResource; creating: boolean; readOnly: boolean; data: ManagedResource[]; capabilities: ResourceCapabilities; parentHint: string | null;
  onSaved: (code: string) => void; onEdit: () => void; canManage: boolean; onAuthorizationChanged: () => void;
}) {
  const t = useT('gateway');
  const [form] = Form.useForm(); const editor = useConfigurationForm(onAuthorizationChanged, form);
  const [kind, setKind] = useState<ResourceKind>(resource ? resourceKind(resource) : 'DIRECTORY');
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
    <header className="configuration-editor-heading"><div><h2>{creating ? t('resources.editor.createTitle') : resource?.displayName}
      {!creating && <Tag>{resourceTypeLabel(resource!)}</Tag>}</h2>
      {!creating && <Typography.Text className="configuration-code" copyable>{resource?.resourceCode}</Typography.Text>}</div>
      <div className="configuration-actions">{resource && <Tag color={resource.status === 'ACTIVE' ? 'green' : 'default'}>{enumLabel(t, 'enum.resourceStatus', resource.status === 'ACTIVE' ? 'ACTIVE' : 'DISABLED')}</Tag>}
        {readOnly && canManage && <Button onClick={onEdit}>{t('resources.action.edit')}</Button>}</div></header>
    {resource && !resource.supported && <Alert type="warning" showIcon title={t('resources.editor.unsupported')} />}
    {resource?.managementProtection.reason && <div className="configuration-protection">{resource.managementProtection.reason}</div>}
    {editor.conflict && <Alert type="warning" showIcon title={t('resources.editor.conflict')} />}
    <Form form={form} layout="vertical" requiredMark={false} className="configuration-form" onFinish={save} onValuesChange={() => editor.setDirty(true)}
      initialValues={{ ...resource, parentResourceId: resource?.parentResourceId || parentHint || undefined, sortOrder: resource?.sortOrder ?? 0, reason: '' }}>
      {creating && <div className="configuration-grid two"><Form.Item label={t('resources.editor.resourceType')}><Select value={kind} disabled={editor.saving} onChange={value => { setKind(value); form.setFieldsValue({ parentResourceId: undefined, componentKey: undefined, resourceCode: undefined }); editor.setDirty(true); }}
        options={resourceKinds.map(value => ({ value, label: t('enum.resourceKind.' + value) }))} /></Form.Item>
        <Form.Item name="resourceCode" label={t('resources.editor.resourceCode')} rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{0,127}$/, message: t('resources.editor.codeInvalid') }]}>
          {kind === 'BUTTON' ? <Select disabled={editor.saving} placeholder={t('resources.editor.selectPageFirst')} options={actions.map(action => ({ value: action.resourceCode, label: t('resources.editor.actionOption', { name: action.displayName, code: action.resourceCode }), disabled: alreadyUsed.has(action.resourceCode) }))} />
            : <Input disabled={editor.saving} maxLength={128} placeholder={t('resources.editor.codePlaceholder')} />}</Form.Item></div>}
      <h3 className="configuration-section-heading">{t('resources.editor.basics')}</h3>
      <div className="configuration-grid resource-basics">
        <Form.Item name="displayName" label={t('resources.editor.displayName')} rules={[{ required: true, whitespace: true, message: t('resources.editor.displayNameRequired') }]}><Input maxLength={255} disabled={!canEditField('displayName')} /></Form.Item>
        <Form.Item name="parentResourceId" label={kind === 'BUTTON' ? t('resources.editor.parentPage') : t('resources.editor.parentResource')} rules={kind === 'BUTTON' ? [{ required: true, message: t('resources.editor.parentPageRequired') }] : []}>
          <TreeSelect treeDataSimpleMode treeData={parentTree} allowClear={kind !== 'BUTTON'} disabled={!canEditField('parentResourceId')}
            placeholder={kind === 'BUTTON' ? t('resources.editor.parentPagePlaceholder') : t('resources.editor.root')} showSearch treeNodeFilterProp="title" /></Form.Item>
        <Form.Item name="sortOrder" label={t('resources.editor.sortOrder')} rules={[{ required: true }]}><InputNumber min={0} max={999999} precision={0} disabled={!canEditField('sortOrder')} /></Form.Item>
      </div>
      <h3 className="configuration-section-heading">{t('resources.editor.pageAndPermission')}</h3>
      <div className="configuration-grid two">
        {kind !== 'BUTTON' && <Form.Item name="componentKey" label={t('resources.editor.component')} rules={kind === 'PAGE' ? [{ required: true, message: t('resources.editor.componentRequired') }] : []}>
          <Select allowClear disabled={!canEditField('componentKey')} placeholder={t('resources.editor.componentPlaceholder')} options={capabilities.components.map(c => ({ value: c.componentKey, label: c.displayName }))}
            onChange={value => { setKind(value ? 'PAGE' : 'DIRECTORY'); }} /></Form.Item>}
        <Form.Item name="permissionId" label={t('resources.editor.visibilityPermission')} rules={[{ required: true, message: t('resources.editor.permissionRequired') }]}><PermissionSelect usage="CONSOLE_RESOURCE"
          currentLabel={resource ? (resource.permissionStatus === 'INACTIVE' ? t('config.permissionDisabled', { code: resource.permissionCode }) : resource.permissionCode) : undefined} disabled={!canEditField('permissionId')} /></Form.Item>
      </div>
      {kind !== 'BUTTON' && <div className="configuration-readonly-field"><span>{t('resources.editor.frontendRoute')}</span><code>{selectedComponent?.frontendRoute || resource?.frontendRoute || '—'}</code></div>}
      {!readOnly && <Form.Item name="reason" label={t('config.reason')} rules={[{ required: true, whitespace: true, message: t('config.reasonRequired') }]}>
        <Input.TextArea rows={3} maxLength={500} showCount disabled={editor.saving || editor.conflict} placeholder={t('config.reasonPlaceholder')} /></Form.Item>}
      {!readOnly && <footer className="configuration-save-bar"><span className="muted">{editor.dirty ? t('config.unsaved') : creating ? t('resources.editor.createdDisabled') : t('config.currentVersion', { version: resource?.version ?? '' })}</span>
        <Button type="primary" htmlType="submit" loading={editor.saving} disabled={editor.conflict || !canManage}>{t('config.save')}</Button></footer>}
    </Form>
  </section>;
}
