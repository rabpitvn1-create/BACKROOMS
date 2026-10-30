"""Package the recovered debug APK with six current Gemini keys, then Argon.

The original credential validator and provider router remain byte-identical.
Only the manifest and generated provider configuration change.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import urllib.error
import urllib.parse
import urllib.request
import zipfile
import zlib

ROOT = Path(__file__).resolve().parents[1]
VERSION = "1.0.0.0"
VERSION_CODE = 90000001
DEX_SHA256 = "bd48603b3f302a7d39b4fcfe5a239612846b3251c40723ea9793a2ed074da5f5"
DEFAULT_BASE = "https://api.vilao.ai/v1"
DEFAULT_MODEL = "chib/gemini-3.8-flash"
PRIMARY_MODELS = ["gemini-3.6-flash", "gemini-3.5-flash-lite",
                  "gemini-3.1-flash-lite", "gemini-2.5-flash-lite"]


def fail(message):
    raise SystemExit(message)


def configuration(env=None, allow_missing_fallback=False):
    env = os.environ if env is None else env
    fields = [f"GEMINI_API_KEY_{i}" for i in range(1, 7)]
    config = {name: env.get(name, "").strip() for name in fields}
    missing = [name for name in fields if not config[name]]
    if missing:
        fail("Missing primary repository Secrets: " + ", ".join(missing))
    if len(set(config.values())) != 6:
        fail("The six primary keys must be distinct.")
    raw = env.get("ARGON", "").strip()
    if not raw and not allow_missing_fallback:
        fail("Repository Secret Argon_api_key is missing or empty.")
    data = {}
    if raw.startswith("{"):
        try:
            data = json.loads(raw)
        except ValueError:
            fail("Argon configuration contains invalid JSON.")
        if not isinstance(data, dict):
            fail("Argon configuration must be a JSON object.")
        key = next((data[k] for k in ("api_key", "apiKey", "API_KEY", "ARGON_API_KEY",
                                     "key", "GEMINI_4_API_KEY") if data.get(k)), "")
    else:
        key = raw
    base = next((data[k] for k in ("base_url", "baseUrl", "BASE_URL", "GEMINI_4_BASE_URL")
                 if data.get(k)), DEFAULT_BASE)
    model = next((data[k] for k in ("model", "MODEL", "GEMINI_4_MODEL") if data.get(k)), DEFAULT_MODEL)
    config.update(GEMINI_4_API_KEY=key, GEMINI_4_BASE_URL=base, GEMINI_4_MODEL=model)
    checked = {k: v for k, v in config.items()
               if not (allow_missing_fallback and k == "GEMINI_4_API_KEY" and not v)}
    if not all(isinstance(v, str) and v.strip() and len(v) <= 1024 for v in checked.values()):
        fail("Current provider configuration contains invalid values.")
    config = {k: v.strip() for k, v in config.items()}
    for name in fields + ["GEMINI_4_API_KEY"]:
        if re.search(r"\s", config[name]):
            fail("Invalid API key value in " + name)
    url = urllib.parse.urlsplit(config["GEMINI_4_BASE_URL"])
    if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment:
        fail("Argon base URL is invalid.")
    return config


def probe_provider(config, native, label):
    """Check an actual generation request without printing keys or response text."""
    if native:
        candidates = [(f"https://generativelanguage.googleapis.com/v1beta/models/{m}:generateContent",
                       {"x-goog-api-key": config["GEMINI_API_KEY_1"]},
                       {"contents": [{"parts": [{"text": 'Return JSON only: {"ok":true}'}]}],
                        "generationConfig": {"responseMimeType": "application/json", "maxOutputTokens": 64}}, m)
                      for m in PRIMARY_MODELS]
    else:
        base = config["GEMINI_4_BASE_URL"].rstrip("/")
        endpoint = base if base.endswith("/chat/completions") else base + "/chat/completions"
        candidates = [(endpoint, {"Authorization": "Bearer " + config["GEMINI_4_API_KEY"]},
                       {"model": config["GEMINI_4_MODEL"], "messages": [{"role": "user", "content": 'Return JSON only: {"ok":true}'}],
                        "max_tokens": 64, "temperature": 0, "stream": False}, config["GEMINI_4_MODEL"])]
    statuses = []
    for endpoint, auth, body, model in candidates:
        request = urllib.request.Request(endpoint, data=json.dumps(body).encode(),
                                         headers={"Content-Type": "application/json", **auth})
        # The Android client does not follow redirects either.
        class NoRedirect(urllib.request.HTTPRedirectHandler):
            def redirect_request(self, req, fp, code, msg, headers, newurl):
                return None
        try:
            with urllib.request.build_opener(NoRedirect).open(request, timeout=45) as response:
                result = json.loads(response.read())
            if native:
                parts = result["candidates"][0]["content"]["parts"]
                answer = "".join(p.get("text", "") for p in parts if not p.get("thought"))
            else:
                answer = result["choices"][0]["message"]["content"]
                if isinstance(answer, list):
                    answer = "".join(p.get("text", "") for p in answer)
            # Mirrors the game's acceptance of a fenced JSON answer.
            answer = re.sub(r"^```(?:json)?\s*|\s*```$", "", answer.strip())
            parsed = json.loads(answer)
            if not isinstance(parsed, dict):
                raise ValueError()
        except urllib.error.HTTPError as exc:
            statuses.append(str(exc.code))
            continue
        except Exception:
            statuses.append("transport_or_response_error")
            continue
        print(f"{label} generation probe passed (model {model}).")
        return
    fail(label + " generation probe failed: " + ", ".join(statuses) + ". No APK has been published.")


def probe(config):
    failures = []
    for i in range(1, 7):
        current = {**config, "GEMINI_API_KEY_1": config[f"GEMINI_API_KEY_{i}"]}
        try:
            probe_provider(current, True, f"Gemini K{i}")
        except SystemExit as error:
            print(str(error), flush=True)
            failures.append(f"Gemini K{i}")
    if config["GEMINI_4_API_KEY"]:
        try:
            probe_provider(config, False, "Argon final fallback")
        except SystemExit as error:
            print(str(error), flush=True)
            failures.append("Argon final fallback")
    else:
        print("Repository Secret Argon_api_key is missing or empty.", flush=True)
        failures.append("Argon final fallback (missing Secret)")
    if failures:
        fail("Provider checks incomplete: " + ", ".join(failures) + ". No APK has been published.")


def axml(b):
    """Return string locations and attributes from the recovered binary manifest."""
    strings = []
    attributes = {}
    offset = 8
    while offset < len(b):
        kind, header, size = struct.unpack_from("<HHI", b, offset)
        if size < header or not size:
            fail("Invalid Android manifest chunk.")
        if kind == 1:
            count, _, flags, start, _ = struct.unpack_from("<5I", b, offset + 8)
            if flags & 0x100:
                fail("Expected the recovered UTF-16 manifest string pool.")
            for i in range(count):
                p = offset + start + struct.unpack_from("<I", b, offset + header + i * 4)[0]
                length = struct.unpack_from("<H", b, p)[0]
                if length & 0x8000:
                    fail("Unexpected long manifest string.")
                strings.append((b[p + 2:p + 2 + length * 2].decode("utf-16le"), p, length))
        if kind == 0x102:
            _, tag, attr_start, attr_size, count = struct.unpack_from("<IIHHH", b, offset + 16)
            for i in range(count):
                p = offset + 16 + attr_start + i * attr_size
                _, name, raw, _, _, data_type, value = struct.unpack_from("<IIIHBBI", b, p)
                attributes[(strings[tag][0], strings[name][0])] = (p, raw, data_type, value)
        offset += size
    return strings, attributes


def patch_manifest(original):
    b = bytearray(original)
    strings, attrs = axml(b)
    p, raw, kind, value = attrs[("manifest", "versionName")]
    if kind != 3 or strings[value][0] != "1.0.0.0.8":
        fail("Unexpected original APK version.")
    _, pos, old_length = strings[value]
    encoded = VERSION.encode("utf-16le")
    if len(VERSION) > old_length:
        fail("Version name does not fit the recovered manifest entry.")
    struct.pack_into("<H", b, pos, len(VERSION))
    b[pos + 2:pos + 2 + old_length * 2 + 2] = encoded + bytes((old_length - len(VERSION)) * 2 + 2)
    p, _, _, old_code = attrs[("manifest", "versionCode")]
    if VERSION_CODE <= old_code:
        fail("Debug versionCode must increase.")
    struct.pack_into("<I", b, p + 16, VERSION_CODE)
    p, _, kind, value = attrs[("application", "debuggable")]
    if kind != 0x12 or not value:
        fail("Recovered APK is not a debug build.")
    return bytes(b)


def package(config, output):
    source = ROOT / "apk-original"
    expected = {"AndroidManifest.xml", "assets/personal-provider-config.json"}
    if hashlib.sha256((source / "classes5.dex").read_bytes()).hexdigest() != DEX_SHA256:
        fail("Recovered provider DEX changed; review runtime routing before building.")
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as apk:
        for p in sorted(source.rglob("*")):
            if not p.is_file():
                continue
            name = p.relative_to(source).as_posix()
            if name == "assets/personal-provider-config.json" or re.fullmatch(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)", name, re.I) or name.upper() == "META-INF/MANIFEST.MF":
                continue
            content = p.read_bytes()
            if name == "AndroidManifest.xml":
                content = patch_manifest(content)
            apk.writestr(name, content, compress_type=zipfile.ZIP_STORED if name == "resources.arsc" else zipfile.ZIP_DEFLATED)
        apk.writestr("assets/personal-provider-config.json", json.dumps(config))
    with zipfile.ZipFile(output) as apk:
        for p in source.rglob("*"):
            if p.is_file() and p.relative_to(source).as_posix() not in expected:
                name = p.relative_to(source).as_posix()
                if apk.read(name) != p.read_bytes():
                    fail("Unexpected change to recovered entry: " + name)
        strings, attrs = axml(apk.read("AndroidManifest.xml"))
        assert strings[attrs[("manifest", "versionName")][3]][0] == VERSION
        assert attrs[("manifest", "versionCode")][3] == VERSION_CODE
        assert attrs[("application", "debuggable")][3]
        assert json.loads(apk.read("assets/personal-provider-config.json")) == config
        dex = apk.read("classes5.dex")
        assert dex[12:32] == hashlib.sha1(dex[32:]).digest()
        assert struct.unpack_from("<I", dex, 8)[0] == zlib.adler32(dex[12:]) & 0xffffffff
    print(f"Recovered debug APK packaged and verified: {VERSION} ({VERSION_CODE}).")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--probe", action="store_true")
    parser.add_argument("--probe-only", action="store_true")
    parser.add_argument("--output", type=Path, default=ROOT / "build/Backrooms-1.0.0.0-debug-unsigned.apk")
    args = parser.parse_args()
    config = configuration(allow_missing_fallback=args.probe_only)
    if args.probe or args.probe_only:
        probe(config)
    if not args.probe_only:
        package(config, args.output)


if __name__ == "__main__":
    main()
