import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Dropdown, Empty, Form, Input, Modal, Segmented, Spin, Tag } from 'antd';
import type { ReactNode } from 'react';
import { CaretRightOutlined, EditOutlined, EllipsisOutlined, PlusOutlined } from '@ant-design/icons';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { requestAllPages } from '../api/paging';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { DefinitionEditor } from '../workflow/DefinitionEditor';
import {
  cardMenuItems, definitionCardStatus, filterDefinitions, loadThumbnail, monthDay, primaryCardAction, thumbnailRequestId,
} from '../workflow/definitionModel';
import type { CardAction, DefinitionFilter } from '../workflow/definitionModel';
import { autoLayout } from '../workflow/graphModel';
import { needsLayout } from '../workflow/fileModel';
import { LaunchModal } from '../workflow/LaunchModal';
import { DETAIL_PRIORITY, DetailQueue, DetailRequestCancelled, retryAfterMillis } from '../workflow/prefetchModel';
import { templateCodePattern } from '../workflow/templateModel';
import { failureText, sendWorkflowCommand, useWorkflowCommand, workflowCommand } from '../workflow/useWorkflowCommand';
import { decodeDefinitionDetail, decodeDefinitionSummary, decodeMutation, decodePage, decodeVersion, workflowPaths } from '../workflow/workflowApi';
import type { DefinitionDetail, DefinitionSummary, WorkflowGraph } from '../workflow/workflowTypes';
import { canOpenInstances, hasButton, shortTime, workflowButtons, workflowRoutes } from '../workflow/WorkflowParts';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../workflow/workflow.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const decodeDefinitions = decodePage(decodeDefinitionSummary);

/**
 * 缩略图与版本历史的读取共用一个串行队列（technique 第 7 节 S7-02：并发读详情触发 429）：同时最多 1 个在途，
 * 缩略图按预取优先级、进入可视区才排队；版本历史按选中优先级插队；429 按默认退避重试一次。
 */
type QueueValue = WorkflowGraph | null | DefinitionDetail;
const thumbnailCache = new Map<string, WorkflowGraph | null>();
const summaries = new Map<string, DefinitionSummary>();
const definitionQueue = new DetailQueue<QueueValue>(async id => {
  if (id.startsWith('detail:')) return session.request(workflowPaths.definition(id.slice(7)), {}, decodeDefinitionDetail);
  const summary = summaries.get(id);
  if (!summary) return null;
  const graph = await loadThumbnail(summary, {
    detail: definitionId => session.request(workflowPaths.definition(definitionId), {}, decodeDefinitionDetail),
    version: (definitionId, versionNo) => session.request(workflowPaths.version(definitionId, versionNo), {}, decodeVersion),
  });
  thumbnailCache.set(id, graph);
  return graph;
}, { retryDelay: error => error instanceof ApiError && error.status === 429 ? retryAfterMillis(null) : null });

/** 流程定义：卡片列表；/:definitionId 进入编辑器，/:definitionId/versions/:versionNo 只读查看（原型 09—12）。 */
export function WorkflowDefinitions({ resources, onAuthorizationChanged }: Props) {
  const { definitionId, versionNo } = useParams();
  const canManage = hasButton(resources, workflowButtons.definitionManage);
  const canManageTemplates = hasButton(resources, workflowButtons.templateManage);
  if (definitionId) return <DefinitionEditor key={`${definitionId}:${versionNo ?? ''}`} definitionId={definitionId} versionNo={versionNo ? Number(versionNo) : undefined}
    canManage={canManage} canManageTemplates={canManageTemplates} canReadInstances={hasButton(resources, workflowButtons.instanceReadAll)}
    onAuthorizationChanged={onAuthorizationChanged} />;
  return <DefinitionList resources={resources} canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} />;
}

const filters: DefinitionFilter[] = ['ALL', 'PUBLISHED', 'DRAFT', 'INACTIVE'];

function DefinitionList({ resources, canManage, onAuthorizationChanged }: Props & { canManage: boolean }) {
  const t = useT('workflow'), tNav = useT('nav');
  const navigate = useNavigate();
  const [items, setItems] = useState<DefinitionSummary[] | null>(null); const [failure, setFailure] = useState<unknown>();
  const [reload, setReload] = useState(0);
  const [filter, setFilter] = useState<DefinitionFilter>('ALL'); const [keyword, setKeyword] = useState('');
  const [creating, setCreating] = useState(false); const [launching, setLaunching] = useState<string | null>(null);
  const [versionsOf, setVersionsOf] = useState<DefinitionSummary | null>(null);
  const permissions = { canStart: hasButton(resources, workflowButtons.instanceStart), canManage };
  const instancesOpen = canOpenInstances(resources);
  useEffect(() => {
    const controller = new AbortController(); setFailure(undefined);
    requestAllPages(workflowPaths.definitions, controller.signal, undefined, {}, decodeDefinitions)
      .then(value => { if (!controller.signal.aborted) setItems(value); })
      .catch(error => { if (!controller.signal.aborted) setFailure(error); });
    return () => controller.abort();
  }, [reload]);
  const { visible, counts } = useMemo(() => filterDefinitions(items ?? [], filter, keyword), [items, filter, keyword]);
  const refresh = useCallback(() => setReload(value => value + 1), []);
  return <div className="wf-page">
    <div className="wf-page-head"><h1>{tNav('resource.OPERATIONS_WORKFLOW_DEFINITIONS')}</h1><span className="wf-grow" />
      <Input.Search allowClear placeholder={t('definitions.search')} style={{ width: 260 }} value={keyword} onChange={event => setKeyword(event.target.value)} />
      {canManage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>{t('definitions.create')}</Button>}
    </div>
    <div className="wf-page-head">
      <Segmented value={filter} onChange={value => setFilter(value as DefinitionFilter)}
        options={filters.map(value => ({ value, label: <span>{enumLabel(t, 'definitions.filter', value)} <span className="muted">{counts[value]}</span></span> }))} />
      <span className="wf-grow" /><span className="muted">{t('definitions.sorted')}</span>
    </div>
    {failure !== undefined && <Alert type="warning" showIcon title={t('editor.loadFailed')} action={<Button size="small" onClick={refresh}>{t('action.retry')}</Button>} />}
    {items === null && failure === undefined ? <Spin /> : !visible.length ? <Empty description={items?.length ? t('definitions.noMatch') : t('definitions.empty')} />
      : <div className="wf-card-grid wf-def-grid">
        {visible.map(item => <DefinitionCard key={item.definitionId} summary={item} permissions={permissions}
          onAction={action => {
            if (action === 'LAUNCH') setLaunching(item.definitionId);
            else void navigate(`${workflowRoutes.definitions}/${item.definitionId}`);
          }}
          onVersions={() => setVersionsOf(item)} onAuthorizationChanged={onAuthorizationChanged} onChanged={refresh} />)}
      </div>}
    {creating && <CreateDefinitionModal onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={id => void navigate(`${workflowRoutes.definitions}/${id}`)} />}
    {launching && <LaunchModal initialDefinitionId={launching} onClose={() => setLaunching(null)} onAuthorizationChanged={onAuthorizationChanged}
      onStarted={id => { setLaunching(null); void navigate(instancesOpen ? `${workflowRoutes.instances}/${id}` : workflowRoutes.started); }} />}
    {versionsOf && <VersionHistoryModal summary={versionsOf} onClose={() => setVersionsOf(null)} />}
  </div>;
}

const actionButtonIcons: Partial<Record<CardAction, ReactNode>> = { LAUNCH: <CaretRightOutlined />, CONTINUE: <EditOutlined /> };

/** 卡片：缩略图（进入可视区才排队读取）、一个状态、更新于、一个主按钮与「⋯」菜单（原型 09）。 */
function DefinitionCard({ summary, permissions, onAction, onVersions, onAuthorizationChanged, onChanged }: {
  summary: DefinitionSummary; permissions: { canStart: boolean; canManage: boolean }; onAction: (action: CardAction) => void;
  onVersions: () => void; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('workflow');
  const { message, modal } = App.useApp();
  const status = definitionCardStatus(summary);
  const primary = primaryCardAction(summary, permissions);
  const menu = cardMenuItems(summary, permissions);
  function changeStatus(next: 'ACTIVE' | 'INACTIVE') {
    modal.confirm({ title: next === 'ACTIVE' ? t('definitions.enableTitle', { name: summary.definitionName }) : t('definitions.disableTitle', { name: summary.definitionName }),
      content: next === 'INACTIVE' ? t('definitions.disableContent') : t('definitions.enableContent'),
      okText: next === 'ACTIVE' ? t('action.enable') : t('action.disable'), cancelText: t('action.cancel'), okButtonProps: { danger: next === 'INACTIVE' },
      onOk: async () => {
        try {
          await sendWorkflowCommand(workflowCommand(workflowPaths.definitionStatus(summary.definitionId), 'POST', { status: next, expectedVersion: summary.version }), decodeMutation);
          void message.success(next === 'ACTIVE' ? t('templateEditor.enabled') : t('templateEditor.disabled')); onChanged();
        } catch (error) {
          if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
          void message.error(failureText(error));
        }
      } });
  }
  return <section className={`wf-def-card${status.kind === 'INACTIVE' ? ' is-inactive' : ''}`}>
    <LazyThumbnail summary={summary} />
    {status.kind === 'DRAFT_ONLY' && <span className="wf-def-draft-mark">{t('definitions.draft')}</span>}
    <div className="wf-def-body">
      <div className="wf-def-title"><strong title={summary.definitionName}>{summary.definitionName}</strong>
        <span className={`wf-status-pill is-${status.tone}`}>{status.label}</span></div>
      <div className="muted wf-def-meta">{t('definitions.updatedAt', { date: monthDay(summary.updateTime) })}{summary.description ? ` · ${summary.description}` : ''}</div>
      <div className="wf-def-actions">
        <Dropdown trigger={['click']} menu={{ items: [
          ...(menu.includes('EDIT') ? [{ key: 'EDIT', label: permissions.canManage ? t('catalog.edit') : t('files.view') }] : []),
          { key: 'VERSIONS', label: t('definitions.versions') },
          ...(menu.includes('DISABLE') ? [{ type: 'divider' as const }, { key: 'DISABLE', label: t('action.disable'), danger: true }] : []),
        ],
          onClick: ({ key }) => key === 'EDIT' ? onAction('EDIT') : key === 'VERSIONS' ? onVersions() : key === 'DISABLE' ? changeStatus('INACTIVE') : undefined }}>
          <Button type="text" aria-label={t('catalog.moreActions')} icon={<EllipsisOutlined />} />
        </Dropdown>
        <Button type={primary === 'ENABLE' || primary === 'VIEW' ? 'default' : 'primary'}
          icon={actionButtonIcons[primary]} onClick={() => primary === 'ENABLE' ? changeStatus('ACTIVE') : onAction(primary)}>{enumLabel(t, 'definitions.action', primary)}</Button>
      </div>
    </div>
  </section>;
}

/** 缩略图：进入可视区后经串行队列读取；失败显示占位与重试。 */
function LazyThumbnail({ summary }: { summary: DefinitionSummary }) {
  const t = useT('workflow');
  const id = thumbnailRequestId(summary);
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);
  const [state, setState] = useState<{ graph?: WorkflowGraph | null; failed?: boolean }>(() => thumbnailCache.has(id) ? { graph: thumbnailCache.get(id) } : {});
  const [attempt, setAttempt] = useState(0);
  summaries.set(id, summary);
  useEffect(() => {
    const element = ref.current;
    if (!element || visible) return;
    if (typeof IntersectionObserver === 'undefined') { setVisible(true); return; }
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) setVisible(true); }, { rootMargin: '120px' });
    observer.observe(element);
    return () => observer.disconnect();
  }, [visible]);
  useEffect(() => {
    if (!visible || thumbnailCache.has(id)) { if (thumbnailCache.has(id)) setState({ graph: thumbnailCache.get(id) }); return; }
    const tag = `thumb:${id}:${attempt}`;
    let active = true;
    setState({});
    definitionQueue.request(id, DETAIL_PRIORITY.PREFETCH, tag)
      .then(value => { if (active) setState({ graph: value as WorkflowGraph | null }); })
      .catch(error => { if (active && !(error instanceof DetailRequestCancelled)) setState({ failed: true }); });
    return () => { active = false; definitionQueue.cancel(tag); };
  }, [visible, id, attempt]);
  return <div ref={ref} className="wf-def-thumb">
    {state.failed ? <div className="wf-def-thumb-empty">{t('definitions.thumbFailed')} <Button type="link" size="small" onClick={() => setAttempt(value => value + 1)}>{t('action.retry')}</Button></div>
      : state.graph ? <GraphThumbnail graph={state.graph} />
      : state.graph === null ? <div className="wf-def-thumb-empty">{t('definitions.noDiagram')}</div>
      : <div className="wf-def-thumb-empty is-loading" />}
  </div>;
}

/** 缩略图：开始绿圈、结束红圈、任务圆角块、网关菱形，连线为折线。 */
function GraphThumbnail({ graph: source }: { graph: WorkflowGraph }) {
  // 新建草稿的节点没有位置（全部重叠），先自动排列再画。
  const t = useT('workflow');
  const graph = useMemo(() => needsLayout(source) ? autoLayout(source) : source, [source]);
  if (!graph.nodes.length) return <div className="wf-def-thumb-empty">{t('definitions.noDiagram')}</div>;
  const xs = graph.nodes.map(node => node.position.x); const ys = graph.nodes.map(node => node.position.y);
  const minX = Math.min(...xs); const minY = Math.min(...ys);
  const width = Math.max(1, Math.max(...xs) - minX); const height = Math.max(1, Math.max(...ys) - minY);
  const scale = Math.min(300 / width, 70 / height, 0.6);
  const offsetX = (360 - width * scale) / 2; const offsetY = (100 - height * scale) / 2;
  const at = (point: { x: number; y: number }) => ({ x: offsetX + (point.x - minX) * scale, y: offsetY + (point.y - minY) * scale });
  const position = new Map(graph.nodes.map(node => [node.key, at(node.position)]));
  return <svg className="wf-thumb-svg" viewBox="0 0 360 100" aria-hidden="true">
    {graph.edges.map(edge => { const from = position.get(edge.from); const to = position.get(edge.to);
      if (!from || !to) return null;
      const middle = (from.x + to.x) / 2;
      return <polyline key={edge.key} points={`${from.x},${from.y} ${middle},${from.y} ${middle},${to.y} ${to.x},${to.y}`} fill="none" stroke="#c9d1de" strokeWidth={1} />; })}
    {graph.nodes.map(node => { const point = position.get(node.key)!;
      if (node.type === 'TASK') return <rect key={node.key} x={point.x - 14} y={point.y - 6} width={28} height={12} rx={3} fill="#fff" stroke="#b7c4dc" />;
      if (node.type === 'START' || node.type === 'END') return <circle key={node.key} cx={point.x} cy={point.y} r={5} fill="#fff" stroke={node.type === 'START' ? '#1f8a4c' : '#c43c3c'} strokeWidth={1.4} />;
      return <rect key={node.key} x={point.x - 5} y={point.y - 5} width={10} height={10} fill="#fffaf0" stroke={node.type === 'EXCLUSIVE' ? '#d08a17' : '#7c5cd6'}
        transform={`rotate(45 ${point.x} ${point.y})`} />; })}
  </svg>;
}

/** 版本历史：草稿与已发布版本，按选中优先级经同一队列读取定义详情。 */
function VersionHistoryModal({ summary, onClose }: { summary: DefinitionSummary; onClose: () => void }) {
  const t = useT('workflow');
  const [detail, setDetail] = useState<DefinitionDetail>(); const [failed, setFailed] = useState(false); const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const tag = `versions:${summary.definitionId}:${attempt}`; let active = true; setFailed(false);
    definitionQueue.request(`detail:${summary.definitionId}`, DETAIL_PRIORITY.SELECTED, tag)
      .then(value => { if (active) setDetail(value as DefinitionDetail); })
      .catch(error => { if (active && !(error instanceof DetailRequestCancelled)) setFailed(true); });
    return () => { active = false; definitionQueue.cancel(tag); };
  }, [summary.definitionId, attempt]);
  const versions = (detail?.versions ?? []).filter(version => version.status === 'PUBLISHED').sort((left, right) => right.versionNo - left.versionNo);
  const base = `${workflowRoutes.definitions}/${summary.definitionId}`;
  return <Modal open title={t('definitions.versionsTitle', { name: summary.definitionName })} footer={null} onCancel={onClose}>
    {failed ? <Alert type="warning" showIcon title={t('definitions.versionsFailed')} action={<Button size="small" onClick={() => setAttempt(value => value + 1)}>{t('action.retry')}</Button>} />
      : !detail ? <Spin /> : <div className="wf-versions">
        {detail.draft && <div><strong>v{detail.draft.versionNo}</strong><Tag color="orange">{t('definitions.draft')}{detail.problems.length ? ` · ${t('editor.problemCount', { count: detail.problems.length })}` : ''}</Tag>
          <span className="muted">{t('definitions.editedAt', { time: shortTime(detail.draft.updateTime) })}</span><span className="wf-grow" /><Link to={base}>{t('definitions.action.CONTINUE')}</Link></div>}
        {versions.map(version => <div key={version.versionNo}><strong>v{version.versionNo}</strong>
          <Tag color={version.versionNo === summary.currentVersionNo ? 'green' : 'default'}>{version.versionNo === summary.currentVersionNo ? t('references.current') : t('definitions.historical')}</Tag>
          <span className="muted">{t('definitions.publishedAt', { time: shortTime(version.publishedTime) })}</span><span className="wf-grow" />
          <Link to={`${base}/versions/${version.versionNo}`}>{t('files.view')}</Link></div>)}
        {!versions.length && !detail.draft && <span className="muted">{t('definitions.noVersions')}</span>}
      </div>}
  </Modal>;
}

function CreateDefinitionModal({ onClose, onCreated, onAuthorizationChanged }: { onClose: () => void; onCreated: (id: string) => void; onAuthorizationChanged: () => void }) {
  const t = useT('workflow');
  const [form] = Form.useForm<{ definitionCode: string; definitionName: string; description?: string }>();
  const command = useWorkflowCommand(onAuthorizationChanged);
  async function submit(values: { definitionCode: string; definitionName: string; description?: string }) {
    const result = await command.run(workflowPaths.definitions, 'POST', { definitionCode: values.definitionCode, definitionName: values.definitionName.trim(),
      ...(values.description?.trim() ? { description: values.description.trim() } : {}) }, decodeMutation, t('templateEditor.createFailed'));
    if (result) onCreated(result.objectId);
  }
  return <Modal open title={t('definitions.create')} okText={t('definitions.createAndEdit')} cancelText={t('action.cancel')} okButtonProps={{ loading: command.saving }} onCancel={onClose} onOk={() => form.submit()}>
    <p className="muted">{t('definitions.createHint')}</p>
    {command.formError && <Alert type="error" showIcon title={command.formError} />}
    <Form form={form} layout="vertical" onFinish={values => void submit(values)}>
      <Form.Item name="definitionCode" label={t('editor.code')} extra={t('definitions.codeHint')} normalize={(value?: string) => value?.toUpperCase()}
        rules={[{ required: true, message: t('definitions.codeRequired') }, { pattern: templateCodePattern, message: t('definitions.codePattern') }]}><Input maxLength={64} /></Form.Item>
      <Form.Item name="definitionName" label={t('editor.name')} rules={[{ required: true, whitespace: true, message: t('definitions.nameRequired') }]}><Input maxLength={128} /></Form.Item>
      <Form.Item name="description" label={t('editor.description')}><Input.TextArea rows={3} maxLength={512} /></Form.Item>
    </Form>
  </Modal>;
}
