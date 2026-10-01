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

    def test_orphans_duplicate_copies_and_binding_collisions_are_reported(self):
        with tempfile.TemporaryDirectory() as directory:
            assets = Path(directory)
            (assets / 'content/world').mkdir(parents=True)
            (assets / 'canon').mkdir()
            (assets / 'content/world/world.md').write_text('# WORLD\nOPEN\n')
            (assets / 'content/world/other.md').write_text('# OTHER\n')
            (assets / 'canon/world.md').write_text('# WORLD\nOPEN\n')
            a, b = source(), source('b', 'world/other.md')
            a['mandatoryFor'] = b['mandatoryFor'] = ['character:hero']
            report = module.audit(assets, {'schemaVersion': 1, 'sources': [a, b]})
            self.assertIn('duplicate_physical_copy:world', report['errors'])
            self.assertIn('mandatory_subject_duplicate:character:hero', report['errors'])
            orphan = module.audit(assets, {'schemaVersion': 1, 'sources': [a]})
            self.assertIn('world/other.md', orphan['orphanContent'])

    def test_shipped_conflict_and_compatibility_coverage_remain_unresolved(self):
        assets = Path(__file__).parents[2] / 'app/src/main/assets'
        report = module.audit(assets)
        self.assertIn({'sourceId': 'cao-minh', 'local': 'R17', 'sourceMap': 'R15',
                       'resolution': 'UNRESOLVED'}, report['authorityConflicts'])
        self.assertIn('character:syvial', report['mandatoryCoverage']['requiresCoreCompatibility'])
        self.assertEqual('lucia', report['mandatoryCoverage']['explicit']['character:lucia'])
        self.assertEqual('luc-tram', report['mandatoryCoverage']['explicit']['character:luc_tram'])
        self.assertEqual([], report['orphanContent'])
        self.assertEqual([], report['duplicatePhysicalCopies'])

    def test_malformed_registry_arrays_fail_closed_without_crashing(self):
        with tempfile.TemporaryDirectory() as directory:
            a = source()
            a.update(supersedes=None, dependencies={}, mandatoryFor=42, authority=[])
            report = module.audit(Path(directory), {'schemaVersion': 1, 'sources': [a]})
            self.assertIn('supersedes_invalid:world', report['errors'])
            self.assertIn('dependencies_invalid:world', report['errors'])
            self.assertIn('mandatoryFor_invalid:world', report['errors'])
            self.assertIn('authority_invalid:world', report['errors'])
