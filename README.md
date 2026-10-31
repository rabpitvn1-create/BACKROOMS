# BACKROOMS APK recovery

Extracted from `CAO_MINH-1.0.0.0.8.apk` (70 APK entries).

- `apk-original/`: byte-identical contents of all 70 APK entries, including compiled `classes*.dex` and Android manifest.
- `web/`: recovered webview game HTML, JavaScript, CSS, game data, music and fonts, with all existing Cloudinary URLs preserved.
- `web/personal-provider-config.json`: empty web configuration. The old provider configuration is not included in this repository. Debug packaging generates the current configuration from the six current primary Secrets.
- `apk-file-manifest.json`: original file hashes for precise audit.

Note: Android `classes*.dex` are compiled bytecode, not the original Kotlin/Java source. This extraction cannot recreate missing Gradle/build configuration. The APK manifest references some `characters/` and `item-icons/` media files that are not embedded in the APK; these need to be resolved from Cloudinary or other source to make all visuals work.

## GitHub Pages

Play now: https://rabpitvn1-create.github.io/Backrooms/

The Pages build is a playable browser port of the recovered APK. TeaVM compiles
the original gameplay core into JavaScript. A browser bridge supplies local
storage, original canon assets, Unicode regular expressions, and Gemini narration.
Turns, travel, party rules, inventory, core stats and combat use the recovered core.

The six Secrets `GEMINI_API_KEY_1` through `GEMINI_API_KEY_6` are used in order.
Argon is disabled. The owner explicitly approved publishing these six keys in
the browser runtime configuration on 2026-10-30. No old provider key is reused.

The deployment workflow rebuilds the core, verifies gameplay and key failover,
tests mobile and desktop browser interactions, and completes a real AI turn before
deployment. It also verifies a real AI turn on the published Pages URL.
Browser evidence is attached to each workflow run.

To stage fixture-only tests locally:
```sh
python3 scripts/prepare_web_engine.py
mvn -B -f engine/pom.xml package
node scripts/test_engine.cjs
python3 scripts/build_pages.py --test-config
```

For a configured local build, pass `--config path/to/runtime.json` containing
a `keys` array of six distinct strings. Generated outputs are not committed.

## Personal Android debug release 1.0.0.0

The `Backrooms debug release` workflow creates `Backrooms-1.0.0.0-debug.apk`
from the recovered APK entries, with `android:debuggable=true` and versionCode
90000001. It does not claim to recover the original Android source project.

The six repository Secrets `GEMINI_API_KEY_1` through `GEMINI_API_KEY_6`
provide the primary key pool in that order. Argon is temporarily disabled:
its Secret is not read, no fallback key/model is packaged, and its availability
does not block publication. The connection job verifies real JSON generation
with all six current keys before publishing. No previous key is reused.

The existing runtime determines fallback availability from its key/model; both
are blank in this build. A pinned four-byte edit to the credential validator
permits blank fields. Primary nonempty values are checked before packaging;
the runtime's distinct-key, value-length, and URL validation remain. The legacy
base URL remains only to satisfy that validation and receives no requests.
The primary router and all gameplay bytecode are unchanged.

Android tests load the router directly from the APK and verify primary success,
key switching, exhausting all six keys, rate-limit handling, and that a disabled
fallback is never called. They also invoke the APK's credential validator with
six keys and blank fallback credentials. All other recovered entries remain
byte-identical except the version manifest and generated configuration.

The workflow verifies APK signing and metadata, installs and launches the app
on an Android 15 emulator, and publishes tag `v1.0.0.0` only after success.
Run it manually, or push a packaging change whose commit message includes
`[release-debug]`. An existing release is never replaced automatically.

A standard Android development signing key is generated inside the runner.
It is not committed or attached to the release. This signature differs from the
unavailable original APK signing key and is regenerated on a new run. Android
may require uninstalling a differently signed build; export any saves first.

## Cao Minh artwork: offline once synced (2026-10-31)

The four new visuals are registered in `web/characters/cao-minh-assets.json`,
with version-pinned Cloudinary URLs, metadata, and original SHA256 hashes.
The same registry is injected into the Pages build and Android debug APK.
No image binaries or Base64 artwork are committed to this repository.

The first online visit saves all four image responses in IndexedDB.
Subsequent visits resolve the avatar, combat overlay, exploration overlay
and sword through local Blob URLs. Pages additionally uses a Service Worker
to cache the static game shell for offline reopening. A new installation
needs one successful online synchronization before the four images can work
offline. The Android 1.0.0.1 debug packaging shares this cache implementation.

Inspect the current state using `window.backroomOfflineCharacterAssets.status()`.
The `offlineReady` flag is true only when all four images are cached.
