from pathlib import Path
MAIN = Path(__file__).resolve().parent / "app/src/main/java/com/rabpit/backroom/MainActivity.java"
text = MAIN.read_text(encoding="utf-8")
old = """  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3
    };
  }
"""
new = """  private String[] geminiKeys() {
    return new String[] {
      BuildConfig.GEMINI_API_KEY_1,
      BuildConfig.GEMINI_API_KEY_2,
      BuildConfig.GEMINI_API_KEY_3,
      BuildConfig.GEMINI_API_KEY_4,
      BuildConfig.GEMINI_API_KEY_5
    };
  }
"""
if new not in text:
    if text.count(old) != 1: raise RuntimeError("Gemini key pool anchor mismatch")
    text = text.replace(old, new, 1)
MAIN.write_text(text, encoding="utf-8")
print("Game Master Gemini fallback pool: keys 1 -> 2 -> 3 -> 4 -> 5.")
