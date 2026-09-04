from pathlib import Path

ROOT = Path(__file__).resolve().parent
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    if new in source:
        return source
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one anchor, found {count}")
    return source.replace(old, new, 1)


# HAKU is the primary runtime writer. The provider is OpenAI-compatible, but unlike Gemini's
# responseMimeType path it is not guaranteed to force JSON at the transport layer. Give it one
# explicit system contract, reduce sampling drift, and leave enough completion budget to avoid
# truncating the final closing brace on longer gameplay turns.
old_messages = '''    JSONArray messages = new JSONArray()
      .put(new JSONObject().put("role", "user").put("content", prompt));
    JSONObject body = new JSONObject()
      .put("model", "claude-haiku-4-5-20251001")
      .put("messages", messages)
      .put("temperature", 0.75)
      .put("max_tokens", 1800)
      .put("stream", false);
'''
new_messages = '''    JSONArray messages = new JSONArray()
      .put(new JSONObject()
        .put("role", "system")
        .put("content", "Return exactly one valid JSON object. No markdown, no code fences, no text outside JSON. Preserve the schema and required keys specified by the user prompt."))
      .put(new JSONObject().put("role", "user").put("content", prompt));
    JSONObject body = new JSONObject()
      .put("model", "claude-haiku-4-5-20251001")
      .put("messages", messages)
      .put("temperature", 0.2)
      .put("max_tokens", 3200)
      .put("stream", false);
'''
text = replace_once(text, old_messages, new_messages, "HAKU strict JSON request")

# 22s was tuned for the old 1800-token cap. A slightly larger read window prevents the new
# completion budget from turning otherwise valid HAKU responses into artificial timeouts.
text = replace_once(
    text,
    '    connection.setReadTimeout(22000);\n',
    '    connection.setReadTimeout(30000);\n',
    "HAKU read timeout",
)

for marker in (
    '"role", "system"',
    'Return exactly one valid JSON object.',
    '.put("temperature", 0.2)',
    '.put("max_tokens", 3200)',
    'connection.setReadTimeout(30000);',
    'AiProviderRouter.route(',
    'this::hakuFallbackText',
    'this::lunaText',
):
    if marker not in text:
        raise RuntimeError("HAKU reliability contract missing: " + marker)

for forbidden in (
    '.put("temperature", 0.75)',
    '.put("max_tokens", 1800)',
    'connection.setReadTimeout(22000);',
):
    if forbidden in text:
        raise RuntimeError("Obsolete HAKU reliability setting survived: " + forbidden)

MAIN.write_text(text, encoding="utf-8")
print("HAKU reliability hardened: strict JSON system contract, lower sampling drift, larger completion budget and aligned read timeout.")
