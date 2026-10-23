import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Drawer, Input, Tag } from 'antd';
import { CloseOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import { session } from '../api/session';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { currentReferences, draftOf, editorSteps, isDirty, needsLeaveConfirm, suggestTemplateCode } from './catalogModel';
import type { EditorStep, TemplateDraft } from './catalogModel';
import { configProblems, normalizeConfig, templateCodePattern } from './templateModel';
import { ActionTypeTag, ConfigEditor, DeactivateModal, ExecutorPreview, ReferencesView, useTemplateReferences } from './TemplateEditors';
import { useWorkflowCommand } from './useWorkflowCommand';
import { decodeMutation, decodePage, decodeTemplate, workflowPaths } from './workflowApi';
import { actionTypeLabels, configStatusLabels } from './workflowLabels';
import type { ActionTemplate, ActionType, FormDefinition } from './workflowTypes';
import { Field, LoadFailed, Spinner, personName, shortTime, useLoad, workflowRoutes } from './WorkflowParts';

const decodeTemplates = decodePage(decodeTemplate);

export interface ActionTemplateEditorProps {
  /** 页面（/workflow/action-templates/:templateId）或抽屉（流程编辑器里就地新建，S3）。 */
  mode: 'page' | 'drawer';
  /** 已有动作 ID；新建时省略，并给出 actionType。 */
  templateId?: string;
  /** 新建动作的类型（先由 ActionTypePicker 选择；数据查询不可新建）。 */
  actionType?: ActionType;
  /** 持有「维护动作」按钮权限；没有时只读。 */
  canManage: boolean;
  onAuthorizationChanged: () => void;
  /** 新建成功后回调完整动作（含配置）；页面形态由调用方跳到该动作的编辑页，抽屉形态由调用方选中并关闭抽屉。 */
  onCreated?: (template: ActionTemplate) => void;
  /** 已有动作保存或启停成功后回调最新内容。 */
  onSaved?: (template: ActionTemplate) => void;
  /** 抽屉形态的关闭；有未保存修改时先确认。 */
  onClose?: () => void;
  /** 未保存状态变化（抽屉宿主可据此提示）。 */
  onDirtyChange?: (dirty: boolean) => void;
}

/** 页面形态的离开确认（路由切换与关闭 / 刷新浏览器）；抽屉形态不注册，避免与宿主页面的离开确认冲突。 */
function LeaveGuard({ dirty, confirm }: { dirty: boolean; confirm: () => Promise<boolean> }) {
  useUnsavedRuleGuard(dirty, confirm);
  return null;
}

/**
 * 动作编辑器（原型 08，需求 C2—C4）：左侧步骤「基本信息 / 字段设计或说明 / 引用 N」，页头「未保存的修改」、放弃修改与保存，
 * 页脚版本与修改人。新建与修改共用；离开有未保存内容时确认。
 */
export function ActionTemplateEditor(props: ActionTemplateEditorProps) {
  const { mode, templateId, canManage, onAuthorizationChanged, onCreated, onSaved, onClose, onDirtyChange } = props;
  const { message, modal } = App.useApp();
  const isNew = !templateId;
  const loaded = useLoad<ActionTemplate>(templateId ? workflowPaths.template(templateId) : null, decodeTemplate);
  const template = loaded.data;
  const type: ActionType = template?.actionType ?? props.actionType ?? 'FORM';
  const [baseline, setBaseline] = useState<TemplateDraft>(() => draftOf(null, type));
  const [draft, setDraft] = useState<TemplateDraft>(() => draftOf(null, type));
  const [codeTouched, setCodeTouched] = useState(false);
  const [step, setStep] = useState<EditorStep>(isNew ? 'basic' : 'config');
  const [deactivating, setDeactivating] = useState(false);
  const [created, setCreated] = useState<ActionTemplate | null>(null);
  const existingCodes = useExistingCodes(isNew);
  const command = useWorkflowCommand(onAuthorizationChanged);
  const references = useTemplateReferences(template, !isNew);
  // 只在已保存内容变化时（首次读取、保存后）重置草稿；启停只改状态与版本，保留未保存的修改。
  const savedContent = template ? JSON.stringify([template.templateName, template.description, template.config]) : '';
  useEffect(() => {
    if (!template) return;
    const next = draftOf(template, template.actionType); setBaseline(next); setDraft(next);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [savedContent]);
  const savedKeys = useMemo(() => new Set(type === 'FORM' ? ((baseline.config as FormDefinition).fields ?? []).map(field => field.key) : []), [baseline, type]);
  const readOnly = !canManage;
  const dirty = needsLeaveConfirm(isDirty(type, baseline, draft), canManage);
  useEffect(() => { onDirtyChange?.(dirty); }, [dirty, onDirtyChange]);
  // 新建成功：基线已等于草稿（不再拦截离开）后再交给调用方跳转。
  useEffect(() => {
    if (!created || dirty) return;
    setCreated(null); onCreated?.(created);
  }, [created, dirty, onCreated]);
  const confirmLeave = useCallback(() => new Promise<boolean>(resolve => {
    modal.confirm({ title: '离开当前动作？', content: '尚未保存的修改将丢失。', okText: '离开', cancelText: '继续编辑', onOk: () => resolve(true), onCancel: () => resolve(false) });
  }), [modal]);
  const problems = useMemo(() => configProblems(type, draft.config), [type, draft.config]);
  const code = isNew && !codeTouched ? suggestTemplateCode(draft.templateName, type, existingCodes) : draft.templateCode;
  const codeProblem = !isNew ? null : !templateCodePattern.test(code) ? '编码以大写字母开头，只能包含大写字母、数字和下划线'
    : existingCodes.has(code) ? `编码 ${code} 已被使用` : null;
  const blocker = !draft.templateName.trim() ? '请填写名称' : codeProblem ?? problems[0] ?? null;

  if (!isNew && loaded.loading && !template) return <Spinner />;
  if (!isNew && !template) return <LoadFailed failure={loaded.failure} retry={loaded.refresh} title="动作加载失败" />;
  if (isNew && type === 'DATA_QUERY') return <Alert type="info" showIcon title="数据查询即将支持，暂不能新建" />;

  const change = (patch: Partial<TemplateDraft>) => setDraft(current => ({ ...current, ...patch }));
  async function reload(id: string): Promise<ActionTemplate | null> {
    try { return await session.request(workflowPaths.template(id), {}, decodeTemplate); } catch { loaded.refresh(); return null; }
  }
  async function save() {
    if (blocker) { void message.warning(blocker); return; }
    const body = { templateName: draft.templateName.trim(), ...(draft.description.trim() ? { description: draft.description.trim() } : {}),
      config: normalizeConfig(type, draft.config) };
    if (isNew) {
      const result = await command.run(workflowPaths.templates, 'POST', { templateCode: code, actionType: type, ...body }, decodeMutation, '新建失败');
      if (!result) return;
      void message.success('动作已创建');
      setBaseline({ ...draft, templateCode: code }); setDraft(current => ({ ...current, templateCode: code }));
      const fresh = await reload(result.objectId);
      setCreated(fresh ?? { templateId: result.objectId, templateCode: code, templateName: body.templateName, actionType: type,
        description: body.description ?? null, config: body.config, status: 'ACTIVE', version: result.version, updatedBy: null, updateTime: new Date().toISOString() });
      return;
    }
    const result = await command.run(workflowPaths.template(template!.templateId), 'PUT', { ...body, expectedVersion: template!.version }, decodeMutation, '保存失败');
    if (!result) return;
    void message.success('已保存；只影响之后新发布的流程版本');
    const fresh = await reload(template!.templateId);
    if (fresh) { loaded.setData(fresh); onSaved?.(fresh); }
  }
  async function changeStatus(status: 'ACTIVE' | 'INACTIVE') {
    if (!template) return;
    const result = await command.run(workflowPaths.templateStatus(template.templateId), 'POST', { status, expectedVersion: template.version }, decodeMutation, '状态修改失败');
    if (!result) return;
    setDeactivating(false); void message.success(status === 'ACTIVE' ? '已启用' : '已停用');
    const fresh = await reload(template.templateId);
    // 只更新状态与版本，保留未保存的修改。
    if (fresh) { loaded.setData(current => current ? { ...current, status: fresh.status, version: fresh.version, updatedBy: fresh.updatedBy,
      updatedByName: fresh.updatedByName, updateTime: fresh.updateTime } : fresh); onSaved?.(fresh); }
  }
  function discard() { setDraft(baseline); setCodeTouched(false); command.clearError(); }
  async function close() {
    if (dirty && !(await confirmLeave())) return;
    onClose?.();
  }

  const referenceCount = references.data ? currentReferences(references.data).current.length : template?.referencedDefinitionCount ?? undefined;
  const steps = editorSteps(type, isNew, referenceCount);
  const title = draft.templateName.trim() || template?.templateName || `新建${actionTypeLabels[type]}`;
  const head = <div className="wf-page-head wf-tpl-head">
    {mode === 'page' ? <h1>{title}</h1> : <h2>{title}</h2>}<ActionTypeTag type={type} />
    {template?.status === 'INACTIVE' && <Tag>已停用</Tag>}
    {dirty && <span className="wf-dirty">未保存的修改</span>}
    <span className="wf-grow" />
    {canManage && <><Button disabled={!dirty} onClick={discard}>放弃修改</Button>
      <Button type="primary" loading={command.saving} disabled={!dirty || !!blocker} title={blocker ?? undefined} onClick={() => void save()}>{isNew ? '创建' : '保存'}</Button></>}
    {mode === 'drawer' && <Button type="text" aria-label="关闭" icon={<CloseOutlined />} onClick={() => void close()} />}
  </div>;
  const body = <>
    {command.conflict && <Alert type="warning" showIcon title="动作已被他人修改，你的修改未保存"
      action={<Button size="small" onClick={() => { command.clearConflict(); loaded.refresh(); }}>重新加载</Button>} />}
    {command.formError && <Alert type="error" showIcon title={command.formError} />}
    {isNew && mode === 'drawer' && <Alert type="info" showIcon title="动作创建后保存在动作目录；即使放弃流程草稿，它也会保留（可停用）" />}
    <div className="wf-tpl-editor">
      <nav className="wf-tpl-steps">
        {steps.map(item => <button type="button" key={item.key} className={`wf-tpl-step${step === item.key ? ' is-active' : ''}`} onClick={() => setStep(item.key)}>
          <span className="wf-grow">{item.label}</span>
          {item.key === 'config' && problems.length > 0 && canManage && <span className="wf-tpl-step-alert" title={problems[0]}>{problems.length}</span>}
          {item.count !== undefined && <span className="wf-tpl-step-count">{item.count}</span>}</button>)}
        <div className="wf-tpl-foot muted">{template ? `版本 ${template.version} · ${template.updatedByName ?? personName(template.updatedBy)} 修改于 ${shortTime(template.updateTime)}`
          : '保存后生成版本 1'}</div>
      </nav>
      <div className="wf-tpl-body">
        {step === 'basic' && <BasicInfo draft={draft} change={change} type={type} template={template} readOnly={readOnly} isNew={isNew} code={code}
          codeProblem={codeProblem} onCode={value => { setCodeTouched(true); change({ templateCode: value }); }}
          onStatus={status => status === 'INACTIVE' ? setDeactivating(true) : void changeStatus('ACTIVE')} />}
        {step === 'config' && (type === 'FORM'
          ? <><ConfigProblems problems={canManage ? problems : []} /><ConfigEditor type={type} config={draft.config} readOnly={readOnly} savedKeys={savedKeys}
            templateName={draft.templateName} description={draft.description} onChange={config => change({ config })} /></>
          : <div className="wf-tpl-instruction">
            <div><ConfigProblems problems={canManage ? problems : []} />
              <ConfigEditor type={type} config={draft.config} readOnly={readOnly} onChange={config => change({ config })} /></div>
            <section><div className="wf-section-title">执行人看到的样子<span className="muted">只读预览</span></div>
              <ExecutorPreview template={{ templateName: draft.templateName, actionType: type, config: draft.config }} /></section>
          </div>)}
        {step === 'references' && template && <ReferencesView template={template} state={references} />}
      </div>
    </div>
    {deactivating && template && <DeactivateModal template={template} references={references} saving={command.saving} error={command.formError}
      onCancel={() => setDeactivating(false)} onConfirm={() => void changeStatus('INACTIVE')} />}
  </>;
  if (mode === 'drawer') return <div className="wf-page wf-tpl-drawer">{head}{body}</div>;
  return <div className="wf-page wf-tpl-page">
    <LeaveGuard dirty={dirty} confirm={confirmLeave} />
    <div className="wf-crumbs"><Link to={workflowRoutes.templates}>动作目录</Link> / {title}</div>
    {head}{body}
  </div>;
}

function ConfigProblems({ problems }: { problems: string[] }) {
  return problems.length ? <Alert type="warning" showIcon className="wf-tpl-problems" title={`还有 ${problems.length} 个问题：${problems.join('；')}`} /> : null;
}

function BasicInfo({ draft, change, type, template, readOnly, isNew, code, codeProblem, onCode, onStatus }: {
  draft: TemplateDraft; change: (patch: Partial<TemplateDraft>) => void; type: ActionType; template: ActionTemplate | undefined; readOnly: boolean;
  isNew: boolean; code: string; codeProblem: string | null; onCode: (value: string) => void; onStatus: (status: 'ACTIVE' | 'INACTIVE') => void;
}) {
  return <section className="wf-card wf-tpl-basic">
    <Field label="名称 *" hint="执行人在待办里看到的名称"><Input value={draft.templateName} maxLength={128} disabled={readOnly} placeholder="如 商户资料表单"
      onChange={event => change({ templateName: event.target.value })} /></Field>
    <Field label="编码" hint={isNew ? '根据名称自动建议（取英文与数字）；大写字母、数字、下划线，全局唯一，创建后不能修改' : '创建后不能修改'}>
      {isNew ? <><Input value={code} maxLength={64} disabled={readOnly} status={codeProblem ? 'error' : undefined}
        onChange={event => onCode(event.target.value.toUpperCase())} />{codeProblem && <span className="wf-danger-text">{codeProblem}</span>}</>
        : <code>{template?.templateCode}</code>}</Field>
    <Field label="描述" hint="列表中名称下方的说明；表单动作也显示在表单标题下"><Input.TextArea rows={3} maxLength={512} value={draft.description} disabled={readOnly}
      onChange={event => change({ description: event.target.value })} /></Field>
    <div className="wf-two-columns">
      <Field label="类型"><span><ActionTypeTag type={type} /> <span className="muted">创建后不能修改</span></span></Field>
      <Field label="状态">{template ? <span className="wf-inline"><span className={`wf-status-dot is-${template.status.toLowerCase()}`} />{configStatusLabels[template.status]}
        {!readOnly && (template.status === 'ACTIVE' ? <Button size="small" danger onClick={() => onStatus('INACTIVE')}>停用</Button>
          : <Button size="small" onClick={() => onStatus('ACTIVE')}>启用</Button>)}</span>
        : <span className="muted">创建后为启用</span>}</Field>
    </div>
  </section>;
}

/** 新建时读取已有编码，用于编码建议与重复提示（服务端仍以唯一约束为准）。 */
function useExistingCodes(enabled: boolean): ReadonlySet<string> {
  const [codes, setCodes] = useState<ReadonlySet<string>>(new Set());
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    requestAllPages(workflowPaths.templates, controller.signal, undefined, {}, decodeTemplates)
      .then(items => { if (!controller.signal.aborted) setCodes(new Set(items.map(item => item.templateCode))); })
      .catch(() => undefined);
    return () => controller.abort();
  }, [enabled]);
  return codes;
}

/**
 * 抽屉形态（S3 流程编辑器就地新建动作）：关闭前确认未保存修改。新建时 actionType 必填（先用 ActionTypePicker 选择）。
 */
export function ActionTemplateDrawer({ open, width = 1180, ...props }: Omit<ActionTemplateEditorProps, 'mode'> & { open: boolean; width?: number }) {
  return <Drawer open={open} size={`min(${width}px, 96vw)`} closable={false} maskClosable={false} destroyOnHidden
    styles={{ body: { padding: 16, background: '#f6f8fb' } }} onClose={props.onClose}>
    {open && <ActionTemplateEditor mode="drawer" {...props} />}
  </Drawer>;
}
