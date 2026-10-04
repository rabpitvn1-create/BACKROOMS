#!/usr/bin/env bash
# Never enable shell tracing: the child inherits VILAO_API_KEY.
set +x
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ponytail_root="${XDG_DATA_HOME:-$HOME/.local/share}/backrooms-ponytail"
ponytail_cli="$ponytail_root/cli/node_modules/.bin/codex"
ponytail_home="$ponytail_root/codex"

if [[ "${1:-}" == "--setup" ]]; then
  if ! command -v npm >/dev/null 2>&1; then
    printf '%s\n' 'npm is required for Ponytail setup. Install Node.js/npm, then retry.' >&2
    exit 1
  fi
  umask 077
  mkdir -p "$ponytail_home" "$ponytail_root/cli"
  npm install --prefix "$ponytail_root/cli" --no-audit --no-fund @openai/codex@0.159.3
  install -m 600 "$repo_root/tools/ponytail-config.toml" "$ponytail_home/config.toml"
  "$ponytail_cli" --version
  exit 0
fi

if [[ "${1:-}" == "--env-check" ]]; then
  if [[ -f "$repo_root/.codex/skills/ponytail/SKILL.md" ]]; then
    echo 'Ponytail skill: configured (.codex/skills/ponytail/SKILL.md)'
  else
    echo 'Ponytail skill: missing'
  fi
  if command -v rg >/dev/null 2>&1; then rg --version | head -1; else echo 'ripgrep: unavailable (optional)'; fi
  if command -v java >/dev/null 2>&1; then java -version 2>&1 | head -1; else echo 'Java: unavailable (needed only for Java/Android checks)'; fi
  if command -v gradle >/dev/null 2>&1; then gradle --version | grep '^Gradle ' | head -1; else echo 'Gradle: unavailable (use project/CI tooling when needed)'; fi
  if command -v npm >/dev/null 2>&1; then echo "npm: $(npm --version)"; else echo 'npm: unavailable (needed only for --setup)'; fi
  if [[ -x "$ponytail_cli" ]]; then echo "Codex CLI: $("$ponytail_cli" --version)"; else echo 'Codex CLI: not installed (run --setup)'; fi
  if [[ -n "${VILAO_API_KEY:-}" ]]; then echo 'VILAO_API_KEY: configured'; else echo 'VILAO_API_KEY: unavailable (needed only for Vilao inference)'; fi
  if [[ -d "${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/nonexistent}}" ]]; then
    echo 'Android SDK: configured (build still requires installed platform/build tools)'
  else
    echo 'Android SDK: unavailable; run APK verification on existing game CI'
  fi
  exit 0
fi

if [[ -z "${VILAO_API_KEY:-}" ]]; then
  printf '%s\n' 'Missing VILAO_API_KEY. Export it from your local secret store/session before launching Vilao Ponytail.' >&2
  exit 1
fi
if [[ ! -x "$ponytail_cli" ]]; then
  printf '%s\n' 'Run bash tools/ponytail.sh --setup first.' >&2
  exit 1
fi

umask 077
mkdir -p "$ponytail_home"
install -m 600 "$repo_root/tools/ponytail-config.toml" "$ponytail_home/config.toml"
cd "$repo_root"

if [[ "${1:-}" == "--smoke-test" ]]; then
  result_file="$(mktemp)"
  trap 'rm -f "$result_file"' EXIT
  env CODEX_HOME="$ponytail_home" "$ponytail_cli" exec --ephemeral \
    --sandbox read-only --output-last-message "$result_file" \
    'Do not use tools or change files. Reply exactly PONYTAIL_OK.' </dev/null
  if [[ "$(cat "$result_file")" != "PONYTAIL_OK" ]]; then
    printf '%s\n' 'Ponytail smoke test failed: unexpected final answer.' >&2
    exit 1
  fi
  printf '%s\n' 'Ponytail/Vilao smoke test passed.'
  exit 0
fi

exec env CODEX_HOME="$ponytail_home" "$ponytail_cli" "$@"
