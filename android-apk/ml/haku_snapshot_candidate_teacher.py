#!/usr/bin/env python3
"""Build deterministic Snapshot light candidates and ask HAKU only for semantics.

Geometry is derived from the same luminance/local-contrast connected-component rules
used by SnapshotLightAnalyzer v3. HAKU never supplies authoritative coordinates.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
import math
import os
import pathlib
import tempfile
import urllib.error
import urllib.request
from collections import deque
from typing import Any, Iterable, Sequence

from haku_snapshot_teacher import DEFAULT_API_URL, DEFAULT_MODEL, SYSTEM_PROMPT, TeacherError

W = 144
H = 81
RADIUS = 4
DETECTOR_VERSION = "snapshot-light-v3-candidate-geometry"

CANDIDATE_PROMPT = """You are classifying ONE deterministic bright-region candidate in a Backrooms Snapshot.
The candidate is marked by a magenta rectangle in the supplied image.
Decide whether the marked region is a real visible light-emitting fixture/source that the renderer
should attach a glow to. Reject bright walls/floors, reflections, windows that merely look bright,
painted bloom, UI, character/entity sprites, and unrelated bright objects.

Return exactly ONE JSON object with this schema and no other text:
{"fixture":true,"kind":"linear","confidence":0.0}

Rules:
- fixture is true or false.
- when fixture=true, kind is "linear" or "point".
- when fixture=false, kind is "none".
- confidence is in [0,1].
- do not return coordinates. Geometry comes from the deterministic detector and must not be re-estimated."""


def _number(value: Any, name: str) -> float:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise TeacherError(f"{name} must be numeric")
    result = float(value)
    if not math.isfinite(result):
        raise TeacherError(f"{name} must be finite")
    return result


def validate_candidate_label(payload: Any) -> dict[str, Any]:
    if not isinstance(payload, dict):
        raise TeacherError("candidate label must be an object")
    if set(payload) != {"fixture", "kind", "confidence"}:
        raise TeacherError("candidate label keys must be exactly fixture,kind,confidence")
    fixture = payload["fixture"]
    if not isinstance(fixture, bool):
        raise TeacherError("fixture must be boolean")
    kind = payload["kind"]
    if fixture and kind not in {"linear", "point"}:
        raise TeacherError("fixture=true requires kind linear or point")
    if not fixture and kind != "none":
        raise TeacherError("fixture=false requires kind none")
    confidence = _number(payload["confidence"], "confidence")
    if not 0.0 <= confidence <= 1.0:
        raise TeacherError("confidence out of bounds")
    return {"fixture": fixture, "kind": kind, "confidence": confidence}


def parse_candidate_content(content: Any) -> dict[str, Any]:
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

    decoder = json.JSONDecoder()
    labels: list[dict[str, Any]] = []
    cursor = 0
    while cursor < len(text):
        while cursor < len(text) and text[cursor].isspace():
            cursor += 1
        if cursor >= len(text):
            break
        try:
            payload, end = decoder.raw_decode(text, cursor)
        except json.JSONDecodeError as error:
            raise TeacherError(f"HAKU returned invalid candidate JSON: {error}") from error
        labels.append(validate_candidate_label(payload))
        cursor = end
    if not labels:
        raise TeacherError("HAKU returned an empty candidate response")
    first = labels[0]
    if any(label != first for label in labels[1:]):
        raise TeacherError("HAKU returned multiple disagreeing candidate objects")
    return first


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


def _image_data_url(path: pathlib.Path) -> str:
    suffix = path.suffix.lower()
    mime = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".webp": "image/webp"}.get(suffix)
    if not mime:
        raise TeacherError(f"unsupported candidate image type: {path}")
    data = path.read_bytes()
    if not data:
        raise TeacherError(f"candidate image is empty: {path}")
    return f"data:{mime};base64,{base64.b64encode(data).decode('ascii')}"


def call_haku_candidate(
    marked_image: pathlib.Path,
    *,
    pass_index: int,
    api_key: str,
    api_url: str,
    model: str,
    timeout: int,
) -> dict[str, Any]:
    prompt = CANDIDATE_PROMPT + f"\nIndependent semantic pass {pass_index}."
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image_url", "image_url": {"url": _image_data_url(marked_image)}},
                ],
            },
        ],
        "temperature": 0,
        "max_tokens": 120,
        "stream": False,
    }
    request = urllib.request.Request(
        api_url,
        data=json.dumps(body, separators=(",", ":")).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {api_key}"},
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
    return parse_candidate_content(_extract_choice_content(payload))


def consensus_candidate(labels: Sequence[dict[str, Any]]) -> dict[str, Any]:
    if not labels:
        raise TeacherError("no candidate labels to combine")
    normalized = [validate_candidate_label(label) for label in labels]
    fixtures = {label["fixture"] for label in normalized}
    if len(fixtures) != 1:
        raise TeacherError("teacher passes disagree whether candidate is a fixture")
    fixture = normalized[0]["fixture"]
    kinds = {label["kind"] for label in normalized}
    if len(kinds) != 1:
        raise TeacherError("teacher passes disagree on candidate kind")
    return {
        "fixture": fixture,
        "kind": normalized[0]["kind"],
        "confidence": sum(label["confidence"] for label in normalized) / len(normalized),
    }


def _luma(pixel: Sequence[int]) -> float:
    return (0.2126 * pixel[0] + 0.7152 * pixel[1] + 0.0722 * pixel[2]) / 255.0


def extract_candidates_from_rgb(
    pixels: Sequence[Sequence[int]],
    *,
    width: int = W,
    height: int = H,
    max_candidates: int = 24,
) -> list[dict[str, Any]]:
    if width <= 0 or height <= 0 or len(pixels) != width * height:
        raise TeacherError("invalid RGB frame dimensions")
    luma = [_luma(pixel) for pixel in pixels]
    integral = [[0.0] * (width + 1) for _ in range(height + 1)]
    for y in range(height):
        row_sum = 0.0
        for x in range(width):
            row_sum += luma[y * width + x]
            integral[y + 1][x + 1] = integral[y][x + 1] + row_sum

    contrast = [0.0] * (width * height)
    mask = [False] * (width * height)
    for y in range(height):
        for x in range(width):
            x0 = max(0, x - RADIUS)
            x1 = min(width - 1, x + RADIUS)
            y0 = max(0, y - RADIUS)
            y1 = min(height - 1, y + RADIUS)
            total = (
                integral[y1 + 1][x1 + 1]
                - integral[y0][x1 + 1]
                - integral[y1 + 1][x0]
                + integral[y0][x0]
            )
            mean = total / ((x1 - x0 + 1) * (y1 - y0 + 1))
            index = y * width + x
            c = max(0.0, luma[index] - mean)
            contrast[index] = c
            mask[index] = luma[index] >= 0.76 and (c >= 0.028 or luma[index] >= 0.93)

    seen = [False] * len(mask)
    found: list[dict[str, Any]] = []
    neighbors = ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1))
    for start, active in enumerate(mask):
        if not active or seen[start]:
            continue
        queue: deque[int] = deque([start])
        seen[start] = True
        area = 0
        min_x, min_y = width, height
        max_x = max_y = 0
        sum_luma = 0.0
        sum_contrast = 0.0
        while queue:
            p = queue.popleft()
            x, y = p % width, p // width
            area += 1
            min_x, max_x = min(min_x, x), max(max_x, x)
            min_y, max_y = min(min_y, y), max(max_y, y)
            sum_luma += luma[p]
            sum_contrast += contrast[p]
            for dx, dy in neighbors:
                nx, ny = x + dx, y + dy
                if 0 <= nx < width and 0 <= ny < height:
                    q = ny * width + nx
                    if mask[q] and not seen[q]:
                        seen[q] = True
                        queue.append(q)

        box_w = max_x - min_x + 1
        box_h = max_y - min_y + 1
        box_area = box_w * box_h
        aspect = box_w / max(1, box_h)
        fill = area / max(1, box_area)
        avg_luma = sum_luma / max(1, area)
        avg_contrast = sum_contrast / max(1, area)
        linear = aspect >= 1.35 or aspect <= 0.74
        point = area <= 24 and 0.65 <= aspect <= 1.55
        if (
            area < 2
            or area > width * height * 0.075
            or fill < 0.28
            or min_y > height * 0.90
            or (not linear and not point)
        ):
            continue
        detector_confidence = min(
            0.99,
            max(0.0, 0.50 + avg_luma * 0.25 + avg_contrast * 1.65 + min(0.12, math.sqrt(area) * 0.012)),
        )
        found.append(
            {
                "x": (min_x + max_x + 1) / 2.0 / width,
                "y": (min_y + max_y + 1) / 2.0 / height,
                "w": box_w / width,
                "h": box_h / height,
                "kind_hint": "linear" if linear else "point",
                "area": area,
                "fill": fill,
                "aspect": aspect,
                "avg_luma": avg_luma,
                "avg_contrast": avg_contrast,
                "detector_confidence": detector_confidence,
                "pixel_box": [min_x, min_y, max_x, max_y],
            }
        )
    found.sort(key=lambda c: c["detector_confidence"] * math.sqrt(c["area"]), reverse=True)
    return found[:max_candidates]


def load_candidates(path: pathlib.Path, max_candidates: int) -> tuple[Any, list[dict[str, Any]]]:
    try:
        from PIL import Image
    except ImportError as error:
        raise TeacherError("Pillow is required for real Snapshot candidate extraction") from error
    image = Image.open(path).convert("RGB")
    resampling = getattr(Image, "Resampling", Image)
    scaled = image.resize((W, H), resample=resampling.BILINEAR)
    if hasattr(scaled, "get_flattened_data"):
        pixels = list(scaled.get_flattened_data())
    else:
        pixels = list(scaled.getdata())
    return image, extract_candidates_from_rgb(pixels, max_candidates=max_candidates)


def mark_candidate(image: Any, candidate: dict[str, Any], output: pathlib.Path) -> None:
    try:
        from PIL import ImageDraw
    except ImportError as error:
        raise TeacherError("Pillow is required to mark Snapshot candidates") from error
    marked = image.copy()
    draw = ImageDraw.Draw(marked)
    image_w, image_h = marked.size
    x, y, w, h = candidate["x"], candidate["y"], candidate["w"], candidate["h"]
    left = max(0, int(round((x - w / 2) * image_w)))
    top = max(0, int(round((y - h / 2) * image_h)))
    right = min(image_w - 1, int(round((x + w / 2) * image_w)))
    bottom = min(image_h - 1, int(round((y + h / 2) * image_h)))
    stroke = max(2, min(image_w, image_h) // 120)
    for offset in range(stroke):
        draw.rectangle(
            [max(0, left - offset), max(0, top - offset), min(image_w - 1, right + offset), min(image_h - 1, bottom + offset)],
            outline=(255, 0, 255),
        )
    output.parent.mkdir(parents=True, exist_ok=True)
    marked.save(output, format="PNG")


def discover_images(patterns: Iterable[str], limit: int | None) -> list[pathlib.Path]:
    found: dict[str, pathlib.Path] = {}
    for pattern in patterns:
        for path in pathlib.Path(".").glob(pattern):
            if path.is_file():
                found[str(path.resolve())] = path
    images = sorted(found.values(), key=lambda path: str(path))
    if limit is not None:
        images = images[:limit]
    if not images:
        raise TeacherError("no Snapshot images matched")
    return images


def run(args: argparse.Namespace, api_key: str) -> dict[str, int]:
    images = discover_images(args.glob, args.limit)
    output = pathlib.Path(args.output)
    output.parent.mkdir(parents=True, exist_ok=True)
    accepted = rejected = fixtures = negatives = total_candidates = 0
    with output.open("w", encoding="utf-8") as handle, tempfile.TemporaryDirectory(prefix="snapshot-candidate-teacher-") as tmp:
        temp_root = pathlib.Path(tmp)
        for image_path in images:
            sha256 = hashlib.sha256(image_path.read_bytes()).hexdigest()
            image, candidates = load_candidates(image_path, args.max_candidates)
            if not candidates:
                handle.write(json.dumps({
                    "status": "no_candidates",
                    "image": str(image_path),
                    "sha256": sha256,
                    "detector": DETECTOR_VERSION,
                }, separators=(",", ":")) + "\n")
                continue
            for index, candidate in enumerate(candidates):
                total_candidates += 1
                marked = temp_root / f"candidate_{hashlib.sha1(str(image_path).encode()).hexdigest()[:8]}_{index}.png"
                mark_candidate(image, candidate, marked)
                try:
                    labels = [
                        call_haku_candidate(
                            marked,
                            pass_index=pass_index + 1,
                            api_key=api_key,
                            api_url=args.api_url,
                            model=args.model,
                            timeout=args.timeout,
                        )
                        for pass_index in range(args.passes)
                    ]
                    teacher = consensus_candidate(labels)
                    accepted += 1
                    fixtures += int(teacher["fixture"])
                    negatives += int(not teacher["fixture"])
                    record = {
                        "status": "accepted",
                        "image": str(image_path),
                        "sha256": sha256,
                        "detector": DETECTOR_VERSION,
                        "candidate_index": index,
                        "candidate": candidate,
                        "teacher_model": args.model,
                        "passes": args.passes,
                        "teacher": teacher,
                    }
                except Exception as error:
                    rejected += 1
                    record = {
                        "status": "rejected",
                        "image": str(image_path),
                        "sha256": sha256,
                        "detector": DETECTOR_VERSION,
                        "candidate_index": index,
                        "candidate": candidate,
                        "teacher_model": args.model,
                        "passes": args.passes,
                        "reason": str(error)[:800],
                    }
                handle.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
            image.close()
    if total_candidates == 0:
        raise TeacherError("deterministic detector produced zero Snapshot candidates")
    if accepted == 0:
        raise TeacherError("HAKU produced zero accepted candidate labels")
    return {
        "images": len(images),
        "candidates": total_candidates,
        "accepted": accepted,
        "rejected": rejected,
        "fixtures": fixtures,
        "negatives": negatives,
    }


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("--api-url", default=os.environ.get("HAKU_API_URL", DEFAULT_API_URL))
    parser.add_argument("--model", default=os.environ.get("HAKU_SNAPSHOT_MODEL", DEFAULT_MODEL))
    parser.add_argument("--timeout", type=int, default=60)
    parser.add_argument(
        "--glob",
        action="append",
        default=["android-apk/app/src/main/assets/level_snapshots/*.webp"],
    )
    parser.add_argument("--limit", type=int, default=None)
    parser.add_argument("--passes", type=int, default=3)
    parser.add_argument("--max-candidates", type=int, default=24)
    parser.add_argument("--output", default="snapshot_teacher/haku_snapshot_candidate_labels.jsonl")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    if args.passes < 1:
        raise SystemExit("--passes must be >= 1")
    if args.max_candidates < 1:
        raise SystemExit("--max-candidates must be >= 1")
    api_key = os.environ.get("HAKU_API_KEY", "").strip()
    if not api_key:
        raise SystemExit("HAKU_API_KEY is required")
    summary = run(args, api_key)
    print(json.dumps(summary, sort_keys=True))


if __name__ == "__main__":
    main()
