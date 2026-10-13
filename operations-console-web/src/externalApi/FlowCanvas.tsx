import { useEffect, useMemo } from 'react';
import { Background, ConnectionLineType, Controls, Handle, MarkerType, Position, ReactFlow, useReactFlow } from '@xyflow/react';
import type { Edge as CanvasEdge, Node as CanvasNode, NodeProps } from '@xyflow/react';
import { Tag } from 'antd';
import { layout, outgoing, requestCoverage, statusCondition } from './flowModel';
import type { ApiContract, ExecuteStatus, Flow, FlowEdge } from './flowModel';
import '@xyflow/react/dist/style.css';

export const startId = '__start';
export type FlowSelection = { kind: 'start' } | { kind: 'node' | 'return' | 'initializer'; id: string } | null;

type CardData = { kind: 'start' | 'node' | 'return'; title: string; subtitle: string; status?: ExecuteStatus; warning: boolean; entry?: boolean };
type CardNode = CanvasNode<CardData, 'externalCard'>;
const statusColors: Record<ExecuteStatus, string> = { SUCCESS: 'green', FAIL: 'orange', ERROR: 'red' };

function Card({ data, selected, isConnectable }: NodeProps<CardNode>) {
  const kindClass = data.kind === 'start' ? 'start' : data.kind === 'node' ? 'rule_set' : 'end';
  return <div className={`flow-node flow-node-${kindClass}${selected ? ' is-selected' : ''}${data.warning ? ' has-warning' : ''}`}>
    {data.kind !== 'start' && <Handle type="target" position={Position.Top} isConnectable={isConnectable} />}
    <div className="flow-node-heading">
      <span className="flow-node-icon">{data.kind === 'start' ? '▷' : data.kind === 'node' ? '⇄' : '⚑'}</span>
      <strong>{data.title}</strong>
      {data.status && <Tag color={statusColors[data.status]}>{data.status}</Tag>}
      {data.entry && <Tag color="blue">入口</Tag>}
    </div>
    <p>{data.subtitle}</p>
    {data.kind !== 'return' && <Handle type="source" position={Position.Bottom} isConnectable={isConnectable} />}
  </div>;
}
const nodeTypes = { externalCard: Card };

/** 连线标签：执行状态快捷条件显示为“执行状态 X”，其他条件截断显示，兜底为“其他情况”。 */
function edgeLabel(edge: FlowEdge): string {
  if (edge.default) return '其他情况';
  const status = (['SUCCESS', 'FAIL', 'ERROR'] as ExecuteStatus[]).find(item => edge.condition === statusCondition(edge.from, item));
  const condition = status ? `执行状态 ${status}` : edge.condition ? (edge.condition.length > 24 ? `${edge.condition.slice(0, 24)}…` : edge.condition) : '条件待填';
  return `${edge.priority} · ${condition}`;
}

/**
 * 流程画布：开始节点连到入口，原子 API 节点之间及到出口的连线按出口顺序标注优先级，“其他情况”最后。
 * 节点位置由图结构自动计算，不保存；拖出新连线交给调用方按规则创建（拒绝环）。
 */
export function FlowCanvas({ flow, apis, readOnly, selection, focusId, onSelect, onConnect }: {
  flow: Flow; apis: ApiContract[]; readOnly: boolean; selection: FlowSelection; focusId?: string;
  onSelect: (selection: FlowSelection) => void; onConnect: (source: string, target: string) => void;
}) {
  const view = useReactFlow();
  const positions = useMemo(() => layout(flow, startId), [flow]);
  const selectedId = selection && selection.kind !== 'start' ? selection.id : selection?.kind === 'start' ? startId : null;
  const nodes = useMemo<CardNode[]>(() => [
    { id: startId, type: 'externalCard', position: positions.get(startId)!, selected: selectedId === startId, deletable: false,
      data: { kind: 'start', title: '开始', subtitle: flow.entry ? `入口：${flow.entry}` : '拖出连线指定入口节点', warning: !flow.entry } },
    ...flow.nodes.map(node => {
      const coverage = requestCoverage(node, apis.find(api => api.apiCode === node.integrationApi));
      const api = apis.find(item => item.apiCode === node.integrationApi);
      return { id: node.id, type: 'externalCard' as const, position: positions.get(node.id)!, selected: selectedId === node.id, deletable: false,
        data: { kind: 'node' as const, title: node.id, entry: flow.entry === node.id,
          subtitle: `${node.integrationApi}${api ? ` · ${api.apiName}` : ' · 原子 API 不存在'} · 请求 ${coverage.covered}/${coverage.required}`,
          warning: !api || coverage.covered < coverage.required || !outgoing(flow, node.id).some(edge => edge.default) } };
    }),
    ...flow.returns.map(item => ({ id: item.id, type: 'externalCard' as const, position: positions.get(item.id)!, selected: selectedId === item.id,
      deletable: false, data: { kind: 'return' as const, title: item.id, status: item.executeStatus,
        subtitle: item.executeStatus === 'ERROR' ? '无输出' : `${Object.keys(item.outputs ?? {}).length} 个输出`, warning: false } })),
  ], [flow, apis, positions, selectedId]);
  const edges = useMemo<CanvasEdge[]>(() => [
    ...(flow.entry ? [{ id: `${startId}->${flow.entry}`, source: startId, target: flow.entry, type: 'smoothstep',
      markerEnd: { type: MarkerType.ArrowClosed }, style: { stroke: '#a5b5d5', strokeDasharray: '4 4' }, deletable: false }] : []),
    ...flow.nodes.flatMap(node => outgoing(flow, node.id).map(edge => {
      const active = selectedId === edge.from;
      return { id: `${edge.from}->${edge.to}`, source: edge.from, target: edge.to, type: 'default', deletable: false,
        label: edgeLabel(edge),
        markerEnd: { type: MarkerType.ArrowClosed },
        style: { stroke: active ? '#3454db' : '#a5b5d5', strokeWidth: active ? 2 : 1.4 },
        labelStyle: { fill: edge.default ? '#71809c' : '#3454db', fontSize: 11 }, labelBgStyle: { fill: '#f8faff', stroke: '#dce4f4' },
        labelBgPadding: [6, 4] as [number, number] };
    })),
  ], [flow, selectedId]);
  useEffect(() => {
    if (focusId) void view.fitView({ nodes: [{ id: focusId }], padding: 0.8, duration: 250, maxZoom: 1 });
  }, [focusId, view]);
  return <div className="flow-canvas">
    <div className="flow-canvas-toolbar"><strong>流程</strong>
      <span>{readOnly ? '只读 · 点击节点查看配置' : '从节点底部拖出连线 · 点击节点配置请求与出口顺序 · 流程暂不支持环'}</span></div>
    <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} connectionLineType={ConnectionLineType.SmoothStep} fitView minZoom={0.2}
      maxZoom={1.5} nodesDraggable={false} nodesConnectable={!readOnly} edgesReconnectable={false} deleteKeyCode={null}
      onNodeClick={(_, node) => onSelect(node.id === startId ? { kind: 'start' }
        : flow.nodes.some(item => item.id === node.id) ? { kind: 'node', id: node.id } : { kind: 'return', id: node.id })}
      onEdgeClick={(_, edge) => onSelect(edge.source === startId ? { kind: 'start' } : { kind: 'node', id: edge.source })}
      onPaneClick={() => onSelect(null)}
      onConnect={connection => { if (!readOnly && connection.source && connection.target) onConnect(connection.source, connection.target); }}
      isValidConnection={connection => connection.source !== connection.target && connection.target !== startId}>
      <Background color="#dfe5f0" gap={24} /><Controls showInteractive={false} />
    </ReactFlow>
  </div>;
}
