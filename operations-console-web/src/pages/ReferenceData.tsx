import { useEffect, useMemo, useRef, useState } from 'react';
import type { Key, ReactNode } from 'react';
import { Empty, Input, Select, Table, Tabs, Tag } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import { useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { CurrencySpecsTab } from './CurrencySpecs';
import { usePaging } from '../hooks/usePaging';
import { Loading, QueryFailure, useDetail } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import { useLocale, useT } from '../i18n/react';
import {
  referenceDataTabs,
  countryName, currencyName, decodeCountries, industryDescription, decodeIndustries, decodeMccPage, decodeSubdivisions, industryTree, matchesKeyword,
  mccQuery, refdataName, referenceDataPaths, referenceStatusColors, referenceStatuses, shortFileAddress, subdivisionCountries, untranslated,
} from '../referenceData/referenceData';
import type { RefCountry, RefIndustry, RefMcc, RefSubdivision, ReferenceStatus } from '../referenceData/referenceData';
import { Flag, IndustryIcon } from '../referenceData/ReferenceDataParts';
import '../dataQuery/dataQuery.css';
import '../referenceData/referenceData.css';

type TabKey = 'currencies' | 'countries' | 'subdivisions' | 'industries' | 'mccs';
type StatusFilter = ReferenceStatus | 'ALL';
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const PAGE_SIZE = 50;
const decodeAllSubdivisions = (value: unknown) => decodeSubdivisions(value);

/** 一次读取的列表页签：在页签首次打开时挂载，挂载时才请求；离开页签后保留，回来不重查。 */
function ListBody<T>({ path, decode, children }: { path: string; decode: (value: unknown) => T; children: (data: T) => ReactNode }) {
  const { data, failure, refresh } = useDetail(path, decode);
  if (failure) return <QueryFailure failure={failure} retry={refresh} />;
  return data ? <>{children(data)}</> : <Loading />;
}

/**
 * 参考数据（原型 平台配置 · 参考数据 01—04 与币种 05—06）：币种（可在线维护）+ 国家、行政区、行业、MCC（只读，维护走评审过的 SQL）
 * <li>页签按授权显示：币种页签需要原"币种"菜单（OPERATIONS_CURRENCY_SPECS），其余四个需要"参考数据"菜单（见 referenceDataTabs）。</li>
 * <li>所选页签写在地址里（`?tab=currencies`、`?tab=subdivisions&country=AE`），旧地址 /currency-specs 跳到币种页签。</li>
 * <li>每个页签切换过去才加载自己的数据，页签之间不互相取数；MCC 按页向后端查询，翻页再查。</li>
 */
export function ReferenceData({ resources, onAuthorizationChanged }: Props) {
  const t = useT('reference-data'), tNav = useT('nav');
  const tabKeys = referenceDataTabs(resources) as readonly TabKey[];
  const [params, setParams] = useSearchParams();
  const requested = params.get('tab') as TabKey | null;
  const tab: TabKey = requested && tabKeys.includes(requested) ? requested : tabKeys[0] ?? 'countries';
  const setTab = (next: TabKey) => setParams(current => { const query = new URLSearchParams(current); query.set('tab', next);
    if (next !== 'subdivisions') query.delete('country'); return query; }, { replace: true });
  const subdivisionCountry = params.get('country') ?? undefined;
  const setSubdivisionCountry = (country: string) => setParams({ tab: 'subdivisions', country }, { replace: true });
  const [mccKeyword, setMccKeyword] = useState('');
  const openMcc = (mccCode: string) => { setMccKeyword(mccCode); setTab('mccs'); };
  return <div className="configuration-page dq-page refdata-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_REFERENCE_DATA')}</h1></div>
    <Tabs activeKey={tab} onChange={key => setTab(key as TabKey)} items={tabKeys.map(key => ({
      key, label: t('tab.' + key),
      children: key === 'currencies' ? <CurrencySpecsTab resources={resources} onAuthorizationChanged={onAuthorizationChanged} />
        : key === 'countries' ? <ListBody path={referenceDataPaths.countries} decode={decodeCountries}>
          {countries => <CountriesTab countries={countries} onOpenSubdivisions={setSubdivisionCountry} />}</ListBody>
        : key === 'subdivisions' ? <ListBody path={referenceDataPaths.subdivisions} decode={decodeAllSubdivisions}>
          {subdivisions => <SubdivisionsTab subdivisions={subdivisions} country={subdivisionCountry} onCountry={setSubdivisionCountry} />}</ListBody>
        : key === 'industries' ? <ListBody path={referenceDataPaths.industries} decode={decodeIndustries}>
          {industries => <IndustriesTab industries={industries} onOpenMcc={openMcc} />}</ListBody>
        : <MccsTab keyword={mccKeyword} onKeyword={setMccKeyword} />,
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

function CountriesTab({ countries, onOpenSubdivisions }: { countries: RefCountry[]; onOpenSubdivisions: (countryCode: string) => void }) {
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
        {current ? <CountryDetail country={current} onOpenSubdivisions={onOpenSubdivisions} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('detail.selectHint')} />}
      </section>
    </div>
  </>;
}

function CountryDetail({ country, onOpenSubdivisions }: { country: RefCountry; onOpenSubdivisions: (countryCode: string) => void }) {
  const t = useT('reference-data'); const { locale } = useLocale();
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
    <Field label={t('tab.subdivisions')}><button type="button" className="dq-link" onClick={() => onOpenSubdivisions(country.countryCode)}>{t('country.openSubdivisions')}</button></Field>
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
        onClick={() => onCountry(c.countryCode)}>{t('subdivision.countryChipName', { code: c.countryCode, name: countryName(c.countryCode, null, locale) })}</button>)}
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

function IndustriesTab({ industries, onOpenMcc }: { industries: RefIndustry[]; onOpenMcc: (mccCode: string) => void }) {
  const t = useT('reference-data'), tRef = useT('refdata');
  const [keyword, setKeyword] = useState(''); const [status, setStatus] = useState<StatusFilter>('ACTIVE');
  const [selected, setSelected] = useState<string>();
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
  // MCC 名称取 refdata 语言包，不为此去查 MCC 列表；没有译文时只显示编码
  const mccLabel = (code: string) => { const label = refdataName(tRef, 'mcc', code, null); return label === code ? '' : label; };
  return <>
    <div className="refdata-toolbar">
      <Input className="refdata-search" allowClear prefix={<SearchOutlined />} placeholder={t('industry.search')} value={keyword} onChange={e => setKeyword(e.target.value)} />
      <StatusSelect value={status} onChange={setStatus} />
      <span className="refdata-expand"><button type="button" className="dq-link" onClick={() => setExpanded(rows.map(r => r.industryCode))}>{t('industry.expandAll')}</button>
        <span className="muted">/</span><button type="button" className="dq-link" onClick={() => setExpanded([])}>{t('industry.collapseAll')}</button></span>
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
        <p className="muted dq-table-foot">{t('industry.footnote')}</p>
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

/**
 * MCC：按页向后端查询（每页 50），关键字（编码前缀或英文名）与状态由后端筛选，翻页再查
 * <li>关键字输入停顿 300 ms 后查询；从行业页签"查看 MCC"带入编码时同样查询。</li>
 */
function MccsTab({ keyword, onKeyword }: { keyword: string; onKeyword: (keyword: string) => void }) {
  const t = useT('reference-data'), tRef = useT('refdata');
  const [status, setStatus] = useState<StatusFilter>('ALL'); const [selected, setSelected] = useState<string>();
  const page = usePaging<RefMcc>(referenceDataPaths.mccs, decodeMccPage, mccQuery(keyword, 'ALL'),
    { initialPaging: { page: 1, size: PAGE_SIZE }, notifyOnError: false });
  const applied = useRef({ keyword: keyword.trim(), status });
  useEffect(() => {
    const next = { keyword: keyword.trim(), status };
    if (next.keyword === applied.current.keyword && next.status === applied.current.status) return;
    const timer = setTimeout(() => { applied.current = next; page.applyFilters(mccQuery(next.keyword, next.status)); }, 300);
    return () => clearTimeout(timer);
  }, [keyword, status]);
  const name = (m: RefMcc) => refdataName(tRef, 'mcc', m.mccCode, m.name);
  const rows = page.error ? [] : page.data.items;
  const current = rows.find(m => m.mccCode === selected) ?? rows[0];
  return <>
    <div className="refdata-toolbar">
      <Input className="refdata-search" allowClear prefix={<SearchOutlined />} placeholder={t('mcc.search')} value={keyword} onChange={e => onKeyword(e.target.value)} />
      <StatusSelect value={status} onChange={setStatus} />
    </div>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <div className="dq-master-detail refdata-split">
      <section className="dq-panel">
        <Table<RefMcc> rowKey="mccCode" size="small" tableLayout="fixed" dataSource={rows} loading={page.loading} scroll={{ x: 600 }}
          pagination={{ ...page.pagination, showSizeChanger: false, hideOnSinglePage: true }}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('empty.filtered')} /> }}
          rowClassName={row => `dq-clickable${row.mccCode === current?.mccCode ? ' dq-selected-row' : ''}`}
          onRow={row => ({ onClick: () => setSelected(row.mccCode) })} columns={[
            { title: t('mcc.column.code'), width: 80, render: (_, m) => <strong>{m.mccCode}</strong> },
            { title: t('mcc.column.name'), render: (_, m) => <div className="dq-cell"><span>{name(m)}</span>
              {untranslated(tRef, 'mcc', m.mccCode, m.name) ? <span className="muted">{t('mcc.noTranslation')}</span>
                : name(m) !== m.name && m.name ? <span className="muted">{m.name}</span> : null}</div> },
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
          <Field label={t('detail.version')}>{current.version}</Field>
          <p className="muted refdata-note">{t('mcc.note')}</p>
        </> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={page.loading ? undefined : t('detail.selectHint')} />}
      </section>
    </div>}
  </>;
}
