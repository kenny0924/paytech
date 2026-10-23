import { useEffect, useState } from 'react';
import { Alert, Button, Descriptions, Input, Progress, Segmented, Select, Spin, Table, Tag } from 'antd';
import type { ConsoleResource } from '../api/types';
import { session } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import { Note, useResource } from '../dataQuery/DataQueryParts';
import { hasButton, localTime } from '../dataQuery/dataQueryCommon';
import {
  binInputIssue, binLibraryManageButton, binLibraryPaths, decodeActivationPage, decodeImportErrorPage, decodeLookup,
  decodeVersion, decodeVersionPage, formatCount, isQueryableBin, versionAction, versionProgress, versionStatusColors,
  versionStatuses,
} from '../card/binLibrary';
import type { BinImportError, BinLibraryVersion, BinLookup, VersionStatus } from '../card/binLibrary';
import { ActivationModal, ImportModal } from '../card/BinLibraryModals';
import '../dataQuery/dataQuery.css';
import '../card/binLibrary.css';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
type Step = { kind: 'import' } | { kind: 'activate'; version: BinLibraryVersion } | null;

const firstPage = (status?: VersionStatus) => `${binLibraryPaths.versions}?${status ? `status=${status}&` : ''}page=1&size=10`;
const pollMillis = 3000;

interface SummaryPages { active?: { items: BinLibraryVersion[]; total: number }; latest?: { items: BinLibraryVersion[]; total: number };
  pending?: { items: BinLibraryVersion[]; total: number } }

/**
 * 摘要所需的三个版本列表逐个请求：版本列表路由按登录账号限并发 2，与左侧分页列表同时最多 2 个请求，
 * 并发发出会被 Gateway 以 429 拒绝（阶段 5 缺陷 S5-01）。
 */
function useSummaryPages(revision: number) {
  const t = useT('card');
  const notify = useErrorNotification();
  const [pages, setPages] = useState<SummaryPages>({}); const [loading, setLoading] = useState(true);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true);
    const load = async () => {
      const next: SummaryPages = {};
      for (const [key, path] of [['active', firstPage('ACTIVE')], ['latest', firstPage()], ['pending', firstPage('VALID')]] as const) {
        try {
          next[key] = await session.request(path, { signal: controller.signal }, decodeVersionPage);
        } catch (error) {
          if (controller.signal.aborted) return;
          notify(error, t('summary.loadFailed'));
        }
      }
      if (!controller.signal.aborted) { setPages(next); setLoading(false); }
    };
    void load();
    return () => controller.abort();
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [revision, notify]);
  return { pages, loading };
}

/** 卡 BIN 库（原型 01—05）：摘要、BIN 查询、版本列表与右侧选中版本详情；有维护按钮时可导入、激活与回切。 */
export function CardBinLibrary({ resources, onAuthorizationChanged }: Props) {
  const t = useT('card'), tNav = useT('nav');
  const canManage = hasButton(resources, binLibraryManageButton);
  const [revision, setRevision] = useState(0);
  const [status, setStatus] = useState<VersionStatus | 'ALL'>('ALL');
  const page = usePaging<BinLibraryVersion>(binLibraryPaths.versions, decodeVersionPage, {}, { notifyOnError: false });
  const [summaryRevision, setSummaryRevision] = useState(0);
  const summary = useSummaryPages(summaryRevision);
  const [selectedNo, setSelectedNo] = useState<number | null>(null);
  const [step, setStep] = useState<Step>(null);
  const activeVersion = summary.pages.active?.items[0] ?? null;
  const items = page.data.items;
  // 列表与生效版本都加载完再自动选中，避免先选中最新版本或沿用切换筛选前的旧列表（阶段 5 缺陷 S5-02）。
  useEffect(() => {
    // 切换筛选后、新列表返回前，items 仍是旧列表：只从符合当前筛选的版本里选。
    const candidates = status === 'ALL' ? items : items.filter(item => item.status === status);
    if (selectedNo !== null || !candidates.length || page.loading || summary.loading) return;
    setSelectedNo(activeVersion && candidates.some(item => item.versionNo === activeVersion.versionNo)
      ? activeVersion.versionNo : candidates[0].versionNo);
  }, [items, activeVersion, selectedNo, status, page.loading, summary.loading]);
  const reload = (versionNo?: number) => {
    setStep(null);
    if (versionNo !== undefined) setSelectedNo(versionNo);
    page.refresh(); setSummaryRevision(v => v + 1); setRevision(v => v + 1);
  };
  const onFilter = (value: VersionStatus | 'ALL') => {
    setStatus(value); setSelectedNo(null);
    page.applyFilters(value === 'ALL' ? {} : { status: value });
  };
  return <div className="configuration-page dq-page bin-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_CARD_BIN_LIBRARY')}</h1>
      {canManage && <Button type="primary" onClick={() => setStep({ kind: 'import' })}>{t('page.import')}</Button>}</div>
    <p className="muted dq-intro">{t('page.intro')}</p>
    <Summary key={revision} active={activeVersion} pages={summary.pages} loading={summary.loading} />
    <BinLookupCard versions={items} />
    <div className="dq-master-detail">
      <section className="dq-panel">
        <div className="bin-table-head"><strong>{t('versions.title')}</strong>
          <Segmented<VersionStatus | 'ALL'> value={status} onChange={onFilter}
            options={[{ value: 'ALL', label: t('versions.all') }, ...versionStatuses.map(value => ({ value, label: enumLabel(t, 'enum.versionStatus', value) }))]} /></div>
        {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
          : <Table<BinLibraryVersion> rowKey="versionNo" size="middle" loading={page.loading} dataSource={items} pagination={page.pagination}
            tableLayout="fixed" scroll={{ x: 860 }} locale={{ emptyText: status === 'ALL' ? t('versions.empty') : t('versions.emptyStatus') }}
            rowClassName={row => `dq-clickable${row.versionNo === selectedNo ? ' dq-selected-row' : ''}`}
            onRow={row => ({ onClick: () => setSelectedNo(row.versionNo) })} columns={[
              { title: t('versions.column.version'), width: 70, render: (_, row) => <strong>v{row.versionNo}</strong> },
              { title: t('versions.column.status'), width: 90, render: (_, row) => <StatusTag status={row.status} /> },
              { title: t('versions.column.fileName'), width: 200, ellipsis: true, render: (_, row) => row.fileName ?? <span className="muted">{t('versions.seedData')}</span> },
              { title: t('versions.column.ranges'), width: 100, align: 'right', render: (_, row) => row.status === 'VALIDATING'
                ? `${formatCount(row.processedRows)} / ${formatCount(row.totalRows)}` : row.status === 'INVALID' ? '—' : formatCount(row.totalRows) },
              { title: t('versions.column.errorRows'), width: 70, align: 'right', render: (_, row) => <span className={row.errorRows ? 'dq-text-error' : undefined}>{formatCount(row.errorRows)}</span> },
              { title: t('versions.column.importedBy'), width: 110, render: (_, row) => <span className="mono">{row.createdBy ?? 'system'}</span> },
              { title: t('versions.column.createdAt'), width: 140, render: (_, row) => localTime(row.createTime) },
              { title: t('versions.column.actions'), width: 150, render: (_, row) => {
                const action = canManage ? versionAction(row) : null;
                return <span className="bin-row-actions"><a onClick={() => setSelectedNo(row.versionNo)}>{t('versions.action.view')}</a>
                  {action && <Button size="small" type={action.kind === 'ACTIVATE' ? 'primary' : 'default'}
                    onClick={event => { event.stopPropagation(); setStep({ kind: 'activate', version: row }); }}>{action.verb}</Button>}</span>;
              } },
            ]} />}
        <p className="muted bin-footnote">{t('versions.footnote')}</p>
      </section>
      <section className="dq-panel dq-detail">
        {selectedNo !== null ? <VersionDetail key={`${selectedNo}:${revision}`} versionNo={selectedNo} canManage={canManage}
          onActivate={version => setStep({ kind: 'activate', version })} onSettled={() => { page.refresh(); setSummaryRevision(v => v + 1); }} />
          : <Note>{page.loading ? t('versions.loading') : t('versions.selectHint')}</Note>}
        <ActivationHistory key={revision} />
      </section>
    </div>
    {step?.kind === 'import' && <ImportModal onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={version => reload(version.versionNo)} />}
    {step?.kind === 'activate' && <ActivationModal target={step.version} active={activeVersion} onClose={() => setStep(null)}
      onDone={() => reload(step.version.versionNo)} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function StatusTag({ status }: { status: VersionStatus }) {
  const t = useT('card');
  return <Tag color={versionStatusColors[status]}>{enumLabel(t, 'enum.versionStatus', status)}</Tag>;
}

/** 顶部摘要：当前生效版本与激活信息、生效号段数、最近导入、待激活版本数。 */
function Summary({ active, pages, loading }: { active: BinLibraryVersion | null; pages: SummaryPages; loading: boolean }) {
  const t = useT('card');
  const activeLoading = loading;
  const history = useResource(`${binLibraryPaths.activations}?page=1&size=10`, decodeActivationPage, t('history.loadFailed'));
  const lastActivation = history.data?.items.find(item => item.versionNo === active?.versionNo) ?? null;
  const newest = pages.latest?.items[0] ?? null;
  if (!loading && !active && !newest) {
    return <Alert type="info" showIcon className="dq-banner" title={t('summary.noneActive')} />;
  }
  return <div className="bin-summary">
    <div className="bin-summary-card"><span className="muted">{t('summary.activeVersion')}</span>
      {active ? <><div className="bin-summary-value">v{active.versionNo} <StatusTag status="ACTIVE" /></div>
        <span className="muted">{lastActivation ? t('summary.activatedBy', { time: localTime(lastActivation.activatedAt), operator: lastActivation.operatorAccountId ?? 'system' }) : ''}
          {active.fileName ? t('summary.fileSuffix', { fileName: active.fileName }) : ''}</span></>
        : <div className="bin-summary-value muted">{activeLoading ? '…' : t('version.none')}</div>}</div>
    <div className="bin-summary-card"><span className="muted">{t('summary.activeRanges')}</span>
      <div className="bin-summary-value">{formatCount(active?.totalRows)}</div></div>
    <div className="bin-summary-card"><span className="muted">{t('summary.latestImport')}</span>
      {newest ? <><div className="bin-summary-value">v{newest.versionNo} <StatusTag status={newest.status} /></div>
        <span className="muted">{newest.status === 'INVALID'
          ? t('summary.importedByInvalid', { time: localTime(newest.createTime), createdBy: newest.createdBy ?? 'system', errors: formatCount(newest.errorRows) })
          : t('summary.importedBy', { time: localTime(newest.createTime), createdBy: newest.createdBy ?? 'system' })}</span></>
        : <div className="bin-summary-value muted">—</div>}</div>
    <div className="bin-summary-card"><span className="muted">{t('summary.pending')}</span>
      <div className="bin-summary-value">{pages.pending ? t('summary.pendingCount', { count: pages.pending.total }) : '—'}</div></div>
  </div>;
}

/** 单个 BIN 查询：6～8 位数字，非数字与超过 8 位即时提示；可选查询版本（默认当前生效）。 */
function BinLookupCard({ versions }: { versions: readonly BinLibraryVersion[] }) {
  const t = useT('card');
  const notify = useErrorNotification();
  const [bin, setBin] = useState('');
  const [versionNo, setVersionNo] = useState<number | null>(null);
  const [result, setResult] = useState<BinLookup | null>(null);
  const [loading, setLoading] = useState(false);
  const issue = binInputIssue(bin);
  const options = [{ value: 0, label: t('activation.currentActive') }, ...versions.filter(item => item.status !== 'VALIDATING' && item.status !== 'INVALID')
    .map(item => ({ value: item.versionNo, label: t('lookup.versionOption', { versionNo: item.versionNo, status: enumLabel(t, 'enum.versionStatus', item.status) }) }))];
  async function query() {
    if (!isQueryableBin(bin) || loading) return;
    setLoading(true);
    try { setResult(await session.request(binLibraryPaths.lookup(bin.trim(), versionNo), {}, decodeLookup)); }
    catch (error) { setResult(null); notify(error, t('lookup.failed')); }
    finally { setLoading(false); }
  }
  return <section className="dq-panel bin-lookup">
    <div className="bin-lookup-form"><strong>{t('lookup.title')}</strong>
      <Input value={bin} maxLength={19} placeholder={t('lookup.placeholder')} status={issue ? 'error' : undefined} className="bin-lookup-input mono"
        onChange={event => setBin(event.target.value)} onPressEnter={() => void query()} />
      <Select value={versionNo ?? 0} options={options} className="bin-lookup-version" onChange={value => setVersionNo(value === 0 ? null : value)} />
      <Button type="primary" disabled={!isQueryableBin(bin)} loading={loading} onClick={() => void query()}>{t('lookup.search')}</Button>
      <span className={issue ? 'dq-text-error' : 'muted'}>{issue ?? t('lookup.hint')}</span>
    </div>
    {result && (result.recognized ? <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 5 }} className="bin-lookup-result" items={[
      { key: 'prefix', label: t('lookup.matchedPrefix'), children: <span className="mono">{t('lookup.prefixValue', { prefix: result.binPrefix ?? '', length: result.matchedPrefixLength ?? '' })}</span> },
      { key: 'brand', label: t('lookup.cardBrand'), children: result.cardBrand ?? '—' },
      { key: 'funding', label: t('lookup.fundingType'), children: result.fundingType ?? '—' },
      { key: 'level', label: t('lookup.cardLevel'), children: result.cardLevel ?? '—' },
      { key: 'product', label: t('lookup.cardProduct'), children: result.cardProduct ?? '—' },
      { key: 'country', label: t('lookup.issuerCountry'), children: result.issuerCountry ?? '—' },
      { key: 'region', label: t('lookup.issuerRegion'), children: result.issuerRegion ?? '—' },
      { key: 'issuer', label: t('lookup.issuerName'), children: result.issuerName ?? '—' },
      { key: 'version', label: t('versions.column.version'), children: result.binLibraryVersionNo ? `v${result.binLibraryVersionNo}` : '—' },
    ]} /> : <p className="muted bin-unrecognized">{result.binLibraryVersionNo ? t('lookup.unrecognizedInVersion', { versionNo: result.binLibraryVersionNo }) : t('lookup.unrecognizedNoActive')}</p>)}
  </section>;
}

/**
 * 右侧版本详情：基本信息 + 状态区
 * <li>校验中每 3 秒轮询（离开或切换版本即停止），结束后通知页面刷新列表与摘要；校验失败显示原因与错误明细。</li>
 */
function VersionDetail({ versionNo, canManage, onActivate, onSettled }: {
  versionNo: number; canManage: boolean; onActivate: (version: BinLibraryVersion) => void; onSettled: () => void;
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
  if (detail.loading && !version) return <Loading />;
  if (detail.failure || !version) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const action = canManage ? versionAction(version) : null;
  return <>
    <div className="dq-detail-head"><div><h2>{t('detail.title', { versionNo: version.versionNo })}</h2>
      <div className="muted">{version.fileName ?? t('versions.seedData')}</div></div><StatusTag status={version.status} /></div>
    {version.status === 'VALIDATING' && <ValidatingState version={version} />}
    {version.status === 'INVALID' && <InvalidState version={version} />}
    <Descriptions size="small" column={1} items={[
      { key: 'file', label: t('detail.file'), children: version.fileName ? <span className="mono">{version.fileName}</span> : '—' },
      { key: 'hash', label: t('detail.hash'), children: version.fileContentHash
        ? <span className="mono" title={version.fileContentHash}>SHA-256 {version.fileContentHash.slice(0, 4)}…{version.fileContentHash.slice(-4)}</span> : '—' },
      { key: 'rows', label: t('versions.column.ranges'), children: t('detail.rowsValue', { total: formatCount(version.totalRows), errors: formatCount(version.errorRows) }) },
      { key: 'creator', label: t('versions.column.importedBy'), children: <span><span className="mono">{version.createdBy ?? 'system'}</span> · {localTime(version.createTime)}</span> },
      { key: 'updated', label: t('detail.updatedAt'), children: localTime(version.updateTime) },
    ]} />
    {version.status === 'ACTIVE' && <Note>{t('detail.activeNote')}</Note>}
    {canManage && version.status === 'VALIDATING' && <div className="dq-detail-actions"><Button disabled>{t('enum.activationType.ACTIVATE')}</Button>
      <span className="muted">{t('detail.activateAfterValidation')}</span></div>}
    {action && <div className="dq-detail-actions"><Button type={action.kind === 'ACTIVATE' ? 'primary' : 'default'} onClick={() => onActivate(version)}>
      {action.verb}</Button></div>}
  </>;
}

function ValidatingState({ version }: { version: BinLibraryVersion }) {
  const t = useT('card');
  const progress = versionProgress(version);
  return <div className="bin-progress">
    {progress.percent === null ? <div className="bin-progress-pending"><Spin size="small" /> <span>{progress.label}</span></div>
      : <><span>{progress.label}</span><Progress percent={progress.percent} status="active" /></>}
    <Note>{t('detail.validatingNote')}</Note>
  </div>;
}

/** 校验失败：文件级原因横幅与错误明细分页（只记录前 200 条）。 */
function InvalidState({ version }: { version: BinLibraryVersion }) {
  const t = useT('card');
  const errors = usePaging<BinImportError>(binLibraryPaths.errors(version.versionNo), decodeImportErrorPage, {}, { enabled: version.errorRows > 0 });
  return <>
    <Alert type="error" showIcon className="dq-banner"
      title={version.failureReason ?? t('invalid.title', { errors: formatCount(version.errorRows) })}
      description={t('invalid.hint')} />
    {version.errorRows > 0 && <>
      {errors.failure ? <QueryFailure failure={errors.failure} retry={errors.refresh} />
        : <Table<BinImportError> rowKey={row => `${row.lineNo}:${row.columnName ?? ''}:${row.errorCode}`} size="small" loading={errors.loading}
          dataSource={errors.data.items} pagination={{ ...errors.pagination, size: 'small' }} tableLayout="fixed" columns={[
            { title: t('invalid.column.line'), width: 70, render: (_, row) => formatCount(row.lineNo) },
            { title: t('invalid.column.column'), width: 110, render: (_, row) => <span className="mono">{row.columnName ?? t('invalid.wholeRow')}</span> },
            { title: t('invalid.column.error'), width: 150, render: (_, row) => <span className="mono">{row.errorCode}</span> },
            { title: t('invalid.column.message'), render: (_, row) => <span>{row.errorMessage}
              {row.valueExcerpt !== null && <span className="muted mono">{t('invalid.excerpt', { value: row.valueExcerpt })}</span>}</span> },
          ]} />}
      <p className="muted">{t('invalid.note', { errors: formatCount(version.errorRows) })}</p>
    </>}
  </>;
}

/** 激活历史：时间、类型、版本、替换的版本与操作人，按时间倒序分页。 */
function ActivationHistory() {
  const t = useT('card');
  const history = usePaging(binLibraryPaths.activations, decodeActivationPage, {}, { notifyOnError: false });
  return <>
    <h3 className="dq-section-title">{t('history.title')}</h3>
    {history.failure ? <QueryFailure failure={history.failure} retry={history.refresh} />
      : <Table rowKey={row => `${row.activatedAt}:${row.versionNo}`} size="small" loading={history.loading} dataSource={history.data.items}
        pagination={{ ...history.pagination, size: 'small' }} locale={{ emptyText: t('history.empty') }} tableLayout="fixed" columns={[
          { title: t('history.column.time'), width: 130, render: (_, row) => localTime(row.activatedAt) },
          { title: t('history.column.type'), width: 90, render: (_, row) => enumLabel(t, 'enum.activationType', row.activationType) },
          { title: t('versions.column.version'), render: (_, row) => row.previousVersionNo === null ? `v${row.versionNo}`
            : row.activationType === 'ROLLBACK' ? `v${row.versionNo} ← v${row.previousVersionNo}` : `v${row.previousVersionNo} → v${row.versionNo}` },
          { title: t('history.column.operator'), width: 100, render: (_, row) => <span className="mono">{row.operatorAccountId ?? 'system'}</span> },
        ]} />}
  </>;
}
