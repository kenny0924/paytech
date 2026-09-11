"""Behavior contracts for the phased mor-test deployment entrypoint."""

from collections import Counter
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import tempfile
import unittest


TEST_ENVIRONMENT = Path(__file__).resolve().parents[1]
DEPLOY_SCRIPT = TEST_ENVIRONMENT / "deploy.sh"
COMPOSE_APPLICATIONS = TEST_ENVIRONMENT / "compose.applications.yaml"
BUILD_SCRIPT = TEST_ENVIRONMENT / "scripts/build.sh"
README = TEST_ENVIRONMENT / "README.md"

FAKE_IMAGE_IDS = {
    "mor-test/api-gateway:development": "sha256:" + "a" * 64,
    "mor-test/nacos:3.1.2": "sha256:" + "b" * 64,
    "mor-test/operations-console-bff:development": "sha256:" + "c" * 64,
    "mor-test/operations-console-web:development": "sha256:" + "d" * 64,
    "mor-test/platform-support-service:development": "sha256:" + "e" * 64,
    "mor-test/risk-service:development": "sha256:" + "f" * 64,
}
EXPECTED_PHASE_CHECKPOINT = (
    "mor-test/api-gateway:development sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\n"
    "mor-test/nacos:3.1.2 sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\n"
    "mor-test/operations-console-bff:development sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc\n"
    "mor-test/operations-console-web:development sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd\n"
    "mor-test/platform-support-service:development sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee\n"
    "mor-test/risk-service:development sha256:ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff\n"
)
MYSQL_CONTAINER_ID = "1" * 64
MYSQL_CONTAINER_QUERY = (
    "docker ps -aq --no-trunc "
    "--filter label=com.docker.compose.project=mor-test "
    "--filter label=com.docker.compose.service=mysql"
)


def logical_shell_commands(markdown):
    """Collapse Markdown shell continuations into commands an operator can run."""
    commands = []
    continuation = ""
    for raw_line in markdown.splitlines():
        line = raw_line.strip()
        if line.endswith("\\"):
            continuation += f"{line[:-1].rstrip()} "
            continue
        if continuation:
            line = f"{continuation}{line}"
            continuation = ""
        commands.append(line)
    if continuation:
        commands.append(continuation.rstrip())
    return commands


class DeployScriptTest(unittest.TestCase):
    def setUp(self):
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary_directory.name) / "test-environment"
        self.root.mkdir()
        self.log = self.root / "calls.log"

    def tearDown(self):
        self.temporary_directory.cleanup()

    def prepare_environment(
        self,
        build_exit_code=0,
        compose_fail_service="",
        compose_exit_code=23,
        smoke_exit_code=0,
    ):
        shutil.copy2(DEPLOY_SCRIPT, self.root / "deploy.sh")
        test_environment = self.root / ".env.test"
        test_environment.write_text("TEST_VALUE=prepared\n", encoding="utf-8")
        test_environment.chmod(0o600)
        private = self.root / "private"
        private.mkdir(mode=0o700)
        private.chmod(0o700)
        for path, content in (
            (private / "platform.env", "TEST_VALUE=prepared\n"),
            (private / "operations-material.json", "{}\n"),
            (private / "administrator-password-changed", "ready\n"),
        ):
            path.write_text(content, encoding="utf-8")
            path.chmod(0o600)
        self.write_checkpoint()

        scripts = self.root / "scripts"
        scripts.mkdir()
        self.write_executable(
            scripts / "build.sh",
            f'printf "build\\n" >> "$DEPLOY_TEST_LOG"\nexit {build_exit_code}\n',
        )
        (scripts / "smoke.py").write_text("# test double\n", encoding="utf-8")
        self.write_executable(
            self.root / "compose.sh",
            'printf "compose %s\\n" "$*" >> "$DEPLOY_TEST_LOG"\n'
            'if [[ "${1:-}" == up && -n "${DEPLOY_TEST_FAIL_SERVICE:-}" ]]; then\n'
            '  for argument in "$@"; do\n'
            '    if [[ "$argument" == "$DEPLOY_TEST_FAIL_SERVICE" ]]; then\n'
            '      exit "$DEPLOY_TEST_COMPOSE_EXIT_CODE"\n'
            '    fi\n'
            '  done\n'
            'fi\n',
        )

        fake_bin = self.root / "fake-bin"
        fake_bin.mkdir()
        image_cases = "\n".join(
            f'    {name}) image_id="{image_id}" ;;'
            for name, image_id in FAKE_IMAGE_IDS.items()
        )
        self.write_executable(
            fake_bin / "docker",
            'printf "docker %s\\n" "$*" >> "$DEPLOY_TEST_LOG"\n'
            'if [[ "${1:-} ${2:-}" == "context show" ]]; then\n'
            '  printf "desktop-linux\\n"\n'
            'elif [[ "${1:-} ${2:-}" == "context inspect" ]]; then\n'
            '  printf "unix:///tmp/docker.sock\\n"\n'
            'elif [[ "$*" == "ps -aq --no-trunc '
            '--filter label=com.docker.compose.project=mor-test '
            '--filter label=com.docker.compose.service=mysql" ]]; then\n'
            '  query_count="$(grep -Fxc "docker ps -aq --no-trunc '
            '--filter label=com.docker.compose.project=mor-test '
            '--filter label=com.docker.compose.service=mysql" '
            '"$DEPLOY_TEST_LOG")"\n'
            '  if [[ "$query_count" == 1 ]]; then\n'
            '    printf "%s\\n" "$DEPLOY_TEST_MYSQL_CONTAINER_ID_BEFORE"\n'
            '  else\n'
            '    printf "%s\\n" "$DEPLOY_TEST_MYSQL_CONTAINER_ID_AFTER"\n'
            '  fi\n'
            'elif [[ "${1:-} ${2:-} ${3:-}" == "image inspect --format" ]]; then\n'
            '  image_name="${5:-}"\n'
            '  if [[ "$image_name" == "${DEPLOY_TEST_IMAGE_INSPECT_FAIL:-}" ]]; then\n'
            '    exit 31\n'
            '  fi\n'
            '  case "$image_name" in\n'
            f"{image_cases}\n"
            '    *) exit 32 ;;\n'
            '  esac\n'
            '  if [[ "$image_name" == "${DEPLOY_TEST_CHANGED_IMAGE:-}" ]]; then\n'
            '    image_id="${DEPLOY_TEST_CHANGED_IMAGE_ID}"\n'
            '  fi\n'
            '  printf "%s\\n" "$image_id"\n'
            'elif [[ "${1:-} ${2:-}" == "image inspect" ]]; then\n'
            '  if [[ "${3:-}" == "${DEPLOY_TEST_IMAGE_INSPECT_FAIL:-}" ]]; then\n'
            '    exit 31\n'
            '  fi\n'
            "fi\n",
        )
        self.write_executable(
            fake_bin / "id",
            'if [[ "${1:-}" == "-u" ]]; then\n'
            '  printf "%s\\n" "$DEPLOY_TEST_CURRENT_UID"\n'
            'else\n'
            '  exit 2\n'
            'fi\n',
        )
        self.write_executable(
            fake_bin / "mv",
            'printf "fs mv %s\\n" "$*" >> "$DEPLOY_TEST_LOG"\n'
            'exec /bin/mv "$@"\n',
        )
        self.write_executable(
            fake_bin / "mkdir",
            'printf "fs mkdir %s\\n" "$*" >> "$DEPLOY_TEST_LOG"\n'
            'exec /bin/mkdir "$@"\n',
        )
        self.write_executable(
            fake_bin / "rm",
            'printf "fs rm %s\\n" "$*" >> "$DEPLOY_TEST_LOG"\n'
            'exec /bin/rm "$@"\n',
        )
        self.write_executable(
            fake_bin / "python3",
            'printf "python3 %s\\n" "$*" >> "$DEPLOY_TEST_LOG"\n'
            f"exit {smoke_exit_code}\n",
        )
        self.write_executable(fake_bin / "node", "exit 0\n")
        self.write_executable(fake_bin / "npm", "exit 0\n")
        fake_jdk = self.root / "fake-jdk"
        (fake_jdk / "bin").mkdir(parents=True)
        self.write_executable(
            fake_jdk / "bin/java",
            'printf \'openjdk version "25" 2025-09-16 LTS\\n\' >&2\n',
        )
        self.write_executable(
            fake_bin / "java",
            'if [[ "$*" == *"-XshowSettings:properties"* ]]; then\n'
            '  printf "    java.home = %s\\n" "$FAKE_JAVA_HOME" >&2\n'
            "else\n"
            '  printf \'openjdk version "25" 2025-09-16 LTS\\n\' >&2\n'
            "fi\n",
        )

        environment = os.environ.copy()
        environment["PATH"] = f"{fake_bin}:{environment['PATH']}"
        environment["JAVA_HOME"] = str(fake_jdk)
        environment["FAKE_JAVA_HOME"] = str(fake_jdk)
        environment["DEPLOY_TEST_LOG"] = str(self.log)
        environment["DEPLOY_TEST_FAIL_SERVICE"] = compose_fail_service
        environment["DEPLOY_TEST_COMPOSE_EXIT_CODE"] = str(compose_exit_code)
        environment["DEPLOY_TEST_CURRENT_UID"] = str(os.getuid())
        environment["DEPLOY_TEST_MYSQL_CONTAINER_ID_BEFORE"] = MYSQL_CONTAINER_ID
        environment["DEPLOY_TEST_MYSQL_CONTAINER_ID_AFTER"] = MYSQL_CONTAINER_ID
        return environment

    @staticmethod
    def write_executable(path, body):
        path.write_text(f"#!/usr/bin/env bash\nset -euo pipefail\n{body}", encoding="utf-8")
        path.chmod(0o755)

    def run_deploy(self, environment, *arguments):
        return subprocess.run(
            ["/bin/bash", str(self.root / "deploy.sh"), *arguments],
            capture_output=True,
            text=True,
            env=environment,
            check=False,
        )

    def calls(self):
        return self.log.read_text(encoding="utf-8").splitlines()

    @property
    def checkpoint(self):
        return self.root / "private/risk-bff-web-phase.ready"

    def write_checkpoint(self, content=EXPECTED_PHASE_CHECKPOINT, mode=0o600):
        self.checkpoint.write_text(content, encoding="utf-8")
        self.checkpoint.chmod(mode)

    def assert_phase_two_rejected_before_replacement_or_smoke(self, environment):
        result = self.run_deploy(
            environment,
            "--skip-build",
            "--phase",
            "platform-gateway-nginx",
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("checkpoint", result.stderr)
        calls = self.calls()
        self.assertFalse(any(call.startswith("compose up ") for call in calls))
        self.assertFalse(any(call.startswith("python3 ") for call in calls))
        return result

    def test_risk_bff_web_phase_builds_then_replaces_only_its_services_in_order(self):
        environment = self.prepare_environment()

        result = self.run_deploy(environment, "--phase", "risk-bff-web")

        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls()
        deployment_calls = [
            call
            for call in calls
            if not call.startswith("fs ")
            and not call.startswith("docker image inspect --format ")
        ]
        self.assertEqual(
            [
                "docker compose version",
                "docker context show",
                "docker context inspect --format {{.Endpoints.docker.Host}} desktop-linux",
                "docker info",
                "compose config --quiet",
                "build",
                MYSQL_CONTAINER_QUERY,
                "compose up -d --no-recreate --no-build --pull never --wait "
                "--wait-timeout 240 mysql redis rabbitmq",
                "compose up -d --no-deps --no-build --pull never --wait "
                "--wait-timeout 240 nacos",
                MYSQL_CONTAINER_QUERY,
                "compose up -d --no-deps --no-build --pull never --force-recreate --wait --wait-timeout 240 "
                "risk-service",
                "compose up -d --no-deps --no-build --pull never --force-recreate --wait --wait-timeout 240 "
                "operations-console-bff",
                "compose up -d --no-deps --no-build --pull never --force-recreate --wait --wait-timeout 240 "
                "operations-console-web",
                "compose ps",
            ],
            deployment_calls,
        )
        self.assertEqual(
            Counter(FAKE_IMAGE_IDS.keys()),
            Counter(
                call.rsplit(" ", 1)[-1]
                for call in calls
                if call.startswith("docker image inspect --format {{.Id}} ")
            ),
        )
        self.assertNotIn("[3/4] 验证登录", result.stdout)
        self.assertIn("Risk/BFF/Web 阶段成功", result.stdout)
        self.assertIn("Vault 未在本次更新或验收", result.stdout)
        self.assertFalse((self.root / "private/deploy.lock").exists())

    def test_phase_one_missing_mysql_container_fails_before_any_container_update(self):
        environment = self.prepare_environment()
        environment["DEPLOY_TEST_MYSQL_CONTAINER_ID_BEFORE"] = ""

        result = self.run_deploy(
            environment, "--skip-build", "--phase", "risk-bff-web"
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("MySQL", result.stderr)
        calls = self.calls()
        self.assertEqual(1, calls.count(MYSQL_CONTAINER_QUERY))
        self.assertFalse(any(call.startswith("compose up ") for call in calls))
        self.assertFalse(any(call.startswith("python3 ") for call in calls))
        self.assertFalse(self.checkpoint.exists())
        self.assertFalse((self.root / "private/deploy.lock").exists())

    def test_phase_one_mysql_replacement_fails_before_application_update(self):
        environment = self.prepare_environment()
        environment["DEPLOY_TEST_MYSQL_CONTAINER_ID_AFTER"] = "2" * 64

        result = self.run_deploy(
            environment, "--skip-build", "--phase", "risk-bff-web"
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("MySQL", result.stderr)
        self.assertIn("替换", result.stderr)
        calls = self.calls()
        self.assertEqual(2, calls.count(MYSQL_CONTAINER_QUERY))
        self.assertEqual(
            [
                "compose up -d --no-recreate --no-build --pull never --wait "
                "--wait-timeout 240 mysql redis rabbitmq",
                "compose up -d --no-deps --no-build --pull never --wait "
                "--wait-timeout 240 nacos",
            ],
            [call for call in calls if call.startswith("compose up ")],
        )
        self.assertFalse(any(call.startswith("python3 ") for call in calls))
        self.assertFalse(self.checkpoint.exists())
        self.assertFalse((self.root / "private/deploy.lock").exists())

    def test_platform_gateway_nginx_phase_replaces_only_its_services_then_smokes_once(self):
        environment = self.prepare_environment()

        result = self.run_deploy(
            environment,
            "--skip-build",
            "--phase",
            "platform-gateway-nginx",
            "--timeout",
            "90",
        )

        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls()
        self.assertNotIn("build", calls)
        expected_images = Counter(
            {
                "mor-test/nacos:3.1.2": 1,
                "mor-test/risk-service:development": 1,
                "mor-test/platform-support-service:development": 1,
                "mor-test/operations-console-bff:development": 1,
                "mor-test/api-gateway:development": 1,
                "mor-test/operations-console-web:development": 1,
            }
        )
        inspected_images = Counter(
            call.rsplit(" ", 1)[-1]
            for call in calls
            if call.startswith("docker image inspect --format {{.Id}} ")
        )
        self.assertEqual(expected_images, inspected_images)
        self.assertNotIn("mor-test/vault-service:development", inspected_images)
        replacement_calls = [call for call in calls if call.startswith("compose up ")]
        self.assertEqual(
            [
                "compose up -d --no-deps --no-build --pull never --force-recreate --wait --wait-timeout 90 "
                "platform-support-service",
                "compose up -d --no-deps --no-build --pull never --force-recreate --wait --wait-timeout 90 "
                "nginx",
                "compose up -d --no-deps --no-build --pull never --force-recreate --wait --wait-timeout 90 "
                "api-gateway",
            ],
            replacement_calls,
        )
        lock_call = f"fs mkdir {self.root / 'private/deploy.lock'}"
        checkpoint_checks = [
            index
            for index, call in enumerate(calls)
            if call.startswith("docker image inspect --format {{.Id}} ")
        ]
        self.assertEqual(6, len(checkpoint_checks))
        self.assertLess(calls.index(lock_call), checkpoint_checks[0])
        self.assertLess(checkpoint_checks[-1], calls.index(replacement_calls[0]))
        smoke_calls = [call for call in calls if call.startswith("python3 ")]
        self.assertEqual(1, len(smoke_calls))
        self.assertTrue(smoke_calls[0].endswith("/scripts/smoke.py"))
        self.assertGreater(calls.index(smoke_calls[0]), calls.index(replacement_calls[-1]))
        self.assertEqual("compose ps", calls[-1])
        self.assertIn("Platform/Gateway/Nginx 阶段与公开烟测成功", result.stdout)

    def test_missing_phase_is_rejected_before_every_preflight_side_effect(self):
        environment = self.prepare_environment()

        result = self.run_deploy(environment)

        self.assertNotEqual(0, result.returncode)
        self.assertIn("--phase", result.stderr)
        self.assertFalse(self.log.exists())

    def test_unknown_phase_is_rejected_before_every_preflight_side_effect(self):
        environment = self.prepare_environment()

        result = self.run_deploy(environment, "--phase", "unknown")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("risk-bff-web|platform-gateway-nginx", result.stderr)
        self.assertFalse(self.log.exists())

    def test_help_is_the_only_action_that_does_not_require_phase(self):
        environment = self.prepare_environment()

        result = self.run_deploy(environment, "--help")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("--phase risk-bff-web|platform-gateway-nginx", result.stdout)
        self.assertIn("risk-bff-web-phase.ready", result.stdout)
        self.assertFalse(self.log.exists())

    def test_check_only_requires_phase_and_runs_preflight_without_mutation(self):
        environment = self.prepare_environment()

        result = self.run_deploy(
            environment, "--check-only", "--phase", "risk-bff-web"
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            [
                "docker compose version",
                "docker context show",
                "docker context inspect --format {{.Endpoints.docker.Host}} desktop-linux",
                "docker info",
                "compose config --quiet",
            ],
            self.calls(),
        )
        self.assertIn("部署前检查通过", result.stdout)

    def test_check_only_never_creates_or_removes_phase_checkpoint(self):
        environment = self.prepare_environment()
        original_stat = self.checkpoint.lstat()
        original_content = self.checkpoint.read_bytes()

        phase_one = self.run_deploy(
            environment, "--check-only", "--phase", "risk-bff-web"
        )
        phase_two = self.run_deploy(
            environment, "--check-only", "--phase", "platform-gateway-nginx"
        )

        self.assertEqual(0, phase_one.returncode, phase_one.stderr)
        self.assertEqual(0, phase_two.returncode, phase_two.stderr)
        self.assertFalse(any(call.startswith("fs mkdir ") for call in self.calls()))
        after_stat = self.checkpoint.lstat()
        self.assertEqual(original_stat.st_ino, after_stat.st_ino)
        self.assertEqual(original_content, self.checkpoint.read_bytes())

        self.checkpoint.unlink()
        without_checkpoint = self.run_deploy(
            environment, "--check-only", "--phase", "risk-bff-web"
        )
        self.assertEqual(0, without_checkpoint.returncode, without_checkpoint.stderr)
        self.assertFalse(self.checkpoint.exists())

    def test_non_skip_phase_two_revalidates_under_lock_before_and_after_build(self):
        environment = self.prepare_environment()

        result = self.run_deploy(environment, "--phase", "platform-gateway-nginx")

        self.assertEqual(0, result.returncode, result.stderr)
        calls = self.calls()
        lock_call = f"fs mkdir {self.root / 'private/deploy.lock'}"
        build_index = calls.index("build")
        first_replacement = next(
            index
            for index, call in enumerate(calls)
            if call.startswith("compose up ")
        )
        checkpoint_checks = [
            index
            for index, call in enumerate(calls)
            if call.startswith("docker image inspect --format {{.Id}} ")
        ]
        self.assertEqual(12, len(checkpoint_checks))
        self.assertEqual(
            Counter({image_name: 2 for image_name in FAKE_IMAGE_IDS}),
            Counter(calls[index].rsplit(" ", 1)[-1] for index in checkpoint_checks),
        )
        self.assertLess(calls.index(lock_call), checkpoint_checks[0])
        self.assertLess(checkpoint_checks[5], build_index)
        self.assertLess(build_index, checkpoint_checks[6])
        self.assertLess(checkpoint_checks[-1], first_replacement)

    def test_phase_two_rejects_missing_checkpoint_before_replacement_or_smoke(self):
        environment = self.prepare_environment()
        self.checkpoint.unlink()

        self.assert_phase_two_rejected_before_replacement_or_smoke(environment)

    def test_phase_two_rejects_malformed_checkpoint_before_replacement_or_smoke(self):
        environment = self.prepare_environment()
        self.write_checkpoint("not an image checkpoint\n")

        self.assert_phase_two_rejected_before_replacement_or_smoke(environment)

    def test_phase_two_rejects_symlink_checkpoint_before_replacement_or_smoke(self):
        environment = self.prepare_environment()
        target = self.checkpoint.with_name("other-phase.ready")
        target.write_text(EXPECTED_PHASE_CHECKPOINT, encoding="utf-8")
        target.chmod(0o600)
        self.checkpoint.unlink()
        self.checkpoint.symlink_to(target.name)

        self.assert_phase_two_rejected_before_replacement_or_smoke(environment)

    def test_phase_two_rejects_wrong_owner_checkpoint_before_replacement_or_smoke(self):
        environment = self.prepare_environment()
        environment["DEPLOY_TEST_CURRENT_UID"] = str(os.getuid() + 1)

        self.assert_phase_two_rejected_before_replacement_or_smoke(environment)

    def test_phase_two_rejects_wrong_mode_checkpoint_before_replacement_or_smoke(self):
        environment = self.prepare_environment()
        self.checkpoint.chmod(0o640)

        self.assert_phase_two_rejected_before_replacement_or_smoke(environment)

    def test_phase_two_rejects_image_stale_checkpoint_before_replacement_or_smoke(self):
        environment = self.prepare_environment()
        environment["DEPLOY_TEST_CHANGED_IMAGE"] = (
            "mor-test/risk-service:development"
        )
        environment["DEPLOY_TEST_CHANGED_IMAGE_ID"] = "sha256:" + "0" * 64

        self.assert_phase_two_rejected_before_replacement_or_smoke(environment)

    def test_build_failure_stops_before_container_update(self):
        environment = self.prepare_environment(build_exit_code=17)

        result = self.run_deploy(environment, "--phase", "risk-bff-web")

        self.assertEqual(17, result.returncode)
        calls = self.calls()
        self.assertIn("build", calls)
        self.assertFalse(any(call.startswith("compose up ") for call in calls))
        self.assertFalse(any(call.startswith("python3 ") for call in calls))
        self.assertIn("应用构建", result.stderr)
        checkpoint_removals = [
            index
            for index, call in enumerate(calls)
            if call.startswith("fs rm ") and call.endswith(str(self.checkpoint))
        ]
        self.assertEqual(1, len(checkpoint_removals))
        self.assertLess(checkpoint_removals[0], calls.index("build"))
        self.assertFalse(self.checkpoint.exists())
        self.assertFalse((self.root / "private/deploy.lock").exists())

    def test_phase_one_stops_at_the_selected_bff_failure_without_crossing_to_web(self):
        environment = self.prepare_environment(compose_fail_service="operations-console-bff")

        result = self.run_deploy(
            environment, "--skip-build", "--phase", "risk-bff-web"
        )

        self.assertEqual(23, result.returncode)
        calls = self.calls()
        self.assertTrue(any(call.endswith("risk-service") for call in calls))
        self.assertTrue(any(call.endswith("operations-console-bff") for call in calls))
        self.assertFalse(any(call.endswith("operations-console-web") for call in calls))
        self.assertFalse(
            any(
                "platform-support-service" in call and call.startswith("compose up")
                for call in calls
            )
        )
        self.assertFalse(any(call.startswith("python3 ") for call in calls))
        self.assertEqual("compose ps", calls[-1])
        self.assertIn("operations-console-bff", result.stderr)
        self.assertFalse(self.checkpoint.exists())

    def test_phase_one_image_id_failure_after_health_cannot_leave_checkpoint(self):
        environment = self.prepare_environment()
        environment["DEPLOY_TEST_IMAGE_INSPECT_FAIL"] = (
            "mor-test/risk-service:development"
        )

        result = self.run_deploy(environment, "--phase", "risk-bff-web")

        self.assertEqual(31, result.returncode)
        calls = self.calls()
        self.assertTrue(any(call.endswith("operations-console-web") for call in calls))
        self.assertFalse(any(call.startswith("python3 ") for call in calls))
        self.assertFalse(self.checkpoint.exists())
        self.assertIn("checkpoint", result.stderr)

    def test_successful_phase_one_atomically_publishes_exact_checkpoint_after_health(self):
        environment = self.prepare_environment()

        result = self.run_deploy(environment, "--phase", "risk-bff-web")

        self.assertEqual(0, result.returncode, result.stderr)
        checkpoint_stat = self.checkpoint.lstat()
        self.assertTrue(stat.S_ISREG(checkpoint_stat.st_mode))
        self.assertFalse(self.checkpoint.is_symlink())
        self.assertEqual(os.getuid(), checkpoint_stat.st_uid)
        self.assertEqual(0o600, stat.S_IMODE(checkpoint_stat.st_mode))
        self.assertEqual(
            EXPECTED_PHASE_CHECKPOINT,
            self.checkpoint.read_text(encoding="utf-8"),
        )
        calls = self.calls()
        publish_calls = [call for call in calls if call.startswith("fs mv ")]
        self.assertEqual(1, len(publish_calls))
        source, destination = publish_calls[0].removeprefix("fs mv ").split()
        self.assertEqual(self.checkpoint.parent, Path(source).parent)
        self.assertEqual(self.checkpoint, Path(destination))
        last_health = next(
            index
            for index, call in enumerate(calls)
            if call.endswith("operations-console-web")
        )
        self.assertGreater(calls.index(publish_calls[0]), last_health)
        self.assertEqual(
            [],
            list(self.checkpoint.parent.glob(".risk-bff-web-phase.ready.tmp.*")),
        )

    def test_phase_two_stops_at_the_selected_nginx_failure_before_gateway_and_smoke(self):
        environment = self.prepare_environment(compose_fail_service="nginx")

        result = self.run_deploy(
            environment, "--skip-build", "--phase", "platform-gateway-nginx"
        )

        self.assertEqual(23, result.returncode)
        calls = self.calls()
        self.assertTrue(any(call.endswith("platform-support-service") for call in calls))
        self.assertTrue(any(call.endswith(" nginx") for call in calls))
        self.assertFalse(any(call.endswith("api-gateway") for call in calls))
        self.assertFalse(
            any(
                "risk-service" in call and call.startswith("compose up")
                for call in calls
            )
        )
        self.assertFalse(any(call.startswith("python3 ") for call in calls))
        self.assertEqual("compose ps", calls[-1])
        self.assertIn("nginx", result.stderr)

    def test_phase_two_smoke_failure_is_reported_without_claiming_success(self):
        environment = self.prepare_environment(smoke_exit_code=29)

        result = self.run_deploy(
            environment, "--skip-build", "--phase", "platform-gateway-nginx"
        )

        self.assertEqual(29, result.returncode)
        calls = self.calls()
        self.assertEqual(1, sum(call.startswith("python3 ") for call in calls))
        self.assertEqual("compose ps", calls[-1])
        self.assertIn("公开 HTTP 烟测", result.stderr)
        self.assertNotIn("阶段与公开烟测成功", result.stdout)

    def test_wrong_ambient_java_home_does_not_override_discovered_jdk_25(self):
        environment = self.prepare_environment()
        java_8 = self.root / "java-8"
        (java_8 / "bin").mkdir(parents=True)
        self.write_executable(
            java_8 / "bin/java",
            'printf \'java version "1.8.0_481"\\n\' >&2\n',
        )
        environment["JAVA_HOME"] = str(java_8)

        result = self.run_deploy(
            environment, "--check-only", "--phase", "risk-bff-web"
        )

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("部署前检查通过", result.stdout)

    def test_invalid_timeout_fails_before_preflight_commands(self):
        environment = self.prepare_environment()

        result = self.run_deploy(
            environment, "--phase", "risk-bff-web", "--timeout", "0"
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("正整数", result.stderr)
        self.assertFalse(self.log.exists())

    def test_phase_one_lock_conflict_with_active_phase_two_preserves_checkpoint(self):
        environment = self.prepare_environment()
        deploy_lock = self.root / "private/deploy.lock"
        deploy_lock.mkdir()
        (deploy_lock / "active-phase").write_text(
            "platform-gateway-nginx\n", encoding="utf-8"
        )
        checkpoint_stat = self.checkpoint.lstat()
        checkpoint_content = self.checkpoint.read_bytes()

        result = self.run_deploy(
            environment, "--skip-build", "--phase", "risk-bff-web"
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("另一个 mor-test Operations 部署", result.stderr)
        calls = self.calls()
        self.assertFalse(any(call.startswith("compose up ") for call in calls))
        self.assertFalse(any(call.startswith("python3 ") for call in calls))
        self.assertFalse(
            any(
                call.startswith("fs rm ") and call.endswith(str(self.checkpoint))
                for call in calls
            )
        )
        after_stat = self.checkpoint.lstat()
        self.assertEqual(checkpoint_stat.st_ino, after_stat.st_ino)
        self.assertEqual(checkpoint_content, self.checkpoint.read_bytes())

    def test_unprepared_environment_fails_with_setup_instruction(self):
        environment = self.prepare_environment()
        (self.root / ".env.test").unlink()

        result = self.run_deploy(environment, "--phase", "risk-bff-web")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("scripts/prepare.py", result.stderr)
        self.assertTrue(self.checkpoint.exists())
        self.assertFalse(self.log.exists())

    def test_insecure_secret_permissions_fail_before_preflight_commands(self):
        environment = self.prepare_environment()
        (self.root / ".env.test").chmod(0o644)

        result = self.run_deploy(environment, "--phase", "risk-bff-web")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("0600", result.stderr)
        self.assertTrue(self.checkpoint.exists())
        self.assertFalse(self.log.exists())


class ApplicationBuildContractTest(unittest.TestCase):
    def test_operations_build_orders_risk_before_consumers_and_does_not_repackage_vault(self):
        build_script = BUILD_SCRIPT.read_text(encoding="utf-8")

        for marker in (
            '(cd "$workspace_dir/risk-compliance-service"',
            'risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar',
            '"$environment_dir/artifacts/risk-service.jar"',
        ):
            self.assertIn(marker, build_script)

        build = build_script.index('(cd "$workspace_dir/paytech-build"')
        foundation = build_script.index('(cd "$workspace_dir/paytech-foundation"')
        risk = build_script.index('(cd "$workspace_dir/risk-compliance-service"')
        risk_copy = build_script.index(
            'risk-web/target/risk-web-1.0.0-SNAPSHOT-exec.jar'
        )
        platform = build_script.index('(cd "$workspace_dir/platform-support-service"')
        gateway = build_script.index('(cd "$workspace_dir/api-gateway"')
        bff = build_script.index('(cd "$workspace_dir/operations-console-bff"')
        web = build_script.index('(cd "$workspace_dir/operations-console-web"')
        remaining_copies = build_script.index(
            'platform-support-web/target/platform-support-web-1.0.0-SNAPSHOT-exec.jar'
        )
        compose = build_script.index('"$environment_dir/compose.sh" build')
        self.assertEqual(
            [
                build,
                foundation,
                risk,
                risk_copy,
                platform,
                gateway,
                bff,
                web,
                remaining_copies,
                compose,
            ],
            sorted(
                (
                    build,
                    foundation,
                    risk,
                    risk_copy,
                    platform,
                    gateway,
                    bff,
                    web,
                    remaining_copies,
                    compose,
                )
            ),
        )
        self.assertIn(
            "./mvnw -pl risk-web -am -DskipTests clean install", build_script
        )
        foundation_command = next(
            line
            for line in build_script.splitlines()
            if '(cd "$workspace_dir/paytech-foundation"' in line
        )
        foundation_modules = tuple(
            re.search(
                r"\./mvnw -pl ([^ ]+) -am -DskipTests clean install", foundation_command
            )
            .group(1)
            .split(",")
        )
        self.assertEqual(
            (
                "paytech-mvc-web-starter",
                "paytech-http-client-support",
                "paytech-mybatis-support",
                "paytech-redis-starter",
                "paytech-snowflake-id",
                "paytech-json-canonicalization",
            ),
            foundation_modules,
            "Risk must be built from the Foundation canonicalization artifact selected in this build",
        )
        self.assertIn(
            '"$environment_dir/artifacts/risk-service.jar"', build_script
        )
        self.assertIn("paytech-redis-starter", build_script)

        compose_build = build_script.split(
            '"$environment_dir/compose.sh" build \\\n', 1
        )[1]
        services = tuple(
            token
            for token in compose_build.replace("\\\n", " ").split()
            if token != "\\"
        )
        self.assertEqual(
            (
                "nacos",
                "risk-service",
                "platform-support-service",
                "operations-console-bff",
                "api-gateway",
                "operations-console-web",
            ),
            services,
        )
        self.assertEqual(1, services.count("risk-service"))
        self.assertNotIn("vault-service", services)


class ReadmeDeploymentContractTest(unittest.TestCase):
    def test_first_time_setup_cannot_dependency_expand_across_deployment_waves(self):
        readme = README.read_text(encoding="utf-8")
        application_services = {
            "risk-service",
            "platform-support-service",
            "operations-console-bff",
            "api-gateway",
            "operations-console-web",
            "nginx",
        }
        unsafe_commands = []
        for command in logical_shell_commands(readme):
            if not command.startswith("./test-environment/compose.sh up "):
                continue
            arguments = command.split()
            selected_services = application_services.intersection(arguments)
            can_expand_dependencies = "--no-deps" not in arguments
            crosses_waves = len(selected_services) > 1
            if selected_services and (can_expand_dependencies or crosses_waves):
                unsafe_commands.append(command)

        self.assertEqual(
            [],
            unsafe_commands,
            "README application startup must use deploy phases, never direct Compose "
            "commands whose dependencies can cross the PREPARED catalog gate",
        )

        first_time_setup = readme.split("## 首次准备", 1)[1].split(
            "## 已有环境切换", 1
        )[0]
        prepared_gate = first_time_setup.index("PREPARED")
        phase_one = first_time_setup.index(
            "./test-environment/deploy.sh --phase risk-bff-web"
        )
        phase_two = first_time_setup.index(
            "./test-environment/deploy.sh --skip-build --phase "
            "platform-gateway-nginx"
        )
        self.assertLess(prepared_gate, phase_one)
        self.assertLess(phase_one, phase_two)


def service_block(compose, service_name):
    marker = f"  {service_name}:\n"
    try:
        remainder = compose.split(marker, 1)[1]
    except IndexError as error:
        raise AssertionError(f"missing Compose service: {service_name}") from error
    next_service = re.search(r"^  [a-z0-9-]+:\n", remainder, re.MULTILINE)
    return remainder if next_service is None else remainder[: next_service.start()]


class RiskComposeContractTest(unittest.TestCase):
    def test_risk_is_backend_only_and_bff_waits_for_platform_and_risk(self):
        compose = COMPOSE_APPLICATIONS.read_text(encoding="utf-8")
        risk = service_block(compose, "risk-service")

        for exact_line in (
            "    <<: *java",
            "    image: mor-test/risk-service:development",
            "      context: ./artifacts",
            "      dockerfile: ../java/Dockerfile",
            "      args: { JAR_FILE: risk-service.jar }",
            "      <<: *discovery",
            "      SERVER_ADDRESS: 0.0.0.0",
            "      SPRING_CLOUD_NACOS_DISCOVERY_IP: 172.30.73.25",
            "      RISK_DB_USERNAME: risk_app",
            "      RISK_DB_PASSWORD: ${MOR_RISK_DB_PASSWORD:?prepare test secrets}",
            "      RISK_REDIS_HOST: redis",
            '      RISK_REDIS_PORT: "6379"',
            "      RISK_REDIS_PASSWORD: ${PAYTECH_REDIS_PASSWORD:?prepare test secrets}",
            '      RISK_SNOWFLAKE_REGION_ID: "0"',
            '      RISK_SNOWFLAKE_WORKER_ID: "25"',
            "      test: [CMD, java, -cp, /app/health-probe, LocalHttpHealthProbe, http://127.0.0.1:18104/actuator/health/readiness]",
            "      backend: { ipv4_address: 172.30.73.25 }",
        ):
            self.assertIn(exact_line, risk)
        self.assertIn("jdbc:mysql://mysql:3306/mor_risk?", risk)
        self.assertEqual(
            {"mysql", "redis", "nacos"},
            set(
                re.findall(
                    r"^      ([a-z0-9-]+): \{ condition: service_healthy \}$",
                    risk.split("    depends_on:\n", 1)[1].split(
                        "    healthcheck:\n", 1
                    )[0],
                    re.MULTILINE,
                )
            ),
        )
        self.assertNotIn("ports:", risk)
        self.assertNotIn("ingress", risk)
        self.assertNotIn("web:", risk)
        self.assertNotIn("networks: [web]", risk)

        bff = service_block(compose, "operations-console-bff")
        bff_dependencies = bff.split("    depends_on:\n", 1)[1].split(
            "    healthcheck:\n", 1
        )[0]
        self.assertEqual(
            {"platform-support-service", "risk-service"},
            set(
                re.findall(
                    r"^      ([a-z0-9-]+): \{ condition: service_healthy \}$",
                    bff_dependencies,
                    re.MULTILINE,
                )
            ),
        )


class ComposeWrapperSecurityTest(unittest.TestCase):
    def test_reserved_docker_variables_are_not_imported_from_test_secrets(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            shutil.copy2(TEST_ENVIRONMENT / "compose.sh", root / "compose.sh")
            (root / ".env.test").write_text(
                "DOCKER_HOST=tcp://example.test:2375\n",
                encoding="utf-8",
            )
            fake_bin = root / "fake-bin"
            fake_bin.mkdir()
            docker_log = root / "docker.log"
            fake_docker = fake_bin / "docker"
            fake_docker.write_text(
                '#!/usr/bin/env bash\nprintf "called\\n" >> "$DOCKER_LOG"\n',
                encoding="utf-8",
            )
            fake_docker.chmod(0o755)
            environment = os.environ.copy()
            environment["PATH"] = f"{fake_bin}:{environment['PATH']}"
            environment["DOCKER_LOG"] = str(docker_log)

            result = subprocess.run(
                ["/bin/bash", str(root / "compose.sh"), "ps"],
                capture_output=True,
                text=True,
                env=environment,
                check=False,
            )

            self.assertNotEqual(0, result.returncode)
            self.assertIn("DOCKER_HOST", result.stderr)
            self.assertFalse(docker_log.exists())


if __name__ == "__main__":
    unittest.main()
