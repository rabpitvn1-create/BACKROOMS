#!/usr/bin/env python3
"""Fail-closed R1 evidence review; never runs tests, builds, or network requests.

This validates evidence structure and consistency, not authenticity of an operator
or device. Hashes detect changes to collected artifacts, not fabricated artifacts.
"""
import argparse
import hashlib
import json
import math
import re
import sys
from pathlib import Path

SHA = re.compile(r"^[0-9a-f]{64}$")
HEAD = re.compile(r"^[0-9a-f]{40}$")
KINDS = {"no_op", "dialogue", "event", "retrieval", "retry", "crash"}
STAGES = {"admission", "context", "provider", "commit", "reload"}
CRASHES = {"before_commit", "after_commit", "ambiguous_readback", "retry", "cross_slot"}
OUTCOMES = {
    "event17_receipt17_no_reroll": {"event_revision": 17, "receipt_revision": 17, "rng_rerolls": 0},
    "actor_private_promise": {"owner_can_read": True, "other_actor_can_read": False, "other_slot_can_read": False},
    "correction_reload": {"superseded_claim_returned": False, "corrected_claim_survives_reload": True},
    "before_commit": {"committed": False, "partial_rows": 0},
    "after_commit": {"committed": True, "duplicate_commits": 0},
    "ambiguous_readback": {"receipt_recovered": True, "duplicate_commits": 0},
    "retry": {"same_receipt": True, "same_rng_tape": True, "duplicate_commits": 0},
    "cross_slot": {"other_slot_mutated": False, "other_slot_readable": False},
    "provider_attempt": {"real_transport": True, "attempt_persisted": True},
    "provider_audit": {"raw_response_retained": True, "request_digest_verified": True},
    "repair": {"repair_attempt_persisted": True, "bounded_attempts": True},
    "fallback": {"fallback_committed": True, "unauthorized_mutations": 0},
    "no_extra_rng": {"rng_rerolls": 0},
    "rollback": {"original_slot_unchanged": True},
    "new_game": {"fresh_slot": True, "old_slot_unchanged": True},
    "cache_preservation": {"authoritative_state_unchanged": True},
    "old_slot_rejected_without_mutation": {"rejected": True, "old_slot_unchanged": True},
    "generated_46_patch_parity": {"patch_count": 46, "source_matches": True},
    "g0_parity": {"source_matches": True},
    "source_provenance": {"source_versions_verified": True},
}


class Review:
    def __init__(self, root):
        self.root = root.resolve()
        self.errors = []
        self.artifacts = {}

    def need(self, condition, reason):
        if not condition:
            self.errors.append(reason)
        return bool(condition)

    def number(self, value):
        return type(value) in (int, float) and math.isfinite(value) and value >= 0

    def artifact(self, name, json_data=True):
        spec = self.artifacts.get(name)
        if not self.need(isinstance(spec, dict), f"missing artifact: {name}"):
            return None
        relative = spec.get("path")
        if not self.need(isinstance(relative, str) and not Path(relative).is_absolute(), f"artifact {name}: relative path required"):
            return None
        path = (self.root / relative).resolve()
        if not self.need(path.is_relative_to(self.root), f"artifact {name}: path escapes evidence directory"):
            return None
        try:
            raw = path.read_bytes()
            if not self.need(SHA.fullmatch(str(spec.get("sha256", ""))) and hashlib.sha256(raw).hexdigest() == spec.get("sha256"), f"artifact {name}: sha256 mismatch"):
                return None
            return json.loads(raw) if json_data else raw
        except (OSError, ValueError) as exc:
            self.errors.append(f"artifact {name}: {exc}")
            return None

    def record(self, record, scope):
        if not self.need(isinstance(record, dict), f"{scope}: record missing"):
            return False
        return True

    def proof(self, name, head, required):
        data = self.artifact(name)
        if not self.record(data, name):
            return
        self.need(data.get("candidate_head") == head, f"{name}: wrong head")
        cases = data.get("cases", [])
        if not self.need(isinstance(cases, list), f"{name}: cases must be array"):
            return
        for case_name in required:
            matches = [c for c in cases if isinstance(c, dict) and c.get("name") == case_name]
            if not self.need(len(matches) == 1, f"{name}: require one case {case_name}"):
                continue
            case = matches[0]
            self.need(case.get("expected") == case.get("actual") and "expected" in case, f"{name}/{case_name}: expected/actual mismatch")
            allowed_source = "build_pipeline" if case_name in {"generated_46_patch_parity", "g0_parity", "source_provenance"} else "native_runtime"
            self.need(case.get("source") == allowed_source, f"{name}/{case_name}: actual {allowed_source} proof required")
            actual = case.get("actual")
            self.need(isinstance(actual, dict) and all(k in actual and type(actual[k]) is type(v) and actual[k] == v for k, v in OUTCOMES[case_name].items()), f"{name}/{case_name}: required observable outcomes missing")
            trace = self.artifact(case.get("trace_artifact"))
            self.need(isinstance(trace, list) and len(trace) > 0 and all(isinstance(row, dict) and row.get("head") == head and row.get("source") == allowed_source and row.get("operation") and row.get("observed") is not None for row in trace), f"{name}/{case_name}: scoped raw operation trace required")

    def trials(self, manifest, head):
        budget = self.artifact(manifest.get("budget_artifact"))
        if not self.record(budget, "budgets"):
            return
        self.need(budget.get("candidate_head") == head, "budgets: wrong candidate head")
        frozen = budget.get("frozen_at_unix_ms")
        self.need(self.number(frozen), "budgets: freeze timestamp missing")
        bounds = budget.get("limits")
        self.need(isinstance(bounds, dict) and bounds, "budgets: numeric limits required")
        trials = manifest.get("trials", [])
        if not self.need(isinstance(trials, list), "trials must be array"):
            return
        for api in (24, 35):
            selected = [t for t in trials if isinstance(t, dict) and t.get("api") == api]
            self.need(len(selected) >= 3, f"API{api}: three independent trials required")
            identities = [t.get("trial_id") for t in selected]
            self.need(all(isinstance(x, str) and x for x in identities) and len(set(identities)) == len(identities), f"API{api}: distinct trial IDs required")
        all_ids = [t.get("trial_id") for t in trials if isinstance(t, dict)]
        self.need(all(isinstance(x, str) and x for x in all_ids) and len(set(all_ids)) == len(all_ids), "trials: global independent trial IDs required")
        parity = None
        for trial in trials:
            if not self.record(trial, "trial"):
                continue
            label = str(trial.get("trial_id", "unnamed"))
            self.need(trial.get("candidate_head") == head, f"{label}: wrong candidate head")
            self.need(trial.get("api") in (24, 35), f"{label}: unsupported API")
            self.need(trial.get("budget_artifact") == manifest.get("budget_artifact"), f"{label}: budget not frozen manifest budget")
            self.need(self.number(trial.get("started_at_unix_ms")) and self.number(frozen) and trial.get("started_at_unix_ms", 0) > (frozen or 0), f"{label}: budget must be frozen before trial")
            config = trial.get("environment")
            self.need(isinstance(config, dict) and all(config.get(k) for k in ("device", "android_build", "sqlite_version", "runtime", "workload_sha256", "apk_sha256")), f"{label}: environment missing")
            if isinstance(config, dict):
                identity = (config.get("workload_sha256"), config.get("apk_sha256"), config.get("runtime"))
                if parity is None:
                    parity = identity
                self.need(identity == parity, f"{label}: workload/APK/runtime parity mismatch")
                self.need(bool(SHA.fullmatch(str(config.get("workload_sha256", "")))), f"{label}: workload digest missing/invalid")
                self.need(config.get("apk_sha256") == manifest.get("apk_sha256"), f"{label}: different APK")
            rows = self.artifact(trial.get("samples_artifact"))
            if not self.need(isinstance(rows, list) and len(rows) >= 1000, f"{label}: 1000 actual raw turn samples required"):
                continue
            seen_kinds, seen_modes = set(), set()
            for expected_turn, row in enumerate(rows, 1):
                if not self.record(row, f"{label} turn{expected_turn}"):
                    continue
                prefix = f"{label} turn{expected_turn}"
                self.need(row.get("turn") == expected_turn, f"{prefix}: missing/duplicate turn")
                self.need(row.get("source") == "native_runtime" and row.get("head") == head, f"{prefix}: actual exact-head source required")
                kind, mode = row.get("kind"), row.get("mode")
                seen_kinds.add(kind); seen_modes.add(mode)
                self.need(kind in KINDS and mode in ("cold", "warm"), f"{prefix}: invalid workload/cache mode")
                self.need(row.get("slot_id") and row.get("request_id") and row.get("receipt_sha256") and row.get("rng_tape_sha256"), f"{prefix}: native identity/receipt/RNG missing")
                for key in ("receipt_sha256", "rng_tape_sha256"):
                    self.need(bool(SHA.fullmatch(str(row.get(key, "")))), f"{prefix}: invalid {key}")
                latency = row.get("latency_ms", {})
                self.need(isinstance(latency, dict) and STAGES <= set(latency) and all(self.number(latency.get(stage)) for stage in STAGES), f"{prefix}: stage latency missing")
                sql = row.get("logical_sql_rows")
                self.need(type(sql) is int and sql >= 0, f"{prefix}: logical SQL rows missing")
                # Physical I/O cannot be inferred from SQL statement/row counters.
                physical = row.get("physical_io")
                self.need(isinstance(physical, dict) and physical.get("status") in ("measured", "unavailable"), f"{prefix}: physical I/O status missing")
                if isinstance(physical, dict) and physical.get("status") == "measured":
                    self.need(physical.get("method") and self.number(physical.get("read_bytes")) and self.number(physical.get("write_bytes")), f"{prefix}: physical I/O measurement incomplete")
                for key in ("unauthorized_mutations", "cross_actor_leaks", "cross_slot_leaks", "duplicate_commits", "partial_batches", "rng_rerolls"):
                    self.need(type(row.get(key)) is int and row[key] == 0, f"{prefix}: {key} must be observed zero")
                if isinstance(bounds, dict):
                    values = {"logical_sql_rows": sql, **({f"{k}_ms": v for k,v in latency.items()} if isinstance(latency, dict) else {})}
                    for metric, limit in bounds.items():
                        self.need(self.number(limit) and self.number(values.get(metric)) and values[metric] <= limit, f"{prefix}: frozen limit failed/missing {metric}")
            self.need(KINDS <= seen_kinds and {"cold", "warm"} <= seen_modes, f"{label}: mixed workload and cold/warm required")
            checkpoints = self.artifact(trial.get("checkpoints_artifact"))
            if self.record(checkpoints, f"{label} checkpoints"):
                for milestone in (500, 1000):
                    self.proof(checkpoints.get(str(milestone)), head, {"event17_receipt17_no_reroll", "actor_private_promise", "correction_reload"})
            self.proof(trial.get("crash_artifact"), head, CRASHES)

    def run(self, manifest):
        if not self.record(manifest, "manifest"):
            return
        head = manifest.get("candidate_head")
        self.need(bool(HEAD.fullmatch(str(head))), "candidate_head: full git SHA required")
        self.need(manifest.get("schema") == "companion-r1-evidence-v2", "unsupported evidence schema")
        self.artifacts = manifest.get("artifacts", {})
        if not self.need(isinstance(self.artifacts, dict), "artifacts must be object"):
            self.artifacts = {}
        apk = self.artifact(manifest.get("apk_artifact"), False)
        self.need(apk is not None and hashlib.sha256(apk).hexdigest() == manifest.get("apk_sha256"), "candidate APK digest mismatch")
        self.trials(manifest, head)
        # A backend WAIT pilot can collect useful metrics but cannot qualify Muse.
        self.need(manifest.get("runtime_scope") == "integrated_companion", "backend WAIT/helper-only evidence cannot qualify integrated companion runtime")
        self.proof(manifest.get("provider_artifact"), head, {"provider_attempt", "provider_audit", "repair", "fallback", "no_extra_rng"})
        self.proof(manifest.get("lifecycle_artifact"), head, {"rollback", "new_game", "cache_preservation", "old_slot_rejected_without_mutation"})
        human = self.artifact(manifest.get("human_playtest_artifact"))
        if self.record(human, "human playtest"):
            self.need(human.get("candidate_head") == head and human.get("reviewer") and human.get("played_at"), "human playtest: reviewer/time/exact head required")
            observations = human.get("observations", [])
            self.need(isinstance(observations, list) and {"voice", "agency"} <= {x.get("category") for x in observations if isinstance(x, dict)}, "human playtest: voice and agency observations required")
            self.need(isinstance(observations, list) and all(isinstance(x, dict) and x.get("scenario") and x.get("notes") and x.get("accepted") is True for x in observations), "human playtest: scenario, notes and explicit acceptance required")
        self.proof(manifest.get("build_artifact"), head, {"generated_46_patch_parity", "g0_parity", "source_provenance"})
        ci = self.artifact(manifest.get("ci_artifact"))
        if self.record(ci, "CI"):
            self.need(ci.get("candidate_head") == head, "CI: wrong head")
            required = ci.get("applicable_workflows")
            runs = ci.get("runs")
            self.need(isinstance(required, list) and required and len(set(required)) == len(required), "CI: applicable workflow inventory required")
            self.need(isinstance(runs, list) and runs, "CI: actual run records required")
            if isinstance(required, list) and isinstance(runs, list):
                for workflow in required:
                    valid = [r for r in runs if isinstance(r, dict) and r.get("workflow") == workflow and r.get("head_sha") == head and r.get("status") == "completed" and r.get("conclusion") == "success" and r.get("url") and r.get("run_id") and r.get("jobs")]
                    self.need(bool(valid), f"CI: no successful exact-head run for {workflow}")
                    for run in valid:
                        self.need(all(isinstance(j, dict) and j.get("conclusion") == "success" and j.get("url") for j in run["jobs"]), f"CI: unsuccessful/missing jobs for {workflow}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--output", type=Path, help="write the evidence review result; never overwrite inputs")
    args = parser.parse_args()
    review = Review(args.manifest.parent)
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        review.run(manifest)
    except (OSError, ValueError, TypeError, KeyError) as exc:
        review.errors.append(f"invalid/incomplete evidence: {exc}")
    result = {"status": "NOT_QUALIFIED" if review.errors else "EVIDENCE_COMPLETE", "reasons": review.errors, "release_approved": False, "authenticity": "operator review required; hashes and schema are not device attestation"}
    rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        if args.output.resolve() == args.manifest.resolve():
            parser.error("output cannot overwrite manifest")
        if args.output.exists():
            parser.error("output already exists; choose a new result path")
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered, end="")
    return 1 if review.errors else 0


if __name__ == "__main__":
    sys.exit(main())
