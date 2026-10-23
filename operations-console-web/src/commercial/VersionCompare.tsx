import { useMemo, useState } from 'react';
import { Button, Segmented, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { commercialRoutes, joinList, localTime, versionBasisLabel } from './commercial';
import type { TermVersion } from './commercial';
import { consolePaths, consoleRoutes, decodeVersionDiff, diffChangeColors, diffCounts, diffRows } from './commercialConsole';
import type { DiffChange, DiffRow } from './commercialConsole';
import { CommercialNote, StatusTag } from './CommercialParts';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';

interface Props { baseVersionId: string; targetVersionId: string }
type Filter = 'ALL' | DiffChange;

const versionWindow = (t: Translate, version: TermVersion) => ['DRAFT', 'PENDING_APPROVAL', 'APPROVED'].includes(version.status) ? t('version.field.setOnSchedule')
  : t('shared.period', { from: localTime(version.effectiveFrom), to: version.effectiveTo ? localTime(version.effectiveTo) : t('shared.openEnded') });

/**
 * 同一方案两个版本的价格对比：按 OE × 币种 × 画像配对，展开到组件；左右两列为基准与目标的组件摘要，差异行高亮
 * <li>修改行下方列出字段级差异（含价格级的优先级、结算参数与条件）；版本口径差异显示在顶部。</li>
 */
export function VersionCompare({ baseVersionId, targetVersionId }: Props) {
  const t = useT('commercial');
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
    <div className="page-heading"><h1>{t('compare.title', { termType: enumLabel(t, 'enum.termType', target.termType), base: base.versionNo, target: target.versionNo })}</h1>
      <div className="commercial-actions">
        <Button onClick={() => void navigate(commercialRoutes.terms)}>{t('compare.backToTerms')}</Button>
        <Button onClick={() => void navigate(consoleRoutes.compare(targetVersionId, baseVersionId))}>{t('compare.swap')}</Button>
        <Button onClick={() => void navigate(consoleRoutes.history('COMMERCIAL_TERM_PLAN', target.planId))}>{t('compare.planHistory')}</Button>
      </div>
    </div>
    <dl className="commercial-summary">
      <div><dt>{t('compare.base', { versionNo: base.versionNo })}</dt><dd><StatusTag status={base.status} kind="versionStatus" /> {versionWindow(t, base)}</dd></div>
      <div><dt>{t('compare.target', { versionNo: target.versionNo })}</dt><dd><StatusTag status={target.status} kind="versionStatus" /> {versionWindow(t, target)}</dd></div>
      <div><dt>{t('compare.priceDiff')}</dt><dd>{t('compare.counts', { changed: counts.CHANGED, added: counts.ADDED, removed: counts.REMOVED, unchanged: counts.UNCHANGED })}</dd></div>
      <div><dt>{t('shared.column.basis')}</dt><dd>{versionDifferences.length ? joinList(versionDifferences.map(d => `${d.field} ${d.baseValue ?? '—'} → ${d.targetValue ?? '—'}`), 'clauses')
        : t('compare.basisUnchanged', { basis: `${versionBasisLabel(target.pricingModel, target.feeTaxTreatment)} · ${target.roundingMode}` })}</dd></div>
    </dl>
    <div className="commercial-section-head">
      <Segmented<Filter> value={filter} onChange={setFilter} options={[{ value: 'ALL', label: t('compare.filterAll', { count: rows.length }) },
        ...(['CHANGED', 'ADDED', 'REMOVED', 'UNCHANGED'] as const).map(value => ({ value,
          label: `${enumLabel(t, 'enum.diffChange', value)} ${rows.filter(r => r.change === value).length}` }))]} />
      <span className="commercial-spacer" />
      <Button size="small" onClick={() => void navigate(commercialRoutes.version(base.versionId))}>{t('compare.open', { versionNo: base.versionNo })}</Button>
      <Button size="small" onClick={() => void navigate(commercialRoutes.version(target.versionId))}>{t('compare.open', { versionNo: target.versionNo })}</Button>
    </div>
    <Table<DiffRow> rowKey="key" size="middle" pagination={false} dataSource={shown} scroll={{ x: 1180 }}
      rowClassName={row => `commercial-diff-${row.change.toLowerCase()}`} locale={{ emptyText: t('compare.empty') }}
      expandable={{ rowExpandable: row => row.fields.length > 0, expandedRowRender: row => <ul className="commercial-diff-fields">
        {row.fields.map(field => <li key={field.field}><span className="mono">{field.field}</span>{t('compare.fieldSeparator')}{field.baseValue ?? '—'} → <strong>{field.targetValue ?? '—'}</strong></li>)}
      </ul> }} columns={[
        { title: 'OE', dataIndex: 'entityNo', width: 70 },
        { title: t('matrix.column.currency'), dataIndex: 'currency', width: 70 },
        { title: t('matrix.column.profile'), width: 200, render: (_, row) => <span className="mono">{row.pricingProfileCode}</span> },
        { title: t('compare.column.event'), dataIndex: 'triggerCode', width: 150 },
        { title: t('compare.column.component'), width: 170, render: (_, row) => <div className="commercial-cell"><span className="mono">{row.componentCode}</span>
          <span className="commercial-cell-meta">{row.componentType}</span></div> },
        { title: `v${base.versionNo}`, width: 200, render: (_, row) => row.baseSummary ?? '—' },
        { title: `v${target.versionNo}`, width: 200, render: (_, row) => row.change === 'CHANGED' ? <strong>{row.targetSummary ?? '—'}</strong> : row.targetSummary ?? '—' },
        { title: t('compare.column.change'), width: 90, render: (_, row) => <Tag color={diffChangeColors[row.change]}>{enumLabel(t, 'enum.diffChange', row.change)}</Tag> },
      ]} />
    <CommercialNote>{t('compare.note')}</CommercialNote>
  </div>;
}
