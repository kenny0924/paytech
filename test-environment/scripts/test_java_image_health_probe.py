"""Offline contracts for the JRE-only Java container readiness probe."""

import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import textwrap
import unittest

TEST_ENVIRONMENT = Path(__file__).resolve().parents[1]
PROBE_SOURCE = TEST_ENVIRONMENT / "java/LocalHttpHealthProbe.java"
SNAPSHOT_FETCH_SOURCE = TEST_ENVIRONMENT / "java/LocalSnapshotEvidenceFetch.java"
JAVA_DOCKERFILE = TEST_ENVIRONMENT / "java/Dockerfile"
BUILD_SCRIPT = TEST_ENVIRONMENT / "scripts/build.sh"
COMPOSE_APPLICATIONS = TEST_ENVIRONMENT / "compose.applications.yaml"

READINESS_URLS = (
    "http://127.0.0.1:18104/actuator/health/readiness",
    "http://127.0.0.1:18106/platform-support/actuator/health/readiness",
    "http://172.30.73.22:18201/actuator/health/readiness",
    "http://127.0.0.1:19080/actuator/health/readiness",
)


class DockerfileContractError(ValueError):
    pass


class ComposeContractError(ValueError):
    pass


def parse_flow_argv(value, line_number):
    if not value.startswith("[") or not value.endswith("]"):
        raise ComposeContractError(
            "line %d: healthcheck test must be one closed flow list" % line_number
        )
    content = value[1:-1].strip()
    if not content:
        return []
    arguments = [argument.strip() for argument in content.split(",")]
    if any(not argument for argument in arguments):
        raise ComposeContractError(
            "line %d: healthcheck test contains an empty argv item" % line_number
        )
    return arguments


def parse_compose_healthcheck_argv(source):
    """Parse only services -> service -> healthcheck -> test flow lists."""

    services_started = False
    services_closed = False
    current_service = None
    current_healthcheck = None
    service_names = set()
    healthcheck_services = set()
    test_services = set()
    disable_services = set()
    healthcheck_argv = {}

    for line_number, raw_line in enumerate(source.splitlines(), start=1):
        leading = raw_line[: len(raw_line) - len(raw_line.lstrip())]
        if "\t" in leading:
            raise ComposeContractError(
                "line %d: tabs are not accepted in the bounded parser" % line_number
            )
        content = raw_line.strip()
        if not content or content.startswith("#"):
            continue
        indentation = len(raw_line) - len(raw_line.lstrip(" "))

        if indentation == 0:
            current_service = None
            current_healthcheck = None
            if content == "services:":
                if services_started:
                    raise ComposeContractError("duplicate top-level services key")
                services_started = True
            elif services_started:
                services_closed = True
            continue
        if not services_started or services_closed:
            continue

        if indentation == 2:
            match = re.fullmatch(r"([A-Za-z0-9][A-Za-z0-9_-]*):", content)
            if match is None:
                raise ComposeContractError(
                    "line %d: service must use a block mapping" % line_number
                )
            current_service = match.group(1)
            current_healthcheck = None
            if current_service in service_names:
                raise ComposeContractError(
                    "line %d: duplicate service %s"
                    % (line_number, current_service)
                )
            service_names.add(current_service)
            continue

        if current_service is None:
            raise ComposeContractError(
                "line %d: services content is outside a service" % line_number
            )
        if indentation == 4:
            key, separator, value = content.partition(":")
            if not separator or not re.fullmatch(r"(?:<<|[A-Za-z0-9_.-]+)", key):
                raise ComposeContractError(
                    "line %d: malformed service mapping key" % line_number
                )
            if key != "healthcheck":
                current_healthcheck = None
                continue
            if value.strip():
                raise ComposeContractError(
                    "line %d: healthcheck must use a block mapping" % line_number
                )
            if current_service in healthcheck_services:
                raise ComposeContractError(
                    "line %d: duplicate healthcheck key for %s"
                    % (line_number, current_service)
                )
            healthcheck_services.add(current_service)
            current_healthcheck = current_service
            continue

        if indentation == 6 and current_healthcheck == current_service:
            key, separator, value = content.partition(":")
            if not separator or not re.fullmatch(r"[A-Za-z0-9_.-]+", key):
                raise ComposeContractError(
                    "line %d: malformed healthcheck mapping key" % line_number
                )
            if key == "disable":
                if current_service in disable_services:
                    raise ComposeContractError(
                        "line %d: duplicate healthcheck disable key for %s"
                        % (line_number, current_service)
                    )
                disable_services.add(current_service)
                if value.strip() != "false":
                    raise ComposeContractError(
                        "line %d: healthcheck must remain enabled for %s"
                        % (line_number, current_service)
                    )
                continue
            if key != "test":
                continue
            if current_service in test_services:
                raise ComposeContractError(
                    "line %d: duplicate healthcheck test key for %s"
                    % (line_number, current_service)
                )
            test_services.add(current_service)
            healthcheck_argv[current_service] = parse_flow_argv(
                value.strip(), line_number
            )

    if not services_started:
        raise ComposeContractError("missing top-level services key")
    return healthcheck_argv


def dockerfile_logical_instructions(source):
    instructions = []
    continued_parts = []
    for raw_line in source.splitlines():
        line = raw_line.strip()
        if not continued_parts and (not line or line.startswith("#")):
            continue
        continued = line.endswith("\\")
        continued_parts.append(line[:-1].rstrip() if continued else line)
        if not continued:
            instructions.append(" ".join(continued_parts))
            continued_parts = []
    if continued_parts:
        instructions.append(" ".join(continued_parts))
    return instructions


def dockerfile_opcode(instruction):
    return instruction.split(maxsplit=1)[0].upper()


def validate_offline_java_dockerfile(source):
    forbidden = []
    for instruction in dockerfile_logical_instructions(source):
        operation = dockerfile_opcode(instruction)
        if operation in {"RUN", "ADD"}:
            forbidden.append(instruction)
            continue
        if operation == "COPY" and any(
            argument.lower() == "--from"
            or argument.lower().startswith("--from=")
            for argument in instruction.split()[1:]
        ):
            forbidden.append(instruction)
    if forbidden:
        raise DockerfileContractError(
            "The shared Java image permits no RUN, ADD, or COPY --from: %s"
            % forbidden
        )


def dockerfile_environment(source):
    environment = {}
    for instruction in dockerfile_logical_instructions(source):
        if dockerfile_opcode(instruction) != "ENV":
            continue
        _, assignment = instruction.split(maxsplit=1)
        name, separator, raw_value = assignment.partition("=")
        if not separator or not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", name):
            raise DockerfileContractError(
                "The bounded Dockerfile parser requires ENV name=value"
            )
        environment[name] = (
            json.loads(raw_value) if raw_value.startswith('"') else raw_value
        )
    return environment


def dockerfile_exec_argv(source, opcode):
    matching = [
        instruction.split(maxsplit=1)[1]
        for instruction in dockerfile_logical_instructions(source)
        if dockerfile_opcode(instruction) == opcode
    ]
    if len(matching) != 1:
        raise DockerfileContractError(
            "The shared Java image requires exactly one %s" % opcode
        )
    try:
        argv = json.loads(matching[0])
    except json.JSONDecodeError as error:
        raise DockerfileContractError(
            "%s must use JSON exec form" % opcode
        ) from error
    if not isinstance(argv, list) or not all(
        isinstance(argument, str) for argument in argv
    ):
        raise DockerfileContractError("%s must be a string argv" % opcode)
    return argv


def prepare_health_probe_output(environment_directory):
    return subprocess.run(
        [
            "/bin/bash",
            "-c",
            'source "$1"; prepare_health_probe_output "$2"',
            "health-probe-test",
            str(BUILD_SCRIPT),
            str(environment_directory),
        ],
        capture_output=True,
        text=True,
        check=False,
    )


def jdk_25_tool(tool_name):
    candidates = []
    configured_home = os.environ.get("JAVA_HOME")
    if configured_home:
        candidates.append(Path(configured_home) / "bin" / tool_name)
    candidates.append(
        Path("/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin") / tool_name
    )
    for candidate in candidates:
        if not candidate.is_file() or not os.access(candidate, os.X_OK):
            continue
        result = subprocess.run(
            [str(candidate), "-version"],
            capture_output=True,
            text=True,
            check=False,
        )
        version = result.stdout + result.stderr
        expected_version = (
            r"\bjavac 25(?:\.|\s|$)"
            if tool_name == "javac"
            else r'\b(?:java|openjdk) version "25(?:\.|"|\s)'
        )
        if result.returncode == 0 and re.search(expected_version, version):
            return candidate
    raise AssertionError("JDK 25 %s is required for this offline gate" % tool_name)


class JavaImageHealthProbeTest(unittest.TestCase):
    def compile_probe(self, output_directory, *additional_sources):
        self.assertTrue(
            PROBE_SOURCE.is_file(),
            "LocalHttpHealthProbe.java must exist before the Java images can be offline",
        )
        self.assertTrue(
            SNAPSHOT_FETCH_SOURCE.is_file(),
            "LocalSnapshotEvidenceFetch.java must exist before Snapshot rollout waits can run",
        )
        command = [
            str(jdk_25_tool("javac")),
            "--release",
            "25",
            "-encoding",
            "UTF-8",
            "-Xlint:all",
            "-d",
            str(output_directory),
            str(PROBE_SOURCE),
            str(SNAPSHOT_FETCH_SOURCE),
            *(str(source) for source in additional_sources),
        ]
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_java_dockerfile_has_no_online_package_installation(self):
        dockerfile = JAVA_DOCKERFILE.read_text(encoding="utf-8")

        validate_offline_java_dockerfile(dockerfile)
        self.assertIn(
            "COPY --chown=10001:10001 health-probe/ /app/health-probe/",
            dockerfile,
        )

    def test_dockerfile_gate_catches_continued_run_and_remote_add(self):
        malicious_dockerfiles = {
            "continued RUN": textwrap.dedent(
                """
                FROM example.invalid/base@sha256:abc
                RUN apt-get update && \\
                    curl https://example.invalid/package
                """
            ),
            "remote ADD": textwrap.dedent(
                """
                FROM example.invalid/base@sha256:abc
                ADD ["https://example.invalid/archive.tar", "/tmp/archive.tar"]
                """
            ),
        }

        for case, malicious_dockerfile in malicious_dockerfiles.items():
            with self.subTest(case=case):
                with self.assertRaises(DockerfileContractError):
                    validate_offline_java_dockerfile(malicious_dockerfile)

    def test_dockerfile_gate_rejects_every_copy_from_image(self):
        malicious_dockerfiles = {
            "plain external image": textwrap.dedent(
                """
                FROM example.invalid/base@sha256:abc
                COPY --from=example.invalid/tools:latest /probe /probe
                """
            ),
            "JSON copy with another flag": textwrap.dedent(
                """
                FROM example.invalid/base@sha256:abc
                COPY --chown=10001:10001 --from=tools ["/probe", "/probe"]
                """
            ),
        }

        for case, malicious_dockerfile in malicious_dockerfiles.items():
            with self.subTest(case=case):
                with self.assertRaises(DockerfileContractError):
                    validate_offline_java_dockerfile(malicious_dockerfile)

    def test_probe_source_exists_and_uses_only_jre_http_apis(self):
        self.assertTrue(
            PROBE_SOURCE.is_file(),
            "LocalHttpHealthProbe.java is the offline replacement for curl",
        )
        source = PROBE_SOURCE.read_text(encoding="utf-8")

        imports = re.findall(r"(?m)^import\s+([^;]+);$", source)
        self.assertTrue(imports)
        self.assertTrue(all(name.startswith("java.") for name in imports), imports)
        self.assertIn("HttpClient.Redirect.NEVER", source)
        self.assertIn(".connectTimeout(Duration.ofSeconds(2))", source)
        self.assertIn(".timeout(Duration.ofSeconds(3))", source)
        self.assertIn(".GET()", source)
        self.assertIn("HttpResponse.BodyHandlers.discarding()", source)
        self.assertNotRegex(source, r"System\.(?:out|err)|printStackTrace|\.body\(\)")

    def test_snapshot_fetch_source_is_exact_bounded_and_jre_only(self):
        self.assertTrue(
            SNAPSHOT_FETCH_SOURCE.is_file(),
            "the Java image needs a JRE-only body fetcher because it contains no curl",
        )
        source = SNAPSHOT_FETCH_SOURCE.read_text(encoding="utf-8")

        imports = re.findall(r"(?m)^import\s+([^;]+);$", source)
        self.assertTrue(imports)
        self.assertTrue(all(name.startswith("java.") for name in imports), imports)
        self.assertEqual(
            1,
            source.count("http://127.0.0.1:19080/actuator/prometheus"),
        )
        self.assertEqual(
            1,
            source.count(
                "http://platform-support-service:18106/"
                "platform-support/gateway-config/v1/public-snapshot"
            ),
        )
        self.assertIn("MAX_BODY_BYTES = 1_048_576", source)
        self.assertIn("HttpClient.Redirect.NEVER", source)
        self.assertIn(".connectTimeout(Duration.ofSeconds(2))", source)
        self.assertIn(".timeout(Duration.ofSeconds(3))", source)
        self.assertIn(".GET()", source)
        self.assertIn("HttpResponse.BodyHandlers.limiting(", source)
        self.assertIn("HttpResponse.BodyHandlers.ofByteArray()", source)
        self.assertIn("response.body().length > MAX_BODY_BYTES", source)
        self.assertNotRegex(source, r"System\.err|printStackTrace")

    def test_build_compiles_both_jre_probes_with_jdk_25_before_image_builds(self):
        build = BUILD_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("prepare_health_probe_output() {", build)
        self.assertIn('prepare_health_probe_output "$environment_dir"', build)
        self.assertIn('[[ "${BASH_SOURCE[0]}" == "$0" ]]', build)
        self.assertIn(
            'health_probe_output="$environment_dir/artifacts/health-probe"', build
        )
        self.assertNotIn('rm -rf -- "$health_probe_output"', build)
        compile_command = (
            '"$JAVA_HOME/bin/javac" --release 25 -encoding UTF-8 '
            '-d "$health_probe_output" '
            '"$environment_dir/java/LocalHttpHealthProbe.java" '
            '"$environment_dir/java/LocalSnapshotEvidenceFetch.java"'
        )
        self.assertIn(compile_command, build)
        self.assertLess(build.index(compile_command), build.index("./mvnw"))
        self.assertLess(
            build.index(compile_command),
            build.index('"$environment_dir/compose.sh" build'),
        )

    def test_build_preparation_creates_and_refreshes_real_directories(self):
        build = BUILD_SCRIPT.read_text(encoding="utf-8")
        self.assertIn(
            'if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then',
            build,
            "The build script must be safely sourceable before exercising its directory helper",
        )

        with tempfile.TemporaryDirectory() as temporary_directory:
            environment_directory = Path(temporary_directory).resolve() / "environment"
            environment_directory.mkdir()

            result = prepare_health_probe_output(environment_directory)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            artifacts = environment_directory / "artifacts"
            probe_output = artifacts / "health-probe"
            self.assertTrue(artifacts.is_dir())
            self.assertFalse(artifacts.is_symlink())
            self.assertTrue(probe_output.is_dir())
            self.assertFalse(probe_output.is_symlink())

            stale_class = probe_output / "Stale.class"
            stale_class.write_bytes(b"stale")
            result = prepare_health_probe_output(environment_directory)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertFalse(stale_class.exists())
            self.assertTrue(probe_output.is_dir())
            self.assertFalse(probe_output.is_symlink())

    def test_build_preparation_rejects_artifacts_symlink_or_non_directory(self):
        build = BUILD_SCRIPT.read_text(encoding="utf-8")
        self.assertIn(
            'if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then',
            build,
            "The build script must be safely sourceable before exercising its directory helper",
        )

        for artifacts_kind in ("symlink", "file"):
            with self.subTest(artifacts_kind=artifacts_kind):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory).resolve()
                    environment_directory = root / "environment"
                    environment_directory.mkdir()
                    artifacts = environment_directory / "artifacts"
                    outside = root / "outside"
                    outside.mkdir()
                    sentinel = outside / "sentinel"
                    sentinel.write_text("preserve", encoding="utf-8")
                    if artifacts_kind == "symlink":
                        artifacts.symlink_to(outside, target_is_directory=True)
                    else:
                        artifacts.write_text("not a directory", encoding="utf-8")

                    result = prepare_health_probe_output(environment_directory)
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual(
                        "preserve", sentinel.read_text(encoding="utf-8")
                    )
                    self.assertFalse((outside / "health-probe").exists())

    def test_build_preparation_rejects_probe_symlink_or_non_directory(self):
        build = BUILD_SCRIPT.read_text(encoding="utf-8")
        self.assertIn(
            'if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then',
            build,
            "The build script must be safely sourceable before exercising its directory helper",
        )

        for probe_kind in ("symlink", "file"):
            with self.subTest(probe_kind=probe_kind):
                with tempfile.TemporaryDirectory() as temporary_directory:
                    root = Path(temporary_directory).resolve()
                    environment_directory = root / "environment"
                    artifacts = environment_directory / "artifacts"
                    artifacts.mkdir(parents=True)
                    probe_output = artifacts / "health-probe"
                    outside = root / "outside"
                    outside.mkdir()
                    sentinel = outside / "sentinel"
                    sentinel.write_text("preserve", encoding="utf-8")
                    if probe_kind == "symlink":
                        probe_output.symlink_to(outside, target_is_directory=True)
                    else:
                        probe_output.write_text("not a directory", encoding="utf-8")

                    result = prepare_health_probe_output(environment_directory)
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual(
                        "preserve", sentinel.read_text(encoding="utf-8")
                    )
                    self.assertTrue(probe_output.exists())

    def test_all_four_java_healthchecks_use_fixed_probe_argv(self):
        compose_source = COMPOSE_APPLICATIONS.read_text(encoding="utf-8")
        healthcheck_argv = parse_compose_healthcheck_argv(compose_source)

        expected_by_service = dict(
            zip(
                (
                    "risk-service",
                    "platform-support-service",
                    "operations-console-bff",
                    "api-gateway",
                ),
                READINESS_URLS,
            )
        )
        actual_probe_services = {
            service_name
            for service_name, test_argv in healthcheck_argv.items()
            if "LocalHttpHealthProbe" in test_argv
        }
        self.assertEqual(set(expected_by_service), actual_probe_services)
        for service_name, url in expected_by_service.items():
            with self.subTest(service=service_name):
                test_argv = healthcheck_argv[service_name]
                self.assertEqual(
                    [
                        "CMD",
                        "java",
                        "-cp",
                        "/app/health-probe",
                        "LocalHttpHealthProbe",
                        url,
                    ],
                    test_argv,
                )
                self.assertNotIn("CMD-SHELL", test_argv)
                self.assertTrue(
                    all("${" not in argument for argument in test_argv), test_argv
                )

    def test_compose_parser_rejects_duplicate_service_healthcheck_or_test_keys(self):
        duplicate_contracts = {
            "service": """
                services:
                  risk-service:
                    healthcheck:
                      test: [CMD, first]
                  risk-service:
                    healthcheck:
                      test: [CMD, second]
            """,
            "healthcheck": """
                services:
                  risk-service:
                    healthcheck:
                      test: [CMD, first]
                    healthcheck:
                      test: [CMD, second]
            """,
            "test": """
                services:
                  risk-service:
                    healthcheck:
                      test: [CMD, first]
                      test: [CMD, second]
            """,
        }

        for key_kind, source in duplicate_contracts.items():
            with self.subTest(key_kind=key_kind):
                with self.assertRaises(ComposeContractError):
                    parse_compose_healthcheck_argv(textwrap.dedent(source))

    def test_compose_parser_rejects_disabled_or_duplicate_disable_keys(self):
        disabled_contracts = {
            "disabled": """
                services:
                  risk-service:
                    healthcheck:
                      test: [CMD, java]
                      disable: true
            """,
            "duplicate disable": """
                services:
                  risk-service:
                    healthcheck:
                      test: [CMD, java]
                      disable: false
                      disable: false
            """,
        }

        for case, source in disabled_contracts.items():
            with self.subTest(case=case):
                with self.assertRaises(ComposeContractError):
                    parse_compose_healthcheck_argv(textwrap.dedent(source))

    def test_probe_compiles_with_jdk_25(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            output_directory = Path(temporary_directory)
            self.compile_probe(output_directory)

            self.assertTrue((output_directory / "LocalHttpHealthProbe.class").is_file())
            self.assertTrue(
                (output_directory / "LocalSnapshotEvidenceFetch.class").is_file()
            )

    def test_invalid_cli_inputs_fail_silently_without_a_network_target(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            output_directory = Path(temporary_directory)
            self.compile_probe(output_directory)
            java = str(jdk_25_tool("java"))
            invalid_arguments = (
                (),
                ("not-a-url",),
                ("http://[invalid",),
                ("http://user:secret@",),
                ("not-a-url", "second-argument"),
            )

            for arguments in invalid_arguments:
                with self.subTest(arguments=arguments):
                    result = subprocess.run(
                        [
                            java,
                            "-cp",
                            str(output_directory),
                            "LocalHttpHealthProbe",
                            *arguments,
                        ],
                        capture_output=True,
                        text=True,
                        check=False,
                    )
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual("", result.stdout)
                    self.assertEqual("", result.stderr)

            invalid_snapshot_arguments = (
                (),
                ("not-a-url",),
                ("http://127.0.0.1:19080/actuator/health",),
                (
                    "http://platform-support-service:18106/"
                    "platform-support/gateway-config/v1/public-snapshot?x=1",
                ),
                (
                    "http://127.0.0.1:19080/actuator/prometheus",
                    "second-argument",
                ),
            )
            for arguments in invalid_snapshot_arguments:
                with self.subTest(snapshot_arguments=arguments):
                    result = subprocess.run(
                        [
                            java,
                            "-cp",
                            str(output_directory),
                            "LocalSnapshotEvidenceFetch",
                            *arguments,
                        ],
                        capture_output=True,
                        check=False,
                    )
                    self.assertNotEqual(0, result.returncode)
                    self.assertEqual(b"", result.stdout)
                    self.assertEqual(b"", result.stderr)

    def test_probe_is_silent_under_the_shared_image_environment(self):
        dockerfile = JAVA_DOCKERFILE.read_text(encoding="utf-8")
        image_environment = dockerfile_environment(dockerfile)

        with tempfile.TemporaryDirectory() as temporary_directory:
            output_directory = Path(temporary_directory)
            self.compile_probe(output_directory)
            environment = os.environ.copy()
            for option_variable in (
                "JAVA_TOOL_OPTIONS",
                "JDK_JAVA_OPTIONS",
                "_JAVA_OPTIONS",
            ):
                environment.pop(option_variable, None)
            environment.update(image_environment)

            result = subprocess.run(
                [
                    str(jdk_25_tool("java")),
                    "-cp",
                    str(output_directory),
                    "LocalHttpHealthProbe",
                    "not-a-url",
                ],
                capture_output=True,
                text=True,
                check=False,
                env=environment,
            )

            self.assertEqual(64, result.returncode)
            self.assertEqual("", result.stdout)
            self.assertEqual("", result.stderr)

    def test_app_entrypoint_keeps_runtime_options_without_exporting_them(self):
        dockerfile = JAVA_DOCKERFILE.read_text(encoding="utf-8")

        self.assertNotIn("JAVA_TOOL_OPTIONS", dockerfile_environment(dockerfile))
        self.assertEqual(
            [
                "java",
                "-Xms128m",
                "-Xmx512m",
                "-Duser.home=/tmp",
                "-Djava.io.tmpdir=/tmp",
                "-jar",
                "/app/application.jar",
            ],
            dockerfile_exec_argv(dockerfile, "ENTRYPOINT"),
        )

    def test_canonical_urls_and_http_status_decisions_are_pure(self):
        harness = textwrap.dedent(
            """
            public final class LocalHttpHealthProbeContractHarness {
                private static void require(boolean condition) {
                    if (!condition) {
                        System.exit(1);
                    }
                }

                public static void main(String[] args) {
                    String[] validUrls = {
                        "http://127.0.0.1:18104/actuator/health/readiness",
                        "http://127.0.0.1:18106/platform-support/actuator/health/readiness",
                        "http://172.30.73.22:18201/actuator/health/readiness",
                        "http://127.0.0.1:19080/actuator/health/readiness"
                    };
                    for (String value : validUrls) {
                        require(LocalHttpHealthProbe.isCanonicalHttpUrl(value));
                    }

                    String[] invalidUrls = {
                        "http://127.0.0.1:18105/actuator/health/readiness",
                        "http://127.0.0.1:18104/actuator/health",
                        "http://172.30.73.23:18201/actuator/health/readiness",
                        "https://127.0.0.1:18104/actuator/health/readiness",
                        "HTTP://127.0.0.1:18104/actuator/health/readiness",
                        "http://user:secret@127.0.0.1:18104/actuator/health/readiness",
                        "http://127.0.0.1/actuator/health/readiness",
                        "http://127.0.0.1:18104",
                        "http://127.0.0.1:18104/actuator/../health/readiness",
                        "http://127.0.0.1:18104/actuator/health/readiness?verbose=true",
                        "http://127.0.0.1:18104/actuator/health/readiness#fragment"
                    };
                    for (String value : invalidUrls) {
                        require(!LocalHttpHealthProbe.isCanonicalHttpUrl(value));
                    }

                    int[] successfulStatuses = {200, 204, 226, 299};
                    for (int status : successfulStatuses) {
                        require(LocalHttpHealthProbe.isSuccessfulStatus(status));
                    }
                    int[] failedStatuses = {-1, 0, 199, 300, 301, 404, 500, 1000};
                    for (int status : failedStatuses) {
                        require(!LocalHttpHealthProbe.isSuccessfulStatus(status));
                    }
                }
            }
            """
        ).strip() + "\n"

        with tempfile.TemporaryDirectory() as temporary_directory:
            output_directory = Path(temporary_directory)
            harness_source = output_directory / "LocalHttpHealthProbeContractHarness.java"
            harness_source.write_text(harness, encoding="utf-8")
            self.compile_probe(output_directory, harness_source)

            result = subprocess.run(
                [
                    str(jdk_25_tool("java")),
                    "-cp",
                    str(output_directory),
                    "LocalHttpHealthProbeContractHarness",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual("", result.stdout)
            self.assertEqual("", result.stderr)

    def test_run_uses_injected_transport_for_all_status_and_failure_paths(self):
        harness = textwrap.dedent(
            """
            import java.io.IOException;

            public final class LocalHttpHealthProbeRunHarness {
                private static void require(boolean condition) {
                    if (!condition) {
                        System.exit(1);
                    }
                }

                public static void main(String[] args) {
                    String validUrl =
                        "http://127.0.0.1:18104/actuator/health/readiness";

                    int[] successfulStatuses = {200, 204, 226, 299};
                    for (int status : successfulStatuses) {
                        int[] calls = {0};
                        int exit = LocalHttpHealthProbe.run(
                            new String[] {validUrl},
                            endpoint -> {
                                calls[0]++;
                                require(validUrl.equals(endpoint.toString()));
                                return status;
                            }
                        );
                        require(exit == 0);
                        require(calls[0] == 1);
                    }

                    int[] failedStatuses = {300, 301, 307, 500, 503};
                    for (int status : failedStatuses) {
                        int[] calls = {0};
                        int exit = LocalHttpHealthProbe.run(
                            new String[] {validUrl},
                            endpoint -> {
                                calls[0]++;
                                return status;
                            }
                        );
                        require(exit != 0);
                        require(calls[0] == 1);
                    }

                    int ioExit = LocalHttpHealthProbe.run(
                        new String[] {validUrl},
                        endpoint -> {
                            throw new IOException("synthetic");
                        }
                    );
                    require(ioExit != 0);

                    Thread.interrupted();
                    int interruptedExit = LocalHttpHealthProbe.run(
                        new String[] {validUrl},
                        endpoint -> {
                            throw new InterruptedException("synthetic");
                        }
                    );
                    require(interruptedExit != 0);
                    require(Thread.currentThread().isInterrupted());
                    Thread.interrupted();

                    String[][] invalidArguments = {
                        {},
                        {"http://127.0.0.1:18105/actuator/health/readiness"},
                        {"http://127.0.0.1:18104/actuator/health"},
                        {"http://172.30.73.23:18201/actuator/health/readiness"},
                        {validUrl, "extra"}
                    };
                    int[] invalidCalls = {0};
                    for (String[] invalid : invalidArguments) {
                        int exit = LocalHttpHealthProbe.run(
                            invalid,
                            endpoint -> {
                                invalidCalls[0]++;
                                return 204;
                            }
                        );
                        require(exit == 64);
                    }
                    require(invalidCalls[0] == 0);
                }
            }
            """
        ).strip() + "\n"

        with tempfile.TemporaryDirectory() as temporary_directory:
            output_directory = Path(temporary_directory)
            harness_source = output_directory / "LocalHttpHealthProbeRunHarness.java"
            harness_source.write_text(harness, encoding="utf-8")
            self.compile_probe(output_directory, harness_source)

            result = subprocess.run(
                [
                    str(jdk_25_tool("java")),
                    "-cp",
                    str(output_directory),
                    "LocalHttpHealthProbeRunHarness",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual("", result.stdout)
            self.assertEqual("", result.stderr)

    def test_snapshot_fetch_run_is_exact_bounded_and_silent_on_failure(self):
        harness = textwrap.dedent(
            """
            import java.io.ByteArrayOutputStream;
            import java.io.IOException;

            public final class LocalSnapshotEvidenceFetchRunHarness {
                private static void require(boolean condition) {
                    if (!condition) {
                        System.exit(1);
                    }
                }

                private static LocalSnapshotEvidenceFetch.EvidenceResponse response(
                        int status, byte[] body) {
                    return new LocalSnapshotEvidenceFetch.EvidenceResponse(
                        status, body
                    );
                }

                public static void main(String[] args) {
                    String metrics =
                        "http://127.0.0.1:19080/actuator/prometheus";
                    String snapshot =
                        "http://platform-support-service:18106/"
                        + "platform-support/gateway-config/v1/public-snapshot";
                    require(LocalSnapshotEvidenceFetch.isCanonicalEvidenceUrl(metrics));
                    require(LocalSnapshotEvidenceFetch.isCanonicalEvidenceUrl(snapshot));
                    String[] invalidUrls = {
                        "https://127.0.0.1:19080/actuator/prometheus",
                        "HTTP://127.0.0.1:19080/actuator/prometheus",
                        "http://user:secret@127.0.0.1:19080/actuator/prometheus",
                        "http://127.0.0.1:19080/actuator/prometheus?x=1",
                        "http://127.0.0.1:19080/actuator/prometheus#x",
                        "http://platform-support-service:18106/"
                            + "platform-support/gateway-config/v1/public-snapshot/"
                    };
                    for (String invalid : invalidUrls) {
                        require(!LocalSnapshotEvidenceFetch.isCanonicalEvidenceUrl(invalid));
                    }

                    byte[] expected = "exact-body\\n".getBytes();
                    for (String endpoint : new String[] {metrics, snapshot}) {
                        int[] calls = {0};
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        int exit = LocalSnapshotEvidenceFetch.run(
                            new String[] {endpoint},
                            uri -> {
                                calls[0]++;
                                require(endpoint.equals(uri.toString()));
                                return response(200, expected);
                            },
                            output
                        );
                        require(exit == 0);
                        require(calls[0] == 1);
                        require(java.util.Arrays.equals(expected, output.toByteArray()));
                    }

                    for (int status : new int[] {199, 300, 404, 500}) {
                        ByteArrayOutputStream output = new ByteArrayOutputStream();
                        int exit = LocalSnapshotEvidenceFetch.run(
                            new String[] {metrics},
                            uri -> response(status, "secret".getBytes()),
                            output
                        );
                        require(exit == 1);
                        require(output.size() == 0);
                    }

                    byte[] oversized = new byte[
                        LocalSnapshotEvidenceFetch.MAX_BODY_BYTES + 1
                    ];
                    byte[] exactLimit = new byte[
                        LocalSnapshotEvidenceFetch.MAX_BODY_BYTES
                    ];
                    ByteArrayOutputStream exactLimitOutput = new ByteArrayOutputStream();
                    require(LocalSnapshotEvidenceFetch.run(
                        new String[] {metrics},
                        uri -> response(200, exactLimit),
                        exactLimitOutput
                    ) == 0);
                    require(exactLimitOutput.size() == exactLimit.length);
                    ByteArrayOutputStream oversizedOutput = new ByteArrayOutputStream();
                    require(LocalSnapshotEvidenceFetch.run(
                        new String[] {metrics},
                        uri -> response(200, oversized),
                        oversizedOutput
                    ) == 1);
                    require(oversizedOutput.size() == 0);

                    int[] invalidCalls = {0};
                    require(LocalSnapshotEvidenceFetch.run(
                        new String[] {"http://127.0.0.1:19080/actuator/health"},
                        uri -> {
                            invalidCalls[0]++;
                            return response(200, expected);
                        },
                        new ByteArrayOutputStream()
                    ) == 64);
                    require(invalidCalls[0] == 0);

                    require(LocalSnapshotEvidenceFetch.run(
                        new String[] {metrics},
                        uri -> { throw new IOException("secret"); },
                        new ByteArrayOutputStream()
                    ) == 1);

                    Thread.interrupted();
                    require(LocalSnapshotEvidenceFetch.run(
                        new String[] {metrics},
                        uri -> { throw new InterruptedException("secret"); },
                        new ByteArrayOutputStream()
                    ) == 1);
                    require(Thread.currentThread().isInterrupted());
                    Thread.interrupted();

                    require(LocalSnapshotEvidenceFetch.run(
                        new String[] {metrics},
                        uri -> response(200, expected),
                        new ByteArrayOutputStream() {
                            @Override
                            public synchronized void write(byte[] value, int offset, int length) {
                                throw new RuntimeException("secret");
                            }
                        }
                    ) == 1);
                }
            }
            """
        ).strip() + "\n"

        with tempfile.TemporaryDirectory() as temporary_directory:
            output_directory = Path(temporary_directory)
            harness_source = (
                output_directory / "LocalSnapshotEvidenceFetchRunHarness.java"
            )
            harness_source.write_text(harness, encoding="utf-8")
            self.compile_probe(output_directory, harness_source)

            result = subprocess.run(
                [
                    str(jdk_25_tool("java")),
                    "-cp",
                    str(output_directory),
                    "LocalSnapshotEvidenceFetchRunHarness",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertEqual("", result.stdout)
            self.assertEqual("", result.stderr)


if __name__ == "__main__":
    unittest.main()
