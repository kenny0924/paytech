"""联合查询演示模板校验（阶段 6，只读）。

1. 联合配置规则（与 QueryTemplateDraftValidator / JointQueryParticipation 相同）：键类型取注册块 144 的 26 个；键参数唯一必填 LIST、
   类型可绑定；LINK 列不屏蔽；默认维度键标成 LINK 给出提醒；联合列名必须出现在实际输出列中。
2. 引擎校验：TemplateCheck.java（候选 Platform JAR 的 QueryTemplateEngine.validate + ReadOnlySqlGuard），渲染后内联字面量。
3. 执行：local —— 本机 mor-local-mysql-1 的 *_v2e2e 库（与 mor-test 同结构，阶段 6 已逐列比对），占位值，只看能否执行与输出列；
   mortest —— mor-test 只读会话（SET SESSION TRANSACTION READ ONLY），按 jq_demo_spec.ACCEPTANCE_ENTRIES 逐层模拟展开
   （同 J3：LINK 列取值、已访问键去重、单键扇出 50、单模板 100 行、最多 6 层），输出预期链路，作为阶段 7 验收预期。
   环境变量 JQ_SKIP_LOADED=1：同时按 technique 8.7 模拟“已加载对象跳过”（depth > 0 的“按自身 ID 查”模板剔除已加载值），
   用于发布 b 的 AC-13 预期。
用法：python3 -I validate_templates.py <local|mortest> <Platform exec JAR 解压目录> <输出 JSON>
凭据只在辅助脚本里读取，不打印。
"""
import json
import re
import subprocess
import sys
from collections import OrderedDict
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import jq_demo_spec as spec  # noqa: E402

MODE, JAR_DIR, OUT = sys.argv[1], Path(sys.argv[2]), Path(sys.argv[3])
SCRATCH = Path("/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/569bb32f-857a-4b46-8dbf-7c60792820c5/scratchpad")
LOCAL_Q = SCRATCH / "s5/q.sh"          # 本机 root，会话只读
TEST_Q = SCRATCH / "s6/tqb.sh"         # mor-test root，会话只读
BLOCK = HERE.parent / "sql/mor_platform-block-144.sql"
JAVA = "/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java"
DB = {s["code"]: s["database"] for s in spec.DATA_SOURCES}
FANOUT, MAX_ROWS, MAX_DEPTH = 50, 100, 6
SKIP_LOADED = __import__("os").environ.get("JQ_SKIP_LOADED") == "1"

KEY_TYPES = {}
for m in re.finditer(r"\((9000000000001441\d\d), '([A-Z_]+)', '([^']*)', '([^']*)', '(INTEGER|STRING)', '(LINK|DIMENSION)', ([01]), "
                     r"(NULL|'[^']*'), (NULL|'[^']*'), (\d+),", BLOCK.read_text()):
    KEY_TYPES[m[2]] = {"valueType": m[5], "defaultRole": m[6], "entry": m[7] == "1",
                       "pattern": None if m[8] == "NULL" else m[8][1:-1]}
assert len(KEY_TYPES) == 26, len(KEY_TYPES)
for extra in getattr(spec, "KEY_TYPES", []):  # 修订 c：子行键类型（mor-test 经 K2 补建）
    KEY_TYPES.setdefault(extra["keyTypeCode"], {"valueType": extra["valueType"], "defaultRole": extra["defaultRole"],
                                                "entry": extra["entryAllowed"], "pattern": None})
results = {"mode": MODE, "keyTypes": len(KEY_TYPES), "templates": OrderedDict(), "problems": []}


def problem(code, level, message):
    results["problems"].append({"templateCode": code, "level": level, "message": message})


def joint_rules(t):
    code, joint, params = t["templateCode"], t["joint"], t["parameters"]
    for field, kt in [("subjectKeyType", joint["subjectKeyType"]), ("keyParameter.keyType", joint["keyParameter"]["keyType"])] + [
            (f"columns.{c['name']}", c["keyType"]) for c in joint["columns"]]:
        if kt not in KEY_TYPES:
            problem(code, "ERROR", f"{field} 键类型不存在: {kt}")
    key = [p for p in params if p["name"] == joint["keyParameter"]["name"]]
    if not key or not key[0].get("required"):
        problem(code, "ERROR", "键参数未声明或不是必填（不参与）")
    others = [p for p in params if p["name"] != joint["keyParameter"]["name"] and p.get("required") and p.get("defaultValue") is None]
    if others:
        problem(code, "ERROR", "必填参数不唯一（不参与）: " + others[0]["name"])
    if key:
        element = key[0]["elementType"] if key[0]["type"] == "LIST" else key[0]["type"]
        kt = KEY_TYPES.get(joint["keyParameter"]["keyType"])
        if key[0]["type"] != "LIST":
            problem(code, "WARNING", "键参数不是 LIST，逐值执行")
        if kt and not (element == "STRING" or element == "INTEGER" and kt["valueType"] == "INTEGER"):
            problem(code, "ERROR", f"参数类型 {element} 不能绑定 {kt['valueType']} 键类型")
    names = [c["name"].lower() for c in joint["columns"]]
    if len(names) != len(set(names)):
        problem(code, "ERROR", "联合查询列重复")
    for c in joint["columns"]:
        if c["keyRole"] == "LINK" and KEY_TYPES.get(c["keyType"], {}).get("defaultRole") == "DIMENSION":
            problem(code, "WARNING", f"列 {c['name']} 按维度键 {c['keyType']} 继续展开")
    for col in t["columns"]:
        if col.get("mask", "NONE") != "NONE" and col["name"].lower() in names:
            problem(code, "ERROR", f"链路列不能屏蔽: {col['name']}")
    if "{{#if " + joint["keyParameter"]["name"] + "}}" not in t["body"]:
        problem(code, "ERROR", "IN 列表没有包在 {{#if}} 中")


def render(items):
    """items: [(template, sample dict)] → {code: {problems, errors, sql}}（同一模板多次时按顺序返回列表）。"""
    payload = [{"templateCode": t["templateCode"], "database": DB[t["dataSourceCode"]], "body": t["body"],
                "parameters": t["parameters"], "sample": sample} for t, sample in items]
    path = SCRATCH / "s6/template-check-input.json"
    path.write_text(json.dumps(payload, ensure_ascii=False))
    cp = f"{JAR_DIR}/BOOT-INF/classes:{JAR_DIR}/BOOT-INF/lib/*"
    out = subprocess.run([JAVA, "-cp", cp, str(HERE / "TemplateCheck.java"), str(path)], capture_output=True, text=True)
    if out.returncode != 0:
        raise SystemExit("TemplateCheck failed: " + out.stderr[-2000:])
    return [json.loads(line) for line in out.stdout.splitlines() if line.startswith("{")]


def run_sql(target, sql):
    """返回 (输出列, 行, 错误)。-B --column-names --column-type-info 在 0 行时也输出列名行。"""
    out = subprocess.run(target + ["--column-type-info", "-e", sql], capture_output=True, text=True, stdin=subprocess.DEVNULL)
    if out.returncode != 0:
        return None, [], out.stderr.strip().splitlines()[-1][:300]
    lines = out.stdout.splitlines()
    if not lines:
        return [], [], None
    header = lines[0].split("\t")
    return header, [dict(zip(header, l.split("\t"))) for l in lines[1:]], None


def execute(database, sql):
    """local：先 *_v2e2e；若结构落后（Unknown column / table）再用本机 auto 库 mor_<x> 复核并记下。mortest：mor-test 只读会话。"""
    if MODE != "local":
        return run_sql([str(TEST_Q), database, "--column-names"], sql) + (database,)
    header, rows, error = run_sql([str(LOCAL_Q), database + "_v2e2e", "-B", "--column-names"], sql)
    if error and re.search(r"Unknown (column|table)|doesn't exist", error):
        header2, rows2, error2 = run_sql([str(LOCAL_Q), database, "-B", "--column-names"], sql)
        return header2, rows2, error2, f"{database}（v2e2e 结构落后：{error}）"
    return header, rows, error, database + "_v2e2e"


for t in spec.TEMPLATES:
    joint_rules(t)


def placeholder(t):
    key = t["parameters"][0]
    return {key["name"]: [0] if key["elementType"] == "INTEGER" else ["__none__"]}


rendered = render([(t, placeholder(t)) for t in spec.TEMPLATES])
for t, r in zip(spec.TEMPLATES, rendered):
    entry = {"name": t["templateName"], "dataSource": t["dataSourceCode"], "engineProblems": r["problems"], "engineErrors": r["errors"]}
    if r["errors"]:
        problem(t["templateCode"], "ERROR", "引擎校验失败: " + "; ".join(r["problems"]))
    if MODE == "local" and not r["errors"]:
        header, rows, error, where = execute(DB[t["dataSourceCode"]], r["sql"])
        entry.update({"executed": error is None, "error": error, "columns": header, "executedOn": where})
        if error:
            problem(t["templateCode"], "ERROR", "执行失败: " + error)
        else:
            missing = [c["name"] for c in t["joint"]["columns"] if c["name"] not in header]
            unlabeled = [h for h in header if h not in {c["name"] for c in t["columns"]}]
            if missing:
                problem(t["templateCode"], "ERROR", "联合列不在输出列中: " + ", ".join(missing))
            if unlabeled:
                problem(t["templateCode"], "WARNING", "输出列没有中文标签: " + ", ".join(unlabeled))
    results["templates"][t["templateCode"]] = entry

if MODE == "mortest":
    by_key = {}
    excluded = set(filter(None, __import__("os").environ.get("JQ_EXCLUDE", "").split(",")))  # 模拟停用的模板
    for t in [t for t in spec.TEMPLATES if t["templateCode"] not in excluded]:
        by_key.setdefault(t["joint"]["keyParameter"]["keyType"], []).append(t)
    chains = []
    for entry in spec.ACCEPTANCE_ENTRIES:
        kt = KEY_TYPES[entry["keyType"]]
        pattern_ok = all(kt["pattern"] is None or re.fullmatch(kt["pattern"], v) for v in entry["values"])
        visited = {(entry["keyType"], v) for v in entry["values"]}
        frontier = {entry["keyType"]: list(entry["values"])}
        layers, loaded = [], {}
        for depth in range(MAX_DEPTH):
            items, already = [], []
            for key_type, values in frontier.items():
                for t in by_key.get(key_type, []):
                    use = values[:FANOUT]
                    if SKIP_LOADED and depth > 0 and t["joint"]["subjectKeyType"] == key_type:
                        skipped = [v for v in use if v in loaded.get(key_type, set())]
                        use = [v for v in use if v not in loaded.get(key_type, set())]
                        if skipped:
                            already.append({"templateCode": t["templateCode"], "keyType": key_type, "values": skipped})
                    if use:
                        items.append((t, {t["parameters"][0]["name"]: [int(v) if t["parameters"][0]["elementType"] == "INTEGER"
                                                                       else v for v in use]}))
            if not items:
                if already:
                    layers.append({"depth": depth, "nodes": [], "newKeys": {}, "alreadyLoaded": already})
                break
            nodes, discovered = [], {}
            for (t, sample), r in zip(items, render(items)):
                header, rows, error, _ = execute(DB[t["dataSourceCode"]], r["sql"])
                rows = rows[:MAX_ROWS] if rows else []
                nodes.append({"templateCode": t["templateCode"], "templateName": t["templateName"],
                              "trigger": f"{t['joint']['keyParameter']['keyType']}={','.join(map(str, list(sample.values())[0]))}",
                              "rows": len(rows), "error": error,
                              "subjects": sorted({row.get(next((c["name"] for c in t["joint"]["columns"]
                                                                  if c["keyType"] == t["joint"]["subjectKeyType"]), ""), "")
                                                  for row in rows} - {"", "NULL"})})
                for c in t["joint"]["columns"]:
                    if c["keyType"] == t["joint"]["subjectKeyType"]:
                        loaded.setdefault(c["keyType"], set()).update(
                            row[c["name"]] for row in rows if row.get(c["name"]) not in (None, "", "NULL"))
                    if c["keyRole"] != "LINK":
                        continue
                    for row in rows:
                        value = row.get(c["name"])
                        if value in (None, "", "NULL") or (c["keyType"], value) in visited:
                            continue
                        visited.add((c["keyType"], value))
                        discovered.setdefault(c["keyType"], []).append(value)
            layers.append({"depth": depth, "nodes": nodes, "newKeys": discovered, "alreadyLoaded": already})
            frontier = discovered
        chains.append({"label": entry["label"], "entry": entry, "patternOk": pattern_ok, "layers": layers})
    results["chains"] = chains

results["verdict"] = not any(p["level"] == "ERROR" for p in results["problems"])
OUT.write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
print("templates", len(spec.TEMPLATES), "errors", sum(p["level"] == "ERROR" for p in results["problems"]),
      "warnings", sum(p["level"] == "WARNING" for p in results["problems"]), "verdict", results["verdict"])
for p in results["problems"]:
    print(" ", p["level"], p["templateCode"], p["message"])
