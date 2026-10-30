# BACKROOMS APK recovery

Extracted from `CAO_MINH-1.0.0.0.8.apk` (70 APK entries).

- `apk-original/`: byte-identical contents of all 70 APK entries, including compiled `classes*.dex` and Android manifest.
- `web/`: recovered webview game HTML, JavaScript, CSS, game data, music and fonts, with all existing Cloudinary URLs preserved.
- `web/personal-provider-config.json`: empty web configuration. The old provider configuration is not included in this repository. Debug packaging generates the current configuration from the six current primary Secrets.
- `apk-file-manifest.json`: original file hashes for precise audit.

Note: Android `classes*.dex` are compiled bytecode, not the original Kotlin/Java source. This extraction cannot recreate missing Gradle/build configuration. The APK manifest references some `characters/` and `item-icons/` media files that are not embedded in the APK; these need to be resolved from Cloudinary or other source to make all visuals work.

## GitHub Pages

The `Deploy GitHub Pages` workflow stages `web/` into `build/pages` and adds a
browser-only preview notice and the download link for APK debug 1.0.0.0.
The Android APK inputs and debug release are unchanged.

This deployment is a recovered UI preview. The current repository still needs
a compatible browser gameplay runtime for turns, combat, progression, and item
actions. Native action controls are disabled on the preview; local UI inspection
and menu navigation remain available. The page does not claim APK gameplay parity.

Expected address: https://rabpitvn1-create.github.io/Backrooms/

Enable GitHub Pages with source **GitHub Actions** in the repository's
Settings → Pages. The workflow uses standard Pages permissions and deploys after
mobile/desktop browser checks under the `/Backrooms/` repository subpath.
Pages enablement itself requires repository administration and cannot be
performed by the default Actions token.

Local staging: `python3 scripts/build_pages.py`.


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
