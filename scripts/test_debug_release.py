import hashlib
import json
from pathlib import Path
import struct
import tempfile
import unittest
import zipfile

import debug_release as release


class DebugReleaseTests(unittest.TestCase):
    def test_single_native_key_replaces_every_primary_slot(self):
        key = "AIza" + "test_" * 8
        config, native = release.configuration(key)
        self.assertTrue(native)
        self.assertEqual({config[f"GEMINI_API_KEY_{i}"] for i in range(1, 7)}, {key})
        self.assertEqual(config["GEMINI_4_API_KEY"], "")

    def test_proxy_key_disables_all_old_primary_slots(self):
        config, native = release.configuration("test-current-agron-key")
        self.assertFalse(native)
        self.assertTrue(all(config[f"GEMINI_API_KEY_{i}"] == "" for i in range(1, 7)))
        self.assertEqual(config["GEMINI_4_API_KEY"], "test-current-agron-key")
        self.assertEqual(config["GEMINI_4_BASE_URL"], release.DEFAULT_BASE)

    def test_json_preserves_explicit_endpoint_and_model(self):
        config, native = release.configuration(json.dumps({"apiKey": "test-key",
            "baseUrl": "https://example.org/v1", "model": "example-model"}))
        self.assertFalse(native)
        self.assertEqual(config["GEMINI_4_BASE_URL"], "https://example.org/v1")
        self.assertEqual(config["GEMINI_4_MODEL"], "example-model")

    def test_invalid_configuration_stops_before_packaging(self):
        for value in ("", "{", '{"key":""}', '{"key":"test","base_url":"http://example.org"}'):
            with self.subTest(value=value), self.assertRaises(SystemExit):
                release.configuration(value)

    def test_unexpected_compiled_code_is_rejected(self):
        with self.assertRaises(SystemExit):
            release.patch_dex(b"unexpected")

    def test_recovered_package_changes_only_required_entries(self):
        config, _ = release.configuration("test-current-agron-key")
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "debug.apk"
            release.package(config, target)
            with zipfile.ZipFile(target) as apk:
                self.assertEqual(apk.getinfo("resources.arsc").compress_type, zipfile.ZIP_STORED)
                self.assertEqual(json.loads(apk.read("assets/personal-provider-config.json")), config)
                strings, attrs = release.axml(apk.read("AndroidManifest.xml"))
                self.assertEqual(strings[attrs[("manifest", "versionName")][3]][0], "1.0.0.0")
                self.assertEqual(attrs[("manifest", "versionCode")][3], 90000001)
                self.assertTrue(attrs[("application", "debuggable")][3])
                dex = apk.read("classes5.dex")
                self.assertEqual(dex[12:32], hashlib.sha1(dex[32:]).digest())
                original = (release.ROOT / "apk-original/classes5.dex").read_bytes()
                self.assertEqual(len(dex), len(original))
                # Locate the patched instruction sequence using the known input layout.
                method_start = 44328 + 16
                self.assertEqual(struct.unpack_from("<H", dex, method_start)[0], 0x0811)
                self.assertEqual(dex[32:method_start], original[32:method_start])
                self.assertEqual(dex[method_start + 176 * 2:], original[method_start + 176 * 2:])


if __name__ == "__main__":
    unittest.main()
