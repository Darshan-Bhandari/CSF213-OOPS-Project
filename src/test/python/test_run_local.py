"""Checks the actual launcher in a disposable project with a fake Maven executable."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

SOURCE = Path(__file__).resolve().parents[3] / 'scripts' / 'run_local.py'


class LocalLauncherTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name).resolve()
        (self.root / 'scripts').mkdir()
        shutil.copyfile(SOURCE, self.root / 'scripts' / 'run_local.py')
        wrapper = self.root / 'mvnw'
        wrapper.write_text('''#!/usr/bin/env python3
import json, os, sys
print(json.dumps(dict(args=sys.argv[1:], cwd=os.getcwd(), literal=os.getenv('LAUNCHER_LITERAL'), blank=os.getenv('LAUNCHER_BLANK'), chosen=os.getenv('LAUNCHER_CHOICE'))))
sys.exit(int(os.getenv('FAKE_MAVEN_EXIT', '0')))
''')
        wrapper.chmod(0o755)

    def launch(self, content, **extra):
        (self.root / '.env').write_text(content)
        env = {k: v for k, v in os.environ.items() if not k.startswith('LAUNCHER_')}
        env.update(extra)
        return subprocess.run(['python3', str(self.root / 'scripts' / 'run_local.py'), '-Dexample=literal $value'],
                              env=env, capture_output=True, text=True, timeout=10)

    def test_literals_are_not_executed_and_blank_values_are_kept(self):
        sentinel = self.root / 'must-not-exist'
        value = f'$(touch "{sentinel}") `whoami` $HOME'
        result = self.launch(f'# Configuration\nLAUNCHER_LITERAL=\'{value}\'\nLAUNCHER_BLANK=\n')
        self.assertEqual(0, result.returncode, result.stderr)
        data = json.loads(result.stdout)
        self.assertEqual(value, data['literal'])
        self.assertEqual('', data['blank'])
        self.assertFalse(sentinel.exists())
        self.assertEqual(str(self.root), data['cwd'])
        self.assertEqual(['-Dmaven.repo.local=.local/m2', 'spring-boot:run', '-Dexample=literal $value'], data['args'])

    def test_exported_configuration_takes_precedence(self):
        result = self.launch('LAUNCHER_CHOICE=from-file\n', LAUNCHER_CHOICE='exported')
        self.assertEqual('exported', json.loads(result.stdout)['chosen'])

    def test_invalid_configuration_is_rejected_before_launch(self):
        for text in ['MISSING_EQUALS', 'bad-name=value', '=value']:
            with self.subTest(text=text):
                result = self.launch(text)
                self.assertNotEqual(0, result.returncode)
                self.assertEqual('', result.stdout)
                self.assertIn('line 1', result.stderr)

    def test_maven_failure_status_is_preserved(self):
        result = self.launch('', FAKE_MAVEN_EXIT='7')
        self.assertEqual(7, result.returncode)


if __name__ == '__main__':
    unittest.main()
