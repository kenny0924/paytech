"""Static contract for Platform Support Redis coordination in mor-test."""

from pathlib import Path
import unittest


TEST_ENVIRONMENT = Path(__file__).resolve().parents[1]
COMPOSE_APPLICATIONS = TEST_ENVIRONMENT / "compose.applications.yaml"


class PlatformRedisDeploymentContractTest(unittest.TestCase):
    def test_platform_uses_the_test_redis_service(self):
        compose = COMPOSE_APPLICATIONS.read_text(encoding="utf-8")
        platform = compose.split("  platform-support-service:\n", 1)[1].split(
            "  operations-console-bff:\n", 1
        )[0]

        self.assertIn("PLATFORM_SUPPORT_REDIS_HOST: redis", platform)
        self.assertIn('PLATFORM_SUPPORT_REDIS_PORT: "6379"', platform)
        self.assertIn(
            "PLATFORM_SUPPORT_REDIS_PASSWORD: ${PAYTECH_REDIS_PASSWORD:?prepare test secrets}",
            platform,
        )
        self.assertIn("redis: { condition: service_healthy }", platform)


if __name__ == "__main__":
    unittest.main()
