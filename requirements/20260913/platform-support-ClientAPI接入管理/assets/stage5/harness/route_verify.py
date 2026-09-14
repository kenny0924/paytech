"""Exercise Scope binding through the actual Operations ingress; leave synthetic routes disabled."""
import json
from verify import call,fixture,login,runtime,results
admin=login('admin');target,route=fixture('TARGET'),fixture('ROUTE')
call('create-client-api-target','POST','/gateway-admin/v1/target-services',admin,{'targetServiceCode':target,'channel':'CLIENT_API','discoveryServiceName':'stage5-unused-client-api','reason':'本机验收'})
body={'routeCode':route,'channel':'CLIENT_API','httpMethod':'GET','externalPathTemplate':'/api/client-api/stage5/'+route,'downstreamPathTemplate':'/stage5/orders','targetServiceCode':target,'securityProfileCode':'CLIENT_API_CREDENTIAL','authorizationMode':'API_SCOPE','permissionId':None,'requiredScopeCode':'orders.read','requestCookieMode':'DROP','responseCookieMode':'NONE','bodyLimitBytes':0,'bodyReceiveTimeoutMillis':5000,'responseTimeoutMillis':10000,'headers':[],'queryParameterNames':[], 'guardRules':[{'ruleType':'RATE_LIMIT','dimension':'SHARED_ROUTE','capacity':30,'refillTokens':30,'refillPeriodMillis':60000,'concurrencyLimit':None},{'ruleType':'CONCURRENCY','dimension':'SHARED_ROUTE','capacity':None,'refillTokens':None,'refillPeriodMillis':None,'concurrencyLimit':10}],'reason':'本机 Scope 关联验收'}
call('create-route-scope-binding','POST','/gateway-admin/v1/routes',admin,body)
edit={k:v for k,v in body.items() if k not in ('routeCode','channel')};edit.update(expectedVersion=1,requiredScopeCode='refunds.write')
call('edit-route-scope-binding','PUT','/gateway-admin/v1/routes/'+route,admin,edit)
old=call('old-scope-excludes-route','GET','/gateway-admin/v1/routes?requiredScopeCode=orders.read',admin);assert old['total']==0
new=call('new-scope-includes-route','GET','/gateway-admin/v1/routes?requiredScopeCode=refunds.write',admin);assert new['total']==1 and new['items'][0]['status']=='DISABLED'
ref=call('bff-scope-route-reference','GET','/api-scope-admin/v1/scopes/refunds.write?referenceKind=ROUTE',admin);assert ref['references']['page']['total']==1
edit.update(expectedVersion=2,requiredScopeCode='missing.notregistered')
call('unknown-scope-rejected','PUT','/gateway-admin/v1/routes/'+route,admin,edit,expected=400)
runtime.joinpath('route-integration.json').write_text(json.dumps({'checks':len(results),'results':results,'route':route,'target':target},ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'checks':len(results),'route':route,'status':'PASS'}))
