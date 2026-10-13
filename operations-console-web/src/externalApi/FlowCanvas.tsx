import { useEffect, useMemo } from 'react';
import { BaseEdge, Background, ConnectionLineType, Controls, EdgeLabelRenderer, Handle, MarkerType, Position, ReactFlow, useReactFlow } from '@xyflow/react';
import type { Edge as CanvasEdge, EdgeProps, Node as CanvasNode, NodeProps } from '@xyflow/react';
import { outgoing, requestCoverage, statusCondition, workbenchLayout } from './flowModel';
import type { ApiContract, ExecuteStatus, Flow, FlowEdge } from './flowModel';
import { StatusTag } from './ui';
import '@xyflow/react/dist/style.css';

export const startId = '__start';
/** 画布与右栏的选中对象；exits 表示某个节点的出口顺序（点击连线进入）。 */
export type FlowSelection = { kind: 'start' } | { kind: 'node' | 'return' | 'initializer' | 'exits'; id: string } | null;

type StartData = { label: string; hint: string };
type ApiData = { id: string; subtitle: string; coverage: string; complete: boolean; error?: string };
type ReturnData = { id: string; status: ExecuteStatus; subtitle: string; error?: string };
type StartNode = CanvasNode<StartData, 'start'>;
type ApiNode = CanvasNode<ApiData, 'api'>;
type ReturnNode = CanvasNode<ReturnData, 'exit'>;

function StartCard({ data }: NodeProps<StartNode>) {
  return <div className="xflow-start" title={data.hint}><span>▷</span> {data.label}
    <Handle type="source" id="b" position={Position.Bottom} /></div>;
}

function ApiCard({ data, selected, isConnectable }: NodeProps<ApiNode>) {
  return <div className={`xflow-card xflow-api${selected ? ' is-selected' : ''}${data.error ? ' has-error' : ''}`}>
    <Handle type="target" id="t" position={Position.Top} isConnectable={isConnectable} />
    <div className="xflow-card-title"><span className="xflow-icon">⌁</span><code>{data.id}</code></div>
    <div className="xflow-card-sub">{data.subtitle}</div>
    <div className={data.complete ? 'xflow-ok' : 'xflow-bad'}>{data.coverage}</div>
    {data.error && <div className="xflow-bad">{data.error}</div>}
    <Handle type="source" id="r" position={Position.Right} isConnectable={isConnectable} />
    <Handle type="source" id="b" position={Position.Bottom} isConnectable={isConnectable} />
  </div>;
}

function ReturnCard({ data, selected, isConnectable }: NodeProps<ReturnNode>) {
  return <div className={`xflow-card xflow-return${selected ? ' is-selected' : ''}${data.error ? ' has-error' : ''}`}>
    <Handle type="target" id="l" position={Position.Left} isConnectable={isConnectable} />
    <div className="xflow-card-title"><StatusTag status={data.status} /><code>{data.id}</code></div>
    <div className={data.error ? 'xflow-bad' : 'xflow-card-sub'}>{data.error ?? data.subtitle}</div>
  </div>;
}
const nodeTypes = { start: StartCard, api: ApiCard, exit: ReturnCard };

type LaneData = { lane: number; text: string; active: boolean; fallback: boolean; showLabel: boolean };
type LaneEdge = CanvasEdge<LaneData, 'lane'>;

/**
 * 节点到出口的直角连线：每条连线占一条独立的竖向通道（从节点右侧水平引出，在通道处折向目标高度，再水平进入出口），
 * 标签放在通向出口的那段水平线上，不同出口的标签不会叠在同一处。
 */
function LaneEdgeView({ id, sourceX, sourceY, targetX, targetY, markerEnd, style, data }: EdgeProps<LaneEdge>) {
  const laneX = Math.min(sourceX + 20 + (data?.lane ?? 0) * 16, targetX - 70);
  const path = `M ${sourceX} ${sourceY} L ${laneX} ${sourceY} L ${laneX} ${targetY} L ${targetX} ${targetY}`;
  return <>
    <BaseEdge id={id} path={path} markerEnd={markerEnd} style={style} />
    {data?.showLabel && <EdgeLabelRenderer><div className={`xflow-edge-label${data.active ? ' is-active' : ''}${data.fallback ? ' is-fallback' : ''}`}
      style={{ transform: `translate(0, -50%) translate(${laneX + 6}px, ${targetY}px)` }}>{data.text}</div></EdgeLabelRenderer>}
  </>;
}
const edgeTypes = { lane: LaneEdgeView };

/** 连线标签：执行状态快捷条件写成“1 · SUCCESS”，其他条件截断显示，兜底为“其他情况”。 */
export function edgeLabel(edge: FlowEdge): string {
  if (edge.default) return '其他情况';
  const status = (['SUCCESS', 'FAIL', 'ERROR'] as ExecuteStatus[]).find(item => edge.condition === statusCondition(edge.from, item));
  const condition = status ?? (edge.condition ? (edge.condition.length > 22 ? `${edge.condition.slice(0, 22)}…` : edge.condition) : '条件待填');
  return `${edge.priority} · ${condition}`;
}

/**
 * 流程画布：开始在顶部，原子 API 节点成列、出口在右列，直角连线；节点卡片显示请求与参数覆盖，出口卡片列出输出字段。
 * 校验问题落在节点或出口上时标红并写出原因。拖出新连线交给调用方按规则创建（拒绝环）。
 */
export function FlowCanvas({ flow, apis, standardApiCode, readOnly, selection, focusId, errors, onSelect, onConnect }: {
  flow: Flow; apis: ApiContract[]; standardApiCode: string; readOnly: boolean; selection: FlowSelection; focusId?: string;
  errors: Map<string, string>; onSelect: (selection: FlowSelection) => void; onConnect: (source: string, target: string) => void;
}) {
  const view = useReactFlow();
  const positions = useMemo(() => workbenchLayout(flow, startId), [flow]);
  const selectedId = selection?.kind === 'start' ? startId : selection?.id ?? null;
  const returnIds = useMemo(() => new Set(flow.returns.map(item => item.id)), [flow.returns]);
  const nodes = useMemo<(StartNode | ApiNode | ReturnNode)[]>(() => [
    { id: startId, type: 'start', position: positions.get(startId)!, selected: selectedId === startId, deletable: false, connectable: !readOnly,
      data: { label: `开始 · ${standardApiCode} 输入`, hint: flow.entry ? `入口：${flow.entry}` : '从开始拖出连线指定入口节点' } },
    ...flow.nodes.map<ApiNode>(node => {
      const api = apis.find(item => item.apiCode === node.integrationApi);
      const coverage = requestCoverage(node, api);
      const requiredParams = api?.params.filter(param => param.required) ?? [];
      const boundParams = requiredParams.filter(param => node.params?.[param.name]).length;
      const complete = !!api && coverage.covered === coverage.required && boundParams === requiredParams.length;
      return { id: node.id, type: 'api', position: positions.get(node.id)!, selected: selectedId === node.id && selection?.kind === 'node', deletable: false,
        data: { id: node.id, subtitle: api ? `${node.integrationApi} · ${api.apiName}` : `${node.integrationApi} · 原子 API 不存在`,
          coverage: `请求 ${coverage.covered}/${coverage.required} · 参数 ${boundParams}/${requiredParams.length}${complete ? ' 已覆盖' : ' 未覆盖'}`,
          complete, error: errors.get(node.id) } };
    }),
    ...flow.returns.map<ReturnNode>(item => ({ id: item.id, type: 'exit', position: positions.get(item.id)!, selected: selectedId === item.id,
      deletable: false, data: { id: item.id, status: item.executeStatus, error: errors.get(item.id),
        subtitle: item.executeStatus === 'ERROR' ? '结果未知，输出为空'
          : Object.keys(item.outputs ?? {}).length ? `输出 ${Object.keys(item.outputs ?? {}).join('、')}` : '尚未配置输出' } })),
  ], [flow, apis, positions, selectedId, selection?.kind, errors, readOnly, standardApiCode]);
  const edges = useMemo<CanvasEdge[]>(() => [
    ...(flow.entry ? [{ id: `${startId}->${flow.entry}`, source: startId, sourceHandle: 'b', target: flow.entry, targetHandle: 't',
      type: 'smoothstep', markerEnd: { type: MarkerType.ArrowClosed, color: '#8a96ad' },
      style: { stroke: '#8a96ad', strokeDasharray: '4 4' }, deletable: false }] : []),
    ...(() => {
      let lane = 0; const labelled = new Set<string>();
      return flow.nodes.flatMap(node => outgoing(flow, node.id).map<CanvasEdge>(edge => {
        const active = selection?.kind === 'exits' && selection.id === edge.from || selectedId === edge.from;
        const color = active ? '#315dca' : '#8a96ad';
        const common = { id: `${edge.from}->${edge.to}`, source: edge.from, target: edge.to, deletable: false,
          markerEnd: { type: MarkerType.ArrowClosed, color }, style: { stroke: color, strokeWidth: active ? 1.8 : 1.2 } };
        if (returnIds.has(edge.to)) {
          const text = edgeLabel(edge);
          // 多条连线以相同文字进入同一出口（如几个节点的“其他情况”）时只标一次，避免标签叠在一起。
          const showLabel = !labelled.has(`${edge.to}:${text}`); labelled.add(`${edge.to}:${text}`);
          return { ...common, type: 'lane', sourceHandle: 'r', targetHandle: 'l',
            data: { lane: lane++, text, active, fallback: !!edge.default, showLabel } } satisfies LaneEdge;
        }
        return { ...common, type: 'smoothstep', sourceHandle: 'b', targetHandle: 't', pathOptions: { borderRadius: 6 }, label: edgeLabel(edge),
          labelStyle: { fill: edge.default ? '#6b7486' : '#315dca', fontSize: 11, fontFamily: 'var(--xapi-mono)' },
          labelBgStyle: { fill: '#fff', stroke: active ? '#315dca' : '#d5dce8' }, labelBgPadding: [8, 3] as [number, number], labelBgBorderRadius: 10 };
      }));
    })(),
  ], [flow, returnIds, selection, selectedId]);
  useEffect(() => {
    if (focusId) void view.fitView({ nodes: [{ id: focusId }], padding: 0.8, duration: 250, maxZoom: 1 });
  }, [focusId, view]);
  return <div className="xflow-canvas">
    <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} edgeTypes={edgeTypes} connectionLineType={ConnectionLineType.SmoothStep} fitView
      fitViewOptions={{ padding: 0.12, maxZoom: 1 }} minZoom={0.3} maxZoom={1.5} nodesDraggable={false} nodesConnectable={!readOnly}
      edgesReconnectable={false} deleteKeyCode={null} proOptions={{ hideAttribution: true }}
      onNodeClick={(_, node) => onSelect(node.id === startId ? { kind: 'start' }
        : flow.nodes.some(item => item.id === node.id) ? { kind: 'node', id: node.id } : { kind: 'return', id: node.id })}
      onEdgeClick={(_, edge) => onSelect(edge.source === startId ? { kind: 'start' } : { kind: 'exits', id: edge.source })}
      onPaneClick={() => onSelect(null)}
      onConnect={connection => { if (!readOnly && connection.source && connection.target) onConnect(connection.source, connection.target); }}
      isValidConnection={connection => connection.source !== connection.target && connection.target !== startId}>
      <Background color="#e6eaf2" gap={24} /><Controls showInteractive={false} position="bottom-left" />
    </ReactFlow>
  </div>;
}
