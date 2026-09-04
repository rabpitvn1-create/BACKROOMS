#!/usr/bin/env python3
from __future__ import annotations

import unittest

import simulate_world_director_v2_trajectories as v2


class WorldDirectorV2FeaturesTest(unittest.TestCase):
    def snapshot(self) -> dict:
        return {
            "turnIndex": 27,
            "featureTextV1": (
                "action_explore visit_deep revision_changed recent_move zone_loop evidence_some "
                "candidate_none candidate_maze_pressure candidate_entity_pressure candidate_item_opportunity"
            ),
            "state": {
                "actionKind": "EXPLORE",
                "visitBucket": "deep",
                "legalProposals": ["NONE", "MAZE_PRESSURE", "ENTITY_PRESSURE", "ITEM_OPPORTUNITY"],
                "turnsSinceEntityPressure": 7,
                "turnsSinceItemOpportunity": 3,
                "searchStreak": 0,
                "exploreStreak": 4,
                "entityPressureDensity8": 0.25,
                "itemOpportunityDensity8": 0.125,
                "mazePressureDensity8": 0.375,
                "pressureEntropy8": 1.4,
            },
            "history": [
                {"actionKind": "SEARCH", "pressure": "ITEM_OPPORTUNITY"},
                {"actionKind": "EXPLORE", "pressure": "NONE"},
                {"actionKind": "EXPLORE", "pressure": "MAZE_PRESSURE"},
                {"actionKind": "EXPLORE", "pressure": "ENTITY_PRESSURE"},
            ],
        }

    def test_v2_adds_pacing_history_without_hidden_authority(self):
        text = v2.feature_text_v2(self.snapshot())
        self.assertIn("contract_world_director_pressure_v2", text)
        self.assertIn("since_entity_5_9", text)
        self.assertIn("explore_streak_4_plus", text)
        self.assertIn("h1_pressure_entity_pressure", text)
        self.assertIn("h2_pressure_maze_pressure", text)
        self.assertIn("cross_action_prevpressure_explore_entity_pressure", text)
        for hidden in (
            "levelid", "zoneid", "evidenceid", "escape", "solution", "blueprint",
            "requiredfact", "requiredaction", "inventory", "entityid", "itemid", "playertext",
        ):
            self.assertNotIn(hidden, text.lower())

    def test_identical_model_input_has_identical_sample_id(self):
        text = v2.feature_text_v2(self.snapshot())
        self.assertEqual(v2.sample_id_v2(text), v2.sample_id_v2(text))
        self.assertEqual(24, len(v2.sample_id_v2(text)))

    def test_history_changes_deployable_v2_input(self):
        first = self.snapshot()
        second = self.snapshot()
        second["history"] = list(second["history"])
        second["history"][-1] = {"actionKind": "EXPLORE", "pressure": "NONE"}
        self.assertNotEqual(v2.feature_text_v2(first), v2.feature_text_v2(second))


if __name__ == "__main__":
    unittest.main()
