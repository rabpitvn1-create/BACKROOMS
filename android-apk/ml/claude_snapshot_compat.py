#!/usr/bin/env python3
"""Compatibility adapter for Claude responses behind OpenAI-compatible gateways.

Some Claude routes include reasoning/preamble text around the requested JSON or put
useful text in ``reasoning_content``. The Snapshot teacher keeps strict schema
validation authoritative; this adapter only locates schema-valid JSON objects and
rejects ambiguous responses.
"""
from __future__ import annotations

import json
from typing import Any, Callable


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
    if not isinstance(response, dict):
        raise error_type("Claude response root is not an object")
    choices = response.get("choices")
    if not isinstance(choices, list) or not choices:
        raise error_type("Claude response has no choices")
    first = choices[0]
    if not isinstance(first, dict) or not isinstance(first.get("message"), dict):
        raise error_type("Claude response choice has no message")

    message = first["message"]
    # Gateways differ on whether adaptive/extended reasoning is exposed through
    # reasoning_content or mixed into content. Preserve both for schema scanning.
    reasoning = _content_text(message.get("reasoning_content")).strip()
    content = _content_text(message.get("content")).strip()
    joined = "\n".join(part for part in (reasoning, content) if part)
    if not joined:
        raise error_type("Claude response message contained no text")
    return joined


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

    def candidate_choice(response: Any) -> str:
        return extract_choice_text(response, candidate_teacher.TeacherError)

    def candidate_content(content: Any) -> dict[str, Any]:
        return select_schema_object(
            content,
            candidate_teacher.validate_candidate_label,
            candidate_teacher.TeacherError,
            "candidate label",
        )

    annotation_teacher._extract_choice_content = annotation_choice
    annotation_teacher.parse_message_content = annotation_content
    candidate_teacher._extract_choice_content = candidate_choice
    candidate_teacher.parse_candidate_content = candidate_content
