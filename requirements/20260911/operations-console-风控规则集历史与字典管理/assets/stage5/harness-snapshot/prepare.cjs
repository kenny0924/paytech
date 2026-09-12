const h=require('./harness.cjs'),fs=require('node:fs'),assert=require('node:assert/strict');
const {owned,persist,owner,rules,dict,dir}=h;
async function call(method,path,body){const r=await owner(method,path,body);assert.equal(r.status,200,r.text);return r.json;}
(async()=>{const suffix=crypto.randomUUID().replaceAll('-','').slice(0,12).toUpperCase(),code='RCO16_BROWSER_'+suffix,reason='RCO16_REASON_'+suffix,tag='RCO16_TAG_'+suffix;
 owned.codes.push(code);owned.dictionaries.push({kind:'rejection-reasons',code:reason},{kind:'subject-tags',code:tag});persist();
 await call('POST',dict+'rejection-reasons',{reasonCode:reason,name:'需要人工复核',description:'本地验收合成字典',defaultMessage:'请人工复核当前订单。'});
 await call('POST',dict+'subject-tags',{tagCode:tag,name:'待复核主体',description:'人工判断适用场景，无主体类型约束。'});
 const conditionNodes=[{nodeKey:'root',parentNodeKey:null,sequenceNo:1,nodeType:'PREDICATE',logicalOperator:null,comparisonOperator:'EQ',operands:[{side:'LEFT',sequenceNo:1,sourceType:'CONTEXT',fieldCode:'country_code',valueType:'STRING',literalValue:null},{side:'RIGHT',sequenceNo:1,sourceType:'LITERAL',fieldCode:null,valueType:'STRING',literalValue:'AE'}]}];
 // 复用已通过原接口的最小条件 Wire，避免把 UI 模型当请求体。
 const template=JSON.parse(fs.readFileSync('/tmp/risk-rule-list-stage5/browser-real-rule.json')).draft.rules[0];
 const rule={...template,ruleCode:'MANUAL_REVIEW',ruleName:'需人工复核的订单',priorityNo:10,reasonCode:reason,reasonMessage:'已保存的手动覆盖消息',action:{actionCode:'DENY_CHECKOUT',actionVersionNo:1},additionalActions:[{sequenceNo:1,actionCode:'ADD_RISK_TAG',actionVersionNo:1,targetBindingCode:'CURRENT_CLIENT_V1',parameters:[{parameterCode:'TAG',valueSequence:1,sourceType:'LITERAL',fieldCode:null,valueType:'STRING',literalValue:tag}]}]};
 // Action 版本字段沿用测试夹具实际形状。
 rule.action={...template.action,actionCode:'DENY_CHECKOUT'};
 const second={...template,ruleCode:'COUNTRY_ALLOW',ruleName:'允许的国家',priorityNo:20,additionalActions:[],reasonMessage:null};
 let draft={pointCode:'CHECKOUT_PREPARE',ruleSetCode:code,ruleSetName:'风控规则集 · 综合验收',baseRuleSetVersionId:null,baseContentHash:null,changeReason:'首次保存：配置拒绝原因与主体标签',rules:[rule,second]};
 let created=await call('POST',rules,{...draft,rules:[second]});const versions=[created];
 await call('POST',rules+'/'+created.ruleSetVersionId+'/freeze',{expectedContentHash:created.contentHash});
 const candidate=await call('POST','/decision-releases/v1/candidates',{ruleSetVersionId:created.ruleSetVersionId,expectedRuleSetContentHash:created.contentHash});owned.releases.push(candidate.releaseId);persist();
 for(let i=2;i<=12;i++){draft={...draft,baseRuleSetVersionId:created.ruleSetVersionId,baseContentHash:created.contentHash,changeReason:'本地综合验收历史版本 '+i};created=await call('POST',rules,draft);versions.push(created);}
 const root=(await call('GET',rules+'?pointCode=CHECKOUT_PREPARE&ruleSetCode='+code+'&page=1&size=10')).items[0];
 fs.writeFileSync(dir+'fixture.json',JSON.stringify({code,reason,tag,draft,root,versions,candidate},null,2));console.log(JSON.stringify({versions:versions.length,root:root.ruleSetId,code,requests:h.calls.length}));})();
