"""Build the playable Pages game using the recovered browser engine.

API keys are NOT embedded in the public build. The game talks to a
Cloudflare Worker proxy (which holds the real keys as Worker secrets),
so this script stages a keyless public config.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import cao_minh_assets

ROOT = Path(__file__).resolve().parents[1]
MODELS = ['gemini-3.5-flash-lite', 'gemini-3.1-flash-lite']


def build(test_config=False, config_path=None):
    # Legacy flags kept for workflow compatibility. Any keys/argon material
    # found in an input config file is deliberately IGNORED and never
    # embedded in the public output.
    if test_config:
        pass
    elif config_path:
        cfg_in = json.loads(Path(config_path).read_text())
        # Accepted but ignored: 'keys', 'argon'. The public build is keyless.
        _ = cfg_in
    else:
        raise SystemExit('Supply an explicitly approved runtime config file, or --test-config for fixture-only verification.')
    engine = ROOT / 'engine/target/javascript/game-core.js'
    if not engine.is_file():
        raise SystemExit('Compile the recovered browser engine before staging Pages.')
    output = ROOT / 'build/pages'
    if output.exists(): shutil.rmtree(output)
    shutil.copytree(ROOT / 'web', output)
    asset_db = cao_minh_assets.load_database()
    (output / 'cao-minh-asset-db.js').write_text(cao_minh_assets.registry_script(asset_db), encoding='utf-8')
    runtime = output / 'gameplay'
    runtime.mkdir()
    shutil.copy2(engine, runtime/'game-core.js')
    shutil.copy2(ROOT/'pages/bridge.js', runtime/'bridge.js')
    shutil.copy2(ROOT/'pages/regex.js', runtime/'regex.js')
    assets = {p.relative_to(ROOT/'web').as_posix(): p.read_text() for p in (ROOT/'web/knowledge').glob('*.json')}
    for p in (ROOT/'web/canon').glob('*.md'): assets[p.relative_to(ROOT/'web').as_posix()] = p.read_text()
    (runtime/'assets.js').write_text('window.BACKROOMS_ASSETS='+json.dumps(assets,ensure_ascii=False)+';\n')
    cfg = {'models':MODELS, 'version':'1.0.0.0-web'}
    (runtime/'config.js').write_text('window.BACKROOMS_CONFIG='+json.dumps(cfg)+';\n')
    digest=hashlib.sha256()
    for p in sorted(runtime.glob('*.js')): digest.update(p.read_bytes())
    version=digest.hexdigest()[:16]
    html=(output/'index.html').read_text().replace('<title>Backroom</title>','<title>Backrooms</title>')
    scripts='\n'.join(f'<script src="gameplay/{name}.js?v={version}"></script>' for name in ['assets','config','regex','game-core','bridge'])
    html=html.replace('</head>',scripts+'\n</head>')
    html=cao_minh_assets.inject_scripts(html)
    (output/'index.html').write_text(html)
    (output/'.nojekyll').touch()
    (output/'personal-provider-config.json').write_text('{}\n')
    (output/'build.json').write_text(json.dumps({'mode':'playable-web','engine':'recovered-apk-core','version':version,'commit':os.environ.get('GITHUB_SHA')},indent=2))
    # Pre-cache the static Pages shell, with no binary image files in Git.
    shell = ['./' + p.relative_to(output).as_posix() for p in sorted(output.rglob('*'))
             if p.is_file() and p.name != 'service-worker.js']
    worker = output / 'service-worker.js'
    header = ('self.__BACKROOMS_PRECACHE=' + json.dumps(shell) + ';\n' +
              'self.__BACKROOMS_SHELL_VERSION=' + json.dumps(
                  hashlib.sha256(
                      (version + cao_minh_assets.registry_script(asset_db)).encode() +
                      (output / 'index.html').read_bytes() +
                      (output / 'phone.html').read_bytes() +
                      (output / 'web-mobile-only.css').read_bytes()
                  ).hexdigest()[:16]
              ) + ';\n')
    worker.write_text(header + worker.read_text(encoding='utf-8'), encoding='utf-8')
    print(f'Playable game staged: {version}; recovered core, keyless public config (keys live on the API proxy).')
    return output


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--test-config',action='store_true');parser.add_argument('--config')
    args=parser.parse_args();build(args.test_config,args.config)
