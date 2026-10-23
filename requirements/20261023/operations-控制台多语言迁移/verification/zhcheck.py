import json,re,subprocess,sys,glob,os,collections
wt,prefixes=sys.argv[1],sys.argv[2].split(',')
os.chdir(wt)
files=subprocess.run(['git','ls-tree','-r','--name-only','9f210b5c','operations-console-web/src'],capture_output=True,text=True).stdout.split()
files=[f for f in files if f.endswith(('.ts','.tsx')) and not f.endswith('.test.ts') and any(f.startswith('operations-console-web/src/'+p) for p in prefixes)]
vals='\n'.join(v for p in glob.glob('operations-console-web/i18n/zh-CN/*.json') for v in json.load(open(p)).values())
run=re.compile(r'[一-鿿　-〿＀-￯—…·“”]+')
tot=0; miss=collections.Counter()
for f in files:
  s=subprocess.run(['git','show','9f210b5c:'+f],capture_output=True,text=True).stdout
  s=re.sub(r'/\*.*?\*/','',s,flags=re.S); s=re.sub(r'(^|[^:])//.*',r'\1',s)
  for r in run.findall(s):
    if not re.search(r'[一-鿿]',r): continue
    tot+=1
    if r not in vals: miss[f.split('/')[-1]+': '+r]+=1
print(f'files={len(files)} chinese_runs={tot} missing={sum(miss.values())}')
for k,v in miss.most_common(40): print(' ',k)
