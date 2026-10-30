"""Stage the recovered web UI for GitHub Pages without changing APK inputs."""
from pathlib import Path
import hashlib
import json
import os
import shutil

ROOT = Path(__file__).resolve().parents[1]


def build():
    output = ROOT / "build/pages"
    if output.exists():
        shutil.rmtree(output)
    shutil.copytree(ROOT / "web", output)
    script = (ROOT / "pages/preview.js").read_bytes()
    version = hashlib.sha256(script).hexdigest()[:12]
    shutil.copy2(ROOT / "pages/preview.js", output / "pages-preview.js")
    page = output / "index.html"
    html = page.read_text()
    html = html.replace('<title>Backroom</title>', '<title>Backrooms | Bản xem trước trên web</title>')
    # Native startup recovery is not a corrupt-save error on a web preview.
    html = html.replace('<script src="level-travel.js"></script>', '')
    html = html.replace('</body>', f'<script src="pages-preview.js?v={version}"></script>\n</body>')
    page.write_text(html)
    (output / ".nojekyll").touch()
    # The current page has no browser gameplay runtime; do not package native credentials.
    (output / "personal-provider-config.json").write_text("{}\n")
    (output / "build.json").write_text(json.dumps({
        "mode": "recovered-web-preview", "gameplay": "android-apk",
        "version": "1.0.0.0", "commit": os.environ.get("GITHUB_SHA"),
        "apk": "https://github.com/rabpitvn1-create/Backrooms/releases/download/v1.0.0.0/Backrooms-1.0.0.0-debug.apk",
    }, indent=2))
    print(f"Staged {sum(p.is_file() for p in output.rglob('*'))} files for GitHub Pages.")
    return output


if __name__ == "__main__":
    build()
