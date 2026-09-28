import test from 'node:test';
import assert from 'node:assert/strict';
import { decodeDictionary, dictionaryWriteOutcome } from './riskDictionary.ts';
import { ApiError } from '../api/session.ts';
const reason = { reasonCode: 'RISK_REVIEW', name: '风险复核', description: null, defaultMessage: '人工复核', status: 'ACTIVE', version: '1', createdBy: '23', updatedBy: '23', createTime: '2026-09-11T10:00:00.000000Z', updateTime: '2026-09-11T10:00:00.000000Z' };
test('字典读取保留代码和默认消息，拒绝越界版本、隐藏主体字段与缺失可空键', () => {
  assert.equal(decodeDictionary('rejection-reasons', reason).code, 'RISK_REVIEW');
  for (const bad of [{ ...reason, version: '9223372036854775808' }, { ...reason, subjectTypes: ['CLIENT'] }, Object.fromEntries(Object.entries(reason).filter(([key]) => key !== 'description'))])
    assert.throws(() => decodeDictionary('rejection-reasons', bad));
});
test('不确定字典写入需要人工查询，CAS 冲突保留草稿且不自动重试', () => {
  assert.equal(dictionaryWriteOutcome(new Error('connection lost')), 'UNKNOWN');
  assert.equal(dictionaryWriteOutcome(new ApiError(502, 'UPSTREAM_INVALID_RESPONSE')), 'UNKNOWN');
  assert.equal(dictionaryWriteOutcome(new ApiError(409, 'RISK_DICTIONARY_CHANGED')), 'CONFLICT');
});

test('字典反查保留已删除根的当前指针事实，不将它误当作仍在配置管理',async()=>{
  const { createRiskDictionaryApi }=await import('./riskDictionary.ts');
  const api=createRiskDictionaryApi({async request<T>(_path:string,_init?:RequestInit,decode?: (value:unknown)=>T):Promise<T>{
    return decode!({page:1,size:10,total:1,items:[{sourceType:'RULE_SET',flowId:null,flowCode:null,flowName:null,flowVersionId:null,flowVersionNo:null,nodeKey:null,nodeName:null,flowStatus:null,ruleSetId:'12',pointCode:'CHECKOUT_PREPARE',ruleSetCode:'RULE_SET',ruleSetName:'规则集',ruleSetVersionId:'15',versionNo:3,ruleCode:'DENY_RULE',ruleName:'拒绝规则',referenceKind:'REJECTION_REASON',actionSequenceNo:null,targetBindingCode:null,ruleSetStatus:'DELETED',isCurrentConfiguration:true,activeEnvironments:[]}]});
  }});
  const result=await api.references('rejection-reasons','REASON',{page:1,size:10},'DELETED');assert.equal(result.items[0]!.isCurrentConfiguration,true);assert.equal(result.items[0]!.ruleSetStatus,'DELETED');
});
test('字典写入不发送幂等 Header，未知结果只发出一次请求',async()=>{
  const { createRiskDictionaryApi }=await import('./riskDictionary.ts');let calls=0;
  const api=createRiskDictionaryApi({async request<T>(_path:string,init?:RequestInit):Promise<T>{calls++;assert.equal(new Headers(init?.headers).has('Idempotency-Key'),false);throw new TypeError('connection lost');}});
  await assert.rejects(api.write('subject-tags','CREATE','TAG',{tagCode:'TAG',name:'标签',description:null},'23'));assert.equal(calls,1);
});
test('历史按根和固定版本上界校验，不能把其他规则集的响应显示在当前页',async()=>{
  const { createRiskDictionaryApi }=await import('./riskDictionary.ts');
  const api=createRiskDictionaryApi({async request<T>(path:string,_init?:RequestInit,decode?:(value:unknown)=>T):Promise<T>{
    assert.equal(new URL(path,'http://test.invalid').searchParams.get('maxVersionNo'),'3');
    return decode!({page:1,size:10,total:1,items:[{ruleSetId:'99',ruleSetVersionId:'15',pointCode:'CHECKOUT_PREPARE',ruleSetCode:'OTHER_SET',ruleSetName:'其他规则集',versionNo:2,status:'FROZEN',isCurrent:false,createdBy:'23',changeReason:'初始版本',createTime:reason.createTime}]});
  }});await assert.rejects(api.history('12',{page:1,size:10},3));
});
test('可空说明遵循字符合同，允许仅空格而不将数据库事实静默改写',()=>{
  assert.equal(decodeDictionary('rejection-reasons',{...reason,description:'  '}).description,'  ');
});
