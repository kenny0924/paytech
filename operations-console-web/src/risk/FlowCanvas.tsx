import { useEffect, useMemo, useState } from 'react';
import { Background, ConnectionLineType, Controls, Handle, MarkerType, Position, ReactFlow, applyNodeChanges, useReactFlow } from '@xyflow/react';
import type { Node as CanvasNode, NodeProps } from '@xyflow/react';
import { Button, Tag } from 'antd';
import { decisionLabels, edgeLabels, newKey, normalizeExitPriorities, removeNode } from './decisionFlow';
import type { FlowGraph, FlowReference } from './decisionFlow';
import '@xyflow/react/dist/style.css';

export type FlowSelection={kind:'node'|'edge';key:string}|null;
type CardNode=CanvasNode<{name:string;subtitle:string;type:'START'|'RULE_SET'|'END';decision:string|null;warning:boolean;threeDs:boolean},'flowCard'>;
function FlowCard({data,selected,isConnectable}:NodeProps<CardNode>){return <div className={`flow-node flow-node-${data.type.toLowerCase()}${selected?' is-selected':''}${data.warning?' has-warning':''}`}>
  {data.type!=='START'&&<Handle type="target" position={Position.Top} isConnectable={isConnectable}/>}
  <div className="flow-node-heading"><span className="flow-node-icon">{data.type==='START'?'▷':data.type==='RULE_SET'?'☷':'⚑'}</span><strong>{data.name}</strong>{data.decision&&<Tag color={data.decision==='DENY'?'red':data.decision==='ALLOW'?'green':'gold'}>{decisionLabels[data.decision as keyof typeof decisionLabels]}</Tag>}{data.threeDs&&<Tag color="blue" className="flow-node-3ds">3DS</Tag>}</div><p>{data.subtitle}</p>
  {data.type==='RULE_SET'&&<small>输出：命中情况</small>}{data.type==='END'&&<small>到达即结束后续检查</small>}
  {data.type!=='END'&&<Handle type="source" position={Position.Bottom} isConnectable={isConnectable}/>}
</div>;}
const nodeTypes={flowCard:FlowCard};
export function FlowCanvas({graph,references,readonly,selection,onSelect,onChange,focusKey,focusRevision=0}:{graph:FlowGraph;references:FlowReference[];readonly:boolean;selection:FlowSelection;onSelect:(s:FlowSelection)=>void;onChange:(g:FlowGraph)=>void;focusKey?:string;focusRevision?:number}) {
  const flow=useReactFlow();const [measured,setMeasured]=useState<Record<string,{width?:number;height?:number}>>({});
  const nodes=useMemo(()=>graph.nodes.map(n=>{const ref=references.find(r=>r.nodeKey===n.nodeKey&&r.ruleSetVersionId===n.ruleSetVersionId);return {id:n.nodeKey,type:'flowCard' as const,position:{x:Number(n.position.x),y:Number(n.position.y)},measured:measured[n.nodeKey],selected:selection?.kind==='node'&&selection.key===n.nodeKey,deletable:!readonly&&n.type!=='START',data:{name:n.name,type:n.type,decision:n.decisionCode,threeDs:n.type==='END'&&n.decisionCode==='ALLOW'&&n.outputs?.AUTHENTICATION_REQUIREMENT==='THREE_DS_REQUIRED',warning:n.type==='END'&&!n.decisionCode||n.type==='RULE_SET'&&(!ref||ref.ruleSetStatus==='DELETED'),subtitle:n.type==='RULE_SET'?ref?`${ref.ruleSetCode} · #${ref.versionNo} · ${ref.ruleCount} 条规则${ref.ruleSetStatus==='DELETED'?' · 已删除':ref.versionStatus==='RETIRED'?' · 已退休':''}`:'请选择准确规则版本':n.type==='START'?'进入决策流':n.decisionCode?'显式结束':'待配置结束决定'}};}),[graph.nodes,references,selection,readonly,measured]);
  const edges=useMemo(()=>graph.edges.map(e=>({id:e.edgeKey,source:e.sourceNodeKey,target:e.targetNodeKey,type:'smoothstep',pathOptions:{borderRadius:20,offset:28},label:e.type==='CONDITIONAL'?`${e.priority} · ${e.name}`:edgeLabels[e.type],selected:selection?.kind==='edge'&&selection.key===e.edgeKey,markerEnd:{type:MarkerType.ArrowClosed},style:{stroke:selection?.key===e.edgeKey?'#3454db':'#a5b5d5',strokeWidth:selection?.key===e.edgeKey?2:1.4},labelStyle:{fill:e.type==='CONDITIONAL'?'#3454db':'#71809c',fontSize:11},labelBgStyle:{fill:'#f8faff',stroke:'#dce4f4'},labelBgPadding:[8,5] as [number,number],reconnectable:!readonly,deletable:!readonly})),[graph.edges,selection,readonly]);
  useEffect(()=>{if(focusKey)void flow.fitView({nodes:[{id:focusKey}],padding:.8,duration:250,maxZoom:1});},[focusKey,focusRevision,flow]);
  function connect(source:string|null,target:string|null){if(readonly||!source||!target||source===target||graph.nodes.find(n=>n.nodeKey===source)?.type==='END'||graph.nodes.find(n=>n.nodeKey===target)?.type==='START'||graph.edges.length>=100)return;const outgoing=graph.edges.filter(e=>e.sourceNodeKey===source);if(graph.nodes.find(n=>n.nodeKey===source)?.type==='START'&&outgoing.length)return;
    // 新连接默认仍为直连；多出口必须由用户在侧栏显式配置条件与其他情况。
    const edge={edgeKey:newKey('edge'),sourceNodeKey:source,targetNodeKey:target,type:'UNCONDITIONAL' as const,priority:0,name:'继续',condition:null};onChange({...graph,edges:[...graph.edges,edge]});onSelect({kind:'edge',key:edge.edgeKey});}
  return <div className="flow-canvas"><div className="flow-canvas-toolbar"><strong>♧ 决策路径</strong><span>{readonly?'只读 · 可选择节点和连线查看配置':'拖动连接点新建连线 · 点击连线配置条件'}</span></div>
    <ReactFlow nodes={nodes} edges={edges} nodeTypes={nodeTypes} connectionLineType={ConnectionLineType.SmoothStep} fitView minZoom={.2} maxZoom={1.5} nodesDraggable={!readonly} nodesConnectable={!readonly} edgesReconnectable={!readonly} deleteKeyCode={readonly?null:['Backspace','Delete']} nodesFocusable edgesFocusable
      onNodeClick={(_,n)=>onSelect({kind:'node',key:n.id})} onEdgeClick={(_,e)=>onSelect({kind:'edge',key:e.id})} onPaneClick={()=>onSelect(null)}
      onNodesChange={changes=>{const dimensions=changes.filter(c=>c.type==='dimensions');if(dimensions.length)setMeasured(old=>{const next={...old};for(const c of dimensions)next[c.id]=c.dimensions??{};return next;});if(readonly)return;const positions=changes.filter(c=>c.type==='position');if(positions.length){const next=applyNodeChanges(positions,nodes);onChange({...graph,nodes:graph.nodes.map(n=>{const pos=next.find(row=>row.id===n.nodeKey)!.position;return {...n,position:{x:String(Math.round(Math.max(-100000,Math.min(100000,pos.x)))),y:String(Math.round(Math.max(-100000,Math.min(100000,pos.y))))}};})});}}}
      onDelete={({nodes:removedNodes,edges:removedEdges})=>{if(!readonly){const next=removedNodes.reduce((g,n)=>removeNode(g,n.id),graph);onChange(normalizeExitPriorities({...next,edges:next.edges.filter(e=>!removedEdges.some(r=>r.id===e.edgeKey))}));}}}
      onConnect={c=>connect(c.source,c.target)} onReconnect={(edge,c)=>{if(!readonly&&c.source&&c.target)onChange(normalizeExitPriorities({...graph,edges:graph.edges.map(e=>e.edgeKey===edge.id?{...e,sourceNodeKey:c.source!,targetNodeKey:c.target!}:e)}));}}
      isValidConnection={c=>c.source!==c.target&&graph.nodes.find(n=>n.nodeKey===c.source)?.type!=='END'&&graph.nodes.find(n=>n.nodeKey===c.target)?.type!=='START'}>
      <Background color="#dfe5f0" gap={24}/><Controls showInteractive={false}/>
    </ReactFlow>
    {readonly&&<Button className="flow-fit" size="small" onClick={()=>void flow.fitView({padding:.2,duration:250})}>适应画布</Button>}
  </div>;
}
