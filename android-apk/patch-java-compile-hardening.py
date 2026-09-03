from pathlib import Path
import re

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

text = MAIN.read_text(encoding="utf-8")

# Drive gameplay uses SecureRandom, but the legacy base activity does not import it.
secure_import = "import java.security.SecureRandom;\n"
if secure_import not in text:
    anchor = "import java.net.URL;\n"
    if anchor not in text:
        raise RuntimeError("SecureRandom import anchor not found")
    text = text.replace(anchor, anchor + secure_import, 1)

# Several routed-canon/state helpers normalize Vietnamese/English terms. Keep one
# locale-stable helper in the final generated Java instead of relying on an
# intermediate patch to have created it.
if "private String lower(String value)" not in text:
    anchor = "  private boolean retryable(int code) {\n"
    if anchor not in text:
        raise RuntimeError("lower helper anchor not found")
    helper = '''  private String lower(String value) {\n    return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);\n  }\n\n'''
    text = text.replace(anchor, helper + anchor, 1)

# patch-provider-status originally injects these helpers immediately before
# generateText(). The health-pool patch replaces the whole geminiText ->
# generateText interval, so those helpers can be swallowed. Recreate them in the
# final source if necessary.
if "private boolean networkFailure(Exception error)" not in text:
    anchor = "  private String generateText(String prompt) throws Exception {\n"
    if anchor not in text:
        raise RuntimeError("network helper anchor not found")
    helpers = '''  private boolean networkFailure(Exception error) {\n    Throwable cause = error;\n    while (cause != null) {\n      if (cause instanceof java.net.UnknownHostException ||\n          cause instanceof java.net.ConnectException ||\n          cause instanceof java.net.SocketTimeoutException ||\n          cause instanceof java.net.SocketException ||\n          cause instanceof java.io.IOException) return true;\n      cause = cause.getCause();\n    }\n    return false;\n  }\n\n  private String networkFailureMessage() {\n    return "Lỗi mạng/DNS: không thể kết nối tới máy chủ AI. Kiểm tra Wi-Fi/4G, Private DNS hoặc VPN.";\n  }\n\n'''
    text = text.replace(anchor, helpers + anchor, 1)

recursive_pattern = re.compile(
    r'''  private void mergeObject\(JSONObject target, JSONObject patch\) throws Exception \{\n'''
    r'''    if \(patch == null\) return;\n'''
    r'''    for \(String key : JSONObject\.getNames\(patch\) == null \? new String\[0\] : JSONObject\.getNames\(patch\)\) \{\n'''
    r'''      Object value = patch\.opt\(key\);\n'''
    r'''      if \(value instanceof JSONObject && target\.opt\(key\) instanceof JSONObject\) mergeObject\(target\.optJSONObject\(key\), \(JSONObject\)value\);\n'''
    r'''      else target\.put\(key, value\);\n'''
    r'''    \}\n'''
    r'''  \}\n'''
)
recursive_replacement = '''  private void mergeObjectDeep(JSONObject target, JSONObject patch) throws Exception {\n    if (patch == null) return;\n    Iterator<String> keys = patch.keys();\n    while (keys.hasNext()) {\n      String key = keys.next();\n      Object value = patch.opt(key);\n      if (value instanceof JSONObject && target.opt(key) instanceof JSONObject) {\n        mergeObjectDeep(target.optJSONObject(key), (JSONObject)value);\n      } else {\n        target.put(key, value);\n      }\n    }\n  }\n'''
text, renamed = recursive_pattern.subn(recursive_replacement, text, count=1)
if renamed == 0 and "private void mergeObjectDeep(JSONObject target, JSONObject patch)" not in text:
    raise RuntimeError("recursive mergeObject definition not found")

if "mergeObjectDeep(safe, patch);" not in text:
    old = "    mergeObject(safe, patch);\n    return safe;\n"
    if old not in text:
        raise RuntimeError("sanitizedFlags deep-merge call not found")
    text = text.replace(old, "    mergeObjectDeep(safe, patch);\n    return safe;\n", 1)

if text.count("private void mergeObject(JSONObject target, JSONObject patch)") != 1:
    raise RuntimeError("final Java must contain exactly one shallow mergeObject definition")
if text.count("private void mergeObjectDeep(JSONObject target, JSONObject patch)") != 1:
    raise RuntimeError("final Java must contain exactly one mergeObjectDeep definition")
for required in [
    "private String lower(String value)",
    "private boolean networkFailure(Exception error)",
    "private String networkFailureMessage()",
    "import java.security.SecureRandom;",
]:
    if required not in text:
        raise RuntimeError(f"final Java compile helper missing: {required}")

MAIN.write_text(text, encoding="utf-8")
print("Final Java compile hardening applied: SecureRandom, lower/network helpers and mergeObject collision fixed. Luna model discovery removed; configured LUNA_MODEL is used directly.")
