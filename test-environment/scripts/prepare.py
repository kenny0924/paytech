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
        print("Existing test material retained; no credentials or keys were regenerated.")
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
