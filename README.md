# BACKROOMS APK recovery

Extracted from `CAO_MINH-1.0.0.0.8.apk` (70 APK entries).

- `apk-original/`: byte-identical contents of all 70 APK entries, including compiled `classes*.dex` and Android manifest.
- `web/`: recovered webview game HTML, JavaScript, CSS, game data, music and fonts, with all existing Cloudinary URLs preserved.
- `web/personal-provider-config.json`: empty web configuration. The old provider configuration is not included in this repository. Debug packaging generates the current configuration from the six current primary Secrets.
- `apk-file-manifest.json`: original file hashes for precise audit.

Note: Android `classes*.dex` are compiled bytecode, not the original Kotlin/Java source. This extraction cannot recreate missing Gradle/build configuration. The APK manifest references some `characters/` and `item-icons/` media files that are not embedded in the APK; these need to be resolved from Cloudinary or other source to make all visuals work.

For a static GitHub Pages website, publish the contents of `web/` as the repository root. APK code relying on the Android JavaScript bridge will only work in an Android wrapper.

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
