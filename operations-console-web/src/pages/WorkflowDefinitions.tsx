import { useEffect, useMemo, useState } from 'react';
import { Alert, App, Button, Empty, Form, Input, Modal, Pagination, Segmented, Space, Tag } from 'antd';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { DefinitionEditor } from '../workflow/DefinitionEditor';
import { autoLayout, publishedBaseVersionNo } from '../workflow/graphModel';
import { needsLayout } from '../workflow/fileModel';
import { templateCodePattern } from '../workflow/templateModel';
import { failureText, sendWorkflowCommand, useWorkflowCommand, workflowCommand } from '../workflow/useWorkflowCommand';
import { decodeDefinitionDetail, decodeDefinitionSummary, decodeMutation, decodePage, decodeVersion, workflowPaths } from '../workflow/workflowApi';
import { configStatusLabels } from '../workflow/workflowLabels';
import type { DefinitionDetail, DefinitionSummary, WorkflowGraph } from '../workflow/workflowTypes';
import { hasButton, shortTime, workflowButtons, workflowRoutes } from '../workflow/WorkflowParts';
import '../workflow/workflow.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const decodeDefinitions = decodePage(decodeDefinitionSummary);

/** 流程定义：卡片列表与版本；/:definitionId 进入编辑器，/:definitionId/versions/:versionNo 只读查看（原型 07—11）。 */
export function WorkflowDefinitions({ resources, onAuthorizationChanged }: Props) {
  const { definitionId, versionNo } = useParams();
  const canManage = hasButton(resources, workflowButtons.definitionManage);
  if (definitionId) return <DefinitionEditor key={`${definitionId}:${versionNo ?? ''}`} definitionId={definitionId} versionNo={versionNo ? Number(versionNo) : undefined}
    canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} />;
  return <DefinitionList resources={resources} canManage={canManage} onAuthorizationChanged={onAuthorizationChanged} />;
}

function DefinitionList({ resources, canManage, onAuthorizationChanged }: Props & { canManage: boolean }) {
  const navigate = useNavigate();
  const [status, setStatus] = useState<'ACTIVE' | 'INACTIVE' | 'ALL'>('ACTIVE');
  const [keyword, setKeyword] = useState('');
  const paging = usePaging<DefinitionSummary>(workflowPaths.definitions, decodeDefinitions, { status: 'ACTIVE' });
  const [creating, setCreating] = useState(false);
  const canStart = hasButton(resources, workflowButtons.instanceStart);
  function apply(nextStatus = status, nextKeyword = keyword) {
    paging.applyFilters({ ...(nextStatus === 'ALL' ? {} : { status: nextStatus }), ...(nextKeyword.trim() ? { keyword: nextKeyword.trim() } : {}) });
  }
  return <div className="wf-page">
    <div className="wf-page-head"><h1>流程定义</h1>
      <Segmented value={status} onChange={value => { setStatus(value as typeof status); apply(value as typeof status); }}
        options={[{ value: 'ACTIVE', label: '启用' }, { value: 'INACTIVE', label: '停用' }, { value: 'ALL', label: '全部' }]} />
      <Input.Search allowClear placeholder="编码或名称" style={{ width: 240 }} value={keyword} onChange={event => setKeyword(event.target.value)} onSearch={value => apply(status, value)} />
      <span className="wf-grow" />
      <Link to={workflowRoutes.templates}><Button>动作目录</Button></Link>
      {canManage && <Button type="primary" onClick={() => setCreating(true)}>＋ 新建流程</Button>}
    </div>
    {paging.error && <Alert type="warning" showIcon title="流程定义加载失败" action={<Button size="small" onClick={paging.refresh}>重试</Button>} />}
    {!paging.loading && !paging.data.items.length ? <Empty description="还没有流程定义" /> : <div className="wf-card-grid">
      {paging.data.items.map(item => <DefinitionCard key={item.definitionId} summary={item} canManage={canManage} canStart={canStart}
        onAuthorizationChanged={onAuthorizationChanged} onChanged={paging.refresh} />)}
    </div>}
    {paging.data.total > paging.data.size && <Pagination {...paging.pagination} />}
    {creating && <CreateDefinitionModal onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={id => void navigate(`${workflowRoutes.definitions}/${id}`)} />}
  </div>;
}

/** 卡片：缩略图、版本标签、启停、版本列表（详情按卡片懒加载）。 */
function DefinitionCard({ summary, canManage, canStart, onAuthorizationChanged, onChanged }: {
  summary: DefinitionSummary; canManage: boolean; canStart: boolean; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const navigate = useNavigate(); const { message, modal } = App.useApp();
  const [detail, setDetail] = useState<DefinitionDetail>();
  const [showVersions, setShowVersions] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    session.request(workflowPaths.definition(summary.definitionId), { signal: controller.signal }, decodeDefinitionDetail)
      .then(value => { if (!controller.signal.aborted) setDetail(value); }).catch(() => undefined);
    return () => controller.abort();
  }, [summary.definitionId, summary.version]);
  // 只有发布版本时，缩略图按接口 12 读取一次该版本（仅当前页卡片，且只在没有草稿时）。
  const baseVersionNo = detail ? publishedBaseVersionNo(detail) : undefined;
  const [published, setPublished] = useState<{ versionNo: number; graph: WorkflowGraph | null }>();
  useEffect(() => {
    if (baseVersionNo === undefined) return;
    const controller = new AbortController();
    session.request(workflowPaths.version(summary.definitionId, baseVersionNo), { signal: controller.signal }, decodeVersion)
      .then(value => { if (!controller.signal.aborted) setPublished({ versionNo: baseVersionNo, graph: value.graph }); })
      .catch(() => { if (!controller.signal.aborted) setPublished({ versionNo: baseVersionNo, graph: null }); });
    return () => controller.abort();
  }, [summary.definitionId, baseVersionNo]);
  const graph = detail?.draft?.graph ?? (published?.versionNo === baseVersionNo ? published?.graph ?? undefined : undefined);
  function toggleStatus() {
    const next = summary.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    modal.confirm({ title: `${next === 'ACTIVE' ? '启用' : '停用'}“${summary.definitionName}”？`,
      content: next === 'INACTIVE' ? '停用后不能发起新实例；在途实例照常运行到结束。' : '启用后，已发布的当前版本可以发起。',
      okText: next === 'ACTIVE' ? '启用' : '停用', okButtonProps: { danger: next === 'INACTIVE' },
      onOk: async () => {
        try {
          await sendWorkflowCommand(workflowCommand(workflowPaths.definitionStatus(summary.definitionId), 'POST', { status: next, expectedVersion: summary.version }), decodeMutation);
          void message.success(`已${next === 'ACTIVE' ? '启用' : '停用'}`); onChanged();
        } catch (error) {
          if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
          void message.error(failureText(error));
        }
      } });
  }
  const versions = (detail?.versions ?? []).slice().sort((left, right) => right.versionNo - left.versionNo);
  return <section className={`wf-card wf-definition-card${summary.status === 'INACTIVE' ? ' is-inactive' : ''}`}>
    <GraphThumbnail graph={graph} />
    <div className="wf-definition-title"><strong>{summary.definitionName}</strong>
      {summary.currentVersionNo ? <Tag color="green">v{summary.currentVersionNo}</Tag> : <Tag>未发布</Tag>}
      {summary.draftVersionNo && <Tag color="orange">v{summary.draftVersionNo} 草稿{detail?.problems.length ? ` · ${detail.problems.length} 个问题` : ''}</Tag>}
      {summary.status === 'INACTIVE' && <Tag>{configStatusLabels.INACTIVE}</Tag>}</div>
    <div className="muted">{summary.definitionCode}{summary.description ? ` · ${summary.description}` : ''}</div>
    <div className="muted">更新 {shortTime(summary.updateTime)}</div>
    <Space className="wf-definition-actions">
      <Button size="small" onClick={() => void navigate(`${workflowRoutes.definitions}/${summary.definitionId}`)}>{canManage ? '编辑' : '查看'}</Button>
      {canStart && summary.status === 'ACTIVE' && summary.currentVersionNo && <Button size="small"
        onClick={() => void navigate(`${workflowRoutes.instances}?launch=${summary.definitionId}`)}>发起</Button>}
      {canManage && <Button size="small" danger={summary.status === 'ACTIVE'} onClick={toggleStatus}>{summary.status === 'ACTIVE' ? '停用' : '启用'}</Button>}
      <Button size="small" type={showVersions ? 'primary' : 'default'} ghost={showVersions} onClick={() => setShowVersions(value => !value)}>版本</Button>
    </Space>
    {showVersions && <div className="wf-versions">
      {detail?.draft && <div><strong>v{detail.draft.versionNo}</strong><Tag color="orange">草稿{detail.problems.length ? ` · ${detail.problems.length} 个问题` : ''}</Tag>
        <span className="muted">编辑于 {shortTime(detail.draft.updateTime)}</span>
        <Link to={`${workflowRoutes.definitions}/${summary.definitionId}`}>继续编辑</Link></div>}
      {versions.filter(version => version.status === 'PUBLISHED').map(version => <div key={version.versionNo}><strong>v{version.versionNo}</strong>
        <Tag color={version.versionNo === summary.currentVersionNo ? 'green' : 'default'}>{version.versionNo === summary.currentVersionNo ? '当前发布' : '历史'}</Tag>
        <span className="muted">发布于 {shortTime(version.publishedTime)}</span>
        <Link to={`${workflowRoutes.definitions}/${summary.definitionId}/versions/${version.versionNo}`}>查看</Link></div>)}
      {!detail && <span className="muted">读取中</span>}
      {detail && !versions.length && !detail.draft && <span className="muted">暂无版本</span>}
    </div>}
  </section>;
}

/** 缩略图：按节点位置画点与块；仅有草稿时画草稿图。 */
function GraphThumbnail({ graph: source }: { graph?: WorkflowGraph }) {
  // 新建草稿的节点没有位置（全部重叠），先自动排列再画。
  const graph = useMemo(() => source && needsLayout(source) ? autoLayout(source) : source, [source]);
  const box = useMemo(() => {
    if (!graph?.nodes.length) return null;
    const xs = graph.nodes.map(node => node.position.x); const ys = graph.nodes.map(node => node.position.y);
    return { minX: Math.min(...xs), minY: Math.min(...ys), width: Math.max(1, Math.max(...xs) - Math.min(...xs)), height: Math.max(1, Math.max(...ys) - Math.min(...ys)) };
  }, [graph]);
  if (!graph || !box) return <div className="wf-thumb" />;
  const x = (value: number) => 12 + (value - box.minX) / box.width * 276; const y = (value: number) => 14 + (value - box.minY) / Math.max(box.height, 1) * 52;
  const position = new Map(graph.nodes.map(node => [node.key, node.position]));
  return <svg className="wf-thumb" viewBox="0 0 320 80" aria-hidden="true">
    {graph.edges.map(edge => { const from = position.get(edge.from); const to = position.get(edge.to);
      return from && to ? <line key={edge.key} x1={x(from.x) + 8} y1={y(from.y) + 4} x2={x(to.x)} y2={y(to.y) + 4} stroke="#d5dce8" /> : null; })}
    {graph.nodes.map(node => node.type === 'TASK'
      ? <rect key={node.key} x={x(node.position.x)} y={y(node.position.y)} width={18} height={9} rx={2} fill="#c7d4f5" />
      : <circle key={node.key} cx={x(node.position.x) + 4} cy={y(node.position.y) + 4} r={3.5} fill="#f2c98a" />)}
  </svg>;
}

function CreateDefinitionModal({ onClose, onCreated, onAuthorizationChanged }: { onClose: () => void; onCreated: (id: string) => void; onAuthorizationChanged: () => void }) {
  const [form] = Form.useForm<{ definitionCode: string; definitionName: string; description?: string }>();
  const command = useWorkflowCommand(onAuthorizationChanged);
  async function submit(values: { definitionCode: string; definitionName: string; description?: string }) {
    const result = await command.run(workflowPaths.definitions, 'POST', { definitionCode: values.definitionCode, definitionName: values.definitionName.trim(),
      ...(values.description?.trim() ? { description: values.description.trim() } : {}) }, decodeMutation, '新建失败');
    if (result) onCreated(result.objectId);
  }
  return <Modal open title="新建流程" okText="创建并编辑" okButtonProps={{ loading: command.saving }} onCancel={onClose} onOk={() => form.submit()}>
    <p className="muted">创建后生成草稿 v1（只有开始与结束节点），在编辑器里配置后发布。</p>
    {command.formError && <Alert type="error" showIcon title={command.formError} />}
    <Form form={form} layout="vertical" onFinish={values => void submit(values)}>
      <Form.Item name="definitionCode" label="流程编码" extra="大写字母开头，大写字母、数字、下划线；全局唯一，创建后不能修改" normalize={(value?: string) => value?.toUpperCase()}
        rules={[{ required: true, message: '请填写流程编码' }, { pattern: templateCodePattern, message: '格式不正确，例如 NEW_CHANNEL_ONBOARD' }]}><Input maxLength={64} /></Form.Item>
      <Form.Item name="definitionName" label="流程名称" rules={[{ required: true, whitespace: true, message: '请填写流程名称' }]}><Input maxLength={128} /></Form.Item>
      <Form.Item name="description" label="说明"><Input.TextArea rows={3} maxLength={512} /></Form.Item>
    </Form>
  </Modal>;
}
