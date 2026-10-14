#!/usr/bin/env python3
"""Explicit mor-test Vault preparation, reviewed SQL initialization and deployment smoke."""
import argparse
import base64
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import secrets
import subprocess
import urllib.parse
import uuid

from initialize import environment, login, nacos_request, require_success

ROOT = Path(__file__).resolve().parents[1]
TABLES = {"t_vault_record", "t_vault_protect_request", "t_vault_access_event"}
KEY_ID = "test-local-aes-v1"
VALUE = "synthetic-vault-deployment-value-v1"
CALLER = "vault-test-smoke"
PURPOSE = "TEST_DEPLOYMENT"
POLICY_VERSION = json.loads((ROOT / "vault/policy.json").read_text(encoding="utf-8"))["policyVersion"]
os.umask(0o077)


def run(arguments, data=None):
    result = subprocess.run(arguments, input=data, text=True, encoding="utf-8",
                            capture_output=True, timeout=45)
    if result.returncode:
        raise SystemExit("Vault deployment command failed; output omitted to protect secrets. No automatic retry.")
    return result.stdout


def mysql(sql):
    return run([str(ROOT / "compose.sh"), "exec", "-T", "mysql", "sh", "-c",
                'MYSQL_PWD="$MOR_VAULT_DB_PASSWORD" exec mysql --protocol=socket '
                '-uvault_app --default-character-set=utf8mb4 --batch --skip-column-names mor_vault'], sql).strip()


def database_tables():
    names = set(mysql("SELECT table_name FROM information_schema.tables WHERE table_schema='mor_vault';").splitlines())
    if names and names != TABLES:
        raise SystemExit("Partial/unexpected test Vault schema; preserve it and review manually.")
    return names


def key_values(path):
    if path.is_symlink() or not path.is_file() or path.stat().st_mode & 0o077:
        raise SystemExit("Vault material must be an existing private regular 0600 file, never a symlink.")
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
        values = dict(line.split("=", 1) for line in lines)
        valid = len(lines) == 2 and set(values) == {"VAULT_LOCAL_AES_ROOT_KEY", "VAULT_LOCAL_AES_KEY_ID"}
        valid = valid and values["VAULT_LOCAL_AES_KEY_ID"] == KEY_ID
        valid = valid and len(base64.b64decode(values["VAULT_LOCAL_AES_ROOT_KEY"], validate=True)) == 32
    except (OSError, ValueError):
        valid = False
    if not valid:
        raise SystemExit("Incomplete/invalid Vault material; no overwrite or key rotation was performed.")
    return values


def prepare_key(root, tables):
    path = root / "private/vault.env"
    if path.parent.is_symlink():
        raise SystemExit("Vault private directory must not be a symlink.")
    if path.exists() or path.is_symlink():
        key_values(path)
        return
    if tables:
        raise SystemExit("Existing Vault database has no key file; restore its original key, never generate a replacement.")
    path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    if path.parent.stat().st_mode & 0o077:
        raise SystemExit("Vault private directory must have mode 0700.")
    key = base64.b64encode(secrets.token_bytes(32)).decode("ascii")
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w", encoding="utf-8") as output:
        output.write(f"VAULT_LOCAL_AES_ROOT_KEY={key}\nVAULT_LOCAL_AES_KEY_ID={KEY_ID}\n")


def initialize_schema():
    key_values(ROOT / "private/vault.env")
    if database_tables():
        print("Existing Vault tables preserved; runtime performs exact schema validation.")
        return
    schema = ROOT.parent / "vault-service/docs/database/sql/vault-schema.sql"
    mysql(schema.read_text(encoding="utf-8"))
    if database_tables() != TABLES:
        raise SystemExit("Vault initialization incomplete; inspect manually, do not replay DDL.")
    print("Applied the reviewed three-table Vault schema to empty mor-test/mor_vault only.")


def request(path, body=None, caller=CALLER, key=None, status=200, request_id=None):
    arguments = [str(ROOT / "compose.sh"), "exec", "-T", "platform-support-service",
                 "curl", "--silent", "--show-error", "--max-time", "15", "--path-as-is",
                 "--write-out", "\n%{http_code}", "--header", "Accept: application/json"]
    if body is not None:
        arguments += ["--request", "POST", "--header", "Content-Type: application/json",
                      "--header", "X-Caller-Service: " + caller,
                      "--header", "X-Request-Id: " + (request_id or uuid.uuid4().hex), "--data-binary", "@-"]
    if key:
        arguments += ["--header", "Idempotency-Key: " + key]
    arguments += ["http://vault-service:18107" + path]
    raw, actual = run(arguments, json.dumps(body) if body is not None else None).rsplit("\n", 1)
    if int(actual) != status:
        raise SystemExit(f"Vault {path}: expected HTTP {status}, got {actual}; response omitted.")
    return json.loads(raw) if raw else None


def discovery():
    token = login("mor-test-services", environment()["MOR_NACOS_SERVICE_PASSWORD"])
    query = urllib.parse.urlencode({"namespaceId": "mor-test", "groupName": "DEFAULT_GROUP", "serviceName": "vault-service"})
    result = require_success(nacos_request("GET", "/v3/client/ns/instance/list?" + query, token=token), "Vault discovery")
    instances = result.get("data") or []
    if len(instances) != 1 or instances[0].get("ip") != "172.30.73.24" or instances[0].get("port") != 18107:
        raise SystemExit("Expected exactly the test Vault Nacos instance; do not use another namespace or instance.")
    if not instances[0].get("healthy") or not instances[0].get("enabled"):
        raise SystemExit("Test Vault Nacos instance is not healthy/enabled.")
    print("PASS: Nacos mor-test/DEFAULT_GROUP/vault-service, one healthy instance.")


def protect_body(data_type="TEST_STABLE_V1", value=VALUE):
    mode = "STABLE" if data_type == "TEST_STABLE_V1" else "REFERENCE"
    return {"dataType": data_type, "tokenMode": mode, "value": value, "purpose": PURPOSE}


def require(condition, message):
    if not condition:
        raise SystemExit(message)


def smoke():
    started = datetime.now(timezone.utc).isoformat()
    discovery()
    request("/health/liveness")
    request("/health/readiness")
    protect = "/vault/v1/records/protect"
    reveal = "/vault/v1/records/reveal"
    body = protect_body()
    token = request(protect, body, key="test-deployment-v1-stable")["token"]
    replay = request(protect, body, key="test-deployment-v1-stable")["token"]
    peer = request(protect, body, caller="vault-test-peer", key="test-deployment-v1-peer")["token"]
    require(token == replay == peer, "STABLE cross-caller convergence/idempotency failed.")
    evidence_id = uuid.uuid4().hex
    revealed = request(reveal, {"token": token, "purpose": PURPOSE}, request_id=evidence_id)
    require(revealed["value"] == VALUE, "Reveal does not recover the original synthetic value.")
    request(protect, protect_body(value=VALUE + "-changed"), key="test-deployment-v1-stable", status=409)
    first = request(protect, protect_body("TEST_REFERENCE_V1"), key="test-deployment-v1-reference-a")["token"]
    second = request(protect, protect_body("TEST_REFERENCE_V1"), key="test-deployment-v1-reference-b")["token"]
    require(first != second, "REFERENCE commands unexpectedly share one Token.")
    require(request(reveal, {"token": first, "purpose": PURPOSE})["value"] == VALUE, "REFERENCE Reveal failed.")
    request(reveal, {"token": token, "purpose": PURPOSE}, caller="vault-test-unauthorized", status=403)
    request(reveal, {"token": token, "purpose": "UNAPPROVED_PURPOSE"}, status=403)
    for path in ("/internal/v1/vault/records:protect", "/internal/v1/vault/records:reveal", "/vault/v1/records:protect"):
        request(path, body, key="test-deployment-v1-legacy", status=404)
    audit = mysql("SELECT COUNT(*) FROM t_vault_access_event WHERE request_id='" + evidence_id + "' "
                  "AND action='REVEAL' AND outcome='SUCCESS' AND policy_version='" + POLICY_VERSION + "';")
    require(audit == "1", "Reveal success audit was not persisted exactly once.")
    logs = run([str(ROOT / "compose.sh"), "logs", "--no-color", "--since", started, "vault-service"])
    sensitive = [VALUE, token, first, second, key_values(ROOT / "private/vault.env")["VAULT_LOCAL_AES_ROOT_KEY"]]
    require(not any(value in logs for value in sensitive), "Sensitive canary found in Vault logs; contents withheld.")
    print("PASS: health, STABLE across callers, idempotency/conflict, REFERENCE, Reveal, denial, old URL 404, audit and log scan.")
    print("Synthetic records/audit retained in mor-test; no cleanup, Bootstrap or other service data changes.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("prepare", "initialize", "smoke"))
    options = parser.parse_args()
    try:
        if options.action == "prepare":
            prepare_key(ROOT, database_tables())
            print("Test-only Vault AES material prepared/preserved; no secret values printed.")
        elif options.action == "initialize":
            initialize_schema()
        else:
            smoke()
    except (OSError, ValueError, subprocess.TimeoutExpired):
        raise SystemExit("Vault deployment check failed; sensitive details omitted. Inspect configuration and health, no automatic retry.") from None
