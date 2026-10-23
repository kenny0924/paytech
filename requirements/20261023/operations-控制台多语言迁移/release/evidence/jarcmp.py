#!/usr/bin/env python3
"""Compare two Spring Boot fat JARs entry by entry.

BOOT-INF/classes and nested paytech-* JARs: per entry CRC; third-party JARs: whole SHA-256.
Excluded: MANIFEST, META-INF/maven, build-info, classpath/layers index, directories.
Usage: jarcmp.py <a.jar> <b.jar> [--list]
"""
import hashlib
import io
import sys
import zipfile


def skip(name):
    return (name.endswith('/') or name.endswith('MANIFEST.MF') or '/META-INF/maven/' in '/' + name
            or name.endswith('build-info.properties') or name.endswith('classpath.idx')
            or name.endswith('layers.idx'))


def entries(path):
    out = {}
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            n = info.filename
            if skip(n):
                continue
            if n.startswith('BOOT-INF/lib/') and n.endswith('.jar'):
                data = z.read(n)
                base = n[len('BOOT-INF/lib/'):]
                if base.startswith('paytech-'):
                    lib = base.rsplit('-', 2)[0] if base.endswith('-SNAPSHOT.jar') else base[:-4]
                    with zipfile.ZipFile(io.BytesIO(data)) as inner:
                        for ii in inner.infolist():
                            if skip(ii.filename):
                                continue
                            out[f'lib:{lib}!{ii.filename}'] = ii.CRC
                else:
                    out[f'3p:{base}'] = hashlib.sha256(data).hexdigest()
            else:
                out[n] = info.CRC
    return out


def main():
    a, b = entries(sys.argv[1]), entries(sys.argv[2])
    removed = sorted(set(a) - set(b))
    added = sorted(set(b) - set(a))
    changed = sorted(k for k in set(a) & set(b) if a[k] != b[k])
    print(f'entries a={len(a)} b={len(b)} removed={len(removed)} added={len(added)} changed={len(changed)}')
    if '--list' in sys.argv:
        for tag, items in (('-', removed), ('+', added), ('~', changed)):
            for k in items:
                print(tag, k)
    return 0 if not (removed or added or changed) else 1


if __name__ == '__main__':
    sys.exit(main())
