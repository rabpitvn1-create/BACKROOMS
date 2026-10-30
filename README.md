# BACKROOMS APK recovery

Extracted from `CAO_MINH-1.0.0.0.8.apk` (70 APK entries).

- `apk-original/`: byte-identical contents of all 70 APK entries, including compiled `classes*.dex` and Android manifest.
- `web/`: recovered webview game HTML, JavaScript, CSS, game data, music and fonts, with all existing Cloudinary URLs preserved.
- `web/personal-provider-config.json`: empty web configuration. The old provider configuration is not included in this repository. Debug packaging generates the current configuration from the repository Secret `Argon_api_key`.
- `apk-file-manifest.json`: original file hashes for precise audit.

Note: Android `classes*.dex` are compiled bytecode, not the original Kotlin/Java source. This extraction cannot recreate missing Gradle/build configuration. The APK manifest references some `characters/` and `item-icons/` media files that are not embedded in the APK; these need to be resolved from Cloudinary or other source to make all visuals work.

For a static GitHub Pages website, publish the contents of `web/` as the repository root. APK code relying on the Android JavaScript bridge will only work in an Android wrapper.

## Personal Android debug release 1.0.0.0

The `Backrooms debug release` workflow creates `Backrooms-1.0.0.0-debug.apk`
from the recovered APK entries, with `android:debuggable=true` and versionCode
90000001. It does not claim to recover the original Android source project.

`Argon_api_key` is the only provider Secret referenced by this workflow. A raw native
Google key uses the existing Gemini router with that key only. Any other raw key
uses the existing endpoint `https://api.vilao.ai/v1` and model
`chib/gemini-3.8-flash`. A JSON Secret may explicitly supply `api_key`, `base_url`,
and `model` instead. A real JSON-generation request must pass before an APK is
published; no previous key is available as fallback.

The old compiled configuration validator required six distinct primary keys
and a separately configured proxy. The packaging script patches only that
method to return the generated configuration, validates configuration before
packaging, and verifies every other recovered entry remains byte-identical
(except the manifest, credential DEX, and generated configuration). Its input
DEX hash is pinned so upstream bytecode changes require review.

The workflow verifies APK signing and metadata, installs and launches the app
on an Android 15 emulator, and publishes tag `v1.0.0.0` only after success.
Run it manually, or push a packaging change whose commit message includes
`[release-debug]`. An existing release is never replaced automatically.

A standard Android development signing key is generated inside the runner.
It is not committed or attached to the release. This signature differs from the
unavailable original APK signing key and is regenerated on a new run. Android
may require uninstalling a differently signed build; export any saves first.
