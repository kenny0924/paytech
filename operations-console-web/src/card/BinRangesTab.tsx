import { useEffect, useMemo, useState } from 'react';
import { Button, Drawer, Input, Pagination, Popover, Select, Spin, Table } from 'antd';
import {
  ArrowRightOutlined, CloseOutlined, CreditCardOutlined, ExclamationCircleOutlined, FileSearchOutlined, FilterOutlined, PlusOutlined,
  ReloadOutlined, ScanOutlined, SearchOutlined, UploadOutlined,
} from '@ant-design/icons';
import { session } from '../api/session';
import { requestPage } from '../api/paging';
import type { PagingResponseBody } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { richText } from '../i18n/richText';
import { countryName } from '../referenceData/referenceData';
import {
  activeFilterCount, binLibraryPaths, binPrefixIssue, cardBrands, commonIssuerCountries, decodeLookup, decodeRangePage, emptyRangeFilters, fundingTypes, isBrowsable,
  isQueryableBin, rangeQueryFilters,
} from './binLibrary';
import type { BinLibraryVersion, BinLookup, BinRange, RangeFilters } from './binLibrary';
import { BinPrefix, BrandBadge, CountryCell, dash, EmptyState, MetaLine, StatusTag } from './BinLibraryParts';
import type { CountryFlags, VersionCatalog } from './BinLibraryParts';
import { useIsMobile } from './useIsMobile';

const pageSize = 50;
const debounceMillis = 300;
type FilterKey = 'cardBrand' | 'fundingType' | 'issuerCountry';

interface Props {
  catalog: VersionCatalog; versionNo: number | null; onVersionChange: (versionNo: number) => void; flags: CountryFlags;
  canManage: boolean; onImport: () => void; onShowPending: () => void;
}

/**
 * 号段数据页签（画板 01—03b、08）：BIN 前缀搜索 + 卡组织 / 借贷记 / 发卡国筛选 + 版本选择，号段表格（390 宽度为卡片列表）
 * <li>前缀输入防抖后查询；非数字或超过 8 位只提示、不发请求。6～8 位时另查单 BIN 最长前缀匹配，结果显示在表格上方。</li>
 * <li>未选版本（没有生效版本）时显示引导空状态，不发请求。</li>
 */
export function BinRangesTab({ catalog, versionNo, onVersionChange, flags, canManage, onImport, onShowPending }: Props) {
  const t = useT('card');
  const mobile = useIsMobile();
  const [binInput, setBinInput] = useState('');
  const [filters, setFilters] = useState<RangeFilters>(emptyRangeFilters);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const inputIssue = binPrefixIssue(binInput);
  // 输入合法时才防抖写入筛选；不合法时保留上一次条件，不发请求
  useEffect(() => {
    if (inputIssue || binInput.trim() === filters.binPrefix) return;
    const timer = setTimeout(() => setFilters(current => ({ ...current, binPrefix: binInput.trim() })), debounceMillis);
    return () => clearTimeout(timer);
  }, [binInput, inputIssue, filters.binPrefix]);
  const version = catalog.items.find(item => item.versionNo === versionNo) ?? (catalog.active?.versionNo === versionNo ? catalog.active : null);
  const ranges = useRangePage(versionNo, filters);
  const lookup = useBinMatch(isQueryableBin(filters.binPrefix) ? filters.binPrefix : null, versionNo);
  const setFilter = (key: FilterKey, value: string | null) => setFilters(current => ({ ...current, [key]: value }));
  const clearAll = () => { setBinInput(''); setFilters(emptyRangeFilters); };
  const filterCount = activeFilterCount(filters);
  const chipCount = [filters.cardBrand, filters.fundingType, filters.issuerCountry].filter(Boolean).length;

  if (versionNo === null) return <section className="bin-panel"><NoActiveState pendingCount={catalog.pendingCount} canManage={canManage}
    onImport={onImport} onShowPending={onShowPending} /></section>;

  const search = <Input allowClear value={binInput} inputMode="numeric" maxLength={24} prefix={<SearchOutlined className="bin-muted-icon" />}
    status={inputIssue ? 'error' : undefined} placeholder={t('ranges.searchPlaceholder')} className="bin-search mono-input"
    onChange={event => setBinInput(event.target.value)} aria-label={t('ranges.searchPlaceholder')} />;
  const versionSelect = <VersionSelect catalog={catalog} value={versionNo} onChange={onVersionChange} compact={mobile} />;
  const summary = filterSummary(t, filters);

  return <section className="bin-panel bin-ranges">
    {mobile ? <div className="bin-toolbar is-mobile">
      {search}
      {inputIssue && <div className="bin-input-hint">{inputIssue}</div>}
      <div className="bin-toolbar-row">
        <Button className={`bin-filter-button${chipCount ? ' is-active' : ''}`} icon={<FilterOutlined />} onClick={() => setDrawerOpen(true)}>
          {t('ranges.filterButton', { count: chipCount })}</Button>
        {versionSelect}
      </div>
    </div> : <div className="bin-toolbar">
      <div className="bin-search-wrap">{search}{inputIssue && <div className="bin-input-hint">{inputIssue}</div>}</div>
      <FilterChip label={t('ranges.filter.cardBrand')} value={filters.cardBrand} options={cardBrands.map(value => ({ value, label: enumLabel(t, 'enum.cardBrand', value) }))}
        onChange={value => setFilter('cardBrand', value)} />
      <FilterChip label={t('ranges.filter.fundingType')} value={filters.fundingType} options={fundingTypes.map(value => ({ value, label: enumLabel(t, 'enum.fundingType', value) }))}
        onChange={value => setFilter('fundingType', value)} />
      <FilterChip label={t('ranges.filter.issuerCountry')} value={filters.issuerCountry} searchable
        options={flags.countryCodes.map(value => ({ value, label: `${value} ${countryName(value, null)}` }))} onChange={value => setFilter('issuerCountry', value)} />
      <span className="bin-spacer" />
      {(filterCount > 0 || binInput) && <button type="button" className="bin-link" onClick={clearAll}>{t('ranges.clearFilters')}</button>}
      <span className="bin-toolbar-label">{t('ranges.version')}</span>{versionSelect}
    </div>}
    {lookup.result && <MatchBar lookup={lookup.result} bin={filters.binPrefix} />}
    {mobile && <MetaLine className="bin-mobile-summary" parts={[ranges.data
      && (filterCount ? t('ranges.matched', { count: ranges.data.total }) : t('ranges.total', { count: ranges.data.total })), summary]} />}
    <RangeBody ranges={ranges} mobile={mobile} flags={flags} filterSummary={summary} filtered={filterCount > 0} onClear={clearAll} />
    {ranges.data && ranges.data.total > 0 && <div className="bin-table-foot">
      <span>{filterCount ? t('ranges.matchedOfTotal', { count: ranges.data.total, total: version?.totalRows ?? ranges.data.total })
        : t('ranges.totalSorted', { count: ranges.data.total })}</span>
      <span className="bin-pager"><span>{t('ranges.pageSize', { size: pageSize })}</span>
        <Pagination simple size="small" current={ranges.page} pageSize={pageSize} total={ranges.data.total} onChange={ranges.setPage} /></span>
    </div>}
    {mobile && <Drawer placement="bottom" open={drawerOpen} onClose={() => setDrawerOpen(false)} size="auto" closable={false}
      rootClassName="bin-filter-drawer" title={<div className="bin-drawer-head"><span>{t('ranges.filterTitle')}</span>
        <button type="button" className="bin-link" onClick={() => setFilters(current => ({ ...current, cardBrand: null, fundingType: null, issuerCountry: null }))}>
          {t('ranges.clear')}</button></div>}>
      <ChipGroup title={t('ranges.filter.cardBrand')} value={filters.cardBrand} onChange={value => setFilter('cardBrand', value)}
        options={cardBrands.map(value => ({ value, label: enumLabel(t, 'enum.cardBrand', value) }))} />
      <ChipGroup title={t('ranges.filter.fundingType')} value={filters.fundingType} onChange={value => setFilter('fundingType', value)}
        options={fundingTypes.map(value => ({ value, label: enumLabel(t, 'enum.fundingType', value) }))} />
      <ChipGroup title={t('ranges.filter.issuerCountry')} value={filters.issuerCountry} onChange={value => setFilter('issuerCountry', value)}
        options={drawerCountries(filters.issuerCountry).map(value => ({ value, label: value }))} />
      <Button type="primary" block size="large" loading={ranges.loading} onClick={() => setDrawerOpen(false)}>
        {t('ranges.showResults', { count: ranges.data?.total ?? 0 })}</Button>
    </Drawer>}
  </section>;
}

/** 抽屉里只列常用发卡国（另加已选值），不铺开全部国家。 */
const drawerCountries = (selected: string | null) => selected && !commonIssuerCountries.includes(selected) ? [...commonIssuerCountries, selected] : commonIssuerCountries;

// ---------- 数据 ----------

interface RangePage { data: PagingResponseBody<BinRange> | null; loading: boolean; failure: unknown; page: number; setPage: (page: number) => void; retry: () => void }

/** 当前版本 + 条件的号段分页；版本或条件变化回到第 1 页；条件不合法（rangeQueryFilters 为 null）时不请求。 */
function useRangePage(versionNo: number | null, filters: RangeFilters): RangePage {
  const query = rangeQueryFilters(filters);
  const key = `${versionNo}|${JSON.stringify(query)}`;
  const [page, setPage] = useState(1); const [pageFor, setPageFor] = useState(key);
  const [data, setData] = useState<PagingResponseBody<BinRange> | null>(null);
  const [loading, setLoading] = useState(false); const [failure, setFailure] = useState<unknown>();
  const [retry, setRetry] = useState(0);
  if (pageFor !== key) { setPageFor(key); setPage(1); }
  const effectivePage = pageFor === key ? page : 1;
  useEffect(() => {
    if (versionNo === null || query === null) return;
    const controller = new AbortController();
    setLoading(true); setFailure(undefined);
    requestPage(binLibraryPaths.ranges(versionNo), { page: effectivePage, size: pageSize }, controller.signal, session, query, decodeRangePage)
      .then(result => { if (!controller.signal.aborted) setData(result); })
      .catch(error => { if (!controller.signal.aborted) { setFailure(error); setData(null); } })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // key 已覆盖 versionNo 与 query
  }, [key, effectivePage, retry]);
  return { data, loading, failure, page: effectivePage, setPage, retry: () => setRetry(value => value + 1) };
}

/** 6～8 位前缀的最长前缀匹配（单 BIN 查询接口）；失败只通知，不影响号段列表。 */
function useBinMatch(bin: string | null, versionNo: number | null) {
  const t = useT('card');
  const notify = useErrorNotification();
  const key = `${bin}|${versionNo}`;
  const [result, setResult] = useState<{ key: string; value: BinLookup } | null>(null);
  useEffect(() => {
    if (bin === null || versionNo === null) return;
    const controller = new AbortController();
    session.request(binLibraryPaths.lookup(bin, versionNo), { signal: controller.signal }, decodeLookup)
      .then(value => { if (!controller.signal.aborted) setResult({ key, value }); })
      .catch(error => { if (!controller.signal.aborted) notify(error, t('lookup.failed')); });
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [bin, versionNo, notify]);
  // 只显示与当前前缀、版本对应的结果
  return { result: bin !== null && result?.key === key ? result.value : null };
}

// ---------- 工具栏 ----------

function VersionSelect({ catalog, value, onChange, compact }: { catalog: VersionCatalog; value: number; onChange: (versionNo: number) => void; compact: boolean }) {
  const t = useT('card');
  const known = catalog.items.some(item => item.versionNo === value);
  const versions: BinLibraryVersion[] = known || !catalog.active ? catalog.items : [catalog.active, ...catalog.items];
  const options = versions.map(item => ({
    value: item.versionNo, disabled: !isBrowsable(item),
    label: <span className="bin-version-option"><strong>v{item.versionNo}</strong>
      {(!compact || item.status !== 'ACTIVE') && <StatusTag status={item.status} />}
      {!isBrowsable(item) && <span className="bin-option-reason">{item.status === 'VALIDATING' ? t('ranges.notBrowsable.validating') : t('ranges.notBrowsable.invalid')}</span>}</span>,
  }));
  if (!versions.some(item => item.versionNo === value)) options.unshift({ value, disabled: false, label: <span className="bin-version-option"><strong>v{value}</strong></span> });
  return <Select<number> value={value} onChange={onChange} options={options} className={`bin-version-select${compact ? ' is-compact' : ''}`}
    popupMatchSelectWidth={false} aria-label={t('ranges.version')}
    labelRender={({ value: selected }) => {
      const item = versions.find(version => version.versionNo === selected);
      return <span className="bin-version-option"><strong>v{String(selected)}</strong>{!compact && item?.status === 'ACTIVE' && <StatusTag status="ACTIVE" />}</span>;
    }} />;
}

interface Option { value: string; label: string }

/** Stripe 式筛选 chip：未选为"+ 名称"，选中为"名称：值 ×"；弹出单选清单，可搜索时带搜索框。 */
function FilterChip({ label, value, options, onChange, searchable = false }: {
  label: string; value: string | null; options: Option[]; onChange: (value: string | null) => void; searchable?: boolean;
}) {
  const t = useT('card');
  const [open, setOpen] = useState(false); const [keyword, setKeyword] = useState('');
  const needle = keyword.trim().toLowerCase();
  const shown = needle ? options.filter(option => option.label.toLowerCase().includes(needle) || option.value.toLowerCase().includes(needle)) : options;
  // 发卡国可直接输入清单外的两位代码
  const typed = searchable && /^[a-z]{2}$/i.test(keyword.trim()) && !options.some(option => option.value === keyword.trim().toUpperCase())
    ? keyword.trim().toUpperCase() : null;
  const choose = (next: string) => { onChange(next); setOpen(false); setKeyword(''); };
  const selected = options.find(option => option.value === value);
  const content = <div className="bin-chip-menu">
    {searchable && <Input size="small" allowClear autoFocus value={keyword} placeholder={t('ranges.filterSearch')} prefix={<SearchOutlined className="bin-muted-icon" />}
      onChange={event => setKeyword(event.target.value)} className="bin-chip-search" />}
    <div className="bin-chip-options">
      {typed && <button type="button" className="bin-chip-option" onClick={() => choose(typed)}>{t('ranges.useCode', { code: typed })}</button>}
      {shown.map(option => <button type="button" key={option.value} className={`bin-chip-option${option.value === value ? ' is-selected' : ''}`}
        onClick={() => choose(option.value)}>{option.label}</button>)}
      {!shown.length && !typed && <span className="bin-chip-empty">{t('ranges.noOptions')}</span>}
    </div>
  </div>;
  return <Popover open={open} onOpenChange={setOpen} trigger="click" placement="bottomLeft" content={content} arrow={false}>
    {value ? <span className="bin-chip is-active" role="button" tabIndex={0}>
      {t('ranges.chipValue', { label, value: selected?.label ?? value })}
      <CloseOutlined className="bin-chip-clear" aria-label={t('ranges.clear')} onClick={event => { event.stopPropagation(); onChange(null); }} />
    </span> : <button type="button" className="bin-chip"><PlusOutlined />{label}</button>}
  </Popover>;
}

function ChipGroup({ title, value, options, onChange }: { title: string; value: string | null; options: Option[]; onChange: (value: string | null) => void }) {
  return <div className="bin-chip-group"><div className="bin-chip-group-title">{title}</div>
    <div className="bin-chip-group-items">{options.map(option => <button type="button" key={option.value}
      className={`bin-chip${option.value === value ? ' is-active' : ''}`} onClick={() => onChange(option.value === value ? null : option.value)}>{option.label}</button>)}</div>
  </div>;
}

/** 当前筛选的可读摘要：BIN 前缀 999999、卡组织 Visa。 */
function filterSummary(t: ReturnType<typeof useT>, filters: RangeFilters): string {
  const parts = [
    filters.binPrefix && t('ranges.summary.binPrefix', { value: filters.binPrefix }),
    filters.cardBrand && t('ranges.summary.cardBrand', { value: enumLabel(t, 'enum.cardBrand', filters.cardBrand) }),
    filters.fundingType && t('ranges.summary.fundingType', { value: enumLabel(t, 'enum.fundingType', filters.fundingType) }),
    filters.issuerCountry && t('ranges.summary.issuerCountry', { value: countryName(filters.issuerCountry, null) }),
  ].filter(Boolean);
  return parts.join(t('ranges.summary.separator'));
}

// ---------- 匹配条与列表 ----------

/** 最长前缀匹配结果（画板 02 / 03）：识别时列出命中前缀与属性，未识别时为琥珀色提示。 */
function MatchBar({ lookup, bin }: { lookup: BinLookup; bin: string }) {
  const t = useT('card');
  const versionNo = lookup.binLibraryVersionNo ?? 0;
  if (!lookup.recognized) return <div className="bin-match is-miss"><ExclamationCircleOutlined />
    <strong>{t('match.unrecognized', { bin, versionNo })}</strong><span>{t('match.unrecognizedHint')}</span></div>;
  const attributes = [lookup.fundingType && enumLabel(t, 'enum.fundingType', lookup.fundingType), lookup.cardLevel,
    lookup.cardProduct && enumLabel(t, 'enum.cardProduct', lookup.cardProduct), lookup.issuerCountry && countryName(lookup.issuerCountry, null), lookup.issuerName]
    .filter(Boolean) as string[];
  const length = lookup.matchedPrefixLength ?? 0;
  return <div className="bin-match"><span className="bin-match-icon"><ScanOutlined /></span>
    <span className="bin-match-text">{richText(t, 'match.recognized', { prefix: <span className="bin-match-prefix">{lookup.binPrefix}</span> }, { bin })}</span>
    <span className="muted">{t('match.prefixLength', { length })}</span>
    {lookup.cardBrand && <BrandBadge brand={lookup.cardBrand} withName={false} />}
    <MetaLine className="bin-match-attrs" parts={attributes} />
    {length < 8 && bin.length < 8 && <span className="bin-match-hint">{t('match.longerHint')}</span>}
  </div>;
}

function RangeBody({ ranges, mobile, flags, filterSummary: summary, filtered, onClear }: {
  ranges: RangePage; mobile: boolean; flags: CountryFlags; filterSummary: string; filtered: boolean; onClear: () => void;
}) {
  const t = useT('card');
  const columns = useRangeColumns(flags);
  if (ranges.failure) return <div className="bin-failure"><QueryFailure failure={ranges.failure} retry={ranges.retry} /></div>;
  if (!ranges.data) return <div className="bin-loading"><Spin /></div>;
  if (!ranges.data.total) return filtered ? <EmptyState icon={<FileSearchOutlined />} title={t('ranges.empty.noMatch')}
    description={summary ? t('ranges.empty.currentFilters', { filters: summary }) : undefined}
    actions={<Button icon={<ReloadOutlined />} onClick={onClear}>{t('ranges.clearFilters')}</Button>} />
    : <EmptyState icon={<CreditCardOutlined />} title={t('ranges.empty.noRanges')} />;
  if (mobile) return <Spin spinning={ranges.loading}><div className="bin-cards">{ranges.data.items.map(range =>
    <RangeCard key={range.binPrefix} range={range} flagUrl={flags.flagOf(range.issuerCountry)} />)}</div></Spin>;
  return <Table<BinRange> className="bin-table" rowKey="binPrefix" size="middle" loading={ranges.loading} dataSource={ranges.data.items}
    pagination={false} tableLayout="fixed" scroll={{ x: 1000 }} columns={columns} />;
}

function useRangeColumns(flags: CountryFlags) {
  const t = useT('card');
  return useMemo(() => [
    { title: t('ranges.column.binPrefix'), width: 140, render: (_: unknown, row: BinRange) => <BinPrefix range={row} /> },
    { title: t('ranges.column.cardBrand'), width: 190, render: (_: unknown, row: BinRange) => <BrandBadge brand={row.cardBrand} /> },
    { title: t('ranges.column.fundingType'), width: 120, render: (_: unknown, row: BinRange) => dash(row.fundingType && enumLabel(t, 'enum.fundingType', row.fundingType)) },
    { title: t('ranges.column.cardLevel'), width: 130, render: (_: unknown, row: BinRange) => row.cardLevel ? <span className="mono">{row.cardLevel}</span> : dash(null) },
    { title: t('ranges.column.cardProduct'), width: 100, render: (_: unknown, row: BinRange) => dash(row.cardProduct && enumLabel(t, 'enum.cardProduct', row.cardProduct)) },
    { title: t('ranges.column.issuerCountry'), width: 170, render: (_: unknown, row: BinRange) => <CountryCell code={row.issuerCountry} flagUrl={flags.flagOf(row.issuerCountry)} /> },
    { title: t('ranges.column.issuerName'), ellipsis: true, render: (_: unknown, row: BinRange) => dash(row.issuerName) },
  ], [t, flags]);
}



/** 390 宽度的号段卡片（画板 08）。 */
function RangeCard({ range, flagUrl }: { range: BinRange; flagUrl: string | null }) {
  const t = useT('card');
  const attributes = [range.fundingType && enumLabel(t, 'enum.fundingType', range.fundingType), range.cardLevel,
    range.cardProduct && enumLabel(t, 'enum.cardProduct', range.cardProduct)].filter(Boolean) as string[];
  return <div className="bin-card">
    <div className="bin-card-head"><BinPrefix range={range} /><BrandBadge brand={range.cardBrand} withName={false} /></div>
    {attributes.length > 0 && <MetaLine className="bin-card-line" parts={attributes} />}
    <MetaLine className="bin-card-line" parts={[<CountryCell code={range.issuerCountry} flagUrl={flagUrl} />, range.issuerName]} />
  </div>;
}


/** 没有生效版本（画板 03b）：引导查看可激活版本或导入。 */
function NoActiveState({ pendingCount, canManage, onImport, onShowPending }: { pendingCount: number; canManage: boolean; onImport: () => void; onShowPending: () => void }) {
  const t = useT('card');
  return <EmptyState icon={<CreditCardOutlined />} title={t('ranges.empty.noActiveTitle')} description={t('ranges.empty.noActiveDescription')}
    actions={<>
      {pendingCount > 0 && <Button type="primary" onClick={onShowPending}>{t('ranges.empty.viewPending', { count: pendingCount })} <ArrowRightOutlined /></Button>}
      {canManage && <Button icon={<UploadOutlined />} onClick={onImport}>{t('page.import')}</Button>}
    </>} />;
}

