import json
from pathlib import Path
import unittest

import cao_minh_assets as assets


class CaoMinhAssetsTests(unittest.TestCase):
    def test_exactly_four_version_pinned_assets_exist_in_database(self):
        db = assets.load_database()
        self.assertEqual(len(db["assets"]), 4)
        self.assertEqual({item["role"] for item in db["assets"]}, assets.ROLES)
        self.assertEqual(sum(item["bytes"] for item in db["assets"]), 6099063)
        self.assertEqual(len({item["url"] for item in db["assets"]}), 4)

    def test_db_is_injected_without_image_files_or_base64(self):
        script = assets.registry_script()
        self.assertEqual(script.count('"cao-minh:'), 4)
        self.assertNotIn("data:image/", script)
        self.assertNotIn("base64", script.lower())
        path = assets.ROOT / "web/offline-character-assets.js"
        runtime = path.read_text(encoding="utf-8")
        self.assertIn("indexedDB.open", runtime)
        self.assertIn("URL.createObjectURL", runtime)
        self.assertIn("register('service-worker.js'", runtime)
        html = '<script src="cloudinary-gameplay.js"></script>'
        modified = assets.inject_scripts(html)
        self.assertEqual(modified.count('cao-minh-asset-db.js'), 1)
        self.assertEqual(modified.count('offline-character-assets.js'), 1)


if __name__ == "__main__":
    unittest.main()
