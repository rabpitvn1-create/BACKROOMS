#!/usr/bin/env bash
# Never enable shell tracing: the child inherits VILAO_API_KEY.
set +x
set -euo pipefail

repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
ponytail_root="${XDG_DATA_HOME:-$HOME/.local/share}/backrooms-ponytail"
ponytail_cli="$ponytail_root/cli/node_modules/.bin/codex"
ponytail_home="$ponytail_root/codex"

if [[ "${1:-}" == "--setup" ]]; then
  umask 077
  mkdir -p "$ponytail_home"
  npm install --prefix "$ponytail_root/cli" --no-audit --no-fund @openai/codex@0.159.3
  install -m 600 "$repo_root/tools/ponytail-config.toml" "$ponytail_home/config.toml"
  "$ponytail_cli" --version
  exit 0
fi

if [[ -z "${VILAO_API_KEY:-}" ]]; then
  printf '%s\n' 'Missing VILAO_API_KEY. Configure the Codespaces Secret and restart the Codespace.' >&2
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
  # Read-only inference; verify the final answer, not a model-list response.
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
