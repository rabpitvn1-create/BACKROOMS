from pathlib import Path
import json

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / 'app/src/main/java/com/rabpit/backroom/MainActivity.java'
HTML = ROOT / 'app/src/main/assets/index.html'


def once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError(f'Readable GM anchor missing or ambiguous: {old[:100]}')
    return text.replace(old, new, 1)


main = MAIN.read_text(encoding='utf-8')
rule = (
    'PROSE RULE: Văn xuôi gameplay phải là tiếng Việt tự nhiên, cụ thể và bám vào trải nghiệm hiện tại. '
    'Dùng từ thông dụng, câu rõ chủ thể; mỗi đoạn 1–3 câu và mỗi câu một ý chính. '
    'Kể điều bạn nhìn, nghe, chạm thấy và kết quả trực tiếp của hành động, không giảng giải trừu tượng. '
    'Tránh câu dịch máy như "kiến trúc thả nhẹ", "các vật chất vật lý", "dò theo mặt nền với cảm giác". '
    'Nếu dữ liệu chỉ cho biết độ dốc, viết "Nền hơi dốc về một phía"; không tự suy ra hướng nước chảy, quy luật không gian hay lối thoát. '
    'Không tự ghi nhớ, suy nghĩ, quyết định hoặc nói thay người chơi. '
    'Không lặp lại ý, kéo dài tả cảnh hoặc tự thêm nguy hiểm, vật phẩm hay manh mối. '
    'Không đổi dữ kiện canon/gameplay. Bằng chứng đã phát hiện được ứng dụng in đậm; không tự đánh dấu cảnh nền thành bằng chứng. '
    'Trả lời bằng văn bản thuần, không HTML hay dấu **.\n\n'
)
anchor = 'public class MainActivity extends Activity {\n'
main = once(main, anchor, anchor + '  private static final String GAMEPLAY_PROSE_RULE = ' + json.dumps(rule, ensure_ascii=False) + ';\n')
lines = main.splitlines(keepends=True)
matches = [i for i, line in enumerate(lines) if line.lstrip().startswith('"PROSE RULE:')]
if len(matches) != 1:
    raise RuntimeError('Expected one existing writer prose rule')
lines[matches[0]] = '      GAMEPLAY_PROSE_RULE +\n'
main = ''.join(lines)
main = once(main,
    'String prompt = "Bạn là Narrative Engine của một text game Backrooms. "',
    'String prompt = GAMEPLAY_PROSE_RULE + "Bạn là Narrative Engine của một text game Backrooms. "')
main = once(main,
    'appendRegisteredNarrativeLog(registeredState, action, registeredReply);',
    'appendRegisteredNarrativeLog(registeredState, action, registeredReply, registeredLevelResult.optJSONArray("evidenceTexts"));')
main = once(main,
    'private void appendRegisteredNarrativeLog(JSONObject state, String action, String reply) throws Exception {',
    'private void appendRegisteredNarrativeLog(JSONObject state, String action, String reply, JSONArray evidenceTexts) throws Exception {')
main = once(main,
    'log.put(new JSONObject().put("role", "gm").put("text", reply == null ? "" : reply));',
    'log.put(new JSONObject().put("role", "gm").put("text", reply == null ? "" : reply)\n'
    '      .put("evidenceTexts", evidenceTexts == null ? new JSONArray() : new JSONArray(evidenceTexts.toString())));')
MAIN.write_text(main, encoding='utf-8')

html = HTML.read_text(encoding='utf-8')
html = html.replace('<script>', '<script src="evidence-text.js"></script>\n<script>', 1)
html = once(html, '+esc(text)+', '+renderEvidenceText(text,x)+')
html = once(html, '</head>', '<style>.gm-evidence{font-family:inherit;font-weight:800;color:inherit;text-shadow:none}</style>\n</head>')
# The existing RPG decorator reconstructs text nodes. Preserve evidence as its
# highest-priority range instead of letting item/skill decoration flatten it.
html = once(html,
    '    var ranges=[],hp=',
    '    var evidenceNames=Array.from(textEl.querySelectorAll("strong.gm-evidence")).map(function(node){return node.textContent});\n'
    '    var ranges=[],hp=')
html = once(html,
    '    var isPlayer=article.classList.contains(\'player\');',
    '    addNamedRanges(text,evidenceNames,"evidence",40,ranges);\n'
    '    var isPlayer=article.classList.contains(\'player\');')
html = once(html,
    "var span=document.createElement('span');\n      span.className=range.type===",
    "var span=document.createElement(range.type==='evidence'?'strong':'span');\n      span.className=range.type==='evidence'?'gm-evidence':range.type===")
HTML.write_text(html, encoding='utf-8')
print('Readable GM: shared concrete prose rule; per-entry surfaced evidence rendered in bold.')
