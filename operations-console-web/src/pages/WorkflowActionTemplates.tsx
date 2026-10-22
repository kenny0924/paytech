import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Empty, Input, Space, Tabs, Tag } from 'antd';
import { useNavigate, useParams } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { configProblems, normalizeConfig } from '../workflow/templateModel';
import { ConfigEditor, CreateTemplateModal, DeactivateModal, ExecutorPreview, ReferencesView, actionTypes, useTemplateReferences } from '../workflow/TemplateEditors';
import { useWorkflowCommand } from '../workflow/useWorkflowCommand';
import { decodeMutation, decodePage, decodeTemplate, workflowPaths } from '../workflow/workflowApi';
import { actionTypeLabels, configStatusLabels } from '../workflow/workflowLabels';
import type { ActionConfig, ActionTemplate, ActionType } from '../workflow/workflowTypes';
import { Field, LoadFailed, Spinner, hasButton, personName, shortTime, useLoad, workflowButtons, workflowRoutes } from '../workflow/WorkflowParts';
import '../workflow/workflow.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const decodeTemplates = decodePage(decodeTemplate);

/** 动作目录：分类、列表、详情三栏；新建、按类型配置、引用与影响、停用确认（原型 12—18）。 */
export function WorkflowActionTemplates({ resources, onAuthorizationChanged }: Props) {
  const { templateId } = useParams(); const navigate = useNavigate();
  const canManage = hasButton(resources, workflowButtons.templateManage);
  const [items, setItems] = useState<ActionTemplate[]>([]); const [loading, setLoading] = useState(true); const [failure, setFailure] = useState<unknown>();
  const [reload, setReload] = useState(0);
  const [category, setCategory] = useState<ActionType | 'ALL'>('ALL'); const [keyword, setKeyword] = useState('');
  const [creating, setCreating] = useState(false);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setFailure(undefined);
    requestAllPages(workflowPaths.templates, controller.signal, undefined, {}, decodeTemplates)
      .then(value => { if (!controller.signal.aborted) setItems(value); })
      .catch(error => { if (!controller.signal.aborted) setFailure(error); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [reload]);
  const refresh = useCallback(() => setReload(value => value + 1), []);
  const visible = items.filter(item => (category === 'ALL' || item.actionType === category)
    && (!keyword.trim() || `${item.templateCode} ${item.templateName}`.toLowerCase().includes(keyword.trim().toLowerCase())));
  const open = (id: string) => void navigate(`${workflowRoutes.templates}/${id}`);
  return <div className="wf-page">
    <div className="wf-page-head"><h1>动作目录</h1><span className="muted">可复用的节点动作，流程发布时冻结当时的配置</span><span className="wf-grow" />
      {canManage && <Button type="primary" onClick={() => setCreating(true)}>＋ 新建动作</Button>}</div>
    {failure ? <LoadFailed failure={failure} retry={refresh} title="动作目录加载失败" /> : null}
    <div className="wf-catalog">
      <aside className="wf-card wf-categories">
        {(['ALL', ...actionTypes] as const).map(value => <button type="button" key={value} className={`wf-category${category === value ? ' is-active' : ''}`}
          onClick={() => setCategory(value)}><span>{value === 'ALL' ? '全部' : actionTypeLabels[value]}</span>
          <span className="muted">{value === 'ALL' ? items.length : items.filter(item => item.actionType === value).length}</span></button>)}
        <p className="muted">数据查询：本期只预留，以后由 data-service 执行其维护的查询。</p>
      </aside>
      <section className="wf-catalog-list">
        <Input.Search allowClear placeholder="搜索编码或名称" value={keyword} onChange={event => setKeyword(event.target.value)} />
        {loading ? <Spinner /> : !visible.length ? <Empty description="没有动作" /> : visible.map(item => <button type="button" key={item.templateId}
          className={`wf-card wf-template-item${item.templateId === templateId ? ' is-selected' : ''}`} onClick={() => open(item.templateId)}>
          <div><strong>{item.templateName}</strong> <Tag color={item.status === 'ACTIVE' ? 'green' : 'default'}>{configStatusLabels[item.status]}</Tag>
            {item.actionType === 'DATA_QUERY' && <Tag color="orange">预留</Tag>}</div>
          <div className="muted">{item.templateCode} · {actionTypeLabels[item.actionType]}{item.description ? ` · ${item.description}` : ''}</div></button>)}
      </section>
      <section className="wf-catalog-detail">
        {templateId ? <TemplateDetail key={templateId} templateId={templateId} canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} onChanged={refresh} />
          : <section className="wf-card"><Empty description="选择一个动作查看与编辑" /></section>}
      </section>
    </div>
    {creating && <CreateTemplateModal existingCodes={new Set(items.map(item => item.templateCode))} onClose={() => setCreating(false)}
      onAuthorizationChanged={onAuthorizationChanged} onCreated={id => { setCreating(false); refresh(); open(id); }} />}
  </div>;
}

interface Draft { templateName: string; description: string; config: ActionConfig }

function TemplateDetail({ templateId, canManage, onAuthorizationChanged, onChanged }: { templateId: string; canManage: boolean; onAuthorizationChanged: () => void; onChanged: () => void }) {
  const { message, modal } = App.useApp();
  const loaded = useLoad<ActionTemplate>(workflowPaths.template(templateId), decodeTemplate);
  const template = loaded.data;
  const [draft, setDraft] = useState<Draft | null>(null);
  const [tab, setTab] = useState('config');
  const [deactivating, setDeactivating] = useState(false);
  const command = useWorkflowCommand(onAuthorizationChanged);
  useEffect(() => { if (template) setDraft({ templateName: template.templateName, description: template.description ?? '', config: template.config ?? { fields: [] } }); }, [template]);
  const dirty = !!template && !!draft && JSON.stringify(draft) !== JSON.stringify({ templateName: template.templateName, description: template.description ?? '', config: template.config });
  const confirmLeave = useCallback(() => new Promise<boolean>(resolve => {
    modal.confirm({ title: '离开当前动作？', content: '尚未保存的修改将丢失。', okText: '离开', cancelText: '继续编辑', onOk: () => resolve(true), onCancel: () => resolve(false) });
  }), [modal]);
  useUnsavedRuleGuard(dirty, confirmLeave);
  const references = useTemplateReferences(template, tab === 'references' || deactivating);
  const problems = useMemo(() => template && draft ? configProblems(template.actionType, draft.config) : [], [template, draft]);
  if (loaded.loading && !template) return <Spinner />;
  if (!template || !draft) return <LoadFailed failure={loaded.failure} retry={loaded.refresh} title="动作加载失败" />;
  const readOnly = !canManage;
  async function save() {
    if (!template || !draft) return;
    if (!draft.templateName.trim()) { void message.warning('请填写名称'); return; }
    const result = await command.run(workflowPaths.template(template.templateId), 'PUT', { templateName: draft.templateName.trim(),
      ...(draft.description.trim() ? { description: draft.description.trim() } : {}), config: normalizeConfig(template.actionType, draft.config),
      expectedVersion: template.version }, decodeMutation, '保存失败');
    if (result) { void message.success('已保存；只影响之后新发布的流程版本'); loaded.refresh(); onChanged(); }
  }
  async function changeStatus(status: 'ACTIVE' | 'INACTIVE') {
    if (!template) return;
    const result = await command.run(workflowPaths.templateStatus(template.templateId), 'POST', { status, expectedVersion: template.version }, decodeMutation, '状态修改失败');
    if (result) { setDeactivating(false); void message.success(status === 'ACTIVE' ? '已启用' : '已停用'); loaded.refresh(); onChanged(); }
  }
  return <div className="wf-template-detail">
    <div className="wf-page-head"><h2>{template.templateName}</h2><Tag color="blue">{actionTypeLabels[template.actionType]} {template.actionType}</Tag>
      <Tag color={template.status === 'ACTIVE' ? 'green' : 'default'}>{configStatusLabels[template.status]}</Tag>
      {template.actionType === 'DATA_QUERY' && <Tag color="orange">预留</Tag>}
      <span className="muted">{template.templateCode} · 版本 {template.version} · {template.updatedByName ?? personName(template.updatedBy)} 于 {shortTime(template.updateTime)} 修改</span>
      <span className="wf-grow" />
      {dirty && <span className="wf-warn-text">有未保存的修改</span>}
      {canManage && <Space>
        {template.status === 'ACTIVE' ? <Button onClick={() => setDeactivating(true)}>停用</Button> : <Button onClick={() => void changeStatus('ACTIVE')}>启用</Button>}
        {dirty && <Button onClick={() => setDraft({ templateName: template.templateName, description: template.description ?? '', config: template.config ?? { fields: [] } })}>放弃修改</Button>}
        <Button type="primary" disabled={!dirty || problems.length > 0} title={problems[0]} loading={command.saving} onClick={() => void save()}>保存</Button></Space>}
    </div>
    {command.conflict && <Alert type="warning" showIcon title="动作已被他人修改，你的修改未保存" action={<Button size="small" onClick={() => { command.clearConflict(); loaded.refresh(); }}>重新加载</Button>} />}
    {command.formError && <Alert type="error" showIcon title={command.formError} />}
    {problems.length > 0 && canManage && <Alert type="warning" showIcon title={`配置还有 ${problems.length} 个问题：${problems.join('；')}`} />}
    <Tabs activeKey={tab} onChange={setTab} items={[
      { key: 'basic', label: '基本信息', children: <section className="wf-card">
        <Field label="编码"><code>{template.templateCode}</code></Field>
        <Field label="名称"><Input value={draft.templateName} maxLength={128} disabled={readOnly} onChange={event => setDraft({ ...draft, templateName: event.target.value })} /></Field>
        <Field label="描述"><Input.TextArea rows={3} maxLength={512} value={draft.description} disabled={readOnly} onChange={event => setDraft({ ...draft, description: event.target.value })} /></Field>
        <Field label="类型"><span>{actionTypeLabels[template.actionType]}（创建后不能修改）</span></Field></section> },
      { key: 'config', label: template.actionType === 'FORM' ? '字段设计' : template.actionType === 'DATA_QUERY' ? '查询与通过规则' : '规则与验证',
        children: <ConfigEditor type={template.actionType} config={draft.config} readOnly={readOnly}
          onChange={config => setDraft({ ...draft, config })} /> },
      { key: 'preview', label: '执行人预览', children: <div className="wf-preview-pane"><ExecutorPreview template={{ templateName: draft.templateName, actionType: template.actionType, config: draft.config }} />
        <p className="muted">预览使用未保存的配置；校验规则与提交时服务端校验一致。</p></div> },
      { key: 'references', label: `引用与影响${references.data ? `（${references.data.items.length}）` : ''}`, children: <ReferencesView template={template} state={references} /> },
    ]} />
    {deactivating && <DeactivateModal template={template} references={references} saving={command.saving} error={command.formError}
      onCancel={() => setDeactivating(false)} onConfirm={() => void changeStatus('INACTIVE')} />}
  </div>;
}
