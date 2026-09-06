#!/usr/bin/env python3
"""Ask the configured Claude provider for one tightly-scoped Snapshot visual patch.

The model is a code author, not an executor. It receives an allowlisted repository
context and must return a unified diff. GitHub Actions validates and applies that
diff, then the normal compiler/tests remain the authority.
"""
from __future__ import annotations

import argparse
import json
import os
import pathlib
import urllib.error
import urllib.request

DEFAULT_MODEL = "claude-fable-5"

CONTEXT_FILES = [
    "android-apk/app/src/main/java/com/rabpit/backroom/core/CombatCore.kt",
    "android-apk/app/src/test/java/com/rabpit/backroom/core/CombatCoreTest.kt",
    "android-apk/patch-auto-turn-combat-final.py",
    "android-apk/patch-combat-runtime-ux-final.py",
    "android-apk/patch-snapshot-visual-runtime-v3.py",
    "android-apk/patch-kai-hd-continuous.py",
    ".github/workflows/build-backroom-apk.yml",
]

TASK = r"""
You are fixing the BACKROOMS Android Snapshot presentation. Return ONLY one git
unified diff beginning with `diff --git`. Do not wrap it in Markdown. Do not
return shell commands, prose, or a plan.

The user has locked these visual invariants:

1. Every playable Character is ALWAYS on the RIGHT side of the Snapshot, both
   idle and while acting in combat.
2. Character positioning should preserve the old idle composition. The existing
   historical/base rule in patch-kai-hd-continuous.py uses right:0, bottom:0,
   height:97%, max-width:55%, object-position:right bottom. Preserve that visual
   baseline instead of inventing a new center anchor.
3. Every hostile Entity is ALWAYS on the LEFT side, both idle and while acting.
   It should be close to the left edge but no visible sprite pixels may be
   clipped. Use alpha-derived visible bounds rather than the transparent PNG box
   when needed.
4. Characters and Entities share exactly one ground/contact line. Their visible
   feet/contact points must land on that line regardless of transparent padding,
   sprite dimensions, or future new sprite assets.
5. The layout must generalize to future Characters/Entities without per-asset
   hand tuning or retraining. Extend deterministic build-time alpha profiles with
   any visible bounds / contact / hit-anchor fields needed.

Verified root causes that must be fixed, not papered over:

- AutoTurnCombatEngine currently emits FOCUS before party actions but does not
  emit an authoritative FOCUS/turn event before the Entity counterattack. The
  renderer therefore cannot know that the Entity owns that turn. Emit an
  explicit authoritative focus/turn event for EVERY acting combatant, including
  the Entity before its attack or skipped/stunned action. Preserve all combat
  math, RNG, damage, growth, status behavior, queue behavior and authority.
- applyVisualFocus() switches Kai to kai_snapshot_overlay_combat.png when Kai is
  active but does not explicitly restore the idle sprite when focus leaves Kai.
  Do not leave combat pose/src latched across later turns.
- Current staging/facing/focus/entry/hit code layers multiple transform/scale
  effects. Make pose/facing/staging deterministic so focus changes and hit
  animation cannot permanently corrupt layout. Prefer the smallest compatible
  change and keep current event-driven presentation.
- COMBAT_END / finish/reset must restore a clean idle visual state with Character
  still on the RIGHT. A hidden or stale Entity DOM node must not force the player
  into encounter staging after combat ends.
- ENTITY_DOWN and ENTITY_ENTER remain the sole source of Entity rotation.
- ATTACK/SKILL targetId remains the sole source of hit target selection. Do not
  use ML or visual guessing to choose turns, actors, targets, damage or outcomes.

Required observable sequence for a normal round:

FOCUS actor=party_character enemy=current_entity
ATTACK/SKILL party_character -> current_entity
FOCUS actor=current_entity target=party_character enemy=current_entity
ATTACK/SKILL current_entity -> party_character
FOCUS actor=next_party_character enemy=current_entity
...

If the Entity is stunned, the Entity FOCUS event still occurs before its STATUS
skip event so the presentation visibly rotates to the Entity's turn.

Tests are mandatory. Extend CombatCoreTest with focused regression assertions for
party -> Entity -> next party ordering and exact actor/target/enemy IDs. Add
source-level contract guards in the existing finalizer where useful so later
patch-chain changes cannot silently reverse right/left placement, pose reset,
Entity rotation, or hit target mapping.

Allowed files to modify:
- android-apk/app/src/main/java/com/rabpit/backroom/core/CombatCore.kt
- android-apk/app/src/test/java/com/rabpit/backroom/core/CombatCoreTest.kt
- android-apk/patch-auto-turn-combat-final.py
- android-apk/patch-combat-runtime-ux-final.py
- android-apk/patch-snapshot-visual-runtime-v3.py
- android-apk/patch-kai-hd-continuous.py

Do NOT modify GitHub workflows, dependency manifests, API/provider code, Gradle
configuration, inventory, item code, save schema, combat formulas, or unrelated
files. Do not add dependencies. Preserve backward compatibility.
"""


def build_prompt() -> str:
    chunks = [TASK.strip(), "\n\nCURRENT REPOSITORY CONTEXT:\n"]
    for raw in CONTEXT_FILES:
        path = pathlib.Path(raw)
        if not path.is_file():
            raise SystemExit(f"missing context file: {raw}")
        text = path.read_text(encoding="utf-8")
        chunks.append(f"\n===== FILE: {raw} =====\n{text}\n===== END FILE =====\n")
    return "".join(chunks)


def extract_text(payload: object) -> str:
    if not isinstance(payload, dict):
        raise RuntimeError("Claude response root is not an object")

    # ViLao is OpenAI-compatible for normal runtime traffic.
    choices = payload.get("choices")
    if isinstance(choices, list) and choices:
        first = choices[0]
        if isinstance(first, dict):
            message = first.get("message")
            if isinstance(message, dict):
                content = message.get("content")
                if isinstance(content, str) and content.strip():
                    return content.strip()
                if isinstance(content, list):
                    texts = [
                        block.get("text", "")
                        for block in content
                        if isinstance(block, dict) and isinstance(block.get("text"), str)
                    ]
                    joined = "\n".join(texts).strip()
                    if joined:
                        return joined

    # Keep Anthropic-shaped responses as a compatibility fallback.
    content = payload.get("content")
    if isinstance(content, list):
        texts: list[str] = []
        for block in content:
            if isinstance(block, dict) and block.get("type") == "text" and isinstance(block.get("text"), str):
                texts.append(block["text"])
        joined = "\n".join(texts).strip()
        if joined:
            return joined

    raise RuntimeError("Claude response contained no usable text")


def normalize_patch(text: str) -> str:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        lines = cleaned.splitlines()
        lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        cleaned = "\n".join(lines).strip()
    start = cleaned.find("diff --git ")
    if start < 0:
        raise RuntimeError("Claude did not return a unified git diff")
    cleaned = cleaned[start:]
    if "\n+++ b/.github/" in cleaned or "\n--- a/.github/" in cleaned:
        raise RuntimeError("Claude attempted to modify GitHub workflow files")
    return cleaned.rstrip() + "\n"


def chat_completions_url(base_url: str) -> str:
    base = base_url.strip().rstrip("/")
    if not base.startswith(("https://", "http://")):
        raise RuntimeError("CLAUDE_BASE_URL must be an http(s) URL")
    for suffix in ("/chat/completions", "/messages"):
        if base.endswith(suffix):
            base = base[: -len(suffix)]
            break
    return base + "/chat/completions"


def call_claude(api_key: str, base_url: str, model: str, timeout: int) -> str:
    body = {
        "model": model,
        "messages": [
            {
                "role": "system",
                "content": "Return only the requested unified git diff. Never return prose, markdown fences, or shell commands.",
            },
            {"role": "user", "content": build_prompt()},
        ],
        "temperature": 0.1,
        "max_tokens": 24000,
        "stream": False,
    }
    request = urllib.request.Request(
        chat_completions_url(base_url),
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={
            "content-type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:1200]
        raise RuntimeError(f"Claude provider HTTP {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"Claude provider connection failed: {error.reason}") from error
    payload = json.loads(raw)
    return normalize_patch(extract_text(payload))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default=os.environ.get("CLAUDE_MODEL", DEFAULT_MODEL))
    parser.add_argument("--base-url", default=os.environ.get("CLAUDE_BASE_URL", ""))
    parser.add_argument("--timeout", type=int, default=300)
    parser.add_argument("--output", default="/tmp/claude_snapshot.patch")
    args = parser.parse_args()

    key = os.environ.get("CLAUDE_API", "").strip()
    if not key:
        raise SystemExit("CLAUDE_API is required")
    if not args.base_url.strip():
        raise SystemExit("CLAUDE_BASE_URL is required")

    patch = call_claude(key, args.base_url, args.model, args.timeout)
    output = pathlib.Path(args.output)
    output.write_text(patch, encoding="utf-8")
    print(json.dumps({"model": args.model, "patch_lines": len(patch.splitlines())}, sort_keys=True))


if __name__ == "__main__":
    main()
