import { Button, Select, Space } from 'antd';
import { newFlowExpression } from './decisionFlow';
import type { FlowContext, FlowExpression } from './decisionFlow';
import { operatorLabels } from './riskVariable';
import { VariablePredicateEditor } from './VariableControls';
const groups={AND:'满足全部条件',OR:'满足任一条件',NOT:'不满足以下条件'};
export function expressionText(expression:FlowExpression,context:FlowContext|null):string {
  if(expression.kind==='GROUP')return `${groups[expression.operator]}（${expression.children.map(e=>expressionText(e,context)).join('；')}）`;
  if('operands' in expression){const operandText=(side:string)=>expression.operands.filter(o=>o.operandRole===side).map(o=>o.sourceType==='VARIABLE'?`变量 ${o.variableCode??'未选择'}`:String(o.literalValue)).join('、');return `${operandText('LEFT')} ${operatorLabels[expression.operator as keyof typeof operatorLabels]??expression.operator} ${operandText('RIGHT')}`;}
  const name=expression.source.type==='NODE_RESULT'?context?.nodeOutputs.find(f=>f.fieldCode===expression.source.fieldCode)?.label??`本节点结果 · ${expression.source.fieldCode}`:context?.fields.find(f=>f.fieldCode===expression.source.fieldCode)?.label??expression.source.fieldCode;
  return `${name} ${operatorLabels[expression.operator as keyof typeof operatorLabels]??expression.operator} ${expression.values.map(v=>v===true?'是':v===false?'否':v).join('、')}`;
}
function count(e:FlowExpression):number {return e.kind==='PREDICATE'?1:1+e.children.reduce((n,c)=>n+count(c),0);}
export function FlowConditionEditor({value,context,readonly,onChange,depth=1,remaining=32,nodeType='RULE_SET'}:{value:FlowExpression;context:FlowContext|null;readonly:boolean;onChange:(v:FlowExpression)=>void;depth?:number;remaining?:number;nodeType?:string}) {
  if(readonly)return <p className="flow-read-value">{expressionText(value,context)}</p>;
  if(value.kind==='GROUP')return <div className="flow-condition-group"><Space><Select aria-label="条件分组方式" value={value.operator} options={Object.entries(groups).map(([value,label])=>({value,label}))} onChange={operator=>onChange({...value,operator,children:operator==='NOT'?value.children.slice(0,1):value.children})}/><Button type="text" disabled={value.children.length!==1} onClick={()=>onChange(value.children[0]!)}>取消分组</Button></Space>
    {value.children.map((child,index)=><div className="flow-condition-child" key={index}><FlowConditionEditor value={child} context={context} nodeType={nodeType} readonly={false} depth={depth+1} remaining={remaining-count(value)+count(child)} onChange={next=>onChange({...value,children:value.children.map((v,i)=>i===index?next:v)})}/><Button size="small" danger disabled={value.children.length===1} onClick={()=>onChange({...value,children:value.children.filter((_,i)=>i!==index)})}>移除此条件</Button></div>)}
    {value.operator!=='NOT'&&<Button type="link" disabled={count(value)>=remaining||depth>=8} onClick={()=>onChange({...value,children:[...value.children,newFlowExpression()]})}>＋ 添加条件</Button>}</div>;
  if(!('operands' in value))return <p className="flow-read-value">{expressionText(value,context)} · 旧合同，请刷新编辑页以转换为变量引用。</p>;
  return <div className="flow-predicate"><VariablePredicateEditor operator={value.operator} operands={value.operands} filters={{pointCode:context?.pointCode,usage:'FLOW_CONDITION',nodeType}} onChange={(operator,operands)=>onChange({kind:'PREDICATE',operator,operands})}/>
    <Button type="link" size="small" disabled={depth>=8||remaining<3} onClick={()=>onChange({kind:'GROUP',operator:'AND',children:[value,newFlowExpression()]})}>＋ 添加组合条件</Button></div>;
}
