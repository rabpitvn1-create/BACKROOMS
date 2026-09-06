#!/usr/bin/env python3
"""Claude-native transport and strict response compatibility for Snapshot teachers.

The existing Snapshot teacher modules were built around an OpenAI-compatible gateway.
When ``CLAUDE_API_KEY`` is requested we instead use Anthropic's native Messages API:
``POST /v1/messages`` with ``x-api-key`` and ``anthropic-version``. Deterministic
candidate geometry and the existing strict schema validators remain authoritative.
"""
from __future__ import annotations

import base64
import json
import pathlib
import urllib.error
import urllib.request
from typing import Any, Callable

ANTHROPIC_VERSION = "2023-06-01"


def _content_text(content: Any) -> str:
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for item in content:
            if not isinstance(item, dict):
                continue
            value = item.get("text")
            if not isinstance(value, str):
                value = item.get("thinking")
            if not isinstance(value, str):
                value = item.get("content")
            if isinstance(value, str):
                parts.append(value)
        return "\n".join(parts)
    if content is None:
        return ""
    return str(content)


def extract_choice_text(response: Any, error_type: type[Exception]) -> str:
    """Compatibility parser for any older OpenAI-compatible stored responses."""
    if not isinstance(response, dict):
        raise error_type("Claude response root is not an object")
    choices = response.get("choices")
    if not isinstance(choices, list) or not choices:
        raise error_type("Claude response has no choices")
    first = choices[0]
    if not isinstance(first, dict) or not isinstance(first.get("message"), dict):
        raise error_type("Claude response choice has no message")
    message = first["message"]
    reasoning = _content_text(message.get("reasoning_content")).strip()
    content = _content_text(message.get("content")).strip()
    joined = "\n".join(part for part in (reasoning, content) if part)
    if not joined:
        raise error_type("Claude response message contained no text")
    return joined


def extract_anthropic_text(response: Any, error_type: type[Exception]) -> str:
    """Return only final text blocks from an Anthropic Messages response."""
    if not isinstance(response, dict):
        raise error_type("Claude Messages response root is not an object")
    content = response.get("content")
    if not isinstance(content, list):
        raise error_type("Claude Messages response has no content blocks")
    parts = [
        block.get("text")
        for block in content
        if isinstance(block, dict)
        and block.get("type") == "text"
        and isinstance(block.get("text"), str)
    ]
    text = "\n".join(part for part in parts if part).strip()
    if not text:
        stop_reason = response.get("stop_reason")
        raise error_type(f"Claude Messages response contained no final text; stop_reason={stop_reason!r}")
    return text


def _json_objects(text: str) -> list[Any]:
    decoder = json.JSONDecoder()
    values: list[Any] = []
    cursor = 0
    while cursor < len(text):
        start = text.find("{", cursor)
        if start < 0:
            break
        try:
            value, end = decoder.raw_decode(text, start)
        except json.JSONDecodeError:
            cursor = start + 1
            continue
        values.append(value)
        cursor = max(end, start + 1)
    return values


def select_schema_object(
    content: Any,
    validator: Callable[[Any], dict[str, Any]],
    error_type: type[Exception],
    label: str,
) -> dict[str, Any]:
    text = _content_text(content).strip()
    valid: list[dict[str, Any]] = []
    for value in _json_objects(text):
        try:
            valid.append(validator(value))
        except Exception:
            continue
    if not valid:
        preview = " ".join(text.split())[:180]
        raise error_type(f"Claude returned no valid {label} JSON object; preview={preview!r}")
    first = valid[0]
    if any(value != first for value in valid[1:]):
        raise error_type(f"Claude returned multiple disagreeing {label} JSON objects")
    return first


def _image_source(path: pathlib.Path, error_type: type[Exception]) -> dict[str, Any]:
    suffix = path.suffix.lower()
    media_type = {
        ".png": "image/png",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".webp": "image/webp",
        ".gif": "image/gif",
    }.get(suffix)
    if not media_type:
        raise error_type(f"unsupported Claude image type: {path}")
    data = path.read_bytes()
    if not data:
        raise error_type(f"Claude image is empty: {path}")
    return {
        "type": "base64",
        "media_type": media_type,
        "data": base64.b64encode(data).decode("ascii"),
    }


def call_anthropic_message(
    image_path: pathlib.Path,
    prompt: str,
    *,
    api_key: str,
    api_url: str,
    model: str,
    timeout: int,
    max_tokens: int,
    system_prompt: str,
    error_type: type[Exception],
) -> str:
    body = {
        "model": model,
        "max_tokens": max_tokens,
        "system": system_prompt,
        "messages": [
            {
                "role": "user",
                "content": [
                    {"type": "text", "text": prompt},
                    {"type": "image", "source": _image_source(image_path, error_type)},
                ],
            }
        ],
        "stream": False,
    }
    request = urllib.request.Request(
        api_url,
        data=json.dumps(body, separators=(",", ":")).encode("utf-8"),
        headers={
            "Content-Type": "application/json",
            "x-api-key": api_key,
            "anthropic-version": ANTHROPIC_VERSION,
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")[:800]
        raise error_type(f"Claude HTTP {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise error_type(f"Claude connection failed: {error.reason}") from error
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        raise error_type("Claude Messages HTTP response was not JSON") from error
    return extract_anthropic_text(payload, error_type)


def install() -> None:
    import haku_snapshot_candidate_teacher as candidate_teacher
    import haku_snapshot_teacher as annotation_teacher

    def annotation_choice(response: Any) -> str:
        return extract_choice_text(response, annotation_teacher.TeacherError)

    def annotation_content(content: Any) -> dict[str, Any]:
        return select_schema_object(
            content,
            annotation_teacher.validate_annotation,
            annotation_teacher.TeacherError,
            "Snapshot annotation",
        )

    def annotation_call(
        image_path: pathlib.Path,
        prompt: str,
        *,
        api_key: str,
        api_url: str,
        model: str,
        timeout: int = 60,
    ) -> dict[str, Any]:
        text = call_anthropic_message(
            image_path,
            prompt,
            api_key=api_key,
            api_url=api_url,
            model=model,
            timeout=timeout,
            max_tokens=1200,
            system_prompt=annotation_teacher.SYSTEM_PROMPT,
            error_type=annotation_teacher.TeacherError,
        )
        return annotation_content(text)

    def candidate_choice(response: Any) -> str:
        return extract_choice_text(response, candidate_teacher.TeacherError)

    def candidate_content(content: Any) -> dict[str, Any]:
        return select_schema_object(
            content,
            candidate_teacher.validate_candidate_label,
            candidate_teacher.TeacherError,
            "candidate label",
        )

    def candidate_call(
        marked_image: pathlib.Path,
        *,
        pass_index: int,
        api_key: str,
        api_url: str,
        model: str,
        timeout: int,
    ) -> dict[str, Any]:
        text = call_anthropic_message(
            marked_image,
            candidate_teacher.CANDIDATE_PROMPT + f"\nIndependent semantic pass {pass_index}.",
            api_key=api_key,
            api_url=api_url,
            model=model,
            timeout=timeout,
            max_tokens=240,
            system_prompt=annotation_teacher.SYSTEM_PROMPT,
            error_type=candidate_teacher.TeacherError,
        )
        return candidate_content(text)

    annotation_teacher._extract_choice_content = annotation_choice
    annotation_teacher.parse_message_content = annotation_content
    annotation_teacher.call_haku = annotation_call
    candidate_teacher._extract_choice_content = candidate_choice
    candidate_teacher.parse_candidate_content = candidate_content
    candidate_teacher.call_haku_candidate = candidate_call
