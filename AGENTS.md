# Ponytail

## Environment and credentials
- Use Vilao Ponytail in the GitHub Codespace for rabpitvn1-create/BACKROOMS. Reuse the requested branch; fetch and verify remote HEAD before editing. Never discard someone else's work.
- Setup: bash tools/ponytail.sh --setup. Launch: bash tools/ponytail.sh; tasks: bash tools/ponytail.sh exec ... . Check environment with --env-check and verify the provider with --smoke-test before claiming the connection works.
- Default model: vgpt/gpt-6.1-sol; reasoning: medium; provider: vilao; endpoint: https://api.vilao.ai/v1. Do not silently change model/provider or raise reasoning effort. This config selects the launched CLI, not the current ChatGPT assistant.
- Read VILAO_API_KEY only from injected Codespaces Secret. Never commit, print or copy credentials, enable shell tracing, or store a key in TOML/history. Report unavailable Codespaces/Secrets access explicitly; instructions do not grant permissions.
- Inspect, change a few related files, commit, wait for applicable real CI, then continue. No merge, force-push, history deletion, version bump or unrelated gameplay changes without explicit authorization.

## Efficient work
- Understand the task and trace callers before changing code. Fix the shared root cause, not one symptom. Read tools/REPO_MAP.md when locating a module.
- Reuse existing code, standard libraries and native features first. Avoid unnecessary dependencies, abstractions and boilerplate. Prefer the smallest correct diff; preserve security, data validation, error handling and accessibility. Mark deliberate limits with a ponytail: comment naming the ceiling and upgrade path.
- Search the relevant directory first with rg -n --max-count 20 --max-columns 200 PATTERN PATH. Default exclusions live in .rgignore; use --no-ignore only for a specifically needed excluded file. Read selected line ranges or functions, then callers/diff; expand when required for correctness.
- Run test/build through python3 tools/ponytail-run.py COMMAND ARGS... . It keeps the full redacted log outside the repo and preserves the exit code. Inspect the relevant saved log range on failure; do not repeat a command without new evidence.
- Reduce JSON/data/logs locally with scripts before returning results to context. Do not paste entire generated files, logs or the repository. Keep outputs concise but include needed errors and evidence.
- Continue the same session for the same objective. Start a new one when the objective/module changes; hand over only the goal, commit, findings, remaining work and test status. Keep stable instructions unchanged during a task.
- Use only tools/MCP needed for the task. No subagents by default. Prompt/task scope should specify the objective, relevant module and success check.
- Measure recorded usage with python3 tools/ponytail-usage.py ROLLOUT.jsonl. Missing fields are unavailable, never assumed zero; token totals do not establish provider billing or cache behavior.

## Correctness
- For bugs, test/build failures, performance or integration issues, follow skills/systematic-debugging/SKILL.md before fixing.
- Before any completion/passing claim, follow skills/verification-before-completion/SKILL.md and require fresh evidence. Nontrivial logic needs one focused runnable check; trivial one-liners do not need a new test.
