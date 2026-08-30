from pathlib import Path

ROOT = Path(__file__).resolve().parent
PATCH = ROOT / "patch-skill-description-vietnamese.py"
source = PATCH.read_text(encoding="utf-8")

# The descriptions are intentionally complete sentences, so contract probes must
# search for phrases that can occur in the middle of those strings as well.
source = source.replace(
    "    '\\"185% DMG vũ khí; Phá Giáp 20% trong 2 lượt.\\"',\n",
    "    '185% DMG vũ khí; Phá Giáp 20% trong 2 lượt.',\n",
)
source = source.replace(
    "    '\\"170% DMG vũ khí; Chảy máu 3 lượt',\n",
    "    '170% DMG vũ khí; Chảy máu 3 lượt',\n",
)
source = source.replace(
    "    '\\"toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity.\\"',\n",
    "    'toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity.',\n",
)

for stale in (
    "'\\\"185% DMG vũ khí; Phá Giáp 20% trong 2 lượt.\\\"'",
    "'\\\"170% DMG vũ khí; Chảy máu 3 lượt'",
    "'\\\"toàn loạt chỉ thực hiện một lần kiểm tra Né tránh của Entity.\\\"'",
):
    if stale in source:
        raise RuntimeError("Issue #125 stale overly-strict marker survived: " + stale)

exec(compile(source, str(PATCH), "exec"), {"__name__": "__main__", "__file__": str(PATCH)})
