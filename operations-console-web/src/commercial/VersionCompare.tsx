import { useMemo, useState } from 'react';
import { Button, Segmented, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { commercialRoutes, localTime, termTypeLabels, versionStatusLabels } from './commercial';
import type { TermVersion } from './commercial';
import { consolePaths, consoleRoutes, decodeVersionDiff, diffChangeColors, diffChangeLabels, diffCounts, diffRows } from './commercialConsole';
import type { DiffChange, DiffRow } from './commercialConsole';
import { CommercialNote, StatusTag } from './CommercialParts';

interface Props { baseVersionId: string; targetVersionId: string }
type Filter = 'ALL' | DiffChange;

const versionWindow = (version: TermVersion) => ['DRAFT', 'PENDING_APPROVAL', 'APPROVED'].includes(version.status) ? '排期时确定'
  : `${localTime(version.effectiveFrom)} ～ ${version.effectiveTo ? localTime(version.effectiveTo) : '未定'}`;

/**
 * 同一方案两个版本的价格对比：按 OE × 币种 × 画像配对，展开到组件；左右两列为基准与目标的组件摘要，差异行高亮
 * <li>修改行下方列出字段级差异（含价格级的优先级、结算参数与条件）；版本口径差异显示在顶部。</li>
 */
export function VersionCompare({ baseVersionId, targetVersionId }: Props) {
  const navigate = useNavigate();
  const decode = useMemo(() => decodeVersionDiff(baseVersionId, targetVersionId), [baseVersionId, targetVersionId]);
  const detail = useDetail(consolePaths.versionDiff(baseVersionId, targetVersionId), decode);
  const rows = useMemo(() => detail.data ? diffRows(detail.data) : [], [detail.data]);
  const [filter, setFilter] = useState<Filter>('ALL');
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const { base, target, versionDifferences } = detail.data;
  const counts = diffCounts(detail.data);
  const shown = filter === 'ALL' ? rows : rows.filter(row => row.change === filter);
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{termTypeLabels[target.termType] ?? target.termType} · v{base.versionNo} → v{target.versionNo} 对比</h1>
      <div className="commercial-actions">
        <Button onClick={() => void navigate(commercialRoutes.terms)}>返回价目</Button>
        <Button onClick={() => void navigate(consoleRoutes.compare(targetVersionId, baseVersionId))}>交换基准</Button>
        <Button onClick={() => void navigate(consoleRoutes.history('COMMERCIAL_TERM_PLAN', target.planId))}>方案变更历史</Button>
      </div>
    </div>
    <dl className="commercial-summary">
      <div><dt>基准 v{base.versionNo}</dt><dd><StatusTag status={base.status} labels={versionStatusLabels} /> {versionWindow(base)}</dd></div>
      <div><dt>目标 v{target.versionNo}</dt><dd><StatusTag status={target.status} labels={versionStatusLabels} /> {versionWindow(target)}</dd></div>
      <div><dt>价格差异</dt><dd>修改 {counts.CHANGED} · 新增 {counts.ADDED} · 删除 {counts.REMOVED} · 未变 {counts.UNCHANGED}</dd></div>
      <div><dt>口径</dt><dd>{versionDifferences.length ? versionDifferences.map(d => `${d.field} ${d.baseValue ?? '—'} → ${d.targetValue ?? '—'}`).join('；')
        : `${target.pricingModel} · ${target.feeTaxTreatment} · ${target.roundingMode}（无变化）`}</dd></div>
    </dl>
    <div className="commercial-section-head">
      <Segmented<Filter> value={filter} onChange={setFilter} options={[{ value: 'ALL', label: `全部 ${rows.length}` },
        ...(['CHANGED', 'ADDED', 'REMOVED', 'UNCHANGED'] as const).map(value => ({ value, label: `${diffChangeLabels[value]} ${rows.filter(r => r.change === value).length}` }))]} />
      <span className="commercial-spacer" />
      <Button size="small" onClick={() => void navigate(commercialRoutes.version(base.versionId))}>打开 v{base.versionNo}</Button>
      <Button size="small" onClick={() => void navigate(commercialRoutes.version(target.versionId))}>打开 v{target.versionNo}</Button>
    </div>
    <Table<DiffRow> rowKey="key" size="middle" pagination={false} dataSource={shown} scroll={{ x: 1180 }}
      rowClassName={row => `commercial-diff-${row.change.toLowerCase()}`} locale={{ emptyText: '没有该类价格' }}
      expandable={{ rowExpandable: row => row.fields.length > 0, expandedRowRender: row => <ul className="commercial-diff-fields">
        {row.fields.map(field => <li key={field.field}><span className="mono">{field.field}</span>：{field.baseValue ?? '—'} → <strong>{field.targetValue ?? '—'}</strong></li>)}
      </ul> }} columns={[
        { title: 'OE', dataIndex: 'entityNo', width: 70 },
        { title: '币种', dataIndex: 'currency', width: 70 },
        { title: '画像', width: 200, render: (_, row) => <span className="mono">{row.pricingProfileCode}</span> },
        { title: '事件', dataIndex: 'triggerCode', width: 150 },
        { title: '组件', width: 170, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.componentCode}</span>
          <span className="commercial-cell-meta">{row.componentType}</span></div> },
        { title: `v${base.versionNo}`, width: 200, render: (_, row) => row.baseSummary ?? '—' },
        { title: `v${target.versionNo}`, width: 200, render: (_, row) => row.change === 'CHANGED' ? <strong>{row.targetSummary ?? '—'}</strong> : row.targetSummary ?? '—' },
        { title: '变化', width: 90, render: (_, row) => <Tag color={diffChangeColors[row.change]}>{diffChangeLabels[row.change]}</Tag> },
      ]} />
    <CommercialNote>对比键为 OE × 币种 × 定价画像（与价格唯一键一致），组件按组件代码配对；金额与费率按数值比较。展开修改行可查看字段级差异（含优先级、结算参数）。</CommercialNote>
  </div>;
}
