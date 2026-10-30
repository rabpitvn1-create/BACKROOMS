"""Package the recovered Android debug app with only the current Agron setting.

The repository contains compiled DEX, not the original Android source project.
Only the manifest and ProviderCredentials.validateConfig are patched. The latter
previously required six different primary keys and an additional fallback key;
validation now happens before packaging so a single current key is supported.
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


def configuration(raw):
    raw = raw.strip()
    if not raw:
        fail("Repository Secret Argon_api_key is missing or empty.")
    data = {}
    if raw.startswith("{"):
        try:
            data = json.loads(raw)
        except ValueError:
            fail("Agron contains invalid JSON.")
        if not isinstance(data, dict):
            fail("Agron must be an API key or a JSON object.")
        key = next((data[k] for k in ("api_key", "apiKey", "API_KEY", "AGRON_API_KEY",
                                     "key", "GEMINI_4_API_KEY", "GEMINI_API_KEY_1")
                    if data.get(k)), "")
    else:
        key = raw
    if not isinstance(key, str) or not key.strip() or re.search(r"\s", key):
        fail("Agron does not contain a valid API key value.")
    key = key.strip()
    base = next((data[k] for k in ("base_url", "baseUrl", "BASE_URL", "GEMINI_4_BASE_URL")
                 if data.get(k)), "")
    model = next((data[k] for k in ("model", "MODEL", "GEMINI_4_MODEL") if data.get(k)), "")
    native = key.startswith("AIza") and not base
    config = {f"GEMINI_API_KEY_{i}": key if native else "" for i in range(1, 7)}
    config.update(GEMINI_4_API_KEY="" if native else key,
                  GEMINI_4_BASE_URL="" if native else (base or DEFAULT_BASE),
                  GEMINI_4_MODEL="" if native else (model or DEFAULT_MODEL))
    if not all(isinstance(v, str) and len(v) <= 1024 for v in config.values()):
        fail("Agron configuration contains invalid values.")
    if not native:
        url = urllib.parse.urlsplit(config["GEMINI_4_BASE_URL"])
        if url.scheme != "https" or not url.hostname or url.username or url.password or url.query or url.fragment:
            fail("Agron base URL is invalid.")
    return config, native


def probe(config, native):
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
        print(f"Current Agron generation probe passed ({'Google Gemini' if native else 'configured endpoint'}; model {model}).")
        return
    fail("Agron generation probe failed: " + ", ".join(statuses) + ". No APK has been published.")


def uleb(b, offset):
    value = shift = 0
    while True:
        c = b[offset]
        offset += 1
        value |= (c & 127) << shift
        if c < 128:
            return value, offset
        shift += 7
        if shift > 28:
            fail("Invalid DEX ULEB128.")


def patch_dex(original):
    if hashlib.sha256(original).hexdigest() != DEX_SHA256:
        fail("Recovered DEX changed; review the credential patch before building.")
    b = bytearray(original)
    sn, so, tn, to, _, _, _, _, _, mo, cn, co = struct.unpack_from("<12I", b, 56)
    strings = []
    for i in range(sn):
        offset = struct.unpack_from("<I", b, so + 4 * i)[0]
        _, offset = uleb(b, offset)
        strings.append(b[offset:b.index(0, offset)].decode("utf8", errors="replace"))
    types = [strings[struct.unpack_from("<I", b, to + 4 * i)[0]] for i in range(tn)]
    patches = 0
    for i in range(cn):
        cls, _, _, _, _, _, offset, _ = struct.unpack_from("<8I", b, co + 32 * i)
        if types[cls] != "Lcom/rabpit/backroom/ProviderCredentials;":
            continue
        counts = []
        for _ in range(4):
            value, offset = uleb(b, offset)
            counts.append(value)
        for _ in range(counts[0] + counts[1]):
            _, offset = uleb(b, offset)
            _, offset = uleb(b, offset)
        for count in counts[2:]:
            index = 0
            for _ in range(count):
                delta, offset = uleb(b, offset)
                index += delta
                flags, offset = uleb(b, offset)
                code, offset = uleb(b, offset)
                _, _, name = struct.unpack_from("<HHI", b, mo + 8 * index)
                if strings[name] != "validateConfig":
                    continue
                regs, ins, _, tries, _, size = struct.unpack_from("<HHHHII", b, code)
                if not flags & 8 or ins != 1 or tries or regs - ins > 255:
                    fail("Unexpected credential method shape.")
                # return-object p0; remaining original instruction space is NOPs.
                # A final unreachable return also keeps the method ending explicit.
                instruction = struct.pack("<H", 0x11 | ((regs - ins) << 8))
                b[code + 16:code + 16 + size * 2] = instruction + bytes((size - 2) * 2) + instruction
                patches += 1
    if patches != 1:
        fail("Credential method was not found exactly once.")
    b[12:32] = hashlib.sha1(b[32:]).digest()
    struct.pack_into("<I", b, 8, zlib.adler32(b[12:]) & 0xffffffff)
    return bytes(b)


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
    expected = {"AndroidManifest.xml", "classes5.dex", "assets/personal-provider-config.json"}
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
            elif name == "classes5.dex":
                content = patch_dex(content)
            apk.writestr(name, content)
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
    parser.add_argument("--output", type=Path, default=ROOT / "build/Backrooms-1.0.0.0-debug-unsigned.apk")
    args = parser.parse_args()
    config, native = configuration(os.environ.get("AGRON", ""))
    if args.probe:
        probe(config, native)
    package(config, args.output)


if __name__ == "__main__":
    main()
