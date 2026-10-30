"""Build the playable Pages game using the recovered browser engine and current keys."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil

ROOT = Path(__file__).resolve().parents[1]
MODELS = ['gemini-3.6-flash', 'gemini-3.5-flash-lite', 'gemini-3.1-flash-lite', 'gemini-2.5-flash-lite']


def build(test_config=False, config_path=None):
    argon=False
    if test_config:
        keys=[f'browser-fixture-key-{i}' for i in range(1,7)]
    elif config_path:
        cfg_in=json.loads(Path(config_path).read_text())
        keys=cfg_in.get('keys',[])
        argon_in=cfg_in.get('argon')
        if isinstance(argon_in,dict):
            akey=str(argon_in.get('key') or '').strip()
            abase=str(argon_in.get('base_url') or '').strip().rstrip('/')
            amodel=str(argon_in.get('model') or '').strip()
            if akey and abase and amodel:
                argon={'key':akey,'base_url':abase,'model':amodel}
    else:
        raise SystemExit('Supply an explicitly approved runtime config file, or --test-config for fixture-only verification.')
    if len(keys)!=6 or len(set(keys))!=6 or not all(isinstance(k,str) and k.strip() for k in keys):
        raise SystemExit('Runtime configuration must contain six distinct configured keys.')
    engine = ROOT / 'engine/target/javascript/game-core.js'
    if not engine.is_file():
        raise SystemExit('Compile the recovered browser engine before staging Pages.')
    output = ROOT / 'build/pages'
    if output.exists(): shutil.rmtree(output)
    shutil.copytree(ROOT / 'web', output)
    runtime = output / 'gameplay'
    runtime.mkdir()
    shutil.copy2(engine, runtime/'game-core.js')
    shutil.copy2(ROOT/'pages/bridge.js', runtime/'bridge.js')
    shutil.copy2(ROOT/'pages/regex.js', runtime/'regex.js')
    assets = {p.relative_to(ROOT/'web').as_posix(): p.read_text() for p in (ROOT/'web/knowledge').glob('*.json')}
    for p in (ROOT/'web/canon').glob('*.md'): assets[p.relative_to(ROOT/'web').as_posix()] = p.read_text()
    (runtime/'assets.js').write_text('window.BACKROOMS_ASSETS='+json.dumps(assets,ensure_ascii=False)+';\n')
    cfg = {'keys':keys, 'models':MODELS, 'version':'1.0.0.0-web', 'argon':argon}
    (runtime/'config.js').write_text('window.BACKROOMS_CONFIG='+json.dumps(cfg)+';\n')
    digest=hashlib.sha256()
    for p in sorted(runtime.glob('*.js')): digest.update(p.read_bytes())
    version=digest.hexdigest()[:16]
    html=(output/'index.html').read_text().replace('<title>Backroom</title>','<title>Backrooms</title>')
    scripts='\n'.join(f'<script src="gameplay/{name}.js?v={version}"></script>' for name in ['assets','config','regex','game-core','bridge'])
    html=html.replace('</head>',scripts+'\n</head>')
    (output/'index.html').write_text(html)
    (output/'.nojekyll').touch()
    (output/'personal-provider-config.json').write_text('{}\n')
    (output/'build.json').write_text(json.dumps({'mode':'playable-web','engine':'recovered-apk-core','version':version,'commit':os.environ.get('GITHUB_SHA'),'primaryKeys':6,'argon':bool(argon)},indent=2))
    print(f'Playable game staged: {version}; recovered core, six primary keys, Argon ' + ('enabled.' if argon else 'disabled.'))
    return output


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--test-config',action='store_true');parser.add_argument('--config')
    args=parser.parse_args();build(args.test_config,args.config)
