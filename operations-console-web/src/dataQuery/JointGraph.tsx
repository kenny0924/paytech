import { useEffect, useMemo, useRef, useState } from 'react';
import { Handle, Position, ReactFlow, ReactFlowProvider, getBezierPath } from '@xyflow/react';
import type { Edge, EdgeProps, Node, NodeProps } from '@xyflow/react';
import { useT } from '../i18n/react';
import { nodeBadge } from './jointLayout';
import type { FlowEdge, Lane, NodeBadge } from './jointLayout';
import type { RunNode } from './jointRun';
import '@xyflow/react/dist/style.css';

/**
 * 泳道与节点尺寸（px）：节点高 40、行距 48，泳道头 36；泳道宽随容器自适应，128—260。
 * 1440 宽（侧栏展开、画布约 1190）下 7 个分组可全部放下；更窄（如 390）时可横向拖动。
 */
const nodeHeight = 40, rowGap = 48, headerHeight = 36, laneGap = 24, minLaneWidth = 128, maxLaneWidth = 260, padding = 16;

type LaneData = { name: string; color: string; height: number; width: number; last: boolean };
type CardData = { node: RunNode; color: string; badge: NodeBadge; selected: boolean; onPath: boolean; title: string };
type FlowNode = Node<LaneData, 'lane'> | Node<CardData, 'card'>;
/** skip：跨过中间泳道的连线走卡片上方的通道，不穿过中间泳道的节点。 */
type LinkData = { active: boolean; title: string; sourceSide: Position; targetSide: Position; skip: boolean };

function LaneNode({ data }: NodeProps<Node<LaneData, 'lane'>>) {
  return <div className={`jq-lane${data.last ? ' is-last' : ''}`} style={{ width: data.width, height: data.height }}>
    <div className="jq-lane-title"><span className="jq-dot" style={{ background: data.color }} />{data.name}</div>
  </div>;
}

export function BadgeText({ badge }: { badge: NodeBadge }) {
  const t = useT('data-query');
  switch (badge.kind) {
    case 'failed': return <span className="jq-badge is-failed">{t('joint.node.failed')}</span>;
    case 'skipped': return <span className="jq-badge is-skipped">{t('joint.node.skipped')}</span>;
    case 'entry': return <span className="jq-badge is-entry">{badge.count === 0 ? t('joint.node.entryEmpty') : badge.count === 1 ? t('joint.node.entry')
      : t('joint.node.entryCount', { count: badge.count })}</span>;
    case 'truncated': return <span className="jq-badge is-truncated">{t('joint.node.truncated', { count: badge.count })}</span>;
    default: return <span className="jq-badge">{badge.count}</span>;
  }
}

function CardNode({ data }: NodeProps<Node<CardData, 'card'>>) {
  const { node, badge } = data;
  return <div className={`jq-card is-${badge.kind}${data.selected ? ' is-selected' : ''}${data.onPath ? ' is-path' : ''}`} title={data.title}>
    <Handle type="target" id="tl" position={Position.Left} isConnectable={false} />
    <Handle type="target" id="tr" position={Position.Right} isConnectable={false} />
    <Handle type="source" id="sl" position={Position.Left} isConnectable={false} />
    <Handle type="source" id="sr" position={Position.Right} isConnectable={false} />
    <span className="jq-dot" style={{ background: data.color }} />
    <span className="jq-card-name">{node.templateName}</span>
    <BadgeText badge={badge} />
  </div>;
}

function LinkEdge({ id, sourceX, sourceY, targetX, targetY, data }: EdgeProps<Edge<LinkData, 'link'>>) {
  const [bezier] = getBezierPath({ sourceX, sourceY, targetX, targetY, sourcePosition: data!.sourceSide, targetPosition: data!.targetSide,
    curvature: data!.sourceSide === data!.targetSide ? 0.6 : 0.25 });
  const out = data!.sourceSide === Position.Right ? 1 : -1; const into = data!.targetSide === Position.Left ? -1 : 1; const lane = headerHeight - 8;
  const path = data!.skip ? `M ${sourceX} ${sourceY} H ${sourceX + out * 8} Q ${sourceX + out * 14} ${sourceY} ${sourceX + out * 14} ${sourceY - 6} V ${lane + 6}`
    + ` Q ${sourceX + out * 14} ${lane} ${sourceX + out * 20} ${lane} H ${targetX + into * 20} Q ${targetX + into * 14} ${lane} ${targetX + into * 14} ${lane + 6}`
    + ` V ${targetY - 6} Q ${targetX + into * 14} ${targetY} ${targetX + into * 8} ${targetY} H ${targetX}` : bezier;
  return <g className={`jq-edge${data!.active ? ' is-active' : ''}`}>
    <path id={id} d={path} className="jq-edge-path" fill="none" />
    <path d={path} className="jq-edge-hit" fill="none"><title>{data!.title}</title></path>
  </g>;
}

const nodeTypes = { lane: LaneNode, card: CardNode };
const edgeTypes = { link: LinkEdge };

export interface JointGraphProps {
  lanes: readonly Lane[]; edges: readonly FlowEdge[]; selectedId: string | null; path: { nodes: ReadonlySet<string>; edges: ReadonlySet<string> };
  rowCount: (node: RunNode) => number; edgeTitle: (edge: FlowEdge) => string; nodeTitle: (node: RunNode) => string; onSelect: (nodeId: string) => void;
}

/**
 * 链路总览（原型 02、03、07）：按分组分列的真实图形画布。节点只显示业务名称与条数（或起点 / 失败 / 未查 / n+），
 * 选中节点高亮其来源路径；连线悬停显示带出它的键值。只用于导航，明细在下方。
 */
export function JointGraph(props: JointGraphProps) {
  return <ReactFlowProvider><GraphInner {...props} /></ReactFlowProvider>;
}

function GraphInner({ lanes, edges, selectedId, path, rowCount, edgeTitle, nodeTitle, onSelect }: JointGraphProps) {
  const box = useRef<HTMLDivElement | null>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const element = box.current;
    if (!element) return;
    const observer = new ResizeObserver(entries => setWidth(entries[0]?.contentRect.width ?? 0));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const laneWidth = Math.min(maxLaneWidth, Math.max(minLaneWidth, lanes.length ? Math.floor((width - padding * 2 - laneGap * (lanes.length - 1)) / lanes.length) : minLaneWidth));
  const rows = Math.max(1, ...lanes.map(lane => lane.nodes.length));
  const height = headerHeight + rows * rowGap + padding;
  const laneOf = useMemo(() => new Map(lanes.flatMap((lane, index) => lane.nodes.map(node => [node.id, index] as const))), [lanes]);

  const nodes = useMemo<FlowNode[]>(() => lanes.flatMap((lane, index) => {
    const x = padding + index * (laneWidth + laneGap);
    const header: FlowNode = { id: `lane:${lane.groupName}`, type: 'lane', position: { x: x - laneGap / 2, y: 0 }, draggable: false, selectable: false,
      focusable: false, zIndex: -1, data: { name: lane.groupName, color: lane.color, height, width: laneWidth + laneGap, last: index === lanes.length - 1 } };
    const cards: FlowNode[] = lane.nodes.map((node, row) => ({
      id: node.id, type: 'card', position: { x, y: headerHeight + row * rowGap }, draggable: false, width: laneWidth, height: nodeHeight,
      data: { node, color: lane.color, badge: nodeBadge(node, rowCount(node)), selected: node.id === selectedId, onPath: path.nodes.has(node.id) && node.id !== selectedId,
        title: nodeTitle(node) },
    }));
    return [header, ...cards];
  }), [lanes, laneWidth, height, selectedId, path, rowCount, nodeTitle]);

  const flowEdges = useMemo<Edge<LinkData, 'link'>[]>(() => edges.filter(edge => laneOf.has(edge.source) && laneOf.has(edge.target)).map(edge => {
    const from = laneOf.get(edge.source)!; const to = laneOf.get(edge.target)!;
    const forward = to > from; const same = to === from;
    return { id: edge.id, source: edge.source, target: edge.target, type: 'link', selectable: false, focusable: false,
      sourceHandle: forward ? 'sr' : 'sl', targetHandle: forward || same ? 'tl' : 'tr', zIndex: 0,
      data: { active: path.edges.has(edge.id), title: edgeTitle(edge), sourceSide: forward ? Position.Right : Position.Left,
        targetSide: forward || same ? Position.Left : Position.Right, skip: Math.abs(to - from) > 1 } };
  }), [edges, laneOf, path, edgeTitle]);

  const contentWidth = padding * 2 + lanes.length * laneWidth + Math.max(0, lanes.length - 1) * laneGap;
  return <div className="jq-graph" ref={box} style={{ height }}>
    {width > 0 && <ReactFlow nodes={nodes} edges={flowEdges} nodeTypes={nodeTypes} edgeTypes={edgeTypes} defaultViewport={{ x: 0, y: 0, zoom: 1 }}
      minZoom={1} maxZoom={1} zoomOnScroll={false} zoomOnPinch={false} zoomOnDoubleClick={false} preventScrolling={false}
      panOnDrag={contentWidth > width} panOnScroll={false} nodesDraggable={false} nodesConnectable={false} elementsSelectable
      translateExtent={[[0, 0], [Math.max(contentWidth, width), height]]} proOptions={{ hideAttribution: true }}
      onNodeClick={(_, node) => { if (node.type === 'card') onSelect(node.id); }} />}
  </div>;
}
