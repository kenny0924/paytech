import { configurationContext, instant, ruleSetDetail } from './riskFixtures.ts';
import type { RuleAuthoringContext, RuleSetDetail } from './riskApiModels.ts';
import type { VariableContext, VariableFilters, VariableSummary } from './riskVariable.ts';
import { ruleForVariableEditing } from './variableEditing.ts';

/** 测试 Owner 目录；编码刻意不依赖真实初始化目录。 */
export const variableContext: RuleAuthoringContext = {
  ...configurationContext, descriptorCode:'RISK_VARIABLE_AUTHORING', operandFormat:'VARIABLE_REFERENCE',
  variableQueryContext:{pointCode:'CHECKOUT_PREPARE',inputContractCode:'ORDER_PREPARATION',inputContractVersion:4,usage:'RULE_CONDITION'},
  fields:configurationContext.fields.map((f,i)=>({...f,variableCode:`REGISTERED_FIELD_${i}`})),
  targetBindings:configurationContext.targetBindings.map(t=>({...t,requiredFields:[],subjectSemanticRole:'CLIENT_ID'})),
  actionParameters:configurationContext.actionParameters.map(p=>({...p,allowedSourceType:p.targetParameterFlag?'VARIABLE':p.allowedSourceType})),
};
export const variableDefinitions:VariableSummary[]=variableContext.fields.map(f=>({
  variableCode:f.variableCode!,variableName:`目录名称 ${f.fieldCode}`,sourceType:'INPUT',valueType:f.valueType,
  pointCode:'CHECKOUT_PREPARE',usages:f.actionParameterFlag?['ACTION_PARAMETER']:['RULE_CONDITION','FLOW_CONDITION'],
  systemManaged:true,status:'ACTIVE',version:'1',numericUnit:f.valueType==='NUMBER'?'SCALAR':null,
  currencyCode:null,currencyVariableCode:null,formatCode:f.valueType==='STRING'?'TEXT':'NONE',allowedValuesVariableCode:null,
  currentValue:null,memberCount:null,previewMembers:[],updateTime:instant,
}));
export const editingCatalog = {
  async list(filters:VariableFilters={}) {
    const codes=String(filters.codes??'').split(',');
    return {items:variableDefinitions.filter(v=>codes.includes(v.variableCode)),missingCodes:codes.filter(c=>!variableDefinitions.some(v=>v.variableCode===c)),valuesAsOf:instant};
  },
  async context():Promise<VariableContext> {return {supportedPoints:[],sources:[],valueTypes:[],formatProfiles:[],numericUnits:[],limits:{memberCount:1000,memberBodyBytes:65536,literalSetCount:100},contracts:[{
    contractCode:'ORDER_PREPARATION',contractVersion:4,pointCode:'CHECKOUT_PREPARE',sourceType:'INPUT',nodeType:null,
    fields:variableContext.fields.map(f=>({fieldKey:f.fieldCode,valueType:f.valueType,required:f.requiredFlag,dataClassification:f.dataClassification,allowedUsages:f.actionParameterFlag?['ACTION_PARAMETER']:['RULE_CONDITION','FLOW_CONDITION'],numericUnit:null,currencyFieldKey:null,semanticRole:f.actionParameterFlag?'CLIENT_ID':null})),
  }]};},
};
export const editHistoricalRule = (detail:RuleSetDetail,context:RuleAuthoringContext,signal?:AbortSignal)=>ruleForVariableEditing(detail,context,signal,editingCatalog);
export const variableRuleSetDetail = {...await editHistoricalRule(ruleSetDetail,variableContext),valuesAsOf:instant,variableIssues:[]};
