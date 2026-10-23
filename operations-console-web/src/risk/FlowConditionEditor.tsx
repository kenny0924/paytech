import { Button, Select, Space } from 'antd';
import { newFlowExpression } from './decisionFlow';
import type { FlowContext, FlowExpression } from './decisionFlow';
import { operatorLabel } from './riskVariable';
import { enumLabel, translator } from '../i18n/index';
import { useT } from '../i18n/react';
import { VariablePredicateEditor } from './VariableControls';
/** 分组显示名是 `risk.enum.flowConditionGroup.<值>`。 */
const groups=['AND','OR','NOT'] as const;
const t=translator('risk');
export function expressionText(expression:FlowExpression,context:FlowContext|null):string {
  if(expression.kind==='GROUP')return t('flowCondition.groupText',{group:enumLabel(t,'enum.flowConditionGroup',expression.operator),children:expression.children.map(e=>expressionText(e,context)).join(t('flowCondition.childSeparator'))});
  if('operands' in expression){const operandText=(side:string)=>expression.operands.filter(o=>o.operandRole===side).map(o=>o.sourceType==='VARIABLE'?t('flowCondition.variable',{code:o.variableCode??t('flowCondition.unselected')}):String(o.literalValue)).join(t('evaluation.valueSeparator'));return `${operandText('LEFT')} ${operatorLabel(expression.operator)} ${operandText('RIGHT')}`;}
  const name=expression.source.type==='NODE_RESULT'?context?.nodeOutputs.find(f=>f.fieldCode===expression.source.fieldCode)?.label??t('flowCondition.nodeResult',{field:expression.source.fieldCode}):context?.fields.find(f=>f.fieldCode===expression.source.fieldCode)?.label??expression.source.fieldCode;
  return `${name} ${operatorLabel(expression.operator)} ${expression.values.map(v=>v===true?t('evaluation.literal.true'):v===false?t('evaluation.literal.false'):v).join(t('evaluation.valueSeparator'))}`;
}
function count(e:FlowExpression):number {return e.kind==='PREDICATE'?1:1+e.children.reduce((n,c)=>n+count(c),0);}
export function FlowConditionEditor({value,context,readonly,onChange,depth=1,remaining=32,nodeType='RULE_SET'}:{value:FlowExpression;context:FlowContext|null;readonly:boolean;onChange:(v:FlowExpression)=>void;depth?:number;remaining?:number;nodeType?:string}) {
  const tr=useT('risk');
  if(readonly)return <p className="flow-read-value">{expressionText(value,context)}</p>;
  if(value.kind==='GROUP')return <div className="flow-condition-group"><Space><Select aria-label={tr('flowCondition.groupMode')} value={value.operator} options={groups.map(value=>({value,label:enumLabel(tr,'enum.flowConditionGroup',value)}))} onChange={operator=>onChange({...value,operator,children:operator==='NOT'?value.children.slice(0,1):value.children})}/><Button type="text" disabled={value.children.length!==1} onClick={()=>onChange(value.children[0]!)}>{tr('flowCondition.ungroup')}</Button></Space>
    {value.children.map((child,index)=><div className="flow-condition-child" key={index}><FlowConditionEditor value={child} context={context} nodeType={nodeType} readonly={false} depth={depth+1} remaining={remaining-count(value)+count(child)} onChange={next=>onChange({...value,children:value.children.map((v,i)=>i===index?next:v)})}/><Button size="small" danger disabled={value.children.length===1} onClick={()=>onChange({...value,children:value.children.filter((_,i)=>i!==index)})}>{tr('flowCondition.remove')}</Button></div>)}
    {value.operator!=='NOT'&&<Button type="link" disabled={count(value)>=remaining||depth>=8} onClick={()=>onChange({...value,children:[...value.children,newFlowExpression()]})}>{tr('flowCondition.add')}</Button>}</div>;
  if(!('operands' in value))return <p className="flow-read-value">{tr('flowCondition.legacy',{text:expressionText(value,context)})}</p>;
  return <div className="flow-predicate"><VariablePredicateEditor operator={value.operator} operands={value.operands} filters={{pointCode:context?.pointCode,usage:'FLOW_CONDITION',nodeType}} onChange={(operator,operands)=>onChange({kind:'PREDICATE',operator,operands})}/>
    <Button type="link" size="small" disabled={depth>=8||remaining<3} onClick={()=>onChange({kind:'GROUP',operator:'AND',children:[value,newFlowExpression()]})}>{tr('flowCondition.addGroup')}</Button></div>;
}
