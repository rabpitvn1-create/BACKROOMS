from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent
INTENT = ROOT / "app/src/main/java/com/rabpit/backroom/core/IntentPipeline.kt"

text = INTENT.read_text(encoding="utf-8")
start = text.find("private data class ResolverAlias")
end = text.find("class DefaultQuantityResolver", start)
if start < 0 or end < 0:
    raise RuntimeError("Issue #124 resolver block missing before compile fix")

block = text[start:end]
fixed, replacements = re.subn(
    r'(?<!\\)\\([sp])',
    lambda match: "\\\\" + match.group(1),
    block,
)
if re.search(r'(?<!\\)\\(?:s|p)', fixed):
    raise RuntimeError("Issue #124 resolver still contains invalid Kotlin regex escapes")

text = text[:start] + fixed + text[end:]
INTENT.write_text(text, encoding="utf-8")
print(f"Issue #124 Kotlin regex escapes normalized ({replacements} replacements).")
