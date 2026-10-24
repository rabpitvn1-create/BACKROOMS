from pathlib import Path
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
with tempfile.TemporaryDirectory() as output:
    compiler = [shutil.which('javac')] if shutil.which('javac') else ['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main']
    subprocess.run(compiler + ['-d', output,
        str(root / 'app/src/main/java/com/rabpit/backroom/TextProviderRouter.java'),
        str(root / 'tests/TextProviderRouterSmokeTest.java')], check=True)
    subprocess.run(['java', '-cp', output, 'TextProviderRouterSmokeTest'], check=True)
