import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('canon_audit', Path(__file__).parents[1] / 'canon_audit.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def source(sid='world', physical='world/world.md'):
    return dict(id=sid, path=sid + '.md', contentPath=physical, type='WORLD',
                authority='WORLD_CANON', status='CURRENT', version='', owner='', note='',
                dependencies=[], supersedes=[], mandatoryFor=[])


class CanonAuditTest(unittest.TestCase):
    def test_shipped_physical_sources_exist_and_report_is_deterministic(self):
        assets = Path(__file__).parents[2] / 'app/src/main/assets'
        report = module.audit(assets)
        self.assertEqual([], report['errors'])
        self.assertEqual(12, report['sourceCount'])
        self.assertEqual(report, module.audit(assets))

    def test_missing_graph_targets_cycles_and_unsafe_paths_fail(self):
        with tempfile.TemporaryDirectory() as directory:
            assets = Path(directory)
            a, b = source('a'), source('b', '../outside.md')
            a['dependencies'], b['dependencies'] = ['b', 'missing'], ['a']
            report = module.audit(assets, {'schemaVersion': 1, 'sources': [a, b]})
            self.assertIn('dependency_cycle', report['errors'])
            self.assertIn('dependencies_missing:a->missing', report['errors'])
            self.assertIn('content_path_invalid:b', report['errors'])
            self.assertTrue(any(x.startswith('physical_source_missing:a:') for x in report['errors']))

    def test_reports_preserve_authority_and_supersedes_without_writing_content(self):
        with tempfile.TemporaryDirectory() as directory:
            assets = Path(directory)
            physical = assets / 'content/world/world.md'
            physical.parent.mkdir(parents=True)
            physical.write_text('# Original\nOPEN\n', encoding='utf-8')
            a = source()
            a['supersedes'] = ['missing']
            before = physical.read_bytes()
            report = module.audit(assets, {'schemaVersion': 1, 'sources': [a]})
            self.assertEqual(['missing'], report['supersedes']['world'])
            self.assertEqual('CURRENT', report['authorityStatus'][0]['status'])
            self.assertEqual(before, physical.read_bytes())
