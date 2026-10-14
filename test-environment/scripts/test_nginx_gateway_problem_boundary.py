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


def nginx_tokens(config: str):
    tokens = []
    current = []
    quote = None
    escaped = False
    index = 0

    def flush():
        if current:
            tokens.append("".join(current))
            current.clear()

    while index < len(config):
        character = config[index]
        if quote is not None:
            if escaped:
                current.append(character)
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
            else:
                current.append(character)
        elif character in ('"', "'"):
            quote = character
        elif character == "#":
            flush()
            newline = config.find("\n", index)
            index = len(config) if newline < 0 else newline
        elif character.isspace():
            flush()
        elif character in "{};":
            flush()
            tokens.append(character)
        else:
            current.append(character)
        index += 1

    if quote is not None or escaped:
        raise AssertionError("unterminated quoted nginx token")
    flush()
    return tokens


def parse_nginx(config: str):
    tokens = nginx_tokens(config)

    def parse_scope(index: int, expects_close: bool):
        entries = []
        statement = []
        while index < len(tokens):
            token = tokens[index]
            index += 1
            if token == ";":
                if not statement:
                    raise AssertionError("empty nginx directive")
                entries.append(("directive", tuple(statement)))
                statement = []
            elif token == "{":
                if not statement:
                    raise AssertionError("nginx block is missing a header")
                children, index = parse_scope(index, True)
                entries.append(("block", tuple(statement), children))
                statement = []
            elif token == "}":
                if not expects_close:
                    raise AssertionError("unexpected nginx closing brace")
                if statement:
                    raise AssertionError("unterminated nginx directive before closing brace")
                return entries, index
            else:
                statement.append(token)

        if expects_close:
            raise AssertionError("unterminated nginx block")
        if statement:
            raise AssertionError("unterminated nginx directive")
        return entries, index

    root, consumed = parse_scope(0, False)
    if consumed != len(tokens):
        raise AssertionError("unparsed nginx configuration")
    return root


def nginx_blocks(scope, header):
    return [
        entry[2]
        for entry in scope
        if entry[0] == "block" and entry[1] == tuple(header)
    ]


def one_nginx_block(scope, header):
    matches = nginx_blocks(scope, header)
    if len(matches) != 1:
        rendered = " ".join(header)
        raise AssertionError(
            f"expected exactly one nginx block '{rendered}', found {len(matches)}"
        )
    return matches[0]


def nginx_directive_arguments(scope, name: str):
    return [
        entry[1][1:]
        for entry in scope
        if entry[0] == "directive" and entry[1][0] == name
    ]


def unique_nginx_scalar(scope, name: str):
    matches = nginx_directive_arguments(scope, name)
    if len(matches) > 1:
        raise AssertionError(f"duplicate nginx directive in one scope: {name}")
    if not matches:
        return None
    if len(matches[0]) != 1:
        raise AssertionError(f"nginx directive must have one value: {name}")
    return matches[0][0]


# Each public listener forwards its API paths to Gateway and owns exactly one trusted entry channel.
ENTRY_CHANNEL_BY_LISTEN = {("8080",): "OPERATIONS", ("8443", "ssl"): "CLIENT"}


def nginx_api_servers(config: str):
    root = parse_nginx(config)
    http = one_nginx_block(root, ("http",))
    servers = nginx_blocks(http, ("server",))
    candidates = [
        server
        for server in servers
        if nginx_blocks(server, ("location", "=", "/api"))
        or nginx_blocks(server, ("location", "^~", "/api/"))
    ]
    by_listen = {}
    for server in candidates:
        listens = [tuple(arguments) for arguments in nginx_directive_arguments(server, "listen")]
        if len(listens) != 1 or listens[0] in by_listen:
            raise AssertionError("expected each API server to declare one distinct listen directive")
        by_listen[listens[0]] = server
    if set(by_listen) != set(ENTRY_CHANNEL_BY_LISTEN):
        raise AssertionError(
            "expected exactly the Operations (8080) and Client (8443 ssl) nginx servers containing the API locations"
        )
    return {
        ENTRY_CHANNEL_BY_LISTEN[listen]: (http, server, {
            "= /api": one_nginx_block(server, ("location", "=", "/api")),
            "^~ /api/": one_nginx_block(server, ("location", "^~", "/api/")),
        })
        for listen, server in by_listen.items()
    }


def nginx_api_scopes(config: str):
    """Yield (channel, http, server, locations) for every API server."""
    for channel, (http, server, locations) in nginx_api_servers(config).items():
        yield channel, http, server, locations


def nginx_api_locations(config: str):
    """Yield (channel, location name, location block) for every API location."""
    for channel, _, _, locations in nginx_api_scopes(config):
        for location_name, scope in locations.items():
            yield channel, channel + " " + location_name, scope


def nginx_api_effective_directives(config: str):
    effective = {}
    for channel, http, server, locations in nginx_api_scopes(config):
        for location_name, values in nginx_server_effective_directives(http, server, locations).items():
            effective[channel + " " + location_name] = values
    return effective


def nginx_server_effective_directives(http, server, locations):
    relevant = (
        "client_max_body_size",
        "proxy_read_timeout",
        "proxy_request_buffering",
    )

    for scope in (http, server, *locations.values()):
        for name in relevant:
            unique_nginx_scalar(scope, name)

    effective = {}
    for location_name, location in locations.items():
        values = {}
        for name in relevant:
            value = None
            for scope in (http, server, location):
                override = unique_nginx_scalar(scope, name)
                if override is not None:
                    value = override
            if value is None:
                raise AssertionError(
                    f"missing effective nginx directive for {location_name}: {name}"
                )
            values[name] = value
        effective[location_name] = values
    return effective


def yaml_scalar(config: str, name: str) -> str:
    match = re.search(rf"^\s*{re.escape(name)}:\s*([^\s#]+)", config, re.MULTILINE)
    if match is None:
        raise AssertionError(f"missing Gateway setting: {name}")
    return match.group(1)


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
        self.assertGreater(nginx_header_buffer, gateway_initial_line)
        self.assertGreater(nginx_large_header_total, gateway_headers)
        self.assertGreater(nginx_body_timeout, gateway_idle)
        self.assertGreater(nginx_header_timeout, gateway_idle)

        for location_name, directives in nginx_api_effective_directives(
            self.nginx
        ).items():
            nginx_body = size_bytes(directives["client_max_body_size"])
            nginx_read_timeout = duration_seconds(directives["proxy_read_timeout"])
            self.assertEqual(128 * 1024, nginx_body, location_name)
            self.assertGreater(nginx_body, 65_537, location_name)
            self.assertEqual(15, nginx_read_timeout, location_name)
            self.assertGreater(nginx_read_timeout, 10, location_name)
            self.assertEqual(
                "off", directives["proxy_request_buffering"], location_name
            )

    def test_every_api_path_streams_to_gateway_instead_of_returning_edge_404(self):
        for _, location_name, scope in nginx_api_locations(self.nginx):
            self.assertEqual(
                "$gateway",
                unique_nginx_scalar(scope, "proxy_pass"),
                location_name,
            )

    def test_every_api_path_overwrites_its_trusted_entry_channel(self):
        for channel, location_name, scope in nginx_api_locations(self.nginx):
            assignments = [
                arguments
                for arguments in nginx_directive_arguments(
                    scope, "proxy_set_header"
                )
                if arguments
                and arguments[0].lower() == "x-entry-channel"
            ]
            self.assertEqual(
                [("X-Entry-Channel", channel)],
                assignments,
                location_name,
            )
            self.assertEqual(
                "off",
                unique_nginx_scalar(scope, "proxy_request_buffering"),
                location_name,
            )
            self.assertEqual(
                "off", unique_nginx_scalar(scope, "proxy_buffering"), location_name
            )
            self.assertFalse(
                any(
                    arguments and arguments[0] == "404"
                    for arguments in nginx_directive_arguments(scope, "return")
                ),
                location_name,
            )

    def test_api_envelope_rejects_server_body_override(self):
        mutated = self.nginx.replace(
            "server_name localhost;",
            "server_name localhost;\n        client_max_body_size 1k;",
            1,
        )

        self.assert_nginx_api_contract_rejects(mutated)

    def test_api_envelope_checks_each_location_read_timeout(self):
        prefix, suffix = self.nginx.rsplit("proxy_read_timeout 15s;", 1)
        mutated = prefix + "proxy_read_timeout 9s;" + suffix

        self.assert_nginx_api_contract_rejects(mutated)

    def test_api_envelope_ignores_commented_buffering_decoy(self):
        mutated = self.nginx.replace(
            "proxy_request_buffering off;",
            "# proxy_request_buffering off;\n            proxy_request_buffering on;",
        )

        self.assert_nginx_api_contract_rejects(mutated)

    def test_api_envelope_rejects_duplicate_relevant_directives(self):
        mutations = {
            "client_max_body_size": self.nginx.replace(
                "client_max_body_size 128k;",
                "client_max_body_size 128k;\n    client_max_body_size 128k;",
                1,
            ),
            "proxy_read_timeout": self.nginx.replace(
                "proxy_read_timeout 15s;",
                "proxy_read_timeout 15s;\n            proxy_read_timeout 15s;",
                1,
            ),
            "proxy_request_buffering": self.nginx.replace(
                "proxy_request_buffering off;",
                "proxy_request_buffering off;\n            proxy_request_buffering off;",
                1,
            ),
        }

        for directive, mutated in mutations.items():
            with self.subTest(directive=directive):
                self.assert_nginx_api_contract_rejects(mutated)

    def assert_nginx_api_contract_rejects(self, mutated: str):
        original = self.nginx
        self.nginx = mutated
        try:
            with self.assertRaises(AssertionError):
                self.test_nginx_absolute_limits_are_strictly_above_gateway_limits()
                self.test_every_api_path_streams_to_gateway_instead_of_returning_edge_404()
        finally:
            self.nginx = original

    def test_compose_mounts_the_reviewed_nginx_configuration_read_only(self):
        compose = COMPOSE_APPLICATIONS.read_text(encoding="utf-8")
        nginx = compose.split("  nginx:\n", 1)[1]
        self.assertIn("./nginx/nginx.conf:/etc/nginx/nginx.conf:ro", nginx)


if __name__ == "__main__":
    unittest.main()
