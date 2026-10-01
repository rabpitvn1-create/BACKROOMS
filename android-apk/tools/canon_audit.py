#!/usr/bin/env python3
"""Read-only canon registry/content audit. Reports conflicts; never resolves lore."""
import argparse
import graphlib
import json
import re
from pathlib import Path, PurePosixPath

ROOTS = {'world', 'levels', 'sublevels', 'entities', 'items', 'factions', 'phenomena',
         'characters', 'history', 'wiki', 'codex', 'continuity'}
TYPES = {'WORLD', 'CHARACTER', 'HISTORY', 'ENVIRONMENT', 'ENTITY_REFERENCE'}
AUTHORITIES = {'PROJECT_OVERRIDE', 'WORLD_CANON', 'CHARACTER_CANON',
               'SCOPED_USER_RETCON', 'REFERENCE', 'UNCLASSIFIED'}
STATUSES = {'CURRENT', 'CANDIDATE', 'REFERENCE', 'UNCLASSIFIED'}
FIELDS = {'id', 'path', 'contentPath', 'type', 'authority', 'status', 'version', 'owner',
          'dependencies', 'mandatoryFor', 'supersedes', 'note'}


def audit(assets, registry=None):
    assets = Path(assets).resolve()
    if registry is None:
        registry = json.loads((assets / 'canon/canon-registry.json').read_text(encoding='utf-8'))
    errors, sources, by_id, graph = [], [], {}, {}
    seen = {'path': set(), 'contentPath': set()}
    if not isinstance(registry, dict) or registry.get('schemaVersion') != 1:
        return {'errors': ['schema_version_invalid']}
    if set(registry) - {'schemaVersion', 'sources'}:
        errors.append('unknown_registry_field')
    if not isinstance(registry.get('sources'), list):
        return {'errors': ['sources_invalid']}
    for index, source in enumerate(registry['sources']):
        if not isinstance(source, dict):
            errors.append(f'source_invalid:{index}')
            continue
        sid = source.get('id', '')
        if not isinstance(sid, str) or not re.fullmatch(r'[a-z0-9][a-z0-9._-]*', sid):
            errors.append(f'id_invalid:{index}')
            continue
        if sid in by_id:
            errors.append(f'id_duplicate:{sid}')
        by_id[sid] = source
        sources.append(source)
        if set(source) - FIELDS:
            errors.append(f'unknown_source_field:{sid}')
        for key, allowed in [('type', TYPES), ('authority', AUTHORITIES), ('status', STATUSES)]:
            if not isinstance(source.get(key), str) or source[key] not in allowed:
                errors.append(f'{key}_invalid:{sid}')
        for key in ['version', 'owner', 'note']:
            if not isinstance(source.get(key), str):
                errors.append(f'{key}_invalid:{sid}')
        if isinstance(source.get('authority'), str) and source['authority'] in {'CHARACTER_CANON', 'SCOPED_USER_RETCON'} and not source.get('owner'):
            errors.append(f'owner_required:{sid}')
        for key in seen:
            value = source.get(key)
            if not isinstance(value, str) or not value:
                errors.append(f'{key}_invalid:{sid}')
                continue
            if value in seen[key]:
                errors.append(f'{key}_duplicate:{sid}')
            seen[key].add(value)
        legacy, physical = source.get('path', ''), source.get('contentPath', '')
        if not isinstance(legacy, str) or not legacy.endswith('.md') or '/' in legacy or '\\' in legacy:
            errors.append(f'path_invalid:{sid}')
        valid_path = isinstance(physical, str) and physical.endswith('.md') and physical == physical.strip()
        if valid_path:
            parts = PurePosixPath(physical).parts
            valid_path = len(parts) >= 2 and parts[0] in ROOTS and not physical.startswith('/') \
                and '\\' not in physical and '..' not in physical and '//' not in physical
        if not valid_path:
            errors.append(f'content_path_invalid:{sid}')
        else:
            target = (assets / 'content' / physical).resolve()
            if not target.is_relative_to(assets / 'content'):
                errors.append(f'content_path_escape:{sid}')
            elif not target.is_file():
                errors.append(f'physical_source_missing:{sid}:{physical}')
        for key in ['dependencies', 'supersedes', 'mandatoryFor']:
            values = source.get(key)
            if not isinstance(values, list) or any(not isinstance(x, str) or not x.strip() for x in values):
                errors.append(f'{key}_invalid:{sid}')
            elif len(values) != len(set(values)):
                errors.append(f'{key}_duplicate:{sid}')
        deps = source.get('dependencies', [])
        graph[sid] = deps if isinstance(deps, list) and all(isinstance(x, str) for x in deps) else []
    for source in sources:
        for key in ['dependencies', 'supersedes']:
            refs = source.get(key, [])
            for target in refs if isinstance(refs, list) else []:
                if not isinstance(target, str):
                    continue
                if target == source['id']:
                    errors.append(f'{key}_self_reference:{target}')
                elif target not in by_id:
                    errors.append(f'{key}_missing:{source["id"]}->{target}')
    try:
        graphlib.TopologicalSorter(graph).prepare()
    except graphlib.CycleError:
        errors.append('dependency_cycle')
    return {
        'errors': sorted(set(errors)), 'sourceCount': len(sources),
        'authorityStatus': [{k: s.get(k, '') for k in ['id', 'path', 'contentPath', 'type',
                             'authority', 'status', 'version', 'owner']}
                            for s in sorted(sources, key=lambda s: s['id'])],
        'dependencies': {s['id']: s.get('dependencies', []) for s in sorted(sources, key=lambda s: s['id'])},
        'supersedes': {s['id']: s.get('supersedes', []) for s in sorted(sources, key=lambda s: s['id'])},
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--assets', type=Path, default=Path(__file__).resolve().parents[1] / 'app/src/main/assets')
    args = parser.parse_args()
    try:
        report = audit(args.assets)
    except (OSError, ValueError) as error:
        report = {'errors': [f'audit_input_invalid:{type(error).__name__}']}
    print(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2))
    return bool(report['errors'])


if __name__ == '__main__':
    raise SystemExit(main())
