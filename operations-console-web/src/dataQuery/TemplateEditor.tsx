import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { App, Alert, Button, Drawer, Input, Modal, Select, Tabs, Tag } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useT } from '../i18n/react';
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
import { ColumnList, ConstraintsForm, ParameterList } from './DeclarationPanel';
import type { DeclarationTab } from './DeclarationPanel';
import { TrialPanel } from './TrialPanel';
import { mergeResultColumns, syncColumns, syncParameters, unreferencedParameters } from './templateSync';
import { VersionDrawer } from './VersionDrawer';
import { ConflictBanner, Note, StatusTag, useDataSourceOptions, useResource } from './DataQueryParts';
import { useDataQueryCommand } from './useDataQueryCommand';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void; templateId: string | null }

/**
 * 模板编辑器（原型 04—06）：templateId 为 null 时新建。详情版本变化（保存、恢复、启停）后重新载入工作区。
 * 正文、参数、约束、列与问题是同一卡片里的标签页；试运行在右侧抽屉中进行，抽屉关闭后保留输入与结果。
 */
export function TemplateEditor({ resources, onAuthorizationChanged, templateId }: Props) {
  const canManage = hasButton(resources, dataQueryButtons.templateManage); const t = useT('data-query');
  const decode = useCallback((value: unknown) => decodeTemplateDetail(value, templateId ?? undefined), [templateId]);
  const detail = useResource(templateId ? dataQueryPaths.template(templateId) : null, decode, t('template.loadFailed'));
  if (!templateId && !canManage) return <Alert type="warning" showIcon title={t('template.noPermission')} />;
  if (templateId && detail.loading && !detail.data) return <Loading />;
  if (templateId && !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  return <Workspace key={detail.data ? `${detail.data.templateId}:${detail.data.version}` : 'new'} detail={detail.data ?? null} canManage={canManage}
    onReload={detail.refresh} onAuthorizationChanged={onAuthorizationChanged} />;
}

type EditorTab = 'body' | DeclarationTab | 'problems';
type Focus = { tab: DeclarationTab; index?: number; field?: string; at: number } | null;
/** 校验结果连同被校验的正文：编辑器标记按该正文换算，之后的编辑由 CodeMirror 映射位置。 */
type ValidationState = { problems: TemplateProblem[]; fingerprint: string; body: string } | null;

function Workspace({ detail, canManage, onReload, onAuthorizationChanged }: {
  detail: TemplateDetail | null; canManage: boolean; onReload: () => void; onAuthorizationChanged: () => void;
}) {
  const navigate = useNavigate(); const { modal, notification } = App.useApp();
  const t = useT('data-query'), tNav = useT('nav');
  const sources = useDataSourceOptions();
  const [draft, setDraft] = useState<TemplateDraft>(() => detail ? draftFromDetail(detail) : emptyDraft());
  const [baseline, setBaseline] = useState(() => draftFingerprint(draft));
  const [validation, setValidation] = useState<ValidationState>(null);
  const [validating, setValidating] = useState(false);
  const [tab, setTab] = useState<EditorTab>('body');
  const [trialOpen, setTrialOpen] = useState(false);
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

  // 正文停顿 400 ms 后按引用同步参数与列；只响应载入后的正文修改，打开已有模板不会产生未保存修改。
  const loadedBody = useRef(draft.body);
  useEffect(() => {
    if (readOnly || draft.body === loadedBody.current) return;
    const timer = window.setTimeout(() => setDraft(current => {
      const parameters = syncParameters(current.body, current.parameters); const columns = syncColumns(current.body, current.columns);
      return parameters === current.parameters && columns === current.columns ? current : { ...current, parameters, columns };
    }), 400);
    return () => window.clearTimeout(timer);
  }, [draft.body, readOnly]);

  const confirmLeave = useCallback(() => new Promise<boolean>(resolve => {
    modal.confirm({ title: t('template.leave.title'), content: t('template.leave.content'), okText: t('template.leave.ok'), cancelText: t('template.leave.cancel'),
      onOk: () => resolve(true), onCancel: () => resolve(false) });
  }), [modal, t]);
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
  const unreferenced = useMemo(() => unreferencedParameters(draft.body, draft.parameters), [draft.body, draft.parameters]);
  const source = sources.items.find(item => item.dataSourceId === draft.dataSourceId);
  const bytes = bodyBytes(draft.body);
  const change = (patch: Partial<TemplateDraft>) => setDraft(current => ({ ...current, ...patch }));

  function locate(problem: TemplateProblem) {
    const target = problemTarget(problem);
    // 正文标签页切回可见后再定位，CodeMirror 需要可见布局才能滚动到目标行。
    if (target.kind === 'body') { setTab('body'); window.setTimeout(() => editor.current?.focus(target.line, target.column)); }
    else if (target.kind === 'parameter') { setTab('parameters'); setFocus({ tab: 'parameters', index: target.index, at: Date.now() }); }
    else if (target.kind === 'columns') { setTab('columns'); setFocus({ tab: 'columns', index: target.index, at: Date.now() }); }
    else if (target.kind === 'constraints') { setTab('constraints'); setFocus({ tab: 'constraints', field: target.field, at: Date.now() }); }
    else if (target.kind === 'dataSource') { setHighlightSource(true); window.setTimeout(() => setHighlightSource(false), 2000); }
  }

  async function save(changeNote: string): Promise<boolean> {
    const result = detail
      ? await command.run(dataQueryPaths.template(detail.templateId), 'PUT', updateTemplateBody(draft, detail.version, changeNote),
        value => decodeSaveResult(value, detail.templateId), t('template.saveFailed'))
      : await command.run(dataQueryPaths.templates, 'POST', createTemplateBody(draft, changeNote), value => decodeSaveResult(value), t('template.createFailed'));
    if (!result) return false;
    announce(result, detail ? 'saved' : 'created');
    flushSync(() => setBaseline(fingerprint));
    if (detail) onReload(); else void navigate(dataQueryRoutes.template(result.templateId), { replace: true });
    return true;
  }
  function announce(result: SaveResult, outcome: 'saved' | 'created' | 'restored') {
    const params = { code: result.templateCode, versionNo: result.currentVersionNo };
    const text = outcome === 'saved' ? t('template.announce.saved', params) : outcome === 'created' ? t('template.announce.created', params)
      : t('template.announce.restored', params);
    if (result.warnings.length) notification.warning({ title: text, description: <ul className="dq-warning-list">{result.warnings.map((warning, index) =>
      <li key={index}>{warning.line ? t('template.warningAtLine', { line: warning.line, message: warning.message }) : warning.message}</li>)}</ul>, duration: 8 });
    else notification.success({ title: text, duration: 4 });
  }
  // 保存失败的 422 字段错误进入问题面板（只在新的字段错误到达时同步）。
  const saveErrors = command.fieldErrors;
  useEffect(() => {
    if (!saveErrors.length) return;
    setValidation({ problems: fieldErrorProblems(saveErrors), fingerprint: latest.current.contentKey, body: latest.current.body }); setTab('problems');
  }, [saveErrors]);

  const nextVersion = detail ? detail.currentVersionNo + 1 : 1;
  const stale = validation !== null && validation.fingerprint !== contentKey;
  return <div className="dq-editor">
    <header className="dq-editor-top">
      <nav className="dq-crumbs"><Link to={dataQueryRoutes.templates}>{tNav('resource.OPERATIONS_QUERY_TEMPLATES')}</Link><span>/</span><span>{detail ? detail.templateCode : t('template.new')}</span></nav>
      <div className="dq-editor-title">
        {detail ? <h1 className="mono">{detail.templateCode}</h1>
          : <Input className="mono dq-code-input" value={draft.templateCode} placeholder={t('template.codePlaceholder')} maxLength={64}
            status={draft.templateCode && !codePattern.test(draft.templateCode) ? 'error' : undefined}
            onChange={event => change({ templateCode: event.target.value.toUpperCase() })} />}
        <Input className="dq-name-input" value={draft.templateName} placeholder={t('template.namePlaceholder')} maxLength={128} disabled={readOnly}
          onChange={event => change({ templateName: event.target.value })} />
        <Select className={`dq-source-select${highlightSource ? ' is-focused' : ''}`} value={draft.dataSourceId ?? undefined} placeholder={t('template.dataSourcePlaceholder')}
          loading={sources.loading} disabled={readOnly} showSearch optionFilterProp="label" popupMatchSelectWidth={false}
          options={templateSourceOptions(sources.items, [detail?.currentVersion.dataSourceId, draft.dataSourceId])}
          onChange={dataSourceId => change({ dataSourceId })} />
        {detail && <StatusTag status={detail.status} />}
        {detail && <Tag>{t('template.currentVersion', { versionNo: detail.currentVersionNo })}</Tag>}
        {dirty && canManage && <Tag color="orange">{t('template.unsaved')}</Tag>}
        <span className="dq-spacer" />
        {detail && <Button onClick={() => setVersionsOpen(true)}>{t('template.versionHistory')}</Button>}
        {canManage && <Button onClick={() => setTrialOpen(true)}>{t('template.trial')}</Button>}
        {detail && canManage && <Button danger={detail.status === 'ACTIVE'} onClick={() => setStatusOpen(true)}>{detail.status === 'ACTIVE' ? t('template.disable') : t('template.enable')}</Button>}
        {canManage && <Button type="primary" disabled={!dirty && !!detail} loading={command.saving} onClick={() => setSaving(true)}>{t('template.saveAs', { versionNo: nextVersion })}</Button>}
      </div>
      <Input className="dq-description-input" value={draft.description} placeholder={t('template.descriptionPlaceholder')} maxLength={512} disabled={readOnly} variant="borderless"
        onChange={event => change({ description: event.target.value })} />
      {command.conflict && <ConflictBanner subject="template" onReload={() => { flushSync(() => setBaseline(fingerprint)); onReload(); }} />}
      {command.formError && !command.fieldErrors.length && <Alert type="error" showIcon closable className="dq-banner" title={command.formError} onClose={command.clearError} />}
      {source && source.status !== 'ACTIVE' && <Alert type="warning" showIcon className="dq-banner" title={t('template.dataSourceInactive', { code: source.dataSourceCode })} />}
      {readOnly && <Note>{t('template.readOnly')}</Note>}
    </header>
    <section className="dq-editor-card">
      <Tabs activeKey={tab} onChange={key => setTab(key as EditorTab)} className="dq-editor-tabs" items={[
        { key: 'body', label: t('template.tab.body'), forceRender: true, children: <div className="dq-code-card">
          <div className="dq-code-legend">
            <span className="muted">{source ? t('template.dialect', { engine: engineLabels[source.engineType] }) : t('template.noDataSource')}</span>
            <span className="dq-legend"><span className="dq-tpl-value">{t('template.legend.value', { marker: '#{}' })}</span><span className="dq-tpl-fragment">{t('template.legend.fragment', { marker: '@{}' })}</span>
              <span className="dq-tpl-tag">{'{{#if}} {{#each}} {{#join}} {{#part}}'}</span><span className="dq-tpl-comment">{'{{!-- --}}'}</span></span>
            <ValidationBadge enabled={canManage && !!draft.dataSourceId} validating={validating} stale={stale} errors={errors.length} warnings={warnings.length}
              onOpen={() => setTab('problems')} />
            <span className={`dq-bytes${bytes > templateLimits.maxBodyBytes ? ' dq-text-error' : ''}`}>{(bytes / 1024).toFixed(1)} / {templateLimits.maxBodyBytes / 1024} KB</span>
          </div>
          <BodyEditor value={draft.body} onChange={body => change({ body })} diagnostics={diagnostics} completions={words} readOnly={readOnly}
            onReady={handle => { editor.current = handle; }} />
        </div> },
        { key: 'parameters', label: <span>{t('template.tab.parameters')} <Tag className="dq-tab-count">{draft.parameters.length}</Tag></span>,
          children: <ParameterList rows={draft.parameters} onChange={parameters => change({ parameters })} readOnly={readOnly} unreferenced={unreferenced}
            focusIndex={focus?.tab === 'parameters' ? focus.index : undefined} /> },
        { key: 'constraints', label: t('template.tab.constraints'),
          children: <ConstraintsForm value={draft.constraints} onChange={constraints => change({ constraints })} readOnly={readOnly}
            focusField={focus?.tab === 'constraints' ? focus.field : undefined} /> },
        { key: 'columns', label: <span>{t('template.tab.columns')} <Tag className="dq-tab-count">{draft.columns.length}</Tag></span>,
          children: <ColumnList rows={draft.columns} onChange={columns => change({ columns })} readOnly={readOnly}
            focusIndex={focus?.tab === 'columns' ? focus.index : undefined} /> },
        { key: 'problems', label: <span>{t('template.tab.problems')} {errors.length ? <Tag color="red">{t('problems.errorCount', { count: errors.length })}</Tag> : null}{warnings.length ? <Tag color="gold">{t('problems.warningCount', { count: warnings.length })}</Tag> : null}</span>,
          children: <ProblemPanel problems={problems} validating={validating} stale={stale} enabled={canManage && !!draft.dataSourceId} onLocate={locate} /> },
      ]} />
    </section>
    {canManage && <Drawer open={trialOpen} rootClassName="dq-modal" size={960} title={t('trial.drawerTitle', { code: draft.templateCode || t('trial.unnamed') })}
      onClose={() => setTrialOpen(false)}>
      {errors.length > 0 && <Note tone="error">{t('trial.blockedByErrors', { count: errors.length })}<button type="button" className="dq-link"
        onClick={() => { setTrialOpen(false); setTab('problems'); }}>{t('problems.view')}</button></Note>}
      <TrialPanel draft={draft} canRun={errors.length === 0} onAuthorizationChanged={onAuthorizationChanged}
        onResultColumns={names => setDraft(current => {
          const columns = mergeResultColumns(current.columns, names);
          return columns === current.columns ? current : { ...current, columns };
        })} />
    </Drawer>}
    {saving && <SaveModal nextVersion={nextVersion} creating={!detail} draft={draft} warnings={warnings} errors={errors.length} stale={stale}
      onClose={() => setSaving(false)} onSave={async note => { await save(note); setSaving(false); }} busy={command.saving} />}
    {versionsOpen && detail && <VersionDrawer templateId={detail.templateId} templateCode={detail.templateCode} currentVersionNo={detail.currentVersionNo}
      expectedVersion={detail.version} dirty={dirty && canManage} canManage={canManage} onClose={() => setVersionsOpen(false)}
      onRestored={result => { announce(result, 'restored'); setVersionsOpen(false); flushSync(() => setBaseline(fingerprint)); onReload(); }}
      onReload={() => { setVersionsOpen(false); flushSync(() => setBaseline(fingerprint)); onReload(); }} onAuthorizationChanged={onAuthorizationChanged} />}
    {statusOpen && detail && <TemplateStatusModal detail={detail} dirty={dirty} onClose={() => setStatusOpen(false)}
      onDone={() => { setStatusOpen(false); flushSync(() => setBaseline(fingerprint)); onReload(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 正文工具栏上的校验状态：点击切到问题标签页。 */
function ValidationBadge({ enabled, validating, stale, errors, warnings, onOpen }: {
  enabled: boolean; validating: boolean; stale: boolean; errors: number; warnings: number; onOpen: () => void;
}) {
  const t = useT('data-query');
  if (!enabled) return null;
  if (validating || stale) return <span className="muted dq-validation">{t('problems.validating')}</span>;
  if (!errors && !warnings) return <span className="dq-ok dq-validation">✓ {t('problems.passed')}</span>;
  return <button type="button" className="dq-link dq-validation" onClick={onOpen}>
    {errors ? <Tag color="red">{t('problems.errorCount', { count: errors })}</Tag> : null}{warnings ? <Tag color="gold">{t('problems.warningCount', { count: warnings })}</Tag> : null}{t('problems.view')}</button>;
}

/** 问题面板：错误、警告与展开校验说明；点击定位到正文行列或右侧对应标签页。 */
function ProblemPanel({ problems, validating, stale, enabled, onLocate }: {
  problems: TemplateProblem[]; validating: boolean; stale: boolean; enabled: boolean; onLocate: (problem: TemplateProblem) => void;
}) {
  const t = useT('data-query');
  if (!enabled) return <Note>{t('problems.disabledHint')}</Note>;
  const sorted = [...problems].sort((a, b) => (a.level === b.level ? 0 : a.level === 'ERROR' ? -1 : 1));
  const errors = problems.some(problem => problem.level === 'ERROR');
  return <div className="dq-problems" aria-live="polite">
    {(validating || stale) && <div className="muted dq-problem-status">{validating ? t('problems.validating') : t('problems.stale')}</div>}
    {sorted.map((problem, index) => <div key={index} className={`dq-problem is-${problem.level.toLowerCase()}`}>
      <Tag color={problem.level === 'ERROR' ? 'red' : 'gold'}>{problem.level === 'ERROR' ? t('problems.error') : t('problems.warning')}</Tag>
      <span className="mono dq-problem-at">{problemLocation(problem)}</span>
      <span className="dq-problem-message">{problem.message}</span>
      <button type="button" className="dq-link" onClick={() => onLocate(problem)}>{t('problems.locate')}</button>
    </div>)}
    <div className="dq-problem is-info"><Tag color="blue">{t('problems.info')}</Tag><span className="mono dq-problem-at">{t('problems.expansion')}</span>
      <span className="dq-problem-message">{errors ? t('problems.expansionHintAfterFix') : t('problems.expansionHint')}</span></div>
    {!validating && !stale && problems.length === 0 && <div className="dq-ok">{t('problems.passed')}</div>}
  </div>;
}

/** 保存：填写变更说明；显示当前警告（不阻止保存）。服务端仍做完整校验，失败的问题回到问题面板。 */
function SaveModal({ nextVersion, creating, draft, warnings, errors, stale, busy, onClose, onSave }: {
  nextVersion: number; creating: boolean; draft: TemplateDraft; warnings: TemplateProblem[]; errors: number; stale: boolean; busy: boolean;
  onClose: () => void; onSave: (note: string) => Promise<void>;
}) {
  const [note, setNote] = useState(''); const t = useT('data-query'), tCommon = useT('common');
  const missing = creating && !codePattern.test(draft.templateCode) ? t('save.codeInvalid') : !draft.templateName.trim() ? t('save.nameRequired')
    : !draft.dataSourceId ? t('save.dataSourceRequired') : null;
  return <Modal open wrapClassName="dq-modal" title={t('template.saveAs', { versionNo: nextVersion })} okText={t('save.ok')} cancelText={tCommon('action.cancel')} confirmLoading={busy} okButtonProps={{ disabled: !!missing }}
    onOk={() => void onSave(note)} onCancel={() => { if (!busy) onClose(); }} destroyOnHidden>
    <p>{t('save.notice', { versionNo: nextVersion })}</p>
    {missing && <Note tone="error">{missing}</Note>}
    {errors > 0 && <Note tone="error">{t('save.errors', { count: errors })}</Note>}
    {stale && <Note tone="warning">{t('save.stale')}</Note>}
    {warnings.length > 0 && <Note tone="warning">{t('save.warnings', { count: warnings.length })}<ul className="dq-warning-list">{warnings.map((warning, index) =>
      <li key={index}>{warning.message}</li>)}</ul></Note>}
    <Input.TextArea rows={3} maxLength={512} showCount value={note} onChange={event => setNote(event.target.value)} placeholder={t('save.notePlaceholder')} autoFocus />
  </Modal>;
}

function TemplateStatusModal({ detail, dirty, onClose, onDone, onAuthorizationChanged }: {
  detail: TemplateDetail; dirty: boolean; onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void;
}) {
  const command = useDataQueryCommand(onAuthorizationChanged); const t = useT('data-query'), tCommon = useT('common');
  const target = detail.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE'; const enabling = target === 'ACTIVE';
  const submit = async () => {
    const result = await command.run(dataQueryPaths.templateStatus(detail.templateId), 'POST', { status: target, expectedVersion: detail.version },
      value => decodeMutation(value, 'QUERY_TEMPLATE', detail.templateId), enabling ? t('templateStatus.enableFailed') : t('templateStatus.disableFailed'));
    if (result) onDone();
  };
  return <Modal open wrapClassName="dq-modal" title={enabling ? t('templateStatus.enableTitle', { code: detail.templateCode }) : t('templateStatus.disableTitle', { code: detail.templateCode })}
    okText={enabling ? t('templateStatus.confirmEnable') : t('templateStatus.confirmDisable')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ danger: target === 'INACTIVE', disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}>
    {command.conflict && <ConflictBanner subject="template" onReload={onDone} />}
    {command.formError && <Note tone="error">{command.formError}</Note>}
    <p>{enabling ? t('templateStatus.enableNotice', { versionNo: detail.currentVersionNo }) : t('templateStatus.disableNotice')}</p>
    {dirty && <Note tone="warning">{enabling ? t('templateStatus.dirtyEnable') : t('templateStatus.dirtyDisable')}</Note>}
  </Modal>;
}
