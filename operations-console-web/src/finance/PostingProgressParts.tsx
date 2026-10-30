import type { ReactNode } from 'react';
import { Alert, Button, Modal, Table, Tag } from 'antd';
import {
  ArrowDownOutlined, ArrowRightOutlined, CheckCircleOutlined, CheckOutlined, CloseCircleOutlined, EyeInvisibleOutlined, FastForwardOutlined,
  HourglassOutlined, InfoCircleOutlined, LoadingOutlined, LockOutlined, PauseCircleOutlined, ReloadOutlined, SyncOutlined, WarningOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { enumLabel, formatDateTime, i18n } from '../i18n/index';
import { richText } from '../i18n/richText';
import { useT } from '../i18n/react';
import { advancedBeforeRejection, catchUpDisplayKind, factUnavailableCause, gapOf, remainingAfterCatchUp } from './postingOps';
import type { CatchUpOutcome, FactSegment, PostingProgress, PostingStream, PostingSummary, RebuildOutcome, SegmentStatus } from './postingOps';

/** 计数与序号：十进制字符串按当前语言加千分位；空值显示 —。 */
export function count(value: string | bigint | null | undefined): string {
  if (value === null || value === undefined || value === '') return '—';
  return BigInt(value).toLocaleString(i18n.locale());
}

/** 时间到秒（进度与批次的排查粒度）。 */
export function opsTime(value: string | null | undefined): string {
  return formatDateTime(value, { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

/** 事实号缩略：保留末 6 位。 */
const factTail = (factId: string | null) => factId ? `…${factId.slice(-6)}` : '—';

type Tone = 'ok' | 'warn' | 'muted';
function Badge({ tone, children }: { tone: Tone; children: ReactNode }) {
  const icon = tone === 'ok' ? <CheckOutlined /> : tone === 'warn' ? <WarningOutlined /> : <EyeInvisibleOutlined />;
  return <span className={`posting-ops-badge is-${tone}`}>{icon}{children}</span>;
}

function Metric({ label, value, strong }: { label: ReactNode; value: ReactNode; strong?: boolean }) {
  return <div className="posting-ops-metric"><span>{label}</span><span className={strong ? 'is-strong' : ''}>{value}</span></div>;
}

function SegmentCard({ index, title, tone, badge, unavailable, onRetry, children }: {
  index: number; title: string; tone: Tone; badge: ReactNode; unavailable?: string; onRetry: () => void; children?: ReactNode;
}) {
  const t = useT('finance');
  return <section className={`posting-ops-segment is-${tone}`} aria-label={title}>
    <header><span className="posting-ops-index">{index}</span><h3>{title}</h3><Badge tone={tone}>{badge}</Badge></header>
    {unavailable ? <div className="posting-ops-unavailable"><p>{unavailable}</p>
      <Button type="link" size="small" icon={<ReloadOutlined />} onClick={onRetry}>{t('common.retry')}</Button></div> : children}
  </section>;
}

const toneOf = (status: SegmentStatus): Tone => status === 'CONSISTENT' ? 'ok' : status === 'BEHIND' ? 'warn' : 'muted';

/**
 * 三段进度卡片（画板 22、23、29、30）：① 计费事实入账 → ② account 过账流 → ③ ledger 总账消费，窄屏纵向排列
 * <li>不可用的段只在本段灰显并可重试（重新请求整页），其余段照常；差距用警示色与差额数字标出。</li>
 */
export function SegmentCards({ progress, onRetry }: { progress: PostingProgress; onRetry: () => void }) {
  const t = useT('finance');
  const { streamSegment } = progress;
  const streams = streamSegment.streams;
  const accountUnavailable = streamSegment.status === 'UNAVAILABLE';
  const behind = streams.filter(stream => stream.status === 'BEHIND');
  const single = streams.length === 1 ? streams[0] : null;
  const lastPosted = latest(streams.map(stream => stream.accountLastPostedTime));
  // ledger 没有消费时间（technique 7.1 阶段 4 修订）：时间维度只用卡住批次的生成时间，取最早的一批
  const stuckSince = earliest(behind.map(stream => stream.stuckPosting?.postedTime ?? null));
  const totalLag = streams.reduce((sum, stream) => sum + BigInt(stream.lag), 0n);
  const ledgerBadge = streamSegment.status === 'BEHIND'
    ? (single ? t('postingOps.badge.behindBatches', { count: count(single.lag) }) : t('postingOps.badge.streamsBehind', { count: behind.length }))
    : accountUnavailable ? t('postingOps.badge.unavailable') : t('postingOps.badge.consistent');
  const arrow = <span className="posting-ops-arrow" aria-hidden="true"><ArrowRightOutlined className="is-wide" /><ArrowDownOutlined className="is-narrow" /></span>;
  return <div className="posting-ops-segments">
    <FactCard progress={progress} onRetry={onRetry} />
    {arrow}
    <SegmentCard index={2} title={t('postingOps.segment.account')} tone={accountUnavailable ? 'muted' : 'ok'} onRetry={onRetry}
      badge={accountUnavailable ? t('postingOps.badge.unavailable') : t('postingOps.badge.streams', { count: streams.length })}
      unavailable={accountUnavailable ? t('postingOps.segment.accountUnavailable') : undefined}>
      <Metric label={t('postingOps.metric.accountMaxSequence')} value={single ? count(single.accountMaxSequence) : streams.length ? t('postingOps.metric.seeTable') : '—'} />
      <Metric label={t('postingOps.metric.lastPosted')} value={opsTime(lastPosted)} />
      <Metric label={t('postingOps.metric.streamCount')} value={count(String(streams.length))} />
    </SegmentCard>
    {arrow}
    <SegmentCard index={3} title={t('postingOps.segment.ledger')} tone={toneOf(streamSegment.status)} badge={ledgerBadge} onRetry={onRetry}
      unavailable={accountUnavailable ? t('postingOps.segment.ledgerUnavailable') : undefined}>
      <Metric label={t('postingOps.metric.ledgerConsumed')} value={single ? count(single.ledgerConsumedSequence)
        : streams.length ? t('postingOps.metric.streamsConsistent', { consistent: streams.length - behind.length, total: streams.length }) : '—'} />
      <Metric label={t('postingOps.metric.lag')} value={count(totalLag)} strong={totalLag > 0n} />
      {stuckSince && <Metric label={t('postingOps.metric.stuckSince')} value={opsTime(stuckSince)} />}
    </SegmentCard>
  </div>;
}

/** ① 计费事实入账：billing 已发布 / account 已应用（计数 · 最大事实号）与 billing 未发布；落后时徽标显示差额；不可用时说明是哪个服务。 */
function FactCard({ progress, onRetry }: { progress: PostingProgress; onRetry: () => void }) {
  const t = useT('finance');
  const fact: FactSegment = progress.factSegment;
  const cause = factUnavailableCause(progress);
  const gap = gapOf(fact.billingPublishedCount, fact.accountAppliedCount);
  const badge = fact.status === 'CONSISTENT' ? t('postingOps.badge.consistent') : fact.status === 'UNAVAILABLE' ? t('postingOps.badge.unavailable')
    : gap > 0n ? t('postingOps.badge.gap', { count: count(gap) }) : t('postingOps.badge.behind');
  return <SegmentCard index={1} title={t('postingOps.segment.fact')} tone={toneOf(fact.status)} badge={badge} onRetry={onRetry}
    unavailable={cause === 'ACCOUNT' ? t('postingOps.segment.factUnavailableAccount') : cause === 'BILLING' ? t('postingOps.segment.factUnavailable') : undefined}>
    <Metric label={t('postingOps.metric.billingPublished')}
      value={t('postingOps.metric.countWithMax', { count: count(fact.billingPublishedCount), max: factTail(fact.billingMaxPublishedFactId) })} />
    <Metric label={t('postingOps.metric.accountApplied')} strong={gap > 0n}
      value={t('postingOps.metric.countWithMax', { count: count(fact.accountAppliedCount), max: factTail(fact.accountMaxAppliedFactId) })} />
    <Metric label={t('postingOps.metric.billingPending')} value={count(fact.billingPendingCount)} strong={fact.billingPendingCount !== '0'} />
  </SegmentCard>;
}

const latest = (values: (string | null)[]) => values.filter((v): v is string => !!v).sort((a, b) => Date.parse(b) - Date.parse(a))[0] ?? null;
const earliest = (values: (string | null)[]) => values.filter((v): v is string => !!v).sort((a, b) => Date.parse(a) - Date.parse(b))[0] ?? null;

export interface StreamActions {
  canCatchUp: boolean; running: string | null;
  onCatchUp: (stream: PostingStream) => void; onBrowse: (stream: PostingStream) => void;
}

/** 流行操作：落后的流有执行权限时为「追平」，没有时为「需执行权限」；任何追平进行中时全部禁用，避免并发请求。 */
function StreamActionCell({ stream, actions, block }: { stream: PostingStream; actions: StreamActions; block?: boolean }) {
  const t = useT('finance');
  const catchUp = stream.status !== 'BEHIND' ? null : actions.canCatchUp
    ? <Button type="primary" size={block ? 'middle' : 'small'} block={block} icon={<FastForwardOutlined />} loading={actions.running === stream.streamId}
      disabled={actions.running !== null && actions.running !== stream.streamId} onClick={() => actions.onCatchUp(stream)}>{t('postingOps.action.catchUp')}</Button>
    : <span className="posting-ops-locked"><LockOutlined /> {t('postingOps.action.needsPermission')}</span>;
  const browse = block ? <Button block onClick={() => actions.onBrowse(stream)}>{t('postingOps.action.browse')}</Button>
    : <a onClick={() => actions.onBrowse(stream)}>{t('postingOps.action.browse')}</a>;
  return <div className={block ? 'posting-ops-card-actions' : 'posting-ops-row-actions'}>{catchUp}{browse}</div>;
}

function StreamName({ stream, bookEntityType }: { stream: PostingStream; bookEntityType: string }) {
  const t = useT('finance');
  return <div><span className="mono">{stream.streamId}</span>
    {stream.entityType !== bookEntityType && <div className="cell-meta">{t('postingOps.stream.feeOe')}</div>}</div>;
}

function StreamStatusTag({ stream }: { stream: PostingStream }) {
  const t = useT('finance');
  return <Tag color={stream.status === 'BEHIND' ? 'gold' : 'green'}>{enumLabel(t, 'postingOps.enum.streamStatus', stream.status)}</Tag>;
}

/** 过账流表（宽屏）与过账流卡片（窄屏 390，画板 32），两者由 CSS 按宽度切换。 */
export function StreamList({ progress, actions }: { progress: PostingProgress; actions: StreamActions }) {
  const t = useT('finance');
  const streams = progress.streamSegment.streams;
  return <>
    <section className="table-panel posting-ops-table">
      <Table<PostingStream> rowKey="streamId" tableLayout="fixed" dataSource={streams} pagination={false} scroll={{ x: 980 }}
        rowClassName={row => row.status === 'BEHIND' ? 'posting-ops-row-behind' : ''}
        locale={{ emptyText: progress.streamSegment.status === 'UNAVAILABLE' ? t('postingOps.stream.unavailable') : t('postingOps.stream.empty') }} columns={[
          { title: t('postingOps.column.stream'), width: 220, render: (_, row) => <StreamName stream={row} bookEntityType={progress.entityType} /> },
          { title: t('postingOps.column.accountMaxSequence'), width: 130, render: (_, row) => count(row.accountMaxSequence) },
          { title: t('postingOps.column.lastPosted'), width: 170, render: (_, row) => opsTime(row.accountLastPostedTime) },
          { title: t('postingOps.column.ledgerConsumed'), width: 120, render: (_, row) => count(row.ledgerConsumedSequence) },
          { title: t('postingOps.column.lag'), width: 80, render: (_, row) => <span className={row.lag !== '0' ? 'posting-ops-lag' : ''}>{count(row.lag)}</span> },
          { title: t('postingOps.column.status'), width: 90, render: (_, row) => <StreamStatusTag stream={row} /> },
          { title: t('postingOps.column.actions'), width: 200, render: (_, row) => <StreamActionCell stream={row} actions={actions} /> },
        ]} />
    </section>
    <div className="posting-ops-cards">
      {!streams.length && <div className="posting-ops-card is-empty">{progress.streamSegment.status === 'UNAVAILABLE' ? t('postingOps.stream.unavailable') : t('postingOps.stream.empty')}</div>}
      {streams.map(stream => <article key={stream.streamId} className={`posting-ops-card${stream.status === 'BEHIND' ? ' is-behind' : ''}`}>
        <header><StreamName stream={stream} bookEntityType={progress.entityType} />
          {stream.status === 'BEHIND' ? <Tag color="gold">{t('postingOps.badge.behindBatches', { count: count(stream.lag) })}</Tag> : <StreamStatusTag stream={stream} />}</header>
        <dl>
          <div><dt>{t('postingOps.card.account')}</dt><dd>{count(stream.accountMaxSequence)}</dd></div>
          <div><dt>{t('postingOps.card.ledger')}</dt><dd>{count(stream.ledgerConsumedSequence)}</dd></div>
          {stream.stuckPosting && <div><dt>{t('postingOps.card.stuck')}</dt><dd>{t('postingOps.card.stuckSequence', { sequence: count(stream.stuckPosting.postingSequence) })}</dd></div>}
        </dl>
        {stream.stuckPosting && <p className="cell-meta">{t('postingOps.card.stuckSummary', {
          factType: stream.stuckPosting.factType ?? enumLabel(t, 'enum.postingSource', stream.stuckPosting.sourceType),
          factId: stream.stuckPosting.factId ?? '—', time: opsTime(stream.stuckPosting.postedTime), count: Number(stream.stuckPosting.entryCount) })}</p>}
        <StreamActionCell stream={stream} actions={actions} block />
      </article>)}
    </div>
  </>;
}

/** 批次的「应用结果」：事实批次为已应用 / 已跳过，桶移动批次没有。 */
export function applyStatusLabel(t: ReturnType<typeof useT>, posting: Pick<PostingSummary, 'factApplyStatus'>): string {
  return posting.factApplyStatus ? enumLabel(t, 'postingOps.enum.applyStatus', posting.factApplyStatus) : '—';
}

/**
 * 卡住的批次卡片（画板 23、24、31）：ledger 已消费 + 1 的 account 批次；规则拒绝时卡片内显示错误码与说明并指引先排除原因再重放
 * <li>只读账号提示联系有执行权限的财务运营；查看流水仍可用。</li>
 */
export function StuckPostingCard({ stream, outcome, progress, checkedAt, canCatchUp, running, onViewEntries, onCatchUp }: {
  stream: PostingStream; outcome?: CatchUpOutcome; progress: PostingProgress; checkedAt: string | null; canCatchUp: boolean; running: string | null;
  onViewEntries: (postingId: string) => void; onCatchUp: () => void;
}) {
  const t = useT('finance');
  const posting = stream.stuckPosting;
  const rejected = outcome?.kind === 'rejected' ? outcome : null;
  const advanced = rejected ? advancedBeforeRejection(rejected, progress, checkedAt, stream.streamId) : null;
  if (!posting) return rejected ? <div className="posting-ops-stuck is-rejected"><RejectedPanel outcome={rejected} advanced={advanced} canCatchUp={canCatchUp}
    running={running} streamId={stream.streamId} onCatchUp={onCatchUp} /></div> : null;
  const consumed = count(stream.ledgerConsumedSequence);
  return <section className={`posting-ops-stuck${rejected ? ' is-rejected' : ''}`} aria-label={t('postingOps.stuck.title')}>
    <header><PauseCircleOutlined /><h3>{t('postingOps.stuck.heading', { streamId: stream.streamId, sequence: count(posting.postingSequence) })}</h3>
      <a onClick={() => onViewEntries(posting.postingId)}>{t('postingOps.stuck.viewEntries')} <ArrowRightOutlined /></a></header>
    <dl className="posting-ops-stuck-fields">
      <div><dt>{t('postingOps.field.source')}</dt><dd>{enumLabel(t, 'enum.postingSource', posting.sourceType)}</dd></div>
      <div><dt>{t('postingOps.field.factId')}</dt><dd className="mono">{posting.factId ?? '—'}</dd></div>
      <div><dt>{t('postingOps.field.factType')}</dt><dd>{posting.factType ?? '—'}{posting.factApplyStatus && ` · ${applyStatusLabel(t, posting)}`}</dd></div>
      <div><dt>{t('postingOps.field.postedAt')}</dt><dd>{opsTime(posting.postedTime)}</dd></div>
      <div><dt>{t('postingOps.field.accountPosted')}</dt><dd className={posting.publishedTime ? '' : 'posting-ops-warn-text'}>
        {posting.publishedTime ? t('postingOps.stuck.publishedAt', { time: opsTime(posting.publishedTime) }) : t('postingOps.published.no')}</dd></div>
      <div><dt>{t('postingOps.field.entries')}</dt><dd>{t('postingOps.stuck.entryCount', { count: Number(posting.entryCount) })}</dd></div>
    </dl>
    {rejected ? <RejectedPanel outcome={rejected} advanced={advanced} canCatchUp={canCatchUp} running={running} streamId={stream.streamId} onCatchUp={onCatchUp} />
      : <p className="posting-ops-stuck-note">{canCatchUp ? t('postingOps.stuck.note', { consumed }) : t('postingOps.stuck.readOnlyNote', { consumed })}
        {!posting.publishedTime && ' ' + t('postingOps.stuck.notPublishedNote')}</p>}
  </section>;
}

/**
 * 规则 / 数据拒绝（400）：原样展示 ledger 的错误码与说明、执行人与时间；「前往记账规则」与「重新追平」
 * <li>ledger 的 400 不带处理数：进度在拒绝后刷新完成才说明本次是否处理了批次（advanced 为 null 时不下结论）。</li>
 */
function RejectedPanel({ outcome, advanced, canCatchUp, running, streamId, onCatchUp }: {
  outcome: Extract<CatchUpOutcome, { kind: 'rejected' }>; advanced: bigint | null; canCatchUp: boolean; running: string | null; streamId: string;
  onCatchUp: () => void;
}) {
  const t = useT('finance');
  const processed = advanced === null ? '' : advanced === 0n ? t('postingOps.catchUp.rejectedNone')
    : t('postingOps.catchUp.rejectedAdvanced', { count: Number(advanced) });
  return <div className="posting-ops-rejected" role="alert">
    <div className="posting-ops-rejected-title"><CloseCircleOutlined /><strong>{t('postingOps.catchUp.rejectedTitle')}</strong>
      <span className="cell-meta">{t('postingOps.result.byAt', { time: opsTime(outcome.at), operator: outcome.by })}</span></div>
    <p><span className="mono">{outcome.code}</span>{outcome.detail ? `: ${outcome.detail}` : ''}{processed && ` ${processed}`}</p>
    <div className="posting-ops-rejected-actions"><span className="cell-meta">{t('postingOps.catchUp.fixFirst')}</span>
      <Link to="/finance/accounting-rules">{t('postingOps.catchUp.goToRules')} <ArrowRightOutlined /></Link>
      {canCatchUp && <Button size="small" icon={<ReloadOutlined />} loading={running === streamId} disabled={running !== null && running !== streamId}
        onClick={onCatchUp}>{t('postingOps.catchUp.retry')}</Button>}</div>
  </div>;
}

/**
 * 追平结果条（画板 24）：部分追平带「继续追平 · 剩余 N」，剩余数 = account 最大序号 − 本次处理后序号；剩余为 0 时按已追平显示
 * <li>每条带执行人与时间；规则拒绝在卡住批次卡片内显示。</li>
 */
export function CatchUpResultBar({ outcome, progress, streamId, canCatchUp, running, onContinue }: {
  outcome: CatchUpOutcome; progress: PostingProgress; streamId: string; canCatchUp: boolean; running: string | null; onContinue: () => void;
}) {
  const t = useT('finance');
  const meta = t('postingOps.result.byAt', { time: opsTime(outcome.at), operator: outcome.by });
  const kind = catchUpDisplayKind(outcome, progress, streamId);
  if (outcome.kind === 'rejected') return null;
  if (outcome.kind === 'conflict') return <ResultBar tone="warning" icon={<HourglassOutlined />} title={t('postingOps.catchUp.conflictTitle')}
    text={t('postingOps.catchUp.conflictText')} meta={meta} />;
  if (outcome.kind === 'timeout') return <ResultBar tone="warning" icon={<HourglassOutlined />} title={t('postingOps.catchUp.timeoutTitle')}
    text={t('postingOps.catchUp.timeoutText')} meta={meta} />;
  if (kind === 'done') return <ResultBar tone="success" icon={<CheckCircleOutlined />} title={t('postingOps.catchUp.doneTitle')}
    text={t('postingOps.catchUp.doneText', { processed: Number(outcome.processedCount), last: count(outcome.lastSequence) })} meta={meta} />;
  const remaining = remainingAfterCatchUp(progress, streamId, outcome.lastSequence);
  const stream = progress.streamSegment.streams.find(item => item.streamId === streamId);
  return <ResultBar tone="info" icon={<LoadingOutlined spin={false} />} title={t('postingOps.catchUp.partialTitle', { processed: Number(outcome.processedCount) })}
    text={t('postingOps.catchUp.partialText', { last: count(outcome.lastSequence), max: count(stream?.accountMaxSequence), remaining: count(remaining) })} meta={meta}
    action={canCatchUp ? <Button type="primary" size="small" loading={running === streamId} disabled={running !== null && running !== streamId}
      onClick={onContinue}>{remaining === null ? t('postingOps.catchUp.continue') : t('postingOps.catchUp.continueRemaining', { remaining: count(remaining) })}</Button> : undefined} />;
}

function ResultBar({ tone, icon, title, text, meta, action, onClose }: {
  tone: 'success' | 'info' | 'warning' | 'neutral'; icon: ReactNode; title: ReactNode; text: ReactNode; meta?: string; action?: ReactNode; onClose?: () => void;
}) {
  return <Alert className={`posting-ops-result is-${tone}`} type={tone === 'neutral' ? 'info' : tone} icon={icon} showIcon
    title={<span><strong>{title}</strong><span className="posting-ops-result-text">{text}</span>
      {meta && <span className="posting-ops-result-meta">{meta}</span>}</span>} action={action}
    closable={!!onClose} onClose={onClose} />;
}

/** 重建结果条（画板 26），带执行人与时间，显示在页顶直到关闭或换账簿。 */
export function RebuildResultBar({ outcome, bookLabel, onClose }: { outcome: RebuildOutcome; bookLabel: string; onClose: () => void }) {
  const t = useT('finance');
  const meta = t('postingOps.result.byAt', { time: opsTime(outcome.at), operator: outcome.by });
  switch (outcome.kind) {
    case 'corrected': case 'recalculated': {
      const r = outcome.result;
      return <ResultBar tone="success" icon={<CheckCircleOutlined />} onClose={onClose} meta={meta}
        title={outcome.kind === 'corrected' ? t('postingOps.rebuild.correctedTitle', { accounts: Number(r.accountCount), changed: Number(r.changedAccountCount) })
          : t('postingOps.rebuild.recalculatedTitle', { accounts: Number(r.accountCount) })}
        text={richText(t, 'postingOps.rebuild.resultText', {
          balances: <Link to="/finance/balances">{t('postingOps.rebuild.balancesLink')}</Link>,
          subledger: <Link to="/finance/subledger">{t('postingOps.rebuild.subledgerLink')}</Link>,
        }, { entries: Number(r.entryCount), max: count(r.maxPostingSequence) })} />;
    }
    case 'unchanged':
      return <ResultBar tone="success" icon={<CheckCircleOutlined />} onClose={onClose} meta={meta} title={t('postingOps.rebuild.unchangedTitle')}
        text={t('postingOps.rebuild.unchangedText', { accounts: Number(outcome.result.accountCount) })} />;
    case 'noStream':
      return <ResultBar tone="neutral" icon={<InfoCircleOutlined />} onClose={onClose} meta={meta} title={t('postingOps.rebuild.noStreamTitle')}
        text={t('postingOps.rebuild.noStreamText', { book: bookLabel })} />;
    case 'conflict':
      return <ResultBar tone="warning" icon={<HourglassOutlined />} onClose={onClose} meta={meta} title={t('postingOps.rebuild.conflictTitle')}
        text={t('postingOps.rebuild.conflictText')} />;
    case 'timeout':
      return <ResultBar tone="warning" icon={<HourglassOutlined />} onClose={onClose} meta={meta} title={t('postingOps.rebuild.timeoutTitle')}
        text={t('postingOps.rebuild.timeoutText')} />;
  }
}

/** 重建确认（画板 25）：一句用途、三条影响，确认后才调用。 */
export function RebuildConfirm({ open, bookLabel, entityKind, saving, onCancel, onConfirm }: {
  open: boolean; bookLabel: string; entityKind: string; saving: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  const t = useT('finance');
  return <Modal open={open} width={520} onCancel={onCancel} onOk={onConfirm} confirmLoading={saving} destroyOnHidden
    okText={t('postingOps.rebuild.confirm')} cancelText={t('common.cancel')} rootClassName="posting-ops-modal"
    title={<div className="posting-ops-modal-title"><span className="posting-ops-modal-icon"><SyncOutlined /></span>
      <div><div>{t('postingOps.rebuild.title')}</div><div className="cell-meta">{t('postingOps.rebuild.subtitle', { book: bookLabel, kind: entityKind })}</div></div></div>}>
    <p>{t('postingOps.rebuild.purpose')}</p>
    <ul className="posting-ops-impacts">
      <li><CheckOutlined className="is-ok" />{t('postingOps.rebuild.impactNoChange')}</li>
      <li><CheckOutlined className="is-ok" />{t('postingOps.rebuild.impactRepeatable')}</li>
      <li><InfoCircleOutlined className="is-warn" />{t('postingOps.rebuild.impactWait')}</li>
    </ul>
  </Modal>;
}
