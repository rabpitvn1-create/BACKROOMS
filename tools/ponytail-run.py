#!/usr/bin/env python3
"""Run a check once; keep its redacted log outside the repo and return its exit code."""
import collections
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET


def run(command):
    if not command:
        raise SystemExit('Usage: python3 tools/ponytail-run.py COMMAND [ARGS...]')
    root = Path(os.environ.get('XDG_STATE_HOME', str(Path.home() / '.local/state'))) / 'backrooms-ponytail/logs'
    root.mkdir(parents=True, exist_ok=True, mode=0o700)
    fd, name = tempfile.mkstemp(prefix='check-', suffix='.log', dir=root)
    secrets = [v for k, v in os.environ.items() if len(v) >= 8 and re.search('KEY|SECRET|TOKEN|PASSWORD', k, re.I)]
    important = collections.deque(maxlen=20)
    lines = 0
    started = time.time()
    with os.fdopen(fd, 'w') as log:
        try:
            child = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, errors='replace')
        except OSError as exc:
            log.write(type(exc).__name__)
            print('START_FAILED; log:', name)
            return 127
        for line in child.stdout:
            for secret in secrets:
                line = line.replace(secret, '[REDACTED]')
            log.write(line)
            lines += 1
            if re.search('error|fail|exception|tests? (run|passed)|BUILD (SUCCESSFUL|FAILED)|# (tests|pass|fail)', line, re.I):
                important.append(line.rstrip()[:300])
        code = child.wait()
    counts = [0, 0, 0, 0]
    found = 0
    for path in Path('android-apk/app/build/test-results').glob('**/TEST-*.xml'):
        if path.stat().st_mtime < started:
            continue
        try:
            suite = ET.parse(path).getroot()
            for i, key in enumerate(('tests', 'failures', 'errors', 'skipped')):
                counts[i] += int(suite.get(key, 0))
            found += 1
        except (ET.ParseError, ValueError):
            continue
    print('exit_code:', code, '; log_lines:', lines, '; log:', name)
    print('fresh JUnit tests/failures/errors/skipped:', counts if found else 'unavailable')
    for line in important:
        print(line)
    if code and not important:
        print('No recognized error summary; inspect the saved log before retrying.')
    return code


if __name__ == '__main__':
    raise SystemExit(run(sys.argv[1:]))
