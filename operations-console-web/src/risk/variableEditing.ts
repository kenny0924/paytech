import type { RuleAuthoringContext, RuleOperand, RuleSetDetail } from './riskApiModels.ts';
import type { FlowContext, FlowExpression, FlowGraph } from './decisionFlow.ts';
import { variableApi, variableOperand } from './riskVariable.ts';
import type { VariableOperand, VariableSummary } from './riskVariable.ts';
import { translator } from '../i18n/index.ts';

const t = translator('risk');

/** 仅为编辑建立新草稿；映射来自 Owner 返回的系统变量，不按业务字段猜编码。 */
export function convertLegacyOperands(operands:readonly RuleOperand[],resolve:(field:string)=>VariableSummary):VariableOperand[] {
  const context=operands.find(o=>o.sourceType==='CONTEXT');
  if(!context?.fieldCode)throw new Error(t('variableEditing.missingSourceField'));
  const variable=resolve(context.fieldCode),binding=variableOperand(variable,context.operandRole);
  return operands.map(o=>o.sourceType==='CONTEXT'?{...binding,valueSequence:o.valueSequence}:{...binding,...o,sourceType:'LITERAL',variableCode:null,fieldCode:null,numericUnit:variable.numericUnit,currencyCode:variable.currencyCode,currencyVariableCode:variable.currencyVariableCode});
}
type EditingCatalog = Pick<typeof variableApi, 'list' | 'context'>;
async function definitions(codes:string[],signal:AbortSignal|undefined,api:EditingCatalog) {
  const result=new Map<string,VariableSummary>();const unique=[...new Set(codes)];
  for(let i=0;i<unique.length;i+=100){const r=await api.list({codes:unique.slice(i,i+100).join(',')},signal);if(r.missingCodes?.length)throw new Error(t('variableEditing.missingMapping'));r.items.forEach(v=>result.set(v.variableCode,v));}
  return result;
}
function selected(map:Map<string,VariableSummary>,code:string|undefined|null):VariableSummary {const v=code?map.get(code):undefined;if(!v||v.status!=='ACTIVE')throw new Error(t('variableEditing.sourceInactive'));return v;}
export async function ruleForVariableEditing(detail:RuleSetDetail,context:RuleAuthoringContext,signal?:AbortSignal,api:EditingCatalog=variableApi):Promise<RuleSetDetail> {
  if(detail.operandFormat==='VARIABLE_REFERENCE')return detail;
  if(context.operandFormat!=='VARIABLE_REFERENCE')throw new Error(t('variableEditing.catalogNotReady'));
  const map=await definitions(context.fields.map(f=>f.variableCode).filter((v):v is string=>!!v),signal,api);
  const source=(field:string)=>selected(map,context.fields.find(f=>f.fieldCode===field)?.variableCode);
  const engineering=detail.rules.some(r=>r.additionalActions.length)?await api.context(signal):null;
  const rules=detail.rules.map(rule=>({...rule,conditionNodes:rule.conditionNodes.map(node=>node.nodeType==='PREDICATE'?{...node,operands:convertLegacyOperands(node.operands,source)}:node),additionalActions:rule.additionalActions.map(action=>{
    const role=context.targetBindings.find(t=>t.bindingCode===action.targetBindingCode)?.subjectSemanticRole;
    const contract=engineering?.contracts.find(c=>c.contractCode===context.point.inputContractCode&&c.contractVersion===context.point.inputContractVersion&&c.pointCode===context.point.pointCode);
    const field=contract?.fields.find(f=>f.semanticRole===role&&f.allowedUsages.includes('ACTION_PARAMETER'));
    if(!field)throw new Error(t('variableEditing.subjectMapping'));return {...action,subjectVariableCode:source(field.fieldKey).variableCode};
  })}));
  return {...detail,operandFormat:'VARIABLE_REFERENCE',rules,currentVariables:[...map.values()]};
}
export async function flowForVariableEditing(graph:FlowGraph,context:FlowContext,signal?:AbortSignal,api:EditingCatalog=variableApi):Promise<FlowGraph> {
  if(!graph.edges.some(e=>e.condition?.schemaVersion===1))return graph;
  const codes=[...context.fields,...context.nodeOutputs].map(f=>f.variableCode).filter((c):c is string=>!!c),map=await definitions(codes,signal,api);
  function convert(expression:FlowExpression):FlowExpression {
    if(expression.kind==='GROUP')return {...expression,children:expression.children.map(convert)};
    if('operands' in expression)return expression;
    const candidates=expression.source.type==='CONTEXT'?context.fields:context.nodeOutputs;
    const variable=selected(map,candidates.find(f=>f.fieldCode===expression.source.fieldCode)?.variableCode),left=variableOperand(variable);
    return {kind:'PREDICATE',operator:expression.operator,operands:[left,...expression.values.map((value,i):VariableOperand=>({...left,operandRole:'RIGHT',valueSequence:i+1,sourceType:'LITERAL',variableCode:null,literalValue:value,numericUnit:variable.numericUnit,currencyCode:variable.currencyCode,currencyVariableCode:variable.currencyVariableCode}))]};
  }
  return {...graph,edges:graph.edges.map(e=>e.condition?.schemaVersion===1?{...e,condition:{schemaVersion:2,root:convert(e.condition.root)}}:e)};
}
