from pathlib import Path

MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str):
    global text
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly 1 match, found {count}")
    text = text.replace(old, new, 1)

luna_http = r'''  private String postJsonLunaFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(12000);
    connection.setReadTimeout(12000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty(authHeader, authHeader.equals("Authorization") ? "Bearer " + key : key);
    try (OutputStream output = connection.getOutputStream()) {
      output.write(payload.toString().getBytes("UTF-8"));
    }
    int status = connection.getResponseCode();
    InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
    StringBuilder body = new StringBuilder();
    if (stream != null) {
      try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
        String line;
        while ((line = reader.readLine()) != null) body.append(line);
      }
    }
    connection.disconnect();
    if (status < 200 || status >= 300) {
      String detail = body.length() > 220 ? body.substring(0, 220) : body.toString();
      throw new HttpError(status, "Provider HTTP " + status + (detail.isEmpty() ? "" : ": " + detail));
    }
    return body.toString();
  }

'''
anchor = "  private String postJsonFast(String endpoint, String key, String authHeader, JSONObject payload) throws Exception {\n"
if "private String postJsonLunaFast(" not in text:
    if anchor not in text:
        raise RuntimeError("Luna fast HTTP anchor not found")
    text = text.replace(anchor, luna_http + anchor, 1)

old_call = r'''        JSONObject result = new JSONObject(postJson(
          baseUrl + "/chat/completions",
          BuildConfig.LUNA_API_KEY,
          "Authorization",
          body
        ));
'''
new_call = old_call.replace("postJson(", "postJsonLunaFast(")
replace_once(old_call, new_call, "Luna fast HTTP call")

# One Luna attempt is enough after five Gemini lanes; repeated long retries defeat
# the point of having a bounded fallback chain and spend unnecessary tokens.
replace_once("    for (int attempt = 0; attempt < 3; attempt++) {\n", "    for (int attempt = 0; attempt < 1; attempt++) {\n", "single Luna attempt")
replace_once(
    "        if (attempt < 2 && (transport || code == 0 || retryable(code))) {\n",
    "        if (false && (transport || code == 0 || retryable(code))) {\n",
    "disable Luna retry branch",
)

for required in [
    "private String postJsonLunaFast(",
    "setConnectTimeout(12000)",
    "setReadTimeout(12000)",
    "for (int attempt = 0; attempt < 1; attempt++)",
]:
    if required not in text:
        raise RuntimeError(f"Luna deadline hardening missing marker: {required}")

MAIN.write_text(text, encoding="utf-8")
print("Android Luna fallback bounded to one 12s text attempt after the Gemini pool.")
