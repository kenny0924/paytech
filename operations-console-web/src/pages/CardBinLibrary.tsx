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
  activationTypeLabels, binInputIssue, binLibraryManageButton, binLibraryPaths, decodeActivationPage, decodeImportErrorPage, decodeLookup,
  decodeVersion, decodeVersionPage, formatCount, isQueryableBin, versionAction, versionProgress, versionStatusColors, versionStatusLabels,
  versionStatuses,
} from '../card/binLibrary';
import type { BinImportError, BinLibraryVersion, BinLookup, VersionStatus } from '../card/binLibrary';
import { ActivationModal, ImportModal } from '../card/BinLibraryModals';
import '../dataQuery/dataQuery.css';
import '../card/binLibrary.css';

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
          notify(error, '版本摘要加载失败');
        }
      }
      if (!controller.signal.aborted) { setPages(next); setLoading(false); }
    };
    void load();
    return () => controller.abort();
  }, [revision, notify]);
  return { pages, loading };
}

/** 卡 BIN 库（原型 01—05）：摘要、BIN 查询、版本列表与右侧选中版本详情；有维护按钮时可导入、激活与回切。 */
export function CardBinLibrary({ resources, onAuthorizationChanged }: Props) {
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
    <div className="page-heading"><h1>卡 BIN 库</h1>
      {canManage && <Button type="primary" onClick={() => setStep({ kind: 'import' })}>＋ 导入新版本</Button>}</div>
    <p className="muted dq-intro">按卡 BIN 识别卡组织、借贷记、发卡国等属性。新版本导入并校验通过后由人工激活；每笔交易在创建支付工具时冻结当时的卡属性，回切不改已有交易。</p>
    <Summary key={revision} active={activeVersion} pages={summary.pages} loading={summary.loading} />
    <BinLookupCard versions={items} />
    <div className="dq-master-detail">
      <section className="dq-panel">
        <div className="bin-table-head"><strong>版本列表</strong>
          <Segmented<VersionStatus | 'ALL'> value={status} onChange={onFilter}
            options={[{ value: 'ALL', label: '全部' }, ...versionStatuses.map(value => ({ value, label: versionStatusLabels[value] }))]} /></div>
        {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} />
          : <Table<BinLibraryVersion> rowKey="versionNo" size="middle" loading={page.loading} dataSource={items} pagination={page.pagination}
            tableLayout="fixed" scroll={{ x: 860 }} locale={{ emptyText: status === 'ALL' ? '还没有导入过版本' : '没有该状态的版本' }}
            rowClassName={row => `dq-clickable${row.versionNo === selectedNo ? ' dq-selected-row' : ''}`}
            onRow={row => ({ onClick: () => setSelectedNo(row.versionNo) })} columns={[
              { title: '版本', width: 70, render: (_, row) => <strong>v{row.versionNo}</strong> },
              { title: '状态', width: 90, render: (_, row) => <StatusTag status={row.status} /> },
              { title: '文件名', width: 200, ellipsis: true, render: (_, row) => row.fileName ?? <span className="muted">种子数据（初始化）</span> },
              { title: '号段数', width: 100, align: 'right', render: (_, row) => row.status === 'VALIDATING'
                ? `${formatCount(row.processedRows)} / ${formatCount(row.totalRows)}` : row.status === 'INVALID' ? '—' : formatCount(row.totalRows) },
              { title: '错误行', width: 70, align: 'right', render: (_, row) => <span className={row.errorRows ? 'dq-text-error' : undefined}>{formatCount(row.errorRows)}</span> },
              { title: '导入人', width: 110, render: (_, row) => <span className="mono">{row.createdBy ?? 'system'}</span> },
              { title: '创建时间', width: 140, render: (_, row) => localTime(row.createTime) },
              { title: '操作', width: 110, render: (_, row) => {
                const action = canManage ? versionAction(row) : null;
                return <span className="bin-row-actions"><a onClick={() => setSelectedNo(row.versionNo)}>查看</a>
                  {action && <Button size="small" type={action.kind === 'ACTIVATE' ? 'primary' : 'default'}
                    onClick={event => { event.stopPropagation(); setStep({ kind: 'activate', version: row }); }}>{action.verb}</Button>}</span>;
              } },
            ]} />}
        <p className="muted bin-footnote">历史版本全部保留（本期不提供删除）。</p>
      </section>
      <section className="dq-panel dq-detail">
        {selectedNo !== null ? <VersionDetail key={`${selectedNo}:${revision}`} versionNo={selectedNo} canManage={canManage}
          onActivate={version => setStep({ kind: 'activate', version })} onSettled={() => { page.refresh(); setSummaryRevision(v => v + 1); }} />
          : <Note>{page.loading ? '加载中…' : '选择左侧版本查看详情。'}</Note>}
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
  return <Tag color={versionStatusColors[status]}>{versionStatusLabels[status]}</Tag>;
}

/** 顶部摘要：当前生效版本与激活信息、生效号段数、最近导入、待激活版本数。 */
function Summary({ active, pages, loading }: { active: BinLibraryVersion | null; pages: SummaryPages; loading: boolean }) {
  const activeLoading = loading;
  const history = useResource(`${binLibraryPaths.activations}?page=1&size=10`, decodeActivationPage, '激活历史加载失败');
  const lastActivation = history.data?.items.find(item => item.versionNo === active?.versionNo) ?? null;
  const newest = pages.latest?.items[0] ?? null;
  if (!loading && !active && !newest) {
    return <Alert type="info" showIcon className="dq-banner" title="尚未激活任何版本，所有卡都为未识别。" />;
  }
  return <div className="bin-summary">
    <div className="bin-summary-card"><span className="muted">当前生效版本</span>
      {active ? <><div className="bin-summary-value">v{active.versionNo} <StatusTag status="ACTIVE" /></div>
        <span className="muted">{lastActivation ? `${localTime(lastActivation.activatedAt)} 由 ${lastActivation.operatorAccountId ?? 'system'} 激活` : ''}
          {active.fileName ? ` · 文件 ${active.fileName}` : ''}</span></>
        : <div className="bin-summary-value muted">{activeLoading ? '…' : '无'}</div>}</div>
    <div className="bin-summary-card"><span className="muted">生效号段数</span>
      <div className="bin-summary-value">{formatCount(active?.totalRows)}</div></div>
    <div className="bin-summary-card"><span className="muted">最近导入</span>
      {newest ? <><div className="bin-summary-value">v{newest.versionNo} <StatusTag status={newest.status} /></div>
        <span className="muted">{localTime(newest.createTime)} 由 {newest.createdBy ?? 'system'} 导入
          {newest.status === 'INVALID' ? ` · 校验失败 ${formatCount(newest.errorRows)} 行` : ''}</span></>
        : <div className="bin-summary-value muted">—</div>}</div>
    <div className="bin-summary-card"><span className="muted">待处理版本</span>
      <div className="bin-summary-value">{pages.pending ? `${pages.pending.total} 个可激活` : '—'}</div></div>
  </div>;
}

/** 单个 BIN 查询：6～8 位数字，非数字与超过 8 位即时提示；可选查询版本（默认当前生效）。 */
function BinLookupCard({ versions }: { versions: readonly BinLibraryVersion[] }) {
  const notify = useErrorNotification();
  const [bin, setBin] = useState('');
  const [versionNo, setVersionNo] = useState<number | null>(null);
  const [result, setResult] = useState<BinLookup | null>(null);
  const [loading, setLoading] = useState(false);
  const issue = binInputIssue(bin);
  const options = [{ value: 0, label: '当前生效' }, ...versions.filter(item => item.status !== 'VALIDATING' && item.status !== 'INVALID')
    .map(item => ({ value: item.versionNo, label: `v${item.versionNo} · ${versionStatusLabels[item.status]}` }))];
  async function query() {
    if (!isQueryableBin(bin) || loading) return;
    setLoading(true);
    try { setResult(await session.request(binLibraryPaths.lookup(bin.trim(), versionNo), {}, decodeLookup)); }
    catch (error) { setResult(null); notify(error, 'BIN 查询失败'); }
    finally { setLoading(false); }
  }
  return <section className="dq-panel bin-lookup">
    <div className="bin-lookup-form"><strong>BIN 查询</strong>
      <Input value={bin} maxLength={19} placeholder="6～8 位数字" status={issue ? 'error' : undefined} className="bin-lookup-input mono"
        onChange={event => setBin(event.target.value)} onPressEnter={() => void query()} />
      <Select value={versionNo ?? 0} options={options} className="bin-lookup-version" onChange={value => setVersionNo(value === 0 ? null : value)} />
      <Button type="primary" disabled={!isQueryableBin(bin)} loading={loading} onClick={() => void query()}>查询</Button>
      <span className={issue ? 'dq-text-error' : 'muted'}>{issue ?? '输入 6～8 位数字；只按 BIN 查询，不接受完整卡号'}</span>
    </div>
    {result && (result.recognized ? <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 5 }} className="bin-lookup-result" items={[
      { key: 'prefix', label: '命中前缀', children: <span className="mono">{result.binPrefix}（{result.matchedPrefixLength} 位）</span> },
      { key: 'brand', label: '卡组织', children: result.cardBrand ?? '—' },
      { key: 'funding', label: '借贷记', children: result.fundingType ?? '—' },
      { key: 'level', label: '卡等级', children: result.cardLevel ?? '—' },
      { key: 'product', label: '卡产品', children: result.cardProduct ?? '—' },
      { key: 'country', label: '发卡国', children: result.issuerCountry ?? '—' },
      { key: 'region', label: '发卡区域', children: result.issuerRegion ?? '—' },
      { key: 'issuer', label: '发卡行', children: result.issuerName ?? '—' },
      { key: 'version', label: '版本', children: result.binLibraryVersionNo ? `v${result.binLibraryVersionNo}` : '—' },
    ]} /> : <p className="muted bin-unrecognized">未识别{result.binLibraryVersionNo ? `（版本 v${result.binLibraryVersionNo}）` : '（尚无生效版本）'}</p>)}
  </section>;
}

/**
 * 右侧版本详情：基本信息 + 状态区
 * <li>校验中每 3 秒轮询（离开或切换版本即停止），结束后通知页面刷新列表与摘要；校验失败显示原因与错误明细。</li>
 */
function VersionDetail({ versionNo, canManage, onActivate, onSettled }: {
  versionNo: number; canManage: boolean; onActivate: (version: BinLibraryVersion) => void; onSettled: () => void;
}) {
  const detail = useResource(binLibraryPaths.version(versionNo), decodeVersion);
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
    <div className="dq-detail-head"><div><h2>版本 v{version.versionNo}</h2>
      <div className="muted">{version.fileName ?? '种子数据（初始化）'}</div></div><StatusTag status={version.status} /></div>
    {version.status === 'VALIDATING' && <ValidatingState version={version} />}
    {version.status === 'INVALID' && <InvalidState version={version} />}
    <Descriptions size="small" column={1} items={[
      { key: 'file', label: '文件', children: version.fileName ? <span className="mono">{version.fileName}</span> : '—' },
      { key: 'hash', label: '文件摘要', children: version.fileContentHash
        ? <span className="mono" title={version.fileContentHash}>SHA-256 {version.fileContentHash.slice(0, 4)}…{version.fileContentHash.slice(-4)}</span> : '—' },
      { key: 'rows', label: '号段数', children: `${formatCount(version.totalRows)}（错误 ${formatCount(version.errorRows)}）` },
      { key: 'creator', label: '导入人', children: <span><span className="mono">{version.createdBy ?? 'system'}</span> · {localTime(version.createTime)}</span> },
      { key: 'updated', label: '最近更新', children: localTime(version.updateTime) },
    ]} />
    {version.status === 'ACTIVE' && <Note>回切只影响之后新建的支付工具，已冻结的交易卡属性不变。</Note>}
    {canManage && version.status === 'VALIDATING' && <div className="dq-detail-actions"><Button disabled>激活</Button>
      <span className="muted">校验通过后可激活</span></div>}
    {action && <div className="dq-detail-actions"><Button type={action.kind === 'ACTIVATE' ? 'primary' : 'default'} onClick={() => onActivate(version)}>
      {action.verb}</Button></div>}
  </>;
}

function ValidatingState({ version }: { version: BinLibraryVersion }) {
  const progress = versionProgress(version);
  return <div className="bin-progress">
    {progress.percent === null ? <div className="bin-progress-pending"><Spin size="small" /> <span>{progress.label}</span></div>
      : <><span>{progress.label}</span><Progress percent={progress.percent} status="active" /></>}
    <Note>每 3 秒自动刷新；校验完成前不能激活。中途失败会自动重试，不需要重新上传。</Note>
  </div>;
}

/** 校验失败：文件级原因横幅与错误明细分页（只记录前 200 条）。 */
function InvalidState({ version }: { version: BinLibraryVersion }) {
  const errors = usePaging<BinImportError>(binLibraryPaths.errors(version.versionNo), decodeImportErrorPage, {}, { enabled: version.errorRows > 0 });
  return <>
    <Alert type="error" showIcon className="dq-banner"
      title={version.failureReason ?? `${formatCount(version.errorRows)} 行内容错误，本版本不能激活`}
      description="请修正源文件后重新导入新版本。失败版本保留用于追溯。" />
    {version.errorRows > 0 && <>
      {errors.failure ? <QueryFailure failure={errors.failure} retry={errors.refresh} />
        : <Table<BinImportError> rowKey={row => `${row.lineNo}:${row.columnName ?? ''}:${row.errorCode}`} size="small" loading={errors.loading}
          dataSource={errors.data.items} pagination={{ ...errors.pagination, size: 'small' }} tableLayout="fixed" columns={[
            { title: '行号', width: 70, render: (_, row) => formatCount(row.lineNo) },
            { title: '列', width: 110, render: (_, row) => <span className="mono">{row.columnName ?? '整行'}</span> },
            { title: '错误', width: 150, render: (_, row) => <span className="mono">{row.errorCode}</span> },
            { title: '说明 / 值摘录', render: (_, row) => <span>{row.errorMessage}
              {row.valueExcerpt !== null && <span className="muted mono">：{row.valueExcerpt}</span>}</span> },
          ]} />}
      <p className="muted">只显示前 200 条，共 {formatCount(version.errorRows)} 行有错误；超过 8 位的数字一律隐藏，防止卡号进入错误记录。</p>
    </>}
  </>;
}

/** 激活历史：时间、类型、版本、替换的版本与操作人，按时间倒序分页。 */
function ActivationHistory() {
  const history = usePaging(binLibraryPaths.activations, decodeActivationPage, {}, { notifyOnError: false });
  return <>
    <h3 className="dq-section-title">激活历史</h3>
    {history.failure ? <QueryFailure failure={history.failure} retry={history.refresh} />
      : <Table rowKey={row => `${row.activatedAt}:${row.versionNo}`} size="small" loading={history.loading} dataSource={history.data.items}
        pagination={{ ...history.pagination, size: 'small' }} locale={{ emptyText: '暂无激活记录' }} tableLayout="fixed" columns={[
          { title: '时间', width: 130, render: (_, row) => localTime(row.activatedAt) },
          { title: '类型', width: 60, render: (_, row) => activationTypeLabels[row.activationType] },
          { title: '版本', render: (_, row) => row.previousVersionNo === null ? `v${row.versionNo}`
            : row.activationType === 'ROLLBACK' ? `v${row.versionNo} ← v${row.previousVersionNo}` : `v${row.previousVersionNo} → v${row.versionNo}` },
          { title: '操作人', width: 100, render: (_, row) => <span className="mono">{row.operatorAccountId ?? 'system'}</span> },
        ]} />}
  </>;
}
