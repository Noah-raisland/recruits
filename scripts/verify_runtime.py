"""Check the production server JAR and the development client on the pinned loader."""
import argparse
import json
import os
from pathlib import Path
import shutil
import signal
import subprocess
import time
import urllib.request
import zipfile


def stop(process):
    if process.poll() is None:
        os.killpg(process.pid, signal.SIGTERM)
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()


def server():
    jar, = Path('build/libs').glob('*-all.jar')
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        assert len(names) == len(set(names)), 'Duplicate JAR entries'
        assert any(n.startswith('de/maxhenkel/recruits/corelib/') for n in names), 'CoreLib not bundled'
        metadata = archive.read('META-INF/neoforge.mods.toml').decode()
        assert '[21.1.233,21.2)' in metadata, 'Wrong loader requirement'
        assert '[1.21.1,1.21.2)' in metadata, 'Wrong Minecraft requirement'
    root = Path('production-server')
    root.mkdir(exist_ok=True)
    installer = root / 'installer.jar'
    urllib.request.urlretrieve('https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.233/neoforge-21.1.233-installer.jar', installer)
    subprocess.run(['java', '-jar', 'installer.jar', '--installServer'], cwd=root, check=True)
    (root / 'mods').mkdir(exist_ok=True)
    shutil.copy(jar, root / 'mods' / jar.name)
    (root / 'eula.txt').write_text('eula=true\n')
    (root / 'server.properties').write_text('online-mode=false\nlevel-type=minecraft:flat\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\ngenerate-structures=false\n')
    commands = ['gamerule doMobSpawning false', 'forceload add 0 0',
                'summon recruits:recruit 0 -59 0 {NoAI:1b,PersistenceRequired:1b,Tags:["port_check"],CustomName:\'{"text":"Production Recruit"}\'}',
                'execute if entity @e[type=recruits:recruit,tag=port_check] run say PORT_ENTITY_OK',
                'save-all flush', 'stop']
    run_server(root, commands, 'initial.log', 'PORT_ENTITY_OK')
    run_server(root, ['execute if entity @e[type=recruits:recruit,tag=port_check] run say PORT_RESTART_OK', 'stop'], 'restart.log', 'PORT_RESTART_OK')
    print('Production JAR loaded, spawned a recruit, and restored it after server restart.')


def run_server(root, commands, logfile, marker):
    path = root / logfile
    with path.open('w') as log:
        proc = subprocess.Popen(['bash', 'run.sh', '--nogui'], cwd=root, stdin=subprocess.PIPE, stdout=log, stderr=subprocess.STDOUT, text=True, start_new_session=True)
        try:
            deadline = time.monotonic() + 240
            while time.monotonic() < deadline and proc.poll() is None:
                if 'Done (' in path.read_text(errors='replace'):
                    break
                time.sleep(1)
            else:
                raise RuntimeError('Dedicated server did not finish startup')
            for command in commands:
                proc.stdin.write(command + '\n')
                proc.stdin.flush()
                time.sleep(2)
            proc.wait(timeout=90)
            assert proc.returncode == 0, 'Server exited with error'
            assert marker in path.read_text(errors='replace'), 'Entity spawn/persistence check failed'
        finally:
            stop(proc)
            print('\n'.join(path.read_text(errors='replace').splitlines()[-80:]))


def client():
    # Hosted runners have no physical audio card. OpenAL's null output still
    # exercises Minecraft sound-engine initialization without needing hardware.
    os.environ.setdefault('ALSOFT_DRIVERS', 'null')
    path = Path('client-smoke.log')
    marker = 'minecraft:textures/atlas/gui.png-atlas'
    with path.open('w') as log:
        proc = subprocess.Popen(['xvfb-run', '-a', './gradlew', 'runClient', '--stacktrace'], stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            deadline = time.monotonic() + 300
            while time.monotonic() < deadline and proc.poll() is None:
                text = path.read_text(errors='replace')
                if marker in text:
                    time.sleep(10)
                    assert proc.poll() is None, 'Client crashed after loading resources'
                    break
                time.sleep(1)
            else:
                raise RuntimeError('Client did not finish loading resources')
        finally:
            stop(proc)
            text = path.read_text(errors='replace')
            print('\n'.join(text.splitlines()[-100:]))
    assert marker in text
    assert 'Error starting SoundSystem' not in text, 'Sound engine failed'
    assert 'Failed to load model recruits:' not in text, 'Recruit model failed'
    assert 'Caught exception during event' not in text, 'Client setup failed'
    print('Client loaded Recruits renderers and resources.')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['server', 'client'])
    args = parser.parse_args()
    server() if args.mode == 'server' else client()
