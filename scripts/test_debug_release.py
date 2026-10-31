import hashlib
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import zipfile
import zlib
import json

import debug_release as release


def fixture():
    return {f"GEMINI_API_KEY_{i}": f"new-test-key-{i}" for i in range(1, 7)}


class DebugReleaseTests(unittest.TestCase):
    def test_only_six_current_keys_are_used(self):
        env = fixture()
        config = release.configuration(env)
        for i in range(1, 7):
            self.assertEqual(config[f"GEMINI_API_KEY_{i}"], env[f"GEMINI_API_KEY_{i}"])
        self.assertEqual(config["GEMINI_4_API_KEY"], "")
        self.assertEqual(config["GEMINI_4_MODEL"], "")

    def test_argon_value_is_ignored_until_reenabled(self):
        self.assertEqual(release.configuration({**fixture(), "ARGON": "old-unused-value"}),
                         release.configuration(fixture()))

    def test_missing_duplicate_or_invalid_primary_keys_stop_packaging(self):
        for i in range(1, 7):
            env = fixture()
            del env[f"GEMINI_API_KEY_{i}"]
            with self.subTest(key=i), self.assertRaises(SystemExit):
                release.configuration(env)
        for invalid in ("new-test-key-1", "contains whitespace", "x" * 1025):
            env = {**fixture(), "GEMINI_API_KEY_6": invalid}
            with self.subTest(value=invalid[:20]), self.assertRaises(SystemExit):
                release.configuration(env)

    def test_probes_cover_all_six_keys_without_external_fallback(self):
        config = release.configuration(fixture())
        with patch.object(release, "probe_provider") as call:
            release.probe(config)
        self.assertEqual(call.call_count, 6)
        for i in range(6):
            args = call.call_args_list[i].args
            self.assertTrue(args[1])
            self.assertEqual(args[0]["GEMINI_API_KEY_1"], config[f"GEMINI_API_KEY_{i + 1}"])

    def test_pinned_patch_changes_only_blank_field_guard_and_checksums(self):
        original = (release.ROOT / "apk-original/classes5.dex").read_bytes()
        result = release.patch_credentials(original)
        self.assertEqual(len(result), len(original))
        self.assertEqual(result[32:44398], original[32:44398])
        self.assertEqual(result[44398:44402], bytes(4))
        self.assertEqual(result[44402:], original[44402:])
        self.assertEqual(result[12:32], hashlib.sha1(result[32:]).digest())
        self.assertEqual(struct.unpack_from("<I", result, 8)[0], zlib.adler32(result[12:]) & 0xffffffff)
        with self.assertRaises(SystemExit):
            release.patch_credentials(b"unexpected input")

    def test_package_preserves_gameplay_and_disables_fallback(self):
        config = release.configuration(fixture())
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "debug.apk"
            release.package(config, target)
            with zipfile.ZipFile(target) as apk:
                self.assertEqual(apk.getinfo("resources.arsc").compress_type, zipfile.ZIP_STORED)
                self.assertEqual(json.loads(apk.read("assets/personal-provider-config.json")), config)
                strings, attrs = release.axml(apk.read("AndroidManifest.xml"))
                self.assertEqual(strings[attrs[("manifest", "versionName")][3]][0], "1.0.0.1")
                self.assertEqual(attrs[("manifest", "versionCode")][3], 90000002)
                self.assertTrue(attrs[("application", "debuggable")][3])
                image_db = json.loads(apk.read("assets/characters/cao-minh-assets.json"))
                self.assertEqual({x["role"] for x in image_db["assets"]},
                                 {"avatar", "combat", "exploration", "sword"})
                self.assertIn(b"indexedDB.open", apk.read("assets/offline-character-assets.js"))
                self.assertIn(b"BACKROOMS_CAO_MINH_ASSET_DATABASE", apk.read("assets/cao-minh-asset-db.js"))
                self.assertIn(b"offline-character-assets.js", apk.read("assets/index.html"))
                self.assertIn(b"characterSword", apk.read("assets/cloudinary-gameplay.js"))
                for p in (release.ROOT / "apk-original").glob("classes*.dex"):
                    expected = release.patch_credentials(p.read_bytes()) if p.name == "classes5.dex" else p.read_bytes()
                    self.assertEqual(apk.read(p.name), expected)


if __name__ == "__main__":
    unittest.main()
