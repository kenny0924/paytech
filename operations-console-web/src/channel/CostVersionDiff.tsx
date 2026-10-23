import { useMemo, useState } from 'react';
import { Button, Segmented, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { useT } from '../i18n/react';
import { channelPaths, channelRoutes, decodeVersionDiff, diffChangeColors, diffChangeLabel, diffRows, diffSummary, localTime, scopeLabel } from './channel';
import type { DiffChange, DiffRow } from './channel';
import { ChannelNote, Summary } from './ChannelParts';

interface Props { planCode: string; baseCostVer: string; targetCostVer: string }
type Filter = 'ALL' | DiffChange;

/**
 * 版本对比（画板 12）：BFF 按六维范围对齐、再按组成代码对齐；这里展开为 范围 × 组成 行，差异行底色区分
 * <li>修改行展开显示字段级差异（含范围的渠道合同引用）。</li>
 */
export function CostVersionDiff({ planCode, baseCostVer, targetCostVer }: Props) {
  const t = useT('channel');
  const navigate = useNavigate();
  const decode = useMemo(() => decodeVersionDiff(baseCostVer, targetCostVer), [baseCostVer, targetCostVer]);
  const detail = useDetail(channelPaths.costVersionDiff(planCode, baseCostVer, targetCostVer), decode);
  const rows = useMemo(() => detail.data ? diffRows(detail.data) : [], [detail.data]);
  const [filter, setFilter] = useState<Filter>('ALL');
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const { base, target } = detail.data;
  const summary = diffSummary(detail.data);
  const shown = filter === 'ALL' ? rows : rows.filter(row => row.change === filter);
  return <>
    <div className="page-heading"><h1>{t('diff.title', { target: target.costVer, base: base.costVer })}</h1>
      <div className="channel-actions">
        <Button onClick={() => void navigate(channelRoutes.costCompare(planCode, targetCostVer, baseCostVer))}>{t('diff.swap')}</Button>
        <Button onClick={() => void navigate(channelRoutes.costPlan(planCode))}>{t('diff.backToPlan')}</Button></div>
    </div>
    <Summary items={[
      { label: t('diff.base'), value: t('diff.versionSummary', { version: base.costVer, status: base.status, from: localTime(base.effectiveFrom) }) },
      { label: t('diff.target'), value: t('diff.versionSummary', { version: target.costVer, status: target.status, from: localTime(target.effectiveFrom) }) },
      { label: t('diff.scopes'), value: t('diff.scopeSummary', { base: summary.baseScopes, target: summary.targetScopes, added: summary.addedScopes, removed: summary.removedScopes }) },
      { label: t('diff.components'), value: t('diff.componentSummary', { base: summary.baseComponents, target: summary.targetComponents, added: summary.addedComponents,
        changed: summary.changedComponents, removed: summary.removedComponents }) },
    ]} />
    <div className="channel-section-head">
      <Segmented<Filter> value={filter} onChange={setFilter} options={[{ value: 'ALL', label: t('diff.filterAll', { count: rows.length }) },
        ...(['ADDED', 'CHANGED', 'REMOVED', 'UNCHANGED'] as const).map(value => ({ value, label: t('diff.filterChange', { change: diffChangeLabel(value), count: rows.filter(r => r.change === value).length }) }))]} />
    </div>
    <Table<DiffRow> rowKey="key" size="middle" pagination={false} dataSource={shown} scroll={{ x: 1100 }}
      rowClassName={row => `channel-diff-${row.change.toLowerCase()}`} locale={{ emptyText: t('diff.empty') }}
      expandable={{ rowExpandable: row => row.differences.length > 0, expandedRowRender: row => <ul className="channel-diff-fields">
        {row.differences.map(field => <li key={field.field}><span className="mono">{field.field}</span>{t('diff.fieldColon')}{field.baseValue ?? '—'} → <strong>{field.targetValue ?? '—'}</strong></li>)}
      </ul> }} columns={[
        { title: t('diff.change'), width: 80, render: (_, row) => <Tag color={diffChangeColors[row.change]}>{diffChangeLabel(row.change)}</Tag> },
        { title: t('diff.scopeColumn'), width: 380, render: (_, row) => <span className="mono">{scopeLabel(row.scope)}</span> },
        { title: t('diff.component'), width: 140, render: (_, row) => <span className="mono">{row.componentCode}</span> },
        { title: `v${base.costVer}`, width: 240, render: (_, row) => row.baseSummary ?? '—' },
        { title: `v${target.costVer}`, width: 240, render: (_, row) => row.change === 'CHANGED' ? <strong>{row.targetSummary ?? '—'}</strong> : row.targetSummary ?? '—' },
      ]} />
    <ChannelNote>{t('diff.note')}</ChannelNote>
  </>;
}
