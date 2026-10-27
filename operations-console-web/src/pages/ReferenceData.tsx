import { useEffect, useMemo, useState } from 'react';
import type { Key, ReactNode } from 'react';
import { Empty, Input, Segmented, Select, Table, Tabs, Tag } from 'antd';
import { LockOutlined, SearchOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { CurrencySpecsTab } from './CurrencySpecs';
import { session } from '../api/session';
import { requestAllPages } from '../api/paging';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import { useLocale, useT } from '../i18n/react';
import {
  referenceDataTabs,
  countryName, currencyName, decodeCountries, industryDescription, decodeIndustries, decodeMccPage, decodeSubdivisions, industryTree, matchesKeyword,
  mccUsage, refdataName, referenceDataPaths, referenceStatusColors, referenceStatuses, shortFileAddress, subdivisionCountries, untranslated,
} from '../referenceData/referenceData';
import type { RefCountry, RefIndustry, RefMcc, RefSubdivision, ReferenceStatus } from '../referenceData/referenceData';
import { Flag, IndustryIcon } from '../referenceData/ReferenceDataParts';
import '../dataQuery/dataQuery.css';
import '../referenceData/referenceData.css';

type DataTab = 'countries' | 'subdivisions' | 'industries' | 'mccs';
type TabKey = 'currencies' | DataTab;
type StatusFilter = ReferenceStatus | 'ALL';
interface Loaded { countries?: RefCountry[]; subdivisions?: RefSubdivision[]; industries?: RefIndustry[]; mccs?: RefMcc[] }
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface Failures { countries?: unknown; subdivisions?: unknown; industries?: unknown; mccs?: unknown }
const PAGE_SIZE = 50;

/**
 * 全部参考数据依次读取（国家 → 行政区 → 行业 → MCC 全量分页），只读一次，各页签共用
 * <li>依次请求：参考数据路由按登录账号限并发 2，同时发出会被 Gateway 以 429 拒绝。</li>
 * <li>某类失败只影响对应页签，可单独重试。</li>
 */
function useReferenceData(enabled: boolean) {
  const [data, setData] = useState<Loaded>({}); const [failures, setFailures] = useState<Failures>({});
  const [revision, setRevision] = useState<Record<DataTab, number>>({ countries: 0, subdivisions: 0, industries: 0, mccs: 0 });
  const [loading, setLoading] = useState<Set<DataTab>>(new Set(enabled ? dataTabs : []));
  const [order, setOrder] = useState<DataTab[]>([...dataTabs]);
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController(); const { signal } = controller;
    const loaders: Record<DataTab, () => Promise<unknown>> = {
      countries: () => session.request(referenceDataPaths.countries, { signal }, decodeCountries),
      subdivisions: () => session.request(referenceDataPaths.subdivisions, { signal }, value => decodeSubdivisions(value)),
      industries: () => session.request(referenceDataPaths.industries, { signal }, decodeIndustries),
      mccs: () => requestAllPages<RefMcc>(referenceDataPaths.mccs, signal, session, {}, decodeMccPage),
    };
    void (async () => {
      for (const key of order) {
        setLoading(current => new Set(current).add(key)); setFailures(current => ({ ...current, [key]: undefined }));
        try {
          const value = await loaders[key]();
          if (signal.aborted) return;
          setData(current => ({ ...current, [key]: value }));
        } catch (error) {
          if (signal.aborted) return;
          setFailures(current => ({ ...current, [key]: error }));
        }
        setLoading(current => { const next = new Set(current); next.delete(key); return next; });
      }
    })();
    return () => controller.abort();
  }, [order, revision, enabled]);
  const retry = (key: DataTab) => { setOrder([key]); setRevision(current => ({ ...current, [key]: current[key] + 1 })); };
  return { data, failures, loading, retry };
}

const dataTabs: readonly DataTab[] = ['countries', 'subdivisions', 'industries', 'mccs'];

/**
 * 参考数据（原型 平台配置 · 参考数据 01—04 与币种 05—06）：币种（可在线维护）+ 国家、行政区、行业、MCC（只读，维护走评审过的 SQL）
 * <li>页签按授权显示：币种页签需要原"币种"菜单（OPERATIONS_CURRENCY_SPECS），其余四个需要"参考数据"菜单（见 referenceDataTabs）。</li>
 * <li>所选页签写在地址里（`?tab=currencies`、`?tab=subdivisions&country=AE`），旧地址 /currency-specs 跳到币种页签。</li>
 */
export function ReferenceData({ resources, onAuthorizationChanged }: Props) {
  const t = useT('reference-data'), tNav = useT('nav');
  const tabKeys = referenceDataTabs(resources) as readonly TabKey[];
  const dataAllowed = tabKeys.includes('countries');
  const { data, failures, loading, retry } = useReferenceData(dataAllowed);
  const [params, setParams] = useSearchParams();
  const requested = params.get('tab') as TabKey | null;
  const tab: TabKey = requested && tabKeys.includes(requested) ? requested : tabKeys[0] ?? 'countries';
  const setTab = (next: TabKey) => setParams(current => { const query = new URLSearchParams(current); query.set('tab', next);
    if (next !== 'subdivisions') query.delete('country'); return query; }, { replace: true });
  const subdivisionCountry = params.get('country') ?? undefined;
  const setSubdivisionCountry = (country: string) => setParams({ tab: 'subdivisions', country }, { replace: true });
  const [mccKeyword, setMccKeyword] = useState('');
  const count = (key: TabKey) => key === 'currencies' ? undefined : data[key]?.length;
  const body = (key: DataTab, render: () => ReactNode) => failures[key] ? <QueryFailure failure={failures[key]} retry={() => retry(key)} />
    : !data[key] && loading.has(key) ? <Loading /> : data[key] ? render() : <Loading />;
  const openSubdivisions = (countryCode: string) => setSubdivisionCountry(countryCode);
  const openMcc = (mccCode: string) => { setMccKeyword(mccCode); setTab('mccs'); };
  return <div className="configuration-page dq-page refdata-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_REFERENCE_DATA')}</h1>
      {tab !== 'currencies' && <span className="muted refdata-readonly"><LockOutlined /> {t('page.readOnly')}</span>}</div>
    <p className="muted dq-intro">{t('page.intro')}</p>
    <Tabs activeKey={tab} onChange={key => setTab(key as TabKey)} items={tabKeys.map(key => ({
      key, label: <span>{t('tab.' + key)} {count(key) !== undefined && <span className="refdata-count">{count(key)}</span>}</span>,
      children: key === 'currencies' ? <CurrencySpecsTab resources={resources} onAuthorizationChanged={onAuthorizationChanged} />
        : key === 'countries' ? body(key, () => <CountriesTab countries={data.countries!} subdivisions={data.subdivisions}
          onOpenSubdivisions={openSubdivisions} />)
        : key === 'subdivisions' ? body(key, () => <SubdivisionsTab subdivisions={data.subdivisions!} country={subdivisionCountry}
          onCountry={setSubdivisionCountry} />)
        : key === 'industries' ? body(key, () => <IndustriesTab industries={data.industries!} mccs={data.mccs} onOpenMcc={openMcc} />)
        : body(key, () => <MccsTab mccs={data.mccs!} industries={data.industries} keyword={mccKeyword} onKeyword={setMccKeyword} />),
    }))} />
  </div>;
}

function StatusTag({ status }: { status: ReferenceStatus }) {
  const t = useT('reference-data');
  return <Tag color={referenceStatusColors[status]}>{enumLabel(t, 'enum.status', status)}</Tag>;
}

function StatusSelect({ value, onChange }: { value: StatusFilter; onChange: (value: StatusFilter) => void }) {
  const t = useT('reference-data');
  return <Select<StatusFilter> className="refdata-filter" value={value} onChange={onChange} options={[
    { value: 'ALL', label: t('filter.statusValue', { status: t('filter.all') }) },
    ...referenceStatuses.map(status => ({ value: status, label: t('filter.statusValue', { status: enumLabel(t, 'enum.status', status) }) }))]} />;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <div className="refdata-field"><span className="muted">{label}</span><span>{children}</span></div>;
}

function FileAddress({ address, url }: { address: string | null; url: string | null }) {
  const t = useT('reference-data');
  if (!address) return <span className="muted">—</span>;
  return <span className="refdata-file"><span className="mono" title={address}>{shortFileAddress(address)}</span>
    <span className="muted"> · {url ? t('detail.publicFile') : t('detail.notPublic')}</span></span>;
}

function CountriesTab({ countries, subdivisions, onOpenSubdivisions }: {
  countries: RefCountry[]; subdivisions: RefSubdivision[] | undefined; onOpenSubdivisions: (countryCode: string) => void;
}) {
  const t = useT('reference-data'); const { locale } = useLocale();
  const [keyword, setKeyword] = useState(''); const [region, setRegion] = useState('ALL'); const [status, setStatus] = useState<StatusFilter>('ACTIVE');
  const [selected, setSelected] = useState<string>(); const [page, setPage] = useState(1);
  const regions = useMemo(() => [...new Set(countries.map(c => c.regionCode).filter((r): r is string => !!r))].sort(), [countries]);
  const rows = useMemo(() => countries.filter(c => (status === 'ALL' || c.status === status) && (region === 'ALL' || c.regionCode === region)
    && matchesKeyword(keyword, c.countryCode, c.alpha3Code, c.numericCode, c.name, countryName(c.countryCode, c.name, locale)))
    .sort((a, b) => countryName(a.countryCode, a.name, locale).localeCompare(countryName(b.countryCode, b.name, locale), locale)),
  [countries, keyword, region, status, locale]);
  const current = rows.find(c => c.countryCode === selected) ?? rows[0];
  const filter = <T,>(set: (value: T) => void) => (value: T) => { set(value); setPage(1); };
  return <>
    <div className="refdata-toolbar">
      <Input className="refdata-search" allowClear prefix={<SearchOutlined />} placeholder={t('country.search')} value={keyword} onChange={e => filter(setKeyword)(e.target.value)} />
      <Select className="refdata-filter" value={region} onChange={filter(setRegion)} options={[{ value: 'ALL', label: t('country.regionValue', { region: t('filter.all') }) },
        ...regions.map(value => ({ value, label: t('country.regionValue', { region: value }) }))]} />
      <StatusSelect value={status} onChange={filter(setStatus)} />
      <span className="dq-spacer" /><span className="muted">{t('country.summary', { count: rows.length })}</span>
    </div>
    <div className="dq-master-detail refdata-split">
      <section className="dq-panel">
        <Table<RefCountry> rowKey="countryCode" size="small" tableLayout="fixed" dataSource={rows} scroll={{ x: 760 }}
          pagination={{ current: page, pageSize: PAGE_SIZE, showSizeChanger: false, onChange: setPage, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('empty.filtered')} /> }}
          rowClassName={row => `dq-clickable${row.countryCode === current?.countryCode ? ' dq-selected-row' : ''}`}
          onRow={row => ({ onClick: () => setSelected(row.countryCode) })} columns={[
            { title: t('country.column.flag'), width: 56, render: (_, c) => <Flag url={c.logoPublicUrl} label={c.countryCode} /> },
            { title: t('country.column.code'), width: 60, render: (_, c) => <strong>{c.countryCode}</strong> },
            { title: t('country.column.alpha3'), width: 72, render: (_, c) => c.alpha3Code ?? '—' },
            { title: t('country.column.numeric'), width: 72, render: (_, c) => c.numericCode ?? '—' },
            { title: t('country.column.name'), ellipsis: true, render: (_, c) => countryName(c.countryCode, c.name, locale) },
            { title: t('country.column.callingCode'), width: 84, render: (_, c) => c.callingCode ?? '—' },
            { title: t('country.column.currency'), width: 96, render: (_, c) => c.defaultCurrencyCode ?? '—' },
            { title: t('country.column.region'), width: 128, ellipsis: true, render: (_, c) => c.regionCode ?? '—' },
            { title: t('column.status'), width: 104, className: 'refdata-nowrap', render: (_, c) => <StatusTag status={c.status} /> },
          ]} />
        <p className="muted dq-table-foot">{t('country.footnote')}</p>
      </section>
      <section className="dq-panel refdata-detail">
        {current ? <CountryDetail country={current} subdivisions={subdivisions?.filter(s => s.countryCode === current.countryCode)}
          onOpenSubdivisions={onOpenSubdivisions} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('detail.selectHint')} />}
      </section>
    </div>
  </>;
}

function CountryDetail({ country, subdivisions, onOpenSubdivisions }: {
  country: RefCountry; subdivisions: RefSubdivision[] | undefined; onOpenSubdivisions: (countryCode: string) => void;
}) {
  const t = useT('reference-data'), tRef = useT('refdata'); const { locale } = useLocale();
  const preview = (subdivisions ?? []).filter(s => s.status === 'ACTIVE').slice(0, 4);
  return <>
    <div className="refdata-detail-head"><Flag url={country.logoPublicUrl} label={country.countryCode} />
      <div className="refdata-grow"><h2>{countryName(country.countryCode, country.name, locale)}</h2>
        <span className="muted">{[country.countryCode, country.alpha3Code, country.numericCode].filter(Boolean).join(' · ')}</span></div>
      <StatusTag status={country.status} /></div>
    <Field label={t('detail.englishName')}>{country.name ?? '—'}</Field>
    <Field label={t('country.column.callingCode')}>{country.callingCode ?? '—'}</Field>
    <Field label={t('country.column.currency')}>{country.defaultCurrencyCode
      ? <><strong>{country.defaultCurrencyCode}</strong> <span className="muted">{currencyName(country.defaultCurrencyCode, null, locale)}</span></> : '—'}</Field>
    <Field label={t('country.column.region')}>{country.regionCode ?? '—'}</Field>
    <Field label={t('country.flag')}><FileAddress address={country.logoFileAddress} url={country.logoPublicUrl} /></Field>
    <Field label={t('detail.version')}>{country.version}</Field>
    {subdivisions && subdivisions.length > 0 && <>
      <div className="dq-section-title">{t('country.subdivisions', { count: subdivisions.length })}</div>
      {preview.map(s => <div key={s.subdivisionCode} className="refdata-subdivision-row"><strong>{s.subdivisionCode}</strong>
        <span>{refdataName(tRef, 'subdivision', s.subdivisionCode, s.name)}</span><span className="muted" dir="auto">{s.localName ?? ''}</span></div>)}
      <button type="button" className="dq-link" onClick={() => onOpenSubdivisions(country.countryCode)}>{t('country.viewSubdivisions', { count: subdivisions.length })}</button>
    </>}
    <p className="muted refdata-note">{t('country.note')}</p>
  </>;
}

function SubdivisionsTab({ subdivisions, country, onCountry }: {
  subdivisions: RefSubdivision[]; country: string | undefined; onCountry: (countryCode: string) => void;
}) {
  const t = useT('reference-data'), tRef = useT('refdata'); const { locale } = useLocale();
  const countries = useMemo(() => subdivisionCountries(subdivisions), [subdivisions]);
  const [keyword, setKeyword] = useState(''); const [status, setStatus] = useState<StatusFilter>('ALL');
  const active = country && countries.some(c => c.countryCode === country) ? country : countries[0]?.countryCode;
  const rows = subdivisions.filter(s => s.countryCode === active && (status === 'ALL' || s.status === status)
    && matchesKeyword(keyword, s.subdivisionCode, s.name, s.localName, refdataName(tRef, 'subdivision', s.subdivisionCode, s.name)));
  if (countries.length === 0) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('empty.subdivisions')} />;
  return <>
    <div className="refdata-toolbar">
      <strong>{t('subdivision.country')}</strong>
      {countries.map(c => <button type="button" key={c.countryCode} className={`refdata-chip${c.countryCode === active ? ' is-active' : ''}`}
        onClick={() => onCountry(c.countryCode)}>{t('subdivision.countryChip', { code: c.countryCode, name: countryName(c.countryCode, null, locale), count: c.count })}</button>)}
      <Input className="refdata-search" allowClear prefix={<SearchOutlined />} placeholder={t('subdivision.search')} value={keyword} onChange={e => setKeyword(e.target.value)} />
      <StatusSelect value={status} onChange={setStatus} />
      <span className="dq-spacer" /><span className="muted">{t('subdivision.hint')}</span>
    </div>
    <section className="dq-panel">
      <Table<RefSubdivision> rowKey="subdivisionCode" size="small" tableLayout="fixed" dataSource={rows} pagination={false} scroll={{ x: 760 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('empty.filtered')} /> }} columns={[
          { title: t('subdivision.column.code'), width: 100, render: (_, s) => <strong>{s.subdivisionCode}</strong> },
          { title: t('subdivision.column.name'), render: (_, s) => refdataName(tRef, 'subdivision', s.subdivisionCode, s.name) },
          { title: t('subdivision.column.englishName'), render: (_, s) => s.name ?? '—' },
          { title: t('subdivision.column.localName'), render: (_, s) => <span dir="auto">{s.localName ?? '—'}</span> },
          { title: t('subdivision.column.category'), width: 160, render: (_, s) => s.category ? enumLabel(t, 'enum.subdivisionCategory', s.category) : '—' },
          { title: t('subdivision.column.parent'), width: 90, render: (_, s) => s.parentSubdivisionCode ?? '—' },
          { title: t('column.status'), width: 104, className: 'refdata-nowrap', render: (_, s) => <StatusTag status={s.status} /> },
        ]} />
      <p className="muted dq-table-foot">{t('subdivision.footnote')}</p>
    </section>
  </>;
}

function IndustriesTab({ industries, mccs, onOpenMcc }: { industries: RefIndustry[]; mccs: RefMcc[] | undefined; onOpenMcc: (mccCode: string) => void }) {
  const t = useT('reference-data'), tRef = useT('refdata');
  const [keyword, setKeyword] = useState(''); const [status, setStatus] = useState<StatusFilter>('ACTIVE');
  const [selected, setSelected] = useState<string>();
  const mccByCode = useMemo(() => new Map((mccs ?? []).map(m => [m.mccCode, m])), [mccs]);
  const name = (i: RefIndustry) => refdataName(tRef, 'industry', i.industryCode, i.name);
  const visible = (i: RefIndustry) => (status === 'ALL' || i.status === status) && matchesKeyword(keyword, i.industryCode, i.name, name(i));
  const groups = industryTree(industries).map(group => ({ ...group, children: group.children.filter(child => visible(child)
    || (!!group.category && !!keyword.trim() && visible(group.category))) }))
    .filter(group => group.children.length > 0 || (group.category && visible(group.category)));
  type Row = RefIndustry & { children?: RefIndustry[] };
  const rows: Row[] = groups.flatMap(group => group.category ? [{ ...group.category, children: group.children.length ? group.children : undefined }] : group.children);
  const all = groups.flatMap(group => [...(group.category ? [group.category] : []), ...group.children]);
  const current = all.find(i => i.industryCode === selected) ?? groups[0]?.children[0] ?? all[0];
  const [expanded, setExpanded] = useState<readonly Key[] | null>(null);
  const expandedKeys = expanded ?? rows.filter(r => r.children).map(r => r.industryCode);
  const mccLabel = (code: string) => refdataName(tRef, 'mcc', code, mccByCode.get(code)?.name);
  const categories = industries.filter(i => i.level === 1).length, subcategories = industries.length - categories;
  return <>
    <div className="refdata-toolbar">
      <Input className="refdata-search" allowClear prefix={<SearchOutlined />} placeholder={t('industry.search')} value={keyword} onChange={e => setKeyword(e.target.value)} />
      <StatusSelect value={status} onChange={setStatus} />
      <span className="dq-spacer" /><span className="muted">{t('industry.summary', { categories, subcategories })}</span>
    </div>
    <div className="dq-master-detail refdata-split">
      <section className="dq-panel">
        <Table<Row> rowKey="industryCode" size="small" tableLayout="fixed" dataSource={rows} pagination={false} scroll={{ x: 720 }}
          expandable={{ expandedRowKeys: expandedKeys, onExpandedRowsChange: keys => setExpanded(keys) }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('empty.filtered')} /> }}
          rowClassName={row => `dq-clickable${row.industryCode === current?.industryCode ? ' dq-selected-row' : ''}`}
          onRow={row => ({ onClick: () => setSelected(row.industryCode) })} columns={[
            { title: t('industry.column.codeAndName'), render: (_, i) => <span className="refdata-code-cell">
              {i.level === 1 && <IndustryIcon url={i.logoPublicUrl} />}<span className="dq-cell"><span className={`mono${i.level === 1 ? ' is-strong' : ''}`}>{i.industryCode}</span>
                <span className="refdata-name">{name(i)}</span></span></span> },
            { title: t('industry.column.level'), width: 112, className: 'refdata-nowrap', render: (_, i) => enumLabel(t, 'enum.industryLevel', String(i.level)) },
            { title: t('industry.column.defaultMcc'), width: 240, ellipsis: true, render: (_, i) => i.defaultMccCode
              ? <><strong>{i.defaultMccCode}</strong> <span className="muted">{mccLabel(i.defaultMccCode)}</span></> : <span className="muted">—</span> },
            { title: t('industry.column.sortNo'), width: 64, render: (_, i) => i.sortNo },
            { title: t('column.status'), width: 104, className: 'refdata-nowrap', render: (_, i) => <StatusTag status={i.status} /> },
          ]} />
        <p className="muted dq-table-foot">{t('industry.footnote')} <button type="button" className="dq-link" onClick={() => setExpanded(rows.map(r => r.industryCode))}>{t('industry.expandAll')}</button>
          {' / '}<button type="button" className="dq-link" onClick={() => setExpanded([])}>{t('industry.collapseAll')}</button></p>
      </section>
      <section className="dq-panel refdata-detail">
        {current ? <IndustryDetail industry={current} parent={industries.find(i => i.industryCode === current.parentIndustryCode)}
          mccName={current.defaultMccCode ? mccLabel(current.defaultMccCode) : null} onOpenMcc={onOpenMcc} />
          : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('detail.selectHint')} />}
      </section>
    </div>
  </>;
}

function IndustryDetail({ industry, parent, mccName, onOpenMcc }: {
  industry: RefIndustry; parent: RefIndustry | undefined; mccName: string | null; onOpenMcc: (mccCode: string) => void;
}) {
  const t = useT('reference-data'), tRef = useT('refdata');
  const name = refdataName(tRef, 'industry', industry.industryCode, industry.name);
  const description = industryDescription(tRef, industry.industryCode, industry.description);
  return <>
    <div className="refdata-detail-head"><IndustryIcon url={industry.logoPublicUrl ?? parent?.logoPublicUrl ?? null} />
      <div className="refdata-grow"><h2>{name}</h2>
        <span className="muted"><span className="mono">{industry.industryCode}</span> · {enumLabel(t, 'enum.industryLevel', String(industry.level))}</span></div>
      <StatusTag status={industry.status} /></div>
    <Field label={t('detail.englishName')}>{industry.name ?? '—'}</Field>
    {parent && <Field label={t('industry.parent')}><strong className="mono">{parent.industryCode}</strong> <span className="muted">
      {refdataName(tRef, 'industry', parent.industryCode, parent.name)}</span></Field>}
    {industry.level === 2 && <Field label={t('industry.column.defaultMcc')}>{industry.defaultMccCode
      ? <><strong>{industry.defaultMccCode}</strong> {mccName} <button type="button" className="dq-link" onClick={() => onOpenMcc(industry.defaultMccCode!)}>{t('industry.viewMcc')}</button></>
      : '—'}</Field>}
    <Field label={t('industry.column.sortNo')}>{industry.sortNo}</Field>
    <Field label={t('industry.icon')}><FileAddress address={industry.logoFileAddress} url={industry.logoPublicUrl} /></Field>
    {description && <><div className="dq-section-title">{t('industry.description')}</div>
      <div className="refdata-description">{description}</div></>}
    <p className="muted refdata-note">{t('industry.localizedAs', { name, key: `refdata.industry.${industry.industryCode}` })}</p>
    <p className="muted refdata-note">{t('industry.note')}</p>
  </>;
}

type MccChip = 'ALL' | 'HIGH_RISK' | 'REFERENCED';

function MccsTab({ mccs, industries, keyword, onKeyword }: {
  mccs: RefMcc[]; industries: RefIndustry[] | undefined; keyword: string; onKeyword: (keyword: string) => void;
}) {
  const t = useT('reference-data'), tRef = useT('refdata');
  const [chip, setChip] = useState<MccChip>('ALL'); const [status, setStatus] = useState<StatusFilter>('ALL');
  const [selected, setSelected] = useState<string>(); const [page, setPage] = useState(1);
  const usage = useMemo(() => mccUsage(industries ?? []), [industries]);
  const industryByCode = useMemo(() => new Map((industries ?? []).map(i => [i.industryCode, i])), [industries]);
  const name = (m: RefMcc) => refdataName(tRef, 'mcc', m.mccCode, m.name);
  const needle = keyword.trim();
  const rows = mccs.filter(m => (status === 'ALL' || m.status === status) && (chip !== 'HIGH_RISK' || m.networkHighRisk)
    && (chip !== 'REFERENCED' || usage.has(m.mccCode))
    && (!needle || m.mccCode.startsWith(needle) || matchesKeyword(needle, m.name, name(m))));
  const current = rows.find(m => m.mccCode === selected) ?? (needle && /^[0-9]{4}$/.test(needle) ? rows[0] : undefined) ?? rows[0];
  const filter = <T,>(set: (value: T) => void) => (value: T) => { set(value); setPage(1); };
  return <>
    <div className="refdata-toolbar">
      <Input className="refdata-search" allowClear prefix={<SearchOutlined />} placeholder={t('mcc.search')} value={keyword} onChange={e => filter(onKeyword)(e.target.value)} />
      <Segmented<MccChip> value={chip} onChange={filter(setChip)} options={(['ALL', 'HIGH_RISK', 'REFERENCED'] as const).map(value => ({ value, label: t('mcc.chip.' + value) }))} />
      <StatusSelect value={status} onChange={filter(setStatus)} />
      <span className="dq-spacer" /><span className="muted">{t('mcc.summary', { count: rows.length })}</span>
    </div>
    <div className="dq-master-detail refdata-split">
      <section className="dq-panel">
        <Table<RefMcc> rowKey="mccCode" size="small" tableLayout="fixed" dataSource={rows} scroll={{ x: 680 }}
          pagination={{ current: page, pageSize: PAGE_SIZE, showSizeChanger: false, onChange: setPage, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('empty.filtered')} /> }}
          rowClassName={row => `dq-clickable${row.mccCode === current?.mccCode ? ' dq-selected-row' : ''}`}
          onRow={row => ({ onClick: () => setSelected(row.mccCode) })} columns={[
            { title: t('mcc.column.code'), width: 80, render: (_, m) => <strong>{m.mccCode}</strong> },
            { title: t('mcc.column.name'), render: (_, m) => <div className="dq-cell"><span>{name(m)}</span>
              {untranslated(tRef, 'mcc', m.mccCode, m.name) ? <span className="muted">{t('mcc.noTranslation')}</span>
                : name(m) !== m.name && m.name ? <span className="muted">{m.name}</span> : null}</div> },
            { title: t('mcc.column.referencedBy'), width: 180, render: (_, m) => usage.get(m.mccCode)?.map(code => <div key={code} className="mono">{code}</div>)
              ?? <span className="muted">—</span> },
            { title: t('mcc.column.highRisk'), width: 120, className: 'refdata-nowrap', render: (_, m) => m.networkHighRisk ? <Tag color="red">{t('mcc.highRisk')}</Tag> : null },
            { title: t('column.status'), width: 104, className: 'refdata-nowrap', render: (_, m) => <StatusTag status={m.status} /> },
          ]} />
        <p className="muted dq-table-foot">{t('mcc.footnote')}</p>
      </section>
      <section className="dq-panel refdata-detail">
        {current ? <>
          <div className="refdata-detail-head"><div className="refdata-grow"><h2>{current.mccCode} · {name(current)}</h2>
            <span className="muted">{current.name ?? ''}</span></div><StatusTag status={current.status} /></div>
          {current.description && <div className="refdata-description">{current.description}</div>}
          <Field label={t('mcc.column.highRisk')}><Tag color={current.networkHighRisk ? 'red' : undefined}>{current.networkHighRisk ? t('detail.yes') : t('detail.no')}</Tag>
            <span className="muted">{t('mcc.highRiskSource')}</span></Field>
          <Field label={t('mcc.column.referencedBy')}>{usage.get(current.mccCode)?.map(code => <div key={code}><strong className="mono">{code}</strong>{' '}
            <span className="muted">{refdataName(tRef, 'industry', code, industryByCode.get(code)?.name)}</span></div>) ?? '—'}</Field>
          <Field label={t('detail.version')}>{current.version}</Field>
          <p className="muted refdata-note">{t('mcc.note')}</p>
        </> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('detail.selectHint')} />}
      </section>
    </div>
  </>;
}
