# BACKROOMS map

Start from the module involved in the task; expand to callers/dependencies as needed.

- android-apk/app/src/main/java/com/rabpit/backroom/core/: deterministic game state, inventory, party, canon retrieval.
- android-apk/app/src/main/java/com/rabpit/backroom/MainActivity.java: Android/WebView bridge and AI orchestration.
- android-apk/app/src/main/assets/index.html and assets/*.js: text-game UI; tests in android-apk/tests/.
- android-apk/app/src/main/assets/knowledge/: packaged knowledge and canon; see android-apk/CANON_RETRIEVER_README.md.
- android-apk/app/src/test/: Java unit tests.
- android-apk/tools/: patchctl, canon_audit and offline tests.
- .github/workflows/gm-core-verify.yml and release-version.yml: actual game verification/build; do not trigger releases just to test tooling.
- tools/ponytail.sh and ponytail-config.toml: isolated CLI/provider setup; .sdkmanrc pins Java/Gradle to CI versions.
- tools/ponytail-run.py: one check, full redacted log in user state directory, bounded summary, original exit code.
- tools/ponytail-usage.py: numerical rollout metrics; pass a specific rollout path. No inference/API calls.

Examples:
- rg -n --max-count 20 --max-columns 200 CanonRetriever android-apk/app/src/main/java/com/rabpit/backroom/core
- sed -n '80,180p' FILE
- python3 tools/ponytail-run.py python3 -m unittest discover -s android-apk/tools/tests -p test_canon_audit.py
- python3 tools/ponytail-run.py node --test android-apk/tests/viewport-layout.test.cjs
- With Android SDK installed: python3 tools/ponytail-run.py gradle -p android-apk :app:testDebugUnitTest :app:assembleDebug --no-daemon

Environment: bash tools/ponytail.sh --env-check. An absent Android SDK means local APK checks are unavailable; use the existing game CI or provision the SDK when a local APK build is actually needed.
