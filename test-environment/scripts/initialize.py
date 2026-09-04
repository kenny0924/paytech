#!/usr/bin/env python3
"""Explicit operator action; never invoked by Compose, application startup, or Java tests."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import urllib.error
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
os.umask(0o077)


def environment():
    return dict(line.split("=", 1) for line in (ROOT / ".env.test").read_text().splitlines()
                if line and not line.startswith("#"))


def mysql(sql, database="mor_platform"):
    result = subprocess.run([str(ROOT / "compose.sh"), "exec", "-T", "mysql", "sh", "-c",
                             'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql --protocol=socket -uroot '
                             '--default-character-set=utf8mb4 --batch --skip-column-names "$1"', "mysql", database],
                            input=sql, encoding="utf-8", capture_output=True)
    if result.returncode:
        raise SystemExit("Test SQL failed; no retry or destructive recovery was performed. Inspect the reviewed SQL and test database.")
    return result.stdout.strip()


def count(sql):
    return int(mysql(sql))


def apply_platform_sql():
    sql_dir = ROOT.parent / "platform-support-service/docs/database/sql"
    tables = count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_platform';")
    if tables == 0:
        mysql((sql_dir / "platform-support-schema.sql").read_text(encoding="utf-8"))
        mysql((sql_dir / "platform-support-initial-data.sql").read_text(encoding="utf-8"))
        print("Applied reviewed Platform schema and initial catalog to the new mor-test database.")
    elif tables != 22 or count("SELECT COUNT(*) FROM t_gateway_route;") not in (13, 17, 18, 19, 20):
        raise SystemExit("Unexpected/partial test schema or catalog; manual review required, nothing replaced.")
    accounts = count("SELECT COUNT(*) FROM t_user_account;")
    keys = count("SELECT COUNT(*) FROM t_jwt_signing_key;")
    if accounts == 0 and keys == 0:
        mysql((ROOT / "private/bootstrap.sql").read_text(encoding="utf-8"))
        print("Initialized the test-only Bootstrap verifier and matching real public signing key.")
    elif accounts == 0 or keys == 0:
        raise SystemExit("Partial authentication initialization; refusing to recreate/reset Bootstrap or keys.")
    else:
        print("Existing Accounts and signing authority preserved; no Bootstrap reset.")


def nacos_request(method, path, values=None, token=None):
    headers = {"Content-Type": "application/x-www-form-urlencoded"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = urllib.parse.urlencode(values or {}).encode() if method != "GET" else None
    request = urllib.request.Request("http://127.0.0.1:18848/nacos" + path,
                                     data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(request, timeout=10) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        try:
            result = json.loads(error.read())
        except (ValueError, UnicodeDecodeError):
            result = {}
        return error.code, result
    except urllib.error.URLError:
        raise SystemExit("Test Nacos is unreachable on 127.0.0.1:18848; check mor-test health and port mappings.") from None


def require_success(result, operation):
    status, body = result
    if status != 200 or body.get("code", 0) not in (0, 200):
        raise SystemExit(f"Nacos {operation} failed (HTTP {status}, code {body.get('code')}); secrets omitted.")
    return body


def login(username, password):
    body = require_success(nacos_request("POST", "/v3/auth/user/login",
                                        {"username": username, "password": password}), "login")
    token = body.get("accessToken") or (body.get("data") or {}).get("accessToken")
    if not token:
        raise SystemExit("Nacos login returned no token; secrets omitted.")
    return token


def initialize_nacos():
    values = environment()
    marker = ROOT / "private/nacos-initialized"
    if marker.exists():
        login("mor-test-services", values["MOR_NACOS_SERVICE_PASSWORD"])
        print("Existing test Nacos service credentials verified.")
        return
    # The bootstrap API refuses replacement after an admin exists. The following login proves ownership.
    nacos_request("POST", "/v3/auth/user/admin", {"password": values["MOR_NACOS_ADMIN_PASSWORD"]})
    token = login("nacos", values["MOR_NACOS_ADMIN_PASSWORD"])
    result = nacos_request("POST", "/v3/admin/core/namespace",
                           {"namespaceId": "mor-test", "namespaceName": "mor-test",
                            "namespaceDesc": "Independent Operations Docker test environment"}, token)
    require_success(result, "namespace creation")
    require_success(nacos_request("POST", "/v3/auth/user",
                    {"username": "mor-test-services", "password": values["MOR_NACOS_SERVICE_PASSWORD"]}, token), "service-user creation")
    require_success(nacos_request("POST", "/v3/auth/role",
                    {"role": "MOR_TEST_SERVICES", "username": "mor-test-services"}, token), "role binding")
    require_success(nacos_request("POST", "/v3/auth/permission",
                    {"role": "MOR_TEST_SERVICES", "resource": "mor-test:*:*", "action": "rw"}, token), "namespace permission")
    login("mor-test-services", values["MOR_NACOS_SERVICE_PASSWORD"])
    marker.write_text("mor-test namespace and scoped service identity initialized\n")
    marker.chmod(0o600)
    print("Created mor-test Nacos namespace and its scoped service identity.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply-platform-sql", action="store_true", help="explicitly apply the reviewed SQL to mor-test only")
    parser.add_argument("--nacos", action="store_true", help="initialize the new test namespace and identities")
    options = parser.parse_args()
    if not options.apply_platform_sql and not options.nacos:
        parser.error("choose an explicit initialization action")
    if options.apply_platform_sql:
        apply_platform_sql()
    if options.nacos:
        initialize_nacos()
