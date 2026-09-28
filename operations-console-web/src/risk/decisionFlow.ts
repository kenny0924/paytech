import { decodeVariableOperand, emptyOperand, literalOperand } from './riskVariable.ts';
import type { VariableOperand, VariableProjection, VariableQueryContext } from './riskVariable.ts';
import type { ConsoleResource } from '../api/types.ts';
import { exact, invariant, id } from './riskDictionary.ts';

export type FlowDecision = 'ALLOW' | 'REVIEW' | 'DENY';
export type FlowExpression = { kind:'GROUP'; operator:'AND'|'OR'|'NOT'; children:FlowExpression[] }
  | { kind:'PREDICATE'; source:{ type:'CONTEXT'|'NODE_RESULT'; fieldCode:string }; operator:string; values:(string|boolean)[] }
  | {kind:'PREDICATE'; operator:string; operands:VariableOperand[]};
export interface FlowNode { nodeKey:string; type:'START'|'RULE_SET'|'END'; name:string; position:{x:string;y:string}; ruleSetVersionId:string|null; decisionCode:FlowDecision|null; reasonCode:string|null; reasonMessage:string|null }
export interface FlowEdge { edgeKey:string; sourceNodeKey:string; targetNodeKey:string; type:'UNCONDITIONAL'|'CONDITIONAL'|'ELSE'; priority:number; name:string; condition:{schemaVersion:1|2;root:FlowExpression}|null }
export interface FlowGraph { schemaVersion:1; nodes:FlowNode[]; edges:FlowEdge[] }
export interface FlowSave { pointCode:string; flowCode:string; flowName:string; baseFlowVersionId:string|null; expectedVersion:string|null; changeReason:string; graph:FlowGraph }
export interface FlowSummary { flowId:string; pointCode:string; flowCode:string; flowName:string; currentFlowVersionId:string; currentVersionNo:number; version:string; ruleSetNodeCount:number; updatedBy:string; updateTime:string }
export interface FlowReference { nodeKey:string; ruleSetId:string; ruleSetCode:string; ruleSetName:string; ruleSetVersionId:string; versionNo:number; ruleCount:number; ruleSetStatus:'CONFIGURABLE'|'DELETED'; versionStatus:'DRAFT'|'FROZEN'|'RETIRED' }
export interface FlowHistory { flowVersionId:string; versionNo:number; flowName:string; createdBy:string; createTime:string; changeReason:string }
export interface FlowDetail extends FlowHistory, VariableProjection { flowId:string; pointCode:string; flowCode:string; flowStatus:'CONFIGURABLE'|'DELETED'; currentFlowVersionId:string; currentVersionNo:number; version:string; inputContractCode:string; inputContractVersion:number; graph:FlowGraph; ruleSetReferences:FlowReference[] }
export interface FlowIssue { code:string; nodeKey:string|null; edgeKey:string|null; field:string|null; message:string }
export interface FlowValidation { valid:boolean; issues:FlowIssue[]; truncated:boolean }
export interface FlowField { variableCode?:string; fieldCode:string; label:string; valueType:string; required:boolean; operators:string[]; literalDomain:string }
export interface FlowContext { descriptorCode?:string;conditionSchemaVersion?:number;variableQueryContext?:VariableQueryContext; supportedPoints:{pointCode:string;pointName:string}[]; pointCode:string; inputContractCode:string; inputContractVersion:number; graphSchemaVersion:1; fields:FlowField[]; nodeOutputs:{variableCode?:string;nodeType:string;fieldCode:string;label:string;valueType:string;required:boolean;operators:string[]}[]; limits:{bodyBytes:number;nodes:number;edges:number;conditionBytes:number;conditionNodes:number;conditionDepth:number} }
export interface LegacyFlow { sourceType:'LEGACY_POINT'; pointVersionId:string; pointCode:string; pointName:string; versionNo:number; inputContractCode:string; inputContractVersion:number; defaultDecision:FlowDecision; evaluationStrategy:string; failureStrategy:string; nodes:FlowNode[]; edges:FlowEdge[]; ruleSetReferences:FlowReference[] }
export const decisionLabels = { ALLOW:'放行', REVIEW:'人工复核', DENY:'拒绝' };
export const nodeLabels = { START:'开始', RULE_SET:'规则集', END:'结束' };
export const edgeLabels = { UNCONDITIONAL:'直接继续', CONDITIONAL:'满足条件', ELSE:'其他情况' };
export function hasFlowAction(resources:readonly ConsoleResource[], action:'SAVE'|'DELETE') {
  return resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === `OPERATIONS_RISK_DECISION_FLOW_${action}`);
}
export function newKey(prefix:string) { return `${prefix}_${crypto.randomUUID().replaceAll('-', '')}`; }
export function initialGraph():FlowGraph { return { schemaVersion:1, nodes:[
  { nodeKey:'start',type:'START',name:'开始',position:{x:'80',y:'180'},ruleSetVersionId:null,decisionCode:null,reasonCode:null,reasonMessage:null },
  { nodeKey:'end',type:'END',name:'结束',position:{x:'440',y:'180'},ruleSetVersionId:null,decisionCode:null,reasonCode:null,reasonMessage:null }],
  edges:[{edgeKey:'next',sourceNodeKey:'start',targetNodeKey:'end',type:'UNCONDITIONAL',priority:0,name:'继续',condition:null}] }; }
export function toWireGraph(graph:FlowGraph):FlowGraph { return { schemaVersion:1,
  nodes:graph.nodes.map(n => ({nodeKey:n.nodeKey,type:n.type,name:n.name,position:{x:String(Math.round(Number(n.position.x))),y:String(Math.round(Number(n.position.y)))},ruleSetVersionId:n.ruleSetVersionId,decisionCode:n.decisionCode,reasonCode:n.reasonCode,reasonMessage:n.reasonMessage})),
  edges:graph.edges.map(e => ({edgeKey:e.edgeKey,sourceNodeKey:e.sourceNodeKey,targetNodeKey:e.targetNodeKey,type:e.type,priority:e.priority,name:e.name,condition:e.condition ? structuredClone(e.condition) : null})) }; }
export function normalizeExitPriorities(graph:FlowGraph):FlowGraph {
  const priorities=new Map<string,number>();
  for(const source of new Set(graph.edges.map(e=>e.sourceNodeKey)))graph.edges.filter(e=>e.sourceNodeKey===source&&e.type==='CONDITIONAL').sort((a,b)=>a.priority-b.priority).forEach((e,i)=>priorities.set(e.edgeKey,i+1));
  return {...graph,edges:graph.edges.map(e=>({...e,priority:priorities.get(e.edgeKey)??0}))};
}
export function removeNode(graph:FlowGraph, key:string):FlowGraph { return graph.nodes.find(n=>n.nodeKey===key)?.type==='START' ? graph : normalizeExitPriorities({...graph,nodes:graph.nodes.filter(n=>n.nodeKey!==key),edges:graph.edges.filter(e=>e.sourceNodeKey!==key&&e.targetNodeKey!==key)}); }
export function reorderExit(graph:FlowGraph,key:string,direction:-1|1):FlowGraph {
  const edge=graph.edges.find(e=>e.edgeKey===key); if(!edge||edge.type!=='CONDITIONAL')return graph;
  const exits=graph.edges.filter(e=>e.sourceNodeKey===edge.sourceNodeKey&&e.type==='CONDITIONAL').sort((a,b)=>a.priority-b.priority);
  const index=exits.findIndex(e=>e.edgeKey===key), target=index+direction; if(target<0||target>=exits.length)return graph;
  [exits[index],exits[target]]=[exits[target]!,exits[index]!]; const order=new Map(exits.map((e,i)=>[e.edgeKey,i+1]));
  return {...graph,edges:graph.edges.map(e=>order.has(e.edgeKey)?{...e,priority:order.get(e.edgeKey)!}:e)};
}
export function newFlowExpression():FlowExpression {return {kind:'PREDICATE',operator:'EQ',operands:[emptyOperand(),literalOperand('STRING')]};}
/** 仅供旧协议历史回读的合同样例；新编辑入口不插入此条件。 */
export function matchedExpression():FlowExpression { return {kind:'PREDICATE',source:{type:'NODE_RESULT',fieldCode:'matched'},operator:'EQ',values:[true]}; }
export function str(v:unknown,max=500):string { invariant(typeof v==='string'&&v.length>0&&[...v].length<=max);return v; }
export function int(v:unknown,min=1,max=2147483647):number { invariant(typeof v==='number'&&Number.isInteger(v)&&v>=min&&v<=max);return v; }
export function one<T extends string>(v:unknown,values:readonly T[]):T { invariant(typeof v==='string'&&values.includes(v as T));return v as T; }
export function arr<T>(v:unknown,decode:(v:unknown)=>T,max=200):T[] { invariant(Array.isArray(v)&&v.length<=max);return v.map(decode); }
export function nil<T>(v:unknown,decode:(v:unknown)=>T):T|null { return v===null?null:decode(v); }
export function bool(v:unknown):boolean { invariant(typeof v==='boolean');return v; }
export function timestamp(v:unknown):string { const s=str(v,32);invariant(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$/.test(s)&&Number.isFinite(Date.parse(s)));return s; }
export function key(v:unknown):string { const s=str(v,64);invariant(/^[A-Za-z][A-Za-z0-9_-]{0,63}$/.test(s));return s; }
export function code(v:unknown):string { const s=str(v,64);invariant(/^[A-Z][A-Z0-9_]{0,63}$/.test(s));return s; }
function coordinate(v:unknown):string { const s=str(v,7);invariant(/^(0|-?[1-9]\d{0,5})$/.test(s)&&Math.abs(Number(s))<=100000);return s; }
export function decodeExpression(value:unknown,depth=1,budget={count:0},version=1):FlowExpression {
  invariant(depth<=8&&++budget.count<=32);const kind=(value as Record<string,unknown>)?.kind;
  if(kind==='GROUP'){const r=exact(value,['kind','operator','children']);return {kind,operator:one(r.operator,['AND','OR','NOT']),children:arr(r.children,v=>decodeExpression(v,depth+1,budget,version),32)};}
  if(version===2){const r=exact(value,['kind','operator','operands']);invariant(kind==='PREDICATE');return {kind,operator:one(r.operator,['EQ','NE','GT','GTE','LT','LTE','IN','NOT_IN','BETWEEN','IS_NULL','IS_NOT_NULL']),operands:arr(r.operands,decodeVariableOperand,101)};}
  const r=exact(value,['kind','source','operator','values']);invariant(kind==='PREDICATE');const s=exact(r.source,['type','fieldCode']);
  return {kind,source:{type:one(s.type,['CONTEXT','NODE_RESULT']),fieldCode:str(s.fieldCode,64)},operator:one(r.operator,['EQ','NE','GT','GTE','LT','LTE','IN','NOT_IN','BETWEEN','IS_NULL','IS_NOT_NULL']),values:arr(r.values,v=>typeof v==='boolean'?v:str(v,512),100)};
}
export function decodeNode(v:unknown):FlowNode {const r=exact(v,['nodeKey','type','name','position','ruleSetVersionId','decisionCode','reasonCode','reasonMessage']);const p=exact(r.position,['x','y']);return {nodeKey:key(r.nodeKey),type:one(r.type,['START','RULE_SET','END']),name:str(r.name,100),position:{x:coordinate(p.x),y:coordinate(p.y)},ruleSetVersionId:nil(r.ruleSetVersionId,id),decisionCode:nil(r.decisionCode,v=>one(v,['ALLOW','REVIEW','DENY'])),reasonCode:nil(r.reasonCode,code),reasonMessage:nil(r.reasonMessage,str)};}
export function decodeEdge(v:unknown):FlowEdge {const r=exact(v,['edgeKey','sourceNodeKey','targetNodeKey','type','priority','name','condition']);let condition:FlowEdge['condition']=null;if(r.condition!==null){const c=exact(r.condition,['schemaVersion','root']);invariant(c.schemaVersion===1||c.schemaVersion===2);condition={schemaVersion:c.schemaVersion,root:decodeExpression(c.root,1,{count:0},c.schemaVersion)};invariant(new TextEncoder().encode(JSON.stringify(condition)).length<=8192);}const type=one(r.type,['UNCONDITIONAL','CONDITIONAL','ELSE']);invariant((type==='CONDITIONAL')===(condition!==null));return {edgeKey:key(r.edgeKey),sourceNodeKey:key(r.sourceNodeKey),targetNodeKey:key(r.targetNodeKey),type,priority:int(r.priority,0,100),name:str(r.name,100),condition};}
export function decodeGraph(value:unknown):FlowGraph {const r=exact(value,['schemaVersion','nodes','edges']);invariant(r.schemaVersion===1);const nodes=arr(r.nodes,decodeNode,50),edges=arr(r.edges,decodeEdge,100);invariant(new Set(nodes.map(n=>n.nodeKey)).size===nodes.length&&new Set(edges.map(e=>e.edgeKey)).size===edges.length);return {schemaVersion:1,nodes,edges};}
export interface FlowNavigation {view:'list'|'create'|'edit'|'detail'|'history'|'version'|'legacy'|'rule-reference';pointCode:string;flowCode:string;keyword:string;page:number;size:number;flowId:string;flowVersionId:string;maxVersionNo:number;pointVersionId:string;nodeKey:string;sourceType:'FLOW_VERSION'|'LEGACY_POINT';sourceVersionId:string}
export function parseFlowNavigation(search:string):FlowNavigation { const q=new URLSearchParams(search);const ident=(k:string)=>{try{return id(q.get(k));}catch{return '';}};const number=(k:string,max:number,fallback:number)=>{const s=q.get(k)??'';return /^[1-9]\d*$/.test(s)&&Number(s)<=max?Number(s):fallback;};let view:FlowNavigation['view']='list';const v=q.get('view');if(['create','edit','detail','history','version','legacy','rule-reference'].includes(v??''))view=v as FlowNavigation['view'];
  if(['edit','detail','history','version'].includes(view)&&!ident('flowId')||view==='version'&&!ident('flowVersionId')||view==='legacy'&&!ident('pointVersionId')||view==='rule-reference'&&(!ident('sourceVersionId')||!q.get('nodeKey')))view='list';
  return {view,pointCode:/^[A-Z][A-Z0-9_]{0,63}$/.test(q.get('pointCode')??'')?q.get('pointCode')!:'',flowCode:q.get('flowCode')??'',keyword:q.get('keyword')??'',page:number('page',100000,1),size:[10,20,50,100,200].includes(number('size',200,10))?number('size',200,10):10,flowId:ident('flowId'),flowVersionId:ident('flowVersionId'),maxVersionNo:number('maxVersionNo',2147483647,1),pointVersionId:ident('pointVersionId'),nodeKey:q.get('nodeKey')??'',sourceType:q.get('sourceType')==='LEGACY_POINT'?'LEGACY_POINT':'FLOW_VERSION',sourceVersionId:ident('sourceVersionId')}; }
export function flowSearch(n:FlowNavigation):string {const q=new URLSearchParams();for(const [k,v] of Object.entries(n)){if(v!==''&&!(k==='view'&&v==='list'))q.set(k,String(v));}return `?${q}`;}
