import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Checkbox, Input, Modal, Space } from 'antd';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { enumLabel, formatShortDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  AutoCheckCard, BusinessCard, Card, CommandAlert, DecisionRows, DueDiligenceCard, ExposureCard, Row,
} from './UnderwritingParts';
import {
  decisionBody, decisionFieldOf, decisionFormOf, decisionIssues, decodePreview, decodeUnderwriting, decodeWorkspace, newPageMode, permissionsOf,
  settlementDaysOf, submitActions, underwritingPaths, underwritingRoutes,
} from './underwriting';
import type { DecisionField, DecisionForm, DecisionIssue, Preview, Underwriting, Workspace } from './underwriting';
import { useUnderwritingCommand } from './useUnderwritingCommand';

interface PageProps { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/** 工作台（ops-bff 聚合）：输入、预览、当前与进行中记录。 */
export function useWorkspace(clientId: string) {
  const decode = useMemo(() => (value: unknown) => decodeWorkspace(value, clientId), [clientId]);
  return useDetail(underwritingPaths.workspace(clientId), decode);
}

/** 页头：面包屑、Client 名称 + 状态 / 版本、元信息。 */
export function UnderwritingHeader({ crumb, title, badges, meta }: { crumb: string; title: string; badges?: ReactNode; meta: (string | null | false)[] }) {
  const tNav = useT('nav');
  return <div className="uw-head"><div className="uw-grow">
    <nav className="uw-crumbs"><Link to={underwritingRoutes.list}>{tNav('resource.OPERATIONS_RISK_UNDERWRITING')}</Link> / {crumb}</nav>
    <h1 className="uw-title"><span className="uw-ellipsis">{title}</span>{badges}</h1>
    <div className="uw-meta">{meta.filter(Boolean).join(' · ')}</div></div></div>;
}

/** 输入未就绪的原因（工作台 inputStatus）。 */
function InputStatusAlert({ workspace }: { workspace: Workspace }) {
  const t = useT('risk');
  if (workspace.inputStatus === 'READY') return null;
  return <Alert showIcon type={workspace.inputStatus === 'SOURCE_UNAVAILABLE' ? 'warning' : 'info'} className="uw-alert"
    title={enumLabel(t, 'underwriting.inputStatus', workspace.inputStatus)} description={t('underwriting.inputStatus.blocksSubmit')} />;
}

function InputColumn({ workspace, preview, settlementDays, previewing }: { workspace: Workspace; preview: Preview | null; settlementDays: number | null; previewing: boolean }) {
  const input = workspace.input;
  return <div className="uw-column">
    <InputStatusAlert workspace={workspace} />
    {input && <><DueDiligenceCard input={input} /><BusinessCard input={input} />
      <ExposureCard exposure={preview?.estimatedExposure ?? null} unavailableReason={preview?.exposureUnavailableReason ?? null} input={input}
        settlementDays={settlementDays} frozen={false} loading={previewing} /></>}
  </div>;
}

/**
 * 新建（/risk/underwriting/new?clientId=）：原型 02 自动承保 / 原型 03 人工承保
 * <li>已有进行中版本时直接打开该版本；已有有效结论时提示走修订（同一 Client 只有一份有效结论）。</li>
 * <li>满足自动承保时默认展示标准模板与"一键自动承保"，可改为人工。</li>
 */
export function NewUnderwriting({ clientId, resources, onAuthorizationChanged }: PageProps & { clientId: string }) {
  const t = useT('risk'); const tMaster = useT('master-data');
  const workspace = useWorkspace(clientId);
  const [manual, setManual] = useState(false);
  if (workspace.loading && !workspace.data) return <Loading />;
  if (workspace.failure || !workspace.data) return <QueryFailure failure={workspace.failure} retry={workspace.refresh} />;
  const data = workspace.data;
  const permissions = permissionsOf(resources);
  const mode = newPageMode(data, permissions.manage, manual);
  if (mode.kind === 'redirect') return <Navigate to={underwritingRoutes.detail(mode.underwritingId)} replace />;
  const client = data.client;
  const header = <UnderwritingHeader crumb={t('underwriting.crumb.new')} title={client.displayName}
    badges={<span className="uw-badge is-info">{t('underwriting.status.new')}</span>}
    meta={[client.clientNo, client.jurisdictionCode, enumLabel(tMaster, 'client.enum.clientType', client.clientType)]} />;
  if (mode.kind === 'manual') return <ManualEditor workspace={data} record={null} header={header} onAuthorizationChanged={onAuthorizationChanged} onReload={workspace.refresh} />;
  return <div className="uw-page">{header}
    <div className="uw-split">
      <InputColumn workspace={data} preview={data.preview} settlementDays={null} previewing={false} />
      <div className="uw-column">
        {mode.kind === 'effective' && <Alert showIcon type="info" title={t('underwriting.effectiveExists', { version: mode.versionNo })}
          action={<Link to={underwritingRoutes.detail(mode.underwritingId)}>{t('underwriting.action.view')}</Link>} />}
        {mode.kind === 'readonly' && <Alert showIcon type="info" title={t('underwriting.readonlyNotice')} />}
        {mode.kind === 'auto' && data.preview && <AutoUnderwriting workspace={data} preview={data.preview} onManual={() => setManual(true)} onAuthorizationChanged={onAuthorizationChanged} />}
      </div>
    </div></div>;
}

/** 原型 02：判定清单 + 标准模板结论（只读）+ 改为人工 / 一键自动承保（U9，服务端按最新输入重判）。 */
function AutoUnderwriting({ workspace, preview, onManual, onAuthorizationChanged }: { workspace: Workspace; preview: Preview; onManual: () => void; onAuthorizationChanged: () => void }) {
  const t = useT('risk'); const navigate = useNavigate();
  const command = useUnderwritingCommand(onAuthorizationChanged);
  async function apply() {
    const created = await command.run(underwritingPaths.auto(workspace.client.clientId), 'POST', {}, value => decodeUnderwriting(value), t('underwriting.done.auto'));
    if (created) void navigate(underwritingRoutes.detail(created.underwritingId), { replace: true });
  }
  return <>
    <AutoCheckCard preview={preview} input={workspace.input} />
    <Card title={t('underwriting.decision.title')} extra={<span className="muted">{t('underwriting.decision.standardTemplate')}</span>}>
      <DecisionRows decision={{ observationDays: 30, observationTransactions: 100, requireDeposit: false, depositAmount: null, depositCurrency: null, depositBlocksCollection: false,
        suggestedSettlementDays: null, highRiskMcc: false, highRiskMccReason: null, limitNote: t('underwriting.decision.standardTemplateNote'), underwritingNote: null, handoverNote: null }} />
    </Card>
    <Card>
      <CommandAlert failure={command.failure} />
      <div className="uw-actions"><Button onClick={onManual}>{t('underwriting.auto.manual')}</Button>
        <Button type="primary" loading={command.saving} onClick={() => void apply()}>{t('underwriting.auto.apply')}</Button></div>
      <p className="uw-hint">{t('underwriting.auto.applyHint', { version: preview.ruleVersionNo ?? '?' })}</p>
    </Card></>;
}

/** 草稿（/risk/underwriting/:id，状态 DRAFT，有维护权限）：按 Client 读取最新输入后编辑。 */
export function DraftEditor({ record, header, onAuthorizationChanged, onChanged }: { record: Underwriting; header: ReactNode; onAuthorizationChanged: () => void; onChanged: () => void }) {
  const workspace = useWorkspace(record.clientId);
  if (workspace.loading && !workspace.data) return <Loading />;
  if (workspace.failure || !workspace.data) return <QueryFailure failure={workspace.failure} retry={workspace.refresh} />;
  return <ManualEditor workspace={workspace.data} record={record} header={header} onAuthorizationChanged={onAuthorizationChanged} onReload={onChanged} />;
}

const issueKey: Record<DecisionIssue, string> = { required: 'underwriting.validation.required', observationDaysRange: 'underwriting.validation.observationDays',
  observationTransactionsRange: 'underwriting.validation.observationTransactions', depositAmount: 'underwriting.validation.depositAmount',
  depositCurrency: 'underwriting.validation.depositCurrency', settlementDaysRange: 'underwriting.validation.settlementDays',
  reasonRequired: 'underwriting.validation.reasonRequired', tooLong: 'underwriting.validation.tooLong' };

/**
 * 原型 03：人工承保（新建或草稿）
 * <li>T+N 变化 400 ms 后调用预览（U11）刷新敞口；预览只读，ops-bff 按最新来源组装输入。</li>
 * <li>保存草稿：首次建草稿（U5）后进入版本地址，之后保存（U6，expectedVersion）。提交：HIGH 进入待复核，LOW / MEDIUM 提交即批准或拒绝。</li>
 * <li>结论字段按 B5 先在页面校验；服务端 422 的结论字段错误显示在对应字段下。</li>
 */
function ManualEditor({ workspace, record, header, onAuthorizationChanged, onReload }: { workspace: Workspace; record: Underwriting | null; header: ReactNode;
  onAuthorizationChanged: () => void; onReload: () => void }) {
  const t = useT('risk'); const navigate = useNavigate();
  const input = workspace.input;
  const networkHighRisk = input?.networkHighRiskMcc ?? false;
  const [draft, setDraft] = useState<Underwriting | null>(record);
  const [form, setForm] = useState<DecisionForm>(() => decisionFormOf(record?.decision ?? null, networkHighRisk, input?.volumeCurrency ?? null));
  const [shown, setShown] = useState<Partial<Record<DecisionField, DecisionIssue>>>({});
  const [declining, setDeclining] = useState(false);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const command = useUnderwritingCommand(onAuthorizationChanged);
  const preview = usePreview(workspace, settlementDaysOf(form.suggestedSettlementDays));
  const reviewLevel = preview.data?.reviewLevel ?? record?.reviewLevel ?? (input?.crr ?? 'MEDIUM');
  const actions = submitActions(reviewLevel);
  const serverIssues = useMemo(() => Object.fromEntries((command.failure?.fieldErrors ?? []).flatMap(error => {
    const field = decisionFieldOf(error.field); return field ? [[field, error.message]] : []; })) as Partial<Record<DecisionField, string>>, [command.failure]);
  const set = <K extends DecisionField>(field: K, value: DecisionForm[K]) => { setForm(current => ({ ...current, [field]: value })); setShown(current => ({ ...current, [field]: undefined })); };
  const error = (field: DecisionField) => shown[field] ? t(issueKey[shown[field]!], { max: field === 'underwritingNote' ? 2000 : 500 }) : serverIssues[field];

  function validate(mode: 'save' | 'submit') { const issues = decisionIssues(form, networkHighRisk, mode); setShown(issues); return Object.keys(issues).length === 0; }
  async function ensureDraft(): Promise<Underwriting | null> {
    if (draft) return draft;
    const created = await command.run(underwritingPaths.create(workspace.client.clientId), 'POST', { decision: decisionBody(form, networkHighRisk) },
      value => decodeUnderwriting(value), null);
    if (created) setDraft(created);
    return created;
  }
  async function save() {
    if (!validate('save')) return;
    if (!draft) {
      const created = await ensureDraft();
      if (created) void navigate(underwritingRoutes.detail(created.underwritingId), { replace: true });
      return;
    }
    const saved = await command.run(underwritingPaths.save(draft.underwritingId), 'PUT', { expectedVersion: draft.version, decision: decisionBody(form, networkHighRisk) },
      value => decodeUnderwriting(value, draft.underwritingId), t('underwriting.done.saved'));
    if (saved) { setDraft(saved); setSavedAt(new Date().toISOString()); }
  }
  async function submit(outcome: 'APPROVE' | 'DECLINE', declineReason?: string) {
    if (!validate('submit')) return false;
    const target = await ensureDraft();
    if (!target) return false;
    const result = await command.run(underwritingPaths.submission(target.underwritingId), 'POST', { expectedVersion: target.version, decision: decisionBody(form, networkHighRisk),
      outcome, ...(outcome === 'DECLINE' ? { declineReason } : {}) }, value => decodeUnderwriting(value, target.underwritingId),
    t(outcome === 'DECLINE' ? 'underwriting.done.declined' : reviewLevel === 'HIGH' ? 'underwriting.done.submittedForReview' : 'underwriting.done.approved'));
    if (!result) return false;
    if (record) onReload(); else void navigate(underwritingRoutes.detail(result.underwritingId), { replace: true });
    return true;
  }
  const blocked = !workspace.canSubmit;
  const conflict = command.failure?.code === 'RISK_UNDERWRITING_VERSION_CONFLICT';
  return <div className="uw-page">{header}
    {draft && (savedAt || draft.revisionReason) && <div className="uw-meta uw-draft-meta">{[savedAt && t('underwriting.savedAt', { time: formatShortDateTime(savedAt) }),
      draft.revisionReason && t('underwriting.history.revisionReason', { reason: draft.revisionReason })].filter(Boolean).join(' · ')}</div>}
    <div className="uw-split">
      <InputColumn workspace={workspace} preview={preview.data} settlementDays={settlementDaysOf(form.suggestedSettlementDays)} previewing={preview.loading} />
      <div className="uw-column">
        {preview.data && <AutoCheckCard preview={preview.data} input={input} />}
        <Card title={t('underwriting.decision.title')} extra={<span className="muted">{t('underwriting.decision.manual')}</span>}>
          <Row label={t('underwriting.decision.startingTierLabel')}>{t('underwriting.startingTier')}</Row>
          <Row label={t('underwriting.decision.observation')}>
            <div className="uw-inline-form">
              <Input className="uw-num" aria-label={t('underwriting.field.observationDays')} inputMode="numeric" maxLength={3} value={form.observationDays}
                status={error('observationDays') ? 'error' : undefined} onChange={event => set('observationDays', event.target.value)} />
              <span>{t('underwriting.decision.daysOr')}</span>
              <Input className="uw-num" aria-label={t('underwriting.field.observationTransactions')} inputMode="numeric" maxLength={6} value={form.observationTransactions}
                status={error('observationTransactions') ? 'error' : undefined} onChange={event => set('observationTransactions', event.target.value)} />
              <span>{t('underwriting.decision.transactionsFirst')}</span></div>
            <FieldError text={error('observationDays') ?? error('observationTransactions')} /></Row>
          <Row label={t('underwriting.decision.deposit')}>
            <div className="uw-inline-form">
              <Checkbox checked={form.requireDeposit} onChange={event => set('requireDeposit', event.target.checked)}>{t('underwriting.decision.require')}</Checkbox>
              {form.requireDeposit && <Space.Compact className="uw-money">
                <Input className="uw-currency" aria-label={t('underwriting.field.depositCurrency')} maxLength={3} value={form.depositCurrency}
                  status={error('depositCurrency') ? 'error' : undefined} onChange={event => set('depositCurrency', event.target.value.toUpperCase())} />
                <Input aria-label={t('underwriting.field.depositAmount')} inputMode="decimal" maxLength={25} value={form.depositAmount}
                  status={error('depositAmount') ? 'error' : undefined} onChange={event => set('depositAmount', event.target.value)} /></Space.Compact>}
              {form.requireDeposit && <Checkbox checked={form.depositBlocksCollection} onChange={event => set('depositBlocksCollection', event.target.checked)}>
                {t('underwriting.field.depositBlocksCollection')}</Checkbox>}</div>
            <FieldError text={error('depositAmount') ?? error('depositCurrency')} /></Row>
          <Row label={t('underwriting.decision.suggestedSettlementDays')}>
            <Input className="uw-tn" aria-label={t('underwriting.field.suggestedSettlementDays')} prefix="T+" inputMode="numeric" maxLength={2} value={form.suggestedSettlementDays}
              status={error('suggestedSettlementDays') ? 'error' : undefined} onChange={event => set('suggestedSettlementDays', event.target.value)} />
            <FieldError text={error('suggestedSettlementDays')} /></Row>
          <Row label={t('underwriting.decision.networkRegistration')}>
            <Checkbox checked={form.highRiskMcc} onChange={event => set('highRiskMcc', event.target.checked)}>
              {t('underwriting.decision.registrationNeeded')}<span className="muted"> {t(networkHighRisk ? 'underwriting.decision.prefilledHighRisk' : 'underwriting.decision.prefilledNotHighRisk')}</span></Checkbox>
            {form.highRiskMcc !== networkHighRisk && <Input.TextArea className="uw-gap" aria-label={t('underwriting.field.highRiskMccReason')} placeholder={t('underwriting.field.highRiskMccReason')}
              autoSize={{ minRows: 2, maxRows: 4 }} maxLength={500} value={form.highRiskMccReason} status={error('highRiskMccReason') ? 'error' : undefined}
              onChange={event => set('highRiskMccReason', event.target.value)} />}
            <FieldError text={error('highRiskMccReason')} /></Row>
          <NoteRow field="limitNote" max={500} form={form} set={set} error={error('limitNote')} />
          <NoteRow field="underwritingNote" max={2000} form={form} set={set} error={error('underwritingNote')} />
          <NoteRow field="handoverNote" max={500} form={form} set={set} error={error('handoverNote')} />
          <div className="uw-callout">{t('underwriting.decision.depositHint')}</div>
        </Card>
        <Card>
          <CommandAlert failure={command.failure} />
          {conflict && <Button className="uw-gap-bottom" onClick={onReload}>{t('underwriting.action.reload')}</Button>}
          {blocked && <Alert showIcon type="info" className="uw-alert" title={t('underwriting.submitBlocked')} />}
          <div className="uw-actions">
            <Button onClick={() => void save()} loading={command.saving} disabled={!input}>{t('underwriting.saveDraft')}</Button>
            {actions.canDecline && <Button danger onClick={() => { if (validate('submit')) setDeclining(true); }} disabled={blocked || command.saving}>{t('underwriting.decline')}</Button>}
            <Button type="primary" onClick={() => void submit('APPROVE')} loading={command.saving} disabled={blocked}>{t('underwriting.' + actions.submitKey)}</Button>
          </div>
          <p className="uw-hint">{reviewLevel === 'HIGH' ? t('underwriting.submitHint.high') : t('underwriting.submitHint.standard')}</p>
        </Card>
      </div>
    </div>
    {declining && <DeclineModal saving={command.saving} onCancel={() => setDeclining(false)} onConfirm={async reason => { if (await submit('DECLINE', reason)) setDeclining(false); }} />}
  </div>;
}

function FieldError({ text }: { text: string | undefined }) { return text ? <div className="uw-field-error" role="alert">{text}</div> : null; }
function NoteRow({ field, max, form, set, error }: { field: 'limitNote' | 'underwritingNote' | 'handoverNote'; max: number; form: DecisionForm;
  set: (field: DecisionField, value: string) => void; error: string | undefined }) {
  const t = useT('risk');
  return <Row label={t('underwriting.decision.' + field)}><Input.TextArea aria-label={t('underwriting.decision.' + field)} autoSize={{ minRows: 1, maxRows: 5 }} maxLength={max} showCount={max > 500}
    value={form[field]} status={error ? 'error' : undefined} onChange={event => set(field, event.target.value)} /><FieldError text={error} /></Row>;
}

/** 拒绝（LOW / MEDIUM 承保员直接拒绝）：原因必填 ≤ 500。 */
function DeclineModal({ saving, onCancel, onConfirm }: { saving: boolean; onCancel: () => void; onConfirm: (reason: string) => void }) {
  const t = useT('risk'); const [reason, setReason] = useState(''); const [touched, setTouched] = useState(false);
  const invalid = !reason.trim();
  return <Modal open title={t('underwriting.decline')} okText={t('underwriting.decline')} okButtonProps={{ danger: true, loading: saving }} onCancel={onCancel}
    onOk={() => { setTouched(true); if (!invalid) onConfirm(reason.trim()); }}>
    <p className="uw-hint">{t('underwriting.declineHint')}</p>
    <Input.TextArea autoFocus aria-label={t('underwriting.declineReason')} placeholder={t('underwriting.declineReason')} autoSize={{ minRows: 3, maxRows: 6 }} maxLength={500} showCount
      value={reason} status={touched && invalid ? 'error' : undefined} onChange={event => setReason(event.target.value)} />
    {touched && invalid && <div className="uw-field-error">{t('underwriting.validation.required')}</div>}
  </Modal>;
}

/** 预览（U11）：T+N 变化后防抖刷新；首次沿用工作台预览；失败保留上一次结果。 */
function usePreview(workspace: Workspace, settlementDays: number | null) {
  const [data, setData] = useState<Preview | null>(workspace.preview);
  const [loading, setLoading] = useState(false);
  const first = useRef(true);
  useEffect(() => {
    if (first.current && settlementDays === null) { first.current = false; return; }
    first.current = false;
    if (!workspace.input) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      setLoading(true);
      session.request(underwritingPaths.previews, { method: 'POST', signal: controller.signal, headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ clientId: workspace.client.clientId, suggestedSettlementDays: settlementDays }) }, decodePreview)
        .then(value => { if (!controller.signal.aborted) setData(value); }).catch(() => undefined)
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }, 400);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [workspace.client.clientId, workspace.input, settlementDays]);
  return { data, loading };
}
