import assert from 'node:assert/strict';
import test from 'node:test';
import { createVariableApi, decodeVariableOperand, decodeVariablePage, deduplicatePastedMembers, literalOperand, matchesVariableFilters, normalizeLiteral, parsePastedMembers, prepareOperands, variableOperand } from './riskVariable.ts';
import type { VariableSummary } from './riskVariable.ts';
import { convertLegacyOperands } from './variableEditing.ts';
import { decodeExpression, newFlowExpression } from './decisionFlow.ts';
import { matchAuthoringContext } from './riskAuthoringCatalog.ts';
import { authoringContext } from './riskFixtures.ts';
export const arbitraryVariable:VariableSummary={variableCode:'NEW_OPERATOR_DEFINED_ALIAS',variableName:'运营新建别名',sourceType:'INPUT',valueType:'NUMBER',pointCode:'CHECKOUT_PREPARE',usages:['RULE_CONDITION','FLOW_CONDITION'],systemManaged:false,status:'ACTIVE',version:'1',numericUnit:'MONEY',currencyCode:null,currencyVariableCode:'ANOTHER_CURRENCY_ALIAS',formatCode:'NONE',allowedValuesVariableCode:null,currentValue:null,memberCount:null,previewMembers:[],updateTime:'2026-09-13T00:00:00.000000Z'};

test('variable catalog accepts newly registered codes and explicit batch missingCodes without a static fallback',()=>{
  const raw={items:[arbitraryVariable],valuesAsOf:arbitraryVariable.updateTime,missingCodes:['ABSENT']};assert.deepEqual(decodeVariablePage(raw),raw);
  assert.throws(()=>decodeVariablePage({...raw,total:1}));assert.throws(()=>decodeVariablePage({...raw,items:[{...arbitraryVariable,currentValue:3}]}));
  assert.throws(()=>decodeVariablePage({...raw,items:[{...arbitraryVariable,secret:'hidden'}]}));
});
test('both operand sides are variables or typed literals and units survive wire round-trip',()=>{
  const left=variableOperand(arbitraryVariable),right=variableOperand({...arbitraryVariable,variableCode:'ANY_NEW_THRESHOLD',sourceType:'CONSTANT',currentValue:'8000.00000000'},'RIGHT');
  assert.equal(left.numericUnit,null);assert.equal(left.currencyVariableCode,null);
  assert.deepEqual(prepareOperands('EQ',[left,right]),[left,right]);
  assert.throws(()=>decodeVariableOperand({...left,numericUnit:'MONEY',currencyVariableCode:arbitraryVariable.currencyVariableCode}));
  const literal={...literalOperand('NUMBER','LEFT','8000'),numericUnit:'MONEY' as const,currencyVariableCode:arbitraryVariable.currencyVariableCode};
  assert.equal(prepareOperands('GT',[literal,right])[0]?.literalValue,'8000.00000000');
  assert.deepEqual(prepareOperands('IS_NULL',[left]),[left]);assert.throws(()=>prepareOperands('EQ',[left]));
  assert.throws(()=>decodeVariableOperand({...left,fieldCode:'net_amount'}));assert.throws(()=>decodeVariableOperand({...left,literalValue:'runtime-value'}));
});
test('collection paste detects normalized numeric duplicates and preserves exact string identity',()=>{
  assert.throws(()=>parsePastedMembers('1\n1.00000000','NUMBER','NONE'),/第 2 行.*重复/);
  assert.deepEqual(parsePastedMembers('001\t前导零\nA\nA \n','STRING','TEXT').map(m=>m.value),['001','A','A ']);
  assert.throws(()=>parsePastedMembers('true\nyes','BOOLEAN','NONE'),/第 2 行/);
  assert.deepEqual(parsePastedMembers('false\t否\ntrue\t是','BOOLEAN','NONE').map(m=>m.value),[false,true]);
  assert.throws(()=>parsePastedMembers('US\nUSA','STRING','UPPER_ALPHA_2'),/第 2 行/);
});
test('explicit collection deduplication retains the first label and reports duplicate source rows',()=>{
  assert.deepEqual(deduplicatePastedMembers('1\t首次\n1.0\t后续\n2','NUMBER','NONE'),{
    members:[{value:'1.00000000',displayName:'首次'},{value:'2.00000000',displayName:null}],duplicateRows:[2],
  });
  assert.deepEqual(deduplicatePastedMembers('A\nA \nA','STRING','TEXT').duplicateRows,[3]);
  assert.throws(()=>deduplicatePastedMembers('true\nyes','BOOLEAN','NONE'),/第 2 行/);
});
test('selected variables outside the current point, usage or node are unavailable',()=>{
  assert.equal(matchesVariableFilters(arbitraryVariable,{pointCode:'CHECKOUT_PREPARE',usage:'RULE_CONDITION'}),true);
  assert.equal(matchesVariableFilters(arbitraryVariable,{pointCode:'ANOTHER_POINT'}),false);
  assert.equal(matchesVariableFilters(arbitraryVariable,{usage:'ACTION_PARAMETER'}),false);
  const output={...arbitraryVariable,sourceType:'NODE_OUTPUT' as const,usages:['FLOW_CONDITION' as const]};
  assert.equal(matchesVariableFilters(output,{usage:'FLOW_CONDITION',nodeType:'START'}),false);
  assert.equal(matchesVariableFilters(output,{usage:'FLOW_CONDITION',nodeType:'RULE_SET'}),true);
});
test('operand preparation rejects malformed sequencing, incompatible types and invalid literal units',()=>{
  const left=variableOperand(arbitraryVariable),right=literalOperand('NUMBER','RIGHT','1');
  assert.throws(()=>prepareOperands('EQ',[left,{...right,valueSequence:2}]));
  assert.throws(()=>prepareOperands('EQ',[left,literalOperand('STRING','RIGHT','1')]));
  assert.throws(()=>prepareOperands('GT',[literalOperand('BOOLEAN','LEFT',true),literalOperand('BOOLEAN','RIGHT',false)]));
  assert.throws(()=>prepareOperands('EQ',[left,{...right,numericUnit:'MONEY',currencyCode:null,currencyVariableCode:null}]));
  assert.throws(()=>prepareOperands('IN',[left,variableOperand(arbitraryVariable,'RIGHT'),right]));
});
test('generic scalar formats preserve precision, reject overflow and do not normalize ordinary text',()=>{
  assert.equal(normalizeLiteral('-0.50000000','NUMBER'),'-0.50000000');assert.equal(normalizeLiteral('-0','NUMBER'),'0.00000000');
  assert.equal(normalizeLiteral('9999999999999999.99999999','NUMBER'),'9999999999999999.99999999');
  for(const v of ['1e3','1.000000001','10000000000000000'])assert.throws(()=>normalizeLiteral(v,'NUMBER'));
  assert.throws(()=>normalizeLiteral('9223372036854775808','DURATION'));assert.throws(()=>normalizeLiteral('9223372036854775808','STRING','POSITIVE_ID'));
  assert.equal(normalizeLiteral(' a ','STRING','TEXT'),' a ');assert.throws(()=>normalizeLiteral('\u0000','STRING','TEXT'));
  assert.equal(normalizeLiteral('en-us','STRING','BCP47'),'en-US');
});
test('datetime enforces valid calendar dates and exact UTC microseconds',()=>{
  assert.equal(normalizeLiteral('2026-09-13T01:02:03.000001Z','DATETIME'),'2026-09-13T01:02:03.000001Z');
  for(const v of ['2026-02-31T00:00:00.000000Z','2026-09-13T00:00:00Z','2026-09-13T00:00:00.1234567Z'])assert.throws(()=>normalizeLiteral(v,'DATETIME'));
});
test('legacy editing uses returned alias metadata and leaves the original literal and source unchanged',()=>{
  const old=[{operandRole:'LEFT' as const,valueSequence:1,sourceType:'CONTEXT' as const,fieldCode:'owner_field',valueType:'NUMBER' as const,literalValue:null},{operandRole:'RIGHT' as const,valueSequence:1,sourceType:'LITERAL' as const,fieldCode:null,valueType:'NUMBER' as const,literalValue:'8000.00000000'}];
  const before=structuredClone(old),result=convertLegacyOperands(old,field=>{assert.equal(field,'owner_field');return arbitraryVariable;});
  assert.deepEqual(old,before);assert.equal(result[0]?.variableCode,arbitraryVariable.variableCode);assert.equal(result[1]?.variableCode,null);assert.equal(result[1]?.literalValue,'8000.00000000');assert.equal(result[1]?.currencyVariableCode,arbitraryVariable.currencyVariableCode);
});
test('node output is an ordinary registered variable and DENY can be a branch comparison value',()=>{
  const node=variableOperand({...arbitraryVariable,variableCode:'ANOTHER_NODE_RESULT',sourceType:'NODE_OUTPUT',valueType:'STRING',numericUnit:null,currencyVariableCode:null});
  const expression={kind:'PREDICATE',operator:'EQ',operands:[node,literalOperand('STRING','RIGHT','DENY')]};
  assert.deepEqual(decodeExpression(expression,1,{count:0},2),expression);assert.throws(()=>decodeExpression({...expression,source:{type:'NODE_RESULT',fieldCode:'matched'}},1,{count:0},2));
  const blank=newFlowExpression();assert.equal(blank.kind,'PREDICATE');assert.ok('operands' in blank);assert.equal(blank.operands[0]?.variableCode,null);
});
test('current authoring admits dynamic metadata and never restores a fixed field list',()=>{
  const current={...authoringContext,descriptorCode:'RISK_VARIABLE_AUTHORING',operandFormat:'VARIABLE_REFERENCE',variableQueryContext:{pointCode:'CHECKOUT_PREPARE',inputContractCode:'ORDER_PREPARATION',inputContractVersion:4,usage:'RULE_CONDITION' as const},fields:[]};
  const result=matchAuthoringContext(current);assert.equal(result.editable,true);if(result.editable)assert.deepEqual(result.catalog.fields,[]);
  assert.equal(matchAuthoringContext(authoringContext).editable,false);
});
test('API search preserves server paging and member writes have no invented idempotency identity',async()=>{
  const calls:{path:string;options?:RequestInit}[]=[];
  const api=createVariableApi({async request(path,options,decode){calls.push({path,options});return decode!({items:[arbitraryVariable],page:2,size:20,total:21,valuesAsOf:arbitraryVariable.updateTime});}});
  await api.list({keyword:'新&别名',usage:'RULE_CONDITION',pointCode:'CHECKOUT_PREPARE',page:2,size:20});
  assert.equal(new URLSearchParams(calls[0]!.path.split('?')[1]).get('keyword'),'新&别名');assert.equal(calls[0]?.options?.method,undefined);
});
