import { useMemo, useState } from 'react';
import { Button, Segmented, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { channelPaths, channelRoutes, decodeVersionDiff, diffChangeColors, diffChangeLabels, diffRows, diffSummary, localTime, scopeLabel } from './channel';
import type { DiffChange, DiffRow } from './channel';
import { ChannelNote, Summary } from './ChannelParts';

interface Props { planCode: string; baseCostVer: string; targetCostVer: string }
type Filter = 'ALL' | DiffChange;

/**
 * 版本对比（画板 12）：BFF 按六维范围对齐、再按组成代码对齐；这里展开为 范围 × 组成 行，差异行底色区分
 * <li>修改行展开显示字段级差异（含范围的渠道合同引用）。</li>
 */
export function CostVersionDiff({ planCode, baseCostVer, targetCostVer }: Props) {
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
    <div className="page-heading"><h1>版本对比 · v{target.costVer} ↔ v{base.costVer}</h1>
      <div className="channel-actions">
        <Button onClick={() => void navigate(channelRoutes.costCompare(planCode, targetCostVer, baseCostVer))}>交换基准</Button>
        <Button onClick={() => void navigate(channelRoutes.costPlan(planCode))}>返回方案</Button></div>
    </div>
    <Summary items={[
      { label: '基准', value: `v${base.costVer} · ${base.status} · ${localTime(base.effectiveFrom)} 起` },
      { label: '对比', value: `v${target.costVer} · ${target.status} · ${localTime(target.effectiveFrom)} 起` },
      { label: '范围', value: `${summary.baseScopes} → ${summary.targetScopes}（新增 ${summary.addedScopes} · 删除 ${summary.removedScopes}）` },
      { label: '组成', value: `${summary.baseComponents} → ${summary.targetComponents}（新增 ${summary.addedComponents} · 修改 ${summary.changedComponents} · 删除 ${summary.removedComponents}）` },
    ]} />
    <div className="channel-section-head">
      <Segmented<Filter> value={filter} onChange={setFilter} options={[{ value: 'ALL', label: `全部 ${rows.length}` },
        ...(['ADDED', 'CHANGED', 'REMOVED', 'UNCHANGED'] as const).map(value => ({ value, label: `${diffChangeLabels[value]} ${rows.filter(r => r.change === value).length}` }))]} />
    </div>
    <Table<DiffRow> rowKey="key" size="middle" pagination={false} dataSource={shown} scroll={{ x: 1100 }}
      rowClassName={row => `channel-diff-${row.change.toLowerCase()}`} locale={{ emptyText: '没有该类差异' }}
      expandable={{ rowExpandable: row => row.differences.length > 0, expandedRowRender: row => <ul className="channel-diff-fields">
        {row.differences.map(field => <li key={field.field}><span className="mono">{field.field}</span>：{field.baseValue ?? '—'} → <strong>{field.targetValue ?? '—'}</strong></li>)}
      </ul> }} columns={[
        { title: '变化', width: 80, render: (_, row) => <Tag color={diffChangeColors[row.change]}>{diffChangeLabels[row.change]}</Tag> },
        { title: '范围（MID / OE / 国家 / 币种 / 支付方式 / 动作）', width: 380, render: (_, row) => <span className="mono">{scopeLabel(row.scope)}</span> },
        { title: '组成', width: 140, render: (_, row) => <span className="mono">{row.componentCode}</span> },
        { title: `v${base.costVer}`, width: 240, render: (_, row) => row.baseSummary ?? '—' },
        { title: `v${target.costVer}`, width: 240, render: (_, row) => row.change === 'CHANGED' ? <strong>{row.targetSummary ?? '—'}</strong> : row.targetSummary ?? '—' },
      ]} />
    <ChannelNote>对比在 BFF 聚合：按六维范围对齐，再按组成代码对齐；参数任一不同记为「修改」。金额与费率按数值比较。</ChannelNote>
  </>;
}
