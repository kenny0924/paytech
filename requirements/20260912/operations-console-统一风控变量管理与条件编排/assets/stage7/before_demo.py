from release_support import *
from smoke import Browser
b=Browser();material=json.loads((ROOT/'test-environment/private/operations-material.json').read_text())
b.login(material['administratorEmail'],material['administratorPassword'])
try:
 rule=b.request('GET','/decision-rule-sets/v1/rule-sets/224578384481587200')
 flow=b.request('GET','/decision-flows/v1/flows/224578384930377728')
 original=json.loads((REQ.parent/'operations-console-决策流配置/assets/stage7/user-demo.json').read_text())
 save('before-demo.json',{'rule':rule,'flow':flow})
 assert rule['contentHash']==original['ruleVersion']['contentHash']
 def normalized(g):return {'schemaVersion':g['schemaVersion'],'nodes':sorted(g['nodes'],key=lambda n:n['nodeKey']),'edges':sorted(g['edges'],key=lambda e:e['edgeKey'])}
 assert normalized(flow['graph'])==normalized(original['flowRequest']['graph']) and flow['version']=='1'
 print('Existing demo content matches approved migration baseline')
finally:b.request('POST','/auth/v1/logout',{'refreshToken':b.refresh_token},status=(200,204))
