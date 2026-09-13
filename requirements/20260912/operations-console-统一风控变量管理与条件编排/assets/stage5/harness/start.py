import os,subprocess,sys,shlex
from pathlib import Path
root=Path('/Users/kenny/Desktop/Workspace/Code-MoR');work=Path('/tmp/rco19-stage5');env=os.environ.copy()
java_home=subprocess.check_output(['/usr/libexec/java_home','-v','25'],text=True).strip()
for line in (root/'local-environment/.env.local').read_text().splitlines():
 if line.startswith(('MOR_RISK_DB_PASSWORD=','MOR_REDIS_PASSWORD=')):
  k,v=line.split('=',1);env[k]=shlex.split(v,comments=True)[0]
mode=sys.argv[1];args=[java_home+'/bin/java','-Xms96m','-Xmx512m','--class-path',(work/(mode+'-classpath.txt')).read_text(),str(work/('LocalRisk.java' if mode=='risk' else 'LocalBff.java'))]
os.execve(args[0],args,env)
