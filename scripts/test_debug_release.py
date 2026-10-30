import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

import debug_release as release


def fixture():
    return {**{f"GEMINI_API_KEY_{i}": f"new-test-key-{i}" for i in range(1, 7)},
            "ARGON": "new-test-argon-key"}


class DebugReleaseTests(unittest.TestCase):
    def test_six_current_keys_and_final_argon_are_independent(self):
        env = fixture()
        config = release.configuration(env)
        for i in range(1, 7):
            self.assertEqual(config[f"GEMINI_API_KEY_{i}"], env[f"GEMINI_API_KEY_{i}"])
        self.assertEqual(config["GEMINI_4_API_KEY"], env["ARGON"])
        self.assertEqual(len(config), 9)

    def test_missing_or_duplicate_primary_keys_stop_packaging(self):
        for i in range(1, 7):
            env = fixture()
            del env[f"GEMINI_API_KEY_{i}"]
            with self.subTest(key=i), self.assertRaises(SystemExit):
                release.configuration(env)
        env = fixture()
        env["GEMINI_API_KEY_6"] = env["GEMINI_API_KEY_1"]
        with self.assertRaises(SystemExit):
            release.configuration(env)

    def test_missing_argon_stops_incomplete_release(self):
        env = fixture()
        del env["ARGON"]
        with self.assertRaises(SystemExit):
            release.configuration(env)

    def test_missing_fallback_allows_primary_probes_but_never_packaging_config(self):
        env = fixture()
        del env["ARGON"]
        config = release.configuration(env, allow_missing_fallback=True)
        with patch.object(release, "probe_provider") as call, self.assertRaises(SystemExit):
            release.probe(config)
        self.assertEqual(call.call_count, 6)
        with self.assertRaises(SystemExit):
            release.configuration(env)

    def test_json_argon_preserves_explicit_endpoint_and_model(self):
        env = fixture()
        env["ARGON"] = json.dumps({"apiKey": "test-argon-key", "baseUrl": "https://example.org/v1", "model": "example-model"})
        config = release.configuration(env)
        self.assertEqual(config["GEMINI_4_BASE_URL"], "https://example.org/v1")
        self.assertEqual(config["GEMINI_4_MODEL"], "example-model")
        self.assertEqual(config["GEMINI_API_KEY_1"], env["GEMINI_API_KEY_1"])

    def test_probes_cover_each_primary_key_then_argon(self):
        config = release.configuration(fixture())
        with patch.object(release, "probe_provider") as call:
            release.probe(config)
        self.assertEqual(call.call_count, 7)
        for i in range(6):
            args = call.call_args_list[i].args
            self.assertTrue(args[1])
            self.assertEqual(args[0]["GEMINI_API_KEY_1"], config[f"GEMINI_API_KEY_{i + 1}"])
        self.assertFalse(call.call_args_list[-1].args[1])
        self.assertEqual(call.call_args_list[-1].args[2], "Argon final fallback")

    def test_invalid_argon_configuration_stops_before_packaging(self):
        for value in ("", "{", '{"key":""}', '{"key":"test","base_url":"http://example.org"}'):
            env = {**fixture(), "ARGON": value}
            with self.subTest(value=value), self.assertRaises(SystemExit):
                release.configuration(env)

    def test_recovered_apk_keeps_original_dex_and_gameplay_entries(self):
        config = release.configuration(fixture())
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
                for p in (release.ROOT / "apk-original").glob("classes*.dex"):
                    self.assertEqual(apk.read(p.name), p.read_bytes())


if __name__ == "__main__":
    unittest.main()
