"""Install production APK/test runner, exercise real SQLite and intentional process kill."""
from pathlib import Path
import hashlib
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
api = sys.argv[1]
if api not in ('24', '35'):
    raise RuntimeError('Unexpected smoke API')
report = ROOT / 'android-apk/app/build/reports/companion-storage' / ('api-' + api)
report.mkdir(parents=True, exist_ok=True)
package = 'com.rabpit.backroom'
component = package + '.test/com.rabpit.backroom.core.companion.CompanionStorageInstrumentation'

def adb(*args, check=True):
    result = subprocess.run(['adb', *args], cwd=ROOT, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=180, check=False)
    if check and result.returncode:
        raise RuntimeError(result.stdout)
    return result

apks = list((ROOT / 'android-apk/app/build/outputs/apk/debug').glob('*.apk'))
tests = list((ROOT / 'android-apk/app/build/outputs/apk/androidTest/debug').glob('*.apk'))
if len(apks) != 1 or len(tests) != 1:
    raise RuntimeError('Expected exactly one app APK and test APK')
if adb('shell', 'getprop', 'ro.build.version.sdk').stdout.strip() != api:
    raise RuntimeError('Emulator API mismatch')
adb('install', '-r', str(apks[0])); adb('install', '-r', str(tests[0]))
outputs = {}
try:
    for mode in ('suite', 'crash', 'recover'):
        result = adb('shell', 'am', 'instrument', '-w', '-e', 'mode', mode, component, check=False)
        outputs[mode] = result.stdout
        (report / (mode + '.log')).write_text(result.stdout)
        print(result.stdout, flush=True)
        if mode == 'crash':
            if 'COMPANION_CRASH_ARMED' not in result.stdout or 'COMPANION_STORAGE_PASS' in result.stdout:
                raise RuntimeError('Intentional process-kill boundary not reached')
            adb('shell', 'am', 'force-stop', package)
        else:
            count = '13' if mode == 'suite' else '1'
            if result.returncode or f'COMPANION_STORAGE_PASS api={api} cases={count}' not in result.stdout or f'OK ({count} tests)' not in result.stdout:
                raise RuntimeError(mode + ' Android storage test failed')
    manifest = {
        'api': int(api), 'source_sha': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'app_sha256': hashlib.sha256(apks[0].read_bytes()).hexdigest(),
        'cases': 14, 'suite': 'PASS', 'process_kill_transaction_rollback': 'PASS',
        'power_loss': 'NOT_TESTED', 'real_device_performance': 'NOT_TESTED',
    }
    (report / 'result.json').write_text(json.dumps(manifest, indent=2) + '\n')
finally:
    (report / 'android-runtime.log').write_text(adb('logcat', '-d', '-s', 'AndroidRuntime', 'SQLiteLog', check=False).stdout)
