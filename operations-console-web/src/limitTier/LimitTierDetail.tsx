import { useMemo, useState } from 'react';
import { Alert, Button, Checkbox, Input, Modal, Select } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { enumLabel, formatDate, formatShortDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { Card, Row } from '../underwriting/UnderwritingParts';
import { useLimitTierCommand } from './useLimitTierCommand';
import {
  CheckIcon, CommandAlert, CurrentTierCard, DueBadge, HistoryCard, ObservationCard, QuotaCard, TierBadge, UnderwritingLink, changeTitle, sourceText,
} from './LimitTierParts';
import {
  approvalAccess, commentIssue, decisionPath, decodeWorkspace, emptyManualState, goLiveReady, limitTierPaths, limitTierRoutes, manualChecks, pendingOf,
  permissionsOf, requestBody, requestIssues, requestableTargets, restoreCeiling, sourceChangeOf, systemCheckSource,
} from './limitTier';
import type { ManualState, Permissions, RequestForm, RequestIssue, SystemCheck, SystemCheckFacts, Tier, TierChange, TierDetail, Workspace } from './limitTier';

interface Props { clientId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/**
 * Client 档位页（/risk/limit-tiers/:clientId，原型 02—05）
 * <li>待审批：当前档位 + 冻结的申请内容，右侧审批区（申请人本人禁用并可撤回，普通审批人遇高级审批禁用）。</li>
 * <li>验证档：额度表 + 上线复核清单（系统项只读、人工项勾选、上线说明必填，满足前提交禁用）。</li>
 * <li>观察档：观察期进度 + 额度表，升到标准档 / 延长观察期 / 降为受限档；标准档：降为受限档；受限档：受限效果 + 申请恢复。</li>
 * <li>无档位：说明额度为零（承保批准后自动进入验证档）。所有状态都显示变更历史。</li>
 */
export function LimitTierDetail({ clientId, resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('risk');
  const decode = useMemo(() => (value: unknown) => decodeWorkspace(value, clientId), [clientId]);
  const workspace = useDetail(limitTierPaths.workspace(clientId), decode);
  if (workspace.loading && !workspace.data) return <Loading />;
  if (workspace.failure || !workspace.data) return <QueryFailure failure={workspace.failure} retry={workspace.refresh} />;
  const data = workspace.data;
  const permissions = permissionsOf(resources);
  const pending = pendingOf(data.history);
  const detail = data.tier;
  const context = { permissions, actorId, onAuthorizationChanged, onChanged: workspace.refresh };
  return <div className="uw-page">
    <DetailHeader workspace={data} pending={pending} />
    {!detail ? <div className="uw-split"><div className="uw-column">
      <Alert showIcon type="info" title={t('limitTier.tier.NONE')} description={t('limitTier.noTierHint')} />
      <Card title={t('limitTier.current.underwriting')}><UnderwritingLink underwriting={data.underwriting} /></Card></div>
      <div className="uw-column"><HistoryCard history={data.history} /></div></div>
      : <div className="uw-split">
        <div className="uw-column">
          <CurrentTierCard detail={detail} history={data.history} underwriting={data.underwriting} />
          {pending ? <PendingRequestCard change={pending} /> : <>
            {detail.observation && <ObservationCard observation={detail.observation} />}
            {detail.tier === 'RESTRICTED' ? <RestrictedCard /> : <QuotaCard detail={detail} />}</>}
        </div>
        <div className="uw-column">
          {pending ? <ApprovalPanel key={pending.changeId + ':' + pending.version} change={pending} {...context} />
            : <NextStep key={detail.version} workspace={data} detail={detail} {...context} />}
          <HistoryCard history={data.history} />
        </div>
      </div>}
  </div>;
}

/** 页头：面包屑、名称 + 档位 / 到期 / 待审批徽标、元信息。 */
function DetailHeader({ workspace, pending }: { workspace: Workspace; pending: TierChange | null }) {
  const t = useT('risk'), tNav = useT('nav'), tMaster = useT('master-data');
  const { client, tier } = workspace;
  const name = tier?.clientDisplayName ?? client.displayName ?? client.clientId;
  const clientType = tier?.clientType ?? client.clientType;
  const meta = pending
    ? [tier?.clientNo ?? client.clientNo, t('limitTier.header.request', { change: changeTitle(t, pending) }),
      t('limitTier.header.requestedBy', { person: pending.requestedByLabel ?? pending.requestedBy ?? '—' }), formatShortDateTime(pending.requestedTime)]
    : [tier?.clientNo ?? client.clientNo, tier?.jurisdictionCode ?? client.jurisdictionCode, clientType ? enumLabel(tMaster, 'client.enum.clientType', clientType) : null,
      tier ? t('limitTier.header.entered', { source: sourceText(t, tier, sourceChangeOf(workspace.history)), time: formatShortDateTime(tier.effectiveTime) }) : null];
  return <div className="uw-head"><div className="uw-grow">
    <nav className="uw-crumbs"><Link to={limitTierRoutes.list}>{tNav('resource.OPERATIONS_RISK_LIMIT_TIERS')}</Link> / {pending ? t('limitTier.crumb.pending') : name}</nav>
    <h1 className="uw-title"><span className="uw-ellipsis">{name}</span><TierBadge tier={tier?.tier ?? null} />
      {tier?.observationDue && !pending && <DueBadge />}
      {pending && <span className="uw-badge is-warn">{enumLabel(t, 'limitTier.pendingBadge', pending.approvalLevel ?? 'ELEVATED')}</span>}</h1>
    <div className="uw-meta">{meta.filter(Boolean).join(' · ')}</div></div></div>;
}

/** 申请内容（原型 04，提交时冻结）：变更、理由 / 上线说明、申请时观察期进度、审批级别。 */
function PendingRequestCard({ change }: { change: TierChange }) {
  const t = useT('risk');
  return <Card title={t('limitTier.pending.title')}>
    <Row label={t('limitTier.pending.change')}>{changeTitle(t, change)}</Row>
    <Row label={t('limitTier.request.reason')}><div className="uw-note">{change.reason}</div></Row>
    {change.goLiveNote && change.goLiveNote !== change.reason && <Row label={t('limitTier.request.goLiveNote')}><div className="uw-note">{change.goLiveNote}</div></Row>}
    {change.checklist && <Row label={t('limitTier.checklist.title')}><FrozenChecklist change={change} /></Row>}
    {change.observationDaysAtRequest !== null && change.observationAllowedAtRequest !== null && <Row label={t('limitTier.observation.title')}>
      {t('limitTier.approval.atRequest', { days: change.observationDaysAtRequest, count: change.observationAllowedAtRequest })}</Row>}
    {(change.observationTargetDays !== null || change.observationTargetTransactions !== null) && change.fromTier === 'OBSERVATION' && <Row label={t('limitTier.request.newTarget')}>
      {t('limitTier.current.observationTarget', { days: change.observationTargetDays ?? '—', count: change.observationTargetTransactions ?? '—' })}</Row>}
    <Row label={t('limitTier.approval.levelLabel')}><span className="uw-inline">
      <span className={`uw-badge ${change.approvalLevel === 'STANDARD' ? 'is-info' : 'is-warn'}`}>{enumLabel(t, 'limitTier.approval.levelShort', change.approvalLevel ?? 'ELEVATED')}</span>
      <span className="muted">{change.crrSnapshot ? `CRR ${change.crrSnapshot}` : t('limitTier.approval.noCrr')}</span></span></Row>
  </Card>;
}
function FrozenChecklist({ change }: { change: TierChange }) {
  const t = useT('risk');
  const c = change.checklist!;
  const flags: [string, boolean | null][] = [['CHANNEL', c.channelRegistrationConfirmed], ['INTEGRATION', c.integrationConfirmed], ['PRODUCTION', c.productionVerified],
    ['OPEN_ISSUES', c.openIssuesCleared]];
  const system = [c.qualificationStatus && `${t('limitTier.checklist.QUALIFICATION')}: ${c.qualificationStatus}`,
    c.underwritingVersionNo !== null && t('limitTier.current.underwritingVersion', { version: c.underwritingVersionNo }),
    c.agreementStatus && `${t('limitTier.checklist.AGREEMENT')}: ${c.agreementStatus}`,
    c.enabledPaymentCapabilities.length > 0 && `${t('limitTier.checklist.CAPABILITIES')}: ${c.enabledPaymentCapabilities.join(', ')}`].filter(Boolean);
  return <div><div>{system.join(' · ')}</div>
    <div className="uw-hint">{flags.filter(([, value]) => value).map(([code]) => t('limitTier.checklist.' + code)).join(' · ')}</div></div>;
}

/** 受限效果（原型 05）。 */
function RestrictedCard() {
  const t = useT('risk');
  return <Card title={t('limitTier.restricted.title')}><p className="lt-restricted">{t('limitTier.restricted.hint')}</p></Card>;
}

interface Context { permissions: Permissions; actorId: string; onAuthorizationChanged: () => void; onChanged: () => void }

/**
 * 审批区（原型 04）：意见驳回必填；按审批级别与权限选择普通 / 高级接口
 * <li>申请人本人：批准 / 驳回禁用并说明四眼，有申请权限时可撤回（L5）。</li>
 * <li>普通审批人遇高级审批：按钮禁用并说明须高级审批人；服务端同样校验。</li>
 */
function ApprovalPanel({ change, permissions, actorId, onAuthorizationChanged, onChanged }: { change: TierChange } & Context) {
  const t = useT('risk');
  const access = approvalAccess(change, actorId, permissions);
  const [comment, setComment] = useState(''); const [issue, setIssue] = useState<string | null>(null);
  const command = useLimitTierCommand(onAuthorizationChanged);
  if (access.kind === 'none') return <Alert showIcon type="info" title={t('limitTier.approval.waiting', { level: enumLabel(t, 'limitTier.approval.levelShort', change.approvalLevel ?? 'ELEVATED') })} />;
  const enabled = access.kind === 'approve';
  async function decide(decision: 'APPROVE' | 'REJECT') {
    if (access.kind !== 'approve') return;
    const problem = commentIssue(decision, comment);
    setIssue(problem === 'required' ? t('limitTier.approval.commentRequired') : problem === 'tooLong' ? t('limitTier.validation.tooLong', { max: 500 }) : null);
    if (problem) return;
    const done = await command.run(decisionPath(change.changeId, decision, access.elevated), 'POST', { expectedVersion: change.version, ...(comment.trim() ? { comment: comment.trim() } : {}) },
      () => true, t(decision === 'APPROVE' ? 'limitTier.done.approved' : 'limitTier.done.rejected'));
    if (done) onChanged();
  }
  async function withdraw() {
    const done = await command.run(limitTierPaths.withdrawal(change.changeId), 'POST', { expectedVersion: change.version }, () => true, t('limitTier.done.withdrawn'));
    if (done) onChanged();
  }
  const hint = access.kind === 'self' ? t('limitTier.approval.selfBlocked') : access.kind === 'insufficient' ? t('limitTier.approval.elevationRequired')
    : t('limitTier.approval.allowed', { level: enumLabel(t, 'limitTier.approval.levelShort', change.approvalLevel ?? 'ELEVATED') });
  return <Card title={t('limitTier.approval.title')}>
    <Input.TextArea aria-label={t('limitTier.approval.comment')} placeholder={t('limitTier.approval.comment')} autoSize={{ minRows: 3, maxRows: 6 }} maxLength={500}
      value={comment} disabled={!enabled} status={issue ? 'error' : undefined} onChange={event => { setComment(event.target.value); setIssue(null); }} />
    {issue && <div className="uw-field-error" role="alert">{issue}</div>}
    <CommandAlert failure={command.failure} />
    <div className="uw-actions uw-gap">
      {access.kind === 'self' && access.canWithdraw && <Button loading={command.saving} onClick={() => void withdraw()}>{t('limitTier.request.withdraw')}</Button>}
      <Button danger disabled={!enabled} loading={enabled && command.saving} onClick={() => void decide('REJECT')}>{t('limitTier.approval.reject')}</Button>
      <Button type="primary" disabled={!enabled} loading={enabled && command.saving} onClick={() => void decide('APPROVE')}>{t('limitTier.approval.approve')}</Button></div>
    <p className="uw-hint">{hint}</p>
  </Card>;
}

/** 无待审批时的右侧操作：验证档为上线复核 + 降为受限档，其他档位为下一步或恢复；没有申请权限只读说明。 */
function NextStep({ workspace, detail, ...context }: { workspace: Workspace; detail: TierDetail } & Context) {
  const t = useT('risk');
  if (detail.tier === 'VERIFICATION') return <><GoLiveCard workspace={workspace} detail={detail} {...context} />
    {context.permissions.request && <ActionCard detail={detail} {...context} />}</>;
  if (!context.permissions.request) return <Alert showIcon type="info" title={t('limitTier.request.readonly')} />;
  return <ActionCard detail={detail} {...context} />;
}

const checkDetail = (t: ReturnType<typeof useT>, code: SystemCheck, facts: SystemCheckFacts): string | null => {
  switch (code) {
    case 'QUALIFICATION': return [facts.qualificationStatus, facts.qualificationValidTo && t('limitTier.checklist.validTo', { date: formatDate(facts.qualificationValidTo) })]
      .filter(Boolean).join(' · ') || null;
    case 'UNDERWRITING': return [facts.underwritingVersionNo !== null && t('limitTier.current.underwritingVersion', { version: facts.underwritingVersionNo }),
      facts.crr && `CRR ${facts.crr}`].filter(Boolean).join(' · ') || null;
    case 'AGREEMENT': return facts.agreementStatus;
    case 'CAPABILITIES': {
      const total = facts.enabledPaymentCapabilities.length + facts.pendingPaymentCapabilities.length;
      return total === 0 ? null : `${facts.enabledPaymentCapabilities.join(', ') || '—'} ${facts.enabledPaymentCapabilities.length} / ${total}`;
    }
  }
};

/**
 * 上线复核（原型 02，验证档 → 观察档）
 * <li>系统核对项由 ops-bff 读取，页面只读；提交时服务端重新读取，页面值不作数。</li>
 * <li>四个人工项须全部勾选、上线说明必填；系统项全部满足前提交按钮禁用。</li>
 */
function GoLiveCard({ workspace, detail, permissions, onAuthorizationChanged, onChanged }: { workspace: Workspace; detail: TierDetail } & Context) {
  const t = useT('risk');
  const [manual, setManual] = useState<ManualState>(emptyManualState);
  const [note, setNote] = useState('');
  const command = useLimitTierCommand(onAuthorizationChanged);
  const canRequest = permissions.request;
  const ready = canRequest && goLiveReady(workspace.checks, manual, note);
  async function submit() {
    if (!ready) return;
    const form: RequestForm = { toTier: 'OBSERVATION', reason: '', goLiveNote: note, targetDays: '', targetTransactions: '', manual };
    const done = await command.run(limitTierPaths.request(detail.clientId), 'POST', requestBody(form, 'VERIFICATION', detail.version), () => true, t('limitTier.done.requested'));
    if (done) onChanged();
  }
  return <Card title={t('limitTier.goLive.title')} extra={t('limitTier.goLive.subtitle')}>
    <div className="lt-subtitle">{t('limitTier.checklist.title')}</div>
    <ul className="lt-checklist">
      {workspace.checks.map(check => {
        const extra = checkDetail(t, check.code, workspace.facts);
        return <li key={check.code}><CheckIcon status={check.status} />
          <span className="lt-check-text">{t('limitTier.checklist.' + check.code)}{extra && <span className="muted"> {t('limitTier.checklist.withDetail', { detail: extra })}</span>}
            {check.status !== 'OK' && <span className={`uw-hint-inline${check.status === 'NOT_OK' ? ' uw-field-error' : ''}`}>{enumLabel(t, 'limitTier.checklist.status', check.status)}</span>}
            <span className="uw-hint-inline lt-source">{systemCheckSource[check.code]}</span></span>
          <span className={`uw-badge ${check.status === 'OK' ? 'is-ok' : 'is-muted'}`}>{t('limitTier.checklist.system')}</span></li>; })}
      {manualChecks.map(code => <li key={code}>
        <Checkbox checked={manual[code]} disabled={!canRequest} onChange={event => setManual(current => ({ ...current, [code]: event.target.checked }))}>
          {t('limitTier.checklist.' + code)}</Checkbox>
        <span className="uw-badge is-muted">{t('limitTier.checklist.manual')}</span></li>)}
    </ul>
    {canRequest && <>
      <Input.TextArea aria-label={t('limitTier.request.goLiveNote')} placeholder={t('limitTier.request.goLiveNotePlaceholder')} autoSize={{ minRows: 3, maxRows: 6 }}
        maxLength={1000} showCount value={note} onChange={event => setNote(event.target.value)} />
      <CommandAlert failure={command.failure} />
      <div className="uw-actions lt-submit"><Button type="primary" disabled={!ready} loading={command.saving} onClick={() => void submit()}>{t('limitTier.request.submit')}</Button></div>
    </>}
    <p className="uw-hint">{canRequest ? t('limitTier.goLive.hint', { level: approvalHint(t, detail.crr) }) : t('limitTier.request.readonly')}</p>
  </Card>;
}
const approvalHint = (t: ReturnType<typeof useT>, crr: string | null) => enumLabel(t, 'limitTier.approval.levelShort', crr === 'LOW' ? 'STANDARD' : 'ELEVATED');

type ActionKind = 'promote' | 'extend' | 'restrict' | 'restore';
const actionTarget: Record<Exclude<ActionKind, 'restore'>, Tier> = { promote: 'STANDARD', extend: 'OBSERVATION', restrict: 'RESTRICTED' };

/** 下一步（原型 03 / 05）：观察档三项、验证档与标准档降为受限、受限档申请恢复；每项打开申请弹窗。 */
function ActionCard({ detail, ...context }: { detail: TierDetail } & Context) {
  const t = useT('risk');
  const [open, setOpen] = useState<ActionKind | null>(null);
  const targets = requestableTargets(detail.tier, detail.previousTier);
  const kinds: ActionKind[] = detail.tier === 'RESTRICTED' ? ['restore']
    : (['promote', 'extend', 'restrict'] as const).filter(kind => targets.includes(actionTarget[kind]) && (kind === 'restrict' || detail.tier === 'OBSERVATION'));
  const title = detail.tier === 'RESTRICTED' ? t('limitTier.restore.title') : detail.observationDue ? t('limitTier.next.dueTitle') : t('limitTier.next.title');
  const level = approvalHint(t, detail.crr);
  return <Card title={title}>
    <ul className="lt-actions">{kinds.map(kind => <li key={kind}><div>
      <strong>{t('limitTier.next.' + kind + '.title')}</strong>
      <div className="uw-hint">{kind === 'restore' ? t('limitTier.restore.hint', { tier: enumLabel(t, 'limitTier.tier', restoreCeiling(detail.previousTier)), level })
        : t('limitTier.next.' + kind + '.hint', { level })}</div></div>
      <Button type={kind === 'promote' ? 'primary' : 'default'} danger={kind === 'restrict'} onClick={() => setOpen(kind)}>{t('limitTier.action.' + kind)}</Button></li>)}</ul>
    {open && <RequestModal kind={open} detail={detail} targets={targets} onClose={() => setOpen(null)} {...context} />}
  </Card>;
}

const issueKey: Record<RequestIssue, string> = { reasonRequired: 'limitTier.validation.reasonRequired', reasonTooLong: 'limitTier.validation.tooLong',
  targetDays: 'limitTier.validation.targetDays', targetTransactions: 'limitTier.validation.targetTransactions', checklist: 'limitTier.validation.checklist' };

/** 申请弹窗：理由必填；延长观察可改目标天数 / 笔数（空 = 沿用）；恢复只列不高于受限前档位的目标。 */
function RequestModal({ kind, detail, targets, onClose, onAuthorizationChanged, onChanged }: { kind: ActionKind; detail: TierDetail; targets: Tier[]; onClose: () => void } & Context) {
  const t = useT('risk');
  const restoreTargets = targets.filter(tier => tier !== 'RESTRICTED');
  const [form, setForm] = useState<RequestForm>({ toTier: kind === 'restore' ? restoreTargets.at(-1)! : actionTarget[kind], reason: '', goLiveNote: '',
    targetDays: kind === 'extend' && detail.observationTargetDays !== null ? String(detail.observationTargetDays) : '',
    targetTransactions: kind === 'extend' && detail.observationTargetTransactions !== null ? String(detail.observationTargetTransactions) : '', manual: emptyManualState });
  const [shown, setShown] = useState<RequestIssue[]>([]);
  const command = useLimitTierCommand(onAuthorizationChanged);
  const set = (patch: Partial<RequestForm>) => { setForm(current => ({ ...current, ...patch })); setShown([]); };
  async function submit() {
    const issues = requestIssues(form, detail.tier);
    setShown(issues);
    if (issues.length) return;
    const done = await command.run(limitTierPaths.request(detail.clientId), 'POST', requestBody(form, detail.tier, detail.version), () => true, t('limitTier.done.requested'));
    if (done) { onClose(); onChanged(); }
  }
  const has = (issue: RequestIssue) => shown.includes(issue);
  return <Modal open title={t('limitTier.next.' + kind + '.title')} okText={t('limitTier.request.submit')} okButtonProps={{ loading: command.saving, danger: kind === 'restrict' }}
    onCancel={onClose} onOk={() => void submit()} destroyOnHidden>
    <p className="uw-hint">{t('limitTier.request.change', { change: changeTitle(t, { changeType: 'REQUEST', fromTier: detail.tier, toTier: form.toTier }) })}</p>
    {kind === 'restore' && <Row label={t('limitTier.request.toTier')}><Select className="uw-filter" value={form.toTier} onChange={value => set({ toTier: value })}
      options={restoreTargets.map(value => ({ value, label: enumLabel(t, 'limitTier.tier', value) }))} /></Row>}
    {kind === 'extend' && <Row label={t('limitTier.request.newTarget')}><div className="uw-inline-form">
      <Input className="uw-num" aria-label={t('limitTier.request.targetDays')} inputMode="numeric" maxLength={3} value={form.targetDays} status={has('targetDays') ? 'error' : undefined}
        onChange={event => set({ targetDays: event.target.value })} /><span>{t('limitTier.request.daysOr')}</span>
      <Input className="uw-num" aria-label={t('limitTier.request.targetTransactions')} inputMode="numeric" maxLength={6} value={form.targetTransactions}
        status={has('targetTransactions') ? 'error' : undefined} onChange={event => set({ targetTransactions: event.target.value })} /><span>{t('limitTier.request.transactionsUnit')}</span></div>
      <p className="uw-hint">{t('limitTier.request.extendHint')}</p></Row>}
    <Input.TextArea className="uw-gap" autoFocus aria-label={t('limitTier.request.reason')} placeholder={t('limitTier.request.reason')} autoSize={{ minRows: 3, maxRows: 6 }}
      maxLength={1000} showCount value={form.reason} status={has('reasonRequired') || has('reasonTooLong') ? 'error' : undefined} onChange={event => set({ reason: event.target.value })} />
    {shown.map(issue => <div key={issue} className="uw-field-error" role="alert">{t(issueKey[issue], { max: 1000 })}</div>)}
    <CommandAlert failure={command.failure} />
    <p className="uw-hint">{t('limitTier.request.approvalHint', { level: approvalHint(t, detail.crr) })}</p>
  </Modal>;
}

