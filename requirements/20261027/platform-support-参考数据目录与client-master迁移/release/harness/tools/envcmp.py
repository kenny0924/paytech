import sys,json,subprocess
rendered=json.load(open(sys.argv[1])); svc=sys.argv[2]; label=sys.argv[3]
s=rendered['services'][svc]; env=s.get('environment') or {}
live=json.loads(subprocess.check_output(['docker','inspect',f'mor-test-{svc}-1','--format','{{json .Config.Env}}']))
live=dict(e.split('=',1) for e in live)
img_env={}
try:
    ie=json.loads(subprocess.check_output(['docker','image','inspect',s['image'],'--format','{{json .Config.Env}}']))
    img_env=dict(e.split('=',1) for e in ie)
except Exception: pass
eff={**img_env,**{k:('' if v is None else str(v)) for k,v in env.items()}}
missing=sorted(set(live)-set(eff)); extra=sorted(set(eff)-set(live)); diff=sorted(k for k in set(live)&set(eff) if live[k]!=eff[k])
print(f"{label:9} {svc:26} image={s['image'][7:19]} keys live={len(live)} rendered={len(eff)} missing={missing} extra={extra} valuediff={diff}")
