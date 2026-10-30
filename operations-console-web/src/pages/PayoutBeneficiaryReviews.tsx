import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Empty, Input, Pagination, Spin, Tag } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, EyeInvisibleOutlined, EyeOutlined, LockOutlined, SendOutlined, TeamOutlined,
  WarningOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import type { ApiError } from '../api/session';
import type { ConsoleResource, PagingResponseBody } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { enumLabel, formatCountry, formatDateTime } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { actorText, bankLine, CommandFailure, Field, OwnerFilter, ReasonDialog } from '../payout/PayoutParts';
import {
  checkerMayApprove, compareNames, countStagesToFetch, countedStages, loadStageCounts, decodeAccount, decodeDetail, decodeReveal, decodeReviewPage, fourEyesBlock, hasAction, isAccountId,
  lastEvent, ownerTypes, payoutActions, payoutApi, queuePageSizes, rejectReasons, revealVisibleSeconds, reviewNoteMaxLength, reviewQuery,
  reviewStageOf, reviewStages, sharedOwnerKey,
} from '../payout/payout';
import type {
  BeneficiaryAccount, BeneficiaryDetail, OwnerType, PayoutOwner, ReviewItem, ReviewStage, RevealedValues, SharedOwner, StageCounts,
} from '../payout/payout';
import { usePayoutCommand } from '../payout/usePayoutCommand';
import '../payout/payout.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
type Owner = { ownerType: OwnerType; ownerId: string };
const shortTime: Intl.DateTimeFormatOptions = { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' };

/** 收款账户核验工作台（O1 待核验、O2 待复核、O4 四眼阻断、O5 拒绝原因）；阶段、Owner 筛选、页码与选中账户写入 URL。 */
export function PayoutBeneficiaryReviews({ resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('payout'), tNav = useT('nav'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate();
  const query = new URLSearchParams(location.search);
  const stage = (reviewStages as readonly string[]).includes(query.get('stage') ?? '') ? query.get('stage') as ReviewStage : 'MAKER';
  const owner = readOwner(query);
  const page = Math.max(Number(query.get('page')) || 1, 1);
  const size = queuePageSizes.includes(Number(query.get('size'))) ? Number(query.get('size')) : 20;
  const selected = isAccountId(query.get('account')) ? query.get('account')! : null;
  const [reload, setReload] = useState(0);
  const list = useQueue(stage, owner, page, size, reload, onAuthorizationChanged);
  const counts = useCounts(stage, owner, reload, list);
  function go(patch: Record<string, string | null>) {
    const next = new URLSearchParams(location.search);
    Object.entries(patch).forEach(([key, value]) => { if (value) next.set(key, value); else next.delete(key); });
    void navigate(`?${next.toString()}`);
  }
  const current = selected ?? list.data?.items[0]?.account.beneficiaryAccountId ?? null;
  return <div className="configuration-page payout-page">
    <div className="page-heading payout-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_PAYOUT_BENEFICIARY_REVIEWS')}</h1>
      <OwnerFilter value={owner} placeholder={t('reviews.ownerFilterPlaceholder')} onApply={value => go({ ownerType: value?.ownerType ?? null, ownerId: value?.ownerId ?? null, page: null, account: null })} /></div>
    <div className="payout-tabs" role="tablist">{reviewStages.map(item => <button key={item} type="button" role="tab" aria-selected={item === stage}
      className={item === stage ? 'is-active' : undefined} onClick={() => go({ stage: item, page: null, account: null })}>
      {enumLabel(t, 'enum.stage', item)}{item !== 'DONE' && counts[item] !== undefined && <span className="payout-count">{counts[item]}</span>}
    </button>)}</div>
    {list.failure ? <QueryFailure failure={list.failure} retry={() => setReload(value => value + 1)} />
      : <div className="payout-workbench">
        <section className="payout-queue" aria-label={t('reviews.queueLabel')}>
          {list.loading && !list.data ? <div className="content-loading"><Spin /></div>
            : !list.data?.items.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t(owner ? 'reviews.emptyFiltered' : 'reviews.empty')} />
              : list.data.items.map(item => <QueueItem key={item.account.beneficiaryAccountId} item={item} stage={stage}
                active={item.account.beneficiaryAccountId === current} onSelect={() => go({ account: item.account.beneficiaryAccountId })} />)}
          {list.data && list.data.total > size && <Pagination size="small" className="payout-queue-pages" current={page} pageSize={size}
            total={list.data.total} pageSizeOptions={queuePageSizes} showSizeChanger onChange={(next, nextSize) =>
              go({ page: String(nextSize === size ? next : 1), size: String(nextSize), account: null })} />}
          <div className="payout-queue-foot"><Button size="small" onClick={() => setReload(value => value + 1)} loading={list.loading}>{tCommon('action.refresh')}</Button></div>
        </section>
        <section className="payout-detail">{current
          ? <ReviewDetail key={`${current}:${reload}`} accountId={current} actorId={actorId} resources={resources}
            onAuthorizationChanged={onAuthorizationChanged} onChanged={() => { go({ account: null }); setReload(value => value + 1); }} />
          : <Empty className="payout-detail-empty" description={t('reviews.selectHint')} />}</section>
      </div>}
  </div>;
}

/** URL 中成对且合法的 Owner 筛选。 */
function readOwner(query: URLSearchParams): Owner | null {
  const ownerType = query.get('ownerType') as OwnerType | null; const ownerId = query.get('ownerId');
  return ownerType && (ownerTypes as readonly string[]).includes(ownerType) && isAccountId(ownerId) ? { ownerType, ownerId } : null;
}

/** 当前阶段的队列分页。 */
function useQueue(stage: ReviewStage, owner: Owner | null, page: number, size: number, reload: number, onAuthorizationChanged: () => void) {
  const notify = useErrorNotification(); const t = useT('payout');
  const [state, setState] = useState<{ data?: PagingResponseBody<ReviewItem>; loading: boolean; failure?: unknown }>({ loading: true });
  const path = `${payoutApi}/beneficiary-reviews?${reviewQuery(stage, owner, page, size)}`;
  useEffect(() => {
    const controller = new AbortController(); setState(current => ({ data: current.data, loading: true }));
    session.request(path, { signal: controller.signal }, decodeReviewPage)
      .then(data => { if (!controller.signal.aborted) setState({ data, loading: false }); })
      .catch(failure => {
        if (controller.signal.aborted) return;
        if (failure?.status === 403) onAuthorizationChanged();
        setState({ loading: false, failure }); notify(failure, t('reviews.loadFailed'));
      });
    return () => controller.abort();
  // 回调随父组件渲染变化，只随查询与刷新重新加载
  }, [path, reload, notify]); // eslint-disable-line react-hooks/exhaustive-deps
  return state;
}

/**
 * 待核验与待复核的页签计数：当前页签取已加载列表的 total；列表返回后再依次查询其他页签（一次一个）
 * <li>核验队列路由每人并发上限 2：列表加载期间不发计数请求，计数之间也不并发；页签、Owner、刷新变化或卸载时取消。</li>
 * <li>429 保留上一次计数；Owner 变化时清空旧 Owner 的计数。</li>
 */
function useCounts(stage: ReviewStage, owner: Owner | null, reload: number, list: { data?: PagingResponseBody<ReviewItem>; loading: boolean }) {
  const key = owner ? `${owner.ownerType}:${owner.ownerId}` : '';
  const [state, setState] = useState<{ key: string; counts: StageCounts }>({ key, counts: {} });
  const latest = useRef(state); latest.current = state;
  const ready = !list.loading && !!list.data;
  const activeTotal = list.data?.total;
  useEffect(() => {
    if (!ready) return;
    const controller = new AbortController();
    const kept = latest.current.key === key ? latest.current.counts : {};
    const base = activeTotal !== undefined && countedStages.includes(stage) ? { ...kept, [stage]: activeTotal } : kept;
    setState({ key, counts: base });
    void loadStageCounts(countStagesToFetch(stage), (target, signal) => session.request(
      `${payoutApi}/beneficiary-reviews?${reviewQuery(target, owner, 1, 10)}`, { signal }, decodeReviewPage).then(result => result.total),
    base, controller.signal).then(counts => { if (!controller.signal.aborted) setState({ key, counts }); });
    return () => controller.abort();
  }, [key, reload, stage, ready, activeTotal]); // eslint-disable-line react-hooks/exhaustive-deps
  return state.key === key ? state.counts : {};
}

/** Owner 显示名：法定名，读取失败时用类型 + 标识。 */
function ownerName(t: Translate, account: Pick<BeneficiaryAccount, 'ownerType' | 'ownerId'>, owner: PayoutOwner | null) {
  return owner?.legalName || t('owner.fallback', { type: enumLabel(t, 'enum.ownerType', account.ownerType), id: account.ownerId });
}

/** 队列左列一行。 */
function QueueItem({ item, stage, active, onSelect }: { item: ReviewItem; stage: ReviewStage; active: boolean; onSelect: () => void }) {
  const t = useT('payout'); const { account, owner } = item;
  const time = stage === 'CHECKER' ? t('reviews.opinionAt', { time: formatDateTime(account.updateTime, shortTime) })
    : formatDateTime(stage === 'DONE' ? account.decisionTime ?? account.updateTime : account.createTime, shortTime);
  return <button type="button" className={active ? 'payout-queue-item is-active' : 'payout-queue-item'} onClick={onSelect} aria-current={active}>
    <div className="payout-queue-line"><strong>{ownerName(t, account, owner)}</strong><span className="cell-meta">{time}</span></div>
    <div className="payout-queue-line"><span>{bankLine(t, account)} <span className="muted">{account.currency} · {enumLabel(t, 'enum.railType', account.railType)}</span></span></div>
    <div className="payout-queue-line cell-meta"><span>{enumLabel(t, 'enum.ownerType', account.ownerType)} · {account.ownerId}
      {!!account.sharedOwners?.length && <Tag className="payout-shared-tag" icon={<TeamOutlined />}>{t('reviews.sharedTag')}</Tag>}</span>
      {stage === 'DONE' && <span>{enumLabel(t, 'enum.displayState', account.status === 'VERIFIED' ? (account.usable ? 'USABLE' : 'COOLING') : account.status)}</span>}</div>
  </button>;
}

/** 右侧详情：账户信息与 Reveal、户名对照、Maker 意见与 Checker 决定。 */
function ReviewDetail({ accountId, actorId, resources, onAuthorizationChanged, onChanged }: {
  accountId: string; actorId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const decode = useMemo(() => decodeDetail(accountId), [accountId]);
  const detail = useDetail(`${payoutApi}/beneficiary-accounts/${accountId}`, decode);
  if (detail.failure) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  if (!detail.data) return <div className="content-loading"><Spin /></div>;
  return <ReviewPanel detail={detail.data} actorId={actorId} canReview={hasAction(resources, payoutActions.review)}
    canReveal={hasAction(resources, payoutActions.reveal)} onAuthorizationChanged={onAuthorizationChanged} onChanged={onChanged} />;
}

type Decision = 'APPROVE' | 'REJECT';

/** 详情主体；stage 由账户状态与 Maker 意见实时判定，不依赖所在页签。 */
function ReviewPanel({ detail, actorId, canReview, canReveal, onAuthorizationChanged, onChanged }: {
  detail: BeneficiaryDetail; actorId: string; canReview: boolean; canReveal: boolean; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('payout');
  const { account, owner, sharedOwnerProfiles, operatorLabels } = detail;
  const stage = reviewStageOf(account);
  const reveal = useReveal(account.beneficiaryAccountId, stage, onAuthorizationChanged);
  const [decision, setDecision] = useState<Decision | null>(null);
  const [rejecting, setRejecting] = useState(false);
  const [makerNote, setMakerNote] = useState('');
  const command = usePayoutCommand(onAuthorizationChanged);
  const blocked = stage === 'CHECKER' ? fourEyesBlock(account, actorId) : null;
  const approveAllowed = stage === 'MAKER' || checkerMayApprove(account);
  const submitter = actorText(t, operatorLabels, account.submittedByType, account.submittedBy, actorId);
  async function submit(choice: Decision, reasonCode: string | null, clientNote: string | null) {
    const maker = stage === 'MAKER';
    const path = `${payoutApi}/beneficiary-accounts/${account.beneficiaryAccountId}/${maker ? 'maker-reviews' : 'checker-decisions'}`;
    const body = { decision: maker ? choice : (choice === 'APPROVE' ? 'APPROVED' : 'REJECTED'), reasonCode, clientNote,
      ...(maker ? { note: makerNote.trim() || null } : {}), contentHash: account.contentHash, expectedVersion: account.version };
    const result = await command.run(path, body, value => decodeAccount(value, account.beneficiaryAccountId),
      t(maker ? 'review.makerSubmitted' : 'review.checkerSubmitted'));
    if (result) { setRejecting(false); onChanged(); }
  }
  return <div className="payout-panel">
    <div className="payout-panel-body">
      <div className="payout-panel-title"><h2>{bankLine(t, account)}</h2>
        <span className={`payout-badge is-stage-${stage.toLowerCase()}`}>{enumLabel(t, 'enum.stageBadge', stage === 'DONE' ? account.status : stage)}</span></div>
      <div className="cell-meta payout-panel-meta">{t('review.meta', { owner: ownerName(t, account, owner), ownerType: enumLabel(t, 'enum.ownerType', account.ownerType),
        ownerId: account.ownerId, code: account.accountCode, currency: account.currency, country: formatCountry(account.bankCountry),
        rail: enumLabel(t, 'enum.railType', account.railType), submitter, time: formatDateTime(account.createTime, shortTime) })}</div>
      {!!account.sharedOwners?.length && <SharedOwnersAlert shared={account.sharedOwners} profiles={sharedOwnerProfiles} />}
      <div className="payout-cards">
        <AccountCard account={account} reveal={reveal} canReveal={canReveal && stage !== 'DONE'} />
        <NameMatchCard account={account} owner={owner} holderName={reveal.values?.holderName ?? null} />
      </div>
      {stage === 'DONE' ? <DoneCard account={account} labels={operatorLabels} actorId={actorId} />
        : <div className="payout-card"><div className="payout-card-title">{t(stage === 'MAKER' ? 'review.maker.title' : 'review.checker.title')}</div>
          {stage === 'CHECKER' && <MakerOpinion account={account} labels={operatorLabels} actorId={actorId} />}
          {!canReview ? <span className="muted">{t('review.noPermission')}</span>
            : <div className="payout-options">
              <OptionCard selected={decision === 'APPROVE'} disabled={!!blocked || !approveAllowed} tone="ok" icon={<CheckCircleOutlined />}
                title={t(stage === 'MAKER' ? 'review.maker.approve' : 'review.checker.approve')}
                hint={t(stage === 'MAKER' ? 'review.maker.approveHint' : approveAllowed ? 'review.checker.approveHint' : 'review.checker.approveBlockedByMaker')}
                onClick={() => setDecision('APPROVE')} />
              <OptionCard selected={decision === 'REJECT'} disabled={!!blocked} tone="danger" icon={<CloseCircleOutlined />}
                title={t(stage === 'MAKER' ? 'review.maker.reject' : 'review.checker.reject')}
                hint={t(stage === 'MAKER' ? 'review.maker.rejectHint' : 'review.checker.rejectHint')}
                onClick={() => { setDecision('REJECT'); setRejecting(true); }} />
            </div>}
          {canReview && stage === 'MAKER' && <div className="payout-note">
            <div className="payout-field-label">{t('review.maker.note')}</div>
            <Input.TextArea rows={2} value={makerNote} maxLength={reviewNoteMaxLength} showCount disabled={!!blocked}
              placeholder={t('review.maker.notePlaceholder')} onChange={event => setMakerNote(event.target.value)} /></div>}
          {!rejecting && <CommandFailure failure={command.failure} />}
        </div>}
    </div>
    {stage !== 'DONE' && canReview && <div className={blocked ? 'payout-panel-foot is-blocked' : 'payout-panel-foot'}>
      <span>{blocked ? <><LockOutlined /> {t(blocked === 'MAKER' ? 'review.fourEyesMaker' : 'review.fourEyesSubmitter')}</>
        : <><TeamOutlined /> {t(stage === 'MAKER' ? 'review.maker.footHint' : 'review.checker.footHint')}</>}</span>
      <Button type="primary" icon={<SendOutlined />} disabled={!!blocked || !decision} loading={command.saving && !rejecting}
        onClick={() => { if (decision === 'REJECT') setRejecting(true); else if (decision) void submit(decision, null, null); }}>
        {t(stage === 'MAKER' ? 'review.maker.submit' : decision === 'REJECT' ? 'review.checker.submitReject' : 'review.checker.submit')}</Button>
    </div>}
    {rejecting && <ReasonDialog title={t(stage === 'MAKER' ? 'reasonDialog.rejectTitle' : 'reasonDialog.checkerRejectTitle')}
      subject={t('reasonDialog.subject', { owner: ownerName(t, account, owner), bank: bankLine(t, account), currency: account.currency })}
      reasons={rejectReasons} reasonPrefix="enum.rejectReason" danger saving={command.saving} failure={command.failure}
      notice={t(stage === 'MAKER' ? 'reasonDialog.makerRejectNotice' : 'reasonDialog.checkerRejectNotice')}
      okText={t(stage === 'MAKER' ? 'reasonDialog.submitMakerReject' : 'reasonDialog.submitCheckerReject')}
      clientNote={{ hint: stage === 'CHECKER' && account.reasonNote ? t('reasonDialog.clientNoteInherited', { note: account.reasonNote }) : undefined }}
      onSubmit={(reason, clientNote) => void submit('REJECT', reason, clientNote)} onClose={() => { setRejecting(false); command.clear(); }} />}
  </div>;
}

/** 共用提示（O1）：该账号也登记在 N 个其他 Owner 名下，逐个列出名称（类型 ID）· 编号 · 状态；名称读取失败时只显示类型与 ID。 */
function SharedOwnersAlert({ shared, profiles }: { shared: SharedOwner[]; profiles: Record<string, PayoutOwner> }) {
  const t = useT('payout');
  return <Alert type="warning" showIcon icon={<WarningOutlined />} className="payout-shared"
    title={t('review.sharedTitle', { count: shared.length })}
    description={<>{shared.map(item => <div key={sharedOwnerKey(item)}>{t('review.sharedOwnerLine', {
      name: ownerName(t, item, profiles[sharedOwnerKey(item)] ?? null), type: enumLabel(t, 'enum.ownerType', item.ownerType), id: item.ownerId,
      code: item.accountCode, status: enumLabel(t, 'enum.stageBadge', item.status) })}</div>)}
      <div>{t('review.sharedHint')}</div></>} />;
}

/** 可选决定卡片（通过 / 拒绝）。 */
function OptionCard({ selected, disabled, tone, icon, title, hint, onClick }: {
  selected: boolean; disabled: boolean; tone: 'ok' | 'danger'; icon: ReactNode; title: string; hint: string; onClick: () => void;
}) {
  return <button type="button" disabled={disabled} aria-pressed={selected} onClick={onClick}
    className={`payout-option is-${tone}${selected ? ' is-selected' : ''}`}>
    <span className="payout-option-icon">{icon}</span><span><strong>{title}</strong><span className="cell-meta">{hint}</span></span>
  </button>;
}

/** Maker 意见（只读）：核验员、时间、意见与拒绝原因。 */
function MakerOpinion({ account, labels, actorId }: { account: BeneficiaryAccount; labels: Record<string, string>; actorId: string }) {
  const t = useT('payout');
  const event = lastEvent(account, 'MAKER_REVIEW');
  const maker = actorText(t, labels, 'OPERATIONS', account.reviewMakerId, actorId);
  const approve = account.reviewMakerDecision === 'APPROVE';
  return <div className={approve ? 'payout-opinion is-ok' : 'payout-opinion is-danger'}>
    {approve ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
    <div><strong>{t('review.makerOpinion', { maker, decision: enumLabel(t, 'enum.makerDecision', account.reviewMakerDecision) })}</strong>
      <div className="cell-meta">{formatDateTime(event?.occurredTime ?? account.updateTime, shortTime)}
        {event?.reasonCode && <> · {enumLabel(t, 'enum.rejectReason', event.reasonCode)}</>}
        {account.reviewMakerNote && <> · {account.reviewMakerNote}</>}</div>
      {!approve && account.reasonNote && <div className="cell-meta">{t('review.makerClientNote', { note: account.reasonNote })}</div>}</div>
  </div>;
}

/** 已处理：复核结果、原因、决定时间、Checker 与冷却结束时间。 */
function DoneCard({ account, labels, actorId }: { account: BeneficiaryAccount; labels: Record<string, string>; actorId: string }) {
  const t = useT('payout');
  return <div className="payout-card"><div className="payout-card-title">{t('review.done.title')}</div>
    <Field label={t('review.done.result')}>{enumLabel(t, 'enum.stageBadge', account.status)}</Field>
    {account.reasonCode && <Field label={t('review.done.reason')}>{enumLabel(t, account.status === 'SUSPENDED' ? 'enum.suspendReason'
      : account.status === 'CLOSED' ? 'enum.closeReason' : 'enum.rejectReason', account.reasonCode)}</Field>}
    {account.reasonNote && <Field label={t('review.done.clientNote')}>{account.reasonNote}</Field>}
    {account.reviewMakerNote && <Field label={t('review.done.makerNote')}>{account.reviewMakerNote}</Field>}
    <Field label={t('review.done.maker')}>{account.reviewMakerId ? actorText(t, labels, 'OPERATIONS', account.reviewMakerId, actorId) : '—'}</Field>
    <Field label={t('review.done.checker')}>{account.checkerId ? actorText(t, labels, 'OPERATIONS', account.checkerId, actorId) : '—'}</Field>
    <Field label={t('review.done.decisionTime')}>{formatDateTime(account.decisionTime)}</Field>
    {account.coolingUntil && <Field label={t('review.done.availableAt')}>{formatDateTime(account.coolingUntil)}</Field>}
  </div>;
}

/** 账户信息卡：户名与账号默认脱敏，“显示”调用一次 Reveal（payout 记审计），60 秒后自动隐藏并清除原值。 */
function AccountCard({ account, reveal, canReveal }: { account: BeneficiaryAccount; reveal: RevealState; canReveal: boolean }) {
  const t = useT('payout');
  const shown = reveal.values;
  const accountValue = shown ? shown.iban ?? shown.accountNumber ?? '—' : t('account.maskedLast4', { last4: account.accountLast4 });
  const toggle = canReveal && <Button size="small" icon={shown ? <EyeInvisibleOutlined /> : <EyeOutlined />} loading={reveal.loading}
    onClick={() => (shown ? reveal.hide() : void reveal.show())}>{shown ? t('reveal.hide', { seconds: reveal.remaining }) : t('reveal.show')}</Button>;
  return <div className="payout-card"><div className="payout-card-title">{t('account.title')}</div>
    <Field label={t('enum.field.holderName')}><span className={shown ? 'payout-original' : undefined}>{shown?.holderName ?? account.holderNameMasked}</span>{toggle}</Field>
    <Field label={t(account.railType === 'SWIFT' || !shown?.iban ? 'account.accountOrIban' : 'enum.field.iban')}>
      <span className={shown ? 'payout-original' : undefined}>{accountValue}</span>{toggle}</Field>
    <Field label={t('account.bank')}>{account.bankName}</Field>
    {account.bankCode && <Field label={t('account.bankCode')}>{account.bankCode}</Field>}
    <Field label={t('account.countryCurrencyRail')}>{formatCountry(account.bankCountry)} · {account.currency} · {enumLabel(t, 'enum.railType', account.railType)}</Field>
    <div className="cell-meta payout-audit">{reveal.revealedAt ? t('reveal.audited', { time: formatDateTime(reveal.revealedAt, { hour: '2-digit', minute: '2-digit' }) })
      : t('reveal.auditNotice', { seconds: revealVisibleSeconds })}</div>
    {reveal.failure && <CommandFailure failure={reveal.failure} />}
  </div>;
}

/** 户名对照卡：Owner 法定名与原值户名（显示后自动比对）。 */
function NameMatchCard({ account, owner, holderName }: { account: BeneficiaryAccount; owner: PayoutOwner | null; holderName: string | null }) {
  const t = useT('payout');
  const result = compareNames(owner?.legalName, holderName);
  return <div className="payout-card"><div className="payout-card-title">{t('nameMatch.title')}</div>
    <Field label={t('nameMatch.legalName', { ownerType: enumLabel(t, 'enum.ownerType', account.ownerType) })}>
      <strong>{owner?.legalName ?? t('nameMatch.legalNameUnavailable')}</strong></Field>
    <Field label={t('enum.field.holderName')}>{holderName ?? <span className="muted">{t('nameMatch.revealToCompare')}</span>}</Field>
    {holderName && owner?.legalName ? <div className={`payout-match is-${result.toLowerCase()}`}>
      {result === 'MISMATCH' ? <CloseCircleOutlined /> : <CheckCircleOutlined />} {enumLabel(t, 'enum.nameMatch', result)}</div>
      : <div className="payout-match is-hint">{t('nameMatch.rule')}</div>}
  </div>;
}

interface RevealState {
  values: RevealedValues | null; loading: boolean; remaining: number; revealedAt: string | null; failure?: ApiError;
  show: () => Promise<void>; hide: () => void;
}

/** 原值只在组件内存中保存，倒计时结束、隐藏或卸载时清除；每次“显示”都是一次带原因的 Reveal。 */
function useReveal(accountId: string, stage: ReviewStage, onAuthorizationChanged: () => void): RevealState {
  const command = usePayoutCommand(onAuthorizationChanged);
  const [values, setValues] = useState<RevealedValues | null>(null);
  const [remaining, setRemaining] = useState(revealVisibleSeconds);
  const [revealedAt, setRevealedAt] = useState<string | null>(null);
  useEffect(() => {
    if (!values) return;
    if (remaining <= 0) { setValues(null); return; }
    const timer = window.setTimeout(() => setRemaining(seconds => seconds - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [values, remaining]);
  useEffect(() => () => setValues(null), []);
  async function show() {
    const result = await command.run(`${payoutApi}/beneficiary-accounts/${accountId}/reveal`,
      { reasonCode: stage === 'CHECKER' ? 'CHECKER_REVIEW' : 'MAKER_REVIEW' }, decodeReveal(accountId));
    if (result) { setValues(result); setRemaining(revealVisibleSeconds); setRevealedAt(new Date().toISOString()); command.clear(); }
  }
  return { values, loading: command.saving, remaining, revealedAt, failure: command.failure, show, hide: () => setValues(null) };
}
