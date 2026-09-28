import type { RuleAuthoringContext } from './riskApiModels.ts';
import type { VariableQueryContext } from './riskVariable.ts';
import { operatorLabels } from './riskVariable.ts';
import type { AuthoringCatalog as LegacyCatalog } from './legacyRiskAuthoringCatalog.ts';
export { CHECKOUT_PREPARE_AUTHORING_V1 } from './legacyRiskAuthoringCatalog.ts';
export type { AuthoringFieldDisplay, AuthoringWidget, AuthoringOperatorDisplay, AuthoringActionDisplay } from './legacyRiskAuthoringCatalog.ts';
export interface AuthoringCatalog extends LegacyCatalog { operandFormat?: 'VARIABLE_REFERENCE'; variableQueryContext?: VariableQueryContext; }
export type AuthoringMatch = {editable:true;catalog:AuthoringCatalog}|{editable:false;reason:string};
/** 变量候选另向变量库分页查询，这里只接收 Owner 的通用编辑能力。 */
export function matchAuthoringContext(context:RuleAuthoringContext):AuthoringMatch {
  if(context.descriptorCode!=='RISK_VARIABLE_AUTHORING'||context.operandFormat!=='VARIABLE_REFERENCE'||!context.variableQueryContext)
    return {editable:false,reason:'请刷新以使用统一变量编辑合同。'};
  if(context.features.length||context.obligations.length||context.limitBindings.length)
    return {editable:false,reason:'当前目录包含尚未支持的能力。'};
  return {editable:true,catalog:{descriptorCode:context.descriptorCode,operandFormat:'VARIABLE_REFERENCE',variableQueryContext:context.variableQueryContext,
    fields:[],operators:context.operators.map(operator=>({operator,label:operatorLabels[operator]})),
    actions:context.actions.filter(a=>a.terminalFlag).map(a=>({actionCode:a.actionCode,label:a.actionName})),
    additionalActions:context.actions.filter(a=>!a.terminalFlag),targetBindings:context.targetBindings,actionParameters:context.actionParameters}};
}
