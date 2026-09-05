from pathlib import Path

ROOT = Path(__file__).resolve().parent
TARGET = ROOT / "app/src/main/java/com/rabpit/backroom/core/SnapshotLightAnalyzer.kt"

text = TARGET.read_text(encoding="utf-8")
old = "  override fun close() = synchronized(lock) { interpreter?.close() }"
new = """  override fun close() {
    synchronized(lock) { interpreter?.close() }
  }"""
if new not in text:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"SnapshotLightAnalyzer close contract: expected exactly one anchor, found {count}")
    text = text.replace(old, new, 1)
if "override fun close(): Unit?" in text or old in text:
    raise RuntimeError("SnapshotLightAnalyzer close still has nullable return semantics")
TARGET.write_text(text, encoding="utf-8")
print("SnapshotLightAnalyzer AutoCloseable contract fixed: close() returns Unit explicitly.")
