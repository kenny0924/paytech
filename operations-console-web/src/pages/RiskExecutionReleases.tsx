import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Checkbox, Form, InputNumber, Modal, Select, Space, Spin, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource, PagingResponseBody } from '../api/types';
import { ApiError } from '../api/session';
import { formatRiskTime } from '../risk/RuleSetDetails';
import { decisionFlowApi } from '../risk/decisionFlowApi';
import type { FlowHistory, FlowSummary } from '../risk/decisionFlow';
import { activationNeedsAcknowledgement, checkLabels, EXECUTION_POINTS, executionReleaseApi, hasResource, releaseActions, releaseStatusLabels, SHADOW_SAMPLE_DEFAULT, SHADOW_SAMPLE_MAX, shadowRunning, statusLabel } from '../risk/riskControl';
import type { ExecutionRelease, ExecutionReleaseItem, ShadowRun } from '../risk/riskControl';
import { pointName, useRiskPoints } from '../risk/riskPoints';
import '../risk/riskExecution.css';

const PREPARE = 'OPERATIONS_RISK_EXECUTION_RELEASE_PREPARE';
const ACTIVATE = 'OPERATIONS_RISK_EXECUTION_RELEASE_ACTIVATE';
const POLL_MILLIS = 3000;
const POLL_LIMIT_MILLIS = 10 * 60 * 1000;
const statusColors: Record<string, string> = { DRAFT: 'blue', SHADOWING: 'gold', REJECTED: 'red', ACTIVE: 'green', SUPERSEDED: 'default' };

function errorText(e: unknown): string {
  if (e instanceof ApiError) return `${e.message}${e.requestId ? ` · Request ID: ${e.requestId}` : ''}`;
  return e instanceof Error ? e.message : '操作失败，请重试';
}
function StatusTag({ status }: { status: string }) { return <Tag color={statusColors[status] ?? 'default'}>{statusLabel(releaseStatusLabels, status)}</Tag>; }

/** 执行发布：把冻结的决策流发布到 TEST 运行，创建 → 校验 → 影子运行 → 激活。回滚＝用旧版本新建发布。 */
export function RiskExecutionReleases({ resources, onAuthorizationChanged }: { resources: readonly ConsoleResource[]; onAuthorizationChanged: () => void }) {
  const location = useLocation(), navigate = useNavigate();
  const q = new URLSearchParams(location.search);
  const pointCode = /^[A-Z][A-Z0-9_]{0,63}$/.test(q.get('pointCode') ?? '') ? q.get('pointCode')! : EXECUTION_POINTS[0]!;
  const releaseId = /^[1-9]\d{0,18}$/.test(q.get('releaseId') ?? '') ? q.get('releaseId')! : '';
  const canPrepare = hasResource(resources, PREPARE), canActivate = hasResource(resources, ACTIVATE);
  const failure = (e: unknown) => { if (e instanceof ApiError && e.status === 403) onAuthorizationChanged(); };
  const go = (next: Record<string, string>) => void navigate({ search: `?${new URLSearchParams(Object.entries({ pointCode, ...next }).filter(([, v]) => v))}` });
  return releaseId
    ? <ReleaseDetail pointCode={pointCode} releaseId={releaseId} canPrepare={canPrepare} canActivate={canActivate} onBack={() => go({})} onFailure={failure} />
    : <ReleaseList pointCode={pointCode} canPrepare={canPrepare} onPoint={code => go({ pointCode: code })} onOpen={id => go({ releaseId: id })} onFailure={failure} />;
}

function ReleaseList({ pointCode, canPrepare, onPoint, onOpen, onFailure }: { pointCode: string; canPrepare: boolean; onPoint: (code: string) => void; onOpen: (id: string) => void; onFailure: (e: unknown) => void }) {
  const { modal, message } = App.useApp();
  const points = useRiskPoints(), supported = EXECUTION_POINTS.includes(pointCode);
  const [paging, setPaging] = useState({ page: 1, size: 10 }), [status, setStatus] = useState('');
  const [data, setData] = useState<PagingResponseBody<ExecutionReleaseItem> | null>(null), [active, setActive] = useState<ExecutionReleaseItem | null>(null);
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [revision, setRevision] = useState(0), [creating, setCreating] = useState(false);
  useEffect(() => {
    if (!supported) { setData(null); setActive(null); return; }
    const abort = new AbortController(); setLoading(true); setError('');
    Promise.all([executionReleaseApi.list(pointCode, paging, status, abort.signal), executionReleaseApi.active(pointCode, abort.signal)])
      .then(([page, current]) => { if (!abort.signal.aborted) { setData(page); setActive(current); } })
      .catch(e => { if (!abort.signal.aborted) { setError(errorText(e)); onFailure(e); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [pointCode, paging, status, revision, supported]);
  function rollback(row: ExecutionReleaseItem) {
    modal.confirm({ title: `以 ${row.flowCode} #${row.flowVersionNo} 新建发布？`, content: '回滚会用该决策流版本新建一个发布，并重新校验、影子运行、激活；不会重新激活旧发布。', okText: '新建发布', cancelText: '取消',
      onOk: async () => { try { const created = await executionReleaseApi.create(pointCode, row.flowVersionId); void message.success('已新建发布'); onOpen(created.releaseId); } catch (e) { void message.error(errorText(e)); onFailure(e); throw e; } } });
  }
  return <div className="variable-page execution-page">
    <div className="page-heading"><div><h1>执行发布</h1><p className="cell-meta">把冻结的决策流发布到 TEST 运行：创建 → 校验 → 影子运行 → 激活。</p></div>
      <Space><Select aria-label="风控点" style={{ width: 200 }} value={pointCode} options={points.map(p => ({ value: p.pointCode, label: `风控点：${p.pointName}` }))} onChange={onPoint} />
        <Button onClick={() => setRevision(r => r + 1)} loading={loading} disabled={!supported}>刷新</Button>
        {canPrepare && <Button type="primary" disabled={!supported} onClick={() => setCreating(true)}>＋ 新建发布</Button>}</Space></div>
    {!supported ? <Alert type="info" showIcon title={`${pointName(points, pointCode)}暂不支持执行发布（运行期尚未接入），请切换到支付尝试。`} /> : <>
      {error && <Alert type="error" showIcon title={error} />}
      {active ? <section className="execution-active"><strong>当前生效：R-{active.releaseNo} · {active.flowCode} #{active.flowVersionNo}</strong>
        <span className="cell-meta">{pointName(points, pointCode)} · {active.environment} · 激活人 {active.activatedBy ?? '—'} · {formatRiskTime(active.activatedTime)} · 内容摘要 {active.contentHash.slice(0, 4)}…{active.contentHash.slice(-4)}</span>
        <Button type="link" onClick={() => onOpen(active.releaseId)}>查看详情</Button></section>
        : !loading && <Alert type="warning" showIcon title="当前没有生效发布，该风控点的评估将返回“未配置”（503）。" />}
      <section className="table-panel">
        <Form layout="inline" className="risk-filters"><Form.Item label="状态"><Select aria-label="发布状态" style={{ width: 160 }} value={status} options={[{ value: '', label: '全部' }, ...Object.entries(releaseStatusLabels).map(([value, label]) => ({ value, label }))]} onChange={value => { setStatus(value); setPaging(p => ({ ...p, page: 1 })); }} /></Form.Item></Form>
        <Table<ExecutionReleaseItem> rowKey="releaseId" loading={loading} dataSource={data?.items ?? []} locale={{ emptyText: error ? '发布加载失败' : '暂无执行发布' }}
          pagination={{ current: paging.page, pageSize: paging.size, total: data?.total ?? 0, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100, 200], onChange: (page, size) => setPaging({ page: size === paging.size ? page : 1, size }) }}
          columns={[
            { title: '发布号', render: (_, r) => `R-${r.releaseNo}` },
            { title: '决策流 / 版本', render: (_, r) => `${r.flowCode} #${r.flowVersionNo}` },
            { title: '状态', render: (_, r) => <StatusTag status={r.status} /> },
            { title: '创建人 / 时间', render: (_, r) => <>{r.createdBy}<div className="cell-meta">{formatRiskTime(r.createTime)}</div></> },
            { title: '激活人 / 时间', render: (_, r) => r.activatedTime ? <>{r.activatedBy ?? '—'}<div className="cell-meta">{formatRiskTime(r.activatedTime)}</div></> : '—' },
            { title: '操作', render: (_, r) => <Space size={0}><Button type="link" onClick={() => onOpen(r.releaseId)}>详情</Button>{canPrepare && r.status === 'SUPERSEDED' && <Button type="link" onClick={() => rollback(r)}>以此版本新建</Button>}</Space> },
          ]} />
        <Alert type="info" showIcon title="回滚：在已替代的发布上点“以此版本新建”，用相同决策流版本新建发布并重新校验、影子运行、激活；不支持直接重新激活旧发布。" />
      </section></>}
    {creating && <CreateReleaseModal pointCode={pointCode} onClose={() => setCreating(false)} onCreated={id => { setCreating(false); onOpen(id); }} onFailure={onFailure} />}
  </div>;
}

function CreateReleaseModal({ pointCode, onClose, onCreated, onFailure }: { pointCode: string; onClose: () => void; onCreated: (id: string) => void; onFailure: (e: unknown) => void }) {
  const [flows, setFlows] = useState<FlowSummary[]>([]), [flowId, setFlowId] = useState(''), [versions, setVersions] = useState<FlowHistory[]>([]), [flowVersionId, setFlowVersionId] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  useEffect(() => {
    const abort = new AbortController();
    decisionFlowApi.list({ page: 1, size: 200 }, { pointCode }, abort.signal).then(p => { if (!abort.signal.aborted) setFlows(p.items); }).catch(e => { if (!abort.signal.aborted) setError(errorText(e)); });
    return () => abort.abort();
  }, [pointCode]);
  useEffect(() => {
    const flow = flows.find(f => f.flowId === flowId); if (!flow) return;
    const abort = new AbortController(); setFlowVersionId(flow.currentFlowVersionId);
    decisionFlowApi.history(flow.flowId, flow.currentVersionNo, { page: 1, size: 50 }, abort.signal).then(p => { if (!abort.signal.aborted) setVersions(p.items); }).catch(e => { if (!abort.signal.aborted) setError(errorText(e)); });
    return () => abort.abort();
  }, [flowId, flows]);
  async function submit() {
    if (!flowVersionId) { setError('请选择决策流版本'); return; }
    setBusy(true); setError('');
    try { onCreated((await executionReleaseApi.create(pointCode, flowVersionId)).releaseId); }
    catch (e) { setError(`${errorText(e)}${e instanceof ApiError && e.status === 400 ? '（引用的规则集版本须已冻结，请先在【规则配置】中冻结）' : ''}`); onFailure(e); }
    finally { setBusy(false); }
  }
  const current = flows.find(f => f.flowId === flowId);
  return <Modal open title="新建执行发布" okText="创建发布" cancelText="取消" confirmLoading={busy} onOk={() => void submit()} onCancel={onClose} destroyOnHidden>
    <Form layout="vertical">
      <Form.Item label="决策流" required><Select aria-label="决策流" showSearch optionFilterProp="label" value={flowId || undefined} placeholder="请选择决策流" options={flows.map(f => ({ value: f.flowId, label: `${f.flowCode} · ${f.flowName}` }))} onChange={setFlowId} /></Form.Item>
      <Form.Item label="流程版本" required extra="引用的规则集版本必须已冻结；否则 Risk 拒绝创建。"><Select aria-label="流程版本" disabled={!current} value={flowVersionId || undefined} options={versions.map(v => ({ value: v.flowVersionId, label: `#${v.versionNo}${v.flowVersionId === current?.currentFlowVersionId ? ' · 当前版本' : ''} · ${v.createdBy} · ${formatRiskTime(v.createTime)}` }))} onChange={setFlowVersionId} /></Form.Item>
    </Form>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}

function ReleaseDetail({ pointCode, releaseId, canPrepare, canActivate, onBack, onFailure }: { pointCode: string; releaseId: string; canPrepare: boolean; canActivate: boolean; onBack: () => void; onFailure: (e: unknown) => void }) {
  const { message } = App.useApp();
  const [release, setRelease] = useState<ExecutionRelease | null>(null), [active, setActive] = useState<ExecutionReleaseItem | null>(null);
  const [error, setError] = useState(''), [busy, setBusy] = useState(false), [revision, setRevision] = useState(0), [sample, setSample] = useState(SHADOW_SAMPLE_DEFAULT);
  const [confirming, setConfirming] = useState(false), [pollExpired, setPollExpired] = useState(false);
  const pollStart = useRef(0);
  useEffect(() => {
    const abort = new AbortController(); setError('');
    Promise.all([executionReleaseApi.detail(releaseId, abort.signal), executionReleaseApi.active(pointCode, abort.signal)])
      .then(([detail, current]) => { if (!abort.signal.aborted) { setRelease(detail); setActive(current); } })
      .catch(e => { if (!abort.signal.aborted) { setError(errorText(e)); onFailure(e); } });
    return () => abort.abort();
  }, [releaseId, pointCode, revision]);
  // 影子运行异步执行：运行中每 3 秒刷新详情，离开页面即停止；超过 10 分钟停止轮询并提示。
  useEffect(() => {
    if (!shadowRunning(release)) { pollStart.current = 0; return; }
    if (!pollStart.current) pollStart.current = Date.now();
    if (Date.now() - pollStart.current > POLL_LIMIT_MILLIS) { setPollExpired(true); return; }
    const timer = window.setTimeout(() => setRevision(r => r + 1), POLL_MILLIS);
    return () => window.clearTimeout(timer);
  }, [release]);
  if (!release) return <div className="variable-page">{error ? <Alert type="error" showIcon title={error} action={<Button onClick={onBack}>返回</Button>} /> : <Spin />}</div>;
  const hasActive = !!active && active.releaseId !== release.releaseId;
  const actions = releaseActions(release, hasActive);
  async function run(label: string, call: () => Promise<ExecutionRelease>) {
    setBusy(true); setError('');
    try { setRelease(await call()); void message.success(label); setRevision(r => r + 1); }
    catch (e) { setError(errorText(e)); onFailure(e); } finally { setBusy(false); }
  }
  const passed = release.checks.filter(c => c.checkStatus === 'PASSED').length;
  return <div className="variable-page execution-page">
    <div className="page-heading"><div><Button type="link" onClick={onBack}>← 执行发布</Button>
      <h1>R-{release.releaseNo} <StatusTag status={release.status} /></h1>
      <p className="cell-meta">{release.pointCode} · {release.environment} · 决策流版本 {release.flowVersionId} · 内容摘要 {release.contentHash.slice(0, 4)}…{release.contentHash.slice(-4)}{hasActive ? ` · 当前生效 R-${active!.releaseNo}` : ' · 当前没有生效发布'}</p></div>
      <Space wrap>
        <Button onClick={() => setRevision(r => r + 1)}>刷新</Button>
        {canPrepare && actions.validate && <Button type="primary" loading={busy} onClick={() => void run('校验完成', () => executionReleaseApi.validate(release.releaseId))}>校验</Button>}
        {canPrepare && actions.shadow && <Space.Compact><InputNumber aria-label="影子样本数" min={1} max={SHADOW_SAMPLE_MAX} value={sample} onChange={v => setSample(v ?? SHADOW_SAMPLE_DEFAULT)} />
          <Button loading={busy} onClick={() => void run('影子运行已发起', () => executionReleaseApi.shadow(release.releaseId, sample))}>{release.shadowRun ? '重新发起影子运行' : '发起影子运行'}</Button></Space.Compact>}
        {canActivate && release.status === 'SHADOWING' && <Button danger type="primary" disabled={!actions.activate || busy} onClick={() => setConfirming(true)}>激活</Button>}
      </Space></div>
    {error && <Alert type="error" showIcon title={error} />}
    <StepBar release={release} hasActive={hasActive} />
    <section className="execution-card"><div className="execution-card-title"><h3>静态检查 · {release.checks.length ? `${passed} / ${release.checks.length} 通过` : '尚未校验'}</h3></div>
      {release.checks.length ? <div className="execution-checks">{release.checks.map(c => <div key={c.checkCode} className={`execution-check ${c.checkStatus === 'PASSED' ? 'is-passed' : 'is-failed'}`}>
        <strong>{c.checkStatus === 'PASSED' ? '✓' : '✕'} {checkLabels[c.checkCode] ?? c.checkCode}</strong>{c.errorSummary && <span>{c.errorCode} · {c.errorSummary}</span>}</div>)}</div>
        : <p className="cell-meta">创建后点击“校验”执行八项静态检查；通过后进入待激活，未通过则发布被拒绝。</p>}
    </section>
    <ShadowCard release={release} hasActive={hasActive} pollExpired={pollExpired} />
    <p className="cell-meta">影子运行不写累计事件、不影响正式结论；运行中每 3 秒刷新一次，离开页面停止刷新。</p>
    {confirming && <ActivateModal release={release} active={hasActive ? active : null} onClose={() => setConfirming(false)}
      onConfirm={async () => { await run('已激活', () => executionReleaseApi.activate(release.releaseId)); setConfirming(false); }} />}
  </div>;
}

function StepBar({ release, hasActive }: { release: ExecutionRelease; hasActive: boolean }) {
  const validated = release.checks.length > 0, rejected = release.status === 'REJECTED', activated = release.status === 'ACTIVE' || release.status === 'SUPERSEDED';
  const shadow = !hasActive && !release.shadowRun ? '首发无需影子' : shadowRunning(release) ? '运行中' : release.shadowRun ? `${release.shadowRun.sampleCount} 样本 · ${release.shadowRun.checkStatus === 'PASSED' ? '门槛通过' : '门槛未通过'}` : '待发起';
  const steps: { title: string; text: string; state: 'done' | 'current' | 'wait' | 'error' }[] = [
    { title: '创建', text: '已完成', state: 'done' },
    { title: '校验', text: rejected ? '未通过' : validated ? '已完成' : '待校验', state: rejected ? 'error' : validated ? 'done' : 'current' },
    { title: '影子运行', text: shadow, state: activated || release.shadowRun?.status === 'COMPLETED' || (!hasActive && validated && !rejected) ? 'done' : validated && !rejected ? 'current' : 'wait' },
    { title: '激活', text: activated ? formatRiskTime(release.activatedTime) : '待确认', state: activated ? 'done' : validated && !rejected ? 'current' : 'wait' },
  ];
  return <section className="execution-steps">{steps.map((s, i) => <div key={s.title} className={`execution-step is-${s.state}`}><span className="execution-step-index">{s.state === 'done' ? '✓' : s.state === 'error' ? '✕' : i + 1}</span><div><strong>{s.title}</strong><small>{s.text}</small></div></div>)}</section>;
}

function Metric({ label, value, highlight, hint }: { label: string; value: number | null; highlight?: boolean; hint?: string }) {
  const alarming = highlight && (value ?? 0) > 0;
  return <div className={`execution-metric${highlight ? ' is-key' : ''}${alarming ? ' is-alarming' : ''}`}><span>{label}</span><strong>{value ?? '—'}</strong>{hint && <small>{hint}</small>}</div>;
}

function ShadowCard({ release, hasActive, pollExpired }: { release: ExecutionRelease; hasActive: boolean; pollExpired: boolean }) {
  const run: ShadowRun | null = release.shadowRun;
  if (!run) return <section className="execution-card"><h3>影子运行</h3><p className="cell-meta">{hasActive ? '校验通过后，发起影子运行回放当前生效发布最近的正式评估（默认 200 条，上限 2000）；完成且失败、超时为 0 后才能激活。' : '这是该风控点的首个发布，没有可回放的基线，校验通过后可直接激活。'}</p></section>;
  const running = shadowRunning(release);
  return <section className="execution-card">
    <div className="execution-card-title"><h3>影子运行 · 回放基线 {run.baselineReleaseId} 最近 {run.sampleTarget} 条正式评估 <Tag color={running ? 'gold' : run.status === 'COMPLETED' ? 'green' : 'red'}>{running ? '运行中' : run.status === 'COMPLETED' ? '已完成' : run.status}</Tag></h3>
      <span className={run.checkStatus === 'PASSED' ? 'execution-pass' : 'cell-meta'}>{run.checkStatus ? `技术门槛：失败 ${run.failedCount ?? 0} · 超时 ${run.timedOutCount ?? 0} → ${run.checkStatus === 'PASSED' ? '通过' : '未通过'}` : '完成后判定技术门槛'}</span></div>
    {running && <div className="execution-progress"><div style={{ width: `${Math.min(100, Math.round(run.sampleCount * 100 / Math.max(1, run.sampleTarget)))}%` }} /></div>}
    {pollExpired && <Alert type="warning" showIcon title="影子运行仍在进行，已停止自动刷新，可稍后手动刷新。" />}
    <div className="execution-metrics">
      <Metric label="拒绝 → 放行" value={run.denyToAllowCount} highlight hint="原本拒绝或审核、新版本放行" />
      <Metric label="认证要求变化" value={run.actionChangeCount} highlight hint="放行时 3DS 要求与基线不同" />
      <Metric label="放行 → 审核" value={run.allowToReviewCount} />
      <Metric label="放行 → 拒绝" value={run.allowToDenyCount} />
    </div>
    <div className="execution-tech">样本 {run.sampleCount} / {run.sampleTarget} · 特征缺失 {run.featureMissingCount ?? '—'} · 失败 {run.failedCount ?? '—'} · 超时 {run.timedOutCount ?? '—'} · P95 {run.p95Millis ?? '—'} ms · P99 {run.p99Millis ?? '—'} ms</div>
  </section>;
}

function ActivateModal({ release, active, onClose, onConfirm }: { release: ExecutionRelease; active: ExecutionReleaseItem | null; onClose: () => void; onConfirm: () => Promise<void> }) {
  const [acknowledged, setAcknowledged] = useState(false), [busy, setBusy] = useState(false);
  const needs = activationNeedsAcknowledgement(release, !!active), run = release.shadowRun;
  return <Modal open title={`激活执行发布 R-${release.releaseNo}`} okText="确认激活" cancelText="取消" okButtonProps={{ danger: true, disabled: needs && !acknowledged }} confirmLoading={busy}
    onOk={async () => { setBusy(true); try { await onConfirm(); } finally { setBusy(false); } }} onCancel={onClose} destroyOnHidden>
    {active ? <Alert type="warning" showIcon title={`激活后立即替换当前生效发布 R-${active.releaseNo}（${active.flowCode} #${active.flowVersionNo}），对新的支付评估生效。`} />
      : <Alert type="warning" showIcon title="这是该风控点的首个发布，没有影子对比，激活后立即对评估生效。" />}
    {active && run && <><div className="execution-metrics execution-confirm-metrics">
      <Metric label="拒绝 → 放行" value={run.denyToAllowCount} highlight hint="原本拒绝或审核、新版本放行" />
      <Metric label="认证要求变化" value={run.actionChangeCount} highlight hint="3DS 要求与基线不同" />
    </div><p className="cell-meta">放行→审核 {run.allowToReviewCount ?? '—'} · 放行→拒绝 {run.allowToDenyCount ?? '—'} · 特征缺失 {run.featureMissingCount ?? '—'} · 失败 {run.failedCount ?? '—'} · 超时 {run.timedOutCount ?? '—'} · 样本 {run.sampleCount} · P99 {run.p99Millis ?? '—'} ms</p></>}
    {needs && <Checkbox checked={acknowledged} onChange={e => setAcknowledged(e.target.checked)}>{active ? '我已查看影子差异并确认激活' : '我已确认首个发布没有影子对比'}</Checkbox>}
  </Modal>;
}
