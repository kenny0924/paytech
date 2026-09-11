"""Offline guards for explicit test-only Vault key preparation; no Docker access."""
import base64
from pathlib import Path
import tempfile
import unittest

from vault import prepare_key


class VaultKeyPreparationTest(unittest.TestCase):
    def test_new_empty_database_gets_private_key_and_replay_preserves_it(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            prepare_key(root, set())
            path = root / "private/vault.env"
            original = path.read_bytes()
            values = dict(line.split("=", 1) for line in original.decode().splitlines())
            self.assertEqual(32, len(base64.b64decode(values["VAULT_LOCAL_AES_ROOT_KEY"])))
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            self.assertEqual(0o700, path.parent.stat().st_mode & 0o777)
            prepare_key(root, {"t_vault_record"})
            self.assertEqual(original, path.read_bytes())

    def test_existing_database_without_key_cannot_silently_rotate(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaises(SystemExit):
                prepare_key(root, {"t_vault_record"})
            self.assertFalse((root / "private/vault.env").exists())

    def test_incomplete_or_symlink_material_is_not_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "private").mkdir()
            path = root / "private/vault.env"
            path.touch()
            with self.assertRaises(SystemExit):
                prepare_key(root, set())
            self.assertEqual(b"", path.read_bytes())
            path.unlink()
            path.symlink_to(root / "missing")
            with self.assertRaises(SystemExit):
                prepare_key(root, set())
            self.assertTrue(path.is_symlink())


if __name__ == "__main__":
    unittest.main()
