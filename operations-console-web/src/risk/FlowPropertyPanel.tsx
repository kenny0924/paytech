import { App, Button, Form, Input, Select, Space, Tag } from 'antd';
import { DictionarySelect } from './DictionarySelect';
import { FlowConditionEditor } from './FlowConditionEditor';
import { decisionLabels, edgeLabels, newFlowExpression, nodeLabels, normalizeExitPriorities, removeNode, reorderExit } from './decisionFlow';
import type { FlowContext, FlowEdge, FlowGraph, FlowNode, FlowReference } from './decisionFlow';
import type { FlowSelection } from './FlowCanvas';

export function FlowPropertyPanel({graph,references,context,readonly,selection,onSelect,onChange,onPickRule,onReadRule}:{graph:FlowGraph;references:FlowReference[];context:FlowContext|null;readonly:boolean;selection:FlowSelection;onSelect:(s:FlowSelection)=>void;onChange:(g:FlowGraph)=>void;onPickRule:(key:string)=>void;onReadRule:(key:string)=>void}) {
  const {modal}=App.useApp();const node=selection?.kind==='node'?graph.nodes.find(n=>n.nodeKey===selection.key):null;const edge=selection?.kind==='edge'?graph.edges.find(e=>e.edgeKey===selection.key):null;
  function changeNode(values:Partial<FlowNode>){if(!readonly&&node)onChange({...graph,nodes:graph.nodes.map(n=>n.nodeKey===node.nodeKey?{...n,...values}:n)});}
  function changeEdge(values:Partial<FlowEdge>){if(!readonly&&edge)onChange({...graph,edges:graph.edges.map(e=>e.edgeKey===edge.edgeKey?{...e,...values}:e)});}
  const source=edge?graph.nodes.find(n=>n.nodeKey===edge.sourceNodeKey):node;
  const exits=graph.edges.filter(e=>e.sourceNodeKey===source?.nodeKey).sort((a,b)=>a.type==='ELSE'?1:b.type==='ELSE'?-1:a.priority-b.priority);
  function typeChanged(type:FlowEdge['type']){if(!edge||readonly)return;
    const apply=()=>{let next=graph.edges;if(type==='UNCONDITIONAL')next=next.filter(e=>e.sourceNodeKey!==edge.sourceNodeKey||e.edgeKey===edge.edgeKey);
      const changed={...edge,type,priority:type==='CONDITIONAL'?edge.type==='CONDITIONAL'?edge.priority:exits.filter(e=>e.type==='CONDITIONAL').length+1:0,condition:type==='CONDITIONAL'?edge.condition??{schemaVersion:2 as const,root:newFlowExpression()}:null};
      onChange(normalizeExitPriorities({...graph,edges:next.map(e=>e.edgeKey===edge.edgeKey?changed:e)}));};
    if(type==='UNCONDITIONAL'&&(exits.length>1||edge.condition))modal.confirm({title:'改为直接继续？',content:'将移除此节点的其他出口及当前连线条件，保留当前目标。',okText:'确认改为直连',cancelText:'保留分支',onOk:apply});else apply();
  }
  if(!node&&!edge)return null;
  const ref=references.find(r=>r.nodeKey===node?.nodeKey&&r.ruleSetVersionId===node.ruleSetVersionId);
  return <aside className="flow-properties"><div className="flow-panel-title"><h3>{node?'节点配置':'连线条件'}</h3><Button type="text" aria-label="关闭属性面板" onClick={()=>onSelect(null)}>×</Button></div>
    {node&&<><Tag>{nodeLabels[node.type]}</Tag>{readonly?<h4>{node.name}</h4>:<Form layout="vertical"><Form.Item label="节点名称"><Input aria-label="节点名称" value={node.name} maxLength={100} onChange={e=>changeNode({name:e.target.value})}/></Form.Item></Form>}
      {node.type==='RULE_SET'&&<><div className="flow-reference-card"><strong>{ref?.ruleSetName??'尚未选择规则版本'}</strong>{ref&&<><p>{ref.ruleSetCode}</p><p>#{ref.versionNo} · {ref.ruleCount} 条规则</p><Tag color={ref.ruleSetStatus==='DELETED'?'red':ref.versionStatus==='RETIRED'?'orange':'blue'}>{ref.ruleSetStatus==='DELETED'?'规则集已删除':ref.versionStatus==='RETIRED'?'版本已退休':ref.versionStatus==='FROZEN'?'版本已冻结':'配置草稿'}</Tag>{ref.ruleSetStatus==='DELETED'&&<p>历史内容仍可查看。新保存前请替换引用。</p>}</>}</div>
        {!readonly&&<Button block onClick={()=>onPickRule(node.nodeKey)}>{ref?'替换准确规则版本':'选择规则集版本'}</Button>}{ref&&<Button block type="link" onClick={()=>onReadRule(node.nodeKey)}>查看此版本规则</Button>}</>}
      {node.type==='END'&&<>{readonly?<p>结束决定：{node.decisionCode?decisionLabels[node.decisionCode]:'未配置'}</p>:<Form layout="vertical"><Form.Item label="结束决定 *"><Select aria-label="结束决定" placeholder="请选择结束决定" value={node.decisionCode??undefined} options={Object.entries(decisionLabels).map(([value,label])=>({value,label}))} onChange={decisionCode=>changeNode({decisionCode,reasonCode:decisionCode==='DENY'?node.reasonCode:null,reasonMessage:decisionCode==='DENY'?node.reasonMessage:null})}/></Form.Item></Form>}
        {node.decisionCode==='DENY'&&(readonly?<><strong>拒绝原因</strong><p>{node.reasonCode??'原版本未登记'}</p><p className="flow-read-value">{node.reasonMessage??'原版本未保存文案'}</p></>:<Form layout="vertical"><Form.Item label="拒绝原因 *"><DictionarySelect kind="rejection-reasons" values={[node.reasonCode??'']} disabled={false} label="结束拒绝原因" onChange={(_,next)=>{if(!next||next.code===node.reasonCode)return;const apply=()=>changeNode({reasonCode:next.code,reasonMessage:next.defaultMessage??''});if(node.reasonMessage)modal.confirm({title:'替换拒绝原因和文案？',content:'切换原因将带入新的默认文案。',okText:'替换',cancelText:'保留原值',onOk:apply});else apply();}}/></Form.Item><Form.Item label="拒绝文案 *"><Input.TextArea aria-label="结束拒绝文案" value={node.reasonMessage??''} maxLength={500} autoSize={{minRows:3,maxRows:6}} onChange={e=>changeNode({reasonMessage:e.target.value})}/></Form.Item></Form>)}<p className="cell-meta">到达此节点立即结束整个流程。</p></>}
      {!readonly&&node.type!=='START'&&<Button className="flow-remove" danger type="text" onClick={()=>{onChange(removeNode(graph,node.nodeKey));onSelect(null);}}>删除节点及关联连线</Button>}</>}
    {edge&&<><div className="flow-endpoints"><span>从 · {source?.name??edge.sourceNodeKey}</span><strong>↳ 到 · {graph.nodes.find(n=>n.nodeKey===edge.targetNodeKey)?.name??edge.targetNodeKey}</strong></div>
      {readonly?<h4>{edge.name}</h4>:<Form layout="vertical"><Form.Item label="连线名称"><Input aria-label="连线名称" value={edge.name} maxLength={100} onChange={e=>changeEdge({name:e.target.value})}/></Form.Item><Form.Item label="目标节点"><Select aria-label="连线目标节点" value={edge.targetNodeKey} options={graph.nodes.filter(n=>n.type!=='START'&&n.nodeKey!==edge.sourceNodeKey).map(n=>({value:n.nodeKey,label:n.name}))} onChange={targetNodeKey=>changeEdge({targetNodeKey})}/></Form.Item></Form>}
      {readonly||source?.type==='START'?<Tag>{edgeLabels[edge.type]}</Tag>:<Select aria-label="连线类型" className="flow-edge-type" value={edge.type} options={Object.entries(edgeLabels).map(([value,label])=>({value,label}))} onChange={typeChanged}/>}
      {edge.condition&&<FlowConditionEditor value={edge.condition.root} context={context} nodeType={source?.type??'RULE_SET'} readonly={readonly} onChange={root=>changeEdge({condition:{schemaVersion:2,root}})}/>}
      {edge.type==='ELSE'&&<p>仅当前节点的所有条件均为正常不匹配时进入。每个分支节点只能保留一个“其他情况”。</p>}
      {!readonly&&<Button className="flow-remove" danger type="text" onClick={()=>{onChange(normalizeExitPriorities({...graph,edges:graph.edges.filter(e=>e.edgeKey!==edge.edgeKey)}));onSelect(null);}}>删除此连线</Button>}</>}
    {!!exits.length&&<div className="flow-exit-list"><strong>本节点出口顺序</strong>{exits.map(e=><div key={e.edgeKey} className={selection?.key===e.edgeKey?'is-selected':''}><Button type="text" onClick={()=>onSelect({kind:'edge',key:e.edgeKey})}>{e.type==='CONDITIONAL'?`${e.priority} · `:e.type==='ELSE'?'其他情况 · ':''}{e.name}</Button>{!readonly&&e.type==='CONDITIONAL'&&<Space size={0}><Button type="text" size="small" aria-label={`上移 ${e.name}`} onClick={()=>onChange(reorderExit(graph,e.edgeKey,-1))}>↑</Button><Button type="text" size="small" aria-label={`下移 ${e.name}`} onClick={()=>onChange(reorderExit(graph,e.edgeKey,1))}>↓</Button></Space>}</div>)}</div>}
  </aside>;
}
