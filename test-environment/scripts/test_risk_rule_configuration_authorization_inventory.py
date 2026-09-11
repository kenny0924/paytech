#!/usr/bin/env python3
"""Source-only authorization inventory for the Risk Operations rollout.

This gate deliberately reads repository text only.  It must never import an
operator adapter, inspect ``test-environment/private``, or invoke Docker, SQL,
HTTP, Maven, npm, or another subprocess.
"""

from pathlib import Path
import json
import hashlib
import re
import unittest


ROOT = Path(__file__).resolve().parents[2]
TEST_ENVIRONMENT = ROOT / "test-environment"
PLAN = ROOT / "docs/superpowers/plans/2026-09-07-risk-operations-local-integration.md"
README = TEST_ENVIRONMENT / "README.md"

BUILD = TEST_ENVIRONMENT / "scripts/build.sh"
DEPLOY = TEST_ENVIRONMENT / "deploy.sh"
COMPOSE_WRAPPER = TEST_ENVIRONMENT / "compose.sh"
COMPOSE_BASE = TEST_ENVIRONMENT / "compose.yaml"
COMPOSE_APPLICATIONS = TEST_ENVIRONMENT / "compose.applications.yaml"
INITIALIZER = TEST_ENVIRONMENT / "scripts/initialize.py"
DEFAULT_SMOKE = TEST_ENVIRONMENT / "scripts/smoke.py"
RISK_SMOKE = TEST_ENVIRONMENT / "scripts/risk_rule_configuration_smoke.py"
RISK_V1_SMOKE = TEST_ENVIRONMENT / "scripts/risk_rule_v1_configuration_smoke.py"
ROLLOUT = TEST_ENVIRONMENT / "scripts/risk_rule_configuration_rollout.py"
RECOVERY = TEST_ENVIRONMENT / "scripts/risk_rule_configuration_recovery.py"
SNAPSHOT_WAIT = TEST_ENVIRONMENT / "scripts/gateway_snapshot_wait.py"
SNAPSHOT_METRICS = TEST_ENVIRONMENT / "scripts/gateway_snapshot_metrics.py"
RISK_STAGE = TEST_ENVIRONMENT / "scripts/risk_rule_configuration_stage.py"
REQUIREMENTS_PACKAGE = ROOT / "requirements-site/package.json"
REQUIREMENTS_LAYOUT = ROOT / "requirements-site/app/layout.tsx"
JAVA_DOCKERFILE = TEST_ENVIRONMENT / "java/Dockerfile"
LOCAL_HTTP_HEALTH_PROBE = TEST_ENVIRONMENT / "java/LocalHttpHealthProbe.java"
LOCAL_SNAPSHOT_EVIDENCE_FETCH = (
    TEST_ENVIRONMENT / "java/LocalSnapshotEvidenceFetch.java"
)
NACOS_DOCKERFILE = TEST_ENVIRONMENT / "nacos/Dockerfile"
VAULT_DOCKERFILE = TEST_ENVIRONMENT / "vault/Dockerfile"
WEB_DOCKERFILE = ROOT / "operations-console-web/Dockerfile"
NGINX_CONFIGURATION = TEST_ENVIRONMENT / "nginx/nginx.conf"
WEB_NGINX_CONFIGURATION = ROOT / "operations-console-web/deploy/nginx.conf"

READ_ONLY_SOURCES = (
    PLAN,
    README,
    BUILD,
    DEPLOY,
    COMPOSE_WRAPPER,
    COMPOSE_BASE,
    COMPOSE_APPLICATIONS,
    INITIALIZER,
    DEFAULT_SMOKE,
    RISK_SMOKE,
    ROLLOUT,
    RECOVERY,
    SNAPSHOT_WAIT,
    REQUIREMENTS_PACKAGE,
    REQUIREMENTS_LAYOUT,
)

# Fail closed if any reviewed executable/configuration source changes between
# the source-only inventory review and the authorized rollout.  A legitimate
# edit requires a fresh review and an explicit digest update here.
REVIEWED_SOURCE_PATHS = READ_ONLY_SOURCES + (
    RISK_V1_SMOKE,
    SNAPSHOT_METRICS,
    RISK_STAGE,
    JAVA_DOCKERFILE,
    LOCAL_HTTP_HEALTH_PROBE,
    LOCAL_SNAPSHOT_EVIDENCE_FETCH,
    NACOS_DOCKERFILE,
    VAULT_DOCKERFILE,
    WEB_DOCKERFILE,
    NGINX_CONFIGURATION,
    WEB_NGINX_CONFIGURATION,
)

REVIEWED_SOURCE_DIGESTS = {
    RISK_V1_SMOKE: "e6adb863163a7cb12f6be851ed73682922b7f5a7191747ddde8259f8eadd90ff",
    PLAN: "7a42138ee04ed0dd006e87d1aef0369f3d251df75ff712caa69a3e621abb3678",
    README: "fe4b09e024d7601adf959da0caaa7bff3251b95f62014225a19148a39554e4e2",
    BUILD: "93687e6bafe31dcf29e07f8039377814b9da7c0451dddc4eafb02d6301b3eb2a",
    DEPLOY: "61770117c3aafc2c40debcc80e3f4ae5f87e4def3e3b018614b46a0e6bc5c562",
    COMPOSE_WRAPPER: "e1e215d4305d2a0af9ab8017cfa6cdf2eef5565dd8143b114283ec5bd7ce6c05",
    COMPOSE_BASE: "23df38a23d0432e3851a829b18c8e58ebe9550c915bf0b9e5613c5d1d6a404cf",
    COMPOSE_APPLICATIONS: "fc1c6b43966771308a978420e6e367e951e9a009beb7bc23960fb6cec817717e",
    INITIALIZER: "ad9663844773fef2e707cfd020f14d00d095dd487756cbf94c43b6943116ae3e",
    DEFAULT_SMOKE: "2af4f445af2c1b38dfd4da2e9855ecd1767b0a528c12740e2b37d4ec61794551",
    RISK_SMOKE: "9d1e6024bb04aff2bbf238f5606bd26bdeee78bd9486f73fa357fcea01edc7e5",
    ROLLOUT: "0b7e223ec17db0a7283acb063298562f8754c92937bda9c942cbe789ce4a83b9",
    RECOVERY: "7d0cbda018a71d90fc06a856d4a627724e9f26515bd2899c2f6a9af109acbf80",
    SNAPSHOT_WAIT: "8d06203a1d08ac14fea03ed0ba4d0868be891243b44ec06a7da4b3d0b4b251d4",
    SNAPSHOT_METRICS: "c512f6b7aa57e172f4984073fc42a3ca1ad3c8f01bdbb24ede36ea3c36f5bdd1",
    RISK_STAGE: "19e086845e6e7caf3125da4feec45c178812cb6b1e4a29504816803e79f92a43",
    REQUIREMENTS_PACKAGE: "418f6b58f27f998f4660ce65e4f1cb1d4cbae4634972b7f284922c9fe20db8bd",
    REQUIREMENTS_LAYOUT: "656662724e8b526143e3a0f16933e513beb91547e2a2a7510d95c13d67af523d",
    JAVA_DOCKERFILE: "afc9b88f9f18c95d1bb07cb38aba9d7b725487912fa90036e07799a5081bb9f1",
    LOCAL_HTTP_HEALTH_PROBE: "641fcad677511ea9d0560b195e2a86d0b2f739f35c9a34f2f0732813eb2903ec",
    LOCAL_SNAPSHOT_EVIDENCE_FETCH: "bfd582f43c0ce9576561a61fcacc1b4e4b0ee78623c057e7e60b30a9f6a37942",
    NACOS_DOCKERFILE: "8454faad2cf0a4028c583ec1739ebd68869a09031ca714b845b1320cff5efb11",
    VAULT_DOCKERFILE: "f6afd4c4e02fb7cae0c504631fdde0afeb5a9b5ae5fe07c23a303224fd403ec0",
    WEB_DOCKERFILE: "bd732cb721dcdfa089bf9e50b46094e54e79569fee0580a3fb716f2cc3684bcb",
    NGINX_CONFIGURATION: "c0924e601767456f6aea6481125ee5d1f950185f7d22c8b6e93f36c0c04e61f4",
    WEB_NGINX_CONFIGURATION: "b04ba0925de617e851bc9423cd0a0535b0b4bc37a0d9323f6eb4ca55f8cc083a",
}

AUTHORIZED_CATEGORIES = frozenset(
    {
        "dependency-repository-and-cache",
        "requirements-font-network-and-output",
        "docker-image-and-build-cache",
        "container-process-network-and-volume",
        "mysql-container-identity-preservation",
        "platform-backup-and-private-directories",
        "risk-owner-sql",
        "platform-catalog-and-resource-sql",
        "deploy-lock-and-phase-checkpoint",
        "rollout-lock-and-route-status",
        "credential-and-iam-fixture",
        "session-security-audit-and-guard",
        "immutable-synthetic-risk-facts",
        "browser-acceptance-and-screenshot-evidence",
        "rollout-evidence-report",
    }
)

# Every operator family in Task 7 Steps 2a-13, Task 8 and Task 9 Steps 1-10 that
# can change local state is mapped to the exact mutation classes that the user
# must authorize before rollout.  Staging and commit stay behind later authority.
ACTION_AUTHORIZATION = {
    "requirements-site-full-gate": frozenset(
        {
            "dependency-repository-and-cache",
            "requirements-font-network-and-output",
        }
    ),
    "canonicalization-freshness-and-vector-gate": frozenset(
        {"dependency-repository-and-cache"}
    ),
    "mysql-preflight-and-start": frozenset(
        {
            "docker-image-and-build-cache",
            "container-process-network-and-volume",
            "mysql-container-identity-preservation",
        }
    ),
    "platform-backup": frozenset(
        {
            "container-process-network-and-volume",
            "platform-backup-and-private-directories",
        }
    ),
    "risk-catalog-apply": frozenset(
        {
            "container-process-network-and-volume",
            "risk-owner-sql",
        }
    ),
    "nginx-configuration-probe": frozenset(
        {
            "docker-image-and-build-cache",
            "container-process-network-and-volume",
        }
    ),
    "deploy-risk-bff-web": frozenset(
        {
            "dependency-repository-and-cache",
            "docker-image-and-build-cache",
            "container-process-network-and-volume",
            "mysql-container-identity-preservation",
            "deploy-lock-and-phase-checkpoint",
        }
    ),
    "deploy-platform-gateway-nginx": frozenset(
        {
            "docker-image-and-build-cache",
            "container-process-network-and-volume",
            "deploy-lock-and-phase-checkpoint",
            "credential-and-iam-fixture",
            "session-security-audit-and-guard",
        }
    ),
    "platform-catalog-and-resource-actions": frozenset(
        {
            "container-process-network-and-volume",
            "platform-catalog-and-resource-sql",
            "rollout-lock-and-route-status",
        }
    ),
    "route-inspect-activate-disable-reconcile-recover": frozenset(
        {
            "container-process-network-and-volume",
            "rollout-lock-and-route-status",
            "session-security-audit-and-guard",
        }
    ),
    "snapshot-and-runtime-read-probes": frozenset(
        {
            "container-process-network-and-volume",
            "session-security-audit-and-guard",
        }
    ),
    "iam-fixture-prepare-grant-cleanup": frozenset(
        {
            "credential-and-iam-fixture",
            "session-security-audit-and-guard",
        }
    ),
    "route-and-full-chain-smoke": frozenset(
        {
            "credential-and-iam-fixture",
            "session-security-audit-and-guard",
            "immutable-synthetic-risk-facts",
        }
    ),
    "operator-browser-acceptance": frozenset(
        {
            "credential-and-iam-fixture",
            "session-security-audit-and-guard",
            "immutable-synthetic-risk-facts",
            "browser-acceptance-and-screenshot-evidence",
        }
    ),
    "rollout-evidence-pack": frozenset({"rollout-evidence-report"}),
}

# Independent hand-checked oracle.  Keeping this separate from
# ACTION_AUTHORIZATION makes a category moved between two families observable
# even when the global union of categories is unchanged.
EXPECTED_ACTION_AUTHORIZATION = {
    "requirements-site-full-gate": frozenset(
        {
            "dependency-repository-and-cache",
            "requirements-font-network-and-output",
        }
    ),
    "canonicalization-freshness-and-vector-gate": frozenset(
        {"dependency-repository-and-cache"}
    ),
    "mysql-preflight-and-start": frozenset(
        {
            "docker-image-and-build-cache",
            "container-process-network-and-volume",
            "mysql-container-identity-preservation",
        }
    ),
    "platform-backup": frozenset(
        {
            "container-process-network-and-volume",
            "platform-backup-and-private-directories",
        }
    ),
    "risk-catalog-apply": frozenset(
        {
            "container-process-network-and-volume",
            "risk-owner-sql",
        }
    ),
    "nginx-configuration-probe": frozenset(
        {
            "docker-image-and-build-cache",
            "container-process-network-and-volume",
        }
    ),
    "deploy-risk-bff-web": frozenset(
        {
            "dependency-repository-and-cache",
            "docker-image-and-build-cache",
            "container-process-network-and-volume",
            "mysql-container-identity-preservation",
            "deploy-lock-and-phase-checkpoint",
        }
    ),
    "deploy-platform-gateway-nginx": frozenset(
        {
            "docker-image-and-build-cache",
            "container-process-network-and-volume",
            "deploy-lock-and-phase-checkpoint",
            "credential-and-iam-fixture",
            "session-security-audit-and-guard",
        }
    ),
    "platform-catalog-and-resource-actions": frozenset(
        {
            "container-process-network-and-volume",
            "platform-catalog-and-resource-sql",
            "rollout-lock-and-route-status",
        }
    ),
    "route-inspect-activate-disable-reconcile-recover": frozenset(
        {
            "container-process-network-and-volume",
            "rollout-lock-and-route-status",
            "session-security-audit-and-guard",
        }
    ),
    "snapshot-and-runtime-read-probes": frozenset(
        {
            "container-process-network-and-volume",
            "session-security-audit-and-guard",
        }
    ),
    "iam-fixture-prepare-grant-cleanup": frozenset(
        {
            "credential-and-iam-fixture",
            "session-security-audit-and-guard",
        }
    ),
    "route-and-full-chain-smoke": frozenset(
        {
            "credential-and-iam-fixture",
            "session-security-audit-and-guard",
            "immutable-synthetic-risk-facts",
        }
    ),
    "operator-browser-acceptance": frozenset(
        {
            "credential-and-iam-fixture",
            "session-security-audit-and-guard",
            "immutable-synthetic-risk-facts",
            "browser-acceptance-and-screenshot-evidence",
        }
    ),
    "rollout-evidence-pack": frozenset({"rollout-evidence-report"}),
}

EXPECTED_ACTION_FAMILIES = frozenset(
    {
        "requirements-site-full-gate",
        "canonicalization-freshness-and-vector-gate",
        "mysql-preflight-and-start",
        "platform-backup",
        "risk-catalog-apply",
        "nginx-configuration-probe",
        "deploy-risk-bff-web",
        "deploy-platform-gateway-nginx",
        "platform-catalog-and-resource-actions",
        "route-inspect-activate-disable-reconcile-recover",
        "snapshot-and-runtime-read-probes",
        "iam-fixture-prepare-grant-cleanup",
        "route-and-full-chain-smoke",
        "operator-browser-acceptance",
        "rollout-evidence-pack",
    }
)

ROUTE_CODES = (
    "operations-risk-rule-authoring-context",
    "operations-risk-rule-set-list",
    "operations-risk-rule-set-detail",
    "operations-risk-test-candidate-list",
    "operations-risk-test-candidate-detail",
    "operations-risk-rule-set-save",
    "operations-risk-rule-set-freeze",
    "operations-risk-test-candidate-create",
    "operations-risk-test-candidate-validate",
)

SMOKE_ACTIONS = (
    "--prepare-authorization-fixture",
    "--grant-authorization-fixture",
    "--probe-runtime-stage",
    "--read-stage",
    "--route-smoke",
    "--full",
    "--cleanup-authorization-fixture",
)

ROLLOUT_ACTIONS = (
    "--inspect-stage",
    "--activate-route",
    "--disable-route",
    "--reconcile-baseline",
)

PRIVATE_EFFECT_PATHS = (
    "test-environment/private",
    "test-environment/private/backups",
    "test-environment/private/backups/"
    "mor_platform-before-risk-operations-20260907.sql",
    "test-environment/private/risk-operations-rollout.lock",
    "test-environment/private/deploy.lock",
    "test-environment/private/risk-bff-web-phase.ready",
    "test-environment/private/access.md",
    "test-environment/private/risk-rule-configuration-smoke.json",
)

PINNED_BUILD_BASES = (
    "nacos/nacos-server:v3.1.2-slim@sha256:8e3a5eed20f3fa58f91d806bfad83b5742c73b13280a42c1c3e3c13e16e45f61",
    "eclipse-temurin:25.0.4_7-jre-noble@sha256:b4c93a50fc67612798db73d68ca3b0ee4ebdd51736e59cca370e689b9797037e",
    "nginx:1.30.4-alpine@sha256:dc5069ad14f19660b141b21236140b91656bf89bbc3e2417c70ae650cd66104c",
)

APPROVED_DOCKERFILE_RUNS = {
    "nacos": (
        "RUN sed -i 's/^set -x$/set +x/' /home/nacos/bin/docker-startup.sh "
        "&& ! grep -q '^set -x$' /home/nacos/bin/docker-startup.sh",
    )
}

DOCUMENTED_ACTIONS = {
    "Smoke 1：准备授权夹具": "--prepare-authorization-fixture",
    "Smoke 2：授予五项 Risk 权限": "--grant-authorization-fixture",
    "Smoke 3：探测 Gateway 实际运行阶段": "--probe-runtime-stage",
    "Smoke 4：聚合验证五条读 Route": "--read-stage",
    "Smoke 5：验证刚启用的一条 Route": "--route-smoke",
    "Smoke 6：完整可见链路与 64 KiB 边界": "--full",
    "Smoke 7：清理 synthetic IAM 夹具": "--cleanup-authorization-fixture",
    "Rollout 1：只读检查闭合阶段": "--inspect-stage",
    "Rollout 2：启用一条相邻 Route": "--activate-route",
    "Rollout 3：禁用一条相邻 Route": "--disable-route",
    "Rollout 4：回退到 BASELINE": "--reconcile-baseline",
    "Recovery：恢复一条失败的相邻发布": "--recover-route",
}


def source_text(path):
    if path.parts[-2:] and "private" in path.parts:
        raise AssertionError("the inventory must not inspect real private state")
    return path.read_text(encoding="utf-8")


def task_seven_preflight_sections(plan):
    authorization = plan.split(
        "- [ ] **Step 2: Obtain one explicit authorization", 1
    )[1]
    step_two, task_seven_remainder = authorization.split(
        "- [ ] **Step 2a:", 1
    )
    steps, _ = task_seven_remainder.split("- [ ] **Step 3:", 1)
    sections = {"authorization": step_two}
    for label, content in re.findall(
        r"(?ms)^([^\n]+)\n(.*?)(?=^- \[ \] \*\*Step 2[abc]:|\Z)",
        "- [ ] **Step 2a:" + steps,
    ):
        match = re.match(r"- \[ \] \*\*Step (2[abc]):", label)
        if match is not None:
            sections[match.group(1)] = content
    if frozenset(sections) != {"authorization", "2a", "2b", "2c"}:
        raise AssertionError("Task 7 must contain authorization and Steps 2a/2b/2c")
    return sections


def validate_inventory(mapping):
    if frozenset(mapping) != EXPECTED_ACTION_FAMILIES:
        raise AssertionError("write-capable action families are missing or unknown")
    if mapping != EXPECTED_ACTION_AUTHORIZATION:
        raise AssertionError("authorization categories do not match their exact families")
    for family, categories in mapping.items():
        if not categories or not frozenset(categories).issubset(AUTHORIZED_CATEGORIES):
            raise AssertionError("invalid authorization categories for " + family)
    covered = frozenset().union(*(frozenset(value) for value in mapping.values()))
    if covered != AUTHORIZED_CATEGORIES:
        raise AssertionError("the mutation-category inventory is incomplete")


def dockerfile_instructions(content):
    instructions = []
    pending = ""
    for raw_line in content.splitlines():
        stripped = raw_line.strip()
        if not stripped or (not pending and stripped.startswith("#")):
            continue
        pending += (" " if pending else "") + stripped
        if pending.endswith("\\"):
            pending = pending[:-1].rstrip()
            continue
        instructions.append(pending)
        pending = ""
    if pending:
        raise AssertionError("unterminated Dockerfile continuation")
    return tuple(instructions)


def reject_online_dockerfile_package_steps(dockerfile_texts):
    package_manager = re.compile(r"\b(?:apt(?:-get)?|apk|dnf|yum)\b", re.I)
    remote_add = re.compile(r"(?i)^ADD\s+(?:--\S+\s+)*(?:https?|git)://")
    for name, content in dockerfile_texts.items():
        actual_runs = []
        for instruction in dockerfile_instructions(content):
            operation = instruction.split(None, 1)[0].upper()
            if operation == "RUN" and package_manager.search(instruction):
                raise AssertionError(name + " contains an online OS package-manager step")
            if operation == "ADD" or remote_add.search(instruction):
                raise AssertionError(name + " contains a forbidden ADD")
            if operation == "COPY" and re.search(
                r"(?i)(?:^|\s)--from(?:=|\s)", instruction
            ):
                raise AssertionError(name + " contains a forbidden external COPY")
            if operation == "RUN":
                actual_runs.append(instruction)
        if tuple(actual_runs) != APPROVED_DOCKERFILE_RUNS.get(name, ()):
            raise AssertionError(name + " contains an unapproved RUN instruction")


def shell_logical_commands(content):
    commands = []
    pending = ""
    heredoc_terminator = None
    heredoc_start = re.compile(r"<<-?['\"]?([A-Za-z_][A-Za-z0-9_]*)['\"]?")
    for raw_line in content.splitlines():
        stripped = raw_line.strip()
        if heredoc_terminator is not None:
            if stripped == heredoc_terminator:
                heredoc_terminator = None
            continue
        if not pending:
            match = heredoc_start.search(stripped)
            if match is not None:
                if stripped != "cat <<'EOF'":
                    raise AssertionError("only the fixed literal usage heredoc is allowed")
                heredoc_terminator = match.group(1)
                commands.append(stripped)
                continue
        if not stripped or (not pending and stripped.startswith("#")):
            continue
        pending += (" " if pending else "") + stripped
        if pending.endswith("\\"):
            pending = pending[:-1].rstrip()
            continue
        commands.append(pending)
        pending = ""
    if heredoc_terminator is not None or pending:
        raise AssertionError("unterminated shell continuation or heredoc")
    return tuple(commands)


def validate_reviewed_source_manifest(sources, expected_digests):
    if frozenset(sources) != frozenset(expected_digests):
        raise AssertionError("reviewed source manifest paths differ")
    for path, content in sources.items():
        expected = expected_digests[path]
        if not isinstance(expected, str) or not re.fullmatch(r"[0-9a-f]{64}", expected):
            raise AssertionError("reviewed source digest is invalid: " + str(path))
        actual = hashlib.sha256(content.encode("utf-8")).hexdigest()
        if actual != expected:
            raise AssertionError("reviewed executable source changed: " + str(path))


def validate_phase_one_infrastructure_contract(deploy):
    commands = shell_logical_commands(deploy)
    application_replacement = (
        '"$compose_script" up -d --no-deps --no-build --pull never '
        '--force-recreate --wait --wait-timeout "$wait_timeout" "$service_name"'
    )
    allowed_compose_references = (
        '"$compose_script" ps >&2 || true',
        '[[ -x "$compose_script" ]] || fail "Compose 脚本不存在或不可执行：$compose_script。"',
        '"$compose_script" config --quiet',
        application_replacement,
        '"$compose_script" up -d --no-recreate --no-build --pull never --wait '
        '--wait-timeout "$wait_timeout" "${protected_infrastructure_services[@]}"',
        '"$compose_script" up -d --no-deps --no-build --pull never --wait '
        '--wait-timeout "$wait_timeout" nacos',
        '"$compose_script" ps',
    )
    actual_compose_references = tuple(
        command for command in commands if "$compose_script" in command
    )
    if actual_compose_references != allowed_compose_references:
        raise AssertionError("deployment contains an unreviewed Compose reference")
    allowed_compose_paths = (
        'compose_script="$environment_dir/compose.sh"',
        "printf '诊断命令：./test-environment/compose.sh logs --tail 200 "
        "<service>\\n' >&2",
    )
    actual_compose_paths = tuple(
        command for command in commands if "compose.sh" in command
    )
    if actual_compose_paths != allowed_compose_paths:
        raise AssertionError("deployment contains an unreviewed Compose path")
    if tuple(command for command in commands if "--force-recreate" in command) != (
        application_replacement,
    ):
        raise AssertionError(
            "only the exact application replacement may use --force-recreate"
        )
    protected = (
        '"$compose_script" up -d --no-recreate --no-build --pull never --wait '
        '--wait-timeout "$wait_timeout" "${protected_infrastructure_services[@]}"'
    )
    nacos = (
        '"$compose_script" up -d --no-deps --no-build --pull never --wait '
        '--wait-timeout "$wait_timeout" nacos'
    )
    if commands.count(protected) != 1 or commands.count(nacos) != 1:
        raise AssertionError("phase-one infrastructure guard is incomplete")
    expected_queries = {
        'mysql_container_id_before="$(docker ps -aq --no-trunc '
        '--filter label=com.docker.compose.project=mor-test '
        '--filter label=com.docker.compose.service=mysql)"',
        'mysql_container_id_after="$(docker ps -aq --no-trunc '
        '--filter label=com.docker.compose.project=mor-test '
        '--filter label=com.docker.compose.service=mysql)"',
    }
    actual_queries = {
        command
        for command in commands
        if command.startswith("mysql_container_id_")
        and "docker ps -aq --no-trunc" in command
    }
    if actual_queries != expected_queries:
        raise AssertionError("the MySQL identity must be queried exactly before and after")
    compose_up = tuple(
        command for command in commands if command.startswith('"$compose_script" up ')
    )
    if any(
        "--force-recreate" in command and command != application_replacement
        for command in compose_up
    ):
        raise AssertionError("deployment must never force-recreate infrastructure")
    if 'readonly -a protected_infrastructure_services=(\n  mysql\n  redis\n  rabbitmq\n)' not in deploy:
        raise AssertionError("the protected infrastructure set is not exact")
    if "MySQL" not in deploy or "替换" not in deploy:
        raise AssertionError("MySQL identity drift must fail explicitly")


def strict_json_object(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            raise AssertionError("duplicate JSON object key: " + key)
        value[key] = item
    return value


def validate_requirements_package_contract(source):
    try:
        package = json.loads(source, object_pairs_hook=strict_json_object)
    except (AssertionError, json.JSONDecodeError) as error:
        raise AssertionError("requirements package.json must be strict JSON") from error
    scripts = package.get("scripts")
    if not isinstance(scripts, dict):
        raise AssertionError("requirements package scripts must be an object")
    expected = {
        "build": "WRANGLER_LOG_PATH=.wrangler/wrangler.log vinext build",
        "test": "npm run build && node --test tests/*.test.mjs",
    }
    for name, command in expected.items():
        if scripts.get(name) != command:
            raise AssertionError("unexpected requirements npm script: " + name)
    forbidden_hooks = {"prebuild", "postbuild", "pretest", "posttest"}
    if forbidden_hooks.intersection(scripts):
        raise AssertionError("requirements npm lifecycle hooks are not authorized")


class AuthorizationInventoryShapeTest(unittest.TestCase):
    def test_every_write_capable_family_maps_to_an_exact_category(self):
        validate_inventory(ACTION_AUTHORIZATION)

    def test_inventory_validation_is_causal(self):
        missing = dict(ACTION_AUTHORIZATION)
        missing.pop("mysql-preflight-and-start")
        with self.assertRaises(AssertionError):
            validate_inventory(missing)

        incomplete = dict(ACTION_AUTHORIZATION)
        incomplete["route-and-full-chain-smoke"] = frozenset(
            {"session-security-audit-and-guard"}
        )
        with self.assertRaises(AssertionError):
            validate_inventory(incomplete)

    def test_category_reassignment_between_families_is_rejected(self):
        reassigned = dict(ACTION_AUTHORIZATION)
        reassigned["risk-catalog-apply"] = frozenset(
            {"container-process-network-and-volume"}
        )
        reassigned["requirements-site-full-gate"] = frozenset(
            set(reassigned["requirements-site-full-gate"]) | {"risk-owner-sql"}
        )

        with self.assertRaises(AssertionError):
            validate_inventory(reassigned)

    def test_pre_staging_task_nine_effects_have_dedicated_families(self):
        self.assertEqual(
            frozenset(
                {
                    "credential-and-iam-fixture",
                    "session-security-audit-and-guard",
                    "immutable-synthetic-risk-facts",
                    "browser-acceptance-and-screenshot-evidence",
                }
            ),
            ACTION_AUTHORIZATION.get("operator-browser-acceptance"),
        )
        self.assertEqual(
            frozenset({"rollout-evidence-report"}),
            ACTION_AUTHORIZATION.get("rollout-evidence-pack"),
        )


class TaskSevenAuthorizationContractTest(unittest.TestCase):
    def test_post_authorization_preflight_gates_are_ordered_and_fail_closed(self):
        plan = source_text(PLAN)
        sections = task_seven_preflight_sections(plan)
        capacity = sections["2a"]

        for required in (
            "df -h /",
            "docker system df",
            "available_kib",
            "15728640",
            "*[!0-9]*",
        ):
            self.assertIn(required, capacity)
        for forbidden in (
            "docker system prune",
            "docker image rm",
            "docker container rm",
            "docker volume rm",
            "docker builder prune",
        ):
            self.assertNotIn(forbidden, capacity)

        post_authorization = plan[plan.index("- [ ] **Step 2a:") :]
        positions = tuple(
            post_authorization.index("- [ ] **Step %s:" % step)
            for step in ("2a", "2b", "2c", "3")
        )
        self.assertEqual(tuple(sorted(positions)), positions)
        self.assertIn("npm --prefix requirements-site test", sections["2b"])
        self.assertIn(
            "Task 7 Steps 2a–13, Task 8 and Task 9 Steps 1–10",
            sections["authorization"],
        )

    def test_canonicalization_freshness_gate_precedes_mysql_mutation(self):
        canonicalization = task_seven_preflight_sections(source_text(PLAN))["2c"]
        for required in (
            "JDK 25",
            'JAVA_HOME="$(/usr/libexec/java_home -v 25)"',
            "paytech-build && ./mvnw clean verify",
            "paytech-build && ./mvnw clean install",
            "./mvnw -pl paytech-json-canonicalization -am clean verify",
            "./mvnw -pl paytech-json-canonicalization -am clean install",
            "node scripts/verify-java-foundation.mjs",
            "RiskDecisionCanonicalizerAutoTest,DecisionReleaseStaticValidatorAutoTest,RiskModuleBoundaryAutoTest",
            "-Dsurefire.failIfNoSpecifiedTests=false",
            "risk-decision-v1-golden-vectors.json",
            "9a5ad56d2edc300a1223afa1df22d88f0334f772a9ca2796500cfc22c30803e5",
            "scripts/pdmaner/risk-decision-engine-contract.test.mjs",
            "scripts/pdmaner/risk-compliance-initial-data.test.mjs",
        ):
            self.assertIn(required, canonicalization)
        self.assertNotIn(
            'JAVA_HOME="${JAVA_HOME:-',
            canonicalization,
            "a nonempty incompatible JAVA_HOME must not override the required JDK 25",
        )
        self.assertNotIn(
            "paytech-foundation && ./mvnw clean verify",
            canonicalization,
            "the full Foundation reactor requires Redis and mutates its test schema",
        )
        self.assertEqual(
            frozenset({"dependency-repository-and-cache"}),
            ACTION_AUTHORIZATION.get("canonicalization-freshness-and-vector-gate"),
        )

    def test_nginx_syntax_probe_is_networkless_without_compose_service_networks(self):
        plan = source_text(PLAN)
        authorization = task_seven_preflight_sections(plan)["authorization"]
        step_eight = plan.split(
            "- [ ] **Step 8: Validate the pinned Nginx configuration**", 1
        )[1].split("- [ ] **Step 9a:", 1)[0]
        step_script = step_eight.split("```bash", 1)[1].split("```", 1)[0]

        for required in (
            "nginx_probe_config='/Users/kenny/Desktop/Workspace/Code-MoR/"
            "test-environment/nginx/nginx.conf'",
            'test -f "$nginx_probe_config"',
            'test ! -L "$nginx_probe_config"',
            "docker run --rm --pull=never --network none --read-only",
            "--tmpfs /tmp:size=16m,mode=1777",
            "--cap-drop ALL",
            "--security-opt no-new-privileges:true",
            "--memory 64m",
            "--user nginx",
            'src=$nginx_probe_config,dst=/etc/nginx/nginx.conf,readonly',
            '--entrypoint nginx "$nginx_probe_image" -t',
        ):
            self.assertIn(required, step_eight)
        self.assertNotIn(
            "compose.sh run",
            step_script,
            "Compose run inherits nginx's fixed ingress address and races the live service",
        )
        self.assertEqual(1, step_script.count("--network"))
        self.assertNotIn("--network mor-test", step_script)
        for required in (
            "Nginx syntax-check container runs directly with `--network none`",
            "read-only bind-mounted reviewed configuration",
            "without Compose service-network inheritance",
        ):
            self.assertIn(required, authorization)

    def test_offline_gate_requires_and_directly_runs_this_inventory_first(self):
        plan = source_text(PLAN)
        step_one = plan.split(
            "- [ ] **Step 1: Run the complete offline/source gate**", 1
        )[1].split("- [ ] **Step 2:", 1)[0]
        required = (
            "test -f test-environment/scripts/test_risk_rule_configuration_authorization_inventory.py",
            "python3 test-environment/scripts/test_risk_rule_configuration_authorization_inventory.py",
            "python3 -m unittest discover -s test-environment/scripts -p 'test_*.py'",
        )
        positions = tuple(step_one.index(value) for value in required)
        self.assertEqual(tuple(sorted(positions)), positions)
        self.assertIn("does not run the Vinext build", step_one)
        self.assertIn(
            "full `npm --prefix requirements-site test` moves behind the exact authorization in Step 2b",
            step_one,
        )

    def test_task_six_tracks_shared_browser_tests_optimization_and_inventory_staging(self):
        plan = source_text(PLAN)
        task_six = plan.split("### Task 6:", 1)[1].split("### Task 7:", 1)[0]
        files = task_six.split("**Files:**", 1)[1].split("**Interfaces:**", 1)[0]
        task_six_paths = tuple(re.findall(r"`([^`]+)`", files))
        self.assertEqual(19, len(task_six_paths))
        self.assertEqual(19, len(frozenset(task_six_paths)))
        for required in (
            "- Modify: `test-environment/scripts/smoke.py`",
            "- Modify: `test-environment/scripts/test_smoke_stage_contract.py`",
            "- Modify: `test-environment/scripts/initialize.py`",
            "- Modify: `test-environment/scripts/test_risk_rule_configuration_catalog.py`",
        ):
            self.assertIn(required, task_six)

        for heading, next_heading in (
            (
                "- [ ] **Step 8: Run Route rollout RED**",
                "- [ ] **Step 9: Write deterministic recovery RED tests**",
            ),
            (
                "- [ ] **Step 12: Run Route rollout GREEN**",
                "- [ ] **Step 13: Implement the Snapshot evidence parser**",
            ),
        ):
            step = task_six.split(heading, 1)[1].split(next_heading, 1)[0]
            self.assertEqual(
                1,
                step.count(
                    "python3 test-environment/scripts/"
                    "test_smoke_stage_contract.py"
                ),
            )
            self.assertEqual(
                1,
                step.count(
                    "test_risk_rule_configuration_catalog.PersistedCatalogTest."
                    "test_mysql_timeout_is_bounded_validated_and_redacted"
                ),
            )
            self.assertEqual(
                1,
                step.count(
                    "test_risk_rule_configuration_catalog.PersistedCatalogTest."
                    "test_persisted_stage_reader_propagates_the_sql_timeout"
                ),
            )
            self.assertEqual(
                1,
                step.count(
                    "python3 test-environment/scripts/"
                    "test_risk_rule_configuration_rollout.py"
                ),
            )

        step_thirteen = plan.split(
            "- [ ] **Step 13: Stage only wholly owned new integration files**", 1
        )[1].split(
            "- [ ] **Step 14: Interactively stage every overlap file**", 1
        )[0]
        step_fourteen = plan.split(
            "- [ ] **Step 14: Interactively stage every overlap file**", 1
        )[1].split("- [ ] **Step 15: Verify the exact staged artifact**", 1)[0]
        for path in task_six_paths:
            self.assertIn(path, step_thirteen + step_fourteen)

        step_thirty_one = task_six.split(
            "- [ ] **Step 31: Run the complete offline GREEN gate**", 1
        )[1]
        for method in (
            "test_rate_limited_get_honors_one_strict_bounded_retry_after",
            "test_rate_limited_get_rejects_bad_retry_after_and_a_second_429",
            "test_rate_limited_get_extends_short_retry_after_for_layered_guard_refill",
            "test_rate_limited_get_caps_each_connection_and_checks_deadline_after_exchange",
            "test_rate_limited_get_has_one_monotonic_deadline_and_never_accepts_other_429",
            "test_real_main_rejects_both_navigation_authority_mismatches",
            "test_real_main_rejects_incomplete_duplicate_unstable_and_wrong_status_routes",
            "test_deadline_exhaustion_before_patch_does_not_mutate_and_after_patch_stops",
            "test_deadline_before_settlement_replay_keeps_the_unknown_route_diagnostic",
            "test_reconcile_deadline_after_first_committed_patch_never_touches_second_route",
            "test_409_requires_the_strict_current_problem_contract",
            "test_problem_shaped_200_is_never_misclassified_as_a_409",
        ):
            self.assertEqual(1, step_thirty_one.count(method))
        self.assertIn("python3 -O -m unittest", step_thirty_one)

    def test_authorization_names_every_file_and_mutation_category(self):
        plan = source_text(PLAN)
        authorization = task_seven_preflight_sections(plan)["authorization"]
        for path in PRIVATE_EFFECT_PATHS:
            self.assertIn(path, authorization)
        for base in PINNED_BUILD_BASES:
            self.assertIn(base, authorization)
        for required in (
            "configured Maven wrapper/plugin/dependency repository access",
            "configured npm registry access",
            "requirements-site/dist",
            ".vinext/fonts",
            "fonts.googleapis.com",
            "fonts.gstatic.com",
            "Docker build-cache writes",
            "mor-test-backend",
            "mor-test-ingress",
            "mor-test-web",
            "mor-test-redis-data",
            "mor-test-rabbitmq-data",
            "MySQL container ID",
            "Session",
            "security/audit",
            "Guard effect",
            "immutable synthetic Risk facts",
        ):
            self.assertIn(required, authorization)
        self.assertIn("No new MySQL volume", authorization)
        self.assertIn("No new MySQL volume, unpinned image pull", authorization)
        self.assertIn("remote Dockerfile `ADD`", authorization)
        self.assertIn("shared Java image has no `RUN`/`ADD` instruction", authorization)
        self.assertIn("Ubuntu APT/package-manager network access", authorization)

    def test_exact_platform_backup_target_is_part_of_the_file_inventory(self):
        self.assertIn(
            "test-environment/private/backups/"
            "mor_platform-before-risk-operations-20260907.sql",
            PRIVATE_EFFECT_PATHS,
        )

    def test_rollout_authorization_scope_excludes_staging_and_commit(self):
        plan = source_text(PLAN)
        authorization = task_seven_preflight_sections(plan)["authorization"]
        for required in (
            "operator-facing browser acceptance",
            "sanitized Request ID/screenshot evidence",
            "ignored Task 9 rollout evidence pack",
            "Task 7 Steps 2a–13, Task 8 and Task 9 Steps 1–10",
            "Git index/object/ref",
            "risk-compose",
            "separate later authorization",
        ):
            self.assertIn(required, authorization)


class RecursiveSourceInventoryTest(unittest.TestCase):
    def test_only_declared_repository_text_is_read(self):
        self.assertEqual(len(READ_ONLY_SOURCES), len(frozenset(READ_ONLY_SOURCES)))
        for path in READ_ONLY_SOURCES:
            self.assertTrue(path.is_file(), path)
            self.assertNotIn("private", path.parts)

    def test_build_deploy_compose_and_python_adapter_chain_is_explicit(self):
        build = source_text(BUILD)
        deploy = source_text(DEPLOY)
        compose_wrapper = source_text(COMPOSE_WRAPPER)
        for required in (
            "./mvnw -DskipTests clean install",
            "npm ci && npm run build",
            'health_probe_output="$environment_dir/artifacts/health-probe"',
            '"$environment_dir/java/LocalSnapshotEvidenceFetch.java"',
            '"$environment_dir/compose.sh" build',
        ):
            self.assertIn(required, build)
        self.assertIn(
            '"$environment_dir/compose.sh" build \\\n'
            "  nacos risk-service platform-support-service operations-console-bff "
            "api-gateway operations-console-web",
            build,
        )
        for required in (
            'build_script="$environment_dir/scripts/build.sh"',
            'compose_script="$environment_dir/compose.sh"',
            'smoke_script="$environment_dir/scripts/smoke.py"',
            'deploy_lock="$environment_dir/private/deploy.lock"',
            'phase_checkpoint="$environment_dir/private/risk-bff-web-phase.ready"',
        ):
            self.assertIn(required, deploy)
        self.assertIn("exec docker compose --project-name mor-test", compose_wrapper)
        self.assertNotIn("risk_rule_configuration_smoke.py", deploy)
        self.assertNotIn("risk_rule_configuration_rollout.py", deploy)
        self.assertNotIn("risk_rule_configuration_recovery.py", deploy)
        self.assertNotIn("--apply-risk", deploy)

    def test_snapshot_wait_uses_the_pinned_bounded_jre_fetcher(self):
        build = source_text(BUILD)
        waiter = source_text(SNAPSHOT_WAIT)
        fetcher = source_text(LOCAL_SNAPSHOT_EVIDENCE_FETCH)
        readme = source_text(README)
        self.assertIn(
            '"$environment_dir/java/LocalSnapshotEvidenceFetch.java"', build
        )
        for argument in (
            '"api-gateway"',
            '"java"',
            '"-cp"',
            '"/app/health-probe"',
            '"LocalSnapshotEvidenceFetch"',
        ):
            self.assertIn(argument, waiter)
        self.assertNotIn('"curl"', waiter)
        self.assertIn("stderr=subprocess.DEVNULL", waiter)
        self.assertIn("MAX_RESPONSE_BODY_BYTES", waiter)
        for endpoint in (
            "http://127.0.0.1:19080/actuator/prometheus",
            "http://platform-support-service:18106/"
            "platform-support/gateway-config/v1/public-snapshot",
        ):
            self.assertIn(endpoint, fetcher)
        self.assertIn("HttpResponse.BodyHandlers.limiting(", fetcher)
        self.assertIn("MAX_BODY_BYTES = 1_048_576", fetcher)
        for required in (
            "不依赖 Java 运行镜像中并不存在的 `curl`",
            "Java helper 与 Python 调用方分别只允许",
            "同时限制为 1 MiB",
            "不会为此恢复包安装、`RUN` 或远程 `ADD`",
        ):
            self.assertIn(required, readme)

    def test_every_compose_referenced_dockerfile_is_offline_inspected(self):
        base = source_text(COMPOSE_BASE)
        applications = source_text(COMPOSE_APPLICATIONS)
        dockerfiles = {
            "java": TEST_ENVIRONMENT / "java/Dockerfile",
            "nacos": TEST_ENVIRONMENT / "nacos/Dockerfile",
            "vault": TEST_ENVIRONMENT / "vault/Dockerfile",
            "web": ROOT / "operations-console-web/Dockerfile",
        }
        self.assertEqual(
            ("./nacos",),
            tuple(re.findall(r"(?m)^    build: (\S+)$", base)),
        )
        self.assertEqual(
            6,
            len(re.findall(r"(?m)^    build:(?:\s+\S+)?$", applications)),
        )
        self.assertEqual(
            ("../../vault/Dockerfile",) + ("../java/Dockerfile",) * 4,
            tuple(re.findall(r"(?m)^      dockerfile: (\S+)$", applications)),
        )
        self.assertEqual(
            ("../operations-console-web",),
            tuple(re.findall(r"(?m)^    build: (\S+)$", applications)),
        )
        texts = {name: source_text(path) for name, path in dockerfiles.items()}
        reject_online_dockerfile_package_steps(texts)
        for base_image in PINNED_BUILD_BASES:
            self.assertTrue(
                any(base_image in value for value in texts.values()), base_image
            )
        self.assertIn("FROM scratch", texts["vault"])
        actual_from = []
        for content in texts.values():
            actual_from.extend(re.findall(r"(?m)^FROM\s+(\S+)", content))
        self.assertEqual(
            frozenset((*PINNED_BUILD_BASES, "scratch")),
            frozenset(actual_from),
        )
        self.assertEqual(4, len(actual_from))

    def test_online_package_breaker_is_rejected(self):
        breakers = (
            (
                "java",
                "FROM example\n"
                "RUN echo ready && \\\n"
                "  apt-get update\n",
            ),
            ("web", "FROM example\nADD https://example.test/archive.tgz /app/\n"),
            (
                "web",
                'FROM example\nADD ["https://example.test/archive.tgz", "/app/"]\n',
            ),
            (
                "java",
                "FROM example\n"
                "COPY --from=example.invalid/unpinned:latest /payload /payload\n",
            ),
            ("java", "FROM example\nRUN echo no-network-needed\n"),
        )
        for name, dockerfile in breakers:
            with self.subTest(name=name, dockerfile=dockerfile), self.assertRaises(
                AssertionError
            ):
                reject_online_dockerfile_package_steps({name: dockerfile})

    def test_compose_effects_and_mysql_identity_are_closed(self):
        base = source_text(COMPOSE_BASE)
        applications = source_text(COMPOSE_APPLICATIONS)
        deploy = source_text(DEPLOY)
        for value in (
            "name: mor-test-mysql-data",
            "name: mor-test-redis-data",
            "name: mor-test-rabbitmq-data",
            "name: mor-test-backend",
            "name: mor-test-ingress",
            "name: mor-test-web",
        ):
            self.assertIn(value, base)
        self.assertEqual(4, applications.count("LocalHttpHealthProbe"))
        validate_phase_one_infrastructure_contract(deploy)
        with self.assertRaises(AssertionError):
            validate_phase_one_infrastructure_contract(
                deploy.replace(" --no-recreate", "", 1)
            )
        unsafe_with_comment_lure = deploy.replace(
            " --no-recreate", " --force-recreate", 1
        ) + (
            "\n# harmless-looking lure: \"$compose_script\" up -d --no-recreate "
            "--no-build --pull never --wait --wait-timeout \"$wait_timeout\" "
            "\"${protected_infrastructure_services[@]}\"\n"
        )
        with self.assertRaises(AssertionError):
            validate_phase_one_infrastructure_contract(unsafe_with_comment_lure)

        unsafe_with_indirect_command = deploy + (
            '\ncompose_alias="$compose_script"\n'
            'unsafe_argv=(up -d --force-recreate mysql)\n'
            '"$compose_alias" "${unsafe_argv[@]}"\n'
        )
        with self.assertRaises(AssertionError):
            validate_phase_one_infrastructure_contract(unsafe_with_indirect_command)

        unsafe_heredoc_execution = deploy + (
            '\nbash <<EOF\n'
            '"$compose_script" up -d --force-recreate mysql\n'
            'EOF\n'
        )
        with self.assertRaises(AssertionError):
            validate_phase_one_infrastructure_contract(unsafe_heredoc_execution)

        unsafe_with_duplicate_dynamic_service = deploy + (
            '\nservice_name=mysql\n'
            '"$compose_script" up -d --no-deps --no-build --pull never --wait '
            '--wait-timeout "$wait_timeout" "$service_name"\n'
        )
        with self.assertRaises(AssertionError):
            validate_phase_one_infrastructure_contract(
                unsafe_with_duplicate_dynamic_service
            )

    def test_reviewed_source_manifest_rejects_obfuscated_command_drift(self):
        validator = globals().get("validate_reviewed_source_manifest")
        self.assertIsNotNone(
            validator,
            "the execution inventory must pin every reviewed executable source",
        )
        originals = {
            DEPLOY: source_text(DEPLOY),
            COMPOSE_WRAPPER: source_text(COMPOSE_WRAPPER),
        }
        expected = {
            path: hashlib.sha256(content.encode("utf-8")).hexdigest()
            for path, content in originals.items()
        }
        validator(originals, expected)
        obfuscated = dict(originals)
        obfuscated[DEPLOY] += (
            '\ncompose_alias="${environment_dir}/com""pose.sh"\n'
            'unsafe_flag="--force""-recreate"\n'
            '"$compose_alias" up -d "$unsafe_flag" mysql\n'
        )
        with self.assertRaises(AssertionError):
            validator(obfuscated, expected)

    def test_every_reviewed_rollout_source_matches_the_pinned_manifest(self):
        paths = globals().get("REVIEWED_SOURCE_PATHS")
        digests = globals().get("REVIEWED_SOURCE_DIGESTS")
        self.assertIsNotNone(paths, "reviewed rollout source paths must be explicit")
        self.assertIsNotNone(digests, "reviewed rollout source digests must be pinned")
        self.assertEqual(len(paths), len(frozenset(paths)))
        self.assertIn(
            TEST_ENVIRONMENT / "java/LocalHttpHealthProbe.java",
            paths,
            "the Java source compiled and executed by the rollout must be pinned",
        )
        self.assertIn(
            TEST_ENVIRONMENT / "java/LocalSnapshotEvidenceFetch.java",
            paths,
            "the Snapshot body fetcher compiled and executed by the rollout must be pinned",
        )
        sources = {path: source_text(path) for path in paths}
        validate_reviewed_source_manifest(sources, digests)

    def test_requirements_build_font_and_cache_effects_are_not_hidden(self):
        package = source_text(REQUIREMENTS_PACKAGE)
        layout = source_text(REQUIREMENTS_LAYOUT)
        plan = source_text(PLAN)
        validate_requirements_package_contract(package)
        self.assertRegex(package, r'"test"\s*:\s*"npm run build && node --test')
        self.assertIn("vinext build", package)
        self.assertIn('from "next/font/google"', layout)
        for effect in (
            "requirements-site/dist",
            ".vinext/fonts",
            "fonts.googleapis.com",
            "fonts.gstatic.com",
        ):
            self.assertIn(effect, plan)

    def test_requirements_gate_rejects_lifecycle_hook_commands(self):
        validator = globals().get("validate_requirements_package_contract")
        self.assertIsNotNone(
            validator,
            "the source-only gate must validate the complete npm lifecycle entrypoint",
        )
        package = json.loads(source_text(REQUIREMENTS_PACKAGE))
        package["scripts"]["pretest"] = "curl https://example.invalid/bootstrap"
        with self.assertRaises(AssertionError):
            validator(json.dumps(package))

    def test_sql_session_audit_guard_credentials_and_fact_effects_are_visible(self):
        initializer = source_text(INITIALIZER)
        default_smoke = source_text(DEFAULT_SMOKE)
        risk_smoke = source_text(RISK_SMOKE)
        rollout = source_text(ROLLOUT)
        recovery = source_text(RECOVERY)
        waiter = source_text(SNAPSHOT_WAIT)
        for action in (
            "--apply-risk-sql",
            "--apply-risk-operations-sql",
            "--enable-risk-operations-resources",
            "--disable-risk-operations-resources",
        ):
            self.assertIn(action, initializer)
        for action in SMOKE_ACTIONS:
            self.assertIn(action, risk_smoke)
        for action in ROLLOUT_ACTIONS:
            self.assertIn(action, rollout)
        self.assertIn("--recover-route", recovery)
        self.assertIn("compose.sh", waiter)
        self.assertIn("/auth/v1/login", default_smoke)
        self.assertIn("/auth/v1/logout", default_smoke)
        self.assertIn("private/access.md", default_smoke)
        self.assertIn("risk-rule-configuration-smoke.json", risk_smoke)
        self.assertIn("/auth/v1/logout", rollout)
        for route_code in ROUTE_CODES:
            self.assertIn(route_code, rollout)


class OperatorDocumentationContractTest(unittest.TestCase):
    def test_all_twelve_opt_in_actions_are_standalone_and_fail_closed(self):
        readme = source_text(README)
        section = readme.split(
            "## Risk Operations 显式动作手册（Task 6）", 1
        )[1].split("## 时间范围下拉组件", 1)[0]
        blocks = {
            match.group(1): match.group(2)
            for match in re.finditer(
                r"(?ms)^### ([^\n]+)\n(.*?)(?=^### |\Z)", section
            )
        }
        self.assertEqual(frozenset(DOCUMENTED_ACTIONS), frozenset(blocks))
        for heading, action in DOCUMENTED_ACTIONS.items():
            block = blocks[heading]
            self.assertIn(action, block)
            for field in (
                "前置阶段",
                "写入范围",
                "成功证据",
                "失败停点",
                "重跑/对账",
            ):
                self.assertIn(field, block, "%s omitted %s" % (heading, field))
        for required in (
            "不会自动",
            "Session、安全审计和 Guard",
            "不可变 Risk synthetic facts",
            "RESOURCES_EXPOSED",
            "UNKNOWN_REQUIRES_RECONCILIATION",
        ):
            self.assertIn(required, section)

    def test_route_observation_documents_the_bounded_get_only_contract(self):
        readme = source_text(README)
        section = readme.split(
            "## Risk Operations 显式动作手册（Task 6）", 1
        )[1].split("### Rollout 1：", 1)[0]
        for required in (
            "固定为共 14 次 GET",
            "4 页/36 条 Route 列表",
            "9 条 Risk Route Detail",
            "1 次 Operations\nNavigation",
            "RFC 9457 `429 GATEWAY_RATE_LIMITED`",
            "`1..6` 秒",
            "最多重试一次",
            "ingress IP Guard",
            "authenticated-principal",
            "`max(Retry-After, 6 秒)`",
            "最慢的账号维度补充周期",
            "无 Body、无 Idempotency-Key 的 GET",
            "绝不用于 PATCH/POST",
            "只读检查 90 秒、单条启用/禁用 180 秒、完整回退 720 秒",
            "不在 post-read 或逐 Route\n重置",
            "`min(20, remaining)`",
            "数据库权威读取也使用当时的 remaining 作为子进程 timeout",
            "返回后再次检查\ndeadline",
            "同一个持久 rollout lock 内",
            "PATCH 前到期不写，PATCH 后到期要求显式对账",
            "不会继续下一条 Route",
        ):
            self.assertIn(required, section)

    def test_smoke_actor_and_fixture_prerequisites_match_runtime(self):
        readme = source_text(README)
        section = readme.split(
            "## Risk Operations 显式动作手册（Task 6）", 1
        )[1].split("## 时间范围下拉组件", 1)[0]
        blocks = {
            match.group(1): match.group(2)
            for match in re.finditer(
                r"(?ms)^### ([^\n]+)\n(.*?)(?=^### |\Z)", section
            )
        }
        expected = {
            "Smoke 1：准备授权夹具": (
                "已准备好 36 条 Route",
                "`BASELINE` 至 `RESOURCES_EXPOSED`",
            ),
            "Smoke 3：探测 Gateway 实际运行阶段": (
                "`BASELINE` 只使用 repository test Platform Administrator，不要求 synthetic identity",
                "`AUTHORING_READ_ACTIVE` 至 `ALL_ROUTES_ACTIVE` 使用管理员作为 authorized actor 和 synthetic denied actor",
                "`RESOURCES_EXPOSED` 由 synthetic authorized/denied",
            ),
            "Smoke 4：聚合验证五条读 Route": (
                "两个 synthetic 夹具均已准备且可重新发现",
                "repository test Platform Administrator 作为 authorized actor",
                "synthetic denied actor",
            ),
            "Smoke 5：验证刚启用的一条 Route": (
                "两个 synthetic 夹具均已准备且可重新发现",
                "repository test Platform Administrator 作为 authorized actor",
                "synthetic denied actor",
                "不可变 Risk facts 的审计 actor 是该管理员",
            ),
        }
        for heading, phrases in expected.items():
            for phrase in phrases:
                self.assertIn(phrase, blocks[heading], "%s omitted %s" % (heading, phrase))

    def test_smoke_fixture_documents_bounded_login_rate_recovery(self):
        readme = source_text(README)
        block = readme.split("### Smoke 1：准备授权夹具", 1)[1].split(
            "### Smoke 2：", 1
        )[0]
        normalized = re.sub(r"\s+", " ", block)
        for required in (
            "管理员和 synthetic 账号登录共享一个 90 秒 monotonic deadline",
            "中间的权威观察",
            "不会重置它",
            "RFC 9457 `429 GATEWAY_RATE_LIMITED`",
            "不超过 60 秒的 `Retry-After`",
            "`max(Retry-After, 6 秒)`",
            "多个不同登录可能分别等待",
            "`401` 是正常的账号发现结果",
            "不重试",
            "`GATEWAY_CONCURRENCY_LIMITED`",
            "第二次 429",
            "传输结果未知",
        ):
            self.assertIn(required, normalized)


if __name__ == "__main__":
    unittest.main()
