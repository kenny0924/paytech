import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Select, Spin } from 'antd';
import { BranchesOutlined, DisconnectOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import { useLocation, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { financeCommand, sendFinanceCommand } from '../finance/finance';
import type { AccountPosting, AccountPostingEntry, LedgerBook } from '../finance/finance';
import { FinanceNote } from '../finance/FinanceParts';
import {
  browseStartSequence, catchUpOutcome, decodeCatchUpResult, decodeRebuildResult, hasPostingOpsAction, listFailureNotice, postingOpsActions, postingOpsPaths,
  postingOpsSearch, postingStreamRoute, readPostingOpsQuery, rebuildBody, rebuildOutcome, recallBookLabel, rememberBookLabel, streamBrowseSearch,
} from '../finance/postingOps';
import type { BookLabelStorage, CatchUpOutcome, PostingProgress, PostingStream, RebuildOutcome } from '../finance/postingOps';
import {
  CatchUpResultBar, opsTime, RebuildConfirm, RebuildResultBar, SegmentCards, StreamList, StuckPostingCard,
} from '../finance/PostingProgressParts';
import { PostingStreamBrowse, openSubledgerAccount } from '../finance/PostingStreamBrowse';
import { SubledgerPostingDrawer } from '../finance/SubledgerPostingDrawer';
import { useCurrencies, useFinanceEntities, useLedgerBooks } from '../finance/useFinanceData';
import type { ListState } from '../finance/useFinanceData';
import { usePostingProgress } from '../finance/usePostingProgress';
import type { PostingProgressState } from '../finance/usePostingProgress';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../finance/postingOps.css';

/** actorLabel：当前登录人员的显示名（与页头一致），写在追平与重建结果条上作为执行人。 */
interface Props { resources: ConsoleResource[]; actorId: string; actorLabel: string; onAuthorizationChanged: () => void }

/**
 * 明细账运维（R1—R5，画板 22—32）：选账簿后看 billing → account → ledger 三段进度，对落后的流追平、对账簿重建余额；
 * 子路由 /streams/:streamId 为过账流批次浏览
 * <li>账簿选择在 URL（?ledgerBookId=），返回进度页时恢复；进度数字以 Owner 返回为准，页面只做对比展示。</li>
 * <li>执行类操作单飞：同一时刻只发一个追平或重建请求（运营路由每人并发上限 2）。</li>
 */
export function FinancePostingOps(props: Props) {
  const { streamId } = useParams();
  if (streamId !== undefined) return <PostingStreamBrowse key={streamId} streamId={streamId} onAuthorizationChanged={props.onAuthorizationChanged} />;
  return <PostingProgressView {...props} />;
}

function PostingProgressView({ resources, actorLabel, onAuthorizationChanged }: Props) {
  const t = useT('finance'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate();
  const { ledgerBookId } = readPostingOpsQuery(new URLSearchParams(location.search));
  const canCatchUp = hasPostingOpsAction(resources, postingOpsActions.catchUp);
  const canRebuild = hasPostingOpsAction(resources, postingOpsActions.rebuild);
  const progress = usePostingProgress(ledgerBookId, onAuthorizationChanged);
  // 两个列表与进度同样依赖 ledger：失败不弹通知，由整页报错或工具栏下的提示统一说明（ledger 不可用时不再叠加两条通知）
  const entities = useFinanceEntities(true, onAuthorizationChanged, false);
  const books = useLedgerBooks(onAuthorizationChanged, false);
  const listNotice = listFailureNotice(ledgerBookId && progress.error, books.error, entities.error);
  const bookLabel = progress.data ? bookTitle(progress.data, entities.byId) : '';
  const [rebuilding, setRebuilding] = useState(false);
  const rebuild = useRebuild(progress, actorLabel, onAuthorizationChanged);
  return <div className="configuration-page finance-page posting-ops-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_POSTING_OPS')}</h1>
      {canRebuild && <Button icon={<SyncOutlined />} disabled={!progress.data || rebuild.saving} loading={rebuild.saving}
        onClick={() => setRebuilding(true)}>{t('postingOps.rebuild.button')}</Button>}</div>
    <p className="posting-ops-intro">{t('postingOps.intro')}</p>
    {rebuild.outcome && rebuild.outcome.ledgerBookId === ledgerBookId && <RebuildResultBar outcome={rebuild.outcome.value} bookLabel={bookLabel}
      onClose={rebuild.clear} />}
    <div className="posting-ops-toolbar">
      <BookPicker value={ledgerBookId} books={books} entities={entities.byId} progress={progress.data}
        onChange={value => void navigate({ search: postingOpsSearch(value) })} />
      {progress.checkedAt && <span className="cell-meta posting-ops-checked">{t('postingOps.checkedAt', { time: opsTime(progress.checkedAt) })}
        <Button type="link" size="small" icon={<ReloadOutlined />} loading={progress.loading} onClick={progress.reload}>{t('common.refresh')}</Button></span>}
    </div>
    {listNotice && <Alert className="finance-banner" type="warning" showIcon title={t('postingOps.failure.listsTitle')} description={listNotice}
      action={<Button size="small" icon={<ReloadOutlined />} onClick={() => { books.reload(); entities.reload(); }}>{t('common.retry')}</Button>} />}
    {!ledgerBookId ? <div className="finance-empty posting-ops-empty"><span className="posting-ops-empty-icon"><BranchesOutlined /></span>
      <h3>{t('postingOps.idle.title')}</h3><p className="cell-meta">{t('postingOps.idle.text')}</p></div>
      : progress.error ? <ProgressFailure error={progress.error} loading={progress.loading} onRetry={progress.reload} />
      : !progress.data ? <div className="posting-ops-loading"><Spin /></div>
      : <ProgressBody key={ledgerBookId} progress={progress.data} checkedAt={progress.checkedAt} reload={progress.reload} canCatchUp={canCatchUp} actorLabel={actorLabel}
        onAuthorizationChanged={onAuthorizationChanged}
        onBrowse={stream => void navigate({ pathname: postingStreamRoute(stream.streamId), search: streamBrowseSearch(ledgerBookId, browseStartSequence(stream)) })} />}
    <FinanceNote>{t('postingOps.note')}</FinanceNote>
    {progress.data && <RebuildConfirm open={rebuilding} bookLabel={bookLabel} saving={rebuild.saving}
      entityKind={enumLabel(t, 'postingOps.enum.bookKind', progress.data.entityType)} onCancel={() => setRebuilding(false)}
      onConfirm={() => { setRebuilding(false); void rebuild.run(); }} />}
  </div>;
}

/**
 * 账簿下拉（画板 28）：全部账簿按主体 + 币种搜索，财务主体显示主体代码
 * <li>已选账簿不在列表中（账簿列表与进度同样依赖 ledger，ledger 不可用时读不到）时，用已加载的进度或本标签页记住的「主体 · 币种」显示，不显示主键。</li>
 */
function BookPicker({ value, books, entities, progress, onChange }: {
  value: string | null; books: ListState<LedgerBook>; entities: ReadonlyMap<string, { entityCode: string }>; progress: PostingProgress | null;
  onChange: (ledgerBookId: string) => void;
}) {
  const t = useT('finance');
  const storage = useMemo(browserSessionStorage, []);
  const listed = value ? books.items.find(book => book.ledgerBookId === value) : undefined;
  const known = listed ?? (progress && progress.ledgerBookId === value ? progress : undefined);
  const knownLabel = known ? bookTitle(known, entities) : null;
  useEffect(() => { if (value && knownLabel) rememberBookLabel(storage, value, knownLabel); }, [storage, value, knownLabel]);
  const options = useMemo(() => {
    const items = books.items.map(book => {
      const title = bookTitle(book, entities);
      const kind = book.entityType === 'FINANCE_ENTITY' && entities.get(book.entityId)
        ? t('postingOps.book.financeEntityKind', { code: entities.get(book.entityId)!.entityCode }) : enumLabel(t, 'postingOps.enum.bookKind', book.entityType);
      return { value: book.ledgerBookId, search: `${title} ${kind}`, label: <span className="posting-ops-book-option"><strong>{title}</strong><span className="cell-meta">{kind}</span></span> };
    });
    const fallback = value && !listed ? knownLabel ?? recallBookLabel(storage, value) : null;
    return fallback && value ? [...items, { value, search: fallback, label: <span className="posting-ops-book-option"><strong>{fallback}</strong></span> }] : items;
  }, [books.items, entities, t, value, listed, knownLabel, storage]);
  return <label className="posting-ops-book"><span className="posting-ops-label">{t('postingOps.book.label')}</span>
    <Select<string> showSearch={{ optionFilterProp: 'search' }} value={value ?? undefined} loading={books.loading} options={options}
      placeholder={t('postingOps.book.placeholder')} notFoundContent={books.error ? books.error : undefined} onChange={onChange} /></label>;
}

/** 本标签页的会话存储；不可访问时为空（只影响账簿显示名的记忆）。 */
function browserSessionStorage(): BookLabelStorage | undefined {
  try { return typeof window === 'undefined' ? undefined : window.sessionStorage; }
  catch { return undefined; }
}

/** 账簿标题「主体 · 币种」：财务主体显示主体代码（未加载时显示主键）。 */
function bookTitle(book: Pick<LedgerBook, 'entityType' | 'entityId' | 'currency'>, entities: ReadonlyMap<string, { entityCode: string }>): string {
  const entity = book.entityType === 'FINANCE_ENTITY' ? entities.get(book.entityId)?.entityCode ?? book.entityId : book.entityId;
  return `${entity} · ${book.currency}`;
}

/** ledger 不可用或账簿不存在时整页报错（画板 29 下半），保留账簿选择可重试。 */
function ProgressFailure({ error, loading, onRetry }: { error: unknown; loading: boolean; onRetry: () => void }) {
  const t = useT('finance');
  const description = error instanceof ApiError
    ? (error.status === 503 ? t('postingOps.failure.ledgerUnavailable', { status: error.status, code: error.code })
      : t('postingOps.failure.withCode', { message: error.detail || error.message, code: error.code }))
    : t('postingOps.failure.generic');
  return <div className="finance-empty posting-ops-failure" role="alert"><span className="posting-ops-failure-icon"><DisconnectOutlined /></span>
    <h3>{t('postingOps.failure.title')}</h3><p className="cell-meta">{description}</p>
    <Button icon={<ReloadOutlined />} loading={loading} onClick={onRetry}>{t('common.retry')}</Button></div>;
}

/**
 * 三段卡片、过账流表与每条落后流的「卡住的批次」及追平结果；追平先简短确认，成功或部分成功后刷新进度
 * <li>结果条保留到换账簿为止；剩余落后数用刷新后的进度计算。</li>
 */
function ProgressBody({ progress, checkedAt, reload, canCatchUp, actorLabel, onBrowse, onAuthorizationChanged }: {
  progress: PostingProgress; checkedAt: string | null; reload: () => void; canCatchUp: boolean; actorLabel: string; onBrowse: (stream: PostingStream) => void;
  onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const { modal } = App.useApp();
  const notify = useErrorNotification();
  const navigate = useNavigate();
  const currencies = useCurrencies(onAuthorizationChanged);
  const [outcomes, setOutcomes] = useState<Record<string, CatchUpOutcome>>({});
  const [running, setRunning] = useState<string | null>(null);
  const [posting, setPosting] = useState<string | null>(null);
  const busy = useRef(false);
  async function catchUp(streamId: string) {
    if (busy.current) return;
    busy.current = true; setRunning(streamId);
    const at = new Date().toISOString();
    const consumedBefore = progress.streamSegment.streams.find(stream => stream.streamId === streamId)?.ledgerConsumedSequence ?? null;
    let outcome: CatchUpOutcome | null;
    try {
      outcome = catchUpOutcome(await sendFinanceCommand(financeCommand(postingOpsPaths.catchUp(streamId), undefined), decodeCatchUpResult), at, actorLabel);
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      outcome = catchUpOutcome(error, at, actorLabel, consumedBefore);
      if (!outcome) notify(error, t('postingOps.catchUp.failed'));
    } finally { busy.current = false; setRunning(null); }
    if (outcome) setOutcomes(current => ({ ...current, [streamId]: outcome }));
    // 已处理的批次都已提交（含 409 / 超时 / 规则拒绝前的批次），刷新后在进度上可见；规则拒绝据刷新后的已消费序号说明本次是否处理了批次
    if (outcome) reload();
  }
  const confirmCatchUp = (stream: PostingStream) => {
    if (busy.current) return;
    modal.confirm({
      title: t('postingOps.catchUp.confirmTitle', { streamId: stream.streamId }), content: t('postingOps.catchUp.confirmText'),
      okText: t('postingOps.action.catchUp'), cancelText: t('common.cancel'), onOk: () => { void catchUp(stream.streamId); },
    });
  };
  const actions = { canCatchUp, running, onCatchUp: confirmCatchUp, onBrowse };
  const panels = progress.streamSegment.streams.filter(stream => stream.stuckPosting || outcomes[stream.streamId]);
  return <>
    <SegmentCards progress={progress} onRetry={reload} />
    <div className="finance-section-head posting-ops-section-head"><h2>{t('postingOps.stream.title')}</h2>
      <span className="cell-meta">{progress.entityType === 'FINANCE_ENTITY' ? t('postingOps.stream.feHint') : t('postingOps.stream.hint')}</span></div>
    <StreamList progress={progress} actions={actions} />
    {panels.map(stream => <div key={stream.streamId} className="posting-ops-panel">
      <StuckPostingCard stream={stream} outcome={outcomes[stream.streamId]} progress={progress} checkedAt={checkedAt} canCatchUp={canCatchUp} running={running}
        onViewEntries={setPosting} onCatchUp={() => void catchUp(stream.streamId)} />
      {outcomes[stream.streamId] && <CatchUpResultBar outcome={outcomes[stream.streamId]} progress={progress} streamId={stream.streamId}
        canCatchUp={canCatchUp} running={running} onContinue={() => void catchUp(stream.streamId)} />}
    </div>)}
    {posting && <SubledgerPostingDrawer postingId={posting} highlightEntryId={null} minorUnits={currencies.minorUnits}
      onOpenAccount={(value: AccountPosting, entry: AccountPostingEntry) => openSubledgerAccount(navigate, value, entry)}
      onClose={() => setPosting(null)} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

/** 重建余额：单飞；结果（含 STREAM_NOT_FOUND、409、超时）显示在页顶，成功后刷新进度；其他错误走通用通知。 */
function useRebuild(progress: PostingProgressState, actorLabel: string, onAuthorizationChanged: () => void) {
  const t = useT('finance');
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false);
  const [outcome, setOutcome] = useState<{ ledgerBookId: string; value: RebuildOutcome } | null>(null);
  const busy = useRef(false);
  async function run() {
    const data = progress.data;
    if (busy.current || !data) return;
    busy.current = true; setSaving(true); setOutcome(null);
    const at = new Date().toISOString();
    let value: RebuildOutcome | null;
    try {
      value = rebuildOutcome(await sendFinanceCommand(financeCommand(postingOpsPaths.rebuild, rebuildBody(data)), decodeRebuildResult), at, actorLabel);
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      value = rebuildOutcome(error, at, actorLabel);
      if (!value) notify(error, t('postingOps.rebuild.failed'));
    } finally { busy.current = false; setSaving(false); }
    if (value) setOutcome({ ledgerBookId: data.ledgerBookId, value });
    if (value && value.kind !== 'noStream' && value.kind !== 'conflict') progress.reload();
  }
  return { saving, outcome, run, clear: () => setOutcome(null) };
}
