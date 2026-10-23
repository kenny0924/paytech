import { ruleForVariableEditing } from '../risk/variableEditing';
import type { ConsoleResource } from '../api/types';
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Drawer, Input, Modal, Space, Spin } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import { commandFailureMessage, hasRiskAction, reduceRiskCommandResult, requireRiskAction, RiskActionUnavailableError, riskCommandPath, riskFieldErrorLabel, submitRiskCommand } from '../risk/riskPageModel';
import type { RiskCommandResult, RiskCommandTarget, RiskPageCommandState } from '../risk/riskPageModel';
import { riskDecisionApi } from '../risk/riskDecisionApi';
import type { RuleSetDetail, RuleSetListItem } from '../risk/riskApiModels';
import { matchAuthoringContext, CHECKOUT_PREPARE_AUTHORING_V1 } from '../risk/riskAuthoringCatalog';
import type { AuthoringMatch } from '../risk/riskAuthoringCatalog';
import { detailToEditor } from '../risk/ruleBuilder';
import { loadObservedRuleSetHead, ObservedHeadChangedError, requireSelectedObservedHead, requireUnusedRuleSetCode } from '../risk/ruleSetHead';
import { RuleSetExplorer } from '../risk/RuleSetExplorer';
import { RuleSetTab } from '../risk/RuleSetTab';
import { ContentHash, RuleSetDetails } from '../risk/RuleSetDetails';
import { RuleSetEditor } from '../risk/RuleSetEditor';
import { randomCommand, saveSizeState, validateKey } from '../risk/riskCommands';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { useT } from '../i18n/react';
import { parseRiskNavigation, riskNavigationSearch, withRiskView } from '../risk/riskNavigation';
import type { RiskNavigation } from '../risk/riskNavigation';
import '../risk/riskRuleConfiguration.css';

export interface RiskRuleConfigurationProps {
  resources: readonly ConsoleResource[];
  actorId: string;
  onAuthorizationChanged: () => void;
}

function RuleConfigurationUnsavedGuard({ dirty }: { dirty: boolean }) {
  const { modal } = App.useApp(); const t = useT('risk');
  const confirmLeave = useCallback(() => new Promise<boolean>((resolve) => {
    let settled = false;
    const settle = (confirmed: boolean) => {
      if (settled) return;
      settled = true; resolve(confirmed);
    };
    modal.confirm({
      title: t('rulePage.leave.title'),
      content: t('rulePage.leave.content'),
      okText: t('rulePage.leave.ok'), cancelText: t('rulePage.leave.cancel'), okButtonProps: { danger: true },
      onOk: () => settle(true), onCancel: () => settle(false),
    });
  }), [modal, t]);
  useUnsavedRuleGuard(dirty, confirmLeave);
  return null;
}

/** 导航通过 Router 完成后才重建页面内存，取消离开时保留原编辑与命令。 */
export function RiskRuleConfiguration(props: RiskRuleConfigurationProps) {
  const location = useLocation(); const navigate = useNavigate();
  const navigation = useMemo(() => parseRiskNavigation(location.search), [location.search]);
  const canonical = riskNavigationSearch(navigation);
  useEffect(() => {
    if (canonical !== location.search) void navigate({ search: canonical }, { replace: true });
  }, [canonical, location.search, navigate]);
  return canonical === location.search
    ? <RiskRuleConfigurationView key={location.key} {...props} navigation={navigation} /> : <Spin />;
}

function RiskRuleConfigurationView({ resources, onAuthorizationChanged, navigation }: RiskRuleConfigurationProps & { navigation: RiskNavigation }) {
  const location = useLocation(); const navigate = useNavigate(); const t = useT('risk'), tNav = useT('nav');
  const [state, setState] = useState<RiskPageCommandState>(() => ({ activeTab: 'rule-sets', editor: null,
    confirmation: null, selectedRuleSetVersionId: null, selectedCandidateReleaseId: null, pendingCommand: null, fieldErrors: [] }));
  const current = useRef(state);
  function update(change: (value: RiskPageCommandState) => RiskPageCommandState) {
    current.current = change(current.current); setState(current.current);
  }
  const live = useRef({ resources, onAuthorizationChanged });
  useLayoutEffect(() => { live.current = { resources, onAuthorizationChanged }; }, [resources, onAuthorizationChanged]);
  const [revision, setRevision] = useState(0);
  const [detail, setDetail] = useState<RuleSetDetail | null>(null);
  const [baseDetail, setBaseDetail] = useState<RuleSetDetail | null>(null);
  const [editorBaseline, setEditorBaseline] = useState<string | null>(null);
  const [observedDetail, setObservedDetail] = useState<RuleSetDetail | null>(null);
  const [catalog, setCatalog] = useState<AuthoringMatch>({ editable: false, reason: t('rulePage.catalogLoading') });
  const [detailLoading, setDetailLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [failure, setFailure] = useState<Exclude<RiskCommandResult, { kind: 'SUCCESS' }> | null>(null);
  const [newCodeOpen, setNewCodeOpen] = useState(false);
  const [newCode, setNewCode] = useState('');
  const [completedNavigation, setCompletedNavigation] = useState<RiskNavigation | null>(null);
  const headRequest = useRef<AbortController | null>(null);
  const mounted = useRef(true);
  const inFlight = useRef(false);
  const pendingTarget = useRef<RiskCommandTarget | null>(null);
  const commandChangeDisabled = busy || !!state.pendingCommand;
  function commandChangeBlocked() { return busy || inFlight.current || current.current.pendingCommand !== null; }
  const onNavigate = useCallback((next: RiskNavigation, replace = false) => {
    const search = riskNavigationSearch(next);
    if (search === location.search) { setRevision(value => value + 1); return; }
    void navigate({ search }, { replace, state: replace ? location.state : { riskOrigin: navigation.view, riskSource: location.pathname } });
  }, [location.search, location.state, navigate, navigation.view]);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; headRequest.current?.abort(); }; }, []);
  function readFailure(error: unknown) {
    if (error instanceof ApiError && error.status === 403) live.current.onAuthorizationChanged();
    setNotice(error instanceof ApiError ? `${error.message}${error.requestId ? ` · Request ID: ${error.requestId}` : ''}`
      : error instanceof ObservedHeadChangedError ? error.message : t('ruleSetExplorer.loadFailed'));
  }
  function clearNavigationFeedback() {
    setNotice(''); setFailure(value => value?.kind === 'UNKNOWN_RESULT' ? value : null);
  }
  useEffect(() => {
    if (!['list', 'detail', 'edit', 'create'].includes(navigation.view)) return;
    const controller = new AbortController();
    riskDecisionApi.authoringContext(navigation.filters.pointCode ?? 'CHECKOUT_PREPARE', controller.signal)
      .then(value => { if (!controller.signal.aborted) setCatalog(matchAuthoringContext(value)); })
      .catch(error => { if (!controller.signal.aborted) { setCatalog({ editable: false, reason: t('rulePage.catalogUnavailable') }); readFailure(error); } });
    return () => controller.abort();
  }, [revision, navigation.view, navigation.filters.pointCode]);
  useEffect(() => {
    if (!['detail', 'edit', 'create'].includes(navigation.view) || !navigation.selectedRuleSetCode || current.current.editor) return;
    const controller = new AbortController(); setDetailLoading(true);
    const code = navigation.selectedRuleSetCode;
    loadObservedRuleSetHead(riskDecisionApi, navigation.filters.pointCode!, code, controller.signal)
      .then(async head => {
        if (controller.signal.aborted) return;
        if (navigation.view === 'create') requireUnusedRuleSetCode(head);
        else if (!head) { setNotice(t('rulePage.notFoundBack')); return; }
        if (navigation.view === 'detail') {
          setDetail(head); update(value => ({ ...value, selectedRuleSetVersionId: head!.ruleSetVersionId }));
        } else {
          const editor = head ? { ...detailToEditor(await ruleForVariableEditing(head,await riskDecisionApi.authoringContext(head.pointCode,controller.signal),controller.signal)), changeReason: '' }
            : { pointCode: navigation.filters.pointCode ?? 'CHECKOUT_PREPARE', ruleSetCode: code, ruleSetName: '', changeReason: '', rules: [] };
          if(controller.signal.aborted)return;
          setBaseDetail(head); setEditorBaseline(JSON.stringify(editor));
          update(value => ({ ...value, editor }));
        }
      }).catch(error => { if (!controller.signal.aborted) readFailure(error); })
      .finally(() => { if (!controller.signal.aborted) setDetailLoading(false); });
    return () => controller.abort();
  }, [navigation, revision]);
  function openDelete(row: RuleSetListItem) {
    if (commandChangeBlocked() || row.inUse || !hasRiskAction(live.current.resources, 'DELETE')) return;
    clearNavigationFeedback();
    update(value => ({ ...value, confirmation: { action: 'DELETE', ruleSetId: row.ruleSetId,
      ruleSetCode: row.ruleSetCode, ruleSetName: row.ruleSetName,
      payload: JSON.stringify({ expectedVersion: row.managementVersion }) }, fieldErrors: [] }));
  }
  async function openLatest(selected: RuleSetDetail, freeze = false) {
    if (freeze && selected.rules.some(rule => !rule.action || rule.additionalActions.length > 0)) { setNotice(t('rulePage.cannotFreeze')); return; }
    if (commandChangeBlocked() || !catalog.editable || selected.status === 'RETIRED' || !hasRiskAction(live.current.resources, freeze ? 'FREEZE' : 'SAVE')) return;
    setBusy(true); setNotice(''); setFailure(null); pendingTarget.current = null; headRequest.current?.abort();
    const controller = new AbortController(); headRequest.current = controller;
    try {
      const observed = await loadObservedRuleSetHead(riskDecisionApi, selected.pointCode, selected.ruleSetCode, controller.signal);
      const head = requireSelectedObservedHead(selected, observed);
      if (controller.signal.aborted || commandChangeBlocked()) return;
      if (freeze) {
        if (head.status !== 'DRAFT') throw new ObservedHeadChangedError();
        update(value => ({ ...value, confirmation: { action: 'FREEZE', ruleSetVersionId: head.ruleSetVersionId,
          payload: JSON.stringify({ expectedContentHash: head.contentHash }) }, pendingCommand: null, fieldErrors: [] }));
      } else { onNavigate(withRiskView(navigation, 'edit', head.ruleSetCode)); }
    } catch (error) { if (!controller.signal.aborted) readFailure(error); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  }
  async function openNew() {
    if (commandChangeBlocked() || !catalog.editable || !hasRiskAction(live.current.resources, 'SAVE')) return;
    try { if(!/^[A-Z][A-Z0-9_]{0,63}$/.test(newCode.trim()))throw new Error('invalid code'); }
    catch { setNotice(t('ruleBuilder.codeFormat')); return; }
    setBusy(true); setNotice(''); setFailure(null); pendingTarget.current = null; headRequest.current?.abort();
    const controller = new AbortController(); headRequest.current = controller;
    try {
      const pointCode = navigation.filters.pointCode ?? 'CHECKOUT_PREPARE';
      const observed = await loadObservedRuleSetHead(riskDecisionApi, pointCode, newCode.trim(), controller.signal);
      requireUnusedRuleSetCode(observed);
      if (controller.signal.aborted || commandChangeBlocked()) return;
      setNewCodeOpen(false);
      onNavigate({ ...withRiskView(navigation, 'create', newCode.trim()), filters: { ...navigation.filters, pointCode } });
    } catch (error) { if (!controller.signal.aborted) readFailure(error); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  }
  async function runCommand(target: RiskCommandTarget, payload: string, retry = false) {
    if (inFlight.current || busy) return;
    const previousPending = current.current.pendingCommand;
    const previousTarget = pendingTarget.current;
    const path = riskCommandPath(target);
    // 未决命令只能显式重试原动作和原请求；编辑内容或最新 Head 不能证明上次未提交。
    if (retry ? !previousPending || previousTarget?.action !== target.action || previousPending.path !== path || previousPending.payload !== payload
      : previousPending !== null) { setNotice(t('rulePage.retryFirst')); return; }
    try { requireRiskAction(live.current.resources, target.action); }
    catch (error) { if (error instanceof RiskActionUnavailableError) { live.current.onAuthorizationChanged(); setNotice(error.message); } return; }
    if ((target.action === 'SAVE' || target.action === 'FREEZE') && !catalog.editable) { setNotice(t('rulePage.catalogIncompatible')); return; }
    if (target.action === 'SAVE' && saveSizeState(payload) === 'TOO_LARGE') { setNotice(t('rulePage.saveTooLarge')); return; }
    const previousFailure = failure;
    inFlight.current = true; setBusy(true); setNotice('');
    setFailure(value => value?.kind === 'UNKNOWN_RESULT' ? value : null);
    update(value => ({ ...value, fieldErrors: [] }));
    try {
      if (target.action === 'SAVE' && !retry) {
        const editor = current.current.editor;
        if (!editor) return;
        const controller = new AbortController(); headRequest.current?.abort(); headRequest.current = controller;
        const observed = await loadObservedRuleSetHead(riskDecisionApi, editor.pointCode, editor.ruleSetCode, controller.signal);
        if (baseDetail) requireSelectedObservedHead(baseDetail, observed); else requireUnusedRuleSetCode(observed);
        if (controller.signal.aborted) return;
      }
      const pending = retry && previousPending ? previousPending : target.action === 'VALIDATE_CANDIDATE'
        ? Object.freeze({ path, payload, key: validateKey(target.releaseId, 1) }) : randomCommand(path, payload, previousPending);
      pendingTarget.current = target;
      update(value => ({ ...value, pendingCommand: pending }));
      const result = await submitRiskCommand(riskDecisionApi, target, pending, () => live.current.resources,
        () => { if (mounted.current) live.current.onAuthorizationChanged(); });
      if (!mounted.current) return;
      update(value => reduceRiskCommandResult(value, result));
      if (result.kind === 'SUCCESS') {
        pendingTarget.current = null; setFailure(null); setNotice(t('rulePage.done'));
        if (result.action === 'SAVE') {
          setBaseDetail(null); setEditorBaseline(null);
          setCompletedNavigation(withRiskView(navigation, 'detail', navigation.selectedRuleSetCode));
        }
        if (result.action === 'DELETE') setNotice(t('rulePage.deleted'));
        setRevision(value => value + 1);
      } else {
        setFailure(result);
        if (result.kind === 'HTTP_FAILURE') {
          pendingTarget.current = null;
          if (result.status === 404) setRevision(value => value + 1);
        }
      }
    } catch (error) {
      if (!mounted.current) return;
      pendingTarget.current = previousTarget;
      update(value => ({ ...value, pendingCommand: previousPending })); setFailure(previousFailure);
      if (error instanceof RiskActionUnavailableError) {
        setNotice(error.message);
      } else if (!(error instanceof DOMException && error.name === 'AbortError')) readFailure(error);
    } finally { inFlight.current = false; if (mounted.current) setBusy(false); }
  }
  async function refreshObserved() {
    const selected = current.current.editor ?? baseDetail ?? detail;
    if (!selected || busy) return;
    setBusy(true); headRequest.current?.abort(); const controller = new AbortController(); headRequest.current = controller;
    try {
      const observed = await loadObservedRuleSetHead(riskDecisionApi, selected.pointCode, selected.ruleSetCode, controller.signal);
      if (!controller.signal.aborted) { setObservedDetail(observed); if (!observed) setNotice(t('rulePage.notFoundRefresh')); }
    } catch (error) { if (!controller.signal.aborted) readFailure(error); }
    finally { if (!controller.signal.aborted) setBusy(false); }
  }
  function confirmCommand() {
    const confirmation = current.current.confirmation;
    if (confirmation) void runCommand(confirmation, confirmation.payload);
  }
  function retryCommand() {
    const pending = current.current.pendingCommand; const target = pendingTarget.current;
    if (pending && target) void runCommand(target, pending.payload, true);
  }
  const editorDirty = state.editor !== null
    && (editorBaseline === null || JSON.stringify(state.editor) !== editorBaseline);
  const unsavedDirty = editorDirty || state.pendingCommand !== null;
  useEffect(() => {
    if (completedNavigation && !unsavedDirty) {
      setCompletedNavigation(null);
      onNavigate(completedNavigation, true);
    }
  }, [completedNavigation, unsavedDirty, onNavigate]);
  const failureAlert = failure && <Alert type={failure.kind === 'UNKNOWN_RESULT' ? 'warning' : 'error'} showIcon title={commandFailureMessage(failure)}
    description={<>
      {failure.kind === 'HTTP_FAILURE' && failure.status === 422 && failure.fieldErrors.length > 0 && <ul>{failure.fieldErrors.map((error, index) => <li key={index}>{t('flowPanel.outputValue', { name: riskFieldErrorLabel(error.field), value: error.message })}</li>)}</ul>}
      {failure.requestId && <p>Request ID: {failure.requestId}</p>}
    </>}
    action={<Space wrap>{failure.kind === 'UNKNOWN_RESULT' && state.pendingCommand && pendingTarget.current && hasRiskAction(resources, pendingTarget.current.action)
      && <Button disabled={busy} onClick={retryCommand}>{t('rulePage.retrySame')}</Button>}
      {failure.kind === 'HTTP_FAILURE' && failure.status === 409 && failure.action === 'DELETE'
        && <Button disabled={busy} onClick={() => { update(value => ({ ...value, confirmation: null })); setFailure(null); setRevision(value => value + 1); }}>{t('rulePage.refreshList')}</Button>}
      {failure.kind === 'HTTP_FAILURE' && failure.status === 409 && failure.action !== 'DELETE' && (state.editor || baseDetail || detail)
        && <Button disabled={busy} onClick={() => void refreshObserved()}>{t('rulePage.viewCurrent')}</Button>}
      {failure.kind === 'UNKNOWN_RESULT' && failure.action !== 'DELETE' && <Button disabled={busy} onClick={() => void refreshObserved()}>{t('rulePage.viewCurrent')}</Button>}
    </Space>} />;
  const editing = navigation.view === 'edit' || navigation.view === 'create';
  return <div className="risk-rule-configuration"><RuleConfigurationUnsavedGuard dirty={unsavedDirty} />
    {!editing && <div className="page-heading"><h1>{tNav('resource.OPERATIONS_RISK_RULE_CONFIGURATION')}</h1>
      {navigation.view === 'list' && hasRiskAction(resources, 'SAVE') && <Button type="primary" disabled={commandChangeDisabled || !catalog.editable}
        onClick={() => { if (commandChangeBlocked()) return; setNewCode(''); setNewCodeOpen(true); }}>{t('ruleEditor.createTitle')}</Button>}
    </div>}
    {notice && <Alert type="warning" showIcon title={notice} closable onClose={() => setNotice('')} />}
    {!state.confirmation && navigation.view !== 'detail' && failureAlert}
    {['list', 'edit', 'create'].includes(navigation.view) && !catalog.editable && <Alert type="warning" title={catalog.reason} action={<Button onClick={() => setRevision(value => value + 1)}>{t('rulePage.refreshCatalog')}</Button>} />}
    {['history','version','references'].includes(navigation.view) ? <RuleSetExplorer navigation={navigation} onNavigate={onNavigate} onAuthorizationChanged={onAuthorizationChanged} />
      : navigation.view === 'detail' ? <>{failureAlert}{detailLoading ? <Spin /> : detail ? <RuleSetEditor mode="read" detail={detail} editor={detailToEditor(detail)} base={detail} catalog={catalog.editable ? catalog.catalog : CHECKOUT_PREPARE_AUTHORING_V1} disabled commandPending={false} saving={false} fieldErrors={[]} onChange={() => {}} onSave={() => {}} />
        : <Space><Button onClick={() => setRevision(value => value + 1)}>{t('rulePage.retryDetail')}</Button></Space>}</>
      : editing ? state.editor ? <RuleSetEditor editor={state.editor} base={baseDetail} catalog={catalog.editable ? catalog.catalog : CHECKOUT_PREPARE_AUTHORING_V1}
      disabled={busy || !catalog.editable || !hasRiskAction(resources, 'SAVE')} commandPending={!!state.pendingCommand} saving={busy} dirty={editorDirty} fieldErrors={state.fieldErrors}
      onChange={editor => { clearNavigationFeedback(); update(value => ({ ...value, editor, fieldErrors: [] })); }}
      onSave={payload => void runCommand({ action: 'SAVE' }, payload)} />
      : <Space>{detailLoading && <Spin />}</Space>
      : <RuleSetTab revision={revision} navigation={navigation} resources={resources} canEdit={catalog.editable} disabled={commandChangeDisabled}
        onNavigate={onNavigate} onReadFailure={() => live.current.onAuthorizationChanged()}
        onSelect={row => { if (!commandChangeBlocked()) onNavigate(withRiskView(navigation, 'detail', row.ruleSetCode)); }}
        onEdit={row => { if (!commandChangeBlocked() && hasRiskAction(live.current.resources, 'SAVE') && catalog.editable) onNavigate(withRiskView(navigation, 'edit', row.ruleSetCode)); }}
        onDelete={openDelete}
        onHistory={row => onNavigate({ ...navigation, view: 'history', selectedRuleSetCode: row.ruleSetCode, rootId: row.ruleSetId, maxVersionNo: row.versionNo, historyPage: 1, historySize: 10, refPage: 1, refSize: 10 })}
        onReferences={row => onNavigate({ ...navigation, view: 'references', selectedRuleSetCode: row.ruleSetCode, rootId: row.ruleSetId, maxVersionNo: row.versionNo, historyPage: 1, historySize: 10, refPage: 1, refSize: 10 })}
        onFreeze={row => { if (commandChangeBlocked() || !hasRiskAction(live.current.resources, 'FREEZE') || !catalog.editable) return; return riskDecisionApi.ruleSet(row.ruleSetVersionId).then(value => { setDetail(value); return openLatest(value, true); }).catch(readFailure); }} />}
    <Drawer open={!!observedDetail} size={900} zIndex={1200} title={t('rulePage.currentReadonly')} onClose={() => setObservedDetail(null)}>{observedDetail && <RuleSetDetails detail={observedDetail} />}</Drawer>
    <Modal open={newCodeOpen} title={t('ruleEditor.createTitle')} okText={t('rulePage.checkCode')} cancelText={t('ruleEditor.cancel')} confirmLoading={busy}
      onOk={() => void openNew()} onCancel={() => { if (!commandChangeBlocked()) setNewCodeOpen(false); }}
      okButtonProps={{ disabled: commandChangeDisabled || !hasRiskAction(resources, 'SAVE') || !catalog.editable }}>
      <p>{t('rulePage.newCodeHint')}</p>
      <label htmlFor="risk-new-code">{t('fieldError.ruleSetCode')}</label><Input id="risk-new-code" value={newCode} autoComplete="off" maxLength={64} onChange={event => setNewCode(event.target.value)} disabled={busy} />
      {notice && <Alert type="warning" title={notice} />}
    </Modal>
    <Modal open={!!state.confirmation} title={state.confirmation?.action === 'DELETE' ? t('rulePage.confirmDeleteTitle') : t('rulePage.confirmFreezeTitle')}
      okText={state.confirmation?.action === 'DELETE' ? t('rulePage.deleteRuleSet') : t('rulePage.confirmFreeze')} cancelText={t('ruleEditor.cancel')} confirmLoading={busy} closable={!commandChangeDisabled} keyboard={!commandChangeDisabled} mask={{ closable: !commandChangeDisabled }}
      onOk={confirmCommand} onCancel={() => { if (!commandChangeBlocked()) { pendingTarget.current = null; setFailure(null); update(value => ({ ...value, confirmation: null, pendingCommand: null, fieldErrors: [] })); } }}
      cancelButtonProps={{ disabled: commandChangeDisabled }}
      okButtonProps={{ danger: state.confirmation?.action === 'DELETE', disabled: commandChangeDisabled || !state.confirmation || !hasRiskAction(resources, state.confirmation.action)
        || (state.confirmation.action === 'FREEZE' && !catalog.editable) }}>
      {state.confirmation?.action === 'DELETE' ? <><p><strong>{state.confirmation.ruleSetName}</strong></p><p>{state.confirmation.ruleSetCode}</p><p>{t('rulePage.deleteHint')}</p></>
        : <>{detail && <><p>{detail.ruleSetCode}</p><ContentHash hash={detail.contentHash} /></>}<p>{t('rulePage.freezeHint')}</p></>}
      {notice && <Alert type="warning" title={notice} />}{failureAlert}
    </Modal>
  </div>;
}
