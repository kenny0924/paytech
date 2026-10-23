import { useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Dropdown } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { nodeTypeIcons } from './workflowIcons';
import {
  Background, BaseEdge, ConnectionLineType, Controls, EdgeLabelRenderer, Handle, MarkerType, MiniMap, Position, ReactFlow, ReactFlowProvider,
  applyNodeChanges, getSmoothStepPath, useReactFlow,
} from '@xyflow/react';
import type { Edge, EdgeProps, Node, NodeChange, NodeProps } from '@xyflow/react';
import { insertableTypes, toCanvas } from './graphModel';
import { nodeTypeLabel } from './workflowLabels';
import { useT } from '../i18n/react';
import type { VariableInfo } from './graphModel';
import type { VisualState } from './runtimeModel';
import type { GraphNode, NodeType, WorkflowGraph } from './workflowTypes';
import '@xyflow/react/dist/style.css';

export type CanvasSelection = { kind: 'node' | 'edge'; key: string } | null;
export const paletteMime = 'application/x-workflow-node';

interface Decoration { subtitle?: ReactNode; state?: VisualState; error?: string; dim?: boolean; badge?: string }
type CardData = { node: GraphNode; decoration: Decoration; connectable: boolean };
type CardNode = Node<CardData>;

const stateClass = (decoration: Decoration) => `${decoration.state ? ` is-${decoration.state}` : ''}${decoration.error ? ' has-error' : ''}${decoration.dim ? ' is-dim' : ''}`;

function CircleCard({ data, selected }: NodeProps<CardNode>) {
  const { node, decoration } = data;
  return <div className={`wf-circle wf-circle-${node.type.toLowerCase()}${selected ? ' is-selected' : ''}${stateClass(decoration)}`}>
    {node.type === 'END' && <Handle type="target" position={Position.Left} isConnectable={data.connectable} />}
    <div className="wf-circle-dot" />
    <div className="wf-circle-label">{node.name || node.key}</div>
    {node.type === 'START' && <Handle type="source" position={Position.Right} isConnectable={data.connectable} />}
  </div>;
}

function GatewayCard({ data, selected }: NodeProps<CardNode>) {
  const { node, decoration } = data;
  return <div className={`wf-gateway${selected ? ' is-selected' : ''}${stateClass(decoration)}`} title={decoration.error}>
    <Handle type="target" position={Position.Left} isConnectable={data.connectable} />
    <div className="wf-gateway-diamond"><span>{nodeTypeIcons[node.type]}</span></div>
    {decoration.error && <span className="wf-error-badge">!</span>}
    <div className="wf-gateway-label">{node.name || node.key}</div>
    <Handle type="source" position={Position.Right} isConnectable={data.connectable} />
  </div>;
}

function TaskCard({ data, selected }: NodeProps<CardNode>) {
  const { node, decoration } = data;
  return <div className={`wf-task${selected ? ' is-selected' : ''}${stateClass(decoration)}`} title={decoration.error}>
    <Handle type="target" position={Position.Left} isConnectable={data.connectable} />
    <div className="wf-task-title">{node.name || node.key}{decoration.badge && <span className="wf-task-badge">{decoration.badge}</span>}</div>
    <div className="wf-task-sub">{decoration.subtitle}</div>
    {decoration.error && <span className="wf-error-badge">!</span>}
    <Handle type="source" position={Position.Right} isConnectable={data.connectable} />
  </div>;
}
const nodeTypes = { circle: CircleCard, gateway: GatewayCard, task: TaskCard };

/** 可插入节点的连线：悬停时显示圆形「+」，点开选择节点类型（需求 D3）。 */
type InsertData = { visible: boolean; onHover: (key: string | null) => void; onMenu: (key: string | null) => void; onInsert: (edgeKey: string, type: NodeType) => void };
type InsertEdge = Edge<InsertData, 'insertable'>;
function InsertableEdge({ id, sourceX, sourceY, targetX, targetY, sourcePosition, targetPosition, markerEnd, style, label, labelStyle, labelBgStyle,
  labelBgPadding, labelBgBorderRadius, data }: EdgeProps<InsertEdge>) {
  const t = useT('workflow');
  const [path, labelX, labelY] = getSmoothStepPath({ sourceX, sourceY, sourcePosition, targetX, targetY, targetPosition });
  // 没有条件标签时「+」在中点；有标签时放到标签与终点之间的最后一段，避免压住标签。
  const plus = label ? { x: (labelX + targetX) / 2, y: targetY } : { x: labelX, y: labelY };
  return <>
    <BaseEdge id={id} path={path} markerEnd={markerEnd} style={style} interactionWidth={24} label={label} labelX={labelX} labelY={labelY}
      labelStyle={labelStyle} labelShowBg={!!label} labelBgStyle={labelBgStyle} labelBgPadding={labelBgPadding} labelBgBorderRadius={labelBgBorderRadius} />
    {data && <EdgeLabelRenderer>
      {/* EdgeLabelRenderer 是 React portal：点击会沿 React 树冒泡到连线的 onEdgeClick，把选中项改回连线（插入后应选中新节点），因此在这里截住。 */}
      <div className={`wf-edge-insert nodrag nopan${data.visible ? ' is-visible' : ''}`}
        style={{ transform: `translate(-50%, -50%) translate(${plus.x}px, ${plus.y}px)` }}
        onClick={event => event.stopPropagation()}
        onMouseEnter={() => data.onHover(id)} onMouseLeave={() => data.onHover(null)}>
        <Dropdown trigger={['click']} onOpenChange={open => data.onMenu(open ? id : null)}
          menu={{ items: insertableTypes.map(type => ({ key: type, label: nodeTypeLabel(type) })), onClick: ({ key, domEvent }) => {
            domEvent.stopPropagation(); data.onMenu(null); data.onInsert(id, key as NodeType); } }}>
          <button type="button" title={t('canvas.insertNode')} aria-label={t('canvas.insertNode')}><PlusOutlined /></button>
        </Dropdown>
      </div>
    </EdgeLabelRenderer>}
  </>;
}
const edgeTypes = { insertable: InsertableEdge };

export interface CanvasProps {
  graph: WorkflowGraph; editable: boolean; selection: CanvasSelection; onSelect: (selection: CanvasSelection) => void;
  decorate?: (node: GraphNode) => Decoration; edgeState?: (edgeKey: string) => 'done' | 'pending' | undefined;
  variables?: (gatewayKey: string) => VariableInfo[];
  /** 定位并居中的节点或连线 key。 */
  focusKey?: string;
  onConnect?: (from: string, to: string) => void; onMove?: (key: string, position: { x: number; y: number }) => void;
  onDelete?: (selection: NonNullable<CanvasSelection>) => void; onDropNode?: (type: NodeType, position: { x: number; y: number }) => void;
  /** 提供时连线悬停出现「+」，可在该连线上插入节点。 */
  onInsertOnEdge?: (edgeKey: string, type: NodeType) => void;
  toolbar?: ReactNode; footer?: ReactNode; height?: number | string;
}

/** 流程图画布：开始 / 结束为圆形、网关为菱形、任务为卡片，连线带箭头和条件标签；编辑态可拖入、拖动、连线与删除。 */
export function WorkflowCanvas(props: CanvasProps) {
  return <ReactFlowProvider><CanvasInner {...props} /></ReactFlowProvider>;
}

function CanvasInner({ graph, editable, selection, onSelect, decorate, edgeState, variables, focusKey, onConnect, onMove, onDelete, onDropNode,
  onInsertOnEdge, toolbar, footer, height = 560 }: CanvasProps) {
  const view = useReactFlow();
  const graphRef = useRef(graph); graphRef.current = graph;
  // 「+」的显示：悬停连线或按钮时显示，离开后稍等再隐藏（便于从连线移到按钮上）；菜单打开时保持显示。
  const [hoverEdge, setHoverEdge] = useState<string | null>(null); const [menuEdge, setMenuEdge] = useState<string | null>(null);
  const hideTimer = useRef<ReturnType<typeof setTimeout>>(undefined);
  useEffect(() => () => clearTimeout(hideTimer.current), []);
  const hover = useMemo(() => (key: string | null) => {
    clearTimeout(hideTimer.current);
    if (key) setHoverEdge(key); else hideTimer.current = setTimeout(() => setHoverEdge(null), 250);
  }, []);
  const derived = useMemo(() => toCanvas(graph, variables, selection?.kind === 'node' ? selection.key : null), [graph, variables, selection]);
  const [nodes, setNodes] = useState<CardNode[]>([]);
  useEffect(() => {
    setNodes(previous => derived.nodes.map(item => ({ id: item.id, type: item.type, position: item.position, selected: item.selected,
      measured: previous.find(old => old.id === item.id)?.measured, draggable: editable, deletable: false,
      data: { node: item.data.node, decoration: decorate?.(item.data.node) ?? {}, connectable: editable } })));
  }, [derived, decorate, editable]);
  const edges = useMemo<Edge[]>(() => derived.edges.map(edge => {
    const state = edgeState?.(edge.id);
    const active = selection?.kind === 'edge' && selection.key === edge.id;
    const color = active ? '#315dca' : state === 'done' ? '#1f8a4c' : '#8a96ad';
    const insertable = editable && onInsertOnEdge
      ? { type: 'insertable', data: { visible: hoverEdge === edge.id || menuEdge === edge.id, onHover: hover, onMenu: setMenuEdge, onInsert: onInsertOnEdge } }
      : { type: 'smoothstep' };
    return { id: edge.id, source: edge.source, target: edge.target, ...insertable, deletable: false, selected: active,
      markerEnd: { type: MarkerType.ArrowClosed, color }, style: { stroke: color, strokeWidth: active ? 2 : 1.4, strokeDasharray: state === 'pending' ? '4 4' : undefined },
      ...(edge.label ? { label: edge.label, labelStyle: { fill: edge.data.isDefault ? '#6b7486' : '#315dca', fontSize: 11 },
        labelBgStyle: { fill: '#fff', stroke: active ? '#315dca' : '#d5dce8' }, labelBgPadding: [6, 3] as [number, number], labelBgBorderRadius: 4 } : {}) };
  }), [derived.edges, edgeState, selection, editable, onInsertOnEdge, hoverEdge, menuEdge, hover]);
  useEffect(() => {
    if (!focusKey) return;
    // 定位连线时把两端节点一起放进视野（节点与连线 key 在版本内不重复）。
    const edge = graphRef.current.edges.find(item => item.key === focusKey);
    void view.fitView({ nodes: (edge ? [edge.from, edge.to] : [focusKey]).map(id => ({ id })), padding: edge ? 0.6 : 1, duration: 250, maxZoom: 1 });
  }, [focusKey, view]);
  function changeNodes(changes: NodeChange<CardNode>[]) {
    setNodes(current => applyNodeChanges(changes.filter(change => change.type === 'position' || change.type === 'dimensions'), current));
  }
  return <div className="wf-canvas" style={{ height }} tabIndex={0}
    onKeyDown={event => {
      const target = event.target as HTMLElement;
      if (!editable || !selection || target.closest('input, textarea, [contenteditable="true"]')) return;
      if (event.key === 'Delete' || event.key === 'Backspace') { event.preventDefault(); onDelete?.(selection); }
    }}
    onDragOver={event => { if (editable && event.dataTransfer.types.includes(paletteMime)) { event.preventDefault(); event.dataTransfer.dropEffect = 'copy'; } }}
    onDrop={event => {
      const type = event.dataTransfer.getData(paletteMime) as NodeType;
      if (!editable || !type) return;
      event.preventDefault();
      onDropNode?.(type, view.screenToFlowPosition({ x: event.clientX, y: event.clientY }));
    }}>
    {toolbar && <div className="wf-canvas-toolbar">{toolbar}</div>}
    <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} edgeTypes={edgeTypes} connectionLineType={ConnectionLineType.SmoothStep} fitView
      fitViewOptions={{ padding: 0.15, maxZoom: 1 }} minZoom={0.25} maxZoom={1.6} nodesDraggable={editable} nodesConnectable={editable}
      elementsSelectable edgesReconnectable={false} deleteKeyCode={null} proOptions={{ hideAttribution: true }}
      onNodesChange={changeNodes}
      onNodeDragStop={(_, node) => onMove?.(node.id, node.position)}
      onNodeClick={(_, node) => onSelect({ kind: 'node', key: node.id })}
      onEdgeClick={(_, edge) => onSelect({ kind: 'edge', key: edge.id })}
      onEdgeMouseEnter={onInsertOnEdge ? (_, edge) => hover(edge.id) : undefined}
      onEdgeMouseLeave={onInsertOnEdge ? () => hover(null) : undefined}
      onPaneClick={() => onSelect(null)}
      onConnect={connection => { if (editable && connection.source && connection.target) onConnect?.(connection.source, connection.target); }}
      isValidConnection={connection => connection.source !== connection.target}>
      <Background color="#e6eaf2" gap={24} />
      <Controls showInteractive={false} position="bottom-left" />
      {editable && <MiniMap pannable zoomable position="bottom-right" style={{ width: 160, height: 100 }} />}
    </ReactFlow>
    {footer && <div className="wf-canvas-footer">{footer}</div>}
  </div>;
}
