"""Available-environment benchmark: native test adapter, existing Core, real Android SQLite."""
from pathlib import Path
import json
import subprocess
import sys

root=Path(__file__).resolve().parent.parent
api=sys.argv[1]
turns=int(sys.argv[2])
if api not in ('24','35') or turns not in (1000,5000,10000):
    raise RuntimeError('benchmark arguments invalid')
report=root/'android-apk/app/build/reports/companion-storage'/('api-'+api)
report.mkdir(parents=True,exist_ok=True)
component='com.rabpit.backroom.test/com.rabpit.backroom.core.companion.CompanionStorageInstrumentation'
# APKs are installed by the preceding production storage smoke.
if subprocess.check_output(['adb','shell','getprop','ro.build.version.sdk'],text=True).strip()!=api:
    raise RuntimeError('benchmark API mismatch')
result=subprocess.run(['adb','shell','am','instrument','-w','-e','mode','benchmark','-e','turns',str(turns),component],
    cwd=root,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=5400,check=False)
(report/('benchmark-'+str(turns)+'.log')).write_text(result.stdout)
print(result.stdout,flush=True)
rows=[]
reloads=[]
for line in result.stdout.splitlines():
    if line.startswith('COMPANION_BENCH_RESULT='):
        rows.append(json.loads(line.split('=',1)[1]))
    if line.startswith('COMPANION_BENCH_RELOAD='):
        reloads.append(json.loads(line.split('=',1)[1]))
expected=[n for n in (1000,5000,10000) if n<=turns]
if result.returncode or f'COMPANION_STORAGE_PASS api={api} cases=1' not in result.stdout or 'OK (1 tests)' not in result.stdout:
    raise RuntimeError('native backend benchmark failed')
if [r['turns'] for r in rows]!=expected or len(reloads)!=1 or reloads[0]['turns']!=turns:
    raise RuntimeError('benchmark milestones/reload missing')
if any(r['physicalDevice'] or r['oldEvent17']!='PASS' or r['oldReceipt17']!='PASS' for r in rows):
    raise RuntimeError('benchmark evidence invalid')
manifest={'source_sha':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
    'api':int(api),'milestones':rows,'reload':reloads[0],'status':'PASS'}
(report/('benchmark-'+str(turns)+'.json')).write_text(json.dumps(manifest,indent=2)+'\n')
