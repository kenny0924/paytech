import { useEffect, useRef, useState } from 'react';
import { Alert, Button, InputNumber, Table, Tag } from 'antd';
import { ArrowLeftOutlined } from '@ant-design/icons';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import type { NavigateFunction } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { subledgerLevelSearch } from './finance';
import type { AccountPosting, AccountPostingEntry, SubledgerScope } from './finance';
import { FinanceNote, LoadFailure } from './FinanceParts';
import {
  browsePageSize, decodePostingPage, postingOpsRoute, postingOpsSearch, postingOpsPaths, readStreamBrowseQuery, streamBrowseSearch,
  streamIdPattern, stuckSequenceOf,
} from './postingOps';
import type { PostingSummary } from './postingOps';
import { applyStatusLabel, count, opsTime } from './PostingProgressParts';
import { SubledgerPostingDrawer } from './SubledgerPostingDrawer';
import { useCurrencies } from './useFinanceData';
import { usePostingProgress } from './usePostingProgress';
import '../configuration/configuration.css';
import './finance.css';
import './postingOps.css';

/** 批次抽屉「账户流水」：进入明细账页该流水所属账户（与明细账查询页一致）。 */
export function openSubledgerAccount(navigate: NavigateFunction, posting: AccountPosting, entry: AccountPostingEntry) {
  const scope: SubledgerScope = { entityType: posting.entityType, entityId: posting.entityId, currency: posting.currency };
  void navigate({ pathname: '/finance/subledger', search: subledgerLevelSearch('', 'entries', { account: entry.accountId, scope }) }, { state: { account: {
    accountId: entry.accountId, accountNo: entry.accountNo, accountCode: entry.accountCode, ownerType: entry.ownerType, ownerId: entry.ownerId,
    balanceBucket: entry.balanceBucket } } });
}

/**
 * 过账流批次浏览（R2，画板 27）：按序号升序，每页 50 批，「加载后 50 批」按 nextFromSequence 追加；点「查看流水」打开批次抽屉
 * <li>起始序号与打开的批次在 URL；带账簿时读取进度，显示流头指标并把「卡住」标在 ledger 已消费 + 1 那一批。</li>
 * <li>追加请求串行进行，不并发翻页。</li>
 */
export function PostingStreamBrowse({ streamId, onAuthorizationChanged }: { streamId: string; onAuthorizationChanged: () => void }) {
  const t = useT('finance');
  const location = useLocation(); const navigate = useNavigate();
  const query = readStreamBrowseQuery(new URLSearchParams(location.search));
  const validStream = streamIdPattern.test(streamId);
  const progress = usePostingProgress(validStream ? query.ledgerBookId : null, onAuthorizationChanged);
  const stream = progress.data?.streamSegment.streams.find(item => item.streamId === streamId);
  const stuckSequence = stuckSequenceOf(stream);
  const currencies = useCurrencies(onAuthorizationChanged);
  const pages = useStreamPages(validStream ? streamId : null, query.fromSequence, onAuthorizationChanged);
  const [jump, setJump] = useState<string | null>(null);
  const backSearch = postingOpsSearch(query.ledgerBookId);
  const go = (fromSequence: string, posting: string | null = null, replace = false) =>
    void navigate({ search: streamBrowseSearch(query.ledgerBookId, fromSequence, posting) }, { replace });
  const items = pages.items;
  const range = items.length ? t('postingOps.browse.range', { from: count(items[0].postingSequence), to: count(items[items.length - 1].postingSequence) }) : '';
  return <div className="configuration-page finance-page posting-ops-page">
    <Link className="posting-ops-back" to={{ pathname: postingOpsRoute, search: backSearch }}><ArrowLeftOutlined /> {progress.data
      ? t('postingOps.browse.backWithBook', { book: `${progress.data.entityId} · ${progress.data.currency}` }) : t('postingOps.browse.back')}</Link>
    <div className="page-heading"><h1>{t('postingOps.browse.title', { streamId })}</h1></div>
    {!validStream ? <Alert type="warning" showIcon title={t('postingOps.browse.invalidStream')} /> : <>
      <dl className="posting-ops-browse-summary">
        {stream && <div><dt>{t('postingOps.metric.accountMaxSequence')}</dt><dd>{count(stream.accountMaxSequence)}</dd></div>}
        {stream && <div><dt>{t('postingOps.metric.ledgerConsumed')}</dt><dd>{count(stream.ledgerConsumedSequence)}</dd></div>}
        <div><dt>{t('postingOps.browse.from')}</dt><dd>{t('postingOps.browse.fromValue', { sequence: count(query.fromSequence) })}</dd></div>
        <div><dt>{t('postingOps.browse.pageSize')}</dt><dd>{t('postingOps.browse.pageSizeValue', { count: browsePageSize })}</dd></div>
        <div className="posting-ops-jump"><InputNumber<string> stringMode min="1" precision={0} controls={false} value={jump}
          placeholder={t('postingOps.browse.jumpPlaceholder')} onChange={value => setJump(value)} />
          <Button disabled={!jump || !/^[1-9][0-9]{0,18}$/.test(jump)} onClick={() => { if (jump) go(jump); }}>{t('postingOps.browse.jump')}</Button></div>
      </dl>
      {pages.error && !items.length ? <LoadFailure message={pages.error} onRetry={pages.retry} /> : <section className="table-panel">
        <Table<PostingSummary> rowKey="postingId" tableLayout="fixed" dataSource={items} loading={pages.loading && !items.length} pagination={false}
          scroll={{ x: 1180 }} rowClassName={row => row.postingSequence === stuckSequence ? 'posting-ops-row-behind' : ''}
          locale={{ emptyText: t('postingOps.browse.empty') }} columns={[
            { title: t('postingOps.column.sequence'), width: 120, render: (_, row) => <span className="posting-ops-sequence">{count(row.postingSequence)}
              {row.postingSequence === stuckSequence && <Tag className="finance-self-tag">{t('postingOps.browse.stuck')}</Tag>}</span> },
            { title: t('postingOps.field.source'), width: 120, render: (_, row) => enumLabel(t, 'enum.postingSource', row.sourceType) },
            { title: t('postingOps.field.factId'), width: 200, render: (_, row) => <span className="mono">{row.factId ?? '—'}</span> },
            { title: t('postingOps.field.factType'), width: 150, render: (_, row) => row.factType ?? '—' },
            { title: t('postingOps.column.applyStatus'), width: 100, render: (_, row) => row.factApplyStatus
              ? <Tag color={row.factApplyStatus === 'APPLIED' ? 'green' : 'default'}>{applyStatusLabel(t, row)}</Tag> : '—' },
            { title: t('postingOps.field.postedAt'), width: 170, render: (_, row) => opsTime(row.postedTime) },
            { title: t('postingOps.field.accountPosted'), width: 120, render: (_, row) => row.publishedTime
              ? <span title={opsTime(row.publishedTime)}>{t('postingOps.published.yes')}</span> : <span className="posting-ops-warn-text">{t('postingOps.published.no')}</span> },
            { title: t('postingOps.field.entries'), width: 80, render: (_, row) => count(row.entryCount) },
            { title: '', width: 100, render: (_, row) => <a onClick={() => go(query.fromSequence, row.postingId)}>{t('postingOps.stuck.viewEntries')}</a> },
          ]} />
      </section>}
      <div className="posting-ops-browse-footer"><span className="cell-meta">{range && t('postingOps.browse.footer', { range })}</span>
        {pages.next ? <Button loading={pages.loading && !!items.length} onClick={pages.loadMore}>{t('postingOps.browse.loadMore', { count: browsePageSize })}</Button>
          : items.length > 0 && <span className="cell-meta">{t('postingOps.browse.end')}</span>}</div>
      {pages.error && items.length > 0 && <LoadFailure message={pages.error} onRetry={pages.retry} />}
      <FinanceNote>{t('postingOps.browse.note')}</FinanceNote>
    </>}
    {query.posting && <SubledgerPostingDrawer postingId={query.posting} highlightEntryId={null} minorUnits={currencies.minorUnits}
      onOpenAccount={(posting, entry) => openSubledgerAccount(navigate, posting, entry)} onClose={() => go(query.fromSequence, null, true)}
      onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

/** 批次页加载：起点变化时重置；「加载后 N 批」从 nextFromSequence 追加，同一时刻只有一个请求。 */
function useStreamPages(streamId: string | null, fromSequence: string, onAuthorizationChanged: () => void) {
  const t = useT('finance');
  const notify = useErrorNotification();
  const [state, setState] = useState<{ key: string; items: PostingSummary[]; next: string | null; loading: boolean; error: string }>(
    { key: '', items: [], next: null, loading: false, error: '' });
  const [request, setRequest] = useState<{ key: string; from: string; token: number } | null>(null);
  const key = streamId ? `${streamId}@${fromSequence}` : '';
  const authorizationChanged = useRef(onAuthorizationChanged); authorizationChanged.current = onAuthorizationChanged;
  useEffect(() => {
    setState({ key, items: [], next: null, loading: false, error: '' });
    setRequest(key ? { key, from: fromSequence, token: 0 } : null);
  }, [key, fromSequence]);
  useEffect(() => {
    if (!request || !streamId || request.key !== key) return;
    const abort = new AbortController();
    setState(current => ({ ...current, loading: true, error: '' }));
    session.request(postingOpsPaths.streamPostings(streamId, request.from, browsePageSize), { signal: abort.signal }, value => decodePostingPage(value, request.from))
      .then(page => { if (!abort.signal.aborted) setState(current => current.key !== request.key ? current
        : { key: request.key, items: [...current.items, ...page.items], next: page.nextFromSequence, loading: false, error: '' }); })
      .catch(error => {
        if (abort.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) authorizationChanged.current();
        setState(current => ({ ...current, loading: false, error: error instanceof Error ? error.message : t('common.loadFailed') }));
        notify(error, t('postingOps.browse.loadFailed'));
      });
    return () => abort.abort();
    // 只在请求变化时加载；t 与 notify 只用于失败提示。
  }, [request]);
  const current = state.key === key ? state : { items: [], next: null, loading: true, error: '' };
  return {
    items: current.items, next: current.next, loading: current.loading, error: current.error,
    loadMore: () => { if (!current.loading && current.next) setRequest({ key, from: current.next, token: Date.now() }); },
    retry: () => setRequest(previous => previous ? { ...previous, token: previous.token + 1 } : previous),
  };
}
