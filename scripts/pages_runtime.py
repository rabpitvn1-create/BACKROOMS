"""Resolve Argon's named Secrets into the public runtime approved by the owner."""
import json
import os
from pathlib import Path
import re


def configuration(secrets):
    # Only Argon fields are considered for this provider.
    scoped = {name: str(value).strip() for name, value in secrets.items()
              if name.upper().startswith('ARGON') and value}
    normalized = {re.sub(r'[^A-Z0-9]', '', name.upper()): name for name in scoped}

    def resolve(label, preferred, predicate):
        for alias in preferred:
            if alias in normalized:
                return scoped[normalized[alias]], normalized[alias]
        matches = [name for name in scoped if predicate(re.sub(r'[^A-Z0-9]', '', name.upper()))]
        if len(matches) != 1:
            raise SystemExit('Cannot uniquely resolve Argon ' + label + '. Argon Secret names: ' + ', '.join(sorted(scoped)))
        return scoped[matches[0]], matches[0]

    key, key_name = resolve('API key', ['ARGONAPIKEY'], lambda n: n.endswith('KEY'))
    url, url_name = resolve('base URL', ['ARGONBASEURL', 'ARGONAPIBASEURL', 'ARGONAPIURL', 'ARGONURL'], lambda n: n.endswith('URL'))
    model, model_name = resolve('model', ['ARGONMODEL', 'ARGONMODELNAME', 'ARGONAPIMODEL'], lambda n: 'MODEL' in n)
    print('Argon configuration resolved from Secrets: ' + ', '.join([key_name, url_name, model_name]))
    return {
        'keys': [str(secrets.get(f'GEMINI_API_KEY_{i}', '')).strip() for i in range(1, 7)],
        'argon': {'apiKey': key, 'baseUrl': url, 'model': model},
    }


if __name__ == '__main__':
    config = configuration(json.loads(os.environ['REPOSITORY_SECRETS_JSON']))
    output = Path('build/runtime.json')
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(config))
    output.chmod(0o600)
