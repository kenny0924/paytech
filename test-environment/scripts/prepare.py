#!/usr/bin/env python3
"""Generate independent test-only material. Never read or copy local-environment secrets."""
import base64
import json
import os
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[1]
PRIVATE = ROOT / "private"
ENV = ROOT / ".env.test"
os.umask(0o077)


# V2 服务拆分（refactor 第 03–12 步，design/10 §5.1）新增的库账号与卡指纹密钥。已有 .env.test 时只追加缺失项，不改已有值。
V2_DATABASE_PASSWORDS = (
    "MOR_CLIENT_MASTER_DB_PASSWORD", "MOR_COMMERCIAL_DB_PASSWORD", "MOR_ACQUIRING_DB_PASSWORD",
    "MOR_COUNTERPARTY_DB_PASSWORD", "MOR_CHANNEL_CONNECTOR_DB_PASSWORD", "MOR_CARD_DB_PASSWORD",
    "MOR_RISK_DECISION_DB_PASSWORD", "MOR_COMPLIANCE_DB_PASSWORD", "MOR_LEDGER_DB_PASSWORD",
    "MOR_BILLING_DB_PASSWORD", "MOR_ACCOUNT_DB_PASSWORD", "MOR_RECONCILIATION_DB_PASSWORD",
    "MOR_TAX_DB_PASSWORD", "MOR_CDC_DB_PASSWORD", "MOR_PAYOUT_DB_PASSWORD")


def v2_values():
    values = {name: secrets.token_hex(24) for name in V2_DATABASE_PASSWORDS}
    values["MOR_CARD_FINGERPRINT_KEY"] = base64.b64encode(secrets.token_bytes(32)).decode()
    return values


def append_missing_v2_values():
    """Append V2 keys absent from an existing .env.test; existing keys and values are never rewritten."""
    present = {line.split("=", 1)[0] for line in ENV.read_text().splitlines() if line and not line.startswith("#")}
    missing = {name: value for name, value in v2_values().items() if name not in present}
    if not missing:
        return []
    with ENV.open("a") as stream:
        stream.write("".join(f"{key}={value}\n" for key, value in missing.items()))
    ENV.chmod(0o600)
    return sorted(missing)


def write_new(path, value):
    with path.open("x") as stream:
        stream.write(value)
    path.chmod(0o600)


def run(arguments):
    result = subprocess.run(arguments, capture_output=True, text=True)
    if result.returncode:
        # Commands only contain file names; private material is never sent to stdout.
        raise SystemExit(f"Preparation failed: {arguments[0]}. Check the tool/runtime installation.")


def java_material():
    java_home = os.environ.get("JAVA_HOME", "/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home")
    repository = Path(os.environ.get("MAVEN_LOCAL_REPOSITORY", str(Path.home() / ".m3/repository")))
    paths = [repository / "org/springframework/security/spring-security-crypto/7.0.7/spring-security-crypto-7.0.7.jar",
             repository / "org/bouncycastle/bcprov-jdk18on/1.85/bcprov-jdk18on-1.85.jar"]
    # Argon2PasswordEncoder uses Spring's logging bridge.
    bridges = sorted((repository / "org/springframework/spring-jcl").glob("*/spring-jcl-*.jar"))
    commons = sorted((repository / "commons-logging/commons-logging").glob("*/commons-logging-*.jar"))
    paths += (commons or bridges)[-1:]
    if not all(path.is_file() for path in paths) or len(paths) != 3:
        raise SystemExit("Install the paytech/ Reactor (build, foundation) and Platform dependencies first; required crypto JARs are absent.")
    run([str(Path(java_home) / "bin/java"), "--class-path", os.pathsep.join(map(str, paths)),
         str(ROOT / "scripts/GenerateOperationsMaterial.java"), str(PRIVATE)])


def main():
    if ENV.exists() or PRIVATE.exists():
        required = [ENV, PRIVATE / "operations-material.json", PRIVATE / "platform.env",
                    PRIVATE / "bootstrap.sql"]
        if not all(path.is_file() for path in required):
            raise SystemExit("Partial test material exists. Inspect and recover it; preparation never overwrites secrets.")
        added = append_missing_v2_values()
        print("Existing test material retained; no credentials or keys were regenerated.")
        if added:
            print("Appended missing V2 test keys (values not shown): " + ", ".join(added))
        return
    PRIVATE.mkdir(mode=0o700)
    java_material()
    material = json.loads((PRIVATE / "operations-material.json").read_text())
    passwords = ["MOR_MYSQL_ROOT_PASSWORD", "MOR_CCT_DB_PASSWORD", "MOR_COMMERCE_DB_PASSWORD",
                 "MOR_PAYMENT_DB_PASSWORD", "MOR_RISK_DB_PASSWORD", "MOR_FINANCE_DB_PASSWORD",
                 "MOR_PLATFORM_DB_PASSWORD", "MOR_VAULT_DB_PASSWORD", "MOR_NACOS_DB_PASSWORD",
                 "MOR_XXL_JOB_DB_PASSWORD", "PAYTECH_FOUNDATION_TEST_DB_PASSWORD", "PAYTECH_REDIS_PASSWORD",
                 "MOR_RABBITMQ_PASSWORD", "MOR_NACOS_ADMIN_PASSWORD", "MOR_NACOS_SERVICE_PASSWORD",
                 "MOR_NACOS_AUTH_IDENTITY_VALUE", "MOR_XXL_JOB_ACCESS_TOKEN", "MOR_XXL_JOB_LOGIN_TOKEN_KEY",
                 "MOR_XXL_JOB_ADMIN_PASSWORD"]
    values = {name: secrets.token_hex(24) for name in passwords}
    values.update(v2_values())
    values.update(MOR_RABBITMQ_USER="mor_test", MOR_RABBITMQ_VHOST="/mor-test",
                  MOR_NACOS_AUTH_IDENTITY_KEY="mor-test-server",
                  MOR_NACOS_AUTH_TOKEN=base64.b64encode(secrets.token_bytes(48)).decode(),
                  OPERATIONS_ACCESS_LOG_HMAC_MATERIAL=base64.b64encode(secrets.token_bytes(32)).decode())
    write_new(ENV, "# Private mor-test material. Never commit or print resolved Compose configuration.\n" +
              "".join(f"{key}={value}\n" for key, value in values.items()))
    write_new(PRIVATE / "platform.env", "".join([
        f"OPERATIONS_LOCAL_SIGNING_PRIVATE_KEY_BASE64={material['privateKeyBase64']}\n",
        f"OPERATIONS_LOCAL_HMAC_KEY_BASE64={material['hmacKeyBase64']}\n",
        f"OPERATIONS_LOCAL_HMAC_KEY_VERSION={material['hmacKeyVersion']}\n"]))
    print("Independent test credentials and signing material created (0600); no database changes made.")


if __name__ == "__main__":
    main()
