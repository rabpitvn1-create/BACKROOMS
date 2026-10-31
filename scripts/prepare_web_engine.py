"""Rebuild JVM class files from the unchanged APK's gameplay DEX files."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import subprocess
import urllib.request
import struct
import level_travel

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / "build/web-engine-tools"
HASHES = {
    "classes3.dex": "6db710987a94854a30fc4a9e51796d652d0bfc2171c94033abd2193d80d2b132",
    "classes4.dex": "e43db82b73e23e4ff964a9ba4863162d640d82d36abb9426bed6b3af8117ac49",
}


def browser_compat_class(data):
    # Remap only unavailable JRE adapters; gameplay method bodies stay unchanged.
    count = struct.unpack_from(">H", data, 8)[0]
    out = bytearray(data[:10]);p=10;i=1
    widths={3:4,4:4,5:8,6:8,7:2,8:2,9:4,10:4,11:4,12:4,15:3,16:2,17:4,18:4,19:2,20:2}
    while i<count:
        tag=data[p];p+=1;out.append(tag)
        if tag==1:
            n=struct.unpack_from(">H",data,p)[0];p+=2
            value=data[p:p+n];p+=n
            value=value.replace(b"java/security/MessageDigest",b"backrooms/compat/MessageDigest")
            value=value.replace(b"java/text/Normalizer",b"backrooms/compat/Normalizer")
            if value in (b"kotlin/text/Regex",b"kotlin/text/Regex$Companion"):
                value=value.replace(b"kotlin/text/Regex",b"backrooms/compat/Regex")
            value=value.replace(b"Lkotlin/text/Regex;",b"Lbackrooms/compat/Regex;")
            value=value.replace(b"Lkotlin/text/Regex$Companion;",b"Lbackrooms/compat/Regex$Companion;")
            out.extend(struct.pack(">H",len(value)));out.extend(value)
        else:
            n=widths[tag];out.extend(data[p:p+n]);p+=n
            if tag in (5,6):i+=1
        i+=1
    out.extend(data[p:]);return bytes(out)


def prepare():
    TOOLS.mkdir(parents=True, exist_ok=True)
    archive = TOOLS / "dex-tools.zip"
    if not archive.exists():
        urllib.request.urlretrieve("https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip", archive)
    assert hashlib.sha256(archive.read_bytes()).hexdigest() == "ee7c45eb3c1d2474a6145d8d447e651a736a22d9664b6d3d3be5a5a817dda23a"
    with ZipFile(archive) as z:
        z.extractall(TOOLS)
    jars = []
    for name, digest in HASHES.items():
        source = ROOT / "apk-original" / name
        assert hashlib.sha256(source.read_bytes()).hexdigest() == digest
        target = TOOLS / (name + ".jar")
        subprocess.run(["java", "-Xmx2g", "-cp", str(TOOLS / "dex-tools-v2.4/lib/*"),
                        "com.googlecode.dex2jar.tools.Dex2jarCmd", str(source), "-o", str(target), "--force"], check=True)
        jars.append(target)
    output = ROOT / "engine/lib/recovered-core.jar"
    output.parent.mkdir(parents=True, exist_ok=True)
    count = 0
    with ZipFile(output, "w", ZIP_DEFLATED) as out:
        for path in jars:
            with ZipFile(path) as z:
                for name in z.namelist():
                    if name.startswith("com/rabpit/backroom/core/") and name.endswith(".class"):
                        out.writestr(name, level_travel.patch(name, browser_compat_class(z.read(name))))
                        count += 1
    print(f"Recovered {count} original gameplay classes for the browser compiler.")


if __name__ == "__main__":
    prepare()

