import test from 'node:test';
import assert from 'node:assert/strict';
import type { RequestClient } from '../api/session.ts';
import { createDecisionFlowApi, decodeFlowDetail, decodeFlowValidation } from './decisionFlowApi.ts';
import { initialGraph } from './decisionFlow.ts';
import type { FlowDetail } from './decisionFlow.ts';
import { ruleSetDetail } from './riskFixtures.ts';

const time='2026-09-12T10:00:00.000000Z';
function detail():FlowDetail {const graph=initialGraph();graph.nodes[1]!.decisionCode='ALLOW';return {
  flowId:'11',flowVersionId:'21',flowCode:'CHECKOUT_STANDARD',flowName:'结账检查',pointCode:'CHECKOUT_PREPARE',flowStatus:'CONFIGURABLE',currentFlowVersionId:'21',currentVersionNo:1,version:'1',versionNo:1,inputContractCode:'ORDER_PREPARATION',inputContractVersion:4,createdBy:'31',createTime:time,changeReason:'初始配置',graph,ruleSetReferences:[]};}
function responder(value:unknown,observe?:(path:string,options?:RequestInit)=>void):RequestClient{return {async request<T>(path:string,options?:RequestInit,decode?:(v:unknown)=>T){observe?.(path,options);assert.ok(decode);return decode(value);}};}

test('flow rule detail accepts an explicit absent legacy point and rejects missing or fabricated IDs',async()=>{
  const reference={sourceType:'FLOW_VERSION',sourceVersionId:'21',nodeKey:'rules',ruleSetStatus:'CONFIGURABLE',ruleSet:{...ruleSetDetail,pointVersionId:null}};
  assert.deepEqual(await createDecisionFlowApi(responder(reference)).reference('FLOW_VERSION','21','rules'),{ruleSetStatus:reference.ruleSetStatus,ruleSet:reference.ruleSet});
  for(const pointVersionId of [undefined,'','0',0,true,{}]) {
    await assert.rejects(createDecisionFlowApi(responder({...reference,ruleSet:{...reference.ruleSet,pointVersionId}})).reference('FLOW_VERSION','21','rules'));
  }
});

test('accurate history keeps deleted-root CAS and rejects mismatched current pointer',()=>{
  const deleted={...detail(),flowStatus:'DELETED',version:'2'};
  assert.equal(decodeFlowDetail(deleted).flowStatus,'DELETED');
  for(const bad of [{...deleted,version:'1'},{...detail(),currentFlowVersionId:'22'},{...detail(),version:1},{...detail(),runtimeStatus:'RUNNING'}])assert.throws(()=>decodeFlowDetail(bad));
});
test('rule references must match each persisted graph node and exact version',()=>{
  const value=detail();value.graph.nodes.push({...value.graph.nodes[0]!,nodeKey:'rules',type:'RULE_SET',ruleSetVersionId:'41'});
  value.ruleSetReferences.push({nodeKey:'rules',ruleSetId:'42',ruleSetCode:'CLIENT_CHECK',ruleSetName:'Client 检查',ruleSetVersionId:'41',versionNo:1,ruleCount:1,ruleSetStatus:'DELETED',versionStatus:'RETIRED'});
  assert.deepEqual(decodeFlowDetail(value),value);
  assert.throws(()=>decodeFlowDetail({...value,ruleSetReferences:[]}));
  assert.throws(()=>decodeFlowDetail({...value,ruleSetReferences:[{...value.ruleSetReferences[0],ruleSetVersionId:'43'}]}));
});
test('current and historical endpoints reject another flow or another version',async()=>{
  const api=createDecisionFlowApi(responder(detail()));
  await assert.rejects(api.current('12'));await assert.rejects(api.version('11','22'));
  assert.equal((await api.version('11','21')).flowVersionId,'21');
  await assert.rejects(createDecisionFlowApi(responder({...detail(),flowStatus:'DELETED',version:'2'})).current('11'));
});
test('delete uses query CAS and idempotency header without body or JSON Content-Type',async()=>{
  let calls=0;const receipt={flowId:'11',status:'DELETED',version:'2',deletedBy:'31',deletedTime:time};
  const api=createDecisionFlowApi(responder(receipt,(path,options)=>{calls++;assert.equal(path,'/decision-flows/v1/flows/11?expectedVersion=1');assert.equal(options?.method,'DELETE');assert.equal(options.body,undefined);assert.equal(new Headers(options.headers).get('Idempotency-Key'),'rco17_delete_command');assert.equal(new Headers(options.headers).has('Content-Type'),false);}));
  await api.delete('11','1','rco17_delete_command','31');assert.equal(calls,1);
  await assert.rejects(createDecisionFlowApi(responder({...receipt,flowId:'12'})).delete('11','1','rco17_delete_command','31'));
});
test('save receipt is bound to actor and next CAS; validation never sends a command key',async()=>{
  const receipt={flowId:'11',flowVersionId:'22',versionNo:2,version:'2',savedBy:'31',savedTime:time},payload=JSON.stringify({expectedVersion:'1'});
  assert.equal((await createDecisionFlowApi(responder(receipt)).save(payload,'rco17_save_command','31')).version,'2');
  for(const bad of [{...receipt,savedBy:'32'},{...receipt,version:'3',versionNo:3}])await assert.rejects(createDecisionFlowApi(responder(bad)).save(payload,'rco17_save_command','31'));
  const valid={valid:true,issues:[],truncated:false};await createDecisionFlowApi(responder(valid,(_path,options)=>assert.equal(new Headers(options?.headers).has('Idempotency-Key'),false))).validate('{}');
  assert.throws(()=>decodeFlowValidation({...valid,truncated:true}));assert.throws(()=>decodeFlowValidation({...valid,valid:false}));
});
