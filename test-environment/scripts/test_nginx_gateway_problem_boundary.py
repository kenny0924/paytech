"""Static contract for keeping edge limits outside Gateway-owned API limits."""

from pathlib import Path
import re
import unittest


WORKSPACE = Path(__file__).resolve().parents[2]
NGINX_CONFIG = WORKSPACE / "test-environment/nginx/nginx.conf"
COMPOSE_APPLICATIONS = WORKSPACE / "test-environment/compose.applications.yaml"
GATEWAY_CONFIG = (
    WORKSPACE / "api-gateway/api-gateway-web/src/main/resources/application.yml"
)


def size_bytes(raw: str) -> int:
    match = re.fullmatch(r"(\d+)([kKmM]?)(?:[bB])?", raw.strip())
    if match is None:
        raise AssertionError(f"unsupported size literal: {raw}")
    multiplier = {"": 1, "k": 1024, "m": 1024 * 1024}[match.group(2).lower()]
    return int(match.group(1)) * multiplier


def duration_seconds(raw: str) -> int:
    match = re.fullmatch(r"(\d+)s", raw.strip())
    if match is None:
        raise AssertionError(f"unsupported duration literal: {raw}")
    return int(match.group(1))


def nginx_directive(config: str, name: str) -> str:
    match = re.search(rf"^\s*{re.escape(name)}\s+([^;]+);", config, re.MULTILINE)
    if match is None:
        raise AssertionError(f"missing nginx directive: {name}")
    return match.group(1)


def yaml_scalar(config: str, name: str) -> str:
    match = re.search(rf"^\s*{re.escape(name)}:\s*([^\s#]+)", config, re.MULTILINE)
    if match is None:
        raise AssertionError(f"missing Gateway setting: {name}")
    return match.group(1)


def location_body(config: str, expression: str) -> str:
    match = re.search(
        rf"location\s+{expression}\s*\{{(?P<body>[^}}]*)\}}", config, re.DOTALL
    )
    if match is None:
        raise AssertionError(f"missing nginx location: {expression}")
    return match.group("body")


class NginxGatewayProblemBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.nginx = NGINX_CONFIG.read_text(encoding="utf-8")
        self.gateway = GATEWAY_CONFIG.read_text(encoding="utf-8")

    def test_gateway_connector_limits_leave_room_for_gateway_owned_filters(self):
        initial_line = size_bytes(yaml_scalar(self.gateway, "max-initial-line-length"))
        aggregate_headers = size_bytes(
            yaml_scalar(self.gateway, "max-http-request-header-size")
        )
        connector_idle = duration_seconds(yaml_scalar(self.gateway, "idle-timeout"))

        self.assertGreater(initial_line, 1_000)
        self.assertGreater(aggregate_headers, 8_192)
        self.assertGreater(connector_idle, 5)

    def test_nginx_absolute_limits_are_strictly_above_gateway_limits(self):
        gateway_initial_line = size_bytes(
            yaml_scalar(self.gateway, "max-initial-line-length")
        )
        gateway_headers = size_bytes(
            yaml_scalar(self.gateway, "max-http-request-header-size")
        )
        gateway_idle = duration_seconds(yaml_scalar(self.gateway, "idle-timeout"))

        nginx_body = size_bytes(nginx_directive(self.nginx, "client_max_body_size"))
        nginx_header_buffer = size_bytes(
            nginx_directive(self.nginx, "client_header_buffer_size")
        )
        large_header_parts = nginx_directive(
            self.nginx, "large_client_header_buffers"
        ).split()
        nginx_large_header_total = int(large_header_parts[0]) * size_bytes(
            large_header_parts[1]
        )
        nginx_body_timeout = duration_seconds(
            nginx_directive(self.nginx, "client_body_timeout")
        )
        nginx_header_timeout = duration_seconds(
            nginx_directive(self.nginx, "client_header_timeout")
        )

        self.assertGreater(nginx_body, 16_384)
        self.assertGreater(nginx_header_buffer, gateway_initial_line)
        self.assertGreater(nginx_large_header_total, gateway_headers)
        self.assertGreater(nginx_body_timeout, gateway_idle)
        self.assertGreater(nginx_header_timeout, gateway_idle)

    def test_every_api_path_streams_to_gateway_instead_of_returning_edge_404(self):
        exact = location_body(self.nginx, r"=\s*/api")
        subtree = location_body(self.nginx, r"\^~\s*/api/")

        for body in (exact, subtree):
            self.assertIn("proxy_pass $gateway;", body)
            self.assertIn("proxy_request_buffering off;", body)
            self.assertIn("proxy_buffering off;", body)
            self.assertNotIn("return 404", body)

    def test_compose_mounts_the_reviewed_nginx_configuration_read_only(self):
        compose = COMPOSE_APPLICATIONS.read_text(encoding="utf-8")
        nginx = compose.split("  nginx:\n", 1)[1]
        self.assertIn("./nginx/nginx.conf:/etc/nginx/nginx.conf:ro", nginx)


if __name__ == "__main__":
    unittest.main()
