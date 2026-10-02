#!/usr/bin/env python3
"""Read numerical rollout usage only; never emit prompts, commands or source."""
import collections
import json
from pathlib import Path
import sys


def analyze(path):
    rows = []
    events = collections.Counter()
    previous = None
    with Path(path).open() as stream:
        for line in stream:
            event = json.loads(line)
            payload = event.get('payload') or {}
            kind = payload.get('type', event.get('type', 'other'))
            if event.get('type') == 'response_item' and kind == 'function_call':
                events['tool_calls'] += 1
                if payload.get('name') == 'spawn_agent':
                    events['subagent_calls'] += 1
            if kind in ('error', 'retry', 'compacted', 'context_compacted'):
                events[kind] += 1
            info = payload.get('info') or {}
            usage = info.get('last_token_usage')
            if kind != 'token_count' or not usage:
                continue
            snapshot = info.get('total_token_usage')
            if snapshot is not None and snapshot == previous:
                continue
            previous = snapshot
            row = {k: usage.get(k) for k in ('input_tokens', 'cached_input_tokens', 'cache_write_tokens', 'output_tokens', 'total_tokens')}
            row['context_window'] = info.get('model_context_window')
            row['uncached_input_tokens'] = (row['input_tokens'] - row['cached_input_tokens'] - row['cache_write_tokens']) if all(row[k] is not None for k in ('input_tokens', 'cached_input_tokens', 'cache_write_tokens')) else None
            rows.append(row)
    def metrics(group):
        if not group:
            return None
        result = {'requests': len(group)}
        for key in rows[0]:
            values = [r[key] for r in group]
            result[key + '_avg'] = sum(values) / len(values) if all(v is not None for v in values) else None
        return result
    result = {'usage_records': len(rows), 'recorded_events': dict(events), 'all': metrics(rows)}
    if len(rows) >= 40:
        result.update(first20=metrics(rows[:20]), middle=metrics(rows[20:-20]), last20=metrics(rows[-20:]))
        first = result['first20']['total_tokens_avg']
        last = result['last20']['total_tokens_avg']
        result['first_to_last_total_percent'] = (last / first - 1) * 100 if first and last is not None else None
    if rows:
        total = sum(r['input_tokens'] or 0 for r in rows)
        for field in ('cached_input_tokens', 'cache_write_tokens', 'uncached_input_tokens'):
            result[field + '_percent'] = 100 * sum(r[field] for r in rows) / total if total and all(r[field] is not None for r in rows) else None
    result['note'] = 'null = unavailable; context_window is capacity, not actual context size. Event counts cover explicit events only. Token counts are not provider billing.'
    return result


if __name__ == '__main__':
    if len(sys.argv) != 2:
        raise SystemExit('Usage: python3 tools/ponytail-usage.py ROLLOUT.jsonl')
    print(json.dumps(analyze(sys.argv[1]), indent=2))
