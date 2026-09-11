#!/usr/bin/env python3
"""Offline checks for the new opt-in operator acceptance entry."""
import contextlib
import io
import unittest
from unittest import mock

import risk_rule_v1_configuration_smoke as smoke


class ConfigurationSmokeEntryTest(unittest.TestCase):
    def test_default_entry_never_touches_the_environment(self):
        with mock.patch.object(smoke, "verify", side_effect=AssertionError("unexpected environment access")) as verify:
            self.assertEqual(0, smoke.main([]))
            verify.assert_not_called()

    def test_failed_acceptance_redacts_internal_response_material(self):
        output = io.StringIO()
        with mock.patch.object(smoke, "verify", side_effect=RuntimeError("secret-response-value")), contextlib.redirect_stderr(output):
            self.assertEqual(1, smoke.main(["--verify"]))
        self.assertEqual("RISK_V1_CONFIGURATION_SMOKE_FAILED\n", output.getvalue())


if __name__ == "__main__":
    unittest.main()
