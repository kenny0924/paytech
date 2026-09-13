from release_support import *
from smoke import Browser
b=Browser();material=json.loads((ROOT/'test-environment/private/operations-material.json').read_text())
b.login(material['administratorEmail'],material['administratorPassword'])
try:
 nav=b.request('GET','/authorization/v1/navigation')
 assert any(r['resourceCode']=='OPERATIONS_RISK_VARIABLES' for r in nav['resources'])
 variables=b.request('GET','/decision-variables/v1/variables?page=1&size=100');assert variables['total']==17
 constants={code:b.request('GET','/decision-variables/v1/variables/'+code) for code in ['DEMO_AMOUNT_THRESHOLD','DEMO_STANDARD_CURRENCY']}
 context=b.request('GET','/decision-variables/v1/authoring-context')
 members=b.request('GET','/decision-variables/v1/variables/RESTRICTED_COUNTRIES/members?page=1&size=100');assert members['total']==3
 rule=b.request('GET','/decision-rule-sets/v1/rule-sets/224578384481587200')
 flow=b.request('GET','/decision-flows/v1/flows/224578384930377728')
 save('public-first-read.json',{'variables':variables,'constants':constants,'members':members,'rule':rule,'flow':flow,'navigationCodes':[r['resourceCode'] for r in nav['resources']],'context':context})
 print({'variables':variables['total'],'menuPresent':True,'constants':{k:{f:v[f] for f in ['currentValue','version','numericUnit','currencyVariableCode']} for k,v in constants.items()},'ruleKeys':list(rule),'flowKeys':list(flow)})
finally:b.request('POST','/auth/v1/logout',{'refreshToken':b.refresh_token},status=(200,204))
