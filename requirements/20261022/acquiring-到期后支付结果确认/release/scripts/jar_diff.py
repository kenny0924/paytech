#!/usr/bin/env python3
"""逐条目比较两个 Spring Boot 可执行 JAR（含 BOOT-INF/lib 内嵌 JAR 的条目），只输出条目名与 +/-/~ 结论。

用法：python3 -I jar_diff.py <基线 JAR> <候选 JAR>
嵌套 JAR 展开为 "<lib>!/<entry>"；比较的是解压后内容的 CRC32 与长度，忽略时间戳。
"""
import io, sys, zipfile


def entries(path):
    out = {}
    with zipfile.ZipFile(path) as z:
        for i in z.infolist():
            if i.is_dir():
                continue
            if i.filename.startswith("BOOT-INF/lib/") and i.filename.endswith(".jar"):
                with zipfile.ZipFile(io.BytesIO(z.read(i))) as n:
                    for j in n.infolist():
                        if not j.is_dir():
                            out[f"{i.filename}!/{j.filename}"] = (j.CRC, j.file_size)
            else:
                out[i.filename] = (i.CRC, i.file_size)
    return out


def main(a, b):
    x, y = entries(a), entries(b)
    added = sorted(set(y) - set(x))
    removed = sorted(set(x) - set(y))
    changed = sorted(k for k in set(x) & set(y) if x[k] != y[k])
    for tag, items in (("+", added), ("-", removed), ("~", changed)):
        for k in items:
            print(tag, k)
    print(f"# baseline {len(x)} candidate {len(y)}  +{len(added)} -{len(removed)} ~{len(changed)}")


if __name__ == "__main__":
    main(*sys.argv[1:3])
