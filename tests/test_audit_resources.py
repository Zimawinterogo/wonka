"""Fault-injection tests for the read-only auditor (no game/release required)."""
import importlib.util
import json
import tempfile
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location('auditor', Path(__file__).resolve().parents[1]/'tools/audit_resources.py')
auditor = importlib.util.module_from_spec(spec); spec.loader.exec_module(auditor)


class AuditResourcesTest(unittest.TestCase):
    def test_strict_json(self):
        for data in ('{"a":1,"a":2}', '{', '{"x":NaN}'):
            with self.assertRaises(ValueError):
                auditor.strict_json(data)

    def test_truncated_media(self):
        for data in (b'', b'not png', b'\x89PNG\r\n\x1a\n'):
            with self.assertRaises(ValueError):
                auditor.png(data)
        with self.assertRaises(ValueError):
            auditor.ogg(b'OggS')

    def test_missing_links_and_overlay(self):
        scratch = Path(__file__).resolve().parents[1]/'build/audit-test-tmp'
        scratch.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory(dir=scratch) as directory:
            root = Path(directory); (root/'base').mkdir()
            (root/'src/main/java').mkdir(parents=True)
            resources = root/'src/main/resources'; resources.mkdir(parents=True)
            with zipfile.ZipFile(root/'base/Whiteout-3.4.2-1.21.1.jar', 'w') as archive:
                archive.writestr('assets/whiteout/sounds.json', '{}')
                archive.writestr('assets/whiteout/models/block/a.json', json.dumps({'textures': {'all': 'whiteout:block/absent'}}))
                archive.writestr('assets/whiteout/blockstates/a.json', json.dumps({'variants': {'': {'model': 'whiteout:block/absent'}}}))
            result = auditor.audit(root)
            self.assertEqual(2, len(result['errors']))
            model = resources/'assets/whiteout/models/block/a.json'; model.parent.mkdir(parents=True)
            model.write_text('{}')
            self.assertEqual(1, len(auditor.audit(root)['errors']))


if __name__ == '__main__':
    unittest.main()
