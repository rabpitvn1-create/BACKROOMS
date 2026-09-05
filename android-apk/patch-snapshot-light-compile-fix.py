from pathlib import Path

ROOT = Path(__file__).resolve().parent
TARGET = ROOT / "app/src/main/java/com/rabpit/backroom/core/SnapshotLightAnalyzer.kt"
MAIN = ROOT / "app/src/main/java/com/rabpit/backroom/MainActivity.java"

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

main = MAIN.read_text(encoding="utf-8")
old_ctor = "    snapshotLightAnalyzer = new SnapshotLightAnalyzer(getApplicationContext());"
new_ctor = "    snapshotLightAnalyzer = new SnapshotLightAnalyzer(getApplicationContext(), \"models/backroom_light.tflite\");"
if new_ctor not in main:
    count = main.count(old_ctor)
    if count != 1:
        raise RuntimeError(f"SnapshotLightAnalyzer Java constructor: expected exactly one anchor, found {count}")
    main = main.replace(old_ctor, new_ctor, 1)
if old_ctor in main:
    raise RuntimeError("MainActivity still relies on Kotlin default constructor arguments from Java")
MAIN.write_text(main, encoding="utf-8")

print("SnapshotLightAnalyzer JVM contracts fixed: close() returns Unit and Java passes modelAsset explicitly.")
