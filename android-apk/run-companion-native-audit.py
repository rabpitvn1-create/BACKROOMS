#!/usr/bin/env python3
"""One backend audit batch: pilot300, freeze, three fresh-process1000 trials.

Requires installed exact-head APK/test APK and an API24/35 emulator. This is
BACKEND_WAIT_ONLY evidence, never an integrated R1 qualification result.
"""
import argparse
import hashlib
import json
import math
import subprocess
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COMPONENT = 'com.rabpit.backroom.test/com.rabpit.backroom.core.companion.CompanionStorageInstrumentation'
STAGES = ('context_decode', 'admit', 'lock', 'reserve', 'prepare', 'commit', 'receipt_retry')


def command(*args):
    return subprocess.check_output(args, cwd=ROOT, text=True, timeout=5400)


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def save(path, value):
    path.write_text(json.dumps(value, indent=2, allow_nan=False) + '\n')


def decode(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError('duplicate JSON key: ' + key)
            result[key] = value
        return result
    return json.loads(raw, object_pairs_hook=pairs,
                      parse_constant=lambda x: (_ for _ in ()).throw(ValueError(x)))


def p95(values):
    ordered = sorted(values)
    return ordered[math.ceil(.95 * len(ordered)) - 1]


def validate(data, head, trial_id, api, turns):
    assert data['version'] == 'native_backend_audit.v2'
    assert data['scope'] == 'BACKEND_WAIT_ONLY'
    assert data['source_sha'] == head and data['trial_id'] == trial_id
    assert data['api'] == api and data['actual_turns'] == turns
    samples = data['samples']
    assert [s['turn'] for s in samples] == list(range(1, turns + 1))
    for sample in samples:
        assert set(sample['stages_ns']) == set(STAGES)
        for value in list(sample['stages_ns'].values()) + [sample['logical_rows_changed'], sample['rng_draws']]:
            assert type(value) is int and value >= 0
        assert sample['rng_draws'] == 5
        for value in sample['sidecars_bytes'].values():
            assert type(value) is int and value >= 0
    expected = [300] if turns == 300 else [500, 1000]
    assert [m['turn'] for m in data['milestones']] == expected
    for milestone in data['milestones']:
        assert milestone['old_event17'] == 'PASS' and milestone['old_receipt17'] == 'PASS'
        assert milestone['owner_evidence'] == 'EMPTY_EVIDENCE_ONLY'
    assert data['physical_io'] == 'NOT_MEASURED'
    assert data['sql_statement_count'] == 'NOT_MEASURED'
    assert data['reopen_verified'] is True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--api', type=int, choices=(24, 35), required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=False)
    head = command('git', 'rev-parse', 'HEAD').strip()
    assert len(head) == 40
    assert int(command('adb', 'shell', 'getprop', 'ro.build.version.sdk').strip()) == args.api
    # Install the exact files we hash, rather than trusting whatever is on device.
    command('adb', 'install', '-r', str(args.apk.resolve()))
    command('adb', 'install', '-r', str(args.test_apk.resolve()))
    environment = {name: command('adb', 'shell', 'getprop', name).strip() for name in (
        'ro.build.fingerprint', 'ro.product.cpu.abi', 'ro.product.model', 'ro.kernel.qemu')}
    assert environment['ro.kernel.qemu'] == '1', 'emulator-only audit policy'
    environment.update(api=args.api, apk_sha256=digest(args.apk), test_apk_sha256=digest(args.test_apk),
                       emulator_instance=command('adb', 'get-serialno').strip())
    artifacts = []
    parity = None

    def run(label, turns):
        nonlocal parity
        trial_id = 'api%d-%s' % (args.api, label)
        command('adb', 'shell', 'am', 'force-stop', 'com.rabpit.backroom')
        started = time.time_ns() // 1000000
        log = command('adb', 'shell', 'am', 'instrument', '-w', '-e', 'mode', 'native_audit',
                      '-e', 'candidate_sha', head, '-e', 'trial_id', trial_id,
                      '-e', 'turns', str(turns), COMPONENT)
        (output / (trial_id + '.log')).write_text(log)
        assert 'COMPANION_STORAGE_PASS api=%d cases=1' % args.api in log and 'OK (1 tests)' in log
        raw = command('adb', 'exec-out', 'run-as', 'com.rabpit.backroom', 'cat',
                      'files/companion_audit/' + trial_id + '.json')
        path = output / (trial_id + '.json')
        path.write_text(raw)
        data = decode(raw)
        validate(data, head, trial_id, args.api, turns)
        current_parity = (data['workload_sha256'], data['environment']['sqlite_version'],
                          data['environment']['android_build'])
        if parity is None:
            parity = current_parity
        assert current_parity == parity, 'pilot/trial workload or environment drift'
        descriptor = dict(path=path.name, sha256=digest(path), started_at_unix_ms=started,
                          cold_semantics='fresh app process; OS/page cache not cleared', environment=environment)
        artifacts.append(descriptor)
        return data, descriptor

    pilot, pilot_ref = run('pilot300', 300)
    # Fixed rule committed before measurement. Never enlarge after a failed trial.
    limits = {stage: dict(warm_p95_ns=max(5000000, 2 * p95([s['stages_ns'][stage] for s in pilot['samples'][20:]])),
                          first_turn_ns=max(10000000, 2 * pilot['samples'][0]['stages_ns'][stage])) for stage in STAGES}
    row_limit = 2 * max(s['logical_rows_changed'] for s in pilot['samples'])
    total_limit = max(10000000, 2 * p95([s['turn_elapsed_ns'] for s in pilot['samples'][20:]]))
    budget = dict(version='native_qa_budgets.v2', scope='BACKEND_WAIT_ONLY', source_sha=head,
                  frozen_at_unix_ms=time.time_ns() // 1000000, environment=environment,
                  workload_sha256=pilot['workload_sha256'], sqlite_version=pilot['environment']['sqlite_version'],
                  pilot_artifact=pilot_ref, limits=limits, logical_rows_max=row_limit,
                  total_warm_p95_ns=total_limit,
                  warm_excludes_first=20, rule='2x pilot; stage floors warm5ms/cold10ms; rows2x')
    budget_path = output / 'frozen-budgets.json'
    save(budget_path, budget)
    budget_hash = digest(budget_path)
    failures = []
    for index in range(1, 4):
        trial, ref = run('trial%d' % index, 1000)
        assert ref['started_at_unix_ms'] >= budget['frozen_at_unix_ms']
        for stage in STAGES:
            if p95([s['stages_ns'][stage] for s in trial['samples'][20:]]) > limits[stage]['warm_p95_ns']:
                failures.append('trial%d/%s warm p95' % (index, stage))
            if trial['samples'][0]['stages_ns'][stage] > limits[stage]['first_turn_ns']:
                failures.append('trial%d/%s first turn' % (index, stage))
        if max(s['logical_rows_changed'] for s in trial['samples']) > row_limit:
            failures.append('trial%d/logical rows' % index)
        if p95([s['turn_elapsed_ns'] for s in trial['samples'][20:]]) > total_limit:
            failures.append('trial%d/end-to-end warm p95' % index)
        assert digest(budget_path) == budget_hash
    save(output / 'batch-result.json', dict(source_sha=head, api=args.api, scope='BACKEND_WAIT_ONLY',
         artifacts=artifacts, budget_sha256=budget_hash, failures=failures,
         backend_budget_status='FAIL' if failures else 'PASS', r1_status='NOT_QUALIFIED',
         missing=['integrated provider/action/UI', 'positive actor/private-promise evidence', 'human voice/agency playtest'],
         physical_io='NOT_MEASURED', sql_statement_count='NOT_MEASURED'))
    if failures:
        raise SystemExit('backend budget failed; frozen budgets retained')


if __name__ == '__main__':
    main()
