import { useEffect, useMemo, useState } from 'react';
import { Button, Empty, Segmented, Select, Table, Tag } from 'antd';
import { DatabaseOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { session } from '../api/session';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import { StatusDot, tone } from '../clientReview/reviewParts';
import { enumLabel, tOr } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { catalogUsages, configApi, decodeCatalogItems, decodeJurisdictions, decodePrograms, decodeSchemas, programInUse, groupFields, referenceCatalogHref, ruleRows } from './onboardingConfig';
import type { CatalogItem, CatalogUsage, Jurisdiction, OnboardingSchema, Program, ProgramItem, RuleRow, SchemaField } from './onboardingConfig';
import { decodeSubdivisions, refdataName, referenceDataPaths } from '../referenceData/referenceData';
import '../clientReview/clientReview.css';
import '../dataQuery/dataQuery.css';
import './onboardingConfig.css';
import { schemaLabel } from './schemaLabel';

/** 只在 path 非空时读取；切换辖区或目录时取消旧请求。 */
function useLoad<T>(path: string | null, decode: (value: unknown) => T) {
  const [state, setState] = useState<{ data?: T; failure?: unknown; loading: boolean }>({ loading: path !== null });
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    if (path === null) { setState({ loading: false }); return; }
    const controller = new AbortController(); setState({ loading: true });
    session.request<T>(path, { signal: controller.signal }, decode)
      .then(data => { if (!controller.signal.aborted) setState({ data, loading: false }); })
      .catch(failure => { if (!controller.signal.aborted) setState({ failure, loading: false }); });
    return () => controller.abort();
  }, [path, decode, revision]);
  return { ...state, refresh: () => setRevision(v => v + 1) };
}
/** 配置页的后端文案键翻译，见 schemaLabel。 */
const labelText = schemaLabel;

/**
 * 入驻配置（只读）：左侧辖区包，右侧概要与规则开关、采集 Schema、目录、尽调程序；首期只经 DML 维护
 * <li>尽调程序来自 compliance，列出该辖区与全局程序的全部版本；只有程序与版本都 ACTIVE 的版本参与解析。</li>
 */
export function OnboardingConfigView() {
  const t = useT('client-review'), tNav = useT('nav'), tCommon = useT('common');
  const jurisdictions = useLoad(`${configApi}/jurisdictions`, decodeJurisdictions);
  const [selected, setSelected] = useState<string>();
  const current = jurisdictions.data?.find(j => j.jurisdictionCode === selected) ?? jurisdictions.data?.[0];
  return <div className="obw-page">
    <div className="obw-head"><h1 className="obw-grow">{tNav('resource.OPERATIONS_ONBOARDING_CONFIG')}</h1>
      <span className="cell-meta">{t('config.readOnly')}</span><Button onClick={jurisdictions.refresh} loading={jurisdictions.loading}>{tCommon('action.refresh')}</Button></div>
    {jurisdictions.loading && !jurisdictions.data ? <Loading /> : jurisdictions.failure || !jurisdictions.data
      ? <QueryFailure failure={jurisdictions.failure} retry={jurisdictions.refresh} />
      : jurisdictions.data.length === 0 ? <div className="obw-card"><Empty description={t('config.noJurisdictions')} /></div>
      : <div className="obc-split">
        <div className="obc-list">{jurisdictions.data.map(j => <button type="button" key={j.jurisdictionCode}
          className={`obc-item${j.jurisdictionCode === current?.jurisdictionCode ? ' is-selected' : ''}`} onClick={() => setSelected(j.jurisdictionCode)}>
          <span className="obc-code">{j.jurisdictionCode}</span>
          <span className="obw-grow"><JurisdictionName jurisdiction={j} /><span className="cell-meta">{enumLabel(t, 'config.enum.acceptancePolicy', j.acceptancePolicy)}</span></span>
          <StatusDot tone={tone(j.status)}>{enumLabel(t, 'config.enum.status', j.status)}</StatusDot></button>)}</div>
        {current && <JurisdictionDetail key={current.jurisdictionCode} jurisdiction={current} />}
      </div>}
  </div>;
}

function JurisdictionName({ jurisdiction }: { jurisdiction: Jurisdiction }) {
  const tSchema = useT('onboarding-schema');
  return <span className="obc-name">{labelText(tSchema, jurisdiction.labelKey, jurisdiction.countryCode)}</span>;
}

function JurisdictionDetail({ jurisdiction }: { jurisdiction: Jurisdiction }) {
  const t = useT('client-review');
  const [tab, setTab] = useState('schemas');
  const decodeSchemaList = useMemo(() => (value: unknown) => decodeSchemas(value, jurisdiction.jurisdictionCode), [jurisdiction.jurisdictionCode]);
  const schemas = useLoad(`${configApi}/onboarding-schemas?jurisdiction=${jurisdiction.jurisdictionCode}`, decodeSchemaList);
  const decodeProgramList = useMemo(() => (value: unknown) => decodePrograms(value, jurisdiction.jurisdictionCode), [jurisdiction.jurisdictionCode]);
  const programs = useLoad(tab === 'programs' ? `${configApi}/programs?jurisdiction=${jurisdiction.jurisdictionCode}` : null, decodeProgramList);
  return <div className="obw-main">
    <div className="obw-card"><div className="obw-card-title"><JurisdictionName jurisdiction={jurisdiction} /><span className="muted">{jurisdiction.jurisdictionCode}</span></div>
      <div className="obw-risk-current">
        <div><div className="obw-label">{t('config.field.country')}</div>{jurisdiction.countryCode}</div>
        <div><div className="obw-label">{t('config.field.acceptancePolicy')}</div>{enumLabel(t, 'config.enum.acceptancePolicy', jurisdiction.acceptancePolicy)}</div>
        <div><div className="obw-label">{t('config.field.addressFormat')}</div>{jurisdiction.addressFormatCode ?? '—'}</div>
        <div><div className="obw-label">{t('config.field.status')}</div><StatusDot tone={tone(jurisdiction.status)}>{enumLabel(t, 'config.enum.status', jurisdiction.status)}</StatusDot></div>
      </div>
      {jurisdiction.ruleSettings.length > 0 && <Table<RuleRow> size="small" rowKey="key" pagination={false} dataSource={ruleRows(t, jurisdiction.ruleSettings)}
        columns={[{ title: t('config.rule.code'), render: (_, r) => <>{r.rule}<div className="cell-meta qualification-mono">{r.ruleCode}</div></> },
          { title: t('config.rule.scope'), width: 280, render: (_, r) => r.scope },
          { title: t('config.rule.value'), width: 160, render: (_, r) => r.value }]} />}
    </div>
    <Segmented value={tab} onChange={value => setTab(String(value))} options={['schemas', 'catalogs', 'programs'].map(value => ({ value, label: t('config.tab.' + value) }))} />
    {tab === 'schemas' && (schemas.loading ? <Loading /> : schemas.failure || !schemas.data ? <QueryFailure failure={schemas.failure} retry={schemas.refresh} />
      : <SchemaBrowser schemas={schemas.data} />)}
    {tab === 'catalogs' && <CatalogBrowser catalogs={catalogUsages(schemas.data ?? [], jurisdiction)} jurisdiction={jurisdiction} />}
    {tab === 'programs' && (programs.loading ? <Loading /> : programs.failure || !programs.data ? <QueryFailure failure={programs.failure} retry={programs.refresh} />
      : <ProgramBrowser programs={programs.data} />)}
  </div>;
}

function SchemaBrowser({ schemas }: { schemas: OnboardingSchema[] }) {
  const t = useT('client-review'), tSchema = useT('onboarding-schema');
  // 默认打开启用中的版本；存量退役版本仍可在下拉中查看。
  const [selected, setSelected] = useState((schemas.find(s => s.status === 'ACTIVE') ?? schemas[0])?.schemaId);
  const schema = schemas.find(s => s.schemaId === selected) ?? schemas[0];
  if (!schema) return <div className="obw-card"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('config.noSchemas')} /></div>;
  return <div className="obw-card">
    <div className="obw-card-title"><Select style={{ minWidth: 380 }} value={schema.schemaId} onChange={setSelected} options={schemas.map(s => ({ value: s.schemaId,
      label: [enumLabel(t, 'config.enum.partyType', s.partyType), s.roleType && enumLabel(t, 'config.enum.roleType', s.roleType),
        s.organizationTypeCode ? tOr(tSchema, `organizationType.${s.jurisdictionCode}.${s.organizationTypeCode}`, s.organizationTypeCode) : t('config.defaultOrganizationType'),
        `v${s.versionNo}`, enumLabel(t, 'config.enum.status', s.status)].filter(Boolean).join(' · ') }))} />
      <span className="muted">{t('config.fieldCount', { count: schema.fields.length })}</span></div>
    {groupFields(schema.fields).map(section => <div key={section.section} className="obc-section">
      <h3>{tOr(tSchema, 'section.' + section.section, enumLabel(t, 'enum.section', section.section))}</h3>
      {section.groups.map(group => <div key={group.group}>{group.group && <div className="obw-label">{tOr(tSchema, 'group.' + group.group, group.group)}</div>}
        <Table<SchemaField> size="small" rowKey="fieldKey" pagination={false} dataSource={group.fields} scroll={{ x: 860 }} columns={[
          { title: t('config.field.field'), render: (_, f) => <div>{labelText(tSchema, f.labelKey, f.fieldKey)}<div className="cell-meta qualification-mono">{f.fieldKey}</div></div> },
          { title: t('config.field.widget'), width: 130, render: (_, f) => <Tag>{enumLabel(t, 'config.enum.widget', f.widget)}</Tag> },
          { title: t('config.field.requirement'), width: 170, render: (_, f) => <div>{enumLabel(t, 'config.enum.requirementLevel', f.requirementLevel)}
            {f.requirementGroup && <div className="cell-meta">{t('config.oneOf', { group: tOr(tSchema, 'requirementGroup.' + f.requirementGroup, f.requirementGroup) })}</div>}</div> },
          { title: t('config.field.catalog'), width: 160, render: (_, f) => f.catalogCode ? <span className="qualification-mono">{f.catalogCode}</span> : '—' },
          { title: t('config.field.conditions'), width: 220, render: (_, f) => [f.appliesTo, f.visibleWhen].filter(Boolean).join(' · ') || '—' },
        ]} /></div>)}
    </div>)}
  </div>;
}

/**
 * 目录（原型 入驻配置 · 目录来源）：左侧列出 Schema 引用的目录与来源，右侧为所选目录
 * <li>入驻目录：目录项（全局项与本辖区项）。</li>
 * <li>Platform 参考数据（REF_ 开头）：不在入驻目录中，说明来源、引用字段，并引导到参考数据页或币种页；行政区按辖区国家预览（无参考数据查看权限时不预览）。</li>
 */
function CatalogBrowser({ catalogs, jurisdiction }: { catalogs: CatalogUsage[]; jurisdiction: Jurisdiction }) {
  const t = useT('client-review');
  const [selected, setSelected] = useState(catalogs[0]?.catalogCode);
  if (catalogs.length === 0) return <div className="obw-card"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('config.noCatalogs')} /></div>;
  const current = catalogs.find(c => c.catalogCode === selected) ?? catalogs[0];
  return <div className="obc-catalogs">
    <div className="obw-card">
      <Table<CatalogUsage> size="small" rowKey="catalogCode" tableLayout="fixed" pagination={false} dataSource={catalogs} scroll={{ x: 760 }}
        rowClassName={row => `dq-clickable${row.catalogCode === current.catalogCode ? ' dq-selected-row' : ''}`}
        onRow={row => ({ onClick: () => setSelected(row.catalogCode) })} columns={[
          { title: t('config.catalog.name'), width: 160, render: (_, c) => <strong>{catalogName(t, c.catalogCode)}</strong> },
          { title: t('config.catalog.code'), width: 240, render: (_, c) => <span className="qualification-mono">{c.catalogCode}</span> },
          { title: t('config.catalog.source'), width: 180, render: (_, c) => <CatalogSourceTag usage={c} /> },
          { title: t('config.catalog.fields'), render: (_, c) => <span className="qualification-mono obc-field-list">{c.fieldKeys.join(', ')}</span> },
        ]} />
      <div className="cell-meta obc-catalog-foot">{t('config.catalog.footnote')}</div>
    </div>
    <div className="obw-card">{current.source === 'PLATFORM'
      ? <ReferenceCatalog key={current.catalogCode} usage={current} jurisdiction={jurisdiction} />
      : <CatalogItems key={current.catalogCode} catalog={current.catalogCode} jurisdictionCode={jurisdiction.jurisdictionCode} />}</div>
  </div>;
}

/** 目录显示名：`client-review.config.catalogName.<CODE>`，没有时显示编码。 */
function catalogName(t: Translate, catalogCode: string): string {
  return tOr(t, 'config.catalogName.' + catalogCode, catalogCode);
}

function CatalogSourceTag({ usage }: { usage: CatalogUsage }) {
  const t = useT('client-review');
  return usage.source === 'PLATFORM' ? <Tag color="green">{t('config.catalog.sourcePlatform')}</Tag> : <span className="cell-meta">{t('config.catalog.sourceOnboarding')}</span>;
}

function CatalogItems({ catalog, jurisdictionCode }: { catalog: string; jurisdictionCode: string }) {
  const t = useT('client-review'), tSchema = useT('onboarding-schema');
  const decode = useMemo(() => (value: unknown) => decodeCatalogItems(value, catalog), [catalog]);
  const result = useLoad(`${configApi}/catalog-items?catalogCode=${encodeURIComponent(catalog)}`, decode);
  const rows = (result.data ?? []).filter(item => item.jurisdictionCode === null || item.jurisdictionCode === jurisdictionCode);
  return <><div className="obw-card-title">{catalogName(t, catalog)}<span className="muted">{t('config.catalogScope', { jurisdiction: jurisdictionCode })}</span></div>
    {result.failure ? <QueryFailure failure={result.failure} retry={result.refresh} /> :
      <Table<CatalogItem> size="small" rowKey="itemCode" tableLayout="fixed" loading={result.loading} pagination={false} dataSource={rows} scroll={{ x: 640 }} columns={[
        { title: t('config.catalog.item'), render: (_, i) => <div>{labelText(tSchema, i.labelKey, i.itemCode)}<div className="cell-meta qualification-mono">{i.itemCode}</div></div> },
        { title: t('config.catalog.scope'), width: 120, render: (_, i) => i.jurisdictionCode ?? t('config.global') },
        { title: t('config.catalog.clientTypes'), width: 180, render: (_, i) => i.clientTypes ?? t('config.allClientTypes') },
        { title: t('config.field.status'), width: 110, render: (_, i) => <StatusDot tone={tone(i.status)}>{enumLabel(t, 'config.enum.status', i.status)}</StatusDot> },
      ]} />}</>;
}

function ReferenceCatalog({ usage, jurisdiction }: { usage: CatalogUsage; jurisdiction: Jurisdiction }) {
  const t = useT('client-review'), tRef = useT('refdata'); const navigate = useNavigate();
  const href = referenceCatalogHref(usage.catalogCode, jurisdiction.countryCode);
  const subdivisions = usage.catalogCode === 'REF_COUNTRY_SUBDIVISION';
  const decode = useMemo(() => (value: unknown) => decodeSubdivisions(value, jurisdiction.countryCode), [jurisdiction.countryCode]);
  const preview = useQuiet(subdivisions ? `${referenceDataPaths.subdivisions}?countryCode=${jurisdiction.countryCode}&status=ACTIVE` : null, decode);
  return <>
    <div className="obw-card-title">{catalogName(t, usage.catalogCode)}<span className="obw-grow" /><Tag color="green">{t('config.catalog.sourcePlatform')}</Tag></div>
    <div className="cell-meta qualification-mono">{usage.catalogCode}</div>
    <div className="obc-reference-note"><DatabaseOutlined /> <span>{tOr(t, 'config.catalog.platformNote.' + usage.catalogCode, t('config.catalog.platformNoteDefault'))}</span></div>
    <div className="obw-label">{t('config.catalog.fields')}</div>
    {usage.fieldKeys.map(field => <div key={field} className="qualification-mono cell-meta">{field}</div>)}
    {subdivisions && preview && preview.length > 0 && <>
      <div className="obw-label" style={{ marginTop: 12 }}>{t('config.catalog.preview', { country: jurisdiction.countryCode, count: preview.length })}</div>
      {preview.slice(0, 6).map(s => <div key={s.subdivisionCode} className="obc-kv"><strong>{s.subdivisionCode}</strong><span>{refdataName(tRef, 'subdivision', s.subdivisionCode, s.name)}</span></div>)}
    </>}
    {href && <Button type="link" className="obc-reference-link" onClick={() => void navigate(href)}>{usage.catalogCode === 'REF_CURRENCY'
      ? t('config.catalog.openCurrencies') : t('config.catalog.openReferenceData')}</Button>}
  </>;
}

/** 只在 path 非空时读取；失败（例如没有参考数据查看权限）不提示、不展示。 */
function useQuiet<T>(path: string | null, decode: (value: unknown) => T): T | undefined {
  const [data, setData] = useState<T>();
  useEffect(() => {
    if (path === null) return;
    const controller = new AbortController();
    session.request<T>(path, { signal: controller.signal }, decode).then(value => { if (!controller.signal.aborted) setData(value); }).catch(() => undefined);
    return () => controller.abort();
  }, [path, decode]);
  return data;
}

/** 程序项附加事实的展示：文件类型、时效、模式、阻断、监控。 */
function itemDetail(t: Translate, item: ProgramItem): string {
  return item.detail.map(d => d.startsWith('maxAge:') ? t('config.program.maxAge', { days: Number(d.slice(7)) })
    : d.startsWith('validity:') ? t('config.program.validity', { days: Number(d.slice(9)) })
    : d.startsWith('mode:') ? enumLabel(t, 'enum.checkMode', d.slice(5))
    : d === 'blocking' ? t('checks.blocking') : d === 'waivable' ? t('config.program.waivable') : d === 'monitoring' ? t('config.program.monitoring')
    : enumLabel(t, 'enum.documentType', d)).join(' · ') || '—';
}

/** 尽调程序：每个版本一张卡片，生效版本在前；材料、检查、筛查合并为一张表。 */
function ProgramBrowser({ programs }: { programs: Program[] }) {
  const t = useT('client-review');
  if (programs.length === 0) return <div className="obw-card"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('config.noPrograms')} /></div>;
  const ordered = [...programs].sort((a, b) => Number(programInUse(b)) - Number(programInUse(a)));
  return <>{ordered.map(program => <div key={program.programVersionId} className="obw-card">
    <div className="obw-card-title"><span className="qualification-mono">{program.programCode}</span><span className="muted">v{program.versionNo}</span>
      <StatusDot tone={programInUse(program) ? 'ok' : 'muted'}>{programInUse(program) ? t('config.program.inUse')
        : `${enumLabel(t, 'config.enum.status', program.programStatus)} / ${enumLabel(t, 'config.enum.status', program.versionStatus)}`}</StatusDot></div>
    <div className="obw-meta">{[program.jurisdictionCode ?? t('config.global'), program.partyType && enumLabel(t, 'config.enum.partyType', program.partyType),
      program.roleType && enumLabel(t, 'config.enum.roleType', program.roleType), program.qualificationType && enumLabel(t, 'enum.qualification', program.qualificationType),
      program.tier && enumLabel(t, 'enum.tier', program.tier), program.fourEyesRequired ? t('config.fourEyes') : t('config.singleDecision'),
      program.reviewIntervalMonths !== null && t('config.reviewInterval', { months: program.reviewIntervalMonths }),
      program.ownerPartnerId ? t('config.ownedBy', { partner: program.ownerPartnerId }) : t('config.platformProgram')].filter(Boolean).join(' · ')}</div>
    <Table<ProgramItem> size="small" rowKey={i => `${i.kind}:${i.code}:${i.subjectRole}`} pagination={false} dataSource={program.items} style={{ marginTop: 10 }}
      locale={{ emptyText: t('config.program.empty') }} columns={[
        { title: t('config.program.kind'), width: 110, render: (_, i) => enumLabel(t, 'config.enum.programItem', i.kind) },
        { title: t('config.program.item'), render: (_, i) => <div>{enumLabel(t, i.kind === 'MATERIAL' ? 'enum.requirement' : i.kind === 'CHECK' ? 'enum.check' : 'enum.screeningType', i.code)}
          <div className="cell-meta qualification-mono">{i.code}</div></div> },
        { title: t('config.program.subject'), width: 170, render: (_, i) => i.subjectRole ? enumLabel(t, 'enum.subjectRole', i.subjectRole) : '—' },
        { title: t('config.program.detail'), width: 260, render: (_, i) => itemDetail(t, i) },
        { title: t('config.program.required'), width: 100, render: (_, i) => i.required ? t('materials.required') : t('config.optional') },
      ]} /></div>)}</>;
}
