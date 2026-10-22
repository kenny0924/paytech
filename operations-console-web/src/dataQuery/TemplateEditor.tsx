import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { App, Alert, Button, Input, Modal, Select, Tabs, Tag } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import { codePattern, dataQueryButtons, dataQueryPaths, dataQueryRoutes, decodeMutation, hasButton } from './dataQueryCommon';
import {
  bodyBytes, completionWords, contentBody, createTemplateBody, decodeSaveResult, decodeTemplateDetail, decodeValidation, draftFingerprint,
  draftFromDetail, emptyDraft, fieldErrorProblems, problemDiagnostics, problemLocation, problemTarget, templateLimits, updateTemplateBody,
} from './templateModel';
import type { SaveResult, TemplateDetail, TemplateDraft, TemplateProblem } from './templateModel';
import { engineLabels, templateSourceOptions } from './dataSourceModel';
import { BodyEditor } from './BodyEditor';
import type { BodyEditorHandle } from './BodyEditor';
import { DeclarationPanel } from './DeclarationPanel';
import type { DeclarationTab } from './DeclarationPanel';
import { TrialPanel } from './TrialPanel';
import { VersionDrawer } from './VersionDrawer';
import { ConflictBanner, Note, StatusTag, useDataSourceOptions, useResource } from './DataQueryParts';
import { useDataQueryCommand } from './useDataQueryCommand';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; templateId: string | null }

/** 模板编辑器（原型 04—06）：templateId 为 null 时新建。详情版本变化（保存、恢复、启停）后重新载入工作区。 */
export function TemplateEditor({ resources, onAuthorizationChanged, templateId }: Props) {
  const canManage = hasButton(resources, dataQueryButtons.templateManage);
  const decode = useCallback((value: unknown) => decodeTemplateDetail(value, templateId ?? undefined), [templateId]);
  const detail = useResource(templateId ? dataQueryPaths.template(templateId) : null, decode, '模板加载失败');
  if (!templateId && !canManage) return <Alert type="warning" showIcon title="当前没有查询模板维护权限" />;
  if (templateId && detail.loading && !detail.data) return <Loading />;
  if (templateId && !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  return <Workspace key={detail.data ? `${detail.data.templateId}:${detail.data.version}` : 'new'} detail={detail.data ?? null} canManage={canManage}
    onReload={detail.refresh} onAuthorizationChanged={onAuthorizationChanged} />;
}

type Focus = { tab: DeclarationTab; index?: number; field?: string; at: number } | null;
/** 校验结果连同被校验的正文：编辑器标记按该正文换算，之后的编辑由 CodeMirror 映射位置。 */
type ValidationState = { problems: TemplateProblem[]; fingerprint: string; body: string } | null;

function Workspace({ detail, canManage, onReload, onAuthorizationChanged }: {
  detail: TemplateDetail | null; canManage: boolean; onReload: () => void; onAuthorizationChanged: () => void;
}) {
  const navigate = useNavigate(); const { modal, notification } = App.useApp();
  const sources = useDataSourceOptions();
  const [draft, setDraft] = useState<TemplateDraft>(() => detail ? draftFromDetail(detail) : emptyDraft());
  const [baseline, setBaseline] = useState(() => draftFingerprint(draft));
  const [validation, setValidation] = useState<ValidationState>(null);
  const [validating, setValidating] = useState(false);
  const [tab, setTab] = useState<DeclarationTab>('parameters');
  const [bottom, setBottom] = useState<'problems' | 'trial'>('problems');
  const [focus, setFocus] = useState<Focus>(null);
  const [highlightSource, setHighlightSource] = useState(false);
  const [saving, setSaving] = useState(false);
  const [versionsOpen, setVersionsOpen] = useState(false);
  const [statusOpen, setStatusOpen] = useState(false);
  const editor = useRef<BodyEditorHandle | null>(null);
  const command = useDataQueryCommand(onAuthorizationChanged);
  const fingerprint = draftFingerprint(draft);
  const dirty = fingerprint !== baseline;
  const readOnly = !canManage;
  const contentKey = useMemo(() => JSON.stringify(contentBody(draft)), [draft]);
  const latest = useRef({ contentKey, body: draft.body }); latest.current = { contentKey, body: draft.body };

  const confirmLeave = useCallback(() => new Promise<boolean>(resolve => {
    modal.confirm({ title: '离开当前编辑？', content: '尚未保存的修改将丢失。', okText: '离开', cancelText: '继续编辑',
      onOk: () => resolve(true), onCancel: () => resolve(false) });
  }), [modal]);
  useUnsavedRuleGuard(dirty && canManage, confirmLeave);

  // 输入停顿 500 ms 后调用校验接口；以服务端结果为准，前端不解析模板。
  useEffect(() => {
    if (!canManage || !draft.dataSourceId) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setValidating(true);
      session.request(dataQueryPaths.validations, { method: 'POST', signal: controller.signal, headers: { 'Content-Type': 'application/json' }, body: contentKey },
        decodeValidation)
        .then(result => { if (!controller.signal.aborted) setValidation({ problems: result.problems, fingerprint: contentKey, body: draft.body }); })
        .catch(error => {
          if (controller.signal.aborted) return;
          if (error instanceof ApiError && error.fieldErrors.length) setValidation({ problems: fieldErrorProblems(error.fieldErrors), fingerprint: contentKey, body: draft.body });
          else if (error instanceof ApiError && error.status === 400) setValidation({ problems: [{ level: 'ERROR', line: null, column: null, field: null, message: error.message }], fingerprint: contentKey, body: draft.body });
          else if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
        })
        .finally(() => { if (!controller.signal.aborted) setValidating(false); });
    }, 500);
    return () => { window.clearTimeout(timer); controller.abort(); };
  }, [contentKey, canManage, draft.dataSourceId, draft.body, onAuthorizationChanged]);

  const problems = validation?.problems ?? [];
  const diagnostics = useMemo(() => validation ? problemDiagnostics(validation.body, validation.problems) : [], [validation]);
  const errors = problems.filter(problem => problem.level === 'ERROR');
  const warnings = problems.filter(problem => problem.level === 'WARNING');
  const words = useMemo(() => completionWords(draft.parameters), [draft.parameters]);
  const source = sources.items.find(item => item.dataSourceId === draft.dataSourceId);
  const bytes = bodyBytes(draft.body);
  const change = (patch: Partial<TemplateDraft>) => setDraft(current => ({ ...current, ...patch }));

  function locate(problem: TemplateProblem) {
    const target = problemTarget(problem);
    if (target.kind === 'body') editor.current?.focus(target.line, target.column);
    else if (target.kind === 'parameter') { setTab('parameters'); setFocus({ tab: 'parameters', index: target.index, at: Date.now() }); }
    else if (target.kind === 'columns') { setTab('columns'); setFocus({ tab: 'columns', index: target.index, at: Date.now() }); }
    else if (target.kind === 'constraints') { setTab('constraints'); setFocus({ tab: 'constraints', field: target.field, at: Date.now() }); }
    else if (target.kind === 'dataSource') { setHighlightSource(true); window.setTimeout(() => setHighlightSource(false), 2000); }
  }

  async function save(changeNote: string): Promise<boolean> {
    const result = detail
      ? await command.run(dataQueryPaths.template(detail.templateId), 'PUT', updateTemplateBody(draft, detail.version, changeNote),
        value => decodeSaveResult(value, detail.templateId), '模板保存失败')
      : await command.run(dataQueryPaths.templates, 'POST', createTemplateBody(draft, changeNote), value => decodeSaveResult(value), '模板创建失败');
    if (!result) return false;
    announce(result, detail ? '已保存' : '已创建');
    flushSync(() => setBaseline(fingerprint));
    if (detail) onReload(); else void navigate(dataQueryRoutes.template(result.templateId), { replace: true });
    return true;
  }
  function announce(result: SaveResult, verb: string) {
    const text = `${result.templateCode} ${verb}为 v${result.currentVersionNo}，已立即生效。`;
    if (result.warnings.length) notification.warning({ title: text, description: <ul className="dq-warning-list">{result.warnings.map((warning, index) =>
      <li key={index}>{warning.line ? `第 ${warning.line} 行：` : ''}{warning.message}</li>)}</ul>, duration: 8 });
    else notification.success({ title: text, duration: 4 });
  }
  // 保存失败的 422 字段错误进入问题面板（只在新的字段错误到达时同步）。
  const saveErrors = command.fieldErrors;
  useEffect(() => {
    if (!saveErrors.length) return;
    setValidation({ problems: fieldErrorProblems(saveErrors), fingerprint: latest.current.contentKey, body: latest.current.body }); setBottom('problems');
  }, [saveErrors]);

  const nextVersion = detail ? detail.currentVersionNo + 1 : 1;
  const stale = validation !== null && validation.fingerprint !== contentKey;
  return <div className="dq-editor">
    <header className="dq-editor-top">
      <nav className="dq-crumbs"><Link to={dataQueryRoutes.templates}>查询模板</Link><span>/</span><span>{detail ? detail.templateCode : '新建模板'}</span></nav>
      <div className="dq-editor-title">
        {detail ? <h1 className="mono">{detail.templateCode}</h1>
          : <Input className="mono dq-code-input" value={draft.templateCode} placeholder="模板编码，如 PAY_REFUND_PENDING" maxLength={64}
            status={draft.templateCode && !codePattern.test(draft.templateCode) ? 'error' : undefined}
            onChange={event => change({ templateCode: event.target.value.toUpperCase() })} />}
        <Input className="dq-name-input" value={draft.templateName} placeholder="模板名称" maxLength={128} disabled={readOnly}
          onChange={event => change({ templateName: event.target.value })} />
        <Select className={`dq-source-select${highlightSource ? ' is-focused' : ''}`} value={draft.dataSourceId ?? undefined} placeholder="选择数据源"
          loading={sources.loading} disabled={readOnly} showSearch optionFilterProp="label" popupMatchSelectWidth={false}
          options={templateSourceOptions(sources.items, [detail?.currentVersion.dataSourceId, draft.dataSourceId])}
          onChange={dataSourceId => change({ dataSourceId })} />
        {detail && <StatusTag status={detail.status} />}
        {detail && <Tag>当前 v{detail.currentVersionNo}</Tag>}
        {dirty && canManage && <Tag color="orange">未保存</Tag>}
        <span className="dq-spacer" />
        {detail && <Button onClick={() => setVersionsOpen(true)}>版本历史</Button>}
        {detail && canManage && <Button danger={detail.status === 'ACTIVE'} onClick={() => setStatusOpen(true)}>{detail.status === 'ACTIVE' ? '停用' : '启用'}</Button>}
        {canManage && <Button type="primary" disabled={!dirty && !!detail} loading={command.saving} onClick={() => setSaving(true)}>保存为 v{nextVersion}</Button>}
      </div>
      <Input className="dq-description-input" value={draft.description} placeholder="说明（可选）" maxLength={512} disabled={readOnly} variant="borderless"
        onChange={event => change({ description: event.target.value })} />
      {command.conflict && <ConflictBanner subject="模板" onReload={() => { flushSync(() => setBaseline(fingerprint)); onReload(); }} />}
      {command.formError && !command.fieldErrors.length && <Alert type="error" showIcon closable className="dq-banner" title={command.formError} onClose={command.clearError} />}
      {source && source.status !== 'ACTIVE' && <Alert type="warning" showIcon className="dq-banner" title={`数据源 ${source.dataSourceCode} 已停用：保存与试运行会被拒绝，请先启用或改选数据源。`} />}
      {readOnly && <Note>只读：没有查询模板维护权限，不进行实时校验与试运行。</Note>}
    </header>
    <div className="dq-editor-body">
      <section className="dq-editor-left">
        <div className="dq-code-card">
          <div className="dq-code-legend">
            <strong>正文</strong>
            <span className="muted">{source ? `${engineLabels[source.engineType]} 方言` : '未选数据源'} · 统一模板语法</span>
            <span className="dq-legend"><span className="dq-tpl-value">#{'{}'} 值</span><span className="dq-tpl-fragment">@{'{}'} 片段</span>
              <span className="dq-tpl-tag">{'{{#if}} {{#each}} {{#join}} {{#part}}'}</span><span className="dq-tpl-comment">{'{{!-- --}}'}</span></span>
            <span className={`dq-bytes${bytes > templateLimits.maxBodyBytes ? ' dq-text-error' : ''}`}>{(bytes / 1024).toFixed(1)} / 64 KB</span>
          </div>
          <BodyEditor value={draft.body} onChange={body => change({ body })} diagnostics={diagnostics} completions={words} readOnly={readOnly}
            onReady={handle => { editor.current = handle; }} />
        </div>
        <div className="dq-bottom">
          <Tabs activeKey={bottom} onChange={key => setBottom(key as 'problems' | 'trial')} items={[
            { key: 'problems', label: <span>问题 {errors.length ? <Tag color="red">{errors.length} 错误</Tag> : null}{warnings.length ? <Tag color="gold">{warnings.length} 警告</Tag> : null}</span>,
              children: <ProblemPanel problems={problems} validating={validating} stale={stale} enabled={canManage && !!draft.dataSourceId} onLocate={locate} /> },
            { key: 'trial', label: '试运行', disabled: !canManage,
              children: <TrialPanel draft={draft} canRun={canManage && errors.length === 0} onAuthorizationChanged={onAuthorizationChanged} /> },
          ]} />
        </div>
      </section>
      <aside className="dq-editor-right">
        <DeclarationPanel tab={tab} onTab={setTab} parameters={draft.parameters} onParameters={parameters => change({ parameters })}
          constraints={draft.constraints} onConstraints={constraints => change({ constraints })} columns={draft.columns} onColumns={columns => change({ columns })}
          readOnly={readOnly} focus={focus} />
      </aside>
    </div>
    {saving && <SaveModal nextVersion={nextVersion} creating={!detail} draft={draft} warnings={warnings} errors={errors.length} stale={stale}
      onClose={() => setSaving(false)} onSave={async note => { await save(note); setSaving(false); }} busy={command.saving} />}
    {versionsOpen && detail && <VersionDrawer templateId={detail.templateId} templateCode={detail.templateCode} currentVersionNo={detail.currentVersionNo}
      expectedVersion={detail.version} dirty={dirty && canManage} canManage={canManage} onClose={() => setVersionsOpen(false)}
      onRestored={result => { announce(result, '已恢复'); setVersionsOpen(false); flushSync(() => setBaseline(fingerprint)); onReload(); }}
      onReload={() => { setVersionsOpen(false); flushSync(() => setBaseline(fingerprint)); onReload(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {statusOpen && detail && <TemplateStatusModal detail={detail} dirty={dirty} onClose={() => setStatusOpen(false)}
      onDone={() => { setStatusOpen(false); flushSync(() => setBaseline(fingerprint)); onReload(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 问题面板：错误、警告与展开校验说明；点击定位到正文行列或右侧对应标签页。 */
function ProblemPanel({ problems, validating, stale, enabled, onLocate }: {
  problems: TemplateProblem[]; validating: boolean; stale: boolean; enabled: boolean; onLocate: (problem: TemplateProblem) => void;
}) {
  if (!enabled) return <Note>选择数据源后，输入停顿 0.5 秒自动校验。</Note>;
  const sorted = [...problems].sort((a, b) => (a.level === b.level ? 0 : a.level === 'ERROR' ? -1 : 1));
  const errors = problems.some(problem => problem.level === 'ERROR');
  return <div className="dq-problems" aria-live="polite">
    {(validating || stale) && <div className="muted dq-problem-status">{validating ? '校验中…' : '内容已修改，等待重新校验…'}</div>}
    {sorted.map((problem, index) => <div key={index} className={`dq-problem is-${problem.level.toLowerCase()}`}>
      <Tag color={problem.level === 'ERROR' ? 'red' : 'gold'}>{problem.level === 'ERROR' ? '错误' : '警告'}</Tag>
      <span className="mono dq-problem-at">{problemLocation(problem)}</span>
      <span className="dq-problem-message">{problem.message}</span>
      <button type="button" className="dq-link" onClick={() => onLocate(problem)}>定位</button>
    </div>)}
    <div className="dq-problem is-info"><Tag color="blue">说明</Tag><span className="mono dq-problem-at">展开校验</span>
      <span className="dq-problem-message">{errors ? '修复错误后，' : ''}按 if / 集合空与一个元素 / 片段取值列举组合，逐个检查只允许单条 SELECT；组合较多时按覆盖集校验。</span></div>
    {!validating && !stale && problems.length === 0 && <div className="dq-ok">校验通过</div>}
  </div>;
}

/** 保存：填写变更说明；显示当前警告（不阻止保存）。服务端仍做完整校验，失败的问题回到问题面板。 */
function SaveModal({ nextVersion, creating, draft, warnings, errors, stale, busy, onClose, onSave }: {
  nextVersion: number; creating: boolean; draft: TemplateDraft; warnings: TemplateProblem[]; errors: number; stale: boolean; busy: boolean;
  onClose: () => void; onSave: (note: string) => Promise<void>;
}) {
  const [note, setNote] = useState('');
  const missing = creating && !codePattern.test(draft.templateCode) ? '模板编码格式不正确' : !draft.templateName.trim() ? '请填写模板名称'
    : !draft.dataSourceId ? '请选择数据源' : null;
  return <Modal open wrapClassName="dq-modal" title={`保存为 v${nextVersion}`} okText="保存并生效" cancelText="取消" confirmLoading={busy} okButtonProps={{ disabled: !!missing }}
    onOk={() => void onSave(note)} onCancel={() => { if (!busy) onClose(); }} destroyOnHidden>
    <p>保存后立即生效，调用方下一次执行使用 v{nextVersion}。</p>
    {missing && <Note tone="error">{missing}</Note>}
    {errors > 0 && <Note tone="error">当前有 {errors} 个错误，服务端会拒绝保存。</Note>}
    {stale && <Note tone="warning">校验结果尚未更新，保存时服务端会重新校验。</Note>}
    {warnings.length > 0 && <Note tone="warning">{warnings.length} 个警告（不阻止保存）：<ul className="dq-warning-list">{warnings.map((warning, index) =>
      <li key={index}>{warning.message}</li>)}</ul></Note>}
    <Input.TextArea rows={3} maxLength={512} showCount value={note} onChange={event => setNote(event.target.value)} placeholder="变更说明（可选），例如：新增 AMOUNT 排序" autoFocus />
  </Modal>;
}

function TemplateStatusModal({ detail, dirty, onClose, onDone, onAuthorizationChanged }: {
  detail: TemplateDetail; dirty: boolean; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const command = useDataQueryCommand(onAuthorizationChanged);
  const target = detail.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'; const verb = target === 'ACTIVE' ? '启用' : '停用';
  const submit = async () => {
    const result = await command.run(dataQueryPaths.templateStatus(detail.templateId), 'POST', { status: target, expectedVersion: detail.version },
      value => decodeMutation(value, 'QUERY_TEMPLATE', detail.templateId), `${verb}模板失败`);
    if (result) onDone();
  };
  return <Modal open wrapClassName="dq-modal" title={`${verb}模板 · ${detail.templateCode}`} okText={`确认${verb}`} cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ danger: target === 'INACTIVE', disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}>
    {command.conflict && <ConflictBanner subject="模板" onReload={onDone} />}
    {command.formError && <Note tone="error">{command.formError}</Note>}
    <p>{target === 'INACTIVE' ? '停用后调用方无法执行此模板，版本与内容保留。' : `启用后调用方按当前版本 v${detail.currentVersionNo} 执行。`}</p>
    {dirty && <Note tone="warning">编辑器中有未保存的修改，{verb}后会重新载入模板，未保存的修改将丢失。</Note>}
  </Modal>;
}
