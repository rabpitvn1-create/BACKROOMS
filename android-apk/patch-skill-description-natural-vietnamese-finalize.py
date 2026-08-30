from pathlib import Path

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-skill-description-natural-vietnamese.py"
source = PATCH.read_text(encoding="utf-8")

old_helper = '''def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Issue #126 {label}: expected one anchor, found {count}")
    return text.replace(old, new, 1)
'''
new_helper = '''def replace_once(text: str, old: str, new: str, label: str) -> str:
    if new in text:
        return text
    count = text.count(old)
    if count == 1:
        return text.replace(old, new, 1)
    if count == 0:
        import re
        name_match = re.search(r's\\(\\"([^\\"]+)\\"', old)
        if name_match:
            skill_name = name_match.group(1)
            row = re.compile(r'(?m)^    s\\(\\"' + re.escape(skill_name) + r'\\",.*$')
            matches = list(row.finditer(text))
            if len(matches) == 1:
                return row.sub(lambda _: new, text, count=1)
    raise RuntimeError(f"Issue #126 {label}: expected one skill row, exact anchors found {count}")
'''
if old_helper not in source:
    raise RuntimeError("Issue #126 natural-description helper anchor missing")
source = source.replace(old_helper, new_helper, 1)
exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})
