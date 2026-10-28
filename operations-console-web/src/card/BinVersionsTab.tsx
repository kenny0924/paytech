import { useEffect, useState } from 'react';
import { Button, Progress, Segmented, Spin, Table } from 'antd';
import { ArrowRightOutlined, CloseCircleOutlined, InfoCircleOutlined, ThunderboltOutlined, UploadOutlined } from '@ant-design/icons';
import { session } from '../api/session';
import { requestPage } from '../api/paging';
import type { PagingResponseBody } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { useResource } from '../dataQuery/DataQueryParts';
import { enumLabel, formatShortDateTime } from '../i18n/index';
import { useT } from '../i18n/react';
import { countryName } from '../referenceData/referenceData';
import {
  binLibraryPaths, decodeImportErrorPage, decodeRangePage, decodeVersion, formatCount, isBrowsable, versionAction, versionProgress,
} from './binLibrary';
import type { BinImportError, BinLibraryActivation, BinLibraryVersion, BinRange, VersionStatus } from './binLibrary';
import { BinPrefix, BrandBadge, dash, MetaLine, OperatorName, StatusTag } from './BinLibraryParts';
import type { VersionCatalog } from './BinLibraryParts';

export type StatusFilter = VersionStatus | 'ALL';
const statusOrder: readonly StatusFilter[] = ['ALL', 'VALID', 'ACTIVE', 'VALIDATING', 'INVALID', 'RETIRED'];
const pollMillis = 3000;
const previewRows = 5;

interface Props {
  catalog: VersionCatalog; activations: BinLibraryActivation[]; canManage: boolean;
  status: StatusFilter; onStatus: (status: StatusFilter) => void; selectedNo: number | null; onSelect: (versionNo: number) => void;
  onActivate: (version: BinLibraryVersion) => void; onImport: () => void; onViewRanges: (versionNo: number) => void; onSettled: () => void;
}

/**
 * 版本页签（画板 04 / 05）：左侧状态分段筛选 + 版本列表，右侧选中版本详情与底部操作栏
 * <li>列表直接用页面的版本目录（最近 100 个）按状态过滤，不另发列表请求；计数在目录完整时显示。</li>
 */
export function BinVersionsTab({ catalog, activations, canManage, status, onStatus, selectedNo, onSelect, onActivate, onImport, onViewRanges, onSettled }: Props) {
  const t = useT('card');
  const count = (value: StatusFilter) => value === 'ALL' ? catalog.items.length : catalog.items.filter(item => item.status === value).length;
  const items = status === 'ALL' ? catalog.items : catalog.items.filter(item => item.status === status);
  const options = statusOrder.filter(value => value === 'ALL' || value === status || !catalog.complete || count(value) > 0).map(value => ({
    value, label: <span className="bin-segment">{value === 'ALL' ? t('versions.all') : enumLabel(t, 'enum.versionStatus', value)}
      {catalog.complete && <span className="bin-segment-count">{count(value)}</span>}</span>,
  }));
  // 选中项不在当前筛选里时改选第一项
  useEffect(() => {
    if (items.length && !items.some(item => item.versionNo === selectedNo)) onSelect(items[0].versionNo);
  }, [items, selectedNo, onSelect]);
  const selected = items.some(item => item.versionNo === selectedNo) ? selectedNo : null;
  return <section className="bin-panel bin-versions">
    <div className="bin-version-list">
      <div className="bin-version-filter"><Segmented<StatusFilter> size="small" value={status} onChange={onStatus} options={options} /></div>
      {items.length ? <div className="bin-version-items" role="listbox" aria-label={t('versions.title')}>
        {items.map(item => <VersionItem key={item.versionNo} version={item} selected={item.versionNo === selected} onSelect={() => onSelect(item.versionNo)} />)}
      </div> : <p className="bin-version-empty">{status === 'ALL' ? t('versions.empty') : t('versions.emptyStatus')}</p>}
      {!catalog.complete && <p className="bin-version-foot">{t('versions.recentOnly', { count: catalog.items.length, total: catalog.total })}</p>}
    </div>
    <div className="bin-version-detail">
      {selected !== null ? <VersionDetail key={selected} versionNo={selected} active={catalog.active} activations={activations} canManage={canManage}
        onActivate={onActivate} onImport={onImport} onViewRanges={onViewRanges} onSettled={onSettled} />
        : <p className="bin-version-empty">{t('versions.selectHint')}</p>}
    </div>
  </section>;
}

function VersionItem({ version, selected, onSelect }: { version: BinLibraryVersion; selected: boolean; onSelect: () => void }) {
  const t = useT('card');
  const progress = version.status === 'VALIDATING' ? versionProgress(version) : null;
  const facts = version.status === 'INVALID'
    ? <span className="bin-text-error bin-ellipsis" title={version.failureReason ?? undefined}>
      {version.failureReason ?? t('versions.errorRows', { count: version.errorRows })}</span>
    : progress ? <span>{progress.percent === null ? progress.label : `${progress.percent}%`}</span>
      : t('versions.rangeCount', { count: version.totalRows ?? 0 });
  return <button type="button" role="option" aria-selected={selected} className={`bin-version-item${selected ? ' is-selected' : ''}`} onClick={onSelect}>
    <span className="bin-version-item-head"><strong>v{version.versionNo}</strong><StatusTag status={version.status} />
      <span className="bin-version-time">{formatShortDateTime(version.createTime)}</span></span>
    <MetaLine className="bin-version-item-meta" parts={[<span className="bin-ellipsis" title={version.fileName ?? undefined}>{version.fileName ?? t('versions.seedData')}</span>, facts,
      <OperatorName label={version.createdByLabel} raw={version.createdBy} />]} />
  </button>;
}

/**
 * 选中版本详情：标题、统计条、号段预览（可浏览版本）或校验错误（校验失败）、激活记录与底部操作栏
 * <li>校验中每 3 秒轮询（离开或切换版本即停止），结束后通知页面刷新目录。</li>
 */
function VersionDetail({ versionNo, active, activations, canManage, onActivate, onImport, onViewRanges, onSettled }: {
  versionNo: number; active: BinLibraryVersion | null; activations: BinLibraryActivation[]; canManage: boolean;
  onActivate: (version: BinLibraryVersion) => void; onImport: () => void; onViewRanges: (versionNo: number) => void; onSettled: () => void;
}) {
  const t = useT('card');
  const detail = useResource(binLibraryPaths.version(versionNo), decodeVersion, t('detail.loadFailed'));
  const [wasValidating, setWasValidating] = useState(false);
  const version = detail.data;
  useEffect(() => {
    if (version?.status !== 'VALIDATING') {
      if (wasValidating && version) { setWasValidating(false); onSettled(); }
      return;
    }
    setWasValidating(true);
    const timer = setTimeout(detail.refresh, pollMillis);
    return () => clearTimeout(timer);
    // detail.refresh 每次渲染新建，按数据变化重新计时即可。
  }, [version]);
  if (detail.loading && !version) return <div className="bin-loading"><Spin /></div>;
  if (detail.failure || !version) return <div className="bin-detail-body"><QueryFailure failure={detail.failure} retry={detail.refresh} /></div>;
  const action = canManage ? versionAction(version) : null;
  return <>
    <div className="bin-detail-body">
      <div className="bin-detail-title"><h2>v{version.versionNo}</h2><StatusTag status={version.status} /></div>
      <MetaLine parts={[version.fileName ?? t('versions.seedData'), t('detail.updated', { time: formatShortDateTime(version.updateTime) })]} />
      <StatStrip version={version} active={active} />
      {version.status === 'VALIDATING' && <ValidatingState version={version} />}
      {version.status === 'INVALID' && <InvalidState version={version} />}
      {isBrowsable(version) && <RangePreview versionNo={version.versionNo} onViewAll={() => onViewRanges(version.versionNo)} />}
      <ActivationTimeline versionNo={version.versionNo} activations={activations} />
    </div>
    <div className="bin-detail-footer">
      <span className="bin-footer-note"><InfoCircleOutlined />{footerNote(t, version)}</span>
      <span className="bin-footer-actions">
        {isBrowsable(version) && <Button onClick={() => onViewRanges(version.versionNo)}>{t('detail.viewRanges')}</Button>}
        {action && <Button type="primary" icon={action.kind === 'ACTIVATE' ? <ThunderboltOutlined /> : undefined} onClick={() => onActivate(version)}>
          {action.kind === 'ACTIVATE' ? t('detail.activate', { versionNo: version.versionNo }) : t('detail.rollback', { versionNo: version.versionNo })}</Button>}
        {canManage && version.status === 'INVALID' && <Button type="primary" icon={<UploadOutlined />} onClick={onImport}>{t('detail.reimport')}</Button>}
      </span>
    </div>
  </>;
}

function footerNote(t: ReturnType<typeof useT>, version: BinLibraryVersion): string {
  const versionNo = version.versionNo;
  switch (version.status) {
    case 'VALID': return t('detail.note.valid', { versionNo });
    case 'RETIRED': return t('detail.note.retired', { versionNo });
    case 'ACTIVE': return t('detail.activeNote');
    case 'INVALID': return t('detail.note.invalid');
    default: return t('detail.validatingNote');
  }
}

/** 统计条：号段（与生效版本的差）、校验错误行、导入人。 */
function StatStrip({ version, active }: { version: BinLibraryVersion; active: BinLibraryVersion | null }) {
  const t = useT('card');
  const rows = version.totalRows;
  let rowsNote: { text: string; tone?: 'warning' } | null = null;
  if (version.status === 'INVALID') rowsNote = { text: t('stat.invalidNoRanges') };
  else if (version.status === 'ACTIVE') rowsNote = { text: t('stat.isActive') };
  else if (version.status !== 'VALIDATING' && active && rows !== null && active.totalRows !== null) {
    const delta = rows - active.totalRows;
    rowsNote = delta === 0 ? { text: t('stat.sameAsActive', { versionNo: active.versionNo }) }
      : { text: delta < 0 ? t('stat.fewerThanActive', { versionNo: active.versionNo, count: -delta }) : t('stat.moreThanActive', { versionNo: active.versionNo, count: delta }),
        tone: delta < 0 ? 'warning' : undefined };
  }
  return <div className="bin-stats">
    <div className="bin-stat"><span className="bin-stat-label">{t('stat.ranges')}</span>
      <span className="bin-stat-value">{version.status === 'INVALID' || version.status === 'VALIDATING' ? '—' : formatCount(rows)}</span>
      {rowsNote && <span className={`bin-stat-note${rowsNote.tone ? ` is-${rowsNote.tone}` : ''}`}>{rowsNote.text}</span>}</div>
    <div className="bin-stat"><span className="bin-stat-label">{t('stat.errorRows')}</span>
      <span className={`bin-stat-value${version.errorRows ? ' is-error' : ''}`}>{formatCount(version.errorRows)}</span>
      <span className="bin-stat-note">{version.status === 'VALIDATING' ? t('enum.versionStatus.VALIDATING')
        : version.errorRows === 0 ? t('stat.allPassed') : version.processedRows > 0 ? t('stat.ofRows', { count: version.processedRows }) : ''}</span></div>
    <div className="bin-stat"><span className="bin-stat-label">{t('stat.importedBy')}</span>
      <span className="bin-stat-value is-text"><OperatorName label={version.createdByLabel} raw={version.createdBy} /></span>
      <span className="bin-stat-note bin-ellipsis">{version.fileName ? t('stat.uploaded', { time: formatShortDateTime(version.createTime), fileName: version.fileName })
        : t('stat.seeded', { time: formatShortDateTime(version.createTime) })}</span></div>
  </div>;
}

function ValidatingState({ version }: { version: BinLibraryVersion }) {
  const progress = versionProgress(version);
  return <div className="bin-section">
    {progress.percent === null ? <div className="bin-progress-pending"><Spin size="small" /> <span>{progress.label}</span></div>
      : <><span>{progress.label}</span><Progress percent={progress.percent} status="active" /></>}
  </div>;
}

/** 校验失败（画板 05）：文件级原因或错误行横幅 + 错误行表（Card 只记录前 200 条）。 */
function InvalidState({ version }: { version: BinLibraryVersion }) {
  const t = useT('card');
  const errors = usePaging<BinImportError>(binLibraryPaths.errors(version.versionNo), decodeImportErrorPage, {}, { enabled: version.errorRows > 0 });
  return <div className="bin-section">
    <h3 className="bin-section-title">{version.errorRows > 0 ? t('invalid.sectionWithCount', { count: version.errorRows }) : t('invalid.section')}</h3>
    <div className="bin-error-banner"><CloseCircleOutlined />
      <span>{version.failureReason ?? t('invalid.banner', { errors: version.errorRows, total: version.processedRows })}</span></div>
    {version.errorRows > 0 && (errors.failure ? <QueryFailure failure={errors.failure} retry={errors.refresh} />
      : <Table<BinImportError> className="bin-table is-compact" rowKey={row => `${row.lineNo}:${row.columnName ?? ''}:${row.errorCode}`} size="small"
        loading={errors.loading} dataSource={errors.data.items} tableLayout="fixed" scroll={{ x: 640 }}
        pagination={errors.data.total > errors.pagination.pageSize ? { ...errors.pagination, size: 'small' } : false} columns={[
          { title: t('invalid.column.line'), width: 70, render: (_, row) => formatCount(row.lineNo) },
          { title: t('invalid.column.column'), width: 150, render: (_, row) => <span className="mono">{row.columnName ?? t('invalid.wholeRow')}</span> },
          { title: t('invalid.column.error'), render: (_, row) => <span className="bin-text-error" title={row.errorCode}>{row.errorMessage}</span> },
          { title: t('invalid.column.excerpt'), width: 220, render: (_, row) => row.valueExcerpt
            ? <span className="bin-excerpt mono">{row.valueExcerpt}</span> : <span className="bin-dash">{t('invalid.emptyValue')}</span> },
        ]} />)}
    {version.errorRows > 0 && <p className="bin-section-foot">{t('invalid.note', { errors: formatCount(version.errorRows) })}</p>}
  </div>;
}

/** 号段预览：号段列表接口第 1 页（10 条）显示前 5 条。 */
function RangePreview({ versionNo, onViewAll }: { versionNo: number; onViewAll: () => void }) {
  const t = useT('card');
  const [page, setPage] = useState<PagingResponseBody<BinRange> | null>(null); const [failure, setFailure] = useState<unknown>();
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); setFailure(undefined);
    requestPage(binLibraryPaths.ranges(versionNo), { page: 1, size: 10 }, controller.signal, session, {}, decodeRangePage)
      .then(result => { if (!controller.signal.aborted) setPage(result); })
      .catch(error => { if (!controller.signal.aborted) setFailure(error); });
    return () => controller.abort();
  }, [versionNo, retry]);
  const rest = page ? page.total - Math.min(previewRows, page.items.length) : 0;
  return <div className="bin-section">
    <div className="bin-section-head"><h3 className="bin-section-title">{t('preview.title')}</h3>
      {page && page.total > 0 && <button type="button" className="bin-link" onClick={onViewAll}>{t('preview.viewAll', { count: page.total })} <ArrowRightOutlined /></button>}</div>
    {failure ? <QueryFailure failure={failure} retry={() => setRetry(value => value + 1)} />
      : !page ? <div className="bin-loading is-small"><Spin size="small" /></div>
        : !page.items.length ? <p className="bin-section-foot">{t('ranges.empty.noRanges')}</p>
          : <div className="bin-preview">
            <Table<BinRange> className="bin-table is-compact" rowKey="binPrefix" size="small" pagination={false} tableLayout="fixed" scroll={{ x: 640 }}
              dataSource={page.items.slice(0, previewRows)} columns={[
                { title: t('ranges.column.binPrefix'), width: 130, render: (_, row) => <BinPrefix range={row} /> },
                { title: t('ranges.column.cardBrand'), width: 170, render: (_, row) => <BrandBadge brand={row.cardBrand} /> },
                { title: t('ranges.column.fundingType'), width: 120, render: (_, row) => dash(row.fundingType && enumLabel(t, 'enum.fundingType', row.fundingType)) },
                { title: t('ranges.column.issuerCountry'), width: 150, render: (_, row) => <span className="bin-country"><strong>{row.issuerCountry}</strong>
                  <span className="bin-country-name">{countryName(row.issuerCountry, null)}</span></span> },
                { title: t('ranges.column.issuerName'), ellipsis: true, render: (_, row) => dash(row.issuerName) },
              ]} />
            {rest > 0 && <div className="bin-preview-more">{t('preview.more', { count: rest })}</div>}
          </div>}
  </div>;
}

/** 激活记录：与本版本有关的记录（激活它或从它切走）；没有时显示全部版本的最近 3 条并说明。 */
function ActivationTimeline({ versionNo, activations }: { versionNo: number; activations: BinLibraryActivation[] }) {
  const t = useT('card');
  const related = activations.filter(item => item.versionNo === versionNo || item.previousVersionNo === versionNo);
  const shown = related.length ? related : activations.slice(0, 3);
  return <div className="bin-section">
    <h3 className="bin-section-title">{t('history.title')}</h3>
    {shown.length ? <ol className="bin-timeline">{shown.map(item => <li key={`${item.activatedAt}:${item.versionNo}`}>
      <div className="bin-timeline-head"><strong>{item.activationType === 'ROLLBACK' ? t('history.rollbackTo', { versionNo: item.versionNo })
        : t('history.activate', { versionNo: item.versionNo })}</strong>
        <MetaLine parts={[<OperatorName label={item.operatorLabel} raw={item.operatorAccountId} />, formatShortDateTime(item.activatedAt)]} /></div>
      <div className="bin-timeline-sub">{item.previousVersionNo === null ? t('history.first')
        : t('history.switch', { from: item.previousVersionNo, to: item.versionNo })}</div>
    </li>)}</ol> : <p className="bin-section-foot">{t('history.empty')}</p>}
    {!related.length && shown.length > 0 && <p className="bin-section-foot">{t('history.notActivated', { versionNo, count: shown.length })}</p>}
  </div>;
}
