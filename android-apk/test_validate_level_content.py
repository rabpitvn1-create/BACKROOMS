import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import validate_level_content as validator


def write_json(root: Path, relative: str, value):
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False), encoding="utf-8")
    return path


def full_profile(level_id: str, *, allow_entities=True):
    return {
        "schemaVersion": 1,
        "id": level_id,
        "canonProfile": {
            "environmentTags": ["test_environment"],
            "requiredZoneTags": ["entry", "escape"],
            "allowedPhenomena": [],
            "forbiddenClaims": ["backrooms_confirmed_conscious"],
            "transitionTags": ["test_transition"],
            "metadata": {},
        },
        "generationConstraints": {
            "minZones": 2,
            "maxZones": 4,
            "minEvidencePerRequiredFact": 1,
            "minEvidenceSourceTypesPerRequiredFact": 1,
            "maxRequiredActions": 2,
            "allowSurvivors": False,
            "allowEntities": allow_entities,
            "proceduralTopology": True,
            "proceduralLandmarks": True,
            "proceduralEvidencePlacement": True,
            "proceduralEscapeBlueprint": True,
        },
    }


def explicit_definition(level_id: str, *, complete=True):
    effects = [{"type": "COMPLETE_LEVEL"}] if complete else []
    return {
        "schemaVersion": 1,
        "id": level_id,
        "name": level_id,
        "initialZoneId": "entry",
        "zones": [
            {"id": "entry", "name": "Entry", "connections": ["exit"], "tags": ["entry"]},
            {"id": "exit", "name": "Exit", "connections": [], "tags": ["escape"]},
        ],
        "environment": {},
        "escapeBlueprint": {"solutionId": "hidden", "requiredFacts": ["FACT"], "requiredActions": ["finish"], "locked": True},
        "evidence": [{"id": "e1", "supports": ["FACT"], "sources": ["SEARCH"], "zoneId": "exit", "discoverConditions": []}],
        "exploreRoute": ["exit"],
        "actions": [{"id": "finish", "matchGroups": [["finish"]], "conditions": ["zone:exit", "fact:FACT"], "effects": effects}],
        "canonProfile": {
            "environmentTags": [],
            "requiredZoneTags": ["entry", "escape"],
            "allowedPhenomena": [],
            "forbiddenClaims": ["backrooms_confirmed_conscious"],
            "transitionTags": [],
            "metadata": {},
        },
        "generationConstraints": {
            "minZones": 2,
            "maxZones": 4,
            "minEvidencePerRequiredFact": 1,
            "minEvidenceSourceTypesPerRequiredFact": 1,
            "maxRequiredActions": 2,
            "allowSurvivors": False,
            "allowEntities": True,
            "proceduralTopology": False,
            "proceduralLandmarks": False,
            "proceduralEvidencePlacement": False,
            "proceduralEscapeBlueprint": False,
        },
    }


class LevelContentValidatorTest(unittest.TestCase):
    def validate(self, root: Path, strict=True):
        return validator.validate_content(root, strict=strict)

    def codes(self, report):
        return {issue["code"] for issue in report["errors"]}

    def test_opaque_ids_and_data_only_onboarding_fixture(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            catalog = {
                "schemaVersion": 1,
                "campaignId": "future",
                "entries": [
                    {"id": "742", "name": "Parent", "kind": "MAIN", "campaignOrder": 1000, "outgoingTransitions": ["742.13"]},
                    {"id": "742.13", "name": "Child", "kind": "SUBLEVEL", "parentId": "742", "campaignOrder": 2000, "outgoingTransitions": ["999.alpha"]},
                    {"id": "999.alpha", "name": "Alpha", "kind": "SPECIAL", "campaignOrder": 3000, "outgoingTransitions": ["Red Rooms"]},
                    {"id": "Red Rooms", "name": "Red Rooms", "kind": "SPECIAL", "campaignOrder": 4000, "outgoingTransitions": ["1.618033988749894..."]},
                    {"id": "1.618033988749894...", "name": "Phi", "kind": "SPECIAL", "campaignOrder": 5000, "metadata": {"terminal": "true"}},
                ],
            }
            write_json(root, "level_catalog/future.json", catalog)
            write_json(root, "level_profiles/742.json", full_profile("742"))
            write_json(root, "level_profiles/742.13.json", {
                "schemaVersion": 2,
                "id": "742.13",
                "inheritsFrom": "742",
                "canonPatch": {"environmentTagsAdd": ["child_environment"], "forbiddenClaimsAdd": ["child_forbidden"]},
                "generationConstraintsPatch": {"allowEntities": False},
            })
            write_json(root, "level_profiles/999.alpha.json", full_profile("999.alpha"))
            write_json(root, "level_profiles/Red Rooms.json", full_profile("Red Rooms"))
            write_json(root, "level_profiles/phi.json", full_profile("1.618033988749894..."))

            report = self.validate(root)
            self.assertEqual([], report["errors"])
            child = next(x for x in report["levels"] if x["levelId"] == "742.13")
            self.assertEqual("inherited-profile", child["definitionSource"])
            self.assertEqual("742", child["inheritanceSource"])
            self.assertFalse(child["generationConstraints"]["allowEntities"])
            encoded = json.dumps(report, ensure_ascii=False)
            for hidden in ["escapeBlueprint", "solutionId", "requiredFacts", "requiredActions", "evidence"]:
                self.assertNotIn(f'"{hidden}"', encoded)

    def test_catalog_duplicate_dangling_parent_cycle_and_duplicate_order_fail_closed(self):
        cases = [
            ([{"id": "x", "name": "x", "kind": "MAIN"}, {"id": "x", "name": "x2", "kind": "MAIN"}], "duplicate_level_id"),
            ([{"id": "x", "name": "x", "kind": "SUBLEVEL", "parentId": "missing", "metadata": {"terminal": True}}], "parent_missing"),
            ([
                {"id": "a", "name": "a", "kind": "SUBLEVEL", "parentId": "b", "campaignId": "c", "campaignOrder": 1, "metadata": {"terminal": True}},
                {"id": "b", "name": "b", "kind": "SUBLEVEL", "parentId": "a", "campaignId": "c", "campaignOrder": 2, "metadata": {"terminal": True}},
            ], "parent_cycle"),
            ([
                {"id": "a", "name": "a", "kind": "MAIN", "campaignId": "c", "campaignOrder": 1, "metadata": {"terminal": True}},
                {"id": "b", "name": "b", "kind": "MAIN", "campaignId": "c", "campaignOrder": 1, "metadata": {"terminal": True}},
            ], "duplicate_campaign_order"),
        ]
        for entries, expected in cases:
            with self.subTest(expected=expected), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                write_json(root, "level_catalog/catalog.json", {"schemaVersion": 1, "entries": entries})
                report = self.validate(root)
                self.assertIn(expected, self.codes(report))

    def test_profile_invalid_schema_duplicate_cycle_and_conflicting_patch(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            entries = [
                {"id": "main", "name": "main", "kind": "MAIN", "campaignId": "c", "campaignOrder": 1, "outgoingTransitions": ["child"]},
                {"id": "child", "name": "child", "kind": "SUBLEVEL", "parentId": "main", "campaignId": "c", "campaignOrder": 2, "metadata": {"terminal": True}},
            ]
            write_json(root, "level_catalog/catalog.json", {"schemaVersion": 1, "entries": entries})
            write_json(root, "level_profiles/a.json", {"schemaVersion": 99, "id": "main", "canonProfile": {}, "generationConstraints": {}})
            write_json(root, "level_profiles/b.json", {"schemaVersion": 1, "id": "main", "canonProfile": {}, "generationConstraints": {}})
            write_json(root, "level_profiles/child.json", {
                "schemaVersion": 2,
                "id": "child",
                "inheritsFrom": "main",
                "canonPatch": {"environmentTagsAdd": ["x"], "environmentTagsRemove": ["x"]},
            })
            report = self.validate(root)
            codes = self.codes(report)
            self.assertIn("unsupported_profile_schema", codes)
            self.assertIn("duplicate_profile", codes)
            self.assertIn("profile_canon_patch_conflict", codes)

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            entries = [
                {"id": "a", "name": "a", "kind": "SUBLEVEL", "parentId": "b", "campaignId": "c", "campaignOrder": 1, "metadata": {"terminal": True}},
                {"id": "b", "name": "b", "kind": "SUBLEVEL", "parentId": "a", "campaignId": "c", "campaignOrder": 2, "metadata": {"terminal": True}},
            ]
            write_json(root, "level_catalog/catalog.json", {"schemaVersion": 1, "entries": entries})
            write_json(root, "level_profiles/a.json", {"schemaVersion": 2, "id": "a", "inheritsFrom": "b", "canonPatch": {"forbiddenClaimsAdd": ["x"]}})
            write_json(root, "level_profiles/b.json", {"schemaVersion": 2, "id": "b", "inheritsFrom": "a", "canonPatch": {"forbiddenClaimsAdd": ["y"]}})
            report = self.validate(root)
            self.assertIn("inheritance_cycle", self.codes(report))

    def test_transition_multi_edge_and_invalid_edges(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            entries = [
                {"id": "a", "name": "a", "kind": "MAIN", "campaignId": "c", "campaignOrder": 1, "outgoingTransitions": ["b", "c"]},
                {"id": "b", "name": "b", "kind": "MAIN", "campaignId": "c", "campaignOrder": 2, "metadata": {"terminal": True, "contentStatus": "placeholder", "allowPlaceholderInStrict": True}},
                {"id": "c", "name": "c", "kind": "MAIN", "campaignId": "c", "campaignOrder": 3, "metadata": {"terminal": True, "contentStatus": "placeholder", "allowPlaceholderInStrict": True}},
            ]
            write_json(root, "level_catalog/catalog.json", {"schemaVersion": 1, "entries": entries})
            write_json(root, "level_profiles/a.json", full_profile("a"))
            report = self.validate(root)
            self.assertNotIn("duplicate_transition", self.codes(report))
            a = next(x for x in report["levels"] if x["levelId"] == "a")
            self.assertEqual(["b", "c"], a["outgoingTransitions"])

        invalid = [
            ([{"id": "a", "name": "a", "kind": "MAIN", "campaignId": "c", "campaignOrder": 1, "outgoingTransitions": ["missing"], "metadata": {"contentStatus": "placeholder", "allowPlaceholderInStrict": True}}], "transition_target_missing"),
            ([{"id": "a", "name": "a", "kind": "MAIN", "campaignId": "c", "campaignOrder": 1, "outgoingTransitions": ["a"], "metadata": {"contentStatus": "placeholder", "allowPlaceholderInStrict": True}}], "transition_self_loop"),
            ([
                {"id": "a", "name": "a", "kind": "MAIN", "campaignId": "c", "campaignOrder": 2, "outgoingTransitions": ["b"], "metadata": {"contentStatus": "placeholder", "allowPlaceholderInStrict": True}},
                {"id": "b", "name": "b", "kind": "MAIN", "campaignId": "c", "campaignOrder": 1, "metadata": {"contentStatus": "placeholder", "allowPlaceholderInStrict": True, "terminal": True}},
            ], "transition_not_forward"),
            ([
                {"id": "a", "name": "a", "kind": "MAIN", "campaignId": "c", "campaignOrder": 1, "outgoingTransitions": ["b"], "metadata": {"contentStatus": "placeholder", "allowPlaceholderInStrict": True}},
                {"id": "b", "name": "b", "kind": "MAIN", "campaignId": "c", "campaignOrder": 2, "outgoingTransitions": ["a"], "metadata": {"contentStatus": "placeholder", "allowPlaceholderInStrict": True}},
            ], "transition_cycle"),
        ]
        for entries, expected in invalid:
            with self.subTest(expected=expected), tempfile.TemporaryDirectory() as temp:
                root = Path(temp)
                write_json(root, "level_catalog/catalog.json", {"schemaVersion": 1, "entries": entries})
                report = self.validate(root)
                self.assertIn(expected, self.codes(report))

    def test_solvability_valid_definition_and_missing_complete_level_fail(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_json(root, "level_catalog/catalog.json", {"schemaVersion": 1, "entries": [{"id": "explicit", "name": "explicit", "kind": "MAIN", "metadata": {"terminal": True}}]})
            write_json(root, "levels/explicit.json", explicit_definition("explicit", complete=True))
            report = self.validate(root)
            self.assertEqual([], report["errors"])

        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            write_json(root, "level_catalog/catalog.json", {"schemaVersion": 1, "entries": [{"id": "explicit", "name": "explicit", "kind": "MAIN", "metadata": {"terminal": True}}]})
            write_json(root, "levels/explicit.json", explicit_definition("explicit", complete=False))
            report = self.validate(root)
            self.assertIn("missing_complete_level_path", self.codes(report))

    def test_scale_1000_main_levels_plus_200_sublevels_is_deterministic(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            entries = []
            main_count = 1000
            sub_count = 200
            for i in range(main_count):
                level = f"main-{i:04d}"
                entry = {"id": level, "name": level, "kind": "MAIN", "campaignId": "scale", "campaignOrder": i * 10}
                if i + 1 < main_count:
                    targets = [f"main-{i + 1:04d}"]
                    if i % 100 == 0 and i + 2 < main_count:
                        targets.append(f"main-{i + 2:04d}")
                    entry["outgoingTransitions"] = targets
                else:
                    entry["metadata"] = {"terminal": True}
                entries.append(entry)
                write_json(root, f"level_profiles/{level}.json", full_profile(level))
            for i in range(sub_count):
                parent = f"main-{i:04d}"
                level = f"{parent}.sub.alpha"
                entries.append({"id": level, "name": level, "kind": "SUBLEVEL", "parentId": parent, "campaignId": "scale", "campaignOrder": main_count * 10 + i, "metadata": {"terminal": True}})
                write_json(root, f"level_profiles/sub/{i:04d}.json", {
                    "schemaVersion": 2,
                    "id": level,
                    "inheritsFrom": parent,
                    "canonPatch": {"environmentTagsAdd": [f"sub-{i}"]},
                    "generationConstraintsPatch": {"allowEntities": i % 2 == 0},
                })
            write_json(root, "level_catalog/scale.json", {"schemaVersion": 1, "entries": entries})

            first = self.validate(root)
            second = self.validate(root)
            self.assertEqual([], first["errors"])
            self.assertEqual(1200, first["summary"]["totalCatalogLevels"])
            self.assertEqual(200, first["summary"]["inheritedProfiles"])
            self.assertEqual(json.dumps(first, sort_keys=True), json.dumps(second, sort_keys=True))


if __name__ == "__main__":
    unittest.main()
