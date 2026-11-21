"""Load .env as literal KEY=value data, then launch the local Spring application."""
import os
from pathlib import Path
import re
import subprocess
import sys
root = Path(__file__).resolve().parent.parent
environment = os.environ.copy()
dotenv = root / '.env'
if dotenv.exists():
    for number, line in enumerate(dotenv.read_text(encoding='utf-8-sig').splitlines(), 1):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        if '=' not in line:
            raise SystemExit(f'Invalid .env entry on line {number}; use KEY=value')
        key, value = line.split('=', 1)
        key, value = key.strip(), value.strip()
        if not re.fullmatch(r'[A-Z][A-Z0-9_]*', key):
            raise SystemExit(f'Invalid configuration name on line {number}')
        if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
            value = value[1:-1]
        environment.setdefault(key, value)
environment.setdefault('MAVEN_USER_HOME', str(root / '.local' / 'maven-home'))
wrapper = 'mvnw.cmd' if os.name == 'nt' else 'mvnw'
command = [str(root / wrapper), '-Dmaven.repo.local=.local/m2', 'spring-boot:run', *sys.argv[1:]]
raise SystemExit(subprocess.call(command, cwd=root, env=environment))
