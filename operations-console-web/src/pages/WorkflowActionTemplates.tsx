import { useCallback, useEffect, useState } from 'react';
import { App, Button, Drawer, Dropdown, Empty, Input, Table } from 'antd';
import { EllipsisOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { ActionTemplateEditor } from '../workflow/ActionTemplateEditor';
import { catalogTabs, currentReferences, filterTemplates, newTemplateType, referenceText } from '../workflow/catalogModel';
import type { CatalogTab } from '../workflow/catalogModel';
import { ActionTypePicker, ActionTypeTag, DeactivateModal, ExecutorPreview, useTemplateReferences } from '../workflow/TemplateEditors';
import { useWorkflowCommand } from '../workflow/useWorkflowCommand';
import { decodeMutation, decodePage, decodeTemplate, workflowPaths } from '../workflow/workflowApi';
import { actionTypeLabels, configStatusLabels } from '../workflow/workflowLabels';
import type { ActionTemplate } from '../workflow/workflowTypes';
import { LoadFailed, Spinner, WorkflowFrame, hasButton, personName, shortTime, useLoad, workflowButtons, workflowRoutes } from '../workflow/WorkflowParts';
import '../workflow/workflow.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const decodeTemplates = decodePage(decodeTemplate);

/**
 * 动作目录（原型 06—08）：/workflow/action-templates 为列表 + 只读预览抽屉；
 * /:templateId 为编辑页，new?type= 为新建（先在列表选类型）。
 */
export function WorkflowActionTemplates({ resources, onAuthorizationChanged }: Props) {
  const { templateId } = useParams(); const [search] = useSearchParams(); const navigate = useNavigate();
  const canManage = hasButton(resources, workflowButtons.templateManage);
  const newType = templateId === 'new' ? newTemplateType(search.get('type')) : null;
  useEffect(() => {
    if (templateId === 'new' && !newType) void navigate(workflowRoutes.templates, { replace: true });
  }, [templateId, newType, navigate]);
  const onCreated = useCallback((template: ActionTemplate) => void navigate(`${workflowRoutes.templates}/${template.templateId}`, { replace: true }), [navigate]);
  if (templateId === 'new') return newType ? <WorkflowFrame><ActionTemplateEditor key={`new:${newType}`} mode="page" actionType={newType} canManage={canManage}
    onAuthorizationChanged={onAuthorizationChanged} onCreated={onCreated} /></WorkflowFrame> : null;
  if (templateId) return <WorkflowFrame><ActionTemplateEditor key={templateId} mode="page" templateId={templateId} canManage={canManage}
    onAuthorizationChanged={onAuthorizationChanged} /></WorkflowFrame>;
  return <WorkflowFrame><TemplateCatalog canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} /></WorkflowFrame>;
}

function TemplateCatalog({ canManage, onAuthorizationChanged }: { canManage: boolean; onAuthorizationChanged: () => void }) {
  const navigate = useNavigate();
  const [items, setItems] = useState<ActionTemplate[]>([]); const [loading, setLoading] = useState(true); const [failure, setFailure] = useState<unknown>();
  const [reload, setReload] = useState(0);
  const [tab, setTab] = useState<CatalogTab>('ALL'); const [keyword, setKeyword] = useState('');
  const [picking, setPicking] = useState(false);
  const [previewId, setPreviewId] = useState<string | null>(null);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailure(undefined);
    requestAllPages(workflowPaths.templates, controller.signal, undefined, {}, decodeTemplates)
      .then(value => { if (!controller.signal.aborted) setItems(value); })
      .catch(error => { if (!controller.signal.aborted) setFailure(error); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [reload]);
  const refresh = useCallback(() => setReload(value => value + 1), []);
  const tabs = catalogTabs(items);
  const activeTab = tabs.some(item => item.key === tab) ? tab : 'ALL';
  const visible = filterTemplates(items, activeTab, keyword);
  const preview = items.find(item => item.templateId === previewId);
  return <div className="wf-page">
    <div className="wf-page-head"><h1>动作目录</h1><span className="wf-grow" />
      <Input allowClear prefix={<SearchOutlined className="muted" />} placeholder="搜索动作名称、编码" style={{ width: 260 }} value={keyword} onChange={event => setKeyword(event.target.value)} />
      {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setPicking(true)}>新建动作</Button>}</div>
    {failure ? <LoadFailed failure={failure} retry={refresh} title="动作目录加载失败" /> : null}
    <section className="wf-catalog-panel">
      <div className="wf-tabs wf-catalog-tabs" role="tablist">{tabs.map(item => <button type="button" role="tab" aria-selected={activeTab === item.key} key={item.key}
        className={`wf-tab${activeTab === item.key ? ' is-active' : ''}`} onClick={() => setTab(item.key)}>
        {item.key === 'ALL' ? '全部' : actionTypeLabels[item.key]}<span className="wf-tab-count">{item.count}</span></button>)}</div>
      {loading && !items.length ? <Spinner /> : <Table<ActionTemplate> rowKey="templateId" size="middle" dataSource={visible} className="wf-catalog-table"
        pagination={visible.length > 50 ? { pageSize: 50, showSizeChanger: false } : false}
        locale={{ emptyText: <Empty description={keyword.trim() ? '没有匹配的动作' : '还没有动作'} /> }}
        rowClassName={row => `${row.templateId === previewId ? 'wf-row-selected' : ''}${row.status === 'INACTIVE' ? ' is-inactive' : ''}`}
        onRow={row => ({ onClick: () => setPreviewId(row.templateId), style: { cursor: 'pointer' } })} columns={[
          { title: '名称', render: (_, row) => <div className="wf-catalog-name"><strong>{row.templateName}</strong>
            {row.description && <div className="muted wf-ellipsis">{row.description}</div>}</div> },
          { title: '类型', width: 130, render: (_, row) => <ActionTypeTag type={row.actionType} /> },
          { title: '被引用', width: 110, render: (_, row) => <span className={row.referencedDefinitionCount ? '' : 'muted'}>{referenceText(row.referencedDefinitionCount)}</span> },
          { title: '状态', width: 90, render: (_, row) => <span className="wf-inline"><span className={`wf-status-dot is-${row.status.toLowerCase()}`} />{configStatusLabels[row.status]}</span> },
          { title: '最近修改', width: 190, render: (_, row) => <span>{row.updatedByName ?? personName(row.updatedBy)} <span className="muted">{shortTime(row.updateTime)}</span></span> },
        ]} />}
    </section>
    {preview && <TemplatePreviewDrawer key={preview.templateId} summary={preview} canManage={canManage} onAuthorizationChanged={onAuthorizationChanged}
      onClose={() => setPreviewId(null)} onChanged={refresh} onEdit={() => void navigate(`${workflowRoutes.templates}/${preview.templateId}`)} />}
    {picking && <ActionTypePicker onCancel={() => setPicking(false)} onNext={type => void navigate(`${workflowRoutes.templates}/new?type=${type}`)} />}
  </div>;
}

/** 只读预览抽屉（原型 06）：执行人看到的样子 + 被这些流程引用；底部「编辑」与「⋯」中的停用 / 启用。 */
function TemplatePreviewDrawer({ summary, canManage, onAuthorizationChanged, onClose, onChanged, onEdit }: {
  summary: ActionTemplate; canManage: boolean; onAuthorizationChanged: () => void; onClose: () => void; onChanged: () => void; onEdit: () => void;
}) {
  const { message } = App.useApp();
  const loaded = useLoad<ActionTemplate>(workflowPaths.template(summary.templateId), decodeTemplate);
  const template = loaded.data;
  const references = useTemplateReferences(template, true);
  const command = useWorkflowCommand(onAuthorizationChanged);
  const [deactivating, setDeactivating] = useState(false);
  const grouped = currentReferences(references.data);
  async function changeStatus(status: 'ACTIVE' | 'INACTIVE') {
    if (!template) return;
    const result = await command.run(workflowPaths.templateStatus(template.templateId), 'POST', { status, expectedVersion: template.version }, decodeMutation, '状态修改失败');
    if (!result) return;
    setDeactivating(false); void message.success(status === 'ACTIVE' ? '已启用' : '已停用'); loaded.refresh(); onChanged();
  }
  const shown = template ?? summary;
  return <Drawer open size={520} onClose={onClose} className="wf-preview-drawer" title={<div className="wf-drawer-title">
    <div className="wf-inline"><strong>{shown.templateName}</strong><ActionTypeTag type={shown.actionType} /></div>
    <div className="muted wf-drawer-sub">{shown.description ? `${shown.description} · ` : ''}{shown.updatedByName ?? personName(shown.updatedBy)} 修改于 {shortTime(shown.updateTime)}</div></div>}
    footer={<div className="wf-drawer-foot">
      {canManage ? <Dropdown trigger={['click']} menu={{ items: [shown.status === 'ACTIVE' ? { key: 'off', label: '停用', danger: true } : { key: 'on', label: '启用' }],
        onClick: ({ key }) => key === 'off' ? setDeactivating(true) : void changeStatus('ACTIVE') }}>
        <Button aria-label="更多操作" loading={command.saving} icon={<EllipsisOutlined />} /></Dropdown> : <span />}
      <Button type="primary" onClick={onEdit}>{canManage ? '编辑' : '查看详情'}</Button></div>}>
    {!template ? (loaded.failure ? <LoadFailed failure={loaded.failure} retry={loaded.refresh} title="动作加载失败" /> : <Spinner />) : <>
      <div className="wf-section-title">执行人看到的样子<span className="muted">只读预览</span></div>
      <ExecutorPreview template={template} />
      <div className="wf-section-title">被这些流程引用<span className="muted">{references.data ? grouped.current.length : ''}</span></div>
      {references.loading && !references.data ? <Spinner /> : references.failure ? <LoadFailed failure={references.failure} title="引用查询失败" />
        : !grouped.current.length ? <div className="muted">还没有已发布的流程引用这个动作{grouped.historical ? `（${grouped.historical} 个历史版本曾引用）` : ''}</div>
        : <ul className="wf-reference-list">{grouped.current.map(item => <li key={item.definitionId}>
          <Link to={`${workflowRoutes.definitions}/${item.definitionId}/versions/${item.versionNo}`}><strong>{item.definitionName}</strong></Link>
          <span className="wf-grow" />{item.runningInstanceCount > 0 && <span className="muted">{item.runningInstanceCount} 个运行中</span>}
          <span className={item.sameAsCurrent ? 'wf-ok-text' : 'wf-warn-text'}>已发布 v{item.versionNo}{item.sameAsCurrent ? '' : ' · 旧配置'}</span></li>)}
          {grouped.historical > 0 && <li className="muted">另有 {grouped.historical} 个历史版本引用（不计入）</li>}</ul>}
    </>}
    {command.formError && !deactivating && <div className="wf-danger-text">{command.formError}</div>}
    {deactivating && template && <DeactivateModal template={{ ...template, referencedDefinitionCount: summary.referencedDefinitionCount }} references={references}
      saving={command.saving} error={command.formError} onCancel={() => setDeactivating(false)} onConfirm={() => void changeStatus('INACTIVE')} />}
  </Drawer>;
}
