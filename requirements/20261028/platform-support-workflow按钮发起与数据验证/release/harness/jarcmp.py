import sys,zipfile,io,re
IGN=re.compile(r'(META-INF/MANIFEST\.MF|BOOT-INF/classpath\.idx|BOOT-INF/layers\.idx|META-INF/maven/.*/pom\.properties)$')
def entries(path):
    out={}
    z=zipfile.ZipFile(path)
    for i in z.infolist():
        if i.is_dir() or IGN.search(i.filename): continue
        n=i.filename
        if n.startswith('BOOT-INF/lib/') and re.search(r'/paytech-[^/]*\.jar$',n):
            lib=n.rsplit('/',1)[1]; lib=re.sub(r'-1\.0\.0-SNAPSHOT','',lib)
            iz=zipfile.ZipFile(io.BytesIO(z.read(n)))
            for j in iz.infolist():
                if j.is_dir() or IGN.search(j.filename): continue
                out[f'{lib}!{j.filename}']=j.CRC
        else:
            out[n]=i.CRC
    return out
a,b=entries(sys.argv[1]),entries(sys.argv[2])
oa=sorted(set(a)-set(b)); ob=sorted(set(b)-set(a)); d=sorted(k for k in set(a)&set(b) if a[k]!=b[k])
print(f"A={len(a)} B={len(b)} onlyA={len(oa)} onlyB={len(ob)} changed={len(d)}")
lim=int(sys.argv[3]) if len(sys.argv)>3 else 40
for k in oa[:lim]: print("  -",k)
for k in ob[:lim]: print("  +",k)
for k in d[:lim]: print("  ~",k)
