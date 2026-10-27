"""由 block138-spec.json 生成 Platform 注册块 138 的全部 SQL（同目录）。

输出：
  block138-precheck.sql       执行前只读核对（区间为空、新 code / 路径未占用、既有依赖唯一）
  registration-block138.sql   休眠注册（permission ACTIVE、PLATFORM_ADMIN 授权 REVOKED、资源 / 路由 DISABLED）
  block138-activate.sql       启用 + 停用 client-onboarding-c03（form-options）
  block138-deactivate.sql     启用的逆操作 + 恢复 c03
  block138-rollback.sql       按自身 ID 段删除（子表先删、按钮先于菜单）+ 恢复 c03
用法：python3 -I gen_block138.py（在任意目录执行，读写本脚本所在目录）。

形状规则（technique S-07、块 137、mor-test 2026-10-27 只读核对）：
  CLIENT：target 按 target_service_code 子查询；CLIENT_JWT + HUMAN_AUTHENTICATED；Body 默认 GET 16384、写 65536，spec 可用 bodyLimit 覆盖；
          Header GET authorization CORS；写 + content-type CORS/FORWARD、idempotency-key CORS、origin FORWARD（同 live c04）；Guard 抄 live c05 / c14。
  OPERATIONS：target 900000000000000001；OPERATIONS_JWT + HUMAN_PERMISSION；GET Body 0、写 65536；Header GET authorization CORS +
          origin FORWARD；写 + content-type、idempotency-key CORS/FORWARD（块 137，主会话决定 6）；Guard 块 137 约定。
既有对象（权限、菜单、路由、Client target）一律先 SET 变量再引用；任一变量为 NULL 时守卫语句触发
ERROR 1242（子查询多行）中止，mysql 客户端退出且事务未提交。
"""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
import sys
SPEC_NAME = sys.argv[1] if len(sys.argv) > 1 else "block138-spec.json"
SPEC = json.loads((HERE / SPEC_NAME).read_text())
START = {"permission": 101, "grant": 201, "resource": 301, "route": 401, "header": 501, "query": 601, "guard": 701, **SPEC.get("idStart", {})}
PREFIX = SPEC.get("filePrefix", "block138")
B = "900000000000138"
BASE = 900000000000138000
ROLE = "900000000000000510"
OPS_TARGET = "900000000000000001"
REQ = "requirements/20261027/client-master-全球合规入驻与资格切片1"

GUARDS = {
    ("CLIENT", "read"): [("SHARED_ROUTE", 600, 50), ("CLIENT_IP", 120, 10), ("AUTHENTICATED_PRINCIPAL", 60, 5)],
    ("CLIENT", "write"): [("SHARED_ROUTE", 600, 50), ("CLIENT_IP", 120, 10), ("AUTHENTICATED_PRINCIPAL", 60, 5)],
    ("OPERATIONS", "read"): [("SHARED_ROUTE", 300, 50), ("CLIENT_IP", 30, 3), ("AUTHENTICATED_PRINCIPAL", 30, 2)],
    ("OPERATIONS", "write"): [("SHARED_ROUTE", 300, 50), ("CLIENT_IP", 30, 3), ("AUTHENTICATED_PRINCIPAL", 10, 2)],
}
HEADERS = {
    ("CLIENT", "read"): [("authorization", "CORS_REQUEST_ALLOWED")],
    ("CLIENT", "write"): [("authorization", "CORS_REQUEST_ALLOWED"), ("content-type", "CORS_REQUEST_ALLOWED"),
                          ("content-type", "FORWARD_REQUEST"), ("idempotency-key", "CORS_REQUEST_ALLOWED"),
                          ("origin", "FORWARD_REQUEST")],  # 同 live c04/c06—c13：idempotency-key 只 CORS（2026-10-16 起不转发）
    ("OPERATIONS", "read"): [("authorization", "CORS_REQUEST_ALLOWED"), ("origin", "FORWARD_REQUEST")],
    ("OPERATIONS", "write"): [("authorization", "CORS_REQUEST_ALLOWED"), ("origin", "FORWARD_REQUEST"),
                              ("content-type", "CORS_REQUEST_ALLOWED"), ("content-type", "FORWARD_REQUEST"),
                              ("idempotency-key", "CORS_REQUEST_ALLOWED"), ("idempotency-key", "FORWARD_REQUEST")],
}


def q(text):
    return "NULL" if text is None else "'" + str(text).replace("'", "''") + "'"


def var(code):
    return "@b138_" + code.lower().replace("-", "_")


def reg_name_strict():
    return "registration-block138.sql" if PREFIX == "block138" else f"registration-{PREFIX}.sql"


def build():
    perms = SPEC["permissions"]
    perm_ids = {p["code"]: str(BASE + START["permission"] + i) for i, p in enumerate(perms)}
    existing_perms = SPEC["existingPermissions"]
    resources = SPEC["resources"]
    res_ids = {r["code"]: str(BASE + START["resource"] + i) for i, r in enumerate(resources)}
    routes = SPEC["routes"]
    route_ids = {r["routeCode"]: str(BASE + START["route"] + i) for i, r in enumerate(routes)}
    extra_queries = SPEC.get("existingRouteQueryParams", [])
    assert len(perms) <= 99 and len(resources) <= 99 and len(routes) <= 99

    def perm_ref(code):
        if code is None:
            return "NULL"
        if code in perm_ids:
            return perm_ids[code]
        if code in existing_perms:
            return var("perm_" + code)
        raise SystemExit(f"unknown permission {code}")

    def parent_ref(parent):
        if parent is None:
            return "NULL"
        if parent.startswith("id:"):
            return parent[3:]
        if parent.startswith("existing:"):
            return var("res_" + parent[9:])
        return res_ids[parent]

    existing_res = sorted({r["parent"][9:] for r in resources if (r.get("parent") or "").startswith("existing:")})
    existing_routes = sorted({e["routeCode"] for e in extra_queries})
    needs_client_target = any(r["channel"] == "CLIENT" for r in routes)

    # ---------- rows ----------
    headers, queries, guards = [], [], []
    for r in routes:
        rid = route_ids[r["routeCode"]]
        kind = "read" if r["method"] == "GET" else "write"
        for name, use in HEADERS[(r["channel"], kind)]:
            headers.append((rid, name, use))
        for name in r.get("query", []):
            queries.append((rid, name))
        for dim, rate, conc in GUARDS[(r["channel"], kind)]:
            guards.append((rid, "RATE_LIMIT", dim, rate, rate, 60000, None))
            guards.append((rid, "CONCURRENCY", dim, None, None, None, conc))
    extra_query_rows = [(var("route_" + e["routeCode"]), e["parameter"]) for e in extra_queries]
    assert len(headers) <= 99 and len(queries) + len(extra_query_rows) <= 99 and len(guards) <= 299, \
        (len(headers), len(queries), len(guards))
    hdr_ids = [str(BASE + START["header"] + i) for i in range(len(headers))]
    qp_ids = [str(BASE + START["query"] + i) for i in range(len(queries) + len(extra_query_rows))]
    grd_ids = [str(BASE + START["guard"] + i) for i in range(len(guards))]  # 7xx—9xx：路由多于 16 条时 guard 超出 99 行
    menus = [r for r in resources if r["type"] == "MENU"]
    buttons = [r for r in resources if r["type"] == "BUTTON"]
    counts = {"permission": len(perms), "role_permission": len(perms), "console_resource": len(resources),
              "gateway_route": len(routes), "route_header": len(headers),
              "query_param": len(queries) + len(extra_query_rows), "guard_rule": len(guards)}
    total = sum(counts.values())


    def rng(kind, n):
        return str(BASE + START[kind]), str(BASE + START[kind] + n - 1)

    all_queries = queries + extra_query_rows
    lookups = []
    for code in existing_perms:
        lookups.append((var("perm_" + code), f"SELECT permission_id FROM t_permission WHERE permission_code = {q(code)}"))
    for code in existing_res:
        lookups.append((var("res_" + code), f"SELECT resource_id FROM t_console_resource WHERE resource_code = {q(code)} "
                                            "AND channel = 'OPERATIONS'"))
    for code in existing_routes:
        lookups.append((var("route_" + code), f"SELECT route_id FROM t_gateway_route WHERE route_code = {q(code)}"))
    if needs_client_target:
        lookups.append(("@b138_client_target", "SELECT target_id FROM t_gateway_target WHERE "
                                               "target_service_code = 'client-console-bff' AND channel = 'CLIENT'"))

    def lookup_sql():
        out = ["-- 既有对象（各环境 ID 不同）：先取到变量；任一为 NULL 时下一句产生两行子查询 → ERROR 1242 中止，事务不提交。"]
        for name, select in lookups:
            out.append(f"SET {name} = ({select});")
        cond = " OR ".join(f"{name} IS NULL" for name, _ in lookups) or "FALSE"
        out.append(f"SET @b138_lookup_guard = (SELECT 1 UNION ALL SELECT 1 FROM DUAL WHERE {cond});")
        return out

    perm_summary = ", ".join(f"{p['code']} ({p['resourceType']}, {p['action']}, {p['risk']})" for p in perms)
    header_lines = [
        "-- Platform 注册块 138" + SPEC.get("title", "") + "，",
        f"-- 只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block138.py 依 {SPEC_NAME} 生成，勿手改。",
        f"-- 来源：{REQ}/technique.md 第 2.1—2.3 节；db/platform-support/registration-block138.draft.md；主会话 2026-10-27 决定 1—6。",
        "-- 前置：本需求 client-master、compliance、client-console-bff、operations-console-bff 新镜像已发布（休眠注册本身不依赖镜像）。",
        "-- 执行后为休眠状态：permission ACTIVE、PLATFORM_ADMIN 授权 REVOKED、菜单 / 按钮 / 路由 DISABLED；启用见 block138-activate.sql。",
        "-- 预期插入：" + "、".join(f"{k} {v}" for k, v in counts.items()) + f"，共 {total} 行。",
        "-- 执行前：block138-precheck.sql 全部符合期望；发布前须用候选 Platform JAR 跑 assets/harness/block_compile_check.sh（五态 COMPILE OK）。",
        f"-- 新权限：{perm_summary}。",
        "-- 既有对象经变量引用：" + ", ".join(n for n, _ in lookups) + "。",
        "-- ID（本文件）：permission " + "—".join(rng("permission", len(perms))) + "、role_permission " + "—".join(rng("grant", len(perms)))
        + "、console_resource " + "—".join(rng("resource", len(resources))) + "、gateway_route " + "—".join(rng("route", len(routes)))
        + "、route_header " + "—".join(rng("header", len(headers))) + "、query_param " + "—".join(rng("query", len(all_queries)))
        + "、guard_rule " + "—".join(rng("guard", len(guards))) + "。回滚只删以上区间（" + PREFIX + "-rollback.sql）。",
    ]

    english = SPEC.get("englishHeader", [])
    def english_section(conditional):
        out = ["-- ---------------------------------------------------------------------------"]
        out += ["-- " + line for line in english]
        out.append("-- Rows: " + ", ".join(f"{k} {v}" for k, v in counts.items()) + f" ({total}). IDs: see the Chinese header above.")
        if conditional:
            out += ["-- initial-data variant: the existing objects below are created through the Platform management API after this file",
                    "-- runs, so on a fresh environment they are absent and every INSERT here is skipped (@b138_apply is NULL).",
                    "-- On an environment that already has them, all rows are inserted together. To register later on a fresh",
                    f"-- environment, run {REQ}/db/platform-support/{reg_name_strict()} (fails closed) after the onboarding registration."]
        else:
            out += ["-- Fails closed: if an existing object is missing, the guard statement raises ERROR 1242 and nothing is committed."]
        out.append("-- ---------------------------------------------------------------------------")
        return out

    reg = header_lines + english_section(False) + ["START TRANSACTION;", "", "SET @b138_time = UTC_TIMESTAMP(6);", "SET @b138_actor_id = 1;",
                          f"SET @b138_role_id = {ROLE};"] + lookup_sql() + [""]
    if perms:
        reg.append("INSERT INTO t_permission (permission_id,permission_code,resource_type,action_code,risk_level,status,version,create_time,update_time) VALUES")
        reg.append(",\n".join(f"  ({perm_ids[p['code']]}, {q(p['code'])}, {q(p['resourceType'])}, {q(p['action'])}, {q(p['risk'])}, 'ACTIVE', 1, @b138_time, @b138_time)"
                              for p in perms) + ";")
        reg.append("")
        reg.append("INSERT INTO t_role_permission (role_permission_id,role_id,permission_id,status,version,create_time,update_time) VALUES")
        reg.append(",\n".join(f"  ({BASE + START['grant'] + i}, @b138_role_id, {perm_ids[p['code']]}, 'REVOKED', 1, @b138_time, @b138_time)"
                              for i, p in enumerate(perms)) + ";")
        reg.append("")
    if resources:
        reg.append("INSERT INTO t_console_resource (resource_id,resource_code,channel,resource_type,parent_resource_id,display_name,frontend_route,component_key,permission_id,sort_order,status,version,create_time,update_time) VALUES")
        reg.append(",\n".join(
            f"  ({res_ids[r['code']]}, {q(r['code'])}, 'OPERATIONS', {q(r['type'])}, {parent_ref(r.get('parent'))}, {q(r['displayName'])}, "
            f"{q(r.get('frontendRoute'))}, {q(r['code'] if r['type'] == 'MENU' else None)}, {perm_ref(r['permission'])}, {r['sort']}, 'DISABLED', 1, @b138_time, @b138_time)"
            for r in menus + buttons) + ";")
        reg.append("")
    reg.append("INSERT INTO t_gateway_route (route_id, route_code, channel, http_method, external_path_template, downstream_path_template, target_id, security_profile_code, authorization_mode, permission_id, required_scope_code, request_cookie_mode, response_cookie_mode, idempotency_mode, body_limit_bytes, body_receive_timeout_millis, response_timeout_millis, status, version, created_by, updated_by, create_time, update_time) VALUES")
    rows = []
    for r in routes:
        client = r["channel"] == "CLIENT"
        body = (16384 if r["method"] == "GET" else 65536) if client else (0 if r["method"] == "GET" else 65536)
        body = r.get("bodyLimit", body)
        rows.append(
            f"  ({route_ids[r['routeCode']]}, {q(r['routeCode'])}, {q(r['channel'])}, {q(r['method'])}, {q(r['externalPath'])}, "
            f"{q(r['downstreamPath'])}, {'@b138_client_target' if client else OPS_TARGET}, "
            f"{q('CLIENT_JWT' if client else 'OPERATIONS_JWT')}, {q('HUMAN_AUTHENTICATED' if client else 'HUMAN_PERMISSION')}, "
            f"{'NULL' if client else perm_ref(r['permission'])}, NULL, 'DROP', 'NONE', 'NONE', {body}, 5000, 10000, 'DISABLED', 1, "
            f"@b138_actor_id, @b138_actor_id, @b138_time, @b138_time)")
    reg.append(",\n".join(rows) + ";")
    reg.append("")
    reg.append("INSERT INTO t_gateway_route_header (route_header_id,route_id,header_name,header_use) VALUES")
    reg.append(",\n".join(f"  ({hid}, {rid}, {q(n)}, {q(u)})" for hid, (rid, n, u) in zip(hdr_ids, headers)) + ";")
    reg.append("")
    if all_queries:
        reg.append("INSERT INTO t_gateway_route_query_param (route_query_param_id,route_id,parameter_name) VALUES")
        reg.append(",\n".join(f"  ({qid}, {rid}, {q(n)})" for qid, (rid, n) in zip(qp_ids, all_queries)) + ";")
        reg.append("")
    reg.append("INSERT INTO t_gateway_guard_rule (guard_rule_id,route_id,rule_type,dimension,capacity,refill_tokens,refill_period_millis,concurrency_limit) VALUES")
    reg.append(",\n".join(
        f"  ({gid}, {rid}, {q(t)}, {q(d)}, {'NULL' if c is None else c}, {'NULL' if rt is None else rt}, {'NULL' if p is None else p}, {'NULL' if cl is None else cl})"
        for gid, (rid, t, d, c, rt, p, cl) in zip(grd_ids, guards)) + ";")
    reg += ["", "COMMIT;", ""]

    # initial-data 变体：同一份行，守卫改为条件插入（依赖缺失时整块跳过）
    cond = " AND ".join(f"{name} IS NOT NULL" for name, _ in lookups) or "TRUE"
    body = reg[reg.index("START TRANSACTION;"):]
    out_init = header_lines[:] + ["-- 本文件为 initial-data 变体：与 " + reg_name_strict() + " 行内容相同，只把失败即中止的守卫换成条件插入；"
                                  "逐字追加到 paytech/platform-support/docs/database/sql/platform-support-initial-data.sql（自分隔线起）。"]
    out_init += english_section(True)
    in_values = False
    for line in body:
        if line.startswith("SET @b138_lookup_guard"):
            out_init.append(f"SET @b138_apply = IF({cond}, 1, NULL);")
            continue
        if line.startswith("INSERT INTO") and line.endswith(" VALUES"):
            out_init.append(line[:-len(" VALUES")] + " SELECT * FROM (VALUES")
            in_values = True
            continue
        if in_values:
            rows = line.split("\n")
            rows = [("  ROW(" + r[3:]) if r.startswith("  (") else r for r in rows]
            last = rows[-1]
            if last.endswith(");"):
                rows[-1] = last[:-1] + ") AS b138_rows WHERE @b138_apply = 1;"
                in_values = False
            out_init.append("\n".join(rows))
            continue
        out_init.append(line)

    r_lo, r_hi = rng("route", len(routes))
    res_lo, res_hi = rng("resource", len(resources))
    g_lo, g_hi = rng("grant", len(perms))
    c03 = SPEC.get("disableOnActivate", [])

    def toggle(label, to_status, from_status, grant_to, grant_from, c03_to, c03_from, note):
        out = [f"-- Platform 块 138 {label}，只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block138.py 生成（{SPEC_NAME}）。", note,
               f"-- 预期影响：路由 {len(routes)}、资源 {len(resources)}、授权 {len(perms)}、c03 {len(c03)}、角色 {1 if perms else 0}。",
               "START TRANSACTION;", "SET @b138_toggle_time = UTC_TIMESTAMP(6);",
               f"UPDATE t_gateway_route SET status='{to_status}', version=version+1, update_time=@b138_toggle_time",
               f" WHERE route_id BETWEEN {r_lo} AND {r_hi} AND status='{from_status}';", "SELECT ROW_COUNT() AS routes;"]
        if resources:
            out += [f"UPDATE t_console_resource SET status='{to_status}', version=version+1, update_time=@b138_toggle_time",
                    f" WHERE resource_id BETWEEN {res_lo} AND {res_hi} AND status='{from_status}';", "SELECT ROW_COUNT() AS resources;"]
        if perms:
            out += [f"UPDATE t_role_permission SET status='{grant_to}', version=version+1, update_time=@b138_toggle_time",
                    f" WHERE role_permission_id BETWEEN {g_lo} AND {g_hi} AND status='{grant_from}';", "SELECT ROW_COUNT() AS grants;"]
        if c03:
            out += [f"UPDATE t_gateway_route SET status='{c03_to}', version=version+1, update_time=@b138_toggle_time",
                    f" WHERE route_code IN ({', '.join(q(c) for c in c03)}) AND status='{c03_from}';", "SELECT ROW_COUNT() AS form_options_route;"]
        if perms:
            out += [f"UPDATE t_role SET version=version+1, update_time=@b138_toggle_time WHERE role_id={ROLE};", "SELECT ROW_COUNT() AS role;"]
        return out + ["COMMIT;", ""]

    activate = toggle("启用", "ACTIVE", "DISABLED", "ACTIVE", "REVOKED", "DISABLED", "ACTIVE",
                      "-- 前置：registration-block138.sql 已提交；BFF / Web 新镜像 healthy；Web 已不再调用 form-options。"
                      "同时停用被 form-schema 取代的 client-onboarding-c03（主会话决定 5）。")
    deactivate = toggle("停用（启用的逆操作）", "DISABLED", "ACTIVE", "REVOKED", "ACTIVE", "ACTIVE", "DISABLED",
                        "-- 注册行保留；恢复 client-onboarding-c03（只在旧 BFF / client-master 仍提供 form-options 时有意义）。")

    hdr_lo, hdr_hi = rng("header", len(headers))
    qp_lo, qp_hi = rng("query", len(all_queries)) if all_queries else (None, None)
    grd_lo, grd_hi = rng("guard", len(guards))
    menu_ids = [res_ids[m["code"]] for m in menus]
    button_ids = [res_ids[b["code"]] for b in buttons]
    rollback = [
        "-- Platform 块 138 彻底移除（按 ID 段删除，子表先删、按钮先于菜单），只在 mor_platform 执行，客户端须 utf8mb4。由 gen_block138.py 生成。",
        "-- 前置：已执行 block138-deactivate.sql（或从未激活）。本文件同样恢复 client-onboarding-c03（若仍为 DISABLED）。",
        "-- 预期删除：" + "、".join(f"{k} {v}" for k, v in counts.items()) + f"（共 {total}）；c03 0—1；角色 1。",
        "START TRANSACTION;", "SET @b138_rollback_time = UTC_TIMESTAMP(6);",
        f"DELETE FROM t_gateway_guard_rule WHERE guard_rule_id BETWEEN {grd_lo} AND {grd_hi};", "SELECT ROW_COUNT() AS guard_rules;"]
    if all_queries:
        rollback += [f"DELETE FROM t_gateway_route_query_param WHERE route_query_param_id BETWEEN {qp_lo} AND {qp_hi};",
                     "SELECT ROW_COUNT() AS query_params;"]
    rollback += [
        f"DELETE FROM t_gateway_route_header WHERE route_header_id BETWEEN {hdr_lo} AND {hdr_hi};", "SELECT ROW_COUNT() AS route_headers;",
        f"DELETE FROM t_gateway_route WHERE route_id BETWEEN {r_lo} AND {r_hi};", "SELECT ROW_COUNT() AS routes;",
    ]
    if button_ids:
        rollback += [f"DELETE FROM t_console_resource WHERE resource_id IN ({', '.join(button_ids)});", "SELECT ROW_COUNT() AS buttons;"]
    if menu_ids:
        rollback += [f"DELETE FROM t_console_resource WHERE resource_id IN ({', '.join(menu_ids)});", "SELECT ROW_COUNT() AS menus;"]
    if perms:
        rollback += [f"DELETE FROM t_role_permission WHERE role_permission_id BETWEEN {g_lo} AND {g_hi};", "SELECT ROW_COUNT() AS grants;",
                     f"DELETE FROM t_permission WHERE permission_id BETWEEN {rng('permission', len(perms))[0]} AND {rng('permission', len(perms))[1]};",
                     "SELECT ROW_COUNT() AS permissions;"]
    if c03:
        rollback += ["UPDATE t_gateway_route SET status='ACTIVE', version=version+1, update_time=@b138_rollback_time",
                     f" WHERE route_code IN ({', '.join(q(c) for c in c03)}) AND status='DISABLED';", "SELECT ROW_COUNT() AS form_options_route;"]
    if perms:
        rollback += [f"UPDATE t_role SET version=version+1, update_time=@b138_rollback_time WHERE role_id={ROLE};", "SELECT ROW_COUNT() AS role;"]
    rollback += [
        "COMMIT;", ""]

    lo, hi = f"{B}001", f"{B}999"
    pre = [
        "-- Platform 块 138 执行前只读核对，只在 mor_platform 执行。由 gen_block138.py 生成。",
        "-- 期望：第 1 段本文件 ID 区间各表 0；第 2 段新 code 0；第 3 段新路径 0；第 4 段既有依赖每行 1；第 5 段 c03 为 ACTIVE（激活时将停用）。",
        "SELECT 'new_permission_codes' AS t, COUNT(*) AS n FROM t_permission WHERE permission_code IN (" + (", ".join(q(p["code"]) for p in perms) or "NULL") + ")",
        "UNION ALL SELECT 'new_resource_codes', COUNT(*) FROM t_console_resource WHERE resource_code IN (" + (", ".join(q(r["code"]) for r in resources) or "NULL") + ")",
        "UNION ALL SELECT 'new_route_codes', COUNT(*) FROM t_gateway_route WHERE route_code IN (" + ", ".join(q(r["routeCode"]) for r in routes) + ");",
        "SELECT 'new_paths' AS t, COUNT(*) AS n FROM t_gateway_route WHERE (http_method, external_path_template) IN (" +
        ", ".join(f"({q(r['method'])}, {q(r['externalPath'])})" for r in routes) + ");",
    ]
    own = [("t_permission", "permission_id", "permission", len(perms)), ("t_role_permission", "role_permission_id", "grant", len(perms)),
           ("t_console_resource", "resource_id", "resource", len(resources)), ("t_gateway_route", "route_id", "route", len(routes)),
           ("t_gateway_route_header", "route_header_id", "header", len(headers)),
           ("t_gateway_route_query_param", "route_query_param_id", "query", len(all_queries)),
           ("t_gateway_guard_rule", "guard_rule_id", "guard", len(guards))]
    pre.insert(2, "\nUNION ALL ".join(f"SELECT {q(t)} AS t, COUNT(*) AS n FROM {t} WHERE {c} BETWEEN {rng(k, n)[0]} AND {rng(k, n)[1]}"
                                     for t, c, k, n in own if n) + ";")
    dep = [f"SELECT {q('dep:' + n)} AS t, COUNT(*) AS n FROM ({sel}) x" for n, sel in lookups]
    dep.append(f"SELECT 'dep:PLATFORM_ADMIN role' AS t, COUNT(*) AS n FROM t_role WHERE role_id = {ROLE}")
    pre.append("\nUNION ALL ".join(dep) + ";")
    if c03:
        pre.append("SELECT 'c03' AS t, route_code, status FROM t_gateway_route WHERE route_code IN (" + ", ".join(q(c) for c in c03) + ");")
    pre.append("")

    reg_name = reg_name_strict()
    files = {reg_name: reg, reg_name.replace(".sql", ".initial-data.sql"): out_init, f"{PREFIX}-activate.sql": activate, f"{PREFIX}-deactivate.sql": deactivate,
             f"{PREFIX}-rollback.sql": rollback, f"{PREFIX}-precheck.sql": pre}
    for name, lines in files.items():
        (HERE / name).write_text("\n".join(lines))
    print(json.dumps({"counts": counts, "total": total, "routes": len(routes)}, ensure_ascii=False))


if __name__ == "__main__":
    build()
