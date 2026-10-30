import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Drawer, Spin } from 'antd';
import { useSearchParams } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { useT } from '../i18n/react';
import { Loading, QueryFailure } from '../tenantAccess/TenantAccessView';
import { dataQueryPaths, isRecord, isText, require } from '../dataQuery/dataQueryCommon';
import { checkEntry, decodeJointSettings, decodeJointStep } from '../dataQuery/jointModel';
import type { JointCell, JointSettings, JointStepRequest } from '../dataQuery/jointModel';
import { normalizeKeyValue, retryNode, runJointQuery } from '../dataQuery/jointRun';
import type { DriveOptions, JointRunState, RunNode } from '../dataQuery/jointRun';
import {
  ancestry, buildEdges, buildLanes, clientKeyType, collectIssues, entryClients, filterByClient, groupCount, laneColors, locateValue, mergeByObject, nodeBadge,
  skippedViews,
  sourcePath,
} from '../dataQuery/jointLayout';
import type { FlowEdge, Issue, SkippedView } from '../dataQuery/jointLayout';
import { JointGraph, BadgeText } from '../dataQuery/JointGraph';
import { DetailSwitch, NodeDetail } from '../dataQuery/JointDetail';
import type { DetailActions, DetailView, NodeRows } from '../dataQuery/JointDetail';
import { JointSearch, readRecent, rememberRecent } from '../dataQuery/JointSearch';
import { JointCoverage } from '../dataQuery/JointCoverage';
import { useResource } from '../dataQuery/DataQueryParts';
import { dataQueryErrorMessage } from '../dataQuery/useDataQueryCommand';
import { useMediaQuery } from '../dataQuery/useMediaQuery';
import '../dataQuery/dataQuery.css';
import '../dataQuery/jointQuery.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/**
 * 联合查询（原型 01—08）：选单号类型、输入单号，逐层查出各系统的关联数据。上方链路总览只负责导航，
 * 点节点在下方看明细；问题汇总为一行可展开；能让用户自己解决的给出操作（重试、按客户筛选、以此查询）。
 */
export function JointQuery({ onAuthorizationChanged }: Props) {
  const t = useT('data-query');
  const settings = useResource(dataQueryPaths.jointSettings, decodeJointSettings, t('joint.settingsLoadFailed'));
  if (settings.loading && !settings.data) return <Loading />;
  if (!settings.data) return <QueryFailure failure={settings.failure} retry={settings.refresh} />;
  return <Workspace settings={settings.data} onAuthorizationChanged={onAuthorizationChanged} />;
}

/** 节点明细中按需查看的未执行视图（technique 8.7）：结果只显示在该节点明细里，不进入链路图、不继续展开。 */
interface ViewsState { loading: boolean; error: string | null; nodes: RunNode[] | null }

interface MoreRows { rows: JointCell[][]; page: number; cursor: string | null; loading: boolean; done: boolean; error: string | null }

function decodeExecution(value: unknown) {
  require(isRecord(value) && Array.isArray(value.columns) && Array.isArray(value.rows) && typeof value.hasMore === 'boolean'
    && (value.nextCursor == null || isText(value.nextCursor)));
  const columns = (value.columns as unknown[]).map(column => { require(isRecord(column) && isText(column.name)); return column.name as string; });
  const rows = value.rows as unknown[];
  require(rows.every(row => Array.isArray(row) && row.length === columns.length));
  return { columns, rows: rows as JointCell[][], hasMore: value.hasMore as boolean, nextCursor: (value.nextCursor ?? null) as string | null };
}
const jsonHeaders = { 'Content-Type': 'application/json' };

function Workspace({ settings, onAuthorizationChanged }: { settings: JointSettings; onAuthorizationChanged: () => void }) {
  const t = useT('data-query');
  const [params, setParams] = useSearchParams();
  const limits = settings.limits;
  const [keyTypeCode, setKeyTypeCode] = useState<string | null>(() => {
    const requested = params.get('keyType');
    const known = (code: string | null | undefined) => code && settings.keyTypes.some(item => item.keyTypeCode === code) ? code : null;
    return known(requested) ?? known(readRecent()[0]) ?? settings.keyTypes[0]?.keyTypeCode ?? null;
  });
  const [text, setText] = useState(() => params.getAll('value').join('\n'));
  const [run, setRun] = useState<JointRunState | null>(null);
  const runRef = useRef<JointRunState | null>(null); runRef.current = run;
  const [busy, setBusy] = useState<'run' | 'retry' | null>(null);
  const controller = useRef<AbortController | null>(null);
  const [attempted, setAttempted] = useState(false);
  const [serverLines, setServerLines] = useState<ReadonlySet<number>>(new Set());
  const [requestError, setRequestError] = useState<string | null>(null);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [view, setView] = useState<DetailView>('node');
  const [client, setClient] = useState<string | null>(null);
  const [issuesOpen, setIssuesOpen] = useState(false);
  const [coverageOpen, setCoverageOpen] = useState(false);
  const [more, setMore] = useState<Record<string, MoreRows>>({});
  const [views, setViews] = useState<Record<string, ViewsState>>({});
  const mobile = useMediaQuery('(max-width: 760px)');
  const [mobileGraph, setMobileGraph] = useState(false);
  const [mobileNode, setMobileNode] = useState<string | null>(null);
  const detailRef = useRef<HTMLDivElement | null>(null);

  const keyType = settings.keyTypes.find(item => item.keyTypeCode === keyTypeCode);
  const check = useMemo(() => checkEntry(text, keyType, limits.maxEntryValues), [text, keyType, limits.maxEntryValues]);
  // 按对象合并显示（修订 c）：同一对象只在首次显示它的节点出现；计数、筛选、定位、路径都用合并后的结果。
  const merged = useMemo(() => mergeByObject(run?.nodes ?? [], run?.links ?? []), [run]);
  const nodes = merged.nodes;
  const links = merged.links;
  // “也可经 … 到达”的路径按原始链路计算（被合并的节点不在图上）。
  const rawEdges = useMemo(() => buildEdges(run?.nodes ?? [], run?.links ?? []), [run]);
  const colors = useMemo(() => laneColors(settings.groups, nodes), [settings.groups, nodes]);
  const clients = useMemo(() => entryClients(nodes), [nodes]);
  const activeClient = client && clients.includes(client) ? client : null;
  const visibleRows = useMemo(() => filterByClient(nodes, links, activeClient), [nodes, links, activeClient]);
  const shown = useMemo(() => visibleRows ? nodes.filter(node => visibleRows.has(node.id)) : nodes, [nodes, visibleRows]);
  // 已加载到最后一页的节点不再显示为截断（n+）。
  const displayed = useMemo(() => shown.map(node => more[node.id]?.done && node.truncated ? { ...node, truncated: false } : node), [shown, more]);
  const lanes = useMemo(() => buildLanes(displayed, colors), [displayed, colors]);
  const ordered = useMemo(() => lanes.flatMap(lane => lane.nodes), [lanes]);
  const edges = useMemo(() => buildEdges(shown, links), [shown, links]);
  const selected = shown.find(node => node.id === selectedId) ?? null;
  const path = useMemo(() => ancestry(selected?.id ?? null, edges), [selected, edges]);
  const issues = useMemo(() => run ? collectIssues({ ...run, nodes }) : [], [run, nodes]);
  const running = busy !== null;

  // 键类型名称：入口类型取 J1；其余取结果列标题（不显示编码）。
  const keyTypeNames = useMemo(() => {
    const names = new Map<string, string>();
    for (const node of nodes) for (const column of node.columns) if (column.keyType && column.label && !names.has(column.keyType)) names.set(column.keyType, column.label);
    for (const item of settings.keyTypes) names.set(item.keyTypeCode, item.keyTypeName);
    return names;
  }, [nodes, settings.keyTypes]);

  // 选中节点不在当前结果中时，默认选第一个起点节点（按泳道顺序）。
  useEffect(() => {
    if (selected || !ordered.length) return;
    setSelectedId((ordered.find(node => node.depth === 0) ?? ordered[0]).id);
  }, [selected, ordered]);
  useEffect(() => () => controller.current?.abort(), []);
  // 地址中带入口（链接分享、从别处跳转）时自动查询一次。
  const autoRun = useRef(params.getAll('value').length > 0);
  useEffect(() => {
    if (!autoRun.current) return;
    autoRun.current = false;
    start(keyTypeCode, text);
  // 只在首次进入时执行
  });

  const reportFailure = useCallback((state: JointRunState, valueLines: readonly number[]) => {
    if (state.status !== 'failed') return;
    const error = state.error;
    if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
    if (error instanceof ApiError && error.fieldErrors.length) {
      const lines = new Set<number>();
      for (const item of error.fieldErrors) {
        const match = /^keys\[0]\.values\[(\d+)]/.exec(item.field);
        if (match && valueLines[Number(match[1])]) lines.add(valueLines[Number(match[1])]);
      }
      setServerLines(lines);
      setRequestError(error.fieldErrors.map(item => item.message).join(t('error.fieldErrorSeparator')));
      return;
    }
    setRequestError(dataQueryErrorMessage(error) ?? (error instanceof Error ? error.message : t('joint.run.failed')));
  }, [onAuthorizationChanged, t]);

  const options = useCallback((own: AbortController): DriveOptions => ({
    limits, signal: own.signal, now: () => Date.now(),
    step: (request: JointStepRequest, signal: AbortSignal) => session.request(dataQueryPaths.jointSteps,
      { method: 'POST', signal, headers: jsonHeaders, body: JSON.stringify(request) }, decodeJointStep),
    onUpdate: state => { if (controller.current === own) setRun(state); },
  }), [limits]);

  function start(code: string | null, raw: string) {
    const type = settings.keyTypes.find(item => item.keyTypeCode === code);
    const entry = checkEntry(raw, type, limits.maxEntryValues);
    setAttempted(true); setServerLines(new Set()); setRequestError(null);
    if (!type || entry.empty || entry.invalid.length || entry.tooMany) return;
    controller.current?.abort();
    const own = new AbortController(); controller.current = own;
    rememberRecent(type.keyTypeCode);
    setParams(new URLSearchParams([['keyType', type.keyTypeCode], ...entry.values.map(value => ['value', value] as [string, string])]), { replace: true });
    const values = [...new Set(entry.values.map(value => normalizeKeyValue(type.valueType, value)))];
    setSelectedId(null); setView('node'); setClient(null); setMore({}); setViews({}); setIssuesOpen(false); setMobileNode(null); setBusy('run');
    void runJointQuery({ keyType: type.keyTypeCode, values }, options(own)).then(state => reportFailure(state, entry.valueLines))
      .finally(() => { if (controller.current === own) { controller.current = null; setBusy(null); } });
  }
  function stop() { controller.current?.abort(); }
  function retry(node: RunNode) {
    const current = runRef.current;
    if (!current || controller.current) return;
    const own = new AbortController(); controller.current = own; setBusy('retry'); setRequestError(null);
    void retryNode(current, node, options(own)).then(state => reportFailure(state, []))
      .finally(() => { if (controller.current === own) { controller.current = null; setBusy(null); } });
  }
  function queryWith(code: string, value: string) {
    setKeyTypeCode(code); setText(value);
    window.scrollTo({ top: 0, behavior: 'smooth' });
    start(code, value);
  }

  async function loadMore(node: RunNode) {
    const current = more[node.id];
    if (current?.loading || current?.done) return;
    const size = node.rows.length || limits.maxRowsPerTemplate;
    const update = (patch: Partial<MoreRows>) => setMore(state => ({ ...state, [node.id]: { ...(state[node.id] ?? { rows: [], page: 1, cursor: node.nextCursor, loading: false, done: false, error: null }), ...patch } }));
    update({ loading: true, error: null });
    const execute = async (list: boolean, paging: Record<string, unknown>) => session.request(dataQueryPaths.execution(node.templateCode),
      { method: 'POST', headers: jsonHeaders, body: JSON.stringify({ params: { [node.keyParameter]: list ? node.triggerValues : node.triggerValues[0] }, withTotal: false, ...paging }) },
      decodeExecution);
    // 键参数是否集合：服务端给出时按其值；否则多个触发值必为集合，单个值先按标量、被拒绝时再按集合。
    const call = async (paging: Record<string, unknown>) => {
      const list = node.keyParameterList ?? node.triggerValues.length > 1;
      try { return await execute(list, paging); } catch (error) {
        if (node.keyParameterList === null && !list && error instanceof ApiError && (error.status === 400 || error.status === 422)) return execute(true, paging);
        throw error;
      }
    };
    try {
      let result;
      if (node.pagingMode === 'CURSOR') {
        let cursor = current?.cursor ?? node.nextCursor;
        if (!cursor) cursor = (await call({ size })).nextCursor;
        result = cursor ? await call({ size, cursor }) : { columns: [], rows: [], hasMore: false, nextCursor: null };
      } else {
        result = await call({ page: (current?.page ?? 1) + 1, size });
      }
      const order = node.columns.map(column => result.columns.findIndex(name => name.toLowerCase() === column.name.toLowerCase()));
      const rows = result.rows.map(row => order.map(index => index < 0 ? null : row[index]));
      setMore(state => {
        const previous = state[node.id] ?? { rows: [], page: 1, cursor: null, loading: false, done: false, error: null };
        return { ...state, [node.id]: { rows: [...previous.rows, ...rows], page: previous.page + 1, cursor: result.nextCursor, loading: false,
          done: !result.hasMore || (node.pagingMode === 'CURSOR' && !result.nextCursor), error: null } };
      });
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      update({ loading: false, error: dataQueryErrorMessage(error) ?? (error instanceof Error ? error.message : t('joint.detail.loadMoreFailed')) });
    }
  }

  const clientIndex = (node: RunNode) => node.columns.findIndex(column => column.keyType === clientKeyType);
  const rowsOf = useCallback((node: RunNode): NodeRows => {
    const keep = visibleRows?.get(node.id);
    const base = keep ? node.rows.filter((_, index) => keep.includes(index)) : node.rows;
    const extra = more[node.id];
    const index = clientIndex(node);
    const added = (extra?.rows ?? []).filter(row => !activeClient || index < 0 || row[index] === null || String(row[index]) === activeClient);
    return { rows: [...base, ...added], more: extra ? { loading: extra.loading, done: extra.done, error: extra.error } : null };
  }, [visibleRows, more, activeClient]);
  const rowCount = useCallback((node: RunNode) => rowsOf(node).rows.length, [rowsOf]);

  const reason = (node: RunNode) => {
    const code = node.errorCode ?? '';
    const key = `joint.reason.${code}`;
    // 未知的跳过码（例如以后新增的）一律按“未查”说明，可重试。
    return code && t.has(key) ? t(key) : node.errorMessage ?? (node.status === 'SKIPPED' ? t('joint.reason.skippedGeneric') : t('joint.reason.generic'));
  };
  const actions: DetailActions = {
    locate: (code, value, exceptId) => locateValue(shown, code, value, exceptId),
    onLocate: node => { setSelectedId(node.id); setView('node'); if (mobile) setMobileNode(node.id); else detailRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }); },
    canQuery: (code, value) => {
      const type = settings.keyTypes.find(item => item.keyTypeCode === code);
      if (!type) return false;
      const result = checkEntry(value, type, 1);
      return !result.invalid.length && !result.empty;
    },
    onQuery: queryWith, onRetry: retry, onLoadMore: node => void loadMore(node), retrying: busy === 'retry', failureReason: reason,
  };
  /** 按需查看：按（层，键类型）分组，用 templateCodes 只执行这些视图；不带 loadedSubjects，不改变链路状态。 */
  async function openViews(node: RunNode, list: readonly SkippedView[]) {
    if (views[node.id]?.loading) return;
    setViews(state => ({ ...state, [node.id]: { loading: true, error: null, nodes: state[node.id]?.nodes ?? null } }));
    try {
      const groups = new Map<string, SkippedView[]>();
      for (const view of list) groups.set(`${view.depth}|${view.keyType}`, [...(groups.get(`${view.depth}|${view.keyType}`) ?? []), view]);
      const loaded: RunNode[] = [];
      for (const group of groups.values()) {
        const values = [...new Set(group.flatMap(view => view.values))].slice(0, limits.maxValuesPerKey);
        const codes = group.map(view => view.templateCode);
        for (let i = 0; i < codes.length; i += 20) {
          const request: JointStepRequest = { depth: group[0].depth, keys: [{ keyType: group[0].keyType, values }], templateCodes: codes.slice(i, i + 20) };
          const response = await session.request(dataQueryPaths.jointSteps, { method: 'POST', headers: jsonHeaders, body: JSON.stringify(request) }, decodeJointStep);
          loaded.push(...response.nodes.map(item => ({ ...item, id: `${node.id}::${item.nodeId}`, depth: response.depth })));
        }
      }
      setViews(state => ({ ...state, [node.id]: { loading: false, error: null, nodes: loaded } }));
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      setViews(state => ({ ...state, [node.id]: { loading: false, error: dataQueryErrorMessage(error) ?? (error instanceof Error ? error.message : t('joint.views.failed')), nodes: state[node.id]?.nodes ?? null } }));
    }
  }
  const viewsOf = (node: RunNode) => run ? skippedViews(node, run.alreadyLoaded) : [];
  const detailOf = (node: RunNode, header?: ReactNode) => {
    const list = viewsOf(node);
    const alsoPaths = (merged.also.get(node.id) ?? []).map(item => sourcePath(item.nodeId, run?.nodes ?? [], rawEdges, lanes).map(step => step.templateName).join(' → '));
    const viewActions: DetailActions = { ...actions, onRetry: () => void openViews(node, list), retrying: !!views[node.id]?.loading };
    return <NodeDetail key={node.id} node={node} color={colors.get(node.groupName) ?? '#a0aec0'} path={sourcePath(node.id, shown, edges, lanes)}
      rows={rowsOf(node)} actions={actions} header={header} also={alsoPaths}
      extra={list.length ? <SkippedViews count={list.length} state={views[node.id]} onOpen={() => void openViews(node, list)}
        render={item => <NodeDetail key={item.id} node={item} color={colors.get(item.groupName) ?? '#a0aec0'} path={[]} rows={rowsOf(item)} actions={viewActions} />} /> : undefined} />;
  };
  const edgeTitle = useCallback((edge: FlowEdge) => `${keyTypeNames.get(edge.keyType) ?? ''}${keyTypeNames.has(edge.keyType) ? t('joint.colon') : ''}${edge.values.slice(0, 5).join(', ')}${edge.values.length > 5 ? ` +${edge.values.length - 5}` : ''}`, [keyTypeNames, t]);
  const nodeTitle = useCallback((node: RunNode) => node.status === 'SUCCEEDED' ? node.templateName : `${node.templateName}${t('joint.colon')}${reason(node)}`, [t]);

  const formMessage = attempted && !keyType ? t('joint.search.chooseTypeFirst')
    : check.tooMany ? t('joint.search.tooMany', { max: limits.maxEntryValues })
      : check.invalid.length ? (keyType?.inputHint ? t('joint.search.invalidWithHint', { name: keyType.keyTypeName, hint: keyType.inputHint, lines: check.invalid.join(', '), count: check.invalid.length })
        : t('joint.search.invalid', { name: keyType?.keyTypeName ?? '', lines: check.invalid.join(', '), count: check.invalid.length }))
        : attempted && check.empty ? t('joint.search.empty') : requestError;

  const hasResults = !!run && nodes.length > 0;
  const total = shown.length;
  return <div className="dq-page jq-page">
    <p className="muted jq-intro">{t('joint.intro')}</p>
    <JointSearch keyTypes={settings.keyTypes} keyType={keyType} onKeyType={code => { setKeyTypeCode(code); setServerLines(new Set()); setRequestError(null); }}
      text={text} onText={value => { setText(value); setServerLines(new Set()); }} check={check} running={running} compact={mobile}
      onSubmit={() => start(keyTypeCode, text)} onStop={stop} serverLines={serverLines} />
    {formMessage && <div className="dq-text-error jq-form-message" role="alert">{formMessage}</div>}
    {run && (hasResults || running) && <Summary run={run} total={total} groups={groupCount(shown)} running={running} issues={issues} issuesOpen={issuesOpen}
      onToggleIssues={() => setIssuesOpen(open => !open)} clients={clients} client={activeClient} onClient={setClient} limitsSeconds={limits} />}
    {run && issuesOpen && issues.length > 0 && <IssueList issues={issues} nodes={nodes} keyTypeNames={keyTypeNames} limits={settings.limits} reason={reason}
      onSelect={id => { setSelectedId(id); setView('node'); if (mobile) setMobileNode(id); }} />}
    {run?.status === 'failed' && hasResults && requestError && <Alert type="error" showIcon className="dq-banner" title={t('joint.run.interrupted', { message: requestError })}
      action={<Button size="small" onClick={() => start(run.entry.keyType, run.entry.values.join('\n'))}>{t('joint.run.again')}</Button>} />}
    {!hasResults && !running && <EmptyState settings={settings} colors={colors} finished={!!run && run.status !== 'failed'} onCoverage={() => setCoverageOpen(true)} canCoverage={!!keyType} />}
    {hasResults && !mobile && <section className="dq-panel jq-overview">
      <JointGraph lanes={lanes} edges={edges} selectedId={selected?.id ?? null} path={path} rowCount={rowCount} edgeTitle={edgeTitle} nodeTitle={nodeTitle}
        onSelect={id => { setSelectedId(id); setView('node'); }} />
    </section>}
    {hasResults && mobile && <MobileResults lanes={lanes} rowCount={rowCount} graphOpen={mobileGraph} onToggleGraph={() => setMobileGraph(open => !open)}
      graph={<JointGraph lanes={lanes} edges={edges} selectedId={selected?.id ?? null} path={path} rowCount={rowCount} edgeTitle={edgeTitle} nodeTitle={nodeTitle}
        onSelect={id => { setSelectedId(id); setMobileNode(id); }} />}
      onSelect={id => { setSelectedId(id); setMobileNode(id); }} />}
    {hasResults && !mobile && <div className="dq-panel jq-detail-card" ref={detailRef}>
      {view === 'node' && selected
        ? detailOf(selected, <DetailSwitch view={view} total={total} onChange={setView} />)
        : <>
          <div className="jq-all-head"><strong>{t('joint.detail.allTitle', { count: total })}</strong><DetailSwitch view={view} total={total} onChange={setView} /></div>
          {ordered.map(node => detailOf(node))}
        </>}
    </div>}
    {mobile && <Drawer open={!!mobileNode && !!selected} placement="bottom" size="88%" rootClassName="dq-modal jq-mobile-detail" title={null}
      onClose={() => setMobileNode(null)} destroyOnHidden>
      {selected && detailOf(selected)}
    </Drawer>}
    {coverageOpen && keyType && <JointCoverage keyType={keyType} colors={colors} keyTypeName={code => keyTypeNames.get(code) ?? null} onClose={() => setCoverageOpen(false)} />}
  </div>;
}

/** “另有 N 个视图未执行 · 查看完整信息”；查看后在本节点明细内依次列出这些视图的结果。 */
function SkippedViews({ count, state, onOpen, render }: {
  count: number; state: ViewsState | undefined; onOpen: () => void; render: (node: RunNode) => ReactNode;
}) {
  const t = useT('data-query');
  return <div className="jq-views">
    {!state?.nodes && <div className="jq-views-line">
      <span className="muted">{t('joint.views.more', { count })}</span>
      <Button type="link" size="small" loading={state?.loading} onClick={onOpen}>{t('joint.views.open')}</Button>
    </div>}
    {state?.error && <div className="dq-text-error jq-views-line">{state.error}</div>}
    {state?.nodes && <>
      <div className="jq-views-head">{t('joint.views.title', { count: state.nodes.length })}<span className="muted">{t('joint.views.hint')}</span></div>
      {state.nodes.length ? state.nodes.map(render) : <div className="muted jq-views-line">{t('joint.views.empty')}</div>}
    </>}
  </div>;
}

function Summary({ run, total, groups, running, issues, issuesOpen, onToggleIssues, clients, client, onClient, limitsSeconds }: {
  run: JointRunState; total: number; groups: number; running: boolean; issues: readonly Issue[]; issuesOpen: boolean; onToggleIssues: () => void;
  clients: readonly string[]; client: string | null; onClient: (client: string | null) => void; limitsSeconds: JointSettings['limits'];
}) {
  const t = useT('data-query');
  const seconds = (run.elapsedMillis / 1000).toFixed(1);
  const limited = run.stop && run.stop.rule !== 'USER_STOPPED';
  const note = running ? t('joint.summary.runningNote', { depth: run.depth + 1 }) : run.stop?.rule === 'USER_STOPPED' ? t('joint.summary.stopped')
    : limited ? t('joint.summary.limited', { reason: stopReason(t, run.stop!.rule, limitsSeconds) }) : t('joint.summary.replicaNote');
  return <div className="jq-summary" aria-live="polite">
    <div className="jq-summary-main">
      {running && <Spin size="small" />}
      <strong>{total ? t('joint.summary.found', { count: total, groups }) : t('joint.summary.searching')}</strong>
      {issues.length > 0 && <button type="button" className="jq-issues-chip" onClick={onToggleIssues} aria-expanded={issuesOpen}>
        {t('joint.issues.chip', { count: issues.length })} {issuesOpen ? '▴' : '▾'}</button>}
      <span className="muted">{t('joint.summary.seconds', { seconds })} · {note}</span>
    </div>
    {clients.length > 1 && <div className="jq-clients">
      <span className="muted">{t('joint.clients.hit', { count: clients.length })}</span>
      <button type="button" className={`jq-client${client === null ? ' is-active' : ''}`} onClick={() => onClient(null)}>{t('joint.clients.all')}</button>
      {clients.map(value => <button key={value} type="button" className={`jq-client mono${client === value ? ' is-active' : ''}`} onClick={() => onClient(value)}>{value}</button>)}
    </div>}
  </div>;
}

function stopReason(t: ReturnType<typeof useT>, rule: string, limits: JointSettings['limits']): string {
  switch (rule) {
    case 'MAX_DEPTH': return t('joint.stop.MAX_DEPTH', { count: limits.maxDepth });
    case 'MAX_TEMPLATE_EXECUTIONS': return t('joint.stop.MAX_TEMPLATE_EXECUTIONS', { count: limits.maxTemplateExecutions });
    case 'MAX_TOTAL_SECONDS': return t('joint.stop.MAX_TOTAL_SECONDS', { count: limits.maxTotalSeconds });
    case 'STEP_EXECUTION_LIMIT': return t('joint.stop.STEP_EXECUTION_LIMIT', { count: limits.maxExecutionsPerStep });
    case 'STEP_BUDGET': case 'STEP_BUDGET_EXHAUSTED': return t('joint.stop.STEP_BUDGET');
    case 'EXECUTOR_BUSY': return t('joint.stop.EXECUTOR_BUSY');
    case 'USER_STOPPED': return t('joint.stop.USER_STOPPED');
    default: return t('joint.stop.notQueried');
  }
}

/** 未查全展开行（原型 03）：失败 / 部分 / 未查；点击失败或未查的节点在下方查看。 */
function IssueList({ issues, nodes, keyTypeNames, limits, reason, onSelect }: {
  issues: readonly Issue[]; nodes: readonly RunNode[]; keyTypeNames: ReadonlyMap<string, string>; limits: JointSettings['limits'];
  reason: (node: RunNode) => string; onSelect: (nodeId: string) => void;
}) {
  const t = useT('data-query');
  const byId = new Map(nodes.map(node => [node.id, node]));
  const keyName = (code: string | null) => (code && keyTypeNames.get(code)) || t('joint.issues.someKey');
  return <div className="jq-issues">{issues.map((issue, index) => {
    const named = issue.nodeIds.map(id => byId.get(id)).filter((node): node is RunNode => !!node);
    let text: string;
    if (issue.kind === 'failed' && named[0]) text = `${named[0].templateName}${t('joint.colon')}${reason(named[0])}`;
    else if (issue.kind === 'partial') text = t('joint.issues.partial', { name: keyName(issue.keyType), total: limits.maxValuesPerKey + (issue.dropped ?? 0), kept: limits.maxValuesPerKey });
    else if (named.length) text = `${named.map(node => node.templateName).join(t('joint.listSeparator'))}${t('joint.colon')}${stopReason(t, issue.rule ?? '', limits)}`;
    else text = t('joint.issues.notExpanded', { reason: stopReason(t, issue.rule ?? '', limits) });
    return <div key={index} className={`jq-issue is-${issue.kind}`}>
      <span className="jq-issue-kind">{t(`joint.issues.kind.${issue.kind}`)}</span>
      {named.length ? <button type="button" className="jq-issue-text" onClick={() => onSelect(named[0].id)}>{text}</button> : <span className="jq-issue-text">{text}</span>}
    </div>;
  })}</div>;
}

function EmptyState({ settings, colors, finished, canCoverage, onCoverage }: {
  settings: JointSettings; colors: ReadonlyMap<string, string>; finished: boolean; canCoverage: boolean; onCoverage: () => void;
}) {
  const t = useT('data-query');
  const examples = settings.keyTypes.slice(0, 4).map(item => item.keyTypeName).join(t('joint.listSeparator'));
  return <section className="dq-panel jq-empty">
    {settings.groups.length > 0 && <div className="jq-chips">{settings.groups.map((group, index) => <span key={group.groupName} className="jq-chip-wrap">
      {index > 0 && <span className="jq-chip-sep" aria-hidden>—</span>}
      <span className="jq-chip"><span className="jq-dot" style={{ background: colors.get(group.groupName) ?? '#a0aec0' }} />{group.groupName}</span></span>)}</div>}
    <h3>{finished ? t('joint.empty.nothingTitle') : t('joint.empty.title')}</h3>
    <p className="muted">{finished ? t('joint.empty.nothingHint') : t('joint.empty.hint', { examples })}</p>
    {canCoverage && <button type="button" className="dq-link" onClick={onCoverage}>{t('joint.empty.coverage')}</button>}
  </section>;
}

/** 390 宽（原型 06）：按分组折叠的卡片，点行查看明细；“查看链路图”切换画布。 */
function MobileResults({ lanes, rowCount, graphOpen, onToggleGraph, graph, onSelect }: {
  lanes: ReturnType<typeof buildLanes>; rowCount: (node: RunNode) => number; graphOpen: boolean; onToggleGraph: () => void;
  graph: ReactNode; onSelect: (nodeId: string) => void;
}) {
  const t = useT('data-query');
  const [open, setOpen] = useState<ReadonlySet<string>>(() => new Set(lanes.slice(0, 2).map(lane => lane.groupName)));
  return <div className="jq-mobile">
    {graphOpen && <div className="dq-panel jq-mobile-graph">{graph}</div>}
    {lanes.map(lane => {
      const expanded = open.has(lane.groupName);
      return <section key={lane.groupName} className="dq-panel jq-mobile-lane">
        <button type="button" className="jq-mobile-lane-head" aria-expanded={expanded}
          onClick={() => setOpen(current => { const next = new Set(current); if (next.has(lane.groupName)) next.delete(lane.groupName); else next.add(lane.groupName); return next; })}>
          <span className="jq-dot" style={{ background: lane.color }} /><strong>{lane.groupName}</strong>
          <span className="muted">{t('joint.mobile.items', { count: lane.nodes.length })} {expanded ? '▴' : '▾'}</span></button>
        {expanded && lane.nodes.map(node => <button type="button" key={node.id} className={`jq-mobile-row${node.depth === 0 ? ' is-entry' : ''}`} onClick={() => onSelect(node.id)}>
          <span>{node.templateName}</span><span className="jq-mobile-badge"><BadgeText badge={nodeBadge(node, rowCount(node))} /> ›</span></button>)}
      </section>;
    })}
    <button type="button" className="dq-link jq-mobile-toggle" onClick={onToggleGraph}>{graphOpen ? t('joint.mobile.hideGraph') : t('joint.mobile.showGraph')}</button>
  </div>;
}
