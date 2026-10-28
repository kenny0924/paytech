"""Compare two Vite dist dirs modulo content hashes: file names and in-file references have
-<8 char hash> replaced by -HASH, so only chunks whose own content changed are reported."""
import sys,os,re,hashlib
H=re.compile(r'-[A-Za-z0-9_-]{8}(?=\.(?:js|css|woff2?|svg|png)\b)')
def load(root):
    out={}
    for d,_,fs in os.walk(root):
        for f in fs:
            p=os.path.join(d,f); rel=os.path.relpath(p,root)
            data=open(p,'rb').read()
            try: data=H.sub('-HASH',data.decode('utf-8')).encode()
            except UnicodeDecodeError: pass
            out.setdefault(H.sub('-HASH',rel),[]).append(hashlib.sha256(data).hexdigest())
    return {k:sorted(v) for k,v in out.items()}
a,b=load(sys.argv[1]),load(sys.argv[2])
oa=sorted(set(a)-set(b)); ob=sorted(set(b)-set(a)); ch=sorted(k for k in set(a)&set(b) if a[k]!=b[k])
print(f"A={sum(map(len,a.values()))} B={sum(map(len,b.values()))} onlyA={len(oa)} onlyB={len(ob)} changed={len(ch)}")
for k in oa: print('  -',k)
for k in ob: print('  +',k)
for k in ch: print('  ~',k)
