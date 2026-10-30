import { useEffect, useState } from 'react';
import { Button, Checkbox, Empty, Input, Segmented, Select, Table } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { enumLabel, formatShortDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { CrrBadge } from '../underwriting/UnderwritingParts';
import { LimitTierDetail } from '../limitTier/LimitTierDetail';
import { ObservationCell, TierBadge } from '../limitTier/LimitTierParts';
import {
  decodeStatistics, decodeSummaryPage, isClientId, limitTierPaths, limitTierRoutes, listFilters, listSearch, listTabs, parseListQuery, permissionsOf, rowAction,
} from '../limitTier/limitTier';
import type { ListQuery, ListTab, Statistics, TierSummary } from '../limitTier/limitTier';
import '../underwriting/underwriting.css';
import '../limitTier/limitTier.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/** 风控 · 限额档位（REQ-04）：/risk/limit-tiers 列表，/risk/limit-tiers/:clientId Client 档位页。 */
export function RiskLimitTiers(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && !isClientId(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return parts.length === 1 ? <LimitTierDetail key={parts[0]} clientId={parts[0]} {...props} /> : <TierList key={location.search} {...props} />;
}

/** 统计卡（原型 01 顺序）及其对应筛选。 */
const statCards: { key: keyof Statistics; tone: string; query: Partial<ListQuery> }[] = [
  { key: 'verification', tone: 'is-info', query: { tab: 'VERIFICATION' } },
  { key: 'observation', tone: 'is-purple', query: { tab: 'OBSERVATION' } },
  { key: 'observationDue', tone: 'is-warn', query: { tab: 'ALL', observationDue: true } },
  { key: 'standard', tone: 'is-ok', query: { tab: 'STANDARD' } },
  { key: 'restricted', tone: 'is-danger', query: { tab: 'RESTRICTED' } },
  { key: 'pendingApproval', tone: 'is-warn', query: { tab: 'ALL', pendingApproval: true } },
];

/** 顶部统计（L11）：读取失败时显示占位，不影响列表。 */
function useStatistics() {
  const [data, setData] = useState<Statistics | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    session.request(limitTierPaths.statistics, { signal: controller.signal }, decodeStatistics).then(setData).catch(() => undefined);
    return () => controller.abort();
  }, []);
  return data;
}

/** 列表（原型 01）：档位页签、搜索、"只看到期待确认"、待审批筛选与分页写入地址。 */
function TierList({ resources, actorId }: Props) {
  const t = useT('risk'), tNav = useT('nav'), tMaster = useT('master-data'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate();
  const query = parseListQuery(new URLSearchParams(location.search));
  const permissions = permissionsOf(resources);
  const [keyword, setKeyword] = useState(query.keyword);
  const statistics = useStatistics();
  const search = new URLSearchParams(location.search);
  const page = usePaging<TierSummary>(limitTierPaths.list, decodeSummaryPage, listFilters(query), {
    initialPaging: { page: Number(search.get('page')) || 1, size: Number(search.get('size')) || 20 }, notifyOnError: false,
    onQueryChange: next => go(query, next.paging) });
  function go(next: ListQuery, paging = { page: 1, size: page.pagination.pageSize }) { void navigate(`?${listSearch(next, paging)}`); }
  const filtered = Object.keys(listFilters(query)).length > 0;
  const activeStat = (card: typeof statCards[number]) => Object.entries({ observationDue: false, pendingApproval: false, ...card.query })
    .every(([key, value]) => query[key as keyof ListQuery] === value);
  return <div className="uw-page">
    <div className="uw-head"><div className="uw-grow"><h1>{tNav('resource.OPERATIONS_RISK_LIMIT_TIERS')}</h1><p className="uw-lead">{t('limitTier.lead')}</p></div></div>
    <div className="uw-stats lt-stats">{statCards.map(card => <button type="button" key={card.key} aria-pressed={activeStat(card)}
      className={`uw-stat is-link${activeStat(card) ? ' is-active' : ''}`} onClick={() => go({ ...query, observationDue: false, pendingApproval: false, ...card.query })}>
      <span className="uw-stat-label">{t('limitTier.stats.' + card.key)}</span>
      <span className={`uw-stat-value ${card.tone}`}>{statistics ? statistics[card.key] : '—'}</span></button>)}</div>
    <section className="uw-panel">
      <div className="uw-toolbar">
        <Segmented value={query.tab} onChange={value => go({ ...query, tab: value as ListTab })}
          options={listTabs.map(tab => ({ value: tab, label: tab === 'ALL' ? t('limitTier.filter.all') : enumLabel(t, 'limitTier.tier', tab) }))} />
        <Input.Search allowClear className="uw-search" placeholder={t('limitTier.filter.search')} value={keyword} maxLength={100}
          onChange={event => setKeyword(event.target.value)} onSearch={value => go({ ...query, keyword: value })} />
        <Checkbox checked={query.observationDue} onChange={event => go({ ...query, observationDue: event.target.checked })}>{t('limitTier.filter.observationDue')}</Checkbox>
        <Select className="uw-filter" value={query.pendingApproval ? 'PENDING' : 'ALL'} onChange={value => go({ ...query, pendingApproval: value === 'PENDING' })}
          aria-label={t('limitTier.filter.pendingApproval')} options={[{ value: 'ALL', label: t('limitTier.filter.pendingAll') }, { value: 'PENDING', label: t('limitTier.filter.pendingOnly') }]} />
        <Button onClick={page.refresh} loading={page.loading}>{tCommon('action.refresh')}</Button>
      </div>
      {page.error ? <div className="uw-panel-body"><QueryFailure failure={page.failure} retry={page.refresh} /></div>
        : <Table<TierSummary> rowKey="clientId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1280 }}
          onRow={row => ({ className: 'uw-clickable', onClick: () => void navigate(limitTierRoutes.detail(row.clientId)) })}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={filtered ? t('limitTier.list.noMatch') : t('limitTier.list.empty')} /> }}
          pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => t('limitTier.list.total', { total }) }} columns={[
            { title: t('limitTier.column.client'), width: 220, render: (_, row) => <div><div className="uw-ellipsis">{row.clientDisplayName}</div>
              <div className="uw-hint">{[row.clientNo, row.jurisdictionCode, enumLabel(tMaster, 'client.enum.clientType', row.clientType)].join(' · ')}</div></div> },
            { title: t('limitTier.column.crr'), width: 100, render: (_, row) => <CrrBadge crr={row.crr} /> },
            { title: t('limitTier.column.tier'), width: 100, render: (_, row) => <TierBadge tier={row.tier} /> },
            { title: t('limitTier.column.entered'), width: 150, render: (_, row) => <div><div>{formatShortDateTime(row.effectiveTime)}</div>
              {row.sourceReason && <div className="uw-hint uw-ellipsis" title={row.sourceReason}>{row.sourceReason}</div>}</div> },
            { title: t('limitTier.column.observation'), width: 240, render: (_, row) => row.tier === 'OBSERVATION'
              ? <ObservationCell daysElapsed={row.observationDaysElapsed} targetDays={row.observationTargetDays} allowed={row.observationAllowedTransactions}
                targetTransactions={row.observationTargetTransactions} due={row.observationDue} />
              : <span className="muted">{row.tier === 'VERIFICATION' ? t('limitTier.observation.notApplicable') : '—'}</span> },
            { title: t('limitTier.column.pending'), width: 170, render: (_, row) => row.pendingChange ? <div>
              <span className="uw-badge is-warn">{`${enumLabel(t, 'limitTier.tier', row.pendingChange.fromTier ?? row.tier)} → ${enumLabel(t, 'limitTier.tier', row.pendingChange.toTier)}`}</span>
              <div className="uw-hint">{enumLabel(t, 'limitTier.pendingBadge', row.pendingChange.approvalLevel)}</div></div> : <span className="muted">—</span> },
            { title: t('limitTier.column.source'), width: 110, render: (_, row) => enumLabel(t, 'limitTier.source', row.sourceType) },
            { title: t('limitTier.column.updated'), width: 120, render: (_, row) => formatShortDateTime(row.updatedAt) },
            { title: t('limitTier.column.actions'), width: 100, fixed: 'right', render: (_, row) => <Button type="link" className="uw-link-button"
              onClick={event => { event.stopPropagation(); void navigate(limitTierRoutes.detail(row.clientId)); }}>{t('limitTier.rowAction.' + rowAction(row, actorId, permissions))}</Button> },
          ]} />}
    </section>
    <ul className="uw-guide">
      <li>{t('limitTier.guide.lifecycle')}</li>
      <li>{t('limitTier.guide.observation')}</li>
      <li>{t('limitTier.guide.lists')}</li>
    </ul>
  </div>;
}
