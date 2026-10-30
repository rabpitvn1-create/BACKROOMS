#!/bin/sh
set -eu
# From an authenticated clone of https://github.com/rabpitvn1-create/Backrooms
# Run: sh /path/to/recovery/publish.sh /path/to/clone /path/to/recovery
repo="$1"
bundle="$2"
mkdir -p "$repo/apk-original" "$repo/web"
cp -R "$bundle/apk-original/." "$repo/apk-original/"
cp -R "$bundle/web/." "$repo/web/"
cp "$bundle/original-CAO_MINH-1.0.0.0.8.apk" "$repo/"
cp "$bundle/apk-file-manifest.json" "$bundle/README.md" "$repo/"
cp "$bundle/web/index.html" "$repo/index.html"
# Root HTML assets are relative. Copy web content to repository root for Pages.
cp -R "$bundle/web/." "$repo/"
cd "$repo"
git add -A
git commit -m "Recover complete APK contents and Cloudinary-backed web game"
git push origin HEAD:main
