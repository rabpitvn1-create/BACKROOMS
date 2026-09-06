#!/usr/bin/env python3
"""Use HAKU as a vision teacher for Snapshot light labels.

This module never trains a model itself. It verifies that the configured HAKU
endpoint can actually consume image input, then produces validated/consensus
JSONL labels that a later TensorFlow/LiteRT training stage can trust.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import math
import mimetypes
import os
import pathlib
import struct
import tempfile
import urllib.error
import urllib.request
import zlib
from typing import Any, Iterable

DEFAULT_API_URL = "https://api.vilao.ai/v1/chat/completions"
DEFAULT_MODEL = "claude-haiku-4-5-20251001"
MAX_LIGHTS = 8

SYSTEM_PROMPT = (
    "Return exactly one valid JSON object and nothing else. "
    "Never use markdown or code fences. Do not invent fields."
)

TEACHER_PROMPT = """Analyze this Backrooms Snapshot background as a light-fixture detector.
Label only visible light-emitting fixtures/sources that the renderer should attach a glow to.
Do NOT label reflections, white walls/floors, windows merely because they are bright,
character/entity sprites, UI, or bloom already painted into the image.

Return exactly:
{"lights":[{"x":0.0,"y":0.0,"w":0.0,"h":0.0,"kind":"linear|point","confidence":0.0}],"ambient":0.0}

Coordinates are normalized to [0,1]. x/y are the fixture BOUNDING-BOX CENTER.
w/h are normalized fixture bounding-box size. ambient is scene ambient brightness [0,1].
Return at most 8 lights. Return an empty lights array when there is no valid fixture."""

PROBE_PROMPT = """This is a synthetic capability probe, not a game scene.
Treat the single bright horizontal rectangle in the supplied image as the only light fixture.
Infer its location from the IMAGE. Do not guess coordinates from this text.
Return exactly:
{"lights":[{"x":0.0,"y":0.0,"w":0.0,"h":0.0,"kind":"linear","confidence":0.0}],"ambient":0.0}
Coordinates are normalized to [0,1]; x/y are bounding-box center and w/h are size."""


class TeacherError(RuntimeError):
    pass


def _number(value: Any, name: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TeacherError(f"{name} must be numeric")
    result = float(value)
    if not math.isfinite(result):
        raise TeacherError(f"{name} must be finite")
    return result


def validate_annotation(payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise TeacherError("annotation must be a JSON object")
    if set(payload) != {"lights", "ambient"}:
        raise TeacherError(f"annotation keys must be exactly lights,ambient; got {sorted(payload)}")
    lights = payload["lights"]
    if not isinstance(lights, list):
        raise TeacherError("lights must be an array")
    if len(lights) > MAX_LIGHTS:
        raise TeacherError(f"too many lights: {len(lights)} > {MAX_LIGHTS}")

    normalized: list[dict[str, Any]] = []
    for index, raw in enumerate(lights):
        if not isinstance(raw, dict):
            raise TeacherError(f"lights[{index}] must be an object")
        expected = {"x", "y", "w", "h", "kind", "confidence"}
        if set(raw) != expected:
            raise TeacherError(f"lights[{index}] keys mismatch")
        x = _number(raw["x"], f"lights[{index}].x")
        y = _number(raw["y"], f"lights[{index}].y")
        w = _number(raw["w"], f"lights[{index}].w")
        h = _number(raw["h"], f"lights[{index}].h")
        confidence = _number(raw["confidence"], f"lights[{index}].confidence")
        kind = raw["kind"]
        if kind not in {"linear", "point"}:
            raise TeacherError(f"lights[{index}].kind must be linear or point")
        if not (0.0 <= x <= 1.0 and 0.0 <= y <= 1.0):
            raise TeacherError(f"lights[{index}] center out of bounds")
        if not (0.0 < w <= 1.0 and 0.0 < h <= 1.0):
            raise TeacherError(f"lights[{index}] size out of bounds")
        if not (0.0 <= confidence <= 1.0):
            raise TeacherError(f"lights[{index}].confidence out of bounds")
        if x - w / 2 < -0.03 or x + w / 2 > 1.03 or y - h / 2 < -0.03 or y + h / 2 > 1.03:
            raise TeacherError(f"lights[{index}] box extends too far outside image")
        normalized.append(
            {"x": x, "y": y, "w": w, "h": h, "kind": kind, "confidence": confidence}
        )

    ambient = _number(payload["ambient"], "ambient")
    if not 0.0 <= ambient <= 1.0:
        raise TeacherError("ambient out of bounds")
    return {"lights": normalized, "ambient": ambient}


def parse_message_content(content: Any) -> dict[str, Any]:
    if isinstance(content, str):
        text = content.strip()
    elif isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if isinstance(item, dict) and item.get("type") in {"text", "output_text"}:
                value = item.get("text")
                if isinstance(value, str):
                    parts.append(value)
        text = "".join(parts).strip()
    else:
        raise TeacherError("HAKU response content is not text")

    if text.startswith("```"):
        lines = text.splitlines()
        if lines and lines[0].startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        text = "\n".join(lines).strip()
    try:
        return validate_annotation(json.loads(text))
    except json.JSONDecodeError as error:
        raise TeacherError(f"HAKU returned invalid JSON: {error}") from error


def _extract_choice_content(response: Any) -> Any:
    if not isinstance(response, dict):
        raise TeacherError("HAKU response root is not an object")
    choices = response.get("choices")
    if not isinstance(choices, list) or not choices:
        raise TeacherError("HAKU response has no choices")
    first = choices[0]
    if not isinstance(first, dict) or not isinstance(first.get("message"), dict):
        raise TeacherError("HAKU response choice has no message")
    return first["message"].get("content")


def _mime_for(path: pathlib.Path) -> str:
    suffix = path.suffix.lower()
    if suffix == ".webp":
        return "image/webp"
    if suffix in {".jpg", ".jpeg"}:
        return "image/jpeg"
    if suffix == ".png":
        return "image/png"
    guessed, _ = mimetypes.guess_type(path.name)
    if guessed and guessed.startswith("image/"):
        return guessed
    raise TeacherError(f"unsupported image type: {path}")


def image_data_url(path: pathlib.Path) -> str:
    data = path.read_bytes()
    if not data:
        raise TeacherError(f"image is empty: {path}")
    return f"data:{_mime_for(path)};base64,{base64.b64encode(data).decode('ascii')}"


def call_haku(
    image_path: pathlib.Path,
    prompt: str,
    *,
    api_key: str,
    api_url: str,
    model: str,
    timeout: int = 60,
) -> dict[str, Any]:
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image_url", "image_url": {"url": image_data_url(image_path)}},
                ],
            },
        ],
        "temperature": 0,
        "max_tokens": 1200,
        "stream": False,
    }
    request = urllib.request.Request(
        api_url,
        data=json.dumps(body, separators=(",", ":")).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:800]
        raise TeacherError(f"HAKU HTTP {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise TeacherError(f"HAKU connection failed: {error.reason}") from error

    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        raise TeacherError("HAKU HTTP response was not JSON") from error
    return parse_message_content(_extract_choice_content(payload))


def _png_chunk(kind: bytes, payload: bytes) -> bytes:
    return (
        struct.pack(">I", len(payload))
        + kind
        + payload
        + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
    )


def write_probe_png(path: pathlib.Path, width: int = 144, height: int = 81) -> dict[str, float]:
    x0, x1 = 17, 88
    y0, y1 = 11, 20
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            bright = x0 <= x <= x1 and y0 <= y <= y1
            value = 255 if bright else 6
            rows.extend((value, value, value))
    header = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n"
    png += _png_chunk(b"IHDR", header)
    png += _png_chunk(b"IDAT", zlib.compress(bytes(rows), 9))
    png += _png_chunk(b"IEND", b"")
    path.write_bytes(png)
    return {
        "x": ((x0 + x1 + 1) / 2) / width,
        "y": ((y0 + y1 + 1) / 2) / height,
        "w": (x1 - x0 + 1) / width,
        "h": (y1 - y0 + 1) / height,
    }


def assess_probe(annotation: dict[str, Any], expected: dict[str, float]) -> None:
    lights = annotation["lights"]
    if len(lights) != 1:
        raise TeacherError(f"vision probe expected exactly one light, got {len(lights)}")
    light = lights[0]
    if light["kind"] != "linear":
        raise TeacherError(f"vision probe expected linear fixture, got {light['kind']}")
    center_error = math.hypot(light["x"] - expected["x"], light["y"] - expected["y"])
    if center_error > 0.12:
        raise TeacherError(f"vision probe center error too large: {center_error:.4f}")
    if abs(light["w"] - expected["w"]) > 0.22 or abs(light["h"] - expected["h"]) > 0.16:
        raise TeacherError("vision probe extent error too large")


def _sorted_lights(annotation: dict[str, Any]) -> list[dict[str, Any]]:
    return sorted(annotation["lights"], key=lambda light: (light["y"], light["x"], light["kind"]))


def consensus_annotation(annotations: list[dict[str, Any]]) -> dict[str, Any]:
    if not annotations:
        raise TeacherError("no annotations to combine")
    if len(annotations) == 1:
        return annotations[0]
    counts = {len(a["lights"]) for a in annotations}
    if len(counts) != 1:
        raise TeacherError(f"teacher passes disagree on light count: {sorted(counts)}")
    ordered = [_sorted_lights(a) for a in annotations]
    result_lights: list[dict[str, Any]] = []
    for index in range(len(ordered[0])):
        group = [lights[index] for lights in ordered]
        kinds = {light["kind"] for light in group}
        if len(kinds) != 1:
            raise TeacherError(f"teacher passes disagree on kind for light {index}")
        xs = [light["x"] for light in group]
        ys = [light["y"] for light in group]
        ws = [light["w"] for light in group]
        hs = [light["h"] for light in group]
        if max(xs) - min(xs) > 0.08 or max(ys) - min(ys) > 0.08:
            raise TeacherError(f"teacher passes disagree on center for light {index}")
        if max(ws) - min(ws) > 0.16 or max(hs) - min(hs) > 0.16:
            raise TeacherError(f"teacher passes disagree on extent for light {index}")
        result_lights.append(
            {
                "x": sum(xs) / len(xs),
                "y": sum(ys) / len(ys),
                "w": sum(ws) / len(ws),
                "h": sum(hs) / len(hs),
                "kind": next(iter(kinds)),
                "confidence": sum(light["confidence"] for light in group) / len(group),
            }
        )
    return validate_annotation(
        {
            "lights": result_lights,
            "ambient": sum(a["ambient"] for a in annotations) / len(annotations),
        }
    )


def discover_images(patterns: Iterable[str], limit: int | None) -> list[pathlib.Path]:
    found: dict[str, pathlib.Path] = {}
    for pattern in patterns:
        for path in pathlib.Path(".").glob(pattern):
            if path.is_file():
                found[str(path.resolve())] = path
    images = sorted(found.values(), key=lambda p: str(p))
    if limit is not None:
        images = images[:limit]
    if not images:
        raise TeacherError("no Snapshot images matched")
    return images


def run_probe(args: argparse.Namespace, api_key: str) -> dict[str, Any]:
    with tempfile.TemporaryDirectory(prefix="haku-snapshot-probe-") as tmp:
        image = pathlib.Path(tmp) / "vision_probe.png"
        expected = write_probe_png(image)
        annotation = call_haku(
            image,
            PROBE_PROMPT,
            api_key=api_key,
            api_url=args.api_url,
            model=args.model,
            timeout=args.timeout,
        )
        assess_probe(annotation, expected)
        return {
            "vision_supported": True,
            "model": args.model,
            "api_url": args.api_url,
            "expected": expected,
            "annotation": annotation,
        }


def run_annotate(args: argparse.Namespace, api_key: str) -> dict[str, int]:
    images = discover_images(args.glob, args.limit)
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    accepted = 0
    rejected = 0
    with output.open("w", encoding="utf-8") as handle:
        for image in images:
            sha256 = hashlib.sha256(image.read_bytes()).hexdigest()
            try:
                annotations = [
                    call_haku(
                        image,
                        TEACHER_PROMPT + f"\nIndependent annotation pass {index + 1}.",
                        api_key=api_key,
                        api_url=args.api_url,
                        model=args.model,
                        timeout=args.timeout,
                    )
                    for index in range(args.passes)
                ]
                consensus = consensus_annotation(annotations)
                record = {
                    "status": "accepted",
                    "image": str(image),
                    "sha256": sha256,
                    "teacher_model": args.model,
                    "passes": args.passes,
                    "annotation": consensus,
                }
                accepted += 1
            except Exception as error:
                record = {
                    "status": "rejected",
                    "image": str(image),
                    "sha256": sha256,
                    "teacher_model": args.model,
                    "passes": args.passes,
                    "reason": str(error)[:800],
                }
                rejected += 1
            handle.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
    if accepted == 0:
        raise TeacherError("teacher produced zero accepted Snapshot labels")
    return {"accepted": accepted, "rejected": rejected, "total": len(images)}


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--api-url",
        default=os.environ.get("HAKU_API_URL", DEFAULT_API_URL),
        help="OpenAI-compatible chat/completions endpoint",
    )
    parser.add_argument(
        "--model",
        default=os.environ.get("HAKU_SNAPSHOT_MODEL", DEFAULT_MODEL),
    )
    parser.add_argument("--timeout", type=int, default=60)
    sub = parser.add_subparsers(dest="command", required=True)

    probe = sub.add_parser("probe")
    probe.add_argument("--output", default="snapshot_teacher/haku_snapshot_probe.json")

    annotate = sub.add_parser("annotate")
    annotate.add_argument(
        "--glob",
        action="append",
        default=["android-apk/app/src/main/assets/level_snapshots/*.webp"],
    )
    annotate.add_argument("--limit", type=int, default=None)
    annotate.add_argument("--passes", type=int, default=3)
    annotate.add_argument("--output", default="snapshot_teacher/haku_snapshot_labels.jsonl")
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    api_key = os.environ.get("HAKU_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("HAKU_API_KEY is required")
    if args.command == "probe":
        result = run_probe(args, api_key)
        output = pathlib.Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"HAKU Snapshot vision probe passed with model {args.model}")
    else:
        summary = run_annotate(args, api_key)
        print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
