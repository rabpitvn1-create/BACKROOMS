"""Offline checks: real exit codes, retained logs, secret redaction, usage arithmetic."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location('usage', ROOT / 'ponytail-usage.py')
usage = importlib.util.module_from_spec(spec)
spec.loader.exec_module(usage)


class ToolsTest(unittest.TestCase):
    def test_failed_check_retains_log_exit_and_redacts_secret(self):
        with tempfile.TemporaryDirectory() as tmp:
            env = dict(os.environ, XDG_STATE_HOME=tmp, PONYTAIL_TEST_SECRET='synthetic-test-secret-123')
            script = "import os,sys; print(os.environ['PONYTAIL_TEST_SECRET']); [print('noise') for _ in range(300)]; print('ERROR deliberate'); sys.exit(7)"
            r = subprocess.run([sys.executable, str(ROOT / 'ponytail-run.py'), sys.executable, '-c', script], cwd=tmp, env=env, capture_output=True, text=True)
            self.assertEqual(r.returncode, 7)
            logs = list(Path(tmp).glob('backrooms-ponytail/logs/*.log'))
            self.assertEqual(len(logs), 1)
            text = logs[0].read_text()
            self.assertEqual(len(text.splitlines()), 302)
            self.assertNotIn(env['PONYTAIL_TEST_SECRET'], text + r.stdout)
            self.assertIn('[REDACTED]', text)
            self.assertLess(len(r.stdout.splitlines()), 25)
            self.assertEqual(logs[0].stat().st_mode & 0o777, 0o600)

    def test_missing_command_returns_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            r = subprocess.run([sys.executable, str(ROOT / 'ponytail-run.py'), '/nonexistent/ponytail-check'], env=dict(os.environ, XDG_STATE_HOME=tmp), capture_output=True, text=True)
            self.assertEqual(r.returncode, 127)

    def test_old_test_reports_are_not_claimed(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'android-apk/app/build/test-results/unit/TEST-old.xml'
            p.parent.mkdir(parents=True)
            p.write_text('<testsuite tests="500" failures="0"/>')
            os.utime(p, (1, 1))
            r = subprocess.run([sys.executable, str(ROOT / 'ponytail-run.py'), sys.executable, '-c', 'pass'], cwd=tmp, env=dict(os.environ, XDG_STATE_HOME=tmp), capture_output=True, text=True)
            self.assertEqual(r.returncode, 0)
            self.assertIn('unavailable', r.stdout)
            self.assertNotIn('500', r.stdout)

    def test_usage_groups_and_duplicate_notifications(self):
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / 'rollout.jsonl'
            events = []
            for i in range(1, 60):
                events.append({'payload': {'type': 'token_count', 'info': {'last_token_usage': {'input_tokens': 100, 'cached_input_tokens': 20, 'cache_write_tokens': 10, 'output_tokens': 2, 'total_tokens': 102}, 'total_token_usage': {'total_tokens': i * 102}, 'model_context_window': 999}}})
            events.insert(1, events[0])
            p.write_text(chr(10).join(json.dumps(e) for e in events))
            result = usage.analyze(p)
            self.assertEqual(result['usage_records'], 59)
            self.assertEqual(result['middle']['requests'], 19)
            self.assertEqual(result['all']['uncached_input_tokens_avg'], 70)
            self.assertEqual(result['cached_input_tokens_percent'], 20)
            self.assertEqual(result['first_to_last_total_percent'], 0)
            del events[0]['payload']['info']['last_token_usage']['cache_write_tokens']
            p.write_text(json.dumps(events[0]))
            self.assertIsNone(usage.analyze(p)['cache_write_tokens_percent'])


if __name__ == '__main__':
    unittest.main()
